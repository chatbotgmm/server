package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeModesShortViewportTest.*;

import android.content.DialogInterface;
import android.graphics.*;
import android.text.Layout;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 두 갈래 모드 입구·스토리 하위 메뉴를 실제 네이티브 View로 검증한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h374dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeModeCategoryNavigationTest {
  final NativeModesShortViewportTest viewport = new NativeModesShortViewportTest();
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeMenusThemeReviewTest menus = new NativeMenusThemeReviewTest();
  static final String[] ROUTES = {"modes", "modesStory"};

  @Before public void isolate() { viewport.isolate(); }
  @After public void restore() { viewport.restoreDisplay(); }

  Path output() throws Exception {
    Path directory = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feedback-mode-navigation");
    Files.createDirectories(directory); return directory;
  }
  void write(String name, JSONObject value) throws Exception {
    Files.write(output().resolve(name), value.toString(2).getBytes(StandardCharsets.UTF_8));
  }
  void show(MainActivity host, Profile p, String route) throws Exception {
    viewport.layout(host, p);
    host.show(route);
    viewport.layout(host, p);
    Bitmap first = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(first)); } finally { first.recycle(); }
    RegistrationArtTest.await(() -> host.art.idle() && host.registrationArt().idle());
    viewport.layout(host, p);
  }
  void capture(MainActivity host, Profile p, String name) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(bitmap));
      Set<Integer> colors = new HashSet<>();
      for (int y = 0; y < p.height; y += 7) for (int x = 0; x < p.width; x += 7) colors.add(bitmap.getPixel(x, y));
      assertTrue("실제 배경·네이티브 글자가 그려져야 한다", colors.size() > 64);
      try (OutputStream stream = Files.newOutputStream(output().resolve(name))) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    } finally { bitmap.recycle(); }
  }

  JSONObject inspect(MainActivity host, Profile p, String route,
      NativeMenusThemeReviewTest.FixtureApi api, JSONArray findings) throws Exception {
    ViewGroup choices = host.root.findViewWithTag("modes-panes");
    check(choices != null && choices.getChildCount() == 2, "카테고리 선택지는 정확히 두 개", findings);
    check(host.screen.equals(route), "잘못된 실제 화면 route", findings);
    check(api.reads.isEmpty() && api.writes.isEmpty(), "카테고리 화면은 순위·저장 요청 없음", findings);
    check(host.menus.pendingFetches == 0, "카테고리 화면에 불필요한 순위 요청", findings);
    check(host.root.findViewWithTag("modes-normal") == null, "대전 하위 실행이 루트에 노출됨", findings);
    check(host.root.findViewWithTag("modes-pvp-summary") == null, "편성 요약이 카테고리에 남음", findings);
    JSONObject cards = new JSONObject(), texts = new JSONObject();
    List<View> all = new ArrayList<>(); collect(host.root, all);
    int scrolls = 0, textChecks = 0, touchChecks = 0;
    for (View view : all) {
      if (view instanceof ScrollView || view instanceof HorizontalScrollView
          || view.getClass().getSimpleName().contains("NestedScrollView")) scrolls++;
      if (!view.isShown()) continue;
      if (view instanceof Button) {
        Rect box = bounds(view, host.root);
        check(box.width() >= host.dp(48) && box.height() >= host.dp(48), "48dp 터치 영역 부족: " + view.getTag(), findings);
        check(box.equals(visibleIntersection(view, host)), "버튼 잘림: " + view.getTag(), findings);
        touchChecks++;
      }
      if (!(view instanceof TextView) || ((TextView) view).length() == 0) continue;
      TextView label = (TextView) view; Layout layout = label.getLayout();
      String id = String.valueOf(view.getTag());
      Rect box = bounds(view, host.root), visible = visibleIntersection(view, host), glyph = glyphBounds(label, host.root);
      check(layout != null, "실제 글자 Layout 없음: " + id, findings);
      if (layout == null) continue;
      int ellipsis = 0;
      for (int line = 0; line < layout.getLineCount(); line++) {
        ellipsis += layout.getEllipsisCount(line);
        check(layout.getLineWidth(line) <= layout.getWidth() + .5f, "글자 가로 넘침: " + id, findings);
      }
      check(ellipsis == 0, "글자 생략: " + id, findings);
      check(label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom() >= layout.getHeight(), "글자 세로 넘침: " + id, findings);
      check(positive(glyph) && box.contains(glyph) && visible.contains(glyph), "실제 글리프 잘림: " + id, findings);
      check(label.getAutoSizeTextType() == TextView.AUTO_SIZE_TEXT_TYPE_NONE && Math.abs(label.getTextScaleX() - 1) < .0001f,
          "글꼴 축소 또는 가로 압축: " + id, findings);
      texts.put(id, MainActivity.obj("text", label.getText().toString(), "boundsPx", rect(box),
          "visibleBoundsPx", rect(visible), "glyphBoundsPx", rect(glyph), "ellipsisCount", ellipsis,
          "textSizePx", label.getTextSize(), "textScaleX", label.getTextScaleX(),
          "autoSizeTextType", label.getAutoSizeTextType()));
      textChecks++;
    }
    if (choices != null && choices.getChildCount() == 2) {
      Rect first = bounds(choices.getChildAt(0), host.root), second = bounds(choices.getChildAt(1), host.root);
      check(!Rect.intersects(first, second), "두 카테고리 터치 영역 겹침", findings);
      String[] labels = route.equals("modes") ? new String[] {"스토리", "대전"} : new String[] {"스토리모드", "모험\n준비 중"};
      for (int i = 0; i < 2; i++) {
        View view = choices.getChildAt(i);
        check(view instanceof Button, "실제 Android Button 아님", findings);
        if (!(view instanceof Button)) continue;
        Button button = (Button) view;
        check(labels[i].contentEquals(button.getText()), "카테고리 이름 변경", findings);
        check(button.isEnabled() == (!route.equals("modesStory") || i == 0), "모험 준비중 활성화 또는 유효 경로 비활성화", findings);
        check(button.getBackground() instanceof NativeMenus.ModeCardDrawable, "책/방패/지도 모티프 누락", findings);
        float expected = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, host.uiText(26), host.getResources().getDisplayMetrics());
        check(Math.abs(expected - button.getTextSize()) < .1f && button.getTypeface().equals(host.headline), "카테고리 글꼴 변경/축소", findings);
        Rect box = bounds(button, host.root), glyph = glyphBounds(button, host.root);
        int motif = button.getBackground() instanceof NativeMenus.ModeCardDrawable
            ? ((NativeMenus.ModeCardDrawable) button.getBackground()).motif : -1;
        check(motif == (i == 0 ? 0 : route.equals("modes") ? 1 : 2), "화면 의미와 장식 불일치", findings);
        float d = host.getResources().getDisplayMetrics().density;
        RectF card = new RectF(box); card.inset(2 * d, 2 * d);
        float size = Math.min(66 * d, Math.min(card.width() * .24f, card.height() * .32f));
        RectF motifBounds = new RectF(card.centerX() - size / 2, card.centerY() - 40 * d - size / 2,
            card.centerX() + size / 2, card.centerY() - 40 * d + size / 2);
        check(!RectF.intersects(new RectF(glyph), motifBounds), "글자가 모드 장식과 겹침", findings);
        Rect reading = new Rect(box); reading.inset(host.dp(12), host.dp(10));
        check(reading.contains(glyph), "카테고리 글자가 얇은 테두리와 겹침", findings);
        cards.put(i == 0 ? "story" : route.equals("modes") ? "pvp" : "adventure",
            MainActivity.obj("text", button.getText().toString(), "enabled", button.isEnabled(),
                "motif", motif, "boundsPx", rect(box), "glyphBoundsPx", rect(glyph), "readingBoundsPx", rect(reading),
                "fullContentDescription", button.getContentDescription() == null ? "" : button.getContentDescription().toString()));
      }
    }
    check(scrolls == 0, "카테고리 화면에 스크롤 컨테이너가 생김", findings);
    return MainActivity.obj("profile", p.name, "route", route, "actualNativeViews", true,
        "screenWidthPx", host.root.getWidth(), "screenHeightPx", host.root.getHeight(),
        "densityDpi", p.dpi, "fontScale", p.fontScale, "safeBoundsPx", rect(safe(host)),
        "cards", cards, "texts", texts, "textChecks", textChecks, "touchChecks", touchChecks,
        "scrollContainers", scrolls, "fixtureReads", new JSONArray(api.reads), "apiWrites", api.writes.size(),
        "visualReview", "pending PNG inspection", "findings", findings);
  }

  @Test public void captureModeCategoriesAcrossShortSafeAreas() throws Exception {
    JSONObject findings = new JSONObject(), metrics = new JSONObject();
    JSONArray profiles = new JSONArray(), captures = new JSONArray();
    List<String> failures = new ArrayList<>(); int captured = 0;
    for (Profile p : PROFILES) {
      profiles.put(p.json());
      for (String route : ROUTES) {
        String filename = p.name + "-" + route + ".png";
        captures.put(MainActivity.obj("filename", filename, "profile", p.name, "route", route, "widthPx", p.width, "heightPx", p.height));
        findings.put(filename, new JSONArray().put("Capture has not completed"));
      }
    }
    JSONObject manifest = MainActivity.obj("schemaVersion", 1, "suite", "feedback-mode-navigation", "baseline", BASELINE,
        "profiles", profiles, "captures", captures, "routes", new JSONArray(Arrays.asList(ROUTES)),
        "generatedBy", "NativeModeCategoryNavigationTest.captureModeCategoriesAcrossShortSafeAreas",
        "insetDelivery", "synthetic WindowInsetsCompat through production GameSafeArea; no OS lifecycle claim",
        "source", "production MainActivity routes and NativeMenus Views; no synthetic UI or new raster art");
    write("manifest.json", manifest); write("findings.json", findings); write("metrics.json", metrics);
    try {
      for (Profile p : PROFILES) for (String route : ROUTES) {
        viewport.configure(p); String filename = p.name + "-" + route + ".png";
        JSONArray problems = new JSONArray();
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = menus.prepare(host, "modes");
          show(host, p, route); capture(host, p, filename); captured++;
          metrics.put(filename, inspect(host, p, route, api, problems));
        } catch (Throwable failure) { problems.put(failure.toString()); }
        findings.put(filename, problems);
        if (problems.length() > 0) failures.add(filename + ": " + problems);
        write("findings.json", findings); write("metrics.json", metrics);
      }
    } finally { write("manifest.json", manifest); write("findings.json", findings); write("metrics.json", metrics); }
    assertEquals(16, captured); assertEquals(16, metrics.length()); assertEquals(16, findings.length());
    assertTrue(String.join("\n", failures), failures.isEmpty());
  }

  Button choice(MainActivity host, int index) {
    return (Button) ((ViewGroup) host.root.findViewWithTag("modes-panes")).getChildAt(index);
  }

  @Test public void storyCategoryKeepsExistingStoryAndNaturalBackRoutes() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = menus.prepare(host, "modes");
      String state = host.state.toString();
      host.show("modes"); choice(host, 0).performClick(); assertEquals("modesStory", host.screen);
      assertEquals("스토리모드", choice(host, 0).getText().toString());
      Button adventure = choice(host, 1); assertFalse(adventure.isEnabled());
      int epoch = host.epoch, reads = api.reads.size(); adventure.performClick();
      assertEquals(epoch, host.epoch); assertEquals(reads, api.reads.size());
      choice(host, 0).performClick(); assertEquals("story", host.screen);
      host.chapter = 0; host.load("stages"); assertEquals("stages", host.screen);
      host.getOnBackPressedDispatcher().onBackPressed(); assertEquals("story", host.screen);
      host.getOnBackPressedDispatcher().onBackPressed(); assertEquals("modesStory", host.screen);
      host.getOnBackPressedDispatcher().onBackPressed(); assertEquals("modes", host.screen);
      assertEquals(state, host.state.toString()); assertTrue(api.writes.isEmpty()); assertFalse(api.reads.contains("pvp/rank"));
    }
  }

  @Test public void pvpSubroutesReturnToTheirCategoryWithoutChangingParty() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = menus.prepare(host, "modes");
      String state = host.state.toString(); host.show("modes"); choice(host, 1).performClick();
      assertEquals("modesPvp", host.screen);
      assertNull("대전 첫 분류에는 편성 바로가기가 없다", host.root.findViewWithTag("modes-pvp-team"));
      host.root.findViewWithTag("modes-standings").performClick(); assertEquals("pvp", host.screen);
      host.back(); assertEquals("modesPvp", host.screen);
      host.root.findViewWithTag("modes-practice").performClick(); assertEquals("practice", host.screen);
      Button rosterPicker = host.root.findViewWithTag("practice-roster-picker");
      assertNotNull("연습 준비 화면의 편성 진입은 유지", rosterPicker);
      rosterPicker.performClick(); assertNotNull(host.visibleDialog); assertTrue(host.visibleDialog.isShowing());
      host.visibleDialog.dismiss();
      host.back(); assertEquals("modesPvp", host.screen);
      host.back(); assertEquals("modes", host.screen);
      assertEquals(state, host.state.toString()); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void missingPvpPartyStillRequiresConfirmationAndReturnsToPvp() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = menus.prepare(host, "modes-empty");
      host.show("modesPvp"); host.root.findViewWithTag("modes-normal").performClick();
      assertNotNull(host.visibleDialog); assertTrue(host.visibleDialog.isShowing()); assertEquals("modesPvp", host.screen);
      android.app.AlertDialog dialog = host.visibleDialog;
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      RegistrationArtTest.await(() -> !dialog.isShowing());
      assertEquals("team", host.screen); assertEquals("modesPvp", host.menus.teamReturnTarget());
      assertEquals("pvp", host.selectionMode); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void repeatedAndBusyCategoryClicksKeepCurrentPageAndEpoch() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = menus.prepare(host, "modes");
      for (String route : ROUTES) {
        host.show(route); View page = host.page; int epoch = host.epoch, reads = api.reads.size();
        host.busy = true;
        try { for (int i = 0; i < 3; i++) { choice(host, 0).performClick(); choice(host, 1).performClick(); } }
        finally { host.busy = false; }
        assertEquals(route, host.screen); assertSame(page, host.page); assertEquals(epoch, host.epoch);
        assertEquals(reads, api.reads.size()); assertTrue(api.writes.isEmpty());
      }
    }
  }
}
