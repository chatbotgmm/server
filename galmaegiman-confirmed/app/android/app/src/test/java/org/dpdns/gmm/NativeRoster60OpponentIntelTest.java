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
  return MainActivity.obj("version","opponent-intel-v2","speciesId","u02","defenseKnowledgeScope","base-observed","completedTurns",turns,"statsKnown",turns>=1,
    "stats",MainActivity.obj("hp",10,"mp",10,"durability",1,"statusResistance",2,"speed",3),
    "defenses",MainActivity.obj("NORMAL","normal","FIRE",turns>=1?"normal":"unknown"));
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
   int calls=api.paths.size();assertNull(ui.opponentDetails.getParent());assertNotNull(h.root.findViewWithTag("roster60-opponent-info"));ui.showOpponentInfo();assertEquals(calls,api.paths.size());assertNotNull(h.visibleDialog);
   assertTrue(text(ui.opponentDetails).contains("1 / 8"));assertTrue(text(ui.opponentDetails).contains("한 턴"));
   JSONObject next=NativeRoster60PlaybackTest.response(api.state,false);next.getJSONObject("state").put("opponentIntel",intel(1));
   ui.bind(next);assertTrue(ui.presenting());assertTrue(text(ui.opponentDetails).contains("1 / 8"));assertFalse(text(ui.opponentDetails).contains("속도  3"));
   NativeRoster60PlaybackTest.finish(ui);assertTrue(text(ui.opponentDetails).contains("2 / 8"));assertTrue(text(ui.opponentDetails).contains("속도  3"));
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
   ui.pause();assertNull(h.visibleDialog);
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

 @Test public void compactInfoDoesNotConsumeStageAndDismissesWithoutNavigation()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   assertSame(ui.stageOverlay,ui.art.getParent());assertEquals(ui.stageOverlay.getHeight(),ui.art.getHeight());
   assertTrue(ui.opponentInfo.getWidth()>=h.dp(48));assertTrue(ui.opponentInfo.getHeight()>=h.dp(48));
   assertEquals(h.dp(6),ui.opponentInfo.getLeft());
   assertEquals(Math.round(Roster60HudGeometry.enemyWidth(ui.art.getWidth(),h.getResources().getDisplayMetrics().density,ui.scale)),ui.opponentInfo.getWidth());
   assertEquals("",ui.opponentInfo.getText().toString());
   assertNull(ui.home.getParent());assertSame(ui.arena,ui.container.getChildAt(0));
   assertEquals(ui.container.getHeight(),ui.arena.getHeight());
   assertTrue(ui.opponentInfo.getRight()<=ui.stageOverlay.getWidth());
   ui.opponentInfo.performClick();assertNull(ui.opponentDialog);
   ui.opponentInfo.performLongClick();android.app.AlertDialog first=ui.opponentDialog;assertNotNull(first);
   ui.opponentInfo.performLongClick();assertSame(first,ui.opponentDialog);
   assertTrue(ui.back());assertNull(ui.opponentDialog);assertEquals("roster60Battle",h.screen);
   ui.opponentInfo.performLongClick();assertNotNull(ui.opponentDialog);ui.pause();assertNull(ui.opponentDialog);assertNull(h.visibleDialog);
  }
 }
 @Test public void staleAccountCannotOpenOpponentDialog()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   ui.opponentInfo.performLongClick();assertNotNull(ui.opponentDialog);
   ui.owner="different-account";ui.opponentInfo.performLongClick();assertNull(ui.opponentDialog);assertNull(h.visibleDialog);
  }
 }

 @Test public void allEightIllustrationsAreMaskedAndOutsideDismissDoesNotAttack()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   api.state.getJSONObject("state").put("opponentIntel",intel(0));ui.bind(api.state);
   int calls=api.paths.size();ui.opponentInfo.performLongClick();assertNotNull(ui.opponentDialog);
   RegistrationArtTest.await(()->LocalGameArt.idle());
   for(int i=0;i<8;i++){
    TextView cell=ui.opponentReactions[i];assertNotNull(cell.getCompoundDrawables()[1]);
    assertTrue(cell.getText().toString().contains(Roster60OpponentIntel.ELEMENT_NAMES[i]));
    assertTrue(cell.getText().toString().contains(i==0?"보통":"?"));
   }
   assertEquals(calls,api.paths.size());assertEquals(0,api.actions.size());
   android.app.AlertDialog shown=ui.opponentDialog;
   long now=android.os.SystemClock.uptimeMillis();MotionEvent outside=MotionEvent.obtain(now,now,MotionEvent.ACTION_OUTSIDE,-100,-100,0);
   shown.onTouchEvent(outside);outside.recycle();
   RegistrationArtTest.await(()->ui.opponentDialog==null);
   assertFalse(shown.isShowing());assertNull(ui.opponentDialog);assertEquals(0,api.actions.size());
   assertEquals("roster60Battle",h.screen);
  }
 }

 @Test public void captureHeaderlessBattleAndVisualIntelAtBothSizes()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("large-1280x720",1280,720,160,1,0,0,0,0)};
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){
   helper.helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.helper.flow.start()){
    MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
    api.state.getJSONObject("state").put("opponentIntel",intel(1));ui.bind(api.state);
    helper.helper.settle(h,profile);
    android.graphics.Bitmap battle=NativeHomePartyFeedbackTest.render(h.root);
    helper.helper.png(battle,"battle-headerless-"+profile.name+".png");battle.recycle();
    ui.opponentInfo.performLongClick();RegistrationArtTest.await(()->LocalGameArt.idle());
    View decor=ui.opponentDialog.getWindow().getDecorView();
    decor.measure(View.MeasureSpec.makeMeasureSpec(ui.opponentDialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(profile.height,View.MeasureSpec.AT_MOST));
    decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
    android.graphics.Bitmap intel=NativeHomePartyFeedbackTest.render(decor);
    helper.helper.png(intel,"battle-intel-"+profile.name+".png");intel.recycle();
    assertEquals(ui.container.getHeight(),ui.arena.getHeight());ui.dismissOpponentDialog();
   }
  }
 }
}
