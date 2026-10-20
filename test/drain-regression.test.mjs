import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattle,activeOf,act} from '../galmaegiman-confirmed/src/battle/engine.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url),'utf8'));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
function run({hp=5,protect=false,endure=0,max=1000,missing=500}={}){
 const R=rulesFrom(structuredClone(data));R.battleAug.get('drain').p.pct=20;
 const rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
 const b=createBattle(R,[rec('me')],[rec('foe')],17,{version:2,catalog:R.battleAugments.map(a=>({id:a.id,tier:'silver'})),weights:{silver:1,gold:0,prismatic:0}});b.phase='action';b.log=[];b.events=[];
 for(const s of b.sides){s.offers=null;s.augs=[];for(const f of s.fighters){f.passive={};f.maxHp=f.hp=1000;f.scale=1;f.attr='STONE';f.base={patk:100,pdef:100,matk:100,mdef:100,spd:100};}}
 const me=activeOf(b,0),foe=activeOf(b,1);me.maxHp=max;me.hp=max-missing;me.base.spd=1000;foe.hp=hp;foe.protect=protect;foe.endure=endure;foe.status.sleep=100;b.sides[0].augs=['drain'];
 const skill={id:'drain-test',name:'시험 공격',attr:'STONE',power:400,cat:'phys',cd:0,effects:[],desc:''};me.skills=[skill];
 act(b,[{kind:'skill',skillId:skill.id},{kind:'skill',skillId:foe.skills[0].id}]);return b;
}
test('증강 흡혈은 과잉피해가 아닌 실제 HP 감소의 20%',()=>{const b=run();const hits=b.events.filter(e=>e.reason==='skill'&&e.kind==='damage');assert.equal(hits.reduce((s,e)=>s+e.amount,0),5);assert.equal(b.events.find(e=>e.reason==='drain')?.amount,1);assert(b.log.some(t=>t.includes('체력을 1 되찾았어요')));});
test('흡혈 회복 로그는 최대체력에 잘린 실제 회복량',()=>{const b=run({hp:100,missing:1});assert.equal(b.events.find(e=>e.reason==='drain')?.amount,1);assert(b.log.some(t=>t.includes('체력을 1 되찾았어요')));});
test('버티기 HP 1 보존 뒤 실제 감소량만 흡혈',()=>{const b=run({hp:5,endure:3});assert.equal(b.events.filter(e=>e.reason==='skill'&&e.kind==='damage').reduce((s,e)=>s+e.amount,0),4);assert.equal(b.events.find(e=>e.reason==='drain')?.amount,1);});
