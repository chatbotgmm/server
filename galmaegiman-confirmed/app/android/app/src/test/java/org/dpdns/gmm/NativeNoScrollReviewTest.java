package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.*;
import android.widget.*;
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

/** 기본 페이지는 고정하고 긴 선택은 원래 데이터·콜백을 유지한 모달에서 읽는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeNoScrollReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  static final String[] PAGES = {
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
  };

  @Before
  public void pausedFrames() {
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  static List<View> descendants(View root) {
    return new NativeDialogLayoutReviewTest().descendants(root);
  }

  static boolean horizontallyPaged(View view, View root) {
    for (ViewParent parent = view.getParent();
        parent instanceof View && parent != root;
        parent = parent.getParent()) if (parent instanceof HorizontalScrollView) return true;
    return false;
  }

  static void assertFullyVisible(View view, View root) {
    NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
    assertTrue("필수 행동은 기본 화면에 표시: " + view.getTag(), view.isShown());
    Rect own = geometry.bounds(view, root);
    assertTrue("필수 행동은 높이와 너비를 확보: " + view.getTag(), own.width() > 0 && own.height() > 0);
    assertEquals(
        "가시 UI를 잘라 고정 화면을 만들지 않음: " + view.getTag(), own, geometry.visibleIntersection(view, root));
  }

  static int assertFixedMain(MainActivity host) {
    return assertFixedMain(host, true);
  }

  static int assertFixedMain(MainActivity host, boolean nativeTextMetrics) {
    int buttons = 0;
    for (View view : descendants(host.root)) {
      if (!view.isShown()) continue;
      // TextView의 계산상 범위는 터치로 스크롤할 수 없는 버튼에도 생길 수 있다.
      // 실제 컨테이너 스크롤과 글자의 읽기 면을 별도로 확인한다.
      if (view instanceof ViewGroup) {
        assertFalse(
            "기본 컨테이너는 아래로 스크롤되지 않음: " + view.getClass().getSimpleName(),
            view.canScrollVertically(1));
        assertFalse(
            "기본 컨테이너는 위로 스크롤되지 않음: " + view.getClass().getSimpleName(),
            view.canScrollVertically(-1));
      }
      assertEquals("기본 페이지의 세로 오프셋은 고정", 0, view.getScrollY());
      if (view instanceof Button && !horizontallyPaged(view, host.root)) {
        Button button = (Button) view;
        if (button.getText().toString().contains("뒤로")) recordFontMetrics(button);
        assertFullyVisible(button, host.root);
        assertTrue("버튼 터치 높이48dp: " + button.getText(), button.getHeight() >= host.dp(48));
        if (button.length() > 0 && nativeTextMetrics) {
          assertNotNull("버튼 실제 글꼴 배치", button.getLayout());
          assertTrue(
              "버튼 글자 높이 압축: "
                  + button.getText()
                  + " h="
                  + button.getHeight()
                  + " padding="
                  + button.getCompoundPaddingTop()
                  + "/"
                  + button.getCompoundPaddingBottom()
                  + " layout="
                  + button.getLayout().getHeight()
                  + " textPx="
                  + button.getTextSize()
                  + " density="
                  + button.getResources().getDisplayMetrics().density
                  + " scaledDensity="
                  + button.getResources().getDisplayMetrics().scaledDensity,
              button.getHeight()
                      - button.getCompoundPaddingTop()
                      - button.getCompoundPaddingBottom()
                  >= button.getLayout().getHeight());
          for (int line = 0; line < button.getLineCount(); line++)
            assertEquals(
                "버튼 이름 말줄임: " + button.getText(), 0, button.getLayout().getEllipsisCount(line));
        }
        buttons++;
      }
    }
    return buttons;
  }

  static void recordFontMetrics(Button button) {
    String folder = System.getProperty("gmm.nativeReviewOutput", "");
    if (folder.isEmpty() || button.getLayout() == null) return;
    android.graphics.Paint.FontMetricsInt metrics = button.getPaint().getFontMetricsInt();
    JSONObject value =
        MainActivity.obj(
            "sdk",
            android.os.Build.VERSION.SDK_INT,
            "fontScale",
            button.getResources().getConfiguration().fontScale,
            "text",
            button.getText().toString(),
            "width",
            button.getWidth(),
            "height",
            button.getHeight(),
            "paddingLeft",
            button.getCompoundPaddingLeft(),
            "paddingRight",
            button.getCompoundPaddingRight(),
            "paddingTop",
            button.getCompoundPaddingTop(),
            "paddingBottom",
            button.getCompoundPaddingBottom(),
            "layoutHeight",
            button.getLayout().getHeight(),
            "lineCount",
            button.getLayout().getLineCount(),
            "line0Width",
            button.getLayout().getLineWidth(0),
            "textPx",
            button.getTextSize(),
            "ascent",
            metrics.ascent,
            "descent",
            metrics.descent,
            "top",
            metrics.top,
            "bottom",
            metrics.bottom,
            "mode",
            android.os.Build.VERSION.SDK_INT == 24
                ? "Robolectric LEGACY; native glyph pixels unavailable"
                : "Android Native Skia with bundled font");
    try {
      Path output = Path.of(folder);
      Files.createDirectories(output);
      Files.write(
          output.resolve(
              "back-font-sdk"
                  + android.os.Build.VERSION.SDK_INT
                  + "-scale"
                  + button.getResources().getConfiguration().fontScale
                  + ".json"),
          value.toString(2).getBytes(StandardCharsets.UTF_8));
    } catch (Exception error) {
      throw new AssertionError(error);
    }
  }

  void captureDiagnostic(MainActivity host, String name, int[] size) throws Exception {
    review.viewport(host, size[0], size[1]);
    Bitmap image = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    host.root.draw(new Canvas(image));
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput"));
    Files.createDirectories(output);
    try (java.io.OutputStream stream = Files.newOutputStream(output.resolve(name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, stream);
    } finally {
      image.recycle();
    }
  }

  static View skillSurface(MainActivity host, BattleView battle) throws Exception {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    if (!battle.skillSheetNeeded) return battle.controls;
    if (host.visibleDialog == null) NativeBattleSheets.skills(battle);
    View decor = new NativeDialogLayoutReviewTest().layoutDialog(host);
    assertNotNull("긴 기술도 실제 선택 모달에서 전부 접근", decor.findViewWithTag("battle-skills-modal"));
    return decor;
  }

  static void scrollModal(MainActivity host, boolean bottom) {
    View decor = new NativeDialogLayoutReviewTest().layoutDialog(host);
    for (View view : descendants(decor))
      if (view instanceof ScrollView) {
        ((ScrollView) view).setSmoothScrollingEnabled(false);
        ((ScrollView) view).fullScroll(bottom ? View.FOCUS_DOWN : View.FOCUS_UP);
      }
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
  }

  void page(MainActivity host, String name) throws Exception {
    if (name.equals("unit"))
      host.unitDetail(host.list(host.state, "units").getJSONObject(0).getString("key"));
    else if (name.equals("practice")) host.practice();
    else if (name.equals("tutorial")) host.tutorial();
    else host.show(name);
    review.await(() -> !host.busy && host.menus.pendingFetches == 0);
  }

  Button named(View root, String name) {
    return new NativeRenderReviewTestEconomy().button(root, name);
  }

  void requiredTag(MainActivity host, String tag) {
    View view = host.root.findViewWithTag(tag);
    assertNotNull("핵심 행동 누락: " + tag, view);
    assertFullyVisible(view, host.root);
  }

  void readyCraftFullCards(MainActivity host, int width, int height) throws Exception {
    review.viewport(host, width, height);
    Bitmap kickoff = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(kickoff)); } finally { kickoff.recycle(); }
    RegistrationArtTest.await(() -> host.registrationArt().idle());
    review.viewport(host, width, height);
  }

  void assertCraftArtOrServerMask(RegistrationCardView card) {
    assertEquals(RegistrationCardView.FULL, card.picture.mode);
    if (card.picture.visibleIdentity) {
      assertNotNull("서버가 공개한 원본 등록증 로딩 완료", card.picture.bitmap);
      assertFalse(card.picture.bitmap.isRecycled());
    } else {
      assertFalse("서버가 가린 그림은 등록증 요청도 하지 않음", card.picture.loadAttempted);
      assertNull(card.picture.bitmap);
      assertEquals(View.VISIBLE, card.name.getVisibility());
      assertEquals(View.VISIBLE, card.grade.getVisibility());
    }
  }

  void criticalActions(MainActivity host, String page) {
    if (page.equals("modes")) {
      for (String name : new String[] {"스토리", "일반 게임", "랭크게임", "연습"}) {
        Button button = named(host.root, name);
        assertNotNull("기본 모드의 직접 진입 행동: " + name, button);
        assertFullyVisible(button, host.root);
      }
    } else if (page.equals("draw")) {
      for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
        requiredTag(host, "draw-card-" + kind);
      }
      requiredTag(host, "draw-quantity");
      requiredTag(host, "draw-total");
      requiredTag(host, "draw-submit");
      requiredTag(host, "draw-pity");
    } else if (page.equals("craft")) {
      requiredTag(host, "craft-recipe-picker");
      assertFullyVisible(host.menus.economy.craftActions, host.root);
      assertEquals("제작 수량과 만들기 보존", 2, host.menus.economy.craftActions.getChildCount());
      int materials = 0;
      for (View view : descendants(host.root)) {
        if (!(view.getTag() instanceof String)
            || !view.getTag().toString().startsWith("craft-material-count-")) continue;
        assertFullyVisible(view, host.root);
        assertTrue(
            "기본 조합에 실제 보유/필요 수량 표시",
            ((TextView) view).getText().toString().matches("[0-9,]+ / [0-9,]+"));
        String id = view.getTag().toString().substring("craft-material-count-".length());
        requiredTag(host, "craft-material-image-" + id);
        RegistrationCardView image = host.root.findViewWithTag("craft-material-image-" + id);
        assertEquals(id, image.picture.id);
        assertCraftArtOrServerMask(image);
        materials++;
      }
      assertTrue("재료 그림과 수량을 모달 뒤에 숨기지 않음", materials > 0);
      requiredTag(host, "craft-result-image");
      RegistrationCardView result = host.root.findViewWithTag("craft-result-image");
      assertCraftArtOrServerMask(result);
    } else if (page.equals("mix")) {
      for (int i = 0; i < 6; i++) {
        View slot = host.root.findViewWithTag("mix-empty-slot-" + i);
        assertNotNull("기본화면의 여섯 재료 슬롯 보존", slot);
        if (horizontallyPaged(slot, host.root)) {
          slot.requestRectangleOnScreen(new Rect(0, 0, slot.getWidth(), slot.getHeight()), true);
          Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertFullyVisible(slot, host.root);
      }
      assertFullyVisible(host.menus.economy.craftActions, host.root);
    } else if (page.equals("collection")) {
      for (String tag :
          new String[] {
            "collection-inline-grid",
            "collection-growth",
            "collection-team",
            "collection-craft",
            "collection-record"
          }) requiredTag(host, tag);
    } else if (page.equals("unit")) {
      for (String tag : new String[] {"unit-summary", "unit-hp", "unit-xp", "unit-growth-actions"})
        requiredTag(host, tag);
    } else if (page.equals("team")) requiredTag(host, "team-inline-candidates");
    else if (page.equals("types")) {
      View chart = null;
      for (View view : descendants(host.root))
        if (view.isShown()
            && view.getTag() instanceof String
            && view.getTag().toString().startsWith("type-chart-")) chart = view;
      assertNotNull("기본 상성 화면에 현재 속성 요약", chart);
      assertFullyVisible(chart, host.root);
      String labels = new NativeGameFlowTest().labels(chart);
      assertTrue("유리 배율을 기본에서 확인", labels.contains("유리 ×1.5"));
      assertTrue("불리 배율을 기본에서 확인", labels.contains("불리 ×0.67"));
    } else if (page.equals("daily")) {
      requiredTag(host, "tutorial:attend");
      requiredTag(host, "daily-attendance-summary");
      View summary = host.root.findViewWithTag("daily-attendance-summary");
      assertTrue(
          "오늘 보상은 숫자와 지원서 단위를 함께 확인",
          new NativeGameFlowTest().labels(summary).matches("(?s).*지원서 [0-9,]+장.*"));
    } else if (page.equals("practice")) {
      for (String tag :
          new String[] {
            "practice-roster-picker",
            "practice-enemy-picker",
            "practice-roster-summary",
            "practice-enemy-summary"
          }) requiredTag(host, tag);
      Button begin = named(host.root, "연습 시작");
      assertNotNull(begin);
      assertFullyVisible(begin, host.root);
    }
  }

  @Test
  public void everyBasicPageIsFixedWithoutClippingItsCoreActionsAtPhoneTabletAndLargeFont()
      throws Exception {
    JSONArray audits = new JSONArray();
    ArrayList<String> violations = new ArrayList<>();
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {780, 360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[0] == 780 ? 1.3f : 1);
      try (ActivityController<MainActivity> controller = review.start()) {
        MainActivity host = controller.get();
        review.viewport(host, size[0], size[1]);
        host.menus.mixDraft.clear();
        host.home();
        review.await(() -> ((LobbyView) host.root.getChildAt(0)).ready());
        try {
          review.capture(host, "fixed-" + size[0] + "-home", size[0], size[1]);
          assertFixedMain(host);
        } catch (AssertionError | RuntimeException error) {
          violations.add(size[0] + "/home: " + error);
          captureDiagnostic(host, "fixed-" + size[0] + "-home", size);
        }
        for (String page : PAGES) {
          int buttons = 0;
          String failure = "";
          try {
            page(host, page);
            if (page.equals("craft")) readyCraftFullCards(host, size[0], size[1]);
            review.capture(host, "fixed-" + size[0] + "-" + page, size[0], size[1]);
            buttons = assertFixedMain(host);
            if (host.visibleDialog != null)
              geometry.capture(host, size, "fixed-" + size[0] + "-" + page + "-modal");
            else criticalActions(host, page);
          } catch (AssertionError | RuntimeException error) {
            failure = error.toString();
            violations.add(size[0] + "/" + page + ": " + error);
            captureDiagnostic(host, "fixed-" + size[0] + "-" + page, size);
          } finally {
            if (host.visibleDialog != null) host.visibleDialog.dismiss();
          }
          audits.put(
              MainActivity.obj(
                  "page",
                  page,
                  "width",
                  size[0],
                  "height",
                  size[1],
                  "fontScale",
                  host.getResources().getConfiguration().fontScale,
                  "passed",
                  failure.isEmpty(),
                  "failure",
                  failure,
                  "checkedButtons",
                  buttons));
        }
      }
    }
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput"));
    Files.createDirectories(output);
    Files.write(
        output.resolve("fixed-page-audit.json"),
        audits.toString(2).getBytes(StandardCharsets.UTF_8));
    assertTrue("기본 페이지 계약 위반:\n" + String.join("\n", violations), violations.isEmpty());
  }

  @Test
  public void collectionPagesAndRecipePickerKeepEveryServerChoiceAndReturnTheExactId()
      throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      JournalNativeReviewTest.ReadOnlyApi api = new JournalNativeReviewTest().readOnly(host);
      host.menus.grade = "all";
      host.menus.query = "";
      page(host, "collection");
      review.capture(host, "picker-collection-main", 915, 412);
      requiredTag(host, "collection-inline-grid");
      JSONArray all = host.list(host.me(), "units");
      Set<String> visited = new HashSet<>();
      while (true) {
        for (View view : descendants(host.root.findViewWithTag("collection-inline-grid")))
          if (view instanceof RegistrationCardView.Artwork)
            visited.add(((RegistrationCardView.Artwork) view).id);
        if (!host.root.findViewWithTag("collection-inline-grid").performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)) break;
      }
      assertEquals("페이지를 넘겨 모든 서버 대원에 접근", all.length(), visited.size());
      JSONObject last = all.getJSONObject(all.length() - 1);
      View art = host.root.findViewWithTag("collection-registration-" + last.getString("id"));
      assertNotNull(art);
      // 한 장형은 보이는 종이 이미 선택되어 별도 탭이 필요 없다.
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals("원래 선택 callback의 정확한 id 유지", last.getString("id"),
          host.menus.library.selectedCollectionId);
      assertNull("기본 도감 페이징은 추가 모달을 열지 않음", host.visibleDialog);
      review.capture(host, "picker-collection-selected", 915, 412);
      assertFixedMain(host);

      host.menus.recipeQuery = "";
      host.menus.craftOnly = false;
      page(host, "craft");
      readyCraftFullCards(host, 915, 412);
      review.capture(host, "picker-craft-main", 915, 412);
      JSONArray recipes = api.responses.get("craft").getJSONArray("recipes");
      Set<String> recipeIds = new HashSet<>();
      while (true) {
        View choices = host.root.findViewWithTag("craft-recipe-list");
        for (View view : descendants(choices)) {
          Object tag = view.getTag();
          if (tag instanceof String && tag.toString().startsWith("craft-recipe-")
              && !tag.equals("craft-recipe-list")) recipeIds.add(tag.toString().substring("craft-recipe-".length()));
        }
        Button next = host.root.findViewWithTag("craft-recipe-pages-next");
        assertNotNull(next);
        if (!next.isEnabled()) break;
        next.performClick();
      }
      assertEquals("서버가 공개한 모든 조합식을 같은 화면의 페이지로 확인", recipes.length(), recipeIds.size());
      JSONObject recipe = recipes.getJSONObject(recipes.length() - 1);
      View target = host.root.findViewWithTag("craft-recipe-" + recipe.getString("id"));
      assertNotNull(target);
      target.performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals("원래 조합 선택 callback의 정확한 id 유지", recipe.getString("id"), host.menus.recipeId);
      assertNull("조합식 선택은 별도 모달 없이 완료", host.visibleDialog);
      readyCraftFullCards(host, 915, 412);
      review.capture(host, "picker-craft-selected", 915, 412);
      assertFixedMain(host);
    }
  }

  @Test
  @Config(sdk = 24)
  @GraphicsMode(GraphicsMode.Mode.LEGACY)
  public void minSdkOverflowRetainsOriginalViewsAndCallbacksAndClosesWithItsPage()
      throws Exception {
    NativeGameFlowTest fixtures = new NativeGameFlowTest();
    fixtures.isolate();
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      host.base("목록 검토", "review");
      LinearLayout original = host.body;
      int[] clicks = {0};
      for (int i = 0; i < 20; i++) {
        Button item = host.button("선택 " + i, () -> clicks[0]++);
        item.setTag("overflow-item-" + i);
        original.addView(item, new LinearLayout.LayoutParams(-1, host.dp(52)));
      }
      review.viewport(host, 915, 412);
      assertFixedMain(host, false);
      Button more = host.root.findViewWithTag("journal-page-more");
      assertTrue(more.isShown());
      more.performClick();
      assertNotNull("API24에서도 실제 목록 모달을 연다", host.visibleDialog);
      assertTrue("API24 목록 모달이 표시된다", host.visibleDialog.isShowing());
      // LEGACY는 번들 글꼴의 실제 픽셀 측정이 불가능하므로 여기서는 원본 위젯·수명주기만 검증한다.
      // 같은 목록의 API35 Native Skia 모달은 다른 사례에서 실제 배치·글자·48dp 검사를 유지한다.
      View decor = host.visibleDialog.getWindow().getDecorView();
      for (int i = 0; i < 20; i++)
        assertNotNull("원본 목록 전체 보존", decor.findViewWithTag("overflow-item-" + i));
      decor.findViewWithTag("overflow-item-19").performClick();
      assertEquals("원래 callback은 한 번 실행", 1, clicks[0]);
      android.app.AlertDialog opened = host.visibleDialog;
      host.base("다음 페이지", "next-review");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertFalse("페이지를 떠나면 선택창도 종료", opened.isShowing());
      assertNotSame("오래된 본문을 새 페이지로 복원하지 않음", original, host.body);
      assertFalse(descendants(host.root).contains(original));
    }
  }

  @Test
  public void firstCodeLoginAndFullDialogueModalPreserveInputAndClearTheirReferences()
      throws Exception {
    RuntimeEnvironment.setQualifiers("w780dp-h300dp-land-mdpi");
    RuntimeEnvironment.setFontScale(1.3f);
    NativeGameFlowTest fixtures = new NativeGameFlowTest();
    fixtures.isolate();
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      assertEquals("첫 실행은 계정 없이 확인", "", host.api.token());
      assertNull("첫 로그인 전에는 일반 페이지 객체가 없음", host.page);
      host.login();
      review.capture(host, "first-login-short-fixed", 780, 300);
      assertFixedMain(host);
      Button codeLogin = named(host.root, "코드로 로그인");
      assertNotNull("기본 로그인 화면에서 코드 입력으로 직접 이동", codeLogin);
      assertFullyVisible(codeLogin, host.root);
      codeLogin.performClick();
      View decor = geometry.layoutDialog(host);
      EditText code = null;
      for (View view : descendants(decor)) if (view instanceof EditText) code = (EditText) view;
      assertNotNull(code);
      code.setText("123456");
      review.viewport(host, 780, 260);
      decor = geometry.layoutDialog(host);
      assertTrue("작은 높이에서도 모달이 같은 입력 위젯을 유지", descendants(decor).contains(code));
      assertEquals("입력한 코드 유지", "123456", code.getText().toString());
      geometry.capture(host, new int[] {780, 260}, "first-login-full-form-modal");
      Button cancelCode = named(decor, "취소");
      assertNotNull("코드 모달의 실제 고정 취소 동작", cancelCode);
      assertFullyVisible(cancelCode, decor);
      cancelCode.performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertNull("로그인 모달을 닫으면 현재 참조 정리", host.visibleDialog);
      assertEquals("계정 입력창 취소는 실제 로그인 요청을 보내지 않음", "", host.api.token());

      RuntimeEnvironment.setFontScale(1);
      RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
      host.motionEnabled = false;
      boolean[] finished = {false};
      String first = "수비대가 항구의 파도를 살피고 다음 모험을 준비해요. ".repeat(30);
      String second = "두 번째 줄의 원문은 그대로 이어져요.";
      host.talk(
          new JSONArray()
              .put(MainActivity.obj("who", "N", "say", first))
              .put(MainActivity.obj("who", "N", "say", second)),
          "이야기",
          () -> finished[0] = true);
      review.capture(host, "long-story-fixed-preview", 915, 412);
      StoryView story = (StoryView) host.root.getChildAt(0);
      assertTrue("긴 대사는 전문 모달 진입 제공", story.longDialogue);
      assertFixedMain(host);
      story.next.performClick();
      decor = geometry.layoutDialog(host);
      TextView full = decor.findViewWithTag("story-full-dialogue");
      assertEquals("기본 화면에서 요약한 전문의 표시 용어만 정리", first.replace("수비대", "방범대"), full.getText().toString());
      assertEquals("서버 대사 원문은 변경하지 않음", first, story.lines.optJSONObject(0).optString("say"));
      host.visibleDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertNull("닫기 후 Activity 모달 참조 정리", host.visibleDialog);
      assertNull("닫기 후 Story 모달 참조 정리", story.dialogueModal);
      assertEquals("닫기만으로 이야기를 넘기지 않음", 0, story.index);
      story.next.performClick();
      geometry.layoutDialog(host);
      android.app.AlertDialog nextDialog = host.visibleDialog;
      Button nextButton = nextDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
      assertTrue("전문 모달의 실제 다음 동작이 표시", nextButton.isShown());
      assertEquals("다음", nextButton.getText().toString());
      nextButton.performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      JSONObject diagnostic =
          MainActivity.obj(
              "clickedDialog",
              System.identityHashCode(nextDialog),
              "clickedDialogShowing",
              nextDialog.isShowing(),
              "remainingDialog",
              host.visibleDialog == null ? 0 : System.identityHashCode(host.visibleDialog),
              "remainingShowing",
              host.visibleDialog != null && host.visibleDialog.isShowing(),
              "storyDialog",
              story.dialogueModal == null ? 0 : System.identityHashCode(story.dialogueModal),
              "index",
              story.index,
              "fullText",
              story.fullText,
              "finished",
              finished[0]);
      if (host.visibleDialog != null) {
        View remaining = host.visibleDialog.getWindow().getDecorView();
        int titleId = host.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : remaining.findViewById(titleId);
        diagnostic.put("remainingTitle", title == null ? "" : title.getText().toString());
        TextView fullDialogue = remaining.findViewWithTag("story-full-dialogue");
        diagnostic.put(
            "remainingFullDialogue", fullDialogue == null ? "" : fullDialogue.getText().toString());
      }
      Files.write(
          Path.of(
              System.getProperty("gmm.nativeReviewOutput"), "story-modal-dismiss-diagnostic.json"),
          diagnostic.toString(2).getBytes(StandardCharsets.UTF_8));
      assertNull("전문 다음 이후 Activity 모달 참조 정리: " + diagnostic, host.visibleDialog);
      assertNull("전문 다음 이후 Story 모달 참조 정리", story.dialogueModal);
      assertEquals("다음 대사를 정확히 한 줄 진행", 1, story.index);
      assertEquals(second, story.fullText);
      assertFalse("전문 다음은 이야기 전체 건너뛰기가 아님", finished[0]);
      review.capture(host, "long-story-next-original-line", 915, 412);
    }
  }
}

