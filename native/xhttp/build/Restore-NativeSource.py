"""Restore a checked source candidate into a NEW project directory and empty cache."""
import argparse
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
parser.add_argument('--archive', type=Path, required=True)
parser.add_argument('--expected-sha256', required=True)
parser.add_argument('--destination', type=Path, required=True)
args = parser.parse_args()
root = args.workspace.resolve(strict=True)
destination = args.destination.resolve()
assert root in destination.parents and not destination.exists(), 'Use a NEW folder inside the project'
with args.archive.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == args.expected_sha256
destination.mkdir(parents=True)

def escape(value):
    return ''.join('!' + c.lower() if c.isupper() else c for c in value)

with zipfile.ZipFile(args.archive) as bundle:
    manifest = json.loads(bundle.read('manifest.json'))
    native = destination / 'native-source'
    core = native / 'sing-box-lx'
    core.mkdir(parents=True)
    sources = [(manifest['core']['core']['archive'], manifest['core']['core']['sha256'], core)]
    sources += [(value['archive'], value['sha256'], core / 'submodules' / name)
                for name, value in manifest['core'].items() if name != 'core']
    sources += [('sources/gomobile-patched.tar', manifest['gomobile_archive_sha256'], native)]
    for name, expected, target in sources:
        with bundle.open(name) as stream:
            assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected, name
        target.mkdir(parents=True, exist_ok=True)
        with bundle.open(name) as stream, tarfile.open(fileobj=stream, mode='r|') as archive:
            archive.extractall(target, filter='data')
    patch = destination / 'core-downlink.patch'
    patch.write_bytes(bundle.read('patches/core-downlink.patch'))
    assert hashlib.sha256(patch.read_bytes()).hexdigest() == manifest['core_patch_sha256']
    subprocess.run(['git', '-C', str(core), 'apply', '--check', str(patch)], check=True)
    subprocess.run(['git', '-C', str(core), 'apply', str(patch)], check=True)
    # Optional pinned Git bundles preserve real commit identity for the normal
    # source guard. Populate indexes without checking out over restored files.
    for name, part in manifest['core'].items():
        if 'bundle' not in part:
            continue
        directory = core if name == 'core' else core / 'submodules' / name
        history = destination / f'{name}.bundle'
        history.write_bytes(bundle.read(part['bundle']))
        assert hashlib.sha256(history.read_bytes()).hexdigest() == part['bundle_sha256']
        subprocess.run(['git', 'init', str(directory)], check=True, stdout=subprocess.DEVNULL)
        def git(*arguments):
            return subprocess.run(['git', '-C', str(directory), *arguments], check=True, stdout=subprocess.PIPE)
        # Git archives on this pinned Windows build use CRLF working files.
        # Normalize them to the original LF blobs when creating the index.
        git('config', 'core.autocrlf', 'true')
        if part.get('shallow'):
            assert all(re.fullmatch('[a-f0-9]{40}', c) for c in part['shallow'])
            (directory / '.git/shallow').write_text('\n'.join(part['shallow']) + '\n', encoding='ascii')
        git('fetch', str(history), 'HEAD')
        revision = git('rev-parse', 'FETCH_HEAD').stdout.decode().strip()
        assert revision == part['commit'], 'Unexpected bundled base revision'
        git('update-ref', 'HEAD', revision)
        git('read-tree', revision)
    if all('bundle' in part for part in manifest['core'].values()):
        # Register patch-created files as well: diff HEAD otherwise omits them.
        # This index belongs only to the new restoration, never a user's checkout.
        subprocess.run(['git', '-C', str(core), 'add', '--all', '--', '.'], check=True)
    cache = destination / 'toolchain/go-mod/cache/download'
    for module in manifest['modules']:
        path, version, identifier = module['path'], module['version'], module['id']
        assert not PurePosixPath(path).is_absolute() and '..' not in PurePosixPath(path).parts and '\\' not in path
        assert '/' not in version and '\\' not in version and re.fullmatch('[a-f0-9]{64}', identifier)
        target = cache / escape(path) / '@v'
        target.mkdir(parents=True, exist_ok=True)
        prefix = f'modules/{identifier}/'
        for member, suffix in [('module.zip', '.zip'), ('go.mod', '.mod')]:
            file = target / (escape(version) + suffix)
            with bundle.open(prefix + member) as source, file.open('wb') as output:
                shutil.copyfileobj(source, output, 1024 * 1024)
            if suffix == '.zip':
                with file.open('rb') as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == module['zip_sha256']
        (target / (escape(version) + '.ziphash')).write_text(module['sum'], encoding='ascii')
        if prefix + 'module.info' in bundle.namelist():
            (target / (escape(version) + '.info')).write_bytes(bundle.read(prefix + 'module.info'))
    (destination / 'source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    helpers = destination / 'source/native/xhttp'
    (helpers / 'build').mkdir(parents=True)
    for name in bundle.namelist():
        if name.startswith('build/') and PurePosixPath(name).name == name.removeprefix('build/'):
            (helpers / name).write_bytes(bundle.read(name))
    (helpers / 'README.md').write_bytes(bundle.read('README-NOVA.md'))
    if 'external_cronet' in manifest:
        external = native / 'cronet-source'
        external.mkdir()
        for name, item in manifest['external_cronet']['files'].items():
            assert PurePosixPath(name).name == name and '\\' not in name
            assert item['archive'] == 'external/cronet/' + name
            file = external / name
            with bundle.open(item['archive']) as source, file.open('xb') as output:
                shutil.copyfileobj(source, output, 1024 * 1024)
            with file.open('rb') as stream:
                assert hashlib.file_digest(stream, 'sha256').hexdigest() == item['sha256'], name
        subprocess.run([sys.executable, str(helpers / 'build/Fetch-CronetSource.py'),
                        '--output', str(external), '--verify-only'], check=True)
print(f'RESTORED sources and {len(manifest["modules"])} module distributions into a new cache', flush=True)
print('No network used; package resolution and compilation are separate checks.', flush=True)
