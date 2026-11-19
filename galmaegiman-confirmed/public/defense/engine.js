// 갈매미맨 랜덤 디펜스 2판: 게임 규칙(화면과 분리). 브라우저와 Node 시험에서 같이 쓴다.
// - 위습: 처음 커먼 랜덤 위습 5개 + 웨이브마다 1개. 위습 하나로 그 등급 갈매미 1마리를 무작위로 부른다.
//   위습 등급은 커먼·언커먼·레어·에픽(보스·외부탐사 보상), 전설은 일부 갈매미의 「위습 찾기」 재주로만.
// - 물딜(타격수)·마딜(술사). 고체력·기습은 딜이 낮은 대신 방깍·스턴·이속감소 / 마방깍·마딜증폭.
// - 갈매미는 판 어디에나 둘 수 있고 겹쳐도 된다(수 제한 없음). 조합은 게임 속 조합법 52개(히든 포함).
// - 웨이브마다 35마리. 길 위의 적이 70마리를 넘은 동안 1초마다 경고 1회(누적), 20회를 넘으면 패배.
// - 10웨이브마다 보스. 그 라운드 안에 못 잡으면 바로 패배. 중후반엔 외부탐사(스토리 지역)에 갈매미를 보내 보상 위습.
import { UNITS, RECIPES } from './data.js?v=47b73634cac1';

export const GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'];
export const GRADE_NAME = { COMMON: '커먼', UNCOMMON: '언커먼', RARE: '레어', EPIC: '에픽', LEGEND: '전설', HIDDEN: '히든', LIMITED: '제한', TRANSCEND: '초월', IMMORTAL: '불멸', ETERNAL: '영원' };
export const WISP_GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGEND'];
export const UNIT = Object.fromEntries(UNITS.map((u) => [u.id, u]));
export { UNITS, RECIPES };
export const gradeTier = (g) => GRADES.indexOf(g);
export const tierOf = (id) => gradeTier(UNIT[id].grade);

// ── 판 모양(논리 좌표 540×960) ──
export const W = 540, H = 960;
export const FIELD = { x: 14, y: 100, w: 512, h: 564 };     // 갈매미를 둘 수 있는 곳
export const LOOP = { x0: 46, y0: 134, x1: 494, y1: 630 };  // 적이 도는 길(중심선)
export const SPAWN = { x: 270, y: 382 };                    // 새로 부른 갈매미가 나타나는 곳
// 커먼 소환 자리(2026-11-10 사용자 요청: 가운데가 아니라 오른쪽 길 안쪽 정해진 8곳 — 커먼 8종이 종류마다 제자리). 발밑 좌표
// 커먼을 창고에 넣으면(자동 창고를 켜도) 판의 언커먼이 같은 8곳에 종류별로 모임
export const COMMON_SPOTS = [186, 242, 298, 354, 410, 466, 522, 578].map((y) => ({ x: 445, y }));
const spotOf = (id) => { const g = UNIT[id]?.grade, i = (BY_GRADE[g] ?? []).indexOf(id); return COMMON_SPOTS[(i < 0 ? 0 : i) % COMMON_SPOTS.length]; };
const commonSpot = (g, id) => spotOf(id);
export function arrangeUncommon(g) {   // 판의 언커먼(고정한 것 빼고)을 종류별 자리로
  let n = 0;
  for (const u of fieldUnits(g)) if (UNIT[u.id].grade === 'UNCOMMON' && !u.lock) { const s = spotOf(u.id); if (moveUnit(g, u.uid, s.x, s.y, true)) n++; }
  return n;
}
const LW = LOOP.x1 - LOOP.x0, LH = LOOP.y1 - LOOP.y0, PERIM = 2 * (LW + LH);

// ── 규칙 수치(시험 봇으로 맞춤) ──
export const RULES = {
  // 용병모집(사용자 요청 2026-11-08): 모집권 4개로 레어 30% · 에픽 40% · 실패 30%
  recruitCost: 4, recruitOdds: { RARE: 0.3, EPIC: 0.4 }, bossTicket: 1, bossTicketEvery: 10, bossTicketLateFrom: 40, bossTicketLate: 2, bossRerollEvery: 10,   // 에픽 리롤권: 10웨이브 보스마다 1개(2026-11-12)
    // 10웨이브 보스 처치마다 모집권 1(소량). 2026-11-11 사용자 요청: 40웨이브부터는 5웨이브 보스마다 2개씩
  storyDeadline: 50,   // 스토리 마지막 장(2026-11-11부터 13장)은 이 웨이브가 끝날 때까지(사용자 요청 2026-11-08: 꼭 해결하게). 40·45웨이브에 알림
  waves: 60, waveLen: 40,   // 60웨이브(사용자 요청: 40에서 끝내지 말고 더)
  perWave: 35, spawnGap: 0.75, limit: 70, warnMax: 20,
  restEvery: 10, restLen: 30,   // 10웨이브마다 끝나고 30초 쉬는 시간(2026-11-09 사용자 요청)
  bossEvery: 5, startWisps: 6, wispPerWave: 3,   // 처음 커먼 위습 6개, 웨이브마다 커먼 3개(2026-11-10: 4+언커먼1로 늘렸다가 사용자 「위습이 너무 남음」으로 줄임)
  // 균열 저항(2026-11-11 사용자 요청: 40웨이브 뒤에 초불영제 없이 전설 2마리로 라인이 버텨지면 안 됨): 41웨이브부터 라인 적은
  // 전설·히든 이하 갈매미의 피해를 riftPer×(웨이브−40)만큼 덜 받음(최대 riftMax). 초불영제 피해·보스는 그대로
  riftFrom: 40, riftPer: 0.1, riftMax: 0.7,   // 41 −10% · 43 −30% · 45 −50% · 47웨이브부터 −70%
  midFrom: 20, midLineHp: 0.02, midBossHp: 0.01,   // 2026-11-15 사용자 요청(중후반 난이도 올리기, 증강체와 함께): 21웨이브부터 웨이브마다 일반 적 체력 +2%·보스 +1%(40웨이브 ×1.4·×1.2, 60웨이브 ×1.8·×1.4)
  lateFrom: 40, lateHpPer: 0.08, lateSpdPer: 0.04,   // 2026-11-16 전설 환산 기준(9·10·11마리)에 맞춰 체력 +4% → +8%(60웨이브 ×2.6).   // 40웨이브부터 웨이브마다 일반 적 체력 +4%·이동속도 +4%(60웨이브 체력 ×1.8·속도 ×1.8). 2026-11-15 사용자 요청: 입구에서 다 죽어 때리는 맛이 짧음 → 속도 1.5%→4%
  bossLen: 60, bossHpX: 1,   // 2026-11-15: 보스 웨이브에 일반 적이 같이 나오고 보스 먼저 때리게 바뀐 뒤 전설 환산 기준에 맞춰 ×1.6 → ×1(아래 설명은 그 전 값)
    // 2026-11-15 사용자 요청: 보스는 시간을 길게(40→60초) 주고 체력을 더(×1.6, 시간보다 조금 더 늘려 필요한 초당 피해 +7%)
  hardLimitFrom: 45, hardLimit: 50,
  stunBase: 0.2,   // 스턴·범위 스턴: 한 번 칠 때 확률 × 스턴 시간(초). 1초 20% · 1.5초 13% · 2초 10%
  physSplash: 0.29,   // 2026-11-16: 물리 주력(에픽 이상, 불 속성 제외) 스플래시 29%(30% 미만이라 보스 감점 없음). 달인 물리 조합 봇 0/60 → 10/60
  topLineBossMul: 1,   // 최상위 라인 정리형의 보스 피해 배율(전설 이하 라인형은 lineBossMul)
  hpX: 0.8,   // 적 체력 전체 배율. 2026-11-16: 선택 위습을 무작위로 바꾸고 몰아주던 모집권을 줄이며 1 → 0.8(달인 봇 깬 비율을 예전과 비슷하게)
  poisonMul: 0.15,   // 독 세기(자연 주력). 2026-11-16: 중첩·방어 무시를 넣으며 0.25 → 0.15(스턴 없는 이감+독 봇 달인 12/60, 일반 봇 9/60)
  poisonStack: 3,   // 독은 다른 갈매미 것 3겹까지 더해짐(2026-11-16)
  synergy: true,   // 시너지 켬
  skillCd: 90, skillFirst: 30, skillPct: 0.35, skillBossPct: 0.05,   // 갈매미맨 폭격
  landTime: 1.5,   // 내려놓은 뒤 주저앉아 행동 불능(초). 2026-11-16 사용자 요청 0.8 → 1.5
  weather: true,   // 날씨(11웨이브부터 5웨이브마다)
  spdX: 1.15,   // 2026-11-15 사용자 요청: 전체적으로 적 이동속도 증가(일반·보스 모두 ×1.15)   // 2026-11-15 사용자 요청: 45웨이브부터 길 위 적이 50마리를 넘으면 즉시 패배(그 전은 70마리 넘은 동안 경고)
  finaleLen: 25,   // 2026-11-16: 30 → 25초(전설 환산 10마리면 남은 적이 조금 남게)
    // 60웨이브 보스를 잡은 뒤 남은 적을 처리하는 시간(초). 끝날 때 남은 적 수를 기록, 0마리면 완벽 클리어
  hpBase: 55, hpGrowth: 1.16, hpLinear: 25,
  // 보스 체력 = 그 웨이브 적 체력 × 아래 배수(2026-11-09 보스 5웨이브마다: 10의 배수 웨이브는 예전 값 그대로, 5·15…는 그 사이)
  // 2026-11-09 사용자 요청: 마방깍 없이 마법 딜러만으로는 보스를 못 잡게. 웨이브별 기준 마법 조합이 보스에게 40초 동안 주는 피해를 재서
  // 체력 = 「마방깍 없을 때 피해×1.2」 이상 · 「있을 때 피해×0.75」 이하가 되게 맞춤(20웨이브부터, 사이 웨이브는 앞뒤 사이 값). 기준 물리 조합(방깍 포함)도 잡을 수 있음
  bossMul: { 5: 2, 10: 3.5, 15: 4, 20: 5.4, 25: 4.5, 30: 3.8, 35: 5.1, 40: 6.6, 45: 4.2, 50: 4.5, 55: 4.5, 60: 3.7 },   // 2026-11-16 사용자 기준(최상위가 있으면 50웨이브까지 보스는 잡음): 45 7.3→4.2 · 50 8→4.5 · 55 5.7→4.5
  // 적 체력 기준점 [웨이브, 체력](사이는 곱으로 이음). 1~10웨이브는 「초반이 많이 어렵다」는 의견으로 ×0.7. 사용자 요청(2026-11-08): 3웨이브쯤부터 커먼 풀강이 아니면 쌓이고,
  // 5웨이브쯤부터는 커먼만으로는 못 버티게. 등급별로 조합하는 봇이 버티는 한계를 재서 맞춤
  hpAnchors: [[1, 146], [2, 280], [3, 426], [4, 627], [5, 952], [6, 1120], [7, 1232], [8, 1400], [10, 1624], [12, 4160], [15, 6720], [20, 11700], [25, 25920], [30, 43200], [35, 70000], [40, 75000], [45, 82000], [50, 95000], [55, 114000], [60, 134000]],   // 2026-11-08 상위 한두 마리·전설·히든 중심으로 바꾸며 31웨이브부터 점점 낮춤(60웨이브 ×0.07)   // 위습 웨이브당 2개로 늘린 뒤 다시 맞춤
  armorBase: 4, armorPer: 2.4,   // (예전 값, 지금은 아래 def… 사용)
  // 방어(2026-11-09 사용자 요청: 40웨이브부터 오르고 그 전은 널널하게): 10웨이브 15 · 20웨이브 25 · 40웨이브 45 → 50웨이브 175 → 60웨이브 305(보스 ×1.3)
  defBase: 5, defPer: 1, defLateFrom: 40, defLatePer: 13, defBossMul: 1.3, armorPow: 2,
  // 등급별 기본 공격력(주력 기준 한 번 때릴 때)과 판매값(새우)
  atkSpeed: 2,
  lineBossMul: 0.6, singleBossMul: 1.35, singleMul: 1.8,   // 라인 정리형 보스 피해 ×0.6, 단일 공격형 ×1.35   // 모든 갈매미 공격 속도 2배·한 번 피해 ½(초당 피해 같음)
  topCap: 0,   // 상위(제한~영원) 최대 마릿수(0 = 제한 없음, 사용자 요청 2026-11-08: 따로 막을 필요 없음)
  // 2026-11-10 사용자 요청: 제한·초월·불멸·영원은 「초불영제」로 같은 등급 취급 → 기본 공격력·유틸·판매가 같음(강화 칸만 따로)
  dmg: [20, 90, 380, 2000, 8500, 11000, 30000, 30000, 30000, 30000],   // 2026-11-10 조합 비용 재조정(에픽·전설·히든이 비싸짐)에 맞춰 에픽 이상을 올림(예전 1500·5500·7500·24000)   // 상위는 전설의 약 2.7~6.5배(전엔 3~73배): 상위 한두 마리 + 전설·히든 중심   // 등급마다 약 4배: 조합하면 확실히 세지게
  sell: [5, 15, 40, 90, 200, 250, 1600, 1600, 1600, 1600],
  // 처치 새우: 웨이브가 갈수록 많이(사용자 요청). 1마리당 killGold + killGoldGrow×웨이브(반올림)
  // 처치 새우 = killGold + killGoldGrow×웨이브 + killGoldSq×웨이브² (후반일수록 많이: 후반 강화 비용에 맞춤)
  killGold: 1, killGoldGrow: 0.6, killGoldSq: 0.02,
  startUncommon: 1,   // 처음 언커먼 위습 1개(사용자 요청)
  startGold: 40,   // 처음 새우: 커먼·언커먼 강화 5단계까지(5+6+7+9+10=37) 바로 가능(사용자 요청)
  // 등급별 강화(사용자 요청 2026-11-08): 커먼·언커먼·레어·에픽·전설 이상 5칸, 한 단계마다 그 등급 공격력 +14%
  // 칸: 커먼·언커먼(묶음) · 레어 · 에픽 · 전설 · 히든 · 제한 · 초월 · 불멸 · 영원 (사용자 요청 2026-11-08)
  // 칸마다 최대 upgMax단계(풀강). 비용 = upgBase × upgGrowth^단계. 등급이 높을수록 훨씬 비쌈(초반과 상위 강화 비용이 비슷하지 않게)
  // 칸: 커먼·언커먼 · 레어 · 에픽 · 전설·히든(묶음) · 제한 · 초월 · 불멸 · 영원 (8칸)
  // 강화(2026-11-09 사용자 요청: 공격 속도도 오르게): 단계마다 공격력 +10% · 공격 속도 +3%. 10단계면 공격력 ×2 · 공속 ×1.3(예전 공격력 +15%×10 = ×2.5와 초당 피해가 비슷)
  upgPct: 0.10, upgSpd: 0.03, upgMax: 10, upgGrowth: 1.2, upgBase: [5, 20, 60, 150, 600, 600, 600, 600],   // 제한·초월·불멸·영원은 같은 가격(사용자 요청)
  bossReward: { 5: { COMMON: 1 }, 10: { EPIC: 1, COMMON: 1 }, 15: { COMMON: 1 }, 20: { EPIC: 1, UNCOMMON: 1 }, 25: { UNCOMMON: 1 }, 30: { EPIC: 1, UNCOMMON: 1 }, 35: { UNCOMMON: 1 }, 40: { EPIC: 1, RARE: 1 }, 45: { RARE: 1 }, 50: { EPIC: 1, RARE: 1 }, 55: { RARE: 1 } },   // 2026-11-16: 예전 보스 선택 위습(고르는 것)을 같은 등급 무작위 위습으로   // 2026-11-11 보상 검토: 20웨이브가 10웨이브(에픽)보다 적던 것(레어)을 에픽으로   // 2026-11-10 사용자 요청: 10웨이브 보스는 레어 → 에픽. 2026-11-08 위습 공급 줄임: 상위 4마리는 전설 위습 2개쯤 얻어야 쥐어짤 정도
  // 선택 위습(원하는 갈매미를 골라 소환): 처음 0개, 스토리(외부탐사)를 깰 때마다 받음(REGIONS의 pick). 보스는 랜덤 위습만
  // 보스마다 커먼 선택 위습(2026-11-09 사용자 요청: 커먼 선택 위습이 너무 적음). bossPick은 특정 웨이브에 더 줄 때
  // 2026-11-11 보상 검토: 보스마다 선택 위습 1개, 등급은 웨이브에 맞춰 5~15 커먼 · 20~35 언커먼 · 40~55 레어(후반 커먼 선택 위습은 쓸 데가 없었음)
  startPick: {}, bossPickEach: {}, bossPick: {},   // 2026-11-16 사용자 요청(재료를 퍼주는 느낌 — 무작위로 받은 재료로 최선의 조합을 짜게): 보스 선택 위습 없앰 → 같은 등급 무작위 위습으로(bossReward). 스토리 선택 위습도 무작위로, 9·11·12장 커먼 선택 5 → 무작위 3
  // 외부탐사 장별 대장 체력(사용자 요청: 10웨이브쯤 2장은 에픽 1마리를 보내야 깨지는 정도)
  // 3·4장: 에픽으로 3웨이브쯤 · 5·6장: 전설이 있어야 · 7장: 상위(히든 이상) 1마리는 있어야(사용자 요청)
  // 2026-11-11 보상 검토: 5장이 6장보다 높던 것(300만·250만)을 맞바꿈.
  // 2026-11-11 사용자 요청: 외부탐사 13장(마감은 그대로 50웨이브) → 7장부터 대장을 약하게 쪼개 촘촘히(예전 7·8장 600만·800만)
  guardHp: [20000, 150000, 400000, 600000, 2500000, 3000000, 3200000, 3600000, 4000000, 4500000, 5000000, 5600000, 6500000],   // 2026-11-08 상위 피해를 줄여 다시 맞춤: 5·6장 전설 둘+에픽(특기)으로 약 2웨이브, 7·8장 상위 하나+전설 둘로 약 2~3웨이브
  guardMul: 12,   // 외부탐사 대장 체력 = (지역 level+2 웨이브)의 적 체력 × 이 값
};

// 외부탐사: 갈매미맨 메인 스토리 7장 지역. 처음부터 1장을 보낼 수 있고, 앞 장을 끝내면 다음 장이 열린다(사용자 요청 2026-11-08).
// 보낸 갈매미가 그곳의 그림자 대장을 쓰러뜨리면 보상 위습(점점 높게). level은 대장 세기 기준 웨이브(열리는 시점과 무관).
export const REGIONS = [
  // 스토리 선택 위습: 장마다 1개(2026-11-10 사용자 「선택위습이 너무 많음」으로 3 → 1). 2026-11-11 보상 검토: 등급은 1~3장 커먼 · 4~6장 언커먼 · 7·8장 레어
  { ch: 1, name: '돌이 된 태초 갈매미', place: '마을 석상 터', img: 'identity-statue', look: 'b:u01', level: 8, reward: { UNCOMMON: 2, COMMON: 1 }, ticket: 1, gold: 150 },
  { ch: 2, name: '인간들이 기억하는 갈매미', place: '인간의 기록 장소', img: 'identity-records', look: 'b:u06', level: 12, reward: { RARE: 1, UNCOMMON: 1, COMMON: 1 }, ticket: 1, gold: 400 },
  { ch: 3, name: '사라진 기록', place: '인간의 기록 장소', img: 'identity-records', look: 'b:u07', level: 16, reward: { RARE: 2, COMMON: 1 }, ticket: 1, gold: 800 },
  { ch: 4, name: '말하지 않는 노인', place: '은신처', img: 'identity-hq', look: 'b:u23', level: 20, reward: { EPIC: 1, UNCOMMON: 1 }, ticket: 1, gold: 1500 },
  { ch: 5, name: '죽은 적이 없는 친구', place: '기억 속 작업장', img: 'identity-records', look: 'b:u14', level: 24, reward: { EPIC: 1, UNCOMMON: 1 }, ticket: 1, gold: 3000 },
  // 6장: 기본 보상 + 아래 중 하나 고르기(2026-11-10 사용자 요청: 초월 갈매미맨 대신 다른 보상도)
  { ch: 6, name: '함께 돌아오는 길', place: '마을 방어 준비', img: 'identity-hq', look: 'b:u28', level: 28, reward: { EPIC: 1, UNCOMMON: 1 }, ticket: 1, gold: 5000, unit: 'u81',
    choice: [{ unit: 'u81' }, { wisps: { LEGEND: 1 } }, { picks: { EPIC: 2 } }] },
  { ch: 7, name: '최초의 균열', place: '석상 앞', img: 'identity-statue', look: 'b:u31', level: 30, reward: { EPIC: 1, RARE: 1 }, ticket: 1, gold: 9000 },
  { ch: 8, name: '균열 너머의 그림자(외전)', place: '먼 바다', img: '10-distant-bay', look: 'e:v17', level: 31, reward: { EPIC: 1, LEGEND: 1, RARE: 1 }, ticket: 1, gold: 15000 },   // 2026-11-08 추가: 스토리 자료(7장)에 없는 게임 전용 외전, 최상위가 여럿 있어야 깸
  // 9~13장(2026-11-11 사용자 요청: 외부탐사 13장까지, 새 외전 이름). 보상: 새우 없음(같은 날 사용자 요청 — 강화 뒤 새우가 남아돎) → 에픽 위습 1 +
  // 9·11·12장 커먼 선택 위습 5 + 모집권 2 / 10·13장 모집권 10
  { ch: 9, name: '안개 속 항로(외전)', place: '먼 바다', img: 'identity-ship', look: 'e:v19', level: 32, reward: { EPIC: 1, COMMON: 3 }, ticket: 2 },
  { ch: 10, name: '그림자 상인(외전)', place: '항구 시장', img: 'identity-market', look: 'e:v20', level: 34, reward: { EPIC: 1 }, ticket: 3 },
  { ch: 11, name: '사라진 주민들(외전)', place: '마을 골목', img: 'identity-residents', look: 'e:v16', level: 35, reward: { EPIC: 1, COMMON: 3 }, ticket: 2 },
  { ch: 12, name: '해안의 잔불(외전)', place: '해안 절벽', img: 'identity-coast-panorama', look: 'e:v14', level: 36, reward: { EPIC: 1, COMMON: 3 }, ticket: 2 },
  { ch: 13, name: '균열의 심장(외전)', place: '석상 아래', img: 'identity-statue', look: 'b:u01', level: 37, reward: { EPIC: 1 }, ticket: 3 },
];

// 위습 찾기 재주: 최상위(제한·초월·불멸·영원) 갈매미만(사용자 요청 2026-11-08: 금각이 너무 쉽게 만들어져 뺌, 거장도 뺌).
// 적을 쓰러뜨리면 확률로 위습(전설 위습은 여기서만). 지금: 가루다(초월) · 리치(불멸) · 여명(영원)
export const WISP_SKILL = {
  // 2026-11-08 사용자 요청(전설 위습이 너무 잘 나옴): 처치 1마리당 전설 위습 확률 가루다 1.75%→0.3% · 리치 2.8%→0.5% · 여명 4%→0.78%
  u49: { p: 0.05, table: { EPIC: 0.94, LEGEND: 0.06 } },
  u56: { p: 0.055, table: { EPIC: 0.91, LEGEND: 0.09 } },
  u57: { p: 0.06, table: { EPIC: 0.87, LEGEND: 0.13 } },
};

// 탐사 특기(사용자 요청 2026-11-08: 외부탐사에 능한 재료 단계 갈매미, 외부탐사에서만 강해짐): 외부탐사 대장에게 주는 피해 배수.
// 재료로 쓰이면서 판 수비는 약한 보조(고체력)는 ×6, 근접술사는 ×3 → 같은 등급 주력보다 외부탐사 피해가 약 1.5~1.7배
// 2026-11-10 사용자 요청: 새 전설·히든에도 탐사 특기(같은 규칙 — 고체력 보조 ×6, 근접술사 ×3), 그 유닛을 재료로 쓰는 초불영제는 일부를 물려받음(EXPLORE_INHERIT)
// 2026-11-12 사용자 요청(탐사 특기 전설·히든이 초불영제보다 탐사 딜이 셈): ×6→×5, ×3→×2.5로 줄이고(×4·×2는 계획형 봇 달인 마지막 장 클리어 14/16→8/16으로 너무 떨어져 되돌림), 초불영제는 모두 기본 ×2·역할 감점 없음(EXPLORE_TOP)
export const EXPLORE = { u19: 5, u24: 2.5, u27: 5, u30: 2.5, u33: 5, u34: 2.5, u43: 2.5, u63: 2.5, u66: 5, u69: 2.5, u75: 2.5, u76: 5 };
export const EXPLORE_TOP = 2;
export const EXPLORE_INHERIT = 0.5;   // 초불영제: 재료 중 가장 높은 탐사 특기의 (배수−1)×0.5를 더함(×6 재료 → ×3.5, ×3 재료 → ×2)
// 전설·히든 이감·스턴 더하기(2026-11-09 사용자 요청: 선택지가 적음). 값은 그 등급 기본 세기에 곱하는 비율(전담 보조보다 조금 약하게)
// 전설: 이감 가시여왕·네레이드·설랑 / 스턴 거장·뇌호 · 히든: 이감 수경·꽃무덤·몽마 / 스턴 무명, 범위 스턴 금각
// 마방깍 더하기(2026-11-09 사용자 요청: 마방깍 유닛이 너무 적음 — 방깍 13종·마방깍 4종이었음): 비어 있던 등급마다 술사(주력)에 그 등급 마방깍의 60%
// 언커먼 먹구름 · 레어 요정 · 에픽 드라이어드·화룡 · 전설 잿불새 · 제한 베히모스 · 초월 가루다 · 불멸 등불정령
export const UTIL_EXTRA = {
  u34: { slow: 0.7 }, u37: { stun: 0.6 }, u39: { stun: 0.6 }, u40: { slow: 0.6 }, u43: { astun: 0.6 }, u44: { slow: 0.7 },
  u16: { mshred: 0.6 }, u18: { mshred: 0.6 }, u26: { mshred: 0.6 }, u32: { mshred: 0.6 }, u38: { mshred: 0.6 }, u46: { mshred: 0.6 }, u49: { mshred: 0.6 }, u54: { mshred: 0.6 },
  // 2026-11-10: 최상위였던 20종(u61~u80)을 전설·히든으로 — 유틸이 없던 것에 이감·스턴·범위 스턴·방깍을 나눔
  u61: { stun: 0.6 }, u64: { slow: 0.6 }, u65: { stun: 0.6 }, u67: { astun: 0.6 }, u68: { shred: 0.6 }, u69: { slow: 0.7 }, u70: { stun: 0.6 },
  u71: { shred: 0.6 }, u74: { slow: 0.7 }, u75: { astun: 0.6 }, u77: { stun: 0.6 }, u79: { slow: 0.6 }, u80: { stun: 0.6 },
};
// 주변 유닛 버프·패널티(2026-11-11 사용자 요청): 공격속도+(haste)·공격력+(might)를 나눠 주고, 딜이 지나치게 잘 나오는 유닛은 주변 공격력−(weaken).
// 자기 자신은 빼고 반경 안 판 위 다른 갈매미에게만. 겹치면 더함(버프 최대 +40%, 패널티 최대 −30%). 초불영제가 재료에게 물려받지는 않음
export const BUFF_R = 130, WEAKEN_R = 110, BUFF_MAX = 0.4, WEAKEN_MAX = 0.3;
const BUFF = (t) => (t < 3 ? 0 : t === 3 ? 0.08 : t === 4 ? 0.12 : t === 5 ? 0.15 : 0.2);   // 에픽 8% · 전설 12% · 히든 15% · 초불영제 20%
export const UTIL_BUFF = {
  u30: 'haste', u25: 'might',                       // 에픽: 그리핀(번개) · 룬장인(성)
  u67: 'haste', u33: 'might', u66: 'might',         // 전설: 금율 · 거장 · 만상
  u75: 'haste', u72: 'haste', u77: 'might', u76: 'might',   // 히든: 귀성 · 잔영 · 황금향 · 불퇴
  u86: 'haste', u88: 'haste', u54: 'might', u45: 'might',   // 초불영제: 갈쫄쫄이 · 갈발 · 등불정령 · 타이탄
};
export const UTIL_WEAKEN = ['u39', 'u71', 'u82', 'u52', 'u92', 'u50'];   // 무명·순금(히든 단일 최고 딜) · 원금(강타)·망령·갈압스타킹(처형)·레비아탄(유틸 많음)
// 이번 걸음의 판 위 버프(갈매미 번호 → [공격력 배수, 공격속도 배수]). 버프 갈매미가 없으면 비어 있음
export function buffsOf(g) {
  const f = fieldUnits(g), src = f.filter((u) => { const k = kit(u.id); return k.haste || k.might || k.weaken; }), out = new Map();
  if (!src.length) return out;
  for (const u of f) {
    let mi = 0, ha = 0, wk = 0;
    for (const b of src) {
      if (b === u) continue;
      const k = kit(b.id), d = Math.hypot(b.x - u.x, b.y - u.y);
      if (k.might && d <= BUFF_R) mi += k.might; if (k.haste && d <= BUFF_R) ha += k.haste; if (k.weaken && d <= WEAKEN_R) wk += k.weaken;
    }
    if (mi || ha || wk) out.set(u.uid, [1 + Math.min(BUFF_MAX, mi) - Math.min(WEAKEN_MAX, wk), 1 + Math.min(BUFF_MAX, ha)]);
  }
  return out;
}
const ELEM = { 불: 'FIRE', 물: 'WATER', 얼음: 'ICE', 번개: 'LIGHTNING', 자연: 'NATURE', 성: 'HOLY', 성술: 'HOLY', 어둠: 'DARK' };
export const ELEM_NAME = { FIRE: '불', WATER: '물', ICE: '얼음', LIGHTNING: '번개', NATURE: '자연', HOLY: '성', DARK: '어둠', NORMAL: '무' };
// 유틸 세기: 커먼~레어는 아주 작게, 에픽부터 의미 있게(사용자 요청 2026-11-08)
// 방깍·마방깍(겹침): 커먼 2 · 언커먼 4 · 레어 7 · 에픽 25 · 전설 45 · 히든 60 · 제한 90 · 초월 120 · 불멸 160 · 영원 210 (후반 방어 300~425를 2~4마리로 깎게)
const SHRED = (t) => [2, 4, 7, 30, 80, 100, 130, 160, 190, 220][t];   // 전설·히든도 크게(상위 없이도 깎는 조합을 짤 수 있게)
const AMP = (t) => (t < 3 ? [0.02, 0.03, 0.05][t] : 0.15 + 0.05 * (t - 3));  // 마딜증폭: 커먼 2% … 에픽 15% · 전설 20% …
// 이속감소는 갈매미마다 따로 걸려 모두 더한다(사용자 요청 2026-11-08: 20%씩 쌓여 100%가 되면 많이 느리게).
// 합계는 100%까지, 실제 속도 = 1 − 0.85×합계(합계 100%면 원래 속도의 15%로 기어감, 완전히 멈추는 건 스턴).
export const SLOW_MAX = 1, SLOW_EFFECT = 0.85;
export const slowMul = (total) => 1 - SLOW_EFFECT * Math.min(SLOW_MAX, total);
const SLOW = (t) => (t < 3 ? [0.06, 0.08, 0.12][t] : Math.min(0.5, 0.25 + 0.04 * (t - 3)));  // 이속감소
const STUN = (t) => (t < 3 ? [0.03, 0.05, 0.08][t] : Math.min(0.35, 0.18 + 0.03 * (t - 3))); // 스턴 확률
// 범위 스턴(전설 이상 보조): 공격마다 확률로 맞은 적 둘레 70 안 모두 스턴. 초월 이상은 20%(사용자 요청: 라인이 안 밀리게)
// 스턴 시간(2026-11-10 사용자 요청: 0.75~2초로 다양하게 — 다른 성능이 좋으면 짧게, 스턴 위주 유닛은 2초).
// 시작값: 보조(고체력 등, 스턴이 본업) 2.25초 · 주력(딜러, 공격이 본업) 1.75초. 스턴 말고 가진 큰 성능 하나마다 −0.25초, 0.75~2초로 자름.
// 큰 성능: 방깍 · 마방깍(그 등급 60% 이상, 전설·히든 모두에게 주는 작은 마방깍은 안 셈) · 마딜증폭 · 이감 · 범위 공격 · 스플래시 30% 이상 · 번개 연쇄 · 독 · 보스 추가 피해 · 위습 찾기
// (탐사 특기는 판 수비와 상관없는 외부탐사 전용이라 안 셈 → 고체력 스턴 보조는 모두 2초)
export const STUN_MIN = 1, STUN_MAX = 2;   // 2026-11-15: 1초 20% ~ 2초 10% 기준에 맞춰 최소 1초(예전 0.75초)
export function stunPerks(k, t) {
  const big = Math.round(SHRED(t) * 0.6);
  return [k.shred && 'shred', (k.mshred ?? 0) >= big && 'mshred', k.amp && 'amp', k.slow && 'slow', k.area && 'area', (k.splash ?? 0) >= 0.3 && 'splash',
    k.chain && 'chain', k.poison && 'poison', k.holy && 'holy', k.wisp && 'wisp'].filter(Boolean);   // 주변 버프(공속·공격력)는 셈하지 않음: 고체력 스턴 보조가 버프를 받아도 2초 그대로
}
export const stunTimeOf = (k, t) => Math.min(STUN_MAX, Math.max(STUN_MIN, (k.job === '보조' ? 2.25 : 1.75) - 0.25 * stunPerks(k, t).length));
export const BOSS_STUN = 0.25;   // 보스는 스턴 시간의 1/4
const ASTUN = (t) => (t < 4 ? 0 : t >= 7 ? 0.2 : [0.12, 0.14, 0.16][t - 4]);

// 초불영제 고유 기술(2026-11-11 사용자 요청: 상위 유닛은 각자 개성을 살린 공격). 공격 every번마다 한 번 나감(초불영제 피해라 균열 저항 안 받음)
// 기술 틀: smash 강타(대상 ×mul) · execute 처형(보스 아닌 적 체력 30% 이하면 쓰러뜨리고, 아니면 ×mul) · pierce 관통(대상 쪽 직선 위 모두 ×mul)
//          bounce 튕김(대상 + 가까운 적 n마리 ×mul) · nova 폭발(대상 둘레 r 안 모두 ×mul) · storm 낙뢰(사거리+60 안 적 n마리 ×mul)
//          bind 묶기(대상 둘레 r 안 모두 ×mul + sec초 스턴, 보스는 1/4) · field 장판(대상 자리에 sec초 동안 r 범위, 0.5초마다 ×mul + 이감 slow)
export const SIGNATURE = {
  u45: { name: '대지 짓누르기', kind: 'bind', every: 6, mul: 1, r: 80, sec: 1.25 },
  u46: { name: '가시덤불', kind: 'field', every: 5, mul: 0.3, r: 80, sec: 3, slow: 0.25 },
  u47: { name: '화염 폭발', kind: 'nova', every: 5, mul: 1.1, r: 110 },
  u48: { name: '빙결', kind: 'bind', every: 6, mul: 1, r: 90, sec: 1.5 },
  u49: { name: '천둥 날개', kind: 'storm', every: 5, mul: 0.9, n: 4, r: 50 },
  u50: { name: '해일 돌진', kind: 'pierce', every: 5, mul: 1.3, len: 280 },
  u51: { name: '뿌리 감옥', kind: 'field', every: 6, mul: 0.3, r: 85, sec: 3, slow: 0.35 },
  u52: { name: '영혼 수확', kind: 'execute', every: 5, mul: 1.6, r: 46 },
  u53: { name: '불사의 불길', kind: 'field', every: 5, mul: 0.35, r: 75, sec: 3, slow: 0.1 },
  u54: { name: '등불 튕기기', kind: 'bounce', every: 5, mul: 0.7, n: 5 },
  u55: { name: '똬리 휘감기', kind: 'nova', every: 5, mul: 1.1, r: 115 },
  u56: { name: '죽음의 손길', kind: 'execute', every: 6, mul: 1.6, r: 46 },
  u57: { name: '새벽 광선', kind: 'pierce', every: 5, mul: 1.2, len: 320 },
  u58: { name: '눈보라', kind: 'field', every: 5, mul: 0.25, r: 95, sec: 3, slow: 0.35 },
  u59: { name: '그림자 비', kind: 'storm', every: 5, mul: 0.9, n: 5, r: 50 },
  u60: { name: '물줄기 숨결', kind: 'pierce', every: 5, mul: 1.2, len: 300 },
  u82: { name: '황금 강타', kind: 'smash', every: 5, mul: 2, r: 46 },
  u83: { name: '어깨 밀치기', kind: 'bind', every: 6, mul: 1, r: 75, sec: 1.25 },
  u84: { name: '리본 휘감기', kind: 'bounce', every: 5, mul: 0.7, n: 4 },
  u85: { name: '깁스 고정', kind: 'bind', every: 5, mul: 1.2, r: 55, sec: 1.75 },
  u86: { name: '쫄쫄이 튕기기', kind: 'bounce', every: 5, mul: 0.75, n: 5 },
  u87: { name: '밀물', kind: 'nova', every: 5, mul: 1, r: 125 },
  u88: { name: '발도장', kind: 'smash', every: 4, mul: 1.8, r: 46 },
  u89: { name: '후끈 내복', kind: 'field', every: 5, mul: 0.35, r: 80, sec: 3, slow: 0.1 },
  u90: { name: '다섯 번째 목소리', kind: 'storm', every: 5, mul: 1, n: 5, r: 55 },
  u91: { name: '고무줄 사격', kind: 'pierce', every: 5, mul: 1.2, len: 280 },
  u92: { name: '조이기', kind: 'execute', every: 5, mul: 1.5, r: 46 },
  u93: { name: '슬리퍼 던지기', kind: 'bounce', every: 4, mul: 0.75, n: 4 },
};
const SIG_DESC = {
  smash: (s) => `대상${s.r ? `과 둘레(반경 ${s.r}) 적 모두` : '에게'} ×${s.mul} 강타`, execute: (s) => `체력 30% 이하 적은 바로 쓰러뜨림(보스 제외), 아니면 ×${s.mul}${s.r ? ` · 둘레(반경 ${s.r}) 다른 적도 ×${s.mul}` : ''}`,
  pierce: (s) => `대상 쪽 직선 위 적 모두 ×${s.mul}`, bounce: (s) => `가까운 적 ${s.n}마리에게 튕기며 ×${s.mul}`,
  nova: (s) => `대상 둘레 적 모두 ×${s.mul}`, storm: (s) => (s.r ? `적 ${s.n}곳에 범위 낙뢰(반경 ${s.r}, 맞은 자리 둘레 적 모두) ×${s.mul}` : `적 ${s.n}마리에게 낙뢰 ×${s.mul}`),
  bind: (s) => `둘레 적 모두 ×${s.mul} + ${s.sec}초 묶기`, field: (s) => `${s.sec}초 장판(0.5초마다 ×${s.mul}${s.slow ? `·이감 ${Math.round(s.slow * 100)}%` : ''})`,
};
export const sigText = (id) => { const s = SIGNATURE[id]; return s ? `고유 기술 「${s.name}」: 공격 ${s.every}번마다 ${SIG_DESC[s.kind](s)}` : ''; };
function signature(g, u, k, best, s) {
  const near = (x, y, r) => g.enemies.filter((e) => e.hp > 0 && Math.hypot(e.x - x, e.y - y) <= r);
  const fx = { kind: 'sig', sk: s.kind, uid: u.uid, name: s.name, x0: u.x, y0: u.y, x1: best.x, y1: best.y, elem: k.elem, type: k.type, t: g.t };
  // 2026-11-15 사용자 요청(「범위처럼 보이는데 한 마리만 맞음」): 강타·처형도 효과 크기(r)만큼 둘레 적에게 피해
  const around = () => { if (s.r) { for (const e of near(best.x, best.y, s.r)) if (e !== best) hurt(g, e, k.dmg * s.mul, k, u.uid); fx.r = s.r; } };
  if (s.kind === 'smash') { hurt(g, best, k.dmg * s.mul, k, u.uid); around(); }
  else if (s.kind === 'execute') { if (!best.boss && best.hp <= best.max * 0.3) { best.by = u.uid; best.hp = 0; } else hurt(g, best, k.dmg * s.mul, k, u.uid); around(); }
  else if (s.kind === 'pierce') {
    const dx = best.x - u.x, dy = best.y - u.y, L = Math.hypot(dx, dy) || 1, ux = dx / L, uy = dy / L;
    fx.x1 = u.x + ux * s.len; fx.y1 = u.y + uy * s.len;
    for (const e of g.enemies) { if (e.hp <= 0) continue; const px = e.x - u.x, py = e.y - u.y, along = px * ux + py * uy; if (along < 0 || along > s.len) continue; if (Math.abs(px * uy - py * ux) <= 26) hurt(g, e, k.dmg * s.mul, k, u.uid); }
  } else if (s.kind === 'bounce') {
    const pts = [[best.x, best.y]], done = new Set([best]); let cur = best; hurt(g, best, k.dmg * s.mul, k, u.uid);
    for (let i = 0; i < s.n; i++) { const nx = near(cur.x, cur.y, 130).filter((e) => !done.has(e)).sort((a, b) => Math.hypot(a.x - cur.x, a.y - cur.y) - Math.hypot(b.x - cur.x, b.y - cur.y))[0]; if (!nx) break; done.add(nx); hurt(g, nx, k.dmg * s.mul, k, u.uid); pts.push([nx.x, nx.y]); cur = nx; }
    fx.pts = pts;
  } else if (s.kind === 'nova') { for (const e of near(best.x, best.y, s.r)) hurt(g, e, k.dmg * s.mul, k, u.uid); fx.r = s.r; }
  else if (s.kind === 'storm') {
    const pool = near(u.x, u.y, k.range + 60), pts = [];
    for (let i = 0; i < s.n && pool.length; i++) { const e = pool.splice(Math.floor(g.rand() * pool.length), 1)[0], x = e.x, y = e.y; if (s.r) { for (const o of near(x, y, s.r)) hurt(g, o, k.dmg * s.mul, k, u.uid); } else hurt(g, e, k.dmg * s.mul, k, u.uid); pts.push([x, y]); }
    fx.pts = pts; if (s.r) fx.r = s.r;
  } else if (s.kind === 'bind') {
    for (const e of near(best.x, best.y, s.r)) { hurt(g, e, k.dmg * s.mul, k, u.uid); if (e.speed) e.stunUntil = Math.max(e.stunUntil, g.t + s.sec * (e.boss ? BOSS_STUN : 1)); }
    fx.r = s.r;
  } else if (s.kind === 'field') { (g.zones ??= []).push({ x: best.x, y: best.y, r: s.r, id: u.id, uid: u.uid, mul: s.mul, slow: s.slow ?? 0, next: g.t, until: g.t + s.sec, elem: k.elem }); fx.r = s.r; }
  g.fx.push(fx);
}
function tickZones(g) {   // 장판: 0.5초마다 범위 안 적에게 피해·이감
  if (!g.zones?.length) return;
  for (const z of g.zones) {
    if (g.t < z.next) continue;
    z.next += 0.5; const k = kit(z.id);
    for (const e of g.enemies) {
      if (e.hp <= 0 || Math.hypot(e.x - z.x, e.y - z.y) > z.r) continue;
      hurt(g, e, k.dmg * z.mul, k, z.uid);
      if (z.slow && e.speed) { (e.slows ??= {})['z' + z.uid] = { v: z.slow, until: g.t + 0.6 }; e.slowUntil = Math.max(e.slowUntil ?? 0, g.t + 0.6); }
    }
  }
  g.zones = g.zones.filter((z) => g.t < z.until);
}
// 갈매미 한 마리의 공격 방식. 같은 규칙이면 결과가 같으므로 한 번 계산해 둠(2026-11-10 렉 줄이기: 매 프레임·매 틱 유닛마다 다시 계산했음).
// 규칙 값(RULES.dmg 등)이 바뀌면 다시 계산. 돌려준 객체는 고치지 않는다
const kitCache = new Map(); let kitKey = [];
const kitKeyNow = () => [RULES.dmg, RULES.sell, RULES.atkSpeed, RULES.mshredAll, RULES.singleMul, RULES.lineBossMul, RULES.singleBossMul, RULES.topLineBossMul, RULES.poisonMul, RULES.physSplash];
export function kit(id) {
  const key = kitKeyNow();
  if (key.some((v, i) => v !== kitKey[i])) { kitCache.clear(); kitKey = key; }
  let k = kitCache.get(id);
  if (!k) { k = kitRaw(id); kitCache.set(id, k); }
  return k;
}
function kitRaw(id) {
  // 초월 갈매미맨(2026-11-10 사용자 요청): 데미지 0, 외부탐사 6장 보상, 초월 등급 조합에 재료로 쓰이면 사라짐
  if (UNIT[id]?.role === SPECIAL_ROLE) return { job: '재료', type: 'phys', elem: 'NORMAL', tier: tierOf(id), dmg: 0, gap: 99, range: 0, mul: 0, sell: 0, wisp: null, explore: 0, special: true };
  const u = UNIT[id], tt = tierOf(id), t = tt >= TOP_TIER ? TOP_POWER_TIER : tt, role = u.role,   // t: 능력치 계산용(초불영제는 모두 같은 값), tt: 실제 등급(강화 칸·물려받기)
    elem = ELEM[role.split(' ')[0]] ?? 'NORMAL';
  const tank = role.startsWith('고체력'), ambush = role.includes('기습'), mage = role.endsWith('술사');
  const type = mage ? 'mag' : 'phys';
  let k;
  if (tank) k = { job: '보조', gap: 1.1, mul: 0.35, range: 150, stun: STUN(t), shred: SHRED(t), ...(ASTUN(t) ? { astun: ASTUN(t) } : {}) };
  else if (ambush && mage) k = { job: '보조', gap: 0.6, mul: 0.3, range: 170, mshred: SHRED(t), amp: AMP(t) };
  else if (ambush) k = { job: '보조', gap: 0.5, mul: 0.3, range: 160, shred: SHRED(t), slow: SLOW(t) };
  else if (role.endsWith('근접술사')) k = { job: '주력', gap: 0.9, mul: 0.75, range: 140, area: 70 };
  else if (mage) k = { job: '주력', gap: 1.1, mul: 1.4, range: 230 };
  else k = { job: '주력', gap: 0.8, mul: 1, range: 170 };
  if (k.job === '주력') {   // 주력의 속성 덤(작게)
    // 스플래시(사용자 요청 2026-11-08: 범위·스플래시 공격): 술사(집중)는 맞은 적 둘레 45 안에 30%, 불 속성은 50 안에 40%(타격수 포함)
    // 낮은 등급은 작게(초반 난이도 유지): 커먼·언커먼 10% · 레어 20% · 에픽 이상 30%
    if (mage && !k.area) { k.splash = t < 2 ? 0.1 : t < 3 ? 0.2 : 0.3; k.splashR = 45; }
    if (elem === 'FIRE') { k.splash = 0.4; k.splashR = 50; }
    if (elem === 'LIGHTNING') k.chain = 2;
    if (elem === 'ICE') k.slow = SLOW(t) * 0.8;
    if (elem === 'WATER') k.slow = SLOW(t) * 0.5;
    if (elem === 'NATURE') k.poison = RULES.poisonMul ?? 0.25;   // 한 번 피해 × 이 값을 초당(독)
    if (elem === 'DARK') { if (mage) k.mshred = SHRED(t); else k.shred = SHRED(t); }   // 어둠: 방깍·마방깍 그대로(마방깍 갈매미가 적어 후반 마법방어를 깎을 수 있게)
    if (elem === 'HOLY') k.holy = 1.5;
    // 2026-11-16 봇 집계: 물리 조합이 라인 정리를 못 해 45웨이브 즉시 패배로 거의 다 짐 → 물리 주력(에픽 이상)도 작은 스플래시(30% 미만이라 보스 감점 없음)
    if (!mage && !k.splash && t >= 3 && RULES.physSplash) { k.splash = RULES.physSplash; k.splashR = 45; }
  }
  const ux = UTIL_EXTRA[id];
  if (ux) {
    if (ux.slow) k.slow = Math.max(k.slow ?? 0, SLOW(t) * ux.slow);
    if (ux.stun) k.stun = Math.max(k.stun ?? 0, STUN(t) * ux.stun);
    if (ux.astun) k.astun = Math.max(k.astun ?? 0, Math.max(ASTUN(t), 0.12) * ux.astun);
    if (ux.mshred) k.mshred = Math.max(k.mshred ?? 0, Math.round(SHRED(t) * ux.mshred));
    if (ux.shred) k.shred = Math.max(k.shred ?? 0, Math.round(SHRED(t) * ux.shred));
  }
  if (UTIL_BUFF[id]) k[UTIL_BUFF[id]] = BUFF(t);
  if (UTIL_WEAKEN.includes(id)) k.weaken = t >= 6 ? 0.12 : 0.1;
  // 2026-11-09 사용자 요청: 전설·히든은 모두, 마법 딜러(술사 주력)는 등급 상관없이 모두 마방깍(적더라도). 위 표에 없으면 그 등급 마방깍의 35%
  if (!k.mshred && (t === 4 || t === 5 || (mage && k.job === '주력'))) k.mshred = Math.max(1, Math.round(SHRED(t) * (RULES.mshredAll ?? 0.35)));
  // 역할(사용자 요청 2026-11-08): 라인 정리를 잘하면 보스 딜이 부족하고, 보스 딜이 좋으면 단일 공격만 좋게
  if (k.job === '주력') {
    k.line = !!(k.area || k.chain || (k.splash ?? 0) >= 0.3); k.bossMul = k.line ? (tt >= TOP_TIER ? (RULES.topLineBossMul ?? RULES.lineBossMul) : RULES.lineBossMul) : RULES.singleBossMul;   // 2026-11-16: 최상위 라인 정리형은 보스 감점 덜(50웨이브까지 보스를 잡을 수 있게)
    if (!k.line && t >= 4) k.mul *= RULES.singleMul ?? 1;   // 전설 이상 단일 공격형은 한 마리에게 더 세게(범위가 없는 대신, 초반 난이도는 그대로)
  }
  // 공격 속도(사용자 요청 2026-11-08: 공속이 느려 보는 맛이 없음): 공격 간격 ÷AS, 한 번 피해 ÷AS → 초당 피해는 같음.
  // 공격마다 굴리는 스턴·범위 스턴 확률도 ÷AS(초당 스턴 횟수 같게), 독은 한 번 피해에 비례하므로 ×AS(초당 독 피해 같게)
  const AS = RULES.atkSpeed ?? 1;
  if (k.stun) k.stun /= AS;
  if (k.astun) k.astun /= AS;
  if (k.poison) k.poison *= AS;
  // 2026-11-10 사용자 요청: 효과(방깍·마방깍·증폭·이감·스턴·독)는 에픽 이상만 — 커먼·언커먼·레어는 공격만
  if (tt < 3) for (const key of ['shred', 'mshred', 'amp', 'slow', 'stun', 'astun', 'poison', 'haste', 'might', 'weaken']) delete k[key];
  const out = { ...k, sig: sigText(id) || undefined, gap: k.gap / AS, type, elem, tier: tt, dmg: Math.round(RULES.dmg[t] * k.mul / AS), sell: RULES.sell[t], wisp: WISP_SKILL[id] ?? null, explore: EXPLORE[id] ?? 0 };
  if (tt >= TOP_TIER) {
    inheritUtil(id, out);
    const m = Math.max(0, ...(RECIPES.find((q) => q.out === id)?.in ?? []).map((i) => EXPLORE[i] ?? 0));
    if (m > 1) { const v = 1 + (m - 1) * EXPLORE_INHERIT; if (v > (out.explore || 0)) { out.explore = v; out.exploreFrom = true; } }
    if ((out.explore || 0) < EXPLORE_TOP) { out.explore = EXPLORE_TOP; out.exploreTop = true; delete out.exploreFrom; }
    out.exDmg = Math.round(RULES.dmg[t] * Math.max(k.mul, 1) / AS);   // 탐사에서는 보조(피해 ×0.3)·라인형(대장 ×0.6) 감점 없음
  }
  // 2026-11-10 사용자 요청: 방깍과 마방깍은 둘 다 갖지 못함 → 물딜은 방깍, 마딜은 마방깍만 남김
  if (out.shred && out.mshred) { if (out.type === 'phys') delete out.mshred; else delete out.shred; }
  // 스턴: 맞힐 때 스턴 확률로 그 적이 stunTimeOf초 멈춤(1~2초, 위 설명). 보스는 그 1/4
  if (out.stun || out.astun) out.stunTime = stunTimeOf(out, t);
  // 2026-11-15 사용자 요청(기준값): 스턴 1초면 20%, 2초면 10% → 한 번 칠 때 확률 = stunBase ÷ 스턴 시간(확률×시간 = 0.2초로 같음)
  if (out.stun) out.stun = RULES.stunBase / out.stunTime;
  if (out.astun) out.astun = RULES.stunBase / out.stunTime;
  return out;
}
// 최상위(제한~영원)는 조합 재료의 유틸을 더 세게 물려받음(2026-11-09 사용자 요청).
// 물딜은 방깍, 마딜은 마방깍·마딜증폭만(다른 쪽 것은 안 받음) — 재료 값의 INHERIT배. 이감·스턴·범위 스턴은 둘 다, 재료 값 그대로(사용자 요청). 자기 값과 큰 것
export const INHERIT = 1.3;
const INHERIT_CAP = { slow: 0.6, stun: 0.3, astun: 0.2 };
function inheritUtil(id, k) {
  const r = RECIPES.find((q) => q.out === id); if (!r) return;
  // 2026-11-16 사용자 요청(최상위에 유틸이 너무 많이 몰림): 이감 · 스턴(범위 스턴 포함) · 깎기(물딜 방깍 / 마딜 마방깍·증폭) 세 갈래 중
  // 자기에게 없는 갈래 하나만 물려받음(재료 값이 가장 센 갈래). 전설 마방깍 덤(35%)은 깎기 갈래로 치지 않음
  const fam = { slow: ['slow'], stun: ['stun', 'astun'], cut: k.type === 'phys' ? ['shred'] : ['mshred', 'amp'] };
  const ref = { slow: 0.5, stun: 0.2, astun: 0.2, shred: SHRED(4), mshred: SHRED(4), amp: AMP(4) };
  const small = Math.round(SHRED(TOP_POWER_TIER) * (RULES.mshredAll ?? 0.35));
  const has = (f) => fam[f].some((key) => (k[key] ?? 0) > (key === 'mshred' ? small : 0));
  k.inherited = [];
  let pick = null, pv = 0;
  for (const f of Object.keys(fam)) {
    if (has(f)) continue;
    let sc = 0; for (const key of fam[f]) for (const ing of r.in) sc = Math.max(sc, (kit(ing)[key] ?? 0) / ref[key]);
    if (sc > pv) { pv = sc; pick = f; }
  }
  if (!pick) return;
  for (const key of fam[pick]) {
    let best = 0;
    for (const ing of r.in) { const v = kit(ing)[key] ?? 0; if (v > best) best = v; }
    if (!best) continue;
    let v = INHERIT_CAP[key] ? best : best * INHERIT;   // 이감·스턴은 그대로, 깎기·증폭만 더 세게
    if (INHERIT_CAP[key]) v = Math.min(INHERIT_CAP[key], v);
    if (key === 'shred' || key === 'mshred') v = Math.round(v);
    if (v > (k[key] ?? 0)) { k[key] = v; k.inherited.push(key); }
  }
}
// 화면용 짧은 설명
export function kitTips(k) {
  const out = [];
  if (k.special) return ['공격하지 않음', '초월 등급 조합 재료(쓰면 사라짐)', '외부탐사 6장 보상'];
  if (k.shred) out.push(`방깍 ${Math.round(k.shred)}`);
  if (k.mshred) out.push(`주변 마방깍 ${Math.round(k.mshred)}`);
  if (k.amp) out.push(`마딜증폭 ${Math.round(k.amp * 100)}%`);
  if (k.stun) out.push(`사거리 안 스턴 ${Math.round(k.stun * 100)}%(${k.stunTime ?? 1}초)`);
  if (k.astun) out.push(`범위 스턴 ${Math.round(k.astun * 100)}%(${k.stunTime ?? 1}초)`);
  if (k.job === '주력') out.push(k.line ? `라인 정리형(보스 피해 ×${k.bossMul})` : `단일 공격형(보스 피해 ×${k.bossMul})`);
  if (k.explore) out.push(k.exploreTop ? `최상위 탐사: 외부탐사 피해 ×${k.explore}(역할 감점 없음)` : `탐사 특기${k.exploreFrom ? '(재료에게 물려받음)' : ''}: 외부탐사 피해 ×${k.explore}${k.exDmg ? '(역할 감점 없음)' : ''}`);
  if (k.slow) out.push(`주변 이속감소 ${Math.round(k.slow * 100)}%(겹침)`);
  if (k.area) out.push(`범위 공격(반경 ${k.area})`);
  if (k.splash) out.push(`스플래시 ${Math.round(k.splash * 100)}%(반경 ${k.splashR ?? 50})`);
  if (k.chain) out.push('번개 연쇄');
  if (k.poison) out.push('독(방어 무시·다른 독과 3겹)');
  if (k.holy) out.push('보스 추가 피해');
  if (k.wisp) out.push('위습 찾기');
  if (k.inherited?.length) out.push('재료 유틸 물려받음');
  // 앞쪽(정보 칸에서 잘리지 않게): 고유 기술 → 패널티 → 주변 버프
  const head = [k.sig, k.weaken && `패널티: 주변 공격력 −${Math.round(k.weaken * 100)}%(반경 ${WEAKEN_R})`,
    k.haste && `주변 공격속도 +${Math.round(k.haste * 100)}%(반경 ${BUFF_R})`, k.might && `주변 공격력 +${Math.round(k.might * 100)}%(반경 ${BUFF_R})`].filter(Boolean);
  out.unshift(...head);
  return out;
}

export function pathPos(s) {   // 2026-11-12 사용자 요청: 반시계 방향(왼쪽 위에서 아래로 → 오른쪽 → 위 → 왼쪽)
  let d = ((s % PERIM) + PERIM) % PERIM;
  if (d < LH) return { x: LOOP.x0, y: LOOP.y0 + d };
  d -= LH;
  if (d < LW) return { x: LOOP.x0 + d, y: LOOP.y1 };
  d -= LW;
  if (d < LH) return { x: LOOP.x1, y: LOOP.y1 - d };
  d -= LH;
  return { x: LOOP.x1 - d, y: LOOP.y0 };
}
// 배치: 길(적이 도는 사각형) 안쪽에서만 자유롭게(사용자 요청 2026-11-08). 길 폭 40의 안쪽 가장자리에서 그림 여유만큼 들임
export const INNER = { x0: LOOP.x0 + 20 + 18, x1: LOOP.x1 - 20 - 18, y0: LOOP.y0 + 20 + 30, y1: LOOP.y1 - 20 - 4 };
export const clampField = (x, y) => ({ x: Math.max(INNER.x0, Math.min(INNER.x1, x)), y: Math.max(INNER.y0, Math.min(INNER.y1, y)) });

export function rng(seed) {   // f.s = 지금 상태(이어하기 저장에 씀)
  const f = () => { f.s = (f.s + 0x6D2B79F5) >>> 0; let t = f.s; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  f.s = seed >>> 0;
  return f;
}
const BY_GRADE = Object.fromEntries(GRADES.map((g) => [g, UNITS.filter((u) => u.grade === g).map((u) => u.id)]));

// 웨이브 정보: 일반 → 갑옷(방어력 높음) → 결계(마법방어 높음) 순서, 10웨이브마다 보스
// 웨이브별 적 모습: 1~15 쭈매미 · 16~30 끼룩단 · 31~45 끼룩단 정예·검게 물든 졸개 · 46~59 흑화 갈매미
export const ENEMY_LOOK = [
  [15, ['e:v1', 'e:v2', 'e:v3', 'e:v4']],
  [30, ['e:v12', 'e:v13', 'e:v14', 'e:v19']],
  [45, ['e:v16', 'e:v19', 'e:v14', 'e:v13']],
  [99, ['b:u02', 'b:u06', 'b:u07', 'b:u14', 'b:u23', 'b:u28', 'b:u31']],
];
// 보스: 10 여덟발 대장 · 20 쭈라켄 · 30 끼룩단 행동대장 · 40 블랙 갈매미맨 · 50 흑화 대장 · 60 흑화 갈매미맨
export const BOSS_LOOK = ['e:v5', 'e:v6', 'e:v15', 'e:v17', 'b:u31', 'b:u01'];
export const defAt = (w) => RULES.defBase + RULES.defPer * Math.min(w, RULES.defLateFrom) + RULES.defLatePer * Math.max(0, w - RULES.defLateFrom);
// 피해 배율 = (100 / (100 + 남은 방어))^armorPow. 2제곱이라 방어가 높으면 피해가 크게 줄고, 깎으면 크게 늘어난다
export const defMul = (left) => Math.pow(100 / (100 + Math.max(0, left)), RULES.armorPow);
export function waveInfo(w, line = false) {   // line: 보스 웨이브에 같이 나오는 일반 적
  const boss = !line && w % RULES.bossEvery === 0;
  // 적 체력: 초반(hpEarlyN웨이브까지)은 hpEarly배씩 가파르게, 그 뒤는 hpGrowth배씩
  const nE = RULES.hpEarlyN ?? 0, eW = Math.min(w - 1, nE), lW = Math.max(0, w - 1 - nE);
  let hp = Math.round(RULES.hpBase * Math.pow(RULES.hpEarly ?? RULES.hpGrowth, eW) * Math.pow(RULES.hpGrowth, lW) * (1 + w / RULES.hpLinear));
  // 기준점 표가 있으면 그 사이를 곱으로 이어 쓴다(등급별 봇이 버티는 한계에 맞춘 값)
  const A = RULES.hpAnchors;
  if (A && A.length) {
    let k = A.findIndex(([aw]) => aw >= w);
    if (k < 0) hp = Math.round(A[A.length - 1][1]);
    else if (k === 0 || A[k][0] === w) hp = Math.round(A[k][1]);
    else { const [w0, h0] = A[k - 1], [w1, h1] = A[k]; hp = Math.round(h0 * Math.pow(h1 / h0, (w - w0) / (w1 - w0))); }
  }
  // 방어력·마법방어력(사용자 요청 2026-11-08: 웨이브마다 갑옷·결계를 번갈아 두지 않고, 모든 적이 둘 다 가지며 후반에 가파르게 올라
  // 방깍·마방깍 조합을 짜지 않으면 못 깨게). defAt(w)까지는 완만, defLateFrom 웨이브부터 웨이브마다 defLatePer씩
  const def = defAt(w);
  const kind = boss ? 'boss' : '일반';
  const armor = Math.round(def * (boss ? RULES.defBossMul : 1)), mr = armor;
  // 적 모습(사용자 요청 2026-11-08: 끼룩단·쭈매미·흑화 갈매미맨 그림, 색 입히지 않음). e: 적 그림, b: 흑화 갈매미 그림
  const look = boss ? BOSS_LOOK[Math.min(BOSS_LOOK.length, Math.ceil(w / 10)) - 1] : (() => { const band = ENEMY_LOOK.find(([to]) => w <= to)[1]; return band[w % band.length]; })();
  // 2026-11-10 사용자 요청(장인도 40웨이브쯤이면 다 만들어져 느슨함): lateFrom웨이브부터 일반 적 체력·이동속도가 웨이브마다 더 오름(보스는 그대로)
  const late = Math.max(0, w - (RULES.lateFrom ?? 999));
  const mid = Math.max(0, w - (RULES.midFrom ?? 999));
  return { boss, kind, armor, mr, look, baseHp: hp, hp: boss ? hp * (RULES.bossMul[w] ?? 2) * (RULES.bossHpX ?? 1) * (1 + (RULES.midBossHp ?? 0) * mid) : Math.round(hp * (1 + (RULES.lateHpPer ?? 0) * late) * (1 + (RULES.midLineHp ?? 0) * mid)),
    speed: (boss ? 34 : (56 + Math.min(30, w)) * (1 + (RULES.lateSpdPer ?? 0) * late)) * (RULES.spdX ?? 1), gold: Math.round((boss ? 30 : 1) * (RULES.killGold + RULES.killGoldGrow * w + (RULES.killGoldSq ?? 0) * w * w)) };
}
export function regionGuard(k) {
  const r = REGIONS[k], wi = waveInfo(r.level + 2);
  // 보스 웨이브여도 보스 배수는 쓰지 않는다(예전엔 1장 대장이 보스 체력×25가 되어 깰 수 없었음)
  const hp = RULES.guardHp?.[k] ?? Math.round(wi.baseHp * RULES.guardMul);
  return { hp, armor: Math.round(wi.armor * 1.3), mr: Math.round(wi.mr * 1.3) };
}

// 도전 모드(사용자 요청 2026-11-08): 기록은 모드마다 따로
export const MODES = {
  normal: { name: '일반', desc: '기본 규칙' },
  hard: { name: '하드', desc: '적 체력 ×1.5 (보스·외부탐사 대장 포함)', hpMul: 1.5 },
  noupg: { name: '강화 금지', desc: '등급별 강화를 할 수 없어요', noUpg: true },
  nostory: { name: '외부탐사 금지', desc: '외부탐사(스토리)를 보낼 수 없어요', noMission: true },
};
export const modeOf = (g) => MODES[g.mode] ?? MODES.normal;
// ── 증강체(2026-11-15 사용자 요청: 「게임 흐름이 바뀌는 웨이브에 한 번씩, 총 2번」) ──
// 20웨이브 보스(중반으로 넘어감)·40웨이브 보스(후반: 이속·체력 증가·균열 저항이 시작) 처치 직후 세 개 중 하나를 고름. 판이 끝날 때까지 유지
export const AUG_WAVES = [20, 40];
export const AUGS = {
  // 20웨이브: 판 짜는 방향을 정하는 것
  phys: { name: '날 선 부리', desc: '물리 공격 피해 +20%', v: 0.2 },
  mag: { name: '마력 폭주', desc: '마법 공격 피해 +20%', v: 0.2 },
  haste: { name: '날갯짓 가속', desc: '모든 갈매미 공격 속도 +12%', v: 0.12 },
  shred: { name: '부식', desc: '방깍·마방깍 효과 +35%', v: 0.35 },
  slow: { name: '끈끈한 바다', desc: '이감 효과 +25%(최대치는 그대로)', v: 0.25 },
  stun: { name: '천둥 부리', desc: '스턴 확률 +50%', v: 0.5 },
  boss: { name: '사냥꾼의 눈', desc: '보스·외부탐사 대장에게 주는 피해 +25%', v: 0.25 },
  explore: { name: '원정대장', desc: '외부탐사 피해 +60%', v: 0.6 },
  gold: { name: '새우 풍년', desc: '처치 새우 +40%', v: 0.4 },
  upg: { name: '단골 대장간', desc: '강화 비용 −30%', v: 0.3 },
  wisp: { name: '위습 꾸러미', desc: '지금 에픽 위습 2개 + 레어 위습 3개', v: 1 },
  warn: { name: '든든한 방파제', desc: '경고 한도 +8회', v: 8 },
  // 룰 브레이커(2026-11-16 기획안 반영: 판 짜는 방식 자체를 뒤집는 것, 대가가 큼)
  lowtide: { name: '하급 총공세', desc: '커먼~레어 피해 ×3 · 전설 이상 −15%', v: 3, rb: true },
  rush: { name: '빠른 물살', desc: '적 이속 +15% · 일반 적 체력 −15%', v: 0.15, rb: true },
  // 40웨이브: 후반을 버티게 하는 큰 것(대가가 있는 것도)
  fury: { name: '광폭화', desc: '모든 공격 피해 +35% · 경고 한도 −5회', v: 0.35 },
  chrono: { name: '시간 왜곡', desc: '일반 적 이동속도 −15%(보스 제외)', v: 0.15 },
  exec: { name: '마무리 일격', desc: '체력 8% 아래로 내려간 일반 적은 바로 쓰러짐', v: 0.08 },
  rift: { name: '균열 봉인', desc: '균열 저항이 절반으로', v: 0.5 },
  storm: { name: '연쇄 번개', desc: '공격의 15%가 가까운 적 하나에게 60% 피해로 튕김', v: 0.15 },
  legend: { name: '전설의 부름', desc: '지금 전설 위습 1개 + 에픽 위습 2개', v: 1 },
  supply: { name: '보급선', desc: '지금 모집권 6개 + 리롤권 2개', v: 1 },
  allin: { name: '모 아니면 도', desc: '모든 피해 +50% · 즉시 패배 50 → 40마리', v: 0.5, rb: true },
};
export const AUG_POOL = { 20: ['phys', 'mag', 'haste', 'shred', 'slow', 'stun', 'boss', 'explore', 'gold', 'upg', 'wisp', 'warn', 'lowtide', 'rush'], 40: ['fury', 'chrono', 'exec', 'rift', 'storm', 'legend', 'supply', 'allin'] }
// 날씨(2026-11-16 기획안 반영): 11웨이브부터 5웨이브마다 바뀜(판 시드로 정해 같이하기도 같음). 사거리·이속·방어·공속을 흔들어 조합을 바꾸게 함
export const WEATHERS = {
  calm: { name: '맑음', desc: '특별한 날씨 없음', icon: '☀' },
  fog: { name: '짙은 해무', desc: '갈매미 사거리 −15%', icon: '🌫', range: 0.85 },
  tide: { name: '만조', desc: '적 이동속도 +12%', icon: '🌊', spd: 1.12 },
  brine: { name: '소금 결계', desc: '적 방어·마방 +25%', icon: '🧂', def: 1.25 },
  wind: { name: '순풍', desc: '갈매미 공격 속도 +10%', icon: '🍃', atk: 1.1 },
};
export const WEATHER_FROM = 11, WEATHER_LEN = 5;
export function weatherAt(g, w) {
  if (!RULES.weather || w < WEATHER_FROM) return 'calm';
  const b = Math.floor((w - WEATHER_FROM) / WEATHER_LEN), r = rng(((g.seed >>> 0) ^ Math.imul(b + 101, 2654435761)) >>> 0), keys = ['fog', 'tide', 'brine', 'wind', 'calm'];
  return keys[Math.floor(r() * keys.length)];
}
export const weatherOf = (g) => { if (g._wx?.w !== g.wave) g._wx = { w: g.wave, id: weatherAt(g, g.wave) }; return WEATHERS[g._wx.id]; };;
export const augV = (g, id) => (g.augs && g.augs.includes(id) ? AUGS[id].v : 0);
export const warnMaxOf = (g) => RULES.warnMax + augV(g, 'warn') - (augV(g, 'fury') ? 5 : 0);
function offerAug(g) {
  const pool = AUG_POOL[g.wave].filter((id) => !(g.augs ?? []).includes(id));
  const r = rng(((g.seed >>> 0) ^ Math.imul(g.wave, 2654435761)) >>> 0);   // 판의 난수 흐름과 따로(증강체 제시가 다른 운에 영향을 주지 않게)
  for (let i = pool.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [pool[i], pool[j]] = [pool[j], pool[i]]; }
  g.augOffer = { wave: g.wave, opts: pool.slice(0, 3) };
  news(g, `${g.wave}웨이브 돌파! 증강체 하나를 고르세요`);
}
export function chooseAug(g, i) {
  const o = g.augOffer, id = o?.opts?.[i];
  if (!id || !AUGS[id]) return false;
  (g.augs ??= []).push(id); g.augOffer = null;
  if (id === 'wisp') addWisps(g, { EPIC: 2, RARE: 3 });
  if (id === 'legend') addWisps(g, { LEGEND: 1, EPIC: 2 });
  if (id === 'supply') { g.tickets = (g.tickets ?? 0) + 6; g.rerolls = (g.rerolls ?? 0) + 2; }
  news(g, `증강체 「${AUGS[id].name}」: ${AUGS[id].desc}`);
  return true;
}
// 난이도(2026-11-09 사용자 요청): 지금까지의 규칙 = 달인. 적 체력·방어 배수, 달인까지는 40웨이브에 전설 랜덤 위습 1개
// 2026-11-09 사용자 요청: 난이도마다 라인 몬스터 체력·보스 체력·방어력·마법방어 차이를 따로(보스·외부탐사 대장은 boss)
export const DIFFS = {
  intro: { name: '입문', line: 0.45, boss: 0.35, armor: 0.55, mr: 0.5, legend40: true },
  mid: { name: '중급', line: 0.6, boss: 0.5, armor: 0.7, mr: 0.65, legend40: true },
  high: { name: '상급', line: 0.8, boss: 0.75, armor: 0.9, mr: 0.85, legend40: true },
  master: { name: '달인', line: 1, boss: 1, armor: 1, mr: 1, legend40: true },
  artisan: { name: '장인', line: 1.35, boss: 1.55, armor: 1.25, mr: 1.35, legend40: false },   // 2026-11-10: 사용자가 예전 장인을 쉽게 깸 → 계획형 봇 기준 달인보다 한 단계 어렵게
};
export const DIFF_ORDER = ['intro', 'mid', 'high', 'master', 'artisan'];
export const diffOf = (g) => DIFFS[g.diff] ?? DIFFS.master;
// 판 성향(2026-11-16 사용자 요청): 판을 시작할 때 무작위로 갑옷형(방어↑ 마방↓) 또는 결계형(반대). 그 판 내내 모든 적에
export const BIAS = { armor: { name: '갑옷형', desc: '적 방어 ×1.4 · 마방 ×0.75 — 마법이 유리', armor: 1.4, mr: 0.75 }, mr: { name: '결계형', desc: '적 마방 ×1.4 · 방어 ×0.75 — 물리가 유리', armor: 0.75, mr: 1.4 } };
const biasMul = (g, key) => BIAS[g.bias]?.[key] ?? 1;
export const armorAt = (g, w) => Math.round(waveInfo(w).armor * diffOf(g).armor * biasMul(g, 'armor'));   // 이 판(난이도·성향)의 그 웨이브 방어력
export const mrAt = (g, w) => Math.round(waveInfo(w).mr * diffOf(g).mr * biasMul(g, 'mr'));            // 마법방어
export function newGame(seed = (Math.random() * 2 ** 32) >>> 0, mode = 'normal', diff = 'master') {
  return {
    seed, mode: MODES[mode] ? mode : 'normal', diff: DIFFS[diff] ? diff : 'master', rand: rng(seed), dmg: {}, dmgWave: {}, tickets: 0, rerolls: 0, t: 0, gold: RULES.startGold ?? 0, kills: 0, upg: Array(8).fill(0),
    wave: 0, waveT: 0, spawnLeft: 0, spawnT: 0,
    warn: 0, warnAcc: 0,
    wisps: { COMMON: RULES.startWisps, UNCOMMON: RULES.startUncommon ?? 0, RARE: 0, EPIC: 0, LEGEND: 0 },
    picks: { COMMON: 0, UNCOMMON: 0, RARE: 0, EPIC: 0, ...RULES.startPick },
    enemies: [], units: [], nextId: 1, nextUid: 1,
    goals: [], mission: null, missionDone: 0, autoStore: false, autoGrades: {}, missionDmg: [], peerDmg: null,
    over: null, reason: '', fx: [], news: [], zones: [], augs: [], augOffer: null,
    bias: rng((seed ^ 0xb1a5) >>> 0)() < 0.5 ? 'armor' : 'mr',   // 판 성향(판의 난수 흐름과 따로)
    skillAt: RULES.skillFirst ?? 30, shopN: {},
  };
}
// 이어하기(사용자 요청 2026-11-08): 판 전체를 글로 저장하고 되살린다. 규칙 판이 바뀌면(SAVE_VER) 예전 저장은 버림
// 2: 외부탐사 13장(2026-11-11). 1 저장본은 버리지 않고 옮김 — 예전 8장을 다 깬 판은 13장까지 다 깬 것으로(갑자기 마감에 걸리지 않게)
export const SAVE_VER = 2, OLD_STORY_LEN = 8;
export function saveGame(g) { return JSON.stringify({ v: SAVE_VER, g: { ...g, rand: g.rand.s, fx: [] } }); }
export function loadGame(text) {
  try {
    const o = JSON.parse(text);
    if (!o || (o.v !== SAVE_VER && o.v !== 1) || !o.g || !Array.isArray(o.g.units)) return null;
    const g = o.g, s = g.rand >>> 0;
    if (o.v === 1 && (g.missionDone ?? 0) >= OLD_STORY_LEN) { g.missionDone = REGIONS.length; g.mission = null; }
    g.rand = rng(0); g.rand.s = s;
    g.fx = []; g.dmg ??= {}; g.dmgWave ??= {}; g.autoGrades ??= {}; g.missionDmg ??= []; g.tickets ??= 0; g.diff ??= 'master'; g.zones ??= []; g.rerolls ??= 0; g.augs ??= []; g.augOffer ??= null; g.skillAt ??= 0; g.shopN ??= {};
    for (const u of g.units) u.held = false;   // 들고 있던 중에 저장된 판
    return g;
  } catch { return null; }
}
const news = (g, msg) => { g.news.push({ msg, t: g.t }); if (g.news.length > 6) g.news.shift(); };
const addWisps = (g, bag) => { for (const [k, v] of Object.entries(bag)) g.wisps[k] = (g.wisps[k] ?? 0) + v; };
export const wispText = (bag) => Object.entries(bag).map(([k, v]) => `${GRADE_NAME[k]} 위습 ${v}개`).join(' · ');
export const pickText = (bag) => Object.entries(bag).map(([k, v]) => `${GRADE_NAME[k]} 선택 위습 ${v}개`).join(' · ');
export const PICK_GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC'];
export const unitsOfGrade = (grade) => BY_GRADE[grade] ?? [];

// 판에서 싸우는 갈매미(탐사 중·창고 제외)
export const fieldUnits = (g) => g.units.filter((u) => !u.away && !u.stored);
// 시너지(2026-11-16 사용자 요청, TFT처럼): 판에 있는 에픽 이상 갈매미를 속성별로 「종류」 수로 셈. 2·4·6종이면 그 속성 피해 +10·22·36%
export const SYN_ELEMS = ['FIRE', 'WATER', 'ICE', 'LIGHTNING', 'NATURE', 'HOLY', 'DARK', 'NORMAL'];
export const SYN_NAME = { FIRE: '불', WATER: '물', ICE: '얼음', LIGHTNING: '번개', NATURE: '자연', HOLY: '빛', DARK: '어둠', NORMAL: '보통' };
export const SYN_STEPS = [2, 3, 4];
// 2026-11-16 사용자 의견(시너지가 밸런스에 영향을 줘야): 속성마다 다른 효과(TFT처럼), 단계 2·3·4종
export const SYN_FX = {
  FIRE: { v: [0.35, 0.7, 1.2], text: (v) => `불 속성 피해 +${Math.round(v * 100)}%` },
  WATER: { v: [0.12, 0.22, 0.32], text: (v) => `모든 적 이동속도 −${Math.round(v * 100)}%` },
  ICE: { v: [0.5, 1, 1.6], text: (v) => `스턴 확률 +${Math.round(v * 100)}%` },
  LIGHTNING: { v: [0.12, 0.25, 0.4], text: (v) => `모든 갈매미 공격 속도 +${Math.round(v * 100)}%` },
  NATURE: { v: [0.6, 1.2, 2], text: (v) => `독 피해 +${Math.round(v * 100)}%` },
  HOLY: { v: [0.3, 0.6, 1], text: (v) => `보스에게 주는 피해 +${Math.round(v * 100)}%` },
  DARK: { v: [0.15, 0.28, 0.42], text: (v) => `적 방어·마방 −${Math.round(v * 100)}%` },
  NORMAL: { v: [0.1, 0.2, 0.32], text: (v) => `모든 피해 +${Math.round(v * 100)}%` },
};
export function synergyOf(g) {
  if (g._syn?.t === g.t && g._syn.n === g.units.length) return g._syn.v;
  const kinds = {};
  for (const u of g.units) { if (u.away || u.stored || UNIT[u.id]?.role === SPECIAL_ROLE || tierOf(u.id) < 3) continue; const el = kit(u.id).elem; (kinds[el] ??= new Set()).add(u.id); }
  const v = {};
  for (const el of SYN_ELEMS) { const n = kinds[el]?.size ?? 0; const lv = RULES.synergy ? SYN_STEPS.filter((x) => n >= x).length : 0; v[el] = { n, lv, bonus: lv ? SYN_FX[el].v[lv - 1] : 0 }; }
  g._syn = { t: g.t, n: g.units.length, v };
  return v;
}
export const synV = (g, el) => (RULES.synergy ? synergyOf(g)[el]?.bonus ?? 0 : 0);   // 그 속성 시너지 효과 값(꺼져 있으면 0)
// 갈매미맨 폭격(2026-11-16 사용자 요청): skillCd초마다. 일반 적은 최대 체력의 skillPct, 보스는 skillBossPct만큼(방어 무시)
export const skillLeft = (g) => Math.max(0, (g.skillAt ?? 0) - g.t);
export function useSkill(g) {
  if (g.over || skillLeft(g) > 0) return false;
  for (const e of g.enemies) { if (e.hp <= 0) continue; const d = e.max * (e.boss ? RULES.skillBossPct : RULES.skillPct); e.hp -= d; e.hitT = g.t; e.hitK = 6; e.hsx = e.x; e.hsy = e.y - 40; }
  g.skillAt = g.t + RULES.skillCd; g.fx.push({ kind: 'bomb', t: g.t, n: g.enemies.length });
  return true;
}
// 새우 상점(2026-11-16 사용자 요청): 무작위 위습·리롤권. 살 때마다 값이 올라감
export const SHOP = { rare: { name: '무작위 레어 위습', base: 1500, grow: 1.18, give: { RARE: 1 } }, epic: { name: '무작위 에픽 위습', base: 6000, grow: 1.25, give: { EPIC: 1 } }, reroll: { name: '에픽 리롤권', base: 4000, grow: 1.3 } };
export const shopPrice = (g, id) => Math.round(SHOP[id].base * Math.pow(SHOP[id].grow, g.shopN?.[id] ?? 0) / 10) * 10;
export function shopBuy(g, id) {
  const it = SHOP[id]; if (!it) return { ok: false, why: '없는 물건이에요.' };
  const pr = shopPrice(g, id); if (g.gold < pr) return { ok: false, why: `새우가 모자라요(${pr} 필요).` };
  g.gold -= pr; (g.shopN ??= {})[id] = (g.shopN[id] ?? 0) + 1;
  if (it.give) addWisps(g, it.give); if (id === 'reroll') g.rerolls = (g.rerolls ?? 0) + 1;
  return { ok: true, price: pr };
}
// 창고: 판에서 빠져 공격은 안 하지만 조합 재료로는 쓰인다(사용자 요청 2026-11-08)
export const storedUnits = (g) => g.units.filter((u) => u.stored);
const nearSpawn = (g, u) => { const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 40; const p = clampField(SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.x = u.tx = p.x; u.y = u.ty = p.y; };
export function store(g, uids) {
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && !u.away && !u.stored) { u.stored = true; n++; } }
  return n;
}
export function unstore(g, uids) {
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && u.stored) { u.stored = false; nearSpawn(g, u); u.cd = 0.3; n++; } }
  return n;
}
// 커먼 자동 창고: 켜면 판의 커먼을 창고로, 이후 새로 부른 커먼도 바로 창고로
// 창고 등급 묶음(사용자 요청 2026-11-08: 커먼만 말고 등급별로 한 번에 넣기). auto=새로 부른(위습) 갈매미 자동 창고를 켤 수 있는 묶음
export const STORE_GROUPS = [
  { key: 'COMMON', name: '커먼', grades: ['COMMON'], auto: true },
  { key: 'UNCOMMON', name: '언커먼', grades: ['UNCOMMON'], auto: true },
  { key: 'RARE', name: '레어', grades: ['RARE'], auto: true },
  { key: 'EPIC', name: '에픽', grades: ['EPIC'], auto: true },
  { key: 'LEGEND', name: '전설·히든', grades: ['LEGEND', 'HIDDEN'], auto: true },
  { key: 'TOP', name: '최상위', grades: ['LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'], auto: false },
];
const groupOf = (grade) => STORE_GROUPS.find((q) => q.grades.includes(grade));
// 그 묶음의 판 갈매미를 한 번에 창고로(위치 고정한 것은 그대로 둠)
export const storeGroupUnits = (g, key) => fieldUnits(g).filter((u) => !u.lock && groupOf(UNIT[u.id].grade)?.key === key);
export function storeGroup(g, key) { const n = store(g, storeGroupUnits(g, key).map((u) => u.uid)); if (key === 'COMMON') arrangeUncommon(g); return n; }
export function setAutoGrade(g, key, on) {
  (g.autoGrades ??= {})[key] = !!on;
  if (key === 'COMMON') g.autoStore = !!on;
  return on ? storeGroup(g, key) : 0;
}
export function setAutoStore(g, on) { return setAutoGrade(g, 'COMMON', on); }   // 예전 이름(커먼 자동 창고)
const autoStoreNew = (g, u) => { const q = groupOf(UNIT[u.id].grade); if (q && (g.autoGrades?.[q.key] || (q.key === 'COMMON' && g.autoStore))) u.stored = true; };
// 같은 갈매미 모으기: 판의 같은 종류를 한 자리로
export function gather(g, uid) {
  const t = unitById(g, uid);
  if (!t || t.away || t.stored) return [];
  const list = fieldUnits(g).filter((u) => u.id === t.id);
  for (const u of list) if (!u.lock || u === t) { u.tx = t.tx; u.ty = t.ty; }   // 고정한 갈매미는 그 자리에
  return list.map((u) => u.uid);
}
export const unitById = (g, uid) => g.units.find((u) => u.uid === uid) ?? null;

// 겹침(2026-11-15 사용자 요청: 「같은 유닛은 겹치게, 나머지는 밀어내고」): 놓을 자리에 다른 종류 갈매미가 SEP 안에 있으면
// 그 자리에서 나선으로 넓혀 가며 가장 가까운 빈자리로. 같은 종류끼리는 그대로 겹칠 수 있음(모으기). 자리는 각자의 목적지(tx·ty)로 봄
// 간격 = SEP_K × (두 갈매미 크기 합). 크기는 화면에 그리는 배율과 같음(커먼 1 … 전설 1.34 … 초불영제 1.65) → 커먼끼리 42 · 전설끼리 56 · 초불영제끼리 69
export const UNIT_SIZE = [1, 1.06, 1.13, 1.22, 1.34, 1.4, 1.65, 1.65, 1.65, 1.65];
export const SEP_K = 21;
const sizeOf = (id) => UNIT_SIZE[tierOf(id)] ?? 1;
function freeSpot(g, id, x, y, selfUid = 0) {
  const others = g.units.filter((o) => !o.away && !o.stored && o.uid !== selfUid && o.id !== id), z = sizeOf(id);
  const ok = (px, py) => others.every((o) => { const d = SEP_K * (z + sizeOf(o.id)); return (o.tx - px) ** 2 + (o.ty - py) ** 2 >= d * d; });
  let p = clampField(x, y);
  if (ok(p.x, p.y)) return p;
  for (let r = 8; r <= 200; r += 8) {
    const n = Math.max(8, Math.round(r * 0.8));
    for (let i = 0; i < n; i++) { const a = (i / n) * Math.PI * 2, q = clampField(x + Math.cos(a) * r, y + Math.sin(a) * r); if (ok(q.x, q.y)) return q; }
  }
  return p;   // 빈자리가 없으면(판이 꽉 참) 그대로
}
function addUnit(g, id, x, y) {
  const p = freeSpot(g, id, x, y);
  const u = { uid: g.nextUid++, id, x: p.x, y: p.y, tx: p.x, ty: p.y, cd: 0.2 + g.rand() * 0.3, born: g.t, away: false };
  g.units.push(u);
  return u;
}

// 위습 하나로 그 등급 갈매미 1마리
export function summon(g, grade = 'COMMON') {
  if (g.over) return { ok: false, why: '' };
  if (!(g.wisps[grade] > 0)) return { ok: false, why: `${GRADE_NAME[grade]} 위습이 없어요.` };
  g.wisps[grade]--;
  const pool = BY_GRADE[grade], id = pool[Math.floor(g.rand() * pool.length)];
  const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 34, cs = grade === 'COMMON' ? commonSpot(g, id) : null;
  const u = cs ? addUnit(g, id, cs.x, cs.y) : addUnit(g, id, SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.got = true;   // 위습·선택 위습·용병모집으로 받음(리롤 가능 표시)
  autoStoreNew(g, u);
  g.fx.push({ kind: 'combine', x: u.x, y: u.y, t: g.t });
  return { ok: true, unit: u };
}
// 선택 위습: 그 등급의 원하는 갈매미 1마리
export function summonPick(g, id) {
  const grade = UNIT[id]?.grade;
  if (g.over || !grade) return { ok: false, why: '' };
  if (!(g.picks[grade] > 0)) return { ok: false, why: `${GRADE_NAME[grade]} 선택 위습이 없어요.` };
  g.picks[grade]--;
  const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 34, cs = grade === 'COMMON' ? commonSpot(g, id) : null;
  const u = cs ? addUnit(g, id, cs.x, cs.y) : addUnit(g, id, SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.got = true;   // 위습·선택 위습·용병모집으로 받음(리롤 가능 표시)
  autoStoreNew(g, u);
  g.fx.push({ kind: 'combine', x: u.x, y: u.y, t: g.t });
  return { ok: true, unit: u };
}
export function sell(g, uid) {
  const k = g.units.findIndex((u) => u.uid === uid);
  if (k < 0 || g.over) return false;
  if (UNIT[g.units[k].id].role === SPECIAL_ROLE || tierOf(g.units[k].id) >= TOP_TIER) return false;   // 초월 갈매미맨·초불영제는 팔 수 없음(2026-11-11 사용자 요청: 상위 판매 버튼 제거)
  const grade = UNIT[g.units[k].id].grade;
  g.gold += kit(g.units[k].id).sell; g.units.splice(k, 1);
  // 판매 보너스(사용자 요청 2026-11-08): 언커먼 30% 확률로 모집권 1 · 레어 모집권 1 · 에픽 모집권 1 + 커먼 위습 2
  let got = '';
  if (grade === 'UNCOMMON' && g.rand() < 0.3) { g.tickets = (g.tickets ?? 0) + 1; got = '모집권 1'; }
  else if (grade === 'RARE') { g.tickets = (g.tickets ?? 0) + 1; got = '모집권 1'; }
  else if (grade === 'EPIC') { g.tickets = (g.tickets ?? 0) + 1; addWisps(g, { COMMON: 2 }); got = '모집권 1 · 커먼 위습 2'; }
  if (got) news(g, `판매 보너스: ${got}`);
  return got || true;
}
// 용병모집: 모집권 RULES.recruitCost개로 레어·에픽 갈매미를 확률로(실패하면 없음)
export function recruit(g) {
  if (g.over) return { ok: false, why: '' };
  if ((g.tickets ?? 0) < RULES.recruitCost) return { ok: false, why: `모집권이 모자라요(${RULES.recruitCost}개 필요).` };
  g.tickets -= RULES.recruitCost;
  let r = g.rand(), grade = null;
  for (const [gr, p] of Object.entries(RULES.recruitOdds)) { if ((r -= p) < 0) { grade = gr; break; } }
  if (!grade) return { ok: true, fail: true };
  g.wisps[grade] = (g.wisps[grade] ?? 0) + 1;
  const res = summon(g, grade);
  return { ok: true, unit: res.unit, grade };
}
// 에픽 리롤(2026-11-12 사용자 요청): 보상으로 받은 에픽(위습·선택 위습·용병모집, got 표시)만 리롤권 1개로 다른 에픽으로 바꿈. 조합으로 만든 에픽은 안 됨
// 2026-11-12: 에픽 이상(전설·히든 등)도 — 같은 등급의 다른 갈매미로. 초월 갈매미맨(재료)은 제외
export const canReroll = (g, u) => !!u && !!u.got && tierOf(u.id) >= 3 && UNIT[u.id]?.role !== SPECIAL_ROLE && !u.away && (g.rerolls ?? 0) > 0 && !g.over;
export function rerollUnit(g, uid) {
  const u = unitById(g, uid);
  if (!canReroll(g, u)) return null;
  const pool = (BY_GRADE[UNIT[u.id].grade] ?? []).filter((id) => id !== u.id && UNIT[id].role !== SPECIAL_ROLE);
  if (!pool.length) return null;
  const was = u.id; u.id = pool[Math.floor(g.rand() * pool.length)]; u.cd = 0.3; u.sigN = 0; g.rerolls--;
  g.fx.push({ kind: 'combine', x: u.x, y: u.y, t: g.t });
  news(g, `리롤: ${UNIT[was].name} → ${UNIT[u.id].name}`);
  return u;
}
// 옮기기: 목표 자리로 걸어간다(걷는 중에도 공격). now=true면 바로 그 자리
export const WALK = 320;
export function moveUnit(g, uid, x, y, now = false) {
  const u = unitById(g, uid);
  if (!u || u.away || u.lock) return false;   // 고정한 갈매미는 안 움직임
  // 스냅(2026-11-15 사용자 요청: 같은 갈매미 근처로 가져가면 붙으면서 하나로): 같은 종류가 SEP_K×2×크기 안에 있으면 그 자리에 딱 겹침
  const z = sizeOf(u.id), snapR = SEP_K * 2 * z;
  let snap = null, sd = snapR * snapR;
  for (const o of g.units) { if (o === u || o.away || o.stored || o.id !== u.id) continue; const d2 = (o.tx - x) ** 2 + (o.ty - y) ** 2; if (d2 <= sd) { sd = d2; snap = o; } }
  const p = snap ? { x: snap.tx, y: snap.ty } : freeSpot(g, u.id, x, y, u.uid); u.tx = p.x; u.ty = p.y;   // 아니면 다른 종류와 겹치지 않는 가까운 빈자리로
  u.snap = snap ? snap.uid : 0;
  if (now) { u.x = p.x; u.y = p.y; }
  return true;
}
// 손으로 집어 옮기기(2026-11-16 사용자 요청): 들고 있는 동안 공격하지 않고, 내려놓으면 landTime초 동안 주저앉아 행동 불능
export function holdUnits(g, uids, on) {
  for (const uid of uids) { const u = unitById(g, uid); if (!u) continue; if (on) u.held = true; else if (u.held) { u.held = false; u.landUntil = g.t + (RULES.landTime ?? 0.8); } }
}
// 위치 고정(사용자 요청 2026-11-08): 고정하면 끌어도·모으기에도 안 움직임
export function setLock(g, uids, on) {
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && !u.away && !u.stored) { u.lock = !!on; n++; } }
  return n;
}
// 등급별 강화 칸: 0 커먼 · 1 언커먼 · 2 레어 · 3 에픽 · 4 전설 이상(전설·히든·제한·초월·불멸·영원)
export const UPG_NAME = ['커먼·언커먼', '레어', '에픽', '전설·히든', '제한', '초월', '불멸', '영원'];
export const UPG_COUNT = UPG_NAME.length;
// 등급(0 커먼 … 9 영원) → 강화 칸: 커먼·언커먼 0 · 레어 1 · 에픽 2 · 전설·히든 3 · 제한 4 · 초월 5 · 불멸 6 · 영원 7 (사용자 요청: 전설·히든 한 번에)
export const upgGroup = (tier) => [0, 0, 1, 2, 3, 3, 4, 5, 6, 7][tier];
// 강화 비용: 단계마다 upgGrowth배(곱으로 늘어 낮은 등급만 계속 강화해 버티는 것을 막음)
export const upgradeCost = (g, gi) => Math.round((1 - augV(g, 'upg')) * (RULES.upgBase[gi] * Math.pow(RULES.upgGrowth ?? 1, g.upg[gi]) + (RULES.upgGrowth ? 0 : RULES.upgStep[gi] * g.upg[gi])));   // 증강체 「단골 대장간」
export const upgMul = (g, tier) => 1 + RULES.upgPct * g.upg[upgGroup(tier)];
export const upgSpdMul = (g, tier) => 1 + (RULES.upgSpd ?? 0) * g.upg[upgGroup(tier)];
export const gapOf = (g, k) => k.gap / upgSpdMul(g, k.tier) / (1 + augV(g, 'haste')) / (weatherOf(g).atk ?? 1) / (1 + synV(g, 'LIGHTNING'));   // 시너지 「번개」   // 날씨 「순풍」   // 증강체 「날갯짓 가속」   // 강화하면 공격 간격이 짧아짐
export const upgMaxed = (g, gi) => g.upg[gi] >= RULES.upgMax;
export function upgrade(g, gi) {
  const cost = upgradeCost(g, gi);
  if (g.over) return { ok: false, why: '' };
  if (modeOf(g).noUpg) return { ok: false, why: '강화 금지 모드예요.' };
  if (upgMaxed(g, gi)) return { ok: false, why: '이미 최대 단계(풀강)예요.' };
  if (g.gold < cost) return { ok: false, why: `새우가 모자라요(${cost} 필요).` };
  g.gold -= cost; g.upg[gi]++;
  return { ok: true };
}

// 조합: 판(탐사 중 제외)에 재료가 다 있으면 쓸 갈매미 uid 목록(keep을 먼저)
export function recipeUnits(g, r, keepUid = 0) {
  const avail = [...g.units].sort((a, b) => (a.away - b.away)), used = new Set(), out = [], need = [...r.in];   // 판·창고·스토리(외부탐사) 것 모두 재료(판 것을 먼저 씀)
  if (keepUid) {
    const ku = avail.find((u) => u.uid === keepUid);
    const k = ku ? need.indexOf(ku.id) : -1;
    if (k < 0) return null;
    need.splice(k, 1); used.add(ku.uid); out.push(ku.uid);
  }
  for (const id of need) {
    const f = avail.find((u) => !used.has(u.uid) && u.id === id);
    if (!f) return null;
    used.add(f.uid); out.push(f.uid);
  }
  return out;
}
// 상위(제한·초월·불멸·영원)는 판·창고·탐사를 합쳐 RULES.topCap마리까지(사용자 요청 2026-11-08: 상위는 한두 개, 조합의 중심은 전설·히든)
export const TOP_TIER = 6, TOP_POWER_TIER = 7;
export const SPECIAL_ROLE = '초월 재료', TRANSCEND_KEY = 'u81';   // 초월 갈매미맨   // 초불영제(제한~영원): 능력치는 모두 7단계 값으로
export const topCount = (g) => g.units.filter((u) => tierOf(u.id) >= TOP_TIER).length;
export const topBlocked = (g, r) => RULES.topCap > 0 && tierOf(r.out) >= TOP_TIER && topCount(g) >= RULES.topCap;
export function combine(g, r, keepUid = 0) {
  if (g.over || topBlocked(g, r)) return null;
  const uids = recipeUnits(g, r, keepUid);
  if (!uids) return null;
  // 조합 결과는 판 가운데(소환되는 곳)에 나온다(사용자 요청: 어디 생겼는지 헷갈리지 않게)
  const a = g.rand() * Math.PI * 2, rr = 10 + g.rand() * 30, x = SPAWN.x + Math.cos(a) * rr, y = SPAWN.y + Math.sin(a) * rr;
  g.units = g.units.filter((u) => !uids.includes(u.uid));
  const nu = addUnit(g, r.out, x, y);
  g.fx.push({ kind: 'combine', x, y, t: g.t });
  return nu;
}
export const recipesUsing = (id) => RECIPES.filter((r) => r.in.includes(id));
export const recipeFor = (id) => RECIPES.find((r) => r.out === id) ?? null;
export function countOnField(g) {
  const c = {};
  for (const u of g.units) c[u.id] = (c[u.id] ?? 0) + 1;   // 창고·스토리 것 포함(조합 재료)
  return c;
}
export const GOAL_MAX = 3;
export function toggleGoal(g, out) {
  const k = g.goals.indexOf(out);
  if (k >= 0) { g.goals.splice(k, 1); return false; }
  if (g.goals.length >= GOAL_MAX) g.goals.shift();
  g.goals.push(out);
  return true;
}

// ── 외부탐사 ──
// 고르는 보상(6장): 선택지 글자와 받기
export const choiceText = (c) => (c.unit ? UNIT[c.unit].name : c.wisps ? wispText(c.wisps) : c.picks ? pickText(c.picks) : '');
export function chooseReward(g, i) {
  const r = g.choice && REGIONS[g.choice.k], c = r?.choice?.[i];
  if (!c) return false;
  if (c.unit) { const nu = addUnit(g, c.unit, SPAWN.x, SPAWN.y); g.fx.push({ kind: 'combine', x: nu.x, y: nu.y, t: g.t }); }
  if (c.wisps) addWisps(g, c.wisps);
  if (c.picks) for (const [k, v] of Object.entries(c.picks)) g.picks[k] = (g.picks[k] ?? 0) + v;
  g.choice = null;
  news(g, `${r.ch}장 보상: ${choiceText(c)}을(를) 받았어요`);
  return true;
}
export const missionOpen = (g) => g.missionDone < REGIONS.length && !modeOf(g).noMission;
function ensureMission(g) {
  if (!missionOpen(g)) return null;
  if (!g.mission || g.mission.k !== g.missionDone) {
    const gd = regionGuard(g.missionDone);
    const mhp = Math.round(gd.hp * (modeOf(g).hpMul ?? 1) * diffOf(g).boss);
    g.mission = { k: g.missionDone, hp: mhp, max: mhp, armor: Math.round(gd.armor * diffOf(g).armor), mr: Math.round(gd.mr * diffOf(g).mr), boss: true, shred: 0, shredUntil: 0, mshred: 0, mshredUntil: 0, amp: 0, ampUntil: 0, x: 0, y: 0 };
  }
  return g.mission;
}
export const missionTarget = (g) => ensureMission(g);
// 같이하기(사용자 요청 2026-11-08: 외부탐사는 둘이서 같이): 두 사람이 같은 장의 대장을 함께 친다.
// 장마다 내가 준 피해(missionDmg)를 친구에게 보내고, 친구가 준 피해(peerDmg)를 받아 대장 체력 = 최대 − 내 피해 − 친구 피해.
// 장별로 쌓아 두므로 한쪽이 먼저 깨도 마지막 피해가 다른 쪽에 전해져 둘 다 같은 장을 깬다. 보상은 각자 전부 받는다.
export function setPeerMission(g, arr) { g.peerDmg = Array.isArray(arr) ? arr.map((v) => Math.max(0, Number(v) || 0)) : g.peerDmg; }
export const missionShare = (g) => { const k = g.missionDone; return { mine: g.missionDmg[k] ?? 0, peer: g.peerDmg?.[k] ?? 0 }; };
export function sendAway(g, uids) {
  if (!ensureMission(g)) return 0;
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && !u.away && !u.stored && UNIT[u.id].role !== SPECIAL_ROLE) { u.away = true; u.cd = 0.3; n++; } }
  return n;
}
export function recall(g, uids = null) {
  let n = 0;
  for (const u of g.units) if (u.away && (!uids || uids.includes(u.uid))) {
    u.away = false; const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 40;
    const p = clampField(SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.x = u.tx = p.x; u.y = u.ty = p.y; n++;
  }
  return n;
}
export const awayUnits = (g) => g.units.filter((u) => u.away);

// ── 싸움 ──
// 방깍·마방깍은 갈매미마다 따로 걸려 모두 더한다(사용자 요청 2026-11-08: 특정 수치만큼 깎는 조합). 같은 갈매미는 시간만 늘어남
function effSum(e, key, now) {
  const m = e[key]; if (!m) return 0;
  let s = 0;
  for (const id in m) { if (now >= m[id].until) delete m[id]; else s += m[id].v; }
  return s;
}
export const shredNow = (e, now) => ({ shred: effSum(e, 'shreds', now), mshred: effSum(e, 'mshreds', now) });
function hurt(g, e, raw, k, uid) {
  if (e.hp <= 0) return 0;
  if (!k.pierce) { e.hitT = g.t; e.hitK = k.tier ?? 0; }   // 화면: 맞은 적이 번쩍·밀려남(규칙에는 안 쓰임). 독(지속 피해)은 매 순간 번쩍이지 않게 뺌
  const now = g.t;
  let m;
  const sh = 1 + augV(g, 'shred');   // 증강체 「부식」: 방깍·마방깍 효과
  const wd = (weatherOf(g).def ?? 1) * (1 - synV(g, 'DARK'));   // 날씨 「소금 결계」·시너지 「어둠」: 방어·마방 ×
  if (k.pierce) m = upgMul(g, k.tier);   // 독: 방어·마방 무시
  else if (k.type === 'phys') m = defMul(e.armor * wd - effSum(e, 'shreds', now) * sh) * upgMul(g, k.tier);
  else m = defMul(e.mr * wd - (RULES.simNoMshred ? 0 : effSum(e, 'mshreds', now) * sh)) * (1 + (now < e.ampUntil ? e.amp : 0)) * upgMul(g, k.tier);
  if (g.augs?.length) { m *= 1 + augV(g, k.type === 'phys' ? 'phys' : 'mag') + augV(g, 'fury'); if (e.boss) m *= 1 + augV(g, 'boss'); }   // 증강체: 물리·마법·광폭화·사냥꾼의 눈   // simNoMshred: 밸런스 시험용(마방깍 없이 보스를 잡는지)
  if (e.shieldUntil > now) m *= 0.3;   // 보스 방어막
  if (g.units) { if (k.elem === 'FIRE') m *= 1 + synV(g, 'FIRE'); m *= 1 + synV(g, 'NORMAL'); if (e.boss) m *= 1 + synV(g, 'HOLY'); if (k.pierce && k.poison) m *= 1 + synV(g, 'NATURE'); }   // 시너지: 불·보통·빛·자연
  if (augV(g, 'lowtide')) m *= k.tier <= 2 ? AUGS.lowtide.v : k.tier >= 4 ? 0.85 : 1;   // 룰 브레이커 「하급 총공세」
  if (augV(g, 'allin')) m *= 1 + AUGS.allin.v;   // 룰 브레이커 「모 아니면 도」
  if (e.boss) { if (k.holy) m *= k.holy; m *= k.bossMul ?? 1; }   // 보스·외부탐사 대장: 단일 공격형은 더 세게, 라인 정리형은 약하게
  if (e.rift && k.tier < TOP_TIER) m *= 1 - e.rift * (augV(g, 'rift') ? 0.5 : 1);   // 증강체 「균열 봉인」: 저항 절반   // 균열 저항: 전설·히든 이하 피해만 줄어듦
  const d = Math.min(Math.max(0, e.hp), raw * m);
  e.hp -= raw * m; e.by = uid;
  if (e.hp > 0 && !e.boss && e.speed && augV(g, 'exec') && e.hp <= e.max * AUGS.exec.v) e.hp = 0;   // 증강체 「마무리 일격」
  if (uid && g.dmg) { g.dmg[uid] = (g.dmg[uid] ?? 0) + d; g.dmgWave[uid] = (g.dmgWave[uid] ?? 0) + d; }
  return d;
}
function debuff(g, e, k, uid = 0, aura = false) {   // aura: 이감·마방깍은 판에서는 범위 효과(applyAuras)라 때릴 때 걸지 않음
  const now = g.t;
  if (k.shred) (e.shreds ??= {})[uid] = { v: k.shred, until: now + 3 };
  if (k.mshred && !aura) (e.mshreds ??= {})[uid] = { v: k.mshred, until: now + 3 };
  if (k.amp) { e.amp = now < e.ampUntil ? Math.max(e.amp, k.amp) : k.amp; e.ampUntil = now + 3; }
  if (k.slow && e.speed && !aura) {   // 같은 갈매미는 다시 맞혀도 시간만 늘고, 다른 갈매미 것은 더해진다
    (e.slows ??= {})[uid] = { v: k.slow, until: now + 1.5 };
    e.slowUntil = now + 1.5;
  }
  const st = k.stunTime ?? 1;
  const stx = (1 + augV(g, 'stun')) * (1 + synV(g, 'ICE'));   // 증강체 「천둥 부리」·시너지 「얼음」
  if (k.stun && e.speed && g.rand() < k.stun * stx) e.stunUntil = Math.max(e.stunUntil, now + st * (e.boss ? BOSS_STUN : 1));
  if (k.astun && e.speed && g.rand() < k.astun * stx) for (const o of g.enemies) if (o.hp > 0 && Math.hypot(o.x - e.x, o.y - e.y) <= 70) o.stunUntil = Math.max(o.stunUntil, now + st * (o.boss ? BOSS_STUN : 1));
  // 독(2026-11-16 사용자 요청: 독 조합으로도 깰 수 있게): 다른 갈매미의 독은 poisonStack겹까지 더해지고(같은 갈매미는 시간만 늘어남), 방어·마방을 무시함
  if (k.poison) { (e.poisons ??= {})[uid] = { v: k.dmg * k.poison, k, until: now + 2 }; e.poisonUntil = Math.max(e.poisonUntil, now + 2); }
}
export const hurtForTest = (g, e, raw, k) => hurt(g, e, raw, k, 0);
export const debuffForTest = (g, e, k, uid) => debuff(g, e, k, uid);   // 시험용
// 이감·마방깍(2026-11-09 사용자 요청): 때린 적만이 아니라 그 갈매미 사거리 안의 적 모두에게 걸림. 갈매미마다 따로 더해지고, 벗어나면 곧 풀림
const AURA_HOLD = 0.3;
function applyAuras(g) {
  const now = g.t;
  for (const u of g.units) {
    if (u.away || u.stored) continue;
    const k = kit(u.id);
    if (!k.slow && !k.mshred) continue;
    const r2 = (k.range * (weatherOf(g).range ?? 1)) ** 2;
    for (const e of g.enemies) {
      if (e.hp <= 0) continue;
      const dx = e.x - u.x, dy = e.y - u.y;
      if (dx * dx + dy * dy > r2) continue;
      if (k.mshred) (e.mshreds ??= {})[u.uid] = { v: k.mshred, until: now + AURA_HOLD };
      if (k.slow && e.speed) { (e.slows ??= {})[u.uid] = { v: k.slow, until: now + AURA_HOLD }; e.slowUntil = now + AURA_HOLD; }
    }
  }
}
function attack(g, u, k) {
  // 공격 대상: 사거리 안에서 가장 가까운 적 먼저(사용자 요청 2026-11-08)
  let best = null, bestD = Infinity;
  const rgw = k.range * (weatherOf(g).range ?? 1), rg2 = rgw * rgw;   // 날씨 「짙은 해무」: 사거리 ×
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    const dx = e.x - u.x, dy = e.y - u.y, d2 = dx * dx + dy * dy;
    if (d2 > rg2) continue;
    // 2026-11-15 사용자 요청: 보스와 일반 적이 같이 나오면 사거리 안 보스를 먼저 때림.
    // 2026-11-16(A안): 라인 정리형(범위·스플래시·연쇄)은 가까운 적부터 — 보스에 붙잡혀 일반 적이 넘쳐 즉시 패배하던 것
    if (!k.line) {
      if (best?.boss && !e.boss) continue;
      if (d2 >= bestD && !(e.boss && !best?.boss)) continue;
    } else if (d2 >= bestD) continue;
    best = e; bestD = d2;
  }
  if (!best) return false;
  const hits = [best];
  if (k.area || k.splash) {
    const r = k.area ?? k.splashR ?? 50;
    for (const e of g.enemies) if (e !== best && e.hp > 0 && Math.hypot(e.x - best.x, e.y - best.y) <= r) hits.push(e);
    g.fx.push({ kind: 'boom', x: best.x, y: best.y, r, area: !!k.area, type: k.type, elem: k.elem, t: g.t });   // 화면에 범위 표시
  }
  let d0 = 0;
  for (const e of hits) { e.hsx = u.x; e.hsy = u.y; }   // 화면: 때린 갈매미 반대쪽으로 밀려나는 방향(규칙에는 안 쓰임)
  hits.forEach((e, i) => { const d = hurt(g, e, i === 0 || k.area ? k.dmg : k.dmg * k.splash, k, u.uid); if (i === 0) d0 = d; debuff(g, e, k, u.uid, true); });
  // 2026-11-15 사용자 요청(스턴 20%·1초 유닛 한 마리 + 이감 100%면 한 마리도 스턴 없이 못 지나가게): 스턴은 이감처럼 사거리 안 적 모두가 공격마다 굴림
  if (k.stun) { const st = (k.stunTime ?? 1), p = k.stun * (1 + augV(g, 'stun')) * (1 + synV(g, 'ICE')), r2 = rg2;
    for (const e of g.enemies) { if (e.hp <= 0 || !e.speed || hits.includes(e) || (e.x - u.x) ** 2 + (e.y - u.y) ** 2 > r2) continue; if (g.rand() < p) e.stunUntil = Math.max(e.stunUntil, g.t + st * (e.boss ? BOSS_STUN : 1)); } }
  // 화면용(2026-11-09 공격 연출): 보스에게 전설 이상이 준 피해 숫자(동시에 4개까지)
  if (best.boss && k.tier >= 4 && d0 > 0 && g.fx.filter((f) => f.kind === 'num').length < 4) g.fx.push({ kind: 'num', x: best.x, y: best.y, v: d0, tier: k.tier, n: (g.numSeq = ((g.numSeq ?? 0) + 1) % 1000), t: g.t });
  if (k.chain) {
    const near = g.enemies.filter((e) => e !== best && e.hp > 0).map((e) => [e, Math.hypot(e.x - best.x, e.y - best.y)]).filter(([, d]) => d <= 90).sort((a, b) => a[1] - b[1]).slice(0, k.chain);
    for (const [e] of near) { hurt(g, e, k.dmg * 0.5, k, u.uid); g.fx.push({ kind: 'zap', x0: best.x, y0: best.y, x1: e.x, y1: e.y, t: g.t }); }
  }
  if (augV(g, 'storm') && g.rand() < AUGS.storm.v) {   // 증강체 「연쇄 번개」: 가까운 다른 적 하나에게 60%
    let o = null, od = 90 * 90; for (const e of g.enemies) { if (e === best || e.hp <= 0) continue; const d2 = (e.x - best.x) ** 2 + (e.y - best.y) ** 2; if (d2 < od) { o = e; od = d2; } }
    if (o) { hurt(g, o, k.dmg * 0.6, k, u.uid); g.fx.push({ kind: 'zap', x0: best.x, y0: best.y, x1: o.x, y1: o.y, t: g.t }); }
  }
  const sg = SIGNATURE[u.id];
  if (sg) { u.sigN = (u.sigN ?? 0) + 1; if (u.sigN >= sg.every) { u.sigN = 0; signature(g, u, k, best, sg); } }
  // shot: 화면이 갈매미 동작(근접은 돌진·원거리는 반동)과 속성별 투사체를 그림. near = 근접(사거리 150 이하·범위 공격)
  g.fx.push({ kind: 'shot', x0: u.x, y0: u.y, x1: best.x, y1: best.y, type: k.type, job: k.job, elem: k.elem, uid: u.uid, tier: k.tier, near: k.range <= 150 || !!k.area, boss: !!best.boss, t: g.t });
  return true;
}

export const storyLeft = (g) => !modeOf(g).noMission && g.missionDone < REGIONS.length;
function startWave(g) {
  if (g.wave >= RULES.waves) return;
  const left = RULES.storyDeadline - g.wave;   // 이번에 시작하는 웨이브부터 마감까지 남은 웨이브(이번 포함)
  if (storyLeft(g) && (left === 10 || left === 5)) news(g, `스토리 ${REGIONS.length}장 마감까지 ${left}웨이브! (${RULES.storyDeadline}웨이브까지 못 깨면 패배)`);
  if (g.wave > 0) { addWisps(g, { COMMON: RULES.wispPerWave }); if (RULES.wispPerWaveUp) addWisps(g, RULES.wispPerWaveUp); }
  g.wave++; g.waveT = 0; g.dmgWave = {};
  if (g.wave === 40 && diffOf(g).legend40) { addWisps(g, { LEGEND: 1 }); news(g, `40웨이브! ${diffOf(g).name} 보상: 전설 위습 1개`); }
  if (g.wave === (RULES.riftFrom ?? 999) + 1) news(g, `균열 저항 시작! 이제 적이 전설·히든 이하 공격을 웨이브마다 ${Math.round((RULES.riftPer ?? 0) * 100)}%씩 덜 받아요(최대 ${Math.round((RULES.riftMax ?? 0) * 100)}%). 초불영제 공격은 그대로`);
  const wi = waveInfo(g.wave);
  g.spawnLeft = (wi.boss ? 1 : 0) + RULES.perWave; g.bossLeft = wi.boss ? 1 : 0; g.spawnT = 0;   // 2026-11-15 사용자 요청: 보스 웨이브에도 일반 적이 똑같이 나옴(보스 먼저)
}
export const riftAt = (w) => (w > (RULES.riftFrom ?? 999) ? Math.min(RULES.riftMax ?? 0, (RULES.riftPer ?? 0) * (w - RULES.riftFrom)) : 0);   // 그 웨이브 라인 적의 균열 저항
// 보스 행동(2026-11-16 사용자 요청): 보스마다 셋 중 하나 — 방어막(10초마다 3초간 받는 피해 30%) · 부하 소환(8초마다 일반 적 2) · 돌진(7초마다 1.5초간 속도 ×2.5)
export const BOSS_PAT = ['shield', 'summon', 'dash'];
export const BOSS_PAT_INFO = { shield: { name: '방어막', desc: '10초마다 3초 동안 받는 피해 30%' }, summon: { name: '부하 소환', desc: '8초마다 일반 적 2마리를 부름' }, dash: { name: '돌진', desc: '7초마다 1.5초 동안 빠르게 달림' } };
function bossTick(g, e) {
  if (!e.bpat || g.t < (e.bt ?? 0) || e.hp <= 0) return;
  if (e.bpat === 'shield') { e.shieldUntil = g.t + 3; e.bt = g.t + 10; }
  else if (e.bpat === 'dash') { e.dashUntil = g.t + 1.5; e.bt = g.t + 7; }
  else if (e.bpat === 'summon') {
    e.bt = g.t + 8;
    for (let i = 0; i < 2; i++) { spawn(g, true); const m = g.enemies[g.enemies.length - 1]; m.s = Math.max(0, e.s - 18 * (i + 1)); const p = pathPos(m.s); m.x = p.x; m.y = p.y; }
    g.fx.push({ kind: 'summon', x: e.x, y: e.y, t: g.t });
  }
}
function spawn(g, minion = false) {
  const w0 = waveInfo(g.wave), asBoss = !minion && w0.boss && (g.bossLeft ?? 1) > 0;   // 예전 저장(bossLeft 없음)은 남은 1마리가 보스
  if (w0.boss && !minion) g.bossLeft = asBoss ? 0 : (g.bossLeft ?? 0);
  const wi = asBoss ? w0 : waveInfo(g.wave, true);
  const df = diffOf(g), hp = wi.hp * (RULES.hpX ?? 1) * (modeOf(g).hpMul ?? 1) * (wi.boss ? df.boss : df.line) * (!wi.boss && augV(g, 'rush') ? 1 - AUGS.rush.v : 1);   // 「빠른 물살」: 일반 적 체력 −15%
  const rift = !wi.boss && g.wave > (RULES.riftFrom ?? 999) ? riftAt(g.wave) : 0;
  g.enemies.push({ id: g.nextId++, hp, max: hp, s: 0, speed: wi.speed, boss: wi.boss, look: wi.look, gold: wi.gold, armor: Math.round(wi.armor * df.armor * biasMul(g, 'armor')), mr: Math.round(wi.mr * df.mr * biasMul(g, 'mr')), rift, ...(wi.boss ? { bpat: BOSS_PAT[(Math.round(g.wave / RULES.bossEvery) - 1) % BOSS_PAT.length], bt: g.t + 6 } : {}),
    shred: 0, shredUntil: 0, mshred: 0, mshredUntil: 0, amp: 0, ampUntil: 0, slow: 0, slowUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: LOOP.x0, y: LOOP.y0, by: 0 });
}
function rollWisp(g, skill) {
  if (g.rand() >= skill.p) return null;
  let r = g.rand();
  for (const [grade, p] of Object.entries(skill.table)) { if ((r -= p) < 0) return grade; }
  return Object.keys(skill.table).pop();
}

// 한 걸음(dt초)
export function step(g, dt) {
  if (g.over) return;
  g.t += dt;
  if (g.wave === 0) startWave(g);
  const wi = waveInfo(g.wave);
  if (g.spawnLeft > 0) {
    g.spawnT -= dt;
    if (g.spawnT <= 0) { spawn(g); g.spawnLeft--; g.spawnT = RULES.spawnGap; }
  }
  applyAuras(g);
  const wsp = (weatherOf(g).spd ?? 1) * (1 + augV(g, 'rush')) * (1 - synV(g, 'WATER'));   // 시너지 「물」   // 날씨 「만조」 · 룰 브레이커 「빠른 물살」
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    e.slow = 0;
    if (e.slows) { for (const id in e.slows) { const sl = e.slows[id]; if (g.t >= sl.until) delete e.slows[id]; else e.slow += sl.v; } }
    e.slow = Math.min(SLOW_MAX, e.slow * (1 + augV(g, 'slow')));   // 합계(화면에 보여 줌). 증강체 「끈끈한 바다」
    if (e.boss) bossTick(g, e);
    const v = g.t < e.stunUntil ? 0 : e.speed * slowMul(e.slow) * (e.boss ? (g.t < (e.dashUntil ?? 0) ? 2.5 : 1) : 1 - augV(g, 'chrono')) * wsp;   // 증강체 「시간 왜곡」
    e.s += v * dt;
    const p = pathPos(e.s); e.x = p.x; e.y = p.y;
    if (e.poisons && g.t < e.poisonUntil) {   // 독: 센 것부터 poisonStack겹(방어 무시)
      const list = []; for (const id in e.poisons) { const ps = e.poisons[id]; if (g.t >= ps.until) delete e.poisons[id]; else list.push([id, ps]); }
      list.sort((a, b) => b[1].v - a[1].v); e.poison = 0;
      for (const [id, ps] of list.slice(0, RULES.poisonStack ?? 1)) { e.poison += ps.v; hurt(g, e, ps.v * dt, { ...ps.k, pierce: true }, +id); }
    }
  }
  const byUid = new Map(g.units.map((u) => [u.uid, u]));
  if (g.peerDmg) ensureMission(g);   // 같이하기: 내가 아무도 안 보냈어도 친구 피해로 깰 수 있게
  const ms = g.mission && missionOpen(g) && g.mission.k === g.missionDone ? g.mission : null;
  const buffs = buffsOf(g);   // 주변 버프·패널티(이번 걸음)
  for (const u of g.units) {
    if (u.stored) continue;   // 창고: 싸우지 않음
    if (!u.away && (u.x !== u.tx || u.y !== u.ty)) {
      const dx = u.tx - u.x, dy = u.ty - u.y, d = Math.hypot(dx, dy), m = WALK * dt;
      if (d <= m) { u.x = u.tx; u.y = u.ty; } else { u.x += dx / d * m; u.y += dy / d * m; }
    }
    if (u.held || g.t < (u.landUntil ?? 0)) continue;   // 들고 있는 중·내려놓고 주저앉은 중: 행동 불능(공격 대기 시간도 멈춤)
    u.cd -= dt;
    if (u.cd > 0) continue;
    if (UNIT[u.id]?.role === SPECIAL_ROLE) { u.cd = 5; continue; }   // 초월 갈매미맨은 공격하지 않음
    const k = kit(u.id);
    if (u.away) {
      if (!ms) { u.cd = 0.2; continue; }
      const before = ms.hp;
      const ek = k.exDmg && (k.bossMul ?? 1) < 1 ? { ...k, bossMul: 1 } : k;   // 초불영제: 라인형 대장 감점 없음
      hurt(g, ms, (k.exDmg || k.dmg) * (k.area ? 1.2 : 1) * (k.explore || 1) * (1 + augV(g, 'explore')), ek, u.uid); debuff(g, ms, k, u.uid);   // 증강체 「원정대장」(검토: 처음에 주석 뒤로 밀려 debuff가 빠졌던 것 고침)
      g.missionDmg[ms.k] = (g.missionDmg[ms.k] ?? 0) + Math.max(0, before - ms.hp);
      u.cd = Math.max(-0.05, u.cd) + gapOf(g, k); continue;
    }
    const bf = buffs.get(u.uid), kk = bf && bf[0] !== 1 ? { ...k, dmg: k.dmg * bf[0] } : k;   // 주변 버프·패널티
    u.cd = attack(g, u, kk) ? Math.max(-0.05, u.cd) + gapOf(g, k) / (bf ? bf[1] : 1) : 0.1;   // 남은 시간을 이어 써서 0.05초 단위 반올림으로 느려지지 않게
  }
  tickZones(g);
  if (ms) ms.hp = ms.max - (g.missionDmg[ms.k] ?? 0) - (g.peerDmg?.[ms.k] ?? 0);
  // 처치
  let bossDown = false;
  g.enemies = g.enemies.filter((e) => {
    if (e.hp > 0) return true;
    g.gold += Math.round(e.gold * (1 + augV(g, 'gold'))); g.kills++;   // 증강체 「새우 풍년」
    if (e.boss) bossDown = true;
    g.fx.push({ kind: 'kill', x: e.x, y: e.y, uid: e.by, look: e.look, boss: !!e.boss, sx: e.hsx, t: g.t });   // 화면: 쓰러짐(모든 적) + 꾸민 갈매미면 처치 효과(꾸미기 2026-11-11)
    const killer = byUid.get(e.by), sk = killer ? WISP_SKILL[killer.id] : null;
    if (sk) { const w = rollWisp(g, sk); if (w) { addWisps(g, { [w]: 1 }); news(g, `${UNIT[killer.id].name}이(가) ${GRADE_NAME[w]} 위습을 찾았어요!`); g.fx.push({ kind: 'wisp', x: e.x, y: e.y, grade: w, t: g.t }); } }
    return false;
  });
  // 외부탐사 대장 쓰러뜨림
  if (ms && ms.hp <= 0) {
    const r = REGIONS[ms.k];
    addWisps(g, r.reward);
    if (r.gold) g.gold += r.gold;
    if (r.ticket) g.tickets = (g.tickets ?? 0) + r.ticket;
    if (r.pick) for (const [k, v] of Object.entries(r.pick)) g.picks[k] = (g.picks[k] ?? 0) + v;
    if (r.choice) g.choice = { k: ms.k };   // 고르는 보상: 화면에서 chooseReward로
    else if (r.unit) { const nu = addUnit(g, r.unit, SPAWN.x, SPAWN.y); g.fx.push({ kind: 'combine', x: nu.x, y: nu.y, t: g.t }); }
    news(g, `${g.peerDmg ? '친구와 함께 ' : ''}외부탐사 「${r.name}」 성공! ${wispText(r.reward)}${r.pick ? ` · ${pickText(r.pick)}` : ''}${r.ticket ? ` · 모집권 ${r.ticket}` : ''}${r.gold ? ` · 새우 ${r.gold}` : ''}${r.choice ? ' · 보상 하나를 골라요' : r.unit ? ` · ${UNIT[r.unit].name}` : ''}${ms.k + 1 < REGIONS.length ? ` · ${ms.k + 2}장이 열렸어요` : ''}`);
    if (ms.k + 1 >= REGIONS.length) recall(g);   // 2026-11-10 사용자 요청: 보낸 갈매미는 다음 장도 계속 탐사, 마지막 장(13장)을 깨면 자동으로 돌아옴
    g.missionDone++; g.mission = null;
  }
  g.fx = g.fx.filter((f) => g.t - f.t < 0.8);
  // 경고: 70마리를 넘은 동안 1초마다 1회(누적)
  if (hardLimitOn(g) && g.enemies.length > hardLimitOf(g)) { g.over = 'lose'; g.reason = `${RULES.hardLimitFrom}웨이브부터는 길 위의 적이 ${hardLimitOf(g)}마리를 넘으면 바로 패배해요.`; return; }
  if (g.enemies.length > RULES.limit) {
    g.warnAcc += dt;
    while (g.warnAcc >= 1) { g.warnAcc -= 1; g.warn++; }
    if (g.warn > warnMaxOf(g)) { g.over = 'lose'; g.reason = `경고가 ${warnMaxOf(g)}회를 넘었어요(길 위의 적 ${RULES.limit}마리 초과).`; return; }
  } else g.warnAcc = 0;
  // 보스·웨이브 진행
  if (wi.boss && bossDown) {
    if (g.wave >= RULES.waves) { g.finaleAt ??= g.t; g.bossDone = g.wave; news(g, `${RULES.waves}웨이브 보스 처치! 남은 적을 ${RULES.finaleLen}초 안에 모두 잡으면 완벽 클리어`); }
    else {
    const rw = RULES.bossReward[g.wave];
    if (rw) addWisps(g, rw);
    const rp = { ...(RULES.bossPickEach ?? {}) };
    for (const [k, v] of Object.entries(RULES.bossPick[g.wave] ?? {})) rp[k] = (rp[k] ?? 0) + v;
    for (const [k, v] of Object.entries(rp)) g.picks[k] = (g.picks[k] ?? 0) + v;
    const tk = g.wave >= (RULES.bossTicketLateFrom ?? 999) ? (RULES.bossTicketLate ?? RULES.bossTicket) : RULES.bossTicket && g.wave % (RULES.bossTicketEvery ?? RULES.bossEvery) === 0 ? RULES.bossTicket : 0;
    if (tk) g.tickets = (g.tickets ?? 0) + tk;
    const rr = RULES.bossRerollEvery && g.wave % RULES.bossRerollEvery === 0 ? 1 : 0;
    if (rr) g.rerolls = (g.rerolls ?? 0) + rr;
    if (AUG_WAVES.includes(g.wave) && !g.augOffer) offerAug(g);   // 증강체 고르기(화면에서 chooseAug)
    news(g, `보스 처치! ${[rw && wispText(rw), Object.keys(rp).length && pickText(rp), tk && `모집권 ${tk}`, rr && `리롤권 ${rr}`].filter(Boolean).join(' · ')}`);
    g.bossDone = g.wave;
    }
  }
  // 마지막 보스 뒤: 남은 적(아직 안 나온 것 포함)을 다 잡거나 finaleLen초가 지나면 끝. 남은 수를 기록(0마리면 완벽 클리어)
  if (g.finaleAt != null) {
    const left = g.enemies.length + (g.spawnLeft ?? 0);
    if (!left || g.t - g.finaleAt >= RULES.finaleLen) {
      g.leftover = left; g.over = 'win';
      g.reason = left ? `${RULES.waves}웨이브 보스까지 막았어요! 남은 적 ${left}마리` : `완벽 클리어! ${RULES.waves}웨이브 보스와 남은 적까지 모두 잡았어요`;
    }
    return;
  }
  g.waveT += dt;
  if (g.waveT >= baseLenOf(g.wave) && wi.boss && g.bossDone !== g.wave) { g.over = 'lose'; g.reason = `${g.wave}웨이브 보스를 라운드 안에 잡지 못했어요.`; return; }
  if (g.waveT >= waveLenOf(g.wave)) {
    if (g.wave >= RULES.waves) return;
    if (g.wave === RULES.storyDeadline && storyLeft(g)) { g.over = 'lose'; g.reason = `스토리 ${REGIONS.length}장을 ${RULES.storyDeadline}웨이브까지 끝내지 못했어요.`; return; }
    startWave(g);
  }
}
// 10·20…웨이브는 끝나고 쉬는 시간만큼 길다(적은 더 나오지 않음)
export const restWave = (w) => !!RULES.restEvery && w % RULES.restEvery === 0 && w < RULES.waves;
export const baseLenOf = (w) => (w % RULES.bossEvery === 0 && RULES.bossLen ? RULES.bossLen : RULES.waveLen);   // 보스 웨이브는 bossLen초
export const waveLenOf = (w) => baseLenOf(w) + (restWave(w) ? RULES.restLen : 0);
export const resting = (g) => restWave(g.wave) && g.waveT >= baseLenOf(g.wave);
export const hardLimitOn = (g) => g.wave >= (RULES.hardLimitFrom ?? 999);
export const hardLimitOf = (g) => RULES.hardLimit - (augV(g, 'allin') ? 10 : 0);   // 「모 아니면 도」: 50 → 40   // 45웨이브부터 즉시 패배 한도
export const nextWaveIn = (g) => Math.max(0, waveLenOf(g.wave) - g.waveT);
