// 상위 조합식 페이지(/defense/recipes, 2026-11-10 사용자 요청): 초불영제·전설·히든 조합식과, 전설·히든이 이어지는 물딜·마딜 초불영제
// 게임과 같은 자료(engine.js → data.js)를 그대로 읽어 그려요. 글은 모두 textContent로 넣어요
import * as E from './engine.js?v=fa4d1d24391f';
const ART = '/game60/assets';
const GRADE_COLOR = { COMMON: '#9aa5ad', UNCOMMON: '#62c370', RARE: '#4d9de0', EPIC: '#a77be8', LEGEND: '#f0a03c', HIDDEN: '#3fd0c0', LIMITED: '#ef5a5a', TRANSCEND: '#ff8fc7', IMMORTAL: '#f3d36b', ETERNAL: '#ffffff' };
const isMag = (id) => E.UNIT[id].role.endsWith('술사'), sideOf = (id) => (isMag(id) ? 'mag' : 'phys');
const isTop = (id) => E.tierOf(id) >= 6 && E.UNIT[id].role !== E.SPECIAL_ROLE, isLH = (id) => [4, 5].includes(E.tierOf(id));
const val = (id) => { if (id === E.TRANSCEND_KEY) return 0; const t = E.tierOf(id); if (t === 0) return 1; if (t === 1) return 2; return E.recipeFor(id).in.reduce((a, i) => a + val(i), 0); };
const tops = E.UNITS.filter((u) => isTop(u.id)).map((u) => u.id).sort((a, b) => E.tierOf(a) - E.tierOf(b) || sideOf(a).localeCompare(sideOf(b)) || +a.slice(1) - +b.slice(1));
const lhs = E.UNITS.filter((u) => isLH(u.id)).map((u) => u.id).sort((a, b) => E.tierOf(a) - E.tierOf(b) || sideOf(a).localeCompare(sideOf(b)) || +a.slice(1) - +b.slice(1));
const usedBy = (id) => tops.filter((t) => E.recipeFor(t).in.includes(id));
const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
const pic = (id, size) => { const i = el('img'); i.src = `${ART}/units/${id}-front.webp`; i.alt = ''; i.width = size; i.height = size; i.loading = 'lazy'; i.decoding = 'async'; return i; };
const recipeText = (id) => { const r = E.recipeFor(id); return r ? `${E.UNIT[id].name} = ${r.in.map((i) => E.UNIT[i].name).join(' + ')}` : E.UNIT[id].name; };
const view = { tab: 'tops', side: 'all', q: '' };

function chipFor(id) {   // 재료 하나: 전설·히든·초불영제면 그 카드로 가는 링크
  const linkable = isLH(id) || isTop(id), a = el(linkable ? 'a' : 'span', 'in');
  if (linkable) a.href = `#${id}`;
  a.style.setProperty('--g', GRADE_COLOR[E.UNIT[id].grade]);
  a.title = recipeText(id);
  a.append(pic(id, 32), el('span', null, E.UNIT[id].name), el('small', null, E.GRADE_NAME[E.UNIT[id].grade]));
  return a;
}
function card(id) {
  const u = E.UNIT[id], s = sideOf(id), c = el('article', 'card'); c.id = id; c.style.setProperty('--g', GRADE_COLOR[u.grade]);
  const head = el('div', 'head'), box = el('div'), meta = el('div', 'meta');
  const gr = el('span', 'chip', E.GRADE_NAME[u.grade]); gr.style.color = GRADE_COLOR[u.grade];
  meta.append(gr, el('span', `chip ${s}`, s === 'mag' ? '마딜' : '물딜'), el('span', 'role', u.role));
  box.append(el('h2', null, u.name), meta); head.append(pic(id, 64), box); c.append(head);
  const tips = E.kitTips(E.kit(id)).filter((t) => !t.startsWith('단일') && !t.startsWith('라인') && t !== '재료 유틸 물려받음');
  if (tips.length) c.append(el('p', 'tips', tips.join(' · ')));
  const r = E.recipeFor(id);
  c.append(el('p', 'lbl', `재료 (커먼 환산 ${val(id)})`));
  const ins = el('div', 'ins'); for (const i of r.in) ins.append(chipFor(i)); c.append(ins);
  if (isLH(id)) for (const side of ['phys', 'mag']) {
    const to = usedBy(id).filter((t) => sideOf(t) === side);
    c.append(el('p', 'lbl', `→ ${side === 'mag' ? '마딜' : '물딜'} 초불영제로 (${to.length})`));
    const row = el('div', 'ins'); for (const t of to) row.append(chipFor(t)); c.append(row);
  }
  return c;
}
const names = (id) => [E.UNIT[id].name, ...E.recipeFor(id).in.map((i) => E.UNIT[i].name), ...(isLH(id) ? usedBy(id).map((t) => E.UNIT[t].name) : [])];
function render() {
  const list = document.getElementById('list'), q = view.q.trim().toLowerCase().replace(/\s+/g, '');
  const ids = (view.tab === 'lh' ? lhs : tops).filter((id) => (view.side === 'all' || sideOf(id) === view.side) && (!q || names(id).some((n) => n.toLowerCase().replace(/\s+/g, '').includes(q))));
  list.replaceChildren(...(ids.length ? ids.map(card) : [el('p', 'empty', '찾는 유닛이 없어요.')]));
  for (const [t, a] of [['tops', 'tab-tops'], ['lh', 'tab-lh']]) { const tab = document.getElementById(a); if (view.tab === t) tab.setAttribute('aria-current', 'page'); else tab.removeAttribute('aria-current'); }
  document.getElementById('tab-tops').textContent = `초불영제 ${tops.length}`;
  document.getElementById('tab-lh').textContent = `전설·히든 ${lhs.length}`;
  document.getElementById('note').textContent = view.tab === 'lh'
    ? '전설·히든은 모두 물딜 초불영제와 마딜 초불영제 양쪽 재료로 쓰여요. 패에 맞춰 어느 쪽으로든 갈 수 있어요. 재료를 누르면 그 카드로 가요.'
    : `초불영제는 전설·히든 2~3마리 + 에픽으로 만들어요(초월 등급은 초월 갈매미맨도 필요). ${ids.length}개 보는 중.`;
}
function route() {   // 주소 끝(#tops·#lh·#u33): 유닛 번호면 그 유닛이 있는 쪽으로 가서 보여 줌
  const h = location.hash.slice(1);
  if (E.UNIT[h] && (isTop(h) || isLH(h))) {
    view.tab = isTop(h) ? 'tops' : 'lh';
    if (view.side !== 'all' && view.side !== sideOf(h)) setSide('all', false);
    if (view.q) { view.q = ''; document.getElementById('q').value = ''; }
    render(); const c = document.getElementById(h); if (c) { c.scrollIntoView({ block: 'center' }); c.classList.add('hit'); setTimeout(() => c.classList.remove('hit'), 1600); }
    return;
  }
  view.tab = h === 'lh' ? 'lh' : 'tops'; render();
}
function setSide(s, draw = true) {
  view.side = s; for (const b of document.querySelectorAll('[data-side]')) b.setAttribute('aria-pressed', String(b.dataset.side === s));
  if (draw) render();
}
for (const b of document.querySelectorAll('[data-side]')) b.addEventListener('click', () => setSide(b.dataset.side));
document.getElementById('q').addEventListener('input', (e) => { view.q = e.target.value; render(); });
addEventListener('hashchange', route);
route();
