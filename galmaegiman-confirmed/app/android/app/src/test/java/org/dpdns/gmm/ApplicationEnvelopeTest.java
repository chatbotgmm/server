package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
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

/** 로컬 표현은 서버 지급이나 재요청을 만들지 않으며 폐기된 실행은 완료할 수 없다. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ApplicationEnvelopeTest {
  final NativeGameFlowTest fixture = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
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
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, originalScale);
  }

  static final class NoRequestApi extends NativeApi {
    String session = "envelope-review";
    int calls;

    NoRequestApi(MainActivity host) { super(host); }
    @Override String token() { return session; }
    @Override void call(String path, JSONObject body, Result done) {
      calls++;
      fail("봉투는 GET/POST를 소유하지 않음: " + path);
    }
  }

  NoRequestApi localOnly(MainActivity host) {
    host.api.close();
    NoRequestApi api = new NoRequestApi(host);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = true;
    return api;
  }

  ApplicationEnvelopeView attach(MainActivity host) {
    host.root.removeAllViews();
    ApplicationEnvelopeView view = new ApplicationEnvelopeView(host);
    host.root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    fixture.measure(host.root);
    view.getViewTreeObserver().dispatchOnPreDraw();
    assertTrue(view.isAttachedToWindow());
    assertTrue(view.active());
    return view;
  }

  @Test
  public void publishedMetadataMatchesAllEightIntegerRectanglesAndOneSecond() throws Exception {
    try (InputStream in = RuntimeEnvironment.getApplication().getAssets()
        .open(ApplicationEnvelopeView.METADATA_ASSET)) {
      JSONObject data = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals("application-envelope-animation/v1", data.getString("schema"));
      assertEquals("../resources/application-envelope-atlas.png", data.getString("image"));
      assertEquals(1774, data.getJSONObject("atlas").getInt("width"));
      assertEquals(887, data.getJSONObject("atlas").getInt("height"));
      assertEquals(4, data.getJSONObject("atlas").getInt("columns"));
      assertEquals(2, data.getJSONObject("atlas").getInt("rows"));
      JSONArray frames = data.getJSONArray("frames");
      assertEquals(8, frames.length());
      assertEquals(8, ApplicationEnvelopeView.frameCount());
      int elapsed = 0, pixels = 0;
      for (int i = 0; i < frames.length(); i++) {
        JSONObject frame = frames.getJSONObject(i), source = frame.getJSONObject("sourceRect");
        Rect actual = ApplicationEnvelopeView.sourceRect(i);
        assertEquals(i, frame.getInt("index"));
        assertEquals(source.getInt("x"), actual.left);
        assertEquals(source.getInt("y"), actual.top);
        assertEquals(source.getInt("width"), actual.width());
        assertEquals(source.getInt("height"), actual.height());
        assertEquals(frame.getInt("durationMs"), ApplicationEnvelopeView.durationMs(i));
        assertEquals(i, ApplicationEnvelopeView.frameAt(elapsed));
        elapsed += frame.getInt("durationMs");
        assertEquals(i, ApplicationEnvelopeView.frameAt(elapsed - 1));
        pixels += actual.width() * actual.height();
        for (int prior = 0; prior < i; prior++)
          assertFalse("정수 프레임은 겹치지 않음", Rect.intersects(actual,
              ApplicationEnvelopeView.sourceRect(prior)));
      }
      assertEquals(1774 * 887, pixels);
      assertEquals(1000, elapsed);
      assertEquals(1000, ApplicationEnvelopeView.TOTAL_DURATION_MS);
      assertEquals(7, ApplicationEnvelopeView.frameAt(1000));
      assertEquals(7, ApplicationEnvelopeView.frameAt(Integer.MAX_VALUE));
      assertEquals("hold_until_authoritative_result",
          data.getJSONObject("completion").getString("atLastFrame"));
    }
    try (InputStream in = RuntimeEnvironment.getApplication().getAssets()
        .open(ApplicationEnvelopeView.ATLAS_ASSET)) {
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, bounds);
      assertEquals(1774, bounds.outWidth);
      assertEquals(887, bounds.outHeight);
    }
  }

  @Test
  public void lastFrameWaitsForExplicitResultAndRapidTapsNeverRestartOrRequest() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      NoRequestApi api = localOnly(host);
      ApplicationEnvelopeView view = attach(host);
      AtomicInteger delivered = new AtomicInteger();
      view.play(false, delivered::incrementAndGet);
      ValueAnimator first = view.clock;
      assertNotNull(first);
      assertEquals(1000, first.getDuration());
      view.play(true, delivered::incrementAndGet);
      assertSame("연속 탭은 재시작하지 않음", first, view.clock);
      first.end();
      assertTrue(view.completed);
      assertEquals(7, view.frame);
      assertFalse(view.delivered);
      assertEquals(0, delivered.get());
      view.authoritativeResultReady();
      view.authoritativeResultReady();
      view.play(true, delivered::incrementAndGet);
      assertEquals(1, delivered.get());
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void readyResultStillWaitsForEnvelopeAndPauseResumesOnlyRemainingTime() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      NoRequestApi api = localOnly(host);
      ApplicationEnvelopeView view = attach(host);
      AtomicInteger delivered = new AtomicInteger();
      view.play(true, delivered::incrementAndGet);
      ValueAnimator old = view.clock;
      old.setCurrentPlayTime(400);
      assertEquals(0, delivered.get());
      int elapsed = view.elapsedMs;
      assertEquals(400, elapsed);
      controller.pause();
      assertNull(view.clock);
      old.end();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
      assertEquals("백그라운드 시간은 재생에 합산하지 않음", elapsed, view.elapsedMs);
      assertEquals(0, delivered.get());
      controller.resume();
      assertNotNull(view.clock);
      assertNotSame(old, view.clock);
      assertEquals(1000 - elapsed, view.clock.getDuration());
      view.clock.end();
      assertEquals(1, delivered.get());
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void cancelDetachEpochAndAccountChangesRejectStaleAnimationEnd() {
    for (int reason = 0; reason < 4; reason++) {
      try (var controller = fixture.start()) {
        MainActivity host = controller.get();
        NoRequestApi api = localOnly(host);
        ApplicationEnvelopeView view = attach(host);
        AtomicInteger delivered = new AtomicInteger();
        view.play(true, delivered::incrementAndGet);
        ValueAnimator old = view.clock;
        old.setCurrentPlayTime(200);
        if (reason == 0) view.cancel();
        else if (reason == 1) host.root.removeView(view);
        else if (reason == 2) host.epoch++;
        else api.session = "different-account";
        view.refresh();
        old.end();
        view.authoritativeResultReady();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("취소/이탈한 실행은 결과를 공개하지 않음: " + reason, 0, delivered.get());
        assertTrue(view.cancelled);
        assertNull(view.clock);
        assertEquals(0, api.calls);
      }
    }
  }

  @Test
  public void systemMotionOffKeepsResultGateAndCannotRevealWhilePaused() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      NoRequestApi api = localOnly(host);
      Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
      ApplicationEnvelopeView view = attach(host);
      AtomicInteger delivered = new AtomicInteger();
      view.play(false, delivered::incrementAndGet);
      assertNull(view.clock);
      assertEquals(7, view.frame);
      assertEquals(0, delivered.get());
      controller.pause();
      view.authoritativeResultReady();
      assertEquals(0, delivered.get());
      controller.resume();
      assertEquals(1, delivered.get());
      assertEquals(0, api.calls);
    }
  }

  @Test
  public void actualAtlasRendersAllFramesWithoutDecodeInDraw() throws Exception {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      localOnly(host);
      ApplicationEnvelopeView view = attach(host);
      review.await(() -> view.atlas != null);
      Bitmap atlas = view.atlas;
      Set<Integer> frameHashes = new HashSet<>();
      for (int i = 0; i < 8; i++) {
        view.frame = i;
        Bitmap output = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
          view.draw(new Canvas(output));
          int[] pixels = new int[output.getWidth() * output.getHeight()];
          output.getPixels(pixels, 0, output.getWidth(), 0, 0, output.getWidth(), output.getHeight());
          int visible = 0;
          for (int pixel : pixels) if ((pixel >>> 24) != 0) visible++;
          assertTrue("봉투 프레임의 실제 픽셀 존재: " + i, visible > 1000);
          frameHashes.add(java.util.Arrays.hashCode(pixels));
          assertSame("onDraw는 준비된 공유 비트맵만 사용", atlas, view.atlas);
        } finally {
          output.recycle();
        }
      }
      assertEquals("서로 다른 여덟 프레임을 실제로 표시", 8, frameHashes.size());
    }
  }
}
