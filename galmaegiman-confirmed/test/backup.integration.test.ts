import {describe,it,expect} from 'vitest';
import {mkdtempSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {PrismaClient} from '@prisma/client';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('백업 검증',()=>{
  it('VACUUM INTO는 상태를 복사하며 기존 백업은 덮어쓰지 않음',async()=>{
    const source=process.env.TEST_DB_URL!.replace(/^file:/,'');
    if(!source.includes('/galmaegiman-tests-'))throw new Error('테스트 DB 이외 접근 차단');
    // JS 운영 스크립트를 그대로 시험합니다.
    const tools=await import(/* @vite-ignore */ resolve('scripts/db-tools.mjs'));
    const path=join(mkdtempSync(join(tmpdir(),'galmaegiman-tests-backup-')),'snapshot.db');
    await tools.snapshot(source,path);
    expect(existsSync(path)).toBe(true);
    expect(await tools.edition(path)).toBe('confirmed-75-v1');
    const backup=new PrismaClient({datasources:{db:{url:`file:${path}`}}});
    try{expect(await backup.characterDefinition.count()).toBe(75);}finally{await backup.$disconnect();}
    await expect(tools.snapshot(source,path)).rejects.toThrow('덮어쓰지');
  });
});
