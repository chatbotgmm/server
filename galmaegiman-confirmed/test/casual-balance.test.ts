import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {loadBattleRules} from '../src/battle/load.js';
import {createBattle} from '../src/battle/engine.js';
import {serializeBattle,deserializeBattle} from '../src/battle/realtime-adapter.js';
import {casualRecord,deployedMean,compensationBoost,matchCasualBot,applyCasualCompensation} from '../src/pvp/casual-balance.js';
import {maxStageAt,type UnitRecord} from '../src/battle/growth.js';
const R=loadBattleRules();
const team=(level:number)=>['D1','T1','E1'].map(id=>casualRecord(R,{id,uid:`owned-${id}`,name:id,tier:loadContent().map.get(id)!.rarity as UnitRecord['tier']},{level},5));
describe('casual actual growth and bounded compensation',()=>{
 it.each([0,-1,NaN,Infinity,1.5,'10',null,undefined])('rejects invalid deployed level %s',level=>{expect(()=>deployedMean([{level:level as number}],60)).toThrow();});
 it('uses only deployed levels, rejects empty or oversized teams',()=>{expect(deployedMean([{level:10},{level:20},{level:30}],60)).toBe(20);expect(()=>deployedMean([],60)).toThrow();expect(()=>deployedMean(Array(4).fill({level:1}),60)).toThrow();});
 it('defaults new units to story start and clones saved growth',()=>{const saved={level:20,xp:4,stage:0 as const,evoAugs:[],learned:['@','basic']};const copy=structuredClone(saved);const rec=casualRecord(R,{id:'D1',uid:'o1',name:'D1',tier:'LIMITED'},saved,5);rec.learned!.push('role');expect(saved).toEqual(copy);expect(rec.level).toBe(20);expect(casualRecord(R,{id:'D1',uid:'o2',name:'D1',tier:'LIMITED'},undefined,5).level).toBe(5);});
 it.each([{level:0},{level:null},{level:NaN},{level:1,stage:3},{level:20,xp:NaN},{level:20,evoAugs:['unknown']},{level:20,learned:[2]}])('rejects corrupt save %j',saved=>{expect(()=>casualRecord(R,{id:'D1',uid:'o1',name:'D1',tier:'LIMITED'},saved,5)).toThrow();});
 it('uses exact formula, equal sides have zero, extreme gap caps at 15%',()=>{expect(compensationBoost(10,10)).toEqual({side:null,boost:0});expect(compensationBoost(20,10)).toEqual({side:1,boost:.25*(Math.sqrt(2)-1)});expect(compensationBoost(1,60)).toEqual({side:0,boost:.15});expect(()=>compensationBoost(0,20)).toThrow();});
 it('boosts only weaker combat HP and both attack/defense stats; preserves records, speed, skills and scale',()=>{
  const a=team(10),b=team(60),records=structuredClone([a,b]);const battle=createBattle(R,a,b,42);const before=deserializeBattle(R,serializeBattle(battle));applyCasualCompensation(battle,'normal',false);
  for(let i=0;i<3;i++){const f=battle.sides[0].fighters[i],old=before.sides[0].fighters[i];expect(f.hp).toBe(Math.floor(old.hp*1.15));expect(f.maxHp).toBe(f.hp);for(const k of ['patk','pdef','matk','mdef'] as const)expect(f.base[k]).toBe(Math.floor(old.base[k]*1.15));expect(f.base.spd).toBe(old.base.spd);expect(f.scale).toBe(old.scale);expect(f.skills).toEqual(old.skills);expect(f.rec).toEqual(old.rec);}expect(battle.sides[1]).toEqual(before.sides[1]);expect([a,b]).toEqual(records);
  expect(serializeBattle(deserializeBattle(R,serializeBattle(battle)))).toBe(serializeBattle(battle));
 });
 it.each([['rank',false],['normal',true]] as const)('does not compensate %s bot=%s', (mode,bot)=>{const b=createBattle(R,team(10),team(60),42),before=serializeBattle(b);applyCasualCompensation(b,mode,bot);expect(serializeBattle(b)).toBe(before);});
 it('AI uses rounded deployed average and rebuilds stage/innate/promotion skills without mutation',()=>{const human=team(10);human[1].level=20;human[1].stage=1;human[2].level=30;human[2].stage=1;const bot=team(60),before=structuredClone([human,bot]);const out=matchCasualBot(R,human,bot);expect(out.map(x=>x.level)).toEqual([20,20,20]);for(const rec of out){expect(rec.stage).toBe(1);expect(rec.xp).toBe(0);expect(rec.evoAugs.length).toBe(rec.stage);if(rec.stage>0)expect(rec.learned).toContain('sig');}expect([human,bot]).toEqual(before);expect(matchCasualBot(R,team(5),out).every(x=>x.level===5&&x.stage===0&&!x.learned!.includes('sig'))).toBe(true);});
 it('keeps high-level unpromoted opponents unpromoted',()=>{const human=team(60),bot=team(60),before=structuredClone(human);const out=matchCasualBot(R,human,bot);expect(out.every(r=>r.level===60&&r.stage===0&&r.evoAugs.length===0&&!r.learned!.includes('sig'))).toBe(true);expect(human).toEqual(before);});
 it('rounds mixed earned promotions and retains consistent augments and skills',()=>{const human=team(60);human[0].stage=1;human[1].stage=2;human[2].stage=3;const before=structuredClone(human);const out=matchCasualBot(R,human,team(5));expect(out.every(r=>r.stage===2&&r.evoAugs.length===2&&r.learned!.includes('sig'))).toBe(true);expect(human).toEqual(before);});
 it('caps rounded promotion at the AI average level legal threshold',()=>{const human=team(1);human[2].level=50;human[2].stage=3;const before=structuredClone(human);const out=matchCasualBot(R,human,team(60));expect(out.every(r=>r.level===17&&r.stage===maxStageAt(R,17)&&r.stage===0)).toBe(true);expect(human).toEqual(before);});
 it.each([-1,4,NaN,Infinity,1.5,undefined,null])('rejects invalid deployed promotion %s',stage=>{const human=team(60);human[0].stage=stage as UnitRecord['stage'];expect(()=>matchCasualBot(R,human,team(5))).toThrow();});
 it('rejects a deployed promotion above its own level threshold',()=>{const human=team(5);human[0].stage=1;expect(()=>matchCasualBot(R,human,team(5))).toThrow();});

});
