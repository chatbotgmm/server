import {beforeAll,afterAll,it,expect,describe} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID,createHmac} from 'node:crypto';import {readFileSync} from 'node:fs';
import {buildApp} from '../src/app.js';import {GameService} from '../src/game.js';import {loadContent} from '../src/content.js';
import {instanceGrowthCurve} from '../src/battle/roster60-rules.js';import {fiveRebirthStats} from '../src/battle/roster60-five-stats.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authorized test baseline through real product authentication and disposable DB',()=>{
 let db:PrismaClient,app:Awaited<ReturnType<typeof buildApp>>,id:string,cookie:string;const old=process.env.ROSTER60_PROFILE;
 const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 const definition=(species:string)=>c.characters.find((u:any)=>u.id===species);
 async function api(path:string,payload?:unknown){const r=await app.inject({method:payload===undefined?'GET':'POST',url:'/my/api/native/roster60/'+path,headers:{cookie,'x-gmm':'1'},...(payload===undefined?{}:{payload:payload as any})});expect(r.statusCode,r.body).toBe(200);return r.json();}
 beforeAll(async()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');db=new PrismaClient({datasources:{db:{url}}});
  id=(await db.player.create({data:{identity:randomUUID(),peanut:4,tutorial:9}})).id;
  const secret='isolated-baseline-auth-test-secret-only',key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`;
  cookie=`gmm_s=${v}.${createHmac('sha256',key).update(v).digest('base64url')}`;process.env.ROSTER60_PROFILE='five-stat-device-test-v1';app=await buildApp(db,new GameService(db,loadContent()),{secret,baseUrl:async()=>'https://example.com'});
 });
 afterAll(async()=>{if(old===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=old;await app.close();await db.player.delete({where:{id}});await db.$disconnect();});
 it('start → real tutorial battle/settlement → mail → permanent choice → craft → five rebirths',async()=>{
  expect((await app.inject({method:'GET',url:'/my/api/native/roster60/battle/options',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
  const started=await api('start',{});expect(started.starter.speciesId).toBe('u01');expect((await api('start',{})).starter.id).toBe(started.starter.id);
  let b=await api('battle/create',{requestId:randomUUID(),ownedId:started.starter.id,encounterId:'tutorial'});
  expect(b.state.fighters[0].mp).toBe(definition('u01').levelOneStats.mp);
  for(let i=0;i<100&&b.status==='ACTIVE';i++){
   const self=b.state.fighters[0],skill=self.skills.filter((s:any)=>s.mpCost<=self.mp).sort((a:any,b:any)=>b.power-a.power)[0];
   b=await api('battle/action',{requestId:randomUUID(),battleId:b.battleId,revision:b.revision,action:skill?{kind:'skill',skillId:skill.id}:{kind:'attack'}});
  }
  expect(b.state.outcome).toBe('win');const settled=await api('battle/settle',{requestId:randomUUID(),battleId:b.battleId,revision:b.revision});expect(settled.status).toBe('SETTLED');
  const completed=await api('complete',{});expect((await api('complete',{})).ticket.ticketId).toBe(completed.ticket.ticketId);
  const preview=await api('preview',{ticketId:completed.ticket.ticketId});expect(preview.characters).toHaveLength(7);
  const claim={ticketId:completed.ticket.ticketId,characterId:'u02',catalogVersion:c.version};const claimed=await api('confirm',claim);expect((await api('confirm',claim)).ticket.ownedId).toBe(claimed.ticket.ownedId);
  // Explicit accelerated progression fixtures ONLY in the disposable DB, no product grant/debug endpoint.
  const xp=definition('u02').growth[4].totalXp;await db.roster60Owned.update({where:{id:claimed.ticket.ownedId},data:{totalXp:xp}});
  const extra=await db.roster60Owned.create({data:{playerId:id,speciesId:'u02',totalXp:xp+1,source:'isolated-e2e-seed'}});
  const input={resultId:'u09',materialIds:[claimed.ticket.ownedId,extra.id],catalogVersion:c.version};const cp=await api('craft/preview',input);expect(cp.inheritedTotalXp).toBe(xp);
  const cr={...input,previewFingerprint:cp.previewFingerprint,requestId:randomUUID()};const crafted=await api('craft/confirm',cr);expect((await api('craft/confirm',cr)).ownedId).toBe(crafted.ownedId);
  const top=definition('u45'),owned=await db.roster60Owned.create({data:{playerId:id,speciesId:'u45',totalXp:top.growth[59].totalXp,source:'isolated-rebirth-seed'}});
  expect((await api('home')).rebirthUnlocked).toBe(true);
  for(let cycle=0;cycle<5;cycle++){
   const required=[60,70,80,90,99][cycle],curve=instanceGrowthCurve(top.growth,cycle);
   await db.roster60Owned.update({where:{id:owned.id},data:{totalXp:curve[required-1].totalXp}});
   const p=(await api('reincarnation/preview')).candidates.find((u:any)=>u.ownedId===owned.id);expect(p.eligible).toBe(true);expect(p.costPeanut).toBe(cycle?1:0);
   expect(p.preview.afterLevelOneStats).toEqual(fiveRebirthStats(top.levelOneStats,top.levelOneStats,cycle+1));
   const request={ownedId:owned.id,requestId:randomUUID(),catalogVersion:c.version,previewFingerprint:p.previewFingerprint};const result=await api('reincarnation/confirm',request);expect(await api('reincarnation/confirm',request)).toEqual(result);
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:owned.id}})).totalXp).toBe(0n);
  }
  expect((await db.player.findUniqueOrThrow({where:{id}})).peanut).toBe(0);
  expect((await api('reincarnation/preview')).candidates.find((u:any)=>u.ownedId===owned.id).reason).toBe('REINCARNATION_LIMIT');
  const reborn=await api('battle/create',{requestId:randomUUID(),ownedId:owned.id,encounterId:'training-1'});expect(reborn.state.fighters[0].stats).toEqual(fiveRebirthStats(top.levelOneStats,top.levelOneStats,5));
 });
});
