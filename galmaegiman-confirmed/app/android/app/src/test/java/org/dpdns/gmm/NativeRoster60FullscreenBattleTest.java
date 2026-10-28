package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Battle owns the safe viewport, not a padded HQ journal page. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60FullscreenBattleTest {
 final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
 @Before public void setup(){helper.setup();}
 @After public void restore(){helper.restore();}

 @Test public void fillsEntireSafeViewportWithoutLoadingAnOuterMenuScene()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={
   NativeHomePartyFeedbackTest.PROFILES[0],
   new NativeHomePartyFeedbackTest.Profile("large-1280x720",1280,720,160,1,0,0,0,0),
   NativeHomePartyFeedbackTest.PROFILES[2]
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
    assertEquals(profile.left,h.page.getLeft());assertEquals(profile.top,h.page.getTop());
    assertEquals(profile.width-profile.right,h.page.getRight());
    assertEquals(profile.height-profile.bottom,h.page.getBottom());
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
}
