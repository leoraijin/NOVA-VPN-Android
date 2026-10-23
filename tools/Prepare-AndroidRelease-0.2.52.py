"""Publish the exact locally tested/signed APK without transferring its signing key."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

REPO = 'leoraijin/NOVA-VPN-Android'
manifest_path = Path('tools/release-0.2.52.json')
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
out = Path('outputs')
out.mkdir(exist_ok=True)
work = Path('release-work')
work.mkdir(exist_ok=True)

def download(url, path, expected=None):
    request = urllib.request.Request(url, headers={'User-Agent': 'NOVA-Android-release'})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if expected and hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Checksum mismatch: ' + path.name)
    path.write_bytes(data)

subprocess.run(['git', 'diff', '--exit-code', manifest['codeCommit'], 'HEAD', '--',
                'app', 'native', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties'], check=True)
delta = work / 'signed-apk.delta.gz'
with delta.open('wb') as target:
    for index in range(manifest['parts']):
        name = f'part-{index:03d}.bin'
        part = work / name
        download(f'https://raw.githubusercontent.com/{REPO}/{manifest["payloadCommit"]}/{name}', part)
        target.write(part.read_bytes())
base = work / 'base.apk'
download(f'https://github.com/{REPO}/releases/download/v0.2.50/NOVA-VPN-Android-0.2.50-arm64-optimized.apk',
         base, manifest['baseSha256'])
subprocess.run([sys.executable, 'tools/Restore-ReleaseApk.py', str(manifest_path), str(base),
                str(delta), str(out / manifest['apkName'])], check=True)
for source in manifest['sources']:
    download(source['url'], out / source['name'], source['sha'])
subprocess.run(['git', 'archive', '--format=zip', '--output=' + str(out / 'NOVA-VPN-Android-0.2.52-source.zip'), 'HEAD'], check=True)
shutil.copyfile('RELEASE-NOTES-0.2.52.md', out / 'RELEASE-NOTES.md')
shutil.copyfile('XHTTP-VERIFICATION-0.2.52.md', out / 'VERIFICATION.md')
shutil.copyfile('THIRD-PARTY-NOTICES.md', out / 'THIRD-PARTY-NOTICES.md')
(out / 'BUILD-COMMITS.txt').write_text('APK application code: ' + manifest['codeCommit'] + '\nPublication source: ' +
    subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() + '\n', encoding='utf-8')
