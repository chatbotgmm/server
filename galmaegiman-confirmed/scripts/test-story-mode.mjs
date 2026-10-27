import {mkdtempSync,openSync,closeSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// 실제 DB 주소를 상속하지 않고 항상 새 임시 SQLite에서만 실행한다.
const root=fileURLToPath(new URL('../',import.meta.url));
const dir=mkdtempSync(join(tmpdir(),'galmaegiman-tests-'));
const path=join(dir,'test.db');
closeSync(openSync(path,'wx',0o600));
const env={...process.env,DATABASE_URL:`file:${path}`,TEST_DB_URL:`file:${path}`};
delete env.ROSTER60_PROFILE;
delete env.ROSTER60_STORY_PROFILE;
const tests=[
  'test/story-content-invalid.test.ts',
  'test/story-mode-content.test.ts',
  'test/story-mode-villains.test.ts',
  'test/story-mode-api.integration.test.ts',
  'test/roster60-battle.integration.test.ts',
  'test/roster60-adventure.integration.test.ts',
  'test/roster60-party-actions.integration.test.ts',
  'test/roster60-observed-defense.integration.test.ts',
  'test/roster60-rebirth-transaction.integration.test.ts',
  'test/roster60-play-modes.integration.test.ts',
];
console.log(`스토리 검증용 임시 DB: ${path}`);
for(const args of [
  ['node_modules/prisma/build/index.js','db','push','--skip-generate'],
  ['--import','tsx','prisma/seed.ts'],
  ['node_modules/vitest/vitest.mjs','run','--maxWorkers=1',...tests],
]){
  const result=spawnSync(process.execPath,args,{cwd:root,env,stdio:'inherit'});
  if(result.status!==0)process.exit(result.status??1);
}
console.log('스토리 및 기존 60종 회귀 검증 완료. 운영 DB는 사용하지 않았습니다.');
