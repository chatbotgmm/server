// 운영 DB를 바꾸지 않고 새 스키마를 "복사본"에 먼저 반영해 기존 데이터가 그대로인지 확인한다.
// 사용(새 코드를 받은 폴더에서, npm ci 후):
//   node scripts/manual/schema-rehearsal.mjs <원본 DB 경로>
// 원본은 VACUUM INTO로 읽기만 한다. 복사본·결과는 임시 폴더에 남기고 지우지 않는다.
import {PrismaClient} from '@prisma/client';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {mkdtempSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {snapshot} from '../db-tools.mjs';

const client=path=>new PrismaClient({datasources:{db:{url:`file:${resolve(path)}`}}});
const text=v=>typeof v==='bigint'?v.toString():v instanceof Date?v.toISOString():v;

// 표마다 행 수와, 반영 전부터 있던 열만으로 만든 지문(SHA-256).
async function fingerprint(path,columns){
  const db=client(path),out={};
  try{
    const tables=(await db.$queryRawUnsafe("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '_prisma%' ORDER BY name")).map(r=>r.name);
    for(const table of tables){
      const cols=columns?.[table]?.cols??(await db.$queryRawUnsafe(`PRAGMA table_info("${table}")`)).map(c=>c.name);
      const rows=await db.$queryRawUnsafe(`SELECT ${cols.map(c=>`"${c}"`).join(',')} FROM "${table}" ORDER BY rowid`);
      const hash=createHash('sha256');
      for(const row of rows)hash.update(JSON.stringify(cols.map(c=>text(row[c]))));
      out[table]={cols,count:rows.length,sha256:hash.digest('hex')};
    }
  }finally{await db.$disconnect();}
  return out;
}

const source=process.argv[2];
if(!source){console.error('사용법: node scripts/manual/schema-rehearsal.mjs <원본 DB 경로>');process.exit(2);}
const work=mkdtempSync(join(tmpdir(),'gmm-schema-rehearsal-')),copy=join(work,'copy.db');
await snapshot(source,copy);
const before=await fingerprint(copy);
const push=spawnSync(process.execPath,['node_modules/prisma/build/index.js','db','push','--skip-generate'],
  {env:{...process.env,DATABASE_URL:`file:${copy}`},stdio:'inherit'});
if(push.status!==0){console.error(`❌ 복사본 스키마 반영 실패(데이터 손실 경고 포함 가능). 운영에는 적용하지 마세요. 복사본: ${copy}`);process.exit(1);}
const after=await fingerprint(copy,before);
const changed=Object.keys(before).filter(t=>!after[t]||after[t].count!==before[t].count||after[t].sha256!==before[t].sha256);
const added=Object.keys(await fingerprint(copy)).filter(t=>!before[t]);
for(const t of Object.keys(before))console.log(`${changed.includes(t)?'❌':'✅'} ${t}: ${before[t].count}행`);
console.log(`새 표: ${added.join(', ')||'없음'}`);
if(changed.length){console.error(`❌ 기존 데이터가 달라졌어요: ${changed.join(', ')}. 운영에는 적용하지 마세요. 복사본: ${copy}`);process.exit(1);}
console.log(`✅ 기존 표의 행 수와 기존 열 값이 반영 전과 같아요. 원본은 바꾸지 않았어요. 복사본: ${copy}`);
