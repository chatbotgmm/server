import { DateTime } from 'luxon';
export const ZONE = 'Asia/Seoul';
export const HOURS = [9, 12, 18];
export function latestSlot(now: Date): Date {
  const t = DateTime.fromJSDate(now).setZone(ZONE);
  const h = HOURS.filter(hour => t.hour >= hour).at(-1);
  return (h === undefined ? t.minus({days:1}).set({hour:18}) : t.set({hour:h}))
    .set({minute:0,second:0,millisecond:0}).toJSDate();
}
function ordinal(date: Date): number {
  const t = DateTime.fromJSDate(date).setZone(ZONE);
  const day = Math.floor(DateTime.utc(t.year,t.month,t.day).toMillis()/86_400_000);
  return day * 3 + HOURS.filter(h => t.hour >= h).length;
}
export function grantsSince(last: Date, now: Date): number {
  return Math.max(0, ordinal(now)-ordinal(last));
}
export function nextSlot(now: Date): string {
  const t=DateTime.fromJSDate(now).setZone(ZONE);
  const h=HOURS.find(h=>t.hour<h);
  return (h===undefined?t.plus({days:1}).set({hour:9}):t.set({hour:h}))
    .set({minute:0,second:0,millisecond:0}).toFormat('MM/dd HH:mm');
}
export const localDay=(now:Date)=>DateTime.fromJSDate(now).setZone(ZONE).toISODate()!;
