import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL(p,import.meta.url),'utf8');
const code=read('../public/game/battle.js');
function fixture(){
 const nodes=new Map(),events={};
 const node=k=>{if(!nodes.has(k))nodes.set(k,{innerHTML:'',textContent:'',hidden:true,dataset:{},classList:{toggle(){},contains(){return false}},append(){},focus(){},addEventListener(){},querySelector:s=>node(s),querySelectorAll:()=>[]});return nodes.get(k)};
 const S={esc:s=>String(s??''),fmt:String,combatEpoch:'gmm-combat-20261021-v3',routes:{},title(){},sprite(){},standee(){},state:{},loadState:async()=>{}};
 const context=vm.createContext({window:{GMM:S},document:{querySelector:node,querySelectorAll:()=>[],createElement:()=>node('chart'),body:node('body')},addEventListener(n,f){events[n]=f},location:{hash:'#battle'},crypto:{},setTimeout(){},clearTimeout(){},setInterval(){},clearInterval(){},Date,matchMedia:()=>({matches:true})});
 vm.runInContext(code.replace('Object.assign(S.routes, { match, battle });','S.test = { pane, draw, replay, ui, canAct, replayAllowed, drawAugment };'),context);
 const attrs=['STONE','GOLD','FIRE','ICE','BLOSSOM','SEA','SHADOW','STAR','THREAD','GRAVITY'];
 const f={key:'own',name:'내 대원',hp:50,maxHp:50,skills:[],status:{}};
 const a={kind:'own-attribute',selectionId:'selection-1',phaseId:'phase-1',targetKey:'own',opponentAttribute:'FIRE',attributes:attrs};
 const snap={session:'session-1',revision:1,phase:'choose',teams:[{active:0,fighters:[f],attributeSelection:a,offers:[]},{active:0,fighters:[{...f,key:'foe',name:'상대 대원'}]}]};S.battle={kind:'native',snap};
 const click=(selector,dataset={})=>node('.cmd-body').onclick({target:{closest:s=>s===selector?{dataset,disabled:false}:null}});
 return {S,node,snap,a,click,events};
}
test('server enemy-front and ten canonical choices; no hidden default; cancel clears draft',()=>{
 const {S,node,click,snap}=fixture();S.test.pane(snap);assert.match(node('.cmd-body').innerHTML,/상대 선두 상대 대원 · 불/);assert.equal((node('.cmd-body').innerHTML.match(/data-attribute="/g)||[]).length,10);assert.match(node('.cmd-body').innerHTML,/data-attribute-confirm disabled/);click('[data-attribute]',{attribute:'SEA'});assert.equal(S.test.ui.attribute,'SEA');click('[data-attribute-cancel]');assert.equal(S.test.ui.attribute,null);
});
test('explicit native confirm sends only exact server scope, not another augment',async()=>{
 const {S,click,snap}=fixture();let sent;S.api=async(path,body)=>{sent={path,body};return snap};S.test.pane(snap);click('[data-attribute]',{attribute:'GRAVITY'});click('[data-attribute-confirm]');await Promise.resolve();assert.equal(sent.path,'native/battle/turn');assert.deepEqual(JSON.parse(JSON.stringify(sent.body)),{session:'session-1',revision:1,choose:{freestyle:{selectionId:'selection-1',phaseId:'phase-1',targetKey:'own',attribute:'GRAVITY'}}});
});
test('invalid attribute lists, target and empty token cannot present confirmation',()=>{
 for(const mutate of [a=>a.attributes.pop(),a=>a.attributes[0]='INVALID',a=>a.attributes[0]='GOLD',a=>a.targetKey='foe',a=>a.selectionId='']){const {S,a,node,snap}=fixture();mutate(a);S.test.pane(snap);assert.match(node('.cmd-body').innerHTML,/다시 받아야/);assert.doesNotMatch(node('.cmd-body').innerHTML,/data-attribute-confirm/);}
});
test('deadline locks native input and explicit supported epoch gates replay',()=>{
 const {S,snap}=fixture();snap.chooseDeadline=Date.now()-1;assert.equal(S.test.canAct(),false);for(const epoch of [null,'old','gmm-combat-20261021-v4'])assert.equal(S.test.replayAllowed({replayAvailable:true,combatRulesEpoch:epoch}),false);assert.equal(S.test.replayAllowed({replayAvailable:true,combatRulesEpoch:S.combatEpoch}),true);assert.equal(S.test.replayAllowed({replayAvailable:false,combatRulesEpoch:S.combatEpoch}),false);
});
test('old replay skips damage events and history display',async()=>{
 const {S,snap,node}=fixture();snap.combatRulesEpoch='old';snap.replayAvailable=false;snap.displayEvents=[{id:1,kind:'damage'}];snap.log=['OLD-LOG-NOT-FOR-PLAYBACK'];await S.test.replay(snap);S.test.draw();assert.doesNotMatch(node('#view').innerHTML,/OLD-LOG-NOT-FOR-PLAYBACK/);
});
test('server tiers and exact reverse label/description retained without future tier generation',()=>{
 const {S,snap,node}=fixture();for(const tier of ['silver','gold','prismatic']){snap.teams[0].offers=[{id:'reverse',name:'자유자재',desc:'서버 설명',tier}];S.test.drawAugment(snap);assert.match(node('#view').innerHTML,new RegExp(`data-tier="${tier}"`));assert.match(node('#view').innerHTML,/자유자재/);assert.match(node('#view').innerHTML,/서버 설명/);}assert.doesNotMatch(code,/tierSequence|augmentSeed/);
});
test('same snapshot preserves draft; new revision, scope, session clear it',()=>{
 const {S,snap}=fixture();S.test.draw();S.test.ui.attribute='SEA';S.test.draw();assert.equal(S.test.ui.attribute,'SEA');snap.revision++;S.test.draw();assert.equal(S.test.ui.attribute,null);S.test.ui.attribute='GOLD';snap.teams[0].attributeSelection.selectionId='next';S.test.draw();assert.equal(S.test.ui.attribute,null);S.test.ui.attribute='FIRE';snap.session='new';S.test.draw();assert.equal(S.test.ui.attribute,null);
});
test('normal attack/item/switch action payloads unchanged',async()=>{
 for(const mode of ['attack','item','switch']){const {S,snap,click}=fixture();snap.phase='action';snap.teams[0].attributeSelection=null;snap.teams[0].fighters.push({...snap.teams[0].fighters[0],key:'bench',name:'대기'});snap.teams[0].fighters[0].skills=[{id:'basic-FIRE',name:'공격'}];snap.inventory=[{id:'potion',count:1}];snap.items=[{id:'potion',name:'회복',desc:'회복'}];let sent;S.api=async(path,body)=>{sent=body;return snap};S.test.ui.pane=mode;S.test.pane(snap);if(mode==='attack')click('[data-skill]',{skill:'basic-FIRE'});if(mode==='item'){click('[data-item]',{item:'potion'});click('[data-use]');}if(mode==='switch'){click('[data-sw]',{sw:'1'});click('[data-go]');}await Promise.resolve();assert.equal(sent.action.kind,mode==='attack'?'skill':mode);assert.equal(sent.revision,1);}
});
test('captured Escape handler clears local draft but retains required choice phase',()=>{
 const {S,snap,events,node}=fixture();S.test.ui.attribute='SEA';events.keydown({key:'Escape'});assert.equal(S.test.ui.attribute,null);assert.match(node('.cmd-body').innerHTML,/data-attribute-confirm disabled/);assert.equal(snap.phase,'choose');
});
test('realtime choice retains room envelope and exact scoped freestyle payload',async()=>{
 const {S,snap,click}=fixture();S.battle={kind:'realtime',roomId:'room-1',snap,room:{phaseId:'room-phase',revision:4,canSubmit:true,mySubmitted:false}};let sent;S.api=async(path,body)=>{sent={path,body};return {roomId:'room-1',phaseId:'room-phase',revision:5,canSubmit:false,mySubmitted:true,battle:snap}};S.test.pane(snap);click('[data-attribute]',{attribute:'ICE'});click('[data-attribute-confirm]');await Promise.resolve();assert.equal(sent.path,'pvp/room/command');assert.equal(sent.body.roomId,'room-1');assert.equal(sent.body.phaseId,'room-phase');assert.equal(typeof sent.body.requestId,'string');assert.deepEqual(JSON.parse(JSON.stringify(sent.body.command)),{choose:{freestyle:{selectionId:'selection-1',phaseId:'phase-1',targetKey:'own',attribute:'ICE'}}});
});
