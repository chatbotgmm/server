// 수비대 대결(모바일 턴제) 전투 데이터: 10속성 상성, 최상위 33종의 속성·역할·고유기.
// 근거: design/STORY-CAMPAIGN.md(10속성·33종 배정, 사용자 승인), design/GAME-DIRECTION.md
// 서버와 앱이 같이 쓰므로 Node 전용 기능을 쓰지 않습니다.

export const attrs=['STONE','GOLD','FIRE','ICE','BLOSSOM','SEA','SHADOW','STAR','THREAD','GRAVITY'] as const;
export type Attr=typeof attrs[number];
export const attrName:Record<Attr,string>={STONE:'돌',GOLD:'황금',FIRE:'불',ICE:'얼음',BLOSSOM:'꽃',SEA:'바다',SHADOW:'그림자',STAR:'별',THREAD:'실',GRAVITY:'중력'};
// 공격 속성 → 강한 상대 2개(×1.5). 거꾸로 맞으면 ×0.67. 모든 속성이 강점 2·약점 2
export const strongAgainst:Record<Attr,[Attr,Attr]>={
  SEA:['FIRE','STONE'],FIRE:['ICE','BLOSSOM'],ICE:['SEA','GOLD'],BLOSSOM:['STONE','SHADOW'],STONE:['ICE','GRAVITY'],
  GOLD:['THREAD','GRAVITY'],THREAD:['BLOSSOM','STAR'],SHADOW:['STAR','GOLD'],STAR:['SEA','SHADOW'],GRAVITY:['THREAD','FIRE'],
};
export const STRONG=1.5,WEAK=0.67;
export function typeMult(attack:Attr,defend:Attr){
  if(strongAgainst[attack].includes(defend))return STRONG;
  if(strongAgainst[defend].includes(attack))return WEAK;
  return 1;
}

export type Role='ATTACK'|'DEFENSE'|'UTILITY';
export const roleName:Record<Role,string>={ATTACK:'공격',DEFENSE:'수비',UTILITY:'유틸'};
export type TopTier='LIMITED'|'TRANSCEND'|'ETERNAL'|'IMMORTAL';
export type Stat='atk'|'def'|'spd';
export type Status='burn'|'freeze'|'sleep'|'bind'|'rage';
export const statusName:Record<Status,string>={burn:'화상',freeze:'빙결',sleep:'잠',bind:'묶임',rage:'땅콩 폭주'};

// 기술 효과(엔진이 아는 것만)
export type Effect=
  |{kind:'buff';stat:Stat;pct:number;turns:number;target:'self'|'foe'|'team'}     // pct<0이면 약화
  |{kind:'status';status:Status;chance:number;turns:number}                        // 상대에게
  |{kind:'heal';pct:number;target:'self'|'bench'}                                  // 최대 체력 대비
  |{kind:'guard';pct:number;turns:number}                                          // 받는 피해 감소
  |{kind:'endure';turns:number}                                                    // 체력 1 아래로 안 떨어짐
  |{kind:'protect'}                                                                // 이번 턴 공격 무효
  |{kind:'regen';pct:number;turns:number;target:'self'|'team'}                     // 턴 끝 회복
  |{kind:'cleanse';immuneTurns:number}                                             // 아군(나와 대기) 상태 이상 해제
  |{kind:'revive';pct:number}                                                      // 쓰러진 아군 하나 되살림(전투당 1회)
  |{kind:'strip'}                                                                  // 상대 강화 제거
  |{kind:'recharge'}                                                               // 쓴 다음 턴 쉼
  |{kind:'lockSelf';atkPct:number};                                                // 교체 불가 대신 공격 강화(전투 내내)
export interface Skill {id:string;name:string;attr?:Attr;power?:number;hits?:number;priority?:boolean;crit?:boolean;cd:number;effects:Effect[];desc:string}

// 기본기(속성마다 하나, 위력 50, 재사용 없음)
export const basicMove:Record<Attr,string>={STONE:'돌머리 박치기',GOLD:'금화 튕기기',FIRE:'불씨 쪼기',ICE:'얼음 깃',BLOSSOM:'꽃잎 바람',SEA:'물보라',SHADOW:'그늘 할퀴기',STAR:'별똥 박치기',THREAD:'실 채찍',GRAVITY:'끌어당기기'};
export function basicSkill(a:Attr):Skill{return {id:`basic-${a}`,name:basicMove[a],attr:a,power:50,cd:0,effects:[],desc:'위력 50'};}
// 역할기(역할마다 하나)
export function roleSkill(role:Role,a:Attr):Skill{
  if(role==='ATTACK')return {id:'role-strike',name:`${attrName[a]} 강타`,attr:a,power:85,cd:2,effects:[],desc:'위력 85'};
  if(role==='DEFENSE')return {id:'role-brace',name:'버티기 자세',cd:3,effects:[{kind:'buff',stat:'def',pct:40,turns:2,target:'self'}],desc:'2턴 동안 방어 +40%'};
  return {id:'role-dampen',name:'기세 꺾기',attr:a,power:40,cd:3,effects:[{kind:'buff',stat:'atk',pct:-25,turns:3,target:'foe'}],desc:'위력 40, 3턴 동안 상대 공격 -25%'};
}

export interface UnitDef {id:string;attr:Attr;role:Role;signature:Skill}
// 최상위 33종. 고유기는 각 유닛 소개글의 소재에서 가져옴
const sig=(name:string,desc:string,cd:number,rest:Partial<Skill>={}):Skill=>({id:'sig',name,desc,cd,effects:[],...rest});
export const unitDefs:UnitDef[]=[
  // 돌
  {id:'D1',attr:'STONE',role:'DEFENSE',signature:sig('사방 방패','위력 55. 3턴 동안 받는 피해 -45%',4,{attr:'STONE',power:55,effects:[{kind:'guard',pct:45,turns:3}]})},
  {id:'T1',attr:'STONE',role:'DEFENSE',signature:sig('만상 방어','위력 70. 3턴 동안 받는 피해 -30%',3,{attr:'STONE',power:70,effects:[{kind:'guard',pct:30,turns:3}]})},
  {id:'E1',attr:'STONE',role:'ATTACK',signature:sig('뒤로 안 걷기','위력 100. 이후 교체할 수 없는 대신 공격 +25%',4,{attr:'STONE',power:100,effects:[{kind:'lockSelf',atkPct:25}]})},
  {id:'I1',attr:'STONE',role:'UTILITY',signature:sig('다섯 번째 목소리','쓰러진 아군 하나를 체력 50%로 일으킴(전투당 1번)',5,{effects:[{kind:'revive',pct:50}]})},
  // 황금
  {id:'D2',attr:'GOLD',role:'ATTACK',signature:sig('순금 일격','위력 110. 상대 강화를 모두 지움',3,{attr:'GOLD',power:110,effects:[{kind:'strip'}]})},
  {id:'D7',attr:'GOLD',role:'DEFENSE',signature:sig('노출형 전신 방어','위력 55. 3턴 동안 방어 +60%, 속도 -20%',4,{attr:'GOLD',power:55,effects:[{kind:'buff',stat:'def',pct:60,turns:3,target:'self'},{kind:'buff',stat:'spd',pct:-20,turns:3,target:'self'}]})},
  {id:'E2',attr:'GOLD',role:'UTILITY',signature:sig('황금 마을 입주','위력 55. 3턴 동안 아군 전체 방어 +25%',3,{attr:'GOLD',power:55,effects:[{kind:'buff',stat:'def',pct:25,turns:3,target:'team'}]})},
  // 불
  {id:'T2',attr:'FIRE',role:'ATTACK',signature:sig('1그램 정밀 주조','위력 95. 반드시 급소',3,{attr:'FIRE',power:95,crit:true,effects:[]})},
  {id:'I2',attr:'FIRE',role:'DEFENSE',signature:sig('다시 부어 만들기','체력 30% 회복, 화상 해제',5,{effects:[{kind:'heal',pct:30,target:'self'}]})},
  {id:'E8',attr:'FIRE',role:'UTILITY',signature:sig('온도 순환','위력 60. 아군 상태 이상을 풀고 2턴 동안 막음, 아군 모두 체력 10% 회복',3,{attr:'FIRE',power:60,effects:[{kind:'cleanse',immuneTurns:2},{kind:'heal',pct:10,target:'self'},{kind:'heal',pct:10,target:'bench'}]})},
  // 얼음
  {id:'T7',attr:'ICE',role:'ATTACK',signature:sig('봉인 해제','위력 135. 다음 턴은 쉼',3,{attr:'ICE',power:135,effects:[{kind:'recharge'}]})},
  {id:'D5',attr:'ICE',role:'UTILITY',signature:sig('한겨울 개화','위력 55. 3턴 동안 상대 속도 -40%, 30% 확률로 빙결',3,{attr:'ICE',power:55,effects:[{kind:'buff',stat:'spd',pct:-40,turns:3,target:'foe'},{kind:'status',status:'freeze',chance:30,turns:1}]})},
  // 꽃
  {id:'E5',attr:'BLOSSOM',role:'DEFENSE',signature:sig('지지 않는 꽃','체력 20% 회복, 3턴 동안 체력이 1 아래로 떨어지지 않음',5,{effects:[{kind:'heal',pct:20,target:'self'},{kind:'endure',turns:3}]})},
  {id:'T5',attr:'BLOSSOM',role:'UTILITY',signature:sig('골목 꽃밭','위력 55. 4턴 동안 아군 모두 턴마다 체력 8% 회복',4,{attr:'BLOSSOM',power:55,effects:[{kind:'regen',pct:8,turns:4,target:'team'}]})},
  {id:'I5',attr:'BLOSSOM',role:'UTILITY',signature:sig('다음 봄','체력 35% 회복, 3턴 동안 턴마다 6% 회복',4,{effects:[{kind:'heal',pct:35,target:'self'},{kind:'regen',pct:6,turns:3,target:'self'}]})},
  // 바다
  {id:'D4',attr:'SEA',role:'ATTACK',signature:sig('두 개의 바다','위력 65로 두 번',3,{attr:'SEA',power:65,hits:2,effects:[]})},
  {id:'I9',attr:'SEA',role:'ATTACK',signature:sig('바다 가르기','위력 100. 3턴 동안 상대 방어 -25%',3,{attr:'SEA',power:100,effects:[{kind:'buff',stat:'def',pct:-25,turns:3,target:'foe'}]})},
  {id:'I4',attr:'SEA',role:'DEFENSE',signature:sig('바다부터 말려라','체력 30% 회복, 2턴 동안 받는 피해 -30%',4,{effects:[{kind:'heal',pct:30,target:'self'},{kind:'guard',pct:30,turns:2}]})},
  {id:'E4',attr:'SEA',role:'UTILITY',signature:sig('되돌아오는 물','위력 50. 대기 아군 체력 20%, 자신 15% 회복',3,{attr:'SEA',power:50,effects:[{kind:'heal',pct:20,target:'bench'},{kind:'heal',pct:15,target:'self'}]})},
  // 그림자
  {id:'T3',attr:'SHADOW',role:'ATTACK',signature:sig('그림자 기습','위력 90. 무조건 먼저, 30% 확률로 잠',3,{attr:'SHADOW',power:90,priority:true,effects:[{kind:'status',status:'sleep',chance:30,turns:1}]})},
  {id:'I3',attr:'SHADOW',role:'DEFENSE',signature:sig('그림자에서 일어서기','체력 20% 회복, 2턴 동안 체력 1 아래로 안 떨어짐',5,{effects:[{kind:'heal',pct:20,target:'self'},{kind:'endure',turns:2}]})},
  {id:'D3',attr:'SHADOW',role:'UTILITY',signature:sig('졸려서 만든 밤','위력 45. 상대가 1턴 잠듦',3,{attr:'SHADOW',power:45,effects:[{kind:'status',status:'sleep',chance:100,turns:1}]})},
  {id:'E3',attr:'SHADOW',role:'UTILITY',signature:sig('아침이 안 오는 동네','위력 55. 4턴 동안 상대 공격 -20%, 속도 -15%',4,{attr:'SHADOW',power:55,effects:[{kind:'buff',stat:'atk',pct:-20,turns:4,target:'foe'},{kind:'buff',stat:'spd',pct:-15,turns:4,target:'foe'}]})},
  // 별
  {id:'D6',attr:'STAR',role:'ATTACK',signature:sig('몇 억 년 만의 점등','위력 150',4,{attr:'STAR',power:150,effects:[]})},
  {id:'E6',attr:'STAR',role:'UTILITY',signature:sig('약점 기록','위력 55. 3턴 동안 상대 방어 -35%',3,{attr:'STAR',power:55,effects:[{kind:'buff',stat:'def',pct:-35,turns:3,target:'foe'}]})},
  // 실
  {id:'E7',attr:'THREAD',role:'ATTACK',signature:sig('가발 선발대','무조건 먼저, 위력 45로 두 번',2,{attr:'THREAD',power:45,hits:2,priority:true,effects:[]})},
  {id:'T8',attr:'THREAD',role:'DEFENSE',signature:sig('수호 무늬 점등','위력 55. 3턴 동안 방어 +40%, 속도 +20%',4,{attr:'THREAD',power:55,effects:[{kind:'buff',stat:'def',pct:40,turns:3,target:'self'},{kind:'buff',stat:'spd',pct:20,turns:3,target:'self'}]})},
  {id:'I7',attr:'THREAD',role:'DEFENSE',signature:sig('절대 안 흘러내림','4턴 동안 받는 피해 -35%',4,{effects:[{kind:'guard',pct:35,turns:4}]})},
  {id:'D8',attr:'THREAD',role:'UTILITY',signature:sig('포장 완료','위력 75. 3턴 동안 상대를 묶음(교체 불가, 속도 -30%)',3,{attr:'THREAD',power:75,effects:[{kind:'status',status:'bind',chance:100,turns:3}]})},
  // 중력
  {id:'T4',attr:'GRAVITY',role:'ATTACK',signature:sig('바다를 한 점에','위력 120',3,{attr:'GRAVITY',power:120,effects:[]})},
  {id:'I6',attr:'GRAVITY',role:'DEFENSE',signature:sig('공전','2턴 동안 받는 피해 -40%, 3턴 동안 턴마다 10% 회복',4,{effects:[{kind:'guard',pct:40,turns:2},{kind:'regen',pct:10,turns:3,target:'self'}]})},
  {id:'I8',attr:'GRAVITY',role:'DEFENSE',signature:sig('중력 압박','3턴 동안 상대 속도 -50%, 자신 방어 +20%',3,{effects:[{kind:'buff',stat:'spd',pct:-50,turns:3,target:'foe'},{kind:'buff',stat:'def',pct:20,turns:3,target:'self'}]})},
  {id:'T6',attr:'GRAVITY',role:'UTILITY',signature:sig('궤도 조정','위력 60. 3턴 동안 아군 전체 속도 +30%, 상대 속도 -20%',3,{attr:'GRAVITY',power:60,effects:[{kind:'buff',stat:'spd',pct:30,turns:3,target:'team'},{kind:'buff',stat:'spd',pct:-20,turns:3,target:'foe'}]})},
];
export const unitDefMap=new Map(unitDefs.map(u=>[u.id,u]));

// 역할별 기본 능력치(레벨 1, 진화 전). 등급이 높을수록 조금 더 강함
export const roleBase:Record<Role,{hp:number;atk:number;def:number;spd:number}>={
  ATTACK:{hp:92,atk:30,def:14,spd:14},DEFENSE:{hp:128,atk:19,def:26,spd:9},UTILITY:{hp:106,atk:21,def:18,spd:15},
};
// 등급 차이는 작게(체력·공격·방어에 모두 붙어 겹치므로). 강함은 레벨·진화·증강체로
export const tierMult:Record<TopTier,number>={LIMITED:1,TRANSCEND:1.03,ETERNAL:1.06,IMMORTAL:1.09};
