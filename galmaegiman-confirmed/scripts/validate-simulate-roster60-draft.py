"""Review-only deterministic arithmetic simulator. NOT the authenticated game engine."""
import json,csv,unittest,copy,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'design/roster60-review/balance-v0.1'
U=json.loads((D/'complete60-unit-data.draft.json').read_text());S=json.loads((D/'skills32.draft.json').read_text());P=json.loads((D/'policies.draft.json').read_text());R=json.loads((D/'recipes52.source-review.json').read_text())
units={u['id']:u for u in U['units']};skills={s['id']:s for s in S['skills']};basic=P['proposed']['basicAttack'];keys=['hp','mp','patk','matk','pdef','mdef'];elements=P['confirmed']['elements']
def half(n,d):return (2*n+d)//(2*d)
def stats(u,level,rebirth=0):
 if not 1<=level<=(99 if rebirth else 60) or not 0<=rebirth<=5 or (rebirth and not u['rebirthAllowed']):raise ValueError('invalid level/rebirth')
 return {k:max(1,half(u['levelOneStats'][k]*(100+10*rebirth),100)+half((v-u['levelOneStats'][k])*(level-1),19)) for k,v in u['referenceStats'].items()}
def actor(id,level=20,rebirth=0):
 u=units[id];v=stats(u,level,rebirth);return {'id':id,'unit':u,'stats':v,'hp':v['hp'],'mp':v['mp'],'skills':[skills[x['skillId']] for x in u['learnset'] if x['level']<=level]}
def focus(a):
 amount=min(a['stats']['mp']-a['mp'],a['stats']['mp']//4);a['mp']+=amount;return amount
def preview(a,b,s):
 attack,defense=('patk','pdef') if s['damageChannel']=='physical' else ('matk','mdef')
 raw=max(1,s['power']*a['stats'][attack]//(b['stats'][defense]+40))
 fit=max(0,100+10*a['unit']['suitability'][s['element']]);reaction=b['unit']['defense'][s['element']]
 mult={'weak':150,'normal':100,'resist':50,'immune':0,'reflect':0}[reaction]
 return {'targetDamage':min(b['hp'],raw*fit*mult//10000),'selfDamage':min(a['hp'],raw*fit//100) if reaction=='reflect' else 0,'reaction':reaction,'base':raw}
def cast(a,b,s):
 if a['mp']<s['mpCost']:raise ValueError('INSUFFICIENT_MP')
 p=preview(a,b,s);a['mp']-=s['mpCost'];b['hp']-=p['targetDamage'];a['hp']-=p['selfDamage'];return p

def battle(left,right,level=20,force_normal_only=False):
 a,b=actor(left,level),actor(right,level);players=[a,b];focuses=[0,0];spent=[0,0];casts=[0,0]
 if force_normal_only:
  for x in players:x['skills']=[s for s in x['skills'] if s['element']=='normal']
 for step in range(200):
  turn=step%2;x,y=players[turn],players[1-turn]
  options=sorted(x['skills']+[basic],key=lambda s:preview(x,y,s)['targetDamage'],reverse=True)
  choice=options[0]
  if preview(x,y,choice)['targetDamage']==0 or x['mp']<choice['mpCost']:
   focus(x);focuses[turn]+=1
  else:
   cast(x,y,choice);casts[turn]+=1;spent[turn]+=choice['mpCost']
  if min(a['hp'],b['hp'])<=0:break
 return {'left':left,'right':right,'level':level,'actions':step+1,'winner':left if b['hp']<=0 else right if a['hp']<=0 else 'draw_cap','remainingHp':[a['hp'],b['hp']],'remainingMp':[a['mp'],b['mp']],'focusActions':focuses,'mpSpent':spent,'attackActions':casts,'fixtureOverride':'normal-only stress fixture' if force_normal_only else None,'strategy':'highest projected target damage; focus until its cost is available; avoids reflection; player first (proposal)'}

class Tests(unittest.TestCase):
 def test_structure_and_source(self):
  source=json.loads((ROOT/'design/roster60-review/workbook-complete-proposal.json').read_text())
  self.assertEqual(list(units),[f'u{i:02}' for i in range(1,61)]);self.assertEqual(len(skills),32);self.assertEqual(len(R['recipes']),52)
  for f in [U,S,P,R]:self.assertIs(f['approved'],False)
  for u,orig in zip(U['units'],source['units']):
   self.assertEqual(set(u['levelOneStats']),set(keys));self.assertEqual(u['name'],orig['name']);self.assertEqual(u['referenceStats']['hp'],orig['hp']);self.assertEqual(u['referenceStats']['mp'],orig['mp'])
   self.assertEqual(stats(u,20),u['referenceStats']);self.assertEqual(stats(u,1),u['levelOneStats']);self.assertEqual(u['passives'],[])
   self.assertEqual(set(u['defense']),set(elements));self.assertEqual(set(u['suitability']),set(elements));self.assertEqual(u['skillSlotCapacity'],6)
   self.assertEqual(len(u['learnset']),5)
   for x in u['learnset']:
    self.assertIn(x['skillId'],skills);self.assertIn(x['level'],[1,5,10,24,40]);self.assertLessEqual(skills[x['skillId']]['mpCost'],stats(u,x['level'])['mp'])
 def test_all_growth_and_rebirth(self):
  for u in units.values():
   previous=stats(u,1)
   for level in range(2,61):
    current=stats(u,level)
    for k in keys:self.assertGreaterEqual(current[k],previous[k]);self.assertGreater(current[k],0)
    previous=current
   if u['rebirthAllowed']:
    for n in range(1,6):
     # Exact baseline scaling; no repeated multiply/round from previous cycle.
     for k,v in u['referenceStats'].items():self.assertEqual(stats(u,1,n)[k],max(1,half(u['levelOneStats'][k]*(100+n*10),100)))
     self.assertGreater(stats(u,99,n)['hp'],0)
     for k in keys:self.assertEqual(stats(u,60,n)[k]-stats(u,60)[k],stats(u,1,n)[k]-stats(u,1)[k])
   else:
    with self.assertRaises(ValueError):stats(u,99,1)
 def test_profiles_names_and_csv(self):
  self.assertEqual(sum(len(s['learnedBy']) for s in skills.values()),300)
  self.assertEqual(sum(s['nameStatus']=='confirmed_in_parent_history' for s in skills.values()),6)
  self.assertEqual(skills['lightning_3']['name'],'벼락송곳');self.assertIn('NOT',skills['lightning_3']['sourceNameConcern'])
  self.assertEqual(len(list(csv.DictReader((D/'complete60-unit-data.draft.csv').open()))),60)
  self.assertEqual(len(list(csv.DictReader((D/'skills32.draft.csv').open()))),32)
  for u in units.values():
   e=u['primarySkillElement'];self.assertEqual(u['suitability'][e],u['sourceSuitability'][e]+u['suitabilityProposal']['gradeBandAddition'])
   for other in elements:
    if other!=e:self.assertEqual(u['suitability'][other],u['sourceSuitability'][other])
 def test_xp_boundaries_and_combination(self):
  x=P['proposed']['xp'];normal=x['normalCumulativeThresholds'];reborn=x['rebornCumulativeThresholds'];self.assertEqual(len(normal),99)
  for current in range(1,99):
   delta=normal[current]-normal[current-1];self.assertGreater(delta,0)
   self.assertEqual(reborn[current]-reborn[current-1],(delta*70+99)//100 if current<60 else delta)
  self.assertEqual((1000+2001)//2,1500)
  # Average XP is unchanged; the ordinary result uses its own ordinary curve.
  inherited=(normal[19]+normal[20])//2
  level=max(i+1 for i,t in enumerate(normal[:60]) if t<=inherited);self.assertEqual(level,20)
 def test_immunity_reflection_and_no_chain(self):
  a,b=actor('u01'),actor('u52');before=copy.deepcopy((a,b));p=cast(a,b,basic);self.assertEqual(p['targetDamage'],0);self.assertEqual(b['hp'],before[1]['hp'])
  a,b=actor('u05',40),actor('u57',40);a['unit']=copy.deepcopy(a['unit']);a['unit']['defense']['holy']='reflect';old=(a['hp'],b['hp']);p=cast(a,b,skills['holy_1'])
  self.assertEqual(b['hp'],old[1]);self.assertEqual(a['hp'],old[0]-p['selfDamage']);self.assertGreater(p['selfDamage'],0)
  a['unit']['suitability']['holy']=-20;self.assertEqual(preview(a,b,skills['holy_1'])['selfDamage'],0)
 def test_mp_exhaustion_focus_and_rejection(self):
  a,b=actor('u07'),actor('u06');self.assertEqual(a['mp'],a['stats']['mp'])
  for _ in range(a['mp']//12):cast(a,b,skills['fire_2'])
  saved=copy.deepcopy((a,b))
  with self.assertRaises(ValueError):cast(a,b,skills['fire_2'])
  self.assertEqual((a,b),saved);old=a['mp'];self.assertEqual(focus(a),min(a['stats']['mp']-old,a['stats']['mp']//4))
  for _ in range(20):focus(a)
  self.assertEqual(a['mp'],a['stats']['mp'])
 def test_battle_termination_and_wall(self):
  for a,b in [('u01','u03'),('u07','u06'),('u04','u07'),('u17','u31'),('u01','u57')]:
   r=battle(a,b,40);self.assertLessEqual(r['actions'],200);self.assertTrue(all(x>=0 for x in r['remainingHp']+r['remainingMp']))
  # Explicit impossible normal-v-normal matchup, not a hidden live roster override.
  a,b=actor('u52'),actor('u52');self.assertEqual(preview(a,b,basic)['targetDamage'],0)
  r=battle('u52','u52',20,True);self.assertEqual(r['winner'],'draw_cap');self.assertEqual(r['actions'],200)

if __name__=='__main__':
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(Tests);r=unittest.TextTestRunner(verbosity=2).run(suite)
 if not r.wasSuccessful():raise SystemExit(1)
 samples=[battle(a,b,l) for a,b,l in [('u01','u03',20),('u07','u06',20),('u04','u07',20),('u17','u31',40),('u01','u57',40),('u53','u47',40),('u49','u48',40)]]
 samples.append(battle('u52','u52',20,True))
 report={'approved':False,'simulator':'draft arithmetic model, NOT runtime/auth/device evidence','testsPassed':r.testsRun,'matchups':samples,'limitations':['Player-first bias; no Monte Carlo tuning','No item/swap/augments; not a full product battle','Source concept passives are not implemented','Current results do not establish win-rate balance across60 units']}
 (D/'validation-simulation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps(samples,ensure_ascii=False,indent=2))
