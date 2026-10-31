package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real PNG regions, raw long presses, and read-only observation dismissal. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageObservationTest {
  final NativeVillageExplorationTest exploration = new NativeVillageExplorationTest();
  final NativeVillageHomeTest fixture = exploration.fixture;
  final NativeHomePartyFeedbackTest helper = exploration.helper;
  static final NativeHomePartyFeedbackTest.Profile PHONE = NativeVillageHomeTest.PHONE;
  static final NativeHomePartyFeedbackTest.Profile TABLET = NativeVillageHomeTest.PROFILES[1];

  static final class Feature {
    final String id, scene, file;
    final int width, height, left, top, right, bottom;
    final double worldU;
    Feature(String id, String scene, String file, int width, int height,
        int left, int top, int right, int bottom, double panelLeft, double panelWidth) {
      this.id=id;this.scene=scene;this.file=file;this.width=width;this.height=height;
      this.left=left;this.top=top;this.right=right;this.bottom=bottom;
      worldU=panelLeft+panelWidth*(left+right)/2/width;
    }
  }
  // Independently recorded bounds from the three visually inspected, unmodified source PNGs.
  static final Feature[] FEATURES = {
      new Feature("stone-wall-planter","layered-join-patrol-approved","layered-join-patrol-approved.png",
          1254,1254,510,900,620,1020,2.333333333333333,1),
      new Feature("quay-rope","layered-quay","layered-quay-v5.png",
          2169,725,1540,570,1810,654,2.666666666666666,3),
      new Feature("house-lantern","layered-join-market-patrol-wrap","layered-join-market-patrol-wrap.png",
          1254,1254,590,660,665,765,-.33333333333333215,1)
  };

  @Before public void setup() { exploration.setup(); }
  @After public void restore() throws Exception { exploration.restore(); }

  NativeVillageHomeView open(MainActivity h, NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    fixture.prepare(h);
    NativeVillageHomeView view=fixture.open(h,profile);
    settle(h,view,profile);view.ambient.fixedTime=0L;
    return view;
  }
  void settle(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    exploration.settle(h,view,profile);
  }
  void camera(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile, double units) throws Exception {
    view.stopMotion();view.moveCameraUnits(units-view.desiredCamera.cameraUnits());settle(h,view,profile);
    assertEquals(units,view.camera.cameraUnits(),.000001);
  }
  void center(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile, Feature feature) throws Exception {
    view.zoomAt(1/view.desiredInspection.scale(),view.field.centerX(),view.field.bottom);settle(h,view,profile);
    camera(h,view,profile,feature.worldU-view.field.width()/(2*view.unitPixels()));
  }
  void zoom(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    view.zoomAt(1.5f,view.field.centerX(),view.field.bottom);settle(h,view,profile);
    assertEquals(1.5f,view.inspection.scale(),.000001f);
  }
  static VillageObservationContent.Observation detail(int index) {
    VillageObservationContent.Observation observation=VillageObservationContent.OBSERVATIONS.get(index);
    assertEquals(FEATURES[index].id,observation.id);assertEquals(FEATURES[index].scene,observation.sceneId);
    return observation;
  }
  RectF artwork(NativeVillageHomeView view, Feature feature) {
    VillageLayout.DistrictScenery scene=view.layout.districtScenery(feature.scene);assertNotNull(scene);
    VillageLayout.Asset asset=view.layout.assets.get(scene.asset);assertTrue(asset.verified);
    assertTrue(asset.path.endsWith("/"+feature.file));assertEquals(feature.width,asset.sourceWidth);assertEquals(feature.height,asset.sourceHeight);
    assertNotNull("The actual packaged PNG must be decoded",view.assets.get(scene.asset));
    RectF panel=new RectF();assertTrue(view.districtScene.bounds(view,feature.scene,panel));
    RectF expected=new RectF(panel.left+panel.width()*feature.left/feature.width,panel.top+panel.height()*feature.top/feature.height,
        panel.left+panel.width()*feature.right/feature.width,panel.top+panel.height()*feature.bottom/feature.height);
    float period=(float)(view.layout.worldUnits*view.unitPixels());
    expected.offset(Math.round((view.visibleWorld().centerX()-expected.centerX())/period)*period,0);
    assertTrue("The inspected source region is visible",expected.intersect(view.visibleWorld()));
    return expected;
  }
  static PointF screenPoint(NativeVillageHomeView view, RectF world) {
    PointF point=view.inspection.toScreen(world.centerX(),world.centerY(),new PointF());
    assertTrue("The real prop must be inside the safe input region",view.safe.contains(point.x,point.y));
    assertFalse("A fixed control must not replace this prop's input",NativeVillageExplorationTest.coveredByControl(view,point.x,point.y));
    return point;
  }
  void longPress(NativeVillageHomeView view, PointF point) {
    long down=SystemClock.uptimeMillis();
    NativeVillageExplorationTest.single(view,down,MotionEvent.ACTION_DOWN,7,point.x,point.y);
    NativeVillageExplorationTest.frame(550);
    assertTrue("Raw stationary input must reach the observation long press",view.observationTouch);
    assertNotNull(view.observationHighlight);assertNotNull(view.owner.menu);
    NativeVillageExplorationTest.single(view,down,MotionEvent.ACTION_UP,7,point.x,point.y);
    NativeVillageExplorationTest.frame(16);
    assertTrue("Releasing the long press keeps its note open",view.owner.menu.isShowing());
    assertNull("A cancelled picture press must not remain selected",view.pressedBuilding);
    assertTrue("An observation release cannot start a fling",view.scroller.isFinished());
  }
  static void assertNote(MainActivity h, NativeVillageHomeView view, VillageObservationContent.Observation detail) {
    AlertDialog dialog=view.owner.menu;assertNotNull(dialog);assertTrue(dialog.isShowing());assertSame(dialog,h.visibleDialog);
    assertEquals(detail.title,Shadows.shadowOf(dialog).getTitle().toString());
    TextView message=dialog.findViewById(android.R.id.message);assertNotNull(message);assertEquals(detail.description,message.getText().toString());
    assertEquals("닫기",dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
    assertNotNull(view.observationHighlight);assertEquals("home",h.screen);assertNull(h.rosterStory);
  }
  static void assertPosition(NativeVillageHomeView view, double camera, float scale, float pan) {
    assertEquals(camera,view.camera.cameraUnits(),.000001);assertEquals(camera,view.desiredCamera.cameraUnits(),.000001);
    assertEquals(scale,view.inspection.scale(),.000001);assertEquals(pan,view.inspection.panNormalized(),.000001);
    assertNull(view.observationHighlight);assertNull(view.pressedBuilding);assertNull(view.owner.menu);
    assertTrue(view.owner.current());
  }
  static Path output() throws Exception {
    Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-observation");
    Files.createDirectories(path);return path;
  }
  void capture(MainActivity h, NativeVillageHomeView view, String name, Bitmap before, RectF world) throws Exception {
    View decor=new NativeDialogLayoutReviewTest().layoutDialog(h);
    Bitmap outlined=NativeHomePartyFeedbackTest.render(h.root);
    try {
      RectF contour=view.screenRect(world);contour.inset(-h.dp(8),-h.dp(8));int changed=0,gold=0;
      for(int y=Math.max(0,(int)contour.top);y<Math.min(outlined.getHeight(),(int)Math.ceil(contour.bottom));y++)
        for(int x=Math.max(0,(int)contour.left);x<Math.min(outlined.getWidth(),(int)Math.ceil(contour.right));x++) {
          int pixel=outlined.getPixel(x,y);
          if(pixel!=before.getPixel(x,y))changed++;
          if(pixel!=before.getPixel(x,y)&&Math.abs(Color.red(pixel)-Color.red(NavyWorkbenchTheme.GOLD_EDGE))<=2
              &&Math.abs(Color.green(pixel)-Color.green(NavyWorkbenchTheme.GOLD_EDGE))<=2
              &&Math.abs(Color.blue(pixel)-Color.blue(NavyWorkbenchTheme.GOLD_EDGE))<=2)gold++;
        }
      assertTrue("The actual world screenshot contains a visible contour",changed>20);
      assertTrue("The contour uses the production gold color",gold>10);
      NativeVillageHomeArtTest.png(output().resolve(name+"-world.png"),outlined);
      Bitmap modal=outlined.copy(Bitmap.Config.ARGB_8888,true);
      try {
        Canvas canvas=new Canvas(modal);canvas.drawColor(0x66000000);canvas.save();
        canvas.translate((modal.getWidth()-decor.getWidth())/2f,(modal.getHeight()-decor.getHeight())/2f);decor.draw(canvas);canvas.restore();
        NativeVillageHomeArtTest.png(output().resolve(name+"-note.png"),modal);
      } finally { modal.recycle(); }
    } finally { outlined.recycle(); }
  }

  @Test public void verifiedSourceRegionsRequireZoomKeepTheirMinimumTargetAndRepeatAtEightUnits() throws Exception {
    assertEquals("Only the three inspected features are authored",3,VillageObservationContent.OBSERVATIONS.size());
    try(ActivityController<MainActivity> activity=helper.flow.start()) {
      MainActivity h=activity.get();NativeVillageHomeView view=open(h,PHONE);
      for(int index=0;index<FEATURES.length;index++) {
        Feature feature=FEATURES[index];VillageObservationContent.Observation observation=detail(index);center(h,view,PHONE,feature);
        RectF expected=artwork(view,feature),actual=new RectF(1,2,3,4);
        assertNull(VillageObservationContent.find(view,expected.centerX(),expected.centerY()));
        assertFalse(VillageObservationContent.bounds(view,observation,actual));assertTrue(actual.isEmpty());
        zoom(h,view,PHONE);expected=artwork(view,feature);assertTrue(VillageObservationContent.bounds(view,observation,actual));
        NativeVillageExplorationTest.assertRect(expected,actual,.01f);
        assertSame(observation,VillageObservationContent.find(view,actual.centerX(),actual.centerY()));
        float halfWidth=Math.max(actual.width(),h.dp(48)/view.inspection.scale())/2;
        float halfHeight=Math.max(actual.height(),h.dp(48)/view.inspection.scale())/2;
        float inset=1/view.inspection.scale();
        for(float[] point:new float[][]{{actual.centerX()-halfWidth+inset,actual.centerY()},
            {actual.centerX()+halfWidth-inset,actual.centerY()},{actual.centerX(),actual.centerY()-halfHeight+inset},
            {actual.centerX(),actual.centerY()+halfHeight-inset}})
          assertSame("At least 48dp of physical touch coverage",observation,VillageObservationContent.find(view,point[0],point[1]));
        assertNull("A local region cannot claim the entire building",VillageObservationContent.find(view,actual.centerX()+halfWidth+5,actual.centerY()));
        RectF original=new RectF(actual);double camera=view.camera.cameraUnits();
        camera(h,view,PHONE,camera+8);assertTrue(VillageObservationContent.bounds(view,observation,actual));
        NativeVillageExplorationTest.assertRect(original,actual,.01f);
        assertSame(observation,VillageObservationContent.find(view,actual.centerX(),actual.centerY()));
      }
      assertTrue(((NativeVillageHomeTest.Api)h.api).writes.isEmpty());
    }
  }

  @Test public void rawLongPressOpensAllThreePhoneNotesOutsideAndBackPreserveInspectionWithActualCaptures() throws Exception {
    try(ActivityController<MainActivity> activity=helper.flow.start()) {
      MainActivity h=activity.get();NativeVillageHomeView view=open(h,PHONE);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
      final int[] clicks={0};for(Button button:view.buildingButtons.values())button.setOnClickListener(ignored->clicks[0]++);
      for(Button button:view.residentButtons.values())button.setOnClickListener(ignored->clicks[0]++);
      for(int index=0;index<FEATURES.length;index++) {
        center(h,view,PHONE,FEATURES[index]);zoom(h,view,PHONE);RectF world=artwork(view,FEATURES[index]);
        double camera=view.camera.cameraUnits();float scale=view.inspection.scale(),pan=view.inspection.panNormalized();List<String> reads=new ArrayList<>(api.paths);
        Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
        try {longPress(view,screenPoint(view,world));assertNote(h,view,detail(index));capture(h,view,PHONE.name+"-"+FEATURES[index].id,before,world);}
        finally {before.recycle();}
        assertEquals("A long press cannot become a building or resident click",0,clicks[0]);assertEquals(reads,api.paths);assertTrue(api.writes.isEmpty());
        if(index%2==0) {
          AlertDialog dialog=view.owner.menu;long time=SystemClock.uptimeMillis();
          MotionEvent outside=MotionEvent.obtain(time,time,MotionEvent.ACTION_OUTSIDE,-h.dp(50),-h.dp(50),0);
          try {dialog.onTouchEvent(outside);}finally {outside.recycle();}
          NativeVillageExplorationTest.frame(16);assertFalse("Outside touch dismisses the observation note",dialog.isShowing());
        } else assertTrue("Back closes the note before navigating",view.owner.back());
        assertPosition(view,camera,scale,pan);assertEquals(reads,api.paths);assertTrue(api.writes.isEmpty());
      }
    }
  }

  @Test public void tabletRopeLongPressRendersItsActualNoteAndBackClearsOnlyTheObservation() throws Exception {
    helper.configure(TABLET);
    try(ActivityController<MainActivity> activity=helper.flow.start()) {
      MainActivity h=activity.get();NativeVillageHomeView view=open(h,TABLET);center(h,view,TABLET,FEATURES[1]);zoom(h,view,TABLET);
      RectF world=artwork(view,FEATURES[1]);double camera=view.camera.cameraUnits();float scale=view.inspection.scale(),pan=view.inspection.panNormalized();
      NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;List<String> reads=new ArrayList<>(api.paths);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
      try {longPress(view,screenPoint(view,world));assertNote(h,view,detail(1));capture(h,view,TABLET.name+"-quay-rope",before,world);}
      finally {before.recycle();}
      assertTrue(view.owner.back());assertPosition(view,camera,scale,pan);assertEquals(reads,api.paths);assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void parentLongPressCancelsAnUnderlyingClickableChildBeforeRelease() throws Exception {
    try(ActivityController<MainActivity> activity=helper.flow.start()) {
      MainActivity h=activity.get();NativeVillageHomeView view=open(h,PHONE);center(h,view,PHONE,FEATURES[0]);zoom(h,view,PHONE);
      RectF world=artwork(view,FEATURES[0]);PointF point=screenPoint(view,world);final int[] events={0,0,0};
      // A transparent test receiver exercises cancellation without covering the real PNG pixels.
      Button receiver=new Button(h);receiver.setText("");receiver.setBackgroundColor(Color.TRANSPARENT);receiver.setStateListAnimator(null);
      receiver.setOnTouchListener((target,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)events[0]++;if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)events[1]++;return false;});
      receiver.setOnClickListener(target->events[2]++);int size=h.dp(64);view.addView(receiver,new FrameLayout.LayoutParams(size,size));
      receiver.measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY));
      receiver.layout(Math.round(point.x-size/2f),Math.round(point.y-size/2f),Math.round(point.x+size/2f),Math.round(point.y+size/2f));
      longPress(view,point);assertNote(h,view,detail(0));
      assertEquals("The child receives the actual down",1,events[0]);assertEquals("The parent sends child ACTION_CANCEL",1,events[1]);assertEquals("Cancelled release cannot click the child",0,events[2]);
      assertTrue(view.owner.back());assertNull(view.observationHighlight);view.removeView(receiver);assertTrue(((NativeVillageHomeTest.Api)h.api).writes.isEmpty());
    }
  }

 @Test public void remainingLongPressStreamCannotPanOrFlingBehindItsNote()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeVillageHomeView view=open(h,PHONE);center(h,view,PHONE,FEATURES[0]);zoom(h,view,PHONE);RectF world=artwork(view,FEATURES[0]);PointF point=screenPoint(view,world);
   double camera=view.camera.cameraUnits();float scale=view.inspection.scale(),pan=view.inspection.panNormalized();long down=SystemClock.uptimeMillis();NativeVillageExplorationTest.single(view,down,MotionEvent.ACTION_DOWN,7,point.x,point.y);NativeVillageExplorationTest.frame(550);assertNote(h,view,detail(0));
   NativeVillageExplorationTest.single(view,down,MotionEvent.ACTION_MOVE,7,point.x+120,point.y-60);NativeVillageExplorationTest.single(view,down,MotionEvent.ACTION_UP,7,point.x+160,point.y-80);NativeVillageExplorationTest.frame(32);
   assertEquals(camera,view.camera.cameraUnits(),.000001);assertEquals(camera,view.desiredCamera.cameraUnits(),.000001);assertEquals(pan,view.inspection.panNormalized(),.000001);assertTrue(view.scroller.isFinished());assertNote(h,view,detail(0));assertTrue(view.owner.back());assertPosition(view,camera,scale,pan);NativeVillageHomeTest.tap(view,view.nextQuarter);assertTrue("A fresh HUD tap after the note starts a new independent gesture",view.quarterMoving);view.stopMotion();assertTrue(((NativeVillageHomeTest.Api)h.api).writes.isEmpty());
  }
 }
}
