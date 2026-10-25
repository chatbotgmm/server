import {it,expect} from 'vitest';import {checkedAdventureRewards,validateRewardRecipients,checkedRewardTotal} from '../src/roster60-adventure-rewards.js';
it('validates every escrow kind without inventing drops or silently truncating',()=>{expect(checkedAdventureRewards({xp:{a:0},currencies:{snack:3},items:{potion:1}},['potion']).items).toEqual({potion:1});for(const raw of [{xp:{a:-1}},{xp:{},currencies:{gold:1}},{xp:{},items:{unknown:1}},{xp:{},items:{potion:NaN}},{xp:{},extra:1}])expect(()=>checkedAdventureRewards(raw,['potion'])).toThrow();expect(()=>validateRewardRecipients({xp:{foreign:2}},['a'])).toThrow();expect(()=>checkedRewardTotal(20,1,20)).toThrow('REWARD_CAPACITY_REQUIRED');});

import {adventureBattleXp} from '../src/roster60-adventure.js';
it('awards each participant 100 percent and each unentered party member floored 50 percent without dividing',()=>{
 expect(adventureBattleXp(11,['a','b','c'],['a','b','a'])).toEqual({a:11,b:11,c:5});
 expect(adventureBattleXp(1,['a','b'],['a'])).toEqual({a:1,b:0});
 expect(adventureBattleXp(0,['a','b'],['a'])).toEqual({a:0,b:0});
 for(const call of [()=>adventureBattleXp(-1,['a'],['a']),()=>adventureBattleXp(1.5,['a'],['a']),()=>adventureBattleXp(2,['a'],['foreign']),()=>adventureBattleXp(2,['a','a'],['a']),()=>adventureBattleXp(2,['a'],[])])expect(call).toThrow('INVALID_PARTICIPATION');
});
