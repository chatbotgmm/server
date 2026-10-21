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


class SourceScopeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.lock = {"source_prefix": "galmaegiman-confirmed/art/gpt/registration-v1"}
        self.approved = json.loads(verifier.PROGRESSION_SOURCE_LOCK.read_text())
        for entry in self.approved["files"]:
            data = ("approved synthetic source: " + entry["path"]).encode()
            entry["bytes"] = len(data); entry["sha256"] = verifier.sha256(data)
            file = self.root / entry["path"]; file.parent.mkdir(parents=True, exist_ok=True); file.write_bytes(data)
    def verify(self, paths=None):
        return verifier.verify_source_scope(self.root, list(verifier.PROGRESSION_PATHS) if paths is None else paths,
                                            self.lock, self.approved)
    def prepare_backend_fixture(self, path):
        data = ("approved fixture: " + path).encode()
        source = self.root / path
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_bytes(data)
        self.addCleanup(mock.patch.stopall)
        mock.patch.dict(verifier.BACKEND_FIXTURE_TEST_LOCKS,
                        {path: (len(data), verifier.sha256(data))}).start()
        return source

    def test_backend_fixture_lock_has_only_two_exact_approved_hashes(self):
        self.assertEqual(verifier.BACKEND_FIXTURE_TEST_LOCKS, {
            "galmaegiman-confirmed/test/battle-events.test.ts":
                (13489, "4c38cd9c5cd9333ece04a7b46528db2e08c7aff1317d649ef61817e188d5ec78"),
            "galmaegiman-confirmed/test/game.integration.test.ts":
                (139862, "092e55b3b42fac030d5ac55591960df1dd50a8c15489e12f7a5461765432e533"),
        })

    def test_exact_backend_fixture_paths_and_hashes_pass(self):
        paths = list(verifier.BACKEND_FIXTURE_TEST_LOCKS)
        for path in paths:
            self.prepare_backend_fixture(path)
        report = self.verify(paths)
        self.assertEqual(set(report["approved_backend_fixture_test_sha256"]), set(paths))
        self.assertEqual(report["approved_progression_files"], 14)

    def test_backend_fixture_hash_size_missing_and_redirect_are_rejected(self):
        for path in list(verifier.BACKEND_FIXTURE_TEST_LOCKS):
            for mutation in ("hash", "size", "missing", "symlink"):
                with self.subTest(path=path, mutation=mutation):
                    source = self.prepare_backend_fixture(path)
                    data = source.read_bytes()
                    if mutation == "hash":
                        source.write_bytes(bytes([data[0] ^ 1]) + data[1:])
                    elif mutation == "size":
                        source.write_bytes(data + b"x")
                    elif mutation == "missing":
                        source.unlink()
                    else:
                        target = self.root / "redirected-fixture"
                        target.write_bytes(data)
                        source.unlink()
                        source.symlink_to(target)
                    with self.assertRaises(ValueError):
                        self.verify([path])
                    if source.is_symlink():
                        source.unlink()

    def test_backend_fixture_neighbors_and_unlisted_path_are_rejected(self):
        for path in list(verifier.BACKEND_FIXTURE_TEST_LOCKS):
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    self.verify([path + ".bak"])
        with self.assertRaises(ValueError):
            verifier.verify_backend_fixture_test(self.root, "galmaegiman-confirmed/test/other.test.ts")

    def test_exact_fourteen_backend_paths_and_hashes_pass(self):
        self.assertEqual(self.verify()["approved_progression_files"], 14)
    def test_existing_android_registration_workflow_scope_remains(self):
        self.verify(["galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/MainActivity.java",
                     "galmaegiman-confirmed/art/gpt/registration-v1/manifest.json",
                     ".github/workflows/home-assembly-check.yml"])
    def test_empty_diff_still_checks_locked_sources(self):
        self.verify([])
        file = self.root / self.approved["files"][0]["path"]; file.write_bytes(b"changed")
        with self.assertRaises(ValueError): self.verify([])
    def test_allowed_path_wrong_hash_fails(self):
        file = self.root / self.approved["files"][0]["path"]
        data = bytearray(file.read_bytes()); data[0] ^= 1; file.write_bytes(data)
        with self.assertRaises(ValueError): self.verify()
    def test_allowed_path_wrong_size_fails(self):
        file = self.root / self.approved["files"][0]["path"]; file.write_bytes(file.read_bytes()+b"extra")
        with self.assertRaises(ValueError): self.verify()
    def test_missing_approved_source_fails(self):
        (self.root / self.approved["files"][0]["path"]).unlink()
        with self.assertRaises(ValueError): self.verify()
    def test_symlink_approved_source_fails(self):
        file = self.root / self.approved["files"][0]["path"]; target = self.root/"redirect"
        file.rename(target); file.symlink_to(target)
        with self.assertRaises(ValueError): self.verify()
    def test_extra_allowlist_entry_fails(self):
        self.approved["files"].append(dict(self.approved["files"][0]))
        with self.assertRaises(ValueError): self.verify()
    def test_duplicate_allowlist_entry_fails(self):
        self.approved["files"][1] = dict(self.approved["files"][0])
        with self.assertRaises(ValueError): self.verify()
    def test_missing_allowlist_entry_fails(self):
        self.approved["files"].pop()
        with self.assertRaises(ValueError): self.verify()
    def test_different_path_even_with_valid_hash_fails(self):
        self.approved["files"][0]["path"] = "galmaegiman-confirmed/src/unrelated.ts"
        with self.assertRaises(ValueError): self.verify()
    def test_wrong_authorized_commit_fails(self):
        self.approved["approved_commit"] = "HEAD"
        with self.assertRaises(ValueError): self.verify()
    def test_changed_lock_hash_fails(self):
        self.approved["files"][0]["sha256"] = "0"*64
        with self.assertRaises(ValueError): self.verify()
    def test_wrong_lock_schema_fails(self):
        self.approved["schema"] = "unbounded"
        with self.assertRaises(ValueError): self.verify()

def _add_source_scope_negative_tests():
    forbidden = {
        "backend": "galmaegiman-confirmed/src/admin.ts",
        "backend_neighbor": "galmaegiman-confirmed/src/progression.ts.bak",
        "schema_neighbor": "galmaegiman-confirmed/prisma/seed.ts",
        "economy": "galmaegiman-confirmed/data/economy.json",
        "web": "galmaegiman-confirmed/public/play.html",
        "sprite": "galmaegiman-confirmed/public/thumbs/C1.jpg",
        "original": "galmaegiman-confirmed/public/images/D1.png",
        "battle_art": "galmaegiman-confirmed/public/battle/duel.png",
        "font": "galmaegiman-confirmed/public/fonts/game.ttf",
        "unrelated_test": "galmaegiman-confirmed/test/admin.test.ts",
        "docs": "galmaegiman-confirmed/docs/UNRELATED.md",
        "package": "galmaegiman-confirmed/package.json",
        "other_workflow": ".github/workflows/deploy.yml",
    }
    for name, path in forbidden.items():
        def test(self, path=path):
            with self.assertRaises(ValueError): self.verify([path])
        setattr(SourceScopeTests, "test_reject_unrelated_"+name, test)
_add_source_scope_negative_tests()


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
