import type {Character} from './content.js';
import {rarityNames} from './content.js';
import type {Choice,GameReply,ListEntry} from './types.js';

export const normalizeName=(value:string)=>value.normalize('NFKC').replace(/\s+/gu,'').toLowerCase();
export const shortName=(value:string)=>value.replace(/\s*갈매미맨$/u,'');
export const characterInfo=(c:Character)=>`${rarityNames[c.rarity]} · ${c.sex==='MALE'?'♂':'♀'}`;

export function matchCharacters(characters:Character[],query:string):Character[]{
  const key=normalizeName(query);
  if(!key)return [];
  const byId=characters.find(c=>c.id.toLowerCase()===key);
  if(byId)return [byId]; // 예전 입력과 저장 기록 호환용. 화면에는 내보내지 않습니다.
  const full=characters.filter(c=>normalizeName(c.name)===key);
  if(full.length)return full;
  const short=characters.filter(c=>normalizeName(shortName(c.name))===key);
  if(short.length)return short;
  return key.length>=2?characters.filter(c=>normalizeName(c.name).includes(key)):[];
}

export function listReply(title:string,items:ListEntry[],hint:string,choices:Choice[]=[]):GameReply{
  return {text:`${title}\n\n${items.map(item=>`${item.title}\n${item.description}`).join('\n\n')}\n\n${hint}`,list:{title,items},choices};
}

export function materialCounts(materials:string[]):Map<string,number>{
  const counts=new Map<string,number>();
  for(const id of materials)counts.set(id,(counts.get(id)??0)+1);
  return counts;
}
