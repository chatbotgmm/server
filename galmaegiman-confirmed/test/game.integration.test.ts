import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {GachaEngine} from '../src/gacha.js';
import {GameError,type GameReply} from '../src/types.js';
import {cardCost,CAROUSEL_TEXT_BUDGET,kakaoResponse} from '../src/kakao.js';
const enabled=Boolean(process.env.TEST_DB_URL);
const suite=enabled?describe:describe.skip;
suite('격리 SQLite 실제 트랜잭션 (v0.3)',()=>{
  let db:PrismaClient,game:GameService;
  let now=new Date('2026-10-02T08:00:00+09:00');
  const content=loadContent();
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
      db.commentLike.deleteMany(),db.comment.deleteMany(),db.pendingAction.deleteMany(),db.adminSnapshot.deleteMany(),db.auditEvent.deleteMany(),db.achievement.deleteMany(),db.dailyProgress.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany()
    ]);
    now=new Date('2026-10-02T08:00:00+09:00');
    // 기본 테스트 계정은 튜토리얼을 마친 상태(튜토리얼 보상은 별도 테스트)
    await db.player.create({data:{identity:actor,tutorial:4,createdAt:now}});
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
    expect((await send('조합목록')).text).toContain('히든 · 발견 0/18');
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
    expect((await send('합치기 황금쌍패성기사, 심해중력군주')).text).toContain('심해중력군주이(가) 부족해요');
    // 재료가 없으면 히든 조합이든 아니든 같은 답(정답 떠보기 방지)
    const probeHit=(await send('합치기 황금쌍패성기사, 은하매듭직조자','prober')).text,probeMiss=(await send('합치기 황금쌍패성기사, 심해중력군주','prober')).text;
    expect(probeHit).toBe(probeMiss);expect(probeHit).toContain('황금쌍패성기사이(가) 부족해요');
    expect((await send('합치기 황금쌍패성기사, 황금쌍패성기사')).text).toContain('부족해요');
    const preview=await send('합치기 황금쌍패성기사, 은하매듭직조자');
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
    expect(preview.text).toContain('새우깡 150개');
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
    expect(shop.cards!.items.map(i=>i.title)).toEqual(['🥚 흔함 고르기','🔹 안흔함 고르기','🎟 뽑기권 10장','🍤 남는 갈매미 팔기','🧭 탐험 즉시 귀환']);
    const before=(await me()).credits;
    const tickets=await send('상점 뽑기권');
    expect(tickets.list!.items[0].message).toBe('구매 확정');
    await confirm(tickets);
    expect(await me()).toMatchObject({snack:150,credits:before+10});
    expect((await send('상점 탐험귀환')).text).toContain('바로 돌아오게 할 탐험이 없어요');
    expect((await send('상점 뽑기권')).text).toContain('새우깡이 부족해요');
  });
  it('탐험: 출발→잠김→1시간 뒤 보상 받기를 눌러야 지급→복귀',async()=>{
    const basic=content.characters.filter(c=>c.synergy==='BASIC').map(c=>c.id);
    await fixtures([...basic,'C1','C1','C2']);
    const preview=await send(`탐험보내기 ${basic.map(id=>content.map.get(id)!.name).join(', ')}`);
    expect(preview.text).toContain('기본 ×4 (2단계)');
    expect(preview.text).toContain('예상 52개');
    await confirm(preview);
    expect(await db.ownedCharacter.count({where:{status:'EXPEDITION'}})).toBe(4);
    expect((await send('잠금 태초의 갈매미맨')).text).toContain('없어요');
    expect((await send('탐험보내기 자동')).text).toContain('이미 탐험 중');
    expect((await send('내정보')).text).toContain('탐험 중');
    expect((await send('탐험보상받기')).text).toContain('🧭 탐험 중 ·');
    now=new Date(now.getTime()+HOUR);
    expect((await send('내정보')).text).toContain('보상 받기 대기');
    const back=await send('탐험 보상 받기');
    expect(back.text).toContain('새우깡 +52');
    expect((await me()).snack).toBe(52);
    expect(await db.ownedCharacter.count({where:{status:'EXPEDITION'}})).toBe(0);
    expect((await send('탐험보상받기')).text).toContain('진행 중인 탐험이 없어요');
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
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1,process.cwd(),code);
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
    // 끄면 모드 동안 뽑은 유닛·도감·새우깡·미션·뽑기권이 켜기 전으로
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
    expect(rank.cards!.items[0].description).toContain('📚 2/142');
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
    const pick=await send('합치기 황금쌍패성기사, 은하');
    const button=pick.cards!.items.find(c=>c.title.includes('은하매듭직조자'))!.buttons![0];
    expect(button).toEqual({label:'이걸로',message:'합치기 황금쌍패성기사, 은하매듭직조자'});
    expect((await send(button.message)).text).toContain('???');
  });
  it('합치기 카드로 재료 고르기: 첫 재료 → 두 번째 재료 → 바로 미리보기',async()=>{
    await fixtures(['R1','R2','C1']);
    const step1=await send('합치기');
    expect(step1.cards!.items.map(c=>c.title)).toEqual(['◆ 황금쌍패성기사','◆ 은하매듭직조자','🥚 기본 갈매미맨']);
    expect(step1.cards!.items[0].buttons![0]).toEqual({label:'첫 재료로',message:'합치기 황금쌍패성기사 다음'});
    const step2=await send('합치기 황금쌍패성기사 다음');
    // 황금쌍패성기사는 1마리뿐이라 두 번째 재료 후보에서 빠짐
    expect(step2.cards!.items.map(c=>c.title)).toEqual(['◆ 은하매듭직조자','🥚 기본 갈매미맨']);
    // 카드마다 버튼 1개(버튼 2개 카드 10장은 카카오가 표시하지 않음)
    expect(step2.cards!.items.every(c=>c.buttons!.length===1)).toBe(true);
    expect(step2.cards!.items[0].buttons![0]).toEqual({label:'재료로 고르기',message:'합치기 황금쌍패성기사, 은하매듭직조자 다음'});
    const step3=await send(step2.cards!.items[0].buttons![0].message);
    // 재료가 2개 이상이면 맨 앞 카드가 '이대로 섞기'
    expect(step3.cards!.items.map(c=>c.title)).toEqual(['🔮 이대로 섞기','🥚 기본 갈매미맨']);
    const mix=step3.cards!.items[0].buttons![0];
    expect(mix).toEqual({label:'섞기',message:'합치기 황금쌍패성기사, 은하매듭직조자'});
    const preview=await send(mix.message);
    expect(preview.cards!.items[0].imageId).toBe('unknown');
    expect(preview.list!.items[0].button!.action).toBe('confirm');
    expect((await send('조합목록 히든')).cards!.items[0].buttons![0]).toEqual({label:'재료 골라 합치기',message:'합치기'});
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
    const preview=await send('합치기 중성 갈매미 ×2, 천체금고주, 금조개');
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
    expect(info.choices!.some(c=>c.message==='상점')).toBe(true);
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
    expect(r.text).toContain('📘 튜토리얼 1/4 완료! 🎟 +10');
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
    const finish=await send('닉네임 친구왕','friend');
    expect(finish.text).toContain('초대해 준 친구에게도 뽑기권 100장');
    expect((await me()).credits).toBe(30-2+10+100);
    // 다시 끝내도(관리자 되돌리기 등) 두 번 주지 않음
    await db.player.update({where:{identity:'friend'},data:{tutorial:3}});
    await send('닉네임 친구왕이','friend');
    expect((await me()).credits).toBe(30-2+10+100);
    // 튜토리얼을 이미 끝낸 사람이 코드를 넣으면 초대한 쪽도 바로
    await send('내정보','late');await db.player.update({where:{identity:'late'},data:{tutorial:4}});
    expect((await send(`초대 ${code}`,'late')).text).toContain('초대한 친구도 🎟 +100');
    expect((await me()).credits).toBe(30-2+10+200);
  });
  it('튜토리얼 4단계 닉네임: 정하면 완료, 이미 정해 둔 사람은 도감 단계에서 이어서·옛 완료자는 튜토리얼에서 완료',async()=>{
    await send('내정보');
    await db.player.update({where:{identity:actor},data:{tutorial:3,credits:0}});
    expect((await send('뽑기')).choices!.some(c=>c.message==='튜토리얼')).toBe(true);
    expect((await send('튜토리얼')).cards!.items[4].title).toContain('👉 4. 닉네임 정하기');
    const set=await send('닉네임 튜토왕');
    expect(set.text).toContain('📘 튜토리얼 4/4 완료! 🎟 +10');
    expect(set.text).toContain('튜토리얼 끝');
    expect((await me()).tutorial).toBe(4);
    expect((await send('닉네임 튜토왕2')).text).not.toContain('튜토리얼');
    // 닉네임이 있는 채로 도감 단계 → 4단계까지 한 번에
    await db.player.update({where:{identity:actor},data:{tutorial:2,credits:0}});
    const dex=await send('도감');
    expect(dex.text).toContain('3/4 완료');
    expect(dex.text).toContain('4/4 완료');
    expect((await me()).credits).toBe(20);
    // 예전에 3단계로 끝낸 사람(닉네임 있음): 튜토리얼 버튼은 안 보이고, 튜토리얼을 열면 완료 처리
    await db.player.update({where:{identity:actor},data:{tutorial:3,credits:0}});
    expect((await send('뽑기')).choices!.some(c=>c.message==='튜토리얼')).toBe(false);
    expect((await send('튜토리얼')).text).toContain('4/4 완료');
    expect((await me()).tutorial).toBe(4);
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
    {const t=(await send('이벤트')).text;expect(t).toContain('진행 중인 기간 이벤트는 없어요');expect(t).toContain('💌 친구 초대: 친구는 바로, 나는 친구가 튜토리얼을 끝내면 뽑기권 100장씩 (최대 20명)');expect(t).toContain('📘 튜토리얼: 4단계');}
    const ev={label:'벚꽃 주간',from:'2026-10-01T00:00:00+09:00',to:'2026-10-08T00:00:00+09:00',claimBonus:10,snackMultiplier:2};
    game=new GameService(db,{...content,economy:{...content.economy,events:[ev]}},()=>now,new GachaEngine(content.characters,content.economy.gachas,()=>0),max=>max-1);
    const r=await send('이벤트');
    expect(r.text).toContain('벚꽃 주간');
    expect(r.text).toContain('새우깡 보상 ×2');
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
    expect(r.cards!.outro).toContain('기본 갈매미맨 0/2 · 하급 뽑기 / 교환 / 탐험(바다 시너지)');
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
      const world=await get('/world');
      expect(world).toContain('<h1>갈매미 섬 이야기</h1>');
      expect(world).toContain('class="prose tale"');expect(world).toContain('갈매미 섬 항구 수비대');expect(world).not.toContain('<table>');
      const dex=await get('/dex');
      const hidden=content.characters.filter(c=>c.rarity==='HIDDEN');
      const top=content.characters.filter(c=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity));
      expect(hidden.some(c=>dex.includes(c.name))).toBe(false);          // 히든 이름 비공개
      expect(hidden.every(c=>!c.hint||dex.includes(c.hint))).toBe(true); // 힌트는 공개
      expect(top.some(c=>dex.includes(c.introduction))).toBe(false);     // 최상위 이야기 비공개
      expect(top.some(c=>dex.includes(`thumbs/${c.id}.jpg`))).toBe(false); // 최상위 그림 비공개
      expect(dex).toContain(content.map.get('C1')!.introduction);
    }finally{await app.close();}
  });
  it('공개 웹 랭킹: 카톡과 같은 순위, 닉네임 표시, 히든·최상위 가림, 관리자 모드 제외',async()=>{
    const mk=async(identity:string,nickname:string|null,ids:string[],adminMode=false)=>{
      const p=await db.player.create({data:{identity,nickname,tutorial:4,adminMode}});
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
    const p1=await db.player.create({data:{identity:'w1',nickname:'돌왕',tutorial:4}});
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
      const ev=await get('/event');
      expect(ev).toContain('친구도 나도 🎟 <b>100장</b>씩');expect(ev).toContain('튜토리얼 4단계를 끝내면 → 나도 🎟 100');expect(ev).toContain('최대 2,000장');
      expect(ev).toContain('4단계 · 단계마다 🎟 <b>10장</b> (총 40장)');expect(ev).toContain('<b>닉네임 정하기</b>');
      expect(ev).toContain('하루 최대 🎟 <b>230장</b>');expect(ev).toContain('진행 중');
      expect(ev).not.toContain('따개비도새우깡은못참지');
      expect(await get('/hidden')).toMatch(/class="tabbar"[^]*href="\/recipes" class="on"/);
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
    expect((await me()).partyDraft).toBe('');
    const busy=await send('탐험편성 만상의 수호자');
    expect(busy.text).toContain('탐험 중');
    expect((await send('편성추가 만상의 수호자')).text).toContain('탐험 중');
    expect((await send('탐험보내기 만상의 수호자')).text).toContain('이미 탐험 중');
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
      expect((await app.inject({method:'GET',url:'/health'})).json()).toMatchObject({edition:'GALMAEMI_118',version:'0.9.39'});
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
});
