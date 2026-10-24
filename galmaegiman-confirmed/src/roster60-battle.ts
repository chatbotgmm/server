import {createHash,randomBytes,randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient} from '@prisma/client';
import {RosterError,verifyRosterTutorial,type RosterCatalog} from './roster60-onboarding.js';
import {growthAt,focusMp,spendMp,validateRosterDefinition,type SkillElement,type ElementProfile,type Defense} from './battle/roster60-rules.js';
import {validateFiveStats,fiveRebirthStats,fiveRebirthPolicy,type FiveStats} from './battle/roster60-five-stats.js';
import {validateFiveCombatPolicy,fiveStatAttack,decideTurnOrder,reuseTurnOrder,type FiveCombatPolicy,type TurnOrderDecision} from './battle/roster60-five-combat.js';
export type BattlePolicy={approved:boolean;version:string;catalogVersion:string;calculation:FiveCombatPolicy;reflection:'return-post-durability';enemyStrategy:'first-affordable'|'best-affordable';maxRounds:number;encounters:{id:string;speciesId:string;level:number;tutorial:boolean;winXp:number;lossXp:number}[]};
type Skill={id:string;name:string;element:SkillElement;mpCost:number;power:number};
type Fighter={id:string;speciesId:string;name:string;level:number;stats:FiveStats;hp:number;mp:number;attack:Skill;skills:Skill[];suitability:ElementProfile<number>;defense:ElementProfile<Defense>};
type State={version:'five-battle-v1';turn:number;fighters:[Fighter,Fighter];decision:TurnOrderDecision;outcome:'win'|'loss'|'draw'|null;events:unknown[]};
export type BattleAction={kind:'attack'|'skill'|'focus';skillId?:string};
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const hash=(v:unknown)=>createHash('sha256').update(JSON.stringify(v)).digest('hex');
function validatePolicy(p:BattlePolicy|null,c:RosterCatalog|null):asserts p is BattlePolicy{
 if(p?.approved!==true||c?.approved!==true||c.statSchema!=='gmm-five-stats-v1'||p.catalogVersion!==c.version)return fail('BATTLE_POLICY_PENDING',503);
 try{validateFiveCombatPolicy(p.calculation);}catch{return fail('BATTLE_POLICY_INVALID',503);}
 if(typeof p.version!=='string'||!p.version||p.reflection!=='return-post-durability'||!['first-affordable','best-affordable'].includes(p.enemyStrategy)||!Number.isSafeInteger(p.maxRounds)||p.maxRounds<1||p.maxRounds>1000||!Array.isArray(p.encounters))return fail('BATTLE_POLICY_INVALID',503);
 for(const e of p.encounters)if(!e.id||typeof e.tutorial!=='boolean'||!Number.isInteger(e.level)||e.level<1||e.level>60||[e.winXp,e.lossXp].some(n=>!Number.isSafeInteger(n)||n<0))return fail('BATTLE_POLICY_INVALID',503);
 if(new Set(p.encounters.map(e=>e.id)).size!==p.encounters.length)return fail('BATTLE_POLICY_INVALID',503);
}
function fighter(c:RosterCatalog,speciesId:string,level:number,id:string,cycle=0):Fighter{
 const matches=c.characters.filter(x=>x.id===speciesId);const u=matches.length===1?matches[0]:undefined;if(!u)return fail('BATTLE_UNIT_DATA_PENDING',503);
 try{validateRosterDefinition(u);}catch{return fail('BATTLE_UNIT_DATA_INVALID',503);}
 const stats=(u as typeof u&{combatGrowth?:FiveStats[]}).combatGrowth?.[level-1];
 try{validateFiveStats(stats);}catch{return fail('BATTLE_GROWTH_PENDING',503);}
 if(stats!.mp!==u.growth[level-1]?.maxMp)return fail('BATTLE_MP_GROWTH_MISMATCH',503);
 for(const sk of [u.attack,...u.skills] as Skill[]){
  if(!Number.isFinite(sk.power)||sk.power<0||'effects' in sk||'priority' in sk)return fail('BATTLE_SKILL_POLICY_PENDING',503);
 }
 return {id,speciesId,name:u.name,level,stats:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle),hp:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle).hp,mp:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle).mp,attack:structuredClone(u.attack as Skill),skills:structuredClone((u.skills as (Skill&{learnLevel?:number})[]).filter(s=>(s.learnLevel??1)<=level)),suitability:structuredClone(u.suitability),defense:structuredClone(u.defense)};
}
function order(id:string,state:Pick<State,'turn'|'fighters'>,seed:string){const f=(i:number)=>({id:state.fighters[i].id,baseSpeed:state.fighters[i].stats.speed,modifiers:[] as number[]});return decideTurnOrder(id,state.turn,[f(0),f(1)],seed);}
function dto(row:{id:string;revision:number;status:string;state:string;settlement:string|null;snapshot?:string}){
 const s=JSON.parse(row.state) as State;const policy=row.snapshot?JSON.parse(row.snapshot).policy:null;
 return {balanceProfile:policy?.version??null,testBaseline:policy?.profileKind==='user-authorized-test-baseline',battleId:row.id,revision:row.revision,status:row.status,state:s,settlement:row.settlement?JSON.parse(row.settlement):null,disabledCommands:{item:'ITEM_POLICY_PENDING',switch:'SWITCH_POLICY_PENDING',escape:'ESCAPE_POLICY_PENDING'}};
}
export class RosterBattle {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>BattlePolicy|null){}
 private async request(playerId:string,requestId:string,payload:unknown,run:(tx:Prisma.TransactionClient)=>Promise<unknown>){
  const fingerprint=hash(payload);const once=()=>this.db.$transaction(async tx=>{
   const prior=await tx.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});
   if(prior){if(prior.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');return JSON.parse(prior.result);}
   const result=await run(tx);await tx.roster60BattleReceipt.create({data:{playerId,requestId,fingerprint,result:JSON.stringify(result)}});return result;
  });
  try{return await once();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return once();throw e;}
 }
 async options(playerId:string){
  const c=this.catalog(),p=this.policy();validatePolicy(p,c);
  const rows=await this.db.roster60Owned.findMany({where:{playerId,status:'AVAILABLE',locked:false},orderBy:{id:'asc'},take:1000});
  return {units:rows.map(u=>({ownedId:u.id,speciesId:u.speciesId,name:c!.characters.find(d=>d.id===u.speciesId)?.name??u.speciesId})),encounters:p.encounters.map(e=>({id:e.id,name:c!.characters.find(u=>u.id===e.speciesId)?.name??e.speciesId,tutorial:e.tutorial})),catalogVersion:c!.version};
 }
 async create(playerId:string,b:{requestId:string;ownedId:string;encounterId:string}){
  return this.request(playerId,b.requestId,['create',b.ownedId,b.encounterId],async tx=>{
   const c=this.catalog(),p=this.policy();validatePolicy(p,c);
   const encounter=p.encounters.find(e=>e.id===b.encounterId);if(!encounter)return fail('ENCOUNTER_NOT_ALLOWED');
   const owned=await tx.roster60Owned.findFirst({where:{id:b.ownedId,playerId}});if(!owned)return fail('UNIT_NOT_FOUND',404);
   if(owned.reincarnation&&c!.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);
   if(owned.status!=='AVAILABLE'||owned.locked)return fail('UNIT_UNAVAILABLE');
   if(owned.totalXp<0n||owned.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
   const def=c!.characters.find(u=>u.id===owned.speciesId);if(!def)return fail('BATTLE_UNIT_DATA_PENDING',503);
   const level=growthAt(Number(owned.totalXp),def.growth,owned.reincarnation,def.reincarnationXpPolicy).level;
   // Unknown cap-overflow policy must not trap a unit in an un-settleable battle.
   try{growthAt(Number(owned.totalXp)+Math.max(encounter.winXp,encounter.lossXp),def.growth,owned.reincarnation,def.reincarnationXpPolicy);}catch(e){return fail((e as Error).message);}
   if(encounter.tutorial&&owned.speciesId!=='u01')return fail('TUTORIAL_STARTER_REQUIRED');
   const id=randomUUID(),seed=randomBytes(32).toString('hex');
   const f:[Fighter,Fighter]=[fighter(c!,owned.speciesId,level,'self',owned.reincarnation),fighter(c!,encounter.speciesId,encounter.level,'enemy')];
   const state:State={version:'five-battle-v1',turn:0,fighters:f,decision:order(id,{turn:0,fighters:f},seed),outcome:null,events:[]};
   const lock=await tx.roster60Owned.updateMany({where:{id:owned.id,playerId,status:'AVAILABLE',locked:false,totalXp:owned.totalXp},data:{status:'BATTLE'}});if(lock.count!==1)return fail('UNIT_UNAVAILABLE');
   const row=await tx.roster60Battle.create({data:{id,playerId,ownedId:owned.id,seed,snapshot:JSON.stringify({policy:p,encounter,growth:def.growth,startingXp:owned.totalXp.toString()}),state:JSON.stringify(state)}});
   return dto(row);
  });
 }
 async read(playerId:string,id:string){const row=await this.db.roster60Battle.findFirst({where:{id,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);return dto(row);}
 async active(playerId:string){return {battles:(await this.db.roster60Battle.findMany({where:{playerId,status:{in:['ACTIVE','FINISHED']}},orderBy:{createdAt:'desc'},take:20})).map(dto)};}
 async action(playerId:string,b:{requestId:string;battleId:string;revision:number;action:BattleAction}){
  return this.request(playerId,b.requestId,['action',b.battleId,b.revision,b.action.kind,b.action.skillId??null],async tx=>{
   const row=await tx.roster60Battle.findFirst({where:{id:b.battleId,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);
   if(row.revision!==b.revision)return fail('STALE_REVISION');if(row.status!=='ACTIVE')return fail('BATTLE_FINISHED');
   const state=JSON.parse(row.state) as State,snap=JSON.parse(row.snapshot) as {policy:BattlePolicy};
   const self=state.fighters[0],enemy=state.fighters[1];
   const playerSkill=b.action.kind==='attack'?self.attack:b.action.kind==='skill'?self.skills.find(s=>s.id===b.action.skillId):null;
   if(b.action.kind!=='focus'&&!playerSkill)return fail('SKILL_NOT_EQUIPPED');
   if(playerSkill&&playerSkill.mpCost>self.mp)return fail('INSUFFICIENT_MP');
   // AI rule is explicit in pinned server policy, never a client action.
   let enemySkill=[enemy.attack,...enemy.skills].find(s=>s.mpCost<=enemy.mp);
   if(snap.policy.enemyStrategy==='best-affordable'){
    const score=(sk:Skill)=>fiveStatAttack({power:sk.power,level:enemy.level,durability:self.stats.durability,suitability:enemy.suitability[sk.element],defense:self.defense[sk.element],attackerHp:enemy.hp,defenderHp:self.hp},snap.policy.calculation,(b,f)=>b*f);
    const usable=[enemy.attack,...enemy.skills].filter(sk=>sk.mpCost<=enemy.mp);
    usable.sort((a,b)=>{const x=score(a),y=score(b);return (y.reflected?-y.damage:y.damage)-(x.reflected?-x.damage:x.damage)||a.mpCost-b.mpCost;});
    enemySkill=usable[0];
    const bestNow=enemySkill?score(enemySkill).damage:0;
    const afterFocus=focusMp(enemy.mp,enemy.stats.mp).mp;
    if(enemy.skills.some(sk=>sk.mpCost>enemy.mp&&sk.mpCost<=afterFocus&&!score(sk).reflected&&score(sk).damage>2*bestNow))enemySkill=undefined;
   }
   const decision=reuseTurnOrder(state.decision,row.id,state.turn);
   const events:unknown[]=[];
   for(const who of decision.order){
    const actor=who==='self'?self:enemy,target=who==='self'?enemy:self;if(!actor.hp||!target.hp)continue;
    const skill=who==='self'?playerSkill:enemySkill;
    if(!skill){const next=focusMp(actor.mp,actor.stats.mp);actor.mp=next.mp;events.push({actor:who,kind:'focus',recovered:next.recovered});continue;}
    actor.mp=spendMp(actor.mp,skill.mpCost);
    const hit=fiveStatAttack({power:skill.power,level:actor.level,durability:target.stats.durability,suitability:actor.suitability[skill.element],defense:target.defense[skill.element],attackerHp:actor.hp,defenderHp:target.hp},snap.policy.calculation,(base,fit)=>base*fit);
    if(hit.target==='attacker')actor.hp-=hit.damage;else target.hp-=hit.damage;
    events.push({actor:who,kind:'skill',skillId:skill.id,...hit});
   }
   state.events.push({turn:state.turn,decision,events});state.turn++;
   state.outcome=self.hp<=0?'loss':enemy.hp<=0?'win':state.turn>=snap.policy.maxRounds?'draw':null;
   if(!state.outcome)state.decision=order(row.id,state,row.seed);
   const status=state.outcome?'FINISHED':'ACTIVE';
   const updated=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'ACTIVE'},data:{state:JSON.stringify(state),revision:{increment:1},status}});
   if(updated.count!==1)return fail('STALE_REVISION');
   return dto({...row,state:JSON.stringify(state),revision:row.revision+1,status});
  });
 }
 async settle(playerId:string,b:{requestId:string;battleId:string;revision:number}){
  return this.request(playerId,b.requestId,['settle',b.battleId,b.revision],async tx=>{
   const row=await tx.roster60Battle.findFirst({where:{id:b.battleId,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);
   if(row.status==='SETTLED')return dto(row); // same battle never awards twice, even with a new request ID
   if(row.revision!==b.revision)return fail('STALE_REVISION');if(row.status!=='FINISHED')return fail('BATTLE_NOT_FINISHED');
   const state=JSON.parse(row.state) as State,snap=JSON.parse(row.snapshot);
   const xp=state.outcome==='win'?snap.encounter.winXp:state.outcome==='loss'?snap.encounter.lossXp:0;
   const owned=await tx.roster60Owned.findFirst({where:{id:row.ownedId,playerId,status:'BATTLE'}});if(!owned)return fail('UNIT_UNAVAILABLE');
   if(owned.totalXp.toString()!==snap.startingXp)return fail('UNIT_STATE_CHANGED');
   const nextXp=owned.totalXp+BigInt(xp);if(nextXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
   try{growthAt(Number(nextXp),snap.growth,owned.reincarnation);}catch(e){return fail((e as Error).message);}
   const settlement={outcome:state.outcome,xpAwarded:xp,totalXp:nextXp.toString(),policyVersion:snap.policy.version};
   const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'FINISHED'},data:{status:'SETTLED',revision:{increment:1},settlement:JSON.stringify(settlement)}});if(changed.count!==1)return fail('STALE_REVISION');
   const released=await tx.roster60Owned.updateMany({where:{id:owned.id,playerId,status:'BATTLE',totalXp:owned.totalXp},data:{status:'AVAILABLE',totalXp:nextXp}});if(released.count!==1)return fail('UNIT_STATE_CHANGED');
   await tx.player.update({where:{id:playerId},data:{revision:{increment:1}}});
   if(state.outcome==='win'&&snap.encounter.tutorial)await verifyRosterTutorial(tx,playerId,row.id);
   return dto({...row,revision:row.revision+1,status:'SETTLED',settlement:JSON.stringify(settlement)});
  });
 }
}
