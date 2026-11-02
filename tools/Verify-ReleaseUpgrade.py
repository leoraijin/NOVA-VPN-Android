"""Cloud-only acceptance of exact signed APKs. Synthetic data; never starts VPN."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from release_upgrade_ui import find_label, profile_disclosure, tab_selected

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--apks', type=Path, required=True)
parser.add_argument('--work', type=Path, required=True)
args = parser.parse_args()
if os.environ.get('GITHUB_ACTIONS') != 'true' or not re.fullmatch(r'emulator-\d+', args.serial):
    raise RuntimeError('Only a disposable GitHub Actions emulator is allowed')
args.work.mkdir(parents=True, exist_ok=False)
PACKAGE = 'com.nova.vpn.android'
COMPONENT = PACKAGE + '/com.nova.vpn.android.MainActivity'
PRIVATE = '/data/user/0/' + PACKAGE
EXPECTED = {'mode': 'INCLUDE_SELECTED', 'packages': ['com.android.settings'],
            'ipv6_enabled': True, 'sites': [{'domain': 'route-upgrade.invalid', 'target': 'DIRECT'}]}


def adb(*values, input_text=None, check=True, timeout=180):
    result = subprocess.run([args.adb, '-s', args.serial, *map(str, values)],
                            input=input_text, text=True, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError('Owned emulator command failed: ' + ' '.join(map(str, values)) + '\n' + result.stderr[-1000:])
    return result.stdout


def shell(*values, **kwargs):
    return adb('shell', ' '.join(shlex.quote(str(value)) for value in values), **kwargs)


def pause_until(description, condition, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = condition()
        if result:
            return result
        time.sleep(.5)
    raise RuntimeError('Timeout: ' + description)


def hierarchy():
    shell('uiautomator', 'dump', '/sdcard/nova-upgrade-ui.xml', timeout=30)
    return ET.fromstring(shell('cat', '/sdcard/nova-upgrade-ui.xml'))


def click(label):
    if tab_selected(hierarchy(), label):
        return
    point = pause_until('click ' + label, lambda: find_label(hierarchy(), label))
    shell('input', 'tap', *point)


def shown(label):
    return any(label in node.get('text', '') for node in hierarchy().iter('node'))


def show_profile_details(name):
    for _ in range(8):
        tree = hierarchy()
        labels = [node.get('text', '') for node in tree.iter('node')]
        if any('В избранное' in value or 'Убрать из избранного' in value for value in labels):
            return
        state = profile_disclosure(tree, name)
        point = find_label(tree, name)
        if state is True:
            return
        if state is False and point is not None:
            shell('input', 'tap', *point)
            return
        shell('input', 'swipe', '540', '1500', '540', '950', '300')
    raise RuntimeError('Cannot safely expand the specific profile card')

def screenshot(label):
    result = subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'],
                            capture_output=True, timeout=30, check=True)
    (args.work / (label + '.png')).write_bytes(result.stdout)


def details():
    # dumpsys renamed userId/appId across Android releases. Read the public UID listing
    # and cross-check actual data-directory ownership instead of guessing a dump field.
    listing = shell('cmd', 'package', 'list', 'packages', '-U', '--user', '0', PACKAGE)
    text = shell('dumpsys', 'package', PACKAGE)
    uid = re.search(r'^package:' + re.escape(PACKAGE) + r'\s+uid:(\d+)\s*$', listing, re.MULTILINE)
    version = re.search(r'\bversionCode=(\d+)', text)
    if uid is None or version is None:
        (args.work / 'package-details.txt').write_text(listing + '\n' + text)
        raise RuntimeError('Cannot identify exact installed package UID/version')
    owner = shell('stat', '-c', '%u', PRIVATE).strip()
    if not owner.isdecimal() or int(owner) != int(uid[1]):
        raise RuntimeError('Package UID differs from private directory owner')
    return int(uid[1]), int(version[1])


def preferences(name):
    tree = ET.fromstring(shell('cat', PRIVATE + '/shared_prefs/' + name + '.xml'))
    result = {}
    for node in tree:
        if node.tag == 'boolean':
            value = node.get('value') == 'true'
        elif node.tag == 'set':
            value = sorted(child.text for child in node)
        else:
            value = node.text or ''
        result[node.get('name')] = value
    return result


def xml_preferences(values):
    root = ET.Element('map')
    for key, value in values.items():
        if isinstance(value, bool):
            ET.SubElement(root, 'boolean', name=key, value=str(value).lower())
        elif isinstance(value, list):
            node = ET.SubElement(root, 'set', name=key)
            for item in value:
                ET.SubElement(node, 'string').text = item
        else:
            ET.SubElement(root, 'string', name=key).text = value
    return ET.tostring(root, encoding='unicode')


def seed_preferences(routes):
    uid, _ = details()
    directory = PRIVATE + '/shared_prefs'
    shell('mkdir', '-p', directory)
    documents = {'app_preferences': {'tutorial_dismissed': True, 'notification_permission_requested': True,
                                    'auto_connect': False, 'update_checks_enabled': False}}
    if routes:
        documents['routing'] = dict(EXPECTED, sites=json.dumps(EXPECTED['sites']))
    for name, values in documents.items():
        target = directory + '/' + name + '.xml'
        adb('shell', 'cat > ' + shlex.quote(target), input_text=xml_preferences(values))
        shell('chown', str(uid) + ':' + str(uid), target)
        shell('chmod', '600', target)
    shell('chown', str(uid) + ':' + str(uid), directory)
    shell('chmod', '700', directory)
    shell('restorecon', '-RF', directory)


def install(path):
    output = adb('install', '-r', path, timeout=240)
    if 'Success' not in output:
        raise RuntimeError('Package manager did not confirm installation')
    shell('pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')


def hashes():
    result = {}
    for relative in ('files/profiles.vault', 'shared_prefs/profile_selection.xml',
                     'shared_prefs/routing.xml', 'shared_prefs/app_preferences.xml'):
        output = shell('sha256sum', PRIVATE + '/' + relative)
        result[relative] = output.split()[0]
    return result


def check_routes():
    saved = preferences('routing')
    actual = {key: saved[key] for key in EXPECTED}
    actual['sites'] = json.loads(actual['sites'])
    if actual != EXPECTED:
        raise RuntimeError('Routing settings changed during update')


reports = []
try:
    if shell('getprop', 'ro.kernel.qemu').strip() != '1':
        raise RuntimeError('Not an emulator')
    abis = shell('getprop', 'ro.product.cpu.abilist').strip()
    if 'arm64-v8a' not in abis.split(','):
        raise RuntimeError('This image cannot install exact ARM64 APK; no forced ABI is allowed: ' + abis)
    (args.work / 'emulator-abis.txt').write_text(abis + '\n')
    adb('root')
    adb('wait-for-device')
    if shell('id', '-u').strip() != '0':
        raise RuntimeError('Disposable image must provide root for fixture storage inspection')
    shell('svc', 'power', 'stayon', 'true')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    for version in ('0.2.52', '0.2.54'):
        adb('uninstall', PACKAGE, check=False)
        install(args.apks / (version + '.apk'))
        seed_preferences(routes=True)
        name = 'NOVA_UPGRADE_' + version.replace('.', '_')
        link = 'vless://11111111-1111-4111-8111-111111111111@fixture.invalid:443?security=tls#' + name
        shell('am', 'start', '-W', '-a', 'android.intent.action.SEND', '-t', 'text/plain',
              '--es', 'android.intent.extra.TEXT', link, '-n', COMPONENT)
        click('Импортировать')
        click('Серверы')
        pause_until('imported profile', lambda: shown(name))
        favorite = False
        if version == '0.2.54':
            show_profile_details(name)
            for _ in range(6):
                tree = hierarchy()
                labels = [node.get('text', '') for node in tree.iter('node')]
                label = next((value for value in labels if 'В избранное' in value), None)
                if label and find_label(tree, label) is not None:
                    click(label)
                    favorite = True
                    break
                shell('input', 'swipe', '540', '1500', '540', '950', '300')
            if not favorite:
                raise RuntimeError('Could not seed favorite on 0.2.54')
            pause_until('favorite saved', lambda: shown('Убрать из избранного'))
        check_routes()
        selected = pause_until('selected id persisted', lambda: preferences('profile_selection').get('selected_profile_id'))
        shell('am', 'force-stop', PACKAGE)
        uid, old_code = details()
        before = hashes()
        install(args.apks / 'candidate.apk')
        new_uid, new_code = details()
        if new_uid != uid or new_code != 57 or new_code <= old_code or hashes() != before:
            raise RuntimeError('Package update did not preserve exact private data and UID')
        shell('am', 'start', '-W', '-n', COMPONENT)
        click('Серверы')
        pause_until('decrypted profile after update', lambda: shown(name))
        if favorite:
            show_profile_details(name)
            pause_until('favorite after update', lambda: shown('Убрать из избранного'))
        check_routes()
        if preferences('profile_selection').get('selected_profile_id') != selected:
            raise RuntimeError('Selected profile changed after update')
        screenshot('upgrade-' + version)
        reports.append({'baseline': version, 'candidate': '0.2.55', 'uid_preserved': True,
                        'encrypted_data_preserved_before_launch': True, 'decrypted_profile_visible': True,
                        'selected_profile_preserved': True, 'routes_preserved': True,
                        'favorite_preserved': favorite, 'vpn_started': False})
        (args.work / 'verification-partial.json').write_text(json.dumps(reports, indent=2) + '\n')
        print('PASS exact signed APK update ' + version + ' -> 0.2.55', flush=True)
    adb('uninstall', PACKAGE)
    install(args.apks / 'candidate.apk')
    seed_preferences(routes=False)
    shell('am', 'start', '-W', '-n', COMPONENT)
    click('Серверы')
    pause_until('empty fresh catalog', lambda: shown('Найдено: 0 из 0'))
    screenshot('clean-install')
    reports.append({'clean_install': True, 'version': '0.2.55', 'empty_catalog': True, 'vpn_started': False})
    (args.work / 'verification.json').write_text(json.dumps(reports, indent=2) + '\n')
    print('PASS exact signed APK clean install', flush=True)
except Exception:
    try:
        screenshot('failure')
        (args.work / 'failure-ui.xml').write_text(ET.tostring(hierarchy(), encoding='unicode'))
    except Exception:
        pass
    raise
finally:
    shell('am', 'force-stop', PACKAGE, check=False)
    (args.work / 'logcat.txt').write_text(adb('logcat', '-d', check=False))