import {describe,it,expect} from 'vitest';
import Fastify from 'fastify';
import {registerWebMaintenance} from '../src/web-maintenance.js';

describe('웹게임 점검 Fastify 진입 회귀',()=>{
  it('GET/HEAD/POST 차단이 실제 핸들러 실행보다 앞서고 공유 경로는 통과한다',async()=>{
    const app=Fastify();let mutations=0;
    registerWebMaintenance(app,true);
    app.get('/game',async()=>({game:true}));
    app.post('/my/api/pvp/match',async()=>{mutations++;return {ok:true};});
    for(const path of ['/','/login','/my/kakao','/review','/my/api/native/state','/duel/data.json'])app.get(path,async()=>({ok:true}));
    for(const path of ['/my/api/claim','/my/api/native/roster60/recruit','/my/api/review/login'])app.post(path,async()=>({ok:true}));
    try{
      for(const method of ['GET','HEAD'] as const){const r=await app.inject({method,url:'/game?cached=1'});expect(r.statusCode).toBe(503);expect(r.headers['cache-control']).toBe('no-store');expect(r.headers['retry-after']).toBe('3600');}
      const r=await app.inject({method:'POST',url:'/my/api/pvp/match',headers:{'x-gmm-app':'1'},payload:{}});expect(r.statusCode).toBe(503);expect(mutations).toBe(0);
      for(const url of ['/','/login','/my/kakao','/review','/my/api/native/state','/duel/data.json'])expect((await app.inject({url})).statusCode).toBe(200);
      for(const url of ['/my/api/claim','/my/api/native/roster60/recruit','/my/api/review/login'])expect((await app.inject({method:'POST',url,payload:{}})).statusCode).toBe(200);
    }finally{await app.close();}
  });
});
