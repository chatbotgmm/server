package org.dpdns.gmm;

/** 서버가 확정한 연출 번호만 소비한다. 잘린 기록의 빈 구간은 추측해서 재생하지 않는다. */
final class BattleEventCursor {
  private long seen;

  BattleEventCursor(long initial) {
    seen = Math.max(0, initial);
  }

  long seen() {
    return seen;
  }

  /** 새 이벤트 시작 위치. 번호 누락·역순·잘못된 끝 번호는 -1로 최종 상태 적용을 요청한다. */
  int firstNew(long[] ids, long latest) {
    if (latest < seen) return -1;
    if (latest == seen) return ids.length;
    long previous = 0;
    int first = ids.length;
    for (int i = 0; i < ids.length; i++) {
      long id = ids[i];
      if (id <= 0 || id <= previous || id > latest) return -1;
      previous = id;
      if (id <= seen) continue;
      if (first == ids.length) {
        if (id != seen + 1) return -1;
        first = i;
      } else if (id != ids[i - 1] + 1) return -1;
    }
    if (first == ids.length || previous != latest) return -1;
    return first;
  }

  void accept(long id) {
    seen = Math.max(seen, id);
  }
}
