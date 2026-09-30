import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {GachaEngine,loadGachaWeights} from '../src/gacha.js';
import type {GameReply} from '../src/types.js';
import {kakaoResponse} from '../src/kakao.js';
import {mkdtemp,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {readRefillSettings,changeRefillAmount} from '../src/refill-settings.js';
const enabled=Boolean(process.env.TEST_DB_URL);
const suite=enabled?describe:describe.skip;
suite('격리 SQLite 실제 트랜잭션',()=>{
  let db:PrismaClient,game:GameService;
  let now=new Date('2026-09-18T08:00:00+09:00');
  const content=loadContent();
  const actor='test-actor';
  const button=(r:GameReply)=>r.list!.items.find(item=>item.button?.action==='confirm')!;
  const send=(text:string,id=actor)=>game.handle(id,text);
  const confirm=(r:GameReply,id=actor)=>game.handle(id,button(r).message,button(r).button);
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(async()=>{
    await db.$transaction([
      db.pendingAction.deleteMany(),db.auditEvent.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.player.deleteMany()
    ]);
    now=new Date('2026-09-18T08:00:00+09:00');
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),()=>0));
  });
  afterAll(async()=>db.$disconnect());
  async function bankroll(credits=1000){
    await send('내정보');
    return db.player.update({where:{identity:actor},data:{credits}});
  }
  async function fixtures(ids:string[]){
    const p=await bankroll();
    for(const characterId of ids)await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});
    return p;
  }
  it('선택 교환 미리보기는 소비하지 않으며 확정 후에만 지급',async()=>{
    const preview=await send('재료 C001M');
    expect(preview.imageId).toBe('C001M');
    expect(await db.ownedCharacter.count()).toBe(0);
    const result=await confirm(preview);
    expect(result.imageId).toBe('C001M');
    expect((await db.player.findUniqueOrThrow({where:{identity:actor}})).credits).toBe(2);
    expect(await db.collectionEntry.count()).toBe(1);
  });
  it('같은 확정 요청 10개 동시 재전송: 지급/소비 한 번',async()=>{
    const preview=await send('재료 C001M');
    const results=await Promise.all(Array.from({length:10},()=>confirm(preview)));
    expect(results.every(r=>JSON.stringify(r)===JSON.stringify(results[0]))).toBe(true);
    expect(await db.ownedCharacter.count()).toBe(1);
    expect((await db.player.findFirstOrThrow()).credits).toBe(2);
  });
  it('다른 사용자 토큰 사용 차단',async()=>{
    const preview=await send('재료 C001M');
    await expect(confirm(preview,'other-user')).rejects.toThrow('본인의');
    expect(await db.ownedCharacter.count()).toBe(0);
  });
  it('만료된 미리보기는 소비 없음',async()=>{
    const preview=await send('재료 C001M');now=new Date(now.getTime()+601000);
    await expect(confirm(preview)).rejects.toThrow('만료');expect(await db.ownedCharacter.count()).toBe(0);
  });
  it('성공한 토큰은 만료 후에도 같은 결과 회신',async()=>{
    const preview=await send('재료 C001M');const result=await confirm(preview);now=new Date(now.getTime()+601000);
    expect(await confirm(preview)).toEqual(result);expect(await db.ownedCharacter.count()).toBe(1);
  });
  it('뽑기는 즉시 랜덤 지급하며 첫 획득 희귀/중복 모두 가능',async()=>{
    const rolls=[90,0,90,0];let cursor=0;
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),()=>rolls[cursor++]));
    const first=await send('뽑기'),second=await send('뽑기');
    expect(first.imageId).toBe('R001M');expect(second.imageId).toBeUndefined();
    expect(first.text).toContain('등장!');expect(first.choices?.some(c=>c.label==='확정')).toBe(false);
    expect(await db.ownedCharacter.count({where:{characterId:'R001M',obtainedVia:'GACHA'}})).toBe(2);
    expect((await db.collectionEntry.findFirstOrThrow()).count).toBe(2);
    expect((await db.player.findFirstOrThrow()).credits).toBe(1);
  });
  it('동시 뽑기 10개도 보유 3장을 초과 소비하지 않음',async()=>{
    const results=await Promise.all(Array.from({length:10},()=>send('뽑기')));
    expect(results.filter(r=>r.text.includes('등장!'))).toHaveLength(3);
    expect(await db.ownedCharacter.count()).toBe(3);
    expect((await db.player.findFirstOrThrow()).credits).toBe(0);
    expect(await db.auditEvent.count({where:{kind:'GACHA'}})).toBe(3);
  });
  it('랜덤 결과 지급 실패 시 획득권/도감/로그 모두 원복',async()=>{
    await send('내정보');const before=await db.player.findFirstOrThrow();
    await db.$executeRawUnsafe("CREATE TRIGGER fail_draw BEFORE INSERT ON OwnedCharacter WHEN NEW.obtainedVia = 'GACHA' BEGIN SELECT RAISE(ABORT, 'TEST_DRAW_FAILURE'); END;");
    try{await expect(send('뽑기')).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_draw');}
    const after=await db.player.findFirstOrThrow();
    expect(after.credits).toBe(before.credits);expect(after.revision).toBe(before.revision);
    expect(await db.ownedCharacter.count()).toBe(0);expect(await db.collectionEntry.count()).toBe(0);
    expect(await db.auditEvent.count({where:{kind:'GACHA'}})).toBe(0);
  });
  it('이전 버전의 미완료 순서 지급은 새 랜덤 뽑기로 안내',async()=>{
    const p=await bankroll();
    await db.pendingAction.create({data:{token:'0123456789abcdef',playerId:p.id,revision:p.revision,kind:'RECEIVE',payload:{characterId:'R001M',cost:'CREDIT'},expiresAt:new Date(now.getTime()+600000)}});
    await expect(send('확정 0123456789abcdef')).rejects.toThrow('뽑기 방식');
    expect(await db.ownedCharacter.count()).toBe(0);expect((await db.player.findFirstOrThrow()).credits).toBe(1000);
  });
  it('이전 버전 완료 요청 재전송도 번호를 숨기고 추가 지급 없음',async()=>{
    const p=await fixtures(['C003F']);
    const saved={text:'갈매미맨 C003F 확정 획득!\n개체 #123',imageId:'C003F'};
    await db.pendingAction.create({data:{token:'0123456789abcdef',playerId:p.id,revision:p.revision,kind:'RECEIVE',payload:{characterId:'C003F',cost:'CREDIT',source:'MATERIAL'},result:saved,expiresAt:new Date(now.getTime()-1)}});
    const reply=await send('확정 0123456789abcdef');
    expect(reply.text).toContain('비닐봉지 갈매미맨');
    expect(reply.text).not.toMatch(/C003F|#123/);
    expect(await send('확정 0123456789abcdef')).toEqual(reply);
    expect(await db.ownedCharacter.count()).toBe(1);
    expect((await db.player.findFirstOrThrow()).credits).toBe(p.credits);
    expect((await db.pendingAction.findFirstOrThrow()).result).toEqual(saved);
  });
  it('도움말/버튼은 뽑기, 내정보는 다음 캐릭터를 예고하지 않음',async()=>{
    const help=await send('도움말');expect(help.text).toContain('뽑기');expect(help.choices?.[0]).toEqual({label:'뽑기',message:'뽑기'});
    const info=await send('내정보');expect(info.text).not.toContain('다음 지급');expect(info.text).toContain('희귀 10%');
    expect((await send('확률')).text).toContain('흔함 40% · 안흔함 30% · 특별 20% · 희귀 10%');
    expect((await send('받기')).text).toContain('등장!');
  });
  it('09/12/18 지급은 누적되고 재시작에도 중복 없음',async()=>{
    await send('내정보');now=new Date('2026-09-18T09:00:00+09:00');await send('내정보');
    expect((await db.player.findFirstOrThrow()).credits).toBe(13);
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),()=>0));await send('내정보');
    expect((await db.player.findFirstOrThrow()).credits).toBe(13);
    now=new Date('2026-09-19T12:00:00+09:00');await send('내정보');
    expect((await db.player.findFirstOrThrow()).credits).toBe(53);
  });
  it('선택 재료는 중복 획득 가능',async()=>{
    await confirm(await send('재료 C001M'));await confirm(await send('재료 C001M'));
    expect(await db.ownedCharacter.count({where:{characterId:'C001M'}})).toBe(2);
  });
  it('획득권 부족 시 추가 캐릭터 없음',async()=>{
    await send('뽑기');await send('뽑기');await send('뽑기');
    expect((await send('뽑기')).text).toContain('획득권이 없습니다');
    expect(await db.ownedCharacter.count()).toBe(3);
  });
  it('75종 도감을 완성해도 등록 여부로 추첨 후보를 제외하지 않음',async()=>{
    const p=await bankroll();
    for(const c of content.characters)await db.collectionEntry.create({data:{playerId:p.id,characterId:c.id}});
    const reply=await send('뽑기');
    expect(reply.text).toContain('새우깡봉지 갈매미맨');
    expect(reply.text).toContain('등장!');
    expect((await db.collectionEntry.findUniqueOrThrow({where:{playerId_characterId:{playerId:p.id,characterId:'C001M'}}})).count).toBe(2);
  });
  it('소비한 개체의 도감 등록은 유지',async()=>{
    await confirm(await send('재료 C001M'));await confirm(await send('재료 C003F'));
    await confirm(await send('조합 U001M'));
    expect(await db.collectionEntry.count()).toBe(3);
    expect(await db.ownedCharacter.count({where:{status:'AVAILABLE'}})).toBe(1);
  });
  it('2개 미리보기 중 먼저 확정된 하나만 소비',async()=>{
    const a=await send('재료 C001M'),b=await send('재료 C001M');await confirm(a);
    await expect(confirm(b)).rejects.toThrow('보유 상태');
    expect(await db.ownedCharacter.count()).toBe(1);
  });
  it('재료 부족/잠금/표식 전환/다른 사용자 재료는 사용하지 않음',async()=>{
    const p=await fixtures(['C001M']);
    expect((await send('조합 U001M')).text).toContain('부족');
    const other=await db.player.create({data:{identity:'other',lastGrantAt:now}});
    await db.ownedCharacter.create({data:{playerId:other.id,characterId:'C003F',obtainedVia:'TEST'}});
    expect((await send('조합 U001M')).text).toContain('부족');
    const c=await db.ownedCharacter.create({data:{playerId:p.id,characterId:'C003F',obtainedVia:'TEST',locked:true}});
    expect((await send('조합 U001M')).text).toContain('부족');
    await send(`잠금해제 ${c.id}`);const r=await confirm(await send('조합 U001M'));
    expect(r.imageId).toBe('U001M');
  });
  it('동일 종류 재료 2개는 서로 다른 개체여야 함',async()=>{
    await fixtures(['C001M']);expect((await send('조합 U005M')).text).toContain('부족');
    await confirm(await send('재료 C001M'));await confirm(await send('조합 U005M'));
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(2);
  });
  it('조합 확인 뒤 잠금하면 오래된 확정은 취소',async()=>{
    await fixtures(['C001M','C003F']);const preview=await send('조합 U001M');
    const c=await db.ownedCharacter.findFirstOrThrow();await send(`잠금 ${c.id}`);
    await expect(confirm(preview)).rejects.toThrow('보유 상태');
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(0);
  });
  it('조합 69개 모두 실제 소비/지급 가능',async()=>{
    const p=await bankroll();
    for(const recipe of content.recipes){
      for(const characterId of recipe.materials)await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});
      const reply=await confirm(await send(`조합 ${recipe.resultId}`));
      expect(reply.imageId).toBe(recipe.resultId);
      expect(reply.text).toContain(content.flavors.get(recipe.resultId)!.fusion);
      expect(reply.text).toContain('첫 발견');
      expect(reply.text.length).toBeLessThanOrEqual(500);
    }
    expect(await db.collectionEntry.count()).toBe(69);
  },30000);
  it('원자적 처리: 결과 지급 실패 시 재료와 revision 되돌림',async()=>{
    await fixtures(['C001M','C003F']);const preview=await send('조합 U001M');
    const before=await db.player.findFirstOrThrow();
    await db.$executeRawUnsafe("CREATE TRIGGER fail_grant BEFORE INSERT ON OwnedCharacter WHEN NEW.obtainedVia = 'COMBINATION' BEGIN SELECT RAISE(ABORT, 'TEST_FAILURE'); END;");
    try{await expect(confirm(preview)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_grant');}
    expect(await db.ownedCharacter.count({where:{status:'AVAILABLE'}})).toBe(2);
    expect((await db.player.findFirstOrThrow()).revision).toBe(before.revision);
    expect((await confirm(preview)).imageId).toBe('U001M');
  });
  it('희귀 수컷 표식은 1회만 전환, 희귀 미만 불가',async()=>{
    await fixtures(['C001M','R001M','R001F']);
    const low=await db.ownedCharacter.findFirstOrThrow({where:{characterId:'C001M'}});
    await expect(send(`땅콩떼기 ${low.id}`)).rejects.toThrow('희귀 수컷');
    const female=await db.ownedCharacter.findFirstOrThrow({where:{characterId:'R001F'}});
    await expect(send(`땅콩떼기 ${female.id}`)).rejects.toThrow('희귀 수컷');
    const rare=await db.ownedCharacter.findFirstOrThrow({where:{characterId:'R001M'}});
    const preview=await send(`땅콩떼기 ${rare.id}`);await confirm(preview);await confirm(preview);
    expect((await db.player.findFirstOrThrow()).peanut).toBe(1);
    await expect(send(`땅콩떼기 ${rare.id}`)).rejects.toThrow('이미');
  });
  it('출석/탐험 고정 보상과 확정 교환',async()=>{
    await send('출석');await send('출석');await confirm(await send('교환 C001F'));
    expect((await db.player.findFirstOrThrow()).snack).toBe(0);
    await send('탐험');await send('탐험');expect((await db.player.findFirstOrThrow()).snack).toBe(10);
    now=new Date(now.getTime()+30000);await send('탐험');expect((await db.player.findFirstOrThrow()).snack).toBe(20);
  });
  it('HTTP 인증·이미지·카카오 응답',async()=>{
    const secret='test-secret-'.repeat(4);
    game=new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),max=>max===100?0:5));
    const app=await buildApp(db,game,{secret,botId:'bot',baseUrl:async()=> 'https://example.com'});
    try{
      expect((await app.inject({method:'POST',url:'/kakao/skill',payload:{}})).statusCode).toBe(401);
      const response=await app.inject({method:'POST',url:'/kakao/skill',headers:{'x-skill-secret':secret,'x-forwarded-host':'evil.invalid'},payload:{bot:{id:'bot'},userRequest:{utterance:'뽑기',user:{id:'http-user',properties:{isFriend:true}}}}});
      expect(response.statusCode).toBe(200);
      expect(response.json().template.outputs).toHaveLength(2);
      expect(response.json().template.outputs[0].simpleImage.imageUrl).toBe('https://example.com/images/C003F.png');
      expect(response.json().template.outputs[1].simpleText.text).toContain('비닐봉지 갈매미맨');
      expect(response.json().template.outputs[1].simpleText.text).toContain('등장!');
      expect((await db.ownedCharacter.findFirstOrThrow()).characterId).toBe('C003F');
      const image=await app.inject('/images/C001M.png');expect(image.statusCode).toBe(200);expect(image.headers['content-type']).toBe('image/png');
      expect((await app.inject('/images/README.txt')).statusCode).toBe(404);
      expect((await app.inject('/health')).json().edition).toBe('RANDOM_75');
      expect((await app.inject('/health')).json().version).toBe('0.2.6');
      const wrongBot=await app.inject({method:'POST',url:'/kakao',headers:{'x-skill-secret':secret},payload:{bot:{id:'other'},userRequest:{utterance:'뽑기',user:{id:'http-user'}}}});
      expect(wrongBot.statusCode).toBe(403);
    }finally{await app.close();}
  });
  it('이전 DB의 코드명 개체도 이름 표시하며 보유 기록과 재화 보존',async()=>{
    const definition=await db.characterDefinition.findUniqueOrThrow({where:{id:'C003F'}});
    const p=await fixtures(['C003F']);
    await db.player.update({where:{id:p.id},data:{snack:30,peanut:2}});
    await db.collectionEntry.create({data:{playerId:p.id,characterId:'C003F',count:4}});
    const snapshot=async()=>({
      player:await db.player.findUniqueOrThrow({where:{id:p.id}}),
      owned:await db.ownedCharacter.findMany({where:{playerId:p.id}}),
      collection:await db.collectionEntry.findMany({where:{playerId:p.id}}),
      audit:await db.auditEvent.findMany({where:{playerId:p.id}})
    });
    const before=await snapshot();
    try{
      await db.characterDefinition.update({where:{id:'C003F'},data:{name:'갈매미맨 C003F',nameStatus:'ID_ONLY'}});
      expect((await send('내갈매미')).text).toContain('비닐봉지 갈매미맨');
      expect((await send('도감 2')).text).toContain('비닐봉지 갈매미맨');
      const detail=await send('도감 C003F');
      expect(detail.text).toContain('비닐봉지 갈매미맨');
      expect(detail.text).toContain('누적 4회 획득');
      expect(detail.imageName).toBe('비닐봉지 갈매미맨');
      expect((await send('조합식 U001M')).text).toContain('비닐봉지 갈매미맨\n   필요 1마리 · 사용 가능 1마리');
      expect(await snapshot()).toEqual(before);
      expect((await db.characterDefinition.findUniqueOrThrow({where:{id:'C003F'}})).name).toBe('갈매미맨 C003F');
    }finally{
      await db.characterDefinition.update({where:{id:'C003F'},data:{name:definition.name,nameStatus:definition.nameStatus}});
    }
  });
  it('이름만으로 조합 미리보기와 재료 수량 확인 후 확정',async()=>{
    await fixtures(['C001M','C003F']);
    const preview=await send('조합 브라자');
    expect(preview.text).toContain('새우깡봉지 갈매미맨\n   필요 1마리 · 사용 가능 1마리');
    expect(preview.text).toContain('비닐봉지 갈매미맨\n   필요 1마리 · 사용 가능 1마리');
    expect(preview.text).not.toMatch(/[CUSR]\d{3}[MF]|#[0-9]|[a-f0-9]{16}/);
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(0);
    const result=await confirm(preview);
    expect(result.text).toContain('브라자 갈매미맨');
    expect(result.text).not.toMatch(/[CUSR]\d{3}[MF]|#[0-9]/);
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(2);
  });
  it('이름이 여러 종류와 일치하면 후보만 보여주고 조합하지 않음',async()=>{
    await bankroll();
    const reply=await send('조합 새우깡');
    expect(reply.list!.items.length).toBeGreaterThan(1);
    expect(reply.list!.items.every(item=>item.message.startsWith('조합 '))).toBe(true);
    expect(await db.pendingAction.count()).toBe(0);
    expect(await db.ownedCharacter.count()).toBe(0);
  });
  it('조합가능은 같은 종류 수량·보호·표식 상태까지 반영',async()=>{
    await fixtures(['C001M','C003F']);
    const first=await send('조합가능 안흔함');
    expect(first.list!.items.some(item=>item.title==='브라자 갈매미맨')).toBe(true);
    expect(first.list!.items.some(item=>item.title==='새우깡 신도 갈매미맨')).toBe(false);
    await send('잠금 비닐봉지');
    expect((await send('조합가능 안흔함')).list).toBeUndefined();
    await confirm(await send('재료 새우깡봉지'));
    expect((await send('조합가능 안흔함')).list!.items.some(item=>item.title==='새우깡 신도 갈매미맨')).toBe(true);
    const one=await db.ownedCharacter.findFirstOrThrow({where:{characterId:'C001M'}});
    await db.ownedCharacter.update({where:{id:one.id},data:{peanutRemoved:true}});
    expect((await send('조합가능 안흔함')).list).toBeUndefined();
    const missing=await send('조합 새우깡 신도');
    expect(missing.text).toContain('필요 2마리 · 사용 가능 1마리');
    expect(missing.list).toBeUndefined();
  });
  it('같은 이름은 수량으로 묶고 이름 잠금은 한 마리에만 적용',async()=>{
    await fixtures(['C003F','C003F']);
    expect((await send('내갈매미')).list!.items).toHaveLength(1);
    const result=await send('잠금 비닐봉지 갈매미맨');
    expect(result.text).toContain('비닐봉지 갈매미맨 1마리');
    expect(await db.ownedCharacter.count({where:{locked:true}})).toBe(1);
    const detail=await send('내갈매미 비닐봉지');
    expect(detail.text).toContain('보유 2마리 · 조합에 사용 가능 1마리');
    await send('잠금해제 비닐봉지');
    expect(await db.ownedCharacter.count({where:{locked:true}})).toBe(0);
  });
  it('취소 버튼은 미완료 요청을 닫고, 텍스트 확정만으로 소비하지 않음',async()=>{
    const preview=await send('재료 도시락');
    const cancel=preview.list!.items.find(item=>item.button?.action==='cancel')!;
    const before=await db.player.findFirstOrThrow();
    await send('교환 확정');
    await expect(game.handle(actor,'내갈매미',button(preview).button)).rejects.toThrow('일치하지');
    expect(await db.ownedCharacter.count()).toBe(0);
    await game.handle(actor,cancel.message,cancel.button);
    expect(await db.player.findFirstOrThrow()).toEqual(before);
    await expect(confirm(preview)).rejects.toThrow('찾지 못했습니다');
    expect(await db.ownedCharacter.count()).toBe(0);
  });
  it('HTTP 목록 선택→이름 조합→숨긴 토큰 확정, 재전송 1회 처리',async()=>{
    const secret='readability-http-secret-'.repeat(3);
    const app=await buildApp(db,game,{secret,baseUrl:async()=>'https://example.com'});
    const request=async(utterance:string,clientExtra:object|null=null,id='flow-user')=>{
      const response=await app.inject({method:'POST',url:'/kakao/skill',headers:{'x-skill-secret':secret},payload:{bot:{id:'bot'},userRequest:{utterance,user:{id}},action:{clientExtra}}});
      expect(response.statusCode).toBe(200);return response.json();
    };
    const selected=(response:any)=>response.template.outputs.find((output:any)=>output.listCard).listCard.items[0];
    try{
      for(const name of ['새우깡봉지','비닐봉지']){
        const item=selected(await request(`재료 ${name}`));
        expect(item.messageText).toBe('교환 확정');
        await request(item.messageText,item.extra);
      }
      const grade=selected(await request('조합목록'));
      expect(grade.title).toBe('안흔함');
      const item=selected(await request(grade.messageText));
      expect(item.title).toBe('브라자 갈매미맨');
      expect(item.messageText).toBe('조합 브라자 갈매미맨');
      const preview=await request(item.messageText);
      const confirmItem=selected(preview);
      expect(confirmItem.title).toBe('조합하기');
      expect(confirmItem.messageText).toBe('조합 확정');
      const stolen=await request(confirmItem.messageText,confirmItem.extra,'other-user');
      expect(stolen.template.outputs[0].simpleText.text).toContain('본인의');
      const done=await request(confirmItem.messageText,confirmItem.extra);
      expect(await request(confirmItem.messageText,confirmItem.extra)).toEqual(done);
      expect(await db.ownedCharacter.count({where:{characterId:'U001M'}})).toBe(1);
      expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(2);
    }finally{await app.close();}
  });
  it('설정 변경은 재시작 없이 읽고 미정산 슬롯은 각 시점의 충전량으로 지급',async()=>{
    const dir=await mkdtemp(join(tmpdir(),'galmaegiman-refill-live-'));
    const path=join(dir,'settings.json');
    game=new GameService(db,content,()=>now,undefined,()=>readRefillSettings(path));
    try{
      expect((await send('내정보')).text).toContain('다음 +10장');
      await changeRefillAmount(20,path,new Date('2026-09-18T10:00:00+09:00'));
      now=new Date('2026-09-18T10:00:00+09:00');
      expect((await send('내정보')).text).toContain('다음 +20장');
      expect((await db.player.findFirstOrThrow()).credits).toBe(13);
      now=new Date('2026-09-18T18:00:00+09:00');
      await Promise.all([send('내정보'),send('내정보')]);
      expect((await db.player.findFirstOrThrow()).credits).toBe(53);
      expect(await db.auditEvent.count({where:{kind:'SCHEDULE_GRANT'}})).toBe(2);
      const before=await db.player.findFirstOrThrow();
      await writeFile(path,'invalid settings');
      await expect(send('뽑기')).rejects.toThrow('충전 설정');
      expect(await db.player.findFirstOrThrow()).toEqual(before);
      expect(await db.ownedCharacter.count()).toBe(0);
    }finally{await rm(dir,{recursive:true,force:true});}
  });
  it('10회 충전과 0회 중지 모두 잔액 안내가 일치하고 누적 상한을 초과하지 않음',async()=>{
    await bankroll(1_999_999_999);
    now=new Date('2026-09-18T09:00:00+09:00');await send('내정보');
    expect((await db.player.findFirstOrThrow()).credits).toBe(2_000_000_000);
    expect((await db.auditEvent.findFirstOrThrow({where:{kind:'SCHEDULE_GRANT'}})).detail).toMatchObject({delta:1,scheduledCredits:10});
    await db.player.updateMany({data:{credits:0}});
    expect((await send('뽑기')).text).toContain('+10장');
    game=new GameService(db,content,()=>now,undefined,async()=>({version:1,initialAmount:10,changes:[{at:new Date('2026-09-18T10:00:00+09:00').toISOString(),amount:0}]}));
    now=new Date('2026-09-18T12:00:00+09:00');
    expect((await send('내정보')).text).toContain('다음 +0장');
    expect((await db.player.findFirstOrThrow()).credits).toBe(0);
    expect((await db.player.findFirstOrThrow()).lastGrantAt).toEqual(now);
  });
  it('일반 채팅은 관리자 설정을 바꿀 수 없음',async()=>{
    const p=await bankroll(3);
    await send('충전설정 999');await send('refill 999');
    expect(await db.player.findFirstOrThrow()).toEqual(p);
    expect((await send('내정보')).text).toContain('다음 +10장');
  });
  it('등급별 9/20/40종 조합목록과 필터를 유지하는 페이지 이동',async()=>{
    const categories=await send('조합목록');
    expect(categories.list!.items.map(i=>i.title)).toEqual(['안흔함','특별','희귀']);
    expect((await send('조합가능')).list!.items.every(i=>i.description.includes('지금 조합 가능 0종'))).toBe(true);
    expect((await send('조합목록 흔함')).text).toContain('기본 재료');
    for(const [label,rarity,total] of [['안흔함','UNCOMMON',9],['특별','SPECIAL',20],['희귀','RARE',40]] as const){
      const found=new Set<string>();
      for(let page=1;page<=Math.ceil(total/5);page++){
        const result=await send(`조합목록 ${label} ${page}`);
        expect(result.list!.title).toContain(label);
        expect(result.choices!.length).toBeLessThanOrEqual(10);
        expect(result.list!.items.every(i=>!i.imageId&&i.description.startsWith(label+' ·'))).toBe(true);
        for(const item of result.list!.items)found.add(item.title);
        const next=result.choices!.find(c=>c.label==='다음 페이지');
        if(page<Math.ceil(total/5))expect(next?.message).toBe(`조합목록 ${label} ${page+1}`);
        else expect(next).toBeUndefined();
      }
      expect([...found].sort()).toEqual(content.characters.filter(c=>c.rarity===rarity).map(c=>c.name).sort());
    }
    await expect(send('조합목록 희귀 9')).rejects.toThrow('페이지');
    await expect(send('조합목록 전설')).rejects.toThrow('등급');
    await fixtures(['C001M','C003F']);
    expect((await send('조합가능 안흔함')).list!.items[0].title).toBe('브라자 갈매미맨');
    expect((await send('조합가능 희귀')).list).toBeUndefined();
  });
  it('뽑기는 전 등급 첫 수집에 이미지, 중복도 목록 클릭으로 원본 이미지 보기',async()=>{
    await bankroll();
    for(const [roll,id] of [[0,'C001M'],[40,'U001M'],[70,'S001M'],[90,'R001M']] as const){
      game=new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),max=>max===100?roll:0));
      const draw=await send('뽑기'),c=content.map.get(id)!;
      expect(draw.text).toContain(c.name);
      expect(draw.imageId).toBe(id);
      const response=kakaoResponse(draw,'https://example.com');
      expect(JSON.stringify(response).includes('imageUrl')).toBe(true);
      const again=await send('뽑기');
      expect(again.imageId).toBeUndefined();
      expect(JSON.stringify(kakaoResponse(again,'https://example.com'))).not.toContain('imageUrl');
      const choice=draw.choices!.find(c=>c.label==='캐릭터 보기')!;
      const detail=await send(choice.message);
      expect(detail.imageId).toBe(id);
      expect(detail.imageName).toBe(c.name);
    }
    const inventory=await send('내갈매미');
    expect(inventory.list!.items).toHaveLength(4);
    expect(JSON.stringify(kakaoResponse(inventory,'https://example.com'))).not.toContain('imageUrl');
    for(const item of inventory.list!.items)expect((await send(item.message)).imageId).toBeDefined();
  });
  it('전체 도감과 조합목록에서 표시·발화에 코드가 없고 페이지 누락 없음',async()=>{
    const seen=new Map<string,Set<string>>([['도감',new Set()],['조합목록',new Set()]]);
    for(const [command,total] of [['도감',75],['조합목록',69]] as const){
      for(let page=1;page<=Math.ceil(total/5);page++){
        const reply=await send(`${command} ${page}`);
        expect(reply.list!.items.length).toBeLessThanOrEqual(5);
        const json=kakaoResponse(reply,'https://example.com');
        const output=json.template.outputs[0] as {listCard:{header:{title:string};items:{title:string;description:string;messageText:string}[]}};
        const visible=[output.listCard.header.title,...output.listCard.items.flatMap(item=>[item.title,item.description,item.messageText]),...json.template.quickReplies.flatMap(item=>[item.label,item.messageText])].join('\n');
        expect(visible).not.toMatch(/[CUSR]\d{3}[MF]|#[0-9]|[a-f0-9]{16}/);
        for(const item of reply.list!.items)seen.get(command)!.add(item.title);
      }
      expect(seen.get(command)!.size).toBe(total);
    }
  });
});
