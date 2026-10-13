package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 실제 테스트 서버 계정으로 모든 Android 화면과 전투 조작을 검증·캡처한다. */
@RunWith(AndroidJUnit4.class)
public class NativeScreensTest {
  interface Check {
    boolean run(MainActivity a);
  }

  void waitFor(ActivityScenario<MainActivity> s, Check check) throws Exception {
    long until = System.currentTimeMillis() + 45000;
    while (System.currentTimeMillis() < until) {
      AtomicReference<Boolean> ok = new AtomicReference<>(false);
      s.onActivity(a -> ok.set(check.run(a)));
      if (ok.get()) return;
      Thread.sleep(150);
    }
    fail("네이티브 화면/응답 대기 시간 초과");
  }

  void shot(ActivityScenario<MainActivity> scenario, String name) throws Exception {
    // 앱 View를 직접 캡처해 소프트웨어 에뮬레이터의 시스템 오류 창이 결과를 가리지 않게 한다.
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    Thread.sleep(400);
    scenario.onActivity(
        a -> {
          Bitmap bitmap =
              Bitmap.createBitmap(a.root.getWidth(), a.root.getHeight(), Bitmap.Config.ARGB_8888);
          int w = a.root.getWidth(), h = a.root.getHeight();
          for (int i = 0; i < 2; i++) {
            a.root.forceLayout();
            a.root.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            a.root.layout(0, 0, w, h);
          }
          a.root.draw(new android.graphics.Canvas(bitmap));
          File dir = new File(a.getExternalFilesDir(null), "review");
          dir.mkdirs();
          try (OutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
          } catch (IOException e) {
            throw new AssertionError(e);
          } finally {
            bitmap.recycle();
          }
        });
  }

  Button button(View root, String text) {
    if (root instanceof Button && ((Button) root).getText().toString().equals(text))
      return (Button) root;
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int i = 0; i < g.getChildCount(); i++) {
        Button b = button(g.getChildAt(i), text);
        if (b != null) return b;
      }
    }
    return null;
  }

  @Test
  public void dialogFrame() throws Exception {
    assertEquals("http://127.0.0.1:4004", BuildConfig.SERVER_URL);
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
      waitFor(s, a -> !a.busy && a.state.has("me") && a.screen.equals("home"));
      s.onActivity(
          a ->
              a.talk(
                  new JSONArray()
                      .put(
                          MainActivity.obj(
                              "who", "N", "say", "쭈매미 천 마리가 한꺼번에 출근 도장을 찍어요. 도장대가 조금 찌그러졌어요.")),
                  "이야기",
                  () -> a.show("story")));
      shot(s, "story-dialog");
      s.onActivity(
          a -> {
            Button next = button(a.root, "다음");
            assertNotNull(next);
            assertTrue(next.getHeight() >= a.dp(48));
          });
      s.onActivity(a -> a.startFight("practice", "1-6"));
      waitFor(s, a -> a.battle != null && a.screen.equals("battle"));
      s.onActivity(
          a -> {
            JSONObject offer =
                a.list(a.list(a.battle, "teams").optJSONObject(0), "offers").optJSONObject(0);
            a.battleTurn(
                MainActivity.obj("choose", MainActivity.obj("augment", offer.optString("id"))));
          });
      waitFor(s, a -> !a.busy && a.battle.optString("phase").equals("action"));
      Thread.sleep(2000);
      shot(s, "battle-menu");
      s.onActivity(a -> a.battleTurn(MainActivity.obj("leave", true)));
      waitFor(s, a -> !a.busy && a.battle.optString("phase").equals("over"));
    }
  }

  @Test
  public void allScreensAndBattle() throws Exception {
    assertTrue(
        "미리보기 테스트 서버로만 검증",
        (BuildConfig.SERVER_URL.contains("10.0.2.2")
            || BuildConfig.SERVER_URL.equals("http://127.0.0.1:4004")));
    InstrumentationRegistry.getInstrumentation()
        .getTargetContext()
        .getSharedPreferences("native_account", 0)
        .edit()
        .remove("token")
        .apply();
    InstrumentationRegistry.getInstrumentation()
        .getTargetContext()
        .getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .apply();
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
      waitFor(s, a -> a.screen.equals("login") || (!a.busy && a.state.has("me")));
      shot(s, "01-login");
      s.onActivity(
          a -> {
            if (a.api.token().isEmpty())
              a.request(
                  "login",
                  MainActivity.obj("code", "135790"),
                  (r, e) -> {
                    a.api.token(r.optString("token"));
                    a.load("home");
                  });
          });
      waitFor(s, a -> !a.busy && a.state.has("me") && a.screen.equals("home"));
      Thread.sleep(1200);
      shot(s, "02-home");
      for (String screen :
          new String[] {
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
            "tutorial"
          }) {
        s.onActivity(
            a -> {
              if (screen.equals("unit"))
                a.unitDetail(a.list(a.state, "units").optJSONObject(0).optString("key"));
              else if (screen.equals("practice")) a.practice();
              else if (screen.equals("tutorial")) a.tutorial();
              else a.show(screen);
            });
        Thread.sleep(3000);
        shot(s, "screen-" + screen);
        s.onActivity(
            a -> {
              assertEquals(screen, a.screen);
              assertTrue(a.root.getWidth() > 0);
              assertNotNull(a.title);
              assertTrue(a.title.getText().length() > 0);
            });
      }
      s.onActivity(
          a ->
              a.talk(
                  new JSONArray()
                      .put(
                          MainActivity.obj(
                              "who", "N", "say", "쭈매미 천 마리가 한꺼번에 출근 도장을 찍어요. 도장대가 조금 찌그러졌어요.")),
                  "이야기",
                  () -> a.show("story")));
      shot(s, "story-dialog");
      s.onActivity(
          a -> {
            Button next = button(a.root, "다음");
            assertNotNull(next);
            assertTrue(next.getHeight() >= a.dp(48));
            next.performClick();
            assertEquals("story", a.screen);
          });
      java.util.concurrent.atomic.AtomicBoolean cleaned =
          new java.util.concurrent.atomic.AtomicBoolean(false);
      s.onActivity(
          a ->
              a.api.call(
                  "native/battle/current",
                  null,
                  (r, e) -> {
                    if (e == null && r.has("session") && !r.isNull("session"))
                      a.api.call(
                          "native/battle/turn",
                          MainActivity.obj(
                              "session",
                              r.optString("session"),
                              "revision",
                              r.optInt("revision"),
                              "leave",
                              true),
                          (done, error) -> cleaned.set(error == null));
                    else cleaned.set(true);
                  }));
      waitFor(s, a -> cleaned.get());
      s.onActivity(a -> a.startFight("practice", "1-6"));
      waitFor(s, a -> a.battle != null && a.screen.equals("battle"));
      shot(s, "battle-choose");
      s.onActivity(
          a -> {
            JSONObject offer =
                a.list(a.list(a.battle, "teams").optJSONObject(0), "offers").optJSONObject(0);
            a.battleTurn(
                MainActivity.obj("choose", MainActivity.obj("augment", offer.optString("id"))));
          });
      waitFor(s, a -> !a.busy && a.battle.optString("phase").equals("action"));
      Thread.sleep(2200);
      shot(s, "battle-menu");
      s.onActivity(
          a -> {
            for (String text : new String[] {"공격", "아이템", "교체", "도망가기"}) {
              Button b = button(a.root, text);
              assertNotNull(b);
              assertTrue("48dp 터치 높이", b.getHeight() >= a.dp(48));
              assertEquals(text, b.getText().toString());
            }
            button(a.root, "공격").performClick();
          });
      shot(s, "battle-skills");
      s.onActivity(
          a -> {
            BattleView b = (BattleView) a.root.getChildAt(0);
            b.pane = "switch";
            b.render();
          });
      shot(s, "battle-switch");
      s.onActivity(
          a -> {
            BattleView b = (BattleView) a.root.getChildAt(0);
            b.pane = "items";
            b.render();
          });
      shot(s, "battle-items");
      s.onActivity(a -> a.battleTurn(MainActivity.obj("leave", true)));
      waitFor(s, a -> !a.busy && a.battle.optString("phase").equals("over"));
    }
  }
}
