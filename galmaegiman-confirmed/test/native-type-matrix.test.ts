import {describe,it,expect} from 'vitest';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {parse} from 'yaml';
import {rulesFrom,attrKeys} from '../src/battle/core.js';
import type {BattleData} from '../src/battle/rules.js';

const bytes=readFileSync('data/battle.yaml');
const data=parse(bytes.toString()) as BattleData;
const asset=JSON.parse(readFileSync('app/android/app/src/main/assets/battle-type-baseline.json','utf8'));
describe('native readable type matrix canonical contract',()=>{
  it('bundled baseline is generated from the exact canonical data without balance changes',()=>{
    expect(asset.chart).toEqual(data.chart);
    expect(asset.multipliers).toEqual(data.multipliers);
    expect(asset.sourceSha256).toBe(createHash('sha256').update(bytes).digest('hex'));
  });
  it('all 100 attack rows and defense columns equal the game engine including 0.67 resistance',()=>{
    // Recipe values are irrelevant to typeMult; preserve every chart/multiplier field.
    const R=rulesFrom({...data,recipePower:undefined});
    let strong=0,weak=0,neutral=0;
    for(const attack of attrKeys)for(const defense of attrKeys){
      const expected=asset.chart[attack].strong.includes(defense)?asset.multipliers.strong:
        asset.chart[defense].strong.includes(attack)?asset.multipliers.weak:1;
      expect(expected,`${attack} -> ${defense}`).toBe(R.typeMult(attack,defense));
      if(expected===data.multipliers.strong)strong++;else if(expected===data.multipliers.weak)weak++;else neutral++;
    }
    expect([strong,weak,neutral]).toEqual([20,18,62]);
    expect(R.typeMult('STAR','SHADOW')).toBe(data.multipliers.strong);
    expect(R.typeMult('SHADOW','STAR')).toBe(data.multipliers.strong);
  });
  it('native state projects canonical multipliers without modifying engine arithmetic',()=>{
    const source=readFileSync('src/native.ts','utf8');
    expect(source).toContain('typeChart:R.data.chart,typeMultipliers:R.data.multipliers,attributes:R.data.attributes');
    const java=readFileSync('app/android/app/src/main/java/org/dpdns/gmm/BattleTypeInfo.java','utf8');
    expect(java).toContain('multipliers.optDouble("strong",Double.NaN)');
    expect(java).toContain('multipliers.optDouble("weak",Double.NaN)');
    expect(java).toContain('앱 기본 데이터');
    expect(java).not.toContain('return 0.5');
  });
});
