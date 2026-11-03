import {beforeAll,beforeEach,afterAll,describe,it,expect,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';
import * as crypto from 'node:crypto';
import type {FastifyInstance} from 'fastify';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import Fastify from 'fastify';
import {buildApp} from '../src/app.js';
import {registerNative} from '../src/native.js';
import {RealtimePvpService} from '../src/pvp/service.js';
import {loadBattleRules} from '../src/battle/load.js';
import {loadStory} from '../src/battle/story.js';
import {choose,aiChoose} from '../src/battle/engine.js';
import {learnChances,learnLeft,type UnitRecord} from '../src/battle/growth.js';
import {V2_CLIENT_HEADERS} from './helpers/v2-client.js';
import {createBattleService} from '../src/battle/service-factory.js';

vi.mock('node:crypto',async importActual=>{
  const actual=await importActual<typeof import('node:crypto')>();
  return {...actual,randomInt:vi.fn(actual.randomInt)};
});
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('네이티브 게임 기능 복원 API (격리 DB)',()=>{
  const content=loadContent(),R=loadBattleRules(),story=loadStory(process.cwd(),R);
  const identity='native-parity-review';
  let db:PrismaClient,game:GameService,app:FastifyInstance,playerId:string,headers:Record<string,string>;
  const get=async(path:string)=>{const r=await app.inject({method:'GET',url:`/my/api/native/${path}`,headers});expect(r.statusCode,r.body).toBe(200);return r.json();};
  const post=async(path:string,payload:object)=>{const r=await app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});expect(r.statusCode,r.body).toBe(200);return r.json();};
  const own=async(id:string,known=true)=>{
    const unit=await db.ownedCharacter.create({data:{playerId,characterId:id,obtainedVia:'TEST'}});
    if(known)await db.collectionEntry.upsert({where:{playerId_characterId:{playerId,characterId:id}},create:{playerId,characterId:id},update:{}});
    return `o${unit.id}`;
  };
  const known=async(id:string)=>db.collectionEntry.upsert({where:{playerId_characterId:{playerId,characterId:id}},create:{playerId,characterId:id},update:{}});
  const record=(id:string,level=15):UnitRecord=>({uid:id,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as UnitRecord['tier'],level,xp:0,stage:0,evoAugs:[]});
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(async()=>{
    vi.mocked(crypto.randomInt).mockReset();vi.mocked(crypto.randomInt).mockImplementation((max:number)=>0);
    await db.$transaction([
      db.webLogin.deleteMany(),db.playerItem.deleteMany(),db.kakaoLink.deleteMany(),db.storySave.deleteMany(),db.chatAppUser.deleteMany(),db.commentLike.deleteMany(),db.comment.deleteMany(),db.pendingAction.deleteMany(),db.adminSnapshot.deleteMany(),db.auditEvent.deleteMany(),db.achievement.deleteMany(),db.dailyProgress.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany(),
    ]);
    game=new GameService(db,content);
    await game.handle(identity,'내정보');
    const p=await db.player.findUniqueOrThrow({where:{identity}});playerId=p.id;
    app=await buildApp(db,game,{secret:'native-parity-review-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
    const login=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}});
    expect(login.statusCode,login.body).toBe(200);headers={'x-gmm':'1','x-gmm-s':login.json().token,...V2_CLIENT_HEADERS};
  });
  afterAll(async()=>{if(app)await app.close();await db.$disconnect();});

  it('네이티브 상태는 실제 경제 설정과 학습·승진 조건 및 장면 메타를 전달한다',async()=>{
    try{
      const key=await own('D1'),rec=record('D1',20),last=story.chapters.find(c=>c.id==='1')!.stages.at(-1)!.id;
      await game.webSaveStory(playerId,{units:{[key]:rec},team:[key],cleared:[last],hp:{}});
      const state=await get('state'),unit=state.units.find((u:any)=>u.key===key);
      expect(state.me.prices).toEqual(game.webPrices());expect(state.me.economy.expedition).toEqual(content.economy.expedition);expect(state.me.economy.rewards).toEqual(content.economy.rewards);
      expect(state.sources.C1).toBe(game.sourceText('C1'));expect(state.storyHpFullMinutes).toBe(R.data.storyHp?.fullMinutes??60);
      expect(unit).toMatchObject({learnChances:learnChances(R,rec),learnLeft:learnLeft(R,rec),learnPerChance:R.data.learn.perChance,promotionNeed:{level:R.data.growth.evolveLevel[0],clearedParts:1,needParts:1,stage:0},canEvolve:true});
      expect(state.sceneActors['마을 회관 방송']).toMatchObject({name:'마을 회관 방송',image:'/duel/sprites/HALL_front.webp'});
      const boss=state.chapters.flatMap((c:any)=>c.stages).find((st:any)=>st.boss);expect(boss.enemy.some((e:any)=>e.boss)).toBe(true);
      const grown=await post('growth',{key,kind:'evolve',augment:unit.evolve[0].id});
      expect(grown.unit).toMatchObject({stage:1,hp:grown.unit.maxHp,promotionNeed:{level:R.data.growth.evolveLevel[1],needParts:2,clearedParts:1,stage:1}});
      expect(grown.unit.canPromote).toBe(grown.unit.promo.length>0);
    }finally{await app.close();}
  });

  it('스토리 잠금은 앞 이야기 클리어만 본다: 승진은 조건이 아니며 적 승진 단계는 안내로만 남는다',async()=>{
    try{
      const empty=await get('state');expect(empty.chapters[0].lock.requirements).toContainEqual(expect.objectContaining({kind:'team',met:false,action:'team'}));
      const a=await own('D1'),b=await own('D2'),flat=story.chapters.flatMap((c,chapterIndex)=>c.stages.map(st=>({...st,chapter:c,chapterIndex})));
      const chapter=story.chapters.find(c=>c.id==='2')!,target=chapter.stages[0],targetIndex=flat.findIndex(st=>st.id===target.id),previous=flat[targetIndex-1];
      const save=async(cleared:string[],stageA:0|1,stageB:0|1,level=15)=>{
        await game.webSaveStory(playerId,{units:{[a]:{...record('D1',level),stage:stageA},[b]:{...record('D2',level),stage:stageB}},team:[a,b],cleared,hp:{}});
        const state=await get('state');
        for(const [ix,st] of flat.entries()){
          const result=state.chapters[st.chapterIndex].stages.find((s:any)=>s.id===st.id);
          // 승진 단계와 무관하게 앞 단계 클리어(또는 뒤쪽 진행 기록)만으로 열린다.
          const expected=ix===0||cleared.includes(flat[ix-1].id)||flat.slice(ix).some(st=>cleared.includes(st.id));
          expect(result.open,st.id).toBe(expected);expect(result.lock.open,st.id).toBe(expected);expect(result.lock.requirements.every((r:any)=>r.met),st.id).toBe(expected);
          expect(result.lock.requirements.some((r:any)=>r.kind==='growth'),st.id).toBe(false);
        }
        return {state,stage:state.chapters.find((c:any)=>c.id==='2').stages[0]};
      };
      const initial=await save([],0,0);expect(initial.stage.lock).toMatchObject({requiredStage:1,previousStageId:previous.id,open:false});
      expect(initial.stage.lock.requirements.find((r:any)=>r.kind==='stage')).toMatchObject({met:false,action:'stage',stageId:previous.id,chapterIndex:previous.chapterIndex});
      expect((await app.inject({method:'POST',url:'/my/api/native/battle/start',headers,payload:{mode:'story',stage:target.id}})).statusCode).toBe(400);
      // 앞 단계만 깨면 아무도 승진하지 않아도 2부에 들어간다.
      const unpromoted=await save([previous.id],0,0,15);
      expect(unpromoted.stage.open).toBe(true);expect(unpromoted.stage.lock.reason).toBe('');expect(unpromoted.state.chapters.find((c:any)=>c.id==='2').open).toBe(true);
      const battle=await post('battle/start',{mode:'story',stage:target.id});expect(battle.stage).toBe(target.id);
      // 적은 여전히 그 부의 승진 단계(1)로 나온다.
      expect(battle.teams[1].fighters.map((f:any)=>f.stage)).toEqual(target.enemy.map((e:any)=>e.stage??chapter.needStage));
      await post('battle/turn',{session:battle.session,revision:battle.revision,leave:true});
      // 후속 단계를 깬 기존 저장은 중간 기록이 없어도 재입장 가능해야 한다.
      expect((await save([chapter.stages[1].id],0,1,20)).stage.open).toBe(true);
    }finally{await app.close();}
  });

  it('네이티브 도감은 소유량과 최초 발견 기록을 구분하고 미발견 히든·최상위의 비공개 정보를 막는다',async()=>{
    try{
      await known('D1');await known('H1');await own('C1');await own('C1');await own('U1');
      const catalog=await get('catalog');const unit=(id:string)=>catalog.units.find((u:any)=>u.id===id);
      expect(unit('D1')).toMatchObject({known:true,have:0,image:'/thumbs/D1.jpg',quote:content.map.get('D1')!.quote});
      expect(unit('H2')).toMatchObject({name:'???',known:false,image:'/thumbs/unknown.jpg',quote:'',tagline:'',intro:'',materials:[],usedIn:[],baseNeeds:[]});
      expect(unit('T1')).toMatchObject({known:false,quote:'',tagline:''});expect(unit('T1').intro).not.toBe(content.map.get('T1')!.introduction);
      expect(unit('C1')).toMatchObject({known:true,have:2,source:game.sourceText('C1')});
      expect(unit('H1').materials.length).toBeGreaterThan(0);expect(unit('R1').materials.length).toBeGreaterThan(0);expect(unit('R1').baseNeeds.length).toBeGreaterThan(0);
      for(const u of catalog.units)expect(u.usedIn.some((r:any)=>r.id==='H2')).toBe(false);
      expect((await app.inject({method:'GET',url:'/my/api/native/catalog',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
    }finally{await app.close();}
  });

  it('네이티브 진행 초기화는 확인값과 전투 종료를 요구하고 재화·갈매미·아이템은 보존한다',async()=>{
    try{
      const key=await own('D1');await game.webSaveStory(playerId,{units:{[key]:record('D1',20)},team:[key],cleared:['P-1'],hp:{[key]:{f:.4,at:Date.now()}}});
      await db.playerItem.create({data:{playerId,itemId:'potion',count:2}});
      const before=await game.webStory(playerId),wallet=await game.webMe(playerId),units=await db.ownedCharacter.count({where:{playerId}});
      for(const payload of [{},{confirm:'reset'},{confirm:'RESET',data:null}])expect((await app.inject({method:'POST',url:'/my/api/native/story/reset',headers,payload})).statusCode).toBe(400);
      expect(await game.webStory(playerId)).toEqual(before);
      const battle=await post('battle/start',{mode:'practice',roster:['D1'],enemyRoster:['T1']});
      expect((await app.inject({method:'POST',url:'/my/api/native/story/reset',headers,payload:{confirm:'RESET'}})).statusCode).toBe(400);
      await post('battle/turn',{session:battle.session,revision:battle.revision,leave:true});
      await post('story/reset',{confirm:'RESET'});expect(await game.webStory(playerId)).toBeNull();
      expect((await game.webMe(playerId))!.credits).toBe(wallet!.credits);expect(await db.ownedCharacter.count({where:{playerId}})).toBe(units);expect((await game.webItems(playerId))[0]).toMatchObject({id:'potion',count:2});
    }finally{await app.close();}
  });

  it('네이티브 연습은 레벨별 학습을 반영하고 상대 능력치·아이템 한도·전체 기록을 읽어도 상태가 변하지 않는다',async()=>{
    try{
      const b=await post('battle/start',{mode:'practice',roster:['D1','T1'],enemyRoster:['E1'],level:30});
      expect(b.teams[0].fighters[0].skills.some((s:any)=>s.id.startsWith('learn-'))).toBe(true);
      expect(b.teams[0].fighters[0].skills.some((s:any)=>s.id==='sig')).toBe(true);
      expect(b.teams[1].fighters[0].base.spd).toBeGreaterThan(0);expect(b.teams[1].fighters[0].role).toBe(R.unitDef('E1')!.role);
      for(const team of b.teams)for(const fighter of team.fighters)expect(Array.isArray(fighter.effects)).toBe(true);
      expect(b.itemLimit).toBe(R.data.itemRules.perBattle);expect(b.teams[0].comboUsed).toBe(false);expect(b.history).toEqual(b.log);
      const current=await get('battle/current');expect(current).toMatchObject({revision:b.revision,displayEventSeq:b.displayEventSeq,teams:b.teams,history:b.history});
    }finally{await app.close();}
  });

  for(const augment of ['reverse','neutral'] as const)it(`네이티브 ${augment} 증강체의 실제 상성은 기술 미리보기와 같고 조회로 RNG가 진행되지 않는다`,async()=>{
    try{
      const enemy=[...R.units.keys()].find(id=>R.typeMult(R.unitDef('D1')!.attr,R.unitDef(id)!.attr)>1)!;
      const mine=record('D1'),foe=record(enemy),battles=createBattleService(R);let seed=0;
      // 운영 기본(V2) 전투로 시드를 찾는다. AI가 상성 배율을 바꾸는 증강(속성 선택·무속성·상대 날씨·약점 방어)을 고르는 시드는 건너뛴다.
      for(;seed<2000;seed++){const b=battles.create([mine],[foe],seed);if(!b.sides[0].offers!.includes(augment))continue;choose(b,0,{augment});if(b.sides[1].offers)aiChoose(b,1);if(!b.sides[1].augs.some(id=>['reverse','neutral','hostileweather','weakguard'].includes(id)))break;}
      expect(seed).toBeLessThan(2000);
      vi.mocked(crypto.randomInt).mockImplementation((max:number)=>max===2**31-1?seed:0);
      let b=await post('battle/start',{mode:'practice',roster:['D1'],enemyRoster:[enemy],level:15});
      b=await post('battle/turn',{session:b.session,revision:b.revision,choose:{augment}});
      if(augment==='reverse'){
        // V2 reverse(자유자재): 상대 속성을 확인한 뒤 내 속성을 한 번 고른다. 원래 유리한 D1에 불리한 속성을 골라 실제 적용을 확인한다.
        const sel=b.teams[0].attributeSelection;expect(b.phase).toBe('choose');expect(sel).toMatchObject({kind:'own-attribute',opponentAttribute:R.unitDef(enemy)!.attr});
        const attribute=sel.attributes.find((a:any)=>R.typeMult(a,sel.opponentAttribute)<1);expect(attribute).toBeDefined();
        b=await post('battle/turn',{session:b.session,revision:b.revision,choose:{freestyle:{selectionId:sel.selectionId,phaseId:sel.phaseId,targetKey:sel.targetKey,attribute}}});
        expect(b.phase).toBe('action');expect(b.teams[0].attributeSelection).toBeNull();
      }
      const me=b.teams[0].fighters[0],basic=me.skills.find((sk:any)=>sk.id.startsWith('basic'));
      expect(me.matchup.attack.multiplier).toBe(basic.preview.multiplier);
      if(augment==='neutral')expect(me.matchup.attack.multiplier).toBe(1);
      else expect(me.matchup.attack.multiplier).toBe(R.data.multipliers.weak);
      const first=await get('battle/current'),second=await get('battle/current');expect(first.teams).toEqual(second.teams);expect(first.displayEvents).toEqual(second.displayEvents);expect(first.revision).toBe(second.revision);
    }finally{await app.close();}
  });

  it('네이티브 AI 랭크를 차단하고 기존 세션을 추가 정산 없이 해제한다',async()=>{
    try{
      for(const id of ['D1','T1','E1'])await own(id);
      await game.webSetBattleTeam(playerId,['D1','T1','E1']);
      await db.player.update({where:{id:playerId},data:{rankPoint:1200}});
      const stats=async()=>{const p=await db.player.findUniqueOrThrow({where:{id:playerId}});return [p.rankPoint,p.rankWins,p.rankLosses];};
      const raw=(path:string,payload:object)=>app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});
      const before=await stats();
      expect((await raw('battle/start',{mode:'rank'})).statusCode).toBe(400);
      expect(await stats()).toEqual(before);
      await db.player.create({data:{identity:'native-parity-opponent',nickname:'상대 수비대',battleTeam:'D1,T1,E1',rankPoint:1200}});
      const rejected=await raw('battle/start',{mode:'rank'});
      expect(rejected.statusCode).toBe(400);expect(rejected.json().error).toContain('실시간 유저 랭크전');
      expect(await stats()).toEqual(before);
      const bypass=()=>vi.spyOn(game,'assertLegacyPvpAllowed').mockImplementation(()=>{});
      // V2(운영 기본): 옛 랭크 차단을 우회해도 네이티브 normal/rank 전투는 만들지 않고 점수도 건드리지 않는다.
      {const guard=bypass();try{for(const mode of ['rank','normal']){const r=await raw('battle/start',{mode});expect(r.statusCode).toBe(400);expect(r.json().error).toContain('실시간 대전에서 시작');}}finally{guard.mockRestore();}}
      expect(await stats()).toEqual(before);expect((await get('battle/current')).session).toBeNull();
      // V2 이전(V1)에 이미 시작해 점수를 먼저 뺀 네이티브 세션은 V1 규칙 API에서만 생길 수 있으므로 그 설정으로 재현한다.
      const realtime=new RealtimePvpService(db,content,process.cwd(),'native-parity-review-secret'.repeat(3),()=>Date.now(),{version:1});
      const legacy=Fastify();registerNative(legacy,game,process.cwd(),()=>playerId,()=>true,realtime,{version:1});
      const legacyCall=(method:'GET'|'POST',path:string,payload?:object)=>legacy.inject({method,url:`/my/api/native/${path}`,headers:{'x-gmm':'1'},...(payload?{payload}:{})});
      const legacyOk=async(method:'GET'|'POST',path:string,payload?:object)=>{const r=await legacyCall(method,path,payload);expect(r.statusCode,r.body).toBe(200);return r.json();};
      try{
        // Test-only bypass simulates an already pre-debited legacy session.
        const oldSession=async()=>{const guard=bypass();try{return await legacyOk('POST','battle/start',{mode:'rank'});}finally{guard.mockRestore();}};
        await oldSession();const debited=await stats();
        expect(debited[0]).toBe(before[0]-content.economy.pvp.lose);
        const current=await legacyOk('GET','battle/current');
        expect(current.session).toBeNull();expect(current.notice).toContain('실시간 유저 랭크전');
        expect(await stats()).toEqual(debited);
        const old=await oldSession(),secondDebit=await stats();
        const stopped=await legacyCall('POST','battle/turn',{session:old.session,revision:old.revision,leave:true});
        expect(stopped.statusCode).toBe(400);expect(stopped.json().error).toContain('실시간 유저 랭크전');
        expect((await legacyOk('GET','battle/current')).session).toBeNull();expect(await stats()).toEqual(secondDebit);
        const normal=await legacyOk('POST','battle/start',{mode:'normal'});
        await legacyOk('POST','battle/turn',{session:normal.session,revision:normal.revision,leave:true});
        expect(await stats()).toEqual(secondDebit);
      }finally{await legacy.close();await realtime.stop();}
    }finally{await app.close();}
  });
});
