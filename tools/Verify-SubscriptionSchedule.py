"""Exercise real WorkManager reconciliation in the isolated Android test package."""
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
                          text=True, encoding='utf-8', errors='replace', timeout=240)

def phase(name):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'subscriptionSchedulePhase', name, component)
    output = result.stdout + result.stderr
    (args.work / (name + '.log')).write_text(output, encoding='utf-8')
    assert result.returncode == 0 and f'PASS subscription schedule phase: {name}' in output \
        and 'INSTRUMENTATION_CODE: -1' in output, output[-1800:]
    print(f'PASS subscription schedule: {name}', flush=True)

phase('normal')
phase('stale-snapshot')
phase('stale-prune')
phase('terminal-retry')
phase('prepare-boot')
assert adb('shell', 'am', 'force-stop', package).returncode == 0
phase('verify-boot')
(args.work / 'verification.json').write_text(json.dumps({
    'import_prunes_obsolete_work': True,
    'import_updates_interval_preserving_work_id': True,
    'cold_process_start_repairs_without_activity': True,
    'unrelated_work_preserved': True,
    'concurrent_snapshot_change_rejected': True,
    'concurrent_prune_preserves_revived_work_id': True,
    'real_terminal_retry_exhaustion_allows_same_scope_retry': True,
    'late_failure_preserves_new_cache_owner': True,
    'test_state_restored': True,
}, indent=2) + '\n', encoding='utf-8')
