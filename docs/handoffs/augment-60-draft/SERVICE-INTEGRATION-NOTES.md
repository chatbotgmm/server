# 증강 V2 서비스 연결 검토

## 적용 범위

- 기준 소스: `chatbotgmm/server@34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d`.
- 추가 원본 7개 파일의 URL, Git blob SHA, SHA-256은 `SOURCE-PIN-service.json`에 기록했다. 원본은 `pristine/galmaegiman-confirmed/src/`에 보존했다.
- `game.ts`, `duel.ts`, `web.ts`는 조사만 했으며 변경하지 않았다.
- 동결된 엔진·core·rules·augment 효과/카탈로그 파일은 수정하지 않았다.

## 신규 서버 경계

`src/battle/service-factory.ts`의 `createBattleService(baseRules, options)`는 기본 V1이다. V2는 서버 코드가 `{version:2, weights:{silver, gold, prismatic}}`를 명시적으로 전달해야 한다. 확률 기본값이나 환경변수 자동 활성화는 추가하지 않았다. 카탈로그 전체 60개와 등급 및 가중치를 검증한다.

- 네이티브: `registerNative`의 마지막 선택 인수.
- 실시간: `RealtimePvpService` 생성자의 마지막 선택 인수.
- 현재 호출자는 이 인수를 전달하지 않으므로 운영 기본은 V1 그대로다.
- 구형 리플레이 검증 대전은 V2 네이티브 설정에서 시작을 거부하고 실시간 대전으로 안내한다.

## 기존 전투 보존

새 DB 저장 전투에는 서버 내부 `serviceRules` 스냅샷을 기록한다. 공개 응답에는 이 필드를 전달하지 않는다. 이전 V1 저장 형식에는 현재 기본 데이터의 V1 규칙을 사용한다.

실시간 방을 재개할 때 기본 전투 데이터가 현재와 같은지 확인하고, 저장 규칙·V2 가중치·카탈로그에서 계산한 지문과 저장 `rulesVersion`의 정확한 일치를 확인한다. 따라서 V1↔V2 설정 전환과 V2 가중치 변경은 진행 방을 재해석하거나 중단시키지 않는다. 일반 기본 규칙 변경 또는 저장 hash 불일치는 기존 `SERVER_ABORT` 경로를 유지한다. 준비 단계에서 실제 전투를 다시 생성할 때도 저장 방 설정을 사용한다.

## 공개 응답과 호환성

- 카드: `id`, `name`, `desc`, `p`, `version`; V2에 `tier`; 후보에 `rerolled`.
- 전투: `augmentVersion`, `effectsVersion`.
- 전투원: `shield`(흡수 가능한 HP 절대량), `shieldTurns`(현재 턴 포함 남은 턴). 만료 시 0.
- 표시 이벤트: `absorbed`, `shieldBefore`, `shieldAfter`를 실시간 allow-list에 추가했다.
- 실시간 상대 후보·숨은 기술·RNG·내부 증강 상태의 기존 비공개 경계를 유지한다.
- 지원 클라이언트는 `x-gmm-augment-version: 2`를 보낸다. 이 헤더는 기능 활성화 권한이 아니다.
- 신규 전투는 서비스 설정, 진행 전투는 실제 저장된 전투 버전으로 헤더 지원 여부를 검사한다. 미지원 시 409와 `AUGMENT_VERSION_UNSUPPORTED`.
- 성장/순위 등 비전투 API 및 취소·항복·네이티브 `leave`는 헤더 미지원만으로 차단하지 않는다.

## 검증

- `node --import ./scripts/ts-source-loader.mjs --test test/service-integration.test.mjs`: 15개 통과.
- `node --import ./scripts/ts-source-loader.mjs --test test/*.test.mjs`: 당시 127개 통과.
- 실제 실시간 공개 mapper를 양쪽 관점으로 실행해 상대 후보/기술 비공개와 보호막 이벤트 전달을 확인했다.
- 수정한 6개 TypeScript 파일의 Node 타입 제거/변환 문법 확인을 수행했다.
- 전체 의존성 및 DB가 없는 제한 스냅샷이므로 Fastify/Prisma 실제 HTTP·DB 통합 테스트와 전체 `tsc`는 수행하지 않았다. 문법 검사와 모듈 실행 테스트가 전체 타입 검사 대체는 아니다.
- 외부 push, 배포, APK 빌드, 실제 DB 변경은 수행하지 않았다.
