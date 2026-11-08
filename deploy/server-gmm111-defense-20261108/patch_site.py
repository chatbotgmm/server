#!/usr/bin/env python3
# 10차: 운영 dist/src/site.js(acdad90a…)에 랜덤 디펜스 주소(/defense, /defense/*)를 덧붙인다.
# 사용: python3 -I patch_site.py <원본 site.js> <출력 site.js>
import hashlib, sys
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()
want = 'acdad90afe11e129da1ad26594e193f789089189fa78e2145a535d576eab3eb8'
got = hashlib.sha256(s.encode('utf-8')).hexdigest()
assert got == want, f'원본 지문이 다름: {got}'
anchor = """            return reply.type(types[ext]).header('Cache-Control', ext === 'css' || ext === 'js' ? 'public, max-age=300' : 'public, max-age=86400').send(body);
        });
}
"""
assert s.count(anchor) == 1, s.count(anchor)
add = """            return reply.type(types[ext]).header('Cache-Control', ext === 'css' || ext === 'js' ? 'public, max-age=300' : 'public, max-age=86400').send(body);
        });
    // ── 갈매미맨 랜덤 디펜스(로그인 없는 미니 게임, public/defense). 그림·글꼴은 /game60/assets를 같이 쓴다 ──
    // 모듈 파일(defense.js → engine.js → data.js)이 서로 다른 판으로 섞이지 않게 매번 새로 받게 한다(no-cache)
    app.get('/defense', limit, async (_req, reply) => {
        let body;
        try {
            body = readFileSync(resolve(opt.root, 'public/defense/index.html'), 'utf8');
        }
        catch {
            return reply.code(404).send({ error: 'not found' });
        }
        return send(reply, body, 'no-cache');
    });
    app.get('/defense/*', limit, async (req, reply) => {
        const rel = req.params['*'], ext = /^[a-z0-9_-]+\\.(js|css)$/.exec(rel)?.[1];
        if (!ext)
            return reply.code(404).send({ error: 'not found' });
        let body;
        try {
            body = readFileSync(resolve(opt.root, 'public/defense', rel));
        }
        catch {
            return reply.code(404).send({ error: 'not found' });
        }
        return reply.type(types[ext]).header('Cache-Control', 'no-cache').send(body);
    });
}
"""
s = s.replace(anchor, add)
open(out, 'w', encoding='utf-8').write(s)
print('완료', hashlib.sha256(s.encode('utf-8')).hexdigest())
