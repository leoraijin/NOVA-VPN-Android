"""Cloud-only restore crash and diagnostic UI acceptance; synthetic isolated package."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', required=True, type=Path)
parser.add_argument('--monitor-only', action='store_true')
args = parser.parse_args()
if os.environ.get('GITHUB_ACTIONS') != 'true' or not re.fullmatch(r'emulator-\d+', args.serial):
    raise RuntimeError('Only a disposable GitHub Actions emulator is allowed')
package = 'com.nova.vpn.android.usbtest.realitypreview'
component = package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'


def adb(*values, timeout=360):
    return subprocess.run([args.adb, '-s', args.serial, *values], capture_output=True,
                          text=True, encoding='utf-8', errors='replace', timeout=timeout)


qemu = adb('shell', 'getprop', 'ro.kernel.qemu', timeout=30)
if qemu.returncode or qemu.stdout.strip() != '1':
    raise RuntimeError('Not a disposable emulator')
args.work.mkdir(parents=True, exist_ok=False)


def instrument(name, flags, expected):
    result = adb('shell', 'am', 'instrument', '-w', '-r', *flags, component)
    output = result.stdout + result.stderr
    (args.work / (name + '.log')).write_text(output, encoding='utf-8')
    if result.returncode or expected not in output or 'INSTRUMENTATION_CODE: -1' not in output:
        raise RuntimeError(name + ' failed:\n' + output[-2200:])
    print('PASS ' + name, flush=True)


try:
    instrument('physical-monitor', ['-e', 'physicalNetworkMonitorOnly', 'true'],
               'PASS physical monitor lifecycle:')
    if args.monitor_only:
        (args.work / 'verification.json').write_text(json.dumps({
            'physical_monitor_lifecycle': True, 'registration_races': 40,
            'synthetic_data_only': True, 'physical_device_used': False,
        }, indent=2) + '\n', encoding='utf-8')
        sys.exit(0)
    instrument('synthetic-store', ['-e', 'subscriptionsOnly', 'true'],
               'PASS Android encrypted legacy migration')
    subprocess.run([sys.executable, str(Path(__file__).with_name('Verify-TransferCrash.py')),
                    '--adb', args.adb, '--serial', args.serial, '--work', str(args.work / 'restore')],
                   check=True, timeout=1200)
    for theme in ('LIGHT', 'DARK'):
        instrument('diagnostics-' + theme, ['-e', 'diagnosticsUiOnly', 'true',
                   '-e', 'diagnosticsTheme', theme.lower()],
                   'PASS Android diagnostic preview in all available designs: ' + theme)
        for style in ('ios_liquid_glass', 'material_you', 'one_ui', 'windows_11'):
            name = 'diagnostics-' + theme.lower() + '-' + style + '.png'
            result = adb('pull', f'/sdcard/Android/data/{package}/files/{name}', str(args.work / name), timeout=30)
            if result.returncode:
                raise RuntimeError('Missing diagnostic screenshot: ' + name)
    (args.work / 'verification.json').write_text(json.dumps({
        'eight_actual_process_death_boundaries': True,
        'five_restore_faults': True,
        'corrupt_startup_safe_recovery_ui': True,
        'diagnostic_themes': ['LIGHT', 'DARK'],
        'diagnostic_designs': ['IOS_LIQUID_GLASS', 'MATERIAL_YOU', 'ONE_UI', 'WINDOWS_11'],
        'synthetic_data_only': True, 'physical_device_used': False,
    }, indent=2) + '\n', encoding='utf-8')
finally:
    for name in ('restore-recovery-error.png', 'restore-recovery-missing.png'):
        adb('pull', f'/sdcard/Android/data/{package}/files/{name}', str(args.work / name), timeout=30)
    adb('shell', 'am', 'force-stop', package, timeout=30)
