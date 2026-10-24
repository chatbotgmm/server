#!/usr/bin/env python3
"""Validate the pinned Git art export and prepare an OFFLINE Android review input.

No Git/network/build/DB operations. Output must not exist. No production wiring.
Requires Pillow for decoding/alpha checks; original PNG bytes are copied unchanged.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
from PIL import Image

ART_COMMIT = "d8465fd3d991212e5318fe3cee2fdd2326c6c375"
MANIFEST_SHA256 = "205699537e0fdc184ced33211210aed906f9f6ddbe80d9a12de61dc19907005d"
DISPLAY_NAMES = {"u01": "갈매미맨"}  # User-approved label override; source archive stays unchanged.
REVIEW_ART_IDS = ("u01",)  # New roster IDs only; no permanent legacy-character mapping.


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(source):
    if sha(source / "manifest.json") != MANIFEST_SHA256:
        raise ValueError("Manifest differs from pinned art commit; review the new revision first")
    manifest = json.loads((source / "manifest.json").read_text())
    entries = manifest["entries"]
    if len(entries) != 60 or {e["id"] for e in entries} != {f"u{i:02}" for i in range(1, 61)}:
        raise ValueError("Expected 60 unique archive IDs")
    files, index = {}, {}
    for entry in entries:
        for info in entry["files"]:
            name = info["file"]
            rel = PurePosixPath(name)
            if rel.is_absolute() or ".." in rel.parts or rel.parts[0] != entry["id"] or rel.suffix != ".png":
                raise ValueError("Unsafe or mismatched image path")
            path = source / name
            if not path.resolve().is_relative_to(source.resolve()) or sha(path) != info["sha256"]:
                raise ValueError(f"Image hash/path mismatch: {name}")
            with Image.open(path) as image:
                image.load()
                if image.mode != "RGBA" or list(image.size) != info["size"]:
                    raise ValueError(f"Image mode/size mismatch: {name}")
            if name in files:
                raise ValueError("Duplicate image")
            files[name] = info
        active = entry["active_views"]
        if set(active) != {"front", "rear"}:
            raise ValueError("Both active directions required")
        views = {}
        for facing, view in active.items():
            name, rect = view["file"], view["source_rect_ltrb_exclusive"]
            info = files[name]
            if info["sha256"] != view["sha256"] or not name.startswith(entry["id"] + "/"):
                raise ValueError("Active view identity mismatch")
            if len(rect) != 4 or any(type(v) is not int for v in rect):
                raise ValueError("Integer exclusive rectangle required")
            left, top, right, bottom = rect
            if not (0 <= left < right <= info["size"][0] and 0 <= top < bottom <= info["size"][1]):
                raise ValueError("Active rectangle outside source")
            # Inspect only the authoritative region. Never use a midpoint split.
            with Image.open(source / name) as image:
                alpha = image.getchannel("A").crop(tuple(rect))
                bounds = alpha.point(lambda a: 255 if a > 8 else 0).getbbox()
            if bounds is None:
                raise ValueError("Empty active silhouette")
            views[facing] = dict(view,
                visible_bounds_local_alpha_gt8=list(bounds),
                anchor_candidate_local=[(bounds[0] + bounds[2]) / 2, bounds[3]],
                anchor_status="candidate_not_approved")
        index[entry["id"]] = {
            "views": views, "review_status": entry["review_status"],
            "known_issues": entry.get("known_issues", [])}
    if len(files) != 69 or len(list(source.rglob("*.png"))) != 69:
        raise ValueError("Expected exactly 69 original PNGs")
    if index["u17"]["views"]["rear"]["file"] != "u17/rear-v4.png":
        raise ValueError("u17 must use the active rear override")
    return index


def prepare(source, output):
    if output.exists():
        raise ValueError("Output already exists; refusing to overwrite")
    index = validate(source)  # Validate everything before creating any output.
    payload = {
        "schema_version": 1, "review_only": True, "default_enabled": False,
        "art_commit": ART_COMMIT, "source_manifest_sha256": sha(source / "manifest.json"),
        "asset_root_proposal": "native/roster60-static-review/",
        "side_to_view": {"0": "rear", "1": "front"},
        "fallback": "existing_renderer",
        "review_constraints": {"u02_height_relative_to_u01": 0.6,
            "mini_in_review_package": False, "anchors_approved": False},
        "characters": {art_id: dict(index[art_id], art_id=art_id, display_name=DISPLAY_NAMES[art_id])
                       for art_id in REVIEW_ART_IDS},
    }
    output.mkdir(parents=True)
    for character in payload["characters"].values():
        for view in character["views"].values():
            target = output / view["file"]
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source / view["file"], target)
            if sha(target) != view["sha256"]:
                raise ValueError("Copy verification failed")
    (output / "catalog.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    (output / "validation.json").write_text(json.dumps({
        "characters_checked": len(index), "active_views_checked": 120,
        "png_checked": 69, "review_art_ids": list(REVIEW_ART_IDS),
        "android_build_run": False, "runtime_integrated": False}, indent=2) + "\n")
    return payload


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Exact roster60 directory exported from pinned Git commit")
    parser.add_argument("output", type=Path, help="New review directory; never production assets")
    args = parser.parse_args()
    prepare(args.source, args.output)
    print("Validated 60 characters / 120 active views / 69 PNGs; prepared u01 only. Runtime NOT connected.")
