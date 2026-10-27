import type {StoryContent} from './types.js';
import data from '../../data/story/story-mode-v1.json' with {type:'json'};
import villains from '../../data/story/villains-test-v1.json' with {type:'json'};

/** 2026-10-26 전달 설계의 교체 가능한 시험 콘텐츠. 수치/임시 인물 이름은 운영 승인이 아니다. */
export const storyContent:StoryContent=data as unknown as StoryContent;
const roleSpeakerNames:Record<string,string>={
  "narrator": "",
  "hero": "주인공",
  "companion": "동료",
  "resident": "마을 주민",
  "traveler": "행인",
  "pictureKeeper": "그림 소유자",
  "messenger": "전달자",
  "cregals": "크레갈스",
  "first": "",
  "darkFirst": "",
  "recordVillain": "",
  "engineerVillain": "",
  "escortVillain": "",
  "guideVillain": ""
};
const villainSpeakers:Record<string,string>={first:'first',darkFirst:'dark_first',recordVillain:'record',engineerVillain:'engineer',escortVillain:'escort',guideVillain:'guide'};
/** 주민/빌런의 임시 이름은 각 데이터의 한 곳을 바꾸면 대화 표시도 함께 바뀐다. */
export const storySpeakerNames:Record<string,string>={
 ...roleSpeakerNames,
 ...Object.fromEntries(Object.entries(villainSpeakers).map(([speakerId,profileId])=>[speakerId,villains.profiles.find(p=>p.id===profileId)!.name])),
 ...Object.fromEntries(storyContent.residents.map(resident=>[resident.id,resident.name]))
};
