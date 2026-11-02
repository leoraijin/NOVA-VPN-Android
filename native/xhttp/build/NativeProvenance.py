"""Bounded, checksum-verified native provenance files; compressed data stays opaque."""
import hashlib
from pathlib import Path
import re

MAX_FILE_BYTES = 4 * 1024 * 1024
MAX_TOTAL_BYTES = 32 * 1024 * 1024
MAX_DOCUMENTS = 128


def valid_name(name):
    return isinstance(name, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*\.(?:json(?:\.gz)?|md|patch)', name) is not None


def restore_provenance(bundle, manifest, destination):
    verified = []
    total = 0
    if len(manifest) > MAX_DOCUMENTS:
        raise ValueError('Too many provenance documents')
    for name, item in manifest.items():
        if not valid_name(name) or item['archive'] != 'provenance/' + name:
            raise ValueError('Invalid provenance filename or archive path')
        entry = bundle.getinfo(item['archive'])
        total += entry.file_size
        if entry.file_size > MAX_FILE_BYTES or total > MAX_TOTAL_BYTES:
            raise ValueError('Provenance size limit exceeded')
        data = bundle.read(entry)
        if hashlib.sha256(data).hexdigest() != item['sha256']:
            raise ValueError('Provenance checksum mismatch: ' + name)
        verified.append((name, data))
    # Validate all records before creating or writing any destination file.
    if verified:
        Path(destination).mkdir(parents=True, exist_ok=True)
    for name, data in verified:
        with (Path(destination) / name).open('xb') as output:
            output.write(data)
