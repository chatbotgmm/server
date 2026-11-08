#!/usr/bin/env python3
"""랜덤 디펜스(public/defense) 자료 만들기: 60종 이름·등급·역할과 공개 조합법만 data.js로.

히든 조합법(visibility=hidden)은 넣지 않는다(브라우저 파일은 누구나 볼 수 있어서 비밀이 드러남).
히든 갈매미 자체는 보스 보상으로 얻는다(게임 규칙, defense.js).

사용: python3 -I scripts/web/build-defense-data.py [catalog.json] [출력 data.js]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
src = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'data/test-profiles/five-stat-device-test-v1/catalog.json'
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'public/defense/data.js'

cat = json.loads(src.read_text(encoding='utf-8'))
units = [{'id': c['id'], 'name': c['name'], 'grade': c['rarity'], 'role': c['role']} for c in cat['characters']]
ids = {u['id'] for u in units}
recipes = []
hidden = 0
for r in cat['recipes']:
    if r.get('visibility') != 'public':
        hidden += 1
        continue
    ing = []
    for i in r['ingredients']:
        assert i['unitId'] in ids, i
        ing += [i['unitId']] * int(i['count'])
    assert r['resultId'] in ids, r
    recipes.append({'out': r['resultId'], 'in': sorted(ing)})

body = (
    '// 자동 생성: scripts/web/build-defense-data.py (원본 ' + cat['version'] + ' catalog.json). 직접 고치지 않는다.\n'
    '// 공개 조합법만 넣었다(히든 조합법 ' + str(hidden) + '개는 제외).\n'
    'export const UNITS = ' + json.dumps(units, ensure_ascii=False, separators=(',', ':')) + ';\n'
    'export const RECIPES = ' + json.dumps(recipes, ensure_ascii=False, separators=(',', ':')) + ';\n'
)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(body, encoding='utf-8')
print(f'갈매미 {len(units)}종, 공개 조합법 {len(recipes)}개(히든 {hidden}개 제외) → {out}')
