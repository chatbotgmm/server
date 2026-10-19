#!/usr/bin/env python3
"""Validate one-time full-suite phase-two captures without re-running Android tests."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import struct
import sys
import time
import xml.etree.ElementTree as ET
import zlib

from verify_registration_ci import check, emit, png_dimensions, sha256

HERE = Path(__file__).resolve().parent


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def integer_at_least(value, minimum):
    return type(value) is int and value >= minimum


def check_suites(directory, contract):
    required = contract["test_suites"]
    results, errors = {}, []
    for expected in required:
        name = expected["name"]
        try:
            root = ET.parse(Path(directory) / ("TEST-" + name + ".xml")).getroot()
            check(root.tag == "testsuite" and root.get("name") == name, "Incorrect suite identity")
            counts = {key: int(root.get(key, "0")) for key in ("tests", "skipped", "failures", "errors")}
            cases = root.findall("testcase")
            check(counts["tests"] == len(cases), "JUnit case count mismatch")
            check(counts["tests"] >= expected["minimum_tests"], "Required test coverage decreased")
            check(counts["skipped"] == 0 and not root.findall("testcase/skipped"), "Required suite was skipped")
            check(not counts["failures"] and not counts["errors"] and
                  not root.findall("testcase/failure") and not root.findall("testcase/error"), "JUnit failures/errors")
            results[name] = {**counts, "seconds": float(root.get("time", "0")), "status": "passed"}
        except (OSError, ValueError, ET.ParseError) as error:
            errors.append("JUnit " + name + ": " + str(error))
            results[name] = {"status": "failed", "error": str(error)}
    return results, errors


def measurement_check(measured, screen, contract, asset_lock, variant=None):
    rules = contract["measurements"]
    check(isinstance(measured, dict), "Missing actual measurement object")
    if "exact_fields" in rules:
        check(set(measured) == set(rules["exact_fields"]), "Unexpected or missing measurement fields")
    for field in rules.get("true_fields", []):
        check(measured.get(field) is True, "Missing true field: " + field)
    for field in rules.get("zero_integer_fields", []):
        check(type(measured.get(field)) is int and measured[field] == 0, "Unexpected write/missing counter: " + field)
    for field in rules.get("positive_integer_fields", []):
        check(integer_at_least(measured.get(field), 1), "Missing positive measurement: " + field)
    for field in rules.get("nonnegative_integer_fields", []):
        check(integer_at_least(measured.get(field), 0), "Missing nonnegative measurement: " + field)
    for field in rules.get("array_fields", []):
        check(isinstance(measured.get(field), list), "Missing array measurement: " + field)
    for field, expected in screen.get("expected_fields", {}).items():
        check(type(measured.get(field)) is type(expected) and measured[field] == expected,
              "Unexpected fixture field: " + field)
    for field, minimum in screen.get("minimum_integers", {}).items():
        check(integer_at_least(measured.get(field), minimum), "Insufficient measured checks: " + field)
    geometry = rules.get("window_geometry")
    if geometry:
        check(isinstance(variant, dict), "Missing viewport contract for window geometry")
        insets = variant["safe_insets"]
        expected_safe = (variant["width"] - insets[0] - insets[2],
                         variant["height"] - insets[1] - insets[3])
        check((measured["safeWidth"], measured["safeHeight"]) == expected_safe,
              "Measured safe viewport does not match size/insets")
        if measured["dialog"]:
            check(measured["dialogBoundsChecks"] == geometry["dialog_bounds_checks"],
                  "Missing actual dialog-bound assertions")
            check(measured["measuredWidth"] <= measured["safeWidth"], "Dialog wider than safe viewport")
            check(measured["measuredHeight"] <= measured["safeHeight"] - geometry["dialog_vertical_margin_pixels"],
                  "Dialog violates required safe-height margin")
        else:
            check(measured["dialogBoundsChecks"] == 0, "Page incorrectly reports dialog checks")
            check((measured["measuredWidth"], measured["measuredHeight"]) == (variant["width"], variant["height"]),
                  "Measured page root does not match viewport")
        check(measured["boundsChecks"] >= measured["dialogBoundsChecks"], "Dialog checks not included in bounds total")
    asset_field = rules.get("scene_asset_field")
    if asset_field:
        assets = {entry["path"]: entry for entry in asset_lock["files"]}
        asset = measured[asset_field]
        check(asset in assets, "Scene asset missing from approved lock")
        check([measured[rules["scene_width_field"]], measured[rules["scene_height_field"]]] == assets[asset]["dimensions"],
              "Scene was not decoded at the approved dimensions")


def verify_module(contract, directory, junit_directory, asset_lock):
    started = time.monotonic()
    directory = Path(directory)
    suites, errors = check_suites(junit_directory, contract)
    expected = {screen["stem"] + "-" + variant["suffix"]: (screen, variant)
                for screen in contract["screens"] for variant in contract["variants"]}
    check(len(expected) == contract["expected_capture_count"], "Duplicate/incorrect capture contract")
    actual_files = {path.name for path in directory.glob("*.png")}
    expected_files = {name + ".png" for name in expected}
    missing = sorted(expected_files - actual_files)
    extra = sorted(actual_files - expected_files)
    if missing:
        errors.append("Missing PNGs: " + ", ".join(missing))
    if extra:
        errors.append("Unexpected PNGs: " + ", ".join(extra))
    try:
        findings = read_json(directory / contract.get("findings_file", "findings.json"))
        metrics = read_json(directory / contract.get("metrics_file", "metrics.json"))
        check(isinstance(findings, dict) and isinstance(metrics, dict), "Expected keyed findings/metrics objects")
        if set(findings) != set(expected):
            errors.append("Findings keys differ from exact capture contract")
        if set(metrics) != set(expected):
            errors.append("Metrics keys differ from exact capture contract")
    except (OSError, ValueError) as error:
        errors.append("Capture metadata: " + str(error))
        findings, metrics = {}, {}
    captures, duplicates, durations, summaries = [], defaultdict(list), defaultdict(int), []
    counters = defaultdict(int)
    for name, (screen, variant) in expected.items():
        try:
            data = (directory / (name + ".png")).read_bytes()
            check(png_dimensions(data) == (variant["width"], variant["height"]), "Wrong PNG dimensions")
            check(name in findings and findings[name] == [], "Missing/nonempty findings")
            measured = metrics.get(name)
            measurement_check(measured, screen, contract, asset_lock, variant)
            digest = sha256(data)
            duplicates[digest].append(name + ".png")
            for field in contract["measurements"].get("duration_fields", []):
                durations[field] += measured[field]
            for field in contract["measurements"].get("report_counter_fields", []):
                counters[field] += measured[field]
            summary_field = contract["measurements"].get("read_only_summary_field")
            if summary_field and measured[summary_field]:
                summaries.append({"capture": name + ".png", "items": measured[summary_field]})
            captures.append({"file": name + ".png", "sha256": digest, "bytes": len(data),
                             "width": variant["width"], "height": variant["height"],
                             "audit_id": screen["audit_id"], "measurements": measured})
        except (OSError, ValueError, TypeError, KeyError, struct.error, zlib.error) as error:
            errors.append(name + ": " + str(error))
    return {"status": "failed" if errors else "passed", "module": contract["id"],
            "contract_status": contract["status"], "expected_capture_count": len(expected),
            "capture_count": len(captures), "junit": suites, "errors": errors,
            "missing_pngs": missing, "unexpected_pngs": extra, "captures": captures,
            "matching_png_groups_for_direct_review": [names for names in duplicates.values() if len(names) > 1],
            "shortened_read_only_summaries_for_direct_review": summaries,
            "fixture_duration_totals_ms": dict(durations),
            "measured_counter_totals_for_direct_review": dict(counters),
            "junit_suite_seconds": sum(suite.get("seconds", 0) for suite in suites.values()),
            "validator_seconds": round(time.monotonic() - started, 3),
            "render_execution": "Read existing full-suite artifacts only; no Android rerun.",
            "all_required_states_complete": False, "manual_visual_review": "pending", "actual_device_review": "not_run"}


def module_command(args):
    contract = read_json(args.contract)
    report = verify_module(contract, Path(args.native_review) / contract["output_subdirectory"],
                           args.junit, read_json(args.asset_lock))
    emit(report, args.output)
    return 0 if report["status"] == "passed" else 1


def readiness_command(args):
    registry = read_json(args.registry)
    blockers = ["Pending review contract: " + name + ": " + reason
                for name, reason in registry["pending_modules"].items()]
    required = {"menus", "realtime", "economy", "extras"}
    listed = [module["id"] for module in registry["modules"]]
    if set(listed) != required or len(listed) != len(required):
        blockers.append("All four distinct requested source groups are required")
    if set(registry.get("required_modules", [])) != required:
        blockers.append("Required source groups were changed or omitted")
    for module in registry["modules"]:
        path = Path(args.registry).parent / module["contract"]
        if not path.exists() and path.parent.name == "phase2-contracts":
            path = path.parent.parent / "contracts" / path.name
        try:
            contract = read_json(path)
            check(contract["id"] == module["id"], "Contract module mismatch")
            check(contract["status"] == "source_complete_native_execution_pending", "Source contract is not complete")
        except (OSError, ValueError, KeyError) as error:
            blockers.append(module["id"] + ": " + str(error))
    if not registry.get("all_sections_ready"):
        blockers.append("All requested phase-two sections have not been declared ready for integration")
    emit({"status": "failed" if blockers else "passed", "blockers": blockers,
          "modules": registry["modules"], "all_app_states_complete": False,
          "scope": "Source-contract readiness only. Separate JUnit and actual capture gates must pass."}, args.output)
    return 1 if blockers else 0


def verify_additional_assets(repo, phase_lock, theme_lock):
    original = {entry["path"]: entry for entry in phase_lock["preserved_phase_one_files"]}
    additions = {entry["path"]: entry for entry in phase_lock["files"]}
    expanded = {entry["path"]: entry for entry in theme_lock["files"]}
    check(not set(original) & set(additions), "Additional asset replaces an existing path")
    check(expanded == {**original, **additions}, "Expanded theme lock altered original assets or omitted additions")
    check(theme_lock["background_count"] == 8 and theme_lock["ui_count"] == 6 and
          theme_lock["protected_home_art_count"] == 11, "Expanded theme role counts changed")
    check(theme_lock["runtime_bytes"] == sum(entry["bytes"] for entry in expanded.values()), "Expanded theme byte count mismatch")
    check(theme_lock["source_prefix"] == phase_lock["source_prefix"] and
          theme_lock["apk_prefix"] == phase_lock["apk_prefix"], "Asset prefix changed")
    for name, entry in additions.items():
        data = (Path(repo) / phase_lock["source_prefix"] / name).read_bytes()
        check(len(data) == entry["bytes"] and sha256(data) == entry["sha256"], "Additional asset bytes changed")
        check(list(png_dimensions(data)) == entry["dimensions"], "Additional PNG dimensions changed")
        alias = phase_lock["alias_of"].get(name)
        check(alias in original and original[alias]["sha256"] == entry["sha256"], "Approved artwork alias changed")
    return {"status": "passed", "additional_files": len(additions), "preserved_files": len(original),
            "files": list(additions.values()), "apk_check": "Existing expanded theme APK gate verifies exact payload and hashes."}


def assets_command(args):
    report = verify_additional_assets(args.repo, read_json(args.additional_lock), read_json(args.asset_lock))
    emit(report, args.output)
    return 0


def coverage_command(args):
    registry, inventory = read_json(args.registry), read_json(args.inventory)
    records, modules, errors = defaultdict(list), {}, []
    ids = {item["id"] for item in inventory["items"]}
    for module in registry["modules"]:
        path = Path(args.reports) / (module["id"] + ".json")
        try:
            report = read_json(path)
            modules[module["id"]] = {key: report[key] for key in
                                     ("status", "capture_count", "expected_capture_count", "junit_suite_seconds")}
            if report["status"] != "passed":
                errors.append("Module not fully validated: " + module["id"])
            for capture in report.get("captures", []):
                check(capture["audit_id"] in ids, "Unknown audit item: " + capture["audit_id"])
                records[capture["audit_id"]].append({"module": module["id"], "file": capture["file"],
                                                     "sha256": capture["sha256"]})
        except (OSError, ValueError, KeyError) as error:
            errors.append(module["id"] + ": " + str(error))
            modules[module["id"]] = {"status": "missing_or_invalid"}
    rows = [{**item, "verified_phase_two_captures": records[item["id"]],
             "status": "captured_cases_only_other_states_pending" if records[item["id"]] else "no_phase_two_capture_evidence",
             "all_required_states_complete": False, "manual_visual_review": "pending", "actual_device_review": "not_run"}
            for item in inventory["items"]]
    emit({"status": "reported", "baseline_commit": registry["baseline_commit"], "modules": modules,
          "errors": errors, "pending_modules": registry["pending_modules"], "item_count": inventory["item_count"],
          "route_count": inventory["route_count"], "items": rows, "all_app_states_complete": False,
          "note": "Captured fixture cases are evidence only for those cases. Other required states and direct review remain pending."}, args.output)
    return 1 if errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(required=True)
    module = commands.add_parser("module")
    module.add_argument("--contract", required=True)
    module.add_argument("--native-review", required=True)
    module.add_argument("--junit", required=True)
    module.add_argument("--asset-lock", default=str(HERE / "theme-assets-lock.json"))
    module.set_defaults(function=module_command)
    assets = commands.add_parser("assets")
    assets.add_argument("--repo", required=True)
    assets.add_argument("--additional-lock", default=str(HERE / "phase2-assets-lock.json"))
    assets.add_argument("--asset-lock", default=str(HERE / "theme-assets-lock.json"))
    assets.set_defaults(function=assets_command)
    readiness = commands.add_parser("readiness")
    readiness.add_argument("--registry", required=True)
    readiness.set_defaults(function=readiness_command)
    coverage = commands.add_parser("coverage")
    coverage.add_argument("--registry", required=True)
    coverage.add_argument("--inventory", required=True)
    coverage.add_argument("--reports", required=True)
    coverage.set_defaults(function=coverage_command)
    for command in (module, readiness, coverage, assets):
        command.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        return args.function(args)
    except (OSError, ValueError, TypeError, KeyError, ET.ParseError, struct.error, zlib.error) as error:
        emit({"status": "failed", "errors": [str(error)]}, args.output)
        print("::error::" + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
