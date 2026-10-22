// Dependency-free focused source tests; full Prisma/Vitest suite remains required.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import {applyLoanLevel} from '../src/starter-loan-levels.ts';
const source=readFileSync(new URL('../src/pvp/service.ts',import.meta.url),'utf8');
const method=(from,to)=>source.slice(source.indexOf(from),source.indexOf(to,source.indexOf(from)));
const body=method('  private async roster(','  async join(')+method('  private async match(','  private async createCasualBotRoom(')+method('  private async preparationOptions(','  private async beginBattle(')+method('  private async beginBattle(','  private side(');
class GameError extends Error{}
const Gate=vm.runInNewContext(stripTypeScriptTypes(`(class Gate {${body}})`),{applyLoanLevel,loanLevelFloors:async()=>new Map(),casualRecord(_R,base,saved,start){return {...base,level:start,stage:0,...saved}},GameError,Date,Set,Map,JSON,Math,topTiers:new Set(['LIMITED','TRANSCEND','ETERNAL','IMMORTAL']),fail(message){throw new GameError(message)},REALTIME_LIMITS:{maxWaiting:2000},mapRealtimePreparationCandidates(content,ids){return [...new Set(ids)].map(id=>({id,...content.map.get(id)}))}});
const expires=5000;
function fixture(now=4999){
 const calls=[],gate=new Gate();gate.now=()=>now;gate.R={data:{growth:{maxLevel:60}}};
 gate.content={map:new Map(['D1','D2','D3'].map(id=>[id,{id,name:id,rarity:'LIMITED'}])),economy:{pvp:{level:15}}};
 const units=[{id:1,playerId:'a',characterId:'D1',loanExpiresAt:null},{id:2,playerId:'a',characterId:'D2',loanExpiresAt:new Date(expires)},{id:3,playerId:'a',characterId:'D3',loanExpiresAt:null},{id:4,playerId:'a',characterId:'D2',loanExpiresAt:null},{id:5,playerId:'b',characterId:'D2',loanExpiresAt:null}].map(x=>({...x,reincarnation:0,inheritedFrom:null,statBonusPct:0}));
 const tx={player:{findUniqueOrThrow:async({where})=>({id:where.id,battleTeam:'D1,D2,D3',nickname:'a'})},realtimeLineup:{findUnique:async()=>null},ownedCharacter:{findMany:async({where})=>{calls.push(where);return units.filter(x=>x.playerId===where.playerId&&(!where.OR||x.loanExpiresAt===null||x.loanExpiresAt>where.OR[1].loanExpiresAt.gt))}},storySave:{findUnique:async()=>null}};
 return {gate,tx,calls};
}
test('직전 허용·정각 만료, 영구 동종 개체 유지',async()=>{
 const before=fixture();const r=await before.gate.roster(before.tx,'a',['o1','o2','o3']);assert.equal(r.units[1].temporary,true);assert.equal(r.units[1].expiresAt,expires);
 const at=fixture(expires);await assert.rejects(()=>at.gate.roster(at.tx,'a',['o1','o2','o3']),GameError);
 const permanent=await at.gate.roster(at.tx,'a',['o1','o4','o3']);assert.equal(permanent.units[1].temporary,false);assert.equal(permanent.units[1].expiresAt,null);
});
test('다른 소유자 개체는 편성 불가',async()=>{const {gate,tx}=fixture();await assert.rejects(()=>gate.roster(tx,'a',['o1','o5','o3']),GameError);});
test('랭크 준비 후보도 개체 단위 만료 메타데이터',async()=>{const {gate,tx}=fixture();const options=await gate.preparationOptions(tx,'a','rank');assert.equal(options.find(x=>x.id==='o2').temporary,true);assert.equal(options.find(x=>x.id==='o4').temporary,false);});
test('대기 도중 만료 시 매칭 생성 전에 EXPIRED 처리',async()=>{
 const {gate,tx}=fixture(expires);let expired=false,released=false;
 gate.hasProtocol=()=>true;gate.attemptUnits=()=>[{uid:'o1'},{uid:'o2'},{uid:'o3'}];
 tx.realtimeAttempt={findMany:async()=>[{id:'q',playerId:'a'}],update:async({data})=>{expired=data.state==='EXPIRED'}};
 tx.realtimeActivity={findMany:async()=>[{playerId:'a',targetId:'q'}],deleteMany:async()=>{released=true}};
 await gate.match(tx,'rank');assert.equal(expired,true);assert.equal(released,true);
});
test('native 정산은 캡처한 개체로 처리하며 신규 전투는 만료 제외',()=>{
 const n=readFileSync(new URL('../src/native.ts',import.meta.url),'utf8');
 assert.match(n,/o=await mine\(id,true\),won=/);assert.match(n,/const r=structuredClone\(fighter.rec\),before=r.level/);
 assert.match(n,/s.team=s.team.filter\(k=>o.has\(k\)\);let team=/);
 assert.match(n,/if\(r.temporary\)throw new GameError/);
});

test('최종 준비 완료 시 다시 만료 검증하여 실제 전투를 시작하지 않음',async()=>{
 const {gate,tx}=fixture(expires);let created=false;gate.battles={restart(){created=true;throw Error('unexpected')}};
 await assert.rejects(()=>gate.beginBattle(tx,{player0Id:'a',player1Id:'b',mode:'rank'},{preparation:{teams:[[{uid:'o1'},{uid:'o2'},{uid:'o3'}],[]]}}),GameError);assert.equal(created,false);
});
test('동종 2개체를 넣어도 PvP 3종 규칙 유지',async()=>{const {gate,tx}=fixture();await assert.rejects(()=>gate.roster(tx,'a',['o1','o2','o4']),GameError);});

test('casual actual roster/preparation methods never use species growth for loan; permanent keeps legacy fallback',async()=>{
 const {gate,tx}=fixture();gate.startLevel=1;
 tx.storySave.findUnique=async()=>({data:JSON.stringify({units:{D2:{level:99,stage:3,evoAugs:['forged']}}})});
 const loan=await gate.roster(tx,'a',['o1','o2','o3'],'normal');assert.equal(loan.units[1].level,1);assert.equal(loan.units[1].stage,0);
 const permanent=await gate.roster(tx,'a',['o1','o4','o3'],'normal');assert.equal(permanent.units[1].level,99);
 const candidates=await gate.preparationOptions(tx,'a','normal');assert.equal(candidates.find(x=>x.id==='o2').level,1);assert.equal(candidates.find(x=>x.id==='o4').level,99);
});
