import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { parse } from 'yaml';
import { z } from 'zod';

export const tierOrder=['COMMON','UNCOMMON','SPECIAL','RARE','LEGEND','HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'] as const;
export type Tier=typeof tierOrder[number];
export const rarityNames:Record<string,string>={COMMON:'흔함',UNCOMMON:'안흔함',SPECIAL:'특별',RARE:'희귀',LEGEND:'전설',HIDDEN:'히든',LIMITED:'제한',TRANSCEND:'초월',ETERNAL:'영원',IMMORTAL:'불멸'};
// 조합 재료는 결과보다 낮은 단계여야 합니다. 전설=히든, 최상위 4분류는 서로 동급입니다.
export const tiers:Record<string,number>={COMMON:1,UNCOMMON:2,SPECIAL:3,RARE:4,LEGEND:5,HIDDEN:5,LIMITED:6,TRANSCEND:6,ETERNAL:6,IMMORTAL:6};
export const topTiers=new Set<string>(['LIMITED','TRANSCEND','ETERNAL','IMMORTAL']);
export const synergyKeys=['BASIC','GOLD','DARK','SEA','BLOSSOM','COSMOS','OUTFIT'] as const;
export type SynergyKey=typeof synergyKeys[number];
const expected:Record<Tier,number>={COMMON:6,UNCOMMON:11,SPECIAL:15,RARE:20,LEGEND:20,HIDDEN:13,LIMITED:8,TRANSCEND:8,ETERNAL:8,IMMORTAL:9};
export const EDITION='galmaemi-118-v1';

const id=z.string().regex(/^[CUSRLHDTEI]\d{1,2}$/);
const text=z.string().trim().min(1);
const unitSchema=z.object({
  id,name:text.max(30),tier:z.enum(tierOrder),quote:text.max(60),tagline:text.max(60),introduction:text.max(400),reason:text.max(400),
  recipe:z.array(z.tuple([id,z.number().int().min(1).max(5)])).max(6),
  synergy:z.enum(synergyKeys).optional(),stage:text.max(300).optional()
}).strict();
const unitsSchema=z.object({edition:z.literal(EDITION),source:text,units:z.array(unitSchema).length(118)}).strict();

const percentPair=z.tuple([z.number().min(0).max(100),z.number().min(0).max(100)]);
const weights=z.partialRecord(z.enum(tierOrder),z.number().int().min(1).max(100));
const gacha=z.object({label:text,cost:z.number().int().min(1).max(100),weights}).strict();
const achievement=z.object({tickets:z.number().int().min(0).max(1000),title:z.string().trim().min(1).max(30)}).strict();
const reward=z.object({first:z.number().int().min(0).max(100000),duplicate:z.number().int().min(0).max(100000)}).strict();
const economySchema=z.object({
  version:z.literal('ECONOMY_V1'),
  tickets:z.object({welcome:z.number().int().min(0).max(1000),claim:z.number().int().min(1).max(1000),cooldownHours:z.number().positive().max(168)}).strict(),
  gachas:z.object({LOW:gacha,MID:gacha,HIGH:gacha}).strict(),
  exchange:z.object({snackCost:z.number().int().min(1).max(100000)}).strict(),
  expedition:z.object({
    minutes:z.number().int().min(1).max(1440),snackPerUnit:z.number().int().min(0).max(10000),maxParty:z.number().int().min(1).max(30),
    levels:z.tuple([z.number().int().min(1),z.number().int().min(1)]),
    effects:z.object({
      BASIC:z.object({label:text,concept:text,snackPercent:percentPair}).strict(),
      GOLD:z.object({label:text,concept:text,doublePercent:percentPair}).strict(),
      DARK:z.object({label:text,concept:text,minutes:z.tuple([z.number().int().min(1),z.number().int().min(1)])}).strict(),
      SEA:z.object({label:text,concept:text,commonPercent:percentPair}).strict(),
      BLOSSOM:z.object({label:text,concept:text,uncommonPercent:percentPair}).strict(),
      COSMOS:z.object({label:text,concept:text,ticketPercent:percentPair}).strict(),
      OUTFIT:z.object({label:text,concept:text,snackPerUnit:z.tuple([z.number().int().min(0),z.number().int().min(0)])}).strict()
    }).strict(),
    countBonusPercent:z.record(z.string().regex(/^\d+$/),z.number().min(0).max(100))
  }).strict(),
  rewards:z.object(Object.fromEntries(tierOrder.map(t=>[t,reward])) as Record<Tier,typeof reward>).strict(),
  achievements:z.object(Object.fromEntries(tierOrder.map(t=>[t,achievement])) as Record<Tier,typeof achievement>).strict(),
  missions:z.array(z.object({key:z.enum(['DRAW','CRAFT','EXPEDITION']),label:text,goal:z.number().int().min(1).max(1000),tickets:z.number().int().min(0).max(1000)}).strict()).max(10)
}).strict();
export type Economy=z.infer<typeof economySchema>;

export interface Character {id:string;name:string;rarity:Tier;position:number;quote:string;tagline:string;imageUrl:string;introduction:string;reason:string;synergy?:SynergyKey;stage?:string}
export interface Recipe {resultId:string;materials:string[];story:string;hidden:boolean}

export function loadEconomy(root=process.cwd()):Economy{
  const economy=economySchema.parse(parse(readFileSync(`${root}/data/economy.yaml`,'utf8')));
  for(const [key,g] of Object.entries(economy.gachas))
    if(Object.values(g.weights).reduce((a,b)=>a+b,0)!==100)throw new Error(`${key} 뽑기 확률 합계는 100이어야 합니다.`);
  const r=economy.rewards;
  if(r.COMMON.first!==0||r.COMMON.duplicate!==0)throw new Error('흔함 획득에는 보상을 지급하지 않습니다.');
  for(const t of tierOrder)if(r[t].first<r[t].duplicate)throw new Error('첫 도감 보상은 중복 보상 이상이어야 합니다.');
  for(const [low,high] of [['COMMON','UNCOMMON'],['UNCOMMON','SPECIAL'],['SPECIAL','RARE'],['RARE','LEGEND'],['RARE','HIDDEN'],['LEGEND','LIMITED'],['HIDDEN','LIMITED'],['LIMITED','TRANSCEND'],['TRANSCEND','ETERNAL'],['ETERNAL','IMMORTAL']] as const)
    if(r[high].first<r[low].first||r[high].duplicate<r[low].duplicate)throw new Error(`상위 등급 보상이 하위보다 작습니다: ${high}`);
  return economy;
}

export function loadContent(root=process.cwd()) {
  const raw=readFileSync(`${root}/data/units.json`,'utf8');
  const input=unitsSchema.parse(JSON.parse(raw));
  const characters:Character[]=input.units.map((u,position)=>({
    id:u.id,name:u.name,rarity:u.tier,position,quote:u.quote,tagline:u.tagline,imageUrl:`/images/${u.id}.png`,
    introduction:u.introduction,reason:u.reason,...(u.synergy?{synergy:u.synergy}:{}),...(u.stage?{stage:u.stage}:{})
  }));
  const map=new Map(characters.map(c=>[c.id,c]));
  if(map.size!==118)throw new Error('중복 유닛 ID');
  if(new Set(characters.map(c=>c.name)).size!==118)throw new Error('중복 유닛 이름');
  for(const t of tierOrder)if(characters.filter(c=>c.rarity===t).length!==expected[t])throw new Error(`등급 수량 오류 ${t}`);
  for(const c of characters)if(topTiers.has(c.rarity)!==Boolean(c.synergy))throw new Error(`시너지는 최상위 유닛에만 있습니다: ${c.id}`);
  const recipes:Recipe[]=[];
  const bundles=new Set<string>();
  for(const u of input.units){
    if(u.tier==='COMMON'){if(u.recipe.length)throw new Error(`흔함은 조합식이 없습니다: ${u.id}`);continue;}
    if(!u.recipe.length)throw new Error(`조합식 누락: ${u.id}`);
    const materials=u.recipe.flatMap(([m,q])=>Array.from({length:q},()=>m));
    if(materials.length<2)throw new Error(`재료는 2마리 이상: ${u.id}`);
    for(const m of materials){
      const c=map.get(m);
      if(!c||tiers[c.rarity]>=tiers[u.tier])throw new Error(`순환/상위 재료 오류: ${u.id} ← ${m}`);
    }
    const key=[...materials].sort().join('+');
    if(bundles.has(key))throw new Error(`같은 재료 묶음 중복: ${u.id}`);
    bundles.add(key);
    recipes.push({resultId:u.id,materials,story:u.reason,hidden:u.tier==='HIDDEN'});
  }
  const hiddenIds=characters.filter(c=>c.rarity==='HIDDEN').map(c=>c.id);
  // DB 잠금 해시는 구조(ID·등급·조합식)만 봅니다. 이름·소개·문구 수정은 seed가 반영합니다.
  const structure=JSON.stringify(input.units.map(u=>[u.id,u.tier,u.recipe]));
  return {edition:input.edition,characters,map,recipes,hiddenIds,economy:loadEconomy(root),
    hash:createHash('sha256').update(structure).digest('hex')};
}
export type Content=ReturnType<typeof loadContent>;
