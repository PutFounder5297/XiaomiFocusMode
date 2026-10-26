package dev.lance.focusallowlist;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.XposedBridge;

/** Use only the uploaded ROM's native per-package lock; no system-server hooks. */
final class FocusProcessGuard {
    private static Handler worker;
    private static Runnable tick;
    private static ContentObserver observer;
    private static long lastError;
    private static SharedPreferences prefs;
    private static ProcessLockLease lease;
    private static boolean confirmed;

    static synchronized void resume(Context context) {
        if (!Policy.TARGET.equals(Application.getProcessName()) || Process.myUid() != 1000) return;
        final Context app = context.getApplicationContext();
        if (app == null) return;
        try {
            if (worker == null) {
                HandlerThread thread = new HandlerThread("FocusProcessGuard");
                thread.start();
                worker = new Handler(thread.getLooper());
                tick = new Runnable() {
                    @Override public void run() {
                        long delay = 0L;
                        try {
                            prepare(app); // Binder calls and durable journal I/O stay off the main thread.
                            // Keep the lock through native expiry cleanup; release only at native status 0.
                            int status = Settings.Global.getInt(app.getContentResolver(), "settings_focus_mode_status", 0);
                            if (status != 0 && status != 1) throw new Exception("native focus status unavailable");
                            int result = lease.sync(status == 1);
                            if (result == ProcessLockLease.ACQUIRED) log("acquired package=" + Policy.TARGET + " user=0");
                            if (result == ProcessLockLease.KEPT && !confirmed)
                                log("confirmed existing lock; module-owned=" + prefs.getBoolean("restore_unlocked", false));
                            if (result == ProcessLockLease.RESTORED) log("restored original unlocked state");
                            confirmed = status == 1;
                            if (status == 1 || prefs.getBoolean("restore_unlocked", false)) delay = 5000L;
                        } catch (Throwable error) {
                            confirmed = false;
                            long now = SystemClock.elapsedRealtime();
                            if (lastError == 0L || now - lastError >= 30000L) {
                                log("failed (will retry): " + error);
                                lastError = now;
                            }
                            delay = 15000L;
                        }
                        worker.removeCallbacks(this);
                        if (delay > 0L) worker.postDelayed(this, delay);
                    }
                };
            }
            request();
        } catch (Throwable error) { log("initialization failed: " + error); }
    }
    private static void prepare(Context app) {
        if (prefs == null) prefs = app.getSharedPreferences("lance_focus_process_lock_v1", Context.MODE_PRIVATE);
        if (lease == null) lease = new ProcessLockLease(new NativeLock(app.getClassLoader()), new ProcessLockLease.Journal() {
            @Override public boolean pending() { return prefs.getBoolean("restore_unlocked", false); }
            @Override public boolean record(boolean value) {
                return prefs.edit().putBoolean("restore_unlocked", value).commit();
            }
        });
        if (observer == null) {
            ContentObserver candidate = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) { request(); }
            };
            app.getContentResolver().registerContentObserver(Settings.Global.getUriFor("settings_focus_mode_status"), false, candidate);
            observer = candidate;
        }
    }
    private static synchronized void request() {
        if (worker == null || tick == null) return;
        worker.removeCallbacks(tick);
        worker.post(tick);
    }
    private static final class NativeLock implements ProcessLockLease.Backend {
        private final ClassLoader loader;
        private Method getDefault, query, update;
        NativeLock(ClassLoader loader) { this.loader = loader; }
        private Object endpoint() throws Exception {
            if (getDefault == null) {
                Class<?> nativeClass = Class.forName("miui.process.ProcessManagerNative", false, loader);
                Class<?> api = Class.forName("miui.process.IProcessManager", false, loader);
                Method get = nativeClass.getMethod("getDefault");
                Method read = api.getMethod("isLockedApplication", String.class, int.class);
                Method write = api.getMethod("updateApplicationLockedState", String.class, int.class, boolean.class);
                if (!Modifier.isStatic(get.getModifiers()) || get.getReturnType() != api
                    || read.getReturnType() != boolean.class || write.getReturnType() != void.class)
                    throw new Exception("native lock API mismatch");
                query = read;
                update = write;
                getDefault = get;
            }
            Object endpoint = getDefault.invoke(null);
            if (endpoint == null) throw new Exception("native process service unavailable");
            return endpoint;
        }
        @Override public boolean locked() throws Exception {
            Object endpoint = endpoint();
            return ((Boolean) query.invoke(endpoint, Policy.TARGET, 0)).booleanValue();
        }
        @Override public void lock(boolean value) throws Exception {
            Object endpoint = endpoint();
            update.invoke(endpoint, Policy.TARGET, 0, value);
        }
    }
    private static void log(String message) { XposedBridge.log("[FocusAllowlist 0.1.20] process-lock " + message); }
    private FocusProcessGuard() {}
}
