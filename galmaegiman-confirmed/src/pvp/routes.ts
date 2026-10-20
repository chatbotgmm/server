import type {FastifyInstance,FastifyReply,FastifyRequest} from 'fastify';
import {z} from 'zod';
import {GameError} from '../types.js';
import type {RealtimePvpService} from './service.js';
const key=z.string().min(1).max(120);
const attempt=z.object({attemptId:key,token:key}).strict();
const action=z.discriminatedUnion('kind',[
  z.object({kind:z.literal('skill'),skillId:key}).strict(),
  z.object({kind:z.literal('item'),item:key,target:z.number().int().min(0).max(2).optional()}).strict(),
  z.object({kind:z.literal('switch'),to:z.number().int().min(0).max(2),then:key.optional()}).strict()
]);
const command=z.union([
  z.object({accept:z.boolean()}).strict(),
  z.object({prepare:z.object({team:z.array(key).length(3),ready:z.boolean()}).strict()}).strict(),
  z.object({action}).strict(),
  z.object({choose:z.object({augment:key.optional(),switchTo:z.number().int().min(0).max(2).optional()}).strict()}).strict(),
  z.object({reroll:z.number().int().min(0).max(2)}).strict()
]);
export function registerRealtimePvp(app:FastifyInstance,service:RealtimePvpService,auth:(req:FastifyRequest)=>string|null,guard:(req:FastifyRequest,reply:FastifyReply)=>boolean){
  const route=(method:'GET'|'POST',path:string,run:(id:string,body:unknown,query:unknown)=>Promise<unknown>)=>app.route({method,url:'/my/api/pvp/'+path,config:{rateLimit:{max:path==='queue/attempt'?10:120,timeWindow:'1 minute'}},handler:async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=auth(req);if(!id)return reply.code(401).send({error:'로그인을 다시 해 주세요.'});
    reply.header('Cache-Control','no-store');
    if(!await service.supportsAugmentClient(id,path,req.body,req.query,req.headers['x-gmm-augment-version']))return reply.code(409).send({error:'새 증강체를 사용하려면 앱을 업데이트해 주세요.',code:'AUGMENT_VERSION_UNSUPPORTED',augmentVersion:2});
    try{return await run(id,req.body??{},req.query??{});}
    catch(e){
      if(e instanceof GameError||e instanceof z.ZodError||(e instanceof Error&&e.message==='Invalid realtime command'))return reply.code(400).send({error:e instanceof z.ZodError?'입력 정보를 확인해 주세요.':e.message});
      throw e;
    }
  }});
  route('POST','queue/attempt',async(id,body)=>{const v=z.object({mode:z.enum(['normal','rank']),protocol:z.number().int().optional()}).strict().parse(body);if(v.protocol!==3)throw new GameError('대전 수락 기능을 사용하려면 앱을 업데이트해 주세요.');return service.attempt(id,v.mode);});
  route('POST','queue/join',async(id,body)=>{const v=attempt.parse(body);return service.join(id,v.attemptId,v.token);});
  route('POST','queue/cancel',async(id,body)=>{const v=attempt.extend({requestId:key}).strict().parse(body);return service.cancel(id,v.attemptId,v.token,v.requestId);});
  route('GET','queue/current',async id=>service.currentQueue(id));
  route('GET','room/current',async(id,_body,query)=>service.currentRoom(id,z.object({roomId:key}).strict().parse(query).roomId));
  route('POST','room/command',async(id,body)=>service.command(id,z.object({roomId:key,phaseId:key,requestId:key,command}).strict().parse(body)));
  route('POST','room/surrender',async(id,body)=>{const v=z.object({roomId:key,requestId:key}).strict().parse(body);return service.surrender(id,v.roomId,v.requestId);});
  route('GET','rank',async id=>service.rank(id));
  service.start(error=>app.log.error({error},'realtime PvP sweep failed'));
  app.addHook('onClose',async()=>service.stop());
}

