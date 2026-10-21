import type {Battle,Fighter} from './engine.js';
const living=(f:Fighter)=>f.hp>0;
const utility=(f:Fighter)=>f.skills.filter(k=>!k.power&&k.effects.length>0);
// 현재 공개 전투 상태만 검사한다. 아직 고르지 않은 강제교체 대상은 가능한 생존 출전 후보로 평가한다.
export function eligibleAugmentIds(b:Battle,side:0|1):string[]{
 const s=b.sides[side],team=s.fighters.filter(living),foes=b.sides[side===0?1:0].fighters.filter(living);
 const leaders=s.needSwitch?team:[s.fighters[s.active]].filter(living);
 const hasBench=team.length>=2;
 const eligible=(id:string)=>{
  switch(id){
   case 'firstaid': return team.some(f=>f.hp<f.maxHp);
   case 'rinse': return leaders.some(f=>Object.values(f.status).some(n=>n&&n>0));
   case 'resetline': return team.some(f=>f.mods.some(m=>m.pct<0&&m.turns>0));
   case 'handoffguard':case 'relief':case 'bloodrelay':case 'baton':return hasBench;
   case 'breach':return team.some(f=>utility(f).length>0);
   case 'underdog':return team.some(f=>foes.some(e=>e.rec.level-f.rec.level>=10));
   case 'equalfield':return team.some(f=>foes.some(e=>e.rec.level>f.rec.level));
   case 'quietstorm':return team.some(f=>new Set(utility(f).map(k=>k.id)).size>=2);
   case 'rainbow':return team.some(f=>new Set(f.skills.filter(k=>(k.power??0)>0).map(k=>k.attr??f.attr)).size>=3);
   case 'mortgage':return leaders.some(f=>f.hp<=f.maxHp*0.65);
   case 'coldbargain':return team.some(f=>f.skills.some(k=>(k.power??0)>0&&((k.drain??0)>0||!!f.passive.lifesteal||s.augs.includes('drain')||s.augs.includes('triage')||k.effects.some(e=>e.kind==='heal'))));
   case 'shatterprice':return leaders.some(f=>f.mods.some(m=>m.pct>0&&m.turns>0)||f.guards.some(g=>g.pct>0&&g.turns>0));
   case 'sharedburden':return leaders.some(lead=>{const bench=team.filter(f=>f!==lead);return bench.length>0&&bench.every(f=>f.hp>f.maxHp*0.1);});
   default:return true;
  }
 };
 return b.R.battleAugments.filter(a=>eligible(a.id)).map(a=>a.id);
}
