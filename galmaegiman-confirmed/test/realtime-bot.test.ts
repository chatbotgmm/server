// DRAFT target: test/realtime-bot.test.ts. NOT RUN.
import {describe,it,expect} from 'vitest';
import {loadBattleRules} from '../src/battle/load.js';
import {loadContent} from '../src/content.js';
import type {Skill} from '../src/battle/core.js';
import type {UnitRecord} from '../src/battle/growth.js';
import {activeOf,createBattle,STRUGGLE,type Battle} from '../src/battle/engine.js';
import {serializeBattle,resolvePhase,validateCommand,type Command} from '../src/battle/realtime-adapter.js';
import {chooseBotCommand,completeBotAction} from '../src/battle/realtime-bot.js';
const R=loadBattleRules(),content=loadContent();
const rec=(id:string,uid:string):UnitRecord=>({id,uid,name:content.map.get(id)!.name,tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const skill=(id='basic-test',power=20):Skill=>({id,name:id,power,cat:'phys',cd:0,effects:[],desc:''});
function battle():Battle {
  const b=createBattle(R,[rec('D1','a0'),rec('D4','a1')],[rec('D1','b0'),rec('D6','b1')],17);
  b.phase='action';b.events=[];b.eventSeq=0;b.log=[];
  for(const s of b.sides){s.offers=null;s.augs=[];for(const f of s.fighters){f.skills=[skill()];f.passive={};f.hp=f.maxHp=1000;}}
  return b;
}
describe('casual bot phase-open command',()=>{
  it('uses the existing deterministic strategy on a clone without consuming RNG',()=>{
    const b=battle();activeOf(b,1).skills=[skill(),skill('strong',200)];const before=serializeBattle(b);
    const first=chooseBotCommand(b,1),second=chooseBotCommand(b,1);
    expect(first).toEqual({action:{kind:'skill',skillId:'strong'}});
    expect(first).toEqual(second);
    expect(serializeBattle(b)).toBe(before);
  });
  it('can commit a legal natural struggle with automatic=false',()=>{
    const b=battle();activeOf(b,1).skills=[];
    const command=chooseBotCommand(b,1);
    expect(command).toEqual({action:{kind:'skill',skillId:STRUGGLE.id}});
    expect(()=>resolvePhase(b,[{action:{kind:'skill',skillId:'basic-test'}},command],[false,false])).not.toThrow();
  });
  it('prepares replacement and augment together without applying either early',()=>{
    const b=battle();b.phase='choose';b.sides[1].needSwitch=true;b.sides[1].fighters[0].hp=0;b.sides[1].offers=['bulwark'];
    const before=serializeBattle(b),out=chooseBotCommand(b,1);
    expect(out).toEqual({choose:{switchTo:1,augment:'bulwark'}});
    expect(serializeBattle(b)).toBe(before);
    const next=resolvePhase(b,[null,out],[false,false]);
    expect(next.events.slice(0,2).map(e=>e.kind)).toEqual(['switch','augment']);
    expect(next.sides[1].fighters[1].guards).toHaveLength(1);
    expect(next.sides[1].fighters[0].guards).toHaveLength(0);
  });
  it('rejects when this side has no choose work',()=>{
    const b=battle();b.phase='choose';expect(()=>chooseBotCommand(b,1)).toThrow();
  });
  it('fixes the free-switch follow-up before resolution and accounts for handoff healing',()=>{
    const b=battle();b.sides[1].augs=['freeswitch'];activeOf(b,1).passive.handoff=50;
    const incoming=b.sides[1].fighters[1];incoming.hp=900;
    incoming.skills=[{id:'heal',name:'heal',cd:0,effects:[{kind:'heal',target:'self',pct:25}],desc:''},skill('incoming')];
    const before=serializeBattle(b),command=completeBotAction(b,1,{kind:'switch',to:1});
    expect(command).toEqual({action:{kind:'switch',to:1,then:'incoming'}});
    expect(()=>validateCommand(b,1,command)).not.toThrow();expect(serializeBattle(b)).toBe(before);
    expect(()=>completeBotAction(b,1,{kind:'switch',to:1,then:'heal'})).toThrow();
    expect(serializeBattle(b)).toBe(before);
  });
  it('rejects items and invalid inputs without changing source or RNG',()=>{
    const b=battle();activeOf(b,1).hp=100;const before=serializeBattle(b);
    for(const action of [{kind:'item',item:'potion'},{kind:'skill',skillId:'missing'},{kind:'switch',to:0}] as const){
      expect(()=>completeBotAction(b,1,action)).toThrow();expect(serializeBattle(b)).toBe(before);
    }
    b.phase='choose';b.sides[1].needSwitch=true;for(const f of b.sides[1].fighters)f.hp=0;
    const invalid=serializeBattle(b);expect(()=>chooseBotCommand(b,1)).toThrow();expect(serializeBattle(b)).toBe(invalid);
  });
  it('retains the precommit while either human command resolves and never recomputes it',()=>{
    const b=battle(),out=chooseBotCommand(b,1),committed=JSON.stringify(out);
    const humans:Command[]=[{action:{kind:'skill',skillId:'basic-test'}},{action:{kind:'switch',to:1}}];
    for(const human of humans){resolvePhase(b,[human,out],[false,false]);expect(JSON.stringify(out)).toBe(committed);}
    // Service tests must additionally check restart, poll, retry and human reroll
    // never call chooseBotCommand again for an already committed phase.
  });
});
