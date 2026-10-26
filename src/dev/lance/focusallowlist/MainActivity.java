package dev.lance.focusallowlist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only launcher UI. Native focus owns timing and application enforcement. */
public final class MainActivity extends Activity {
    private static final String HOME = "home", APPS = "apps", GUIDE = "guide", ABOUT = "about";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private ScrollView scroll;
    private String page = HOME;
    private volatile boolean destroyed;
    private boolean loading, expanded, compatible;
    private List<AppEntry> installed, undetected;
    private Bitmap art;
    private String targetVersion = "未检测到", moduleVersion = "0.1.23-test";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("专注模式");
        try { moduleVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) {}
        try {
            PackageInfo target = getPackageManager().getPackageInfo(Policy.TARGET, 0);
            targetVersion = target.versionName;
            compatible = Policy.VERSION.equals(target.versionName) && target.versionCode == Policy.VERSION_CODE;
        } catch (Exception ignored) {}

        // Target SDK 35 is edge-to-edge. Apply real insets, including landscape navigation.
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color("page_background"));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        body = column();
        body.setPadding(dp(24), dp(20), dp(24), dp(28));
        int width = getResources().getConfiguration().screenWidthDp > 640 ? dp(640) : -1;
        frame.addView(body, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        scroll.addView(frame);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new android.window.OnBackInvokedCallback() {
                    @Override public void onBackInvoked() { navigateBack(); }
                });
        }
        if (state != null) expanded = state.getBoolean("expanded", false);
        show(state == null ? HOME : state.getString("page", HOME));
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page);
        state.putBoolean("expanded", expanded);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() { navigateBack(); }
    private void navigateBack() { if (HOME.equals(page)) finish(); else show(HOME); }
    @Override protected void onDestroy() {
        destroyed = true;
        reader.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private void show(String destination) {
        page = destination;
        body.removeAllViews();
        if (APPS.equals(page)) apps();
        else if (GUIDE.equals(page)) guide();
        else if (ABOUT.equals(page)) about();
        else { page = HOME; home(); }
        scroll.scrollTo(0, 0);
    }
    private void home() {
        body.addView(text("F O C U S", 11, "text_muted", true));
        add(body, text("专注模式", 30, "text_primary", true), 6);
        add(body, text("把时间留给重要的事", 14, "text_secondary", false), 6);
        LinearLayout hero = card("hero_background");
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView picture = new ImageView(this);
        if (art == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            art = BitmapFactory.decodeResource(getResources(), resource("module_art", "drawable"), options);
        }
        picture.setImageBitmap(art);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackground(shape("hero_background", 18));
        picture.setClipToOutline(true);
        picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(picture, new LinearLayout.LayoutParams(dp(148), dp(126)));
        TextView title = text("专心，做一件事", 23, "text_primary", true);
        title.setGravity(Gravity.CENTER);
        add(hero, title, 10);
        TextView caption = text("保留原生计时，让必要应用随时可用", 13, "text_secondary", false);
        caption.setGravity(Gravity.CENTER);
        add(hero, caption, 10);
        Button open = button("进入专注  →", true);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openNative(); }
        });
        add(hero, open, 22);
        TextView returning = text("正在专注时，将返回当前计时", 12, "text_secondary", false);
        returning.setGravity(Gravity.CENTER);
        add(hero, returning, 12);
        add(body, hero, 26);

        LinearLayout links = card("surface");
        links.setPadding(0, 0, 0, 0);
        links.addView(link("icon_apps", "允许的应用", "查看专注时可用的应用", APPS));
        divider(links);
        links.addView(link("icon_guide", "使用说明", "启用、时长与返回方式", GUIDE));
        divider(links);
        links.addView(link("icon_info", "关于与帮助", "版本信息与问题排查", ABOUT));
        add(body, links, 20);

        LinearLayout status = row();
        status.setGravity(Gravity.CENTER);
        status.addView(icon(compatible ? "icon_check" : "icon_info", compatible ? "status_good" : "status_warning", 16));
        TextView statusText = text(compatible ? "小米设置版本匹配" : "当前小米设置版本未适配", 12,
            compatible ? "text_secondary" : "status_warning", false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.leftMargin = dp(6);
        status.addView(statusText, statusParams);
        add(body, status, 24);
    }
    private void openNative() {
        try { startActivity(new Intent().setClassName(Policy.TARGET, FocusNavigation.SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception error) { Toast.makeText(this, "暂时无法打开，请从系统设置进入专注模式", Toast.LENGTH_LONG).show(); }
    }
    private void apps() {
        header("允许的应用", "专注时，也能使用必要的工具。");
        note("固定允许名单", "名单为预设内容。微信、QQ 的原生分身同样放行，应用锁与隐藏状态仍按原来的方式使用。");
        if (installed == null) {
            LinearLayout waiting = row();
            waiting.setGravity(Gravity.CENTER_VERTICAL);
            ProgressBar progress = new ProgressBar(this);
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(color("accent")));
            waiting.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView label = text("正在读取应用列表…", 14, "text_secondary", false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.leftMargin = dp(12);
            waiting.addView(label, params);
            add(body, waiting, 28);
            loadApps();
            return;
        }
        section("已检测到 · " + installed.size());
        if (installed.isEmpty()) note("暂未检测到应用", "隐藏应用或其他用户中的分身可能不显示，预设名单仍然保留。");
        else {
            LinearLayout list = card("surface");
            list.setPadding(0, dp(4), 0, dp(4));
            for (int i = 0; i < installed.size(); i++) {
                if (i > 0) divider(list);
                list.addView(appRow(installed.get(i)));
            }
            add(body, list, 12);
        }
        if (!undetected.isEmpty()) {
            Button toggle = button((expanded ? "收起" : "查看") + "未检测到的预设应用（" + undetected.size() + "）", false);
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final int position = scroll.getScrollY();
                    expanded = !expanded;
                    show(APPS);
                    scroll.post(new Runnable() {
                        @Override public void run() { if (APPS.equals(page)) scroll.scrollTo(0, position); }
                    });
                }
            });
            add(body, toggle, 20);
            if (expanded) {
                LinearLayout list = card("surface");
                list.setPadding(0, dp(4), 0, dp(4));
                for (int i = 0; i < undetected.size(); i++) {
                    if (i > 0) divider(list);
                    list.addView(appRow(undetected.get(i)));
                }
                add(body, list, 12);
            }
        }
        add(body, text("隐藏应用、分身与不同发行版本可能影响检测结果。系统界面、输入法、电话和文件选择器等必要组件另行保留。", 12, "text_muted", false), 20);
    }
    private void loadApps() {
        if (loading) return;
        loading = true;
        final PackageManager pm = getApplicationContext().getPackageManager();
        reader.execute(new Runnable() {
            @Override public void run() {
                final List<AppEntry> found = new ArrayList<AppEntry>();
                final List<AppEntry> missing = new ArrayList<AppEntry>();
                for (String pkg : Policy.APPS) {
                    if (Thread.currentThread().isInterrupted()) return;
                    AppEntry item = new AppEntry(pkg, AppCatalog.label(pkg));
                    try {
                        PackageInfo info = pm.getPackageInfo(pkg, 0);
                        item.name = String.valueOf(pm.getApplicationLabel(info.applicationInfo));
                        try { item.icon = pm.getApplicationIcon(info.applicationInfo); }
                        catch (Exception ignored) {} // Icon failure does not turn an installed app into a missing one.
                        item.disabled = !info.applicationInfo.enabled;
                        found.add(item);
                    } catch (Exception error) { missing.add(item); }
                }
                final Collator collator = Collator.getInstance(Locale.CHINA);
                Comparator<AppEntry> order = new Comparator<AppEntry>() {
                    @Override public int compare(AppEntry a, AppEntry b) { return collator.compare(a.name, b.name); }
                };
                Collections.sort(found, order);
                Collections.sort(missing, order);
                if (destroyed) return;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        installed = found;
                        undetected = missing;
                        loading = false;
                        if (APPS.equals(page)) show(APPS);
                    }
                });
            }
        });
    }
    private View appRow(final AppEntry item) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(18), dp(14), dp(18), dp(14));
        ImageView image = new ImageView(this);
        image.setImageDrawable(item.icon == null ? getDrawable(resource("icon_apps", "drawable")) : item.icon);
        if (item.icon == null) image.setImageTintList(android.content.res.ColorStateList.valueOf(color("text_muted")));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(image, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout labels = column();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(14);
        line.addView(labels, params);
        labels.addView(text(item.name, 15, "text_primary", true));
        if (item.disabled) add(labels, text("已在系统中停用", 12, "text_muted", false), 4);
        line.setBackground(ripple("surface", 0));
        line.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new AlertDialog.Builder(MainActivity.this).setTitle(item.name)
                    .setMessage("已列入专注模式的固定允许名单。\n\n包名\n" + item.pkg)
                    .setPositiveButton("知道了", null).show();
            }
        });
        return line;
    }
    private void guide() {
        header("使用说明", "从设置到专注，只需要几步。");
        step("01", "启用模块", "在 LSPosed 中启用「专注模式」，作用域只选择「小米设置」，然后重启手机。");
        step("02", "选择一段时间", "首页点「进入专注」，在原生时长列表中选择或添加时间。支持 1–35791 整数分钟，也可先使用一分钟体验。");
        step("03", "专注，也能使用工具", "开始后回到桌面，打开允许的应用。其余应用仍受原生专注限制；输入法、电话和必要系统组件可正常使用。");
        section("常用操作");
        note("怎么返回计时页面？", "从本应用首页点「进入专注」，或点击专注通知。会返回原来的计时，专注卡片不会显示在最近任务中。");
        note("可以调整正在运行的专注吗？", "时长在开始前设置。开始后保留原来的倒计时，不提供提前结束、重置计时或自由修改允许名单的入口。");
        note("隐藏应用与分身怎么打开？", "继续使用小米原来的隐藏入口和密码、指纹验证。白名单放行不会跳过应用锁。");
        note("怎么停用模块？", "等当前专注结束，再在 LSPosed 中关闭模块并重启。不要卸载或清除「小米设置」的数据。");
    }
    private void about() {
        header("关于与帮助", "版本与支持信息。");
        LinearLayout identity = card("surface");
        identity.addView(text("专注模式", 23, "text_primary", true));
        add(identity, text("版本 " + moduleVersion, 13, "text_secondary", false), 8);
        add(identity, text("保留小米原生专注，允许必要应用。", 14, "text_secondary", false), 16);
        add(body, identity, 20);
        section("适配信息");
        note(compatible ? "小米设置版本匹配" : "当前版本未适配", "当前版本：" + targetVersion + "\n适配版本：" + Policy.VERSION + "\n\n这里仅核对系统应用版本。模块是否启用，请在 LSPosed 中确认。");
        LinearLayout actions = card("surface");
        actions.setPadding(0, 0, 0, 0);
        View release = action("icon_info", "更新说明", "查看本次改动");
        release.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new AlertDialog.Builder(MainActivity.this).setTitle("本次更新")
                    .setMessage("修复原生专注任务退到后台后可能仍显示在最近任务中的处理遗漏。\n\n保留原来的计时、固定允许名单、后台保护和超级岛显示。")
                    .setPositiveButton("知道了", null).show();
            }
        });
        actions.addView(release);
        divider(actions);
        View help = action("icon_guide", "问题排查", "故障反馈与设备信息");
        help.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { diagnostics(); }
        });
        actions.addView(help);
        add(body, actions, 20);
        section("隐私");
        note("本地运行", "不联网，不读取聊天记录或笔记内容。应用列表只用于展示固定名单中的本机应用，不会上传。");
    }
    private void diagnostics() {
        new AlertDialog.Builder(this).setTitle("问题排查")
            .setMessage("先在 LSPosed 中确认模块已启用、作用域为「小米设置」，并在升级后重启。\n\n反馈故障时，请提供操作步骤、发生时间，以及 LSPosed 导出的本次模块日志。计时在走不一定表示应用限制仍在生效。\n\n界面版本为 " + moduleVersion + "；沿用的核心日志前缀仍为 [FocusAllowlist 0.1.20]。")
            .setPositiveButton("复制设备信息", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String value = "专注模式 " + moduleVersion + "\n设备：" + Build.MANUFACTURER + " " + Build.MODEL
                        + "\nAndroid：" + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）"
                        + "\n小米设置：" + targetVersion + "\n适配版本：" + Policy.VERSION
                        + "\n版本匹配：" + (compatible ? "是" : "否") + "\n核心日志前缀：[FocusAllowlist 0.1.20]";
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("专注模式设备信息", value));
                        Toast.makeText(MainActivity.this, "设备信息已复制", Toast.LENGTH_SHORT).show();
                    }
                }
            }).setNegativeButton("关闭", null).show();
    }
    private void header(String title, String subtitle) {
        FrameLayout hit = new FrameLayout(this);
        hit.addView(icon("icon_back", "text_primary", 24), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        hit.setContentDescription("返回首页");
        hit.setBackground(ripple("page_background", 24));
        hit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { show(HOME); } });
        body.addView(hit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        add(body, text(title, 28, "text_primary", true), 12);
        add(body, text(subtitle, 14, "text_secondary", false), 8);
    }
    private View link(String glyph, String title, String subtitle, final String destination) {
        View view = action(glyph, title, subtitle);
        view.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { show(destination); } });
        return view;
    }
    private View action(String glyph, String title, String subtitle) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(76));
        line.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(shape("icon_background", 13));
        iconBox.addView(icon(glyph, "accent", 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        line.addView(iconBox, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout labels = column();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(13);
        params.rightMargin = dp(8);
        labels.addView(text(title, 16, "text_primary", true));
        add(labels, text(subtitle, 12, "text_secondary", false), 5);
        line.addView(labels, params);
        line.addView(icon("icon_chevron", "text_muted", 18));
        line.setBackground(ripple("surface", 0));
        return line;
    }
    private void note(String title, String message) {
        LinearLayout panel = card("surface");
        panel.addView(text(title, 16, "text_primary", true));
        add(panel, text(message, 14, "text_secondary", false), 10);
        add(body, panel, 16);
    }
    private void step(String number, String title, String message) {
        LinearLayout panel = card("surface");
        panel.addView(text(number, 12, "accent", true));
        add(panel, text(title, 18, "text_primary", true), 8);
        add(panel, text(message, 14, "text_secondary", false), 10);
        add(body, panel, 16);
    }
    private void section(String value) { add(body, text(value, 13, "text_muted", true), 26); }
    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(primary ? 17 : 13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setAllCaps(false);
        button.setTextColor(color(primary ? "on_accent" : "accent"));
        button.setBackgroundTintList(null);
        button.setBackground(ripple(primary ? "accent" : "icon_background", 16));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setMinimumHeight(dp(56));
        button.setMinHeight(dp(56));
        button.setStateListAnimator(null);
        return button;
    }
    private TextView text(String value, int size, String palette, boolean strong) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color(palette));
        label.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        label.setIncludeFontPadding(false);
        label.setLineSpacing(dp(4), 1.08f);
        return label;
    }
    private ImageView icon(String name, String palette, int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource(name, "drawable"));
        image.setImageTintList(android.content.res.ColorStateList.valueOf(color(palette)));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card(String palette) {
        LinearLayout view = column();
        view.setPadding(dp(22), dp(22), dp(22), dp(22));
        view.setBackground(shape(palette, 24));
        view.setClipToOutline(true);
        return view;
    }
    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(color("divider"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(73);
        params.rightMargin = dp(18);
        parent.addView(line, params);
    }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }
    private GradientDrawable shape(String palette, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(palette));
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private Drawable ripple(String palette, int radius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(color("ripple")), shape(palette, radius), mask);
    }
    private int color(String name) { return getColor(resource(name, "color")); }
    private int resource(String name, String type) { return getResources().getIdentifier(name, type, getPackageName()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class AppEntry {
        final String pkg;
        String name;
        Drawable icon;
        boolean disabled;
        AppEntry(String pkg, String name) { this.pkg = pkg; this.name = name; }
    }
}
