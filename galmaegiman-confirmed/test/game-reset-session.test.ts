import {it,expect,vi} from 'vitest';
import {createHmac} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {resetSessionCutoff,sessionPredatesReset,ordinarySessionLifetimeSeconds as ttl} from '../src/game-reset-session.js';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {PrismaClient} from '@prisma/client';
it('reset cutoff rejects earlier and same-second sessions, accepts next second, fails closed on corrupt marker',()=>{
 const cutoff=resetSessionCutoff(JSON.stringify({epoch:'test',at:1000999}));expect(cutoff).toBe(1001);
 for(const issued of [999,1000])expect(sessionPredatesReset(`player.${issued+ttl}.signature`,cutoff)).toBe(true);
 expect(sessionPredatesReset(`player.${1001+ttl}.signature`,cutoff)).toBe(false);
 expect(resetSessionCutoff(null)).toBe(0);expect(()=>resetSessionCutoff('{}')).toThrow();expect(()=>resetSessionCutoff('bad')).toThrow();expect(sessionPredatesReset('p.NaN.s',cutoff)).toBe(true);
});
it('real HTTP boundary rejects old cookie/header requests including new request IDs before any debit, and accepts fresh same-account login token',async()=>{
 const secret='isolated-reset-test-secret'.repeat(3),now=Math.floor(Date.now()/1000),cutoff=now-10;
 const key=createHmac('sha256',secret).update('web-session').digest();
 function token(issued:number){const s=`test-player.${issued+ttl}`;return s+'.'+createHmac('sha256',key).update(s).digest('base64url');}
 const findUnique=vi.fn(async()=>({id:'test-player',identity:'normal:test'}));
 const db={player:{findUnique},appMeta:{findUnique:vi.fn(async()=>null)}} as unknown as PrismaClient;
 const game=new GameService(db,loadContent());const who=vi.spyOn(game,'webWho').mockResolvedValue({nickname:'same-account'} as any);
 const app=await buildApp(db,game,{secret,baseUrl:async()=>'',gameResetSessionCutoff:cutoff});
 try{
  for(const auth of [{'x-gmm-s':token(cutoff-1)},{cookie:'gmm_s='+token(cutoff-1)}]){
   for(const requestId of ['previous-saved-request','new-request-id']){
    const r=await app.inject({method:'POST',url:'/my/api/native/roster60/recruit/session/start',headers:{...auth,'x-gmm':'1'},payload:{requestId,kind:'LOW',catalogVersion:'old'}});
    expect(r.statusCode).toBe(401);expect(r.json().code).toBe('GAME_PROGRESS_RESET_RELOGIN');
   }
   for(const url of ['/my/api/native/roster60/battle/action','/my/api/native/roster60/confirm','/my/api/draw'])expect((await app.inject({method:'POST',url,headers:{...auth,'x-gmm':'1'},payload:{ownedId:'old-unit'}})).statusCode).toBe(401);
  }
  expect(findUnique).not.toHaveBeenCalled();expect(who).not.toHaveBeenCalled();
  const r=await app.inject({url:'/my/api/who',headers:{'x-gmm':'1','x-gmm-s':token(now)}});expect(r.statusCode).toBe(200);expect(who).toHaveBeenCalledWith('test-player');
  expect((await app.inject({url:'/my/api/who',headers:{'x-gmm':'1','x-gmm-s':token(now)+'tampered'}})).statusCode).toBe(401);
 }finally{await app.close();}
});
it('existing Android binds deferred requests to full session token and clears only the rejected session on401',()=>{
 const root='app/android/app/src/main/java/org/dpdns/gmm/';
 for(const file of ['NativeRecruitSession.java','Roster60RecruitPurchase.java','NativeRoster60Library.java'])expect(readFileSync(root+file,'utf8')).toContain('NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner)');
 const api=readFileSync(root+'NativeApi.java','utf8');expect(api).toContain('final String requestSession = token();');expect(api).toContain('if (requestSession.equals(token())) token("");');
 expect(readFileSync(root+'NativeProgression.java','utf8')).toContain('digest(account.getBytes(StandardCharsets.UTF_8))');
});
