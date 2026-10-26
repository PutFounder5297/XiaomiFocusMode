package dev.lance.focusallowlist;

public final class RecentsPolicyTest {
    private static int checks;
    private static final String PKG = "com.xiaomi.misettings";
    private static final String PORT = PKG + ".usagestats.focusmode.port.FocusModePortActivity";
    private static final String LAND = PKG + ".usagestats.focusmode.land.FocusModeLandActivity";
    private static final String INTRO = PKG + ".usagestats.focusmode.port.FocusModeTimingPortActivity";
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(RecentsPolicy.taskId(42, 42, -1) == 42, "background task has stable modern ID despite legacy -1");
        check(RecentsPolicy.taskId(-1, 42, -1) == 42, "API 28 uses persistent ID");
        check(RecentsPolicy.taskId(42, 8, 8) == 42, "modern ID takes precedence");
        check(RecentsPolicy.taskId(-1, -1, 42) == 42, "legacy fallback");
        check(RecentsPolicy.taskId(-1, -1, -1) == -1, "missing ID stays invalid");
        check(RecentsPolicy.taskId(0, 4, 4) == 0, "zero is a valid task ID");
        check(RecentsPolicy.focusPage(PKG, PORT), "portrait countdown");
        check(RecentsPolicy.focusPage(PKG, LAND), "landscape countdown");
        check(RecentsPolicy.focusPage(PKG, INTRO), "portrait intro");
        check(RecentsPolicy.focusPage(PKG, PKG + ".usagestats.focusmode.land.FocusModeTimingLandActivity"), "landscape intro");
        check(!RecentsPolicy.focusPage("other.package", PORT), "same component text in another package not trusted");
        check(!RecentsPolicy.focusPage(PKG, PORT + "Fake"), "no class prefix matching");
        check(!RecentsPolicy.focusPage(PKG, PKG + ".usagestats.FocusSettingsMainActivity"), "settings setup alone remains visible");
        check(!RecentsPolicy.focusPage(PKG, PKG + ".password.appcontrol.ui.AccessCheckActivity"), "app-lock screen untouched");
        check(!RecentsPolicy.focusPage(PKG, PKG + ".usagestats.focusmode.FocusModeFinishActivity"), "completion/history untouched");
        check(!RecentsPolicy.focusPage(null, PORT), "null package");
        check(!RecentsPolicy.focusPage(PKG, null), "null class");
        check(RecentsPolicy.hide(42, RecentsPolicy.taskId(42, 42, -1), null, null, null, null), "current background task matches without deprecated ID");
        check(!RecentsPolicy.hide(-1, -1, null, null, null, null), "invalid IDs do not match each other");
        check(RecentsPolicy.hide(42, 8, PKG, PORT, null, null), "older portrait task also hidden");
        check(RecentsPolicy.hide(42, 9, PKG, LAND, null, null), "separate landscape task also hidden");
        check(RecentsPolicy.hide(42, 10, PKG, INTRO, null, null), "leftover intro task hidden");
        check(RecentsPolicy.hide(42, 11, "com.android.settings", "com.android.settings.Settings", PKG, PORT), "focus on top of owned shared settings task");
        check(!RecentsPolicy.hide(42, 12, PKG, PKG + ".usagestats.FocusSettingsMainActivity", null, null), "unrelated settings task preserved");
        check(!RecentsPolicy.hide(42, 13, PKG, PKG + ".password.appcontrol.ui.AccessCheckActivity", null, null), "other app-lock task preserved");
        check(!RecentsPolicy.hide(42, 14, "dev.lance.focusallowlist", "dev.lance.focusallowlist.MainActivity", null, null), "module home task preserved");
        System.out.println("PASS " + checks + " exact focus-page and persistent task identity checks");
    }
}
