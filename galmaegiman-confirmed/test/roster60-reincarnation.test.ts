import {it,expect} from 'vitest';
import {previewReincarnation,previewReincarnationCycle,reincarnationBaseStats} from '../src/battle/roster60-reincarnation.js';
import {growthAt} from '../src/battle/roster60-rules.js';
const input={totalXp:590,reincarnation:0,baseLevelOneStats:{hp:10,mp:5},curve:Array.from({length:99},(_,i)=>({totalXp:i*10,maxMp:i+1}))};
it('bonus approval gates reset; no balance defaults',()=>{
 expect(()=>previewReincarnation(input)).toThrow('REINCARNATION_BONUS_POLICY_REQUIRED');
 expect(()=>previewReincarnation(input,{approved:true,version:'synthetic',bonusFor:()=>({hp:0,mp:0})})).toThrow('MUST_IMPROVE');
});
it('new cycle starts at one, permanent bonus is separate, prior XP cannot immediately restore level',()=>{
 const before=structuredClone(input);
 const p=previewReincarnation(input,{approved:true,version:'synthetic-only',bonusFor:cycle=>{if(cycle!==1)throw Error('REPEAT_POLICY_REQUIRED');return {hp:2,mp:0};}});
 expect(p.next).toMatchObject({level:1,maxLevel:99,totalXp:0,reincarnation:1,permanentBonusStats:{hp:2,mp:0}});
 expect(growthAt(p.next.totalXp,input.curve,p.next.reincarnation).level).toBe(1);
 expect(p.afterLevelOneStats).toEqual({hp:12,mp:5});expect(input).toEqual(before);
});

it('confirmed first bonus covers six baseline fields once, rounding halves up, profiles unchanged',()=>{
 const base={hp:105,mp:55,patk:35,matk:45,pdef:65,mdef:75};
 const expected={hp:116,mp:61,patk:39,matk:50,pdef:72,mdef:83};
 expect(reincarnationBaseStats(base)).toEqual(expected);expect(reincarnationBaseStats(base)).toEqual(expected);
 const source={...input,baseLevelOneStats:base,suitability:{FIRE:2},defense:{FIRE:'resist'}};
 const p=previewReincarnationCycle(source);
 expect(p.afterLevelOneStats).toEqual(expected);expect(p.suitability).toEqual(source.suitability);expect(p.defense).toEqual(source.defense);
 expect(source.baseLevelOneStats).toEqual(base);expect(p.next.totalXp).toBe(0);
 expect(()=>previewReincarnationCycle({...source,reincarnation:5})).toThrow('REINCARNATION_LIMIT');
 expect(()=>reincarnationBaseStats({hp:100,mp:10} as any)).toThrow('SIX_BASE_STATS_REQUIRED');
 expect(()=>reincarnationBaseStats({...base,patk:-1})).toThrow('INVALID_BASE_STAT');
});

it('repeated bonuses add from original baseline, max 50 percent, never compound',()=>{
 const base={hp:100,mp:100,patk:100,matk:100,pdef:100,mdef:100};
 for(let n=1;n<=5;n++)expect(reincarnationBaseStats(base,n)).toEqual(Object.fromEntries(Object.keys(base).map(k=>[k,100+n*10])));
 expect(()=>reincarnationBaseStats(base,6)).toThrow('REINCARNATION_LIMIT');
});
