> **통합 배치 마이그레이션 안내:** 이 문서의 starter-only SQL은 과거 참고본이다. 이번 V2/온보딩/뉴스 배치는 `docs/migrations/v2-onboarding-news.sql` 하나만 기준 스키마 확인 후 한 번 적용한다. `docs/temporary-starter-migration.sql`과 **둘 다 실행하지 않는다** (중복 ALTER/테이블 충돌).

# 임시 시작 갈매미 2마리 — 로컬 검토 패치

기준: chatbotgmm/server `manual/home-assembly-20261019`, HEAD `02a46bc58e8c031dd65e9a058e0ccec80f9e29b2` 커넥터로 확인. 게시/커밋/배포/실제 지급/DB 마이그레이션/APK 빌드 없음. CLAUDE.md 확인, 루트·프로젝트·src AGENTS.md는 404.

## 동작
- 영구 D1 지급(튜토리얼 1~2에서 3 이상 전환)은 그대로다.
- 이번 구현 이후 서버 `webTutorial`의 신규 판정(기존 보유/StorySave 없음, step0)에서만 enrollment를 기록한다. 이후 실제 tutorial 완료/skip(1~5→9) transaction에 pending entitlement를 저장한다. 중간 종료 후 재접속해도 선택 가능. 기존 완료자 및 구버전 진행자는 소급 지급하지 않는다. 클라이언트 story clear/reset은 entitlement와 무관하다.
- 10개 속성별 1종, 서로 다른 2종 선택. STONE T1, GOLD D2, FIRE T2, ICE D5, BLOSSOM T5, SEA D4, SHADOW D3, STAR D6, THREAD D8, GRAVITY T6. 실제 units.json/battle.yaml 대조; D1 중복 없음. 밸런스 시뮬레이션 완료 의미는 아니다.
- 서버 시각으로 지급 시점부터 정확히 15×24시간. owner unique receipt에 원래 선택·정확한 owned IDs·지급/만료 시각 보존. 순서만 다른 재시도는 같은 영수증, 다른 선택은 오류. 일반 grant 함수를 호출하지 않아 collection/최초획득/중복보상/업적을 주지 않는다.
- 임시 loanExpiresAt는 개체 필드다. locked를 풀려고 해도 임시 개체에는 적용되지 않으며, 판매·조합·땅콩떼기·환생/재평가·탐험 쿼리는 permanent predicate를 별도로 적용한다.
- 새 전투/대기 큐/준비 완료 시 재검증에서 만료 개체 제외. 이미 시작한 전투는 캡처된 fighter와 metadata로 끝까지 처리한다. ordinary XP만 허용; 성장선택 및 해당 임시 대상 소모품 지출은 정책 확정 전 차단한다.
- 만료 행/영수증은 감사와 활성 전투 정산용으로 보존한다. 이후 웹 transaction에서 StorySave oID/team/hp와 저장 lineup 참조를 제거한다. 레거시 종 편성은 해당 종 유효/영구본이 남으면 보존한다. 종 ID로 보유 개체를 삭제하지 않는다.
- 상위 4등급, 스토리 1~3명, PvP 3종 및 환생 inherited-upper 예외 유지. 스토리 캐릭터 보상, 제안 단계 티켓50/100, 증강 규칙은 변경하지 않았다.

## API/DTO
GET `/my/api/starter-loans`: 기존 signed session 및 x-gmm guard, no-store.
POST 같은 URL body `{ids:["T1","D2"]}`: 기존 owner-authenticated act wrapper. 응답 `{ok:true,result:DTO}`. 별도 playerId/client expiry는 받지 않는다.

DTO: `{state:'ineligible'|'pending'|'claimed'|'expired',serverNow:number,chooseCount:2,durationMs:1296000000,options:[{id,name,attrCode,attr,role,tier,image}],receipt:null|{id,selectedIds:string[],ownedIds:number[],grantedAt:number,expiresAt:number}}`.
모든 시간은 epoch milliseconds. 옵션 image는 `/duel/sprites/<id>_s0_front.webp`.
Native instance/detail/fighter/growth에는 `temporary:boolean`, `expiresAt:number|null`. native/state는 `serverNow`, detail은 `battleEligible:true`. 종별 me.units 및 native catalog 집계는 temporaryCount/permanentCount/temporaryInstances:[{key,temporary:true,expiresAt}]를 제공한다. 종 자체 temporary=true 표식은 붙이지 않는다. 영구본까지 회색 처리하면 안 된다. 현재 심사용 계정 새 route allowlist는 변경하지 않음(기존 계정 소급 권한 없음).

## 검증 및 남은 필수 확인
- Node24 dependency-free 집중검사 **25/25 통과**. 선택 유효성, 원데이터 속성/등급, 동일 영수증 재시도, 소유자 분리, 서버 만료 경계, 직렬 동시요청 모형, exact-ref 정리·동종 영구본 보존, destructive predicates, 큐 만료·최종 준비 재검증·3종 규칙, native active settlement source contract.
- 수정 TS 8파일 Node syntax/strip 검사 통과.
- 테스트에는 모의 transaction/소스 계약 검사가 포함된다. 실제 Prisma DB concurrent writer race/rollback, Fastify 실제 세션 E2E, full tsc/Vitest, 시각 QA/실기기 테스트는 **미실행**. 의존성·Prisma generated client 없는 제한된 scratch snapshot이며 전체 검증 완료로 간주하지 않는다.
- 적용 전 전체 체크아웃에서 Prisma schema validate/generate 후 typecheck 및 기존+신규 DB/integration 테스트가 필요하다. DDL은 docs/temporary-starter-migration.sql 검토용 초안만; 실행 승인 없음. frozen CI hash는 수정하지 않았다.
- API schema 변경 때문에 backend/client를 함께 검토해야 한다. 우편/소식 작업의 schema/my/native hunk와 통합 시 재검증 필요.

## 독립 안전 리뷰 후 보강
- 기존 `/my/api/story`가 임의 성장/HP를 받아 임시 oID와 종별 fallback으로 제한을 우회할 수 있음을 확인하고 차단했다.
- `saveStory`의 기본 신뢰도는 legacy/untrusted. 임시 개체 units/HP는 저장된 서버 snapshot만 유지한다. 요청에서 제외하거나 null reset해도 임시 성장은 지워지지 않는다. 신규 loan snapshot이 없으면 클라이언트 oID 입력도 제거한다. 영구 개체의 기존 legacy 저장 호환성은 유지한다.
- 서버가 계산한 `webNativeCommit`만 trustedNative=true로 실제 전투 XP/HP를 기록한다. native rec 및 casual PvP roster/preparation은 임시 개체에 종별 성장 fallback을 사용하지 않는다.
- 추가 테스트는 실제 production webSaveStory→saveStory, webNativeCommit, native rec, PvP roster/preparation 메서드를 소스에서 추출해 실행한다. exact oID 위조/누락/null reset/최초값 주입/정상 native XP/동종 영구본 호환성을 검사한다. DB delegate와 일부 성장 의존성은 모의이므로 실DB/Fastify 통합 테스트를 대체하지 않는다.
- itemRequirement의 실제 pinned 반환형 `{itemId:string;target?:number}|null`을 원본 adapter에서 재확인하여 item.target 타입 의심은 해소했다. adapter 자체는 변경하지 않았다.
- 집계와 loan metadata 사이 만료/동시 지급 race를 막기 위해 webMe는 서버 시각 1회와 소유행 snapshot 1회로 permanent/temporary 수량을 함께 계산한다. 실제 webMe 실행 회귀시험으로 동종 영구+임시 분류와 만료 직전 시각 일관성을 확인했다.
