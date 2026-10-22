import {test} from 'node:test';
import assert from 'node:assert/strict';
import {registerNews} from '../src/news-routes.js';
import {NewsError} from '../src/news.js';
test('route boundary only registers read/list; requires guard and authenticated owner',async()=>{
 const routes:any[]=[];let allowed=true,player:string|null='alice';const calls:any[]=[];
 const service:any={list:async(...args:any[])=>{calls.push(args);return {items:[]};},read:async()=>{throw new NewsError(404,'NOT_FOUND');}};
 registerNews({route:(r:any)=>routes.push(r)} as any,service,()=>player,()=>allowed);
 assert.deepEqual(routes.map(r=>r.method+' '+r.url),['GET /my/api/native/news/notices','GET /my/api/native/news/events','GET /my/api/native/news/inbox','POST /my/api/native/news/read']);
 const reply:any={status:200,payload:null,headers:{} as any,header(k:string,v:string){this.headers[k]=v;return this;},code(n:number){this.status=n;return this;},send(p:any){this.payload=p;return this;}};
 allowed=false;await routes[0].handler({},reply);assert.equal(calls.length,0);
 allowed=true;player=null;await routes[0].handler({},reply);assert.equal(reply.status,401);assert.equal(calls.length,0);
 player='alice';await routes[0].handler({query:{limit:'1'}},reply);assert.deepEqual(calls[0],['alice','notices',{limit:'1'}]);
 await routes[3].handler({body:{}},reply);assert.equal(reply.status,404);assert.equal(reply.headers['Cache-Control'],'no-store');
});
