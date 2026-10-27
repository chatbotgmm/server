package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.View;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Deterministic work counts, not real-device frame-rate measurements. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FormationAndFrameWorkTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static int exact(int size){return View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY);}
 @Test public void firstExactMeasureBuildsOnlyOnceAndPreservesPrelayoutSelection()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);JSONArray units=PartyUiWorkPerformanceTest.roster(60);
   Roster60FormationView f=new Roster60FormationView(h,units,ids->{},()->{});
   assertEquals(0,f.slotReplacements);assertEquals(0,f.ownedTiles.size());
   f.toggle(units.getJSONObject(0));assertEquals(0,f.slotReplacements);
   f.measure(exact(h.dp(1140)),exact(0));assertEquals(0,f.slotReplacements);
   f.measure(exact(h.dp(1140)),exact(h.dp(390)));
   assertEquals(6,f.slotReplacements);assertEquals(60,f.ownedTiles.size());assertTrue(f.scale>1);
   assertEquals("o0",f.selected.get(0));assertTrue(f.start.isEnabled());
   Object first=f.ownedTiles.get("o0");long replacements=f.slotReplacements;
   for(int i=0;i<20;i++){f.forceLayout();f.measure(exact(h.dp(1140)),exact(h.dp(390)));f.draw();}
   assertEquals(replacements,f.slotReplacements);assertSame(first,f.ownedTiles.get("o0"));
   f.toggle(units.getJSONObject(1));assertSame(first,f.ownedTiles.get("o0"));assertEquals(replacements+1,f.slotReplacements);
  }
 }
 @Test public void formationBadgesShowEachOwnedLevelWhileAccessibilityKeepsOrder()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);JSONArray units=PartyUiWorkPerformanceTest.roster(6);
   for(int i=0;i<6;i++)units.getJSONObject(i).put("level",i+11);
   Roster60FormationView f=new Roster60FormationView(h,units,ids->{},()->{});
   for(int i=0;i<6;i++)f.toggle(units.getJSONObject(i));
   f.measure(exact(h.dp(1140)),exact(h.dp(390)));
   for(int i=0;i<6;i++){
    Roster60MemberTile tile=(Roster60MemberTile)f.slots.getChildAt(i);
    assertEquals("Lv. "+(i+11),tile.role.getText().toString());
    assertEquals("Lv. "+(i+11),f.ownedTiles.get("o"+i).role.getText().toString());
    assertTrue(tile.action.getContentDescription().toString().contains(Roster60FormationView.role(i)));
    assertEquals("o"+i,f.selected.get(i));
   }
   assertNotNull(((Roster60MemberTile)f.slots.getChildAt(5)).focus);
   assertFalse(f.level.getText().toString().contains("예비"));assertTrue(f.level.getText().toString().contains("Lv16"));
   assertTrue(f.level.getContentDescription().toString().contains("예비 5"));
  }
 }
 @Test public void progressTicksDoNotRebindButStillInterpolateAndSwitchAtBoundary()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);NativeRoster60Art art=new NativeRoster60Art(h);
   JSONObject before=NativeRoster60PlaybackTest.baseline().getJSONObject("state"),after=new JSONObject(before.toString());
   after.getJSONArray("fighters").getJSONObject(0).put("hp",4);
   JSONObject event=NativeRoster60PlaybackTest.event("self",before.getJSONArray("fighters"),after.getJSONArray("fighters"),"attacker",6);
   event.put("defense","reflect");art.frame(before,after,event,.5f);long binds=art.stateBindings;
   assertEquals(10,art.shownValue(0,"hp",-1));
   for(int i=0;i<20;i++)art.frame(before,after,event,.5f+i*.35f/19);
   assertEquals(binds,art.stateBindings);assertEquals(4,art.shownValue(0,"hp",-1));assertEquals(10,art.shownValue(1,"hp",-1));
   JSONObject swap=new JSONObject(event.toString()).put("kind","switch");
   art.frame(before,after,swap,.2f);assertEquals(binds+1,art.stateBindings);assertSame(before,art.snapshot);
   art.frame(before,after,swap,.44f);assertEquals(binds+1,art.stateBindings);
   art.frame(before,after,swap,.45f);assertEquals(binds+2,art.stateBindings);assertSame(after,art.snapshot);
   art.frame(before,after,swap,.9f);assertEquals(binds+2,art.stateBindings);
   art.clearFrame();art.frame(before,after,event,.5f);assertEquals(binds+3,art.stateBindings);
  }
 }
}
