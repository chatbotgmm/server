import {describe,it,expect} from 'vitest';
import {grantsSince,latestSlot,nextSlot,localDay} from '../src/schedule.js';
const d=(s:string)=>new Date(`${s}+09:00`);
describe('한국 시간 09/12/18 +3 누적',()=>{
  it.each([
    ['2026-09-18T08:59:59','2026-09-17T18:00:00'],
    ['2026-09-18T09:00:00','2026-09-18T09:00:00'],
    ['2026-09-18T11:59:59','2026-09-18T09:00:00'],
    ['2026-09-18T12:00:00','2026-09-18T12:00:00'],
    ['2026-09-18T17:59:59','2026-09-18T12:00:00'],
    ['2026-09-18T18:00:00','2026-09-18T18:00:00'],
    ['2026-09-19T00:00:00','2026-09-18T18:00:00']
  ])('%s 최신 슬롯',(now,last)=>expect(latestSlot(d(now))).toEqual(d(last)));
  it('경계 1회, 중복 없음',()=>{
    const last=d('2026-09-17T18:00:00');
    expect(grantsSince(last,d('2026-09-18T08:59:59'))).toBe(0);
    expect(grantsSince(last,d('2026-09-18T09:00:00'))).toBe(1);
    expect(grantsSince(d('2026-09-18T09:00:00'),d('2026-09-18T09:00:00'))).toBe(0);
  });
  it('오프라인과 월/연 경계',()=>{
    expect(grantsSince(d('2026-12-31T18:00:00'),d('2027-01-02T12:00:00'))).toBe(5);
    expect(grantsSince(d('2026-09-18T18:00:00'),d('2026-09-28T18:00:00'))).toBe(30);
  });
  it('시계 역행은 추가 지급하지 않음',()=>expect(grantsSince(d('2026-09-19T18:00:00'),d('2026-09-18T18:00:00'))).toBe(0));
  it('자정은 출석 경계일 뿐 획득권 초기화 아님',()=>{
    expect(localDay(new Date('2026-09-18T15:00:00Z'))).toBe('2026-09-19');
    expect(nextSlot(d('2026-09-18T18:00:00'))).toBe('09/19 09:00');
  });
});
