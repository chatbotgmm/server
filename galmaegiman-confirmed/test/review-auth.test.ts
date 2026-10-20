import {describe,it,expect,vi} from 'vitest';
import {scryptSync,createHmac} from 'node:crypto';
import type {PrismaClient} from '@prisma/client';
import {ReviewAuth,reviewConfig,reviewRouteAllowed,REVIEW_IDENTITY,type ReviewConfig} from '../src/review-auth.js';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
// 테스트 전용 가짜 인증정보. 실제 계정/자격증명 발급이나 DB 생성 없음.
const password='dummy-test-password-only',salt='11'.repeat(16);
const config:ReviewConfig={enabled:true,username:'dummy-review',playerId:'dummy-player',version:'test-version',passwordHash:`scrypt$131072$8$1$${salt}$${scryptSync(password,Buffer.from(salt,'hex'),32,{N:131072,r:8,p:1,maxmem:192*1024*1024}).toString('hex')}`};
function fixture(){
  const player={id:'dummy-player',identity:REVIEW_IDENTITY,isAdmin:false,adminMode:false,kakao:null as unknown,chatApp:null as unknown,adminSnapshot:null as unknown};
  const db={player:{findUnique:vi.fn(async()=>player)}} as unknown as PrismaClient;
  const c={...config};let now=100000;
  const auth=new ReviewAuth(db,c,()=>now);
  return {auth,db,player,c,advance:(ms:number)=>now+=ms};
}
describe('심사 전용 인증',()=>{
  it('기본 비활성, 일부/잘못된 설정은 거부',async()=>{
    expect(reviewConfig({})).toBeUndefined();expect(reviewConfig({REVIEW_LOGIN_ENABLED:'true'})).toBeUndefined();
    expect(await new ReviewAuth(fixture().db).login(config.username,password)).toBeNull();
  });
  it('반복 로그인, 만료, 단일 로그아웃, 전체 폐기',async()=>{
    const f=fixture();const a=await f.auth.login(config.username,password),b=await f.auth.login(config.username,password);
    expect(a).not.toBe(b);expect(await f.auth.authenticate(a!)).toBe(config.playerId);
    f.auth.revoke(a!);expect(await f.auth.authenticate(a!)).toBeNull();expect(await f.auth.authenticate(b!)).toBe(config.playerId);
    f.advance(12*3600000);expect(await f.auth.authenticate(b!)).toBeNull();
    const c=await f.auth.login(config.username,password);f.auth.close();expect(await f.auth.authenticate(c!)).toBeNull();
  });
  it.each(['identity','isAdmin','adminMode','kakao','chatApp','adminSnapshot'] as const)('%s 오염 계정은 거부',async field=>{
    const f=fixture();Object.assign(f.player,{[field]:field==='identity'?'normal-user':true});
    expect(await f.auth.login(config.username,password)).toBeNull();
  });
  it('인증 이후 권한 변경/설정 버전 변경/비활성도 거부',async()=>{
    const f=fixture();const a=await f.auth.login(config.username,password);f.c.version='changed-version';expect(await f.auth.authenticate(a!)).toBeNull();
    const b=await f.auth.login(config.username,password);f.c.enabled=false;expect(await f.auth.authenticate(b!)).toBeNull();
    f.c.enabled=true;const c=await f.auth.login(config.username,password);f.player.isAdmin=true;expect(await f.auth.authenticate(c!)).toBeNull();
  });
  it('틀린 사용자/비밀번호와 과다 요청 거부',async()=>{
    const f=fixture();expect(await f.auth.login('wrong-name',password)).toBeNull();expect(await f.auth.login(config.username,'wrong-dummy-password')).toBeNull();
    for(let i=0;i<8;i++)await f.auth.login('', '');expect(await f.auth.login(config.username,password)).toBeNull();
    f.advance(60001);expect(await f.auth.login(config.username,password)).toMatch(/^rv_/);
  });
  it('명시적 자기 계정 경로와 단독 전투만 허용',()=>{
    expect(reviewRouteAllowed('POST','mix',{})).toBe(true);expect(reviewRouteAllowed('POST','native/battle/start',{mode:'story'})).toBe(true);
    for(const p of ['account/delete','kakao/link-start','coupon','admin','pvp/queue/attempt','future-route'])expect(reviewRouteAllowed('POST',p,{})).toBe(false);
    expect(reviewRouteAllowed('POST','native/battle/start',{mode:'normal'})).toBe(false);
  });
});
describe('실제 HTTP 인증 경계 (mock DB)',()=>{
  it('응답/세션/허용 경로/로그아웃/구 코드 제거',async()=>{
    const f=fixture(),game=new GameService(f.db,loadContent());
    const who=vi.spyOn(game,'webWho').mockResolvedValue({nickname:'dummy',credits:0});
    const legacy=vi.spyOn(game,'webLoginCode').mockResolvedValue(null);
    const app=await buildApp(f.db,game,{secret:'test-only-secret'.repeat(4),baseUrl:async()=>'',reviewLogin:f.c});
    const headers={'x-gmm':'1','x-gmm-app':'1'};
    try{
      const login=await app.inject({method:'POST',url:'/my/api/review/login',headers,payload:{username:config.username,password,playerId:'victim'}});
      expect(login.statusCode).toBe(200);const token=login.json().token;
      const auth={...headers,'x-gmm-s':token};
      expect((await app.inject({url:'/my/api/who',headers:auth})).statusCode).toBe(200);expect(who).toHaveBeenCalledWith(config.playerId);
      expect((await app.inject({method:'POST',url:'/my/api/kakao/unlink',headers:auth,payload:{}})).statusCode).toBe(403);
      expect((await app.inject({method:'POST',url:'/my/api/logout',headers:auth,payload:{}})).statusCode).toBe(200);
      expect((await app.inject({url:'/my/api/who',headers:auth})).statusCode).toBe(401);
      expect((await app.inject({method:'POST',url:'/my/api/login',headers,payload:{code:'424242'}})).statusCode).toBe(400);expect(legacy).toHaveBeenCalledWith('424242');
      // 이전 심사 세션은 서명이 유효해도 재사용 불가.
      const value=`dummy-player.${Math.floor(Date.now()/1000)+3600}`;
      const key=createHmac('sha256','test-only-secret'.repeat(4)).update('web-session').digest();
      const old=value+'.'+createHmac('sha256',key).update(value).digest('base64url');
      expect((await app.inject({url:'/my/api/who',headers:{...headers,'x-gmm-s':old}})).statusCode).toBe(401);
      const fail=()=>app.inject({method:'POST',url:'/my/api/review/login',headers,payload:{username:'bad',password:'wrong-dummy-password'}});
      const a=await fail(),b=await fail();expect(a.json()).toEqual(b.json());await fail();await fail();expect((await fail()).statusCode).toBe(429);
    }finally{await app.close();}
  });
});

describe('일반 인증 유지와 심사 실패 폐쇄',()=>{
  it('일반 코드 계정은 기존 서명 세션으로 동작',async()=>{
    const f=fixture();f.player.identity='normal-player';f.player.id='normal-player';
    const game=new GameService(f.db,loadContent());vi.spyOn(game,'webLoginCode').mockResolvedValue('normal-player');
    const who=vi.spyOn(game,'webWho').mockResolvedValue({nickname:'normal',credits:1});
    const app=await buildApp(f.db,game,{secret:'test-only-secret'.repeat(4),baseUrl:async()=>''});
    try{
      const r=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code:'123456'}});
      expect(r.statusCode).toBe(200);
      expect((await app.inject({url:'/my/api/who',headers:{'x-gmm':'1','x-gmm-s':r.json().token}})).statusCode).toBe(200);
      expect(who).toHaveBeenCalledWith('normal-player');
      const off=await app.inject({method:'POST',url:'/my/api/review/login',headers:{'x-gmm':'1'},payload:{username:config.username,password}});
      expect(off.statusCode).toBe(400);expect(off.headers['set-cookie']).toBeUndefined();
    }finally{await app.close();}
  });
  it('DB 오류도 인증 실패로 닫힘',async()=>{
    const f=fixture();vi.mocked(f.db.player.findUnique).mockRejectedValue(new Error('test DB unavailable'));
    expect(await f.auth.login(config.username,password)).toBeNull();
  });
  it('동시 비밀번호 검증 작업은 하나만 실행',async()=>{
    const f=fixture();const first=f.auth.login(config.username,password);
    expect(await f.auth.login(config.username,password)).toBeNull();expect(await first).toMatch(/^rv_/);
  });
});
