"""Inventory pinned Cronet source notices and archive members without claiming closure.

Never executes archived source or extracts binaries. Candidate notices retain original
bytes. Flattened object names are only mapped when source basenames are unique.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path, PurePosixPath
import posixpath
import re
import subprocess
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--source-archive', type=Path, required=True)
parser.add_argument('--module-cache', type=Path, required=True)
parser.add_argument('--llvm-ar', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--supplemental-notices', type=Path)
args = parser.parse_args()
root = args.workspace.resolve(strict=True)
output = args.output.resolve()
assert root in output.parents and not output.exists(), 'Use a NEW project output directory'
source_sha = '396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19'
source_commit = '72a06c9fca0e2d228588c7f3074bf7efff3ff686'
publication = 'c10c03c318db6d47bec8705c8290bad34bc32b64'
version = 'v0.0.0-20260912104006-c10c03c318db'

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

assert sha(args.source_archive) == source_sha
metadata_path = Path(__file__).resolve().parent.parent / 'provenance/cronet-android-inputs.json'
inputs = json.loads(metadata_path.read_text(encoding='utf-8-sig'))
assert inputs['library_commit'] == publication and inputs['naiveproxy_commit'] == source_commit
assert {row['abi'] for row in inputs['architectures']} == {'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'}
report = {'schema': 1, 'source_commit': source_commit, 'source_archive_sha256': source_sha,
          'published_library_commit': publication, 'input_inventory_sha256': sha(metadata_path),
          'scope': 'Candidate source notices and archive object-name inventory; not GN/link closure',
          'architectures': [], 'source_notices': [], 'chromium_metadata': []}
all_objects = set()
wrapper_notices = []
for row in inputs['architectures']:
    assert row['version'] == version and row['module'].startswith('github.com/sagernet/cronet-go/lib/android_')
    folder = args.module_cache / (row['module'] + '@' + version)
    library = folder / 'libcronet.a'
    expected = next(item for item in row['files'] if item['name'] == 'libcronet.a')
    assert sha(library) == expected['sha256'], row['abi']
    notice = folder / 'LICENSE'
    assert sha(notice) == row['module_license']['sha256']
    wrapper_notices.append((row['abi'], notice.read_bytes()))
    process = subprocess.run([str(args.llvm_ar), 't', str(library)], check=True, capture_output=True)
    names = process.stdout.decode('utf-8').splitlines()
    assert names and all(n.endswith('.o') and '\x00' not in n for n in names)
    all_objects.update(names)
    report['architectures'].append({'abi': row['abi'], 'library_sha256': expected['sha256'],
        'archive_member_count': len(names), 'duplicate_member_names': {n: c for n, c in Counter(names).items() if c > 1},
        'members': names, 'wrapper_license_sha256': row['module_license']['sha256']})
output.mkdir(parents=True)
report['supplemental_notices'] = []
if args.supplemental_notices:
    supplement = json.loads(args.supplemental_notices.read_text(encoding='utf-8-sig'))
    assert supplement['schema'] == 1 and supplement['source_naiveproxy_commit'] == source_commit
    report['supplemental_input_sha256'] = sha(args.supplemental_notices)
    identifiers = set()
    for item in supplement['components']:
        identifier = item['component_id']
        assert re.fullmatch('[a-z0-9-]+', identifier) and identifier not in identifiers
        identifiers.add(identifier)
        assert re.fullmatch('[a-f0-9]{40}', item['revision']) and item['license_file'] == 'LICENSE'
        data = item['license_text'].encode('utf-8')
        assert len(data) < 2 * 1024 * 1024 and hashlib.sha256(data).hexdigest() == item['license_sha256']
        target = output / 'supplemental-notices' / identifier / 'LICENSE'
        target.parent.mkdir(parents=True)
        target.write_bytes(data)
        report['supplemental_notices'].append({k: v for k, v in item.items() if k != 'license_text'})
for abi, data in wrapper_notices:
    path = output / 'wrapper-licenses' / abi / 'LICENSE'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
source_candidates = defaultdict(list)
metadata_directories = set()
with tarfile.open(args.source_archive, 'r:gz') as archive:
    prefix = 'naiveproxy-' + source_commit + '/'
    files = {}
    for member in archive.getmembers():
        if not member.isfile():
            continue
        assert member.name.startswith(prefix)
        relative = member.name.removeprefix(prefix)
        assert not PurePosixPath(relative).is_absolute() and '..' not in PurePosixPath(relative).parts
        files[relative] = member
        if PurePosixPath(relative).suffix in {'.c', '.cc', '.cpp', '.cxx', '.S', '.s', '.asm', '.mm', '.m'}:
            source_candidates[PurePosixPath(relative).stem].append(relative)
    for relative, member in sorted(files.items()):
        basename = PurePosixPath(relative).name
        is_notice = re.fullmatch(r'(?i)(LICENSE|COPYING|NOTICE|COPYRIGHT)(\.[A-Za-z0-9_-]+)?', basename)
        if is_notice or basename == 'README.chromium':
            assert member.size < 4 * 1024 * 1024
            data = archive.extractfile(member).read()
            target = output / 'source-documents' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            entry = {'path': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
            if is_notice:
                report['source_notices'].append(entry)
            if basename == 'README.chromium':
                metadata_directories.add(str(PurePosixPath(relative).parent))
                dependencies = []
                for block in re.split(r'^-{20} DEPENDENCY DIVIDER -{20}$', data.decode('utf-8', errors='replace'), flags=re.M):
                    fields = {}
                    duplicates = []
                    for line in block.splitlines():
                        if line.startswith('Description:'):
                            break
                        match = re.match(r'^(Name|URL|License|License File|Shipped|Version|Revision):\s*(.*)$', line)
                        if match:
                            name, value = match.groups()
                            if name in fields:
                                duplicates.append(name)
                            fields[name] = value
                    if not fields:
                        continue
                    declared = []
                    for value in fields.get('License File', '').split(','):
                        value = value.strip()
                        if not value:
                            continue
                        if value == 'NOT_SHIPPED':
                            declared.append({'declared': value, 'marker': True})
                            continue
                        path = posixpath.normpath('src/' + value[2:] if value.startswith('//') else
                                                 posixpath.join(str(PurePosixPath(relative).parent), value))
                        assert not path.startswith(('../', '/'))
                        found = files.get(path)
                        declared.append({'declared': value, 'resolved': path, 'present': found is not None})
                    dependencies.append({'fields': fields, 'duplicate_fields': duplicates, 'declared_license_files': declared})
                entry['dependencies'] = dependencies
                report['chromium_metadata'].append(entry)
report['object_source_candidates'] = []
for name in sorted(all_objects):
    stem = PurePosixPath(name).name.removesuffix('.o')
    paths = source_candidates.get(stem, [])
    item = {'object': name, 'source_candidates': paths,
            'mapping': 'unique-basename' if len(paths) == 1 else 'ambiguous' if paths else 'unmatched/generated'}
    if len(paths) == 1:
        parent = PurePosixPath(paths[0]).parent
        parents = [str(parent), *(str(p) for p in parent.parents)]
        item['nearest_readme'] = next((p + '/README.chromium' for p in parents if p in metadata_directories), None)
        item['third_party_without_metadata'] = '/third_party/' in paths[0] and item['nearest_readme'] is None
    report['object_source_candidates'].append(item)
report['summary'] = {'source_notice_documents': len(report['source_notices']),
    'chromium_metadata_documents': len(report['chromium_metadata']),
    'supplemental_notice_documents': len(report['supplemental_notices']),
    'object_mapping': dict(Counter(row['mapping'] for row in report['object_source_candidates'])),
    'declared_missing_license_paths': sorted({f['resolved'] for row in report['chromium_metadata'] for d in row['dependencies']
        for f in d['declared_license_files'] if f.get('present') is False}),
    'unique_third_party_sources_without_metadata': sorted({row['source_candidates'][0] for row in report['object_source_candidates']
        if row.get('third_party_without_metadata')})}
report['limits'] = ['No GN target graph or build argument equivalence was established',
    'Object member names are flattened; basename matches do not prove the actual compiled source path',
    'Generated/ambiguous objects and inline headers cannot be closed by this inventory',
    'Source documents include host tools and platform-specific dependencies; this is not a final shipped-component selection',
    'No independent Cronet C++ rebuild or complete linked NOTICE is claimed']
(output / 'inventory.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
(output / 'README.txt').write_text('Pinned Cronet candidate notice inventory. Original source-document bytes retained.\n'
    'This is NOT a complete linked NOTICE or release acceptance. See inventory.json limits.\n', encoding='utf-8')
print(json.dumps(report['summary'], indent=2))