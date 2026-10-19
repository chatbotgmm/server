#!/usr/bin/env python3
"""Additional theme gates; existing registration/home verification stays unchanged."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from verify_registration_ci import check, emit, png_dimensions, sha256

HERE = Path(__file__).resolve().parent
LOCK = HERE / "theme-assets-lock.json"
CONTRACT = HERE / "theme-review-contract.json"
INVENTORY = HERE / "theme-coverage-inventory.json"


def verify_payload(read, paths, lock):
    expected = {entry["path"]: entry for entry in lock["files"]}
    check(set(paths) == set(expected), "Theme payload mismatch: missing=%s extra=%s" %
          (sorted(set(expected) - set(paths)), sorted(set(paths) - set(expected))))
    total = 0
    for path, entry in expected.items():
        data = read(path)
        check(len(data) == entry["bytes"], "Theme asset byte count changed: " + path)
        check(sha256(data) == entry["sha256"], "Theme asset SHA-256 changed: " + path)
        check(list(png_dimensions(data)) == entry["dimensions"], "Theme PNG dimensions changed: " + path)
        total += len(data)
    check(total == lock["runtime_bytes"], "Theme total byte count changed")
    counts = {role: sum(entry["role"] == role for entry in expected.values())
              for role in ("background", "ui", "protected_home_art")}
    check(counts == {"background": 7, "ui": 6, "protected_home_art": 11}, "Theme asset inventory changed")
    return {"status": "passed", "files": len(expected), "runtime_bytes": total, "roles": counts}


def allowed_asset_changes(changed, lock):
    allowed = {lock["source_prefix"] + "/" + entry["path"]
               for entry in lock["files"] if not entry["baseline"]}
    # No implicit permission for new sprites, registration edits, fonts or audio.
    sensitive_suffixes = {".png", ".webp", ".jpg", ".jpeg", ".gif", ".svg", ".ktx", ".ktx2",
                          ".ttf", ".otf", ".woff", ".woff2", ".mp3", ".ogg", ".wav"}
    forbidden = [path for path in changed if path and path not in allowed and
                 ("/src/main/assets/" in path or Path(path).suffix.lower() in sensitive_suffixes)]
    check(not forbidden, "Unapproved art/sprite/font/audio changes: " + ", ".join(forbidden))


def verify_source(args):
    lock = json.loads(LOCK.read_text())
    repo = Path(args.repo).resolve()
    source = repo / lock["source_prefix"]
    paths = [path.relative_to(source).as_posix() for path in source.rglob("*") if path.is_file()]
    report = verify_payload(lambda path: (source / path).read_bytes(), paths, lock)
    baseline = lock["app_baseline_commit"]
    subprocess.run(["git", "-C", str(repo), "merge-base", "--is-ancestor", baseline, "HEAD"], check=True)
    changed = subprocess.check_output(["git", "-C", str(repo), "diff", "--name-only", "--no-renames",
                                       "-z", baseline, "HEAD"]).decode().split("\0")
    allowed_asset_changes(changed, lock)
    report.update({"app_baseline_commit": baseline, "protected_native_home_files": 11,
                   "unapproved_asset_changes": [], "added_files": lock["added_file_count"]})
    emit(report, args.output)


def verify_apk(args):
    lock = json.loads(LOCK.read_text())
    reports = []
    for filename in args.apks:
        path = Path(filename)
        with zipfile.ZipFile(path) as archive:
            names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
            check(len(names) == len(set(names)), "Duplicate APK ZIP entry")
            prefix = lock["apk_prefix"] + "/"
            paths = [name[len(prefix):] for name in names if name.startswith(prefix)]
            report = verify_payload(lambda relative: archive.read(prefix + relative), paths, lock)
        report.update({"apk": str(path), "apk_bytes": path.stat().st_size,
                       "apk_sha256": sha256(path.read_bytes())})
        reports.append(report)
    emit({"status": "passed", "apks": reports}, args.output)


def positive_integer(value):
    return type(value) is int and value > 0


def verify_captures(args):
    directory = Path(args.directory)
    contract = json.loads(CONTRACT.read_text())
    lock = json.loads(LOCK.read_text())
    assets = {entry["path"]: entry for entry in lock["files"]}
    findings = json.loads((directory / "findings.json").read_text())
    metrics = json.loads((directory / "metrics.json").read_text())
    check(isinstance(findings, dict) and isinstance(metrics, dict), "Expected keyed findings and metrics")
    expected = {screen["stem"] + "-" + variant["suffix"]: (screen, variant)
                for screen in contract["screens"] for variant in contract["variants"]}
    check(len(expected) == 32, "Theme contract must contain 32 distinct cases")
    check(set(findings) == set(expected), "Theme findings keys differ from required capture cases")
    check(set(metrics) == set(expected), "Theme metrics keys differ from required capture cases")
    captures, hashes = [], set()
    for stem, (screen, variant) in expected.items():
        data = (directory / (stem + ".png")).read_bytes()
        check(png_dimensions(data) == (variant["width"], variant["height"]), "Wrong theme PNG size: " + stem)
        check(findings[stem] == [], "Theme geometry/interaction finding: " + stem)
        measured = metrics[stem]
        check(isinstance(measured, dict), "Missing theme measurement object: " + stem)
        for key in ("checkedBounds", "actions", "text"):
            check(positive_integer(measured.get(key)), "Missing actual " + key + " checks: " + stem)
        check(measured.get("sceneAsset") == screen["scene_asset"], "Wrong theme scene: " + stem)
        check([measured.get("sceneWidth"), measured.get("sceneHeight")] == assets[screen["scene_asset"]]["dimensions"],
              "Missing/unexpected decoded scene image dimensions: " + stem)
        check(measured.get("rootKind") == screen["root_kind"], "Wrong window root inspected: " + stem)
        check(measured.get("actualNativeViews") is True, "Capture did not inspect native views: " + stem)
        check(type(measured.get("apiReads")) is int and measured["apiReads"] >= 0,
              "Capture did not count fixture reads: " + stem)
        check(type(measured.get("apiWrites")) is int and measured["apiWrites"] == 0,
              "Theme capture issued writes or did not count them: " + stem)
        digest = sha256(data)
        check(digest not in hashes, "Duplicate screenshots for different theme cases: " + stem)
        hashes.add(digest)
        captures.append({"file": stem + ".png", "bytes": len(data), "sha256": digest,
                         "width": variant["width"], "height": variant["height"],
                         "audit_id": screen["audit_id"], "measurements": measured})
    emit({"status": "passed", "capture_count": len(captures), "captures": captures,
          "scope": "Eight representative surfaces in four configurations; not all app pages/states.",
          "manual_visual_review": "Pending separate direct review before recommending installation."}, args.output)


def verify_coverage(args):
    inventory = json.loads(INVENTORY.read_text())
    captures = json.loads(Path(args.capture_report).read_text())
    check(captures.get("status") == "passed" and captures.get("capture_count") == 32,
          "Coverage requires successfully validated theme capture report")
    mapped = {}
    for item in captures["captures"]:
        mapped.setdefault(item["audit_id"], []).append(item["file"])
    items = []
    for item in inventory["items"]:
        pngs = mapped.get(item["id"], [])
        items.append({**item, "verified_theme_pngs": pngs,
                      "coverage": "representative_native_capture_only" if pngs else "not_covered_by_theme_capture",
                      "all_required_states_complete": False, "manual_visual_review": "pending",
                      "actual_device_review": "not_run"})
    emit({"status": "reported", "baseline_commit": inventory["baseline_commit"],
          "route_count": inventory["route_count"], "item_count": inventory["item_count"],
          "theme_capture_count": 32, "representative_audit_items": len(mapped),
          "all_pages_and_states_complete": False, "completion_rule": inventory["completion_rule"],
          "note": "Existing home/registration/full-suite artifacts remain separate evidence. Green CI does not prove untested states or full-app visual completion.",
          "items": items}, args.output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(required=True)
    source = commands.add_parser("source")
    source.add_argument("--repo", required=True)
    source.set_defaults(function=verify_source)
    apk = commands.add_parser("apk")
    apk.add_argument("apks", nargs="+")
    apk.set_defaults(function=verify_apk)
    captures = commands.add_parser("captures")
    captures.add_argument("directory")
    captures.set_defaults(function=verify_captures)
    coverage = commands.add_parser("coverage")
    coverage.add_argument("capture_report")
    coverage.set_defaults(function=verify_coverage)
    for command in (source, apk, captures, coverage):
        command.add_argument("--output")
    args = parser.parse_args()
    try:
        args.function(args)
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print("::error::" + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
