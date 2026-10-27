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
  assets.load(layout,()->true,()->changes[0]++);assertTrue(source.paths.isEmpty());assertTrue(assets.images.isEmpty());assertEquals(layout.assets.size(),assets.missing);assertFalse(assets.ready());assertEquals(1,changes[0]);
 }
 @Test public void oldGenerationAndChangedOwnerCannotInstallAnOutdatedBitmap()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);VillageLayout fixture=verifiedFixture();boolean[] current={true};int[] changes={0};
  assets.load(fixture,()->current[0],()->changes[0]++);assertEquals(1,assets.pending);assertEquals("native/village-loop-v2/assets/recruit.png",source.paths.get(0));
  Bitmap first=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888),latest=Bitmap.createBitmap(12,6,Bitmap.Config.ARGB_8888);
  current[0]=false;source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));int before=changes[0];
  current[0]=true;assets.load(fixture,()->current[0],()->changes[0]++);source.callbacks.get(0).accept(first);assertNull(assets.get("recruit"));assertEquals(before+1,changes[0]);
  source.callbacks.get(1).accept(latest);assertSame(latest,assets.get("recruit"));assertEquals(0,assets.pending);assertFalse(assets.ready());
 }
 @Test public void cancellationInvalidatesQueuedCompletionsWithoutRestartingDecode()throws Exception{
  Delayed source=new Delayed();NativeVillageHomeAssets assets=new NativeVillageHomeAssets(source);int[] changes={0};assets.load(verifiedFixture(),()->true,()->changes[0]++);assets.cancel();
  source.callbacks.get(0).accept(Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888));assertEquals(0,assets.pending);assertTrue(assets.images.isEmpty());assertEquals(1,changes[0]);assertEquals(1,source.paths.size());
 }
}
