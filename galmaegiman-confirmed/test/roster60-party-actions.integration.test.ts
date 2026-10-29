import {describe,it,expect,beforeAll,afterAll} from 'vitest';import {PrismaClient} from '@prisma/client';import {readFileSync,mkdtempSync,writeFileSync,rmSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';import {randomUUID} from 'node:crypto';import Fastify from 'fastify';
import {registerRoster60} from '../src/roster60-routes.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authenticated sequential party actions on isolated DB',()=>{
 let db:PrismaClient,app:ReturnType<typeof Fastify>,root:string,player:string,other:string;const prior=process.env.ROSTER60_PROFILE;
 const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8')),p=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
 async function post(path:string,body:any,who=player){return app.inject({method:'POST',url:'/my/api/native/roster60/battle/'+path,headers:{authorization:who},payload:body});}
 async function owned(speciesId='u01',who=player){return db.roster60Owned.create({data:{playerId:who,speciesId,source:randomUUID()}});}
 async function create(reserveIds:string[]=[]){const u=await owned();const r=await post('create',{requestId:randomUUID(),ownedId:u.id,encounterId:'party-test',reserveIds});expect(r.statusCode,r.body).toBe(200);return r.json();}
 async function action(v:any,a:any){const b={requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:a};const r=await post('action',b);expect(r.statusCode,r.body).toBe(200);return {v:r.json(),b};}
 beforeAll(async()=>{delete process.env.ROSTER60_PROFILE;const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated only');db=new PrismaClient({datasources:{db:{url}}});player=(await db.player.create({data:{identity:randomUUID()}})).id;other=(await db.player.create({data:{identity:randomUUID()}})).id;root=mkdtempSync(join(tmpdir(),'party-api-'));const {mkdirSync}=await import('node:fs');mkdirSync(join(root,'data'));
 const enemy=c.characters.find((u:any)=>u.id==='u07');enemy.combatGrowth[0].hp=10000;enemy.levelOneStats.hp=10000;enemy.combatGrowth[0].speed=1000;enemy.levelOneStats.speed=1000;enemy.attack.power=40;enemy.skills=[];
 p.encounters=[{id:'party-test',speciesId:'u07',level:1,tutorial:false,mode:'story',winXp:10,lossXp:7}];
 writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(c));writeFileSync(join(root,'data/roster60-battle-policy.json'),JSON.stringify(p));app=Fastify();registerRoster60(app,db,root,r=>[player,other].includes(String(r.headers.authorization))?String(r.headers.authorization):null,()=>true);
 });
 afterAll(async()=>{if(prior===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=prior;await app.close();await db.playerItem.deleteMany({where:{playerId:{in:[player,other]}}});await db.player.deleteMany({where:{id:{in:[player,other]}}});await db.$disconnect();rmSync(root,{recursive:true,force:true});});
 it('locks three unique owned units; rejects duplicate, fourth, foreign, locked and competing parties',async()=>{
  const ids=[];for(let i=0;i<2;i++)ids.push((await owned('u03')).id);const v=await create(ids);expect(v.state.reserves).toHaveLength(2);expect(await db.roster60Owned.count({where:{id:{in:ids},status:'BATTLE'}})).toBe(2);
  const base=await owned();for(const list of [[base.id],[(await owned('u03',other)).id],ids])expect((await post('create',{requestId:randomUUID(),ownedId:base.id,encounterId:'party-test',reserveIds:list})).statusCode).toBeGreaterThanOrEqual(400);
  const locked=await owned();await db.roster60Owned.update({where:{id:locked.id},data:{locked:true}});expect((await post('create',{requestId:randomUUID(),ownedId:base.id,encounterId:'party-test',reserveIds:[locked.id]})).json().error).toBe('UNIT_UNAVAILABLE');
  expect((await post('create',{requestId:randomUUID(),ownedId:base.id,encounterId:'party-test',reserveIds:[...ids,locked.id]})).statusCode).toBe(400);
 });
 it('switch precedes faster enemy, consumes own action, preserves HP/MP and fixed receipt/reconnect',async()=>{
  const r=await owned('u03');let v=await create([r.id]);const original=v.state.fighters[0],enemyHp=v.state.fighters[1].hp;
  const out=await action(v,{kind:'switch',targetOwnedId:r.id});v=out.v;expect(v.state.fighters[0].ownedId).toBe(r.id);expect(v.state.reserves[0].hp).toBe(original.hp);expect(v.state.fighters[1].hp).toBe(enemyHp);expect(v.state.fighters[0].hp).toBeLessThan(v.state.fighters[0].stats.hp);expect(v.state.events[0].events[0].kind).toBe('switch');
  expect((await post('action',out.b)).json()).toEqual(v);expect((await post('read',{battleId:v.battleId})).json()).toEqual(v);
  expect((await post('action',{...out.b,requestId:randomUUID()})).json().error).toBe('STALE_REVISION');expect((await post('action',{...out.b,requestId:randomUUID(),revision:v.revision,action:{kind:'switch',targetOwnedId:r.id}})).json().error).toBe('INVALID_SWITCH');
 });
 it('uses more than three real items; rejects full HP/wrong owner/empty inventory and preserves duplicate deduction',async()=>{
  await db.playerItem.upsert({where:{playerId_itemId:{playerId:player,itemId:'potion'}},create:{playerId:player,itemId:'potion',count:5},update:{count:5}});let v=await create();
  expect((await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:0,action:{kind:'item',itemId:'potion'}})).json().error).toBe('HP_FULL');v=(await action(v,{kind:'attack'})).v;
  for(let i=0;i<5;i++){const used=await action(v,{kind:'item',itemId:'potion'});v=used.v;expect(v.state.events.at(-1).events[0].kind).toBe('item');expect((await post('action',used.b)).json()).toEqual(v);expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:player,itemId:'potion'}}})).count).toBe(4-i);}
  expect((await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'item',itemId:'potion'}})).json().error).toBe('ITEM_UNAVAILABLE');
  expect((await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'focus'}},other)).statusCode).toBe(404);
 });
 it('waits for explicit free forced switch and retries cannot advance the turn',async()=>{
  const reserve=await owned('u03');let v=await create([reserve.id]);const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}});const s=JSON.parse(row.state);s.fighters[0].hp=0;await db.roster60Battle.update({where:{id:v.battleId},data:{state:JSON.stringify(s)}});
  v=(await post('read',{battleId:v.battleId})).json();expect(v.disabledCommands.switch).toBe('');expect(v.disabledCommands.item).toBe('FORCED_SWITCH_REQUIRED');expect((await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'focus'}})).json().error).toBe('FORCED_SWITCH_REQUIRED');
  const before=v.state.fighters[1];const switched=await action(v,{kind:'switch',targetOwnedId:reserve.id});expect(switched.v.state.turn).toBe(v.state.turn);expect(switched.v.state.fighters[0].hp).toBe(switched.v.state.fighters[0].stats.hp);expect(switched.v.state.fighters[1]).toEqual(before);expect((await post('action',switched.b)).json()).toEqual(switched.v);expect((await post('action',{...switched.b,requestId:randomUUID()})).json().error).toBe('STALE_REVISION');
  expect((await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:switched.v.revision,action:{kind:'switch',targetOwnedId:s.fighters[0].ownedId}})).json().error).toBe('INVALID_SWITCH');
 });
 it('surrender immediately loses with zero XP despite loss reward, releases entire party once',async()=>{
  const reserve=await owned('u03');let v=await create([reserve.id]);const initial=v.state.fighters[0].ownedId;v=(await action(v,{kind:'escape'})).v;expect(v.status).toBe('FINISHED');expect(v.state.outcome).toBe('loss');expect(v.state.turn).toBe(0);
  const b={requestId:randomUUID(),battleId:v.battleId,revision:v.revision};const settled=(await post('settle',b)).json();expect(settled.settlement.xpAwarded).toBe(0);expect((await post('settle',b)).json()).toEqual(settled);expect(await db.roster60Owned.count({where:{id:{in:[initial,reserve.id]},status:'AVAILABLE',totalXp:0}})).toBe(2);
 });
});
