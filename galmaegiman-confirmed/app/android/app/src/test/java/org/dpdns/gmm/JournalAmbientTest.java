package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

/** 비동기 순서·첫 배경·두 장 제한과 기존 단일 장면 시계의 수명주기를 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalAmbientTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  static final class Pending {
    LocalTimeTheme.Phase phase;
    BooleanSupplier wanted;
    Consumer<Bitmap> ready;

    Pending(LocalTimeTheme.Phase phase, BooleanSupplier wanted, Consumer<Bitmap> ready) {
      this.phase = phase;
      this.wanted = wanted;
      this.ready = ready;
    }
  }

  static final class Loader implements JournalAmbient.Loader {
    final ArrayList<Pending> requests = new ArrayList<>();

    public void load(
        LocalTimeTheme.Phase phase,
        int width,
        int height,
        BooleanSupplier wanted,
        Consumer<Bitmap> ready) {
      assertTrue(width > 0 && height > 0);
      requests.add(new Pending(phase, wanted, ready));
    }
  }

  Bitmap image(int color) {
    Bitmap b = Bitmap.createBitmap(189, 83, Bitmap.Config.ARGB_8888);
    b.eraseColor(color);
    return b;
  }

  JournalAmbient ambient(MainActivity host, Loader loader) {
    FrameLayout owner = new FrameLayout(host);
    owner.layout(0, 0, 915, 412);
    JournalAmbient ambient = new JournalAmbient(owner, host, new SceneMotion(owner, host), loader);
    ambient.resize(915, 412);
    ambient.attach();
    return ambient;
  }

  void startVisibleMotion(FrameLayout owner, SceneMotion motion) {
    // headless WindowManager가 생략한 앱 가시성 통보를 실제 ViewRoot 경로로 전달한다.
    Object viewRoot =
        ReflectionHelpers.callInstanceMethod(
            motion.host.getWindow().getDecorView(), "getViewRootImpl");
    assertNotNull(viewRoot);
    ReflectionHelpers.callInstanceMethod(
        viewRoot,
        "handleAppVisibility",
        ReflectionHelpers.ClassParameter.from(boolean.class, true));
    ReflectionHelpers.callInstanceMethod(viewRoot, "performTraversals");
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    motion.start();
    owner.getViewTreeObserver().dispatchOnPreDraw();
    assertNotNull(
        "장면 시계: attached="
            + owner.isAttachedToWindow()
            + ", shown="
            + owner.isShown()
            + ", window="
            + owner.getWindowVisibility()
            + ", resumed="
            + motion.host.motionResumed
            + ", enabled="
            + SceneMotion.enabled(motion.host),
        motion.clock);
  }

  @Test
  public void firstSceneUsesRequestedPhaseImmediatelyAndIgnoresStaleLoads() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      Loader loader = new Loader();
      JournalAmbient ambient = ambient(host, loader);
      assertNull("첫 로드 전 임의 낮 배경을 표시하지 않음", ambient.current);
      Pending first = loader.requests.get(0);
      ambient.request(LocalTimeTheme.Phase.NIGHT, 915, 412);
      Pending last = loader.requests.get(1);
      assertFalse("시간대가 달라진 이전 요청은 작업/결과를 무시", first.wanted.getAsBoolean());
      Bitmap night = image(0xff12233a);
      last.ready.accept(night);
      assertSame(night, ambient.current);
      assertEquals(LocalTimeTheme.Phase.NIGHT, ambient.currentPhase);
      assertNull("첫 진입에는 600ms 전환을 하지 않음", ambient.incoming);
      first.ready.accept(image(0xfff2d9b7));
      assertSame("늦게 도착한 오전 이미지가 밤을 덮지 않음", night, ambient.current);
      int requests = loader.requests.size();
      for (int i = 0; i < 3; i++) ambient.draw(new Canvas(image(0)), 0, 0);
      assertEquals("onDraw는 디코딩이나 작업 예약을 하지 않음", requests, loader.requests.size());
      ambient.detach();
      assertEquals(0, ambient.cachedSceneCount());
    }
  }

  @Test
  public void transitionKeepsOnlyTwoScenesAndCompletesAtSixHundredMilliseconds() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      Loader loader = new Loader();
      JournalAmbient ambient = ambient(host, loader);
      Bitmap first = image(0xffc9e9ed);
      loader.requests.get(0).ready.accept(first);
      host.motionEnabled = true;
      ambient.motion.clock = ValueAnimator.ofFloat(0, 1);
      ambient.motion.millis = 100;
      LocalTimeTheme.Phase target =
          ambient.currentPhase == LocalTimeTheme.Phase.EVENING
              ? LocalTimeTheme.Phase.NIGHT
              : LocalTimeTheme.Phase.EVENING;
      ambient.request(target, 915, 412);
      Bitmap evening = image(0xffbf815b);
      loader.requests.get(1).ready.accept(evening);
      assertEquals(2, ambient.cachedSceneCount());
      assertSame(first, ambient.current);
      assertSame(evening, ambient.incoming);
      ambient.motion.millis = 699;
      ambient.draw(new Canvas(image(0)), 0, 0);
      assertSame("전환 끝 전에는 현재/대상 두 장 유지", evening, ambient.incoming);
      ambient.motion.millis = 700;
      ambient.draw(new Canvas(image(0)), 0, 0);
      assertSame(evening, ambient.current);
      assertNull(ambient.incoming);
      assertEquals(1, ambient.cachedSceneCount());
      ambient.detach();
      ambient.motion.close();
    }
  }

  @Test
  public void reducedMotionAndMissingAssetRemainStaticAndDetachedLoadCannotReviveScene() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      Loader loader = new Loader();
      JournalAmbient ambient = ambient(host, loader);
      Bitmap initial = image(0xffc9e9ed);
      loader.requests.get(0).ready.accept(initial);
      LocalTimeTheme.Phase target =
          ambient.currentPhase == LocalTimeTheme.Phase.EVENING
              ? LocalTimeTheme.Phase.NIGHT
              : LocalTimeTheme.Phase.EVENING;
      ambient.request(target, 915, 412);
      Bitmap evening = image(0xffbf815b);
      loader.requests.get(1).ready.accept(evening);
      assertSame("움직임 줄이기는 즉시 정적 교체", evening, ambient.current);
      assertNull(ambient.incoming);
      ambient.request(
          target == LocalTimeTheme.Phase.NIGHT
              ? LocalTimeTheme.Phase.EVENING
              : LocalTimeTheme.Phase.NIGHT,
          915,
          412);
      loader.requests.get(2).ready.accept(null);
      assertSame("로컬 자산 실패가 게임 조작이나 현재 배경을 없애지 않음", evening, ambient.current);
      ambient.request(LocalTimeTheme.Phase.MORNING, 915, 412);
      Pending pending = loader.requests.get(3);
      ambient.detach();
      pending.ready.accept(image(0xffffddaa));
      assertFalse(pending.wanted.getAsBoolean());
      assertNull(ambient.current);
      assertNull(ambient.incoming);
    }
  }

  @Test
  public void returningToCurrentPhaseReusesSceneAndCancelsObsoleteDecode() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      Loader loader = new Loader();
      JournalAmbient ambient = ambient(host, loader);
      Bitmap initial = image(0xffc9e9ed);
      loader.requests.get(0).ready.accept(initial);
      LocalTimeTheme.Phase original = ambient.currentPhase;
      LocalTimeTheme.Phase other =
          original == LocalTimeTheme.Phase.NIGHT
              ? LocalTimeTheme.Phase.DAY
              : LocalTimeTheme.Phase.NIGHT;
      ambient.request(other, 915, 412);
      Pending obsolete = loader.requests.get(1);
      ambient.request(original, 915, 412);
      assertEquals("같은 크기·시간대는 다시 디코딩하지 않음", 2, loader.requests.size());
      assertFalse(obsolete.wanted.getAsBoolean());
      obsolete.ready.accept(image(0xff12233a));
      assertSame(initial, ambient.current);
      assertEquals(original, ambient.currentPhase);
      assertEquals(1, ambient.cachedSceneCount());
      ambient.detach();
    }
  }

  @Test
  public void originalSceneClockStopsOnPauseHiddenSystemOffAndDetach() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      float old =
          Settings.Global.getFloat(
              host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
      FrameLayout owner = new FrameLayout(host);
      SceneMotion motion = new SceneMotion(owner, host);
      try {
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        host.root.removeAllViews();
        host.root.addView(owner, new FrameLayout.LayoutParams(-1, -1));
        fixtures.measure(host.root);
        host.motionEnabled = true;
        startVisibleMotion(owner, motion);
        c.pause();
        assertNull("백그라운드 시 반복 시계 해제", motion.clock);
        double paused = motion.millis;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(paused, motion.millis, 0);
        c.resume();
        assertNotNull(motion.clock);
        owner.setVisibility(View.INVISIBLE);
        owner.getViewTreeObserver().dispatchOnPreDraw();
        assertNull("화면 미노출에서는 정지", motion.clock);
        owner.setVisibility(View.VISIBLE);
        owner.getViewTreeObserver().dispatchOnPreDraw();
        assertNotNull(motion.clock);
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull("시스템 애니메이션 끄기는 API 24 분기도 포함해 정지", motion.clock);
        motion.close();
        assertNull(motion.clock);
      } finally {
        motion.close();
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, old);
      }
    }
  }

  @Test
  public void systemOffDuringFadeRequestsOneStaticDrawWithoutPreDrawInvalidationLoop() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      MainActivity host = c.get();
      float old =
          Settings.Global.getFloat(
              host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
      class ObservedScene extends FrameLayout {
        int invalidations;

        ObservedScene() {
          super(host);
        }

        @Override
        public void invalidate() {
          invalidations++;
          super.invalidate();
        }
      }
      ObservedScene owner = new ObservedScene();
      SceneMotion motion = new SceneMotion(owner, host);
      Loader loader = new Loader();
      JournalAmbient ambient = new JournalAmbient(owner, host, motion, loader);
      try {
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        host.root.removeAllViews();
        host.root.addView(owner, new FrameLayout.LayoutParams(-1, -1));
        fixtures.measure(host.root);
        ambient.resize(915, 412);
        ambient.attach();
        loader.requests.get(0).ready.accept(image(0xffc9e9ed));
        host.motionEnabled = true;
        startVisibleMotion(owner, motion);
        LocalTimeTheme.Phase target =
            ambient.currentPhase == LocalTimeTheme.Phase.NIGHT
                ? LocalTimeTheme.Phase.DAY
                : LocalTimeTheme.Phase.NIGHT;
        ambient.request(target, 915, 412);
        Bitmap next = image(0xff12233a);
        loader.requests.get(1).ready.accept(next);
        assertSame(next, ambient.incoming);
        motion.millis += 300;
        ambient.draw(new Canvas(image(0)), 0, 0);
        assertNotNull("설정 변경 직전에는 전환 도중", ambient.incoming);
        owner.invalidations = 0;
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(motion.clock);
        assertTrue("시스템 설정 observer가 마지막 정적 프레임을 예약", owner.invalidations > 0);
        ambient.draw(new Canvas(image(0)), 0, 0);
        assertSame(next, ambient.current);
        assertNull("마지막 draw에서 중간 알파가 남지 않음", ambient.incoming);
        owner.invalidations = 0;
        for (int i = 0; i < 10; i++) owner.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals("중지 상태 pre-draw는 반복 무효화하지 않음", 0, owner.invalidations);
      } finally {
        ambient.detach();
        motion.close();
        Settings.Global.putFloat(
            host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, old);
      }
    }
  }

  @Test
  public void coverCoordinatesGroundOriginalCharacterOnMapForBothAspectRatios() {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {1006, 411}}) {
      RectF box = new RectF();
      JournalAmbient.coverBounds(box, size[0], size[1]);
      assertTrue(box.left < 0 && box.top < 0);
      assertTrue(box.right > size[0] && box.bottom > size[1]);
      float foot = box.top + box.height() * JournalAmbient.HERO_FOOT;
      assertTrue("대표 캐릭터 발은 지도 테이블 지면 위", foot > size[1] * .74f && foot < size[1] * .88f);
    }
  }

  @Test
  public void lobbyCanvasUsesScaledDensityAndKeepsFontMetricsWithinFixedRows() {
    try (ActivityController<MainActivity> c = fixtures.start()) {
      LobbyView lobby = new LobbyView(c.get());
      try {
        RuntimeEnvironment.setFontScale(1);
        float normal = lobby.canvasTextSize(12, 100, true);
        lobby.p.setTypeface(c.get().headline);
        lobby.p.setTextSize(normal);
        float normalWidth = lobby.p.measureText("수비대");
        assertEquals("기본 크기는 원래 Canvas 배치 유지", 12, normal, .01);
        RuntimeEnvironment.setFontScale(1.3f);
        float large = lobby.canvasTextSize(12, 100, true);
        assertEquals("Canvas 글자에도 시스템 130% 적용", normal * 1.3, large, .02);
        String fitted = lobby.fitted("수비대", normalWidth + .1f, large, true);
        assertTrue("맞지 않는 긴 행은 확대된 실제 크기로 말줄임", fitted.endsWith("…"));
        lobby.label(new Canvas(image(0)), fitted, 60, 30, large, true);
        assertEquals("맞춤 검사와 실제 draw는 동일 픽셀 크기 사용", large, lobby.p.getTextSize(), 0);
        float capped = lobby.canvasTextSize(24, 19, false);
        lobby.p.setTypeface(c.get().prose);
        lobby.p.setTextSize(capped);
        android.graphics.Paint.FontMetrics fm = lobby.p.getFontMetrics();
        assertTrue("고정 행 안에서 기존 글꼴 높이 상한 보존", fm.descent - fm.ascent <= 19.01);
      } finally {
        RuntimeEnvironment.setFontScale(1);
      }
    }
  }
}
