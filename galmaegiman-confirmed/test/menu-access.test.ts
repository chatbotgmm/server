import {afterEach,describe,it,expect,vi} from 'vitest';
import Fastify from 'fastify';
import {randomUUID} from 'node:crypto';
import {MenuAccess,menuFeatures} from '../src/menu-access.js';
import {registerRoster60} from '../src/roster60-routes.js';
import {RosterHome} from '../src/roster60-home.js';

afterEach(()=>vi.unstubAllEnvs());
const unavailable=new Proxy({},{get(){throw Error('OBSOLETE_MENU_QUERY');}});
function fixture(db:unknown=unavailable){
 const app=Fastify();
 registerRoster60(app,db as never,process.cwd(),r=>r.headers.authorization==='test-session'?'owner':null,(r,p)=>{
  if(r.headers['x-gmm']==='1')return true;
  p.code(403).send({error:'forbidden'});return false;
 },true); // The legacy opt-in cannot restore menu locks.
 return app;
}
const headers={'x-gmm':'1',authorization:'test-session'};

describe('menu entry without business locks (no DB)',()=>{
 it('returns all eight compatibility entries without configuration, ownership, catalog or level reads',async()=>{
  const catalog=vi.fn(()=>{throw Error('CATALOG_UNAVAILABLE');});
  const state=await new MenuAccess(unavailable as never,catalog,true).read('owner');
  expect(Object.keys(state.features)).toEqual(menuFeatures);
  expect(Object.values(state.features).every(f=>f.enabled&&f.mode==='auto'&&f.requiredLevel===null&&f.reason==='')).toBe(true);
  expect(state).toMatchObject({version:'menu-access-v1',enforcementEnabled:false,testDefaultLock:false,representativeLevel:null,levelPolicy:'NONE'});
  expect(catalog).not.toHaveBeenCalled();
 });
 it.each(['locked','open','invalid-old-setting'])('ignores obsolete default setting %s and preserves route authentication',async value=>{
  vi.stubEnv('GMM_TEST_MENU_DEFAULT',value);
  const app=fixture();
  try{
   expect((await app.inject({url:'/my/api/native/menu-access'})).statusCode).toBe(403);
   expect((await app.inject({url:'/my/api/native/menu-access',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
   const r=await app.inject({url:'/my/api/native/menu-access',headers});
   expect(r.statusCode,r.body).toBe(200);expect(r.headers['cache-control']).toBe('no-store');
   expect(Object.values(r.json().features).every((f:any)=>f.enabled)).toBe(true);
  }finally{await app.close();}
 });
 it('keeps strict action schemas after removing the common gate',async()=>{
  const app=fixture();
  try{
   for(const [path,payload] of [
    ['recruit/session/start',{times:0}],['recruit/session/claim-all',{sessions:[]}],
    ['craft/confirm',{}],['adventure/start',{}],['pvp/party',{ownedIds:[]}],
    ['reincarnation/confirm',{}],['battle/create',{adventureId:randomUUID()}],
    ['battle/action',{battleId:randomUUID(),revision:0,action:{kind:'forged'}}]
   ] as const){
    const r=await app.inject({method:'POST',url:'/my/api/native/roster60/'+path,headers,payload});
    expect(r.statusCode,path+':'+r.body).toBe(400);expect(r.json().error).toBe('INVALID_REQUEST');
   }
  }finally{await app.close();}
 });
 it('does not intercept legacy menu routes or read story saves to decide entry',async()=>{
  const app=fixture();
  for(const path of ['draw','craft','shop','daily','story','battle-team'])app.post('/my/api/'+path,async()=>({reached:true}));
  try{
   for(const path of ['draw','craft','shop','daily','story','battle-team']){
    const r=await app.inject({method:'POST',url:'/my/api/'+path,headers,payload:{data:{team:['changed']}}});
    expect(r.statusCode,r.body).toBe(200);expect(r.json()).toEqual({reached:true});
   }
  }finally{await app.close();}
 });
 it('retains owner-scoped battle lookup and does not hide actual action query errors',async()=>{
  const findFirst=vi.fn().mockResolvedValue(null),app=fixture({roster60Battle:{findFirst}}),battleId=randomUUID();
  const call=()=>app.inject({method:'POST',url:'/my/api/native/roster60/battle/read',headers,payload:{battleId}});
  try{
   const r=await call();expect(r.statusCode).toBe(404);expect(r.json().error).toBe('BATTLE_NOT_FOUND');
   expect(findFirst).toHaveBeenCalledWith({where:{id:battleId,playerId:'owner'}});
   findFirst.mockRejectedValueOnce(Error('ACTION_DB_UNAVAILABLE'));expect((await call()).statusCode).toBe(500);
  }finally{await app.close();}
 });
 it('retires administrative lock writes after strict input and admin authorization, without writes',async()=>{
  const player=vi.fn().mockResolvedValue({isAdmin:false,identity:'ordinary'});
  const db=new Proxy({player:{findUnique:player}},{get(target,key){if(key==='player')return target.player;throw Error('MENU_MUST_NOT_WRITE');}});
  const app=fixture(db),input={requestId:'retired-lock',targetPlayerId:'owner',featureId:'rebirth',mode:'force_lock',expectedRevision:0};
  const call=(payload:any)=>app.inject({method:'POST',url:'/my/api/admin/menu-access',headers,payload});
  try{
   expect((await call(input)).statusCode).toBe(403);
   player.mockResolvedValue({isAdmin:true,identity:'review:restricted'});expect((await call(input)).statusCode).toBe(403);
   player.mockResolvedValue({isAdmin:true,identity:'administrator'});
   expect((await call({...input,isAdmin:true})).statusCode).toBe(400);
   expect((await call({...input,featureId:'unknown'})).statusCode).toBe(400);
   const r=await call(input);expect(r.statusCode).toBe(410);expect(r.json().error).toBe('MENU_ACCESS_RETIRED');
   const read=await app.inject({url:'/my/api/admin/menu-access?playerId=owner',headers});
   expect(read.statusCode,read.body).toBe(200);expect(read.json().features.rebirth.enabled).toBe(true);
  }finally{await app.close();}
 });
 it('opens the home rebirth menu for missing/old locked state and keeps introduction completion idempotent',async()=>{
  const upsert=vi.fn().mockResolvedValue({}),findUnique=vi.fn().mockResolvedValue(null);
  const db={roster60Home:{findUnique,upsert},nativeInboxMessage:{findMany:async()=>[]},nativeNewsRead:{count:async()=>0},roster60State:{findUnique:async()=>null}};
  const home=new RosterHome(db as never,()=>{throw Error('CATALOG_UNAVAILABLE');});
  expect(await home.read('owner')).toMatchObject({rebirthUnlocked:true,rebirthIntroPending:true});
  findUnique.mockResolvedValue({rebirthUnlocked:false,rebirthIntroComplete:false});
  expect(await home.read('owner')).toMatchObject({rebirthUnlocked:true,rebirthIntroPending:true});
  expect(await home.completeIntro('owner')).toEqual({complete:true});expect(await home.completeIntro('owner')).toEqual({complete:true});
  expect(upsert).toHaveBeenLastCalledWith({where:{playerId:'owner'},create:{playerId:'owner',rebirthUnlocked:true,rebirthIntroComplete:true},update:{rebirthUnlocked:true,rebirthIntroComplete:true}});
  findUnique.mockResolvedValue({rebirthUnlocked:false,rebirthIntroComplete:true});
  expect(await home.read('owner')).toMatchObject({rebirthUnlocked:true,rebirthIntroPending:false});
 });
});
