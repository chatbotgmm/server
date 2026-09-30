import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {GachaEngine,loadGachaWeights} from '../src/gacha.js';
import {kakaoResponse} from '../src/kakao.js';
import type {GameReply} from '../src/types.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('소개와 획득 보상의 실제 트랜잭션',()=>{
  const content=loadContent(),now=new Date('2026-09-18T08:00:00+09:00'),actor='reward-player';
  let db:PrismaClient,game:GameService;
  const makeGame=(roll=90)=>new GameService(db,content,()=>now,new GachaEngine(content.characters,loadGachaWeights(),max=>max===100?roll:0));
  const send=(message:string)=>game.handle(actor,message);
  const confirm=(r:GameReply)=>{
    const item=r.list!.items.find(i=>i.button?.action==='confirm')!;
    return game.handle(actor,item.message,item.button);
  };
  const player=()=>db.player.findUniqueOrThrow({where:{identity:actor}});
  async function materials(resultId:string){
    await send('내정보');const p=await player();
    for(const characterId of content.recipes.find(r=>r.resultId===resultId)!.materials)
      await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});
  }
  beforeAll(()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리 테스트 DB만 허용');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(async()=>{
    await db.$transaction([db.pendingAction.deleteMany(),db.auditEvent.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.player.deleteMany()]);
    game=makeGame();
  });
  afterAll(async()=>db.$disconnect());

  it.each([[0,'C001M',0,0],[40,'U001M',10,3],[70,'S001M',30,10],[90,'R001M',90,30]] as const)(
    '등급 추첨 %i: 첫 발견 %s와 중복의 서로 다른 보상',async(roll,id,first,duplicate)=>{
      game=makeGame(roll);
      const a=await send('뽑기'),b=await send('뽑기');
      expect(a.text).toContain('첫 발견');expect(b.text).toContain('다시 합류');
      expect(a.imageId).toBe(id);expect(b.imageId).toBeUndefined();
      expect(JSON.stringify(kakaoResponse(a,'https://example.com'))).toContain('imageUrl');
      expect(JSON.stringify(kakaoResponse(b,'https://example.com'))).not.toContain('imageUrl');
      expect(b.text).toContain('누적 2회');
      expect((await player()).snack).toBe(first+duplicate);
      expect((await player()).credits).toBe(1);
      expect(await db.ownedCharacter.count({where:{characterId:id,status:'AVAILABLE'}})).toBe(2);
      const ledger=await db.auditEvent.findMany({where:{kind:'ACQUISITION_REWARD'}});
      expect(ledger).toHaveLength(first?2:0);
      if(first){
        expect(a.text).toContain(`새우깡 +${first}`);expect(b.text).toContain(`새우깡 +${duplicate}`);
        expect(ledger.map(e=>(e.detail as {delta:number}).delta).sort((x,y)=>x-y)).toEqual([duplicate,first].sort((x,y)=>x-y));
      }
    });
  it('동시 희귀 뽑기 두 번은 첫 발견 1회와 중복 1회로 정산',async()=>{
    const results=await Promise.all([send('뽑기'),send('뽑기')]);
    expect(results.filter(r=>r.text.includes('📖 첫 발견'))).toHaveLength(1);
    expect(results.filter(r=>r.imageId)).toHaveLength(1);
    expect((await player()).snack).toBe(120);
    expect(await db.ownedCharacter.count()).toBe(2);
  });
  it.each(['U001M','S001M','R001M'])('조합 %s: 첫 수집만 이미지, 재조합은 텍스트',async id=>{
    await materials(id);
    const first=await confirm(await send(`조합 ${content.map.get(id)!.name}`));
    expect(first.imageId).toBe(id);
    await materials(id);
    const again=await confirm(await send(`조합 ${content.map.get(id)!.name}`));
    expect(again.imageId).toBeUndefined();
    expect(JSON.stringify(kakaoResponse(again,'https://example.com'))).not.toContain('imageUrl');
    expect(again.text).toContain('다시 합류');
    expect((await send(`내갈매미 ${content.map.get(id)!.name}`)).imageId).toBe(id);
  });
  it('선택 교환과 뽑기는 같은 수집 이력으로 이미지를 판단',async()=>{
    game=makeGame(0);
    const first=await confirm(await send('재료 새우깡봉지'));
    expect(first.imageId).toBe('C001M');
    expect((await send('뽑기')).imageId).toBeUndefined();
    const again=await confirm(await send('재료 새우깡봉지'));
    expect(again.imageId).toBeUndefined();
    expect((await player()).credits).toBe(0);
    expect(await db.ownedCharacter.count()).toBe(3);
  });
  it('수집 이력은 사용자별로 구분하여 다른 사용자의 첫 획득에 이미지 표시',async()=>{
    expect((await send('뽑기')).imageId).toBe('R001M');
    expect((await send('뽑기')).imageId).toBeUndefined();
    expect((await game.handle('another-image-player','뽑기')).imageId).toBe('R001M');
  });
  it('희귀 조합 확인을 8번 재전송해도 재료 소비와 보상은 한 번',async()=>{
    await materials('R001M');const preview=await send('조합 우주배달총책임자');
    const results=await Promise.all(Array.from({length:8},()=>confirm(preview)));
    expect(results.every(r=>JSON.stringify(r)===JSON.stringify(results[0]))).toBe(true);
    expect(results[0].text).toContain(content.flavors.get('R001M')!.fusion);
    expect((await player()).snack).toBe(90);
    expect(await db.auditEvent.count({where:{kind:'ACQUISITION_REWARD'}})).toBe(1);
    expect(await db.ownedCharacter.count({where:{characterId:'R001M',status:'AVAILABLE'}})).toBe(1);
    expect(await db.ownedCharacter.count({where:{status:'CONSUMED'}})).toBe(content.recipes.find(r=>r.resultId==='R001M')!.materials.length);
  });
  it('희귀 첫 보상은 실제로 흔함 재료 3마리와 교환되며 교환 보너스 없음',async()=>{
    await send('뽑기');
    for(let n=0;n<3;n++)await confirm(await send('교환 도시락'));
    expect((await player()).snack).toBe(0);
    expect(await db.ownedCharacter.count({where:{status:'AVAILABLE'}})).toBe(4);
    expect(await db.auditEvent.count({where:{kind:'ACQUISITION_REWARD'}})).toBe(1);
    await expect(send('교환 도시락')).rejects.toThrow('재화');
  });
  it('조합에 소비한 종류를 재획득해도 첫 발견 보상은 다시 생기지 않음',async()=>{
    game=makeGame(40);await send('뽑기');
    const c=await db.ownedCharacter.findFirstOrThrow({where:{characterId:'U001M'}});
    const recipe=content.recipes.find(r=>r.materials.includes('U001M'))!;
    const remaining=[...recipe.materials];remaining.splice(remaining.indexOf('U001M'),1);
    const p=await player();
    for(const characterId of remaining)await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});
    await confirm(await send(`조합 ${content.map.get(recipe.resultId)!.name}`));
    expect((await db.ownedCharacter.findUniqueOrThrow({where:{id:c.id}})).status).toBe('CONSUMED');
    const before=(await player()).snack;const reply=await send('뽑기');
    expect(reply.text).toContain('다시 합류');expect((await player()).snack).toBe(before+3);
    expect(reply.imageId).toBeUndefined();
  });
  it('업데이트 이전 도감은 조회 시 소급 보상 없이 다음 획득부터 중복 보상',async()=>{
    await send('내정보');const p=await player();
    await db.collectionEntry.create({data:{playerId:p.id,characterId:'R001M',count:7}});
    await send('도감 우주배달총책임자');await send('보상');
    expect((await player()).snack).toBe(0);
    const reply=await send('뽑기');
    expect(reply.text).toContain('누적 8회');expect((await player()).snack).toBe(30);
    expect(reply.imageId).toBeUndefined();
    expect((await send('도감 우주배달총책임자')).imageId).toBe('R001M');
  });
  it('이전 판의 이미 완료된 조합 버튼에는 새 보상을 소급 지급하지 않음',async()=>{
    await send('내정보');const p=await player();
    const saved={text:'우주배달총책임자 갈매미맨 조합 완료!',imageId:'R001M'};
    await db.pendingAction.create({data:{token:'1234567890abcdef',playerId:p.id,revision:p.revision,kind:'CRAFT',payload:{recipeId:'R001M'},result:saved,expiresAt:new Date(now.getTime()-1)}});
    expect((await send('확정 1234567890abcdef')).text).toBe(saved.text);
    expect((await player()).snack).toBe(0);expect(await db.ownedCharacter.count()).toBe(0);
  });
  it.each(['GACHA','COMBINATION'])('%s 보상 로그 실패는 캐릭터·재료·재화·도감·확인 결과까지 원복',async via=>{
    if(via==='COMBINATION')await materials('R001M');else await send('내정보');
    const preview=via==='COMBINATION'?await send('조합 우주배달총책임자'):undefined;
    const snapshot=async()=>({player:await player(),owned:await db.ownedCharacter.findMany(),collection:await db.collectionEntry.findMany(),actions:await db.pendingAction.findMany(),audit:await db.auditEvent.findMany()});
    const before=await snapshot();
    await db.$executeRawUnsafe("CREATE TRIGGER fail_reward BEFORE INSERT ON AuditEvent WHEN NEW.kind = 'ACQUISITION_REWARD' BEGIN SELECT RAISE(ABORT, 'TEST_REWARD_FAILURE'); END;");
    try{await expect(preview?confirm(preview):send('뽑기')).rejects.toThrow();}
    finally{await db.$executeRawUnsafe('DROP TRIGGER fail_reward');}
    expect(await snapshot()).toEqual(before);
    await (preview?confirm(preview):send('뽑기'));
    expect((await player()).snack).toBe(90);
  });
  it('새 보상은 새우깡 상한 안에서 실제 지급량만 안내하고 기록',async()=>{
    await send('내정보');const p=await player();
    await db.player.update({where:{id:p.id},data:{snack:1_999_999_999}});
    const result=await send('뽑기');
    expect(result.text).toContain('새우깡 +1\n');
    expect((await player()).snack).toBe(2_000_000_000);
    expect((await db.auditEvent.findFirstOrThrow({where:{kind:'ACQUISITION_REWARD'}})).detail).toMatchObject({requested:90,delta:1,balanceAfter:2_000_000_000});
  });
  it('획득 버튼→소개와 보상 안내는 이름으로 연결되며 카카오 본문에 보상 보존',async()=>{
    game=makeGame(70);const draw=await send('뽑기');
    const info=await send(draw.choices!.find(c=>c.label==='캐릭터 보기')!.message);
    expect(info.text).toContain(content.flavors.get('S001M')!.lore);
    expect(info.imageId).toBe('S001M');
    expect((await send(draw.choices!.find(c=>c.label==='획득 보상')!.message)).text).toContain('희귀 · 첫 도감 90개 / 중복 30개');
    const response=kakaoResponse(draw,'https://example.com');
    expect(response.template.outputs).toHaveLength(2);
    expect(JSON.stringify(response)).toContain('새우깡 +30');
    expect(response.template.quickReplies.length).toBeLessThanOrEqual(10);
    expect((await send('보상')).text).toContain('희귀 · 첫 도감 90개 / 중복 30개');
    expect(draw.text).not.toMatch(/[CUSR]\d{3}[MF]|개체 #/);
  });
});
