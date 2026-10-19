package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.OutputStream;
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

/** 실제 Android View·글꼴·번들 이미지를 검사한다. 생성 시안을 캡처로 대신하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeThemeReviewTest {
  static final String[] PAGES = {"settings-sound", "settings-alerts", "settings-account",
      "settings-probability", "profile", "modes", "shop", "explore"};
  static final int[][] SIZES = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final JSONObject findings = new JSONObject(), metrics = new JSONObject();

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  static JSONObject rank() {
    return MainActivity.obj("me", MainActivity.obj("tier", "골드", "point", 1250,
        "wins", 12, "losses", 8, "rank", 24), "level", 30, "win", 20, "lose", 15, "team", true);
  }

  /** webDaily() 스키마 + economy.yaml@9d8d7b4. 진행 값만 오프라인 첫날 상태다. */
  static JSONObject daily() {
    JSONArray missions = new JSONArray();
    String[][] definitions = {{"DRAW", "뽑기 5회", "5", "20"},
        {"CRAFT", "조합 3회", "3", "20"}, {"CRAFT_RARE", "희귀 조합하기", "1", "30"},
        {"CRAFT_LEGEND", "전설 또는 히든 조합하기", "1", "50"},
        {"CRAFT_TOP", "최상위 유닛 조합하기", "1", "100"},
        {"EXPEDITION", "탐험 다녀오기 1회", "1", "10"}};
    for (String[] definition : definitions) missions.put(MainActivity.obj(
        "key", definition[0], "label", definition[1], "goal", Integer.parseInt(definition[2]),
        "tickets", Integer.parseInt(definition[3]), "value", 0, "done", false));
    JSONArray rewards = new JSONArray();
    for (int amount : new int[] {8, 10, 12, 14, 16, 18, 40}) rewards.put(amount);
    return MainActivity.obj("day", "2026-10-19", "missions", missions, "attend", MainActivity.obj(
        "today", false, "streak", 0, "week", 1, "dayInWeek", 1, "rewards", rewards, "next", 8));
  }

  static final class ReviewApi extends NativeApi {
    final JSONObject me;
    int writes, reads, drawReads, homeDrawReads;
    ReviewApi(MainActivity host, JSONObject me) { super(host); this.me = me; }
    @Override String token() { return "offline-native-theme-review"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) { writes++; throw new AssertionError("표시 검토에서 API 쓰기 금지: " + path); }
      reads++;
      if (path.equals("pvp/rank")) done.done(rank(), null);
      else if (path.equals("daily")) done.done(daily(), null);
      else if (path.equals("draw")) {
        drawReads++;
        done.done(NativeRecruitmentProbabilityTest.serverEconomyFixture(), null);
      }
      else if (path.equals("me")) done.done(me, null);
      else throw new AssertionError("예상하지 않은 API 읽기: " + path);
    }
  }

  ReviewApi prepare(MainActivity host) throws Exception {
    host.api.close();
    host.guide.enabled = false;
    host.motionEnabled = false;
    JSONObject me = MainActivity.obj("nickname", "항구 수비조장", "snack", 1240,
        "peanut", 80, "credits", 30, "units", new JSONArray(), "party", new JSONArray(),
        "battleTeam", new JSONArray(), "prices", MainActivity.obj(
            "tickets", MainActivity.obj("snack", 1000, "amount", 10),
            "peanutTickets", MainActivity.obj("peanut", 1, "amount", 20),
            "skip", MainActivity.obj("snack", 100), "maxParty", 10),
        "itemRules", MainActivity.obj("perBattle", 3, "maxStack", 20),
        "itemDefs", new JSONArray().put(MainActivity.obj("id", "potion", "name", "치료약", "price", 60,
            "heal", 35, "desc", "지금 나와 있는 갈매미의 체력을 35% 회복해요")));
    ReviewApi api = new ReviewApi(host, me);
    host.api = api;
    host.art.key = "offline-native-theme-review";
    host.state = MainActivity.obj("me", me, "units", new JSONArray(), "team", new JSONArray(),
        "chapters", new JSONArray(), "tutorial", MainActivity.obj("step", 9));
    return api;
  }

  void layout(MainActivity host, int w, int h) {
    review.viewport(host, w, h);
    ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(w == 1006 ? 36 : 0, w == 1006 ? 10 : 0,
        w == 1006 ? 10 : 0, w == 1006 ? 10 : 0);
    Runnable measure = () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, w, h);
    };
    measure.run(); review.preparePreDraw(host.root, measure);
  }

  View show(MainActivity host, String page, int w, int h) throws Exception {
    host.home(); layout(host, w, h);
    // Lobby attachment reads draw once for its claim badge. Measure this page from that settled baseline.
    ReviewApi api = (ReviewApi) host.api;
    api.homeDrawReads = api.drawReads;
    if (page.startsWith("settings-")) {
      NativeSettingsSheet.show(host);
      int tab = page.equals("settings-sound") ? 0 : page.equals("settings-alerts") ? 1
          : page.equals("settings-account") ? 2 : 3;
      View decor = geometry.layoutDialog(host);
      assertEquals("Opening Settings on the sound tab never fetches hidden probabilities",
          api.homeDrawReads, api.drawReads);
      decor.findViewWithTag("settings-tab-" + tab).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      RegistrationArtTest.await(host.art::idle);
      return geometry.layoutDialog(host);
    }
    if (page.equals("profile")) {
      View anchor = host.root.findViewWithTag("lobby-profile");
      assertNotNull("홈의 실제 프로필 버튼", anchor);
      anchor.performClick();
      assertNotNull(host.menus.extras.profilePopup);
      PopupWindow popup = host.menus.extras.profilePopup;
      View content = popup.getContentView();
      content.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h - host.dp(72), View.MeasureSpec.AT_MOST));
      content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
      RegistrationArtTest.await(host.art::idle);
      return content;
    }
    if (page.equals("modes")) host.menus.modes();
    else if (page.equals("shop")) host.menus.economy.shop();
    else host.menus.economy.expedition();
    layout(host, w, h);
    RegistrationArtTest.await(() -> host.art.idle() && host.menus.pendingFetches == 0);
    layout(host, w, h);
    return host.root;
  }

  String scene(String page) {
    if (page.startsWith("settings-")) return "backgrounds/bg-settings-harbor-office-v1.png";
    if (page.equals("profile")) return "backgrounds/bg-home.png";
    if (page.equals("shop")) return AppSkin.background("market");
    if (page.equals("explore")) return AppSkin.background("routes");
    return AppSkin.background("training");
  }

  void probabilityHeadlineInk(MainActivity host, View root, TextView title) {
    assertNotNull("A real native headline is required", title);
    assertNotNull("Headline text must be laid out", title.getLayout());
    assertEquals("Recruitment headline stays on one line", 1, title.getLineCount());
    Rect ink = new Rect();
    String label = title.getText().toString();
    title.getPaint().getTextBounds(label, 0, label.length(), ink);
    int inkTop = title.getBaseline() + ink.top;
    int inkBottom = title.getBaseline() + ink.bottom;
    assertTrue("Korean headline ink is not clipped above its native TextView: " + label,
        inkTop >= title.getCompoundPaddingTop());
    assertTrue("Korean headline ink is not clipped below its native TextView: " + label,
        inkBottom <= title.getHeight() - title.getCompoundPaddingBottom());
    Rect titleBounds = geometry.bounds(title, root);
    Rect panelBounds = geometry.bounds(root.findViewWithTag("settings-reading-panel"), root);
    assertTrue("Headline ink clears the brass frame by the 12dp reading inset: " + label,
        titleBounds.top + inkTop >= panelBounds.top + host.dp(12));
    assertEquals("Headline view is fully visible", titleBounds, geometry.visibleIntersection(title, root));
  }

  JSONObject inspect(MainActivity host, View root, String page, ReviewApi api) throws Exception {
    int bounds = 0, actions = 0, text = 0;
    List<View> buttons = new ArrayList<>();
    for (View view : geometry.descendants(root)) {
      if (!view.isShown()) continue;
      boolean scrolling = geometry.hasScrollingAncestor(view, root);
      if (view instanceof TextView && ((TextView) view).length() > 0) {
        geometry.readable((TextView) view); text++;
      }
      if (view instanceof Button || view instanceof SeekBar || view instanceof Switch) {
        assertTrue("터치 영역 48dp: " + view.getTag(), view.getHeight() >= host.dp(48));
        assertTrue("터치 너비 48dp: " + view.getTag(), view.getWidth() >= host.dp(48));
        if (!scrolling) {
          assertEquals("고정 조작부 전체가 실제 부모 안에 보임: " + view.getTag(),
              geometry.bounds(view, root), geometry.visibleIntersection(view, root));
          bounds++; buttons.add(view);
        }
        actions++;
      }
    }
    for (int i = 0; i < buttons.size(); i++) for (int j = i + 1; j < buttons.size(); j++) {
      assertFalse("고정 조작부 겹침: " + buttons.get(i).getTag() + "/" + buttons.get(j).getTag(),
          Rect.intersects(geometry.bounds(buttons.get(i), root), geometry.bounds(buttons.get(j), root)));
      bounds++;
    }
    if (page.startsWith("settings-")) {
      View nav = root.findViewWithTag("settings-dialog-nav");
      View panel = root.findViewWithTag("settings-reading-panel");
      assertNotNull(nav); assertNotNull(panel);
      assertEquals(geometry.bounds(nav, root), geometry.visibleIntersection(nav, root));
      assertEquals(geometry.bounds(panel, root), geometry.visibleIntersection(panel, root));
      bounds += 2;
      float occupied = nav.getWidth() * (float) nav.getHeight() + panel.getWidth() * (float) panel.getHeight();
      assertTrue("그림을 전체 종이판으로 가리지 않음", occupied < root.getWidth() * root.getHeight() * .72f);
      assertFalse("전체 설정창은 스크롤하지 않음", root.findViewWithTag("settings-dialog-body").canScrollVertically(1));
    } else if (!page.equals("profile")) {
      assertNotNull(host.root.findViewWithTag("theme-scene-background"));
      assertNotNull(host.root.findViewWithTag("theme-page-header"));
      for (View v : geometry.descendants(root)) if (v instanceof Button && v.isShown())
        assertFalse("핵심 선택을 전체 보기 뒤로 숨기지 않음", ((Button) v).getText().toString().contains("전체 보기"));
    }
    if (page.equals("shop")) {
      TextView description = host.root.findViewWithTag("shop-selected-description");
      assertNotNull(description);
      assertNotNull("Selected product copy has a dedicated reading surface", description.getBackground());
      assertEquals(MainActivity.INK, description.getCurrentTextColor());
    }
    if (page.equals("settings-probability")) {
      assertEquals("The opened probability tab performs exactly one read", 1, api.drawReads - api.homeDrawReads);
      JSONArray offers = NativeRecruitmentProbabilityTest.serverEconomyFixture().getJSONArray("gachas");
      int verifiedRates = 0;
      for (int i = 0; i < offers.length(); i++) {
        JSONObject offer = offers.getJSONObject(i);
        String kind = offer.getString("kind");
        View section = root.findViewWithTag("recruitment-probability-" + kind);
        assertNotNull("Loaded section for every server tier: " + kind, section);
        probabilityHeadlineInk(host, root, section.findViewWithTag("recruitment-title-" + kind));
        assertEquals("All three tiers are fully visible together: " + kind,
            geometry.bounds(section, root), geometry.visibleIntersection(section, root));
        JSONArray weights = offer.getJSONArray("weights");
        for (int j = 0; j < weights.length(); j++) {
          JSONObject weight = weights.getJSONObject(j);
          TextView rate = section.findViewWithTag("recruitment-rate-" + kind + "-" + weight.getString("tier"));
          assertNotNull("A loading-only capture cannot pass", rate);
          assertEquals("Exact economy.yaml rate", weight.getInt("w") + "%", rate.getText().toString());
          assertEquals("Every current rate is visible without scrolling",
              geometry.bounds(rate, root), geometry.visibleIntersection(rate, root));
          verifiedRates++;
        }
      }
      assertEquals(10, verifiedRates);
      View table = root.findViewWithTag("recruitment-probability-columns");
      assertNotNull(table);
      View summary = root.findViewWithTag("recruitment-probability-footer");
      assertEquals("Pity and refresh remain visible",
          geometry.bounds(summary, root), geometry.visibleIntersection(summary, root));
      for (View ancestor = table; ancestor != root && ancestor != null;) {
        assertFalse("Probability table requires no vertical scrolling", ancestor.canScrollVertically(1));
        ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
      }
      assertTrue(flow.labels(root).contains("보장까지 100회"));
      assertNull(root.findViewWithTag("recruitment-probability-status"));
    } else assertEquals("Closed probability tab never requests rates", 0, api.drawReads - api.homeDrawReads);
    Bitmap scene = ThemeArt.of(host).image(scene(page));
    assertNotNull("정확한 실제 번들 배경 존재", scene);
    assertTrue("낮은 해상도 대체 그림 금지", scene.getWidth() >= 1800 && scene.getHeight() >= 800);
    assertTrue(bounds > 0); assertTrue(actions > 0); assertTrue(text > 0); assertEquals(0, api.writes);
    return MainActivity.obj("checkedBounds", bounds, "actions", actions, "text", text,
        "sceneAsset", scene(page), "sceneWidth", scene.getWidth(), "sceneHeight", scene.getHeight(),
        "apiWrites", api.writes, "apiReads", api.reads, "homeDrawReads", api.homeDrawReads, "totalDrawReads", api.drawReads,
        "probabilityReads", api.drawReads - api.homeDrawReads,
        "verifiedProbabilityRates", page.equals("settings-probability") ? 10 : 0,
        "verifiedHeadlineInkBounds", page.equals("settings-probability") ? 3 : 0, "actualNativeViews", true,
        "rootKind", page.startsWith("settings-") ? "dialog" : page.equals("profile") ? "popup" : "page");
  }

  Path output() throws Exception {
    Path root = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "theme");
    Files.createDirectories(root); return root;
  }

  void capture(MainActivity host, View window, String page, String name, int w, int h) throws Exception {
    Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image); host.root.draw(canvas);
    if (window != host.root) {
      int x, y;
      if (page.equals("profile")) {
        View anchor = host.root.findViewWithTag("lobby-profile");
        Rect a = geometry.bounds(anchor, host.root);
        x = Math.max(host.root.getPaddingLeft(), Math.min(a.left, w - host.root.getPaddingRight() - window.getWidth()));
        y = Math.min(a.bottom + host.dp(8), h - host.root.getPaddingBottom() - window.getHeight());
      } else {
        canvas.drawColor(0x66000000);
        RectF safe = host.safeBounds(w, h);
        x = (int) (safe.centerX() - window.getWidth() / 2f);
        y = (int) (safe.centerY() - window.getHeight() / 2f);
      }
      canvas.save(); canvas.translate(x, y); window.draw(canvas); canvas.restore();
    }
    try (OutputStream stream = Files.newOutputStream(output().resolve(name + ".png"))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { image.recycle(); }
  }

  @Test public void captureSettingsProfileAndSharedPages() throws Exception {
    List<String> failures = new ArrayList<>();
    for (int[] size : SIZES) for (String page : PAGES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = page + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      JSONArray problems = new JSONArray(); findings.put(name, problems);
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get(); ReviewApi api = prepare(host);
        View rendered = null;
        try {
          rendered = show(host, page, size[0], size[1]);
          capture(host, rendered, page, name, size[0], size[1]);
          metrics.put(name, inspect(host, rendered, page, api));
        } catch (Throwable failure) {
          problems.put(failure.toString()); failures.add(name + ": " + failure);
          if (rendered == null && host.visibleDialog != null)
            rendered = host.visibleDialog.getWindow().getDecorView();
          if (rendered == null && host.menus.extras.profilePopup != null)
            rendered = host.menus.extras.profilePopup.getContentView();
          if (rendered == null) rendered = host.root;
          if (rendered.getWidth() > 0 && rendered.getHeight() > 0)
            capture(host, rendered, page, name, size[0], size[1]);
        } finally {
          if (host.visibleDialog != null) host.visibleDialog.dismiss();
          if (host.menus.extras.profilePopup != null) host.menus.extras.profilePopup.dismiss();
        }
      } catch (Throwable failure) { problems.put(failure.toString()); failures.add(name + ": " + failure); }
      Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output().resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    assertTrue(String.join("\n", failures), failures.isEmpty());
    assertEquals(PAGES.length * SIZES.length, metrics.length());
  }
}
