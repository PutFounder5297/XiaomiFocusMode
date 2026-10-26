package dev.lance.focusallowlist;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Recover the real native enforcer, independently of the page and island renderer. */
final class FocusMonitor {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final MonitorRecovery recovery = new MonitorRecovery();
    private static WeakReference<Service> live = new WeakReference<Service>(null);
    private static Class<?> nativeUtils;
    private static Class<?> nativeService;
    private static Class<?> nativeDelegate;
    private static IslandRefreshLoop loop;

    static void configure(Class<?> utils, Class<?> service, Class<?> delegate) {
        nativeUtils = utils;
        nativeService = service;
        nativeDelegate = delegate;
    }
    static void created(Service service) { live = new WeakReference<Service>(service); }
    static void destroyed(Service service) {
        if (live.get() == service) live.clear();
        resume(service, "service-destroy"); // Called after native unregister/cleanup, never skip it.
    }
    static void taskRemoved(Service service) {
        try {
            if (live.get() != service || !active(service)) return;
            Object listener = XposedHelpers.getObjectField(service, "c");
            // Refresh precisely this service's listener; never accumulate duplicate registrations.
            XposedHelpers.callStaticMethod(nativeDelegate, "unregisterForegroundWindowListener", listener);
            XposedHelpers.callStaticMethod(nativeDelegate, "registerForegroundWindowListener", listener);
            log("listener refreshed after task removal");
        } catch (Throwable error) { log("listener recovery failed: " + error); }
        resume(service, "task-removed");
    }
    static void resume(Context context, final String source) {
        if (!Policy.TARGET.equals(Application.getProcessName())) return;
        final Context app = context.getApplicationContext();
        if (app == null) return;
        handler.post(new Runnable() {
            @Override public void run() {
                if (nativeUtils == null) return;
                FocusProcessGuard.resume(app);
                if (loop == null) {
                    loop = new IslandRefreshLoop(new IslandRefreshLoop.Scheduler() {
                        @Override public boolean post(Runnable task, long delay) { return handler.postDelayed(task, delay); }
                        @Override public void remove(Runnable task) { handler.removeCallbacks(task); }
                    }, new IslandRefreshLoop.Renderer() {
                        @Override public long refresh() { return check(app); }
                        @Override public void clear() { recovery.clear(); }
                    });
                }
                loop.resume();
                log("check requested source=" + source);
            }
        });
    }
    private static boolean active(Context app) {
        return Settings.Global.getInt(app.getContentResolver(), "settings_focus_mode_status", 0) == 1
            && !((Boolean) XposedHelpers.callStaticMethod(nativeUtils, "o", app)).booleanValue();
    }
    private static long check(Context app) {
        try {
            int status = Settings.Global.getInt(app.getContentResolver(), "settings_focus_mode_status", 0);
            if (status != 1) return 0L;
            if (!active(app)) {
                // Timer may expire while the service is absent. Use the native finish path.
                if (live.get() == null) XposedHelpers.callStaticMethod(nativeUtils, "g", app);
                return 0L;
            }
            long start = Settings.System.getLong(app.getContentResolver(), "misettings_key_enter_focus_time", 0L);
            long duration = Settings.System.getLong(app.getContentResolver(), "misettings_focus_limit_milli", 0L);
            Service service = live.get();
            if (recovery.request(start, duration, SystemClock.elapsedRealtime(), service != null)) {
                // No timing/intro activity, no observer_ringer extras, no writes to session clocks.
                app.startService(new Intent(app, nativeService));
                log("native monitor restart requested");
            }
            if (service != null) FocusHook.checkForeground(service);
            return 2000L;
        } catch (Throwable error) {
            log("monitor check/restart failed: " + error);
            return 5000L; // No main-thread exception or tight restart loop.
        }
    }
    private static void log(String message) { XposedBridge.log("[FocusAllowlist 0.1.20] monitor " + message); }
    private FocusMonitor() {}
}
