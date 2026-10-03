import { Prisma, PrismaClient } from '@prisma/client';
import { loadContent } from '../src/content.js';
const db=new PrismaClient();
const content=loadContent();
try{
  const marker=await db.appMeta.findUnique({where:{key:'edition'}});
  if(marker&&marker.value!==content.edition)throw new Error('다른 에디션 DB에 적용할 수 없습니다. 개편 데이터 초기화(scripts/reset-data.sh)가 필요합니다.');
  const oldHash=await db.appMeta.findUnique({where:{key:'contentHash'}});
  if(oldHash&&oldHash.value!==content.hash){
    // 조합식만 바뀐 경우(유닛 ID·등급이 DB와 모두 같음)는 안전하게 반영합니다.
    // 플레이어 데이터는 유닛 ID로만 연결되어 있어 조합식 변경의 영향을 받지 않습니다.
    const defs=await db.characterDefinition.findMany({select:{id:true,rarity:true}});
    // 기존 유닛은 ID·등급이 그대로여야 하고, 새 유닛은 추가만 허용합니다(삭제·등급 변경은 중단).
    const same=defs.length<=content.characters.length&&defs.every(d=>content.map.get(d.id)?.rarity===d.rarity);
    if(!same)throw new Error('기존 데이터와 유닛 구조(ID·등급)가 다릅니다. 임의 덮어쓰기를 중단했습니다. 별도 콘텐츠 마이그레이션이 필요합니다.');
    if(defs.length<content.characters.length)console.log(`새 유닛 ${content.characters.length-defs.length}종을 추가합니다.`);
    // 바뀐 조합식으로 만들던 확인 요청(10분 유효)은 지웁니다.
    const removed=await db.pendingAction.deleteMany({where:{result:{equals:Prisma.DbNull}}});
    await db.appMeta.update({where:{key:'contentHash'},data:{value:content.hash}});
    console.log(`콘텐츠 변경을 반영했습니다. (기존 유닛 ID·등급 동일, 진행 중이던 확인 요청 ${removed.count}건 취소)`);
  }
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
