"""Retain actual Ninja dependency evidence in disposable CI; not license closure."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def parse_deps(text):
    records = []
    current = None
    for line in text.splitlines():
        if not line:
            continue
        if line.startswith('    '):
            if current is None:
                raise ValueError('Dependency without an object record')
            current['dependencies'].append(line[4:])
            continue
        match = re.fullmatch(r'(.+): #deps (\d+), deps mtime (\d+) \((VALID|STALE)\)', line)
        if not match:
            raise ValueError('Unrecognized Ninja dependency record')
        current = dict(object=match[1], expectedCount=int(match[2]), status=match[4], dependencies=[])
        records.append(current)
    for record in records:
        if len(record['dependencies']) != record['expectedCount']:
            raise ValueError('Ninja dependency count mismatch')
    if not records:
        raise ValueError('No Ninja dependency evidence')
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if os.name != 'posix' or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise RuntimeError('Only disposable Linux CI is supported')
    source, out = args.source.resolve(), args.out.resolve()
    if not out.is_relative_to(source / 'out') or not (out / 'build.ninja').is_file():
        raise ValueError('Expected a generated output inside this source tree')
    args.output.mkdir(parents=True, exist_ok=False)
    raw = subprocess.check_output(['ninja', '-C', str(out), '-t', 'deps'], timeout=120)
    (args.output / 'ninja-deps.txt').write_bytes(raw)
    records = parse_deps(raw.decode('utf-8'))
    files = []
    for name in sorted({name for record in records for name in record['dependencies']}):
        path = (out / name).resolve()
        contained = path.is_relative_to(source)
        row = dict(path=name, containedInSourceTree=contained, present=path.is_file())
        # Never read unrelated host files just because a tool emitted their paths.
        if contained and row['present']:
            with path.open('rb') as stream:
                row['sha256'] = hashlib.file_digest(stream, 'sha256').hexdigest()
            row['sourceRelativePath'] = path.relative_to(source).as_posix()
            row['bytes'] = path.stat().st_size
        files.append(row)
    report = dict(schema=1, records=records, files=files,
                  staleRecords=sum(row['status'] == 'STALE' for row in records),
                  rawSha256=hashlib.sha256(raw).hexdigest(),
                  scope='Actual Ninja dependency database; includes host/generator objects, not necessarily final archive members. Header origin and notice mapping remain required.')
    (args.output / 'dependencies.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Inventoried {len(records)} object records and {len(files)} dependency paths')


if __name__ == '__main__':
    main()
