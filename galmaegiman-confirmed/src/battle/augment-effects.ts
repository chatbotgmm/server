// V2 신규 증강의 유한 횟수·비용·지속 상태. V1 전투에는 관여하지 않는다.
import type { Battle, Fighter } from './engine.js';
import type { Attr, Skill } from './core.js';

export interface AugmentIO {
  heal(target:Fighter,amount:number,reason:string):void;
  /** 보호막·흡혈·반사 재귀 없이 실제 잃은 HP를 반환한다. 비용에도 같은 경로를 쓴다. */
  hurt(target:Fighter,amount:number,reason:string):number;
  status(target:Fighter,reason:string):void;
}
export interface AugmentCast {
  newHealRequested?:number;direct:boolean; attr:Attr; powerPct:number; piercePct:number; critPlus:number;
  disableCrit:boolean; disableHealing:boolean; disableAugmentHealing:boolean;
  ignoreGuards:boolean; ignoreAugmentDefense:boolean; sealed:boolean;
  typeMultiplier?:number; attackLevelBonus:number; guardPct:number; incomingPlusPct:number;
  defenderHpBefore:number; triage:boolean; underdog:boolean; doubleedge:boolean; crackedarmor:boolean;
}
const NEW_IDS=new Set('rinse resetline handoffguard relief aim response exploit relaytype breach triage rescue firstshell chase baton overflowguard typebridge redline underdog glasswing lonecharge equalfield blindspot doubleedge lastmatch bloodrelay quietstorm rainbow crackedarmor mortgage coldbargain shatterprice hostileweather slowhammer siegebreak sharedburden sealedduel'.split(' '));
const USES:Record<string,number>={handoffguard:2,relief:2,aim:1,response:3,exploit:3,relaytype:3,breach:1,triage:2,rescue:1,chase:2,baton:1,overflowguard:3,typebridge:2,redline:1,underdog:2,glasswing:2,lonecharge:2,equalfield:1,blindspot:1,doubleedge:1,lastmatch:1,bloodrelay:1,quietstorm:1,rainbow:1,crackedarmor:1,coldbargain:1,shatterprice:1,slowhammer:1,siegebreak:1,sharedburden:1,sealedduel:1};
export const isAugmentV2=(b:Battle)=>b.R.augmentVersion===2&&b.augmentOffers?.version===2;
const fs=(f:Fighter):Record<string,any>=>f.augment??=( {} );
const ss=(b:Battle,s:0|1):Record<string,any>=>b.sides[s].augment??=( {} );
const owned=(b:Battle,s:0|1,id:string)=>b.sides[s].augs.includes(id);
const param=(b:Battle,id:string,key:string,fallback:number):number=>b.R.battleAug.get(id)?.p[key]??fallback;
const remaining=(b:Battle,s:0|1,id:string)=>owned(b,s,id)?(b.sides[s].augment?.uses?.[id]??param(b,id,id==='overflowguard'?'conversions':'uses',USES[id]??0)):0;
const spend=(b:Battle,s:0|1,id:string)=>{const st=ss(b,s);st.uses??={};st.uses[id]=Math.max(0,remaining(b,s,id)-1);};
const active=(b:Battle,s:0|1)=>b.sides[s].fighters[b.sides[s].active];
const pct=(f:Fighter,n:number)=>Math.max(0,Math.round(f.maxHp*n/100));
const valid=(until:unknown,b:Battle)=>typeof until==='number'&&until>=b.turn;
const cost=(f:Fighter,n:number,reason:string,io:AugmentIO)=>{const amount=pct(f,n);if(f.hp<=amount)return false;io.hurt(f,amount,reason);return true;};

export function applyNewAugment(b:Battle,side:0|1,id:string,io:AugmentIO):boolean {
  if(!isAugmentV2(b)||!NEW_IDS.has(id))return false;
  const st=ss(b,side),me=active(b,side),f=fs(me);st.uses??={};if(USES[id]!==undefined&&st.uses[id]===undefined)st.uses[id]=param(b,id,id==='overflowguard'?'conversions':'uses',USES[id]);
  if(id==='rinse'&&me.hp>0){me.status={};io.status(me,id);}
  if(id==='resetline')for(const x of b.sides[side].fighters)if(x.hp>0)x.mods=x.mods.filter(m=>m.pct>=0);
  if(id==='firstshell')for(const x of b.sides[side].fighters)fs(x).firstshellReady=true;
  if(id==='glasswing')st.glasswingUntil=b.turn+2;
  if(id==='lonecharge'){f.lonechargeUntil=b.turn+2;f.lonechargeBound=true;}
  if(id==='quietstorm'||id==='rainbow')st[id+'Until']=b.turn+4;
  if(id==='mortgage'&&me.hp>0){io.heal(me,pct(me,param(b,'mortgage','healMaxHpPct',35)),id);f.mortgageDue=b.turn+1;}
  if(id==='hostileweather'&&!b.sides.some(s=>s.augment?.hostileweatherUsed)){st.hostileweatherUsed=true;st.hostileweatherUntil=b.turn+1;}
  return true;
}

/** 기본 상성표 뒤, reverse/focus/약점 보강 전 호출. 무속성 전장은 항상 우선한다. */
export function augmentWeatherMultiplier(b:Battle,m:number):number {
  if(!isAugmentV2(b))return m;
  if(b.sides.some(s=>s.neutral>0))return 1;
  return b.sides.some(s=>valid(s.augment?.hostileweatherUntil,b))?(m>1?param(b,'hostileweather','bothSidesStrongMultiplier',2.2):m<1?param(b,'hostileweather','bothSidesWeakMultiplier',0.6):1):m;
}
export function augmentPriority(b:Battle,f:Fighter,skill:Skill):number|undefined {
  return isAugmentV2(b)&&!!skill.power&&remaining(b,f.side,'slowhammer')>0?param(b,'slowhammer','forcePriority',-3):undefined;
}
export function augmentSwitchLocked(b:Battle,f:Fighter):boolean {
  return isAugmentV2(b)&&f.hp>0&&!!f.augment?.lonechargeBound&&valid(f.augment?.lonechargeUntil,b);
}
export const augmentItemHealBlocked=augmentSwitchLocked;
export function augmentDefenseFactor(b:Battle,f:Fighter):number {
  return isAugmentV2(b)&&valid(f.augment?.crackedarmorUntil,b)?1-param(b,'crackedarmor','afterwardDefensePenaltyPct',25)/100:1;
}

/** 실제 시전에만 호출한다. 수면/빙결 실패, 미리보기에서 호출 금지. */
export function beginAugmentCast(b:Battle,user:Fighter,target:Fighter,skill:Skill,attr:Attr,rawMultiplier:number,io:AugmentIO):AugmentCast {
  const c:AugmentCast={direct:!!skill.power,attr,powerPct:0,piercePct:0,critPlus:0,disableCrit:false,disableHealing:false,disableAugmentHealing:false,ignoreGuards:false,ignoreAugmentDefense:false,sealed:false,attackLevelBonus:0,guardPct:0,incomingPlusPct:0,defenderHpBefore:target.hp,triage:false,underdog:false,doubleedge:false,crackedarmor:false};
  if(!isAugmentV2(b))return c;
  const userHpBefore=user.hp;
  const s=user.side,t=target.side,st=ss(b,s),ft=fs(user),dt=fs(target),dst=ss(b,t),has=(id:string)=>remaining(b,s,id)>0;
  // 상대가 '행동'이 아니라 실제 기술을 시전했는지를 기록한다.
  st.lastCastTurn=b.turn;
  if(!c.direct){
    ft.rainbowTypes=[];
    if(has('breach')&&!ft.breachUntil){ft.breachUntil=b.turn+1;spend(b,s,'breach');}
    if(has('quietstorm')&&valid(st.quietstormUntil,b)){
      const ids:string[]=ft.quietstormSkills??=[];if(!ids.includes(skill.id))ids.push(skill.id);
    }
    return c;
  }
  if(has('sealedduel')){spend(b,s,'sealedduel');c.sealed=true;c.ignoreAugmentDefense=true;c.disableHealing=true;c.disableAugmentHealing=true;}
  if(!c.sealed){
    const add=(id:string,n:number,condition=true)=>{if(condition&&has(id)){spend(b,s,id);c.powerPct+=param(b,id,id==='rainbow'?'thirdPowerBonusPct':id==='quietstorm'?'nextPowerBonusPct':'powerBonusPct',n);return true;}return false;};
    if(has('aim')){spend(b,s,'aim');c.critPlus+=param(b,'aim','critChancePlus',20);}
    add('response',12,dst.lastCastTurn===b.turn);
    if(has('exploit')&&target.mods.some(m=>m.pct<0&&m.turns>0)){spend(b,s,'exploit');c.piercePct+=param(b,'exploit','piercePct',10);}
    add('relaytype',25,ft.lastAttackAttr!==undefined&&ft.lastAttackAttr!==attr);
    if(valid(ft.breachUntil,b)){c.ignoreGuards=true;delete ft.breachUntil;}
    c.triage=has('triage')&&user.hp<=user.maxHp*0.5;
    add('chase',30,dst.switchedTurn===b.turn);
    if(has('redline')&&cost(user,param(b,'redline','selfMaxHpCostPct',20),'redline',io)){add('redline',65);c.disableHealing=true;}
    if(has('underdog')&&target.rec.level-user.rec.level>=10&&cost(user,param(b,'underdog','selfMaxHpCostPct',8),'underdog',io)){spend(b,s,'underdog');c.underdog=true;}
    if(add('glasswing',50,valid(st.glasswingUntil,b))&&!has('glasswing'))st.glasswingUntil=b.turn;
    if(add('lonecharge',45,augmentSwitchLocked(b,user))){c.piercePct+=param(b,'lonecharge','piercePct',20);if(!has('lonecharge'))ft.lonechargeUntil=b.turn;}
    if(has('equalfield')){spend(b,s,'equalfield');c.attackLevelBonus=Math.min(param(b,'equalfield','effectiveAttackLevelGapCap',15),Math.max(0,target.rec.level-user.rec.level));c.typeMultiplier=param(b,'equalfield','outgoingTypeMultiplier',1);c.disableCrit=true;}
    if(has('blindspot')&&rawMultiplier<1&&!b.sides.some(x=>x.neutral>0)&&c.typeMultiplier===undefined&&cost(user,param(b,'blindspot','selfMaxHpCostPct',12),'blindspot',io)){spend(b,s,'blindspot');c.typeMultiplier=param(b,'blindspot','replaceTypeMultiplier',2);}
    if(has('typebridge')&&rawMultiplier<1&&c.typeMultiplier===undefined&&!b.sides.some(x=>x.neutral>0)){spend(b,s,'typebridge');c.typeMultiplier=param(b,'typebridge','floorOutgoingTypeMultiplier',1);}
    if(has('doubleedge')){c.doubleedge=true;c.disableHealing=true;}
    if(has('lastmatch')&&b.sides[s].fighters.filter(x=>x.hp>0).length===1&&userHpBefore<=user.maxHp*.3&&cost(user,param(b,'lastmatch','selfMaxHpCostPct',10),'lastmatch',io)){add('lastmatch',80);c.disableHealing=true;}
    if(valid(ft.bloodrelayUntil,b)){c.powerPct+=param(b,'bloodrelay','incomingNextPowerBonusPct',60);delete ft.bloodrelayUntil;}
    if(has('quietstorm')&&valid(st.quietstormUntil,b)&&(ft.quietstormSkills?.length??0)>=param(b,'quietstorm','requiredDistinctUtilityCasts',2)){add('quietstorm',90);c.disableCrit=true;c.disableHealing=true;}
    if(has('rainbow')&&valid(st.rainbowUntil,b)){
      let types:Attr[]=ft.rainbowTypes??=[];
      if(types.includes(attr))types=ft.rainbowTypes=[];
      types.push(attr);
      if(types.length>=param(b,'rainbow','distinctTypes',3)){add('rainbow',75);c.disableCrit=true;ft.rainbowTypes=[];}else c.disableAugmentHealing=true;
    }
    const healSource=!!skill.drain||!!user.passive.lifesteal||skill.effects.some(e=>e.kind==='heal')||remaining(b,s,'drain')>0||has('triage');
    if(add('coldbargain',55,healSource))c.disableHealing=true;
    if(has('shatterprice')&&(user.mods.some(m=>m.pct>0)||user.guards.length>0)){
      spend(b,s,'shatterprice');user.mods=user.mods.filter(m=>m.pct<=0);user.guards=[];c.piercePct+=param(b,'shatterprice','piercePct',65);
    }
    add('slowhammer',70);
    if(add('siegebreak',65,target.hp>=target.maxHp*.8))ft.siegebreakUntil=b.turn+1;
    const bench=b.sides[s].fighters.filter(x=>x!==user&&x.hp>0);
    if(has('sharedburden')&&bench.length>0&&bench.every(x=>x.hp>pct(x,param(b,'sharedburden','benchMaxHpCostPct',10)))){for(const x of bench)io.hurt(x,pct(x,param(b,'sharedburden','benchMaxHpCostPct',10)),'sharedburden');add('sharedburden',60);}
  }
  ft.quietstormSkills=[];ft.lastAttackAttr=attr;
  // 동일 피격 시전 전체에 고정. 봉인 결투는 증강 방어와 그 소비를 모두 건너뛴다.
  if(!c.ignoreAugmentDefense&&!target.protect){
    const guards:number[]=[];
    if(dt.firstshellReady&&owned(b,t,'firstshell')){dt.firstshellReady=false;guards.push(param(b,'firstshell','guardPct',30));}
    if(remaining(b,t,'crackedarmor')>0){spend(b,t,'crackedarmor');guards.push(param(b,'crackedarmor','guardPct',60));c.crackedarmor=true;}
    if(valid(dt.handoffguardUntil,b))guards.push(param(b,'handoffguard','guardPct',12));
    if(valid(dt.batonUntil,b))guards.push(param(b,'baton','guardPct',35));
    c.guardPct=(1-guards.reduce((n,g)=>n*(1-g/100),1))*100;
  }
  // 위험 대가는 봉인/강화 해제로 지워지지 않는다.
  if(valid(dst.glasswingUntil,b))c.incomingPlusPct+=param(b,'glasswing','incomingDamagePlusPct',25);
  if(valid(dt.siegebreakUntil,b))c.incomingPlusPct+=param(b,'siegebreak','nextTurnIncomingPlusPct',30);
  c.powerPct=Math.min(100,c.powerPct);c.piercePct=Math.min(70,c.piercePct);
  return c;
}

/** 기본 흡혈 처리 뒤, 반사 전에 호출. 추가 피해는 원래 실제피해에 다시 합산하지 않는다. */
export function finishAugmentCast(b:Battle,user:Fighter,target:Fighter,c:AugmentCast,actualDamage:number,io:AugmentIO):void {
  if(!isAugmentV2(b)||!c.direct||actualDamage<=0)return;
  if(c.triage&&!c.disableHealing&&!c.disableAugmentHealing&&user.hp>0){spend(b,user.side,'triage');io.heal(user,Math.min(Math.round(actualDamage*param(b,'triage','actualDamageHealPct',25)/100),pct(user,param(b,'triage','maxHpHealCapPct',10))),'triage');}
  let extraBudget=pct(target,18);
  if(c.underdog&&target.hp>0){const requested=Math.min(extraBudget,pct(target,param(b,'underdog','extraDamageFoeMaxHpCapPct',18)),Math.round(actualDamage*param(b,'underdog','actualDamageBonusPct',35)/100));const dealt=io.hurt(target,requested,'underdog');extraBudget-=dealt;}
  if(c.doubleedge){
    spend(b,user.side,'doubleedge');
    const dealt=target.hp>0&&extraBudget>0?io.hurt(target,Math.min(extraBudget,pct(target,param(b,'doubleedge','extraFoeMaxHpCapPct',18)),Math.round(actualDamage*param(b,'doubleedge','extraActualDamagePct',30)/100)),'doubleedge'):0;
    if(dealt>0&&user.hp>0)io.hurt(user,Math.round(dealt*param(b,'doubleedge','recoilOfExtraPct',50)/100),'doubleedge-recoil');
  }
}
export function finishAugmentDefense(b:Battle,target:Fighter,c:AugmentCast,io:AugmentIO):void {
  if(!isAugmentV2(b)||!c.direct)return;
  if(c.crackedarmor)fs(target).crackedarmorUntil=b.turn+1;
  if(remaining(b,target.side,'rescue')>0&&target.hp>0&&c.defenderHpBefore>target.maxHp*.3&&target.hp<=target.maxHp*.3){
    spend(b,target.side,'rescue');target.status={};io.status(target,'rescue');
  }
}

/** 반드시 회복 금지·실제 피해 기반·시전 상한 적용을 끝낸 합법적 요청량을 전달한다. 부활에는 호출하지 않는다. */
export function augmentOverflow(b:Battle,target:Fighter,legitimateRequestedHeal:number,actualRestored:number):void {
  if(!isAugmentV2(b)||target.hp<=0||remaining(b,target.side,'overflowguard')<=0)return;
  const extra=Math.max(0,legitimateRequestedHeal-actualRestored)*param(b,'overflowguard','overflowToShieldPct',100)/100,st=fs(target);
  const before=valid(st.shieldUntil,b)?st.shield??0:0;
  const after=Math.min(Math.floor(target.maxHp*Math.min(20,param(b,'overflowguard','shieldMaxHpCapPct',20))/100),before+Math.floor(extra));
  if(after>before){st.shield=after;st.shieldUntil=b.turn+param(b,'overflowguard','shieldTurns',2)-1;spend(b,target.side,'overflowguard');}
}
/** 직접 기술의 최종 피해에서만 차감. 보호막 소모는 HP 피해/흡혈 재료가 아니다. */
export function absorbAugmentShield(b:Battle,target:Fighter,amount:number):number {
  if(!isAugmentV2(b)||!valid(target.augment?.shieldUntil,b))return amount;
  const st=fs(target),used=Math.min(Math.max(0,amount),st.shield??0);st.shield-=used;return amount-used;
}
export function onAugmentSwitch(b:Battle,out:Fighter,incoming:Fighter,voluntary:boolean,io:AugmentIO):void {
  if(!isAugmentV2(b))return;
  const side=out.side,st=ss(b,side);fs(out).rainbowTypes=[];fs(incoming).rainbowTypes=[];
  if(!voluntary){
    if(out.hp<=0&&remaining(b,side,'baton')>0){spend(b,side,'baton');fs(incoming).batonUntil=b.turn;}
    if(out.hp<=0){delete fs(out).lonechargeUntil;delete fs(out).lonechargeBound;}
    return;
  }
  st.switchedTurn=b.turn;
  if(remaining(b,side,'handoffguard')>0){spend(b,side,'handoffguard');fs(incoming).handoffguardUntil=b.turn;}
  if(remaining(b,side,'relief')>0&&incoming.hp<incoming.maxHp){spend(b,side,'relief');io.heal(incoming,pct(incoming,param(b,'relief','healMaxHpPct',8)),'relief');}
  if(remaining(b,side,'bloodrelay')>0&&cost(out,param(b,'bloodrelay','outgoingMaxHpCostPct',20),'bloodrelay',io)){spend(b,side,'bloodrelay');fs(incoming).bloodrelayUntil=b.turn+1;}
}
/** 모든 대기 유닛을 포함해서, 현재 행동 턴 종료 시 b.turn 증가 전에 호출한다. */
export function endAugmentTurn(b:Battle,io:AugmentIO):void {
  if(!isAugmentV2(b))return;
  for(const side of [0,1] as const){
    const st=ss(b,side);
    for(const f of b.sides[side].fighters){
      const ft=fs(f);
      if(ft.mortgageDue!==undefined&&ft.mortgageDue<=b.turn){delete ft.mortgageDue;if(f.hp>0)io.hurt(f,pct(f,param(b,'mortgage','deferredMaxHpCostPct',25)),'mortgage');}
      if(ft.shieldUntil!==undefined&&ft.shieldUntil<=b.turn){delete ft.shield;delete ft.shieldUntil;}
      if(f.hp<=0){delete ft.lonechargeBound;delete ft.lonechargeUntil;}
    }
    // 강제 후공 대기 중 전투불가가 되면 팀 공유 기회도 소멸한다.
    if(active(b,side).hp<=0&&remaining(b,side,'slowhammer')>0)spend(b,side,'slowhammer');
    if(st.glasswingUntil!==undefined&&st.glasswingUntil<=b.turn)st.uses.glasswing=0;
    if(st.quietstormUntil!==undefined&&st.quietstormUntil<=b.turn)st.uses.quietstorm=0;
    if(st.rainbowUntil!==undefined&&st.rainbowUntil<=b.turn)st.uses.rainbow=0;
  }
}
