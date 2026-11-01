import Fastify, {type FastifyInstance, type FastifyRequest} from 'fastify';
import sharp from 'sharp';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {NOTICE_ADMIN_ROLE, type PrincipalResolver} from '../src/content-admin/access.js';
import {SerialQueue} from '../src/content-admin/serial.js';
import {MemoryContentStore} from '../src/content-admin/store.js';
import {registerMediaRoutes} from '../src/media/routes.js';
import {MediaService} from '../src/media/service.js';
import {MemoryMediaStorage} from '../src/media/storage.js';
import {registerNoticeRoutes} from '../src/notices/routes.js';
import {NoticeService} from '../src/notices/service.js';

/**
 * 시험 전용 가짜 인증. src 에는 없다. 헤더의 시험용 세션 번호를 서버 쪽 표에서 찾는다.
 * (요청이 role 을 직접 주장하는 방식이 아니다. 실제 인증 모듈도 같은 계약으로 연결한다.)
 */
const sessions = new Map<string, {userId: string; roles: Set<string>}>([
  ['admin-session', {userId: 'admin_1', roles: new Set([NOTICE_ADMIN_ROLE])}],
  ['admin2-session', {userId: 'admin_2', roles: new Set([NOTICE_ADMIN_ROLE])}],
  ['user-session', {userId: 'player_1', roles: new Set(['player'])}],
  ['kakao-only-session', {userId: 'player_2', roles: new Set(['kakao.linked'])}],
]);
const testResolver: PrincipalResolver = {resolve: async (req: FastifyRequest) => sessions.get(String(req.headers['x-test-session'] ?? '')) ?? null};

const ADMIN = {'x-test-session': 'admin-session'};
const USER = {'x-test-session': 'user-session'};
let n = 0;
const rid = () => `req-${Date.now()}-${++n}-abcdef`;
const png = (w = 40, h = 30, color = '#c83232') => sharp({create: {width: w, height: h, channels: 3, background: color}}).png().toBuffer();

let app: FastifyInstance, store: MemoryContentStore, blobs: MemoryMediaStorage;

async function build(principals?: PrincipalResolver) {
  store = new MemoryContentStore();
  blobs = new MemoryMediaStorage();
  const queue = new SerialQueue();
  const server = Fastify();
  await registerNoticeRoutes(server, {service: new NoticeService(store, queue), store, principals});
  await registerMediaRoutes(server, {service: new MediaService(store, blobs, queue), principals});
  await server.ready();
  return server;
}

async function upload(data: Buffer, headers: Record<string, string> = ADMIN, contentType = 'image/png', key = rid()) {
  return app.inject({method: 'POST', url: '/api/v1/admin/media', headers: {...headers, 'content-type': contentType, 'idempotency-key': key}, payload: data});
}

async function create(body: Record<string, unknown> = {}) {
  const res = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: rid(), title: '점검 안내', body: '<p>내일 점검</p>', ...body}});
  expect(res.statusCode).toBe(201);
  return res.json();
}

const adminRoutes = (id = '00000000-0000-4000-8000-000000000000') => [
  ['GET', '/api/v1/admin/notices'],
  ['GET', `/api/v1/admin/notices/${id}`],
  ['GET', `/api/v1/admin/notices/${id}/revisions`],
  ['POST', '/api/v1/admin/notices'],
  ['PUT', `/api/v1/admin/notices/${id}`],
  ['POST', `/api/v1/admin/notices/${id}/publish`],
  ['POST', `/api/v1/admin/notices/${id}/hide`],
  ['POST', `/api/v1/admin/notices/${id}/pin`],
  ['POST', `/api/v1/admin/notices/${id}/restore`],
  ['GET', '/api/v1/admin/audit'],
  ['GET', `/api/v1/admin/media/${id}`],
  ['GET', `/api/v1/admin/media/${id}/info`],
  ['POST', `/api/v1/admin/media/${id}/retire`],
] as const;

describe('인증 연결 전 기본 거부', () => {
  beforeEach(async () => { app = await build(); });
  afterEach(async () => { await app.close(); });
  it('principals 를 넘기지 않으면 관리자 API 는 모두 401', async () => {
    for (const [method, url] of adminRoutes()) {
      const res = await app.inject({method, url, headers: ADMIN, payload: method === 'GET' ? undefined : {requestId: rid()}});
      expect(res.statusCode, `${method} ${url}`).toBe(401);
    }
    expect((await upload(await png(), ADMIN)).statusCode).toBe(401);
  });
});

describe('공지·이미지 API', () => {
  beforeEach(async () => { app = await build(testResolver); });
  afterEach(async () => { await app.close(); });

  it('권한 없음 401 · 일반 사용자 403 · 카카오 연동만 된 계정도 403', async () => {
    for (const [method, url] of adminRoutes()) {
      const payload = method === 'GET' ? undefined : {requestId: rid()};
      expect((await app.inject({method, url, payload})).statusCode, `${method} ${url}`).toBe(401);
      expect((await app.inject({method, url, headers: USER, payload})).statusCode, `${method} ${url}`).toBe(403);
      expect((await app.inject({method, url, headers: {'x-test-session': 'kakao-only-session'}, payload})).statusCode).toBe(403);
    }
    expect((await upload(await png(), {})).statusCode).toBe(401);
    expect((await upload(await png(), USER)).statusCode).toBe(403);
    expect(blobs.files.size).toBe(0);
  });

  it('클라이언트가 보낸 role·상태 값은 무시하거나 거부한다', async () => {
    const res = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: {...USER, 'x-role': NOTICE_ADMIN_ROLE}, payload: {requestId: rid(), title: 't', role: NOTICE_ADMIN_ROLE}});
    expect(res.statusCode).toBe(403);
    const injected = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: rid(), title: 't', status: 'published', createdBy: 'x'}});
    expect(injected.statusCode).toBe(400);
    expect(injected.json()).toMatchObject({error: 'UNKNOWN_FIELD'});
  });

  it('작성(초안)·수정·게시·숨김·고정과 공개 노출 규칙', async () => {
    const draft = await create();
    expect(draft).toMatchObject({status: 'draft', version: 1, publishedAt: null});
    // 초안은 공개 목록·상세에 없다
    expect((await app.inject({url: '/api/v1/notices'})).json().items).toEqual([]);
    expect((await app.inject({url: `/api/v1/notices/${draft.id}`})).statusCode).toBe(404);

    const saved = await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${draft.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: '점검 안내(수정)', body: '<p>수정</p>'}});
    expect(saved.json()).toMatchObject({version: 2, status: 'draft', title: '점검 안내(수정)'});
    expect((await app.inject({url: `/api/v1/notices/${draft.id}`})).statusCode).toBe(404);

    const published = (await app.inject({method: 'POST', url: `/api/v1/admin/notices/${draft.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2}})).json();
    expect(published).toMatchObject({status: 'published', version: 3});
    const detail = await app.inject({url: `/api/v1/notices/${draft.id}`});
    expect(detail.statusCode).toBe(200);
    expect(detail.json()).toMatchObject({title: '점검 안내(수정)', bodyHtml: '<p>수정</p>'});
    expect(detail.json()).not.toHaveProperty('bodySource');
    expect(detail.json()).not.toHaveProperty('status');
    expect(detail.headers['x-content-type-options']).toBe('nosniff');

    const other = await create({title: '두 번째'});
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${other.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1}});
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${draft.id}/pin`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 3, pinned: true}});
    const list = (await app.inject({url: '/api/v1/notices'})).json().items;
    expect(list.map((i: {id: string}) => i.id)).toEqual([draft.id, other.id]);
    expect(list[0].pinned).toBe(true);

    const hidden = (await app.inject({method: 'POST', url: `/api/v1/admin/notices/${draft.id}/hide`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 4}})).json();
    expect(hidden.status).toBe('hidden');
    expect((await app.inject({url: `/api/v1/notices/${draft.id}`})).statusCode).toBe(404);
    expect((await app.inject({url: '/api/v1/notices'})).json().items.map((i: {id: string}) => i.id)).toEqual([other.id]);
    // 관리자 목록에서는 숨긴 글도 보인다
    expect((await app.inject({url: '/api/v1/admin/notices?status=hidden', headers: ADMIN})).json().items).toHaveLength(1);
    // 잘못된 id·없는 id 도 같은 404
    expect((await app.inject({url: '/api/v1/notices/../../etc/passwd'})).statusCode).toBe(404);
  });

  it('악성 HTML 은 저장 시 서버에서 정화되고, 원문은 관리자 JSON 문자열로만 남는다', async () => {
    const evil = '<p onclick="alert(1)">안녕<script>alert(2)</script><img src=x onerror=alert(3)><a href="javascript:alert(4)">x</a></p>';
    const notice = await create({body: evil});
    expect(notice.bodyHtml).toBe('<p>안녕<a rel="noopener noreferrer nofollow" target="_blank">x</a></p>');
    expect(notice.bodySource).toBe(evil);
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1}});
    const pub = await app.inject({url: `/api/v1/notices/${notice.id}`});
    expect(pub.headers['content-type']).toContain('application/json');
    expect(pub.body).not.toMatch(/<script|onerror|onclick|javascript:/i);
    expect((await app.inject({url: '/api/v1/admin/notices/' + notice.id, headers: ADMIN})).headers['content-security-policy']).toContain("default-src 'none'");
  });

  it('수정 충돌은 409 이고 아무것도 바뀌지 않는다 · 동시 수정은 하나만 성공', async () => {
    const notice = await create();
    const first = await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: 'A'}});
    expect(first.statusCode).toBe(200);
    const stale = await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: {'x-test-session': 'admin2-session'}, payload: {requestId: rid(), expectedVersion: 1, title: 'B'}});
    expect(stale.statusCode).toBe(409);
    expect(stale.json()).toEqual({error: 'VERSION_CONFLICT', currentVersion: 2});
    const results = await Promise.all(['C', 'D', 'E'].map(title => app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2, title}})));
    expect(results.map(r => r.statusCode).sort()).toEqual([200, 409, 409]);
    const final = (await app.inject({url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN})).json();
    expect(final.version).toBe(3);
    expect((await app.inject({url: `/api/v1/admin/notices/${notice.id}/revisions`, headers: ADMIN})).json().items.map((r: {version: number}) => r.version)).toEqual([1, 2, 3]);
  });

  it('수정 이력과 이전 버전 복원(이력은 지우지 않고 새 판으로)', async () => {
    const notice = await create({title: '원래 제목', body: '<p>원래</p>'});
    await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: '바뀐 제목', body: '<p>바뀜</p>', pinned: true}});
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2}});
    const restored = await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/restore`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 3, revision: 1}});
    expect(restored.statusCode).toBe(200);
    expect(restored.json()).toMatchObject({version: 4, title: '원래 제목', bodyHtml: '<p>원래</p>', pinned: false, status: 'published'});
    const history = (await app.inject({url: `/api/v1/admin/notices/${notice.id}/revisions`, headers: ADMIN})).json().items;
    expect(history.map((r: {version: number; action: string}) => `${r.version}:${r.action}`)).toEqual(['1:create', '2:update', '3:publish', '4:restore']);
    expect((await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/restore`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 4, revision: 99}})).statusCode).toBe(404);
    expect((await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/restore`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, revision: 2}})).statusCode).toBe(409);
  });

  it('중복 요청: 같은 requestId 재전송은 한 번만 반영, 내용이 다르면 409', async () => {
    const requestId = rid();
    const payload = {requestId, title: '한 번만', body: ''};
    const [a, b] = await Promise.all([
      app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload}),
      app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload}),
    ]);
    expect([a.statusCode, b.statusCode]).toEqual([201, 201]);
    expect(a.json().id).toBe(b.json().id);
    expect([a.headers['idempotent-replayed'], b.headers['idempotent-replayed']].filter(Boolean)).toHaveLength(1);
    expect((await app.inject({url: '/api/v1/admin/notices', headers: ADMIN})).json().items).toHaveLength(1);
    const reused = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {...payload, title: '다른 내용'}});
    expect(reused.statusCode).toBe(409);
    expect(reused.json().error).toBe('REQUEST_ID_REUSED');
    expect((await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {title: 'requestId 없음'}})).statusCode).toBe(400);
  });

  it('저장 실패는 일부만 반영되지 않고, 같은 요청으로 다시 시도하면 성공한다', async () => {
    const requestId = rid();
    store.failNextWrite = true;
    const failed = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId, title: '실패 후 재시도'}});
    expect(failed.statusCode).toBe(500);
    expect(failed.json()).toEqual({error: 'INTERNAL'});
    expect((await app.inject({url: '/api/v1/admin/notices', headers: ADMIN})).json().items).toHaveLength(0);
    const retried = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId, title: '실패 후 재시도'}});
    expect(retried.statusCode).toBe(201);

    const key = rid();
    blobs.failNextPut = true;
    expect((await upload(await png(), ADMIN, 'image/png', key)).statusCode).toBe(503);
    expect(blobs.files.size).toBe(0);
    store.failNextWrite = true;
    expect((await upload(await png(), ADMIN, 'image/png', key)).statusCode).toBe(503);
    expect(blobs.files.size).toBe(0);
    expect((await upload(await png(), ADMIN, 'image/png', key)).statusCode).toBe(201);
    expect(blobs.files.size).toBe(1);
  });

  it('이미지 여러 장 업로드·순서·대체 텍스트·게시글 연결, 초안 이미지는 공개되지 않는다', async () => {
    const ids: string[] = [];
    for (const color of ['#ff0000', '#00ff00', '#0000ff']) {
      const res = await upload(await png(20, 20, color));
      expect(res.statusCode).toBe(201);
      expect(res.json()).toMatchObject({mime: 'image/png', width: 20, height: 20, status: 'active'});
      ids.push(res.json().id);
    }
    const notice = await create({images: ids.map((mediaId, i) => ({mediaId, alt: `그림 ${i + 1}`}))});
    expect(notice.images.map((i: {alt: string}) => i.alt)).toEqual(['그림 1', '그림 2', '그림 3']);
    // 초안 이미지: 공개 404, 관리자 미리보기는 가능
    expect((await app.inject({url: `/api/v1/media/${ids[0]}`})).statusCode).toBe(404);
    const preview = await app.inject({url: `/api/v1/admin/media/${ids[0]}`, headers: ADMIN});
    expect(preview.statusCode).toBe(200);
    expect(preview.headers['content-type']).toBe('image/png');
    expect(preview.headers['cache-control']).toContain('no-store');

    const reordered = await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: notice.title, body: notice.bodySource, images: [{mediaId: ids[2], alt: '파랑'}, {mediaId: ids[0], alt: '빨강'}]}});
    expect(reordered.json().images).toEqual([{mediaId: ids[2], alt: '파랑'}, {mediaId: ids[0], alt: '빨강'}]);
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2}});
    const pub = await app.inject({url: `/api/v1/media/${ids[2]}`});
    expect(pub.statusCode).toBe(200);
    expect(pub.headers['content-type']).toBe('image/png');
    expect(pub.headers['x-content-type-options']).toBe('nosniff');
    expect(pub.headers['content-security-policy']).toContain('sandbox');
    // 게시글에서 빠진 이미지(ids[1])는 공개되지 않는다
    expect((await app.inject({url: `/api/v1/media/${ids[1]}`})).statusCode).toBe(404);

    // 대체 텍스트 필수 · 중복 · 없는 이미지 · 개수 제한
    const bad = async (images: unknown) => (await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: rid(), title: 'x', images}})).statusCode;
    expect(await bad([{mediaId: ids[0]}])).toBe(400);
    expect(await bad([{mediaId: ids[0], alt: 'a'}, {mediaId: ids[0], alt: 'b'}])).toBe(400);
    expect(await bad([{mediaId: '00000000-0000-4000-8000-000000000000', alt: 'a'}])).toBe(409);
    expect(await bad([{mediaId: '../x', alt: 'a'}])).toBe(400);
    expect(await bad(Array.from({length: 21}, () => ({mediaId: ids[0], alt: 'a'})))).toBe(400);
  });

  it('위장·과대·비지원 이미지 업로드는 거부되고 파일이 남지 않는다', async () => {
    expect((await upload(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'), ADMIN, 'image/png')).statusCode).toBe(415);
    expect((await upload(Buffer.from('<html><body>hi</body></html>'), ADMIN, 'image/jpeg')).statusCode).toBe(415);
    expect((await upload(Buffer.from('<svg/>'), ADMIN, 'image/svg+xml')).statusCode).toBe(415);
    expect((await upload(Buffer.from('<p>x</p>'), ADMIN, 'text/html')).statusCode).toBe(415);
    const tooBig = Buffer.concat([await png(), Buffer.alloc(9 * 1024 * 1024)]);
    expect((await upload(tooBig, ADMIN, 'application/octet-stream')).statusCode).toBe(413);
    // 확장자/MIME 이 jpeg 라고 주장해도 실제 PNG 면 PNG 로 저장한다
    const lied = await upload(await png(), ADMIN, 'image/jpeg');
    expect(lied.statusCode).toBe(201);
    expect(lied.json().mime).toBe('image/png');
    expect(blobs.files.size).toBe(1);
    expect([...blobs.files.keys()][0]).toMatch(/^[0-9a-f-]{36}\.png$/);
  });

  it('이미지 폐기: 현재 글이나 복원 이력이 참조하면 거부, 아무도 안 쓰면 폐기(파일은 보존)', async () => {
    const used = (await upload(await png())).json().id;
    const unused = (await upload(await png(10, 10))).json().id;
    const notice = await create({images: [{mediaId: used, alt: 'a'}]});
    expect((await app.inject({method: 'POST', url: `/api/v1/admin/media/${used}/retire`, headers: ADMIN, payload: {requestId: rid()}})).statusCode).toBe(409);
    // 글에서 빼도 이력이 참조하므로 여전히 거부
    await app.inject({method: 'PUT', url: `/api/v1/admin/notices/${notice.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: notice.title, images: []}});
    const still = await app.inject({method: 'POST', url: `/api/v1/admin/media/${used}/retire`, headers: ADMIN, payload: {requestId: rid()}});
    expect(still.statusCode).toBe(409);
    expect(still.json()).toMatchObject({error: 'MEDIA_IN_USE', current: 0, history: 1});
    const retired = await app.inject({method: 'POST', url: `/api/v1/admin/media/${unused}/retire`, headers: ADMIN, payload: {requestId: rid()}});
    expect(retired.json().status).toBe('retired');
    expect(blobs.files.size).toBe(2);
    // 폐기된 이미지는 새 글에 붙일 수 없다
    expect((await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: rid(), title: 'x', images: [{mediaId: unused, alt: 'a'}]}})).statusCode).toBe(409);
  });

  it('감사 기록: 관리자 작업만, 본문·토큰·세션 값은 남기지 않는다', async () => {
    const secretBody = '<p>비밀번호는 hunter2 토큰 sk-live-123</p>';
    const notice = await create({body: secretBody});
    await upload(await png());
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1}});
    await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: USER, payload: {requestId: rid(), title: '거부됨'}});
    const audit = await app.inject({url: '/api/v1/admin/audit', headers: ADMIN});
    const items = audit.json().items;
    expect(items.map((e: {action: string}) => e.action)).toEqual(['notice.publish', 'media.upload', 'notice.create']);
    expect(items.every((e: {actorId: string}) => e.actorId === 'admin_1')).toBe(true);
    const text = JSON.stringify(items);
    expect(text).not.toMatch(/hunter2|sk-live|admin-session|x-test-session|비밀번호/);
    expect(Object.keys(items[0].detail).every(k => ['version', 'status', 'pinned', 'restoredFrom', 'imageCount', 'bytes', 'mime', 'width', 'height'].includes(k))).toBe(true);
  });
});
