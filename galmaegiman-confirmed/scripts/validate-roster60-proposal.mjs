import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const data=JSON.parse(readFileSync(new URL('../design/roster60-review/workbook-complete-proposal.json',import.meta.url),'utf8'));
assert.equal(data.approved,false);
const ids=Array.from({length:60},(_,i)=>`u${String(i+1).padStart(2,'0')}`);
assert.deepEqual(data.units.map(u=>u.id),ids);
const elements=['normal','fire','water','lightning','ice','nature','holy','dark'].sort();
for(const [i,u] of data.units.entries()){
 assert.equal(Object.keys(u).length,14,u.id);
 assert.equal(u.sourceRow,i+8);assert.equal(u.statsReferenceLevel,20);
 assert.equal(u.hp+4*u.mp,600,u.id);
 assert.deepEqual(Object.keys(u.aptitudes).sort(),elements);
 assert.deepEqual(Object.keys(u.defenses).sort(),elements);
 assert.ok(Object.values(u.aptitudes).every(Number.isFinite));
 assert.ok(Object.values(u.defenses).every(x=>['weak','normal','resist','null','reflect'].includes(x)));
 assert.deepEqual(u.learnset.map(s=>s.level),[1,5,10,24,40]);
 assert.ok(u.learnset.every(s=>s.name&&s.stage));
 assert.equal(u.name,data.identities[i].name);
}
assert.equal(data.units[0].name,'갈매미맨');
assert.equal(data.latestConfirmedRules.maxSkills,6);
assert.equal(data.recipes.length,52);
assert.equal(new Set(data.recipes.map(r=>r.resultId)).size,52);
const byResult=new Map(data.recipes.map(r=>[r.resultId,r]));
function visit(id,trail=new Set()){
 assert.ok(!trail.has(id),'recipe cycle '+id);
 const next=new Set(trail);next.add(id);
 for(const m of byResult.get(id)?.ingredients??[]){
  assert.ok(ids.includes(m.unitId));assert.ok(Number.isInteger(m.count)&&m.count>0);
  assert.ok(Number.isInteger(m.minLevel)&&m.minLevel>0);visit(m.unitId,next);
 }
}
for(const r of data.recipes){assert.ok(ids.includes(r.resultId));visit(r.resultId);}
console.log('Validated draft: 60 full records, 300 learned skills, 52 acyclic recipes. NOT approved runtime balance.');
