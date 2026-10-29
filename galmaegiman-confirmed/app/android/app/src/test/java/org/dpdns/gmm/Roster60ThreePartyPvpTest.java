package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Admission uses the three-party contract; historical rooms retain their real snapshot. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ThreePartyPvpTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeRoster60HumanPvpTest.SMALL);}
  @After public void restore(){helper.restore();}

  static ArrayList<String> ids(int count){ArrayList<String> result=new ArrayList<>();for(int i=0;i<count;i++)result.add("owned"+i);return result;}
  static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception bad){throw new AssertionError(bad);}}
  static JSONObject contract(JSONObject value,boolean required){
    try{
      value.put("minParty",1).put("maxParty",3).put("partyPolicy",MainActivity.obj(
          "version","single-active-party-3-v1","minParty",1,"maxParty",3,"activeSlots",1,"sizeRule","up-to-max"));
      value.put("partySelectionRequired",required).put("partyCondition",required?"PARTY_RESELECTION_REQUIRED":JSONObject.NULL);
      return value;
    }catch(Exception bad){throw new AssertionError(bad);}
  }
  static JSONObject legacySixBattle()throws Exception{
    JSONObject battle=NativeRoster60HumanPvpTest.battle(),state=battle.getJSONObject("state");
    state.getJSONArray("fighters").getJSONObject(0).put("ownedId","owned0");
    JSONArray reserves=new JSONArray();
    for(int i=1;i<6;i++){JSONObject reserve=copy(state.getJSONArray("fighters").getJSONObject(0));reserve.put("ownedId","owned"+i).put("name","기존 예비 "+i);reserves.put(reserve);}
    state.put("reserves",reserves);return battle;
  }

  static class Api extends NativeApi {
    final ArrayList<String> paths=new ArrayList<>(),writePaths=new ArrayList<>(),saved=new ArrayList<>();
    final ArrayList<JSONObject> writes=new ArrayList<>();
    int ownedCount=7;
    boolean missingPolicy,delayParty;
    String partyError;
    JSONObject current=MainActivity.obj("status","idle"),partyBody;
    Result pendingParty,pendingJoin,pendingCancel;
    Api(Context context,int count){super(context);saved.addAll(ids(count));}
    @Override String token(){return "three-party-pvp.1.signature";}
    boolean required(){return saved.isEmpty()||saved.size()>3;}
    JSONObject policy(JSONObject value){return missingPolicy?value:contract(value,required());}
    JSONObject options(){
      JSONArray units=new JSONArray();
      for(int i=0;i<ownedCount;i++)units.put(MainActivity.obj("ownedId","owned"+i,"speciesId","u01","name","개체 "+i,"level",60-i,"maxLevel",60,"rankEligible",true,"rankReason",""));
      return policy(MainActivity.obj("units",units,"partyIds",new JSONArray(saved),"rankEligibility",NativePlayNavigationTest.rankEligibility(60,true,"")));
    }
    JSONObject currentResponse(){JSONObject value=copy(current);return value.optJSONObject("battle")!=null?value:policy(value);}
    @Override void call(String path,JSONObject body,Result callback){
      paths.add(path);
      if(path.equals("native/roster60/story/state")){callback.done(NativeRoster60StoryTest.state(),null);return;}
      if(path.equals("native/roster60/pvp/options")){callback.done(options(),null);return;}
      if(path.equals("native/roster60/pvp/current")){callback.done(currentResponse(),null);return;}
      if(body!=null){writePaths.add(path);writes.add(copy(body));}
      if(path.equals("native/roster60/pvp/party")){
        partyBody=copy(body);pendingParty=callback;
        if(!delayParty)completeParty(partyError);
        return;
      }
      if(path.equals("native/roster60/pvp/join")){pendingJoin=callback;return;}
      if(path.equals("native/roster60/pvp/cancel")){pendingCancel=callback;return;}
      throw new AssertionError("Unexpected three-party test request: "+path);
    }
    void completeParty(String error){
      Result next=pendingParty;assertNotNull(next);pendingParty=null;
      if(error!=null){next.done(null,error);return;}
      saved.clear();JSONArray selected=partyBody.optJSONArray("ownedIds");for(int i=0;selected!=null&&i<selected.length();i++)saved.add(selected.optString(i));
      next.done(policy(MainActivity.obj("partyIds",new JSONArray(saved))),null);
    }
    void completeJoin(String error){
      Result next=pendingJoin;assertNotNull(next);pendingJoin=null;
      if(error!=null){next.done(null,error);return;}
      current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000,"serverNow",1000,"aiAvailableAt",31000);
      next.done(currentResponse(),null);
    }
    void completeCancel(){Result next=pendingCancel;assertNotNull(next);pendingCancel=null;current=MainActivity.obj("status","idle");next.done(currentResponse(),null);}
    int count(String suffix){int result=0;for(String path:paths)if(path.equals("native/roster60/pvp/"+suffix))result++;return result;}
  }

  Api prepare(MainActivity h,int count)throws Exception{
    helper.prepare(h);h.api.close();Api api=new Api(h,count);h.api=api;
    h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
    return api;
  }
  NativeRoster60Pvp open(MainActivity h){h.openRosterPvp("normal");helper.layout(h,NativeRoster60HumanPvpTest.SMALL);return h.rosterPvp;}
  void edit(NativeRoster60Pvp ui){if(!ui.editing)ui.editParty.performClick();assertTrue(ui.editing);}
  void removeLastUntilThree(NativeRoster60Pvp ui){
    while(ui.selected.size()>3){int last=ui.selected.size()-1;((Roster60MemberTile)ui.slots.getChildAt(last)).action.performClick();}
    assertEquals(ids(3),ui.selected);
  }

  @Test public void validThreeMemberPartyProceedsOnceWithoutOpeningEditorOrReordering()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,3);api.saved.clear();api.saved.addAll(Arrays.asList("owned2","owned0","owned1"));NativeRoster60Pvp ui=open(h);
      assertEquals(api.saved,ui.selected);assertFalse(ui.editing);assertTrue(ui.normal.isEnabled());assertEquals(3,ui.slots.getChildCount());
      ui.normal.performClick();ui.normal.performClick();ui.join("normal");
      assertEquals(1,api.count("party"));assertEquals(1,api.count("join"));assertEquals("[\"owned2\",\"owned0\",\"owned1\"]",api.writes.get(0).getJSONArray("ownedIds").toString());
      assertFalse(ui.prefs.getString(ui.key,"").isEmpty());api.completeJoin(null);assertTrue(ui.waiting);ui.pause();
    }
  }

  @Test public void oneOrTwoOwnedMembersCanSaveAndJoinWithoutInventingMissingMembers()throws Exception{
    for(int count:new int[]{1,2})try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,count);api.ownedCount=count;NativeRoster60Pvp ui=open(h);
      assertTrue(ui.normal.isEnabled());ui.normal.performClick();assertEquals(count,api.writes.get(0).getJSONArray("ownedIds").length());assertEquals(1,api.count("join"));
      assertEquals(ids(count),ui.selected);api.completeJoin(null);ui.pause();
    }
  }

  @Test public void savedSixRequiresExplicitReselectionAndCancelRestoresAllSixWithoutWrites()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);NativeRoster60Pvp ui=open(h);
      assertEquals(ids(6),ui.savedIds);assertEquals(ids(6),ui.selected);assertTrue(ui.repairRequired);assertFalse(ui.normal.isEnabled());assertEquals(6,ui.slots.getChildCount());
      edit(ui);((Roster60MemberTile)ui.slots.getChildAt(0)).action.performClick();assertEquals(5,ui.selected.size());ui.back();
      assertEquals(ids(6),ui.selected);assertEquals(ids(6),ui.savedIds);assertEquals(6,ui.slots.getChildCount());assertFalse(ui.normal.isEnabled());assertTrue(api.writes.isEmpty());ui.pause();
    }
  }

  @Test public void removingThreeFromLegacySixSavesOnlyTheExplicitOrderAndNeverStartsAMatch()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);NativeRoster60Pvp ui=open(h);edit(ui);removeLastUntilThree(ui);api.delayParty=true;
      ui.save.performClick();ui.save();assertEquals(1,api.count("party"));assertTrue(ui.busy);assertEquals(ids(6),api.saved);assertEquals(ids(6),ui.savedIds);
      api.completeParty(null);assertEquals(ids(3),api.saved);assertEquals(ids(3),ui.savedIds);assertEquals(3,ui.slots.getChildCount());assertFalse(ui.repairRequired);assertFalse(ui.editing);assertTrue(ui.normal.isEnabled());assertEquals(0,api.count("join"));ui.pause();
    }
  }

  @Test public void missingPolicyBlocksNewSubmissionsButCannotBlockAnExistingSixMemberRoom()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,3);api.missingPolicy=true;NativeRoster60Pvp ui=open(h);
      assertFalse(ui.normal.isEnabled());assertFalse(ui.save.isEnabled());ui.join("normal");ui.save();assertTrue(api.writes.isEmpty());
      assertTrue(ui.status.getText().toString().contains("서버"));assertTrue(ui.status.getText().toString().contains("편성"));ui.pause();
    }
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);api.missingPolicy=true;JSONObject existing=legacySixBattle();api.current=MainActivity.obj("status","matched","mode","normal","battle",existing);open(h);
      assertEquals("roster60Battle",h.screen);assertTrue(h.rosterBattle instanceof NativeRoster60PvpBattle);NativeRoster60PvpBattle battle=(NativeRoster60PvpBattle)h.rosterBattle;
      RegistrationArtTest.await(()->battle.art.assetsReady());assertEquals(5,battle.view.getJSONObject("state").getJSONArray("reserves").length());
      NativeRoster60ProductBattleTest.named(battle.panel,"기술").performClick();assertNotNull(NativeRoster60ProductBattleTest.named(battle.panel,"시험기술5"));
      assertTrue(api.writes.isEmpty());assertEquals(0,api.count("party"));assertEquals(0,api.count("join"));battle.pause();
    }
  }

  @Test public void oldSixMemberWaitMustCancelBeforeAnyNewPartyOrJoin()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);api.current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000,"serverNow",2000,"aiAvailableAt",31000);NativeRoster60Pvp ui=open(h);
      assertTrue(ui.waiting);assertEquals(ids(6),ui.selected);assertFalse(ui.editParty.isEnabled());assertTrue(ui.cancel.isEnabled());
      ui.save();ui.join("normal");assertTrue(api.writes.isEmpty());ui.cancel.performClick();assertEquals(Arrays.asList("native/roster60/pvp/cancel"),api.writePaths);
      ui.save();ui.join("normal");assertEquals(1,api.writes.size());api.completeCancel();assertFalse(ui.waiting);assertEquals(ids(6),ui.selected);
      edit(ui);removeLastUntilThree(ui);ui.save.performClick();assertEquals(ids(3),api.saved);ui.normal.performClick();
      assertTrue(api.writePaths.indexOf("native/roster60/pvp/cancel")<api.writePaths.indexOf("native/roster60/pvp/party"));
      assertTrue(api.writePaths.indexOf("native/roster60/pvp/party")<api.writePaths.indexOf("native/roster60/pvp/join"));assertEquals(1,api.count("cancel"));assertEquals(1,api.count("join"));api.completeJoin(null);ui.pause();
    }
  }

  @Test public void partyReselectionRejectionClearsItsPendingIntentAndRestoresEditing()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,3);api.delayParty=true;NativeRoster60Pvp ui=open(h);edit(ui);ui.save.performClick();
      assertFalse(ui.prefs.getString(ui.key,"").isEmpty());api.completeParty("PARTY_RESELECTION_REQUIRED");
      assertTrue(ui.prefs.getString(ui.key,"").isEmpty());assertFalse(ui.busy);assertTrue(ui.editing);assertTrue(ui.repairRequired);assertFalse(ui.normal.isEnabled());assertEquals(ids(3),ui.savedIds);assertEquals(0,api.count("join"));ui.pause();
    }
  }

  @Test public void historicalJoinReselectionIsAcknowledgedBeforeEditingAndNeverAutomaticallyRejoins()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);String key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+api.token());
      JSONObject intent=MainActivity.obj("op","join","mode","normal","body",MainActivity.obj("requestId","legacy-join-intent","mode","normal"));
      h.getSharedPreferences("roster60_human_queue",0).edit().putString(key,intent.toString()).commit();NativeRoster60Pvp ui=open(h);
      assertEquals(1,api.count("join"));assertEquals("legacy-join-intent",api.writes.get(0).getString("requestId"));assertFalse(ui.prefs.getString(ui.key,"").isEmpty());
      api.completeJoin("PARTY_RESELECTION_REQUIRED");assertTrue(ui.prefs.getString(ui.key,"").isEmpty());assertFalse(ui.busy);assertTrue(ui.repairRequired);assertTrue(ui.editing);assertFalse(ui.normal.isEnabled());
      ui.resume();assertEquals(1,api.count("join"));assertEquals(ids(6),ui.savedIds);assertEquals(ids(6),ui.selected);ui.pause();
    }
  }

  @Test public void terminalLegacySixRoomShowsReselectionNoticeWithoutRequeueOrTruncation()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h,6);JSONObject ended=legacySixBattle();ended.put("status","SETTLED").put("requeued",false).put("settlement",MainActivity.obj("xpAwarded",0,"reason","ACCEPT_TIMEOUT","partySelectionRequired",true,"partyCondition","PARTY_RESELECTION_REQUIRED"));
      h.openRosterHumanBattle(ended);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
      assertEquals("roster60Battle",h.screen);assertEquals(5,ui.view.getJSONObject("state").getJSONArray("reserves").length());assertTrue(ui.message.getText().toString().contains("편성"));assertTrue(ui.message.getText().toString().contains("3"));
      assertEquals(0,api.count("join"));assertTrue(api.writes.isEmpty());ui.pause();
    }
  }
}
