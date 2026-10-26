"""Bounded mixed HTTP real-TUN tests against an already running local Xray fixture."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--frontend', required=True)
parser.add_argument('--backend-ca', required=True)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--work', type=Path, required=True)
parser.add_argument('--package', choices=('com.nova.vpn.android.usbtest.realitypreview',
    'com.nova.vpn.android.usbtest.completionpreview'), default='com.nova.vpn.android.usbtest.realitypreview')
parser.add_argument('--pairs', nargs='+', choices=('1-1', '1-2', '2-1', '1-3', '3-1', '2-2', '2-3', '3-2', '3-3'), default=['2-3', '3-2'])
parser.add_argument('--ipv6-first', action='store_true', help='Diagnostic control: reverse the two independent UID probes')
parser.add_argument('--core-trace', action='store_true', help='Synthetic-only native debug log; never use with provider profiles')
args = parser.parse_args()
args.work.mkdir(parents=True, exist_ok=False)
rows = []
for pair in dict.fromkeys(args.pairs):
    up, down = map(int, pair.split('-'))
    folder = args.work / f'h{up}-h{down}'
    folder.mkdir()
    with (folder / 'frontend.log').open('wb') as log:
        process = subprocess.Popen([args.frontend, '-backend-ca', args.backend_ca, '-work', str(folder),
            '-up-version', str(up), '-down-version', str(down), '-android-address', '10.0.2.2', '-seconds', '30'],
            stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            deadline = time.monotonic() + 10
            while not (folder / 'manifest.json').exists():
                assert process.poll() is None and time.monotonic() < deadline, 'Frontend not ready'
                time.sleep(.1)
            encoded = base64.b64encode((folder / 'manifest.json').read_bytes()).decode()
            result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                '-e', 'xhttpDownlinkOnly', 'true', '-e', 'xhttpTlsManifestBase64', encoded,
                '-e', 'xhttpIpv6First', str(args.ipv6_first).lower(),
                '-e', 'xhttpCoreTrace', str(args.core_trace).lower(),
                args.package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner'],
                capture_output=True, timeout=60)
            output = result.stdout + result.stderr
            (folder / 'android-smoke.log').write_bytes(output)
            text = output.decode('utf-8', errors='replace')
            # Preserve protocol counters even when Android fails.
            assert process.wait(timeout=40) == 0
            assert result.returncode == 0 and 'INSTRUMENTATION_CODE: -1' in text and 'smoke failed' not in text, text[-1800:]
            for mode in ('packet-up', 'stream-up', 'auto'):
                assert f'XHTTP {mode}: separate-UID IPv4/IPv6 TUN echo PASS' in text
            stats = json.loads((folder / 'requests.json').read_text())
            for leg in ('upload', 'download'):
                counters = stats[leg]
                assert counters['requests'] > 0 and counters['requests'] == counters['backend_h2'], stats
                assert all(counters[key] == 0 for key in ('wrong_leg', 'wrong_protocol', 'errors')), stats
            rows.append({'upload_http': up, 'download_http': down, 'counters': stats})
            print(f'PASS Android H{up}/H{down}: 3 modes, separate UID IPv4/IPv6, observed HTTP protocols', flush=True)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
(args.work / 'verification.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
