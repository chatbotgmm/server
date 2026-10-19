// Android 전용 JSON API. 화면은 네이티브 View/Canvas, 전투는 공통 TypeScript 엔진.
import {randomBytes, randomInt} from 'node:crypto';
import {existsSync} from 'node:fs';
import type {FastifyInstance,FastifyRequest,FastifyReply} from 'fastify';
import {z} from 'zod';
import type {GameService} from './game.js';
import {GameError} from './types.js';
import {loadBattleRules} from './battle/load.js';
import {loadStory} from './battle/story.js';
import {loadContent,rarityNames,topTiers} from './content.js';
import {createBattle,act,choose,aiChoose,pickAi,legalActions,skillProblem,skillPreview,itemProblem,rerollAugment,pending,type Battle,type Action,type Fighter} from './battle/engine.js';
import {statusKeys,statusName,type Attr,type Rules} from './battle/core.js';
import {addXp,battleXp,xpToNext,innateLearned,statsOf,slotSkills,learnOffers,learnChances,learnLeft,learnSkill,learnPromo,promoSkills,canEvolve,stableEvoChoices,type UnitRecord,type Stage} from './battle/growth.js';
import type {PvpEvent} from './battle/replay.js';

interface Save {units:Record<string,Partial<UnitRecord>>;team:string[];cleared:string[];hp:Record<string,{f:number;at:number}>}
export interface NativeFighterEffect {id:string;label:string;valueText:string;remainingTurns:number|null;kind:'buff'|'debuff'|'guard'|'regen'|'status';attrCode?:Attr}
// 표시용 현재값만 복사한다. 남은 턴을 차감하거나 중첩 효과를 합산하지 않는다.
export function nativeFighterEffects(R:Rules,f:Fighter):NativeFighterEffect[]{
  const pct=(n:number)=>`${n>0?'+':''}${n}%`,st=R.data.status;
  const statusValue={burn:`턴마다 최대 체력 ${st.burnDamage}% 피해 · 공격 ${pct(st.burnAtk)}`,freeze:'행동 불가',sleep:'행동 불가',bind:`속도 ${pct(st.bindSpd)}`,rage:`공격 ${pct(st.rageAtk)} · 방어 ${pct(st.rageDef)}`};
  const effects:NativeFighterEffect[]=statusKeys.filter(k=>(f.status[k]??0)>0).map(k=>({id:`status:${k}`,label:statusName[k],valueText:statusValue[k],remainingTurns:f.status[k]!,kind:'status'}));
  const statName={atk:'공격',def:'방어',spd:'속도'};
  for(const [i,m] of f.mods.entries())if(m.turns>0&&m.pct!==0)effects.push({id:`mod:${m.stat}:${i}`,label:`${statName[m.stat]} ${m.pct>0?'강화':'약화'}`,valueText:pct(m.pct),remainingTurns:m.turns,kind:m.pct>0?'buff':'debuff'});
  for(const [i,g] of f.guards.entries())if(g.turns>0)effects.push({id:`guard:${i}`,label:'받는 피해 감소',valueText:`-${g.pct}%`,remainingTurns:g.turns,kind:'guard'});
  for(const [i,r] of f.regens.entries())if(r.turns>0)effects.push({id:`regen:${i}`,label:'재생',valueText:`턴마다 최대 체력 ${r.pct}% 회복`,remainingTurns:r.turns,kind:'regen'});
  if(f.endure>0)effects.push({id:'endure',label:'버티기',valueText:'체력이 1 아래로 떨어지지 않음',remainingTurns:f.endure,kind:'guard'});
  if(f.immune>0)effects.push({id:'immune',label:'상태이상 면역',valueText:'새로운 상태이상 차단',remainingTurns:f.immune,kind:'guard'});
  if(f.protect)effects.push({id:'protect',label:'완전 방어',valueText:'이번 턴 공격 차단',remainingTurns:1,kind:'guard'});
  if(f.lockAtk)effects.push({id:'lockAtk',label:'교체 불가',valueText:`공격 ${pct(f.lockAtk)}`,remainingTurns:null,kind:'buff'});
  if(f.extraAttr)effects.push({id:'extraAttr',label:'이중 속성',valueText:R.attrName(f.extraAttr),remainingTurns:null,kind:'buff',attrCode:f.extraAttr});
  return effects;
}
// 실제 승진과 같은 단계·표창을 복사본에 적용한다. 저장·체력·난수 진행은 건드리지 않는다.
export function nativeEvolutionChoices(R:Rules,r:UnitRecord){
  if(r.stage>=3)return [];
  const beforeStats=statsOf(R,r),beforeSkills=slotSkills(R,r),beforePromo=promoSkills(R,r);
  return stableEvoChoices(R,r).map(a=>{
    const next:UnitRecord={...structuredClone(r),stage:(r.stage+1) as Stage,evoAugs:[...r.evoAugs,a.id]};
    return {...a,preview:{fromStage:r.stage,toStage:next.stage,beforeStats:{...beforeStats},afterStats:statsOf(R,next),beforeSkills:structuredClone(beforeSkills),afterSkills:structuredClone(slotSkills(R,next)),beforePromo:structuredClone(beforePromo),afterPromo:structuredClone(promoSkills(R,next)),restoresHp:true,cost:0}};
  });
}
interface Fight {id:string;player:string;b:Battle;keys:string[];mode:string;stage?:string;revision:number;exp:number;events:PvpEvent[];match?:string;matchInfo?:{mode:string;level:number;mineName:string;foeName:string;foeTier:string|null;bot:boolean;pointBefore:number;pointAfterStart:number};result?:unknown;done:boolean;chooseDeadline?:number;timer?:ReturnType<typeof setTimeout>}
const action=z.union([z.object({kind:z.literal('skill'),skillId:z.string().max(40)}).strict(),z.object({kind:z.literal('switch'),to:z.number().int().min(0).max(2)}).strict(),z.object({kind:z.literal('item'),item:z.string().max(30),target:z.number().int().min(0).max(2).optional()}).strict()]);
const turn=z.object({session:z.string().max(40),revision:z.number().int().min(0),action:action.optional(),choose:z.object({augment:z.string().max(30).optional(),switchTo:z.number().int().min(0).max(2).optional()}).strict().optional(),reroll:z.number().int().min(0).max(2).optional(),leave:z.boolean().optional()}).strict().refine(b=>[b.action,b.choose,b.reroll,b.leave].filter(x=>x!==undefined).length===1);
export function registerNative(app:FastifyInstance,game:GameService,root:string,auth:(r:FastifyRequest)=>string|null,guard:(r:FastifyRequest,p:FastifyReply)=>boolean){
  const R=loadBattleRules(root),story=loadStory(root,R),content=loadContent(root),fights=new Map<string,Fight>(),locks=new Map<string,Promise<unknown>>();
  const serial=async<T>(id:string,run:()=>Promise<T>)=>{const p=(locks.get(id)??Promise.resolve()).catch(()=>{}).then(run);locks.set(id,p);try{return await p;}finally{if(locks.get(id)===p)locks.delete(id);}};
  const route=(method:'GET'|'POST',path:string,run:(id:string,body:any)=>Promise<unknown>)=>app.route({method,url:`/my/api/native/${path}`,config:{rateLimit:{max:120,timeWindow:'1 minute'}},handler:async(req,reply)=>{
    if(!guard(req,reply))return;const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    reply.header('Cache-Control','no-store');try{return await serial(id,()=>run(id,req.body??{}));}catch(e){if(e instanceof GameError||e instanceof z.ZodError)return reply.code(400).send({error:e instanceof GameError?e.message:'입력 정보를 확인해 주세요.'});throw e;}
  }});
  const requireIdle=(id:string)=>{if([...fights.values()].some(f=>f.player===id&&!f.done&&f.exp>Date.now()))throw new GameError('진행 중인 전투를 마친 뒤 편성·성장·회복을 변경해 주세요.');};
  const read=async(id:string):Promise<Save>=>{const d=await game.webStory(id) as Partial<Save>|null;return {units:d?.units??{},team:d?.team??[],cleared:d?.cleared??[],hp:d?.hp??{}};};
  const mine=async(id:string)=>new Map((await game.webStoryUnits(id)).map(u=>[`o${u.oid}`,u.id]));
  const rec=(key:string,s:Save,owned:Map<string,string>):UnitRecord=>{
    const id=owned.get(key);if(!id)throw new GameError('지금 가진 갈매미를 선택해 주세요.');
    // 옛 종류 단위 저장도 첫 네이티브 접속 때 이어받는다.
    const u=s.units[key]??s.units[id]??{};const c=content.map.get(id)!;
    return {uid:key,id,name:c.name,tier:c.rarity as UnitRecord['tier'],level:u.level??story.start.level,xp:u.xp??0,stage:u.stage??0,evoAugs:u.evoAugs??[],learned:u.learned??innateLearned(R,id,key),learnUsed:u.learnUsed??0,learnPick:u.learnPick??0};
  };
  const keep=(s:Save,key:string,r:UnitRecord)=>{const {level,xp,stage,evoAugs,learned,learnUsed,learnPick}=r;s.units[key]={level,xp,stage,evoAugs,learned,learnUsed,learnPick};};
  const hp=(s:Save,key:string)=>{const h=s.hp[key];return h?Math.min(1,h.f+Math.max(0,Date.now()-h.at)/((R.data.storyHp?.fullMinutes??60)*60000)):1;};
  const parts=(s:Save)=>story.chapters.filter(c=>/^\d+$/.test(c.id)&&s.cleared.includes(c.stages.at(-1)!.id)).length;
  const image=(id:string,side='front',stage=0)=>{
    if(id.startsWith('V')){const v=R.data.villains[id];if(v?.tint&&existsSync(`${root}/public/battle/sprites/V17_front.webp`))return '/duel/sprites/V17_front.webp';if(!existsSync(`${root}/public/battle/sprites/${id}_front.webp`)&&v?.look)return image(v.look,side,stage);return `/duel/sprites/${id}_front.webp`;}
    return `/duel/sprites/${id}_s${existsSync(`${root}/public/battle/sprites/${id}_s${stage}_${side}.webp`)?stage:0}_${side}.webp`;
  };
  // 웹 bodySz 기준: 그림의 상징물 때문에 몸이 작아 보이는 원형만 눈높이를 보정한다.
  const bodyScale:Record<string,number>={D1:1.19,D3:1.36,D4:1.24,D6:1.7,D8:1.19,T1:1.51,T3:1.51,T4:1.08,T6:1.24,T7:1.1,E2:1.1,E4:1.17,E5:1.7,E6:1.05,E7:1.8,I2:1.17,I3:1.8,I4:1.24,I5:1.1,I6:1.17};
  const actor=(id:string)=>{const v=R.villains.get(id),c=content.map.get(id);return {name:v?.name??c?.name??id,image:R.units.has(id)||v?image(id):`/thumbs/${id}.jpg`,tint:R.data.villains[id]?.tint??null,boss:!!v?.boss,bodyScale:bodyScale[id]??1,realTintArt:!!R.data.villains[id]?.tint&&existsSync(`${root}/public/battle/sprites/V17_front.webp`)};};
  const sceneActors={...Object.fromEntries([...content.characters.map(c=>c.id),...R.villains.keys()].map(id=>[id,actor(id)])),...{HALL:{name:'마을 회관 방송',image:'/duel/sprites/HALL_front.webp',tint:null,boss:false,bodyScale:1},'마을 회관 방송':{name:'마을 회관 방송',image:'/duel/sprites/HALL_front.webp',tint:null,boss:false,bodyScale:1}}};
  const stages=story.chapters.flatMap(c=>c.stages.map(st=>({...st,chapter:c})));
  const stageAccess=(s:Save,id:string,team:UnitRecord[])=>{
    const ix=stages.findIndex(st=>st.id===id);if(ix<0)throw new GameError('스토리 단계를 다시 골라 주세요.');
    const st=stages[ix],previous=stages[ix-1],requiredStage=st.chapter.needStage??0,clearedParts=parts(s);
    // 이미 뒤쪽까지 진행한 저장도 다시 플레이할 수 있는 기존 판정을 그대로 사용한다.
    const progressed=ix===0||s.cleared.includes(previous.id)||stages.slice(ix).some(st=>s.cleared.includes(st.id));
    const requirements:Array<{kind:string;label:string;met:boolean;action:string;stageId?:string;chapterIndex?:number;key?:string;name?:string;current?:number;required?:number;level?:number;needLevel?:number;clearedParts?:number;needParts?:number;canEvolve?:boolean;nextStage?:number;conditions?:Array<{kind:string;label:string;met:boolean;current:number;required:number;action?:string;stageId?:string;chapterIndex?:number}>}>=[];
    if(previous)requirements.push({kind:'stage',label:`${previous.id} ${previous.title} 클리어`,met:progressed,action:'stage',stageId:previous.id,chapterIndex:story.chapters.indexOf(previous.chapter)});
    requirements.push({kind:'team',label:'전투 수비대 1마리 이상 편성',met:team.length>0,action:'team',current:team.length,required:1});
    if(requiredStage>0)for(const r of team){
      const met=r.stage>=requiredStage,needLevel=(R.data.growth.evolveLevel as number[])[r.stage],needParts=r.stage+1;
      const part=story.chapters.find(c=>/^\d+$/.test(c.id)&&!s.cleared.includes(c.stages.at(-1)!.id)),stageId=part?.stages.at(-1)?.id;
      requirements.push({kind:'growth',label:`${r.name} ${requiredStage}단계 승진`,met,action:'growth',key:r.uid,name:r.name,current:r.stage,required:requiredStage,level:r.level,needLevel,clearedParts,needParts,canEvolve:canEvolve(R,r,clearedParts),nextStage:Math.min(3,r.stage+1),conditions:met?[]:[
        {kind:'level',label:`Lv${needLevel} 도달`,met:r.level>=needLevel,current:r.level,required:needLevel},
        {kind:'chapter',label:`스토리 ${needParts}개 부 클리어`,met:clearedParts>=needParts,current:clearedParts,required:needParts,action:'stage',stageId,chapterIndex:part?story.chapters.indexOf(part):undefined},
      ]});
    }
    const open=requirements.every(r=>r.met);
    return {open,reason:requirements.filter(r=>!r.met).map(r=>r.label).join(' · '),requiredStage,previousStageId:previous?.id??null,requirements};
  };
  const unlocked=(s:Save,id:string,team:UnitRecord[])=>stages.some(st=>st.id===id)&&stageAccess(s,id,team).open;
  const growthInfo=(r:UnitRecord,s:Save)=>({
    learnChances:learnChances(R,r),learnLeft:learnLeft(R,r),learnPerChance:R.data.learn.perChance??1,
    learnTier:learnChances(R,r)>0?R.data.learn.levels[r.learnUsed??0]:null,
    nextLearnLevel:R.data.learn.levels.find(level=>level>r.level)??null,
    canLearn:learnOffers(R,r).length>0,canPromote:promoSkills(R,r).length>0,canEvolve:canEvolve(R,r,parts(s)),
    promotionNeed:{level:r.stage<3?(R.data.growth.evolveLevel as number[])[r.stage]:null,clearedParts:parts(s),needParts:r.stage<3?r.stage+1:null,stage:r.stage},
  });
  const detail=(key:string,s:Save,owned:Map<string,string>)=>{const r=rec(key,s,owned);return {...r,...growthInfo(r,s),key,recipeValue:R.recipeValue(r.id),recipeBonusPct:R.recipeBonus(r.id),stats:statsOf(R,r),role:R.unitDef(r.id)!.role,attrCode:R.unitDef(r.id)!.attr,synergy:content.map.get(r.id)?.synergy??null,image:image(r.id,'front',r.stage),hp:Math.round(statsOf(R,r).hp*hp(s,key)),maxHp:statsOf(R,r).hp,xpNext:xpToNext(R,r.level),attr:R.attrName(R.unitDef(r.id)!.attr),skills:slotSkills(R,r),learn:learnOffers(R,r),promo:promoSkills(R,r),evolve:canEvolve(R,r,parts(s))?nativeEvolutionChoices(R,r):[]};};
  const sources=Object.fromEntries(content.characters.map(c=>[c.id,game.sourceText(c.id)]));
  route('GET','catalog',async id=>{
    const [me,craft,collected]=await Promise.all([game.webMe(id),game.webCraftInfo(id),game.webCollectionIds(id)]);
    if(!me)throw new GameError('로그인을 다시 해 주세요.');
    const known=new Set(collected),stock=new Map(me.units.map(u=>[u.id,u])),recipes=new Map(craft.recipes.map(r=>[r.id,r]));
    const visible=(cid:string)=>content.map.get(cid)?.rarity!=='HIDDEN'||known.has(cid);
    const face=(cid:string)=>known.has(cid)?cid:visible(cid)&&existsSync(`${root}/public/images/q-${content.map.get(cid)!.rarity}.png`)?`q-${content.map.get(cid)!.rarity}`:'unknown';
    const name=(cid:string)=>visible(cid)?content.map.get(cid)!.name:'???';
    const source=(cid:string)=>visible(cid)?sources[cid]:'숨은 조합을 직접 발견해 보세요.';
    const bases=(cid:string,n:number,counts:Map<string,number>)=>{
      if(!visible(cid))return;
      const recipe=recipes.get(cid);
      if(!recipe){counts.set(cid,(counts.get(cid)??0)+n);return;}
      for(const m of recipe.materials)bases(m.id,n*m.need,counts);
    };
    return {units:content.characters.map(c=>{
      const have=stock.get(c.id),isKnown=known.has(c.id),revealed=visible(c.id),top=topTiers.has(c.rarity),recipe=revealed?recipes.get(c.id):undefined;
      const base=new Map<string,number>();if(recipe)bases(c.id,1,base);
      return {id:c.id,name:name(c.id),tier:c.rarity,tierName:rarityNames[c.rarity],top,known:isKnown,
        image:`/thumbs/${face(c.id)}.jpg`,quote:revealed&&(!top||isKnown)?c.quote:'',tagline:revealed&&(!top||isKnown)?c.tagline:'',intro:revealed&&(!top||isKnown)?c.introduction:top?'이 원형의 모습과 이야기는 직접 만나면 열려요.':'',
        hint:!revealed?c.hint??'재료로 직접 조합해서 찾아요':'',source:source(c.id),have:have?.have??0,away:have?.away??0,locked:have?.locked??0,
        ...(R.units.has(c.id)?{attr:R.unitDef(c.id)!.attr,attrName:R.attrName(R.unitDef(c.id)!.attr)}:{}),
        materials:recipe?.materials.map(m=>({id:m.id,name:name(m.id),img:face(m.id),need:m.need,usable:m.usable,locked:m.locked,away:m.away,source:source(m.id)}))??[],
        usedIn:revealed?craft.recipes.filter(r=>r.materials.some(m=>m.id===c.id)).map(r=>({id:r.id,name:name(r.id),img:face(r.id)})):[],
        baseNeeds:[...base].sort((a,b)=>b[1]-a[1]).map(([cid,need])=>({id:cid,name:name(cid),need})),
      };
    })};
  });
  route('GET','state',async id=>{const [s,o,me,items,tutorial]=await Promise.all([read(id),mine(id),game.webMe(id),game.webItems(id),game.webTutorial(id)]);const original=JSON.stringify(s);if(!me)throw new GameError('로그인을 다시 해 주세요.');
    if(!s.team.length){s.team=(me.battleTeam??[]).map(sp=>[...o].find(([,id])=>id===sp)?.[0]).filter((k):k is string=>!!k).slice(0,3);if(!s.team.length)s.team=[...o.keys()].slice(0,3);}
    s.team=s.team.map(k=>o.has(k)?k:[...o].find(([,sp])=>sp===k)?.[0]).filter((k):k is string=>!!k);
    for(const k of s.team)keep(s,k,rec(k,s,o));if(JSON.stringify(s)!==original)await game.webSaveStory(id,s);
    const team=s.team.map(k=>rec(k,s,o));const nativeMe=me?{...me,prices:game.webPrices(),economy:{expedition:game.content.economy.expedition,rewards:game.content.economy.rewards},units:me.units.map(u=>{const def=R.unitDef(u.id);return def?{...u,attr:def.attr,attrName:R.attrName(def.attr)}:u;})}:me;return {me:nativeMe,sources,sceneActors,storyHpFullMinutes:R.data.storyHp?.fullMinutes??60,activeBattle:[...fights.values()].some(f=>f.player===id&&!f.done&&f.exp>Date.now()),tutorial,practiceUnits:[...R.units.keys()].map(id=>({id,name:content.map.get(id)!.name,attr:R.attrName(R.unitDef(id)!.attr),image:image(id)})),names:Object.fromEntries([...content.characters.map(c=>[c.id,c.name]),...[...R.villains].map(([id,v])=>[id,v.name])]),portraits:{...Object.fromEntries([...R.units.keys(),...R.villains.keys()].map(id=>[id,image(id)])),...(existsSync(`${root}/public/battle/sprites/HALL_front.webp`)?{'마을 회관 방송':'/duel/sprites/HALL_front.webp'}:{})},team:s.team,units:[...o.keys()].map(k=>detail(k,s,o)),chapters:story.chapters.map(c=>{const lock=stageAccess(s,(c.stages.find(st=>!s.cleared.includes(st.id))??c.stages[0]).id,team);return {...c,open:lock.open,lock,stages:c.stages.map(st=>({...st,boss:st.enemy.some(e=>R.villains.get(e.id)?.boss),enemy:st.enemy.map(e=>({...e,boss:!!R.villains.get(e.id)?.boss,role:R.unitDef(e.id)?.role,attrCode:R.unitDef(e.id)?.attr})),open:unlocked(s,st.id,team),lock:stageAccess(s,st.id,team),cleared:s.cleared.includes(st.id)}))};}),items,itemDefs:R.data.items,synergies:R.data.synergies,evoDefinitions:Object.fromEntries(R.evoAug),typeChart:R.data.chart,attributes:R.data.attributes};
  });
  route('POST','story/reset',async(id,body)=>{requireIdle(id);z.object({confirm:z.literal('RESET')}).strict().parse(body);await game.webSaveStory(id,null);return {ok:true};});
  route('POST','team',async(id,body)=>{requireIdle(id);const b=z.object({keys:z.array(z.string().regex(/^o\d+$/)).min(1).max(3)}).strict().parse(body),o=await mine(id);if(new Set(b.keys).size!==b.keys.length||b.keys.some(k=>!o.has(k)))throw new GameError('내 갈매미 1~3마리를 골라 주세요.');const s=await read(id);s.team=b.keys;for(const k of s.team)keep(s,k,rec(k,s,o));await game.webSaveStory(id,s);return {ok:true};});
  route('POST','growth',async(id,body)=>{requireIdle(id);const b=z.object({key:z.string().regex(/^o\d+$/),kind:z.enum(['learn','promo','evolve']),skill:z.string().max(40).nullable().optional(),replace:z.string().max(40).optional(),augment:z.string().max(40).optional()}).strict().parse(body),s=await read(id),o=await mine(id),r=rec(b.key,s,o);
    try{if(b.kind==='learn')learnSkill(R,r,b.skill??null,b.replace);else if(b.kind==='promo')learnPromo(R,r,b.skill??'',b.replace);else{if(!canEvolve(R,r,parts(s)))throw new Error('승진 조건을 확인해 주세요.');if(!stableEvoChoices(R,r).some(a=>a.id===b.augment))throw new Error('표창을 다시 골라 주세요.');r.stage=(r.stage+1) as Stage;r.evoAugs.push(b.augment!);s.hp[b.key]={f:1,at:Date.now()};}}catch(e){throw new GameError((e as Error).message);}
    keep(s,b.key,r);await game.webSaveStory(id,s);return {ok:true,unit:detail(b.key,s,o)};
  });
  route('POST','heal',async(id,body)=>{requireIdle(id);const b=z.object({key:z.string().regex(/^o\d+$/),item:z.string().max(30)}).strict().parse(body),s=await read(id),o=await mine(id);rec(b.key,s,o);const it=R.data.items[b.item];if(!it?.heal)throw new GameError('회복 아이템을 골라 주세요.');const f=hp(s,b.key);if(f>=1)throw new GameError('체력이 가득해요.');s.hp[b.key]={f:Math.min(1,f+it.heal/100),at:Date.now()};await game.webNativeCommit(id,{id:b.item,name:it.name},s);return {ok:true};});
  // 다시 뽑기·재접속으로 제한 시간을 늘리지 않는다. 사용자별 직렬 처리 안에서만 실행한다.
  function expireChoice(f:Fight){
    if(f.done||!f.chooseDeadline||Date.now()<f.chooseDeadline||f.b.phase!=='choose')return;
    const offers=f.b.sides[0].offers;if(!offers?.length)return;
    const next:Battle={...f.b,sides:structuredClone(f.b.sides),log:[...f.b.log],events:structuredClone(f.b.events)};
    const live=next.sides[0].fighters.map((x,i)=>x.hp>0?i:-1).filter(i=>i>=0);
    const pick={augment:offers[randomInt(offers.length)],...(next.sides[0].needSwitch?{switchTo:live[randomInt(live.length)]}:{})};
    next.log.push('선택 시간 30초가 지나 증강체를 무작위로 골랐어요.');
    choose(next,0,pick);if(next.phase==='choose')aiChoose(next,1);
    f.b=next;f.events.push({c:pick});f.revision++;syncChoice(f);
  }
  function syncChoice(f:Fight){
    if(f.done||f.b.phase!=='choose'||!f.b.sides[0].offers?.length){
      if(f.timer)clearTimeout(f.timer);f.timer=undefined;f.chooseDeadline=undefined;return;
    }
    if(f.chooseDeadline)return;
    f.chooseDeadline=Date.now()+30_000;
    f.timer=setTimeout(()=>{void serial(f.player,async()=>expireChoice(f)).catch(error=>app.log.error({errorCode:error instanceof Error?error.name:'Unknown'},'augment timeout'));},30_000);
    f.timer.unref();
  }
  app.addHook('onClose',async()=>{for(const f of fights.values())if(f.timer)clearTimeout(f.timer);});
  const battleFor=(b:Battle,side:0|1,slot:number):Battle=>({...b,sides:b.sides.map((s,index)=>index===side?{...s,active:slot}:s) as Battle['sides']});
  function matchup(b:Battle,side:0|1,slot:number){
    // 임시 active 인덱스만 바꿔서 같은 피해 미리보기 규칙을 사용한다. RNG·원본 전투는 건드리지 않는다.
    const copy=battleFor(b,side,slot);
    const move={id:'basic-preview',name:'상성 확인',power:1,cd:0,effects:[],desc:''};
    return {attack:skillPreview(copy,side,move),defense:skillPreview(copy,(1-side) as 0|1,move)};
  }
  function snapshot(f:Fight){const b=f.b;return {session:f.id,revision:f.revision,serverNow:Date.now(),chooseDeadline:f.chooseDeadline,mode:f.mode,stage:f.stage,scene:stages.find(st=>st.id===f.stage)?.scene??'harbor',hint:stages.find(st=>st.id===f.stage)?.hint??'',boss:b.sides[1].fighters.some(x=>R.villains.get(x.rec.id)?.boss),phase:b.phase,turn:b.turn,winner:b.winner,result:f.result,matchInfo:f.matchInfo,itemLimit:R.data.itemRules.perBattle,history:b.log,log:b.log.slice(-50),displayEventVersion:1,displayEventSeq:b.eventSeq,displayEvents:b.events,displayLogStart:Math.max(0,b.log.length-50),teams:b.sides.map((s,side)=>({active:s.active,comboUsed:s.comboUsed,augments:s.augs.map(id=>R.battleAug.get(id)),needSwitch:s.needSwitch,offers:s.offers?.map((id,i)=>({...R.battleAug.get(id),icon:existsSync(`${root}/public/battle/icons/${id}.png`)?id:'u-gift',rerolled:s.rerolled[i]}))??[],fighters:s.fighters.map((x,slot)=>({key:x.rec.uid,id:x.rec.id,name:x.name,level:x.rec.level,stage:x.rec.stage,role:x.role,base:x.base,tint:R.data.villains[x.rec.id]?.tint??null,realTintArt:!!R.data.villains[x.rec.id]?.tint&&existsSync(`${root}/public/battle/sprites/V17_front.webp`),bodyScale:x.rec.stage&&existsSync(`${root}/public/battle/sprites/${x.rec.id}_s${x.rec.stage}_front.webp`)?1:bodyScale[x.rec.id]??1,matchup:matchup(b,side as 0|1,slot),image:image(x.rec.id,side===0?'back':'front',x.rec.stage),hp:x.hp,maxHp:x.maxHp,attr:R.attrName(x.attr),attrCode:x.attr,extraAttr:x.extraAttr?R.attrName(x.extraAttr):null,status:x.status,effects:nativeFighterEffects(R,x),skills:x.skills.map(sk=>({...sk,preview:skillPreview(battleFor(b,side as 0|1,slot),side as 0|1,sk),reason:skillProblem(battleFor(b,side as 0|1,slot),side as 0|1,sk)}))})),legal:b.phase==='action'?legalActions(b,side as 0|1):[],itemsUsed:s.itemsUsed})),items:Object.entries(R.data.items).map(([id,it])=>({id,...it,reason:itemProblem(b,0,id)}))};}
  route('POST','battle/start',async(id,body)=>{const v=z.object({mode:z.enum(['story','practice','normal','rank']),stage:z.string().max(12).optional(),roster:z.array(z.string()).min(1).max(3).optional(),enemyRoster:z.array(z.string()).min(1).max(3).optional(),level:z.number().int().min(1).max(60).optional(),promotion:z.number().int().min(0).max(3).optional()}).strict().refine(v=>v.mode==='practice'||[v.roster,v.enemyRoster,v.level,v.promotion].every(x=>x===undefined)).parse(body);const now=Date.now();for(const [k,f] of fights)if(f.exp<now){if(f.timer)clearTimeout(f.timer);fights.delete(k);}if([...fights.values()].some(f=>f.player===id&&!f.done))throw new GameError('진행 중인 전투로 돌아가 주세요.');if(fights.size>=20000)throw new GameError('잠시 후 다시 시도해 주세요.');
    const s=await read(id),o=await mine(id);let team=s.team.filter(k=>o.has(k)).map(k=>rec(k,s,o)),foe:UnitRecord[],seed=randomInt(2**31-1),match:string|undefined,matchInfo:Fight['matchInfo'];
    if(v.mode==='normal'||v.mode==='rank'){const before=await game.webPvp(id),me=await game.webMe(id),m=await game.webPvpMatch(id,v.mode),after=await game.webPvp(id);team=m.mine;foe=m.foe;seed=m.seed;match=m.match;matchInfo={mode:m.mode,level:m.mine[0].level,mineName:me?.nickname??'내 수비대',foeName:m.foeName,foeTier:m.foeTier,bot:m.bot,pointBefore:before.me.point,pointAfterStart:after.me.point};}
    else{
      const practice=(ids:string[],prefix:string):UnitRecord[]=>ids.map((id,i)=>{if(!R.units.has(id))throw new GameError('연습용 갈매미를 다시 골라 주세요.');const r:UnitRecord={uid:prefix+i,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as UnitRecord['tier'],level:v.level??15,xp:0,stage:(v.promotion??0) as Stage,evoAugs:[]};
        while(learnChances(R,r)>0){const offers=learnOffers(R,r),used=r.learnUsed??0,replace=slotSkills(R,r).find(sk=>!sk.id.startsWith('basic-')&&sk.id!=='sig')?.id;learnSkill(R,r,offers.length&&replace?offers[randomInt(offers.length)].id:null,replace);if((r.learnUsed??0)===used)learnSkill(R,r,null);}
        return r;});
      if(v.mode==='practice')team=practice(v.roster??['D1','D2','D3'],'practice');
      if(!team.length)throw new GameError('먼저 수비대를 편성해 주세요.');const st=stages.find(st=>st.id===(v.stage??'P-1'));if(!st||v.mode==='story'&&!unlocked(s,st.id,team))throw new GameError('앞 전투와 승진 조건을 확인해 주세요.');if(v.mode==='story'&&s.team.some(k=>Math.round(statsOf(R,rec(k,s,o)).hp*hp(s,k))<=0))throw new GameError('체력이 0인 갈매미를 회복해 주세요.');foe=st.enemy.map((e,i)=>({uid:`foe${i}`,id:e.id,name:R.villains.get(e.id)!.name!,tier:'LIMITED',level:e.level,xp:0,stage:(e.stage??st.chapter.needStage??0) as Stage,evoAugs:[]}));if(v.mode==='practice'&&v.enemyRoster)foe=practice(v.enemyRoster,'practice-foe');}
    const b=createBattle(R,team,foe,seed);if(v.mode==='story')b.sides[0].fighters.forEach((f,i)=>f.hp=Math.max(1,Math.round(f.maxHp*hp(s,s.team[i]))));
    const f:Fight={id:randomBytes(16).toString('base64url'),player:id,b,keys:s.team,mode:v.mode,stage:v.stage??'P-1',revision:0,exp:now+2*3600000,events:[],match,matchInfo,done:false};fights.set(f.id,f);syncChoice(f);return {...snapshot(f),inventory:await game.webItems(id)};
  });
  route('GET','battle/current',async id=>{const f=[...fights.values()].find(f=>f.player===id&&!f.done&&f.exp>Date.now());if(f)expireChoice(f);return f?snapshot(f):{session:null};});
  route('POST','battle/turn',async(id,body)=>{const v=turn.parse(body),f=fights.get(v.session);if(!f||f.player!==id||f.exp<Date.now())throw new GameError('전투가 만료됐어요. 다시 시작해 주세요.');expireChoice(f);if(v.revision!==f.revision)return snapshot(f);if(f.done)return snapshot(f);
    const b=f.b;
    // 실패 시 턴/RNG/아이템이 일부만 바뀌지 않도록 복사에서 먼저 검증한다.
    const next:Battle={...b,sides:structuredClone(b.sides),log:[...b.log],events:structuredClone(b.events)};let event:PvpEvent|undefined;
    try{if(v.leave){next.phase='over';next.winner=1;}
      else if(v.choose){choose(next,0,v.choose);if(next.phase==='choose')aiChoose(next,1);event={c:v.choose};}
      else if(v.reroll!==undefined){rerollAugment(next,0,v.reroll);event={r:v.reroll};}
      else if(v.action){if(v.action.kind==='item'){if(f.mode!=='story')throw new Error('스토리에서만 아이템을 사용할 수 있어요.');const why=itemProblem(next,0,v.action.item,v.action.target);if(why)throw new Error(why);}act(next,[v.action as Action,pickAi(next,1)]);event={a:v.action as Action};}
      while(next.phase==='choose'&&!pending(next,0).augments&&!pending(next,0).needSwitch)aiChoose(next,1);
    }catch(e){throw new GameError((e as Error).message);}
    const item=v.action?.kind==='item'?{id:v.action.item,name:R.data.items[v.action.item].name}:undefined;
    const events=event?[...f.events,event]:f.events;
    let committed=false;
    if(next.phase==='over'){
      if(f.mode==='story'){const s=await read(id),o=await mine(id),won=next.winner===0,st=stages.find(st=>st.id===f.stage)!;const first=won&&!s.cleared.includes(st.id);if(first)s.cleared.push(st.id);const xp=v.leave?0:battleXp(R,st.enemy.reduce((n,e)=>n+e.level,0)/st.enemy.length,won);const ups:string[]=[];const growth:unknown[]=[];
        next.sides[0].fighters.forEach((fighter,i)=>{const key=f.keys[i];if(!o.has(key))return;const r=rec(key,s,o),before=r.level;addXp(R,r,xp);keep(s,key,r);s.hp[key]={f:r.level>before?1:fighter.hp/fighter.maxHp,at:Date.now()};if(r.level>before)ups.push(`${r.name} Lv${before} → ${r.level} · 체력 회복`);growth.push({key,...growthInfo(r,s),name:r.name,stage:r.stage,learn:learnOffers(R,r),promo:promoSkills(R,r),evolve:canEvolve(R,r,parts(s))?nativeEvolutionChoices(R,r):[],fromLevel:before,level:r.level,xp:r.xp,xpNext:xpToNext(R,r.level),gained:xp,hp:Math.round(statsOf(R,r).hp*s.hp[key].f),maxHp:statsOf(R,r).hp,image:image(r.id,'front',r.stage)});});await game.webNativeCommit(id,item,s);committed=true;f.result={won,left:!!v.leave,xp,ups,growth,scene:st.scene??"harbor",foe:st.enemy[0].id,dialog:won?st.after:st.lose??[],chapterClear:won&&first&&st.id===st.chapter.stages.at(-1)!.id?st.chapter.clear??[]:[]};
      }else if(f.match&&!v.leave)f.result=await game.webPvpResult(id,R,f.match,events);
      else if(f.match&&v.leave){const d=await game.webPvp(id);f.result={won:false,left:true,mode:f.mode,xp:0,change:f.mode==='rank'?(f.matchInfo?.pointAfterStart??d.me.point)-(f.matchInfo?.pointBefore??d.me.point):0,...d.me};}
      else f.result={won:next.winner===0,left:!!v.leave,xp:0};f.done=true;
    }
    if(item&&!committed)await game.webNativeCommit(id,item);
    f.b=next;f.revision++;f.events=events;f.exp=Date.now()+2*3600000;syncChoice(f);
    return {...snapshot(f),inventory:await game.webItems(id)};
  });
  return {hasActiveStory:(id:string)=>[...fights.values()].some(f=>f.player===id&&f.mode==='story'&&!f.done&&f.exp>Date.now())};
}
