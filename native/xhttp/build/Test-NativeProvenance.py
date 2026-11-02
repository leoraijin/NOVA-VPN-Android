"""Exercise actual provenance restore with current proof files and adversarial entries."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('native_provenance', HERE / 'NativeProvenance.py')
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class ProvenanceRoundtripTests(unittest.TestCase):
    def setUp(self):
        self.root = HERE.parents[3] / 'work/provenance-roundtrip-tests'
        self.root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='case-', dir=self.root)
        self.path = Path(self.temp.name)
        assert self.path.resolve().is_relative_to(self.root.resolve())
        self.addCleanup(self.temp.cleanup)

    def archive(self, files):
        stream = io.BytesIO()
        manifest = {}
        with zipfile.ZipFile(stream, 'w') as bundle:
            for name, data in files.items():
                entry = 'provenance/' + name
                bundle.writestr(entry, data)
                manifest[name] = dict(archive=entry, sha256=hashlib.sha256(data).hexdigest())
        stream.seek(0)
        return zipfile.ZipFile(stream), manifest

    def test_current_compressed_proofs_and_license_patch_roundtrip(self):
        folder = HERE.parent / 'provenance'
        names = ['cronet-arm64-units-2026-11-03.json.gz', 'compiler-rt-atomic-vendored.patch', 'cronet-android-inputs.json', 'README.md']
        files = {name: (folder / name).read_bytes() for name in names}
        with self.archive(files)[0] as bundle:
            manifest = {name: dict(archive='provenance/' + name, sha256=hashlib.sha256(data).hexdigest()) for name, data in files.items()}
            policy.restore_provenance(bundle, manifest, self.path / 'restored')
        for name, data in files.items():
            self.assertEqual((self.path / 'restored' / name).read_bytes(), data)

    def test_bad_hash_does_not_write_even_earlier_valid_entry(self):
        bundle, manifest = self.archive({'first.json': b'{}', 'second.json.gz': b'opaque'})
        manifest['second.json.gz']['sha256'] = '0' * 64
        with bundle, self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            policy.restore_provenance(bundle, manifest, self.path / 'restored')
        self.assertFalse((self.path / 'restored').exists())

    def test_names_cannot_escape_or_choose_other_extensions(self):
        for name in ('../escape.json', '/absolute.json', 'C:escape.json', 'folder/file.json', '.json', 'secret.exe', 'blob.gz'):
            with self.subTest(name=name):
                self.assertFalse(policy.valid_name(name))
                with self.assertRaises(ValueError):
                    policy.restore_provenance(None, {name: dict(archive='provenance/' + name)}, self.path / 'restored')
        self.assertFalse((self.path / 'restored').exists())

    def test_archive_member_cannot_redirect_valid_name(self):
        with self.assertRaises(ValueError):
            policy.restore_provenance(None, {'valid.json': dict(archive='../valid.json')}, self.path / 'restored')

    def test_oversized_entry_is_refused_before_output(self):
        bundle, manifest = self.archive({'large.json': b'x' * (policy.MAX_FILE_BYTES + 1)})
        with bundle, self.assertRaisesRegex(ValueError, 'size limit'):
            policy.restore_provenance(bundle, manifest, self.path / 'restored')
        self.assertFalse((self.path / 'restored').exists())

    def test_excessive_document_count_is_rejected_before_archive_read(self):
        manifest = {f'doc-{i}.json': {} for i in range(policy.MAX_DOCUMENTS + 1)}
        with self.assertRaisesRegex(ValueError, 'Too many'):
            policy.restore_provenance(None, manifest, self.path / 'restored')
        self.assertFalse((self.path / 'restored').exists())

    def test_existing_file_is_never_overwritten(self):
        bundle, manifest = self.archive({'first.json': b'new'})
        destination = self.path / 'restored'
        destination.mkdir()
        (destination / 'first.json').write_bytes(b'original')
        with bundle, self.assertRaises(FileExistsError):
            policy.restore_provenance(bundle, manifest, destination)
        self.assertEqual((destination / 'first.json').read_bytes(), b'original')


if __name__ == '__main__':
    unittest.main()
