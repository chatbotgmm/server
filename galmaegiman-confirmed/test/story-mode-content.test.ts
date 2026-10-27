import {describe,it,expect} from 'vitest';
import {readFileSync} from 'node:fs';
import type {StoryContent,StoryQuest} from '../src/story/types.js';

const content=JSON.parse(readFileSync('data/story/story-mode-v1.json','utf8')) as StoryContent;
const quest=(id:string)=>content.quests.find(q=>q.id===id)!;
const text=(q:StoryQuest)=>content.scenes.filter(s=>[q.acceptScene,q.returnScene].includes(s.id)).flatMap(s=>s.lines.map(l=>l.text)).join('\n');

describe('시험용 스토리 콘텐츠의 서사·기존 전투 경계',()=>{
 it('7장·21개 선형 본편과 주민 네 명의 유한한 의뢰를 갖는다',()=>{
  const main=content.quests.filter(q=>q.kind==='main');
  expect(content.chapters).toHaveLength(7);expect(main).toHaveLength(21);
  expect(main.map(q=>q.id)).toEqual(Array.from({length:21},(_,i)=>`STORY_${String(i+1).padStart(2,'0')}`));
  expect(new Set(content.quests.map(q=>q.id)).size).toBe(25);
  for(let i=1;i<main.length;i++)expect(main[i].unlockAfter).toContain(main[i-1].id);
  const side=content.quests.filter(q=>q.kind==='resident');
  expect(side).toHaveLength(4);expect(content.residents).toHaveLength(4);
  expect(new Set(side.map(q=>q.npcId))).toEqual(new Set(content.residents.map(n=>n.id)));
  expect(side.map(q=>q.unlockAfter)).toEqual([['STORY_04'],['STORY_07'],['STORY_15'],['STORY_17']]);
  expect(content.residents.every(n=>n.dailyDialogue.length>0)).toBe(true);
 });

 it('짧은 대화와 확정 목표가 연결되며 랜덤·일일접속·PvP승수 관문을 추가하지 않는다',()=>{
  const scenes=new Map(content.scenes.map(s=>[s.id,s]));
  expect(scenes.size).toBe(content.scenes.length);
  const encounters=new Map(content.encounters.map(e=>[e.id,e]));
  for(const q of content.quests){
   expect(scenes.has(q.acceptScene),q.id).toBe(true);expect(scenes.has(q.returnScene),q.id).toBe(true);
   for(const id of [q.acceptScene,q.returnScene]){
    const scene=scenes.get(id)!;expect(scene.lines.length,q.id).toBeGreaterThan(0);
    for(const line of scene.lines){expect(line.text.length,id).toBeGreaterThan(0);expect(line.text.length,id).toBeLessThan(350);}
   }
   expect(q.objective.wins,q.id).toBe(q.objective.encounterIds.length);
   for(const id of q.objective.encounterIds)expect(encounters.has(id),id).toBe(true);
   expect(['adventure_win','scene']).toContain(q.objective.kind);
   expect(q).not.toHaveProperty('requiredLevel');expect(q).not.toHaveProperty('pvpWins');expect(q).not.toHaveProperty('dailyLogin');
  }
  expect(content.policy.thresholdsAreTestValues).toBe(true);
  expect(content.productionBalanceApproved).toBe(false);
 });

 it('초반 크레갈스 동행과 5장 반전의 선공개를 막고 원전 핵심 대사를 보존한다',()=>{
  for(const q of content.quests.filter(q=>q.kind==='main'&&q.chapter<4)){
   const speakers=content.scenes.filter(s=>[q.acceptScene,q.returnScene].includes(s.id)).flatMap(s=>s.lines.map(l=>l.speakerId));
   expect(speakers,q.id).not.toContain('cregals');
  }
  for(const q of content.quests.filter(q=>q.chapter<5)){
   expect(text(q),q.id).not.toContain('애초에 죽은 적이 없어');
   expect(text(q),q.id).not.toContain('지금도 돌 안에서 변하고 있는 거야');
  }
  const reveal=text(quest('STORY_14'));
  expect(reveal).toContain('니들이 살릴 방법을 찾아다녔던 그 녀석 말이다.');
  expect(reveal).toContain('애초에 죽은 적이 없어.');
  expect(reveal).toContain('그 녀석은 지금도 돌 안에서 변하고 있는 거야.');
 });

 it('첫깃은 마지막 직접 전투에서만 등장하고 결말 뒤 새 보스를 붙이지 않는다',()=>{
  const firstCombat=content.encounters.filter(e=>e.villainProfileId?.includes('first'));
  expect(firstCombat).toHaveLength(1);
  expect(quest('STORY_20').objective.encounterIds).toContain(firstCombat[0].id);
  expect(firstCombat[0].villainProfileId).not.toMatch(/basic|base/);
  expect(quest('STORY_21').objective).toEqual({kind:'scene',encounterIds:[],wins:0});
  const early=content.quests.filter(q=>q.kind==='main'&&q.chapter<7);
  expect(early.some(q=>text(q).includes('첫깃'))||content.scenes.some(s=>s.id.startsWith('STORY_01')&&s.lines.some(l=>l.speakerId==='first'))).toBe(true);
 });

 it('주민의 흑화 상대 여덟 종은 기존 종이며 인간 적을 공통 드롭 대상으로 넣지 않는다',()=>{
  const side=content.quests.filter(q=>q.kind==='resident');
  const ids=side.flatMap(q=>q.objective.encounterIds);
  const enemies=ids.map(id=>content.encounters.find(e=>e.id===id)!);
  expect(new Set(enemies.map(e=>e.speciesId))).toEqual(new Set(['u01','u02','u06','u07','u14','u23','u28','u31']));
  for(const enemy of enemies){expect(enemy.kind).toBe('black');expect(enemy.mode).toBe('adventure');expect(enemy.villainProfileId).toBeUndefined();}
  for(const enemy of content.encounters.filter(e=>e.kind==='black'))expect(enemy.speciesId).toMatch(/^u\d{2}$/);
  for(const enemy of content.encounters.filter(e=>e.villainProfileId))expect(enemy.kind).toBe('villain');
 });

 it('조각은 별도 확정 항목이며 복원 전환은 2·3·4·5 순서다',()=>{
  const restores=content.quests.filter(q=>q.statueStageAfter!==undefined);
  // 5단계는 STORY_19 수락 시 적용하도록 별도 필드로 이동할 수 있다.
  expect(restores.filter(q=>q.statueStageAfter!==5).map(q=>[q.id,q.statueStageAfter])).toEqual([['STORY_06',2],['STORY_12',3],['STORY_18',4]]);
  const final=quest('STORY_19') as StoryQuest&{statueStageOnAccept?:number};
  expect(final.statueStageOnAccept??final.statueStageAfter).toBe(5);
  expect(quest('STORY_01').fragmentBundle?.count).toBeGreaterThan(0);
  for(const q of content.quests.filter(q=>q.fragmentBundle)){
   expect(q.fragmentBundle!.id).not.toMatch(/gang/i);expect(q.fragmentBundle!.count).toBeGreaterThan(0);
  }
 });
});
