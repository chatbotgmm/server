import {describe,it,expect} from 'vitest';
import {validateFiveStats,validateFiveStatCatalog,fiveStatSchema} from '../src/battle/roster60-five-stats.js';
// Structural fixtures only; these numbers are not product balance.
const stats=()=>({hp:1,mp:0,durability:0,statusResistance:0,speed:0});
const catalog=()=>({statSchema:fiveStatSchema,characters:[{stats:stats(),levelOneStats:stats(),growth:[{maxMp:0}]}]});
describe('current five-stat product boundary',()=>{
 it('accepts five explicit fields with MP separate from status resistance',()=>expect(()=>validateFiveStatCatalog(catalog())).not.toThrow());
 it('rejects old or mixed attack/defense fields instead of mapping them',()=>{
  expect(()=>validateFiveStats({hp:1,mp:0,patk:1,matk:1,pdef:1,mdef:1})).toThrow('FIVE_STATS_REQUIRED');
  expect(()=>validateFiveStats({...stats(),patk:1})).toThrow('FIVE_STATS_REQUIRED');
  expect(()=>validateFiveStatCatalog({...catalog(),statSchema:undefined})).toThrow('FIVE_STAT_CATALOG_REQUIRED');
 });
 it('does not accept an element resistance table as statusResistance',()=>expect(()=>validateFiveStats({...stats(),statusResistance:{FIRE:'resist'}})).toThrow('INVALID_FIVE_STATS'));
 it('rejects invalid integer stats and mismatched growth MP',()=>{
  for(const value of [-1,NaN,Infinity,1.2])expect(()=>validateFiveStats({...stats(),speed:value})).toThrow();
  const c=catalog();c.characters[0].growth[0].maxMp=1;expect(()=>validateFiveStatCatalog(c)).toThrow('BASE_MP_MISMATCH');
 });
});

it('five-stat rebirth preview blocks unapproved bonus before cost or mutation',async()=>{
 const {RosterReincarnation}=await import('../src/roster60-reincarnation.js');
 const {skillElements}=await import('../src/battle/roster60-rules.js');
 const owned={id:'owned-test',speciesId:'u45',reincarnation:0,status:'AVAILABLE',locked:false,totalXp:0n};
 const unit={id:'u45',name:'타이탄',rarity:'LIMITED',stats:stats(),levelOneStats:stats(),skills:[],attack:{id:'a',name:'a',element:'NORMAL',mpCost:0},suitability:Object.fromEntries(skillElements.map(e=>[e,0])),defense:Object.fromEntries(skillElements.map(e=>[e,'normal'])),growth:[{totalXp:0,maxMp:0}]};
 const db={roster60Owned:{findMany:async()=>[owned],findFirst:async()=>owned}};
 const service=new RosterReincarnation(db as never,()=>({approved:true,version:'structural-test',statSchema:fiveStatSchema,characters:[unit]}) as never);
 const preview=await service.preview('test-owner');expect(preview.candidates[0]).toMatchObject({eligible:false,reason:'FIVE_STAT_REBIRTH_POLICY_PENDING',preview:null});
});
