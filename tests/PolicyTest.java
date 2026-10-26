package dev.lance.focusallowlist;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        for (String app : new String[]{"com.openai.chatgpt", "md.obsidian", "com.chaoxing.mobile",
            "com.tencent.mm", "com.tencent.mobileqq", "com.android.contacts", "com.android.mms"}) {
            check(Policy.allowed(app, false, 10200, true, false), "main user: " + app);
        }
        check(Policy.named("com.miui.securitycenter"), "native application lock/hidden entry");
        check(Policy.named("com.miui.home"), "launcher");
        check(Policy.named("com.android.systemui"), "system UI");
        check(Policy.allowed("vendor.permission.dialog", true, 10015, false, false), "system component without launcher");
        check(Policy.allowed("vendor.system.core", true, 1000, true, false), "system UID component");
        check(Policy.allowed("example.keyboard", false, 10090, true, true), "third party input method");
        check(!Policy.allowed("com.tencent.qqmusic", false, 10101, true, false), "no Tencent prefix exemption");
        check(!Policy.allowed("com.tencent.mm.fake", false, 10101, true, false), "exact package matching");
        check(!Policy.allowed("com.deepseek.harness.fake", false, 10101, true, false), "DSH variants are exact names");
        check(!Policy.allowed("org.geogebra.android.fake", false, 10101, true, false), "GeoGebra variants are exact names");
        check(!Policy.allowed("com.baidu.searchbox", false, 10101, true, false), "Baidu browser not exempt");
        check(!Policy.allowed("cn.com.langeasy.easylisten", false, 10101, true, false), "case-sensitive package name");
        check(!Policy.allowed("com.miui.video", true, 10200, true, false), "preinstalled entertainment not exempt");
        check(!Policy.allowed("example.reader", false, 10101, false, false), "non-system no-launcher app not exempt");
        check(!Policy.allowed(null, false, 10101, false, false), "unknown package not exempt");
        check(Policy.PRESERVED_SETTINGS.contains("force_fsg_nav_bar"), "native gesture mode preserved");
        check(!Policy.PRESERVED_SETTINGS.contains("settings_focus_mode_status"), "native focus state retained");
        check(!Policy.PRESERVED_SETTINGS.contains("misettings_key_enter_focus_time"), "native clock retained");
        System.out.println("PASS " + checks + " policy and boundary checks");
    }
}
