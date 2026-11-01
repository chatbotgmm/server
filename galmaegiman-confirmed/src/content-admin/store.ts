import type {AuditEntry} from './audit.js';
/** 이미지 형식(media/validate.ts 의 ImageFormat 과 같은 값) */
export type ImageFormat = 'png' | 'jpeg' | 'webp';

export type NoticeStatus = 'draft' | 'published' | 'hidden';
export type NoticeImage = {mediaId: string; alt: string};

export type NoticeRecord = {
  id: string;
  status: NoticeStatus;
  pinned: boolean;
  title: string;
  /** 관리자가 입력한 원문. 화면에 HTML 로 그리지 않는다(편집용 문자열). */
  bodySource: string;
  /** 서버에서 허용 목록으로 정화한 HTML. 공개 화면은 이것만 쓴다. */
  bodyHtml: string;
  images: NoticeImage[];
  version: number;
  createdAt: Date;
  updatedAt: Date;
  publishedAt: Date | null;
  createdBy: string;
  updatedBy: string;
};

export type RevisionRecord = {
  noticeId: string;
  version: number;
  action: 'create' | 'update' | 'publish' | 'hide' | 'pin' | 'restore';
  status: NoticeStatus;
  pinned: boolean;
  title: string;
  bodySource: string;
  bodyHtml: string;
  images: NoticeImage[];
  editedBy: string;
  editedAt: Date;
};

export type MediaRecord = {
  id: string;
  format: ImageFormat;
  mime: string;
  width: number;
  height: number;
  bytes: number;
  sha256: string;
  status: 'active' | 'retired';
  createdBy: string;
  createdAt: Date;
};

export type RequestRecord = {
  actorId: string;
  requestId: string;
  operation: string;
  payloadHash: string;
  /** 첫 응답(JSON 문자열)을 그대로 다시 돌려준다. */
  response: string;
  status: number;
  createdAt: Date;
};

export type MediaReferences = {
  /** 현재 판(최신 버전)에서 이 이미지를 쓰는 공지 수 */
  current: number;
  /** 현재 게시 중인 공지가 쓰고 있는지 */
  published: boolean;
  /** 지난 판(복원 가능 이력)에서 쓰는 횟수 */
  history: number;
};

/**
 * 공지·이미지 데이터 저장 인터페이스. 메모리 구현(시험·개발)과 Prisma 구현(통합 변경)이 따른다.
 * 쓰기는 SerialQueue 안에서만 부른다.
 */
export interface ContentStore {
  getNotice(id: string): Promise<NoticeRecord | null>;
  listNotices(statuses: readonly NoticeStatus[]): Promise<NoticeRecord[]>;
  /** 새 공지와 첫 이력을 함께 저장 */
  insertNotice(notice: NoticeRecord, revision: RevisionRecord): Promise<void>;
  /** expectedVersion 이 그대로일 때만 바꾼다. 다르면 false(아무것도 쓰지 않음). */
  updateNotice(notice: NoticeRecord, revision: RevisionRecord, expectedVersion: number): Promise<boolean>;
  listRevisions(noticeId: string): Promise<RevisionRecord[]>;
  getRevision(noticeId: string, version: number): Promise<RevisionRecord | null>;

  getMedia(id: string): Promise<MediaRecord | null>;
  insertMedia(media: MediaRecord): Promise<void>;
  setMediaStatus(id: string, status: MediaRecord['status']): Promise<void>;
  mediaReferences(id: string): Promise<MediaReferences>;

  findRequest(actorId: string, requestId: string): Promise<RequestRecord | null>;
  saveRequest(request: RequestRecord): Promise<void>;

  appendAudit(entry: AuditEntry): Promise<void>;
  listAudit(limit: number): Promise<AuditEntry[]>;
}

const copy = <T>(value: T): T => structuredClone(value);

/** 메모리 구현. 프로세스가 끝나면 사라진다. 시험과 로컬 개발에만 쓴다. */
export class MemoryContentStore implements ContentStore {
  private notices = new Map<string, NoticeRecord>();
  private revisions = new Map<string, RevisionRecord[]>();
  private media = new Map<string, MediaRecord>();
  private requests = new Map<string, RequestRecord>();
  private audit: AuditEntry[] = [];
  /** 시험에서 저장 실패를 흉내 낸다. */
  failNextWrite = false;

  private checkFailure() {
    if (this.failNextWrite) {
      this.failNextWrite = false;
      throw new Error('store unavailable');
    }
  }
  async getNotice(id: string) {
    const found = this.notices.get(id);
    return found ? copy(found) : null;
  }
  async listNotices(statuses: readonly NoticeStatus[]) {
    return [...this.notices.values()].filter(n => statuses.includes(n.status)).map(copy);
  }
  async insertNotice(notice: NoticeRecord, revision: RevisionRecord) {
    this.checkFailure();
    if (this.notices.has(notice.id)) throw new Error('duplicate notice id');
    this.notices.set(notice.id, copy(notice));
    this.revisions.set(notice.id, [copy(revision)]);
  }
  async updateNotice(notice: NoticeRecord, revision: RevisionRecord, expectedVersion: number) {
    this.checkFailure();
    const current = this.notices.get(notice.id);
    if (!current || current.version !== expectedVersion) return false;
    this.notices.set(notice.id, copy(notice));
    this.revisions.get(notice.id)!.push(copy(revision));
    return true;
  }
  async listRevisions(noticeId: string) {
    return (this.revisions.get(noticeId) ?? []).map(copy);
  }
  async getRevision(noticeId: string, version: number) {
    const found = (this.revisions.get(noticeId) ?? []).find(r => r.version === version);
    return found ? copy(found) : null;
  }
  async getMedia(id: string) {
    const found = this.media.get(id);
    return found ? copy(found) : null;
  }
  async insertMedia(media: MediaRecord) {
    this.checkFailure();
    this.media.set(media.id, copy(media));
  }
  async setMediaStatus(id: string, status: MediaRecord['status']) {
    this.checkFailure();
    const found = this.media.get(id);
    if (found) found.status = status;
  }
  async mediaReferences(id: string): Promise<MediaReferences> {
    let current = 0, published = false, history = 0;
    for (const notice of this.notices.values()) {
      if (notice.images.some(i => i.mediaId === id)) {
        current++;
        if (notice.status === 'published') published = true;
      }
    }
    for (const list of this.revisions.values()) for (const r of list) if (r.images.some(i => i.mediaId === id)) history++;
    return {current, published, history};
  }
  async findRequest(actorId: string, requestId: string) {
    const found = this.requests.get(`${actorId}\u0000${requestId}`);
    return found ? copy(found) : null;
  }
  async saveRequest(request: RequestRecord) {
    this.requests.set(`${request.actorId}\u0000${request.requestId}`, copy(request));
  }
  async appendAudit(entry: AuditEntry) {
    this.audit.push(copy(entry));
  }
  async listAudit(limit: number) {
    return this.audit.slice(-limit).reverse().map(copy);
  }
}
