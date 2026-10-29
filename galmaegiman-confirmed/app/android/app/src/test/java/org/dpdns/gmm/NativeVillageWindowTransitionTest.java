package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real authored pixels remain complete until the next camera/window can be committed together. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageWindowTransitionTest {
 final NativeVillageHomeLoadingTest fixture=new NativeVillageHomeLoadingTest();
 NativeHomePartyFeedbackTest.Profile profile=NativeVillageHomeTest.PHONE;
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 static final class Request {
  final String path;final Consumer<Bitmap> ready;
  Request(String path,Consumer<Bitmap> ready){this.path=path;this.ready=ready;}
 }
 static final class DelayedOriginals implements NativeVillageHomeAssets.Source {
  final MainActivity h;final List<Request> requests=new ArrayList<>();int completed,maxInFlight;long peakPinnedBytes;
  DelayedOriginals(MainActivity h){this.h=h;}
  public void load(String path,Consumer<Bitmap> ready){requests.add(new Request(path,ready));}
  void finishOne(NativeVillageHomeAssets assets,boolean fail)throws Exception{
   Request request=requests.get(completed++);Bitmap bitmap=fail?null:LocalGameArt.decode(h,request.path,NativeVillageHomeAssets.decodeSample(request.path,h.getResources().getDisplayMetrics().heightPixels));
   if(!fail)assertNotNull("Original runtime art must be bundled: "+request.path,bitmap);
   request.ready.accept(bitmap);observe(assets);
  }
  void finishAll(NativeVillageHomeAssets assets)throws Exception{while(completed<requests.size())finishOne(assets,false);}
  void observe(NativeVillageHomeAssets assets){
   maxInFlight=Math.max(maxInFlight,assets.inFlight.size());assertTrue(assets.inFlight.size()<=NativeVillageHomeAssets.MAX_IN_FLIGHT);
   long bytes=0;for(Bitmap bitmap:assets.images.values())bytes+=bitmap.getAllocationByteCount();peakPinnedBytes=Math.max(peakPinnedBytes,bytes);
   java.util.Set<String> allowed=new java.util.HashSet<>(assets.committedImages);allowed.addAll(assets.wantedImages);allowed.add(assets.currentStatue);assertTrue(allowed.containsAll(assets.images.keySet()));
  }
 }
 Bitmap world(NativeVillageHomeView view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.onDraw(new Canvas(bitmap));return bitmap;}
 void capture(MainActivity h,String name)throws Exception{
  Path directory=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-window-05");Files.createDirectories(directory);
  Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try{NativeVillageHomeArtTest.png(directory.resolve(profile.name+"-"+name+".png"),bitmap);}finally{bitmap.recycle();}
 }
 void layout(MainActivity h){fixture.fixture.helper.layout(h,profile);}
 NativeVillageHomeView open(MainActivity h,DelayedOriginals source)throws Exception{
  NativeVillageHomeView view=fixture.open(h,source,profile);view.ambient.fixedTime=1250L;
  assertFalse("Initial partial art still uses the required loading gate",view.sceneReady());
  view.owner.statueStage=1;view.assets.loadStatue(view.layout,1,view.owner::current,view::updateNotice);source.finishAll(view.assets);layout(h);assertTrue(view.sceneReady());return view;
 }

 @Test public void delayedAndPartialNextWindowKeepsTheIdenticalOriginalFrameAndHud()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile selected:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("window-tablet-1280x720",1280,720,160,1,0,0,0,0)}){
   profile=selected;fixture.fixture.helper.configure(profile);
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();DelayedOriginals source=new DelayedOriginals(h);NativeVillageHomeView view=open(h,source);Bitmap original=world(view);capture(h,"01-complete-original");double camera=view.camera.cameraUnits();
   try{
    view.moveCameraUnits(4);layout(h);assertFalse(view.targetReady);assertEquals(camera,view.camera.cameraUnits(),0);assertEquals(camera+4,view.desiredCamera.cameraUnits(),0);
    assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());assertEquals(View.VISIBLE,view.settings.getVisibility());
    Bitmap held=world(view);try{assertTrue("A late district must not replace original art with a loading/partial frame",original.sameAs(held));}finally{held.recycle();}capture(h,"02-delayed-original-retained");
    source.finishOne(view.assets,false);layout(h);Bitmap partial=world(view);try{assertTrue(original.sameAs(partial));}finally{partial.recycle();}capture(h,"03-partial-original-retained");
    source.finishAll(view.assets);layout(h);assertEquals(camera+4,view.camera.cameraUnits(),0);assertTrue(view.targetReady);assertTrue(view.sceneReady());
    Bitmap next=world(view);try{assertFalse("A complete new district commits as an authored scene",original.sameAs(next));}finally{next.recycle();}capture(h,"04-complete-next-window");
    System.out.println("VILLAGE_WINDOW_FRAME_PROOF delayedSame=true partialSame=true committedCamera="+view.camera.cameraUnits()+" maxInFlight="+source.maxInFlight+" peakPinnedBytes="+source.peakPinnedBytes+" profile="+profile.name+" deviceFps=not-measured");
   }finally{original.recycle();}
  }
 }
 }

 @Test public void fastReverseAndBothEightUnitDirectionsCannotPaintAnObsoleteWindow()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();DelayedOriginals source=new DelayedOriginals(h);NativeVillageHomeView view=open(h,source);Bitmap original=world(view);
   try{
    view.moveCameraUnits(4);layout(h);view.moveCameraUnits(-4);layout(h);assertEquals(0,view.camera.cameraUnits(),0);assertTrue(view.targetReady);
    source.finishAll(view.assets);layout(h);Bitmap reversed=world(view);try{assertTrue(original.sameAs(reversed));}finally{reversed.recycle();}
    int requests=source.requests.size();for(double delta:new double[]{8,-16,8}){view.moveCameraUnits(delta);layout(h);Bitmap loop=world(view);try{assertTrue("Exact authored pixels close in either 8U direction",original.sameAs(loop));}finally{loop.recycle();}}
    assertEquals(0,view.camera.cameraUnits(),0);assertEquals("Equivalent windows do not start decoding again",requests,source.requests.size());capture(h,"05-fast-reverse-and-8U-closure");
   }finally{original.recycle();}
  }
 }

 @Test public void failedNextWindowAndExplicitRetryKeepTheCompletedSceneThroughSharedCacheTrim()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();DelayedOriginals source=new DelayedOriginals(h);NativeVillageHomeView view=open(h,source);Bitmap original=world(view);
   try{
    view.moveCameraUnits(4);layout(h);source.finishOne(view.assets,true);source.finishAll(view.assets);layout(h);
    assertTrue(view.sceneReady());assertEquals(0,view.camera.cameraUnits(),0);assertEquals(View.VISIBLE,view.windowRetry.getVisibility());assertEquals(View.GONE,view.loadingPanel.getVisibility());
    LocalGameArt.trim();Bitmap failed=world(view);try{assertTrue(original.sameAs(failed));}finally{failed.recycle();}capture(h,"06-failed-window-nonmodal-retry");
    view.windowRetry.performClick();layout(h);assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());Bitmap retrying=world(view);try{assertTrue(original.sameAs(retrying));}finally{retrying.recycle();}
    source.finishAll(view.assets);layout(h);assertTrue(view.sceneReady());assertEquals(4,view.camera.cameraUnits(),0);assertEquals(View.GONE,view.windowRetry.getVisibility());capture(h,"07-retry-complete-original-art");
   }finally{original.recycle();}
  }
 }

 @Test public void lateWindowCannotReopenTheOldAccountOrDetachedView()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();DelayedOriginals source=new DelayedOriginals(h);NativeVillageHomeView view=open(h,source);view.moveCameraUnits(4);layout(h);
   ((NativeVillageHomeTest.Api)h.api).account="different-owner.signature";source.finishAll(view.assets);view.updateNotice();assertFalse(view.sceneReady());assertEquals(0,view.camera.cameraUnits(),0);
   h.root.removeView(view);source.finishAll(view.assets);assertFalse(view.sceneReady());assertFalse(view.assets.active);assertEquals(-1,h.root.indexOfChild(view));
  }
 }
 @Test public void aWiderPhysicalViewportWaitsForItsOwnCompleteArtInsteadOfExposingUnloadedEdges()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();DelayedOriginals source=new DelayedOriginals(h);NativeVillageHomeView view=open(h,source);
   double original=view.camera.cameraUnits(),covered=view.committedViewportUnits;
   profile=new NativeHomePartyFeedbackTest.Profile("window-expanded-2048x412",2048,412,160,1,0,0,0,0);fixture.fixture.helper.configure(profile);layout(h);
   assertTrue(view.field.width()/view.unitPixels()>covered);assertFalse(view.targetReady);assertFalse(view.sceneReady());
   assertEquals("A physical resize preserves the initial completeness gate for newly exposed edges",View.VISIBLE,view.loadingPanel.getVisibility());
   assertEquals(original,view.camera.cameraUnits(),0);source.finishAll(view.assets);layout(h);
   assertTrue(view.sceneReady());assertTrue(view.targetReady);assertEquals(View.GONE,view.loadingPanel.getVisibility());
   assertEquals(view.field.width()/view.unitPixels(),view.committedViewportUnits,0);capture(h,"08-expanded-viewport-complete");
  }
 }
}
