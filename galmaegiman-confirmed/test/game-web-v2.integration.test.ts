// NEW recovery candidate against exact875; historical test results do not apply.
import {afterAll,beforeAll,describe,expect,it,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {createHmac,randomUUID} from 'node:crypto';
import * as crypto from 'node:crypto';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {createBattleService} from '../src/battle/service-factory.js';
import {loadBattleRules} from '../src/battle/load.js';
import {CURRENT_COMBAT_RULES_EPOCH} from '../src/battle/combat-epoch.js';
import {attrKeys} from '../src/battle/core.js';
vi.mock('node:crypto',async importActual=>{const actual=await importActual<typeof import('node:crypto')>();return {...actual,randomInt:vi.fn(actual.randomInt)};});
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('recovered /game V2 production API through authenticated Fastify',()=>{
 let db:PrismaClient,game:GameService,app:Awaited<ReturnType<typeof buildApp>>,id:string,cookie:string,api:(path:string,body?:object)=>Promise<any>;
 const secret='isolated-web-v2-recovery-secret'.repeat(2),requests:any[]=[];
 const payload={mode:'practice',roster:['D1'],enemyRoster:['T1'],level:1};
 const headers={'x-gmm':'1','x-gmm-augment-version':'2','x-gmm-combat-epoch':CURRENT_COMBAT_RULES_EPOCH};
 const seeds=new Map<string,number>();let reverseSeed=-1;
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('Only explicitly isolated /tmp DB permitted');
  db=new PrismaClient({datasources:{db:{url}}});game=new GameService(db,loadContent());
  id=(await db.player.create({data:{identity:`web-v2-recovery:${randomUUID()}`,tutorial:9}})).id;
  const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`;
  cookie=`gmm_s=${v}.${createHmac('sha256',key).update(v).digest('base64url')}`;
  app=await buildApp(db,game,{secret,baseUrl:async()=>'https://example.com'});
  const node={addEventListener(){},classList:{add(){},toggle(){}}};const window:any={};
  runInNewContext(readFileSync('public/game/game.js','utf8'),{window,document:{querySelector:()=>node,documentElement:node},location:{search:'',hash:'#battle'},URLSearchParams,addEventListener(){},localStorage:{getItem:()=>null},fetch:async(url:string,opt:any)=>{
   requests.push({url,...opt});expect(opt.credentials).toBe('same-origin');
   const r=await app.inject({method:opt.method,url,headers:{...opt.headers,cookie},...(opt.body===undefined?{}:{payload:opt.body})});
   return {status:r.statusCode,ok:r.statusCode<400,json:async()=>r.json()};
  }});api=window.GMM.api;
  const svc=createBattleService(loadBattleRules());
  const rec=(uid:string,unit:string)=>({uid,id:unit,name:unit,tier:'LIMITED' as const,level:1,xp:0,stage:0 as const,evoAugs:[]});
  for(let n=0;n<10000&&(seeds.size<3||reverseSeed<0);n++){
   const b=svc.create([rec('practice0','D1')],[rec('practice-foe0','T1')],n),offers=b.sides[0].offers!;
   seeds.set(b.R.battleAug.get(offers[0])!.tier!,n);if(offers.includes('reverse'))reverseSeed=n;
  }
  expect(seeds.size).toBe(3);expect(reverseSeed).toBeGreaterThanOrEqual(0);
 });
 afterAll(async()=>{vi.mocked(crypto.randomInt).mockRestore();if(app)await app.close();if(db){if(id)await game.webDeleteAccount(id);await db.$disconnect();}});
 function seed(n:number){vi.mocked(crypto.randomInt).mockImplementation(((max:number)=>max===2**31-1?n:0) as any);}
 const turn=(s:any,body:object)=>api('native/battle/turn',{session:s.session,revision:s.revision,...body});
 const noSecrets=(s:any)=>expect(JSON.stringify(s)).not.toMatch(/"(?:seed|sequence|rng|weights|serviceRules|augmentOffers)"\s*:/);
 it('served entrypoint uses modified source and mtime cache keys, not stale bundle',async()=>{
  const html=await app.inject({url:'/game'});expect(html.statusCode).toBe(200);expect(html.headers['cache-control']).toBe('no-cache');
  for(const file of ['game.js','battle.js','game.css']){
   const path=html.body.match(new RegExp(`/game/${file.replace('.', '\\.')}\\?v=\\d+`))?.[0];expect(path).toBeTruthy();
   const asset=await app.inject({url:path!});expect(asset.statusCode).toBe(200);expect(asset.body).toBe(readFileSync(`public/game/${file}`,'utf8'));expect(asset.headers['cache-control']).toBe('public, max-age=300');
  }
 });
 it('missing, old, malformed, future capabilities rejected; headers do not replace authentication',async()=>{
  for(const bad of [{},{'x-gmm-augment-version':'1'},{'x-gmm-augment-version':'02'},{'x-gmm-augment-version':'3'},{'x-gmm-augment-version':'2'},{'x-gmm-augment-version':'2','x-gmm-combat-epoch':'old'},{'x-gmm-augment-version':'2','x-gmm-combat-epoch':CURRENT_COMBAT_RULES_EPOCH+'-future'}]){
   const r=await app.inject({method:'POST',url:'/my/api/native/battle/start',headers:{'x-gmm':'1',cookie,...bad},payload});expect(r.statusCode,r.body).toBe(409);
  }
  expect((await app.inject({method:'POST',url:'/my/api/native/battle/start',headers,payload})).statusCode).toBe(401);
 });
 it('web GET/POST headers, all server tiers same sides, reroll and reconnect preserve server state',async()=>{
  for(const [tier,n] of seeds){seed(n);let s=await api('native/battle/start',payload);expect(s.combatRulesEpoch).toBe(CURRENT_COMBAT_RULES_EPOCH);expect(s.augmentVersion).toBe(2);
   expect(s.teams.flatMap((t:any)=>t.offers).every((o:any)=>o.tier===tier)).toBe(true);noSecrets(s);
   for(const bad of [{},{'x-gmm-augment-version':'1','x-gmm-combat-epoch':CURRENT_COMBAT_RULES_EPOCH},{'x-gmm-augment-version':'2','x-gmm-combat-epoch':'old'}]){
    const h={'x-gmm':'1',cookie,...bad};expect((await app.inject({url:'/my/api/native/battle/current',headers:h})).statusCode).toBe(409);
    expect((await app.inject({method:'POST',url:'/my/api/native/battle/turn',headers:h,payload:{session:s.session,revision:s.revision,reroll:0}})).statusCode).toBe(409);
   }
   const old=s.teams[0].offers.map((o:any)=>o.id);s=await turn(s,{reroll:0});expect(s.teams[0].offers[0].tier).toBe(tier);expect(old).not.toContain(s.teams[0].offers[0].id);expect(s.teams[0].offers[0].rerolled).toBe(true);
   const current=await api('native/battle/current');expect(current.revision).toBe(s.revision);expect(current.teams[0].offers).toEqual(s.teams[0].offers);noSecrets(current);
   await turn(s,{leave:true});
  }
  expect(requests.some(r=>r.method==='GET')).toBe(true);expect(requests.some(r=>r.method==='POST')).toBe(true);
  for(const r of requests){expect(r.headers['x-gmm-augment-version']).toBe('2');expect(r.headers['x-gmm-combat-epoch']).toBe(CURRENT_COMBAT_RULES_EPOCH);}
 });
 it('reverse scoped canonical confirmation rejects stale/foreign/extra input then completes battle',async()=>{
  seed(reverseSeed);let s=await api('native/battle/start',payload);expect(s.teams[0].offers.some((o:any)=>o.id==='reverse')).toBe(true);
  s=await turn(s,{choose:{augment:'reverse'}});const a=s.teams[0].attributeSelection;expect(a.attributes).toEqual([...attrKeys]);expect(a.targetKey).toBe(s.teams[0].fighters[s.teams[0].active].key);
  const current=await api('native/battle/current');expect(current.teams[0].attributeSelection).toEqual(a);
  const choice={selectionId:a.selectionId,phaseId:a.phaseId,targetKey:a.targetKey,attribute:'SEA'};
  for(const bad of [{...choice,attribute:'INVALID'},{...choice,attribute:''},{...choice,selectionId:'stale'},{...choice,phaseId:'old'},{...choice,targetKey:'foe'},{...choice,extra:true}]){
   await expect(turn(s,{choose:{freestyle:bad}})).rejects.toThrow();expect((await api('native/battle/current')).revision).toBe(s.revision);
  }
  const before=s;s=await turn(s,{choose:{freestyle:choice}});const retry=await turn(before,{choose:{freestyle:choice}});expect(retry.revision).toBe(s.revision);expect(s.teams[0].fighters[0].attrCode).toBe('SEA');
  for(let i=0;i<100&&s.phase!=='over';i++){
   if(s.phase==='choose'){
    const t=s.teams[0],a=t.attributeSelection;
    if(a)s=await turn(s,{choose:{freestyle:{selectionId:a.selectionId,phaseId:a.phaseId,targetKey:a.targetKey,attribute:'SEA'}}});
    else s=await turn(s,{choose:{...(t.offers.length?{augment:t.offers[0].id}:{}),...(t.needSwitch?{switchTo:t.fighters.findIndex((f:any,j:number)=>j!==t.active&&f.hp>0)}:{})}});
   }else {const action=s.teams[0].legal.find((a:any)=>a.kind==='skill');expect(action).toBeTruthy();s=await turn(s,{action});}
  }
  expect(s.phase).toBe('over');expect(s.result).toBeTruthy();noSecrets(s);
 });
});
