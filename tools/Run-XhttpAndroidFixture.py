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
args = parser.parse_args()
assert 1 <= args.seconds <= 900
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
    path.write_text(json.dumps(config), encoding='utf-8')
    with (folder / 'android-fixture.log').open('wb') as log:
        process = subprocess.Popen([args.xray, 'run', '-c', str(path)], stdout=log, stderr=log,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            time.sleep(.5)
            assert process.poll() is None
            print('ANDROID_XHTTP_FIXTURE_READY tcp:38123 -> echo tcp:38124', flush=True)
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
