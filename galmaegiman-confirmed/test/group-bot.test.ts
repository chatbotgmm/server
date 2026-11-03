import {describe,it,expect} from 'vitest';
import {loadGroupBotConfig,botEnv} from '../src/group/config.js';
import {buildResponse,buildWelcome,assertAllowedOutput,guardResponse,text,image,list,itemCard,quick,GroupResponseError,type GroupOutput} from '../src/group/response.js';
import {buildMentions} from '../src/group/mentions.js';
import {appLanding,LandingError} from '../src/group/landing.js';
import {ProactiveGate,MemoryProactiveStore,kstMonth,proactiveEnabled,ProactiveError} from '../src/group/proactive.js';

const config=loadGroupBotConfig();
const deps={config};

describe('단체방 설정',()=>{
  it('data/group-bot.yaml을 읽고 카카오 최대치 안의 값만 받는다',()=>{
    expect(config.limits.outputs).toBeLessThanOrEqual(3);
    expect(config.mentions).toEqual({perBubble:15,maxBubbles:3});
    expect(config.proactive.monthlyMax).toBe(2);
  });
  it('BOT_ENV는 dev·prod만, 없으면 시작하지 않는다',()=>{
    expect(botEnv('dev')).toBe('dev');expect(botEnv('prod')).toBe('prod');
    expect(()=>botEnv(undefined)).toThrow('BOT_ENV');expect(()=>botEnv('production')).toThrow();
  });
});

describe('허용 말풍선만',()=>{
  it('텍스트·이미지·리스트·아이템 카드는 만들 수 있다',()=>{
    const r=buildResponse([text('도감 92/145'),itemCard({title:'우리 방 도감',itemList:[{title:'발견',description:'92/145'}]})],[quick('방도감')],deps);
    expect(r.version).toBe('2.0');expect(r.template.outputs).toHaveLength(2);
    expect(()=>buildResponse([image('https://e.com/a.png','갈매미')],[],deps)).not.toThrow();
  });
  it.each(['basicCard','carousel','commerceCard','textCard'])('%s는 거절한다',key=>{
    const bad={[key]:{title:'x'}} as unknown as GroupOutput;
    expect(()=>assertAllowedOutput(bad)).toThrow(GroupResponseError);
    expect(()=>buildResponse([bad],[],deps)).toThrow('허용되지 않은 말풍선');
  });
  it('키가 두 개 섞인 말풍선도 거절한다',()=>expect(()=>assertAllowedOutput({simpleText:{text:'a'},basicCard:{}})).toThrow());
  it('허용하지 않은 버튼 동작(share 등)과 http 링크를 거절한다',()=>{
    expect(()=>buildResponse([list('a',[{title:'1'}],[{action:'share',label:'공유'} as never])],[],deps)).toThrow('버튼 동작');
    expect(()=>buildResponse([list('a',[{title:'1'}],[{action:'webLink',label:'열기',webLinkUrl:'http://e.com'}])],[],deps)).toThrow('https');
  });
});

describe('응답 길이 상한(ResponseGuard)',()=>{
  const L=config.limits;
  it('말풍선 수 상한',()=>{
    expect(()=>buildResponse(Array.from({length:L.outputs},()=>text('a')),[],deps)).not.toThrow();
    expect(()=>buildResponse(Array.from({length:L.outputs+1},()=>text('a')),[],deps)).toThrow(`말풍선은 ${L.outputs}개까지`);
  });
  it('글자 수·줄 수 상한',()=>{
    expect(()=>buildResponse([text('가'.repeat(L.textChars))],[],deps)).not.toThrow();
    expect(()=>buildResponse([text('가'.repeat(L.textChars+1))],[],deps)).toThrow('자까지');
    expect(()=>buildResponse([text(Array(L.textLines+1).fill('가').join('\n'))],[],deps)).toThrow('줄까지');
  });
  it('리스트 항목·아이템 카드 목록·바로가기 수 상한',()=>{
    const items=(n:number)=>Array.from({length:n},(_,i)=>({title:`갈매미${i}`}));
    expect(()=>buildResponse([list('목록',items(L.listItems))],[],deps)).not.toThrow();
    expect(()=>buildResponse([list('목록',items(L.listItems+1))],[],deps)).toThrow('리스트 항목');
    expect(()=>buildResponse([itemCard({itemList:Array.from({length:L.itemCardItems+1},()=>({title:'a',description:'b'}))})],[],deps)).toThrow('아이템 카드 목록');
    expect(()=>buildResponse([itemCard({itemList:[{title:'여섯글자제목',description:'b'}]})],[],deps)).not.toThrow();
    expect(()=>buildResponse([itemCard({itemList:[{title:'일곱글자의제목',description:'b'}]})],[],deps)).toThrow('6자');
    expect(()=>buildResponse([text('a')],Array.from({length:L.quickReplies+1},(_,i)=>quick(`도감${i}`)),deps)).toThrow('바로가기');
  });
  it('버튼 문구 14자 상한',()=>{
    expect(()=>buildResponse([text('a')],[quick('가'.repeat(15),'도감')],deps)).toThrow('바로가기 문구');
  });
  it('빈 응답은 거절',()=>expect(()=>guardResponse({version:'2.0',template:{outputs:[],quickReplies:[]}},L)).toThrow());
  it('봇 입장 인사는 더 짧게',()=>{
    expect(()=>buildWelcome('안녕하세요! 갈매미맨이에요.\n@갈매미맨 도움말로 시작해요.',[],deps)).not.toThrow();
    expect(()=>buildWelcome('가'.repeat(L.welcomeChars+1),[],deps)).toThrow('입장 인사');
    expect(()=>buildWelcome(Array(L.welcomeLines+1).fill('가').join('\n'),[],deps)).toThrow('입장 인사');
  });
});

describe('멘션 나누기',()=>{
  const people=(n:number)=>Array.from({length:n},(_,i)=>`u${i}`);
  it('15명 단위로 말풍선을 나눈다',()=>{
    expect(buildMentions(people(15),config.mentions)).toHaveLength(1);
    expect(buildMentions(people(16),config.mentions).map(b=>b.length)).toEqual([15,1]);
    expect(buildMentions(people(45),config.mentions).map(b=>b.length)).toEqual([15,15,15]);
  });
  it('45명을 넘으면 잘라내고 경고한다',()=>{
    const warns:unknown[]=[];
    const out=buildMentions(people(50),config.mentions,w=>warns.push(w));
    expect(out.flat()).toHaveLength(45);expect(warns).toEqual([{requested:50,kept:45}]);
  });
  it('같은 사람은 한 번만',()=>expect(buildMentions(['a','a','b'],config.mentions)).toEqual([['a','b']]));
  it('비어 있으면 말풍선 없음',()=>expect(buildMentions([],config.mentions)).toEqual([]));
});

describe('앱 랜딩 링크',()=>{
  const ok={summary:'심해수호자는 바다 + 방패로 만들어요.',installed:'gmm://dex/S3',notInstalled:'https://play.google.com/store/apps/details?id=org.gmm'};
  it('설치·미설치 두 경로와 안내 글이 모두 있어야 한다',()=>{
    expect(appLanding(ok)).toEqual(ok);
    expect(()=>appLanding({...ok,summary:'앱에서 보기'})).toThrow(LandingError);
    expect(()=>appLanding({...ok,notInstalled:'http://play.google.com'})).toThrow('https');
    expect(()=>appLanding({...ok,installed:'javascript:alert(1)'})).toThrow('앱 링크');
    expect(()=>appLanding({...ok,installed:''})).toThrow('형식');
  });
});

describe('선톡',()=>{
  const make=(store=new MemoryProactiveStore(),now=new Date('2026-11-03T03:00:00Z'),enabled=true)=>new ProactiveGate({enabled,env:'prod',config,store,now:()=>now});
  it('기본은 꺼져 있다',()=>{
    expect(proactiveEnabled(undefined)).toBe(false);expect(proactiveEnabled('1')).toBe(false);expect(proactiveEnabled('true')).toBe(true);
    return expect(make(undefined,undefined,false).approve('새 갈매미가 왔어요')).rejects.toThrow('꺼져');
  });
  it('한 달 2회를 넘지 못한다(한국 시간 달 기준)',async()=>{
    const store=new MemoryProactiveStore();
    await make(store).approve('첫 번째');await make(store).approve('두 번째');
    await expect(make(store).approve('세 번째')).rejects.toThrow(ProactiveError);
    // 2026-11-30T15:00Z = 한국 12월 1일 0시: 새 달
    expect(kstMonth(new Date('2026-11-30T14:59:59Z'))).toBe('2026-11');
    expect(kstMonth(new Date('2026-11-30T15:00:00Z'))).toBe('2026-12');
    await expect(make(store,new Date('2026-11-30T15:00:00Z')).approve('12월 첫 번째')).resolves.toEqual({month:'2026-12'});
    // 개발·운영은 따로 센다
    const dev=new ProactiveGate({enabled:true,env:'dev',config,store,now:()=>new Date('2026-11-03T03:00:00Z')});
    await expect(dev.approve('개발 시험')).resolves.toEqual({month:'2026-11'});
  });
  it('2줄 초과·이미지는 거절하고 횟수를 쓰지 않는다',async()=>{
    const store=new MemoryProactiveStore(),g=make(store);
    await expect(g.approve('1\n2\n3')).rejects.toThrow('줄까지');
    await expect(g.approve('그림 선톡',{image:true})).rejects.toThrow('이미지');
    expect(store.sent).toHaveLength(0);
  });
});
