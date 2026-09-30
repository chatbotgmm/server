import { PrismaClient } from '@prisma/client';
import { loadContent } from '../src/content.js';
const db=new PrismaClient();
const content=loadContent();
try{
  const marker=await db.appMeta.findUnique({where:{key:'edition'}});
  if(marker&&marker.value!==content.edition)throw new Error('다른 에디션 DB에 적용할 수 없습니다.');
  const oldHash=await db.appMeta.findUnique({where:{key:'contentHash'}});
  if(oldHash&&oldHash.value!==content.hash)throw new Error('기존 데이터와 카탈로그가 다릅니다. 임의 덮어쓰기를 중단했습니다. 별도 콘텐츠 마이그레이션이 필요합니다.');
  await db.$transaction(async tx=>{
    for(const c of content.characters){
      const {concept,...data}=c;
      await tx.characterDefinition.upsert({where:{id:c.id},create:data,update:{}});
    }
    for(const [key,value] of [['edition',content.edition],['contentHash',content.hash]])
      await tx.appMeta.upsert({where:{key},create:{key,value},update:{}});
  },{timeout:15000});
  console.log('75종 콘텐츠 준비 완료. 플레이어 상태는 변경하지 않았습니다.');
}finally{await db.$disconnect();}
