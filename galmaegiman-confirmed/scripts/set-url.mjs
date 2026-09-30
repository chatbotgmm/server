import { mkdirSync,writeFileSync,renameSync } from 'node:fs';
const raw=process.argv[2];
if(!raw){console.error('사용법: npm run url -- https://발급주소.trycloudflare.com');process.exit(1);}
const u=new URL(raw);
if(u.protocol!=='https:'||u.username||u.password||u.pathname!=='/'||u.search||u.hash)throw new Error('HTTPS 도메인만 입력하세요. /kakao/skill을 붙이지 마세요.');
mkdirSync('config',{recursive:true});
writeFileSync('config/public-url.next',u.origin+'\n',{mode:0o600});
renameSync('config/public-url.next','config/public-url.txt');
console.log(`이미지 주소 설정 완료. 카카오 스킬 URL: ${u.origin}/kakao/skill`);
