#!/usr/bin/env python3
# 15차: 운영 dist/src/site.js(10차 결과 0d612929…)의 /defense/* 주소가 배경음악(mp3)도 내보내게 한다(부분 요청 Range 지원).
# 사용: python3 -I patch_site2.py <원본 site.js> <출력 site.js>
import hashlib, sys
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()
want = '0d61292950032b83a65af9537e50c970b2b0f26f6bd1b9fd6c65decaa174038f'
got = hashlib.sha256(s.encode('utf-8')).hexdigest()
assert got == want, f'원본 지문이 다름: {got}'
old = """        const rel = req.params['*'], ext = /^[a-z0-9_-]+\\.(js|css)$/.exec(rel)?.[1];
        if (!ext)
            return reply.code(404).send({ error: 'not found' });
        let body;
        try {
            body = readFileSync(resolve(opt.root, 'public/defense', rel));
        }
        catch {
            return reply.code(404).send({ error: 'not found' });
        }
        return reply.type(types[ext]).header('Cache-Control', 'no-cache').send(body);"""
new = """        const rel = req.params['*'], ext = /^[a-z0-9_-]+\\.(js|css|mp3)$/.exec(rel)?.[1];
        if (!ext)
            return reply.code(404).send({ error: 'not found' });
        let body;
        try {
            body = readFileSync(resolve(opt.root, 'public/defense', rel));
        }
        catch {
            return reply.code(404).send({ error: 'not found' });
        }
        // 배경음악: 브라우저(특히 사파리)가 부분 요청(Range)으로 받으므로 206 응답을 지원한다
        if (ext === 'mp3') {
            reply.type('audio/mpeg').header('Accept-Ranges', 'bytes').header('Cache-Control', 'public, max-age=86400');
            const m = /^bytes=(\\d*)-(\\d*)$/.exec(String(req.headers.range ?? ''));
            if (m && (m[1] || m[2])) {
                const size = body.length;
                const start = m[1] ? Number(m[1]) : Math.max(0, size - Number(m[2]));
                const end = m[1] && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;
                if (start >= size || start > end)
                    return reply.code(416).header('Content-Range', `bytes */${size}`).send();
                return reply.code(206).header('Content-Range', `bytes ${start}-${end}/${size}`).send(body.subarray(start, end + 1));
            }
            return reply.send(body);
        }
        return reply.type(types[ext]).header('Cache-Control', 'no-cache').send(body);"""
assert s.count(old) == 1, s.count(old)
s = s.replace(old, new)
open(out, 'w', encoding='utf-8').write(s)
print('완료', hashlib.sha256(s.encode('utf-8')).hexdigest())
