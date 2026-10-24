import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {RosterOnboarding,verifyRosterTutorial,commonChoices,type RosterCatalog} from '../src/roster60-onboarding.js';
import {skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('roster60 real authenticated routes and atomic rewards',()=>{
  let db:PrismaClient,game:GameService,app:Awaited<ReturnType<typeof buildApp>>,owner:string,other:string;
  let headers:Record<string,string>,service:RosterOnboarding,ticketId:string;
  // Synthetic contract inputs, never written to data/roster60-approved.json.
  const catalog:RosterCatalog={approved:true,version:'synthetic-test-only',characters:commonChoices.map(id=>({id,name:id,rarity:'COMMON',stats:{hp:10},
    skills:[],attack:{id:'test',name:'test',element:'NORMAL',mpCost:2},growth:[{totalXp:0,maxMp:7}],
    suitability:Object.fromEntries(skillElements.map(e=>[e,0])) as any,defense:Object.fromEntries(skillElements.map(e=>[e,'normal'])) as any}))};
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('disposable DB only');
    db=new PrismaClient({datasources:{db:{url}}});game=new GameService(db,loadContent());
    app=await buildApp(db,game,{secret:'roster60-isolated-test-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    const identity='roster60-'+randomUUID();await game.handle(identity,'내정보');owner=(await db.player.findUniqueOrThrow({where:{identity}})).id;
    other=(await db.player.create({data:{identity:'roster60-other-'+randomUUID()}})).id;
    const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
    const login=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}});
    expect(login.statusCode).toBe(200);headers={'x-gmm':'1','x-gmm-s':login.json().token};
    service=new RosterOnboarding(db,()=>catalog);
  });
  afterAll(async()=>{if(app)await app.close();if(db){for(const id of [owner,other])if(id)await game.webDeleteAccount(id);await db.$disconnect();}});
  const post=(path:string,payload:unknown={})=>app.inject({method:'POST',url:'/my/api/native/roster60/'+path,headers,payload:payload as any});
  it('real auth and strict body; repeated start grants one permanent u01',async()=>{
    expect((await app.inject({method:'POST',url:'/my/api/native/roster60/start',headers:{'x-gmm':'1'},payload:{}})).statusCode).toBe(401);
    expect((await post('start',{playerId:other})).statusCode).toBe(400);
    expect((await app.inject({url:'/my/api/native/roster60/craft/options',headers})).statusCode).toBe(503);
    expect((await post('craft/receipt',{requestId:'not-present'})).json()).toEqual({status:'unknown'});
    expect((await post('craft/receipt',{requestId:'a',playerId:'forged'})).statusCode).toBe(400);
    expect((await post('craft/preview',{resultId:'u09',materialIds:['test-a','test-b'],catalogVersion:'test'})).statusCode).toBe(503);
    expect((await post('craft/confirm',{resultId:'u09',materialIds:['test-a','test-b'],catalogVersion:'test',totalXp:1000})).statusCode).toBe(400);
    const a=await post('start'),b=await post('start');expect(a.statusCode).toBe(200);expect(b.json()).toEqual(a.json());
    expect(await db.roster60Owned.count({where:{playerId:owner,speciesId:'u01'}})).toBe(1);
    expect(await db.ownedCharacter.count({where:{playerId:owner,obtainedVia:'TUTORIAL'}})).toBe(0);
  });
  it('client completion cannot forge proof; enrolled account cannot claim D1 or temporary starters',async()=>{
    expect((await post('complete')).statusCode).toBe(409);
    expect((await post('complete',{tutorialProof:'forged'})).statusCode).toBe(400);
    expect(await game.webStarterLoans(owner)).toMatchObject({state:'retired'});
    await expect(game.webClaimStarterLoans(owner,['D1','E1'])).rejects.toThrow();
    await expect(game.webTutorialSet(owner,9)).rejects.toThrow();
    expect(await db.nativeInboxMessage.count({where:{playerId:owner}})).toBe(0);
  });
  it('server proof produces one real inbox mail, retry and preview never consume',async()=>{
    await db.$transaction(tx=>verifyRosterTutorial(tx,owner,'test-settled-battle-receipt'));
    const a=await post('complete'),b=await post('complete');expect(a.statusCode).toBe(200);expect(b.json()).toEqual(a.json());
    ticketId=a.json().ticket.ticketId;
    const inbox=await app.inject({url:'/my/api/native/news/inbox',headers});expect(inbox.statusCode).toBe(200);expect(inbox.body).toContain(ticketId);
    expect((await post('preview',{ticketId})).statusCode).toBe(503); // no approved product catalog installed
    expect((await service.preview(owner,ticketId) as any).characters).toHaveLength(7);
    expect((await service.preview(owner,ticketId) as any).ticket.quantity).toBe(1);
    await expect(service.preview(other,ticketId)).rejects.toThrow('TICKET_NOT_FOUND');
    expect(await db.nativeInboxMessage.count({where:{playerId:owner}})).toBe(1);
  });
  it('grant failure rolls back conditional consumption; duplicate and foreign choices are rejected',async()=>{
    await expect(service.confirm(owner,ticketId,'u01',catalog.version)).rejects.toThrow('INVALID_CHOICE');
    await expect(service.confirm(other,ticketId,'u02',catalog.version)).rejects.toThrow('TICKET_NOT_FOUND');
    await expect(service.confirm(owner,ticketId,'u02','stale')).rejects.toThrow('CATALOG_CHANGED');
    await db.$executeRawUnsafe("CREATE TRIGGER roster60_test_fail BEFORE INSERT ON Roster60Owned WHEN NEW.speciesId = 'u02' BEGIN SELECT RAISE(ABORT, 'synthetic grant failure'); END");
    try{await expect(service.confirm(owner,ticketId,'u02',catalog.version)).rejects.toThrow();}
    finally{await db.$executeRawUnsafe('DROP TRIGGER roster60_test_fail');}
    expect((await db.roster60State.findUniqueOrThrow({where:{playerId:owner}})).selectedId).toBeNull();
    const a=await service.confirm(owner,ticketId,'u02',catalog.version),b=await service.confirm(owner,ticketId,'u02',catalog.version);
    expect(b).toEqual(a);await expect(service.confirm(owner,ticketId,'u03',catalog.version)).rejects.toThrow('ALREADY_REDEEMED');
    expect(await db.roster60Owned.count({where:{playerId:owner,source:'tutorial-common-choice'}})).toBe(1);
  });
  it('concurrent starts and competing choices cannot duplicate permanent units',async()=>{
    await Promise.allSettled([service.start(other),service.start(other)]);
    await service.start(other);
    expect(await db.roster60Owned.count({where:{playerId:other,source:'tutorial-starter'}})).toBe(1);
    await db.$transaction(tx=>verifyRosterTutorial(tx,other,'second-server-receipt'));
    await service.complete(other);
    const state=await db.roster60State.findUniqueOrThrow({where:{playerId:other}});
    const results=await Promise.allSettled([
      service.confirm(other,state.mailId!,'u03',catalog.version),service.confirm(other,state.mailId!,'u04',catalog.version)]);
    expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);
    const winner=await db.roster60State.findUniqueOrThrow({where:{playerId:other}});
    expect(await db.roster60Owned.count({where:{playerId:other,source:'tutorial-common-choice'}})).toBe(1);
    expect((await service.confirm(other,state.mailId!,winner.selectedId!,catalog.version)).ticket?.ownedId).toBe(winner.rewardOwnedId);
  },20000);
});
