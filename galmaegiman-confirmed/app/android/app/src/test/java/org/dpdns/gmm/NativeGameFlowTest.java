package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 계정·API 요청 없이 안내 클릭과 보상 이동, 대전 소개 창의 생명주기를 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeGameFlowTest {
  @Before
  public void isolate() {
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("native_account", 0).edit().clear().commit();
    app.getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .commit();
  }

  ActivityController<MainActivity> start() {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create();
    controller.get().motionEnabled = false;
    controller.start().resume().visible();
    awaitStartupResources(controller.get());
    return controller;
  }

  /** Install per-screen fake APIs only after the real public-art startup gate settles. */
  static void awaitStartupResources(MainActivity host) {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
      GameResourcePreparation.Progress progress = org.robolectric.util.ReflectionHelpers.getField(host, "resourceProgress");
      if (progress != null && progress.done && progress.failed > 0)
        fail("Bundled startup resources failed: " + progress.failures);
      boolean accepted = org.robolectric.util.ReflectionHelpers.getField(host, "resourcesAccepted");
      Runnable continuation = org.robolectric.util.ReflectionHelpers.getField(host, "resourceContinuation");
      if (accepted && continuation == null && !host.updateBlocks()) return;
      try { Thread.sleep(5); }
      catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while awaiting real startup resources", interrupted);
      }
    }
    fail("Timed out waiting for real startup preparation and its navigation callback");
  }

  void measure(View view) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 915, 412);
  }

  JSONObject fighter(String key) {
    return MainActivity.obj(
        "key",
        key,
        "id",
        "D1",
        "name",
        "갈매미",
        "image",
        "",
        "level",
        11,
        "hp",
        100,
        "maxHp",
        100,
        "attr",
        "돌",
        "attrCode",
        "STONE",
        "role",
        "DEFENSE",
        "status",
        new JSONObject(),
        "base",
        new JSONObject(),
        "skills",
        new JSONArray());
  }

  JSONObject battle(String session) {
    JSONArray teams = new JSONArray();
    for (int i = 0; i < 2; i++)
      teams.put(
          MainActivity.obj(
              "active",
              0,
              "fighters",
              new JSONArray().put(fighter("side" + i)),
              "augments",
              new JSONArray(),
              "legal",
              new JSONArray()));
    return MainActivity.obj(
        "session",
        session,
        "phase",
        "action",
        "mode",
        "normal",
        "turn",
        1,
        "revision",
        0,
        "teams",
        teams,
        "log",
        new JSONArray(),
        "displayEvents",
        new JSONArray(),
        "matchInfo",
        MainActivity.obj("foeName", "상대 수비대", "foeTier", "실버", "bot", false));
  }

  View tag(View view, String wanted) {
    if (wanted.equals(view.getTag())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = tag(group.getChildAt(i), wanted);
        if (found != null) return found;
      }
    }
    return null;
  }

  String labels(View view) {
    StringBuilder out = new StringBuilder();
    if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) out.append(labels(group.getChildAt(i)));
    }
    return out.toString();
  }

  @Test
  public void firstBattleSpotlightTargetsActualAttackAndOpensSkills() {
    ActivityController<MainActivity> controller = start();
    try {
      MainActivity host = controller.get();
      assertEquals("", host.api.token());
      host.api.close();
      final String session = "readonly-legacy-tutorial-replay";
      final int[] writes = {0};
      host.api = new NativeApi(host) {
        @Override String token() { return session; }
        @Override void call(String path, JSONObject body, Result done) {
          if (body != null) {
            writes[0]++;
            fail("Tutorial replay must never persist an action: " + path);
          }
          assertEquals("Only the existing read-only starter check is permitted", "starter-loans", path);
          done.done(StarterLoanTestFixture.ineligible(), null);
        }
      };
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
      host.state =
          MainActivity.obj(
              "tutorial", MainActivity.obj("flow", "", "step", 4, "seen", new JSONArray().put("tut")));
      host.battle = battle("guided-battle");
      host.guide.onStateUpdated(false);
      assertTrue("The authenticated legacy context may show guidance", host.guide.canGuide());
      host.guide.progress.replay = true;
      host.guide.progress.step = 4;
      host.guide.progress.typeSeen = true;
      assertFalse("Replay guidance cannot save progress", host.guide.canPersist());
      host.battleScreen();
      measure(host.root);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      measure(host.root);
      BattleView battleView = null;
      for (int i = 0; i < host.root.getChildCount(); i++)
        if (host.root.getChildAt(i) instanceof BattleView)
          battleView = (BattleView) host.root.getChildAt(i);
      assertNotNull(battleView);
      // Authenticated local replay exercises the real guide without writing tutorial progress.
      host.guide.battleGuide();
      measure(host.root);
      NativeTutorialOverlay overlay = host.guide.overlay;
      assertNotNull(overlay);
      assertTrue("안내 대상은 명령판 컨테이너가 아닌 실제 공격 버튼이어야 해요", overlay.target instanceof Button);
      overlay.locate();
      assertFalse(overlay.hole.isEmpty());
      float x = overlay.hole.centerX(), y = overlay.hole.centerY();
      MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
      MotionEvent up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, x, y, 0);
      overlay.onTouchEvent(down);
      overlay.onTouchEvent(up);
      down.recycle();
      up.recycle();
      assertEquals("skills", battleView.pane);
      assertEquals(session, host.api.token());
      assertEquals(0, writes[0]);
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void rewardGrowthButtonUsesExactInstanceKeyAndUnavailableGrowthHasNoAction() {
    ActivityController<MainActivity> controller = start();
    try {
      MainActivity host = controller.get();
      JSONObject ready =
          MainActivity.obj(
              "key",
              "D1-instance-2",
              "name",
              "같은 갈매미",
              "level",
              12,
              "fromLevel",
              11,
              "hp",
              100,
              "maxHp",
              100,
              "xp",
              3,
              "xpNext",
              30,
              "canLearn",
              true,
              "canPromote",
              false,
              "canEvolve",
              false);
      JSONObject unavailable =
          MainActivity.obj(
              "key",
              "D1-instance-1",
              "name",
              "같은 갈매미",
              "level",
              11,
              "fromLevel",
              11,
              "hp",
              50,
              "maxHp",
              100,
              "xp",
              5,
              "xpNext",
              25,
              "canLearn",
              false,
              "canPromote",
              false,
              "canEvolve",
              false);
      JSONObject result =
          MainActivity.obj(
              "mode",
              "story",
              "winner",
              0,
              "result",
              MainActivity.obj("xp", 12, "growth", new JSONArray().put(ready).put(unavailable)));
      AtomicReference<String> key = new AtomicReference<>();
      ScrollView reward = BattleRewardsView.create(host, result, key::set);
      host.root.removeAllViews();
      host.root.addView(reward);
      measure(host.root);
      View button = tag(reward, "reward-growth-D1-instance-2");
      assertNotNull(button);
      assertTrue(button.isEnabled());
      assertNull(tag(reward, "reward-growth-D1-instance-1"));
      button.performClick();
      assertEquals("D1-instance-2", key.get());
      assertEquals("", host.api.token());
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void versusShowsServerOpponentAndOldTimerClosesOnlyItsOwnDialog() {
    ActivityController<MainActivity> controller = start();
    try {
      MainActivity host = controller.get();
      host.state = MainActivity.obj("me", MainActivity.obj("nickname", "내 수비대"));
      measure(host.root);
      host.battle = battle("first-match");
      NativeVersusView.show(host, host.battle);
      AlertDialog first = host.visibleDialog;
      assertNotNull(first);
      assertTrue(first.isShowing());
      String text = labels(first.getWindow().getDecorView());
      assertTrue(text.contains("상대 수비대"));
      assertTrue(text.contains("실버"));
      assertFalse(text.contains("연습 팀"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      host.battle = battle("second-match");
      try {
        host.battle.getJSONObject("matchInfo").put("foeTier", "").put("bot", true);
      } catch (JSONException e) {
        throw new AssertionError(e);
      }
      NativeVersusView.show(host, host.battle);
      AlertDialog second = host.visibleDialog;
      assertNotNull(second);
      assertNotSame(first, second);
      assertTrue(labels(second.getWindow().getDecorView()).contains("연습 팀"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
      assertFalse("세션 변경 뒤에도 이전 대전 소개 창은 닫혀야 해요", first.isShowing());
      assertTrue(second.isShowing());
      assertSame(second, host.visibleDialog);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      assertFalse(second.isShowing());
      assertNull(host.visibleDialog);
      assertEquals("", host.api.token());
    } finally {
      controller.pause().stop().destroy();
    }
  }
}
