package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowDisplayManager;

/** 실제 앱 View와 원본 글꼴·이미지를 Android native Skia로 렌더한다. 웹 모형을 사용하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTest {
  @Before
  public void scheduledFramesUseThePausedLooperClock() {
    ShadowChoreographer.setPaused(true);
    ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
  }

  void await(BooleanSupplier condition) throws Exception {
    long end = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < end) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      if (condition.getAsBoolean()) return;
      Thread.sleep(30);
    }
    fail("네이티브 화면 준비 대기 시간 초과");
  }

  void dump(View v, StringBuilder b, int depth) {
    b.append(" ".repeat(depth))
        .append(v.getClass().getSimpleName())
        .append(" ")
        .append(v.getLeft())
        .append(",")
        .append(v.getTop())
        .append(" ")
        .append(v.getWidth())
        .append("x")
        .append(v.getHeight());
    if (v instanceof TextView) b.append(" ").append(((TextView) v).getText());
    b.append("\n");
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) dump(g.getChildAt(i), b, depth + 1);
    }
  }

  void force(View v) {
    v.forceLayout();
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) force(group.getChildAt(i));
    }
  }

  int checkButtons(View v, View root, int width, int height) {
    int checked = 0;
    if (v instanceof Button && v.getVisibility() == View.VISIBLE && v.getWidth() > 0) {
      float x = v.getLeft(), y = v.getTop();
      boolean sx = false, sy = false;
      android.view.ViewParent parent = v.getParent();
      while (parent instanceof View && parent != root) {
        View q = (View) parent;
        sx |= q instanceof HorizontalScrollView;
        sy |= q instanceof ScrollView;
        x += q.getLeft() - q.getScrollX();
        y += q.getTop() - q.getScrollY();
        parent = q.getParent();
      }
      String label = ((Button) v).getText().toString();
      if (!sx)
        assertTrue(label + " 가로 잘림: " + x + "/" + width, x >= -1 && x + v.getWidth() <= width + 1);
      if (!sy)
        assertTrue(
            label + " 세로 잘림: " + y + "/" + height, y >= -1 && y + v.getHeight() <= height + 1);
      assertTrue(
          label
              + " 터치 높이 "
              + v.getHeight()
              + " / 설명="
              + v.getContentDescription()
              + " / shown="
              + v.isShown(),
          !v.isShown() || v.getHeight() >= 48);
      checked++;
    }
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++)
        checked += checkButtons(group.getChildAt(i), root, width, height);
    }
    return checked;
  }

  /** 출석 보상 숫자는 활성 일차의 두꺼운 프레임 안쪽에서도 온전히 읽혀야 한다. */
  int checkDailyRewardInk(View view) {
    int checked = 0;
    CharSequence description = view.getContentDescription();
    if (view instanceof ViewGroup
        && description != null
        && description.toString().matches("\\d+일 .* · 지원서 \\d+장")) {
      ViewGroup card = (ViewGroup) view;
      for (int i = 0; i < card.getChildCount(); i++) {
        View child = card.getChildAt(i);
        if (!(child instanceof TextView)) continue;
        TextView reward = (TextView) child;
        if (!reward.getText().toString().matches("\\d+")) continue;
        assertNotNull("출석 보상 숫자의 실제 글꼴 배치", reward.getLayout());
        float left =
            reward.getLeft()
                + reward.getCompoundPaddingLeft()
                + reward.getLayout().getLineLeft(0)
                - reward.getScrollX();
        float right =
            reward.getLeft()
                + reward.getCompoundPaddingLeft()
                + reward.getLayout().getLineRight(0)
                - reward.getScrollX();
        float edge = 6 * view.getResources().getDisplayMetrics().density;
        assertTrue(description + " 숫자가 왼쪽 프레임을 침범: " + left, left >= edge - 1);
        assertTrue(description + " 숫자가 오른쪽 프레임을 침범: " + right, right <= card.getWidth() - edge + 1);
        checked++;
      }
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        checked += checkDailyRewardInk(group.getChildAt(i));
    }
    return checked;
  }

  void checkBattleGeometry(BattleView v, String name, int w, int h) throws Exception {
    Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    float world = w * .645f;
    v.scene.fighter(
        new Canvas(mask),
        v.active(1),
        new android.graphics.RectF(world * .51f, h * .2f, world * .95f, h * .66f),
        1);   // 상대 진영(앞모습): Scene.fighter에 진영 인자가 추가됨
    int[] pixels = new int[w * h];
    mask.getPixels(pixels, 0, w, 0, 0, w, h);
    mask.recycle();
    int total = 0;
    for (android.graphics.RectF box :
        new android.graphics.RectF[] {
          new android.graphics.RectF(w * .055f, h * .04f, w * .335f, h * .29f),
          new android.graphics.RectF(w * .30f, h * .69f, w * .575f, h * .965f)
        }) {
      for (int y = (int) box.top; y < (int) box.bottom; y++)
        for (int x = (int) box.left; x < (int) box.right; x++)
          if (android.graphics.Color.alpha(pixels[y * w + x]) >= 80) total++;
    }
    assertEquals("상태창과 상대 그림의 불투명 영역 겹침", 0, total);
    Files.write(
        Path.of(System.getProperty("gmm.nativeReviewOutput"), name + "-overlap.json"),
        ("{\"opaqueOverlapPixels\":" + total + "}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  void viewport(MainActivity a, int w, int h) {
    // API 대기 중 Window가 다시 배치돼도 실제 표시 영역을 같은 크기로 유지한다.
    // root만 강제 측정하면 태블릿의 비동기 화면이 기본 폰 크기로 구성될 수 있다.
    ShadowDisplayManager.changeDisplay(
        Display.DEFAULT_DISPLAY, "w" + w + "dp-h" + h + "dp-land-mdpi");
    a.getWindow().setLayout(w, h);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    force(a.root);
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, w, h);
  }

  void preparePreDraw(View view, Runnable measureAndLayout) {
    // 실제 ViewRootImpl은 layout 다음 pre-draw를 보낸다. 직접 Canvas 캡처에서도
    // TextView의 단일 행 scrollX와 최종 글자 배치를 같은 순서로 보정한다.
    boolean cancelled = view.getViewTreeObserver().dispatchOnPreDraw();
    if (cancelled || view.isLayoutRequested()) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      force(view);
      measureAndLayout.run();
      // 재측정은 한 번만 허용하며, 마지막 레이아웃의 pre-draw를 반드시 거친다.
      view.getViewTreeObserver().dispatchOnPreDraw();
    }
  }

  void capture(MainActivity a, String name, int w, int h) throws Exception {
    Files.createDirectories(Path.of(System.getProperty("gmm.nativeReviewOutput")));
    // 해상도 전환 뒤 실제 Window/Display까지 맞춰 자동 배치가 다른 크기로 덮지 않게 한다.
    viewport(a, w, h);
    force(a.root);
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, w, h);
    Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    a.root.draw(new Canvas(image));
    await(a.art::idle);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    // 대기 중 Window의 자동 배치가 실행될 수 있으므로 최종 비율을 다시 적용한다.
    force(a.root);
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, w, h);
    preparePreDraw(
        a.root,
        () -> {
          a.root.measure(
              View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
          a.root.layout(0, 0, w, h);
        });
    image.eraseColor(android.graphics.Color.TRANSPARENT);
    a.root.draw(new Canvas(image));
    if (name.endsWith("battle-menu"))
      checkBattleGeometry((BattleView) a.root.getChildAt(0), name, w, h);
    int checked = checkButtons(a.root, a.root, w, h);
    Files.write(
        Path.of(System.getProperty("gmm.nativeReviewOutput"), name + "-metrics.json"),
        ("{\"width\":"
                + w
                + ",\"height\":"
                + h
                + ",\"checkedButtons\":"
                + checked
                + ",\"unscrollableButtonClips\":0}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (name.endsWith("modes")) {
      StringBuilder bounds = new StringBuilder();
      dump(a.root, bounds, 0);
      Files.write(
          Path.of(System.getProperty("gmm.nativeReviewOutput"), name + "-bounds.txt"),
          bounds.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    File dir = new File(System.getProperty("gmm.nativeReviewOutput"));
    dir.mkdirs();
    try (OutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally {
      image.recycle();
    }
  }

  void popup(MainActivity a, String name) throws Exception {
    assertNotNull(a.visibleDialog);
    View v = a.visibleDialog.getWindow().getDecorView();
    int width = a.visibleDialog.getWindow().getAttributes().width;
    v.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(
            Math.min(a.root.getHeight() - 16, 520), View.MeasureSpec.AT_MOST));
    v.layout(0, 0, width, v.getMeasuredHeight());
    preparePreDraw(
        v,
        () -> {
          v.measure(
              View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(
                  Math.min(a.root.getHeight() - 16, 520), View.MeasureSpec.AT_MOST));
          v.layout(0, 0, width, v.getMeasuredHeight());
        });
    Bitmap image = Bitmap.createBitmap(width, v.getHeight(), Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(image));
    checkButtons(v, v, width, v.getHeight());
    try (OutputStream out =
        new FileOutputStream(
            new File(System.getProperty("gmm.nativeReviewOutput"), name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally {
      image.recycle();
    }
  }

  ActivityController<MainActivity> start() throws Exception {
    String tokenFile = System.getProperty("gmm.nativeReviewTokenFile", "");
    assumeTrue(!tokenFile.isEmpty() && Files.isRegularFile(Path.of(tokenFile)));
    assertTrue(
        "격리 검토 서버만 허용",
        java.util.Set.of("http://127.0.0.1:3999", "http://127.0.0.1:4002")
            .contains(BuildConfig.SERVER_URL));
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("native_account", 0)
        .edit()
        .putString(
            "token",
            new String(
                Files.readAllBytes(Path.of(tokenFile)), java.nio.charset.StandardCharsets.UTF_8))
        .apply();
    app.getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .apply();
    ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    MainActivity a = c.get();
    a.motionEnabled = false;
    NativeTutorialController.get(a).enabled = false;
    await(() -> !a.busy && a.state.has("me"));
    return c;
  }

  void drag(View v, float x, float y) {
    long time = android.os.SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent move =
        MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, x + 110, y + 45, 0);
    assertTrue("중앙 드래그 시작", v.dispatchTouchEvent(down));
    assertTrue("중앙 드래그 이동", v.dispatchTouchEvent(move));
    down.recycle();
    move.recycle();
  }

  void release(View v) {
    long time = android.os.SystemClock.uptimeMillis();
    MotionEvent up = MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, 450, 210, 0);
    v.dispatchTouchEvent(up);
    up.recycle();
    for (int frame = 0; frame < 30; frame++)
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
  }

  @Test
  public void parallaxKeepsControlsFixedAndMuteRestoresVolume() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      a.motionEnabled = true;
      a.titleScreen();
      capture(a, "phone-title-parallax-before", 915, 412);
      TitleView title = (TitleView) a.root.getChildAt(0);
      android.graphics.Rect fixed = new android.graphics.Rect();
      title.enter.getHitRect(fixed);
      drag(title, 440, 210);
      assertTrue("캐릭터 레이어 이동", title.parallax.x > 0);
      android.graphics.Rect after = new android.graphics.Rect();
      title.enter.getHitRect(after);
      assertEquals("게임 시작 UI 고정", fixed, after);
      capture(a, "phone-title-parallax", 915, 412);
      release(title);
      assertEquals(0, title.parallax.x, .01f);
      a.home();
      await(() -> ((LobbyView) a.root.getChildAt(0)).ready());
      capture(a, "phone-home-parallax-before", 915, 412);
      LobbyView lobby = (LobbyView) a.root.getChildAt(0);
      lobby.buttons.get(0).getHitRect(fixed);
      drag(lobby, 440, 210);
      assertTrue(lobby.parallax.x > 0);
      lobby.buttons.get(0).getHitRect(after);
      assertEquals("하단 메뉴 고정", fixed, after);
      capture(a, "phone-home-parallax", 915, 412);
      release(lobby);
      assertEquals(0, lobby.parallax.x, .01f);
      // 서로 다른 저장 볼륨을 유지한 채 각 조작을 독립적으로 음소거한다.
      a.sound.volume("music", 63);
      a.sound.volume("effects", 41);
      AudioControlView music = new AudioControlView(a, "배경음악", "music");
      AudioControlView effects = new AudioControlView(a, "효과음", "effects");
      music.mute.performClick();
      effects.mute.performClick();
      assertEquals(0, a.sound.audibleVolume("music"));
      assertEquals(0, a.sound.audibleVolume("effects"));
      assertEquals(63, music.slider.getProgress());
      assertEquals(41, effects.slider.getProgress());
      a.titleScreen();
      ((TitleView) a.root.getChildAt(0)).settings.performClick();
      popup(a, "phone-sound-muted");
      a.visibleDialog.dismiss();
      NativeSound restored = new NativeSound(a);
      assertTrue("앱 재실행에도 음소거 유지", restored.muted("music"));
      restored.close();
      music.mute.performClick();
      assertEquals(63, a.sound.audibleVolume("music"));
      assertEquals("효과음 음소거는 독립", 0, a.sound.audibleVolume("effects"));
      effects.mute.performClick();
      assertEquals(41, a.sound.audibleVolume("effects"));
      a.sound.masterMuted(true);
      assertEquals(0, a.sound.audibleVolume("music"));
      assertEquals(0, a.sound.audibleVolume("effects"));
      music.mute.performClick();
      music.mute.performClick();
      assertFalse("개별 음소거 해제는 전체 소리도 복구", a.sound.masterMuted());
      assertEquals(63, a.sound.audibleVolume("music"));
      assertEquals(41, a.sound.audibleVolume("effects"));
      a.titleScreen();
      ((TitleView) a.root.getChildAt(0)).settings.performClick();
      popup(a, "phone-sound-restored");
      a.visibleDialog.dismiss();
      a.sound.volume("music", 0);
      a.sound.volume("effects", 0);
    }
  }

  @Test
  public void captureAllScreens() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        String prefix = size[0] == 915 ? "phone-" : "tablet-";
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        viewport(a, size[0], size[1]);
        a.lobbyRoom = "cafe";
        a.home();
        await(() -> ((LobbyView) a.root.getChildAt(0)).ready());
        capture(a, prefix + "home", size[0], size[1]);
        for (String screen :
            new String[] {
              "modes",
              "story",
              "stages",
              "team",
              "unit",
              "collection",
              "items",
              "draw",
              "craft",
              "mix",
              "shop",
              "expedition",
              "daily",
              "pvp",
              "practice",
              "types",
              "settings",
              "account",
              "tutorial"
            }) {
          if (screen.equals("unit"))
            a.unitDetail(a.list(a.state, "units").optJSONObject(0).optString("key"));
          else if (screen.equals("practice")) a.practice();
          else if (screen.equals("tutorial")) a.tutorial();
          else a.show(screen);
          await(() -> !a.busy && a.menus.pendingFetches == 0);
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
          Thread.sleep(120);
          Shadows.shadowOf(Looper.getMainLooper()).idle();
          if (a.visibleDialog != null) {
            popup(a, prefix + screen);
            a.visibleDialog.dismiss();
          } else {
            capture(a, prefix + screen, size[0], size[1]);
            if (screen.equals("daily")) {
              Button rewards = null;
              NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
              for (View view : geometry.descendants(a.root))
                if (view instanceof Button
                    && ((Button) view).getText().toString().equals("출석표 · 보상 안내"))
                  rewards = (Button) view;
              assertNotNull("기본 출석 화면에서 전체 보상표에 접근", rewards);
              NativeNoScrollReviewTest.assertFullyVisible(rewards, a.root);
              rewards.performClick();
              View decor = geometry.layoutDialog(a);
              int count = 0;
              for (View view : geometry.descendants(decor))
                if (view instanceof TextView
                    && ((TextView) view).getText().toString().matches("\\d+일째 · 지원서 \\d+장"))
                  count++;
              assertEquals("모달에서도 매주 일곱 보상의 숫자와 단위를 모두 유지", 7, count);
              geometry.capture(a, size, prefix + "daily-rewards-modal-top");
              NativeNoScrollReviewTest.scrollModal(a, true);
              geometry.capture(a, size, prefix + "daily-rewards-modal-bottom");
              a.visibleDialog.dismiss();
            }
            if (screen.equals("craft") || screen.equals("mix")) {
              ViewGroup header = (ViewGroup) a.page.getChildAt(0);
              assertEquals("조합 머릿말은 뒤로·제목만 표시하고 재화 세 개는 생략", 2, header.getChildCount());
            }
          }
        }
      }
    }
  }

  @Test
  public void remindersSurviveRestartAndDoNotRepeat() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      Shadows.shadowOf(RuntimeEnvironment.getApplication())
          .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
      a.getSharedPreferences("native_account", 0)
          .edit()
          .putBoolean("alerts", true)
          .remove("alert_sent_1")
          .remove("alert_sent_2")
          .apply();
      long now = System.currentTimeMillis();
      JSONObject me =
          MainActivity.obj(
              "claimReadyAt",
              java.time.Instant.ofEpochMilli(now + 3600000).toString(),
              "trip",
              MainActivity.obj("endsAt", java.time.Instant.ofEpochMilli(now + 600000).toString()));
      NativeAlerts.schedule(a, me);
      android.app.AlarmManager alarms =
          (android.app.AlarmManager) a.getSystemService(android.content.Context.ALARM_SERVICE);
      assertEquals(2, Shadows.shadowOf(alarms).getScheduledAlarms().size());
      new NativeAlerts()
          .onReceive(a, new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
      assertEquals("재부팅 뒤 중복 예약 없음", 2, Shadows.shadowOf(alarms).getScheduledAlarms().size());
      android.content.Intent delivery =
          new android.content.Intent(a, NativeAlerts.class).putExtra("id", 1);
      new NativeAlerts().onReceive(a, delivery);
      android.app.NotificationManager manager =
          (android.app.NotificationManager)
              a.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
      assertEquals(1, Shadows.shadowOf(manager).size());
      String delivered = a.getSharedPreferences("native_account", 0).getString("alert_sent_1", "");
      assertFalse(delivered.isEmpty());
      new NativeAlerts().onReceive(a, delivery);
      assertEquals(1, Shadows.shadowOf(manager).size());
      NativeAlerts.schedule(a, new JSONObject());
      assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
      assertEquals(0, Shadows.shadowOf(manager).size());
      a.getSharedPreferences("native_account", 0).edit().putBoolean("alerts", false).apply();
    }
  }

  void checkSafeButtons(View v, MainActivity a) {
    if (v instanceof Button && v.isShown()) {
      android.graphics.Rect r = new android.graphics.Rect();
      if (v.getGlobalVisibleRect(r)) {
        android.graphics.Rect root = new android.graphics.Rect();
        a.root.getGlobalVisibleRect(root);
        assertTrue("컷아웃 왼쪽 조작부", r.left >= root.left + a.root.getPaddingLeft());
        assertTrue("오른쪽 모서리 조작부", r.right <= root.right - a.root.getPaddingRight());
        assertTrue("상단 모서리 조작부", r.top >= root.top + a.root.getPaddingTop());
        assertTrue("하단 시스템 영역 조작부", r.bottom <= root.bottom - a.root.getPaddingBottom());
      }
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) checkSafeButtons(g.getChildAt(i), a);
    }
  }

  void fullBleedCapture(MainActivity a, String name) throws Exception {
    capture(a, "zflip-" + name, 1006, 411);
    assertEquals(36, a.root.getPaddingLeft());
    checkSafeButtons(a.root, a);
    Bitmap image = Bitmap.createBitmap(1006, 411, Bitmap.Config.ARGB_8888);
    a.root.draw(new Canvas(image));
    int colored = 0;
    for (int y = 0; y < 411; y++)
      if (image.getPixel(4, y) != android.graphics.Color.WHITE
          && android.graphics.Color.alpha(image.getPixel(4, y)) == 255) colored++;
    image.recycle();
    assertTrue("흰 컷아웃 띠 없이 배경 채움", colored > 350);
  }

  @Test
  public void zflipFullBleedAndSafeControls() throws Exception {
    RuntimeEnvironment.setQualifiers("w1006dp-h411dp-land-mdpi");
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, (v, insets) -> insets);
      a.root.setPadding(36, 10, 8, 10);
      a.titleScreen();
      fullBleedCapture(a, "title");
      a.home();
      await(() -> ((LobbyView) a.root.getChildAt(0)).ready());
      fullBleedCapture(a, "home");
      a.login();
      fullBleedCapture(a, "login");
      a.show("draw");
      await(() -> a.menus.pendingFetches == 0);
      fullBleedCapture(a, "draw");
      a.show("modes");
      await(() -> a.menus.pendingFetches == 0);
      fullBleedCapture(a, "modes");
      a.settings();
      popup(a, "zflip-settings");
      a.visibleDialog.dismiss();
      a.practiceRoster = new java.util.ArrayList<>(java.util.Arrays.asList("D1", "T1", "E1"));
      a.startFight("practice", "1-6");
      await(() -> !a.busy && a.battle != null);
      fullBleedCapture(a, "augment");
      NativeAugmentView choice = ((BattleView) a.root.getChildAt(0)).augmentView;
      choice.picks.get(0).performClick();
      choice.confirm.performClick();
      await(() -> !a.busy && a.battle.optString("phase").equals("action"));
      fullBleedCapture(a, "battle");
      a.battleTurn(MainActivity.obj("leave", true));
      await(() -> !a.busy && a.battle.optString("phase").equals("over"));
      a.visibleDialog.dismiss();
    }
  }

  @Test
  public void idleMotionAndStoryTypingStopWithScene() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      a.motionEnabled = true;
      a.titleScreen();
      TitleView title = (TitleView) a.root.getChildAt(0);
      assertNotNull("첫 화면 반복 모션", title.motion.clock);
      title.motion.millis = 0;
      float initial = title.motion.pulse(3400, 0);
      title.motion.millis = 1700;
      assertTrue("레이어별 숨쉬기 변위", title.motion.pulse(3400, 0) > initial);
      a.show("home");
      assertNull("사라진 장면 모션 중지", title.motion.clock);
      LobbyView lobby = (LobbyView) a.root.getChildAt(0);
      assertNotNull("로비 반복 모션", lobby.motion.clock);
      boolean[] ended = {false};
      String firstLine =
          "안녕 🐦 수비대. 오늘은 항구에서 첫 순찰을 시작해요. 친구들과 바다를 지키며 함께 성장해요. 체력이 부족하면 치료약으로 준비하고, 속성을 살펴 유리한"
              + " 기술을 골라 보세요. 모든 준비가 끝나면 다음 이야기로 떠나요.";
      a.talk(
          new JSONArray()
              .put(MainActivity.obj("who", "N", "say", firstLine))
              .put(MainActivity.obj("who", "N", "say", "두 번째 이야기")),
          "이야기",
          () -> ended[0] = true);
      assertNull("로비 종료 시 모션 중지", lobby.motion.clock);
      StoryView story = (StoryView) a.root.getChildAt(0);
      story.advance(); // 첫 터치는 장면 제목을 닫는다.
      assertTrue(story.typing);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
      assertTrue(
          "타이핑 len="
              + story.say.length()
              + "/"
              + story.fullText.length()
              + " shown="
              + story.shown
              + " attached="
              + story.isAttachedToWindow()
              + " typing="
              + story.typing,
          story.say.length() > 0 && story.say.length() < story.fullText.length());
      story.next.performClick();
      assertFalse("첫 터치는 현재 줄 완성", story.typing);
      assertEquals(firstLine.replace("수비대", "방범대"), story.say.getText().toString());
      assertEquals("서버 원문 대사는 변경하지 않음", firstLine, story.lines.getJSONObject(0).getString("say"));
      assertEquals(0, story.index);
      story.next.performClick();
      assertEquals("다음 터치는 다음 대사", 1, story.index);
      assertTrue(story.typing);
      a.show("home");
      assertFalse("화면 종료 시 타이핑 중지", story.typing);
      assertFalse(ended[0]);
    }
  }

  @Test
  public void ticketClaimRefreshesBeforeClosingPopup() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      a.menus.economy.draw();
      await(() -> a.menus.pendingFetches == 0);
      capture(a, "ticket-before", 915, 412);
      int before = a.me().optInt("credits");
      assertTrue(
          "지급 가능 검토 계정 필요",
          System.currentTimeMillis()
              >= java.time.Instant.parse(a.me().optString("claimReadyAt")).toEpochMilli());
      a.menus.economy.claimTickets();
      await(() -> !a.busy && a.visibleDialog != null);
      capture(a, "ticket-after", 915, 412);
      popup(a, "ticket-popup");
      assertTrue("팝업 전에 상단 수량 갱신: " + a.me().optInt("credits"), a.me().optInt("credits") > before);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
      capture(a, "ticket-after", 915, 412);
      popup(a, "ticket-popup");
      assertTrue(a.me().optInt("credits") > before);
      a.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      assertTrue(a.me().optInt("credits") > before);
    }
  }

  void capturePlaybackFrame(MainActivity a, BattleView view, String name, int width, int height)
      throws Exception {
    String effect = view.playback.effect;
    long sequence = view.playback.cursor.seen();
    // Skia 자원 준비 중 다음 이벤트로 넘어가지 않도록 현재 프레임만 고정한다.
    view.playback.animator.cancel();
    a.api.ui.removeCallbacks(view.playback.advance);
    capture(a, name, width, height);
    assertEquals("캡처 중 같은 효과 유지", effect, view.playback.effect);
    assertEquals("캡처 중 같은 이벤트 유지", sequence, view.playback.cursor.seen());
    a.api.ui.post(view.playback.advance);
  }

  @Test
  public void identicalNamesUseServerTargetsDuringActualBattle() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      a.practiceRoster = new java.util.ArrayList<>(java.util.Arrays.asList("D1", "D1"));
      a.practiceEnemy = new java.util.ArrayList<>(java.util.Arrays.asList("D1", "D1"));
      a.startFight("practice", "P-1");
      await(() -> !a.busy && a.battle != null);
      assertEquals(
          a.list(a.list(a.battle, "teams").optJSONObject(0), "fighters")
              .optJSONObject(0)
              .optString("name"),
          a.list(a.list(a.battle, "teams").optJSONObject(1), "fighters")
              .optJSONObject(0)
              .optString("name"));
      boolean[] shown = {false, false};
      capture(a, "phone-mirror-before", 915, 412);
      for (int turn = 0;
          turn < 12 && !a.battle.optString("phase").equals("over") && (!shown[0] || !shown[1]);
          turn++) {
        if (a.battle.optString("phase").equals("choose")) {
          JSONObject team = a.list(a.battle, "teams").optJSONObject(0);
          JSONArray offers = a.list(team, "offers");
          JSONObject pick = MainActivity.obj("augment", offers.optJSONObject(0).optString("id"));
          if (team.optBoolean("needSwitch")) {
            JSONArray fighters = a.list(team, "fighters");
            for (int slot = 0; slot < fighters.length(); slot++)
              if (fighters.optJSONObject(slot).optInt("hp") > 0) {
                pick.put("switchTo", slot);
                break;
              }
          }
          a.battleTurn(MainActivity.obj("choose", pick));
        } else {
          JSONArray legal = a.list(a.list(a.battle, "teams").optJSONObject(0), "legal");
          JSONObject action = null;
          for (int i = 0; i < legal.length(); i++)
            if (legal.optJSONObject(i).optString("kind").equals("skill")) {
              action = legal.optJSONObject(i);
              break;
            }
          assertNotNull(action);
          a.battleTurn(MainActivity.obj("action", action));
        }
        while (a.busy) {
          await(
              () -> {
                if (!a.busy) return true;
                BattleView current = (BattleView) a.root.getChildAt(0);
                return current.playback.playing
                    && current.playback.effect.equals("hit")
                    && !shown[current.playback.side];
              });
          if (!a.busy) break;
          BattleView current = (BattleView) a.root.getChildAt(0);
          int side = current.playback.side;
          long sequence = current.playback.cursor.seen();
          JSONObject event = null;
          JSONArray events = a.list(current.playback.pending, "displayEvents");
          for (int i = 0; i < events.length(); i++)
            if (events.optJSONObject(i).optLong("id") == sequence) event = events.optJSONObject(i);
          assertNotNull(event);
          assertEquals("damage", event.optString("kind"));
          assertEquals("서버 지정 피해 진영", side, event.optJSONObject("target").optInt("side"));
          assertEquals("피해 진영의 표시 HP", event.optInt("hpAfter"), current.active(side).optInt("hp"));
          capturePlaybackFrame(
              a, current, side == 0 ? "phone-mirror-hit-own" : "phone-mirror-hit-foe", 915, 412);
          shown[side] = true;
        }
      }
      assertTrue("동일 이름 내 편 피격", shown[0]);
      assertTrue("동일 이름 상대 피격", shown[1]);
      capture(a, "phone-mirror-after", 915, 412);
      if (!a.battle.optString("phase").equals("over")) {
        a.battleTurn(MainActivity.obj("leave", true));
        await(() -> !a.busy);
      }
      if (a.visibleDialog != null) a.visibleDialog.dismiss();
    }
  }

  /** 검토 실패로 연출이 멈춰도 서버 최신 revision으로 연습 전투를 정리한다. */
  void clearReviewFight(MainActivity host) throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      JSONObject[] current = {null};
      String[] error = {null};
      boolean[] done = {false};
      host.api.call(
          "native/battle/current",
          null,
          (value, failure) -> {
            current[0] = value;
            error[0] = failure;
            done[0] = true;
          });
      await(() -> done[0]);
      assertNull("격리 검토 전투 조회", error[0]);
      if (current[0] == null || current[0].isNull("session")) return;
      JSONObject live = current[0];
      done[0] = false;
      host.api.call(
          "native/battle/turn",
          MainActivity.obj(
              "session",
              live.optString("session"),
              "revision",
              live.optInt("revision"),
              "leave",
              true),
          (value, failure) -> {
            current[0] = value;
            error[0] = failure;
            done[0] = true;
          });
      await(() -> done[0]);
      assertNull("격리 검토 전투 정리", error[0]);
      if (current[0] != null && "over".equals(current[0].optString("phase"))) return;
    }
    fail("검토 전투를 정리하지 못했어요.");
  }

  @Test
  public void titleDialogueAndBattlePlayback() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      clearReviewFight(a);
      try {
        String token = a.api.token();
        JSONObject state = a.state;
        for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
          String prefix = size[0] == 915 ? "phone-" : "tablet-";
          RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
          viewport(a, size[0], size[1]);
          a.api.token("");
          a.state = new JSONObject();
          a.titleScreen();
          capture(a, prefix + "title-out", size[0], size[1]);
          a.login();
          capture(a, prefix + "login", size[0], size[1]);
          a.api.token(token);
          a.state = state;
          a.titleScreen();
          capture(a, prefix + "title-in", size[0], size[1]);
          ((TitleView) a.root.getChildAt(0)).settings.performClick();
          popup(a, prefix + "sound-popup");
          a.visibleDialog.dismiss();
          a.storyScene = "deep";
          a.talk(
              new JSONArray()
                  .put(
                      MainActivity.obj(
                          "who", "N", "say", "쭈매미 천 마리가 한꺼번에 출근 도장을 찍어요. 도장대가 조금 찌그러졌어요."))
                  .put(MainActivity.obj("who", "V1", "say", "항구를 지켜라!")),
              "이야기",
              () -> a.show("story"));
          StoryView dialogue = (StoryView) a.root.getChildAt(0);
          a.art.scene(dialogue.leadImage, () -> {});
          a.art.scene(dialogue.foeImage, () -> {});
          await(dialogue::portraitsReady);
          capture(a, prefix + "dialogue", size[0], size[1]);
        }
        RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
        a.practiceRoster = new java.util.ArrayList<>(java.util.Arrays.asList("D1", "T1", "E1"));
        a.startFight("practice", "1-6");
        await(() -> !a.busy && a.battle != null);
        capture(a, "phone-battle-choose", 915, 412);
        NativeAugmentView choice = ((BattleView) a.root.getChildAt(0)).augmentView;
        assertNotNull("전체 화면 증강체 선택판", choice);
        assertTrue(
            "서버 선택 마감 시각", a.battle.optLong("chooseDeadline") > a.battle.optLong("serverNow"));
        assertTrue("카운트다운 표시", choice.title.getText().toString().contains("초"));
        assertEquals(3, choice.picks.size());
        assertFalse("고르기 전 확인 잠금", choice.confirm.isEnabled());
        int chooseRevision = a.battle.optInt("revision");
        choice.rerolls.get(0).performClick();
        await(() -> !a.busy && a.battle.optInt("revision") > chooseRevision);
        choice = ((BattleView) a.root.getChildAt(0)).augmentView;
        assertFalse("각 칸 다시 뽑기는 한 번", choice.rerolls.get(0).isEnabled());
        int pickedRevision = a.battle.optInt("revision");
        choice.picks.get(1).performClick();
        assertTrue(choice.confirm.isEnabled());
        assertEquals("카드를 누르는 것만으로 확정하지 않음", pickedRevision, a.battle.optInt("revision"));
        capture(a, "phone-battle-augment-selected", 915, 412);
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
        viewport(a, 1280, 720);
        a.battleScreen();
        capture(a, "tablet-battle-augment-selected", 1280, 720);
        choice = ((BattleView) a.root.getChildAt(0)).augmentView;
        assertTrue("선택을 화면 재배치 후에도 유지", choice.confirm.isEnabled());
        choice.confirm.performClick();
        RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
        await(() -> !a.busy && a.battle.optString("phase").equals("action"));
        capture(a, "phone-battle-menu", 915, 412);
        BattleView view = (BattleView) a.root.getChildAt(0);
        int logLeft = view.logBox.getLeft(), logTop = view.logBox.getTop();
        a.motionEnabled = true;
        drag(view, 370, 190);
        assertTrue("전투 캐릭터 드래그", view.parallax.x > 0);
        assertEquals(logLeft, view.logBox.getLeft());
        assertEquals(logTop, view.logBox.getTop());
        capture(a, "phone-battle-parallax", 915, 412);
        release(view);
        assertEquals(0, view.parallax.x, .01f);
        view.pane = "skills";
        view.render();
        capture(a, "phone-battle-skills", 915, 412);
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
        viewport(a, 1280, 720);
        a.battleScreen();
        capture(a, "tablet-battle-menu", 1280, 720);
        view = (BattleView) a.root.getChildAt(0);
        view.pane = "skills";
        view.render();
        capture(a, "tablet-battle-skills", 1280, 720);
        JSONArray legal = a.list(a.list(a.battle, "teams").optJSONObject(0), "legal");
        JSONObject action = null;
        for (int i = 0; i < legal.length(); i++)
          if (legal.optJSONObject(i).optString("skillId").startsWith("combo-")) {
            action = legal.optJSONObject(i);
            break;
          }
        assertNotNull("사용 가능한 공격 기술", action);
        int revision = a.battle.optInt("revision");
        a.battleTurn(MainActivity.obj("action", action));
        final BattleView animationView = view;
        await(() -> animationView.playback.playing);
        assertTrue(a.busy);
        assertEquals("재생이 끝날 때까지 서버 상태 교체 보류", revision, a.battle.optInt("revision"));
        try {
          await(() -> animationView.playback.effect.equals("combo"));
        } catch (AssertionError missing) {
          JSONArray events = a.list(animationView.battle, "displayEvents");
          StringBuilder trace = new StringBuilder();
          for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            trace
                .append(event.optString("kind"))
                .append(":")
                .append(event.optString("skillId"))
                .append(" actor=")
                .append(event.optJSONObject("actor"))
                .append("; ");
          }
          throw new AssertionError(
              "합동기 재생 확인: " + trace + " log=" + a.list(animationView.battle, "log"), missing);
        }
        capturePlaybackFrame(a, animationView, "tablet-combo-animation", 1280, 720);
        await(
            () ->
                animationView.playback.effect.equals("attack")
                    || animationView.playback.effect.equals("hit"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        assertTrue("중간 프레임 진행", animationView.playback.progress > 0);
        capturePlaybackFrame(a, animationView, "tablet-battle-animation", 1280, 720);
        await(() -> !a.busy && a.battle.optInt("revision") > revision);
        assertNotSame("연출 후 최종 상태로 교체", animationView, a.root.getChildAt(0));
        a.battleTurn(MainActivity.obj("leave", true));
        await(() -> !a.busy && a.battle.optString("phase").equals("over"));
        popup(a, "battle-result-popup");
        a.visibleDialog.dismiss();
        JSONObject growth =
            MainActivity.obj(
                "name",
                "태초의 갈매미맨",
                "image",
                a.unit(a.list(a.state, "team").optString(0)).optString("image"),
                "fromLevel",
                11,
                "level",
                12,
                "xp",
                18,
                "xpNext",
                100,
                "hp",
                192,
                "maxHp",
                192,
                "gained",
                88);
        new GameDialog(a)
            .setView(
                BattleRewardsView.create(
                    a,
                    MainActivity.obj(
                        "winner",
                        0,
                        "mode",
                        "story",
                        "result",
                        MainActivity.obj("xp", 88, "growth", new JSONArray().put(growth)))))
            .setPositiveButton("계속", null)
            .show();
        popup(a, "battle-growth-reward");
        a.visibleDialog.dismiss();
        a.selectionMode = "pvp";
        a.menus.teamDraft = null;
        a.show("team");
        capture(a, "tablet-pvp-lineup", 1280, 720);
        RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
        a.show("team");
        capture(a, "phone-pvp-lineup", 915, 412);
        for (int slot = 0; slot < 3; slot++) {
          View card = a.root.findViewWithTag("lineup-slot-" + slot);
          android.graphics.Rect visible = new android.graphics.Rect();
          assertTrue(card.getGlobalVisibleRect(visible));
          assertEquals("휴대폰 출전 카드 전체 표시", card.getHeight(), visible.height());
        }
        boolean[] closed = {false};
        JSONObject live = a.battle;
        a.api.call(
            "native/battle/turn",
            MainActivity.obj(
                "session",
                live.optString("session"),
                "revision",
                live.optInt("revision"),
                "leave",
                true),
            (response, error) -> closed[0] = true);
        await(() -> closed[0]);
        a.battle = null;
      } finally {
        clearReviewFight(a);
      }
    }
  }
}
