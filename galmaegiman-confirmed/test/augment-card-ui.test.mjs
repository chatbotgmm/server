import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { augmentCatalogV2 } from '../src/battle/augment-catalog-v2.ts';
const read = p => readFileSync(new URL(p, import.meta.url), 'utf8');
const web = read('../public/duel/duel.js');
const java = read('../app/android/app/src/main/java/org/dpdns/gmm/NativeAugmentView.java');
const original = read('../../pristine/public/duel/duel.js');
function helpers(source, v2 = false) {
  const context = vm.createContext({});
  const section = source.slice(source.indexOf('const AUG_ICON ='), source.indexOf('let closeAugmentInfo ='));
  const core = source.slice(source.indexOf('const AUG_CORE_TEXT ='), source.indexOf('function chooseSheet('));
  vm.runInContext(`${section}\n${core}\nglobalThis.helpers = { augCore, augKind, augKindName${v2 ? ', augTier, augLabel, augStyle' : ''} };`, context);
  return context.helpers;
}
const ui = helpers(web, true), old = helpers(original);
test('V2 전체 60개는 ID 재사용 여부와 무관하게 정확한 카탈로그 설명을 표시한다', () => {
  for (const a of augmentCatalogV2.augments) {
    assert.equal(ui.augCore(a), a.desc, a.id);
    assert.ok(ui.augLabel(a).startsWith(({ silver: '실버', gold: '골드', prismatic: '프리즘' })[a.tier] + ' · '), a.id);
    assert.match(ui.augStyle(a), /--aug-color:#[a-f0-9]{6};--aug-soft:#[a-f0-9]{6}/);
  }
});
test('V1 24개 설명·분류와 등급 없는 표시는 원본 그대로다', () => {
  const fixture = JSON.parse(read('../../test/battle-fixture.json'));
  const augs = Object.entries(fixture.battleAugments).map(([id, a]) => ({ id, ...a }));
  assert.equal(augs.length, 24);
  for (const a of augs) {
    assert.equal(ui.augCore(a), old.augCore(a), a.id);
    assert.equal(ui.augKind(a.id), old.augKind(a.id), a.id);
    assert.equal(ui.augLabel(a), old.augKindName(a.id), a.id);
    assert.equal(ui.augStyle(a), '');
  }
});
test('신규 36개와 미래 ID는 역할을 유틸로 단정하지 않고 안전하게 표시한다', () => {
  const fresh = augmentCatalogV2.augments.filter(a => !original.includes(`"${a.id}":`));
  assert.equal(fresh.length, 36);
  for (const a of fresh) assert.match(ui.augLabel(a), / · 증강$/);
  const future = { id: 'future-id', version: 2, tier: 'unknown', desc: '서버의 새 설명' };
  assert.equal(ui.augCore(future), future.desc);
  assert.equal(ui.augLabel(future), '등급 미확인 · 증강');
  assert.equal(ui.augTier({ ...future, tier: '__proto__' }).name, '등급 미확인');
  assert.ok(!ui.augStyle({ ...future, tier: '" onmouseover="alert(1)' }).includes('onmouseover'));
});
test('네이티브는 V2 설명 우선·등급 선택색 유지·공통 아이콘 폴백을 연결한다 (소스 계약)', () => {
  assert.ok(java.indexOf('if (augment.optInt("version") == 2)') < java.indexOf('switch (augment.optString("id"))'));
  assert.match(java, /columnCard\(cardColor\(aug\)\)/);
  assert.match(java, /new CardFrame\(cardColor\(aug\), on\)/);
  assert.match(java, /badge\.setTextColor\(cardColor\(aug\)\)/);
  assert.match(java, /aug\.optInt\("version"\) == 2 \? "u-gift"/);
  for (const tier of ['silver', 'gold', 'prismatic']) assert.ok(java.includes(`case "${tier}"`));
});
test('등급 잉크는 실제 양피지 바탕에서 일반 글자 대비 4.5 이상이다', () => {
  const luminance = hex => hex.match(/[a-f\d]{2}/gi).map(x => parseInt(x, 16) / 255).map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4).reduce((a, v, i) => a + v * [.2126, .7152, .0722][i], 0);
  for (const a of augmentCatalogV2.augments) {
    const c = luminance(ui.augTier(a).color);
    for (const bg of ['#f5efdc', '#fffaf0']) assert.ok((luminance(bg) + .05) / (c + .05) >= 4.5, a.tier);
    assert.ok(1.05 / (c + .05) >= 4.5, a.tier);
  }
});
test('네이티브 공통 HTTP는 지원 버전만 광고하며 나머지 요청 코드는 원본과 같다', () => {
  const api = read('../app/android/app/src/main/java/org/dpdns/gmm/NativeApi.java');
  const before = read('../../pristine/app/android/app/src/main/java/org/dpdns/gmm/NativeApi.java');
  const added = '            // 클라이언트 표시 능력만 알린다. V2 활성화는 서버 설정이 결정한다.\n            conn.setRequestProperty("x-gmm-augment-version", "2");\n';
  assert.ok(api.includes(added));
  assert.equal(api.replace(added, ''), before);
});
test('웹 보호막은 V2의 살아 있는 전투원에게 남은 HP 절대량·턴 수만 표시한다', () => {
  const context = vm.createContext({});
  vm.runInContext(web.slice(web.indexOf('function shieldLabel('), web.indexOf('function render() {')) + '\nglobalThis.label = shieldLabel;', context);
  const b = { R: { augmentVersion: 2 }, augmentOffers: { version: 2 }, turn: 3 };
  const f = { hp: 100, augment: { shield: 36, shieldUntil: 4 } };
  assert.equal(context.label(f, b), '보호막 36 · 2턴');
  assert.equal(context.label(f, { ...b, turn: 5 }), '');
  assert.equal(context.label({ ...f, hp: 0 }, b), '');
  assert.equal(context.label({ hp: 100 }, b), '');
  assert.equal(context.label(f, { ...b, R: { augmentVersion: 1 } }), '');
});
test('네이티브 보호막 DTO는 효과 요약과 접근성 설명에 연결된다 (소스 계약)', () => {
  const battle = read('../app/android/app/src/main/java/org/dpdns/gmm/BattleView.java');
  assert.match(battle, /fighter\.optInt\("shield"\)/);
  assert.match(battle, /fighter\.optInt\("shieldTurns"\)/);
  assert.match(battle, /if \(!shield\.isEmpty\(\)\) effects = shield;/);
  assert.match(battle, /if \(!shield\.isEmpty\(\)\) labels\.add\(shield\);/);
});
