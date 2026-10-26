"""Inventory the pinned published Cronet archives; never builds or executes them.

Published Git blobs and build metadata establish input identity. They do not
prove reproducible C++ compilation, runtime behavior or linked license closure.
"""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--module-cache', type=Path, required=True)
parser.add_argument('--metadata', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
metadata = json.loads(args.metadata.read_text(encoding='utf-8'))
if metadata.get('schema') != 1 or metadata['published_files']['truncated']:
    raise ValueError('Complete pinned metadata is required')
entries = {item['path']: item for item in metadata['published_files']['entries']}
module_version = 'v0.0.0-20260912104006-c10c03c318db'
rows = []
for arch, abi in [('386','x86'), ('amd64','x86_64'), ('arm','armeabi-v7a'), ('arm64','arm64-v8a')]:
    directory = args.module_cache / 'github.com/sagernet/cronet-go/lib' / ('android_' + arch + '@' + module_version)
    row = {'abi': abi, 'module': 'github.com/sagernet/cronet-go/lib/android_' + arch,
           'version': module_version, 'files': []}
    for name in ('libcronet.a', 'libcronet_cgo.go'):
        file = directory / name
        expected = entries['lib/android_' + arch + '/' + name]
        size = file.stat().st_size
        git_hash = hashlib.sha1(usedforsecurity=False)
        git_hash.update(('blob ' + str(size) + '\0').encode('ascii'))
        sha = hashlib.sha256()
        with file.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                git_hash.update(chunk)
                sha.update(chunk)
        if size != expected['size'] or git_hash.hexdigest() != expected['sha']:
            raise ValueError('Published input mismatch: ' + expected['path'])
        row['files'].append({'name': name, 'bytes': size, 'sha256': sha.hexdigest(),
                             'published_git_blob': git_hash.hexdigest()})
    license_file = directory / 'LICENSE'
    root_license = entries['LICENSE']
    data = license_file.read_bytes()
    git_hash = hashlib.sha1(('blob ' + str(len(data)) + '\0').encode('ascii') + data,
                            usedforsecurity=False).hexdigest()
    if len(data) != root_license['size'] or git_hash != root_license['sha']:
        raise ValueError('Unexpected module license copy: ' + abi)
    row['module_license'] = {'name': 'LICENSE', 'bytes': len(data),
                            'sha256': hashlib.sha256(data).hexdigest(), 'published_root_git_blob': git_hash}
    rows.append(row)
report = {'schema': 1, 'metadata_sha256': hashlib.sha256(args.metadata.read_bytes()).hexdigest(),
          'library_commit': metadata['library_commit']['sha'],
          'build_source_commit': metadata['build_source']['sha'],
          'naiveproxy_commit': metadata['build_source']['naiveproxy']['sha'],
          'architectures': rows,
          'limits': ['Published input identity only; no C++ rebuild or runtime test.',
                     'The copied module license is the cronet-go license, not proof of all linked C++ notices.',
                     'Upstream job success is metadata, not cryptographic build attestation.']}
args.output.parent.mkdir(parents=True, exist_ok=True)
with args.output.open('x', encoding='utf-8', newline='\n') as output:
    json.dump(report, output, indent=2)
    output.write('\n')
print('Inventoried four published Cronet archives and their CGo linker files; no binaries executed.')