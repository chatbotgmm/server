import {readFile} from 'node:fs/promises';
import {readRefillSettings,changeRefillAmount,refillAmountAt} from '../src/refill-settings.js';
import {nextSlot} from '../src/schedule.js';

try{
  const pkg=JSON.parse(await readFile('package.json','utf8'));
  if(pkg.name!=='galmaegiman-confirmed')throw new Error('cd ~/galmaegiman-confirmed 후 실행하세요.');
  const args=process.argv.slice(2),raw=args[0];
  if(args.length>1||(raw!==undefined&&!/^\d{1,4}$/.test(raw)))throw new Error('사용법: npm run refill -- 10 (0~1000의 정수) / 확인: npm run refill');
  const now=new Date();
  const settings=raw===undefined?await readRefillSettings():await changeRefillAmount(Number(raw),undefined,now);
  const amount=refillAmountAt(settings,now);
  console.log(`${raw===undefined?'현재 설정':'설정 완료'}: 한국 시간 09:00 / 12:00 / 18:00마다 +${amount}회`);
  console.log(`다음 충전: ${nextSlot(now)} (한국 시간)`);
  console.log('서버 재시작 없이 다음 충전 시점부터 적용합니다. 기존 보유 횟수는 유지됩니다.');
  console.log('변경 전에 지난 충전 시점은 이전 설정값으로 계산합니다. 첫 접속 지급은 3회입니다.');
}catch(error){
  console.error(`설정을 변경하지 못했습니다: ${error instanceof Error?error.message:String(error)}`);
  process.exitCode=1;
}
