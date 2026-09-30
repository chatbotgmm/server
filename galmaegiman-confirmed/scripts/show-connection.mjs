import {readFileSync} from 'node:fs';
const env=readFileSync('.env','utf8');
const key=env.match(/^SKILL_SECRET=(.+)$/m)?.[1]?.replace(/^"|"$/g,'');
if(!key)throw new Error('먼저 설치를 완료하세요.');
let base='(먼저 npm run tunnel 실행)';
try{base=readFileSync('config/public-url.txt','utf8').trim();}catch{}
console.log(`URL / Test URL: ${base}/kakao/skill\n헤더 Key: X-Skill-Secret\n헤더 Value: ${key}\nURL 헤더와 테스트 헤더 양쪽에 같은 값을 넣으세요.\n이 화면은 비밀키가 있으니 공유하지 마세요.`);
