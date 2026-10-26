package dev.lance.focusallowlist;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import java.util.WeakHashMap;
import de.robv.android.xposed.XposedBridge;

/** Reconcile owned native focus tasks after real page transitions, without removing tasks. */
final class FocusRecents implements Application.ActivityLifecycleCallbacks {
    private static final WeakHashMap<Activity, String> last = new WeakHashMap<Activity, String>();

    static void install(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof Application)
            ((Application) app).registerActivityLifecycleCallbacks(new FocusRecents());
    }

    static void update(Activity activity, String source) {
        if (activity == null || !RecentsPolicy.focusPage(activity.getPackageName(), activity.getClass().getName())) return;
        try {
            String result = FocusNavigation.hideInRecents(activity);
            // A stable success is quiet. Changed task IDs / readback failures remain diagnosable.
            if (!result.equals(last.get(activity))) {
                last.put(activity, result);
                XposedBridge.log("[FocusAllowlist 0.1.23] recents " + source + " " + result);
            }
        } catch (Throwable error) {
            String result = "failed " + error;
            if (!result.equals(last.get(activity))) {
                last.put(activity, result);
                XposedBridge.log("[FocusAllowlist 0.1.23] recents " + source + " " + result);
            }
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle state) { update(a, "created"); }
    @Override public void onActivityStarted(Activity a) { update(a, "started"); }
    @Override public void onActivityResumed(Activity a) { update(a, "resumed"); }
    @Override public void onActivityPaused(Activity a) { update(a, "paused"); }
    @Override public void onActivityStopped(Activity a) { update(a, "stopped"); }
    // API 29+: run after concrete subclass callbacks, not just inside their super calls.
    @Override public void onActivityPostCreated(Activity a, Bundle state) { update(a, "post-created"); }
    @Override public void onActivityPostResumed(Activity a) { update(a, "post-resumed"); }
    @Override public void onActivityPostPaused(Activity a) { update(a, "post-paused"); }
    @Override public void onActivityPostStopped(Activity a) { update(a, "post-stopped"); }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
    @Override public void onActivityDestroyed(Activity a) { last.remove(a); }
}
