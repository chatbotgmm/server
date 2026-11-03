// 멤버 멘션 나누기(덱 32·33): 말풍선 1개당 최대 15명, 말풍선 3개를 이어 보내면 최대 45명. 방을 나간 멤버는 멘션되지 않아요.
// TODO(verify): 멘션을 응답 JSON에 넣는 형식(필드 이름·위치)은 그룹챗봇 개발 가이드로 확인 후 이 묶음을 말풍선으로 바꾸는 코드를 추가
export interface MentionLimits {perBubble:number;maxBubbles:number}
export function buildMentions<T>(members:readonly T[],limits:MentionLimits,warn:(info:{requested:number;kept:number})=>void=()=>{},key:(m:T)=>string=m=>String(m)):T[][]{
  const seen=new Set<string>(),unique:T[]=[];
  for(const m of members){const k=key(m);if(!seen.has(k)){seen.add(k);unique.push(m);}}
  const max=limits.perBubble*limits.maxBubbles,kept=unique.slice(0,max);
  if(unique.length>max)warn({requested:unique.length,kept:kept.length});
  const out:T[][]=[];
  for(let i=0;i<kept.length;i+=limits.perBubble)out.push(kept.slice(i,i+limits.perBubble));
  return out;
}
