# 내 갈매미 보유 개체 조회

이번 변경은 새 60종의 보유 개체 읽기 API다. 기존 도감의 종 기본값과 구분한다. 서버 배포나 계정 데이터 변환은 포함하지 않는다.

- `GET /my/api/native/roster60/owned`: `CONSUMED`를 제외한 해당 계정의 모든 보유 개체를 반환한다. 잠금·전투·탐험 중 개체도 조회할 수 있다. 같은 종의 다른 소유 ID는 유지하며, 레벨 내림차순·소유 ID 순으로 정렬한다.
- `POST /my/api/native/roster60/owned/read`, 본문 `{ownedId}`: 정확한 소유 ID를 조회한다. 타인·없는 ID·소모된 ID는 동일한 `404 UNIT_NOT_FOUND`다. 조회 요청에는 부가 필드를 허용하지 않는다.

두 응답은 `flow: roster60-owned-v1`, `catalogVersion`, `serverNow`를 포함한다. 목록은 `units`, 상세는 `unit`에 들어간다. 기존 인증과 프로필 경계, `Cache-Control: no-store`를 재사용하며 지급·성장·편성 저장을 수행하지 않는다.

## 상세 필드

| 필드 | 의미 |
|---|---|
| `ownedId`, `speciesId` | 소유 개체 ID와 종 ID. 화면의 선택 기준은 소유 개체 ID다. |
| `level`, `maxLevel`, `reincarnation`, `stats` | 현재 소유 XP·전생 횟수로 서버가 계산한 레벨과 최종 5능력치 |
| `growth` | `level`, `maxLevel`, `reincarnation`, 현재 주기 누적 `totalXp`, 현재 레벨 내 `xp`, 다음 레벨까지 `xpToNext`, 현재 레벨 시작 누적 `levelStartTotalXp`, 실제 다음 누적 임계치 `nextLevelTotalXp`. 다음 레벨이 없으면 `xpToNext`와 `nextLevelTotalXp`는 `null`이다. |
| `statBreakdown` | `base` + `growth` + `rebirth` = `total` = `stats`. 기존 `fighter`/`growthAt`/전생 계산을 재사용한다. `other`는 `null`, `otherStatus`는 `NO_OTHER_BONUS_MODEL`이다. 없는 보너스를 0으로 만들어 표시하지 않는다. |
| `aptitude`, `ownResistance` | 소유한 개체의 실제 적합도·속성별 방어. 새 상세 응답에서 소유권을 확인한 뒤에만 제공한다. 기존 상대/도감/영수증의 `publicDefenseSafe` 필터는 변경하지 않는다. |
| `skills.attack`, `skills.current` | 현재 서버 전투 규칙으로 사용할 수 있는 기본 공격·습득 기술. ID·이름·속성·위력·MP·습득 레벨과 위력 설명을 반환한다. |
| `skills.nextLearn` | 정의된 기술 중 다음 습득 레벨과 그 레벨의 기술들. 없으면 `null`이다. |
| `skills.growth` | `DESIGN_NEEDED` / `NO_PER_INDIVIDUAL_SKILL_GROWTH_MODEL`. 개체별 기술 강화 단계·강화 비용을 추측하지 않는다. |
| `party.pvp` | 서버에 저장된 PVP 편성에서의 1부터 시작하는 `position`과 `size`. 미편성이면 위치는 `null`. |
| `status`, `locked`, `party.activity` | 조회 시점의 사용·잠금 상태. 출전 가능성을 클라이언트에서 확정하는 증명이 아니다. |

소유 목록에는 현재 전투 체력/MP가 아닌 최종 능력치의 최대 HP/MP를 반환한다. 전투 중 남은 자원이나 클라이언트에만 저장된 스토리 편성 위치를 추정하지 않는다.

## 확인 결과

2026-10-28, Node 24.19.0, Vitest 5.0.3, 격리 SQLite `test.db`에서 새 `roster60-owned.integration.test.ts` **6/6 통과**, 기존 observed-defense integration·library integration·five-stats route **19/19 통과**, `npx tsc --noEmit --pretty false` 통과.

새 시험은 같은 종의 서로 다른 개체·계정 격리·잠금/사용 중 조회·소유권 404·5능력치 분해 합계·실제 전투 정산 후 XP/레벨 새 조회·실제 전생 후 같은 소유 ID 새 조회·다음 습득/최대 레벨·API 인증/엄격한 본문/비캐시·기존 방어 비공개 필터 유지를 확인한다. Android 화면·실기기·운영 API는 이 서버 시험의 범위가 아니다.
