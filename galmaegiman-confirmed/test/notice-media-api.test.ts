import Fastify, {type FastifyInstance, type FastifyRequest} from 'fastify';
import sharp from 'sharp';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {NOTICE_ADMIN_ROLE, type PrincipalResolver} from '../src/content-admin/access.js';
import {accountPrincipalResolver} from '../src/content-admin/account-bridge.js';
import {originCsrfPolicy, type CsrfPolicy} from '../src/content-admin/csrf.js';
import {MemoryContentStore} from '../src/content-admin/store.js';
import {registerMediaRoutes} from '../src/media/routes.js';
import {MediaService} from '../src/media/service.js';
import {MemoryMediaStorage} from '../src/media/storage.js';
import {registerNoticeRoutes} from '../src/notices/routes.js';
import {NoticeService} from '../src/notices/service.js';

/**
 * 시험 전용 가짜 계정 모듈. src 에는 없다.
 * x-test-session 은 '서버가 검증한 세션'을 흉내 내는 시험용 값이며, 역할은 서버 쪽 표(permissions)에서만 정한다.
 */
const accounts = new Map([['admin-session', 'a100'], ['admin2-session', 'a101'], ['user-session', 'a200'], ['kakao-session', 'a300']]);
const permissions = new Map<string, Set<string>>([['a100', new Set(['notice:write'])], ['a101', new Set(['notice:write'])], ['a200', new Set()], ['a300', new Set(['kakao.linked'])]]);
let accountOutage = false, permissionOutage = false;
const resolver: PrincipalResolver = accountPrincipalResolver({
  accounts: {verifiedAccountId: async (req: FastifyRequest) => { if (accountOutage) throw new Error('session store down'); return accounts.get(String(req.headers['x-test-session'] ?? '')) ?? null; }},
  permissions: {hasPermission: async (id, p) => { if (permissionOutage) throw new Error('permission store down'); return permissions.get(id)?.has(p) ?? false; }},
});

const ORIGIN = 'https://admin.gmm.test';
const ADMIN = {'x-test-session': 'admin-session', origin: ORIGIN};
const ADMIN2 = {'x-test-session': 'admin2-session', origin: ORIGIN};
const USER = {'x-test-session': 'user-session', origin: ORIGIN};
let n = 0;
const rid = () => `req-${Date.now()}-${++n}-abcdef`;
const png = (w = 40, h = 30, color = '#c83232') => sharp({create: {width: w, height: h, channels: 3, background: color}}).png().toBuffer();

let app: FastifyInstance, store: MemoryContentStore, blobs: MemoryMediaStorage;

async function build(principals?: PrincipalResolver, csrfOption: CsrfPolicy | null = originCsrfPolicy([ORIGIN])) {
  const csrf = csrfOption ?? undefined;
  store = new MemoryContentStore();
  blobs = new MemoryMediaStorage();
  const server = Fastify();
  await registerNoticeRoutes(server, {service: new NoticeService(store), store, principals, csrf});
  await registerMediaRoutes(server, {service: new MediaService(store, blobs), principals, csrf});
  await server.ready();
  return server;
}

const upload = (data: Buffer, headers: Record<string, string> = ADMIN, contentType = 'image/png', key = rid()) =>
  app.inject({method: 'POST', url: '/api/v1/admin/media', headers: {...headers, 'content-type': contentType, 'idempotency-key': key}, payload: data});
const call = (method: 'POST' | 'PUT', url: string, payload: Record<string, unknown>, headers: Record<string, string> = ADMIN) => app.inject({method, url, headers, payload: {requestId: rid(), ...payload}});
async function create(body: Record<string, unknown> = {}) {
  const res = await call('POST', '/api/v1/admin/notices', {title: '점검 안내', body: '<p>내일 점검</p>', ...body});
  expect(res.statusCode, res.body).toBe(201);
  return res.json();
}
const edit = (id: string, expectedVersion: number, body: Record<string, unknown>) => call('PUT', `/api/v1/admin/notices/${id}`, {expectedVersion, ...body});
const publish = (id: string, expectedVersion: number) => call('POST', `/api/v1/admin/notices/${id}/publish`, {expectedVersion});
const pub = (id: string) => app.inject({url: `/api/v1/notices/${id}`});

const Z = '00000000-0000-4000-8000-000000000000';
const adminRoutes = [
  ['GET', '/api/v1/admin/notices'], ['GET', `/api/v1/admin/notices/${Z}`], ['GET', `/api/v1/admin/notices/${Z}/revisions`],
  ['POST', '/api/v1/admin/notices'], ['PUT', `/api/v1/admin/notices/${Z}`], ['POST', `/api/v1/admin/notices/${Z}/publish`],
  ['POST', `/api/v1/admin/notices/${Z}/hide`], ['POST', `/api/v1/admin/notices/${Z}/restore`], ['GET', '/api/v1/admin/audit'],
  ['GET', `/api/v1/admin/media/${Z}`], ['GET', `/api/v1/admin/media/${Z}/info`], ['POST', `/api/v1/admin/media/${Z}/retire`],
] as const;

describe('인증 연결 전 기본 거부', () => {
  beforeEach(async () => { app = await build(undefined); });
  afterEach(async () => { await app.close(); });
  it('principals 가 없으면 관리자 API 는 모두 401, 업로드도 401', async () => {
    for (const [method, url] of adminRoutes) expect((await app.inject({method, url, headers: ADMIN, payload: method === 'GET' ? undefined : {requestId: rid()}})).statusCode, `${method} ${url}`).toBe(401);
    expect((await upload(await png())).statusCode).toBe(401);
  });
});

describe('인증·권한·CSRF', () => {
  afterEach(async () => { accountOutage = false; permissionOutage = false; await app.close(); });

  it('미인증 401 · notice:write 없는 계정 403 · 카카오 연동만 된 계정 403', async () => {
    app = await build(resolver);
    for (const [method, url] of adminRoutes) {
      const payload = method === 'GET' ? undefined : {requestId: rid()};
      expect((await app.inject({method, url, headers: {origin: ORIGIN}, payload})).statusCode, `${method} ${url}`).toBe(401);
      expect((await app.inject({method, url, headers: USER, payload})).statusCode, `${method} ${url}`).toBe(403);
      expect((await app.inject({method, url, headers: {'x-test-session': 'kakao-session', origin: ORIGIN}, payload})).statusCode).toBe(403);
    }
    expect((await upload(await png(), USER)).statusCode).toBe(403);
    expect(blobs.files.size).toBe(0);
  });

  it('클라이언트가 보낸 역할·권한 값은 무시한다', async () => {
    app = await build(resolver);
    const res = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: {...USER, 'x-role': NOTICE_ADMIN_ROLE, 'x-permissions': 'notice:write'}, payload: {requestId: rid(), title: 't'}});
    expect(res.statusCode).toBe(403);
    const injected = await call('POST', '/api/v1/admin/notices', {title: 't', role: NOTICE_ADMIN_ROLE});
    expect(injected.statusCode).toBe(400);
    expect(injected.json()).toMatchObject({error: 'UNKNOWN_FIELD', field: 'role'});
  });

  it('인증 저장소 장애는 401 이 아니라 503(Retry-After)', async () => {
    app = await build(resolver);
    accountOutage = true;
    const r1 = await app.inject({url: '/api/v1/admin/notices', headers: ADMIN});
    expect(r1.statusCode).toBe(503);
    expect(r1.json()).toEqual({error: 'AUTH_UNAVAILABLE'});
    expect(r1.headers['retry-after']).toBe('5');
    accountOutage = false; permissionOutage = true;
    expect((await call('POST', '/api/v1/admin/notices', {title: 't'})).statusCode).toBe(503);
    expect((await upload(await png())).statusCode).toBe(503);
  });

  it('형식이 잘못된 인증 결과도 503', async () => {
    app = await build({resolve: async () => ({userId: '../x', roles: new Set([NOTICE_ADMIN_ROLE])})});
    expect((await app.inject({url: '/api/v1/admin/notices', headers: ADMIN})).statusCode).toBe(503);
  });

  it('CSRF: 정책이 없으면 쓰기 403, Origin 이 다르거나 없으면 403, 읽기는 영향 없음', async () => {
    app = await build(resolver, null);
    const missing = await call('POST', '/api/v1/admin/notices', {title: 't'});
    expect(missing.statusCode).toBe(403);
    expect(missing.json().error).toBe('CSRF_POLICY_MISSING');
    expect((await app.inject({url: '/api/v1/admin/notices', headers: ADMIN})).statusCode).toBe(200);
    await app.close();
    app = await build(resolver);
    const evil = await call('POST', '/api/v1/admin/notices', {title: 't'}, {...ADMIN, origin: 'https://evil.example'});
    expect(evil.json().error).toBe('CSRF_REJECTED');
    const noOrigin = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: {'x-test-session': 'admin-session'}, payload: {requestId: rid(), title: 't'}});
    expect(noOrigin.statusCode).toBe(403);
    const sameSite = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: {'x-test-session': 'admin-session', 'sec-fetch-site': 'same-origin'}, payload: {requestId: rid(), title: 't'}});
    expect(sameSite.statusCode).toBe(201);
    expect((await upload(await png(), {'x-test-session': 'admin-session', origin: 'https://evil.example'})).statusCode).toBe(403);
    expect(blobs.files.size).toBe(0);
  });
});

describe('공지·이미지', () => {
  beforeEach(async () => { app = await build(resolver); });
  afterEach(async () => { await app.close(); });

  it('작업 판과 공개 판 분리: 임시저장·복원은 공개 내용을 바꾸지 않고 게시할 때만 바뀐다', async () => {
    const n1 = await create({title: '원래 제목', body: '<p>원래</p>'});
    expect(n1).toMatchObject({status: 'draft', version: 1, revision: 1, publishedRevision: null, published: null});
    expect((await pub(n1.id)).statusCode).toBe(404);

    expect((await publish(n1.id, 1)).json()).toMatchObject({status: 'published', publishedRevision: 1, hasUnpublishedChanges: false});
    expect((await pub(n1.id)).json()).toMatchObject({title: '원래 제목', bodyHtml: '<p>원래</p>'});

    const saved = (await edit(n1.id, 2, {title: '수정 중', body: '<p>아직 비공개</p>', pinned: true})).json();
    expect(saved).toMatchObject({version: 3, revision: 2, publishedRevision: 1, hasUnpublishedChanges: true, status: 'published'});
    expect(saved.working.title).toBe('수정 중');
    expect(saved.published.title).toBe('원래 제목');
    const after = (await pub(n1.id)).json();
    expect(after).toMatchObject({title: '원래 제목', bodyHtml: '<p>원래</p>', pinned: false});
    expect(JSON.stringify((await app.inject({url: '/api/v1/notices'})).json())).not.toContain('수정 중');

    const restored = (await call('POST', `/api/v1/admin/notices/${n1.id}/restore`, {expectedVersion: 3, revision: 1})).json();
    expect(restored).toMatchObject({revision: 3, publishedRevision: 1, status: 'published'});
    expect(restored.working.title).toBe('원래 제목');
    expect((await pub(n1.id)).json().title).toBe('원래 제목');
    // 다시 수정 후 명시적 게시 → 그때 공개 판 교체
    await edit(n1.id, 4, {title: '최종', body: '<p>새 공지</p>', pinned: true});
    expect((await pub(n1.id)).json().title).toBe('원래 제목');
    expect((await publish(n1.id, 5)).json()).toMatchObject({publishedRevision: 4, hasUnpublishedChanges: false});
    expect((await pub(n1.id)).json()).toMatchObject({title: '최종', pinned: true});
    // 지금 작업 판이 아닌 판을 게시하려 하면 거부
    expect((await call('POST', `/api/v1/admin/notices/${n1.id}/publish`, {expectedVersion: 6, revision: 2})).statusCode).toBe(409);
    const history = (await app.inject({url: `/api/v1/admin/notices/${n1.id}/revisions`, headers: ADMIN})).json().items;
    expect(history.map((r: {revision: number; action: string}) => `${r.revision}:${r.action}`)).toEqual(['1:create', '2:edit', '3:restore', '4:edit']);
    expect(history.find((r: {isPublished: boolean}) => r.isPublished).revision).toBe(4);
  });

  it('숨김은 공개를 멈추고, 상단 고정은 게시해야 공개 목록에 반영된다', async () => {
    const a = await create({title: 'A'}), b = await create({title: 'B'});
    await publish(a.id, 1); await publish(b.id, 1);
    await edit(a.id, 2, {title: 'A', pinned: true});
    const before = (await app.inject({url: '/api/v1/notices'})).json().items;
    expect(before.find((i: {id: string}) => i.id === a.id).pinned).toBe(false);
    await publish(a.id, 3);
    const list = (await app.inject({url: '/api/v1/notices'})).json().items;
    expect(list[0]).toMatchObject({id: a.id, pinned: true});
    expect((await call('POST', `/api/v1/admin/notices/${a.id}/hide`, {expectedVersion: 4})).json().status).toBe('hidden');
    expect((await pub(a.id)).statusCode).toBe(404);
    expect((await app.inject({url: '/api/v1/notices'})).json().items.map((i: {id: string}) => i.id)).toEqual([b.id]);
    expect((await app.inject({url: '/api/v1/admin/notices?status=hidden', headers: ADMIN})).json().items).toHaveLength(1);
  });

  it('악성 HTML·스크립트 원문: 서버 정화, 원문은 관리자 JSON 문자열로만, 공개 응답에 스크립트 원문 없음', async () => {
    const evil = '<p onclick="alert(1)">안녕<script>alert(2)</script><img src=x onerror=alert(3)><a href="javascript:alert(4)">x</a></p>';
    const n1 = await create({body: evil, scriptSource: 'document.cookie="x";alert(5)'});
    expect(n1.working.bodyHtml).toBe('<p>안녕<a rel="noopener noreferrer nofollow" target="_blank">x</a></p>');
    expect(n1.working.bodySource).toBe(evil);
    expect(n1.working.scriptSource).toBe('document.cookie="x";alert(5)');
    await publish(n1.id, 1);
    const res = await pub(n1.id);
    expect(res.body).not.toMatch(/<script|onerror|onclick|javascript:|document\.cookie|scriptSource|bodySource/i);
    expect(res.headers['content-security-policy']).toContain("default-src 'none'");
  });

  it('본문 이미지 삽입은 연결된 이미지만, 주소는 서버가 붙인다', async () => {
    const [m1, m2] = [(await upload(await png())).json().id, (await upload(await png(10, 10))).json().id];
    const body = `<p>그림</p><img data-media-id="${m1}" alt="첫 그림" src="javascript:alert(1)" onerror="x"><img data-media-id="${m2}" alt="연결 안 됨">`;
    const n1 = await create({body, images: [{mediaId: m1, alt: '첫 그림'}]});
    expect(n1.working.bodyHtml).toBe(`<p>그림</p><img data-media-id="${m1}" src="/api/v1/admin/media/${m1}" alt="첫 그림" loading="lazy" />`);
    await publish(n1.id, 1);
    const p = (await pub(n1.id)).json();
    expect(p.bodyHtml).toBe(`<p>그림</p><img data-media-id="${m1}" src="/api/v1/media/${m1}" alt="첫 그림" loading="lazy" />`);
    expect(p.images).toEqual([{mediaId: m1, alt: '첫 그림', url: `/api/v1/media/${m1}`}]);
    expect((await app.inject({url: `/api/v1/media/${m1}`})).statusCode).toBe(200);
    expect((await app.inject({url: `/api/v1/media/${m2}`})).statusCode).toBe(404);
  });

  it('작업 판에만 있는 이미지는 공개되지 않고, 게시 후에야 공개된다', async () => {
    const [m1, m2] = [(await upload(await png())).json().id, (await upload(await png(12, 12))).json().id];
    const n1 = await create({images: [{mediaId: m1, alt: 'a'}]});
    expect((await app.inject({url: `/api/v1/media/${m1}`})).statusCode).toBe(404);
    expect((await app.inject({url: `/api/v1/admin/media/${m1}`, headers: ADMIN})).statusCode).toBe(200);
    await publish(n1.id, 1);
    await edit(n1.id, 2, {title: n1.working.title, images: [{mediaId: m2, alt: 'b'}, {mediaId: m1, alt: 'a'}]});
    expect((await app.inject({url: `/api/v1/media/${m2}`})).statusCode).toBe(404);
    expect((await pub(n1.id)).json().images.map((i: {mediaId: string}) => i.mediaId)).toEqual([m1]);
    await publish(n1.id, 3);
    expect((await app.inject({url: `/api/v1/media/${m2}`})).statusCode).toBe(200);
    expect((await pub(n1.id)).json().images.map((i: {mediaId: string}) => i.mediaId)).toEqual([m2, m1]);
  });

  it('수정 충돌 409 · 같은 버전 동시 수정은 하나만 성공', async () => {
    const n1 = await create();
    expect((await edit(n1.id, 1, {title: 'A'})).statusCode).toBe(200);
    const stale = await call('PUT', `/api/v1/admin/notices/${n1.id}`, {expectedVersion: 1, title: 'B'}, ADMIN2);
    expect(stale.statusCode).toBe(409);
    expect(stale.json()).toEqual({error: 'VERSION_CONFLICT', currentVersion: 2});
    const results = await Promise.all(['C', 'D', 'E'].map(title => edit(n1.id, 2, {title})));
    expect(results.map(r => r.statusCode).sort()).toEqual([200, 409, 409]);
    expect((await app.inject({url: `/api/v1/admin/notices/${n1.id}/revisions`, headers: ADMIN})).json().items).toHaveLength(3);
  });

  it('중복 요청: 동시 재전송은 한 번만 반영, 내용이 다르면 409, 실패하면 기록이 남지 않아 재시도 가능', async () => {
    const payload = {requestId: rid(), title: '한 번만'};
    const [a, b] = await Promise.all([1, 2].map(() => app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload})));
    expect(a.json().id).toBe(b.json().id);
    expect([a.headers['idempotent-replayed'], b.headers['idempotent-replayed']].filter(Boolean)).toHaveLength(1);
    expect((await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {...payload, title: '다른'}})).json().error).toBe('REQUEST_ID_REUSED');

    for (const step of ['insertRevision', 'appendAudit', 'completeRequest'] as const) {
      const requestId = rid();
      const before = store.requestCount();
      store.failAt = step;
      const failed = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId, title: `실패 ${step}`}});
      expect(failed.statusCode, step).toBe(500);
      expect(store.requestCount(), step).toBe(before);
      expect((await app.inject({url: '/api/v1/admin/notices', headers: ADMIN})).json().items.some((i: {working: {title: string}}) => i.working.title === `실패 ${step}`)).toBe(false);
      const retried = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId, title: `실패 ${step}`}});
      expect(retried.statusCode, step).toBe(201);
    }
  });

  it('이미지 업로드 실패·재시도·중복: 파일과 기록이 어긋나지 않는다', async () => {
    const key = rid(), data = await png();
    blobs.failNextPut = true;
    expect((await upload(data, ADMIN, 'image/png', key)).statusCode).toBe(503);
    store.failAt = 'insertMedia';
    expect((await upload(data, ADMIN, 'image/png', key)).statusCode).toBe(503);
    expect(blobs.files.size).toBe(0);
    const ok = await upload(data, ADMIN, 'image/png', key);
    expect(ok.statusCode).toBe(201);
    const again = await upload(data, ADMIN, 'image/png', key);
    expect(again.headers['idempotent-replayed']).toBe('true');
    expect(again.json().id).toBe(ok.json().id);
    expect(blobs.files.size).toBe(1);
  });

  it('위장·과대·비지원 이미지는 거부되고 파일이 남지 않는다', async () => {
    expect((await upload(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'), ADMIN, 'image/png')).statusCode).toBe(415);
    expect((await upload(Buffer.from('<html><body>hi</body></html>'), ADMIN, 'image/jpeg')).statusCode).toBe(415);
    expect((await upload(Buffer.from('<svg/>'), ADMIN, 'image/svg+xml')).statusCode).toBe(415);
    expect((await upload(Buffer.concat([await png(), Buffer.alloc(9 * 1024 * 1024)]), ADMIN, 'application/octet-stream')).statusCode).toBe(413);
    const lied = await upload(await png(), ADMIN, 'image/jpeg');
    expect(lied.json().mime).toBe('image/png');
    expect([...blobs.files.keys()]).toEqual([`${lied.json().id}.png`]);
  });

  it('이미지 폐기: 작업·공개·지난 판 어디서든 참조하면 거부, 폐기 후에는 연결 불가', async () => {
    const used = (await upload(await png())).json().id, unused = (await upload(await png(10, 10))).json().id;
    const n1 = await create({images: [{mediaId: used, alt: 'a'}]});
    await edit(n1.id, 1, {title: n1.working.title, images: []});
    const res = await call('POST', `/api/v1/admin/media/${used}/retire`, {});
    expect(res.statusCode).toBe(409);
    expect(res.json()).toMatchObject({error: 'MEDIA_IN_USE', revisions: 1});
    // 지난 판의 이미지로 복원할 수 있다
    expect((await call('POST', `/api/v1/admin/notices/${n1.id}/restore`, {expectedVersion: 2, revision: 1})).json().working.images).toEqual([{mediaId: used, alt: 'a', url: `/api/v1/admin/media/${used}`}]);
    expect((await call('POST', `/api/v1/admin/media/${unused}/retire`, {})).json().status).toBe('retired');
    expect((await call('POST', '/api/v1/admin/notices', {title: 'x', images: [{mediaId: unused, alt: 'a'}]})).statusCode).toBe(409);
  });

  it('감사 기록: 같은 트랜잭션으로 남고, 본문·스크립트·세션 값은 없다', async () => {
    const n1 = await create({body: '<p>비밀번호 hunter2 토큰 sk-live-1</p>', scriptSource: 'secret()'});
    await upload(await png());
    await publish(n1.id, 1);
    await call('POST', '/api/v1/admin/notices', {title: '거부됨'}, USER);
    const items = (await app.inject({url: '/api/v1/admin/audit', headers: ADMIN})).json().items;
    expect(items.map((e: {action: string}) => e.action)).toEqual(['notice.publish', 'media.upload', 'notice.create']);
    expect(items.every((e: {actorId: string}) => e.actorId === 'acct_a100')).toBe(true);
    expect(JSON.stringify(items)).not.toMatch(/hunter2|sk-live|secret\(\)|admin-session|x-test-session/);
  });
});
