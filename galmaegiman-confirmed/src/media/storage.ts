import {mkdir, open, readFile, rename, rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {join, resolve, sep} from 'node:path';
import type {ImageFormat} from './validate.js';
import {isMediaId, newMediaId} from '../content-admin/ids.js';

/**
 * 이미지 바이트 저장소. 파일 이름은 서버가 만든 UUID 와 형식 확장자뿐이다.
 * 사용자가 보낸 파일 이름은 어디에도 쓰지 않는다.
 */
export interface MediaBlobStorage {
  put(id: string, format: ImageFormat, data: Buffer): Promise<void>;
  get(id: string, format: ImageFormat): Promise<Buffer | null>;
  remove(id: string, format: ImageFormat): Promise<void>;
}

const EXT: Record<ImageFormat, string> = {png: 'png', jpeg: 'jpg', webp: 'webp'};

export {isMediaId, newMediaId};

export class MediaPathError extends Error {}

/**
 * 디스크 저장소. 웹 서버 정적 폴더(public/) 밖의 전용 폴더를 써야 한다.
 * 파일은 실행 권한 없이(0640) 쓰고, 응답은 항상 API 가 정한 Content-Type 으로만 내보낸다.
 */
export class FileMediaStorage implements MediaBlobStorage {
  private readonly root: string;
  constructor(root: string) {
    this.root = resolve(root);
  }
  private path(id: string, format: ImageFormat): string {
    if (!isMediaId(id) || !(format in EXT)) throw new MediaPathError('invalid media id');
    const target = resolve(this.root, id.slice(0, 2), `${id}.${EXT[format]}`);
    if (!target.startsWith(this.root + sep)) throw new MediaPathError('path escaped storage root');
    return target;
  }
  async put(id: string, format: ImageFormat, data: Buffer): Promise<void> {
    const target = this.path(id, format);
    await mkdir(join(target, '..'), {recursive: true, mode: 0o750});
    const temp = `${target}.${randomUUID()}.tmp`;
    const handle = await open(temp, 'wx', 0o640);
    try {
      await handle.writeFile(data);
      await handle.sync();
    } finally {
      await handle.close();
    }
    try {
      await rename(temp, target);
    } catch (error) {
      await rm(temp, {force: true});
      throw error;
    }
  }
  async get(id: string, format: ImageFormat): Promise<Buffer | null> {
    try {
      return await readFile(this.path(id, format));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null;
      throw error;
    }
  }
  async remove(id: string, format: ImageFormat): Promise<void> {
    await rm(this.path(id, format), {force: true});
  }
}

/** 시험·개발용 메모리 저장소. */
export class MemoryMediaStorage implements MediaBlobStorage {
  readonly files = new Map<string, Buffer>();
  failNextPut = false;
  async put(id: string, format: ImageFormat, data: Buffer): Promise<void> {
    if (!isMediaId(id)) throw new MediaPathError('invalid media id');
    if (this.failNextPut) {
      this.failNextPut = false;
      throw new Error('storage unavailable');
    }
    this.files.set(`${id}.${EXT[format]}`, Buffer.from(data));
  }
  async get(id: string, format: ImageFormat): Promise<Buffer | null> {
    if (!isMediaId(id)) return null;
    return this.files.get(`${id}.${EXT[format]}`) ?? null;
  }
  async remove(id: string, format: ImageFormat): Promise<void> {
    this.files.delete(`${id}.${EXT[format]}`);
  }
}
