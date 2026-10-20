// 실행할 때마다 OS 임시 폴더의 새 DB만 사용. 서버/OTP/토큰은 이 로컬 fixture 전용.
import {mkdtemp,readFile,writeFile,rm,chmod} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {PrismaClient} from '@prisma/client';
import {loadContent} from '../../src/content.js';
import {GameService} from '../../src/game.js';
import {buildApp} from '../../src/app.js';

const args=process.argv.slice(2);
if(args.length>1||(args.length===1&&args[0]!=='--check'))throw new Error('사용: review-server.sh [--check]');
const check=args[0]==='--check',root=resolve(import.meta.dirname,'../..');
// TMPDIR 등으로 운영 경로가 전달되지 않도록 /tmp를 명시. 사용자 지정 DB 경로 없음.
const dir=await mkdtemp('/tmp/gmm-render-fixture-');await chmod(dir,0o700);
const url=`file:${join(dir,'fixture.db')}`;
let db:PrismaClient|undefined,app:Awaited<ReturnType<typeof buildApp>>|undefined;
let closing:Promise<void>|undefined;
const close=()=>closing??=(async()=>{try{await app?.close();}finally{try{await db?.$disconnect();}finally{await rm(dir,{recursive:true,force:true});}}})();
try{
  // Prisma CLI는 격리 cwd와 복사된 schema만 보며 .env를 탐색하지 않는다.
  const schema=join(dir,'schema.prisma');await writeFile(schema,await readFile(join(root,'prisma/schema.prisma')),{mode:0o600});
  execFileSync(process.execPath,[join(root,'node_modules/prisma/build/index.js'),'db','push','--schema',schema,'--skip-generate'],{
    cwd:dir,env:{PATH:process.env.PATH??'',DATABASE_URL:url,PRISMA_HIDE_UPDATE_MESSAGE:'1'},stdio:'pipe'});
  db=new PrismaClient({datasources:{db:{url}}});
  const content=loadContent(root);
  await db.characterDefinition.createMany({data:content.characters.map(c=>({id:c.id,name:c.name,rarity:c.rarity,position:c.position,quote:c.quote,imageUrl:c.imageUrl,synergy:c.synergy??null}))});
  const player=await db.player.create({data:{identity:'fixture:render-only',nickname:'검토대장',credits:400,snack:30000,peanut:40,gameTutorial:9,tutorial:4}});
  const ids=['C1','C2','D1','T1','E1','I1','D2','D7','E2','T2','I2','E8','T7','D5'];
  await db.ownedCharacter.createMany({data:ids.map(characterId=>({playerId:player.id,characterId,obtainedVia:'LOCAL_FIXTURE'}))});
  await db.collectionEntry.createMany({data:ids.map(characterId=>({playerId:player.id,characterId,count:1}))});
  // 더미 OTP도 정상 WebLogin의 1회 소비·만료 규칙을 따른다. 영구 코드 분기 없음.
  await db.webLogin.create({data:{code:'135790',playerId:player.id,expiresAt:new Date(Date.now()+600000)}});
  app=await buildApp(db,new GameService(db,content),{root,secret:'local-render-fixture-only-not-a-production-key',baseUrl:async()=>'',log:false});
  if(!check)await app.listen({host:'127.0.0.1',port:3999});
  const request={method:'POST' as const,url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code:'135790'}};
  const login=await app.inject(request);
  if(login.statusCode!==200||typeof login.json().token!=='string')throw new Error('합성 OTP 로그인 실패');
  const token=login.json().token as string;
  if((await app.inject(request)).statusCode!==400)throw new Error('OTP 중복 사용이 차단되지 않음');
  if((await app.inject({url:'/my/api/native/state',headers:{'x-gmm':'1','x-gmm-s':token}})).statusCode!==200)throw new Error('합성 화면 상태 조회 실패');
  await writeFile(join(dir,'token.txt'),token,{mode:0o600});
  if(check){await close();console.log('PASS: 임시 DB/일회용 로그인/중복 차단/네이티브 상태/정리');}
  else{
    console.log(`검토 서버: http://127.0.0.1:3999\n캡처용 토큰 파일: ${join(dir,'token.txt')}\n종료(Ctrl+C)하면 임시 DB와 토큰을 삭제합니다.`);
    const stop=()=>{void close().then(()=>process.exit(0),()=>process.exit(1));};
    process.once('SIGINT',stop);process.once('SIGTERM',stop);
  }
}catch{await close();throw new Error('로컬 합성 fixture 실패. 임시 파일을 정리했습니다. 운영 환경은 사용하지 않았습니다.');}
