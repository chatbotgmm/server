// 새 판 설치 후 실행하는 선택 테스트. 로컬 전용 테스트 계정만 사용합니다.
// 사용자 갤탭에서 실행하면 테스트 계정 1개의 상태가 생깁니다.
import assert from 'node:assert/strict';
process.loadEnvFile('.env');
const origin=`http://127.0.0.1:${process.env.PORT??3000}`;
assert.equal((await (await fetch(`${origin}/health`)).json()).edition,'RANDOM_75');
assert.equal((await (await fetch(`${origin}/ready`)).json()).ready,true);
const image=await fetch(`${origin}/images/C001M.png`);
assert.equal(image.status,200);assert.match(image.headers.get('content-type'),/image\/png/);
const request=async utterance=>{
  const res=await fetch(`${origin}/kakao/skill`,{method:'POST',headers:{'Content-Type':'application/json','X-Skill-Secret':process.env.SKILL_SECRET},body:JSON.stringify({bot:{id:process.env.KAKAO_BOT_ID||'LOCAL_SMOKE_BOT'},userRequest:{utterance,user:{id:'LOCAL_SMOKE_USER',type:'localDiagnostic',properties:{isFriend:true}}}})});
  assert.equal(res.status,200);return res.json();
};
const result=await request('뽑기');
const text=result.template.outputs.map(x=>x.simpleText?.text??'').join('\n');
assert.ok(text.includes('등장!')||text.includes('획득권이 없습니다'));
const info=await request('확률');
assert.match(info.template.outputs.map(x=>x.simpleText?.text??'').join(''), /흔함 40%.*안흔함 30%.*특별 20%.*희귀 10%/);
console.log('로컬 HTTP /health /ready /images /kakao/skill 및 랜덤 뽑기·확률 안내 확인 완료.');
