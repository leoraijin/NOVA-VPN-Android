"""Verify saved fixture evidence, not universal Android/XHTTP compatibility."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--core-sha256', required=True)
parser.add_argument('--patch-sha256', required=True)
args = parser.parse_args()
workspace = args.workspace.resolve(strict=True)
root = workspace / 'work/xhttp-android-distinct-tls'
rows = []
for upload, download in [(2, 2), (1, 2), (2, 1), (1, 1)]:
    folder = root / f'h{upload}-h{download}'
    manifest = json.loads((folder / 'manifest.json').read_text())
    stats = json.loads((folder / 'requests.json').read_text())
    log = (folder / 'android-smoke.log').read_text(encoding='utf-8-sig')
    assert manifest['upload']['version'] == upload and manifest['download']['version'] == download
    for field in ['ca', 'server_name', 'port', 'path']:
        assert manifest['upload'][field] != manifest['download'][field], field
    for leg in ['upload', 'download']:
        counters = stats[leg]
        assert counters['requests'] > 0 and counters['backend_h2'] == counters['requests'], (folder, leg)
        assert all(counters[key] == 0 for key in ['wrong_leg', 'wrong_protocol', 'errors']), (folder, leg)
    for mode in ['packet-up', 'stream-up', 'auto']:
        assert f'XHTTP {mode}: separate-UID IPv4/IPv6 TUN echo PASS' in log, (folder, mode)
    assert 'INSTRUMENTATION_CODE: -1' in log and 'smoke failed' not in log
    rows.append({'upload_http': upload, 'download_http': download, 'modes': 3, 'counters': stats,
                 'android_log_sha256': hashlib.sha256((folder / 'android-smoke.log').read_bytes()).hexdigest()})

negative = root / 'download-refusal'
log = (negative / 'android-smoke.log').read_text(encoding='utf-8-sig')
assert log.count('XHTTP packet-up: separate-UID IPv4/IPv6 TUN echo PASS') == 2
assert 'XHTTP download TLS identity refusal: IPv4/IPv6 blocked, no working fallback PASS' in log
assert 'INSTRUMENTATION_CODE: -1' in log and 'smoke failed' not in log
stats = json.loads((negative / 'requests.json').read_text())
for leg in ['upload', 'download']:
    counters = stats[leg]
    assert counters['requests'] > 0 and counters['backend_h2'] > 0
    assert counters['wrong_leg'] == 0 and counters['wrong_protocol'] == 0
    # An intentional TLS failure can cancel its paired upload. Preserve error
    # counts; do not relabel this fault-injection run as an error-free session.
    assert counters['errors'] >= 0 and counters['backend_h2'] + counters['errors'] == counters['requests']

apk = workspace / 'source/app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk'
with zipfile.ZipFile(apk) as archive:
    core = hashlib.sha256(archive.read('lib/x86_64/libbox.so')).hexdigest()
assert core == args.core_sha256
patch = hashlib.sha256((workspace / 'source/native/xhttp/core-downlink.patch').read_bytes()).hexdigest()
assert patch == args.patch_sha256
report = {'http_pairs': rows, 'download_tls_control_refusal_control': True, 'negative_counters': stats,
          'libbox_elf_sha256': core, 'native_patch_sha256': patch,
          'limits': ['x86_64 Android 15 4KB pages', 'TCP ADB reverse', 'echo through real TUN with separate UID',
                     'negative run retains observed backend error count', 'no HTTP3/Reality/ARM/bootstrap-DNS/long-session claim']}
(root / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('PASS: 4 TLS HTTP pairs x 3 modes x IPv4/IPv6 real TUN; download TLS control/refusal/control; counters and exact native hashes')
