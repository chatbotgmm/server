package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** A desired window may load late; only a complete committed window owns the visible scene. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageHomeAssetsWindowTest {
  VillageLayout layout() throws Exception {
    return VillageLayout.parse(NativeVillageHomeAssetsTest.manifest());
  }

  NativeVillageHomeAssets open(VillageLayout layout, NativeVillageHomeAssetsTest.Delayed source,
      java.util.function.BooleanSupplier current) {
    NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
    assets.setViewport(.5,1);
    assets.load(layout,current,()->{});
    return assets;
  }

  void completeAndCommit(NativeVillageHomeAssets assets, NativeVillageHomeAssetsTest.Delayed source) {
    source.finishAll(assets);
    assertTrue(assets.ready());
    assertTrue(assets.commitWindow(""));
    assertTrue(assets.committedReady());
  }

  void assertBounded(NativeVillageHomeAssets assets) {
    Set<String> allowed=new HashSet<>(assets.wantedImages);
    allowed.addAll(assets.committedImages);
    if(!assets.currentStatue.isEmpty())allowed.add(assets.currentStatue);
    assertTrue("Only the committed and latest wanted window may pin bitmaps",
        allowed.containsAll(assets.images.keySet()));
    assertTrue("The window queue shares a two-decode bound",assets.inFlight.size()<=2);
  }

  @After public void clearSharedCache() throws Exception {
    LocalGameArtTest.drain();
    LocalGameArt.trim();
  }

  @Test public void onlyExplicitCompleteCommitPinsTheSceneAndLateWindowKeepsOriginalPixels() throws Exception {
    VillageLayout layout=layout();
    NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();
    NativeVillageHomeAssets assets=open(layout,source,()->true);
    assertFalse(assets.commitWindow(""));
    assertTrue(assets.committedImages.isEmpty());
    completeAndCommit(assets,source);
    Set<String> original=new HashSet<>(assets.committedImages);
    Bitmap recruit=assets.get("recruit");assertNotNull(recruit);

    assets.setViewport(4.5,1);
    assertFalse(assets.ready());
    assertFalse(assets.commitWindow(""));
    assertTrue("A complete old scene remains available while the next window loads",assets.committedReady());
    assertEquals(original,assets.committedImages);
    assertSame(recruit,assets.get("recruit"));
    source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());
    assertTrue(assets.committedReady());assertEquals(original,assets.committedImages);
    assertSame(recruit,assets.get("recruit"));assertBounded(assets);

    source.finishAll(assets);assertTrue(assets.ready());assertTrue(assets.commitWindow(""));
    assertEquals(assets.requiredImages,assets.committedImages);
    assertFalse("Commit releases the old district once it is outside the wanted apron",
        assets.images.containsKey("recruit"));
    assertBounded(assets);
  }

  @Test public void failedNextWindowRetriesExplicitlyWithoutDroppingOrReloadingTheCommittedOne() throws Exception {
    VillageLayout layout=layout();
    NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();
    NativeVillageHomeAssets assets=open(layout,source,()->true);completeAndCommit(assets,source);
    Set<String> original=new HashSet<>(assets.committedImages);Bitmap recruit=assets.get("recruit");
    assets.setViewport(4.5,1);
    source.callbacks.get(source.completed++).accept(null);source.finishAll(assets);
    assertTrue(assets.hasFailure(""));assertFalse(assets.ready());assertTrue(assets.committedReady());
    assertEquals(original,assets.committedImages);assertSame(recruit,assets.get("recruit"));
    int requests=source.paths.size();
    for(int i=0;i<25;i++)assets.setViewport(4.5,1);
    assertEquals("Drawing/layout does not spin failed decodes",requests,source.paths.size());
    assets.retryWindow();
    assertTrue("A deliberate retry schedules failed wanted art",source.paths.size()>requests);
    assertTrue(assets.committedReady());assertEquals(original,assets.committedImages);
    assertSame(recruit,assets.get("recruit"));
    for(int i=requests;i<source.paths.size();i++)
      assertFalse("A retry does not reload the already retained original building",
          source.paths.get(i).equals(layout.assets.get("recruit").path));
    source.finishAll(assets);assertTrue(assets.commitWindow(""));assertBounded(assets);
  }

  @Test public void rapidReversalDoesNotInstallAnObsoleteWindowOrQueueMoreThanTwoDecodes() throws Exception {
    VillageLayout layout=layout();
    NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();
    NativeVillageHomeAssets assets=open(layout,source,()->true);completeAndCommit(assets,source);
    Bitmap original=assets.get("recruit");Set<String> committed=new HashSet<>(assets.committedImages);
    int began=source.paths.size();assets.setViewport(4.5,1);
    assertEquals(2,source.paths.size()-began);
    assets.setViewport(6.5,1);assets.setViewport(.5,1);
    assertEquals("Viewport churn waits for the existing bounded decode queue",2,source.paths.size()-began);
    assertTrue(assets.ready());assertTrue(assets.committedReady());assertSame(original,assets.get("recruit"));
    assertTrue(assets.commitWindow(""));assertEquals(committed,assets.committedImages);
    source.finishAll(assets);
    assertTrue(assets.ready());assertSame(original,assets.get("recruit"));
    assertFalse("A callback for the abandoned far window cannot pin it",assets.images.containsKey("archive"));
    assertFalse(assets.images.containsKey("harbor"));assertBounded(assets);
  }

  @Test public void bothEightUnitDirectionsAndRepeatedTravelKeepOnlyCurrentAndWantedImages() throws Exception {
    VillageLayout layout=layout();List<String> requests=new ArrayList<>();
    NativeVillageHomeAssets assets=new NativeVillageHomeAssets((path,done)->{requests.add(path);done.accept(NativeVillageHomeAssetsTest.image());});
    assets.setViewport(.5,1);assets.load(layout,()->true,()->{});assertTrue(assets.commitWindow(""));
    int initial=requests.size();Set<String> first=new HashSet<>(assets.committedImages);
    for(double camera:new double[]{8.5,-7.5,16.5,-15.5}){
      assets.setViewport(camera,1);assertTrue(assets.commitWindow(""));
      assertEquals("Eight-unit equivalents use the same original window",first,assets.committedImages);
      assertEquals("Wrapping is projection, not another decode",initial,requests.size());assertBounded(assets);
    }
    for(int step=-32;step<=32;step++){
      assets.setViewport(step*.25,1);assertTrue(assets.ready());assertTrue(assets.commitWindow(""));assertBounded(assets);
      assertTrue("A view never retains the entire eight-unit pack",assets.images.size()<layout.assets.size());
    }
  }

  @Test public void aCachedRequiredWindowCanCommitWhileTwoObsoleteDecodesAreStillOutstanding() throws Exception {
    VillageLayout layout=layout();NativeVillageHomeAssetsTest.Delayed delayed=new NativeVillageHomeAssetsTest.Delayed();
    Map<String,Bitmap> cached=new HashMap<>();
    NativeVillageHomeAssets assets=new NativeVillageHomeAssets(new NativeVillageHomeAssets.Source(){
      public Bitmap peek(String path){return cached.get(path);}
      public void load(String path,Consumer<Bitmap> ready){delayed.load(path,ready);}
    });
    assets.setViewport(.5,1);assets.load(layout,()->true,()->{});completeAndCommit(assets,delayed);
    assets.setViewport(4.5,1);int requests=delayed.paths.size();
    assertEquals(2,requests-delayed.completed);
    for(VillageLayout.Asset asset:layout.assets.values())cached.put(asset.path,NativeVillageHomeAssetsTest.image());
    Set<String> recovered=assets.selectLayered(6.5,1);recovered.removeIf(id->assets.get(id)!=null);assets.failed.addAll(recovered);
    assets.setViewport(6.5,1);
    assertTrue("Ready public cache pixels must not wait for obsolete decoder slots",assets.ready());
    assertTrue(assets.commitWindow(""));assertEquals(requests,delayed.paths.size());
    for(String id:recovered)assertFalse("A cache recovery also clears its obsolete failure marker",assets.failed.contains(id));
    delayed.finishAll(assets);assertTrue(assets.committedReady());assertBounded(assets);
  }

  @Test public void sceneAndStatueShareTheDecodeLimitAndTheOldCommittedStatueSurvivesReplacement() throws Exception {
    VillageLayout layout=layout();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();
    NativeVillageHomeAssets assets=open(layout,source,()->true);
    assertEquals(2,source.paths.size());assets.loadStatue(layout,1,()->true,()->{});
    assertEquals("A statue joins the existing two-decode queue",2,source.paths.size());
    source.finishAll(assets);assertTrue(assets.commitWindow("statue-stage-1"));
    Bitmap first=assets.get("statue-stage-1");assertNotNull(first);
    assets.setViewport(4.5,1);assets.loadStatue(layout,2,()->true,()->{});
    assertSame("The shown statue cannot disappear while the next one loads",first,assets.get("statue-stage-1"));
    assertTrue(assets.committedReady());assertFalse(assets.commitWindow("statue-stage-2"));
    assertTrue("Actual outstanding requests, including statue, remain bounded",source.paths.size()-source.completed<=2);
    source.finishAll(assets);assertTrue(assets.commitWindow("statue-stage-2"));
    assertFalse(assets.images.containsKey("statue-stage-1"));assertNotNull(assets.get("statue-stage-2"));assertBounded(assets);
  }

  @Test public void cancellationAndChangedOwnerRejectLateCallbacksAndDoNotReuseTheOldCommittedWindow() throws Exception {
    VillageLayout layout=layout();NativeVillageHomeAssetsTest.Delayed source=new NativeVillageHomeAssetsTest.Delayed();
    boolean[] current={true};NativeVillageHomeAssets assets=open(layout,source,()->current[0]);completeAndCommit(assets,source);
    assets.setViewport(4.5,1);int oldEnd=source.paths.size();assets.cancel();
    Set<String> retained=new HashSet<>(assets.images.keySet());
    source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());
    assertEquals(retained,assets.images.keySet());assertFalse(assets.committedReady());
    current[0]=false;assets.setViewport(.5,1);assets.load(layout,()->true,()->{});
    assertTrue("An account change clears the previous scene commitment",assets.committedImages.isEmpty());
    assertFalse(assets.committedReady());assertTrue("A different owner starts its own initial readiness gate",assets.images.isEmpty());
    while(source.completed<oldEnd)source.callbacks.get(source.completed++).accept(NativeVillageHomeAssetsTest.image());
    assertTrue("An old-account decode delivered after the new load cannot fill its scene",assets.images.isEmpty());
    assertTrue(assets.committedImages.isEmpty());
    source.finishAll(assets);assertTrue(assets.commitWindow(""));assertBounded(assets);
  }

  @Test public void sharedCacheTrimCannotEvictTheCommittedStrongReferences() throws Exception {
    VillageLayout layout=layout();LocalGameArt.trim();
    // Seed original-sized ownership-independent test pixels into the real shared LRU.
    // The production selector still requests only the visible window and .35U apron.
    for(VillageLayout.Asset asset:layout.assets.values())
      LocalGameArt.work(LocalGameArt.key(asset.path,2),NativeVillageHomeAssetsTest::image,image->assertNotNull(image));
    LocalGameArtTest.drain();assertTrue(LocalGameArt.cachedBytes()>0);
    NativeVillageHomeAssets assets=new NativeVillageHomeAssets(new NativeVillageHomeAssets.Source(){
      public Bitmap peek(String path){return LocalGameArt.peek(path,2);}
      public void load(String path,Consumer<Bitmap> ready){throw new AssertionError("Seeded current pixels must be synchronous");}
    });
    assets.setViewport(.5,1);assets.load(layout,()->true,()->{});assertTrue(assets.commitWindow(""));
    Bitmap original=assets.get("recruit");Set<String> committed=new HashSet<>(assets.committedImages);
    LocalGameArt.trim();assertEquals(0,LocalGameArt.cachedBytes());
    assertSame(original,assets.get("recruit"));assertFalse(original.isRecycled());
    assertTrue(assets.committedReady());assertEquals(committed,assets.committedImages);assertBounded(assets);
  }
}
