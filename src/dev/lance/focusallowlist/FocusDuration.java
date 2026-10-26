package dev.lance.focusallowlist;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Scoped to two native focus dialogs and the native timing intro; never writes session settings. */
final class FocusDuration {
    private static final String KEY = "keyFocusModeTimeIndex";
    private static final String WATCHER = "dev.lance.focusallowlist.durationWatcher";
    private static final String RANGE = "请输入 1–" + DurationPolicy.MAX + " 分钟（整数）";

    static void validate(Class<?> settings, Class<?> fragment, Class<?> timing, Class<?> adapter, Class<?> utils) throws Exception {
        settings.getDeclaredMethod("p", String.class);
        fragment.getDeclaredMethod("I", String.class);
        for (Class<?> owner : new Class<?>[]{settings, fragment}) owner.getDeclaredMethod("f", String.class);
        for (String field : new String[]{"A", "B", "C", "s", "x"}) settings.getDeclaredField(field);
        for (String field : new String[]{"G", "H", "I", "C", "D"}) fragment.getDeclaredField(field);
        adapter.getDeclaredMethod("e", String.class);
        utils.getDeclaredMethod("j", Context.class);
        timing.getDeclaredMethod("onCreate", Bundle.class);
        if (timing.getDeclaredField("d").getType() != int.class) throw new IllegalStateException("duration field changed");
    }

    static void install(List<XC_MethodHook.Unhook> hooks, Class<?> settings, Class<?> fragment,
                        Class<?> timing, final Class<?> utils) {
        dialog(hooks, settings, "p", "A", "B", "C", "s", "x", utils);
        dialog(hooks, fragment, "I", "G", "H", "I", "C", "D", utils);
        hooks.add(XposedHelpers.findAndHookMethod(timing, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    Intent intent = ((Activity) h.thisObject).getIntent();
                    if (intent == null) return;
                    int actual = intent.getIntExtra(KEY, 0);
                    if (!DurationPolicy.valid(actual) || actual <= 180) return;
                    h.setObjectExtra("durationIntent", intent);
                    h.setObjectExtra("actualMinutes", Integer.valueOf(actual));
                    // Only onCreate contains the hard-coded >180 finish guard. Run super and
                    // all native initialization, then restore before onStart starts the intro.
                    intent.putExtra(KEY, 180);
                } catch (Throwable t) { log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam h) {
                Integer actual = (Integer) h.getObjectExtra("actualMinutes");
                if (actual == null) return;
                try {
                    ((Intent) h.getObjectExtra("durationIntent")).putExtra(KEY, actual.intValue());
                    if (!h.hasThrowable()) XposedHelpers.setIntField(h.thisObject, "d", actual.intValue());
                } catch (Throwable t) {
                    log(t);
                    ((Activity) h.thisObject).finish(); // Do not commit a substituted 180-minute session.
                }
            }
        }));
        hooks.add(XposedHelpers.findAndHookMethod(timing, "o", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam h) {
                try {
                    int minutes = XposedHelpers.getIntField(h.thisObject, "d");
                    if (minutes == -100 || DurationPolicy.valid(minutes)) return; // Preserve native one-minute trial.
                } catch (Throwable t) { log(t); }
                h.setResult(null);
                Activity activity = (Activity) h.thisObject;
                Toast.makeText(activity, RANGE, Toast.LENGTH_LONG).show();
                activity.finish();
            }
        }));
    }

    private static void dialog(List<XC_MethodHook.Unhook> hooks, Class<?> owner, String method,
                               final String inputField, final String tipField, final String okField,
                               final String adapterField, final String dialogField, final Class<?> utils) {
        hooks.add(XposedHelpers.findAndHookMethod(owner, method, String.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam h) {
                if (h.hasThrowable()) return;
                try {
                    final Object screen = h.thisObject;
                    final EditText input = (EditText) XposedHelpers.getObjectField(screen, inputField);
                    final TextView tip = (TextView) XposedHelpers.getObjectField(screen, tipField);
                    final Button ok = (Button) XposedHelpers.getObjectField(screen, okField);
                    final Object adapter = XposedHelpers.getObjectField(screen, adapterField);
                    final Dialog nativeDialog = (Dialog) XposedHelpers.getObjectField(screen, dialogField);
                    if (input == null || tip == null || ok == null || adapter == null || nativeDialog == null) return;
                    ArrayList<InputFilter> filters = new ArrayList<InputFilter>();
                    for (InputFilter filter : input.getFilters())
                        if (!(filter instanceof InputFilter.LengthFilter)) filters.add(filter);
                    filters.add(new InputFilter.LengthFilter(5));
                    input.setFilters(filters.toArray(new InputFilter[filters.size()]));
                    input.setHint(RANGE);
                    tip.setText(RANGE);
                    Object previous = XposedHelpers.getAdditionalInstanceField(input, WATCHER);
                    Object oldWatcher = previous instanceof WeakReference ? ((WeakReference<?>) previous).get() : null;
                    if (oldWatcher instanceof TextWatcher) input.removeTextChangedListener((TextWatcher) oldWatcher);
                    TextWatcher watcher = new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) { update(input, tip, ok); }
                    };
                    input.addTextChangedListener(watcher);
                    // Xposed's extra-field map must not retain a watcher that strongly owns
                    // its weak-map key (the input view). The view itself owns this watcher.
                    XposedHelpers.setAdditionalInstanceField(input, WATCHER, new WeakReference<TextWatcher>(watcher));
                    ok.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            int minutes = DurationPolicy.parse(input.getText());
                            if (minutes == 0) { update(input, tip, ok); return; }
                            try {
                                Context context = input.getContext();
                                // Refuse even saving a preset while a session is occupied.
                                if (android.provider.Settings.Global.getInt(context.getContentResolver(),
                                        "settings_focus_mode_status", 0) == 1) {
                                    Toast.makeText(context, "当前专注进行中，请结束后再设置时长", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String value = String.valueOf(minutes);
                                Object existing = XposedHelpers.callStaticMethod(utils, "j", context);
                                if (!(existing instanceof List)) throw new IllegalStateException("native timer list changed");
                                if (((List<?>) existing).contains(value)) {
                                    Toast.makeText(context, "该时长已存在，请在原生列表中选择", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                // Native adapter persists the preset, selects it and calls screen.f.
                                // Never start focus here or write the running duration/start time.
                                XposedHelpers.callMethod(adapter, "e", value);
                                nativeDialog.dismiss();
                            } catch (Throwable t) {
                                log(t);
                                Toast.makeText(input.getContext(), "时长设置失败，请关闭弹窗后重试", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    // The native dialog may have populated an existing >3-digit value before
                    // our LengthFilter was installed. Restore that argument after installing it.
                    if (h.args[0] instanceof String && DurationPolicy.parse((String) h.args[0]) > 0)
                        input.setText((String) h.args[0]);
                    update(input, tip, ok);
                } catch (Throwable t) { log(t); }
            }
        }));
    }
    private static void update(EditText input, TextView tip, Button ok) {
        boolean valid = DurationPolicy.parse(input.getText()) > 0;
        ok.setEnabled(valid);
        tip.setVisibility(input.length() == 0 || valid ? View.GONE : View.VISIBLE);
    }
    private static void log(Throwable t) { XposedBridge.log("[FocusAllowlist 0.1.20] custom duration: " + t); }
    private FocusDuration() {}
}
