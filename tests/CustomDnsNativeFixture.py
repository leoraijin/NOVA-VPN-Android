import json, pathlib, socket, socketserver, struct, subprocess, threading, urllib.request
import argparse
parser = argparse.ArgumentParser()
parser.add_argument('--fixtures', required=True)
parser.add_argument('--core', required=True)
args = parser.parse_args()
root = pathlib.Path(args.fixtures).resolve()
core = args.core
class Dns(socketserver.BaseRequestHandler):
    def handle(self):
        query, sock = self.request
        pos = 12
        while query[pos]: pos += query[pos] + 1
        end = pos + 5
        reply = query[:2] + b'\x81\x80' + b'\x00\x01\x00\x01\x00\x00\x00\x00' + query[12:end]
        reply += b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x01\x00\x04\x7f\x00\x00\x01'
        sock.sendto(reply, self.client_address)
class Http(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.recv(4096)
        body = b'NOVA DNS route fixture'
        self.request.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(body)).encode() + b'\r\nConnection: close\r\n\r\n' + body)
def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0)); return sock.getsockname()[1]
with socketserver.UDPServer(('127.0.0.1',0),Dns) as dns, socketserver.ThreadingTCPServer(('127.0.0.1',0),Http) as http:
    for server in (dns,http): threading.Thread(target=server.serve_forever,daemon=True).start()
    for name in ('single','multi'):
        config = json.loads((root/f'custom-dns-{name}.json').read_text())
        port = free_port()
        config['inbounds']=[dict(type='mixed',tag='fixture',listen='127.0.0.1',listen_port=port)]
        config.pop('experimental',None)
        for server in config['dns']['servers']:
            if server['tag']=='nova-custom-dns-resolver':
                server.clear();server.update(type='udp',tag='nova-custom-dns-resolver',server='127.0.0.1',server_port=dns.server_address[1])
        path=root/f'native-{name}.json';path.write_text(json.dumps(config))
        proc=subprocess.Popen([core,'run','-c',str(path)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            import time
            for _ in range(40):
                try:
                    with socket.create_connection(('127.0.0.1',port),timeout=.2): break
                except OSError: time.sleep(.1)
            if proc.poll() is not None: raise RuntimeError(proc.stderr.read().decode(errors='replace'))
            opener=urllib.request.build_opener(urllib.request.ProxyHandler({'http':f'http://127.0.0.1:{port}'}))
            with opener.open(f'http://fixture.example.org:{http.server_address[1]}/',timeout=5) as response:
                assert response.read()==b'NOVA DNS route fixture'
            print(f'PASS native {name}: selected domain resolved through custom DNS and connected directly with primary proxy unavailable')
        finally:
            proc.terminate();proc.wait(timeout=5)
            path.unlink(missing_ok=True)

    dns.shutdown()
    http.shutdown()
