import {describe,it,expect} from 'vitest';
import {roster60LossFeedback,type Roster60LossFeedbackInput} from '../src/battle/roster60-loss-feedback.js';
import {capturePresentation,presentStep} from '../src/battle/roster60-presentation.js';
import {fiveStatAttack} from '../src/battle/roster60-five-combat.js';

// Structural fixtures use existing snapshot/damage helpers; these coefficients are not balance policy.
const calculation={model:'linear-level-hyperbolic-v1' as const,version:'test-only',levelSlope:0,durabilityScale:100,resistanceScale:100};
const fighter=(speciesId:string,hp=30)=>({speciesId,name:'not copied',level:1,hp,mp:10,stats:{hp:100,mp:40}});
function knockout({viewerSide=0,reflected=false,enemyFirst=true,turn=2}: {viewerSide?:0|1;reflected?:boolean;enemyFirst?:boolean;turn?:number}={}) {
 const pair=[fighter('u01'),fighter('u02')],before=capturePresentation(pair),actor=reflected?viewerSide:(1-viewerSide) as 0|1;
 const hit=fiveStatAttack({power:100,level:1,durability:0,suitability:0,defense:reflected?'reflect':'normal',attackerHp:pair[actor].hp,defenderHp:pair[1-actor].hp},calculation,(base,fit)=>base*fit);
 pair[hit.target==='attacker'?actor:1-actor].hp-=hit.damage;
 const event={side:actor,kind:'skill',skillId:'private-skill-id',...hit,presentation:presentStep(before,pair,{id:'private-skill-id',name:'not copied',element:'FIRE',mpCost:3})};
 const first=enemyFirst?1-viewerSide:viewerSide;
 return {turn,decision:{version:'five-speed-v1',turn,order:[`p${first}`,`p${1-first}`],speeds:[777,123],secret:'not copied'},events:[event]};
}
function input(overrides:Partial<Roster60LossFeedbackInput>={}):Roster60LossFeedbackInput {
 return {state:{events:[knockout()]},viewerSide:0,mode:'normal',outcome:'loss',reason:'BATTLE',status:'FINISHED',opponentKind:'HUMAN',...overrides};
}
const feedback=(overrides:Partial<Roster60LossFeedbackInput>={})=>roster60LossFeedback(input(overrides));

describe('server-observed roster60 loss feedback',()=>{
 it('reports one verified opponent-first KO and one experiment without claiming speed caused the defeat',()=>{
  const result=feedback();
  expect(result).toMatchObject({version:'roster60-loss-feedback-v1',mode:'normal',opponentKind:'HUMAN',kind:'battle',explanationKey:'ENEMY_FIRST_KO',nextAttemptKey:'TRY_SWITCH_OR_HEAL_EARLIER',
   fact:{kind:'enemy-first-ko',turn:2,roundIndex:0,eventIndex:0,hpBefore:30,hpAfter:0,damage:30},replay:{available:true,turn:2,roundIndex:0,eventIndex:0}});
  expect(JSON.stringify(result)).not.toMatch(/speed|level|MP|777|123/);
 });
 it('reports only the executed reflection attribute and exact self damage/KO',()=>{
  const result=feedback({state:{events:[knockout({reflected:true})]}});
  expect(result).toMatchObject({explanationKey:'REFLECTED_KO',nextAttemptKey:'TRY_DIFFERENT_ELEMENT',fact:{kind:'reflected-ko',element:'FIRE',hpBefore:30,hpAfter:0,damage:30}});
  expect(result?.fact).not.toHaveProperty('defense');
 });
 it.each([0,1] as const)('interprets raw p0/p1 snapshots for viewer %s without leaking side identities',viewerSide=>{
  expect(feedback({viewerSide,state:{events:[knockout({viewerSide})]}})?.explanationKey).toBe('ENEMY_FIRST_KO');
  expect(feedback({viewerSide,state:{events:[knockout({viewerSide,reflected:true})]}})?.explanationKey).toBe('REFLECTED_KO');
 });
 it('supports AI and human losses with the same evidence rules and explicit opponent kind',()=>{
  const human=feedback(),ai=feedback({opponentKind:'AI'});
  expect(ai).toEqual({...human,opponentKind:'AI'});
  expect(feedback({mode:'rank'})?.mode).toBe('rank');
 });
 it('does not turn unproved order, ties or a prior own action into a speed diagnosis',()=>{
  const missing=knockout();delete (missing as {decision?:unknown}).decision;
  expect(feedback({state:{events:[missing]}})?.explanationKey).toBe('DIRECT_KO');
  expect(feedback({state:{events:[knockout({enemyFirst:false})]}})?.explanationKey).toBe('DIRECT_KO');
  const prior=knockout();prior.events.unshift({...prior.events[0],side:0,kind:'focus',damage:0,
   presentation:presentStep(prior.events[0].presentation.before,prior.events[0].presentation.before)});
  expect(feedback({state:{events:[prior]}})?.explanationKey).toBe('DIRECT_KO');
  const tie=knockout();Object.assign(tie.decision,{tie:true,speeds:[5,5]});
  expect(feedback({state:{events:[tie]}})?.explanationKey).toBe('ENEMY_FIRST_KO');
  expect(JSON.stringify(feedback({state:{events:[tie]}}))).not.toContain('speeds');
 });
 it('selects the latest verified KO deterministically instead of an older reflection',()=>{
  const state={events:[knockout({reflected:true,turn:1}),knockout({turn:3})]},original=JSON.stringify(state);
  const result=feedback({state});
  expect(result?.explanationKey).toBe('ENEMY_FIRST_KO');expect(result?.fact?.turn).toBe(3);expect(result?.fact?.roundIndex).toBe(1);
  expect(feedback({state})).toEqual(result);expect(JSON.stringify(state)).toBe(original);
 });
 it.each(['win','draw',null] as const)('invents no combat cause for BATTLE outcome %s',outcome=>{
  expect(feedback({outcome})).toBeNull();
 });
 it.each(['ACTIVE','ACCEPTING','UNKNOWN'])('has no final feedback while status is %s',status=>expect(feedback({status})).toBeNull());
 it('provides a record fallback for absent, legacy, malformed or contradictory evidence',()=>{
  const mismatch=knockout();mismatch.events[0].damage=999;
  const switched=knockout();switched.events[0].presentation.after[0].speciesId='u09';
  const malformed=knockout();malformed.events[0].presentation.after[0].hp=NaN;
  const wrongVersion=knockout();(wrongVersion.events[0].presentation as {version:number}).version=2;
  for(const state of [null,{}, {events:'private'}, {events:[{turn:0,events:[null,1,'bad']} ]}, {events:[mismatch]}, {events:[switched]}, {events:[malformed]}, {events:[wrongVersion]}])
   expect(feedback({state})).toMatchObject({kind:'record',explanationKey:'INSUFFICIENT_RECORD',nextAttemptKey:'REVIEW_REPLAY'});
  expect(feedback({state:null})?.replay.available).toBe(false);
 });
 it('rejects cancelled, missed and nonlethal attacks as reflection KO evidence',()=>{
  for(const patch of [{cancelled:true},{executed:false},{miss:true},{hit:false},{reflected:false},{target:'defender'},{defense:'normal'}]){
   const round=knockout({reflected:true});Object.assign(round.events[0],patch);
   expect(feedback({state:{events:[round]}})?.explanationKey).toBe('INSUFFICIENT_RECORD');
  }
  const round=knockout({reflected:true});round.events[0].presentation.after[0].hp=1;round.events[0].damage=29;
  expect(feedback({state:{events:[round]}})?.explanationKey).toBe('INSUFFICIENT_RECORD');
 });
 it('does not invent MP exhaustion or low levels from vitals, private stats or rejected-command-shaped data',()=>{
  const own=fighter('u01'),enemy=fighter('u02');own.mp=0;own.level=1;enemy.level=99;
  const p=presentStep(capturePresentation([own,enemy]),[own,enemy]);
  const state={events:[{turn:1,events:[{side:0,kind:'focus',recovered:0,presentation:p},{side:0,kind:'rejected',code:'INSUFFICIENT_MP',presentation:p}]}],
   parties:[[own],[{...enemy,defense:{FIRE:'reflect'},stats:{speed:1000}}]],pending:[{kind:'skill',skillId:'secret'},null]};
  expect(feedback({state})).toMatchObject({explanationKey:'INSUFFICIENT_RECORD',nextAttemptKey:'REVIEW_REPLAY',replay:{available:true}});
  expect(feedback({state})?.fact).toBeUndefined();
 });
 it('does not infer reflection from an undisclosed defense table or accept an unrecognized attribute',()=>{
  const state={events:[],parties:[[],[{defense:{DARK:'reflect'}}]]};
  expect(feedback({state})?.explanationKey).toBe('INSUFFICIENT_RECORD');
  const round=knockout({reflected:true});round.events[0].presentation.skill!.element='private-element';
  expect(feedback({state:{events:[round]}})?.explanationKey).toBe('INSUFFICIENT_RECORD');
 });
 it.each(['SURRENDER','TIMEOUT','DOUBLE_TIMEOUT','SERVER_ABORT','MATCH_TIMEOUT','MATCH_DECLINED'] as const)('keeps %s as a neutral terminal reason, even when a prior KO exists',reason=>{
  const result=feedback({reason});
  expect(result).toMatchObject({kind:'termination',reason,explanationKey:reason,nextAttemptKey:'REVIEW_REPLAY',replay:{available:true}});
  expect(result?.fact).toBeUndefined();
 });
 it('distinguishes separately verified disconnection, input timeout and observation gaps without guessing from misses',()=>{
  expect(feedback({reason:'TIMEOUT',state:{misses:[3,0],events:[]}})?.explanationKey).toBe('TIMEOUT');
  expect(feedback({reason:'TIMEOUT',terminationKind:'DISCONNECTED'})).toMatchObject({reason:'TIMEOUT',explanationKey:'DISCONNECTED',nextAttemptKey:'CHECK_CONNECTION_AND_REPLAY'});
  expect(feedback({reason:'TIMEOUT',terminationKind:'TURN_INPUT'})).toMatchObject({explanationKey:'TURN_INPUT_TIMEOUT',nextAttemptKey:'CHECK_TURN_DEADLINE_AND_REPLAY'});
  expect(feedback({reason:'SERVER_ABORT',terminationKind:'SERVER_OBSERVATION_GAP'})).toMatchObject({explanationKey:'SERVER_OBSERVATION_GAP',nextAttemptKey:'REVIEW_REPLAY'});
  expect(feedback({reason:'SERVER_ABORT',terminationKind:'TURN_INPUT'})?.explanationKey).toBe('SERVER_ABORT');
  expect(feedback({terminationKind:'DISCONNECTED'})?.explanationKey).toBe('ENEMY_FIRST_KO');
  expect(feedback({reason:'SURRENDER',terminationKind:'DISCONNECTED'})?.explanationKey).toBe('SURRENDER');
 });
 it('reports termination neutrally for a winner/draw without inventing their loss',()=>{
  expect(feedback({reason:'SURRENDER',outcome:'win'})).toMatchObject({kind:'termination',outcome:'win',explanationKey:'SURRENDER'});
  expect(feedback({reason:'DOUBLE_TIMEOUT',outcome:'draw'})).toMatchObject({kind:'termination',outcome:'draw',explanationKey:'DOUBLE_TIMEOUT'});
 });
 it('allowlists the returned DTO and never serializes names, peer IDs, defense maps, reserves, seeds or raw logs',()=>{
  const secret='NEVER-LEAVE-SERVER',round=knockout({reflected:true});
  Object.assign(round,{seed:secret,rawLogs:secret});Object.assign(round.events[0],{targetOwnedId:secret,defenseObservation:{targetKey:secret,profileKey:secret},intelBefore:[{private:secret}]});
  for(const frame of [...round.events[0].presentation.before,...round.events[0].presentation.after])Object.assign(frame,{name:secret,ownedId:secret,defense:{DARK:secret},reserves:[secret]});
  const state={events:[round],seed:secret,player1Id:secret,parties:[[],[{ownedId:secret,defense:{DARK:secret},reserves:[secret]}]],rawLogs:secret,pending:[null,{secret}]};
  const result=feedback({state});expect(result?.explanationKey).toBe('REFLECTED_KO');
  const json=JSON.stringify(result);expect(json).not.toContain(secret);expect(json).not.toMatch(/defense|reserves|seed|rawLogs|ownedId|skillId|intel|name|player1Id|pending/);
  expect(Object.keys(result!.fact!).sort()).toEqual(['damage','element','eventIndex','hpAfter','hpBefore','kind','roundIndex','turn']);
  expect(Object.keys(result!.replay).sort()).toEqual(['available','eventIndex','roundIndex','turn']);
 });
 it('rejects unsupported terminal reasons and invalid viewer boundaries',()=>{
  expect(feedback({reason:'private-reason'})).toBeNull();
  expect(feedback({viewerSide:2 as 0})).toBeNull();
 });
});
