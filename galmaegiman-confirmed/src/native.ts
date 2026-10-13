// Android 전용 JSON API. 화면은 네이티브 View/Canvas, 전투는 공통 TypeScript 엔진.
import {randomBytes, randomInt} from 'node:crypto';
import {existsSync} from 'node:fs';
import type {FastifyInstance,FastifyRequest,FastifyReply} from 'fastify';
import {z} from 'zod';
import type {GameService} from './game.js';
import {GameError} from './types.js';
import {loadBattleRules} from './battle/load.js';
import {loadStory} from './battle/story.js';
import {loadContent} from './content.js';
import {createBattle,act,choose,aiChoose,pickAi,legalActions,skillProblem,itemProblem,rerollAugment,pending,type Battle,type Action} from './battle/engine.js';
import {addXp,battleXp,xpToNext,innateLearned,statsOf,slotSkills,learnOffers,learnSkill,learnPromo,promoSkills,canEvolve,evoChoices,type UnitRecord,type Stage} from './battle/growth.js';
import type {PvpEvent} from './battle/replay.js';

interface Save {units:Record<string,Partial<UnitRecord>>;team:string[];cleared:string[];hp:Record<string,{f:number;at:number}>}
interface Fight {id:string;player:string;b:Battle;keys:string[];mode:string;stage?:string;revision:number;exp:number;events:PvpEvent[];match?:string;result?:unknown;done:boolean}
const action=z.union([z.object({kind:z.literal('skill'),skillId:z.string().max(40)}).strict(),z.object({kind:z.literal('switch'),to:z.number().int().min(0).max(2)}).strict(),z.object({kind:z.literal('item'),item:z.string().max(30),target:z.number().int().min(0).max(2).optional()}).strict()]);
const turn=z.object({session:z.string().max(40),revision:z.number().int().min(0),action:action.optional(),choose:z.object({augment:z.string().max(30).optional(),switchTo:z.number().int().min(0).max(2).optional()}).strict().optional(),reroll:z.number().int().min(0).max(2).optional(),leave:z.boolean().optional()}).strict().refine(b=>[b.action,b.choose,b.reroll,b.leave].filter(x=>x!==undefined).length===1);
export function registerNative(app:FastifyInstance,game:GameService,root:string,auth:(r:FastifyRequest)=>string|null,guard:(r:FastifyRequest,p:FastifyReply)=>boolean){
  const R=loadBattleRules(root),story=loadStory(root,R),content=loadContent(root),fights=new Map<string,Fight>(),locks=new Map<string,Promise<unknown>>();
  const serial=async<T>(id:string,run:()=>Promise<T>)=>{const p=(locks.get(id)??Promise.resolve()).catch(()=>{}).then(run);locks.set(id,p);try{return await p;}finally{if(locks.get(id)===p)locks.delete(id);}};
  const route=(method:'GET'|'POST',path:string,run:(id:string,body:any)=>Promise<unknown>)=>app.route({method,url:`/my/api/native/${path}`,config:{rateLimit:{max:120,timeWindow:'1 minute'}},handler:async(req,reply)=>{
    if(!guard(req,reply))return;const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    reply.header('Cache-Control','no-store');try{return await serial(id,()=>run(id,req.body??{}));}catch(e){if(e instanceof GameError||e instanceof z.ZodError)return reply.code(400).send({error:e instanceof GameError?e.message:'입력 정보를 확인해 주세요.'});throw e;}
  }});
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
  const stages=story.chapters.flatMap(c=>c.stages.map(st=>({...st,chapter:c})));
  const unlocked=(s:Save,id:string,team:UnitRecord[])=>{const ix=stages.findIndex(st=>st.id===id);if(ix<0)return false;const st=stages[ix];return (ix===0||s.cleared.includes(stages[ix-1].id)||stages.slice(ix).some(st=>s.cleared.includes(st.id)))&&team.length>0&&team.every(r=>r.stage>=(st.chapter.needStage??0));};
  const detail=(key:string,s:Save,owned:Map<string,string>)=>{const r=rec(key,s,owned);return {...r,key,image:image(r.id,'front',r.stage),hp:Math.round(statsOf(R,r).hp*hp(s,key)),maxHp:statsOf(R,r).hp,xpNext:xpToNext(R,r.level),attr:R.attrName(R.unitDef(r.id)!.attr),skills:slotSkills(R,r),learn:learnOffers(R,r),promo:promoSkills(R,r),evolve:canEvolve(R,r,parts(s))?evoChoices(R,r,()=>0):[]};};
  route('GET','state',async id=>{const [s,o,me,items,tutorial]=await Promise.all([read(id),mine(id),game.webMe(id),game.webItems(id),game.webTutorial(id)]);const original=JSON.stringify(s);if(!me)throw new GameError('로그인을 다시 해 주세요.');
    if(!s.team.length){s.team=(me.battleTeam??[]).map(sp=>[...o].find(([,id])=>id===sp)?.[0]).filter((k):k is string=>!!k).slice(0,3);if(!s.team.length)s.team=[...o.keys()].slice(0,3);}
    s.team=s.team.map(k=>o.has(k)?k:[...o].find(([,sp])=>sp===k)?.[0]).filter((k):k is string=>!!k);
    for(const k of s.team)keep(s,k,rec(k,s,o));if(JSON.stringify(s)!==original)await game.webSaveStory(id,s);
    const team=s.team.map(k=>rec(k,s,o));return {me:await game.webMe(id),tutorial,practiceUnits:[...R.units.keys()].map(id=>({id,name:content.map.get(id)!.name,attr:R.attrName(R.unitDef(id)!.attr),image:image(id)})),names:Object.fromEntries([...content.characters.map(c=>[c.id,c.name]),...[...R.villains].map(([id,v])=>[id,v.name])]),portraits:Object.fromEntries([...R.units.keys(),...R.villains.keys()].map(id=>[id,image(id)])),team:s.team,units:[...o.keys()].map(k=>detail(k,s,o)),chapters:story.chapters.map(c=>({...c,stages:c.stages.map(st=>({...st,open:unlocked(s,st.id,team),cleared:s.cleared.includes(st.id)}))})),items,itemDefs:R.data.items,typeChart:R.data.chart,attributes:R.data.attributes};
  });
  route('POST','team',async(id,body)=>{const b=z.object({keys:z.array(z.string().regex(/^o\d+$/)).min(1).max(3)}).strict().parse(body),o=await mine(id);if(new Set(b.keys).size!==b.keys.length||b.keys.some(k=>!o.has(k)))throw new GameError('내 갈매미 1~3마리를 골라 주세요.');const s=await read(id);s.team=b.keys;for(const k of s.team)keep(s,k,rec(k,s,o));await game.webSaveStory(id,s);await game.webSetBattleTeam(id,[...new Set(b.keys.map(k=>o.get(k)!))]);return {ok:true};});
  route('POST','growth',async(id,body)=>{const b=z.object({key:z.string().regex(/^o\d+$/),kind:z.enum(['learn','promo','evolve']),skill:z.string().max(40).nullable().optional(),replace:z.string().max(40).optional(),augment:z.string().max(40).optional()}).strict().parse(body),s=await read(id),o=await mine(id),r=rec(b.key,s,o);
    try{if(b.kind==='learn')learnSkill(R,r,b.skill??null,b.replace);else if(b.kind==='promo')learnPromo(R,r,b.skill??'',b.replace);else{if(!canEvolve(R,r,parts(s)))throw new Error('승진 조건을 확인해 주세요.');if(!evoChoices(R,r,()=>0).some(a=>a.id===b.augment))throw new Error('표창을 다시 골라 주세요.');r.stage=(r.stage+1) as Stage;r.evoAugs.push(b.augment!);s.hp[b.key]={f:1,at:Date.now()};}}catch(e){throw new GameError((e as Error).message);}
    keep(s,b.key,r);await game.webSaveStory(id,s);return {ok:true};
  });
  route('POST','heal',async(id,body)=>{const b=z.object({key:z.string().regex(/^o\d+$/),item:z.string().max(30)}).strict().parse(body),s=await read(id),o=await mine(id);rec(b.key,s,o);const it=R.data.items[b.item];if(!it?.heal)throw new GameError('회복 아이템을 골라 주세요.');const f=hp(s,b.key);if(f>=1)throw new GameError('체력이 가득해요.');s.hp[b.key]={f:Math.min(1,f+it.heal/100),at:Date.now()};await game.webNativeCommit(id,{id:b.item,name:it.name},s);return {ok:true};});
  function snapshot(f:Fight){const b=f.b;return {session:f.id,revision:f.revision,mode:f.mode,phase:b.phase,turn:b.turn,winner:b.winner,result:f.result,log:b.log.slice(-50),teams:b.sides.map((s,side)=>({active:s.active,needSwitch:s.needSwitch,offers:s.offers?.map((id,i)=>({...R.battleAug.get(id),rerolled:s.rerolled[i]}))??[],fighters:s.fighters.map(x=>({key:x.rec.uid,id:x.rec.id,name:x.name,level:x.rec.level,stage:x.rec.stage,image:image(x.rec.id,side===0?'back':'front',x.rec.stage),hp:x.hp,maxHp:x.maxHp,attr:R.attrName(x.attr),status:x.status,skills:x.skills.map(sk=>({...sk,reason:skillProblem(b,side as 0|1,sk)}))})),legal:b.phase==='action'?legalActions(b,side as 0|1):[],itemsUsed:s.itemsUsed})),items:Object.entries(R.data.items).map(([id,it])=>({id,...it,reason:itemProblem(b,0,id)}))};}
  route('POST','battle/start',async(id,body)=>{const v=z.object({mode:z.enum(['story','practice','normal','rank']),stage:z.string().max(12).optional(),roster:z.array(z.string()).min(1).max(3).optional(),enemyRoster:z.array(z.string()).min(1).max(3).optional(),level:z.number().int().min(1).max(60).optional(),promotion:z.number().int().min(0).max(3).optional()}).strict().refine(v=>v.mode==='practice'||[v.roster,v.enemyRoster,v.level,v.promotion].every(x=>x===undefined)).parse(body);const now=Date.now();for(const [k,f] of fights)if(f.exp<now)fights.delete(k);if([...fights.values()].some(f=>f.player===id&&!f.done))throw new GameError('진행 중인 전투로 돌아가 주세요.');if(fights.size>=20000)throw new GameError('잠시 후 다시 시도해 주세요.');
    const s=await read(id),o=await mine(id);let team=s.team.filter(k=>o.has(k)).map(k=>rec(k,s,o)),foe:UnitRecord[],seed=randomInt(2**31-1),match:string|undefined;
    if(v.mode==='normal'||v.mode==='rank'){const m=await game.webPvpMatch(id,v.mode);team=m.mine;foe=m.foe;seed=m.seed;match=m.match;}
    else{
      const practice=(ids:string[],prefix:string):UnitRecord[]=>ids.map((id,i)=>{if(!R.units.has(id))throw new GameError('연습용 갈매미를 다시 골라 주세요.');return {uid:prefix+i,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as UnitRecord['tier'],level:v.level??15,xp:0,stage:(v.promotion??0) as Stage,evoAugs:[],learned:['@','basic','sig','role','sub']};});
      if(v.mode==='practice')team=practice(v.roster??['D1','D2','D3'],'practice');
      if(!team.length)throw new GameError('먼저 수비대를 편성해 주세요.');const st=stages.find(st=>st.id===(v.stage??'P-1'));if(!st||v.mode==='story'&&!unlocked(s,st.id,team))throw new GameError('앞 전투와 승진 조건을 확인해 주세요.');if(v.mode==='story'&&s.team.some(k=>Math.round(statsOf(R,rec(k,s,o)).hp*hp(s,k))<=0))throw new GameError('체력이 0인 갈매미를 회복해 주세요.');foe=st.enemy.map((e,i)=>({uid:`foe${i}`,id:e.id,name:R.villains.get(e.id)!.name!,tier:'LIMITED',level:e.level,xp:0,stage:(e.stage??st.chapter.needStage??0) as Stage,evoAugs:[]}));if(v.mode==='practice'&&v.enemyRoster)foe=practice(v.enemyRoster,'practice-foe');}
    const b=createBattle(R,team,foe,seed);if(v.mode==='story')b.sides[0].fighters.forEach((f,i)=>f.hp=Math.max(1,Math.round(f.maxHp*hp(s,s.team[i]))));
    const f:Fight={id:randomBytes(16).toString('base64url'),player:id,b,keys:s.team,mode:v.mode,stage:v.stage??'P-1',revision:0,exp:now+2*3600000,events:[],match,done:false};fights.set(f.id,f);return {...snapshot(f),inventory:await game.webItems(id)};
  });
  route('GET','battle/current',async id=>{const f=[...fights.values()].find(f=>f.player===id&&!f.done&&f.exp>Date.now());return f?snapshot(f):{session:null};});
  route('POST','battle/turn',async(id,body)=>{const v=turn.parse(body),f=fights.get(v.session);if(!f||f.player!==id||f.exp<Date.now())throw new GameError('전투가 만료됐어요. 다시 시작해 주세요.');if(v.revision!==f.revision)return snapshot(f);if(f.done)return snapshot(f);
    const b=f.b;
    // 실패 시 턴/RNG/아이템이 일부만 바뀌지 않도록 복사에서 먼저 검증한다.
    const next:Battle={...b,sides:structuredClone(b.sides),log:[...b.log]};let event:PvpEvent|undefined;
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
      if(f.mode==='story'){const s=await read(id),o=await mine(id),won=next.winner===0,st=stages.find(st=>st.id===f.stage)!;const xp=v.leave?0:battleXp(R,st.enemy.reduce((n,e)=>n+e.level,0)/st.enemy.length,won);const ups:string[]=[];
        next.sides[0].fighters.forEach((fighter,i)=>{const key=f.keys[i];if(!o.has(key))return;const r=rec(key,s,o),before=r.level;addXp(R,r,xp);keep(s,key,r);s.hp[key]={f:r.level>before?1:fighter.hp/fighter.maxHp,at:Date.now()};if(r.level>before)ups.push(`${r.name} Lv${before} → ${r.level} · 체력 회복`);});if(won&&!s.cleared.includes(st.id))s.cleared.push(st.id);await game.webNativeCommit(id,item,s);committed=true;f.result={won,xp,ups,dialog:won?st.after:st.lose??[],chapterClear:won&&st.id===st.chapter.stages.at(-1)!.id?st.chapter.clear??[]:[]};
      }else if(f.match&&!v.leave)f.result=await game.webPvpResult(id,R,f.match,events);
      else f.result={won:next.winner===0,xp:0};f.done=true;
    }
    if(item&&!committed)await game.webNativeCommit(id,item);
    f.b=next;f.revision++;f.events=events;f.exp=Date.now()+2*3600000;
    return {...snapshot(f),inventory:await game.webItems(id)};
  });
}
