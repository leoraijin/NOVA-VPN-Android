"""Package pinned native sources and checked module distributions, without secrets.

Prebuilt files inside dependencies are inventoried explicitly. This archive does
not certify the completeness of their external C/C++ upstream source provenance.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tarfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--cronet-source', type=Path, required=True, help='Directory verified by Fetch-CronetSource.py')
args = parser.parse_args()
root = args.workspace.resolve(strict=True)
output = args.output.resolve()
assert root in output.parents and not output.exists(), 'Use a NEW output inside the project workspace'
external = args.cronet_source.resolve(strict=True)
subprocess.run([sys.executable, str(root / 'source/native/xhttp/build/Fetch-CronetSource.py'),
                '--output', str(external), '--verify-only'], check=True)
external_verified = json.loads((external / 'verification.json').read_text(encoding='utf-8'))
parts = root / 'work/native-source-package/parts'
parts.mkdir(parents=True, exist_ok=True)
output.parent.mkdir(parents=True, exist_ok=True)
core = root / 'native-source/sing-box-lx'
patch = root / 'source/native/xhttp/core-downlink.patch'
base = '20f89c2f4cb3f77acaf3daa063bd857a055a5259'
patch_sha = '37ea56ca9fa6865f6faae56df14498d5ee84130fca51c7b446ead6c9afeaafac'

def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def git(directory, *arguments):
    return subprocess.check_output(['git', '--no-optional-locks', '-c', 'core.safecrlf=false', '-C', str(directory), *arguments])

assert git(core, 'rev-parse', 'HEAD').decode().strip() == base
assert sha(patch) == patch_sha
assert hashlib.sha256(git(core, 'diff', 'HEAD', '--binary', '--no-ext-diff', '--abbrev=8')).hexdigest() == patch_sha
inputs = json.loads((root / 'work/native-source-package/graphs/inputs.json').read_text(encoding='utf-8-sig'))
modules = {}
counts = {}
decoder = json.JSONDecoder()
for graph in sorted((root / 'work/native-source-package/graphs').glob('*.jsonl')):
    text, index, count = graph.read_text(encoding='utf-8-sig'), 0, 0
    while index < len(text):
        while index < len(text) and text[index].isspace():
            index += 1
        if index == len(text):
            break
        package, index = decoder.raw_decode(text, index)
        assert not package.get('Error'), package.get('ImportPath')
        count += 1
        module = package.get('Module', {})
        module = module.get('Replace', module)
        if module.get('Version'):
            key = module['Path'], module['Version']
            if key not in modules:
                modules[key] = dict(module, scopes=set())
            assert modules[key]['Sum'] == module['Sum']
            modules[key]['scopes'].add(graph.stem)
    counts[graph.stem] = count

pins = {'gvisor': '117243aa02fa2915cb53d1d4549bfca3a3ebf238',
        'sing-tun': 'f236400f3d29bc434f568db93602ef031f0b34cd',
        'utls': 'ad69b191e2f83367ec0ac5f3a1a61310732c43b9',
        'wireguard-go': '8a53421edf7edd2f60dbeab3bd6e49256eab4d04'}
source_parts = {}
for name, directory, commit in [('core', core, base)] + [(n, core / 'submodules' / n, c) for n, c in pins.items()]:
    assert git(directory, 'rev-parse', 'HEAD').decode().strip() == commit
    if name != 'core':
        assert not git(directory, 'status', '--porcelain'), f'Edited submodule {name}'
    destination = parts / f'{name}.tar'
    subprocess.run(['git', '-C', str(directory), 'archive', '--format=tar', f'--output={destination}', 'HEAD'], check=True)
    history = parts / f'{name}.bundle'
    subprocess.run(['git', '-C', str(directory), 'bundle', 'create', str(history), 'HEAD'], check=True)
    shallow = []
    if git(directory, 'rev-parse', '--is-shallow-repository').decode().strip() == 'true':
        shallow_path = Path(git(directory, 'rev-parse', '--git-path', 'shallow').decode().strip())
        if not shallow_path.is_absolute():
            shallow_path = directory / shallow_path
        shallow = shallow_path.read_text(encoding='ascii').splitlines()
        assert shallow and all(re.fullmatch('[a-f0-9]{40}', c) for c in shallow)
    source_parts[name] = {'commit': commit, 'archive': destination, 'sha256': sha(destination),
                          'bundle': history, 'bundle_sha256': sha(history), 'shallow': shallow}

mobile = root / 'native-source/gomobile'
mobile_base = root / 'toolchain/go-mod/github.com/sagernet/gomobile@v0.1.12'
files = {p.relative_to(mobile).as_posix() for p in mobile.rglob('*') if p.is_file()}
base_files = {p.relative_to(mobile_base).as_posix() for p in mobile_base.rglob('*') if p.is_file()}
extras = {'cmd/gomobile/compiler_command_nova.go', 'cmd/gomobile/compiler_command_nova_test.go'}
assert files - base_files == extras and not base_files - files
assert {n for n in files & base_files if (mobile / n).read_bytes() != (mobile_base / n).read_bytes()} == {'cmd/gomobile/env.go'}

def clean_header(info):
    assert not info.issym() and not info.islnk(), 'Unexpected source symlink'
    info.uid = info.gid = 0
    info.uname = info.gname = ''
    info.mtime = 0
    return info

mobile_tar = parts / 'gomobile-patched.tar'
with tarfile.open(mobile_tar, 'w') as archive:
    for name in sorted(files):
        archive.add(mobile / name, arcname=f'gomobile/{name}', recursive=False, filter=clean_header)
go_root = root / 'toolchain/go-1.25.5/go'
assert (go_root / 'VERSION').read_text(encoding='utf-8').startswith('go1.25.5')
go_tar = parts / 'go-sdk-source.tar'
with tarfile.open(go_tar, 'w') as archive:
    for name in ['LICENSE', 'PATENTS', 'VERSION']:
        archive.add(go_root / name, arcname=f'go/{name}', filter=clean_header)
    for file in sorted((go_root / 'src').rglob('*')):
        if file.is_file():
            archive.add(file, arcname='go/' + file.relative_to(go_root).as_posix(), recursive=False, filter=clean_header)
print('PASS pinned core, four clean submodules, exact patched gomobile and Go SDK source', flush=True)

manifest = {'schema': 1, 'version': '1.14.3-lx.23-nova-downlink.1', 'inputs': inputs, 'package_counts': counts,
            'core_patch_sha256': patch_sha, 'core': {}, 'modules': [],
            'external_cronet': dict(external_verified, files={}),
            'gomobile_base': 'v0.1.12', 'gomobile_archive_sha256': sha(mobile_tar), 'go_sdk_source_sha256': sha(go_tar),
            'limits': ['External C/C++ source provenance of inventoried prebuilt dependencies still needs review',
                       'No signing keys, user profiles, caches or local package graph paths included',
                       'Archive source integrity is not ARM/16KB/HTTP3/Reality runtime proof']}
license_pattern = re.compile(r'(?i)^(licen[cs]e|copying|notice|copyright)([._-].*)?$')

def put(archive, name, data, compress=zipfile.ZIP_DEFLATED):
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = compress
    archive.writestr(info, data)

def put_file(archive, name, path, compress=zipfile.ZIP_STORED):
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = compress
    with archive.open(info, 'w', force_zip64=True) as destination, Path(path).open('rb') as source:
        shutil.copyfileobj(source, destination, 1024 * 1024)

with zipfile.ZipFile(output, 'x', allowZip64=True) as bundle:
    for name in ['git-tree.json', 'verification.json', f'naiveproxy-{external_verified["commit"]}.tar.gz']:
        path = external / name
        archive_name = 'external/cronet/' + name
        put_file(bundle, archive_name, path)
        manifest['external_cronet']['files'][name] = {'archive': archive_name, 'sha256': sha(path)}
    for name, source in source_parts.items():
        archive_name = f'sources/{name}.tar'
        put_file(bundle, archive_name, source['archive'], zipfile.ZIP_DEFLATED)
        manifest['core'][name] = {'commit': source['commit'], 'archive': archive_name, 'sha256': source['sha256']}
        history_name = f'sources/{name}.bundle'
        put_file(bundle, history_name, source['bundle'])
        manifest['core'][name].update(bundle=history_name, bundle_sha256=source['bundle_sha256'],
                                      shallow=source['shallow'])
    put_file(bundle, 'sources/gomobile-patched.tar', mobile_tar, zipfile.ZIP_DEFLATED)
    put_file(bundle, 'sources/go-sdk-source.tar', go_tar, zipfile.ZIP_DEFLATED)
    put_file(bundle, 'patches/core-downlink.patch', patch)
    bindings = root / 'work/xhttp-downlink-build/libbox-arm64-arm-amd64-386-sources.jar'
    put_file(bundle, 'bindings/libbox-sources.jar', bindings)
    manifest['binding_sources_sha256'] = sha(bindings)
    manifest['aar_sha256'] = sha(root / 'work/xhttp-downlink-build/libbox-arm64-arm-amd64-386.aar')
    assert manifest['aar_sha256'] == 'c8a9981a684cc42c8a83e3b6d3a2839f08e1d91fd8f7b27c64e73bd87ce61583'
    for number, ((path, version), module) in enumerate(sorted(modules.items()), 1):
        module_zip = Path(module['GoMod']).with_suffix('.zip')
        module_mod = Path(module['GoMod'])
        identifier = hashlib.sha256(f'{path}@{version}'.encode()).hexdigest()
        summary = hashlib.sha256()
        notices = []
        binaries = []
        with zipfile.ZipFile(module_zip) as archive:
            assert len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate module ZIP names'
            for name in sorted(archive.namelist()):
                assert '\n' not in name and name.startswith(f'{path}@{version}/')
                relative = name[len(f'{path}@{version}/'):]
                assert '..' not in PurePosixPath(relative).parts and not relative.startswith('/')
                with archive.open(name) as stream:
                    file_hash = hashlib.file_digest(stream, 'sha256').hexdigest()
                summary.update(f'{file_hash}  {name}\n'.encode())
                if license_pattern.match(PurePosixPath(name).name):
                    notices.append(relative)
                    put(bundle, f'licenses/modules/{identifier}/{relative}', archive.read(name))
                if PurePosixPath(name).suffix.lower() in ['.a', '.so', '.dll', '.lib', '.dylib']:
                    binaries.append(relative)
        h1 = 'h1:' + base64.b64encode(summary.digest()).decode()
        assert h1 == module['Sum'], f'Module source checksum mismatch: {path}@{version}'
        mod_summary = f'{hashlib.sha256(module_mod.read_bytes()).hexdigest()}  go.mod\n'.encode()
        mod_h1 = 'h1:' + base64.b64encode(hashlib.sha256(mod_summary).digest()).decode()
        assert mod_h1 == module['GoModSum'], f'go.mod checksum mismatch: {path}@{version}'
        assert notices, f'No standalone license/notice: {path}@{version}'
        put_file(bundle, f'modules/{identifier}/module.zip', module_zip)
        put_file(bundle, f'modules/{identifier}/go.mod', module_mod)
        manifest['modules'].append({'path': path, 'version': version, 'id': identifier, 'sum': h1, 'go_mod_sum': mod_h1,
                                    'zip_sha256': sha(module_zip), 'scopes': sorted(module['scopes']),
                                    'notice_files': notices, 'prebuilt_files': binaries})
        if number % 20 == 0:
            print(f'PASS source and go.mod checksums + notices: {number}/{len(modules)} modules', flush=True)
    for file in sorted((root / 'source/native/xhttp/build').glob('*')):
        if file.suffix in ['.py', '.ps1']:
            put_file(bundle, 'build/' + file.name, file)
    put_file(bundle, 'README-NOVA.md', root / 'source/native/xhttp/README.md')
    put(bundle, 'manifest.json', (json.dumps(manifest, indent=2) + '\n').encode())
(output.with_suffix('.manifest.json')).write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
(output.with_suffix('.sha256')).write_text(f'{sha(output)}  {output.name}\n', encoding='ascii')
print(f'PACKAGED {len(modules)} checked module distributions: {output.name}', flush=True)
