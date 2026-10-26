package dev.lance.focusallowlist;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import de.robv.android.xposed.XposedBridge;

/** Local countdown display, using a buttons-free island template on the confirmed protocol. */
final class FocusIsland {
    private static final String CHANNEL = "focusallowlist_native_focus_v1";
    private static final String TAG = "dev.lance.focusallowlist.nativefocus";
    private static final int ID = 0x4c4619;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static IslandRefreshLoop loop;
    private static ContentObserver observer;
    private static Context observerContext;
    private static boolean failed;
    private static int loggedProtocol = Integer.MIN_VALUE;
    private static boolean channelReady;

    static void start(Service service) { resume(service, "service-start"); }

    static void onServiceDestroyed(Service service) {
        // A destroyed native service is not proof that native focus has ended.
        // Re-read the session; the renderer alone decides whether to remove its display.
        resume(service, "service-destroy");
    }

    static void resume(Context context, final String source) {
        try {
            // All focus activities and the monitor service run in this process in the
            // supported APK. Other MiSettings processes cannot compete for this notification.
            if (!Policy.TARGET.equals(Application.getProcessName())) return;
            Context candidate = context.getApplicationContext();
            if (candidate == null) {
                if (context instanceof android.app.Activity || context instanceof Service) return;
                candidate = context;
            }
            final Context app = candidate; // Never capture an Activity or Service.
            Runnable recover = new Runnable() {
                @Override public void run() {
                    try { resumeOnMain(app, source); }
                    catch (Throwable error) { clear(app); log("resume failed; native focus unchanged: " + error); }
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) recover.run();
            else if (!handler.post(recover)) log("resume dispatch rejected: " + source);
        } catch (Throwable error) { log("resume failed; native focus unchanged: " + error); }
    }

    private static void resumeOnMain(final Context app, String source) {
        if (loop == null) {
            loop = new IslandRefreshLoop(new IslandRefreshLoop.Scheduler() {
                @Override public boolean post(Runnable task, long delay) { return handler.postDelayed(task, delay); }
                @Override public void remove(Runnable task) { handler.removeCallbacks(task); }
            }, new IslandRefreshLoop.Renderer() {
                @Override public long refresh() { return refreshDisplay(app); }
                @Override public void clear() { removeObserver(); cancel(app); }
            });
        }
        // Check immediately even when the old task was lost, the notification was cleared,
        // or the process restarted. Do not reset the native start time or duration.
        if (!loop.resume()) return;
        if (observer == null) observe(app);
        log("refresh requested source=" + source);
    }

    private static long refreshDisplay(Context app) {
        try {
            long now = System.currentTimeMillis();
            int status = Settings.Global.getInt(app.getContentResolver(), "settings_focus_mode_status", 0);
            long start = Settings.System.getLong(app.getContentResolver(), "misettings_key_enter_focus_time", 0L);
            long duration = Settings.System.getLong(app.getContentResolver(), "misettings_focus_limit_milli", 0L);
            long remaining = IslandTiming.remaining(status, start, duration, now);
            if (remaining == 0L) return 0L;
            boolean island = publish(app, IslandTiming.end(start, duration), remaining);
            failed = false;
            return IslandTemplate.refreshDelay(remaining, island);
        } catch (Throwable error) {
            cancel(app);
            if (!failed) log("update failed; native focus unchanged: " + error);
            failed = true;
            return 30000L;
        }
    }

    private static void clear(Context app) {
        if (loop != null) loop.stop();
        else { removeObserver(); cancel(app); }
    }
    private static void observe(final Context app) {
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                // Observer callbacks and display changes are confined to the main handler.
                if (observer != this) return;
                try {
                    if (Settings.Global.getInt(app.getContentResolver(), "settings_focus_mode_status", 0) != 1) clear(app);
                    else if (loop != null) loop.resume();
                } catch (Throwable error) { log("status observer fallback: " + error); }
            }
        };
        observerContext = app;
        try {
            app.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("settings_focus_mode_status"), false, observer);
        } catch (Throwable error) {
            removeObserver();
            log("status observer unavailable; retaining polling: " + error);
        }
    }
    private static void removeObserver() {
        ContentObserver old = observer;
        Context app = observerContext;
        observer = null;
        observerContext = null;
        if (old != null && app != null) try { app.getContentResolver().unregisterContentObserver(old); }
        catch (Throwable error) { log("unregister status observer: " + error); }
    }
    private static void cancel(Context app) {
        try {
            NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(TAG, ID);
        } catch (Throwable error) { if (!failed) log("cancel failed: " + error); }
    }

    static void cleanupLegacy(Context app) {
        try {
            NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            for (StatusBarNotification item : manager.getActiveNotifications()) {
                if (ID == item.getId() && TAG.equals(item.getTag())
                        && item.getNotification().extras != null
                        && item.getNotification().extras.containsKey("miui.focus.param")
                        && !IslandTemplate.REVISION.equals(item.getNotification().extras.getString(IslandTemplate.REVISION_KEY))) {
                    manager.cancel(TAG, ID);
                    log("removed legacy vendor-template notification");
                }
            }
        } catch (Throwable error) { log("legacy notification cleanup failed: " + error); }
    }

    private static boolean publish(Context app, long end, long remaining) {
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        if (!channelReady) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "原生专注倒计时", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
            channelReady = true;
        }

        // Exported settings entry is valid even after expiry. Its existing hook resumes live focus.
        Intent intent = new Intent().setClassName(Policy.TARGET, FocusNavigation.SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(app, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("原生专注进行中")
            .setContentText("剩余 " + IslandTiming.label(remaining) + " · 点击返回专注")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(end).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(remaining);
        int protocol = Settings.System.getInt(app.getContentResolver(), "notification_focus_protocol", 0);
        String payload = IslandTemplate.build(protocol, remaining);
        if (payload != null) {
            Bundle pictures = new Bundle();
            pictures.putParcelable(IslandTemplate.PIC, Icon.createWithResource("android", android.R.drawable.ic_lock_idle_alarm));
            Bundle extras = new Bundle();
            extras.putBundle("miui.focus.pics", pictures);
            extras.putString("miui.focus.param", payload);
            extras.putString(IslandTemplate.REVISION_KEY, IslandTemplate.REVISION);
            builder.addExtras(extras);
        }
        Notification notification = builder.build();
        manager.notify(TAG, ID, notification);
        if (loggedProtocol != protocol) {
            loggedProtocol = protocol;
            log("protocol=" + protocol + " template=" + (payload == null ? "normal" : IslandTemplate.REVISION));
        }
        return payload != null;
    }

    private static void log(String message) { XposedBridge.log("[FocusAllowlist 0.1.20] notification " + message); }
    private FocusIsland() {}
}
