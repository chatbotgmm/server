import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID} from 'node:crypto';import {readFileSync} from 'node:fs';
import {RecruitSessions,type RecruitSessionPolicy} from '../src/roster60-recruit-session.js';import {RosterOnboarding} from '../src/roster60-onboarding.js';
// 실제 배포 파일(data/roster60-recruit-policy.json): 사용자 승인 비용·확률·새로고침과 모집권 차감이 정확한지 확인한다.
const policy=JSON.parse(readFileSync('data/roster60-recruit-policy.json','utf8')) as RecruitSessionPolicy;
it('approved policy: costs 2/6/10, reroll half 1/3/5, old odds by grade order, no pity',()=>{
 expect(policy.pity).toBe('none');expect(policy.halfRounding).toBeNull();
 expect(policy.offers.map(o=>[o.kind,o.cost])).toEqual([['LOW',2],['MID',6],['HIGH',10]]);
 expect(policy.offers.map(o=>o.weights)).toEqual([{COMMON:70,UNCOMMON:30},{COMMON:20,UNCOMMON:50,RARE:30},{COMMON:10,UNCOMMON:15,RARE:54,EPIC:20,LEGEND:1}]);
 for(const o of policy.offers)expect(Object.values(o.weights).reduce((a,b)=>a+b,0)).toBe(100);
});
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('approved recruit policy file on isolated DB',()=>{
 let db:PrismaClient,id:string;const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 beforeAll(async()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});id=(await db.player.create({data:{identity:randomUUID(),credits:30}})).id;await new RosterOnboarding(db,()=>c).start(id);});
 afterAll(async()=>{await db.player.delete({where:{id}});await db.$disconnect();});
 it('info shows the same costs the server charges; start and reroll debit exactly once',async()=>{
  const service=new RecruitSessions(db,()=>c,()=>policy,()=>0);
  const info=await service.info(id);expect(info.enabled).toBe(true);
  expect(info.offers.map((o:any)=>[o.kind,o.cost,o.rerollCost])).toEqual([['LOW',2,1],['MID',6,3],['HIGH',10,5]]);
  const credits=async()=>(await db.player.findUniqueOrThrow({where:{id}})).credits;
  const before=await credits(),start={requestId:randomUUID(),kind:'MID',catalogVersion:c.version};
  const [a,b]=await Promise.all([service.start(id,start),service.start(id,start)]);expect(a).toEqual(b);
  expect(await credits()).toBe(before-6);expect(a.rerollCost).toBe(3);
  const reroll={requestId:randomUUID(),sessionId:a.sessionId,revision:a.revision,action:'reroll' as const};
  const [r1,r2]=await Promise.all([service.action(id,reroll),service.action(id,reroll)]);expect(r1).toEqual(r2);
  expect(await credits()).toBe(before-6-3);
  await service.action(id,{requestId:randomUUID(),sessionId:a.sessionId,revision:r1.revision,action:'claim'});
  const high=await service.start(id,{requestId:randomUUID(),kind:'HIGH',catalogVersion:c.version});expect(await credits()).toBe(before-6-3-10);expect(high.rerollCost).toBe(5);
 });
});
