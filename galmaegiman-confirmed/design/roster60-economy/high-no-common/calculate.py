"""Unapproved 30-ticket candidate; no runtime imports or mutations."""
import json, pathlib, math
p=.01;price=30
out={'status':'candidate_not_approved','confirmedDirection':['no common in HIGH','HIGH price greater than prior proposed 20'],'candidate':{'price':price,'probabilities':{'COMMON':0,'UNCOMMON':.80,'RARE':.19,'EPIC':p}},'pity':[],'budgets':[],'materialEfficiencyPer100Tickets':{}}
for n in [100,150,200]:
 e=(1-(1-p)**n)/p
 # Independent sum of survival probabilities checks the truncated geometric formula.
 assert math.isclose(e,sum((1-p)**k for k in range(n)),rel_tol=1e-12)
 out['pity'].append({'threshold':n,'expectedDrawsToFirstEpic':e,'expectedTicketsToFirstEpic':price*e,'guaranteedMaximumTicketsToAnyEpic':price*n,'probabilityOfReachingPityDraw':(1-p)**(n-1)})
for label,budget in [('ordinaryDay_twoClaims_matureAttendanceDay4',84),('new7_twoClaimsDaily',738),('new7_twoClaimsDaily_plusExistingCoupon',1238),('new7_fourClaimsDaily',1158)]:
 count=budget//price
 out['budgets'].append({'label':label,'tickets':budget,'allHighDraws':count,'leftoverTickets':budget%price,'expectedDirectEpics':count*p,'probabilityAtLeastOneDirectEpic':1-(1-p)**count,'prior20TicketHighDraws':budget//20})
for name,cost,probs in [('LOW_proposed',3,[.75,.25,0,0]),('MID_proposed',8,[.40,.55,.05,0]),('HIGH_candidate',30,[0,.8,.19,.01])]:
 assert math.isclose(sum(probs),1)
 out['materialEfficiencyPer100Tickets'][name]={k:100*v/cost for k,v in zip(['COMMON','UNCOMMON','RARE','EPIC'],probs)}
path=pathlib.Path(__file__).with_name('calculations.json');path.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n');print(json.dumps(out,ensure_ascii=False,indent=2))
