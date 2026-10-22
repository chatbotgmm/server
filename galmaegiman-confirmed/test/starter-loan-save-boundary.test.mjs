// Execute the production GameService methods, not a reimplementation or string-only assertion.
// Database delegates are in-memory doubles; full Prisma/Fastify integration remains required.
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {applyLoanLevel} from '../src/starter-loan-levels.ts';
import {activeLoanWhere} from '../src/starter-loans.ts';
const source=readFileSync(new URL('../src/game.ts',import.meta.url),'utf8');
const methods=source.slice(source.indexOf('  private async saveStory('),source.indexOf('  async webProgression('));
const Service=vm.runInNewContext(stripTypeScriptTypes(`(class Service {${methods}})`),{structuredClone,Set,Map,JSON,Object,activeLoanWhere});
const trusted={level:7,xp:11,stage:0,evoAugs:[],learned:['@','basic','sig'],learnUsed:0,learnPick:0};
const attack={level:99,xp:999999,stage:3,evoAugs:['forged'],learned:['forged'],learnUsed:9,learnPick:9};
function fixture(initial=true){
 let row=initial?{data:JSON.stringify({units:{o10:trusted,o11:{level:2,xp:1}},team:['o10','o11'],cleared:[],hp:{o10:{f:.3,at:100}}})}:null;
 const tx={ownedCharacter:{findMany:async({where})=>where.loanExpiresAt?[{id:10}]:[{id:10},{id:11}],updateMany:async()=>({count:1})},storySave:{findUnique:async()=>row,deleteMany:async()=>{row=null},upsert:async({create,update})=>{row={data:row?update.data:create.data}}}};
 const svc=new Service();svc.topIds=['D2','D3'];svc.clock=()=>new Date(200);svc.webRun=async(id,fn)=>fn(tx,{id,adminMode:false,isAdmin:false},new Date(200));
 return {svc,read:()=>row?JSON.parse(row.data):null};
}
test('real webSaveStory/saveStory rejects forged loan growth/HP while preserving ordinary permanent changes',async()=>{
 const {svc,read}=fixture();await svc.webSaveStory('owner',{units:{o10:attack,o11:attack,D2:attack},team:['o10','o11'],cleared:['1-6'],hp:{o10:{f:1,at:999}}});
 assert.deepEqual(read().units.o10,trusted);assert.deepEqual(read().units.o11,attack);assert.deepEqual(read().hp.o10,{f:.3,at:100});
});
test('real legacy omission and null reset cannot erase/reset server-owned loan progression',async()=>{
 const {svc,read}=fixture();await svc.webSaveStory('owner',{units:{},team:[],cleared:[]});assert.deepEqual(read().units.o10,trusted);
 await svc.webSaveStory('owner',null);assert.deepEqual(read().units.o10,trusted);assert.deepEqual(read().team,[]);assert.equal(read().units.o11,undefined);
});
test('real legacy save cannot seed an uninitialized loan oID',async()=>{
 const {svc,read}=fixture(false);await svc.webSaveStory('owner',{units:{o10:attack,D2:attack},team:['o10'],cleared:[],hp:{o10:{f:1,at:999}}});assert.equal(read().units.o10,undefined);assert.equal(read().hp.o10,undefined);
});
test('real webNativeCommit accepts server-computed XP then later legacy writes cannot replace it',async()=>{
 const {svc,read}=fixture();const growth={...trusted,level:8,xp:12};await svc.webNativeCommit('owner',undefined,{units:{o10:growth},team:['o10'],cleared:[],hp:{o10:{f:.5,at:200}}});assert.deepEqual(read().units.o10,growth);
 await svc.webSaveStory('owner',{units:{o10:attack},team:['o10'],cleared:[]});assert.deepEqual(read().units.o10,growth);
});
test('native rec executes without species fallback for loans; permanent legacy compatibility remains',()=>{
 const n=readFileSync(new URL('../src/native.ts',import.meta.url),'utf8');const code=n.slice(n.indexOf('  const rec='),n.indexOf('  const keep='));
 const rec=vm.runInNewContext(stripTypeScriptTypes(`(()=>{${code};return rec})()`),{applyLoanLevel,GameError:Error,content:{map:new Map([['D2',{name:'D2',rarity:'LIMITED'}]])},story:{start:{level:1}},innateLearned:()=>['basic'],R:{data:{growth:{maxLevel:60}}}});
 for(const temporary of [true,false]){const owned=Object.assign(new Map([['o10','D2']]),{lineage:new Map([['o10',{temporary,expiresAt:5000,ownedCharacterId:'D2'}]])});const r=rec('o10',{units:{D2:attack}},owned);assert.equal(r.level,temporary?1:99);assert.equal(r.stage,temporary?0:3);assert.equal(r.temporary,temporary);}
});

test('real webMe derives mixed-copy counts and expiry metadata from one owned snapshot/clock',async()=>{
 const code=source.slice(source.indexOf('  async webMe('),source.indexOf('  // 웹 요청도'));
 const Summary=vm.runInNewContext(stripTypeScriptTypes(`(class Summary {${code}})`),{activeLoanWhere,Map,Date,Set,rarityNames:{LIMITED:'제한'},topTiers:new Set(['LIMITED']),tiers:{LIMITED:1}});
 const service=new Summary();let clockReads=0,ownedReads=0;service.clock=()=>new Date(clockReads++?5001:4999);
 service.content={characters:[{id:'D2'}],map:new Map([['D2',{id:'D2',name:'D2',rarity:'LIMITED',position:1}]]),economy:{tickets:{cooldownHours:1}}};
 service.db={player:{findUnique:async()=>({id:'a',battleTeam:'D2',partyDraft:'',lastClaimAt:null})},ownedCharacter:{findMany:async({where})=>{ownedReads++;assert.equal(where.AND.OR[1].loanExpiresAt.gt.getTime(),4999);return [{id:10,characterId:'D2',status:'AVAILABLE',locked:true,loanExpiresAt:new Date(5000)},{id:11,characterId:'D2',status:'AVAILABLE',locked:false,loanExpiresAt:null}];}},collectionEntry:{count:async()=>1},expedition:{findFirst:async()=>null},kakaoLink:{count:async()=>0}};
 const me=await service.webMe('a');assert.equal(clockReads,1);assert.equal(ownedReads,1);assert.equal(me.units[0].have,2);assert.equal(me.units[0].temporaryCount,1);assert.equal(me.units[0].permanentCount,1);assert.equal(me.units[0].temporaryInstances[0].key,'o10');assert.equal(me.units[0].temporaryInstances[0].expiresAt,5000);
});
