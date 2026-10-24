import {readFileSync,writeFileSync} from 'node:fs';
import {fiveStatAttack,decideTurnOrder} from '../src/battle/roster60-five-combat.js';
import {focusMp} from '../src/battle/roster60-rules.js';
const root='data/test-profiles/five-stat-device-test-v1/';const catalog=JSON.parse(readFileSync(root+'catalog.json','utf8')),policy=JSON.parse(readFileSync(root+'battle-policy.json','utf8'));
function fight(a:any,b:any,level:number,seed:string){
 const f=[a,b].map((u,i)=>({...u,id:String(i),stats:u.combatGrowth[level-1],hp:u.combatGrowth[level-1].hp,mp:u.combatGrowth[level-1].mp,skills:u.skills.filter((s:any)=>s.learnLevel<=level)}));let actions=0,focus=0,reflected=0,immune=0;
 for(let turn=0;turn<policy.maxRounds&&f.every(x=>x.hp>0);turn++){
  const order=decideTurnOrder('sim',turn,f.map(x=>({id:x.id,baseSpeed:x.stats.speed,modifiers:[]})) as any,seed).order;
  for(const id of order){const x=f[+id],y=f[1-Number(id)];if(x.hp<=0||y.hp<=0)break;
   const damage=(s:any)=>fiveStatAttack({power:s.power,level,durability:y.stats.durability,suitability:x.suitability[s.element],defense:y.defense[s.element],attackerHp:x.hp,defenderHp:y.hp},policy.calculation,(b,m)=>b*m);
   const score=(s:any)=>{const d=damage(s);return d.reflected?-d.damage:d.damage;};
   const all=[x.attack,...x.skills],available=all.filter(s=>s.mpCost<=x.mp).sort((a,b)=>score(b)-score(a)||a.mpCost-b.mpCost);let skill=available[0];
   const refill=focusMp(x.mp,x.stats.mp);if(all.some(s=>s.mpCost>x.mp&&s.mpCost<=refill.mp&&score(s)>2*score(skill))){x.mp=refill.mp;focus++;}else{const hit=damage(skill);x.mp-=skill.mpCost;hit.target==='attacker'?x.hp-=hit.damage:y.hp-=hit.damage;reflected+=+hit.reflected;immune+=+(hit.defense==='immune');}
   actions++;
  }
 }
 return {winner:f[0].hp<=0?1:f[1].hp<=0?0:null,actions,focus,reflected,immune};
}
const summaries=[];for(const level of [1,20,40,60]){const results=[];for(let i=0;i<60;i++)for(let j=i+1;j<60;j++){results.push(fight(catalog.characters[i],catalog.characters[j],level,'a'));results.push(fight(catalog.characters[j],catalog.characters[i],level,'b'));}
 const counts=results.map(r=>r.actions).sort((a,b)=>a-b);summaries.push({level,matches:results.length,min:counts[0],median:counts[Math.floor(counts.length/2)],p95:counts[Math.floor(counts.length*.95)],max:counts.at(-1),draws:results.filter(r=>r.winner===null).length,focusActions:results.reduce((n,r)=>n+r.focus,0),reflections:results.reduce((n,r)=>n+r.reflected,0),immuneHits:results.reduce((n,r)=>n+r.immune,0)});}
const representative=[];for(const level of [20,40,60])for(const ids of [['u01','u57'],['u07','u53'],['u06','u41'],['u03','u08']]){const a=catalog.characters.find((x:any)=>x.id===ids[0]),b=catalog.characters.find((x:any)=>x.id===ids[1]);representative.push({level,ids,forward:fight(a,b,level,'a'),reverse:fight(b,a,level,'b')});}
const tieFirstSideChecks=[];
for(const u of catalog.characters){const speeds=[{id:'0',baseSpeed:u.combatGrowth[19].speed,modifiers:[]},{id:'1',baseSpeed:u.combatGrowth[19].speed,modifiers:[]}];
 for(const first of ['0','1']){let n=0;while(decideTurnOrder('sim',0,speeds as any,String(n)).order[0]!==first)n++;tieFirstSideChecks.push({species:u.id,first,result:fight(u,u,20,String(n))});}}
writeFileSync(root+'simulation.json',JSON.stringify({profile:catalog.version,scope:'deterministic algorithmic comparison, not device/playtesting; speed order applies, side/seed swapped',summaries,representative,tieFirstSideChecks},null,2)+'\n');console.log(JSON.stringify(summaries));
