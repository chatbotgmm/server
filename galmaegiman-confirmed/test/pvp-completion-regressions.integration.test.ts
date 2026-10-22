import {beforeAll,afterAll,beforeEach,describe,it,expect,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
import {loadBattleRules} from '../src/battle/load.js';
import {createBattle,pending,pickAi,automaticFreestyleChoice} from '../src/battle/engine.js';
import {applyEvent,type PvpEvent} from '../src/battle/replay.js';

// Fix only the battle seed and timeout slot. Tokens and all other randomness stay real.
const random=vi.hoisted(()=>({seed:303,slot:0,controlled:false}));
vi.mock('node:crypto',async importOriginal=>{
  const actual=await importOriginal<typeof import('node:crypto')>();
  return {...actual,randomInt:((...args:unknown[])=>{
    if(random.controlled&&args.length===1){
      if(args[0]===2**31-1)return random.seed;
      if(args[0]===3)return random.slot;
    }
    return (actual.randomInt as (...args:unknown[])=>unknown)(...args);
  })};
});
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('완료 판정과 V2 자동 선택 회귀',()=>{
  let db:PrismaClient;
  const content=loadContent();
  beforeAll(()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(()=>{random.controlled=false;vi.restoreAllMocks();});
  afterAll(async()=>{random.controlled=false;vi.restoreAllMocks();await db.$disconnect();});
  async function setup(identity:string){
    const player=await db.player.create({data:{identity,nickname:identity,tutorial:5}});
    const game=new GameService(db,content);
    const app=await buildApp(db,game,{secret:'completion-regression'.repeat(3),baseUrl:async()=>'https://example.com'});
    const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
    const login=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}});
    expect(login.statusCode).toBe(200);
    const headers={'x-gmm':'1','x-gmm-augment-version':'2','x-gmm-combat-epoch':'gmm-combat-20261021-v3','x-gmm-s':login.json().token};
    return {player,game,app,headers};
  }
  it.each([{seed:303,slot:0,human:false},{seed:82,slot:1,human:true}])('시간 초과 seed $seed: AI만 처리하고 사람의 속성 선택은 보존한다',async({seed,slot,human})=>{
    const {app,headers}=await setup(`deadline-regression-${seed}`);
    try{
      random.seed=seed;random.slot=slot;random.controlled=true;
      const startResponse=await app.inject({method:'POST',url:'/my/api/native/battle/start',headers,payload:{mode:'practice'}});
      expect(startResponse.statusCode).toBe(200);const start=startResponse.json();
      const turn=async(payload:object)=>{const r=await app.inject({method:'POST',url:'/my/api/native/battle/turn',headers,payload});expect(r.statusCode).toBe(200);return r.json();};
      const reroll=await turn({session:start.session,revision:start.revision,reroll:0});
      expect(reroll.chooseDeadline).toBe(start.chooseDeadline);
      expect(reroll.teams[0].offers[slot].id).toBe(human?'reverse':'glasswing');
      const clock=vi.spyOn(Date,'now').mockReturnValue(start.chooseDeadline+1);
      const response=await app.inject({method:'GET',url:'/my/api/native/battle/current',headers});
      expect(response.statusCode).toBe(200);const current=response.json();
      expect(current.revision).toBe(reroll.revision+1);
      expect(current.teams[0].augments.at(-1).id).toBe(human?'reverse':'glasswing');
      expect(current.teams[1].attributeSelection).toBeNull(); // AI choice is never exposed.
      if(human){
        expect(current.phase).toBe('choose');expect(current.choiceKind).toBe('own-attribute');
        expect(current.teams[0].attributeSelection).not.toBeNull();
        expect(current.chooseDeadline).toBe(start.chooseDeadline+30001);
        const unchanged=(await app.inject({method:'GET',url:'/my/api/native/battle/current',headers})).json();
        expect(unchanged.revision).toBe(current.revision);expect(unchanged.teams[0].attributeSelection).toEqual(current.teams[0].attributeSelection);
      }else{
        expect(current.teams[1].augments.at(-1).id).toBe('reverse');
        expect(current.phase).toBe('action');expect(current.choiceKind).toBeUndefined();
        expect(current.teams[0].attributeSelection).toBeNull();expect(current.chooseDeadline).toBeUndefined();
      }
      const duplicate=await turn({session:start.session,revision:reroll.revision,choose:{augment:reroll.teams[0].offers[0].id}});
      expect(duplicate.revision).toBe(current.revision);expect(duplicate.teams).toEqual(current.teams);
      expect(duplicate.chooseDeadline).toBe(current.chooseDeadline);
      if(human){
        const selection=current.teams[0].attributeSelection;
        const chosen=await turn({session:start.session,revision:current.revision,choose:{freestyle:{selectionId:selection.selectionId,phaseId:selection.phaseId,targetKey:selection.targetKey,attribute:selection.attributes[0]}}});
        expect(chosen.phase).toBe('action');expect(chosen.revision).toBe(current.revision+1);
      }
      clock.mockRestore();
    }finally{random.controlled=false;vi.restoreAllMocks();await app.close();}
  });
  it('일반전 seed 63 무승부는 완료로 받되 미완료·잘못된 기록·중복 정산·AI 랭크는 거부한다',async()=>{
    const {app,game,player,headers}=await setup('draw-regression-63');
    try{
      for(const characterId of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:player.id,characterId,obtainedVia:'TEST'}});
      await db.player.update({where:{id:player.id},data:{battleTeam:'D1,T1,E1'}});
      random.seed=63;random.controlled=true;
      const match=await game.webPvpMatch(player.id,'normal');random.controlled=false;
      // The returned records are the captured match records. Install the exact draw fixture,
      // not a winning seed that would hide the completed-draw rejection.
      match.foe.splice(0,match.foe.length,...['I7','D1','D5'].map((id,i)=>({...match.mine[i],uid:`b${i}`,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as typeof match.mine[number]['tier']})));
      expect(match.seed).toBe(63);
      const b=createBattle(loadBattleRules(),match.mine,match.foe,match.seed),log:PvpEvent[]=[];let first=true;
      const push=(e:PvpEvent)=>{applyEvent(b,e);log.push(e);};
      while(b.phase!=='over'&&log.length<500){
        const pd=pending(b,0);
        if(b.phase==='choose'){
          if(pd.freestyle){push({c:{freestyle:automaticFreestyleChoice(b,0)}});continue;}
          if(first&&pd.augments){first=false;push({r:0});continue;}
          push({c:{...(pd.augments?{augment:b.sides[0].offers![0]}:{}),...(pd.needSwitch?{switchTo:b.sides[0].fighters.findIndex(f=>f.hp>0)}:{})}});
        }else push({a:pickAi(b,0)});
      }
      expect(b.phase).toBe('over');expect(b.winner).toBeNull();expect(log).toHaveLength(17);
      expect(b.sides.flatMap(s=>s.fighters).every(f=>f.hp===0)).toBe(true);
      const post=(events:unknown)=>app.inject({method:'POST',url:'/my/api/pvp/result',headers,payload:{match:match.match,log:events}});
      const before=await db.player.findUniqueOrThrow({where:{id:player.id}});
      const incomplete=await post(log.slice(0,1));expect(incomplete.statusCode).toBe(400);expect(incomplete.json().error).toContain('아직 끝나지');
      expect((await post([{a:{kind:'skill',skillId:'없는기술'}}])).statusCode).toBe(400);
      const result=await post(log);expect(result.statusCode).toBe(200);
      expect(result.json().result).toMatchObject({won:false,mode:'normal',change:0,point:before.rankPoint,wins:before.rankWins,losses:before.rankLosses});
      const after=await db.player.findUniqueOrThrow({where:{id:player.id}});
      expect(after).toMatchObject({rankPoint:before.rankPoint,rankWins:before.rankWins,rankLosses:before.rankLosses,credits:before.credits});
      const duplicate=await post(log);expect(duplicate.statusCode).toBe(400);expect(duplicate.json().error).toContain('이미 결과');
      const rank=await app.inject({method:'POST',url:'/my/api/pvp/match',headers,payload:{mode:'rank'}});
      expect(rank.statusCode).toBe(400);expect(rank.json().error).toContain('실시간 유저 랭크전');
    }finally{random.controlled=false;await app.close();}
  });
});
