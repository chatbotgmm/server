import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {GachaEngine} from '../src/gacha.js';
import {GameError,type GameReply} from '../src/types.js';
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
      db.pendingAction.deleteMany(),db.adminSnapshot.deleteMany(),db.auditEvent.deleteMany(),db.achievement.deleteMany(),db.dailyProgress.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany()
    ]);
    now=new Date('2026-10-02T08:00:00+09:00');
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
    expect(multi.text).toContain('━━━━━━━━━━\n🎟');
    expect(multi.cards?.items).toEqual([{imageId:'C1',title:'🥚 기본 갈매미맨 ×5',description:'흔함',buttons:[{label:'도감 보기',message:'도감 기본 갈매미맨'}]}]);
    expect(multi.cards?.outro).toContain('━━━━━━━━━━\n🎟');
    // 뽑기 8회째에 '뽑기 5회' 미션 달성 → +20
    expect(multi.text).toContain('오늘의 미션 완료');
    expect((await me()).credits).toBe(30);
    expect((await send('고급뽑기 10')).text).toContain('뽑기권이 모자라요');
    expect((await me()).credits).toBe(30);
    expect(await db.ownedCharacter.count()).toBe(8);
  });
  it('조합 미리보기는 소비하지 않고 확정 시 재료 소비 + 첫 도감 보상 50',async()=>{
    await fixtures(['C1','C1','C2']);
    const preview=await send('조합 금갑');
    expect(preview.text).toContain('재료가 모두 모였어요');
    expect(preview.text).not.toContain('가리키기');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(0);
    const done=await confirm(preview);
    expect(done.text).toContain('조합 성공! 새 안흔함 발견');
    expect(done.text).not.toContain('💬');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(3);
    expect((await me()).snack).toBe(50);
  });
  it('같은 확정 요청 10개 동시 재전송: 소비·지급 한 번',async()=>{
    await fixtures(['C1','C1','C2']);
    const preview=await send('조합 금갑');
    const results=await Promise.all(Array.from({length:10},()=>confirm(preview)));
    expect(results.every(r=>JSON.stringify(r)===JSON.stringify(results[0]))).toBe(true);
    expect(await db.ownedCharacter.count({where:{characterId:'U1'}})).toBe(1);
  });
  it('히든: 목록·도감·이름 검색에서 숨김, 합치기로 발견하면 공개',async()=>{
    await fixtures(['R1','R2']);
    expect((await send('조합목록')).text).toContain('히든 · 발견 0/13');
    const hint=await send('조합목록 히든');
    expect(hint.cards!.items[0]).toMatchObject({imageId:'unknown',title:'???',description:'❔ 힌트: 방패 두 장을 매듭으로 묶었다. 왜 그 모양인지는 묻지 말 것.'});
    expect(hint.cards!.items).toHaveLength(10);
    expect(JSON.stringify(hint)).not.toContain('브라자');
    expect((await send('도감 브라자 갈매미맨')).text).toContain('그런 갈매미는 없어요');
    const dex=await send('도감 히든');
    expect(dex.cards!.items[0]).toMatchObject({title:'???',imageId:'unknown'});
    expect(JSON.stringify(dex)).not.toContain('브라자');
    expect((await send('합치기 황금쌍패성기사, 심해중력군주')).text).toContain('아무 일도 일어나지 않았다');
    const preview=await send('합치기 황금쌍패성기사, 은하매듭직조자');
    expect(preview.text).toContain('???');
    expect(JSON.stringify(preview)).not.toContain('브라자');
    const done=await confirm(preview);
    expect(done.text).toContain('숨은 조합을 발견했습니다');
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
  it('교환: 최상위 유닛이 없으면 불가, 있으면 새우깡 150으로 흔함 1마리',async()=>{
    await fixtures([],{snack:300});
    expect((await send('교환 기본')).text).toContain('최상위 갈매미');
    await fixtures(['D1']);
    const preview=await send('교환 기본');
    expect(preview.text).toContain('새우깡 150개');
    const done=await confirm(preview);
    expect(done.text).toContain('기본 갈매미맨');
    expect((await me()).snack).toBe(150);
    expect(await db.ownedCharacter.count({where:{characterId:'C1'}})).toBe(1);
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
    expect((await send('탐험보상받기')).text).toContain('진행 중인 탐험이 없습니다');
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
    for(const id of ['C2','C3','C4','C5','C6'])await db.collectionEntry.create({data:{playerId:p.id,characterId:id}});
    const done=await confirm(await send('교환 기본'));
    expect(done.text).toContain('흔함 도감 완성');
    expect(done.text).toContain('바닷가 산책자');
    expect((await me()).credits).toBe(35);
    await confirm(await send('교환 기본'));
    expect((await me()).credits).toBe(35);
    expect((await send('칭호')).text).toContain('칭호 1/10');
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
    expect(dup.text).toContain('기본 갈매미맨 (2번째)');
    expect(dup.text).not.toContain('도감 기록');
    expect(dup.text).toContain('지금 조합 가능 1종');
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
    expect(one.cards!.outro).toContain('━━━━━━━━━━');
    const preview=await send('조합 금갑');
    expect(preview.cards!.items[0]).toMatchObject({imageId:'U1',title:'🧩 🔹 금갑'});
    expect(preview.cards!.outro).toContain('✅');
    expect(preview.list!.items[0].button!.action).toBe('confirm');
    const done=await confirm(preview);
    expect(done.cards!.items[0]).toMatchObject({imageId:'U1',title:'🔹 금갑'});
    expect(done.cards!.items[0].description).toContain('🧩 조합 성공!');
    const dex=await send('도감');
    expect(dex.text).toContain('📚 도감 ·');
    expect(dex.cards!.items).toHaveLength(10);
    expect(dex.cards!.items[5]).toMatchObject({imageId:'unknown',title:'❔ 히든'});
    const grades=await send('조합목록');
    expect(grades.cards!.items).toHaveLength(9);
    expect(grades.cards!.items[0].buttons![0]).toEqual({label:'목록 보기',message:'조합목록 안흔함'});
    const common=await send('도감 흔함');
    expect(common.cards!.items).toHaveLength(6);
    expect(common.cards!.items.find(c=>c.title.includes('우주'))!.imageId).toBe('unknown');
    expect(common.cards!.items.find(c=>c.title.includes('기본'))!.imageId).toBe('C1');
    const info=await send('내정보');
    expect(info.cards!.items[0].imageId).toBe('U1');
    const menuReply=await send('뽑기');
    expect(menuReply.cards!.items.map(c=>c.buttons!.length)).toEqual([3,3,3]);
    expect(menuReply.list).toBeUndefined();
    expect((await send('뭐야이건')).cards!.items).toHaveLength(6);
    expect((await send('도감 금갑')).cards!.outro).toContain('💬');
  });
  it('고급 뽑기 천장: 100회 안에 전설 확정, 전설이 나오면 다시 0부터',async()=>{
    // 난수 0 = 항상 특별만 나오는 상황
    await fixtures([],{credits:1000});
    await db.player.update({where:{identity:actor},data:{highPity:95}});
    const r=await send('고급뽑기 5');
    expect(r.text).toContain('🎯 천장! 전설 확정');
    expect(r.text).toContain('★ 전설');
    expect((await me()).highPity).toBe(0);
    expect((await send('고급뽑기')).text).toContain('🎯 전설 보장까지 99회');
    expect((await send('뽑기')).cards!.items[2].description).toContain('전설 보장까지 99회');
    expect((await send('확률')).text).toContain('천장: 100회 안에 전설 확정');
  });
  it('조합 직전 알림: 재료 1마리만 모자라면 다음 안내에 표시',async()=>{
    // 1마리 차이인 조합이 여럿이면 높은 등급 하나를 안내(여기선 특별 유성대장장이 = 운석 + 기본 + 황금)
    await fixtures(['C2','C2'],{credits:30});
    const r=await send('하급뽑기');   // 난수 0 → 기본 갈매미맨 1마리
    const m=r.text.match(/🔜 \S+ (.+)까지 (.+) 1마리!/);
    expect(m).not.toBeNull();
    expect(r.choices!.some(c=>c.message===`조합식 ${m![1]}`)).toBe(true);
  });
  it('랭킹·닉네임: 도감 수 순위, 닉네임 중복 불가, 관리자 모드 제외',async()=>{
    await fixtures(['C1','C2']);
    for(const id of ['C1','C2'])await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:id}});
    expect((await send('닉네임 갈매미왕')).text).toContain('갈매미왕');
    const other=await db.player.create({data:{identity:'other-rank',nickname:'둘째'}});
    await db.collectionEntry.create({data:{playerId:other.id,characterId:'C3'}});
    const rank=await send('랭킹');
    expect(rank.cards!.items.map(i=>i.title)).toEqual(['🥇 갈매미왕','🥈 둘째']);
    expect(rank.cards!.items[0].description).toContain('📚 2/118');
    expect(rank.text).toContain('내 순위: 1위 / 2명');
    expect((await send('닉네임 갈매미왕','other-rank')).text).toContain('이미 누가');
    expect((await send('닉네임 a')).text).toContain('2~10자');
  });
  it('일일 미션: 희귀·전설/히든·최상위 조합은 결과 등급으로 달성, 한 번만 지급',async()=>{
    await fixtures(['S1','U1','R1','R20','L2','H1','S1','U1'],{credits:0});
    await db.collectionEntry.create({data:{playerId:(await me()).id,characterId:'H1'}});
    const rare=await confirm(await send('조합 황금쌍패성기사'));
    expect(rare.text).toContain('🎯 오늘의 미션 완료! 희귀 조합하기 · 🎟 +30');
    const legend=await confirm(await send('조합 쌍패성왕'));
    expect(legend.text).toContain('🎯 오늘의 미션 완료! 전설 또는 히든 조합하기 · 🎟 +50');
    expect(legend.text).not.toContain('조합 3회');
    const top=await confirm(await send('조합 갈크탑'));
    expect(top.text).toContain('🎯 오늘의 미션 완료! 최상위 유닛 조합하기 · 🎟 +100');
    expect(top.text).toContain('🎯 오늘의 미션 완료! 조합 3회 · 🎟 +20');
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
  it('관리자 코드가 설정되지 않으면 관리자가 될 수 없음',async()=>{
    expect((await send('관리자 아무거나')).text).toContain('꺼져 있어요');
    expect((await me()).isAdmin).toBe(false);
  });
  it('종료된 기능(출석·재료·땅콩)은 안내만 하고 재화를 바꾸지 않음',async()=>{
    for(const cmd of ['출석','재료 기본','땅콩떼기 금갑'])expect((await send(cmd)).text).toContain('종료');
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
      expect((await app.inject({method:'GET',url:'/health'})).json()).toMatchObject({edition:'GALMAEMI_118',version:'0.6.4'});
      await request('하급뽑기 2');
      const flow=await db.player.findFirstOrThrow({where:{identity:{contains:'flow-user'}}});
      for(const id of ['C1','C1','C2'])await db.ownedCharacter.create({data:{playerId:flow.id,characterId:id,obtainedVia:'TEST'}});
      const item=firstButton(await request('조합목록 안흔함'));
      expect(item).toEqual({action:'message',label:'만들기',messageText:'조합 금갑'});
      const confirmItem=selected(await request(item.messageText));
      expect(confirmItem.messageText).toBe('조합 확정');
      const stolen=await request(confirmItem.messageText,confirmItem.extra,'other-user');
      expect(stolen.template.outputs[0].basicCard.title).toContain('본인의');
      const done=await request(confirmItem.messageText,confirmItem.extra);
      expect(await request(confirmItem.messageText,confirmItem.extra)).toEqual(done);
      expect(await db.ownedCharacter.count({where:{characterId:'U1'}})).toBe(1);
    }finally{await app.close();}
  });
});
