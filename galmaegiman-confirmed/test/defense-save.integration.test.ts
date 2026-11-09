import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {DEFENSE_MODES,DEFENSE_DIFFS} from '../src/defense-save.js';

// 랜덤 디펜스 계정 저장(/my/api/defense): 로그인 확인, 최고 기록 합치기, 이어하기 판(나중 것만), 계정 삭제 때 함께 지움
const enabled=Boolean(process.env.TEST_DB_URL);
const suite=enabled?describe:describe.skip;
suite('랜덤 디펜스 계정 저장',()=>{
  let db:PrismaClient,game:GameService;
  const actor='defense-actor';
  beforeAll(async()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  const clean=async()=>{
    const ids=(await db.player.findMany({where:{identity:{startsWith:'defense-'}},select:{id:true}})).map(p=>p.id);
    await db.$transaction([db.defenseSave.deleteMany({where:{playerId:{in:ids}}}),db.webLogin.deleteMany({where:{playerId:{in:ids}}}),db.auditEvent.deleteMany({where:{playerId:{in:ids}}}),db.player.deleteMany({where:{id:{in:ids}}})]);
  };
  beforeEach(async()=>{
    await clean();
    await db.player.create({data:{identity:actor,tutorial:5,nickname:'디펜스왕'}});
    game=new GameService(db,loadContent());
  });
  afterAll(async()=>{await clean();await db.$disconnect();});

  const login=async(app:Awaited<ReturnType<typeof buildApp>>,identity=actor)=>{
    const r=await game.handle(identity,'웹 로그인');
    const code=/(\d{6})/.exec(r.text??'')![1];
    return String((await app.inject({method:'POST',url:'/my/api/login',payload:{code},headers:{'x-gmm':'1'}})).headers['set-cookie']).split(';')[0];
  };

  it('모드 목록이 게임 엔진과 같다',async()=>{
    const p='../public/defense/engine.js';
    const E=await import(p) as {MODES:Record<string,unknown>;DIFF_ORDER:string[]};
    expect([...DEFENSE_MODES].sort()).toEqual(Object.keys(E.MODES).sort());
    expect([...DEFENSE_DIFFS]).toEqual(E.DIFF_ORDER);
  });

  it('로그인해야 쓰고, 최고 기록은 큰 값으로 합치고, 이어하기는 나중 것만 받는다',async()=>{
    const app=await buildApp(db,game,{secret:'defense-save-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const get=(cookie='',h:Record<string,string>={'x-gmm':'1'})=>app.inject({method:'GET',url:'/my/api/defense',headers:{...h,...(cookie?{cookie}:{})}});
      const post=(body:unknown,cookie:string)=>app.inject({method:'POST',url:'/my/api/defense',payload:body as object,headers:{'x-gmm':'1',cookie}});
      expect((await get()).statusCode).toBe(401);                              // 로그인 안 하면 브라우저에만
      const cookie=await login(app);
      expect((await get(cookie,{})).statusCode).toBe(403);                     // 다른 사이트 요청 막기(x-gmm 없음)
      expect((await get(cookie)).json()).toEqual({nickname:'디펜스왕',best:{},save:null});
      expect((await post({best:{normal:12,hard:3}},cookie)).statusCode).toBe(200);
      expect((await post({best:{normal:9,noupg:7,'normal.intro':40}},cookie)).json()).toMatchObject({best:{normal:12,hard:3,noupg:7,'normal.intro':40}});   // 작은 값은 무시, 난이도별 키도 받음
      const save={data:JSON.stringify({v:1,wave:14}),wave:14,mode:'normal',at:2_000_000};
      expect((await post({save},cookie)).json()).toMatchObject({saved:true,save});
      const stale={...save,data:JSON.stringify({v:1,wave:5}),wave:5,at:1_000_000};
      expect((await post({save:stale},cookie)).json()).toMatchObject({saved:false,save});    // 오래된 탭의 저장은 덮어쓰지 않음
      expect((await get(cookie)).json()).toMatchObject({best:{normal:12},save});
      for(const bad of [{best:{normal:99}},{best:{weird:3}},{best:{'normal.master':3}},{save:{...save,data:'not json'}},{save:{...save,mode:'x'}},{save:{...save,extra:1}},{other:1},{save:{...save,data:'x'.repeat(120_001)}}])
        expect((await post(bad,cookie)).statusCode).toBe(400);
      const big={...save,data:JSON.stringify({v:1,pad:'x'.repeat(100_000)}),at:3_000_000};   // 기본 요청 크기(32KB)보다 큰 판도 받음
      expect((await post({save:big},cookie)).json()).toMatchObject({saved:true});
      expect((await post({save:null,best:{normal:61}},cookie)).json()).toMatchObject({save:null,best:{normal:61}});   // 끝난 판은 지우고 클리어 기록
      // 다른 계정에는 보이지 않음
      await db.player.create({data:{identity:'defense-other',tutorial:5}});
      const other=await login(app,'defense-other');
      expect((await get(other)).json()).toMatchObject({best:{},save:null});
    }finally{await app.close();}
  });

  it('계정을 지우면 디펜스 저장도 함께 지운다',async()=>{
    const app=await buildApp(db,game,{secret:'defense-save-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
    try{
      const cookie=await login(app);
      await app.inject({method:'POST',url:'/my/api/defense',payload:{best:{normal:20}},headers:{'x-gmm':'1',cookie}});
      const p=await db.player.findUniqueOrThrow({where:{identity:actor}});
      expect(await db.defenseSave.count({where:{playerId:p.id}})).toBe(1);
      expect((await app.inject({method:'POST',url:'/my/api/account/delete',payload:{confirm:'탈퇴'},headers:{'x-gmm':'1',cookie}})).statusCode).toBe(200);
      expect(await db.defenseSave.count({where:{playerId:p.id}})).toBe(0);
    }finally{await app.close();}
  });
});
