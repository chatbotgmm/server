import {test} from 'node:test';
import assert from 'node:assert/strict';
import {MenuAccess,menuFeatures} from '../src/menu-access.ts';

const noDb=new Proxy({},{get(){throw Error('MENU_STATE_MUST_NOT_QUERY_DB');}});
const noCatalog=()=>{throw Error('MENU_STATE_MUST_NOT_READ_CATALOG');};
function assertOpen(state){
 assert.deepEqual(Object.keys(state.features),menuFeatures);
 assert.equal(state.enforcementEnabled,false);
 assert.equal(state.testDefaultLock,false);
 for(const feature of Object.values(state.features)){
  assert.equal(feature.enabled,true);assert.equal(feature.mode,'auto');
  assert.equal(feature.requiredLevel,null);assert.equal(feature.reason,'');
 }
}
async function withMenuEnv(env,run){
 const keys=['GMM_TEST_MENU_DEFAULT','ROSTER60_PROFILE'];
 const before=Object.fromEntries(keys.map(key=>[key,process.env[key]]));
 try{
  for(const key of keys)if(env[key]===undefined)delete process.env[key];else process.env[key]=env[key];
  return await run();
 }finally{
  for(const key of keys)if(before[key]===undefined)delete process.env[key];else process.env[key]=before[key];
 }
}

test('retired default menu setting cannot close any entry',async()=>{
 for(const env of [{},{ROSTER60_PROFILE:'five-stat-device-test-v1'},{GMM_TEST_MENU_DEFAULT:'locked'}]){
  await withMenuEnv(env,async()=>assertOpen(await new MenuAccess(noDb,noCatalog,true).read('existing-account')));
 }
});
test('menu compatibility is independent of legacy open switches and invalid menu/profile settings',async()=>{
 // Actual roster profile and action validation remain the routes' responsibility.
 for(const env of [{GMM_TEST_MENU_DEFAULT:'open'},
  {GMM_TEST_MENU_DEFAULT:'open',ROSTER60_PROFILE:'production'},
  {GMM_TEST_MENU_DEFAULT:'typo',ROSTER60_PROFILE:'unknown-old-profile'}]){
  await withMenuEnv(env,async()=>assertOpen(await new MenuAccess(noDb,noCatalog,true).read('existing-account')));
 }
});
test('stored force_lock configuration is ignored and preserved without a menu settings query',async()=>{
 const saved={revision:12,overrides:Object.fromEntries(menuFeatures.map(feature=>[feature,'force_lock']))};
 const snapshot=JSON.stringify(saved);let reads=0;
 const db={appMeta:{findUnique:async()=>{reads++;return {value:JSON.stringify(saved)};}}};
 assertOpen(await new MenuAccess(db,noCatalog,true).read('legacy-locked-account'));
 assert.equal(reads,0);assert.equal(JSON.stringify(saved),snapshot);
});
test('new accounts receive all enabled compatibility menus without a menu state DB query',async()=>{
 // HTTP authentication is covered separately; this class never queries account/menu state.
 assertOpen(await new MenuAccess(noDb,noCatalog).read('never-created-menu-state'));
 const state=await new MenuAccess(noDb,noCatalog).read('another-new-menu-state');
 assert.equal(state.revision,0);assert.equal(state.representativeLevel,null);
 assert.equal(state.levelPolicy,'NONE');assert.equal(state.levelDecreasePolicy,'NONE');
});
