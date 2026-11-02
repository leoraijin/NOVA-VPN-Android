"""Match actual compiler inputs to pinned Git source bytes; no license conclusions."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument('--dependencies', type=Path, required=True)
parser.add_argument('--receipt', type=Path, required=True)
parser.add_argument('--source-archive', type=Path, required=True)
parser.add_argument('--git-tree', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
receipt = json.loads(args.receipt.read_text(encoding='utf-8-sig'))
assert receipt['targetObjectMultisetMatchesPublished'] and receipt['targetDependencyPathsWithoutHash'] == 0
assert hashlib.sha256(args.dependencies.read_bytes()).hexdigest() == receipt['targetDependencyInventorySha256']
with args.source_archive.open('rb') as stream:
    archive_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
assert archive_sha == '396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19'
tree = json.loads(args.git_tree.read_text(encoding='utf-8-sig'))
assert tree['sha'] == '72a06c9fca0e2d228588c7f3074bf7efff3ff686' and not tree['truncated']
blobs = {row['path']: row for row in tree['tree'] if row['type'] == 'blob' and row['mode'] in ('100644', '100755')}
rows = json.loads(gzip.decompress(args.dependencies.read_bytes()))
assert len(rows) == receipt['uniqueTargetDependencyPaths']
selected = {'src/' + row['sourceRelativePath'] for row in rows}
matched = {}
with tarfile.open(args.source_archive, 'r:gz') as archive:
    for entry in archive:
        if '/src/' not in entry.name or not entry.isfile():
            continue
        name = 'src/' + entry.name.split('/src/', 1)[1]
        if name not in selected or name not in blobs:
            continue
        assert name not in matched and entry.size <= 16 * 1024 * 1024
        data = archive.extractfile(entry).read()
        git_hash = lambda value: hashlib.sha1(b'blob ' + str(len(value)).encode() + b'\0' + value).hexdigest()
        normalized = False
        if git_hash(data) != blobs[name]['sha']:
            candidate = data.replace(b'\r\n', b'\n')
            assert git_hash(candidate) == blobs[name]['sha'], name
            data, normalized = candidate, True
        matched[name] = dict(gitBlob=blobs[name]['sha'], sha256=hashlib.sha256(data).hexdigest(), restoredGitLineEndings=normalized)
result = []
for row in rows:
    name = 'src/' + row['sourceRelativePath']
    value = matched.get(name)
    if value:
        status = 'pinned_git_bytes_match' if value['sha256'] == row['sha256'] else 'pinned_git_bytes_differ'
    elif row['sourceRelativePath'].startswith('out/'):
        status = 'generated_not_in_git'
    else:
        status = 'not_in_pinned_git'
    if name in blobs and value is None:
        raise ValueError('Pinned Git entry missing from archive: ' + name)
    result.append(dict(sourceRelativePath=row['sourceRelativePath'], sha256=row['sha256'], bytes=row['bytes'], status=status, pinnedGit=value))
report = dict(schema=1, sourceCommit=tree['sha'], sourceArchiveSha256=archive_sha,
              abi=receipt['abi'], compilerRun=receipt['run'], dependenciesInputSha256=receipt['targetDependencyInventorySha256'],
              counts=dict(Counter(row['status'] for row in result)), files=result,
              scope='Actual target compiler inputs matched to exact pinned Git bytes. Generated/toolchain files and license associations remain separate; this is not final NOTICE acceptance.')
with args.output.open('x', encoding='utf-8') as stream:
    json.dump(report, stream, indent=2)
    stream.write('\n')
print(json.dumps(report['counts']))
