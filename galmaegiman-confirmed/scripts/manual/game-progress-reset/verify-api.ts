/** Local injection only, using a read-only DB. Never prints a credential or a player's details. */
import assert from 'node:assert/strict';import {PrismaClient} from '@prisma/client';import {createHmac} from 'node:crypto';
import {buildApp} from '../../../src/app.js';import {GameService} from '../../../src/game.js';import {loadContent} from '../../../src/content.js';
import {resetSessionCutoff,gameResetMarkerKey,ordinarySessionLifetimeSeconds as ttl} from '../../../src/game-reset-session.js';
import {ReviewAuth,reviewConfig} from '../../../src/review-auth.js';
const db=new PrismaClient({datasourceUrl:'file:/home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db?mode=ro'});
const secret=process.env.SKILL_SECRET;if(!secret)throw Error('SIGNING_CONFIG_MISSING');
const cutoff=resetSessionCutoff((await db.appMeta.findUnique({where:{key:gameResetMarkerKey}}))?.value);assert(cutoff>0);
const config=reviewConfig(process.env);if(process.env.REVIEW_LOGIN_ENABLED==='true')assert(config,'REVIEW_CONFIG_INVALID');
const app=await buildApp(db,new GameService(db,loadContent()),{secret,baseUrl:async()=>'',gameResetSessionCutoff:cutoff,reviewLogin:config});
try{
 const accounts=await db.player.findMany({select:{id:true,identity:true}});let checked=0;
 const key=createHmac('sha256',secret).update('web-session').digest();
 for(const p of accounts.filter(p=>!p.identity.startsWith('review:'))){
  const issued=Math.max(Math.floor(Date.now()/1000),cutoff),v=p.id+'.'+(issued+ttl),token=v+'.'+createHmac('sha256',key).update(v).digest('base64url');
  for(const path of ['state','recruit/session']){
   const r=await app.inject({url:'/my/api/native/roster60/'+path,headers:{'x-gmm':'1','x-gmm-s':token}});assert.equal(r.statusCode,200,'READ_API_FAILED');const body=r.json();
   if(path==='state'){assert.equal(body.flow,'roster60-v1');assert.equal(body.starter.speciesId,'u01');assert.equal(body.tutorialComplete,false);}
   else{assert.equal(body.enabled,true);assert.deepEqual(body.offers.map((o:any)=>[o.cost,o.rerollCost]),[[2,1],[6,3],[10,5]]);}
  }
  const old=p.id+'.'+(cutoff-1+ttl),oldToken=old+'.'+createHmac('sha256',key).update(old).digest('base64url');
  assert.equal((await app.inject({url:'/my/api/native/roster60/state',headers:{'x-gmm':'1','x-gmm-s':oldToken}})).statusCode,401);checked++;
 }
 if(config){const review=new ReviewAuth(db,config);assert.equal(await review.account(),config.playerId,'REVIEW_ACCOUNT_INVALID');review.close();}
 console.log(JSON.stringify({readOnlyAuthenticatedApiAccounts:checked,reviewIdentityVerified:!!config,gameplayRequests:0}));
}finally{await app.close();await db.$disconnect();}
