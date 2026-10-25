import {PrismaClient} from '@prisma/client';
import {spawnSync} from 'node:child_process';
import {existsSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {isolatedUrl,plan,applyIsolated} from './reset.js';
async function main(){
 const [url,mode,expected,confirm]=process.argv.slice(2);
 const path=isolatedUrl(url??'');
 if(mode&&mode!=='--apply-isolated')throw Error('UNKNOWN_MODE');
 const db=new PrismaClient({datasourceUrl:url});
 try{
  if(!mode){console.log(JSON.stringify(await plan(db),null,2));return;}
  if(!expected||confirm!=='APPLY TO ISOLATED COPY ONLY')throw Error('CONFIRMATION_REQUIRED');
  const backup=path+'.before-reset';if(existsSync(backup))throw Error('BACKUP_ALREADY_EXISTS');
  const r=spawnSync('python3',['-c',`import sqlite3,sys,os
fd=os.open(sys.argv[2],os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600);os.close(fd)
a=sqlite3.connect(sys.argv[1]+'?mode=ro',uri=True);b=sqlite3.connect(sys.argv[2]);a.backup(b);a.close()
assert b.execute('PRAGMA integrity_check').fetchall()==[('ok',)]
b.close()`,pathToFileURL(path).href,backup],{stdio:'inherit'});
  if(r.status!==0)throw Error('BACKUP_FAILED');
  console.log(JSON.stringify(await applyIsolated(db,url,expected,confirm)));
 }finally{await db.$disconnect();}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
