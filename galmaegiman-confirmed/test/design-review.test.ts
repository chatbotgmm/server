import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import Fastify, { type FastifyInstance } from 'fastify';
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { registerDesignReview } from '../src/design-review.js';

describe('공개 디자인 검토 갤러리 정적 경로', () => {
  let root: string;
  let gallery: string;
  let app: FastifyInstance;
  const files: Record<string, { type: string; body: string }> = {
    'index.html': { type: 'text/html', body: '<!doctype html><title>모험 도감</title>' },
    'app.js': { type: 'text/javascript', body: 'document.documentElement.dataset.ready="true";' },
    'style.css': { type: 'text/css', body: ':root { color: #302b25; }' },
    'manifest.json': { type: 'application/json', body: '{"screens":21}' },
    'images/01-title.webp': { type: 'image/webp', body: 'final vision' },
    'thumbs/01-title.webp': { type: 'image/webp', body: 'thumbnail' },
    'assets/images/01-title.webp': { type: 'image/webp', body: 'alternate image path' },
    'before/lobby.webp': { type: 'image/webp', body: 'previous lobby' },
    'before/battle.png': { type: 'image/png', body: 'previous battle' },
    'after/lobby.webp': { type: 'image/webp', body: 'current lobby' },
    'after/battle.png': { type: 'image/png', body: 'current battle' },
    'assets/fonts/Jua-Regular.ttf': { type: 'font/ttf', body: 'Jua' },
    'assets/fonts/GowunDodum-Regular.ttf': { type: 'font/ttf', body: 'Gowun Dodum' },
    'downloads/final-vision.zip': { type: 'application/zip', body: 'PK\x03\x04' },
  };

  beforeEach(async () => {
    root = await mkdtemp(join(tmpdir(), 'gmm-design-review-'));
    gallery = join(root, 'public/design-review');
    for (const [path, file] of Object.entries(files)) {
      await mkdir(join(gallery, path, '..'), { recursive: true });
      await writeFile(join(gallery, path), file.body);
    }
    app = Fastify();
    registerDesignReview(app, { root });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await rm(root, { recursive: true, force: true });
  });

  it('슬래시 주소로 정규화하고 로그인 없이 첫 화면과 명시한 정적 파일을 제공한다', async () => {
    const redirect = await app.inject({ method: 'GET', url: '/design-review' });
    expect(redirect.statusCode).toBe(308);
    expect(redirect.headers.location).toBe('/design-review/');
    expect(redirect.headers['cache-control']).toBe('no-cache');
    const index = await app.inject({ method: 'GET', url: '/design-review/' });
    expect(index.statusCode).toBe(200);
    expect(index.headers['content-type']).toContain('text/html');
    expect(index.headers['cache-control']).toBe('no-cache');
    expect(index.body).toBe(files['index.html'].body);
    for (const [path, file] of Object.entries(files)) {
      const response = await app.inject({ method: 'GET', url: `/design-review/${path}` });
      expect(response.statusCode, path).toBe(200);
      expect(response.headers['content-type'], path).toContain(file.type);
      expect(response.headers['x-content-type-options'], path).toBe('nosniff');
      expect(response.headers['cache-control'], path).toBe(path === 'index.html' ? 'no-cache' : 'public, max-age=300');
      expect(response.body, path).toBe(file.body);
    }
  });

  it('그림과 묶음 파일은 원래 바이트를 유지하고 다운로드·HEAD를 지원한다', async () => {
    const download = await app.inject({ method: 'GET', url: '/design-review/downloads/final-vision.zip' });
    expect(download.headers['content-disposition']).toBe('attachment; filename="final-vision.zip"');
    expect(download.rawPayload).toEqual(Buffer.from(files['downloads/final-vision.zip'].body));
    const head = await app.inject({ method: 'HEAD', url: '/design-review/after/lobby.webp' });
    expect(head.statusCode).toBe(200);
    expect(head.headers['content-type']).toBe('image/webp');
    expect(head.body).toBe('');
  });

  it('없는 파일·디렉터리·허용하지 않은 확장자와 공개 경로는 404로 응답한다', async () => {
    await writeFile(join(gallery, 'private.json'), '{"player":"not public"}');
    await writeFile(join(gallery, 'images/secret.html'), '<script>not allowed</script>');
    for (const path of ['images/missing.webp', 'images/', 'before/', 'downloads/', 'private.json', 'images/secret.html', 'assets/fonts/Other.ttf', '.env', 'app.js.map']) {
      const response = await app.inject({ method: 'GET', url: `/design-review/${path}` });
      expect(response.statusCode, path).toBe(404);
      expect(response.body, path).not.toContain('not public');
      expect(response.body, path).not.toContain(root);
    }
  });

  it('일반·인코딩·역슬래시 경로 탈출과 공개 폴더 밖을 가리키는 링크를 거부한다', async () => {
    const secret = join(root, 'secret.webp');
    await writeFile(secret, 'must stay outside gallery');
    await symlink(secret, join(gallery, 'images/outside.webp'));
    await symlink(join(gallery, 'manifest.json'), join(gallery, 'images/inside.webp'));
    for (const path of ['../secret.webp', '%2e%2e/secret.webp', 'images/%2e%2e/%2e%2e/secret.webp', 'images/..%2f..%2fsecret.webp', 'images/..%5c..%5csecret.webp', 'images/outside.webp', 'images/inside.webp']) {
      const response = await app.inject({ method: 'GET', url: `/design-review/${path}` });
      expect(response.statusCode, path).toBe(404);
      expect(response.body, path).not.toContain('must stay outside gallery');
    }
  });
});
