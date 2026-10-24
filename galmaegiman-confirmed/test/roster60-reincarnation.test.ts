import {it,expect} from 'vitest';
import {previewReincarnation} from '../src/battle/roster60-reincarnation.js';
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
