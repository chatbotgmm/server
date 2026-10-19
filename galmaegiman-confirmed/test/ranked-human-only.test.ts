import {describe,it,expect,vi} from 'vitest';
import type {PrismaClient} from '@prisma/client';
import {GameService,LEGACY_RANK_UNAVAILABLE} from '../src/game.js';
import {loadContent} from '../src/content.js';
import type {Rules} from '../src/battle/core.js';

function fixture(){
  const reads:string[]=[];
  const db=new Proxy({},{get(_target,key){reads.push(String(key));throw new Error('Unexpected database access');}}) as PrismaClient;
  return {game:new GameService(db,loadContent()),reads};
}
describe('legacy ranked PvP fails closed',()=>{
  it('rejects ranked start before any database access',async()=>{
    const {game,reads}=fixture();
    await expect(game.webPvpMatch('owner','rank')).rejects.toThrow(LEGACY_RANK_UNAVAILABLE);
    expect(reads).toEqual([]);
  });
  it('does not make exceptions for users with saved opposing teams',async()=>{
    const {game,reads}=fixture();
    // The guard is unconditional: it cannot inspect saved-team availability.
    await expect(game.webPvpMatch('owner-with-opponents','rank')).rejects.toThrow(LEGACY_RANK_UNAVAILABLE);
    expect(reads).toEqual([]);
  });
  it('rejects a legacy settlement before replay, completion or database mutation',async()=>{
    const {game,reads}=fixture();
    const match={player:'owner',mode:'rank',seed:1,mine:[],foe:[],exp:Date.now()+60000,lost:15,done:false};
    const internals=game as unknown as {pvpMatches:Map<string,typeof match>};
    internals.pvpMatches.set('old',match);
    await expect(game.webPvpResult('owner',{} as Rules,'old',[])).rejects.toThrow(LEGACY_RANK_UNAVAILABLE);
    expect(match.done).toBe(false);expect(match.lost).toBe(15);
    expect(internals.pvpMatches.get('old')).toBe(match);expect(reads).toEqual([]);
  });
  it('checks match ownership before exposing its blocked mode',async()=>{
    const {game,reads}=fixture();
    const match={player:'owner',mode:'rank',seed:1,mine:[],foe:[],exp:Date.now()+60000,lost:15,done:false};
    (game as unknown as {pvpMatches:Map<string,typeof match>}).pvpMatches.set('old',match);
    await expect(game.webPvpResult('other',{} as Rules,'old',[])).rejects.toThrow('대전 정보가 없어요.');
    expect(reads).toEqual([]);
  });
  it('continues into the existing normal pipeline',async()=>{
    const {game,reads}=fixture(),marker=new Error('normal pipeline reached');
    const internals=game as unknown as {webRun:(...args:unknown[])=>Promise<unknown>};
    const run=vi.spyOn(internals,'webRun').mockRejectedValue(marker);
    try{
      await expect(game.webPvpMatch('owner','normal')).rejects.toBe(marker);
      expect(run).toHaveBeenCalledTimes(1);expect(reads).toEqual([]);
    }finally{run.mockRestore();}
  });
});
