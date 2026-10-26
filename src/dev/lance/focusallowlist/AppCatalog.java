package dev.lance.focusallowlist;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Display labels only; Policy.APPS remains the sole source of allow decisions. */
final class AppCatalog {
    static final Map<String, String> LABELS;
    static {
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put("com.openai.chatgpt", "ChatGPT");
        names.put("com.google.android.googlequicksearchbox", "Google（含圈定即搜）");
        names.put("md.obsidian", "Obsidian");
        names.put("com.chaoxing.mobile", "学习通");
        names.put("org.telegram.messenger", "Telegram · Play 版");
        names.put("org.telegram.messenger.web", "Telegram · 官网版");
        names.put("com.tencent.mm", "微信");
        names.put("com.tencent.mobileqq", "QQ");
        names.put("com.android.contacts", "通讯录与拨号");
        names.put("com.android.mms", "短信");
        names.put("com.google.android.dialer", "Google 电话");
        names.put("com.google.android.contacts", "Google 通讯录");
        names.put("com.google.android.apps.messaging", "Google 信息");
        names.put("com.baidu.drive.app", "百度网盘");
        names.put("com.intsig.camscanner", "扫描全能王");
        names.put("com.tencent.weread", "微信读书");
        names.put("cn.xuexi.android", "学习强国");
        names.put("com.eg.android.AlipayGphone", "支付宝");
        names.put("com.xiaomi.smarthome", "米家");
        names.put("com.deepseek.harness", "DSH");
        names.put("com.deepseek.harness.beta", "DSH · Lite");
        names.put("com.deepseek.harness.compat", "DSH · 兼容版");
        names.put("org.geogebra.android.calculator.suite", "GeoGebra 计算器套件");
        names.put("org.geogebra.android", "GeoGebra 图形计算器");
        names.put("org.geogebra.android.geometry", "GeoGebra 几何");
        names.put("org.geogebra.android.g3d", "GeoGebra 3D");
        names.put("org.geogebra.android.cascalc", "GeoGebra CAS");
        names.put("org.geogebra.android.scicalc", "GeoGebra 科学计算器");
        names.put("me.weishu.kernelsu", "KernelSU");
        names.put("moe.shizuku.privileged.api", "Shizuku");
        names.put("mark.via", "Via · 国内版");
        names.put("mark.via.gp", "Via · Play 版");
        names.put("bin.mt.plus", "MT 管理器");
        names.put("bin.mt.plus.canary", "MT 管理器 · Canary");
        names.put("com.omarea.vtools", "Scene");
        names.put("bin.mt.termex", "MT 终端扩展包");
        names.put("cn.wps.moffice_eng.xiaomi.lite", "小米文档查看器");
        names.put("com.android.calendar", "日历");
        names.put("com.android.soundrecorder", "录音机");
        names.put("com.flyersoft.moonreaderp", "静读天下专业版");
        names.put("com.github.android", "GitHub");
        names.put("com.github.catfriend1.syncthingandroid", "Syncthing-Fork");
        names.put("com.github.metacubex.clash.meta", "Clash Meta for Android");
        names.put("com.microsoft.office.excel", "Excel");
        names.put("com.microsoft.office.outlook", "Outlook");
        names.put("com.microsoft.office.powerpoint", "PowerPoint");
        names.put("com.microsoft.office.word", "Word");
        names.put("com.microsoft.skydrive", "OneDrive");
        names.put("com.microsoft.todos", "Microsoft To Do");
        names.put("com.mipay.wallet", "钱包");
        names.put("com.miui.calculator", "计算器");
        names.put("com.miui.gallery", "相册");
        names.put("com.miui.notes", "笔记");
        names.put("com.miui.weather2", "天气");
        names.put("com.safeincloud.free", "Safe");
        names.put("com.termux", "Termux");
        names.put("com.xiaomi.scanner", "AI 扫描");
        LABELS = Collections.unmodifiableMap(names);
    }
    static String label(String pkg) { String name = LABELS.get(pkg); return name == null ? pkg : name; }
    private AppCatalog() {}
}
