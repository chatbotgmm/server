package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 기술 후보 선택부터 검사한다. 부모에게 가려진 버튼도 자신의 높이만으로 통과시키지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeDialogLayoutReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGrowthComparisonTest fixture = new NativeGrowthComparisonTest();
  final int[][] sizes = {{780, 360}, {915, 412}, {1006, 411}, {1280, 720}};

  @Before
  public void isolateAndKeepAnimationClockPaused() {
    review.scheduledFramesUseThePausedLooperClock();
    fixture.isolate();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  void frame() {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
  }

  JSONObject candidate(String id, String name, String effect) {
    return MainActivity.obj(
        "id", id, "name", name, "attr", "ICE", "cat", "mag", "power", 0, "desc", effect);
  }

  ActivityController<MainActivity> start(int[] size) throws Exception {
    RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
    RuntimeEnvironment.setFontScale(size[0] == 1006 ? 1 : 1.3f);
    ActivityController<MainActivity> controller = fixture.start();
    MainActivity h = controller.get();
    h.api =
        new NativeApi(h) {
          @Override
          String token() {
            return "local-dialog-layout-fixture";
          }

          @Override
          void call(String path, JSONObject body, Result done) {
            fail("팝업 배치 검사에서 API 요청 금지: " + path);
          }
        };
    h.root.setPadding(size[0] == 1006 ? 36 : 8, 8, 8, 8);
    review.viewport(h, size[0], size[1]);
    JSONObject unit = h.unit("o1");
    unit.put("id", "D1")
        .put("learnLeft", 2)
        .put("learnTier", 10)
        .put("evolve", new JSONArray())
        .put(
            "learn",
            new JSONArray()
                .put(candidate("review-a", "서리로 감싸는 긴 이름의 방어 자세", "3턴 동안 받는 피해가 20% 감소해요."))
                .put(candidate("review-b", "빙하 너머로 이어지는 회복의 물결", "자신의 최대 체력 25%를 회복해요."))
                .put(
                    candidate(
                        "review-c",
                        "달빛 아래 조용히 펼쳐지는 아주 긴 기술 이름",
                        "60% 확률로 상대를 1턴 동안 잠들게 해요. 같은 효과가 이미 적용 중일 때는 다시 적용하지 않아요.")));
    h.state.put("me", MainActivity.obj("nickname", "팝업 회귀 검사"));
    h.state.put("team", new JSONArray().put("o1"));
    h.state.put(
        "itemDefs",
        MainActivity.obj(
            "potion", MainActivity.obj("name", "치료약", "heal", 50, "desc", "체력을 회복해요."),
            "potion2", MainActivity.obj("name", "고급 치료약", "heal", 100, "desc", "체력을 더 회복해요.")));
    h.state.put(
        "items",
        new JSONArray()
            .put(MainActivity.obj("id", "potion", "count", 3))
            .put(MainActivity.obj("id", "potion2", "count", 2)));
    h.menus.library.unit("o1");
    review.viewport(h, size[0], size[1]);
    return controller;
  }

  List<View> descendants(View root) {
    List<View> result = new ArrayList<>();
    result.add(root);
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++)
        result.addAll(descendants(group.getChildAt(i)));
    }
    return result;
  }

  View tagged(View root, String tag) {
    View found = root.findViewWithTag(tag);
    assertNotNull("필수 배치 태그: " + tag, found);
    return found;
  }

  Rect bounds(View child, View root) {
    if (child == root) return new Rect(0, 0, root.getWidth(), root.getHeight());
    Rect result = new Rect();
    child.getDrawingRect(result);
    ((ViewGroup) root).offsetDescendantRectToMyCoords(child, result);
    return result;
  }

  Rect visibleIntersection(View child, View root) {
    Rect visible = bounds(child, root);
    View current = child;
    while (current != root && current.getParent() instanceof View) {
      current = (View) current.getParent();
      if (current instanceof ViewGroup && ((ViewGroup) current).getClipChildren()) {
        Rect clip = bounds(current, root);
        if (((ViewGroup) current).getClipToPadding()) {
          clip.left += current.getPaddingLeft();
          clip.top += current.getPaddingTop();
          clip.right -= current.getPaddingRight();
          clip.bottom -= current.getPaddingBottom();
        }
        if (!visible.intersect(clip)) return new Rect();
      }
    }
    if (!visible.intersect(0, 0, root.getWidth(), root.getHeight())) return new Rect();
    return visible;
  }

  boolean hasScrollingAncestor(View view, View root) {
    for (ViewParent p = view.getParent(); p instanceof View && p != root; p = p.getParent())
      if (p instanceof ScrollView || p instanceof HorizontalScrollView || p instanceof ListView)
        return true;
    return false;
  }

  void readable(TextView text) {
    if (!text.isShown() || text.getText().length() == 0) return;
    assertNotNull("텍스트 Layout: " + text.getText(), text.getLayout());
    assertTrue(
        "텍스트 자체 높이 부족: " + text.getText(),
        text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
            >= text.getLayout().getHeight());
    for (int i = 0; i < text.getLineCount(); i++)
      assertEquals("본문 말줄임 금지: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
  }

  void fixedButton(MainActivity h, View decor, Button button) {
    assertTrue(button.getText() + " 터치 높이48dp", button.getHeight() >= h.dp(48));
    Rect own = bounds(button, decor), visible = visibleIntersection(button, decor);
    assertEquals(button.getText() + " 부모 가시영역에 전체 버튼이 들어가야 함", own, visible);
    readable(button);
  }

  View layoutDialog(MainActivity h) {
    frame();
    assertNotNull(h.visibleDialog);
    assertTrue(h.visibleDialog.isShowing());
    Window window = h.visibleDialog.getWindow();
    View decor = window.getDecorView();
    WindowManager.LayoutParams p = window.getAttributes();
    int safeWidth = h.root.getWidth() - h.root.getPaddingLeft() - h.root.getPaddingRight();
    int safeHeight = h.root.getHeight() - h.root.getPaddingTop() - h.root.getPaddingBottom();
    int width = p.width > 0 ? p.width : safeWidth;
    int limit = Math.max(1, safeHeight - h.dp(16));
    assertTrue("창 너비가 안전 영역 이내", width <= safeWidth);
    assertTrue("창 지정 높이가 안전 영역 이내", p.height <= 0 || p.height <= limit);
    Runnable measure =
        () -> {
          review.force(decor);
          decor.measure(
              View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(
                  p.height > 0 ? p.height : limit,
                  p.height > 0 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST));
          decor.layout(0, 0, width, decor.getMeasuredHeight());
        };
    measure.run();
    review.preparePreDraw(decor, measure);
    assertTrue("실제 팝업 높이가 안전 영역 이내", decor.getHeight() <= limit);
    for (View view : descendants(decor)) {
      if (!view.isShown()) continue;
      if (view instanceof TextView) readable((TextView) view);
      if (view instanceof Button) {
        assertTrue("본문 버튼도48dp 보장: " + ((Button) view).getText(), view.getHeight() >= h.dp(48));
        if (!hasScrollingAncestor(view, decor)) fixedButton(h, decor, (Button) view);
      }
    }
    int titleId = h.getResources().getIdentifier("alertTitle", "id", "android");
    TextView title = titleId == 0 ? null : decor.findViewById(titleId);
    if (title != null && title.isShown())
      assertEquals("팝업 제목 전체 가시영역", bounds(title, decor), visibleIntersection(title, decor));
    return decor;
  }

  void capture(MainActivity h, int[] size, String name) throws Exception {
    View decor = layoutDialog(h);
    String output = System.getProperty("gmm.nativeReviewOutput", "");
    if (output.isEmpty()) return;
    Path directory = Path.of(output);
    Files.createDirectories(directory);
    Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    h.root.draw(canvas);
    canvas.drawColor(0x66000000);
    int left =
        h.root.getPaddingLeft()
            + (size[0] - h.root.getPaddingLeft() - h.root.getPaddingRight() - decor.getWidth()) / 2;
    int top =
        h.root.getPaddingTop()
            + (size[1] - h.root.getPaddingTop() - h.root.getPaddingBottom() - decor.getHeight())
                / 2;
    canvas.save();
    canvas.translate(left, top);
    decor.draw(canvas);
    canvas.restore();
    try (OutputStream stream = Files.newOutputStream(directory.resolve(name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    } finally {
      bitmap.recycle();
    }
    JSONObject metrics =
        MainActivity.obj(
            "width",
            size[0],
            "height",
            size[1],
            "dialogWidth",
            decor.getWidth(),
            "dialogHeight",
            decor.getHeight(),
            "parentIntersectionChecks",
            true);
    Files.write(
        directory.resolve(name + "-metrics.json"),
        metrics.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  void scrollBottom(MainActivity h, ScrollView scroll) {
    layoutDialog(h);
    scroll.setSmoothScrollingEnabled(false);
    scroll.fullScroll(View.FOCUS_DOWN);
    frame();
  }

  void close(MainActivity h) {
    h.visibleDialog.dismiss();
    frame();
  }

  @Test
  public void actualLearningEntrySelectionAndScrollingKeepFooterVisibleWithoutSpending()
      throws Exception {
    for (int[] size : sizes) {
      try (ActivityController<MainActivity> controller = start(size)) {
        MainActivity h = controller.get();
        String stateBefore = h.state.toString();
        tagged(h.root, "unit-learn").performClick();
        AlertDialog selection = h.visibleDialog;
        View decor = layoutDialog(h);
        fixedButton(h, decor, (Button) tagged(decor, "skill-choice-close"));
        ScrollView scroll = (ScrollView) tagged(decor, "skill-choice-scroll");
        Button confirm = (Button) tagged(decor, "skill-choice-confirm");
        Button finish = (Button) tagged(decor, "skill-choice-finish");
        assertFalse(confirm.isEnabled());
        Rect footer = bounds(tagged(decor, "skill-choice-footer"), decor);
        for (String id : new String[] {"review-a", "review-b", "review-c"})
          assertNotNull(tagged(decor, "skill-choice-" + id));
        capture(h, size, "dialog75-" + size[0] + "-learning-top");
        scrollBottom(h, scroll);
        capture(h, size, "dialog75-" + size[0] + "-learning-bottom");
        View last = tagged(decor, "skill-choice-review-c");
        assertTrue("마지막 후보를 실제 스크롤로 볼 수 있음", visibleIntersection(last, decor).height() > 0);
        last.performClick();
        decor = layoutDialog(h);
        confirm = (Button) tagged(decor, "skill-choice-confirm");
        assertTrue(confirm.isEnabled());
        assertEquals(
            "선택 후 고정 footer 위치 보존", footer, bounds(tagged(decor, "skill-choice-footer"), decor));
        capture(h, size, "dialog75-" + size[0] + "-learning-selected");
        ((ScrollView) tagged(decor, "skill-choice-scroll")).fullScroll(View.FOCUS_UP);
        frame();
        layoutDialog(h);
        assertEquals(
            "스크롤 후 고정 footer 위치 보존", footer, bounds(tagged(decor, "skill-choice-footer"), decor));
        fixedButton(h, decor, confirm);
        fixedButton(h, decor, (Button) tagged(decor, "skill-choice-finish"));
        assertEquals("후보 선택/스크롤만으로 학습 기회 소모 금지", stateBefore, h.state.toString());
        assertFalse(h.busy);
        finish.performClick();
        capture(h, size, "dialog75-" + size[0] + "-learning-finish-confirmation");
        assertEquals(stateBefore, h.state.toString());
        close(h);
        tagged(selection.getWindow().getDecorView(), "skill-choice-close").performClick();
        frame();
        assertFalse("비소모 닫기는 실제 후보 창을 닫음", selection.isShowing());
        assertEquals("닫기만으로 학습 기회 소모 금지", stateBefore, h.state.toString());
      }
    }
  }

  @Test
  public void healingSettingsAndSelectionPreserveFixedActionsAndScrollableDetails()
      throws Exception {
    for (int[] size : sizes) {
      try (ActivityController<MainActivity> controller = start(size)) {
        MainActivity h = controller.get();
        tagged(h.root, "unit-heal").performClick();
        View decor = layoutDialog(h);
        fixedButton(h, decor, (Button) tagged(decor, "heal-confirm"));
        ScrollView healing = (ScrollView) tagged(decor, "heal-body-scroll");
        scrollBottom(h, healing);
        capture(h, size, "dialog75-" + size[0] + "-healing-bottom");
        fixedButton(h, decor, (Button) tagged(decor, "heal-close"));
        close(h);
        NativeSettingsSheet.show(h);
        decor = layoutDialog(h);
        fixedButton(h, decor, (Button) tagged(decor, "settings-dialog-close"));
        capture(h, size, "dialog75-" + size[0] + "-settings-top");
        for (View view : descendants(decor))
          if (view instanceof Button && ((Button) view).getText().toString().contains("계정 삭제 ▾")) {
            view.performClick();
            break;
          }
        for (View view : descendants(decor))
          if (view instanceof ScrollView) scrollBottom(h, (ScrollView) view);
        decor = layoutDialog(h);
        Button delete = null;
        for (View view : descendants(decor))
          if (view instanceof Button && ((Button) view).getText().toString().equals("계정 삭제"))
            delete = (Button) view;
        assertNotNull("계정 삭제 펼침 영역", delete);
        assertEquals(
            "펼친 영역 끝까지 실제로 스크롤 가능", bounds(delete, decor), visibleIntersection(delete, decor));
        capture(h, size, "dialog75-" + size[0] + "-settings-expanded-bottom");
        close(h);
        JSONArray practice = new JSONArray();
        for (int i = 1; i <= 9; i++)
          practice.put(
              MainActivity.obj(
                  "id", "review-" + i, "name", "검토 갈매미 " + i, "image", "", "attr", "돌"));
        h.state.put("practiceUnits", practice);
        h.menus.extras.picker(false);
        capture(h, size, "dialog75-" + size[0] + "-practice-picker");
        close(h);
      }
    }
  }

  @Test
  public void rewardMaterialProbabilityAndBattleDetailsRemainReadableWithLongContent()
      throws Exception {
    for (int[] size : sizes) {
      try (ActivityController<MainActivity> controller = start(size)) {
        MainActivity h = controller.get();
        h.menus.economy.showTicketReward(
            "뽑기권 +30!\n지금 1,000장 · 다음은 1시간 뒤\n다음 지급까지 기다려 주세요.", false);
        capture(h, size, "dialog75-" + size[0] + "-ticket-reward");
        close(h);
        h.menus.economy.materialGuide(
            MainActivity.obj(
                "id",
                "D1",
                "name",
                "태초의 갈매미맨",
                "usable",
                2,
                "need",
                3,
                "locked",
                1,
                "away",
                1,
                "source",
                "뽑기 · 조합 · 교환에서 얻을 수 있어요. 보호하거나 탐험 중인 갈매미는 재료로 사용할 수 없어요."));
        capture(h, size, "dialog75-" + size[0] + "-material-guide");
        close(h);
        JSONArray weights = new JSONArray();
        for (String tier : new String[] {"흔함", "안흔함", "특별", "희귀", "전설", "신화"})
          weights.put(MainActivity.obj("tierName", tier, "w", 1.0));
        h.menus.economy.gachaGuide(
            MainActivity.obj("label", "고급", "kind", "HIGH", "cost", 5, "weights", weights),
            MainActivity.obj("pity", MainActivity.obj("tierName", "전설", "left", 99)));
        capture(h, size, "dialog75-" + size[0] + "-probability-top");
        for (View view : descendants(h.visibleDialog.getWindow().getDecorView()))
          if (view instanceof ScrollView) scrollBottom(h, (ScrollView) view);
        capture(h, size, "dialog75-" + size[0] + "-probability-bottom");
        close(h);
        JSONObject battle = new NativeGameFlowTest().battle("dialog-readonly");
        JSONArray history = new JSONArray();
        for (int i = 0; i < 24; i++)
          history.put("[나] 검토 갈매미가 기술을 사용했어요. 피해 25 · 공격 -20% · 남은 시간 2턴");
        battle.put("history", history);
        h.battle = battle;
        BattleView view = new BattleView(h, battle);
        NativeBattleSheets.history(view);
        capture(h, size, "dialog75-" + size[0] + "-battle-history-top");
        for (View child : descendants(h.visibleDialog.getWindow().getDecorView()))
          if (child instanceof ScrollView) scrollBottom(h, (ScrollView) child);
        capture(h, size, "dialog75-" + size[0] + "-battle-history-bottom");
        close(h);
      }
    }
  }

  @Test
  public void growthComparisonsKeepTitleAndNativeActionsVisibleAtBothScrollEnds() throws Exception {
    for (int[] size : sizes) {
      try (ActivityController<MainActivity> controller = start(size)) {
        MainActivity h = controller.get();
        h.menus.library.compareReplacement("o1", "learn", fixture.oldSkill(), fixture.nextSkill());
        View decor = layoutDialog(h);
        capture(h, size, "dialog75-" + size[0] + "-skill-comparison-top");
        scrollBottom(h, (ScrollView) tagged(decor, "growth-comparison-scroll"));
        capture(h, size, "dialog75-" + size[0] + "-skill-comparison-bottom");
        close(h);
        h.menus.library.comparePromotion("o1", fixture.award());
        decor = layoutDialog(h);
        capture(h, size, "dialog75-" + size[0] + "-promotion-comparison-top");
        scrollBottom(h, (ScrollView) tagged(decor, "growth-comparison-scroll"));
        capture(h, size, "dialog75-" + size[0] + "-promotion-comparison-bottom");
        close(h);
      }
    }
  }
}
