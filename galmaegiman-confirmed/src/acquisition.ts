import type {Character,Content,Tier} from './content.js';
import {rarityNames,tierOrder} from './content.js';
import {mask} from './presentation.js';
export interface Receipt {
  first:boolean;count:number;snacks:number;snackBalance:number;tierKnown:number;tierTotal:number;
  achievement?:{tier:Tier;title:string;tickets:number};
}
export type AcquisitionKind='GACHA'|'COMBINATION'|'EXCHANGE'|'EXPEDITION';
export const emblems:Record<string,string>={COMMON:'🥚',UNCOMMON:'🔹',SPECIAL:'✦',RARE:'◆',LEGEND:'★',HIDDEN:'❔',LIMITED:'⛓',TRANSCEND:'✴',ETERNAL:'♾',IMMORTAL:'🗿'};
const high=new Set(['LEGEND','HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL']);

export const speech=(c:Character)=>`💬 ${c.name}: "${c.quote}"`;
// 높은 등급은 갈매미 카운트로 뜸을 들입니다. 불멸 등 stage가 있으면 그것을 씁니다.
export function prelude(c:Character,first:boolean){
  if(c.stage)return `${c.stage}\n\n`;
  if(high.has(c.rarity))return '……하늘이 어두워진다.\n갈매미 한 마리… 두 마리… 세 마리… 네 마리…\n(정적)\n다섯 마리.\n\n';
  if(c.rarity==='RARE'&&first)return '……공기가 무거워진다.\n\n';
  return '';
}
export function acquisitionText(content:Content,c:Character,kind:AcquisitionKind,receipt:Receipt,known:Set<string>):string{
  const e=emblems[c.rarity],grade=rarityNames[c.rarity];
  const snack=receipt.snacks?` · 🍤 +${receipt.snacks}`:'';
  // 획득 결과(뽑기·조합·교환·탐험)에는 한 줄 소개와 대사를 넣지 않습니다. 도감에서 봅니다.
  let text:string;
  if(receipt.first){
    const head=kind==='COMBINATION'?`🧩 조합 성공! 새 ${grade} 발견!`:kind==='EXPEDITION'?`🧭 탐험에서 새 ${grade} 발견!`:`${e}${e} 새 ${grade} 발견! ${e}${e}`;
    text=`${prelude(c,true)}${head}\n【${c.name}】\n📖 ${grade} 도감 ${receipt.tierKnown}/${receipt.tierTotal}${snack}`;
  }else{
    const head=kind==='COMBINATION'?`🧩 ${c.name} 조합 완료`:`${e} ${c.name}`;
    text=`${high.has(c.rarity)?prelude(c,false):''}${head} (${receipt.count}번째)${receipt.snacks?`\n🍤 +${receipt.snacks}`:''}`;
  }
  if(receipt.achievement)text+=`\n\n${achievementText(receipt.achievement)}`;
  return mask(content,text,known);
}
export const achievementText=(a:{tier:Tier;title:string;tickets:number})=>`🏆 ${rarityNames[a.tier]} 도감 완성!\n칭호 「${a.title}」 획득 · 🎟 +${a.tickets}`;
export function rewardGuide(content:Content):string{
  const r=content.economy.rewards;
  return '🎁 획득 보상 (새우깡)\n\n'+tierOrder.filter(t=>t!=='COMMON').map(t=>`${rarityNames[t]} · 첫 발견 ${r[t].first} / 중복 ${r[t].duplicate}`).join('\n')+
    `\n\n흔함은 보상이 없습니다.\n새우깡 ${content.economy.exchange.snackCost}개로 원하는 흔함 1마리를 교환합니다.\n교환은 최상위 유닛(제한·초월·영원·불멸)을 보유해야 할 수 있습니다.\n\n등급 도감을 모두 채우면 칭호와 뽑기권을 받습니다. (칭호)`;
}
