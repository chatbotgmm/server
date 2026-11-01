import type {PrismaClient} from '@prisma/client';
import type {AuditEntry} from './audit.js';
import type {ContentStore, MediaRecord, MediaReferences, NoticeImage, NoticeRecord, NoticeStatus, RequestRecord, RevisionRecord} from './store.js';

/**
 * ContentStore 의 Prisma(SQLite) 구현. 통합 변경: prisma/schema.prisma 의 Content* 표가 있어야 한다.
 * 공지와 이력은 한 트랜잭션으로 저장하고, 버전이 맞을 때만 갱신한다(낙관적 잠금).
 */
type Row = {
  id: string; status: string; pinned: boolean; title: string; bodySource: string; bodyHtml: string; imagesJson: string;
  version: number; createdAt: Date; updatedAt: Date; publishedAt: Date | null; createdBy: string; updatedBy: string;
};
type RevRow = {
  noticeId: string; version: number; action: string; status: string; pinned: boolean; title: string; bodySource: string;
  bodyHtml: string; imagesJson: string; editedBy: string; editedAt: Date;
};

const images = (json: string): NoticeImage[] => JSON.parse(json) as NoticeImage[];
const notice = (r: Row): NoticeRecord => ({...r, status: r.status as NoticeStatus, images: images(r.imagesJson)});
const revision = (r: RevRow): RevisionRecord => ({...r, action: r.action as RevisionRecord['action'], status: r.status as NoticeStatus, images: images(r.imagesJson)});

function noticeData(n: NoticeRecord) {
  const {images: list, ...rest} = n;
  return {...rest, imagesJson: JSON.stringify(list)};
}
function revisionData(r: RevisionRecord) {
  const {images: list, ...rest} = r;
  return {...rest, imagesJson: JSON.stringify(list)};
}

export class PrismaContentStore implements ContentStore {
  constructor(private readonly db: PrismaClient) {}

  async getNotice(id: string) {
    const row = await this.db.contentNotice.findUnique({where: {id}});
    return row ? notice(row) : null;
  }
  async listNotices(statuses: readonly NoticeStatus[]) {
    return (await this.db.contentNotice.findMany({where: {status: {in: [...statuses]}}})).map(notice);
  }
  async insertNotice(n: NoticeRecord, r: RevisionRecord) {
    await this.db.$transaction([this.db.contentNotice.create({data: noticeData(n)}), this.db.contentNoticeRevision.create({data: revisionData(r)})]);
  }
  async updateNotice(n: NoticeRecord, r: RevisionRecord, expectedVersion: number) {
    return this.db.$transaction(async tx => {
      const {id, ...data} = noticeData(n);
      const changed = await tx.contentNotice.updateMany({where: {id, version: expectedVersion}, data});
      if (changed.count !== 1) return false;
      await tx.contentNoticeRevision.create({data: revisionData(r)});
      return true;
    });
  }
  async listRevisions(noticeId: string) {
    return (await this.db.contentNoticeRevision.findMany({where: {noticeId}, orderBy: {version: 'asc'}})).map(revision);
  }
  async getRevision(noticeId: string, version: number) {
    const row = await this.db.contentNoticeRevision.findUnique({where: {noticeId_version: {noticeId, version}}});
    return row ? revision(row) : null;
  }

  async getMedia(id: string) {
    const row = await this.db.contentMedia.findUnique({where: {id}});
    return row ? ({...row, format: row.format as MediaRecord['format'], status: row.status as MediaRecord['status']}) : null;
  }
  async insertMedia(m: MediaRecord) {
    await this.db.contentMedia.create({data: m});
  }
  async setMediaStatus(id: string, status: MediaRecord['status']) {
    await this.db.contentMedia.update({where: {id}, data: {status}});
  }
  async mediaReferences(id: string): Promise<MediaReferences> {
    // UUID 는 겹치지 않으므로 문자열 포함으로 후보를 좁힌 뒤 JSON 을 풀어 정확히 센다.
    const [current, revisions] = await Promise.all([
      this.db.contentNotice.findMany({where: {imagesJson: {contains: id}}, select: {status: true, imagesJson: true}}),
      this.db.contentNoticeRevision.findMany({where: {imagesJson: {contains: id}}, select: {imagesJson: true}}),
    ]);
    const uses = (json: string) => images(json).some(i => i.mediaId === id);
    const live = current.filter(r => uses(r.imagesJson));
    return {current: live.length, published: live.some(r => r.status === 'published'), history: revisions.filter(r => uses(r.imagesJson)).length};
  }

  async findRequest(actorId: string, requestId: string) {
    return (await this.db.contentAdminRequest.findUnique({where: {actorId_requestId: {actorId, requestId}}})) as RequestRecord | null;
  }
  async saveRequest(r: RequestRecord) {
    await this.db.contentAdminRequest.create({data: r});
  }

  async appendAudit(e: AuditEntry) {
    const {detail, ...rest} = e;
    await this.db.contentAdminAudit.create({data: {...rest, detailJson: JSON.stringify(detail)}});
  }
  async listAudit(limit: number) {
    const rows = await this.db.contentAdminAudit.findMany({orderBy: [{at: 'desc'}, {id: 'desc'}], take: limit});
    return rows.map(({detailJson, ...r}) => ({...r, action: r.action as AuditEntry['action'], targetType: r.targetType as AuditEntry['targetType'], detail: JSON.parse(detailJson)}));
  }
}
