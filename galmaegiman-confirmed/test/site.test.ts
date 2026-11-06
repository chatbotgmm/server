import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import Fastify,{type FastifyInstance} from 'fastify';
import {loadContent,topTiers} from '../src/content.js';
import {registerSite,safeFrom,safeNext} from '../src/site.js';

// 공개 홈페이지: 로그인 없이 읽기, 스포일러(히든·최상위) 가림, 로그인 뒤 갈 곳·돌아갈 곳은 허용 경로만, 확인 안 된 다운로드·공지는 만들지 않음
describe('공개 홈페이지(src/site.ts)',()=>{
  const content=loadContent();
  let app:FastifyInstance;
  beforeAll(async()=>{app=Fastify();await app.register((await import('@fastify/rate-limit')).default,{global:false});registerSite(app,content,{root:process.cwd(),version:'test',kakao:true});await app.ready();});
  afterAll(()=>app.close());
  const get=async(url:string,code=200)=>{const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(code);return r.body;};

  it('소개 홈: 게임 시작은 /game, 로그인 상태로 자동 이동하는 코드 없음, 공지 없으면 없다고',async()=>{
    const h=await get('/');
    expect(h).toContain('href="/game" data-start');expect(h).toContain('href="/world"');expect(h).toContain('아직 게시된 공지가 없어요.');
    // 승인된 새 홈(home-v4)은 APK 버튼 대신 다운로드 안내 페이지로 연결한다(배포 상태는 /download에서 비활성 표시)
    expect(h).toContain('href="/download"');expect(h).not.toMatch(/href="[^"]*\.apk"/);
    expect(h).not.toMatch(/location\.(replace|href)\s*=\s*['"]\/game/);
  });
  it('캐릭터 소개: 히든·최상위 이름을 내보내지 않음',async()=>{
    const h=await get('/characters');
    for(const c of content.characters.filter(c=>c.rarity==='HIDDEN'||topTiers.has(c.rarity)))expect(h).not.toContain(`"n":"${c.name}"`);
    expect(h).toContain('"n":"기본 갈매미맨"');
  });
  it('로그인 진입: 바깥 주소·이상한 경로는 기본값으로, 허용 경로는 그대로',async()=>{
    const bad=await get('/login?next=https://evil.example&from=//evil.example');
    expect(bad).toContain('data-next="/game"');expect(bad).toContain('data-from="/"');expect(bad).not.toContain('evil');
    const ok=await get('/login?next=/account&from=/characters');
    expect(ok).toContain('data-next="/account"');expect(ok).toContain('data-from="/characters"');expect(ok).toContain('카카오로 로그인');
    expect(safeNext('/game')).toBe('/game');expect(safeNext('javascript:alert(1)')).toBe('/game');
    expect(safeFrom('/notices/abc')).toBe('/notices/abc');expect(safeFrom('/\\evil')).toBe('/');expect(safeFrom('/my/api/logout')).toBe('/');
  });
  it('공지·다운로드: 확인된 자료가 없으면 비활성·없음',async()=>{
    expect(await get('/notices')).toContain('아직 게시된 공지가 없어요.');
    await get('/notices/none',404);
    const d=await get('/download');expect(d).toContain('시험 배포 연결 대기');expect(d).not.toContain('.apk"');
  });
  it('웹 게임 화면과 정적 파일: 정해진 폴더·확장자만',async()=>{
    expect(await get('/game')).toContain('id="view"');
    const r=await app.inject({method:'GET',url:'/account'});expect(r.statusCode).toBe(302);expect(r.headers.location).toBe('/game#account');
    expect((await app.inject({method:'GET',url:'/site/site.css'})).headers['content-type']).toContain('text/css');
    expect((await app.inject({method:'GET',url:'/site/reg/cards/C1.webp'})).headers['content-type']).toBe('image/webp');   // 입사 등록증(art/gpt/registration-v1)
    await get('/site/reg/cards/Z99.webp',404);await get('/site/reg/cards/manifest.json',404);
    await get('/site/../../.env',404);await get('/game/%2e%2e/%2e%2e/package.json',404);await get('/site/a/x.exe',404);
    await get('/game/zzz.js',404);await get('/site/a/none.webp',404);   // 정해진 형식이지만 없는 파일도 404(500 아님)
  });
});
