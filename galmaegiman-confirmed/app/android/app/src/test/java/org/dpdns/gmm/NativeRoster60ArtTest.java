package org.dpdns.gmm;
import static org.junit.Assert.*;
import org.junit.Test;import org.junit.runner.RunWith;import org.json.*;
import org.robolectric.*;import org.robolectric.annotation.Config;import org.robolectric.annotation.LooperMode;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60ArtTest {
 @Test public void realLocalAssetsRespectRearFrontMiniRatioAndV6Rect()throws Exception{
  NativeRoster60Art art=new NativeRoster60Art(RuntimeEnvironment.getApplication());assertEquals(60,art.index.getJSONArray("entries").length());
  art.bind(MainActivity.obj("fighters",new JSONArray().put(MainActivity.obj("speciesId","u02")).put(MainActivity.obj("speciesId","u01"))));
  LocalGameArtTest.drain();assertNotNull(art.images[0]);assertNotNull(art.images[1]);assertTrue(art.images[0].getHeight()<=256);
  for(int side=0;side<2;side++){android.graphics.RectF r=art.destination(side,180,240);assertTrue(r.left>=0&&r.right<=180&&r.top>=0&&r.bottom<=240);}
  // The canonical mini-species height remains .6; the opposing ground plane projects to .72.
  assertEquals(.6f/.72f,art.destination(0,180,240).height()/art.destination(1,180,240).height(),.001f);
  JSONObject views=art.entry("u31").getJSONObject("views");assertEquals(906,views.getJSONObject("rear").getJSONArray("sourceRect").getInt(0));assertTrue(views.getJSONObject("front").getString("sourceFile").contains("v6"));
  art.bind(MainActivity.obj("fighters",new JSONArray().put(MainActivity.obj("speciesId","u31")).put(MainActivity.obj("speciesId","u31"))));LocalGameArtTest.drain();assertNotNull(art.images[0]);assertNotNull(art.images[1]);
  art.bind(MainActivity.obj("fighters",new JSONArray().put(MainActivity.obj("speciesId","D1")).put(MainActivity.obj("speciesId","missing"))));assertNull(art.images[0]);assertNull(art.images[1]);
 }
}
