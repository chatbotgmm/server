# 공지 관리·이미지 업로드 백엔드 (content-admin)

- 브랜치: `feature/notice-media-backend-20261101`
- 기준 커밋: `0b0fb8b5c528c3bf15029cfd74334ad89d7d5500` (`origin/backup/gmm-110-source-20261101`, 2026-11-01 기준 가장 최근 원격 커밋)
- 상태: 서비스 전 백엔드 모듈. **서버 진입 파일(app.ts)에는 연결하지 않았다.** 연결은 `integration-app-ts.patch` 로 따로 검토한다.
- 기존 관리자 HTML 시안은 실제 서버와 연결되지 않은 모의 화면이며, 이 작업은 그 시안을 구현 완료로 보지 않는다.

## 1. 구성(합칠 때 나눠서 보면 되는 단위)

| 묶음 | 파일 | 다른 작업과 겹침 |
|---|---|---|
| A. 의존성(통합) | `package.json`, `package-lock.json` (sanitize-html 2.18.1, sharp 0.35.5, @types/sanitize-html) | **겹침 가능**: 다른 브랜치가 의존성을 바꾸면 lock 충돌. lock 은 손으로 합치지 말고 `npm install` 로 다시 만든다 |
| B. 공용 계층 | `src/content-admin/{access,audit,errors,http,ids,requests,serial,store}.ts` | 새 파일만 |
| C. 공지 모듈 | `src/notices/{sanitize,service,routes}.ts`, `test/notice-sanitize.test.ts` | 새 파일만 |
| D. 이미지 모듈 | `src/media/{validate,storage,service,routes}.ts`, `test/media-validate.test.ts`, `test/notice-media-api.test.ts` | 새 파일만 |
| E. 통합 | `prisma/schema.prisma`(새 모델 5개 끝에 추가), `prisma/manual/20261101_content_notice_media_additive.sql`, `src/content-admin/{prisma-store,wiring}.ts`, `test/content-admin-prisma.integration.test.ts`, `docs/content-admin/*` | **겹침**: `schema.prisma` 끝부분(다른 작업도 모델을 추가하면 같은 위치). 기존 모델은 수정하지 않았다 |
| (미적용) 서버 진입 | `docs/content-admin/integration-app-ts.patch` → `src/app.ts` | **겹침**: app.ts 는 다른 작업(인증)도 고칠 가능성이 높음. 패치는 import 2줄, AppOptions 필드 1개, 등록 2줄 |

네이티브 로비(카메라·스크롤·조명)와 자체 계정 인증 파일은 건드리지 않았다. 기존 `/my/api/native/news/*` 공지(파일 기반)도 그대로다.

## 2. 인증 담당에게 필요한 연결 조건

`src/content-admin/access.ts`

```ts
type Principal = { userId: string; roles: ReadonlySet<string> };
interface PrincipalResolver { resolve(req: FastifyRequest): Promise<Principal | null>; }
const NOTICE_ADMIN_ROLE = 'content.notice.admin';
```

1. 인증 모듈이 **서버에서 확인한 세션**으로 `PrincipalResolver` 를 구현해 `registerContentAdmin(app, {principals})` 에 넘긴다.
2. `userId` 는 자체 계정의 내부 식별자(`[A-Za-z0-9_-]{1,100}`). 이메일·전화번호·카카오 ID 를 넣지 않는다.
3. `roles` 는 서버 저장소에서 읽은 역할. 요청 본문·헤더·쿼리의 role 값은 쓰지 않는다. **카카오 연동 여부로 관리자 역할을 주지 않는다.**
4. 관리자 판정은 `roles.has('content.notice.admin')` 하나뿐. 역할 부여 방법(관리 화면·DB·운영 절차)은 인증 담당이 정한다. 이 작업에서는 관리자 계정·권한을 만들지 않았다.
5. resolver 가 없으면(기본 `denyAllPrincipals`) 관리자 API 는 모두 401. resolver 가 예외를 던지거나 형식이 이상한 값을 주면 인증되지 않은 것으로 본다.
6. 시험용 가짜 인증은 **시험 파일 안에만** 있다(`test/*.test.ts`). `src/` 에는 가짜 인증·우회 스위치가 없어서 운영 빌드에서 켜질 수 없다.
7. CSRF: 관리자 API 가 쿠키 세션을 쓴다면 인증 담당이 SameSite·Origin 검사 또는 CSRF 토큰을 resolver 앞단에 둬야 한다(이 모듈은 세션 방식을 모른다).

## 3. API 명세 (모두 JSON, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`)

### 공개 (로그인 불필요, 게시된 글만)
| 메서드·경로 | 응답 |
|---|---|
| `GET /api/v1/notices?limit=1..100` | `{items:[{id,title,pinned,excerpt,publishedAt,updatedAt,imageCount}]}` 고정 글 먼저, 게시일 최신순 |
| `GET /api/v1/notices/:id` | `{id,title,pinned,bodyHtml,images:[{mediaId,alt}],publishedAt,updatedAt}` · 초안/숨김/없음은 모두 404 |
| `GET /api/v1/media/:id` | 이미지 바이트. **게시 중인 글이 현재 쓰는 활성 이미지만**, 그 외 404 |

### 관리자 (`content.notice.admin` 필요: 미인증 401, 권한 없음 403)
쓰기 요청은 모두 `requestId`(8~80자, `[A-Za-z0-9_-]`)가 필요하다. 같은 사용자·같은 requestId 재전송은 첫 결과를 다시 돌려주고 `Idempotent-Replayed: true` 헤더를 붙인다. 내용이 다르면 409 `REQUEST_ID_REUSED`.
수정 계열은 `expectedVersion` 이 현재 버전과 다르면 409 `VERSION_CONFLICT`(`currentVersion` 포함)이고 아무것도 바꾸지 않는다.
정의되지 않은 필드(role, status, createdBy 등)를 보내면 400 `UNKNOWN_FIELD`.

| 메서드·경로 | 본문 | 결과 |
|---|---|---|
| `GET /api/v1/admin/notices?status=draft\|published\|hidden` | | 관리자 목록(원문 포함) |
| `GET /api/v1/admin/notices/:id` | | `{...공개필드, status, bodySource, version, createdAt, publishedAt}` |
| `POST /api/v1/admin/notices` | `{requestId,title,body?,pinned?,images?}` | 201, 초안 v1 |
| `PUT /api/v1/admin/notices/:id` | `{requestId,expectedVersion,title,body?,pinned?,images?}` | 내용 저장(임시저장). 게시 상태는 그대로(**게시 중인 글을 고치면 바로 공개에 반영**) |
| `POST /api/v1/admin/notices/:id/publish` | `{requestId,expectedVersion}` | 게시(첫 게시 시각 유지) |
| `POST /api/v1/admin/notices/:id/hide` | `{requestId,expectedVersion}` | 숨김 |
| `POST /api/v1/admin/notices/:id/pin` | `{requestId,expectedVersion,pinned}` | 상단 고정 |
| `GET /api/v1/admin/notices/:id/revisions` | | `{items:[{version,action,status,pinned,title,imageCount,editedBy,editedAt}]}` |
| `POST /api/v1/admin/notices/:id/restore` | `{requestId,expectedVersion,revision}` | 지난 판 내용을 **새 판**으로 되살림(이력 보존, 게시 상태 유지) |
| `POST /api/v1/admin/media` | 이미지 바이트 1장, 헤더 `Idempotency-Key` | 201 `{id,mime,width,height,bytes,status,createdAt}` |
| `GET /api/v1/admin/media/:id` · `/info` | | 초안 미리보기용 바이트·정보(`private, no-store`) |
| `POST /api/v1/admin/media/:id/retire` | `{requestId}` | 현재 글·지난 판 어디서든 쓰이면 409 `MEDIA_IN_USE`. 파일은 지우지 않고 상태만 폐기 |
| `GET /api/v1/admin/audit?limit=1..200` | | 감사 기록 |

본문 규칙: 제목 1~120자, 본문 원문 최대 50,000자, 이미지 글당 최대 20장(중복 불가, 대체 텍스트 1~300자 필수), 순서는 배열 순서.

## 4. HTML·자바스크립트
- 저장할 때 서버에서 `sanitize-html`(htmlparser2 파서) 허용 목록으로 정화해 `bodyHtml` 을 만든다. 공개 응답은 `bodyHtml` 만 준다.
- 허용 태그: p br strong b em i u s blockquote ul ol li h2 h3 h4 a code pre hr. 속성은 a[href] 만(http/https/mailto), 링크에 `rel="noopener noreferrer nofollow" target="_blank"` 를 붙인다.
- script/style/iframe/object/embed/svg/math/template 등은 내용까지 제거. on* 속성, style/class/id/data-* 제거. 본문 안 `<img>` 는 허용하지 않는다(이미지는 연결 목록으로 따로 그린다).
- 관리자가 입력한 원문 `bodySource` 는 관리자 JSON 에 문자열로만 보관·반환한다. **관리자 화면은 원문을 HTML 로 그리면 안 되고, 미리보기도 `bodyHtml` 만 써야 한다.**
- 자바스크립트는 보관만 가능하고 실행 기능은 없다. 미리보기 iframe 은 관리자 로그인 세션과 다른 출처(origin)·`sandbox` 로 분리하는 별도 과제로 남긴다(이번 범위 아님).

## 5. 이미지
- 허용: PNG·JPEG·WebP. 파일 서명(앞 바이트)으로 형식을 판정하고 Content-Type·확장자는 믿지 않는다. SVG·HTML·GIF·기타는 415.
- `sharp`(libvips)로 실제 디코딩. 픽셀 한도(2,400만)·가로세로(6000)·용량(8MB)·애니메이션(여러 프레임) 거부. 큰 크기를 선언한 픽셀 폭탄은 디코딩 전에 거부.
- 다시 인코딩해 EXIF·ICC·XMP 메타데이터를 버린다(방향은 픽셀에 반영).
- 파일 이름은 서버가 만든 UUID + 형식 확장자(`<저장폴더>/<앞2글자>/<uuid>.png`). 사용자 파일 이름은 쓰지 않는다. 0640 권한, 임시 파일 후 이름 바꾸기.
- 저장 폴더가 `public/`·`dist/` 같은 정적 제공 폴더 안이면 시작을 거부한다. 응답은 API 가 정한 Content-Type + `nosniff` + `Content-Security-Policy: default-src 'none'; sandbox`.
- 업로드 후 DB 기록이 실패하면 방금 쓴 파일을 지운다. 저장 실패는 503 `STORAGE_UNAVAILABLE`, 같은 Idempotency-Key 로 다시 시도할 수 있다.
- 폐기·교체: 이미지 파일은 실제로 지우지 않는다. 교체는 새 이미지를 올려 글의 연결만 바꾸므로 지난 판 복원이 깨지지 않는다. 쓰이지 않는 파일의 실제 삭제는 별도 정리 작업으로 남긴다.
- 외부 URL 이미지를 서버가 가져오는 기능은 없다.

## 6. DB 변경안 (실행하지 않음)
`prisma/manual/20261101_content_notice_media_additive.sql` — 새 표 5개(`ContentNotice`, `ContentNoticeRevision`, `ContentMedia`, `ContentAdminRequest`, `ContentAdminAudit`)와 색인 3개. 기존 표 변경 없음. 기준 스키마로 만든 임시 DB 와 새 스키마의 `prisma migrate diff` 결과다. 실제 DB 에는 실행하지 않았다(시험용 임시 SQLite 파일에만 `db push`).

## 7. 감사 기록
작업 종류·대상 ID·관리자 userId·시각과 허용 목록 필드(version, status, pinned, restoredFrom, imageCount, bytes, mime, width, height)만 남긴다. 본문·제목·원문·비밀번호·토큰·요청 헤더·IP·이메일은 남기지 않는다. 권한 없는 시도는 감사 기록에 남기지 않는다(필요하면 인증 담당 로그로).

## 8. 알려진 한계
- 쓰기 순서 보장(SerialQueue)은 한 서버 프로세스 기준. 여러 프로세스로 늘리면 DB 트랜잭션·고유 제약 기반으로 바꿔야 한다(버전 갱신 자체는 DB 조건부 갱신이라 안전).
- 공지 저장 후 감사 기록 쓰기가 실패하면 공지는 저장됐는데 요청 기록이 안 남아, 같은 requestId 재시도가 409(버전)로 끝날 수 있다.
- 목록 요약(excerpt)은 HTML 이스케이프된 글자다(예: `&amp;`). 글자로 그릴 때 디코딩이 필요하다.
- 공개 이미지는 `public, max-age=300` 캐시라 글을 숨긴 뒤 최대 5분간 캐시에 남을 수 있다.
