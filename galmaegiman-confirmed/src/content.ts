import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { parse } from 'yaml';
import { z } from 'zod';
const id=z.string().regex(/^[CUSR]\d{3}[MF]$/);
const catalogSchema=z.object({edition:z.literal('confirmed-75-v1'),characters:z.array(z.tuple([id,z.string().min(1).nullable(),z.string().min(1),z.string().min(1)])).length(75)});
const recipeSchema=z.object({status:z.literal('TEST_DRAFT'),recipes:z.array(z.tuple([id,z.array(id).min(2).max(10),z.string().min(1)])).length(69)});
const displayNamesSchema=z.object({source:z.literal('CONCEPT_DISPLAY'),names:z.record(id,z.string().trim().min(1).max(50))}).strict();
const caption=z.string().trim().min(1);
const flavorSchema=z.object({version:z.literal('FLAVOR_V1'),characters:z.array(z.tuple([id,caption.max(40),caption.max(100),caption.max(220),caption.max(200)])).length(75)}).strict();
const reward=z.object({first:z.number().int().min(0).max(10000),duplicate:z.number().int().min(0).max(10000)}).strict();
const rewardsSchema=z.object({version:z.literal('ACQUISITION_REWARDS_V1'),rewards:z.object({COMMON:reward,UNCOMMON:reward,SPECIAL:reward,RARE:reward}).strict()}).strict();
export const rarityNames:Record<string,string>={COMMON:'흔함',UNCOMMON:'안흔함',SPECIAL:'특별',RARE:'희귀'};
export const tiers:Record<string,number>={COMMON:1,UNCOMMON:2,SPECIAL:3,RARE:4};
export interface Character {id:string;name:string;rarity:string;sex:string;position:number;quote:string;imageUrl:string;nameStatus:string;artVersion:string;concept:string}
export interface Recipe {resultId:string;materials:string[];story:string;requiresCollection?:string[];peanutState?:boolean}
export interface Flavor {title:string;arrival:string;lore:string;fusion:string}
export function loadContent(root=process.cwd()) {
  const raw=readFileSync(`${root}/data/catalog.yaml`,'utf8');
  const rawRecipes=readFileSync(`${root}/data/recipes.yaml`,'utf8');
  const catalog=catalogSchema.parse(parse(raw));
  const input=recipeSchema.parse(parse(rawRecipes));
  const display=displayNamesSchema.parse(parse(readFileSync(`${root}/data/display-names.yaml`,'utf8')));
  const missingNames=new Set(catalog.characters.filter(c=>c[1]===null).map(c=>c[0]));
  for(const key of Object.keys(display.names))if(!missingNames.has(key))throw new Error(`기존 이름 또는 알 수 없는 ID는 덮어쓸 수 없습니다: ${key}`);
  for(const key of missingNames)if(!display.names[key])throw new Error(`표시명이 누락되었습니다: ${key}`);
  const characters:Character[]=catalog.characters.map(([id,name,quote,concept],position)=>({
    id,name:name??display.names[id],quote,concept,position,
    rarity:({C:'COMMON',U:'UNCOMMON',S:'SPECIAL',R:'RARE'} as Record<string,string>)[id[0]],
    sex:id.endsWith('M')?'MALE':'FEMALE',imageUrl:`/images/${id}.png`,
    nameStatus:name?'REFERENCE':'CONCEPT_DISPLAY',
    artVersion:id[0]==='C'||id[0]==='U'||/^S00[1-5][MF]$/.test(id)?'v3':'v2'
  }));
  const map=new Map(characters.map(c=>[c.id,c]));
  if(map.size!==75)throw new Error('중복 캐릭터 ID');
  for(const [rarity,count] of Object.entries({COMMON:6,UNCOMMON:9,SPECIAL:20,RARE:40}))
    if(characters.filter(c=>c.rarity===rarity).length!==count)throw new Error(`등급 수량 오류 ${rarity}`);
  const recipes:Recipe[]=input.recipes.map(([resultId,materials,story])=>({resultId,materials,story}));
  if(new Set(recipes.map(r=>r.resultId)).size!==69)throw new Error('중복 조합 결과');
  for(const r of recipes){
    const result=map.get(r.resultId);
    if(!result||result.rarity==='COMMON')throw new Error('잘못된 조합 결과');
    for(const material of r.materials){
      const c=map.get(material);
      if(!c||tiers[c.rarity]>=tiers[result.rarity])throw new Error('순환/상위 재료 오류');
    }
  }
  if(characters.some(c=>c.rarity!=='COMMON'&&!recipes.some(r=>r.resultId===c.id)))throw new Error('조합식 누락');
  const flavorInput=flavorSchema.parse(parse(readFileSync(`${root}/data/flavor.yaml`,'utf8')));
  const flavors=new Map<string,Flavor>(flavorInput.characters.map(([id,title,arrival,lore,fusion])=>[id,{title,arrival,lore,fusion}]));
  if(flavors.size!==75||[...flavors.keys()].some(id=>!map.has(id)))throw new Error('소개 중복 또는 알 수 없는 캐릭터');
  if(characters.some(c=>!flavors.has(c.id)||(c.rarity!=='COMMON'&&flavors.get(c.id)!.fusion==='-')))throw new Error('캐릭터 소개 또는 조합 소개 누락');
  const rewards=rewardsSchema.parse(parse(readFileSync(`${root}/data/acquisition-rewards.yaml`,'utf8'))).rewards;
  if(rewards.COMMON.first!==0||rewards.COMMON.duplicate!==0)throw new Error('흔함 재료 교환에는 획득 보너스를 지급하지 않습니다.');
  for(const [i,value] of Object.values(rewards).entries()){
    if(value.first<value.duplicate)throw new Error('첫 도감 보상은 중복 보상 이상이어야 합니다.');
    const previous=Object.values(rewards)[i-1];
    if(previous&&(value.first<previous.first||value.duplicate<previous.duplicate))throw new Error('상위 등급 보상이 하위보다 작습니다.');
  }
  return {edition:catalog.edition,characters,map,recipes,flavors,rewards,hash:createHash('sha256').update(raw).update(rawRecipes).digest('hex')};
}
export type Content=ReturnType<typeof loadContent>;
