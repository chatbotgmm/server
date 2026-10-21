// Play 심사 전용 계정 준비·검증(EC2에서 승인 단계마다 한 명령씩).
// 비밀번호는 화면에 표시하지 않고 받으며, .env에는 scrypt 해시만 저장한다. 평문은 파일·로그·명령줄에 남기지 않는다.
// 사용(galmaegiman-confirmed 폴더에서, DB 주소는 .env에서 읽음):
//   node --env-file=.env scripts/manual/review-account.mjs check      읽기 전용: 서버 설정·계정 상태 확인
//   node --env-file=.env scripts/manual/review-account.mjs create     심사 계정 1개 생성(새 이용자와 같은 시작 상태)
//   node --env-file=.env scripts/manual/review-account.mjs prepare    승인한 시작 자원 1회 지급(아래 GRANT). 이미 갈매미가 있으면 아무것도 하지 않음
//   node --env-file=.env scripts/manual/review-account.mjs password   비밀번호 입력 → 해시·사용자명·계정 ID를 .env에 저장(활성화는 하지 않음)
//   node --env-file=.env scripts/manual/review-account.mjs enable     REVIEW_LOGIN_ENABLED=true (그다음 서버 재시작 필요)
//   node --env-file=.env scripts/manual/review-account.mjs disable    REVIEW_LOGIN_ENABLED=false (재시작하면 모든 심사 세션 폐기)
//   node --env-file=.env scripts/manual/review-account.mjs verify     비밀번호 입력 → 이 서버에 로그인·주요 화면·로그아웃·재로그인 확인(상태 코드만 출력)
import {PrismaClient} from '@prisma/client';
import {randomBytes, scrypt} from 'node:crypto';
import {existsSync, readFileSync, writeFileSync, statSync, chmodSync} from 'node:fs';

const IDENTITY = 'review:play-store';
const USERNAME = 'gmm-review';
const ENV = '.env';
const KEYS = ['REVIEW_LOGIN_ENABLED', 'REVIEW_LOGIN_USERNAME', 'REVIEW_LOGIN_PLAYER_ID', 'REVIEW_LOGIN_PASSWORD_HASH', 'REVIEW_LOGIN_VERSION'];

const readEnv = () => {
  const out = {};
  if (!existsSync(ENV)) return out;
  for (const line of readFileSync(ENV, 'utf8').split('\n')) {
    const m = /^([A-Z0-9_]+)=(.*)$/.exec(line);
    if (m) out[m[1]] = m[2].replace(/^"(.*)"$/, '$1');
  }
  return out;
};
// 기존 줄 순서·다른 값은 그대로 두고 지정한 키만 바꾸거나 끝에 추가한다. 파일 권한도 유지한다.
const writeEnv = values => {
  if (!existsSync(ENV)) throw new Error('.env가 없습니다. galmaegiman-confirmed 폴더에서 실행해 주세요.');
  const mode = statSync(ENV).mode & 0o777;
  const lines = readFileSync(ENV, 'utf8').split('\n');
  const left = new Map(Object.entries(values));
  const next = lines.map(line => {
    const m = /^([A-Z0-9_]+)=/.exec(line);
    if (m && left.has(m[1])) { const v = left.get(m[1]); left.delete(m[1]); return `${m[1]}=${v}`; }
    return line;
  });
  if (next.at(-1) === '') next.pop();
  for (const [k, v] of left) next.push(`${k}=${v}`);
  writeFileSync(ENV, next.join('\n') + '\n', {mode});
  chmodSync(ENV, mode);
};

// 화면에 글자를 표시하지 않고 한 줄을 읽는다.
const hidden = question => new Promise((resolve, reject) => {
  const stdin = process.stdin;
  if (!stdin.isTTY) return reject(new Error('터미널에서 직접 실행해 주세요(입력 숨김 필요).'));
  // 화면 표시(echo)를 먼저 끈 뒤 질문을 보여준다. 반대 순서면 빠른 입력·붙여넣기가 화면에 남을 수 있다.
  stdin.setRawMode(true); stdin.resume(); stdin.setEncoding('utf8');
  let value = '';
  process.stdout.write(question);
  const done = err => { stdin.setRawMode(false); stdin.pause(); stdin.removeListener('data', onData); process.stdout.write('\n'); err ? reject(err) : resolve(value); };
  const onData = chunk => {
    for (const ch of chunk) {
      if (ch === '\r' || ch === '\n') return done();
      if (ch === '\u0003') return done(new Error('취소했어요.'));
      if (ch === '\u007f' || ch === '\b') value = value.slice(0, -1);
      else if (ch >= ' ') value += ch;
    }
  };
  stdin.on('data', onData);
});

const hash = (password, salt) => new Promise((resolve, reject) =>
  scrypt(password, salt, 32, {N: 131072, r: 8, p: 1, maxmem: 192 * 1024 * 1024}, (e, key) => e ? reject(e) : resolve(key)));

// 심사자가 실시간 PvP(최상위 3마리 필요)까지 직접 확인할 수 있게 하는 1회 시작 자원. 게임 규칙·확률은 바꾸지 않는다.
const GRANT = {nickname: 'GmmReview', credits: 100, snack: 3000, tutorial: 4, gameTutorial: 9,
  units: ['D1', 'D2', 'D3', 'C1', 'C2', 'U1'], team: 'D1,D2,D3'};

const db = new PrismaClient();
const account = () => db.player.findUnique({where: {identity: IDENTITY}, include: {kakao: true, chatApp: true, adminSnapshot: true}});
const clean = p => !!p && !p.isAdmin && !p.adminMode && !p.kakao && !p.chatApp && !p.adminSnapshot;

async function check() {
  const env = readEnv(), p = await account();
  console.log('서버 설정(.env, 값은 표시하지 않음):');
  for (const k of KEYS) console.log(`  ${k}: ${k === 'REVIEW_LOGIN_ENABLED' ? (env[k] ?? '없음') : env[k] ? '설정됨' : '없음'}`);
  if (env.REVIEW_LOGIN_PLAYER_ID && p && env.REVIEW_LOGIN_PLAYER_ID !== p.id) console.log('  ⚠ REVIEW_LOGIN_PLAYER_ID가 심사 계정 ID와 다릅니다.');
  console.log(`심사 계정(${IDENTITY}): ${p ? `있음 · 관리자 ${p.isAdmin || p.adminMode ? '예 ⚠' : '아니요'} · 카카오/챗봇 연결 ${p.kakao || p.chatApp ? '있음 ⚠' : '없음'} · 관리 스냅샷 ${p.adminSnapshot ? '있음 ⚠' : '없음'}` : '없음'}`);
  const legacy = await db.player.count({where: {identity: {startsWith: 'review:'}, NOT: {identity: IDENTITY}}});
  console.log(`예전 심사 계정(review:*, 로그인 차단됨): ${legacy}개`);
  if (p) {
    const owned = await db.ownedCharacter.count({where: {playerId: p.id}});
    console.log(`  재화: 뽑기권 ${p.credits} · 새우깡 ${p.snack} · 보유 갈매미 ${owned}마리 · 튜토리얼 ${p.gameTutorial}`);
  }
}

async function create() {
  const existing = await account();
  if (existing) { console.log(`이미 있어요(새로 만들지 않음). 안전 조건 ${clean(existing) ? '통과' : '실패 ⚠'}`); return; }
  // 새 이용자와 같은 기본값으로만 만든다(재화·갈매미 추가 지급, 관리자 권한, 카카오 연결 없음).
  const p = await db.player.create({data: {identity: IDENTITY}});
  console.log(`심사 계정을 만들었어요. 다음 단계: node scripts/manual/review-account.mjs password`);
  void p;
}

async function prepare() {
  const p = await account();
  if (!clean(p)) throw new Error('안전 조건을 만족하는 심사 계정이 없어요. check/create를 먼저 실행해 주세요.');
  const owned = await db.ownedCharacter.count({where: {playerId: p.id}});
  if (owned) { console.log(`이미 갈매미 ${owned}마리가 있어 지급하지 않았어요(중복 지급 방지).`); return; }
  await db.$transaction(async tx => {
    await tx.player.update({where: {id: p.id}, data: {nickname: GRANT.nickname, credits: GRANT.credits, snack: GRANT.snack,
      tutorial: GRANT.tutorial, gameTutorial: GRANT.gameTutorial, battleTeam: GRANT.team}});
    for (const characterId of GRANT.units) {
      await tx.ownedCharacter.create({data: {playerId: p.id, characterId, obtainedVia: 'review-grant'}});
      await tx.collectionEntry.upsert({where: {playerId_characterId: {playerId: p.id, characterId}},
        create: {playerId: p.id, characterId}, update: {count: {increment: 1}}});
    }
  });
  console.log(`지급했어요: 닉네임 ${GRANT.nickname} · 뽑기권 ${GRANT.credits} · 새우깡 ${GRANT.snack} · 갈매미 ${GRANT.units.join(',')} · 대전 편성 ${GRANT.team} · 튜토리얼 완료`);
}

async function password() {
  const p = await account();
  if (!clean(p)) throw new Error('안전 조건을 만족하는 심사 계정이 없어요. check/create를 먼저 실행해 주세요.');
  const first = await hidden('새 심사 비밀번호(16~256자, 입력 내용은 보이지 않음): ');
  if (first.length < 16 || first.length > 256) throw new Error('비밀번호는 16~256자여야 해요.');
  const again = await hidden('한 번 더 입력: ');
  if (first !== again) throw new Error('두 입력이 달라요. 다시 실행해 주세요.');
  const salt = randomBytes(16), key = await hash(first, salt);
  const env = readEnv();
  writeEnv({
    REVIEW_LOGIN_ENABLED: env.REVIEW_LOGIN_ENABLED === 'true' ? 'true' : 'false',
    REVIEW_LOGIN_USERNAME: USERNAME,
    REVIEW_LOGIN_PLAYER_ID: p.id,
    REVIEW_LOGIN_PASSWORD_HASH: `scrypt$131072$8$1$${salt.toString('hex')}$${key.toString('hex')}`,
    // 비밀번호를 바꿀 때마다 새 버전 → 재시작 후 이전 세션은 모두 무효.
    REVIEW_LOGIN_VERSION: randomBytes(12).toString('base64url'),
  });
  console.log(`저장했어요(해시만). 사용자명: ${USERNAME}. 활성화는 enable 후 서버 재시작.`);
}

function toggle(on) {
  writeEnv({REVIEW_LOGIN_ENABLED: on ? 'true' : 'false'});
  console.log(`REVIEW_LOGIN_ENABLED=${on}. 적용하려면: sudo systemctl restart gmm-server`);
}

async function verify() {
  const base = process.env.REVIEW_VERIFY_BASE ?? 'http://127.0.0.1:3000';
  const secret = await hidden(`${USERNAME} 비밀번호(보이지 않음): `);
  const head = token => ({'x-gmm': '1', 'x-gmm-app': '1', 'content-type': 'application/json', ...(token ? {'x-gmm-s': token} : {})});
  const call = async (method, path, token, body) => {
    const r = await fetch(`${base}/my/api/${path}`, {method, headers: head(token), body: body ? JSON.stringify(body) : undefined});
    let json = null; try { json = await r.json(); } catch {}
    return {status: r.status, json};
  };
  const line = (name, ok, status) => { console.log(`${ok ? '✅' : '❌'} ${name} (${status})`); return ok; };
  let ok = true;
  const login = await call('POST', 'review/login', null, {username: USERNAME, password: secret});
  const token = login.json?.token;
  ok = line('심사 로그인', login.status === 200 && typeof token === 'string' && token.startsWith('rv_'), login.status) && ok;
  if (!token) { process.exitCode = 1; return; }
  for (const path of ['me', 'native/state', 'native/catalog', 'native/progression', 'daily', 'pvp/rank', 'pvp/queue/current'])
  { const r = await call('GET', path, token); ok = line(`GET ${path}`, r.status === 200, r.status) && ok; }
  const blocked = await call('POST', 'account/delete', token, {});
  ok = line('계정 삭제는 막힘', blocked.status === 403, blocked.status) && ok;
  const out = await call('POST', 'logout', token, {});
  ok = line('로그아웃', out.status === 200, out.status) && ok;
  const after = await call('GET', 'me', token);
  ok = line('로그아웃한 세션은 거부', after.status === 401, after.status) && ok;
  const again = await call('POST', 'review/login', null, {username: USERNAME, password: secret});
  ok = line('다시 로그인', again.status === 200 && String(again.json?.token ?? '').startsWith('rv_'), again.status) && ok;
  if (again.json?.token) await call('POST', 'logout', again.json.token, {});
  console.log(ok ? '모든 확인 통과' : '실패 항목이 있어요');
  if (!ok) process.exitCode = 1;
}

const command = process.argv[2];
const run = {check, create, prepare, password, enable: async () => toggle(true), disable: async () => toggle(false), verify}[command];
if (!run) { console.error('사용법: node scripts/manual/review-account.mjs check|create|prepare|password|enable|disable|verify'); process.exitCode = 2; }
else {
  try { await run(); } catch (e) { console.error('❌ ' + (e instanceof Error ? e.message : e)); process.exitCode = 1; } finally { await db.$disconnect(); }
}
