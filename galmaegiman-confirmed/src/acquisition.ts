import type {Character,Content} from './content.js';
import {rarityNames} from './content.js';
import {characterInfo} from './presentation.js';
export interface Receipt {
  first:boolean;count:number;snacks:number;snackBalance:number;tierKnown:number;tierTotal:number;
}
export type AcquisitionKind='GACHA'|'COMBINATION'|'CONFIRMED';
const emblems:Record<string,string>={COMMON:'🥚',UNCOMMON:'🔹',SPECIAL:'✦',RARE:'◆'};
export function introduction(content:Content,c:Character,kind:AcquisitionKind):string{
  const f=content.flavors.get(c.id)!;
  const headline=kind==='COMBINATION'?`${emblems[c.rarity]} ${rarityNames[c.rarity]} 조합 완성!`:
    kind==='CONFIRMED'?`${emblems[c.rarity]} 재료 영입 완료!`:`${emblems[c.rarity]} ${rarityNames[c.rarity]} 등장!`;
  const scene=kind==='COMBINATION'?f.fusion:f.arrival;
  // The longer lore belongs in the character detail; draw text stays compact.
  if(c.rarity==='COMMON')return `${headline}\n${c.name}\n${characterInfo(c)}\n\n${scene}\n“${c.quote}”`;
  return `${headline}\n${scene}\n\n【${c.name}】\n${f.title}\n${characterInfo(c)}\n\n${c.rarity==='RARE'?`${f.lore}\n\n`:''}“${c.quote}”`;
}
export function receiptText(c:Character,receipt:Receipt):string{
  const found=receipt.first?'📖 첫 발견 · 도감에 새로 기록했습니다.':`📚 다시 합류! · 누적 ${receipt.count}회 획득`;
  return `${found}\n${rarityNames[c.rarity]} 도감 ${receipt.tierKnown}/${receipt.tierTotal}`+
    (receipt.snacks?`\n🎁 ${receipt.first?'첫 발견':'중복 획득'} 보상 · 새우깡 +${receipt.snacks}\n새우깡 현재 ${receipt.snackBalance}개`:'');
}
export function rewardGuide(content:Content):string{
  return '🎁 캐릭터 획득 보상\n\n'+Object.entries(content.rewards).map(([rarity,r])=>
    `${rarityNames[rarity]} · 첫 도감 ${r.first}개 / 중복 ${r.duplicate}개`).join('\n')+
    '\n\n무료 게임 내 새우깡 보상입니다.\n뽑기·조합으로 캐릭터를 얻으면 함께 지급합니다.\n소비한 캐릭터를 다시 얻어도 도감 이력이 있으면 중복 보상입니다.\n조합 버튼을 다시 눌러도 보상은 추가되지 않습니다.\n새우깡 30개로 흔함 재료 1마리를 선택해 교환합니다.';
}
