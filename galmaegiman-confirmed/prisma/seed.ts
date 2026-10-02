import { PrismaClient } from '@prisma/client';
import { loadContent } from '../src/content.js';
const db=new PrismaClient();
const content=loadContent();
try{
  const marker=await db.appMeta.findUnique({where:{key:'edition'}});
  if(marker&&marker.value!==content.edition)throw new Error('다른 에디션 DB에 적용할 수 없습니다. 개편 데이터 초기화(scripts/reset-data.sh)가 필요합니다.');
  const oldHash=await db.appMeta.findUnique({where:{key:'contentHash'}});
  if(oldHash&&oldHash.value!==content.hash)throw new Error('기존 데이터와 유닛 구조(ID·등급·조합식)가 다릅니다. 임의 덮어쓰기를 중단했습니다. 별도 콘텐츠 마이그레이션이 필요합니다.');
  await db.$transaction(async tx=>{
    for(const c of content.characters){
      const data={name:c.name,rarity:c.rarity,position:c.position,quote:c.quote,imageUrl:c.imageUrl,synergy:c.synergy??null};
      // 구조는 해시로 고정되어 있으므로 이름·문구 같은 표시 정보만 갱신합니다.
      await tx.characterDefinition.upsert({where:{id:c.id},create:{id:c.id,...data},update:{name:data.name,quote:data.quote,imageUrl:data.imageUrl,synergy:data.synergy}});
    }
    for(const [key,value] of [['edition',content.edition],['contentHash',content.hash]])
      await tx.appMeta.upsert({where:{key},create:{key,value},update:{}});
  },{timeout:15000});
  console.log(`${content.characters.length}종 콘텐츠 준비 완료. 플레이어 상태는 변경하지 않았습니다.`);
}finally{await db.$disconnect();}
