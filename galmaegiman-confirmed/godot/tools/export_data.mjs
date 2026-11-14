// 고도 판이 쓰는 수치·표를 웹 판 규칙(public/defense/engine.js)에서 그대로 뽑아 godot/data/game_data.json으로 쓴다.
// 밸런스 수치는 웹 판 한 곳에서만 고치고, 고친 뒤 이 스크립트를 다시 돌리면 고도 판에도 같은 값이 들어간다.
// 사용(galmaegiman-confirmed 폴더에서): node godot/tools/export_data.mjs
import { writeFileSync } from 'node:fs';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const here = dirname(fileURLToPath(import.meta.url));
const E = await import(pathToFileURL(resolve(here, '../../public/defense/engine.js')).href);
const pick = (names) => Object.fromEntries(names.map((n) => { if (!(n in E)) throw new Error(`engine.js에 ${n}이(가) 없어요`); return [n, E[n]]; }));
const data = {
  ...pick(['UNITS', 'RECIPES', 'GRADES', 'GRADE_NAME', 'WISP_GRADES', 'RULES', 'REGIONS', 'WISP_SKILL', 'EXPLORE', 'EXPLORE_TOP', 'EXPLORE_INHERIT',
    'UTIL_EXTRA', 'UTIL_BUFF', 'UTIL_WEAKEN', 'BUFF_R', 'WEAKEN_R', 'BUFF_MAX', 'WEAKEN_MAX', 'ELEM_NAME', 'SLOW_MAX', 'SLOW_EFFECT', 'STUN_MIN', 'STUN_MAX', 'BOSS_STUN',
    'SIGNATURE', 'INHERIT', 'ENEMY_LOOK', 'BOSS_LOOK', 'MODES', 'DIFFS', 'DIFF_ORDER', 'SAVE_VER', 'OLD_STORY_LEN', 'PICK_GRADES', 'STORE_GROUPS', 'WALK',
    'UPG_NAME', 'TOP_TIER', 'TOP_POWER_TIER', 'SPECIAL_ROLE', 'TRANSCEND_KEY', 'GOAL_MAX', 'W', 'H', 'FIELD', 'LOOP', 'SPAWN', 'COMMON_SPOTS', 'INNER']),
};
const out = resolve(here, '../data/game_data.json');
writeFileSync(out, JSON.stringify(data, null, 1) + '\n');
console.log(`썼어요: ${out} (유닛 ${data.UNITS.length} · 조합법 ${data.RECIPES.length})`);
