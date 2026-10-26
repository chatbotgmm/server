import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {registerWebMaintenance,webMaintenanceTarget as target} from '../src/web-maintenance.ts';

const pages=['/game','/game/','/game?x=1','/game/game.js','/%67ame','/my/game','/my/game/','/my/classic','/my?g=1','/my?next=duel','/play','/battle','/duel?k=secret','/duel/duel.js'];
const actions=['story','story/battle','pvp/match','pvp/result','items/use','items/use-field'];
const shared=['/','/health','/ready','/about','/download','/login','/account','/review','/my','/my/kakao?app=2','/my/kakao/callback','/my/api/login','/my/api/review/login','/my/api/review/exchange','/my/api/app/exchange','/my/api/claim','/my/api/draw','/my/api/craft','/my/api/mix','/my/api/party','/my/api/expedition/start','/my/api/expedition/claim','/my/api/shop','/my/api/duel-key','/my/api/native/state','/my/api/native/roster60/recruit','/my/api/native/roster60/craft/confirm','/my/api/native/battle/start','/my/api/native/news/inbox','/my/api/native/news/read','/my/api/pvp/queue/join','/my/api/pvp/room/command','/duel/data.json','/duel/sprites/D1.png','/duel/bg/sea.png','/site/site.js','/kakao/skill'];
test('웹 진입·새로고침·캐시 스크립트·쿼리·HEAD를 서버에서 차단',()=>{for(const p of pages)for(const m of ['GET','HEAD'])assert.equal(target(p,m),'page',p);});
test('구형 클라이언트 계산 전투 쓰기만 선별 차단',()=>{for(const p of actions){assert.equal(target('/my/api/'+p,'POST'),'api');assert.equal(target('/my/api/'+p,'GET'),null);}});
test('홈·로그인·심사·공유 모집/조합·native API·앱 그림은 유지',()=>{for(const p of shared)for(const m of ['GET','POST'])assert.equal(target(p,m),null,p);});
test('플래그 off는 훅도 추가하지 않음',()=>{registerWebMaintenance({addHook(){assert.fail('unexpected hook');}},false);});
test('503·Retry-After·no-store와 한글 안내; 헤더로 우회 불가',async()=>{
 let hook;registerWebMaintenance({addHook(name,fn){assert.equal(name,'onRequest');hook=fn;}},true);
 for(const [url,method] of [['/game','GET'],['/my/api/pvp/match','POST']]){
  const reply={headers:{},code(n){this.status=n;return this;},header(k,v){this.headers[k]=v;return this;},type(t){this.contentType=t;return this;},send(body){this.body=body;return this;}};
  await hook({raw:{url},url,method,headers:{'x-gmm-app':'1','x-gmm':'1','x-gmm-s':'forged'}},reply);
  assert.equal(reply.status,503);assert.equal(reply.headers['Cache-Control'],'no-store');assert.equal(reply.headers['Retry-After'],'3600');
  if(method==='GET'){assert.match(reply.body,/웹게임 점검 중입니다/);assert.match(reply.body,/href="\/"/);}else assert.equal(reply.body.code,'WEB_GAME_MAINTENANCE');
 }
});
test('앱 빌더는 모든 라우트 등록 전에 훅을 설치, 운영 기본 on·false 복구',()=>{
 const app=readFileSync(new URL('../src/app.ts',import.meta.url),'utf8'),server=readFileSync(new URL('../src/server.ts',import.meta.url),'utf8');
 assert.ok(app.indexOf('registerWebMaintenance(app,')<app.indexOf('registerDesignReview(app,'));
 assert.match(server,/GMM_WEB_GAME_MAINTENANCE:z.enum\(\['true','false'\]\).default\('true'\)/);
 assert.match(server,/webGameMaintenance:env.GMM_WEB_GAME_MAINTENANCE==='true'/);
});
