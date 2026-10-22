import type {PrismaClient} from '@prisma/client';
import {NewsError,type NewsStore,type NewsKind} from './news.js';
// Requires the additive schema fragment. No publisher or reward-grant method exists.
export function prismaNewsStore(db:PrismaClient):NewsStore{return {
  async inbox(playerId,now,selection){return (await db.nativeInboxMessage.findMany({take:selection.limit,where:{playerId,...(selection.id?{id:selection.id}:{}),...(selection.after?{AND:[{OR:[{publishedAt:{lt:new Date(selection.after[0])}},{publishedAt:new Date(selection.after[0]),id:{lt:selection.after[1]}}]}]}:{}),publishedAt:{lte:now},OR:[{expiresAt:null},{expiresAt:{gt:now}}]},orderBy:[{publishedAt:'desc'},{id:'desc'}]})).map(r=>({id:r.id,title:r.title,bodyText:r.bodyText,publishedAt:r.publishedAt.toISOString(),expiresAt:r.expiresAt?.toISOString()??null}));},
  async reads(playerId,kind,ids){const rows=await db.nativeNewsRead.findMany({where:{playerId,kind,itemId:{in:ids}}});return Object.fromEntries(rows.map(r=>[r.itemId,r.readAt.toISOString()]));},
  async mark(playerId,kind,id,requestId,now){const run=()=>db.$transaction(async tx=>{
    const old=await tx.nativeNewsRequest.findUnique({where:{playerId_requestId:{playerId,requestId}}});
    if(old){if(old.kind!==kind||old.itemId!==id)throw new NewsError(409,'REQUEST_ID_REUSED');return old.readAt.toISOString();}
    // Ownership and visibility rechecked inside the write transaction.
    if(kind==='inbox'&&!await tx.nativeInboxMessage.findFirst({where:{id,playerId,publishedAt:{lte:now},OR:[{expiresAt:null},{expiresAt:{gt:now}}]}}))throw new NewsError(404,'NOT_FOUND');
    const read=await tx.nativeNewsRead.upsert({where:{playerId_kind_itemId:{playerId,kind,itemId:id}},create:{playerId,kind,itemId:id,readAt:now},update:{}});
    await tx.nativeNewsRequest.create({data:{playerId,requestId,kind,itemId:id,readAt:read.readAt}});return read.readAt.toISOString();
  });try{return await run();}catch(e){if((e as {code?:string}).code==='P2002')return run();throw e;}}
};}
