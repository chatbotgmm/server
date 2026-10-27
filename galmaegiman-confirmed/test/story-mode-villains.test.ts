import {describe,expect,it} from 'vitest';
import {readFileSync} from 'node:fs';
import {storyVillainFighter,storyVillainProfiles} from '../src/story/villains.js';
import {fighter} from '../src/roster60-battle.js';
import {skillElements} from '../src/battle/roster60-rules.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';

const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8')) as RosterCatalog;
describe('스토리 전용 빌런은 기존 60종의 공격형 전투 계약만 사용한다',()=>{
 it('첫깃 기본형 출전을 거절하고 운영 밸런스 승인을 주장하지 않는다',()=>{
  expect(storyVillainProfiles.productionBalanceApproved).toBe(false);
  expect(storyVillainProfiles.numericValuesAreTestValues).toBe(true);
  expect(storyVillainProfiles.profiles.find(p=>p.id==='first')?.combatAllowed).toBe(false);
  expect(()=>storyVillainFighter(catalog,'first',1,'enemy')).toThrow('STORY_VILLAIN_PROFILE_PENDING');
  expect(()=>storyVillainFighter(catalog,'not-approved-character',1,'enemy')).toThrow('STORY_VILLAIN_PROFILE_PENDING');
 });

 it('5개 빌런의 레벨 1·20·60 전투 개체 생성이 검증기를 통과하며 로스터 원본을 바꾸지 않는다',()=>{
  const before=JSON.stringify(catalog);const profiles=storyVillainProfiles.profiles.filter(p=>p.combatAllowed);expect(profiles).toHaveLength(5);
  for(const profile of profiles)for(const level of [1,20,60]){
   const enemy=storyVillainFighter(catalog,profile.id,level,'enemy'),baseline=fighter(catalog,profile.baselineSpeciesId,level,'enemy');
   expect(enemy.speciesId).toBe('story_'+profile.id);expect(enemy.level).toBe(level);
   expect(Object.keys(enemy.stats).sort()).toEqual(['durability','hp','mp','speed','statusResistance']);
   expect(enemy.stats).toEqual(baseline.stats);expect(enemy.suitability).toEqual(baseline.suitability);expect(enemy.defense).toEqual(baseline.defense);
   expect(enemy).not.toHaveProperty('element');expect(enemy).not.toHaveProperty('attr');expect(enemy.stats).not.toHaveProperty('attack');
   expect(enemy.skills.length).toBeLessThanOrEqual(3);
   for(const skill of [enemy.attack,...enemy.skills]){
    expect(skillElements).toContain(skill.element);expect(skill).not.toHaveProperty('effects');expect(skill).not.toHaveProperty('priority');
    expect(skill.power).toBeGreaterThan(0);expect(skill.mpCost).toBeGreaterThanOrEqual(0);
   }
   expect(enemy.attack.mpCost).toBe(0);
   const locked=profile.skills.filter(s=>s.learnLevel>level).map(s=>s.id);expect(enemy.skills.some(s=>locked.includes(s.id))).toBe(false);
  }
  expect(JSON.stringify(catalog)).toBe(before);expect(catalog.characters).toHaveLength(60);
 });
});
