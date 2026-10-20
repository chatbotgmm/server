// Manual draft: place at src/battle/realtime-adapter.ts after the paired engine patch.
import type { Rules } from './core.js';
import { act, activeOf, chooseTogether, itemProblem, legalActions, rerollAugment, STRUGGLE, type Action, type Battle } from './engine.js';

export type Command={action?:Action;choose?:{augment?:string;switchTo?:number}};
type Side=0|1;
const sides=[0,1] as const;
const fail=():never=>{throw new Error('Invalid realtime command');};
function object(v:unknown):Record<string,unknown>{
  if(!v||typeof v!=='object'||Array.isArray(v))return fail();
  return v as Record<string,unknown>;
}
function keys(v:Record<string,unknown>,allowed:string[]){if(Object.keys(v).some(k=>!allowed.includes(k)))fail();}
export function serializeBattle(b:Battle):string{const {R,...state}=b;return JSON.stringify(state);}
export function deserializeBattle(R:Rules,json:string):Battle{
  const state=JSON.parse(json);
  if((state.effectsVersion===2)!==(R.augmentVersion===2))throw new Error('저장 전투와 규칙의 증강 버전이 달라요');
  // Trusted database payload only. Never deserialize a client-supplied battle.
  return {...state,R};
}
const clone=(b:Battle)=>deserializeBattle(b.R,serializeBattle(b));
export function required(b:Battle,side:Side):boolean{
  return b.phase==='action'||(b.phase==='choose'&&!!(b.sides[side].offers||b.sides[side].needSwitch));
}
function basic(b:Battle,side:Side):Action{
  const f=activeOf(b,side),legal=legalActions(b,side);
  const sk=f.skills.find(k=>k.id.startsWith('basic-')&&(k.power??0)>0&&legal.some(a=>a.kind==='skill'&&a.skillId===k.id));
  return {kind:'skill',skillId:sk?.id??STRUGGLE.id};
}
export function automaticCommand(b:Battle,side:Side):Command{
  if(b.phase==='over')return fail();
  if(b.phase==='action')return {action:basic(b,side)};
  const s=b.sides[side],pick:NonNullable<Command['choose']>={};
  if(s.offers){if(!s.offers.length)return fail();pick.augment=s.offers[0];}
  if(s.needSwitch){pick.switchTo=s.fighters.findIndex(f=>f.hp>0);if(pick.switchTo<0)return fail();}
  return {choose:pick};
}
function skillLegal(b:Battle,side:Side,id:unknown,trustedStruggle:boolean){
  if(typeof id!=='string')return fail();
  if(trustedStruggle&&id===STRUGGLE.id)return;
  if(!legalActions(b,side).some(a=>a.kind==='skill'&&a.skillId===id))fail();
}
function validate(b:Battle,side:Side,c:Command,trustedAutomatic:boolean):Action|undefined{
  const cmd=object(c);keys(cmd,['action','choose']);
  if(b.phase==='over')return fail();
  if(b.phase==='choose'){
    if('action' in cmd||!('choose' in cmd))return fail();
    const p=object(cmd.choose);keys(p,['augment','switchTo']);const s=b.sides[side];
    if(s.offers){if(typeof p.augment!=='string'||!s.offers.includes(p.augment))fail();}
    else if('augment' in p)fail();
    if(s.needSwitch){if(!Number.isInteger(p.switchTo)||typeof p.switchTo!=='number'||!s.fighters[p.switchTo]||s.fighters[p.switchTo].hp<=0)fail();}
    else if('switchTo' in p)fail();
    return;
  }
  if('choose' in cmd||!('action' in cmd))return fail();
  const a=object(cmd.action);
  if(a.kind==='skill'){
    keys(a,['kind','skillId']);skillLegal(b,side,a.skillId,trustedAutomatic);
    return {kind:'skill',skillId:a.skillId as string};
  }
  if(a.kind==='item'){
    keys(a,['kind','item','target']);
    if(typeof a.item!=='string'||!Object.prototype.hasOwnProperty.call(b.R.data.items,a.item))return fail();
    const item=b.R.data.items[a.item];
    if(item.revive){
      const target=a.target===undefined?b.sides[side].fighters.findIndex(f=>f.hp<=0):a.target;
      if(typeof target!=='number'||!Number.isInteger(target)||target<0||target>=b.sides[side].fighters.length)return fail();
      if(itemProblem(b,side,a.item,target))return fail();
      return {kind:'item',item:a.item,target};
    }
    // Healing/cleanse affect only the active fighter; never silently ignore a supplied target.
    if('target' in a||itemProblem(b,side,a.item))return fail();
    return {kind:'item',item:a.item};
  }
  if(a.kind!=='switch')return fail();
  keys(a,['kind','to','then']);
  if(!Number.isInteger(a.to)||!legalActions(b,side).some(x=>x.kind==='switch'&&x.to===a.to))return fail();
  const out=activeOf(b,side),s=b.sides[side],to=a.to as number;
  if(!s.augs.includes('freeswitch')){if('then' in a)fail();return {kind:'switch',to};}
  // Validate follow-up against our incoming fighter and the phase-start opponent.
  // Mirror the handoff heal, without executing or consuming RNG.
  const probe=clone(b);probe.sides[side].active=to;const incoming=activeOf(probe,side);
  if(out.passive.handoff)incoming.hp=Math.min(incoming.maxHp,incoming.hp+Math.round(incoming.maxHp*out.passive.handoff/100));
  const then=a.then; // Human free switches must explicitly choose the follow-up.
  skillLegal(probe,side,then,false);
  return {kind:'switch',to,then:then as string};
}
export function validateCommand(b:Battle,side:Side,c:Command):void{validate(b,side,c,false);}
// Inventory is NOT part of Battle. The service must debit/hold this exact item in
// the same transaction as accepting the command, then consume or refund the hold.
// Never call resolvePhase for an item without its authoritative persisted hold.
export function itemRequirement(b:Battle,side:Side,c:Command):{itemId:string;target?:number}|null{
  const a=validate(b,side,c,false);
  return a?.kind==='item'?{itemId:a.item,...(a.target===undefined?{}:{target:a.target})}:null;
}
// useItem increments this counter only when it actually executes. This remains
// reliable even if the bounded presentation event buffer truncates older events.
export function consumedItemSides(before:Battle,after:Battle):Array<0|1>{
  const used:Array<0|1>=[];
  for(const side of sides){const delta=after.sides[side].itemsUsed-before.sides[side].itemsUsed;
    if(delta!==0&&delta!==1)throw new Error('Invalid realtime item-use delta');
    if(delta===1)used.push(side);
  }
  return used;
}
export function rerollCommand(b:Battle,side:Side,index:number):Battle{
  if(!Number.isInteger(index)||index<0)fail();
  const next=clone(b);
  try{rerollAugment(next,side,index);}catch{fail();}
  return next;
}
export function resolvePhase(b:Battle,commands:[Command|null,Command|null],automatic:[boolean,boolean]):Battle{
  if(b.phase==='over')return fail();
  const chosen:Command[]=[];const actions:Action[]=[];
  for(const side of sides){
    if(!required(b,side)){
      if(commands[side]!==null||automatic[side])return fail();
      chosen[side]={choose:{}};continue;
    }
    // Automatic flags are persisted server decisions, never accepted from HTTP.
    const c=automatic[side]?automaticCommand(b,side):commands[side];
    if(!c)return fail();chosen[side]=c;
    const a=validate(b,side,c,automatic[side]);if(a)actions[side]=a;
  }
  const next=clone(b);
  if(b.phase==='choose')chooseTogether(next,[chosen[0].choose!,chosen[1].choose!]);
  else act(next,actions as [Action,Action],{prevalidated:true});
  return next;
}

