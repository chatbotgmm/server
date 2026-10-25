#!/usr/bin/env python3
"""방범대 등록증 v10(60장, 900×1200 PNG)을 APK용 WebP로 줄인다.

원본(art/gpt/registration-v10)은 건드리지 않는다. 빌드 폴더에만 결과를 만든다.
- manifest.json의 SHA-256·크기와 실제 파일을 대조하고 하나라도 다르면 실패한다.
- 완성 카드(프레임·제목 포함)를 비율 그대로 720×960으로 줄여 WebP 품질 88로 저장한다.
- index.json: {version, cards: {u01: "u01.webp", ...}} — 앱 로더가 읽는다.
사용: python3 scripts/registration-v10-webp.py <원본 폴더> <생성 폴더>
"""
import hashlib
import json
import sys
from pathlib import Path

from PIL import Image, features

WIDTH, HEIGHT = 720, 960
QUALITY = 88


def main(source: Path, out: Path) -> None:
    if not features.check("webp"):
        raise SystemExit("Pillow WebP 지원이 없어요.")
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    items = manifest["items"]
    if manifest.get("count") != 60 or len(items) != 60:
        raise SystemExit(f"등록증은 60장이어야 해요: {len(items)}")
    out.mkdir(parents=True, exist_ok=True)
    cards, total = {}, 0
    for item in items:
        card_id, path = item["id"], source / item["path"]
        data = path.read_bytes()
        if len(data) != item["size_bytes"] or hashlib.sha256(data).hexdigest() != item["sha256"]:
            raise SystemExit(f"원본 해시가 manifest와 달라요: {path}")
        if card_id in cards or "/" in card_id or ".." in card_id:
            raise SystemExit(f"잘못된 ID: {card_id}")
        with Image.open(path) as image:
            if list(image.size) != item["dimensions"] or image.size != (900, 1200):
                raise SystemExit(f"크기가 달라요: {path} {image.size}")
            card = image.convert("RGB").resize((WIDTH, HEIGHT), Image.LANCZOS)
        target = out / f"{card_id}.webp"
        card.save(target, "WEBP", quality=QUALITY, method=4)
        cards[card_id] = target.name
        total += target.stat().st_size
    (out / "index.json").write_text(json.dumps(
        {"version": manifest["version"], "width": WIDTH, "height": HEIGHT, "cards": cards},
        ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"등록증 v10 WebP: {len(cards)}장, {total} bytes ({WIDTH}×{HEIGHT}, 품질 {QUALITY})")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("사용: registration-v10-webp.py <원본 폴더> <생성 폴더>")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
