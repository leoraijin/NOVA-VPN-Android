"""Restore an already signed APK, checking all artifacts against a release manifest."""
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys

def checked(path, expected):
    data = Path(path).read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Artifact checksum mismatch: ' + Path(path).name)
    return data

def restore(manifest_path, base_path, delta_path, output_path):
    manifest = json.loads(Path(manifest_path).read_text(encoding='utf-8'))
    result = bytearray(checked(base_path, manifest['baseSha256']))
    checked(delta_path, manifest['deltaSha256'])
    with gzip.open(delta_path, 'rb') as stream:
        if stream.read(6) != b'NOVAD1':
            raise ValueError('Unknown delta format')
        length = struct.unpack('<q', stream.read(8))[0]
        if length != manifest['apkLength'] or not 0 < length < 512*1024*1024:
            raise ValueError('Unexpected APK length')
        del result[length:]
        if len(result) < length:
            result.extend(bytes(length - len(result)))
        previous_end = 0
        while True:
            record = stream.read(12)
            if not record:
                break
            if len(record) != 12:
                raise ValueError('Truncated delta record')
            offset, size = struct.unpack('<qi', record)
            if offset < previous_end or size <= 0 or offset + size > length:
                raise ValueError('Invalid delta range')
            replacement = stream.read(size)
            if len(replacement) != size:
                raise ValueError('Truncated delta data')
            result[offset:offset+size] = replacement
            previous_end = offset + size
    if hashlib.sha256(result).hexdigest() != manifest['apkSha256']:
        raise ValueError('Reconstructed APK checksum mismatch')
    Path(output_path).write_bytes(result)
    print('Signed APK reconstructed and verified: ' + manifest['apkSha256'])

if __name__ == '__main__':
    if len(sys.argv) != 5:
        raise SystemExit('Usage: Restore-ReleaseApk.py MANIFEST BASE.apk DELTA.gz OUTPUT.apk')
    restore(*sys.argv[1:])
