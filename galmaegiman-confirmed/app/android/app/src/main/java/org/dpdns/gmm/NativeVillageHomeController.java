package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.FrameLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Home reads only server-authored quest availability. A resident tap never accepts an order. */
final class NativeVillageHomeController {
  final MainActivity h;
  final Map<String,JSONObject> residents=new LinkedHashMap<>();
  final SharedPreferences preferences;
  final String owner,scope;
  final int epoch;
  NativeVillageHomeView view;
  VillageLayout layout;
  String problem="";
  String savedCamera="";
  int readGeneration;
  int statueStage; // 0 means no authenticated stage has been read for this controller/account.
  boolean reading,paused;
  android.app.AlertDialog menu;
  NativeVillageHomeController(MainActivity h) {
    this.h=h;owner=h.api.token();epoch=h.epoch;
    scope=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|village-v2|"+Roster60StoryJournal.accountId(owner));
    preferences=h.getSharedPreferences("native_village_home_v2",Context.MODE_PRIVATE);
    try { layout=VillageLayout.load(h); }
    catch(Exception unavailable) { layout=VillageLayout.referenceLayout();problem="마을 배치 자료를 다시 확인해 주세요."; }
  }
  void attach() {
    if(owner.isEmpty()){h.login();return;}
    view=new NativeVillageHomeView(h,this,layout);h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));
    try{savedCamera=preferences.getString(scope+":camera","0");double saved=Double.parseDouble(savedCamera);if(Double.isFinite(saved))view.camera.setCameraUnits(saved);}catch(Exception invalid){savedCamera="";}
    view.assets.setViewport(view.camera.cameraUnits(),view.field.width()>0?view.field.width()/view.unitPixels():1);view.assets.load(layout,this::current,()->{if(current()){view.updateNotice();view.invalidate();}});
    refresh();
  }
  boolean current() { return !paused&&view!=null&&epoch==h.epoch&&owner.equals(h.api.token())&&!owner.isEmpty()
      &&"home".equals(h.screen)&&!h.isFinishing()&&!h.isDestroyed()&&h.root.indexOfChild(view)>=0; }
  void refresh() {
    if(!current()||reading)return;reading=true;int generation=++readGeneration;
    h.api.call("native/roster60/story/state",null,(result,error)->{
      if(!paused&&view!=null&&epoch==h.epoch&&"home".equals(h.screen)&&h.api.token().isEmpty()){
        pause();h.operations.close();h.busy=false;if(h.menus.extras.profilePopup!=null)h.menus.extras.profilePopup.dismiss();h.login();return;
      }
      if(!current()||generation!=readGeneration)return;reading=false;
      if(error==null){
        NativeCommander.sync(h,result==null?null:result.optJSONObject("commander"),owner,epoch);
        JSONObject statue=result==null?null:result.optJSONObject("statue");
        int stage=validatedStatueStage(statue);
        if(stage>=1&&stage<=5){statueStage=stage;loadCurrentStatue();}
      }
      residents.clear();
      JSONArray list=result==null?null:result.optJSONArray("residents");
      if(error!=null||list==null)problem="주민 부탁 상태를 불러오지 못했어요. 다시 확인할 수 있어요.";
      else {problem="";for(int i=0;i<list.length();i++){JSONObject npc=list.optJSONObject(i);if(npc!=null)residents.put(npc.optString("id"),npc);}}
      view.bindResidents();view.updateNotice();
    });
  }
  void loadCurrentStatue(){if(view!=null)view.assets.loadStatue(layout,statueStage,this::current,()->{if(current())view.invalidate();});}
  static int validatedStatueStage(JSONObject statue){
    Object raw=statue==null?null:statue.opt("stage");if(!(raw instanceof Number))return 0;
    double stage=((Number)raw).doubleValue();return stage>=1&&stage<=5&&stage==Math.rint(stage)?(int)stage:0;
  }
  String residentStatus(String id){JSONObject npc=residents.get(id);return npc==null?"LOCKED":npc.optString("status","LOCKED");}
  String residentName(String id,String fallback){JSONObject npc=residents.get(id);return npc==null?fallback:npc.optString("name",fallback);}
  void saveCamera(){if(view!=null&&!owner.isEmpty()){String position=Double.toString(view.camera.cameraUnits());if(!position.equals(savedCamera)&&preferences.edit().putString(scope+":camera",position).commit())savedCamera=position;}}
  void pause(){saveCamera();paused=true;readGeneration++;reading=false;if(view!=null){view.stopMotion();view.ambient.pause();view.assets.cancel();}if(menu!=null){menu.dismiss();menu=null;}}
  void resume(){
    if(!"home".equals(h.screen)||h.villageHome!=this)return;
    if(!owner.equals(h.api.token())){
      pause();h.operations.close();h.busy=false;String account=h.api.token();
      if(h.menus.extras.profilePopup!=null)h.menus.extras.profilePopup.dismiss();
      if(account.isEmpty())h.login();
      else if(Roster60StoryJournal.accountId(owner).equals(Roster60StoryJournal.accountId(account)))h.home();
      else {h.state=new JSONObject();h.menus.reset();h.expeditionDraft=null;h.expeditionDirty=false;h.currentKey="";h.load("home");}
      return;
    }
    if(epoch!=h.epoch)return;
    paused=false;if(view!=null){view.assets.setViewport(view.camera.cameraUnits(),view.field.width()>0?view.field.width()/view.unitPixels():1);view.assets.load(layout,this::current,()->{if(current()){view.updateNotice();view.invalidate();}});loadCurrentStatue();refresh();}
  }
  void resident(String id){
    if(!current()||h.busy||h.updateBlocks()||view.blocksClick())return;saveCamera();view.stopMotion();
    h.rosterStory=new NativeRoster60StoryController(h,id);h.rosterStory.openVillage();
  }
  void mainStory(){
    if(!current()||h.busy||h.updateBlocks()||view.blocksClick()||statueStage==0)return;saveCamera();view.stopMotion();
    h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillage();
  }
  void building(VillageLayout.Building building){
    if(!current()||h.busy||h.updateBlocks()||view.blocksClick())return;
    if(building.actionIds.length==1){navigate(building.actionIds[0]);return;}
    menu=new GameDialog(h).navy().setTitle(building.name).setItems(building.menu,(dialog,index)->{
      if(current()&&index<building.actionIds.length)navigate(building.actionIds[index]);
    }).setNegativeButton("닫기",null).show();
  }
  void navigate(String id){
    if(!current()||h.busy||h.updateBlocks()||view.blocksClick())return;saveCamera();
    switch(id){
      case "recruit":pause();h.load("draw");break;
      case "party":pause();h.team();break;
      case "collection":pause();h.collection();break;
      case "craft":pause();h.mix();break;
      case "mail":case "events":case "notices":int tab=id.equals("mail")?0:id.equals("events")?1:2;pause();h.menus.news.tab=tab;h.load("news");break;
      case "explore":pause();h.openRosterBattle(Roster60BattleMode.ADVENTURE);break;
      case "story":mainStory();break;
      case "departure":view.stopMotion();h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillageDeparture();break;
      case "normal":case "rank":pause();h.openRosterPvp(id);break;
      case "items":case "currency":pause();h.load("shop");break;
      case "pass":pause();h.load("playPass");break;
      case "daily":pause();h.load("daily");break;
      case "settings":h.menus.extras.settings();break;
      case "rebirth":pause();NativeProgression.openRosterRebirth(h);break;
      case "profile":h.menus.extras.profile(view.settings);break;
    }
  }
  void more(){
    if(!current()||view.blocksClick())return;
    String[] labels={"출석·미션","내 정보","환생 안내","설정","주민 부탁 다시 확인"};
    String[] actions={"daily","profile","rebirth","settings","refresh"};
    menu=new GameDialog(h).navy().setTitle("마을 메뉴").setItems(labels,(dialog,index)->{if(!current())return;if("refresh".equals(actions[index]))refresh();else navigate(actions[index]);}).setNegativeButton("닫기",null).show();
  }
  boolean back(){if(!current())return false;if(h.rosterStory!=null&&h.rosterStory.villageOverlayOpen()){h.rosterStory.back();return true;}if(menu!=null&&menu.isShowing()){menu.dismiss();menu=null;return true;}return false;}
}
