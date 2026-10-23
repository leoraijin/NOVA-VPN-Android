"""Local-only interop: pinned sing-box-lx client against official Xray server.

Usage: python Test-XhttpInterop.py --xray PATH --sing-box PATH --work DIR
All keys and endpoints below are synthetic. Does not modify the system VPN.
"""
import argparse
import http.server
import json
from pathlib import Path
import socket
import subprocess
import threading
import time

parser = argparse.ArgumentParser()
parser.add_argument('--xray', required=True)
parser.add_argument('--sing-box', required=True)
parser.add_argument('--work', required=True)
args = parser.parse_args()
folder = Path(args.work)
folder.mkdir(parents=True, exist_ok=True)
uid = '11111111-1111-4111-8111-111111111111'

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        payload = b'nova-xhttp-interoperability'
        self.send_response(200)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
    def log_message(self, *args):
        pass

def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

def start(binary, config, name):
    path = folder / (name + '.json')
    path.write_text(json.dumps(config), encoding='utf-8')
    log = (folder / (name + '.log')).open('wb')
    process = subprocess.Popen([binary, 'run', '-c', str(path)], stdout=log, stderr=log,
                               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    time.sleep(.6)
    if process.poll() is not None:
        log.close()
        raise RuntimeError((folder / (name + '.log')).read_text(errors='replace'))
    return process, log

def stop(pair):
    process, log = pair
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
    log.close()

httpd = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=httpd.serve_forever, daemon=True).start()
server_port = port()
xray = None
try:
    xray = start(args.xray, {
        'log': {'loglevel': 'debug'},
        'inbounds': [{'listen': '127.0.0.1', 'port': server_port, 'protocol': 'vless',
                      'settings': {'clients': [{'id': uid}], 'decryption': 'none'},
                      'streamSettings': {'network': 'xhttp', 'security': 'none',
                                         'xhttpSettings': {'path': '/nova-fixture', 'mode': 'auto'}}}],
        'outbounds': [{'protocol': 'freedom', 'settings': {'redirect': f'127.0.0.1:{httpd.server_port}',
                       'finalRules': [{'action': 'allow', 'network': 'tcp', 'ip': ['127.0.0.1/32'], 'port': httpd.server_port}]}}]
    }, 'xray-server')
    for mode in ['packet-up', 'stream-up', 'stream-one', 'auto']:
        proxy_port = port()
        client = start(args.sing_box, {
            'log': {'level': 'debug'},
            'inbounds': [{'type': 'mixed', 'listen': '127.0.0.1', 'listen_port': proxy_port}],
            'outbounds': [{'type': 'vless', 'tag': 'proxy', 'server': '127.0.0.1', 'server_port': server_port,
                           'uuid': uid, 'transport': {'type': 'xhttp', 'path': '/nova-fixture', 'mode': mode}}],
            'route': {'final': 'proxy'}
        }, 'client-' + mode)
        try:
            request = subprocess.run(['curl.exe', '--silent', '--show-error', '--max-time', '12',
                                      '--proxy', f'socks5h://127.0.0.1:{proxy_port}', '--noproxy', '',
                                      'http://fixture.invalid/echo'], capture_output=True, timeout=15)
            assert request.returncode == 0 and request.stdout == b'nova-xhttp-interoperability', (
                mode, request.returncode, request.stdout, request.stderr)
            print(f'PASS {mode}: HTTP payload through VLESS/XHTTP to official Xray', flush=True)
        finally:
            stop(client)
finally:
    if xray:
        stop(xray)
    httpd.shutdown()
