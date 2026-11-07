#!/usr/bin/env python3
# 9차: 운영 dist/src/my.js(0f485633…)에 GameMaker 게임용 카카오 로그인을 덧붙인다.
# 사용: python3 -I patch_my.py <원본 my.js> <출력 my.js>
import hashlib, sys

src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()
want = '0f485633e49d8c47374e9bf6cbd2f659233e0e9c5171c3b2c50bf63f6f35ad0d'
got = hashlib.sha256(s.encode('utf-8')).hexdigest()
assert got == want, f'원본 지문이 다름: {got}'

def put(anchor, text, after=True):
    global s
    assert s.count(anchor) == 1, f'기준 줄이 하나가 아님({s.count(anchor)}): {anchor[:80]}'
    s = s.replace(anchor, anchor + text if after else text + anchor)

EXPIRED = "gmPage('로그인 요청이 만료됐어요', '<p>게임에서 「카카오로 로그인」을 다시 눌러 주세요. 이 창은 닫아도 돼요.</p>')"

# 1) 세션 확인(preHandler)을 거치지 않는 경로: 게임 카카오 로그인 시작·결과 확인·브라우저 확인 버튼
put("""        if (path === '/review')
            return; // 심사용 로그인 페이지: 기존 브라우저 쿠키와 무관하게 동작
""", """        // GameMaker 게임 카카오 로그인(9차): 브라우저에 남은 예전 쿠키와 무관하게 동작
        if (req.method === 'POST' && (path === '/my/api/app/kakao-start' || path === '/my/api/app/kakao-poll' || path === '/my/kakao/gm'))
            return;
        if (req.method === 'GET' && ((path === '/my/kakao' && req.query?.gm !== undefined) || (path === '/my/kakao/callback' && cookie(req, KAPP)?.startsWith('gm:'))))
            return;
""")

# 2) 상태 저장소·안내 화면
put("""    const appLinkPending = new Map();
""", """    // GameMaker 게임 카카오 로그인(9차): 게임이 시작 요청 → 브라우저에서 카카오 로그인 → 확인 버튼 → 게임이 2초마다 결과를 물어 열쇠를 받음.
    // 주소에는 요청 번호(ticket)만 들어가고, 열쇠를 받는 비밀값(secret)은 게임만 가진다. 확인 화면에 게임과 같은 확인 글자를 보여 준다(남이 보낸 주소로 로그인되는 것 방지).
    const gmLogins = new Map();
    const gmPage = (title, body) => `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>갈매미맨 방범대 로그인</title>
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#07131c;color:#f3ead4;font-family:system-ui,-apple-system,'Malgun Gothic',sans-serif;padding:16px;box-sizing:border-box;word-break:keep-all}main{width:100%;max-width:420px;background:#0f2230;border:1px solid #d4b06a;border-radius:12px;padding:24px;box-sizing:border-box}h1{margin:0 0 12px;font-size:22px;color:#f6d98f}p{line-height:1.6;margin:10px 0;color:#d9cfb8}.word{margin:16px 0;padding:14px;text-align:center;font-size:34px;font-weight:800;letter-spacing:8px;color:#f6d98f;background:#07131c;border:1px solid #6b5a33;border-radius:10px}.note{font-size:14px;color:#a9b4bd}button{display:block;width:100%;margin-top:12px;padding:14px;font:inherit;font-size:18px;font-weight:700;border-radius:10px;border:0;cursor:pointer;background:#d4b06a;color:#1b1b1b}button.quiet{background:transparent;color:#d9cfb8;border:1px solid #4a5a66}</style></head><body><main><h1>${title}</h1>${body}</main></body></html>`;
""")

# 3) 시작·결과 확인 API (앱 교환 API 바로 뒤)
put("""    // 「갈매미맨 수비대」(대결 게임) 열쇠: 로그인한 사람만(최상위 그림·이름이 나오는 게임이라 공개하지 않음)
""", """    app.post('/my/api/app/kakao-start', { config: { rateLimit: { max: 10, timeWindow: '1 minute', keyGenerator: ipKey('mygmstart') } } }, async (req, reply) => {
        if (!guard(req, reply))
            return;
        reply.header('Cache-Control', 'no-store');
        if (!kakao)
            return reply.code(404).send({ error: '카카오 로그인을 사용할 수 없어요.' });
        const now = Date.now();
        for (const [k, v] of gmLogins)
            if (v.exp < now)
                gmLogins.delete(k);
        if (gmLogins.size >= 2000)
            return reply.code(503).send({ error: '잠시 뒤 다시 시도해 주세요.' });
        const ticket = randomBytes(18).toString('base64url'), secret = randomBytes(32).toString('base64url');
        const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', word = Array.from(randomBytes(4), (b) => letters[b % 32]).join('');
        gmLogins.set(ticket, { secret, word, exp: now + 600_000 });
        return { url: `${await origin(req)}/my/kakao?gm=${ticket}`, ticket, secret, word, wait: 2 };
    });
    app.post('/my/api/app/kakao-poll', { config: { rateLimit: { max: 90, timeWindow: '1 minute', keyGenerator: ipKey('mygmpoll') } } }, async (req, reply) => {
        if (!guard(req, reply))
            return;
        reply.header('Cache-Control', 'no-store');
        const ticket = typeof req.body?.ticket === 'string' ? req.body.ticket : '', secret = typeof req.body?.secret === 'string' ? req.body.secret : '';
        const g = gmLogins.get(ticket);
        const same = !!g && Buffer.byteLength(secret) === Buffer.byteLength(g.secret) && timingSafeEqual(Buffer.from(secret), Buffer.from(g.secret));
        if (!g || !same)
            return reply.code(400).send({ error: '로그인 요청을 찾지 못했어요. 다시 시작해 주세요.' });
        if (g.exp < Date.now()) {
            gmLogins.delete(ticket);
            return reply.code(400).send({ error: '로그인 시간(10분)이 지났어요. 다시 시작해 주세요.' });
        }
        if (g.error) {
            gmLogins.delete(ticket);
            return reply.code(400).send({ error: g.error });
        }
        if (!g.done)
            return { pending: true, step: g.kakao ? 'confirm' : 'wait' };
        gmLogins.delete(ticket); // 한 번만
        try {
            return { token: issue(await game.webKakaoSignIn(g.kakao)) };
        }
        catch (error) {
            if (error instanceof GameError)
                return reply.code(409).send({ error: error.message });
            throw error;
        }
    });
""", after=False)

# 4) 카카오 로그인 화면으로 보내기(게임 요청 번호가 있으면)
put("""        const q = req.query;
        let linkTarget = '';
""", """        if (q.gm !== undefined) {
            const ticket = typeof q.gm === 'string' ? q.gm : '', g = gmLogins.get(ticket);
            if (!g || g.exp < Date.now() || g.kakao || g.error)
                return reply.code(400).type('text/html; charset=utf-8').header('Cache-Control', 'no-store').send(""" + EXPIRED + """);
            const u = new URL('https://kauth.kakao.com/oauth/authorize');
            u.search = new URLSearchParams({ client_id: kakao.restKey, redirect_uri: `${await origin(req)}/my/kakao/callback`, response_type: 'code', state }).toString();
            reply.header('Set-Cookie', [cookieLine(req, KSTATE, state, 600), cookieLine(req, KAPP, 'gm:' + ticket, 600), cookieLine(req, KLINK, '', 0)]);
            return reply.redirect(u.toString());
        }
""")

# 5) 카카오 로그인 취소·실패 때 게임에도 알림
put("""    const kakaoBack = (req, reply, error) => {
        const appC = cookie(req, KAPP);
""", """        if (appC?.startsWith('gm:')) {
            const g = gmLogins.get(appC.slice(3));
            if (g && !g.kakao && !g.error)
                g.error = error === 'cancel' ? '카카오 로그인을 취소했어요. 다시 시작해 주세요.' : '카카오 로그인에 실패했어요. 다시 시작해 주세요.';
            return reply.header('Set-Cookie', [cookieLine(req, KSTATE, '', 0), cookieLine(req, KAPP, '', 0)]).code(400).type('text/html; charset=utf-8').header('Cache-Control', 'no-store')
                .send(gmPage(error === 'cancel' ? '로그인을 취소했어요' : '로그인하지 못했어요', '<p>게임으로 돌아가 다시 시도해 주세요. 이 창은 닫아도 돼요.</p>'));
        }
""")

# 6) 카카오 로그인 성공: 확인 화면(게임에 나온 확인 글자와 같은지 보고 누름)
put("""        const linked = await game.webKakaoPlayer(kakaoId);
        const appC = cookie(req, KAPP);
""", """        if (appC?.startsWith('gm:')) {
            const ticket = appC.slice(3), g = gmLogins.get(ticket);
            reply.header('Set-Cookie', [clear, cookieLine(req, KAPP, '', 0), cookieLine(req, KLINK, '', 0)]).type('text/html; charset=utf-8').header('Cache-Control', 'no-store').header('Referrer-Policy', 'no-referrer');
            if (!g || g.exp < Date.now() || g.kakao || g.error)
                return reply.code(400).send(""" + EXPIRED + """);
            g.kakao = kakaoId;
            g.nonce = randomBytes(18).toString('base64url');
            return gmPage('게임에 로그인할까요?', `<p>게임 화면에 나온 확인 글자와 같은지 봐 주세요.</p><div class="word">${g.word}</div>
<p class="note">게임에서 직접 「카카오로 로그인」을 누른 게 아니라면 「취소」를 누르세요. 다른 사람이 보낸 주소일 수 있어요.</p>
<form method="post" action="/my/kakao/gm"><input type="hidden" name="ticket" value="${ticket}"><input type="hidden" name="nonce" value="${g.nonce}">
<button type="submit" name="act" value="ok">같아요, 게임에 로그인</button><button type="submit" name="act" value="cancel" class="quiet">취소</button></form>`);
        }
""")

# 7) 확인 버튼(HTML 폼) 받기: 이 경로만 폼 해석
tail = """        return back(reply.header('Set-Cookie', [clear, cookieLine(req, KPEND, signed('kp.', kakaoId, 600), 600)]), '?kakao=link');
    });
"""
put(tail, """    app.register(async (sub) => {
        sub.addContentTypeParser('application/x-www-form-urlencoded', { parseAs: 'string', bodyLimit: 2048 }, (_req, body, done) => { try {
            done(null, Object.fromEntries(new URLSearchParams(String(body))));
        }
        catch (e) {
            done(e, undefined);
        } });
        sub.post('/my/kakao/gm', { bodyLimit: 2048, config: { rateLimit: { max: 20, timeWindow: '1 minute', keyGenerator: ipKey('mygmok') } } }, async (req, reply) => {
            reply.type('text/html; charset=utf-8').header('Cache-Control', 'no-store').header('Referrer-Policy', 'no-referrer');
            const ticket = typeof req.body?.ticket === 'string' ? req.body.ticket : '', nonce = typeof req.body?.nonce === 'string' ? req.body.nonce : '';
            const g = gmLogins.get(ticket);
            const same = !!g?.nonce && Buffer.byteLength(nonce) === Buffer.byteLength(g.nonce) && timingSafeEqual(Buffer.from(nonce), Buffer.from(g.nonce));
            if (!g || !same || g.exp < Date.now() || g.done || g.error)
                return reply.code(400).send(""" + EXPIRED + """);
            g.nonce = undefined;
            if (req.body?.act !== 'ok') {
                g.error = '브라우저에서 로그인을 취소했어요.';
                return gmPage('취소했어요', '<p>게임에는 로그인하지 않았어요. 이 창은 닫아도 돼요.</p>');
            }
            g.done = true;
            return gmPage('로그인했어요', '<p>이제 게임으로 돌아가세요. 몇 초 안에 게임이 저절로 이어져요. 이 창은 닫아도 돼요.</p>');
        });
    });
""")

open(out, 'w', encoding='utf-8').write(s)
print('완료', hashlib.sha256(s.encode('utf-8')).hexdigest())
