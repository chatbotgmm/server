// 스토리 밸런스 계산: npx tsx scripts/story-balance.ts [판 수=2000]
// 무작위 최상위 3마리(시작 레벨, 진화 전)로 스테이지를 순서대로 깸. 양쪽 모두 간단한 AI가 조종(사람은 상성·증강체를 더 잘 고르므로 실제보다 어려운 쪽)
// 지면 진 경험치를 받고 같은 스테이지를 다시 도전. 스테이지마다 첫 도전 승률·평균 도전 횟수·들어갈 때 평균 레벨
import { loadContent } from '../src/content.js';
import type { TopTier } from '../src/battle/rules.js';
import { loadBattleRules } from '../src/battle/load.js';
import { loadStory } from '../src/battle/story.js';
import { addXp, battleXp, type UnitRecord } from '../src/battle/growth.js';
import { createBattle, act, pickAi, aiChoose, type Battle } from '../src/battle/engine.js';

const R=loadBattleRules(),story=loadStory(process.cwd(),R),content=loadContent();
const top=[...R.units.keys()];
let seed=4242;const rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;};
function run(b:Battle){
  for(let g=0;g<400&&b.phase!=='over';g++){if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);continue;}act(b,[pickAi(b,0),pickAi(b,1)]);}
  return b;
}
const N=Number(process.argv[2]??2000);
const stages=story.chapters.flatMap(c=>c.stages);
const stat=stages.map(()=>({first:0,tries:0,level:0,stuck:0}));
for(let n=0;n<N;n++){
  const ids=new Set<string>();while(ids.size<3)ids.add(top[Math.floor(rnd()*top.length)]);
  const team:UnitRecord[]=[...ids].map((id,i)=>({uid:`a${i}`,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as TopTier,level:story.start.level,xp:0,stage:0,evoAugs:[]}));
  stages.forEach((st,si)=>{
    stat[si].level+=team.reduce((a,u)=>a+u.level,0)/3;
    const foe=st.enemy.map((e,i)=>({uid:`b${i}`,id:e.id,name:R.villains.get(e.id)!.name!,tier:'LIMITED' as TopTier,level:e.level,xp:0,stage:0 as const,evoAugs:[]}));
    const avg=st.enemy.reduce((a,e)=>a+e.level,0)/st.enemy.length;
    for(let t=1;t<=20;t++){
      stat[si].tries++;
      const b=run(createBattle(R,team.map(u=>({...u})),foe.map(u=>({...u})),Math.floor(rnd()*1e9)));
      const won=b.winner===0;for(const u of team)addXp(R,u,battleXp(R,avg,won));
      if(won){if(t===1)stat[si].first++;break;}
      if(t===20)stat[si].stuck++;
    }
  });
}
console.log(`\n## 스토리 밸런스 (${N}회, 시작 레벨 ${story.start.level}, AI끼리)`);
console.log('| 스테이지 | 상대(레벨) | 들어갈 때 평균 레벨 | 첫 도전 승률 | 평균 도전 횟수 | 20번 넘게 못 깸 |\n|---|---|---|---|---|---|');
stages.forEach((st,i)=>console.log(`| ${st.id} ${st.title} | ${st.enemy.map(e=>`${R.villains.get(e.id)!.name} ${e.level}`).join(', ')} | ${(stat[i].level/N).toFixed(1)} | ${(stat[i].first/N*100).toFixed(0)}% | ${(stat[i].tries/N).toFixed(2)} | ${stat[i].stuck} |`));
