#!/usr/bin/env python3
"""상위 33종 APK 파생 시트(셀 256px PNG)를 WebP로 바꿔 앱 용량을 줄인다.

PrepareUpper33Art.java가 만든 출력 폴더를 그 자리에서 바꾼다(원본 576MB는 건드리지 않음).
- 264장 모두 768×512, 투명도 유지(손실 압축, 투명 채널은 무손실)
- catalog.json의 url을 .webp로 바꾸고, 바뀐 PNG는 지운다
- Pillow에 WebP 기능이 없으면 실패한다(조용히 PNG로 넘어가지 않음)
사용: python3 scripts/upper33-webp.py <생성 폴더>
"""
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

from PIL import Image, features

QUALITY = 85
# method 6은 264장에 약 22분(4코어 기준) 걸려 빌드마다 부담 → 4로 낮추고 여러 장을 함께 변환한다.
METHOD = 4


def convert(png: Path) -> tuple[int, int]:
    with Image.open(png) as image:
        if image.size != (768, 512):
            raise SystemExit(f"크기가 달라요: {png} {image.size}")
        rgba = image.convert("RGBA")
    webp = png.with_suffix(".webp")
    rgba.save(webp, "WEBP", quality=QUALITY, method=METHOD, alpha_quality=100, exact=False)
    with Image.open(webp) as check:
        if check.size != (768, 512) or check.mode != "RGBA":
            raise SystemExit(f"WebP 검증 실패: {webp} {check.size} {check.mode}")
    before = png.stat().st_size
    png.unlink()
    return before, webp.stat().st_size


def main(out: Path) -> None:
    if not features.check("webp"):
        raise SystemExit("Pillow WebP 지원이 없어요. WebP 가능한 Pillow를 설치해 주세요.")
    catalog_path = out / "catalog.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    pngs = []
    for character in catalog["characters"]:
        for sheet in character["sheets"]:
            png = out / sheet["url"]
            if png.suffix != ".png" or not png.is_file():
                raise SystemExit(f"생성된 PNG가 없어요: {png}")
            pngs.append(png)
            sheet["url"] = sheet["url"][:-4] + ".webp"
    with ProcessPoolExecutor(max_workers=min(4, os.cpu_count() or 1)) as pool:
        sizes = list(pool.map(convert, pngs))
    before = sum(b for b, _ in sizes)
    after = sum(a for _, a in sizes)
    count = len(sizes)
    if count != 264:
        raise SystemExit(f"264장이어야 해요: {count}")
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"상위 33종 WebP: {count}장, PNG {before} → WebP {after} bytes (품질 {QUALITY}, method {METHOD})")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("사용: upper33-webp.py <생성 폴더>")
    main(Path(sys.argv[1]))
