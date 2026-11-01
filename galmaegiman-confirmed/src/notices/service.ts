import {randomUUID} from 'node:crypto';
import {safeDetail, type AuditAction} from '../content-admin/audit.js';
import type {Principal} from '../content-admin/access.js';
import {ContentError} from '../content-admin/errors.js';
import {once, requireRequestId} from '../content-admin/requests.js';
import {SerialQueue} from '../content-admin/serial.js';
import type {ContentStore, NoticeImage, NoticeRecord, NoticeStatus, RevisionRecord} from '../content-admin/store.js';
import {isMediaId} from '../content-admin/ids.js';
import {MAX_BODY_SOURCE, plainExcerpt, sanitizeNoticeHtml} from './sanitize.js';

export const MAX_TITLE = 120;
export const MAX_IMAGES_PER_NOTICE = 20;
export const MAX_ALT = 300;

const NOTICE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type PublicNoticeSummary = {id: string; title: string; pinned: boolean; excerpt: string; publishedAt: string; updatedAt: string; imageCount: number};
export type PublicNotice = {id: string; title: string; pinned: boolean; bodyHtml: string; images: NoticeImage[]; publishedAt: string; updatedAt: string};
export type AdminNotice = Omit<PublicNotice, 'publishedAt'> & {status: NoticeStatus; bodySource: string; version: number; createdAt: string; publishedAt: string | null};

type Draft = {title: string; body: string; pinned: boolean; images: NoticeImage[]};

function text(value: unknown, field: string, max: number, allowEmpty = false): string {
  if (typeof value !== 'string') throw new ContentError(400, 'INVALID_FIELD', {field});
  const trimmed = value.trim();
  if ((!allowEmpty && !trimmed) || trimmed.length > max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(trimmed)) throw new ContentError(400, 'INVALID_FIELD', {field});
  return trimmed;
}

function version(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1) throw new ContentError(400, 'EXPECTED_VERSION_REQUIRED');
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

function draftOf(body: Record<string, unknown>): Draft {
  return {
    title: text(body.title, 'title', MAX_TITLE),
    body: text(body.body ?? '', 'body', MAX_BODY_SOURCE, true),
    pinned: body.pinned === undefined ? false : body.pinned === true ? true : body.pinned === false ? false : (() => { throw new ContentError(400, 'INVALID_FIELD', {field: 'pinned'}); })(),
    images: images(body.images),
  };
}

function only(body: unknown, keys: readonly string[]): Record<string, unknown> {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ContentError(400, 'INVALID_BODY');
  const record = body as Record<string, unknown>;
  // role, status, createdBy 처럼 서버가 정하는 값은 받지 않는다.
  for (const key of Object.keys(record)) if (!keys.includes(key)) throw new ContentError(400, 'UNKNOWN_FIELD', {field: key});
  return record;
}

const iso = (d: Date | null) => (d ? d.toISOString() : null);

export function toPublic(n: NoticeRecord): PublicNotice {
  return {id: n.id, title: n.title, pinned: n.pinned, bodyHtml: n.bodyHtml, images: n.images, publishedAt: iso(n.publishedAt)!, updatedAt: n.updatedAt.toISOString()};
}

export function toAdmin(n: NoticeRecord): AdminNotice {
  return {id: n.id, title: n.title, pinned: n.pinned, bodyHtml: n.bodyHtml, images: n.images, updatedAt: n.updatedAt.toISOString(), status: n.status, bodySource: n.bodySource, version: n.version, createdAt: n.createdAt.toISOString(), publishedAt: iso(n.publishedAt)};
}

const order = (a: NoticeRecord, b: NoticeRecord) =>
  Number(b.pinned) - Number(a.pinned) || (b.publishedAt?.getTime() ?? 0) - (a.publishedAt?.getTime() ?? 0) || b.updatedAt.getTime() - a.updatedAt.getTime() || a.id.localeCompare(b.id);

export class NoticeService {
  constructor(private readonly store: ContentStore, private readonly queue = new SerialQueue(), private readonly now = () => new Date()) {}

  // ---- 공개 조회: 게시된 글만 ----
  async listPublished(limit = 50): Promise<PublicNoticeSummary[]> {
    const list = (await this.store.listNotices(['published'])).sort(order).slice(0, Math.max(1, Math.min(100, limit)));
    return list.map(n => ({id: n.id, title: n.title, pinned: n.pinned, excerpt: plainExcerpt(n.bodyHtml), publishedAt: n.publishedAt!.toISOString(), updatedAt: n.updatedAt.toISOString(), imageCount: n.images.length}));
  }

  async getPublished(id: string): Promise<PublicNotice> {
    const found = NOTICE_ID.test(id) ? await this.store.getNotice(id) : null;
    // 초안·숨김·없음 모두 같은 404 로 응답해 존재 여부를 드러내지 않는다.
    if (!found || found.status !== 'published') throw new ContentError(404, 'NOT_FOUND');
    return toPublic(found);
  }

  // ---- 관리자 조회 ----
  async adminList(status?: unknown): Promise<AdminNotice[]> {
    const statuses: NoticeStatus[] = status === undefined ? ['draft', 'published', 'hidden'] : ['draft', 'published', 'hidden'].includes(status as string) ? [status as NoticeStatus] : (() => { throw new ContentError(400, 'INVALID_STATUS'); })();
    return (await this.store.listNotices(statuses)).sort(order).map(toAdmin);
  }

  async adminGet(id: string): Promise<AdminNotice> {
    return toAdmin(await this.mustGet(id));
  }

  async revisions(id: string) {
    await this.mustGet(id);
    return (await this.store.listRevisions(id)).map(r => ({version: r.version, action: r.action, status: r.status, pinned: r.pinned, title: r.title, imageCount: r.images.length, editedBy: r.editedBy, editedAt: r.editedAt.toISOString()}));
  }

  // ---- 관리자 쓰기 ----
  create(actor: Principal, body: unknown) {
    const input = only(body, ['requestId', 'title', 'body', 'pinned', 'images']);
    const requestId = requireRequestId(input.requestId);
    const draft = draftOf(input);
    return this.write(actor, requestId, 'notice.create', draft, async () => {
      await this.requireActiveMedia(draft.images);
      const at = this.now(), id = randomUUID();
      const notice: NoticeRecord = {id, status: 'draft', pinned: draft.pinned, title: draft.title, bodySource: draft.body, bodyHtml: sanitizeNoticeHtml(draft.body), images: draft.images, version: 1, createdAt: at, updatedAt: at, publishedAt: null, createdBy: actor.userId, updatedBy: actor.userId};
      await this.store.insertNotice(notice, this.revision(notice, 'create', actor, at));
      await this.audit(actor, 'notice.create', id, {version: 1, status: 'draft', imageCount: draft.images.length});
      return {status: 201, body: toAdmin(notice)};
    });
  }

  /** 내용 저장(임시저장). 게시 상태는 바꾸지 않는다. */
  update(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion', 'title', 'body', 'pinned', 'images']);
    const requestId = requireRequestId(input.requestId), expected = version(input.expectedVersion);
    const draft = draftOf(input);
    return this.write(actor, requestId, 'notice.update', {id, expected, draft}, async () => {
      const current = await this.mustGet(id);
      await this.requireActiveMedia(draft.images);
      return this.commit(actor, current, expected, 'update', {pinned: draft.pinned, title: draft.title, bodySource: draft.body, bodyHtml: sanitizeNoticeHtml(draft.body), images: draft.images}, 'notice.update');
    });
  }

  publish(actor: Principal, id: string, body: unknown) {
    return this.transition(actor, id, body, 'published', 'publish', 'notice.publish');
  }

  hide(actor: Principal, id: string, body: unknown) {
    return this.transition(actor, id, body, 'hidden', 'hide', 'notice.hide');
  }

  pin(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion', 'pinned']);
    const requestId = requireRequestId(input.requestId), expected = version(input.expectedVersion);
    if (typeof input.pinned !== 'boolean') throw new ContentError(400, 'INVALID_FIELD', {field: 'pinned'});
    const pinned = input.pinned;
    return this.write(actor, requestId, 'notice.pin', {id, expected, pinned}, async () => {
      const current = await this.mustGet(id);
      return this.commit(actor, current, expected, 'pin', {pinned}, 'notice.pin');
    });
  }

  /** 지난 판의 제목·본문·이미지·고정을 새 판으로 되살린다. 이력은 지우지 않고, 게시 상태는 유지한다. */
  restore(actor: Principal, id: string, body: unknown) {
    const input = only(body, ['requestId', 'expectedVersion', 'revision']);
    const requestId = requireRequestId(input.requestId), expected = version(input.expectedVersion), target = version(input.revision);
    return this.write(actor, requestId, 'notice.restore', {id, expected, target}, async () => {
      const current = await this.mustGet(id);
      const old = await this.store.getRevision(id, target);
      if (!old) throw new ContentError(404, 'REVISION_NOT_FOUND');
      // 이력에 남아 있는 이미지는 보존되므로 재사용할 수 있다(폐기된 이미지는 막는다).
      await this.requireActiveMedia(old.images);
      return this.commit(actor, current, expected, 'restore', {pinned: old.pinned, title: old.title, bodySource: old.bodySource, bodyHtml: sanitizeNoticeHtml(old.bodySource), images: old.images}, 'notice.restore', {restoredFrom: target});
    });
  }

  // ---- 내부 ----
  private transition(actor: Principal, id: string, body: unknown, status: NoticeStatus, action: 'publish' | 'hide', audit: AuditAction) {
    const input = only(body, ['requestId', 'expectedVersion']);
    const requestId = requireRequestId(input.requestId), expected = version(input.expectedVersion);
    return this.write(actor, requestId, audit, {id, expected}, async () => {
      const current = await this.mustGet(id);
      if (status === 'published') await this.requireActiveMedia(current.images);
      return this.commit(actor, current, expected, action, {status, publishedAt: status === 'published' ? current.publishedAt ?? this.now() : current.publishedAt}, audit);
    });
  }

  private async commit(actor: Principal, current: NoticeRecord, expected: number, action: RevisionRecord['action'], change: Partial<NoticeRecord>, audit: AuditAction, extra: Record<string, unknown> = {}) {
    if (current.version !== expected) throw new ContentError(409, 'VERSION_CONFLICT', {currentVersion: current.version});
    const at = this.now();
    const next: NoticeRecord = {...current, ...change, version: current.version + 1, updatedAt: at, updatedBy: actor.userId};
    const saved = await this.store.updateNotice(next, this.revision(next, action, actor, at), expected);
    if (!saved) {
      const latest = await this.store.getNotice(current.id);
      throw new ContentError(409, 'VERSION_CONFLICT', {currentVersion: latest?.version ?? null});
    }
    await this.audit(actor, audit, next.id, {version: next.version, status: next.status, pinned: next.pinned, imageCount: next.images.length, ...extra});
    return {status: 200, body: toAdmin(next)};
  }

  private revision(n: NoticeRecord, action: RevisionRecord['action'], actor: Principal, at: Date): RevisionRecord {
    return {noticeId: n.id, version: n.version, action, status: n.status, pinned: n.pinned, title: n.title, bodySource: n.bodySource, bodyHtml: n.bodyHtml, images: n.images, editedBy: actor.userId, editedAt: at};
  }

  private async mustGet(id: string): Promise<NoticeRecord> {
    const found = NOTICE_ID.test(id) ? await this.store.getNotice(id) : null;
    if (!found) throw new ContentError(404, 'NOT_FOUND');
    return found;
  }

  private async requireActiveMedia(list: NoticeImage[]) {
    for (const image of list) {
      const media = await this.store.getMedia(image.mediaId);
      if (!media || media.status !== 'active') throw new ContentError(409, 'MEDIA_UNAVAILABLE', {mediaId: image.mediaId});
    }
  }

  private async audit(actor: Principal, action: AuditAction, targetId: string, detail: Record<string, unknown>) {
    await this.store.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action, targetType: 'notice', targetId, detail: safeDetail(detail)});
  }

  private write<T>(actor: Principal, requestId: string, operation: string, payload: unknown, run: () => Promise<{status: number; body: T}>) {
    return this.queue.run(() => once(this.store, actor.userId, requestId, operation, payload, run, this.now));
  }
}
