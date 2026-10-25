/** Existing ordinary sessions encode expiry, with the fixed 30-day lifetime in my.ts. */
export const ordinarySessionLifetimeSeconds=30*86400;
export const gameResetMarkerKey='game-progress-reset-v1';
export function resetSessionCutoff(value:string|null|undefined):number{
 if(value==null)return 0;
 let m:any;try{m=JSON.parse(value);}catch{throw Error('GAME_RESET_MARKER_INVALID');}
 if(!m||typeof m.epoch!=='string'||!m.epoch||!Number.isSafeInteger(m.at)||m.at<0)throw Error('GAME_RESET_MARKER_INVALID');
 // Reject the whole reset second: issuance is only recorded to second precision.
 return Math.floor(m.at/1000)+1;
}
// Call only AFTER verifying the session signature. Not an authentication substitute.
export function sessionPredatesReset(raw:string,cutoff:number):boolean{
 if(!Number.isSafeInteger(cutoff)||cutoff<0)throw Error('GAME_RESET_CUTOFF_INVALID');
 if(!cutoff)return false;
 const parts=raw.split('.'),expiry=Number(parts[1]);
 return parts.length!==3||!Number.isSafeInteger(expiry)||expiry-ordinarySessionLifetimeSeconds<cutoff;
}
