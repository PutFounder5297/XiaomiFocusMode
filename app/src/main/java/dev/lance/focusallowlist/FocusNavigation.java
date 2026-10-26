package dev.lance.focusallowlist;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;

/** Reopen an existing native session without changing its start time or duration. */
final class FocusNavigation {
    static final String BASE = "com.xiaomi.misettings.usagestats.focusmode.FocusModeActivityBase";
    static final String TIMING = "com.xiaomi.misettings.usagestats.focusmode.FocusModeTimingActivityBase";
    static final String SETTINGS = "com.xiaomi.misettings.usagestats.FocusSettingsMainActivity";
    static final String PORT = "com.xiaomi.misettings.usagestats.focusmode.port.FocusModePortActivity";
    static final String LAND = "com.xiaomi.misettings.usagestats.focusmode.land.FocusModeLandActivity";

    // Status remains occupied during expiry cleanup; do not overwrite that session either.
    static boolean pending(Activity activity) {
        return Settings.Global.getInt(activity.getContentResolver(), "settings_focus_mode_status", 0) == 1;
    }

    static void openCurrent(Activity activity) {
        boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Intent intent = new Intent().setClassName(Policy.TARGET, landscape ? LAND : PORT);
        intent.putExtra("keyCanWrite", false);
        // Both native activities are singleTask: reuse their task and never start the timing intro.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        activity.startActivity(intent);
    }

    static String hideInRecents(Activity activity) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
        if (manager == null) return "manager unavailable";
        Intent incoming = activity.getIntent();
        if (incoming != null) incoming.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        int taskId = activity.getTaskId();
        int matched = 0, verified = 0, failed = 0;
        boolean current = false;
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            try {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                int candidate = RecentsPolicy.taskId(Build.VERSION.SDK_INT >= 29 ? info.taskId : -1,
                    info.persistentId, info.id);
                ComponentName base = info.baseActivity;
                if (base == null && info.baseIntent != null) base = info.baseIntent.getComponent();
                ComponentName top = info.topActivity;
                if (!RecentsPolicy.hide(taskId, candidate, pkg(base), name(base), pkg(top), name(top))) continue;
                matched++;
                current |= taskId >= 0 && candidate == taskId;
                // Reapply to the server's root Intent, not merely Activity.getIntent().
                task.setExcludeFromRecents(true);
                Intent root = task.getTaskInfo().baseIntent;
                if (root != null && (root.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) verified++;
            } catch (RuntimeException error) {
                // A stale task must not prevent hiding other live focus tasks.
                failed++;
            }
        }
        return "task=" + taskId + " current=" + current + " matched=" + matched
            + " verified=" + verified + " failed=" + failed;
    }
    private static String pkg(ComponentName component) { return component == null ? null : component.getPackageName(); }
    private static String name(ComponentName component) { return component == null ? null : component.getClassName(); }
    private FocusNavigation() {}
}
