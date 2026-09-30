# 갈매미맨 AI 인수인계 — 전체본 v0.2.6

기준: 2026-09-29, 이 ZIP의 소스·설정·검증 기록 및 사용자 대화. 추측으로 미확정 내용을 확정하지 않는다. 설치는 INSTALL-FULL.md, 검증은 FULL-RELEASE-VERIFICATION.md를 먼저 확인한다.

## 1. 프로젝트 개요

카카오톡 채널 1:1 수집·조합 게임. Galaxy Tab S9의 Termux/proot Ubuntu에서 Node 서버를 실행하고 Cloudflare Tunnel로 HTTPS 공개. 카카오 블록 → 스킬 POST /kakao 또는 /kakao/skill → GameService → version 2.0 응답. 단체방용 API 구현은 없다. 반응을 보고 VPS·제휴를 검토하는 단계다.

## 2. 스택과 버전

package.json의 버전 범위보다 package-lock.json의 정확한 버전을 우선 확인한다. 현재 잠금 파일: Fastify 5.12.5, Prisma/Client 6.19.3, TypeScript 5.9.3, Vitest 3.2.7, tsx 4.23.13, Zod 4.6.5, Luxon 3.7.2, YAML 2.9.1. Node >=22 요구, 사용자가 첨부한 갤탭 화면은 Node 22.23.2. SQLite 엔진·Ubuntu·Termux의 정확한 설치 버전과 현재 cloudflared 버전은 미확정. 단일 프로세스 운영. HOST 127.0.0.1, PORT 기본 3000.

## 3. 현재 버전/에디션과 변경 이력

- package.name: galmaegiman-confirmed. 이름과 달리 일반 뽑기는 랜덤이다.
- 소스 버전 0.2.6, 콘텐츠 confirmed-75-v1, health 에디션 RANDOM_75.
- v0.2.2: 표시명 누락 보완. v0.2.3: 이름 중심 UI/조합과 내부 번호 숨김.
- v0.2.4: 기본 충전 10회, 관리자 설정 변경, 등급별 조합 목록.
- v0.2.5: 캐릭터 소개와 첫 수집/중복 획득 새우깡 보상.
- v0.2.6: 뽑기/조합/교환 획득 결과는 전 등급 첫 수집일 때만 이미지.
- 이번 전체본은 이 기능들이 반영된 소스와 기존 이미지 75장을 묶은 것이다. 과거 패치 설치 순서를 실행하지 않는다.
- 사용자 갤탭의 직전 v0.2.6 패치 적용은 테스트 파일 해시 불일치로 중단됐다. 이번 전체본의 갤탭 설치 성공은 아직 미확인이다.

## 4. 파일 역할

src/server.ts: 환경·DB 메타 검증과 시작. app.ts: 인증·HTTP·이미지·health. kakao.ts: 요청/응답 어댑터. game.ts: 게임과 트랜잭션. content.ts: 데이터 로딩/검증. gacha.ts: 랜덤 추첨. acquisition.ts: 소개/보상 문구. presentation.ts: 이름/목록 표시. schedule.ts: KST 충전. refill-settings.ts: 설정 이력.

data/catalog.yaml: 캐릭터 원본. display-names.yaml: 누락 이름 11개 보완. recipes.yaml: 조합. gacha.yaml: 확률. flavor.yaml: 소개. acquisition-rewards.yaml: 보상.

prisma/schema.prisma: 스키마, seed.ts: 콘텐츠 등록. public/images: 75개 PNG. scripts: setup/backup/tunnel/refill/test-db 등. test: 단위/통합 테스트. dist/node_modules는 장치에서 생성하며 이 배포에는 없다.

운영 파일은 .env, storage/confirmed.db, config/public-url.txt, config/refill-settings.json. 실제 사용자 DB·키·설정은 협업용 ZIP에 없으며 기존 갤탭에서 보존 복사한다.

## 5. DB 스키마

- AppMeta(key PK, value): edition/contentHash.
- Player(id UUID PK, identity unique, credits 기본3, snack, peanut, lastGrantAt, revision, repeatCursor, attendanceDate, lastExploreAt, createdAt).
- CharacterDefinition(id PK, name, rarity, sex, position unique, quote, imageUrl, nameStatus, artVersion).
- OwnedCharacter(id autoincrement, playerId FK, characterId FK, status, locked, peanutRemoved, obtainedVia, obtainedAt, consumedAt).
- CollectionEntry(playerId/characterId 복합 PK 및 FK, count): 누적 수집 이력.
- PendingAction(token PK, playerId FK, revision, kind, payload Json, result Json?, expiresAt, createdAt).
- AuditEvent(id UUID PK, playerId FK, kind, detail Json, createdAt).

성별/등급/상태는 문자열 필드다. 조합 소비 개체는 삭제하지 않고 CONSUMED로 남긴다. 별도 히든 발견 테이블은 없다. source schema를 권위로 삼고 필드명을 임의로 resultJson 등으로 바꾸지 않는다.

## 6. 구현 완료 기능

캐릭터는 흔함6 + 안흔함9 + 특별20 + 희귀40 = 75종. 조합은 비흔함 9+20+40 = 69개. 일반 뽑기 확률 40/30/20/10%, 합100%. 등급 먼저 추첨 후 등급 안 균등 추첨. 기본 75종은 중복 가능. 최초 획득권3, 매일 KST 09/12/18시 기본10장 누적; 실제 사용자 요청 시 미정산 시점을 정산한다. 관리자 `npm run refill -- 10`으로 공통 충전량 설정, `npm run refill` 조회. 변경 이력을 저장하므로 과거 시점은 이전 값으로 계산한다. 관리자 웹 UI는 없다.

이름 기반 내갈매미/도감/조합/조합식/조합목록/조합가능, 등급별 조합 목록, 잠금/잠금해제, 흔함 선택 교환(획득권1 또는 새우깡30 또는 땅콩1), 출석(한국 날짜 하루1회 새우깡30), 탐험(30초마다 새우깡10), 희귀 수컷 표식 전환(개체당1회 땅콩1) 구현.

전 등급 첫 수집 획득 결과만 이미지. 소비 후 재획득도 누적 이력이 있으면 이미지 없음. 수동 상세/조합식/교환 미리보기 이미지는 유지한다. 실시간 이미지 생성은 없다.

## 7. 미완성/미확정

전설/히든 개발 요청은 있으나 구현되지 않았다. 최종 이름·종수·레시피·보상·이미지·히든 발견 UX는 미확정. 과거 전설6/히든4 제안은 사용자 확정 목록이 아니다. 새우깡 상위 뽑기·특수함·불멸·VPS 이전·관리자 웹 UI는 미구현. 이미지 유무에 따른 실제 갤탭 응답속도 측정은 미확정.

이미지 75장은 v3 25장(흔함6/안흔함9/특별10) + v2 50장(특별10/희귀40) 구성이다. 75장을 전부 최신 스타일로 재생성했다는 의미가 아니다. 기존 이미지의 문구/한글 오류는 ART-STATUS.md를 참고한다. 69개 조합식의 데이터 상태는 TEST_DRAFT이며, 과거 확정 조합식 원문을 복원한 것이라고 주장하지 않는다. 이름 64개는 원본 유지, 11개는 이미지 콘셉트 기반 보완명이다. 원본 출처가 미확정인 부분을 새 AI가 임의로 확정하거나 이름을 바꾸지 않는다.

## 8. 알려진 미해결 이슈

갤탭에서 `node ../galmaegiman-update-v0.2.6/apply.mjs` 실행 시 `기존 파일이 배포본과 다릅니다 ... test/game.integration.test.ts`로 사전 검사 중단. 해당 설치기는 전체 검사 뒤에 쓰므로 이 오류 단계에서는 코드 쓰기가 시작되지 않았다.

기대 기존 파일 SHA256: 8d8798a79fde84d4e199b75703ff24b365fe1c42cee142d52c05f2494647da10
사용자 첨부 파일 SHA256: d22fa8d7f8c84f3e77006b8a4bc2c8d01ca5aaee85246f4de494759ee78f4167

첨부 파일은 중복 이미지 생략 테스트가 일부 들어 있으나 HTTP 테스트는 예전 출력과 health 0.2.5를 기대한다. 정식 v0.2.5/v0.2.6 어느 테스트 파일과도 같지 않다. 수정 주체/시점은 미확정. 이 ZIP은 정식 v0.2.6 소스/테스트를 담고, INSTALL-FULL.md의 별도 전체 교체 절차를 사용한다. 예전 패치 설치기의 체크를 무력화한 것이 아니다.

Ubuntu 안에서 proot-distro login을 재실행해 중첩 proot 오류를 겪었다. root@localhost이면 이미 Ubuntu다. 현재 갤탭 서버/터널 프로세스 상태는 미확정.

## 9. 설계 규칙

교배 폐지, 고정 조합 중심. 일반 뽑기는 랜덤이며 선택 교환과 별도다. 흔함/특수함을 제외하면 조합식을 둔다는 사용자 방향. 전설과 히든은 동급: 전설 공개식/히든 비공개식. 특수함만 중복 금지 방향이고 실제 구현은 아직 없다. 특수함 한~다섯 마리는 추후 새우깡 뽑기 및 불멸 재료로 사용하며 초반에 넣지 않는다. 전설은 희귀3 가치, 히든은 희귀2.5~3.5 가치 방향이며 정확한 재료는 미확정.

현재 획득 보상(first/duplicate): 흔함0/0, 안흔함10/3, 특별30/10, 희귀90/30 새우깡. grant에서 개체 지급과 CollectionEntry 증가 후 count===1로 첫 수집 판정. 보유0이어도 과거 획득 이력이 있으면 중복이다. 기존 소급 지급 없음. 중복도 개체는 지급한다. 획득 보상은 새우깡 2,000,000,000 잔액 상한까지 실제 증가분을 기록; 모든 재화 지급 경로가 이 상한을 공통 사용한다는 뜻은 아니다.

한 프로세스의 전역 큐로 직렬 처리(pending 제한30, 대기1초). Prisma maxWait500ms/timeout2500ms. 소비·획득·수집·보상·행동 기록을 한 트랜잭션으로 처리. 조합/선택 교환/표식 전환은 미리보기 → 확인. 8바이트 난수의 16자리 hex 토큰, 10분 유효. 소유자/revision/재료 상태 확인. 완료 토큰 재전송은 저장 결과 반환, 추가 지급 없음. 저장된 첫 획득 이미지가 재표시될 수 있다. 일반 뽑기 요청은 재전송하면 새 뽑기이며, 모든 HTTP 요청의 멱등성을 보장하지 않는다.

기본 조합은 잠금·표식 전환 개체 제외, ID 오름차순으로 사용 가능한 개체 선택. 현재 레시피 로더는 결과보다 낮은 등급의 재료만 허용한다. requiresCollection/peanutState 타입 지원은 있으나 현재 recipes.yaml 로더는 튜플의 기본 세 필드만 읽는다.

contentHash는 catalog.yaml와 recipes.yaml의 원문 해시. 시작/seed에서 다른 콘텐츠 DB를 거부한다. 보상/표시명/소개 파일은 이 해시에 포함되지 않는다. 로더에 75종/69식과 CUSR ID가 고정되어 있으므로 상위 추가에는 안전한 콘텐츠 마이그레이션이 필요하다.

세계관: 갈매미는 독창적인 거대 석상 수호자+갈매기. 곤충/Mommy 해석 금지. 돌/갑옷 비율은 개성을 위해 낮출 수 있다. 불멸이 최고점이며 상위는 반드시 거대/우주적 압도보다 개성과 개연성이 중요하다. 진지한 연출과 하찮은 새우깡 개그의 낙차. 이미지 문구 허용, 전설/히든 배경 획일화 금지. 기존 75종 이름은 요청 없이 변경하지 않는다.

## 10. 카카오 응답/요청 제약

아래는 프로젝트의 구현 제한이다. 최신 공식 규격 전체를 검증한 목록이 아니다.

version2.0, template.outputs 최대3; 일반 텍스트 950자씩 최대1900자; listCard 1~5항목; quickReplies 최대10/라벨14자; simpleImage altText50자; HTTPS 공개 이미지 URL. 허용 이미지 ID 정규식은 C/U/S/R+숫자3+M/F, 제공 파일은 PNG. 상위 등급 추가 시 app.ts/kakao.ts/content.ts의 제한을 검토한다.

확인 정보는 listCard 항목 extra에 gmAction/gmToken, 요청 action.clientExtra에서 수신. 확인 문구만 직접 입력하고 정보가 없으면 실행하지 않는다. 사용자 식별자는 JSON.stringify(['KAKAO_CHANNEL',bot.id,user.type 또는 botUserKey,user.id]). user.properties는 unknown 값 허용. x-skill-secret 최소32자 검증, KAKAO_BOT_ID 설정 시 봇 일치 확인. HTTP bodyLimit32768, 스킬 전역 rate limit 분당180은 이 코드 설정이다.

## 11. 다음 작업

1. 전체본을 기존 DB/설정을 보존해 갤탭에 설치하고 health/ready/채널을 확인한다. 실제 적용 완료 전 완료라고 말하지 않는다.
2. 첫 수집 이미지/중복 이미지 없음/상세 이미지/충전/보상 유지 확인.
3. 전설/히든의 미확정 콘텐츠와 발견 규칙을 확정한다.
4. 기존 75종·도감·재화 보존과 콘텐츠 마이그레이션을 포함해 확정 범위만 개발한다.

최소 범위 원칙: 관련 파일만 확인/수정, 불필요한 리팩터링·미요청 기능 금지, 관련 검증만 수행, 완료 보고는 변경 파일/내용/검증/미해결 이슈. 스키마/기능의 현재 사실은 소스, 설정 결정은 사용자 최신 지시를 우선한다.
