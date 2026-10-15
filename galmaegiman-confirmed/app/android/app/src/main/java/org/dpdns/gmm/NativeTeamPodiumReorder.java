package org.dpdns.gmm;

import android.view.View;
import android.view.ViewGroup;
import java.util.*;
import java.util.function.Consumer;

/** 좌우로 끌어 임시 출전 순서를 바꾼다. 저장 버튼을 누르기 전에는 서버에 보내지 않는다. */
final class NativeTeamPodiumReorder {
  static List<String> orderAfterMove(List<String> keys, int from, int to) {
    ArrayList<String> ordered = new ArrayList<>(keys);
    if (from < 0 || to < 0 || from >= keys.size() || to >= keys.size()) return ordered;
    String moved = ordered.remove(from);
    ordered.add(to, moved);
    return ordered;
  }

  static void bind(
      MainActivity host,
      ViewGroup parent,
      List<View> cards,
      List<String> keys,
      Consumer<List<String>> changed) {
    NativeTeamReorder.bindAxis(host, parent, cards, keys, changed, true);
  }
}
