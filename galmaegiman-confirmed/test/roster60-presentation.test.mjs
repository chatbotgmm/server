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
const stubs={
 'src/roster60-onboarding.ts':{RosterError,verifyRosterTutorial:noop},
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
  stats:{hp:100,mp:40,speed,durability:0,resistance:0},
  attack:{id:'basic',name:'불꽃 타격',element:'FIRE',mpCost:3,power:20},skills:[],
  suitability:Object.fromEntries(elements.map(e=>[e,0])),defense:Object.fromEntries(elements.map(e=>[e,defense]))};
}
function story({self=fighter('self',10),enemy=fighter('enemy',20),reserves=[],mode='story',items={}}={}){
 const state={version:'five-battle-v1',rulesVersion:'party-actions-v2',turn:0,fighters:[self,enemy],reserves,mode,outcome:null,events:[],decision:{version:'five-speed-v1',battleId:'b',turn:0,fighters:['enemy','self'],speeds:[enemy.stats.speed,self.stats.speed],order:enemy.stats.speed>self.stats.speed?['enemy','self']:['self','enemy'],tie:false}};
 const row={id:'b',playerId:'player',ownedId:self.ownedId,seed:'seed',revision:0,status:'ACTIVE',settlement:null,state:JSON.stringify(state),snapshot:JSON.stringify({policy,items})};
 const receipts=new Map();let writes=0;
 const tx={roster60Battle:{findFirst:async()=>({...row}),updateMany:async({data})=>{writes++;Object.assign(row,data,{revision:row.revision+1});return {count:1};}},roster60BattleReceipt:{findUnique:async({where})=>receipts.get(where.playerId_requestId.requestId),create:async({data})=>{receipts.set(data.requestId,data);}},playerItem:{findMany:async()=>[],updateMany:async()=>({count:1})}};
 const service=new RosterBattle({$transaction:fn=>fn(tx)},()=>null,()=>null);
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

test('snapshot projection is detached and contains no private state',()=>{
 const a=fighter('self'),b=fighter('enemy');a.pending={kind:'secret'};a.stats.secret='secret';
 const before=capturePresentation([a,b]);a.hp=7;a.stats.hp=999;
 assert.equal(before[0].hp,100);assert.equal(before[0].stats.hp,100);
 assert.deepEqual(Object.keys(before[0]).sort(),['hp','id','level','mp','name','speciesId','stats']);
 assert.deepEqual(Object.keys(before[0].stats).sort(),['hp','mp']);
 const step=presentStep(before,[a,b],{...a.attack,secret:'secret'});a.hp=3;
 assert.equal(step.after[0].hp,7);assert.deepEqual(Object.keys(step.skill).sort(),['element','id','mpCost','name']);
 assert.equal(presentationForViewer(undefined,1),undefined);
});
test('story enemy-first order, intermediate HP/MP, and exact duplicate receipt are retained',async()=>{
 const h=story(),initial=JSON.parse(h.row.state).fighters;const r=await h.act({kind:'attack'}),events=r.state.events[0].events;
 assert.deepEqual(events.map(e=>e.actor),['enemy','self']);
 assert.equal(events[0].presentation.after[0].hp,80);assert.equal(events[0].presentation.after[1].hp,100);
 assert.equal(events[1].presentation.before[0].hp,80);assert.equal(events[1].presentation.after[1].hp,80);
 assert.equal(events[0].presentation.skill.element,'FIRE');assert.equal(events[0].presentation.after[1].mp,37);
 chained(events,initial,r.state.fighters);
 assert.deepEqual(await h.act({kind:'attack'}),r);assert.equal(h.writes,1);
});
for(const defense of ['weak','resist','immune','reflect'])test('story '+defense+' matches authoritative target and exact vitals',async()=>{
 const h=story({self:fighter('self',30),enemy:fighter('enemy',10,defense)}),initial=JSON.parse(h.row.state).fighters;
 const r=await h.act({kind:'attack'}),events=r.state.events[0].events,e=events[0];
 assert.equal(e.defense,defense);assert.equal(e.presentation.skill.element,'FIRE');
 if(defense==='reflect'){assert.equal(e.target,'attacker');assert.equal(e.presentation.after[0].hp,80);assert.equal(e.presentation.after[1].hp,100);}
 if(defense==='immune')assert.deepEqual(e.presentation.after.map(f=>f.hp),[100,100]);
 chained(events,initial,r.state.fighters);
});
test('story focus, active healing, reserve revival, manual and forced switch preserve chains',async()=>{
 const self=fighter('self',30);self.mp=36;let h=story({self}),r=await h.act({kind:'focus'});assert.equal(r.state.events[0].events[0].recovered,4);assert.equal(r.state.events[0].events[0].presentation.after[0].mp,40);
 for(const forced of [false,true]){const self=fighter('self');if(forced)self.hp=0;const reserve=fighter('reserve');reserve.speciesId='u03';h=story({self,reserves:[reserve]});const initial=JSON.parse(h.row.state).fighters;r=await h.act({kind:'switch',targetOwnedId:reserve.ownedId});const events=r.state.events[0].events;assert.equal(events[0].kind,forced?'forced-switch':'switch');assert.equal(events[0].presentation.before[0].speciesId,'u01');assert.equal(events[0].presentation.after[0].speciesId,'u03');chained(events,initial,r.state.fighters);}
 const active=fighter('self');active.hp=40;h=story({self:active,items:{heal:{heal:30}}});r=await h.act({kind:'item',itemId:'heal'});assert.equal(r.state.events[0].events[0].presentation.after[0].hp,70);
 const reserve=fighter('reserve');reserve.hp=0;h=story({reserves:[reserve],items:{revive:{revive:50}}});r=await h.act({kind:'item',itemId:'revive',targetOwnedId:reserve.ownedId});const item=r.state.events[0].events[0];assert.equal(item.recovered,50);assert.deepEqual(item.presentation.before,item.presentation.after);assert.equal(r.state.reserves[0].hp,50);
});
test('story KO omits defeated response; escape records unchanged final vitals',async()=>{
 const enemy=fighter('enemy',10);enemy.hp=10;const h=story({self:fighter('self',30),enemy}),r=await h.act({kind:'attack'});assert.equal(r.status,'FINISHED');assert.equal(r.state.events[0].events.length,1);assert.equal(r.state.events[0].events[0].presentation.after[1].hp,0);
 for(const mode of ['story','adventure']){const x=story({mode}),out=await x.act({kind:'escape'}),e=out.state.events[0].events[0];assert.equal(e.kind,mode==='story'?'surrender':'escape-success');assert.deepEqual(e.presentation.before,e.presentation.after);}
});
test('PvP ordered events are correctly mirrored for viewer 1 and reveal no owned IDs',async()=>{
 const h=pvp(),initial=h.state.parties.map(p=>structuredClone(p[0]));await h.resolve();const events=h.state.events[0].events;assert.deepEqual(events.map(e=>e.side),[1,0]);chained(events,initial,h.state.parties.map(p=>p[0]));
 for(const side of [0,1]){const view=await h.view(side),ev=view.state.events[0].events;assert.deepEqual(ev.map(e=>e.actor),side===0?['enemy','self']:['self','enemy']);assert.equal(ev[0].presentation.before[0].name,side===0?'a':'b');assert.equal(ev[0].presentation.before[0].id,'self');assert.equal(ev[0].presentation.before[1].id,'enemy');assert.ok(!JSON.stringify(ev).includes('private-'));assert.ok(!JSON.stringify(ev).includes('"decision"'));}
});
test('PvP switch and item priority stay ahead of faster attacker',async()=>{
 const reserve=fighter('reserve');reserve.speciesId='u03';let h=pvp({reserveA:[reserve],actions:[{kind:'switch',targetOwnedId:reserve.ownedId},{kind:'attack'}]});let initial=h.state.parties.map(p=>structuredClone(p[0]));await h.resolve();let ev=h.state.events[0].events;assert.deepEqual(ev.map(e=>e.kind),['switch','skill']);assert.equal(ev[0].presentation.after[0].speciesId,'u03');chained(ev,initial,h.state.parties.map(p=>p[0]));
 const a=fighter('a');a.hp=50;h=pvp({a,items:{heal:{heal:30}},actions:[{kind:'item',itemId:'heal',heldItem:'heal'},{kind:'attack'}]});initial=h.state.parties.map(p=>structuredClone(p[0]));await h.resolve();ev=h.state.events[0].events;assert.equal(ev[0].kind,'item');assert.equal(ev[0].presentation.after[0].hp,80);assert.equal(ev[1].presentation.after[0].hp,60);chained(ev,initial,h.state.parties.map(p=>p[0]));
});
test('PvP forced switch and surrender action endpoints emit metadata',async()=>{
 const a=fighter('a');a.hp=0;const reserve=fighter('reserve');reserve.speciesId='u03';let h=pvp({a,reserveA:[reserve],actions:[null,null]});let r=await h.service.action('player0',{battleId:'pvp',revision:1,requestId:'forced',action:{kind:'switch',targetOwnedId:reserve.ownedId}});assert.equal(r.state.events[0].events[0].kind,'forced-switch');assert.equal(r.state.events[0].events[0].presentation.before[0].hp,0);assert.equal(r.state.events[0].events[0].presentation.after[0].speciesId,'u03');
 h=pvp({actions:[null,null]});r=await h.service.action('player1',{battleId:'pvp',revision:1,requestId:'surrender',action:{kind:'escape'}});const e=r.state.events[0].events[0];assert.equal(e.kind,'surrender');assert.equal(e.actor,'self');assert.deepEqual(e.presentation.before,e.presentation.after);
});

test('PvP reflect, immune, focus and reserve revival preserve exact active snapshots',async()=>{
 for(const defense of ['reflect','immune']){const h=pvp({a:fighter('a',30),b:fighter('b',10,defense)}),initial=h.state.parties.map(p=>structuredClone(p[0]));await h.resolve();const ev=h.state.events[0].events;assert.equal(ev[0].defense,defense);assert.equal(ev[0].presentation.after[0].hp,defense==='reflect'?80:100);assert.equal(ev[0].presentation.after[1].hp,100);chained(ev,initial,h.state.parties.map(p=>p[0]));}
 const a=fighter('a',30);a.mp=2;let h=pvp({a,actions:[{kind:'focus'},{kind:'attack'}]});await h.resolve();assert.equal(h.state.events[0].events[0].presentation.after[0].mp,12);
 const reserve=fighter('reserve');reserve.hp=0;h=pvp({reserveA:[reserve],items:{revive:{revive:50}},actions:[{kind:'item',itemId:'revive',heldItem:'revive',targetOwnedId:reserve.ownedId},{kind:'attack'}]});await h.resolve();const e=h.state.events[0].events[0];assert.equal(e.recovered,50);assert.deepEqual(e.presentation.before,e.presentation.after);const other=await h.view(1);assert.equal(other.state.events[0].events[0].targetOwnedId,undefined);assert.ok(!JSON.stringify(other.state.events[0].events[0]).includes('private-'));
});
test('PvP KO prevents later attack and last presentation matches final active state',async()=>{
 const b=fighter('b',10);b.hp=10;const h=pvp({a:fighter('a',30),b}),initial=h.state.parties.map(p=>structuredClone(p[0]));await h.resolve();assert.equal(h.row.status,'FINISHED');const ev=h.state.events[0].events;assert.equal(ev.length,1);assert.equal(ev[0].presentation.after[1].hp,0);chained(ev,initial,h.state.parties.map(p=>p[0]));
});
