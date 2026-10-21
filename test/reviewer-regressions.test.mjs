import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {augmentCatalogV2,parseAugmentCatalogV2,createAugmentConfigV2} from '../galmaegiman-confirmed/src/battle/augment-catalog-v2.ts';
import {createBattle,choose,act,activeOf} from '../galmaegiman-confirmed/src/battle/engine.ts';
import {eligibleAugmentIds} from '../galmaegiman-confirmed/src/battle/augment-eligibility.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url),'utf8'));delete data.recipePower;data.multipliers.critChance=0;
function make(){const R=rulesFrom(data,{augmentVersion:2}),rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]}),b=createBattle(R,[rec('a')],[rec('b')],19,createAugmentConfigV2({silver:1,gold:1,prismatic:1}));for(const s of b.sides){s.offers=null;const f=s.fighters[0];f.hp=f.maxHp=10000;f.scale=10;f.base={patk:100,matk:100,pdef:100,mdef:100,spd:f.side?90:100};f.passive={};f.skills=[{id:'hit',name:'hit',power:1000,cat:'phys',cd:0,effects:[],desc:''}];}b.phase='action';return b;}
function select(b,id,side=0){b.phase='choose';b.sides[side].offers=[id];choose(b,side,{augment:id});}
function step(b){activeOf(b,1).skills.push({id:'idle',name:'idle',cd:0,effects:[],desc:''});act(b,[{kind:'skill',skillId:'hit'},{kind:'skill',skillId:'idle'}],{prevalidated:true});}
test('검토수정 coldbargain: regen-only 공격은 후보에서 제외',()=>{const b=make();activeOf(b,0).skills[0].effects=[{kind:'regen',pct:10,turns:3,target:'self'}];assert(!eligibleAugmentIds(b,0).includes('coldbargain'));activeOf(b,0).skills[0].drain=20;assert(eligibleAugmentIds(b,0).includes('coldbargain'));});
test('검토수정 triage: 설정에서15%상한 거절 및 런타임변조에도 신규회복15% 유지',()=>{const catalog=structuredClone(augmentCatalogV2);catalog.augments.find(a=>a.id==='triage').p.maxHpHealCapPct=100;assert.throws(()=>parseAugmentCatalogV2(catalog));const b=make(),f=activeOf(b,0);f.hp=1000;select(b,'triage');Object.assign(b.R.battleAug.get('triage').p,{actualDamageHealPct:100,maxHpHealCapPct:100});step(b);assert.equal(f.hp,2500);});
test('검토수정 rescue: 같은 시전이 부여한 상태도 완료시 해제',()=>{const b=make(),f=activeOf(b,1);f.hp=3100;f.endure=2;select(b,'rescue',1);activeOf(b,0).skills[0].effects=[{kind:'status',status:'bind',chance:100,turns:3}];step(b);assert(f.hp>0&&f.hp<=3000);assert.equal(f.status.bind,undefined);assert(f.statusUntil.bind>0);});
test('검토수정 underdog: 비용은내HP만, 추가피해는 상대버티기 보존',()=>{const b=make(),f=activeOf(b,1);f.rec.level=40;f.hp=200;f.endure=2;select(b,'underdog');step(b);assert.equal(f.hp,1);assert.equal(activeOf(b,0).hp,9200);});
