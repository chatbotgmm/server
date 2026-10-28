import {test} from 'vitest';
import assert from 'node:assert/strict';
import {publicBattleAppearance,restoreBlackStoryAppearance} from '../src/battle/roster60-appearance.js';
import {capturePresentation,presentationForViewer} from '../src/battle/roster60-presentation.js';
import {opponentFighter,opponentIntel} from '../src/battle/roster60-opponent-intel.js';
const ids=['u01','u02','u06','u07','u14','u23','u28','u31'];
const fighter=(variant)=>({id:'enemy',speciesId:'u01',name:'흑화 갈매미맨',artVariant:variant,level:1,hp:10,mp:5,stats:{hp:10,mp:5,power:99,speed:88},defense:{FIRE:'weak'},ownedId:'private',attack:{id:'basic'},skills:[]});
test('exact eight species only, explicit black variant required',()=>{
 for(const speciesId of ids)assert.deepEqual(publicBattleAppearance({speciesId,artVariant:'black'}),{artVariant:'black'});
 for(const speciesId of ['u03','story_dark_first','../u01'])assert.deepEqual(publicBattleAppearance({speciesId,artVariant:'black'}),{});
 assert.deepEqual(publicBattleAppearance(fighter()),{});
});
test('opponent projection preserves black, keeps private defenses/identity and early stats masked',()=>{
 const f=fighter('black'),v=opponentFighter(f,opponentIntel(f,0));
 assert.equal(v.artVariant,'black');assert.equal(v.defense,undefined);assert.equal(v.ownedId,undefined);
 assert.deepEqual(v.stats,{hp:10,mp:5});
});
test('presentation retains cosmetic without private data',()=>{
 const f=fighter('black'),v=capturePresentation([fighter(),f])[1];
 assert.equal(v.artVariant,'black');assert.equal(v.defense,undefined);assert.equal(v.ownedId,undefined);
 assert.deepEqual(v.stats,{hp:10,mp:5});
});
test('legacy snapshot decorates only matching enemy and enemy frames',()=>{
 const pair=()=>[fighter(),fighter()],state={fighters:pair(),events:[{events:[{presentation:{version:1,before:pair(),after:pair()}}]}]};
 restoreBlackStoryAppearance(state,{version:'story-battle-v1',kind:'black',mode:'adventure',speciesId:'u01'});
 assert.equal(state.fighters[0].artVariant,undefined);assert.equal(state.fighters[1].artVariant,'black');
 const p=state.events[0].events[0].presentation;
 assert.equal(p.before[1].artVariant,'black');assert.equal(p.after[1].artVariant,'black');assert.equal(p.before[0].artVariant,undefined);
});
test('no nickname inference or ordinary/villain/unknown snapshot expansion',()=>{
 for(const story of [null,{version:'story-battle-v1',kind:'ordinary',mode:'adventure',speciesId:'u01'},{version:'story-battle-v1',kind:'villain',mode:'story',speciesId:'u01'},{version:'story-battle-v1',kind:'black',mode:'adventure',speciesId:'u02'}]){
  const state={fighters:[fighter(),fighter()]};restoreBlackStoryAppearance(state,story);assert.equal(state.fighters[1].artVariant,undefined);
 }
});
test('unknown stat/defense discovery remains unchanged by artwork',()=>{
 const plain=opponentIntel(fighter(),0),black=opponentIntel(fighter('black'),0);
 assert.deepEqual(plain,black);assert.equal(black.knownDefenseCount,0);assert.equal(black.statsKnown,false);
});
test('presentation viewer reordering does not lose cosmetic field',()=>{
 const pair=capturePresentation([fighter(),fighter('black')]);
 assert.equal(presentationForViewer({version:1,before:pair,after:pair},1).before[0].artVariant,'black');
});
