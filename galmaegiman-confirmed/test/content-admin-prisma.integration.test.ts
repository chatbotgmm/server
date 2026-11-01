import {PrismaClient} from '@prisma/client';
import Fastify, {type FastifyInstance, type FastifyRequest} from 'fastify';
import sharp from 'sharp';
import {spawnSync} from 'node:child_process';
import {closeSync, mkdtempSync, openSync, readdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {afterAll, beforeAll, describe, expect, it} from 'vitest';
import {NOTICE_ADMIN_ROLE, type PrincipalResolver} from '../src/content-admin/access.js';
import {registerContentAdmin} from '../src/content-admin/wiring.js';

// 새 임시 SQLite 파일에만 스키마를 적용한다(실제 DB 는 건드리지 않음).
const dir = mkdtempSync(join(tmpdir(), 'content-admin-it-'));
const dbFile = join(dir, 'test.db');
closeSync(openSync(dbFile, 'wx', 0o600));
const url = `file:${dbFile}`;
const mediaRoot = join(dir, 'media-store');

const resolver: PrincipalResolver = {
  resolve: async (req: FastifyRequest) => (req.headers['x-test-session'] === 'admin' ? {userId: 'admin_it', roles: new Set([NOTICE_ADMIN_ROLE])} : req.headers['x-test-session'] === 'user' ? {userId: 'user_it', roles: new Set(['player'])} : null),
};
const ADMIN = {'x-test-session': 'admin'};
let n = 0;
const rid = () => `it-${Date.now()}-${++n}-xyz`;
let db: PrismaClient, app: FastifyInstance;

beforeAll(async () => {
  const push = spawnSync(process.execPath, ['node_modules/prisma/build/index.js', 'db', 'push', '--skip-generate'], {env: {...process.env, DATABASE_URL: url}, encoding: 'utf8'});
  if (push.status !== 0) throw new Error(push.stderr || push.stdout);
  db = new PrismaClient({datasources: {db: {url}}});
  app = Fastify();
  await registerContentAdmin(app, {db, mediaRoot, staticRoots: [join(dir, 'public')], principals: resolver});
  await app.ready();
}, 120_000);

afterAll(async () => {
  await app?.close();
  await db?.$disconnect();
});

describe('Prisma 저장소 + 디스크 이미지 저장 통합', () => {
  it('공지 전 과정과 버전 충돌·복원·중복 요청이 DB 에서도 같다', async () => {
    const created = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: 'dup-request-0001', title: '통합', body: '<p onmouseover="x()">본문</p>'}});
    expect(created.statusCode).toBe(201);
    const id = created.json().id;
    expect(created.json().bodyHtml).toBe('<p>본문</p>');
    const replay = await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: 'dup-request-0001', title: '통합', body: '<p onmouseover="x()">본문</p>'}});
    expect(replay.json().id).toBe(id);
    expect(await db.contentNotice.count()).toBe(1);

    const [a, b] = await Promise.all(['A', 'B'].map(title => app.inject({method: 'PUT', url: `/api/v1/admin/notices/${id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title}})));
    expect([a.statusCode, b.statusCode].sort()).toEqual([200, 409]);
    expect(await db.contentNoticeRevision.count({where: {noticeId: id}})).toBe(2);

    expect((await app.inject({url: `/api/v1/notices/${id}`})).statusCode).toBe(404);
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2}});
    expect((await app.inject({url: `/api/v1/notices/${id}`})).statusCode).toBe(200);
    const restored = await app.inject({method: 'POST', url: `/api/v1/admin/notices/${id}/restore`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 3, revision: 1}});
    expect(restored.json()).toMatchObject({version: 4, title: '통합', status: 'published'});
    expect(await db.contentNoticeRevision.count({where: {noticeId: id}})).toBe(4);
  });

  it('이미지: 디스크에 서버 이름으로 저장, 초안은 비공개, 참조 중 폐기 거부, 감사 기록 저장', async () => {
    const data = await sharp({create: {width: 30, height: 20, channels: 3, background: '#123456'}}).withMetadata({exif: {IFD0: {Artist: 'private-name'}}}).jpeg().toBuffer();
    const up = await app.inject({method: 'POST', url: '/api/v1/admin/media', headers: {...ADMIN, 'content-type': 'image/jpeg', 'idempotency-key': rid()}, payload: data});
    expect(up.statusCode).toBe(201);
    const mediaId = up.json().id;
    const sub = readdirSync(join(mediaRoot, mediaId.slice(0, 2)));
    expect(sub).toEqual([`${mediaId}.jpg`]);
    const notice = (await app.inject({method: 'POST', url: '/api/v1/admin/notices', headers: ADMIN, payload: {requestId: rid(), title: '그림', images: [{mediaId, alt: '파란 상자'}]}})).json();
    expect((await app.inject({url: `/api/v1/media/${mediaId}`})).statusCode).toBe(404);
    await app.inject({method: 'POST', url: `/api/v1/admin/notices/${notice.id}/publish`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1}});
    const pub = await app.inject({url: `/api/v1/media/${mediaId}`});
    expect(pub.statusCode).toBe(200);
    expect(pub.rawPayload.includes(Buffer.from('private-name'))).toBe(false);
    expect((await app.inject({method: 'POST', url: `/api/v1/admin/media/${mediaId}/retire`, headers: ADMIN, payload: {requestId: rid()}})).statusCode).toBe(409);
    expect((await app.inject({method: 'POST', url: '/api/v1/admin/media', headers: {'x-test-session': 'user', 'content-type': 'image/jpeg', 'idempotency-key': rid()}, payload: data})).statusCode).toBe(403);
    const audit = await db.contentAdminAudit.findMany();
    expect(audit.length).toBeGreaterThan(0);
    expect(JSON.stringify(audit)).not.toMatch(/private-name|본문|x-test-session/);
  });

  it('정적 제공 폴더 안을 이미지 저장 위치로 쓰면 시작을 거부한다', async () => {
    const other = Fastify();
    await expect(registerContentAdmin(other, {db, mediaRoot: join(dir, 'public', 'uploads'), staticRoots: [join(dir, 'public')], principals: resolver})).rejects.toThrow('static web root');
    await other.close();
  });
});
