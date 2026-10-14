package org.dpdns.gmm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** 서버의 진행 단계와 재보기의 임시 단계를 분리한다. */
final class NativeTutorialState {
  int step = 9;
  boolean replay, typeSeen, hpSeen, attacked, drew;
  final Set<String> seen = new LinkedHashSet<>();

  void sync(int savedStep, Collection<String> savedSeen) {
    seen.addAll(savedSeen);
    if (!replay) step = Math.max(1, Math.min(9, savedStep));
  }

  void replay() {
    replay = true;
    step = 1;
    typeSeen = hpSeen = attacked = drew = false;
  }

  boolean advance(int next) {
    if (next != 9 && (next < 1 || next > 5)) throw new IllegalArgumentException("안내 단계");
    if (next <= step) return false;
    step = next;
    return true;
  }

  boolean persist() {
    return !replay;
  }

  boolean canHint(String key) {
    return step >= 9 && !replay && seen.contains("tut") && !seen.contains(key);
  }
}
