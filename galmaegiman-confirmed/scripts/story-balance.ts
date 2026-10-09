// 스토리 밸런스 계산: npx tsx scripts/story-balance.ts [판 수=1000]
// 무작위 최상위 3마리(시작 레벨)로 스테이지를 순서대로 깸. 양쪽 모두 간단한 AI(사람은 상성·증강체를 더 잘 고르므로 실제보다 어려운 쪽)
// 지면 진 경험치를 받고 다시 도전. 장에 승진 조건(needStage)이 있으면, 조건이 될 때까지 앞 장 마지막 스테이지를 반복(수련)하고 승진(직무 표창은 무작위)
import type { TopTier } from '../src/battle/rules.js';
import { loadContent } from '../src/content.js';
import { loadBattleRules } from '../src/battle/load.js';
import { loadStory } from '../src/battle/story.js';
import { addXp, battleXp, canEvolve, evoChoices, learnChances, learnOffers, learnSkill, slotSkills, type UnitRecord, type Stage } from '../src/battle/growth.js';
import { createBattle, act, pickAi, aiChoose, type Battle } from '../src/battle/engine.js';

const R=loadBattleRules(),story=loadStory(process.cwd(),R),content=loadContent();
const top=[...R.units.keys()];
let seed=4242;const rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;};
function run(b:Battle){for(let g=0;g<400&&b.phase!=='over';g++){if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);continue;}act(b,[pickAi(b,0),pickAi(b,1)]);}return b;}
const N=Number(process.argv[2]??1000);
const stages=story.chapters.flatMap(c=>c.stages.map(s=>({...s,ch:c})));
const stat=stages.map(()=>({first:0,tries:0,level:0,stage:0,stuck:0}));
const grind=new Map<string,number[]>();
for(let n=0;n<N;n++){
  const ids=new Set<string>();while(ids.size<3)ids.add(top[Math.floor(rnd()*top.length)]);
  const team:UnitRecord[]=[...ids].map((id,i)=>({uid:`a${i}`,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as TopTier,level:story.start.level,xp:0,stage:0,evoAugs:[]}));
  let parts=0,blocked=false;
  const fight=(st:typeof stages[number])=>{
    const foe=st.enemy.map((e,i)=>({uid:`b${i}`,id:e.id,name:R.villains.get(e.id)!.name!,tier:'LIMITED' as TopTier,level:e.level,xp:0,stage:(e.stage??st.ch.needStage??0) as Stage,evoAugs:[]}));
    const b=run(createBattle(R,team.map(u=>({...u})),foe,Math.floor(rnd()*1e9))),won=b.winner===0,avg=st.enemy.reduce((a,e)=>a+e.level,0)/st.enemy.length;
    for(const u of team){addXp(R,u,battleXp(R,avg,won));while(learnChances(R,u)>0){const o=learnOffers(R,u);learnSkill(R,u,o[0]?.id??null,slotSkills(R,u)[0]?.id);}}
    return won;
  };
  const evolveAll=()=>{for(const u of team)while(canEvolve(R,u,parts)){const c=evoChoices(R,u,rnd);u.stage=(u.stage+1) as Stage;if(c.length)u.evoAugs.push(c[Math.floor(rnd()*c.length)].id);}};
  let prevBoss:typeof stages[number]|null=null;
  stages.forEach((st,si)=>{
    if(blocked)return;
    if(st===stages.find(x=>x.ch===st.ch)&&st.ch.needStage){
      let g=0;evolveAll();
      while(team.some(u=>u.stage<st.ch.needStage!)&&g<300){fight(prevBoss!);g++;evolveAll();}
      (grind.get(st.ch.id)??grind.set(st.ch.id,[]).get(st.ch.id)!).push(g);
      if(team.some(u=>u.stage<st.ch.needStage!)){blocked=true;return;}
    }
    stat[si].level+=team.reduce((a,u)=>a+u.level,0)/3;stat[si].stage+=team.reduce((a,u)=>a+u.stage,0)/3;
    for(let t=1;t<=20;t++){stat[si].tries++;if(fight(st)){if(t===1)stat[si].first++;break;}if(t===20)stat[si].stuck++;}
    if(st.id===st.ch.stages.at(-1)!.id&&/^\d+$/.test(st.ch.id))parts++;
    prevBoss=st;
  });
}
console.log(`\n## 스토리 밸런스 (${N}회, 시작 레벨 ${story.start.level}, 승진 레벨 ${R.data.growth.evolveLevel.join('/')}, AI끼리)`);
for(const [ch,g] of grind){g.sort((a,b)=>a-b);console.log(`- ${ch}부 입장 전 수련(반복) 대결 수: 평균 ${(g.reduce((a,b)=>a+b,0)/g.length).toFixed(1)}, 중앙값 ${g[g.length>>1]}, 상위 10% ${g[Math.floor(g.length*0.9)]}`);}
console.log('| 스테이지 | 상대(레벨) | 들어갈 때 평균 레벨·승진 | 첫 도전 승률 | 평균 도전 횟수 | 20번 넘게 못 깸 |\n|---|---|---|---|---|---|');
stages.forEach((st,i)=>{const c=stat[i].tries?N:1;console.log(`| ${st.id} ${st.title} | ${st.enemy.map(e=>`${R.villains.get(e.id)!.name} ${e.level}`).join(', ')} | ${(stat[i].level/N).toFixed(1)} · ${(stat[i].stage/N).toFixed(1)}단계 | ${(stat[i].first/N*100).toFixed(0)}% | ${(stat[i].tries/N).toFixed(2)} | ${stat[i].stuck} |`);void c;});
