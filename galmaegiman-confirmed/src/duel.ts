// 수비대 대결 시험판(/duel): 새 전투 엔진(src/battle)을 브라우저에서 그대로 돌림.
// 최상위 33종의 이름·그림이 나오므로 공개하지 않고, 비밀 키(k)가 맞을 때만 열림(주소 확인: sudo -iu ubuntu node gmm/galmaegiman-confirmed/scripts/duel-link.mjs)
import { createHmac, timingSafeEqual } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
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
      // 빌런 그림(public/battle/sprites/V1_front.webp)이 있는 것만 알려 줌. 없으면 화면이 임시 그림을 그림
      const villainArt=(await readdir(resolve(opt.root,'public/battle/sprites')).catch(()=>[] as string[])).map(f=>/^(V\d+|HALL)_front\.(webp|png)$/.exec(f)?.[1]).filter((v):v is string=>!!v);
      // 대사에 나오는 갈매미 이름(원금 갈매미맨·갈크탑·중성 갈매미 등)
      const names:Record<string,string>={};
      for(const c of story.chapters)for(const l of [...c.stages.flatMap(st=>[...st.before,...st.after,...(st.lose??[])]),...(c.clear??[])])
        if(/^[DTEIN]\d+$/.test(l.who)){const u=content.map.get(l.who);if(u)names[l.who]=u.name;}
      const bgArt=(await readdir(resolve(opt.root,'public/battle/bg')).catch(()=>[] as string[])).map(f=>/^([a-z]+)\.jpg$/.exec(f)?.[1]).filter((v):v is string=>!!v);
      // 승진 단계 그림(D1_s1_front.webp … s3): 있는 것만 알려 줌. 없으면 화면이 원형 그림 + 빛 효과
      const stageArt=(await readdir(resolve(opt.root,'public/battle/sprites')).catch(()=>[] as string[])).map(f=>/^([DTEI]\d_s[1-3]_(front|back))\.(webp|png)$/.exec(f)?.[1]).filter((v):v is string=>!!v);
      // 아이템·증강체 아이콘(public/battle/icons/potion.png): 없으면 화면이 글자 그림(이모지)
      const iconArt=(await readdir(resolve(opt.root,'public/battle/icons')).catch(()=>[] as string[])).map(f=>/^([a-z]+)\.png$/.exec(f)?.[1]).filter((v):v is string=>!!v);
      return reply.header('Cache-Control','no-store').send({rules:R.data,units,story,villainArt,names,bgArt,iconArt,stageArt});
    }catch(e){return reply.code(500).send({error:(e as Error).message});}
  });
  // 배경 그림(public/battle/bg/harbor.jpg): 없으면 화면이 색 그라데이션으로 그림
  app.get<{Params:{file:string};Querystring:{k?:string}}>('/duel/bg/:file',async(req,reply)=>{
    if(!ok(req.query.k)||!/^[a-z]+\.jpg$/.test(req.params.file))return deny(reply);
    return file(reply,'image/jpeg',resolve(opt.root,'public/battle/bg',req.params.file));
  });
  // 아이콘은 비밀이 아니라 열쇠 없이(아이템·증강체 그림)
  app.get<{Params:{file:string}}>('/duel/icons/:file',async(req,reply)=>{
    if(!/^[a-z]+\.png$/.test(req.params.file))return reply.code(404).send({error:'not found'});
    return file(reply,'image/png',resolve(opt.root,'public/battle/icons',req.params.file));
  });
  app.get<{Params:{file:string};Querystring:{k?:string}}>('/duel/sprites/:file',async(req,reply)=>{
    if(!ok(req.query.k)||!/^([DTEI]\d_s[0-3]_(front|back)|V\d+_front|HALL_front)\.webp$/.test(req.params.file))return deny(reply);
    // webp(선명하게 키운 그림)가 없으면 같은 이름의 png로(그림을 바꾸는 중에도 화면이 깨지지 않게)
    const f=resolve(opt.root,'public/battle/sprites',req.params.file);
    return existsSync(f)?file(reply,'image/webp',f):file(reply,'image/png',f.replace(/\.webp$/,'.png'));
  });
}
