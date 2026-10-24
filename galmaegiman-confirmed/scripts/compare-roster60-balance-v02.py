"""Unapproved balance experiments; no runtime catalog, network, or DB writes."""
import json,copy,csv,hashlib,statistics,itertools,unittest
from functools import lru_cache
from pathlib import Path
R=Path(__file__).resolve().parents[1];OLD=R/'design/roster60-review/balance-v0.1';OUT=OLD.parent/'balance-v0.2';OUT.mkdir(exist_ok=True)
def read(n):return json.loads((OLD/n).read_text())
def write(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=None if n.endswith('-matches.json') else 2)+'\n')
def half(n,d):return (2*n+d)//(2*d)
original={n:hashlib.sha256(p.read_bytes()).hexdigest() for p in OLD.iterdir() if p.is_file() for n in [p.name]}
u1=read('complete60-unit-data.draft.json');s1=read('skills32.draft.json');policy=read('policies.draft.json');u2=copy.deepcopy(u1);s2=copy.deepcopy(s1)
for doc in [u2,s2,policy]:doc['schemaVersion']='roster60-balance-draft-v0.2';doc['approved']=False
for u in u2['units']:
 u['v01ReferenceStats']=copy.deepcopy(u['referenceStats'])
 # Role-level survivability change, not rarity-based superiority.
 if u['roleProfile']=='caster':u['referenceStats']['hp']=half(u['referenceStats']['hp']*120,100)
 for k,v in u['referenceStats'].items():
  b=half(v*25,44);u['levelOneStats'][k]=b;u['growth'][k]={'levelOneRounded':b,'levelOneExact':{'numerator':b,'denominator':1},'perLevelExact':{'numerator':v-b,'denominator':19}}
 u['referenceStatus']['hp_mp']='v0.2 proposal: caster HP +20%; MP unchanged; v01ReferenceStats preserves workbook comparison'
for s in s2['skills']:
 s['power']=[35,60,85,110][s['tier']-1];s['damageChannelStatus']='proposed per-skill field; not approved classification'
policy['proposed']['v02Changes']={'casterHpPercent':120,'tierPowers':[35,60,85,110],'mpCostsUnchanged':[6,12,20,30],'reason':'Reduce burst and caster fragility by role, not grade. Preserve source fit/defenses and confirmed policies.'}
write('complete60-unit-data.draft.json',u2);write('skills32.draft.json',s2);write('policies.draft.json',policy)
for filename,rows in [('complete60-unit-data.draft.csv',u2['units']),('skills32.draft.csv',s2['skills'])]:
 with (OUT/filename).open('w') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0])+['approved']);w.writeheader()
  for row in rows:w.writerow({**{k:json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for k,v in row.items()},'approved':False})
BASIC=policy['proposed']['basicAttack']
def stat(u,l):return {k:b+half((u['referenceStats'][k]-b)*(l-1),19) for k,b in u['levelOneStats'].items()}
def actor(u,ss,l):
 st=stat(u,l);return {'u':u,'st':st,'hp':st['hp'],'mp':st['mp'],'skills':[ss[x['skillId']] for x in u['learnset'] if x['level']<=l]+[BASIC]}
def damage(a,b,s):
 ak,dk=('patk','pdef') if s['damageChannel']=='physical' else ('matk','mdef');raw=max(1,s['power']*a['st'][ak]//(b['st'][dk]+40));fit=max(0,100+10*a['u']['suitability'][s['element']]);r=b['u']['defense'][s['element']]
 return (raw*fit*{'weak':150,'normal':100,'resist':50,'immune':0,'reflect':0}[r]//10000,raw*fit//100 if r=='reflect' else 0)
def choose(a,b,legacy=False):
 opts=[(s,*damage(a,b,s)) for s in a['skills']];rec=a['st']['mp']//4
 if legacy:
  s,d,_=max(opts,key=lambda x:min(b['hp'],x[1]));return None if d==0 or s['mpCost']>a['mp'] else s
 usable=[x for x in opts if x[0]['mpCost']<=a['mp'] and x[2]<a['hp']]
 kills=[x for x in usable if x[1]>=b['hp']]
 if kills:return min(kills,key=lambda x:(x[0]['mpCost'],x[2]))[0]
 # Three-own-action horizon, opponent's next affordable hit as a survival constraint.
 threat=max((damage(b,a,s)[0] for s in b['skills'] if s['mpCost']<=b['mp']),default=0)
 @lru_cache(None)
 def continuation(mp,hp,depth):
  if depth==0 or hp<=0:return 0
  values=[min(hp,dd)-rr+0.8*continuation(mp-sk['mpCost'],max(0,hp-dd),depth-1) for sk,dd,rr in opts if sk['mpCost']<=mp and rr==0]
  if rec and mp<a['st']['mp']:values.append(0.8*continuation(min(a['st']['mp'],mp+rec),hp,depth-1))
  return max(values,default=0)
 actions=usable+[(None,0,0)]
 scored=[]
 for s,d,refl in actions:
  if s is None:
   if not rec or a['mp']==a['st']['mp']:continue
   mp=min(a['st']['mp'],a['mp']+rec)
  else:mp=a['mp']-s['mpCost']
  future=continuation(mp,max(0,b['hp']-d),2)
  # Small sustainable MP value breaks near ties; immediate damage wins ties.
  mana_value=max((dd/(sk['mpCost']+rec) for sk,dd,rr in opts if sk['mpCost'] and not rr),default=0)*0.1
  score=min(b['hp'],d)+0.8*future-refl+mp*mana_value
  if a['hp']-refl<=threat:score=min(b['hp'],d)-refl # no imaginary future turn when lethal reply is available
  scored.append((score,d,mp,s))
 return max(scored,key=lambda x:x[:3])[3] if scored else None

def battle(ua,ub,skills,l,legacy=False):
 a,b=actor(ua,skills,l),actor(ub,skills,l);ps=[a,b];focus=[0,0];spent=[0,0];basic=[0,0];reflections=0
 for i in range(200):
  t=i%2;x,y=ps[t],ps[1-t];s=choose(x,y,legacy)
  if s is None:x['mp']=min(x['st']['mp'],x['mp']+x['st']['mp']//4);focus[t]+=1
  else:
   assert x['mp']>=s['mpCost'];x['mp']-=s['mpCost'];spent[t]+=s['mpCost'];d,r=damage(x,y,s);y['hp']=max(0,y['hp']-d);x['hp']=max(0,x['hp']-r);reflections+=bool(r);basic[t]+=s['id']=='basic_attack'
  assert all(0<=z['mp']<=z['st']['mp'] and 0<=z['hp']<=z['st']['hp'] for z in ps)
  if not a['hp'] or not b['hp']:break
 return {'left':ua['id'],'right':ub['id'],'level':l,'winner':ua['id'] if b['hp']==0 else ub['id'] if a['hp']==0 else None,'actions':i+1,'focus':focus,'mpSpent':spent,'basicAttacks':basic,'reflections':reflections}

def aggregate(units,ss,l,legacy=False):
 records=[];win={u['id']:0 for u in units}
 for a,b in itertools.combinations(units,2):
  for x,y in [(a,b),(b,a)]:
   r=battle(x,y,ss,l,legacy);records.append(r)
   if r['winner']:win[r['winner']]+=1
 acts=sorted(r['actions'] for r in records);n=len(records)
 summary={'level':l,'battles':n,'draws':sum(r['winner'] is None for r in records),'actionsMinMedianP90Max':[acts[0],statistics.median(acts),acts[int(.9*(n-1))],acts[-1]],'firstMoverWinRate':round(sum(r['winner']==r['left'] for r in records)/n,4),'focusPerBattle':round(sum(sum(r['focus']) for r in records)/n,3),'basicPerBattle':round(sum(sum(r['basicAttacks']) for r in records)/n,3),'mpSpentPerBattle':round(sum(sum(r['mpSpent']) for r in records)/n,3),'unitWinsOutOf118':win}
 return summary,records

class Tests(unittest.TestCase):
 def test_preserved(self):
  self.assertEqual(policy['confirmed'],read('policies.draft.json')['confirmed']);self.assertFalse(u2['approved']);self.assertFalse(s2['approved'])
  self.assertEqual(len(u2['units']),60);self.assertEqual(len(s2['skills']),32)
  for a,b in zip(u1['units'],u2['units']):
   for k in ['id','name','grade','suitability','defense','learnset','passives']:self.assertEqual(a[k],b[k])
   self.assertEqual(stat(b,20),b['referenceStats'])
 def test_affordable_basic_and_focus(self):
  ss={s['id']:s for s in s2['skills']};a=actor(u2['units'][0],ss,40);b=actor(u2['units'][2],ss,40)
  a['mp']=0;b['hp']=1;self.assertEqual(choose(a,b)['id'],'basic_attack')
  b['hp']=b['st']['hp'];a['hp']=99999;self.assertIsNone(choose(a,b))
  a['mp']=6;s=choose(a,b);self.assertTrue(s is None or s['mpCost']<=6)
 def test_immune_reflect_alternative(self):
  ss={s['id']:s for s in s2['skills']};a=actor(u2['units'][52],ss,40);b=actor(u2['units'][46],ss,40)
  self.assertGreater(damage(a,b,ss['fire_4'])[1],0);s=choose(a,b);self.assertIsNotNone(s);self.assertNotEqual(s['element'],'fire')
  b['u']=copy.deepcopy(b['u']);b['u']['defense']['fire']='immune';self.assertEqual(damage(a,b,ss['fire_4']),(0,0));self.assertNotEqual(choose(a,b)['element'],'fire')
 def test_wall_and_reflection(self):
  ss={s['id']:s for s in s2['skills']};u=copy.deepcopy(u2['units'][0]);u['defense']={e:'immune' for e in u['defense']}
  r=battle(u,u,ss,40);self.assertIsNone(r['winner']);self.assertEqual(r['actions'],200)
  a=actor(u2['units'][52],ss,40);b=actor(u2['units'][46],ss,40);d,returned=damage(a,b,ss['fire_4']);self.assertEqual(d,0);self.assertGreater(returned,0)
  a['u']=copy.deepcopy(a['u']);a['u']['defense']['fire']='reflect';self.assertEqual(damage(a,b,ss['fire_4']),(d,returned))
 def test_skill_fields_and_mp(self):
  for s in s2['skills']:
   self.assertIn(s['damageChannel'],['physical','magical']);self.assertIn('proposed',s['damageChannelStatus']);self.assertEqual(s['power'],[35,60,85,110][s['tier']-1]);self.assertEqual(s['mpCost'],[6,12,20,30][s['tier']-1])
  for u in u2['units']:
   for l in [20,40,60]:
    st=stat(u,l);self.assertGreater(st['mp']//4,0)
 def test_fixed_rounding_and_bounds(self):
  for u in u2['units']:
   for l in [1,20,40,60]:self.assertTrue(all(v>0 for v in stat(u,l).values()))
  self.assertEqual(original,{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OLD.iterdir() if p.is_file()})

if __name__=='__main__':
 test=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Tests))
 if not test.wasSuccessful():raise SystemExit(1)
 report={'approved':False,'testsPassed':test.testsRun,'limitations':['Deterministic independent arithmetic model; not runtime or device evidence','Three own actions lookahead, not adversarial search; survival uses affordable next-hit estimate','No items, switches, escape, augments, rebirth or PvP first-turn policy','No global balancing guarantee; equal-level unreborn fixtures'],'comparisons':{}}
 for label,ud,sd,legacy in [('v01_old_policy',u1,s1,True),('v01_new_policy',u1,s1,False),('v02_new_policy',u2,s2,False)]:
  ss={s['id']:s for s in sd['skills']};entries=[]
  for l in [20,40,60]:
   summary,rows=aggregate(ud['units'],ss,l,legacy);entries.append(summary);write(f'{label}-lv{l}-matches.json',{'approved':False,'matches':rows});print(label,json.dumps(summary,ensure_ascii=False),flush=True)
  report['comparisons'][label]=entries
 write('comparison-summary.json',report)
 assert original=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OLD.iterdir() if p.is_file()}
