// 전투 그림 자동 생성: 카드 그림을 첨부해 OpenAI 이미지 API(편집)로 '앞모습 + 뒷모습' 두 칸 시트를 만듦.
// 결과(원본 시트)는 art/raw/<ID>_s0.png 에 저장 → python3 -I scripts/process-sprites.py 로 배경 제거·자르기
//
// 사용:
//   OPENAI_API_KEY=... node scripts/gen-sprites.mjs --ids D1            (먼저 1장 시험)
//   OPENAI_API_KEY=... node scripts/gen-sprites.mjs --all               (최상위 33종, 이미 있는 건 건너뜀)
//   node scripts/gen-sprites.mjs --ids D1 --dry                         (요청문만 보기, 비용 없음)
// 옵션: --quality low|medium|high (기본 medium), --model <모델 이름> (기본: 환경변수 OPENAI_IMAGE_MODEL 또는 gpt-image-2), --force(다시 만들기)
// 키는 절대 저장소에 넣지 말고 환경변수로만. 요청마다 API가 알려 주는 사용량(토큰)을 art/raw/usage.log 에 남김
import { existsSync, mkdirSync, readFileSync, writeFileSync, appendFileSync } from 'node:fs';

const args=process.argv.slice(2);
const opt=(k,d)=>{const i=args.indexOf(`--${k}`);return i>=0?(args[i+1]&&!args[i+1].startsWith('--')?args[i+1]:true):d;};
const units=JSON.parse(readFileSync('data/units.json','utf8')).units;
const top=units.filter(u=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(u.tier));
const ids=opt('all',false)?top.map(u=>u.id):String(opt('ids','')).split(',').filter(Boolean);
if(!ids.length){console.error('--ids D1,D2 또는 --all 을 주세요');process.exit(1);}
const model=String(opt('model',process.env.OPENAI_IMAGE_MODEL||'gpt-image-2'));
const quality=String(opt('quality','medium'));
const dry=!!opt('dry',false),force=!!opt('force',false);

// 요청문: docs/BATTLE-SPRITE-PROMPTS.md 의 ① 공통 요청문과 같음(배경은 자르기 쉽게 자홍색 단색)
const prompt=(name)=>`첨부한 카드 속 갈매미 캐릭터를 그대로 그려 줘. 그림체·색·비율·외곽선·장식을 하나도 바꾸지 마.
카드 테두리·이름판·글자·배경은 빼고, 캐릭터만. 배경은 완전 단색 자홍색(#FF00FF) 한 가지 색으로만, 그림자·바닥도 그리지 마.
한 장에 가로로 두 칸:
- 왼쪽 칸: 앞모습. 보는 사람 쪽을 보면서 얼굴과 몸을 보는 사람 기준 화면 왼쪽으로 살짝 튼(부리가 그림의 왼쪽을 향함) 자세(대결 상대 쪽 모습). 당당한 대기 자세.
- 오른쪽 칸: 뒷모습. 뒤에서 본 모습으로, 몸을 보는 사람 기준 화면 오른쪽 위로 살짝 튼(머리가 그림의 오른쪽 위를 향함) 자세(내가 지휘하는 쪽 모습). 등의 날개·볏·장식이 뒤에서 보면 어떻게 생겼는지 자연스럽게.
두 칸 사이는 넓게 띄우고, 두 칸의 캐릭터 크기·높이를 똑같이, 발바닥이 같은 높이에. 전신이 잘리지 않게.
두 칸은 같은 캐릭터여야 해(볏 모양, 날개 금, 색, 소품이 똑같이).
Avoid: card frame, name plate, text, letters, watermark, background scenery, floor shadow, extra characters, eagle, hawk, hooked beak
캐릭터: ${name}`;

mkdirSync('art/raw',{recursive:true});
const key=process.env.OPENAI_API_KEY;
if(!dry&&!key){console.error('OPENAI_API_KEY 환경변수가 없어요. (키를 파일이나 채팅에 쓰지 마세요)');process.exit(1);}

for(const id of ids){
  const u=units.find(x=>x.id===id);if(!u){console.error(`없는 유닛 ${id}`);continue;}
  const out=`art/raw/${id}_s0.png`;
  if(existsSync(out)&&!force){console.log(`${id} 건너뜀(이미 있음, 다시 만들려면 --force)`);continue;}
  const text=prompt(u.name);
  if(dry){console.log(`--- ${id} ${u.name} (첨부: public/images/${id}.png, 모델 ${model}, 품질 ${quality}) ---\n${text}\n`);continue;}
  const form=new FormData();
  form.append('model',model);form.append('prompt',text);form.append('size','1536x1024');form.append('quality',quality);form.append('n','1');
  form.append('image[]',new Blob([readFileSync(`public/images/${id}.png`)],{type:'image/png'}),`${id}.png`);
  let res,body;
  for(let attempt=1;attempt<=3;attempt++){
    res=await fetch('https://api.openai.com/v1/images/edits',{method:'POST',headers:{Authorization:`Bearer ${key}`},body:form});
    body=await res.json().catch(()=>({}));
    if(res.ok||![429,500,502,503].includes(res.status))break;
    console.log(`${id} 재시도 ${attempt} (${res.status})`);await new Promise(r=>setTimeout(r,attempt*5000));
  }
  if(!res.ok){console.error(`${id} 실패 ${res.status}: ${body?.error?.message??JSON.stringify(body).slice(0,200)}`);continue;}
  const b64=body.data?.[0]?.b64_json;if(!b64){console.error(`${id} 그림이 응답에 없어요`);continue;}
  writeFileSync(out,Buffer.from(b64,'base64'));
  appendFileSync('art/raw/usage.log',`${new Date().toISOString()} ${id} ${model} ${quality} ${JSON.stringify(body.usage??{})}\n`);
  console.log(`${id} ${u.name} 완료 → ${out} 사용량 ${JSON.stringify(body.usage??'(알 수 없음)')}`);
}
