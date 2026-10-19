#!/usr/bin/env python3
"""Fail-closed, standard-library-only checks for the registration preview workflow."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib


HERE = Path(__file__).resolve().parent
LOCK = HERE / "registration-runtime-lock.json"
SIZES = ((915, 412), (1006, 411), (1280, 720))
SCREENS = ("recruit", "collection", "team-story", "team-pvp", "result")


def check(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def emit(report, output=None):
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(text, end="")


def png_dimensions(data):
    """Check the PNG stream (including CRCs/deflate), not only file existence."""
    check(data[:8] == b"\x89PNG\r\n\x1a\n", "Invalid PNG signature")
    offset, chunks, payload, ended = 8, [], bytearray(), False
    dimensions = None
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        check(end <= len(data), "Truncated PNG chunk")
        kind = data[offset + 4:offset + 8]
        chunk = data[offset + 8:offset + 8 + length]
        checksum = struct.unpack_from(">I", data, offset + 8 + length)[0]
        check(zlib.crc32(kind + chunk) & 0xFFFFFFFF == checksum, "PNG CRC mismatch")
        if kind == b"IHDR":
            check(not chunks and length == 13, "Invalid PNG IHDR")
            dimensions = struct.unpack_from(">II", chunk)
            check(all(dimensions), "Empty PNG dimensions")
        elif kind == b"IDAT":
            payload.extend(chunk)
        elif kind == b"IEND":
            check(length == 0 and end == len(data), "Invalid PNG end")
            ended = True
        chunks.append(kind)
        offset = end
    check(ended and dimensions and payload, "Incomplete PNG")
    check(len(zlib.decompress(payload)) > 0, "Empty PNG pixels")
    return dimensions


def webp_dimensions(data):
    check(data[:4] == b"RIFF" and data[8:12] == b"WEBP", "Invalid WebP header")
    check(struct.unpack_from("<I", data, 4)[0] + 8 == len(data), "Truncated WebP")
    offset = 12
    while offset + 8 <= len(data):
        kind = data[offset:offset + 4]
        size = struct.unpack_from("<I", data, offset + 4)[0]
        chunk = data[offset + 8:offset + 8 + size]
        check(len(chunk) == size, "Truncated WebP chunk")
        if kind == b"VP8X":
            check(size >= 10, "Invalid VP8X header")
            return (int.from_bytes(chunk[4:7], "little") + 1,
                    int.from_bytes(chunk[7:10], "little") + 1)
        if kind == b"VP8 ":
            check(size >= 10 and chunk[3:6] == b"\x9d\x01\x2a", "Invalid VP8 header")
            return tuple(value & 0x3FFF for value in struct.unpack_from("<HH", chunk, 6))
        if kind == b"VP8L":
            check(size >= 5 and chunk[0] == 0x2F, "Invalid VP8L header")
            value = int.from_bytes(chunk[1:5], "little")
            return ((value & 0x3FFF) + 1, ((value >> 14) & 0x3FFF) + 1)
        offset += 8 + size + size % 2
    raise ValueError("Missing WebP image chunk")


def verify_runtime(read, paths, lock):
    expected = {entry["path"]: entry for entry in lock["files"]}
    check(set(paths) == set(expected),
          "Runtime file mismatch: missing=%s extra=%s" %
          (sorted(set(expected) - set(paths)), sorted(set(paths) - set(expected))))
    content = {}
    for path, entry in expected.items():
        data = read(path)
        check(len(data) == entry["bytes"], "Size mismatch: " + path)
        check(sha256(data) == entry["sha256"], "SHA-256 mismatch: " + path)
        content[path] = data
    manifest = json.loads(content["manifest.json"])
    items = manifest["items"]
    ids = [item["id"] for item in items]
    check(len(ids) == len(set(ids)) == lock["card_count"] == 145, "Expected 145 unique IDs")
    check(manifest["characterCount"] == 145 and manifest["canonicalCharacterCount"] == 145,
          "Manifest character count changed")
    cards = {path for path in paths if path.startswith("resources/cards/")}
    check(cards == {item["path"] for item in items}, "Manifest/card allowlist mismatch")
    for item in items:
        data = content[item["path"]]
        check(item["path"] == "resources/cards/" + item["id"] + ".webp", "Unsafe card path")
        check(sha256(data) == item["sha256"] and len(data) == item["bytes"],
              "Manifest mismatch: " + item["id"])
        check(webp_dimensions(data) == (768, 1024), "Invalid card size: " + item["id"])
    layout = json.loads(content["component/layout.json"])
    check(layout["viewBox"] == [0, 0, 1086, 1448], "Card coordinate system changed")
    check(layout["portraitWindow"] == {"x": 54, "y": 286, "width": 978, "height": 779},
          "Portrait window changed")
    animation = json.loads(content["component/application-animation.json"])
    frames = animation["frames"]
    check(len(frames) == 8 and sum(frame["durationMs"] for frame in frames) == 1000,
          "Expected 8 envelope frames / 1000 ms")
    check(png_dimensions(content["resources/application-envelope-atlas.png"]) == (1774, 887),
          "Unexpected atlas dimensions")
    rectangles = []
    for index, frame in enumerate(frames):
        rect = frame["sourceRect"]
        values = [rect[key] for key in ("x", "y", "width", "height")]
        check(all(type(value) is int for value in values), "Non-integer envelope rectangle")
        x, y, width, height = values
        check(frame["index"] == index and 0 <= x < x + width <= 1774 and
              0 <= y < y + height <= 887, "Invalid envelope rectangle")
        rectangles.append(values)
    total = sum(len(value) for value in content.values())
    check(total == lock["runtime_bytes"], "Runtime byte count mismatch")
    return {"files": len(expected), "cards": len(cards), "runtime_bytes": total,
            "atlas_frames": rectangles, "card_bytes": sum(len(content[path]) for path in cards)}


# This is a finite, reviewed source extension, never a backend directory wildcard.
PROGRESSION_SOURCE_LOCK = HERE / "progression-source-lock.json"
PROGRESSION_PATHS = frozenset(['galmaegiman-confirmed/docs/PROGRESSION.md', 'galmaegiman-confirmed/prisma/manual/20261019_progression_additive.sql', 'galmaegiman-confirmed/prisma/schema.prisma', 'galmaegiman-confirmed/src/battle/engine.ts', 'galmaegiman-confirmed/src/battle/growth.ts', 'galmaegiman-confirmed/src/battle/realtime-adapter.ts', 'galmaegiman-confirmed/src/game.ts', 'galmaegiman-confirmed/src/native.ts', 'galmaegiman-confirmed/src/progression.ts', 'galmaegiman-confirmed/src/pvp/realtime-native-view.ts', 'galmaegiman-confirmed/src/pvp/service.ts', 'galmaegiman-confirmed/src/server.ts', 'galmaegiman-confirmed/test/progression-migration.test.ts', 'galmaegiman-confirmed/test/progression.integration.test.ts'])


MATRIX_TEST_PATH = "galmaegiman-confirmed/test/native-type-matrix.test.ts"
MATRIX_TEST_SHA256 = "2080ce099a136f81a01dd1131a8add09bf3528e66395640ab44983ac7480d7e6"

def verify_matrix_test(root):
    source = root / MATRIX_TEST_PATH
    check(source.is_file() and not source.is_symlink() and source.resolve().is_relative_to(root.resolve()), "Missing or redirected canonical matrix backend test")
    data = source.read_bytes()
    check(len(data) == 2372 and sha256(data) == MATRIX_TEST_SHA256, "Canonical matrix backend test changed")
    return MATRIX_TEST_SHA256


RECEIPT_TEST_PATH = "galmaegiman-confirmed/test/recruitment-owned-receipts.integration.test.ts"
RECEIPT_TEST_SHA256 = "72fa07d762db915b709e344895adf43eaa2f11fa01d5ea6b7d8195a57fb4785d"

def verify_receipt_test(root):
    source = root / RECEIPT_TEST_PATH
    check(source.is_file() and not source.is_symlink() and source.resolve().is_relative_to(root.resolve()), "Missing or redirected owned-receipt backend test")
    data = source.read_bytes()
    check(len(data) == 8395 and sha256(data) == RECEIPT_TEST_SHA256, "Owned-receipt backend test changed")
    return RECEIPT_TEST_SHA256


def verify_source_scope(root, changed, registration_lock, progression_lock=None):
    approved = json.loads(PROGRESSION_SOURCE_LOCK.read_text()) if progression_lock is None else progression_lock
    check(approved.get("schema") == "authorized-progression-source/v1"
          and approved.get("approved_commit") == "5279b39021a6cea5919a61379ca6173e0cd9c98e",
          "Unrecognized progression source authorization")
    files = approved.get("files")
    check(type(files) is list and len(files) == 14 and all(type(item) is dict for item in files),
          "Progression source authorization must contain exactly fourteen files")
    indexed = {item.get("path"): item for item in files}
    check(set(indexed) == PROGRESSION_PATHS, "Progression source path allowlist changed")
    for path, item in indexed.items():
        check(set(item) == {"path", "sha256", "bytes"}
              and type(item["bytes"]) is int and item["bytes"] > 0
              and type(item["sha256"]) is str and len(item["sha256"]) == 64
              and all(c in "0123456789abcdef" for c in item["sha256"]),
              "Invalid progression source lock entry: " + path)
        source = root / path
        check(source.is_file() and not source.is_symlink() and source.resolve().is_relative_to(root.resolve()),
              "Missing or redirected approved progression source: " + path)
        data = source.read_bytes()
        check(len(data) == item["bytes"] and sha256(data) == item["sha256"],
              "Approved progression source hash changed: " + path)
    if MATRIX_TEST_PATH in changed:
        verify_matrix_test(root)
    if RECEIPT_TEST_PATH in changed:
        verify_receipt_test(root)
    allowed_prefixes = ("galmaegiman-confirmed/app/android/", registration_lock["source_prefix"] + "/")
    forbidden = [path for path in changed if path
                 and path != ".github/workflows/home-assembly-check.yml"
                 and path not in indexed and path not in (MATRIX_TEST_PATH, RECEIPT_TEST_PATH) and not path.startswith(allowed_prefixes)]
    check(not forbidden, "Changes outside Android/registration/locked progression scope: " + ", ".join(forbidden))
    return {"approved_progression_files": len(indexed),
            "progression_source_commit": approved["approved_commit"],
            "progression_source_sha256": {path: entry["sha256"] for path, entry in sorted(indexed.items())}}


def verify_source(args):
    lock = json.loads(LOCK.read_text())
    root = Path(args.repo).resolve()
    source = root / lock["source_prefix"]
    paths = [entry["path"] for entry in lock["files"]]
    report = verify_runtime(lambda path: (source / path).read_bytes(), paths, lock)
    manifest = json.loads((source / "manifest.json").read_text())
    original_paths = []
    for item in manifest["items"]:
        relative = Path(item["source"]["path"])
        check(not relative.is_absolute() and ".." not in relative.parts, "Unsafe original path")
        check(str(relative).startswith("galmaegiman-confirmed/public/images/"),
              "Original image outside expected directory")
        original_paths.append(str(relative))
        check(sha256((root / relative).read_bytes()) == item["source"]["sha256"],
              "Original character image changed: " + str(relative))
    # Compare all original/public assets, web/backend and economy data to the
    # original frozen baseline; only fourteen exact progression source hashes extend scope.
    baseline = lock["app_baseline_commit"]
    subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", baseline, "HEAD"], check=True)
    changed = subprocess.check_output(
        ["git", "-C", str(root), "diff", "--name-only", "--no-renames", "-z", baseline, "HEAD"]
    ).decode().split("\0")
    report.update(verify_source_scope(root, changed, lock))
    report.update({"status": "passed", "original_images_unchanged": len(original_paths),
                   "app_baseline_commit": baseline, "art_commit": lock["art_commit"]})
    emit(report, args.output)


def verify_apk(args):
    lock = json.loads(LOCK.read_text())
    reports = []
    for filename in args.apk:
        path = Path(filename)
        with zipfile.ZipFile(path) as archive:
            names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
            check(len(names) == len(set(names)), "Duplicate APK ZIP entries")
            prefix = lock["apk_prefix"] + "/"
            selected = [name[len(prefix):] for name in names if name.startswith(prefix)]
            report = verify_runtime(lambda relative: archive.read(prefix + relative), selected, lock)
            # Do not permit accidental duplicate card payloads under a second path.
            other_cards = [name for name in names if "/registration" in name and
                           name.endswith(".webp") and not name.startswith(prefix)]
            check(not other_cards, "Registration cards included under unexpected APK path")
        report.update({"apk": str(path), "apk_bytes": path.stat().st_size,
                       "apk_sha256": sha256(path.read_bytes()), "status": "passed"})
        reports.append(report)
    emit({"status": "passed", "apks": reports}, args.output)


def verify_junit(args):
    xml_files = sorted(Path(args.directory).glob("TEST-*.xml"))
    check(xml_files, "No JUnit XML reports")
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    suites = {}
    for path in xml_files:
        suite = ET.parse(path).getroot()
        check(suite.tag == "testsuite", "Unexpected JUnit root: " + str(path))
        counts = {key: int(suite.get(key, "0")) for key in totals}
        check(counts["tests"] == len(suite.findall("testcase")), "JUnit testcase count mismatch")
        check(not suite.findall("testcase/failure") and not suite.findall("testcase/error"),
              "JUnit testcase contains a failure/error")
        check(counts["skipped"] == len(suite.findall("testcase/skipped")), "JUnit skipped count mismatch")
        for key in totals:
            totals[key] += counts[key]
        suites[suite.get("name")] = counts
    check(not totals["failures"] and not totals["errors"], "JUnit reports contain test failures/errors")
    check(totals["tests"] >= args.minimum_tests, "Full suite test count fell below baseline")
    for required in args.require_suite:
        check(required in suites, "Required suite did not run: " + required)
        count = suites[required]
        check(count["tests"] > count["skipped"], "Required suite was empty/fully skipped: " + required)
    for required in args.require_unskipped_suite:
        check(required in suites, "Required suite did not run: " + required)
        count = suites[required]
        check(count["tests"] > 0 and count["skipped"] == 0, "Required suite was empty/skipped: " + required)
    warnings = []
    if args.previous_skipped is not None and totals["skipped"] > args.previous_skipped:
        warning = "Skipped tests increased from %d to %d; review the retained full report." % (
            args.previous_skipped, totals["skipped"])
        warnings.append(warning)
        print("::warning::" + warning)
    totals["passed"] = totals["tests"] - totals["skipped"]
    emit({"status": "passed", "totals": totals, "suites": suites, "warnings": warnings}, args.output)


def verify_captures(args):
    root = Path(args.directory)
    expected = {}
    if args.kind == "home":
        expected = {"home-%dx%d" % size: size for size in SIZES}
    else:
        for screen in SCREENS:
            expected.update({"%s-%dx%d" % (screen, *size): size for size in SIZES})
            expected[screen + "-915x412-font130"] = (915, 412)
    findings = json.loads((root / "findings.json").read_text())
    check(isinstance(findings, dict), "Findings must map screenshot names to issue arrays")
    files = []
    hashes = set()
    for name, dimensions in expected.items():
        data = (root / (name + ".png")).read_bytes()
        check(png_dimensions(data) == dimensions, "Wrong screenshot size: " + name)
        check(name in findings and findings[name] == [], "Missing/nonempty findings: " + name)
        digest = sha256(data)
        check(digest not in hashes, "Duplicate screenshots for different requested cases: " + name)
        hashes.add(digest)
        files.append({"file": name + ".png", "width": dimensions[0], "height": dimensions[1],
                      "bytes": len(data), "sha256": digest})
    emit({"status": "passed", "captures": files,
          "manual_visual_review": "Required separately before recommending installation"}, args.output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(required=True)
    source = subparsers.add_parser("source")
    source.add_argument("--repo", required=True)
    source.set_defaults(function=verify_source)
    apk = subparsers.add_parser("apk")
    apk.add_argument("apk", nargs="+")
    apk.set_defaults(function=verify_apk)
    junit = subparsers.add_parser("junit")
    junit.add_argument("directory")
    junit.add_argument("--minimum-tests", type=int, default=1)
    junit.add_argument("--previous-skipped", type=int)
    junit.add_argument("--require-suite", action="append", default=[])
    junit.add_argument("--require-unskipped-suite", action="append", default=[])
    junit.set_defaults(function=verify_junit)
    captures = subparsers.add_parser("captures")
    captures.add_argument("kind", choices=("home", "registration"))
    captures.add_argument("directory")
    captures.set_defaults(function=verify_captures)
    for command in (source, apk, junit, captures):
        command.add_argument("--output")
    args = parser.parse_args()
    try:
        args.function(args)
    except (ValueError, OSError, KeyError, TypeError, struct.error, zlib.error,
            zipfile.BadZipFile, ET.ParseError, subprocess.CalledProcessError) as error:
        print("::error::" + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

