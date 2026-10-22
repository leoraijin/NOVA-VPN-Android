"""Reconstruct a locally signed APK; require pinned base, delta and final hashes."""
import gzip
import hashlib
from pathlib import Path
import struct
import sys

BASE_SHA = "43bc0ba4854eefb2093ee9e14d379ceb54b411e2ae0caf6e949f5af8cff2c158"
DELTA_SHA = "7dcaeb532bd8085b9ab7c23aa21e1603a7bea5a2807c0034aec74553c5ffcd39"
APK_SHA = "6a2197d15c6d7442d4ac42120f1d23b2063c17b04d55c2c4a8cf48d7a4d87690"


def checked(path, expected):
    data = Path(path).read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("Artifact checksum mismatch: " + str(path))
    return data


def restore(base_path, delta_path, output_path):
    base = checked(base_path, BASE_SHA)
    checked(delta_path, DELTA_SHA)
    result = bytearray(base)
    with gzip.open(delta_path, "rb") as stream:
        if stream.read(6) != b"NOVAD1":
            raise ValueError("Unknown delta format")
        length_bytes = stream.read(8)
        if len(length_bytes) != 8:
            raise ValueError("Truncated delta header")
        length = struct.unpack("<q", length_bytes)[0]
        if length != 105301203:
            raise ValueError("Unexpected APK length")
        del result[length:]
        if len(result) < length:
            result.extend(bytes(length - len(result)))
        previous_end = 0
        while True:
            record = stream.read(12)
            if not record:
                break
            if len(record) != 12:
                raise ValueError("Truncated delta record")
            offset, size = struct.unpack("<qi", record)
            if offset < previous_end or size <= 0 or offset + size > length:
                raise ValueError("Invalid delta range")
            replacement = stream.read(size)
            if len(replacement) != size:
                raise ValueError("Truncated delta data")
            result[offset:offset + size] = replacement
            previous_end = offset + size
    if hashlib.sha256(result).hexdigest() != APK_SHA:
        raise ValueError("Reconstructed APK checksum mismatch")
    Path(output_path).write_bytes(result)
    print("Signed APK reconstructed and verified: " + APK_SHA)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: Restore-SignedApk.py BASE.apk DELTA.gz OUTPUT.apk")
    restore(*sys.argv[1:])
