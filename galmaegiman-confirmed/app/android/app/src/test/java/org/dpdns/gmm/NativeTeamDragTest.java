package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 View 계층의 터치 전달부터 기존 저장 API 요청까지 검사한다. 외부 계정은 사용하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTeamDragTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  long downTime;

  @Before
  public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  JSONObject fixture() {
    JSONArray units = new JSONArray(), owned = new JSONArray();
    for (int i = 1; i <= 3; i++) {
      units.put(
          MainActivity.obj(
              "key",
              "same-species-" + i,
              "id",
              "D" + i,
              "name",
              "갈매미 " + i,
              "image",
              "",
              "level",
              12,
              "hp",
              100,
              "maxHp",
              100,
              "xp",
              20,
              "xpNext",
              100,
              "attr",
              "돌",
              "attrName",
              "돌",
              "attrCode",
              "STONE"));
      owned.put(
          MainActivity.obj(
              "id",
              "D" + i,
              "name",
              "갈매미 " + i,
              "top",
              true,
              "attr",
              "STONE",
              "attrName",
              "돌",
              "tierName",
              "상위"));
    }
    return MainActivity.obj(
        "me",
        MainActivity.obj(
            "name",
            "검토",
            "units",
            owned,
            "battleTeam",
            MainActivity.arr(List.of("D1", "D2", "D3"))),
        "units",
        units,
        "team",
        MainActivity.arr(List.of("same-species-1", "same-species-2", "same-species-3")),
        "chapters",
        new JSONArray(),
        "attributes",
        MainActivity.obj("STONE", MainActivity.obj("name", "돌")),
        "typeChart",
        new JSONObject());
  }

  static final class StoredApi extends NativeApi {
    JSONObject stored;
    final List<String> paths = new ArrayList<>();
    final List<JSONObject> submitted = new ArrayList<>();
    boolean reject;

    StoredApi(MainActivity host, JSONObject state) {
      super(host);
      stored = state;
    }

    @Override
    String token() {
      return "isolated-fixture";
    }

    @Override
    void call(String path, JSONObject body, Result callback) {
      paths.add(path);
      if (body != null) {
        submitted.add(body);
        if (reject) {
          callback.done(null, "저장할 수 없어요.");
          return;
        }
        try {
          if (path.equals("native/team")) stored.put("team", body.getJSONArray("keys"));
          else if (path.equals("battle-team"))
            stored.getJSONObject("me").put("battleTeam", body.getJSONArray("ids"));
          else fail("편성 이외 쓰기 금지: " + path);
        } catch (JSONException e) {
          throw new AssertionError(e);
        }
      }
      callback.done(path.equals("native/state") ? stored : new JSONObject(), null);
    }
  }

  StoredApi prepare(MainActivity host, boolean pvp) {
    host.api.close();
    StoredApi api = new StoredApi(host, fixture());
    host.api = api;
    host.state = api.stored;
    host.art.key = "isolated-fixture";
    host.guide.enabled = false;
    host.selectionMode = pvp ? "pvp" : "story";
    host.menus.teamChart = false;
    return api;
  }

  void layout(MainActivity host) {
    review.viewport(host, 915, 412);
    review.preparePreDraw(
        host.root,
        () -> {
          review.force(host.root);
          flow.measure(host.root);
        });
  }

  Rect box(MainActivity host, View view) {
    Rect rect = new Rect();
    view.getDrawingRect(rect);
    host.root.offsetDescendantRectToMyCoords(view, rect);
    return rect;
  }

  void touch(MainActivity host, int action, float x, float y) {
    if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
    else Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
    long time = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(downTime, time, action, x, y, 0);
    try {
      host.root.dispatchTouchEvent(event);
    } finally {
      event.recycle();
    }
  }

  void drag(MainActivity host, View from, View to, boolean wait) {
    Rect start = box(host, from), end = box(host, to);
    touch(host, MotionEvent.ACTION_DOWN, start.exactCenterX(), start.exactCenterY());
    if (wait)
      Shadows.shadowOf(Looper.getMainLooper())
          .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 32));
    touch(host, MotionEvent.ACTION_MOVE, end.exactCenterX(), end.exactCenterY());
    touch(host, MotionEvent.ACTION_UP, end.exactCenterX(), end.exactCenterY());
    layout(host);
  }

  @Test
  public void holdingThenDraggingPodiumUsesWholeCardAndPersistsExactOrderOnlyAfterSave()
      throws Exception {
    for (boolean pvp : new boolean[] {false, true}) {
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity host = controller.get();
        StoredApi api = prepare(host, pvp);
        host.team();
        layout(host);
        ViewGroup first = host.root.findViewWithTag("lineup-slot-0");
        ViewGroup last = host.root.findViewWithTag("lineup-slot-2");
        // ImageView가 카드의 OnTouchListener를 가로채지 않는 실제 root dispatch 경로.
        drag(
            host,
            last.findViewWithTag("lineup-portrait"),
            first.findViewWithTag("lineup-portrait"),
            true);
        List<String> expected =
            pvp
                ? List.of("D3", "D1", "D2")
                : List.of("same-species-3", "same-species-1", "same-species-2");
        assertEquals(expected, host.menus.teamDraft);
        assertTrue(host.menus.isTeamDirty());
        assertTrue("놓기만 해서는 서버에 저장하지 않음", api.submitted.isEmpty());
        Button save = host.root.findViewWithTag("tutorial:team-save");
        assertTrue(save.isEnabled());
        save.performClick();
        assertEquals(pvp ? "battle-team" : "native/team", api.paths.get(0));
        assertEquals(
            new JSONArray(expected).toString(),
            api.submitted.get(0).getJSONArray(pvp ? "ids" : "keys").toString());
        assertEquals(
            new JSONArray(expected).toString(),
            (pvp
                    ? api.stored.getJSONObject("me").getJSONArray("battleTeam")
                    : api.stored.getJSONArray("team"))
                .toString());
        // 저장 응답 이후 재진입해도 확정된 선봉을 그대로 복원한다.
        host.menus.teamDraft = null;
        host.team();
        layout(host);
        assertEquals(expected, host.menus.teamDraft);
        assertFalse(host.menus.isTeamDirty());
      }
    }
  }

  @Test
  public void storyGripDragImmediatelySavesInstanceKeysWhileBodyStillOpensGrowth()
      throws Exception {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      StoredApi api = prepare(host, false);
      host.menus.story(false);
      layout(host);
      View first = host.root.findViewWithTag("story-team-unit-same-species-1");
      View last = host.root.findViewWithTag("story-team-unit-same-species-3");
      View firstGrip = first.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
      View lastGrip = last.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
      assertEquals(host.dp(48), lastGrip.getWidth());
      assertEquals(host.dp(48), lastGrip.getHeight());
      drag(host, lastGrip, firstGrip, true);
      assertEquals(List.of("native/team", "native/state"), api.paths);
      assertEquals(
          "[\"same-species-3\",\"same-species-1\",\"same-species-2\"]",
          api.stored.getJSONArray("team").toString());
      assertEquals("story", host.screen);
      assertNull("끌기 때문에 상세/순서 창을 열지 않음", host.visibleDialog);
      ViewGroup lead = host.root.findViewWithTag("story-team-unit-same-species-3");
      View body = null;
      for (View child : new NativeDialogLayoutReviewTest().descendants(lead))
        if (child instanceof TextView && ((TextView) child).getText().toString().equals("갈매미 3"))
          body = child;
      assertNotNull("구조 인덱스와 무관하게 실제 부착된 갈매미 이름 본문을 누른다", body);
      NativeNoScrollReviewTest.assertFullyVisible(body, host.root);
      Rect point = box(host, body);
      Rect handle = box(host, lead.findViewWithTag(NativeTeamReorder.HANDLE_TAG));
      assertFalse(
          "본문 터치는 순서 손잡이 밖에서 실행",
          handle.contains((int) point.exactCenterX(), (int) point.exactCenterY()));
      touch(host, MotionEvent.ACTION_DOWN, point.exactCenterX(), point.exactCenterY());
      touch(host, MotionEvent.ACTION_UP, point.exactCenterX(), point.exactCenterY());
      layout(host);
      assertEquals("unit", host.screen);
      assertEquals("same-species-3", host.currentKey);
      assertEquals(1, api.submitted.size());
    }
  }

  @Test
  public void gripTapOffersAccessibleOrderMenuAndCardTapAloneRemovesMember() {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      StoredApi api = prepare(host, false);
      host.team();
      layout(host);
      ViewGroup last = host.root.findViewWithTag("lineup-slot-2");
      View grip = last.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
      assertTrue(grip.isFocusable());
      Rect point = box(host, grip);
      touch(host, MotionEvent.ACTION_DOWN, point.exactCenterX(), point.exactCenterY());
      touch(host, MotionEvent.ACTION_UP, point.exactCenterX(), point.exactCenterY());
      assertEquals(3, host.menus.teamDraft.size());
      AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      assertNotNull(dialog);
      dialog.getListView().performItemClick(null, 0, 0);
      assertEquals(
          List.of("same-species-3", "same-species-1", "same-species-2"), host.menus.teamDraft);
      assertTrue(api.submitted.isEmpty());
      layout(host);
      last = host.root.findViewWithTag("lineup-slot-2");
      point = box(host, last.findViewWithTag("lineup-portrait"));
      touch(host, MotionEvent.ACTION_DOWN, point.exactCenterX(), point.exactCenterY());
      touch(host, MotionEvent.ACTION_UP, point.exactCenterX(), point.exactCenterY());
      assertEquals(List.of("same-species-3", "same-species-1"), host.menus.teamDraft);
      assertTrue(api.submitted.isEmpty());
    }
  }

  @Test
  public void cancelOutsideDropCrossAxisScrollAndBusyStateNeverChangeOrRemoveMembers() {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      StoredApi api = prepare(host, false);
      host.team();
      layout(host);
      List<String> saved = new ArrayList<>(host.menus.teamDraft);
      ViewGroup first = host.root.findViewWithTag("lineup-slot-0");
      Rect point = box(host, first.findViewWithTag("lineup-portrait"));
      float x = point.exactCenterX(), y = point.exactCenterY();
      touch(host, MotionEvent.ACTION_DOWN, x, y);
      touch(host, MotionEvent.ACTION_MOVE, x + 160, y);
      touch(host, MotionEvent.ACTION_CANCEL, x + 160, y);
      assertEquals(saved, host.menus.teamDraft);
      assertEquals(0, first.getTranslationX(), .001);
      touch(host, MotionEvent.ACTION_DOWN, x, y);
      touch(host, MotionEvent.ACTION_MOVE, x + 160, y);
      touch(host, MotionEvent.ACTION_UP, 2000, -50);
      assertEquals(saved, host.menus.teamDraft);
      assertEquals(0, first.getTranslationX(), .001);
      touch(host, MotionEvent.ACTION_DOWN, x, y);
      touch(host, MotionEvent.ACTION_MOVE, x + 1, y + 55);
      touch(host, MotionEvent.ACTION_UP, x + 1, y + 55);
      assertEquals("세로 스크롤 제스처를 제외/재정렬로 처리하지 않음", saved, host.menus.teamDraft);
      touch(host, MotionEvent.ACTION_DOWN, x, y);
      touch(host, MotionEvent.ACTION_MOVE, x + 160, y);
      host.busy = true;
      touch(host, MotionEvent.ACTION_UP, x + 160, y);
      host.busy = false;
      assertEquals(saved, host.menus.teamDraft);
      assertEquals(0, first.getTranslationX(), .001);
      assertTrue(api.submitted.isEmpty());
    }
  }

  @Test
  public void rejectedSaveRetainsDraggedDraftAndConfirmedOrder() {
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get();
      StoredApi api = prepare(host, false);
      host.team();
      layout(host);
      ViewGroup first = host.root.findViewWithTag("lineup-slot-0");
      ViewGroup last = host.root.findViewWithTag("lineup-slot-2");
      drag(
          host,
          last.findViewWithTag("lineup-portrait"),
          first.findViewWithTag("lineup-portrait"),
          false);
      api.reject = true;
      ((Button) host.root.findViewWithTag("tutorial:team-save")).performClick();
      assertEquals(
          List.of("same-species-3", "same-species-1", "same-species-2"), host.menus.teamDraft);
      assertEquals(
          List.of("same-species-1", "same-species-2", "same-species-3"), host.menus.teamSavedIds);
      assertTrue(host.menus.isTeamDirty());
      assertFalse(host.busy);
      assertEquals("team", host.screen);
    }
  }

  @Test
  public void compactPvpShowsEveryCardAnd48DpGripWithLongNamesAndFont130() throws Exception {
    try {
      for (int width : new int[] {915, 1006}) {
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h412dp-land-mdpi");
        RuntimeEnvironment.setFontScale(1.3f);
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get();
          StoredApi api = prepare(host, true);
          JSONArray owned = api.stored.getJSONObject("me").getJSONArray("units");
          owned.getJSONObject(0).put("name", "태초의 갈매미맨");
          owned.getJSONObject(1).put("name", "민상의 수호자");
          owned.getJSONObject(2).put("name", "보리의 수호자");
          host.team();
          review.viewport(host, width, 412);
          review.preparePreDraw(
              host.root,
              () -> {
                review.force(host.root);
                host.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY));
                host.root.layout(0, 0, width, 412);
              });
          for (int slot = 0; slot < 3; slot++) {
            View card = host.root.findViewWithTag("lineup-slot-" + slot);
            Rect visible = new Rect();
            assertTrue(card.getGlobalVisibleRect(visible));
            assertEquals("큰 글자에도 편성 카드 전체 표시", card.getHeight(), visible.height());
            View grip = card.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
            assertTrue(grip.getWidth() >= host.dp(48));
            assertTrue(grip.getHeight() >= host.dp(48));
          }
        }
      }
    } finally {
      RuntimeEnvironment.setFontScale(1);
    }
  }
}
