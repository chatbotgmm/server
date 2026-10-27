package org.dpdns.gmm;
import android.content.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Real authenticated product transport. No offline fixture or client-side damage. */
class NativeRoster60Battle {
 final MainActivity h; final SharedPreferences prefs;
 static final Set<String> IN_FLIGHT=new HashSet<>();
 String owner,key,autoSettled="",settlementError="",rejectionNotice=""; int epoch; LinearLayout container; TextView message,stats; Button retry,settle,returnAdventure,home;
 Roster60FormationView formation;LinearLayout arena;
 android.app.AlertDialog conditionDialog;LinearLayout opponentDetails;TextView opponentSummary;final TextView[] opponentReactions=new TextView[8];boolean opponentInfoChanging;JSONObject opponentProjection;
 LinearLayout choicePanel;String choiceKind="",switchTarget="",targetItem="";int itemPage;
 NativeRoster60Art art; NativeRoster60Playback playback; CombatV2ReviewPanel panel; LinearLayout toolbar; TextView hold;
 // 휴대폰 가로(약 760×300dp)=1. 큰 화면에서는 글자·아이콘·버튼 높이를 같은 비율로 키운다(Roster60Scale).
 float scale=1; JSONObject view,adventure; boolean reading;boolean presentationPaused; android.app.AlertDialog selection;
 final Roster60BattleMode requestedMode;
 NativeRoster60Battle(MainActivity host){this(host,Roster60BattleMode.ALL);}
 NativeRoster60Battle(MainActivity host,Roster60BattleMode mode){h=host;requestedMode=mode;prefs=h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE);}
 boolean current(){return epoch==h.epoch&&"roster60Battle".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 String storageScope(){return "";}
 void open(){
  if(h.api.token().isEmpty()){h.login();return;}
  h.base("전투","roster60Battle");owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner+storageScope());epoch=h.epoch;
  h.page.removeAllViews();
  Roster60Scale.Column column=new Roster60Scale.Column(h,760,300);column.changed=this::rescale;container=column;container.setBackgroundColor(NavyWorkbenchTheme.HEADER);h.page.addView(container,new LinearLayout.LayoutParams(-1,0,1));
  FrameLayout top=new FrameLayout(h);toolbar=new LinearLayout(h);toolbar.setBaselineAligned(false);top.addView(toolbar,new FrameLayout.LayoutParams(-1,-1));home=h.button("‹ 홈",()->back());home.setTextSize(12);toolbar.addView(home,new LinearLayout.LayoutParams(h.dp(72),h.dp(48)));
  message=h.text("전투 확인 중…",12);message.setTextColor(NavyWorkbenchTheme.TEXT);message.setMaxLines(2);toolbar.addView(message,new LinearLayout.LayoutParams(0,h.dp(48),1));container.addView(top,new LinearLayout.LayoutParams(-1,h.dp(48)));
  // Same field/right-hand command split as production BattleView.onMeasure().
  arena=new LinearLayout(h);arena.setOrientation(LinearLayout.HORIZONTAL);arena.setBaselineAligned(false);container.addView(arena,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout stage=new LinearLayout(h);stage.setOrientation(LinearLayout.VERTICAL);stage.setTag("roster60-battle-stage");arena.addView(stage,new LinearLayout.LayoutParams(0,-1,.625f));
  art=new NativeRoster60Art(h);art.setTag("roster60-battle-art");stage.addView(art,new LinearLayout.LayoutParams(-1,0,1));
  createOpponentPanel(stage);

  // 기술 설명(누르는 동안): 전장 위가 아니라 상단 안내줄 자리에 띄운다. 얼굴·HP/MP 패널·명령 칸을 가리지 않고,
  // 긴 설명은 칸 안에서 글자를 줄이고 그래도 넘치면 말줄임으로 끝낸다(화면 밖 잘림 없음).
  hold=new TextView(h){@Override protected void onTextChanged(CharSequence t,int a,int b,int c){super.onTextChanged(t,a,b,c);if(getWidth()>0)Roster60Scale.fit(this,12*scale,9);}};hold.setTag("battle-hold-description");CombatV2ReviewStyle.text(hold);hold.setTextColor(JournalTheme.INK);hold.setTextSize(12);hold.setGravity(android.view.Gravity.CENTER_VERTICAL);hold.setPadding(h.dp(12),h.dp(6),h.dp(12),h.dp(6));hold.setBackground(new BattleSkin(h,"panel-hp"));hold.setVisibility(android.view.View.INVISIBLE);
  FrameLayout.LayoutParams holdBox=new FrameLayout.LayoutParams(-1,-1);holdBox.leftMargin=h.dp(74);top.addView(hold,holdBox);
  // 큰 화면에서는 명령 칸이 남는 높이 전체로 늘지 않게 높이를 너비×1.3까지만 쓰고 가운데에 둔다(휴대폰 가로에서는 제한에 닿지 않음).
  LinearLayout commandRail=new Roster60Scale.Capped(h,1.3f);commandRail.setOrientation(LinearLayout.VERTICAL);commandRail.setTag("roster60-command-rail");commandRail.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));commandRail.setBackground(new BattleSkin(h,"panel-command"));LinearLayout.LayoutParams railBox=new LinearLayout.LayoutParams(0,-1,.375f);railBox.gravity=android.view.Gravity.CENTER_VERTICAL;arena.addView(commandRail,railBox);
  stats=h.text("",12);stats.setTextColor(NavyWorkbenchTheme.TEXT);stats.setMaxLines(3);stats.setVisibility(android.view.View.GONE);commandRail.addView(stats);
  LinearLayout controls=new LinearLayout(h);controls.setOrientation(LinearLayout.HORIZONTAL);commandRail.addView(controls);
  retry=h.button("연결 재시도",()->{if(adventure!=null&&"ACTIVE".equals(adventure.optString("status")))chooseAdventureBattle();else resume();});retry.setTag("roster60-battle-retry");controls.addView(retry,new LinearLayout.LayoutParams(0,h.dp(48),1));
  settle=h.button("정산 다시 시도",()->{if(view!=null)mutate("settle",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision")));});settle.setTag("roster60-battle-settle");settle.setEnabled(false);controls.addView(settle,new LinearLayout.LayoutParams(0,h.dp(48),1));
  returnAdventure=h.button("모험 정상 복귀",()->{if(adventure!=null)mutate("adventure/return",h.obj("adventureId",adventure.optString("adventureId"),"revision",adventure.optInt("revision")));});controls.addView(returnAdventure,new LinearLayout.LayoutParams(0,h.dp(48),1));
  for(Button button:new Button[]{retry,settle,returnAdventure}){button.setVisibility(android.view.View.GONE);NavyWorkbenchTheme.tab(h,button,false);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setMaxLines(2);}
  panel=new CombatV2ReviewPanel(h,this::command,CombatV2ReviewStyle::icon,true);panel.setTag("roster60-battle-panel");panel.setHoldOverlay(hold);commandRail.addView(panel,new LinearLayout.LayoutParams(-1,0,1));choicePanel=new LinearLayout(h);choicePanel.setOrientation(1);choicePanel.setTag("roster60-battle-choices");choicePanel.setVisibility(android.view.View.GONE);commandRail.addView(choicePanel,new LinearLayout.LayoutParams(-1,0,1));resume();
 }
 void rescale(float s){
  scale=s;int bar=h.dp(48*s);container.getChildAt(0).getLayoutParams().height=bar;
  home.getLayoutParams().width=h.dp(72*s);home.getLayoutParams().height=bar;home.setTextSize(12*s);message.getLayoutParams().height=bar;message.setTextSize(12*s);
  ((FrameLayout.LayoutParams)hold.getLayoutParams()).leftMargin=h.dp(74*s);hold.setTextSize(12*s);
  art.setScale(s);scaleOpponentPanel();panel.setScale(s);if(choicePanel.getVisibility()==android.view.View.VISIBLE)renderChoices();
 }
 void resume(){
  presentationPaused=false;
  if(!current()||reading||(selection!=null&&selection.isShowing()))return;
  String pending=prefs.getString(key+":pending","");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(Exception bad){message.setText("저장된 요청 확인이 필요합니다. 새 요청을 만들지 않았습니다.");}return;}
  reading=true;h.api.call("native/roster60/battle/active",null,(r,e)->{
   if(!current())return;reading=false;if(e!=null){error(e);return;}
   JSONArray battles=r.optJSONArray("battles");if(battles!=null&&battles.length()>0){JSONObject latest=battles.optJSONObject(0);if(completeActiveSnapshot(latest))bind(latest);else if(latest!=null&&!latest.optString("battleId").isEmpty())read(latest.optString("battleId"));else error("서버 전투 데이터 형식을 확인할 수 없습니다.");return;}adventureResume();
  });
 }
 // /active returns the same authorized, masked DTO as /read. Reuse only a
 // complete current snapshot; older/partial compatibility responses still read.
 static boolean completeActiveSnapshot(JSONObject snapshot){
  if(snapshot==null||snapshot.optString("battleId").isEmpty()||!snapshot.has("revision")
      ||snapshot.optInt("revision",-1)<0||!("ACTIVE".equals(snapshot.optString("status"))||"FINISHED".equals(snapshot.optString("status"))))return false;
  JSONObject state=snapshot.optJSONObject("state");JSONArray fighters=state==null?null:state.optJSONArray("fighters");
  if(state==null||!"five-battle-v1".equals(state.optString("version"))||fighters==null||fighters.length()!=2)return false;
  for(int i=0;i<2;i++)if(fighters.optJSONObject(i)==null||fighters.optJSONObject(i).optJSONObject("stats")==null)return false;
  return fighters.optJSONObject(0).optJSONArray("skills")!=null;
 }
 void adventureResume(){reading=true;h.api.call("native/roster60/adventure/active",null,(r,e)->{if(!current())return;reading=false;if(e!=null){if(e.startsWith("MENU_LOCKED")&&requestedMode!=Roster60BattleMode.ADVENTURE){options();return;}error(e);return;}JSONArray runs=r.optJSONArray("adventures");if(runs!=null&&runs.length()>0)showAdventure(runs.optJSONObject(0));else{adventure=null;returnAdventure.setVisibility(android.view.View.GONE);options();}});}
 void showAdventure(JSONObject run){
  clearFormation();adventure=run;view=null;closeOpponentInfo();panel.lock();panel.setVisibility(android.view.View.GONE);stats.setVisibility(android.view.View.VISIBLE);retry.setVisibility(android.view.View.VISIBLE);settle.setEnabled(false);boolean active="ACTIVE".equals(run.optString("status"));returnAdventure.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);returnAdventure.setEnabled(active&&run.isNull("activeBattleId"));retry.setText(active?"모험 계속":"홈으로");retry.setOnClickListener(v->{if(active)chooseAdventureBattle();else h.load("home");});
  message.setText(active?"모험 전투 밖 · 도망 성공은 복귀가 아닙니다.":"모험 종료");JSONObject rewards=run.optJSONObject("pendingRewards"),xp=rewards==null?null:rewards.optJSONObject("xp");long totalXp=0;if(xp!=null){java.util.Iterator<String> ids=xp.keys();while(ids.hasNext())totalXp+=xp.optLong(ids.next());}stats.setText("누적 경험치 합계 "+totalXp+" · 정상 복귀 시 보상 획득\n전멸하면 이번 모험의 미정산 보상을 잃습니다.");
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
  if(formation!=null)container.removeView(formation);panel.cancelInteraction();arena.setVisibility(android.view.View.GONE);home.setText("‹ 뒤로");message.setText("전투 편성 · "+("adventure".equals(encounter.optString("mode"))?"모험":"스토리")+" · 출전 1 + 예비 5");
  formation=new Roster60FormationView(h,units,ids->{if(!current())return;if(!prefs.getString(key+":pending","").isEmpty()){resume();return;}if("adventure".equals(encounter.optString("mode")))mutate("adventure/start",h.obj("ownedIds",ids));else{JSONArray reserves=new JSONArray();for(int i=1;i<ids.length();i++)reserves.put(ids.optString(i));mutate("create",h.obj("ownedId",ids.optString(0),"reserveIds",reserves,"encounterId",encounter.optString("id")));}},()->{clearFormation();options();});container.addView(formation,new LinearLayout.LayoutParams(-1,0,1));
 }
 void clearFormation(){if(formation!=null){container.removeView(formation);formation=null;}arena.setVisibility(android.view.View.VISIBLE);if(home!=null)home.setText("‹ 홈");}
 void chooseReserves(JSONArray units,String first,String encounter,boolean isAdventure){
  ArrayList<JSONObject> candidates=new ArrayList<>();for(int i=0;i<units.length();i++)if(!first.equals(units.optJSONObject(i).optString("ownedId")))candidates.add(units.optJSONObject(i));
  String[] names=new String[candidates.size()];boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++)names[i]=candidates.get(i).optString("name");
  selection=new GameDialog(h).navy().setTitle("예비 최대 5마리 · 출전 1마리씩")
   .setMultiChoiceItems(names,checked,(d,n,on)->checked[n]=on).setPositiveButton("전투 시작",(d,n)->{if(!current())return;JSONArray ids=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])ids.put(candidates.get(i).optString("ownedId"));if(ids.length()>5){message.setText("예비는 최대 5마리입니다.");return;}if(isAdventure){JSONArray party=new JSONArray().put(first);for(int j=0;j<ids.length();j++)party.put(ids.optString(j));mutate("adventure/start",h.obj("ownedIds",party));}else mutate("create",h.obj("ownedId",first,"encounterId",encounter,"reserveIds",ids));}).setNegativeButton("취소",null).show();
 }
 void action(JSONObject a){if(!presenting()&&view!=null)mutate("action",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision"),"action",a));}
 void cancelSelection(){if(current()&&view!=null&&prefs.getString(key+":pending","").isEmpty())bind(view);}
 void unlockOnDismiss(){final android.app.AlertDialog dialog=selection;if(dialog!=null)dialog.setOnDismissListener(d->{if(h.visibleDialog==dialog)h.visibleDialog=null;if(selection!=dialog)return;selection=null;if(current()&&view!=null&&prefs.getString(key+":pending","").isEmpty())bind(view);});}
 void chooseSwitch(){switchTarget="";choiceKind="switch";renderChoices();}
 void chooseItem(){choiceKind="item";itemPage=0;renderChoices();}
 void closeChoices(){switchTarget="";choiceKind="";choicePanel.setVisibility(android.view.View.GONE);panel.setVisibility(android.view.View.VISIBLE);cancelSelection();}
 void renderChoices(){
 if(view==null||choiceKind.isEmpty())return;panel.cancelInteraction();panel.setVisibility(android.view.View.GONE);choicePanel.setVisibility(android.view.View.VISIBLE);choicePanel.removeAllViews();
 JSONObject state=view.optJSONObject("state");ArrayList<JSONObject> entries=new ArrayList<>();boolean reviving="revive".equals(choiceKind),switching="switch".equals(choiceKind)||reviving;
 if(switching){entries.add(state.optJSONArray("fighters").optJSONObject(0));JSONArray reserves=state.optJSONArray("reserves");if(reserves!=null)for(int i=0;i<reserves.length();i++)entries.add(reserves.optJSONObject(i));}
 else{JSONArray inventory=state.optJSONArray("inventory");JSONObject defs=view.optJSONObject("itemDefinitions");if(inventory!=null&&defs!=null)for(int i=0;i<inventory.length();i++){JSONObject item=inventory.optJSONObject(i);if(item.optInt("count")>0&&defs.has(item.optString("itemId")))entries.add(item);}}
 TextView heading=h.text(reviving?"회복할 대원 선택 · 전투불능만":switching?"대원 교체 · 후보 선택":"아이템 선택",12);CombatV2ReviewStyle.text(heading);heading.setTextSize(13*scale);heading.setTextColor(NavyWorkbenchTheme.TEXT);heading.setSingleLine(true);heading.setPadding(h.dp(4*scale),0,h.dp(4*scale),h.dp(3*scale));heading.setTag("battle-choices-heading");choicePanel.addView(heading,new LinearLayout.LayoutParams(-1,-2));
 int pages=Math.max(1,(entries.size()+5)/6);itemPage=Math.min(itemPage,pages-1);boolean available=prefs.getString(key+":pending","").isEmpty()&&!view.optBoolean("waitingForOpponent");
 for(int row=0;row<3;row++){LinearLayout line=new LinearLayout(h);choicePanel.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<2;col++){int index=itemPage*6+row*2+col;
 if(index>=entries.size()){line.addView(new android.view.View(h),new LinearLayout.LayoutParams(0,-1,1));continue;}JSONObject item=entries.get(index);
 if(switching){boolean active=index==0,alive=item.optInt("hp")>0;Roster60SwitchTile tile=new Roster60SwitchTile(h,item,active,(reviving?!alive:!active&&alive)&&available,item.optString("ownedId").equals(switchTarget),()->{switchTarget=item.optString("ownedId");renderChoices();},scale);tile.action.setTag("battle-switch-"+item.optString("ownedId"));line.addView(tile,new LinearLayout.LayoutParams(0,-1,1));}
 else{String id=item.optString("itemId");JSONObject def=view.optJSONObject("itemDefinitions").optJSONObject(id);Button button=h.button(def.optString("name")+" ×"+item.optInt("count"),()->{choiceKind="";choicePanel.setVisibility(android.view.View.GONE);if(def.optInt("revive")>0)chooseRevive(id);else action(h.obj("kind","item","itemId",id));});button.setTextSize(12*scale);button.setMinWidth(0);button.setMinimumWidth(0);button.setEnabled(available);button.setBackground(new BattleSkin(h,"button-item"));line.addView(button,new LinearLayout.LayoutParams(0,-1,1));}
 }}
 LinearLayout footer=new LinearLayout(h);LinearLayout.LayoutParams footerBox=new LinearLayout.LayoutParams(-1,h.dp(48*scale));footerBox.topMargin=h.dp(3*scale);choicePanel.addView(footer,footerBox);
 if(pages>1){Button prev=h.button("‹",()->{itemPage--;renderChoices();}),next=h.button("›",()->{itemPage++;renderChoices();});prev.setEnabled(itemPage>0);next.setEnabled(itemPage<pages-1);footer.addView(prev,new LinearLayout.LayoutParams(0,-1,1));footer.addView(next,new LinearLayout.LayoutParams(0,-1,1));}
 // 취소는 남색 보조 행동(교체 확정과 같은 글자 크기·같은 터치 높이), 확정만 크림색 주행동. 취소는 서버 요청 없이 닫기만 한다.
 Button back=h.button("취소",this::closeChoices);back.setTag("battle-choices-back");back.setBackground(new BattleSkin(h,"button-switch"));CombatV2ReviewStyle.text(back);back.setTextColor(0xfffff8e8);back.setTextSize(12*scale);back.setMinWidth(0);back.setMinimumWidth(0);back.setPadding(h.dp(4),0,h.dp(4),0);footer.addView(back,new LinearLayout.LayoutParams(0,-1,1));
 if(switching){JSONObject target=null;for(JSONObject candidate:entries)if(candidate.optString("ownedId").equals(switchTarget)&&(reviving?candidate.optInt("hp")==0:candidate.optInt("hp")>0&&candidate!=entries.get(0)))target=candidate;final JSONObject chosen=target;
 Button confirm=h.button(chosen==null?"대원 선택":chosen.optString("name")+(reviving?" 회복":" 교체"),()->{if(chosen==null||!current())return;switchTarget="";choiceKind="";choicePanel.setVisibility(android.view.View.GONE);action(reviving?h.obj("kind","item","itemId",targetItem,"targetOwnedId",chosen.optString("ownedId")):h.obj("kind","switch","targetOwnedId",chosen.optString("ownedId")));});confirm.setTag("battle-switch-confirm");confirm.setEnabled(chosen!=null&&available);confirm.setBackground(new BattleSkin(h,"button-surrender"));CombatV2ReviewStyle.text(confirm);confirm.setTextColor(chosen!=null&&available?JournalTheme.INK:JournalTheme.MUTED);confirm.setTextSize(12*scale);confirm.setMaxLines(1);confirm.setEllipsize(android.text.TextUtils.TruncateAt.END);confirm.setMinWidth(0);confirm.setMinimumWidth(0);confirm.setPadding(h.dp(4),0,h.dp(4),0);footer.addView(confirm,new LinearLayout.LayoutParams(0,-1,2));}

 }
 void chooseRevive(String item){targetItem=item;switchTarget="";choiceKind="revive";itemPage=0;renderChoices();}
 void command(JSONObject command){
  if(view==null||presenting())return;String kind=command.optString("kind");
  if("switch".equals(kind)){chooseSwitch();return;}if("item".equals(kind)){chooseItem();return;}
  if("escape".equals(kind)){boolean exploring="adventure".equals(view.optJSONObject("state").optString("mode"));selection=new GameDialog(h).navy().setTitle(exploring?"모험 도망":"항복").setMessage(exploring?"성공률 70%. 실패하면 상대만 행동합니다. 성공해도 보상은 정상 복귀 전까지 미정산입니다.":"즉시 패배하며 경험치는 0입니다.").setPositiveButton(exploring?"도망 시도":"항복",(d,w)->{if(current())action(h.obj("kind","escape"));}).setNegativeButton("취소",(d,w)->cancelSelection()).show();final android.app.AlertDialog dialog=selection;dialog.getButton(-2).setOnClickListener(v->{cancelSelection();dialog.dismiss();});dialog.getButton(-1).setOnClickListener(v->{dialog.getButton(-1).setEnabled(false);if(current())action(h.obj("kind","escape"));dialog.dismiss();});unlockOnDismiss();return;}
  JSONObject action=h.obj("kind",command.optString("kind"));try{if(command.has("skillId"))action.put("skillId",command.getString("skillId"));}catch(Exception impossible){throw new IllegalStateException(impossible);}
  mutate("action",h.obj("battleId",command.optString("battleId"),"revision",command.optInt("expectedRevision"),"action",action));
 }
 void mutate(String op,JSONObject body){
  if(!current()||presenting()||!prefs.getString(key+":pending","").isEmpty())return;
  if("settle".equals(op))settlementError="";
  try{body.put("requestId",UUID.randomUUID().toString());JSONObject intent=h.obj("operation",op,"body",body);
   if(!prefs.edit().putString(key+":pending",intent.toString()).commit()){error("요청 저장 실패");return;}send(intent);
  }catch(Exception bad){error("요청 생성 실패");}
 }
 void send(JSONObject intent){
  JSONObject body=intent.optJSONObject("body");if(body==null)return;String id=body.optString("requestId"),op=intent.optString("operation");
  if(!Arrays.asList("create","action","settle","adventure/start","adventure/return").contains(op)||id.isEmpty()){error("저장된 요청이 올바르지 않습니다.");return;}
  if(!IN_FLIGHT.add(id)){message.setText("서버 응답 확인 중…");return;}panel.lock();settle.setEnabled(false);
  NativeApi sender=h.api;Runnable releaseWatch=sender.whenClosed(()->IN_FLIGHT.remove(id));
  sender.call("native/roster60/"+(op.startsWith("adventure/")?op:"battle/"+op),body,(r,e)->{
   releaseWatch.run();IN_FLIGHT.remove(id);if(!owner.equals(h.api.token()))return;
   boolean rejected=(e!=null&&e.startsWith("MENU_LOCKED"))||Arrays.asList("TUTORIAL_STARTER_REQUIRED","ADVENTURE_REQUIRED","ADVENTURE_NOT_FOUND","ADVENTURE_BATTLE_ACTIVE","ADVENTURE_STATE_CHANGED","RETURN_ONLY_OUTSIDE_BATTLE","CATALOG_CHANGED","UNIT_STATE_CHANGED","INVALID_PARTY","INVALID_SWITCH","INVALID_ITEM","INVALID_ITEM_TARGET","NO_STATUS_TO_CLEANSE","HP_FULL","ITEM_UNAVAILABLE","ACTIVE_UNIT_FAINTED","FORCED_SWITCH_REQUIRED","FORCED_SWITCH_POLICY_PENDING","ESCAPE_POLICY_PENDING","BATTLE_POLICY_PENDING","BATTLE_POLICY_INVALID","BATTLE_GROWTH_PENDING","BATTLE_SKILL_POLICY_PENDING","FIVE_STAT_REBIRTH_POLICY_PENDING","ROSTER_CATALOG_PENDING","FIVE_STAT_CATALOG_REQUIRED","FIVE_STAT_CATALOG_INVALID","FIVE_STATS_REQUIRED","INVALID_FIVE_STATS","BASE_MP_MISMATCH","ROSTER_CATALOG_INVALID","INSUFFICIENT_MP","STALE_REVISION","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","ENCOUNTER_NOT_ALLOWED","BATTLE_NOT_FOUND","BATTLE_FINISHED","BATTLE_NOT_FINISHED","INVALID_REQUEST","REQUEST_ID_REUSED","CAP_XP_POLICY_REQUIRED").contains(e);
   if(e==null||rejected)prefs.edit().remove(key+":pending").commit();
   if(!current())return;
   if(e!=null){if("settle".equals(op))settlementError=e;error(e);if(Roster60BattleErrorText.starter(e))return;if(rejected&&view!=null&&!"settle".equals(op))read(view.optString("battleId"));return;}
   // Receipt may be older than current state after reconnect: read authoritative latest.
   if(op.startsWith("adventure/")){showAdventure(r);return;}
   actionAcknowledged(op);
   if("create".equals(op))bindCreated(r);else bind(r);read(r.optString("battleId"));
  });
 }
 void error(String e){
  if(formation!=null)formation.unlock();
  if(Roster60BattleErrorText.starter(e)){showStarterCondition();return;}
  String translated=Roster60BattleErrorText.message(e);rejectionNotice=e!=null&&!translated.equals(e)?translated:"";
  if(view!=null&&"FINISHED".equals(view.optString("status"))){settle.setVisibility(android.view.View.VISIBLE);settle.setEnabled(true);}
  retry.setVisibility(android.view.View.VISIBLE);retry.setEnabled(true);retry.setText(Roster60BattleErrorText.retryLabel(e));retry.setOnClickListener(v->resume());
  message.setText(e!=null&&(e.contains("PENDING")||e.contains("CATALOG"))?h.getString(R.string.roster60_battle_pending)+"\n"+e:Roster60BattleErrorText.message(e));
 }
 void showStarterCondition(){
  message.setText(Roster60BattleErrorText.message(Roster60BattleErrorText.STARTER));
  retry.setVisibility(android.view.View.VISIBLE);retry.setEnabled(true);
  retry.setText(formation!=null?"편성 확인":"뒤로");
  retry.setOnClickListener(v->{if(formation!=null)formation.unlock();else back();});
  if(conditionDialog!=null&&conditionDialog.isShowing())return;
  GameDialog dialog=new GameDialog(h).navy().setTitle("출전 조건 확인");
  dialog.setMessage(Roster60BattleErrorText.starterDetail(formation!=null));
  if(formation!=null)dialog.setPositiveButton("현재 편성 보기",(d,w)->{if(formation!=null)formation.unlock();});
  else dialog.setPositiveButton("확인",null);
  dialog.setNegativeButton("뒤로",(d,w)->back());
  conditionDialog=dialog.show();final android.app.AlertDialog shown=conditionDialog;
  shown.setOnDismissListener(d->{if(h.visibleDialog==shown)h.visibleDialog=null;if(conditionDialog==shown)conditionDialog=null;});
 }
 void closeCondition(){if(conditionDialog!=null)conditionDialog.dismiss();conditionDialog=null;}

 void actionAcknowledged(String operation){
  rejectionNotice="";
  if("action".equals(operation)){panel.cancelInteraction();panel.restoreSkillPage(false);}
 }
 void createOpponentPanel(LinearLayout stage){
  opponentDetails=new LinearLayout(h);opponentDetails.setOrientation(LinearLayout.VERTICAL);
  opponentDetails.setTag("roster60-opponent-panel");opponentDetails.setPadding(h.dp(8),h.dp(3),h.dp(8),h.dp(3));
  opponentDetails.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,6));
  opponentDetails.setVisibility(android.view.View.GONE);
  opponentSummary=intelText();opponentSummary.setTag("roster60-opponent-summary");opponentDetails.addView(opponentSummary,new LinearLayout.LayoutParams(-1,-2));
  for(int row=0;row<2;row++){
   LinearLayout line=new LinearLayout(h);opponentDetails.addView(line,new LinearLayout.LayoutParams(-1,-2));
   for(int col=0;col<4;col++){
    int index=row*4+col;TextView cell=intelText();cell.setTag("roster60-opponent-reaction-"+Roster60OpponentIntel.ELEMENTS[index]);
    opponentReactions[index]=cell;line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
   }
  }
  stage.addView(opponentDetails,new LinearLayout.LayoutParams(-1,-2));scaleOpponentPanel();
 }
 TextView intelText(){TextView text=h.text("",12);text.setTypeface(h.prose);text.setTextColor(NavyWorkbenchTheme.TEXT);text.setIncludeFontPadding(true);text.setSingleLine(false);text.setHorizontallyScrolling(false);text.setPadding(0,h.dp(1),h.dp(3),h.dp(1));return text;}
 void scaleOpponentPanel(){float size=12*Math.min(1.3f,scale);if(opponentSummary!=null)opponentSummary.setTextSize(size);for(TextView cell:opponentReactions)if(cell!=null)cell.setTextSize(size);}
 // Kept as a read-only refresh entry for existing callers; the panel is already on screen.
 void showOpponentInfo(){if(view==null||view.optJSONObject("state")==null)return;opponentDetails.setVisibility(android.view.View.VISIBLE);refreshOpponentInfo();}
 void refreshOpponentInfo(){
  if(opponentDetails==null)return;
  Roster60OpponentIntel intel=new Roster60OpponentIntel(opponentProjection==null?view:opponentProjection,opponentInfoChanging);
  String scope="battle-observed".equals(intel.defenseKnowledgeScope)?"이번 전투":"공격 관찰";
  String headline=intel.changing?"상대 교체 확인 중 · 0 / 8":scope+" "+intel.knownCount+" / 8";
  headline+=" · "+(intel.statsKnown?"내구  "+intel.stats[2]+" · 저항  "+intel.stats[3]+" · 속도  "+intel.stats[4]:intel.statsNotice());
  opponentSummary.setText(headline);StringBuilder accessible=new StringBuilder(headline);
  for(int i=0;i<opponentReactions.length;i++){
   String value=Roster60OpponentIntel.ELEMENT_NAMES[i]+" "+Roster60OpponentIntel.defenseLabel(intel.defenses[i]);
   opponentReactions[i].setText(value);accessible.append(" · ").append(value);
  }
  opponentDetails.setContentDescription(accessible);
 }
 void opponentEvent(JSONObject event){
  if("enemy".equals(event.optString("actor"))&&("switch".equals(event.optString("kind"))||"forced-switch".equals(event.optString("kind")))){
   opponentProjection=null;opponentInfoChanging=true;refreshOpponentInfo();return;
  }
  JSONObject presentation=event.optJSONObject("presentation"),before=presentation==null?null:presentation.optJSONObject("opponentIntelBefore");
  JSONArray fighters=presentation==null?null:presentation.optJSONArray("before");
  if(before!=null&&fighters!=null&&fighters.length()==2&&Roster60OpponentIntel.VERSION.equals(before.optString("version"))){
   // Before contains only earlier observed hits. The current hit's After is not shown during wind-up.
   opponentProjection=h.obj("state",h.obj("fighters",fighters,"opponentIntel",before));opponentInfoChanging=false;refreshOpponentInfo();
  }
 }
 void suspendOpponentInfo(){opponentProjection=null;opponentInfoChanging=true;refreshOpponentInfo();}
 void closeOpponentInfo(){if(opponentDetails!=null)opponentDetails.setVisibility(android.view.View.GONE);opponentProjection=null;}
 boolean presenting(){return playback!=null&&playback.playing();}
 void lockPresentation(){
  panel.lock();panel.cancelInteraction();panel.restoreSkillPage(false);
  choiceKind="";switchTarget="";choicePanel.setVisibility(android.view.View.GONE);
  panel.setVisibility(android.view.View.VISIBLE);
  retry.setEnabled(false);settle.setEnabled(false);returnAdventure.setEnabled(false);
 }
 // 모험의 다음 조우는 같은 presenter를 사용한다. 성공한 create 응답만 새 전투 기준선을 연다.
 void bindCreated(JSONObject r){
  if(playback!=null&&playback.latest!=null&&!r.optString("battleId").isEmpty()
      &&!r.optString("battleId").equals(playback.latest.optString("battleId"))){
   playback.suspend();playback=new NativeRoster60Playback(this);
  }
  bind(r);
 }
 void bind(JSONObject r){
  if(playback==null)playback=new NativeRoster60Playback(this);
  playback.accept(r);
 }
 void renderSnapshot(JSONObject r){
  opponentProjection=null;opponentInfoChanging=false;retry.setEnabled(true);
  try{
   JSONObject s=r.getJSONObject("state");clearFormation();art.bind(s);if(!"five-battle-v1".equals(s.getString("version")))throw new JSONException("version");
   JSONArray fighters=s.getJSONArray("fighters");JSONObject self=fighters.getJSONObject(0),foe=fighters.getJSONObject(1);
   stats.setText("");stats.setVisibility(android.view.View.GONE);boolean active="ACTIVE".equals(r.getString("status"));JSONArray commands=new JSONArray(),skills=new JSONArray();String[] ids={"attack","skills","focus","item","switch","escape"},labels={"공격","기술","집중","아이템","교체","도망치기"};
   for(int i=0;i<6;i++){String reason=i<3?"":r.optJSONObject("disabledCommands")==null?"정책 준비 중":r.optJSONObject("disabledCommands").optString(ids[i]);boolean enabled=active&&reason.isEmpty();if(i<3&&self.optInt("hp")==0){enabled=false;reason="전투불가";}commands.put(h.obj("kind",ids[i],"label",labels[i],"enabled",enabled,"reason",reason));}
   JSONArray raw=self.getJSONArray("skills");for(int i=0;i<raw.length();i++){JSONObject sk=new JSONObject(raw.getJSONObject(i).toString());boolean enabled=active&&self.optInt("hp")>0&&self.getInt("mp")>=sk.getInt("mpCost");sk.put("enabled",enabled);sk.put("reason",active?(enabled?"":"MP 부족"):"전투 종료");sk.put("description","위력 "+sk.optDouble("power"));skills.put(sk);}
   JSONObject displaySelf=new JSONObject(self.toString());displaySelf.put("maxHp",self.getJSONObject("stats").getInt("hp"));displaySelf.put("maxMp",self.getJSONObject("stats").getInt("mp"));JSONObject displayFoe=new JSONObject(foe.toString());displayFoe.put("maxHp",foe.getJSONObject("stats").getInt("hp"));displayFoe.put("maxMp",foe.getJSONObject("stats").getInt("mp"));
   panel.bind(h.obj("epoch","product-five-battle-v1","battleId",r.getString("battleId"),"revision",r.getInt("revision"),"self",displaySelf,"foe",displayFoe,"commands",commands,"skills",skills,"events",new JSONArray()));
   adventure=null;returnAdventure.setVisibility(android.view.View.GONE);retry.setVisibility(android.view.View.GONE);settle.setVisibility(android.view.View.GONE);view=r;showOpponentInfo();
   if(!active){choiceKind="";choicePanel.setVisibility(android.view.View.GONE);}
   panel.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);
   if(active&&!choiceKind.isEmpty())renderChoices();
   message.setText(active?"턴 "+s.getInt("turn")+" · 행동을 선택하세요":resultLabel(s.optString("outcome")));
   if(active&&self.optInt("hp")==0)message.setText("전투불가 · 생존한 예비 개체를 선택하세요");
   if(active&&!rejectionNotice.isEmpty()){message.setText(rejectionNotice);rejectionNotice="";}
   if("FINISHED".equals(r.optString("status"))&&!settlementError.isEmpty()){error(settlementError);}
   else if("FINISHED".equals(r.optString("status"))){message.setText(resultLabel(s.optString("outcome"))+" · 결과 정산 중…");
    if(!r.optString("battleId").equals(autoSettled)){autoSettled=r.optString("battleId");final String id=autoSettled;h.page.post(()->{if(current()&&view!=null&&id.equals(view.optString("battleId"))&&"FINISHED".equals(view.optString("status")))mutate("settle",h.obj("battleId",id,"revision",view.optInt("revision")));});}
   }else if("SETTLED".equals(r.optString("status"))){stats.setVisibility(android.view.View.VISIBLE);JSONObject receipt=r.optJSONObject("settlement");boolean exploring="adventure".equals(s.optString("mode"));stats.setText(exploring?"LOST".equals(receipt==null?"":receipt.optString("adventureStatus"))?"전멸로 이번 모험의 미정산 보상을 잃었습니다.":"모험 보상은 누적 보관됩니다. 정상 복귀 시 획득합니다.":"경험치 +"+(receipt==null?0:receipt.optInt("xpAwarded"))+" · 결과 저장 완료");retry.setVisibility(android.view.View.VISIBLE);retry.setText(exploring?"모험 확인":"홈으로");retry.setOnClickListener(v->{if(exploring)adventureResume();else h.load("home");});}

  }catch(Exception bad){view=null;closeOpponentInfo();panel.lock();settle.setEnabled(false);message.setText("서버 전투 데이터 형식을 확인할 수 없습니다.");}
 }
 static String resultLabel(String outcome){return "win".equals(outcome)?"승리":"loss".equals(outcome)?"패배":"escaped".equals(outcome)?"도망 성공":"전투 종료";}
 boolean back(){if(presenting()){playback.suspend();h.load("home");return true;}if(formation!=null){clearFormation();message.setText("전투를 선택하세요.");options();return true;}if(!choiceKind.isEmpty()){closeChoices();return true;}if(panel!=null){panel.cancelInteraction();if(panel.showCommands())return true;}h.load("home");return true;}
 void pause(){closeCondition();presentationPaused=true;if(playback!=null)playback.suspend();if(panel!=null)panel.cancelInteraction();if(selection!=null){selection.dismiss();selection=null;}}
}

