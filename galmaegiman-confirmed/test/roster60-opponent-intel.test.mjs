// Focused dependency-free contract suite; no database, Android build or network required.
// node --experimental-vm-modules --test test/roster60-presentation.test.mjs
import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync,existsSync} from 'node:fs';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {stripTypeScriptTypes} from 'node:module';
import {SourceTextModule,SyntheticModule} from 'node:vm';
import * as crypto from 'node:crypto';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const limits={turnMs:30000,roomMaxMs:3600000};
class RosterError extends Error{constructor(status,code){super(code);this.status=status;}}
const noop=()=>{};
let tutorialVerifications=0;
const stubs={
 'src/roster60-onboarding.ts':{RosterError,verifyRosterTutorial:()=>{tutorialVerifications++;}},
 'src/roster60-adventure.ts':{adventureEscapePolicy:{},adventureEscapeSucceeds:()=>true,settleAdventureBattle:noop},
 'src/battle/load.ts':{loadBattleRules:()=>({data:{items:{}}})},
 'src/content.ts':{loadContent:()=>({economy:{pvp:{level:1}}})},
 'src/pvp/service.ts':{REALTIME_LIMITS:limits},
 'src/ranked/settlement.ts':{matchmakingRating:noop,registerRankedMatch:noop,finishAndSettleRankedMatch:noop,settleCasualHuman:noop},
 'src/progression.ts':{awardPlayPoints:noop}
};
const cache=new Map();
function synthetic(id,values){return new SyntheticModule(Object.keys(values),function(){for(const [key,value] of Object.entries(values))this.setExport(key,value);},{identifier:id});}
function source(path){let file=resolve(root,path);if(!existsSync(file)&&process.env.ROSTER_PRESENTATION_SOURCE_FALLBACK)file=resolve(process.env.ROSTER_PRESENTATION_SOURCE_FALLBACK,path);return readFileSync(file,'utf8');}
function moduleFor(path){
 if(cache.has(path))return cache.get(path);
 const module=path==='node:crypto'?synthetic(path,crypto):stubs[path]?synthetic(path,stubs[path]):new SourceTextModule(stripTypeScriptTypes(source(path),{mode:'transform'}),{identifier:path});
 cache.set(path,module);return module;
}
async function load(path){
 const module=moduleFor(path);
 if(module.status==='unlinked')await module.link((specifier,referencing)=>moduleFor(specifier.startsWith('node:')?specifier:resolve('/',dirname(referencing.identifier),specifier).slice(1).replace(/\.js$/,'.ts')));
 return module;
}
const battleModule=await load('src/roster60-battle.ts');await battleModule.evaluate();
const pvpModule=await load('src/roster60-pvp.ts');await pvpModule.evaluate();
const helperModule=await load('src/battle/roster60-presentation.ts');
const {RosterBattle}=battleModule.namespace,{RosterPvp}=pvpModule.namespace;
const {capturePresentation,presentStep,presentationForViewer}=helperModule.namespace;
const calculation={model:'linear-level-hyperbolic-v1',version:'test',levelSlope:0,durabilityScale:100,resistanceScale:100};
const policy={calculation,maxRounds:100,enemyStrategy:'first-affordable'};
const elements=['NORMAL','FIRE','WATER','LIGHTNING','ICE','NATURE','HOLY','DARK'];
function fighter(id,speed=10,defense='normal'){
 return {id,ownedId:'private-'+id,speciesId:id==='self'?'u01':'u02',name:id,level:1,hp:100,mp:40,
  stats:{hp:100,mp:40,speed,durability:0,statusResistance:0},
  attack:{id:'basic',name:'불꽃 타격',element:'FIRE',mpCost:3,power:20},skills:[],
  suitability:Object.fromEntries(elements.map(e=>[e,0])),defense:Object.fromEntries(elements.map(e=>[e,defense]))};
}
function story({self=fighter('self',10),enemy=fighter('enemy',20),reserves=[],mode='story',items={}}={}){
 const state={version:'five-battle-v1',rulesVersion:'party-actions-v2',turn:0,fighters:[self,enemy],reserves,mode,outcome:null,events:[],decision:{version:'five-speed-v1',battleId:'b',turn:0,fighters:['enemy','self'],speeds:[enemy.stats.speed,self.stats.speed],order:enemy.stats.speed>self.stats.speed?['enemy','self']:['self','enemy'],tie:false}};
 const row={id:'b',playerId:'player',ownedId:self.ownedId,seed:'seed',revision:0,status:'ACTIVE',settlement:null,state:JSON.stringify(state),snapshot:JSON.stringify({policy,items})};
 const receipts=new Map();let writes=0;
 const tx={roster60Battle:{findFirst:async()=>({...row}),updateMany:async({data})=>{writes++;Object.assign(row,data,{revision:row.revision+1});return {count:1};}},roster60BattleReceipt:{findUnique:async({where})=>receipts.get(where.playerId_requestId.requestId),create:async({data})=>{receipts.set(data.requestId,data);}},playerItem:{findMany:async()=>[],updateMany:async()=>({count:1})}};
 const service=new RosterBattle({...tx,$transaction:fn=>fn(tx)},()=>null,()=>null);
 return {service,row,get writes(){return writes;},act:(action,requestId='req')=>service.action('player',{battleId:'b',revision:0,requestId,action})};
}
function chained(events,initial,final){
 let expected=capturePresentation(initial);
 for(const e of events){assert.equal(e.presentation.version,1);assert.deepEqual(e.presentation.before,expected);expected=e.presentation.after;}
 assert.deepEqual(expected,capturePresentation(final));
}
function pvp({a=fighter('a',10),b=fighter('b',20),reserveA=[],reserveB=[],actions=[{kind:'attack'},{kind:'attack'}],items={}}={}){
 const state={turn:0,parties:[[a,...reserveA],[b,...reserveB]],pending:actions,accepted:[true,true],ack:[false,false],misses:[0,0],winner:null,events:[]};
 const row={id:'pvp',revision:1,status:'ACTIVE',player0Id:'player0',player1Id:'player1',seed:'seed',createdAt:new Date(),deadline:new Date(Date.now()+30000),state:JSON.stringify(state),snapshot:JSON.stringify({policy,items,members:[]}),settlement:null};
 const tx={roster60PvpMatch:{updateMany:async({data})=>{Object.assign(row,data);return {count:1};},update:async({data})=>{Object.assign(row,data,{revision:data.revision?row.revision+1:row.revision});return {...row};},findUnique:async()=>({...row})},playerItem:{findMany:async()=>[],upsert:async()=>{}},realtimeItemEscrow:{updateMany:async()=>({count:1})}};
 const service=new RosterPvp({},()=>null,()=>null);
 // Isolate presentation contract from settlement DB/economy; resolver and view are real methods.
 service.finish=async(_tx,r,s,winner)=>{s.winner=winner;Object.assign(row,r,{state:JSON.stringify(s),status:'FINISHED',revision:r.revision+1});return {...row};};
 service.request=async(_id,_requestId,_payload,run)=>run(tx);
 return {service,state,row,tx,resolve:()=>service.resolve(tx,row,state),view:side=>service.view(tx,row,'player'+side)};
}

const intelModule=await load('src/battle/roster60-opponent-intel.ts');
const {opponentIntel,selectEnemySpecies}=intelModule.namespace;
const known=i=>Object.entries(i.defenses).filter(([,v])=>v!=='unknown').map(([k])=>k);

test('no random or turn defense reveal; stats after first round and stable polling',async()=>{
 const h=story();let r=await h.service.read('player','b');const initial=r.state.opponentIntel;
 assert.equal(initial.statsKnown,false);assert.equal(initial.stats,null);assert.equal(known(initial).length,0);
 assert.deepEqual(Object.keys(r.state.fighters[1].stats).sort(),['hp','mp']);
 assert.equal(r.state.fighters[1].defense,undefined);assert.equal(r.state.fighters[1].suitability,undefined);assert.equal(r.state.fighters[1].ownedId,undefined);assert.equal(r.state.decision.speeds,undefined);
 const own=JSON.parse(h.row.state).fighters[0];own.ownResistance={...own.defense};delete own.defense;assert.deepEqual(r.state.fighters[0],own);
 for(let turn=1;turn<=3;turn++){
  r=await h.service.action('player',{battleId:'b',revision:h.row.revision,requestId:'turn'+turn,action:{kind:'focus'}});
  const i=r.state.opponentIntel;assert.equal(i.completedTurns,turn);assert.equal(i.statsKnown,true);assert.deepEqual(i.stats,JSON.parse(h.row.state).fighters[1].stats);assert.equal(known(i).length,0);
  for(const e of known(initial))assert.notEqual(i.defenses[e],'unknown');
  for(let poll=0;poll<3;poll++)assert.deepEqual((await h.service.read('player','b')).state.opponentIntel,i);
  // A fresh service instance obtains identical knowledge exclusively from persisted row/seed.
  const fresh=new RosterBattle({roster60Battle:{findFirst:async()=>({...h.row})}},()=>null,()=>null);
  assert.deepEqual((await fresh.read('player','b')).state.opponentIntel,i);
 }
 assert.ok(!JSON.stringify(r.state.opponentIntel).includes('private-'));
 assert.equal(JSON.parse(h.row.state).fighters[1].defense.FIRE,'normal');
});
test('forced replacement/revision does not grant a round; ordinary switch does',async()=>{
 for(const forced of [true,false]){
  const self=fighter('self');if(forced)self.hp=0;const reserve=fighter('reserve');
  const h=story({self,reserves:[reserve]});const before=(await h.service.read('player','b')).state.opponentIntel;
  const r=await h.act({kind:'switch',targetOwnedId:reserve.ownedId});
  assert.equal(r.state.opponentIntel.completedTurns,forced?0:1);
  assert.equal(r.state.opponentIntel.knownDefenseCount,0);
  if(forced)assert.deepEqual(r.state.opponentIntel,before);
 }
});
test('PvP pending/read projections are viewer-correct, private-free and cannot turn humans into AI',async()=>{
 const h=pvp({reserveA:[fighter('reserveA')],reserveB:[fighter('reserveB')],actions:[{kind:'skill',skillId:'private-pending'},null]});
 for(const side of [0,1]){
  const r=await h.view(side),other=1-side;const i=r.state.opponentIntel;
  assert.equal(i.name,h.state.parties[other][0].name);assert.equal(i.statsKnown,false);assert.equal(i.knownDefenseCount,0);
  assert.equal(r.state.fighters[0].defense,undefined);assert.deepEqual(r.state.fighters[0].suitability,h.state.parties[side][0].suitability);
  assert.deepEqual(r.state.fighters[0].ownResistance,h.state.parties[side][0].defense);
  assert.equal(r.state.fighters[1].ownResistance,undefined);
  const serialized=JSON.stringify(r);assert.ok(!serialized.includes(h.state.parties[other][0].ownedId));assert.ok(!serialized.includes(h.state.parties[other][1].ownedId));assert.ok(!serialized.includes('private-pending'));assert.equal(r.seed,undefined);
  assert.deepEqual((await h.view(side)).state.opponentIntel,i);
 }
 assert.deepEqual(h.state.parties.map(p=>p[0].name),['a','b']);
 h.state.pending=[{kind:'focus'},{kind:'focus'}];await h.resolve();
 for(const side of [0,1]){const r=await h.view(side);assert.equal(r.state.opponentIntel.statsKnown,true);assert.equal(r.state.opponentIntel.knownDefenseCount,0);}
});
test('opponent replacement has stable separate identity, battle reset clears progress',()=>{
 const a=fighter('a'),b=fighter('b');b.speciesId='u09';
 const one=opponentIntel(a,0,{},'unverified');
 assert.deepEqual(opponentIntel(a,0,{},'unverified'),one);
 const two=opponentIntel(b,1,{},'unverified');assert.equal(two.speciesId,'u09');assert.equal(two.knownDefenseCount,0);
 assert.deepEqual(opponentIntel(a,0,{},'unverified'),one);
 assert.equal(opponentIntel(a,0,{},'unverified').knownDefenseCount,0);
});
const actualPolicy=JSON.parse(readFileSync(resolve(root,'data/test-profiles/five-stat-device-test-v1/battle-policy.json'),'utf8'));
const allowed=['u07','u09','u17','u31','u45','u57'];
function creationHarness(encounterId='training-1',adventure=false,options={}){
 const approved={...actualPolicy};
 const characters=['u01','u02',...allowed].map(id=>{
  const f=fighter(id);if(id===options.speciesId&&options.power)f.attack.power=options.power;return {...f,id,name:id==='u01'?'갈매미맨':id,growth:Array.from({length:60},(_,i)=>({totalXp:i*1000,maxMp:40})),combatGrowth:Array.from({length:60},()=>({...f.stats})),levelOneStats:f.stats};
 });
 const catalog={approved:true,statSchema:'gmm-five-stats-v1',version:approved.catalogVersion,characters};
 const owned={id:'owned',speciesId:'u01',playerId:'player',reincarnation:0,status:adventure?'ADVENTURE':'AVAILABLE',locked:false,totalXp:0n,...options};
 const receipts=new Map();let row,creates=0,awards=0;
 const matches=(record,where)=>record&&Object.entries(where).every(([k,v])=>record[k]===v);
 const assign=(record,data)=>{for(const [k,v] of Object.entries(data))record[k]=v&&typeof v==='object'&&'increment' in v?record[k]+v.increment:v;};
 const run={id:'run',revision:0,catalogVersion:catalog.version,state:JSON.stringify({party:[{ownedId:owned.id,hp:100}]}),pendingRewards:'{"xp":{}}'};
 const tx={roster60ObservedDefense:{findMany:async()=>[],upsert:async()=>{}},roster60Owned:{findFirst:async({where})=>matches(owned,where)?{...owned}:null,updateMany:async({where,data})=>{if(!matches(owned,where))return {count:0};if(data.totalXp!==undefined)awards++;assign(owned,data);return {count:1};}},
  roster60BattleReceipt:{findUnique:async({where})=>receipts.get(where.playerId_requestId.requestId),create:async({data})=>{receipts.set(data.requestId,data);}},
  roster60Battle:{create:async({data})=>{creates++;row={...data,revision:0,status:'ACTIVE',settlement:null};return {...row};},findFirst:async({where})=>matches(row,where)?{...row}:null,updateMany:async({where,data})=>{if(!matches(row,where))return {count:0};assign(row,data);return {count:1};}},
  player:{update:async()=>{}},roster60Adventure:{findFirst:async()=>run,updateMany:async()=>({count:1})},playerItem:{findMany:async()=>[]}};
 const service=new RosterBattle({...tx,$transaction:fn=>fn(tx)},()=>catalog,()=>approved);
 return {service,owned,get row(){return row;},get awards(){return awards;},get creates(){return creates;},create:()=>service.create('player',{requestId:'same',ownedId:'owned',encounterId,...(adventure?{adventureId:'run'}:{})})};
}
test('actual create endpoint draws allowed AI pool, pins level/rewards, replay returns same enemy',async()=>{
 for(const [id,adventure] of [['training-5',false],['adventure-1',true],['tutorial',false]]){
  const h=creationHarness(id,adventure),r=await h.create(),e=actualPolicy.encounters.find(e=>e.id===id);
  assert.equal(r.state.fighters[1].level,e.level);assert.equal(r.state.opponentIntel.knownDefenseCount,0);
  assert.ok(id==='tutorial'?r.state.fighters[1].speciesId==='u02':allowed.includes(r.state.fighters[1].speciesId));
  const snap=JSON.parse(h.row.snapshot);assert.equal(snap.encounter.winXp,e.winXp);assert.equal(snap.encounter.lossXp,e.lossXp);
  assert.deepEqual(await h.create(),r);assert.equal(h.creates,1);
  assert.deepEqual(await h.service.read('player',h.row.id),r);
 }
});
test('random pool varies by server seed; fixed story/tutorial/boss exceptions survive',()=>{
 for(const e of actualPolicy.encounters.filter(e=>!e.tutorial)){
  const seen=new Set(Array.from({length:128},(_,i)=>selectEnemySpecies(e,actualPolicy.encounters,'seed'+i)));
  assert.deepEqual([...seen].sort(),[...allowed].sort());
  assert.equal(selectEnemySpecies(e,actualPolicy.encounters,'same'),selectEnemySpecies(e,actualPolicy.encounters,'same'));
 }
 for(const exception of [{tutorial:true},{mode:'story'},{fixedEnemy:true}]){
  const e={...actualPolicy.encounters[1],...exception};assert.equal(selectEnemySpecies(e,actualPolicy.encounters,'seed'),e.speciesId);
 }
});

test('non-u01 owned member can create and win fixed tutorial; action/settlement replay awards once',async()=>{
 const h=creationHarness('tutorial',false,{speciesId:'u09',power:500});
 const created=await h.create();assert.equal(created.state.fighters[0].speciesId,'u09');assert.equal(created.state.fighters[1].speciesId,'u02');
 assert.equal(h.owned.status,'BATTLE');assert.equal(h.awards,0);
 const command={requestId:'tutorial-attack',battleId:h.row.id,revision:0,action:{kind:'attack'}};
 const fought=await h.service.action('player',command);assert.equal(fought.status,'FINISHED');assert.equal(fought.state.outcome,'win');
 assert.deepEqual(await h.service.action('player',command),JSON.parse(JSON.stringify(fought)));assert.equal(h.awards,0);
 const verificationBefore=tutorialVerifications;
 const request={requestId:'tutorial-settle',battleId:h.row.id,revision:h.row.revision};
 const settled=await h.service.settle('player',request);assert.equal(settled.status,'SETTLED');assert.equal(settled.settlement.xpAwarded,30);assert.equal(h.owned.totalXp,30n);assert.equal(h.owned.status,'AVAILABLE');
 assert.deepEqual(await h.service.settle('player',request),JSON.parse(JSON.stringify(settled)));
 await h.service.settle('player',{...request,requestId:'settle-again',revision:h.row.revision});
 assert.equal(h.awards,1);assert.equal(tutorialVerifications-verificationBefore,1);
});
test('tutorial still rejects foreign or unavailable members and out-of-scope read/action/settlement',async()=>{
 for(const [options,code] of [[{playerId:'someone-else'},'UNIT_NOT_FOUND'],[{status:'CONSUMED'},'UNIT_UNAVAILABLE'],[{status:'BATTLE'},'UNIT_UNAVAILABLE'],[{locked:true},'UNIT_UNAVAILABLE']]){
  const h=creationHarness('tutorial',false,{speciesId:'u09',...options});await assert.rejects(h.create(),e=>e.message===code);assert.equal(h.creates,0);assert.equal(h.awards,0);
 }
 const h=creationHarness('tutorial',false,{speciesId:'u09'});await h.create();
 await assert.rejects(h.service.create('player',{requestId:'missing',ownedId:'not-owned',encounterId:'tutorial'}),e=>e.message==='UNIT_NOT_FOUND');
 await assert.rejects(h.service.read('other-player',h.row.id),e=>e.message==='BATTLE_NOT_FOUND');
 await assert.rejects(h.service.action('other-player',{requestId:'unauthorized-action',battleId:h.row.id,revision:0,action:{kind:'attack'}}),e=>e.message==='BATTLE_NOT_FOUND');
 await assert.rejects(h.service.settle('other-player',{requestId:'unauthorized-settle',battleId:h.row.id,revision:0}),e=>e.message==='BATTLE_NOT_FOUND');
 await assert.rejects(h.service.settle('player',{requestId:'early-settle',battleId:h.row.id,revision:0}),e=>e.message==='BATTLE_NOT_FINISHED');assert.equal(h.awards,0);
});
