import type {AuditEntry} from './audit.js';

/** 이미지 형식(media/validate.ts 의 ImageFormat 과 같은 값) */
export type ImageFormat = 'png' | 'jpeg' | 'webp';
export type NoticeStatus = 'draft' | 'published' | 'hidden';
export type NoticeImage = {mediaId: string; alt: string};

/**
 * 공지 한 건의 머리 정보. 내용은 모두 불변 판(RevisionRecord)에 있다.
 * - revision: 지금 편집 중인 작업 판(관리자만 봄)
 * - publishedRevision: 공개 중인 판. 명시적 게시(publish) 때만 바뀐다
 * - version: 낙관적 잠금용. 머리 정보가 바뀔 때마다 1씩 오른다
 */
export type NoticeHead = {
  id: string;
  status: NoticeStatus;
  version: number;
  revision: number;
  publishedRevision: number | null;
  publishedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string;
  updatedBy: string;
};

/** 공지 내용 판. 한 번 쓰면 바꾸지 않는다. */
export type RevisionRecord = {
  noticeId: string;
  revision: number;
  action: 'create' | 'edit' | 'restore';
  title: string;
  pinned: boolean;
  /** 관리자가 입력한 원문. 화면에 HTML 로 그리지 않는다. */
  bodySource: string;
  /** 서버가 허용 목록으로 정화한 HTML(이미지는 data-media-id 자리표시만) */
  bodyHtml: string;
  /** 보관만 하는 자바스크립트 원문. 공개 응답에 넣지 않고 어디서도 실행하지 않는다. */
  scriptSource: string;
  images: NoticeImage[];
  restoredFrom: number | null;
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
  status: number;
  response: string;
  createdAt: Date;
};

export type MediaReferences = {
  /** 이 이미지를 쓰는 판(작업 판·공개 판·지난 판 모두)의 수 */
  revisions: number;
  /** 지금 공개 중인 판이 쓰는지 */
  published: boolean;
};

/** 한 트랜잭션 안에서 쓰는 읽기·쓰기. 이 안의 쓰기는 모두 함께 반영되거나 함께 취소된다. */
export interface ContentTx {
  /**
   * 요청 예약. 처음이면 'reserved', 이미 끝난 같은 요청이 있으면 그 기록을 돌려준다.
   * DB 구현은 쓰기로 시작해 트랜잭션 잠금을 먼저 잡는다.
   */
  reserveRequest(r: Omit<RequestRecord, 'status' | 'response'>): Promise<'reserved' | RequestRecord>;
  completeRequest(actorId: string, requestId: string, status: number, response: string): Promise<void>;

  getNotice(id: string): Promise<NoticeHead | null>;
  insertNotice(head: NoticeHead): Promise<void>;
  /** expectedVersion 일 때만 바꾼다. 다르면 false. */
  updateNotice(head: NoticeHead, expectedVersion: number): Promise<boolean>;
  getRevision(noticeId: string, revision: number): Promise<RevisionRecord | null>;
  insertRevision(r: RevisionRecord): Promise<void>;

  getMedia(id: string): Promise<MediaRecord | null>;
  insertMedia(m: MediaRecord): Promise<void>;
  /** status 가 expected 일 때만 바꾼다. */
  setMediaStatus(id: string, expected: MediaRecord['status'], next: MediaRecord['status']): Promise<boolean>;
  mediaReferences(id: string): Promise<MediaReferences>;

  appendAudit(e: AuditEntry): Promise<void>;
}

export interface ContentStore {
  transaction<T>(run: (tx: ContentTx) => Promise<T>): Promise<T>;
  // 읽기 전용(트랜잭션 밖)
  getNotice(id: string): Promise<NoticeHead | null>;
  listNotices(statuses: readonly NoticeStatus[]): Promise<NoticeHead[]>;
  getRevision(noticeId: string, revision: number): Promise<RevisionRecord | null>;
  listRevisions(noticeId: string): Promise<RevisionRecord[]>;
  getMedia(id: string): Promise<MediaRecord | null>;
  mediaReferences(id: string): Promise<MediaReferences>;
  listAudit(limit: number): Promise<AuditEntry[]>;
}

/** 일시적 잠금 충돌. 같은 트랜잭션을 처음부터 다시 실행해도 안전하다. */
export class TransientStoreError extends Error {}

const copy = <T>(value: T): T => structuredClone(value);

type State = {
  notices: Map<string, NoticeHead>;
  revisions: Map<string, RevisionRecord>;
  media: Map<string, MediaRecord>;
  requests: Map<string, RequestRecord>;
  audit: AuditEntry[];
};

const rkey = (id: string, rev: number) => `${id}\u0000${rev}`;

/**
 * 메모리 구현(시험·로컬 개발). 트랜잭션은 한 번에 하나씩, 실패하면 시작 전 상태로 되돌린다.
 * failAt 으로 특정 쓰기 단계에서 실패를 흉내 낼 수 있다.
 */
export class MemoryContentStore implements ContentStore {
  private state: State = {notices: new Map(), revisions: new Map(), media: new Map(), requests: new Map(), audit: []};
  private tail: Promise<unknown> = Promise.resolve();
  failAt: 'reserveRequest' | 'insertNotice' | 'updateNotice' | 'insertRevision' | 'insertMedia' | 'setMediaStatus' | 'appendAudit' | 'completeRequest' | null = null;

  private fail(step: NonNullable<MemoryContentStore['failAt']>) {
    if (this.failAt === step) {
      this.failAt = null;
      throw new Error(`store failure at ${step}`);
    }
  }

  transaction<T>(run: (tx: ContentTx) => Promise<T>): Promise<T> {
    const task = async () => {
      const draft = copy(this.state);
      const tx = this.txFor(draft);
      const result = await run(tx);
      this.state = draft;
      return result;
    };
    const next = this.tail.then(task, task);
    this.tail = next.catch(() => undefined);
    return next;
  }

  private txFor(s: State): ContentTx {
    const self = this;
    return {
      async reserveRequest(r) {
        self.fail('reserveRequest');
        const key = `${r.actorId}\u0000${r.requestId}`;
        const found = s.requests.get(key);
        if (found) return copy(found);
        s.requests.set(key, {...r, status: 0, response: ''});
        return 'reserved';
      },
      async completeRequest(actorId, requestId, status, response) {
        self.fail('completeRequest');
        const found = s.requests.get(`${actorId}\u0000${requestId}`)!;
        found.status = status;
        found.response = response;
      },
      async getNotice(id) {
        const n = s.notices.get(id);
        return n ? copy(n) : null;
      },
      async insertNotice(head) {
        self.fail('insertNotice');
        if (s.notices.has(head.id)) throw new Error('duplicate notice');
        s.notices.set(head.id, copy(head));
      },
      async updateNotice(head, expectedVersion) {
        self.fail('updateNotice');
        const cur = s.notices.get(head.id);
        if (!cur || cur.version !== expectedVersion) return false;
        s.notices.set(head.id, copy(head));
        return true;
      },
      async getRevision(noticeId, revision) {
        const r = s.revisions.get(rkey(noticeId, revision));
        return r ? copy(r) : null;
      },
      async insertRevision(r) {
        self.fail('insertRevision');
        if (s.revisions.has(rkey(r.noticeId, r.revision))) throw new Error('duplicate revision');
        for (const image of r.images) if (!s.media.has(image.mediaId)) throw new Error('foreign key: media');
        s.revisions.set(rkey(r.noticeId, r.revision), copy(r));
      },
      async getMedia(id) {
        const m = s.media.get(id);
        return m ? copy(m) : null;
      },
      async insertMedia(m) {
        self.fail('insertMedia');
        s.media.set(m.id, copy(m));
      },
      async setMediaStatus(id, expected, next) {
        self.fail('setMediaStatus');
        const m = s.media.get(id);
        if (!m || m.status !== expected) return false;
        m.status = next;
        return true;
      },
      async mediaReferences(id) {
        return MemoryContentStore.references(s, id);
      },
      async appendAudit(e) {
        self.fail('appendAudit');
        s.audit.push(copy(e));
      },
    };
  }

  private static references(s: State, id: string): MediaReferences {
    let revisions = 0, published = false;
    for (const r of s.revisions.values()) {
      if (!r.images.some(i => i.mediaId === id)) continue;
      revisions++;
      const head = s.notices.get(r.noticeId);
      if (head && head.status === 'published' && head.publishedRevision === r.revision) published = true;
    }
    return {revisions, published};
  }

  async getNotice(id: string) {
    const n = this.state.notices.get(id);
    return n ? copy(n) : null;
  }
  async listNotices(statuses: readonly NoticeStatus[]) {
    return [...this.state.notices.values()].filter(n => statuses.includes(n.status)).map(copy);
  }
  async getRevision(noticeId: string, revision: number) {
    const r = this.state.revisions.get(rkey(noticeId, revision));
    return r ? copy(r) : null;
  }
  async listRevisions(noticeId: string) {
    return [...this.state.revisions.values()].filter(r => r.noticeId === noticeId).sort((a, b) => a.revision - b.revision).map(copy);
  }
  async getMedia(id: string) {
    const m = this.state.media.get(id);
    return m ? copy(m) : null;
  }
  async mediaReferences(id: string) {
    return MemoryContentStore.references(this.state, id);
  }
  async listAudit(limit: number) {
    return this.state.audit.slice(-limit).reverse().map(copy);
  }
  /** 시험용: 요청 기록 수 */
  requestCount() {
    return this.state.requests.size;
  }
}
