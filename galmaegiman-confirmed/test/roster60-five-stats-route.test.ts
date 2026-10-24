import {it,expect} from 'vitest';
import Fastify from 'fastify';
import {mkdtempSync,mkdirSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {registerRoster60} from '../src/roster60-routes.js';
it('real authenticated product route rejects an obsolete approved catalog before DB access',async()=>{
 const root=mkdtempSync(join(tmpdir(),'five-stat-route-'));const app=Fastify();
 try{
  mkdirSync(join(root,'data'));writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify({approved:true,version:'old-six-stat',characters:[]}));
  const db=new Proxy({},{get(){throw Error('DB_MUST_NOT_BE_ACCESSED');}});
  registerRoster60(app,db as never,root,r=>r.headers.authorization==='test-session'?'test-owner':null,()=>true);
  const unauthorized=await app.inject({method:'GET',url:'/my/api/native/roster60/craft/options'});expect(unauthorized.statusCode).toBe(401);
  const rejected=await app.inject({method:'GET',url:'/my/api/native/roster60/craft/options',headers:{authorization:'test-session'}});
  expect(rejected.statusCode).toBe(503);expect(rejected.json().error).toBe('FIVE_STAT_CATALOG_REQUIRED');
 }finally{await app.close();rmSync(root,{recursive:true,force:true});}
});
