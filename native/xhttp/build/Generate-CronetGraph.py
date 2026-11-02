"""Attempt the publisher-pinned Android ARM64 GN graph without compiling target C++."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--source', required=True, type=Path)
parser.add_argument('--work', required=True, type=Path)
parser.add_argument('--cpu', choices=('arm64', 'arm', 'x64', 'x86'), default='arm64')
parser.add_argument('--rebuild', action='store_true')
args = parser.parse_args()
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.name != 'posix':
    raise RuntimeError('Only disposable Linux CI is supported')
source = args.source.resolve() / 'src'
work = args.work.resolve()
work.mkdir(parents=True, exist_ok=False)
url = 'https://raw.githubusercontent.com/SagerNet/cronet-go/049f2909701ca088c9569f6b303bba5376a2b2ff/cmd/build-naive/cmd_build.go'
with urllib.request.urlopen(url, timeout=90) as response:
    builder = response.read()
assert hashlib.sha256(builder).hexdigest() == 'f07ff6f4783115d5031c4a9633deb510260c007d0f49f6eef5c0b3eba466c4ea'
(work / 'publisher-cmd_build.go').write_bytes(builder)
block = builder.decode().split('func buildTarget(t Target)', 1)[1].split('args := []string{', 1)[1].split('\n\t}', 1)[0]
flags = re.findall(r'^\s*"([^"\n]+)",', block, re.MULTILINE)
assert len(flags) > 20 and 'use_thin_lto=false' in flags and 'is_cronet_build=true' in flags
flags += ['target_os="android"', f'target_cpu="{args.cpu}"', 'use_sysroot=false', 'default_min_sdk_version=23']
(work / 'publisher-args.gn').write_text('\n'.join(flags) + '\n')
env = dict(os.environ, EXTRA_FLAGS=f'target_os="android" target_cpu="{args.cpu}"')
# This is the original pinned source's setup, scoped to the disposable checkout.
# It fetches publisher-pinned Clang/GN/PGO and NDK r24; no host drivers or VPN run.
with (work / 'toolchain-setup.log').open('w') as output:
    subprocess.run(['bash', './get-clang.sh'], cwd=source, env=env,
                   stdout=output, stderr=subprocess.STDOUT, check=True, timeout=1200)
gn = source / 'gn/out/gn'
version = subprocess.check_output([str(gn), '--version'], text=True).strip()
assert '3357c4f' in version
out = source / ('out/cronet-android-' + args.cpu)
with (work / 'gn-gen.log').open('w') as output:
    subprocess.run([str(gn), 'gen', str(out), '--args=' + ' '.join(flags)], cwd=source,
                   stdout=output, stderr=subprocess.STDOUT, check=True, timeout=300)
for kind, values in [('targets', ['*']), ('closure', ['//components/cronet:cronet_static'])]:
    with (work / (kind + '.json')).open('w') as output:
        subprocess.run([str(gn), 'desc', str(out), *values, '--format=json', '--all'],
                       cwd=source, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=120)
(work / 'verification.json').write_text(json.dumps(dict(gnVersion=version,
    publisherBuilderSha256=hashlib.sha256(builder).hexdigest(), target='android/' + args.cpu,
    scope='GN generation only; generated target closure still requires linked source/license mapping and binary rebuild comparison'), indent=2) + '\n')
print('PASS publisher-argument GN generation for ' + args.cpu + '; not binary/license acceptance', flush=True)

if args.rebuild:
    with (work / 'ninja-build.log').open('w') as output:
        subprocess.run(['ninja', '-C', str(out), '-j', '2', 'cronet_static'], cwd=source,
                       stdout=output, stderr=subprocess.STDOUT, check=True, timeout=3900)
    graph = json.loads((work / 'closure.json').read_text())
    result = graph['//components/cronet:cronet_static']['outputs']
    assert len(result) == 1 and result[0].startswith('//out/')
    rebuilt = source / result[0][2:]
    assert rebuilt.is_file()
    inputs = json.loads((Path(__file__).resolve().parent.parent / 'provenance/cronet-android-inputs.json').read_text())
    abi = {'arm64': 'arm64-v8a', 'arm': 'armeabi-v7a', 'x64': 'x86_64', 'x86': 'x86'}[args.cpu]
    row = next(row for row in inputs['architectures'] if row['abi'] == abi)
    published = next(item for item in row['files'] if item['name'] == 'libcronet.a')
    def file_sha(path):
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest()
    expected = published['sha256']
    actual = file_sha(rebuilt)
    shutil.copyfile(rebuilt, work / 'rebuilt-libcronet.a')
    unit_sources = sorted({item for value in json.loads((work / 'targets.json').read_text()).values()
                           for item in value.get('sources', []) if item.startswith('//out/')})
    generated = []
    for name in unit_sources:
        path = source / name[2:]
        if path.is_file():
            generated.append(dict(path=name, sha256=file_sha(path), bytes=path.stat().st_size))
    (work / 'generated-sources.json').write_text(json.dumps(generated, indent=2) + '\n')
    comparison = dict(abi=abi, rebuiltSha256=actual, publishedSha256=expected,
                      byteIdentical=actual == expected, generatedFilesInventoried=len(generated),
                      scope='Independent publisher-source rebuild; equality is measured, not assumed')
    (work / 'rebuild-comparison.json').write_text(json.dumps(comparison, indent=2) + '\n')
    print('REBUILD comparison: ' + json.dumps(comparison), flush=True)
    if actual != expected:
        raise RuntimeError('Rebuilt archive differs from pinned published archive; inspect evidence')
