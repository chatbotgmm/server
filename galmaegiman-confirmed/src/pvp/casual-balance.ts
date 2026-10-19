import type {Rules} from '../battle/core.js';
import type {Battle} from '../battle/engine.js';
import {innateLearned,maxStageAt,stableEvoChoices,type UnitRecord} from '../battle/growth.js';
import {GameError} from '../types.js';

const invalid=():never=>{throw new GameError('저장된 갈매미 성장 정보를 확인해 주세요.');};
export function validLevel(level:unknown,max:number):number {
  if(typeof level!=='number'||!Number.isInteger(level)||level<1||level>max)return invalid();
  return level;
}
export function deployedMean(team:ReadonlyArray<Pick<UnitRecord,'level'>>,max:number):number {
  if(team.length<1||team.length>3)return invalid();
  return team.reduce((sum,u)=>sum+validLevel(u?.level,max),0)/team.length;
}
/** Same per-owned-instance save lookup and innate defaults as native.ts; never writes growth. */
export function casualRecord(R:Rules,base:Pick<UnitRecord,'uid'|'id'|'name'|'tier'>,saved:unknown,startLevel:number):UnitRecord {
  if(saved!==undefined&&(!saved||typeof saved!=='object'||Array.isArray(saved)))return invalid();
  const u=(saved??{}) as Partial<UnitRecord>;
  const level=validLevel(u.level===undefined?startLevel:u.level,R.data.growth.maxLevel);
  const stage=u.stage===undefined?0:u.stage;
  if(!Number.isInteger(stage)||stage<0||stage>3||stage>maxStageAt(R,level))return invalid();
  if(u.xp!==undefined&&(!Number.isFinite(u.xp)||u.xp<0))return invalid();
  if(u.evoAugs!==undefined&&(!Array.isArray(u.evoAugs)||u.evoAugs.some(x=>typeof x!=='string'||!R.evoAug.has(x))))return invalid();
  if(u.learned!==undefined&&(!Array.isArray(u.learned)||u.learned.some(x=>typeof x!=='string')))return invalid();
  return {...base,level,xp:u.xp??0,stage,evoAugs:[...(u.evoAugs??[])],learned:u.learned?[...u.learned]:innateLearned(R,base.id,base.uid),learnUsed:u.learnUsed??0,learnPick:u.learnPick??0};
}
/** Rebuilt at the final preparation boundary, so edits never leave a stale AI level. */
export function matchCasualBot(R:Rules,human:UnitRecord[],bot:UnitRecord[]):UnitRecord[] {
  const level=Math.round(deployedMean(human,R.data.growth.maxLevel));
  // Match earned promotion, not the maximum promotion the level could unlock.
  const meanStage=human.reduce((sum,rec)=>{
    if(!Number.isInteger(rec.stage)||rec.stage<0||rec.stage>3||rec.stage>maxStageAt(R,rec.level))return invalid();
    return sum+rec.stage;
  },0)/human.length;
  const stage=Math.min(Math.round(meanStage),maxStageAt(R,level));
  return bot.map(original=>{
    const rec:UnitRecord={uid:original.uid,id:original.id,name:original.name,tier:original.tier,level,xp:0,stage:0,evoAugs:[],learned:innateLearned(R,original.id,original.uid)};
    while(rec.stage<stage){const choice=stableEvoChoices(R,rec)[0];if(choice)rec.evoAugs.push(choice.id);rec.stage++;}
    if(stage>0)rec.learned=['@',...rec.learned!.slice(1).filter(k=>k!=='sig').slice(0,R.data.learn.slots-1),'sig'];
    return rec;
  });
}
export function compensationBoost(a:number,b:number):{side:0|1|null;boost:number} {
  if(!Number.isFinite(a)||!Number.isFinite(b)||a<=0||b<=0)return invalid();
  if(a===b)return {side:null,boost:0};
  return {side:a<b?0:1,boost:Math.min(.15,.25*(Math.sqrt(Math.max(a,b)/Math.min(a,b))-1))};
}
/** Called once on a fresh battle only. No record, skill, speed or scale changes. */
export function applyCasualCompensation(battle:Battle,mode:'normal'|'rank',bot:boolean):void {
  if(mode!=='normal'||bot)return;
  const means=battle.sides.map(s=>deployedMean(s.fighters.map(f=>f.rec),battle.R.data.growth.maxLevel));
  const {side,boost}=compensationBoost(means[0],means[1]);if(side===null)return;
  for(const f of battle.sides[side].fighters){
    f.maxHp=Math.floor(f.maxHp*(1+boost));f.hp=f.maxHp;
    for(const key of ['patk','pdef','matk','mdef'] as const)f.base[key]=Math.floor(f.base[key]*(1+boost));
  }
}
