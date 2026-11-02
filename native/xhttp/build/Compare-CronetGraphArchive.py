"""Compare pinned Cronet archive object names to target GN units, not binary identity."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--graph', type=Path, required=True)
parser.add_argument('--archive', type=Path, required=True)
parser.add_argument('--llvm-ar', type=Path, required=True)
parser.add_argument('--cpu', choices=('arm64', 'arm', 'x64', 'x86'), required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
inputs = json.loads((Path(__file__).resolve().parent.parent / 'provenance/cronet-android-inputs.json').read_text())
abi = {'arm64': 'arm64-v8a', 'arm': 'armeabi-v7a', 'x64': 'x86_64', 'x86': 'x86'}[args.cpu]
row = next(row for row in inputs['architectures'] if row['abi'] == abi)
expected = next(item for item in row['files'] if item['name'] == 'libcronet.a')
with args.archive.open('rb') as stream:
    actual = hashlib.file_digest(stream, 'sha256').hexdigest()
if actual != expected['sha256'] or args.archive.stat().st_size != expected['bytes']:
    raise ValueError('Archive is not the pinned published ABI input')
verification = json.loads((args.graph / 'verification.json').read_text())
if verification['target'] != 'android/' + args.cpu or verification['publisherBuilderSha256'] != 'f07ff6f4783115d5031c4a9633deb510260c007d0f49f6eef5c0b3eba466c4ea':
    raise ValueError('Graph target or publisher flags provenance mismatch')
all_targets = json.loads((args.graph / 'targets.json').read_text())
root_label = '//components/cronet:cronet_static'
root = json.loads((args.graph / 'closure.json').read_text())[root_label]
labels = {root_label, *root['deps']}
if labels - all_targets.keys():
    raise ValueError('Incomplete target closure')
toolchain = '//build/toolchain/android:android_clang_' + args.cpu
if root['toolchain'] != toolchain:
    raise ValueError('Unexpected root toolchain')
units = []
for label in sorted(labels):
    target = all_targets[label]
    if target['toolchain'] != toolchain or target['type'] not in ('static_library', 'source_set'):
        continue
    for source in target.get('sources', []):
        path = PurePosixPath(source)
        if path.suffix in ('.c', '.cc', '.cpp', '.cxx', '.m', '.mm', '.s', '.S', '.asm'):
            units.append(dict(target=label, source=source, object=path.stem + '.o', generated=source.startswith('//out/')))
members = subprocess.check_output([str(args.llvm_ar.resolve()), 't', str(args.archive.resolve())], text=True, timeout=120).splitlines()
compiled, archived = Counter(unit['object'] for unit in units), Counter(members)
missing, extra = sorted((archived - compiled).elements()), sorted((compiled - archived).elements())
report = dict(sourceCommit=inputs['naiveproxy_commit'], target='android/' + args.cpu,
    toolchain=toolchain, dependencyLabels=len(labels), compileUnits=len(units),
    generatedCompileUnits=sum(unit['generated'] for unit in units), publishedArchiveMembers=len(members),
    publishedArchiveSha256=actual, objectNameMultisetMatches=not missing and not extra,
    missing=missing, extra=extra, units=units,
    limits=['Object-name multiset correspondence, not source/binary equivalence',
            'Generated code, included headers and final notice mapping require additional evidence'])
with args.output.open('x', encoding='utf-8') as stream:
    json.dump(report, stream, indent=2)
    stream.write('\n')
print(json.dumps({key: report[key] for key in ('target', 'compileUnits', 'generatedCompileUnits', 'publishedArchiveMembers', 'objectNameMultisetMatches')}))
if missing or extra:
    raise SystemExit('Archive object correspondence mismatch; retained report for inspection')
