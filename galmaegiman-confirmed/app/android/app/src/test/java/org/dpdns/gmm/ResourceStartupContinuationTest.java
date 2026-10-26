package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.View;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/** Resource decoding stays active; only an obsolete startup navigation is discarded. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ResourceStartupContinuationTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  boolean previousPlaySkip;

  @Before public void setup() {
    fixtures.isolate();
    previousPlaySkip = NativeRequiredUpdate.skipPlayCheckForUnitTests;
    NativeRequiredUpdate.skipPlayCheckForUnitTests = true;
  }
  @After public void restore() { NativeRequiredUpdate.skipPlayCheckForUnitTests = previousPlaySkip; }

  ActivityController<MainActivity> pendingStartup() {
    ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
    MainActivity h = controller.get(); h.motionEnabled = false;
    // Keep the real background cache work, but deliver its completion explicitly
    // in these tests so ordering is deterministic rather than machine-speed dependent.
    GameResourcePreparation.Run run = ReflectionHelpers.getField(h, "resourcePreparation");
    assertNotNull(run); run.cancel();
    ReflectionHelpers.setField(h, "resourcesAccepted", false);
    controller.start().resume().visible();
    assertNotNull(ReflectionHelpers.getField(h, "resourceContinuation"));
    return controller;
  }

  static void completeResources(MainActivity h) {
    ReflectionHelpers.setField(h, "resourcesAccepted", true);
    ReflectionHelpers.callInstanceMethod(h, "continueAfterResources");
  }

  static final class NoReadApi extends NativeApi {
    int requests;
    NoReadApi(MainActivity h) { super(h); }
    @Override String token() { return "new-account"; }
    @Override void call(String path, JSONObject body, Result done) {
      requests++;
      fail("Obsolete startup navigation must not read the new account: " + path);
    }
  }

  @Test public void sameEpochCompletionStillOpensTheTitleNormally() {
    try (ActivityController<MainActivity> controller = pendingStartup()) {
      MainActivity h = controller.get();
      assertNotEquals("title", h.screen);
      completeResources(h);
      assertEquals("title", h.screen);
      assertTrue(h.root.getChildAt(0) instanceof TitleView);
      assertNull(ReflectionHelpers.getField(h, "resourceContinuation"));
    }
  }

  @Test public void laterScreenAndNewAccountSurviveLateCompletionAndResume() {
    try (ActivityController<MainActivity> controller = pendingStartup()) {
      MainActivity h = controller.get(); h.api.close();
      NoReadApi api = new NoReadApi(h); h.api = api;
      h.base("획득 결과", "drawResult");
      View page = h.page; int epoch = h.epoch;
      completeResources(h);
      assertSame(page, h.page); assertEquals(epoch, h.epoch);
      assertEquals("drawResult", h.screen); assertEquals(0, api.requests);
      controller.pause().resume();
      assertSame(page, h.page); assertEquals(epoch, h.epoch);
      assertEquals("drawResult", h.screen); assertEquals(0, api.requests);
    }
  }

  @Test public void obsoleteCallbackDoesNotConsumeTheDeferredTarget() {
    try (ActivityController<MainActivity> controller = pendingStartup()) {
      MainActivity h = controller.get();
      h.requiredUpdate.deferLoad("collection");
      h.base("새 화면", "drawResult");
      int epoch = h.epoch;
      completeResources(h);
      assertEquals(epoch, h.epoch); assertEquals("drawResult", h.screen);
      assertEquals("collection", ReflectionHelpers.getField(h.requiredUpdate, "pendingTarget"));
    }
  }

  @Test public void aClosedActivityRejectsAnAlreadyCapturedCompletion() {
    ActivityController<MainActivity> controller = pendingStartup();
    MainActivity h = controller.get();
    Runnable continuation = ReflectionHelpers.getField(h, "resourceContinuation");
    int epoch = h.epoch;
    controller.pause().stop().destroy();
    continuation.run();
    assertEquals(epoch, h.epoch);
    assertNotEquals("title", h.screen);
  }
}
