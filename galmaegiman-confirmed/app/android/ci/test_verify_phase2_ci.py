#!/usr/bin/env python3
"""Synthetic checker tests. These do not execute or certify Android UI."""
import contextlib
import copy
import io
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest
import zlib

import verify_phase2_ci as verifier


def png(width, height, value=0):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = (b"\0" + bytes((value, 47, 83)) * width) * height
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class PhaseTwoCheckerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.output = self.root / "native-review" / "sample"
        self.output.mkdir(parents=True)
        self.junit = self.root / "junit"; self.junit.mkdir()
        self.lock = {"files": [{"path": "scene.png", "dimensions": [8, 4]}]}
        self.contract = {"id": "sample", "status": "synthetic_checker_test_only",
                         "output_subdirectory": "sample", "expected_capture_count": 4,
                         "test_suites": [{"name": "Review", "minimum_tests": 1}, {"name": "State", "minimum_tests": 2}],
                         "variants": [{"suffix": "12x8", "width": 12, "height": 8},
                                      {"suffix": "16x9", "width": 16, "height": 9}],
                         "screens": [{"stem": "one", "audit_id": "home", "expected_fields": {"rootKind": "page", "sceneAsset": "scene.png"}, "minimum_integers": {"actions": 1}},
                                     {"stem": "two", "audit_id": "settings", "expected_fields": {"rootKind": "page", "sceneAsset": "scene.png"}, "minimum_integers": {"actions": 1}}],
                         "measurements": {"true_fields": ["actualNativeViews"], "zero_integer_fields": ["apiWrites"],
                                          "positive_integer_fields": ["actions", "text"],
                                          "nonnegative_integer_fields": ["checkedBounds", "renderMillis"],
                                          "array_fields": ["shortened"], "duration_fields": ["renderMillis"],
                                          "read_only_summary_field": "shortened", "scene_asset_field": "sceneAsset",
                                          "scene_width_field": "sceneWidth", "scene_height_field": "sceneHeight"}}
        findings, metrics = {}, {}
        for index, (screen, variant) in enumerate((s, v) for s in self.contract["screens"] for v in self.contract["variants"]):
            name = screen["stem"] + "-" + variant["suffix"]
            (self.output / (name + ".png")).write_bytes(png(variant["width"], variant["height"], index))
            findings[name] = []
            metrics[name] = {"actualNativeViews": True, "apiWrites": 0, "actions": 1, "text": 2,
                             "checkedBounds": 1, "renderMillis": 3, "shortened": [], "rootKind": "page",
                             "sceneAsset": "scene.png", "sceneWidth": 8, "sceneHeight": 4}
        (self.output / "findings.json").write_text(json.dumps(findings))
        (self.output / "metrics.json").write_text(json.dumps(metrics))
        self.write_suite("Review", 1)
        self.write_suite("State", 2)

    def tearDown(self):
        self.temporary.cleanup()

    def write_suite(self, name, count, skipped=False, failed=False):
        cases = ''.join('<testcase name="case%d">%s</testcase>' % (i, '<skipped/>' if skipped else '<failure/>' if failed else '') for i in range(count))
        text = '<testsuite name="%s" tests="%d" skipped="%d" failures="%d" errors="0" time="1.2">%s</testsuite>' % (name, count, count if skipped else 0, count if failed else 0, cases)
        (self.junit / ("TEST-" + name + ".xml")).write_text(text)

    def verify(self):
        return verifier.verify_module(self.contract, self.output, self.junit, self.lock)

    def metric(self, field, value):
        path = self.output / "metrics.json"; metrics = json.loads(path.read_text())
        metrics["one-12x8"][field] = value; path.write_text(json.dumps(metrics))

    def test_complete_measured_captures_and_suites_pass(self):
        report = self.verify()
        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["capture_count"], 4)
        self.assertEqual(report["fixture_duration_totals_ms"], {"renderMillis": 12})

    def test_missing_junit_fails_even_with_all_pngs(self):
        (self.junit / "TEST-Review.xml").unlink()
        self.assertEqual(self.verify()["status"], "failed")

    def test_skipped_suite_fails_even_with_all_pngs(self):
        self.write_suite("Review", 1, skipped=True)
        self.assertEqual(self.verify()["status"], "failed")

    def test_reduced_state_test_count_fails(self):
        self.write_suite("State", 1)
        self.assertEqual(self.verify()["status"], "failed")

    def test_state_failure_keeps_capture_diagnostics_but_blocks_release(self):
        self.write_suite("State", 2, failed=True)
        report = self.verify(); self.assertEqual(report["status"], "failed")
        self.assertEqual(report["capture_count"], 4)

    def test_missing_png_fails(self):
        (self.output / "one-12x8.png").unlink()
        self.assertEqual(self.verify()["missing_pngs"], ["one-12x8.png"])

    def test_extra_png_fails_exact_set(self):
        (self.output / "extra.png").write_bytes(png(12, 8))
        self.assertEqual(self.verify()["status"], "failed")

    def test_wrong_png_size_fails(self):
        (self.output / "one-12x8.png").write_bytes(png(10, 10))
        self.assertEqual(self.verify()["status"], "failed")

    def test_corrupted_png_fails_and_retains_other_cases(self):
        data = bytearray((self.output / "one-12x8.png").read_bytes()); data[35] ^= 1
        (self.output / "one-12x8.png").write_bytes(data)
        report = self.verify(); self.assertEqual(report["status"], "failed")
        self.assertEqual(report["capture_count"], 3)

    def test_empty_findings_alone_is_not_evidence(self):
        (self.output / "metrics.json").write_text("{}")
        self.assertEqual(self.verify()["status"], "failed")

    def test_nonempty_findings_fail(self):
        path = self.output / "findings.json"; findings = json.loads(path.read_text())
        findings["one-12x8"] = ["overlap"]; path.write_text(json.dumps(findings))
        self.assertEqual(self.verify()["status"], "failed")

    def test_boolean_counter_rejected(self):
        self.metric("actions", True)
        self.assertEqual(self.verify()["status"], "failed")

    def test_server_writes_rejected(self):
        self.metric("apiWrites", 1)
        self.assertEqual(self.verify()["status"], "failed")

    def test_wrong_scene_dimension_rejected(self):
        self.metric("sceneWidth", 0)
        self.assertEqual(self.verify()["status"], "failed")

    def test_wrong_window_root_rejected(self):
        self.metric("rootKind", "dialog")
        self.assertEqual(self.verify()["status"], "failed")

    def test_synthetic_fixture_cannot_replace_normal_proof(self):
        self.contract["screens"][0]["expected_fields"]["syntheticItemFixture"] = False
        self.metric("syntheticItemFixture", True)
        self.assertEqual(self.verify()["status"], "failed")

    def test_wrong_canonical_fixture_source_rejected(self):
        self.contract["screens"][0]["expected_fields"]["itemDefinitionSource"] = "battle.yaml:273–277@9d8d7b4"
        self.metric("itemDefinitionSource", "synthetic")
        self.assertEqual(self.verify()["status"], "failed")

    def test_duplicate_visual_cases_flagged_for_review_not_misrepresented(self):
        (self.output / "two-12x8.png").write_bytes((self.output / "one-12x8.png").read_bytes())
        report = self.verify(); self.assertEqual(report["status"], "passed")
        self.assertEqual(len(report["matching_png_groups_for_direct_review"]), 1)

    def test_shortened_read_only_summaries_remain_visible(self):
        self.metric("shortened", [{"text": "long summary"}])
        self.assertEqual(len(self.verify()["shortened_read_only_summaries_for_direct_review"]), 1)

    def test_failed_module_still_writes_diagnostic_report(self):
        (self.output / "one-12x8.png").unlink()
        contract = self.root / "contract.json"; contract.write_text(json.dumps(self.contract))
        lock = self.root / "lock.json"; lock.write_text(json.dumps(self.lock))
        report = self.root / "failed.json"
        with contextlib.redirect_stdout(io.StringIO()):
            result = verifier.module_command(SimpleNamespace(contract=str(contract), native_review=str(self.output.parent), junit=str(self.junit), asset_lock=str(lock), output=str(report)))
        self.assertEqual(result, 1)
        self.assertEqual(json.loads(report.read_text())["status"], "failed")

    def test_pending_section_blocks_readiness(self):
        path = self.root / "registry.json"
        path.write_text(json.dumps({"modules": [], "pending_modules": {"economy": "not frozen"}, "all_sections_ready": False}))
        output = self.root / "ready.json"
        with contextlib.redirect_stdout(io.StringIO()):
            result = verifier.readiness_command(SimpleNamespace(registry=str(path), output=str(output)))
        self.assertEqual(result, 1)
        self.assertFalse(json.loads(output.read_text())["all_app_states_complete"])

    def test_economy_geometry_filename_is_used_without_synthetic_metrics(self):
        self.contract["metrics_file"] = "geometry.json"
        (self.output / "metrics.json").rename(self.output / "geometry.json")
        self.assertEqual(self.verify()["status"], "passed")

    def test_economy_geometry_missing_fails_even_if_metrics_exists(self):
        self.contract["metrics_file"] = "geometry.json"
        self.assertEqual(self.verify()["status"], "failed")

    def test_economy_read_only_truncations_remain_reported(self):
        self.contract["measurements"]["report_counter_fields"] = ["checkedBounds"]
        self.assertEqual(self.verify()["measured_counter_totals_for_direct_review"], {"checkedBounds": 4})

    def test_practice_art_must_have_exact_three_real_cards(self):
        self.contract["screens"][0]["expected_fields"]["checkedArt"] = 3
        self.metric("checkedArt", 0)
        self.assertEqual(self.verify()["status"], "failed")

    def ready_registry(self):
        modules = []
        for name in ("menus", "realtime", "economy", "extras"):
            path = self.root / (name + ".json")
            path.write_text(json.dumps({"id": name, "status": "source_complete_native_execution_pending"}))
            modules.append({"id": name, "contract": path.name})
        return {"modules": modules, "required_modules": [m["id"] for m in modules],
                "pending_modules": {}, "all_sections_ready": True}

    def readiness(self, registry):
        path = self.root / "registry.json"; path.write_text(json.dumps(registry))
        with contextlib.redirect_stdout(io.StringIO()):
            return verifier.readiness_command(SimpleNamespace(registry=str(path), output=str(self.root / "ready.json")))

    def test_four_source_groups_ready_does_not_claim_native_success(self):
        self.assertEqual(self.readiness(self.ready_registry()), 0)
        report = json.loads((self.root / "ready.json").read_text())
        self.assertFalse(report["all_app_states_complete"])
        self.assertIn("Source-contract readiness only", report["scope"])

    def test_missing_group_blocks_even_with_ready_flag(self):
        registry = self.ready_registry(); registry["modules"].pop()
        self.assertEqual(self.readiness(registry), 1)

    def test_unfrozen_contract_blocks_even_with_ready_flag(self):
        registry = self.ready_registry()
        (self.root / "extras.json").write_text(json.dumps({"id": "extras", "status": "draft"}))
        self.assertEqual(self.readiness(registry), 1)

    def asset_locks(self):
        original = []
        for index, role in enumerate(["background"] * 7 + ["ui"] * 6 + ["protected_home_art"] * 11):
            data = png(8, 4, index)
            original.append({"path": str(index) + ".png", "sha256": verifier.sha256(data),
                             "bytes": len(data), "dimensions": [8, 4], "role": role})
        addition = {**original[0], "path": "workshop.png"}
        (self.root / "assets").mkdir()
        (self.root / "assets/workshop.png").write_bytes(png(8, 4, 0))
        phase = {"files": [addition], "preserved_phase_one_files": original,
                 "source_prefix": "assets", "apk_prefix": "assets/native/release-v1",
                 "alias_of": {"workshop.png": "0.png"}}
        full = {"files": original + [addition], "source_prefix": "assets",
                "apk_prefix": phase["apk_prefix"], "background_count": 8, "ui_count": 6,
                "protected_home_art_count": 11,
                "runtime_bytes": sum(entry["bytes"] for entry in original + [addition])}
        return phase, full

    def test_approved_workshop_alias_preserves_old_entries(self):
        phase, full = self.asset_locks()
        report = verifier.verify_additional_assets(self.root, phase, full)
        self.assertEqual(report["preserved_files"], 24)
        self.assertEqual(report["additional_files"], 1)

    def test_expanded_lock_cannot_silently_change_old_sprite(self):
        phase, full = self.asset_locks(); full = copy.deepcopy(full)
        full["files"][0]["sha256"] = "changed"
        with self.assertRaises(ValueError): verifier.verify_additional_assets(self.root, phase, full)

    def test_workshop_alias_cannot_change_art_bytes(self):
        phase, full = self.asset_locks()
        (self.root / "assets/workshop.png").write_bytes(png(8, 4, 1))
        with self.assertRaises(ValueError): verifier.verify_additional_assets(self.root, phase, full)

    def test_owner_confirmed_contract_counts_and_case_names_are_unique(self):
        directory = Path(__file__).resolve().parent
        contracts = directory / "phase2-contracts"
        if not contracts.exists(): contracts = directory / "contracts"
        for name, count in (("menus", 84), ("realtime", 124), ("economy", 50), ("extras", 28)):
            contract = json.loads((contracts / (name + ".json")).read_text())
            names = [screen["stem"] + "-" + variant["suffix"] for screen in contract["screens"] for variant in contract["variants"]]
            self.assertEqual(len(names), count)
            self.assertEqual(len(set(names)), count)
            self.assertEqual(contract["expected_capture_count"], count)



class MenuWindowGeometryTests(unittest.TestCase):
    def setUp(self):
        directory = Path(__file__).resolve().parent
        contract_path = directory / "phase2-contracts/menus.json"
        if not contract_path.exists(): contract_path = directory / "menus.json"
        self.contract = json.loads(contract_path.read_text())
        self.screen = next(x for x in self.contract["screens"] if x["stem"] == "growth-details")
        self.variant = next(x for x in self.contract["variants"] if x["suffix"] == "1006x411")
        self.measured = {"actualNativeViews": True, "actions": 5, "boundsChecks": 2,
            "dialogBoundsChecks": 2, "measuredWidth": 900, "measuredHeight": 359,
            "safeWidth": 960, "safeHeight": 391, "textLabels": 36, "inlineChoices": 0,
            "shortenedReadOnlySummaries": [], "fixtureReads": [], "fixtureWrites": 0,
            "dialog": True, "itemDefinitionSource": "battle.yaml:273–277@9d8d7b4",
            "syntheticItemFixture": False, "visualReview": "pending PNG inspection"}

    def verify(self):
        verifier.measurement_check(self.measured, self.screen, self.contract, {}, self.variant)

    def reject(self, key, value):
        self.measured[key] = value
        with self.assertRaises(ValueError): self.verify()

    def test_actual_growth_details_dialog_measurements_pass(self): self.verify()
    def test_ordinary_page_keeps_its_own_root_contract(self):
        self.screen = next(x for x in self.contract["screens"] if x["stem"] == "growth")
        self.measured.update(dialog=False, dialogBoundsChecks=0, boundsChecks=5, measuredWidth=1006, measuredHeight=411)
        self.verify()
    def test_growth_details_cannot_be_relabelled_page(self): self.reject("dialog", False)
    def test_dialog_width_cannot_exceed_safe_viewport(self): self.reject("measuredWidth", 961)
    def test_dialog_height_must_keep_sixteen_pixel_margin(self): self.reject("measuredHeight", 376)
    def test_missing_dialog_assertion_count_fails(self): self.reject("dialogBoundsChecks", 1)
    def test_invented_extra_dialog_assertions_fail(self): self.reject("dialogBoundsChecks", 3)
    def test_dialog_assertions_must_be_included_in_bounds_total(self): self.reject("boundsChecks", 1)
    def test_safe_width_must_match_actual_insets(self): self.reject("safeWidth", 1006)
    def test_safe_height_must_match_actual_insets(self): self.reject("safeHeight", 411)
    def test_zero_measured_window_fails(self): self.reject("measuredWidth", 0)
    def test_boolean_geometry_is_not_a_measurement(self): self.reject("dialogBoundsChecks", True)
    def test_unknown_metric_is_not_arbitrarily_allowed(self): self.reject("uncheckedGeometry", True)
    def test_missing_new_measurement_fails(self):
        del self.measured["measuredHeight"]
        with self.assertRaises(ValueError): self.verify()
    def test_original_fixture_invariants_remain_required(self): self.reject("syntheticItemFixture", True)
    def test_exact_84_names_and_eight_tests_unchanged(self):
        names = {s["stem"] + "-" + v["suffix"] for s in self.contract["screens"] for v in self.contract["variants"]}
        self.assertEqual(len(names), 84)
        self.assertEqual(self.contract["test_suites"][0]["minimum_tests"], 8)


if __name__ == "__main__":
    unittest.main()
