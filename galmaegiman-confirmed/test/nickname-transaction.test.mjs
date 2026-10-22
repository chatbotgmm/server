// Isolated actual service-method tests. No database/server integration is implied.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {normalizeNickname, nicknameError, needsInitialNickname} from '../src/nickname.ts';
const source=readFileSync(new URL('../src/game.ts',import.meta.url),'utf8');
const method=source.slice(source.indexOf('  async webInitialNickname('),source.indexOf('  // 단계는 앞으로만'));
class GameError extends Error {}
class UniqueError extends Error { code='P2002'; }
const Prisma={PrismaClientKnownRequestError:UniqueError};
const Subject=new Function('normalizeNickname','nicknameError','needsInitialNickname','GameError','Prisma',stripTypeScriptTypes(`class Subject {${method}}`)+";return Subject;")(normalizeNickname,nicknameError,needsInitialNickname,GameError,Prisma);
function fixture(extra={}, duplicate=false, race=false) {
 const player={id:'new',nickname:null,gameTutorial:1,gameSeen:'',...extra};
 const calls=[]; const s=new Subject();
 s.audit=async(...args)=>calls.push(['audit',args[2]]);
 s.webRun=async(id,run)=>run({player:{findUnique:async()=>duplicate?{id:'other'}:null, update:async({data})=>{if(race)throw new UniqueError();Object.assign(player,data);calls.push(['update',data]);}}},player);
 return {s,player,calls};
}
test('persists normalized name once and repeat request is idempotent',async()=>{
 const {s,player,calls}=fixture();
 assert.deepEqual(await s.webInitialNickname('new',' ＡＢ  대원 '),{nickname:'AB 대원'});
 assert.deepEqual(await s.webInitialNickname('new','AB 대원'),{nickname:'AB 대원'});
 assert.equal(player.nickname,'AB 대원');assert.equal(player.gameTutorial,1);assert.equal(player.gameSeen,'');
 assert.equal(calls.filter(c=>c[0]==='update').length,1);assert.equal(calls.filter(c=>c[0]==='audit').length,1);
});
test('does not rename established players or modify started tutorial',async()=>{
 for(const extra of [{nickname:'기존이름'},{gameTutorial:9},{gameSeen:'tut'}]){
  const {s,calls}=fixture(extra); await assert.rejects(s.webInitialNickname('new','새이름'),GameError);assert.equal(calls.length,0);
 }
});
test('rejects invalid inputs and maps duplicate and unique constraint race',async()=>{
 for(const value of [null,{},'a','관리자','닉!네임']) await assert.rejects(fixture().s.webInitialNickname('new',value),GameError);
 for(const f of [fixture({},true),fixture({},false,true)]) {await assert.rejects(f.s.webInitialNickname('new','모험가'),GameError);assert.equal(f.calls.length,0);}
});
const tutorialMethod=source.slice(source.indexOf('  async webTutorial('),source.indexOf('  // Initial setup only:'));
const TutorialSubject=new Function('needsInitialNickname','GameError',stripTypeScriptTypes(`class TutorialSubject {${tutorialMethod}}`)+";return TutorialSubject;")(needsInitialNickname,GameError);
test('tutorial first-load initializes only fresh users; existing inventory/story users stay legacy',async()=>{
 for(const old of ['none','story','inventory']){
  const p={gameTutorial:0,gameSeen:'',nickname:null};const s=new TutorialSubject();
  s.db={player:{findUnique:async()=>({...p}),update:async({data})=>Object.assign(p,data)},storySave:{count:async()=>old==='story'?1:0},ownedCharacter:{count:async()=>old==='inventory'?1:0}};
  s.webRun=async(id,run)=>{assert.equal(id,'new');return run(s.db,p);};
  const state=await s.webTutorial('new');assert.equal(state.nicknameRequired,old==='none');assert.equal(state.step,old==='none'?1:9);assert.equal(p.starterLoanEnrolled,old==='none');
 }
});
