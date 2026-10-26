import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';

export const features=['recruit','roster','craft','adventure','shop','party','daily','rebirth'];
const modes=['auto','force_lock','force_unlock'];
export function validateState(s){
 if(s?.version!=='menu-access-v1'||!Number.isSafeInteger(s.revision)||s.revision<0||Object.keys(s.features??{}).length!==features.length)throw Error('INVALID_STATE');
 for(const f of features)if(!modes.includes(s.features[f]?.mode)||typeof s.features[f]?.enabled!=='boolean')throw Error('INVALID_STATE');
 return s;
}
export function validateIds(ids){
 if(!Array.isArray(ids)||!ids.length||ids.some(id=>typeof id!=='string'||!id.trim()||id.length>100)||new Set(ids).size!==ids.length)throw Error('INVALID_ACCOUNT_LIST');
 return ids;
}
export function client(origin,session,fetcher=fetch){
 const u=new URL(origin);if(u.protocol!=='https:'||u.username||u.password||u.pathname!=='/'||u.search||u.hash)throw Error('HTTPS_ORIGIN_REQUIRED');
 if(!session||session.length>=200||/[\r\n]/.test(session))throw Error('ADMIN_SESSION_REQUIRED');
 return async(path,body)=>{
  let r;try{r=await fetcher(new URL('/my/api/admin/menu-access'+path,u),{method:body?'POST':'GET',headers:{'x-gmm':'1','x-gmm-s':session,...(body?{'content-type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,redirect:'error',signal:AbortSignal.timeout(15000)});}catch{throw Error('NETWORK_OUTCOME_UNKNOWN_STOP_AND_REVIEW');}
  if(!r.ok)throw Error('HTTP_'+r.status+'_STOP_AND_REVIEW');
  try{return await r.json();}catch{throw Error('INVALID_RESPONSE_STOP_AND_REVIEW');}
 };
}
export async function plan(api,ids){
 validateIds(ids);const accounts=[];
 for(const id of ids){const s=validateState(await api('?playerId='+encodeURIComponent(id)));accounts.push({id,revision:s.revision,before:Object.fromEntries(features.map(f=>[f,s.features[f].mode])),requests:Object.fromEntries(features.map(f=>[f,randomUUID()]))});}
 return {version:1,accounts};
}
export async function apply(api,p){
 if(p?.version!==1)throw Error('INVALID_PLAN');validateIds(p.accounts?.map(a=>a.id));
 // Check the complete reviewed set before the first mutation. Never overwrite a stale plan.
 for(const a of p.accounts){
  if(!Number.isSafeInteger(a.revision)||a.revision<0||features.some(f=>!modes.includes(a.before?.[f])||typeof a.requests?.[f]!=='string'||!a.requests[f]||a.requests[f].length>100))throw Error('INVALID_PLAN');
  const s=validateState(await api('?playerId='+encodeURIComponent(a.id)));
  if(s.revision!==a.revision||features.some(f=>s.features[f].mode!==a.before[f]))throw Error('STALE_PLAN_STOP_AND_REVIEW');
 }
 let changed=0;
 for(const a of p.accounts){let revision=a.revision;
  for(const featureId of features){if(a.before[featureId]==='force_unlock')continue;
   const result=await api('',{requestId:a.requests[featureId],targetPlayerId:a.id,featureId,mode:'force_unlock',expectedRevision:revision});
   if(result?.revision!==revision+1||result?.targetPlayerId!==a.id||result?.featureId!==featureId||result?.mode!=='force_unlock')throw Error('INVALID_RECEIPT_STOP_AND_REVIEW');
   revision++;changed++;
  }
 }
 // A final pass detects changes to earlier accounts while later accounts were being processed.
 for(const a of p.accounts){const s=validateState(await api('?playerId='+encodeURIComponent(a.id)));if(features.some(f=>s.features[f].mode!=='force_unlock'||!s.features[f].enabled))throw Error('VERIFY_FAILED_STOP_AND_REVIEW');}
 return {accounts:p.accounts.length,changed,verified:true};
}
async function main(){
 const [action,file,output,...extra]=process.argv.slice(2);
 if(extra.length||!file||!['plan','apply'].includes(action)||(action==='plan'?!output:output!=='--confirm-all-listed-test-accounts'))throw Error('USAGE: plan accounts.json plan.json | apply plan.json --confirm-all-listed-test-accounts');
 const api=client(process.env.GMM_ORIGIN,process.env.GMM_ADMIN_SESSION);
 if(action==='plan'){
  const p=await plan(api,JSON.parse(readFileSync(file,'utf8')));p.origin=new URL(process.env.GMM_ORIGIN).origin;
  writeFileSync(output,JSON.stringify(p,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log(JSON.stringify({accounts:p.accounts.length,plannedChanges:p.accounts.reduce((n,a)=>n+features.filter(f=>a.before[f]!=='force_unlock').length,0),applied:false}));
 }else {const p=JSON.parse(readFileSync(file,'utf8'));if(p.origin!==new URL(process.env.GMM_ORIGIN).origin)throw Error('INVALID_PLAN_ORIGIN');console.log(JSON.stringify(await apply(api,p)));}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)main().catch(e=>{console.error(e.message?.match(/^(INVALID_|HTTP_|NETWORK_|HTTPS_|ADMIN_|STALE_|VERIFY_|USAGE:)/)?e.message:'FAILED_STOP_AND_REVIEW');process.exitCode=1;});
