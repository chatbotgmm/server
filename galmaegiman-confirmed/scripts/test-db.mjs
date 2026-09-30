import {mkdtempSync,openSync,closeSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
const dir=mkdtempSync(join(tmpdir(),'galmaegiman-tests-'));
const url=`file:${join(dir,'test.db')}`;
// Prisma 6의 일부 환경에서 미존재 SQLite 파일 생성 시 빈 엔진 오류가 발생합니다.
// 새 임시 경로에만 빈 파일을 배타적으로 생성하고 스키마는 Prisma로 적용합니다.
closeSync(openSync(join(dir,'test.db'),'wx',0o600));
const env={...process.env,DATABASE_URL:url,TEST_DB_URL:url};
for(const args of [
  ['node_modules/prisma/build/index.js','db','push','--skip-generate'],
  ['--import','tsx','prisma/seed.ts'],
  ['node_modules/vitest/vitest.mjs','run','--maxWorkers=1']
]){
  const r=spawnSync(process.execPath,args,{env,stdio:'inherit'});
  if(r.status!==0)process.exit(r.status??1);
}
console.log(`격리된 테스트 DB: ${dir} (실제 DB는 수정하지 않았습니다.)`);
