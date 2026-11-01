package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** G의 실제 첫 화면: 원본 식별·접지·접근 가능한 배치·기존 진입 행동을 검사한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TitleGTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before
  public void isolate() {
    fixtures.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restore() {
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_display", 0)
        .edit()
        .clear()
        .commit();
  }

  static final class Api extends NativeApi {
    final List<String> calls = new ArrayList<>();

    Api(MainActivity host) {
      super(host);
    }

    @Override
    String token() {
      return "local-title-g-fixture";
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      calls.add(path);
    }
  }

  Api api(MainActivity host) {
    host.api.close();
    Api api = new Api(host);
    host.api = api;
    host.guide.enabled = false;
    host.state = MainActivity.obj("me", MainActivity.obj("nickname", "긴 닉네임을 가진 실제 계정 표시 검사"));
    return api;
  }

  TitleView title(MainActivity host, boolean loggedIn) {
    host.screen = "title";
    host.root.removeAllViews();
    TitleView title = new TitleView(host, loggedIn);
    host.root.addView(title, new FrameLayout.LayoutParams(-1, -1));
    return title;
  }

  void ready(TitleView view) throws Exception {
    long until = System.nanoTime() + 8_000_000_000L;
    while (view.guardianArt == null && System.nanoTime() < until) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
      Thread.sleep(10);
    }
    assertNotNull("APK의 전투 원본 캐릭터 준비", view.guardianArt);
    assertNotNull(view.guardianSource);
  }

  RectF box(View view) {
    return new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
  }

  void textFits(TextView text) {
    assertNotNull(text.getLayout());
    int lines = text.getLayout().getLineCount();
    assertTrue("본문 전체 줄 표시", lines <= text.getMaxLines());
    assertTrue(
        "본문 마지막 줄 세로 잘림",
        text.getLayout().getLineBottom(lines - 1)
            <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
    for (int i = 0; i < lines; i++)
      assertTrue(
          "제목/부제 가로 잘림: " + text.getText(),
          text.getLayout().getLineWidth(i)
              <= text.getWidth()
                  - text.getCompoundPaddingLeft()
                  - text.getCompoundPaddingRight()
                  + 1);
  }

  @Test
  public void originalBattleArtAndLargeFontsFitPhoneFlipSmallPhoneAndTablet() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      Api api = api(host);
      for (float scale : new float[] {1, 1.3f}) {
        RuntimeEnvironment.setFontScale(scale);
        for (int[] size : new int[][] {{780, 360}, {915, 412}, {1006, 411}, {1280, 720}}) {
          TitleView title = title(host, true);
          host.root.setPadding(34, 8, 18, 12);
          review.viewport(host, size[0], size[1]);
          ready(title);
          review.viewport(host, size[0], size[1]);
          RectF safe = host.safeBounds(size[0], size[1]);
          assertEquals(View.GONE, title.journal.getVisibility());
          assertEquals(0, title.journal.getChildCount());
          assertEquals(host.me().getString("nickname"), title.account.getText().toString());
          assertTrue(
              title
                  .account
                  .getContentDescription()
                  .toString()
                  .contains(host.me().getString("nickname")));
          List<View> fixed =
              List.of(
                  title.caption,
                  title.titleFirst,
                  title.titleSecond,
                  title.enter,
                  title.settings,
                  title.account);
          for (View child : fixed) {
            assertTrue("안전 영역 내부: " + child.getTag(), safe.contains(box(child)));
          }
          for (int i = 0; i < fixed.size(); i++)
            for (int j = i + 1; j < fixed.size(); j++)
              assertFalse(
                  "UI 겹침 " + fixed.get(i).getTag() + "/" + fixed.get(j).getTag(),
                  RectF.intersects(box(fixed.get(i)), box(fixed.get(j))));
          for (Button button : new Button[] {title.enter, title.settings, title.account}) {
            assertTrue(button.getHeight() >= host.dp(48));
            assertTrue(button.getWidth() >= host.dp(48));
          }
          textFits(title.caption);
          textFits(title.titleFirst);
          textFits(title.titleSecond);
          textFits(title.enter);
          assertTrue("캐릭터는 고정 UI의 왼쪽", title.guardianBox(null).right < title.mainBox().left);
          RectF stage = title.guardianBox(null);
          for (float dragX : new float[] {-host.dp(18), 0, host.dp(18)})
            for (float dragY : new float[] {-host.dp(12), 0, host.dp(12)})
              for (float pulse : new float[] {0, 1}) {
                title.parallax.x = dragX;
                title.parallax.y = dragY;
                RectF drawn = title.guardianDrawBox(stage, .9f, pulse);
                assertTrue("전신은 안전한 책상 영역 안에 표시", safe.contains(drawn));
                assertTrue("드래그·호흡 중에도 오른쪽 UI와 분리", drawn.right < title.mainBox().left);
              }
          title.parallax.x = title.parallax.y = 0;
          assertEquals(0, api.calls.size());
        }
      }
      TitleView title = (TitleView) host.root.getChildAt(0);
      try (InputStream input = host.getAssets().open(TitleView.GUARDIAN_PATH)) {
        Bitmap original = BitmapFactory.decodeStream(input);
        assertEquals("최신 기본 갈매미맨의 투명 전신 경로", "roster60-art/u01-front.webp", TitleView.GUARDIAN_PATH);
        assertTrue("별도 생성·옛 캐릭터 대체 없이 APK u01 원본", original.sameAs(title.guardianArt));
        original.recycle();
      }
    }
  }

  @Test
  public void loginStartSettingsAndRealNicknameAccountKeepTheirExistingActions() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      Api api = api(host);
      TitleView out = title(host, false);
      review.viewport(host, 915, 412);
      assertEquals("게임 시작", out.enter.getText().toString());
      assertNull(out.account);
      out.enter.performClick();
      assertEquals("login", host.screen);
      TitleView backdrop = (TitleView) host.root.getChildAt(0);
      ready(backdrop);
      review.viewport(host, 915, 412);
      assertTrue(backdrop.loginBackdrop);
      assertEquals(View.VISIBLE, backdrop.enter.getVisibility());
      assertEquals(EntryActionUi.KAKAO_LABEL, backdrop.enter.getText().toString());
      for (View view :
          new View[] {
            backdrop.caption,
            backdrop.titleFirst,
            backdrop.titleSecond,
            backdrop.settings
          }) assertEquals("로그인은 바로 전 시작 화면의 제목과 고정 구성을 유지", View.VISIBLE, view.getVisibility());
      assertTrue(
          "원본 캐릭터는 시작 화면과 같은 왼쪽에 유지",
          backdrop.guardianBox(null).right < backdrop.mainBox().left);
      assertEquals("뒤로", backdrop.settings.getText().toString());
      assertEquals(View.VISIBLE, backdrop.loginNote.getVisibility());
      assertNull("구형 기록부 로그인 판은 표시하지 않음", host.loginScroll);
      TitleView in = title(host, true);
      review.viewport(host, 915, 412);
      assertEquals("게임 시작", in.enter.getText().toString());
      in.settings.performClick();
      assertNotNull(host.visibleDialog);
      host.visibleDialog.dismiss();
      in.account.performClick();
      assertEquals("account", host.screen);
      assertTrue(api.calls.contains("me"));
      in = title(host, true);
      review.viewport(host, 915, 412);
      in.enter.performClick();
      assertTrue("게임 시작은 기존 상태 로딩 경로", api.calls.contains("native/state"));
    }
  }

  @Test
  public void breathingKeepsFeetFixedAndPlatformMotionResetsDrag() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      api(host);
      TitleView title = title(host, true);
      review.viewport(host, 915, 412);
      ready(title);
      RectF bounds = title.guardianBox(null);
      RectF resting = title.guardianDrawBox(bounds, .9f, 0),
          breathing = title.guardianDrawBox(bounds, .9f, 1);
      assertEquals("호흡으로 발이 뜨지 않음", resting.bottom, breathing.bottom, 0);
      assertTrue("상체만 작은 호흡 확장", breathing.top < resting.top);
      Rect fixed = new Rect();
      title.enter.getHitRect(fixed);
      title.parallax.x = 12;
      title.parallax.y = 7;
      RectF dragged = title.guardianDrawBox(bounds, .9f, 0);
      assertTrue("접지선 아래로 드래그하지 않음", dragged.bottom <= bounds.bottom);
      assertTrue("전경 영역의 왼쪽 경계 보존", dragged.left >= bounds.left);
      assertTrue("전경 영역의 오른쪽 경계 보존", dragged.right <= bounds.right);
      Rect after = new Rect();
      title.enter.getHitRect(after);
      assertEquals(fixed, after);
      float previousScale = android.provider.Settings.Global.getFloat(
          host.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
      try {
        android.provider.Settings.Global.putFloat(host.getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        host.journalTime.motionPreferenceChanged();
        assertFalse("Platform animation-off remains authoritative", SceneMotion.enabled(host));
        assertEquals(0, title.parallax.x, 0);
        assertEquals(0, title.parallax.y, 0);
        assertNull(title.motion.clock);
      } finally {
        android.provider.Settings.Global.putFloat(host.getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, previousScale);
        host.journalTime.motionPreferenceChanged();
      }
    }
  }

  @Test
  public void brassCtaIsOpaqueAndTextContrastExceedsFourPointFive() {
    assertTrue(
        ColorUtils.calculateContrast(TitleHarborDrawable.TEXT, TitleHarborDrawable.TOP) >= 4.5);
    assertTrue(
        ColorUtils.calculateContrast(TitleHarborDrawable.TEXT, TitleHarborDrawable.BOTTOM) >= 4.5);
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      TitleHarborDrawable button = new TitleHarborDrawable(controller.get(), true);
      button.setBounds(0, 0, 320, 64);
      int[] colors = new int[2];
      int i = 0;
      for (int background : new int[] {Color.RED, Color.BLUE}) {
        Bitmap bitmap = Bitmap.createBitmap(320, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(background);
        button.draw(new Canvas(bitmap));
        colors[i++] = bitmap.getPixel(160, 32);
        bitmap.recycle();
      }
      assertEquals("밤낮 배경에 따라 CTA 면이 변하지 않음", colors[0], colors[1]);
      assertTrue(ColorUtils.calculateContrast(TitleHarborDrawable.TEXT, colors[0]) >= 4.5);
    }
  }
}
