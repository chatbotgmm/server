// Manual draft target: test/item-escrow.integration.test.ts.
import {beforeAll,beforeEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient,type Prisma} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {holdItem,consumePhaseItems,refundRoomItems,inventoryForPlayer} from '../src/pvp/item-escrow.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('realtime item escrow (isolated SQLite)',()=>{
  let db:PrismaClient,game:GameService,roomId:string,playerId:string,phaseId:string;
  const tx=<T>(work:(t:Prisma.TransactionClient)=>Promise<T>)=>db.$transaction(async t=>{
    await t.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
    return work(t);
  });
  const expected=()=>[{side:0 as const,playerId,itemId:'potion'}];
  const hold=()=>tx(t=>holdItem(t,{roomId,phaseId,...expected()[0]}));
  const stock=()=>db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId,itemId:'potion'}}});
  beforeAll(()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('Isolated test DB required');
    db=new PrismaClient({datasources:{db:{url}}});game=new GameService(db,loadContent());
  });
  beforeEach(async()=>{
    const p=await db.player.create({data:{identity:'escrow-'+randomUUID(),snack:10000}});
    const foe=await db.player.create({data:{identity:'escrow-'+randomUUID()}});
    playerId=p.id;roomId=randomUUID();phaseId=roomId+':1';
    await db.realtimeRoom.create({data:{id:roomId,mode:'rank',player0Id:p.id,player1Id:foe.id,namesJson:'["a","b"]',stateJson:'{}'}});
    await db.playerItem.create({data:{playerId,itemId:'potion',count:2}});
  });
  afterAll(async()=>db.$disconnect());
  it('holds once, consumes once, and never refunds an executed item',async()=>{
    await hold();await hold();expect((await stock()).count).toBe(1);
    await tx(t=>consumePhaseItems(t,roomId,phaseId,expected(),[0]));
    await tx(t=>consumePhaseItems(t,roomId,phaseId,expected(),[0]));
    await tx(t=>refundRoomItems(t,roomId));expect((await stock()).count).toBe(1);
    expect(await db.auditEvent.count({where:{playerId,kind:'ITEM_USE'}})).toBe(1);
  });
  it('terminal-before-effects refunds once, including after service restart',async()=>{
    await hold();
    await tx(t=>refundRoomItems(t,roomId));await tx(t=>refundRoomItems(t,roomId));
    expect((await stock()).count).toBe(2);
    expect(await db.auditEvent.count({where:{playerId,kind:'PVP_ITEM_REFUND'}})).toBe(1);
  });
  it('an accepted but skipped item is refunded when its phase resolves',async()=>{
    await hold();await tx(t=>consumePhaseItems(t,roomId,phaseId,expected(),[]));
    expect((await stock()).count).toBe(2);
    expect(await db.auditEvent.count({where:{playerId,kind:'ITEM_USE'}})).toBe(0);
  });
  it('missing escrow cannot authorize free effects and failed acceptance rolls back debit',async()=>{
    await expect(tx(t=>consumePhaseItems(t,roomId,phaseId,expected(),[0]))).rejects.toThrow('escrow mismatch');
    await expect(tx(async t=>{await holdItem(t,{roomId,phaseId,...expected()[0]});throw new Error('reject command');})).rejects.toThrow('reject command');
    expect((await stock()).count).toBe(2);
    expect(await db.realtimeItemEscrow.count({where:{roomId}})).toBe(0);
  });
  it('existing noncombat consumption cannot spend an escrowed last item',async()=>{
    await db.playerItem.update({where:{playerId_itemId:{playerId,itemId:'potion'}},data:{count:1}});
    await hold();await expect(game.webUseItem(playerId,{id:'potion',name:'치료약'})).rejects.toThrow();
    expect(await tx(t=>inventoryForPlayer(t,playerId))).toEqual([]);
    await tx(t=>refundRoomItems(t,roomId));expect((await stock()).count).toBe(1);
  });
  it('shop cap counts refundable escrow so refund cannot exceed maxStack',async()=>{
    await db.playerItem.update({where:{playerId_itemId:{playerId,itemId:'potion'}},data:{count:20}});
    await hold();
    await expect(game.webBuyItem(playerId,{id:'potion',name:'치료약',price:60},1,20)).rejects.toThrow();
    await tx(t=>refundRoomItems(t,roomId));expect((await stock()).count).toBe(20);
  });
});
