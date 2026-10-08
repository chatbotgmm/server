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
html = html.replace(/<script>if\('serviceWorker'[^<]*<\/script>\n?/, '')
  .replace(/<link rel="manifest"[^>]*>/, '')
  .replace('<script src="/my/my.js"></script>', '<script src="app.js"></script>\n<script src="my.js"></script>');
if (!html.includes('src="app.js"')) throw new Error('index.html 에서 my.js 줄을 못 찾았어요');
// 화면 안 그림 경로(/gull.png 등)는 앱 안 파일로
html = html.replaceAll('href="/favicon.png"', 'href="favicon.png"').replaceAll('href="/icon-192.png"', 'href="icon-192.png"');
writeFileSync(resolve(out, 'index.html'), html);

let js = readFileSync(resolve(root, 'public/my/my.js'), 'utf8');
js = js.replaceAll('src="/gull.png"', 'src="gull.png"').replaceAll('src="/images/logo.png"', 'src="images/logo.png"');
writeFileSync(resolve(out, 'my.js'), js);
copyFileSync(resolve(here, 'src/app.js'), resolve(out, 'app.js'));
for (const f of ['gull.png', 'favicon-32.png', 'icon-192.png']) copyFileSync(resolve(root, 'public', f), resolve(out, f === 'favicon-32.png' ? 'favicon.png' : f));
copyFileSync(resolve(root, 'public/images/logo.png'), resolve(out, 'images/logo.png'));
console.log('www/ 준비 완료');
