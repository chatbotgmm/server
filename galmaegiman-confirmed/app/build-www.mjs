// 앱 화면 만들기: ../public/my(「내 갈매미」)를 www/ 로 복사하고, 앱 연결부(src/app.js)를 먼저 불러오게 끼워 넣음
// 사용: npm run www (그다음 npx cap sync android)
import { mkdirSync, readFileSync, writeFileSync, copyFileSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url)), root = resolve(here, '..'), out = resolve(here, 'www');
rmSync(out, { recursive: true, force: true });
mkdirSync(resolve(out, 'images'), { recursive: true });

let html = readFileSync(resolve(root, 'public/my/index.html'), 'utf8');
// 웹 전용(서비스 워커·앱 정보 파일)은 빼고, 앱 연결부를 my.js 앞에
html = html.replace(/<!--install-->[\s\S]*?<!--\/install-->\n?/, '')   // 웹의 「앱 설치」 칸은 앱에선 필요 없음
  .replace(/<script>if\('serviceWorker'[^<]*<\/script>\n?/, '')
  .replace(/<link rel="manifest"[^>]*>/, '')
  .replace('<script src="/my/my.js"></script>', '<script src="app.js"></script>\n<script src="my.js"></script>');
if (!html.includes('src="app.js"') || html.includes('id="inst"')) throw new Error('index.html 에서 my.js 줄 또는 앱 설치 칸 표시(<!--install-->)를 못 찾았어요');
// 화면 안 그림 경로(/gull.png 등)는 앱 안 파일로
html = html.replaceAll('href="/favicon.png"', 'href="favicon.png"').replaceAll('href="/icon-192.png"', 'href="icon-192.png"');
writeFileSync(resolve(out, 'my.html'), html.replace(', viewport-fit=cover', ''));

// 메인 = 「갈매미맨 수비대」(대결 게임): public/duel/index.html + 묶음 dist/duel/duel.js (먼저 루트에서 npm run build)
let game = readFileSync(resolve(root, 'public/duel/index.html'), 'utf8');
game = game.replace(/<script>if\('serviceWorker'[^<]*<\/script>\n?/, '').replace(/<link rel="manifest"[^>]*>/, '')
  .replace('<script type="module" src="/duel/duel.js"></script>', '<script src="app.js"></script>\n<script type="module" src="duel.js"></script>')
  .replaceAll('href="/favicon.png"', 'href="favicon.png"').replaceAll('href="/icon-192.png"', 'href="icon-192.png"');
// 앱: 상태 표시줄 밑까지 그리지 않게(viewport-fit=cover를 빼면 Capacitor가 상태 표시줄·내비게이션 바만큼 띄워 줌)
game = game.replace(', viewport-fit=cover', '');
if (!game.includes('src="duel.js"')) throw new Error('duel index.html 에서 duel.js 줄을 못 찾았어요');
writeFileSync(resolve(out, 'index.html'), game);
writeFileSync(resolve(out, 'duel.js'), readFileSync(resolve(root, 'dist/duel/duel.js'), 'utf8').replaceAll('src="/gull.png"', 'src="gull.png"'));

let js = readFileSync(resolve(root, 'public/my/my.js'), 'utf8');
js = js.replaceAll('src="/gull.png"', 'src="gull.png"').replaceAll('src="/images/logo.png"', 'src="images/logo.png"');
writeFileSync(resolve(out, 'my.js'), js);
copyFileSync(resolve(here, 'src/app.js'), resolve(out, 'app.js'));
for (const f of ['gull.png', 'favicon-32.png', 'icon-192.png']) copyFileSync(resolve(root, 'public', f), resolve(out, f === 'favicon-32.png' ? 'favicon.png' : f));
copyFileSync(resolve(root, 'public/images/logo.png'), resolve(out, 'images/logo.png'));
// 게임 첫 화면 그림(로그인 전에도 보여야 해서 앱 안에): 대표 그림
copyFileSync(resolve(root, 'public/battle/bg/key.jpg'), resolve(out, 'title.jpg'));
console.log('www/ 준비 완료');
