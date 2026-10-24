import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID} from 'node:crypto';import {readFileSync} from 'node:fs';
import {RecruitSessions,halfRecruitCost,type RecruitSessionPolicy} from '../src/roster60-recruit-session.js';import {RosterOnboarding} from '../src/roster60-onboarding.js';
it('half cost needs explicit odd rounding and rejects invalid amounts',()=>{expect(halfRecruitCost(30,null)).toBe(15);expect(()=>halfRecruitCost(3,null)).toThrow();expect(halfRecruitCost(3,'ceil')).toBe(2);expect(halfRecruitCost(3,'floor')).toBe(1);for(const v of [NaN,Infinity,-1,0,1.5])expect(()=>halfRecruitCost(v,null)).toThrow();});
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('candidate recruitment; fixture policy only',()=>{
 let db:PrismaClient,id:string,service:RecruitSessions;const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 const p:RecruitSessionPolicy={version:'TEST-NOT-PRODUCT',halfRounding:null,pity:'none',offers:[{kind:'LOW',label:'fixture',cost:2,weights:{COMMON:100}}]};
 const start=()=>({requestId:randomUUID(),kind:'LOW',catalogVersion:c.version});
 beforeAll(async()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});id=(await db.player.create({data:{identity:randomUUID(),credits:100}})).id;await new RosterOnboarding(db,()=>c).start(id);service=new RecruitSessions(db,()=>c,()=>p,()=>0);});
 afterAll(async()=>{await db.player.delete({where:{id}});await db.$disconnect();});
 it('policy absent rejects before charge; product is not silently activated',async()=>{const svc=new RecruitSessions(db,()=>c,()=>null);expect((await svc.info(id)).enabled).toBe(false);await expect(svc.start(id,start())).rejects.toMatchObject({code:'RECRUIT_SESSION_POLICY_PENDING'});});
 it('start grants nothing, duplicate fixed, same-grade reroll once, claim exactly once',async()=>{
 const initial=await db.roster60Owned.count({where:{playerId:id}}),b=start();const s=await service.start(id,b);expect(await service.start(id,b)).toEqual(s);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(initial);
 const r={requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'reroll' as const};const [a,replay]=await Promise.all([service.action(id,r),service.action(id,r)]);expect(replay).toEqual(a);expect(a.candidate.rarity).toBe(s.candidate.rarity);expect(a.credits).toBe(97);expect(a.rerolls).toBe(1);
 await expect(service.action(id,{...r,requestId:randomUUID(),revision:1})).rejects.toMatchObject({code:'RECRUIT_REROLL_USED'});
 await expect(service.action('not-owner',{...r,requestId:randomUUID()})).rejects.toMatchObject({code:'RECRUIT_SESSION_NOT_FOUND'});
 const claim={...r,requestId:randomUUID(),revision:1,action:'claim' as const};const granted=await service.action(id,claim);expect(await service.action(id,claim)).toEqual(granted);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(initial+1);
 await expect(service.action(id,{...claim,action:'reroll'})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
 const row=await db.roster60RecruitSession.findUniqueOrThrow({where:{id:s.sessionId}});expect(JSON.parse(row.snapshot).original).toEqual(s.candidate);
 });
 it('insufficient reroll rolls back state and candidate, reconnect restores; claim/reroll race commits only one',async()=>{
 const s=await service.start(id,start());await db.player.update({where:{id},data:{credits:0}});
 const r={requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'reroll' as const};await expect(service.action(id,r)).rejects.toMatchObject({code:'INSUFFICIENT_CREDITS'});
 expect((await service.info(id)).sessions[0]).toMatchObject({revision:0,rerolls:0,candidate:s.candidate});await db.player.update({where:{id},data:{credits:10}});
 const results=await Promise.allSettled([service.action(id,r),service.action(id,{...r,requestId:randomUUID(),action:'claim'})]);expect(results.filter(x=>x.status==='fulfilled')).toHaveLength(1);
 const row=await db.roster60RecruitSession.findUniqueOrThrow({where:{id:s.sessionId}});if(row.status==='PENDING')await service.action(id,{...r,requestId:randomUUID(),revision:1,action:'claim'});
 });
 it('random failure rolls back debit and once flag; repeated character is allowed',async()=>{
 const s=await service.start(id,start()),before=(await db.player.findUniqueOrThrow({where:{id}})).credits;
 const r={requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'reroll' as const};await expect(new RecruitSessions(db,()=>c,()=>p,()=>{throw Error('rng failure');}).action(id,r)).rejects.toThrow('rng failure');
 expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(before);const result=await service.action(id,r);expect(result.candidate.speciesId).toBe(s.candidate.speciesId);await service.action(id,{...r,requestId:randomUUID(),revision:1,action:'claim'});
 });
 it('concurrent fresh starts leave one pending round, claim read survives reconnect',async()=>{
  await db.player.update({where:{id},data:{credits:30}});
  const results=await Promise.allSettled([service.start(id,start()),service.start(id,start())]);expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);
  const sessions=(await service.info(id)).sessions;expect(sessions).toHaveLength(1);expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(28);
  const s=sessions[0];const claim=await service.action(id,{requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'claim'});expect(await service.read(id,s.sessionId)).toMatchObject({status:'CLAIMED',ownedId:claim.ownedId});
 });
 it('epic remains epic with inherited grant growth; no upward or downward grade roll',async()=>{
  const epic=new RecruitSessions(db,()=>c,()=>({...p,offers:[{kind:'MID',label:'fixture epic',cost:2,weights:{EPIC:100}}]}),()=>0);
  const s=await epic.start(id,{...start(),kind:'MID'});expect(s.candidate).toMatchObject({rarity:'EPIC',level:15});
  const next=await epic.action(id,{requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'reroll'});expect(next.candidate.rarity).toBe('EPIC');
  const claim=await epic.action(id,{requestId:randomUUID(),sessionId:s.sessionId,revision:1,action:'claim'});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:claim.ownedId}})).totalXp).toBe(BigInt(s.candidate.totalXp));
 });
 it('unapproved odd rounding and disallowed top grade fail without charging',async()=>{
  const before=(await db.player.findUniqueOrThrow({where:{id}})).credits;
  const odd=new RecruitSessions(db,()=>c,()=>({...p,offers:[{...p.offers[0],cost:3}]}));await expect(odd.start(id,start())).rejects.toMatchObject({code:'RECRUIT_ROUNDING_PENDING'});
  const top=new RecruitSessions(db,()=>c,()=>({...p,offers:[{...p.offers[0],weights:{ETERNAL:100}}]}));await expect(top.start(id,start())).rejects.toMatchObject({code:'RECRUIT_POLICY_INVALID'});
  expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(before);
 });

});
