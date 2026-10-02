// 새 판 설치 후 실행하는 선택 테스트. 로컬 전용 테스트 계정만 사용합니다.
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
process.loadEnvFile('.env');
const origin=`http://127.0.0.1:${process.env.PORT??3000}`;
assert.equal((await (await fetch(`${origin}/health`)).json()).edition,'GALMAEMI_118');
assert.equal((await (await fetch(`${origin}/ready`)).json()).ready,true);
if(existsSync('public/images/C1.png')){
  const image=await fetch(`${origin}/images/C1.png`);
  assert.equal(image.status,200);assert.match(image.headers.get('content-type'),/image\/png/);
}
const request=async utterance=>{
  const res=await fetch(`${origin}/kakao/skill`,{method:'POST',headers:{'Content-Type':'application/json','X-Skill-Secret':process.env.SKILL_SECRET},body:JSON.stringify({bot:{id:process.env.KAKAO_BOT_ID||'LOCAL_SMOKE_BOT'},userRequest:{utterance,user:{id:'LOCAL_SMOKE_USER',type:'localDiagnostic',properties:{isFriend:true}}}})});
  assert.equal(res.status,200);return res.json();
};
const textOf=r=>r.template.outputs.map(x=>x.simpleText?.text??'').join('\n');
const result=await request('하급뽑기');
assert.ok(textOf(result).includes('🎟')||textOf(result).includes('부족'));
assert.match(textOf(await request('확률')),/하급 뽑기 \(2장\): 흔함 70%/);
console.log('로컬 HTTP /health /ready /kakao/skill 및 하급 뽑기·확률 안내 확인 완료.');
