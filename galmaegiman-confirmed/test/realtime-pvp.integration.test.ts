import {beforeAll,beforeEach,afterEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {loadContent} from '../src/content.js';
import {RealtimePvpService,REALTIME_LIMITS} from '../src/pvp/service.js';
import {GameService} from '../src/game.js';
import {buildApp} from '../src/app.js';
import {loadBattleRules} from '../src/battle/load.js';
import {createBattleService,serializeServiceBattle} from '../src/battle/service-factory.js';
import {V2_CLIENT_HEADERS,freestyleChoose} from './helpers/v2-client.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('durable realtime two-human PvP (isolated SQLite)',()=>{
  let db:PrismaClient,service:RealtimePvpService,now:number;
  const content=loadContent(),secret='realtime-review-test-secret'.repeat(3);
  beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('Isolated test DB required');db=new PrismaClient({datasources:{db:{url}}});});
  beforeEach(async()=>{
    await db.$transaction([db.realtimeItemEscrow.deleteMany(),db.realtimeLineup.deleteMany(),db.realtimeRequest.deleteMany(),db.realtimeActivity.deleteMany(),db.realtimeAttempt.deleteMany(),db.realtimeRoom.deleteMany(),db.rankedSettlement.deleteMany(),db.rankedMatch.deleteMany(),db.rankedProfile.deleteMany(),db.casualHumanSettlement.deleteMany(),db.casualHumanRating.deleteMany()]);
    now=Date.now();service=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
  });
  afterEach(()=>service.stop());afterAll(async()=>db.$disconnect());
  async function player(){const p=await db.player.create({data:{identity:'realtime-'+randomUUID(),battleTeam:'D1,T1,E1'}});for(const characterId of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});return p;}
  async function ticket(pid:string,mode:'rank'|'normal'='rank'){return service.attempt(pid,mode);}
  async function join(pid:string,t:Awaited<ReturnType<typeof ticket>>){return service.join(pid,t.attemptId,t.token);}
  async function pair(ready=true,mode:'rank'|'normal'='rank'){const a=await player(),b=await player(),ta=await ticket(a.id,mode),tb=await ticket(b.id,mode);await join(a.id,ta);const q=await join(b.id,tb);expect(q.status).toBe('matched');const roomId=(q as {roomId:string}).roomId;for(const p of [a,b]){const v=await service.currentRoom(p.id,roomId);await service.command(p.id,{roomId,phaseId:v.phaseId,requestId:randomUUID(),command:{accept:true}});}if(ready){for(const p of [a,b]){const v=await service.currentRoom(p.id,roomId);await service.command(p.id,{roomId,phaseId:v.phaseId,requestId:randomUUID(),command:{prepare:{team:['D1','T1','E1'],ready:true}}});}}return {a,b,ta,tb,roomId};}
  const choose=(v:any)=>({choose:freestyleChoose(v.battle!.teams[0])??{...(v.battle!.teams[0].offers.length?{augment:v.battle!.teams[0].offers[0].id}:{}),...(v.battle!.teams[0].needSwitch?{switchTo:v.battle!.teams[0].fighters.findIndex((f:any)=>f.hp>0)}:{})}});
  // 두 사람이 증강을 고른 뒤, 자유자재를 고른 쪽만 속성 선택을 한 번 더 보내 행동 단계까지 간다.
  async function actionPhase(p:Awaited<ReturnType<typeof pair>>){
    let last:any;
    for(let round=0;round<3;round++){
      for(const id of [p.a.id,p.b.id]){
        const v=await service.currentRoom(id,p.roomId);if(v.phase!=='choose')return v;
        if(round>0&&!v.battle!.teams[0].attributeSelection)continue;
        last=await service.command(id,{roomId:p.roomId,phaseId:v.phaseId,requestId:randomUUID(),command:choose(v)});
      }
      if(last.phase!=='choose')return last;
    }
    throw new Error('증강·속성 선택 뒤에도 행동 단계가 되지 않았어요');
  }

  it('preparation stays private and starts augment selection only after both ready',async()=>{
    const p=await pair(false),a=await service.currentRoom(p.a.id,p.roomId),b=await service.currentRoom(p.b.id,p.roomId);
    expect(a.phase).toBe('prepare');expect(a.battle).toBeNull();expect(a.deadline).toBe(now+30_000);
    expect(a.preparation?.selectedIds).toEqual(['D1','T1','E1']);expect(a.preparation).not.toHaveProperty('opponentTeam');
    const ready=await service.command(p.a.id,{roomId:p.roomId,phaseId:a.phaseId,requestId:'ready-a',command:{prepare:{team:['E1','D1','T1'],ready:true}}});
    expect(ready.phase).toBe('prepare');expect(ready.deadline).toBe(a.deadline);expect(ready.canSubmit).toBe(false);
    const started=await service.command(p.b.id,{roomId:p.roomId,phaseId:b.phaseId,requestId:'ready-b',command:{prepare:{team:['D1','T1','E1'],ready:true}}});
    expect(started.phase).toBe('choose');expect(started.phaseId).not.toBe(a.phaseId);expect(started.deadline).toBe(now+30_000);
    expect((await db.player.findUniqueOrThrow({where:{id:p.a.id}})).battleTeam).toBe('E1,D1,T1');
  });
  it('preparation expiry retains idle team and commits last validated edited draft',async()=>{
    const p=await pair(false),a=await service.currentRoom(p.a.id,p.roomId);
    now+=4000;
    const edited=await service.command(p.a.id,{roomId:p.roomId,phaseId:a.phaseId,requestId:'draft',command:{prepare:{team:['T1','E1','D1'],ready:false}}});
    expect(edited.deadline).toBe(a.deadline);expect((await db.player.findUniqueOrThrow({where:{id:p.a.id}})).battleTeam).toBe('D1,T1,E1');
    const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
    expect((await restarted.currentRoom(p.a.id,p.roomId)).preparation?.selectedIds).toEqual(['T1','E1','D1']);
    now=a.deadline!;await service.tick();const started=await service.currentRoom(p.a.id,p.roomId);
    expect(started.phase).toBe('choose');expect(started.deadline).toBe(now+30_000);
    expect((await db.player.findUniqueOrThrow({where:{id:p.a.id}})).battleTeam).toBe('T1,E1,D1');
    expect((await db.player.findUniqueOrThrow({where:{id:p.b.id}})).battleTeam).toBe('D1,T1,E1');
  });
  it('invalid, late and repeated preparation edits cannot replace accepted rosters',async()=>{
    const p=await pair(false),v=await service.currentRoom(p.a.id,p.roomId);
    const before=(await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}})).stateJson;
    for(const team of [['D1','D1','T1'],['D1','T1','unknown']])await expect(service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:randomUUID(),command:{prepare:{team,ready:false}}})).rejects.toThrow();
    expect((await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}})).stateJson).toBe(before);
    const cmd={roomId:p.roomId,phaseId:v.phaseId,requestId:'saved',command:{prepare:{team:['T1','D1','E1'],ready:false}}};await service.command(p.a.id,cmd);await service.command(p.a.id,cmd);
    await expect(service.command(p.a.id,{...cmd,command:{prepare:{team:['E1','D1','T1'],ready:false}}})).rejects.toThrow('같은 요청 번호');
    now=v.deadline!;const late=await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'late-team',command:{prepare:{team:['E1','D1','T1'],ready:true}}});
    expect(late.phase).toBe('choose');expect((await db.player.findUniqueOrThrow({where:{id:p.a.id}})).battleTeam).toBe('T1,D1,E1');
  });
  async function wound(p:Awaited<ReturnType<typeof pair>>){
    const r=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}}),s=JSON.parse(r.stateJson),b=createBattleService(loadBattleRules(process.cwd())).restore(s.battle),side=r.player0Id===p.a.id?0:1;
    b.sides[side].fighters[b.sides[side].active].hp=1;s.battle=serializeServiceBattle(b);
    await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});
    await db.playerItem.upsert({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}},create:{playerId:p.a.id,itemId:'potion',count:1},update:{count:1}});
  }
  it.each(['rank','normal'] as const)('%s item selection holds once and consumes only when the paired turn resolves',async mode=>{
    const p=await pair(true,mode);await actionPhase(p);await wound(p);
    const v=await service.currentRoom(p.a.id,p.roomId),cmd={roomId:p.roomId,phaseId:v.phaseId,requestId:'item-one',command:{action:{kind:'item' as const,item:'potion'}}};
    const held=await service.command(p.a.id,cmd);await service.command(p.a.id,cmd);
    expect(held.mySubmitted).toBe(true);expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}}})).count).toBe(0);
    expect((await db.realtimeItemEscrow.findFirstOrThrow({where:{roomId:p.roomId}})).state).toBe('HELD');
    const foe=await service.currentRoom(p.b.id,p.roomId),skill=foe.battle!.teams[0].legal.find((x:any)=>x.kind==='skill');
    await service.command(p.b.id,{roomId:p.roomId,phaseId:foe.phaseId,requestId:'foe-skill',command:{action:skill}});
    expect((await db.realtimeItemEscrow.findFirstOrThrow({where:{roomId:p.roomId}})).state).toBe('CONSUMED');
    expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}}})).count).toBe(0);
  });
  it('pending items are refunded exactly once on surrender before paired resolution',async()=>{
    const p=await pair();await actionPhase(p);await wound(p);const v=await service.currentRoom(p.a.id,p.roomId);
    await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'held',command:{action:{kind:'item',item:'potion'}}});
    const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
    await restarted.surrender(p.b.id,p.roomId,'leave');await restarted.surrender(p.b.id,p.roomId,'leave-again');
    expect((await db.realtimeItemEscrow.findFirstOrThrow({where:{roomId:p.roomId}})).state).toBe('REFUNDED');
    expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}}})).count).toBe(1);
  });
  it('late and invalid item inputs never debit inventory',async()=>{
    const p=await pair();await actionPhase(p);await wound(p);const v=await service.currentRoom(p.a.id,p.roomId);
    await expect(service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'bad-target',command:{action:{kind:'item',item:'potion',target:2}}})).rejects.toThrow();
    now=v.deadline!;await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'late-item',command:{action:{kind:'item',item:'potion'}}});
    expect(await db.realtimeItemEscrow.count({where:{roomId:p.roomId}})).toBe(0);
    expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}}})).count).toBe(1);
  });
  it('cancellation before join persists a tombstone and changes no score',async()=>{
    const p=await player(),t=await ticket(p.id);
    expect((await service.cancel(p.id,t.attemptId,t.token,'cancel-1')).status).toBe('cancelled');
    expect((await join(p.id,t)).status).toBe('cancelled');
    expect(await db.realtimeRoom.count()).toBe(0);expect(await db.rankedProfile.count()).toBe(0);
    expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).rankPoint).toBe(p.rankPoint);
  });
  it('one waiting ranked user never receives an AI opponent',async()=>{
    const p=await player(),t=await ticket(p.id);await join(p.id,t);
    for(let i=0;i<8;i++){now+=20_000;await service.currentQueue(p.id);await service.tick();}
    expect(await db.realtimeRoom.count()).toBe(0);expect((await service.currentQueue(p.id)).status).toBe('waiting');
  });
  it('matches exactly two distinct humans without pre-debit; cancel loses safely once matched',async()=>{
    const p=await pair(),room=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}});
    expect(new Set([room.player0Id,room.player1Id]).size).toBe(2);
    expect((await service.cancel(p.a.id,p.ta.attemptId,p.ta.token,'after-match')).status).toBe('matched');
    expect((await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}})).state).toBe('ACTIVE');
    for(const x of [p.a,p.b]){expect((await db.rankedProfile.findUniqueOrThrow({where:{playerId:x.id}})).lp).toBe(0);expect((await db.player.findUniqueOrThrow({where:{id:x.id}})).rankPoint).toBe(x.rankPoint);}
  });
  it('cancel racing a second join is either cancelled or matched, never both',async()=>{
    const a=await player(),b=await player(),ta=await ticket(a.id),tb=await ticket(b.id);await join(a.id,ta);
    const [cancel]=await Promise.all([service.cancel(a.id,ta.attemptId,ta.token,'race'),join(b.id,tb)]);
    const stored=await db.realtimeAttempt.findUniqueOrThrow({where:{id:ta.attemptId}});
    expect(['cancelled','matched']).toContain(cancel.status);
    if(cancel.status==='cancelled'){expect(stored.state).toBe('CANCELLED');expect(await db.realtimeRoom.count()).toBe(0);}
    else {expect(stored.state).toBe('MATCHED');expect(await db.realtimeRoom.count()).toBe(1);}
  });
  it('private views hide opponent choices; only two submissions advance once',async()=>{
    const p=await pair(),first=await service.currentRoom(p.a.id,p.roomId),other=await service.currentRoom(p.b.id,p.roomId);
    expect(first.battle!.teams[1].offers).toEqual([]);
    const cmd={roomId:p.roomId,phaseId:first.phaseId,requestId:'first',command:choose(first)};
    const pending=await service.command(p.a.id,cmd);expect(pending.phaseId).toBe(first.phaseId);expect(pending.canSubmit).toBe(false);
    const foe=await service.currentRoom(p.b.id,p.roomId);expect(foe.opponentReady).toBe(true);expect(foe.battle!.teams[1].offers).toEqual([]);
    const done=await service.command(p.b.id,{roomId:p.roomId,phaseId:other.phaseId,requestId:'second',command:choose(other)});
    expect(done.phaseId).not.toBe(first.phaseId);expect(done.battle!.phase).toBe('action');
    expect((await service.command(p.a.id,cmd)).phaseId).toBe(done.phaseId);
    await expect(service.command(p.a.id,{...cmd,command:{choose:{augment:'forged'}}})).rejects.toThrow('같은 요청 번호');
    const stranger=await player();await expect(service.currentRoom(stranger.id,p.roomId)).rejects.toThrow('참가한');
  });
  it('stale phase requests do not mutate battle, RNG or event stream',async()=>{
    const p=await pair(),v=await service.currentRoom(p.a.id,p.roomId),before=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}});
    await service.command(p.a.id,{roomId:p.roomId,phaseId:'obsolete',requestId:'stale',command:choose(v)});
    const after=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}});expect(after.stateJson).toBe(before.stateJson);expect(after.revision).toBe(before.revision);
  });
  it('polling and reroll preserve deadline; exact-deadline command loses to timeout',async()=>{
    const p=await pair(),v=await service.currentRoom(p.a.id,p.roomId);
    const rerolled=await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'reroll',command:{reroll:0}});
    expect(rerolled.deadline).toBe(v.deadline);expect(rerolled.phaseId).toBe(v.phaseId);
    now=v.deadline!;
    const advanced=await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'late',command:choose(rerolled)});
    expect(advanced.phaseId).not.toBe(v.phaseId);expect(advanced.deadline).toBe(now+REALTIME_LIMITS.turnMs);
  });
  it('restart reloads pending selections and the original deadline',async()=>{
    const p=await pair(),v=await service.currentRoom(p.a.id,p.roomId);
    await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'persist',command:choose(v)});
    const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
    const mine=await restarted.currentRoom(p.a.id,p.roomId),foe=await restarted.currentRoom(p.b.id,p.roomId);
    expect(mine.mySubmitted).toBe(true);expect(foe.opponentReady).toBe(true);expect(mine.deadline).toBe(v.deadline);
  });
  it('V2 freestyle augment adds one own-attribute selection before the action phase',async()=>{
    // 선택지 순서는 난수라서 자유자재(reverse)가 첫 선택지일 때만 나던 경로를 양쪽에 고정해 매번 확인한다.
    const p=await pair(),battles=createBattleService(loadBattleRules(process.cwd()));
    const r=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}}),s=JSON.parse(r.stateJson),b=battles.restore(s.battle);
    for(const side of b.sides)side.offers=['reverse',...side.offers!.filter(id=>id!=='reverse')].slice(0,side.offers!.length);
    s.battle=serializeServiceBattle(b);await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});
    expect((await actionPhase(p)).phase).toBe('action');
    const after=battles.restore(JSON.parse((await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}})).stateJson).battle);
    for(const side of after.sides){expect(side.augs).toContain('reverse');expect(side.freestyle?.chosen).toBeTruthy();expect(side.fighters[side.active].attr).toBe(side.freestyle!.chosen);}
  });
  it('three missed action turns settle a deterministic double-timeout void once',async()=>{
    const p=await pair();await actionPhase(p);
    // Seed only the miss counter to exercise the third-strike boundary without a long fight.
    const r=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}}),s=JSON.parse(r.stateJson);s.misses=[2,2];
    await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});now=r.deadline!.getTime();await service.tick();
    const v=await service.currentRoom(p.a.id,p.roomId);expect(v.result).toMatchObject({winner:null,reason:'DOUBLE_TIMEOUT',settlement:'not_applicable'});
    expect(await db.rankedSettlement.count({where:{roomId:r.id}})).toBe(1);
    expect((await db.rankedProfile.findUniqueOrThrow({where:{playerId:p.a.id}})).games).toBe(0);
  });
  it('valid action resets only its own consecutive timeout counter',async()=>{
    const p=await pair();await actionPhase(p);
    const r=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}}),s=JSON.parse(r.stateJson);s.misses=[2,2];
    await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});
    const view=await service.currentRoom(p.a.id,p.roomId),skill=view.battle!.teams[0].legal.find((a:any)=>a.kind==='skill');
    await service.command(p.a.id,{roomId:p.roomId,phaseId:view.phaseId,requestId:'valid',command:{action:skill}});
    const next=JSON.parse((await db.realtimeRoom.findUniqueOrThrow({where:{id:r.id}})).stateJson);expect(next.misses).toEqual(r.player0Id===p.a.id?[0,2]:[2,0]);
  });
  it('surrender is exactly-once scoring and clears both activity locks',async()=>{
    const p=await pair();const [first,second]=await Promise.all([service.surrender(p.a.id,p.roomId,'leave'),service.surrender(p.a.id,p.roomId,'leave')]);
    expect(first.result?.winner).toBe(1);expect(second.result?.winner).toBe(1);
    expect(await db.rankedSettlement.count({where:{roomId:p.roomId}})).toBe(1);
    expect((await db.rankedProfile.findUniqueOrThrow({where:{playerId:p.b.id}})).lp).toBe(20);
    expect((await db.rankedProfile.findUniqueOrThrow({where:{playerId:p.a.id}})).lp).toBe(0);
    expect(await db.realtimeActivity.count({where:{targetId:p.roomId}})).toBe(0);
    const requests=await db.realtimeRequest.count({where:{roomId:p.roomId}});await service.surrender(p.a.id,p.roomId,'new-after-end');expect(await db.realtimeRequest.count({where:{roomId:p.roomId}})).toBe(requests);
  });
  it('legacy/native starts and queued matches cannot own the same player',async()=>{
    const p=await player();await service.claimLegacy(p.id,'old-session');await expect(ticket(p.id)).rejects.toThrow('진행 중인');
    await service.releaseLegacy(p.id,'old-session');const t=await ticket(p.id);await join(p.id,t);await expect(service.claimLegacy(p.id,'new-session')).rejects.toThrow('진행 중인');
  });
  it('HTTP authenticates identities and refuses synthetic or client-selected participants',async()=>{
    const p=await player(),game=new GameService(db,content),app=await buildApp(db,game,{secret,baseUrl:async()=>'https://example.com'});
    try{
      const code=(await game.handle(p.identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=(await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}})).json();
      const headers={'x-gmm':'1','x-gmm-s':login.token,...V2_CLIENT_HEADERS};
      expect((await app.inject({method:'POST',url:'/my/api/pvp/queue/attempt',headers:{'x-gmm':'1'},payload:{mode:'rank',protocol:3}})).statusCode).toBe(401);
      for(const extra of [{playerId:'someone-else'},{bot:true},{participantKind:'AI'}])expect((await app.inject({method:'POST',url:'/my/api/pvp/queue/attempt',headers,payload:{mode:'rank',protocol:3,...extra}})).statusCode).toBe(400);
      const rank=(await app.inject({method:'GET',url:'/my/api/pvp/rank',headers})).json();expect(rank.me).toMatchObject({point:0,tier:'Bronze'});
      const t=(await app.inject({method:'POST',url:'/my/api/pvp/queue/attempt',headers,payload:{mode:'rank',protocol:3}})).json();
      expect((await app.inject({method:'POST',url:'/my/api/pvp/queue/join',headers,payload:{attemptId:t.attemptId,token:t.token}})).json().status).toBe('waiting');
      expect((await app.inject({method:'POST',url:'/my/api/pvp/queue/cancel',headers,payload:{attemptId:t.attemptId,token:t.token,requestId:'cancel'}})).json().status).toBe('cancelled');
    }finally{await app.close();}
  });
  it('native empty-current and late web results cannot release another activity',async()=>{
    const p=await player();
    await service.legacyWebStart(p.id,async()=>({match:'web-one'}));
    await service.nativeOperation(p.id,'battle/current',async()=>({session:null}));
    expect((await db.realtimeActivity.findUniqueOrThrow({where:{playerId:p.id}})).targetId).toBe('web-one');
    await service.legacyWebResult(p.id,'web-one',async()=>({won:false}));
    await service.nativeOperation(p.id,'battle/start',async()=>({session:'native-two',phase:'choose'}));
    await service.legacyWebResult(p.id,'web-one',async()=>({won:false}));
    expect((await db.realtimeActivity.findUniqueOrThrow({where:{playerId:p.id}})).targetId).toBe('native-two');
  });
  it('account deletion cannot strand a waiting or active opponent',async()=>{
    const p=await pair(),game=new GameService(db,content);
    await expect(game.webDeleteAccount(p.a.id)).rejects.toThrow('매칭을 취소하거나');
    expect(await db.player.findUnique({where:{id:p.a.id}})).not.toBeNull();
    await service.surrender(p.a.id,p.roomId,'delete-after-finish');
    await game.webDeleteAccount(p.a.id);
    expect(await db.player.findUnique({where:{id:p.a.id}})).toBeNull();
    const history=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}});
    expect(JSON.parse(history.namesJson)).toContain('탈퇴한 사용자');
  });
});
