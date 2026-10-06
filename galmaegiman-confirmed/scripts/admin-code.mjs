// 관리자 코드(.env ADMIN_CODE)를 확인하거나, 없으면 새로 만듭니다. 코드를 아는 사람은 누구나 관리자가 될 수 있으니 공유하지 마세요.
// 새 코드로 바꾸기: npm run admin-code -- --new   (바꾼 뒤 서버를 다시 시작해야 적용)
import {readFileSync,writeFileSync,chmodSync,existsSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
// 어느 폴더에서 실행해도 프로젝트의 .env를 씁니다
const file=resolve(dirname(fileURLToPath(import.meta.url)),'..','.env');
if(!existsSync(file)){console.error(`.env가 없습니다: ${file}\n먼저 bash scripts/setup.sh 로 설치하세요.`);process.exit(1);}
let env=readFileSync(file,'utf8');
let code=env.match(/^ADMIN_CODE=(.+)$/m)?.[1]?.trim();
const renew=process.argv.includes('--new');
if(!code||renew){
  code=randomBytes(16).toString('hex');
  env=/^ADMIN_CODE=.*$/m.test(env)?env.replace(/^ADMIN_CODE=.*$/m,`ADMIN_CODE=${code}`):env.replace(/\n?$/,'\n')+`ADMIN_CODE=${code}\n`;
  writeFileSync(file,env,{mode:0o600});chmodSync(file,0o600);
  console.log(`${renew?'관리자 코드를 새로 바꿔':'새 관리자 코드를'} .env에 저장했습니다. 적용하려면: sudo systemctl restart gmm-server`);
}
console.log(`카카오 채팅에 입력: 관리자 ${code}\n켜기·끄기: 관리자 켜기 / 관리자 끄기 · 등록 해제: 관리자 해제\n이 코드는 비밀번호처럼 다루고 공유하지 마세요.`);
