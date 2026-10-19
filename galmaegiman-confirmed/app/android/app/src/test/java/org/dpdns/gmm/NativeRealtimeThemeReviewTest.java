package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Production View rendering, bundled assets/fonts, offline transport, and strict geometry evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRealtimeThemeReviewTest {
  static final int[][] SIZES = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
  static final String[] SCREENS = {
      "queue-connecting", "queue-normal", "queue-rank", "queue-cancelling", "queue-failure",
      "accept-human", "accept-ai", "accept-accepted", "accept-expired",
      "prepare-rank", "prepare-slot", "prepare-page2", "prepare-ready", "prepare-expired", "prepare-ai", "prepare-empty",
      "augment", "augment-selected", "augment-rerolls-spent", "augment-next-fighter", "augment-switch-only", "augment-expired", "augment-long-effects",
      "result-pending", "result-win", "result-loss", "result-draw", "result-abort", "result-ai", "result-promoted", "result-promotion-failed"};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRealtimeThemeStateTest fixture = new NativeRealtimeThemeStateTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final JSONObject findings = new JSONObject(), metrics = new JSONObject();
  static final long ASSET_TIMEOUT_MS = 10000, ASSET_WAIT_BUDGET_MS = 120000;
  long assetWaitMillis;

  @Before public void isolate() {
    fixture.isolate(); review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  long awaitAssets(MainActivity h) throws InterruptedException {
    long started = System.nanoTime();
    try {
      while (true) {
        // Drain only callbacks that are due now. Asset decoding must not consume
        // simulated battle-deadline seconds or add a fixed sleep to every fixture.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        if (h.art.idle()) return (System.nanoTime() - started) / 1000000L;
        long elapsed = (System.nanoTime() - started) / 1000000L;
        assertTrue("Bundled asset wait exceeded 10 seconds", elapsed < ASSET_TIMEOUT_MS);
        assertTrue("Combined asset-wait budget exceeded two minutes", assetWaitMillis + elapsed < ASSET_WAIT_BUDGET_MS);
        Thread.sleep(2);
      }
    } finally { assetWaitMillis += (System.nanoTime() - started) / 1000000L; }
  }

  void layout(MainActivity h, int width, int height) {
    review.viewport(h, width, height);
    ViewCompat.setOnApplyWindowInsetsListener(h.root, null);
    h.root.setPadding(width == 1006 ? 36 : 0, width == 1006 ? 10 : 0,
        width == 1006 ? 10 : 0, width == 1006 ? 10 : 0);
    Runnable measure = () -> {
      review.force(h.root);
      h.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      h.root.layout(0, 0, width, height);
    };
    measure.run(); review.preparePreDraw(h.root, measure);
  }

  JSONObject preparation() throws Exception {
    JSONArray available = new JSONArray(); NativeRegistrationReviewTest catalog = new NativeRegistrationReviewTest();
    for (String id : NativeRegistrationReviewTest.IDS) {
      JSONObject species = catalog.species(id);
      available.put(MainActivity.obj("id", id, "name", species.getString("name"), "level", 30,
          "rarity", species.getString("tierName")));
    }
    return MainActivity.obj("selectedIds", new JSONArray().put("D1").put("D2").put("D3"),
        "available", available, "ready", false, "opponentReady", false);
  }

  JSONObject augmentBattle() throws Exception {
    // Canonical battle.yaml at 2d96f22, Git blob adf073849b998288873be2d7ef2c31acd7f4a0ea.
    // Do not inherit the synthetic parameter-transport fixture from the state tests.
    JSONObject b = new JSONObject(new R1ScreenReviewTest().battle().toString());
    b.put("phase", "choose").put("serverNow", System.currentTimeMillis())
        .put("chooseDeadline", System.currentTimeMillis() + 30000);
    b.getJSONArray("teams").getJSONObject(0).put("offers", new JSONArray()
        .put(MainActivity.obj("id", "thick", "name", "두꺼운 깃털", "desc", "아군 물리·마법 방어 +15%", "p", MainActivity.obj("pct", 15)))
        .put(MainActivity.obj("id", "dual", "name", "이중 속성", "desc", "기본기·역할 공격에 상대 약점 속성 추가", "rerolled", true))
        .put(MainActivity.obj("id", "neutral", "name", "무속성 지대", "desc", "3턴 동안 양쪽 모든 상성 ×1.0", "p", MainActivity.obj("turns", 3))));
    NativeRegistrationReviewTest catalog = new NativeRegistrationReviewTest();
    JSONArray own = b.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters");
    for (int i = 0; i < own.length(); i++) {
      String id = "D" + (i + 1); JSONObject unit = own.getJSONObject(i);
      unit.put("id", id).put("name", catalog.species(id).getString("name"))
          .put("image", "/duel/sprites/" + id + "_s0_front.webp");
    }
    return b;
  }

  View show(MainActivity h, String screen, int width, int height) throws Exception {
    layout(h, width, height);
    if (screen.startsWith("queue-")) {
      h.realtime.room = ""; h.realtime.mode = screen.equals("queue-rank") ? "rank" : "normal";
      h.realtime.queueConfirmed = !screen.equals("queue-connecting");
      h.realtime.queueWaitBase = h.realtime.queueConfirmed ? 65000 : -1;
      h.realtime.queueWaitReceivedAt = android.os.SystemClock.elapsedRealtime();
      h.realtime.cancelling = screen.equals("queue-cancelling");
      h.realtime.showQueue(screen.equals("queue-connecting") ? "진행 중인 매칭 확인 중…" : "매칭 취소 확인 중…");
      if (screen.equals("queue-failure")) {
        h.realtime.queueRetryCount = 3;
        h.realtime.queueFailure("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", () -> {});
      }
      h.realtime.tick.run(); return h.root;
    }
    if (screen.startsWith("accept-")) {
      JSONObject r = fixture.envelope("accept"); r.put("mode", "normal");
      if (screen.equals("accept-ai")) r.put("opponentKind", "AI");
      if (screen.equals("accept-accepted")) r.getJSONObject("acceptance").put("accepted", true);
      if (screen.equals("accept-expired")) r.put("deadline", r.getLong("serverNow") - 1);
      h.realtime.acceptRoom(r); h.realtime.tick.run(); return h.root;
    }
    if (screen.startsWith("prepare-")) {
      JSONObject r = fixture.envelope("prepare"); r.put("preparation", preparation());
      if (screen.equals("prepare-ai")) r.put("mode", "normal").put("opponentKind", "AI");
      if (screen.equals("prepare-ready")) r.getJSONObject("preparation").put("ready", true).put("opponentReady", true);
      if (screen.equals("prepare-expired")) r.put("deadline", r.getLong("serverNow") - 1);
      if (screen.equals("prepare-empty")) r.getJSONObject("preparation").put("selectedIds", new JSONArray()).put("available", new JSONArray());
      h.realtime.acceptRoom(r);
      if (screen.equals("prepare-slot")) h.root.findViewWithTag("realtime-prepare-slot-2").performClick();
      if (screen.equals("prepare-page2")) h.root.findViewWithTag("realtime-prepare-next").performClick();
      h.realtime.tick.run(); return h.root;
    }
    if (screen.startsWith("augment")) {
      JSONObject b = augmentBattle(); JSONObject own = b.getJSONArray("teams").getJSONObject(0);
      if (screen.equals("augment-rerolls-spent"))
        for (int i = 0; i < own.getJSONArray("offers").length(); i++) own.getJSONArray("offers").getJSONObject(i).put("rerolled", true);
      if (screen.equals("augment-next-fighter")) own.put("needSwitch", true);
      if (screen.equals("augment-switch-only")) own.put("needSwitch", true).put("offers", new JSONArray());
      if (screen.equals("augment-expired")) b.put("chooseDeadline", System.currentTimeMillis() - 1);
      if (screen.equals("augment-long-effects")) own.put("offers", new JSONArray()
          .put(MainActivity.obj("id", "strip", "name", "기세 꺾기", "desc", "상대 선두 강화 능력치·보호막 제거. 공격 -20%, 2턴", "p", MainActivity.obj("pct", 20, "turns", 2)))
          .put(MainActivity.obj("id", "learn", "name", "상성 학습", "desc", "동일 속성 피격마다 피해 -15%, 최대 -45%", "p", MainActivity.obj("step", 15, "max", 45)))
          .put(MainActivity.obj("id", "counter", "name", "역습 태세", "desc", "약한 상성 공격을 맞고 생존 시 위력 40 반격", "p", MainActivity.obj("power", 40))));
      h.battle = b; h.battleScreen();
      NativeAugmentView choice = ((BattleView) h.root.getChildAt(0)).augmentView;
      assertNotNull(choice);
      if (screen.equals("augment-selected") || screen.equals("augment-next-fighter")) choice.picks.get(0).performClick();
      if (screen.equals("augment-next-fighter")) { choice.confirm.performClick(); choice.picks.get(1).performClick(); }
      if (screen.equals("augment-switch-only")) choice.picks.get(0).performClick();
      choice.tick.run(); return choice;
    }
    JSONObject r = fixture.envelope("over"); r.put("mode", "normal");
    JSONObject result = MainActivity.obj("settlement", screen.equals("result-pending") ? "pending" : "complete",
        "winner", screen.equals("result-draw") || screen.equals("result-abort") ? JSONObject.NULL : screen.equals("result-loss") ? 1 : 0);
    if (screen.equals("result-abort")) result.put("reason", "SERVER_ABORT");
    if (screen.equals("result-ai")) r.put("opponentKind", "AI");
    if (screen.equals("result-promoted") || screen.equals("result-promotion-failed")) {
      r.put("mode", "rank");
      result.put("rank", MainActivity.obj("tier", "골드", "promotionChange", screen.equals("result-promoted") ? "PROMOTED" : "FAILED",
          "after", MainActivity.obj("lp", 24, "promotionActive", true, "promotionWins", 2, "promotionLosses", 1)));
    }
    r.put("result", result); h.realtime.acceptRoom(r); h.realtime.tick.run();
    return h.visibleDialog == null ? h.root : geometry.layoutDialog(h);
  }

  String scene(String screen) {
    return AppSkin.background(screen.startsWith("augment") ? "workshop" : "training");
  }

  JSONObject inspect(MainActivity h, View target, String screen, NativeRealtimeTest.FakeApi api) throws Exception {
    int texts = 0, actions = 0, checkedBounds = 0;
    List<View> controls = new ArrayList<>();
    for (View view : geometry.descendants(target)) {
      if (!view.isShown()) continue;
      if (!screen.startsWith("result-"))
        assertFalse("Core selection must not scroll: " + screen, view instanceof ScrollView || view instanceof HorizontalScrollView || view instanceof ListView);
      if (view instanceof TextView && ((TextView) view).length() > 0) {
        geometry.readable((TextView) view); texts++;
      }
      if (view.isClickable() && view.getWidth() > 0) {
        assertTrue("48dp action width: " + view.getTag(), view.getWidth() >= h.dp(48));
        assertTrue("48dp action height: " + view.getTag(), view.getHeight() >= h.dp(48));
        assertEquals("Action clipped: " + view.getTag(), geometry.bounds(view, target), geometry.visibleIntersection(view, target));
        controls.add(view); checkedBounds++; actions++;
      }
    }
    for (int i = 0; i < controls.size(); i++) for (int j = i + 1; j < controls.size(); j++) {
      if (isAncestor(controls.get(i), controls.get(j)) || isAncestor(controls.get(j), controls.get(i))) continue;
      assertFalse("Action overlap: " + controls.get(i).getTag() + "/" + controls.get(j).getTag(),
          Rect.intersects(geometry.bounds(controls.get(i), target), geometry.bounds(controls.get(j), target)));
      checkedBounds++;
    }
    if (screen.startsWith("augment")) {
      assertNotNull(target.findViewWithTag("theme-augment-desk"));
      NativeAugmentView choice = (NativeAugmentView) target;
      assertNull(choice.timerRule.getParent()); assertNull(choice.hint.getParent());
      if (!choice.switching) {
        for (int i = 0; i < choice.offers.length(); i++) {
          JSONObject offer = choice.offers.getJSONObject(i); View card = choice.picks.get(i);
          TextView effect = card.findViewWithTag("augment-core-effect"); assertNotNull(effect);
          assertEquals(NativeAugmentView.coreEffect(offer), effect.getText().toString());
          assertEquals(geometry.bounds(effect, target), geometry.visibleIntersection(effect, target));
          View icon = card.findViewWithTag("augment-icon-" + offer.getString("id"));
          assertTrue(icon.getWidth() >= h.dp(56)); assertTrue(icon.getHeight() >= h.dp(56));
          checkedBounds += 2;
        }
      } else {
        // These are the actual bundled front sprites used by the production ImageViews.
        assertNotNull(h.art.peek("/duel/sprites/D1_s0_front.webp"));
        assertNotNull(h.art.peek("/duel/sprites/D2_s0_front.webp"));
      }
    } else assertNotNull(h.root.findViewWithTag("theme-scene-background"));
    Bitmap background = ThemeArt.of(h).image(scene(screen));
    assertNotNull("Missing real scene asset", background);
    assertTrue(background.getWidth() >= 1800 && background.getHeight() >= 800);
    for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++)
      assertEquals("Scene cannot reveal underlying battle", 255,
          Color.alpha(background.getPixel(x * (background.getWidth() - 1) / 4, y * (background.getHeight() - 1) / 4)));
    for (NativeRealtimeTest.Request request : api.requests)
      assertNull("A render fixture must not send an API mutation: " + request.path, request.body);
    try (InputStream jua = h.getAssets().open("native/Jua-Regular.ttf");
         InputStream prose = h.getAssets().open("native/GowunDodum-Regular.ttf")) {
      assertTrue(jua.read() >= 0); assertTrue(prose.read() >= 0);
    }
    assertTrue(texts > 0);
    return MainActivity.obj("actualNativeViews", true, "sceneAsset", scene(screen), "sceneWidth", background.getWidth(),
        "sceneHeight", background.getHeight(), "checkedBounds", checkedBounds, "actions", actions, "text", texts,
        "apiWrites", 0, "apiReads", api.requests.size(),
        "rootKind", h.visibleDialog != null ? "dialog" : screen.startsWith("augment") ? "overlay" : "page");
  }

  static boolean isAncestor(View parent, View child) {
    for (ViewParent node = child.getParent(); node instanceof View; node = node.getParent()) if (node == parent) return true;
    return false;
  }

  Path output() throws IOException {
    Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "realtime-theme");
    Files.createDirectories(path); return path;
  }

  void capture(MainActivity h, View target, String name, int width, int height) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); h.root.draw(canvas);
    if (h.visibleDialog != null) {
      canvas.drawColor(0x66000000); RectF safe = h.safeBounds(width, height);
      int save = canvas.save(); canvas.translate(safe.centerX() - target.getWidth() / 2f, safe.centerY() - target.getHeight() / 2f);
      target.draw(canvas); canvas.restoreToCount(save);
    }
    try (OutputStream stream = Files.newOutputStream(output().resolve(name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { bitmap.recycle(); }
  }

  @Test public void captureRealtimeAndAugmentSurfaces() throws Exception {
    List<String> failures = new ArrayList<>();
    for (int[] size : SIZES) for (String screen : SCREENS) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = screen + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      JSONArray problems = new JSONArray(); findings.put(name, problems);
      long started = System.nanoTime();
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity h = controller.get(); NativeRealtimeTest.FakeApi api = fixture.install(h);
        View target = null;
        try {
          target = show(h, screen, size[0], size[1]);
          long waited = awaitAssets(h);
          layout(h, size[0], size[1]);
          if (h.visibleDialog != null) target = geometry.layoutDialog(h);
          capture(h, target, name, size[0], size[1]);
          metrics.put(name, inspect(h, target, screen, api)
              .put("assetWaitMillis", waited).put("renderMillis", (System.nanoTime() - started) / 1000000L));
        } catch (Throwable failure) {
          problems.put(failure.toString()); failures.add(name + ": " + failure);
          if (target != null) capture(h, target, name, size[0], size[1]);
        } finally {
          h.realtime.pause();
          if (h.visibleDialog != null) h.visibleDialog.dismiss();
        }
      } catch (Throwable failure) { problems.put(failure.toString()); failures.add(name + ": " + failure); }
      Files.writeString(output().resolve("findings.json"), findings.toString(2), StandardCharsets.UTF_8);
      Files.writeString(output().resolve("metrics.json"), metrics.toString(2), StandardCharsets.UTF_8);
    }
    assertEquals(SCREENS.length * SIZES.length, findings.length());
    assertTrue(String.join("\n", failures), failures.isEmpty());
    assertEquals(SCREENS.length * SIZES.length, metrics.length());
  }
}
