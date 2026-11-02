import {createHash, randomUUID} from 'node:crypto';
import type {Principal} from '../content-admin/access.js';
import {safeDetail} from '../content-admin/audit.js';
import {ContentError} from '../content-admin/errors.js';
import {isMediaId, newMediaId} from '../content-admin/ids.js';
import {once, requireRequestId} from '../content-admin/requests.js';
import type {ContentStore, MediaRecord} from '../content-admin/store.js';
import type {MediaBlobStorage} from './storage.js';
import {cleanImage, DEFAULT_IMAGE_LIMITS, ImageRejected, type ImageLimits} from './validate.js';

export type MediaInfo = {id: string; mime: string; width: number; height: number; bytes: number; status: MediaRecord['status']; createdAt: string};

const info = (m: MediaRecord): MediaInfo => ({id: m.id, mime: m.mime, width: m.width, height: m.height, bytes: m.bytes, status: m.status, createdAt: m.createdAt.toISOString()});

export class MediaService {
  constructor(
    private readonly store: ContentStore,
    private readonly blobs: MediaBlobStorage,
    private readonly limits: ImageLimits = DEFAULT_IMAGE_LIMITS,
    private readonly now = () => new Date(),
  ) {}

  /**
   * 관리자만 부른다(라우트에서 권한 확인). 한 번에 한 장.
   * 순서: 검사·재인코딩 → 새 UUID 로 파일 쓰기 → DB 트랜잭션(요청 예약·이미지 기록·감사·결과).
   * 트랜잭션이 실패하거나 같은 요청의 기존 결과를 돌려주게 되면 방금 쓴 파일은 지운다.
   */
  async upload(actor: Principal, requestIdValue: unknown, data: Buffer) {
    const requestId = requireRequestId(requestIdValue);
    const digest = createHash('sha256').update(data).digest('hex');
    let clean;
    try {
      clean = await cleanImage(data, this.limits);
    } catch (error) {
      if (error instanceof ImageRejected) throw new ContentError(error.code === 'TOO_LARGE' ? 413 : 415, `IMAGE_${error.code}`);
      throw error;
    }
    const id = newMediaId();
    try {
      await this.blobs.put(id, clean.format, clean.data);
    } catch {
      throw new ContentError(503, 'STORAGE_UNAVAILABLE');
    }
    let outcome;
    try {
      outcome = await once(this.store, actor.userId, requestId, 'media.upload', {digest}, async tx => {
        const record: MediaRecord = {id, format: clean.format, mime: clean.mime, width: clean.width, height: clean.height, bytes: clean.data.length, sha256: clean.sha256, status: 'active', createdBy: actor.userId, createdAt: this.now()};
        await tx.insertMedia(record);
        await tx.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action: 'media.upload', targetType: 'media', targetId: id, detail: safeDetail({bytes: record.bytes, mime: record.mime, width: record.width, height: record.height})});
        return {status: 201, body: info(record)};
      }, this.now);
    } catch (error) {
      await this.blobs.remove(id, clean.format).catch(() => undefined);
      if (error instanceof ContentError) throw error;
      throw new ContentError(503, 'STORAGE_UNAVAILABLE');
    }
    if (outcome.replayed) await this.blobs.remove(id, clean.format).catch(() => undefined);
    return outcome;
  }

  /**
   * 폐기: 어떤 판(작업·공개·지난 판)이든 참조하면 거부한다. 참조 확인과 상태 변경이 한 트랜잭션이라
   * 동시에 다른 서버가 이 이미지를 글에 붙여도 둘 중 하나만 성공한다.
   * 파일은 지우지 않는다(실제 삭제는 참조가 없음을 다시 확인하는 별도 정리 작업으로 남김).
   */
  retire(actor: Principal, id: string, body: unknown) {
    if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(k => k !== 'requestId')) throw new ContentError(400, 'INVALID_BODY');
    const requestId = requireRequestId((body as Record<string, unknown>).requestId);
    return once(this.store, actor.userId, requestId, 'media.retire', {id}, async tx => {
      const media = isMediaId(id) ? await tx.getMedia(id) : null;
      if (!media) throw new ContentError(404, 'NOT_FOUND');
      const refs = await tx.mediaReferences(id);
      if (refs.revisions > 0) throw new ContentError(409, 'MEDIA_IN_USE', {revisions: refs.revisions, published: refs.published});
      if (media.status === 'active' && !(await tx.setMediaStatus(id, 'active', 'retired'))) throw new ContentError(409, 'MEDIA_STATE_CHANGED');
      await tx.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action: 'media.retire', targetType: 'media', targetId: id, detail: {}});
      return {status: 200, body: {...info(media), status: 'retired' as const}};
    }, this.now);
  }

  async adminInfo(id: string): Promise<MediaInfo> {
    return info(await this.mustGet(id));
  }

  /** 관리자 미리보기용. 작업 판·폐기 이미지도 볼 수 있다. */
  async adminRead(id: string) {
    return this.bytes(await this.mustGet(id));
  }

  /** 공개: 게시 중인 글의 '공개 판'이 쓰는 활성 이미지만. 그 밖은 존재 여부를 드러내지 않고 404. */
  async publicRead(id: string) {
    const media = isMediaId(id) ? await this.store.getMedia(id) : null;
    if (!media || media.status !== 'active' || !(await this.store.mediaReferences(id)).published) throw new ContentError(404, 'NOT_FOUND');
    return this.bytes(media);
  }

  private async bytes(media: MediaRecord) {
    const data = await this.blobs.get(media.id, media.format);
    if (!data) throw new ContentError(404, 'NOT_FOUND');
    return {mime: media.mime, data, sha256: media.sha256, name: `${media.id}.${media.format === 'jpeg' ? 'jpg' : media.format}`};
  }

  private async mustGet(id: string) {
    const media = isMediaId(id) ? await this.store.getMedia(id) : null;
    if (!media) throw new ContentError(404, 'NOT_FOUND');
    return media;
  }
}
