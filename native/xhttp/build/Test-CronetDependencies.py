"""Offline validation of dependency evidence parsing; no compiler/device execution."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('cronet_dependency_collector',
    Path(__file__).with_name('Collect-CronetDependencies.py'))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class DependencyEvidenceTests(unittest.TestCase):
    def test_records_preserve_spaces_and_repeated_headers(self):
        records = collector.parse_deps(
            'obj/a.o: #deps 2, deps mtime 123 (VALID)\n'
            '    ../../file with spaces.h\n    ../../shared.h\n\n'
            'obj/b.o: #deps 1, deps mtime 456 (VALID)\n    ../../shared.h\n')
        self.assertEqual(len(records), 2)
        self.assertEqual(records[0]['dependencies'], ['../../file with spaces.h', '../../shared.h'])
        self.assertEqual(records[1]['dependencies'], ['../../shared.h'])

    def test_stale_evidence_is_retained(self):
        records = collector.parse_deps('obj/a.o: #deps 0, deps mtime 0 (STALE)\n')
        self.assertEqual(records[0]['status'], 'STALE')
        self.assertEqual(records[0]['dependencies'], [])

    def test_empty_evidence_is_not_success(self):
        with self.assertRaises(ValueError):
            collector.parse_deps('\n')

    def test_orphan_dependency_is_rejected(self):
        with self.assertRaises(ValueError):
            collector.parse_deps('    orphan.h\n')

    def test_missing_or_extra_dependencies_are_rejected(self):
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaises(ValueError):
                collector.parse_deps(f'obj/a.o: #deps {count}, deps mtime 1 (VALID)\n    header.h\n')

    def test_unknown_format_is_not_silently_ignored(self):
        for value in ('ninja failed\n', 'obj/a.o: #deps 0, deps mtime 1 (UNKNOWN)\n'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                collector.parse_deps(value)


if __name__ == '__main__':
    unittest.main()
