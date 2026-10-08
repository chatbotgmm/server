// 수비대 대결 시험판(/duel) 주소를 알려 줍니다. 최상위 33종이 나와서 비밀 키가 붙은 주소로만 열려요(키는 SKILL_SECRET에서 계산).
// 사용(서버 어느 폴더에서든): sudo -iu ubuntu node gmm/galmaegiman-confirmed/scripts/duel-link.mjs
//   (프로젝트 폴더에서 ubuntu 사용자라면 npm run duel-link 도 됨. 주소를 공유하면 받은 사람도 열 수 있으니 조심)
import {readFileSync,existsSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const env=readFileSync(resolve(root,'.env'),'utf8');
const secret=env.match(/^SKILL_SECRET=(.+)$/m)?.[1]?.trim();
if(!secret){console.error('.env에 SKILL_SECRET이 없어요.');process.exit(1);}
const key=createHmac('sha256',secret).update('duel-preview').digest('base64url').slice(0,22);
const urlFile=resolve(root,'config/public-url.txt');
const base=(existsSync(urlFile)?readFileSync(urlFile,'utf8'):env.match(/^PUBLIC_BASE_URL=(.+)$/m)?.[1]??'').trim().replace(/\/$/,'')||'https://<서버 주소>';
console.log(`${base}/duel?k=${key}`);
