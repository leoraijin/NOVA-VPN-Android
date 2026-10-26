"""Direct official Xray TLS/Reality ingress through a disposable Android emulator TUN.

Run Run-XhttpAndroidFixture.py with --probe-tls and --backend-h2/--backend-h3
first. For Reality use --probe-reality on the fixture and --version 2 --reality
here; --reality-key-refusal checks a wrong download key. No frontend or host TUN
is used. Inputs and profiles are synthetic only.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--fixture', type=Path, required=True)
parser.add_argument('--work', type=Path, required=True)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--version', type=int, choices=(2, 3), required=True)
parser.add_argument('--tls-refusal', action='store_true')
parser.add_argument('--bootstrap-case', choices=('ip-domain', 'domain-domain'))
parser.add_argument('--reality', action='store_true')
parser.add_argument('--reality-key-refusal', action='store_true')
parser.add_argument('--package', choices=('com.nova.vpn.android.usbtest.completionpreview',
                                         'com.nova.vpn.android.usbtest.realitypreview'),
                    default='com.nova.vpn.android.usbtest.completionpreview')
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'This direct host-alias fixture is emulator-only'
fixture = json.loads((args.fixture / 'android-fixture.json').read_text(encoding='utf-8'))
inbound = fixture['inbounds'][0]
stream = inbound['streamSettings']
assert inbound['listen'] == '127.0.0.1' and inbound['port'] == 38123
assert stream['network'] == 'xhttp'
assert not args.reality_key_refusal or args.reality
assert not (args.tls_refusal and args.reality_key_refusal)
assert not args.tls_refusal or not args.reality
if args.reality:
    assert args.version == 2 and stream['security'] == 'reality'
else:
    assert stream['security'] == 'tls'
    assert stream['tlsSettings']['alpn'] == ['h' + str(args.version)]
assert stream['xhttpSettings']['path'] == '/nova-fixture'
if args.reality:
    manifest = json.loads((args.fixture / 'reality-manifest.json').read_text(encoding='utf-8'))
    for endpoint in manifest.values():
        assert endpoint['address'] == '10.0.2.2' and endpoint['port'] == 38123
        assert endpoint['security'] == 'reality' and endpoint['version'] == 2
        assert endpoint['server_name'] == 'localhost' and endpoint['path'] == '/nova-fixture'
else:
    ca = (args.fixture / 'probe-ca.pem').read_text(encoding='utf-8')
    endpoint = {'address': '10.0.2.2', 'port': 38123, 'version': args.version,
                'server_name': 'localhost', 'path': '/nova-fixture', 'ca': ca}
    manifest = {'upload': endpoint, 'download': endpoint}
args.work.mkdir(parents=True, exist_ok=False)
(args.work / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
encoded = base64.b64encode(json.dumps(manifest).encode()).decode()
command = [args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
           '-e', 'xhttpDownlinkOnly', 'true', '-e', 'xhttpTlsManifestBase64', encoded]
if args.bootstrap_case:
    assert not args.tls_refusal and not args.reality_key_refusal
    command += ['-e', 'xhttpBootstrapCase', args.bootstrap_case]
if args.tls_refusal:
    command += ['-e', 'xhttpTlsRefusal', 'true']
if args.reality_key_refusal:
    command += ['-e', 'xhttpRealityKeyRefusal', 'true']
command += [args.package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner']
result = subprocess.run(command, capture_output=True, timeout=100)
output = result.stdout + result.stderr
(args.work / 'android-smoke.log').write_bytes(output)
text = output.decode(errors='replace')
assert result.returncode == 0 and 'INSTRUMENTATION_CODE: -1' in text and 'smoke failed' not in text, text[-2400:]
assert text.count('separate-UID IPv4/IPv6 TUN echo PASS') == (2 if args.tls_refusal or args.reality_key_refusal else 3), text[-2400:]
if args.tls_refusal:
    assert 'XHTTP download TLS identity refusal: IPv4/IPv6 blocked, no working fallback PASS' in text, text[-2400:]
if args.reality_key_refusal:
    assert 'XHTTP download Reality key refusal: IPv4/IPv6 blocked, no working fallback PASS' in text, text[-2400:]
if args.bootstrap_case:
    assert text.count('XHTTP bootstrap ' + args.bootstrap_case + ':') == 3, text[-2400:]
server_log = (args.fixture / 'android-fixture.log').read_bytes()
if args.version == 3:
    assert b'listening QUIC for XHTTP/3 on 127.0.0.1:38123' in server_log
report = {'result': 'PASS', 'serial': args.serial, 'http_version': args.version,
          'test': ('Reality key control/refusal/control' if args.reality_key_refusal else
                   'TLS control/refusal/control' if args.tls_refusal else 'packet-up/stream-up/auto'),
          'security': 'reality' if args.reality else 'tls',
          'bootstrap_case': args.bootstrap_case,
          'scope': 'Direct official Xray ingress; Android TUN; separate-UID IPv4/IPv6; imported download_settings',
          'android_log_sha256': hashlib.sha256(output).hexdigest(),
          'fixture_config_sha256': hashlib.sha256((args.fixture / 'android-fixture.json').read_bytes()).hexdigest(),
          'limits': ['Same endpoint/path for upload and download', 'Emulator only', 'Not network handover']}
(args.work / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(f"PASS direct official Xray H{args.version}: {report['test']}, separate-UID IPv4/IPv6", flush=True)