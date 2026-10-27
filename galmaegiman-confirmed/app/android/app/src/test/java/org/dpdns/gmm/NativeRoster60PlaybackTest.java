package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.widget.Button;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60PlaybackTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore(){helper.restore();}

 static class Api extends NativeRoster60ProductBattleTest.Api {
  int settlements;boolean failReads;
  Api(Context c)throws Exception{super(c);state=baseline();}
  @Override void call(String path,JSONObject body,Result cb){
   if(failReads&&(path.endsWith("battle/active")||path.endsWith("battle/read"))){cb.done(null,"연결하지 못했어요.");return;}
   if(path.endsWith("battle/settle")){settlements++;pending=cb;return;}
   super.call(path,body,cb);
  }
 }
 static JSONObject baseline()throws Exception{
  JSONObject r=new JSONObject(NativeRoster60ProductBattleTest.view().toString());
  JSONArray fighters=r.getJSONObject("state").getJSONArray("fighters");
  for(int i=0;i<2;i++){JSONObject f=fighters.getJSONObject(i);f.put("id",i==0?"self":"enemy");f.put("speciesId",i==0?"u01":"u02");f.put("level",5);}
  r.getJSONObject("state").put("events",new JSONArray());return r;
 }
 static JSONObject event(String actor,JSONArray before,JSONArray after,String target,int damage)throws Exception{
  return MainActivity.obj("actor",actor,"kind","skill","target",target,"damage",damage,"defense","normal",
   "presentation",MainActivity.obj("version",1,"before",new JSONArray(before.toString()),"after",new JSONArray(after.toString()),
    "skill",MainActivity.obj("id","s0","name","불꽃","element","FIRE","mpCost",2)));
 }
 static JSONObject response(JSONObject initial,boolean terminal)throws Exception{
  JSONObject r=new JSONObject(initial.toString());JSONArray before=r.getJSONObject("state").getJSONArray("fighters");
  JSONArray hit=new JSONArray(before.toString());hit.getJSONObject(0).put("hp",7);
  JSONArray after=new JSONArray(hit.toString());after.getJSONObject(1).put("hp",terminal?0:6);after.getJSONObject(0).put("mp",8);
  JSONArray events=new JSONArray().put(event("enemy",before,hit,"defender",3)).put(event("self",hit,after,"defender",terminal?10:4));
  r.put("revision",1);r.put("status",terminal?"FINISHED":"ACTIVE");JSONObject s=r.getJSONObject("state");
  s.put("turn",1);s.put("fighters",after);s.put("events",new JSONArray().put(MainActivity.obj("turn",0,"events",events)));
  if(terminal)s.put("outcome","win");return r;
 }
 static void finish(NativeRoster60Battle ui){
  for(int i=0;ui.presenting()&&i<8;i++){
   if(ui.playback.animator!=null)ui.playback.animator.end();
   ShadowLooper.idleMainLooper(181,TimeUnit.MILLISECONDS);
  }
  assertFalse(ui.presenting());
 }
 Api open(MainActivity h)throws Exception{
  helper.prepare(h);Api api=new Api(h);h.api=api;
  h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
  h.openRosterBattle();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);return api;
 }
 @Test public void actualResponseLocksInputAndDoesNotShowFinalHpBeforeImpact()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   NativeRoster60ProductBattleTest.named(ui.panel,"공격").performClick();assertEquals(1,api.actions.size());
   JSONObject next=response(api.state,false);api.state=next;api.pending.done(next,null);
   assertTrue(ui.presenting());assertEquals("enemy",ui.art.frameEvent.getString("actor"));
   assertEquals(10,ui.art.shownValue(0,"hp",-1));assertEquals(0,ui.view.getInt("revision"));
   for(Button b:NativeRoster60ProductBattleTest.buttons(ui.panel))assertFalse(b.isEnabled());
   ui.command(MainActivity.obj("kind","attack","battleId","test-battle","expectedRevision",0));
   assertEquals(1,api.actions.size());
   Object animator=ui.playback.animator;ui.bind(next);assertSame(animator,ui.playback.animator);
   finish(ui);assertEquals(1,ui.view.getInt("revision"));assertEquals(7,ui.art.snapshot.getJSONArray("fighters").getJSONObject(0).getInt("hp"));
   assertFalse(ui.panel.isSkillPage());assertTrue(NativeRoster60ProductBattleTest.named(ui.panel,"공격").isEnabled());
  }
 }
 @Test public void terminalSettlesOnlyAfterLastEffectAndInitialReconnectDoesNotReplay()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   JSONObject terminal=response(api.state,true);api.state=terminal;ui.bind(terminal);
   assertTrue(ui.presenting());assertEquals(0,api.settlements);finish(ui);
   // The final render schedules layout; View.post settlement waits for the next UI frame.
   ShadowLooper.idleMainLooper(16,TimeUnit.MILLISECONDS);assertEquals(1,api.settlements);
   ui.pause();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
   h.openRosterBattle();assertFalse(h.rosterBattle.presenting());
  }
 }
 @Test public void pauseAndLateResponseSynchronizeWithoutReplayingAndIgnoreStale()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   JSONObject initial=api.state,next=response(initial,false);ui.bind(next);assertTrue(ui.presenting());
   ui.pause();assertFalse(ui.presenting());ui.bind(next);assertFalse(ui.presenting());
   api.state=next;ui.resume();assertFalse(ui.presenting());assertEquals(1,ui.view.getInt("revision"));
   ui.bind(initial);assertEquals(1,ui.view.getInt("revision"));
  }
 }
 @Test public void idlePauseAndFailedResumeKeepOnlyAlreadyDisplayedKnowledge()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   api.state.getJSONObject("state").put("opponentIntel",NativeRoster60OpponentIntelTest.intel(0));ui.bind(api.state);
   assertFalse(ui.opponentInfoChanging);assertTrue(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("1 / 8"));
   ui.pause();api.failReads=true;ui.resume();
   assertFalse(ui.opponentInfoChanging);assertTrue(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("1 / 8"));
   assertFalse(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("속도  3"));
  }
 }
 @Test public void interruptedOrBackgroundUpdatedPlaybackStaysMaskedUntilAuthorizedRefresh()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   api.state.getJSONObject("state").put("opponentIntel",NativeRoster60OpponentIntelTest.intel(0));ui.bind(api.state);
   JSONObject next=response(api.state,false);next.getJSONObject("state").put("opponentIntel",NativeRoster60OpponentIntelTest.intel(1));
   ui.bind(next);assertTrue(ui.presenting());ui.pause();api.failReads=true;ui.resume();
   assertTrue(ui.opponentInfoChanging);assertTrue(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("0 / 8"));
   api.failReads=false;api.state=next;ui.resume();assertFalse(ui.opponentInfoChanging);
   assertTrue(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("2 / 8"));
   ui.pause();JSONObject newer=new JSONObject(next.toString());newer.put("revision",2);ui.bind(newer);
   assertTrue(ui.opponentInfoChanging);assertTrue(NativeRoster60OpponentIntelTest.text(ui.opponentDetails).contains("0 / 8"));
  }
 }
 @Test public void legacyServerStillPlaysEffectsWithoutInventingIntermediateHp()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   JSONObject next=response(api.state,false);
   JSONArray events=next.getJSONObject("state").getJSONArray("events").getJSONObject(0).getJSONArray("events");
   for(int i=0;i<events.length();i++)events.getJSONObject(i).remove("presentation");
   ui.bind(next);assertTrue(ui.presenting());assertTrue(ui.art.legacyFrame());
   assertEquals("enemy",ui.art.frameEvent.getString("actor"));
   ui.art.frame(ui.art.frameBefore,ui.art.frameAfter,ui.art.frameEvent,.85f);
   assertEquals(10,ui.art.shownValue(0,"hp",-1));
   Object animator=ui.playback.animator;ui.bind(next);assertSame(animator,ui.playback.animator);
   finish(ui);assertEquals(7,ui.view.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getInt("hp"));
  }
 }
 @Test public void legacySwitchMasksUnverifiedFighterUntilFinalSnapshot()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   JSONObject next=new JSONObject(api.state.toString());next.put("revision",1);
   JSONObject state=next.getJSONObject("state");state.getJSONArray("fighters").getJSONObject(0).put("speciesId","u03");
   state.getJSONArray("fighters").getJSONObject(0).put("hp",7);
   state.put("events",new JSONArray().put(MainActivity.obj("turn",0,"events",new JSONArray()
     .put(MainActivity.obj("actor","self","kind","switch"))
     .put(MainActivity.obj("actor","enemy","kind","skill","target","defender","damage",3,"defense","normal")))));
   ui.bind(next);assertTrue(ui.presenting());assertTrue(ui.art.uncertain(0));assertFalse(ui.art.isSwitch());
   assertEquals("u01",ui.art.snapshot.getJSONArray("fighters").getJSONObject(0).getString("speciesId"));
   finish(ui);assertEquals("u03",ui.art.snapshot.getJSONArray("fighters").getJSONObject(0).getString("speciesId"));
  }
 }
 @Test public void sameRevisionOldPvpPollCannotUnlockSubmittedCommand()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();open(h);JSONObject initial=baseline();h.openRosterHumanBattle(initial);
   NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   JSONObject waiting=new JSONObject(initial.toString());waiting.put("waitingForOpponent",true);ui.bind(waiting);
   ui.bind(initial);
   assertTrue(ui.view.getBoolean("waitingForOpponent"));
   assertFalse(NativeRoster60ProductBattleTest.named(ui.panel,"공격").isEnabled());
   ui.pause();
  }
 }
 @Test public void nextAdventureEncounterResetsOnlyOnSuccessfulCreateAndRejectsOldResponse()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=open(h);NativeRoster60Battle ui=h.rosterBattle;
   JSONObject old=api.state,next=baseline();next.put("battleId","next-adventure-encounter");
   ui.bindCreated(next);assertEquals("next-adventure-encounter",ui.view.getString("battleId"));
   assertFalse(ui.presenting());ui.bind(old);
   assertEquals("next-adventure-encounter",ui.view.getString("battleId"));
  }
 }
 @Test public void reflectionAndFocusInterpolateOnlyAuthoritativeTarget()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();open(h);NativeRoster60Art art=h.rosterBattle.art;
   JSONObject before=baseline().getJSONObject("state"),after=new JSONObject(before.toString());
   after.getJSONArray("fighters").getJSONObject(0).put("hp",4);
   JSONObject reflected=event("self",before.getJSONArray("fighters"),after.getJSONArray("fighters"),"attacker",6);reflected.put("defense","reflect");
   art.frame(before,after,reflected,.5f);assertEquals(10,art.shownValue(0,"hp",-1));assertEquals(0,art.targetSide());
   art.frame(before,after,reflected,.85f);assertEquals(4,art.shownValue(0,"hp",-1));assertEquals(10,art.shownValue(1,"hp",-1));
   assertEquals(10,before.getJSONArray("fighters").getJSONObject(0).getInt("hp"));
   art.clearFrame();assertNull(art.frameEvent);
  }
 }
}
