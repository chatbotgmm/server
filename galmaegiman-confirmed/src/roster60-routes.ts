import {MenuAccess,menuFeatureFor,menuChangeSchema} from './menu-access.js';
import {RosterPvp} from './roster60-pvp.js';
import {RecruitSessions,type RecruitSessionPolicy} from './roster60-recruit-session.js';
import {RosterAdventure} from './roster60-adventure.js';
import {RosterLibrary} from './roster60-library.js';
import {RosterHome} from './roster60-home.js';
import type {FastifyInstance,FastifyRequest,FastifyReply} from 'fastify';
import {readFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {z} from 'zod';
import {RosterOnboarding,RosterError,type RosterCatalog} from './roster60-onboarding.js';
import type {PrismaClient} from '@prisma/client';
import {RosterReincarnation} from './roster60-reincarnation.js';
import {validateFiveStatCatalog} from './battle/roster60-five-stats.js';
import {RosterBattle,fighter,type BattlePolicy} from './roster60-battle.js';
import {RosterCraft} from './roster60-craft.js';
export function registerRoster60(app:FastifyInstance,db:PrismaClient,root:string,auth:(r:FastifyRequest)=>string|null,guard:(r:FastifyRequest,p:FastifyReply)=>boolean,menuLocks=false){
  const profile=process.env.ROSTER60_PROFILE;
  if(profile && profile!=='five-stat-device-test-v1')throw Error('UNKNOWN_ROSTER60_PROFILE');
  const file=resolve(root,profile?'data/test-profiles/'+profile+'/catalog.json':'data/roster60-approved.json');
  // No fallback to review fixtures or legacy character definitions.
  const catalog=()=>{if(!existsSync(file))return null;try{const c=JSON.parse(readFileSync(file,'utf8'));validateFiveStatCatalog(c);return c as RosterCatalog;}catch(e){throw new RosterError(503,e instanceof Error&&['FIVE_STAT_CATALOG_REQUIRED','FIVE_STAT_CATALOG_INVALID','FIVE_STATS_REQUIRED','INVALID_FIVE_STATS','BASE_MP_MISMATCH'].includes(e.message)?e.message:'ROSTER_CATALOG_INVALID');}};
  const menus=new MenuAccess(db,catalog);
  // Production server opts in explicitly; isolated legacy suites can test their own policies independently.
  if(menuLocks){
    app.addHook('preHandler',async(req,reply)=>{
      if(!req.url.startsWith('/my/api/'))return;
      let feature=menuFeatureFor(decodeURIComponent(req.url.split('?')[0]));
      if(req.url.split('?')[0]==='/my/api/native/roster60/battle/create'){
        const body=req.body as {adventureId?:unknown;encounterId?:unknown}|undefined;
        if(body?.adventureId)feature='adventure';
        else if(typeof body?.encounterId==='string'){
          const policy=battlePolicy();if(policy?.encounters?.some(e=>e.id===body.encounterId&&e.mode==='adventure'))feature='adventure';
        }
      }
      // Legacy story-save also carries an editable party. Preserve same-party progress saves,
      // but do not let it bypass a locked party editor (including clearing the saved team).
      if(req.method==='POST'&&req.url.split('?')[0]==='/my/api/story'){
        const owner=auth(req);if(!owner)return reply.code(401).send({error:'login'});
        const saved=await db.storySave.findUnique({where:{playerId:owner}});
        const before=saved?JSON.parse(saved.data).team??[]:[];
        const data=(req.body as {data?:{team?:unknown}|null}|undefined)?.data;
        if(JSON.stringify(data?.team??[])!==JSON.stringify(before))feature='party';
      }
      if(!feature)return;
      const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
      try{await menus.require(id,feature);}catch(e){if(e instanceof RosterError)return reply.code(e.status).send({error:e.code,code:'MENU_LOCKED',featureId:feature});throw e;}
    });
  }
  app.get('/my/api/native/menu-access',async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;
    const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    return {...await menus.read(id),enforcementEnabled:menuLocks};
  });
  app.get('/my/api/admin/menu-access',async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;
    const actor=auth(req);if(!actor)return reply.code(401).send({error:'login'});
    const player=await db.player.findUnique({where:{id:actor},select:{isAdmin:true,identity:true}});
    if(!player?.isAdmin||player.identity.startsWith('review:'))return reply.code(403).send({error:'ADMIN_REQUIRED'});
    try{const q=z.object({playerId:z.string().min(1).max(100)}).strict().parse(req.query);return await menus.read(q.playerId);}
    catch(e){if(e instanceof RosterError)return reply.code(e.status).send({error:e.code});if(e instanceof z.ZodError)return reply.code(400).send({error:'INVALID_REQUEST'});throw e;}
  });
  app.post('/my/api/admin/menu-access',{bodyLimit:2048},async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;
    const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    try{return await menus.change(id,menuChangeSchema.parse(req.body));}
    catch(e){if(e instanceof RosterError)return reply.code(e.status).send({error:e.code});if(e instanceof z.ZodError)return reply.code(400).send({error:'INVALID_REQUEST'});throw e;}
  });
  const policyFile=resolve(root,profile?'data/test-profiles/'+profile+'/battle-policy.json':'data/roster60-battle-policy.json');
  const battlePolicy=()=>{if(!existsSync(policyFile))return null;try{return JSON.parse(readFileSync(policyFile,'utf8')) as BattlePolicy;}catch{throw new RosterError(503,'BATTLE_POLICY_INVALID');}};
  const battle=new RosterBattle(db,catalog,battlePolicy);
  const pvp=new RosterPvp(db,catalog,battlePolicy);
  let pvpTimer:ReturnType<typeof setInterval>|undefined,pvpWork:Promise<unknown>|undefined;
  app.addHook('onReady',async()=>{pvpTimer=setInterval(()=>{if(pvpWork)return;pvpWork=pvp.tick().catch(e=>app.log.error({err:e},'roster pvp deadline sweep')).finally(()=>{pvpWork=undefined;});},1000);pvpTimer.unref();});
  app.addHook('onClose',async()=>{if(pvpTimer)clearInterval(pvpTimer);await pvpWork;});
  const service=new RosterOnboarding(db,catalog);
  const adventure=new RosterAdventure(db,catalog,fighter);
  const library=new RosterLibrary(db,catalog);
  // No implicit prices, odds, rounding or pity. Explicit reviewed policy file required.
  const recruitPolicy=()=>{const path=resolve(root,'data/roster60-recruit-policy.json');return existsSync(path)?JSON.parse(readFileSync(path,'utf8')) as RecruitSessionPolicy:null;};
  const recruitSessions=new RecruitSessions(db,catalog,recruitPolicy);
  const home=new RosterHome(db,catalog);
  const craft=new RosterCraft(db,catalog);
  const reincarnation=new RosterReincarnation(db,catalog);
  const empty=z.object({}).strict();
  const route=(method:'GET'|'POST',path:string,run:(id:string,r:FastifyRequest)=>Promise<unknown>)=>app.route({method,url:'/my/api/native/roster60/'+path,bodyLimit:2048,config:{rateLimit:{max:60,timeWindow:'1 minute',...(path.startsWith('pvp/')?{keyGenerator:(req:FastifyRequest)=>'roster-pvp:'+(auth(req)??'ip:'+req.ip)}:{})}},handler:async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;
    const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    try{return await run(id,req);}catch(e){
      if(e instanceof RosterError)return reply.code(e.status).send({error:e.code});
      if(e instanceof z.ZodError)return reply.code(400).send({error:'INVALID_REQUEST'});
      throw e;
    }
  }});
  const requestId=z.string().min(1).max(100),battleId=z.string().uuid(),revision=z.number().int().nonnegative();
  route('GET','adventure/active',id=>adventure.active(id));
  route('POST','adventure/read',(id,r)=>adventure.read(id,z.object({adventureId:z.string().uuid()}).strict().parse(r.body).adventureId));
  route('POST','adventure/start',(id,r)=>adventure.start(id,z.object({requestId,ownedIds:z.array(z.string().min(1).max(100)).min(1).max(6)}).strict().parse(r.body)));
  route('POST','adventure/return',(id,r)=>adventure.return(id,z.object({requestId,adventureId:z.string().uuid(),revision}).strict().parse(r.body)));
  route('GET','collection',id=>library.collection(id));
  route('GET','recruit',id=>library.info(id));
  route('POST','recruit',(id,r)=>library.recruit(id,z.object({requestId,kind:z.enum(['LOW','MID','HIGH']),times:z.number().int().min(1).max(50),catalogVersion:z.string().min(1).max(100)}).strict().parse(r.body)));
  route('GET','recruit/session',id=>recruitSessions.info(id));
  route('POST','recruit/session/read',(id,r)=>recruitSessions.read(id,z.object({sessionId:z.string().uuid()}).strict().parse(r.body).sessionId));
  route('POST','recruit/session/start',(id,r)=>recruitSessions.start(id,z.object({requestId,kind:z.enum(['LOW','MID','HIGH']),catalogVersion:z.string().min(1).max(100)}).strict().parse(r.body)));
  route('POST','recruit/session/action',(id,r)=>recruitSessions.action(id,z.object({requestId,sessionId:z.string().uuid(),revision,action:z.enum(['reroll','claim'])}).strict().parse(r.body)));
  route('GET','home',id=>home.read(id));
  route('POST','home/rebirth-intro/complete',(id,r)=>{empty.parse(r.body);return home.completeIntro(id);});
  route('GET','battle/options',id=>battle.options(id));
  route('POST','battle/create',(id,r)=>battle.create(id,z.object({requestId,ownedId:z.string().min(1).max(100),encounterId:z.string().min(1).max(100),reserveIds:z.array(z.string().min(1).max(100)).max(5).optional(),adventureId:z.string().uuid().optional()}).strict().parse(r.body)));
  route('POST','battle/read',(id,r)=>battle.read(id,z.object({battleId}).strict().parse(r.body).battleId));
  route('GET','battle/active',id=>battle.active(id));
  const action=z.discriminatedUnion('kind',[z.object({kind:z.literal('attack')}).strict(),z.object({kind:z.literal('focus')}).strict(),z.object({kind:z.literal('switch'),targetOwnedId:z.string().min(1).max(100)}).strict(),z.object({kind:z.literal('item'),itemId:z.string().min(1).max(100),targetOwnedId:z.string().min(1).max(100).optional()}).strict(),z.object({kind:z.literal('escape')}).strict(),z.object({kind:z.literal('skill'),skillId:z.string().min(1).max(100)}).strict()]);
  route('GET','pvp/options',id=>pvp.options(id));
  route('POST','pvp/party',(id,r)=>pvp.party(id,z.object({ownedIds:z.array(z.string().min(1).max(100)).min(1).max(6)}).strict().parse(r.body).ownedIds));
  route('POST','pvp/join',(id,r)=>pvp.join(id,z.object({requestId,mode:z.enum(['normal','rank'])}).strict().parse(r.body)));
  route('POST','pvp/cancel',(id,r)=>pvp.cancel(id,z.object({requestId}).strict().parse(r.body)));
  route('GET','pvp/current',id=>pvp.current(id));
  route('POST','pvp/accept',(id,r)=>pvp.accept(id,z.object({requestId,battleId,revision,accept:z.boolean()}).strict().parse(r.body)));
  route('POST','pvp/action',(id,r)=>pvp.action(id,z.object({requestId,battleId,revision,action}).strict().parse(r.body)));
  route('POST','pvp/settle',(id,r)=>pvp.settle(id,z.object({requestId,battleId,revision}).strict().parse(r.body)));
  route('POST','battle/action',(id,r)=>battle.action(id,z.object({requestId,battleId,revision,action}).strict().parse(r.body)));
  route('POST','battle/settle',(id,r)=>battle.settle(id,z.object({requestId,battleId,revision}).strict().parse(r.body)));
  route('GET','reincarnation/preview',id=>reincarnation.preview(id));
  route('POST','reincarnation/confirm',(id,r)=>{const b=z.object({ownedId:z.string().min(1).max(100),requestId:z.string().min(1).max(100),catalogVersion:z.string().min(1).max(100),previewFingerprint:z.string().regex(/^[a-f0-9]{64}$/)}).strict().parse(r.body);return reincarnation.confirm(id,b);});
  route('GET','state',id=>service.state(id));
  route('POST','start',(id,r)=>{empty.parse(r.body);return service.start(id);});
  route('POST','complete',(id,r)=>{empty.parse(r.body);return service.complete(id);});
  route('POST','preview',(id,r)=>{const b=z.object({ticketId:z.string().min(1).max(100)}).strict().parse(r.body);return service.preview(id,b.ticketId);});
  route('POST','confirm',(id,r)=>{const b=z.object({ticketId:z.string().min(1).max(100),characterId:z.string(),catalogVersion:z.string().min(1).max(100)}).strict().parse(r.body);return service.confirm(id,b.ticketId,b.characterId,b.catalogVersion);});
  const selection=z.object({resultId:z.string().regex(/^u\d{2}$/),materialIds:z.array(z.string().min(1).max(100)).min(1).max(6),catalogVersion:z.string().min(1).max(100)}).strict();
  route('POST','craft/preview',(id,r)=>craft.preview(id,selection.parse(r.body)));
  route('GET','craft/options',id=>craft.options(id));
  route('POST','craft/receipt',(id,r)=>{const b=z.object({requestId:z.string().min(1).max(100)}).strict().parse(r.body);return craft.receipt(id,b.requestId);});
  route('POST','craft/confirm',(id,r)=>craft.confirm(id,selection.extend({requestId:z.string().min(1).max(100),previewFingerprint:z.string().regex(/^[a-f0-9]{64}$/)}).strict().parse(r.body)));
}
