import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { loadBattleRules } from '../src/battle/load.js';
import { loadContent } from '../src/content.js';
import type { Skill } from '../src/battle/core.js';
import type { UnitRecord } from '../src/battle/growth.js';
import { act, activeOf, aiChoose, BATTLE_EVENT_LIMIT, choose, createBattle, pickAi, type Battle, type BattleEvent } from '../src/battle/engine.js';

const R=loadBattleRules(),content=loadContent();
const rec=(id='D1',uid='same-key'):UnitRecord=>({uid,id,name:content.map.get(id)!.name,tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
function ready(a=[rec()],c=[rec()]):Battle{
  const b=createBattle(R,a,c,7);b.phase='action';
  for(const s of b.sides){s.offers=null;for(const f of s.fighters){f.passive={};f.maxHp=f.hp=1000;f.base.spd=1;}}
  activeOf(b,0).base.spd=1000;activeOf(b,1).status.sleep=100;
  return b;
}
const move=(extra:Partial<Skill>={}):Skill=>({id:'test-skill',name:'시험 기술',power:20,cat:'phys',cd:0,effects:[],desc:'',...extra});
function cast(b:Battle,skill:Skill){activeOf(b,0).skills=[skill];act(b,[{kind:'skill',skillId:skill.id},{kind:'skill',skillId:activeOf(b,1).skills[0].id}]);}
const events=(b:Battle,kind:BattleEvent['kind'])=>b.events.filter(e=>e.kind===kind);

describe('전투 표시 이벤트',()=>{
  it('양측 이름·종류·기록번호가 같아도 다단 공격과 반사 대상을 진영으로 구분한다',()=>{
    const b=ready(),me=activeOf(b,0),foe=activeOf(b,1);foe.passive.reflect=25;b.sides[1].augs=['thorns'];
    cast(b,move({hits:3}));
    const damage=events(b,'damage'),hits=damage.filter(e=>e.reason==='skill');
    expect(hits).toHaveLength(3);
    for(const e of hits){expect(e.actor).toEqual({side:0,slot:0,key:'same-key',id:'D1'});expect(e.target).toEqual({side:1,slot:0,key:'same-key',id:'D1'});expect(e.amount).toBe(e.hpBefore!-e.hpAfter!);}
    expect(hits.map(e=>e.hit)).toEqual([1,2,3]);
    expect(hits[1].hpBefore).toBe(hits[0].hpAfter);expect(hits[2].hpBefore).toBe(hits[1].hpAfter);
    expect(new Set(hits.map(e=>e.logIndex)).size).toBe(1);
    for(const e of damage.filter(e=>e.reason!=='skill')){expect(e.actor?.side).toBe(1);expect(e.target?.side).toBe(0);expect(e.amount).toBe(e.hpBefore!-e.hpAfter!);}
    expect(damage.map(e=>e.reason)).toEqual(['skill','skill','skill','thorns','reflect']);
    expect(me.hp).toBe(damage.at(-1)!.hpAfter);expect(foe.hp).toBe(hits.at(-1)!.hpAfter);
  });

  it('과잉 피해는 실제 남은 체력까지만 표시하며 버티기·방어에도 음수 체력이 생기지 않는다',()=>{
    const b=ready(),foe=activeOf(b,1);foe.hp=2;cast(b,move({power:100,hits:3}));
    const hit=events(b,'damage')[0];expect(hit).toMatchObject({amount:2,hpBefore:2,hpAfter:0,maxHp:1000,hit:1});
    expect(events(b,'damage')).toHaveLength(1);expect(events(b,'faint')[0].target?.side).toBe(1);
    const endured=ready();activeOf(endured,1).hp=1;activeOf(endured,1).endure=2;cast(endured,move({hits:2}));
    expect(events(endured,'damage').map(e=>e.amount)).toEqual([0,0]);
    const protectedBattle=ready();activeOf(protectedBattle,1).protect=true;cast(protectedBattle,move());
    expect(events(protectedBattle,'damage')[0]).toMatchObject({amount:0,hpBefore:1000,hpAfter:1000,reason:'protect'});
    const lastStand=ready();activeOf(lastStand,1).hp=2;activeOf(lastStand,1).passive.lastStand=true;cast(lastStand,move());
    expect(events(lastStand,'damage')[0]).toMatchObject({amount:1,hpBefore:2,hpAfter:1});
    expect(lastStand.log.some(l=>l.includes('불굴의 의지'))).toBe(true);
  });

  it('역습은 원래 공격의 반대 진영·속성·배율을 기록한다',()=>{
    const b=ready([rec('D4','me')],[rec('D6','foe')]);b.sides[1].augs=['counter'];
    cast(b,move({attr:'SEA'}));
    const counter=events(b,'damage').find(e=>e.reason==='counter')!;
    expect(counter).toMatchObject({actor:{side:1,key:'foe'},target:{side:0,key:'me'},skillId:'counter',skillName:'역습',attr:'STAR',multiplier:1.5});
  });

  it('기술·흡혈·증강체·재생·아이템 회복을 회복 상한과 실제 대상 편성 칸으로 기록한다',()=>{
    const b=ready([rec('D1','lead'),rec('D1','bench')]);
    const me=activeOf(b,0),bench=b.sides[0].fighters[1];me.hp=999;me.passive.lifesteal=100;bench.hp=990;
    cast(b,move({effects:[{kind:'heal',pct:30,target:'bench'}]}));
    expect(events(b,'heal')).toEqual(expect.arrayContaining([
      expect.objectContaining({reason:'lifesteal',amount:1,target:expect.objectContaining({side:0,slot:0,key:'lead'})}),
      expect.objectContaining({reason:'skill',amount:10,target:expect.objectContaining({side:0,slot:1,key:'bench'})})
    ]));
    me.passive={};me.hp=999;me.regens=[{pct:30,turns:2}];cast(b,move());
    expect(events(b,'heal').at(-1)).toMatchObject({reason:'regen',amount:1,hpAfter:1000});
    me.passive={};me.regens=[];me.hp=999;
    act(b,[{kind:'item',item:'potion'},{kind:'skill',skillId:activeOf(b,1).skills[0].id}]);
    expect(events(b,'heal').at(-1)).toMatchObject({reason:'potion',amount:1,hpBefore:999,hpAfter:1000});
    const aid=ready([rec('D1','lead'),rec('D1','bench')]);aid.phase='choose';aid.sides[0].offers=['firstaid'];
    activeOf(aid,0).hp=999;aid.sides[0].fighters[1].hp=990;choose(aid,0,{augment:'firstaid'});
    expect(events(aid,'heal').map(e=>[e.target?.slot,e.amount,e.reason])).toEqual([[0,1,'firstaid'],[1,10,'firstaid']]);
    const drain=ready();activeOf(drain,0).hp=999;drain.sides[0].augs=['drain'];cast(drain,move());
    expect(events(drain,'heal')[0]).toMatchObject({amount:1,reason:'drain'});
  });

  it('교체 인계·강제 교체·대기 유닛 부활은 같은 이름이어도 실제 편성 칸을 가리킨다',()=>{
    const b=ready([rec('D1','lead'),rec('D1','bench')]);const me=activeOf(b,0),bench=b.sides[0].fighters[1];
    me.passive.handoff=30;bench.hp=990;
    act(b,[{kind:'switch',to:1},{kind:'skill',skillId:activeOf(b,1).skills[0].id}]);
    expect(events(b,'heal')[0]).toMatchObject({reason:'handoff',amount:10,actor:{slot:0,key:'lead'},target:{slot:1,key:'bench'}});
    expect(events(b,'switch')[0]).toMatchObject({reason:'action',actor:{side:0,slot:0,key:'lead'},target:{side:0,slot:1,key:'bench'}});
    b.sides[0].fighters[0].hp=0;
    act(b,[{kind:'item',item:'feather',target:0},{kind:'skill',skillId:activeOf(b,1).skills[0].id}]);
    expect(events(b,'revive')[0]).toMatchObject({actor:{slot:1,key:'bench'},target:{slot:0,key:'lead'},hpBefore:0,hpAfter:500,amount:500});
    const forced=ready([rec('D1','dead'),rec('D1','living')]);activeOf(forced,0).hp=0;forced.phase='choose';forced.sides[0].needSwitch=true;
    choose(forced,0,{switchTo:1});expect(events(forced,'switch')[0]).toMatchObject({reason:'faint',actor:{slot:0,key:'dead'},target:{slot:1,key:'living'}});
    const reviveSkill=ready([rec('D1','lead'),rec('D1','bench')]);reviveSkill.sides[0].fighters[1].hp=0;
    cast(reviveSkill,move({power:undefined,effects:[{kind:'revive',pct:30}]}));
    expect(events(reviveSkill,'revive')[0]).toMatchObject({reason:'skill',target:{slot:1,key:'bench'},amount:300,hpAfter:300});
  });

  it('화상·상태 적용/해제는 대상과 독립 복사된 중간 상태를 기록한다',()=>{
    const b=ready(),me=activeOf(b,0),foe=activeOf(b,1);me.status.burn=1;
    cast(b,move({effects:[{kind:'status',status:'bind',chance:100,turns:2}]}));
    const applied=events(b,'status').find(e=>e.reason==='skill')!;
    expect(applied).toMatchObject({actor:{side:0},target:{side:1},status:{sleep:100,bind:2},attr:foe.attr,extraAttr:null});
    expect(events(b,'damage').find(e=>e.reason==='burn')).toMatchObject({target:{side:0},reason:'burn'});
    expect(applied.status?.bind).toBe(2);expect(foe.status.bind).toBe(1);
    expect(events(b,'status').find(e=>e.reason==='expire'&&e.target?.side===0)?.status).toEqual({});
  });

  it('최근 이벤트만 보관해도 번호는 단조 증가하고 이전 저장본도 이어 쓸 수 있다',()=>{
    const b=ready();for(const s of b.sides)for(const f of s.fighters)f.hp=f.maxHp=100000;activeOf(b,1).status.sleep=1000;
    for(let i=0;i<100;i++)cast(b,move());
    expect(b.events).toHaveLength(BATTLE_EVENT_LIMIT);expect(b.events[0].id).toBeGreaterThan(1);
    expect(new Set(b.events.map(e=>e.id)).size).toBe(BATTLE_EVENT_LIMIT);
    expect(b.events.at(-1)!.id).toBe(b.eventSeq);for(let i=1;i<b.events.length;i++)expect(b.events[i].id).toBe(b.events[i-1].id+1);
    for(const e of b.events){expect(e.logIndex).toBeGreaterThanOrEqual(0);expect(e.logIndex).toBeLessThan(b.log.length);}
    const legacy=ready();delete (legacy as Partial<Battle>).events;delete (legacy as Partial<Battle>).eventSeq;cast(legacy,move());
    expect(legacy.events[0].id).toBe(1);expect(legacy.eventSeq).toBe(legacy.events.at(-1)!.id);
  });

  it('기존 전투를 이벤트만 순서대로 재생하면 양측 HP·출전·상태·속성이 매 단계 최종 상태와 같다',()=>{
    for(const seed of [1,7,42,99]){
      const mk=(id:string,i:number)=>({...rec(id,`u${i}`),tier:content.map.get(id)!.rarity as UnitRecord['tier']});
      const b=createBattle(R,['D6','T3','I4'].map(mk),['D4','E2','T7'].map(mk),seed);
      const update=(run:()=>void)=>{
        const seq=b.eventSeq,slots=b.sides.map(s=>s.fighters.map(f=>({hp:f.hp,status:{...f.status},attr:f.attr,extraAttr:f.extraAttr??null}))),active=b.sides.map(s=>s.active);
        run();
        for(const e of b.events.filter(e=>e.id>seq)){
          for(const ref of [e.actor,e.target])if(ref){const f=b.sides[ref.side].fighters[ref.slot];expect(ref).toEqual({side:ref.side,slot:ref.slot,key:f.rec.uid,id:f.rec.id});}
          if(['damage','heal','revive'].includes(e.kind)){
            const ref=e.target!,f=slots[ref.side][ref.slot];expect(e.hpBefore).toBe(f.hp);expect(e.amount).toBe(e.kind==='damage'?e.hpBefore!-e.hpAfter!:e.hpAfter!-e.hpBefore!);f.hp=e.hpAfter!;
          }
          if(e.kind==='switch'){expect(e.actor!.slot).toBe(active[e.actor!.side]);active[e.target!.side]=e.target!.slot;}
          if(e.kind==='status'){const ref=e.target!,f=slots[ref.side][ref.slot];f.status={...e.status};f.attr=e.attr!;f.extraAttr=e.extraAttr??null;}
        }
        expect(active).toEqual(b.sides.map(s=>s.active));
        expect(slots).toEqual(b.sides.map(s=>s.fighters.map(f=>({hp:f.hp,status:f.status,attr:f.attr,extraAttr:f.extraAttr??null}))));
      };
      for(let i=0;i<400&&b.phase!=='over';i++)if(b.phase==='choose'){update(()=>aiChoose(b,0));update(()=>aiChoose(b,1));}else update(()=>act(b,[pickAi(b,0),pickAi(b,1)]));
      expect(b.phase).toBe('over');
    }
  });

  it('속성 연계·조합 위력 적용 후 고정 시드 7개의 로그·난수·전투 상태를 보존한다',()=>{
    const baseline=[];
    for(const seed of [1,2,3,7,17,42,99]){
      const mk=(id:string,i:number)=>({...rec(id,`u${i}`),tier:content.map.get(id)!.rarity as UnitRecord['tier']});
      const b=createBattle(R,['D6','T3','I4'].map(mk),['D4','E2','T7'].map(mk),seed);
      for(let i=0;i<400&&b.phase!=='over';i++)if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);}else act(b,[pickAi(b,0),pickAi(b,1)]);
      baseline.push({rng:b.rng,turn:b.turn,phase:b.phase,winner:b.winner,sides:b.sides,log:b.log});
    }
    expect(createHash('sha256').update(JSON.stringify(baseline)).digest('hex')).toBe('5599e2f51741f8273bbc5cfe866cc0efb407cfc33132f695bf225ab1c618a7ca');
  });
});
