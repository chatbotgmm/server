// DRAFT target: src/battle/realtime-bot.ts. Not executed or deployed.
import {activeOf,pickAi,type Action,type Battle} from './engine.js';
import {deserializeBattle,required,serializeBattle,validateCommand,type Command} from './realtime-adapter.js';

type Side=0|1;
const clone=(b:Battle)=>deserializeBattle(b.R,serializeBattle(b));
const fail=():never=>{throw new Error('Invalid casual bot command');};

// Complete and validate a strategy action without running a turn. In particular,
// act() must never fall back to picking a free-switch skill after seeing the
// human's switch. The existing pickAi emits skills only; this handles a future
// strategy's switch explicitly as well. Items are forbidden regardless of stock.
export function completeBotAction(b:Battle,side:Side,action:Action):Command {
  if(b.phase!=='action'||action.kind==='item')return fail();
  let command:Command;
  if(action.kind==='switch'&&b.sides[side].augs.includes('freeswitch')){
    const probe=clone(b),s=probe.sides[side],out=activeOf(probe,side);
    const incoming=s.fighters[action.to];
    if(!Number.isInteger(action.to)||!incoming||incoming.hp<=0||action.to===s.active)return fail();
    s.active=action.to;
    if(out.passive.handoff)incoming.hp=Math.min(incoming.maxHp,incoming.hp+Math.round(incoming.maxHp*out.passive.handoff/100));
    const follow=action.then===undefined?pickAi(probe,side):{kind:'skill' as const,skillId:action.then};
    if(follow.kind!=='skill')return fail();
    command={action:{kind:'switch',to:action.to,then:follow.skillId}};
  }else command={action:{...action}};
  validateCommand(b,side,command);
  return command;
}

// Call only on a persisted phase-opening snapshot, before accepting human input.
// Do not add a pending-command parameter. Caller enforces mode==='normal' and
// botSide===1. Persist command in pending[1], automatic[1]=false, in the same
// transaction that opens the phase; never regenerate on poll/retry/reroll.
// Current pickAi and choice heuristic consume no RNG. Caller must check required
// first; a side with no choose work has no bot command to commit.
export function chooseBotCommand(source:Battle,side:Side):Command{
  const battle=clone(source);
  if(battle.phase==='over')return fail();
  if(!required(battle,side))return fail();
  let command:Command;
  if(battle.phase==='action')command=completeBotAction(battle,side,pickAi(battle,side));
  else {
    const s=battle.sides[side],foe=activeOf(battle,(1-side) as Side);
    const choose:NonNullable<Command['choose']>={};
    // The opponent remains the public phase-start fighter. Never project its
    // pending forced replacement or selected augment into this decision.
    if(s.needSwitch){
      let best=-1,score=-Infinity;
      s.fighters.forEach((f,i)=>{
        if(f.hp<=0)return;
        const value=battle.R.typeMult(f.attr,foe.attr)/battle.R.typeMult(foe.attr,f.attr);
        if(value>score){best=i;score=value;}
      });
      if(best<0)return fail();choose.switchTo=best;
    }
    if(s.offers){
      if(!s.offers.length)return fail();
      const me=s.fighters[choose.switchTo??s.active];
      const bad=battle.R.typeMult(foe.attr,me.attr)>1||battle.R.typeMult(me.attr,foe.attr)<1;
      const prefer=bad?['camo','reverse','weakguard','neutral','dual','counter']:['focus','dual','overtime','pierce','first','peanut','strip','sharp','bulwark'];
      choose.augment=prefer.find(id=>s.offers!.includes(id))??s.offers[0];
    }
    command={choose};validateCommand(battle,side,command);
  }
  return command;
}
