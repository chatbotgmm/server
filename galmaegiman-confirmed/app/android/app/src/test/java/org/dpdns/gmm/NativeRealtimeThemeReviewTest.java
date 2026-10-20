package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  final JSONObject findings = new JSONObject(), metrics = new JSONObject(), layoutDiagnostics = new JSONObject();
  static final long ASSET_TIMEOUT_MS = 10000, ASSET_WAIT_BUDGET_MS = 120000;
  long assetWaitMillis;

  @Before public void isolate() {
    fixture.isolate(); review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  long awaitAssets(MainActivity h) throws Exception {
    long started = System.nanoTime(), advancedMillis = 0;
    try {
      while (true) {
        // A ViewRoot traversal sync barrier can hold NativeArt's ordinary Handler
        // callbacks until the next Choreographer frame. idle() alone cannot release it.
        // Pace virtual frames by wall waiting time: a tight decoder poll must never
        // burn through the server's 30-second input phase.
        long elapsed = (System.nanoTime() - started) / 1000000L;
        if (advancedMillis <= elapsed) {
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
          advancedMillis += 16;
        } else Shadows.shadowOf(Looper.getMainLooper()).idle();
        if (assetsReady(h)) {
          Shadows.shadowOf(Looper.getMainLooper()).idle();
          return (System.nanoTime() - started) / 1000000L;
        }
        assertTrue("Bundled asset wait exceeded 10 seconds; pending=" + pendingAssets(h), elapsed < ASSET_TIMEOUT_MS);
        assertTrue("Combined asset-wait budget exceeded two minutes; pending=" + pendingAssets(h),
            assetWaitMillis + elapsed < ASSET_WAIT_BUDGET_MS);
        Thread.sleep(2);
      }
    } finally { assetWaitMillis += (System.nanoTime() - started) / 1000000L; }
  }

  boolean assetsReady(MainActivity h) {
    if (!h.art.idle()) return false;
    if (!"realtimePrepare".equals(h.screen)) return true;
    if (!h.registrationArt().idle()) return false;
    for (View view : geometry.descendants(h.root)) {
      if (!(view instanceof PartyCharacterView.Artwork) || !view.isShown()) continue;
      PartyCharacterView.Artwork art = (PartyCharacterView.Artwork) view;
      if (art.path.isEmpty()) {
        assertNull(art.bitmap); assertEquals("이미지 없음", art.status); continue;
      }
      assertFalse("Missing bundled preparation sprite: " + art.id, "이미지 없음".equals(art.status));
      if (art.bitmap == null || !art.status.isEmpty()) return false;
    }
    return true;
  }

  String pendingAssets(MainActivity h) throws Exception {
    java.lang.reflect.Field field = NativeArt.class.getDeclaredField("loading");
    field.setAccessible(true);
    Set<?> pending = (Set<?>) field.get(h.art);
    synchronized (pending) {
      return pending.toString() + ("realtimePrepare".equals(h.screen)
          ? "; registration=" + h.registrationArt().pendingCount() : "");
    }
  }

  void assertBundledAssets(MainActivity h, String screen) throws IOException {
    List<String> paths = new ArrayList<>();
    paths.add("native/release-v1/" + scene(screen));
    if (screen.startsWith("augment"))
      for (int i = 1; i <= 3; i++) paths.add("game-art/duel/sprites/D" + i + "_s0_front.webp");
    for (String path : paths) {
      BitmapFactory.Options dimensions = new BitmapFactory.Options();
      dimensions.inJustDecodeBounds = true;
      try (InputStream input = h.getAssets().open(path)) {
        BitmapFactory.decodeStream(input, null, dimensions);
      }
      assertTrue("Bundled fixture image must decode: " + path,
          dimensions.outWidth > 0 && dimensions.outHeight > 0);
    }
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
      assertTrue("Augment fixture must be attached", choice.isAttachedToWindow());
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

  JSONObject inspectPreparationStatus(MainActivity h, View root) throws Exception {
    TextView status = root.findViewWithTag("realtime-prepare-status"); assertNotNull(status);
    geometry.readable(status);
    assertEquals(geometry.bounds(status, root), geometry.visibleIntersection(status, root));
    android.text.Layout text = status.getLayout(); assertNotNull(text);
    int width = status.getWidth() - status.getCompoundPaddingLeft() - status.getCompoundPaddingRight();
    int height = status.getHeight() - status.getCompoundPaddingTop() - status.getCompoundPaddingBottom();
    assertTrue("Complete status uses at most two lines", text.getLineCount() >= 1 && text.getLineCount() <= 2);
    assertTrue("Status text height fits its fixed header", text.getHeight() <= height);
    assertEquals("The whole status is laid out", status.length(), text.getLineEnd(text.getLineCount() - 1));
    JSONArray lines = new JSONArray();
    for (int i = 0; i < text.getLineCount(); i++) {
      assertEquals("Status cannot be ellipsized", 0, text.getEllipsisCount(i));
      assertTrue("Status starts inside its content width", text.getLineLeft(i) >= -.01f);
      assertTrue("Status ends inside its content width", text.getLineRight(i) <= width + .01f);
      lines.put(MainActivity.obj("left", text.getLineLeft(i), "right", text.getLineRight(i)));
    }
    if (h.realtime.isAiRoom(h.realtime.latest)) assertEquals(NativeRealtime.AI_NOTICE, status.getText().toString());
    return MainActivity.obj("text", status.getText().toString(), "lines", lines,
        "contentWidth", width, "contentHeight", height, "layoutHeight", text.getHeight(),
        "textLength", status.length(), "laidOutTextEnd", text.getLineEnd(text.getLineCount() - 1));
  }

  JSONArray inspectPreparationCards(MainActivity h, View root) throws Exception {
    JSONObject preparation = h.realtime.latest.getJSONObject("preparation");
    JSONArray selected = preparation.getJSONArray("selectedIds"), available = preparation.getJSONArray("available");
    Map<String, String> expected = new LinkedHashMap<>();
    for (int i = 0; i < selected.length(); i++) expected.put("realtime-prepare-slot-" + i, selected.getString(i));
    for (int i = h.realtime.preparationPage * NativeRealtime.PREPARATION_PAGE_SIZE;
        i < Math.min(available.length(), (h.realtime.preparationPage + 1) * NativeRealtime.PREPARATION_PAGE_SIZE); i++)
      expected.put("realtime-prepare-candidate-" + i, available.getJSONObject(i).getString("id"));
    JSONArray cards = new JSONArray();
    for (View view : geometry.descendants(root)) {
      assertFalse("Preparation cannot use a registration frame", view instanceof RegistrationCardView);
      if (!(view instanceof PartyCharacterView) || !view.isShown()) continue;
      PartyCharacterView card = (PartyCharacterView) view;
      PartyCharacterView.Artwork art = card.picture;
      String tag = String.valueOf(card.getTag()), id = expected.remove(tag);
      assertNotNull("Only server-authoritative slots/candidates are rendered: " + tag, id);
      assertEquals(id, art.id);
      new NativeRegistrationReviewTest().assertPartyArt(h, art);
      assertEquals(geometry.bounds(art, root), geometry.visibleIntersection(art, root));
      JSONObject canonical = NativeRealtime.preparationCandidate(available, id);
      assertEquals("Visible name comes from the server", canonical.getString("name"), card.name.getText().toString());
      geometry.readable(card.name);
      assertEquals(geometry.bounds(card.name, root), geometry.visibleIntersection(card.name, root));
      assertEquals("Masking is never bypassed", PartyCharacterView.spritePath(canonical), art.path);
      TextView state = card.findViewWithTag(tag + "-state");
      JSONObject candidate = NativeRealtime.preparationCandidate(available, id);
      assertTrue(state.getText().toString().contains("Lv" + candidate.getInt("level")));
      android.view.accessibility.AccessibilityNodeInfo node = card.createAccessibilityNodeInfo();
      assertTrue(node.getContentDescription().toString().contains(candidate.getString("name")));
      assertTrue(node.getContentDescription().toString().contains(state.getText()));
      assertEquals(card.isEnabled(), node.isEnabled());
      cards.put(MainActivity.obj("tag", tag, "id", id, "mode", "CHARACTER",
          "spritePath", art.path, "imageStatus", art.status,
          "pixelIdentityVerified", art.bitmap != null, "missingIdentityPreserved", art.path.isEmpty(),
          "nameMatchesServer", true, "registrationViews", 0, "targetContained", true,
          "containedWidth", art.target.width(), "containedHeight", art.target.height(),
          "bitmapWidth", art.bitmap == null ? 0 : art.bitmap.getWidth(),
          "bitmapHeight", art.bitmap == null ? 0 : art.bitmap.getHeight(),
          "enabled", card.isEnabled(), "dynamicState", state.getText().toString()));
    }
    assertTrue("Missing server preparation cards: " + expected, expected.isEmpty());
    assertEquals("Empty state or three slots plus three current candidates", available.length() == 0 ? 0 : 6, cards.length());
    return cards;
  }

  JSONObject inspect(MainActivity h, View target, String screen, NativeRealtimeTest.FakeApi api) throws Exception {
    assertTrue("Capture target must be attached: " + screen, target.isAttachedToWindow());
    assertTrue("Capture target must be shown: " + screen, target.isShown());
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
        assertEquals("Action clipped: " + view.getClass().getSimpleName() + "/" + view.getTag(), geometry.bounds(view, target), geometry.visibleIntersection(view, target));
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
          assertEquals(NativeAugmentView.coreEffect(offer).replace('\n', ' '), effect.getText().toString().replace('\n', ' '));
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
    JSONObject result = MainActivity.obj("actualNativeViews", true, "sceneAsset", scene(screen), "sceneWidth", background.getWidth(),
        "sceneHeight", background.getHeight(), "checkedBounds", checkedBounds, "actions", actions, "text", texts,
        "apiWrites", 0, "apiReads", api.requests.size(),
        "rootKind", h.visibleDialog != null ? "dialog" : screen.startsWith("augment") ? "overlay" : "page");
    if (screen.startsWith("prepare-")) result.put("preparationCards", inspectPreparationCards(h, target))
        .put("preparationStatus", inspectPreparationStatus(h, target));
    return result;
  }

  JSONObject geometryNode(View view, View target) throws JSONException {
    JSONObject node = MainActivity.obj("class", view.getClass().getName(), "id", view.getId(),
        "tag", String.valueOf(view.getTag()), "text", view instanceof TextView ? ((TextView) view).getText().toString() : "",
        "bounds", geometry.bounds(view, target).toShortString(),
        "visible", geometry.visibleIntersection(view, target).toShortString(),
        "scrollX", view.getScrollX(), "scrollY", view.getScrollY(),
        "measuredWidth", view.getMeasuredWidth(), "measuredHeight", view.getMeasuredHeight(),
        "padding", new JSONArray().put(view.getPaddingLeft()).put(view.getPaddingTop())
            .put(view.getPaddingRight()).put(view.getPaddingBottom()),
        "layoutRequested", view.isLayoutRequested(), "shown", view.isShown(), "attached", view.isAttachedToWindow());
    if (view instanceof ViewGroup) node.put("clipChildren", ((ViewGroup) view).getClipChildren())
        .put("clipToPadding", ((ViewGroup) view).getClipToPadding());
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
      node.put("margins", new JSONArray().put(margins.leftMargin).put(margins.topMargin)
          .put(margins.rightMargin).put(margins.bottomMargin));
    }
    return node;
  }

  JSONArray actionGeometry(View target) throws JSONException {
    JSONArray actions = new JSONArray();
    for (View view : geometry.descendants(target)) {
      if (!view.isShown() || !view.isClickable() || view.getWidth() <= 0) continue;
      JSONObject action = geometryNode(view, target); JSONArray ancestors = new JSONArray();
      for (ViewParent node = view.getParent(); node instanceof View; node = node.getParent()) {
        ancestors.put(geometryNode((View) node, target));
        if (node == target) break;
      }
      actions.put(action.put("ancestors", ancestors));
    }
    return actions;
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
          assertBundledAssets(h, screen);
          target = show(h, screen, size[0], size[1]);
          // Mount the new tree and release its first frame's traversal barrier before
          // waiting for the real asynchronous asset completions.
          layout(h, size[0], size[1]);
          long waited = awaitAssets(h);
          layout(h, size[0], size[1]);
          if (h.visibleDialog != null) target = geometry.layoutDialog(h);
          JSONObject drawing = null;
          if (h.visibleDialog != null) {
            drawing = MainActivity.obj("beforeDraw", actionGeometry(target));
            layoutDiagnostics.put(name, drawing);
          }
          capture(h, target, name, size[0], size[1]);
          if (drawing != null) drawing.put("afterDraw", actionGeometry(target));
          metrics.put(name, inspect(h, target, screen, api)
              .put("assetWaitMillis", waited).put("renderMillis", (System.nanoTime() - started) / 1000000L));
        } catch (Throwable failure) {
          problems.put(failure.toString()); failures.add(name + ": " + failure);
          if (target != null) {
            // Failure evidence must still contain the real mounted tree, not an
            // unmeasured root with only its solid fallback background.
            layout(h, size[0], size[1]);
            if (h.visibleDialog != null) target = geometry.layoutDialog(h);
            capture(h, target, name, size[0], size[1]);
          }
        } finally {
          h.realtime.pause();
          if (h.visibleDialog != null) h.visibleDialog.dismiss();
        }
      } catch (Throwable failure) { problems.put(failure.toString()); failures.add(name + ": " + failure); }
      Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output().resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output().resolve("layout-diagnostics.json"), layoutDiagnostics.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    assertEquals(SCREENS.length * SIZES.length, findings.length());
    assertTrue(String.join("\n", failures), failures.isEmpty());
    assertEquals(SCREENS.length * SIZES.length, metrics.length());
  }
}
