import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import Fastify,{type FastifyInstance} from 'fastify';
import {readFileSync} from 'node:fs';
import {loadContent} from '../src/content.js';
import {registerSite,safeFrom,safeNext,R60_PUBLIC,stampFor} from '../src/site.js';

// 공개 홈페이지: 로그인 없이 읽기, 스포일러(히든·최상위) 가림, 로그인 뒤 갈 곳·돌아갈 곳은 허용 경로만, 확인 안 된 다운로드·공지는 만들지 않음
describe('공개 홈페이지(src/site.ts)',()=>{
  const content=loadContent();
  let app:FastifyInstance;
  beforeAll(async()=>{app=Fastify();await app.register((await import('@fastify/rate-limit')).default,{global:false});registerSite(app,content,{root:process.cwd(),version:'test',kakao:true});await app.ready();});
  afterAll(()=>app.close());
  const get=async(url:string,code=200)=>{const r=await app.inject({method:'GET',url});expect(r.statusCode).toBe(code);return r.body;};

  it('소개 홈: 예전 PC 웹게임 대신 앱 다운로드로 안내, 로그인 상태로 자동 이동하는 코드 없음, 공지 없으면 없다고',async()=>{
    const h=await get('/');
    // 2026-11-06: /game은 60종 이전 규칙의 웹게임이라 홈에서 연결하지 않는다(사용자 결정). 머리줄 행동은 앱 다운로드
    expect(h).not.toContain('href="/game"');expect(h).toContain('class="gold-cta" href="/download"');
    expect(h).toContain('href="/world"');expect(h).toContain('아직 게시된 공지가 없어요.');
    // 승인된 새 홈(home-v4)은 APK 버튼 대신 다운로드 안내 페이지로 연결한다(배포 상태는 /download에서 비활성 표시)
    expect(h).toContain('href="/download"');expect(h).not.toMatch(/href="[^"]*\.apk"/);
    expect(h).not.toMatch(/location\.(replace|href)\s*=\s*['"]\/game/);
  });
  it('캐릭터 소개: 지금 게임(60종)의 공개 등급만, 히든·최상위 이름과 예전 145종은 내보내지 않음',async()=>{
    const h=await get('/characters');
    const units=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8')).characters as {id:string;name:string;rarity:string}[];
    const listed=JSON.parse(/id="chars-data">([^<]*)</.exec(h)![1]) as {id:string;n:string;img:string}[];
    expect(listed.map(u=>u.id)).toEqual(units.filter(u=>R60_PUBLIC.has(u.rarity)).map(u=>u.id));
    for(const u of units.filter(u=>!R60_PUBLIC.has(u.rarity)))expect(h).not.toContain(`"n":"${u.name}"`);
    expect(h).not.toContain('"n":"기본 갈매미맨"');   // 예전 145종 대원
    for(const u of listed)expect((await app.inject({method:'GET',url:u.img})).headers['content-type']).toBe('image/webp');
    // 비공개 등급 그림은 파일 자체를 두지 않는다
    for(const u of units.filter(u=>!R60_PUBLIC.has(u.rarity)))await get(`/site/units/${u.id}.webp`,404);
  });
  it('모집 확률·세계관·계정 삭제: 서버의 60종 자료로 만들고, 예전 145종 페이지는 첫 화면으로 닫는다',async()=>{
    const policy=JSON.parse(readFileSync('data/roster60-recruit-policy.json','utf8'));
    const odds=await get('/odds');expect(odds).toContain(policy.version);
    for(const o of policy.offers){expect(odds).toContain(`${o.label} 모집`);expect(odds).toContain(`지원서 ${o.cost}장`);}
    expect(odds).toContain('8.75%');expect(odds).toContain('0.0833%');   // 하급 커먼 70%÷8종, 고급 전설 0.5%÷6종
    const world=await get('/world');expect(world).toContain('돌이 된 태초 갈매미');expect(world).toContain('이야기의 시작');
    const del=await get('/account/delete');expect(del).toContain('탈퇴');expect(del).not.toContain('웹 로그인');
    for(const path of ['/dex','/dex/C1','/recipes','/hidden','/rank','/talk','/event','/search','/p/someone','/search-index.json','/api/players']){
      const r=await app.inject({method:'GET',url:path});expect(r.statusCode,path).toBe(301);expect(r.headers.location).toBe('/');
    }
    const map=await get('/sitemap.xml');expect(map).toContain('/odds');expect(map).not.toContain('/dex');
  });
  it('로그인 진입: 바깥 주소·이상한 경로는 기본값으로, 허용 경로는 그대로',async()=>{
    const bad=await get('/login?next=https://evil.example&from=//evil.example');
    expect(bad).toContain('data-next="/game60"');expect(bad).toContain('data-from="/"');expect(bad).not.toContain('evil');
    const ok=await get('/login?next=/account&from=/characters');
    expect(ok).toContain('data-next="/account"');expect(ok).toContain('data-from="/characters"');expect(ok).toContain('카카오로 로그인');
    expect(safeNext('/game')).toBe('/game60');expect(safeNext('javascript:alert(1)')).toBe('/game60');   // 로그인 뒤 기본은 지금 게임 웹게임
    expect(safeFrom('/notices/abc')).toBe('/notices/abc');expect(safeFrom('/\\evil')).toBe('/');expect(safeFrom('/my/api/logout')).toBe('/');
  });
  it('공지·다운로드: 확인된 자료가 없으면 비활성·없음',async()=>{
    expect(await get('/notices')).toContain('아직 게시된 공지가 없어요.');
    await get('/notices/none',404);
    const d=await get('/download');expect(d).toContain('시험 배포 연결 대기');expect(d).not.toContain('.apk"');
  });
  it('PC 웹게임(60종, /game60): 화면·그림·글꼴 제공, 로그인 뒤 돌아올 곳으로 허용',async()=>{
    const h=await get('/game60');expect(h).toMatch(/\/game60\/game\.js\?v=[0-9a-f]{12}"/);   // 파일 내용 지문(내용이 바뀌면 주소도 바뀜)expect(h).toContain('noindex');expect(h).not.toContain('{{');
    for(const [url,type] of [['/game60/game.js','javascript'],['/game60/game.css','text/css'],['/game60/assets/units/u01-front.webp','image/webp'],['/game60/assets/black/u01.webp','image/webp'],['/game60/assets/fonts/workbench-noto-regular.otf','font/otf']] as const)
      expect((await app.inject({method:'GET',url})).headers['content-type'],url).toContain(type);
    await get('/game60/../package.json',404);await get('/game60/assets/none.webp',404);
    expect(safeNext('/game60')).toBe('/game60');
    // 버전 값은 파일 내용에서 나온다: 수정 시각이 같아도 내용이 다르면 다른 값
    const {mkdtempSync,mkdirSync,writeFileSync,utimesSync}=await import('node:fs');const {join}=await import('node:path');const {tmpdir}=await import('node:os');
    const root=mkdtempSync(join(tmpdir(),'stamp-'));mkdirSync(join(root,'public'));const f=join(root,'public','a.js'),t=new Date('2026-11-07T00:00:00Z');
    writeFileSync(f,'one');utimesSync(f,t,t);const v1=stampFor(root)('a.js');writeFileSync(f,'two');utimesSync(f,t,t);const v2=stampFor(root)('a.js');
    expect(v1).toMatch(/^\/a\.js\?v=[0-9a-f]{12}$/);expect(v2).not.toBe(v1);
  });
  it('웹 게임 화면과 정적 파일: 정해진 폴더·확장자만',async()=>{
    // 예전 웹게임(60종 이전 규칙)은 내리고 새 웹게임으로 보낸다
    for(const url of ['/game','/account']){const r=await app.inject({method:'GET',url});expect(r.statusCode,url).toBe(302);expect(r.headers.location).toBe('/game60');}
    expect((await app.inject({method:'GET',url:'/site/site.css'})).headers['content-type']).toContain('text/css');
    await get('/site/reg/cards/C1.webp',404);   // 예전 145종 입사 등록증 경로는 닫음
    await get('/site/../../.env',404);await get('/game/%2e%2e/%2e%2e/package.json',404);await get('/site/a/x.exe',404);
    await get('/game/zzz.js',404);await get('/site/a/none.webp',404);   // 정해진 형식이지만 없는 파일도 404(500 아님)
  });
});
