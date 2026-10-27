/** 이야기 수치와 주민 이름은 시험용 콘텐츠다. 운영 밸런스 승인과 분리한다. */
export type StoryLine={speakerId:string;text:string;pose?:string};
export type StoryScene={id:string;background:string;lines:StoryLine[];camera?:string};
export type StoryQuest={id:string;kind:'main'|'resident';chapter:number;recommendedLevel:[number,number];title:string;unlockAfter:string[];npcId?:string;acceptScene:string;objective:{kind:'adventure_win'|'scene';encounterIds:string[];wins:number};returnScene:string;statueStageAfter?:number;fragmentBundle?:{id:string;count:number};statueStageOnAccept?:number;fragmentBundleOnAccept?:{id:string;count:number};darkGangLifetimeRequired?:number};
export type StoryEncounter={id:string;speciesId:string;level:number;kind:'black'|'villain'|'ordinary';mode:'adventure'|'story';winXp?:number;lossXp?:number;villainProfileId?:string;hpMultiplier?:number;powerMultiplier?:number};
export type StoryResident={id:string;name:string;role:string;dailyDialogue:string;proposal?:boolean};
export type StoryContent={version:string;productionBalanceApproved:false;chapters:{id?:string|number;number?:number;title:string;recommendedLevel?:[number,number]}[];residents:StoryResident[];scenes:StoryScene[];quests:StoryQuest[];encounters:StoryEncounter[];policy:{darkGangPerWin:number;maxBalance:number;mainLifetimeGates:Record<string,number>;thresholdsAreTestValues:true}};
export type AcceptedQuest={token:string;wins:number;adventureId?:string;battles:string[];commanderPolicy?:import('../progression/commander.js').CommanderPolicy|null;unitXpPolicy?:import('../progression/story-unit-xp.js').StoryUnitXpPolicy|null};
export type SceneProgress={questId:string;phase:'intro'|'return'|'replay';cursor:number};
export type StoryDeparture={requestId:string;questId?:string;encounterId:string;ownedIds:string[];requestedAdventureId?:string};
export type StoryProgress={version:1;completed:string[];accepted:Record<string,AcceptedQuest>;scene:SceneProgress|null;checkpoints:Record<string,number>;readScenes:string[];fragments:Record<string,number>;statueStage:number;hatched:boolean;departure?:StoryDeparture;materialAdventureId?:string};
export const initialStoryProgress=():StoryProgress=>({version:1,completed:[],accepted:{},scene:null,checkpoints:{},readScenes:[],fragments:{},statueStage:1,hatched:false});
