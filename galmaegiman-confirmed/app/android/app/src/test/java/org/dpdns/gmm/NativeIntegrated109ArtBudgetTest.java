package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real packaged bitmap allocations and retention. Host evidence does not measure device FPS. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeIntegrated109ArtBudgetTest {
  final NativeVillageHomeTest fixture = new NativeVillageHomeTest();
  final NativeHomePartyFeedbackTest helper = fixture.helper;
  static final long PINNED_LIMIT = 64L * 1024 * 1024;

  @Before public void isolate() { helper.isolate(); LocalGameArt.trim(); }
  @After public void restore() throws Exception {
    LocalGameArtTest.drain(); LocalGameArt.trim(); helper.restore();
  }

  void settle(MainActivity h, NativeVillageHomeView view,
      NativeHomePartyFeedbackTest.Profile profile, double camera) throws Exception {
    view.moveCameraUnits(camera - view.desiredCamera.cameraUnits());
    for (int i=0; i<4; i++) { helper.layout(h,profile); LocalGameArtTest.drain(); }
    helper.layout(h,profile);
    assertTrue("Original packaged pixels must finish decoding", view.sceneReady());
    assertTrue("No pending replacement scene", view.targetReady);
    assertEquals(camera, view.camera.cameraUnits(), 1e-7);
    assertEquals(0, view.assets.missing);
    Set<String> allowed = new HashSet<>(view.assets.wantedImages);
    allowed.addAll(view.assets.committedImages);
    if (!view.assets.currentStatue.isEmpty()) allowed.add(view.assets.currentStatue);
    assertTrue("Travel cannot pin an abandoned window", allowed.containsAll(view.assets.images.keySet()));
    assertTrue("The entire world is never retained by one view",
        view.assets.images.size() < view.layout.assets.size());
    assertTrue("The decode queue stays bounded", view.assets.inFlight.size() <= 2);
  }

  long pinnedBytes(NativeVillageHomeView view) {
    Set<Bitmap> unique = Collections.newSetFromMap(new IdentityHashMap<Bitmap,Boolean>());
    unique.addAll(view.assets.images.values());
    long bytes=0;
    for (Bitmap bitmap : unique) {
      assertNotNull(bitmap); assertFalse(bitmap.isRecycled());
      bytes += bitmap.getAllocationByteCount();
    }
    assertTrue("A displayed/prefetched village window must stay below 64 MiB: " + bytes,
        bytes <= PINNED_LIMIT);
    assertTrue("The shared LRU must keep its existing byte budget",
        LocalGameArt.cachedBytes() <= LocalGameArt.budgetBytes());
    return bytes;
  }

  @Test public void realArtStaysBoundedAcrossBothLoopsAndSurvivesCacheTrim() throws Exception {
    JSONArray profiles = new JSONArray();
    for (NativeHomePartyFeedbackTest.Profile profile : new NativeHomePartyFeedbackTest.Profile[] {
        NativeVillageHomeTest.PHONE,
        new NativeHomePartyFeedbackTest.Profile("budget-tablet-1280x720",1280,720,160,1,0,0,0,0),
        new NativeHomePartyFeedbackTest.Profile("budget-dense-2048x838",2048,838,420,1.3f,32,0,32,0)}) {
      helper.configure(profile); LocalGameArt.trim();
      try (ActivityController<MainActivity> activity = helper.flow.start()) {
        MainActivity h=activity.get(); NativeVillageHomeTest.Api api=fixture.prepare(h);
        NativeVillageHomeView view=fixture.open(h,profile); view.ambient.fixedTime=0L;
        JSONArray windows=new JSONArray(); long peak=0;
        for (double camera : new double[] {0,1.5,2,3.5,4,5.5,6,7.5,8,16,8,0,-8,-16,0}) {
          settle(h,view,profile,camera); long bytes=pinnedBytes(view); peak=Math.max(peak,bytes);
          windows.put(MainActivity.obj("cameraUnits",camera,"pinnedBytes",bytes,
              "pinnedImages",view.assets.images.size(),"cacheBytes",LocalGameArt.cachedBytes()));
        }
        Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
        try {
          LocalGameArt.trim(); assertEquals(0,LocalGameArt.cachedBytes());
          assertTrue("A cache trim cannot discard the committed window",view.assets.committedReady());
          Bitmap after=NativeHomePartyFeedbackTest.render(h.root);
          try { assertTrue("A cache trim cannot change already displayed pixels",before.sameAs(after)); }
          finally { after.recycle(); }
        } finally { before.recycle(); }
        assertTrue("Exploration and artwork profiling send no game writes",api.writes.isEmpty());
        profiles.put(MainActivity.obj("profile",profile.json(),"peakPinnedBytes",peak,
            "pinnedLimitBytes",PINNED_LIMIT,"sharedCacheBudgetBytes",LocalGameArt.budgetBytes(),
            "windows",windows,"trimPreservesPixels",true));
      }
      LocalGameArtTest.drain(); LocalGameArt.trim();
    }
    Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"integrated109");
    Files.createDirectories(output);
    JSONObject evidence=MainActivity.obj("measurement","Native-Skia host bitmap allocations; not device FPS",
        "profiles",profiles,"libraryOrSigningAttempted",false);
    Files.write(output.resolve("art-memory-budget.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
  }
}
