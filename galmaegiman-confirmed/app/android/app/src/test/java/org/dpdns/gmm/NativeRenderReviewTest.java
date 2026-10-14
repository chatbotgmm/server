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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 앱 View와 원본 글꼴·이미지를 Android native Skia로 렌더한다. 웹 모형을 사용하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTest {
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
          label + " 터치 높이 " + v.getHeight() + " / shown=" + v.isShown(),
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

  void checkBattleGeometry(BattleView v, String name, int w, int h) throws Exception {
    Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    float world = w * .645f;
    v.scene.fighter(
        new Canvas(mask),
        v.active(1),
        new android.graphics.RectF(world * .51f, h * .2f, world * .95f, h * .66f));
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

  void capture(MainActivity a, String name, int w, int h) throws Exception {
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
    assertEquals("격리 검토 서버만 허용", "http://127.0.0.1:3999", BuildConfig.SERVER_URL);
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
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
  }

  @Test
  public void parallaxKeepsControlsFixedAndMuteRestoresVolume() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
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
          if (screen.equals("settings")) {
            popup(a, prefix + screen);
            a.visibleDialog.dismiss();
          } else capture(a, prefix + screen, size[0], size[1]);
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

  @Test
  public void titleDialogueAndBattlePlayback() throws Exception {
    try (ActivityController<MainActivity> c = start()) {
      MainActivity a = c.get();
      String token = a.api.token();
      JSONObject state = a.state;
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        String prefix = size[0] == 915 ? "phone-" : "tablet-";
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
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
      assertTrue("서버 선택 마감 시각", a.battle.optLong("chooseDeadline") > a.battle.optLong("serverNow"));
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
      await(() -> animationView.playback.effect.equals("combo"));
      capture(a, "tablet-combo-animation", 1280, 720);
      await(
          () ->
              animationView.playback.effect.equals("attack")
                  || animationView.playback.effect.equals("hit"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
      assertTrue("중간 프레임 진행", animationView.playback.progress > 0);
      capture(a, "tablet-battle-animation", 1280, 720);
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
    }
  }
}
