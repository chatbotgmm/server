# 공지 관리·이미지 업로드 백엔드 (content-admin)

- 브랜치: `feature/notice-media-backend-20261101` (PR chatbotgmm/server#12)
- 기준 커밋: `0b0fb8b5c528c3bf15029cfd74334ad89d7d5500` (`backup/gmm-110-source-20261101`)
- 2차 수정: 6ed316b 검토 의견 6건 반영(공개 판 분리, DB 원자성, 이미지 경합, 계정 변환 계층, 401/403/503·CSRF, 명세).
- **이 브랜치는 공유 파일 `prisma/schema.prisma`·`src/app.ts` 를 바꾸지 않는다.** 둘 다 `docs/content-admin/integration-*.patch` 로만 제공한다.
- 실제 DB 실행·운영 배포·권한 부여는 하지 않았다. 기존 관리자 HTML 시안은 실제 서버와 연결되지 않은 모의 화면이며 구현 완료로 보지 않는다.

## 1. 구성과 겹침

| 묶음 | 파일 | 다른 작업과 겹침 |
|---|---|---|
| 의존성 | `package.json`, `package-lock.json` (sanitize-html 2.18.1, sharp 0.35.5, @types/sanitize-html) | 의존성 변경 브랜치와 lock 충돌 가능 → `npm install` 로 재생성 |
| 공용 | `src/content-admin/{access,account-bridge,audit,csrf,errors,http,ids,requests,store,sql-store,wiring}.ts` | 없음(새 파일). 계정 모듈 파일은 읽거나 바꾸지 않음 |
| 공지 | `src/notices/{sanitize,service,routes}.ts` | 없음 |
| 이미지 | `src/media/{validate,storage,service,routes}.ts` | 없음 |
| DB 변경안 | `prisma/manual/20261101_content_notice_media_additive.sql` (미실행) | 없음(새 파일) |
| 통합 패치 ① | `docs/content-admin/integration-schema.patch` → `prisma/schema.prisma` 끝에 모델 6개 추가 | **계정·세션·인증코드 모델을 추가하는 작업과 같은 파일 끝부분.** 텍스트 충돌만 나며 모델 이름(Content*)은 겹치지 않음 |
| 통합 패치 ② | `docs/content-admin/integration-app-ts.patch` → `src/app.ts` (import 3줄, AppOptions 필드 1개, 등록 3줄) | **인증 작업이 app.ts 를 고치면 충돌 가능.** 등록 위치는 `rateLimit` 등록 바로 뒤 |

두 패치는 기준 커밋 파일에 `git apply --check` 로 적용 가능함을 확인했다.
모델 파일 원본은 `docs/content-admin/schema-models.prisma`. 이 모델과 SQL 파일이 같은 구조인지는 "기준 스키마 + SQL 적용 DB → 기준 + 모델" `prisma migrate diff` 결과가 빈 마이그레이션인 것으로 확인했다.

### 통합 순서
1. 계정 모듈 작업과 schema.prisma 를 합친 뒤 ① 패치의 모델 6개를 덧붙인다(기존 모델 수정 없음).
2. 검토 후 운영자가 SQL 파일을 백업 뒤 적용한다(이 작업에서는 실행하지 않음). ①을 넣지 않고 SQL 만 적용하면 이후 `prisma db push` 가 이 표들을 지우려 할 수 있으니 반드시 함께 넣는다.
3. 계정 모듈이 `VerifiedAccountReader`·`AccountPermissionReader` 를 구현하면 ② 패치로 app.ts 에 연결하고 `contentAdmin.admin = {principals: accountPrincipalResolver(...), csrf: ...}` 를 넘긴다.

## 2. 인증 연결 (계정 모듈 담당에게)

`src/content-admin/account-bridge.ts`

```ts
interface VerifiedAccountReader { verifiedAccountId(req): Promise<string | null> }   // 서버가 검증한 자체 계정 ID
interface AccountPermissionReader { hasPermission(accountId, 'notice:write'): Promise<boolean> }
accountPrincipalResolver({accounts, permissions, toUserId?}): PrincipalResolver
```

- 계정 모듈이 세션 쿠키/토큰을 검증해 **accountId** 를 준다. 로그인 안 됨·만료·취소 세션은 `null`, 저장소 장애는 **예외**.
- 변환 계층이 `notice:write` 권한을 서버 저장소에서 확인한 경우에만 `content.notice.admin` 역할을 붙인다.
- 감사 기록용 userId 는 기본 `acct_<accountId>` (`toUserId` 로 바꿀 수 있음). 이메일·전화번호·카카오 ID 는 쓰지 않는다.
- 요청 헤더·본문·쿼리의 role/permission 값은 어디서도 읽지 않는다. 정의되지 않은 본문 필드(role 등)는 400. 카카오 연동 계정이라는 사실만으로는 권한이 없다.
- 시험용 가짜 계정 모듈은 시험 파일에만 있다(`src/` 에 우회 스위치 없음). 실제 권한 부여·계정 생성·인증 키 발급은 하지 않았다.

### 응답 구분
| 상황 | 응답 |
|---|---|
| 확인된 계정 없음 | 401 `LOGIN_REQUIRED` |
| 계정은 있으나 `notice:write` 없음 | 403 `ADMIN_REQUIRED` |
| 계정·권한 확인 중 예외(저장소 장애), 형식이 잘못된 결과 | 503 `AUTH_UNAVAILABLE` + `Retry-After: 5` |
| 관리자 쓰기에 CSRF 정책이 연결되지 않음 | 403 `CSRF_POLICY_MISSING` |
| CSRF 검사 실패 | 403 `CSRF_REJECTED` |

### CSRF (통합 필수 항목)
- 관리자 쓰기(POST/PUT, 업로드·폐기 포함)는 CSRF 정책이 있어야만 동작한다. `wiring.ts` 는 인증을 연결하면서 CSRF 정책을 빼면 시작을 거부한다.
- 쿠키 인증이면: `originCsrfPolicy(['https://관리자-출처'])`(Origin 이 정확히 일치하거나, Origin 이 없을 때 `Sec-Fetch-Site: same-origin`) 또는 인증 담당이 만든 토큰 검증 `{verify(req)}`.
- `'bearer-only'` 는 Authorization 헤더처럼 브라우저가 자동으로 붙이지 않는 자격만 쓰는 연결에서만 허용한다.
- 쿠키에는 `SameSite=Lax` 이상·`HttpOnly`·`Secure` 를 권장(계정 모듈 결정 사항).

## 3. 공개 판과 작업 판

- 공지 내용은 **불변 판**(`ContentNoticeRevision`)으로만 저장한다. 공지 머리에 `revision`(작업 판)과 `publishedRevision`(공개 판)이 있다.
- 임시저장(PUT)·이전 판 복원(restore)은 **새 작업 판**만 만든다. 공개 판·게시 상태는 그대로다.
- **명시적 게시(publish)만** `publishedRevision` 을 지금 작업 판으로 바꾼다. 다른 판을 지정하면 409 `REVISION_NOT_WORKING`(화면에 보이지 않던 판이 게시되지 않게).
- 상단 고정(pinned)도 판의 내용이라 게시해야 공개 목록에 반영된다(별도 pin API 없음).
- 숨김(hide)은 공개를 멈춘다. 다시 게시하면 그때의 작업 판이 공개된다.
- 관리자 응답: `working`(작업 판)·`published`(공개 판)·`hasUnpublishedChanges` 를 함께 준다.

## 4. 원자성·중복·동시성

- 관리자 쓰기 한 건 = DB 트랜잭션 하나: **요청 예약 → 공지/판/이미지 참조 변경 → 감사 기록 → 결과 기록**. 중간에 실패하면 예약까지 모두 취소되어 같은 requestId 로 다시 시도할 수 있다.
- 요청 예약은 `ContentAdminRequest` 기본키(actorId, requestId)와 `INSERT … ON CONFLICT DO NOTHING` 으로 한다. 트랜잭션의 첫 문장이 쓰기라 SQLite 쓰기 잠금을 먼저 잡고, 다른 서버 인스턴스의 같은 요청은 잠금을 기다린 뒤 완료된 결과를 그대로 돌려받는다(`Idempotent-Replayed: true`).
- 프로세스 내부 큐는 쓰지 않는다. 잠금 시간 초과는 트랜잭션 전체를 처음부터 다시 실행하고(최대 40회, 짧은 대기), 그래도 안 되면 503 `STORE_BUSY`.
- 버전 검사(`expectedVersion`)는 DB 조건부 UPDATE 로 한다(불일치 409 `VERSION_CONFLICT`).
- 이미지 업로드: 파일을 먼저 새 UUID 로 쓰고 → 트랜잭션. 트랜잭션이 실패하거나 다른 인스턴스가 이미 처리한 요청이면 방금 쓴 파일을 지운다.

## 5. 이미지 참조와 폐기

- 판별 이미지 참조는 `ContentNoticeImageRef` 행으로 저장하고, `ContentMedia` 에 **외래키 ON DELETE RESTRICT** 를 건다. 참조가 남은 이미지 행은 DB 에서 지울 수 없다.
- 폐기(retire)는 같은 트랜잭션에서 "어떤 판(작업·공개·지난 판)도 참조하지 않음"을 확인한 뒤 `active → retired` 조건부 갱신을 한다. 글에 이미지를 붙이는 쪽도 같은 트랜잭션에서 `active` 를 확인한다. 두 트랜잭션은 쓰기 잠금으로 직렬화되므로 "폐기됐는데 참조됨"이 생기지 않는다.
- 지난 판이 쓰는 이미지는 폐기할 수 없으므로 복원에 필요한 이미지는 남는다. 이미지 파일은 이 모듈에서 실제로 지우지 않는다(실제 삭제는 참조 없음을 다시 확인하는 별도 정리 작업으로 남김).

## 6. 관리자 시안과의 연결(본문 이미지·JS 원문)

> 시안 파일은 저장소의 모든 원격 브랜치에서 찾지 못했다(이름에 admin/notice/공지/관리 가 들어간 HTML 없음). 아래는 시안이 따라야 할 서버 계약이며, 시안 실제 동작과의 대조는 하지 못했다.

| 시안 요소(예상) | 서버 필드·API | 규칙 |
|---|---|---|
| 제목·본문 편집기 | `title`, `body`(원문 → 서버가 `bodyHtml` 생성) | 미리보기는 응답의 `working.bodyHtml` 만 innerHTML 로. `bodySource` 는 textarea 값으로만 |
| 이미지 첨부 목록(순서·대체 텍스트) | `images: [{mediaId, alt}]` 배열 순서 = 표시 순서 | 업로드는 `POST /api/v1/admin/media`(이미지 바이트, `Idempotency-Key`) → `id` 를 받아 배열에 넣음 |
| 본문 중간 이미지 삽입 | 본문 원문에 `<img data-media-id="<id>" alt="…">` | **그 글의 `images` 에 있는 이미지만** 남는다. `src`·이벤트 속성은 저장 시 제거되고, 응답할 때 서버가 주소를 붙인다(관리자: `/api/v1/admin/media/<id>`, 공개: `/api/v1/media/<id>`) |
| 별도 JS 소스 입력란 | `scriptSource`(최대 50,000자) | **보관만.** 관리자 JSON 에 문자열로만 돌려주고 공개 응답에는 넣지 않는다. 서버·관리자 화면 어디서도 실행하지 않는다 |
| 미리보기 창 | `working.bodyHtml` | 관리자 로그인 세션과 섞지 않는다. 격리 실행(다른 출처 + sandbox iframe)은 별도 과제 |
| 저장 / 게시 / 숨김 / 복원 버튼 | PUT / publish / hide / restore | 모든 쓰기에 `requestId`, 수정 계열에 `expectedVersion`. 409 를 받으면 최신을 다시 불러와 사용자에게 알림 |
| 상단 고정 체크 | 본문 저장의 `pinned` | 게시해야 공개 목록에 반영 |
| 화면에서 숨긴 버튼 | — | 권한은 서버가 다시 확인한다. 버튼을 숨기는 것으로 권한을 대신하지 않는다 |

JS 실행 기능은 켜지 않았다(실행 경로 없음).

## 7. API 요약 (JSON, `Cache-Control: no-store`, `nosniff`, `CSP default-src 'none'`)

공개(로그인 불필요, 게시 중인 글의 공개 판만)
- `GET /api/v1/notices?limit=1..100` → `{items:[{id,title,pinned,excerpt,publishedAt,imageCount}]}` (고정 먼저, 게시일 최신순)
- `GET /api/v1/notices/:id` → `{id,title,pinned,bodyHtml,images:[{mediaId,alt,url}],publishedAt}` · 초안·숨김·없음은 같은 404
- `GET /api/v1/media/:id` → 공개 판이 쓰는 활성 이미지만, 그 외 404

관리자(`content.notice.admin`; 쓰기는 CSRF 정책 필요)
- `GET /api/v1/admin/notices?status=` · `GET /api/v1/admin/notices/:id` · `GET /api/v1/admin/notices/:id/revisions` · `GET /api/v1/admin/audit?limit=`
- `POST /api/v1/admin/notices` `{requestId,title,body?,scriptSource?,pinned?,images?}` → 201
- `PUT /api/v1/admin/notices/:id` `{requestId,expectedVersion,title,body?,scriptSource?,pinned?,images?}` → 새 작업 판
- `POST /api/v1/admin/notices/:id/publish` `{requestId,expectedVersion,revision?}` → 공개 판 교체
- `POST /api/v1/admin/notices/:id/hide` `{requestId,expectedVersion}`
- `POST /api/v1/admin/notices/:id/restore` `{requestId,expectedVersion,revision}` → 새 작업 판(공개 판 그대로)
- `POST /api/v1/admin/media`(이미지 바이트, 헤더 `Idempotency-Key`) · `GET /api/v1/admin/media/:id` · `GET /api/v1/admin/media/:id/info` · `POST /api/v1/admin/media/:id/retire` `{requestId}`

규칙: 제목 1~120자, 본문 원문 최대 50,000자, 이미지 글당 20장(중복 불가, 대체 텍스트 필수 1~300자). requestId 8~80자 `[A-Za-z0-9_-]`, 같은 요청 재전송은 첫 결과, 내용이 다르면 409 `REQUEST_ID_REUSED`.

## 8. HTML 정화·이미지 검사
- 저장 시 `sanitize-html`(htmlparser2) 허용 목록: p br strong b em i u s blockquote ul ol li h2 h3 h4 a code pre hr img. a[href](http/https/mailto, rel·target 자동), img[data-media-id, alt] 만. script·style·iframe·object·svg·math 등은 내용째 제거, on*·style·class·id·src 제거. 응답할 때 한 번 더 같은 허용 목록으로 걸러 서버가 정한 이미지 주소만 붙인다.
- 이미지: PNG·JPEG·WebP 서명 + sharp 실제 디코딩, 2,400만 픽셀·6000px·8MB·애니메이션 거부, EXIF 등 메타데이터 제거 후 재인코딩, 서버 UUID 파일명, 정적 폴더 안 저장 거부, `nosniff` + `CSP sandbox`, 외부 URL 가져오기 없음.

## 9. 감사 기록
작업 종류·대상·관리자 userId·시각·허용 필드(version, revision, status, pinned, restoredFrom, imageCount, bytes, mime, width, height)만 같은 트랜잭션에 남긴다. 본문·제목·원문·JS 원문·토큰·세션·헤더·IP·이메일은 남기지 않는다.

## 10. 알려진 한계·미검증
- 실제 계정 모듈과의 연결, 실제 쿠키 세션에서의 CSRF 동작은 시험용 가짜 계정 모듈로만 확인했다.
- app.ts 연결 상태로 서버 전체를 띄운 시험은 하지 않았다(패치 적용 검사·타입 검사만).
- 여러 서버 인스턴스 시험은 같은 SQLite 파일을 쓰는 두 PrismaClient 로 했다. 다른 DB(예: PostgreSQL)로 옮기면 잠금 동작이 다르므로 다시 시험해야 한다.
- 목록 요약(excerpt)은 HTML 이스케이프된 글자. 공개 이미지는 `public, max-age=300` 이라 숨긴 뒤 최대 5분 캐시에 남을 수 있다.
