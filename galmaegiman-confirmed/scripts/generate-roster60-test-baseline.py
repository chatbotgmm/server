"""User-authorized changeable TEST profile, not approval of preserved workbook values."""
import json,math
from pathlib import Path
source=json.load(open('design/roster60-review/workbook-complete-proposal.json'))
sk=json.load(open('design/roster60-review/balance-v0.2/skills32.draft.json'))['skills']
version='five-stat-device-test-v1'
out=Path('data/test-profiles')/version;out.mkdir(parents=True,exist_ok=True)
grades=dict(zip(['커먼','언커먼','레어','에픽','전설','히든','제한','초월','불멸','영원'],['COMMON','UNCOMMON','RARE','EPIC','LEGEND','HIDDEN','LIMITED','TRANSCEND','IMMORTAL','ETERNAL']))
fit=dict(zip(grades,[2,2,3,3,4,4,5,5,6,6]))
def rnd(x):return math.floor(x+.5)
skills=[{'id':s['id'],'name':s['name'],'element':s['element'].upper(),'power':[24,36,50,68][s['tier']-1],'mpCost':[4,8,14,22][s['tier']-1],'nameStatus':s['nameStatus']} for s in sk]
byname={s['name']:s for s in skills};units=[]
for u in source['units']:
 tank='고체력' in u['role'];mage='집중' in u['role'];fast='기습' in u['role'] or u['id']=='u02'
 base={'hp':rnd(u['hp']*.55),'mp':max(12,rnd(u['mp']*.55)),'durability':30 if tank else 18 if mage else 24,'statusResistance':20 if tank else 12,'speed':24 if fast else 10 if tank else 18}
 inc={'hp':(u['hp']-base['hp'])/19,'mp':(u['mp']-base['mp'])/19,'durability':.65 if tank else .45,'statusResistance':.25,'speed':.18}
 growth=[];combat=[];xp=0
 for lv in range(1,100):
  if lv>1:xp+=rnd(20*(lv-1)**1.5)
  stats={k:base[k]+rnd(inc[k]*(lv-1)) for k in base};growth.append({'totalXp':xp,'maxMp':stats['mp']});combat.append(stats)
 learned=[dict(byname[s['name']],learnLevel=s['level']) for s in u['learnset']]
 units.append({'id':u['id'],'name':u['name'],'rarity':grades[u['grade']],'role':u['role'],'stats':combat[0],'levelOneStats':base,'growthPerLevel':inc,'growth':growth,'combatGrowth':combat,'suitability':{k.upper():(fit[u['grade']] if v==2 else v) for k,v in u['aptitudes'].items()},'defense':{k.upper():('immune' if v=='null' else v) for k,v in u['defenses'].items()},'attack':{'id':'basic_attack','name':'공격','element':'NORMAL','power':16,'mpCost':0},'skills':learned})
calculation={'model':'linear-level-hyperbolic-v1','version':version,'levelSlope':.025,'durabilityScale':50,'resistanceScale':50}
c={'version':version,'profileKind':'user-authorized-test-baseline','approved':True,'productionBalanceApproved':False,'statSchema':'gmm-five-stats-v1','rebirthPolicy':'hp-mp-durability-additive-v1','combatPolicy':calculation,'characters':units,'recipes':source['recipes'],'skillLibrary':skills,'notes':['All numeric values are replaceable test baseline, not final production balance.','Original workbook remains approved:false.','HP/MP reference level20 retained for comparison only.','Accuracy100%,critical0,no damage variance; no extra creature passives.','All learned skills up to6; no implicit four-slot truncation.']}
p={'approved':True,'profileKind':c['profileKind'],'productionBalanceApproved':False,'version':version,'catalogVersion':version,'calculation':calculation,'reflection':'return-post-durability','enemyStrategy':'best-affordable','maxRounds':100,'encounters':[{'id':'tutorial','speciesId':'u02','level':1,'tutorial':True,'winXp':30,'lossXp':0}]+[{'id':'training-'+str(lv),'speciesId':species,'level':lv,'tutorial':False,'winXp':60*lv,'lossXp':20*lv} for lv,species in [(1,'u07'),(5,'u09'),(10,'u17'),(20,'u31'),(40,'u45'),(60,'u57')]]}
for name,value in [('catalog.json',c),('battle-policy.json',p)]: (out/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
print(version,len(units),len(skills),len(c['recipes']))
