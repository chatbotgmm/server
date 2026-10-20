import {beforeAll,beforeEach,afterAll,describe,it,expect,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent,topTiers} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {duelKey} from '../src/duel.js';
import {GachaEngine} from '../src/gacha.js';
import {GameError,type GameReply} from '../src/types.js';
import {cardCost,CAROUSEL_TEXT_BUDGET,kakaoResponse} from '../src/kakao.js';
import {existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {DateTime} from 'luxon';
import {loadBattleRules} from '../src/battle/load.js';
import {createBattle,pending,pickAi} from '../src/battle/engine.js';
import {applyEvent,type PvpEvent} from '../src/battle/replay.js';
import {xpToNext,type UnitRecord} from '../src/battle/growth.js';
const enabled=Boolean(process.env.TEST_DB_URL);
const suite=enabled?describe:describe.skip;
suite('격리 SQLite 실제 트랜잭션 (v0.3)',()=>{
  let db:PrismaClient,game:GameService;
  let now=new Date('2026-10-02T08:00:00+09:00');
  // 테스트는 예전 뽑기권 수치(가입 30·받기 10)를 기준으로 계산되어 있어 고정합니다. 실제 수치는 data/economy.yaml
  const content=(()=>{const c=loadContent();return {...c,economy:{...c.economy,tickets:{...c.economy.tickets,welcome:30,claim:10}}};})();
  const actor='test-actor';
  const HOUR=3_600_000;
  const button=(r:GameReply)=>r.list!.items.find(item=>item.button?.action==='confirm')!;
  // 카카오 응답처럼 GameError는 안내 문구로 받습니다(src/app.ts와 동일).
  const send=(text:string,id=actor)=>game.handle(id,text).catch(e=>{if(e instanceof GameError)return {text:e.message} as GameReply;throw e;});
  const confirm=(r:GameReply,id=actor)=>game.handle(id,button(r).message,button(r).button);
  const me=()=>db.player.findUniqueOrThrow({where:{identity:actor}});
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(async()=>{
    await db.$transaction([
      db.webLogin.deleteMany(),db.playerItem.deleteMany(),db.kakaoLink.deleteMany(),db.storySave.deleteMany(),db.chatAppUser.deleteMany(),db.commentLike.deleteMany(),db.comment.deleteMany(),db.pendingAction.deleteMany(),db.adminSnapshot.deleteMany(),db.auditEvent.deleteMany(),db.achievement.deleteMany(),db.dailyProgress.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany()
    ]);
    now=new Date('2026-10-02T08:00:00+09:00');
    // 기본 테스트 계정은 튜토리얼을 마친 상태(튜토리얼 보상은 별도 테스트)
    await db.player.create({data:{identity:actor,tutorial:5,createdAt:now}});
    // 뽑기 난수 0 → 각 등급의 첫 유닛, 탐험 확률 판정은 항상 실패(max-1)
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
  });
  afterAll(async()=>db.$disconnect());
  async function fixtures(ids:string[],data:{credits?:number;snack?:number}={}){
    await send('내정보');
    const p=await db.player.update({where:{identity:actor},data});
    for(const characterId of ids)await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});
    return p;
  }

  it('카드 보호 API는 로그인한 사람의 대기 중인 개체만 보호하고 보호 수를 반환한다',async()=>{
    const p=await fixtures(['D1','D1']);
    const other=await db.player.create({data:{identity:'other-owner'}});
    const otherUnit=await db.ownedCharacter.create({data:{playerId:other.id,characterId:'D1',obtainedVia:'TEST'}});
    const away=await db.ownedCharacter.create({data:{playerId:p.id,characterId:'D1',status:'EXPEDITION',obtainedVia:'TEST'}});
    const app=await buildApp(db,game,{secret:'protect-test-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}});
      const cookie=String(login.headers['set-cookie']).split(';')[0];
      const post=(payload:object,headers:Record<string,string>={'x-gmm':'1',cookie})=>app.inject({method:'POST',url:'/my/api/units/protect',payload,headers});
      expect((await post({id:'D1',locked:true},{cookie})).statusCode).toBe(403);
      expect((await post({id:'D1',locked:true},{'x-gmm':'1'})).statusCode).toBe(401);
      expect((await post({id:'D1',locked:'true'})).statusCode).toBe(400);
      expect((await post({id:'missing',locked:true})).statusCode).toBe(400);
      expect((await post({id:'D1',locked:true})).statusCode).toBe(200);
      expect(await db.ownedCharacter.count({where:{playerId:p.id,locked:true}})).toBe(2);
      expect((await db.ownedCharacter.findUniqueOrThrow({where:{id:otherUnit.id}})).locked).toBe(false);
      expect((await db.ownedCharacter.findUniqueOrThrow({where:{id:away.id}})).locked).toBe(false);
      expect((await game.webMe(p.id))!.units.find(u=>u.id==='D1')).toMatchObject({have:3,away:1,locked:2});
      expect((await post({id:'D1',locked:false})).statusCode).toBe(200);
      expect(await db.ownedCharacter.count({where:{playerId:p.id,locked:true}})).toBe(0);
      const images=await app.inject({method:'GET',url:'/image-state.js'});
      expect(images.statusCode).toBe(200);expect(images.headers['content-type']).toContain('javascript');
    }finally{await app.close();}
  });

  it('신규 30장, 첫 받기는 즉시 +10, 1시간 쿨타임, 늦어도 1회분만',async()=>{
    expect((await send('내정보')).text).toContain('🎟 30장');
    expect((await send('뽑기권 받기')).text).toContain('뽑기권 +10');
    expect((await me()).credits).toBe(40);
    expect((await send('뽑기권받기')).text).toContain('1시간 뒤 (');
    now=new Date(now.getTime()+59*60000);
    expect((await send('받기')).text).toContain('1분 뒤 (');
    now=new Date(now.getTime()+20*HOUR);
    await send('받기');
    expect((await me()).credits).toBe(50);
  });
  it('뽑기 비용 하급 2 / 중급 3 / 고급 5, 여러 번 뽑기, 부족하면 차감 없음',async()=>{
    expect((await send('하급뽑기')).text).toContain('새 흔함 발견');
    expect((await me()).credits).toBe(28);
    await send('중급 뽑기');expect((await me()).credits).toBe(25);
    await send('뽑기 고급');expect((await me()).credits).toBe(20);
    const multi=await send('하급뽑기 5');
    expect(multi.text).toContain('하급 뽑기 ×5');
    // 같은 유닛은 한 줄로 묶고 등급 머리말 아래에 표시
    expect(multi.text).toContain('🥚 흔함\n  기본 갈매미맨 ×5');
    expect(multi.text).toContain('──────────\n📌 지금  🎟');
    expect(multi.cards?.items).toEqual([{imageId:'C1',title:'🥚 기본 갈매미맨 ×5',description:'흔함',buttons:[{label:'도감 보기',message:'도감 기본 갈매미맨'}]}]);
    expect(multi.cards?.outro).toContain('──────────\n📌 지금  🎟');
    // 뽑기 8회째에 '뽑기 5회' 미션 달성 → +20
    expect(multi.text).toContain('오늘의 미션 완료');
    expect((await me()).credits).toBe(30);
    expect((await send('고급뽑기 10')).text).toContain('뽑기권이 모자라요');
    expect((await me()).credits).toBe(30);
    expect(await db.ownedCharacter.count()).toBe(8);
  });
  it('희귀 이상 조합: 미리보기는 소비하지 않고 확정 시 재료 소비 + 첫 도감 보상 200',async()=>{
    await fixtures(['S1','U1']);
    const preview=await send('조합 황금쌍패성기사');
    expect(preview.text).toContain('재료가 모두 모였어요');
    expect(preview.text).not.toContain('가리키기');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(0);
    const done=await confirm(preview);
    expect(done.text).toContain('조합 성공! 새 희귀 발견');
    expect(done.text).not.toContain('💬');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(2);
    expect((await me()).snack).toBe(200);
  });
  it('같은 확정 요청 10개 동시 재전송: 소비·지급 한 번',async()=>{
    await fixtures(['S1','U1']);
    const preview=await send('조합 황금쌍패성기사');
    const results=await Promise.all(Array.from({length:10},()=>confirm(preview)));
    expect(results.every(r=>JSON.stringify(r)===JSON.stringify(results[0]))).toBe(true);
    expect(await db.ownedCharacter.count({where:{characterId:'R1'}})).toBe(1);
  });
  it('히든: 목록·도감·이름 검색에서 숨김, 합치기로 발견하면 공개',async()=>{
    await fixtures(['R1','R2']);
    expect((await send('조합목록')).text).toContain('히든 · 발견 0/21');
    const hint=await send('조합목록 히든');
    expect(hint.cards!.items[0]).toMatchObject({imageId:'unknown',title:'???',description:'❔ 힌트: 방패 두 장을 매듭으로 묶었다. 왜 그 모양인지는 묻지 말 것.'});
    // 한 쪽 카드 수는 카카오 글자 예산 안에서(힌트 문구가 길어 10장보다 적음)
    expect(hint.cards!.items.length).toBeGreaterThanOrEqual(6);
    expect(hint.cards!.items.reduce((s,c)=>s+cardCost(c),0)).toBeLessThanOrEqual(CAROUSEL_TEXT_BUDGET);
    expect(JSON.stringify(hint)).not.toContain('브라자');
    expect((await send('도감 브라자 갈매미맨')).text).toContain('그런 갈매미는 없어요');
    const dex=await send('도감 히든');
    expect(dex.cards!.items[0]).toMatchObject({title:'???',imageId:'unknown'});
    expect(JSON.stringify(dex)).not.toContain('브라자');
    expect((await send('조합 황금쌍패성기사, 심해중력군주')).text).toContain('심해중력군주이(가) 부족해요');
    // 재료가 없으면 히든 조합이든 아니든 같은 답(정답 떠보기 방지)
    const probeHit=(await send('조합 황금쌍패성기사, 은하매듭직조자','prober')).text,probeMiss=(await send('조합 황금쌍패성기사, 심해중력군주','prober')).text;
    expect(probeHit).toBe(probeMiss);expect(probeHit).toContain('황금쌍패성기사이(가) 부족해요');
    expect((await send('조합 황금쌍패성기사, 황금쌍패성기사')).text).toContain('부족해요');
    const preview=await send('조합 황금쌍패성기사, 은하매듭직조자');
    expect(preview.text).toContain('???');
    expect(JSON.stringify(preview)).not.toContain('브라자');
    const done=await confirm(preview);
    expect(done.text).toContain('숨은 조합을 발견했어요');
    expect(done.text).toContain('브라자 갈매미맨');
    expect((await send('조합목록 히든')).cards!.items[0].title).toBe('❔ 브라자 갈매미맨');
  });
  it('히든을 재료로 쓰는 최상위 조합식은 발견 전 ???로 표시',async()=>{
    await fixtures([]);
    const before=await send('조합식 갈크탑');
    expect(before.text).toContain('???');
    expect(before.text).not.toContain('브라자');
    await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:'H1'}});
    expect((await send('조합식 갈크탑')).text).toContain('브라자 갈매미맨');
  });
  it('교환·상점: 최상위 없이도 흔함 150·안흔함 350, 뽑기권 10장 1000, 탐험 즉시 귀환 100',async()=>{
    await fixtures([],{snack:1600});
    const preview=await send('교환 기본');
    expect(preview.text).toContain('새우gang 150개');
    expect((await confirm(preview)).text).toContain('기본 갈매미맨');
    expect((await me()).snack).toBe(1450);
    const list=await send('교환 안흔함');
    expect(list.cards!.items.length).toBeGreaterThanOrEqual(8);   // 한 쪽은 글자 예산 안에서 최대 10장
    expect(list.cards!.items.reduce((s,c)=>s+cardCost(c),0)).toBeLessThanOrEqual(CAROUSEL_TEXT_BUDGET);
    expect(list.cards!.items[0].description).toContain('🍤 350');
    await confirm(await send('교환 금갑'));
    expect((await me()).snack).toBe(1450-350+50);   // 금갑 첫 발견 보상 +50
    expect(await db.ownedCharacter.count({where:{characterId:'U1'}})).toBe(1);
    const shop=await send('상점');
    expect(shop.cards!.items.map(i=>i.title)).toEqual(['🥚 흔함 고르기','🔹 안흔함 고르기','🎟 뽑기권 10장','🍤 남는 갈매미 팔기','🧭 탐험 즉시 귀환','🥜 땅콩 상점']);
    const before=(await me()).credits;
    const tickets=await send('상점 뽑기권');
    expect(tickets.list!.items[0].message).toBe('구매 확정');
    await confirm(tickets);
    expect(await me()).toMatchObject({snack:150,credits:before+10});
    expect((await send('상점 탐험귀환')).text).toContain('바로 돌아오게 할 탐험이 없어요');
    expect((await send('상점 뽑기권')).text).toContain('새우gang이 부족해요');
  });
  it('탐험: 출발→잠김→1시간 뒤 보상 받기를 눌러야 지급→복귀',async()=>{
    const basic=content.characters.filter(c=>c.synergy==='BASIC').map(c=>c.id);
    await fixtures([...basic,'C1','C1','C2']);
    const preview=await send(`탐험보내기 ${basic.map(id=>content.map.get(id)!.name).join(', ')}`);
    expect(preview.text).toContain('기본 ×4 (2단계)');
    expect(preview.text).toContain('예상 52개');
    await confirm(preview);
    expect(await db.ownedCharacter.count({where:{status:'EXPEDITION'}})).toBe(4);
    // 이전 버전이 출발 때 지웠던 편성도 이번 보상 수령 때 복원한다.
    await db.player.update({where:{id:(await me()).id},data:{partyDraft:''}});
    expect((await send('잠금 태초의 갈매미맨')).text).toContain('없어요');
    expect((await send('탐험보내기 자동')).text).toContain('이미 탐험 중');
    expect((await send('내정보')).text).toContain('탐험 중');
    expect((await send('탐험보상받기')).text).toContain('🧭 탐험 중 ·');
    now=new Date(now.getTime()+HOUR);
    expect((await send('내정보')).text).toContain('보상 받기 대기');
    const back=await send('탐험 보상 받기');
    expect(back.text).toContain('새우gang +52');
    expect((await me()).snack).toBe(52);
    expect(await db.ownedCharacter.count({where:{status:'EXPEDITION'}})).toBe(0);
    expect((await send('탐험보상받기')).text).toContain('진행 중인 탐험이 없어요');
    expect((await game.webMe((await me()).id))!.party).toEqual(basic);
    expect((await game.webMe((await me()).id))!.previousParty).toEqual(basic);
    await db.player.update({where:{id:(await me()).id},data:{partyDraft:''}});
    const previous=(await game.webMe((await me()).id))!.previousParty;
    await game.webSetParty((await me()).id,previous);
    expect((await game.webMe((await me()).id))!.party).toEqual(basic);
  });
  it('탐험 자동 편성은 최대 10마리, 같은 유닛 다섯 마리 연출',async()=>{
    await fixtures(Array(12).fill('D7'));
    const preview=await send('탐험보내기 자동');
    expect(preview.text).toContain('🧭 10마리');
    await confirm(preview);
    now=new Date(now.getTime()+HOUR);
    expect((await send('탐험보상받기')).text).toContain('앙~~~~ 갈매미맨이야!!!!');
  });
  it('흔함 도감 완성: 칭호와 뽑기권 5장 1회만',async()=>{
    const p=await fixtures(['D1'],{snack:300});
    for(const id of ['C2','C3','C4','C5','C6','C7','C8','C9'])await db.collectionEntry.create({data:{playerId:p.id,characterId:id}});
    const done=await confirm(await send('교환 기본'));
    expect(done.text).toContain('흔함 도감 완성');
    expect(done.text).toContain('바닷가 산책자');
    expect((await me()).credits).toBe(35);
    await confirm(await send('교환 기본'));
    expect((await me()).credits).toBe(35);
    expect((await send('칭호')).text).toContain('칭호 1/11');
    expect((await send('내정보')).text).toContain('「바닷가 산책자」');
  });
  it('미션: 하루 단위, 한국 시간 0시에 초기화',async()=>{
    await send('하급뽑기 5');
    expect((await send('미션')).text).toContain('✅ 뽑기 5회 (5/5)');
    expect((await me()).credits).toBe(40);
    now=new Date('2026-10-03T00:00:00+09:00');
    expect((await send('미션')).text).toContain('⬜ 뽑기 5회 (0/5)');
  });
  it('중복 결과는 짧게, 조합 가능하면 알려 줌',async()=>{
    const p=await fixtures(['C1','C2']);
    await db.collectionEntry.create({data:{playerId:p.id,characterId:'C1'}});
    const dup=await send('하급뽑기');
    expect(dup.text).toContain('기본 갈매미맨 (2번째 만남)');
    expect(dup.text).not.toContain('도감 기록');
    expect(dup.text).toContain('✨ 조합 가능 1종');
  });
  it('뽑기권이 없을 때: 뽑기 메뉴·받기·부족 안내에 다음 시각과 할 일을 보여 줌',async()=>{
    await fixtures(['C1','C1','C2'],{credits:0});
    await send('뽑기권받기');
    await db.player.update({where:{identity:actor},data:{credits:0}});
    const menuReply=await send('뽑기');
    expect(menuReply.list).toBeUndefined();
    expect(menuReply.text).toContain('다음 뽑기권: 1시간 뒤 (오전 9:00)');
    expect(menuReply.text).toContain('✨ 조합 가능 1종');
    expect((await send('뽑기권받기')).text).toContain('그동안 해 볼 것');
    await db.player.update({where:{identity:actor},data:{credits:3}});
    const short=await send('고급뽑기');
    expect(short.text).toContain('고급 1회 5장 · 지금 3장');
    expect(short.text).toContain('하급·중급 뽑기는 지금 할 수 있어요');
  });
  it('관리자: 모드를 켰을 때만 무제한, 끄거나 해제하면 모드 동안의 변화가 원상복구',async()=>{
    const code='admin-test-code-0123456789';
    // 환급 대상 재화도 복원되는지 확인하기 위해 특별 등급을 확정해서 뽑습니다.
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,content.economy.gachas,max=>max===100?25:0),max=>max-1,process.cwd(),code);
    const counts=async()=>({owned:await db.ownedCharacter.count(),dex:await db.collectionEntry.count(),daily:await db.dailyProgress.count(),snap:await db.adminSnapshot.count()});
    expect((await send('관리자 켜기')).text).toContain('등록된 계정이 아니에요');
    expect((await send('관리자 wrong-code-0000000000')).text).toContain('맞지 않아요');
    expect((await me()).isAdmin).toBe(false);
    expect((await send(`관리자 ${code}`)).text).toContain('관리자 모드 켜짐');
    const r=await send('고급뽑기 10');
    expect(r.text).toContain('무제한(관리자)');
    // 뽑기권은 그대로, 뽑기 5회 미션 보상(+20)만 들어옴
    expect((await me()).credits).toBe(50);
    expect((await me()).snack).toBeGreaterThan(0);
    expect(await counts()).toMatchObject({owned:10,snap:1});
    expect((await send('관리자 켜기')).text).toContain('이미');
    // 끄면 모드 동안 뽑은 유닛·도감·새우gang·미션·뽑기권이 켜기 전으로
    expect((await send('관리자 끄기')).text).toContain('되돌렸어요');
    expect(await counts()).toEqual({owned:0,dex:0,daily:0,snap:0});
    expect(await me()).toMatchObject({credits:30,snack:0,isAdmin:true,adminMode:false});
    // 꺼진 동안은 일반 유저: 뽑기권이 줄고 결과도 남음
    await send('고급뽑기');
    expect((await me()).credits).toBe(25);
    expect((await send('내정보')).text).toContain('관리자(모드 꺼짐)');
    expect((await send('관리자')).text).toContain('꺼짐');
    // 다시 켜서 뽑은 것은 해제할 때도 되돌림. 꺼진 동안 뽑은 1마리는 유지
    expect((await send('관리자 켜기')).text).toContain('관리자 모드 켜짐');
    await send('고급뽑기 3');
    expect((await me()).credits).toBe(25);
    expect(await db.ownedCharacter.count()).toBe(4);
    expect((await send('관리자 해제')).text).toContain('되돌렸어요');
    expect(await db.ownedCharacter.count()).toBe(1);
    expect(await me()).toMatchObject({credits:25,isAdmin:false,adminMode:false});
    expect((await send('관리자 켜기')).text).toContain('등록된 계정이 아니에요');
    await send('고급뽑기');
    expect((await me()).credits).toBe(20);
  });
  it('관리자 모드 동안의 조합은 끈 뒤에도 주간 랭킹에 안 들어가고, 끈 뒤 조합은 들어감',async()=>{
    const code='admin-test-code-0123456789';
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1,process.cwd(),code);
    const r=content.recipes.find(x=>content.map.get(x.resultId)!.rarity==='UNCOMMON')!,name=content.map.get(r.resultId)!.name;
    await send('닉네임 운영테스트');
    await send(`관리자 ${code}`);
    await fixtures(r.materials);
    expect((await send(`조합 ${name}`)).text).toContain(name);
    await send('관리자 끄기');
    const week=async()=>(await new GameService(db,content,()=>now).publicRanking('WEEK')).rows.find(x=>x.name==='운영테스트');
    expect(await week()).toBeUndefined();
    await new Promise(res=>setTimeout(res,5));
    await fixtures(r.materials);
    await send(`조합 ${name}`);
    expect((await week())?.score).toBe(1);
  });
  it('관리자 모드 원상복구: 보유 유닛의 잠금 상태와 번호까지 그대로',async()=>{
    const code='admin-test-code-0123456789';
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1,process.cwd(),code);
    await fixtures(['C1','C1','C2']);
    const before=await db.ownedCharacter.findMany({orderBy:{id:'asc'}});
    await db.ownedCharacter.update({where:{id:before[0].id},data:{locked:true}});
    const locked=await db.ownedCharacter.findMany({orderBy:{id:'asc'}});
    await send(`관리자 ${code}`);
    await send('잠금해제 C1');
    await send('하급뽑기 10');
    await send('관리자 끄기');
    expect(await db.ownedCharacter.findMany({orderBy:{id:'asc'}})).toEqual(locked);
  });
  it('획득 결과에는 한 줄 소개·대사가 없고 도감에서만 보임',async()=>{
    await fixtures([],{credits:30});
    const one=await send('하급뽑기');
    expect(one.text).not.toContain('💬');
    const drawn=content.characters.find(c=>one.text.includes(`【${c.name}】`))!;
    expect(one.text).not.toContain(drawn.tagline);
    expect(one.text).toContain('새 흔함 발견');
    expect((await send('하급뽑기')).text).not.toContain('💬');
    expect((await send('고급뽑기 3')).text).not.toContain('💬');
    expect((await send('도감 C1')).text).toContain('💬');
  });
  it('그림 카드: 1회 뽑기·조합 미리보기·도감·내정보·뽑기 메뉴·도움말',async()=>{
    await fixtures(['C1','C1','C2']);
    const one=await send('하급뽑기');
    expect(one.cards!.items).toHaveLength(1);
    expect(one.cards!.items[0].buttons![0]).toEqual({label:'도감 보기',message:`도감 ${one.cards!.items[0].title.split(' ').slice(1).join(' ')}`});
    expect(one.cards!.outro).toContain('📌 지금');
    // 특별 이하는 확인 없이 바로 조합
    const done=await send('조합 금갑');
    expect(done.list).toBeUndefined();
    expect(done.cards!.items[0]).toMatchObject({imageId:'U1',title:'🔹 금갑'});
    expect(done.cards!.items[0].description).toContain('🧩 조합 성공!');
    const dex=await send('도감');
    expect(dex.text).toContain('📚 도감 ·');
    expect(dex.cards!.items).toHaveLength(11);   // 등급 11개(특수함 포함) → 카카오에서는 10+1 두 묶음
    expect(dex.cards!.items[5]).toMatchObject({imageId:'unknown',title:'❔ 히든'});
    const grades=await send('조합목록');
    expect(grades.cards!.items).toHaveLength(9);
    expect(grades.cards!.items[0].buttons![0]).toEqual({label:'목록 보기',message:'조합목록 안흔함'});
    const common=await send('도감 흔함');
    expect(common.cards!.items).toHaveLength(9);
    expect(common.cards!.items.find(c=>c.title.includes('우주'))!.imageId).toBe('q-COMMON');// 미수집 흔함은 흔함 실루엣
    expect(common.cards!.items.find(c=>c.title.includes('기본'))!.imageId).toBe('C1');
    const info=await send('내정보');
    expect(info.cards!.items[0].imageId).toBe('U1');
    const menuReply=await send('뽑기');
    expect(menuReply.cards!.items.map(c=>c.buttons!.length)).toEqual([2,2,2]);
    expect(menuReply.list).toBeUndefined();
    expect((await send('뭐야이건')).cards!.items).toHaveLength(8);
    expect((await send('도감 금갑')).cards!.outro).toContain('💬');
  });
  it('고급 뽑기 천장: 100회 안에 전설 확정, 전설이 나오면 다시 0부터',async()=>{
    // 난수 0 = 항상 특별만 나오는 상황
    await fixtures([],{credits:1000});
    await db.player.update({where:{identity:actor},data:{highPity:95}});
    const r=await send('고급뽑기 5');
    expect(r.text).toContain('🎯 천장! 전설 확정');
    expect(r.text).toContain('★ 전설');
    // 뽑기 결과도 전설 이상 카드에만 📤 공유하기
    const shared=r.cards!.items.filter(i=>i.buttons?.some(b=>b.kind==='share'));
    expect(shared.length).toBe(1);expect(shared[0].description).toContain('전설');
    expect((await me()).highPity).toBe(0);
    await db.player.update({where:{identity:actor},data:{highPity:99}});
    const one=await send('고급뽑기');
    expect(one.cards!.items[0].buttons!.some(b=>b.kind==='share')).toBe(true);
    expect((await send('고급뽑기')).cards!.items[0].buttons!.some(b=>b.kind==='share')).toBe(false);
    expect((await send('고급뽑기')).text).toContain('🎯 전설 보장까지 98회');
    expect((await send('뽑기')).cards!.items[2].description).toContain('전설 보장까지 98회');
    expect((await send('확률')).text).toContain('천장: 100회 안에 전설 확정');
  });
  it('조합 직전 알림: 재료 1마리만 모자라면 다음 안내에 표시',async()=>{
    // 1마리 차이인 조합이 여럿이면 높은 등급 하나를 안내(여기선 특별 유성대장장이 = 운석 + 기본 + 황금)
    await fixtures(['C2','C2'],{credits:30});
    const r=await send('하급뽑기');   // 난수 0 → 기본 갈매미맨 1마리
    const m=r.text.match(/🔜 (.+) 1마리만 더 있으면 \S+ (.+)!/);
    expect(m).not.toBeNull();
    expect(r.choices!.some(c=>c.message===`조합식 ${m![2]}`)).toBe(true);
  });
  it('랭킹·닉네임: 도감 수 순위, 닉네임 중복 불가, 관리자 모드 제외',async()=>{
    await fixtures(['C1','C2']);
    for(const id of ['C1','C2'])await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:id}});
    expect((await send('닉네임 갈매미왕')).text).toContain('갈매미왕');
    const other=await db.player.create({data:{identity:'other-rank',nickname:'둘째'}});
    await db.collectionEntry.create({data:{playerId:other.id,characterId:'C3'}});
    const rank=await send('랭킹');
    expect(rank.cards!.items.map(i=>i.title)).toEqual(['🥇 갈매미왕','🥈 둘째']);
    expect(rank.cards!.items[0].description).toContain('📚 2/145');
    expect(rank.text).toContain('내 순위: 1위 / 2명');
    expect((await send('닉네임 갈매미왕','other-rank')).text).toContain('이미 누가');
    expect((await send('닉네임 a')).text).toContain('2~10자');
    expect((await send('닉네임 운영자 김씨')).text).toContain('운영진과 헷갈릴');expect((await send('닉네임 ADMIN1')).text).toContain('운영진과 헷갈릴');
    expect((await send('닉네임 갈매_왕')).text).toContain('2~10자');
  });
  it('일일 미션: 희귀·전설/히든·최상위 조합은 결과 등급으로 달성, 한 번만 지급',async()=>{
    await fixtures(['S1','U1','R1','R20','L2','H1','S1','U1'],{credits:0});
    await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:'H1'}});
    const rare=await confirm(await send('조합 황금쌍패성기사'));
    expect(rare.text).toContain('🎉 오늘의 미션 완료! 희귀 조합하기 · 🎟 +30');
    const legend=await confirm(await send('조합 쌍패성왕'));
    expect(legend.text).toContain('🎉 오늘의 미션 완료! 전설 또는 히든 조합하기 · 🎟 +50');
    expect(legend.text).not.toContain('조합 3회');
    const top=await confirm(await send('조합 갈크탑'));
    expect(top.text).toContain('🎉 오늘의 미션 완료! 최상위 유닛 조합하기 · 🎟 +100');
    expect(top.text).toContain('🎉 오늘의 미션 완료! 조합 3회 · 🎟 +20');
    expect((await confirm(await send('조합 황금쌍패성기사'))).text).not.toContain('희귀 조합하기');
    expect((await me()).credits).toBe(30+50+100+20);
    expect((await send('미션')).text).toContain('✅ 최상위 유닛 조합하기 · 🎟 100');
  });
  it('합치기: 이름이 여럿 맞으면 고른 유닛으로 나머지 재료를 지킨 채 다시 보냄',async()=>{
    await fixtures(['R1','R2']);
    const pick=await send('조합 황금쌍패성기사, 은하');
    const button=pick.cards!.items.find(c=>c.title.includes('은하매듭직조자'))!.buttons![0];
    expect(button).toEqual({label:'이걸로',message:'조합 황금쌍패성기사, 은하매듭직조자'});
    expect((await send(button.message)).text).toContain('???');
  });
  it('합치기 카드로 재료 고르기: 첫 재료 → 두 번째 재료 → 바로 미리보기',async()=>{
    await fixtures(['R1','R2','C1']);
    const step1=await send('조합 고르기');
    expect((await send('합치기')).cards!.items[0].buttons![0]).toEqual({label:'첫 재료로',message:'조합 황금쌍패성기사 다음'});// 예전 말도 동작
    expect(step1.cards!.items.map(c=>c.title)).toEqual(['◆ 황금쌍패성기사','◆ 은하매듭직조자','🥚 기본 갈매미맨']);
    expect(step1.cards!.items[0].buttons![0]).toEqual({label:'첫 재료로',message:'조합 황금쌍패성기사 다음'});
    const step2=await send('조합 황금쌍패성기사 다음');
    // 황금쌍패성기사는 1마리뿐이라 두 번째 재료 후보에서 빠짐
    expect(step2.cards!.items.map(c=>c.title)).toEqual(['◆ 은하매듭직조자','🥚 기본 갈매미맨']);
    // 카드마다 버튼 1개(버튼 2개 카드 10장은 카카오가 표시하지 않음)
    expect(step2.cards!.items.every(c=>c.buttons!.length===1)).toBe(true);
    expect(step2.cards!.items[0].buttons![0]).toEqual({label:'재료로 고르기',message:'조합 황금쌍패성기사, 은하매듭직조자 다음'});
    const step3=await send(step2.cards!.items[0].buttons![0].message);
    // 재료가 2개 이상이면 맨 앞 카드가 '이대로 섞기'
    expect(step3.cards!.items.map(c=>c.title)).toEqual(['🔮 이대로 섞기','🥚 기본 갈매미맨']);
    const mix=step3.cards!.items[0].buttons![0];
    expect(mix).toEqual({label:'섞기',message:'조합 황금쌍패성기사, 은하매듭직조자'});
    const preview=await send(mix.message);
    expect(preview.cards!.items[0].imageId).toBe('unknown');
    expect(preview.list!.items[0].button!.action).toBe('confirm');
    expect((await send('조합목록 히든')).cards!.items[0].buttons![0]).toEqual({label:'재료 고르기',message:'조합 고르기'});
  });
  it('땅콩떼기: 갈매미맨만 가능, 1마리 → 중성 갈매미 + 땅콩 1, 갈매미걸은 불가',async()=>{
    await fixtures(['C1','C9','U1']);
    const pick=await send('땅콩떼기');
    expect(pick.cards!.items.map(c=>c.title)).toEqual(['🥚 기본 갈매미맨']);
    expect((await send('땅콩떼기 갈매미걸')).text).toContain('그런 갈매미는 없어요');
    const preview=await send('땅콩떼기 기본 갈매미맨');
    expect(preview.list!.items[0].message).toBe('땅콩떼기 확정');
    const done=await confirm(preview);
    expect(done.cards!.items[0]).toMatchObject({title:'🔘 중성 갈매미'});
    expect(done.cards!.intro).toContain('땅콩을 뗐다');
    expect((await me()).peanut).toBe(1);
    expect(await db.ownedCharacter.count({where:{characterId:'C1',status:'AVAILABLE'}})).toBe(0);
    expect(await db.ownedCharacter.count({where:{characterId:'N1'}})).toBe(1);
    expect((await send('내정보')).cards!.items[0].description).toContain('🥜 1');
  });
  it('특수함 히든: 땅콩떼기로 얻은 중성 갈매미를 합치기 재료로 써서 발견',async()=>{
    await fixtures(['C1','C1','R18','U6']);
    for(let i=0;i<2;i++)await confirm(await send('땅콩떼기 기본 갈매미맨'));
    const preview=await send('조합 중성 갈매미 ×2, 천체금고주, 금조개');
    expect(JSON.stringify(preview)).not.toContain('땅콩 보관소');
    const done=await confirm(preview);
    expect(done.text).toContain('숨은 조합을 발견했어요');
    expect(done.text).toContain('땅콩 보관소');
  });
  it('조합가능은 만들 수 있는 유닛 카드를 바로, 바로가기는 지금 쓸 수 있는 것만',async()=>{
    await fixtures(['C1','C1','C2','C1','C3']);
    const r=await send('조합가능');
    expect(r.cards!.intro).toContain('✨ 지금 만들 수 있는 갈매미');
    expect(r.cards!.items[0].description).toMatch(/보유|처음/);
    expect(r.cards!.items[0].buttons![0].label).toBe('지금 만들기');
    expect(r.cards!.items[0].description).toContain('🆕 처음 만드는 갈매미!');
    // 뽑기권 받기 전: 맨 앞에 받기 버튼, 받은 뒤엔 사라짐
    expect(r.choices![0].message).toBe('뽑기권받기');
    await send('뽑기권받기');
    const info=await send('내정보');
    expect(info.choices!.some(c=>c.message==='뽑기권받기')).toBe(false);
    expect(info.choices!.some(c=>c.message==='내정보')).toBe(false);
    expect(info.choices!.some(c=>c.message==='탐험')).toBe(false);   // 최상위가 없으면 탐험 버튼 숨김
    expect(info.choices!.length).toBeLessThanOrEqual(5);expect(info.choices!.some(c=>c.message==='미션')).toBe(true);
    expect((await send('하급뽑기')).choices![0]).toEqual({label:'하급 한 번 더',message:'하급뽑기'});
  });
  it('여러 개 조합·최대 버튼, 팔기(1마리는 남김), 잠금은 전부',async()=>{
    await fixtures(['C1','C1','C1','C1','C1','C1','C2','C2','C2','C3','C3','C3','C3']);
    const card=(await send('조합가능')).cards!.items.find(c=>c.title.includes('금갑'))!;
    expect(card.buttons!.map(b=>b.label)).toEqual(['지금 만들기','최대 3개','재료 보기']);
    const many=await send('조합 금갑 최대');
    expect(many.cards!.items[0].title).toBe('🔹 금갑 ×3');
    expect(await db.ownedCharacter.count({where:{characterId:'U1',status:'AVAILABLE'}})).toBe(3);
    const list=await send('팔기');
    expect(list.cards!.items.find(c=>c.title.includes('암흑'))!.description).toContain('팔 수 있는 3마리');
    const sell=await send('팔기 암흑 갈매미맨 전부');
    expect(sell.list!.items[0].message).toBe('팔기 확정');
    await confirm(sell);
    expect(await db.ownedCharacter.count({where:{characterId:'C3',status:'AVAILABLE'}})).toBe(1);
    expect((await me()).snack).toBe(50+10+10+30);   // 금갑 첫 50 + 중복 10×2 + 흔함 10×3
    expect((await send('팔기 암흑 갈매미맨')).text).toContain('팔 수 있는');
    await send('잠금 금갑');
    expect(await db.ownedCharacter.count({where:{characterId:'U1',locked:true}})).toBe(3);
  });
  it('튜토리얼 3단계 보상, 친구 초대 둘 다 보상(자기 코드·두 번 입력 불가)',async()=>{
    await db.player.update({where:{identity:actor},data:{tutorial:0}});
    const r=await send('하급뽑기');
    expect(r.text).toContain('📘 튜토리얼 1/5 완료! 🎟 +10');
    const tut=await send('튜토리얼');
    expect(tut.cards!.items[0].imageId).toBe('s-tutorial');
    expect(tut.cards!.items[2].title).toContain('👉 2.');
    const code=(await send('초대코드')).text.match(/초대 코드: ([0-9A-F]{6})/)![1];
    expect((await send(`초대 ${code}`)).text).toContain('내 코드는');
    expect((await send(`초대 ${code}`,'friend')).text).toContain('초대 완료');
    expect((await send(`초대 ${code}`,'friend')).text).toContain('이미');
    expect((await db.player.findUniqueOrThrow({where:{identity:'friend'}})).credits).toBe(30+100);
    // 초대한 쪽은 친구가 튜토리얼을 끝낼 때 받음
    expect((await me()).credits).toBe(30-2+10);
    expect((await send('초대코드')).text).toContain('튜토리얼 진행 중 1명');
    await db.player.update({where:{identity:'friend'},data:{tutorial:3}});
    expect((await send('닉네임 친구왕','friend')).text).toContain('다음: 재료로 직접 조합해 보기');
    expect((await me()).credits).toBe(30-2+10);
    const fr=await db.player.findUniqueOrThrow({where:{identity:'friend'}});
    for(const id of ['C1','C1'])await db.ownedCharacter.create({data:{playerId:fr.id,characterId:id,obtainedVia:'TEST'}});
    const c1=content.map.get('C1')!.name;
    const finish=await send(`조합 ${c1}, ${c1}`,'friend');
    expect(finish.text).toContain('📘 튜토리얼 5/5 완료');
    expect(finish.text).toContain('초대해 준 친구에게도 뽑기권 100장');
    expect((await me()).credits).toBe(30-2+10+100);
    // 다시 끝내도(관리자 되돌리기 등) 두 번 주지 않음
    await db.player.update({where:{identity:'friend'},data:{tutorial:4}});
    await send(`조합 ${c1}, ${c1}`,'friend');
    expect((await me()).credits).toBe(30-2+10+100);
    // 튜토리얼을 이미 끝낸 사람이 코드를 넣으면 초대한 쪽도 바로
    await send('내정보','late');await db.player.update({where:{identity:'late'},data:{tutorial:5}});
    expect((await send(`초대 ${code}`,'late')).text).toContain('초대한 친구도 🎟 +100');
    expect((await me()).credits).toBe(30-2+10+200);
  });
  it('튜토리얼 4단계 닉네임: 정하면 완료, 이미 정해 둔 사람은 도감 단계에서 이어서·옛 완료자는 튜토리얼에서 완료',async()=>{
    await send('내정보');
    await db.player.update({where:{identity:actor},data:{tutorial:3,credits:0}});
    expect((await send('뽑기')).choices!.some(c=>c.message==='튜토리얼')).toBe(true);
    expect((await send('튜토리얼')).cards!.items[4].title).toContain('👉 4. 닉네임 정하기');
    const set=await send('닉네임 튜토왕');
    expect(set.text).toContain('📘 튜토리얼 4/5 완료! 🎟 +10');
    expect(set.text).toContain('다음: 재료로 직접 조합해 보기');
    expect((await me()).tutorial).toBe(4);
    expect((await send('닉네임 튜토왕2')).text).not.toContain('튜토리얼');
    // 닉네임이 있는 채로 도감 단계 → 4단계까지 한 번에
    await db.player.update({where:{identity:actor},data:{tutorial:2,credits:0}});
    const dex=await send('도감');
    expect(dex.text).toContain('3/5 완료');
    expect(dex.text).toContain('4/5 완료');
    expect((await me()).credits).toBe(20);
    // 예전에 3단계로 끝낸 사람(닉네임 있음): 튜토리얼 버튼은 안 보이고, 튜토리얼을 열면 완료 처리
    await db.player.update({where:{identity:actor},data:{tutorial:3,credits:0}});
    expect((await send('뽑기')).choices!.some(c=>c.message==='튜토리얼')).toBe(false);
    expect((await send('튜토리얼')).text).toContain('4/5 완료');
    expect((await me()).tutorial).toBe(4);
    // 5단계: 재료로 직접 조합(가진 재료면 아무 일도 안 일어나도 완료), 재료가 없으면 완료 안 됨
    const c2=content.map.get('C2')!.name;
    await send(`조합 ${c2}, ${c2}`);expect((await me()).tutorial).toBe(4);
    await fixtures(['C2','C2']);
    const lesson=await send(`조합 ${c2}, ${c2}`);
    expect(lesson.text).toContain('📘 튜토리얼 5/5 완료');expect(lesson.text).toContain('튜토리얼 끝');
    expect((await me()).tutorial).toBe(5);
  });
  it('랭킹 탭(도감·최상위·주간), 관리자 상태, 확인 만료 시 다시 하기',async()=>{
    await fixtures(['S1','U1','D1']);
    expect((await send('랭킹 최상위')).cards!.items[0].description).toContain('🗿 1');
    expect((await send('랭킹 주간')).text).toContain('이번 주 조합');
    const preview=await send('조합 황금쌍패성기사');
    now=new Date(now.getTime()+11*60000);
    const late=await confirm(preview);
    expect(late.text).toContain('10분이 지나');
    expect(late.choices).toContainEqual({label:'다시 하기',message:'조합 황금쌍패성기사'});
    expect((await send('관리자 상태')).text).toContain('관리자로 등록된 계정이 아니에요');
  });
  it('스포일러 방지: 얻은 적 없는 유닛은 그림 대신 실루엣(도감 등급 카드·조합목록·조합식·시너지)',async()=>{
    await fixtures(['C1','C2']);
    await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:'C1'}});
    const face=(r:GameReply,title:string)=>r.cards!.items.find(c=>c.title.includes(title))!.imageId;
    const dex=await send('도감');
    expect(face(dex,'흔함')).toBe('C1');// 얻은 유닛
    expect(['q-IMMORTAL','unknown']).toContain(face(dex,'불멸'));// 못 얻은 등급
    expect(['q-RARE','unknown']).toContain((await send('조합목록 희귀')).cards!.items[0].imageId);
    expect(['q-RARE','unknown']).toContain((await send('조합식 황금쌍패성기사')).cards!.items[0].imageId);
    expect((await send('시너지')).cards!.items.every(c=>c.imageId==='unknown'||c.imageId.startsWith('y-'))).toBe(true);
    // 얻고 나면 그림
    await fixtures(['R1']);await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:'R1'}});
    expect((await send('조합식 황금쌍패성기사')).cards!.items[0].imageId).toBe('R1');
  });
  it('도감 상세: 이야기는 문장마다 줄바꿈, 대사는 따로',async()=>{
    await fixtures(['U1']);
    const r=await send('도감 금갑');
    expect(r.cards!.outro).toMatch(/^📖 이야기\n/);
    expect(r.cards!.outro).toMatch(/\n\n💬 「.+」$/);
    expect(r.cards!.outro!.split('\n').length).toBeGreaterThan(3);
  });
  it('이벤트: 진행 중이 아니면 안내, 기간이면 내용과 기한',async()=>{
    expect((await send('이벤트')).text).toContain('쿠폰');   // 쿠폰 진행 중이면 쿠폰 안내
    game=new GameService(db,{...content,economy:{...content.economy,coupons:[]}},()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    {const t=(await send('이벤트')).text;expect(t).toContain('진행 중인 기간 이벤트는 없어요');expect(t).toContain('💌 친구 초대: 친구는 바로, 나는 친구가 튜토리얼을 끝내면 뽑기권 100장씩 (최대 20명)');expect(t).toContain('📘 튜토리얼: 5단계');}
    const ev={label:'벚꽃 주간',from:'2026-10-01T00:00:00+09:00',to:'2026-10-08T00:00:00+09:00',claimBonus:10,snackMultiplier:2};
    game=new GameService(db,{...content,economy:{...content.economy,events:[ev]}},()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    const r=await send('이벤트');
    expect(r.text).toContain('벚꽃 주간');
    expect(r.text).toContain('새우gang 보상 ×2');
  });
  it('쿠폰: 조각을 합친 코드(공백 무시)로 뽑기권, 1인 1회, 틀린 코드·기간 밖은 거절',async()=>{
    const c=content.economy.coupons.find(x=>x.id==='HOMEPAGE_OPEN')!;
    expect(c.pieces.join('')).toBe(c.code);
    const before=(await me()).credits;
    expect((await send('쿠폰')).text).toContain(c.label);
    expect((await send('쿠폰 갈매미는없다')).text).toContain('맞는 쿠폰이 없어요');
    expect((await send(`쿠폰 ${c.pieces.join(' ')}`)).text).toContain(`뽑기권 +${c.tickets}`);
    expect((await me()).credits).toBe(before+c.tickets);
    expect((await send(`쿠폰 ${c.code}`)).text).toContain('이미 받은 쿠폰');
    expect((await me()).credits).toBe(before+c.tickets);
    // 기간이 끝난 쿠폰은 없는 것처럼
    game=new GameService(db,{...content,economy:{...content.economy,coupons:[{...c,id:'OLD',to:'2026-01-01T00:00:00+09:00'}]}},()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    expect((await send(`쿠폰 ${c.code}`,'other-user')).text).toContain('맞는 쿠폰이 없어요');
  });
  it('공유: 전설 이상 조합 결과 카드에 카톡 공유 버튼(희귀 이하는 없음), 카카오 응답은 action share',async()=>{
    await fixtures(['R3','R4','S1','U1']);
    const legend=await confirm(await send('조합 만조의 갈매미맨'));
    const btns=legend.cards!.items[0].buttons!;
    expect(btns.some(b=>b.kind==='share')).toBe(true);
    const out=kakaoResponse(legend,'https://example.com').template as any;
    const card=out.outputs.find((o:any)=>o.basicCard||o.carousel);
    const kb=(card.basicCard??card.carousel.items[0]).buttons;
    expect(kb).toContainEqual({action:'share',label:'📤 공유하기'});
    expect(out.quickReplies.every((q:any)=>q.action==='message')).toBe(true);
    const rare=await confirm(await send('조합 황금쌍패성기사'));
    expect((rare.cards?.items[0].buttons??[]).some(b=>b.kind==='share')).toBe(false);
  });
  it('조합식: 얻는 곳에 교환·탐험도 표시, 모자란 흔함·안흔함은 교환 버튼',async()=>{
    await fixtures(['C2']);
    const r=await send('조합식 금갑');
    expect(r.cards!.outro).toContain('기본 갈매미맨 0/2 · 하급·중급·고급 뽑기 / 교환 / 탐험(바다 시너지)');
    expect(r.cards!.items[0].buttons).toContainEqual({label:'기본 갈매미맨 교환',message:'교환 기본 갈매미맨'});
  });
  it('공개 웹: 홈·세계관·도감, 히든·최상위 스포일러 가림',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const get=async(url:string)=>{const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(200);expect(r.headers['content-type']).toContain('text/html');return r.body;};
      expect(await get('/')).toContain('갈매미는 <span class="hl">존재</span>한다');
      expect(await get('/dex?view=s&sort=name')).toContain('<link rel="canonical" href="https://gmm.dpdns.org/dex">');
      expect(await get('/rank?k=top')).toContain('<link rel="canonical" href="https://gmm.dpdns.org/rank?k=top">');
      expect(await get('/search?q=금갑')).toContain('<meta name="robots" content="noindex">');
      const robots=(await app.inject({method:'GET',url:'/robots.txt'})).body;expect(robots).toContain('Sitemap: https://gmm.dpdns.org/sitemap.xml');expect(robots).toContain('Disallow: /api/');
      const sm=(await app.inject({method:'GET',url:'/sitemap.xml'})).body;expect(sm).toContain('<loc>https://gmm.dpdns.org/dex/C1</loc>');expect(sm).toContain('<loc>https://gmm.dpdns.org/talk</loc>');expect(sm).not.toContain('/dex/H1<');
      expect((sm.match(/<loc>/g)??[]).length).toBe(8+content.characters.filter(c=>c.rarity!=='HIDDEN').length);
      // 횡스크롤 시험판: 색인 제외, 엔진·스크립트 제공, 그림 목록은 png 이름만, 이상한 파일 이름은 거절
      const play=await get('/play');expect(play).toContain('<meta name="robots" content="noindex">');expect(sm).not.toContain('/play');
      for(const url of ['/play/game.js','/play/phaser.js','/play/game3d.js']){const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(200);expect(r.headers['content-type']).toContain('javascript');}
      expect(Array.isArray((await app.inject({method:'GET',url:'/play/sprites.json'})).json())).toBe(true);
      // 턴제 배틀 시험판: 흔함~전설 90종만(히든·최상위 정보 없음), 타입은 7개 동네 중 1~2개, 같은 값이 항상 나옴
      expect(await get('/battle')).toContain('<meta name="robots" content="noindex">');
      expect((await app.inject({method:'GET',url:'/battle/battle.js'})).headers['content-type']).toContain('javascript');
      const roster=(await app.inject({method:'GET',url:'/battle/roster.json'})).json() as {id:string;tier:string;types:string[];hp:number}[];
      expect(roster).toHaveLength(content.characters.filter(c=>['COMMON','UNCOMMON','SPECIAL','RARE','LEGEND'].includes(c.rarity)).length);
      expect(roster.every(u=>['COMMON','UNCOMMON','SPECIAL','RARE','LEGEND'].includes(u.tier)&&u.types.length>=1&&u.types.length<=2&&u.types.every(t=>['BASIC','GOLD','DARK','SEA','BLOSSOM','COSMOS','OUTFIT'].includes(t))&&u.hp>0)).toBe(true);
      expect(JSON.stringify(roster)).not.toMatch(/"id":"[HDTEIN]\d/);
      expect(roster.find(u=>u.id==='C2')!.types[0]).toBe('GOLD');
      for(const url of ['/play/sprites/..%2Fgame.js','/play/sprites/Player.PNG','/play/sprites/nope.png'])expect((await app.inject({method:'GET',url})).statusCode).toBe(404);
      // 수비대 대결 시험판: 최상위 33종이 나오므로 비밀 키가 있어야만 열림, 엔진 묶음에는 이름·그림 없음
      const k=duelKey('s'.repeat(40));
      for(const url of ['/duel','/duel?k=wrong','/duel/data.json','/duel/sprites/D1_s0_front.webp','/duel/data.json?k='+k.slice(1)])expect((await app.inject({method:'GET',url})).statusCode).toBe(404);
      expect(await get('/duel?k='+k)).toContain('<meta name="robots" content="noindex">');
      const duel=(await app.inject({method:'GET',url:'/duel/data.json?k='+k})).json() as {units:{id:string;name:string}[];rules:{version:string};story:{chapters:{stages:unknown[]}[]}};
      expect(duel.units).toHaveLength(33);expect(duel.rules.version).toBe('BATTLE_V1');expect(duel.story.chapters[0].stages.length).toBeGreaterThan(0);
      // 옛 앱은 .png 주소로 물어봄 → 같은 그림을 보내 줌
      expect((await app.inject({method:'GET',url:'/duel/sprites/D1_s0_front.png?k='+k})).statusCode).toBe(200);
      expect((await app.inject({method:'GET',url:'/duel/sprites/D1_s0_back.webp?k='+k})).headers['content-type']).toMatch(/^image\/(webp|png)/);
      for(const url of ['/duel/sprites/..%2F..%2F.env?k='+k,'/duel/sprites/C1_s0_front.webp?k='+k])expect((await app.inject({method:'GET',url})).statusCode).toBe(404);
      const js=await app.inject({method:'GET',url:'/duel/duel.js'});expect(js.statusCode).toBe(200);expect(js.body).not.toContain(content.map.get('D1')!.name);
      const world=await get('/world');
      expect(world).toContain('<h1>갈매미 섬 이야기</h1>');
      expect(world).toContain('class="prose tale"');expect(world).toContain('갈매미 섬 항구 수비대');expect(world).not.toContain('<table>');
      const dex=await get('/dex?view=card');   // 소개가 나오는 큰 카드 보기(기본은 그림만)
      const hidden=content.characters.filter(c=>c.rarity==='HIDDEN');
      const top=content.characters.filter(c=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity));
      expect(hidden.some(c=>dex.includes(c.name))).toBe(false);          // 히든 이름 비공개
      expect(hidden.every(c=>!c.hint||dex.includes(c.hint))).toBe(true); // 힌트는 공개
      expect(top.some(c=>dex.includes(c.introduction))).toBe(false);     // 최상위 이야기 비공개
      expect(top.some(c=>dex.includes(`thumbs/${c.id}.jpg`))).toBe(false); // 최상위 그림 비공개
      expect(dex).toContain(content.map.get('C1')!.introduction);
    }finally{await app.close();}
  });
  it('내 갈매미(/my): 카톡 코드로 로그인, 코드는 한 번만, 다른 사이트 요청·가지지 않은 유닛 막기',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const r=await send('웹 로그인');
      const code=/(\d{6})/.exec(r.text??JSON.stringify(r))![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      await db.ownedCharacter.create({data:{playerId:p.id,characterId:'D1',obtainedVia:'TEST'}});
      const post=(url:string,body:unknown,headers:Record<string,string>={})=>app.inject({method:'POST',url,payload:body as object,headers:{'x-gmm':'1',...headers}});
      expect((await app.inject({method:'POST',url:'/my/api/login',payload:{code}})).statusCode).toBe(403);   // 머리글 없으면 거절
      expect((await post('/my/api/login',{code:'000000'})).statusCode).toBe(400);
      const ok=await post('/my/api/login',{code});
      expect(ok.statusCode).toBe(200);
      const cookie=String(ok.headers['set-cookie']).split(';')[0];
      expect(cookie).toMatch(/^gmm_s=/);expect(String(ok.headers['set-cookie'])).toContain('HttpOnly');
      expect((await post('/my/api/login',{code})).statusCode).toBe(400);   // 같은 코드 다시 안 됨
      expect((await app.inject({method:'GET',url:'/my/api/me'})).statusCode).toBe(401);
      expect((await app.inject({method:'GET',url:'/my/api/me',headers:{cookie:cookie.replace(/.$/,'x')}})).statusCode).toBe(401);   // 서명 틀림
      const me=(await app.inject({method:'GET',url:'/my/api/me',headers:{cookie}})).json() as {units:{id:string;have:number}[];battleTeam:string[]};
      expect(me.units.find(u=>u.id==='D1')?.have).toBe(1);expect(me.battleTeam).toEqual([]);
      expect((await post('/my/api/battle-team',{ids:['D1','T1']},{cookie})).statusCode).toBe(400);   // T1은 없음
      expect((await post('/my/api/battle-team',{ids:['C1']},{cookie})).statusCode).toBe(400);   // 최상위가 아님
      expect((await post('/my/api/battle-team',{ids:['D1']},{cookie})).json()).toEqual({battleTeam:['D1']});
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).battleTeam).toBe('D1');
      expect((await app.inject({method:'GET',url:'/my'})).headers['content-type']).toContain('text/html');
      expect((await app.inject({method:'GET',url:'/my/api/who',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
      expect((await app.inject({method:'GET',url:'/my/api/who',headers:{'x-gmm':'1',cookie}})).json()).toMatchObject({credits:expect.any(Number)});
      // 탐험 편성 → 출발 → (시간 지나) 받기
      expect((await post('/my/api/party',{ids:['D1','D1']},{cookie})).statusCode).toBe(400);   // 1마리뿐
      expect((await post('/my/api/party',{ids:['C1']},{cookie})).statusCode).toBe(400);        // 최상위 아님
      expect((await post('/my/api/party',{ids:['D1']},{cookie})).json()).toEqual({ok:true,result:['D1']});
      expect((await post('/my/api/expedition/start',{},{cookie})).statusCode).toBe(200);
      expect(await db.expedition.count({where:{playerId:p.id}})).toBe(1);
      expect((await post('/my/api/party',{ids:[]},{cookie})).statusCode).toBe(400);            // 탐험 중엔 편성 못 고침
      const trip=(await app.inject({method:'GET',url:'/my/api/me',headers:{cookie}})).json() as {trip:{units:string[]}|null};
      expect(trip.trip?.units).toEqual([content.map.get('D1')!.name]);
      await db.expedition.updateMany({where:{playerId:p.id},data:{endsAt:new Date(now.getTime()-60_000)}});
      expect((await post('/my/api/expedition/claim',{},{cookie})).statusCode).toBe(200);
      expect((await db.expedition.findFirstOrThrow({where:{playerId:p.id}})).claimedAt).not.toBeNull();
      // 아이템: 새우gang 부족·없는 아이템·보유 한도
      await db.player.update({where:{id:p.id},data:{snack:100}});
      expect((await post('/my/api/items/buy',{id:'nope',count:1},{cookie})).statusCode).toBe(400);
      expect((await post('/my/api/items/buy',{id:'potion',count:0},{cookie})).statusCode).toBe(400);
      expect((await post('/my/api/items/buy',{id:'potion',count:1},{cookie})).statusCode).toBe(200);
      expect((await post('/my/api/items/buy',{id:'potion',count:1},{cookie})).statusCode).toBe(400);   // 40개 남음, 60 필요
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).snack).toBe(40);
      await db.player.update({where:{id:p.id},data:{snack:100000}});
      await db.playerItem.update({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}},data:{count:19}});
      expect((await post('/my/api/items/buy',{id:'potion',count:2},{cookie})).statusCode).toBe(400);   // 20개 한도
      expect((await post('/my/api/items/buy',{id:'potion',count:1},{cookie})).statusCode).toBe(200);
      const after=(await app.inject({method:'GET',url:'/my/api/me',headers:{cookie}})).json() as {items:{id:string;count:number}[];itemDefs:{id:string}[]};
      expect(after.items).toEqual([{id:'potion',count:20}]);expect(after.itemDefs.length).toBeGreaterThan(0);
      // 상점: 카톡 상점과 같은 확인 과정을 웹에서 바로 확정
      expect((await post('/my/api/shop',{item:'x'},{cookie})).statusCode).toBe(400);
      const tickets=(await db.player.findUniqueOrThrow({where:{id:p.id}})).credits;
      expect((await post('/my/api/shop',{item:'tickets'},{cookie})).statusCode).toBe(200);
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).credits).toBeGreaterThan(tickets);
      // 수량: 뽑기권 3묶음 = 한 거래, 새우gang이 모자라면 하나도 안 삼
      const t=content.economy.shop.tickets,b0=await db.player.findUniqueOrThrow({where:{id:p.id}});
      expect((await post('/my/api/shop',{item:'tickets',count:3},{cookie})).statusCode).toBe(200);
      const b1=await db.player.findUniqueOrThrow({where:{id:p.id}});
      expect(b1.credits-b0.credits).toBe(t.amount*3);expect(b0.snack-b1.snack).toBe(t.snacks*3);
      await db.player.update({where:{id:p.id},data:{snack:t.snacks*2}});
      expect((await post('/my/api/shop',{item:'tickets',count:3},{cookie})).statusCode).toBe(400);
      const b2=await db.player.findUniqueOrThrow({where:{id:p.id}});
      expect(b2.snack).toBe(t.snacks*2);expect(b2.credits).toBe(b1.credits);   // 전부 취소
      expect((await post('/my/api/shop',{item:'tickets',count:51},{cookie})).statusCode).toBe(400);
    }finally{await app.close();}
  });
  it('카카오 로그인: 처음엔 카톡 코드로 한 번 연결, 다음부터 카카오만, 상태값 틀리면 거절',async()=>{
    const calls:string[]=[];
    const fakeFetch=(async(url:string|URL,init?:RequestInit)=>{
      calls.push(String(url));
      if(String(url).startsWith('https://kauth.kakao.com/oauth/token')){
        const b=new URLSearchParams(String(init?.body));
        expect(b.get('client_secret')).toBe('sec-0123456789');expect(b.get('redirect_uri')).toBe('https://example.com/my/kakao/callback');
        return new Response(JSON.stringify({access_token:'tok-'+b.get('code')}),{status:200});
      }
      const tok=String((init?.headers as Record<string,string>).authorization).replace('Bearer tok-','');
      return new Response(JSON.stringify({id:tok==='bad'?undefined:Number(tok)}),{status:200});
    }) as typeof fetch;
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com',kakaoLogin:{restKey:'0123456789abcdef0123456789abcdef',clientSecret:'sec-0123456789'},fetch:fakeFetch});
    try{
      const cookiesOf=(r:{headers:Record<string,unknown>})=>{const v=r.headers['set-cookie'];return (Array.isArray(v)?v:v?[v]:[]).map(String);};
      const jar=(list:string[])=>list.map(c=>c.split(';')[0]).filter(c=>!/=$/.test(c)).join('; ');
      const start=async()=>{
        const r=await app.inject({method:'GET',url:'/my/kakao'});
        expect(r.statusCode).toBe(302);
        const loc=new URL(String(r.headers.location));
        expect(loc.origin+loc.pathname).toBe('https://kauth.kakao.com/oauth/authorize');
        expect(loc.searchParams.get('client_id')).toBe('0123456789abcdef0123456789abcdef');
        return {state:loc.searchParams.get('state')!,cookie:jar(cookiesOf(r))};
      };
      expect(((await app.inject({method:'GET',url:'/my/api/me'})).json() as {kakao:boolean}).kakao).toBe(true);
      // 상태값이 다르면(다른 사이트가 끼워 넣은 요청) 거절
      const s0=await start();
      const bad=await app.inject({method:'GET',url:`/my/kakao/callback?code=777&state=x${s0.state}`,headers:{cookie:s0.cookie}});
      expect(bad.headers.location).toBe('/my?kakao=fail');expect(calls).toHaveLength(0);
      // 처음: 연결된 계정 없음 → 연결 대기 쿠키 → 카톡 코드 로그인하면 연결
      const s1=await start();
      const cb=await app.inject({method:'GET',url:`/my/kakao/callback?code=777&state=${s1.state}`,headers:{cookie:s1.cookie}});
      expect(cb.headers.location).toBe('/my?kakao=link');
      const pending=jar(cookiesOf(cb));expect(pending).toMatch(/gmm_kp=/);expect(pending).not.toMatch(/gmm_s=/);
      expect(((await app.inject({method:'GET',url:'/my/api/me',headers:{cookie:pending}})).json() as {linking:boolean}).linking).toBe(true);
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const login=await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1',cookie:pending}});
      expect(login.json()).toEqual({ok:true,kakao:'linked'});
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      expect((await db.kakaoLink.findUnique({where:{playerId:p.id}}))?.kakaoId).toBe('777');
      // 다음부터: 카카오만으로 로그인
      const s2=await start();
      const cb2=await app.inject({method:'GET',url:`/my/kakao/callback?code=777&state=${s2.state}`,headers:{cookie:s2.cookie}});
      expect(cb2.headers.location).toBe('/my');
      const sess=jar(cookiesOf(cb2));expect(sess).toMatch(/gmm_s=/);
      const me=(await app.inject({method:'GET',url:'/my/api/me',headers:{cookie:sess}})).json() as {kakao:boolean;kakaoLogin:boolean};
      expect(me.kakao).toBe(true);expect(me.kakaoLogin).toBe(true);
      // 연결 대기 쿠키는 위조 불가
      const forged=pending.replace(/gmm_kp=\d+/,'gmm_kp=999');
      const code2=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      expect((await app.inject({method:'POST',url:'/my/api/login',payload:{code:code2},headers:{'x-gmm':'1',cookie:forged}})).json()).toEqual({ok:true});
      // 카카오에서 회원번호를 못 받으면 실패 안내
      const s3=await start();
      expect((await app.inject({method:'GET',url:`/my/kakao/callback?code=bad&state=${s3.state}`,headers:{cookie:s3.cookie}})).headers.location).toBe('/my?kakao=fail');
      // 연결 해제
      expect((await app.inject({method:'POST',url:'/my/api/kakao/unlink',payload:{},headers:{'x-gmm':'1',cookie:sess}})).statusCode).toBe(200);
      expect(await db.kakaoLink.count({where:{playerId:p.id}})).toBe(0);
    }finally{await app.close();}
    // 키가 없으면 카카오 로그인 주소 자체가 없음
    const plain=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{expect((await plain.inject({method:'GET',url:'/my/kakao'})).statusCode).toBe(404);}finally{await plain.close();}
  });
  it('스토리 진행 계정 저장·모양 검사, 대결 아이템(대결 표·개수 제한·계정에서 차감)',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      await db.ownedCharacter.create({data:{playerId:p.id,characterId:'D1',obtainedVia:'TEST'}});
      await db.player.update({where:{id:p.id},data:{battleTeam:'D1'}});
      const post=(url:string,body:unknown,cookie='')=>app.inject({method:'POST',url,payload:body as object,headers:{'x-gmm':'1',...(cookie?{cookie}:{})}});
      expect((await app.inject({method:'GET',url:'/my/api/story',headers:{'x-gmm':'1'}})).statusCode).toBe(401);   // 로그인 안 하면 브라우저 저장
      const cookie=String((await post('/my/api/login',{code})).headers['set-cookie']).split(';')[0];
      const get=async()=>(await app.inject({method:'GET',url:'/my/api/story',headers:{'x-gmm':'1',cookie}})).json() as {data:unknown;battleTeam:string[];owned:string[]};
      expect(await get()).toMatchObject({data:null,battleTeam:['D1'],owned:['D1']});
      const data={units:{D1:{level:12,xp:30,learned:[],learnUsed:1,stage:0,evoAugs:[]},T3:{level:10,xp:0}},team:['D1','T3','E2'],cleared:['P-1','1-1']};
      expect((await post('/my/api/story',{data},cookie)).statusCode).toBe(200);
      expect((await get()).data).toEqual(data);
      for(const bad of [{...data,units:{D1:{level:999,xp:0}}},{...data,units:{C1:{level:1,xp:0}}},{...data,extra:1},{...data,units:{D1:{level:1,xp:0,hack:1}}},'x'])
        expect((await post('/my/api/story',{data:bad},cookie)).statusCode).toBe(400);
      expect((await get()).data).toEqual(data);   // 잘못된 저장은 반영 안 됨
      // 스토리 체력(v0.14.7): 갈매미별 남은 비율(0~1)과 시각만 저장
      const withHp={...data,hp:{D1:{f:0.4,at:1700000000000}}};
      expect((await post('/my/api/story',{data:withHp},cookie)).statusCode).toBe(200);
      expect((await get()).data).toEqual(withHp);
      for(const bad of [{...data,hp:{D1:{f:1.5,at:1}}},{...data,hp:{D1:{f:0.5,at:1,x:1}}},{...data,hp:{C1:{f:0.5,at:1}}}])
        expect((await post('/my/api/story',{data:bad},cookie)).statusCode).toBe(400);
      expect((await post('/my/api/story',{data},cookie)).statusCode).toBe(200);
      // 아이템: 대결 표 없으면 거절, 표마다 대결당 3개, 계정에서 1개씩 빠짐, 없으면 거절
      await db.playerItem.create({data:{playerId:p.id,itemId:'potion',count:4}});
      expect((await post('/my/api/items/use',{battle:'nope',id:'potion'},cookie)).statusCode).toBe(400);
      const fight=(await post('/my/api/story/battle',{},cookie)).json() as {result:{battle:string;items:{id:string;count:number}[]}};
      expect(fight.result.items).toEqual([{id:'potion',count:4}]);
      for(const left of [3,2,1])expect((await post('/my/api/items/use',{battle:fight.result.battle,id:'potion'},cookie)).json()).toEqual({ok:true,result:{left}});
      expect((await post('/my/api/items/use',{battle:fight.result.battle,id:'potion'},cookie)).json()).toMatchObject({error:expect.stringContaining('3개까지')});
      const fight2=(await post('/my/api/story/battle',{},cookie)).json() as {result:{battle:string}};
      expect((await post('/my/api/items/use',{battle:fight2.result.battle,id:'big'},cookie)).statusCode).toBe(400);   // 없는 아이템
      expect((await post('/my/api/items/use',{battle:fight2.result.battle,id:'potion'},cookie)).json()).toEqual({ok:true,result:{left:0}});
      expect((await post('/my/api/items/use',{battle:fight2.result.battle,id:'potion'},cookie)).statusCode).toBe(400);   // 다 씀
      // 전투 밖 치료(v0.14.7): 체력 회복 아이템만, 대결 표 없이, 계정에서 1개 빠짐
      await db.playerItem.update({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}},data:{count:2}});
      await db.playerItem.create({data:{playerId:p.id,itemId:'clear',count:1}});
      expect((await post('/my/api/items/use-field',{id:'potion'},cookie)).json()).toEqual({ok:true,result:{left:1,heal:35}});
      expect((await post('/my/api/items/use-field',{id:'clear'},cookie)).json()).toMatchObject({error:expect.stringContaining('대결 중에만')});
      expect((await post('/my/api/items/use-field',{id:'big'},cookie)).statusCode).toBe(400);   // 없는 아이템
      expect((await post('/my/api/items/use-field',{id:'potion'},cookie)).json()).toEqual({ok:true,result:{left:0,heal:35}});
      expect((await post('/my/api/items/use-field',{id:'potion'},cookie)).statusCode).toBe(400);   // 다 씀
      // 게임 튜토리얼(v0.15.0): 스토리 저장이 있는 이 계정은 기존 유저 → 9(끝). 새 계정은 1 → 뽑기 단계 지나면 D1 한 번 지급
      expect((await app.inject({method:'GET',url:'/my/api/tutorial',headers:{'x-gmm':'1',cookie}})).json()).toMatchObject({step:9});
      {
        const nc=/(\d{6})/.exec((await send('웹 로그인','tut-new-user')).text!)![1];
        const lr=await post('/my/api/login',{code:nc});const ck=String(lr.headers['set-cookie']).split(';')[0];
        const np=await db.player.findUniqueOrThrow({where:{identity:'tut-new-user'}});
        await db.ownedCharacter.deleteMany({where:{playerId:np.id}});await db.collectionEntry.deleteMany({where:{playerId:np.id}});await db.storySave.deleteMany({where:{playerId:np.id}});
        expect((await app.inject({method:'GET',url:'/my/api/tutorial',headers:{'x-gmm':'1',cookie:ck}})).json()).toEqual({step:1,seen:[]});
        expect((await post('/my/api/tutorial',{step:2},ck)).json()).toMatchObject({ok:true,result:{step:2,granted:false}});
        expect((await post('/my/api/tutorial',{step:3,seen:'craft'},ck)).json()).toMatchObject({ok:true,result:{step:3,granted:true,seen:['craft']}});
        expect(await db.ownedCharacter.count({where:{playerId:np.id,characterId:'D1'}})).toBe(1);
        expect((await db.player.findUniqueOrThrow({where:{id:np.id}})).battleTeam).toBe('');   // 진행 중이면 편성은 직접(3단계)
        expect((await post('/my/api/tutorial',{step:2},ck)).json()).toMatchObject({result:{step:3}});   // 뒤로는 안 감
        expect((await post('/my/api/tutorial',{step:9},ck)).json()).toMatchObject({result:{step:9,granted:false}});
        expect(await db.ownedCharacter.count({where:{playerId:np.id,characterId:'D1'}})).toBe(1);   // 두 번 안 줌
        for(const bad of [{step:0},{step:7},{step:'x'},{step:4,seen:'A B'}])expect((await post('/my/api/tutorial',bad,ck)).statusCode).toBe(400);
      }
      // 다른 사람의 대결 표는 못 씀
      const code2=/(\d{6})/.exec((await send('웹 로그인','other-user')).text!)![1];
      const other=String((await post('/my/api/login',{code:code2})).headers['set-cookie']).split(';')[0];
      expect((await post('/my/api/items/use',{battle:fight2.result.battle,id:'potion'},other)).json()).toMatchObject({error:expect.stringContaining('대결 정보')});
      // 처음부터 다시
      expect((await post('/my/api/story',{data:null},cookie)).statusCode).toBe(200);
      expect((await get()).data).toBeNull();
    }finally{await app.close();}
  });
  it('챗봇이 보낸 appUserId(카카오 로그인 회원번호)가 있으면 웹 카카오 로그인이 코드 없이 연결',async()=>{
    const secret='appuser-secret-'.repeat(4);
    const fakeFetch=(async(url:string|URL)=>String(url).includes('/oauth/token')?new Response(JSON.stringify({access_token:'t'}),{status:200}):new Response(JSON.stringify({id:4242}),{status:200})) as typeof fetch;
    const app=await buildApp(db,game,{secret,baseUrl:async()=>'https://example.com',kakaoLogin:{restKey:'0123456789abcdef0123456789abcdef'},fetch:fakeFetch});
    try{
      const skill=(user:object)=>app.inject({method:'POST',url:'/kakao/skill',headers:{'x-skill-secret':secret},payload:{bot:{id:'bot'},userRequest:{utterance:'내정보',user}}});
      await skill({id:'chat-u1'});                                         // 회원번호 없이 온 요청: 기록 없음
      expect(await db.chatAppUser.count()).toBe(0);
      await skill({id:'chat-u1',properties:{appUserId:'4242'}});
      await new Promise(r=>setTimeout(r,50));
      const p=await db.player.findUniqueOrThrow({where:{identity:JSON.stringify(['KAKAO_CHANNEL','bot','botUserKey','chat-u1'])}});
      expect(await db.chatAppUser.findUnique({where:{appUserId:'4242'}})).toMatchObject({playerId:p.id});
      await skill({id:'chat-u1',properties:{appUserId:'<script>'}});      // 숫자가 아니면 무시
      expect(await db.chatAppUser.count()).toBe(1);
      const start=await app.inject({method:'GET',url:'/my/kakao'});
      const state=new URL(String(start.headers.location)).searchParams.get('state')!;
      const ck=(Array.isArray(start.headers['set-cookie'])?start.headers['set-cookie']:[start.headers['set-cookie']]).map(c=>String(c).split(';')[0]).join('; ');
      const cb=await app.inject({method:'GET',url:`/my/kakao/callback?code=c&state=${state}`,headers:{cookie:ck}});
      expect(cb.headers.location).toBe('/my');                             // 코드 입력 없이 바로 로그인
      expect((await db.kakaoLink.findUnique({where:{kakaoId:'4242'}}))?.playerId).toBe(p.id);
    }finally{await app.close();}
  });
  it('웹 조합: 목록(숨은 조합 가림)·이름으로 조합·재료로 섞기(안 맞으면 재료 그대로)·히든 발견',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      const post=(url:string,body:unknown,cookie:string)=>app.inject({method:'POST',url,payload:body as object,headers:{'x-gmm':'1',cookie}});
      const cookie=String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
      const info=async()=>(await app.inject({method:'GET',url:'/my/api/craft',headers:{'x-gmm':'1',cookie}})).json() as {recipes:{id:string;name:string;img:string;ready:boolean;max:number}[];pool:{id:string;usable:number}[]};
      const open=content.recipes.find(r=>!r.hidden&&new Set(r.materials).size>=2)!;
      const hidden=content.recipes.find(r=>r.hidden)!;
      const give=async(ids:string[])=>{for(const id of ids)await db.ownedCharacter.create({data:{playerId:p.id,characterId:id,obtainedVia:'TEST'}});};
      await give([...open.materials,...open.materials]);
      let list=await info();
      const row=list.recipes.find(r=>r.id===open.resultId)!;
      expect(row).toMatchObject({ready:true,max:2});
      expect(list.recipes.some(r=>r.id===hidden.resultId)).toBe(false);                    // 발견 안 한 히든 조합식은 안 보임
      expect(JSON.stringify(list)).not.toContain(content.map.get(hidden.resultId)!.name);
      expect(row.img).not.toBe(open.resultId);                                               // 아직 못 얻은 결과는 그림 대신 실루엣
      expect((await post('/my/api/craft',{id:hidden.resultId,times:1},cookie)).statusCode).toBe(400);
      expect((await post('/my/api/craft',{id:open.resultId,times:3},cookie)).statusCode).toBe(400);   // 2개 만들 재료뿐
      const made=await post('/my/api/craft',{id:open.resultId,times:1},cookie);
      expect(made.statusCode).toBe(200);expect((made.json() as {result:string}).result).toContain(content.map.get(open.resultId)!.name);
      expect(await db.ownedCharacter.count({where:{playerId:p.id,characterId:open.resultId,status:'AVAILABLE'}})).toBe(1);
      list=await info();
      expect(list.recipes.find(r=>r.id===open.resultId)).toMatchObject({ready:true,max:1});
      // 섞기: 안 맞는 조합이면 재료 그대로
      const before=await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}});
      const wrong=[open.materials[0],open.resultId];
      const r1=(await post('/my/api/mix',{ids:wrong},cookie)).json() as {result:{made:boolean;text:string}};
      expect(r1.result.made).toBe(false);
      expect(await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}})).toBe(before);
      expect((await post('/my/api/mix',{ids:[hidden.materials[0]]},cookie)).statusCode).toBe(400);   // 1마리
      expect((await post('/my/api/mix',{ids:hidden.materials},cookie)).statusCode).toBe(400);         // 재료 없음
      // 히든: 재료를 갖고 섞으면 발견
      await give(hidden.materials);
      const r2=(await post('/my/api/mix',{ids:hidden.materials},cookie)).json() as {result:{made:boolean;text:string}};
      expect(r2.result.made).toBe(true);expect(r2.result.text).toContain('숨은 조합을 발견');
      expect((await info()).recipes.some(r=>r.id===hidden.resultId)).toBe(true);
    }finally{await app.close();}
  });
  it('게임 출석·오늘의 미션: 하루 한 번 뽑기권, 연속 일수, 미션 진행 보기',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      const cookie=String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
      const get=async()=>(await app.inject({method:'GET',url:'/my/api/daily',headers:{'x-gmm':'1',cookie}})).json() as {missions:{key:string;value:number;done:boolean}[];attend:{today:boolean;streak:number;next:number|null}};
      const d0=await get();expect(d0.missions.length).toBe(content.economy.missions.length);expect(d0.attend.today).toBe(false);
      const before=(await db.player.findUniqueOrThrow({where:{id:p.id}})).credits;
      const ok=await app.inject({method:'POST',url:'/my/api/attend',payload:{},headers:{'x-gmm':'1',cookie}});expect(ok.statusCode).toBe(200);
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).credits).toBe(before+content.economy.attendance.days[0]);
      const d1=await get();expect(d1.attend.today).toBe(true);expect(d1.attend.streak).toBe(1);expect(d1.attend.next).toBeNull();
      const again=await app.inject({method:'POST',url:'/my/api/attend',payload:{},headers:{'x-gmm':'1',cookie}});expect(again.statusCode).toBe(400);
      // 날이 갈수록·주가 갈수록 커짐: 지난 7일 연속 출석이 있으면 오늘은 2주째 1일째(1일째 보상 + weekStep)
      const a=content.economy.attendance,today=(d1 as unknown as {day:string}).day;
      expect(a.days[6]).toBeGreaterThan(a.days[0]);
      await db.achievement.deleteMany({where:{playerId:p.id,key:`ATTEND_${today}`}});
      for(let k=1;k<=7;k++)await db.achievement.create({data:{playerId:p.id,key:`ATTEND_${DateTime.fromISO(today).minus({days:k}).toISODate()}`}});
      const d2=(await get()) as unknown as {attend:{week:number;dayInWeek:number;rewards:number[];next:number}};
      expect(d2.attend).toMatchObject({week:2,dayInWeek:1,next:a.days[0]+a.weekStep});expect(d2.attend.rewards).toEqual(a.days.map(t=>t+a.weekStep));
      const c2=(await db.player.findUniqueOrThrow({where:{id:p.id}})).credits;
      expect((await app.inject({method:'POST',url:'/my/api/attend',payload:{},headers:{'x-gmm':'1',cookie}})).json().result).toMatchObject({streak:8,tickets:a.days[0]+a.weekStep});
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).credits).toBe(c2+a.days[0]+a.weekStep);
      expect((await app.inject({method:'POST',url:'/my/api/draw',payload:{kind:'LOW',times:1},headers:{'x-gmm':'1',cookie}})).statusCode).toBe(200);expect((await get()).missions.find(m=>m.key==='DRAW')!.value).toBe(1);
    }finally{await app.close();}
  });
  it('PvP: 일반전 기록 검증은 유지하고 AI 랭크 시작과 점수 변경은 차단한다',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      for(const c of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:p.id,characterId:c,obtainedVia:'TEST'}});
      const post=(url:string,body:unknown,cookie='')=>app.inject({method:'POST',url,payload:body as object,headers:{'x-gmm':'1',...(cookie?{cookie}:{})}});
      const cookie=String((await post('/my/api/login',{code})).headers['set-cookie']).split(';')[0];
      expect((await post('/my/api/pvp/match',{mode:'normal'},cookie)).statusCode).toBe(400);   // 편성 전
      expect((await post('/my/api/battle-team',{ids:['D1','T1','E1']},cookie)).statusCode).toBe(200);
      expect((await post('/my/api/pvp/match',{mode:'rank'},cookie)).statusCode).toBe(400);   // 랭크 상대 없음
      const bot=(await post('/my/api/pvp/match',{mode:'normal'},cookie)).json().result;
      expect(bot.bot).toBe(true);expect(bot.foe).toHaveLength(3);expect(bot.mine.map((r:{level:number})=>r.level)).toEqual([30,30,30]);
      const foe=await db.player.create({data:{identity:'pvp-foe',nickname:'상대왕',battleTeam:'D2,T2,E2'}});
      const R=loadBattleRules();
      // 화면처럼 한 판 두기: 고를 게 있으면 첫 칸(시작 때 한 번 다시 뽑기), 행동은 간단 AI. 같은 함수(applyEvent)로 기록
      const play=(m:{seed:number;mine:UnitRecord[];foe:UnitRecord[]})=>{
        const b=createBattle(R,m.mine,m.foe,m.seed),log:PvpEvent[]=[];let first=true;
        const push=(e:PvpEvent)=>{applyEvent(b,e);log.push(e);};
        while(b.phase!=='over'&&log.length<500){
          const pd=pending(b,0);
          if(b.phase==='choose'){if(first&&pd.augments){first=false;push({r:0});continue;}
            push({c:{...(pd.augments?{augment:b.sides[0].offers![0]}:{}),...(pd.needSwitch?{switchTo:b.sides[0].fighters.findIndex(f=>f.hp>0)}:{})}});}
          else push({a:pickAi(b,0)});
        }
        return {log,won:b.winner===0};
      };
      const botGame=play(bot);
      expect((await post('/my/api/pvp/result',{match:bot.match,log:botGame.log},cookie)).statusCode).toBe(200);
      const before=(await db.player.findUniqueOrThrow({where:{id:p.id}})).rankPoint;
      const rejected=await post('/my/api/pvp/match',{mode:'rank'},cookie);
      expect(rejected.statusCode).toBe(400);expect(rejected.json().error).toContain('실시간 유저 랭크전');
      const m=(await post('/my/api/pvp/match',{mode:'normal'},cookie)).json().result;
      expect(m.foeName).toBe('상대왕');expect(m.foe.map((r:{id:string})=>r.id)).toEqual(['D2','T2','E2']);
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).rankPoint).toBe(before);   // 차단된 랭크와 일반전은 랭크 점수를 바꾸지 않는다
      const g=play(m);
      expect((await post('/my/api/pvp/result',{match:m.match,log:g.log.slice(0,1)},cookie)).statusCode).toBe(400);   // 덜 끝난 기록
      expect((await post('/my/api/pvp/result',{match:m.match,log:[{a:{kind:'skill',skillId:'없는기술'}}]},cookie)).statusCode).toBe(400);   // 맞지 않는 기록
      const r=(await post('/my/api/pvp/result',{match:m.match,log:g.log},cookie)).json().result;
      expect(r.won).toBe(g.won);
      const after=await db.player.findUniqueOrThrow({where:{id:p.id}});
      expect(after.rankPoint).toBe(before);
      expect([after.rankWins,after.rankLosses]).toEqual([0,0]);
      expect((await post('/my/api/pvp/result',{match:m.match,log:g.log},cookie)).statusCode).toBe(400);   // 같은 대전 두 번 안 됨
      const info=(await app.inject({method:'GET',url:'/my/api/pvp',headers:{'x-gmm':'1',cookie}})).json();
      expect(info.me).toMatchObject({point:after.rankPoint,rank:null});expect(info.top).toEqual([]);
      expect(content.characters.filter(c=>topTiers.has(c.rarity)).every(c=>R.units.has(c.id))).toBe(true);   // 편성할 수 있는 갈매미는 모두 전투 유닛
      await db.player.delete({where:{id:foe.id}});
    }finally{await app.close();}
  });
  it('웹 쿠폰: 설정 창 입력(1인 1회·틀린 코드·빈 코드·로그인 필요)',async()=>{
    const c0=content.economy.coupons[0]!;
    game=new GameService(db,{...content,economy:{...content.economy,coupons:[{...c0,id:'WEBTEST',code:'웹 시험 코드',from:undefined,to:undefined}]}});
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      const cookie=String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
      const post=(body:unknown,ck=cookie)=>app.inject({method:'POST',url:'/my/api/coupon',payload:body as object,headers:{'x-gmm':'1',...(ck?{cookie:ck}:{})}});
      expect((await post({code:'웹시험코드'},'')).statusCode).toBe(401);
      expect((await post({code:'  '})).statusCode).toBe(400);
      const wrong=await post({code:'아무말'});expect(wrong.statusCode).toBe(400);expect(wrong.json().error).toContain('맞는 쿠폰이 없어요');
      const before=(await db.player.findUniqueOrThrow({where:{id:p.id}})).credits;
      const ok=await post({code:'웹시험코드'});expect(ok.statusCode).toBe(200);expect(ok.json().result).toContain(`뽑기권 +${c0.tickets}`);
      expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).credits).toBe(before+c0.tickets);
      const again=await post({code:'웹 시험 코드'});expect(again.statusCode).toBe(400);expect(again.json().error).toContain('이미 받은 쿠폰');
    }finally{await app.close();}
  });
  it('웹 뽑기: 정보·뽑기권 받기(대기 시간)·뽑기(그림 카드 결과, 차감)·부족·잘못된 종류',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      const cookie=String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
      const post=(url:string,body:unknown)=>app.inject({method:'POST',url,payload:body as object,headers:{'x-gmm':'1',cookie}});
      const info=async()=>(await app.inject({method:'GET',url:'/my/api/draw',headers:{'x-gmm':'1',cookie}})).json() as {credits:number;wait:number;gachas:{kind:string;cost:number}[]};
      const before=await info();
      expect(before.gachas.map(g=>g.kind)).toEqual(['LOW','MID','HIGH']);
      await db.player.update({where:{id:p.id},data:{lastClaimAt:null}});
      expect((await post('/my/api/claim',{})).statusCode).toBe(200);
      const after=await info();
      expect(after.credits).toBe(before.credits+content.economy.tickets.claim);expect(after.wait).toBeGreaterThan(0);
      const low=content.economy.gachas.LOW.cost;
      const r=(await post('/my/api/draw',{kind:'LOW',times:2})).json() as {result:{items:{id:string;n:number;img:string}[];summary:string}};
      expect(r.result.items.reduce((a,x)=>a+x.n,0)).toBe(2);
      expect((await info()).credits).toBe(after.credits-low*2);
      expect(await db.ownedCharacter.count({where:{playerId:p.id,obtainedVia:'GACHA'}})).toBe(2);
      expect((await post('/my/api/draw',{kind:'NOPE',times:1})).statusCode).toBe(400);
      expect((await post('/my/api/draw',{kind:'LOW',times:51})).statusCode).toBe(400);
      await db.player.update({where:{id:p.id},data:{credits:0}});
      const poor=await post('/my/api/draw',{kind:'HIGH',times:1});
      expect(poor.statusCode).toBe(400);expect(poor.json().error).toContain('뽑기권이 모자라요');
    }finally{await app.close();}
  });
  it('계정 삭제: 확인 글자 필수, 이 사람 데이터만 전부 지움, 다시 말 걸면 새 계정 · 방침·확률·앱 파일',async()=>{
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      expect((await send('탈퇴')).text).toContain('/my#account');
      await send('닉네임 지울사람');
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      const other=await db.player.create({data:{identity:'keep-me',tutorial:5}});
      await db.ownedCharacter.createMany({data:[{playerId:p.id,characterId:'C1',obtainedVia:'TEST'},{playerId:other.id,characterId:'C1',obtainedVia:'TEST'}]});
      await db.collectionEntry.create({data:{playerId:p.id,characterId:'C1'}});
      await db.playerItem.create({data:{playerId:p.id,itemId:'potion',count:2}});
      await db.storySave.create({data:{playerId:p.id,data:'{}'}});
      await db.kakaoLink.create({data:{kakaoId:'555',playerId:p.id}});
      const c=await db.comment.create({data:{playerId:p.id,characterId:'C1',text:'안녕'}});
      const oc=await db.comment.create({data:{playerId:other.id,characterId:'C1',text:'남아요'}});
      await db.commentLike.createMany({data:[{commentId:c.id,voter:'w:x'},{commentId:oc.id,voter:`p:${p.id}`},{commentId:oc.id,voter:'w:y'}]});
      const cookie=String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
      const del=(body:object,h:Record<string,string>={'x-gmm':'1',cookie})=>app.inject({method:'POST',url:'/my/api/account/delete',payload:body,headers:h});
      expect((await del({confirm:'탈퇴'},{cookie})).statusCode).toBe(403);   // 다른 사이트 요청 막기
      expect((await del({confirm:'네'})).statusCode).toBe(400);
      const ok=await del({confirm:'탈퇴'});
      expect(ok.statusCode).toBe(200);expect(String(ok.headers['set-cookie'])).toContain('Max-Age=0');
      expect(await db.player.count({where:{id:p.id}})).toBe(0);
      for(const n of [await db.ownedCharacter.count({where:{playerId:p.id}}),await db.collectionEntry.count({where:{playerId:p.id}}),await db.playerItem.count({where:{playerId:p.id}}),
        await db.storySave.count({where:{playerId:p.id}}),await db.kakaoLink.count({where:{playerId:p.id}}),await db.comment.count({where:{playerId:p.id}}),await db.auditEvent.count({where:{playerId:p.id}}),await db.webLogin.count({where:{playerId:p.id}})])expect(n).toBe(0);
      expect(await db.commentLike.count()).toBe(1);                        // 남의 댓글에 남이 누른 좋아요만 남음
      expect(await db.ownedCharacter.count({where:{playerId:other.id}})).toBe(1);expect(await db.comment.count({where:{id:oc.id}})).toBe(1);
      expect((await app.inject({method:'GET',url:'/my/api/me',headers:{cookie}})).statusCode).toBe(401);
      await send('내정보');                                                   // 다시 말 걸면 새 계정
      const again=await db.player.findUniqueOrThrow({where:{identity:actor}});
      expect(again.id).not.toBe(p.id);expect(again.nickname).toBeNull();
      // 공개 페이지·앱 파일
      for(const [url,has] of [['/privacy','개인정보처리방침'],['/account/delete','계정 삭제'],['/odds','뽑기 확률']] as const){
        const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(200);expect(r.body).toContain(has);
      }
      const odds=(await app.inject({method:'GET',url:'/odds'})).body;
      for(const k of ['LOW','MID','HIGH'] as const)expect(odds).toContain(`${content.economy.gachas[k].label} 뽑기`);
      expect(content.characters.filter(x=>x.rarity==='HIDDEN').some(x=>odds.includes(x.name))).toBe(false);
      const man=await app.inject({method:'GET',url:'/manifest.webmanifest'});
      expect(man.headers['content-type']).toContain('application/manifest+json');expect((man.json() as {icons:unknown[]}).icons.length).toBe(4);
      expect((await app.inject({method:'GET',url:'/sw.js'})).body).toContain('offline.html');
      expect((await app.inject({method:'GET',url:'/icon-512.png'})).headers['content-type']).toBe('image/png');
      expect((await app.inject({method:'GET',url:'/'})).body).toContain('rel="manifest"');
      // 안드로이드 앱 내려받기(「내 갈매미」 맨 아래 설치 칸)
      // 파일은 git이 아니라 apk 브랜치에서 update.sh가 꺼내 둠 → 없으면 404(설치 칸 숨김)
      const info=await app.inject({method:'GET',url:'/app/apk.json'});
      // 앱이 새 버전을 확인할 수 있게(앱 주소에서 읽기 허용)
      expect((await app.inject({method:'GET',url:'/app/apk.json',headers:{origin:'https://localhost'}})).headers['access-control-allow-origin']).toBe('https://localhost');
      const apk=await app.inject({method:'GET',url:'/app/galmaegiman-suhbidae.apk'});
      if(existsSync(resolve(process.cwd(),'public/app/galmaegiman-suhbidae.apk'))){
        expect(info.statusCode).toBe(200);expect(info.json()).toMatchObject({versionName:expect.any(String),versionCode:expect.any(Number)});
        expect(apk.headers['content-type']).toBe('application/vnd.android.package-archive');expect(apk.headers['content-disposition']).toContain('attachment');
        expect(apk.rawPayload.subarray(0,2).toString()).toBe('PK');
      }else{expect(info.statusCode).toBe(404);expect(apk.statusCode).toBe(404);}
      expect((await app.inject({method:'GET',url:'/my'})).body).toContain('id="inst"');
    }finally{await app.close();}
  });
  it.each(['2','3'])('네이티브 카카오 앱 %s: 처음 연결은 코드 확인, 계정 연결은 현재 로그인 계정을 유지',async(appVersion)=>{
    let kakaoId=9090;
    const fakeFetch=(async(url:string|URL)=>new Response(JSON.stringify(String(url).includes('/oauth/token')?{access_token:'t'}:{id:kakaoId}),{status:200})) as typeof fetch;
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com',kakaoLogin:{restKey:'0123456789abcdef0123456789abcdef'},fetch:fakeFetch});
    const headers={'x-gmm':'1','x-gmm-app':'1'};
    const {createHash}=await import('node:crypto');const verifier='v'.repeat(50),challenge=createHash('sha256').update(verifier).digest('base64url');
    const exchange=(code:string)=>app.inject({method:'POST',url:'/my/api/app/exchange',headers,payload:{code,verifier}});
    const oauth=async(url:string)=>{
      const start=await app.inject({method:'GET',url});expect(start.statusCode).toBe(302);
      const state=new URL(String(start.headers.location)).searchParams.get('state')!;
      const cookie=(start.headers['set-cookie'] as string[]).map(c=>c.split(';')[0]).join('; ');
      const cb=await app.inject({method:'GET',url:`/my/kakao/callback?code=x&state=${state}`,headers:{cookie}});
      expect(new URL(String(cb.headers.location)).protocol).toBe(appVersion==='3'?'gmmrevised:':'gmmnative:');
      return new URL(String(cb.headers.location)).searchParams.get('code')!;
    };
    try{
      const one=await oauth(`/my/kakao?app=${appVersion}&c=${challenge}`),pending=(await exchange(one)).json();
      expect(pending.needsLink).toBe(true);expect(pending.token).toBeUndefined();expect((await exchange(one)).statusCode).toBe(400);
      const loginCode=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=await app.inject({method:'POST',url:'/my/api/login',headers,payload:{code:loginCode,linkCode:pending.linkCode}});
      expect(login.statusCode).toBe(200);const token=login.json().token;const player=await me();
      expect(await game.webKakaoPlayer('9090')).toBe(player.id);
      expect((await app.inject({method:'POST',url:'/my/api/login',headers,payload:{code:loginCode,linkCode:pending.linkCode}})).statusCode).toBe(400);
      expect((await app.inject({method:'POST',url:'/my/api/kakao/link-start',headers,payload:{challenge,app:appVersion}})).statusCode).toBe(401);
      kakaoId=9091;
      const startLink=await app.inject({method:'POST',url:'/my/api/kakao/link-start',headers:{...headers,'x-gmm-s':token},payload:{challenge,app:appVersion}});
      expect(startLink.statusCode).toBe(200);const url=startLink.json().result.url;
      const linked=await exchange(await oauth(url));expect(linked.statusCode).toBe(200);
      expect(linked.json().token.split('.')[0]).toBe(player.id);expect(await game.webKakaoPlayer('9091')).toBe(player.id);
      expect((await app.inject({method:'GET',url})).statusCode).toBe(400);
    }finally{await app.close();}
  });
  it('안드로이드 앱: 앱 주소만 허용(CORS), 토큰 머리글 로그인, 카카오 앱 로그인은 한 번 쓰는 코드+비밀값 교환',async()=>{
    const fakeFetch=(async(url:string|URL)=>String(url).includes('/oauth/token')?new Response(JSON.stringify({access_token:'t'}),{status:200}):new Response(JSON.stringify({id:8080}),{status:200})) as typeof fetch;
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com',kakaoLogin:{restKey:'0123456789abcdef0123456789abcdef'},fetch:fakeFetch});
    try{
      const pre=await app.inject({method:'OPTIONS',url:'/my/api/me',headers:{origin:'https://localhost','access-control-request-headers':'x-gmm,x-gmm-s'}});
      expect(pre.statusCode).toBe(204);expect(pre.headers['access-control-allow-origin']).toBe('https://localhost');
      expect((await app.inject({method:'GET',url:'/my/api/who',headers:{origin:'https://evil.example','x-gmm':'1'}})).headers['access-control-allow-origin']).toBeUndefined();
      const code=/(\d{6})/.exec((await send('웹 로그인')).text!)![1];
      const login=await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1',origin:'https://localhost'}});
      const token=(login.json() as {token:string}).token;expect(token).toMatch(/\./);
      expect((await app.inject({method:'GET',url:'/my/api/me',headers:{'x-gmm':'1','x-gmm-s':token}})).statusCode).toBe(200);
      expect((await app.inject({method:'GET',url:'/my/api/me',headers:{'x-gmm':'1','x-gmm-s':token.slice(0,-2)+'xx'}})).statusCode).toBe(401);
      // 「갈매미맨 수비대」 열쇠는 로그인한 사람만, 게임 데이터도 앱 주소에서 받을 수 있음
      expect((await app.inject({method:'GET',url:'/my/api/duel-key',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
      const k=((await app.inject({method:'GET',url:'/my/api/duel-key',headers:{'x-gmm':'1','x-gmm-s':token}})).json() as {k:string}).k;
      const dj=await app.inject({method:'GET',url:`/duel/data.json?k=${k}`,headers:{origin:'https://localhost'}});
      expect(dj.statusCode).toBe(200);expect(dj.headers['access-control-allow-origin']).toBe('https://localhost');
      // 카카오 앱 로그인(이미 연결된 계정)
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      await db.kakaoLink.create({data:{kakaoId:'8080',playerId:p.id}});
      const {createHash}=await import('node:crypto');
      const verifier='v'.repeat(50),challenge=createHash('sha256').update(verifier).digest('base64url');
      const start=await app.inject({method:'GET',url:`/my/kakao?app=1&c=${challenge}`});
      const state=new URL(String(start.headers.location)).searchParams.get('state')!;
      const ck=(start.headers['set-cookie'] as string[]).map(c=>c.split(';')[0]).filter(c=>!/=$/.test(c)).join('; ');
      const cb=await app.inject({method:'GET',url:`/my/kakao/callback?code=x&state=${state}`,headers:{cookie:ck}});
      const loc=String(cb.headers.location);expect(loc).toMatch(/^gmmapp:\/\/auth\?code=/);
      const one=new URL(loc).searchParams.get('code')!;
      const ex=(body:object)=>app.inject({method:'POST',url:'/my/api/app/exchange',payload:body,headers:{'x-gmm':'1',origin:'https://localhost'}});
      expect((await ex({code:one,verifier:'w'.repeat(50)})).statusCode).toBe(400);   // 비밀값 틀림(코드도 버려짐)
      const cb2=await app.inject({method:'GET',url:`/my/kakao/callback?code=x&state=${state}`,headers:{cookie:ck}});
      const two=new URL(String(cb2.headers.location)).searchParams.get('code')!;
      const okx=await ex({code:two,verifier});
      expect(okx.statusCode).toBe(200);
      const t2=(okx.json() as {token:string}).token;
      expect((await app.inject({method:'GET',url:'/my/api/who',headers:{'x-gmm':'1','x-gmm-s':t2}})).statusCode).toBe(200);
      expect((await ex({code:two,verifier})).statusCode).toBe(400);                  // 한 번만
      const nativeStart=await app.inject({method:'GET',url:`/my/kakao?app=2&c=${challenge}`});
      const nativeState=new URL(String(nativeStart.headers.location)).searchParams.get('state')!;
      const nativeCookie=(nativeStart.headers['set-cookie'] as string[]).map(c=>c.split(';')[0]).join('; ');
      const nativeCallback=await app.inject({method:'GET',url:`/my/kakao/callback?code=x&state=${nativeState}`,headers:{cookie:nativeCookie}});
      expect(nativeCallback.headers.location).toMatch(/^gmmnative:\/\/auth\?code=/);
      const nativeCode=new URL(String(nativeCallback.headers.location)).searchParams.get('code')!;
      expect((await ex({code:nativeCode,verifier})).statusCode).toBe(200);

    }finally{await app.close();}
    // 심사용 코드: 정해 둔 경우만, 심사 전용 계정으로
    const rv=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com',reviewCode:'424242'});
    try{
      const r=await rv.inject({method:'POST',url:'/my/api/login',payload:{code:'424242'},headers:{'x-gmm':'1','x-gmm-app':'1'}});
      expect(r.statusCode).toBe(200);
      const me=(await rv.inject({method:'GET',url:'/my/api/me',headers:{'x-gmm':'1','x-gmm-s':(r.json() as {token:string}).token}})).json() as {nickname:string};
      expect(me.nickname).toBe('심사용 수비대원');
    }finally{await app.close();}
  });
  it('공개 웹 랭킹: 카톡과 같은 순위, 닉네임 표시, 히든·최상위 가림, 관리자 모드 제외',async()=>{
    const mk=async(identity:string,nickname:string|null,ids:string[],adminMode=false)=>{
      const p=await db.player.create({data:{identity,nickname,tutorial:5,adminMode}});
      await db.collectionEntry.createMany({data:ids.map(characterId=>({playerId:p.id,characterId}))});
      return p;
    };
    await mk('r1','돌왕',['C1','C2','C3','H1']);
    const r2=await mk('r2',null,['C1','C2','D1']);
    await db.ownedCharacter.create({data:{playerId:r2.id,characterId:'D1',obtainedVia:'TEST'}});   // 최상위 랭킹은 보유 수 기준
    await mk('r3','관리자',['C1','C2','C3','C4','C5'],true);
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const r=await app.inject({method:'GET',url:'/rank'});
      expect(r.statusCode).toBe(200);
      const body=r.body;
      expect(body.indexOf('돌왕')).toBeGreaterThan(-1);
      expect(body.indexOf('돌왕')).toBeLessThan(body.indexOf('갈매미 #'));   // 4종 > 3종
      expect(body).not.toContain('관리자</b>');                              // 관리자 모드 제외
      expect(body).not.toContain(content.map.get('H1')!.name);              // 히든 이름 가림
      expect(body).not.toContain('thumbs/D1.jpg');                          // 최상위 그림 가림
      expect((await app.inject({method:'GET',url:'/rank?k=week'})).statusCode).toBe(200);
      expect((await app.inject({method:'GET',url:'/rank?k=top'})).body).toContain(content.map.get('D1')!.name);
    }finally{await app.close();}
  });
  it('공개 웹: 검색(이름·초성·닉네임)·조합법·히든 단서·상세·프로필·쿠폰 조각, 히든 이름 비공개',async()=>{
    const p1=await db.player.create({data:{identity:'w1',nickname:'돌왕',tutorial:5}});
    await db.collectionEntry.createMany({data:['C1','C2','U1','H1'].map(characterId=>({playerId:p1.id,characterId}))});
    await db.auditEvent.create({data:{playerId:p1.id,kind:'CRAFT',detail:{recipe:'H1',times:1}}});
    const app=await buildApp(db,game,{secret:'s'.repeat(40),baseUrl:async()=>'https://example.com'});
    try{
      const get=async(url:string,code=200)=>{const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(code);return r.body;};
      const hiddenNames=content.characters.filter(c=>c.rarity==='HIDDEN').map(c=>c.name);
      const noHidden=(html:string)=>expect(hiddenNames.filter(n=>html.includes(`>${n}<`)||html.includes(`${n}</`))).toEqual([]);
      // 검색: 이름, 초성, 닉네임
      const byName=await get('/search?q='+encodeURIComponent('금갑'));expect(byName).toContain('/dex/U1');
      expect(await get('/search?q='+encodeURIComponent('ㄱㄱ'))).toContain('/dex/U1');
      expect(await get('/search?q='+encodeURIComponent('돌'))).toContain('/p/'+encodeURIComponent('돌왕'));
      noHidden(await get('/search?q='+encodeURIComponent('갈매미')));
      // 자동완성 목록(히든 제외)·닉네임 API·등급/이야기 검색·재료 쓰임
      const idx=JSON.parse(await get('/search-index.json'));
      expect(idx.some((u:any)=>u.i==='U1')).toBe(true);expect(idx.some((u:any)=>u.i==='H1')).toBe(false);
      expect(JSON.parse(await get('/api/players?q='+encodeURIComponent('돌')))).toEqual(['돌왕']);
      expect(await get('/search?q='+encodeURIComponent('전설'))).toContain('전설 등급 23종');
      expect(await get('/search?q='+encodeURIComponent('엘리베이터'))).toContain('/dex/U1');
      expect(await get('/search?q='+encodeURIComponent('금갑'))).toContain('이 갈매미로 만들 수 있는 것');
      // 조합법·상세
      const rec=await get('/recipes');expect(rec).toContain('/dex/U1');noHidden(rec);
      const det=await get('/dex/U1');expect(det).toContain('만드는 법');expect(det).toContain('재료로 쓰이는 곳');
      // 히든 재료라는 사실도 비밀: R2(H1의 재료) 상세·검색에 히든 칩이 없음
      expect(await get('/dex/R2')).not.toContain('/hidden#h');expect(await get('/search?q='+encodeURIComponent('은하매듭직조자'))).not.toContain('/hidden#h');
      const top=await get('/dex/I1');expect(top).toContain('히든 제외');expect(top).toContain('합계에 넣지 않았어요');
      expect((await app.inject({method:'GET',url:'/dex/H1'})).statusCode).toBe(302);
      await get('/dex/ZZ9',404);
      // 계보도·기본 재료 합계·공유 미리보기, 히든 재료는 펼치지 않음
      const l1=await get('/dex/L1');expect(l1).toContain('조합 계보도');expect(l1).toContain('처음부터 만들면 필요한 기본 재료');
      expect(l1).toContain('og:image" content="https://gmm.dpdns.org/images/L1.png');
      const d1=await get('/dex/D1');expect(d1).toContain('조합 계보도');noHidden(d1);expect(d1).not.toContain('og:image" content="https://gmm.dpdns.org/images/D1.png');
      // 도감 작은 그림·가나다순, 홈 구성
      const small=await get('/dex?view=s&sort=name');expect(small).toContain('class="tiles"');noHidden(small);
      const homeHtml=await get('/');expect(homeHtml).toContain('href="https://pf.kakao.com/_GIAaX"');expect(homeHtml).toContain('오늘의 갈매미');expect(homeHtml).toContain('TOP 3');expect(homeHtml).toContain('최근 발견된 히든');expect(homeHtml).toContain('class="tabbar"');
      expect(homeHtml).toContain('href="/event"');
      // v0.16.0: 메인(/)은 「내 갈매미」 대시보드 + 로그인 전 소개 화면. 검색엔진용 정보는 그대로, /my는 색인 제외
      expect(homeHtml).toContain('<script src="/my/dash.js"></script>');expect(homeHtml).toContain('id="login"');expect(homeHtml).toContain('<title>갈매미맨</title>');
      expect(homeHtml).toContain('"@type":"WebSite"');expect(homeHtml).toContain('<link rel="canonical" href="https://gmm.dpdns.org/">');expect(homeHtml).not.toContain('noindex');
      expect(homeHtml).toContain('class="foot"');expect(homeHtml).not.toContain('/web/img/char-');
      expect(await get('/web/landing')).toContain('갈매미는 <span class="hl">존재</span>한다');
      expect(await get('/web/foot')).toContain('href="/privacy"');
      expect(await get('/my')).toContain('<meta name="robots" content="noindex">');
      const ev=await get('/event');
      expect(ev).toContain('친구도 나도 🎟 <b>100장</b>씩');expect(ev).toContain('튜토리얼 5단계를 끝내면 → 나도 🎟 100');expect(ev).toContain('최대 2,000장');
      expect(ev).toContain('5단계 · 단계마다 🎟 <b>10장</b> (총 50장)');expect(ev).toContain('<b>재료로 직접 조합해 보기</b>');expect(ev).toContain('<b>닉네임 정하기</b>');
      expect(ev).toContain('하루 최대 🎟 <b>230장</b>');expect(ev).toContain('진행 중');
      expect(ev).not.toContain('따개비도새우깡은못참지');
      expect(await get('/hidden')).toMatch(/class="more"[^]*href="\/hidden" class="on"/);
      // 히든: 1명 발견 → 초성 공개, 3명 전 → 재료 이름 잠김, 최초 발견자 표시
      const hid=await get('/hidden');noHidden(hid);
      expect(hid).toContain('재료 초성');expect(hid).toContain('최초 발견: <a href="/p/'+encodeURIComponent('돌왕'));
      expect(hid).toContain('🔒 단서 4');
      // 프로필
      const prof=await get('/p/'+encodeURIComponent('돌왕'));expect(prof).toContain('4/'+content.characters.length);expect(prof).toContain('1개</b>히든 최초 발견');
      await get('/p/'+encodeURIComponent('없는사람'),404);
      // 쿠폰 조각: 세계관·조합법·히든에 하나씩, 홈에 이벤트 배너
      const c=content.economy.coupons.find(x=>x.id==='HOMEPAGE_OPEN')!;
      expect(await get('/world')).toContain(`「${c.pieces[0]}」`);expect(rec).toContain(`「${c.pieces[1]}」`);expect(hid).toContain(`「${c.pieces[2]}」`);
      const home=await get('/');expect(home).toContain(c.label);expect(home).not.toContain(c.code);
    }finally{await app.close();}
  });
  it('탐험대 편성: 하나씩 추가·빼기(서버에 저장, 짧은 버튼 명령), 최대 10마리, 출발 후 다른 편성 불가',async()=>{
    await fixtures(['D1','D1','T1','E1','I1',...Array(8).fill('D7')]);
    const start=await send('탐험');
    expect(start.cards!.intro).toContain('탐험대 편성 0/10');
    const add=start.cards!.items.find(c=>c.title.includes('태초의 갈매미맨'))!.buttons![0];
    expect(add).toEqual({label:'➕ 추가',message:'편성추가 태초의 갈매미맨'});
    const one=await send(add.message);
    expect(one.cards!.intro).toContain('편성 1/10');
    expect(one.cards!.items[0]).toMatchObject({title:'✅ ⛓ 태초의 갈매미맨',buttons:[{label:'빼기',message:'편성빼기 태초의 갈매미맨'}]});
    expect(one.choices).toContainEqual({label:'🚀 출발 (1마리)',message:'편성출발'});
    // 다시 열어도 편성이 남아 있음
    expect((await send('탐험편성')).cards!.intro).toContain('편성 1/10');
    expect((await send('편성추가 태초의 갈매미맨')).cards!.intro).toContain('태초의 갈매미맨 ×2');
    expect((await send('편성빼기 태초의 갈매미맨')).cards!.intro).toContain('편성 1/10');
    expect((await send('편성추가 갈크탑 3')).cards!.intro).toContain('편성 4/10');
    expect((await send('편성비우기')).cards!.intro).toContain('편성 0/10');
    // 모든 카드 버튼은 짧은 명령(카카오 표시 한도)
    for(const c of one.cards!.items)for(const b of c.buttons??[])expect(Buffer.byteLength(b.message)).toBeLessThan(60);
    const full=await send('탐험편성 갈크탑 ×8, 태초의 갈매미맨 ×2');
    expect(full.cards!.intro).toContain('편성 10/10');
    expect(full.cards!.items.every(c=>c.title.startsWith('✅'))).toBe(true);
    expect((await send('편성추가 만상의 수호자')).text).toContain('최대 10마리');
    expect((await send('탐험편성 갈크탑 ×8, 태초의 갈매미맨 ×2, 만상의 수호자')).text).toContain('최대 10마리');
    expect((await send('탐험편성 태초의 갈매미맨 ×3')).text).toContain('2마리만');
    await confirm(await send(full.choices!.find(c=>c.label.startsWith('🚀'))!.message));
    const saved=[...Array(8).fill('D7'),'D1','D1'];
    expect((await game.webMe((await me()).id))!.party).toEqual(saved);
    const busy=await send('탐험편성 만상의 수호자');
    expect(busy.text).toContain('탐험 중');
    expect((await send('편성추가 만상의 수호자')).text).toContain('탐험 중');
    expect((await send('탐험보내기 만상의 수호자')).text).toContain('이미 탐험 중');
    now=new Date(now.getTime()+HOUR);
    await game.webClaimExpedition((await me()).id);
    expect((await game.webMe((await me()).id))!.party).toEqual(saved);
    await game.webStartExpedition((await me()).id);
    expect((await game.webMe((await me()).id))!.trip!.ids).toEqual(saved);
    expect((await game.webMe((await me()).id))!.party).toEqual(saved);
  });
  it('이벤트 기간: 뽑기권 받기 추가 지급과 뽑기 메뉴 배너',async()=>{
    const ev={label:'벚꽃 주간',from:'2026-10-01T00:00:00+09:00',to:'2026-10-08T00:00:00+09:00',claimBonus:10,snackMultiplier:2};
    game=new GameService(db,{...content,economy:{...content.economy,events:[ev]}},()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    expect((await send('뽑기권받기')).text).toContain('뽑기권 +20');
    const menuReply=await send('뽑기');
    expect(menuReply.cards!.items[0]).toMatchObject({imageId:'s-event',title:'🎉 벚꽃 주간'});
  });
  it('관리자 코드가 설정되지 않으면 관리자가 될 수 없음',async()=>{
    expect((await send('관리자 아무거나')).text).toContain('꺼져 있어요');
    expect((await me()).isAdmin).toBe(false);
  });
  it('종료된 기능 명령은 따로 안내하지 않고 도움말로, 재화는 그대로',async()=>{
    for(const cmd of ['출석','재료 기본','땅콩교환'])expect((await send(cmd)).text).toContain('갈매미맨 — 갈매미를 모아');
    expect((await me()).snack).toBe(0);
    expect((await me()).credits).toBe(30);
  });
  it('도감·조합목록 전체 페이지에 미발견 히든 이름이 나오지 않음',async()=>{
    await fixtures([]);
    const hiddenNames=content.hiddenIds.map(id=>content.map.get(id)!.name);
    for(const cmd of ['도감','조합목록 전설','조합목록 제한','조합목록 불멸','도감 히든','도감 제한']){
      let page=1,pages=1;
      do{
        const r=await send(`${cmd} ${page}`);
        const body=JSON.stringify(r);
        for(const name of hiddenNames)expect(body).not.toContain(name);
        pages=Number((r.list?.title??r.cards?.intro??'').match(/(\d+) \/ (\d+)/)?.[2]??1);
      }while(++page<=pages);
    }
  });
  it('댓글: 닉네임·만나 본 유닛만, 1분 1개·링크·금지어 차단, 좋아요(카톡·웹 1번씩), 관리자 숨김, 웹 표시·히든 가림',async()=>{
    const secret='comment-http-secret-'.repeat(3);
    const app=await buildApp(db,game,{secret,baseUrl:async()=>''});
    const page=async(url:string)=>{const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(200);return r.body;};
    const like=async(id:number,ip:string)=>app.inject({method:'POST',url:`/api/comments/${id}/like`,headers:{'cf-connecting-ip':ip}});
    try{
      const c1=content.map.get('C1')!.name;
      expect((await send(`댓글 ${c1} 안녕`)).text).toContain('닉네임이 있어야');
      await send('닉네임 수다왕');
      expect((await send(`댓글 ${c1} 안녕`)).text).toContain('아직 만나지 못했어요');
      const p=await fixtures(['C1']);await db.collectionEntry.create({data:{playerId:p.id,characterId:'C1'}});
      expect((await send(`댓글 ${c1} https://spam.example 놀러와`)).text).toContain('링크');
      expect((await send(`댓글 ${c1} 시 발`)).text).toContain('고운 말');
      const ok=await send(`댓글 ${c1} 너무 귀여워요 브라자 갈매미맨 보다 좋아`);
      expect(ok.text).toContain('댓글을 남겼어요');
      expect((await send(`댓글 ${c1} 또 쓸래`)).text).toContain('1분에 1개');
      const cm=await db.comment.findFirstOrThrow();
      expect((await send(`좋아요 ${cm.id}`)).text).toContain('내 댓글');
      await send('내정보','fan');
      expect((await send(`좋아요 ${cm.id}`,'fan')).text).toContain('👍 좋아요');
      expect((await send(`좋아요 ${cm.id}`,'fan')).text).toContain('이미');
      const view=await send(`댓글 ${c1}`,'fan');
      expect(view.text).toContain('수다왕');expect(view.text).toContain('👍 1');expect(view.text).not.toContain('브라자');
      // 웹: 도감·수다방·홈에 보이고, 히든 이름은 ???
      const dex=await page('/dex/C1');expect(dex).toContain('너무 귀여워요');expect(dex).not.toContain('브라자');
      expect(await page('/talk')).toContain('수다왕');expect(await page('/')).toContain('최근 한마디');
      expect((await like(cm.id,'198.51.100.1')).json()).toEqual({likes:2,already:false});
      expect((await like(cm.id,'198.51.100.1')).json()).toEqual({likes:2,already:true});
      expect((await like(cm.id,'198.51.100.2')).json()).toEqual({likes:3,already:false});
      expect((await like(99999,'198.51.100.1')).statusCode).toBe(404);
      // 히든 유닛 댓글은 웹에 안 보임
      await db.comment.create({data:{playerId:p.id,characterId:'H1',text:'히든 비밀 댓글'}});
      expect(await page('/talk')).not.toContain('히든 비밀 댓글');
      // 관리자 숨김 → 웹·카톡에서 사라짐, 다른 사람은 내 댓글 못 지움
      await db.player.update({where:{identity:'fan'},data:{isAdmin:true}});
      expect((await send(`관리자 댓글숨김 ${cm.id}`,'fan')).text).toContain('숨겼어요');
      expect(await page('/dex/C1')).not.toContain('너무 귀여워요');
      expect((await like(cm.id,'198.51.100.9')).statusCode).toBe(404);
      await send('내정보','other');
      expect((await send(`댓글삭제 ${cm.id}`,'other')).text).toContain('내 댓글 번호');
      expect((await send(`댓글삭제 ${cm.id}`)).text).toContain('지웠어요');
      expect(await db.comment.count({where:{id:cm.id}})).toBe(0);
    }finally{await app.close();}
  });
  it('땅콩 상점: 땅콩 1 → 뽑기권 20, 땅콩 3 → 특별 골라 받기, 닉네임 장식 사기·달기·빼기(랭킹에 표시)',async()=>{
    await fixtures([],{credits:0});
    await db.player.update({where:{identity:actor},data:{peanut:10,nickname:'땅콩부자'}});
    const shop=await send('상점 땅콩');
    expect(shop.cards!.items.map(i=>i.title)).toEqual(['🎟 뽑기권 20장','✦ 특별 골라 받기','🎀 닉네임 장식']);
    await confirm(await send('상점 땅콩뽑기권'));
    expect(await me()).toMatchObject({credits:20,peanut:9});
    const list=await send('교환 특별');
    expect(list.cards!.items[0].description).toContain('🥜 3');
    const s1=content.characters.find(c=>c.rarity==='SPECIAL')!;
    await confirm(await send(`교환 ${s1.name}`));
    expect((await me()).peanut).toBe(6);
    expect(await db.ownedCharacter.count({where:{characterId:s1.id}})).toBe(1);
    await confirm(await send('장식 사기 왕관'));
    expect(await me()).toMatchObject({peanut:1,badge:'CROWN'});
    expect((await send('장식 사기 별')).text).toContain('땅콩이 부족');
    expect((await send('장식 사기 왕관')).text).toContain('이미 가지고');
    expect((await send('랭킹')).text).toContain('👑 땅콩부자');
    await send('장식 빼기');expect((await me()).badge).toBeNull();
    await send('장식 달기 왕관');expect((await me()).badge).toBe('CROWN');
  });
  it('새 히든(중성 갈매미 재료): 회의 중 갈매미·바스락상회 사장·인사팀장 갈매미를 재료로 직접 조합해서 발견',async()=>{
    for(const [mats,name] of [[['N1','N1','N1','C1'],'회의 중 갈매미'],[['N1','C2','U1'],'바스락상회 사장'],[['N1','R17','R19'],'인사팀장 갈매미']] as const){
      await fixtures([...mats]);
      const names=[...new Set(mats)].map(id=>{const n=mats.filter(x=>x===id).length;return `${content.map.get(id)!.name}${n>1?` ×${n}`:''}`;}).join(', ');
      const preview=await send(`조합 ${names}`);
      expect(preview.text).toContain('???');expect(JSON.stringify(preview)).not.toContain(name);
      expect((await confirm(preview)).text).toContain(name);
    }
  });
  it('HTTP 요청 제한: 웹은 방문자(CF-Connecting-IP)마다, 카톡은 사람마다 따로',async()=>{
    const secret='rate-limit-http-secret-'.repeat(3);
    const app=await buildApp(db,game,{secret,baseUrl:async()=>''});
    try{
      const web=async(ip:string)=>(await app.inject({method:'GET',url:'/event',headers:{'cf-connecting-ip':ip}})).statusCode;
      for(let i=0;i<300;i++)expect(await web('203.0.113.1')).toBe(200);
      expect(await web('203.0.113.1')).toBe(429);
      expect(await web('203.0.113.2')).toBe(200);
      const talk=async(id:string)=>(await app.inject({method:'POST',url:'/kakao/skill',headers:{'x-skill-secret':secret},payload:{bot:{id:'bot'},userRequest:{utterance:'확률',user:{id}}}})).json().template.outputs.map((o:any)=>JSON.stringify(o)).join('');
      for(let i=0;i<40;i++)expect(await talk('fast-user')).not.toContain('너무 빨리');
      expect(await talk('fast-user')).toContain('너무 빨리');
      expect(await talk('calm-user')).not.toContain('너무 빨리');
    }finally{await app.close();}
  });
  it('HTTP: 목록 선택→조합→숨긴 토큰 확정, 다른 사용자 차단, 재전송 1회 처리',async()=>{
    const secret='readability-http-secret-'.repeat(3);
    const app=await buildApp(db,game,{secret,baseUrl:async()=>'https://example.com'});
    const request=async(utterance:string,clientExtra:object|null=null,id='flow-user')=>{
      const response=await app.inject({method:'POST',url:'/kakao/skill',headers:{'x-skill-secret':secret},payload:{bot:{id:'bot'},userRequest:{utterance,user:{id}},action:{clientExtra}}});
      expect(response.statusCode).toBe(200);return response.json();
    };
    const selected=(response:any)=>response.template.outputs.find((output:any)=>output.listCard).listCard.items[0];
    const firstButton=(response:any)=>response.template.outputs.find((output:any)=>output.carousel).carousel.items[0].buttons[0];
    try{
      expect((await app.inject({method:'GET',url:'/health'})).json()).toMatchObject({edition:'GALMAEMI_118',version:'0.16.2'});
      for(const f of ['m-draw.jpg','ticket.jpg','gacha-MID.jpg','C1.jpg','s-oops.jpg','s-info.jpg','unknown.jpg'])expect((await app.inject({method:'GET',url:`/thumbs/${f}`})).statusCode).toBe(200);
      for(const f of ['X99.jpg','secret.jpg','..%2Fenv.jpg'])expect((await app.inject({method:'GET',url:`/thumbs/${f}`})).statusCode).toBe(404);
      await request('하급뽑기 2');
      const flow=await db.player.findFirstOrThrow({where:{identity:{contains:'flow-user'}}});
      for(const id of ['S1','U1'])await db.ownedCharacter.create({data:{playerId:flow.id,characterId:id,obtainedVia:'TEST'}});
      const list=await request('조합목록 희귀');
      expect(list.template.outputs.find((o:any)=>o.carousel).carousel.items[0].thumbnail.imageUrl).toMatch(/^https:\/\/example\.com\/thumbs\/(?:q-RARE|unknown)\.jpg(?:\?v=[0-9a-z]+)?$/);// 아직 못 얻은 R1은 그림 대신 실루엣
      const item=firstButton(list);
      expect(item).toEqual({action:'message',label:'만들기',messageText:'조합 황금쌍패성기사'});
      const confirmItem=selected(await request(item.messageText));
      expect(confirmItem.messageText).toBe('조합 확정');
      const stolen=await request(confirmItem.messageText,confirmItem.extra,'other-user');
      expect(stolen.template.outputs[0].basicCard.title).toContain('본인의');
      const done=await request(confirmItem.messageText,confirmItem.extra);
      expect(await request(confirmItem.messageText,confirmItem.extra)).toEqual(done);
      expect(await db.ownedCharacter.count({where:{characterId:'R1'}})).toBe(1);
    }finally{await app.close();}
  });
  it('네이티브 동일 이름 전투의 표시 이벤트는 개체와 실제 HP·교체 상태를 식별하고 재요청에 중복되지 않는다',async()=>{
    await fixtures(['D1']);
    const app=await buildApp(db,game,{secret:'native-display-events-test'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=(await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1'}})).json();
      const headers={'x-gmm':'1','x-gmm-s':login.token};
      const post=async(path:string,payload:object)=>{const r=await app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});expect(r.statusCode,r.body).toBe(200);return r.json();};
      let b=await post('battle/start',{mode:'practice',roster:['D1','D1'],enemyRoster:['D1','D1'],level:30});
      expect(b.displayEventVersion).toBe(1);expect(b.displayEventSeq).toBe(0);expect(b.displayEvents).toEqual([]);expect(b.displayLogStart).toBe(0);
      expect(b.teams[0].fighters[0].name).toBe(b.teams[1].fighters[0].name);
      let seq=0;const health=new Map<string,number>();const active=[0,0];const observed:any[]=[];
      for(const [side,team] of b.teams.entries())for(const [slot,f] of team.fighters.entries())health.set(`${side}:${slot}`,f.hp);
      const validate=(snapshot:any)=>{
        expect(snapshot.displayEventSeq).toBeGreaterThanOrEqual(seq);
        const fresh=snapshot.displayEvents.filter((e:any)=>e.id>seq);
        for(const e of fresh){
          expect(e.id).toBe(seq+1);seq=e.id;observed.push(e);
          for(const ref of [e.actor,e.target].filter(Boolean)){
            expect([0,1]).toContain(ref.side);const f=snapshot.teams[ref.side].fighters[ref.slot];
            expect(ref).toMatchObject({key:f.key,id:f.id});
          }
          if(e.target&&e.hpAfter!==undefined){
            const key=`${e.target.side}:${e.target.slot}`;
            expect(e.hpBefore).toBe(health.get(key));
            expect(e.hpAfter).toBeGreaterThanOrEqual(0);expect(e.hpAfter).toBeLessThanOrEqual(e.maxHp);
            if(e.kind==='faint'){
              // 전투불가는 앞선 피해의 결과를 알리는 HP 스냅샷이며 추가 피해가 아니다.
              expect(e.hpBefore).toBe(0);expect(e.hpAfter).toBe(0);expect(e.amount).toBeUndefined();
            }else{
              expect(['damage','heal','revive']).toContain(e.kind);
              expect(e.amount).toBe(Math.abs(e.hpAfter-e.hpBefore));
            }
            health.set(key,e.hpAfter);
          }
          if(e.kind==='switch')active[e.target.side]=e.target.slot;
        }
        expect(seq).toBe(snapshot.displayEventSeq);
        for(const [side,team] of snapshot.teams.entries()){
          expect(team.active).toBe(active[side]);
          for(const [slot,f] of team.fighters.entries())expect(f.hp).toBe(health.get(`${side}:${slot}`));
        }
      };
      let switched=false;
      for(let i=0;i<14&&b.phase!=='over';i++){
        let event:object;
        if(b.phase==='choose')event={choose:{augment:b.teams[0].offers.find((x:any)=>x.id!=='rustle')?.id??b.teams[0].offers[0].id,...(b.teams[0].needSwitch?{switchTo:b.teams[0].fighters.findIndex((f:any)=>f.hp>0)}:{})}};
        else if(!switched&&b.teams[0].legal.some((a:any)=>a.kind==='switch'&&a.to===1)){event={action:{kind:'switch',to:1}};switched=true;}
        else event={action:b.teams[0].legal.find((a:any)=>a.kind==='skill'&&a.skillId.startsWith('basic'))??b.teams[0].legal.find((a:any)=>a.kind==='skill')};
        const command={session:b.session,revision:b.revision,...event};b=await post('battle/turn',command);validate(b);
        const duplicate=await post('battle/turn',command);
        expect(duplicate.displayEventSeq).toBe(b.displayEventSeq);expect(duplicate.displayEvents).toEqual(b.displayEvents);expect(duplicate.revision).toBe(b.revision);
        if(observed.some(e=>e.kind==='damage'&&e.target.side===0)&&observed.some(e=>e.kind==='damage'&&e.target.side===1)&&switched)break;
      }
      expect(observed.some(e=>e.kind==='switch'&&e.target.side===0&&e.target.slot===1)).toBe(true);
      expect(observed.filter(e=>e.kind==='damage').map(e=>e.target.side)).toEqual(expect.arrayContaining([0,1]));
      const bad=await app.inject({method:'POST',url:'/my/api/native/battle/turn',headers,payload:{session:b.session,revision:b.revision,action:{kind:'skill',skillId:'forged'}}});
      if(b.phase!=='over')expect(bad.statusCode).toBe(400);
      const current=(await app.inject({method:'GET',url:'/my/api/native/battle/current',headers})).json();
      if(b.phase!=='over'){expect(current.displayEventSeq).toBe(b.displayEventSeq);expect(current.displayEvents).toEqual(b.displayEvents);}
    }finally{await app.close();}
  });

  it('아이템 저장 실패 시 임시 HP 변경과 표시 이벤트도 원래 전투에 남지 않는다',async()=>{
    const p=await fixtures(['D1']);const key=`o${(await game.webStoryUnits(p.id))[0].oid}`;
    await game.webSaveStory(p.id,{units:{[key]:{level:30,xp:0,stage:0,evoAugs:[]}},team:[key],cleared:[],hp:{[key]:{f:.25,at:Date.now()}}});
    const app=await buildApp(db,game,{secret:'native-display-rollback-test'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=(await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1'}})).json();
      const headers={'x-gmm':'1','x-gmm-s':login.token};
      const post=async(path:string,payload:object)=>app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});
      let b=(await post('battle/start',{mode:'story',stage:'P-1'})).json();
      b=(await post('battle/turn',{session:b.session,revision:b.revision,choose:{augment:b.teams[0].offers.find((x:any)=>x.id!=='rustle')?.id??b.teams[0].offers[0].id}})).json();
      const failed=await post('battle/turn',{session:b.session,revision:b.revision,action:{kind:'item',item:'potion'}});
      expect(failed.statusCode).toBe(400);
      const current=(await app.inject({method:'GET',url:'/my/api/native/battle/current',headers})).json();
      expect(current.revision).toBe(b.revision);expect(current.displayEventSeq).toBe(b.displayEventSeq);expect(current.displayEvents).toEqual(b.displayEvents);
      expect(current.log).toEqual(b.log);expect(current.teams).toEqual(b.teams);
    }finally{await app.close();}
  });

  it('네이티브 전투는 소유권·행동을 검사하고 같은 리비전의 중복 요청을 한 번만 처리한다',async()=>{
    await fixtures(['D1']);
    const app=await buildApp(db,game,{secret:'native-test-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1'}});
      const headers={'x-gmm':'1','x-gmm-s':login.json().token};
      const post=(path:string,payload:object)=>app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});
      expect((await app.inject({method:'GET',url:'/my/api/native/state',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
      expect((await post('team',{keys:['o99999999']})).statusCode).toBe(400);
      const state=(await app.inject({method:'GET',url:'/my/api/native/state',headers})).json();expect(state.units[0].xpNext).toBeGreaterThan(0);
      expect(state.practiceUnits).toHaveLength(33);
      expect((await post('battle/start',{mode:'story',stage:'P-1',roster:['I9'],level:60})).statusCode).toBe(400);
      const start=(await post('battle/start',{mode:'practice',stage:'P-1',roster:['I9'],enemyRoster:['T7'],level:22,promotion:1})).json();
      expect(start.teams[0].fighters[0]).toMatchObject({id:'I9',level:22,stage:1});expect(start.teams[1].fighters[0].id).toBe('T7');
      expect((await post('battle/start',{mode:'practice'})).statusCode).toBe(400);
      expect((await post('battle/turn',{session:start.session,revision:0,choose:{augment:'forged'}})).statusCode).toBe(400);
      const pick={session:start.session,revision:0,choose:{augment:start.teams[0].offers[0].id}};
      const responses=await Promise.all([post('battle/turn',pick),post('battle/turn',pick)]);
      expect(responses.map(r=>r.statusCode)).toEqual([200,200]);expect(responses.map(r=>r.json().revision)).toEqual([1,1]);
      const current=responses[0].json();expect(current.turn).toBe(1);
      expect((await post('battle/turn',{session:start.session,revision:1,action:{kind:'skill',skillId:'forged'}})).statusCode).toBe(400);
      const still=(await app.inject({method:'GET',url:'/my/api/native/battle/current',headers})).json();expect(still.revision).toBe(1);expect(still.log).toEqual(current.log);
      const otherCode=(await send('웹 로그인','native-other')).text!.match(/\b\d{6}\b/)![0];
      const other=(await app.inject({method:'POST',url:'/my/api/login',payload:{code:otherCode},headers:{'x-gmm':'1','x-gmm-app':'1'}})).json();
      expect((await app.inject({method:'POST',url:'/my/api/native/battle/turn',payload:{session:start.session,revision:1,leave:true},headers:{'x-gmm':'1','x-gmm-s':other.token}})).statusCode).toBe(400);
      const before=await game.webStory((await me()).id);
      await post('battle/turn',{session:start.session,revision:1,leave:true});expect(await game.webStory((await me()).id)).toEqual(before);
    }finally{await app.close();}
  });
  it('증강체 제한 시간은 재추첨으로 늘지 않고 30초 뒤 현재 선택지에서 한 번만 무작위 확정한다',async()=>{
    await fixtures(['D1']);
    const app=await buildApp(db,game,{secret:'augment-deadline-test'.repeat(3),baseUrl:async()=>'https://example.com'});
    let clock:ReturnType<typeof vi.spyOn>|undefined;
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];
      const login=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}});
      const headers={'x-gmm':'1','x-gmm-s':login.json().token};
      const post=async(payload:object)=>(await app.inject({method:'POST',url:'/my/api/native/battle/turn',headers,payload})).json();
      const start=(await app.inject({method:'POST',url:'/my/api/native/battle/start',headers,payload:{mode:'practice'}})).json();
      expect(start.chooseDeadline-start.serverNow).toBeLessThanOrEqual(30000);
      expect(start.chooseDeadline-start.serverNow).toBeGreaterThan(29000);
      const reroll=await post({session:start.session,revision:start.revision,reroll:0});
      expect(reroll.chooseDeadline).toBe(start.chooseDeadline);
      clock=vi.spyOn(Date,'now').mockReturnValue(start.chooseDeadline+1);
      const current=(await app.inject({method:'GET',url:'/my/api/native/battle/current',headers})).json();
      expect(current.phase).toBe('action');expect(current.chooseDeadline).toBeUndefined();
      expect(current.revision).toBe(reroll.revision+1);
      const chosen=current.teams[0].augments.at(-1).id;
      expect(reroll.teams[0].offers.map((x:{id:string})=>x.id)).toContain(chosen);
      const duplicate=await post({session:start.session,revision:reroll.revision,choose:{augment:reroll.teams[0].offers[0].id}});
      expect(duplicate.revision).toBe(current.revision);expect(duplicate.teams[0].augments).toEqual(current.teams[0].augments);
    }finally{clock?.mockRestore();await app.close();}
  });
  it('진행 중인 전투에서는 로비 회복·성장·편성 변경을 막고 치료약을 보존한다',async()=>{
    const p=await fixtures(['D1']);const key=`o${(await game.webStoryUnits(p.id))[0].oid}`;
    await game.webSaveStory(p.id,{units:{[key]:{level:11,xp:0,stage:0,evoAugs:[]}},team:[key],cleared:[],hp:{[key]:{f:.25,at:Date.now()}}});
    await db.playerItem.create({data:{playerId:p.id,itemId:'potion',count:1}});
    const app=await buildApp(db,game,{secret:'native-idle-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];const token=(await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1'}})).json().token;
      const headers={'x-gmm':'1','x-gmm-s':token};const post=(path:string,payload:object)=>app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});
      const b=(await post('battle/start',{mode:'story',stage:'P-1'})).json();
      for(const [path,payload] of [['heal',{key,item:'potion'}],['growth',{key,kind:'evolve',augment:'reflect'}],['team',{keys:[key]}]] as const){const r=await post(path,payload);expect(r.statusCode).toBe(400);expect(r.json().error).toContain('진행 중인 전투');}
      expect((await app.inject({method:'POST',url:'/my/api/items/use-field',headers,payload:{id:'potion'}})).statusCode).toBe(400);
      expect((await app.inject({method:'POST',url:'/my/api/story',headers,payload:{data:await game.webStory(p.id)}})).statusCode).toBe(400);
      expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}}})).count).toBe(1);
      expect((await post('battle/turn',{session:b.session,revision:0,leave:true})).statusCode).toBe(200);
      expect((await post('heal',{key,item:'potion'})).statusCode).toBe(200);
      const saved=await game.webStory(p.id) as {hp:Record<string,{f:number}>};expect(saved.hp[key].f).toBeGreaterThanOrEqual(.59);
      expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}}})).count).toBe(0);
    }finally{await app.close();}
  });

  it('네이티브 성장 보상은 종료 때 한 번만 저장하고 레벨업 체력을 가득 회복한다',async()=>{
    const p=await fixtures(['D1']);const owned=await game.webStoryUnits(p.id),key=`o${owned[0].oid}`;
    const R=loadBattleRules();await game.webSaveStory(p.id,{units:{[key]:{level:5,xp:xpToNext(R,5)-1,stage:0,evoAugs:[]}},team:[key],cleared:[],hp:{[key]:{f:1,at:Date.now()}}});
    const app=await buildApp(db,game,{secret:'native-test-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{const code=(await send('웹 로그인')).text!.match(/\b\d{6}\b/)![0];const token=(await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1','x-gmm-app':'1'}})).json().token;
      const headers={'x-gmm':'1','x-gmm-s':token};const post=async(path:string,payload:object)=>{const r=await app.inject({method:'POST',url:`/my/api/native/${path}`,headers,payload});expect(r.statusCode,r.body).toBe(200);return r.json();};
      let b=await post('battle/start',{mode:'story',stage:'P-1'}),last:object={};
      for(let g=0;g<100&&b.phase!=='over';g++){
        const event=b.phase==='choose'?{choose:{...(b.teams[0].offers.length?{augment:b.teams[0].offers[0].id}:{}),...(b.teams[0].needSwitch?{switchTo:b.teams[0].fighters.findIndex((f:{hp:number})=>f.hp>0)}:{})}}:{action:b.teams[0].legal.find((a:{kind:string})=>a.kind==='skill')};
        last={session:b.session,revision:b.revision,...event};b=await post('battle/turn',last);
      }
      expect(b.phase).toBe('over');expect(b.result.xp).toBeGreaterThan(0);const saved=await game.webStory(p.id) as {units:Record<string,{level:number}>;hp:Record<string,{f:number}>};expect(saved.units[key].level).toBeGreaterThan(5);expect(saved.hp[key].f).toBe(1);
      const repeated=await post('battle/turn',last);expect(repeated.revision).toBe(b.revision);expect(await game.webStory(p.id)).toEqual(saved);
    }finally{await app.close();}
  });

  it('네이티브 아이템 차감·진행 저장은 실패하면 모두 되돌아간다',async()=>{
    const p=await fixtures(['D1']);await db.playerItem.create({data:{playerId:p.id,itemId:'potion',count:2}});
    await expect(game.webNativeCommit(p.id,{id:'potion',name:'치료약'},{units:null,team:[]})).rejects.toThrow();
    expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}}})).count).toBe(2);
    expect(await game.webStory(p.id)).toBeNull();expect(await db.auditEvent.count({where:{playerId:p.id,kind:'ITEM_USE'}})).toBe(0);
    await game.webNativeCommit(p.id,{id:'potion',name:'치료약'},{units:{},team:[],cleared:[],hp:{}});
    expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.id,itemId:'potion'}}})).count).toBe(1);
    expect(await game.webStory(p.id)).toMatchObject({cleared:[]});
  });

});
