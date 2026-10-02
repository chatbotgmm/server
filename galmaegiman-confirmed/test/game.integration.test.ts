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
      db.pendingAction.deleteMany(),db.auditEvent.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany()
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

  it('신규 30장, 첫 받기는 즉시 +10, 5시간 쿨타임, 늦어도 1회분만',async()=>{
    expect((await send('내정보')).text).toContain('뽑기권 30장');
    expect((await send('뽑기권 받기')).text).toContain('10장을 받았습니다');
    expect((await me()).credits).toBe(40);
    expect((await send('뽑기권받기')).text).toContain('5시간 뒤에');
    now=new Date(now.getTime()+4*HOUR+59*60000);
    expect((await send('받기')).text).toContain('1분 뒤에');
    now=new Date(now.getTime()+20*HOUR);
    await send('받기');
    expect((await me()).credits).toBe(50);
  });
  it('뽑기 비용 하급 2 / 중급 3 / 고급 5, 여러 번 뽑기, 부족하면 차감 없음',async()=>{
    expect((await send('하급뽑기')).text).toContain('흔함 등장');
    expect((await me()).credits).toBe(28);
    await send('중급 뽑기');expect((await me()).credits).toBe(25);
    await send('뽑기 고급');expect((await me()).credits).toBe(20);
    const multi=await send('하급뽑기 5');
    expect(multi.text).toContain('하급 뽑기 5회 결과');
    expect((await me()).credits).toBe(10);
    expect((await send('고급뽑기 3')).text).toContain('뽑기권이 부족합니다');
    expect((await me()).credits).toBe(10);
    expect(await db.ownedCharacter.count()).toBe(8);
  });
  it('조합 미리보기는 소비하지 않고 확정 시 재료 소비 + 첫 도감 보상 50',async()=>{
    await fixtures(['C1','C1','C2']);
    const preview=await send('조합 금갑');
    expect(preview.text).toContain('조합 준비 완료');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(0);
    const done=await confirm(preview);
    expect(done.text).toContain('안흔함 조합 완성');
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
    expect((await send('조합목록')).text).toContain('히든 · 발견 0/13종');
    expect((await send('조합목록 히든')).text).toContain('아직 발견한 히든 조합이 없습니다');
    expect((await send('도감 브라자 갈매미맨')).text).toContain('이름을 찾지 못했습니다');
    const dex=await send('도감 히든');
    expect(dex.list!.items[0].title).toBe('???');
    expect(JSON.stringify(dex)).not.toContain('브라자');
    expect((await send('합치기 황금쌍패성기사, 심해중력군주')).text).toContain('아무 일도 일어나지 않았다');
    const preview=await send('합치기 황금쌍패성기사, 은하매듭직조자');
    expect(preview.text).toContain('???');
    expect(JSON.stringify(preview)).not.toContain('브라자');
    const done=await confirm(preview);
    expect(done.text).toContain('숨은 조합을 발견했습니다');
    expect(done.text).toContain('브라자 갈매미맨');
    expect((await send('조합목록 히든')).list!.items[0].title).toBe('브라자 갈매미맨');
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
    expect((await send('교환 기본')).text).toContain('최상위 유닛');
    await fixtures(['D1']);
    const preview=await send('교환 기본');
    expect(preview.text).toContain('새우깡 150개');
    const done=await confirm(preview);
    expect(done.text).toContain('교환 완료');
    expect((await me()).snack).toBe(150);
    expect(await db.ownedCharacter.count({where:{characterId:'C1'}})).toBe(1);
  });
  it('탐험: 출발→잠김→1시간 뒤 보상 받기를 눌러야 지급→복귀',async()=>{
    const basic=content.characters.filter(c=>c.synergy==='BASIC').map(c=>c.id);
    await fixtures([...basic,'C1','C1','C2']);
    const preview=await send(`탐험보내기 ${basic.map(id=>content.map.get(id)!.name).join(', ')}`);
    expect(preview.text).toContain('기본 4마리 → 2단계');
    expect(preview.text).toContain('시너지 반영 52개');
    await confirm(preview);
    expect(await db.ownedCharacter.count({where:{status:'EXPEDITION'}})).toBe(4);
    expect((await send('잠금 태초의 갈매미맨')).text).toContain('할 수 있는 개체가 없습니다');
    expect((await send('탐험보내기 자동')).text).toContain('이미 탐험 중');
    expect((await send('내정보')).text).toContain('탐험 중');
    expect((await send('탐험보상받기')).text).toContain('아직 탐험 중');
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
    expect(preview.text).toContain('파견 10마리');
    await confirm(preview);
    now=new Date(now.getTime()+HOUR);
    expect((await send('탐험보상받기')).text).toContain('앙~~~~ 갈매미맨이야!!!!');
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
        pages=Number(r.list?.title.match(/(\d+) \/ (\d+)/)?.[2]??1);
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
    try{
      expect((await app.inject({method:'GET',url:'/health'})).json()).toMatchObject({edition:'GALMAEMI_118',version:'0.3.0'});
      await request('하급뽑기 2');
      const flow=await db.player.findFirstOrThrow({where:{identity:{contains:'flow-user'}}});
      for(const id of ['C1','C1','C2'])await db.ownedCharacter.create({data:{playerId:flow.id,characterId:id,obtainedVia:'TEST'}});
      const item=selected(await request('조합목록 안흔함'));
      expect(item.title).toBe('금갑');
      const confirmItem=selected(await request(item.messageText));
      expect(confirmItem.messageText).toBe('조합 확정');
      const stolen=await request(confirmItem.messageText,confirmItem.extra,'other-user');
      expect(stolen.template.outputs[0].simpleText.text).toContain('본인의');
      const done=await request(confirmItem.messageText,confirmItem.extra);
      expect(await request(confirmItem.messageText,confirmItem.extra)).toEqual(done);
      expect(await db.ownedCharacter.count({where:{characterId:'U1'}})).toBe(1);
    }finally{await app.close();}
  });
});
