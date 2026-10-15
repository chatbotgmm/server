import { rarityNames } from './content.js';
export const normalizeName = (value) => value.normalize('NFKC').replace(/\s+/gu, '').toLowerCase();
export const shortName = (value) => value.replace(/\s*갈매미맨$/u, '');
export const HIDDEN_NAME = '???';
export function characterInfo(content, c) {
    const synergy = c.synergy ? ` · ${content.economy.expedition.effects[c.synergy].label} 시너지` : '';
    return `${rarityNames[c.rarity]}${synergy}`;
}
export const isRevealed = (c, known) => c.rarity !== 'HIDDEN' || known.has(c.id);
export const displayName = (c, known) => isRevealed(c, known) ? c.name : HIDDEN_NAME;
// 미발견 히든의 이름을 플레이어 화면에서 가립니다.
export function mask(content, text, known) {
    let out = text;
    const hidden = content.hiddenIds.filter(id => !known.has(id)).map(id => content.map.get(id).name).sort((a, b) => b.length - a.length);
    for (const name of hidden)
        out = out.split(name).join(HIDDEN_NAME);
    return out;
}
export function matchCharacters(characters, query) {
    const key = normalizeName(query);
    if (!key)
        return [];
    const byId = characters.find(c => c.id.toLowerCase() === key);
    if (byId)
        return [byId];
    const full = characters.filter(c => normalizeName(c.name) === key);
    if (full.length)
        return full;
    const short = characters.filter(c => normalizeName(shortName(c.name)) === key);
    if (short.length)
        return short;
    return key.length >= 2 ? characters.filter(c => normalizeName(c.name).includes(key)) : [];
}
export function listReply(title, items, hint, choices = []) {
    return { text: `${title}\n\n${items.map(item => `${item.title}\n${item.description}`).join('\n\n')}\n\n${hint}`, list: { title, items }, choices };
}
export function materialCounts(materials) {
    const counts = new Map();
    for (const id of materials)
        counts.set(id, (counts.get(id) ?? 0) + 1);
    return counts;
}
export function duration(ms) {
    const total = Math.max(0, Math.ceil(ms / 60000));
    const h = Math.floor(total / 60), m = total % 60;
    return h ? `${h}시간${m ? ` ${m}분` : ''}` : `${m}분`;
}
