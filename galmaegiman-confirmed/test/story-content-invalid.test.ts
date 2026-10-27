import {describe,expect,it} from 'vitest';
import {storyContent} from '../src/story/content.js';
import {validateStoryContent} from '../src/story/service.js';
import {storyVillainProfiles,validateStoryVillainProfiles} from '../src/story/villains.js';

describe('스토리 데이터의 현재 엔진 경계',()=>{
 it('시험 콘텐츠와 공격 프로필이 실제 런타임 검증을 통과한다',()=>{
  expect(validateStoryContent(storyContent)).toBe(storyContent);
  expect(()=>validateStoryVillainProfiles(storyVillainProfiles)).not.toThrow();
 });

 it.each(['effects','priority','charge','heal'])('전용 적 기술의 미지원 %s 키를 빈 값이어도 거절한다',key=>{
  const data=structuredClone(storyVillainProfiles) as any;
  data.profiles[1].skills[0][key]=key==='effects'?[]:0;
  expect(()=>validateStoryVillainProfiles(data)).toThrow('STORY_VILLAIN_PROFILE_INVALID');
 });

 it.each([NaN,Infinity,-1,0])('유효한 피해 위력이 아닌 %s 값을 거절한다',power=>{
  const data=structuredClone(storyVillainProfiles);data.profiles[1].skills[0].power=power;
  expect(()=>validateStoryVillainProfiles(data)).toThrow('STORY_VILLAIN_PROFILE_INVALID');
 });

 it('개체 속성·ATK 추가와 운영승인 플래그 혼입을 거절한다',()=>{
  for(const extra of [{element:'DARK'},{stats:{attack:1}}]){
   const data=structuredClone(storyVillainProfiles);Object.assign(data.profiles[1],extra);
   expect(()=>validateStoryVillainProfiles(data)).toThrow('STORY_VILLAIN_PROFILE_INVALID');
  }
  const data=structuredClone(storyVillainProfiles);data.productionBalanceApproved=true;
  expect(()=>validateStoryVillainProfiles(data)).toThrow('STORY_VILLAIN_PROFILE_INVALID');
 });

 it('즉시 지급·변조된 조우 분류·과도한 복원 단계는 콘텐츠 오류로 거절한다',()=>{
  const data=structuredClone(storyContent);data.policy.darkGangPerWin=-1;
  expect(()=>validateStoryContent(data)).toThrow('STORY_CONTENT_INVALID');
  const human=structuredClone(storyContent);human.encounters.find(e=>e.villainProfileId==='record')!.kind='black';
  expect(()=>validateStoryContent(human)).toThrow('STORY_CONTENT_INVALID');
  const stage=structuredClone(storyContent);stage.quests.find(q=>q.id==='STORY_19')!.statueStageOnAccept=6;
  expect(()=>validateStoryContent(stage)).toThrow('STORY_CONTENT_INVALID');
 });
});
