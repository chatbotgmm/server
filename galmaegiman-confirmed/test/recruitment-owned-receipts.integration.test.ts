import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {GameService} from '../src/game.js';
import {loadContent,tiers} from '../src/content.js';
import {GachaEngine,gachaKinds} from '../src/gacha.js';

const content=loadContent();
const now=new Date('2026-10-02T08:00:00+09:00');
const suite=process.env.TEST_DB_URL?describe:describe.skip;
type DrawReceipt=Awaited<ReturnType<GameService['webDraw']>>;
const keys=(r:DrawReceipt)=>r.items.flatMap(item=>item.ownedKeys);

// Uses the same isolated, seeded SQLite harness as game.integration.test.ts.
// No production database URL is accepted, even when TEST_DB_URL is set accidentally.
suite('recruitment owned-instance transaction receipts',()=>{
  let db:PrismaClient,game:GameService,engine:GachaEngine,playerId:string;
  beforeAll(()=>{
    const url=process.env.TEST_DB_URL!;
    if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw new Error('Refusing a non-isolated test database');
    db=new PrismaClient({datasources:{db:{url}}});
  });
  beforeEach(async()=>{
    await db.$transaction([
      db.webLogin.deleteMany(),db.playerItem.deleteMany(),db.kakaoLink.deleteMany(),db.storySave.deleteMany(),db.chatAppUser.deleteMany(),db.commentLike.deleteMany(),db.comment.deleteMany(),db.pendingAction.deleteMany(),db.adminSnapshot.deleteMany(),db.auditEvent.deleteMany(),db.achievement.deleteMany(),db.dailyProgress.deleteMany(),db.collectionEntry.deleteMany(),db.ownedCharacter.deleteMany(),db.expedition.deleteMany(),db.player.deleteMany()
    ]);
    const p=await db.player.create({data:{identity:'receipt-owner',tutorial:5,credits:1000,createdAt:now}});
    playerId=p.id;
    engine=new GachaEngine(content.characters,content.economy.gachas,()=>0);
    game=new GameService(db,content,()=>now,engine,max=>max-1);
  });
  afterEach(()=>vi.restoreAllMocks());
  afterAll(async()=>{await db?.$disconnect();});
  const owned=()=>db.ownedCharacter.findMany({where:{playerId},orderBy:{id:'asc'}});
  async function checkReceipt(receipt:DrawReceipt,expectedIds:number[],owner=playerId){
    expect(keys(receipt).sort()).toEqual(expectedIds.map(id=>`o${id}`).sort());
    expect(new Set(keys(receipt)).size).toBe(expectedIds.length);
    for(const item of receipt.items){
      expect(item.ownedKeys).toHaveLength(item.n);
      for(const key of item.ownedKeys){
        expect(key).toMatch(/^o[1-9]\d*$/);
        expect(await db.ownedCharacter.findUniqueOrThrow({where:{id:Number(key.slice(1))}}))
          .toMatchObject({playerId:owner,characterId:item.id,status:'AVAILABLE'});
      }
    }
  }
  async function snapshot(){
    return {player:await db.player.findUniqueOrThrow({where:{id:playerId}}),owned:await owned(),
      collection:await db.collectionEntry.findMany({where:{playerId}}),daily:await db.dailyProgress.findMany({where:{playerId}}),
      audit:await db.auditEvent.findMany({where:{playerId}})};
  }

  it('groups exactly the newly granted instances by species, with distinct keys for duplicate copies',async()=>{
    const old=await db.ownedCharacter.create({data:{playerId,characterId:'C1',obtainedVia:'TEST'}});
    const other=await db.player.create({data:{identity:'receipt-other'}});
    await db.ownedCharacter.create({data:{playerId:other.id,characterId:'C1',obtainedVia:'TEST'}});
    vi.spyOn(engine,'draw').mockReturnValueOnce(content.map.get('C1')!).mockReturnValueOnce(content.map.get('U1')!).mockReturnValueOnce(content.map.get('C1')!);
    const result=await game.webDraw(playerId,'LOW',3);
    const granted=(await owned()).filter(row=>row.id!==old.id);
    await checkReceipt(result,granted.map(row=>row.id));
    expect(result.items.map(item=>({id:item.id,n:item.n,first:item.first}))).toEqual([{id:'U1',n:1,first:true},{id:'C1',n:2,first:true}]);
    expect(keys(result)).not.toContain(`o${old.id}`);
  });

  it('keeps simultaneous same-player draw receipts disjoint and mapped to their own grants',async()=>{
    const draws=vi.spyOn(engine,'draw');
    for(const id of ['C1','C1','U1','C1'])draws.mockReturnValueOnce(content.map.get(id)!);
    const [first,second]=await Promise.all([game.webDraw(playerId,'LOW',2),game.webDraw(playerId,'LOW',2)]);
    const rows=await owned();
    await checkReceipt(first,rows.slice(0,2).map(row=>row.id));
    await checkReceipt(second,rows.slice(2).map(row=>row.id));
    expect(keys(first).some(key=>keys(second).includes(key))).toBe(false);
    expect(first.items.map(item=>item.id)).toEqual(['C1']);
    expect(second.items.find(item=>item.id==='C1')?.first).toBe(false);
  });

  it('rolls back a failed draw after one grant, returns no receipt, and does not leak keys into the next draw',async()=>{
    const before=await snapshot();
    vi.spyOn(engine,'draw').mockReturnValueOnce(content.map.get('C1')!).mockImplementationOnce(()=>{throw new Error('injected second draw failure');});
    let returned:DrawReceipt|undefined;
    await expect(game.webDraw(playerId,'HIGH',2).then(r=>{returned=r;})).rejects.toThrow('injected second draw failure');
    expect(returned).toBeUndefined();
    expect(await snapshot()).toEqual(before);
    vi.restoreAllMocks();
    const result=await game.webDraw(playerId,'LOW',1);
    await checkReceipt(result,(await owned()).map(row=>row.id));
    expect(keys(result)).toHaveLength(1);
  });

  it.each([0,-1,1.5,51,NaN,Infinity,Number.MAX_SAFE_INTEGER+1])('rejects invalid count %s without charging or granting',async times=>{
    const before=await snapshot();
    await expect(game.webDraw(playerId,'LOW',times)).rejects.toThrow();
    expect(await snapshot()).toEqual(before);
  });

  it('rejects invalid kind and insufficient funds without a receipt or state changes',async()=>{
    await db.player.update({where:{id:playerId},data:{credits:0}});
    const before=await snapshot();
    await expect(game.webDraw(playerId,'INVALID',1)).rejects.toThrow();
    await expect(game.webDraw(playerId,'LOW',1)).rejects.toThrow();
    expect(await snapshot()).toEqual(before);
  });

  it.each(gachaKinds)('%s keeps cost, reward, pity, mission and tutorial behavior identical to the existing chat draw',async kind=>{
    const control=await db.player.create({data:{identity:'receipt-control',tutorial:5,credits:1000,createdAt:now}});
    const result=await game.webDraw(playerId,kind,1);
    await game.handle(control.identity,`${content.economy.gachas[kind].label}뽑기`);
    const fields={credits:true,snack:true,highPity:true,tutorial:true,revision:true};
    const actual=await db.player.findUniqueOrThrow({where:{id:playerId},select:fields});
    expect(actual).toEqual(await db.player.findUniqueOrThrow({where:{id:control.id},select:fields}));
    expect(actual.credits).toBe(1000-content.economy.gachas[kind].cost);
    const daily=(id:string)=>db.dailyProgress.findMany({where:{playerId:id},select:{day:true,draws:true,crafts:true,expeditions:true,rewarded:true}});
    expect(await daily(playerId)).toEqual(await daily(control.id));
    const audit=(id:string)=>db.auditEvent.findMany({where:{playerId:id,kind:'GACHA'},select:{detail:true}});
    expect(await audit(playerId)).toEqual(await audit(control.id));
    await checkReceipt(result,(await owned()).map(row=>row.id));
  });

  it('returns the actual pity replacement key and preserves the high-draw provenance and counter reset',async()=>{
    const pity=content.economy.gachas.HIGH.pity!;
    await db.player.update({where:{id:playerId},data:{highPity:pity.count-1}});
    const result=await game.webDraw(playerId,'HIGH',1);
    const rows=await owned();
    await checkReceipt(result,rows.map(row=>row.id));
    expect(tiers[result.items[0].tier]).toBeGreaterThanOrEqual(tiers[pity.tier]);
    expect(rows[0].obtainedVia).toBe('GACHA_HIGH');
    expect((await db.player.findUniqueOrThrow({where:{id:playerId}})).highPity).toBe(0);
  });
});

describe('recruitment receipt rate invariance',()=>{
  it.each(gachaKinds)('%s retains every configured integer probability bucket',kind=>{
    const counts:Record<string,number>={};
    for(let ticket=0;ticket<100;ticket++){
      let calls=0;
      const engine=new GachaEngine(content.characters,content.economy.gachas,()=>calls++===0?ticket:0);
      const tier=engine.draw(kind).rarity;
      counts[tier]=(counts[tier]??0)+1;
    }
    expect(counts).toEqual(content.economy.gachas[kind].weights);
  });
});
