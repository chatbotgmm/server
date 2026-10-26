// 개별 관리자 잠금이 전역 테스트 기본값보다 우선한다.
export function testMenuDefaultLock(env:Record<string,string|undefined>):boolean {
 const value=env.GMM_TEST_MENU_DEFAULT;
 if(value===undefined||value==='locked')return true;
 if(value!=='open')throw Error('INVALID_TEST_MENU_DEFAULT');
 if(env.ROSTER60_PROFILE!=='five-stat-device-test-v1')throw Error('TEST_MENU_OPEN_REQUIRES_TEST_PROFILE');
 return false;
}
export function menuDecision(mode:'force_lock'|'force_unlock'|'auto',testDefaultLock:boolean){
 const enabled=mode==='force_unlock'||(mode==='auto'&&!testDefaultLock);
 return {enabled,reason:enabled?'':mode==='force_lock'?'ACCOUNT_LOCKED':'TEST_DEFAULT_LOCK'};
}
