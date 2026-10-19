package org.dpdns.gmm;

import java.util.*;

/** 탐험 규칙을 읽기 전용으로 미리 보여 준다. 실제 보상은 서버가 판정한다. */
final class EconomyPreview {
  static final class Member {
    final String id, synergy;

    Member(String id, String synergy) {
      this.id = id;
      this.synergy = synergy;
    }
  }

  static final class Rules {
    int minutes, snackPerUnit;
    int[] levels;
    final Map<String, int[]> effects = new LinkedHashMap<>();
    final Map<String, String> labels = new LinkedHashMap<>();
    final SortedMap<Integer, Integer> countBonuses = new TreeMap<>();
  }

  static final class Plan {
    int minutes, snacks, doublePercent, commonPercent, uncommonPercent, ticketPercent;
    final ArrayList<String> active = new ArrayList<>();
  }

  static Plan estimate(Rules rules, List<Member> members) {
    Plan plan = new Plan();
    plan.minutes = rules.minutes;
    Map<String, Integer> groups = new LinkedHashMap<>(), counts = new HashMap<>();
    for (Member member : members) {
      groups.put(member.synergy, groups.getOrDefault(member.synergy, 0) + 1);
      counts.put(member.id, counts.getOrDefault(member.id, 0) + 1);
    }
    int snackPercent = 0, flatPerUnit = 0;
    for (Map.Entry<String, Integer> group : groups.entrySet()) {
      int level =
          group.getValue() >= rules.levels[1] ? 2 : group.getValue() >= rules.levels[0] ? 1 : 0;
      int[] effect = rules.effects.get(group.getKey());
      if (level == 0 || effect == null) continue;
      int value = effect[level - 1];
      plan.active.add(
          rules.labels.getOrDefault(group.getKey(), group.getKey()) + " " + level + "단계");
      switch (group.getKey()) {
        case "BASIC":
          snackPercent += value;
          break;
        case "GOLD":
          plan.doublePercent += value;
          break;
        case "DARK":
          plan.minutes = Math.min(plan.minutes, value);
          break;
        case "SEA":
          plan.commonPercent += value;
          break;
        case "BLOSSOM":
          plan.uncommonPercent += value;
          break;
        case "COSMOS":
          plan.ticketPercent += value;
          break;
        case "OUTFIT":
          flatPerUnit += value;
          break;
      }
    }
    int same = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    int countPercent = 0;
    for (Map.Entry<Integer, Integer> bonus : rules.countBonuses.entrySet())
      if (same >= bonus.getKey()) countPercent = bonus.getValue();
    if (countPercent > 0) {
      snackPercent += countPercent;
      plan.active.add("같은 갈매미 " + same + "마리 +" + countPercent + "%");
    }
    plan.snacks =
        (int) Math.round(members.size() * rules.snackPerUnit * (1 + snackPercent / 100.0))
            + flatPerUnit * members.size();
    return plan;
  }
}
