#!/usr/bin/env python3
"""Checker tests with explicitly synthetic data, not native UI evidence."""
import contextlib
import io
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import zlib

import verify_theme_ci as verifier


def png(width, height, color=0):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = (b"\0" + bytes((color, 42, 77)) * width) * height
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class ThemeCheckerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.lock = json.loads(verifier.LOCK.read_text())

    def tearDown(self):
        self.temporary.cleanup()

    def invoke(self, function, args):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            function(args)
        return json.loads(output.getvalue())

    def test_only_explicit_added_asset_paths_allowed(self):
        paths = [self.lock["source_prefix"] + "/" + entry["path"]
                 for entry in self.lock["files"] if not entry["baseline"]]
        self.assertEqual(len(paths), 11)
        verifier.allowed_asset_changes(paths, self.lock)

    def test_existing_home_character_cannot_change(self):
        with self.assertRaises(ValueError):
            verifier.allowed_asset_changes([self.lock["source_prefix"] + "/characters/home-guide-map.png"], self.lock)

    def test_new_unapproved_sprite_cannot_be_added(self):
        with self.assertRaises(ValueError):
            verifier.allowed_asset_changes([self.lock["source_prefix"] + "/characters/new.png"], self.lock)

    def test_public_original_cannot_change(self):
        with self.assertRaises(ValueError):
            verifier.allowed_asset_changes(["galmaegiman-confirmed/public/images/C1.png"], self.lock)

    def test_unrelated_native_asset_cannot_change(self):
        with self.assertRaises(ValueError):
            verifier.allowed_asset_changes(["galmaegiman-confirmed/app/android/app/src/main/assets/native/unapproved.json"], self.lock)

    def test_code_file_allowed_by_asset_gate(self):
        verifier.allowed_asset_changes(["galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/AppSkin.java"], self.lock)

    def payload(self):
        roles = ["background"] * 8 + ["ui"] * 6 + ["protected_home_art"] * 11
        data, entries = {}, []
        for index, role in enumerate(roles):
            name = str(index) + ".png"
            image = png(8, 4, index)
            data[name] = image
            entries.append({"path": name, "role": role, "bytes": len(image),
                            "sha256": verifier.sha256(image), "dimensions": [8, 4]})
        return data, {"files": entries, "runtime_bytes": sum(map(len, data.values()))}

    def test_exact_payload_passes(self):
        data, lock = self.payload()
        self.assertEqual(verifier.verify_payload(data.__getitem__, list(data), lock)["files"], 25)

    def test_missing_asset_fails(self):
        data, lock = self.payload(); del data["0.png"]
        with self.assertRaises(ValueError):
            verifier.verify_payload(data.__getitem__, list(data), lock)

    def test_extra_asset_fails(self):
        data, lock = self.payload(); data["extra.png"] = png(8, 4)
        with self.assertRaises(ValueError):
            verifier.verify_payload(data.__getitem__, list(data), lock)

    def test_corrupt_asset_fails(self):
        data, lock = self.payload(); data["0.png"] = data["0.png"][:-1] + b"x"
        with self.assertRaises(ValueError):
            verifier.verify_payload(data.__getitem__, list(data), lock)

    def test_changed_byte_count_still_fails(self):
        data, lock = self.payload()
        data["0.png"] += b"x"
        with self.assertRaisesRegex(ValueError, "byte count changed"):
            verifier.verify_payload(data.__getitem__, list(data), lock)

    def test_changed_dimensions_fail_even_if_content_hash_is_registered(self):
        data, lock = self.payload()
        data["0.png"] = png(9, 4)
        # Isolate the independent dimension guard: updating a hash must not resize art.
        lock["files"][0]["bytes"] = len(data["0.png"])
        lock["files"][0]["sha256"] = verifier.sha256(data["0.png"])
        with self.assertRaisesRegex(ValueError, "dimensions changed"):
            verifier.verify_payload(data.__getitem__, list(data), lock)

    def test_reviewed_home_lighting_keeps_original_canvas_and_exact_identity(self):
        home = next(entry for entry in self.lock["files"]
                    if entry["path"] == "backgrounds/bg-home.png")
        self.assertEqual(home["dimensions"], [1870, 841])
        self.assertEqual(home["bytes"], 3013465)
        self.assertEqual(home["sha256"],
                         "c6a3c3b3b09f13b8659fad7e69c15e913242c022b6111fa6eb34702f5bc1a492")
        self.assertEqual(home["git_blob_sha"], "f568071de2bdae33dd7da33c10d036e5fbb2b43b")
        self.assertTrue(home["baseline"])
        self.assertEqual(sum(entry["bytes"] for entry in self.lock["files"]),
                         self.lock["runtime_bytes"])

    def captures(self):
        contract = json.loads(verifier.CONTRACT.read_text())
        assets = {entry["path"]: entry for entry in self.lock["files"]}
        findings, metrics, index = {}, {}, 0
        for screen in contract["screens"]:
            for variant in contract["variants"]:
                stem = screen["stem"] + "-" + variant["suffix"]
                (self.root / (stem + ".png")).write_bytes(png(variant["width"], variant["height"], index))
                findings[stem] = []
                dimensions = assets[screen["scene_asset"]]["dimensions"]
                # Synthetic unit-test data only. Real producer measures actual views.
                metrics[stem] = {"checkedBounds": 3, "actions": 2, "text": 1,
                                 "sceneAsset": screen["scene_asset"], "sceneWidth": dimensions[0],
                                 "sceneHeight": dimensions[1], "rootKind": screen["root_kind"], "apiWrites": 0,
                                 "apiReads": 0, "actualNativeViews": True}
                index += 1
        (self.root / "findings.json").write_text(json.dumps(findings))
        (self.root / "metrics.json").write_text(json.dumps(metrics))
        return SimpleNamespace(directory=str(self.root), output=None)

    def mutate_metric(self, field, value):
        path = self.root / "metrics.json"
        data = json.loads(path.read_text()); data[next(iter(data))][field] = value
        path.write_text(json.dumps(data))

    def test_all_32_complete_cases_pass(self):
        report = self.invoke(verifier.verify_captures, self.captures())
        self.assertEqual(report["capture_count"], 32)

    def test_empty_findings_without_actual_measurements_fails(self):
        args = self.captures(); (self.root / "metrics.json").write_text("{}")
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_zero_bounds_checks_fail(self):
        args = self.captures(); self.mutate_metric("checkedBounds", 0)
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_boolean_counts_are_not_actual_counts(self):
        args = self.captures(); self.mutate_metric("actions", True)
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_wrong_root_fails(self):
        args = self.captures(); self.mutate_metric("rootKind", "page")
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_missing_scene_fails(self):
        args = self.captures(); self.mutate_metric("sceneWidth", 0)
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_network_write_fails(self):
        args = self.captures(); self.mutate_metric("apiWrites", 1)
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_failed_geometry_fails(self):
        args = self.captures(); path = self.root / "findings.json"; data = json.loads(path.read_text())
        data[next(iter(data))] = ["overlap"]
        path.write_text(json.dumps(data))
        with self.assertRaises(ValueError):
            self.invoke(verifier.verify_captures, args)

    def test_missing_png_fails(self):
        args = self.captures(); next(self.root.glob("*.png")).unlink()
        with self.assertRaises(FileNotFoundError):
            self.invoke(verifier.verify_captures, args)

    def test_coverage_cannot_claim_full_app_complete(self):
        capture_report = self.invoke(verifier.verify_captures, self.captures())
        path = self.root / "capture-report.json"; path.write_text(json.dumps(capture_report))
        report = self.invoke(verifier.verify_coverage, SimpleNamespace(capture_report=str(path), output=None))
        self.assertEqual(report["item_count"], 100)
        self.assertEqual(report["route_count"], 28)
        self.assertEqual(report["representative_audit_items"], 5)
        self.assertFalse(report["all_pages_and_states_complete"])
        self.assertTrue(all(not item["all_required_states_complete"] for item in report["items"]))


if __name__ == "__main__":
    unittest.main()
