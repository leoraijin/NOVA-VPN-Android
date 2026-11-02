"""Cloud-only library saved-state check across real Android background process death."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', required=True, type=Path)
args = parser.parse_args()
if os.environ.get('GITHUB_ACTIONS') != 'true' or not re.fullmatch(r'emulator-\d+', args.serial):
    raise RuntimeError('Only a disposable GitHub Actions emulator is allowed')
# Refuse before importing UI helpers or issuing any device command.
from release_upgrade_ui import find_label, tab_selected
PACKAGE = 'com.nova.vpn.android.usbtest.realitypreview'
COMPONENT = PACKAGE + '/com.nova.vpn.android.MainActivity'
QUERY = 'My fixture'


def adb(*values, check=True):
    result = subprocess.run([args.adb, '-s', args.serial, *map(str, values)], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=45)
    if check and result.returncode:
        raise RuntimeError('Isolated cloud command failed: ' + result.stderr[-600:])
    return result.stdout


def shell(*values, **kwargs):
    return adb('shell', ' '.join(shlex.quote(str(v)) for v in values), **kwargs)


def until(label, condition, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = condition()
        if isinstance(result, ET.Element) or result:
            return result
        time.sleep(.2)
    raise RuntimeError('Timeout: ' + label)


def tree():
    shell('uiautomator', 'dump', '/sdcard/nova-library-restart.xml')
    return ET.fromstring(shell('cat', '/sdcard/nova-library-restart.xml'))


def servers():
    if tab_selected(tree(), 'Серверы'):
        return
    point = until('servers tab', lambda: find_label(tree(), 'Серверы'))
    shell('input', 'tap', *point)


def search_field():
    return next((node for node in tree().iter('node')
                 if node.get('class', '').endswith('EditText') and node.get('enabled') == 'true'), None)


def has_query():
    field = search_field()
    return field is not None and field.get('text') == QUERY


if shell('getprop', 'ro.kernel.qemu').strip() != '1':
    raise RuntimeError('Not an emulator')
args.work.mkdir(parents=True, exist_ok=False)
try:
    # This runner uses only fixed synthetic data and an explicitly isolated package.
    seed = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'subscriptionsOnly', 'true',
               PACKAGE + '.test/com.nova.vpn.android.RealityCoreSmokeRunner')
    (args.work / 'seed.log').write_text(seed, encoding='utf-8')
    if 'PASS Android encrypted legacy migration' not in seed or 'INSTRUMENTATION_CODE: -1' not in seed:
        raise RuntimeError('Synthetic library setup failed')
    shell('am', 'start', '-W', '-n', COMPONENT)
    servers()
    field = until('search field', search_field)
    box = list(map(int, re.findall(r'\d+', field.get('bounds', ''))))
    if len(box) != 4:
        raise RuntimeError('Search field has no bounds')
    shell('input', 'tap', (box[0] + box[2]) // 2, (box[1] + box[3]) // 2)
    shell('input', 'text', 'My%sfixture')
    until('entered query', has_query)
    original_pid = until('original process', lambda: shell('pidof', PACKAGE, check=False).strip())
    shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    # onStop saves the task's Activity state; am kill removes the background process,
    # unlike force-stop which intentionally discards normal task restoration semantics.
    def kill_background():
        shell('am', 'kill', PACKAGE)
        return not shell('pidof', PACKAGE, check=False).strip()
    until('background process death', kill_background)
    shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN',
          '-c', 'android.intent.category.LAUNCHER', '-n', COMPONENT)
    new_pid = until('replacement process', lambda: shell('pidof', PACKAGE, check=False).strip())
    if original_pid == new_pid:
        raise RuntimeError('Process was not replaced')
    servers()
    until('query restored after process death', has_query)
    screenshot = subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'],
                                capture_output=True, timeout=30, check=True)
    (args.work / 'restored-search.png').write_bytes(screenshot.stdout)
    (args.work / 'verification.json').write_text(json.dumps({
        'background_process_replaced': True, 'activity_search_query_restored': True,
        'synthetic_data_only': True, 'physical_device_used': False,
        'scope': 'Activity task saved-state after am kill; not force-stop/reboot/TUN',
    }, indent=2) + '\n', encoding='utf-8')
    print('PASS library search restored across actual background process death', flush=True)
finally:
    try:
        (args.work / 'last-ui.xml').write_text(ET.tostring(tree(), encoding='unicode'), encoding='utf-8')
    except Exception:
        pass
    shell('am', 'force-stop', PACKAGE, check=False)
