package org.dpdns.gmm;

/** 최근 50줄로 제한된 서버 기록에서도 새로 추가된 부분만 찾는다. */
final class BattleLogCursor {
  static int firstNewLine(String[] before, String[] after) {
    for (int overlap = Math.min(before.length, after.length); overlap > 0; overlap--) {
      boolean same = true;
      for (int i = 0; i < overlap; i++)
        if (!before[before.length - overlap + i].equals(after[i])) {
          same = false;
          break;
        }
      if (same) return overlap;
    }
    return 0;
  }
}
