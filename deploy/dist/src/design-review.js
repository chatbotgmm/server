import { readFile, realpath, stat } from 'node:fs/promises';
import { resolve } from 'node:path';
const types = {
    'index.html': 'text/html; charset=utf-8',
    'app.js': 'text/javascript; charset=utf-8',
    'style.css': 'text/css; charset=utf-8',
    'manifest.json': 'application/json; charset=utf-8',
};
// 검토용으로 복사한 파일만 공개한다. 폴더 탐색이나 게임 저장 데이터에는 접근하지 않는다.
function contentType(path) {
    if (Object.hasOwn(types, path))
        return types[path];
    if (/^(?:images|thumbs|before|after|assets\/images)\/[A-Za-z0-9][A-Za-z0-9_-]*\.(?:webp|png)$/.test(path))
        return path.endsWith('.webp') ? 'image/webp' : 'image/png';
    if (/^assets\/fonts\/(?:Jua-Regular|GowunDodum-Regular)\.ttf$/.test(path))
        return 'font/ttf';
    if (/^downloads\/[A-Za-z0-9][A-Za-z0-9_-]*\.zip$/.test(path))
        return 'application/zip';
    return null;
}
export function registerDesignReview(app, options) {
    const directory = resolve(options.root, 'public/design-review');
    const missing = (reply) => reply.code(404).header('Cache-Control', 'no-store').send({ error: 'not found' });
    const send = async (reply, path) => {
        const type = contentType(path);
        if (!type)
            return missing(reply);
        try {
            const base = await realpath(directory);
            const expected = resolve(base, path);
            const file = await realpath(expected);
            // 허용 이름을 가진 링크로 다른 파일이나 폴더를 노출하지 않는다.
            if (file !== expected || !(await stat(file)).isFile())
                return missing(reply);
            const body = await readFile(file);
            reply.type(type).header('X-Content-Type-Options', 'nosniff')
                .header('Cache-Control', path === 'index.html' ? 'no-cache' : 'public, max-age=300');
            if (type === 'application/zip')
                reply.header('Content-Disposition', `attachment; filename="${path.slice('downloads/'.length)}"`);
            return reply.send(body);
        }
        catch {
            return missing(reply);
        }
    };
    app.get('/design-review', async (_request, reply) => reply.header('Cache-Control', 'no-cache').redirect('/design-review/', 308));
    app.get('/design-review/', async (_request, reply) => send(reply, 'index.html'));
    app.get('/design-review/*', async (request, reply) => send(reply, request.params['*']));
}
