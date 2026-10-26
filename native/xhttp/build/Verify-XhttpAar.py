"""Inspect every JNI ELF and 64-bit LOAD/RELRO alignment; does not execute native code."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("aar", type=Path)
parser.add_argument("--output", type=Path)
parser.add_argument("--version", default="1.14.3-lx.23-nova-downlink.3", choices=("1.14.3-lx.23-nova-downlink.3", "1.14.3-lx.23-nova-downlink.4"))
args = parser.parse_args()
expected = {"arm64-v8a": (183, 64), "armeabi-v7a": (40, 32), "x86_64": (62, 64), "x86": (3, 32)}
rows = []
with zipfile.ZipFile(args.aar) as archive:
    names = [name for name in archive.namelist() if name.startswith("jni/") and name.endswith(".so")]
    assert set(names) == {f"jni/{abi}/libbox.so" for abi in expected}, names
    for name in names:
        abi = name.split("/")[1]
        data = archive.read(name)
        assert data[:4] == b"\x7fELF" and data[4] in (1, 2) and data[5] == 1
        wide = data[4] == 2
        machine = struct.unpack_from("<H", data, 18)[0]
        bits = 64 if wide else 32
        assert (machine, bits) == expected[abi], (abi, machine, bits)
        assert struct.unpack_from("<H", data, 16)[0] == 3, "Not a shared object"
        phoff = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)[0]
        stride, count = struct.unpack_from("<HH", data, 54 if wide else 42)
        assert stride >= (56 if wide else 32) and phoff + stride * count <= len(data)
        loads, relro = [], []
        page = 16384 if wide else 4096
        for index in range(count):
            offset = phoff + stride * index
            kind = struct.unpack_from("<I", data, offset)[0]
            number = "<Q" if wide else "<I"
            virtual = struct.unpack_from(number, data, offset + (16 if wide else 8))[0]
            size = struct.unpack_from(number, data, offset + (40 if wide else 20))[0]
            if kind == 1:
                alignment = struct.unpack_from(number, data, offset + (48 if wide else 28))[0]
                file_offset = struct.unpack_from(number, data, offset + (8 if wide else 4))[0]
                assert alignment >= page and alignment & (alignment - 1) == 0, (abi, "LOAD", alignment)
                assert (virtual - file_offset) % page == 0, (abi, "LOAD congruence")
                loads.append(alignment)
            elif kind == 0x6474E552:
                remainder = (virtual + size) % page
                assert remainder == 0, (abi, "RELRO", remainder)
                relro.append(remainder)
        assert loads and relro, (abi, "Missing LOAD or RELRO")
        assert args.version.encode("ascii") in data, (abi, "Missing version marker")
        rows.append({"abi": abi, "bits": bits, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                     "load_alignment": loads, "relro_end_remainder": relro, "checked_page_size": page})
digest = hashlib.sha256()
with args.aar.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
report = {"aar_sha256": digest.hexdigest(), "version": args.version, "libraries": rows}
encoded = json.dumps(report, indent=2)
if args.output:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(encoded + "\n", encoding="utf-8")
print(encoded)
