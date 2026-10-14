package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 표시 이름과 로그 문구가 같아도 서버 대상·실제 체력·이벤트 번호만 사용한다. 서버 연결 없음. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BattlePlaybackTest {
  @Before
  public void noAccountNetworkRequests() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_account", 0)
        .edit()
        .clear()
        .commit();
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .commit();
  }

  ActivityController<MainActivity> activity() {
    ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    c.get().motionEnabled = false;
    return c;
  }

  JSONObject ref(int side, int slot) {
    return MainActivity.obj(
        "side", side, "slot", slot, "key", "team" + side + "-" + slot, "id", "D1");
  }

  JSONObject fighter(int side, int slot, int hp) {
    return MainActivity.obj(
        "key",
        "team" + side + "-" + slot,
        "id",
        "D1",
        "name",
        "같은 갈매미",
        "level",
        11,
        "hp",
        hp,
        "maxHp",
        100,
        "attr",
        "돌",
        "attrCode",
        "STONE",
        "status",
        new JSONObject(),
        "skills",
        new JSONArray().put(MainActivity.obj("id", "same-attack", "name", "같은 기술", "power", 40)));
  }

  JSONObject snapshot(long sequence) {
    JSONArray teams = new JSONArray();
    for (int s = 0; s < 2; s++)
      teams.put(
          MainActivity.obj(
              "active",
              0,
              "fighters",
              new JSONArray().put(fighter(s, 0, 100)).put(fighter(s, 1, 40)),
              "augments",
              new JSONArray()));
    return MainActivity.obj(
        "session",
        "test-session",
        "revision",
        1,
        "phase",
        "action",
        "turn",
        1,
        "displayEventVersion",
        1,
        "displayEventSeq",
        sequence,
        "displayEvents",
        new JSONArray(),
        "displayLogStart",
        0,
        "log",
        new JSONArray().put("대결 시작!"),
        "teams",
        teams);
  }

  BattleView battle(MainActivity a, JSONObject snapshot) {
    a.battle = snapshot;
    BattleView view = new BattleView(a, snapshot);
    a.root.removeAllViews();
    a.root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    return view;
  }

  JSONObject event(long id, String kind, JSONObject actor, JSONObject target) {
    return MainActivity.obj(
        "id",
        id,
        "turn",
        1,
        "kind",
        kind,
        "actor",
        actor,
        "target",
        target,
        "text",
        "같은 갈매미가 999 피해를 입었어요.",
        "logIndex",
        1);
  }

  JSONObject hpEvent(
      long id, String kind, int actorSide, int targetSide, int slot, int before, int after) {
    JSONObject e = event(id, kind, ref(actorSide, 0), ref(targetSide, slot));
    try {
      e.put("hpBefore", before)
          .put("hpAfter", after)
          .put("maxHp", 100)
          .put("amount", Math.abs(before - after));
    } catch (JSONException ex) {
      throw new AssertionError(ex);
    }
    return e;
  }

  JSONObject next(BattleView v, long sequence, JSONArray events) throws JSONException {
    JSONObject next = new JSONObject(v.battle.toString());
    next.put("revision", v.battle.optInt("revision") + 1)
        .put("displayEventSeq", sequence)
        .put("displayEvents", events)
        .put("log", new JSONArray().put("대결 시작!").put("같은 갈매미가 999 피해를 입었어요."));
    return next;
  }

  JSONObject fighter(JSONObject snapshot, int side, int slot) {
    return snapshot
        .optJSONArray("teams")
        .optJSONObject(side)
        .optJSONArray("fighters")
        .optJSONObject(slot);
  }

  @Test
  public void mirrorMatchUsesExactSideAndRecordNotName() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      JSONObject skill = event(1, "skill", ref(1, 0), ref(0, 0));
      skill.put("skillId", "same-attack").put("skillName", "같은 기술").put("attr", "STONE");
      assertTrue(v.playback.cue(skill));
      assertEquals("상대의 동일 이름 기술", 1, v.playback.side);
      assertEquals("attack", v.playback.effect);
      assertTrue(v.playback.cue(hpEvent(2, "damage", 1, 0, 0, 100, 73)));
      assertEquals(0, v.playback.side);
      assertEquals(73, v.active(0).optInt("hp"));
      assertEquals(100, v.active(1).optInt("hp"));
      assertEquals("로그의 999가 아닌 실제 감소량", "−27", v.playback.label);
      assertTrue(v.playback.cue(hpEvent(3, "damage", 0, 1, 0, 100, 64)));
      assertEquals(1, v.playback.side);
      assertEquals(64, v.active(1).optInt("hp"));
    }
  }

  @Test
  public void reflectionAndCounterUseExplicitTarget() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      assertTrue(v.playback.cue(hpEvent(1, "damage", 0, 1, 0, 100, 80)));
      JSONObject reflect = hpEvent(2, "damage", 1, 0, 0, 100, 94);
      reflect.put("reason", "reflect").put("text", "역습! 같은 갈매미가 20 피해를 입었어요.");
      assertTrue(v.playback.cue(reflect));
      assertEquals(0, v.playback.side);
      assertEquals("−6", v.playback.label);
      assertEquals(94, v.active(0).optInt("hp"));
      assertEquals(80, v.active(1).optInt("hp"));
    }
  }

  @Test
  public void multiHitUsesEveryHpTransitionInOrder() {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      for (int hit = 1; hit <= 3; hit++) {
        assertTrue(
            v.playback.cue(hpEvent(hit, "damage", 0, 1, 0, 100 - (hit - 1) * 8, 100 - hit * 8)));
        assertEquals(100 - hit * 8, v.active(1).optInt("hp"));
        assertEquals("−8", v.playback.label);
      }
      assertEquals(100, v.active(0).optInt("hp"));
    }
  }

  @Test
  public void cappedHealingAndBenchHealingDoNotAffectOtherFighters() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      v.active(0).put("hp", 98);
      assertTrue(v.playback.cue(hpEvent(1, "heal", 0, 0, 0, 98, 100)));
      assertEquals("+2", v.playback.label);
      assertEquals(100, v.active(0).optInt("hp"));
      assertTrue(v.playback.cue(hpEvent(2, "heal", 0, 0, 1, 40, 65)));
      assertEquals("대기 칸 회복은 출전 캐릭터에 입자를 표시하지 않음", "none", v.playback.effect);
      assertEquals(65, fighter(v.battle, 0, 1).optInt("hp"));
      assertEquals(100, v.active(0).optInt("hp"));
    }
  }

  @Test
  public void switchAppliesHpToIncomingSlotAndLeavesOutgoingSlotAlone() {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      assertTrue(v.playback.cue(event(1, "switch", ref(0, 0), ref(0, 1))));
      assertEquals(1, v.side(0).optInt("active"));
      assertEquals("enter", v.playback.effect);
      assertTrue(v.playback.cue(hpEvent(2, "damage", 1, 0, 1, 40, 22)));
      assertEquals(22, v.active(0).optInt("hp"));
      assertEquals(100, fighter(v.battle, 0, 0).optInt("hp"));
      assertTrue(v.playback.cue(hpEvent(3, "heal", 1, 0, 0, 100, 100)));
      assertEquals("none", v.playback.effect);
      assertEquals(22, v.active(0).optInt("hp"));
    }
  }

  @Test
  public void wrongRecordAndStaleHpAreRejectedWithoutChangingHealth() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      JSONObject bad = hpEvent(1, "damage", 0, 1, 0, 100, 70);
      bad.optJSONObject("target").put("key", "replaced-record");
      assertFalse(v.playback.cue(bad));
      bad.optJSONObject("target").put("key", "team1-0").put("id", "D2");
      assertFalse(v.playback.cue(bad));
      bad.optJSONObject("target").put("id", "D1").put("slot", 99);
      assertFalse(v.playback.cue(bad));
      bad.optJSONObject("target").put("slot", 0);
      bad.put("hpBefore", 99);
      assertFalse(v.playback.cue(bad));
      assertEquals(100, v.active(1).optInt("hp"));
      bad.put("hpBefore", 100).put("amount", 999);
      assertFalse(v.playback.cue(bad));
      assertEquals(100, v.active(1).optInt("hp"));
    }
  }

  @Test
  public void statusAndBenchRevivalUseTargetSlot() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity a = c.get();
      a.state.put(
          "attributes",
          MainActivity.obj(
              "ICE", MainActivity.obj("name", "얼음"), "SEA", MainActivity.obj("name", "바다")));
      BattleView v = battle(a, snapshot(0));
      JSONObject state = event(1, "status", ref(0, 0), ref(1, 0));
      state.put("status", MainActivity.obj("freeze", 2)).put("attr", "ICE").put("extraAttr", "SEA");
      assertTrue(v.playback.cue(state));
      assertEquals(1, v.playback.side);
      assertEquals(2, v.active(1).optJSONObject("status").optInt("freeze"));
      assertEquals("얼음", v.active(1).optString("attr"));
      assertEquals("바다", v.active(1).optString("extraAttr"));
      assertEquals(0, v.active(0).optJSONObject("status").length());
      state.put("status", new JSONObject()).put("extraAttr", JSONObject.NULL);
      assertTrue(v.playback.cue(state));
      assertEquals(0, v.active(1).optJSONObject("status").length());
      assertTrue(v.active(1).isNull("extraAttr"));
      fighter(v.battle, 0, 1).put("hp", 0);
      assertTrue(v.playback.cue(hpEvent(2, "revive", 0, 0, 1, 0, 40)));
      assertEquals(40, fighter(v.battle, 0, 1).optInt("hp"));
      assertEquals("none", v.playback.effect);
    }
  }

  @Test
  public void comboKeepsItsEffectWithoutParsingLog() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      JSONObject skill = event(1, "skill", ref(1, 0), ref(0, 0));
      skill.put("skillId", "combo-bird").put("skillName", "함께 날기").put("text", "어떤 문구든 무관");
      assertTrue(v.playback.cue(skill));
      assertEquals("combo", v.playback.effect);
      assertEquals(1, v.playback.side);
      assertEquals("합동기 · 함께 날기", v.playback.label);
    }
  }

  @Test
  public void repeatedResponseDoesNotReplayOrDeductHealthAgain() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      JSONObject next = next(v, 1, new JSONArray().put(hpEvent(1, "damage", 0, 1, 0, 100, 70)));
      fighter(next, 1, 0).put("hp", 70);
      AtomicInteger done = new AtomicInteger();
      v.playback.start(next, done::incrementAndGet);
      assertTrue(v.playback.playing);
      assertEquals(70, v.active(1).optInt("hp"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
      assertEquals(1, done.get());
      assertFalse(v.playback.playing);
      v.playback.start(next, done::incrementAndGet);
      assertFalse(v.playback.playing);
      assertEquals(2, done.get());
      assertEquals(70, v.active(1).optInt("hp"));
      assertEquals(1, v.playback.cursor.seen());
    }
  }

  @Test
  public void eventGapAndLegacyResponseApplySnapshotWithoutGuessing() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity a = c.get();
      BattleView v = battle(a, snapshot(5));
      JSONObject next = next(v, 8, new JSONArray().put(hpEvent(8, "damage", 0, 1, 0, 100, 70)));
      fighter(next, 1, 0).put("hp", 70);
      AtomicInteger done = new AtomicInteger();
      v.playback.start(next, done::incrementAndGet);
      assertFalse(v.playback.playing);
      assertEquals(1, done.get());
      assertEquals(70, v.active(1).optInt("hp"));
      assertEquals(70, fighter(a.battle, 1, 0).optInt("hp"));
      JSONObject legacy = next(v, 0, new JSONArray());
      legacy.remove("displayEventVersion");
      legacy.remove("displayEvents");
      legacy.remove("displayEventSeq");
      fighter(legacy, 0, 0).put("hp", 58);
      v.playback.start(legacy, done::incrementAndGet);
      assertFalse(v.playback.playing);
      assertEquals(2, done.get());
      assertEquals(58, v.active(0).optInt("hp"));
      assertEquals(70, v.active(1).optInt("hp"));
    }
  }

  @Test
  public void invalidRefSkipsRemainingAnimationAndRestoresFullSnapshot() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      BattleView v = battle(c.get(), snapshot(0));
      JSONObject damage = hpEvent(1, "damage", 0, 1, 0, 100, 70);
      damage.optJSONObject("target").put("key", "old-key");
      JSONObject next = next(v, 1, new JSONArray().put(damage));
      fighter(next, 1, 0).put("hp", 70);
      AtomicInteger done = new AtomicInteger();
      v.playback.start(next, done::incrementAndGet);
      assertFalse(v.playback.playing);
      assertEquals(1, done.get());
      assertEquals(70, v.active(1).optInt("hp"));
      assertFalse(c.get().busy);
    }
  }

  @Test
  public void detachDuringPlaybackKeepsConfirmedServerStateAndDoesNotRunCallback()
      throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity a = c.get();
      BattleView v = battle(a, snapshot(0));
      JSONArray events =
          new JSONArray()
              .put(hpEvent(1, "damage", 0, 1, 0, 100, 70))
              .put(hpEvent(2, "damage", 1, 0, 0, 100, 65));
      JSONObject next = next(v, 2, events);
      fighter(next, 1, 0).put("hp", 70);
      fighter(next, 0, 0).put("hp", 65);
      AtomicInteger done = new AtomicInteger();
      v.playback.start(next, done::incrementAndGet);
      assertTrue(v.playback.playing);
      a.root.removeView(v);
      assertFalse(v.playback.playing);
      assertFalse(a.busy);
      assertEquals(0, done.get());
      assertEquals(65, fighter(a.battle, 0, 0).optInt("hp"));
      assertEquals(70, fighter(a.battle, 1, 0).optInt("hp"));
      assertEquals(2, v.playback.cursor.seen());
      BattleView reconnect = battle(a, a.battle);
      reconnect.playback.start(next, done::incrementAndGet);
      assertFalse(reconnect.playback.playing);
      assertEquals(1, done.get());
    }
  }

  @Test
  public void delayedOlderResponseCannotInterruptConfirmedPendingTurn() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity a = c.get();
      BattleView v = battle(a, snapshot(0));
      JSONObject confirmed =
          next(
              v,
              2,
              new JSONArray()
                  .put(hpEvent(1, "damage", 0, 1, 0, 100, 70))
                  .put(hpEvent(2, "damage", 1, 0, 0, 100, 60)));
      confirmed.put("revision", 3);
      fighter(confirmed, 0, 0).put("hp", 60);
      fighter(confirmed, 1, 0).put("hp", 70);
      AtomicInteger done = new AtomicInteger();
      v.playback.start(confirmed, done::incrementAndGet);
      assertTrue(v.playback.playing);
      assertEquals(1, v.playback.cursor.seen());
      JSONObject delayed = next(v, 1, new JSONArray().put(hpEvent(1, "damage", 0, 1, 0, 100, 70)));
      fighter(delayed, 1, 0).put("hp", 70);
      v.playback.start(delayed, () -> fail("뒤늦은 이전 응답은 완료 콜백도 호출하지 않음"));
      assertTrue(v.playback.playing);
      assertTrue(a.busy);
      assertEquals(View.INVISIBLE, v.controls.getVisibility());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
      assertEquals(1, done.get());
      assertEquals(3, a.battle.optInt("revision"));
      assertEquals(60, v.active(0).optInt("hp"));
      assertEquals(70, v.active(1).optInt("hp"));
    }
  }

  @Test
  public void staleSessionAndRevisionNeverReplaceCurrentBattle() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity a = c.get();
      BattleView v = battle(a, snapshot(4));
      AtomicInteger done = new AtomicInteger();
      JSONObject next = next(v, 5, new JSONArray().put(hpEvent(5, "damage", 0, 1, 0, 100, 70)));
      next.put("session", "previous-session");
      v.playback.start(next, done::incrementAndGet);
      assertEquals(0, done.get());
      assertEquals("test-session", a.battle.optString("session"));
      next.put("session", "test-session").put("revision", 0);
      v.playback.start(next, done::incrementAndGet);
      assertEquals(0, done.get());
      next.put("revision", 2).put("displayEventSeq", 3);
      v.playback.start(next, done::incrementAndGet);
      assertEquals(0, done.get());
      assertEquals(100, v.active(1).optInt("hp"));
      assertEquals(View.VISIBLE, v.controls.getVisibility());
    }
  }
}
