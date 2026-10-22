#!/usr/bin/env python3
"""Additional theme gates; existing registration/home verification stays unchanged."""
import argparse
import hashlib
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


MATRIX_ASSET_PATH = "galmaegiman-confirmed/app/android/app/src/main/assets/battle-type-baseline.json"
# 2026-10-20: battle.yaml 승진 배율 변경(88dec36)으로 원본 해시·커밋만 갱신. 상성표·배율 값은 그대로.
MATRIX_ASSET_SHA256 = "8524ced649302dcd1806f8dd6eef60fb293834f981e948bf7039aa8c9e16c489"

def verify_matrix_asset(data):
    check(len(data) == 2267 and sha256(data) == MATRIX_ASSET_SHA256, "Canonical matrix asset changed")
    return MATRIX_ASSET_SHA256


def verify_payload(read, paths, lock, recruitment=None):
    # Inventory is unchanged except the exact approved home lighting identity.
    # Three separately hash-locked trays extend the exact payload.
    if recruitment is not None:
        from verify_recruitment_revision_ci import verify_trays
        tray_report = verify_trays(read, recruitment)
        from recruitment_reference_assets import reference_lock
        reference = [e for e in reference_lock() if 'kind' in e]
        for e in reference:
            relative = e['path'].removeprefix('native/release-v1/')
            data = read(relative)
            check(len(data) == e['bytes'] and sha256(data) == e['sha256'], 'R3 reference PNG identity mismatch')
            check(list(png_dimensions(data)) == e['dimensions'] and data[25] == 6, 'R3 reference PNG dimensions mismatch')
        tray_paths = {entry["path"] for entry in recruitment} | {e['path'].removeprefix('native/release-v1/') for e in reference}
        check(tray_paths <= set(paths), "Missing recruitment tray payload")
        paths = [path for path in paths if path not in tray_paths]
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
    check(counts == {"background": 8, "ui": 6, "protected_home_art": 11}, "Theme asset inventory changed")
    report = {"status": "passed", "files": len(expected), "runtime_bytes": total, "roles": counts}
    if recruitment is not None:
        report["recruitment_trays"] = tray_report
    return report


# 유일한 승인 전이: 실제 이전/이후 bytes를 검사하고 baseline 보호를 유지한다.
HOME_PATH = "galmaegiman-confirmed/app/android/app/src/main/assets/native/release-v1/backgrounds/bg-home.png"
HOME_BEFORE = (3047052, "adce04f6958598d4b6c1382909f7012d0ad880eb5f32aecf671e4ba8796900ea", "a16c93c978c941b70877b8e873ade770fbbbfbfc")
HOME_AFTER = (3013465, "c6a3c3b3b09f13b8659fad7e69c15e913242c022b6111fa6eb34702f5bc1a492", "f568071de2bdae33dd7da33c10d036e5fbb2b43b")


def verify_home_transition(before, after, lock):
    check(lock["source_prefix"] + "/backgrounds/bg-home.png" == HOME_PATH,
          "Unapproved home source path")
    home = [entry for entry in lock["files"] if entry["path"] == "backgrounds/bg-home.png"]
    check(len(home) == 1, "Missing/duplicate approved home entry")
    entry = home[0]
    check(entry["baseline"] is True and entry["dimensions"] == [1870, 841]
          and (entry["bytes"], entry["sha256"], entry["git_blob_sha"]) == HOME_AFTER,
          "Approved home lock identity changed")
    for label, data, identity in (("before", before, HOME_BEFORE), ("after", after, HOME_AFTER)):
        check(type(data) is bytes, "Missing actual home transition bytes: " + label)
        blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        check((len(data), sha256(data), blob) == identity, "Unapproved home transition identity: " + label)
        check(png_dimensions(data) == (1870, 841), "Home transition dimensions changed: " + label)
    return {"path": HOME_PATH, "before_git_blob": HOME_BEFORE[2], "after_git_blob": HOME_AFTER[2]}


def allowed_asset_changes(changed, lock, recruitment=None, home_transition=None):
    allowed = {lock["source_prefix"] + "/" + entry["path"]
               for entry in lock["files"] if not entry["baseline"]}
    if recruitment is not None:
        from verify_recruitment_revision_ci import TRAYS
        check(len(recruitment) == 3 and {entry["path"] for entry in recruitment} == TRAYS, "Unexpected tray allowlist")
        allowed.update(lock["source_prefix"] + "/" + path for path in TRAYS)
        from recruitment_reference_assets import reference_lock, ASSET_PREFIX
        allowed.update(ASSET_PREFIX + e['path'] for e in reference_lock())
    if home_transition is not None:
        verify_home_transition(*home_transition, lock)
        allowed.add(HOME_PATH)
    allowed.add(MATRIX_ASSET_PATH)
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
    from verify_recruitment_revision_ci import tray_lock
    recruitment = tray_lock()
    report = verify_payload(lambda path: (source / path).read_bytes(), paths, lock, recruitment)
    from recruitment_reference_assets import verify_reference_assets, ASSET_PREFIX
    report['recruitment_reference_v3'] = verify_reference_assets(lambda path: (repo / ASSET_PREFIX / path).read_bytes())
    baseline = lock["app_baseline_commit"]
    subprocess.run(["git", "-C", str(repo), "merge-base", "--is-ancestor", baseline, "HEAD"], check=True)
    changed = subprocess.check_output(["git", "-C", str(repo), "diff", "--name-only", "--no-renames",
                                       "-z", baseline, "HEAD"]).decode().split("\0")
    transition = None
    if HOME_PATH in changed:
        before = subprocess.check_output(["git", "-C", str(repo), "show", baseline + ":" + HOME_PATH])
        transition = (before, (repo / HOME_PATH).read_bytes())
    allowed_asset_changes(changed, lock, recruitment, transition)
    report["canonical_matrix_sha256"] = verify_matrix_asset((repo / MATRIX_ASSET_PATH).read_bytes())
    report.update({"app_baseline_commit": baseline, "protected_native_home_files": 11,
                   "unapproved_asset_changes": [], "added_files": lock["added_file_count"]})
    emit(report, args.output)


def verify_apk(args):
    lock = json.loads(LOCK.read_text())
    from verify_recruitment_revision_ci import tray_lock
    recruitment = tray_lock()
    reports = []
    for filename in args.apks:
        path = Path(filename)
        with zipfile.ZipFile(path) as archive:
            names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
            check(len(names) == len(set(names)), "Duplicate APK ZIP entry")
            prefix = lock["apk_prefix"] + "/"
            paths = [name[len(prefix):] for name in names if name.startswith(prefix)]
            report = verify_payload(lambda relative: archive.read(prefix + relative), paths, lock, recruitment)
            from recruitment_reference_assets import verify_reference_assets
            report['recruitment_reference_v3'] = verify_reference_assets(lambda path: archive.read('assets/'+path))
            report["canonical_matrix_sha256"] = verify_matrix_asset(archive.read("assets/battle-type-baseline.json"))
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

