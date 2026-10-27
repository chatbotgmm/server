import {it,expect,vi} from 'vitest';
import Fastify from 'fastify';
import type {PrismaClient} from '@prisma/client';
import {registerRoster60} from '../src/roster60-routes.js';
import {RosterCraft} from '../src/roster60-craft.js';

it('craft disclosure is opt-in only for the exact masking-aware client capability',async()=>{
 const options=vi.spyOn(RosterCraft.prototype,'options').mockResolvedValue({catalogVersion:'synthetic',discoveryVersion:undefined,truncated:false,units:[],recipes:[]});
 const app=Fastify();registerRoster60(app,{} as PrismaClient,process.cwd(),()=> 'synthetic-player',()=>true);
 try{
  for(const [header,expected] of [[undefined,false],['1',true],['yes',false]] as const){
   const response=await app.inject({url:'/my/api/native/roster60/craft/options',headers:header?{'X-GMM-Craft-Discovery':header}:{}});
   expect(response.statusCode).toBe(200);expect(options).toHaveBeenLastCalledWith('synthetic-player',expected);
  }
 }finally{await app.close();options.mockRestore();}
});
