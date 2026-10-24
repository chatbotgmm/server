"""Read-only catalog calculations. Proposal data never imported by the game."""
import json, collections, functools, pathlib, math
root=pathlib.Path(__file__).resolve().parents[3]
c=json.loads((root/'data/test-profiles/five-stat-device-test-v1/catalog.json').read_text()); units={u['id']:u for u in c['characters']}; recipes={r['resultId']:r for r in c['recipes']}
def xp(i,l):return units[i]['growth'][l-1]['totalXp']
def crafted_xp(i):
 if i not in recipes:return 0
 a=recipes[i]['ingredients'];return sum(xp(z['unitId'],z['minLevel'])*z['count'] for z in a)//sum(z['count'] for z in a)
def expand(i):
 if i not in recipes:return collections.Counter({i:1}),0
 leaves=collections.Counter(); earned=0
 for a in recipes[i]['ingredients']:
  t,e=expand(a['unitId']);n=a['count'];leaves.update({k:v*n for k,v in t.items()})
  earned+=n*(e+xp(a['unitId'],a['minLevel'])-crafted_xp(a['unitId']))
 return leaves,earned
@functools.cache
def draws(rem):
 active=[i for i,v in enumerate(rem) if v]
 if not active:return 0.
 p=.75/8
 return (1+sum(p*draws(rem[:i]+(rem[i]-1,)+rem[i+1:]) for i in active))/(p*len(active))
rows=[];bottleneck=collections.Counter()
for i in [f'u{n:02}' for n in range(25,33)]:
 leaves,earned=expand(i);bottleneck.update(leaves)
 rows.append(dict(id=i,name=units[i]['name'],commonCopies=sum(leaves.values()),commonCounts=dict(leaves),externalXpAtMinimum=earned,resultLevel=max(n+1 for n,g in enumerate(units[i]["growth"]) if g["totalXp"]<=crafted_xp(i)),lowDrawExpectedCost=round(draws(tuple(leaves.get(f'u{n:02}',0) for n in range(1,9)))*3,2)))
def dist(n,p,pity):
 # number of successes and current misses; hard pity replaces the next failed draw.
 states={(0,0):1.}
 for _ in range(n):
  out=collections.defaultdict(float)
  for (k,m),v in states.items():
   q=1 if m==pity-1 else p;out[k+1,0]+=v*q
   if q<1:out[k,m+1]+=v*(1-q)
  states=out
 return dict(draws=n,expectedEpics=sum(k*v for (k,m),v in states.items()),probabilityAtLeastOne=1-sum(v for (k,m),v in states.items() if k==0))
plans={}
for name,p,pity in [('recommended',.01,200),('relaxed',.02,100)]:
 plans[name]=dict(highEpicProbability=p,epicPity=pity,expectedDrawsToEpic=(1-(1-p)**pity)/p,expectedTicketsToEpic=20*(1-(1-p)**pity)/p,windows={label:dist(n,p,pity) for label,n in [('new7MaterialPlan',2),('new7AllHigh',36),('new7CouponAllHigh',61),('ordinaryDayAllHigh',4)]})
assert 179*3+20*8+2*20==737
assert 200+7*2*30+118==738
assert 2*30+14+10==84
assert sum(bottleneck.values())==61
assert all(x['resultLevel']==15 for x in rows)
assert abs(dist(2,.01,200)['probabilityAtLeastOne']-(1-.99**2))<1e-12
out=dict(status='proposal_not_runtime_approved',sourceCatalogVersion=c['version'],budgets=dict(new7=738,new7WithCoupon=1238,ordinaryDay=84),epics=rows,commonBottleneck=dict(bottleneck),plans=plans)
path=pathlib.Path(__file__).with_name('calculations.json');path.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n');print(json.dumps(out,ensure_ascii=False,indent=2))
