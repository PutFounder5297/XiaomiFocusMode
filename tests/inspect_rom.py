"""Check the user-supplied ROM APIs and cleanup branches. No phone execution."""
import sys
from zipfile import ZipFile
from loguru import logger
logger.remove()
from androguard.core.dex import DEX, HiddenApiClassDataItem
# This ROM uses hidden-api flag values newer than this analyzer's enums.
HiddenApiClassDataItem.DomapiApiFlag = staticmethod(lambda value: value)
HiddenApiClassDataItem.RestrictionApiFlag = staticmethod(lambda value: value)

def load(path):
    classes = {}
    with ZipFile(path) as z:
        for name in z.namelist():
            if name.endswith('.dex'):
                classes.update({c.get_name(): c for c in DEX(z.read(name)).get_classes()})
    return classes

framework, services = [load(path) for path in sys.argv[1:3]]
native = framework['Lmiui/process/ProcessManagerNative;']
method = next(m for m in native.get_methods() if m.get_name() == 'getDefault')
assert method.get_descriptor() == '()Lmiui/process/IProcessManager;'
assert 'public' in method.get_access_flags_string() and 'static' in method.get_access_flags_string()
api = framework['Lmiui/process/IProcessManager;']
signatures = {(m.get_name(), m.get_descriptor()) for m in api.get_methods()}
assert ('isLockedApplication', '(Ljava/lang/String; I)Z') in signatures
assert ('updateApplicationLockedState', '(Ljava/lang/String; I Z)V') in signatures

def instructions(cn, name):
    method = next(m for m in services[cn].get_methods() if m.get_name() == name)
    result = {}; offset = 0
    for i in method.get_instructions():
        result[offset] = i
        offset += i.get_length()
    return result

def branch(items, key):
    start, switch = next((off, i) for off, i in items.items() if i.get_name() == 'packed-switch')
    payload = items[start + switch.get_ref_off() * 2]
    return start + payload.get_targets()[key - payload.first_key] * 2

reason = instructions('Lcom/android/server/am/ProcessCleanerBase;', 'getKillReason')
# Select the int overload, first in this ROM, verified by the constants below.
onekey = next(off for off, i in reason.items() if '"OneKeyClean"' in i.get_output())
swipe = next(off for off, i in reason.items() if '"SwipeUpClean"' in i.get_output())
assert branch(reason, 1) == onekey
assert branch(reason, 7) == swipe
white = instructions('Lcom/android/server/am/ProcessManagerService;', 'isInWhiteList')
onekey_start, swipe_start = branch(white, 1), branch(white, 7)
assert onekey_start != swipe_start
onekey_text = '\n'.join(i.get_output() for off, i in white.items() if onekey_start <= off < onekey_start + 128)
assert '->isLockedApplication(' in onekey_text
swipe_text = '\n'.join(i.get_output() for off, i in white.items() if swipe_start <= off < onekey_start)
assert '->isLockedApplication(' not in swipe_text
permission = '\n'.join(i.get_output() for i in instructions('Lcom/android/server/am/ProcessManagerService;', 'checkPermission').values())
assert '10000' in permission and '2000' in permission
update = '\n'.join(i.get_output() for i in instructions('Lcom/android/server/am/ProcessManagerService;', 'updateApplicationLockedState').values())
assert '->checkPermission()' in update and '->updateApplicationLockedState(' in update
print('PASS: 3 native lock API signatures; OneKeyClean respects app lock, SwipeUpClean has a different branch; privileged permission gate')
