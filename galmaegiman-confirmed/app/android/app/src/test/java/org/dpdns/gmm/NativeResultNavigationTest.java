package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 보상 돌아보기에서 실제 편성으로 이동해도 결과 지급·성장·전투를 재실행하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeResultNavigationTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
    new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock();
  }

  JSONObject state() {
    JSONObject first = fixtures.fighter("o11"), second = fixtures.fighter("o12");
    try {
      first.put("stage", 0).put("xp", 12).put("xpNext", 100).put("stats", new JSONObject());
      second
          .put("stage", 1)
          .put("level", 23)
          .put("xp", 81)
          .put("xpNext", 200)
          .put("stats", new JSONObject());
    } catch (JSONException error) {
      throw new AssertionError(error);
    }
    return MainActivity.obj(
        "me",
            MainActivity.obj(
                "nickname",
                "결과 이동 검토",
                "credits",
                100,
                "units",
                new JSONArray()
                    .put(
                        MainActivity.obj(
                            "id",
                            "D1",
                            "name",
                            "갈매미",
                            "top",
                            true,
                            "tierName",
                            "한정",
                            "attr",
                            "STONE",
                            "attrName",
                            "돌",
                            "have",
                            2)),
                "battleTeam",
                new JSONArray().put("D1")),
        "team", new JSONArray().put("o11").put("o12"),
        "units", new JSONArray().put(first).put(second),
        "practiceUnits",
            new JSONArray()
                .put(MainActivity.obj("id", "D1", "name", "갈매미", "attr", "돌", "image", "")),
        "attributes", new JSONObject(),
        "typeChart", new JSONObject(),
        "synergies", new JSONObject());
  }

  static final class FakeApi extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final JSONObject state;
    String error;

    FakeApi(MainActivity host, JSONObject state) {
      super(host);
      this.state = state;
    }

    @Override
    String token() {
      return "local-result-navigation";
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      assertEquals("결과에서 편성으로 이동할 때 상태만 읽기", "native/state", path);
      assertNull("저장·지급·성장·새 전투 요청 없음", body);
      done.done(error == null ? state : null, error);
    }
  }

  FakeApi connect(MainActivity host) {
    host.api.close();
    FakeApi api = new FakeApi(host, state());
    host.api = api;
    host.state = api.state;
    host.art.key = "local-review";
    host.guide.enabled = false;
    host.screen = "battle";
    return api;
  }

  JSONObject ended(String mode, boolean dialogue) throws JSONException {
    JSONObject ended = fixtures.battle("ended-" + mode);
    JSONObject result = MainActivity.obj("won", true, "xp", 0);
    if (dialogue) {
      result.put("dialog", new JSONArray().put(MainActivity.obj("who", "N", "say", "전투 뒤 이야기예요.")));
      result.put(
          "chapterClear", new JSONArray().put(MainActivity.obj("who", "N", "say", "챕터를 마쳤어요.")));
    }
    return ended
        .put("phase", "over")
        .put("mode", mode)
        .put("stage", "1-6")
        .put("winner", 0)
        .put("result", result);
  }

  AlertDialog openTeamFromReport(MainActivity host, JSONObject ended) {
    host.battle = ended;
    host.showBattleResult(ended);
    AlertDialog result = host.visibleDialog;
    assertNotNull(result);
    View decor = result.getWindow().getDecorView();
    View details = fixtures.tag(decor, "reward-debrief-details");
    assertEquals("보상·성장을 먼저 보여 주도록 기본 접힘", View.GONE, details.getVisibility());
    ((Button) fixtures.tag(decor, "reward-debrief-toggle")).performClick();
    assertEquals(View.VISIBLE, details.getVisibility());
    Button team = (Button) fixtures.tag(decor, "reward-team");
    assertNotNull("실제 편성 이동 버튼 제공", team);
    team.performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    assertFalse("편성 이동 전 보상창 닫기", result.isShowing());
    return result;
  }

  @Test
  public void storyWaitsForResultDialogueThenOpensExactOwnedKeysAndReturnsToStages()
      throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get();
      FakeApi api = connect(host);
      String saved = host.state.toString();
      openTeamFromReport(host, ended("story", true));
      assertEquals("dialog", host.screen);
      assertNull(host.battle);
      assertTrue("결과 대사를 끝내기 전 상태 조회·편성 변경 없음", api.requests.isEmpty());
      StoryView story = (StoryView) host.root.getChildAt(0);
      assertEquals("대사와 챕터 마무리 모두 유지", 2, story.lines.length());
      story.finish();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals("team", host.screen);
      assertEquals("story", host.selectionMode);
      assertEquals("stages", host.menus.teamReturnTarget());
      assertEquals(Arrays.asList("o11", "o12"), host.menus.teamDraft);
      assertEquals(
          "같은 종류 두 개체를 종 ID로 합치지 않음", Arrays.asList("o11", "o12"), host.menus.teamSavedIds);
      assertEquals(Collections.singletonList("GET native/state"), api.requests);
      assertEquals("읽기 이동으로 레벨·경험치·재화·저장 편성을 변경하지 않음", saved, host.state.toString());
      assertFalse(host.busy);
    }
  }

  @Test
  public void normalAndRankOpenSpeciesTeamAndReturnToModesWithoutNewMatch() throws Exception {
    for (String mode : new String[] {"normal", "rank"}) {
      try (var controller = fixtures.start()) {
        MainActivity host = controller.get();
        FakeApi api = connect(host);
        String saved = host.state.toString();
        openTeamFromReport(host, ended(mode, false));
        assertEquals("team", host.screen);
        assertEquals("pvp", host.selectionMode);
        assertEquals("modes", host.menus.teamReturnTarget());
        assertEquals(Collections.singletonList("D1"), host.menus.teamDraft);
        assertEquals(Collections.singletonList("GET native/state"), api.requests);
        assertEquals("보상이나 점수를 재처리하지 않음", saved, host.state.toString());
        assertNull(host.battle);
        assertFalse(host.busy);
      }
    }
  }

  @Test
  public void practiceReturnsToPracticeRosterWithoutReadingOrWritingSavedTeam() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get();
      FakeApi api = connect(host);
      String saved = host.state.toString();
      List<String> roster = new ArrayList<>(host.practiceRoster);
      openTeamFromReport(host, ended("practice", false));
      assertEquals("practice", host.screen);
      assertEquals(roster, host.practiceRoster);
      assertEquals("연습 결과는 계정 조회나 저장을 요청하지 않음", Collections.emptyList(), api.requests);
      assertEquals(saved, host.state.toString());
      assertNull(host.battle);
      assertFalse(host.busy);
    }
  }

  @Test
  public void failedRosterRefreshKeepsRecordsAndRetryOnlyReadsCurrentState() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get();
      FakeApi api = connect(host);
      String saved = host.state.toString();
      api.error = "검토용 연결 실패";
      openTeamFromReport(host, ended("story", false));
      assertEquals(Collections.singletonList("GET native/state"), api.requests);
      assertEquals("조회 실패에도 성장·재화·원래 편성 보존", saved, host.state.toString());
      assertEquals(Arrays.asList("o11", "o12"), host.menus.teamDraft);
      assertFalse(host.busy);
      AlertDialog reconnect = host.visibleDialog;
      assertNotNull(reconnect);
      assertEquals("다시 연결", reconnect.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
      api.error = null;
      reconnect.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals(Arrays.asList("GET native/state", "GET native/state"), api.requests);
      assertEquals("team", host.screen);
      assertEquals("story", host.selectionMode);
      assertEquals("stages", host.menus.teamReturnTarget());
      assertEquals(saved, host.state.toString());
    }
  }
}
