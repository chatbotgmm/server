package org.dpdns.gmm;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** 문자열의 이름·명목 피해량 대신 실제 개체 참조와 HP 변경만 요약하는 회귀 검사. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeBattleDebriefTest {
  static JSONObject ref(int side, int slot) {
    return MainActivity.obj("side", side, "slot", slot, "key", "o" + slot, "id", "D1");
  }

  static JSONObject battle(JSONArray events) {
    JSONArray teams = new JSONArray();
    for (int side = 0; side < 2; side++) {
      JSONArray fighters = new JSONArray();
      for (int slot = 0; slot < 3; slot++)
        fighters.put(MainActivity.obj("key", "o" + slot, "id", "D1", "name", "같은 이름"));
      teams.put(MainActivity.obj("fighters", fighters, "augments", new JSONArray()));
    }
    return MainActivity.obj(
        "teams", teams, "displayEvents", events, "displayEventSeq", events.length());
  }

  static JSONObject damage(
      int id, int actor, int target, String reason, int before, int after, double multiplier) {
    return MainActivity.obj(
        "id",
        id,
        "kind",
        "damage",
        "actor",
        ref(actor, 0),
        "target",
        ref(target, 0),
        "reason",
        reason,
        "hpBefore",
        before,
        "hpAfter",
        after,
        "amount",
        999,
        "multiplier",
        multiplier);
  }

  static JSONObject recovery(
      int id, String kind, int actor, int slot, String reason, int before, int after) {
    return MainActivity.obj(
        "id",
        id,
        "kind",
        kind,
        "actor",
        ref(actor, 0),
        "target",
        ref(actor, slot),
        "reason",
        reason,
        "hpBefore",
        before,
        "hpAfter",
        after,
        "amount",
        999);
  }

  @Test
  public void sameNamesAndKeysOnOppositeTeamsNeverMixSidesAndMultihitsCountAsHits() {
    JSONArray events =
        new JSONArray()
            .put(damage(1, 0, 1, "skill", 100, 80, 1.5))
            .put(damage(2, 0, 1, "skill", 80, 60, 1.5))
            .put(damage(3, 1, 0, "skill", 100, 90, .67));
    NativeBattleDebrief report = NativeBattleDebrief.from(battle(events));
    assertEquals(2, report.sides[0].strongHits);
    assertEquals(0, report.sides[1].strongHits);
    assertEquals(1, report.sides[1].weakHits);
    assertEquals(0, report.sides[0].weakHits);
    assertFalse(report.partial);
    assertTrue(report.coverage().contains("타격마다"));
  }

  @Test
  public void actualHpDifferenceExcludesOverkillAndOverhealAndSeparatesRevives() {
    JSONArray events =
        new JSONArray()
            .put(damage(1, 1, 0, "burn", 7, 0, 1))
            .put(damage(2, 0, 1, "thorns", 8, 0, 1))
            .put(recovery(3, "heal", 0, 0, "drain", 95, 100))
            .put(recovery(4, "heal", 0, 1, "firstaid", 80, 100))
            .put(recovery(5, "heal", 0, 0, "regen", 90, 100))
            .put(recovery(6, "revive", 0, 2, "skill", 0, 50));
    NativeBattleDebrief report = NativeBattleDebrief.from(battle(events));
    assertEquals(7, report.sides[0].burnedHp);
    assertEquals(8, report.sides[0].thornHp);
    assertEquals(35, report.sides[0].healedHp);
    assertEquals("재생은 증강체·특성·기술이 합쳐지므로 증강체 기여로 추정하지 않음", 25, report.sides[0].augmentHealedHp);
    assertEquals(1, report.sides[0].revives);
    assertFalse(report.facts(0).toString().contains("999"));
  }

  @Test
  public void invalidOrMissingInstanceReferenceIsNotGuessedFromTextOrFinalMatchup()
      throws Exception {
    JSONObject bad = damage(1, 0, 1, "skill", 100, 60, 1.5);
    bad.getJSONObject("actor").put("key", "stale-instance");
    bad.put("text", "[내 팀] 같은 이름의 공격! 효과가 굉장해요!");
    JSONObject missing = damage(2, 0, 1, "skill", 60, 50, 1.5);
    missing.getJSONObject("actor").remove("id");
    JSONObject noHp = damage(3, 0, 1, "skill", 50, 30, 1.5);
    noHp.remove("hpBefore");
    NativeBattleDebrief report =
        NativeBattleDebrief.from(battle(new JSONArray().put(bad).put(missing).put(noHp)));
    assertEquals(0, report.sides[0].strongHits);
    assertEquals(0, report.sides[1].weakHits);
  }

  @Test
  public void protectAndCounterHaveOwnFactsAndOtherEffectsAreNotCreditedAsAttacks() {
    JSONArray events =
        new JSONArray()
            .put(damage(1, 0, 1, "protect", 100, 100, 1.5))
            .put(damage(2, 0, 1, "counter", 100, 88, 1.5))
            .put(damage(3, 0, 1, "reflect", 88, 85, 1.5));
    NativeBattleDebrief report = NativeBattleDebrief.from(battle(events));
    assertEquals(1, report.sides[1].guardedHits);
    assertEquals(1, report.sides[0].strongHits);
    assertEquals(12, report.sides[0].counterHp);
    assertEquals(0, report.sides[0].thornHp);
  }

  @Test
  public void onlyAppliedOpposingStatusEventsCountNotExpirationCleanseOrBlockedActions() {
    JSONObject applied =
        MainActivity.obj(
            "id",
            1,
            "kind",
            "status",
            "actor",
            ref(1, 0),
            "target",
            ref(0, 0),
            "reason",
            "skill",
            "status",
            MainActivity.obj("burn", 2));
    JSONObject expired =
        MainActivity.obj(
            "id",
            2,
            "kind",
            "status",
            "target",
            ref(0, 0),
            "reason",
            "expire",
            "status",
            MainActivity.obj("burn", 1));
    JSONObject changed =
        MainActivity.obj(
            "id",
            3,
            "kind",
            "status",
            "actor",
            ref(0, 0),
            "target",
            ref(0, 0),
            "reason",
            "camo",
            "status",
            MainActivity.obj("burn", 1));
    JSONObject b = battle(new JSONArray().put(applied).put(expired).put(changed));
    b =
        MainActivity.obj(
            "teams",
            b.optJSONArray("teams"),
            "displayEvents",
            b.optJSONArray("displayEvents"),
            "history",
            new JSONArray().put("같은 이름은 얼어서 움직이지 못해요."));
    NativeBattleDebrief report = NativeBattleDebrief.from(b);
    assertEquals(1, report.sides[0].statusApplications);
    assertEquals(0, report.sides[1].statusApplications);
    assertTrue(report.suggestions().get(0).contains("해제·면역"));
    assertFalse(report.facts(0).toString().contains("행동"));
  }

  @Test
  public void cappedAndGappedStreamsAreExplicitlyRecentAndDuplicateIdsCountOnce() throws Exception {
    JSONArray events = new JSONArray();
    for (int id = 101; id <= 300; id++) events.put(damage(id, 0, 1, "skill", 100, 90, 1.5));
    JSONObject b = battle(events);
    b.put("displayEventSeq", 300);
    NativeBattleDebrief capped = NativeBattleDebrief.from(b);
    assertTrue(capped.partial);
    assertTrue(capped.coverage().contains("최근"));
    assertTrue(capped.preview().startsWith("최근 기록"));
    assertEquals(200, capped.sides[0].strongHits);
    JSONObject hit = damage(1, 0, 1, "skill", 100, 90, 1.5);
    NativeBattleDebrief duplicate =
        NativeBattleDebrief.from(battle(new JSONArray().put(hit).put(hit)));
    assertEquals(1, duplicate.sides[0].strongHits);
    assertTrue(duplicate.partial);
    NativeBattleDebrief gap =
        NativeBattleDebrief.from(
            battle(new JSONArray().put(hit).put(damage(3, 0, 1, "skill", 90, 80, 1.5))));
    assertTrue(gap.partial);
  }

  @Test
  public void missingEventStreamShowsLimitsAndChosenAugmentsWithoutInventedContribution()
      throws Exception {
    JSONObject b = battle(new JSONArray());
    b.getJSONArray("teams")
        .getJSONObject(0)
        .put("augments", new JSONArray().put(MainActivity.obj("id", "lunch", "name", "점심시간")));
    NativeBattleDebrief report = NativeBattleDebrief.from(b);
    assertFalse(report.hasEvents);
    assertEquals(0, report.facts(0).size());
    assertEquals("점심시간", report.augments[0].get(0));
    assertTrue(report.coverage().contains("요약할 수 없어요"));
    assertEquals(0, report.sides[0].augmentHealedHp);
  }
}
