"""Verify delete-all persistence and UI using synthetic data in an isolated package."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', type=Path, required=True)
parser.add_argument('--package', choices=('com.nova.vpn.android.usbtest.realitypreview',
    'com.nova.vpn.android.usbtest.completionpreview'), default='com.nova.vpn.android.usbtest.realitypreview')
args = parser.parse_args()
args.work.mkdir(parents=True, exist_ok=False)
component = args.package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'

for flag, expected in (
    ('deleteAllStoreOnly', 'PASS delete all store:'),
    ('deleteAllOnly', 'PASS delete all configurations:'),
):
    result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
        '-e', flag, 'true', component], capture_output=True, text=True,
        encoding='utf-8', errors='replace', timeout=240)
    output = result.stdout + result.stderr
    (args.work / (flag + '.log')).write_text(output, encoding='utf-8')
    # adb may return zero even when instrumentation reports a failed assertion.
    if result.returncode != 0 or expected not in output or 'INSTRUMENTATION_CODE: -1' not in output:
        raise RuntimeError(flag + ' failed:\n' + output[-1800:])
    print('PASS ' + flag, flush=True)

screenshot = subprocess.run([args.adb, '-s', args.serial, 'pull',
    f'/sdcard/Android/data/{args.package}/files/delete-all-empty.png',
    str(args.work / 'delete-all-empty.png')], capture_output=True, timeout=30)
if screenshot.returncode != 0:
    raise RuntimeError('Missing delete-all UI screenshot')