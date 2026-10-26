import {test} from 'node:test';
import assert from 'node:assert/strict';
import {testMenuDefaultLock,menuDecision} from '../src/menu-policy.ts';
test('default remains locked without explicit opening, including test profile',()=>{
 for(const env of [{},{ROSTER60_PROFILE:'five-stat-device-test-v1'},{GMM_TEST_MENU_DEFAULT:'locked'}])assert.equal(testMenuDefaultLock(env),true);
});
test('opening requires both explicit switch and approved test profile',()=>{
 assert.equal(testMenuDefaultLock({GMM_TEST_MENU_DEFAULT:'open',ROSTER60_PROFILE:'five-stat-device-test-v1'}),false);
 for(const env of [{GMM_TEST_MENU_DEFAULT:'open'},{GMM_TEST_MENU_DEFAULT:'open',ROSTER60_PROFILE:'production'},{GMM_TEST_MENU_DEFAULT:'typo'}])assert.throws(()=>testMenuDefaultLock(env));
});
test('all priority combinations preserve explicit locks and unlocks',()=>{
 for(const locked of [true,false]){
  assert.deepEqual(menuDecision('force_lock',locked),{enabled:false,reason:'ACCOUNT_LOCKED'});
  assert.deepEqual(menuDecision('force_unlock',locked),{enabled:true,reason:''});
  assert.deepEqual(menuDecision('auto',locked),{enabled:!locked,reason:locked?'TEST_DEFAULT_LOCK':''});
 }
});
test('new accounts using auto inherit the explicit open default',()=>{
 assert.deepEqual(menuDecision('auto',testMenuDefaultLock({GMM_TEST_MENU_DEFAULT:'open',ROSTER60_PROFILE:'five-stat-device-test-v1'})),{enabled:true,reason:''});
});
