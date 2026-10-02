import type {Character,Content} from './content.js';
import {rarityNames,tierOrder} from './content.js';
import {characterInfo,mask} from './presentation.js';
export interface Receipt {
  first:boolean;count:number;snacks:number;snackBalance:number;tierKnown:number;tierTotal:number;
}
export type AcquisitionKind='GACHA'|'COMBINATION'|'EXCHANGE'|'EXPEDITION';
const emblems:Record<string,string>={COMMON:'🥚',UNCOMMON:'🔹',SPECIAL:'✦',RARE:'◆',LEGEND:'★',HIDDEN:'❔',LIMITED:'⛓',TRANSCEND:'✴',ETERNAL:'♾',IMMORTAL:'🗿'};
export function introduction(content:Content,c:Character,kind:AcquisitionKind,known:Set<string>):string{
  const grade=rarityNames[c.rarity];
  const headline=kind==='COMBINATION'?`${emblems[c.rarity]} ${grade} 조합 완성!`:
    kind==='EXCHANGE'?`${emblems[c.rarity]} 교환 완료!`:kind==='EXPEDITION'?`${emblems[c.rarity]} 탐험에서 합류!`:`${emblems[c.rarity]} ${grade} 등장!`;
  const scene=kind==='COMBINATION'?`🧩 ${c.reason}\n\n`:'';
  const stage=kind==='COMBINATION'&&c.stage?`${c.stage}\n\n`:'';
  return mask(content,`${headline}\n\n${stage}【${c.name}】\n${characterInfo(content,c)}\n\n${scene}${c.introduction}\n\n「${c.quote}」`,known);
}
export function receiptText(c:Character,receipt:Receipt):string{
  const found=receipt.first?'📖 첫 발견 · 도감에 새로 기록했습니다.':`📚 다시 합류! · 누적 ${receipt.count}회 획득`;
  return `${found}\n${rarityNames[c.rarity]} 도감 ${receipt.tierKnown}/${receipt.tierTotal}`+
    (receipt.snacks?`\n🎁 ${receipt.first?'첫 발견':'중복 획득'} 보상 · 새우깡 +${receipt.snacks}\n새우깡 현재 ${receipt.snackBalance}개`:'');
}
export function rewardGuide(content:Content):string{
  const r=content.economy.rewards;
  return '🎁 획득 보상 (새우깡)\n\n'+tierOrder.filter(t=>t!=='COMMON').map(t=>`${rarityNames[t]} · 첫 도감 ${r[t].first} / 중복 ${r[t].duplicate}`).join('\n')+
    `\n\n뽑기·조합·탐험으로 유닛을 얻으면 함께 지급합니다.\n흔함은 보상이 없습니다.\n새우깡 ${content.economy.exchange.snackCost}개로 원하는 흔함 1마리를 교환합니다.\n교환은 최상위 유닛(제한·초월·영원·불멸)을 보유해야 할 수 있습니다.`;
}
