package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60OpponentIntelTest {
 final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
 @Before public void setup(){helper.setup();}
 @After public void restore(){helper.restore();}
 static JSONObject intel(int turns){
  return MainActivity.obj("version","opponent-intel-v1","speciesId","u02","completedTurns",turns,"statsKnown",turns>=1,
    "stats",MainActivity.obj("hp",10,"mp",10,"durability",1,"statusResistance",2,"speed",3),
    "defenses",MainActivity.obj("NORMAL","normal","FIRE","resist","WATER",turns>=1?"weak":"unknown","ICE",turns>=1?"immune":"unknown"));
 }
 static String text(View view){
  StringBuilder s=new StringBuilder();if(view instanceof TextView)s.append(((TextView)view).getText()).append("\n");
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)s.append(text(((ViewGroup)view).getChildAt(i)));
  return s.toString();
 }
 @Test public void cachedInformationDoesNotRevealFutureTurnDuringPlayback()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   api.state.getJSONObject("state").put("opponentIntel",intel(0));
   // Real server redaction must not invalidate the command widget.
   api.state.getJSONObject("state").getJSONArray("fighters").getJSONObject(1).remove("suitability");
   ui.bind(api.state);assertTrue(NativeRoster60ProductBattleTest.named(ui.panel,"공격").isEnabled());
   int calls=api.paths.size();ui.opponentInfo.performClick();assertEquals(calls,api.paths.size());
   assertTrue(text(ui.opponentDetails).contains("2 / 8"));assertTrue(text(ui.opponentDetails).contains("한 턴"));
   JSONObject next=NativeRoster60PlaybackTest.response(api.state,false);next.getJSONObject("state").put("opponentIntel",intel(1));
   ui.bind(next);assertTrue(ui.presenting());assertTrue(text(ui.opponentDetails).contains("2 / 8"));assertFalse(text(ui.opponentDetails).contains("속도  3"));
   NativeRoster60PlaybackTest.finish(ui);assertTrue(text(ui.opponentDetails).contains("4 / 8"));assertTrue(text(ui.opponentDetails).contains("속도  3"));
   ui.closeOpponentInfo();assertNull(h.visibleDialog);
  }
 }
 @Test public void switchAndMissingLegacyIntelNeverExposeAnotherFighterStats()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   ui.showOpponentInfo();assertTrue(text(ui.opponentDetails).contains("0 / 8"));
   api.state.getJSONObject("state").put("opponentIntel",intel(1));ui.bind(api.state);
   assertTrue(text(ui.opponentDetails).contains("속도  3"));
   ui.opponentEvent(MainActivity.obj("actor","enemy","kind","switch"));
   assertTrue(text(ui.opponentDetails).contains("교체 확인 중"));assertTrue(text(ui.opponentDetails).contains("0 / 8"));assertFalse(text(ui.opponentDetails).contains("속도  3"));
   ui.pause();assertNull(ui.opponentDialog);
  }
 }
 @Test public void successfulSkillClosesListButRejectionAndHoldDoNot()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   NativeRoster60ProductBattleTest.named(ui.panel,"기술").performClick();assertTrue(ui.panel.isSkillPage());
   Button skill=NativeRoster60ProductBattleTest.named(ui.panel,"시험기술0");long now=android.os.SystemClock.uptimeMillis();
   NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_DOWN,now);
   org.robolectric.shadows.ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout()+1);
   NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_UP,now);
   assertTrue(ui.panel.isSkillPage());assertEquals(0,api.actions.size());
   skill.performClick();api.pending.done(null,"INSUFFICIENT_MP");assertTrue(ui.panel.isSkillPage());assertTrue(ui.message.getText().toString().startsWith("MP가 부족"));
   NativeRoster60ProductBattleTest.named(ui.panel,"시험기술0").performClick();
   api.pending.done(api.state,null);assertFalse(ui.panel.isSkillPage());
  }
 }
}
