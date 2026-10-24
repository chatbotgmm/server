/** Persisted server-owned escrow. No client reward payload or new drop table. */
export type AdventureRewards={xp:Record<string,number>;currencies?:Partial<Record<'credits'|'snack'|'peanut',number>>;items?:Record<string,number>};
export function checkedAdventureRewards(raw:unknown,itemIds:readonly string[]):AdventureRewards{
 const map=(v:unknown)=>!!v&&typeof v==='object'&&!Array.isArray(v);
 if(!map(raw))throw Error('INVALID_REWARD');const r=raw as Record<string,unknown>;
 if(Object.keys(r).some(k=>!['xp','currencies','items'].includes(k))||!map(r.xp))throw Error('INVALID_REWARD');
 for(const key of ['xp','currencies','items']){const v=r[key];if(v===undefined&&key!=='xp')continue;if(!map(v))throw Error('INVALID_REWARD');for(const [id,n] of Object.entries(v as object)){if(!id||['__proto__','constructor','prototype'].includes(id)||!Number.isSafeInteger(n)||Number(n)<0||Number(n)>2_000_000_000)throw Error('INVALID_REWARD');if(key==='currencies'&&!['credits','snack','peanut'].includes(id))throw Error('INVALID_REWARD');if(key==='items'&&!itemIds.includes(id))throw Error('INVALID_REWARD_ITEM');}}
 return structuredClone(raw) as AdventureRewards;
}
export function validateRewardRecipients(rewards:AdventureRewards,ownedIds:string[]){if(Object.keys(rewards.xp).some(id=>!ownedIds.includes(id)))throw Error('INVALID_REWARD_RECIPIENT');}
export function checkedRewardTotal(current:number,amount:number,max:number){if(!Number.isSafeInteger(current)||current<0||!Number.isSafeInteger(amount)||amount<0||current+amount>max)throw Error('REWARD_CAPACITY_REQUIRED');return current+amount;}
