"""Verify 2000-node library, rotation and Activity recreation in eight appearances."""
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
    for theme in ('LIGHT', 'DARK'):
        label = style + '-' + theme
        result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                                 '-e', 'serverLibraryStyle', style, '-e', 'serverLibraryTheme', theme,
                                 package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'],
                                capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=240)
        output = result.stdout + result.stderr
        (args.work / (label + '.log')).write_text(output, encoding='utf-8')
        assert result.returncode == 0 and f'PASS server library UI: {style}' in output \
            and 'INSTRUMENTATION_CODE: -1' in output, output[-1800:]
        for stage in ('portrait', 'landscape', 'recreated'):
            screenshot = subprocess.run([args.adb, '-s', args.serial, 'pull',
                f'/sdcard/Android/data/{package}/files/server-library-{label}-{stage}.png',
                str(args.work / (label + '-' + stage + '.png'))], capture_output=True, timeout=30)
            assert screenshot.returncode == 0, screenshot.stderr
        print(f'PASS 2000-server library, rotation and Activity recreation: {label}', flush=True)
