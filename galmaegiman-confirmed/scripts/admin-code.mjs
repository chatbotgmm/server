// 관리자 코드(.env ADMIN_CODE)를 확인하거나, 없으면 새로 만듭니다. 코드를 아는 사람은 누구나 관리자가 될 수 있으니 공유하지 마세요.
import {readFileSync,writeFileSync,chmodSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
let env=readFileSync('.env','utf8');
let code=env.match(/^ADMIN_CODE=(.+)$/m)?.[1]?.trim();
if(!code){
  code=randomBytes(16).toString('hex');
  env=/^ADMIN_CODE=.*$/m.test(env)?env.replace(/^ADMIN_CODE=.*$/m,`ADMIN_CODE=${code}`):env.replace(/\n?$/,'\n')+`ADMIN_CODE=${code}\n`;
  writeFileSync('.env',env,{mode:0o600});chmodSync('.env',0o600);
  console.log('새 관리자 코드를 .env에 저장했습니다. 서버를 다시 시작해야 적용됩니다.');
}
console.log(`카카오 채팅에 입력: 관리자 ${code}\n끄기: 관리자 해제\n이 코드는 비밀번호처럼 다루고 공유하지 마세요.`);
