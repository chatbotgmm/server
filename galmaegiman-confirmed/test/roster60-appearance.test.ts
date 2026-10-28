import {describe,expect,it} from 'vitest';
import type {Fighter} from '../src/roster60-battle.js';
import {publicBattleAppearance,restoreBlackStoryAppearance} from '../src/battle/roster60-appearance.js';
import {capturePresentation,presentationForViewer} from '../src/battle/roster60-presentation.js';
import {opponentFighter,opponentIntel} from '../src/battle/roster60-opponent-intel.js';

function fighter(artVariant?:'black'):Fighter {
 return {id:'enemy',speciesId:'u01',name:'흑화 갈매미맨',artVariant,level:1,hp:10,mp:5,
  stats:{hp:10,mp:5,durability:99,statusResistance:77,speed:88},ownedId:'private',
  defense:{NORMAL:'normal',FIRE:'weak',WATER:'normal',LIGHTNING:'normal',ICE:'normal',NATURE:'normal',HOLY:'normal',DARK:'normal'},
  suitability:{NORMAL:1,FIRE:1,WATER:1,LIGHTNING:1,ICE:1,NATURE:1,HOLY:1,DARK:1},
  attack:{id:'basic',name:'Basic',element:'NORMAL',mpCost:1,power:1},skills:[]};
}
const story={version:'story-battle-v1',kind:'black',mode:'adventure',speciesId:'u01'};
const pair=()=>[fighter(),fighter()];

describe('server-authoritative black appearance without opponent-intel disclosure',()=>{
 it('requires explicit black variant and the exact eight supported species',()=>{
  for(const speciesId of ['u01','u02','u06','u07','u14','u23','u28','u31'])expect(publicBattleAppearance({speciesId,artVariant:'black'})).toEqual({artVariant:'black'});
  for(const speciesId of ['u03','story_dark_first','../u01'])expect(publicBattleAppearance({speciesId,artVariant:'black'})).toEqual({});
  for(const artVariant of [undefined,null,'normal','BLACK'])expect(publicBattleAppearance({speciesId:'u01',artVariant})).toEqual({});
  expect(publicBattleAppearance(fighter())).toEqual({}); // A nickname is not authorization.
 });
 it('projects cosmetic appearance without identity, defenses, or undiscovered stats',()=>{
  const f=fighter('black'),view=opponentFighter(f,opponentIntel(f,0));
  expect(view.artVariant).toBe('black');expect(view).not.toHaveProperty('defense');expect(view).not.toHaveProperty('ownedId');
  expect(view.stats).toEqual({hp:10,mp:5});
 });
 it('preserves only public appearance and hp/mp in presentation snapshots',()=>{
  const view=capturePresentation([fighter(),fighter('black')])[1];
  expect(view.artVariant).toBe('black');expect(view).not.toHaveProperty('defense');expect(view).not.toHaveProperty('ownedId');expect(view.stats).toEqual({hp:10,mp:5});
 });
 it('backfills only the matching enemy in legacy state and both presentation frames',()=>{
  const state={fighters:pair(),events:[{events:[{presentation:{version:1,before:pair(),after:pair()}}]}]};
  restoreBlackStoryAppearance(state,story);
  for(const fighters of [state.fighters,state.events[0].events[0].presentation.before,state.events[0].events[0].presentation.after]){
   expect(fighters[0].artVariant).toBeUndefined();expect(fighters[1].artVariant).toBe('black');
  }
 });
 it('does not infer black from name, ordinary/villain, unsupported context, or mismatched species',()=>{
  for(const context of [null,{...story,kind:'ordinary'},{...story,kind:'villain'},{...story,mode:'story'},{...story,version:'other'},{...story,speciesId:'u02'},{...story,speciesId:'u03'}]){
   const state={fighters:pair()};restoreBlackStoryAppearance(state,context);expect(state.fighters.every(f=>f.artVariant===undefined)).toBe(true);
  }
 });
 it('does not change defense discovery or turn-based stat revelation',()=>{
  for(const turn of [0,1,5])expect(opponentIntel(fighter('black'),turn)).toEqual(opponentIntel(fighter(),turn));
  const intel=opponentIntel(fighter('black'),0);expect(intel.knownDefenseCount).toBe(0);expect(intel.statsKnown).toBe(false);
 });
 it('retains cosmetic fields when reordering presentation for the other viewer',()=>{
  const fighters=capturePresentation([fighter(),fighter('black')]);
  const view=presentationForViewer({version:1,before:fighters,after:fighters},1);
  expect(view?.before[0].artVariant).toBe('black');expect(view?.after[1].artVariant).toBeUndefined();
 });
});
