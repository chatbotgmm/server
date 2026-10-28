package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60BattleArtGateTest {
 final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
 @Before public void setup(){helper.setup();} @After public void restore(){helper.restore();}
 void ready(MainActivity h)throws Exception{helper.helper.settle(h,NativeHomePartyFeedbackTest.PROFILES[0]);RegistrationArtTest.await(()->h.rosterBattle.art.assetsReady());}
 @Test public void incompletePublicPairHidesBattleUntilReadyWithoutATimer()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;Bitmap opponent=ui.art.images[1];
   ui.art.images[1]=null;ui.art.imagePending[1]=1;ui.art.readinessChanged();
   assertSame(h.root,ui.artGate.getParent());assertEquals(View.INVISIBLE,ui.arena.getVisibility());assertEquals(View.GONE,ui.artGateRetry.getVisibility());
   helper.helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);Bitmap capture=NativeHomePartyFeedbackTest.render(h.root);try{helper.helper.png(capture,"battle-critical-art-loading.png");}finally{capture.recycle();}
   ui.art.images[1]=opponent;ui.art.imagePending[1]=0;ui.art.readinessChanged();
   assertEquals(View.GONE,ui.artGate.getVisibility());assertEquals(View.VISIBLE,ui.arena.getVisibility());
  }
 }
 @Test public void failedAssetsRetryLocallyAndOldScreenCannotReopenGate()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;int requests=api.paths.size();
   ui.art.images[1]=null;ui.art.imagePending[1]=0;ui.art.readinessChanged();assertTrue(ui.art.assetsFailed());assertEquals(View.VISIBLE,ui.artGateRetry.getVisibility());
   ui.artGateRetry.performClick();RegistrationArtTest.await(()->ui.art.assetsReady());assertEquals(requests,api.paths.size());assertEquals(View.GONE,ui.artGate.getVisibility());
   ui.pause();h.epoch++;h.screen="home";ui.art.images[1]=null;ui.art.readinessChanged();assertEquals(View.GONE,ui.artGate.getVisibility());
  }
 }
 @Test public void resourcePauseDoesNotSkipAnAnimatingTurn()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;
   ui.bind(NativeRoster60PlaybackTest.response(api.state,false));assertTrue(ui.presenting());Bitmap opponent=ui.art.images[1];
   ui.art.images[1]=null;ui.art.imagePending[1]=1;ui.art.readinessChanged();assertTrue(ui.playback.animator.isPaused());
   ui.art.images[1]=opponent;ui.art.imagePending[1]=0;ui.art.readinessChanged();assertFalse(ui.playback.animator.isPaused());
   NativeRoster60PlaybackTest.finish(ui);assertFalse(ui.presenting());
  }
 }
 @Test public void allFiveApprovedStoryAliasesClearTheSameRequiredArtGateAndRetryLocally()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;int requests=api.paths.size();
   for(String id:new String[]{"story_record","story_guide","story_engineer","story_escort","story_dark_first"}){
    org.json.JSONObject state=new org.json.JSONObject(api.state.getJSONObject("state").toString());
    state.getJSONArray("fighters").getJSONObject(1).put("speciesId",id);
    ui.art.bind(state);RegistrationArtTest.await(()->ui.art.assetsReady());
    assertFalse(id,ui.art.assetsFailed());assertEquals(id,View.GONE,ui.artGate.getVisibility());assertEquals(View.VISIBLE,ui.arena.getVisibility());
    ui.art.retryAssets();RegistrationArtTest.await(()->ui.art.assetsReady());assertEquals(requests,api.paths.size());
    assertEquals(id,state.getJSONArray("fighters").getJSONObject(1).getString("speciesId"));
   }
  }
 }

 @Test public void authorizedUnknownReplaySideDoesNotWaitForOrRevealAnotherArt()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;
   ui.art.frameEvent=MainActivity.obj("presentation",MainActivity.obj("version",0,"uncertainSides",new org.json.JSONArray().put(false).put(true)));
   ui.art.images[1]=null;ui.art.imagePending[1]=0;ui.art.readinessChanged();
   assertTrue(ui.art.assetsReady());assertFalse(ui.art.assetsFailed());assertTrue(ui.art.uncertain(1));assertEquals(View.GONE,ui.artGate.getVisibility());
  }
 }
 @Test public void sameArtFramesDoNotCreateAGateOrPauseAndRetryPreservesHealthyCache()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();NativeRoster60PlaybackTest.Api api=helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;
   Bitmap healthy=BattleSkin.bitmap(h,"bg-harbor");BattleSkin.retryMissing("bg-harbor");assertSame(healthy,BattleSkin.bitmap(h,"bg-harbor"));
   ui.bind(NativeRoster60PlaybackTest.response(api.state,false));
   for(int i=0;i<5;i++){ui.refreshArtGate();assertEquals(View.GONE,ui.artGate.getVisibility());assertFalse(ui.playback.animator.isPaused());}
   ui.pause();assertFalse(ui.presenting());assertEquals(View.GONE,ui.artGate.getVisibility());
  }
 }

 @Test public void coldBattleSkinsDecodeOffMainAndInvalidateTheirDrawable()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);LocalGameArtTest.drain();
   synchronized(BattleSkin.CACHE){BattleSkin.CACHE.clear();}
   android.util.LruCache<?,?> local=org.robolectric.util.ReflectionHelpers.getStaticField(LocalGameArt.class,"CACHE");local.evictAll();
   long mainBefore=BattleSkin.mainThreadDecodes,decodesBefore=BattleSkin.decodeCount;
   final int[] invalidations={0};final android.graphics.drawable.Drawable.Callback retainedCallback=new android.graphics.drawable.Drawable.Callback(){
    public void invalidateDrawable(android.graphics.drawable.Drawable drawable){assertSame(android.os.Looper.getMainLooper(),android.os.Looper.myLooper());invalidations[0]++;}
    public void scheduleDrawable(android.graphics.drawable.Drawable drawable,Runnable task,long when){}public void unscheduleDrawable(android.graphics.drawable.Drawable drawable,Runnable task){}
   };
   int primaryCold=BattleSkin.CACHE.size(),sharedCold=LocalGameArt.cachedBytes();
   BattleSkin skin=new BattleSkin(h,"panel-hp");skin.setCallback(retainedCallback);
   h.api=new NativeRoster60PlaybackTest.Api(h);h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();NativeRoster60Battle ui=h.rosterBattle;
   assertSame(h.root,ui.artGate.getParent());assertEquals(View.INVISIBLE,ui.arena.getVisibility());
   ready(h);
   for(String name:BattleSkin.REQUIRED)assertNotNull(name,BattleSkin.peek(name));
   assertNotNull(NativeRoster60ProductBattleTest.named(ui.panel,"공격").getCompoundDrawables()[0]);
   java.nio.file.Path output=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-cold-preparation");java.nio.file.Files.createDirectories(output);
   java.nio.file.Files.write(output.resolve("evidence.json"),MainActivity.obj("mainThreadSkinDecodes",BattleSkin.mainThreadDecodes-mainBefore,"workerSkinDecodes",BattleSkin.decodeCount-decodesBefore,"requiredSkinCount",BattleSkin.REQUIRED.length,"drawableInvalidations",invalidations[0],"primaryEntriesBefore",primaryCold,"sharedBytesBefore",sharedCold,"callbackRetained",skin.getCallback()==retainedCallback,"realDeviceFrameTimingMeasured",false).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   assertEquals(0,primaryCold);assertEquals(0,sharedCold);assertSame(retainedCallback,skin.getCallback());assertEquals(mainBefore,BattleSkin.mainThreadDecodes);assertTrue("Worker decodes="+(BattleSkin.decodeCount-decodesBefore),BattleSkin.decodeCount>decodesBefore);assertTrue("Drawable invalidations="+invalidations[0],invalidations[0]>0);assertEquals(View.GONE,ui.artGate.getVisibility());
  }
 }

 @Test public void requiredSkinsFitBudgetAndStayReadyUnderNoncriticalCachePressure()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);ready(h);NativeRoster60Battle ui=h.rosterBattle;
   long requiredBytes=0;for(String name:BattleSkin.REQUIRED)requiredBytes+=BattleSkin.peek(name).getAllocationByteCount();assertTrue(requiredBytes<BattleSkin.CACHE_BUDGET);
   Bitmap pressure=Bitmap.createBitmap(2048,2048,Bitmap.Config.ARGB_8888);
   synchronized(BattleSkin.CACHE){BattleSkin.CACHE.put("noncritical-pressure-test",pressure);long bytes=0;for(Bitmap image:BattleSkin.CACHE.values())if(image!=null)bytes+=image.getAllocationByteCount();assertTrue(bytes<=BattleSkin.CACHE_BUDGET);}
   ui.refreshArtGate();assertTrue(ui.art.assetsReady());assertEquals(View.GONE,ui.artGate.getVisibility());
   java.nio.file.Path dir=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-cold-preparation");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("cache-budget.json"),MainActivity.obj("requiredBytes",requiredBytes,"budgetBytes",BattleSkin.CACHE_BUDGET,"pressureBytes",pressure.getAllocationByteCount(),"requiredReadyAfterPressure",true).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   synchronized(BattleSkin.CACHE){BattleSkin.CACHE.remove("noncritical-pressure-test");}
  }
 }

 @Test public void legacyCommandIconsAndReplayHarborRecoverFromColdCache()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);LocalGameArtTest.drain();
   synchronized(BattleSkin.CACHE){BattleSkin.CACHE.clear();}
   android.util.LruCache<?,?> local=org.robolectric.util.ReflectionHelpers.getStaticField(LocalGameArt.class,"CACHE");local.evictAll();long mainBefore=BattleSkin.mainThreadDecodes;
   BattleReadabilityTest fixture=new BattleReadabilityTest();BattleView legacy=fixture.mount(h,915,412,false);legacy.pane="menu";legacy.render();
   android.widget.Button attack=NativeRoster60ProductBattleTest.named(legacy,"공격");assertNotNull(attack);LocalGameArtTest.drain();assertNotNull(attack.getCompoundDrawables()[1]);
   synchronized(BattleSkin.CACHE){BattleSkin.CACHE.remove("bg-harbor");}local.evictAll();
   NativeBattleReplay.Arena replay=new NativeBattleReplay.Arena(h,MainActivity.obj("scene","harbor","teams",new org.json.JSONArray()));replay.layout(0,0,400,200);
   Bitmap before=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888),after=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);
   try{replay.draw(new android.graphics.Canvas(before));LocalGameArtTest.drain();replay.draw(new android.graphics.Canvas(after));assertFalse(before.sameAs(after));assertNotNull(BattleSkin.peek("bg-harbor"));}finally{before.recycle();after.recycle();}
   assertEquals(mainBefore,BattleSkin.mainThreadDecodes);
  }
 }
}
