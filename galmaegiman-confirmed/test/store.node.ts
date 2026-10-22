import {test} from 'node:test';
import assert from 'node:assert/strict';
import {prismaNewsStore} from '../src/news-store.js';
const now=new Date('2026-10-21T00:00:00Z');
test('Prisma adapter applies owner, visibility, cursor, bounded take and DTO allowlist',async()=>{
 let query:any;const store=prismaNewsStore({nativeInboxMessage:{findMany:async(q:any)=>{query=q;return [{id:'m',title:'t',bodyText:'b',publishedAt:now,expiresAt:null,playerId:'alice',secret:'PRIVATE'}];}}} as any);
 const rows=await store.inbox('alice',now,{limit:21,after:[now.toISOString(),'z']});
 assert.equal(query.take,21);assert.equal(query.where.playerId,'alice');assert.deepEqual(query.where.publishedAt,{lte:now});assert.deepEqual(query.where.OR,[{expiresAt:null},{expiresAt:{gt:now}}]);assert.equal(query.where.AND[0].OR[1].id.lt,'z');assert.equal('secret' in rows[0],false);assert.equal('playerId' in rows[0],false);
});
test('transaction rechecks owner before marking; conflicting replay never marks',async()=>{
 let upserts=0,checks:any;let old:any=null;
 const tx={nativeNewsRequest:{findUnique:async()=>old,create:async()=>{}},nativeInboxMessage:{findFirst:async(q:any)=>{checks=q;return null;}},nativeNewsRead:{upsert:async()=>{upserts++;return {readAt:now};}}};
 const store=prismaNewsStore({$transaction:async(fn:any)=>fn(tx)} as any);
 await assert.rejects(()=>store.mark('alice','inbox','other','request_1',now),{status:404});assert.equal(checks.where.playerId,'alice');assert.equal(upserts,0);
 old={kind:'notices',itemId:'old',readAt:now};await assert.rejects(()=>store.mark('alice','notices','new','request_1',now),{status:409});assert.equal(upserts,0);
 assert.equal(await store.mark('alice','notices','old','request_1',new Date(now.getTime()+1000)),now.toISOString());assert.equal(upserts,0);
});
test('unique-race retry reloads durable request and preserves timestamp',async()=>{
 let tries=0;const store=prismaNewsStore({$transaction:async(fn:any)=>{if(tries++===0)throw Object.assign(new Error('race'),{code:'P2002'});return fn({nativeNewsRequest:{findUnique:async()=>({kind:'notices',itemId:'n',readAt:now})}});}} as any);
 assert.equal(await store.mark('alice','notices','n','request_1',new Date()),now.toISOString());assert.equal(tries,2);
});
