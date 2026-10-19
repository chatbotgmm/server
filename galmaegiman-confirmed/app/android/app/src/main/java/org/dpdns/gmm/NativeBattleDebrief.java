package org.dpdns.gmm;

import java.util.*;
import org.json.*;

/** 확정 이벤트만 돌아본다. 이름·최종 상성·기술 설명으로 지나간 전투를 재계산하지 않는다. */
final class NativeBattleDebrief {
  static final class Side {
    int strongHits, weakHits, guardedHits, statusApplications, revives;
    long healedHp, burnedHp, augmentHealedHp, thornHp, counterHp;
  }

  final Side[] sides = {new Side(), new Side()};
  final List<String>[] augments;
  final boolean hasEvents, partial;

  @SuppressWarnings("unchecked")
  private NativeBattleDebrief(JSONObject battle) {
    augments = new List[] {new ArrayList<String>(), new ArrayList<String>()};
    JSONArray teams = array(battle, "teams");
    for (int side = 0; side < 2; side++) {
      JSONArray selected = array(teams.optJSONObject(side), "augments");
      for (int i = 0; i < selected.length(); i++) {
        JSONObject a = selected.optJSONObject(i);
        String name = a == null ? "" : a.optString("name");
        if (!name.isBlank() && !augments[side].contains(name)) augments[side].add(name);
      }
    }
    JSONArray events = array(battle, "displayEvents");
    TreeMap<Long, JSONObject> unique = new TreeMap<>();
    boolean incomplete = false;
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      long id = e == null ? 0 : e.optLong("id");
      if (id <= 0 || unique.putIfAbsent(id, e) != null) incomplete = true;
    }
    hasEvents = !unique.isEmpty();
    long previous = 0;
    for (Map.Entry<Long, JSONObject> entry : unique.entrySet()) {
      if (entry.getKey() != previous + 1) incomplete = true;
      previous = entry.getKey();
      JSONObject e = entry.getValue();
      int actor = sideOf(e.optJSONObject("actor"), teams);
      int target = sideOf(e.optJSONObject("target"), teams);
      String kind = e.optString("kind"), reason = e.optString("reason");
      long loss = hpChange(e, false), gain = hpChange(e, true);
      if (kind.equals("damage") && target >= 0) {
        if (reason.equals("burn")) sides[target].burnedHp += loss;
        if (actor >= 0 && actor != target) {
          if (reason.equals("protect")
              && e.has("hpBefore")
              && e.has("hpAfter")
              && e.optLong("hpBefore", -1) == e.optLong("hpAfter", -2)) sides[target].guardedHits++;
          if (loss > 0 && (reason.equals("skill") || reason.equals("counter"))) {
            double mult = e.optDouble("multiplier", Double.NaN);
            if (Double.isFinite(mult) && mult > 0) {
              if (mult > 1) sides[actor].strongHits++;
              else if (mult < 1) sides[actor].weakHits++;
            }
          }
          if (reason.equals("thorns")) sides[actor].thornHp += loss;
          if (reason.equals("counter")) sides[actor].counterHp += loss;
        }
      } else if (kind.equals("heal") && target >= 0) {
        sides[target].healedHp += gain;
        if (actor >= 0 && actor == target && (reason.equals("drain") || reason.equals("firstaid")))
          sides[target].augmentHealedHp += gain;
      } else if (kind.equals("revive") && target >= 0 && gain > 0) {
        sides[target].revives++;
      } else if (kind.equals("status")
          && actor >= 0
          && target >= 0
          && actor != target
          && (reason.equals("skill") || reason.equals("rustle"))
          && hasStatus(e.optJSONObject("status"))) {
        sides[target].statusApplications++;
      }
    }
    partial = incomplete || battle.optLong("displayEventSeq", previous) > previous;
  }

  static NativeBattleDebrief from(JSONObject battle) {
    return new NativeBattleDebrief(battle == null ? new JSONObject() : battle);
  }

  String coverage() {
    if (!hasEvents) return "이 전투에는 상세 이벤트 기록이 없어 수치를 요약할 수 없어요.";
    return partial
        ? "최근에 남은 기록 기준이에요. 긴 전투의 앞부분은 합계에 포함되지 않을 수 있어요."
        : "서버에 확정된 전투 기록 기준이에요. 다단 기술은 타격마다 셌어요.";
  }

  String preview() {
    if (!hasEvents) return "선택한 증강체와 다음 전투 준비를 확인해요.";
    List<String> parts = new ArrayList<>();
    if (sides[0].strongHits > 0) parts.add("내 유리한 타격 " + sides[0].strongHits + "회");
    if (sides[0].weakHits > 0) parts.add("내 불리한 타격 " + sides[0].weakHits + "회");
    if (parts.size() < 2 && sides[0].burnedHp > 0)
      parts.add("받은 화상 피해 " + sides[0].burnedHp + " HP");
    if (parts.size() < 2 && sides[0].healedHp > 0)
      parts.add("내 실제 회복 " + sides[0].healedHp + " HP");
    String summary = parts.isEmpty() ? "상성·상태 이상·회복 기록을 확인해요." : String.join(" · ", parts);
    return partial ? "최근 기록 · " + summary : summary;
  }

  List<String> facts(int side) {
    Side s = sides[side];
    List<String> rows = new ArrayList<>();
    if (!hasEvents) return rows;
    rows.add("상성 타격 · 유리 " + s.strongHits + "회 / 불리 " + s.weakHits + "회");
    if (s.guardedHits > 0) rows.add("완전 방어 " + s.guardedHits + "회");
    if (s.healedHp > 0) rows.add("실제 회복 " + s.healedHp + " HP");
    if (s.revives > 0) rows.add("부활 " + s.revives + "회");
    if (s.statusApplications > 0) rows.add("받은 상태 이상 적용 " + s.statusApplications + "회");
    if (s.burnedHp > 0) rows.add("받은 화상 피해 " + s.burnedHp + " HP");
    if (s.augmentHealedHp > 0) rows.add("증강체 직접 회복 " + s.augmentHealedHp + " HP");
    if (s.thornHp > 0) rows.add("가시 깃털로 돌려준 피해 " + s.thornHp + " HP");
    if (s.counterHp > 0) rows.add("역습 피해 " + s.counterHp + " HP");
    return rows;
  }

  List<String> suggestions() {
    List<String> rows = new ArrayList<>();
    if (sides[0].weakHits > 0) rows.add("불리한 속성으로 타격한 기록이 있어요. 상대에게 유리한 속성의 기술이나 교체할 동료를 준비해 보세요.");
    if (sides[1].strongHits > 0) rows.add("상대의 유리한 타격이 있었어요. 상대 기술을 덜 아프게 받을 동료를 편성해 보세요.");
    if (sides[0].statusApplications > 0 || sides[0].burnedHp > 0)
      rows.add("상태 이상을 받았어요. 해제·면역 기술과 회복 아이템을 확인해 보세요.");
    if (rows.size() < 2 && sides[1].guardedHits > 0)
      rows.add("상대가 공격을 완전히 막은 기록이 있어요. 공격과 강화·회복 기술을 번갈아 쓸 준비를 해보세요.");
    if (rows.isEmpty()) rows.add("다음 상대의 속성과 내 기술을 비교하고, 고른 증강체와 어울리는 동료를 준비해 보세요.");
    return rows.subList(0, Math.min(2, rows.size()));
  }

  /** HP 최대치를 넘는 설명상 피해·회복량 대신 실제 전후 차이만 사용한다. */
  private static long hpChange(JSONObject e, boolean gain) {
    if (!e.has("hpBefore") || !e.has("hpAfter")) return 0;
    long before = e.optLong("hpBefore", -1), after = e.optLong("hpAfter", -1);
    if (before < 0 || after < 0) return 0;
    return Math.max(0, gain ? after - before : before - after);
  }

  private static int sideOf(JSONObject ref, JSONArray teams) {
    if (ref == null || !ref.has("side") || !ref.has("slot")) return -1;
    int side = ref.optInt("side", -1), slot = ref.optInt("slot", -1);
    if (side < 0 || side > 1 || slot < 0 || slot > 2) return -1;
    JSONObject unit = array(teams.optJSONObject(side), "fighters").optJSONObject(slot);
    if (unit == null || ref.optString("key").isEmpty() || ref.optString("id").isEmpty()) return -1;
    return ref.optString("key").equals(unit.optString("key"))
            && ref.optString("id").equals(unit.optString("id"))
        ? side
        : -1;
  }

  private static boolean hasStatus(JSONObject status) {
    if (status == null) return false;
    for (String key : new String[] {"burn", "freeze", "bind", "sleep", "rage"})
      if (status.optInt(key) > 0) return true;
    return false;
  }

  private static JSONArray array(JSONObject object, String key) {
    JSONArray result = object == null ? null : object.optJSONArray(key);
    return result == null ? new JSONArray() : result;
  }
}
