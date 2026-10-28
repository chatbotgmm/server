package org.dpdns.gmm;

import android.view.View;
import org.json.JSONObject;

/** Story adapter around the existing 60-species battle renderer and server action contract. */
final class NativeRoster60StoryBattle extends NativeRoster60Battle {
  JSONObject initial;
  final String questId;
  String battleId;
  final android.os.Handler reconnect=new android.os.Handler(android.os.Looper.getMainLooper());
  final Runnable checkOwnBattle=()->{if(current())resume();};
  boolean observedPending;
  NativeRoster60StoryBattle(MainActivity host,JSONObject initial,String questId) {
    super(host,Roster60BattleMode.STORY);this.initial=initial;this.questId=questId;battleId=initial.optString("battleId");
  }
  @Override String storageScope(){return "|story-v1";}
  @Override void resume(){
    presentationPaused=false;if(!current()||reading)return;
    key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+Roster60StoryJournal.accountId(owner)+storageScope());
    String pending=prefs.getString(key+":pending","");
    if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(Exception invalid){error("저장된 전투 요청 확인이 필요해요.");}return;}
    if(observedPending){observedPending=false;initial=null;if(!battleId.isEmpty())read(battleId);return;}
    if(initial!=null){JSONObject snapshot=initial;initial=null;bindCreated(snapshot);return;}
    if(!battleId.isEmpty())read(battleId);
  }
  @Override void send(JSONObject intent){
    JSONObject body=intent.optJSONObject("body");String id=body==null?"":body.optString("requestId");
    if(!id.isEmpty()&&IN_FLIGHT.contains(id)){observedPending=true;message.setText("전투 요청 결과를 확인하고 있어요.");reconnect.removeCallbacks(checkOwnBattle);reconnect.postDelayed(checkOwnBattle,500);return;}
    super.send(intent);
  }
  @Override void renderSnapshot(JSONObject result){
    super.renderSnapshot(result);if(!current()||view==null)return;battleId=result.optString("battleId",battleId);
    if("SETTLED".equals(result.optString("status"))){
      retry.setVisibility(View.VISIBLE);retry.setEnabled(true);retry.setText("마을로 돌아가기");retry.setOnClickListener(v->{if(current())returnToStory();});
      JSONObject receipt=result.optJSONObject("settlement");
      JSONObject drop=receipt==null?null:receipt.optJSONObject("story");
      if(drop!=null&&drop.optInt("darkGangAwarded")>0)stats.setText("어둠의 새우Gang +"+drop.optInt("darkGangAwarded")+" · 계정 지급 완료\n귀환한 뒤 주민과 이야기를 나눠요.");
    }
  }
  void returnToStory(){
    if(!current()||reading)return;
    JSONObject state=view==null?null:view.optJSONObject("state");String adventureId=state==null?"":state.optString("adventureId");
    if(adventureId.isEmpty()||"null".equals(adventureId)){leave();return;}
    reading=true;h.api.call("native/roster60/adventure/read",h.obj("adventureId",adventureId),(result,error)->{
      if(!current())return;reading=false;if(error!=null){error(error);return;}
      if("ACTIVE".equals(result.optString("status"))&&result.isNull("activeBattleId"))mutate("adventure/return",h.obj("adventureId",adventureId,"revision",result.optInt("revision")));
      else leave();
    });
  }
  @Override void showAdventure(JSONObject result){
    if(!current())return;
    if("RETURNED".equals(result.optString("status"))||"LOST".equals(result.optString("status"))){leave();return;}
    super.showAdventure(result);
  }
  @Override void options(){leave();}
  @Override void adventureResume(){returnToStory();}
  void leave(){pause();h.home();}
  @Override void pause(){reconnect.removeCallbacks(checkOwnBattle);super.pause();}
  @Override boolean back(){
    if(!current())return false;
    if(!choiceKind.isEmpty()){closeChoices();return true;}
    if(panel!=null){panel.cancelInteraction();if(panel.showCommands())return true;}
    // Leaving the view does not abandon or settle a battle. The server checkpoint is resumed later.
    leave();return true;
  }
}
