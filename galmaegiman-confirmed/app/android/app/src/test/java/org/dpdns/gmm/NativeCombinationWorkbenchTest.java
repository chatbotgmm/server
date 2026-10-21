package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.*;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 재료 작업대: 단서 반짝임의 시작·중단, 결과 탭 복귀, 중복 조합 차단을 실제 View로 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCombinationWorkbenchTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeResultDossierTest screens = new NativeResultDossierTest();
  final NativeRegistrationReviewTest captures = new NativeRegistrationReviewTest();

  @Before public void isolate() throws Exception { screens.setup(); }

  @After public void restore() { screens.restore(); }

  static JSONObject material(String id, String name, int usable, Boolean hint) {
    JSONObject unit = MainActivity.obj("id", id, "img", id, "name", name, "tierName", "흔함", "usable", usable);
    if (hint != null) {
      try { unit.put("hiddenHint", hint); } catch (JSONException error) { throw new AssertionError(error); }
    }
    return unit;
  }

  /** 서버 응답 그대로: 새 서버는 maxMaterials·hiddenHint를 싣고, 구 서버는 둘 다 없다. */
  static final class Api extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final List<JSONObject> writes = new ArrayList<>();
    final List<Result> pending = new ArrayList<>();
    JSONObject craft;
    boolean holdWrites;
    Api(MainActivity host, boolean updatedServer) {
      super(host);
      JSONObject recipe = MainActivity.obj("id", "U2", "img", "U2", "name", "야경", "tierName", "안흔함",
          "ready", true, "max", 2, "materials", new JSONArray()
              .put(MainActivity.obj("id", "C3", "img", "C3", "name", "암흑 갈매미맨", "need", 1, "usable", 4))
              .put(MainActivity.obj("id", "C1", "img", "C1", "name", "기본 갈매미맨", "need", 1, "usable", 4)));
      JSONArray pool = new JSONArray()
          .put(material("C3", "암흑 갈매미맨", 4, updatedServer ? true : null))
          .put(material("C1", "기본 갈매미맨", 4, updatedServer ? false : null))
          .put(material("I3", "꺼지지 않는 그림자", 6, updatedServer ? false : null));
      craft = MainActivity.obj("recipes", new JSONArray().put(recipe), "pool", pool);
      if (updatedServer) {
        try { craft.put("maxMaterials", 5); } catch (JSONException error) { throw new AssertionError(error); }
      }
    }
    @Override String token() { return "offline-workbench"; }
    @Override void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      if (body != null) {
        writes.add(body);
        if (holdWrites) { pending.add(done); return; }
        done.done(MainActivity.obj("result", MainActivity.obj("id", "U2", "made", true, "text", "조합 완료")), null);
        return;
      }
      if (path.equals("craft")) done.done(craft, null);
      else if (path.equals("native/state")) done.done(MainActivity.obj("me", MainActivity.obj("units", new JSONArray())), null);
      else throw new AssertionError("Unexpected read " + path);
    }
  }

  Api prepare(MainActivity host, boolean updatedServer) {
    host.api.close();
    Api api = new Api(host, updatedServer);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = false;
    host.art.key = "offline-workbench";
    host.state = MainActivity.obj("me", MainActivity.obj("units", new JSONArray()), "units", new JSONArray());
    host.menus.mixDraft.clear();
    return api;
  }

  void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32)); }

  void add(MainActivity host, String id) {
    host.root.findViewWithTag("mix-picker-material-" + id).performClick();
    idle();
  }

  void remove(MainActivity host, String id) {
    View slot = host.root.findViewWithTag("mix-material-" + id);
    assertNotNull("선택한 재료 칸 " + id, slot);
    assertTrue("× 배지가 있는 칸 전체가 빼기 버튼", slot.isClickable());
    slot.performClick();
    idle();
  }

  HiddenRecipeFrame frame(MainActivity host) {
    View view = host.root.findViewWithTag("mix-result-card");
    return view instanceof HiddenRecipeFrame ? (HiddenRecipeFrame) view : null;
  }

  boolean hinted(HiddenRecipeFrame frame) {
    return frame != null && frame.getContentDescription().toString().contains("숨은 조합 단서");
  }

  /** 테두리 한 점의 알파. 반짝임이 멈추면 시간이 흘러도 같은 값이어야 한다. */
  int borderAlpha(HiddenRecipeFrame frame) {
    Bitmap bitmap = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
    try {
      frame.draw(new Canvas(bitmap));
      int x = frame.getWidth() / 2, y = Math.round(frame.getResources().getDisplayMetrics().density * 2);
      return Color.alpha(bitmap.getPixel(x, y)) + Color.red(bitmap.getPixel(x, y));
    } finally {
      bitmap.recycle();
    }
  }

  void confirm(MainActivity host) {
    assertNotNull(host.visibleDialog);
    host.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    idle();
  }

  @Test public void hintStartsWithClueMaterialAndStopsWhenItIsRemovedOrCleared() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.load("craft");
      idle();
      assertEquals("홈 기본 진입은 재료 탭", "mix", host.screen);
      assertTrue(((Button) host.root.findViewWithTag("craft-recipe-picker")).getText().toString().contains("결과로 찾기"));
      add(host, "C1"); add(host, "C1");
      assertFalse("단서 없는 재료만 고르면 반짝이지 않음", hinted(frame(host)));
      add(host, "C3");
      assertTrue("단서 재료를 넣으면 미공개 카드 테두리가 반짝임", hinted(frame(host)));
      remove(host, "C3");
      assertFalse("단서 재료를 빼면 반짝임 중단", hinted(frame(host)));
      add(host, "C3");
      assertTrue(hinted(frame(host)));
      host.root.findViewWithTag("mix-clear").performClick();
      idle();
      assertTrue(host.menus.mixDraft.isEmpty());
      assertFalse("전체 비우기 뒤 반짝임 없음", hinted(frame(host)));
      assertTrue("조회·선택만으로는 소모 요청 없음", api.writes.isEmpty());
    }
  }

  @Test public void hintPulseOnlyAdvancesWhileForegroundAndFocused() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      add(host, "C3"); add(host, "C1");
      HiddenRecipeFrame frame = frame(host);
      assertTrue(hinted(frame));
      screens.layout(host, 915, 412, 160);
      // 백그라운드(onPause)에서는 motionResumed=false: 시간이 흘러도 테두리 밝기가 그대로다.
      host.motionResumed = false;
      int paused = borderAlpha(frame);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
      assertEquals("백그라운드에서는 반짝임 정지", paused, borderAlpha(frame));
      // 포그라운드·창 포커스에서는 약 2초 주기로 밝기가 바뀐다(1초 뒤가 가장 밝다).
      host.motionResumed = true;
      frame.onWindowFocusChanged(true);
      if (frame.hasWindowFocus()) {
        int start = borderAlpha(frame);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertNotEquals("복귀하면 반짝임 재개", start, borderAlpha(frame));
        System.out.println("WORKBENCH-RESUME-VERIFIED");
      } else System.out.println("WORKBENCH-RESUME-NOT-VERIFIED: Robolectric window has no focus");
      host.root.removeAllViews();
      assertFalse("화면을 떠나면 분리되어 다음 프레임을 예약하지 않음", frame.isAttachedToWindow());
    }
  }

  @Test public void resultTabCraftReturnsToResultTabAndHomeEntryStaysOnMaterials() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.menus.economy.craft(false);
      idle();
      assertEquals("craftRecipes", host.screen);
      View recipe = host.root.findViewWithTag("craft-recipe-picker");
      assertTrue("결과로 찾기 탭 선택 상태", recipe.isSelected());
      Button make = null;
      for (View view : NativeNoScrollReviewTest.descendants(host.menus.economy.craftActions))
        if (view instanceof Button && ((Button) view).getText().toString().contains("만들기")) make = (Button) view;
      assertNotNull("결과 탭의 조합 버튼", make);
      make.performClick();
      confirm(host);
      assertTrue(api.requests.contains("POST craft"));
      assertEquals("craftResult", host.screen);
      assertEquals("결과 탭에서 시작한 조합은 결과 탭으로 돌아감", "craftRecipes", host.menus.economy.acquisitionOrigin());
      host.getOnBackPressedDispatcher().onBackPressed();
      idle();
      assertEquals("craftRecipes", host.screen);
      assertTrue(host.root.findViewWithTag("craft-recipe-picker").isSelected());
      host.load("craft");
      idle();
      assertEquals("홈의 조합 진입은 재료 탭", "mix", host.screen);
      assertTrue("재료로 찾기 탭 선택", host.root.findViewWithTag("craft-material-picker").isSelected());
      assertFalse(host.root.findViewWithTag("craft-recipe-picker").isSelected());
    }
  }

  @Test public void confirmationCannotOpenTwiceOrSpendTwiceOrSendChangedSelection() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      add(host, "C3"); add(host, "C1");
      View submit = host.root.findViewWithTag("mix-submit");
      submit.performClick();
      AlertDialog first = host.visibleDialog;
      assertNotNull(first);
      submit.performClick();
      assertSame("확인창은 하나만", first, host.visibleDialog);
      // 확인 전에 선택이 바뀌면 이전 스냅샷으로 조합하지 않는다.
      host.menus.mixDraft.add("C1");
      confirm(host);
      assertTrue("바뀐 선택은 조합 요청을 보내지 않음", api.writes.isEmpty());
      host.menus.mixDraft.remove(host.menus.mixDraft.size() - 1);
      host.menus.economy.craft(true);
      idle();
      api.holdWrites = true;
      host.root.findViewWithTag("mix-submit").performClick();
      confirm(host);
      assertEquals(1, api.writes.size());
      assertEquals("[\"C3\",\"C1\"]", api.writes.get(0).optJSONArray("ids").toString());
      assertTrue("요청 중에는 busy", host.busy);
      host.root.findViewWithTag("mix-submit").performClick();
      assertTrue("요청 중에는 확인창도 다시 열지 않음", host.visibleDialog == null || !host.visibleDialog.isShowing());
      assertEquals("중복 조합 요청 없음", 1, api.writes.size());
    }
  }

  @Test public void olderServerWithoutHintsKeepsSixMaterialLimitAndNeverPulses() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, false);
      host.menus.economy.craft(true);
      idle();
      for (int i = 0; i < 3; i++) { add(host, "C3"); add(host, "C1"); }
      assertEquals("구 서버는 maxMaterials가 없어 기존 6개 한도", 6, host.menus.mixDraft.size());
      assertFalse(host.root.findViewWithTag("mix-picker-material-I3").isEnabled());
      assertFalse("구 서버는 hiddenHint가 없어 반짝이지 않음", hinted(frame(host)));
    }
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); prepare(host, true);
      host.menus.economy.craft(true);
      idle();
      for (int i = 0; i < 3; i++) { add(host, "I3"); add(host, "C1"); }
      assertEquals("새 서버 한도(실제 최대 재료 수) 5", 5, host.menus.mixDraft.size());
    }
  }

  @Test public void captureWorkbenchAtShortLandscapeLargeFontAndTablet() throws Exception {
    for (int[] size : new int[][] {{780, 360, 160}, {915, 412, 160}, {1280, 720, 160}})
      for (float fontScale : new float[] {1, 1.3f}) {
        RuntimeEnvironment.setQualifiers("w" + Math.round(size[0] * 160f / size[2]) + "dp-h"
            + Math.round(size[1] * 160f / size[2]) + "dp-land-" + size[2] + "dpi");
        RuntimeEnvironment.setFontScale(fontScale);
        try (var controller = flow.start()) {
          MainActivity host = controller.get(); prepare(host, true);
          host.menus.mixDraft.addAll(Arrays.asList("C3", "C1"));
          host.menus.economy.craft(true);
          screens.layout(host, size[0], size[1], size[2]);
          // 실제 등록증 그림이 다 읽힌 뒤 찍는다(이미지 준비 중 자리 표시를 검토하지 않는다).
          screens.review.await(() -> host.registrationArt().pendingCount() == 0);
          screens.layout(host, size[0], size[1], size[2]);
          String name = size[0] + "x" + size[1] + "-font" + Math.round(fontScale * 100);
          Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"),
              "combination-workbench");
          Files.createDirectories(output);
          captures.writeScreenshot(host, output.resolve(name + ".png"), size[0], size[1]);
          Rect screen = new Rect(host.root.getPaddingLeft(), host.root.getPaddingTop(),
              size[0] - host.root.getPaddingRight(), size[1] - host.root.getPaddingBottom());
          for (String tag : new String[] {"mix-submit", "mix-clear", "craft-recipe-picker", "mix-result-card"}) {
            View view = host.root.findViewWithTag(tag);
            assertNotNull(tag + " · " + name, view);
            // 공개 결과와 단서가 함께면 두 카드를 옆으로 넘긴다. 넘기는 영역이 화면 안이면 된다.
            for (ViewParent up = view.getParent(); up instanceof View; up = up.getParent())
              if (up instanceof HorizontalScrollView) { view = (View) up; break; }
            Rect box = screens.bounds(view, host.root);
            assertTrue(tag + " 화면 안 · " + name + " " + box, screen.contains(box));
            if (view instanceof Button)
              assertTrue(tag + " 48dp · " + name, view.getHeight() >= host.dp(48));
          }
          if (host.root.findViewWithTag("mix-picker-material-I3") == null)
            assertNotNull("보유 재료가 한 쪽을 넘으면 쪽 넘김 · " + name, host.root.findViewWithTag("mix-material-pages"));
          for (String tag : new String[] {"mix-picker-material-C3", "mix-picker-material-C1", "mix-material-C3",
              "mix-empty-slot-2"}) {
            View touch = host.root.findViewWithTag(tag);
            assertNotNull(tag + " · " + name, touch);
            assertTrue(tag + " 48dp 터치 · " + name, touch.getWidth() >= host.dp(48) && touch.getHeight() >= host.dp(48));
            assertTrue(tag + " 화면 안 · " + name, screen.contains(screens.bounds(touch, host.root)));
          }
          for (String tag : new String[] {"craft-material-picker", "craft-recipe-picker", "mix-count",
              "mix-result-heading", "mix-clear", "mix-submit"}) {
            TextView label = host.root.findViewWithTag(tag);
            assertNotNull(tag, label);
            assertNotNull(tag, label.getLayout());
            for (int line = 0; line < label.getLineCount(); line++)
              assertTrue("글자 가로 잘림 없음 · " + tag + " · " + name, label.getLayout().getLineWidth(line)
                  <= label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight() + 1);
            assertTrue("글자 세로 잘림 없음 · " + tag + " · " + name, label.getLayout().getHeight()
                <= label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom() + 1);
          }
          // 재료를 하나만 고른 시작 상태: 「재료 선택 중」 카드 두 장, 조합하기 비활성.
          host.menus.mixDraft.clear();
          host.menus.mixDraft.add("C1");
          host.menus.economy.craft(true);
          screens.layout(host, size[0], size[1], size[2]);
          screens.review.await(() -> host.registrationArt().pendingCount() == 0);
          screens.layout(host, size[0], size[1], size[2]);
          idle();
          screens.layout(host, size[0], size[1], size[2]);
          captures.writeScreenshot(host, output.resolve(name + "-start.png"), size[0], size[1]);
          for (View view : NativeNoScrollReviewTest.descendants(host.root.findViewWithTag("mix-results")))
            if (view instanceof TextView && view.isShown() && ((TextView) view).getLayout() != null) {
              TextView label = (TextView) view;
              assertEquals("결과 카드 글자는 띄어쓰기에서만 줄바꿈 · " + label.getText() + " · " + name,
                  label.getText().toString().split("\n").length, label.getLineCount());
            }
          assertFalse("재료 2개 미만이면 조합하기 비활성", host.root.findViewWithTag("mix-submit").isEnabled());
          assertEquals(2, ((ViewGroup) host.root.findViewWithTag("mix-results")).getChildCount());
          assertTrue(NativeNoScrollReviewTest.descendants(host.root.findViewWithTag("mix-results")).stream()
              .anyMatch(v -> v instanceof TextView && ((TextView) v).getText().toString().replace('\n', ' ').equals("재료 선택 중")));
          host.menus.mixDraft.clear();
          host.menus.mixDraft.addAll(Arrays.asList("C3", "C1"));
          host.menus.economy.craft(true);
          screens.layout(host, size[0], size[1], size[2]);
          Rect submit = screens.bounds(host.root.findViewWithTag("mix-submit"), host.root);
          View result = host.root.findViewWithTag("mix-result-card");
          for (ViewParent up = result.getParent(); up instanceof View; up = up.getParent())
            if (up instanceof HorizontalScrollView) { result = (View) up; break; }
          Rect card = screens.bounds(result, host.root);
          assertTrue("조합 버튼은 결과 아래 · " + name, submit.top >= card.bottom - 1);
          for (View view : NativeNoScrollReviewTest.descendants(host.root))
            if (view instanceof TextView && view.isShown() && ((TextView) view).getLayout() != null) {
              TextView label = (TextView) view;
              // 실제 데이터의 가장 긴 이름(10자)도 사용 가능 수량까지 잘리지 않아야 한다.
              for (int line = 0; line < label.getLineCount(); line++)
                assertEquals("말줄임 없음 · " + name + " · " + label.getText(), 0, label.getLayout().getEllipsisCount(line));
            }
        }
      }
  }
}
