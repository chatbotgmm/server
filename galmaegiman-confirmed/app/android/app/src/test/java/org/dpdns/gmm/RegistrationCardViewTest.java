package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real attached registration views: contain geometry, server text, hidden identities and stale loads. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationCardViewTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  static JSONObject unit(String id) {
    return MainActivity.obj("id", id, "name", "서버가 보낸 실제 대원 이름", "tier", "LEGEND",
        "tierName", "서버 등급", "first", true, "n", 3, "known", true,
        "image", "/images/C2.png");
  }

  void attach(MainActivity host, View view, int width, int height) {
    host.guide.enabled = false;
    ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(0, 0, 0, 0);
    host.root.removeAllViews();
    host.root.addView(view, new FrameLayout.LayoutParams(width, height));
    layout(host);
  }

  void layout(MainActivity host) {
    review.viewport(host, 915, 412);
    review.preparePreDraw(host.root, () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, 915, 412);
    });
  }

  @Test public void actualNameTierNewCountAndAccessibilityStayRealTextAtBothFontScales() throws Exception {
    for (float scale : new float[] {1f, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get();
        JSONObject data = unit("C1");
        RegistrationCardView card = new RegistrationCardView(host, data, RegistrationCardView.FULL);
        attach(host, card, 280, 390);
        RegistrationArtTest.await(() -> card.picture.bitmap != null);
        layout(host);
        assertEquals(data.getString("name"), card.name.getText().toString());
        assertEquals(data.getString("tierName"), card.grade.getText().toString());
        assertEquals(data.getString("name"), card.name.getContentDescription().toString());
        assertEquals("서버가 보낸 실제 대원 이름 · 서버 등급 · 3마리 · 처음 만난 대원",
            card.getContentDescription().toString());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, card.picture.getImportantForAccessibility());
        String text = new NativeGameFlowTest().labels(card);
        assertTrue("NEW is an accessible runtime TextView", text.contains("NEW"));
        assertTrue("Count is a runtime TextView", text.contains("×3"));
        assertTrue(card.name.getTop() >= card.getChildAt(0).getBottom());
        assertTrue(card.grade.getTop() >= card.name.getBottom());
        NativeNoScrollReviewTest.assertFullyVisible(card.name, host.root);
        NativeNoScrollReviewTest.assertFullyVisible(card.grade, host.root);
        assertEquals("Canonical ID wins over misleading image URL", "C1", card.picture.id);
        int bucket = RegistrationArt.widthBucket(RegistrationCardView.FULL,
            card.picture.getWidth(), card.picture.getHeight());
        Bitmap reference = RegistrationArtTest.expected(host, "C1", RegistrationCardView.FULL, bucket);
        try { RegistrationArtTest.samePixels("C1, never C2 from image URL", reference, card.picture.bitmap); }
        finally { reference.recycle(); }
      }
    }
  }

  @Test public void fullThreeByFourAndCompactPortraitWindowUseCenteredContainInEveryShape() throws Exception {
    for (int[] area : new int[][] {{300, 200}, {200, 300}, {100, 100}, {1280, 720}}) {
      RectF full = new RectF();
      RegistrationCardView.contain(full, area[0], area[1], 768, 1024);
      assertEquals(3f / 4f, full.width() / full.height(), .0001f);
      assertEquals(area[0] / 2f, full.centerX(), .001f);
      assertEquals(area[1] / 2f, full.centerY(), .001f);
      assertTrue(new RectF(0, 0, area[0], area[1]).contains(full));
      RectF compact = new RectF();
      RegistrationCardView.contain(compact, area[0], area[1], 978, 779);
      assertEquals(978f / 779f, compact.width() / compact.height(), .0001f);
      assertEquals(area[0] / 2f, compact.centerX(), .001f);
      assertEquals(area[1] / 2f, compact.centerY(), .001f);
      assertTrue(new RectF(0, 0, area[0], area[1]).contains(compact));
    }
    RectF empty = new RectF(1, 1, 2, 2);
    RegistrationCardView.contain(empty, 0, 100, 768, 1024);
    assertTrue(empty.isEmpty());
    for (int mode : new int[] {RegistrationCardView.FULL, RegistrationCardView.COMPACT}) {
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get();
        RegistrationCardView.Artwork view = (RegistrationCardView.Artwork) RegistrationCardView.art(host, unit("L14"), mode);
        attach(host, view, 300, 200);
        RegistrationArtTest.await(() -> view.bitmap != null);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.getImportantForAccessibility());
        assertTrue(view.getContentDescription().toString().contains("등록증 그림"));
        RectF expected = new RectF();
        RegistrationCardView.contain(expected, 300, 200, view.bitmap.getWidth(), view.bitmap.getHeight());
        assertEquals(expected, view.target);
        assertTrue(new RectF(0, 0, 300, 200).contains(view.target));
        assertEquals(mode == RegistrationCardView.FULL ? 3f / 4f : 978f / 779f,
            view.target.width() / view.target.height(), .02f);
        assertEquals("No square crop", mode == RegistrationCardView.COMPACT, view.target.width() > view.target.height());
      }
    }
  }

  @Test public void unknownAndMissingIdsNeverRevealAnotherOrLocallyKnownCharacter() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      List<JSONObject> hidden = Arrays.asList(
          new JSONObject(unit("C1").toString()).put("known", false),
          new JSONObject(unit("C1").toString()).put("image", "/images/unknown.png"),
          new JSONObject(unit("C1").toString()).put("image", "/images/q-C1.png"),
          new JSONObject(unit("C1").toString()).put("img", "unknown"),
          new JSONObject(unit("C1").toString()).put("img", "q-C1"));
      for (JSONObject data : hidden) {
        assertFalse(RegistrationCardView.canShow(data));
        RegistrationCardView.Artwork view = (RegistrationCardView.Artwork) RegistrationCardView.art(host, data, RegistrationCardView.FULL);
        attach(host, view, 192, 256);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(view.visibleIdentity);
        assertFalse("Hidden identities never issue a local decode", view.loadAttempted);
        assertNull(view.bitmap);
        assertTrue(view.target.isEmpty());
        assertEquals("미발견", view.status);
        assertTrue(view.getContentDescription().toString().contains("미발견"));
      }
      RegistrationCardView card = new RegistrationCardView(host, unit("Z1"), RegistrationCardView.FULL);
      attach(host, card, 250, 360);
      RegistrationArtTest.await(() -> card.picture.status.equals("이미지 없음"));
      assertNull("Missing canonical ID is not another bird", card.picture.bitmap);
      assertEquals("서버가 보낸 실제 대원 이름", card.name.getText().toString());
      assertEquals("서버 등급", card.grade.getText().toString());
      assertTrue(card.picture.getContentDescription().toString().contains("이미지를 불러오지 못했어요"));
      assertTrue("Wrapper announces failed art while picture is not separately focusable",
          card.getContentDescription().toString().contains("이미지를 불러오지 못했어요"));
    }
  }

  @Test public void detachedAndOldEpochCallbacksCannotReviveArtworkAndReattachCanLoadAgain() throws Exception {
    RegistrationArt closedArt;
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      RegistrationArt art = host.registrationArt();
      closedArt = art;
      CountDownLatch release = RegistrationArtTest.blockWorker(art);
      RegistrationCardView.Artwork stale = (RegistrationCardView.Artwork) RegistrationCardView.art(host, unit("C1"), RegistrationCardView.FULL);
      try {
        attach(host, stale, 192, 256);
        assertTrue(stale.loadAttempted);
        host.root.removeView(stale);
        assertFalse(stale.isAttachedToWindow());
        assertFalse(stale.loadAttempted);
        assertNull(stale.bitmap);
      } finally { release.countDown(); }
      RegistrationArtTest.await(art::idle);
      RegistrationArtTest.worker(art).submit(() -> {}).get(5, TimeUnit.SECONDS);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull("Late callback is discarded after detach", stale.bitmap);
      assertTrue(stale.target.isEmpty());
      attach(host, stale, 192, 256);
      RegistrationArtTest.await(() -> stale.bitmap != null);
      Bitmap held = stale.bitmap;
      CountDownLatch releaseEpoch = RegistrationArtTest.blockWorker(art);
      RegistrationCardView.Artwork oldEpoch = (RegistrationCardView.Artwork) RegistrationCardView.art(host, unit("C2"), RegistrationCardView.FULL);
      try {
        attach(host, oldEpoch, 192, 256);
        host.epoch++;
      } finally { releaseEpoch.countDown(); }
      RegistrationArtTest.await(art::idle);
      RegistrationArtTest.worker(art).submit(() -> {}).get(5, TimeUnit.SECONDS);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull("Old screen epoch cannot apply a bitmap", oldEpoch.bitmap);
      assertFalse("A detached view cannot recycle another live holder's bitmap", held.isRecycled());
      host.root.removeAllViews();
    }
    assertTrue("Activity destruction closes shared registration loader", closedArt.closed());
    assertEquals(0, closedArt.cachedBytes());
  }

  @Test public void tokenOnlyAccountChangeRejectsLateCallbackWithoutAnEpochChange() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      host.api.close();
      final String[] account = {"fixture-account-before"};
      host.api = new NativeApi(host) {
        @Override String token() { return account[0]; }
        @Override void call(String path, JSONObject body, Result done) {
          throw new AssertionError("Card lifecycle never makes API requests: " + path);
        }
      };
      RegistrationArt art = host.registrationArt();
      CountDownLatch release = RegistrationArtTest.blockWorker(art);
      RegistrationCardView.Artwork view = (RegistrationCardView.Artwork)
          RegistrationCardView.art(host, unit("C3"), RegistrationCardView.FULL);
      int epoch = host.epoch;
      try {
        attach(host, view, 192, 256);
        assertTrue(view.loadAttempted);
        account[0] = "fixture-account-after";
      } finally { release.countDown(); }
      RegistrationArtTest.await(art::idle);
      RegistrationArtTest.worker(art).submit(() -> {}).get(5, TimeUnit.SECONDS);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals("This specifically tests token-only identity change", epoch, host.epoch);
      assertTrue(view.isAttachedToWindow());
      assertNull("Old account's callback cannot show after token replacement", view.bitmap);
      assertTrue(view.target.isEmpty());
    }
  }
}
