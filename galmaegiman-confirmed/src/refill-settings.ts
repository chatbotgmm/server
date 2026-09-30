import {readFile,writeFile,mkdir,rename,unlink,open} from 'node:fs/promises';
import {dirname,resolve} from 'node:path';
import {randomUUID} from 'node:crypto';
import {z} from 'zod';
import {grantsSince} from './schedule.js';

const amountSchema=z.number().int().min(0).max(1000);
const schema=z.object({
  version:z.literal(1),
  initialAmount:amountSchema,
  changes:z.array(z.object({at:z.iso.datetime(),amount:amountSchema}).strict()).max(10000)
}).strict().superRefine((value,ctx)=>{
  for(let i=1;i<value.changes.length;i++){
    if(Date.parse(value.changes[i].at)<=Date.parse(value.changes[i-1].at))ctx.addIssue({code:'custom',message:'변경 시각은 증가해야 합니다.'});
  }
});
export type RefillSettings=z.infer<typeof schema>;
export const defaultRefillSettings=():RefillSettings=>({version:1,initialAmount:10,changes:[]});
export const refillSettingsPath=(root=process.cwd())=>resolve(root,'config/refill-settings.json');
export async function readRefillSettings(path=refillSettingsPath()):Promise<RefillSettings>{
  let raw:string;
  try{raw=await readFile(path,'utf8');}
  catch(error){if((error as NodeJS.ErrnoException).code==='ENOENT')return defaultRefillSettings();throw error;}
  if(Buffer.byteLength(raw)>1_000_000)throw new Error('충전 설정 파일이 너무 큽니다.');
  return schema.parse(JSON.parse(raw));
}
export function refillAmountAt(settings:RefillSettings,now:Date):number{
  let amount=settings.initialAmount;
  for(const change of settings.changes){if(Date.parse(change.at)>now.getTime())break;amount=change.amount;}
  return amount;
}
// Each slot uses the rate active at that slot, even if its owner comes back much later.
export function refillCreditsSince(settings:RefillSettings,last:Date,now:Date):number{
  if(now<=last)return 0;
  let cursor=last,amount=refillAmountAt(settings,last),credits=0;
  for(const change of settings.changes){
    const at=Date.parse(change.at);
    if(at<=last.getTime())continue;
    if(at>now.getTime())break;
    const before=new Date(at-1);
    credits+=grantsSince(cursor,before)*amount;
    cursor=before;amount=change.amount;
  }
  return credits+grantsSince(cursor,now)*amount;
}
export async function changeRefillAmount(amount:number,path=refillSettingsPath(),now=new Date()):Promise<RefillSettings>{
  amountSchema.parse(amount);
  await mkdir(dirname(path),{recursive:true,mode:0o700});
  const lockPath=`${path}.lock`;
  const lock=await open(lockPath,'wx',0o600).catch(error=>{
    if((error as NodeJS.ErrnoException).code==='EEXIST')throw new Error('다른 충전 설정 작업이 진행 중입니다. 잠시 뒤 다시 시도하세요.');
    throw error;
  });
  const temp=`${path}.${randomUUID()}.tmp`;
  try{
    const settings=await readRefillSettings(path);
    const previous=settings.changes.at(-1);
    if(previous&&Date.parse(previous.at)>=now.getTime())throw new Error('이전 변경 시각보다 늦어야 합니다. 기기 시간을 확인하거나 잠시 뒤 다시 시도하세요.');
    if(refillAmountAt(settings,now)===amount)return settings;
    const updated=schema.parse({...settings,changes:[...settings.changes,{at:now.toISOString(),amount}]});
    await writeFile(temp,JSON.stringify(updated,null,2)+'\n',{flag:'wx',mode:0o600});
    await rename(temp,path);
    return updated;
  }finally{
    try{await unlink(temp).catch(error=>{if((error as NodeJS.ErrnoException).code!=='ENOENT')throw error;});}
    finally{try{await lock.close();}finally{await unlink(lockPath);}}
  }
}
