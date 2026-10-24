"""Design-only recursive material costs and unapproved budget curves."""
import json,csv,pathlib,math,collections,statistics
root=pathlib.Path(__file__).resolve().parents[3];c=json.loads((root/'data/test-profiles/five-stat-device-test-v1/catalog.json').read_text());units={u['id']:u for u in c['characters']};recipes={}
for r in c['recipes']:
 assert r['resultId'] not in recipes,'multiple recipes require explicit route selection';recipes[r['resultId']]=r
cache={}
def cost(i,visiting=()):
 assert i in units and i not in visiting,('missing/cyclic',i)
 if i in cache:return cache[i]
 if units[i]['rarity']=='COMMON':assert i not in recipes;cache[i]=1
 else:
  assert i in recipes,('unobtainable',i);a=recipes[i]['ingredients'];assert all(isinstance(z['count'],int) and z['count']>0 for z in a);cache[i]=sum(z['count']*cost(z['unitId'],visiting+(i,)) for z in a)
 return cache[i]
def earned(i):
 if i not in recipes:return 0,0
 a=recipes[i]['ingredients'];total=0
 for z in a:
  e,prior=earned(z['unitId']);need=units[z['unitId']]['growth'][z['minLevel']-1]['totalXp'];assert need>=prior;total+=z['count']*(e+need-prior)
 result=sum(z['count']*units[z['unitId']]['growth'][z['minLevel']-1]['totalXp'] for z in a)//sum(z['count'] for z in a)
 return total,result
rows=[]
for u in c['characters']:
 k=cost(u['id']);e,_=earned(u['id']);s=u['combatGrowth'][19];fits=u['suitability'];defs=collections.Counter(u['defense'].values());skills=[x for x in u['skills'] if x.get('learnLevel',1)<=20]
 rows.append(dict(id=u['id'],name=u['name'],grade=u['rarity'],commonCost=k,externalXpMin=e,**{'lv20_'+key:value for key,value in s.items()},primarySuitability=max(fits.values()),positiveSuitability=sum(max(0,v) for v in fits.values()),negativeSuitability=sum(min(0,v) for v in fits.values()),weak=defs['weak'],resist=defs['resist'],immune=defs['immune'],reflect=defs['reflect'],learnedSkills20=len(skills),maxSkillPower20=max(x['power'] for x in skills),linearBudget=round(1+.025*(k-1),6),logBudget=round(1+.15*math.log2(k),6),sqrtBudget=round(1+.15*(math.sqrt(k)-1),6)))
assert len(rows)==60 and len(recipes)==52 and cost('u09')==2 and cost('u10')==3 and cost('u28')==10
for key in ['linearBudget','logBudget','sqrtBudget']:
 sortedRows=sorted(rows,key=lambda r:r['commonCost']);assert all(a[key]<=b[key] for a,b in zip(sortedRows,sortedRows[1:]));assert all(r[key]==1 for r in rows if r['commonCost']==1)
out={'status':'design_candidates_only_not_runtime','sourceVersion':c['version'],'costRule':'COMMON=1; result=sum(count*recursive ingredient cost); current unique recipes only','alternativesFound':0,'units':rows,'sameCostGroups':{str(k):[r['id'] for r in rows if r['commonCost']==k] for k in sorted({r['commonCost'] for r in rows})},'topSummary':{g:{'commonMin':min(r['commonCost'] for r in a),'commonMax':max(r['commonCost'] for r in a),'commonAverage':statistics.mean(r['commonCost'] for r in a),'xpMin':min(r['externalXpMin'] for r in a),'xpMax':max(r['externalXpMin'] for r in a),'xpAverage':statistics.mean(r['externalXpMin'] for r in a)} for g in ['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'] if (a:=[r for r in rows if r['grade']==g])}}
p=pathlib.Path(__file__).parent;(p/'costs60.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
with (p/'costs60.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
print('60 units, 52 unique recipes; cycles/missing IDs=0; all three curves monotonic; duplicate ingredient count verified')
print(out['topSummary'])
