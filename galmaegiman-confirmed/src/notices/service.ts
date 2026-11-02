import {randomUUID} from 'node:crypto';
import {safeDetail, type AuditAction} from '../content-admin/audit.js';
import type {Principal} from '../content-admin/access.js';
import {ContentError} from '../content-admin/errors.js';
import {isMediaId} from '../content-admin/ids.js';
import {once, requireRequestId} from '../content-admin/requests.js';
import type {ContentStore, ContentTx, NoticeHead, NoticeImage, NoticeStatus, RevisionRecord} from '../content-admin/store.js';
import {MAX_BODY_SOURCE, MAX_SCRIPT_SOURCE, plainExcerpt, renderNoticeHtml, sanitizeNoticeHtml} from './sanitize.js';

export const MAX_TITLE = 120;
export const MAX_IMAGES_PER_NOTICE = 20;
export const MAX_ALT = 300;

const NOTICE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type PublicNoticeSummary = {id: string; title: string; pinned: boolean; excerpt: string; publishedAt: string; imageCount: number};
export type PublicNotice = {id: string; title: string; pinned: boolean; bodyHtml: string; images: (NoticeImage & {url: string})[]; publishedAt: string};
export type AdminContent = {revision: number; title: string; pinned: boolean; bodySource: string; bodyHtml: string; scriptSource: string; images: (NoticeImage & {url: string})[]; editedBy: string; editedAt: string};
export type AdminNotice = {
  id: string; status: NoticeStatus; version: number; revision: number; publishedRevision: number | null;
  hasUnpublishedChanges: boolean; publishedAt: string | null; createdAt: string; updatedAt: string;
  working: AdminContent; published: AdminContent | null;
};

type Draft = {title: string; body: string; script: string; pinned: boolean; images: NoticeImage[]};

export type MediaUrls = {publicUrl: (id: string) => string; adminUrl: (id: string) => string};
const DEFAULT_URLS: MediaUrls = {publicUrl: id => `/api/v1/media/${id}`, adminUrl: id => `/api/v1/admin/media/${id}`};

function text(value: unknown, field: string, max: number, allowEmpty = false): string {
  if (typeof value !== 'string') throw new ContentError(400, 'INVALID_FIELD', {field});
  const trimmed = field === 'scriptSource' ? value : value.trim();
  if ((!allowEmpty && !trimmed) || trimmed.length > max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(trimmed)) throw new ContentError(400, 'INVALID_FIELD', {field});
  return trimmed;
}

function int(value: unknown, code: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1) throw new ContentError(400, code);
  return value;
}

function images(value: unknown): NoticeImage[] {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > MAX_IMAGES_PER_NOTICE) throw new ContentError(400, 'INVALID_IMAGES');
  const seen = new Set<string>();
  return value.map(item => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new ContentError(400, 'INVALID_IMAGES');
    const {mediaId, alt, ...rest} = item as Record<string, unknown>;
    if (Object.keys(rest).length || !isMediaId(mediaId) || seen.has(mediaId)) throw new ContentError(400, 'INVALID_IMAGES');
    seen.add(mediaId);
    return {mediaId, alt: text(alt, 'alt', MAX_ALT)};
  });
}

function only(body: unknown, keys: readonly string[]): Record<string, unknown> {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ContentError(400, 'INVALID_BODY');
  const record = body as Record<string, unknown>;
  // role, status, publishedRevision, createdBy 처럼 서버가 정하는 값은 받지 않는다.
  for (const key of Object.keys(record)) if (!keys.includes(key)) throw new ContentError(400, 'UNKNOWN_FIELD', {field: key});
  return record;
}

function draftOf(body: Record<string, unknown>): Draft {
  const pinned = body.pinned === undefined ? false : body.pinned;
  if (typeof pinned !== 'boolean') throw new ContentError(400, 'INVALID_FIELD', {field: 'pinned'});
  return {
    title: text(body.title, 'title', MAX_TITLE),
    body: text(body.body ?? '', 'body', MAX_BODY_SOURCE, true),
    script: text(body.scriptSource ?? '', 'scriptSource', MAX_SCRIPT_SOURCE, true),
    pinned,
    images: images(body.images),
  };
}

const DRAFT_KEYS = ['requestId', 'title', 'body', 'scriptSource', 'pinned', 'images'];

export class NoticeService {
  constructor(private readonly store: ContentStore, private readonly now = () => new Date(), private readonly urls: MediaUrls = DEFAULT_URLS) {}

  // ---- 공개: 게시 상태인 글의 '공개 판'만 ----
  async listPublished(limit = 50): Promise<PublicNoticeSummary[]> {
    const heads = await this.store.listNotices(['published']);
    const rows: {head: NoticeHead; rev: RevisionRecord}[] = [];
    for (const head of heads) {
      const rev = head.publishedRevision ? await this.store.getRevision(head.id, head.publishedRevision) : null;
      if (rev) rows.push({head, rev});
    }
    rows.sort((a, b) => Number(b.rev.pinned) - Number(a.rev.pinned) || b.head.publishedAt!.getTime() - a.head.publishedAt!.getTime() || a.head.id.localeCompare(b.head.id));
    return rows.slice(0, Math.max(1, Math.min(100, limit))).map(({head, rev}) => ({
      id: head.id, title: rev.title, pinned: rev.pinned, excerpt: plainExcerpt(rev.bodyHtml), publishedAt: head.publishedAt!.toISOString(), imageCount: rev.images.length,
    }));
  }

  async getPublished(id: string): Promise<PublicNotice> {
    const head = NOTICE_ID.test(id) ? await this.store.getNotice(id) : null;
    // 초안·숨김·없음은 모두 같은 404(존재 여부를 드러내지 않음)
    if (!head || head.status !== 'published' || !head.publishedRevision) throw new ContentError(404, 'NOT_FOUND');
    const rev = await this.store.getRevision(id, head.publishedRevision);
    if (!rev) throw new ContentError(404, 'NOT_FOUND');
    const attached = new Set(rev.images.map(i => i.mediaId));
    return {
      id, title: rev.title, pinned: rev.pinned, publishedAt: head.publishedAt!.toISOString(),
      bodyHtml: renderNoticeHtml(rev.bodyHtml, m => (attached.has(m) ? this.urls.publicUrl(m) : null)),
      images: rev.images.map(i => ({...i, url: this.urls.publicUrl(i.mediaId)})),
    };
  }

  // ---- 관리자 조회 ----
  async adminList(status?: unknown): Promise<AdminNotice[]> {
    const all: NoticeStatus[] = ['draft', 'published', 'hidden'];
    if (status !== undefined && !all.includes(status as NoticeStatus)) throw new ContentError(400, 'INVALID_STATUS');
    const heads = await this.store.listNotices(status === undefined ? all : [status as NoticeStatus]);
    const out: AdminNotice[] = [];
    for (const head of heads.sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime() || a.id.localeCompare(b.id))) out.push(await this.admin(head));
    return out;
  }

  async adminGet(id: string): Promise<AdminNotice> {
    return this.admin(await this.mustGet(id));
  }

  async revisions(id: string) {
    const head = await this.mustGet(id);
    return (await this.store.listRevisions(id)).map(r => ({
      revision: r.revision, action: r.action, restoredFrom: r.restoredFrom, title: r.title, pinned: r.pinned, imageCount: r.images.length,
      isWorking: r.revision === head.revision, isPublished: r.revision === head.publishedRevision, editedBy: r.editedBy, editedAt: r.editedAt.toISOString(),
    }));
  }

  // ---- 관리자 쓰기(모두 DB 트랜잭션 하나) ----
  create(actor: Principal, body: unknown) {
    const input = only(body, DRAFT_KEYS);
    const requestId = requireRequestId(input.requestId);
    const draft = draftOf(input);
    return once(this.store, actor.userId, requestId, 'notice.create', draft, async tx => {
      await this.requireActiveMedia(tx, draft.images);
      const at = this.now(), id = randomUUID();
      const head: NoticeHead = {id, status: 'draft', version: 1, revision: 1, publishedRevision: null, publishedAt: null, createdAt: at, updatedAt: at, createdBy: actor.userId, updatedBy: actor.userId};
      await tx.insertNotice(head);
      const rev = this.revisionOf(id, 1, 'create', draft, actor, at, null);
      await tx.insertRevision(rev);
      await this.audit(tx, actor, 'notice.create', id, {version: 1, revision: 1, status: 'draft', imageCount: draft.images.length});
      return {status: 201, body: await this.adminTx(tx, head, rev)};
    }, this.now);
  }

  /** 작업 판 저장(임시저장). 공개 판은 바꾸지 않는다. */
  update(actor: Principal, id: string, body: unknown) {
    const input = only(body, [...DRAFT_KEYS, 'expectedVersion']);
    const requestId = requireRequestId(input.requestId), expected = int(input.expectedVersion, 'EXPECTED_VERSION_REQUIRED');
    const draft = draftOf(input);
    return once(this.store, actor.userId, requestId, 'notice.update', {id, expected, draft}, async tx => {
      const head = await this.mustGetTx(tx, id, expected);
      await this.requireActiveMedia(tx, draft.images);
      return this.newWorkingRevision(tx, actor, head, expected, 'edit', draft, null, 'notice.update');
    }, this.now);
  }

  /** 지난 판 내용을 새 작업 판으로 되살린다. 공개 판·게시 상태는 바꾸지 않는다. */
  restore(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion', 'revision']);
    const requestId = requireRequestId(input.requestId), expected = int(input.expectedVersion, 'EXPECTED_VERSION_REQUIRED'), target = int(input.revision, 'REVISION_REQUIRED');
    return once(this.store, actor.userId, requestId, 'notice.restore', {id, expected, target}, async tx => {
      const head = await this.mustGetTx(tx, id, expected);
      const old = await tx.getRevision(id, target);
      if (!old) throw new ContentError(404, 'REVISION_NOT_FOUND');
      await this.requireActiveMedia(tx, old.images);
      const draft: Draft = {title: old.title, body: old.bodySource, script: old.scriptSource, pinned: old.pinned, images: old.images};
      return this.newWorkingRevision(tx, actor, head, expected, 'restore', draft, target, 'notice.restore');
    }, this.now);
  }

  /** 명시적 게시: 지금 작업 판(또는 지정한 판)을 공개 판으로 만든다. 공개 내용이 바뀌는 유일한 경로. */
  publish(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion', 'revision']);
    const requestId = requireRequestId(input.requestId), expected = int(input.expectedVersion, 'EXPECTED_VERSION_REQUIRED');
    const requested = input.revision === undefined ? null : int(input.revision, 'REVISION_REQUIRED');
    return once(this.store, actor.userId, requestId, 'notice.publish', {id, expected, requested}, async tx => {
      const head = await this.mustGetTx(tx, id, expected);
      const target = requested ?? head.revision;
      // 화면에 보이던 작업 판과 다른 판이 게시되지 않게: 지정하려면 지금 작업 판과 같아야 한다.
      if (target !== head.revision) throw new ContentError(409, 'REVISION_NOT_WORKING', {workingRevision: head.revision});
      const rev = await tx.getRevision(id, target);
      if (!rev) throw new ContentError(404, 'REVISION_NOT_FOUND');
      await this.requireActiveMedia(tx, rev.images);
      const at = this.now();
      return this.saveHead(tx, actor, {...head, status: 'published', publishedRevision: target, publishedAt: at}, expected, 'notice.publish', {revision: target});
    }, this.now);
  }

  /** 숨김: 공개를 멈춘다. 공개 판 기록은 남는다(다시 게시하면 그때의 작업 판이 공개된다). */
  hide(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion']);
    const requestId = requireRequestId(input.requestId), expected = int(input.expectedVersion, 'EXPECTED_VERSION_REQUIRED');
    return once(this.store, actor.userId, requestId, 'notice.hide', {id, expected}, async tx => {
      const head = await this.mustGetTx(tx, id, expected);
      if (head.status === 'draft') throw new ContentError(409, 'NOT_PUBLISHED');
      return this.saveHead(tx, actor, {...head, status: 'hidden'}, expected, 'notice.hide', {});
    }, this.now);
  }

  // ---- 내부 ----
  private async newWorkingRevision(tx: ContentTx, actor: Principal, head: NoticeHead, expected: number, action: RevisionRecord['action'], draft: Draft, restoredFrom: number | null, audit: AuditAction) {
    const at = this.now();
    const rev = this.revisionOf(head.id, head.revision + 1, action, draft, actor, at, restoredFrom);
    await tx.insertRevision(rev);
    return this.saveHead(tx, actor, {...head, revision: rev.revision}, expected, audit, {revision: rev.revision, imageCount: draft.images.length, ...(restoredFrom ? {restoredFrom} : {})});
  }

  private async saveHead(tx: ContentTx, actor: Principal, next: NoticeHead, expected: number, audit: AuditAction, detail: Record<string, unknown>) {
    const head: NoticeHead = {...next, version: expected + 1, updatedAt: this.now(), updatedBy: actor.userId};
    if (!(await tx.updateNotice(head, expected))) {
      const latest = await tx.getNotice(head.id);
      throw new ContentError(409, 'VERSION_CONFLICT', {currentVersion: latest?.version ?? null});
    }
    await this.audit(tx, actor, audit, head.id, {version: head.version, status: head.status, ...detail});
    return {status: 200, body: await this.adminTx(tx, head)};
  }

  private revisionOf(noticeId: string, revision: number, action: RevisionRecord['action'], d: Draft, actor: Principal, at: Date, restoredFrom: number | null): RevisionRecord {
    const attached = new Set(d.images.map(i => i.mediaId));
    return {noticeId, revision, action, title: d.title, pinned: d.pinned, bodySource: d.body, bodyHtml: sanitizeNoticeHtml(d.body, attached), scriptSource: d.script, images: d.images, restoredFrom, editedBy: actor.userId, editedAt: at};
  }

  private content(rev: RevisionRecord): AdminContent {
    const attached = new Set(rev.images.map(i => i.mediaId));
    return {
      revision: rev.revision, title: rev.title, pinned: rev.pinned, bodySource: rev.bodySource, scriptSource: rev.scriptSource,
      bodyHtml: renderNoticeHtml(rev.bodyHtml, m => (attached.has(m) ? this.urls.adminUrl(m) : null)),
      images: rev.images.map(i => ({...i, url: this.urls.adminUrl(i.mediaId)})),
      editedBy: rev.editedBy, editedAt: rev.editedAt.toISOString(),
    };
  }

  private shape(head: NoticeHead, working: RevisionRecord, published: RevisionRecord | null): AdminNotice {
    return {
      id: head.id, status: head.status, version: head.version, revision: head.revision, publishedRevision: head.publishedRevision,
      hasUnpublishedChanges: head.publishedRevision !== head.revision, publishedAt: head.publishedAt?.toISOString() ?? null,
      createdAt: head.createdAt.toISOString(), updatedAt: head.updatedAt.toISOString(),
      working: this.content(working), published: published ? this.content(published) : null,
    };
  }

  private async admin(head: NoticeHead): Promise<AdminNotice> {
    const working = (await this.store.getRevision(head.id, head.revision))!;
    const published = head.publishedRevision ? await this.store.getRevision(head.id, head.publishedRevision) : null;
    return this.shape(head, working, published);
  }

  private async adminTx(tx: ContentTx, head: NoticeHead, known?: RevisionRecord): Promise<AdminNotice> {
    const working = known && known.revision === head.revision ? known : (await tx.getRevision(head.id, head.revision))!;
    const published = head.publishedRevision ? await tx.getRevision(head.id, head.publishedRevision) : null;
    return this.shape(head, working, published);
  }

  private async mustGet(id: string): Promise<NoticeHead> {
    const head = NOTICE_ID.test(id) ? await this.store.getNotice(id) : null;
    if (!head) throw new ContentError(404, 'NOT_FOUND');
    return head;
  }

  private async mustGetTx(tx: ContentTx, id: string, expected: number): Promise<NoticeHead> {
    const head = NOTICE_ID.test(id) ? await tx.getNotice(id) : null;
    if (!head) throw new ContentError(404, 'NOT_FOUND');
    if (head.version !== expected) throw new ContentError(409, 'VERSION_CONFLICT', {currentVersion: head.version});
    return head;
  }

  /** 같은 트랜잭션 안에서 확인하므로 폐기와 동시에 일어나도 폐기된 이미지가 연결되지 않는다. */
  private async requireActiveMedia(tx: ContentTx, list: NoticeImage[]) {
    for (const image of list) {
      const media = await tx.getMedia(image.mediaId);
      if (!media || media.status !== 'active') throw new ContentError(409, 'MEDIA_UNAVAILABLE', {mediaId: image.mediaId});
    }
  }

  private async audit(tx: ContentTx, actor: Principal, action: AuditAction, targetId: string, detail: Record<string, unknown>) {
    await tx.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action, targetType: 'notice', targetId, detail: safeDetail(detail)});
  }
}
