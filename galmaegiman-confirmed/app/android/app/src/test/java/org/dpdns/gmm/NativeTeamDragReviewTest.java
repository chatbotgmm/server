package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 격리된 실제 서버에 순서를 저장하고 재조회하며 휴대폰/태블릿 실제 네이티브 캡처를 남긴다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTeamDragReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  long downTime;

  @Before
  public void setup() {
    review.scheduledFramesUseThePausedLooperClock();
  }

  List<String> keys(JSONArray array) {
    ArrayList<String> out = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) out.add(array.optString(i));
    return out;
  }

  /** 준비·원상복구 전용. 실제 드래그/저장 버튼은 아래에서 기존 앱 request 경로를 사용한다. */
  void save(MainActivity host, List<String> keys) throws Exception {
    review.await(() -> !host.busy);
    AtomicBoolean done = new AtomicBoolean();
    AtomicReference<String> error = new AtomicReference<>();
    host.api.call(
        "native/team",
        MainActivity.obj("keys", MainActivity.arr(keys)),
        (result, problem) -> {
          error.set(problem);
          done.set(true);
        });
    review.await(done::get);
    assertNull("격리된 검토 계정의 편성 준비/원상복구 요청 실패: " + error.get(), error.get());
  }

  void load(MainActivity host, String screen, int[] size) throws Exception {
    host.menus.teamDraft = null;
    review.viewport(host, size[0], size[1]);
    host.load(screen);
    review.await(
        () ->
            !host.busy
                && host.screen.equals(screen)
                && host.menus.pendingFetches == 0
                && host.art.idle());
    review.viewport(host, size[0], size[1]);
  }

  Rect box(MainActivity host, View view) {
    Rect box = new Rect();
    view.getDrawingRect(box);
    host.root.offsetDescendantRectToMyCoords(view, box);
    return box;
  }

  void touch(MainActivity host, int action, float x, float y) {
    long time = SystemClock.uptimeMillis();
    if (action == MotionEvent.ACTION_DOWN) downTime = time;
    MotionEvent event = MotionEvent.obtain(downTime, time, action, x, y, 0);
    host.root.dispatchTouchEvent(event);
    event.recycle();
  }

  void drag(MainActivity host, View from, View to, String name, int[] size) throws Exception {
    Rect start = box(host, from), end = box(host, to);
    assertTrue("실제 화면 안의 출발 손잡이", start.top >= 0 && start.bottom <= size[1]);
    assertTrue("실제 화면 안의 도착 손잡이", end.top >= 0 && end.bottom <= size[1]);
    touch(host, MotionEvent.ACTION_DOWN, start.exactCenterX(), start.exactCenterY());
    Shadows.shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 32));
    touch(host, MotionEvent.ACTION_MOVE, end.exactCenterX(), end.exactCenterY());
    review.capture(host, name + "-dragging", size[0], size[1]);
    assertNull("길게 잡고 끌 때 순서 창이 가로채지 않음", host.visibleDialog);
    touch(host, MotionEvent.ACTION_UP, end.exactCenterX(), end.exactCenterY());
    review.await(() -> !host.busy && host.menus.pendingFetches == 0);
    review.viewport(host, size[0], size[1]);
  }

  @Test
  public void captureStoryImmediateSaveAndTeamDraftDragWithServerReadbackOnPhoneAndTablet()
      throws Exception {
    assumeTrue(!System.getProperty("gmm.nativeReviewOutput", "").isEmpty());
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      List<String> original = keys(host.list(host.state, "team"));
      ArrayList<String> members = new ArrayList<>();
      JSONArray roster = host.list(host.state, "units");
      for (int i = 0; i < roster.length() && members.size() < 3; i++) {
        JSONObject unit = roster.optJSONObject(i);
        if (!unit.optString("key").isEmpty()) members.add(unit.optString("key"));
      }
      assumeTrue("드래그 검토에 실제 보유 개체 2마리 이상 필요", members.size() > 1 && !original.isEmpty());
      try {
        for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
          String device = size[0] == 915 ? "phone" : "tablet";
          RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
          save(host, members);
          load(host, "story", size);
          String storyName = "order75-" + device + "-story";
          review.capture(host, storyName + "-before", size[0], size[1]);
          View first = host.root.findViewWithTag("story-team-unit-" + members.get(0));
          View last =
              host.root.findViewWithTag("story-team-unit-" + members.get(members.size() - 1));
          drag(
              host,
              last.findViewWithTag(NativeTeamReorder.HANDLE_TAG),
              first.findViewWithTag(NativeTeamReorder.HANDLE_TAG),
              storyName,
              size);
          List<String> changed =
              NativeTeamPodiumReorder.orderAfterMove(members, members.size() - 1, 0);
          assertEquals(changed, keys(host.list(host.state, "team")));
          review.capture(host, storyName + "-after", size[0], size[1]);
          // 실제 서버를 다시 읽어 Activity 메모리에만 순서가 바뀐 것이 아님을 확인한다.
          load(host, "story", size);
          assertEquals(changed, keys(host.list(host.state, "team")));

          host.selectionMode = "story";
          host.menus.teamDraft = null;
          host.team();
          review.await(() -> host.art.idle());
          review.viewport(host, size[0], size[1]);
          String teamName = "order75-" + device + "-team";
          review.capture(host, teamName + "-before", size[0], size[1]);
          ViewGroup firstSlot = host.root.findViewWithTag("lineup-slot-0");
          ViewGroup lastSlot = host.root.findViewWithTag("lineup-slot-" + (members.size() - 1));
          drag(
              host,
              lastSlot.findViewWithTag("lineup-portrait"),
              firstSlot.findViewWithTag("lineup-portrait"),
              teamName,
              size);
          assertTrue(host.menus.isTeamDirty());
          assertEquals("저장 전에는 스토리 확정 순서를 변경하지 않음", changed, keys(host.list(host.state, "team")));
          List<String> draft = new ArrayList<>(host.menus.teamDraft);
          review.capture(host, teamName + "-after", size[0], size[1]);
          ((Button) host.root.findViewWithTag("tutorial:team-save")).performClick();
          review.await(() -> !host.busy && host.menus.teamDraft == null);
          load(host, "story", size);
          assertEquals(draft, keys(host.list(host.state, "team")));
        }
      } finally {
        host.menus.teamDraft = null;
        save(host, original);
      }
    }
  }
}
