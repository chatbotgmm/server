import sharp from 'sharp';
import {randomBytes} from 'node:crypto';
import {mkdtemp, readdir, stat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {describe, expect, it} from 'vitest';
import {FileMediaStorage, isMediaId, newMediaId} from '../src/media/storage.js';
import {cleanImage, ImageRejected, sniffFormat, type ImageLimits} from '../src/media/validate.js';

const image = (format: 'png' | 'jpeg' | 'webp', width = 32, height = 24) =>
  sharp({create: {width, height, channels: 3, background: {r: 200, g: 40, b: 40}}})[format]().toBuffer();

async function rejected(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ImageRejected) return error.code;
    throw error;
  }
  throw new Error('expected rejection');
}

describe('이미지 검사', () => {
  it.each(['png', 'jpeg', 'webp'] as const)('%s: 실제 디코딩 후 같은 형식으로 다시 저장', async format => {
    const clean = await cleanImage(await image(format));
    expect(clean.format).toBe(format);
    expect(clean.mime).toBe(`image/${format}`);
    expect([clean.width, clean.height]).toEqual([32, 24]);
    expect(sniffFormat(clean.data)).toBe(format);
    expect(clean.sha256).toMatch(/^[0-9a-f]{64}$/);
  });

  it('EXIF 등 메타데이터를 지운다(방향은 픽셀에 반영)', async () => {
    const withExif = await sharp({create: {width: 40, height: 20, channels: 3, background: '#336699'}})
      .withMetadata({orientation: 6, exif: {IFD0: {Copyright: 'private-owner', Artist: 'private-name'}}})
      .jpeg().toBuffer();
    expect((await sharp(withExif).metadata()).exif).toBeDefined();
    const clean = await cleanImage(withExif);
    const meta = await sharp(clean.data).metadata();
    expect(meta.exif).toBeUndefined();
    expect(meta.icc).toBeUndefined();
    expect(meta.xmp).toBeUndefined();
    expect(meta.orientation ?? 1).toBe(1);
    expect([clean.width, clean.height]).toEqual([20, 40]);
    expect(clean.data.includes(Buffer.from('private-owner'))).toBe(false);
  });

  it('확장자·MIME 위장: 내용이 HTML/SVG/스크립트면 거부', async () => {
    expect(await rejected(cleanImage(Buffer.from('<html><script>alert(1)</script></html>')))).toBe('UNSUPPORTED_TYPE');
    expect(await rejected(cleanImage(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>')))).toBe('UNSUPPORTED_TYPE');
    expect(await rejected(cleanImage(Buffer.from('GIF89a......')))).toBe('UNSUPPORTED_TYPE');
  });

  it('서명만 흉내 낸 파일·잘린 파일은 디코딩 실패로 거부', async () => {
    const fakePng = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.from('<script>alert(1)</script>')]);
    expect(await rejected(cleanImage(fakePng))).toBe('DECODE_FAILED');
    const real = await image('png', 200, 200);
    expect(await rejected(cleanImage(real.subarray(0, Math.floor(real.length / 2))))).toBe('DECODE_FAILED');
    const jpeg = await image('jpeg', 200, 200);
    expect(await rejected(cleanImage(Buffer.concat([jpeg.subarray(0, 120), Buffer.alloc(64)])))).toBe('DECODE_FAILED');
  });

  it('PNG 서명 뒤에 HTML 을 붙인 다중형식 파일도 다시 인코딩되어 HTML 이 남지 않는다', async () => {
    const polyglot = Buffer.concat([await image('png'), Buffer.from('<html><script>alert(1)</script></html>')]);
    try {
      const clean = await cleanImage(polyglot);
      expect(clean.data.includes(Buffer.from('<script>'))).toBe(false);
    } catch (error) {
      expect(error).toBeInstanceOf(ImageRejected);
    }
  });

  it('형식 서명과 실제 디코딩 형식이 다르면 거부', async () => {
    const webp = await image('webp');
    const lying = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), webp]);
    expect(['SIGNATURE_MISMATCH', 'DECODE_FAILED']).toContain(await rejected(cleanImage(lying)));
  });

  it('용량·가로세로·픽셀 수 제한', async () => {
    const small: ImageLimits = {maxBytes: 2000, maxWidth: 100, maxHeight: 100, maxPixels: 5000};
    expect(await rejected(cleanImage(Buffer.alloc(0)))).toBe('EMPTY');
    const noisy = await sharp(randomBytes(90 * 90 * 3), {raw: {width: 90, height: 90, channels: 3}}).png().toBuffer();
    expect(noisy.length).toBeGreaterThan(2000);
    expect(await rejected(cleanImage(noisy, small))).toBe('TOO_LARGE');
    expect(await rejected(cleanImage(await image('png', 101, 10), small))).toBe('DIMENSIONS');
    expect(await rejected(cleanImage(await image('png', 80, 80), small))).toBe('DIMENSIONS');
  });

  it('픽셀 폭탄(작은 파일, 거대한 크기 선언)은 디코딩 전에 거부', async () => {
    const bomb = await sharp({create: {width: 7000, height: 7000, channels: 3, background: {r: 0, g: 0, b: 0}}}).png({compressionLevel: 9}).toBuffer();
    expect(bomb.length).toBeLessThan(1024 * 1024);
    expect(['DIMENSIONS', 'DECODE_FAILED']).toContain(await rejected(cleanImage(bomb)));
  });

  it('애니메이션 WebP 는 거부', async () => {
    let animated: Buffer;
    try {
      // 16x32 원시 픽셀을 16x16 두 프레임으로 나눠 애니메이션 WebP 를 만든다.
      animated = await sharp(randomBytes(16 * 32 * 4), {raw: {width: 16, height: 32, channels: 4, pageHeight: 16}} as never).webp({loop: 0}).toBuffer();
    } catch {
      return; // 이 sharp 빌드가 애니메이션 생성을 지원하지 않으면 생략(보고서에 미검증으로 표시)
    }
    expect((await sharp(animated).metadata()).pages).toBe(2);
    expect(await rejected(cleanImage(animated))).toBe('ANIMATED');
  });
});

describe('저장 경로', () => {
  it('서버가 만든 UUID 만 받고, 경로 조작은 거부한다', async () => {
    const root = await mkdtemp(join(tmpdir(), 'notice-media-'));
    const storage = new FileMediaStorage(root);
    for (const bad of ['../../etc/passwd', '..', '/etc/passwd', 'a/b', 'x.png', '..%2F..%2Fx', '00000000-0000-4000-8000-00000000000/', '00000000-0000-4000-8000-000000000000\u0000.png']) {
      expect(isMediaId(bad)).toBe(false);
      await expect(storage.put(bad, 'png', Buffer.from('x'))).rejects.toThrow();
      await expect(storage.get(bad, 'png')).rejects.toThrow();
    }
    const id = newMediaId();
    expect(isMediaId(id)).toBe(true);
    await storage.put(id, 'png', await image('png'));
    expect((await storage.get(id, 'png'))!.length).toBeGreaterThan(0);
    const dir = join(root, id.slice(0, 2));
    expect(await readdir(dir)).toEqual([`${id}.png`]);
    const mode = (await stat(join(dir, `${id}.png`))).mode & 0o777;
    expect(mode & 0o111).toBe(0);
  });
});
