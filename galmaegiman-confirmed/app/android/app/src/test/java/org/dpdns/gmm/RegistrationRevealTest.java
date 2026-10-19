package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** 지원서의 서버 결과·등록증 텍스트·좁은 결과 스트립·기존 획득 행동을 함께 검증한다. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationRevealTest {
  final NativeGameFlowTest fixture = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeAcquisitionFlowTest acquisition = new NativeAcquisitionFlowTest();
  float originalScale;

  @Before
  public void prepare() {
    fixture.isolate();
    review.scheduledFramesUseThePausedLooperClock();
    originalScale = Settings.Global.getFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, 1);
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, 1);
  }

  @After
  public void restore() {
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, originalScale);
  }

  JSONObject result() {
    return MainActivity.obj("items", new JSONArray()
        .put(MainActivity.obj("id", "I3", "name", "꺼지지 않는 그림자", "tier", "IMMORTAL",
            "tierName", "불멸", "img", "I3", "n", 3, "first", true))
        .put(MainActivity.obj("id", "C1", "name", "갈매미맨", "tier", "COMMON",
            "tierName", "흔함", "img", "C1", "n", 1, "first", false))
        .put(MainActivity.obj("id", "D1", "name", "태초의 갈매미맨", "tier", "LIMITED",
            "tierName", "한정", "img", "D1", "n", 6, "first", false)));
  }

  TextView text(View view, String label) {
    if (view instanceof TextView && label.contentEquals(((TextView) view).getText()))
      return (TextView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = text(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  List<NativeReveal.RevealCard> cards(View view) {
    List<NativeReveal.RevealCard> result = new ArrayList<>();
    if (view instanceof NativeReveal.RevealCard) result.add((NativeReveal.RevealCard) view);
    else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) result.addAll(cards(group.getChildAt(i)));
    }
    return result;
  }

  NativeReveal attach(MainActivity host, JSONObject result, Runnable done, boolean again,
      NativeAcquisitionFlow flow) {
    host.base("모집 결과", "drawResult");
    NativeReveal reveal = new NativeReveal(host, result, done, again, flow);
    host.root.addView(reveal, new FrameLayout.LayoutParams(-1, -1));
    // 직접 레이아웃은 attachment를 만들지 않는다. PAUSED looper의 실제 Window
    // traversal까지 처리해 production의 attached/resumed 게이트를 그대로 시험한다.
    int width = host.getResources().getDisplayMetrics().widthPixels;
    int height = host.getResources().getDisplayMetrics().heightPixels;
    review.viewport(host, width, height);
    reveal.getViewTreeObserver().dispatchOnPreDraw();
    assertTrue("결과 화면이 실제 Window에 붙어 있음", reveal.isAttachedToWindow());
    assertTrue("결과 화면은 resumed/visible/current 조건을 모두 만족", reveal.active());
    return reveal;
  }

  @Test
  public void openingAndReplayKeepExactServerOrderIdentityTierCountAndFirstWithoutRequests() throws Exception {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      ApplicationEnvelopeTest.NoRequestApi api = new ApplicationEnvelopeTest().localOnly(host);
      host.motionEnabled = false;
      JSONObject payload = result();
      String untouched = payload.toString();
      AtomicInteger finished = new AtomicInteger();
      NativeReveal reveal = attach(host, payload, finished::incrementAndGet, false, null);
      assertFalse(reveal.revealed);
      assertNotNull(text(reveal, "지원서 열기"));
      assertNull(text(reveal, "꺼지지 않는 그림자"));
      assertSame(payload.getJSONArray("items"), reveal.units);
      reveal.openEgg();
      reveal.openEgg();
      fixture.measure(host.root);
      assertEquals(11, reveal.topRank);
      assertEquals(3, cards(reveal).size());
      assertEquals(untouched, payload.toString());
      String[] expectedIds = {"I3", "C1", "D1"};
      for (int i = 0; i < expectedIds.length; i++) {
        NativeReveal.RevealCard card = cards(reveal).get(i);
        assertEquals(expectedIds[i], acquisition.registrationArtwork(card).id);
        assertTrue(card.front instanceof LinearLayout);
        assertEquals(View.VISIBLE, card.front.getVisibility());
      }
      assertNotNull(text(reveal, "NEW"));
      assertNotNull(text(reveal, "×3"));
      assertNotNull(text(reveal, "×6"));
      assertNull("Tier is already printed on the FULL card", text(reveal, "불멸"));
      assertEquals(0, reveal.animations.size());
      reveal.finish();
      reveal.finish();
      assertEquals(1, finished.get());
      NativeReveal replay = attach(host, payload, () -> {}, true, null);
      assertTrue(replay.revealed);
      assertNull(replay.envelope);
      assertSame(payload.getJSONArray("items"), replay.units);
      assertEquals(untouched, payload.toString());
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void missingAuthoritativeItemsCanNeverBecomeSuccessfulCards() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      ApplicationEnvelopeTest.NoRequestApi api = new ApplicationEnvelopeTest().localOnly(host);
      host.motionEnabled = false;
      for (JSONObject absent : new JSONObject[] {null, new JSONObject(),
          MainActivity.obj("items", new JSONArray())}) {
        AtomicInteger finished = new AtomicInteger();
        NativeReveal reveal = attach(host, absent, finished::incrementAndGet, true, null);
        reveal.openEgg();
        reveal.cards(true);
        reveal.finish();
        assertFalse(reveal.revealed);
        assertTrue(cards(reveal).isEmpty());
        assertNull(text(reveal, "확인"));
        assertEquals(0, finished.get());
      }
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void resultCannotArriveFromCancelledPausedOrDetachedEnvelope() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      ApplicationEnvelopeTest.NoRequestApi api = new ApplicationEnvelopeTest().localOnly(host);
      NativeReveal reveal = attach(host, result(), () -> {}, false, null);
      reveal.openEgg();
      assertFalse(reveal.revealed);
      ValueAnimator old = reveal.envelope.clock;
      assertNotNull(old);
      old.setCurrentPlayTime(360);
      controller.pause();
      old.end();
      assertFalse(reveal.revealed);
      controller.resume();
      ValueAnimator resumed = reveal.envelope.clock;
      assertNotNull(resumed);
      host.root.removeView(reveal);
      resumed.end();
      assertFalse(reveal.revealed);
      assertTrue(reveal.detached);
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void pausedConfirmationResumesOnceAndDetachedFadeNeverNavigates() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      ApplicationEnvelopeTest.NoRequestApi api = new ApplicationEnvelopeTest().localOnly(host);
      AtomicInteger finished = new AtomicInteger();
      NativeReveal reveal = attach(host, result(), finished::incrementAndGet, true, null);
      reveal.finish();
      ValueAnimator fade = reveal.animation;
      assertNotNull(fade);
      controller.pause();
      assertTrue(fade.isPaused());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      assertEquals(0, finished.get());
      controller.resume();
      assertFalse(fade.isPaused());
      fade.end();
      assertEquals(1, finished.get());
      NativeReveal removed = attach(host, result(), finished::incrementAndGet, true, null);
      removed.finish();
      ValueAnimator stale = removed.animation;
      host.root.removeView(removed);
      stale.end();
      assertEquals(1, finished.get());
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void largeFullCardsAvoidDuplicateIdentityAndKeepBadgesAndInlineActionsAtEveryViewport() throws Exception {
    for (float scale : new float[] {1, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      for (int[] size : new int[][] {{915, 412}, {1006, 411}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        try (var controller = fixture.start()) {
          MainActivity host = controller.get();
          NativeAcquisitionFlowTest.FakeApi api = acquisition.api(host,
              acquisition.state(acquisition.fighter("o20", 5)));
          review.viewport(host, size[0], size[1]);
          NativeAcquisitionFlow flow = new NativeAcquisitionFlow(host, "draw", Set.of());
          NativeReveal reveal = attach(host, result(), () -> {}, true, flow);
          String name = "registration-reveal-" + size[0] + "-font" + Math.round(scale * 100);
          review.capture(host, name, size[0], size[1]);
          for (NativeReveal.RevealCard card : cards(reveal)) {
            acquisition.assertCardTextFits(card.front);
            RegistrationCardView.Artwork image = acquisition.registrationArtwork(card);
            assertEquals(RegistrationCardView.FULL, image.mode);
            review.await(() -> image.bitmap != null);
            RectF target = new RectF(image.target);
            assertTrue(target.width() > 0 && target.height() > 0);
            assertEquals(.75, target.width() / target.height(), .01);
            assertTrue(target.left >= 0 && target.top >= 0);
            assertTrue(target.right <= image.getWidth() + .5f);
            assertTrue(target.bottom <= image.getHeight() + .5f);
            LinearLayout face = (LinearLayout) card.front;
            assertEquals("No name/tier rows outside the printed full card", 1, face.getChildCount());
            assertNull("No second cream card wrapper", face.getBackground());
            assertTrue("Card uses the recovered name/tier space", target.height() >= host.dp(180));
          }
          assertNull("Printed name is not repeated outside the image", text(reveal, "꺼지지 않는 그림자"));
          assertNotNull(text(reveal, "NEW"));
          assertNotNull(text(reveal, "×3"));
          assertNotNull(text(reveal, "×6"));
          acquisition.assertAcquisitionActionsVisible(host, size[0], size[1]);
          HorizontalScrollView strip = host.root.findViewWithTag("acquisition-cards-scroll");
          assertFalse(strip.canScrollVertically(1));
          NativeReveal.RevealCard last = cards(reveal).get(2);
          last.requestRectangleOnScreen(new Rect(0, 0, last.getWidth(), last.getHeight()), true);
          last.performClick();
          assertEquals("같은 화면의 실제 결과 인덱스 선택", 2, flow.selected);
          assertNotNull("카드가 바로 도감 기록을 연다", reveal.dossier);
          review.capture(host, name + "-record", size[0], size[1]);
          reveal.dossier.findViewWithTag("acquisition-dossier-back").performClick();
          assertNull(reveal.dossier);
          assertEquals("기록을 닫아도 선택 유지", 2, flow.selected);
          review.capture(host, name + "-last", size[0], size[1]);
          NativeNoScrollReviewTest.assertFullyVisible(last, host.root);
          acquisition.assertAcquisitionActionsVisible(host, size[0], size[1]);
          assertEquals("기록은 catalog와 현재 개체를 읽기만 한다",
              java.util.Arrays.asList("GET native/catalog", "GET native/state"), api.requests);
          // Native Skia 실제 렌더는 텍스트와 그림이 함께 있는 front 전체를 그린다.
          Bitmap output = Bitmap.createBitmap(last.getWidth(), last.getHeight(), Bitmap.Config.ARGB_8888);
          try {
            last.draw(new Canvas(output));
            assertNotEquals(0, output.getPixel(output.getWidth() / 2, output.getHeight() / 2));
          } finally {
            output.recycle();
          }
        }
      }
    }
  }

  @Test
  public void oneTenAndFiftyDrawsKeepGroupedQuantitiesAndServerItemOrder() throws Exception {
    JSONArray published;
    try (InputStream in = RuntimeEnvironment.getApplication().getAssets()
        .open("native/registration-v1/manifest.json")) {
      published = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .getJSONArray("items");
    }
    for (int drawCount : new int[] {1, 10, 50}) {
      try (var controller = fixture.start()) {
        MainActivity host = controller.get();
        ApplicationEnvelopeTest.NoRequestApi api = new ApplicationEnvelopeTest().localOnly(host);
        host.motionEnabled = false;
        int itemCount = drawCount == 50 ? 35 : drawCount == 10 ? 8 : 1;
        JSONArray items = new JSONArray();
        for (int i = 0; i < itemCount; i++) {
          JSONObject record = new JSONObject(published.getJSONObject(itemCount - 1 - i).toString());
          record.put("tierName", record.getString("tier_ko"));
          record.put("img", record.getString("id"));
          record.put("n", i == 0 ? drawCount - itemCount + 1 : 1);
          record.put("first", i % 2 == 0);
          items.put(record);
        }
        JSONObject payload = MainActivity.obj("items", items);
        String untouched = payload.toString();
        NativeReveal reveal = attach(host, payload, () -> {}, true, null);
        int quantity = 0, visited = 0;
        List<RegistrationCardView.Artwork> oldArt = new ArrayList<>();
        for (int page = 0; page < (itemCount + 2) / 3; page++) {
          List<NativeReveal.RevealCard> shown = cards(reveal);
          assertEquals(Math.min(3, itemCount - page * 3), shown.size());
          for (int i = 0; i < shown.size(); i++) {
            JSONObject expected = items.getJSONObject(page * 3 + i);
            RegistrationCardView.Artwork art = acquisition.registrationArtwork(shown.get(i));
            assertEquals(expected.getString("id"), art.id);
            assertNull("Identity is printed only once", text(shown.get(i), expected.getString("name")));
            assertTrue(shown.get(i).getContentDescription().toString().contains(expected.getString("name")));
            assertEquals(expected.getBoolean("first"), text(shown.get(i), "NEW") != null);
            if (expected.getInt("n") > 1)
              assertNotNull(text(shown.get(i), "×" + expected.getInt("n")));
            quantity += expected.getInt("n");
            visited++;
            oldArt.add(art);
          }
          if ((page + 1) * 3 < itemCount) {
            reveal.findViewWithTag("acquisition-next-page").performClick();
            review.viewport(host, host.root.getWidth(), host.root.getHeight());
            for (RegistrationCardView.Artwork detached : oldArt) {
              assertFalse("Previous page is detached", detached.isAttachedToWindow());
              assertNull("Detached page does not retain 768px full bitmaps", detached.bitmap);
            }
          }
          assertTrue("The global image cache stays bounded", host.registrationArt().cachedBytes()
              <= RegistrationArt.MAX_CACHE_BYTES);
        }
        assertEquals(itemCount, visited);
        assertEquals(drawCount, quantity);
        assertEquals(untouched, payload.toString());
        assertEquals(0, api.calls);
      }
    }
  }
}

