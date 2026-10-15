import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { PrismaClient } from '@prisma/client';
import { buildApp } from '../src/app.js';
import { GameService } from '../src/game.js';
import { loadContent } from '../src/content.js';
import { loadBattleRules } from '../src/battle/load.js';
import { loadStory } from '../src/battle/story.js';
import { innateLearned, promoSkills, slotSkills, stableEvoChoices, statsOf } from '../src/battle/growth.js';
import { nativeEvolutionChoices } from '../src/native.js';
const R = loadBattleRules(), content = loadContent(), story = loadStory(process.cwd(), R);
const record = (uid = 'o1', level = 20, stage = 0) => ({ uid, id: 'D1', name: content.map.get('D1').name, tier: content.map.get('D1').rarity, level, xp: 17, stage, evoAugs: [], learned: innateLearned(R, 'D1', uid), learnUsed: 0, learnPick: 0 });
describe('승진 미리보기의 엔진 계산', () => {
    it('같은 후보와 능력치를 재현하며 개체·규칙·학습 기술을 변경하지 않는다', () => {
        const r = record(), saved = JSON.stringify(r), rules = JSON.stringify(R.data), choices = nativeEvolutionChoices(R, r);
        expect(choices.map(a => a.id)).toEqual(stableEvoChoices(R, r).map(a => a.id));
        expect(choices.length).toBeGreaterThan(0);
        expect(nativeEvolutionChoices(R, r)).toEqual(choices);
        for (const choice of choices) {
            const next = { ...structuredClone(r), stage: 1, evoAugs: [...r.evoAugs, choice.id] };
            expect(choice.preview).toEqual({ fromStage: 0, toStage: 1, beforeStats: statsOf(R, r), afterStats: statsOf(R, next), beforeSkills: slotSkills(R, r), afterSkills: slotSkills(R, next), beforePromo: promoSkills(R, r), afterPromo: promoSkills(R, next), restoresHp: true, cost: 0 });
            expect(choice.preview.afterStats.hp).toBeGreaterThan(choice.preview.beforeStats.hp);
            expect(choice.preview.afterSkills).toEqual(choice.preview.beforeSkills);
            expect(choice.preview.beforePromo).toEqual([]);
            expect(choice.preview.afterPromo.map(s => s.id)).toEqual(['sig']);
        }
        // 미리보기 응답에 들어간 기술 객체도 실제 규칙의 고유기를 공유하지 않는다.
        choices[0].preview.afterPromo[0].desc = '화면에서 바꾼 설명';
        expect(JSON.stringify(r)).toBe(saved);
        expect(JSON.stringify(R.data)).toBe(rules);
        expect(nativeEvolutionChoices(R, r)[0].preview.afterPromo[0].desc).not.toBe('화면에서 바꾼 설명');
    });
    it('이미 배운 전용기는 승진으로 중복 해금하지 않고 최종 단계는 후보가 없다', () => {
        const r = record('o2', 35, 1);
        r.evoAugs = [stableEvoChoices(R, { ...r, stage: 0 })[0].id];
        r.learned = ['@', 'basic', 'sig'];
        const choices = nativeEvolutionChoices(R, r);
        expect(choices.length).toBeGreaterThan(0);
        for (const { preview } of choices) {
            expect(preview).toMatchObject({ fromStage: 1, toStage: 2, beforePromo: [], afterPromo: [] });
            expect(preview.beforeSkills).toEqual(preview.afterSkills);
        }
        expect(nativeEvolutionChoices(R, { ...r, stage: 3 })).toEqual([]);
    });
});
const suite = process.env.TEST_DB_URL ? describe : describe.skip;
suite('네이티브 승진 미리보기 API (격리 DB)', () => {
    let db, game, app, playerId, headers, serial = 0;
    const get = async () => { const response = await app.inject({ method: 'GET', url: '/my/api/native/state', headers }); expect(response.statusCode, response.body).toBe(200); return response.json(); };
    const post = async (payload) => app.inject({ method: 'POST', url: '/my/api/native/growth', headers, payload });
    const own = async (id = 'D1', owner = playerId) => `o${(await db.ownedCharacter.create({ data: { playerId: owner, characterId: id, obtainedVia: 'TEST' } })).id}`;
    const savedUnit = (r) => ({ level: r.level, xp: r.xp, stage: r.stage, evoAugs: r.evoAugs, learned: r.learned, learnUsed: r.learnUsed, learnPick: r.learnPick });
    const firstPart = story.chapters.find(c => c.id === '1').stages.at(-1).id;
    beforeAll(async () => {
        const url = process.env.TEST_DB_URL;
        if (!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))
            throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
        db = new PrismaClient({ datasources: { db: { url } } });
    });
    beforeEach(async () => {
        const identity = `native-growth-preview-${Date.now()}-${serial++}`;
        game = new GameService(db, content);
        await game.handle(identity, '내정보');
        playerId = (await db.player.findUniqueOrThrow({ where: { identity } })).id;
        app = await buildApp(db, game, { secret: 'native-growth-preview-test-secret'.repeat(3), baseUrl: async () => 'https://example.com' });
        const code = (await game.handle(identity, '웹 로그인')).text.match(/\b\d{6}\b/)[0];
        const login = await app.inject({ method: 'POST', url: '/my/api/login', headers: { 'x-gmm': '1', 'x-gmm-app': '1' }, payload: { code } });
        expect(login.statusCode, login.body).toBe(200);
        headers = { 'x-gmm': '1', 'x-gmm-s': login.json().token };
    });
    afterEach(async () => { if (app)
        await app.close(); });
    afterAll(async () => { await db.$disconnect(); });
    it('각 표창의 미리보기는 실제 승진 후 수치·기술·개체와 같고 비용 없이 체력을 회복한다', async () => {
        const key = await own(), r = record(key), other = await own(), untouched = record(other, 15);
        const fixture = () => ({ units: { [key]: savedUnit(r), [other]: savedUnit(untouched) }, team: [key], cleared: [firstPart], hp: { [key]: { f: .2, at: Date.now() }, [other]: { f: .4, at: Date.now() } } });
        await game.webSaveStory(playerId, fixture());
        const choices = (await get()).units.find((u) => u.key === key).evolve;
        for (const choice of choices) {
            await game.webSaveStory(playerId, fixture());
            const wallet = await db.player.findUniqueOrThrow({ where: { id: playerId } }), before = await game.webStory(playerId);
            const response = await post({ key, kind: 'evolve', augment: choice.id });
            expect(response.statusCode, response.body).toBe(200);
            const grown = response.json().unit, after = await game.webStory(playerId);
            expect(grown).toMatchObject({ key, id: r.id, level: r.level, xp: r.xp, stage: choice.preview.toStage, evoAugs: [choice.id], stats: choice.preview.afterStats, skills: choice.preview.afterSkills, promo: choice.preview.afterPromo, hp: choice.preview.afterStats.hp, maxHp: choice.preview.afterStats.hp });
            expect(after.units[other]).toEqual(before.units[other]);
            expect(after.hp[other]).toEqual(before.hp[other]);
            expect(after.team).toEqual(before.team);
            expect(after.cleared).toEqual(before.cleared);
            expect(await db.player.findUniqueOrThrow({ where: { id: playerId } })).toMatchObject({ credits: wallet.credits, snack: wallet.snack, peanut: wallet.peanut });
        }
    });
    it('반복 조회는 후보·저장·체력·재화·플레이어 리비전을 변경하지 않는다', async () => {
        const key = await own(), r = record(key);
        await game.webSaveStory(playerId, { units: { [key]: savedUnit(r) }, team: [key], cleared: [firstPart], hp: { [key]: { f: .25, at: Date.now() } } });
        await get(); // 기존 튜토리얼 최초 판정 등 API 초기화 이후의 순수 조회를 검사한다.
        const before = await game.webStory(playerId), wallet = await db.player.findUniqueOrThrow({ where: { id: playerId } }), save = await db.storySave.findUniqueOrThrow({ where: { playerId } });
        const first = await get(), second = await get();
        expect(first.units.find((u) => u.key === key).evolve).toEqual(second.units.find((u) => u.key === key).evolve);
        expect(await game.webStory(playerId)).toEqual(before);
        expect(await db.storySave.findUniqueOrThrow({ where: { playerId } })).toEqual(save);
        expect(await db.player.findUniqueOrThrow({ where: { id: playerId } })).toEqual(wallet);
    });
    it('미리보기가 오래됐거나 다른 개체·임의 후보면 승진을 거부하고 저장을 보존한다', async () => {
        const key = await own(), r = record(key), fixture = (level, cleared = [firstPart]) => ({ units: { [key]: savedUnit({ ...r, level }) }, team: [key], cleared, hp: { [key]: { f: .2, at: Date.now() } } });
        await game.webSaveStory(playerId, fixture(20));
        const choice = (await get()).units.find((u) => u.key === key).evolve[0];
        const stranger = await db.player.create({ data: { identity: `native-growth-stranger-${Date.now()}` } }), foreignKey = await own('D1', stranger.id);
        for (const [save, payload] of [
            [fixture(19), { key, kind: 'evolve', augment: choice.id }],
            [fixture(20, []), { key, kind: 'evolve', augment: choice.id }],
            [fixture(20), { key: foreignKey, kind: 'evolve', augment: choice.id }],
            [fixture(20), { key, kind: 'evolve', augment: 'not-a-medal' }],
            [fixture(20), { key, kind: 'evolve', augment: choice.id, preview: choice.preview }],
        ]) {
            await game.webSaveStory(playerId, structuredClone(save));
            const before = await game.webStory(playerId);
            const response = await post(payload);
            expect(response.statusCode, response.body).toBe(400);
            expect(await game.webStory(playerId)).toEqual(before);
        }
    });
});
