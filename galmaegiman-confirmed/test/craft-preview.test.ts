import {describe, expect, it, vi} from 'vitest';
import type {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';

// DB 없이 실제 응답 조립·보호 수량·계정별 비공개 경계를 검증한다.
const content = loadContent();
function fixture() {
  const known = new Set<string>();
  const tx = {
    player: {findUnique: vi.fn(async () => ({id:'viewer'}))},
    collectionEntry: {findMany: vi.fn(async () => [...known].map(characterId => ({characterId})))},
    ownedCharacter: {findMany: vi.fn(async (query: {where?:{loanExpiresAt?:unknown}}) => {
      // This preview fixture contains no expired loans; do not bypass production pruning.
      expect(query.where?.loanExpiresAt).toBeDefined();return [];
    }),groupBy: vi.fn(async () => content.characters.flatMap(c => [
      {characterId:c.id,locked:false,status:'AVAILABLE',_count:{_all:2}},
      {characterId:c.id,locked:true,status:'AVAILABLE',_count:{_all:3}},
      {characterId:c.id,locked:false,status:'EXPEDITION',_count:{_all:4}},
    ]))},
  };
  const db = {$transaction: async (run: (tx: unknown) => unknown) => run(tx)} as unknown as PrismaClient;
  return {game:new GameService(db,content),known,tx};
}
describe('조합 작업대 미리보기 응답', () => {
  it('힌트가 있어도 미발견 결과의 이름·그림·레시피는 내보내지 않는다', async () => {
    const {game} = fixture();
    const result = await game.webCraftInfo('viewer');
    const hidden = content.recipes.filter(r => r.hidden);
    for (const recipe of hidden) {
      expect(result.recipes.some(r => r.id === recipe.resultId)).toBe(false);
      expect(result.pool.some(r => r.id === recipe.resultId)).toBe(false);
      expect(JSON.stringify(result)).not.toContain(content.map.get(recipe.resultId)!.name);
    }
    expect(result.pool.some(u => u.hiddenHint)).toBe(true);
    expect(result.pool.every(u => typeof u.hiddenHint === 'boolean')).toBe(true);
  });
  it('잠금과 탐험·편성 보호 개체를 사용 가능 수량에 합산하지 않는다', async () => {
    const {game,tx} = fixture();
    const result = await game.webCraftInfo('viewer');
    expect(result.pool.every(u => u.usable === 2)).toBe(true);
    expect(tx.ownedCharacter.groupBy).toHaveBeenCalledWith(expect.objectContaining({where:expect.objectContaining({playerId:'viewer'})}));
  });
  it('발견 계정과 미발견 계정의 결과·힌트는 캐시로 섞이지 않는다', async () => {
    const {game,known} = fixture();
    for (const r of content.recipes.filter(r => r.hidden)) known.add(r.resultId);
    const discovered = await game.webCraftInfo('viewer');
    expect(discovered.pool.every(u => !u.hiddenHint)).toBe(true);
    known.clear();
    const fresh = await game.webCraftInfo('other-viewer');
    expect(fresh.pool.some(u => u.hiddenHint)).toBe(true);
    expect(fresh.recipes.length).toBeLessThan(discovered.recipes.length);
  });
  it('부족·잘못된 개체 ID는 소모 없이 실패하고 반복한 불일치 조합도 재료를 유지한다', async () => {
    const {game} = fixture();
    const publicIds = content.characters.filter(c => c.rarity !== 'HIDDEN').map(c => c.id);
    const id = publicIds[0];
    await expect(game.webMix('viewer',['not-owned-instance',id])).rejects.toThrow();
    await expect(game.webMix('viewer',[id,id,id])).rejects.toThrow('부족');
    const pair = publicIds.flatMap(a => publicIds.map(b => [a,b])).find(ids =>
      !content.recipes.some(r => [...r.materials].sort().join('+') === [...ids].sort().join('+')))!;
    // 실패 조합에서 허용되는 튜토리얼 진행만 격리한다. 소모 쓰기 메서드는 fixture에 없다.
    vi.spyOn(game as unknown as {tutorialStep: () => Promise<string[]>},'tutorialStep').mockResolvedValue([]);
    expect(await game.webMix('viewer',pair)).toMatchObject({made:false});
    expect(await game.webMix('viewer',pair)).toMatchObject({made:false});
  });

});
