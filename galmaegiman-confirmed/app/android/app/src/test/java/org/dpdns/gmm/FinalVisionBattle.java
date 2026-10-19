package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;

/** 승인 검토용 최종 전투·편성 시안. 수치와 기술 효과는 실제 전투 규칙으로 검산했다. */
final class FinalVisionBattle extends FinalVisionKit {
  FinalVisionBattle(Context ctx, Canvas c) {
    super(ctx, c);
  }

  void draw(String scene) {
    switch (scene) {
      case "team":
        team();
        break;
      case "battle":
        battle();
        break;
      case "skills":
        skills();
        break;
      case "augment":
        augment();
        break;
      case "reward":
        reward();
        break;
      default:
        throw new IllegalArgumentException(scene);
    }
  }

  void team() {
    bg("headquarters", .11f);
    header("전투 편성", "함께 출전할 갈매미와 순서를 정해요");
    currencies();
    panel(28, 91, 762, 414);
    text("출전 순서", 50, 126, 25, INK, true);
    paleChip("스토리", 672, 106, 94, TEAL);
    text("길게 눌러 순서 변경", 197, 125, 18, MUTED, false);
    String[] ids = {"D1", "T1", "D5"};
    String[] names = {"태초의 갈매미맨", "만상의 수호자", "시원화 갈매미맨"};
    String[] attrs = {"돌 · 수비", "돌 · 수비", "얼음 · 유틸"};
    String[] levels = {"Lv18", "Lv17", "Lv17"};
    String[] hp = {"202 / 202", "226 / 226", "174 / 174"};
    for (int i = 0; i < 3; i++) {
      float x = 50 + i * 242;
      inset(x, 146, 224, 214);
      circle(x + 21, 166, 12, TEAL);
      center("" + (i + 1), r(x + 9, 153, 24, 26), 18, WHITE, true);
      unit(ids[i], false, x + 24, 156, 176, 119);
      center(names[i], r(x + 8, 279, 208, 28), 21, INK, true);
      center(levels[i] + " · " + attrs[i], r(x + 8, 309, 208, 24), 18, MUTED, false);
      bar(x + 16, 332, 192, 6, 1, GREEN);
      text(hp[i], x + 16, 355, 18, MUTED, false);
    }
    divider(50, 376, 718);
    icon("freeswitch", 50, 388, 38);
    text("원형 수호진", 97, 409, 23, INK, true);
    text("다른 원형 동료가 함께 있어 합동기를 쓸 수 있어요.", 97, 437, 18, MUTED, false);
    paleChip("저장된 편성", 50, 457, 201, TEAL);
    button("편성 저장", 540, 442, 228, 56, true, "save");
    panel(810, 91, 442, 414);
    text("보유 갈매미", 832, 126, 25, INK, true);
    paleChip("33종", 1147, 106, 81, TEAL);
    inset(832, 145, 396, 42);
    icon("compass", 842, 153, 24);
    text("이름 또는 속성 검색", 878, 173, 18, MUTED, false);
    tab("전체", 832, 200, 122, true);
    tab("공격", 965, 200, 122, false);
    tab("수비 · 유틸", 1098, 200, 130, false);
    candidate("D2", "원금 갈매미맨", "황금 · 공격", 832, 250, false);
    candidate("D3", "첫밤의 갈매미맨", "그림자 · 유틸", 1034, 250, false);
    candidate("D7", "갈크탑", "황금 · 수비", 832, 347, false);
    candidate("D4", "원해 갈매미맨", "바다 · 공격", 1034, 347, false);
    text("다음 상대 · 쭈라켄", 832, 474, 19, INK, true);
    text("얼음 공격은 바다에 유리해요. ×1.5", 832, 495, 18, GREEN, false);
  }

  void candidate(String id, String name, String attr, float x, float y, boolean selected) {
    inset(x, y, 194, 85);
    if (selected) stroke(r(x, y, 194, 85), TEAL, 2, 7);
    unit(id, false, x + 5, y + 5, 65, 72);
    wrap(name.replace(" ", "\n"), x + 76, y + 25, 107, 18, INK, 20, true);
    text(attr.split(" · ")[0], x + 76, y + 68, 18, MUTED, false);
    text("＋", x + 165, y + 76, 21, TEAL, true);
  }

  void world(boolean dim) {
    bg("deep", dim ? .13f : 0);
    unit("V6", false, 419, 120, 370, 240);
    unit("D1", true, 31, 170, 318, 333);
    hud(false, 28, 20, 327, 119);
    hud(true, 358, 367, 449, 137);
  }

  void hud(boolean own, float x, float y, float w, float h) {
    panel(x, y, w, h);
    text(own ? "태초의 갈매미맨" : "쭈라켄", x + 18, y + 32, 24, INK, true);
    text(own ? "Lv18" : "Lv13", x + w - 74, y + 31, 18, MUTED, false);
    bar(x + 18, y + 46, w - 36, 10, own ? 125f / 202 : 403f / 455, GREEN);
    paleChip(own ? "돌" : "바다", x + 18, y + 67, 57, own ? 0xff896e4c : BLUE);
    if (own) paleChip("얼음 추가", x + 84, y + 67, 98, BLUE);
    text(own ? "125 / 202" : "89%", x + w - (own ? 123 : 67), y + 88, 20, INK, true);
    if (own) text("이중 속성 · 기본기에 얼음 공격 추가", x + 18, y + 119, 18, TEAL, false);
  }

  void battle() {
    world(false);
    button("기록", 386, 20, 104, 56, false, null);
    button("증강체", 502, 20, 120, 56, false, null);
    button("상대 정보", 634, 20, 158, 56, false, null);
    panel(846, 20, 406, 185);
    text("현장 기록", 867, 53, 24, INK, true);
    paleChip("2턴", 1162, 33, 67, TEAL);
    divider(867, 66, 362);
    text("[내 팀] 똥폭탄!", 867, 98, 21, INK, true);
    text("쭈라켄에게", 867, 132, 20, MUTED, false);
    text("52 피해", 980, 132, 24, RED, true);
    paleChip("얼음 ×1.5 유리", 867, 153, 184, GREEN);
    text("이중 속성 적용", 1062, 174, 18, TEAL, false);
    command("공격", 846, 220, 197, 134, true);
    command("아이템", 1055, 220, 197, 134, false);
    command("교체", 846, 368, 197, 134, false);
    command("도망가기", 1055, 368, 197, 134, false);
  }

  void command(String name, float x, float y, float w, float h, boolean primary) {
    button("", x, y, w, h, primary, null);
    center(name, r(x + 14, y + 16, w - 28, h - 32), 32, primary ? WHITE : INK, true);
  }

  void skills() {
    bg("deep", .13f);
    unit("V6", false, 168, 107, 330, 223);
    unit("D1", true, 26, 268, 249, 229);
    panel(28, 20, 467, 91);
    text("쭈라켄", 48, 54, 26, INK, true);
    paleChip("바다", 365, 37, 104, BLUE);
    text("이중 속성으로 기본기가 얼음 공격이 돼요.", 48, 87, 20, TEAL, false);
    panel(288, 365, 207, 136);
    text("내 갈매미", 307, 395, 19, MUTED, false);
    text("Lv18 · 125/202", 307, 426, 22, INK, true);
    bar(307, 444, 168, 8, 125f / 202, GREEN);
    text("돌 · 얼음 추가", 307, 480, 19, TEAL, false);
    panel(517, 20, 735, 484);
    text("기술 선택", 539, 57, 29, INK, true);
    button("전투로", 1111, 35, 119, 56, false, null);
    text("기술을 누르면 사용해요. 위력·상성과 추가 효과를 확인해요.", 539, 91, 19, MUTED, false);
    skill("똥폭탄", "기본기 · 얼음 · 물리", "50", "×1.5 유리", "기본 공격 · 이중 속성 적용", 539, 112, true);
    skill("사방 방패", "전용기 · 돌 · 물리", "55", "×0.67 불리", "받는 피해 −45% · 3턴", 891, 112, false);
    skill("버티기 자세", "역할기 · 보조", "—", "보조 효과", "내 방어 +40% · 2턴", 539, 277, false);
    skill("금빛 일격", "보조 속성 · 황금 · 물리", "60", "×1.0 보통", "황금 속성으로 공격", 891, 277, false);
    button("합동기 · 원형 수호진", 539, 442, 484, 56, false, "freeswitch");
    paleChip("전투당 1회", 1038, 456, 190, TEAL);
  }

  void skill(
      String name,
      String category,
      String power,
      String relation,
      String effect,
      float x,
      float y,
      boolean selected) {
    inset(x, y, 337, 151);
    if (selected) stroke(r(x, y, 337, 151), TEAL, 2.4f, 7);
    text(category, x + 15, y + 25, 18, MUTED, false);
    text(name, x + 15, y + 56, 26, INK, true);
    text(power.equals("—") ? "보조 기술" : "위력 " + power, x + 15, y + 89, 23, INK, true);
    text(
        relation,
        x + 170,
        y + 89,
        19,
        relation.contains("유리") ? GREEN : relation.contains("불리") ? RED : MUTED,
        true);
    text(effect, x + 15, y + 120, 19, TEAL, false);
  }

  void augment() {
    bg("navigation", .20f);
    text("전투 증강체", 28, 50, 32, WHITE, true);
    text("현장 판단으로 다음 수를 바꿔요", 29, 79, 20, 0xfff1e5cc, false);
    panel(1051, 19, 201, 65);
    icon("first", 1068, 32, 39);
    text("남은 30초", 1115, 61, 26, INK, true);
    panel(28, 94, 1224, 41);
    text("선택 전 상성", 46, 122, 18, MUTED, false);
    text("내 공격 · 돌 ×0.67 불리", 223, 122, 20, RED, true);
    text("상대 공격 · 바다 ×1.5 유리", 659, 122, 20, RED, true);
    augmentCard(
        "dual", "이중 속성", "공격", "상대의 약점을 찌르는\n공격 속성을 하나 더 얻어요.", "선택 시 · 기본기 얼음 ×1.5", 28, true);
    augmentCard(
        "bulwark", "철벽", "수비", "지금 나와 있는 내 갈매미가\n2턴 동안 받는 피해 −40%", "내 선두에게 적용 · 2턴", 443, false);
    augmentCard(
        "freeswitch", "인사 발령", "유틸", "이번 전투 동안\n교체가 턴을 쓰지 않아요.", "전투가 끝날 때까지 지속", 858, false);
    text("시간이 지나면 선택지 중 무작위로 결정돼요.", 28, 466, 18, WHITE, false);
    text("다시 뽑기는 선택지마다 한 번씩 가능해요.", 28, 493, 18, 0xfff1e5cc, false);
    button("이중 속성 확정", 476, 442, 776, 64, true, null);
  }

  void augmentCard(
      String id, String name, String kind, String desc, String detail, float x, boolean selected) {
    panel(x, 151, 394, 273);
    if (selected) stroke(r(x + 6, 157, 382, 259), TEAL, 3, 8);
    icon(id, x + 22, 170, 55);
    text(name, x + 94, 201, 29, INK, true);
    paleChip(kind, x + 94, 212, 68, TEAL);
    if (selected) text("✓ 선택", x + 292, 234, 19, TEAL, true);
    wrap(desc, x + 23, 270, 347, 21, MUTED, 29, false);
    divider(x + 23, 316, 347);
    text(detail, x + 23, 344, 18, TEAL, false);
    button("다시 뽑기", x + 22, 360, 350, 56, false, null);
  }

  void reward() {
    bg("quarters", .12f);
    header("전투 결과", "쭈라켄 전투 · 확정된 보상과 다음 준비");
    stamp("전투 승리", 1081, 23, 158, BRASS);
    panel(28, 91, 748, 414);
    text("함께 성장했어요", 50, 126, 28, INK, true);
    text("경험치 +780", 548, 126, 24, TEAL, true);
    growth("D1", "태초의 갈매미맨", "18", "19", "253 / 1,656", "206 / 206", 146, 253f / 1656);
    growth("T1", "만상의 수호자", "17", "18", "178 / 1,527", "232 / 232", 242, 178f / 1527);
    growth("D5", "시원화 갈매미맨", "17", "18", "178 / 1,527", "178 / 178", 338, 178f / 1527);
    button("성장 확인", 50, 442, 223, 56, false, "skilllearn");
    button("스토리로", 291, 442, 463, 56, true, null);
    panel(798, 91, 454, 414);
    text("전투 돌아보기", 820, 126, 28, INK, true);
    paleChip("확정된 기록 · 8턴", 820, 140, 410, TEAL);
    inset(820, 184, 195, 72);
    inset(1034, 184, 196, 72);
    text("내 유리한 타격", 835, 209, 18, MUTED, false);
    text("7회", 835, 243, 29, GREEN, true);
    text("상대 유리한 타격", 1049, 209, 18, MUTED, false);
    text("7회", 1049, 243, 29, RED, true);
    text("선택한 증강체", 820, 290, 21, INK, true);
    text("이중 속성 · 인사 발령 · 송곳 부리", 820, 320, 19, TEAL, false);
    divider(820, 337, 410);
    text("다음 전투 준비", 820, 367, 22, INK, true);
    wrap("상대의 유리한 타격이 있었어요. 받는 상성을 보고 교체할 동료를 준비해 보세요.", 820, 396, 407, 19, MUTED, 27, false);
    button("전투 편성 확인", 820, 442, 410, 56, false, "shield");
  }

  void growth(
      String id,
      String name,
      String from,
      String to,
      String xp,
      String hp,
      float y,
      float progress) {
    unit(id, false, 48, y + 4, 77, 77);
    text(name, 140, y + 25, 22, INK, true);
    paleChip("Lv" + from + " → " + to, 584, y + 5, 169, TEAL);
    text("경험치 " + xp, 140, y + 51, 18, MUTED, false);
    bar(140, y + 61, 613, 6, progress, BRASS);
    text("체력 " + hp + " · 레벨업으로 완전 회복", 140, y + 86, 18, GREEN, false);
    divider(50, y + 93, 704);
  }
}
