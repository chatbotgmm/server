import test from 'node:test';
import assert from 'node:assert/strict';
import {instanceGrowthCurve,growthAt,rosterMaxLevel,validateRosterLevel,skillElements,validateRosterDefinition,combinationGrowth,startMp,focusMp,spendMp,resolveElementDamage} from '../src/battle/roster60-rules.ts';
// Synthetic arithmetic cases only, NOT a product roster, MP curve, or skill cost table.
const curve=[{totalXp:0,maxMp:7},{totalXp:11,maxMp:13},{totalXp:30,maxMp:17}];
const damage=(defense,suitability=0)=>resolveElementDamage({baseDamage:20,suitability,defense,attackerHp:100,defenderHp:100},(base,fit)=>base*fit);
test('all eight skill elements use the same five defenses, fit is multiplicative',()=>{
  assert.equal(skillElements.length,8);
  for(const element of skillElements){
    assert.ok(element);
    assert.deepEqual(['weak','normal','resist','immune','reflect'].map(x=>damage(x,2).damage),[36,24,12,0,24]);
  }
  assert.equal(damage('normal',-20).damage,0);
  assert.equal(damage('weak').extraTurn,false);
  assert.equal(damage('reflect').target,'attacker');
  assert.equal(damage('reflect').canReflectAgain,false);
});
test('immune never calls reflection policy; reflection must have explicit policy',()=>{
  const input={baseDamage:20,suitability:2,defense:'immune',attackerHp:5,defenderHp:7};
  assert.equal(resolveElementDamage(input,()=>{throw Error('must not run');}).damage,0);
  assert.throws(()=>resolveElementDamage({...input,defense:'reflect'}),/POLICY_REQUIRED/);
  assert.equal(resolveElementDamage({...input,defense:'reflect'},()=>100).damage,5);
  assert.throws(()=>resolveElementDamage({...input,defense:'normal',baseDamage:Infinity}),/INVALID/);
  assert.throws(()=>resolveElementDamage({...input,defense:'reflect'},()=>-1),/INVALID/);
});
test('full starting MP, focus restores half with floor and caps without integer overflow',()=>{
  assert.deepEqual(startMp(12,curve),{mp:13,maxMp:13});
  assert.deepEqual(focusMp(2,13),{mp:8,recovered:6});
  assert.deepEqual(focusMp(12,13),{mp:13,recovered:1});
  assert.deepEqual(focusMp(0,3),{mp:1,recovered:1});
  assert.deepEqual(focusMp(0,1),{mp:0,recovered:0});
  assert.deepEqual(focusMp(2,31),{mp:17,recovered:15});
  assert.deepEqual(focusMp(8,42),{mp:29,recovered:21});
  assert.deepEqual(focusMp(31,31),{mp:31,recovered:0});
  assert.throws(()=>focusMp(-1,31),/INVALID/);
  assert.throws(()=>focusMp(32,31),/MP_EXCEEDS_MAX/);
  assert.equal(focusMp(Number.MAX_SAFE_INTEGER-1,Number.MAX_SAFE_INTEGER).mp,Number.MAX_SAFE_INTEGER);
  assert.equal(spendMp(7,7),0);
  assert.throws(()=>spendMp(7,8),/INSUFFICIENT/);
  assert.throws(()=>spendMp(7,-1),/INVALID/);
});
test('combination averages cumulative XP with floor then calculates level; cap retains XP',()=>{
  assert.deepEqual(combinationGrowth([10,15],curve),{level:2,maxLevel:60,reincarnation:0,totalXp:12,xp:1,maxMp:13,xpToNext:18});
  assert.equal(combinationGrowth([0,30,2],curve).level,1);
  assert.equal(combinationGrowth([100,101],curve).totalXp,100);
  assert.equal(combinationGrowth([Number.MAX_SAFE_INTEGER,Number.MAX_SAFE_INTEGER],curve).totalXp,Number.MAX_SAFE_INTEGER);
  assert.throws(()=>combinationGrowth([],curve),/MATERIALS/);
  assert.throws(()=>combinationGrowth([-1],curve),/INVALID/);
  assert.throws(()=>combinationGrowth([5],[{totalXp:1,maxMp:1}]),/ORIGIN/);
});
test('roster contract accepts six skills and rejects legacy creature elements/incomplete profiles',()=>{
  const skill={id:'attack',name:'테스트',element:'NORMAL',mpCost:1};
  const unit={id:'u01',name:'갈매미맨',attack:skill,
    skills:Array.from({length:6},(_,i)=>({...skill,id:`test${i}`})),
    growth:curve,suitability:Object.fromEntries(skillElements.map(e=>[e,0])),
    defense:Object.fromEntries(skillElements.map(e=>[e,'normal']))};
  validateRosterDefinition(unit);
  assert.throws(()=>validateRosterDefinition({...unit,attr:'STONE'}),/REMOVED/);
  assert.throws(()=>validateRosterDefinition({...unit,name:'기본'}),/U01/);
  assert.throws(()=>validateRosterDefinition({...unit,skills:[...unit.skills,skill]}),/TOO_MANY/);
  assert.throws(()=>validateRosterDefinition({...unit,suitability:{NORMAL:0}}),/PROFILE/);
  assert.throws(()=>validateRosterDefinition({...unit,attack:{...skill,mpCost:-1}}),/MP_COST/);
});

test('only server-owned reincarnation unlocks 99; overflow and reset are not inferred',()=>{
 const c=Array.from({length:99},(_,i)=>({totalXp:i*10,maxMp:i+1}));
 assert.equal(rosterMaxLevel(),60);assert.equal(rosterMaxLevel(1),99);
 assert.equal(growthAt(590,c).level,60);assert.equal(growthAt(590,c).xpToNext,null);
 assert.throws(()=>growthAt(600,c),/CAP_XP_POLICY_REQUIRED/);
 assert.throws(()=>validateRosterLevel(99,0),/LEVEL_CAP_EXCEEDED/);
 assert.throws(()=>rosterMaxLevel(-1),/INVALID/);assert.throws(()=>rosterMaxLevel(true),/INVALID/);
 assert.equal(growthAt(980,c,1,{earlyRequiredPercent:100,throughLevel:60}).level,99);assert.equal(growthAt(980,c,1,{earlyRequiredPercent:100,throughLevel:60}).maxLevel,99);
 assert.throws(()=>growthAt(981,c,1,{earlyRequiredPercent:100,throughLevel:60}),/CAP_XP_POLICY_REQUIRED/);
 assert.throws(()=>combinationGrowth([600,600],c),/CAP_XP_POLICY_REQUIRED/);
 assert.deepEqual(startMp(980,c,1,{earlyRequiredPercent:100,throughLevel:60}),{mp:99,maxMp:99});
});

test('confirmed default 70 percent requirement policy switches after 59 to 60, never boosts earned XP',()=>{
 const c=Array.from({length:99},(_,i)=>({totalXp:i*11,maxMp:i+1}));
 const policy={earlyRequiredPercent:70,throughLevel:60};
 const r=instanceGrowthCurve(c,1,policy);
 assert.equal(r[59].totalXp-r[58].totalXp,8); // ceil(11*.7)
 assert.equal(r[60].totalXp-r[59].totalXp,11);
 assert.equal(growthAt(r[98].totalXp,c,1,policy).level,99);
 assert.equal(growthAt(80,c,1,policy).totalXp,80);
 assert.equal(combinationGrowth([80,80],c).level,8); // normal result curve, no inherited rebirth
 assert.deepEqual(instanceGrowthCurve(c,1),r);
 assert.equal(growthAt(80,c,1).level,11);
 assert.equal(growthAt(80,c,0).level,8);
 assert.equal(growthAt(r[98].totalXp,c,1).level,99);
 assert.throws(()=>growthAt(r[98].totalXp+1,c,1),/CAP_XP_POLICY_REQUIRED/);
});
