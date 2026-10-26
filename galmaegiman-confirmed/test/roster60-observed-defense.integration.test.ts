import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {writeFileSync} from 'node:fs';
import {RosterBattle} from '../src/roster60-battle.js';
import {RosterPvp} from '../src/roster60-pvp.js';
import {RosterLibrary} from '../src/roster60-library.js';
import {skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('account observed reactions commit with actual battle transitions (isolated SQLite)',()=>{
 let db:PrismaClient;const players:string[]=[];
 const makeUnit=(id:string)=>({id,name:id==='u01'?'갈매미맨':id,rarity:'COMMON',stats:{hp:1000,mp:100,durability:0,statusResistance:0,speed:id==='u01'?30:10},
  levelOneStats:{hp:1000,mp:100,durability:0,statusResistance:0,speed:id==='u01'?30:10},
  combatGrowth:[{hp:1000,mp:100,durability:0,statusResistance:0,speed:id==='u01'?30:10}],growth:[{totalXp:0,maxMp:100}],
  attack:{id:'basic',name:'basic',element:'NORMAL',mpCost:0,power:1},
  skills:skillElements.filter(e=>e!=='NORMAL').slice(0,6).map(element=>({id:element,name:element,element,mpCost:0,power:1})),
  suitability:Object.fromEntries(skillElements.map(e=>[e,0])),defense:Object.fromEntries(skillElements.map(e=>[e,'normal']))});
 function environment(){
  const catalog:any={approved:true,statSchema:'gmm-five-stats-v1',version:'observation-test',characters:[makeUnit('u01'),makeUnit('u02'),makeUnit('u03')]};
  Object.assign(catalog.characters[1].defense,{NORMAL:'normal',FIRE:'weak',WATER:'resist',LIGHTNING:'immune',ICE:'reflect'});
  const policy:any={approved:true,version:'observation-test',catalogVersion:catalog.version,calculation:{model:'linear-level-hyperbolic-v1',version:'test',levelSlope:0,durabilityScale:100,resistanceScale:100},reflection:'return-post-durability',enemyStrategy:'first-affordable',maxRounds:100,
   encounters:[{id:'observe',speciesId:'u02',level:1,tutorial:false,mode:'story',winXp:0,lossXp:0}]};
  return {catalog,policy,service:new RosterBattle(db,()=>catalog,()=>policy),library:new RosterLibrary(db,()=>catalog)};
 }
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{
  await db.roster60PvpMatch.deleteMany({where:{OR:[{player0Id:{in:players}},{player1Id:{in:players}}]}});
  await db.roster60PvpQueue.deleteMany({where:{playerId:{in:players}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:players}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:players}}});await db.player.deleteMany({where:{id:{in:players}}});await db.$disconnect();
 });
 async function account(){const p=await db.player.create({data:{identity:randomUUID(),credits:40,snack:3,peanut:4}});players.push(p.id);return p.id;}
 async function owned(playerId:string,speciesId='u01'){return db.roster60Owned.create({data:{playerId,speciesId,source:randomUUID()}});}
 async function create(e:ReturnType<typeof environment>,playerId:string){const u=await owned(playerId);return await e.service.create(playerId,{requestId:randomUUID(),ownedId:u.id,encounterId:'observe'}) as any;}
 function action(e:ReturnType<typeof environment>,playerId:string,v:any,element='NORMAL',requestId=randomUUID()){
  return e.service.action(playerId,{requestId,battleId:v.battleId,revision:v.revision,action:element==='focus'?{kind:'focus'}:element==='NORMAL'?{kind:'attack'}:{kind:'skill',skillId:element}}) as Promise<any>;
 }
 const knowledge=(v:any)=>v.state.opponentIntel;
 async function collection(e:ReturnType<typeof environment>,id:string,species='u02'){return (await e.library.collection(id)).characters.find(u=>u.id===species)!;}
 it('start, polls and completed turns reveal no defenses; stats still reveal after turn one and own raw tables stay hidden',async()=>{
  const e=environment(),id=await account();let v=await create(e,id);
  expect(knowledge(v)).toMatchObject({version:'opponent-intel-v2',knownDefenseCount:0,statsKnown:false});
  expect(v.state.fighters.every((f:any)=>f.defense===undefined)).toBe(true);expect(v.state.fighters[0].suitability.NORMAL).toBe(0);
  for(let i=0;i<3;i++){v=await action(e,id,v,'focus');expect(knowledge(v).knownDefenseCount).toBe(0);expect(knowledge(v).statsKnown).toBe(true);}
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(0);
  const item=await collection(e,id);expect((item as any).defense).toBeUndefined();expect(item.defenseKnowledge.knownDefenseCount).toBe(0);
 });
 it('records only each executed attacking element including NORMAL/zero immune/reflect; reconnect, reencounter and collection persist for that account',async()=>{
  const e=environment(),id=await account(),other=await account();let v=await create(e,id);
  const expected={NORMAL:'normal',FIRE:'weak',WATER:'resist',LIGHTNING:'immune',ICE:'reflect'};
  for(const [element,reaction] of Object.entries(expected)){
   const before=knowledge(v).knownDefenseCount;v=await action(e,id,v,element);
   expect(knowledge(v).defenses[element]).toBe(reaction);expect(knowledge(v).knownDefenseCount).toBe(before+1);
   const event=v.state.events.at(-1).events.find((x:any)=>x.actor==='self'&&x.kind==='skill');
   expect(event.observedDefense).toEqual({speciesId:'u02',element,reaction});
   expect(event.presentation.opponentIntelBefore.knownDefenseCount).toBe(before);
   expect(event.presentation.opponentIntelAfter.knownDefenseCount).toBe(before+1);
   expect(event.presentation.opponentIntelBefore.defenses[element]).toBe('unknown');
  }
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(5);
  expect((await collection(e,other)).defenseKnowledge.knownDefenseCount).toBe(0);
  expect((await collection(e,id)).defenseKnowledge.defenses).toMatchObject({...expected,HOLY:'unknown'});
  const fresh=new RosterBattle(db,()=>e.catalog,()=>e.policy);expect(knowledge(await fresh.read(id,v.battleId))).toEqual(knowledge(v));
  const next=await create(e,id);expect(knowledge(next).knownDefenseCount).toBe(5);expect(knowledge(next).statsKnown).toBe(false);
  const serialized=JSON.stringify(v);for(const hidden of ['profileKey','targetKey','defenseObservation','"initial"','"profiles"','"seed"','"snapshot"'])expect(serialized).not.toContain(hidden);
  if(process.env.OBSERVED_DEFENSE_FIXTURE_DIR)writeFileSync(process.env.OBSERVED_DEFENSE_FIXTURE_DIR+'/pve-public.json',JSON.stringify({battle:v,collection:await collection(e,id)},null,2));
 });
 it('temporary profiles and provenance-free old battles show actual battle-only knowledge without poisoning base collection',async()=>{
  const e=environment(),id=await account();let v=await create(e,id);v=await action(e,id,v);
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}}),state=JSON.parse(row.state);state.fighters[1].defense.FIRE='reflect';
  await db.roster60Battle.update({where:{id:row.id},data:{state:JSON.stringify(state)}});
  v=await action(e,id,v,'FIRE');expect(knowledge(v)).toMatchObject({defenseKnowledgeScope:'battle-observed',knownDefenseCount:1,defenses:{FIRE:'reflect',NORMAL:'unknown'}});
  expect((await collection(e,id)).defenseKnowledge).toMatchObject({knownDefenseCount:1,defenses:{NORMAL:'normal',FIRE:'unknown'}});
  const changed=await db.roster60Battle.findUniqueOrThrow({where:{id:row.id}}),replacement=JSON.parse(changed.state);replacement.fighters[1].id='replacement-instance';
  await db.roster60Battle.update({where:{id:row.id},data:{state:JSON.stringify(replacement)}});
  expect(knowledge(await e.service.read(id,row.id)).knownDefenseCount).toBe(0);
  let old=await create(e,id);const saved=await db.roster60Battle.findUniqueOrThrow({where:{id:old.battleId}}),snap=JSON.parse(saved.snapshot);delete snap.observedDefense;
  await db.roster60Battle.update({where:{id:saved.id},data:{snapshot:JSON.stringify(snap)}});
  old=await action(e,id,old,'WATER');expect(knowledge(old)).toMatchObject({defenseKnowledgeScope:'battle-observed',knownDefenseCount:1,defenses:{WATER:'resist',NORMAL:'unknown'}});
  expect((await collection(e,id)).defenseKnowledge.defenses.WATER).toBe('unknown');
 });
 it('catalog defense changes invalidate persistent lookup and cannot backfill an old snapshot profile',async()=>{
  const e=environment(),id=await account();let v=await create(e,id);v=await action(e,id,v,'FIRE');
  e.catalog.characters[1].defense.FIRE='immune';
  expect((await collection(e,id)).defenseKnowledge.knownDefenseCount).toBe(0);
  v=await action(e,id,v,'WATER');expect(knowledge(v).defenseKnowledgeScope).toBe('battle-observed');
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(1);
  const next=await create(e,id);expect(knowledge(next).knownDefenseCount).toBe(0);
 });
 it('receipt retries, stale/foreign/invalid/MP failures and two-element same-revision races cannot invent knowledge',async()=>{
  const e=environment(),id=await account(),other=await account();let v=await create(e,id);const requestId=randomUUID();
  const first=await action(e,id,v,'FIRE',requestId);expect(await action(e,id,v,'FIRE',requestId)).toEqual(first);
  await expect(action(e,other,v,'WATER')).rejects.toMatchObject({code:'BATTLE_NOT_FOUND'});
  await expect(action(e,id,v,'WATER')).rejects.toMatchObject({code:'STALE_REVISION'});
  await expect(action(e,id,first,'not-equipped')).rejects.toMatchObject({code:'SKILL_NOT_EQUIPPED'});
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}}),state=JSON.parse(row.state);state.fighters[0].mp=0;state.fighters[0].skills.find((s:any)=>s.id==='HOLY').mpCost=1;
  await db.roster60Battle.update({where:{id:row.id},data:{state:JSON.stringify(state)}});await expect(action(e,id,first,'HOLY')).rejects.toMatchObject({code:'INSUFFICIENT_MP'});
  const outcomes=await Promise.allSettled([action(e,id,first,'WATER'),action(e,id,first,'LIGHTNING')]);expect(outcomes.filter(x=>x.status==='fulfilled')).toHaveLength(1);
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(2);
 });
 it('knowledge failure rolls back battle CAS, HP/MP and request receipt; same request can retry successfully',async()=>{
  const e=environment(),id=await account(),v=await create(e,id),requestId=randomUUID();
  await db.$executeRawUnsafe("CREATE TRIGGER fail_observed_write BEFORE INSERT ON Roster60ObservedDefense BEGIN SELECT RAISE(ABORT,'observed-test-rollback'); END");
  try{await expect(action(e,id,v,'FIRE',requestId)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_observed_write');}
  expect(await e.service.read(id,v.battleId)).toEqual(v);expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(0);
  expect(await db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:id,requestId}}})).toBeNull();
  expect(knowledge(await action(e,id,v,'FIRE',requestId)).defenses.FIRE).toBe('weak');
 });
 it('a defeated or surrendered attacker never observes an unexecuted command',async()=>{
  const e=environment(),id=await account();e.catalog.characters[1].combatGrowth[0].speed=100;e.catalog.characters[1].attack.power=10000;
  let v=await create(e,id);v=await action(e,id,v,'FIRE');expect(v.state.outcome).toBe('loss');
  expect(v.state.events[0].events.some((event:any)=>event.actor==='self'&&event.kind==='skill')).toBe(false);
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(0);
  const surrender=await create(e,id);await e.service.action(id,{requestId:randomUUID(),battleId:surrender.battleId,revision:0,action:{kind:'escape'}});
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(0);
 });
 it('concurrent separate battles preserve two elements and account deletion cascades knowledge',async()=>{
  const e=environment(),id=await account(),one=await create(e,id),two=await create(e,id);
  await Promise.all([action(e,id,one,'FIRE'),action(e,id,two,'WATER')]);
  expect((await collection(e,id)).defenseKnowledge).toMatchObject({knownDefenseCount:2,defenses:{FIRE:'weak',WATER:'resist'}});
  await db.player.delete({where:{id}});expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(0);
 });
 it('old cached receipts cannot re-expose random v1 defense knowledge or raw own tables',async()=>{
  const e=environment(),id=await account(),v=await create(e,id),requestId=randomUUID(),result=await action(e,id,v,'FIRE',requestId);
  const cached=structuredClone(result);cached.state.fighters[0].defense={...e.catalog.characters[0].defense};
  Object.assign(cached.state.opponentIntel,{version:'opponent-intel-v1',defenses:{...e.catalog.characters[1].defense},knownDefenseCount:8});
  await db.roster60BattleReceipt.update({where:{playerId_requestId:{playerId:id,requestId}},data:{result:JSON.stringify(cached)}});
  const replay=await action(e,id,v,'FIRE',requestId);expect(replay.state.fighters[0].defense).toBeUndefined();
  expect(knowledge(replay)).toMatchObject({version:'opponent-intel-v2',knownDefenseCount:0,defenseKnowledgeScope:'unverified'});
  expect(Object.values(knowledge(replay).defenses).every(value=>value==='unknown')).toBe(true);
  expect(await db.roster60ObservedDefense.count({where:{playerId:id}})).toBe(1);
 });
 async function pair(e:ReturnType<typeof environment>){
  const a=await account(),b=await account(),pvp=new RosterPvp(db,()=>e.catalog,()=>e.policy);
  await pvp.party(a,[(await owned(a)).id]);await pvp.party(b,[(await owned(b,'u02')).id]);
  await pvp.join(a,{requestId:randomUUID(),mode:'normal'});const matched:any=await pvp.join(b,{requestId:randomUUID(),mode:'normal'}),battleId=matched.battle.battleId;
  await pvp.accept(a,{requestId:randomUUID(),battleId,revision:0,accept:true});const v:any=await pvp.accept(b,{requestId:randomUUID(),battleId,revision:0,accept:true});
  return {a,b,pvp,battleId,revision:v.revision};
 }
 it('PvP stores only the attacking account observation; pending/replay and opposing view reveal no other account map',async()=>{
  const e=environment(),p=await pair(e),request={requestId:randomUUID(),battleId:p.battleId,revision:p.revision,action:{kind:'skill' as const,skillId:'FIRE'}};
  const pending=await p.pvp.action(p.a,request);expect(await p.pvp.action(p.a,request)).toEqual(pending);expect(await db.roster60ObservedDefense.count({where:{playerId:p.a}})).toBe(0);
  await p.pvp.action(p.b,{...request,requestId:randomUUID(),action:{kind:'focus'}});
  const own:any=(await p.pvp.current(p.a) as any).battle,other:any=(await p.pvp.current(p.b) as any).battle;
  expect(knowledge(own).defenses.FIRE).toBe('weak');expect(knowledge(other).knownDefenseCount).toBe(0);
  expect(await db.roster60ObservedDefense.count({where:{playerId:p.a}})).toBe(1);expect(await db.roster60ObservedDefense.count({where:{playerId:p.b}})).toBe(0);
  expect(other.state.events.flatMap((r:any)=>r.events).every((x:any)=>!x.observedDefense)).toBe(true);
  const event=own.state.events.flatMap((r:any)=>r.events).find((x:any)=>x.actor==='self'&&x.kind==='skill');
  expect(event.presentation.opponentIntelBefore.defenses.FIRE).toBe('unknown');expect(event.presentation.opponentIntelAfter.defenses.FIRE).toBe('weak');
  expect(JSON.stringify(other)).not.toContain('targetKey');expect(JSON.stringify(other)).not.toContain('profileKey');
  await expect(p.pvp.action(await account(),{...request,requestId:randomUUID(),revision:own.revision})).rejects.toMatchObject({code:'BATTLE_NOT_FOUND'});
  if(process.env.OBSERVED_DEFENSE_FIXTURE_DIR)writeFileSync(process.env.OBSERVED_DEFENSE_FIXTURE_DIR+'/pvp-public.json',JSON.stringify({attacker:own,other},null,2));
 });
 it('PvP observation, resolved round CAS and second submission receipt roll back together',async()=>{
  const e=environment(),p=await pair(e),base={battleId:p.battleId,revision:p.revision};
  await p.pvp.action(p.a,{...base,requestId:randomUUID(),action:{kind:'skill',skillId:'FIRE'}});const request={...base,requestId:randomUUID(),action:{kind:'focus' as const}};
  await db.$executeRawUnsafe("CREATE TRIGGER fail_pvp_observed BEFORE INSERT ON Roster60ObservedDefense BEGIN SELECT RAISE(ABORT,'pvp-observed-test-rollback'); END");
  try{await expect(p.pvp.action(p.b,request)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_pvp_observed');}
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:p.battleId}});expect(JSON.parse(row.state).turn).toBe(0);expect(row.revision).toBe(p.revision);
  expect(await db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:p.b,requestId:request.requestId}}})).toBeNull();
  await p.pvp.action(p.b,request);expect(await db.roster60ObservedDefense.count({where:{playerId:p.a}})).toBe(1);
 });
});
