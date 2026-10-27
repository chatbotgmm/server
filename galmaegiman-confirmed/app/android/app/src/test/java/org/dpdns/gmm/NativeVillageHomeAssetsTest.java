package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Controlled decode completions; no unverified reference file is opened as production art. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NativeVillageHomeAssetsTest {
 static final class Delayed implements NativeVillageHomeAssets.Source {
  final List<String> paths=new ArrayList<>();final List<Consumer<Bitmap>> callbacks=new ArrayList<>();
  public void load(String path,Consumer<Bitmap> ready){paths.add(path);callbacks.add(ready);}
 }
 static JSONObject manifest()throws Exception{try(java.io.InputStream in=RuntimeEnvironment.getApplication().getAssets().open(VillageLayout.MANIFEST)){return new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
 static JSONObject pendingManifest()throws Exception{JSONObject data=manifest();data.put("artworkStatus","pending-verified-pixels");JSONObject assets=data.getJSONObject("assets");for(java.util.Iterator<String> ids=assets.keys();ids.hasNext();)assets.getJSONObject(ids.next()).put("verified",false);return data;}
 static VillageLayout verifiedFixture()throws Exception{JSONObject data=pendingManifest();data.getJSONObject("assets").getJSONObject("recruit").put("verified",true);return VillageLayout.parse(data);}
 @Test public void explicitUnverifiedReferencesRemainPlaceholdersAfterProductionArtIsConnected()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);VillageLayout layout=VillageLayout.parse(pendingManifest());int[] changes={0};
  assets.load(layout,()->true,()->changes[0]++);assertTrue(source.paths.isEmpty());assertTrue(assets.images.isEmpty());assertEquals(layout.assets.values().stream().filter(a->a.required).count(),assets.missing);assertFalse(assets.ready());assertEquals(1,changes[0]);
 }
 @Test public void oldGenerationAndChangedOwnerCannotInstallAnOutdatedBitmap()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);VillageLayout fixture=verifiedFixture();boolean[] current={true};int[] changes={0};
  assets.load(fixture,()->current[0],()->changes[0]++);assertEquals(1,assets.pending);assertEquals("native/village-loop-v2/assets/companions-v3-recruit.png",source.paths.get(0));
  Bitmap first=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888),latest=Bitmap.createBitmap(12,6,Bitmap.Config.ARGB_8888);
  current[0]=false;source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));int before=changes[0];
  current[0]=true;assets.load(fixture,()->current[0],()->changes[0]++);source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));assertEquals(before+1,changes[0]);
  source.callbacks.get(1).accept(latest);assertSame(latest,assets.get("recruit"));assertEquals(0,assets.pending);assertFalse(assets.ready());
 }
 @Test public void cancellationInvalidatesQueuedCompletionsWithoutRestartingDecode()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);int[] changes={0};assets.load(verifiedFixture(),()->true,()->changes[0]++);assets.cancel();
  source.callbacks.get(0).accept(Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888));assertEquals(0,assets.pending);assertTrue(assets.images.isEmpty());assertEquals(1,changes[0]);assertEquals(1,source.paths.size());
 }
 @Test public void statuesLoadOnlyAuthenticatedCurrentStageAndDoNotBlockCoreReady()throws Exception{
  VillageLayout layout=VillageLayout.parse(manifest());Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);
  boolean[] current={true};int[] changes={0};assets.load(layout,()->current[0],()->changes[0]++);
  assertEquals("Initial request set is only the twenty-two scene assets including image nameplates",22,source.paths.size());
  for(String path:source.paths)assertFalse(path.contains("statue-stage-"));
  Bitmap image=Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888);
  for(int i=0;i<22;i++)source.callbacks.get(i).accept(image);
  assertTrue(assets.ready());assertEquals(0,assets.pending);
  assets.loadStatue(layout,1,()->current[0],()->changes[0]++);assertEquals(23,source.paths.size());
  assertTrue(assets.statuePending.contains("statue-stage-1"));assertTrue("Core ready never waits for statue",assets.ready());
  assets.loadStatue(layout,1,()->current[0],()->changes[0]++);assertEquals(23,source.paths.size());
  source.callbacks.get(22).accept(image);assertSame(image,assets.get("statue-stage-1"));
  assets.loadStatue(layout,1,()->current[0],()->changes[0]++);assertEquals(23,source.paths.size());
  assets.loadStatue(layout,3,()->current[0],()->changes[0]++);assertEquals(24,source.paths.size());
  source.callbacks.get(23).accept(null);assertTrue(assets.statueErrors.contains("statue-stage-3"));assertTrue(assets.ready());
  assets.loadStatue(layout,3,()->current[0],()->changes[0]++);assertEquals("Repeated state does not spin failed decode",24,source.paths.size());
  JSONObject rejectedManifest=manifest();rejectedManifest.getJSONObject("assets").getJSONObject("statue-stage-2").put("verified",false);
  VillageLayout rejectedFixture=VillageLayout.parse(rejectedManifest);
  assets.loadStatue(rejectedFixture,2,()->current[0],()->changes[0]++);assertEquals("Explicitly unverified stage fixture is never attempted",24,source.paths.size());
  assets.loadStatue(layout,2,()->current[0],()->changes[0]++);assertEquals("Current verified server stage 2 loads only itself",25,source.paths.size());
  assertEquals("native/village-loop-v2/assets/statue-stage-2.png",source.paths.get(24));source.callbacks.get(24).accept(image);
  assertSame(image,assets.get("statue-stage-2"));assets.loadStatue(layout,2,()->current[0],()->changes[0]++);assertEquals(25,source.paths.size());
  assets.loadStatue(layout,4,()->current[0],()->changes[0]++);assertEquals(26,source.paths.size());
  assets.cancel();source.callbacks.get(25).accept(image);assertNull("Cancelled generation cannot install art",assets.get("statue-stage-4"));
  current[0]=false;assets.loadStatue(layout,5,()->current[0],()->changes[0]++);assertEquals("Inactive account cannot request future art",26,source.paths.size());
 }

}
