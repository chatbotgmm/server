import {PrismaClient} from '@prisma/client';
import Fastify, {type FastifyInstance, type FastifyRequest} from 'fastify';
import sharp from 'sharp';
import {spawnSync} from 'node:child_process';
import {closeSync, mkdtempSync, openSync, readdirSync, readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {afterAll, beforeAll, describe, expect, it} from 'vitest';
import {NOTICE_ADMIN_ROLE, type PrincipalResolver} from '../src/content-admin/access.js';
import {originCsrfPolicy} from '../src/content-admin/csrf.js';
import {SqlContentStore} from '../src/content-admin/sql-store.js';
import type {ContentTx} from '../src/content-admin/store.js';
import {registerContentAdmin} from '../src/content-admin/wiring.js';
import {registerNoticeRoutes} from '../src/notices/routes.js';
import {NoticeService} from '../src/notices/service.js';

// 새 임시 SQLite 파일: 기준 스키마(db push) + 검토용 SQL 파일. 실제 DB 는 건드리지 않는다.
const dir = mkdtempSync(join(tmpdir(), 'content-admin-sql-'));
const dbFile = join(dir, 'test.db');
closeSync(openSync(dbFile, 'wx', 0o600));
const url = `file:${dbFile}`;
const mediaRoot = join(dir, 'media');
const ORIGIN = 'https://admin.gmm.test';
const ADMIN = {'x-test-session': 'admin', origin: ORIGIN};
const csrf = originCsrfPolicy([ORIGIN]);
const resolver: PrincipalResolver = {resolve: async (req: FastifyRequest) => (req.headers['x-test-session'] === 'admin' ? {userId: 'acct_it', roles: new Set([NOTICE_ADMIN_ROLE])} : null)};
let n = 0;
const rid = () => `sql-${Date.now()}-${++n}-xyz`;

/** 두 서버 인스턴스: 각자 PrismaClient(DB 연결)와 Fastify 를 가진다. 프로세스 내부 큐를 공유하지 않는다. */
let dbA: PrismaClient, dbB: PrismaClient, A: FastifyInstance, B: FastifyInstance;

async function instance(db: PrismaClient) {
  const app = Fastify();
  await registerContentAdmin(app, {db, mediaRoot, staticRoots: [join(dir, 'public')], admin: {principals: resolver, csrf}});
  await app.ready();
  return app;
}

const count = async (table: string, where = '1=1', ...args: unknown[]) => Number((await dbA.$queryRawUnsafe<{c: bigint}[]>(`SELECT COUNT(*) AS c FROM "${table}" WHERE ${where}`, ...args))[0].c);
const post = (app: FastifyInstance, path: string, payload: Record<string, unknown>) => app.inject({method: 'POST', url: `/api/v1/admin/${path}`, headers: ADMIN, payload});
const png = (size: number) => sharp({create: {width: size, height: size, channels: 3, background: '#2266aa'}}).png().toBuffer();
const upload = (app: FastifyInstance, data: Buffer, key = rid()) => app.inject({method: 'POST', url: '/api/v1/admin/media', headers: {...ADMIN, 'content-type': 'image/png', 'idempotency-key': key}, payload: data});

beforeAll(async () => {
  const push = spawnSync(process.execPath, ['node_modules/prisma/build/index.js', 'db', 'push', '--skip-generate'], {env: {...process.env, DATABASE_URL: url}, encoding: 'utf8'});
  if (push.status !== 0) throw new Error(push.stderr || push.stdout);
  dbA = new PrismaClient({datasources: {db: {url}}});
  dbB = new PrismaClient({datasources: {db: {url}}});
  const sql = readFileSync('prisma/manual/20261101_content_notice_media_additive.sql', 'utf8').split('\n').filter(l => !l.trim().startsWith('--')).join('\n');
  for (const statement of sql.split(';').map(s => s.trim()).filter(Boolean)) await dbA.$executeRawUnsafe(statement);
  A = await instance(dbA);
  B = await instance(dbB);
}, 120_000);

afterAll(async () => {
  await A?.close();
  await B?.close();
  await dbA?.$disconnect();
  await dbB?.$disconnect();
});

describe('SQLite 저장소: 두 서버 인스턴스', () => {
  it('같은 requestId 를 두 인스턴스가 동시에 받아도 공지·판·감사·요청 기록은 한 번만', async () => {
    const payload = {requestId: rid(), title: '동시 요청', body: '<p>한 번</p>'};
    const results = await Promise.all([post(A, 'notices', payload), post(B, 'notices', payload), post(A, 'notices', payload), post(B, 'notices', payload)]);
    expect(results.map(r => r.statusCode)).toEqual([201, 201, 201, 201]);
    expect(new Set(results.map(r => r.json().id)).size).toBe(1);
    expect(results.filter(r => r.headers['idempotent-replayed']).length).toBe(3);
    const id = results[0].json().id;
    expect(await count('ContentNotice', '"id"=?', id)).toBe(1);
    expect(await count('ContentNoticeRevision', '"noticeId"=?', id)).toBe(1);
    expect(await count('ContentAdminAudit', '"targetId"=?', id)).toBe(1);
    expect(await count('ContentAdminRequest', '"requestId"=?', payload.requestId)).toBe(1);
  });

  it('트랜잭션 중간 실패: 예약·공지·판이 모두 취소되고, 다른 인스턴스에서 같은 requestId 로 재시도하면 한 번 반영', async () => {
    // 감사 기록 단계에서 실패하는 인스턴스
    class FailingStore extends SqlContentStore {
      override transaction<T>(run: (tx: ContentTx) => Promise<T>) {
        return super.transaction(tx => run({...tx, appendAudit: async () => { throw new Error('audit storage failure'); }}));
      }
    }
    const store = new FailingStore(dbA);
    const failing = Fastify();
    await registerNoticeRoutes(failing, {service: new NoticeService(store), store, principals: resolver, csrf});
    await failing.ready();
    const payload = {requestId: rid(), title: '중간 실패'};
    const before = await count('ContentNotice');
    expect((await post(failing, 'notices', payload)).statusCode).toBe(500);
    expect(await count('ContentNotice')).toBe(before);
    expect(await count('ContentNoticeRevision', '"title"=?', '중간 실패')).toBe(0);
    expect(await count('ContentAdminRequest', '"requestId"=?', payload.requestId)).toBe(0);
    const retried = await post(B, 'notices', payload);
    expect(retried.statusCode).toBe(201);
    expect(retried.headers['idempotent-replayed']).toBeUndefined();
    expect(await count('ContentNotice')).toBe(before + 1);
    await failing.close();
  });

  it('두 인스턴스의 같은 버전 동시 수정은 하나만 성공', async () => {
    const created = (await post(A, 'notices', {requestId: rid(), title: '충돌'})).json();
    const [a, b] = await Promise.all([
      A.inject({method: 'PUT', url: `/api/v1/admin/notices/${created.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: 'A'}}),
      B.inject({method: 'PUT', url: `/api/v1/admin/notices/${created.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 1, title: 'B'}}),
    ]);
    expect([a.statusCode, b.statusCode].sort()).toEqual([200, 409]);
    expect(await count('ContentNoticeRevision', '"noticeId"=?', created.id)).toBe(2);
  });

  it('작업 판 저장은 공개 내용을 바꾸지 않는다(DB)', async () => {
    const created = (await post(A, 'notices', {requestId: rid(), title: '공개본'})).json();
    await post(B, `notices/${created.id}/publish`, {requestId: rid(), expectedVersion: 1});
    await B.inject({method: 'PUT', url: `/api/v1/admin/notices/${created.id}`, headers: ADMIN, payload: {requestId: rid(), expectedVersion: 2, title: '작업본'}});
    expect((await A.inject({url: `/api/v1/notices/${created.id}`})).json().title).toBe('공개본');
    expect((await A.inject({url: `/api/v1/admin/notices/${created.id}`, headers: ADMIN})).json()).toMatchObject({revision: 2, publishedRevision: 1, hasUnpublishedChanges: true});
  });

  it('이미지 연결과 폐기가 두 인스턴스에서 동시에 일어나도 "폐기됐는데 참조됨" 상태가 생기지 않는다', async () => {
    let attached = 0, retired = 0;
    for (let i = 0; i < 8; i++) {
      const media = (await upload(A, await png(8 + i))).json().id;
      const [link, retire] = await Promise.all([
        post(A, 'notices', {requestId: rid(), title: `경합 ${i}`, images: [{mediaId: media, alt: 'x'}]}),
        post(B, `media/${media}/retire`, {requestId: rid()}),
      ]);
      expect([link.statusCode, retire.statusCode].sort(), `round ${i}`).toEqual(link.statusCode === 201 ? [201, 409] : [200, 409]);
      if (link.statusCode === 201) attached++; else retired++;
      const status = (await dbA.$queryRawUnsafe<{status: string}[]>('SELECT "status" FROM "ContentMedia" WHERE "id"=?', media))[0].status;
      const refs = await count('ContentNoticeImageRef', '"mediaId"=?', media);
      expect(status === 'retired' && refs > 0, `round ${i}`).toBe(false);
    }
    expect(attached + retired).toBe(8);
  });

  it('참조 중인 이미지 행은 DB 외래키로 지울 수 없다(복원 이력 보호)', async () => {
    const media = (await upload(A, await png(30))).json().id;
    await post(A, 'notices', {requestId: rid(), title: '참조', images: [{mediaId: media, alt: 'x'}]});
    await expect(dbA.$executeRawUnsafe('DELETE FROM "ContentMedia" WHERE "id"=?', media)).rejects.toThrow(/FOREIGN KEY/i);
  });

  it('같은 Idempotency-Key 업로드를 두 인스턴스가 동시에 받아도 이미지 기록·파일은 하나', async () => {
    const key = rid(), data = await png(40);
    const before = readdirSync(mediaRoot, {recursive: true}).filter(f => String(f).endsWith('.png')).length;
    const [a, b] = await Promise.all([upload(A, data, key), upload(B, data, key)]);
    expect([a.statusCode, b.statusCode]).toEqual([201, 201]);
    expect(a.json().id).toBe(b.json().id);
    expect(await count('ContentMedia', '"id"=?', a.json().id)).toBe(1);
    const after = readdirSync(mediaRoot, {recursive: true}).filter(f => String(f).endsWith('.png')).length;
    expect(after - before).toBe(1);
  });
});
