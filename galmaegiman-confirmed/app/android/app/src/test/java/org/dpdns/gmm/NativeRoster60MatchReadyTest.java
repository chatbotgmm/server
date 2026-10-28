package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60MatchReadyTest {
 final NativeRoster60HumanPvpTest helper=new NativeRoster60HumanPvpTest();
 @Before public void setup(){helper.setup();} @After public void restore(){helper.restore();}
 static JSONObject ready(String mode,String opponent)throws Exception{return NativeRoster60HumanPvpTest.battle().put("status","ACCEPTING").put("mode",mode).put("opponentKind",opponent).put("accepted",false).put("serverNow",1000).put("deadline",11000);}
 NativeRoster60HumanPvpTest.Api prepare(MainActivity h)throws Exception{helper.helper.prepare(h);NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;helper.clear(h);return api;}
 @Test public void acceptanceStaysOutsideBattleUntilServerActive()throws Exception{
  for(String mode:new String[]{"normal","rank"})try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject state=ready(mode,"normal".equals(mode)?"AI":"HUMAN");h.openRosterHumanBattle(state);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   assertTrue(ui.preparing);assertNull(ui.art);assertNull(ui.panel);assertNull(h.root.findViewWithTag("roster60-battle-art"));assertTrue(api.writes.isEmpty());
   ui.accept.performClick();assertEquals(1,api.writes.size());assertTrue(api.writes.get(0).getBoolean("accept"));
   JSONObject accepted=new JSONObject(state.toString()).put("accepted",true);api.pending.done(MainActivity.obj("battle",accepted),null);
   assertTrue(ui.preparing);assertNull(ui.art);assertFalse(ui.accept.isEnabled());
   ui.bind(new JSONObject(state.toString()));assertTrue(ui.view.getBoolean("accepted"));
   JSONObject active=new JSONObject(accepted.toString()).put("status","ACTIVE").put("revision",state.optInt("revision")+1);ui.bind(active);
   assertFalse(ui.preparing);assertNotNull(ui.art);assertNotNull(ui.panel);assertNull(h.root.findViewWithTag("roster60-match-ready"));assertEquals(1,api.writes.size());ui.bind(state);assertFalse(ui.preparing);assertEquals("ACTIVE",ui.view.optString("status"));ui.pause();
  }
 }
 @Test public void declineAndDeadlineNeverStartCombatOrAutoAccept()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);h.openRosterHumanBattle(ready("normal","AI"));NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   ui.readyView.remainingAtReceipt=0;ui.readyView.refresh();assertFalse(ui.accept.isEnabled());assertTrue(api.writes.isEmpty());assertNull(ui.art);
   ui.decline.performClick();assertEquals(1,api.writes.size());assertFalse(api.writes.get(0).getBoolean("accept"));
   JSONObject ended=ready("normal","AI").put("status","SETTLED");api.pending.done(MainActivity.obj("battle",ended),null);
   assertTrue(ui.preparing);assertNull(ui.art);assertTrue(ui.message.getText().toString().contains("시작되지"));ui.pause();
  }
 }
 @Test public void failedAcceptanceRetriesSameReceiptWithoutLoadingBattle()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject state=ready("normal","HUMAN");h.openRosterHumanBattle(state);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   ui.accept.performClick();String id=api.writes.get(0).getString("requestId");api.pending.done(null,"연결하지 못했어요.");assertNull(ui.art);assertFalse(ui.prefs.getString(ui.key+":pending","").isEmpty());
   ui.retry.performClick();assertEquals(id,api.writes.get(1).getString("requestId"));api.pending.done(MainActivity.obj("battle",new JSONObject(state.toString()).put("accepted",true)),null);assertNull(ui.art);assertTrue(ui.prefs.getString(ui.key+":pending","").isEmpty());ui.pause();
  }
 }
 @Test public void lateAcceptanceAfterLeavingCannotReplaceHome()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);h.openRosterHumanBattle(ready("normal","AI"));NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;ui.accept.performClick();NativeApi.Result response=api.pending;
   ui.pause();h.epoch++;h.screen="home";response.done(MainActivity.obj("battle",NativeRoster60HumanPvpTest.battle().put("status","ACTIVE")),null);assertEquals("home",h.screen);assertNull(ui.art);assertEquals(1,api.writes.size());
  }
 }
 @Test public void readyScreenRendersWithoutBattleResources()throws Exception{
  NativeHomePartyFeedbackTest.Profile profile=NativeHomePartyFeedbackTest.PROFILES[0];helper.helper.configure(profile);
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();prepare(h);h.openRosterHumanBattle(ready("normal","AI"));helper.helper.settle(h,profile);
   Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try{helper.helper.png(bitmap,"match-ready-before-battle.png");}finally{bitmap.recycle();}h.rosterBattle.pause();
  }
 }
 @Test public void terminalPollBeforeAcceptanceAckStillSettlesExactlyOnce()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   ui.accept.performClick();NativeApi.Result acceptReply=api.pending;
   JSONObject finished=new JSONObject(waiting.toString()).put("status","FINISHED").put("revision",2);ui.bind(finished);
   assertEquals(1,api.writes.size());assertEquals("",ui.autoSettled);assertNull(ui.art);
   acceptReply.done(MainActivity.obj("battle",waiting),null);ui.bind(finished);
   assertEquals(2,api.writes.size());assertTrue(api.paths.get(api.paths.size()-1).endsWith("pvp/settle"));ui.bind(finished);assertEquals(2,api.writes.size());
   api.pending.done(MainActivity.obj("battle",new JSONObject(finished.toString()).put("status","SETTLED")),null);assertEquals("SETTLED",ui.view.optString("status"));ui.pause();
  }
 }
 @Test public void delayedActiveAndEqualRevisionFinishedCannotRegressReadyTerminal()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject waiting=ready("normal","HUMAN");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   ui.bind(new JSONObject(waiting.toString()).put("status","SETTLED").put("revision",3));
   ui.bind(new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2));
   ui.bind(new JSONObject(waiting.toString()).put("status","FINISHED").put("revision",3));
   assertTrue(ui.preparing);assertNull(ui.art);assertEquals("SETTLED",ui.view.optString("status"));assertFalse(ui.message.getText().toString().contains("저장 중"));assertTrue(api.writes.isEmpty());ui.pause();
  }
 }
 @Test public void acceptanceActiveDoesNotWaitForOrGetOverwrittenByOlderCurrentRead()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);final NativeApi.Result[] held={null};
   NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h){@Override void call(String path,JSONObject body,Result callback){if(path.endsWith("pvp/current")){paths.add(path);held[0]=callback;return;}super.call(path,body,callback);}};h.api=api;helper.clear(h);
   JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;ui.resume();assertTrue(ui.reading);
   ui.accept.performClick();api.pending.done(MainActivity.obj("battle",new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2)),null);
   assertFalse(ui.preparing);assertFalse(ui.reading);assertNotNull(ui.panel);assertEquals("ACTIVE",ui.view.optString("status"));
   held[0].done(MainActivity.obj("battle",waiting),null);assertFalse(ui.reading);assertEquals("ACTIVE",ui.view.optString("status"));ui.pause();
  }
 }

 @Test public void pausedAcceptanceDefersCombatCreationUntilResume()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
   ui.accept.performClick();ui.pause();api.pending.done(MainActivity.obj("battle",new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2)),null);
   assertTrue(ui.preparing);assertNull(ui.art);ui.resume();assertFalse(ui.preparing);assertNotNull(ui.art);ui.pause();
  }
 }

 @Test public void pausedOutOfOrderCurrentCannotReplaceNewerDeferredAcceptance()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);final NativeApi.Result[] held={null};
   NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h){@Override void call(String path,JSONObject body,Result callback){if(path.endsWith("pvp/current")){paths.add(path);held[0]=callback;return;}super.call(path,body,callback);}};h.api=api;helper.clear(h);
   JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;ui.resume();ui.accept.performClick();ui.pause();
   api.pending.done(MainActivity.obj("battle",new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2)),null);
   held[0].done(MainActivity.obj("battle",waiting),null);assertEquals("ACTIVE",ui.initial.optString("status"));assertNull(ui.art);
   ui.resume();assertEquals("ACTIVE",ui.view.optString("status"));assertFalse(ui.preparing);ui.pause();
  }
 }

 @Test public void pausedDeferredTerminalCannotRegressToOlderActiveOrEqualFinished()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60HumanPvpTest.Api api=prepare(h);JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;ui.pause();
   ui.bind(new JSONObject(waiting.toString()).put("status","SETTLED").put("revision",3));
   ui.bind(new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2));
   ui.bind(new JSONObject(waiting.toString()).put("status","FINISHED").put("revision",3));
   assertEquals("SETTLED",ui.initial.optString("status"));ui.resume();assertTrue(ui.preparing);assertNull(ui.art);assertEquals("SETTLED",ui.view.optString("status"));assertTrue(api.writes.isEmpty());ui.pause();
  }
 }

 @Test public void deferredActiveResumesImmediatelyEvenIfOldReadyReadNeverReturned()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);final NativeApi.Result[] held={null};
   NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h){@Override void call(String path,JSONObject body,Result callback){if(path.endsWith("pvp/current")){paths.add(path);held[0]=callback;return;}super.call(path,body,callback);}};h.api=api;helper.clear(h);
   JSONObject waiting=ready("normal","AI");h.openRosterHumanBattle(waiting);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;ui.resume();ui.accept.performClick();ui.pause();
   api.pending.done(MainActivity.obj("battle",new JSONObject(waiting.toString()).put("status","ACTIVE").put("revision",2)),null);assertFalse(ui.reading);
   ui.resume();assertFalse(ui.preparing);assertNotNull(ui.panel);assertEquals("ACTIVE",ui.view.optString("status"));
   held[0].done(MainActivity.obj("battle",waiting),null);assertEquals("ACTIVE",ui.view.optString("status"));ui.pause();
  }
 }
}
