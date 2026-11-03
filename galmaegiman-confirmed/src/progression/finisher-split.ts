// 경험치 분배 규칙: 이긴 전투에서 마무리한 유닛(이긴 순간 나와 있던 내 유닛)은 보상 W 전액,
// 편성된 나머지 유닛(출전하지 않은 예비 포함)은 각각 W의 절반(내림)을 받는다. 진 전투는 모두 0이다.
// 금액 W와 승패 판정은 각 전투 모드가 정하고, 이 함수는 나누기만 한다. 클라이언트 입력을 받지 않는다.
import {RosterError} from '../roster60-onboarding.js';

export const FINISHER_SPLIT_DISTRIBUTION='finisher-full-rest-half' as const;
export const ADVENTURE_FINISHER_SPLIT_POLICY='adventure-finisher-100-rest-50-v1' as const;

const fail=(code:string):never=>{throw new RosterError(409,code);};
const safeXp=(n:unknown):n is number=>typeof n==='number'&&Number.isSafeInteger(n)&&n>=0&&n<=2_000_000_000;

/** 편성 순서와 무관하게 같은 결과를 낸다. 마무리 유닛은 반드시 편성 안에 있어야 한다. */
export function finisherSplitShares(winXp:number,memberIds:readonly string[],finisherId:string):Map<string,number>{
 if(!safeXp(winXp)||!memberIds.length||new Set(memberIds).size!==memberIds.length||memberIds.some(id=>typeof id!=='string'||!id))return fail('INVALID_PARTICIPATION');
 if(typeof finisherId!=='string'||!memberIds.includes(finisherId))return fail('FINISHER_NOT_IN_PARTY');
 const half=Math.floor(winXp/2);
 return new Map(memberIds.map(id=>[id,id===finisherId?winXp:half]));
}
