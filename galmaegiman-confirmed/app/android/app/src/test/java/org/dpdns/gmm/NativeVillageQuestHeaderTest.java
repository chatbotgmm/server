package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Actual parent dispatch and window-coordinate header bounds, not only a panel-size formula. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageQuestHeaderTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}
  static final NativeHomePartyFeedbackTest.Profile[] PROFILES={
      NativeVillageHomeTest.PHONE,
      new NativeHomePartyFeedbackTest.Profile("goal-tablet",1280,720,160,1,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("goal-phone-font130",915,374,160,1.3f,0,0,0,0),
      new NativeHomePartyFeedbackTest.Profile("goal-insets-highdensity",2048,838,420,1,73,26,26,26)};
  static Rect bounds(NativeVillageHomeView village,View child){
    Rect actualHit=new Rect();child.getHitRect(actualHit);
    village.offsetDescendantRectToMyCoords((View)child.getParent(),actualHit);return actualHit;
  }
  void layout(MainActivity h,NativeHomePartyFeedbackTest.Profile profile){fixture.helper.layout(h,profile);}
  void bind(NativeVillageHomeView village)throws Exception{village.questTracker.bindStoryState(NativeVillageQuestTrackerTest.state(),null);assertTrue(village.questTracker.setPinned(Arrays.asList("R1","R2")));}
  static void save(MainActivity h,String name)throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"goal-fixed-header");Files.createDirectories(out);
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
  }

  @Test public void samePhysicalHitRectOpensAndClosesAcrossPhoneTabletFontAndInsets()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile p:PROFILES){fixture.helper.configure(p);try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,p);bind(village);LocalGameArtTest.drain();layout(h,p);
      NativeVillageQuestTracker tracker=village.questTracker;assertFalse(tracker.expanded);Rect folded=bounds(village,tracker.toggle);
      assertEquals(h.dp(176),folded.width());assertEquals(h.dp(48),folded.height());int reads=api.paths.size();
      assertFalse(fixture.helper.flow.labels(tracker).contains("주민 선택"));
      if(p.dpi==160)save(h,p.name+"-collapsed");
      NativeVillageHomeTest.tapAt(village,folded.centerX(),folded.centerY());layout(h,p);assertTrue(tracker.expanded);
      assertEquals("The actual toggle hit rect must not move or widen",folded,bounds(village,tracker.toggle));
      assertSame("Reuse the existing picker button in the body",tracker.list,tracker.select.getParent());assertEquals("추적할 부탁",tracker.select.getText().toString());assertTrue(tracker.select.getHeight()>=h.dp(48));
      if(p.dpi==160)save(h,p.name+"-expanded");
      NativeVillageHomeTest.tapAt(village,folded.centerX(),folded.centerY());layout(h,p);assertFalse(tracker.expanded);
      assertEquals(folded,bounds(village,tracker.toggle));assertEquals(View.GONE,tracker.scroll.getVisibility());assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());
    }}
  }

  @Test public void rapidRepeatedPhysicalTapsAndSnapshotUpdatesKeepOneStableTarget()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,NativeVillageHomeTest.PHONE);bind(village);layout(h,NativeVillageHomeTest.PHONE);
      NativeVillageQuestTracker tracker=village.questTracker;Rect target=bounds(village,tracker.toggle);int reads=api.paths.size();
      for(int i=0;i<12;i++){
        NativeVillageHomeTest.tapAt(village,target.centerX(),target.centerY());layout(h,NativeVillageHomeTest.PHONE);
        assertEquals(i%2==0,tracker.expanded);assertEquals(target,bounds(village,tracker.toggle));
      }
      tracker.toggle();layout(h,NativeVillageHomeTest.PHONE);JSONObject next=NativeVillageQuestTrackerTest.state();
      next.getJSONArray("quests").getJSONObject(1).put("status","COMPLETED");tracker.bindStoryState(next,null);layout(h,NativeVillageHomeTest.PHONE);
      assertFalse(tracker.cards.containsKey("R1"));assertEquals(target,bounds(village,tracker.toggle));
      tracker.bindStoryState(null,"network");layout(h,NativeVillageHomeTest.PHONE);assertFalse(tracker.select.isEnabled());assertEquals(target,bounds(village,tracker.toggle));
      assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void transparentHeaderSideAndCollapsedBodyPassActualDragsToTheVillage()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,NativeVillageHomeTest.PHONE);bind(village);layout(h,NativeVillageHomeTest.PHONE);
      NativeVillageQuestTracker tracker=village.questTracker;tracker.toggle();layout(h,NativeVillageHomeTest.PHONE);int reads=api.paths.size();
      float blankX=tracker.getLeft()+h.dp(24),blankY=tracker.getTop()+h.dp(24);
      assertFalse("Transparent side of the expanded header is not an overlay target",tracker.contains(blankX,blankY));
      double before=village.camera.cameraUnits(),desired=village.desiredCamera.cameraUnits();drag(village,blankX,blankY);assertNotEquals(desired,village.desiredCamera.cameraUnits(),.000001);
      LocalGameArtTest.drain();layout(h,NativeVillageHomeTest.PHONE);assertNotEquals(before,village.camera.cameraUnits(),.000001);
      village.stopMotion();float bodyX=tracker.getRight()-h.dp(40),bodyY=tracker.getBottom()-h.dp(16);assertTrue(tracker.contains(bodyX,bodyY));assertFalse("The pass-through point avoids the new fixed quarter arrows",new RectF(village.nextQuarter.getLeft(),village.nextQuarter.getTop(),village.nextQuarter.getRight(),village.nextQuarter.getBottom()).contains(bodyX,bodyY));
      before=village.camera.cameraUnits();desired=village.desiredCamera.cameraUnits();drag(village,bodyX,bodyY);assertEquals("Expanded content owns its drag",before,village.camera.cameraUnits(),0);assertEquals(desired,village.desiredCamera.cameraUnits(),0);
      tracker.collapse();layout(h,NativeVillageHomeTest.PHONE);assertFalse(tracker.contains(bodyX,bodyY));
      before=village.camera.cameraUnits();desired=village.desiredCamera.cameraUnits();drag(village,bodyX,bodyY);assertNotEquals("Collapsed content must pass the physical drag to the camera",desired,village.desiredCamera.cameraUnits(),.000001);
      LocalGameArtTest.drain();layout(h,NativeVillageHomeTest.PHONE);assertNotEquals("After the actual art is ready the visible camera must follow",before,village.camera.cameraUnits(),.000001);
      village.stopMotion();assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());assertNull(h.rosterStory);
    }
  }
  static void drag(NativeVillageHomeView village,float x,float y){
    long t=android.os.SystemClock.uptimeMillis();NativeVillageHomeTest.touch(village,MotionEvent.ACTION_DOWN,t,t,x,y);
    NativeVillageHomeTest.touch(village,MotionEvent.ACTION_MOVE,t,t+30,x-120,y);
    NativeVillageHomeTest.touch(village,MotionEvent.ACTION_MOVE,t,t+60,x-180,y);
    NativeVillageHomeTest.touch(village,MotionEvent.ACTION_CANCEL,t,t+90,x-180,y);
  }

  @Test public void bodyPickerOutsideDismissPreservesPinsScrollExpansionAndCamera()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,NativeVillageHomeTest.PHONE);bind(village);layout(h,NativeVillageHomeTest.PHONE);
      NativeVillageQuestTracker tracker=village.questTracker;tracker.toggle();layout(h,NativeVillageHomeTest.PHONE);Rect target=bounds(village,tracker.toggle);
      tracker.scroll.scrollTo(0,h.dp(25));int scroll=tracker.scroll.getScrollY(),reads=api.paths.size();double camera=village.camera.cameraUnits();
      tracker.chooseResidents();assertNotNull(tracker.pinDialog);new NativeDialogLayoutReviewTest().layoutDialog(h);
      android.app.AlertDialog dialog=tracker.pinDialog;new GameDialogImmersiveTest().assertBarsHidden(dialog.getWindow());long time=android.os.SystemClock.uptimeMillis();
      MotionEvent outside=MotionEvent.obtain(time,time,MotionEvent.ACTION_OUTSIDE,-h.dp(50),-h.dp(50),0);dialog.onTouchEvent(outside);outside.recycle();RegistrationArtTest.await(()->!dialog.isShowing());
      assertFalse("GameDialog retains native outside dismissal",dialog.isShowing());layout(h,NativeVillageHomeTest.PHONE);
      new GameDialogImmersiveTest().assertBarsHidden(h.getWindow());
      assertTrue(tracker.expanded);assertEquals(Arrays.asList("R1","R2"),tracker.selected);assertEquals(scroll,tracker.scroll.getScrollY());assertEquals(camera,village.camera.cameraUnits(),0);assertEquals(target,bounds(village,tracker.toggle));
      assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());
    }
  }
}
