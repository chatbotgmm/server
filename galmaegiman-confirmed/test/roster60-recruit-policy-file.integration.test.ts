import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID,createHmac} from 'node:crypto';import {readFileSync} from 'node:fs';
import {RecruitSessions,recruitWeightTickets,type RecruitSessionPolicy} from '../src/roster60-recruit-session.js';import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {buildApp} from '../src/app.js';import {GameService} from '../src/game.js';import {loadContent} from '../src/content.js';
// 실제 배포 파일(data/roster60-recruit-policy.json): 사용자 승인 비용·확률·새로고침과 모집권 차감이 정확한지 확인한다.
const policy=JSON.parse(readFileSync('data/roster60-recruit-policy.json','utf8')) as RecruitSessionPolicy;
it('approved policy: costs 2/6/10, reroll half 1/3/5, latest pools with 0.5% legendary/hidden, no pity',()=>{
 expect(policy.pity).toBe('none');expect(policy.halfRounding).toBeNull();
 expect(policy.offers.map(o=>[o.kind,o.cost])).toEqual([['LOW',2],['MID',6],['HIGH',10]]);
 expect(policy.offers.map(o=>o.weights)).toEqual([{COMMON:70,UNCOMMON:28,RARE:2},{UNCOMMON:70,RARE:28,EPIC:2},{RARE:90,EPIC:9,LEGEND:0.5,HIDDEN:0.5}]);
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
  expect(await db.player.findUniqueOrThrow({where:{id}})).toMatchObject({credits:30});
  expect(info.offers.map((o:any)=>[o.kind,o.cost,o.rerollCost])).toEqual([['LOW',2,1],['MID',6,3],['HIGH',10,5]]);
  const credits=async()=>(await db.player.findUniqueOrThrow({where:{id}})).credits;
  const before=await credits(),start={requestId:randomUUID(),kind:'MID',catalogVersion:c.version};
  const [a,b]=await Promise.all([service.start(id,start),service.start(id,start)]);expect(a).toEqual(b);
  expect(await credits()).toBe(before-6);expect(a.rerollCost).toBe(3);
  const reroll={requestId:randomUUID(),sessionId:a.sessionId,revision:a.revision,action:'reroll' as const};
  const [r1,r2]=await Promise.all([service.action(id,reroll),service.action(id,reroll)]);expect(r1).toEqual(r2);
  expect(await credits()).toBe(before-6-3);
  await service.action(id,{requestId:randomUUID(),sessionId:a.sessionId,revision:r1.revision,action:'claim'});
  const high=await new RecruitSessions(db,()=>c,()=>policy,max=>max===10000?9000:0).start(id,{requestId:randomUUID(),kind:'HIGH',catalogVersion:c.version});expect(await credits()).toBe(before-6-3-10);expect(high.rerollCost).toBe(5);expect(high.candidate.rarity).toBe('EPIC');await service.action(id,{requestId:randomUUID(),sessionId:high.sessionId,revision:0,action:'claim'});
 });
 it('authenticated session reports the configured profile costs and weights without a debit',async()=>{
  const previous=process.env.ROSTER60_PROFILE,secret='isolated-policy-auth-fixture-only';
  process.env.ROSTER60_PROFILE='five-stat-device-test-v1';
  const app=await buildApp(db,new GameService(db,loadContent()),{secret,baseUrl:async()=>'https://example.com'});
  try{
   const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`;
   const cookie=`gmm_s=${v}.${createHmac('sha256',key).update(v).digest('base64url')}`;
   const before=(await db.player.findUniqueOrThrow({where:{id}})).credits;
   const response=await app.inject({method:'GET',url:'/my/api/native/roster60/recruit/session',headers:{cookie,'x-gmm':'1'}});
   expect(response.statusCode).toBe(200);const body=response.json();expect(body.enabled).toBe(true);
   expect(body.offers.map((o:any)=>[o.cost,o.rerollCost,o.weights])).toEqual(policy.offers.map(o=>[o.cost,o.cost/2,o.weights]));
   expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(before);
  }finally{await app.close();if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;}
 });
 it('all 0.5% boundaries preserve balance when grant level is not approved',async()=>{
  for(const ticket of [0,8999,9900,9949,9950,9999]){
   const before=await db.player.findUniqueOrThrow({where:{id}});
   const service=new RecruitSessions(db,()=>c,()=>policy,max=>max===10000?ticket:0);
   await expect(service.start(id,{requestId:randomUUID(),kind:'HIGH',catalogVersion:c.version})).rejects.toMatchObject({code:'RECRUIT_LEVEL_POLICY_PENDING'});
   expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(before.credits);
   expect(await db.roster60RecruitSession.count({where:{playerId:id,status:'PENDING'}})).toBe(0);
  }
 });
 it('rejects disallowed pools and invalid percentages before changing balances',async()=>{
  const invalid:Record<string,number>[]=[{COMMON:100},{LIMITED:100},{TRANSCEND:100},{IMMORTAL:100},{ETERNAL:100},{RARE:99},{RARE:-1,EPIC:101},{RARE:NaN}];
  for(const weights of invalid){
   const bad={...policy,offers:[{kind:'HIGH',label:'invalid',cost:10,weights}]};
   const service=new RecruitSessions(db,()=>c,()=>bad as RecruitSessionPolicy,()=>0);
   const before=(await db.player.findUniqueOrThrow({where:{id}})).credits;
   await expect(service.info(id)).rejects.toMatchObject({code:'RECRUIT_POLICY_INVALID'});
   await expect(service.start(id,{requestId:randomUUID(),kind:'HIGH',catalogVersion:c.version})).rejects.toMatchObject({code:'RECRUIT_POLICY_INVALID'});
   expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(before);
  }
 });

});

it('basis-point distribution is exact, including half-percent bands',()=>{
 expect(recruitWeightTickets(policy.offers[2].weights)).toEqual([['RARE',9000],['EPIC',900],['LEGEND',50],['HIDDEN',50]]);
 for(const offer of policy.offers){const counts:Record<string,number>={};for(let ticket=0;ticket<10000;ticket++){let n=ticket;for(const [grade,w] of recruitWeightTickets(offer.weights)){if(n<w){counts[grade]=(counts[grade]??0)+1;break;}n-=w;}}expect(counts).toEqual(Object.fromEntries(Object.entries(offer.weights).map(([g,w])=>[g,w*100])));}
 const invalidWeights:Record<string,number>[]=[{RARE:99.999,HIDDEN:.001},{RARE:Infinity},{RARE:0,EPIC:100}];
 for(const weights of invalidWeights)expect(()=>recruitWeightTickets(weights)).toThrow();
});
