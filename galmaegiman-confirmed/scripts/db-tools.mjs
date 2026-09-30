import {PrismaClient} from '@prisma/client';
import {existsSync,readFileSync,statSync,realpathSync,mkdtempSync,mkdirSync,copyFileSync,chmodSync} from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {pathToFileURL} from 'node:url';

const client=(path)=>new PrismaClient({datasources:{db:{url:`file:${resolve(path)}`}}});
export async function edition(path){
  if(!existsSync(path)||statSync(path).size===0)return '';
  const db=client(path);
  try{
    const rows=await db.$queryRawUnsafe('SELECT value FROM AppMeta WHERE key = \'edition\'');
    return rows[0]?.value??'';
  }catch{return '';}finally{await db.$disconnect();}
}
export async function snapshot(source,destination){
  if(!existsSync(source)||statSync(source).size===0)throw new Error('원본 SQLite DB를 찾을 수 없습니다.');
  if(existsSync(destination))throw new Error('기존 백업을 덮어쓰지 않습니다.');
  mkdirSync(dirname(destination),{recursive:true,mode:0o700});
  const db=client(source);
  try{
    // 사용자 입력 SQL을 실행하지 않습니다. 경로는 바인딩 파라미터입니다.
    await db.$executeRaw`VACUUM INTO ${resolve(destination)}`;
  }finally{await db.$disconnect();}
  chmodSync(destination,0o600);
  const check=client(destination);
  try{
    const rows=await check.$queryRawUnsafe('PRAGMA integrity_check');
    if(!rows.length||Object.values(rows[0])[0]!=='ok')throw new Error('백업 DB 무결성 검증 실패');
  }finally{await check.$disconnect();}
}
async function main(){
  const mode=process.argv[2];
  if(mode==='inspect'){console.log(await edition(process.argv[3]));return;}
  if(!['current','legacy'].includes(mode))throw new Error('사용법: db-tools.mjs current | legacy [이전 폴더] | inspect DB경로');
  const root=process.cwd();
  const legacy=mode==='legacy';
  const source=legacy?realpathSync(process.argv[3]??'../galmaegiman'):root;
  if(legacy){
    const pkg=JSON.parse(readFileSync(join(source,'package.json'),'utf8'));
    if(pkg.name!=='galmaegiman')throw new Error('이전 galmaegiman 프로젝트 경로가 아닙니다.');
  }
  const candidates=legacy?['data/galmaegiman.db','prisma/data/galmaegiman.db','prisma/galmaegiman.db']:['storage/confirmed.db'];
  const available=candidates.filter(rel=>existsSync(join(source,rel)));
  if(!available.length)throw new Error('알려진 경로에 DB가 없습니다. 원본은 변경하지 않았습니다. DATABASE_URL을 확인하세요.');
  mkdirSync(join(root,'backups'),{recursive:true,mode:0o700});
  const target=mkdtempSync(join(root,'backups',`${mode}-${new Date().toISOString().replace(/[:.]/g,'-')}-`));
  for(const rel of available)await snapshot(join(source,rel),join(target,rel));
  if(existsSync(join(source,'.env'))){copyFileSync(join(source,'.env'),join(target,'settings.env'));chmodSync(join(target,'settings.env'),0o600);}
  if(!legacy&&existsSync('config/public-url.txt'))copyFileSync('config/public-url.txt',join(target,'public-url.txt'));
  if(!legacy&&existsSync('config/refill-settings.json'))copyFileSync('config/refill-settings.json',join(target,'refill-settings.json'));
  console.log(`백업 및 무결성 확인 완료: ${target}\n설정 비밀키가 들어 있으므로 공개하지 마세요. 원본 DB는 삭제하지 않았습니다.`);
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url)await main();
