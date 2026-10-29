package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Loading gates are driven by decode completion, never by a presentation timer. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageHomeLoadingTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 NativeVillageHomeView open(MainActivity h,NativeVillageHomeAssets.Source source)throws Exception{return open(h,source,NativeVillageHomeTest.PHONE);}
 NativeVillageHomeView open(MainActivity h,NativeVillageHomeAssets.Source source,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  fixture.prepare(h);h.home();h.villageHome.pause();h.root.removeAllViews();h.epoch++;h.screen="home";
  NativeVillageHomeController owner=new NativeVillageHomeController(h);VillageLayout layout=VillageLayout.parse(NativeVillageHomeAssetsTest.manifest());owner.layout=layout;
  NativeVillageHomeView view=new NativeVillageHomeView(h,owner,layout,new NativeVillageHomeAssets(source));owner.view=view;h.villageHome=owner;h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));
  fixture.helper.layout(h,profile);view.assets.load(layout,owner::current,view::updateNotice);fixture.helper.layout(h,profile);return view;
 }
 void capture(MainActivity h,String name)throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"art-loading");Files.createDirectories(out);Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);
  try{NativeVillageHomeArtTest.png(out.resolve(name+".png"),bitmap);}finally{bitmap.recycle();}
 }
 /**
  * 휴대폰에서 미는 동안 다음 구역 그림이 늦게 디코드되면 불투명 로딩 화면이 번쩍이며 미끄러짐이 끊겼다.
  * 가정: 약 1.5U/초(프레임당 .025U, 60fps), 그림 한 장 디코드 5프레임(~80ms), 동시 2장. 8U 한 바퀴 동안 로딩 화면이 다시 나오면 안 된다.
  */
 @Test public void slidingAtPhoneSpeedWithRealisticDecodeLatencyNeverFlashesTheLoadingScreen()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);
   source.finishAll(view.assets);fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());
   java.util.List<Integer> requestedAt=new java.util.ArrayList<>();for(int i=0;i<source.callbacks.size();i++)requestedAt.add(-100);
   int flashes=0,peak=view.assets.images.size();boolean shown=false;
   for(int frame=0;frame<320;frame++){
    for(int dir:new int[]{1}){view.moveCameraUnits(.025*dir);}
    while(requestedAt.size()<source.callbacks.size())requestedAt.add(frame);
    while(source.completed<source.callbacks.size()&&frame-requestedAt.get(source.completed)>=5){source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());while(requestedAt.size()<source.callbacks.size())requestedAt.add(frame);}
    boolean loading=view.loadingPanel.getVisibility()==View.VISIBLE;if(loading&&!shown)flashes++;shown=loading;peak=Math.max(peak,view.assets.images.size());
   }
   assertEquals("Loading screen must not flash while sliding",0,flashes);
  }
 }
 @Test public void delayedFailureShowsRetryAndBackWithoutWorldAndRetryRevealsOnlyCompleteScene()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);
   assertFalse(view.sceneReady());assertEquals(View.VISIBLE,view.loadingPanel.getVisibility());assertEquals(View.GONE,view.loadingRetry.getVisibility());capture(h,"loading-915x412");
   Bitmap before=NativeHomePartyFeedbackTest.render(h.root);source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());fixture.helper.layout(h,NativeVillageHomeTest.PHONE);Bitmap partial=NativeHomePartyFeedbackTest.render(h.root);
   try{assertTrue("A partially decoded building never replaces the loading screen",before.sameAs(partial));}finally{before.recycle();partial.recycle();}
   source.callbacks.get(source.completed++).accept(null);fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
   assertEquals(View.VISIBLE,view.loadingRetry.getVisibility());assertEquals(View.VISIBLE,view.loadingBack.getVisibility());assertEquals(View.INVISIBLE,view.settings.getVisibility());assertTrue(view.loadingMessage.getText().toString().contains("불러오지 못했어요"));capture(h,"retry-915x412");
   view.loadingBack.performClick();assertNotNull(h.visibleDialog);assertTrue(h.visibleDialog.isShowing());h.visibleDialog.dismiss();assertFalse(view.sceneReady());
   int firstRequests=source.paths.size();view.loadingRetry.performClick();assertTrue(source.paths.size()>firstRequests);assertEquals(View.GONE,view.loadingRetry.getVisibility());source.finishAll(view.assets);fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
   assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());assertEquals(View.VISIBLE,view.settings.getVisibility());
  }
 }
 @Test public void currentAccountAndDetachPreventLateDecodeFromReopeningScene()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source);int generation=view.assets.generation;
   h.root.removeView(view);assertTrue(view.assets.generation>generation);assertFalse(view.assets.active);
   source.finishAll(view.assets);assertFalse(view.sceneReady());assertTrue(view.assets.images.isEmpty());assertEquals(-1,h.root.indexOfChild(view));
  }
 }
 @Test public void fullyCachedSceneSkipsLoadingAtFirstLayoutAndResume()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();Bitmap pixels=NativeVillageHomeAssetsTest.image();NativeVillageHomeView view=open(h,new NativeVillageHomeAssets.Source(){
    public Bitmap peek(String path){return pixels;}
    public void load(String path,java.util.function.Consumer<Bitmap> done){throw new AssertionError("Cached art must be synchronous");}
   });
   assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());view.owner.pause();view.owner.resume();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());
  }
 }
 @Test public void loadingAndRetryStayVisibleOnLargeAndCompactLandscapeProfiles()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PROFILES[1],NativeVillageHomeTest.PROFILES[2],new NativeHomePartyFeedbackTest.Profile("village-loading-640x320",640,320,160,1,24,14,20,12)};
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.helper.configure(profile);try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();NativeVillageHomeView view=open(h,source,profile);fixture.helper.layout(h,profile);
   NativeNoScrollReviewTest.assertFullyVisible(view.loadingMessage,h.root);capture(h,"loading-"+profile.name);
   source.callbacks.get(source.completed++).accept(null);fixture.helper.layout(h,profile);NativeNoScrollReviewTest.assertFullyVisible(view.loadingMessage,h.root);NativeNoScrollReviewTest.assertFullyVisible(view.loadingRetry,h.root);NativeNoScrollReviewTest.assertFullyVisible(view.loadingBack,h.root);capture(h,"retry-"+profile.name);
  }}
 }

 NativeVillageHomeView delayedState(MainActivity h,boolean statueVisible)throws Exception{
  NativeVillageHomeTest.Api api=fixture.prepare(h);api.deferStory=true;
  NativeVillageHomeController initial=new NativeVillageHomeController(h);
  initial.preferences.edit().putString(initial.scope+":camera",statueVisible?"2.25":"0").commit();
  h.home();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);return h.villageHome.view;
 }
 @Test public void onlyVisibleUnknownStatueWaitsForInitialStateAndResumeKeepsKnownStage()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeView view=delayedState(h,true);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
   assertTrue(view.assets.ready());assertTrue(view.needsInitialStatueState());assertFalse(view.sceneReady());assertEquals(View.VISIBLE,view.loadingPanel.getVisibility());assertTrue(view.loadingMessage.getText().toString().contains("상태를 확인하는 중"));capture(h,"visible-statue-state-loading");
   api.storyReads.get(0).done(NativeVillageHomeTest.story(),null);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.sceneReady());assertNotNull(view.assets.get(view.visibleStatue()));assertEquals(View.GONE,view.loadingPanel.getVisibility());
   view.owner.pause();view.owner.resume();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue("Same-account resume does not wait for a new state read",view.sceneReady());assertTrue(view.owner.reading);assertEquals(View.GONE,view.loadingPanel.getVisibility());
  }
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeView view=delayedState(h,false);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
   assertEquals(1,api.storyReads.size());assertTrue(view.owner.reading);assertFalse(view.needsInitialStatueState());assertTrue("An offscreen unknown statue never blocks this district",view.sceneReady());
  }
 }
 @Test public void firstOffscreenSceneMayReachUnknownStatueWithoutStallingAndBindsItsLaterState()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeView view=delayedState(h,false);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
   assertTrue(view.sceneReady());assertEquals(0,view.owner.statueStage);assertEquals(1,api.storyReads.size());
   view.moveCameraUnits(2.25);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
   assertEquals("Later travel reaches the requested region while only an unknown statue is omitted",2.25,view.camera.cameraUnits(),0);
   assertFalse(view.needsInitialStatueState());assertTrue(view.sceneReady());assertEquals(View.GONE,view.loadingPanel.getVisibility());assertEquals(View.INVISIBLE,view.statue.getVisibility());
   assertEquals("Travel never starts another story read or polling",1,api.storyReads.size());assertTrue(api.writes.isEmpty());
   api.storyReads.get(0).done(NativeVillageHomeTest.story(),null);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
   assertTrue(view.sceneReady());assertEquals(2.25,view.camera.cameraUnits(),0);assertEquals(1,view.drawnStatueStage());assertEquals(View.VISIBLE,view.statue.getVisibility());
   assertNotNull(view.assets.get(view.visibleStatue()));assertEquals(View.GONE,view.loadingPanel.getVisibility());assertEquals(1,api.storyReads.size());
  }
 }
 @Test public void initialStatueStateFailureOffersRetryAndLateAccountReplyCannotRevealIt()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeView view=delayedState(h,true);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
   api.storyReads.get(0).done(null,"NETWORK_TIMEOUT");fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.owner.statueReadFailed);assertFalse(view.sceneReady());assertEquals(View.VISIBLE,view.loadingRetry.getVisibility());assertEquals(View.VISIBLE,view.loadingBack.getVisibility());assertTrue(view.loadingMessage.getText().toString().contains("상태를 확인하지 못했어요"));capture(h,"visible-statue-state-retry");
   view.loadingRetry.performClick();assertEquals("Retry starts one explicit read, not a poll",2,api.storyReads.size());assertFalse(view.owner.statueReadFailed);assertTrue(view.owner.reading);
   api.account="new-account.1.signature";api.storyReads.get(1).done(NativeVillageHomeTest.story(),null);LocalGameArtTest.drain();assertEquals(0,view.owner.statueStage);assertFalse(view.sceneReady());assertTrue(api.writes.isEmpty());
  }
 }

 @Test public void missingInitialStatueStageShowsARecoverableErrorWithoutPolling()throws Exception{
  try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
   MainActivity h=activity.get();NativeVillageHomeView view=delayedState(h,true);NativeVillageHomeTest.Api api=(NativeVillageHomeTest.Api)h.api;
   org.json.JSONObject incomplete=NativeVillageHomeTest.story();incomplete.remove("statue");api.storyReads.get(0).done(incomplete,null);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
   assertTrue(view.owner.statueReadFailed);assertFalse(view.sceneReady());assertEquals(1,api.storyReads.size());assertEquals(View.VISIBLE,view.loadingRetry.getVisibility());
   view.loadingRetry.performClick();assertEquals(2,api.storyReads.size());api.storyReads.get(1).done(NativeVillageHomeTest.story(),null);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertTrue(view.sceneReady());assertFalse(view.owner.statueReadFailed);assertTrue(api.writes.isEmpty());
  }
 }

}
