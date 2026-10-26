"""Bounded loopback-only official Xray + TCP echo fixture for Android adb reverse."""
import argparse
import json
from pathlib import Path
import socketserver
import subprocess
import threading
import time

parser = argparse.ArgumentParser()
parser.add_argument('--xray', required=True)
parser.add_argument('--work', required=True)
parser.add_argument('--seconds', type=int, default=240)
parser.add_argument('--probe-https', action='store_true', help='Allow only the two HTTPS health-check destinations')
parser.add_argument('--block-google', action='store_true', help='Simulate failure of one control provider')
parser.add_argument('--probe-echo', action='store_true', help='Also allow synthetic IPv4/IPv6 TUN echo destinations')
parser.add_argument('--transport', choices=('xhttp', 'tcp'), default='xhttp', help='Use plain VLESS TCP for engine compatibility checks')
parser.add_argument('--probe-tls', action='store_true', help='Generate an ephemeral localhost certificate for verified TLS probes')
parser.add_argument('--backend-h2', action='store_true', help='Require HTTP/2 TLS for the shared XHTTP reverse-proxy backend')
parser.add_argument('--backend-h3', action='store_true', help='Require direct official Xray HTTP/3 TLS ingress')
parser.add_argument('--probe-reality', action='store_true', help='Use synthetic Reality/H2 with a loopback TLS handshake target')
args = parser.parse_args()
if args.probe_reality:
    assert not args.backend_h3 and args.transport == 'xhttp'
    args.probe_tls = True
    args.backend_h2 = True
assert 1 <= args.seconds <= 900
assert not (args.backend_h2 and args.backend_h3)
assert not (args.backend_h2 or args.backend_h3) or (args.probe_tls and args.transport == 'xhttp')
folder = Path(args.work)
folder.mkdir(parents=True, exist_ok=True)

class Echo(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(15)
        try:
            while True:
                data = self.request.recv(65536)
                if not data:
                    break
                self.request.sendall(data)
        except (TimeoutError, OSError):
            pass

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

with Server(('127.0.0.1', 38124), Echo) as echo:
    threading.Thread(target=echo.serve_forever, daemon=True).start()
    config = {
        'log': {'loglevel': 'info'},
        'inbounds': [{'listen': '127.0.0.1', 'port': 38123, 'protocol': 'vless',
                      'settings': {'clients': [{'id': '11111111-1111-4111-8111-111111111111'}], 'decryption': 'none'},
                      'streamSettings': {'network': 'xhttp', 'security': 'none',
                                         'xhttpSettings': {'path': '/nova-fixture', 'mode': 'auto'}}}],
        'outbounds': [{'protocol': 'freedom', 'settings': {'redirect': '127.0.0.1:38124',
                       'finalRules': [{'action': 'allow', 'network': 'tcp', 'ip': ['127.0.0.1/32'], 'port': 38124}]}}]
    }
    path = folder / 'android-fixture.json'
    if args.transport == 'tcp':
        config['inbounds'][0]['streamSettings'] = {'network': 'tcp', 'security': 'none'}
    if args.probe_tls:
        from datetime import datetime, timedelta, timezone
        import ipaddress
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'NOVA isolated Android probe')])
        ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'NOVA ephemeral test CA')])
        now = datetime.now(timezone.utc)
        ca_certificate = (x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name).public_key(ca_key.public_key())
                          .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(days=30))
                          .not_valid_after(now + timedelta(days=30))
                          .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
                          .add_extension(x509.KeyUsage(False, False, False, False, False, True, True, False, False), critical=True)
                          .sign(ca_key, hashes.SHA256()))
        certificate = (x509.CertificateBuilder().subject_name(name).issuer_name(ca_name).public_key(key.public_key())
                       .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(days=30))
                       .not_valid_after(now + timedelta(days=30))
                       .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                       .add_extension(x509.KeyUsage(True, False, True, False, False, False, False, False, False), critical=True)
                       .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'),
                                      x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False)
                       .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
                       .sign(ca_key, hashes.SHA256()))
        cert_file, key_file = folder / 'probe-server.pem', folder / 'probe-key.pem'
        (folder / 'probe-ca.pem').write_bytes(ca_certificate.public_bytes(serialization.Encoding.PEM))
        cert_file.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
        key_file.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                               serialization.NoEncryption()))
        config['inbounds'][0]['streamSettings'].update(security='tls', tlsSettings={
            'certificates': [{'certificateFile': str(cert_file), 'keyFile': str(key_file)}]})
        if args.backend_h2 or args.backend_h3:
            config['inbounds'][0]['streamSettings']['tlsSettings']['alpn'] = ['h3' if args.backend_h3 else 'h2']
    reality_target = None
    if args.probe_reality:
        import base64
        import ssl
        from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
        target_tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        target_tls.minimum_version = ssl.TLSVersion.TLSv1_3
        target_tls.set_alpn_protocols(['h2'])
        target_tls.load_cert_chain(str(cert_file), str(key_file))

        class RealityTarget(socketserver.BaseRequestHandler):
            def handle(self):
                self.request.settimeout(5)
                try:
                    with target_tls.wrap_socket(self.request, server_side=True) as channel:
                        channel.recv(1024)
                except (OSError, ssl.SSLError):
                    pass

        reality_target = Server(('127.0.0.1', 38127), RealityTarget)
        threading.Thread(target=reality_target.serve_forever, daemon=True).start()
        reality_key = X25519PrivateKey.generate()
        def raw_key(data):
            return base64.urlsafe_b64encode(data).decode().rstrip('=')
        private_key = raw_key(reality_key.private_bytes(serialization.Encoding.Raw,
                              serialization.PrivateFormat.Raw, serialization.NoEncryption()))
        public_key = raw_key(reality_key.public_key().public_bytes(serialization.Encoding.Raw,
                             serialization.PublicFormat.Raw))
        stream = config['inbounds'][0]['streamSettings']
        stream.pop('tlsSettings')
        stream.update(security='reality', realitySettings={
            'target': '127.0.0.1:38127', 'serverNames': ['localhost'],
            'privateKey': private_key, 'shortIds': ['0123456789abcdef']})
        endpoint = {'address': '10.0.2.2', 'port': 38123, 'version': 2, 'security': 'reality',
                    'server_name': 'localhost', 'path': '/nova-fixture',
                    'public_key': public_key, 'short_id': '0123456789abcdef',
                    'wrong_public_key': raw_key(X25519PrivateKey.generate().public_key().public_bytes(
                        serialization.Encoding.Raw, serialization.PublicFormat.Raw)),
                    'ca': (folder / 'probe-ca.pem').read_text(encoding='utf-8')}
        (folder / 'reality-manifest.json').write_text(json.dumps({'upload': endpoint, 'download': endpoint}),
                                                   encoding='utf-8')
    if args.probe_https:
        rules = [{'type': 'field', 'ip': ['1.1.1.1/32'], 'port': '443', 'network': 'tcp', 'outboundTag': 'probe'}]
        if not args.block_google:
            rules.append({'type': 'field', 'domain': ['full:www.gstatic.com'], 'port': '443', 'network': 'tcp', 'outboundTag': 'probe'})
        config['outbounds'] = [{'tag': 'blocked', 'protocol': 'blackhole'}, {'tag': 'probe', 'protocol': 'freedom'}]
        config['routing'] = {'domainStrategy': 'AsIs', 'rules': rules}
        if args.probe_echo:
            rules.append({'type': 'field', 'ip': ['198.51.100.7/32', '2001:db8::7/128'], 'port': '80', 'network': 'tcp', 'outboundTag': 'echo'})
            config['outbounds'].append({'tag': 'echo', 'protocol': 'freedom', 'settings': {'redirect': '127.0.0.1:38124',
                'finalRules': [{'action': 'allow', 'network': 'tcp', 'ip': ['127.0.0.1/32'], 'port': 38124}]}})
    path.write_text(json.dumps(config), encoding='utf-8')
    with (folder / 'android-fixture.log').open('wb') as log:
        process = subprocess.Popen([args.xray, 'run', '-c', str(path)], stdout=log, stderr=log,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            time.sleep(.5)
            assert process.poll() is None
            protocol = 'udp/h3' if args.backend_h3 else 'tcp'
            print(f'ANDROID_XHTTP_FIXTURE_READY {protocol}:38123 -> echo tcp:38124', flush=True)
            process.wait(timeout=args.seconds)
            raise RuntimeError('Xray fixture exited unexpectedly')
        except subprocess.TimeoutExpired:
            pass
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            echo.shutdown()
            if reality_target is not None:
                reality_target.shutdown()
                reality_target.server_close()
