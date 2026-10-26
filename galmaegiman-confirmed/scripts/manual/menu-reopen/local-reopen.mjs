import {readFileSync,writeFileSync,realpathSync,statSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash,randomUUID} from 'node:crypto';
import {plan,apply,features} from './reopen.mjs';

const hash=s=>createHash('sha256').update(s).digest('hex');
export async function localContext(db,service,{serviceDir,databaseFile,adminPlayerId}){
 const databases=await db.$queryRawUnsafe('PRAGMA database_list');
 const main=databases.find(d=>d.name==='main');
 if(!main?.file||realpathSync(main.file)!==databaseFile)throw Error('DATABASE_PATH_MISMATCH');
 const admins=await db.player.findMany({where:{isAdmin:true,NOT:{identity:{startsWith:'review:'}}},select:{id:true},orderBy:{id:'asc'}});
 const actor=adminPlayerId?admins.find(p=>p.id===adminPlayerId):admins.length===1?admins[0]:null;
 if(!actor)throw Error(adminPlayerId?'ADMIN_NOT_ELIGIBLE':'EXACTLY_ONE_ADMIN_OR_EXPLICIT_ID_REQUIRED');
 const accounts=await db.player.findMany({select:{id:true},orderBy:{id:'asc'}});
 if(!accounts.length)throw Error('EMPTY_TEST_SERVER');
 const api=(path,body)=>body?service.change(actor.id,body):service.read(new URLSearchParams(path.slice(1)).get('playerId'));
 return {actor:actor.id,ids:accounts.map(a=>a.id),api,serviceDir,databaseFile,readIds:async()=>(await db.player.findMany({select:{id:true},orderBy:{id:'asc'}})).map(a=>a.id)};
}
export async function localPlan(db,c){
 const p=await plan(c.api,c.ids),run=randomUUID();
 const snapshots=[];
 for(let i=0;i<p.accounts.length;i++){
  const a=p.accounts[i];
  for(const f of features)a.requests[f]=`ec2-shell:${run}:${i}:${f}`;
  const key='menu-access:v1:account:'+hash(a.id),row=await db.appMeta.findUnique({where:{key}});
  const saved=row?JSON.parse(row.value):{revision:0,overrides:{}};
  if(saved.revision!==a.revision||features.some(f=>(saved.overrides?.[f]??'auto')!==a.before[f]))throw Error('PLAN_CHANGED_DURING_SNAPSHOT');
  snapshots.push({key,value:row?.value??null});
 }
 return {...p,source:'ec2-operator-shell',run,actorPlayerId:c.actor,serviceDir:c.serviceDir,databaseFile:c.databaseFile,accountCount:c.ids.length,appMetaBefore:snapshots};
}
export async function localApply(c,p,expectedCount){
 if(p.source!=='ec2-operator-shell'||p.actorPlayerId!==c.actor||p.serviceDir!==c.serviceDir||p.databaseFile!==c.databaseFile||p.accountCount!==expectedCount||p.accountCount!==c.ids.length||JSON.stringify(p.accounts?.map(a=>a.id))!==JSON.stringify(c.ids))throw Error('LOCAL_PLAN_SCOPE_CHANGED');
 if(p.accounts.some(a=>features.some(f=>!a.requests?.[f]?.startsWith(`ec2-shell:${p.run}:`))))throw Error('LOCAL_PLAN_PROVENANCE_INVALID');
 if(JSON.stringify(await c.readIds())!==JSON.stringify(c.ids))throw Error('ACCOUNT_SET_CHANGED');
 const result=await apply(c.api,p);
 if(JSON.stringify(await c.readIds())!==JSON.stringify(c.ids))throw Error('ACCOUNT_SET_CHANGED_AFTER_APPLY');
 return result;
}
function options(args){
 const o={};for(let i=0;i<args.length;i++){
  const k=args[i];if(k==='--apply'){if(o.apply)throw Error('INVALID_ARGUMENT');o.apply=true;continue;}
  if(!['--service-dir','--database-file','--plan','--expect-accounts','--admin-player-id'].includes(k)||o[k]!==undefined||!args[i+1]||args[i+1].startsWith('--'))throw Error('INVALID_ARGUMENT');o[k]=args[++i];
 }
 if(!o['--service-dir']||!o['--database-file']||!o['--plan'])throw Error('SERVICE_DIR_DATABASE_FILE_PLAN_REQUIRED');
 if(o.apply&&(!/^\d+$/.test(o['--expect-accounts']??'')||Number(o['--expect-accounts'])<1))throw Error('EXPECTED_ACCOUNT_COUNT_REQUIRED');
 return o;
}
async function main(){
 const o=options(process.argv.slice(2));
 if(process.env.ROSTER60_PROFILE!=='five-stat-device-test-v1')throw Error('EXPLICIT_TEST_PROFILE_REQUIRED');
 if(!process.env.DATABASE_URL?.startsWith('file:'))throw Error('EXISTING_SQLITE_DATABASE_URL_REQUIRED');
 const serviceDir=realpathSync(o['--service-dir']),databaseFile=realpathSync(o['--database-file']);
 if(realpathSync(process.cwd())!==serviceDir||!statSync(databaseFile).isFile())throw Error('SERVICE_DIRECTORY_OR_DATABASE_MISMATCH');
 const planFile=resolve(o['--plan']);
 if(o.apply){const s=statSync(planFile);if(!s.isFile()||(s.mode&0o077)!==0)throw Error('PRIVATE_PLAN_FILE_REQUIRED');}
 // Use only the deployed module and generated Prisma client from the confirmed service folder.
 const {MenuAccess}=await import(pathToFileURL(join(serviceDir,'dist/src/menu-access.js')).href);
 const {PrismaClient}=await import(pathToFileURL(join(serviceDir,'node_modules/@prisma/client/default.js')).href);
 const db=new PrismaClient();
 try{
  const service=new MenuAccess(db,()=>null),c=await localContext(db,service,{serviceDir,databaseFile,adminPlayerId:o['--admin-player-id']});
  if(!o.apply){
   const p=await localPlan(db,c);writeFileSync(planFile,JSON.stringify(p,null,2)+'\n',{flag:'wx',mode:0o600});
   console.log(JSON.stringify({applied:false,source:p.source,actorVerified:true,accounts:p.accountCount,plannedChanges:p.accounts.reduce((n,a)=>n+features.filter(f=>a.before[f]!=='force_unlock').length,0)}));
  }else{
   const p=JSON.parse(readFileSync(planFile,'utf8'));
   console.log(JSON.stringify({...await localApply(c,p,Number(o['--expect-accounts'])),source:p.source,actorVerified:true}));
  }
 }finally{await db.$disconnect();}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)main().catch(e=>{
 const code=e.message??'';console.error(/^[A-Z][A-Z0-9_]+$/.test(code)?code:'LOCAL_REOPEN_FAILED_STOP_AND_REVIEW');process.exitCode=1;
});
