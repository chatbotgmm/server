import {skillElements,type SkillElement} from './roster60-rules.js';

type Side=0|1;
type Outcome='win'|'loss'|'draw'|null;
export type Roster60FeedbackTerminationKind='DISCONNECTED'|'TURN_INPUT'|'SERVER_OBSERVATION_GAP';
export type Roster60FeedbackReason='BATTLE'|'SURRENDER'|'TIMEOUT'|'DOUBLE_TIMEOUT'|'SERVER_ABORT'|'MATCH_TIMEOUT'|'MATCH_DECLINED';
export type Roster60LossExplanationKey='REFLECTED_KO'|'ENEMY_FIRST_KO'|'DIRECT_KO'|'INSUFFICIENT_RECORD'|
 'SURRENDER'|'TIMEOUT'|'DOUBLE_TIMEOUT'|'SERVER_ABORT'|'MATCH_TIMEOUT'|'MATCH_DECLINED'|
 'DISCONNECTED'|'TURN_INPUT_TIMEOUT'|'SERVER_OBSERVATION_GAP';
export type Roster60NextAttemptKey='TRY_DIFFERENT_ELEMENT'|'TRY_SWITCH_OR_HEAL_EARLIER'|'REVIEW_LAST_TURN'|
 'REVIEW_REPLAY'|'CHECK_CONNECTION_AND_REPLAY'|'CHECK_TURN_DEADLINE_AND_REPLAY';
export type Roster60LossFeedbackInput={
 /** Server-owned raw PvP state, before the viewer projection. Never accept a client replay. */
 state:unknown;viewerSide:Side;mode:'normal'|'rank';outcome:Outcome;reason:unknown;status:string;opponentKind:'AI'|'HUMAN';
 /** Supply only when separate server evidence proves this category. Miss counters do not prove disconnection. */
 terminationKind?:Roster60FeedbackTerminationKind;
};
type ReplayLocation={turn:number;roundIndex:number;eventIndex:number};
export type Roster60LossFeedbackFact=ReplayLocation&{
 kind:'reflected-ko'|'enemy-first-ko'|'direct-ko';hpBefore:number;hpAfter:0;damage:number;element?:SkillElement;
};
export type Roster60LossFeedback={
 version:'roster60-loss-feedback-v1';kind:'battle'|'termination'|'record';mode:'normal'|'rank';outcome:Outcome;
 opponentKind:'AI'|'HUMAN';reason:Roster60FeedbackReason;explanationKey:Roster60LossExplanationKey;
 nextAttemptKey:Roster60NextAttemptKey;fact?:Roster60LossFeedbackFact;
 replay:{available:boolean;turn?:number;roundIndex?:number;eventIndex?:number};
};
type ObjectValue=Record<string,unknown>;
type Frame={hp:number;speciesId:string};
type Step={before:[Frame,Frame];after:[Frame,Frame];element:SkillElement|null};
const reasons=new Set<string>(['BATTLE','SURRENDER','TIMEOUT','DOUBLE_TIMEOUT','SERVER_ABORT','MATCH_TIMEOUT','MATCH_DECLINED']);
const object=(value:unknown):ObjectValue|null=>value!==null&&typeof value==='object'&&!Array.isArray(value)?value as ObjectValue:null;
const nonnegativeInteger=(value:unknown):value is number=>typeof value==='number'&&Number.isSafeInteger(value)&&value>=0;

function frames(value:unknown):[Frame,Frame]|null {
 if(!Array.isArray(value)||value.length!==2)return null;
 const pair=value.map(object);
 if(pair.some(f=>!f||!nonnegativeInteger(f.hp)||typeof f.speciesId!=='string'||!f.speciesId))return null;
 // Read only public active-fighter vitals. Never carry a raw frame to the returned DTO.
 return pair.map(f=>({hp:f!.hp as number,speciesId:f!.speciesId as string})) as [Frame,Frame];
}
function step(value:unknown):Step|null {
 const presentation=object(value);if(presentation?.version!==1)return null;
 const before=frames(presentation.before),after=frames(presentation.after);if(!before||!after)return null;
 const rawElement=object(presentation.skill)?.element;
 return {before,after,element:skillElements.includes(rawElement as SkillElement)?rawElement as SkillElement:null};
}
function isExecutedSkill(event:ObjectValue):boolean {
 return event.kind==='skill'&&event.executed!==false&&event.hit!==false&&event.miss!==true&&event.missed!==true&&
  event.cancelled!==true&&(event.outcome===undefined||event.outcome==='hit');
}
function opponentActedFirst(round:ObjectValue,eventIndex:number,viewerSide:Side):boolean {
 const decision=object(round.decision),order=decision?.order,events=round.events as unknown[];
 return decision?.version==='five-speed-v1'&&decision.turn===round.turn&&Array.isArray(order)&&order.length===2&&
  order[0]===`p${1-viewerSide}`&&order[1]===`p${viewerSide}`&&
  !events.slice(0,eventIndex).some(e=>object(e)?.side===viewerSide);
}
function koFact(round:ObjectValue,event:ObjectValue,location:ReplayLocation,viewerSide:Side,p:Step):Roster60LossFeedbackFact|null {
 if(!isExecutedSkill(event)||!nonnegativeInteger(event.damage)||event.damage===0)return null;
 const other=(1-viewerSide) as Side,before=p.before[viewerSide].hp,after=p.after[viewerSide].hp;
 if(before===0||p.before[other].hp===0||after!==0||event.damage!==before-after||
  p.before.some((f,i)=>f.speciesId!==p.after[i].speciesId)||p.before[other].hp!==p.after[other].hp)return null;
 const fact={...location,hpBefore:before,hpAfter:0 as const,damage:event.damage};
 if(event.side===viewerSide&&event.target==='attacker'&&event.defense==='reflect'&&event.reflected===true&&p.element)
  return {...fact,kind:'reflected-ko',element:p.element};
 if(event.side!==other||event.target!=='defender'||event.reflected!==false||
  !['weak','normal','resist'].includes(String(event.defense)))return null;
 return {...fact,kind:opponentActedFirst(round,location.eventIndex,viewerSide)?'enemy-first-ko':'direct-ko'};
}

/** One observed scene and one optional next experiment, never a claim about the sole cause of defeat. */
export function roster60LossFeedback(input:Roster60LossFeedbackInput):Roster60LossFeedback|null {
 if(!['FINISHED','SETTLED'].includes(input.status)||(input.viewerSide!==0&&input.viewerSide!==1)||
  !['normal','rank'].includes(input.mode)||!['AI','HUMAN'].includes(input.opponentKind)||
  ![null,'win','loss','draw'].includes(input.outcome)||typeof input.reason!=='string'||!reasons.has(input.reason))return null;
 const reason=input.reason as Roster60FeedbackReason;
 // Victory and normal draws get no invented combat cause. Other terminal reasons remain neutral notices.
 if(reason==='BATTLE'&&input.outcome!=='loss')return null;
 const rounds=object(input.state)?.events;
 let latestFact:Roster60LossFeedbackFact|null=null,lastReplay:ReplayLocation|null=null;
 if(Array.isArray(rounds))rounds.forEach((rawRound,roundIndex)=>{
  const round=object(rawRound);if(!round||!nonnegativeInteger(round.turn)||!Array.isArray(round.events))return;
  round.events.forEach((rawEvent,eventIndex)=>{
   const event=object(rawEvent);if(!event)return;const p=step(event.presentation);if(!p)return;
   const location={turn:round.turn as number,roundIndex,eventIndex};lastReplay=location;
   const fact=koFact(round,event,location,input.viewerSide,p);if(fact)latestFact=fact;
  });
 });
 const base={version:'roster60-loss-feedback-v1' as const,mode:input.mode,outcome:input.outcome,opponentKind:input.opponentKind,reason};
 const replay=(location:ReplayLocation|null)=>location?
  {available:true,turn:location.turn,roundIndex:location.roundIndex,eventIndex:location.eventIndex}:{available:false};
 if(reason!=='BATTLE'){
  let explanationKey:Roster60LossExplanationKey=reason,nextAttemptKey:Roster60NextAttemptKey='REVIEW_REPLAY';
  if(['TIMEOUT','DOUBLE_TIMEOUT','SERVER_ABORT'].includes(reason)){
   if(input.terminationKind==='DISCONNECTED'){explanationKey='DISCONNECTED';nextAttemptKey='CHECK_CONNECTION_AND_REPLAY';}
   else if(input.terminationKind==='TURN_INPUT'&&reason!=='SERVER_ABORT'){explanationKey='TURN_INPUT_TIMEOUT';nextAttemptKey='CHECK_TURN_DEADLINE_AND_REPLAY';}
   else if(input.terminationKind==='SERVER_OBSERVATION_GAP')explanationKey='SERVER_OBSERVATION_GAP';
  }
  return {...base,kind:'termination',explanationKey,nextAttemptKey,replay:replay(lastReplay)};
 }
 // Chronological append order selects the latest proved KO, rather than cherry-picking an older reflection.
 const fact=latestFact as Roster60LossFeedbackFact|null;
 if(!fact)return {...base,kind:'record',explanationKey:'INSUFFICIENT_RECORD',nextAttemptKey:'REVIEW_REPLAY',replay:replay(lastReplay)};
 const explanationKey=fact.kind==='reflected-ko'?'REFLECTED_KO':fact.kind==='enemy-first-ko'?'ENEMY_FIRST_KO':'DIRECT_KO';
 const nextAttemptKey=fact.kind==='reflected-ko'?'TRY_DIFFERENT_ELEMENT':fact.kind==='enemy-first-ko'?'TRY_SWITCH_OR_HEAL_EARLIER':'REVIEW_LAST_TURN';
 return {...base,kind:'battle',explanationKey,nextAttemptKey,fact,replay:replay(fact)};
}
