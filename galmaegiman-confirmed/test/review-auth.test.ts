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
  it('틀린 사용자/비밀번호 거부, 무효 입력은 입장 예산을 소진하지 않음',async()=>{
    const f=fixture();expect(await f.auth.login('wrong-name',password)).toBeNull();expect(await f.auth.login(config.username,'wrong-dummy-password')).toBeNull();
    for(let i=0;i<12;i++)expect(await f.auth.login('', '')).toBeNull();
expect(await f.auth.login(config.username,password)).toMatch(/^rv_/);
  });
  it('명시적 자기 계정 경로와 일반 이용자와 같은 전투·실시간 PvP를 허용',()=>{
    expect(reviewRouteAllowed('POST','mix',{})).toBe(true);expect(reviewRouteAllowed('POST','native/battle/start',{mode:'story'})).toBe(true);
    // 실시간 PvP는 일반 이용자와 같은 규칙으로 연다(심사 계정에만 다른 동작 금지).
    for(const p of ['pvp/queue/attempt','pvp/queue/join','pvp/queue/cancel','pvp/room/command','pvp/room/surrender'])expect(reviewRouteAllowed('POST',p,{})).toBe(true);
    for(const p of ['pvp/queue/current','pvp/room/current','pvp/rank'])expect(reviewRouteAllowed('GET',p,{})).toBe(true);
    expect(reviewRouteAllowed('POST','native/battle/start',{mode:'normal'})).toBe(true);
    // 계정 연결·삭제·쿠폰·관리 기능·검토하지 않은 새 경로는 계속 막는다.
    for(const p of ['account/delete','kakao/link-start','kakao/unlink','coupon','admin','app/exchange','future-route'])expect(reviewRouteAllowed('POST',p,{})).toBe(false);
    expect(reviewRouteAllowed('POST','native/battle/start',{mode:'rank'})).toBe(false);
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

describe('심사용 로그인 페이지 → 일회용 코드 → 앱 세션',()=>{
  it('페이지 로그인 성공 시 앱 열기 주소만 주고, 코드는 한 번만 세션으로 바뀐다',async()=>{
    const f=fixture(),game=new GameService(f.db,loadContent());
    vi.spyOn(game,'webWho').mockResolvedValue({nickname:'dummy',credits:0});
    const app=await buildApp(f.db,game,{secret:'test-only-secret'.repeat(4),baseUrl:async()=>'',reviewLogin:f.c});
    const form=(u:string,p:string)=>app.inject({method:'POST',url:'/review',headers:{'content-type':'application/x-www-form-urlencoded'},payload:new URLSearchParams({username:u,password:p}).toString()});
    try{
      const page=await app.inject({url:'/review'});
      expect(page.statusCode).toBe(200);expect(page.headers['cache-control']).toBe('no-store');expect(page.headers['x-robots-tag']).toBe('noindex');
      expect(page.body).toContain('type="password"');
      const bad=await form(config.username,'wrong-dummy-password');
      expect(bad.statusCode).toBe(400);expect(bad.body).not.toContain('wrong-dummy-password');expect(bad.body).not.toContain('intent://');
      const ok=await form(config.username,password);
      expect(ok.statusCode).toBe(200);expect(ok.body).not.toContain(password);expect(ok.body).not.toContain('rv_');
      const code=/review=([A-Za-z0-9_-]+)#Intent;scheme=gmmrevised;package=org\.gmm;end/.exec(ok.body)?.[1];
      expect(code).toBeTruthy();expect(ok.body).toContain(`gmmrevised://auth?review=${code}`);
      const headers={'x-gmm':'1','x-gmm-app':'1'};
      expect((await app.inject({method:'POST',url:'/my/api/review/exchange',payload:{code}})).statusCode).toBe(403);   // x-gmm 없으면 거부(코드는 그대로)
      const ex=await app.inject({method:'POST',url:'/my/api/review/exchange',headers,payload:{code}});
      expect(ex.statusCode).toBe(200);const token=ex.json().token;expect(token).toMatch(/^rv_/);
      expect((await app.inject({url:'/my/api/who',headers:{...headers,'x-gmm-s':token}})).statusCode).toBe(200);
      expect((await app.inject({method:'POST',url:'/my/api/review/exchange',headers,payload:{code}})).statusCode).toBe(400);   // 한 번만
      expect((await app.inject({method:'POST',url:'/my/api/account/delete',headers:{...headers,'x-gmm-s':token},payload:{confirm:'탈퇴'}})).statusCode).toBe(403);
    }finally{await app.close();}
  });
  it('설정이 꺼져 있으면 페이지 로그인도 실패하고 코드를 만들지 않는다',async()=>{
    const f=fixture(),app=await buildApp(f.db,new GameService(f.db,loadContent()),{secret:'test-only-secret'.repeat(4),baseUrl:async()=>''});
    try{
      const r=await app.inject({method:'POST',url:'/review',headers:{'content-type':'application/x-www-form-urlencoded'},payload:new URLSearchParams({username:config.username,password}).toString()});
      expect(r.statusCode).toBe(400);expect(r.body).not.toContain('intent://');
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

describe('브라우저 심사 쿠키 복구',()=>{
  it.each(['valid','expired','revoked'] as const)('%s 쿠키로 재로그인·로그아웃 가능, 일반 API 우회 불가',async state=>{
    let clock=Date.now();vi.spyOn(Date,'now').mockImplementation(()=>clock);
    const f=fixture(),game=new GameService(f.db,loadContent());
    vi.spyOn(game,'webWho').mockResolvedValue({nickname:'dummy',credits:0});
    const app=await buildApp(f.db,game,{secret:'test-only-secret'.repeat(4),baseUrl:async()=>'',reviewLogin:f.c});
    const base={'x-gmm':'1'},payload={username:config.username,password};
    const login=(cookie?:string)=>app.inject({method:'POST',url:'/my/api/review/login',headers:{...base,...(cookie?{cookie}:{})},payload});
    const cookieOf=(r:{headers:Record<string,unknown>})=>String(r.headers['set-cookie']).split(';')[0];
    try{
      const initial=await login();expect(initial.statusCode).toBe(200);const old=cookieOf(initial);
      if(state==='revoked')await app.inject({method:'POST',url:'/my/api/logout',headers:{...base,cookie:old},payload:{}});
      if(state==='expired')clock+=13*3600000;
      const denied=await app.inject({method:'POST',url:'/my/api/account/delete',headers:{...base,cookie:old},payload:{confirm:'탈퇴'}});
      expect(denied.statusCode).toBe(state==='valid'?403:401);
      if(state!=='valid')expect(denied.headers['set-cookie']).toContain('Max-Age=0');
      // 만료/철회 여부와 무관하게 logout은 쿠키를 제거한다.
      const out=await app.inject({method:'POST',url:'/my/api/logout',headers:{...base,cookie:old},payload:{}});
      expect(out.statusCode).toBe(200);expect(out.headers['set-cookie']).toContain('Max-Age=0');
      const again=await login(old);expect(again.statusCode).toBe(200);const current=cookieOf(again);
      expect(current).not.toBe(old);
      expect((await app.inject({url:'/my/api/who',headers:{...base,cookie:current}})).statusCode).toBe(200);
      // 유효 쿠키도 재인증 가능하며 교체한 세션은 폐기된다.
      const replaced=await login(current);expect(replaced.statusCode).toBe(200);
      expect((await app.inject({url:'/my/api/who',headers:{...base,cookie:current}})).statusCode).toBe(401);
      const forbidden=await app.inject({method:'POST',url:'/my/api/review/login',headers:{cookie:cookieOf(replaced)},payload});
      expect(forbidden.statusCode).toBe(403); // CSRF guard 유지
      const bad=await app.inject({method:'POST',url:'/my/api/review/login',headers:{...base,cookie:cookieOf(replaced)},payload:{username:config.username,password:'wrong-dummy-password'}});
      expect(bad.statusCode).toBe(400);expect(bad.headers['set-cookie']).toContain('Max-Age=0');
    }finally{vi.restoreAllMocks();await app.close();}
  });
});

describe('로그인 출처별 제한과 한정된 공정 대기',()=>{
  it('한 직접 IP의 무효 요청/위조 전달 헤더가 다른 직접 IP의 입장을 소진하지 않음',async()=>{
    const f=fixture(),game=new GameService(f.db,loadContent());
    const app=await buildApp(f.db,game,{secret:'test-only-secret'.repeat(4),baseUrl:async()=>'',reviewLogin:f.c});
    try{
      for(let i=0;i<5;i++)expect((await app.inject({method:'POST',url:'/my/api/review/login',remoteAddress:'192.0.2.1',headers:{'x-gmm':'1','x-forwarded-for':`198.51.100.${i}`,'cf-connecting-ip':`198.51.100.${i}`},payload:{username:'invalid',password:''}})).statusCode).toBe(400);
      expect((await app.inject({method:'POST',url:'/my/api/review/login',remoteAddress:'192.0.2.1',headers:{'x-gmm':'1','x-forwarded-for':'203.0.113.99'},payload:{username:config.username,password}})).statusCode).toBe(429);
      expect((await app.inject({method:'POST',url:'/my/api/review/login',remoteAddress:'192.0.2.2',headers:{'x-gmm':'1'},payload:{username:config.username,password}})).statusCode).toBe(200);
    }finally{await app.close();}
  });
  it('DB 계정 검증 중 종료해도 뒤늦게 세션을 발급하지 않음',async()=>{
    const f=fixture();let entered!:()=>void,release!:(id:string)=>void;
    const ready=new Promise<void>(resolve=>{entered=resolve;});
    vi.spyOn(f.auth,'account').mockImplementation(()=>{entered();return new Promise<string>(resolve=>{release=resolve;});});
    const pending=f.auth.login(config.username,password,'source-a');
    await ready;f.auth.close();release(config.playerId);expect(await pending).toBeNull();
  });
  it('전역 대기는 8개로 제한하며 종료 시 대기 요청은 세션을 만들지 않음',async()=>{
    const f=fixture();
    const pending=Array.from({length:9},(_,i)=>f.auth.login(config.username,password,`source-${i}`));
    expect(await f.auth.login(config.username,password,'overflow')).toBeNull();
    f.auth.close();expect(await Promise.all(pending)).toEqual(Array(9).fill(null));
  });
  it('한 출처 중복 대기는 거부하되 다른 출처는 FIFO로 처리',async()=>{
    const f=fixture();const order:string[]=[];
    const a=f.auth.login(config.username,'wrong-dummy-password','source-a').then(x=>{order.push('a');return x;});
    const b=f.auth.login(config.username,password,'source-b').then(x=>{order.push('b');return x;});
    expect(await f.auth.login(config.username,password,'source-a')).toBeNull();
    expect(await f.auth.login(config.username,password,'source-b')).toBeNull();
    expect(await a).toBeNull();expect(await b).toMatch(/^rv_/);expect(order).toEqual(['a','b']);
  });
});
