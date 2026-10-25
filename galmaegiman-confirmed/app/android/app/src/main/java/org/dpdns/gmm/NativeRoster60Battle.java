package org.dpdns.gmm;
import android.content.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Real authenticated product transport. No offline fixture or client-side damage. */
class NativeRoster60Battle {
 final MainActivity h; final SharedPreferences prefs;
 static final Set<String> IN_FLIGHT=new HashSet<>();
 String owner,key,autoSettled="",settlementError=""; int epoch; LinearLayout container; TextView message,stats; Button retry,settle,returnAdventure;
 Roster60FormationView formation;LinearLayout arena;
 LinearLayout choicePanel;String choiceKind="";int itemPage;
 NativeRoster60Art art; CombatV2ReviewPanel panel; JSONObject view,adventure; boolean reading; android.app.AlertDialog selection;
 final Roster60BattleMode requestedMode;
 NativeRoster60Battle(MainActivity host){this(host,Roster60BattleMode.ALL);}
 NativeRoster60Battle(MainActivity host,Roster60BattleMode mode){h=host;requestedMode=mode;prefs=h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE);}
 boolean current(){return epoch==h.epoch&&"roster60Battle".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 String storageScope(){return "";}
 void open(){
  if(h.api.token().isEmpty()){h.login();return;}
  h.base("전투","roster60Battle");owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner+storageScope());epoch=h.epoch;
  h.page.removeAllViews();
  container=new LinearLayout(h);container.setOrientation(LinearLayout.VERTICAL);container.setBackgroundColor(NavyWorkbenchTheme.HEADER);h.page.addView(container,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout toolbar=new LinearLayout(h);Button home=h.button("‹ 홈",()->back());home.setTextSize(12);toolbar.addView(home,new LinearLayout.LayoutParams(h.dp(72),h.dp(48)));
  message=h.text("전투 확인 중…",12);message.setTextColor(NavyWorkbenchTheme.TEXT);message.setMaxLines(2);toolbar.addView(message,new LinearLayout.LayoutParams(0,h.dp(48),1));container.addView(toolbar);
  // Same field/right-hand command split as production BattleView.onMeasure().
  arena=new LinearLayout(h);arena.setOrientation(LinearLayout.HORIZONTAL);arena.setBaselineAligned(false);container.addView(arena,new LinearLayout.LayoutParams(-1,0,1));
  art=new NativeRoster60Art(h);art.setTag("roster60-battle-art");arena.addView(art,new LinearLayout.LayoutParams(0,-1,BattleView.FIELD_WIDTH_FRACTION));
  LinearLayout commandRail=new LinearLayout(h);commandRail.setOrientation(LinearLayout.VERTICAL);commandRail.setTag("roster60-command-rail");commandRail.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));commandRail.setBackground(new BattleSkin(h,"panel-command"));arena.addView(commandRail,new LinearLayout.LayoutParams(0,-1,1-BattleView.FIELD_WIDTH_FRACTION));
  stats=h.text("",12);stats.setTextColor(NavyWorkbenchTheme.TEXT);stats.setMaxLines(3);stats.setVisibility(android.view.View.GONE);commandRail.addView(stats);
  LinearLayout controls=new LinearLayout(h);controls.setOrientation(LinearLayout.HORIZONTAL);commandRail.addView(controls);
  retry=h.button("연결 재시도",()->{if(adventure!=null&&"ACTIVE".equals(adventure.optString("status")))chooseAdventureBattle();else resume();});retry.setTag("roster60-battle-retry");controls.addView(retry,new LinearLayout.LayoutParams(0,h.dp(48),1));
  settle=h.button("정산 다시 시도",()->{if(view!=null)mutate("settle",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision")));});settle.setTag("roster60-battle-settle");settle.setEnabled(false);controls.addView(settle,new LinearLayout.LayoutParams(0,h.dp(48),1));
  returnAdventure=h.button("모험 정상 복귀",()->{if(adventure!=null)mutate("adventure/return",h.obj("adventureId",adventure.optString("adventureId"),"revision",adventure.optInt("revision")));});controls.addView(returnAdventure,new LinearLayout.LayoutParams(0,h.dp(48),1));
  for(Button button:new Button[]{retry,settle,returnAdventure}){button.setVisibility(android.view.View.GONE);NavyWorkbenchTheme.tab(h,button,false);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setMaxLines(2);}
  panel=new CombatV2ReviewPanel(h,this::command,CombatV2ReviewStyle::icon,true);panel.setTag("roster60-battle-panel");commandRail.addView(panel,new LinearLayout.LayoutParams(-1,0,1));choicePanel=new LinearLayout(h);choicePanel.setOrientation(1);choicePanel.setTag("roster60-battle-choices");choicePanel.setVisibility(android.view.View.GONE);commandRail.addView(choicePanel,new LinearLayout.LayoutParams(-1,0,1));resume();
 }
 void resume(){
  if(!current()||reading||(selection!=null&&selection.isShowing()))return;
  String pending=prefs.getString(key+":pending","");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(Exception bad){message.setText("저장된 요청 확인이 필요합니다. 새 요청을 만들지 않았습니다.");}return;}
  reading=true;h.api.call("native/roster60/battle/active",null,(r,e)->{
   if(!current())return;reading=false;if(e!=null){error(e);return;}
   JSONArray battles=r.optJSONArray("battles");if(battles!=null&&battles.length()>0){read(battles.optJSONObject(0).optString("battleId"));return;}adventureResume();
  });
 }
 void adventureResume(){reading=true;h.api.call("native/roster60/adventure/active",null,(r,e)->{if(!current())return;reading=false;if(e!=null){if(e.startsWith("MENU_LOCKED")&&requestedMode!=Roster60BattleMode.ADVENTURE){options();return;}error(e);return;}JSONArray runs=r.optJSONArray("adventures");if(runs!=null&&runs.length()>0)showAdventure(runs.optJSONObject(0));else{adventure=null;returnAdventure.setVisibility(android.view.View.GONE);options();}});}
 void showAdventure(JSONObject run){
  clearFormation();adventure=run;view=null;panel.lock();panel.setVisibility(android.view.View.GONE);stats.setVisibility(android.view.View.VISIBLE);retry.setVisibility(android.view.View.VISIBLE);settle.setEnabled(false);boolean active="ACTIVE".equals(run.optString("status"));returnAdventure.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);returnAdventure.setEnabled(active&&run.isNull("activeBattleId"));retry.setText(active?"모험 계속":"홈으로");retry.setOnClickListener(v->{if(active)chooseAdventureBattle();else h.load("home");});
  message.setText(active?"모험 전투 밖 · 도망 성공은 복귀가 아닙니다.":"모험 종료");stats.setText("정상 복귀하면 모은 보상을 획득합니다.\n전멸하면 이번 모험의 미정산 보상을 잃습니다.");
 }
 void chooseAdventureBattle(){if(!current()||adventure==null||reading)return;reading=true;h.api.call("native/roster60/battle/options",null,(r,e)->{if(!current())return;reading=false;if(e!=null){error(e);return;}JSONArray all=r.optJSONArray("encounters");ArrayList<JSONObject> encounters=new ArrayList<>();if(all!=null)for(int i=0;i<all.length();i++)if("adventure".equals(all.optJSONObject(i).optString("mode")))encounters.add(all.optJSONObject(i));if(encounters.isEmpty()){error("ADVENTURE_ENCOUNTER_PENDING");return;}
  JSONArray party=adventure.optJSONArray("party");ArrayList<JSONObject> alive=new ArrayList<>();for(int i=0;i<party.length();i++)if(party.optJSONObject(i).optInt("hp")>0)alive.add(party.optJSONObject(i));String[] names=new String[alive.size()];for(int i=0;i<names.length;i++)names[i]=alive.get(i).optString("name")+" HP "+alive.get(i).optInt("hp");
  selection=new GameDialog(h).navy().setTitle("모험 출전 개체").setItems(names,(d,i)->{String[] targets=new String[encounters.size()];for(int j=0;j<targets.length;j++)targets[j]=encounters.get(j).optString("name");selection=new GameDialog(h).navy().setTitle("다음 모험 전투").setItems(targets,(dd,j)->{if(!current())return;JSONArray reserve=new JSONArray();String first=alive.get(i).optString("ownedId");for(int k=0;k<party.length();k++)if(!first.equals(party.optJSONObject(k).optString("ownedId")))reserve.put(party.optJSONObject(k).optString("ownedId"));mutate("create",h.obj("ownedId",first,"reserveIds",reserve,"encounterId",encounters.get(j).optString("id"),"adventureId",adventure.optString("adventureId")));}).setNegativeButton("취소",null).show();}).setNegativeButton("취소",null).show();
 });}
 void read(String id){reading=true;h.api.call("native/roster60/battle/read",h.obj("battleId",id),(r,e)->{if(!current())return;reading=false;if(e!=null)error(e);else bind(r);});}
 void options(){
  reading=true;h.api.call("native/roster60/battle/options",null,(r,e)->{
   if(!current())return;reading=false;if(e!=null){error(e);return;}
   JSONArray units=r.optJSONArray("units"),allEncounters=r.optJSONArray("encounters");
   JSONArray encounters=new JSONArray();if(allEncounters!=null)for(int i=0;i<allEncounters.length();i++){JSONObject encounter=allEncounters.optJSONObject(i);if(encounter!=null&&requestedMode.includes(encounter.optString("mode")))encounters.put(encounter);}
   if(units==null||units.length()==0){message.setText("편성할 개체가 없습니다. 튜토리얼에서 갈매미맨으로 시작하세요.");return;}
   if(encounters.length()==0){message.setText("선택한 모드의 전투가 아직 준비되지 않았습니다.");return;}
   message.setText("전투를 선택하고 편성을 확정하세요.");String[] targets=new String[encounters.length()];for(int j=0;j<targets.length;j++)targets[j]=encounters.optJSONObject(j).optString("name");
   selection=new GameDialog(h).navy().setTitle("전투 선택").setItems(targets,(dialog,index)->{if(!current())return;JSONObject encounter=encounters.optJSONObject(index);showFormation(units,encounter);}).setNegativeButton("취소",null).show();
  });
 }
 void showFormation(JSONArray units,JSONObject encounter){
  if(formation!=null)container.removeView(formation);arena.setVisibility(android.view.View.GONE);
  formation=new Roster60FormationView(h,units,ids->{if(!current())return;if(!prefs.getString(key+":pending","").isEmpty()){resume();return;}if("adventure".equals(encounter.optString("mode")))mutate("adventure/start",h.obj("ownedIds",ids));else{JSONArray reserves=new JSONArray();for(int i=1;i<ids.length();i++)reserves.put(ids.optString(i));mutate("create",h.obj("ownedId",ids.optString(0),"reserveIds",reserves,"encounterId",encounter.optString("id")));}},()->{clearFormation();options();});container.addView(formation,new LinearLayout.LayoutParams(-1,0,1));
 }
 void clearFormation(){if(formation!=null){container.removeView(formation);formation=null;}arena.setVisibility(android.view.View.VISIBLE);}
 void chooseReserves(JSONArray units,String first,String encounter,boolean isAdventure){
  ArrayList<JSONObject> candidates=new ArrayList<>();for(int i=0;i<units.length();i++)if(!first.equals(units.optJSONObject(i).optString("ownedId")))candidates.add(units.optJSONObject(i));
  String[] names=new String[candidates.size()];boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++)names[i]=candidates.get(i).optString("name");
  selection=new GameDialog(h).navy().setTitle("예비 최대 5마리 · 출전 1마리씩")
   .setMultiChoiceItems(names,checked,(d,n,on)->checked[n]=on).setPositiveButton("전투 시작",(d,n)->{if(!current())return;JSONArray ids=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])ids.put(candidates.get(i).optString("ownedId"));if(ids.length()>5){message.setText("예비는 최대 5마리입니다.");return;}if(isAdventure){JSONArray party=new JSONArray().put(first);for(int j=0;j<ids.length();j++)party.put(ids.optString(j));mutate("adventure/start",h.obj("ownedIds",party));}else mutate("create",h.obj("ownedId",first,"encounterId",encounter,"reserveIds",ids));}).setNegativeButton("취소",null).show();
 }
 void action(JSONObject a){if(view!=null)mutate("action",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision"),"action",a));}
 void cancelSelection(){if(current()&&view!=null&&prefs.getString(key+":pending","").isEmpty())bind(view);}
 void unlockOnDismiss(){final android.app.AlertDialog dialog=selection;if(dialog!=null)dialog.setOnDismissListener(d->{if(h.visibleDialog==dialog)h.visibleDialog=null;if(selection!=dialog)return;selection=null;if(current()&&view!=null&&prefs.getString(key+":pending","").isEmpty())bind(view);});}
 void chooseSwitch(){choiceKind="switch";renderChoices();}
 void chooseItem(){choiceKind="item";itemPage=0;renderChoices();}
 void closeChoices(){choiceKind="";choicePanel.setVisibility(android.view.View.GONE);panel.setVisibility(android.view.View.VISIBLE);cancelSelection();}
 void renderChoices(){
 if(view==null||choiceKind.isEmpty())return;panel.cancelInteraction();panel.setVisibility(android.view.View.GONE);choicePanel.setVisibility(android.view.View.VISIBLE);choicePanel.removeAllViews();
 JSONObject state=view.optJSONObject("state");ArrayList<JSONObject> entries=new ArrayList<>();boolean switching="switch".equals(choiceKind);
 if(switching){entries.add(state.optJSONArray("fighters").optJSONObject(0));JSONArray reserves=state.optJSONArray("reserves");if(reserves!=null)for(int i=0;i<reserves.length();i++)entries.add(reserves.optJSONObject(i));}
 else{JSONArray inventory=state.optJSONArray("inventory");JSONObject defs=view.optJSONObject("itemDefinitions");if(inventory!=null&&defs!=null)for(int i=0;i<inventory.length();i++){JSONObject item=inventory.optJSONObject(i);if(item.optInt("count")>0&&defs.has(item.optString("itemId")))entries.add(item);}}
 int pages=Math.max(1,(entries.size()+5)/6);itemPage=Math.min(itemPage,pages-1);boolean available=prefs.getString(key+":pending","").isEmpty()&&!view.optBoolean("waitingForOpponent");
 for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(h);choicePanel.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<3;col++){int index=itemPage*6+row*3+col;
 if(index>=entries.size()){line.addView(new android.view.View(h),new LinearLayout.LayoutParams(0,-1,1));continue;}JSONObject item=entries.get(index);
 if(switching){boolean active=index==0,alive=item.optInt("hp")>0;String caption=item.optString("name")+(active?" · 출전":!alive?" · 전투불능":"")+"\nHP "+item.optInt("hp")+" · MP "+item.optInt("mp");Roster60MemberTile tile=new Roster60MemberTile(h,item,caption,!active&&alive&&available,()->{choiceKind="";choicePanel.setVisibility(android.view.View.GONE);action(h.obj("kind","switch","targetOwnedId",item.optString("ownedId")));});tile.action.setTag("battle-switch-"+item.optString("ownedId"));line.addView(tile,new LinearLayout.LayoutParams(0,-1,1));}
 else{String id=item.optString("itemId");JSONObject def=view.optJSONObject("itemDefinitions").optJSONObject(id);Button button=h.button(def.optString("name")+" ×"+item.optInt("count"),()->{choiceKind="";choicePanel.setVisibility(android.view.View.GONE);if(def.optInt("revive")>0)chooseRevive(id);else action(h.obj("kind","item","itemId",id));});button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setEnabled(available);button.setBackground(new BattleSkin(h,"button-item"));line.addView(button,new LinearLayout.LayoutParams(0,-1,1));}
 }}
 LinearLayout footer=new LinearLayout(h);choicePanel.addView(footer,new LinearLayout.LayoutParams(-1,h.dp(48)));
 if(pages>1){Button prev=h.button("‹",()->{itemPage--;renderChoices();}),next=h.button("›",()->{itemPage++;renderChoices();});prev.setEnabled(itemPage>0);next.setEnabled(itemPage<pages-1);footer.addView(prev,new LinearLayout.LayoutParams(0,-1,1));footer.addView(next,new LinearLayout.LayoutParams(0,-1,1));}
 Button back=h.button("돌아가기",this::closeChoices);back.setTag("battle-choices-back");footer.addView(back,new LinearLayout.LayoutParams(0,-1,1));
 }
 void chooseRevive(String item){
  ArrayList<JSONObject> fallen=new ArrayList<>();JSONObject state=view.optJSONObject("state"),active=state.optJSONArray("fighters").optJSONObject(0);if(active.optInt("hp")==0)fallen.add(active);JSONArray reserves=state.optJSONArray("reserves");if(reserves!=null)for(int i=0;i<reserves.length();i++)if(reserves.optJSONObject(i).optInt("hp")==0)fallen.add(reserves.optJSONObject(i));
  String[] names=new String[fallen.size()];for(int i=0;i<names.length;i++)names[i]=fallen.get(i).optString("name");
  selection=new GameDialog(h).navy().setTitle("다시 일으킬 개체").setItems(names,(d,i)->{if(current())action(h.obj("kind","item","itemId",item,"targetOwnedId",fallen.get(i).optString("ownedId")));}).setNegativeButton("취소",(d,w)->cancelSelection()).show();unlockOnDismiss();
 }
 void command(JSONObject command){
  if(view==null)return;String kind=command.optString("kind");
  if("switch".equals(kind)){chooseSwitch();return;}if("item".equals(kind)){chooseItem();return;}
  if("escape".equals(kind)){boolean exploring="adventure".equals(view.optJSONObject("state").optString("mode"));selection=new GameDialog(h).navy().setTitle(exploring?"모험 도망":"항복").setMessage(exploring?"성공률 70%. 실패하면 상대만 행동합니다. 성공해도 보상은 정상 복귀 전까지 미정산입니다.":"즉시 패배하며 경험치는 0입니다.").setPositiveButton(exploring?"도망 시도":"항복",(d,w)->{if(current())action(h.obj("kind","escape"));}).setNegativeButton("취소",(d,w)->cancelSelection()).show();final android.app.AlertDialog dialog=selection;dialog.getButton(-2).setOnClickListener(v->{cancelSelection();dialog.dismiss();});dialog.getButton(-1).setOnClickListener(v->{dialog.getButton(-1).setEnabled(false);if(current())action(h.obj("kind","escape"));dialog.dismiss();});unlockOnDismiss();return;}
  JSONObject action=h.obj("kind",command.optString("kind"));try{if(command.has("skillId"))action.put("skillId",command.getString("skillId"));}catch(Exception impossible){throw new IllegalStateException(impossible);}
  mutate("action",h.obj("battleId",command.optString("battleId"),"revision",command.optInt("expectedRevision"),"action",action));
 }
 void mutate(String op,JSONObject body){
  if(!current()||!prefs.getString(key+":pending","").isEmpty())return;
  if("settle".equals(op))settlementError="";
  try{body.put("requestId",UUID.randomUUID().toString());JSONObject intent=h.obj("operation",op,"body",body);
   if(!prefs.edit().putString(key+":pending",intent.toString()).commit()){error("요청 저장 실패");return;}send(intent);
  }catch(Exception bad){error("요청 생성 실패");}
 }
 void send(JSONObject intent){
  JSONObject body=intent.optJSONObject("body");if(body==null)return;String id=body.optString("requestId"),op=intent.optString("operation");
  if(!Arrays.asList("create","action","settle","adventure/start","adventure/return").contains(op)||id.isEmpty()){error("저장된 요청이 올바르지 않습니다.");return;}
  if(!IN_FLIGHT.add(id)){message.setText("서버 응답 확인 중…");return;}panel.lock();settle.setEnabled(false);
  h.api.call("native/roster60/"+(op.startsWith("adventure/")?op:"battle/"+op),body,(r,e)->{
   IN_FLIGHT.remove(id);if(!owner.equals(h.api.token()))return;
   boolean rejected=(e!=null&&e.startsWith("MENU_LOCKED"))||Arrays.asList("ADVENTURE_REQUIRED","ADVENTURE_NOT_FOUND","ADVENTURE_BATTLE_ACTIVE","ADVENTURE_STATE_CHANGED","RETURN_ONLY_OUTSIDE_BATTLE","CATALOG_CHANGED","UNIT_STATE_CHANGED","INVALID_PARTY","INVALID_SWITCH","INVALID_ITEM","INVALID_ITEM_TARGET","NO_STATUS_TO_CLEANSE","HP_FULL","ITEM_UNAVAILABLE","ACTIVE_UNIT_FAINTED","FORCED_SWITCH_REQUIRED","FORCED_SWITCH_POLICY_PENDING","ESCAPE_POLICY_PENDING","BATTLE_POLICY_PENDING","BATTLE_POLICY_INVALID","BATTLE_GROWTH_PENDING","BATTLE_SKILL_POLICY_PENDING","FIVE_STAT_REBIRTH_POLICY_PENDING","ROSTER_CATALOG_PENDING","FIVE_STAT_CATALOG_REQUIRED","FIVE_STAT_CATALOG_INVALID","FIVE_STATS_REQUIRED","INVALID_FIVE_STATS","BASE_MP_MISMATCH","ROSTER_CATALOG_INVALID","INSUFFICIENT_MP","STALE_REVISION","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","ENCOUNTER_NOT_ALLOWED","BATTLE_NOT_FOUND","BATTLE_FINISHED","BATTLE_NOT_FINISHED","INVALID_REQUEST","REQUEST_ID_REUSED","CAP_XP_POLICY_REQUIRED").contains(e);
   if(e==null||rejected)prefs.edit().remove(key+":pending").commit();
   if(!current())return;
   if(e!=null){if("settle".equals(op))settlementError=e;error(e);if(rejected&&view!=null&&!"settle".equals(op))read(view.optString("battleId"));return;}
   // Receipt may be older than current state after reconnect: read authoritative latest.
   if(op.startsWith("adventure/")){showAdventure(r);return;}
   bind(r);read(r.optString("battleId"));
  });
 }
 void error(String e){if(formation!=null)formation.unlock();if(view!=null&&"FINISHED".equals(view.optString("status"))){settle.setVisibility(android.view.View.VISIBLE);settle.setEnabled(true);}retry.setVisibility(android.view.View.VISIBLE);retry.setText("연결 재시도");retry.setOnClickListener(v->resume());message.setText(e.contains("PENDING")||e.contains("CATALOG")?h.getString(R.string.roster60_battle_pending)+"\n"+e:e);}
 void bind(JSONObject r){
  try{
   JSONObject s=r.getJSONObject("state");clearFormation();art.bind(s);if(!"five-battle-v1".equals(s.getString("version")))throw new JSONException("version");
   JSONArray fighters=s.getJSONArray("fighters");JSONObject self=fighters.getJSONObject(0),foe=fighters.getJSONObject(1);
   stats.setText("");stats.setVisibility(android.view.View.GONE);boolean active="ACTIVE".equals(r.getString("status"));JSONArray commands=new JSONArray(),skills=new JSONArray();String[] ids={"attack","skills","focus","item","switch","escape"},labels={"공격","기술","집중","아이템","교체","도망치기"};
   for(int i=0;i<6;i++){String reason=i<3?"":r.optJSONObject("disabledCommands")==null?"정책 준비 중":r.optJSONObject("disabledCommands").optString(ids[i]);boolean enabled=active&&reason.isEmpty();if(i<3&&self.optInt("hp")==0){enabled=false;reason="전투불가";}commands.put(h.obj("kind",ids[i],"label",labels[i],"enabled",enabled,"reason",reason));}
   JSONArray raw=self.getJSONArray("skills");for(int i=0;i<raw.length();i++){JSONObject sk=new JSONObject(raw.getJSONObject(i).toString());boolean enabled=active&&self.optInt("hp")>0&&self.getInt("mp")>=sk.getInt("mpCost");sk.put("enabled",enabled);sk.put("reason",active?(enabled?"":"MP 부족"):"전투 종료");sk.put("description","위력 "+sk.optDouble("power"));skills.put(sk);}
   JSONObject displaySelf=new JSONObject(self.toString());displaySelf.put("maxHp",self.getJSONObject("stats").getInt("hp"));displaySelf.put("maxMp",self.getJSONObject("stats").getInt("mp"));JSONObject displayFoe=new JSONObject(foe.toString());displayFoe.put("maxHp",foe.getJSONObject("stats").getInt("hp"));displayFoe.put("maxMp",foe.getJSONObject("stats").getInt("mp"));
   panel.bind(h.obj("epoch","product-five-battle-v1","battleId",r.getString("battleId"),"revision",r.getInt("revision"),"self",displaySelf,"foe",displayFoe,"commands",commands,"skills",skills,"events",new JSONArray()));
   adventure=null;returnAdventure.setVisibility(android.view.View.GONE);retry.setVisibility(android.view.View.GONE);settle.setVisibility(android.view.View.GONE);view=r;
   if(!active){choiceKind="";choicePanel.setVisibility(android.view.View.GONE);}
   panel.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);
   if(active&&!choiceKind.isEmpty())renderChoices();
   message.setText(active?"턴 "+s.getInt("turn")+" · 행동을 선택하세요":resultLabel(s.optString("outcome")));
   if(active&&self.optInt("hp")==0)message.setText("전투불가 · 생존한 예비 개체를 선택하세요");
   if("FINISHED".equals(r.optString("status"))&&!settlementError.isEmpty()){error(settlementError);}
   else if("FINISHED".equals(r.optString("status"))){message.setText(resultLabel(s.optString("outcome"))+" · 결과 정산 중…");
    if(!r.optString("battleId").equals(autoSettled)){autoSettled=r.optString("battleId");final String id=autoSettled;h.page.post(()->{if(current()&&view!=null&&id.equals(view.optString("battleId"))&&"FINISHED".equals(view.optString("status")))mutate("settle",h.obj("battleId",id,"revision",view.optInt("revision")));});}
   }else if("SETTLED".equals(r.optString("status"))){stats.setVisibility(android.view.View.VISIBLE);JSONObject receipt=r.optJSONObject("settlement");boolean exploring="adventure".equals(s.optString("mode"));stats.setText(exploring?"LOST".equals(receipt==null?"":receipt.optString("adventureStatus"))?"전멸로 이번 모험의 미정산 보상을 잃었습니다.":"모험 보상은 누적 보관됩니다. 정상 복귀 시 획득합니다.":"경험치 +"+(receipt==null?0:receipt.optInt("xpAwarded"))+" · 결과 저장 완료");retry.setVisibility(android.view.View.VISIBLE);retry.setText(exploring?"모험 확인":"홈으로");retry.setOnClickListener(v->{if(exploring)adventureResume();else h.load("home");});}

  }catch(Exception bad){view=null;panel.lock();settle.setEnabled(false);message.setText("서버 전투 데이터 형식을 확인할 수 없습니다.");}
 }
 static String resultLabel(String outcome){return "win".equals(outcome)?"승리":"loss".equals(outcome)?"패배":"escaped".equals(outcome)?"도망 성공":"전투 종료";}
 boolean back(){if(!choiceKind.isEmpty()){closeChoices();return true;}if(panel!=null){panel.cancelInteraction();if(panel.showCommands())return true;}h.load("home");return true;}
 void pause(){if(panel!=null)panel.cancelInteraction();if(selection!=null){selection.dismiss();selection=null;}}
}
