import type {FastifyInstance,FastifyRequest,FastifyReply} from 'fastify';
import {NewsError,type NewsService} from './news.js';
export function registerNews(app:FastifyInstance,news:NewsService,auth:(r:FastifyRequest)=>string|null,guard:(r:FastifyRequest,p:FastifyReply)=>boolean){
  const route=(method:'GET'|'POST',suffix:string,run:(player:string,req:FastifyRequest)=>Promise<unknown>)=>app.route({method,url:'/my/api/native/news/'+suffix,bodyLimit:2048,config:{rateLimit:{max:60,timeWindow:'1 minute'}},handler:async(req,reply)=>{
    reply.header('Cache-Control','no-store');if(!guard(req,reply))return;const player=auth(req);if(!player)return reply.code(401).send({error:'login'});
    try{return await run(player,req);}catch(e){if(e instanceof NewsError)return reply.code(e.status).send({error:e.code});throw e;}
  }});
  for(const kind of ['notices','events','inbox'] as const)route('GET',kind,(player,req)=>news.list(player,kind,(req.query??{}) as Record<string,unknown>));
  route('POST','read',(player,req)=>news.read(player,req.body));
}
