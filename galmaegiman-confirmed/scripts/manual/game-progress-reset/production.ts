import {PrismaClient} from '@prisma/client';
import {execFileSync} from 'node:child_process';
import {realpathSync,lstatSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {applyTransaction,plan,erased,initial,marker,preserved} from './reset.js';
export const approvedDb='/home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db';
export const confirmation='RESET GAME PROGRESS KEEP ALL LOGIN IDENTITIES';
const canonical=(v:unknown)=>JSON.stringify(v,(_,x)=>typeof x==='bigint'?x.toString():x);
export function requireStopped(state:string,pid:string){if(state.trim()!=='inactive'||pid.trim()!=='0')throw Error('SERVICE_MUST_BE_STOPPED');}
function stopped(){requireStopped(execFileSync('systemctl',['show','gmm-server.service','-p','ActiveState','--value'],{encoding:'utf8'}),execFileSync('systemctl',['show','gmm-server.service','-p','MainPID','--value'],{encoding:'utf8'}));}
async function connect(path:string){const db=new PrismaClient({datasourceUrl:'file:'+path+'?mode=ro'});return db;}
export async function verifyReset(db:PrismaClient,before:PrismaClient){
 const a=await before.player.findMany({orderBy:{id:'asc'}}),b=await db.player.findMany({orderBy:{id:'asc'}});
 const changed=new Set([...Object.keys(initial()),'revision']);
 const identity=(rows:any[])=>rows.map(p=>Object.fromEntries(Object.entries(p).filter(([k])=>!changed.has(k))));
 if(canonical(identity(a))!==canonical(identity(b)))throw Error('IDENTITY_CHANGED');
 for(const t of preserved.filter(x=>!['Player','AppMeta','AuditEvent'].includes(x))){
  if(canonical(await before.$queryRawUnsafe(`SELECT * FROM "${t}" ORDER BY rowid`))!==canonical(await db.$queryRawUnsafe(`SELECT * FROM "${t}" ORDER BY rowid`)))throw Error('PRESERVED_DATA_CHANGED:'+t);
 }
 const oldAudit=await before.auditEvent.findMany();
 for(const e of oldAudit){if(canonical(await db.auditEvent.findUnique({where:{id:e.id}}))!==canonical(e))throw Error('AUDIT_CHANGED');}
 const oldMeta=await before.appMeta.findMany();for(const e of oldMeta)if(canonical(await db.appMeta.findUnique({where:{key:e.key}}))!==canonical(e))throw Error('APP_META_CHANGED');
 if(!await db.appMeta.findUnique({where:{key:marker}}))throw Error('RESET_MARKER_MISSING');
 for(const t of erased.filter(x=>!['Roster60Owned','Roster60State'].includes(x))){const r=await db.$queryRawUnsafe<{n:bigint}[]>(`SELECT COUNT(*) n FROM "${t}"`);if(Number(r[0].n)!==0)throw Error('PROGRESS_REMAINS:'+t);}
 if(await db.roster60Owned.count()!==a.length||await db.roster60State.count()!==a.length)throw Error('STARTER_COUNT_INVALID');
 for(let i=0;i<b.length;i++){
  for(const [key,value] of Object.entries(initial()))if(canonical((b[i] as any)[key])!==canonical(value))throw Error('INITIAL_VALUE_INVALID:'+key);
  if(b[i].revision!==a[i].revision+1)throw Error('REVISION_INVALID');
  const s=await db.roster60State.findUniqueOrThrow({where:{playerId:b[i].id}}),u=await db.roster60Owned.findUniqueOrThrow({where:{id:s.starterId}});
  if(u.playerId!==b[i].id||u.speciesId!=='u01'||u.totalXp!==0n||u.reincarnation!==0||u.status!=='AVAILABLE'||u.locked||s.tutorialProof||s.completedAt||s.mailId||s.selectedId||s.rewardOwnedId)throw Error('START_STATE_INVALID');
 }
 if((await db.$queryRawUnsafe<unknown[]>('PRAGMA foreign_key_check')).length)throw Error('FOREIGN_KEYS_INVALID');
 return {accounts:a.length,identityPreserved:true,starterVerified:true};
}
export async function runProduction(mode:string,backupPath:string,expected:string,ack:string){
 if(!['plan','apply','verify'].includes(mode))throw Error('MODE_INVALID');
 if(realpathSync(approvedDb)!==approvedDb||!lstatSync(approvedDb).isFile())throw Error('DB_TARGET_CHANGED');
 stopped();
 if(mode==='plan'){const db=await connect(approvedDb);try{return await plan(db);}finally{await db.$disconnect();}}
 if(ack!==confirmation)throw Error('CONFIRMATION_REQUIRED');
 const backup=realpathSync(backupPath);
 if(!/^\/home\/ubuntu\/gmm-deploy-backups\/game-progress-reset-[^/]+\/before.db$/.test(backup)||!lstatSync(backup).isFile()||backup===approvedDb)throw Error('BACKUP_PATH_INVALID');
 const before=await connect(backup),db=new PrismaClient({datasourceUrl:'file:'+approvedDb+(mode==='verify'?'?mode=ro':'')});
 try{
  const check=await before.$queryRawUnsafe<any[]>('PRAGMA integrity_check');if(check.length!==1||Object.values(check[0])[0]!=='ok')throw Error('BACKUP_INVALID');
  if(mode==='verify')return await verifyReset(db,before);
  const old=await plan(before),current=await plan(db);if(old.fingerprint!==expected||current.fingerprint!==expected)throw Error('BACKUP_PLAN_CHANGED');
  if(initial().credits!==200)throw Error('WELCOME_CREDITS_CHANGED');
  stopped();const applied=await applyTransaction(db,expected,'approved-production');
  return {...applied,...await verifyReset(db,before)};
 }finally{await before.$disconnect();await db.$disconnect();}
}
// Importing this module in tests never executes an operation.
if(process.argv[1]&&resolve(process.argv[1]).endsWith('/scripts/manual/game-progress-reset/production.ts')){
 runProduction(process.argv[2]??'plan',process.argv[3]??'',process.argv[4]??'',process.argv[5]??'').then(r=>console.log(JSON.stringify(r,null,2))).catch(e=>{console.error(e.message);process.exitCode=1;});
}
