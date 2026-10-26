"""Offline package graphs for the exact Android tags and binding tools.

Raw graphs stay in work/ because they contain local absolute paths. They are
inputs for source packaging, not a claim that every module in go.mod is linked.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--toolchain-root', type=Path, help='Pinned external SDK/Go installation; caches still belong to workspace')
args = parser.parse_args()
root = args.workspace.resolve(strict=True)
toolchain_root = args.toolchain_root.resolve(strict=True) if args.toolchain_root else root
output = root / 'work/native-source-package/graphs'
output.mkdir(parents=True, exist_ok=True)
build_script = (root / 'source/native/xhttp/build/Build-XhttpAar.ps1').read_text(encoding='utf-8-sig')
match = re.search(r"(?m)^\$tags = '([^']+)'\r?$", build_script)
assert match, 'Canonical native tags not found'
tags = match.group(1)
go = toolchain_root / 'toolchain/go-1.25.5/go/bin/go.exe'
clang = toolchain_root / 'toolchain/android-sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
for directory in ['go-workspace', 'go-cache', 'tmp']:
    (root / 'toolchain' / directory).mkdir(parents=True, exist_ok=True)
environment = dict(os.environ, GOENV='off', GOTELEMETRY='off', GOPATH=str(root / 'toolchain/go-workspace'), GOMODCACHE=str(root / 'toolchain/go-mod'),
                   GOCACHE=str(root / 'toolchain/go-cache'), GOTMPDIR=str(root / 'toolchain/tmp'),
                   TEMP=str(root / 'toolchain/tmp'), TMP=str(root / 'toolchain/tmp'),
                   GOTOOLCHAIN='local', GOPROXY='off', GOSUMDB='off', GONOPROXY='none', GOPRIVATE='',
                   GOFLAGS='-mod=readonly -buildvcs=false', GOWORK='off')
targets = {'arm64': 'aarch64-linux-android24', 'arm': 'armv7a-linux-androideabi24',
           'amd64': 'x86_64-linux-android24', '386': 'i686-linux-android24'}
for arch, target in targets.items():
    env = dict(environment, GOOS='android', GOARCH=arch, CGO_ENABLED='1', CC=f'"{clang}" --target={target}')
    if arch == 'arm':
        env['GOARM'] = '7'
    with (output / f'android-{arch}.jsonl').open('wb') as stdout, (output / f'android-{arch}.stderr').open('wb') as stderr:
        subprocess.run([str(go), 'list', '-deps', '-json', '-tags', tags, './experimental/libbox',
                        'github.com/sagernet/gomobile/bind/seq'], cwd=root / 'native-source/sing-box-lx',
                       env=env, stdout=stdout, stderr=stderr, check=True)
    print(f'PASS offline Android {arch} package graph', flush=True)
# Android runtime graphs do not include Windows-only packages or test imports.
# Include the actual host regression closure in the portable source package.
with (output / 'host-tests.jsonl').open('wb') as stdout, (output / 'host-tests.stderr').open('wb') as stderr:
    subprocess.run([str(go), 'list', '-deps', '-test', '-json', '-tags',
                    'with_xhttp,with_quic,with_utls,with_reality', './transport/v2rayxhttp', './option'],
                   cwd=root / 'native-source/sing-box-lx',
                   env=dict(environment, GOOS='windows', GOARCH='amd64', CGO_ENABLED='0'),
                   stdout=stdout, stderr=stderr, check=True)
print('PASS offline Windows native regression/test package graph', flush=True)
with (output / 'binding-tools.jsonl').open('wb') as stdout, (output / 'binding-tools.stderr').open('wb') as stderr:
    subprocess.run([str(go), 'list', '-deps', '-json', './cmd/gomobile', './cmd/gobind'],
                   cwd=root / 'native-source/gomobile', env=dict(environment, GOOS='windows', GOARCH='amd64', CGO_ENABLED='0'),
                   stdout=stdout, stderr=stderr, check=True)
(output / 'inputs.json').write_text(json.dumps({'android_tags': tags, 'architectures': list(targets),
                                               'go_version': '1.25.5', 'android_api': 24,
                                               'ndk_version': '28.0.13004108'}, indent=2) + '\n', encoding='utf-8')
print('PASS offline gomobile/gobind tool graph', flush=True)
