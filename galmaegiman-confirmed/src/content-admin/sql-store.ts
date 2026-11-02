import type {PrismaClient} from '@prisma/client';
import type {AuditEntry} from './audit.js';
import {TransientStoreError, type ContentStore, type ContentTx, type MediaRecord, type MediaReferences, type NoticeHead, type NoticeImage, type NoticeStatus, type RequestRecord, type RevisionRecord} from './store.js';

/**
 * ContentStore 의 SQLite 구현. 이 모듈의 표(Content*)는 schema.prisma 모델 없이도 쓸 수 있게
 * 기존 PrismaClient 의 매개변수 SQL($queryRawUnsafe/$executeRawUnsafe, 값은 모두 자리표시자)로만 다룬다.
 * 표 만들기: prisma/manual/20261101_content_notice_media_additive.sql (검토 후 운영자가 적용)
 *
 * 원자성: 쓰기는 모두 PrismaClient 대화형 트랜잭션 하나 안에서 한다. 첫 문장이 요청 예약 INSERT 라서
 * 트랜잭션 시작과 동시에 SQLite 쓰기 잠금을 잡는다. 다른 서버 인스턴스의 같은 요청은 잠금 대기 후
 * ON CONFLICT DO NOTHING 으로 이미 끝난 결과를 읽는다. 잠금 시간 초과는 TransientStoreError 로 올려 처음부터 다시 실행한다.
 */
type Executor = Pick<PrismaClient, '$queryRawUnsafe' | '$executeRawUnsafe'>;
type Row = Record<string, unknown>;

const num = (v: unknown) => (v === null || v === undefined ? null : Number(v));
const date = (v: unknown) => (v === null || v === undefined ? null : v instanceof Date ? v : new Date(Number(v)));
const ms = (d: Date | null) => (d ? d.getTime() : null);

function transient(error: unknown): boolean {
  const text = `${(error as {code?: string})?.code ?? ''} ${(error as Error)?.message ?? ''}`;
  return /database is locked|SQLITE_BUSY|P2034|P1008|Unable to start a transaction in the given time|Transaction already closed|Socket timeout/i.test(text);
}

const head = (r: Row): NoticeHead => ({
  id: String(r.id), status: String(r.status) as NoticeStatus, version: Number(r.version), revision: Number(r.revision),
  publishedRevision: num(r.publishedRevision), publishedAt: date(r.publishedAt), createdAt: date(r.createdAt)!, updatedAt: date(r.updatedAt)!,
  createdBy: String(r.createdBy), updatedBy: String(r.updatedBy),
});
const media = (r: Row): MediaRecord => ({
  id: String(r.id), format: String(r.format) as MediaRecord['format'], mime: String(r.mime), width: Number(r.width), height: Number(r.height),
  bytes: Number(r.bytes), sha256: String(r.sha256), status: String(r.status) as MediaRecord['status'], createdBy: String(r.createdBy), createdAt: date(r.createdAt)!,
});

async function revisionOf(db: Executor, r: Row): Promise<RevisionRecord> {
  const imgs = await db.$queryRawUnsafe<Row[]>('SELECT "mediaId","alt" FROM "ContentNoticeImageRef" WHERE "noticeId"=? AND "revision"=? ORDER BY "position"', r.noticeId, r.revision);
  return {
    noticeId: String(r.noticeId), revision: Number(r.revision), action: String(r.action) as RevisionRecord['action'], title: String(r.title),
    pinned: Number(r.pinned) === 1 || r.pinned === true, bodySource: String(r.bodySource), bodyHtml: String(r.bodyHtml), scriptSource: String(r.scriptSource ?? ''),
    images: imgs.map(i => ({mediaId: String(i.mediaId), alt: String(i.alt)}) as NoticeImage), restoredFrom: num(r.restoredFrom), editedBy: String(r.editedBy), editedAt: date(r.editedAt)!,
  };
}

const read = {
  async notice(db: Executor, id: string) {
    const rows = await db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentNotice" WHERE "id"=?', id);
    return rows[0] ? head(rows[0]) : null;
  },
  async revision(db: Executor, noticeId: string, revision: number) {
    const rows = await db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentNoticeRevision" WHERE "noticeId"=? AND "revision"=?', noticeId, revision);
    return rows[0] ? revisionOf(db, rows[0]) : null;
  },
  async media(db: Executor, id: string) {
    const rows = await db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentMedia" WHERE "id"=?', id);
    return rows[0] ? media(rows[0]) : null;
  },
  async references(db: Executor, id: string): Promise<MediaReferences> {
    const rows = await db.$queryRawUnsafe<Row[]>(
      `SELECT COUNT(*) AS "revisions",
              COALESCE(MAX(CASE WHEN n."status"='published' AND n."publishedRevision"=r."revision" THEN 1 ELSE 0 END),0) AS "published"
         FROM "ContentNoticeImageRef" r JOIN "ContentNotice" n ON n."id"=r."noticeId" WHERE r."mediaId"=?`, id);
    return {revisions: Number(rows[0]?.revisions ?? 0), published: Number(rows[0]?.published ?? 0) === 1};
  },
};

export class SqlContentStore implements ContentStore {
  constructor(private readonly db: PrismaClient, private readonly txOptions = {maxWait: 5000, timeout: 15000}) {}

  async transaction<T>(run: (tx: ContentTx) => Promise<T>): Promise<T> {
    try {
      return await this.db.$transaction(async raw => run(this.tx(raw)), this.txOptions);
    } catch (error) {
      if (transient(error)) throw new TransientStoreError('store busy');
      throw error;
    }
  }

  private tx(db: Executor): ContentTx {
    return {
      async reserveRequest(r) {
        const inserted = await db.$executeRawUnsafe(
          'INSERT INTO "ContentAdminRequest"("actorId","requestId","operation","payloadHash","status","response","createdAt") VALUES (?,?,?,?,0,\'\',?) ON CONFLICT("actorId","requestId") DO NOTHING',
          r.actorId, r.requestId, r.operation, r.payloadHash, ms(r.createdAt));
        if (inserted === 1) return 'reserved';
        const rows = await db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentAdminRequest" WHERE "actorId"=? AND "requestId"=?', r.actorId, r.requestId);
        const found = rows[0];
        return {actorId: String(found.actorId), requestId: String(found.requestId), operation: String(found.operation), payloadHash: String(found.payloadHash), status: Number(found.status), response: String(found.response), createdAt: date(found.createdAt)!} as RequestRecord;
      },
      async completeRequest(actorId, requestId, status, response) {
        await db.$executeRawUnsafe('UPDATE "ContentAdminRequest" SET "status"=?, "response"=? WHERE "actorId"=? AND "requestId"=?', status, response, actorId, requestId);
      },
      getNotice: id => read.notice(db, id),
      async insertNotice(h) {
        await db.$executeRawUnsafe(
          'INSERT INTO "ContentNotice"("id","status","version","revision","publishedRevision","publishedAt","createdAt","updatedAt","createdBy","updatedBy") VALUES (?,?,?,?,?,?,?,?,?,?)',
          h.id, h.status, h.version, h.revision, h.publishedRevision, ms(h.publishedAt), ms(h.createdAt), ms(h.updatedAt), h.createdBy, h.updatedBy);
      },
      async updateNotice(h, expectedVersion) {
        const changed = await db.$executeRawUnsafe(
          'UPDATE "ContentNotice" SET "status"=?,"version"=?,"revision"=?,"publishedRevision"=?,"publishedAt"=?,"updatedAt"=?,"updatedBy"=? WHERE "id"=? AND "version"=?',
          h.status, h.version, h.revision, h.publishedRevision, ms(h.publishedAt), ms(h.updatedAt), h.updatedBy, h.id, expectedVersion);
        return changed === 1;
      },
      getRevision: (noticeId, revision) => read.revision(db, noticeId, revision),
      async insertRevision(r) {
        await db.$executeRawUnsafe(
          'INSERT INTO "ContentNoticeRevision"("noticeId","revision","action","title","pinned","bodySource","bodyHtml","scriptSource","restoredFrom","editedBy","editedAt") VALUES (?,?,?,?,?,?,?,?,?,?,?)',
          r.noticeId, r.revision, r.action, r.title, r.pinned ? 1 : 0, r.bodySource, r.bodyHtml, r.scriptSource, r.restoredFrom, r.editedBy, ms(r.editedAt));
        // 외래키(ContentMedia ON DELETE RESTRICT)로 없는 이미지를 참조할 수 없고, 참조 중인 이미지 행은 지울 수 없다.
        for (const [position, image] of r.images.entries()) {
          await db.$executeRawUnsafe('INSERT INTO "ContentNoticeImageRef"("noticeId","revision","position","mediaId","alt") VALUES (?,?,?,?,?)', r.noticeId, r.revision, position, image.mediaId, image.alt);
        }
      },
      getMedia: id => read.media(db, id),
      async insertMedia(m) {
        await db.$executeRawUnsafe(
          'INSERT INTO "ContentMedia"("id","format","mime","width","height","bytes","sha256","status","createdBy","createdAt") VALUES (?,?,?,?,?,?,?,?,?,?)',
          m.id, m.format, m.mime, m.width, m.height, m.bytes, m.sha256, m.status, m.createdBy, ms(m.createdAt));
      },
      async setMediaStatus(id, expected, next) {
        return (await db.$executeRawUnsafe('UPDATE "ContentMedia" SET "status"=? WHERE "id"=? AND "status"=?', next, id, expected)) === 1;
      },
      mediaReferences: id => read.references(db, id),
      async appendAudit(e: AuditEntry) {
        await db.$executeRawUnsafe(
          'INSERT INTO "ContentAdminAudit"("id","at","actorId","action","targetType","targetId","detailJson") VALUES (?,?,?,?,?,?,?)',
          e.id, ms(e.at), e.actorId, e.action, e.targetType, e.targetId, JSON.stringify(e.detail));
      },
    };
  }

  getNotice(id: string) {
    return read.notice(this.db, id);
  }
  async listNotices(statuses: readonly NoticeStatus[]) {
    if (!statuses.length) return [];
    const rows = await this.db.$queryRawUnsafe<Row[]>(`SELECT * FROM "ContentNotice" WHERE "status" IN (${statuses.map(() => '?').join(',')})`, ...statuses);
    return rows.map(head);
  }
  getRevision(noticeId: string, revision: number) {
    return read.revision(this.db, noticeId, revision);
  }
  async listRevisions(noticeId: string) {
    const rows = await this.db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentNoticeRevision" WHERE "noticeId"=? ORDER BY "revision"', noticeId);
    const out: RevisionRecord[] = [];
    for (const r of rows) out.push(await revisionOf(this.db, r));
    return out;
  }
  getMedia(id: string) {
    return read.media(this.db, id);
  }
  mediaReferences(id: string) {
    return read.references(this.db, id);
  }
  async listAudit(limit: number) {
    const rows = await this.db.$queryRawUnsafe<Row[]>('SELECT * FROM "ContentAdminAudit" ORDER BY "at" DESC, "id" DESC LIMIT ?', limit);
    return rows.map(r => ({id: String(r.id), at: date(r.at)!, actorId: String(r.actorId), action: String(r.action) as AuditEntry['action'], targetType: String(r.targetType) as AuditEntry['targetType'], targetId: String(r.targetId), detail: JSON.parse(String(r.detailJson))}));
  }
}
