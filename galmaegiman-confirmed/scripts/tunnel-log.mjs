import { createInterface } from 'node:readline';
import { mkdirSync,writeFileSync,renameSync } from 'node:fs';
const lines=createInterface({input:process.stdin});
for await(const line of lines){
  console.log(line);
  const url=line.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com\b/)?.[0];
  if(url){
    mkdirSync('config',{recursive:true});
    writeFileSync('config/public-url.next',url+'\n',{mode:0o600});
    renameSync('config/public-url.next','config/public-url.txt');
    console.log(`\n카카오 URL 및 Test URL: ${url}/kakao/skill\n이미지 주소도 자동으로 갱신했습니다. 이 세션을 켜 두세요.\n`);
  }
}
