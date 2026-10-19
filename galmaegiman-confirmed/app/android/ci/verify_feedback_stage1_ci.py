#!/usr/bin/env python3
"""Fail closed on stage-one native evidence; never create screenshots or run devices."""
import argparse
import json
import math
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zlib

from verify_registration_ci import check, emit, png_dimensions, sha256

BASELINE_TOTAL = 350
BASELINE_PASSED = 334
BASELINE_SKIPPED = 16
SUITES = {
    "org.dpdns.gmm.NativeModesShortViewportTest": [
        "captureModesAcrossShortSafeAreas",
        "shortModeButtonsKeepTheirRoutesAndRankConfirmation",
        "rankPendingCompletionUpdatesOnlyItsOwnEpoch",
        "rankCompletionAndBusyModeClicksKeepPendingWriteEpoch",
        "shortWideShortAndInsetReflowPreserveViewsRoutesAndPendingEpoch",
    ],
    "org.dpdns.gmm.NativeShopCardBoundsTest": [
        "renderShopCardsAtShortAspectAndLargeFont",
        "selectionPagingAndQuantityPreserveShopContract",
    ],
}


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def integer(value, minimum=0):
    return type(value) is int and value >= minimum


def rect(value):
    if isinstance(value, dict):
        check(set(value) == {"left", "top", "right", "bottom"}, "Wrong rectangle keys")
        value = [value[k] for k in ("left", "top", "right", "bottom")]
    check(isinstance(value, list) and len(value) == 4 and all(finite(x) for x in value)
          and value[0] < value[2] and value[1] < value[3], "Empty or non-finite measured rectangle")
    return value


def inside(outer, inner, tolerance=0.01):
    outer, inner = rect(outer), rect(inner)
    return (outer[0] - tolerance <= inner[0] and outer[1] - tolerance <= inner[1]
            and inner[2] <= outer[2] + tolerance and inner[3] <= outer[3] + tolerance)


def same_rect(a, b):
    return all(abs(x - y) <= .01 for x, y in zip(rect(a), rect(b)))


def overlap(a, b):
    a, b = rect(a), rect(b)
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def utf16_length(value):
    return len(value.encode("utf-16-le")) // 2


def verify_junit(results, full_report):
    """Check the actual full-suite XML before Gradle replaces it on targeted reruns."""
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = {}
    files = sorted(Path(results).glob("TEST-*.xml"))
    check(files, "Missing full-suite JUnit XML")
    for path in files:
        root = ET.parse(path).getroot()
        name = root.get("name")
        check(root.tag == "testsuite" and name and name not in suites, "Wrong or duplicate JUnit suite")
        counts = {key: int(root.get(key, "-1")) for key in totals}
        check(all(value >= 0 for value in counts.values()), "Missing/negative JUnit counters")
        cases = root.findall("testcase")
        check(counts["tests"] == len(cases), "JUnit case count mismatch: " + name)
        for key, child in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped")):
            check(counts[key] == sum(case.find(child) is not None for case in cases),
                  "JUnit child/count mismatch: " + name + "/" + key)
        check(counts["failures"] == counts["errors"] == 0, "Native tests failed: " + name)
        if name in SUITES:
            methods = [case.get("name") for case in cases]
            check(counts["skipped"] == 0 and sorted(methods) == sorted(SUITES[name]),
                  "Required stage-one method missing, duplicated or skipped: " + name)
            check(all(case.get("classname") == name for case in cases), "Wrong stage-one testcase class")
        suites[name] = counts
        for key, value in counts.items():
            totals[key] += value
    check(set(SUITES) <= set(suites), "Missing stage-one native suites")
    new_tests = sum(len(methods) for methods in SUITES.values())
    totals["passed"] = totals["tests"] - totals["skipped"]
    check(totals["tests"] >= BASELINE_TOTAL + new_tests, "Existing 350-case baseline was reduced")
    check(totals["passed"] >= BASELINE_PASSED + new_tests, "Executed/passed baseline was reduced")
    check(totals["skipped"] <= BASELINE_SKIPPED, "Legacy skip count increased above 16")
    check(full_report.get("status") == "passed" and full_report.get("totals") == totals,
          "Full-suite gate report missing, stale or inconsistent with current XML")
    check(full_report.get("suites") == suites, "Full-suite suite identities/counters changed")
    return {"totals": totals, "stage1_suites": {name: suites[name] for name in SUITES}}


def verify_png(path, width, height, minimum_bytes=10000):
    data = path.read_bytes()
    check(png_dimensions(data) == (width, height), "Wrong PNG dimensions: " + path.name)
    check(len(data) > minimum_bytes, "Missing substantive native scene: " + path.name)
    # Android Bitmap.compress emits non-interlaced 8-bit RGB/RGBA. Require all
    # declared scanlines, not merely a plausible IHDR and a nonempty deflate stream.
    bit_depth, color_type, compression, filtering, interlace = struct.unpack_from(">BBBBB", data, 24)
    check(bit_depth == 8 and color_type in (2, 6) and compression == filtering == interlace == 0,
          "Unexpected native PNG encoding: " + path.name)
    offset, payload = 8, bytearray()
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        if data[offset + 4:offset + 8] == b"IDAT":
            payload.extend(data[offset + 8:offset + 8 + length])
        offset += length + 12
    pixels = zlib.decompress(payload)
    stride = width * (3 if color_type == 2 else 4) + 1
    check(len(pixels) == stride * height and all(pixels[y * stride] <= 4 for y in range(height)),
          "Incomplete/invalid native PNG scanlines: " + path.name)
    return {"file": path.name, "width": width, "height": height,
            "bytes": len(data), "sha256": sha256(data)}


SHOP_ASSET = "release-v1/ui/button-secondary.png"
SHOP_ASSET_SHA = "267b4d660290f8ff5b169e8dc5582c38b49ff905"  # Git blob SHA, verified from bundled bytes.
SHOP_STATES = ("first-page", "blocked-selected", "second-page")
SHOP_FIRST_CARDS = ("shop-card-shop:tickets", "shop-card-shop:peanutTickets", "shop-card-items/buy:potion")
SHOP_SECOND_CARDS = ("shop-card-items/buy:big", "shop-card-items/buy:clear", "shop-card-items/buy:feather")


def shop_profiles():
    profiles = {}
    for width, height in ((915, 374), (780, 319)):
        for font in (100, 130):
            for inset in (False, True):
                name = f"{width}x{height}-font{font}" + ("-insets" if inset else "")
                profiles[name] = (width, height, 160, font / 100, [32, 12, 12, 12] if inset else [0, 0, 0, 0])
    profiles["tablet1280x720-font100"] = (1280, 720, 160, 1.0, [0, 0, 0, 0])
    for dpi in (360, 420):
        for font in (100, 130):
            for inset in (False, True):
                name = f"2048x838-dpi{dpi}-font{font}" + ("-insets" if inset else "")
                profiles[name] = (2048, 838, dpi, font / 100, [32, 12, 12, 12] if inset else [0, 0, 0, 0])
    return profiles


def java_round(value):
    return math.floor(value + .5)


def verify_shop_metric(metric, profile, state):
    width, height, dpi, font, insets = profile
    density = dpi / 160
    check(metric.get("passed") is True and metric.get("actualNativeViews") is True, "Missing passing native shop proof")
    check(metric.get("sourceCommit") == "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89", "Shop source baseline changed")
    check(type(metric.get("apiWrites")) is int and metric["apiWrites"] == 0, "Shop capture performed API writes")
    for key, expected in (("widthPx", width), ("heightPx", height), ("densityDpi", dpi), ("state", state)):
        check(type(metric.get(key)) is type(expected) and metric[key] == expected, "Shop profile mismatch: " + key)
    check(finite(metric.get("fontScale")) and abs(metric["fontScale"] - font) < .001, "Wrong shop font scale")
    check(metric.get("insetsDp") == insets and isinstance(metric["insetsDp"], list)
          and all(integer(value) for value in metric["insetsDp"]), "Wrong shop safe insets")
    for key, pixels in (("widthDp", width), ("heightDp", height)):
        check(finite(metric.get(key)) and abs(metric[key] - pixels / density) <= 1, "Unmeasured shop dp size")
    safe = [java_round(insets[0] * density), java_round(insets[1] * density),
            width - java_round(insets[2] * density), height - java_round(insets[3] * density)]
    expected_tags = set(SHOP_SECOND_CARDS if state == "second-page" else SHOP_FIRST_CARDS)
    cards = metric.get("cards")
    check(isinstance(cards, list) and len(cards) == 3 and type(metric.get("cardCount")) is int and metric["cardCount"] == 3,
          "Missing three full shop cards")
    check({card.get("tag") for card in cards} == expected_tags, "Shop card identity mismatch")
    check(metric.get("alignedCardRects") is True, "Shop cards are misaligned")
    card_rects = [rect(card["rect"]) for card in cards]
    check(max(r[1] for r in card_rects) - min(r[1] for r in card_rects) <= .01
          and max(r[3] for r in card_rects) - min(r[3] for r in card_rects) <= .01,
          "Actual card top/bottom alignment failed")
    check(not any(overlap(a, b) for i, a in enumerate(card_rects) for b in card_rects[i + 1:]),
          "Actual shop cards overlap")
    text_count = 0
    for card in cards:
        outer, reading, icon = rect(card["rect"]), rect(card["readingRect"]), rect(card["iconRect"])
        check(inside(safe, outer) and same_rect(outer, card["visibleRect"]), "Shop card clipped by viewport/ancestor")
        check(card.get("asset") == SHOP_ASSET and card.get("assetSha") == SHOP_ASSET_SHA,
              "Shop ornamental frame asset changed")
        dx, dy = java_round(density * (80 / 3 + 1)), java_round(density * (72 / 3 + 1))
        actual_reading = [outer[0] + dx, outer[1] + dy, outer[2] - dx, outer[3] - dy]
        check(same_rect(reading, actual_reading), "Shop reading bounds are not the ornamental interior")
        check(inside(reading, icon), "Shop icon crosses the ornamental reading bounds")
        image = rect(card["iconImageRect"])
        minimum_icon = java_round(24 * density)
        check(integer(card.get("minIconHeightPx"), minimum_icon) and card["minIconHeightPx"] == minimum_icon
              and icon[3] - icon[1] >= minimum_icon and image[3] - image[1] >= minimum_icon,
              "Shop illustration reduced below a useful 24dp height")
        check(inside(icon, image), "Actual shop illustration is clipped by its ImageView")
        texts = card.get("texts")
        wanted = {"shop-card-name", "shop-card-price", "shop-card-state"}
        check(isinstance(texts, list) and len(texts) == len(wanted)
              and {text.get("tag") for text in texts} == wanted, "Missing exact card text measurements")
        measured_ink = []
        for text in texts:
            check(isinstance(text.get("text"), str) and text["text"]
                  and text["text"] == text.get("expectedText"), "Shop text was shortened or replaced")
            check(text.get("fullText") is True and type(text.get("ellipsisCount")) is int
                  and text["ellipsisCount"] == 0, "Shop text is truncated")
            check(finite(text.get("fontSizePx")) and finite(text.get("expectedFontSizePx"))
                  and text["fontSizePx"] > 0 and abs(text["fontSizePx"] - text["expectedFontSizePx"]) <= .01,
                  "Shop text font shrank or was not measured")
            check(same_rect(text["viewRect"], text["visibleRect"]), "Shop text view is partially invisible")
            check(inside(outer, text["viewRect"]), "Shop text view escapes its card")
            ink, lines = text.get("inkRects"), text.get("layoutRects")
            check(isinstance(ink, list) and ink and isinstance(lines, list) and lines
                  and integer(text.get("lineCount"), 1) and len(lines) == text["lineCount"],
                  "Missing native glyph or Layout line measurements")
            for bounds in ink + lines:
                check(inside(reading, bounds), "Native shop glyph/Layout crosses ornamental frame")
                check(inside(text["visibleRect"], bounds), "Native shop glyph/Layout clipped inside TextView")
                check(not overlap(icon, bounds), "Shop text overlaps icon")
            measured_ink.extend(ink)
            text_count += 1
        check(not any(overlap(a, b) for i, a in enumerate(measured_ink) for b in measured_ink[i + 1:]),
              "Shop text glyph lines overlap each other")
    controls = metric.get("controls")
    check(isinstance(controls, list) and len(controls) >= 13, "Missing full shop controls")
    check(len({c.get("tag") for c in controls}) == len(controls), "Duplicate shop control identity")
    tags = {c.get("tag") for c in controls}
    required = expected_tags | {"JournalButton:‹", "JournalButton:›", "JournalButton:−",
                                "JournalButton:＋", "JournalButton:최대", "EditText:1"}
    buy = "JournalButton:땅콩 부족" if state == "blocked-selected" else ("JournalButton:지원서 10장 구매 · 새우gang 1000개" if state == "first-page" else "JournalButton:1개 구매 · 새우gang 150개")
    check(required | {buy} <= tags, "Missing required shop navigation/quantity/purchase controls")
    indexed_controls = {c["tag"]: c for c in controls}
    check(indexed_controls[buy].get("enabled") is (state != "blocked-selected"), "Wrong shop purchase availability")
    check(indexed_controls["JournalButton:‹"].get("enabled") is (state == "second-page")
          and indexed_controls["JournalButton:›"].get("enabled") is (state != "second-page"),
          "Wrong shop pager availability")
    for control in controls:
        bounds = rect(control["rect"])
        minimum = java_round(48 * density)
        check(inside(safe, bounds) and same_rect(bounds, control["visibleRect"]), "Shop control clipped")
        check(integer(control.get("minTouchTargetPx"), minimum) and control["minTouchTargetPx"] == minimum, "Missing shop touch target measurement")
        check(bounds[2] - bounds[0] >= minimum and bounds[3] - bounds[1] >= minimum,
              "Shop control smaller than 48dp")
        check(type(control.get("enabled")) is bool, "Missing shop enabled state")
    return {"cards": len(cards), "texts": text_count, "controls": len(controls)}


def verify_shop(root):
    directory = Path(root) / "feedback-stage1-shop"
    geometry, findings = read(directory / "geometry.json"), read(directory / "findings.json")
    profiles = shop_profiles()
    expected = {state + "-" + name for name in profiles for state in SHOP_STATES}
    check(isinstance(geometry, dict) and set(geometry) == expected, "Missing/unexpected shop geometry cases")
    check(isinstance(findings, dict) and set(findings) == expected, "Missing/unexpected shop findings cases")
    check({path.stem for path in directory.glob("*.png")} == expected, "Missing/unexpected shop screenshots")
    captures, counts = [], dict(cards=0, texts=0, controls=0)
    for name, profile in profiles.items():
        for state in SHOP_STATES:
            key = state + "-" + name
            check(findings[key] == [], "Native shop findings: " + key)
            found = verify_shop_metric(geometry[key], profile, state)
            for field in counts:
                counts[field] += found[field]
            capture = verify_png(directory / (key + ".png"), profile[0], profile[1])
            capture.update(profile=name, state=state)
            captures.append(capture)
    return {"screenshots": len(captures), **counts, "captures": captures}


MODE_STATES = ("ready", "loading", "error", "empty")
MODE_ACTIONS = ("modes-story-team", "modes-story", "modes-adventure", "modes-normal",
                "modes-ranked", "modes-standings", "modes-practice", "modes-pvp-team")
MODE_CONTAINERS = ("modes-story-summary", "modes-pvp-summary")
MODE_PROFILES = {
    "short-915x374-mdpi": (915, 374, 160, 1.0, [0, 0, 0, 0]),
    "short-780x319-mdpi": (780, 319, 160, 1.0, [0, 0, 0, 0]),
    "short-915x374-font130": (915, 374, 160, 1.3, [0, 0, 0, 0]),
    "short-780x319-font130": (780, 319, 160, 1.3, [0, 0, 0, 0]),
    "device-ratio-2048x838-360dpi": (2048, 838, 360, 1.0, [73, 26, 26, 26]),
    "device-ratio-2048x838-420dpi": (2048, 838, 420, 1.0, [73, 26, 26, 26]),
    "insets-915x374-xhdpi": (1950, 844, 320, 1.0, [72, 32, 48, 64]),
    "insets-780x319-hdpi": (1260, 551, 240, 1.0, [54, 24, 36, 48]),
}
EDGES = ("left", "top", "right", "bottom")


def edge_values(value):
    check(isinstance(value, dict) and set(value) == set(EDGES), "Missing edge measurements")
    result = [value[key] for key in EDGES]
    check(all(integer(x) for x in result), "Invalid edge measurements")
    return result


def density_interpretation(profile_id):
    return "hypothesis; source screenshot DPI unknown" if profile_id.startswith("device-ratio-") else "test profile"


def verify_mode_profile(value, profile_id, profile, manifest=False):
    width, height, dpi, font, insets = profile
    keys = ("widthPx", "heightPx", "name", "insetsPx") if manifest else ("screenWidthPx", "screenHeightPx", "profile", "safeInsetsPx")
    for key, expected in ((keys[0], width), (keys[1], height), ("densityDpi", dpi), (keys[2], profile_id)):
        check(type(value.get(key)) is type(expected) and value[key] == expected, "Modes profile mismatch: " + key)
    check(finite(value.get("fontScale")) and abs(value["fontScale"] - font) < .001, "Modes font scale mismatch")
    check(edge_values(value.get(keys[3])) == insets, "Modes insets mismatch")
    check(value.get("densityInterpretation") == density_interpretation(profile_id), "Reference device density was assumed")
    safe = [insets[0], insets[1], width - insets[2], height - insets[3]]
    if manifest:
        check(finite(value.get("safeWidthDp")) and abs(value["safeWidthDp"] - (safe[2] - safe[0]) * 160 / dpi) < .001,
              "Modes safe dp width mismatch")
        check(finite(value.get("safeHeightDp")) and abs(value["safeHeightDp"] - (safe[3] - safe[1]) * 160 / dpi) < .001,
              "Modes safe dp height mismatch")
    else:
        check(same_rect(value["safeBoundsPx"], safe), "Modes safe rectangle mismatch")
    return safe


def verify_modes_metric(metric, profile_id, state):
    safe = verify_mode_profile(metric, profile_id, MODE_PROFILES[profile_id])
    check(metric.get("state") == state and metric.get("actualNativeViews") is True, "Missing actual native modes state")
    check(type(metric.get("apiWrites")) is int and metric["apiWrites"] == 0, "Modes capture wrote to API")
    check(type(metric.get("scrollContainers")) is int and metric["scrollContainers"] == 0, "Modes requires scrolling")
    check(metric.get("realBundledImages") is True and integer(metric.get("loadedPortraits"))
          and (state == "empty" or metric["loadedPortraits"] >= 6), "Missing real modes portraits")
    check(type(metric.get("pendingFetches")) is int and metric["pendingFetches"] == (1 if state == "loading" else 0),
          "Modes asynchronous rank state mismatch")
    check(metric.get("findings") == [], "Native modes measurements contain findings")
    containers, actions, texts = metric.get("containers"), metric.get("actions"), metric.get("texts")
    check(isinstance(containers, dict) and set(containers) == set(MODE_CONTAINERS), "Missing modes team summaries")
    for box in containers.values():
        check(inside(safe, box["boundsPx"]) and same_rect(box["boundsPx"], box["visibleBoundsPx"]),
              "Modes summary clipped by viewport/ancestor")
    check(isinstance(actions, dict) and set(MODE_ACTIONS) <= actions.keys(), "Missing required modes actions")
    check(isinstance(texts, dict) and set(MODE_ACTIONS) | {"modes-rank-status"} <= texts.keys(), "Missing modes text measurements")
    minimum = math.ceil(48 * MODE_PROFILES[profile_id][2] / 160)
    for key, action in actions.items():
        bounds = rect(action["boundsPx"])
        check(inside(safe, bounds) and same_rect(bounds, action["visibleBoundsPx"]), "Modes control clipped: " + key)
        check(integer(action.get("minTouchTargetPx"), minimum) and action["minTouchTargetPx"] == minimum
              and bounds[2] - bounds[0] >= minimum and bounds[3] - bounds[1] >= minimum,
              "Modes control smaller than 48dp: " + key)
        check(type(action.get("enabled")) is bool, "Missing modes enabled state")
    check(actions["modes-adventure"]["enabled"] is False, "Unavailable adventure action became enabled")
    # The eight required destinations are siblings, never nested click targets.
    for i, key in enumerate(MODE_ACTIONS):
        for other in MODE_ACTIONS[i + 1:]:
            check(not overlap(actions[key]["boundsPx"], actions[other]["boundsPx"]), "Modes actions overlap")
    check(integer(metric.get("actionOverlapChecks"), 28), "Modes independent action checks did not execute")
    framed = 0
    for key, text in texts.items():
        box, visible, ink = rect(text["boundsPx"]), rect(text["visibleBoundsPx"]), rect(text["glyphBoundsPx"])
        check(inside(safe, box) and same_rect(box, visible) and inside(visible, ink), "Modes text/glyph clipped: " + key)
        actual = text.get("text")
        check(isinstance(actual, str) and actual, "Missing actual modes text")
        check(integer(text.get("textLength"), 1) and text["textLength"] == utf16_length(actual)
              and integer(text.get("laidOutTextEnd"), 1) and text["laidOutTextEnd"] == text["textLength"],
              "Modes text not fully laid out: " + key)
        check(integer(text.get("layoutHeightPx"), 1) and integer(text.get("contentHeightPx"), 1)
              and text["layoutHeightPx"] <= text["contentHeightPx"], "Modes Layout height clipped: " + key)
        check(integer(text.get("ellipsisCount")), "Missing modes ellipsis measurement")
        allowed = text.get("ellipsisAllowed")
        check(type(allowed) is bool, "Missing explicit modes ellipsis policy")
        if allowed:
            readonly_rank = key == "modes-rank-status" and state in ("ready", "empty")
            readonly_summary = key.startswith("TextView-") and any(inside(c["boundsPx"], box) for c in containers.values())
            check((readonly_rank or readonly_summary) and "아직 편성한" not in actual,
                  "Required modes action/status may not ellipsize")
            if text["ellipsisCount"] > 0:
                check(isinstance(text.get("fullContentDescription"), str) and actual in text["fullContentDescription"],
                      "Ellipsized summary lost its full accessible copy")
        else:
            check(text["ellipsisCount"] == 0, "Required modes label is ellipsized: " + key)
        check(type(text.get("autoSizeTextType")) is int and text["autoSizeTextType"] == 0
              and finite(text.get("textScaleX")) and abs(text["textScaleX"] - 1) < .0001, "Modes font shrank/squeezed")
        check(finite(text.get("textSizePx")) and text["textSizePx"] > 0
              and finite(text.get("expectedTextSizePx"))
              and abs(text["textSizePx"] - text["expectedTextSizePx"]) < .1, "Modes font size mismatch")
        parent, reading = rect(text["parentFrameBoundsPx"]), rect(text["readingBoundsPx"])
        insets = edge_values(text["readingInsetsPx"])
        check(same_rect(reading, [parent[0] + insets[0], parent[1] + insets[1], parent[2] - insets[2], parent[3] - insets[3]])
              and inside(parent, reading) and inside(reading, ink), "Modes glyph crosses measured ornamental reading edge")
        check(type(text.get("decorativeFrameChecked")) is bool, "Missing modes drawable frame measurement")
        if text["decorativeFrameChecked"]:
            check(text.get("frameKind") in ("SurfaceDrawable", "GameButtonDrawable") and min(insets) >= 1,
                  "Modes ornamental edge was not independently measured")
            framed += 1
        else:
            check(text.get("frameKind") == "none" and same_rect(parent, box) and same_rect(reading, box),
                  "Modes unframed text bounds mismatch")
    check(integer(metric.get("decorativeFrameChecks"), 1) and metric["decorativeFrameChecks"] == framed,
          "Modes decorative frame check count mismatch")
    rank = texts["modes-rank-status"]["text"]
    if state == "empty":
        check("modes-rank-required-team" in texts and texts["modes-rank-required-team"]["text"] == "편성 3마리 필요",
              "Missing full required-team notice")
        check(any(t["text"] == "아직 편성한 갈매미가 없어요." for t in texts.values()), "Missing full empty-party notice")
    check("불러오는" in rank if state == "loading" else ("불러오지 못" in rank or "순위 조회 실패" in rank) if state == "error" else "승리 +" in rank,
          "Modes rank fixture state was not rendered")
    return {"actions": len(actions), "texts": len(texts), "decorative_frame_checks": framed}


def verify_modes(root):
    directory = Path(root) / "feedback-stage1-modes"
    metrics, findings, manifest = read(directory / "metrics.json"), read(directory / "findings.json"), read(directory / "manifest.json")
    expected = {profile + "-" + state + ".png" for profile in MODE_PROFILES for state in MODE_STATES}
    check(isinstance(metrics, dict) and set(metrics) == expected, "Missing/unexpected modes measurements")
    check(isinstance(findings, dict) and set(findings) == expected, "Missing/unexpected modes findings")
    check({path.name for path in directory.glob("*.png")} == expected, "Missing/unexpected modes screenshots")
    check(manifest.get("schemaVersion") == 1 and manifest.get("suite") == "feedback-stage1-modes"
          and manifest.get("baseline") == "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89", "Modes manifest identity mismatch")
    check(manifest.get("states") == list(MODE_STATES) and manifest.get("requiredActions") == list(MODE_ACTIONS)
          and manifest.get("requiredContainers") == list(MODE_CONTAINERS), "Modes manifest reduced required state/control coverage")
    profiles = manifest.get("profiles")
    check(isinstance(profiles, list) and len(profiles) == len(MODE_PROFILES)
          and {p.get("name") for p in profiles} == set(MODE_PROFILES), "Modes manifest profile set mismatch")
    for profile in profiles:
        verify_mode_profile(profile, profile["name"], MODE_PROFILES[profile["name"]], manifest=True)
    capture_manifest = manifest.get("captures")
    check(isinstance(capture_manifest, list) and len(capture_manifest) == len(expected)
          and {c.get("filename") for c in capture_manifest} == expected, "Modes manifest capture set mismatch")
    for capture in capture_manifest:
        check(capture.get("profile") in MODE_PROFILES and capture.get("state") in MODE_STATES
              and capture["filename"] == capture["profile"] + "-" + capture["state"] + ".png", "Modes capture identity mismatch")
    reference = manifest.get("reference", {})
    check(reference.get("widthPx") == 2048 and reference.get("heightPx") == 838
          and reference.get("densityDpi") == "unknown", "Reference screenshot DPI must remain unknown")
    captures, counts = [], dict(actions=0, texts=0, decorative_frame_checks=0)
    for profile_id, profile in MODE_PROFILES.items():
        for state in MODE_STATES:
            key = profile_id + "-" + state + ".png"
            check(findings[key] == [], "Native modes findings: " + key)
            found = verify_modes_metric(metrics[key], profile_id, state)
            for field in counts:
                counts[field] += found[field]
            capture = verify_png(directory / key, profile[0], profile[1])
            capture.update(profile=profile_id, state=state)
            captures.append(capture)
    return {"screenshots": len(captures), **counts, "captures": captures}


def verify(native_review, test_results, full_suite_report):
    junit = verify_junit(test_results, full_suite_report)
    modes, shop = verify_modes(native_review), verify_shop(native_review)
    return {"status": "passed", "junit": junit, "modes": modes, "shop": shop,
            "screenshots": modes["screenshots"] + shop["screenshots"],
            "manual_visual_review": "pending", "actual_device_review": "not_run",
            "scope": "Stage 1 only: mode controls and ornamental shop text/card bounds. Existing gates remain required.",
            "density_note": "2048x838 does not identify the reference device density; 360/420dpi are synthetic hypotheses."}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-review", type=Path, required=True)
    parser.add_argument("--test-results", type=Path, required=True)
    parser.add_argument("--full-suite-report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = verify(args.native_review, args.test_results, read(args.full_suite_report))
    except (OSError, ValueError, TypeError, KeyError, IndexError, AttributeError,
            ET.ParseError, struct.error, zlib.error) as error:
        emit({"status": "failed", "errors": [str(error)], "manual_visual_review": "pending",
              "actual_device_review": "not_run"}, args.output)
        print("::error::" + str(error), file=sys.stderr)
        return 1
    emit(report, args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
