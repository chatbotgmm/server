import { randomInt } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import { z } from 'zod';
import type { Character } from './content.js';

export const drawRarities = ['COMMON', 'UNCOMMON', 'SPECIAL', 'RARE'] as const;
export type DrawRarity = typeof drawRarities[number];
const weight = z.number().int().min(1).max(100);
const weightsSchema = z.object({COMMON: weight, UNCOMMON: weight, SPECIAL: weight, RARE: weight}).strict()
  .refine(v => drawRarities.reduce((sum, rarity) => sum + v[rarity], 0) === 100, '등급 확률의 합계는 100이어야 합니다.');
const configSchema = z.object({mode: z.literal('RANDOM'), rarityWeights: weightsSchema}).strict();
export type GachaWeights = z.infer<typeof weightsSchema>;
export type RandomIndex = (exclusiveMax: number) => number;

export function loadGachaWeights(root = process.cwd()): GachaWeights {
  return configSchema.parse(parse(readFileSync(`${root}/data/gacha.yaml`, 'utf8'))).rarityWeights;
}

export class GachaEngine {
  readonly weights: Readonly<GachaWeights>;
  private readonly pools: Record<DrawRarity, Character[]>;
  constructor(characters: Character[], weights: GachaWeights, private readonly random: RandomIndex = randomInt) {
    this.weights = Object.freeze(weightsSchema.parse(weights));
    this.pools = Object.fromEntries(drawRarities.map(rarity => [rarity, characters.filter(c => c.rarity === rarity)])) as Record<DrawRarity, Character[]>;
    for (const rarity of drawRarities) if (this.pools[rarity].length === 0) throw new Error(`뽑기 캐릭터가 없는 등급: ${rarity}`);
  }
  private index(max: number): number {
    const value = this.random(max);
    if (!Number.isInteger(value) || value < 0 || value >= max) throw new Error('난수 범위 오류');
    return value;
  }
  draw(): Character {
    // 1. 등급 추첨. 캐릭터 수/보유 여부가 등급 확률을 바꾸지 않습니다.
    let ticket = this.index(100);
    for (const rarity of drawRarities) {
      if (ticket < this.weights[rarity]) {
        // 2. 선택된 등급 안에서 모든 캐릭터를 동일 확률로 추첨합니다.
        const pool = this.pools[rarity];
        return pool[this.index(pool.length)];
      }
      ticket -= this.weights[rarity];
    }
    throw new Error('뽑기 확률 설정 오류');
  }
}
