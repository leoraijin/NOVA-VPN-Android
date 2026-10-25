"""Kill only the isolated Android test process at real restore commit boundaries."""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', type=Path, required=True)
args = parser.parse_args()
args.work.mkdir(parents=True, exist_ok=False)
package = 'com.nova.vpn.android.usbtest.realitypreview'
component = package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'

def adb(*arguments):
    return subprocess.run([args.adb, '-s', args.serial, *arguments], capture_output=True,
                          text=True, encoding='utf-8', errors='replace', timeout=90)

def phase(name, crash=False):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'transferCrashPhase', name, component)
    output = result.stdout + result.stderr
    (args.work / (name + '.log')).write_text(output, encoding='utf-8')
    if crash:
        assert 'Process crashed' in output or 'process crash' in output.lower(), output[-1200:]
        assert 'FAIL transfer crash phase' not in output, output[-1200:]
    else:
        assert f'PASS transfer crash phase: {name}' in output and 'INSTRUMENTATION_CODE: -1' in output, output[-1200:]
    return output

assert adb('shell', 'am', 'force-stop', package).returncode == 0
phase('prepare')
results = []
for cut in range(8):
    assert adb('shell', 'am', 'force-stop', package).returncode == 0
    phase(f'cut-{cut}', crash=True)
    assert adb('shell', 'am', 'force-stop', package).returncode == 0
    receipt = adb('shell', 'cat', f'/sdcard/Android/data/{package}/files/restore-crash-{cut}.json')
    assert receipt.returncode == 0, receipt.stderr
    checked = json.loads(receipt.stdout)
    assert checked['cut'] == cut and checked['journal_checked'] and checked['cleartext_markers_absent']
    assert len(checked['journal_sha256']) == 64
    verify = phase('verify')
    # Preserve independent verification output for every cut.
    (args.work / f'verify-{cut}.log').write_text(verify, encoding='utf-8')
    results.append({'cut': cut, 'actual_process_death': True, 'journal_verified_before_process_death': True,
                    'journal_sha256': checked['journal_sha256'],
                    'damaged_profile_target': checked['damaged_profile_target'],
                    'startup_rollback_matches_original': True, 'recovery_idempotent': True})
    (args.work / 'verification.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print(f'PASS actual Android process death / startup restore at commit boundary {cut}', flush=True)
for fault in ('apply', 'rollback', 'recovery', 'corrupt-journal', 'journal-delete'):
    assert adb('shell', 'am', 'force-stop', package).returncode == 0
    phase('fault-' + fault)
    print(f'PASS same-process restore fault: {fault}', flush=True)
phase('corrupt-startup-prepare')
assert adb('shell', 'am', 'force-stop', package).returncode == 0
phase('corrupt-startup-ui')
print('PASS corrupt-journal startup screen, safe retry/code copy, healthy retry returns to app', flush=True)
phase('cleanup')
print('PASS eight crash boundaries, five faults and corrupt-startup UI; isolated package only', flush=True)
