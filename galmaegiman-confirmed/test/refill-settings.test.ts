import {afterEach,describe,it,expect} from 'vitest';
import {mkdtemp,rm,writeFile,mkdir,readFile,access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {execFileSync} from 'node:child_process';
import {defaultRefillSettings,readRefillSettings,changeRefillAmount,refillCreditsSince,refillAmountAt,type RefillSettings} from '../src/refill-settings.js';
const dirs:string[]=[];
const time=(s:string)=>new Date(`2026-09-${s}+09:00`);
async function temp(){const dir=await mkdtemp(join(tmpdir(),'galmaegiman-refill-'));dirs.push(dir);return join(dir,'config/refill-settings.json');}
afterEach(async()=>{await Promise.all(dirs.splice(0).map(dir=>rm(dir,{recursive:true,force:true})));});
describe('관리자 충전 설정과 시점별 정산',()=>{
  it('설정이 없는 경우 기본 10회, 날짜가 바뀌어도 모든 지나간 슬롯 정산',async()=>{
    const settings=await readRefillSettings(await temp());
    expect(refillAmountAt(settings,time('18T08:00:00'))).toBe(10);
    expect(refillCreditsSince(settings,time('18T08:00:00'),time('19T18:00:00'))).toBe(60);
    expect(refillCreditsSince(settings,time('18T09:00:00'),time('18T09:00:00'))).toBe(0);
    expect(refillCreditsSince(settings,time('18T12:00:00'),time('18T09:00:00'))).toBe(0);
  });
  it('접속이 늦어도 오전은 10회, 변경 후 점심·저녁은 20회',async()=>{
    const path=await temp();
    const settings=await changeRefillAmount(20,path,time('18T10:00:00'));
    expect(refillCreditsSince(settings,time('18T08:00:00'),time('18T18:00:00'))).toBe(50);
    expect(refillCreditsSince(settings,time('18T09:00:00'),time('18T18:00:00'))).toBe(40);
    expect(await readRefillSettings(path)).toEqual(settings);
    expect(refillAmountAt(settings,time('18T09:59:59'))).toBe(10);
    expect(refillAmountAt(settings,time('18T10:00:00'))).toBe(20);
  });
  it('정각 변경은 그 정각부터, 정각 직후 변경은 다음 슬롯부터 적용',()=>{
    const make=(at:string):RefillSettings=>({...defaultRefillSettings(),changes:[{at:time(at).toISOString(),amount:20}]});
    expect(refillCreditsSince(make('18T09:00:00'),time('18T08:00:00'),time('18T09:00:00'))).toBe(20);
    expect(refillCreditsSince(make('18T09:00:00.001'),time('18T08:00:00'),time('18T09:00:00.001'))).toBe(10);
    expect(refillCreditsSince(make('18T09:00:00.001'),time('18T08:00:00'),time('18T12:00:00'))).toBe(30);
  });
  it('동일 슬롯 사이 여러 변경과 0회 중지는 소급·중복 지급하지 않음',async()=>{
    const path=await temp();
    await changeRefillAmount(20,path,time('18T10:00:00'));
    await changeRefillAmount(7,path,time('18T10:00:00.001'));
    await changeRefillAmount(0,path,time('18T12:00:00.001'));
    const settings=await changeRefillAmount(4,path,time('19T10:00:00'));
    expect(refillCreditsSince(settings,time('18T08:00:00'),time('19T18:00:00'))).toBe(10+7+0+0+4+4);
    expect(refillCreditsSince(settings,time('18T12:00:00'),time('19T18:00:00'))).toBe(8);
  });
  it('음수·소수·초과값·깨진 설정은 파일을 덮어쓰거나 기본값으로 처리하지 않음',async()=>{
    const path=await temp();await changeRefillAmount(20,path,time('18T10:00:00'));
    const before=await readFile(path,'utf8');
    for(const value of [-1,1.5,1001,NaN])await expect(changeRefillAmount(value,path)).rejects.toThrow();
    expect(await readFile(path,'utf8')).toBe(before);
    await expect(changeRefillAmount(30,path,time('18T09:00:00'))).rejects.toThrow('변경 시각');
    await writeFile(path,'broken');
    await expect(changeRefillAmount(30,path)).rejects.toThrow();
    await expect(readRefillSettings(path)).rejects.toThrow();
    expect(await readFile(path,'utf8')).toBe('broken');
    await expect(access(`${path}.lock`)).rejects.toThrow();
  });
  it('동시 설정 작업은 잠금으로 직렬화하며 마지막 변경을 유실하지 않음',async()=>{
    const path=await temp();await mkdir(dirname(path),{recursive:true});await writeFile(`${path}.lock`,'');
    await expect(changeRefillAmount(20,path)).rejects.toThrow('다른 충전 설정');
    await expect(access(path)).rejects.toThrow();
    await rm(`${path}.lock`);
    await changeRefillAmount(20,path,time('18T10:00:00'));
    await changeRefillAmount(20,path,time('18T11:00:00'));
    expect((await readRefillSettings(path)).changes).toHaveLength(1);
  });
  it('관리 명령으로 변경·조회하고 잘못된 입력은 기존 설정 보존',async()=>{
    const path=await temp(),dir=dirname(dirname(path));
    await writeFile(join(dir,'package.json'),JSON.stringify({name:'galmaegiman-confirmed',type:'module'}));
    const args=['--import',new URL('../node_modules/tsx/dist/loader.mjs',import.meta.url).href,new URL('../scripts/refill.ts',import.meta.url).pathname];
    const cli=(extra:string[])=>execFileSync(process.execPath,[...args,...extra],{cwd:dir,encoding:'utf8',stdio:['ignore','pipe','pipe']});
    expect(cli(['20'])).toContain('+20회');
    expect(cli([])).toContain('현재 설정');
    const before=await readFile(path,'utf8');
    for(const value of ['-1','2.5','1001','20abc'])expect(()=>cli([value])).toThrow();
    expect(await readFile(path,'utf8')).toBe(before);
  });
});
