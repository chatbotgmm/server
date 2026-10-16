from pathlib import Path
import argparse
import difflib
import sys

p = argparse.ArgumentParser()
p.add_argument('--apply', action='store_true')
a = p.parse_args()
root = Path(__file__).resolve().parent
if not (root / 'src/battle/engine.ts').is_file():
    root = root / 'galmaegiman-confirmed'
engine = root / 'src/battle/engine.ts'
tests = root / 'test/battle-events.test.ts'
old = '''export function choose(b:Battle,side:0|1,pick:{augment?:string;switchTo?:number}){
  if(b.phase!=='choose')throw new Error('지금은 고를 때가 아님');
  const s=b.sides[side];
  if(s.offers){
    if(!pick.augment||!s.offers.includes(pick.augment))throw new Error('제안된 증강체 중 하나를 골라야 함');
    s.augs.push(pick.augment);s.offers=null;applyAugment(b,side,pick.augment);
  }
  if(s.needSwitch){
    const to=pick.switchTo;
    if(to===undefined||!s.fighters[to]||!alive(s.fighters[to]))throw new Error('나갈 갈매미를 골라야 함');
    const out=s.fighters[s.active];s.active=to;s.needSwitch=false;b.log.push(`${josa(s.fighters[to].name,'이가')} 나왔어요!`);
    event(b,{kind:'switch',actor:fighterRef(b,out),target:fighterRef(b,s.fighters[to]),reason:'faint'});
  }
  if(b.sides.every(x=>!x.offers&&!x.needSwitch))b.phase='action';
}'''
new = '''export function choose(b:Battle,side:0|1,pick:{augment?:string;switchTo?:number}){
  if(b.phase!=='choose')throw new Error('지금은 고를 때가 아님');
  const s=b.sides[side];
  const augment=s.offers?pick.augment:undefined;
  if(s.offers&&(!augment||!s.offers.includes(augment)))throw new Error('제안된 증강체 중 하나를 골라야 함');
  const to=s.needSwitch?pick.switchTo:undefined;
  if(s.needSwitch&&(to===undefined||!s.fighters[to]||!alive(s.fighters[to])))throw new Error('나갈 갈매미를 골라야 함');
  if(s.needSwitch){
    const out=s.fighters[s.active];
    s.active=to!;s.needSwitch=false;
    const incoming=s.fighters[s.active];
    b.log.push(`${josa(incoming.name,'이가')} 나왔어요!`);
    event(b,{kind:'switch',actor:fighterRef(b,out),target:fighterRef(b,incoming),reason:'faint'});
  }
  if(augment){
    s.augs.push(augment);s.offers=null;applyAugment(b,side,augment);
  }
  if(b.sides.every(x=>!x.offers&&!x.needSwitch))b.phase='action';
}'''
marker = "describe('강제 교체 증강체 회귀'"
extra = '''

describe('강제 교체 증강체 회귀',()=>{
  it.each(['camo','dual','bulwark'])('%s를 새 선두에게 적용한다',(id)=>{
    const b=ready([rec('D1','out'),rec('D1','in')]);
    const s=b.sides[0],out=s.fighters[0],incoming=s.fighters[1];
    out.hp=0;out.attr='GOLD';incoming.attr='STONE';activeOf(b,1).attr='FIRE';
    b.phase='choose';s.needSwitch=true;s.offers=[id];
    const skillsBefore=JSON.stringify(incoming.skills),seq=b.eventSeq;
    choose(b,0,{augment:id,switchTo:1});
    const emitted=b.events.filter(e=>e.id>seq);
    expect(s.active).toBe(1);expect(s.needSwitch).toBe(false);expect(b.phase).toBe('action');
    expect(s.augs).toContain(id);expect(s.offers).toBeNull();
    expect(emitted[0]).toMatchObject({kind:'switch',target:{slot:1,key:'in'}});
    expect(emitted[1]).toMatchObject({kind:'augment',actor:{slot:1,key:'in'}});
    expect(out.attr).toBe('GOLD');expect(out.extraAttr).toBeUndefined();expect(out.guards).toEqual([]);
    if(id==='camo')expect(R.typeMult(incoming.attr,'FIRE')).toBe(R.data.multipliers.strong);
    if(id==='dual')expect(R.typeMult(incoming.extraAttr!,'FIRE')).toBe(R.data.multipliers.strong);
    if(id==='bulwark')expect(incoming.guards).toEqual([{pct:R.battleAug.get(id)!.p.pct,turns:R.battleAug.get(id)!.p.turns}]);
    expect(JSON.stringify(incoming.skills)).toBe(skillsBefore);
  });
  it('잘못된 선택은 일부 상태도 바꾸지 않는다',()=>{
    const b=ready([rec('D1','out'),rec('D1','in')]);
    b.phase='choose';b.sides[0].needSwitch=true;b.sides[0].offers=['camo'];activeOf(b,0).hp=0;
    const before=JSON.stringify(b);
    for(const pick of [{augment:'camo'},{augment:'camo',switchTo:0},{augment:'camo',switchTo:99},{augment:'reverse',switchTo:1}]){
      expect(()=>choose(b,0,pick)).toThrow();expect(JSON.stringify(b)).toBe(before);
    }
  });
});
'''
try:
    original = {f: f.read_text(encoding='utf-8') for f in (engine, tests)}
    if new in original[engine] or marker in original[tests]:
        sys.exit('Already applied or partially applied; no changes made. Review files.')
    if original[engine].count(old) != 1:
        sys.exit('choose() differs from reviewed source; no changes made.')
    for anchor in ('function ready(', 'const rec=', 'const R=loadBattleRules()', 'choose, createBattle'):
        if anchor not in original[tests]:
            sys.exit('Test helpers/imports differ; no changes made.')
    if not original[tests].rstrip().endswith('});'):
        sys.exit('Test ending differs; no changes made.')
    changed = {engine: original[engine].replace(old, new, 1), tests: original[tests].rstrip() + extra}
    backups = {f: f.with_name(f.name + '.augment-switch.bak') for f in changed}
    if a.apply and any(f.exists() for f in backups.values()):
        sys.exit('Backup already exists; review it first. No changes made.')
    for f, result in changed.items():
        print(''.join(difflib.unified_diff(original[f].splitlines(True), result.splitlines(True), fromfile=str(f), tofile=str(f))), end='')
    if a.apply:
        for f, backup in backups.items():
            with backup.open('x', encoding='utf-8') as out:
                out.write(original[f])
        try:
            for f, result in changed.items():
                f.write_text(result, encoding='utf-8')
        except OSError:
            for f, result in original.items():
                f.write_text(result, encoding='utf-8')
            raise
        print('Applied. Backups kept. Tests/build have NOT been run.')
    else:
        print('Dry-run only. Review diff, then use --apply.')
except OSError as e:
    sys.exit(str(e))
