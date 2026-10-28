package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Controlled local decode completions, including rapid viewport/account changes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NativeVillageHomeAssetsTest {
 static final class Delayed implements NativeVillageHomeAssets.Source {
  final List<String> paths=new ArrayList<>();final List<Consumer<Bitmap>> callbacks=new ArrayList<>();int completed;
  public void load(String path,Consumer<Bitmap> ready){paths.add(path);callbacks.add(ready);}
  void finishAll(NativeVillageHomeAssets assets){while(completed<callbacks.size()){assertTrue("At most two scene decodes in flight",assets.inFlight.size()<=2);callbacks.get(completed++).accept(image());}}
 }
 static Bitmap image(){return Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888);}
 static JSONObject manifest()throws Exception{try(java.io.InputStream in=RuntimeEnvironment.getApplication().getAssets().open(VillageLayout.MANIFEST)){return new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
 static JSONObject pendingManifest()throws Exception{JSONObject data=manifest();data.put("artworkStatus","pending-verified-pixels");JSONObject assets=data.getJSONObject("assets");for(java.util.Iterator<String> ids=assets.keys();ids.hasNext();)assets.getJSONObject(ids.next()).put("verified",false);return data;}
 static VillageLayout verifiedFixture()throws Exception{JSONObject data=pendingManifest();data.getJSONObject("assets").getJSONObject("recruit").put("verified",true);return VillageLayout.parse(data);}
 @Test public void explicitUnverifiedReferencesRemainPlaceholdersAfterProductionArtIsConnected()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);VillageLayout layout=VillageLayout.parse(pendingManifest());int[] changes={0};
  assets.load(layout,()->true,()->changes[0]++);assertTrue(source.paths.isEmpty());assertTrue(assets.images.isEmpty());assertEquals(assets.requiredImages.size(),assets.missing);assertFalse(assets.ready());assertEquals(1,changes[0]);
 }
 @Test public void oldGenerationAndChangedOwnerCannotInstallAnOutdatedBitmap()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);VillageLayout fixture=verifiedFixture();boolean[] current={true};int[] changes={0};
  assets.load(fixture,()->current[0],()->changes[0]++);assertEquals(1,assets.pending);assertEquals(fixture.assets.get("recruit").path,source.paths.get(0));
  Bitmap first=image(),latest=Bitmap.createBitmap(12,6,Bitmap.Config.ARGB_8888);
  current[0]=false;source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));int before=changes[0];
  current[0]=true;assets.load(fixture,()->current[0],()->changes[0]++);source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));assertEquals(before+1,changes[0]);
  source.callbacks.get(1).accept(latest);assertSame(latest,assets.get("recruit"));assertEquals(0,assets.pending);assertFalse(assets.ready());
 }
 @Test public void cancellationInvalidatesQueuedCompletionsWithoutRestartingDecode()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);int[] changes={0};assets.load(verifiedFixture(),()->true,()->changes[0]++);assets.cancel();
  source.callbacks.get(0).accept(image());assertEquals(0,assets.pending);assertTrue(assets.images.isEmpty());assertEquals(1,changes[0]);assertEquals(1,source.paths.size());
  assets.setViewport(4,1);assertEquals(1,source.paths.size());assertFalse(assets.ready());
 }
 @Test public void visibleDistrictLoadsBeforePrefetchAndDoesNotWaitForIt()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  assets.setViewport(.65,1);assets.load(layout,()->true,()->{});assertEquals(2,source.paths.size());
  assertTrue(assets.requiredImages.contains("recruit"));assertFalse(assets.requiredImages.contains("market"));assertTrue("8U seam prefetch",assets.wantedImages.contains("market"));
  assertTrue(assets.wantedImages.contains("archive"));assertFalse(assets.wantedImages.contains("harbor"));
  while(!assets.ready()){assertTrue(source.completed<source.callbacks.size());source.callbacks.get(source.completed++).accept(image());}
  assertTrue("Neighbor requests may still be decoding",source.completed<source.callbacks.size());
  int before=source.paths.size();for(int n=0;n<20;n++)assets.setViewport(.65,1);assertEquals("Repeated layout never restarts decode",before,source.paths.size());
  source.finishAll(assets);assertTrue(assets.ready());assertFalse(assets.images.containsKey("harbor"));
 }
 @Test public void cameraWrapAndRapidJumpDiscardNoLongerWantedCompletions()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  assets.setViewport(.5,1);assets.load(layout,()->true,()->{});source.finishAll(assets);
  Set<String> first=new HashSet<>(assets.wantedImages);assets.setViewport(8.5,1);assertEquals(first,assets.wantedImages);assertTrue(assets.ready());
  int before=source.paths.size();assets.setViewport(4.5,1);assertFalse(assets.ready());assertFalse(assets.images.containsKey("recruit"));assertTrue(source.paths.size()>before);
  assets.setViewport(5.1,1);assertTrue(assets.requiredImages.contains("harbor"));source.finishAll(assets);
  assertTrue(assets.ready());assertFalse("Old window completion cannot pin archive",assets.images.containsKey("archive"));assertFalse(assets.images.containsKey("recruit"));assertNotNull(assets.get("harbor"));
  for(String id:assets.images.keySet())assertTrue("Only current window assets retained",assets.wantedImages.contains(id));
 }
 @Test public void failedDecodeIsBoundedUntilFreshLoadAndNeverSpinsOnLayout()throws Exception{
  VillageLayout layout=verifiedFixture();Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  assets.load(layout,()->true,()->{});source.callbacks.get(0).accept(null);assertTrue(assets.failed.contains("recruit"));assertFalse(assets.ready());
  for(int i=0;i<30;i++){assets.setViewport(i%2==0?0:8,1);}assertEquals(1,source.paths.size());
  assets.load(layout,()->true,()->{});assertEquals("Fresh lifecycle allows one retry",2,source.paths.size());source.callbacks.get(1).accept(image());assertNotNull(assets.get("recruit"));
 }
 @Test public void synchronousCacheHitsDrainWithoutRecursionAndKeepSamplingQuality()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());List<String> requested=new ArrayList<>();
  NativeVillageHomeAssets assets=new NativeVillageHomeAssets((path,done)->{requested.add(path);done.accept(image());});
  assets.setViewport(2.5,1);assets.load(layout,()->true,()->{});assertTrue(assets.ready());assertTrue(assets.inFlight.isEmpty());assertFalse(requested.contains(layout.assets.get("market").path));
  assertEquals(2,NativeVillageHomeAssets.decodeSample("native/village-loop-v2/assets/companions-v3-background.png"));assertEquals(4,NativeVillageHomeAssets.decodeSample("native/village-loop-v2/assets/market-v4-shadows.png"));
 }
 @Test public void unchangedResumeKeepsReadyStaticBitmapsWithoutAnotherDecode()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  assets.setViewport(.5,1);assets.load(layout,()->true,()->{});source.finishAll(assets);Bitmap recruit=assets.get("recruit");int count=source.paths.size();
  assets.cancel();assets.load(layout,()->true,()->{});assertTrue(assets.ready());assertSame(recruit,assets.get("recruit"));assertEquals("Same view resumes from retained static pixels",count,source.paths.size());
 }
 @Test public void statuesLoadOnlyAuthenticatedCurrentStageAndDoNotBlockCoreReady()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  boolean[] current={true};assets.load(layout,()->current[0],()->{});source.finishAll(assets);assertTrue(assets.ready());
  for(String path:source.paths)assertFalse(path.contains("statue-stage-"));int n=source.paths.size();
  assets.loadStatue(layout,1,()->current[0],()->{});assertEquals(n+1,source.paths.size());assertTrue(assets.ready());
  assets.loadStatue(layout,1,()->current[0],()->{});assertEquals(n+1,source.paths.size());source.callbacks.get(n).accept(image());assertNotNull(assets.get("statue-stage-1"));
  assets.loadStatue(layout,3,()->current[0],()->{});assertNull("Only one statue is pinned",assets.get("statue-stage-1"));source.callbacks.get(n+1).accept(null);assertTrue(assets.statueErrors.contains("statue-stage-3"));assertTrue(assets.ready());
  assets.loadStatue(layout,3,()->current[0],()->{});assertEquals(n+2,source.paths.size());
  assets.loadStatue(layout,2,()->current[0],()->{});assets.loadStatue(layout,4,()->current[0],()->{});
  source.callbacks.get(n+2).accept(image());assertNull("Older stage response cannot replace current",assets.get("statue-stage-2"));source.callbacks.get(n+3).accept(image());assertNotNull(assets.get("statue-stage-4"));
  assets.loadStatue(layout,1,()->current[0],()->{});assertEquals("Returning to a successful evicted stage can load again",n+5,source.paths.size());
  assets.cancel();source.callbacks.get(n+4).accept(image());assertNull(assets.get("statue-stage-1"));current[0]=false;assets.loadStatue(layout,5,()->current[0],()->{});assertEquals(n+5,source.paths.size());
 }
}
