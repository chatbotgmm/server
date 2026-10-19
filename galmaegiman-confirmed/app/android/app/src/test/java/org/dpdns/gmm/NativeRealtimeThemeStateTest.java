package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Offline behavior contracts for the real phase-two views. No live account or API writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRealtimeThemeStateTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRealtimeTest transport = new NativeRealtimeTest();

  @Before public void isolate() {
    transport.isolate(); new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock();
  }

  NativeRealtimeTest.FakeApi install(MainActivity h) {
    NativeRealtimeTest.FakeApi api = transport.install(h);
    h.guide.enabled = false; h.motionEnabled = false;
    h.realtime.loadSaved(); h.realtime.room = "room-theme";
    return api;
  }

  JSONObject preparation() {
    JSONArray available = new JSONArray();
    for (String id : new String[] {"A", "B", "C", "D", "E", "F"})
      available.put(MainActivity.obj("id", id, "name", "갈매미 " + id, "level", 30, "rarity", "일반"));
    return MainActivity.obj("selectedIds", new JSONArray().put("A").put("B").put("C"),
        "available", available, "ready", false, "opponentReady", false);
  }

  JSONObject envelope(String phase) {
    return MainActivity.obj("roomId", "room-theme", "mode", "rank", "opponentKind", "HUMAN",
        "phase", phase, "phaseId", "room-theme:" + phase, "revision", 4,
        "serverNow", 1700000000000L, "deadline", 1700000000000L + (phase.equals("accept") ? 10000 : 30000), "canSubmit", true,
        "mySubmitted", false, "preparation", preparation(),
        "acceptance", MainActivity.obj("accepted", false, "opponentAccepted", false));
  }

  void show(MainActivity h, JSONObject envelope) {
    h.realtime.acceptRoom(envelope);
    flow.measure(h.root);
    h.realtime.tick.run();
    flow.measure(h.root);
  }

  @Test public void rankSearchNeverChangesModeOrRequestsClientAiFallback() {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h); h.realtime.room = "";
      h.realtime.begin("rank"); api.last("pvp/queue/current").ok(MainActivity.obj("status", "idle"));
      NativeRealtimeTest.Request attempt = api.last("pvp/queue/attempt");
      assertEquals("rank", attempt.body.optString("mode")); assertEquals(3, attempt.body.optInt("protocol"));
      attempt.ok(transport.attempt());
      api.last("pvp/queue/join").ok(MainActivity.obj("status", "waiting", "mode", "rank", "serverNow", 40000, "queuedAt", 1000));
      h.realtime.queueFailure("연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", () -> {});
      assertEquals("rank", h.realtime.mode); assertTrue(h.realtime.queueMode.getText().toString().contains("랭크"));
      for (NativeRealtimeTest.Request request : api.requests) {
        assertFalse(request.path.contains("ai"));
        if (request.body != null) assertFalse(request.body.has("opponentKind"));
      }
    }
  }

  RegistrationCardView loadedPreparationCard(MainActivity h, String tag, String id) throws Exception {
    View view = h.root.findViewWithTag(tag); assertTrue(view instanceof RegistrationCardView);
    RegistrationCardView card = (RegistrationCardView) view;
    assertEquals(id, card.picture.id); assertEquals(RegistrationCardView.FULL, card.picture.mode);
    assertNotNull(card.picture.bitmap); assertFalse(card.picture.bitmap.isRecycled());
    assertEquals("", card.picture.status);
    assertEquals(View.GONE, card.name.getVisibility()); assertEquals(View.GONE, card.grade.getVisibility());
    int bucket = RegistrationArt.widthBucket(card.picture.mode, card.picture.getWidth(), card.picture.getHeight());
    android.graphics.Bitmap reference = RegistrationArtTest.expected(h, id, RegistrationCardView.FULL, bucket);
    try { RegistrationArtTest.samePixels("Preparation action " + tag, reference, card.picture.bitmap); }
    finally { reference.recycle(); }
    android.view.accessibility.AccessibilityNodeInfo node = card.createAccessibilityNodeInfo();
    assertTrue(node.getContentDescription().toString().contains("Lv30"));
    assertEquals(Button.class.getName(), node.getClassName().toString());
    return card;
  }

  @Test public void inlinePreparationSwapsOnlyServerCandidatesWithoutOptimisticSave() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      NativeRealtimeThemeReviewTest visual = new NativeRealtimeThemeReviewTest();
      JSONObject r = envelope("prepare"); r.put("preparation", visual.preparation()); show(h, r);
      visual.awaitAssets(h);
      loadedPreparationCard(h, "realtime-prepare-slot-0", "D1");
      loadedPreparationCard(h, "realtime-prepare-candidate-0", "D1");
      h.root.findViewWithTag("realtime-prepare-slot-1").performClick();
      assertNull("Core choice stays on the same page", h.visibleDialog);
      assertEquals(1, h.realtime.preparationSlot);
      h.root.findViewWithTag("realtime-prepare-candidate-0").performClick();
      JSONObject command = api.last("pvp/room/command").body;
      assertEquals("room-theme:prepare", command.getString("phaseId"));
      JSONObject prepare = command.getJSONObject("command").getJSONObject("prepare");
      assertEquals("[\"D2\",\"D1\",\"D3\"]", prepare.getJSONArray("team").toString());
      assertFalse(prepare.getBoolean("ready"));
      assertEquals("[\"D1\",\"D2\",\"D3\"]", h.realtime.latest.getJSONObject("preparation").getJSONArray("selectedIds").toString());
      assertFalse(h.realtime.latest.getJSONObject("preparation").getBoolean("ready"));
      for (View action : h.realtime.phaseActions) assertFalse("All card and button actions lock during writes", action.isEnabled());
      h.realtime.choosePreparationCandidate(r, "D3");
      assertEquals("Duplicate click cannot overwrite the in-flight selection", 1, api.count("pvp/room/command"));
      JSONObject authoritative = new JSONObject(r.toString()); authoritative.put("revision", 5);
      authoritative.getJSONObject("preparation").put("selectedIds", new JSONArray(prepare.getJSONArray("team").toString()));
      api.last("pvp/room/command").ok(authoritative); flow.measure(h.root); visual.awaitAssets(h);
      assertTrue(loadedPreparationCard(h, "realtime-prepare-slot-0", "D2").isEnabled());
      assertTrue(loadedPreparationCard(h, "realtime-prepare-slot-1", "D1").isSelected());
    }
  }

  @Test public void preparationPaginationDoesNotWriteOrResetServerDeadline() {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h); show(h, envelope("prepare"));
      long server = h.realtime.serverAtReceive, elapsed = h.realtime.receivedElapsed;
      h.root.findViewWithTag("realtime-prepare-next").performClick();
      assertEquals(1, h.realtime.preparationPage);
      assertNotNull(h.root.findViewWithTag("realtime-prepare-candidate-3"));
      assertNull(h.root.findViewWithTag("realtime-prepare-candidate-0"));
      h.root.findViewWithTag("realtime-prepare-previous").performClick();
      assertEquals(0, h.realtime.preparationPage);
      assertEquals(server, h.realtime.serverAtReceive); assertEquals(elapsed, h.realtime.receivedElapsed);
      assertEquals(0, api.count("pvp/room/command")); assertNull(h.visibleDialog);
      for (View v : new NativeDialogLayoutReviewTest().descendants(h.root))
        assertFalse("No whole-page or core-choice scroll", v instanceof ScrollView || v instanceof HorizontalScrollView);
    }
  }

  @Test public void inventedCandidateCannotBeSubmitted() {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      JSONObject r = envelope("prepare"); show(h, r);
      h.realtime.choosePreparationCandidate(r, "opponent-secret-choice");
      assertEquals(0, api.count("pvp/room/command"));
    }
  }

  @Test public void expiredPreparationLocksAllEditsWithoutClientAutoChoice() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      JSONObject r = envelope("prepare"); r.put("deadline", r.getLong("serverNow") - 1); show(h, r);
      for (View b : h.realtime.phaseActions) assertFalse(b.isEnabled());
      h.realtime.prepareCommand(r, new JSONArray().put("C").put("B").put("A"), true);
      h.realtime.choosePreparationCandidate(r, "D");
      assertEquals(0, api.count("pvp/room/command"));
      assertEquals("결과 확인 중", h.realtime.clock.getText().toString());
    }
  }

  @Test public void expiredAcceptanceAndStalePhaseCannotSend() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      JSONObject r = envelope("accept"); r.put("deadline", r.getLong("serverNow") - 1); show(h, r);
      h.realtime.acceptMatch(r, true); assertEquals(0, api.count("pvp/room/command"));
      assertFalse(h.root.findViewWithTag("realtime-match-accept").isEnabled());
      JSONObject next = envelope("prepare"); next.put("revision", 5); show(h, next);
      h.realtime.acceptMatch(r, true); assertEquals(0, api.count("pvp/room/command"));
    }
  }

  @Test public void acceptedAndReadyStatesNeverKeepAnInputCountdown() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); install(h);
      JSONObject accept = envelope("accept"); accept.getJSONObject("acceptance").put("accepted", true); show(h, accept);
      assertEquals("상대 수락 대기", h.realtime.clock.getText().toString());
      for (View b : h.realtime.phaseActions) assertFalse(b.isEnabled());
      JSONObject prep = envelope("prepare"); prep.put("revision", 5); prep.getJSONObject("preparation").put("ready", true); show(h, prep);
      assertEquals("상대 준비 대기", h.realtime.clock.getText().toString());
      for (View b : h.realtime.phaseActions) assertFalse(b.isEnabled());
    }
  }

  @Test public void failedPreparationWriteUnlocksSameRevisionWithoutPretendingReady() {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      JSONObject r = envelope("prepare"); show(h, r);
      Button confirm = h.root.findViewWithTag("realtime-prepare-confirm"); confirm.performClick();
      assertFalse(confirm.isEnabled()); assertTrue(h.realtime.writing);
      api.last("pvp/room/command").fail();
      assertFalse(h.realtime.writing); assertTrue(confirm.isEnabled());
      assertFalse(h.realtime.latest.optJSONObject("preparation").optBoolean("ready"));
      assertEquals(1, api.count("pvp/room/current?roomId=room-theme"));
    }
  }

  @Test public void opponentSecretSelectionsAreNeverMounted() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); install(h);
      JSONObject r = envelope("prepare");
      r.put("opponentSelectedIds", new JSONArray().put("SECRET_OPPONENT"));
      r.getJSONObject("preparation").put("opponentTeam", new JSONArray().put(MainActivity.obj("name", "SECRET_OPPONENT")));
      show(h, r);
      assertFalse(flow.labels(h.root).contains("SECRET_OPPONENT"));
      assertTrue(flow.labels(h.root).contains("상대가 팀을 고르고 있어요."));
    }
  }

  /** Deliberately noncanonical values test parameter transport, not current game balance or visual proof. */
  @Test public void syntheticAugmentParametersAndFallbackDescriptionArePreserved() {
    assertEquals("아군 물리·마법 방어\n+27%", NativeAugmentView.coreEffect(MainActivity.obj(
        "id", "thick", "p", MainActivity.obj("pct", 27), "desc", "원문")));
    assertEquals("5턴 동안\n양쪽 모든 상성 ×1.0", NativeAugmentView.coreEffect(MainActivity.obj(
        "id", "neutral", "p", MainActivity.obj("turns", 5), "desc", "원문")));
    assertEquals("서버의 새 효과 설명", NativeAugmentView.coreEffect(MainActivity.obj(
        "id", "new-effect", "desc", "서버의 새 효과 설명")));
    assertEquals("파라미터 없는 실제 설명", NativeAugmentView.coreEffect(MainActivity.obj(
        "id", "drain", "desc", "파라미터 없는 실제 설명")));
  }

  /** Behavior stress fixture only. The visual review supplies its own canonical battle.yaml offers. */
  JSONObject syntheticAugmentBattle() throws Exception {
    JSONObject battle = new JSONObject(new R1ScreenReviewTest().battle().toString());
    battle.put("phase", "choose").put("serverNow", System.currentTimeMillis())
        .put("chooseDeadline", System.currentTimeMillis() + 30000);
    battle.getJSONArray("teams").getJSONObject(0).put("offers", new JSONArray()
        .put(MainActivity.obj("id", "thick", "name", "두꺼운 깃털", "desc", "아군 물리·마법 방어 +27%", "p", MainActivity.obj("pct", 27)))
        .put(MainActivity.obj("id", "dual", "name", "이중 속성", "desc", "기본기·역할 공격에 상대 약점 속성 추가", "rerolled", true))
        .put(MainActivity.obj("id", "neutral", "name", "무속성 지대", "desc", "5턴 동안 양쪽 모든 상성 ×1.0", "p", MainActivity.obj("turns", 5))));
    return battle;
  }

  NativeAugmentView mountAugment(MainActivity h, JSONObject battle) {
    h.battle = battle; h.battleScreen(); flow.measure(h.root);
    NativeAugmentView choice = ((BattleView) h.root.getChildAt(0)).augmentView;
    assertNotNull(choice);
    assertTrue("Choice is mounted in the actual visible window", choice.isAttachedToWindow() && choice.isShown());
    assertTrue(choice.getWidth() > 0 && choice.getHeight() > 0);
    return choice;
  }

  @Test public void largeIconsAndCompleteEffectsStayOnChoicePage() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      NativeAugmentView choice = mountAugment(h, syntheticAugmentBattle());
      assertEquals(3, choice.picks.size()); assertFalse(choice.confirm.isEnabled());
      assertFalse(choice.rerolls.get(1).isEnabled());
      assertTrue(choice.findViewWithTag("augment-icon-thick").getWidth() >= h.dp(56));
      choice.picks.get(0).performClick(); assertTrue(choice.confirm.isEnabled());
      assertEquals("thick", h.pendingAugment); assertNull(h.visibleDialog);
      assertTrue(flow.labels(choice).contains("+27%"));
      assertNull(choice.hint.getParent()); assertNull(choice.timerRule.getParent());
      for (View v : new NativeDialogLayoutReviewTest().descendants(choice)) {
        assertFalse(v instanceof ScrollView || v instanceof HorizontalScrollView);
        if (v instanceof Button) assertFalse(((Button) v).getText().toString().contains("자세히"));
      }
      assertEquals(0, api.count("native/battle/turn")); assertEquals(0, api.count("pvp/room/command"));
    }
  }

  @Test public void switchingUsesOnlyLivingOwnFightersAndKeepsChosenAugment() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); install(h); JSONObject battle = syntheticAugmentBattle();
      battle.getJSONArray("teams").getJSONObject(0).put("needSwitch", true);
      battle.getJSONArray("teams").getJSONObject(1).getJSONArray("fighters").getJSONObject(0).put("name", "SECRET_ENEMY");
      NativeAugmentView choice = mountAugment(h, battle);
      choice.picks.get(0).performClick(); choice.confirm.performClick();
      assertTrue(choice.switching); assertEquals("thick", choice.selected); assertEquals(2, choice.picks.size());
      assertFalse(flow.labels(choice).contains("SECRET_ENEMY")); assertNull(h.visibleDialog);
      choice.picks.get(1).performClick(); assertEquals(1, choice.switchTo); assertTrue(choice.confirm.isEnabled());
      choice.findViewWithTag("augment-back").performClick();
      assertFalse(choice.switching); assertEquals("thick", choice.selected); assertTrue(choice.confirm.isEnabled());
    }
  }

  @Test public void realtimeAugmentLocksWhileSendingSubmittedOrStale() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); install(h); JSONObject battle = syntheticAugmentBattle();
      battle.put("realtime", true).put("session", "room-theme").put("phaseId", "room-theme:choose")
          // acceptRoom/decorate supplies this on the actual server battle snapshot.
          // latest alone is not enough: BattleView correctly gates its own snapshot.
          .put("canSubmit", true).put("mySubmitted", false);
      h.realtime.latest = envelope("choose");
      NativeAugmentView choice = mountAugment(h, battle); assertTrue(choice.canChoose());
      h.realtime.writing = true; assertFalse(choice.canChoose());
      h.realtime.writing = false; h.realtime.latest.put("mySubmitted", true); assertFalse(choice.canChoose());
      h.realtime.latest.put("mySubmitted", false).put("phaseId", "room-theme:next"); assertFalse(choice.canChoose());
    }
  }

  @Test public void expiredAugmentNeverAutoSelectsOrRerolls() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); NativeRealtimeTest.FakeApi api = install(h);
      JSONObject battle = syntheticAugmentBattle(); battle.put("chooseDeadline", System.currentTimeMillis() - 1);
      NativeAugmentView choice = mountAugment(h, battle); choice.tick.run();
      assertFalse(choice.canChoose()); assertFalse(choice.confirm.isEnabled());
      for (View pick : choice.picks) assertFalse(pick.isEnabled());
      for (Button reroll : choice.rerolls) assertFalse(reroll.isEnabled());
      choice.picks.get(0).performClick(); choice.rerolls.get(0).performClick(); choice.submit();
      assertEquals(0, api.count("native/battle/turn")); assertEquals(0, api.count("pvp/room/command"));
    }
  }

  @Test public void pendingSettlementDoesNotFabricateResultAndFinalIsShownOnce() throws Exception {
    try (var activity = flow.start()) {
      MainActivity h = activity.get(); install(h);
      JSONObject r = envelope("over");
      r.put("result", MainActivity.obj("settlement", "pending", "winner", 0)); show(h, r);
      assertNull(h.visibleDialog); assertNotNull(h.root.findViewWithTag("realtime-result-stage"));
      r.put("revision", 5).put("result", MainActivity.obj("settlement", "complete", "winner", 0,
          "rank", MainActivity.obj("tier", "골드", "promotionChange", "PROMOTED", "after", MainActivity.obj("lp", 24))));
      show(h, r); assertNotNull(h.visibleDialog); android.app.AlertDialog dialog = h.visibleDialog;
      assertTrue(flow.labels(dialog.getWindow().getDecorView()).contains("24 LP"));
      h.realtime.terminal(r); assertSame(dialog, h.visibleDialog);
      dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
      // AlertDialog dispatches its listener through a Handler, even after performClick.
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals("", h.realtime.room); assertEquals("room-theme", h.realtime.dismissed);
      assertNull(h.realtime.latest); assertNull(h.battle);
    }
  }
}
