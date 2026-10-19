package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;

/** Strategy and everyday administration: aspirational presentation, existing rules. */
final class FinalVisionStrategy extends FinalVisionKit {
  FinalVisionStrategy(Context context, Canvas canvas) {
    super(context, canvas);
  }

  void draw(String scene) {
    switch (scene) {
      case "growth":
        growth();
        break;
      case "combo":
        combo();
        break;
      case "types":
        types();
        break;
      case "missions":
        missions();
        break;
      case "settings":
        settings();
        break;
      case "items":
        items();
        break;
      default:
        throw new IllegalArgumentException(scene);
    }
  }

  void growth() {
    bg("quarters", .18f);
    header("갈매미 성장", "인사기록  /  개체별 기술과 승진");
    currencies();
    panel(28, 89, 350, 409);
    chip("제한 · 원형", 52, 109, 117, TEAL);
    text("Lv.20", 284, 132, 23, INK, true);
    unit("D1", false, 65, 151, 275, 221);
    text("태초의 갈매미맨", 55, 400, 29, INK, true);
    paleChip("돌", 55, 412, 51, 0xff77786b);
    paleChip("수비", 114, 412, 64, TEAL);
    bar(55, 457, 293, 8, 840f / 1789, BLUE);
    text("EXP 840 / 1,789", 55, 484, 16, MUTED, false);
    panel(394, 89, 858, 409);
    tab("기술", 414, 108, 126, false);
    tab("승진", 549, 108, 126, true);
    tab("직무 표창", 684, 108, 145, false);
    text("첫 번째 승진", 418, 185, 31, INK, true);
    stamp("조건 달성", 1062, 154, 154, GREEN);
    text("Lv.20 달성  +  스토리 1부 완료", 419, 215, 19, GREEN, true);
    divider(418, 230, 808);
    text("능력치", 419, 261, 21, INK, true);
    text("현재  →  승진 후", 1034, 260, 17, MUTED, false);
    String[] labels = {"체력", "물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
    String[] before = {"211", "30", "51", "18", "40", "10"},
        after = {"237", "34", "57", "20", "45", "10"};
    for (int i = 0; i < 6; i++) {
      float x = 420 + (i % 3) * 272, y = 279 + (i / 3) * 61;
      inset(x, y, 254, 51);
      text(labels[i], x + 13, y + 21, 16, MUTED, false);
      text(before[i] + "  →", x + 13, y + 43, 20, INK, true);
      text(after[i], x + 137, y + 43, 22, GREEN, true);
    }
    text("표창 선택 전 기본 비교 · 승진하면 체력을 모두 회복해요.", 420, 421, 17, MUTED, false);
    button("기술 살펴보기", 420, 440, 269, 41, false, "skilllearn");
    button("표창 고르기", 893, 440, 334, 41, true, "promotion");
  }

  void combo() {
    bg("harbor", .37f);
    header("합동기", "같은 동네의 동료와 함께하는 한 번의 결정");
    panel(28, 93, 777, 404);
    chip("기본 시너지", 51, 114, 143, TEAL);
    paleChip("전투당 1회", 635, 114, 143, TEAL);
    unit("D1", false, 72, 153, 215, 194);
    unit("T1", false, 294, 153, 215, 194);
    unit("E1", false, 515, 153, 215, 194);
    line(171, 358, 628, 358, BRASS, 3);
    for (float x : new float[] {171, 397, 628}) {
      circle(x, 358, 8, TEAL);
      circle(x, 358, 4, BRASS);
    }
    center("태초의 갈매미맨", r(58, 378, 244, 25), 21, INK, true);
    center("만상의 수호자", r(280, 378, 244, 25), 21, INK, true);
    center("불퇴의 수호자", r(501, 378, 244, 25), 21, INK, true);
    inset(53, 423, 724, 49);
    icon("shield", 65, 431, 32);
    text("살아 있는 같은 시너지 동료와 함께 사용해요.", 110, 455, 20, TEAL, true);
    panel(825, 93, 427, 404);
    icon("shield", 849, 111, 48);
    text("원형 수호진", 907, 147, 31, INK, true);
    divider(850, 164, 376);
    text("공격 위력", 852, 197, 18, MUTED, false);
    text("60", 1134, 201, 29, INK, true);
    text("아군 전체 방어", 852, 243, 20, INK, true);
    text("+40%", 1097, 245, 29, GREEN, true);
    chip("3턴", 853, 263, 67, TEAL);
    wrap("공격과 함께 아군의 물리·마법 방어를 높여요.", 852, 326, 363, 20, INK, 29, false);
    text("다른 합동기도 함께 소모돼요.", 852, 399, 17, MUTED, false);
    button("합동기 사용", 850, 430, 376, 47, true, "shield");
  }

  void types() {
    bg("navigation", .38f);
    header("속성 상성", "공격 속성을 먼저 고르고, 상대 속성을 확인해요.");
    panel(28, 91, 294, 407);
    text("공격 속성", 51, 127, 23, INK, true);
    String[] names = {"돌", "황금", "불", "얼음", "꽃", "바다", "그림자", "별", "실", "중력"};
    String[] glyphs = {
      "stone", "gold", "fire", "ice", "blossom", "sea", "shadow", "star", "thread", "gravity"
    };
    for (int i = 0; i < 10; i++) {
      float x = 50 + (i % 2) * 129, y = 144 + (i / 2) * 63;
      boolean selected = i == 5;
      fill(x, y, 119, 53, selected ? TEAL : 0xffeee4d0, 7);
      stroke(r(x, y, 119, 53), selected ? BRASS : LINE, 1, 7);
      icon(glyphs[i], x + 8, y + 9, 34);
      text(names[i], x + 49, y + 34, 20, selected ? WHITE : INK, true);
    }
    panel(340, 91, 912, 407);
    icon("sea", 366, 107, 46);
    text("바다로 공격할 때", 424, 140, 29, INK, true);
    paleChip("일반 피해 ×1", 1080, 110, 144, MUTED);
    divider(366, 155, 859);
    fill(365, 175, 419, 141, 0xffe4ecdf, 9);
    chip("유리 ×1.5", 382, 188, 121, GREEN);
    icon("fire", 395, 235, 46);
    text("불", 451, 266, 25, INK, true);
    icon("stone", 576, 235, 46);
    text("돌", 633, 266, 25, INK, true);
    fill(800, 175, 425, 141, 0xfff1e1d6, 9);
    chip("불리 ×0.67", 819, 188, 133, RED);
    icon("ice", 831, 235, 46);
    text("얼음", 889, 266, 25, INK, true);
    icon("star", 1032, 235, 46);
    text("별", 1090, 266, 25, INK, true);
    text("파도가 바위를 깎아요.", 380, 355, 25, TEAL, true);
    text("상성은 공격하는 기술의 속성을 기준으로 계산해요.", 380, 388, 20, INK, false);
    divider(366, 410, 859);
    text("복합 속성·증강체가 적용되면 전투의 최종 배율을 확인해요.", 380, 446, 18, MUTED, false);
    button("편성으로", 1023, 437, 201, 42, true, "shield");
  }

  void missions() {
    bg("headquarters", .26f);
    header("오늘의 근무", "출석과 매일 임무  /  한국 시간 0시 초기화");
    currencies();
    panel(28, 91, 463, 407);
    icon("attendance", 53, 110, 48);
    text("출근 도장", 111, 145, 29, INK, true);
    text("연속 4일째", 53, 184, 21, TEAL, true);
    for (int i = 0; i < 7; i++) {
      float x = 53 + (i % 4) * 104, y = 211 + (i / 4) * 96;
      inset(x, y, 91, 81);
      center((i + 1) + "일", r(x, y + 4, 91, 21), 16, MUTED, false);
      icon("ticket", x + 12, y + 29, 27);
      text(
          new String[] {"8", "10", "12", "14", "16", "18", "40"}[i], x + 47, y + 51, 22, INK, true);
      if (i < 3) stamp("출근", x + 8, y + 46, 74, GREEN);
      if (i == 3) stroke(r(x, y, 91, 81), TEAL, 2.5f, 7);
    }
    button("출석하기", 52, 436, 415, 43, true, "attendance");
    panel(508, 91, 744, 407);
    text("매일 임무", 532, 133, 28, INK, true);
    text("달성한 보상은 자동으로 지급돼요.", 841, 132, 17, MUTED, false);
    divider(532, 149, 693);
    String[] labels = {"뽑기 5회", "조합 3회", "희귀 조합하기", "탐험 다녀오기 1회"};
    String[] progress = {"5 / 5", "1 / 3", "0 / 1", "0 / 1"};
    String[] rewards = {"20", "20", "30", "10"};
    for (int i = 0; i < 4; i++) {
      float y = 170 + i * 72;
      icon(i == 0 ? "recruit" : i == 3 ? "map" : "craft", 532, y + 2, 38);
      text(labels[i], 584, y + 24, 22, INK, true);
      text(progress[i], 901, y + 24, 18, MUTED, true);
      icon("ticket", 1015, y, 29);
      text(rewards[i], 1055, y + 24, 22, INK, true);
      if (i == 0) chip("지급 완료", 1120, y - 1, 105, GREEN);
      else text("진행 중", 1145, y + 24, 17, MUTED, false);
      bar(584, y + 39, 401, 5, i == 0 ? 1 : i == 1 ? .33f : 0, TEAL);
    }
    text("작은 근무가 모여 더 든든한 수비대가 돼요.", 535, 477, 19, TEAL, true);
  }

  void settings() {
    bg("headquarters", .42f);
    header("설정", "수비대 기록부  /  소리·화면·알림");
    panel(28, 91, 242, 407);
    String[] tabs = {"소리·화면", "알림", "계정", "도움말"};
    for (int i = 0; i < 4; i++) {
      tab(tabs[i], 49, 113 + i * 66, 201, i == 0);
    }
    icon("cog", 110, 398, 66);
    panel(289, 91, 963, 407);
    text("소리", 315, 134, 28, INK, true);
    text("음소거를 해제하면 이전 볼륨으로 돌아가요.", 707, 133, 18, MUTED, false);
    divider(315, 149, 909);
    volume("배경음악", 75, 178, false);
    volume("효과음", 60, 255, true);
    divider(315, 331, 909);
    text("화면", 315, 369, 25, INK, true);
    text("중앙 화면 드래그", 315, 407, 20, INK, true);
    text("배경·소품·캐릭터가 서로 다른 깊이로 움직여요.", 315, 439, 18, MUTED, false);
    chip("패럴랙스 켜짐", 1050, 381, 172, TEAL);
    text("메뉴와 전투 정보는 고정돼요.", 315, 478, 18, TEAL, true);
    button("저장", 1050, 444, 172, 40, true, "save");
  }

  void volume(String name, int value, float y, boolean muted) {
    text(name, 315, y + 27, 23, INK, true);
    text(muted ? "음소거" : value + "%", 1034, y + 27, 19, muted ? MUTED : TEAL, true);
    bar(486, y + 15, 512, 10, value / 100f, muted ? 0xffa8aea5 : TEAL);
    circle(486 + 512 * value / 100f, y + 20, 11, BRASS);
    circle(486 + 512 * value / 100f, y + 20, 7, PAPER);
    button(muted ? "켜기" : "끄기", 1129, y, 94, 43, !muted, null);
    if (muted) text("이전 볼륨 60%", 488, y + 54, 17, MUTED, false);
  }

  void items() {
    bg("canteen", .24f);
    header("아이템", "보급 가방  /  스토리 전투를 든든하게");
    currencies();
    panel(28, 91, 552, 407);
    text("보유 아이템", 52, 132, 28, INK, true);
    text("전투당 최대 3개", 387, 131, 18, MUTED, false);
    divider(52, 148, 502);
    String[] names = {"치료약", "큰 치료약", "정신 차리기 물", "다시 출근 깃털"},
        glyphs = {"potion", "big", "clear", "feather"},
        desc = {"체력 35% 회복", "체력 75% 회복", "상태 이상 해제", "쓰러진 아군을 체력 50%로"};
    for (int i = 0; i < 4; i++) {
      float y = 164 + i * 76;
      inset(51, y, 505, 64);
      if (i == 0) stroke(r(51, y, 505, 64), TEAL, 2, 7);
      icon(glyphs[i], 65, y + 10, 43);
      text(names[i], 123, y + 27, 23, INK, true);
      text(desc[i], 124, y + 51, 17, MUTED, false);
      text(new String[] {"5개", "2개", "3개", "1개"}[i], 487, y + 38, 22, i == 0 ? TEAL : INK, true);
    }
    panel(598, 91, 654, 407);
    icon("potion", 624, 111, 45);
    text("치료약", 683, 144, 30, INK, true);
    chip("전투 밖에서도 사용", 1002, 114, 222, TEAL);
    divider(624, 163, 602);
    text("회복할 갈매미를 고르세요.", 625, 199, 23, INK, true);
    String[] ids = {"D1", "T1", "E1"},
        labels = {"태초의 갈매미맨", "만상의 수호자", "불퇴의 수호자"},
        hp = {"125 / 211", "178 / 178", "0 / 158"};
    for (int i = 0; i < 3; i++) {
      float x = 624 + i * 203;
      inset(x, 216, 187, 169);
      unit(ids[i], false, x + 34, 225, 119, 91);
      center(labels[i], r(x + 6, 321, 175, 26), 18, INK, true);
      bar(x + 16, 355, 155, 7, new float[] {125f / 211, 1, 0}[i], i == 2 ? RED : GREEN);
      center(hp[i], r(x, 367, 187, 18), 15, MUTED, false);
      if (i == 0) stroke(r(x, 216, 187, 169), TEAL, 2, 7);
    }
    text("선택한 갈매미의 최대 체력 35%를 회복해요.", 625, 419, 18, MUTED, false);
    button("보급품 사기", 624, 438, 234, 45, false, "store");
    button("치료약 사용", 890, 438, 333, 45, true, "potion");
  }
}
