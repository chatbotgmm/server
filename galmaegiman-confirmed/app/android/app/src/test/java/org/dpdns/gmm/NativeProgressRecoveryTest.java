package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 응답을 놓친 소비 행동을 다시 보내지 않고 읽기 요청으로 진행을 복구한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeProgressRecoveryTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  static final class FakeApi extends NativeApi {
    final List<String> requests = new ArrayList<>();
    JSONObject current;
    Result deferred;
    boolean deferState;

    FakeApi(MainActivity h, JSONObject current) {
      super(h);
      this.current = current;
    }

    @Override
    String token() {
      return "local-recovery-test";
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      if (path.equals("native/battle/turn")) {
        done.done(null, "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.");
      } else if (path.equals("native/battle/current")) {
        done.done(current, null);
      } else if (path.equals("native/state") && deferState) {
        deferred = done;
      } else if (path.equals("native/state")) {
        done.done(MainActivity.obj("me", new JSONObject()), null);
      } else if (path.equals("daily")) {
        done.done(new JSONObject(), null);
      } else if (path.equals("draw") && body == null) {
        // 로비에 붙을 때 뽑기권 배지가 다음 지급 시간을 읽는다. 뽑기는 실행하지 않는다.
        done.done(MainActivity.obj("wait", 3600000), null);
      } else fail("예상하지 못한 요청: " + path);
    }
  }

  @Test
  public void lostTurnResponseReadsLatestBattleWithoutRepeatingAction() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      h.api.close();
      JSONObject latest = fixtures.battle("recovered").put("revision", 9);
      FakeApi api = new FakeApi(h, latest);
      h.api = api;
      h.battle = fixtures.battle("recovered");
      h.battleScreen();
      h.battleTurn(
          MainActivity.obj("action", MainActivity.obj("kind", "skill", "skillId", "basic")));
      assertNotNull(h.visibleDialog);
      h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(
          Arrays.asList("POST native/battle/turn", "GET native/battle/current"), api.requests);
      assertEquals("battle", h.screen);
      assertEquals(9, h.battle.optInt("revision"));
      assertFalse(h.busy);
      assertNull(h.operations.overlay);
    }
  }

  @Test
  public void completedBattleRecoversAccountStateWithoutNewBattle() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      h.api.close();
      FakeApi api = new FakeApi(h, MainActivity.obj("session", JSONObject.NULL));
      h.api = api;
      h.art.key = "local-review";
      h.battle = fixtures.battle("ended");
      h.refreshProgress("battle");
      assertEquals("GET native/battle/current", api.requests.get(0));
      assertTrue(api.requests.contains("GET native/state"));
      assertTrue("홈의 출석 배지도 읽기 요청만 사용", api.requests.stream().allMatch(p -> p.startsWith("GET ")));
      assertEquals("home", h.screen);
      assertNull(h.battle);
      assertFalse(h.busy);
    }
  }

  @Test
  public void destroyedActivityIgnoresDelayedStateResponse() {
    var controller = fixtures.start();
    MainActivity h = controller.get();
    h.api.close();
    FakeApi api = new FakeApi(h, new JSONObject());
    api.deferState = true;
    h.api = api;
    h.state = MainActivity.obj("me", MainActivity.obj("nickname", "기존"));
    h.load("home");
    assertNotNull(api.deferred);
    controller.pause().stop().destroy();
    api.deferred.done(MainActivity.obj("me", MainActivity.obj("nickname", "지연 응답")), null);
    assertEquals("기존", h.me().optString("nickname"));
    assertNull(h.visibleDialog);
    assertNull(h.operations.overlay);
    assertEquals(Collections.singletonList("GET native/state"), api.requests);
  }
}
