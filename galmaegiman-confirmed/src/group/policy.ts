// ContentPolicy: 단체방 챗봇이 내보내는 모든 글이 거치는 금지 문구 검사(덱 40·43·47)
// 개발(dev)은 오류로 멈추고, 운영(prod)은 응답을 막고 기록만 남겨요. 기록에는 금지어 종류만 쓰고 본문은 쓰지 않아요.
import type { BanReason, BotEnv, GroupBotConfig } from './config.js';
export interface Violation {phrase:string;reason:BanReason}
export class ContentPolicyError extends Error {
  constructor(readonly violations:Violation[]){super(`금지 문구: ${violations.map(v=>`${v.phrase}(${v.reason})`).join(', ')}`);this.name='ContentPolicyError';}
}
// 띄어쓰기·대소문자·전각 차이를 없앤 뒤 비교해요(「채팅 열람」 = 「채팅열람」)
const normalize=(text:string)=>text.normalize('NFKC').toLowerCase().replace(/\s+/gu,'');
export class ContentPolicy {
  private readonly rules:{needle:string;phrase:string;reason:BanReason}[];
  constructor(config:Pick<GroupBotConfig,'banned'>,private readonly env:BotEnv,private readonly log:(info:{reasons:BanReason[]})=>void=()=>{}){
    this.rules=config.banned.map(b=>({needle:normalize(b.phrase),phrase:b.phrase,reason:b.reason}));
  }
  check(text:string):Violation[]{
    const flat=normalize(text);
    return this.rules.filter(r=>flat.includes(r.needle)).map(({phrase,reason})=>({phrase,reason}));
  }
  // 글 여러 개를 한꺼번에 검사. 위반이 있으면 dev는 예외, prod는 기록 후 false(호출한 쪽이 안전한 기본 응답으로 바꿈)
  allow(texts:string[]):boolean{
    const violations=texts.flatMap(t=>this.check(t));
    if(!violations.length)return true;
    if(this.env==='dev')throw new ContentPolicyError(violations);
    this.log({reasons:[...new Set(violations.map(v=>v.reason))]});
    return false;
  }
}
