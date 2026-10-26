package dev.lance.focusallowlist;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Only the original focus implementation in the supplied MiSettings version is modified. */
public final class FocusHook implements IXposedHookLoadPackage {
    private static final String SERVICE = "com.xiaomi.misettings.usagestats.focusmode.service.FocusModeForeBackGroundMonitorService";
    private static final String TAG = "[FocusAllowlist 0.1.23] ";
    private static boolean initialized;
    private static Context context;
    private static Class<?> utils;
    private static final Map<String, PackageDecision> cache = Collections.synchronizedMap(new HashMap<String, PackageDecision>());
    private static final WatchdogRegistry<Service> timers = new WatchdogRegistry<Service>();
    private static final FocusScope ORIGIN = new FocusScope();
    private static final HandlerHolder TIMER = new HandlerHolder();
    private static final List<XC_MethodHook.Unhook> hooks = new ArrayList<XC_MethodHook.Unhook>();

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if (!Policy.TARGET.equals(p.packageName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) {
                synchronized (FocusHook.class) {
                    if (initialized) return;
                    initialized = true;
                }
                Context c = ((Context) h.args[0]).getApplicationContext();
                if (c == null) c = (Context) h.args[0];
                try {
                    PackageInfo info = c.getPackageManager().getPackageInfo(Policy.TARGET, 0);
                    if (!Policy.VERSION.equals(info.versionName) || info.versionCode != Policy.VERSION_CODE) {
                        log("UNSUPPORTED " + info.versionName + ": no focus hooks installed");
                        return;
                    }
                    context = c;
                    // Only remove this module's old malformed notification, never other notifications.
                    if (Policy.TARGET.equals(p.processName)) FocusIsland.cleanupLegacy(c);
                    install(p.classLoader);
                    if (Policy.TARGET.equals(p.processName)) {
                        try { FocusRecents.install(c); }
                        catch (Throwable error) { log("recents lifecycle unavailable: " + error); }
                    }
                    if (Policy.TARGET.equals(p.processName)) FocusIsland.resume(c, "process-start");
                    if (Policy.TARGET.equals(p.processName)) FocusMonitor.resume(c, "process-start");
                    log("READY " + info.versionName + " process=" + p.processName);
                } catch (Throwable t) {
                    for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) {}
                    hooks.clear();
                    log("DISABLED: " + t);
                }
            }
        });
    }

    private static void install(ClassLoader cl) throws Throwable {
        final Class<?> service = XposedHelpers.findClass(SERVICE, cl);
        final Class<?> listener = XposedHelpers.findClass("ae.b", cl);
        final Class<?> foreground = XposedHelpers.findClass("miui.process.ForegroundInfo", cl);
        final Class<?> status = XposedHelpers.findClass("f7.b", cl);
        final Class<?> delegate = XposedHelpers.findClass("com.xiaomi.misettings.usagestats.delegate.ActivityManagerdDelegate", cl);
        final Class<?> processDelegate = XposedHelpers.findClass("com.xiaomi.misettings.usagestats.delegate.ProcessManagerDelegate", cl);
        final Class<?> focusSettings = XposedHelpers.findClass("com.xiaomi.misettings.usagestats.FocusSettingsMainActivity", cl);
        final Class<?> focusFragment = XposedHelpers.findClass("com.xiaomi.misettings.usagestats.focusmode.FocusSettingsFragment", cl);
        final Class<?> focusBase = XposedHelpers.findClass(FocusNavigation.BASE, cl);
        final Class<?> focusTiming = XposedHelpers.findClass(FocusNavigation.TIMING, cl);
        final Class<?> taskB = XposedHelpers.findClass("be.b", cl);
        final Class<?> taskC = XposedHelpers.findClass("be.c", cl);
        final Class<?> effect = XposedHelpers.findClass("yd.a", cl);
        utils = XposedHelpers.findClass("be.e", cl);
        final Class<?> durationAdapter = XposedHelpers.findClass("xd.i", cl);
        // Validate the full set before installing any of the version-specific hooks.
        service.getDeclaredMethod("a", service, String.class);
        service.getDeclaredMethod("d");
        service.getDeclaredMethod("c");
        service.getDeclaredField("g");
        service.getDeclaredMethod("onCreate");
        service.getDeclaredMethod("onStartCommand", android.content.Intent.class, int.class, int.class);
        service.getDeclaredMethod("onDestroy");
        listener.getDeclaredMethod("onForegroundWindowChanged", foreground);
        listener.getDeclaredField("a");
        service.getDeclaredField("a");
        service.getDeclaredField("b");
        service.getDeclaredField("c");
        Class<?> foregroundListener = XposedHelpers.findClass("miui.process.IForegroundWindowListener", cl);
        processDelegate.getDeclaredMethod("registerForegroundWindowListener", foregroundListener);
        processDelegate.getDeclaredMethod("unregisterForegroundWindowListener", foregroundListener);
        foreground.getDeclaredField("mForegroundPackageName");
        status.getDeclaredMethod("C", Context.class, int.class);
        utils.getDeclaredMethod("o", Context.class);
        utils.getDeclaredMethod("g", Context.class);
        delegate.getDeclaredMethod("forceStopPackage", ActivityManager.class, String.class);
        focusSettings.getDeclaredMethod("onCreate", android.os.Bundle.class);
        focusSettings.getDeclaredMethod("onResume");
        focusSettings.getDeclaredMethod("q");
        focusSettings.getDeclaredField("k");
        focusFragment.getDeclaredMethod("onViewCreated", View.class, android.os.Bundle.class);
        focusFragment.getDeclaredMethod("onResume");
        focusFragment.getDeclaredMethod("E", focusFragment);
        focusBase.getDeclaredMethod("onCreate", android.os.Bundle.class);
        focusBase.getDeclaredMethod("onResume");
        focusBase.getDeclaredMethod("onNewIntent", Intent.class);
        focusTiming.getDeclaredMethod("o");
        XposedHelpers.findClass(FocusNavigation.PORT, cl);
        XposedHelpers.findClass(FocusNavigation.LAND, cl);
        focusFragment.getDeclaredField("n");
        for (Class<?> task : new Class<?>[]{taskB, taskC}) {
            task.getDeclaredMethod("run");
            if (task.getDeclaredField("a").getType() != int.class) throw new IllegalStateException("task discriminator changed");
        }
        effect.getDeclaredMethod("a", Context.class);
        if (effect.getDeclaredField("a").getType() != String.class) throw new IllegalStateException("effect key changed");
        FocusDuration.validate(focusSettings, focusFragment, focusTiming, durationAdapter, utils);
        FocusMonitor.configure(utils, service, processDelegate);
        hookFocusTask(taskB);
        hookFocusTask(taskC);
        installNavigation(focusSettings, focusFragment, focusBase, focusTiming);
        FocusDuration.install(hooks, focusSettings, focusFragment, focusTiming, utils);
        add(XposedHelpers.findAndHookMethod(effect, "a", Context.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    String key = (String) XposedHelpers.getObjectField(h.thisObject, "a");
                    if (Policy.PRESERVED_SETTINGS.contains(key) && focusStack()) h.setResult(null);
                } catch (Throwable t) { log("effect restore fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(android.content.ContextWrapper.class, "sendBroadcast",
            android.content.Intent.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam h) {
                    android.content.Intent intent = (android.content.Intent) h.args[0];
                    if (intent != null && "miui.action.handymode.changemode".equals(intent.getAction())
                        && focusStack()) h.setResult(null);
                }
            }));

        // Both native screens attach the trial click listener even when they hide the button.
        // Restore visibility only: keep the native one-minute duration and all stored history.
        add(XposedHelpers.findAndHookMethod(focusSettings, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) { showTrial(h.thisObject, "k"); }
        }));
        add(XposedHelpers.findAndHookMethod(focusFragment, "onViewCreated", View.class, android.os.Bundle.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) { showTrial(h.thisObject, "n"); }
        }));

        add(XposedHelpers.findAndHookMethod(listener, "onForegroundWindowChanged", foreground, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    String pkg = (String) XposedHelpers.getObjectField(h.args[0], "mForegroundPackageName");
                    Service s = (Service) XposedHelpers.getObjectField(h.thisObject, "a");
                    if (active(s) && allow(pkg) && !nativeSos(s)) {
                        XposedHelpers.setObjectField(s, "b", pkg);
                        cancelNativePullback(s);
                        clearStatusBar();
                        h.setResult(null);
                    }
                } catch (Throwable t) { log("foreground fallback: " + t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                try { schedule((Service) XposedHelpers.getObjectField(h.thisObject, "a")); }
                catch (Throwable t) { log("foreground timer fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(service, "a", service, String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    Service s = (Service) h.args[0];
                    String pkg = (String) h.args[1];
                    if (pkg == null || pkg.length() == 0) pkg = topPackage(s);
                    if (active(s) && allow(pkg) && !nativeSos(s)) {
                        cancelNativePullback(s);
                        clearStatusBar();
                        h.setResult(null);
                    }
                } catch (Throwable t) { log("monitor fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(service, "d", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    Service s = (Service) h.thisObject;
                    if (active(s) && allow(topPackage(s))) h.setResult(null);
                } catch (Throwable t) { log("pullback fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(delegate, "forceStopPackage", ActivityManager.class, String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                if (focusStack() && allow((String) h.args[1])) h.setResult(null);
            }
        }));
        add(XposedHelpers.findAndHookMethod(status, "C", Context.class, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                if (focusStack()) h.args[1] = Integer.valueOf(0);
            }
        }));

        XC_MethodHook preserve = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                if (h.args.length >= 3 && h.args[1] instanceof String
                    && Policy.PRESERVED_SETTINGS.contains((String) h.args[1]) && focusStack()) h.setResult(Boolean.TRUE);
            }
        };
        for (Class<?> settings : new Class<?>[]{Settings.System.class, Settings.Secure.class, Settings.Global.class}) {
            for (String name : new String[]{"putInt", "putLong", "putString"})
                hooks.addAll(XposedBridge.hookAllMethods(settings, name, preserve));
        }
        // Keep system audio policy intact; only intercept calls made by the native focus flow.
        add(XposedHelpers.findAndHookMethod(AudioManager.class, "requestAudioFocus",
            AudioManager.OnAudioFocusChangeListener.class, int.class, int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam h) {
                    if (focusStack()) h.setResult(Integer.valueOf(AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
                }
            }));
        add(XposedHelpers.findAndHookMethod(AudioManager.class, "abandonAudioFocus",
            AudioManager.OnAudioFocusChangeListener.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam h) {
                    if (focusStack()) h.setResult(Integer.valueOf(AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
                }
            }));
        add(XposedHelpers.findAndHookMethod(AudioManager.class, "setMicrophoneMute", boolean.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                if (focusStack()) h.setResult(null);
            }
        }));
        Class<?> sound = XposedHelpers.findClass("android.provider.MiuiSettings$SoundMode", cl);
        add(XposedHelpers.findAndHookMethod(sound, "setSilenceModeOn", Context.class, boolean.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                if (focusStack()) h.setResult(null);
            }
        }));
        add(XposedHelpers.findAndHookMethod(service, "onCreate", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                timers.register((Service) h.thisObject);
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                if (!h.hasThrowable()) FocusMonitor.created((Service) h.thisObject);
            }
        }));
        add(XposedHelpers.findAndHookMethod(service, "onStartCommand", android.content.Intent.class, int.class, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) { cache.clear(); }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                FocusIsland.start((Service) h.thisObject);
                FocusMonitor.resume((Service) h.thisObject, "service-start");
                try { schedule((Service) h.thisObject); }
                catch (Throwable t) { log("start timer fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(service, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                Service s = (Service) h.thisObject;
                FocusIsland.onServiceDestroyed(s);
                Runnable old = timers.unregister(s);
                if (old != null) TIMER.get().removeCallbacks(old);
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                FocusMonitor.destroyed((Service) h.thisObject);
            }
        }));
        // Supported native service inherits the empty framework callback. Scope by exact class.
        add(XposedHelpers.findAndHookMethod(Service.class, "onTaskRemoved", Intent.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) {
                if (h.thisObject.getClass() == service) FocusMonitor.taskRemoved((Service) h.thisObject);
            }
        }));
    }

    private static void add(XC_MethodHook.Unhook h) { hooks.add(h); }
    private static void installNavigation(Class<?> settings, final Class<?> fragment, Class<?> base, Class<?> timing) {
        add(XposedHelpers.findAndHookMethod(base, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                Activity activity = (Activity) h.thisObject;
                Intent intent = activity.getIntent();
                if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                hideFocusTask((Activity) h.thisObject);
                FocusIsland.resume((Activity) h.thisObject, "focus-page");
                FocusMonitor.resume((Activity) h.thisObject, "focus-page");
            }
        }));
        add(XposedHelpers.findAndHookMethod(base, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) {
                hideFocusTask((Activity) h.thisObject);
                FocusIsland.resume((Activity) h.thisObject, "focus-page");
                FocusMonitor.resume((Activity) h.thisObject, "focus-page");
            }
        }));
        add(XposedHelpers.findAndHookMethod(base, "onNewIntent", Intent.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                Intent intent = (Intent) h.args[0];
                if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                FocusRecents.update((Activity) h.thisObject, "new-intent");
            }
        }));
        add(XposedHelpers.findAndHookMethod(settings, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) { resumeFromSettings((Activity) h.thisObject); }
        }));
        add(XposedHelpers.findAndHookMethod(fragment, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) {
                try { resumeFromSettings(fragmentActivity(h.thisObject)); }
                catch (Throwable t) { log("fragment resume fallback: " + t); }
            }
        }));
        add(XposedHelpers.findAndHookMethod(settings, "q", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) { guardStart(h, (Activity) h.thisObject, false); }
        }));
        add(XposedHelpers.findAndHookMethod(fragment, "E", fragment, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try { guardStart(h, fragmentActivity(h.args[0]), false); }
                catch (Throwable t) { h.setResult(null); log("fragment start blocked: " + t); }
            }
        }));
        // Last gate, before native o() increments trial count or overwrites any session data.
        add(XposedHelpers.findAndHookMethod(timing, "o", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) { guardStart(h, (Activity) h.thisObject, true); }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                if (!h.hasThrowable()) FocusMonitor.resume((Activity) h.thisObject, "native-start");
            }
        }));
    }
    private static Activity fragmentActivity(Object fragment) {
        return (Activity) XposedHelpers.callMethod(fragment, "getActivity");
    }
    private static void resumeFromSettings(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        FocusIsland.resume(activity, "settings-page");
        FocusMonitor.resume(activity, "settings-page");
        try {
            // Expired cleanup is allowed to finish without repeatedly reopening an expired page.
            if (active(activity)) FocusNavigation.openCurrent(activity);
        } catch (Throwable t) { log("resume current fallback: " + t); }
    }
    private static void hideFocusTask(Activity activity) {
        FocusRecents.update(activity, "native-page");
    }
    private static void guardStart(XC_MethodHook.MethodHookParam h, Activity activity, boolean timing) {
        if (activity == null) { h.setResult(null); return; }
        boolean pending;
        try { pending = FocusNavigation.pending(activity); }
        catch (Throwable t) {
            h.setResult(null);
            log("start blocked: cannot read session state " + t);
            blockedMessage(activity, "无法读取专注状态，未启动新的专注");
            return;
        }
        if (!pending) return;
        h.setResult(null);
        try {
            FocusNavigation.openCurrent(activity);
            log("duplicate start blocked: returning to current session");
        } catch (Throwable t) {
            log("return current failed; duplicate start remains blocked: " + t);
            blockedMessage(activity, "当前专注尚未结束，未启动新的专注");
        } finally {
            if (timing) try { activity.finish(); } catch (Throwable t) { log("close duplicate intro: " + t); }
        }
    }
    private static void blockedMessage(Activity activity, String message) {
        // A toast failure must not escape the hook and undo its blocked result.
        try { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show(); }
        catch (Throwable t) { log("start message: " + t); }
    }
    private static void hookFocusTask(final Class<?> task) {
        add(XposedHelpers.findAndHookMethod(task, "run", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    int id = XposedHelpers.getIntField(h.thisObject, "a");
                    if (FocusScope.task(task.getName(), id)) {
                        h.setObjectExtra("focusOrigin", Integer.valueOf(ORIGIN.enter()));
                        // This branch only toggles the ringer on old Android versions.
                        if (task.getName().equals("be.b") && id == 0) h.setResult(null);
                    }
                } catch (Throwable t) { log("task origin fallback: " + t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                Object old = h.getObjectExtra("focusOrigin");
                if (old instanceof Integer) ORIGIN.restore(((Integer) old).intValue());
            }
        }));
    }
    private static void log(String s) { XposedBridge.log(TAG + s); }
    private static void showTrial(Object screen, String field) {
        try {
            View trial = (View) XposedHelpers.getObjectField(screen, field);
            if (trial != null) trial.setVisibility(View.VISIBLE);
        } catch (Throwable t) { log("trial visibility fallback: " + t); }
    }
    private static boolean focusStack() {
        return ORIGIN.contains(Thread.currentThread().getStackTrace());
    }
    private static boolean active(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), "settings_focus_mode_status", 0) == 1
            && !((Boolean) XposedHelpers.callStaticMethod(utils, "o", c)).booleanValue();
    }
    private static boolean allow(String pkg) {
        if (Policy.named(pkg)) return true;
        if (pkg == null || context == null) return false;
        boolean result = false;
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) for (InputMethodInfo input : imm.getInputMethodList()) {
                if (pkg.equals(input.getPackageName())) { result = true; break; }
            }
            if (!result) {
                long now = android.os.SystemClock.elapsedRealtime();
                PackageDecision cached = cache.get(pkg);
                if (cached != null && now - cached.when < 30000L) return cached.allowed;
                ApplicationInfo a = context.getPackageManager().getApplicationInfo(pkg, 0);
                boolean system = (a.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                boolean launch = context.getPackageManager().getLaunchIntentForPackage(pkg) != null;
                result = Policy.allowed(pkg, system, a.uid % 100000, launch, false);
                cache.put(pkg, new PackageDecision(result, now));
            }
        } catch (Throwable t) { log("package check " + pkg + ": " + t.getClass().getSimpleName()); }
        return result;
    }
    private static final class PackageDecision {
        final boolean allowed; final long when;
        PackageDecision(boolean allowed, long when) { this.allowed = allowed; this.when = when; }
    }
    private static String topPackage(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return null;
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null ? null : tasks.get(0).topActivity.getPackageName();
    }
    /** Fallback if the vendor foreground listener was lost during recent-task cleanup. */
    static void checkForeground(Service s) {
        if (!active(s)) return;
        ActivityManager am = (ActivityManager) s.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks == null || tasks.isEmpty()) return;
        ActivityManager.RunningTaskInfo task = tasks.get(0);
        // Preserve the native listener's user boundary; never force-stop a different profile.
        int user = XposedHelpers.getIntField(task, "userId");
        if (user != android.os.Process.myUid() / 100000 || task.topActivity == null) return;
        String pkg = task.topActivity.getPackageName();
        if (pkg == null || pkg.length() == 0 || allow(pkg)) return;
        XposedHelpers.callStaticMethod(s.getClass(), "a", s, pkg);
    }
    private static boolean nativeSos(Service s) {
        Object activities = XposedHelpers.getStaticObjectField(s.getClass(), "g");
        Object top = XposedHelpers.callMethod(s, "c");
        return activities instanceof List && ((List<?>) activities).contains(top);
    }
    private static void cancelNativePullback(Service s) {
        Handler h = (Handler) XposedHelpers.getObjectField(s, "a");
        if (h != null) h.removeCallbacksAndMessages(null);
    }
    private static void clearStatusBar() {
        try {
            Object manager = context.getSystemService("statusbar");
            if (manager != null) XposedHelpers.callMethod(manager, "disable", 0);
        } catch (Throwable t) { log("statusbar: " + t.getClass().getSimpleName()); }
    }
    /** Native activity stops updating its timer onStop; use its existing finish path at the deadline. */
    private static void schedule(final Service s) {
            final WeakReference<Service> ref = new WeakReference<Service>(s);
            Runnable tick = new Runnable() {
                @Override public void run() {
                    Service live = ref.get();
                    if (live == null || !timers.current(live, this)) return;
                    try {
                        if (((Boolean) XposedHelpers.callStaticMethod(utils, "o", live)).booleanValue()) {
                            if (!timers.finish(live, this)) return;
                            XposedHelpers.callStaticMethod(utils, "g", live);
                            live.stopSelf();
                            log("native finish requested");
                        } else repost(live, this, nextDelay(live));
                    } catch (Throwable t) {
                        log("deadline retry: " + t);
                        if (timers.retry(live, this)) repost(live, this, 5000L);
                    }
                }
            };
            long delay = 1000L;
            try { delay = nextDelay(s); }
            catch (Throwable t) { log("deadline initial retry: " + t); }
            if (timers.install(s, tick)) repost(s, tick, delay);
    }
    private static long nextDelay(Service s) {
        long start = Settings.System.getLong(s.getContentResolver(), "misettings_key_enter_focus_time", 0L);
        int minutes = Settings.System.getInt(s.getContentResolver(), "misettings_focus_limit_time", 20);
        return FocusTiming.delay(start, minutes, System.currentTimeMillis());
    }
    private static void repost(Service s, Runnable tick, long delay) {
        synchronized (timers) {
            if (timers.current(s, tick)) TIMER.get().postDelayed(tick, delay);
        }
    }
    private static final class HandlerHolder {
        private Handler handler;
        synchronized Handler get() {
            if (handler == null) handler = new Handler(Looper.getMainLooper());
            return handler;
        }
    }
}
