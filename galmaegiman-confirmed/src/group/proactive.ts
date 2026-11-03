// 선톡(덱 42): 카카오 심사를 받은 뒤에만, 월 최대 2회, 1~2줄, 이미지 없이, 광고·기능과 무관한 홍보 금지.
// 기본은 꺼짐(PROACTIVE_MSG_ENABLED=true일 때만). 이 모듈은 '보내도 되는지'만 판단하고 횟수를 예약해요.
// 발송 대상 채팅방은 카카오에 추출을 요청해 받는 것이 원칙이라, 우리가 방 목록을 만들어 보내는 코드는 두지 않아요.
// TODO(verify): Event API 호출 형식과 '월 2회'의 기준(봇 전체인지 방마다인지)은 카카오 확인 후 발송 코드를 추가
// 사용자가 받고 싶다고 설정하는 메시지는 선톡이 아니라 '알림' 기능으로 따로 만들어요. TODO(verify): 알림 개발 가이드
import type { BotEnv, GroupBotConfig } from './config.js';
export class ProactiveError extends Error {constructor(message:string){super(message);this.name='ProactiveError';}}
// 횟수 저장소. reserve는 한 번에(원자적으로) 세고 기록해야 해요: 동시에 두 번 불려도 한도를 넘으면 안 됨
export interface ProactiveStore {reserve(env:BotEnv,month:string,max:number,summary:string,at:Date):Promise<boolean>}
// 한국 시간 기준 달(yyyy-MM)
export function kstMonth(at:Date){return new Date(at.getTime()+9*3600_000).toISOString().slice(0,7);}
export const proactiveEnabled=(value=process.env.PROACTIVE_MSG_ENABLED)=>value==='true';
export class ProactiveGate {
  constructor(private readonly deps:{enabled:boolean;env:BotEnv;config:GroupBotConfig;store:ProactiveStore;now?:()=>Date}){}
  async approve(message:string,options:{image?:boolean}={}){
    const {enabled,env,config,store}=this.deps;
    if(!enabled)throw new ProactiveError('선톡이 꺼져 있어요(PROACTIVE_MSG_ENABLED)');
    const body=message.trim();
    if(!body)throw new ProactiveError('선톡 내용이 비었어요');
    if(body.split('\n').length>config.proactive.maxLines)throw new ProactiveError(`선톡은 ${config.proactive.maxLines}줄까지`);
    if(options.image)throw new ProactiveError('선톡에는 이미지를 쓸 수 없어요');
    const at=(this.deps.now??(()=>new Date()))(),month=kstMonth(at);
    // 기록에는 첫 줄 앞부분만(운영자가 어떤 선톡인지 알아볼 정도)
    if(!await store.reserve(env,month,config.proactive.monthlyMax,body.split('\n')[0].slice(0,40),at))throw new ProactiveError(`이번 달 선톡 ${config.proactive.monthlyMax}회를 모두 썼어요`);
    return {month};
  }
}
// 시험·임시용 메모리 저장소(서버를 다시 켜면 사라지므로 운영에는 DB 저장소를 써요)
export class MemoryProactiveStore implements ProactiveStore {
  readonly sent:{env:BotEnv;month:string;summary:string;at:Date}[]=[];
  async reserve(env:BotEnv,month:string,max:number,summary:string,at:Date){
    if(this.sent.filter(s=>s.env===env&&s.month===month).length>=max)return false;
    this.sent.push({env,month,summary,at});return true;
  }
}
