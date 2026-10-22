import test from 'node:test';
import assert from 'node:assert/strict';
import {checkpointLoanLevels,bindLoanLevelRecipient,loanLevelFloors,applyLoanLevel} from '../src/starter-loan-levels.ts';
function fixture(){
 const archive={loanOwnedId:10,playerId:'a',characterId:'D2',grantId:'receipt-a',highestLevel:1,recipientOwnedId:null,lastSession:null,createdAt:new Date(100),updatedAt:new Date(100)};
 const audits=[];const tx={starterLoanLevel:{findUnique:async({where})=>where.loanOwnedId===10?structuredClone(archive):null,findMany:async({where})=>where.playerId!=='a'||(where.characterId&&where.characterId!=='D2')||(where.recipientOwnedId===null&&archive.recipientOwnedId!==null)?[]:[structuredClone(archive)],updateMany:async({where,data})=>{if(where.recipientOwnedId===null&&archive.recipientOwnedId!==null||where.highestLevel&&archive.highestLevel>=where.highestLevel.lt)return {count:0};Object.assign(archive,data);return {count:1};}},ownedCharacter:{findFirst:async({where})=>where.id===10&&where.playerId==='a'?{id:10,loanExpiresAt:new Date(500)}:null},auditEvent:{create:async({data})=>audits.push(data)}};
 const permanent=(id=20,characterId='D2')=>({id,characterId,obtainedAt:new Date(600),loanExpiresAt:null});
 return {tx,archive,audits,permanent};
}
test('expired loan level survives independently and first later same species receives level only',async()=>{
 const {tx,archive,permanent}=fixture();await checkpointLoanLevels(tx,'a','battle-1',[{ownedId:10,level:22}],60,new Date(501));
 await bindLoanLevelRecipient(tx,'a',permanent(),new Date(600));assert.equal(archive.recipientOwnedId,20);
 const floors=await loanLevelFloors(tx,'a',[20]);const original={level:1,xp:9,stage:0,learned:['basic'],evoAugs:[]};const next=applyLoanLevel(original,floors.get(20),60);
 assert.deepEqual(next,{...original,level:22,xp:0});assert.equal(original.level,1);
});
test('grant while active binds once; later trusted loan levels update same recipient not every copy',async()=>{
 const {tx,archive,permanent,audits}=fixture();await bindLoanLevelRecipient(tx,'a',permanent(),new Date(200));
 await checkpointLoanLevels(tx,'a','battle-2',[{ownedId:10,level:31}],60,new Date(300));
 await bindLoanLevelRecipient(tx,'a',permanent(21),new Date(400));await bindLoanLevelRecipient(tx,'a',permanent(20),new Date(400));
 assert.equal(archive.recipientOwnedId,20);assert.equal(archive.highestLevel,31);assert.equal(audits.filter(x=>x.kind==='STARTER_LOAN_LEVEL_BIND').length,1);
 const f=await loanLevelFloors(tx,'a',[20,21]);assert.equal(f.get(20),31);assert.equal(f.has(21),false);
});
test('higher permanent level and own XP are unchanged; transfer is max not addition',()=>{
 const original={level:40,xp:80,stage:2,evoAugs:['own'],learned:['own']};assert.equal(applyLoanLevel(original,22,60),original);assert.equal(applyLoanLevel(applyLoanLevel(original,55,60),55,60).level,55);
});
test('wrong species/owner/temporary recipients do not inherit',async()=>{
 const {tx,archive,permanent}=fixture();await bindLoanLevelRecipient(tx,'a',permanent(20,'D3'),new Date(600));await bindLoanLevelRecipient(tx,'b',permanent(),new Date(600));await bindLoanLevelRecipient(tx,'a',{...permanent(),loanExpiresAt:new Date(900)},new Date(600));assert.equal(archive.recipientOwnedId,null);
 await assert.rejects(()=>checkpointLoanLevels(tx,'b','forged',[{ownedId:10,level:20}],60,new Date()));
});
test('checkpoint caps, monotonic updates and replay do not duplicate or lower archive',async()=>{
 const {tx,archive,audits}=fixture();await checkpointLoanLevels(tx,'a','battle-3',[{ownedId:10,level:30}],60,new Date(600));await checkpointLoanLevels(tx,'a','battle-3',[{ownedId:10,level:30}],60,new Date(601));await checkpointLoanLevels(tx,'a','old',[{ownedId:10,level:20}],60,new Date(602));assert.equal(archive.highestLevel,30);assert.equal(audits.length,1);
 for(const level of [0,61,NaN,2.5])await assert.rejects(()=>checkpointLoanLevels(tx,'a','bad',[{ownedId:10,level}],60,new Date()));
});
test('active battle captured snapshot stays unchanged until next roster creation',async()=>{
 const {tx,permanent}=fixture();const captured={level:5,xp:2,stage:0};await bindLoanLevelRecipient(tx,'a',permanent(),new Date(200));await checkpointLoanLevels(tx,'a','battle-4',[{ownedId:10,level:25}],60,new Date(600));const floors=await loanLevelFloors(tx,'a',[20]);const next=applyLoanLevel(captured,floors.get(20),60);assert.equal(captured.level,5);assert.equal(next.level,25);
});

test('production settlement checkpoints archive, legacy tamper/reset and expiry cleanup cannot replace it',async()=>{
 const {readFileSync}=await import('node:fs');const {stripTypeScriptTypes}=await import('node:module');const vm=await import('node:vm');
 const {activeLoanWhere,pruneExpiredLoanReferences}=await import('../src/starter-loans.ts');
 const source=readFileSync(new URL('../src/game.ts',import.meta.url),'utf8');const methods=source.slice(source.indexOf('  private async saveStory('),source.indexOf('  async webProgression('));
 const receipts=new Map();const Service=vm.runInNewContext(stripTypeScriptTypes(`(class Service {${methods}})`),{structuredClone,Set,Map,JSON,Object,activeLoanWhere,checkpointLoanLevels,loadBattleRules:()=>({data:{growth:{maxLevel:60}}}),awardPlayPoints:async()=>{},progressionReceipt:async(_tx,_p,key,_a,_payload,fn)=>{if(receipts.has(key))return receipts.get(key);const result=await fn();receipts.set(key,result);return result;}});
 const {tx,archive}=fixture();let saved=null;tx.storySave={findUnique:async()=>saved,upsert:async({create,update})=>{saved={data:saved?update.data:create.data}},update:async({data})=>{saved=data},deleteMany:async()=>{saved=null}};
 tx.ownedCharacter.findMany=async({where})=>where.loanExpiresAt||where.id?[{id:10,characterId:'D2'}]:[];tx.ownedCharacter.updateMany=async()=>({count:0});tx.player={update:async()=>({})};tx.realtimeLineup={findUnique:async()=>null};
 const service=new Service();service.clock=()=>new Date(400);service.topIds=['D2'];service.webRun=async(_id,fn)=>fn(tx,{id:'a',isAdmin:false,adminMode:false},new Date(400));
 const data={units:{o10:{level:18,xp:99,stage:0}},team:['o10'],cleared:[],hp:{}};
 await service.webNativeCommit('a',undefined,data,{session:'real-native-session',points:3,loanLevels:[{ownedId:10,level:18}]});assert.equal(archive.highestLevel,18);assert.equal(archive.lastSession,'real-native-session');
 await service.webSaveStory('a',{...data,units:{o10:{level:60,xp:999}}});assert.equal(archive.highestLevel,18);assert.equal(JSON.parse(saved.data).units.o10.level,18);
 service.webRun=async(_id,fn)=>fn(tx,{id:'a',isAdmin:true,adminMode:true},new Date(450));
 await service.webNativeCommit('a',undefined,{...data,units:{o10:{level:59,xp:999,stage:0}}},{session:'admin-trial',points:3,loanLevels:[{ownedId:10,level:59}]});assert.equal(archive.highestLevel,18);assert.equal(JSON.parse(saved.data).units.o10.level,18);
 service.webRun=async(_id,fn)=>fn(tx,{id:'a',isAdmin:false,adminMode:false},new Date(460));
 await service.webSaveStory('a',null);assert.equal(archive.highestLevel,18);
 await pruneExpiredLoanReferences(tx,{id:'a',battleTeam:'D2'},new Date(900));assert.equal(JSON.parse(saved.data).units.o10,undefined);assert.equal(archive.highestLevel,18);
 // A replayed settlement cannot mint a second checkpoint even if its caller retries.
 await service.webNativeCommit('a',undefined,data,{session:'real-native-session',points:3,loanLevels:[{ownedId:10,level:50}]});assert.equal(archive.highestLevel,18);
});
