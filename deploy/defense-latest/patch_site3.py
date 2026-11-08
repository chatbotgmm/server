#!/usr/bin/env python3
# 16차: 운영 dist/src/site.js(15차 결과 2a618247…)에 랜덤 디펜스 「친구랑 같이하기」 방 주소(/defense/room…)를 더한다.
# 넣는 코드는 저장소 src/site.ts를 tsc로 번역한 그대로(routes.js·store.js). 사용: python3 -I patch_site3.py <원본 site.js> <출력 site.js>
import hashlib, sys
from pathlib import Path
here = Path(__file__).resolve().parent
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()
want = '2a6182477fa733659187e9296f6a08f70353087c24bbbf9fd2ef43d80e5f5c3d'
got = hashlib.sha256(s.encode('utf-8')).hexdigest()
assert got == want, f'원본 지문이 다름: {got}'
def rep(old, new):
    global s
    assert s.count(old) == 1, (s.count(old), old[:60])
    s = s.replace(old, new)
rep("import { createHash } from 'node:crypto';", "import { createHash, randomBytes, randomInt } from 'node:crypto';")
end = "        return reply.type(types[ext]).header('Cache-Control', 'no-cache').send(body);\n    });\n}\n"
rep(end, end[:-2] + (here / 'routes.js').read_text(encoding='utf-8') + "}\n")
mark = "// ── 지금 게임(60종, roster60) 자료 읽기"
rep(mark, "// ── 랜덤 디펜스 「친구랑 같이하기」 방: 6자리 코드·2명·메모리에만(서버를 다시 켜면 사라짐). 시작(방장)·속도·멈춤만 함께 바뀜 ──\n" + (here / 'store.js').read_text(encoding='utf-8') + mark)
open(out, 'w', encoding='utf-8').write(s)
print('완료', hashlib.sha256(s.encode('utf-8')).hexdigest())
