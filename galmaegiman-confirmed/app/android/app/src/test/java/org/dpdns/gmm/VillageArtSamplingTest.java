package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class VillageArtSamplingTest {
  VillageLayout layout()throws Exception{return VillageLayout.parse(NativeVillageHomeAssetsTest.manifest());}
  int sample(Map<String,Integer> plan,VillageLayout layout,String id){return plan.get(layout.assets.get(id).path);}
  @Test public void physicalPixelsReduceOnlyWhenBothAxesFitAndNeverSharpenExistingLargeArt()throws Exception {
    VillageLayout layout=layout();
    for(int[] size:new int[][]{{915,412},{2048,838},{1280,720},{3440,1440}}) {
      Map<String,Integer> plan=VillageArtSampling.plan(layout,size[0],size[1],2.625f);
      for(VillageLayout.DistrictScenery scene:layout.districtScenery) {
        VillageLayout.Asset source=layout.assets.get(scene.asset);int sample=plan.get(source.path);
        int baseline=NativeVillageHomeAssets.decodeSample(source.path,size[1]);
        assertTrue("This performance patch never adds pixels to sharpen existing art",sample>=baseline);
        assertTrue(sample==baseline||VillageArtSampling.croppedWidth(source)/sample>=scene.worldWidth*size[1]*VillageArtSampling.RESERVE);
        assertTrue(sample==baseline||VillageArtSampling.croppedHeight(source)/sample>=scene.heightRatio*size[1]*VillageArtSampling.RESERVE);
      }
      VillageLayout.Asset sky=layout.assets.get("layered-sky");
      assertEquals(Math.max(NativeVillageHomeAssets.decodeSample(sky.path,size[1]),VillageArtSampling.sample(Math.max(size[0]/(double)sky.sourceWidth,size[1]/(double)sky.sourceHeight))),sample(plan,layout,"layered-sky"));
    }
    // A deliberately non-uniform panel cannot use FIT_CENTER/min-axis sampling.
    JSONObject data=NativeVillageHomeAssetsTest.manifest();data.getJSONArray("districtScenery").getJSONObject(0).put("worldWidth",3).put("heightRatio",.2);
    VillageLayout stretched=VillageLayout.parse(data);
    assertEquals(2,sample(VillageArtSampling.plan(stretched,915,412,1),stretched,"layered-patrol"));
    assertEquals(1,sample(VillageArtSampling.plan(stretched,2048,838,2.625f),stretched,"layered-patrol"));
    // The old policy used resource metrics. A split window must not switch that baseline accidentally.
    assertEquals(1,sample(VillageArtSampling.plan(layout,915,412,1,838),layout,"layered-patrol"));
  }
  @Test public void smallPortraitsShrinkButPanelBackedArchiveKeepsItsExistingResolution()throws Exception {
    VillageLayout layout=layout();Map<String,Integer> phone=VillageArtSampling.plan(layout,915,412,1),high=VillageArtSampling.plan(layout,2048,838,2.625f);
    assertEquals(8,sample(phone,layout,"merchant"));assertEquals(4,sample(high,layout,"merchant"));
    String archivePath=layout.assets.get("archive").path;
    assertEquals("native/village-loop-v2/assets/layered-library.png",archivePath);
    // The existing layered-library path already used sample 1 at height >=600; retain that
    // baseline for the full panel instead of treating it like an ordinary sample-2 actor.
    assertEquals(2,NativeVillageHomeAssets.decodeSample(archivePath,412));
    assertEquals(1,NativeVillageHomeAssets.decodeSample(archivePath,838));
    assertEquals(2,sample(phone,layout,"archive"));assertEquals(1,sample(high,layout,"archive"));
    assertEquals(2,sample(phone,layout,"layered-join-approved-market"));assertEquals(1,sample(high,layout,"layered-join-approved-market"));
    for(VillageLayout.Resident actor:layout.residents) {
      VillageLayout.Asset source=layout.assets.get(actor.asset);int sample=sample(high,layout,actor.asset);
      assertTrue(source.sourceHeight*(source.crop[3]-source.crop[1])/sample>=838*actor.heightRatio*VillageArtSampling.RESERVE);
    }
  }
  @Test public void atlasCellsDensityAndPathAliasesUseOneConservativeSharedKey()throws Exception {
    VillageLayout layout=layout();Map<String,Integer> one=VillageArtSampling.plan(layout,915,412,1),dense=VillageArtSampling.plan(layout,915,412,4);
    assertTrue(sample(dense,layout,"nameplates")<sample(one,layout,"nameplates"));
    int[] marker=VillageNameplates.BOUNDS[10];
    assertTrue(sample(dense,layout,"nameplates")==NativeVillageHomeAssets.decodeSample(layout.assets.get("nameplates").path,838)
        ||(marker[3]-marker[1])/(double)sample(dense,layout,"nameplates")>=30*4*VillageArtSampling.RESERVE);
    String shared=layout.assets.get("training-nameplate").path;
    assertEquals(shared,layout.assets.get("harbor-nameplate").path);assertEquals(shared,layout.assets.get("statue-nameplate").path);
    int sample=dense.get(shared);
    for(String id:new String[]{"training-nameplate","harbor-nameplate","statue-nameplate"}) {
      VillageLayout.Asset asset=layout.assets.get(id);double target=id.equals("statue-nameplate")?Math.min(32*4,412*.05):Math.min(45*4,412*.065);
      assertTrue(VillageArtSampling.croppedHeight(asset)/sample>=target*VillageArtSampling.RESERVE);
    }
    assertTrue(VillageArtSampling.plan(layout,0,0,1).isEmpty());
    JSONObject missing=NativeVillageHomeAssetsTest.manifest();missing.getJSONObject("assets").getJSONObject("merchant").remove("sourceWidth");missing.getJSONObject("assets").getJSONObject("merchant").remove("sourceHeight");missing.getJSONObject("assets").getJSONObject("merchant").remove("renderContentBoundsPx");
    VillageLayout unknown=VillageLayout.parse(missing);assertFalse(VillageArtSampling.plan(unknown,915,412,1).containsKey(unknown.assets.get("merchant").path));
  }
  static class SizedSource implements NativeVillageHomeAssets.Source {
    final List<Consumer<Bitmap>> callbacks=new ArrayList<>();final List<Integer> requested=new ArrayList<>();final List<String> paths=new ArrayList<>();int wanted=4,completed;final java.util.Map<String,Bitmap> cached=new java.util.HashMap<>();
    public int sample(String path){return wanted;}
    public Bitmap peek(String path){return cached.get(path+"@"+wanted);}
    public void configure(VillageLayout layout,int width,int height,float density){wanted=height>=800?1:4;}
    public void load(String path,Consumer<Bitmap> ready){requested.add(wanted);paths.add(path);callbacks.add(ready);}
    void finish(){callbacks.get(completed++).accept(NativeVillageHomeAssetsTest.image());}
    void finishAll(){while(completed<callbacks.size())finish();}
  }
  @Test public void resizeUpgradesPinnedSceneWithoutDroppingItAndOldCallbacksCannotWin()throws Exception {
    VillageLayout layout=layout();SizedSource source=new SizedSource();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
    assets.setRenderSize(915,412,1);assets.load(layout,()->true,()->{});source.finishAll();assertTrue(assets.commitWindow(""));
    Bitmap old=assets.get("recruit");assertEquals(Integer.valueOf(4),assets.imageSamples.get("recruit"));
    assets.setRenderSize(2048,838,2.625f);assertSame(old,assets.get("recruit"));assertTrue(assets.committedReady());assertFalse(assets.ready());
    assertTrue(source.requested.get(source.requested.size()-1)==1);source.finishAll();assertTrue(assets.ready());assertNotSame(old,assets.get("recruit"));
    Bitmap upgraded=assets.get("recruit");int count=source.callbacks.size();
    assets.setRenderSize(915,412,1);assertTrue(assets.ready());assertEquals(count,source.callbacks.size());assertSame(upgraded,assets.get("recruit"));
    // Resize with old low-resolution requests still in flight; complete before upgrade requests.
    SizedSource delayed=new SizedSource();NativeVillageHomeAssets loading=new NativeVillageHomeAssets(delayed);
    loading.setRenderSize(915,412,1);loading.load(layout,()->true,()->{});loading.setRenderSize(2048,838,2.625f);
    delayed.finishAll();assertTrue(loading.ready());for(String id:loading.requiredImages)assertEquals(Integer.valueOf(1),loading.imageSamples.get(id));
    // A desired-key cache hit may arrive before the old-key worker callback.
    SizedSource cacheSource=new SizedSource();NativeVillageHomeAssets cached=new NativeVillageHomeAssets(cacheSource);
    cached.setRenderSize(915,412,1);cached.load(layout,()->true,()->{});
    String path=cacheSource.paths.get(0),id="";for(VillageLayout.Asset asset:layout.assets.values())if(asset.path.equals(path)){id=asset.id;break;}
    Bitmap high=NativeVillageHomeAssetsTest.image();cacheSource.cached.put(path+"@1",high);
    cached.setRenderSize(2048,838,2.625f);assertSame(high,cached.get(id));
    cacheSource.finish();assertSame("Late lower-resolution decode cannot replace the desired-key cache hit",high,cached.get(id));
    cacheSource.finishAll();assertTrue(cached.ready());
    loading.cancel();int n=delayed.callbacks.size();loading.setRenderSize(915,412,1);assertEquals(n,delayed.callbacks.size());
  }
  @Test public void resizedCurrentStatueUpgradesAndFailedUpgradeRetriesWithoutDiscardingDisplayedArt()throws Exception {
    VillageLayout layout=layout();SizedSource source=new SizedSource();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
    assets.setRenderSize(915,412,1);assets.load(layout,()->true,()->{});source.finishAll();assets.loadStatue(layout,1,()->true,()->{});source.finishAll();assertTrue(assets.commitWindow("statue-stage-1"));
    Bitmap statue=assets.get("statue-stage-1");assets.setRenderSize(2048,838,2.625f);source.finishAll();assertTrue(assets.ready("statue-stage-1"));assertNotSame(statue,assets.get("statue-stage-1"));
    // A failed larger decode preserves the lower-resolution committed scene until explicit retry.
    SizedSource failing=new SizedSource();NativeVillageHomeAssets retry=new NativeVillageHomeAssets(failing);
    retry.setRenderSize(915,412,1);retry.load(layout,()->true,()->{});failing.finishAll();retry.commitWindow("");Bitmap retained=retry.get("recruit");
    retry.setRenderSize(2048,838,2.625f);while(failing.completed<failing.callbacks.size())failing.callbacks.get(failing.completed++).accept(null);
    assertTrue(retry.committedReady());assertSame(retained,retry.get("recruit"));assertFalse(retry.ready());retry.retryWindow();failing.finishAll();assertTrue(retry.ready());
    SizedSource statueSource=new SizedSource();NativeVillageHomeAssets statueRetry=new NativeVillageHomeAssets(statueSource);
    statueRetry.setRenderSize(915,412,1);statueRetry.load(layout,()->true,()->{});statueSource.finishAll();
    statueRetry.loadStatue(layout,1,()->true,()->{});statueSource.finishAll();assertTrue(statueRetry.commitWindow("statue-stage-1"));
    Bitmap retainedStatue=statueRetry.get("statue-stage-1");String statuePath=layout.assets.get("statue-stage-1").path;
    statueRetry.setRenderSize(2048,838,2.625f);
    while(statueSource.completed<statueSource.callbacks.size()) {
      if(statueSource.paths.get(statueSource.completed).equals(statuePath))statueSource.callbacks.get(statueSource.completed++).accept(null);
      else statueSource.finish();
    }
    assertTrue(statueRetry.ready());assertFalse(statueRetry.ready("statue-stage-1"));assertTrue(statueRetry.hasFailure("statue-stage-1"));
    assertTrue(statueRetry.committedReady());assertSame(retainedStatue,statueRetry.get("statue-stage-1"));
    statueRetry.retryWindow();statueSource.finishAll();
    assertTrue(statueRetry.ready("statue-stage-1"));assertFalse(statueRetry.hasFailure("statue-stage-1"));
    assertNotSame(retainedStatue,statueRetry.get("statue-stage-1"));
  }
  static class Buckets extends SizedSource {
    public void configure(VillageLayout layout,int width,int height,float density){wanted=height>=1000?1:height>=800?2:4;}
  }
  String idFor(VillageLayout layout,String path){for(VillageLayout.Asset asset:layout.assets.values())if(asset.path.equals(path))return asset.id;throw new AssertionError(path);}
  @Test public void lateOldKeyFailuresCannotPoisonNewBucketsWithOrWithoutDesiredCacheHit()throws Exception {
    VillageLayout layout=layout();
    for(boolean statue:new boolean[]{false,true})for(boolean cacheHit:new boolean[]{false,true}) {
      Buckets source=new Buckets();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
      assets.setRenderSize(915,412,1);assets.load(layout,()->true,()->{});
      if(statue){source.finishAll();assets.loadStatue(layout,1,()->true,()->{});}
      String path=source.paths.get(source.completed),id=idFor(layout,path);Bitmap desired=NativeVillageHomeAssetsTest.image();
      if(cacheHit)source.cached.put(path+"@2",desired);
      assets.setRenderSize(2048,838,2.625f);
      source.callbacks.get(source.completed++).accept(null);
      assertFalse("An old decode key must not poison the current key",assets.failed.contains(id));
      assertFalse(assets.statueErrors.contains(id));
      if(cacheHit)assertSame(desired,assets.get(id));
      source.finishAll();assertTrue(assets.ready());if(statue)assertTrue(assets.ready("statue-stage-1"));
      assets.setRenderSize(2560,1200,3);source.finishAll();
      assertTrue(assets.ready());assertEquals(Integer.valueOf(1),assets.imageSamples.get(id));
      if(statue){assertTrue(assets.ready("statue-stage-1"));assertFalse(assets.hasFailure("statue-stage-1"));}
      assertTrue(assets.inFlight.isEmpty());
    }
  }
  @Test public void finishedOldFailuresAndThrownDecodesAreBoundedAndRetryForNewSize()throws Exception {
    VillageLayout layout=layout();
    for(boolean statue:new boolean[]{false,true}) {
      Buckets source=new Buckets();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
      assets.setRenderSize(915,412,1);assets.load(layout,()->true,()->{});
      if(statue){source.finishAll();assets.loadStatue(layout,1,()->true,()->{});}
      String id=idFor(layout,source.paths.get(source.completed));source.callbacks.get(source.completed++).accept(null);source.finishAll();
      assertTrue(statue?assets.statueErrors.contains(id):assets.failed.contains(id));
      assets.setRenderSize(2048,838,2.625f);source.finishAll();
      assertFalse(assets.failed.contains(id));assertFalse(assets.statueErrors.contains(id));assertTrue(assets.ready());
      if(statue)assertTrue(assets.ready("statue-stage-1"));
    }
    int[] throwsCount={0};Buckets throwing=new Buckets(){
      public void load(String path,Consumer<Bitmap> ready){if(wanted==4){throwsCount[0]++;throw new IllegalStateException("fixture");}super.load(path,ready);}
    };
    NativeVillageHomeAssets assets=new NativeVillageHomeAssets(throwing);assets.setRenderSize(915,412,1);assets.load(layout,()->true,()->{});
    assertTrue(throwsCount[0]>0);assertTrue(throwsCount[0]<=assets.wantedImages.size());assertFalse(assets.ready());assertTrue(assets.inFlight.isEmpty());
    int before=throwsCount[0];for(int frame=0;frame<30;frame++)assets.setRenderSize(915,412,1);assertEquals(before,throwsCount[0]);
    assets.setRenderSize(2048,838,2.625f);throwing.finishAll();assertTrue(assets.ready());assertTrue(assets.failed.isEmpty());
  }

}
