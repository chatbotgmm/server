package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Manual draft regression tests; not executed. All transport callbacks are controlled. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRealtimeTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  @Before public void isolate() {
    fixtures.isolate();
    org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("native_realtime", 0).edit().clear().commit();
  }

  static final class Request {
    final String path; final JSONObject body; final NativeApi.Result callback;
    Request(String p, JSONObject b, NativeApi.Result c) { path = p; body = b; callback = c; }
    void ok(JSONObject value) { callback.done(value, null); }
    void fail() { callback.done(null, "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."); }
  }
  static final class FakeApi extends NativeApi {
    final MainActivity h;
    final List<Request> requests = new ArrayList<>();
    FakeApi(MainActivity activity) { super(activity); h = activity; }
    @Override String token() { return "realtime-test-account"; }
    @Override void call(String path, JSONObject body, Result callback) {
      if (path.equals("native/state")) { callback.done(h.state, null); return; }
      requests.add(new Request(path, body, callback));
    }
    Request last(String path) {
      for (int i = requests.size() - 1; i >= 0; i--) if (requests.get(i).path.equals(path)) return requests.get(i);
      throw new AssertionError("Missing request: " + path);
    }
    long count(String path) { return requests.stream().filter(r -> r.path.equals(path)).count(); }
  }
  FakeApi install(MainActivity h) {
    if (h.realtime != null) h.realtime.close();
    h.getSharedPreferences("native_realtime", 0).edit().clear().commit();
    h.api.close(); FakeApi api = new FakeApi(h); h.api = api;
    h.art.key = "local-review";
    // Recovery requires a loaded authenticated state as well as the fake API token.
    h.state = MainActivity.obj("me", MainActivity.obj("nickname", "실시간 테스트"));
    h.realtime = new NativeRealtime(h); h.realtime.resumed = true;
    return api;
  }
  JSONObject attempt() { return MainActivity.obj("attemptId", "attempt-1", "token", "opaque-test-token",
      "joinNotAfter", 9999999999999L, "serverNow", 1000); }

  @Test public void cancelDuringInertPrepareNeverJoinsAfterLateResponse() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      Request prepare = api.last("pvp/queue/attempt");
      assertFalse(h.busy);
      h.realtime.cancel(); prepare.ok(attempt());
      assertEquals(0, api.count("pvp/queue/join"));
      assertEquals(0, api.count("pvp/queue/cancel"));
      assertFalse(h.realtime.cancelling);
    }
  }
  @Test public void cancelDuringJoinHonoursMatchedRaceWithoutSurrender() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      api.last("pvp/queue/attempt").ok(attempt());
      Request join = api.last("pvp/queue/join");
      h.realtime.cancel(); Request cancel = api.last("pvp/queue/cancel");
      join.ok(MainActivity.obj("status", "waiting", "attemptId", "attempt-1"));
      assertTrue(h.realtime.cancelling);
      cancel.ok(MainActivity.obj("status", "matched", "roomId", "room-1"));
      assertEquals("room-1", h.realtime.room);
      assertEquals(1, api.count("pvp/room/current?roomId=room-1"));
      assertEquals(0, api.count("pvp/room/surrender"));
    }
  }
  @Test public void unknownCancelRetriesSameRequestIdentity() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      api.last("pvp/queue/attempt").ok(attempt());
      h.realtime.cancel(); Request first = api.last("pvp/queue/cancel");
      first.fail();
      assertTrue(h.realtime.cancelling); assertEquals("attempt-1", h.realtime.attempt);
      h.realtime.sendCancel(); Request second = api.last("pvp/queue/cancel");
      assertEquals(first.body.optString("requestId"), second.body.optString("requestId"));
      assertEquals(2, api.count("pvp/queue/cancel"));
    }
  }
  @Test public void staleRenderedPhaseCannotSubmitToNewTurn() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved();
      h.realtime.room = "room-1";
      h.realtime.latest = MainActivity.obj("phaseId", "room-1:2", "canSubmit", true);
      JSONObject old = MainActivity.obj("realtime", true, "session", "room-1", "phaseId", "room-1:1");
      h.realtime.submit(old, MainActivity.obj("action", MainActivity.obj("kind", "skill", "skillId", "basic")));
      assertEquals(0, api.count("pvp/room/command"));
      assertEquals(1, api.count("pvp/room/current?roomId=room-1"));
    }
  }
  @Test public void noPollCanOvertakePendingJoin() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      api.last("pvp/queue/attempt").ok(attempt());
      h.realtime.refresh();
      assertEquals(1, api.count("pvp/queue/current"));
    }
  }
  @Test public void cancellationDuringRecoveryWaitsForServerAcknowledgment() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); h.realtime.cancel();
      assertTrue(h.realtime.cancelling);
      api.last("pvp/queue/current").ok(MainActivity.obj("status", "waiting",
          "attemptId", "attempt-1", "token", "opaque-test-token"));
      assertEquals(1, api.count("pvp/queue/cancel"));
      assertEquals(0, api.count("pvp/queue/attempt"));
    }
  }
  @Test public void idleAfterUncertainJoinMustTombstoneKnownAttempt() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      api.last("pvp/queue/attempt").ok(attempt());
      api.last("pvp/queue/join").fail(); h.realtime.refresh();
      api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      assertTrue(h.realtime.cancelling);
      assertEquals("attempt-1", api.last("pvp/queue/cancel").body.optString("attemptId"));
    }
  }
  @Test public void transportBusyDoesNotPersistDisabledSnapshot() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.room = "room-1";
      h.realtime.writing = true;
      JSONObject b = h.realtime.decorate(MainActivity.obj("battle", new JSONObject(),
          "canSubmit", true, "phaseId", "room-1:1"));
      assertTrue(b.optBoolean("canSubmit"));
    }
  }
  @Test public void preparationSaveUsesAuthoritativeOpaquePhaseAndDoesNotMarkReadyLocally() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved();
      h.realtime.room = "room-1";
      JSONObject shown = MainActivity.obj("roomId", "room-1", "phase", "prepare",
          "phaseId", "room-1:prepare:1", "preparation", MainActivity.obj("ready", false));
      h.realtime.latest = shown;
      org.json.JSONArray team = new org.json.JSONArray(); team.put("A"); team.put("B"); team.put("C");
      h.realtime.prepareCommand(shown, team, true);
      Request request = api.last("pvp/room/command");
      assertEquals("room-1:prepare:1", request.body.optString("phaseId"));
      assertTrue(request.body.optJSONObject("command").optJSONObject("prepare").optBoolean("ready"));
      assertFalse(h.realtime.latest.optJSONObject("preparation").optBoolean("ready"));
    }
  }
  @Test public void expiredPreparationCannotSubmitIntoAugmentPhase() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved(); h.realtime.room = "room-1";
      h.realtime.latest = MainActivity.obj("roomId", "room-1", "phase", "choose", "phaseId", "room-1:2");
      JSONObject old = MainActivity.obj("roomId", "room-1", "phase", "prepare", "phaseId", "room-1:1");
      h.realtime.prepareCommand(old, new org.json.JSONArray(), true);
      assertEquals(0, api.count("pvp/room/command"));
    }
  }
  @Test public void duplicateItemClickWhileSendingCannotConsumeTwice() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved(); h.realtime.room = "room-1";
      h.realtime.latest = MainActivity.obj("phaseId", "room-1:2", "canSubmit", true);
      JSONObject shown = MainActivity.obj("realtime", true, "session", "room-1", "phaseId", "room-1:2");
      JSONObject item = MainActivity.obj("action", MainActivity.obj("kind", "item", "item", "potion"));
      h.realtime.submit(shown, item); h.realtime.submit(shown, item);
      assertEquals(1, api.count("pvp/room/command"));
      assertEquals("item", api.last("pvp/room/command").body.optJSONObject("command").optJSONObject("action").optString("kind"));
    }
  }

  @Test public void queueTimeUsesOnlyConfirmedServerWait() {
    assertEquals("대기 시간 01:05", NativeRealtime.queueTimeLabel(65));
    assertFalse(NativeRealtime.transientQueueError("입력 정보를 확인해 주세요."));
    assertFalse(NativeRealtime.transientQueueError("앱을 업데이트해 주세요."));
    assertTrue(NativeRealtime.transientQueueError("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."));
  }
  @Test public void aiDisclosureUsesOnlyAuthoritativeOpponentKind() {
    assertTrue(NativeRealtime.isAiRoom(MainActivity.obj("opponentKind", "AI")));
    assertFalse(NativeRealtime.isAiRoom(MainActivity.obj("opponentKind", "HUMAN", "name", "AI")));
    assertFalse(NativeRealtime.isAiRoom(null));
    assertEquals("AI 대전 · 일반 MMR에 반영되지 않아요", NativeRealtime.AI_NOTICE);
  }
  @Test public void queuePaperHasOnlyCancelAndNoUnconfirmedClock() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.mode = "normal";
      h.realtime.showQueue("상대를 찾고 있어요");
      assertNotNull(h.root.findViewWithTag("realtime-queue-paper"));
      assertEquals("매칭 취소", h.realtime.cancelButton.getText().toString());
      assertTrue(h.realtime.cancelButton.isEnabled());
      h.realtime.cancelling = true; h.realtime.showQueue("매칭 취소 확인 중…");
      assertFalse(h.realtime.cancelButton.isEnabled());
      h.realtime.queueError("연결 확인 필요");
      assertNull(h.root.findViewWithTag("realtime-queue-retry"));
      assertEquals(android.view.View.GONE, h.realtime.queueElapsed.getVisibility());
      assertFalse(h.realtime.queuePolicyView.getText().toString().contains("30초"));
    }
  }

  @Test public void matchmakingUsesAcceptanceProtocolThree() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.begin("normal");
      api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      assertEquals(3, api.last("pvp/queue/attempt").body.optInt("protocol"));
    }
  }
  @Test public void acceptanceIsNotOptimisticAndDuplicateClicksAreBlocked() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved(); h.realtime.room = "room-1";
      JSONObject shown = MainActivity.obj("roomId", "room-1", "phase", "accept", "phaseId", "room-1:accept",
          "acceptance", MainActivity.obj("accepted", false, "opponentAccepted", false));
      h.realtime.latest = shown; h.realtime.acceptMatch(shown, true); h.realtime.acceptMatch(shown, true);
      assertEquals(1, api.count("pvp/room/command"));
      assertTrue(api.last("pvp/room/command").body.optJSONObject("command").optBoolean("accept"));
      assertFalse(h.realtime.latest.optJSONObject("acceptance").optBoolean("accepted"));
    }
  }
  @Test public void opponentDeclineRequeuesWithoutSurrenderOrBattle() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved(); h.realtime.room = "room-1";
      h.realtime.acceptRoom(MainActivity.obj("roomId", "room-1", "mode", "normal", "revision", 2,
          "result", MainActivity.obj("reason", "MATCH_DECLINED", "requeued", true)));
      assertEquals("", h.realtime.room); assertNull(h.battle);
      assertEquals(1, api.count("pvp/queue/current")); assertEquals(0, api.count("pvp/room/surrender"));
    }
  }
  @Test public void requeuedSearchRecoversAfterProcessDeathBeforeQueueReply() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved(); h.realtime.room = "room-1";
      h.realtime.finishAcceptance(MainActivity.obj("reason", "MATCH_TIMEOUT", "requeued", true));
      assertTrue(h.getSharedPreferences("native_realtime", 0).getBoolean("queueRecoveryNeeded", false));
      h.realtime.close();
      h.realtime = new NativeRealtime(h); h.realtime.resumed = true; h.realtime.recover();
      assertEquals(2, api.count("pvp/queue/current"));
      api.last("pvp/queue/current").ok(MainActivity.obj("status", "waiting", "mode", "normal",
          "attemptId", "again", "token", "again-token"));
      assertFalse(h.getSharedPreferences("native_realtime", 0).getBoolean("queueRecoveryNeeded", true));
      assertEquals("again", h.realtime.attempt);
      assertNotNull(h.realtime.queueMode);
      assertNotNull(h.realtime.cancelButton);
      assertTrue(h.realtime.cancelButton.isEnabled());
    }
  }
  @Test public void cancelDuringIdentifierFreeRequeueRecoveryWaitsForAcknowledgment() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved();
      h.realtime.queueRecoveryNeeded = true; h.realtime.save(); h.realtime.recover();
      h.realtime.cancel();
      assertTrue(h.realtime.cancelling); assertEquals(0, api.count("pvp/queue/cancel"));
      api.last("pvp/queue/current").ok(MainActivity.obj("status", "waiting", "mode", "normal",
          "attemptId", "again", "token", "again-token"));
      assertEquals(1, api.count("pvp/queue/cancel"));
      assertTrue(h.realtime.cancelling);
    }
  }
  @Test public void rejectedProtocolStopsBeforeJoinAndHidesTimer() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h);
      h.realtime.begin("normal"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      api.last("pvp/queue/attempt").callback.done(null, "입력 정보를 확인해 주세요.");
      assertEquals(0, api.count("pvp/queue/join"));
      assertTrue(h.realtime.queueStopped); assertFalse(h.realtime.queueConfirmed);
      assertEquals(android.view.View.GONE, h.realtime.queueElapsed.getVisibility());
      assertTrue(h.realtime.queueText.getText().toString().contains("앱과 서버 버전"));
      assertEquals("돌아가기", h.realtime.cancelButton.getText().toString());
      assertEquals(0, h.realtime.queueRetryCount);
    }
  }
  @Test public void transientQueueRecoveryIsCappedAndCancelRemainsAvailable() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      for (int i = 0; i < 5; i++) h.realtime.queueFailure("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", () -> {});
      assertEquals(3, h.realtime.queueRetryCount);
      assertTrue(h.realtime.queueStopped);
      assertTrue(h.realtime.cancelButton.isEnabled());
      assertEquals(android.view.View.GONE, h.realtime.queueElapsed.getVisibility());
    }
  }

  @Test public void pendingAutomaticRecoverySurvivesPauseAndResume() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      int[] retries = {0};
      h.realtime.queueFailure("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", () -> retries[0]++);
      h.realtime.pause(); assertTrue(h.realtime.queueRetryPending);
      h.realtime.resume();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofSeconds(1));
      assertEquals(1, retries[0]); assertFalse(h.realtime.queueRetryPending);
    }
  }

  @Test public void automaticRecoveryUsesTwoFourEightSecondBackoffOnly() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      int[] calls = {0}; Runnable[] retry = new Runnable[1];
      retry[0] = () -> { calls[0]++; h.realtime.queueFailure(
          "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", retry[0]); };
      h.realtime.queueFailure("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", retry[0]);
      var main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
      main.idleFor(java.time.Duration.ofSeconds(1)); assertEquals(0, calls[0]);
      main.idleFor(java.time.Duration.ofSeconds(1)); assertEquals(1, calls[0]);
      main.idleFor(java.time.Duration.ofSeconds(4)); assertEquals(2, calls[0]);
      main.idleFor(java.time.Duration.ofSeconds(8)); assertEquals(3, calls[0]);
      main.idleFor(java.time.Duration.ofSeconds(30)); assertEquals(3, calls[0]);
      assertTrue(h.realtime.queueStopped); assertFalse(h.realtime.queueRetryPending);
    }
  }

  @Test public void preparingAcknowledgmentDoesNotResetFailedJoinBudget() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); FakeApi api = install(h); h.realtime.loadSaved();
      h.realtime.queueRetryCount = 2;
      h.realtime.acceptQueue(MainActivity.obj("status", "preparing", "attemptId", "a", "token", "t"));
      api.last("pvp/queue/join").fail();
      assertEquals(3, h.realtime.queueRetryCount);
      assertTrue(h.realtime.queueRetryPending);
    }
  }

  @Test public void waitingCopyChangesOnlyOnTapAndNeverOpensDialog() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      h.realtime.queueConfirmed = true; h.realtime.showQueue(NativeRealtime.WAITING_LINES[0]);
      var dialog = h.visibleDialog;
      h.realtime.tick.run();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofSeconds(30));
      assertEquals(NativeRealtime.WAITING_LINES[0], h.realtime.queueText.getText().toString());
      for (int i = 1; i <= NativeRealtime.WAITING_LINES.length; i++) {
        h.realtime.queueText.performClick();
        assertEquals(NativeRealtime.WAITING_LINES[i % NativeRealtime.WAITING_LINES.length],
            h.realtime.queueText.getText().toString());
        assertSame(dialog, h.visibleDialog);
      }
    }
  }
  @Test public void pollingPreservesTappedWaitingCopy() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      h.realtime.acceptQueue(MainActivity.obj("status", "waiting"));
      h.realtime.queueText.performClick();
      h.realtime.acceptQueue(MainActivity.obj("status", "waiting"));
      assertEquals(NativeRealtime.WAITING_LINES[1], h.realtime.queueText.getText().toString());
    }
  }
  @Test public void queueErrorTextDoesNotRotateOrOpenDialogOnTap() {
    try (var activity = fixtures.start()) {
      MainActivity h = activity.get(); install(h); h.realtime.loadSaved();
      h.realtime.queueError("입력 정보를 확인해 주세요.");
      String text = h.realtime.queueText.getText().toString(); var dialog = h.visibleDialog;
      assertFalse(h.realtime.queueText.isClickable()); h.realtime.queueText.performClick();
      assertEquals(text, h.realtime.queueText.getText().toString());
      assertSame(dialog, h.visibleDialog);
    }
  }

}
