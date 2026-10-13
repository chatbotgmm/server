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

  void enterHome(ActivityScenario<MainActivity> s) throws Exception {
    waitFor(
        s,
        a ->
            !a.busy
                && (a.screen.equals("title")
                    || a.screen.equals("login")
                    || a.screen.equals("home")));
    s.onActivity(
        a -> {
          if (a.screen.equals("title") && a.api.token().isEmpty())
            button(a.root, "로그인").performClick();
        });
    s.onActivity(
        a -> {
          if (a.screen.equals("login"))
            a.request(
                "login",
                MainActivity.obj("code", "135790"),
                (r, e) -> {
                  a.api.token(r.optString("token"));
                  a.load("title");
                });
        });
    waitFor(
        s,
        a -> !a.busy && a.state.has("me") && (a.screen.equals("title") || a.screen.equals("home")));
    if (!s.getState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) fail("액티비티 준비 실패");
    shot(s, "02-title-in");
    s.onActivity(
        a -> {
          if (a.screen.equals("title")) button(a.root, "게임 시작").performClick();
        });
    waitFor(s, a -> !a.busy && a.screen.equals("home"));
  }

  @Test
  public void lobbyAndPopups() throws Exception {
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
      enterHome(s);
      waitFor(
          s,
          a -> {
            if (!(a.root.getChildAt(0) instanceof LobbyView)) return false;
            LobbyView lobby = (LobbyView) a.root.getChildAt(0);
            for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"})
              if (a.art.peek("/duel/bg/" + lobby.room + layer) == null) return false;
            return a.art.peek("/duel/icons/btn-play.png") != null;
          });
      shot(s, "lobby-restored");
      s.onActivity(
          a -> {
            LobbyView lobby = (LobbyView) a.root.getChildAt(0);
            for (int i = 0; i < lobby.buttons.size(); i++) {
              Button b = lobby.buttons.get(i);
              assertTrue(b.getHeight() >= a.dp(48));
              assertTrue(b.getLeft() >= 0 && b.getRight() <= lobby.getWidth());
              assertTrue(b.getTop() >= 0 && b.getBottom() <= lobby.getHeight());
              for (int j = 0; j < i; j++) {
                Button other = lobby.buttons.get(j);
                assertFalse(
                    "로비 버튼 터치 영역 겹침",
                    android.graphics.Rect.intersects(
                        new android.graphics.Rect(
                            b.getLeft(), b.getTop(), b.getRight(), b.getBottom()),
                        new android.graphics.Rect(
                            other.getLeft(), other.getTop(), other.getRight(), other.getBottom())));
              }
            }
          });
      AtomicReference<android.app.AlertDialog> dialog = new AtomicReference<>();
      s.onActivity(
          a ->
              dialog.set(
                  new GameDialog(a)
                      .setMessage("게임을 닫을까요?")
                      .setNegativeButton("취소", null)
                      .setPositiveButton("확인", null)
                      .show()));
      dialogShot(s, dialog, "popup-exit");
      s.onActivity(
          a -> dialog.get().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick());
      s.onActivity(
          a ->
              dialog.set(
                  new GameDialog(a)
                      .setTitle("플레이")
                      .setItems(new String[] {"스토리", "대전", "연습"}, (d, which) -> a.load("story"))
                      .setNegativeButton("닫기", null)
                      .show()));
      dialogShot(s, dialog, "popup-play");
      s.onActivity(
          a -> {
            android.app.AlertDialog d = dialog.get();
            d.getListView().performItemClick(d.getListView().getChildAt(0), 0, 0);
          });
      waitFor(s, a -> !a.busy && a.screen.equals("story"));
    }
  }

  void dialogShot(
      ActivityScenario<MainActivity> s, AtomicReference<android.app.AlertDialog> ref, String name)
      throws Exception {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    Thread.sleep(300);
    s.onActivity(
        a -> {
          View v = ref.get().getWindow().getDecorView();
          assertTrue(v.getWidth() <= a.root.getWidth());
          assertTrue(v.getHeight() <= a.root.getHeight());
          Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
          v.draw(new android.graphics.Canvas(bitmap));
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

  @Test
  public void dialogFrame() throws Exception {
    assertEquals("http://127.0.0.1:4004", BuildConfig.SERVER_URL);
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
      enterHome(s);
      s.onActivity(
          a -> {
            a.storyScene = "deep";
            a.talk(
                new JSONArray()
                    .put(
                        MainActivity.obj(
                            "who", "N", "say", "쭈매미 천 마리가 한꺼번에 출근 도장을 찍어요. 도장대가 조금 찌그러졌어요."))
                    .put(MainActivity.obj("who", "V1", "say", "항구를 지켜라!")),
                "이야기",
                () -> a.show("story"));
          });
      waitFor(
          s,
          a ->
              a.root.getChildAt(0) instanceof StoryView
                  && ((StoryView) a.root.getChildAt(0)).portraitsReady());
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
      waitFor(s, a -> a.screen.equals("title") && !a.busy);
      shot(s, "00-title-out");
      s.onActivity(a -> button(a.root, "로그인").performClick());
      shot(s, "01-login");
      enterHome(s);
      Thread.sleep(1200);
      shot(s, "02-home");
      // 늦은 상태 응답이 이미 이동한 화면을 덮어쓰지 않는다.
      s.onActivity(
          a -> {
            a.load("home");
            a.chapters();
          });
      waitFor(s, a -> !a.busy);
      s.onActivity(a -> assertEquals("story", a.screen));
      s.onActivity(a -> a.load("home"));
      waitFor(s, a -> !a.busy && a.screen.equals("home"));
      // 보호된 수비대도 탐험 선택이 가능하며 뒤로 이동할 때 편성을 저장한다.
      s.onActivity(
          a ->
              a.request(
                  "party",
                  MainActivity.obj("ids", new JSONArray()),
                  (r, e) -> a.load("expedition")));
      waitFor(s, a -> !a.busy && a.screen.equals("expedition"));
      s.onActivity(
          a -> {
            button(a.root, "＋").performClick();
            assertEquals(1, a.expeditionDraft.size());
            a.back();
          });
      waitFor(s, a -> !a.busy && a.screen.equals("home"));
      s.onActivity(a -> assertEquals(1, a.list(a.me(), "party").length()));
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
          a -> {
            a.storyScene = "deep";
            a.talk(
                new JSONArray()
                    .put(
                        MainActivity.obj(
                            "who", "N", "say", "쭈매미 천 마리가 한꺼번에 출근 도장을 찍어요. 도장대가 조금 찌그러졌어요."))
                    .put(MainActivity.obj("who", "V1", "say", "항구를 지켜라!")),
                "이야기",
                () -> a.show("story"));
          });
      waitFor(
          s,
          a ->
              a.root.getChildAt(0) instanceof StoryView
                  && ((StoryView) a.root.getChildAt(0)).portraitsReady());
      shot(s, "story-dialog");
      s.onActivity(
          a -> {
            Button next = button(a.root, "다음");
            assertNotNull(next);
            assertTrue(next.getHeight() >= a.dp(48));
            while (a.screen.equals("dialog")) button(a.root, "다음").performClick();
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
