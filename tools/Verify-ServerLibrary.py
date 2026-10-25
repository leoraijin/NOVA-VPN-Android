"""Run the isolated 2000-server UI scenario for every current design."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', type=Path, required=True)
args = parser.parse_args()
args.work.mkdir(parents=True, exist_ok=False)
package = 'com.nova.vpn.android.usbtest.realitypreview'
for style in ('IOS_LIQUID_GLASS', 'MATERIAL_YOU', 'ONE_UI', 'WINDOWS_11'):
    result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                             '-e', 'serverLibraryStyle', style,
                             package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'],
                            capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=150)
    output = result.stdout + result.stderr
    (args.work / (style + '.log')).write_text(output, encoding='utf-8')
    assert result.returncode == 0 and f'PASS server library UI: {style}' in output \
        and 'INSTRUMENTATION_CODE: -1' in output, output[-1800:]
    screenshot = subprocess.run([args.adb, '-s', args.serial, 'pull',
        f'/sdcard/Android/data/{package}/files/server-library-{style}.png', str(args.work / (style + '.png'))],
        capture_output=True, timeout=30)
    assert screenshot.returncode == 0, screenshot.stderr
    print(f'PASS 2000-server library UI: {style}', flush=True)
