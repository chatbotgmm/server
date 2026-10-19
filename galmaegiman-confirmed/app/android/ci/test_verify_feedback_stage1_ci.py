#!/usr/bin/env python3
"""Synthetic validator tests. Passing these does not claim native/device execution."""
import contextlib
import copy
import io
import json
from pathlib import Path
import random
import struct
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zlib

import verify_feedback_stage1_ci as verifier


def shop_metric(profile=(915, 374, 160, 1.0, [0, 0, 0, 0]), state="first-page"):
    w, h, dpi, font, insets = profile
    density = dpi / 160
    scale = lambda value: verifier.java_round(value * density)
    safe = [scale(insets[0]), scale(insets[1]), w - scale(insets[2]), h - scale(insets[3])]
    dx, dy = scale(80 / 3 + 1), scale(72 / 3 + 1)
    gap = scale(6)
    card_width = (safe[2] - safe[0] - 4 * gap) // 3
    cards = []
    tags = verifier.SHOP_SECOND_CARDS if state == "second-page" else verifier.SHOP_FIRST_CARDS
    for i, tag in enumerate(tags):
        left, top = safe[0] + gap + i * (card_width + gap), safe[1] + scale(50)
        bounds = [left, top, left + card_width, safe[3] - scale(50)]
        reading = [left + dx, top + dy, bounds[2] - dx, bounds[3] - dy]
        texts = []
        text_tags = ["shop-card-name", "shop-card-price"]
        if tag != "shop-card-shop:tickets":
            text_tags.append("shop-card-state")
        for j, key in enumerate(text_tags):
            y = reading[1] + scale(44 + j * 18)
            box = [reading[0] + scale(2), y, reading[2] - scale(2), y + scale(12)]
            text = {"tag": key, "text": "실제 문자열", "expectedText": "실제 문자열",
                    "fontSizePx": 12 * density * font, "expectedFontSizePx": 12 * density * font,
                    "viewRect": box[:], "visibleRect": box[:], "inkRects": [box[:]],
                    "layoutRects": [box[:]], "ellipsisCount": 0, "lineCount": 1, "fullText": True}
            texts.append(text)
        cards.append({"tag": tag, "rect": bounds, "visibleRect": bounds[:], "readingRect": reading,
                      "iconRect": [reading[0], reading[1], reading[0] + scale(30), reading[1] + scale(30)],
                      "iconImageRect": [reading[0], reading[1], reading[0] + scale(30), reading[1] + scale(30)],
                      "minIconHeightPx": scale(24), "asset": verifier.SHOP_ASSET, "assetSha": verifier.SHOP_ASSET_SHA, "texts": texts})
    controls = []
    names = list(tags) + ["JournalButton:‹", "JournalButton:›", "JournalButton:−", "JournalButton:＋",
                          "JournalButton:최대", "EditText:1", "JournalButton:땅콩 부족" if state == "blocked-selected"
                          else "JournalButton:선택 상품 구매", "header-1", "header-2", "header-3"]
    for i in range(13):
        x = safe[0] + (i % 6) * scale(48)
        y = safe[1] if i < 6 else safe[3] - scale(48)
        box = [x, y, x + scale(48), y + scale(48)]
        controls.append({"tag": names[i], "rect": box, "visibleRect": box[:],
                         "enabled": True, "minTouchTargetPx": scale(48)})
    for control in controls:
        if control["tag"] == "JournalButton:‹": control["enabled"] = state == "second-page"
        if control["tag"] == "JournalButton:›": control["enabled"] = state != "second-page"
        if control["tag"] == "JournalButton:땅콩 부족": control["enabled"] = False
    return {"passed": True, "actualNativeViews": True, "sourceCommit": "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89",
            "widthPx": w, "heightPx": h, "widthDp": w / density, "heightDp": h / density,
            "densityDpi": dpi, "fontScale": font, "insetsDp": insets[:], "state": state,
            "cardCount": 3, "alignedCardRects": True, "apiWrites": 0, "cards": cards, "controls": controls}


def mode_metric(profile_id="short-915x374-mdpi", state="ready"):
    w, h, dpi, font, insets = verifier.MODE_PROFILES[profile_id]
    density = dpi / 160
    scale = lambda x: verifier.java_round(x * density)
    edges = lambda values: dict(zip(verifier.EDGES, values))
    safe = [insets[0], insets[1], w - insets[2], h - insets[3]]
    actions, texts = {}, {}
    def label(value, bounds, framed=False):
        ink = [bounds[0] + 4, bounds[1] + 4, bounds[2] - 4, bounds[3] - 4]
        reading = [bounds[0] + 2, bounds[1] + 2, bounds[2] - 2, bounds[3] - 2] if framed else bounds[:]
        return {"text": value, "boundsPx": edges(bounds), "visibleBoundsPx": edges(bounds),
                "glyphBoundsPx": edges(ink), "parentFrameBoundsPx": edges(bounds),
                "readingBoundsPx": edges(reading), "readingInsetsPx": edges([2] * 4 if framed else [0] * 4),
                "decorativeFrameChecked": framed, "frameKind": "GameButtonDrawable" if framed else "none",
                "layoutHeightPx": bounds[3] - bounds[1] - 4, "contentHeightPx": bounds[3] - bounds[1],
                "textLength": verifier.utf16_length(value), "laidOutTextEnd": verifier.utf16_length(value),
                "ellipsisCount": 0, "ellipsisAllowed": False, "fullContentDescription": value,
                "textSizePx": 16 * density * font, "expectedTextSizePx": 16 * density * font,
                "autoSizeTextType": 0, "textScaleX": 1.0}
    for i, key in enumerate(verifier.MODE_ACTIONS):
        x, y = safe[0] + scale(10 + i % 4 * 100), safe[1] + scale(10 + i // 4 * 60)
        bounds = [x, y, x + scale(90), y + scale(48)]
        actions[key] = {"boundsPx": edges(bounds), "visibleBoundsPx": edges(bounds),
                        "minTouchTargetPx": int(__import__("math").ceil(48 * density)), "enabled": key != "modes-adventure"}
        texts[key] = label("실제 버튼", bounds, True)
    y = safe[1] + scale(140)
    rank_text = "순위 불러오는 중" if state == "loading" else "순위 조회 실패\n순위에서 다시 확인" if state == "error" else "승리 +20점"
    texts["modes-rank-status"] = label(rank_text, [safe[0] + scale(10), y, safe[0] + scale(200), y + scale(25)])
    containers = {}
    for i, key in enumerate(verifier.MODE_CONTAINERS):
        x = safe[0] + scale(10 + i * 220)
        bounds = [x, safe[3] - scale(100), x + scale(200), safe[3] - scale(10)]
        containers[key] = {"boundsPx": edges(bounds), "visibleBoundsPx": edges(bounds)}
    if state == "empty":
        y = safe[1] + scale(175)
        texts["modes-rank-required-team"] = label("편성 3마리 필요", [safe[0] + scale(10), y, safe[0] + scale(200), y + scale(25)])
        y = safe[3] - scale(80)
        texts["TextView-10"] = label("아직 편성한 갈매미가 없어요.", [safe[0] + scale(20), y, safe[0] + scale(200), y + scale(25)])
    return {"profile": profile_id, "state": state, "screenWidthPx": w, "screenHeightPx": h,
            "densityDpi": dpi, "fontScale": font, "safeInsetsPx": edges(insets), "safeBoundsPx": edges(safe),
            "actualNativeViews": True, "apiWrites": 0, "actions": actions, "texts": texts, "containers": containers,
            "scrollContainers": 0, "decorativeFrameChecks": 8, "actionOverlapChecks": 28,
            "loadedPortraits": 6, "realBundledImages": True, "pendingFetches": 1 if state == "loading" else 0,
            "densityInterpretation": verifier.density_interpretation(profile_id), "findings": []}


def mode_manifest():
    profiles, captures = [], []
    for name, (w, h, dpi, font, insets) in verifier.MODE_PROFILES.items():
        profiles.append({"name": name, "widthPx": w, "heightPx": h, "densityDpi": dpi,
                         "fontScale": font, "insetsPx": dict(zip(verifier.EDGES, insets)),
                         "safeWidthDp": (w - insets[0] - insets[2]) * 160 / dpi,
                         "safeHeightDp": (h - insets[1] - insets[3]) * 160 / dpi,
                         "densityInterpretation": verifier.density_interpretation(name)})
        for state in verifier.MODE_STATES:
            captures.append({"filename": name + "-" + state + ".png", "profile": name, "state": state})
    return {"schemaVersion": 1, "suite": "feedback-stage1-modes", "baseline": "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89",
            "profiles": profiles, "states": list(verifier.MODE_STATES), "captures": captures,
            "requiredActions": list(verifier.MODE_ACTIONS), "requiredContainers": list(verifier.MODE_CONTAINERS),
            "reference": {"widthPx": 2048, "heightPx": 838, "densityDpi": "unknown"}}


def png(width, height):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    row = random.Random(width + height).randbytes(width * 3)
    raw = (b"\0" + row) * height
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 0)) + chunk(b"IEND", b""))


class StageOneValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.junit = self.root / "junit"
        self.junit.mkdir()
        self.full = self.write_junit()
        self.profile = (915, 374, 160, 1.0, [0, 0, 0, 0])
        self.shop = shop_metric()
        self.mode = mode_metric()

    def tearDown(self):
        self.temp.cleanup()

    def write_junit(self, skipped=16, passed=334):
        classes = {"legacy.Baseline": ["legacy" + str(i) for i in range(passed + skipped)], **verifier.SUITES}
        totals, suites = dict(tests=0, failures=0, errors=0, skipped=0), {}
        for name, methods in classes.items():
            count_skipped = skipped if name == "legacy.Baseline" else 0
            counts = dict(tests=len(methods), failures=0, errors=0, skipped=count_skipped)
            root = ET.Element("testsuite", name=name, **{key: str(value) for key, value in counts.items()})
            for i, method in enumerate(methods):
                case = ET.SubElement(root, "testcase", name=method, classname=name)
                if i < count_skipped:
                    ET.SubElement(case, "skipped")
            ET.ElementTree(root).write(self.junit / ("TEST-" + name + ".xml"), encoding="utf-8")
            suites[name] = counts
            for key, value in counts.items():
                totals[key] += value
        totals["passed"] = totals["tests"] - totals["skipped"]
        return {"status": "passed", "totals": totals, "suites": suites, "warnings": []}

    def mutate_junit(self, edit):
        path = self.junit / ("TEST-org.dpdns.gmm.NativeShopCardBoundsTest.xml")
        tree = ET.parse(path)
        edit(tree.getroot())
        tree.write(path)

    def assert_shop_fails(self):
        with self.assertRaises((ValueError, KeyError, TypeError)):
            verifier.verify_shop_metric(self.shop, self.profile, "first-page")

    def test_full_suite_334_passes_16_skips_plus_seven_new_passes(self):
        result = verifier.verify_junit(self.junit, self.full)
        self.assertEqual(result["totals"], dict(tests=357, passed=341, skipped=16, errors=0, failures=0))

    def test_stage_one_child_skip_cannot_hide_behind_zero_suite_counter(self):
        self.mutate_junit(lambda root: ET.SubElement(root.find("testcase"), "skipped"))
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_stage_one_child_failure_cannot_hide_behind_zero_suite_counter(self):
        self.mutate_junit(lambda root: ET.SubElement(root.find("testcase"), "failure"))
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_missing_stage_one_method_fails(self):
        self.mutate_junit(lambda root: root.find("testcase").set("name", "differentMethod"))
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_wrong_stage_one_classname_fails(self):
        self.mutate_junit(lambda root: root.find("testcase").set("classname", "OtherClass"))
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_stale_full_suite_report_fails(self):
        self.full["totals"]["passed"] += 1
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_missing_stage_one_suite_fails(self):
        (self.junit / "TEST-org.dpdns.gmm.NativeModesShortViewportTest.xml").unlink()
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, self.full)

    def test_reducing_existing_suite_fails(self):
        full = self.write_junit(passed=333)
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, full)

    def test_increasing_legacy_skips_fails(self):
        full = self.write_junit(skipped=17)
        with self.assertRaises(ValueError): verifier.verify_junit(self.junit, full)

    def test_actual_shop_geometry_passes(self):
        result = verifier.verify_shop_metric(self.shop, self.profile, "first-page")
        self.assertEqual(result, dict(cards=3, texts=8, controls=13))

    def test_shop_has_seventeen_profiles_and_fifty_one_cases(self):
        profiles = verifier.shop_profiles()
        self.assertEqual(len(profiles), 17)
        self.assertEqual(len(profiles) * len(verifier.SHOP_STATES), 51)
        self.assertEqual({p[2] for p in profiles.values() if p[:2] == (2048, 838)}, {360, 420})

    def test_shop_real_density_cannot_be_relabelled(self):
        self.shop["densityDpi"] = 320
        self.assert_shop_fails()

    def test_shop_font_scale_cannot_be_relabelled(self):
        self.shop["fontScale"] = 1.3
        self.assert_shop_fails()

    def test_shop_missing_insets_fails(self):
        del self.shop["insetsDp"]
        self.assert_shop_fails()

    def test_shop_fake_native_flag_fails(self):
        self.shop["actualNativeViews"] = False
        self.assert_shop_fails()

    def test_shop_api_write_fails(self):
        self.shop["apiWrites"] = 1
        self.assert_shop_fails()

    def test_shop_card_order_identity_loss_fails(self):
        self.shop["cards"][0]["tag"] = self.shop["cards"][1]["tag"]
        self.assert_shop_fails()

    def test_shop_card_alignment_cannot_hide_behind_true_flag(self):
        self.shop["cards"][0]["rect"][3] += 2
        self.assert_shop_fails()

    def test_shop_partial_card_visibility_fails(self):
        self.shop["cards"][0]["visibleRect"][3] -= 1
        self.assert_shop_fails()

    def test_shop_outer_card_cannot_substitute_for_reading_bounds(self):
        self.shop["cards"][0]["readingRect"] = self.shop["cards"][0]["rect"][:]
        self.assert_shop_fails()

    def test_shop_artwork_sha_mismatch_fails(self):
        self.shop["cards"][0]["assetSha"] = "0" * 64
        self.assert_shop_fails()

    def test_shop_glyph_crossing_border_fails(self):
        card = self.shop["cards"][0]
        card["texts"][0]["inkRects"][0][0] = card["readingRect"][0] - 1
        self.assert_shop_fails()

    def test_shop_layout_crossing_border_fails(self):
        card = self.shop["cards"][0]
        card["texts"][0]["layoutRects"][0][2] = card["readingRect"][2] + 1
        self.assert_shop_fails()

    def test_shop_blank_text_fails(self):
        self.shop["cards"][0]["texts"][0]["text"] = ""
        self.assert_shop_fails()

    def test_shop_missing_text_fails(self):
        self.shop["cards"][0]["texts"].pop()
        self.assert_shop_fails()

    def test_shop_ellipsis_fails(self):
        self.shop["cards"][0]["texts"][0]["ellipsisCount"] = 1
        self.assert_shop_fails()

    def test_shop_boolean_counter_fails(self):
        self.shop["cards"][0]["texts"][0]["ellipsisCount"] = False
        self.assert_shop_fails()

    def test_shop_shortened_copy_fails(self):
        self.shop["cards"][0]["texts"][0]["text"] = "짧음"
        self.assert_shop_fails()

    def test_shop_font_shrink_fails(self):
        self.shop["cards"][0]["texts"][0]["fontSizePx"] -= 1
        self.assert_shop_fails()

    def test_shop_nonfinite_measurement_fails(self):
        self.shop["cards"][0]["texts"][0]["inkRects"][0][0] = float("nan")
        self.assert_shop_fails()

    def test_shop_clipped_control_fails(self):
        self.shop["controls"][0]["visibleRect"][3] -= 1
        self.assert_shop_fails()

    def test_shop_zero_size_control_fails(self):
        self.shop["controls"][0]["rect"][2] = self.shop["controls"][0]["rect"][0]
        self.assert_shop_fails()

    def test_shop_sub48dp_control_fails(self):
        control = self.shop["controls"][0]
        control["rect"][2] -= 1
        control["visibleRect"] = control["rect"][:]
        self.assert_shop_fails()

    def test_shop_missing_controls_fails(self):
        self.shop["controls"] = []
        self.assert_shop_fails()

    def test_png_valid_stream_is_checked(self):
        path = self.root / "test.png"
        path.write_bytes(png(96, 64))
        self.assertEqual(verifier.verify_png(path, 96, 64)["width"], 96)

    def test_png_corruption_is_rejected(self):
        path = self.root / "test.png"
        data = bytearray(png(96, 64)); data[40] ^= 1; path.write_bytes(data)
        with self.assertRaises(ValueError): verifier.verify_png(path, 96, 64)

    def test_wrong_png_dimensions_are_rejected(self):
        path = self.root / "test.png"; path.write_bytes(png(96, 64))
        with self.assertRaises(ValueError): verifier.verify_png(path, 97, 64)

    def test_missing_png_is_rejected(self):
        with self.assertRaises(OSError): verifier.verify_png(self.root / "missing.png", 96, 64)

    def build_shop_evidence(self):
        folder = self.root / "feedback-stage1-shop"
        folder.mkdir()
        geometry, findings = {}, {}
        for profile_id, profile in verifier.shop_profiles().items():
            for state in verifier.SHOP_STATES:
                name = state + "-" + profile_id
                geometry[name] = shop_metric(profile, state)
                findings[name] = []
                (folder / (name + ".png")).touch()
        (folder / "geometry.json").write_text(json.dumps(geometry))
        (folder / "findings.json").write_text(json.dumps(findings))
        return folder

    def shop_fixture_verify(self):
        # This test isolates the 51-case contract. Real PNG parsing has separate
        # positive/negative tests; empty placeholder files are never native proof.
        with patch.object(verifier, "verify_png", side_effect=lambda path, w, h: {"file": path.name, "width": w, "height": h}):
            return verifier.verify_shop(self.root)

    def test_all_fifty_one_shop_profiles_validate(self):
        self.build_shop_evidence()
        report = self.shop_fixture_verify()
        self.assertEqual(report["screenshots"], 51)
        self.assertEqual(report["cards"], 153)
        self.assertEqual(report["texts"], 425)
        self.assertEqual(report["controls"], 663)

    def test_shop_missing_capture_case_fails(self):
        folder = self.build_shop_evidence()
        next(folder.glob("*.png")).unlink()
        with self.assertRaises(ValueError): self.shop_fixture_verify()

    def test_shop_extra_capture_case_fails(self):
        folder = self.build_shop_evidence()
        (folder / "unexpected.png").touch()
        with self.assertRaises(ValueError): self.shop_fixture_verify()

    def test_shop_missing_geometry_case_fails(self):
        folder = self.build_shop_evidence(); path = folder / "geometry.json"
        value = json.loads(path.read_text()); value.pop(next(iter(value))); path.write_text(json.dumps(value))
        with self.assertRaises(ValueError): self.shop_fixture_verify()

    def test_shop_nonempty_findings_fail(self):
        folder = self.build_shop_evidence(); path = folder / "findings.json"
        value = json.loads(path.read_text()); value[next(iter(value))] = ["bottom clipped"]; path.write_text(json.dumps(value))
        with self.assertRaises(ValueError): self.shop_fixture_verify()

    def test_shop_missing_findings_case_fails(self):
        folder = self.build_shop_evidence(); path = folder / "findings.json"
        value = json.loads(path.read_text()); value.pop(next(iter(value))); path.write_text(json.dumps(value))
        with self.assertRaises(ValueError): self.shop_fixture_verify()

    def test_shop_wrong_required_control_fails(self):
        self.shop["controls"][3]["tag"] = "extra-control"
        self.assert_shop_fails()

    def test_shop_wrong_purchase_availability_fails(self):
        next(c for c in self.shop["controls"] if c["tag"] == "JournalButton:선택 상품 구매")["enabled"] = False
        self.assert_shop_fails()

    def test_shop_wrong_pager_availability_fails(self):
        next(c for c in self.shop["controls"] if c["tag"] == "JournalButton:‹")["enabled"] = True
        self.assert_shop_fails()

    def test_shop_wrong_source_baseline_fails(self):
        self.shop["sourceCommit"] = "other"
        self.assert_shop_fails()

    def test_shop_duplicate_control_tag_fails(self):
        self.shop["controls"][0]["tag"] = self.shop["controls"][1]["tag"]
        self.assert_shop_fails()

    def test_shop_text_icon_overlap_fails(self):
        card = self.shop["cards"][0]
        card["iconRect"] = card["texts"][0]["inkRects"][0][:]
        self.assert_shop_fails()

    def test_shop_text_lines_overlap_each_other_fails(self):
        card = self.shop["cards"][0]
        card["texts"][1] = {**copy.deepcopy(card["texts"][0]), "tag": "shop-card-price"}
        self.assert_shop_fails()

    def test_shop_tiny_drawn_icon_fails(self):
        card = self.shop["cards"][0]
        card["iconImageRect"][3] = card["iconImageRect"][1] + 23
        self.assert_shop_fails()

    def test_shop_drawn_icon_clipping_fails(self):
        card = self.shop["cards"][0]
        card["iconImageRect"][2] = card["iconRect"][2] + 1
        self.assert_shop_fails()

    def assert_mode_fails(self):
        with self.assertRaises((ValueError, KeyError, TypeError)):
            verifier.verify_modes_metric(self.mode, "short-915x374-mdpi", "ready")

    def build_modes_evidence(self):
        folder = self.root / "feedback-stage1-modes"
        folder.mkdir()
        metrics, findings = {}, {}
        for profile in verifier.MODE_PROFILES:
            for state in verifier.MODE_STATES:
                name = profile + "-" + state + ".png"
                metrics[name] = mode_metric(profile, state)
                findings[name] = []
                (folder / name).touch()
        (folder / "metrics.json").write_text(json.dumps(metrics))
        (folder / "findings.json").write_text(json.dumps(findings))
        (folder / "manifest.json").write_text(json.dumps(mode_manifest()))
        return folder

    def modes_fixture_verify(self):
        with patch.object(verifier, "verify_png", side_effect=lambda path, w, h: {"file": path.name, "width": w, "height": h}):
            return verifier.verify_modes(self.root)

    def test_positive_modes_geometry_passes(self):
        result = verifier.verify_modes_metric(self.mode, "short-915x374-mdpi", "ready")
        self.assertEqual(result, dict(actions=8, texts=9, decorative_frame_checks=8))

    def test_all_thirty_two_modes_profiles_validate(self):
        self.build_modes_evidence()
        report = self.modes_fixture_verify()
        self.assertEqual(report["screenshots"], 32)
        self.assertEqual(report["actions"], 256)

    def test_complete_synthetic_evidence_counts_both_modules(self):
        self.build_modes_evidence(); self.build_shop_evidence()
        with patch.object(verifier, "verify_png", side_effect=lambda path, w, h: {"file": path.name, "width": w, "height": h}):
            report = verifier.verify(self.root, self.junit, self.full)
        self.assertEqual(report["screenshots"], 83)
        self.assertEqual(report["manual_visual_review"], "pending")
        self.assertEqual(report["actual_device_review"], "not_run")

    def test_modes_clipped_bottom_button_fails(self):
        self.mode["actions"]["modes-pvp-team"]["visibleBoundsPx"]["bottom"] -= 1
        self.assert_mode_fails()

    def test_modes_zero_size_button_fails(self):
        box = self.mode["actions"]["modes-pvp-team"]["boundsPx"]
        box["bottom"] = box["top"]
        self.assert_mode_fails()

    def test_modes_missing_action_fails(self):
        del self.mode["actions"]["modes-pvp-team"]
        self.assert_mode_fails()

    def test_modes_overlap_fails(self):
        self.mode["actions"]["modes-pvp-team"] = copy.deepcopy(self.mode["actions"]["modes-normal"])
        self.assert_mode_fails()

    def test_modes_missing_summary_fails(self):
        del self.mode["containers"]["modes-story-summary"]
        self.assert_mode_fails()

    def test_modes_wrong_insets_fail(self):
        self.mode["safeInsetsPx"]["left"] = 1
        self.assert_mode_fails()

    def test_modes_density_assumption_fails(self):
        self.mode["densityInterpretation"] = "verified user DPI"
        self.assert_mode_fails()

    def test_modes_scrolling_fails(self):
        self.mode["scrollContainers"] = 1
        self.assert_mode_fails()

    def test_modes_loading_counter_in_ready_state_fails(self):
        self.mode["pendingFetches"] = 1
        self.assert_mode_fails()

    def test_modes_missing_bundled_portraits_fails(self):
        self.mode["loadedPortraits"] = 5
        self.assert_mode_fails()

    def test_modes_subminimum_touch_fails(self):
        action = self.mode["actions"]["modes-pvp-team"]
        action["boundsPx"]["bottom"] -= 1
        action["visibleBoundsPx"] = copy.deepcopy(action["boundsPx"])
        self.assert_mode_fails()

    def test_modes_ellipsis_in_action_fails(self):
        self.mode["texts"]["modes-normal"]["ellipsisCount"] = 1
        self.assert_mode_fails()

    def test_modes_action_cannot_self_authorize_ellipsis(self):
        self.mode["texts"]["modes-normal"].update(ellipsisCount=1, ellipsisAllowed=True)
        self.assert_mode_fails()

    def test_modes_rank_readonly_ellipsis_retains_accessible_copy(self):
        self.mode["texts"]["modes-rank-status"].update(ellipsisCount=1, ellipsisAllowed=True)
        verifier.verify_modes_metric(self.mode, "short-915x374-mdpi", "ready")

    def test_modes_ellipsis_without_accessible_copy_fails(self):
        self.mode["texts"]["modes-rank-status"].update(ellipsisCount=1, ellipsisAllowed=True, fullContentDescription="")
        self.assert_mode_fails()

    def test_modes_glyph_crossing_reading_frame_fails(self):
        text = self.mode["texts"]["modes-normal"]
        text["glyphBoundsPx"]["left"] = text["readingBoundsPx"]["left"] - 1
        self.assert_mode_fails()

    def test_modes_reading_insets_must_match_parent_frame(self):
        self.mode["texts"]["modes-normal"]["readingInsetsPx"]["left"] += 1
        self.assert_mode_fails()

    def test_modes_native_layout_clipping_fails(self):
        text = self.mode["texts"]["modes-normal"]
        text["layoutHeightPx"] = text["contentHeightPx"] + 1
        self.assert_mode_fails()

    def test_modes_missing_full_text_fails(self):
        self.mode["texts"]["modes-normal"]["laidOutTextEnd"] -= 1
        self.assert_mode_fails()

    def test_modes_auto_shrunk_text_fails(self):
        self.mode["texts"]["modes-normal"]["autoSizeTextType"] = 1
        self.assert_mode_fails()

    def test_modes_nonfinite_glyph_fails(self):
        self.mode["texts"]["modes-normal"]["glyphBoundsPx"]["left"] = float("inf")
        self.assert_mode_fails()

    def test_modes_wrong_reference_dpi_claim_fails(self):
        folder = self.build_modes_evidence(); path = folder / "manifest.json"
        manifest = json.loads(path.read_text()); manifest["reference"]["densityDpi"] = 420; path.write_text(json.dumps(manifest))
        with self.assertRaises(ValueError): self.modes_fixture_verify()

    def test_modes_missing_capture_case_fails(self):
        folder = self.build_modes_evidence(); next(folder.glob("*.png")).unlink()
        with self.assertRaises(ValueError): self.modes_fixture_verify()

    def test_modes_missing_geometry_case_fails(self):
        folder = self.build_modes_evidence(); path = folder / "metrics.json"
        value = json.loads(path.read_text()); value.pop(next(iter(value))); path.write_text(json.dumps(value))
        with self.assertRaises(ValueError): self.modes_fixture_verify()

    def test_modes_nonempty_findings_fails(self):
        folder = self.build_modes_evidence(); path = folder / "findings.json"
        value = json.loads(path.read_text()); value[next(iter(value))] = ["clipped"]; path.write_text(json.dumps(value))
        with self.assertRaises(ValueError): self.modes_fixture_verify()

    def test_modes_missing_required_team_notice_fails(self):
        metric = mode_metric(state="empty"); del metric["texts"]["modes-rank-required-team"]
        with self.assertRaises(ValueError): verifier.verify_modes_metric(metric, "short-915x374-mdpi", "empty")

    def test_modes_empty_notice_cannot_self_authorize_ellipsis(self):
        metric = mode_metric(state="empty")
        metric["texts"]["TextView-10"].update(ellipsisCount=1, ellipsisAllowed=True)
        with self.assertRaises(ValueError): verifier.verify_modes_metric(metric, "short-915x374-mdpi", "empty")

    def test_failure_writes_nonpassing_report(self):
        full_path = self.root / "full.json"; full_path.write_text(json.dumps(self.full))
        output = self.root / "validation.json"
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            result = verifier.main(["--native-review", str(self.root), "--test-results", str(self.junit),
                                    "--full-suite-report", str(full_path), "--output", str(output)])
        self.assertEqual(result, 1)
        self.assertEqual(json.loads(output.read_text())["status"], "failed")

    def test_workflow_gates_and_preserves_stage1_before_reruns(self):
        workflow = (Path(__file__).resolve().parents[4] / ".github/workflows/home-assembly-check.yml").read_text()
        gate = workflow.index("id: feedback_stage1_gate")
        evidence = workflow.index("id: feedback_stage1_evidence")
        rerun = workflow.index("- name: Targeted home lobby capture")
        assemble = workflow.index("- name: Assemble debug and separately installable preview APKs")
        self.assertLess(gate, evidence); self.assertLess(evidence, rerun); self.assertLess(rerun, assemble)
        assembly_block = workflow[assemble:workflow.index("id: assemble", assemble)]
        for identifier in ("feedback_stage1_checks", "feedback_stage1_gate", "feedback_stage1_evidence"):
            self.assertIn("steps." + identifier + ".outcome == 'success'", assembly_block)
        for suite in verifier.SUITES:
            self.assertIn("--require-unskipped-suite " + suite, workflow)
            self.assertNotIn("--tests " + suite, workflow)
            self.assertIn("TEST-" + suite + ".xml", workflow[evidence:rerun])
        self.assertIn("if-no-files-found: error", workflow[evidence:rerun])

    def test_native_methods_are_required_exactly(self):
        self.assertEqual(len(verifier.SUITES["org.dpdns.gmm.NativeModesShortViewportTest"]), 5)
        self.assertEqual(len(verifier.SUITES["org.dpdns.gmm.NativeShopCardBoundsTest"]), 2)
        self.assertEqual(sum(map(len, verifier.SUITES.values())), 7)


if __name__ == "__main__":
    unittest.main()
