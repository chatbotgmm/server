import { randomInt } from 'node:crypto';
import type { Character, Economy, Tier } from './content.js';

export const gachaKinds=['LOW','MID','HIGH'] as const;
export type GachaKind=typeof gachaKinds[number];
export type RandomIndex=(exclusiveMax:number)=>number;

export class GachaEngine {
  private readonly pools:Map<Tier,Character[]>;
  constructor(characters:Character[],readonly config:Economy['gachas'],private readonly random:RandomIndex=randomInt){
    this.pools=new Map();
    for(const kind of gachaKinds){
      const weights=Object.entries(config[kind].weights);
      if(weights.reduce((sum,[,w])=>sum+w,0)!==100)throw new Error(`${kind} 확률 합계는 100이어야 합니다.`);
      for(const [tier] of weights){
        const pool=characters.filter(c=>c.rarity===tier);
        if(!pool.length)throw new Error(`뽑기 유닛이 없는 등급: ${tier}`);
        this.pools.set(tier as Tier,pool);
      }
    }
  }
  private index(max:number):number{
    const value=this.random(max);
    if(!Number.isInteger(value)||value<0||value>=max)throw new Error('난수 범위 오류');
    return value;
  }
  // 천장: 지정 등급에서 바로 뽑습니다(등급 안에서는 동일 확률).
  drawTier(tier:Tier):Character{
    const pool=this.pools.get(tier);
    if(!pool)throw new Error(`뽑기 유닛이 없는 등급: ${tier}`);
    return pool[this.index(pool.length)];
  }
  draw(kind:GachaKind):Character{
    // 1. 등급 추첨(합계 100). 2. 같은 등급 안에서는 유닛마다 동일 확률.
    let ticket=this.index(100);
    for(const [tier,weight] of Object.entries(this.config[kind].weights)){
      if(ticket<weight){
        const pool=this.pools.get(tier as Tier)!;
        return pool[this.index(pool.length)];
      }
      ticket-=weight;
    }
    throw new Error('뽑기 확률 설정 오류');
  }
}
