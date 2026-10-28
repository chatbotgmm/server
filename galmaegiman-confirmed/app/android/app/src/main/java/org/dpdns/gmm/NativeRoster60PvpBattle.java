package org.dpdns.gmm;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.util.*;
/** Shared server-owned PvP transport, including the server-created normal-game AI opponent. */
final class NativeRoster60PvpBattle extends NativeRoster60Battle {
 final Handler poll=new Handler(Looper.getMainLooper());JSONObject initial;boolean paused;Button accept,decline,waitingSurrender;TextView lossFeedback,presenceNotice,commanderStats;
 boolean preparing;int currentReadGeneration;Roster60MatchReadyView readyView;
 NativeRoster60PvpBattle(MainActivity h,JSONObject initial){super(h);this.initial=initial;}
 @Override String storageScope(){return "|human-pvp";}
 @Override void open(){
  if(initial!=null&&"ACCEPTING".equals(initial.optString("status"))){openReady();return;}
  openBattle();
 }
 void openReady(){
  if(h.api.token().isEmpty()){h.login();return;}
  preparing=true;h.epoch++;h.screen="roster60Battle";h.root.removeAllViews();
  owner=h.api.token();epoch=h.epoch;key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner+storageScope());
  readyView=new Roster60MatchReadyView(h,()->accept(true),()->accept(false),this::resume);
  h.root.addView(readyView,new FrameLayout.LayoutParams(-1,-1));
  message=readyView.notice;accept=readyView.accept;decline=readyView.decline;retry=readyView.retry;
  view=initial;readyView.bind(initial,!prefs.getString(key+":pending","").isEmpty());
  initial=null;h.screenShown();if(!prefs.getString(key+":pending","").isEmpty())resume();else schedule();
 }
 @Override void bind(JSONObject snapshot){
  // A delayed pre-accept poll must never put acceptance controls into the field.
  if(!preparing){if(snapshot!=null&&"ACCEPTING".equals(snapshot.optString("status")))return;super.bind(snapshot);return;}
  if(snapshot==null||!current())return;
  JSONObject baseline=(paused||presentationPaused)&&initial!=null?initial:view;
  if(baseline!=null&&baseline.optString("battleId").equals(snapshot.optString("battleId"))){
   if(snapshot.optInt("revision",-1)<baseline.optInt("revision",-1))return;
   String previous=baseline.optString("status"),next=snapshot.optString("status");
   if("ACTIVE".equals(previous)&&"ACCEPTING".equals(next))return;
   if(("FINISHED".equals(previous)||"SETTLED".equals(previous))&&("ACTIVE".equals(next)||"ACCEPTING".equals(next)))return;
   if("SETTLED".equals(previous)&&"FINISHED".equals(next))return;
   if(snapshot.optInt("revision")==baseline.optInt("revision")&&baseline.optBoolean("accepted"))try{snapshot=new JSONObject(snapshot.toString());snapshot.put("accepted",true);}catch(JSONException bad){return;}
  }
  if(paused||presentationPaused){initial=snapshot;currentReadGeneration++;reading=false;return;}
  if(NativeRoster60Pvp.validMode(snapshot.optString("mode")))h.rosterPvpMode=snapshot.optString("mode");
  if(snapshot.optBoolean("requeued")){pause();h.openRosterPvp();return;}
  if("ACTIVE".equals(snapshot.optString("status"))){
   readyView.stop();poll.removeCallbacksAndMessages(null);currentReadGeneration++;reading=false;preparing=false;view=null;initial=snapshot;openBattle();return;
  }
  view=snapshot;readyView.bind(snapshot,!prefs.getString(key+":pending","").isEmpty());
  String status=snapshot.optString("status");
  if("FINISHED".equals(status)){
   message.setText("대전 준비가 종료됐어요 · 서버 결과 저장 중");
   if(!snapshot.optString("battleId").equals(autoSettled)&&prefs.getString(key+":pending","").isEmpty()){String id=snapshot.optString("battleId");if(persistMutation("settle",h.obj("battleId",id,"revision",snapshot.optInt("revision"))))autoSettled=id;}
  }else if("SETTLED".equals(status)){
   String condition=terminalCondition(snapshot,"rankCondition"),pool=terminalCondition(snapshot,"matchPoolCondition");
   message.setText(NativeRoster60Pvp.poolRejection(pool)?NativeRoster60Pvp.poolMessage(pool):NativeRoster60Pvp.rankRejection(condition)?NativeRoster60Pvp.rankMessage(condition,null):"거절 또는 수락 시간 종료로 대전이 시작되지 않았어요.");
   retry.setText("매칭으로 돌아가기");retry.setOnClickListener(v->{pause();h.openRosterPvp();});
  }
 }
 @Override void error(String error){
  if(!preparing){super.error(error);return;}
  readyView.stop();message.setText(error);retry.setVisibility(android.view.View.VISIBLE);retry.setEnabled(true);
  retry.setText("다시 확인");retry.setOnClickListener(v->{if(view!=null&&"FINISHED".equals(view.optString("status"))&&prefs.getString(key+":pending","").isEmpty())mutate("settle",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision")));else resume();});
 }
 void openBattle(){accept=null;decline=null;waitingSurrender=null;super.open();if(!current())return;LinearLayout controls=(LinearLayout)settle.getParent();LinearLayout rail=(LinearLayout)controls.getParent();presenceNotice=message;presenceNotice.setTag("roster60-pvp-presence");commanderStats=h.text("",11);commanderStats.setTag("roster60-commander-reward");commanderStats.setTextColor(NavyWorkbenchTheme.TEXT);commanderStats.setMaxLines(3);commanderStats.setVisibility(android.view.View.GONE);rail.addView(commanderStats,rail.indexOfChild(controls),new LinearLayout.LayoutParams(-1,-2));lossFeedback=h.text("",11);lossFeedback.setTag("roster60-loss-feedback");lossFeedback.setTextColor(NavyWorkbenchTheme.TEXT);lossFeedback.setMaxLines(4);lossFeedback.setVisibility(android.view.View.GONE);rail.addView(lossFeedback,rail.indexOfChild(controls),new LinearLayout.LayoutParams(-1,-2));accept=h.button("대전 수락",()->accept(true));decline=h.button("대전 거절",()->accept(false));waitingSurrender=h.button("항복 · 경험치 0",()->command(h.obj("kind","escape")));controls.addView(accept,new LinearLayout.LayoutParams(0,h.dp(48),1));controls.addView(decline,new LinearLayout.LayoutParams(0,h.dp(48),1));controls.addView(waitingSurrender,new LinearLayout.LayoutParams(0,h.dp(48),1));if(view!=null)bind(view);}
 @Override void resume(){paused=false;presentationPaused=false;if(!current()||reading||(selection!=null&&selection.isShowing())){schedule();return;}String pending=prefs.getString(key+":pending","");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(JSONException bad){error("저장된 대전 요청 확인 필요");}return;}if(initial!=null){JSONObject value=initial;initial=null;bind(value);schedule();return;}reading=true;final int requestedRead=++currentReadGeneration,requestedEpoch=epoch;h.api.call("native/roster60/pvp/current",null,(r,e)->{if(requestedRead!=currentReadGeneration||requestedEpoch!=epoch)return;reading=false;if(!current())return;if(e!=null){error(e);schedule();return;}JSONObject b=r.optJSONObject("battle");if(b!=null)bind(b);else{pause();h.openRosterPvp();return;}schedule();});}
 @Override void read(String id){resume();}
 @Override void options(){h.openRosterPvp();}
 void accept(boolean value){if(view!=null&&"ACCEPTING".equals(view.optString("status"))&&(!value||!view.optBoolean("accepted")))mutate("accept",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision"),"accept",value));}
 @Override void send(JSONObject intent){JSONObject body=intent.optJSONObject("body");if(body==null)return;String id=body.optString("requestId"),op=intent.optString("operation");if(!Arrays.asList("action","settle","accept").contains(op)||id.isEmpty()){error("저장된 대전 요청 확인 필요");return;}if(!IN_FLIGHT.add(id)){message.setText("대전 요청 결과 확인 중…");schedule();return;}if(panel!=null)panel.lock();if(settle!=null)settle.setEnabled(false);if(readyView!=null&&preparing)readyView.pending();if(accept!=null){accept.setEnabled(false);decline.setEnabled(false);if(waitingSurrender!=null)waitingSurrender.setEnabled(false);}
 NativeApi sender=h.api;Runnable releaseWatch=sender.whenClosed(()->IN_FLIGHT.remove(id));
 sender.call("native/roster60/pvp/"+op,body,(r,e)->{releaseWatch.run();IN_FLIGHT.remove(id);if(!owner.equals(h.api.token()))return;boolean rejected=e!=null&&(e.contains("PENDING")||NativeRoster60Pvp.rankRejection(e)||NativeRoster60Pvp.poolRejection(e)||Arrays.asList("STALE_REVISION","PVP_NOT_FOUND","BATTLE_NOT_FOUND","BATTLE_FINISHED","BATTLE_NOT_FINISHED","INVALID_ACTION","INVALID_REQUEST","REQUEST_ID_REUSED","FORCED_SWITCH_REQUIRED","INVALID_SWITCH","INVALID_ITEM","INVALID_ITEM_TARGET","ITEM_UNAVAILABLE","NO_STATUS_TO_CLEANSE","HP_FULL","INSUFFICIENT_MP","SKILL_NOT_EQUIPPED","ACTION_ALREADY_SUBMITTED","ACCEPTANCE_REQUIRED","OPPONENT_FORCED_SWITCH","PVP_BUSY","PARTY_REQUIRED").contains(e));if(e==null||rejected)prefs.edit().remove(key+":pending").commit();if(!current())return;if(e!=null){String shown=NativeRoster60Pvp.poolRejection(e)?NativeRoster60Pvp.poolMessage(e):NativeRoster60Pvp.rankRejection(e)?NativeRoster60Pvp.rankMessage(e,null):e;if("settle".equals(op))settlementError=shown;error(shown);if(rejected&&!"settle".equals(op))resume();else schedule();return;}actionAcknowledged(op);JSONObject b=r.optJSONObject("battle");bind(b==null?r:b);schedule();});}
 @Override void renderSnapshot(JSONObject r){
 if(NativeRoster60Pvp.validMode(r.optString("mode")))h.rosterPvpMode=r.optString("mode");
 if(r.optBoolean("requeued")){pause();h.openRosterPvp();return;}
 if("ACCEPTING".equals(r.optString("status")))return; // Never render acceptance inside combat.

 String notice=rejectionNotice;super.renderSnapshot(r);if(view==null)return;boolean active="ACTIVE".equals(r.optString("status")),waiting=r.optBoolean("waitingForOpponent");settle.setVisibility(settlementError.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);if(accept!=null){accept.setVisibility(android.view.View.GONE);decline.setVisibility(android.view.View.GONE);waitingSurrender.setVisibility(active&&waiting?android.view.View.VISIBLE:android.view.View.GONE);waitingSurrender.setEnabled(active&&prefs.getString(key+":pending","").isEmpty());}
 if(waiting){panel.lock();message.setText(r.optJSONObject("state").optJSONArray("fighters").optJSONObject(1).optInt("hp")<=0?"상대 강제 교체 대기 중":"선택 완료 · 상대 선택 대기\n상대 선택 비공개");}else if(active&&r.optJSONObject("state").optJSONArray("fighters").optJSONObject(0).optInt("hp")>0)message.setText(("normal".equals(r.optString("mode"))&&"AI".equals(r.optString("opponentKind"))?"일반게임 · AI 대전":"대전")+" · 턴 "+r.optJSONObject("state").optInt("turn"));if(!prefs.getString(key+":pending","").isEmpty())panel.lock();if("SETTLED".equals(r.optString("status"))){retry.setText("마을로");retry.setOnClickListener(v->{pause();h.home();});}else{retry.setVisibility(android.view.View.GONE);retry.setText("연결 재시도");retry.setOnClickListener(v->resume());}if(active&&!notice.isEmpty())message.setText(notice);
 if("normal".equals(r.optString("mode"))&&Arrays.asList("FINISHED","SETTLED").contains(r.optString("status"))){stats.setVisibility(android.view.View.VISIBLE);stats.setText(normalXpText(r.optJSONObject("settlement")));}
 String rankCondition=terminalCondition(r,"rankCondition"),poolCondition=terminalCondition(r,"matchPoolCondition");
 if("rank".equals(r.optString("mode"))&&(NativeRoster60Pvp.rankRejection(rankCondition)||"RANK_OPPONENT_ELIGIBILITY_CHANGED".equals(rankCondition)))message.setText(NativeRoster60Pvp.rankMessage(rankCondition,null));
 if(NativeRoster60Pvp.poolRejection(poolCondition)||"MATCH_POOL_OPPONENT_CHANGED".equals(poolCondition))message.setText(NativeRoster60Pvp.poolMessage(poolCondition));
 if(presenceNotice!=null){String presence=presenceText(r);if(!presence.isEmpty()&&notice.isEmpty())presenceNotice.setText(presence);}
 if(commanderStats!=null){JSONObject receipt=r.optJSONObject("settlement"),reward=receipt==null?null:receipt.optJSONObject("commanderReward");boolean terminal=Arrays.asList("FINISHED","SETTLED").contains(r.optString("status"));if(terminal&&("normal".equals(r.optString("mode"))||reward!=null)){if(reward!=null)NativeCommander.syncReward(h,reward.optJSONObject("commander"),owner,epoch);commanderStats.setText(NativeCommander.styled(NativeCommander.rewardText(receipt)));commanderStats.setVisibility(android.view.View.VISIBLE);}else commanderStats.setVisibility(android.view.View.GONE);}
 if(lossFeedback!=null){String feedback=Arrays.asList("FINISHED","SETTLED").contains(r.optString("status"))?lossFeedbackText(r.optJSONObject("settlement")):"";lossFeedback.setText(feedback);lossFeedback.setVisibility(feedback.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);}
 }
 static String terminalCondition(JSONObject snapshot,String key){JSONObject settlement=snapshot.optJSONObject("settlement");return settlement!=null&&settlement.has(key)?settlement.optString(key):snapshot.optString(key);}
 static String presenceText(JSONObject snapshot){
  if(!"ACTIVE".equals(snapshot.optString("status")))return "";JSONObject presence=snapshot.optJSONObject("presence");if(presence==null)return "";
  String status=presence.optString("status");if("OBSERVATION_UNAVAILABLE".equals(status))return "연결 상태 확인이 지연되고 있어요.";if(!"RECONNECT_GRACE".equals(status))return "";
  Long now=serverAmount(presence,"serverNow");if(now==null)now=serverAmount(snapshot,"serverNow");if(now==null)return "재접속 유예 시간을 서버에서 확인하고 있어요.";
  ArrayList<String> parts=new ArrayList<>();String[] keys={"ownGraceUntil","opponentGraceUntil"},labels={"내 연결","상대 연결"};
  for(int i=0;i<keys.length;i++){Long until=serverAmount(presence,keys[i]);if(until!=null){long remaining=until>now?until-now:0,seconds=remaining/1000+(remaining%1000>0?1:0);parts.add(labels[i]+(remaining>0?" "+seconds+"초 남음":" 결과 확인 중"));}}
  return parts.isEmpty()?"재접속 유예 상태를 확인하고 있어요.":"재접속 유예 · "+android.text.TextUtils.join(" · ",parts);
 }
 static String lossFeedbackText(JSONObject settlement){
  JSONObject feedback=settlement==null?null:settlement.optJSONObject("lossFeedback");if(feedback==null||!"roster60-loss-feedback-v1".equals(feedback.optString("version")))return "";
  String key=feedback.optString("explanationKey"),factText;JSONObject fact=feedback.optJSONObject("fact");
  if(Arrays.asList("REFLECTED_KO","ENEMY_FIRST_KO","DIRECT_KO").contains(key)){
   Long turn=fact==null?null:serverAmount(fact,"turn"),before=fact==null?null:serverAmount(fact,"hpBefore"),after=fact==null?null:serverAmount(fact,"hpAfter"),damage=fact==null?null:serverAmount(fact,"damage");
   if(turn==null||before==null||before<=0||after==null||after!=0||damage==null||damage<=0)return "";
   String kind="REFLECTED_KO".equals(key)?"reflected-ko":"ENEMY_FIRST_KO".equals(key)?"enemy-first-ko":"direct-ko";if(!kind.equals(fact.optString("kind")))return "";long shownTurn=turn+1;
   if("REFLECTED_KO".equals(key)){String element=feedbackElement(fact.optString("element"));if(element.isEmpty())return "";factText="기록: "+shownTurn+"턴, "+element+" 기술 반사로 전투불능이 됐어요.";}
   else factText="기록: "+shownTurn+"턴, "+("ENEMY_FIRST_KO".equals(key)?"상대가 먼저 행동해":"상대 기술로")+" 전투불능이 됐어요.";
  }else switch(key){
   case "INSUFFICIENT_RECORD":factText="기록이 부족해 패배 원인을 확인하기 어려워요.";break;
   case "SURRENDER":factText="항복으로 대전이 끝났어요.";break;
   case "TIMEOUT":factText="제한 시간이 지나 대전이 끝났어요.";break;
   case "DOUBLE_TIMEOUT":factText="양쪽의 제한 시간이 지나 대전이 끝났어요.";break;
   case "MATCH_TIMEOUT":factText="대전 수락 시간이 지나 종료됐어요.";break;
   case "MATCH_DECLINED":factText="대전 수락이 취소됐어요.";break;
   case "SERVER_ABORT":factText="서버가 대전을 종료했어요.";break;
   case "DISCONNECTED":factText="연결 종료로 대전이 끝났어요.";break;
   case "TURN_INPUT_TIMEOUT":factText="행동 선택 시간이 지나 대전이 끝났어요.";break;
   case "SERVER_OBSERVATION_GAP":factText="연결 상태를 확인할 수 없어 대전이 종료됐어요.";break;
   default:return "";
  }
  String next;switch(feedback.optString("nextAttemptKey")){
   case "TRY_DIFFERENT_ELEMENT":next="다음 시도: 다른 속성 기술을 살펴보세요.";break;
   case "TRY_SWITCH_OR_HEAL_EARLIER":next="다음 시도: 더 일찍 교체하거나 회복해 보세요.";break;
   case "REVIEW_LAST_TURN":next="다음 시도: 마지막 턴의 행동을 확인해 보세요.";break;
   case "REVIEW_REPLAY":next="다음 시도: 확인할 수 있는 전투 기록을 살펴보세요.";break;
   case "CHECK_CONNECTION_AND_REPLAY":next="다음 시도: 연결 상태와 전투 기록을 확인해 보세요.";break;
   case "CHECK_TURN_DEADLINE_AND_REPLAY":next="다음 시도: 제한 시간 안에 행동을 선택해 보세요.";break;
   default:return "";
  }
  JSONObject replay=feedback.optJSONObject("replay");if(!"INSUFFICIENT_RECORD".equals(key)&&replay!=null&&Boolean.FALSE.equals(replay.opt("available")))factText+=" (재생 기록 없음)";
  return factText+"\n"+next;
 }
 static String feedbackElement(String id){String[] ids={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"},labels={"일반","불","물","번개","얼음","자연","성","어둠"};for(int i=0;i<ids.length;i++)if(ids[i].equals(id))return labels[i];return "";}
 static String normalXpText(JSONObject settlement){
  JSONObject xp=settlement==null?null:settlement.optJSONObject("normalXp");if(xp==null)return "대원 경험치 정산 상태를 다시 확인해 주세요.";
  String status=xp.optString("status");
  if("APPLIED".equals(status))return serverAmount(settlement,"xpAwarded")==null?"대원 경험치 정산 상태를 다시 확인해 주세요.":"대원 경험치 +"+serverAmount(settlement,"xpAwarded")+" · 지급 완료";
  if("CAP_CLAMPED".equals(status)){
   Long applied=serverAmount(settlement,"xpAwarded"),discarded=serverAmount(settlement,"xpDiscarded");
   return applied==null||discarded==null?"대원 경험치 정산 상태를 다시 확인해 주세요.":"대원 경험치 +"+applied+" · 적용\n상한 초과 "+discarded+" 폐기 · 저장 안 됨";
  }
  if("CAP_POLICY_PENDING".equals(status)){
   Long applied=serverAmount(settlement,"xpAwarded"),deferred=serverAmount(settlement,"xpDeferred");
   if(applied==null||deferred==null)return "대원 경험치 정산 상태를 다시 확인해 주세요.";
   return (applied>0?"대원 경험치 +"+applied+" · 지급 완료":"이전 정산 대원 경험치 지급 보류")+"\n보류 "+deferred+" · 자동 지급 아님";
  }
  if("LEGACY_POLICY_UNCONFIGURED".equals(status))return "대원 경험치 지급 보류 · 이전 전투의 보상 설정 확인 필요";
  if("SURRENDER_ZERO".equals(status))return "항복 · 대원 경험치 "+settlement.optLong("xpAwarded");
  if("DRAW_POLICY_PENDING".equals(status))return "대원 경험치 지급 보류 · 무승부 보상 규칙 확인 필요";
  if("TERMINAL_POLICY_PENDING".equals(status))return "대원 경험치 지급 보류 · 종료 사유별 보상 규칙 확인 필요";
  if("TIMEOUT_RESOLUTION_POLICY_PENDING".equals(status))return "대원 경험치 지급 보류 · 시간초과 종료의 보상 규칙 확인 필요";
  return "대원 경험치 정산 상태를 다시 확인해 주세요.";
 }
 static Long serverAmount(JSONObject value,String key){Object amount=value.opt(key);if(!(amount instanceof Number))return null;Number n=(Number)amount;return n.longValue()>=0&&n.doubleValue()==n.longValue()?n.longValue():null;}
 void schedule(){poll.removeCallbacksAndMessages(null);if(current()&&!paused)poll.postDelayed(this::resume,1500);}
 @Override void pause(){if(readyView!=null)readyView.stop();paused=true;poll.removeCallbacksAndMessages(null);super.pause();}
 @Override void lockPresentation(){super.lockPresentation();if(accept!=null){accept.setEnabled(false);decline.setEnabled(false);waitingSurrender.setEnabled(false);}}
 @Override boolean back(){if(presenting()){pause();h.home();return true;}if(!choiceKind.isEmpty()){closeChoices();return true;}if(panel!=null){panel.cancelInteraction();if(panel.showCommands())return true;}pause();h.home();return true;}
}

