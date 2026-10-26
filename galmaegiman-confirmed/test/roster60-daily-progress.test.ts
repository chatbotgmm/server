import {describe,it,expect} from 'vitest';
import {rosterDailyDay,rosterDailyIncrements} from '../src/roster60-daily-progress.js';
describe('new roster completion daily mapping',()=>{
 it('uses the server completion date in Korea across midnight',()=>{
  expect(rosterDailyDay(new Date('2026-10-26T14:59:59.999Z'))).toBe('2026-10-26');
  expect(rosterDailyDay(new Date('2026-10-26T15:00:00.000Z'))).toBe('2026-10-27');
  expect(()=>rosterDailyDay(new Date('invalid'))).toThrow('INVALID_SERVER_COMPLETION_TIME');
 });
 it('counts permanently granted quantities, never a candidate or a reroll',()=>{
  expect(rosterDailyIncrements('DRAW',5)).toEqual({draws:5});
  for(const invalid of [0,-1,1.5,NaN,Infinity])expect(()=>rosterDailyIncrements('DRAW',invalid)).toThrow('INVALID_COMPLETED_AMOUNT');
 });
 it('maps new EPIC to the existing 에픽 field while new RARE stays 레어',()=>{
  expect(rosterDailyIncrements('CRAFT',1,'RARE')).toEqual({crafts:1});
  expect(rosterDailyIncrements('CRAFT',1,'EPIC')).toEqual({crafts:1,craftRare:1});
  for(const rarity of ['LEGEND','HIDDEN'])expect(rosterDailyIncrements('CRAFT',1,rarity)).toEqual({crafts:1,craftLegend:1});
  for(const rarity of ['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'])expect(rosterDailyIncrements('CRAFT',1,rarity)).toEqual({crafts:1,craftTop:1});
 });
 it('keeps successful expedition returns in the existing daily field',()=>{
  expect(rosterDailyIncrements('EXPEDITION',1)).toEqual({expeditions:1});
 });
});
