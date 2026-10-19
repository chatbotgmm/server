#!/usr/bin/env python3
"""Validate existing full-suite native home-tip evidence; never generate captures."""
import argparse
import json
import math
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zlib

from verify_registration_ci import check, emit, png_dimensions, sha256


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def rectangle(value):
    return (isinstance(value, list) and len(value) == 4 and all(number(x) for x in value)
            and value[0] < value[2] and value[1] < value[3])


def inside(outer, inner):
    return outer[0] <= inner[0] <= inner[2] <= outer[2] and outer[1] <= inner[1] <= inner[3] <= outer[3]


def overlap(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def evidence(value, contract):
    check(isinstance(value, dict) and set(contract["requiredMetrics"]) <= value.keys(), "Missing native measurements")
    check(type(value["clippedInkPixels"]) is int and value["clippedInkPixels"] == 0, "Clipped native ink")
    check(rectangle(value["safe"]), "Invalid safe rectangle")
    exclusions = value["exclusions"]
    check(isinstance(exclusions, list) and len(exclusions) > 0 and all(rectangle(x) for x in exclusions),
          "Missing real hero/action exclusions")
    for key in ("bubble", "nativePaper", "nativeInk"):
        check(rectangle(value[key]) and inside(value["safe"], value[key]), "Invalid/out-of-safe " + key)
        check(not any(overlap(value[key], excluded) for excluded in exclusions), "Excluded-area overlap: " + key)
    check(inside(value["bubble"], value["nativeInk"]), "Native ink escapes the bubble")
    minimum = value["minimumTouchPixels"]
    check(type(minimum) is int and minimum >= 48, "Missing minimum touch measurement")
    check(value["bubble"][2] - value["bubble"][0] >= minimum and
          value["bubble"][3] - value["bubble"][1] >= minimum, "Bubble smaller than measured minimum touch target")
    check(number(value["fontPixels"]) and value["fontPixels"] >= 12, "Unreadable/unmeasured font")
    check(type(value["lines"]) is int and value["lines"] >= 1, "Missing native line count")
    check(number(value["verticalPaddingPixels"]) and value["verticalPaddingPixels"] >= 4, "Insufficient measured vertical padding")


def verify(contract, captures, results):
    captures, results = Path(captures), Path(results)
    fixtures = contract["fixtures"]
    check(len(fixtures) == contract["screenshotCount"], "Screenshot contract count mismatch")
    expected_pngs = {f["png"] for f in fixtures}
    expected_metrics = {f["metrics"] for f in fixtures}
    check(len(expected_pngs) == len(fixtures) and len(expected_metrics) == len(fixtures), "Duplicate fixture names")
    check(contract["tipChecks"] == len(fixtures) * 8, "Incomplete all-tip contract")
    check({x.name for x in captures.glob("home-advice*.png")} == expected_pngs, "Missing or unexpected home-advice PNGs")
    check({x.name for x in captures.glob("home-advice*.json")} == expected_metrics | {contract["summary"]},
          "Missing or unexpected home-advice JSON evidence")
    junit_path = results / contract["junitFile"]
    root = ET.parse(junit_path).getroot()
    check(root.tag == "testsuite" and root.get("name") == contract["testClass"], "Wrong JUnit suite identity")
    cases = root.findall("testcase")
    check(int(root.get("tests", "0")) == contract["testCount"] == len(cases), "JUnit test count mismatch")
    check(all(int(root.get(key, "0")) == 0 for key in ("failures", "errors", "skipped")), "JUnit failed or skipped")
    check(not any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")),
          "Failed/skipped JUnit testcase")
    check({case.get("name") for case in cases} == set(contract["testMethods"]), "Required home-tip tests missing")
    summary = read_json(captures / contract["summary"])
    check(type(summary.get("tipChecks")) is int and summary["tipChecks"] == contract["tipChecks"], "All-tip count mismatch")
    check(type(summary.get("screenshotCount")) is int and summary["screenshotCount"] == contract["screenshotCount"], "Summary screenshot count mismatch")
    check(len(summary["fixtures"]) == len(fixtures), "Summary fixture count mismatch")
    checked, images = 0, []
    for expected, actual in zip(fixtures, summary["fixtures"]):
        for key in ("width", "height", "qualifiers", "fontScale", "resume"):
            check((number(actual[key]) if key == "fontScale" else type(actual[key]) is type(expected[key]))
                  and actual[key] == expected[key], "Fixture metadata mismatch: " + key)
        check(type(actual["pagedTips"]) is int and actual["pagedTips"] == 0, "Normal advice must not paginate")
        check(type(actual["scrollableTips"]) is int and actual["scrollableTips"] == 0, "Normal advice must not scroll")
        check(len(actual["tips"]) == 8, "Missing tip metrics")
        for tip in actual["tips"]:
            evidence(tip, contract)
            check(inside([0, 0, expected["width"], expected["height"]], tip["safe"]), "Safe bounds outside screenshot")
            checked += 1
        data = (captures / expected["png"]).read_bytes()
        check(png_dimensions(data) == (expected["width"], expected["height"]), "Wrong PNG dimensions")
        check(len(data) > 10000, "Unexpectedly blank/tiny native scene")
        metric = read_json(captures / expected["metrics"])
        evidence(metric, contract)
        check(metric == actual["tips"][expected["tipIndex"]], "Captured tip differs from its all-tip measurement")
        images.append({"file": expected["png"], "sha256": sha256(data), "bytes": len(data), "width": expected["width"], "height": expected["height"]})
    check(checked == contract["tipChecks"], "Incomplete measured tip coverage")
    return {"status": "passed", "testClass": contract["testClass"], "junitTests": len(cases),
            "screenshots": len(images), "tipChecks": checked, "captures": images,
            "normalFixturePagination": 0, "scrolling": 0, "clippedNativeInkPixels": 0, "exclusionOverlaps": 0,
            "manual_visual_review": "pending", "actual_device_review": "not_run",
            "scope": "Existing full-suite evidence only. Synthetic xhdpi does not identify the reference device DPI."}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--captures", required=True, type=Path)
    parser.add_argument("--test-results", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = verify(read_json(args.contract), args.captures, args.test_results)
    except (OSError, ValueError, TypeError, KeyError, IndexError, ET.ParseError, struct.error, zlib.error) as error:
        emit({"status": "failed", "errors": [str(error)], "manual_visual_review": "pending"}, args.output)
        print("::error::" + str(error), file=sys.stderr)
        return 1
    emit(report, args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
