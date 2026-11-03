import type { PrismaClient } from '@prisma/client';
// 운영 DB에 단체방 표가 없으면 시작하지 않아요(계정 삭제가 이 표들도 지우기 때문). 반영: prisma/manual/20261103_group_bot_additive.sql
export async function assertGroupSchema(db:PrismaClient){
  try{await Promise.all([db.groupUserKey.count(),db.groupRoom.count(),db.roomMember.count(),db.roomDex.count(),db.roomWeek.count(),db.proactiveLog.count()]);}
  catch{throw new Error('단체방 챗봇 DB 표가 아직 없습니다. 서버를 시작하지 않습니다. 백업 후 prisma/manual/20261103_group_bot_additive.sql을 검토·적용하고 Prisma client를 다시 만드세요.');}
}
