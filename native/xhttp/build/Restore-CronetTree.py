"""Restore exact Cronet Git blobs/modes on Linux, including contained symbolic links."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument('--input', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
if os.name != 'posix':
    raise RuntimeError('Use Linux for faithful source symbolic links')
commit = '72a06c9fca0e2d228588c7f3074bf7efff3ff686'
archive = args.input / f'naiveproxy-{commit}.tar.gz'
with archive.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == '396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19'
tree = json.loads((args.input / 'git-tree.json').read_text())
assert tree['sha'] == commit and not tree['truncated']
assert not any(row['type'] == 'commit' for row in tree['tree'])
expected = {row['path']: row for row in tree['tree'] if row['type'] == 'blob'}
links = {name for name, row in expected.items() if row['mode'] == '120000'}
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=False)
seen = set()
normalized = []


def git_hash(data):
    return hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()


with tarfile.open(archive, 'r|gz') as source:
    for member in source:
        prefix = f'naiveproxy-{commit}/'
        if member.name == prefix[:-1]:
            assert member.isdir()
            continue
        assert member.name.startswith(prefix)
        name = member.name[len(prefix):]
        parts = PurePosixPath(name)
        assert not parts.is_absolute() and '..' not in parts.parts
        if member.isdir():
            continue
        assert name in expected and name not in seen
        assert not any(str(parent) in links for parent in parts.parents)
        entry = expected[name]
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if member.issym():
            assert entry['mode'] == '120000'
            data = member.linkname.encode()
            destination = posixpath.normpath(posixpath.join(str(parts.parent), member.linkname))
            assert not member.linkname.startswith('/') and destination != '..' and not destination.startswith('../')
            assert destination in expected
            assert len(data) == entry['size'] and git_hash(data) == entry['sha']
            os.symlink(member.linkname, target)
        else:
            assert member.isfile() and entry['mode'] in ('100644', '100755') and member.size <= 64 * 1024 * 1024
            data = source.extractfile(member).read()
            if git_hash(data) != entry['sha']:
                data = data.replace(b'\r\n', b'\n')
                normalized.append(name)
            assert len(data) == entry['size'] and git_hash(data) == entry['sha'], name
            with target.open('xb') as output:
                output.write(data)
            target.chmod(0o755 if entry['mode'] == '100755' else 0o644)
        seen.add(name)
assert seen == set(expected)
report = dict(sourceCommit=commit, verifiedBlobs=len(seen), symbolicLinks=len(links),
              restoredGitLineEndings=normalized, scope='Source identity/modes only; no graph or binary equivalence claim')
(root.parent / 'restoration.json').write_text(json.dumps(report, indent=2) + '\n')
print(f'PASS restored {len(seen)} Git blobs and {len(links)} contained symbolic links', flush=True)
