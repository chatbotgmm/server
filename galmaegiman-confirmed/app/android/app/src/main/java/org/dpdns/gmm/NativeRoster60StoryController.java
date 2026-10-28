package org.dpdns.gmm;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Account-scoped story transport. The client never submits progress, drops, or statue stages. */
final class NativeRoster60StoryController {
  static final String SCREEN = "roster60Story";
  static final Set<String> IN_FLIGHT = new HashSet<>();
  final MainActivity h;
  NativeRoster60StoryView view;
  Roster60StoryJournal journal;
  JSONObject state;
  String owner = "", problem = "";
  int epoch = -1, readGeneration;
  boolean reading;
  final Handler reconnect=new Handler(Looper.getMainLooper());
  final Runnable checkOwnSave=()->{if(current())refresh();};
  Roster60FormationView formation;
  Roster60PreparationView preparation;
  android.app.AlertDialog residentDialogue;
  String residentEntryId="";
  NativeVillageStoryOverlay overlay;
  String villageResidentId="";
  boolean villageMode,villageDeparture,villageExploration,villageHistory;

  NativeRoster60StoryController(MainActivity host) { h = host; }
  NativeRoster60StoryController(MainActivity host,String residentId) {h=host;if(java.util.Arrays.asList("blacksmith","merchant","librarian","guard").contains(residentId))residentEntryId=residentId;}
  void open() {
    if(h.busy || h.updateBlocks() || h.isFinishing() || h.isDestroyed()) return;
    if(h.api.token().isEmpty()) { h.login(); return; }
    owner=h.api.token(); journal=new Roster60StoryJournal(h.getSharedPreferences("roster60_story_v1",Context.MODE_PRIVATE),owner);
    h.base("이야기",SCREEN);epoch=h.epoch;
    // Story and resident conversation are not a currency spending destination.
    View header=h.root.findViewWithTag("theme-page-header");if(header instanceof LinearLayout) {
      LinearLayout row=(LinearLayout)header;while(row.getChildCount()>2)row.removeViewAt(row.getChildCount()-1);
    }
    h.page.removeViewAt(h.page.getChildCount()-1);view=new NativeRoster60StoryView(h,this);h.body=view;
    h.page.addView(view,new LinearLayout.LayoutParams(-1,0,1));refresh();
  }
  /** Modal entry: never replaces the village or executes a quest mutation on opening. */
  void openVillage() {
    if(h.busy||h.updateBlocks()||h.isFinishing()||h.isDestroyed()||h.villageHome==null||!h.villageHome.current())return;
    owner=h.api.token();epoch=h.epoch;villageMode=true;villageResidentId=residentEntryId;residentEntryId="";
    journal=new Roster60StoryJournal(h.getSharedPreferences("roster60_story_v1",Context.MODE_PRIVATE),owner);
    h.villageHome.view.stopMotion();
    overlay=new NativeVillageStoryOverlay(h,this);overlay.open();readState(false);
  }
  void openVillageHistory(){villageHistory=true;openVillage();}
  void openVillageExploration(){villageExploration=true;openVillage();}
  void openVillageDeparture(){villageDeparture=true;openVillage();}
  boolean villageOverlayOpen(){return villageMode&&overlay!=null&&overlay.isShowing();}
  void loading(String message){if(villageMode){if(overlay!=null)overlay.loading(message);}else view.loading(message);}
  void closeVillage(){closeVillage(true);}
  void closeVillage(boolean refreshHome){
    if(!villageMode)return;
    readGeneration++;reading=false;reconnect.removeCallbacks(checkOwnSave);
    if(residentDialogue!=null){residentDialogue.dismiss();residentDialogue=null;}
    if(overlay!=null){overlay.close();overlay=null;}
    if(refreshHome&&sameVillage())h.villageHome.ensureStoryFresh();
  }
  boolean sameVillage(){return villageMode&&h.villageHome!=null&&owner.equals(h.api.token())&&epoch==h.epoch&&h.villageHome.current();}
  static boolean fullStoryState(JSONObject result){
    if(result==null||!(result.opt("revision") instanceof Number)||result.optString("contentVersion").isEmpty())return false;
    double revision=((Number)result.opt("revision")).doubleValue();
    return Double.isFinite(revision)&&revision>=0&&revision<=9_007_199_254_740_991d&&revision==Math.rint(revision)&&result.optJSONArray("quests")!=null&&result.optJSONArray("residents")!=null&&result.optJSONObject("statue")!=null&&result.optJSONArray("history")!=null&&result.optJSONArray("adventures")!=null;
  }
  boolean current() {
    if(villageMode)return !owner.isEmpty()&&owner.equals(h.api.token())&&epoch==h.epoch
        &&"home".equals(h.screen)&&!h.isFinishing()&&!h.isDestroyed()&&overlay!=null&&overlay.isShowing()
        &&h.villageHome!=null&&h.villageHome.current();
    return !owner.isEmpty() && owner.equals(h.api.token()) && epoch==h.epoch && SCREEN.equals(h.screen)
        && !h.isFinishing() && !h.isDestroyed() && view!=null && h.root.indexOfChild(h.page)>=0;
  }
  boolean waiting() {
    if(reading)return true;
    try{return journal!=null&&journal.pending()!=null;}catch(IllegalStateException invalid){return true;}
  }
  void refresh() {
    if(!current()||reading)return;
    if(villageMode){readState();return;}
    retrySavedProgress();
  }
  /** Explicit user action only in village mode; preserves durable request ids. */
  void retrySavedProgress() {
    if(!current()||reading)return;
    try{JSONObject pending=journal.pending();if(pending==null){recoverBattleReturn();pending=journal.pending();}if(pending==null&&villageMode&&state!=null){JSONObject recovery=state.optJSONObject("pendingDeparture");if(recovery!=null){if(!journal.restore(recovery))throw new IllegalStateException("출발 요청을 복구하지 못했어요.");pending=journal.pending();}}if(pending!=null){send(pending);return;}}
    catch(IllegalStateException invalid){error(invalid.getMessage());return;}
    readState();
  }
  String battlePendingKey(){return NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+Roster60StoryJournal.accountId(owner)+"|story-v1")+":pending";}
  void recoverBattleReturn(){
    String raw=h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE).getString(battlePendingKey(),"");if(raw.isEmpty())return;
    try{JSONObject intent=new JSONObject(raw);if("adventure/return".equals(intent.optString("operation"))&&!journal.restore(intent))throw new IllegalStateException("귀환 요청을 복구하지 못했어요.");}
    catch(org.json.JSONException invalid){throw new IllegalStateException("저장된 전투 귀환 요청 확인이 필요해요.");}
  }
  void acknowledgeBattleReturn(JSONObject intent){
    if(!"adventure/return".equals(intent.optString("operation")))return;
    android.content.SharedPreferences prefs=h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE);String raw=prefs.getString(battlePendingKey(),"");if(raw.isEmpty())return;
    try{JSONObject pending=new JSONObject(raw);if(pending.optJSONObject("body")!=null&&pending.optJSONObject("body").optString("requestId").equals(intent.optJSONObject("body").optString("requestId")))prefs.edit().remove(battlePendingKey()).commit();}
    catch(org.json.JSONException ignored){}
  }
  void readState(){readState(true);}
  void readState(boolean force){
    if(!current()||reading)return;
    boolean pending=false;
    try{pending=journal!=null&&journal.pending()!=null;}catch(IllegalStateException invalid){pending=true;}
    pending=pending||!h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE).getString(battlePendingKey(),"").isEmpty();
    boolean mustRead=force||pending;
    if(sameVillage()&&!mustRead){JSONObject cached=h.villageHome.freshStorySnapshot();if(cached!=null){acceptStoryState(cached);return;}}
    reading=true;int generation=++readGeneration;loading("이야기를 불러오는 중…");
    NativeApi.Result callback=(result,error)->{
      if(!current()||generation!=readGeneration)return;reading=false;
      if(error!=null){error(error);return;}
      if(!fullStoryState(result)){error("이야기 저장 응답을 확인할 수 없습니다.");return;}
      acceptStoryState(result);
    };
    if(sameVillage())h.villageHome.requestStoryState(mustRead,callback);else h.api.call("native/roster60/story/state",null,callback);
  }
  void acceptStoryState(JSONObject result){
    if(!current()||!fullStoryState(result))return;
    if(state!=null&&result.optString("contentVersion").equals(state.optString("contentVersion"))&&result.optLong("revision")<state.optLong("revision")){error("STORY_STATE_CHANGED");return;}
      // A refreshed account snapshot invalidates any disabled departure choices.
      formation=null;preparation=null;state=result;problem="";
      JSONObject recovery=result.optJSONObject("pendingDeparture");
      if(!villageMode&&recovery!=null&&java.util.Arrays.asList("battle/create","adventure/create").contains(recovery.optString("operation"))){
        try{if(journal.pending()==null&&!journal.restore(recovery)){error("출발 요청을 기기에 저장하지 못했어요. 다시 확인해 주세요.");return;}send(journal.pending());return;}
        catch(IllegalStateException invalid){error(invalid.getMessage());return;}
      }
      render();
      if(!residentEntryId.isEmpty()){
        String id=residentEntryId;residentEntryId="";view.tab="residents";render();
        if(state.optJSONObject("scene")==null){org.json.JSONArray npcs=state.optJSONArray("residents");for(int i=0;npcs!=null&&i<npcs.length();i++){JSONObject npc=npcs.optJSONObject(i);if(npc!=null&&id.equals(npc.optString("id"))){resident(npc);break;}}}
        else view.notice.setText("읽던 이야기를 마치거나 닫은 뒤 주민의 부탁을 들을 수 있어요.");
      }
  }
  void render() { if(current()&&state!=null&&formation==null&&preparation==null){if(villageMode)overlay.show(state);else view.show(state);} }
  void error(String error) {
    if(!current())return;if(sameVillage())h.villageHome.invalidateStorySnapshot();problem=message(error);
    if(preparation!=null){
      if(waiting())preparation.retryPending(error,this::retrySavedProgress);else preparation.unlock(error);
      return;
    }
    if(formation!=null)formation.unlock();
    if(villageMode){overlay.error(problem);return;}view.notice.setText(problem);
    // Even an uncertain write can be safely retried with the durable intent.
    view.enabled(false);view.continueTab.setEnabled(true);view.continueTab.setText("연결 다시 확인");
    view.continueTab.setOnClickListener(v->{if(current()){view.continueTab.setText("이어하기");view.continueTab.setOnClickListener(w->{if(current()){view.tab="main";render();}});refresh();}});
  }
  static String message(String error) {
    if(error==null)return "이야기를 확인하지 못했어요.";
    if(error.startsWith("STORY_POLICY")||error.startsWith("STORY_CONTENT")||error.startsWith("STORY_PROFILE"))return "이야기 기능이 아직 열리지 않았어요. 저장된 진행은 유지됩니다.";
    if(error.contains("LOCKED"))return "아직 열리지 않은 이야기예요.";
    if("STALE_REVISION".equals(error)||"STORY_STALE_REVISION".equals(error)||"STORY_STATE_CHANGED".equals(error)||"STORY_SCENE_STALE".equals(error))return "다른 요청에서 진행이 바뀌었어요. 저장 위치를 다시 확인해 주세요.";
    if("STORY_SCENE_ACTIVE".equals(error))return "읽고 있는 대화를 마치거나 나중에 다시 듣기로 닫아 주세요.";
    if("STORY_ACCEPT_REQUIRED".equals(error)||"STORY_ACCEPT_NOT_READY".equals(error))return "대화를 끝까지 들은 뒤 의뢰를 수락해 주세요.";
    if("STORY_OBJECTIVE_INCOMPLETE".equals(error))return "현재 의뢰의 목표와 모험 귀환을 먼저 확인해 주세요.";
    if("STORY_BATTLE_ACTIVE".equals(error)||"STORY_DEPARTURE_PENDING".equals(error))return "진행 중인 출발과 전투를 먼저 이어서 확인해 주세요.";
    if(error.startsWith("STORY_"))return "저장된 이야기 위치를 다시 확인해 주세요.";
    if(error.contains("ADVENTURE")||error.contains("UNIT_"))return "진행 중인 모험과 출전 대원을 다시 확인해 주세요.";
    String battleMessage=Roster60BattleErrorText.message(error);return battleMessage.equals(error)?error:battleMessage;
  }
  void mutate(String operation,JSONObject body) {
    if(!current()||reading||state==null)return;
    try{
      JSONObject existing=journal.pending();if(existing!=null){send(existing);return;}
      if(!"battle/create".equals(operation)&&!"adventure/create".equals(operation)&&!"adventure/return".equals(operation))body.put("revision",state.optInt("revision"));
      send(journal.prepare(operation,body));
    }catch(Exception invalid){error(invalid.getMessage());}
  }
  void send(JSONObject intent) {
    if(!current())return;
    JSONObject body=intent.optJSONObject("body");if(body==null)return;
    String operation=intent.optString("operation"),requestId=body.optString("requestId"),flight=journal.key+"|"+requestId;
    if(!Roster60StoryJournal.allowed(operation)||requestId.isEmpty()){error("저장된 이야기 요청 확인이 필요합니다.");return;}
    if(("adventure/return".equals(operation)&&NativeRoster60Battle.IN_FLIGHT.contains(requestId))||!IN_FLIGHT.add(flight)){loading("이야기 요청의 결과를 확인하고 있어요.");reconnect.removeCallbacks(checkOwnSave);reconnect.postDelayed(checkOwnSave,500);return;}
    if(sameVillage())h.villageHome.invalidateStorySnapshot();
    loading("이야기를 저장하고 있어요.");
    final String requestedContentVersion=state==null?"":state.optString("contentVersion");
    NativeApi api=h.api;Runnable release=api.whenClosed(()->IN_FLIGHT.remove(flight));
    String route="adventure/return".equals(operation)?"native/roster60/adventure/return":"native/roster60/story/"+operation;
    api.call(route,body,(result,error)->{
      release.run();IN_FLIGHT.remove(flight);
      if(!owner.equals(h.api.token()))return;
      if(error==null||Roster60StoryJournal.definitive(error)){journal.acknowledge(intent);acknowledgeBattleReturn(intent);}
      boolean storyReply=java.util.Arrays.asList("open","advance","accept","close").contains(operation)&&error==null&&fullStoryState(result)&&requestedContentVersion.equals(result.optString("contentVersion"))&&result.optLong("revision")>body.optLong("revision",Long.MAX_VALUE);
      boolean published=storyReply&&sameVillage()&&h.villageHome.publishStorySnapshot(result);
      if(!current())return;
      if(error!=null){
        if(error.contains("STALE_REVISION")||"STORY_STATE_CHANGED".equals(error)||"STORY_SCENE_STALE".equals(error)){readState();return;}
        error(error);return;
      }
      problem="";
      if("battle/create".equals(operation)||"adventure/create".equals(operation)){
        if(result==null||result.optString("battleId").isEmpty()){error("전투 응답을 확인하지 못했어요. 저장 위치를 다시 확인해 주세요.");return;}
        openBattle(result,body.optString("questId"));return;
      }
      if(storyReply&&(!villageMode||published))acceptStoryState(result);else readState();
    });
  }
  JSONObject quest(String id) {
    JSONArray quests=state==null?null:state.optJSONArray("quests");if(quests!=null)for(int i=0;i<quests.length();i++){JSONObject quest=quests.optJSONObject(i);if(quest!=null&&id.equals(quest.optString("id")))return quest;}return null;
  }
  void continueQuest(JSONObject quest) {
    if(!current()||waiting()||quest==null)return;
    String status=quest.optString("status");
    if("READY".equals(status)){openQuest(quest.optString("id"),"return");return;}
    if("AVAILABLE".equals(status)){openQuest(quest.optString("id"),"intro");return;}
    if("COMPLETED".equals(status)){openQuest(quest.optString("id"),"replay");return;}
    if(!"ACTIVE".equals(status))return;
    String battleId=reference(quest,"battleId"),adventureId=reference(quest,"adventureId");
    if(!battleId.isEmpty()){resumeBattle(quest.optString("id"),battleId);return;}
    if(!adventureId.isEmpty()){resumeAdventure(quest,adventureId);return;}
    if(quest.optBoolean("canDepart")){chooseParty(quest);return;}
    openQuest(quest.optString("id"),"intro");
  }
  void openQuest(String questId,String phase) { if(!current()||waiting())return;mutate("open",h.obj("questId",questId,"phase",phase)); }
  void advance(int cursor) { if(!current()||waiting())return;mutate("advance",h.obj("cursor",cursor)); }
  void accept(String questId) { if(!current()||waiting())return;mutate("accept",h.obj("questId",questId)); }
  void closeScene() { if(!current()||waiting())return;mutate("close",h.obj()); }
  void resident(JSONObject resident) {
    if(!current()||waiting())return;
    if(villageMode){overlay.show(state);return;}
    String status=resident.optString("status"),questId=resident.optString("questId");
    if("AVAILABLE".equals(status)){openQuest(questId,"intro");return;}
    if("READY".equals(status)){openQuest(questId,"return");return;}
    String dialogue=resident.optString("dailyDialogue","");JSONArray lines=resident.optJSONArray("dailyDialogue");if(lines!=null){java.util.ArrayList<String> text=new java.util.ArrayList<>();for(int i=0;i<lines.length();i++)text.add(lines.optString(i));dialogue=TextUtilsCompat.join(text);}
    GameDialog dialog=new GameDialog(h).navy();dialog.setTitle(resident.optString("name")+" · "+resident.optString("role"));dialog.setMessage(dialogue);dialog.setNegativeButton("닫기",null);
    if("ACTIVE".equals(status)){JSONObject active=quest(questId);dialog.setPositiveButton("부탁 계속",(d,w)->{if(current())continueQuest(active);});}
    residentDialogue=dialog.show();
  }
  void chooseParty(JSONObject quest) {
    chooseParty(quest,"");
  }
  void chooseParty(JSONObject quest,String encounterId) {
    if(!current()||waiting())return;reading=true;loading("출항할 대원과 저장된 편성을 확인하고 있어요.");
    final int generation=++readGeneration;
    final JSONObject[] results=new JSONObject[2];final String[] failures=new String[2];
    final boolean[] ready={false,false},joined={false};
    Runnable complete=()->{
      if(!current()||generation!=readGeneration||joined[0]||!ready[0]||!ready[1])return;
      joined[0]=true;reading=false;
      if(failures[0]!=null||failures[1]!=null){error(failures[0]!=null?failures[0]:failures[1]);return;}
      JSONArray units=results[0]==null?null:results[0].optJSONArray("units");
      if(units==null||units.length()==0){error("모험에 나갈 수 있는 대원이 없어요. 진행 중인 전투와 편성을 확인해 주세요.");return;}
      JSONArray ids=results[1]==null?null:results[1].optJSONArray("partyIds");
      showPreparation(units,ids,quest,encounterId);
    };
    // Independent authoritative reads overlap; neither response alone can start a battle.
    h.api.call("native/roster60/battle/options",null,(result,error)->{if(!current()||generation!=readGeneration||ready[0])return;results[0]=result;failures[0]=error;ready[0]=true;complete.run();});
    h.api.call("native/roster60/pvp/options",null,(result,error)->{if(!current()||generation!=readGeneration||ready[1])return;results[1]=result;failures[1]=error;ready[1]=true;complete.run();});
  }
  static boolean validSavedParty(JSONArray ids,JSONArray units){
    return Roster60PreparationView.valid(ids,units);
  }
  void showPreparation(JSONArray units,JSONArray ids,JSONObject quest,String encounterId){
    if(view!=null)view.pauseTyping();android.widget.FrameLayout destination=villageMode?overlay.formationContent():view.content;destination.removeAllViews();
    preparation=new Roster60PreparationView(h,units,ids,quest==null?"모험 출항 준비":quest.optString("title","부탁 출항 준비"),chosen->{
      if(current()&&!waiting())mutate(encounterId.isEmpty()?"battle/create":"adventure/create",encounterId.isEmpty()?h.obj("questId",quest.optString("id"),"ownedIds",chosen):h.obj("encounterId",encounterId,"ownedIds",chosen));
    },()->{if(current()){preparation=null;formation=null;render();}},this::current);
    destination.addView(preparation,new android.widget.FrameLayout.LayoutParams(-1,-1));
  }
  void showFormation(JSONArray units,JSONObject quest,String encounterId){
      if(view!=null)view.pauseTyping();android.widget.FrameLayout destination=villageMode?overlay.formationContent():view.content;destination.removeAllViews();formation=new Roster60FormationView(h,units,ids->{if(current()&&!waiting())mutate(encounterId.isEmpty()?"battle/create":"adventure/create",encounterId.isEmpty()?h.obj("questId",quest.optString("id"),"ownedIds",ids):h.obj("encounterId",encounterId,"ownedIds",ids));},()->{if(current()){formation=null;render();}});
      destination.addView(formation,new android.widget.FrameLayout.LayoutParams(-1,-1));
  }
  void chooseMaterialAdventure(){
    if(!current()||waiting())return;JSONArray places=state.optJSONArray("adventures");if(places==null||places.length()==0)return;
    String[] labels=new String[places.length()];for(int i=0;i<labels.length;i++)labels[i]=places.optJSONObject(i).optString("name","흑화 갈매미 모험")+" · 권장 "+places.optJSONObject(i).optInt("level");
    residentDialogue=new GameDialog(h).navy().setTitle("열린 모험 지점").setItems(labels,(dialog,index)->{if(current())chooseParty(null,places.optJSONObject(index).optString("id"));}).setNegativeButton("취소",null).show();
  }
  void resumeMaterialAdventure(){
    if(!current()||waiting())return;JSONObject active=state.optJSONObject("materialAdventure");if(active==null)return;
    String battleId=reference(active,"activeBattleId");if(!battleId.isEmpty()){resumeBattle("",battleId);return;}
    String adventureId=reference(active,"adventureId");if(adventureId.isEmpty())return;reading=true;loading("모험 귀환 위치를 확인하고 있어요.");
    h.api.call("native/roster60/adventure/read",h.obj("adventureId",adventureId),(result,error)->{
      if(!current())return;reading=false;if(error!=null){error(error);return;}
      String nextBattle=reference(result,"activeBattleId");if(!nextBattle.isEmpty()){resumeBattle("",nextBattle);return;}
      if("ACTIVE".equals(result.optString("status")))mutate("adventure/return",h.obj("adventureId",adventureId,"revision",result.optInt("revision")));else refresh();
    });
  }
  void resumeBattle(String questId,String battleId) {
    if(!current()||waiting())return;reading=true;loading("진행 중인 전투를 이어서 확인하고 있어요.");
    h.api.call("native/roster60/battle/read",h.obj("battleId",battleId),(result,error)->{if(!current())return;reading=false;if(error!=null){error(error);return;}openBattle(result,questId);});
  }
  void resumeAdventure(JSONObject quest,String adventureId) {
    if(!current()||waiting())return;reading=true;loading("모험의 귀환 위치를 확인하고 있어요.");
    h.api.call("native/roster60/adventure/read",h.obj("adventureId",adventureId),(result,error)->{
      if(!current())return;reading=false;if(error!=null){error(error);return;}
      String battleId=result.optString("activeBattleId");if("null".equals(battleId))battleId="";
      if(!battleId.isEmpty()){resumeBattle(quest.optString("id"),battleId);return;}
      if(!"ACTIVE".equals(result.optString("status"))){if(quest.optBoolean("canDepart"))chooseParty(quest);else refresh();return;}
      JSONObject objective=quest.optJSONObject("objective");boolean complete=objective!=null&&objective.optInt("wins")>=objective.optInt("requiredWins",1);
      if(complete){mutate("adventure/return",h.obj("adventureId",adventureId,"revision",result.optInt("revision")));return;}
      JSONArray party=result.optJSONArray("party"),ids=new JSONArray();if(party!=null)for(int i=0;i<party.length();i++)ids.put(party.optJSONObject(i).optString("ownedId"));
      mutate("battle/create",h.obj("questId",quest.optString("id"),"ownedIds",ids,"adventureId",adventureId));
    });
  }
  void openBattle(JSONObject initial,String questId) {
    if(!current())return;pause();h.rosterBattle=new NativeRoster60StoryBattle(h,initial,questId);h.rosterBattle.open();
  }
  void pause() { if(villageMode){closeVillage(false);return;}reconnect.removeCallbacks(checkOwnSave);if(view!=null)view.pauseTyping();if(residentDialogue!=null){residentDialogue.dismiss();residentDialogue=null;} }
  void freeAdventure(){if(!current()||waiting())return;pause();h.openRosterBattle(Roster60BattleMode.ADVENTURE);}
  void playMenu(){if(!current()||waiting())return;pause();h.openPlayMenu();}
  static String reference(JSONObject value,String name){return value==null||value.isNull(name)?"":value.optString(name);}
  boolean back() {
    if(preparation!=null&&current())return preparation.back();
    if(villageMode){if(!villageOverlayOpen())return false;closeVillage();return true;}
    if(!current())return false;if(h.busy||h.updateBlocks())return true;
    if(formation!=null){formation=null;render();return true;}
    pause();h.openPlayMenu();return true;
  }
  static final class TextUtilsCompat { static String join(java.util.List<String> values){return android.text.TextUtils.join("\n",values);} }
}
