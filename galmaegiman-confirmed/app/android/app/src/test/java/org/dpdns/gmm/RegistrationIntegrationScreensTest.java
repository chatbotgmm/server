package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 등록증 연결만 검증한다. 실제 계정·서버·추첨 없이 기존 화면과 콜백 계약을 유지한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RegistrationIntegrationScreensTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();

  @Before
  public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject fixture() {
    JSONArray instances = new JSONArray(), species = new JSONArray();
    for (int i = 1; i <= 10; i++) {
      String speciesId = i <= 6 ? "C" + i : "U" + (i - 6);
      String name = i == 4 ? "아주 긴 이름의 등록증 갈매미맨" : "등록증 갈매미 " + i;
      instances.put(MainActivity.obj(
          "key", "o" + i, "id", speciesId, "name", name, "image", "",
          "level", 12, "hp", 100, "maxHp", 100, "xp", 20, "xpNext", 100,
          "tier", "COMMON", "tierName", "흔함", "attr", "돌", "attrName", "돌",
          "attrCode", "STONE", "skills", new JSONArray()));
      species.put(MainActivity.obj(
          "id", speciesId, "name", name, "image", "", "have", 1, "known", true,
          "top", true, "tier", "COMMON", "tierName", "흔함", "attr", "STONE", "attrName", "돌"));
    }
    return MainActivity.obj(
        "me", MainActivity.obj("name", "검토", "units", species,
            "battleTeam", MainActivity.arr(List.of("C1", "C2", "C3"))),
        "units", instances, "team", MainActivity.arr(List.of("o1", "o2", "o3")),
        "chapters", new JSONArray(), "attributes", MainActivity.obj("STONE", MainActivity.obj("name", "돌")),
        "typeChart", new JSONObject(), "tutorial", MainActivity.obj("step", 9));
  }

  JSONObject recruitment() {
    JSONArray offers = new JSONArray();
    String[] kinds = {"LOW", "MID", "HIGH"}, labels = {"하급", "중급", "고급"};
    for (int i = 0; i < kinds.length; i++)
      offers.put(MainActivity.obj("kind", kinds[i], "label", labels[i], "cost", i == 2 ? 5 : i + 1,
          "img", "C1", "weights", new JSONArray().put(
              MainActivity.obj("tier", "COMMON", "tierName", "흔함", "w", 100))));
    return MainActivity.obj("credits", 100, "claim", 5, "wait", 1000, "gachas", offers,
        "pity", MainActivity.obj("tierName", "전설", "left", 9));
  }

  final class FixtureApi extends NativeApi {
    final JSONObject stored, offers;
    final List<String> writes = new ArrayList<>();
    final List<JSONObject> submitted = new ArrayList<>();

    FixtureApi(MainActivity host, JSONObject state) {
      super(host);
      stored = state;
      offers = recruitment();
    }

    @Override String token() { return "registration-fixture"; }

    @Override void call(String path, JSONObject body, Result callback) {
      if (body != null) {
        writes.add(path);
        submitted.add(body);
        // 쓰기는 기록만 한다. 완료/결과/지급을 테스트에서 임의 생성하지 않는다.
        return;
      }
      if (path.equals("draw")) callback.done(offers, null);
      else if (path.equals("native/catalog"))
        callback.done(MainActivity.obj("units", stored.optJSONObject("me").optJSONArray("units")), null);
      else callback.done(stored, null);
    }
  }

  FixtureApi prepare(MainActivity host) {
    host.api.close();
    FixtureApi api = new FixtureApi(host, fixture());
    host.api = api;
    host.state = api.stored;
    host.art.key = "registration-fixture";
    host.guide.enabled = false;
    return api;
  }

  void layout(MainActivity host, int width, int height) {
    review.viewport(host, width, height);
    review.preparePreDraw(host.root, () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, width, height);
    });
  }

  RegistrationCardView.Artwork artwork(View view, int mode) {
    assertNotNull(view);
    assertTrue("등록증 전용 뷰를 사용", view instanceof RegistrationCardView.Artwork);
    RegistrationCardView.Artwork art = (RegistrationCardView.Artwork) view;
    assertEquals(mode, art.mode);
    return art;
  }

  Button button(View root, String label) {
    for (View view : geometry.descendants(root))
      if (view instanceof Button && ((Button) view).getText().toString().equals(label))
        return (Button) view;
    fail("버튼 없음: " + label);
    return null;
  }

  @Test
  public void collectionKeepsEightInlineChoicesAndFullDetailWithinEachViewport() throws Exception {
    for (float scale : new float[] {1, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      for (int[] size : new int[][] {{915, 412}, {1006, 411}, {1280, 720}}) {
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get();
          FixtureApi api = prepare(host);
          layout(host, size[0], size[1]);
          host.collection();
          layout(host, size[0], size[1]);
          ViewGroup grid = host.root.findViewWithTag("collection-inline-grid");
          assertEquals(2, grid.getChildCount());
          int cards = 0;
          for (View view : geometry.descendants(grid))
            if (view instanceof RegistrationCardView.Artwork) {
              artwork(view, RegistrationCardView.COMPACT);
              NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
              cards++;
            }
          assertEquals(8, cards);
          RegistrationCardView.Artwork selectedArt = artwork(
              host.root.findViewWithTag("collection-selected-registration"), RegistrationCardView.FULL);
          NativeNoScrollReviewTest.assertFullyVisible(selectedArt, host.root);
          android.graphics.RectF display = new android.graphics.RectF();
          RegistrationCardView.contain(display, selectedArt.getWidth(), selectedArt.getHeight(), 768, 1024);
          assertTrue("선택 등록증은 최소 108dp 너비", display.width() >= host.dp(108));
          assertTrue("선택 등록증은 최소 144dp 높이", display.height() >= host.dp(144));
          for (String tag : new String[] {"collection-growth", "collection-team", "collection-craft", "collection-record"})
            NativeNoScrollReviewTest.assertFullyVisible(host.root.findViewWithTag(tag), host.root);
          View choice = host.root.findViewWithTag("collection-registration-C4");
          ((View) choice.getParent()).performClick();
          assertEquals("C4", host.menus.library.selectedCollectionId);
          assertEquals("C4", artwork(host.root.findViewWithTag("collection-selected-registration"),
              RegistrationCardView.FULL).id);
          host.root.findViewWithTag("collection-record").performClick();
          assertNotNull(host.visibleDialog);
          artwork(host.visibleDialog.getWindow().getDecorView().findViewWithTag("collection-record-registration"),
              RegistrationCardView.FULL);
          host.visibleDialog.dismiss();
          assertTrue("선택·기록 열기는 서버 쓰기 없음", api.writes.isEmpty());
        }
      }
    }
  }

  @Test
  public void teamArtUsesSpeciesWhileStoryAndPvpKeepTheirOwnSaveKeys() throws Exception {
    for (boolean pvp : new boolean[] {false, true}) {
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get();
        FixtureApi api = prepare(host);
        host.selectionMode = pvp ? "pvp" : "story";
        host.team();
        layout(host, 915, 412);
        for (int i = 0; i < 3; i++) {
          View slot = host.root.findViewWithTag("lineup-slot-" + i);
          RegistrationCardView.Artwork art = artwork(slot.findViewWithTag("lineup-portrait"),
              RegistrationCardView.COMPACT);
          assertEquals("C" + (i + 1), art.id);
          NativeNoScrollReviewTest.assertFullyVisible(art, host.root);
          View handle = slot.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
          assertNotNull("기존 드래그 핸들 유지", handle);
          assertTrue(handle.getHeight() >= host.dp(48));
        }
        ViewGroup grid = host.root.findViewWithTag("team-inline-candidates");
        int count = 0;
        for (View view : geometry.descendants(grid))
          if (view instanceof RegistrationCardView.Artwork) {
            artwork(view, RegistrationCardView.COMPACT);
            NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
            count++;
          }
        assertEquals(6, count);
        String fourth = pvp ? "C4" : "o4";
        View replacement = host.root.findViewWithTag("team-candidate-registration-" + fourth);
        ((View) replacement.getParent()).performClick();
        assertEquals(fourth, host.menus.teamDraft.get(0));
        assertTrue("후보 선택은 저장 전 초안만 변경", api.writes.isEmpty());
        layout(host, 915, 412);
        Button save = host.root.findViewWithTag("tutorial:team-save");
        NativeNoScrollReviewTest.assertFullyVisible(save, host.root);
        save.performClick();
        assertEquals(List.of(pvp ? "battle-team" : "native/team"), api.writes);
        assertEquals(fourth, api.submitted.get(0).getJSONArray(pvp ? "ids" : "keys").getString(0));
      }
    }
  }

  @Test
  public void largeFontPvpKeepsSixPicturesAndNavigationInTheFixedScreen() {
    RuntimeEnvironment.setFontScale(1.3f);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.selectionMode = "pvp";
      layout(host, 915, 412);
      host.team();
      layout(host, 915, 412);
      for (String tag : new String[] {"team-open-collection", "team-open-items", "tutorial:team-save"}) {
        Button action = host.root.findViewWithTag(tag);
        assertNotNull(action);
        NativeNoScrollReviewTest.assertFullyVisible(action, host.root);
        assertTrue(action.getHeight() >= host.dp(48));
        assertNotNull(action.getLayout());
        assertTrue(action.getHeight() - action.getCompoundPaddingTop() - action.getCompoundPaddingBottom()
            >= action.getLayout().getHeight());
        for (int line = 0; line < action.getLineCount(); line++)
          assertEquals(0, action.getLayout().getEllipsisCount(line));
      }
      ViewGroup grid = host.root.findViewWithTag("team-inline-candidates");
      int count = 0;
      for (View view : geometry.descendants(grid))
        if (view instanceof RegistrationCardView.Artwork) {
          artwork(view, RegistrationCardView.COMPACT);
          NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
          assertTrue("큰 글자에서도 후보 그림에 40dp 이상 배정", view.getHeight() >= host.dp(40));
          count++;
        }
      assertEquals(6, count);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test
  public void compatibilityTeamPickerKeepsSelectionBusyGuardAndUpdateCallbacks() {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.selectionMode = "story";
      host.team();
      ArrayList<JSONObject> candidates = new ArrayList<>();
      JSONArray roster = host.list(host.state, "units");
      for (int i = 0; i < roster.length(); i++) candidates.add(roster.optJSONObject(i));
      int[] updates = {0};
      host.menus.teamPicker(false, candidates, () -> updates[0]++);
      View dialog = host.visibleDialog.getWindow().getDecorView();
      View first = dialog.findViewWithTag("lineup-candidate-o1");
      assertNotNull(first);
      int cards = 0;
      for (View view : geometry.descendants(dialog))
        if (view instanceof RegistrationCardView.Artwork) {
          artwork(view, RegistrationCardView.COMPACT);
          cards++;
        }
      assertEquals(10, cards);
      host.busy = true;
      first.performClick();
      assertEquals(List.of("o1", "o2", "o3"), host.menus.teamDraft);
      assertEquals(0, updates[0]);
      host.busy = false;
      first.performClick();
      assertEquals(List.of("o2", "o3"), host.menus.teamDraft);
      assertEquals(1, updates[0]);
      dialog.findViewWithTag("lineup-candidate-o4").performClick();
      assertEquals(List.of("o2", "o3", "o4"), host.menus.teamDraft);
      assertEquals(2, updates[0]);
      assertTrue(api.writes.isEmpty());
      host.visibleDialog.dismiss();
    }
  }

  @Test
  public void recruitmentKeepsQuantityCostsCallbackTagsAndExplicitTutorialAnchor() throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.menus.economy.draw();
      layout(host, 915, 412);
      for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
        View card = host.root.findViewWithTag("draw-card-" + kind);
        Button submit = host.root.findViewWithTag("draw-submit-" + kind);
        TextView total = host.root.findViewWithTag("draw-total-" + kind);
        View quantity = host.root.findViewWithTag("draw-quantity-" + kind);
        assertEquals("지원서 받기", submit.getText().toString());
        assertSame(card, submit.getParent());
        assertSame(card, total.getParent());
        button(quantity, "＋").performClick();
        assertTrue(total.getText().toString().startsWith("2회 · 총 "));
        assertEquals("지원서 받기", submit.getText().toString());
        assertTrue(submit.getContentDescription().toString().contains("2회"));
        assertTrue(quantity.getBottom() <= total.getTop());
        assertTrue(total.getBottom() <= submit.getTop());
        NativeNoScrollReviewTest.assertFullyVisible(submit, host.root);
      }
      Button low = host.root.findViewWithTag("draw-submit-LOW");
      assertSame(low, host.guide.findTag(host.root, "tutorial:draw-go"));
      low.setText("문구가 달라져도 같은 버튼");
      assertSame("안내는 표시 문구 검색에 의존하지 않음", low,
          host.guide.findTag(host.root, "tutorial:draw-go"));
      Button high = host.root.findViewWithTag("draw-submit-HIGH");
      high.performClick();
      TextView prompt = host.visibleDialog.findViewById(android.R.id.message);
      assertTrue(prompt.getText().toString().contains("고급 2회"));
      assertTrue(prompt.getText().toString().contains("10장"));
      assertTrue("확정 전에는 추첨 요청 없음", api.writes.isEmpty());
      host.visibleDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      // AlertDialog 기본 버튼은 Handler로 확인 콜백을 보낸다. PAUSED looper에서 전달을 완료한다.
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(List.of("draw"), api.writes);
      assertEquals("HIGH", api.submitted.get(0).getString("kind"));
      assertEquals(2, api.submitted.get(0).getInt("times"));
    }
  }

  @Test
  public void onlyDrawOperationCopyChangesAndCompletionActionStillAdvancesTutorial() {
    assertEquals("지원서 읽는 중…", NativeOperationUi.label("draw"));
    assertEquals("편성 저장 중…", NativeOperationUi.label("native/team"));
    assertEquals("보상 받는 중…", NativeOperationUi.label("claim"));
    assertEquals("하급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "LOW")));
    assertEquals("중급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "MID")));
    assertEquals("고급 모집", NativeEconomy.recruitmentLabel(MainActivity.obj("kind", "HIGH")));
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      FixtureApi api = prepare(host);
      host.guide.enabled = true;
      host.guide.progress.step = 2;
      host.guide.onAction("draw-complete");
      assertTrue(host.guide.progress.drew);
      assertEquals(3, host.guide.progress.step);
      assertEquals(List.of("tutorial"), api.writes);
    }
  }
}
