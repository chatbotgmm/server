// 수비대 대결 규칙: data/battle.yaml의 모양을 검사(zod)하고 core.ts로 엔진이 쓰는 형태로 바꿈. 서버 전용으로 씀
// (브라우저는 서버가 검사한 데이터를 받아 core.ts의 rulesFrom만 씀 → 묶음 크기를 줄임)
import { z } from 'zod';
import { attrKeys, roleKeys, statusKeys, battleAugIds, rulesFrom } from './core.js';
import { loadContent, type Content } from '../content.js';
import { commonRecipeValues } from './recipe-value.js';
import { parseAugmentCatalogV2, type AugmentCatalogV2 } from './augment-catalog-v2.js';
import type { RulesOptions } from './core.js';
// Pure parser enforces stable IDs, exact numeric parameter keys and bounded values.
export const augmentCatalogV2Schema=z.unknown().transform((input,ctx):AugmentCatalogV2=>{
  try{return parseAugmentCatalogV2(input);}
  catch(error){ctx.addIssue({code:'custom',message:error instanceof Error?error.message:'증강 카탈로그 오류'});return z.NEVER;}
});
export * from './core.js';

// ── YAML 모양 검사 ──
const attr=z.enum(attrKeys),cat=z.enum(['물리','마법']),catAuto=z.enum(['물리','마법','자동']),role=z.enum(roleKeys),pct=z.number().min(-100).max(500),turns=z.number().int().min(1).max(20);
const effectIn=z.union([
  z.object({buff:z.enum(['atk','def','spd']),pct,turns,to:z.enum(['self','foe','team'])}).strict(),
  z.object({status:z.enum(statusKeys),chance:z.number().min(0).max(100),turns}).strict(),
  z.object({heal:z.number().min(1).max(100),to:z.enum(['self','bench'])}).strict(),
  z.object({guard:z.number().min(1).max(100),turns}).strict(),
  z.object({endure:turns}).strict(),
  z.object({regen:z.number().min(1).max(100),turns,to:z.enum(['self','team'])}).strict(),
  z.object({cleanse:z.number().int().min(0).max(10)}).strict(),
  z.object({revive:z.number().min(1).max(100)}).strict(),
  z.object({strip:z.literal(true)}).strict(),
  z.object({recharge:z.literal(true)}).strict(),
  z.object({lockSelf:z.number().min(0).max(200)}).strict(),
  z.object({protect:z.literal(true)}).strict(),
]);
const skillCondition=z.enum(['burned','slowed','bound','lowHp','finisher','opening','slower','weakened']);
const skillIn=z.object({name:z.string().min(1).max(30),cd:z.number().int().min(0).max(10),power:z.number().int().min(1).max(400).optional(),
  hits:z.number().int().min(2).max(5).optional(),priority:z.boolean().optional(),crit:z.boolean().optional(),attr:attr.optional(),cat:catAuto.optional(),
  pierce:z.number().min(0).max(70).optional(),drain:z.number().min(0).max(50).optional(),
  bonus:z.object({when:skillCondition,pct:z.number().int().min(1).max(100)}).strict().optional(),
  effects:z.array(effectIn).max(5).optional(),desc:z.string().max(120).optional()}).strict();
const passiveIn=z.object({pierce:z.number(),execute:z.number(),critPlus:z.number(),opener:z.number(),lifesteal:z.number(),reflect:z.number(),cut:z.number(),regen:z.number(),
  lastStand:z.boolean(),statusResist:z.number(),statusPlus:z.number(),durationPlus:z.number().int(),sigCd:z.number().int(),handoff:z.number(),hpPct:z.number(),atkPct:z.number(),defPct:z.number(),spdPct:z.number()}).partial();
// 유닛 능력치(레벨 1·원형 기준): 체력·물리공격·물리방어·마법공격·마법방어·속도
const statsIn=z.object({hp:z.number().positive(),patk:z.number().positive(),pdef:z.number().positive(),matk:z.number().positive(),mdef:z.number().positive(),spd:z.number().positive()}).strict();
const tierNums=z.object({LIMITED:z.number(),TRANSCEND:z.number(),ETERNAL:z.number(),IMMORTAL:z.number()}).strict();
export const battleSchema=z.object({
  version:z.literal('BATTLE_V1'),
  augmentVersion:z.union([z.literal(1),z.literal(2)]).optional(),
  augmentCatalog:augmentCatalogV2Schema.optional(),
  multipliers:z.object({strong:z.number().min(1),weak:z.number().min(0.1).max(1),sameAttr:z.number().min(1),crit:z.number().min(1),critChance:z.number().min(0).max(100),maxGuard:z.number().min(0).max(95)}).strict(),
  attributes:z.record(attr,z.object({name:z.string().min(1).max(10),basic:z.string().min(1).max(30),sub:z.string().min(1).max(30),basicCat:cat,subCat:cat}).strict()),
  basicPower:z.number().int().min(1).max(200),
  subSkill:z.object({power:z.number().int().min(1).max(200),cd:z.number().int().min(0).max(10)}).strict(),
  chart:z.record(attr,z.object({strong:z.tuple([attr,attr]),why:z.tuple([z.string(),z.string()])}).strict()),
  roles:z.record(role,z.object({name:z.string(),hp:z.number().positive(),atk:z.number().positive(),def:z.number().positive(),spd:z.number().positive(),skill:skillIn}).strict()),
  tiers:tierNums,
  recipePower:z.object({baseline:z.number().int().positive(),perCommonPct:z.number().min(0).max(5),capPct:z.number().min(0).max(50)}).strict().optional(),
  // 서버가 조합식에서 계산해 브라우저에도 함께 보내는 값. YAML에 수동으로 적지 않아요.
  recipeValues:z.record(z.string().regex(/^[DTEI]\d+$/),z.number().int().positive()).optional(),
  elemental:z.record(attr,z.object({basic:skillIn,sub:skillIn,attack:skillIn}).strict()).optional(),
  units:z.record(z.string().regex(/^[DTEI]\d+$/),z.object({attr,sub:attr.optional(),role,stats:statsIn,skill:skillIn}).strict()),
  synergies:z.record(z.string().regex(/^[A-Z]+$/),z.object({name:z.string().min(1).max(20),members:z.array(z.string().regex(/^[DTEI]\d+$/)).min(2),skill:skillIn}).strict()),
  learn:z.object({levels:z.array(z.number().int().min(2).max(200)).min(1).max(10),slots:z.number().int().min(1).max(4),offers:z.number().int().min(1).max(30),perChance:z.number().int().min(1).max(4).default(1),
    skills:z.record(z.string().regex(/^[a-z0-9]+$/),z.object({...skillIn.shape,level:z.number().int().min(1).max(200),roles:z.array(role).min(1).optional()}).strict())}).strict(),
  items:z.record(z.string().regex(/^[a-z0-9]+$/),z.object({name:z.string().min(1).max(20),price:z.number().int().min(1).max(100000),desc:z.string().max(120),
    heal:z.number().min(1).max(100).optional(),cleanse:z.literal(true).optional(),revive:z.number().min(1).max(100).optional()}).strict()),
  itemRules:z.object({perBattle:z.number().int().min(0).max(20),maxStack:z.number().int().min(1).max(999)}).strict(),
  storyHp:z.object({fullMinutes:z.number().positive().max(10080)}).strict().optional(),
  villains:z.record(z.string().regex(/^V\d+$/),z.object({name:z.string().min(1).max(20),basic:z.string().min(1).max(30).optional(),basicCat:cat.optional(),look:z.string().regex(/^[DTEI]\d+$/).optional(),tint:z.enum(['shadow','mirror','peanut']).optional(),mirror:z.boolean().optional(),attr,sub:attr.optional(),role,power:z.number().min(0.2).max(10),hp:z.number().min(0.2).max(20).optional(),boss:z.boolean().optional(),skill:skillIn}).strict()),
  status:z.object({burnDamage:z.number(),burnAtk:z.number(),bindSpd:z.number(),rageAtk:z.number(),rageDef:z.number()}).strict(),
  battleAugments:z.record(z.enum(battleAugIds),z.object({name:z.string().min(1).max(20),desc:z.string().max(120).optional()}).catchall(z.number())),
  growth:z.object({maxLevel:z.number().int().min(2).max(200),evolveLevel:z.tuple([z.number().int(),z.number().int(),z.number().int()]),stageMult:z.tuple([z.number(),z.number(),z.number(),z.number()]),
    levelGrowth:z.number().min(0),xpBase:z.number().positive(),xpExp:z.number().positive(),winXp:z.number().min(0),loseXp:z.number().min(0)}).strict(),
  evoAugments:z.record(z.string().regex(/^[a-z0-9]+$/),z.object({name:z.string().min(1).max(20),roles:z.union([z.literal('ANY'),z.array(role).min(1)]),desc:z.string().max(120).optional()}).merge(passiveIn).strict()),
  scout:z.object({freeOutingsPerDay:z.number().int().min(0),extraOutingPeanut:z.number().int().min(0),maxExtraPerDay:z.number().int().min(0),levelBand:z.number().int().min(0),
    encounter:tierNums,base:tierNums,stageFactor:z.tuple([z.number(),z.number(),z.number(),z.number()]),cardBonus:z.number().min(0),maxCards:z.number().int().min(0),
    cap:z.number().min(0).max(100),pity:tierNums}).strict(),
}).strict();
export type BattleData=z.infer<typeof battleSchema>;

export function buildRules(input:unknown,content?:Pick<Content,'characters'|'recipes'>,options:RulesOptions={}){
  const data=battleSchema.parse(input);
  // 조합식 변경이 즉시 반영되도록 저장 수치 대신 검증된 원재료 그래프에서 재계산해요.
  if(data.recipePower)data.recipeValues=commonRecipeValues(content??loadContent(),Object.keys(data.units));
  return rulesFrom(data,options);
}

