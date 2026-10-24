import type {FastifyInstance,FastifyRequest,FastifyReply} from 'fastify';
import {readFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {z} from 'zod';
import {RosterOnboarding,RosterError,type RosterCatalog} from './roster60-onboarding.js';
import type {PrismaClient} from '@prisma/client';
import {RosterCraft} from './roster60-craft.js';
export function registerRoster60(app:FastifyInstance,db:PrismaClient,root:string,auth:(r:FastifyRequest)=>string|null,guard:(r:FastifyRequest,p:FastifyReply)=>boolean){
  const file=resolve(root,'data/roster60-approved.json');
  // No fallback to review fixtures or legacy character definitions.
  const catalog=()=>{if(!existsSync(file))return null;try{return JSON.parse(readFileSync(file,'utf8')) as RosterCatalog;}catch{throw new RosterError(503,'ROSTER_CATALOG_INVALID');}};
  const service=new RosterOnboarding(db,catalog);
  const craft=new RosterCraft(db,catalog);
  const empty=z.object({}).strict();
  const route=(method:'GET'|'POST',path:string,run:(id:string,r:FastifyRequest)=>Promise<unknown>)=>app.route({method,url:'/my/api/native/roster60/'+path,bodyLimit:2048,config:{rateLimit:{max:60,timeWindow:'1 minute'}},handler:async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;
    const id=auth(req);if(!id)return reply.code(401).send({error:'login'});
    try{return await run(id,req);}catch(e){
      if(e instanceof RosterError)return reply.code(e.status).send({error:e.code});
      if(e instanceof z.ZodError)return reply.code(400).send({error:'INVALID_REQUEST'});
      throw e;
    }
  }});
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
