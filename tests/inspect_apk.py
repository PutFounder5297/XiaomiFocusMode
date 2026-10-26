"""Optional APK/target structural check. Requires androguard; not an Android runtime test."""
import sys
import zipfile
import pathlib
import re
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX

module = sys.argv[1]
original = sys.argv[2]
a = APK(module)
assert a.get_package() == 'dev.lance.focusallowlist'
assert a.get_permissions() == [], a.get_permissions()
assert a.get_min_sdk_version() == '28'
assert a.get_target_sdk_version() == '35'
assert a.get_androidversion_name() == '0.1.23-test'
assert a.get_androidversion_code() == '24'
x = a.get_android_manifest_xml()
ns = '{http://schemas.android.com/apk/res/android}'
policy_source = (pathlib.Path(__file__).resolve().parents[1] / 'src/dev/lance/focusallowlist/Policy.java').read_text()
app_block = policy_source.split('APPS = set(', 1)[1].split(');', 1)[0]
apps = set(re.findall(r'"([^"]+)"', app_block))
queries = {q.get(ns+'name') for q in x.findall('./queries/package')}
assert queries == apps | {'com.xiaomi.misettings'}, (queries, apps)
meta = {m.get(ns+'name'): (m.get(ns+'value') or m.get(ns+'resource'))
        for m in x.findall('./application/meta-data')}
assert meta['xposedmodule'] == 'true'
assert meta['xposedminversion'] == '82'
app = x.find('./application')
assert app.get(ns+'icon') and app.get(ns+'roundIcon') == app.get(ns+'icon')
resources = a.get_android_resources()
assert resources.get_id(a.get_package(), int(app.get(ns+'theme')[1:], 16))[1] == 'ModuleTheme'
public = __import__('xml.etree.ElementTree', fromlist=['ElementTree']).fromstring(resources.get_public_resources(a.get_package()))
resource_names = {(n.get('type'), n.get('name')) for n in public}
for name in ['page_background', 'surface', 'hero_background', 'icon_background', 'text_primary',
             'text_secondary', 'text_muted', 'accent', 'on_accent', 'divider', 'ripple', 'status_good', 'status_warning']:
    assert ('color', name) in resource_names, 'dynamic UI color missing: ' + name
for name in ['icon_apps', 'icon_guide', 'icon_info', 'icon_back', 'icon_check', 'icon_chevron', 'module_art']:
    assert ('drawable', name) in resource_names, 'dynamic UI image missing: ' + name
catalog = (pathlib.Path(__file__).resolve().parents[1] / 'src/dev/lance/focusallowlist/AppCatalog.java').read_text()
assert set(re.findall(r'names.put\("([^"]+)"', catalog)) == apps, 'display names must match fixed policy'
with zipfile.ZipFile(module) as z:
    assert z.read('assets/xposed_init').decode().strip() == 'dev.lance.focusallowlist.FocusHook'
    assert z.read('res/drawable-nodpi-v4/module_art.jpg') == (pathlib.Path(__file__).resolve().parents[1] / 'res/drawable-nodpi/module_art.jpg').read_bytes()
    icon = a.get_android_resources().get_resolved_res_configs(int(app.get(ns+'icon')[1:], 16))
    assert icon, 'launcher icon must resolve'
    dex = DEX(z.read('classes.dex'))
    names = [c.get_name() for c in dex.get_classes()]
    main_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/MainActivity')]
    ui_text = '\n'.join(i.get_output() for c in main_classes for m in c.get_methods() for i in m.get_instructions())
    assert '->startActivity(' in ui_text and '->startService(' not in ui_text
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', ui_text)
    assert '->getOnBackInvokedDispatcher(' in ui_text
    assert apps <= set(dex.get_strings()), 'whitelist packages missing from built DEX'
    assert 'Ldev/lance/focusallowlist/FocusDuration;' in names
    assert 'Ldev/lance/focusallowlist/DurationPolicy;' in names
    duration_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/FocusDuration')]
    duration_instructions = '\n'.join(i.get_output() for c in duration_classes for m in c.get_methods() for i in m.get_instructions())
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', duration_instructions)
    assert '->putExtra(' in duration_instructions and '->setIntField(' in duration_instructions
    assert '->getText(' in duration_instructions and '->dismiss(' in duration_instructions
    assert '->removeTextChangedListener(' in duration_instructions and '->addTextChangedListener(' in duration_instructions
    assert '->startActivity(' not in duration_instructions
    assert 'Ldev/lance/focusallowlist/FocusHook;' in names
    assert 'Ldev/lance/focusallowlist/FocusIsland;' in names
    assert 'Ldev/lance/focusallowlist/IslandRefreshLoop;' in names
    hook_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/FocusHook')]
    recovery_calls = [(c.get_name(), m.get_name(), i.get_output()) for c in hook_classes for m in c.get_methods() for i in m.get_instructions() if 'Ldev/lance/focusallowlist/FocusIsland;->resume(' in i.get_output()]
    assert len(recovery_calls) >= 4, 'process, focus page create/resume and settings return must recover display'
    monitor_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/FocusMonitor')]
    assert monitor_classes and 'Ldev/lance/focusallowlist/MonitorRecovery;' in names
    monitor_text = '\n'.join(i.get_output() for c in monitor_classes for m in c.get_methods() for i in m.get_instructions())
    assert 'Landroid/content/Context;->startService(' in monitor_text
    assert '->startForeground(' not in monitor_text and '->startActivity(' not in monitor_text
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', monitor_text)
    assert 'Landroid/os/SystemClock;->elapsedRealtime(' in monitor_text
    assert 'Ldev/lance/focusallowlist/MonitorRecovery;->request(' in monitor_text
    assert 'Ldev/lance/focusallowlist/FocusHook;->checkForeground(' in monitor_text
    monitor_calls = [i.get_output() for c in hook_classes for m in c.get_methods() for i in m.get_instructions() if 'Ldev/lance/focusallowlist/FocusMonitor;->resume(' in i.get_output()]
    assert len(monitor_calls) >= 5
    assert 'onTaskRemoved' in dex.get_strings()
    foreground_check = next(m for c in hook_classes for m in c.get_methods() if m.get_name() == 'checkForeground')
    foreground_text = '\n'.join(i.get_output() for i in foreground_check.get_instructions())
    assert 'userId' in foreground_text and '->myUid(' in foreground_text and '->allow(' in foreground_text
    assert '->callStaticMethod(' in foreground_text and '->startActivity(' not in foreground_text
    assert not any('MonitorRecoveryTest' in n for n in names)
    assert not any('ProcessLockLeaseTest' in n for n in names)
    assert 'Ldev/lance/focusallowlist/ProcessLockLease;' in names
    guard_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/FocusProcessGuard')]
    guard_text = '\n'.join(i.get_output() for c in guard_classes for m in c.get_methods() for i in m.get_instructions())
    assert 'Landroid/os/HandlerThread;->start(' in guard_text
    assert 'Landroid/content/SharedPreferences$Editor;->commit(' in guard_text
    assert 'Landroid/content/ContentResolver;->registerContentObserver(' in guard_text
    assert 'miui.process.ProcessManagerNative' in dex.get_strings()
    assert 'isLockedApplication' in dex.get_strings() and 'updateApplicationLockedState' in dex.get_strings()
    assert not {'protectCurrentProcess', 'onePushUpdateWhitelist'} & set(dex.get_strings())
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', guard_text)
    assert '->startService(' not in guard_text and 'Ljava/lang/Runtime;' not in guard_text
    guard_main = dex.get_class('Ldev/lance/focusallowlist/FocusProcessGuard;')
    guard_resume = next(m for m in guard_main.get_methods() if m.get_name() == 'resume')
    guard_resume_text = '\n'.join(i.get_output() for i in guard_resume.get_instructions())
    assert '->getProcessName(' in guard_resume_text and '->myUid(' in guard_resume_text
    assert '->commit(' not in guard_resume_text and '->invoke(' not in guard_resume_text
    navigation = dex.get_class('Ldev/lance/focusallowlist/FocusNavigation;')
    hidden = next(m for m in navigation.get_methods() if m.get_name() == 'hideInRecents')
    hidden_text = '\n'.join(i.get_output() for i in hidden.get_instructions())
    assert '->setExcludeFromRecents(' in hidden_text
    assert '->taskId ' in hidden_text and '->persistentId ' in hidden_text
    assert 'Ldev/lance/focusallowlist/RecentsPolicy;->hide(' in hidden_text
    assert '->getTaskInfo(' in hidden_text and '->getFlags(' in hidden_text
    assert 'hideInRecents' in dex.get_strings() and 'showInRecents' not in dex.get_strings()
    assert 'Ldev/lance/focusallowlist/RecentsPolicy;' in names
    assert not any('RecentsPolicyTest' in n for n in names)
    recents = dex.get_class('Ldev/lance/focusallowlist/FocusRecents;')
    assert recents is not None
    recents_text = '\n'.join(i.get_output() for m in recents.get_methods() for i in m.get_instructions())
    assert '->registerActivityLifecycleCallbacks(' in recents_text
    assert '->focusPage(' in recents_text and '->hideInRecents(' in recents_text
    for callback in ['onActivityCreated', 'onActivityResumed', 'onActivityPaused', 'onActivityStopped',
                     'onActivityPostCreated', 'onActivityPostResumed', 'onActivityPostPaused', 'onActivityPostStopped']:
        method = next(m for m in recents.get_methods() if m.get_name() == callback)
        assert any('->update(' in i.get_output() for i in method.get_instructions()), callback
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', recents_text + hidden_text)
    assert '->finishAndRemoveTask(' not in recents_text + hidden_text
    assert '->startActivity(' not in recents_text + hidden_text
    assert '->startService(' not in recents_text + hidden_text
    # Regression for the recorded SystemUI ModuleButtonViewHolder crash:
    # No action/button renderer or vendor timer is used in the replacement template.
    assert not {'miui.focus.actions', 'timerInfo', 'timerSystemCurrent', 'canShowFocus'} & set(dex.get_strings())
    assert {'miui.focus.pics', 'miui.focus.param', 'buttons-free-v2'} <= set(dex.get_strings())
    island_classes = [c for c in dex.get_classes() if c.get_name().startswith('Ldev/lance/focusallowlist/FocusIsland')]
    island_instructions = '\n'.join(i.get_output() for c in island_classes for m in c.get_methods() for i in m.get_instructions())
    assert not re.search(r'Landroid/provider/Settings\$(?:System|Global|Secure);->put', island_instructions)
    assert 'Landroid/app/Application;->getProcessName(' in island_instructions
    assert 'Ldev/lance/focusallowlist/IslandRefreshLoop;->resume(' in island_instructions
    assert 'Ldev/lance/focusallowlist/IslandRefreshLoop;->stop(' in island_instructions
    assert 'Ljava/lang/ref/WeakReference;' not in island_instructions, 'display no longer expires with service owner'
    assert '->startForeground(' not in island_instructions and '->startService(' not in island_instructions
    island_main = dex.get_class('Ldev/lance/focusallowlist/FocusIsland;')
    service_stop = next(m for m in island_main.get_methods() if m.get_name() == 'onServiceDestroyed')
    service_stop_text = '\n'.join(i.get_output() for i in service_stop.get_instructions())
    assert '->resume(' in service_stop_text and '->clear(' not in service_stop_text
    assert 'Landroid/app/NotificationManager;->cancel(' in island_instructions
    assert 'Landroid/app/NotificationManager;->notify(' in island_instructions
    assert 'Landroid/content/ContentResolver;->registerContentObserver(' in island_instructions
    assert 'Landroid/content/ContentResolver;->unregisterContentObserver(' in island_instructions
    producer = next(m for c in island_classes for m in c.get_methods() if m.get_name() == 'publish')
    producer_text = '\n'.join(i.get_output() for i in producer.get_instructions())
    assert 'Landroid/app/NotificationManager;->notify(' in producer_text
    assert 'Lorg/json/' not in island_instructions
    assert '->addExtras(' in producer_text and '->putParcelable(' in producer_text
    assert 'Ldev/lance/focusallowlist/IslandTemplate;->build(' in producer_text
    assert 'miui.focus.actions' not in island_instructions
    assert 'Landroid/app/Notification$Action' not in island_instructions
    assert '->addAction(' not in island_instructions
    assert '->setContentIntent(' in producer_text and '->setChronometerCountDown(' in producer_text
    cleanup = next(m for c in island_classes for m in c.get_methods() if m.get_name() == 'cleanupLegacy')
    cleanup_text = '\n'.join(i.get_output() for i in cleanup.get_instructions())
    assert 'miui.focus.param' in cleanup_text and '->containsKey(' in cleanup_text
    assert '->getTag(' in cleanup_text and '->getId(' in cleanup_text
    assert 'buttons-free-v2' in cleanup_text and '->getString(' in cleanup_text
    assert '->cancelAll(' not in island_instructions
    assert not any(n.startswith('Lde/robv/android/xposed/') for n in names)
    assert not any('PolicyTest' in n for n in names)
    assert not any('RegressionTest' in n for n in names)
    assert not any('IslandTemplateDump' in n for n in names)
    assert not any('IslandRefreshLoopTest' in n for n in names)
p = APK(original)
assert p.get_androidversion_name() == '16.4.260829.00'
assert p.get_androidversion_code() == '260829400'
native_activities = {node.get(ns+'name'): node for node in p.get_android_manifest_xml().findall('./application/activity')}
settings_entry = native_activities['com.xiaomi.misettings.usagestats.FocusSettingsMainActivity']
assert settings_entry.get(ns+'exported') == 'true' and not settings_entry.get(ns+'permission')
for name in ['com.xiaomi.misettings.usagestats.focusmode.port.FocusModePortActivity',
             'com.xiaomi.misettings.usagestats.focusmode.land.FocusModeLandActivity']:
    node = native_activities[name]
    assert node.get(ns+'launchMode') == '2', 'existing task reuse requires singleTask'
    assert node.get(ns+'excludeFromRecents') == 'true'
target_app = p.get_android_manifest_xml().find('./application')
for node in target_app:
    name = node.get(ns+'name', '')
    if node.tag in ('activity', 'service') and ('focusmode.port.FocusModePortActivity' in name or 'focusmode.land.FocusModeLandActivity' in name or 'FocusModeForeBackGroundMonitorService' in name):
        assert node.get(ns+'process', target_app.get(ns+'process', p.get_package())) == p.get_package(), 'display ownership requires native main process'
d = DEX(zipfile.ZipFile(original).read('classes.dex'))
service = 'Lcom/xiaomi/misettings/usagestats/focusmode/service/FocusModeForeBackGroundMonitorService;'
expected = {
    service: [('a', '('+service+' Ljava/lang/String;)V'), ('d', '()V'),
              ('c', '()Ljava/lang/String;'), ('onCreate', '()V'),
              ('onStartCommand', '(Landroid/content/Intent; I I)I'), ('onDestroy', '()V')],
    'Lae/b;': [('onForegroundWindowChanged', '(Lmiui/process/ForegroundInfo;)V')],
    'Lf7/b;': [('C', '(Landroid/content/Context; I)V')],
    'Lbe/e;': [('o', '(Landroid/content/Context;)Z'), ('g', '(Landroid/content/Context;)V'), ('j', '(Landroid/content/Context;)Ljava/util/ArrayList;')],
    'Lxd/i;': [('e', '(Ljava/lang/String;)V')],
    'Lbe/b;': [('run', '()V')],
    'Lbe/c;': [('run', '()V')],
    'Lyd/a;': [('a', '(Landroid/content/Context;)V')],
    'Lcom/xiaomi/misettings/usagestats/FocusSettingsMainActivity;':
        [('onCreate', '(Landroid/os/Bundle;)V'), ('onResume', '()V'), ('q', '()V'), ('p', '(Ljava/lang/String;)V'), ('f', '(Ljava/lang/String;)V')],
    'Lcom/xiaomi/misettings/usagestats/focusmode/FocusSettingsFragment;':
        [('onViewCreated', '(Landroid/view/View; Landroid/os/Bundle;)V'), ('onResume', '()V'),
         ('E', '(Lcom/xiaomi/misettings/usagestats/focusmode/FocusSettingsFragment;)V'),
         ('I', '(Ljava/lang/String;)V'), ('f', '(Ljava/lang/String;)V')],
    'Lcom/xiaomi/misettings/usagestats/focusmode/FocusModeActivityBase;':
        [('onCreate', '(Landroid/os/Bundle;)V'), ('onResume', '()V'), ('onNewIntent', '(Landroid/content/Intent;)V')],
    'Lcom/xiaomi/misettings/usagestats/focusmode/FocusModeTimingActivityBase;': [('o', '()V'), ('onCreate', '(Landroid/os/Bundle;)V')],
    'Lcom/xiaomi/misettings/usagestats/delegate/ActivityManagerdDelegate;':
        [('forceStopPackage', '(Landroid/app/ActivityManager; Ljava/lang/String;)V')],
    'Lcom/xiaomi/misettings/usagestats/delegate/ProcessManagerDelegate;':
        [('registerForegroundWindowListener', '(Lmiui/process/IForegroundWindowListener;)V'),
         ('unregisterForegroundWindowListener', '(Lmiui/process/IForegroundWindowListener;)V')]
}
count = 0
for cn, methods in expected.items():
    c = d.get_class(cn)
    assert c is not None, cn
    seen = {(m.get_name(), m.get_descriptor()) for m in c.get_methods()}
    for m in methods:
        assert m in seen, (cn, m)
        count += 1
native_monitor = d.get_class(service)
assert not any(m.get_name() == 'onTaskRemoved' for m in native_monitor.get_methods()), 'callback hook must match supported inherited method'
for method, marker in [('onCreate', '->registerForegroundWindowListener('), ('onDestroy', '->unregisterForegroundWindowListener(')]:
    text = '\n'.join(i.get_output() for m in native_monitor.get_methods() if m.get_name() == method for i in m.get_instructions())
    assert marker in text
assert ('c', 'Lae/b;') in {(f.get_name(), f.get_descriptor()) for f in native_monitor.get_fields()}
# Reentry relies on native keyCanWrite=false bypassing initial record/effect setup.
base = d.get_class('Lcom/xiaomi/misettings/usagestats/focusmode/FocusModeActivityBase;')
create = next(m for m in base.get_methods() if m.get_name() == 'onCreate')
create_text = '\n'.join(i.get_output() for i in create.get_instructions())
assert 'keyCanWrite' in create_text and 'Lbe/b;-><init>' in create_text
timing = d.get_class('Lcom/xiaomi/misettings/usagestats/focusmode/FocusModeTimingActivityBase;')
start = next(m for m in timing.get_methods() if m.get_name() == 'o')
start_text = '\n'.join(i.get_output() for i in start.get_instructions())
for key in ['misettings_focus_limit_time', 'misettings_focus_limit_milli',
            'misettings_key_enter_focus_time', 'settings_focus_mode_status', 'settings_experience_count']:
    assert key in start_text, 'final start gate must precede these native writes: ' + key
duration_create = next(m for m in timing.get_methods() if m.get_name() == 'onCreate')
duration_create_text = '\n'.join(i.get_output() for i in duration_create.get_instructions())
assert 'keyFocusModeTimeIndex' in duration_create_text and '180' in duration_create_text
assert '->o()V' not in duration_create_text, 'start must occur after restoring actual duration'
assert 'mul-long' in '\n'.join(i.get_name() for i in start.get_instructions())
for cn, fields_to_check in [
    ('Lcom/xiaomi/misettings/usagestats/FocusSettingsMainActivity;', {'A': 'Lmiuix/androidbasewidget/widget/StateEditText;', 'B': 'Landroid/widget/TextView;', 'C': 'Landroid/widget/Button;', 's': 'Lxd/i;', 'x': 'Lmiuix/appcompat/app/p;'}),
    ('Lcom/xiaomi/misettings/usagestats/focusmode/FocusSettingsFragment;', {'G': 'Lmiuix/androidbasewidget/widget/StateEditText;', 'H': 'Landroid/widget/TextView;', 'I': 'Landroid/widget/Button;', 'C': 'Lxd/i;', 'D': 'Lmiuix/appcompat/app/p;'}),
    ('Lcom/xiaomi/misettings/usagestats/focusmode/FocusModeTimingActivityBase;', {'d': 'I'})]:
    fields = {(f.get_name(), f.get_descriptor()) for f in d.get_class(cn).get_fields()}
    for field, descriptor in fields_to_check.items(): assert (field, descriptor) in fields, (cn, field)
for cn, field, descriptor in [('Lcom/xiaomi/misettings/usagestats/FocusSettingsMainActivity;', 'k', 'Landroid/widget/TextView;'),
                  ('Lcom/xiaomi/misettings/usagestats/focusmode/FocusSettingsFragment;', 'n', 'Landroid/widget/TextView;'),
                  ('Lbe/b;', 'a', 'I'), ('Lbe/c;', 'a', 'I'), ('Lyd/a;', 'a', 'Ljava/lang/String;'),
                  (service, 'g', 'Lae/a;'), (service, 'a', 'Landroid/os/Handler;'),
                  (service, 'b', 'Ljava/lang/String;'), ('Lae/b;', 'a', service)]:
    fields = {(f.get_name(), f.get_descriptor()) for f in d.get_class(cn).get_fields()}
    assert (field, descriptor) in fields, (cn, field)
# Guard the R8 task IDs used for origin marking against the supplied native bytecode.
for cn, id, marker in [('Lbe/b;', 0, 'toggleRinger'), ('Lbe/b;', 3, 'setFocusModeInfo'),
                       ('Lbe/b;', 4, 'ensureFinishFocusMode'), ('Lbe/b;', 5, 'screentime_turn_off_ringer'),
                       ('Lbe/b;', 6, 'ensureStartObserverRinger'), ('Lbe/c;', 0, 'forceStopFocusMode')]:
    m = next(m for m in d.get_class(cn).get_methods() if m.get_name() == 'run')
    ins = {}; offset = 0
    for item in m.get_instructions():
        ins[offset] = item; offset += item.get_length()
    sw, instruction = next((off, item) for off, item in ins.items() if item.get_name() == 'packed-switch')
    payload = ins[sw + instruction.get_ref_off() * 2]
    start = sw + payload.get_targets()[id - payload.first_key] * 2
    preview = '\n'.join(item.get_output() for off, item in ins.items() if start <= off < start + 120)
    assert marker in preview, (cn, id, marker)
print('PASS:', len(apps), 'whitelist packages, manifest, permissions, Xposed entry, DEX packaging; target version and', count, 'native method signatures')
