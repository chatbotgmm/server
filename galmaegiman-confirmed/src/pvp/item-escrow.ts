// Manual draft target: src/pvp/item-escrow.ts. All writes require caller's room transaction.
import type {Prisma} from '@prisma/client';
import {GameError} from '../types.js';
type Tx=Prisma.TransactionClient;
export type ItemHold={side:0|1;playerId:string;itemId:string};
export async function inventoryForPlayer(tx:Tx,playerId:string):Promise<Array<{id:string;count:number}>>{
  return (await tx.playerItem.findMany({where:{playerId,count:{gt:0}},select:{itemId:true,count:true}}))
    .map(x=>({id:x.itemId,count:x.count}));
}
export async function holdItem(tx:Tx,input:ItemHold&{roomId:string;phaseId:string}){
  const {roomId,phaseId,side,playerId,itemId}=input;
  const room=await tx.realtimeRoom.findUniqueOrThrow({where:{id:roomId}});
  if(room.state!=='ACTIVE'||(side===0?room.player0Id:side===1?room.player1Id:null)!==playerId)
    throw new GameError('아이템을 사용할 대전이 아니에요.');
  const key={roomId,phaseId,side};
  const old=await tx.realtimeItemEscrow.findUnique({where:{roomId_phaseId_side:key}});
  if(old){
    if(old.playerId!==playerId||old.itemId!==itemId||old.state!=='HELD')throw new GameError('이미 처리된 아이템 선택이에요.');
    return old;
  }
  // Phase/target/item rules MUST have passed validateCommand before this call.
  const used=await tx.playerItem.updateMany({where:{playerId,itemId,count:{gte:1}},data:{count:{decrement:1}}});
  if(used.count!==1)throw new GameError('가지고 있는 아이템이 없어요.');
  const row=await tx.realtimeItemEscrow.create({data:{...key,playerId,itemId,state:'HELD'}});
  await tx.auditEvent.create({data:{playerId,kind:'PVP_ITEM_HOLD',detail:{roomId,phaseId,side,item:itemId}}});
  return row;
}
export async function consumePhaseItems(tx:Tx,roomId:string,phaseId:string,expected:readonly ItemHold[],consumedSides:readonly (0|1)[]){
  // Require exact authoritative command/hold correspondence before committing effects.
  // A missing hold must roll back the whole resolution, never create free healing.
  const rows=await tx.realtimeItemEscrow.findMany({where:{roomId,phaseId}});
  if(new Set(expected.map(x=>x.side)).size!==expected.length||new Set(consumedSides).size!==consumedSides.length||
    consumedSides.some(s=>!expected.some(x=>x.side===s))||rows.length!==expected.length||
    rows.some(r=>!expected.some(x=>x.side===r.side&&x.playerId===r.playerId&&x.itemId===r.itemId)))
    throw new Error('Realtime item command/escrow mismatch');
  for(const row of rows){
    if(!consumedSides.includes(row.side as 0|1)){
      if(row.state==='CONSUMED')throw new Error('Realtime consumed/refund mismatch');
      await refundRow(tx,row);continue;
    }
    if(row.state==='REFUNDED')throw new Error('Realtime refunded/consume mismatch');
    const changed=await tx.realtimeItemEscrow.updateMany({where:{roomId,phaseId,side:row.side,state:'HELD'},data:{state:'CONSUMED'}});
    if(changed.count)await tx.auditEvent.create({data:{playerId:row.playerId,kind:'ITEM_USE',detail:{roomId,phaseId,side:row.side,item:row.itemId}}});
  }
}
async function refundRow(tx:Tx,row:{roomId:string;phaseId:string;side:number;playerId:string;itemId:string}){
  const {roomId}=row;
  const changed=await tx.realtimeItemEscrow.updateMany({where:{roomId,phaseId:row.phaseId,side:row.side,state:'HELD'},data:{state:'REFUNDED'}});
  if(!changed.count)return;
  await tx.playerItem.upsert({where:{playerId_itemId:{playerId:row.playerId,itemId:row.itemId}},
    create:{playerId:row.playerId,itemId:row.itemId,count:1},update:{count:{increment:1}}});
  await tx.auditEvent.create({data:{playerId:row.playerId,kind:'PVP_ITEM_REFUND',detail:{roomId,phaseId:row.phaseId,side:row.side,item:row.itemId}}});
}
export async function refundRoomItems(tx:Tx,roomId:string){
  const rows=await tx.realtimeItemEscrow.findMany({where:{roomId,state:'HELD'}});
  for(const row of rows){
    await refundRow(tx,row);
  }
}
