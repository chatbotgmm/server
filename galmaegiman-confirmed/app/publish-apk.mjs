// 만든 APK를 웹사이트 내려받기 자리(public/app/)에 넣고 버전 정보(apk.json)를 적음 → 커밋·배포하면 「내 갈매미」 맨 아래 설치 칸에 나옴
// 사용: (android 폴더에서 ./gradlew assembleDebug 또는 assembleRelease 뒤에) node publish-apk.mjs [apk 경로]
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url)), out = resolve(here, '../public/app');
const src = resolve(process.argv[2] || resolve(here, 'android/app/build/outputs/apk/debug/app-debug.apk'));
const gradle = readFileSync(resolve(here, 'android/app/build.gradle'), 'utf8');
const versionCode = +(gradle.match(/versionCode\s+(\d+)/) || [])[1], versionName = (gradle.match(/versionName\s+"([^"]+)"/) || [])[1];
if (!versionCode || !versionName) throw new Error('build.gradle 에서 versionCode·versionName 을 못 찾았어요');
mkdirSync(out, { recursive: true });
copyFileSync(src, resolve(out, 'galmaegiman-suhbidae.apk'));
const info = { versionName, versionCode, size: statSync(src).size, date: new Date().toISOString().slice(0, 10), test: /debug/.test(src) };
writeFileSync(resolve(out, 'apk.json'), `${JSON.stringify(info, null, 2)}\n`);
console.log(`public/app/ 에 넣었어요: ${versionName} (${versionCode}), ${(info.size / 1048576).toFixed(1)}MB`);
