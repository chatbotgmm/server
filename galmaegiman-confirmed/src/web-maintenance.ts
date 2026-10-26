import type {FastifyInstance} from 'fastify';

// 경로만으로 구분되는 웹 진입과 구형 클라이언트 계산 전투만 잠급니다.
// native/*, 모집/조합, 로그인, 우편 및 /duel 그림은 Android와 공유하므로 유지합니다.
const legacyActions=new Set([
  '/my/api/story', '/my/api/story/battle',
  '/my/api/pvp/match', '/my/api/pvp/result',
  '/my/api/items/use', '/my/api/items/use-field',
]);
const webTrees=['/game','/my/game','/play','/battle'];
const webFiles=new Set(['/my/classic','/my/my.js','/my/dash.js','/duel','/duel/duel.js']);

export function webMaintenanceTarget(rawUrl:string,method:string):'page'|'api'|null {
  const url=new URL(rawUrl,'http://localhost');
  // 쿼리·마지막 슬래시·인코딩으로 같은 진입을 우회하지 않게 정규화합니다.
  let path:string;
  try {path=decodeURIComponent(url.pathname).replace(/\/+$/,'')||'/';} catch {return null;}
  if(method==='POST'&&legacyActions.has(path))return 'api';
  if(webTrees.some(root=>path===root||path.startsWith(root+'/'))||webFiles.has(path))return 'page';
  if(path==='/my'&&(url.searchParams.get('g')==='1'||url.searchParams.get('next')==='duel'))return 'page';
  return null;
}

const maintenancePage=`<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>웹게임 점검 중입니다</title><style>body{margin:0;background:#faf8f1;color:#263b49;font:18px/1.7 system-ui,sans-serif}main{max-width:36rem;margin:15vh auto;padding:24px}h1{font-size:1.8rem}a{color:#185c7a}</style><main><h1>웹게임 점검 중입니다</h1><p>웹게임을 잠시 쉬어갑니다. 공식 홈페이지와 앱 서비스는 계속 이용할 수 있습니다.</p><a href="/">홈으로 돌아가기</a></main></html>`;

export function registerWebMaintenance(app:FastifyInstance,enabled:boolean) {
  if(!enabled)return;
  // 인증/본문 처리와 실제 게임 핸들러보다 앞에서 종료. 클라이언트 헤더 예외 없음.
  app.addHook('onRequest',async(req,reply)=>{
    const target=webMaintenanceTarget(req.raw.url??req.url,req.method);
    if(!target)return;
    reply.code(503).header('Retry-After','3600').header('Cache-Control','no-store').header('X-Robots-Tag','noindex');
    if(target==='api')return reply.send({error:'웹게임 점검 중입니다',code:'WEB_GAME_MAINTENANCE'});
    return reply.type('text/html; charset=utf-8').send(maintenancePage);
  });
}
