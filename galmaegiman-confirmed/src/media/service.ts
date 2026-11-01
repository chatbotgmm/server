import {createHash, randomUUID} from 'node:crypto';
import type {Principal} from '../content-admin/access.js';
import {safeDetail} from '../content-admin/audit.js';
import {ContentError} from '../content-admin/errors.js';
import {once, requireRequestId} from '../content-admin/requests.js';
import {SerialQueue} from '../content-admin/serial.js';
import type {ContentStore, MediaRecord} from '../content-admin/store.js';
import {isMediaId, newMediaId, type MediaBlobStorage} from './storage.js';
import {cleanImage, DEFAULT_IMAGE_LIMITS, ImageRejected, type ImageLimits} from './validate.js';

export type MediaInfo = {id: string; mime: string; width: number; height: number; bytes: number; status: MediaRecord['status']; createdAt: string};

const info = (m: MediaRecord): MediaInfo => ({id: m.id, mime: m.mime, width: m.width, height: m.height, bytes: m.bytes, status: m.status, createdAt: m.createdAt.toISOString()});

export class MediaService {
  constructor(
    private readonly store: ContentStore,
    private readonly blobs: MediaBlobStorage,
    private readonly queue = new SerialQueue(),
    private readonly limits: ImageLimits = DEFAULT_IMAGE_LIMITS,
    private readonly now = () => new Date(),
  ) {}

  /** 관리자만 부른다(라우트에서 권한 확인). 한 번에 한 장. 여러 장은 요청을 나눠 보낸다. */
  upload(actor: Principal, requestIdValue: unknown, data: Buffer) {
    const requestId = requireRequestId(requestIdValue);
    const digest = createHash('sha256').update(data).digest('hex');
    return this.queue.run(() => once(this.store, actor.userId, requestId, 'media.upload', {digest}, async () => {
      let clean;
      try {
        clean = await cleanImage(data, this.limits);
      } catch (error) {
        if (error instanceof ImageRejected) throw new ContentError(error.code === 'TOO_LARGE' ? 413 : 415, `IMAGE_${error.code}`);
        throw error;
      }
      const id = newMediaId();
      const record: MediaRecord = {id, format: clean.format, mime: clean.mime, width: clean.width, height: clean.height, bytes: clean.data.length, sha256: clean.sha256, status: 'active', createdBy: actor.userId, createdAt: this.now()};
      try {
        await this.blobs.put(id, clean.format, clean.data);
      } catch {
        throw new ContentError(503, 'STORAGE_UNAVAILABLE');
      }
      try {
        await this.store.insertMedia(record);
      } catch {
        // 기록이 실패하면 방금 쓴 파일을 지워 주인 없는 파일이 남지 않게 한다.
        await this.blobs.remove(id, clean.format).catch(() => undefined);
        throw new ContentError(503, 'STORAGE_UNAVAILABLE');
      }
      await this.store.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action: 'media.upload', targetType: 'media', targetId: id, detail: safeDetail({bytes: record.bytes, mime: record.mime, width: record.width, height: record.height})});
      return {status: 201, body: info(record)};
    }, this.now));
  }

  /**
   * 더 이상 쓰지 않는 이미지를 폐기한다. 현재 판이나 복원 가능한 이력이 참조하면 거부한다.
   * 파일은 지우지 않고 상태만 바꾼다(복원 이력·감사 추적 보존). 실제 삭제는 별도 정리 작업으로 남긴다.
   */
  retire(actor: Principal, id: string, body: unknown) {
    if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(k => k !== 'requestId')) throw new ContentError(400, 'INVALID_BODY');
    const requestId = requireRequestId((body as Record<string, unknown>).requestId);
    return this.queue.run(() => once(this.store, actor.userId, requestId, 'media.retire', {id}, async () => {
      const media = await this.mustGet(id);
      const refs = await this.store.mediaReferences(id);
      if (refs.current > 0 || refs.history > 0) throw new ContentError(409, 'MEDIA_IN_USE', {current: refs.current, history: refs.history});
      if (media.status !== 'retired') await this.store.setMediaStatus(id, 'retired');
      await this.store.appendAudit({id: randomUUID(), at: this.now(), actorId: actor.userId, action: 'media.retire', targetType: 'media', targetId: id, detail: {}});
      return {status: 200, body: {...info(media), status: 'retired' as const}};
    }, this.now));
  }

  async adminInfo(id: string): Promise<MediaInfo> {
    return info(await this.mustGet(id));
  }

  /** 관리자 미리보기용. 초안·폐기 이미지도 볼 수 있다. */
  async adminRead(id: string) {
    const media = await this.mustGet(id);
    return this.bytes(media);
  }

  /** 공개: 지금 게시 중인 공지가 쓰는 활성 이미지만. 그 밖은 존재 여부를 드러내지 않고 404. */
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
