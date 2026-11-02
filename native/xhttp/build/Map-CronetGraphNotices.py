"""Map proven GN compilation-unit paths to notice candidates, not legal closure."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument('--units', type=Path, required=True)
parser.add_argument('--notices', type=Path, required=True)
parser.add_argument('--source-archive', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
units = json.loads(args.units.read_text(encoding='utf-8-sig'))
notices = json.loads(args.notices.read_text(encoding='utf-8-sig'))
assert units['objectNameMultisetMatches'] and not units['missing'] and not units['extra']
assert units['sourceCommit'] == notices['source_commit'] == '72a06c9fca0e2d228588c7f3074bf7efff3ff686'
with args.source_archive.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == notices['source_archive_sha256']
metadata = {str(PurePosixPath(row['path']).parent): row for row in notices['chromium_metadata']}
supplements = {row['component_id']: row for row in notices['supplemental_notices']}
assert {'perfetto', 'protobuf', 'compiler-rt'} <= supplements.keys()
source_documents = {row['path'] for row in notices['source_notices']}
assert 'src/LICENSE' in source_documents
stub = 'src/buildtools/third_party/libc++abi/cxa_demangle_stub.cc'
with tarfile.open(args.source_archive, 'r:gz') as archive:
    entry = next(row for row in archive if row.name.endswith('/' + stub))
    stub_bytes = archive.extractfile(entry).read()
assert stub_bytes.startswith(b'// Copyright 2018 The Chromium Authors\n// Use of this source code is governed by a BSD-style license')
prefixes = {'src/third_party/perfetto': 'perfetto', 'src/third_party/protobuf': 'protobuf',
            'src/third_party/compiler-rt': 'compiler-rt'}
rows = []
for unit in units['units']:
    origin = 'src/' + (unit['target'].split(':')[0][2:] + '/generated' if unit['generated'] else unit['source'][2:])
    parent = PurePosixPath(origin).parent
    parents = [str(parent), *(str(row) for row in parent.parents)]
    meta = next((metadata[p] for p in parents if p in metadata), None)
    supplement = next((value for prefix, value in prefixes.items() if origin.startswith(prefix + '/')), None)
    declared = sorted({item['resolved'] for dep in meta['dependencies'] for item in dep['declared_license_files']
                       if item.get('present')}) if meta else []
    unresolved = False
    if supplement:
        component = supplement
        candidates = ['supplemental:' + supplement + '/' + supplements[supplement]['license_file']]
        basis = 'Pinned supplemental notice; local source patches disclosed separately'
    elif meta and declared:
        component = str(PurePosixPath(meta['path']).parent)
        candidates = declared
        basis = 'Nearest README.chromium declared files in the pinned archive'
    elif origin == stub or '/third_party/' not in origin:
        component, candidates = 'chromium', ['src/LICENSE']
        basis = 'Chromium source; stub license header independently checked' if origin == stub else 'Chromium source directory'
    else:
        component, candidates, unresolved = 'unresolved', [], True
        basis = 'No sufficient notice association found'
    rows.append(dict(target=unit['target'], source=unit['source'], generated=unit['generated'],
                     component=component, noticeCandidates=candidates, basis=basis, unresolved=unresolved))
report = dict(schema=1, sourceCommit=units['sourceCommit'], target=units['target'],
              unitsInputSha256=hashlib.sha256(args.units.read_bytes()).hexdigest(),
              noticeInventorySha256=hashlib.sha256(args.notices.read_bytes()).hexdigest(),
              compilationUnits=len(rows), components=dict(Counter(row['component'] for row in rows)),
              unresolvedCompilationUnits=sum(row['unresolved'] for row in rows), units=rows,
              limits=['GN compilation-unit notice candidates only, not a final NOTICE or compliance conclusion',
                      'Actual included headers and generated-code origins require additional evidence',
                      'Object-name correspondence does not prove binary/source equivalence',
                      'Other ABI classifications are separate'])
with args.output.open('x', encoding='utf-8') as output:
    json.dump(report, output, indent=2)
    output.write('\n')
print(json.dumps({key: report[key] for key in ('compilationUnits', 'components', 'unresolvedCompilationUnits')}, indent=2))
