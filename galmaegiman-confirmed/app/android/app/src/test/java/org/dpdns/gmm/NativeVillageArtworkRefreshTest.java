package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.os.Looper;
import android.view.View;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Completion bursts publish one latest-state frame; lifecycle changes cannot revive an old refresh. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageArtworkRefreshTest {
 final NativeVillageHomeLoadingTest fixture=new NativeVillageHomeLoadingTest();
 int activeRequestStart;
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 void frame(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));}
 NativeVillageHomeView open(MainActivity h,NativeVillageHomeAssetsTest.Delayed source)throws Exception{
  NativeVillageHomeView view=fixture.open(h,source);
  activeRequestStart=source.callbacks.size();view.assets.cancel();view.assets.load(view.layout,view.owner::current,view::scheduleArtworkRefresh);
  assertTrue(view.isAttachedToWindow());return view;
 }
 void ready(NativeVillageHomeView view,NativeVillageHomeAssetsTest.Delayed source){
  source.finishAll(view.assets);frame();assertTrue(view.sceneReady());assertNull(view.artworkRefresh);
 }
 @Test public void decodeBurstUsesOnePendingTaskAndPublishesTheCompleteInitialScene()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(activity.get(),source);
   Runnable first=view.artworkRefresh;assertNotNull(first);source.finishAll(view.assets);
   for(int i=0;i<30;i++){view.scheduleArtworkRefresh();assertSame("A completion burst keeps exactly one frame task",first,view.artworkRefresh);}
   assertTrue(view.assets.ready());assertFalse("Decode completion does not synchronously lay out each image",view.committedScene);
   frame();assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());assertNull(view.artworkRefresh);
   int count=source.paths.size();frame();assertEquals(count,source.paths.size());assertNull(view.artworkRefresh);
  }
 }
 @Test public void reverseBeforeQueuedFramePublishesOnlyTheLatestDesiredWindow()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(activity.get(),source);ready(view,source);
   double original=view.camera.cameraUnits();view.moveCameraUnits(4);assertFalse(view.targetReady);source.finishAll(view.assets);Runnable first=view.artworkRefresh;assertNotNull(first);
   view.moveCameraUnits(-4);assertEquals(original,view.desiredCamera.cameraUnits(),0);assertEquals(original,view.camera.cameraUnits(),0);
   view.scheduleArtworkRefresh();assertSame(first,view.artworkRefresh);frame();assertTrue(view.sceneReady());assertEquals(original,view.camera.cameraUnits(),0);
   for(double delta:new double[]{8,-16,8}){view.moveCameraUnits(delta);source.finishAll(view.assets);frame();assertTrue(view.sceneReady());assertEquals(original+delta,view.camera.cameraUnits(),0);original+=delta;}
  }
 }
 @Test public void pauseCancelsTheQueuedTaskAndResumeCanPublishNewCompletions()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);
   source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());Runnable obsolete=view.artworkRefresh;assertNotNull(obsolete);
   view.owner.pause();assertNull(view.artworkRefresh);source.finishAll(view.assets);obsolete.run();frame();assertFalse(view.sceneReady());assertFalse(view.committedScene);
   view.owner.resume();assertTrue(view.assets.active);source.finishAll(view.assets);frame();assertTrue(view.sceneReady());assertNull(view.artworkRefresh);
  }
 }
 @Test public void detachAndChangedAccountDiscardAlreadyQueuedTasksAndLateDecodes()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);
   Runnable obsolete=view.artworkRefresh;assertNotNull(obsolete);h.root.removeView(view);assertNull(view.artworkRefresh);source.finishAll(view.assets);obsolete.run();frame();assertFalse(view.committedScene);assertFalse(view.assets.active);
  }
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);
   source.finishAll(view.assets);assertNotNull(view.artworkRefresh);((NativeVillageHomeTest.Api)h.api).account="another-owner.signature";
   frame();assertFalse(view.committedScene);assertFalse(view.sceneReady());assertNull(view.artworkRefresh);view.scheduleArtworkRefresh();assertNull(view.artworkRefresh);
  }
 }
 @Test public void failureAndRetryUseTheSameFrameQueueWithoutLosingTheirState()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.fixture.helper.flow.start()){
   NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(activity.get(),source);
   // Finish superseded fixture-generation requests before failing a request from the active load.
   while(source.completed<activeRequestStart)source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());
   source.callbacks.get(source.completed++).accept(null);source.finishAll(view.assets);frame();assertFalse(view.sceneReady());assertEquals(View.VISIBLE,view.loadingRetry.getVisibility());
   view.loadingRetry.performClick();source.finishAll(view.assets);frame();assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());assertNull(view.artworkRefresh);
  }
 }
}
