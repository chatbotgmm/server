package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Actual battle/story response adapters; the receipt view never submits another payout. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeXpResultIntegrationTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();fixture.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore(){fixture.restore();}
  static JSONObject progress(){return NativeXpRewardsTest.progress(NativeXpRewardsTest.point(2,"100","80","120"),NativeXpRewardsTest.point(3,"160","120","200"));}
  static JSONObject settled(JSONObject source)throws Exception{
    JSONObject result=new JSONObject(source.toString());result.put("status","SETTLED").put("revision",2);
    result.getJSONObject("state").put("outcome","win");
    result.put("settlement",MainActivity.obj("xpAwarded",60,"totalXp","160","xpProgress",progress()));return result;
  }
  static final class Api extends NativeRoster60ProductBattleTest.Api {
    final String account="xp-result-"+UUID.randomUUID()+".1.signature";int payouts;
    Api(Context context)throws Exception{super(context);state=NativeRoster60PlaybackTest.baseline();}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){
      if(path.endsWith("battle/settle")){payouts++;try{state=settled(state);}catch(Exception invalid){throw new AssertionError(invalid);}done.done(state,null);return;}
      super.call(path,body,done);
    }
  }
  Api prepare(MainActivity h)throws Exception{
    fixture.prepare(h).close();Api api=new Api(h);h.api=api;
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();return api;
  }
  // View.post settlement, reward presentation, and dialog dismissal wait for a UI frame.
  static void idle(){
    org.robolectric.shadows.ShadowLooper main=Shadows.shadowOf(Looper.getMainLooper());
    main.idleFor(java.time.Duration.ofMillis(32));
    // The final traversal can enqueue the receipt Handler at the boundary of idleFor.
    main.idle();
  }

  @Test public void actualSettledBattleShowsXpOnceAndDialogBackKeepsReceiptWithoutAnotherPayout()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);h.openRosterBattle();fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);NativeRoster60Battle battle=h.rosterBattle;
      assertNotNull(battle.xpRewards);assertEquals(View.GONE,battle.xpRewards.button.getVisibility());
      JSONObject terminal=NativeRoster60PlaybackTest.response(api.state,true);api.state=terminal;battle.bind(terminal);
      NativeRoster60PlaybackTest.finish(battle);idle();
      assertEquals(1,api.payouts);assertEquals(View.VISIBLE,battle.xpRewards.button.getVisibility());
      // Art completion/settlement can enqueue presentation after the last traversed frame.
      RegistrationArtTest.await(()->battle.xpRewards.dialog!=null);
      NativeXpRewards rewards=battle.xpRewards;assertNotNull("bound="+rewards.bound+" generation="+rewards.presentationGeneration+" live="+rewards.liveEvents+" current="+rewards.current()+" receipt="+(rewards.receipt==null?"none":rewards.receipt.rows.size()+"/"+rewards.receipt.positive())+" marked="+h.getSharedPreferences("roster60_xp_presentation",0).getBoolean(NativeXpRewards.marker(BuildConfig.SERVER_URL,api.account,"battle:test-battle"),false)+" latest="+battle.view.optString("status"),rewards.dialog);assertTrue(rewards.dialog.isShowing());assertTrue(rewards.panel.fresh);
      NativeXpRewards.Panel animation=rewards.panel;animation.skip.performClick();assertTrue(animation.complete);assertEquals("+60 XP",animation.cells.get(0).number.getText().toString());assertEquals(500,animation.cells.get(0).bar.getProgress());
      assertTrue(animation.cells.get(0).level.getText().toString().contains("Lv.2 → Lv.3"));
      rewards.dialog.onBackPressed();idle();assertNull(rewards.dialog);assertNull(rewards.panel);assertNull(h.visibleDialog);
      battle.bind(api.state);idle();assertNull(rewards.dialog);assertEquals(1,api.payouts);
      rewards.button.performClick();assertNotNull(rewards.panel);assertFalse(rewards.panel.fresh);assertEquals("기록 60 XP",rewards.panel.cells.get(0).number.getText().toString());assertEquals(View.GONE,rewards.panel.cells.get(0).level.getVisibility());
      rewards.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertNull(h.visibleDialog);assertNull(rewards.panel);assertEquals(1,api.payouts);
      battle.pause();h.openRosterBattle();fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);idle();assertNull(h.rosterBattle.xpRewards.dialog);assertEquals(View.VISIBLE,h.rosterBattle.xpRewards.button.getVisibility());assertEquals(1,api.payouts);h.rosterBattle.pause();
    }
  }
  @Test public void firstColdReconnectToHistoricalReceiptDoesNotCelebrateAnOldLevelUp()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.state=settled(api.state);h.openRosterBattle();fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);idle();
      NativeXpRewards rewards=h.rosterBattle.xpRewards;assertEquals(0,api.payouts);assertNull(rewards.dialog);assertEquals(View.VISIBLE,rewards.button.getVisibility());
      rewards.button.performClick();assertFalse(rewards.panel.fresh);assertEquals(View.GONE,rewards.panel.cells.get(0).level.getVisibility());assertEquals("기록 60 XP",rewards.panel.cells.get(0).number.getText().toString());
      h.rosterBattle.pause();idle();assertNull(h.visibleDialog);assertEquals(0,api.payouts);
    }
  }
  @Test public void questCompletionUsesActualCommanderRewardAndRepeatedResponseCannotReplayIt()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h).close();NativeRoster60StoryTest.Api api=new NativeRoster60StoryTest.Api(h);api.account="xp-quest-"+UUID.randomUUID()+".1.signature";h.api=api;
      h.getSharedPreferences("roster60_story_v1",0).edit().clear().commit();NativeRoster60StoryTest.openLegacy(h);fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);NativeRoster60StoryController story=h.rosterStory;
      story.advance(0);assertEquals(1,api.bodies.size());
      JSONObject reward=NativeXpRewardsTest.commanderReceipt(false).put("xpProgress",NativeXpRewardsTest.progress(NativeXpRewardsTest.point(2,"100","80","120"),NativeXpRewardsTest.point(3,"140","120","200")));
      JSONObject result=NativeRoster60StoryTest.state().put("revision",4).put("commanderReward",reward);
      api.respond(0,result,null);idle();assertNotNull(story.xpRewards.dialog);assertTrue(story.xpRewards.panel.fresh);
      story.xpRewards.panel.skip.performClick();assertEquals("+40 XP",story.xpRewards.panel.cells.get(0).number.getText().toString());assertEquals(250,story.xpRewards.panel.cells.get(0).bar.getProgress());
      story.xpRewards.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertNull(h.visibleDialog);
      api.respond(0,result,null);idle();assertNull(story.xpRewards.dialog);assertEquals(1,api.bodies.size());story.pause();
    }
  }
}
