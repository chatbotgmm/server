package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ThreePartyTransportTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);NativeRoster60Battle.IN_FLIGHT.clear();NativeRoster60StoryController.IN_FLIGHT.clear();}
  @After public void restore(){helper.restore();NativeRoster60Battle.IN_FLIGHT.clear();NativeRoster60StoryController.IN_FLIGHT.clear();}
  static JSONObject copyJson(JSONObject value){return NativeRealtime.copy(value);}
  static JSONObject legacySix()throws Exception{return Roster60ThreePartyPvpTest.legacySixBattle();}
  static JSONObject run()throws Exception{
    JSONArray party=new JSONArray();JSONObject battle=legacySix(),state=battle.getJSONObject("state");party.put(copyJson(state.getJSONArray("fighters").getJSONObject(0)));
    JSONArray reserve=state.getJSONArray("reserves");for(int i=0;i<reserve.length();i++)party.put(copyJson(reserve.getJSONObject(i)));
    return MainActivity.obj("adventureId","legacy-adventure-six","revision",4,"status","ACTIVE","party",party,"activeBattleId",JSONObject.NULL,"escrow",MainActivity.obj("gold",0,"xp",new JSONObject()));
  }
  static class Api extends NativeApi {
    final ArrayList<String> paths=new ArrayList<>(),writePaths=new ArrayList<>();final ArrayList<JSONObject> writes=new ArrayList<>();
    JSONObject active,adventure,oldCreateReceipt,story=NativeRoster60StoryTest.state();Result pending;boolean oldOptions;
    Api(Context c){super(c);}
    @Override String token(){return "three-party-transport.1.signature";}
    @Override void call(String path,JSONObject body,Result callback){
      paths.add(path);
      if(path.equals("native/roster60/story/state")){callback.done(copyJson(story),null);return;}
      if(path.equals("native/roster60/pvp/options")){callback.done(Roster60PartyPolicyTest.response(MainActivity.obj("units",Roster60ThreePartyPreparationTest.units(),"partyIds",Roster60ThreePartyPreparationTest.ids(3)),false),null);return;}
      if(path.equals("native/roster60/battle/options")){
        JSONObject response=MainActivity.obj("units",Roster60ThreePartyPreparationTest.units(),"encounters",new JSONArray().put(MainActivity.obj("id","adventure-encounter","name","기존 모험 조우","mode","adventure")));
        callback.done(oldOptions?response:Roster60PartyPolicyTest.response(response,false),null);return;
      }
      if(path.equals("native/roster60/battle/active")){callback.done(MainActivity.obj("battles",active==null?new JSONArray():new JSONArray().put(active)),null);return;}
      if(path.equals("native/roster60/battle/read")){callback.done(active,null);return;}
      if(path.equals("native/roster60/adventure/active")){callback.done(MainActivity.obj("adventures",adventure==null?new JSONArray():new JSONArray().put(adventure)),null);return;}
      if(path.equals("native/roster60/adventure/read")){callback.done(adventure,null);return;}
      if(body!=null){writePaths.add(path);writes.add(copyJson(body));pending=callback;
        if(path.equals("native/roster60/pvp/party"))callback.done(Roster60PartyPolicyTest.response(MainActivity.obj("partyIds",body.optJSONArray("ownedIds")),false),null);
        else if(path.equals("native/roster60/battle/create")&&oldCreateReceipt!=null)callback.done(oldCreateReceipt,null);
        return;
      }
      throw new AssertionError("Unexpected transport request "+path);
    }
  }
  Api prepare(MainActivity h)throws Exception{helper.prepare(h);h.api.close();Api api=new Api(h);h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.getSharedPreferences("roster60_story_v1",0).edit().clear().commit();return api;}
  NativeRoster60Battle blank(MainActivity h){NativeRoster60Battle battle=new NativeRoster60Battle(h){boolean initial=true;@Override void resume(){if(initial){initial=false;return;}super.resume();}};h.rosterBattle=battle;battle.open();return battle;}
  @Test public void definitiveBattleRejectionClearsItsIntentBeforeUnlockingReselection()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60Battle battle=blank(h);battle.prepareParty(Roster60ThreePartyPreparationTest.units(),MainActivity.obj("id","story-encounter","mode","story"));
      battle.preparation.start.performClick();battle.preparation.start.performClick();assertEquals(1,api.writes.size());assertFalse(battle.prefs.getString(battle.key+":pending","").isEmpty());
      api.pending.done(null,"PARTY_RESELECTION_REQUIRED");assertTrue(battle.prefs.getString(battle.key+":pending","").isEmpty());assertFalse(battle.preparation.locked);assertNull(battle.preparation.retry);assertTrue(battle.preparation.edit.isEnabled());assertFalse(battle.preparation.start.isEnabled());assertTrue(battle.preparation.notice.getText().toString().contains("최대 3"));
      battle.preparation.start.performClick();assertEquals(1,api.writes.size());battle.preparation.edit.performClick();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);battle.preparation.formation.start.performClick();assertEquals(2,api.writes.size());assertNull(battle.preparation.formation);battle.preparation.start.performClick();assertEquals(3,api.writes.size());assertNotEquals(api.writes.get(0).getString("requestId"),api.writes.get(2).getString("requestId"));battle.pause();
    }
  }
  @Test public void uncertainBattleCreateKeepsTheSameRequestAndCannotOpenAnotherEditor()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60Battle battle=blank(h);battle.prepareParty(Roster60ThreePartyPreparationTest.units(),MainActivity.obj("id","story-encounter","mode","story"));battle.preparation.start.performClick();String original=api.writes.get(0).toString();
      api.pending.done(null,"network");assertFalse(battle.preparation.edit.isEnabled());assertNotNull(battle.preparation.retry);assertFalse(battle.prefs.getString(battle.key+":pending","").isEmpty());battle.preparation.start.performClick();battle.preparation.start.performClick();assertEquals(2,api.writes.size());assertEquals(original,api.writes.get(1).toString());battle.pause();
    }
  }
  @Test public void missingBattlePolicyCannotCreateButAnActiveSixSnapshotStillResumes()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.oldOptions=true;NativeRoster60Battle battle=blank(h);battle.options();assertNull(battle.preparation);assertNull(battle.formation);assertTrue(battle.message.getText().toString().contains("업데이트"));assertTrue(api.writes.isEmpty());battle.pause();
    }
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.oldOptions=true;api.active=legacySix();h.openRosterBattle();NativeRoster60Battle battle=h.rosterBattle;RegistrationArtTest.await(()->battle.art.assetsReady());
      assertEquals(5,battle.view.getJSONObject("state").getJSONArray("reserves").length());assertNull(battle.preparation);assertFalse(api.paths.contains("native/roster60/pvp/options"));assertFalse(api.paths.contains("native/roster60/battle/options"));assertTrue(api.writes.isEmpty());battle.pause();
    }
  }
  @Test public void unacknowledgedOldSixCreateReplaysItsOriginalReceiptWithoutNewOptions()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.oldOptions=true;api.active=legacySix();api.oldCreateReceipt=api.active;
      JSONArray reserves=new JSONArray();for(int i=1;i<6;i++)reserves.put("owned"+i);
      JSONObject body=MainActivity.obj("requestId","old-six-create-request","ownedId","owned0","reserveIds",reserves,"encounterId","old-encounter");
      String key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+api.token());h.getSharedPreferences("roster60_product_battle",0).edit().putString(key+":pending",MainActivity.obj("operation","create","body",body).toString()).commit();
      h.openRosterBattle();assertEquals(1,api.writes.size());assertEquals(body.toString(),api.writes.get(0).toString());assertEquals(5,h.rosterBattle.view.getJSONObject("state").getJSONArray("reserves").length());assertTrue(h.rosterBattle.prefs.getString(key+":pending","").isEmpty());assertFalse(api.paths.contains("native/roster60/battle/options"));assertFalse(api.paths.contains("native/roster60/pvp/options"));
      h.rosterBattle.pause();h.openRosterBattle();assertEquals(1,api.writes.size());assertEquals(5,h.rosterBattle.view.getJSONObject("state").getJSONArray("reserves").length());h.rosterBattle.pause();
    }
  }
  @Test public void activeSixAdventureNextEncounterSubmitsItsWholeOriginalParty()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.adventure=run();h.openRosterBattle(Roster60BattleMode.ADVENTURE);NativeRoster60Battle battle=h.rosterBattle;assertEquals(6,battle.adventure.getJSONArray("party").length());
      battle.chooseAdventureBattle();org.robolectric.Shadows.shadowOf(battle.selection).clickOnItem(0);org.robolectric.Shadows.shadowOf(battle.selection).clickOnItem(0);
      assertEquals(1,api.writes.size());JSONObject body=api.writes.get(0);assertEquals("legacy-adventure-six",body.getString("adventureId"));assertEquals("owned0",body.getString("ownedId"));assertEquals(5,body.getJSONArray("reserveIds").length());assertEquals("owned5",body.getJSONArray("reserveIds").getString(4));assertFalse(api.paths.contains("native/roster60/pvp/options"));assertNull(battle.preparation);battle.pause();
    }
  }
  @Test public void storyAndMaterialDepartureReselectionAcknowledgeBeforeUnlock()throws Exception{
    for(String encounter:new String[]{"","material-encounter"})try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryTest.openLegacy(h);NativeRoster60StoryController story=h.rosterStory;JSONObject quest=NativeRoster60StoryTest.quest("ACTIVE");story.chooseParty(quest,encounter);assertNotNull(story.preparation);story.preparation.start.performClick();assertEquals(1,api.writes.size());assertNotNull(story.journal.pending());
      assertEquals(encounter.isEmpty()?"native/roster60/story/battle/create":"native/roster60/story/adventure/create",api.writePaths.get(0));api.pending.done(null,"PARTY_RESELECTION_REQUIRED");
      assertNull(story.journal.pending());assertFalse(story.waiting());assertFalse(story.preparation.locked);assertTrue(story.preparation.edit.isEnabled());assertFalse(story.preparation.start.isEnabled());assertNull(story.preparation.retry);assertTrue(story.preparation.notice.getText().toString().contains("최대 3"));story.preparation.start.performClick();assertEquals(1,api.writes.size());story.pause();
    }
  }
  @Test public void storySixAdventureContinuationBypassesNewSelectionAndPreservesTheSnapshot()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.adventure=run();String original=api.adventure.toString();NativeRoster60StoryTest.openLegacy(h);NativeRoster60StoryController story=h.rosterStory;story.resumeAdventure(NativeRoster60StoryTest.quest("ACTIVE"),"legacy-adventure-six");
      assertEquals(1,api.writes.size());JSONObject body=api.writes.get(0);assertEquals(6,body.getJSONArray("ownedIds").length());assertEquals("owned5",body.getJSONArray("ownedIds").getString(5));assertEquals("legacy-adventure-six",body.getString("adventureId"));assertEquals(original,api.adventure.toString());assertFalse(api.paths.contains("native/roster60/pvp/options"));assertFalse(api.paths.contains("native/roster60/battle/options"));assertNull(story.preparation);story.pause();
    }
  }
}
