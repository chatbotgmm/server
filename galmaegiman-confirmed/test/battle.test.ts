import { describe, expect, it } from 'vitest';
import { loadContent } from '../src/content.js';
import { attrKeys as attrs, buildRules, type TopTier } from '../src/battle/rules.js';
import { loadBattleRules } from '../src/battle/load.js';
import { parse } from 'yaml';
import { readFileSync } from 'node:fs';
import { addXp, battleXp, canEvolve, learnChances, learnOffers, learnSkill, maxStageAt, statsOf, xpToNext, type UnitRecord } from '../src/battle/growth.js';
import { act, activeOf, aiChoose, choose, createBattle, itemProblem, legalActions, pending, pickAi, type Battle } from '../src/battle/engine.js';
import { checkStory, loadStory } from '../src/battle/story.js';
import { expectedAttempts, levelRange, referenceLevel, scoutChance, scoutCopy } from '../src/battle/scout.js';

const R=loadBattleRules();
const unitDefs=[...R.units.values()],unitDefMap=R.units,typeMult=R.typeMult,scoutRules=R.data.scout;
const strongAgainst=Object.fromEntries(attrs.map(a=>[a,R.data.chart[a].strong])) as Record<string,string[]>;

const content=loadContent();
const top=content.characters.filter(c=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity));
const rec=(id:string,level=30,extra:Partial<UnitRecord>={}):UnitRecord=>({uid:`${id}-${level}`,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as TopTier,level,xp:0,stage:0,evoAugs:[],...extra});
function play(b:Battle,limit=400){
  for(let i=0;i<limit&&b.phase!=='over';i++){if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);continue;}act(b,[pickAi(b,0),pickAi(b,1)]);}
  return b;
}

describe('수비대 대결 데이터', ()=>{
  it('대결 아이템: 턴을 쓰고 기술보다 먼저, 회복·상태 풀기·다시 일으키기, 대결당 개수 제한', ()=>{
    const b=createBattle(R,[rec('D6',30),rec('T3',30)],[rec('D4',1)],3);   // 상대는 Lv1이라 몇 턴 맞아도 안 쓰러짐
    aiChoose(b,0);aiChoose(b,1);
    const me=activeOf(b,0),per=R.data.itemRules.perBattle;
    expect(itemProblem(b,0,'potion')).toBe('체력이 가득해요.');
    expect(itemProblem(b,0,'clear')).toBe('풀 상태 이상이 없어요.');
    expect(itemProblem(b,0,'feather')).toBe('다시 일으킬 갈매미가 없어요.');
    expect(itemProblem(b,0,'nope')).toBe('없는 아이템');
    me.hp=Math.round(me.maxHp*0.2);
    const turn=b.turn,from=b.log.length;
    act(b,[{kind:'item',item:'potion'},pickAi(b,1)]);
    const log=b.log.slice(from);
    expect(log[0]).toContain('아이템 「치료약」');expect(log[1]).toMatch(/체력이 \d+ 회복됐어요/);   // 상대 기술보다 먼저
    expect(b.turn).toBe(turn+1);expect(b.phase).toBe('action');
    me.status={sleep:2};
    act(b,[{kind:'item',item:'clear'},pickAi(b,1)]);
    expect(b.log.some(l=>l.includes('정신을 차렸어요'))).toBe(true);
    b.sides[0].fighters[1].hp=0;
    expect(itemProblem(b,0,'feather',0)).toBe('다시 일으킬 갈매미가 없어요.');
    act(b,[{kind:'item',item:'feather',target:1},pickAi(b,1)]);
    expect(b.sides[0].fighters[1].hp).toBe(Math.round(b.sides[0].fighters[1].maxHp*R.data.items.feather.revive!/100));
    expect(b.sides[0].itemsUsed).toBe(3);expect(per).toBe(3);
    me.hp=1;
    expect(itemProblem(b,0,'potion')).toBe('아이템은 대결 한 번에 3개까지예요.');
    expect(()=>act(b,[{kind:'item',item:'potion'},pickAi(b,1)])).toThrow();
  });
  it('10속성: 모두 강점 2·약점 2, 배율 1.5 / 0.67', ()=>{
    for(const a of attrs){
      expect(strongAgainst[a]).toHaveLength(2);
      expect(attrs.filter(x=>strongAgainst[x].includes(a))).toHaveLength(2);
    }
    expect(typeMult('STAR','SEA')).toBe(1.5);expect(typeMult('SEA','STAR')).toBe(0.67);expect(typeMult('STONE','STAR')).toBe(1);
  });
  it('최상위 33종 모두 전투 정보가 있고, 역할은 공격 10·수비 11·유틸 12', ()=>{
    expect(unitDefs).toHaveLength(33);
    expect(new Set(unitDefs.map(u=>u.id))).toEqual(new Set(top.map(c=>c.id)));
    const n=(r:string)=>unitDefs.filter(u=>u.role===r).length;
    expect([n('ATTACK'),n('DEFENSE'),n('UTILITY')]).toEqual([10,11,12]);
  });
});

describe('성장', ()=>{
  it('경험치로 레벨이 오르고, 진화는 레벨과 스토리 조건을 둘 다 봄', ()=>{
    const r=rec('D1',1);addXp(R,r,xpToNext(R,1)+xpToNext(R,2));expect(r.level).toBe(3);
    const r20=rec('D1',20);expect(canEvolve(R,r20,0)).toBe(false);expect(canEvolve(R,r20,1)).toBe(true);
    expect(canEvolve(R,rec('D1',34,{stage:1}),2)).toBe(false);
    expect(battleXp(R,10,true)).toBe(600);expect(battleXp(R,10,false)).toBe(200);
    let total=0;for(let l=1;l<20;l++)total+=xpToNext(R,l);expect(total).toBe(13427);
  });
  it('레벨·진화가 오르면 능력치가 커짐', ()=>{
    expect(statsOf(R,rec('T4',30)).hp).toBeGreaterThan(statsOf(R,rec('T4',10)).hp);
    expect(statsOf(R,rec('T4',30,{stage:1})).atk).toBeGreaterThan(statsOf(R,rec('T4',30)).atk);
  });
});

describe('전투 엔진', ()=>{
  it('같은 시드면 같은 결과', ()=>{
    const mk=()=>play(createBattle(R,[rec('D6'),rec('T3'),rec('I4')],[rec('D4'),rec('E2'),rec('T7')],42));
    const a=mk(),b=mk();expect(a.log).toEqual(b.log);expect(a.winner).toEqual(b.winner);
    expect(a.phase).toBe('over');
  });
  it('전투 시작 때와 전투불가 때 양쪽 모두 증강체를 고름', ()=>{
    const b=createBattle(R,[rec('D6',60)],[rec('D4',1),rec('E2',1)],7);
    expect(pending(b,0).augments).toHaveLength(3);expect(pending(b,1).augments).toHaveLength(3);
    aiChoose(b,0);aiChoose(b,1);expect(b.phase).toBe('action');
    // 레벨 60 별(바다에 강함)이 레벨 1 바다를 한 방에
    for(let i=0;i<5&&activeOf(b,1).hp>0;i++){act(b,[{kind:'skill',skillId:'basic-STAR'},pickAi(b,1)]);if(b.phase==='choose')break;}
    expect(b.phase).toBe('choose');
    expect(pending(b,0).augments).toHaveLength(3);expect(pending(b,1).augments).toHaveLength(3);expect(pending(b,1).needSwitch).toBe(true);
    expect(()=>choose(b,1,{augment:pending(b,1).augments![0]})).toThrow();   // 교체도 골라야 함
  });
  it('상성: 별 → 바다가 바다 → 별보다 아픔, 역상성 증강체는 뒤집음', ()=>{
    const hit=(a:string,d:string,aug?:string)=>{
      const b=createBattle(R,[rec(a)],[rec(d)],1);
      b.sides[0].offers=null;b.sides[1].offers=null;b.phase='action';if(aug)b.sides[0].augs.push(aug);
      const before=activeOf(b,1).hp;act(b,[{kind:'skill',skillId:`basic-${unitDefMap.get(a)!.attr}`},{kind:'skill',skillId:`basic-${unitDefMap.get(d)!.attr}`}]);
      return (before-activeOf(b,1).hp)/activeOf(b,1).maxHp;
    };
    expect(hit('D6','D4')).toBeGreaterThan(hit('D6','T1'));            // 별→바다(강) > 별→돌(보통)
    expect(hit('D6','D4','reverse')).toBeLessThan(hit('D6','D4'));     // 역상성
  });
  it('땅콩 폭주는 이름에 갈매미맨이 붙은 갈매미에게만', ()=>{
    const b=createBattle(R,[rec('D7')],[rec('D1')],3);   // 갈크탑 vs 태초의 갈매미맨
    expect(activeOf(b,0).peanut).toBe(false);expect(activeOf(b,1).peanut).toBe(true);
  });
});

describe('외근(스카우트)', ()=>{
  it('기준 레벨은 높은 3마리 평균, 만나는 범위는 ±3', ()=>{
    expect(referenceLevel([rec('D1',40),rec('D2',30),rec('D3',20),rec('D4',1)])).toBe(30);
    expect(levelRange(R,2)).toEqual([1,5]);expect(levelRange(R,59)).toEqual([56,60]);
    expect(maxStageAt(R,19)).toBe(0);expect(maxStageAt(R,35)).toBe(2);expect(maxStageAt(R,60)).toBe(3);
  });
  it('성공률: 등급·진화·명함 반영, 최대 90%, 천장에서 100%', ()=>{
    expect(scoutChance(R,'LIMITED',0,0,0)).toBe(35);
    expect(scoutChance(R,'IMMORTAL',2,3,0)).toBeCloseTo(6*0.7*2.5);
    expect(scoutChance(R,'LIMITED',0,3,0)).toBe(87.5);expect(scoutChance(R,'LIMITED',0,9,0)).toBe(87.5);
    expect(scoutChance(R,'IMMORTAL',0,0,scoutRules.pity.IMMORTAL-1)).toBe(100);
    expect(expectedAttempts(100,4)).toBe(1);
    expect(expectedAttempts(6,16)).toBeCloseTo((1-0.94**16)/0.06);
  });
  it('스카우트하면 레벨·진화·증강체까지 그대로, 새 번호만 다름', ()=>{
    const src=rec('E7',42,{xp:123,stage:2,evoAugs:['pierce','atk']});
    const copy=scoutCopy(src,'new-1');
    expect(copy).toEqual({...src,uid:'new-1'});expect(copy.evoAugs).not.toBe(src.evoAugs);
  });
});

describe('data/battle.yaml 편집', ()=>{
  const raw=()=>parse(readFileSync('data/battle.yaml','utf8'));
  it('기술 설명은 숫자로 자동 생성되고, 숫자를 바꾸면 설명도 바뀜', ()=>{
    expect(R.units.get('D6')!.signature.desc).toBe('위력 150');
    const d=raw();d.units.D6.skill.power=170;
    expect(buildRules(d).units.get('D6')!.signature.desc).toBe('위력 170');
    expect(R.units.get('D8')!.signature.desc).toBe('위력 75, 상대 묶임(3턴)');
    expect(R.roleSkill('ATTACK','SEA').name).toBe('바다 강타');
    expect(R.battleAug.get('focus')!.desc).toContain('×1.9');
  });
  it('잘못 쓰면 검사에서 걸림', ()=>{
    const bad1=raw();bad1.units.D1.attr='WATER';expect(()=>buildRules(bad1)).toThrow();
    const bad2=raw();bad2.chart.SEA.strong=['FIRE','ICE'];expect(()=>buildRules(bad2)).toThrow(/상성 오류/);
    const bad3=raw();bad3.units.D1.skill.effects=[{guard:45}];expect(()=>buildRules(bad3)).toThrow();
  });
});

describe('스토리 모드(data/story.yaml)',()=>{
  it('스토리 파일 검사: 빌런 번호·스테이지 번호, 보스는 같은 레벨 졸병보다 체력이 큼',()=>{
    const story=loadStory(process.cwd(),R);
    expect(story.chapters.map(c=>c.id)).toEqual(['P','1','2','3']);
    const v=(id:string,level:number):UnitRecord=>({uid:id,id,name:R.villains.get(id)!.name!,tier:'LIMITED',level,xp:0,stage:0,evoAugs:[]});
    expect(statsOf(R,v('V6',13)).hp).toBeGreaterThan(statsOf(R,v('V3',13)).hp*2);
    const raw=()=>parse(readFileSync('data/story.yaml','utf8'));
    const bad=raw();bad.chapters[0].stages[0].enemy[0].id='V99';expect(()=>checkStory(bad,R)).toThrow(/V99/);
    const dup=raw();dup.chapters[1].stages[1].id=dup.chapters[1].stages[0].id;expect(()=>checkStory(dup,R)).toThrow(/겹쳐요/);
  });
  it('스토리 대결: 빌런 팀과 끝까지 싸울 수 있음',()=>{
    const story=loadStory(process.cwd(),R),st=story.chapters[1].stages.at(-1)!;
    const me=['D1','T4','I3'].map((id,i):UnitRecord=>({uid:`a${i}`,id,name:id,tier:'LIMITED',level:15,xp:0,stage:0,evoAugs:[]}));
    const foe=st.enemy.map((e,i):UnitRecord=>({uid:`b${i}`,id:e.id,name:R.villains.get(e.id)!.name!,tier:'LIMITED',level:e.level,xp:0,stage:0,evoAugs:[]}));
    const b=createBattle(R,me,foe,7);
    for(let g=0;g<400&&b.phase!=='over';g++){if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);continue;}act(b,[pickAi(b,0),pickAi(b,1)]);}
    expect(b.phase).toBe('over');
  });
});

describe('합동기·기술 배우기',()=>{
  const mk=(id:string,i:number,level=30):UnitRecord=>({uid:`u${i}`,id,name:id,tier:'LIMITED',level,xp:0,stage:0,evoAugs:[]});
  it('같은 시너지 2마리면 그 둘만 합동기, 팀당 1번',()=>{
    const b=createBattle(R,[mk('D2',0),mk('T2',1),mk('D1',2)],[mk('D3',3),mk('T3',4),mk('E3',5)],3);
    expect(b.sides[0].fighters[0].skills.some(k=>k.id==='combo-GOLD')).toBe(true);
    expect(b.sides[0].fighters[1].skills.some(k=>k.id==='combo-GOLD')).toBe(true);
    expect(b.sides[0].fighters[2].skills.some(k=>k.id.startsWith('combo-'))).toBe(false);
    expect(b.sides[1].fighters.every(f=>f.skills.some(k=>k.id==='combo-DARK'))).toBe(true);
    aiChoose(b,0);aiChoose(b,1);
    act(b,[{kind:'skill',skillId:'combo-GOLD'},pickAi(b,1)]);
    expect(b.sides[0].comboUsed).toBe(true);
    expect(b.log.some(l=>l.includes('합동기 「금고 문 열기」'))).toBe(true);
    if(b.phase==='action')expect(legalActions(b,0).some(a=>a.kind==='skill'&&a.skillId==='combo-GOLD')).toBe(false);
  });
  it('레벨 10·20…마다 기회, 후보는 항상 같고, 칸이 차면 바꿔야 함',()=>{
    const r=mk('T7',0,25);
    expect(learnChances(R,r)).toBe(2);
    const o=learnOffers(R,r);
    expect(o).toHaveLength(3);expect(learnOffers(R,r).map(k=>k.id)).toEqual(o.map(k=>k.id));
    expect(o.every(k=>R.learnables.find(x=>`learn-${x.id}`===k.id)!.level<=10)).toBe(true);
    learnSkill(R,r,o[0].id);expect(r.learned).toEqual([o[0].id.slice(6)]);
    learnSkill(R,r,learnOffers(R,r)[0].id);expect(r.learned).toHaveLength(2);expect(learnChances(R,r)).toBe(0);
    r.level=31;const o3=learnOffers(R,r);
    expect(()=>learnSkill(R,r,o3[0].id)).toThrow(/꽉 찼어요/);
    learnSkill(R,r,o3[0].id,r.learned![0]);expect(r.learned).toHaveLength(2);expect(r.learned).toContain(o3[0].id.slice(6));
    r.level=45;learnSkill(R,r,null);expect(r.learnUsed).toBe(4);expect(learnChances(R,r)).toBe(0);
    const b=createBattle(R,[r],[mk('D1',1)],1);
    expect(b.sides[0].fighters[0].skills.filter(k=>k.id.startsWith('learn-'))).toHaveLength(2);
  });
});

describe('빌런 기본기 이름',()=>{
  it('쭈매미는 먹물 뿌리기, 랍매미는 집게 꼬집기, 갈매미·끼룩단은 똥폭탄',()=>{
    const mk=(id:string,i:number):UnitRecord=>({uid:`u${i}`,id,name:id,tier:'LIMITED',level:10,xp:0,stage:0,evoAugs:[]});
    const b=createBattle(R,[mk('D1',0),mk('V12',1)],[mk('V1',2),mk('V7',3)],1);
    const basic=(s:0|1,i:number)=>b.sides[s].fighters[i].skills.find(k=>k.id.startsWith('basic-'))!.name;
    expect([basic(0,0),basic(0,1),basic(1,0),basic(1,1)]).toEqual(['똥폭탄','똥폭탄','먹물 뿌리기','집게 꼬집기']);
  });
});

describe('3부 블랙 갈매미맨',()=>{
  it('거울 단계는 상대 팀에 가장 많은 속성을 복사, 땅콩 폭주는 이름에 갈매미맨이 있는 쪽만',()=>{
    const mk=(id:string,i:number,level=24):UnitRecord=>({uid:`u${i}`,id,name:(R.units.has(id)?id:R.villains.get(id)!.name!),tier:'LIMITED',level,xp:0,stage:0,evoAugs:[]});
    // D7 갈크탑(황금)·D2 원금 갈매미맨(황금)·T8 갈쫄쫄이(실) → 거울은 황금
    const team=[{...mk('D7',0),name:'갈크탑'},{...mk('D2',1),name:'원금 갈매미맨'},{...mk('T8',2),name:'갈쫄쫄이'}];
    const b=createBattle(R,team,[mk('V17',3),mk('V21',4),mk('V22',5)],5);
    expect(b.sides[1].fighters[1].attr).toBe('GOLD');
    expect(b.sides[1].fighters[0].attr).toBe('SHADOW');
    expect(b.sides[0].fighters.map(f=>f.peanut)).toEqual([false,true,false]);
  });
});

describe('승진해야 2·3부',()=>{
  it('2부는 팀 전원 1단계, 3부는 2단계 승진이 조건이고, 승진은 레벨+깬 부 수가 필요',()=>{
    const story=loadStory(process.cwd(),R);
    expect(story.chapters.map(c=>c.needStage??0)).toEqual([0,0,1,2]);
    const u:UnitRecord={uid:'a',id:'D2',name:'원금 갈매미맨',tier:'LIMITED',level:20,xp:0,stage:0,evoAugs:[]};
    expect(canEvolve(R,u,0)).toBe(false);            // 1부를 안 깼으면 안 됨
    expect(canEvolve(R,u,1)).toBe(true);
    expect(canEvolve(R,{...u,level:19},1)).toBe(false);
    expect(canEvolve(R,{...u,stage:1,level:35},1)).toBe(false);   // 2단계는 2부까지 깨야
    expect(canEvolve(R,{...u,stage:1,level:35},2)).toBe(true);
  });
});
