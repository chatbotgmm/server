import json,pathlib,collections
root=pathlib.Path(__file__).resolve().parents[3];c=json.loads((root/'data/test-profiles/five-stat-device-test-v1/catalog.json').read_text());u={x['id']:x for x in c['characters']};r={x['resultId']:x for x in c['recipes']};top={'LIMITED','TRANSCEND','IMMORTAL','ETERNAL'}
def resultxp(i):
 if i not in r:return 0
 a=r[i]['ingredients'];return sum(u[z['unitId']]['growth'][z['minLevel']-1]['totalXp']*z['count'] for z in a)//sum(z['count'] for z in a)
def walk(i,seen=()):
 assert i in u and i not in seen,('missing or cycle',i,seen)
 if i not in r:
  assert u[i]['rarity']=='COMMON',('unobtainable leaf',i)
  return 1,0
 n=xp=0
 for a in r[i]['ingredients']:
  m,x=walk(a['unitId'],seen+(i,));n+=m*a['count'];delta=u[a['unitId']]['growth'][a['minLevel']-1]['totalXp']-resultxp(a['unitId']);assert delta>=0;xp+=(x+delta)*a['count']
 return n,xp
rows=[];viol=[]
for i in u:
 walk(i)
 if u[i]['rarity'] not in top:continue
 bad=[a['unitId'] for a in r[i]['ingredients'] if u[a['unitId']]['rarity'] in top];viol.extend((i,j) for j in bad);n,x=walk(i)
 rows.append(dict(id=i,name=u[i]['name'],grade=u[i]['rarity'],before=r[i]['ingredients'],proposed=r[i]['ingredients'],change='retain_no_peer_dependency',commonEquivalent=n,externalXp=x,resultLevel=max(k+1 for k,g in enumerate(u[i]['growth']) if g['totalXp']<=resultxp(i))))
old=json.loads((root/'data/units.json').read_text())['units'];om={x['id']:x for x in old};ob=[]
for x in old:
 if x['tier'] in top:
  bad=[i for i,n in x['recipe'] if om[i]['tier'] in top]
  if bad:ob.append(dict(id=x['id'],name=x['name'],grade=x['tier'],peerIngredients=bad))
optional=[]
for target,oldid,newid,reason in [('u57','u43','u25','무명과 잿불새를 룬장인의 성 문양으로 잇는 여명'),('u59','u41','u29','몽마의 악몽과 흑등의 어둠에 서리마녀의 차가운 밤을 결합')]:
 before=json.loads(json.dumps(r[target]['ingredients']));oldn,oldx=walk(target)
 for a in r[target]['ingredients']:
  if a['unitId']==oldid:a['unitId']=newid
 sig=lambda aa:sorted((z['unitId'],z['count']) for z in aa)
 assert not any(k!=target and sig(v['ingredients'])==sig(r[target]['ingredients']) for k,v in r.items()),'duplicate recipe'
 n,x=walk(target);optional.append(dict(id=target,before=before,after=json.loads(json.dumps(r[target]['ingredients'])),commonBefore=oldn,commonAfter=n,xpBefore=oldx,xpAfter=x,reason=reason,status='optional_unapproved_cost_normalization'))
 r[target]['ingredients']=before
out=dict(optionalChanges=optional,status='review_only_no_runtime_changes',newTopRecipes=rows,newPeerDependencyViolations=viol,legacyPeerDependencyViolations=ob,gradeSummary={g:dict(count=len(a:=[z for z in rows if z['grade']==g]),averageCommon=sum(z['commonEquivalent'] for z in a)/len(a),averageXp=sum(z['externalXp'] for z in a)/len(a)) for g in sorted(top)},validation={'units':len(u),'recipes':len(r),'allReachCommonLeaves':True,'cycles':0})
assert len(rows)==16 and not viol
pathlib.Path(__file__).with_name('audit.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
for z in rows:print(z['id'],z['name'],z['commonEquivalent'],z['externalXp'],z['resultLevel'])
print('legacy',ob);print(out['gradeSummary'])
