// Review data only: compare proposed Lv1 projections with the real server contract.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {reincarnationBaseStats} from '../src/battle/roster60-reincarnation.ts';
const data=JSON.parse(readFileSync(new URL('../design/roster60-review/balance-v0.1/complete60-unit-data.draft.json',import.meta.url)));
assert.equal(data.approved,false);
let cases=0;
for(const unit of data.units.filter(u=>u.rebirthAllowed)) {
 for(let cycle=1;cycle<=5;cycle++) {
  const expected=Object.fromEntries(Object.entries(unit.levelOneStats).map(([k,v])=>[k,Math.floor((v*(100+10*cycle)+50)/100)]));
  assert.deepEqual(reincarnationBaseStats(unit.levelOneStats,cycle),expected);
  cases++;
 }
}
assert.equal(cases,80);
console.log(`PASS: ${cases} draft Lv1 projections match server six-stat rebirth contract; catalog remains unapproved.`);
