import { PrismaClient } from '@prisma/client';
import { readFile } from 'node:fs/promises';
import { z } from 'zod';
import { loadContent } from './content.js';
import { GameService } from './game.js';
import { buildApp } from './app.js';
const env=z.object({
  DATABASE_URL:z.string().startsWith('file:'),
  HOST:z.literal('127.0.0.1').default('127.0.0.1'),
  PORT:z.coerce.number().int().min(1024).max(65535).default(3000),
  SKILL_SECRET:z.string().min(32),KAKAO_BOT_ID:z.string().optional(),
  PUBLIC_BASE_URL:z.string().optional(),ADMIN_CODE:z.preprocess(v=>v===''?undefined:v,z.string().min(16).optional()),LOG_ENABLED:z.enum(['true','false']).default('true')
}).parse(process.env);
const db=new PrismaClient();
const content=loadContent();
const edition=await db.appMeta.findUnique({where:{key:'edition'}});
const hash=await db.appMeta.findUnique({where:{key:'contentHash'}});
if(edition?.value!==content.edition||hash?.value!==content.hash){
  await db.$disconnect();throw new Error('DB와 데이터 파일 버전이 다릅니다. scripts/setup.sh를 실행하세요.');
}
await db.$queryRawUnsafe('PRAGMA journal_mode=WAL');
await db.$queryRawUnsafe('PRAGMA busy_timeout=3000');
async function baseUrl(){
  const value=(await readFile('config/public-url.txt','utf8').catch(()=>env.PUBLIC_BASE_URL??'')).trim().replace(/\/$/,'');
  if(!value)return '';
  try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&u.pathname==='/'&&!u.search&&!u.hash?u.origin:'';}catch{return '';}
}
const app=await buildApp(db,new GameService(db,content,undefined,undefined,undefined,undefined,env.ADMIN_CODE),{secret:env.SKILL_SECRET,botId:env.KAKAO_BOT_ID,baseUrl,log:env.LOG_ENABLED==='true'});
let closing=false;
const close=async()=>{if(closing)return;closing=true;await app.close();await db.$disconnect();};
process.on('SIGTERM',()=>void close());process.on('SIGINT',()=>void close());
await app.listen({host:env.HOST,port:env.PORT});
