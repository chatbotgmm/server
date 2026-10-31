package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.content.Context;
import android.view.View;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Canvas reaches physical edges; only HUD/commands consume device-safe insets. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60FullscreenBattleTest {
 final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
 @Before public void setup(){helper.setup();}
 @After public void restore(){helper.restore();}

 @Test public void fillsPhysicalScreenWithoutLoadingAnOuterMenuScene()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={
   NativeHomePartyFeedbackTest.PROFILES[0],
   new NativeHomePartyFeedbackTest.Profile("large-1280x720",1280,720,160,1,0,0,0,0),
   NativeHomePartyFeedbackTest.PROFILES[2],
   NativeHomePartyFeedbackTest.PROFILES[3]
  };
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){
   helper.helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.helper.flow.start()){
    MainActivity h=c.get();helper.helper.prepare(h);h.api=new NativeRoster60PlaybackTest.Api(h);
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
    h.openRosterBattle();NativeRoster60Battle ui=h.rosterBattle;
    helper.helper.settle(h,profile);
    assertEquals("Battle viewport plus an inert readiness overlay, no menu scene",2,h.root.getChildCount());
    assertSame(ui.artGate,h.root.getChildAt(1));assertEquals(View.GONE,ui.artGate.getVisibility());
    assertSame(h.page,h.root.getChildAt(0));
    assertNull(h.root.findViewWithTag("theme-scene-background"));
    assertNull(h.root.findViewWithTag("theme-page-header"));
    assertEquals(0,h.page.getPaddingLeft());assertEquals(0,h.page.getPaddingTop());
    assertEquals(0,h.page.getPaddingRight());assertEquals(0,h.page.getPaddingBottom());
    assertFullscreen(h,ui,profile);
    assertEquals(h.page.getWidth(),ui.arena.getWidth());assertEquals(h.page.getHeight(),ui.arena.getHeight());
    assertEquals(ui.stageOverlay.getWidth(),ui.art.getWidth());
    assertEquals(ui.stageOverlay.getHeight(),ui.art.getHeight());
    View rail=h.root.findViewWithTag("roster60-command-rail");
    assertEquals(ui.arena.getWidth(),rail.getRight());
    assertTrue(ui.opponentInfo.getWidth()>0);ui.opponentInfo.performLongClick();
    assertNotNull(ui.opponentDialog);ui.back();assertNull(ui.opponentDialog);
    assertEquals("roster60Battle",h.screen);
    Bitmap capture=NativeHomePartyFeedbackTest.render(h.root);
    try{helper.helper.png(capture,"battle-fullscreen-"+profile.name+".png");}finally{capture.recycle();}
   }
  }
 }
 void assertFullscreen(MainActivity h,NativeRoster60Battle ui,NativeHomePartyFeedbackTest.Profile profile){
  assertEquals("Canvas must reach the physical left edge",0,h.page.getLeft());assertEquals(0,h.page.getTop());
  assertEquals(profile.width,h.page.getRight());assertEquals(profile.height,h.page.getBottom());
  Rect art=NativeHomePartyFeedbackTest.bounds(ui.art,h.root);
  assertEquals(0,art.left);assertEquals(0,art.top);assertEquals(profile.height,art.bottom);
  Rect enemy=NativeHomePartyFeedbackTest.bounds(ui.opponentInfo,h.root),self=NativeHomePartyFeedbackTest.bounds(ui.allyInfo,h.root);
  assertTrue(enemy.left>=profile.left);assertTrue(enemy.top>=profile.top);assertTrue(self.bottom<=profile.height-profile.bottom);
  int protectedButtons=0;
  for(android.widget.Button b:NativeRoster60ProductBattleTest.buttons(h.root.findViewWithTag("roster60-command-rail")))if(b.isShown()&&b.getWidth()>0){
   Rect box=NativeHomePartyFeedbackTest.bounds(b,h.root);assertTrue(b.getText()+" right "+box,box.right<=profile.width-profile.right);assertTrue(b.getText()+" top "+box,box.top>=profile.top);assertTrue(b.getText()+" bottom "+box,box.bottom<=profile.height-profile.bottom);
   protectedButtons++;
  }
  assertTrue("Expected visible command buttons inside the safe area",protectedButtons>=1);
 }
 static class Api extends NativeRoster60PlaybackTest.Api {
  Api(Context context)throws Exception{super(context);NativeRoster60AllyInfoTest.own(state);}
  @Override void call(String path,JSONObject body,Result reply){
   if(path.endsWith("pvp/options")){paths.add(path);reply.done(Roster60PartyPolicyTest.response(MainActivity.obj("partyIds",new JSONArray().put("first")),false),null);return;}
   super.call(path,body,reply);
  }
 }
 Api open(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  helper.helper.prepare(h);Api api=new Api(h);h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();helper.helper.settle(h,profile);return api;
 }
 @Test public void focusPopupBackAndResumeKeepTheCanvasFullWhileInsetsCanChangeSides()throws Exception{
  NativeHomePartyFeedbackTest.Profile left=NativeHomePartyFeedbackTest.PROFILES[2],right=new NativeHomePartyFeedbackTest.Profile("cutout-right",2048,838,420,1,26,26,73,26);
  helper.helper.configure(left);
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();Api api=open(h,left);NativeRoster60Battle ui=h.rosterBattle;assertFullscreen(h,ui,left);
   ui.opponentInfo.performLongClick();assertNotNull(ui.opponentDialog);new GameDialogImmersiveTest().assertBarsHidden(ui.opponentDialog.getWindow());ui.back();assertNull(ui.opponentDialog);
   h.onWindowFocusChanged(false);h.onWindowFocusChanged(true);helper.helper.layout(h,left);assertFullscreen(h,ui,left);
   ui.allyInfo.performLongClick();assertNotNull(ui.allyDialog);ui.allyDialog.onBackPressed();RegistrationArtTest.await(()->ui.allyDialog==null);assertEquals("roster60Battle",h.screen);
   helper.helper.configure(right);h.onConfigurationChanged(new android.content.res.Configuration(h.getResources().getConfiguration()));helper.helper.layout(h,right);assertFullscreen(h,ui,right);
   c.pause().resume();h.onWindowFocusChanged(true);helper.helper.settle(h,right);assertFullscreen(h,ui,right);new GameDialogImmersiveTest().assertBarsHidden(h.getWindow());
   helper.helper.configure(left);helper.helper.layout(h,left);assertFullscreen(h,ui,left);assertEquals(0,api.actions.size());
  }
 }
 @Test public void optionalPreparationAndItsEditorKeepSafeBoundsAndCancelPreservesTheSavedParty()throws Exception{
  NativeHomePartyFeedbackTest.Profile profile=NativeHomePartyFeedbackTest.PROFILES[2];helper.helper.configure(profile);
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();Api api=open(h,profile);NativeRoster60Battle ui=h.rosterBattle;
   ui.prepareParty(Roster60PreparationTest.units(),MainActivity.obj("id","fixture","name","출발 준비","mode","story"));helper.helper.layout(h,profile);assertNotNull(ui.preparation);
   Roster60PreparationView prep=ui.preparation;Rect box=NativeHomePartyFeedbackTest.bounds(prep,h.root);
   assertEquals(profile.left,box.left);assertEquals(profile.top,box.top);assertEquals(profile.width-profile.right,box.right);assertEquals(profile.height-profile.bottom,box.bottom);
   prep.edit.performClick();helper.helper.layout(h,profile);prep.formation.toggle(prep.formation.unit("second"));ui.back();assertNull(prep.formation);assertEquals("[\"first\"]",prep.saved.toString());
   ui.back();helper.helper.layout(h,profile);assertNull(ui.preparation);assertFullscreen(h,ui,profile);assertEquals("[\"first\"]",prep.saved.toString());assertEquals(0,api.actions.size());
  }
 }
 @Test public void storyAndHumanPvpAdaptersShareThePhysicalViewportWithoutChangingSnapshots()throws Exception{
  NativeHomePartyFeedbackTest.Profile profile=NativeHomePartyFeedbackTest.PROFILES[2];helper.helper.configure(profile);
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();Api api=open(h,profile);h.rosterBattle.pause();String original=api.state.toString();
   NativeRoster60StoryBattle story=new NativeRoster60StoryBattle(h,new JSONObject(original),"STORY_01");h.rosterBattle=story;story.open();helper.helper.settle(h,profile);assertFullscreen(h,story,profile);story.pause();
   JSONObject dto=new JSONObject(original);dto.put("mode","normal");NativeRoster60PvpBattle duel=new NativeRoster60PvpBattle(h,dto);h.rosterBattle=duel;duel.open();helper.helper.settle(h,profile);assertFullscreen(h,duel,profile);duel.pause();
   assertEquals(original,api.state.toString());assertEquals(0,api.actions.size());
  }
 }
}
