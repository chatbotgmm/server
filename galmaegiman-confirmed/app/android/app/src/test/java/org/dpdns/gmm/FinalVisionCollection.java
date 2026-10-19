package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;

/** 최종 검토용 수집·조합·보급 화면. 원본 카드와 캐릭터는 그대로 사용한다. */
final class FinalVisionCollection extends FinalVisionKit {
  FinalVisionCollection(Context ctx, Canvas canvas) {
    super(ctx, canvas);
  }

  void draw(String scene) {
    switch (scene) {
      case "recruit":
        recruit();
        break;
      case "recruit-result":
        recruitResult();
        break;
      case "collection":
        collection();
        break;
      case "craft":
        craft();
        break;
      case "expedition":
        expedition();
        break;
      case "shop":
        shop();
        break;
      default:
        throw new IllegalArgumentException(scene);
    }
  }

  float flowText(
      String value,
      float x,
      float y,
      float max,
      float size,
      int color,
      float leading,
      boolean bold) {
    StringBuilder row = new StringBuilder();
    for (String word : value.split(" +")) {
      String next = row.length() == 0 ? word : row + " " + word;
      if (row.length() > 0 && width(next, size, bold) > max) {
        text(row.toString(), x, y, size, color, bold);
        y += leading;
        row.setLength(0);
      }
      if (width(word, size, bold) > max) {
        if (row.length() > 0) {
          text(row.toString(), x, y, size, color, bold);
          y += leading;
          row.setLength(0);
        }
        y = super.wrap(word, x, y, max, size, color, leading, bold);
      } else {
        if (row.length() > 0) row.append(' ');
        row.append(word);
      }
    }
    if (row.length() > 0) {
      text(row.toString(), x, y, size, color, bold);
      y += leading;
    }
    return y;
  }

  void recruit() {
    bg("recruitment", .12f);
    header("뽑기", "항구 채용소 · 새 인연을 만나요");
    currencies();
    panel(28, 96, 278, 388);
    text("오늘의 채용 공고", 50, 131, 18, TEAL, true);
    text("오늘은 어떤", 50, 173, 30, INK, true);
    text("갈매미가 올까요?", 50, 210, 30, INK, true);
    text("갈매미를 모으고 조합해", 50, 246, 17, MUTED, false);
    text("새로운 수비대원을 만나요.", 50, 270, 17, MUTED, false);
    text("뽑을 횟수", 50, 303, 17, INK, true);
    button("−", 50, 315, 48, 43, false, null);
    inset(108, 315, 44, 43);
    center("5", r(108, 315, 44, 43), 23, INK, true);
    button("+", 162, 315, 48, 43, false, null);
    button("최대", 220, 315, 64, 43, false, null);
    divider(50, 380, 234);
    text("1시간마다 뽑기권 30장", 50, 405, 17, MUTED, false);
    button("30장 받기", 50, 413, 234, 60, false, "ticket");
    recruitPack(330, "하급 뽑기", "LOW", 2, "흔함 70% · 안흔함 30%", false);
    recruitPack(638, "중급 뽑기", "MID", 3, "특별 30% · 흔함·안흔함 포함", false);
    recruitPack(946, "고급 뽑기", "HIGH", 5, "전설 1% · 낮은 등급도 등장", true);
    footer("확률과 보장 횟수를 확인하고 뽑아요. 획득한 갈매미는 조합에 사용할 수 있어요.");
  }

  void recruitPack(float x, String title, String kind, int cost, String odds, boolean high) {
    panel(x, 96, 288, 388);
    center(title, r(x + 12, 111, 264, 34), 28, INK, true);
    thumb("gacha-" + kind, x + 70, 156, 148, 148);
    center(odds, r(x + 10, 315, 268, 25), 16, MUTED, false);
    if (high) paleChip("100회 안에 전설 보장", x + 39, 350, 210, BRASS);
    else center("필요한 재료를 차근차근 모아요", r(x + 16, 350, 256, 27), 16, MUTED, false);
    center("5회 · 뽑기권 " + (cost * 5) + "장", r(x + 16, 385, 256, 25), 19, INK, true);
    button("5회 뽑기", x + 20, 413, 248, 60, high, "ticket");
  }

  void recruitResult() {
    bg("recruitment", .30f);
    header("뽑기 결과", "획득 기록 · 고급 뽑기 5회");
    currencies();
    panel(28, 96, 904, 319);
    String[] ids = {"C1", "C2", "S1", "R1", "L1"};
    String[] names = {"기본 갈매미맨", "황금 갈매미맨", "쌍패수호자", "황금쌍패성기사", "만조의 갈매미맨"};
    String[] grades = {"흔함", "흔함", "특별", "희귀", "전설"};
    int[] colors = {MUTED, MUTED, MUTED, BLUE, 0xff9b712f};
    for (int i = 0; i < ids.length; i++) {
      float x = 47 + i * 175;
      inset(x, 120, 157, 235);
      if (i == 4) stroke(r(x, 120, 157, 235), TEAL, 2.3f, 8);
      thumb(ids[i], x + 10, 131, 137, 137);
      flowText(names[i], x + 12, 295, 133, 18, INK, 23, true);
      center(grades[i], r(x + 10, 323, 137, 23), 16, colors[i], true);
      if (i == 4) {
        fill(x + 10, 130, 54, 23, TEAL, 4);
        center("NEW", r(x + 10, 130, 54, 23), 14, WHITE, true);
      }
    }
    text("획득 완료 · 전설 1마리 / 희귀 1마리 / 특별 1마리 / 흔함 2마리", 51, 390, 17, MUTED, false);
    panel(952, 96, 300, 319);
    icon("book", 976, 116, 43);
    text("새 전설을 발견!", 1029, 147, 25, INK, true);
    divider(976, 168, 252);
    flowText("만조의 갈매미맨", 976, 205, 250, 25, INK, 29, true);
    text("“바다는 들었다. 나는 못 떴다.”", 976, 243, 16, MUTED, false);
    flowText("전설을 모으면 최상위 갈매미를 조합할 수 있어요.", 976, 283, 250, 18, INK, 27, false);
    paleChip("다음 목표 · 원해 갈매미맨", 976, 361, 250, TEAL);
    panel(28, 428, 1224, 78);
    text("카드를 골라 상세와 다음 조합을 확인하세요.", 51, 476, 19, INK, false);
    button("상세 보기", 748, 437, 218, 60, false, "book");
    button("조합하러 가기", 983, 437, 246, 60, true, "craft");
  }

  void collection() {
    bg("quarters", .14f);
    header("수비대", "인사 기록부 · 수집한 145종 중 최상위 33종을 육성해요");
    currencies();
    panel(28, 96, 810, 388);
    tab("내 갈매미", 46, 111, 136, true);
    tab("전투 편성", 192, 111, 136, false);
    tab("아이템", 338, 111, 112, false);
    inset(468, 111, 352, 36);
    icon("book", 476, 115, 27);
    text("이름 · 등급 · 속성으로 찾기", 513, 135, 17, MUTED, false);
    paleChip("최상위 33종", 46, 161, 132, TEAL);
    paleChip("모든 속성", 190, 161, 116, MUTED);
    text("보유한 갈매미 · 6종", 642, 182, 16, MUTED, false);
    String[] ids = {"D1", "T1", "E1", "D2", "D4", "I1"};
    String[] names = {"태초의 갈매미맨", "만상의 수호자", "불퇴의 수호자", "원금 갈매미맨", "원해 갈매미맨", "앙~ 갈매미맨이야"};
    String[] meta = {
      "제한 · 돌 · Lv13",
      "초월 · 돌 · Lv12",
      "영원 · 돌 · Lv10",
      "제한 · 황금 · Lv8",
      "제한 · 바다 · Lv9",
      "불멸 · 돌 · Lv5"
    };
    for (int i = 0; i < ids.length; i++) {
      float x = 46 + (i % 3) * 258, y = 207 + (i / 3) * 136;
      inset(x, y, 242, 122);
      if (i == 0) stroke(r(x, y, 242, 122), TEAL, 2, 7);
      thumb(ids[i], x + 9, y + 13, 87, 87);
      float baseline = flowText(names[i], x + 101, y + 37, 130, 18, INK, 23, true);
      flowText(meta[i], x + 101, Math.max(y + 83, baseline + 7), 130, 14, MUTED, 20, false);
    }
    panel(858, 96, 394, 388);
    text("태초의 갈매미맨", 881, 135, 28, INK, true);
    text("Lv13 · 제한 · 수비", 882, 163, 17, MUTED, false);
    paleChip("돌", 1158, 151, 70, 0xff8a6b4c);
    unit("D1", false, 930, 181, 250, 164);
    text("체력", 882, 365, 16, MUTED, false);
    text("178 / 178", 1092, 365, 17, INK, true);
    bar(882, 376, 346, 8, 1, GREEN);
    text("경험치 52 / 937", 882, 400, 16, MUTED, false);
    text("배울 기술 1개", 1088, 400, 16, TEAL, true);
    button("육성하기", 882, 413, 346, 60, true, "promotion");
    footer("같은 갈매미도 개체마다 레벨과 배운 기술이 달라요. 키울 갈매미를 직접 골라 주세요.");
  }

  void craft() {
    bg("headquarters", .16f);
    header("조합", "발령 기록 · 재료를 모아 새로운 갈매미를 만나요");
    currencies();
    panel(28, 96, 260, 388);
    text("조합식", 49, 132, 25, INK, true);
    tab("만들 수 있는 것", 48, 151, 220, true);
    inset(48, 199, 220, 37);
    text("이름 · 재료 검색", 62, 225, 17, MUTED, false);
    recipeLine(48, 252, "D1", "태초의 갈매미맨", "제한 · 1마리 가능", true);
    recipeLine(48, 326, "T1", "만상의 수호자", "초월 · 재료 2 / 4", false);
    recipeLine(48, 400, "D4", "원해 갈매미맨", "제한 · 재료 1 / 2", false);
    panel(310, 96, 600, 388);
    text("태초의 갈매미맨", 336, 135, 29, INK, true);
    text("쌍패성왕 + 거울떼갈매미", 337, 164, 18, MUTED, false);
    divider(336, 181, 548);
    ingredient(347, 205, "L2", "쌍패성왕", "전설", true);
    center("+", r(567, 265, 74, 60), 36, TEAL, true);
    ingredient(649, 205, "H6", "거울떼갈매미", "히든", true);
    text("조합 횟수", 337, 438, 17, INK, true);
    button("−", 444, 413, 58, 43, false, null);
    inset(514, 413, 54, 43);
    center("1", r(514, 413, 54, 43), 23, INK, true);
    button("+", 580, 413, 58, 43, false, null);
    text("보호·탐험 중인 갈매미 제외", 664, 440, 15, MUTED, false);
    panel(932, 96, 320, 388);
    text("새로운 수비대원", 956, 135, 26, INK, true);
    paleChip("제한 · 돌", 956, 152, 131, 0xff8a6b4c);
    unit("D1", false, 964, 190, 256, 181);
    center("조합 후 육성하고 출전해요", r(947, 380, 290, 24), 18, INK, true);
    button("1마리 조합하기", 956, 413, 272, 60, true, "craft");
    footer("조합 재료와 결과를 먼저 확인하세요. 최상위 갈매미는 기술·승진으로 성장시킬 수 있어요.");
  }

  void recipeLine(float x, float y, String id, String name, String status, boolean selected) {
    inset(x, y, 220, 64);
    if (selected) stroke(r(x, y, 220, 64), TEAL, 2, 7);
    thumb(id, x + 6, y + 6, 52, 52);
    text(name, x + 68, y + 28, 17, INK, true);
    text(status, x + 68, y + 51, 14, selected ? TEAL : MUTED, false);
  }

  void ingredient(float x, float y, String id, String name, String grade, boolean ready) {
    inset(x, y, 210, 190);
    thumb(id, x + 48, y + 12, 114, 114);
    center(name, r(x + 9, y + 133, 192, 24), 20, INK, true);
    text(grade, x + 17, y + 177, 15, MUTED, false);
    text("보유 1 / 필요 1", x + 72, y + 177, 16, ready ? GREEN : RED, true);
  }

  void expedition() {
    bg("navigation", .20f);
    header("탐험대 편성", "항해 일지 · 같은 시너지끼리 보내면 보너스가 생겨요");
    currencies();
    panel(28, 96, 250, 388);
    text("함께 보낼 갈매미", 49, 134, 25, INK, true);
    text("최상위 갈매미 · 최대 10마리", 49, 161, 16, MUTED, false);
    candidateLine(47, 184, "E1", "불퇴의 수호자", "기본 · 보유 1");
    candidateLine(47, 275, "I1", "앙~ 갈매미맨이야", "기본 · 보유 1");
    candidateLine(47, 366, "D5", "시원화 갈매미맨", "벚꽃 · 보유 1");
    panel(300, 96, 624, 388);
    text("연안 탐험", 324, 134, 26, INK, true);
    paleChip("저장된 편성 4 / 10", 696, 111, 202, TEAL);
    map();
    String[] ids = {"D1", "T1", "D2", "D4"};
    String[] names = {"태초의 갈매미맨", "만상의 수호자", "원금 갈매미맨", "원해 갈매미맨"};
    for (int i = 0; i < 4; i++) {
      float x = 319 + i * 146;
      inset(x, 336, 137, 132);
      thumb(ids[i], x + 31, 344, 75, 75);
      flowText(names[i], x + 8, 443, 121, 15, INK, 19, true);
    }
    panel(946, 96, 306, 388);
    text("출발 준비 완료", 970, 136, 27, INK, true);
    divider(970, 157, 258);
    text("탐험 시간", 970, 193, 17, MUTED, false);
    text("60분", 1159, 193, 24, INK, true);
    text("예상 새우gang", 970, 234, 17, MUTED, false);
    text("46개", 1156, 234, 24, INK, true);
    paleChip("기본 2마리 · 보상 +15%", 970, 259, 258, TEAL);
    flowText("같은 시너지는 2마리·4마리부터 보너스가 적용돼요.", 970, 310, 254, 18, MUTED, 24, false);
    button("편성 저장", 970, 352, 258, 50, false, "save");
    button("탐험 출발", 970, 413, 258, 60, true, "map");
    footer("보상 수령 후에도 이전 편성을 유지해요. 돌아온 탐험대는 알림으로 알려드려요.");
  }

  void candidateLine(float x, float y, String id, String name, String sub) {
    inset(x, y, 212, 78);
    thumb(id, x + 7, y + 10, 58, 58);
    float next = flowText(name, x + 76, y + 26, 126, 16, INK, 21, true);
    text(sub, x + 76, Math.max(y + 63, next + 1), 14, MUTED, false);
  }

  void map() {
    inset(320, 153, 580, 170);
    fill(326, 159, 568, 158, 0xffe6ebdf, 5);
    p.setColor(0xffcadbd2);
    p.setStyle(Paint.Style.FILL);
    Path island = new Path();
    island.moveTo(327, 316);
    island.lineTo(327, 260);
    island.cubicTo(380, 226, 382, 206, 443, 239);
    island.cubicTo(485, 269, 502, 268, 541, 316);
    island.close();
    c.drawPath(island, p);
    Path coast = new Path();
    coast.moveTo(894, 159);
    coast.lineTo(799, 159);
    coast.cubicTo(764, 187, 746, 229, 802, 243);
    coast.cubicTo(845, 257, 846, 270, 894, 265);
    coast.close();
    c.drawPath(coast, p);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0xff9fb5a7);
    p.setStrokeWidth(1);
    c.drawOval(r(348, 199, 94, 49), p);
    c.drawOval(r(344, 193, 102, 61), p);
    c.drawOval(r(785, 177, 83, 47), p);
    Path route = new Path();
    route.moveTo(364, 276);
    route.cubicTo(419, 265, 461, 177, 542, 209);
    route.cubicTo(616, 238, 652, 287, 716, 239);
    route.cubicTo(756, 210, 780, 215, 831, 196);
    p.setColor(TEAL);
    p.setStrokeWidth(3);
    p.setPathEffect(new DashPathEffect(new float[] {7, 6}, 0));
    c.drawPath(route, p);
    p.setPathEffect(null);
    p.setStyle(Paint.Style.FILL);
    for (float[] pt : new float[][] {{364, 276}, {542, 209}, {716, 239}, {831, 196}}) {
      circle(pt[0], pt[1], 10, WHITE);
      circle(pt[0], pt[1], 5, TEAL);
    }
    icon("compass", 336, 169, 41);
    text("항구", 344, 307, 16, INK, true);
    text("산호길", 511, 191, 16, INK, true);
    text("탐험지", 787, 233, 16, INK, true);
  }

  void shop() {
    bg("market", .18f);
    header("상점", "항구 보급소 · 필요한 만큼 챙겨 출전하세요");
    currencies();
    panel(28, 96, 294, 388);
    text("교환 창구", 51, 135, 26, INK, true);
    divider(51, 154, 247);
    icon("ticket", 53, 172, 43);
    text("뽑기권 10장", 109, 202, 23, INK, true);
    text("새우gang 1,000개", 53, 235, 18, MUTED, false);
    button("교환하기", 52, 245, 246, 56, false, "ticket");
    divider(51, 318, 247);
    icon("peanut", 53, 333, 43);
    text("뽑기권 20장", 109, 364, 23, INK, true);
    text("땅콩 1개", 53, 394, 18, MUTED, false);
    fill(52, 414, 246, 58, 0xffe2d9c8, 8);
    stroke(r(52, 414, 246, 58), LINE, 1, 8);
    center("땅콩 부족", r(52, 414, 246, 58), 21, MUTED, true);
    panel(344, 96, 598, 388);
    text("전투 보급품", 367, 135, 26, INK, true);
    text("각 아이템은 20개까지 보유", 708, 133, 16, MUTED, false);
    supply(364, 157, "potion", "치료약", "체력 35% 회복", 60, false);
    supply(654, 157, "big", "큰 치료약", "체력 75% 회복", 150, true);
    supply(364, 310, "clear", "정신 차리기 물", "상태이상 해제", 80, false);
    supply(654, 310, "feather", "다시 출근 깃털", "쓰러진 아군 · 체력 50%", 300, false);
    panel(964, 96, 288, 388);
    text("큰 치료약", 989, 136, 27, INK, true);
    paleChip("보유 2 / 20개", 989, 152, 148, TEAL);
    icon("big", 1055, 192, 105);
    center("체력을 75% 회복해요", r(984, 288, 248, 27), 18, INK, false);
    button("−", 988, 322, 62, 56, false, null);
    inset(1062, 322, 60, 56);
    center("1", r(1062, 322, 60, 56), 23, INK, true);
    button("+", 1134, 322, 92, 56, false, null);
    text("총 새우gang 150개", 989, 403, 19, INK, true);
    button("구매하기", 988, 413, 240, 60, true, "bag");
    footer("갈매미 조합과 육성은 재료를, 전투 보급품은 새우gang을 사용해요.");
  }

  void supply(
      float x, float y, String glyph, String name, String desc, int cost, boolean selected) {
    inset(x, y, 268, 133);
    if (selected) stroke(r(x, y, 268, 133), TEAL, 2.3f, 7);
    icon(glyph, x + 15, y + 19, 48);
    text(name, x + 77, y + 39, 20, INK, true);
    flowText(desc, x + 77, y + 68, 179, 15, MUTED, 21, false);
    divider(x + 15, y + 96, 238);
    text("새우gang " + cost + "개", x + 17, y + 120, 17, selected ? TEAL : INK, true);
    if (selected) text("선택됨", x + 192, y + 120, 15, TEAL, true);
  }
}
