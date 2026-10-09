// 만든 APK를 웹사이트 「앱 받기」 칸에 올림
// - 저장소를 키우지 않으려고 작업 브랜치가 아니라 apk 브랜치에 「최신 1개만」 둠(올릴 때마다 덮어씀, 옛 APK는 남지 않음)
// - 서버는 update.sh 때 apk 브랜치에서 꺼내 public/app/ 에 둠(git 제외)
// 사용: (android 폴더에서 ./gradlew assembleDebug 또는 assembleRelease 뒤에) node publish-apk.mjs [apk 경로] [--no-push]
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url)), out = resolve(here, '../public/app');
const args = process.argv.slice(2), push = !args.includes('--no-push');
const src = resolve(args.find(a => !a.startsWith('--')) || resolve(here, 'android/app/build/outputs/apk/debug/app-debug.apk'));
const gradle = readFileSync(resolve(here, 'android/app/build.gradle'), 'utf8');
const versionCode = +(gradle.match(/versionCode\s+(\d+)/) || [])[1], versionName = (gradle.match(/versionName\s+"([^"]+)"/) || [])[1];
if (!versionCode || !versionName) throw new Error('build.gradle 에서 versionCode·versionName 을 못 찾았어요');

// 1) 이 컴퓨터의 public/app/ (시험 서버용, git 제외)
mkdirSync(out, { recursive: true });
const apk = resolve(out, 'galmaegiman-suhbidae.apk'), json = resolve(out, 'apk.json');
copyFileSync(src, apk);
const info = { versionName, versionCode, size: statSync(src).size, date: new Date().toISOString().slice(0, 10), test: /debug/.test(src) };
writeFileSync(json, `${JSON.stringify(info, null, 2)}\n`);
console.log(`public/app/ 에 넣었어요: ${versionName} (${versionCode}), ${(info.size / 1048576).toFixed(1)}MB`);
if (!push) process.exit(0);

// 2) apk 브랜치: 부모 없는 커밋 하나(파일 2개)로 덮어쓰기. 지금 브랜치·작업 폴더는 건드리지 않음
const git = (a, input) => execFileSync('git', a, { cwd: here, input, encoding: input === undefined ? 'utf8' : undefined }).toString().trim();
const blobA = git(['hash-object', '-w', apk]), blobJ = git(['hash-object', '-w', json]);
const tree = git(['mktree'], `100644 blob ${blobJ}\tapk.json\n100644 blob ${blobA}\tgalmaegiman-suhbidae.apk\n`);
const commit = git(['commit-tree', tree, '-m', `앱 ${versionName} (${versionCode})`], '');
git(['push', '-f', 'origin', `${commit}:refs/heads/apk`]);
console.log(`apk 브랜치에 올렸어요(${commit.slice(0, 7)}). 서버에서 update.sh 를 돌리면 사이트에 나와요.`);
