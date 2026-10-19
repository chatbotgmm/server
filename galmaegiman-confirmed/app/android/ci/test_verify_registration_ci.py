#!/usr/bin/env python3
"""Local checker tests. These are NOT Android, Robolectric or real APK tests."""
import contextlib
import io
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import zipfile
import zlib

import verify_registration_ci as verifier


def png(width, height, color=0):
    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data +
                struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))
    raw = b"".join(b"\0" + bytes((color, 42, 77)) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" +
            chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class CheckerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def invoke(self, function, args):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            function(args)
        return output.getvalue()

    def junit(self, tests=1, skipped=0, failures=0, detail=None):
        if detail is None:
            detail = '<testcase name="renamedValidTest" classname="Example"/>'
        (self.root / "TEST-Example.xml").write_text(
            '<testsuite name="Example" tests="%d" skipped="%d" failures="%d" errors="0">%s</testsuite>' %
            (tests, skipped, failures, detail))
        return SimpleNamespace(directory=str(self.root), minimum_tests=1, previous_skipped=None,
                               require_suite=[], require_unskipped_suite=[], output=None)

    def test_valid_png_stream(self):
        self.assertEqual(verifier.png_dimensions(png(8, 4)), (8, 4))

    def test_corrupt_png_crc_rejected(self):
        data = bytearray(png(8, 4)); data[35] ^= 1
        with self.assertRaises(ValueError):
            verifier.png_dimensions(bytes(data))

    def test_truncated_png_rejected(self):
        with self.assertRaises(ValueError):
            verifier.png_dimensions(png(8, 4)[:-4])

    def test_junit_test_rename_does_not_fail(self):
        result = self.invoke(verifier.verify_junit, self.junit())
        self.assertIn('"passed": 1', result)

    def test_failed_junit_rejected(self):
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_junit, self.junit(failures=1))

    def test_hidden_junit_failure_rejected(self):
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_junit, self.junit(detail='<testcase name="x"><failure/></testcase>'))

    def test_missing_required_suite_rejected(self):
        args = self.junit(); args.require_suite = ["Missing"]
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_junit, args)

    def test_junit_reduced_aggregate_rejected(self):
        args = self.junit(); args.minimum_tests = 2
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_junit, args)

    def test_increased_skip_is_reported_not_silently_ignored_or_blocked(self):
        args = self.junit(skipped=1, detail='<testcase name="x"><skipped/></testcase>')
        args.previous_skipped = 0
        self.assertIn("::warning::Skipped tests increased", self.invoke(verifier.verify_junit, args))

    def test_required_capture_suite_cannot_be_skipped(self):
        args = self.junit(skipped=1, detail='<testcase name="x"><skipped/></testcase>')
        args.require_unskipped_suite = ["Example"]
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_junit, args)

    def make_captures(self):
        findings = {}
        for index, size in enumerate(verifier.SIZES):
            name = "home-%dx%d" % size
            (self.root / (name + ".png")).write_bytes(png(*size, color=index))
            findings[name] = []
        (self.root / "findings.json").write_text(json.dumps(findings))
        return SimpleNamespace(kind="home", directory=str(self.root), output=None)

    def test_expected_pngs_and_empty_findings_pass(self):
        self.invoke(verifier.verify_captures, self.make_captures())

    def test_capture_missing_file_fails(self):
        args = self.make_captures(); (self.root / "home-915x412.png").unlink()
        with self.assertRaises(FileNotFoundError):
            self.invoke(verifier.verify_captures, args)

    def test_capture_wrong_dimensions_fails(self):
        args = self.make_captures(); (self.root / "home-915x412.png").write_bytes(png(10, 10))
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_nonempty_geometry_findings_fail(self):
        args = self.make_captures()
        path = self.root / "findings.json"; findings = json.loads(path.read_text())
        findings["home-915x412"] = ["overlap"]
        path.write_text(json.dumps(findings))
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_incomplete_findings_fail(self):
        args = self.make_captures(); (self.root / "findings.json").write_text("{}")
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_source_allowlist_mismatch_fails(self):
        lock = json.loads(verifier.LOCK.read_text())
        with self.assertRaises(ValueError):
            verifier.verify_runtime(lambda _: b"", [], lock)

    def test_corrupted_runtime_payload_fails(self):
        lock = json.loads(verifier.LOCK.read_text())
        with self.assertRaises(ValueError):
            verifier.verify_runtime(lambda _: b"corrupt", [entry["path"] for entry in lock["files"]], lock)


if __name__ == "__main__":
    unittest.main()
