package dev.lance.focusallowlist;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Package decisions do not depend on Android user ID: Xiaomi clones share package names. */
public final class Policy {
    public static final String MODULE = "dev.lance.focusallowlist";
    public static final String TARGET = "com.xiaomi.misettings";
    public static final String VERSION = "16.4.260829.00";
    public static final int VERSION_CODE = 260829400;
    public static final Set<String> APPS = set(
        "com.openai.chatgpt", "com.google.android.googlequicksearchbox", "md.obsidian", "com.chaoxing.mobile",
        "org.telegram.messenger", "org.telegram.messenger.web",
        "com.tencent.mm", "com.tencent.mobileqq", "com.android.contacts", "com.android.mms",
        "com.google.android.dialer", "com.google.android.contacts", "com.google.android.apps.messaging",
        "com.baidu.drive.app", "com.intsig.camscanner",
        "com.tencent.weread", "cn.xuexi.android", "com.eg.android.AlipayGphone", "com.xiaomi.smarthome",
        "com.deepseek.harness", "com.deepseek.harness.beta", "com.deepseek.harness.compat",
        "org.geogebra.android.calculator.suite", "org.geogebra.android", "org.geogebra.android.geometry",
        "org.geogebra.android.g3d", "org.geogebra.android.cascalc", "org.geogebra.android.scicalc",
        "me.weishu.kernelsu", "moe.shizuku.privileged.api", "mark.via", "mark.via.gp", "bin.mt.plus", "bin.mt.plus.canary",
        "com.omarea.vtools",
        "bin.mt.termex",
        "cn.wps.moffice_eng.xiaomi.lite",
        "com.android.calendar",
        "com.android.soundrecorder",
        "com.flyersoft.moonreaderp",
        "com.github.android",
        "com.github.catfriend1.syncthingandroid",
        "com.github.metacubex.clash.meta",
        "com.microsoft.office.excel",
        "com.microsoft.office.outlook",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.word",
        "com.microsoft.skydrive",
        "com.microsoft.todos",
        "com.mipay.wallet",
        "com.miui.calculator",
        "com.miui.gallery",
        "com.miui.notes",
        "com.miui.weather2",
        "com.safeincloud.free",
        "com.termux",
        "com.xiaomi.scanner");
    public static final Set<String> COMPONENTS = set(
        MODULE, TARGET, "android", "com.android.systemui", "com.android.settings",
        "com.miui.home", "com.miui.securitycenter", "com.miui.securityadd",
        "com.android.phone", "com.android.server.telecom", "com.android.incallui",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.miui.packageinstaller", "com.lbe.security.miui",
        "com.android.documentsui", "com.google.android.documentsui", "com.android.camera",
        "com.miui.touchassistant", "com.android.deskclock");
    public static final Set<String> PRESERVED_SETTINGS = set(
        "force_fsg_nav_bar", "touch_assistant_enabled", "double_click_power_key",
        "miui_slider_tool_choice", "mode_ringer", "screentime_turn_off_ringer",
        "lock_screen_allow_private_notifications", "lock_screen_show_notifications",
        "disallow_key_menu", "disallow_key_home", "disallow_key_back",
        "long_press_back_key", "long_press_home_key", "long_press_menu_key");

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
    public static boolean named(String pkg) {
        return pkg != null && (APPS.contains(pkg) || COMPONENTS.contains(pkg));
    }
    public static boolean allowed(String pkg, boolean system, int appId,
                                  boolean hasLauncher, boolean inputMethod) {
        return named(pkg) || inputMethod || (system && (appId < 10000 || !hasLauncher));
    }
    private Policy() {}
}
