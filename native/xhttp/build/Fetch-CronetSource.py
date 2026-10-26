"""Fetch pinned external Cronet source and verify every blob against its Git tree.

This verifies source identity, not that this source rebuilds the prebuilt library
or that the complete build dependency/license closure has been established.
Archives are inspected without extraction. Use a new project output directory.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile
import urllib.request

COMMIT = '72a06c9fca0e2d228588c7f3074bf7efff3ff686'
REPOSITORY = 'SagerNet/naiveproxy'
ARCHIVE_SHA256 = '396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19'
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--verify-only', action='store_true', help='Check an already downloaded archive without network')
args = parser.parse_args()
if args.verify_only:
    assert args.output.is_dir()
else:
    args.output.mkdir(parents=True, exist_ok=False)

def fetch(url, destination):
    request = urllib.request.Request(url, headers={'User-Agent': 'NOVA-source-audit'})
    with urllib.request.urlopen(request, timeout=90) as response, destination.open('xb') as target:
        shutil.copyfileobj(response, target)

tree_file = args.output / 'git-tree.json'
tree_url = f'https://api.github.com/repos/{REPOSITORY}/git/trees/{COMMIT}?recursive=1'
if not args.verify_only:
    fetch(tree_url, tree_file)
tree = json.loads(tree_file.read_text(encoding='utf-8'))
assert not tree.get('truncated'), 'Incomplete upstream tree'
assert not any(entry['type'] == 'commit' for entry in tree['tree']), 'Unbundled Git submodule'
expected = {entry['path']: entry for entry in tree['tree'] if entry['type'] == 'blob'}
assert len(expected) > 30_000
archive = args.output / f'naiveproxy-{COMMIT}.tar.gz'
archive_url = f'https://codeload.github.com/{REPOSITORY}/tar.gz/{COMMIT}'
if not args.verify_only:
    fetch(archive_url, archive)
with archive.open('rb') as stream:
    archive_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
assert archive_sha == ARCHIVE_SHA256, 'Archive differs from reviewed pinned download'
seen = set()
newline_exports = []
prefix = f'naiveproxy-{COMMIT}/'
total = 0
with tarfile.open(archive, 'r|gz') as source:
    for member in source:
        if member.name == prefix.rstrip('/'):
            assert member.isdir()
            continue
        assert member.name.startswith(prefix), member.name
        relative = member.name[len(prefix):]
        assert '..' not in PurePosixPath(relative).parts and not relative.startswith('/')
        if member.isdir():
            continue
        assert relative in expected and relative not in seen, relative
        entry = expected[relative]
        if member.issym():
            assert entry['mode'] == '120000', relative
            data = member.linkname.encode('utf-8')
            digest = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
            size = len(data)
        else:
            assert member.isfile() and entry['mode'] in ('100644', '100755'), relative
            digest_state = hashlib.sha1(f'blob {member.size}\0'.encode())
            size = 0
            with source.extractfile(member) as stream:
                while chunk := stream.read(1024 * 1024):
                    digest_state.update(chunk)
                    size += len(chunk)
            digest = digest_state.hexdigest()
        if size != entry['size'] or digest != entry['sha']:
            # GitHub archive export can apply eol=crlf attributes. Accept only
            # when reversing CRLF reconstructs the exact pinned Git blob hash.
            assert member.isfile() and member.size <= 8 * 1024 * 1024, relative
            # Streaming tar cannot reopen the consumed member; collect failures
            # and verify them in a second archive pass below.
            newline_exports.append({'path': relative, 'git_sha1': entry['sha'], 'git_size': entry['size']})
        total += size
        seen.add(relative)
assert seen == set(expected), f'Missing {len(set(expected) - seen)} files'
pending = {entry['path']: entry for entry in newline_exports}
if pending:
    with tarfile.open(archive, 'r|gz') as source:
        for member in source:
            relative = member.name[len(prefix):]
            if relative not in pending:
                continue
            entry = pending.pop(relative)
            data = source.extractfile(member).read().replace(b'\r\n', b'\n')
            digest = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
            assert len(data) == entry['git_size'] and digest == entry['git_sha1'], relative
    assert not pending
manifest = {'repository': f'https://github.com/{REPOSITORY}', 'commit': COMMIT,
            'tree_url': tree_url, 'archive_url': archive_url, 'archive_sha256': archive_sha,
            'verified_blobs': len(seen), 'source_bytes': total, 'unbundled_gitlinks': 0,
            'archive_crlf_exports_matching_original_git_blob_after_lf_restoration': newline_exports,
            'limits': ['Build dependency/source/license closure not yet certified',
                       'Prebuilt Cronet rebuild equivalence not verified']}
(args.output / 'verification.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'PASS {len(seen)} source blobs verified; archive SHA256 {archive_sha}', flush=True)
