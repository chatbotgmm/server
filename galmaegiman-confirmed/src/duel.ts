// 수비대 대결 시험판(/duel): 새 전투 엔진(src/battle)을 브라우저에서 그대로 돌림.
// 최상위 33종의 이름·그림이 나오므로 공개하지 않고, 비밀 키(k)가 맞을 때만 열림(키는 npm run duel-link 로 확인)
import { createHmac, timingSafeEqual } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply } from 'fastify';
import type { Content } from './content.js';
import { loadBattleRules } from './battle/load.js';
import { loadStory } from './battle/story.js';

export const duelKey=(secret:string)=>createHmac('sha256',secret).update('duel-preview').digest('base64url').slice(0,22);

export function registerDuel(app:FastifyInstance,content:Content,opt:{root:string;secret:string}){
  const key=Buffer.from(duelKey(opt.secret));
  const ok=(k:unknown)=>typeof k==='string'&&k.length===key.length&&timingSafeEqual(Buffer.from(k),key);
  const deny=(reply:FastifyReply)=>reply.code(404).send({error:'not found'});
  const file=async(reply:FastifyReply,type:string,path:string)=>{
    const body=await readFile(path).catch(()=>null);
    return body?reply.type(type).header('Cache-Control','private, max-age=300').send(body):deny(reply);
  };
  app.get<{Querystring:{k?:string}}>('/duel',async(req,reply)=>ok(req.query.k)?file(reply,'text/html; charset=utf-8',resolve(opt.root,'public/duel/index.html')):deny(reply));
  // 묶음 파일(엔진 코드)에는 유닛 이름·그림이 없어서 키 없이 제공
  app.get('/duel/duel.js',async(_req,reply)=>file(reply,'text/javascript; charset=utf-8',resolve(opt.root,'dist/duel/duel.js')));
  // 규칙·스토리는 요청 때마다 읽음: battle.yaml·story.yaml을 고치면 서버를 다시 켜지 않아도 반영. 잘못 고쳤으면 어디가 틀렸는지 알려 줌
  app.get<{Querystring:{k?:string}}>('/duel/data.json',async(req,reply)=>{
    if(!ok(req.query.k))return deny(reply);
    try{
      const R=loadBattleRules(opt.root);
      const units=[...R.units.keys()].map(id=>{const c=content.map.get(id);return {id,name:c?.name??id,tier:c?.rarity??''};});
      const story=loadStory(opt.root,R);
      // 빌런 그림(public/battle/sprites/V1_front.png)이 있는 것만 알려 줌. 없으면 화면이 임시 그림을 그림
      const villainArt=(await readdir(resolve(opt.root,'public/battle/sprites')).catch(()=>[] as string[])).map(f=>/^(V\d+)_front\.png$/.exec(f)?.[1]).filter((v):v is string=>!!v);
      return reply.header('Cache-Control','no-store').send({rules:R.data,units,story,villainArt});
    }catch(e){return reply.code(500).send({error:(e as Error).message});}
  });
  app.get<{Params:{file:string};Querystring:{k?:string}}>('/duel/sprites/:file',async(req,reply)=>{
    if(!ok(req.query.k)||!/^([DTEI]\d_s[0-3]_(front|back)|V\d+_front)\.png$/.test(req.params.file))return deny(reply);
    return file(reply,'image/png',resolve(opt.root,'public/battle/sprites',req.params.file));
  });
}
