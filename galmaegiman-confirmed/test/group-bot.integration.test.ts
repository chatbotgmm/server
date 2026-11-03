import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomBytes} from 'node:crypto';
import type {FastifyInstance} from 'fastify';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {GachaEngine} from '../src/gacha.js';
import {buildApp} from '../src/app.js';
import {loadGroupBotConfig} from '../src/group/config.js';
import {ALLOWED_OUTPUTS} from '../src/group/response.js';
import {GroupStore,groupIdentity} from '../src/group/store.js';
import {PrismaProactiveStore} from '../src/group/store.js';

const enabled=Boolean(process.env.TEST_DB_URL);
const suite=enabled?describe:describe.skip;
const SECRET='group-skill-secret-'.repeat(3),SKILL=SECRET.slice(0,40);
suite('단체방 챗봇 (격리 SQLite)',()=>{
  let db:PrismaClient,game:GameService,app:FastifyInstance;
  const content=loadContent(),config=loadGroupBotConfig();
  let room='',run='';
  // 덱 28 필드 순서로 만든 요청(TODO(verify): 실제 예시 JSON으로 확인 전)
  const body=(user:string,utterance:string,extra:{group?:string;app?:string;bot?:string}={})=>({
    bot:{id:extra.bot??'group-bot'},
    userRequest:{block:{id:'b1',name:'폴백'},utterance,params:{},lang:'ko',timezone:'Asia/Seoul',
      user:{properties:{botUserKey:`${run}-${user}`,...(extra.app?{appUserId:extra.app,appUserStatus:'registered'}:{})}},
      chat:{properties:{botGroupKey:extra.group??room}}},
  });
  const call=async(user:string,utterance:string,extra:Parameters<typeof body>[2]={},path='/kakao/group/skill',secret=SKILL)=>{
    const res=await app.inject({method:'POST',url:path,headers:{'x-skill-secret':secret},payload:body(user,utterance,extra)});
    return {status:res.statusCode,json:res.json() as any};
  };
  const said=(r:{json:any})=>r.json.template.outputs.map((o:any)=>o.simpleText?.text??'').join('\n');
  const player=async(user:string)=>{const k=await db.groupUserKey.findUniqueOrThrow({where:{env_kind_value:{env:'dev',kind:'botUserKey',value:`${run}-${user}`}}});return db.player.findUniqueOrThrow({where:{id:k.playerId}});};
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
    // 뽑기 난수 0 → 각 등급의 첫 유닛
    game=new GameService(db,content,undefined,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    app=await buildApp(db,game,{secret:SECRET,baseUrl:async()=>'https://example.com',group:{secret:SKILL,env:'dev',botId:'group-bot'}});
  });
  beforeEach(async()=>{
    run=randomBytes(4).toString('hex');room=`room-${run}`;
    await db.$transaction([db.groupUserKey.deleteMany(),db.groupRoom.deleteMany(),db.roomMember.deleteMany(),db.roomDex.deleteMany(),db.roomWeek.deleteMany(),db.proactiveLog.deleteMany()]);
  });
  afterAll(async()=>{await app?.close();await db?.$disconnect();});

  it('모든 응답은 허용 말풍선·상한 안에 든다',async()=>{
    for(const u of ['','도움말','모집','모집 하급 3','모집권 받기','조합가능','조합 없는이름','도감','방도감','방순위','닉네임','아무말']){
      const r=await call('a',u);
      expect(r.status).toBe(200);
      const outs=r.json.template.outputs;
      expect(outs.length).toBeGreaterThan(0);expect(outs.length).toBeLessThanOrEqual(config.limits.outputs);
      for(const o of outs)expect(ALLOWED_OUTPUTS).toContain(Object.keys(o)[0]);
      for(const o of outs){const t=o.simpleText?.text??'';expect([...t].length).toBeLessThanOrEqual(config.limits.textChars);expect(t.split('\n').length).toBeLessThanOrEqual(config.limits.textLines);}
      expect(r.json.template.quickReplies.length).toBeLessThanOrEqual(config.limits.quickReplies);
    }
  });
  it('입장·도움말 블록 주소',async()=>{
    const w=await call('a','',{},'/kakao/group/welcome');
    expect(said(w)).toContain('갈매미맨');expect([...said(w)].length).toBeLessThanOrEqual(config.limits.welcomeChars);
    expect(said(await call('a','',{},'/kakao/group/help'))).toContain('방도감');
    expect(said(await call('a','@갈매미맨 도움말'))).toContain('명령어');   // 앞의 멘션은 떼고 처리
  });
  it('처음 부른 사람은 가입 보상을 받고, 방과 멤버가 기록된다',async()=>{
    await call('a','도감');
    const p=await player('a');
    expect(p.identity).toBe(groupIdentity('dev',`${run}-a`));
    expect(p.credits).toBe(content.economy.tickets.welcome);
    const r=await db.groupRoom.findUniqueOrThrow({where:{env_groupKey:{env:'dev',groupKey:room}}});
    expect(await db.roomMember.count({where:{roomId:r.id}})).toBe(1);
  });
  it('모집: 결과·우리 방 첫 발견·방 공동 도감·주간 순위',async()=>{
    const r1=await call('a','모집 하급 2');
    expect(said(r1)).toContain('하급 모집 ×2');expect(said(r1)).toContain('🆕');expect(said(r1)).toContain('우리 방 첫 발견');
    // 잔액은 DB 값과 같다(첫 모집에 튜토리얼 보상이 붙을 수 있어 계산하지 않고 비교)
    expect(said(r1)).toContain(`남은 모집권 ${(await player('a')).credits}장`);
    // 같은 것을 다른 멤버가 얻으면 방 첫 발견이 아니다
    const r2=await call('b','모집 하급 1');
    expect(said(r2)).toContain('🆕');expect(said(r2)).not.toContain('우리 방 첫 발견');
    const dex=said(await call('b','방도감'));
    expect(dex).toMatch(/우리 방 도감 \d+\/145/);expect(dex).toContain('함께 모은 사람 2명');
    await call('a','닉네임 갈매미왕'+run.slice(0,2));
    const rank=said(await call('b','방순위'));
    expect(rank).toContain('1. 갈매미왕');expect(rank).toContain('모집 2');
  });
  it('결과가 상한을 넘으면 목록만 「외 N종」으로 줄이고, 방 첫 발견·잔액 줄은 남긴다',async()=>{
    // 난수 0이라 같은 등급 첫 유닛만 나오므로, 여러 종이 나오게 다른 게임 엔진으로 10회
    let i=0;const varied=new GameService(db,content,undefined,new GachaEngine(content.characters,content.economy.gachas,max=>(i++*7)%max),max=>max-1);
    const app2=await buildApp(db,varied,{secret:SECRET,baseUrl:async()=>'https://example.com',group:{secret:SKILL,env:'dev',botId:'group-bot'}});
    try{
      const res=await app2.inject({method:'POST',url:'/kakao/group/skill',headers:{'x-skill-secret':SKILL},payload:body('many','모집 고급 10')});
      const t=(res.json() as any).template.outputs[0].simpleText.text as string,lines=t.split('\n');
      expect(lines.length).toBeLessThanOrEqual(config.limits.textLines);
      expect(lines.at(-1)).toMatch(/^남은 모집권 \d+장$/);
      expect(t).toContain('우리 방 첫 발견');
      // 보여 준 종 수 + 「외 N종」 = 실제로 얻은 종 수
      // 첫 줄(머리말 「모집 ×10」)은 빼고 셈
      const shown=lines.slice(1).filter(l=>l.includes('🆕')||/×\d+$/.test(l)).length,more=Number(t.match(/외 (\d+)종/)?.[1]??0);
      const owned=await db.collectionEntry.count({where:{playerId:(await player('many')).id}});
      expect(shown+more).toBe(owned);
    }finally{await app2.close();}
  });
  it('모집 횟수·모집권 부족 안내',async()=>{
    expect(said(await call('a','모집 고급 11'))).toContain('1~10회');
    await call('a','도감');await db.player.update({where:{id:(await player('a')).id},data:{credits:3}});
    expect(said(await call('a','모집 고급 1'))).toContain('모집권이 모자라요');
  });
  it('모집권 받기: 받은 뒤에는 기다릴 시간을 알려 준다',async()=>{
    expect(said(await call('a','모집권 받기'))).toContain('모집권 +');
    expect(said(await call('a','모집권 받기'))).toContain('뒤에 받을 수 있어요');
  });
  it('조합: 가능 목록·실행·높은 등급 확정 요청',async()=>{
    await call('a','도감');const p=await player('a');
    const tier=new Map(content.characters.map(c=>[c.id,c.rarity]));
    const easy=content.recipes.find(r=>tier.get(r.resultId)==='UNCOMMON'&&r.materials.every(m=>tier.get(m)==='COMMON')&&!r.hidden)!;
    for(const m of easy.materials)await db.ownedCharacter.create({data:{playerId:p.id,characterId:m,obtainedVia:'TEST'}});
    for(const m of new Set(easy.materials))await db.collectionEntry.upsert({where:{playerId_characterId:{playerId:p.id,characterId:m}},create:{playerId:p.id,characterId:m},update:{}});
    const name=content.map.get(easy.resultId)!.name;
    expect(said(await call('a','조합가능'))).toContain(name);
    const made=said(await call('a',`조합 ${name}`));
    expect(made).toContain('조합 성공');expect(made).toContain(name);expect(made).toContain('우리 방 첫 발견');
    expect(said(await call('a',`조합 ${name}`))).toContain('재료가 모자라요');
    // 전설 조합식은 확정을 먼저 묻는다(재료가 있어도)
    const legend=content.recipes.find(r=>tier.get(r.resultId)==='LEGEND'&&!r.hidden)!;
    for(const m of legend.materials)await db.ownedCharacter.create({data:{playerId:p.id,characterId:m,obtainedVia:'TEST'}});
    for(const m of new Set(legend.materials))await db.collectionEntry.upsert({where:{playerId_characterId:{playerId:p.id,characterId:m}},create:{playerId:p.id,characterId:m},update:{}});
    const lname=content.map.get(legend.resultId)!.name;
    const ask=await call('a',`조합 ${lname}`);
    expect(said(ask)).toContain('만들까요');expect(ask.json.template.quickReplies[0].messageText).toBe(`조합 ${lname} 확정`);
    expect(said(await call('a',`조합 ${lname} 확정`))).toContain('조합 성공');
  });
  it('카카오 로그인 회원번호(appUserId)가 앱 계정과 같으면 그 계정으로 이어진다',async()=>{
    const owner=await db.player.create({data:{identity:`app-owner-${run}`,credits:7}});
    const kakaoId=String(9_000_000+Math.floor(Math.random()*999_999));
    await db.kakaoLink.create({data:{kakaoId,playerId:owner.id}});
    expect(said(await call('linked','내정보',{app:kakaoId}))).toContain('모집권 7장');
    expect((await player('linked')).id).toBe(owner.id);
    // 상태(registered)가 없으면 쓰지 않는다
    const res=await app.inject({method:'POST',url:'/kakao/group/skill',headers:{'x-skill-secret':SKILL},payload:{...body('other','내정보'),userRequest:{...body('other','내정보').userRequest,user:{properties:{botUserKey:`${run}-other`,appUserId:kakaoId}}}}});
    expect(res.statusCode).toBe(200);expect((await player('other')).id).not.toBe(owner.id);
  });
  it('개발·운영 키 공간을 나눈다',async()=>{
    const store=new GroupStore(db,game),base={utterance:'',botUserKey:`${run}-same`,groupKey:room};
    const dev=await store.resolvePlayer({...base,env:'dev'}),prod=await store.resolvePlayer({...base,env:'prod'});
    expect(dev.playerId).not.toBe(prod.playerId);
    expect(await store.room('dev',room)).not.toBe(await store.room('prod',room));
  });
  it('인증·형식·봇 검사',async()=>{
    expect((await call('a','도움말',{},'/kakao/group/skill','x'.repeat(40))).status).toBe(401);
    expect((await call('a','도움말',{bot:'other'})).status).toBe(403);
    const bad=await app.inject({method:'POST',url:'/kakao/group/skill',headers:{'x-skill-secret':SKILL},payload:{userRequest:{utterance:'도움말',user:{properties:{botUserKey:'k'}}}}});
    expect(bad.statusCode).toBe(400);   // 방 키(botGroupKey) 없음
  });
  it('계정 삭제 때 단체방 기록도 지우고, 방 첫 발견 기록은 이름만 비운다',async()=>{
    await call('a','모집 하급 1');const p=await player('a');
    expect(await db.roomDex.count({where:{firstPlayerId:p.id}})).toBeGreaterThan(0);
    await game.webDeleteAccount(p.id);
    expect(await db.groupUserKey.count({where:{playerId:p.id}})).toBe(0);
    expect(await db.roomMember.count({where:{playerId:p.id}})).toBe(0);
    expect(await db.roomWeek.count({where:{playerId:p.id}})).toBe(0);
    expect(await db.roomDex.count({where:{firstPlayerId:p.id}})).toBe(0);
    expect(await db.roomDex.count({where:{firstPlayerId:null}})).toBeGreaterThan(0);
    // 다시 부르면 새 계정으로 시작
    expect(said(await call('a','도감'))).toContain('도감 0/145');
  });
  it('선톡 횟수 DB 저장소: 같은 달 2회까지',async()=>{
    const s=new PrismaProactiveStore(db),at=new Date();
    expect(await s.reserve('prod','2026-11',2,'a',at)).toBe(true);
    expect(await s.reserve('prod','2026-11',2,'b',at)).toBe(true);
    expect(await s.reserve('prod','2026-11',2,'c',at)).toBe(false);
    expect(await s.reserve('dev','2026-11',2,'d',at)).toBe(true);
  });
});
