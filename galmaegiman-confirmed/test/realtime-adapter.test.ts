// Manual draft: place at test/realtime-adapter.test.ts.
import {describe,it,expect} from 'vitest';
import {loadBattleRules} from '../src/battle/load.js';
import {loadContent} from '../src/content.js';
import {attrKeys,type Skill} from '../src/battle/core.js';
import type {UnitRecord} from '../src/battle/growth.js';
import {activeOf,createBattle,STRUGGLE,type Battle} from '../src/battle/engine.js';
import {automaticCommand,consumedItemSides,deserializeBattle,itemRequirement,required,rerollCommand,resolvePhase,serializeBattle,validateCommand,type Command} from '../src/battle/realtime-adapter.js';
const R=loadBattleRules(),content=loadContent();
const rec=(id:string,uid:string):UnitRecord=>({id,uid,name:content.map.get(id)!.name,tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const attack=(id='basic-test'):Skill=>({id,name:id,power:20,cat:'phys',cd:0,effects:[],desc:''});
function battle():Battle{
  const b=createBattle(R,[rec('D1','a0'),rec('D4','a1')],[rec('D1','b0'),rec('D6','b1')],17);
  b.phase='action';b.events=[];b.eventSeq=0;b.log=[];
  for(const s of b.sides){s.offers=null;s.augs=[];for(const f of s.fighters){f.skills=[attack()];f.passive={};f.maxHp=f.hp=1000;f.base.spd=10;}}
  return b;
}
const hit:Command={action:{kind:'skill',skillId:'basic-test'}};
function faintBoth(b:Battle){b.phase='choose';for(const s of b.sides){s.fighters[0].hp=0;s.needSwitch=true;}}
describe('ranked realtime engine adapter',()=>{
  it('validates both inputs before advancing and preserves the complete source on rejection',()=>{
    const b=battle(),before=serializeBattle(b);
    expect(()=>resolvePhase(b,[hit,{action:{kind:'skill',skillId:'missing'}}],[false,false])).toThrow();
    expect(serializeBattle(b)).toBe(before);
    const next=resolvePhase(b,[hit,hit],[false,false]);expect(next.turn).toBe(2);
    expect(serializeBattle(b)).toBe(before);expect(next.R).toBe(R);
  });
  it('replaces both fainted fighters before camo or bulwark targets are resolved',()=>{
    const b=battle();faintBoth(b);b.sides[0].offers=['camo'];b.sides[1].offers=['bulwark'];
    const expected=attrKeys.filter(a=>R.data.chart[a].strong.includes(b.sides[1].fighters[1].attr))
      .sort((a,z)=>R.typeMult(b.sides[1].fighters[1].attr,a)-R.typeMult(b.sides[1].fighters[1].attr,z))[0];
    const next=resolvePhase(b,[{choose:{augment:'camo',switchTo:1}},{choose:{augment:'bulwark',switchTo:1}}],[false,false]);
    expect(next.events.slice(0,2).map(e=>e.kind)).toEqual(['switch','switch']);
    expect(next.events.filter(e=>e.kind==='augment').every(e=>e.actor?.slot===1)).toBe(true);
    expect(activeOf(next,0).attr).toBe(expected);expect(activeOf(next,1).guards).toHaveLength(1);
    expect(next.sides[1].fighters[0].guards).toHaveLength(0);expect(next.phase).toBe('action');
  });
  it('applies opponent-targeting rustle to the incoming fighter',()=>{
    const b=battle();faintBoth(b);b.sides[0].offers=['rustle'];b.sides[1].offers=['dual'];
    const next=resolvePhase(b,[{choose:{augment:'rustle',switchTo:1}},{choose:{augment:'dual',switchTo:1}}],[false,false]);
    expect(activeOf(next,1).status.sleep).toBe(1);expect(next.sides[1].fighters[0].status.sleep).toBeUndefined();
    expect(next.sides[1].fighters[0].extraAttr).toBeUndefined();expect(activeOf(next,1).extraAttr).toBeDefined();
  });
  it('rejects invalid second mandatory replacement without applying the first choice',()=>{
    const b=battle();faintBoth(b);b.sides[0].offers=['bulwark'];b.sides[1].offers=['camo'];const before=serializeBattle(b);
    expect(()=>resolvePhase(b,[{choose:{augment:'bulwark',switchTo:1}},{choose:{augment:'camo',switchTo:0}}],[false,false])).toThrow();
    expect(serializeBattle(b)).toBe(before);
  });
  it('keeps a phase-start legal utility legal if the opponent switches before execution',()=>{
    const b=battle();activeOf(b,0).mods=[{stat:'atk',pct:20,turns:3}];
    activeOf(b,1).skills=[{id:'strip-test',name:'strip',cd:0,effects:[{kind:'strip'}],desc:''}];
    const c:Command={action:{kind:'skill',skillId:'strip-test'}};
    expect(()=>validateCommand(b,1,c)).not.toThrow();
    expect(()=>resolvePhase(b,[{action:{kind:'switch',to:1}},c],[false,false])).not.toThrow();
  });
  it('rejects unknown items, mixed phases, unknown keys and noninteger slots',()=>{
    const b=battle();
    for(const c of [{action:{kind:'item',item:'anything'}},{action:hit.action,choose:{}},{choose:{}},
      {action:{kind:'switch',to:1.2}},{action:{kind:'skill',skillId:'basic-test',automatic:true}}]){
      expect(()=>validateCommand(b,0,c as Command)).toThrow();
    }
  });
  it('requires an explicit legal free-switch follow-up on the incoming fighter',()=>{
    const b=battle();b.sides[0].augs=['freeswitch'];b.sides[0].fighters[1].skills=[attack('incoming-only')];
    expect(()=>validateCommand(b,0,{action:{kind:'switch',to:1}})).toThrow();
    expect(()=>validateCommand(b,0,{action:{kind:'switch',to:1,then:'basic-test'}})).toThrow();
    expect(()=>validateCommand(b,0,{action:{kind:'switch',to:1,then:'incoming-only'}})).not.toThrow();
  });
  it('validates a free-switch healing follow-up after the handoff heal',()=>{
    const b=battle();b.sides[0].augs=['freeswitch'];activeOf(b,0).passive.handoff=50;
    const incoming=b.sides[0].fighters[1];incoming.hp=900;
    incoming.skills=[{id:'heal-test',name:'heal',cd:0,effects:[{kind:'heal',target:'self',pct:25}],desc:''},attack()];
    expect(()=>validateCommand(b,0,{action:{kind:'switch',to:1,then:'heal-test'}})).toThrow();
  });
  it('uses a damaging basic skill for timeout, else server-only struggle without selecting AI skills',()=>{
    const b=battle();expect(automaticCommand(b,0)).toEqual(hit);
    activeOf(b,0).skills=[attack('strong-custom')];const before=serializeBattle(b);
    expect(automaticCommand(b,0)).toEqual({action:{kind:'skill',skillId:STRUGGLE.id}});
    expect(()=>validateCommand(b,0,{action:{kind:'skill',skillId:STRUGGLE.id}})).toThrow();
    const next=resolvePhase(b,[null,hit],[true,false]);
    expect(next.events.some(e=>e.kind==='skill'&&e.actor?.side===0&&e.skillId===STRUGGLE.id)).toBe(true);
    expect(serializeBattle(b)).toBe(before);
  });
  it('timeout chooses the first offer and lowest living replacement without mutating RNG',()=>{
    const b=battle();faintBoth(b);b.sides[0].offers=['bulwark','camo'];const before=serializeBattle(b);
    expect(automaticCommand(b,0)).toEqual({choose:{augment:'bulwark',switchTo:1}});expect(serializeBattle(b)).toBe(before);
  });
  it('allows null for a side with no choose work and advances without an empty-phase deadlock',()=>{
    const b=battle();b.phase='choose';expect(required(b,0)).toBe(false);
    expect(resolvePhase(b,[null,null],[false,false]).phase).toBe('action');
    expect(()=>resolvePhase(b,[null,null],[true,false])).toThrow();
  });
  it('rerolls only on a clone and rejects invalid indices',()=>{
    const b=createBattle(R,[rec('D1','a')],[rec('D4','b')],17),before=serializeBattle(b);
    const next=rerollCommand(b,0,0);expect(next.sides[0].rerolled[0]).toBe(true);expect(serializeBattle(b)).toBe(before);
    expect(()=>rerollCommand(next,0,0)).toThrow();expect(()=>rerollCommand(b,0,0.5)).toThrow();
  });
  it('preserves event ids and lifesteal order through resolution and serialization',()=>{
    const b=battle();activeOf(b,0).base.spd=1000;activeOf(b,0).hp=400;activeOf(b,0).passive.lifesteal=50;
    const next=resolvePhase(b,[hit,hit],[false,false]);
    const healing=next.events.findIndex(e=>e.kind==='heal'&&e.actor?.side===0);
    const opponent=next.events.findIndex(e=>e.kind==='skill'&&e.actor?.side===1);
    expect(healing).toBeGreaterThan(-1);expect(opponent).toBeGreaterThan(healing);
    expect(next.events.map(e=>e.id)).toEqual([...next.events.map(e=>e.id)].sort((a,z)=>a-z));
    expect(serializeBattle(deserializeBattle(R,serializeBattle(next)))).toBe(serializeBattle(next));
  });
  it('heals before the opponent action and spends the item user entire turn',()=>{
    const b=battle();activeOf(b,0).hp=200;
    const c:Command={action:{kind:'item',item:'potion'}};
    expect(itemRequirement(b,0,c)).toEqual({itemId:'potion'});
    const next=resolvePhase(b,[c,hit],[false,false]);
    expect(consumedItemSides(b,next)).toEqual([0]);expect(next.turn).toBe(2);
    expect(next.events.filter(e=>e.kind==='skill').map(e=>e.actor?.side)).toEqual([1]);
    const heal=next.events.find(e=>e.kind==='heal')!;
    expect(heal).toMatchObject({hpBefore:200,hpAfter:550,amount:350});
    expect(next.events.indexOf(heal)).toBeLessThan(next.events.findIndex(e=>e.kind==='skill'));
    expect(b.sides[0].itemsUsed).toBe(0);expect(activeOf(b,0).hp).toBe(200);
  });
  it('both item users resolve in existing side order without extra skill actions',()=>{
    const b=battle();activeOf(b,0).hp=500;activeOf(b,1).hp=500;
    const next=resolvePhase(b,[{action:{kind:'item',item:'potion'}},{action:{kind:'item',item:'big'}}],[false,false]);
    expect(next.events.filter(e=>e.kind==='item').map(e=>e.actor?.side)).toEqual([0,1]);
    expect(next.events.filter(e=>e.kind==='skill')).toEqual([]);expect(consumedItemSides(b,next)).toEqual([0,1]);
    expect(activeOf(next,0).hp).toBe(850);expect(activeOf(next,1).hp).toBe(1000);expect(next.turn).toBe(2);
  });
  it('revive defaults to first dead ally and explicit target must be a dead own integer slot',()=>{
    const b=battle();b.sides[0].fighters[1].hp=0;b.sides[0].fighters[1].status.burn=3;
    const c:Command={action:{kind:'item',item:'feather'}};
    expect(itemRequirement(b,0,c)).toEqual({itemId:'feather',target:1});
    const next=resolvePhase(b,[c,hit],[false,false]);
    expect(next.sides[0].fighters[1].hp).toBe(500);expect(next.sides[0].fighters[1].status).toEqual({});
    expect(next.sides[0].reviveUsed).toBe(false); // Preserve item-vs-skill revive distinction.
    for(const target of [-1,0,2,0.5,NaN])expect(()=>validateCommand(b,0,{action:{kind:'item',item:'feather',target}})).toThrow();
  });
  it('rejects full-HP healing, empty cleanse, battle limit and ignored nonrevive targets',()=>{
    const b=battle();
    for(const item of ['potion','big','clear','feather','__proto__','constructor'])expect(()=>validateCommand(b,0,{action:{kind:'item',item}})).toThrow();
    activeOf(b,0).hp=100;
    expect(()=>validateCommand(b,0,{action:{kind:'item',item:'potion',target:1}})).toThrow();
    b.R={...b.R,data:{...b.R.data,itemRules:{...b.R.data.itemRules,perBattle:3}}};b.sides[0].itemsUsed=3;
    expect(()=>validateCommand(b,0,{action:{kind:'item',item:'potion'}})).toThrow();
  });
  it('cleanse remains usable at full HP and while frozen, with no bonus attack',()=>{
    const b=battle();activeOf(b,0).status.freeze=2;
    const next=resolvePhase(b,[{action:{kind:'item',item:'clear'}},hit],[false,false]);
    expect(activeOf(next,0).status.freeze).toBeUndefined();expect(next.sides[0].itemsUsed).toBe(1);
    expect(next.events.filter(e=>e.kind==='skill'&&e.actor?.side===0)).toEqual([]);
  });
  it('invalid opponent command rolls back all item HP/events/counter changes',()=>{
    const b=battle();activeOf(b,0).hp=100;const before=serializeBattle(b);
    expect(()=>resolvePhase(b,[{action:{kind:'item',item:'potion'}},{action:{kind:'item',item:'feather',target:0}}],[false,false])).toThrow();
    expect(serializeBattle(b)).toBe(before);
  });
});
