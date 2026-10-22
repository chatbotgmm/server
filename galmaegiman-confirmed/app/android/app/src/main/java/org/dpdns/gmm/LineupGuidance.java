package org.dpdns.gmm;

final class LineupGuidance {
  static final String DETAIL = "상위: 한정(LIMITED) / 초월(TRANSCEND) / 영원(ETERNAL) / 불멸(IMMORTAL).\n"
      + "하위: 전설(LEGEND) / 히든(HIDDEN) 및 그 아래 등급.\n"
      + "전투는 상위부터 편성해요. 상위에서 환생한 대원은 현재 등급이 낮아도 출전할 수 있어요.\n"
      + "스토리는 1~3마리, 대전은 서로 다른 3마리예요. 실제 출전 가능 여부는 서버 기준을 따라요.";
  static String shortText(boolean pvp) {
    return "상위부터 편성 / " + (pvp ? "대전: 서로 다른 3마리" : "스토리: 1~3마리");
  }
  static void show(MainActivity h) { new GameDialog(h).setTitle("상위와 하위 / 편성 기준")
      .setMessage(DETAIL).setPositiveButton("확인", null).show(); }
}
