package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;

/** 항구 수비조장의 최종 디자인 제안. 실제 게임의 탐색·상태·내비게이션과 연결하지 않는다. */
final class FinalVisionJourney extends FinalVisionKit {
  FinalVisionJourney(Context ctx, Canvas c) {
    super(ctx, c);
  }

  void draw(String scene) {
    switch (scene) {
      case "title":
        title();
        break;
      case "login":
        login();
        break;
      case "home":
        home();
        break;
      case "chapters":
        chapters();
        break;
      case "dialogue":
        dialogue();
        break;
      default:
        throw new IllegalArgumentException(scene);
    }
  }

  void title() {
    bg("headquarters", .01f);
    sprite("native/t2_guardian.webp", 710, 92, 480, 307);
    panel(38, 38, 536, 450);
    icon("shield", 69, 63, 34);
    text("갈매미 섬 항구 수비대", 115, 85, 18, MUTED, false);
    divider(70, 107, 470);
    text("갈매미맨 수비대", 70, 171, 43, INK, true);
    text("수비조장의 모험 도감", 72, 210, 22, TEAL, false);
    text("돌처럼 듬직한 동료들과,", 72, 260, 19, INK, false);
    text("한 마리씩 쌓아 가는 우리 섬의 이야기.", 72, 290, 19, INK, false);
    paleChip("수집", 72, 315, 87, TEAL);
    paleChip("조합", 169, 315, 87, TEAL);
    paleChip("턴제 전략", 266, 315, 126, TEAL);
    button("로그인", 70, 373, 470, 63, true, "profile");
    text("오늘도 출근 도장부터 찍어요.", 73, 461, 15, MUTED, false);
    button("설정", 1160, 22, 92, 43, false, null);
    panel(703, 426, 507, 62);
    center("갈매미는 존재해요. 다만 늘 조금씩 허술해요.", r(720, 438, 473, 35), 18, INK, false);
  }

  void login() {
    bg("headquarters", .025f);
    sprite("native/t2_guardian.webp", 714, 96, 475, 303);
    panel(39, 31, 550, 463);
    icon("profile", 71, 55, 31);
    text("수비대 인사 기록부", 115, 80, 29, INK, true);
    button("×", 520, 49, 42, 40, false, null);
    // 외부 로그인 브랜드는 행동 계층과 별개인 식별 색으로 제한한다.
    fill(73, 112, 482, 53, 0xfffee500, 9);
    stroke(r(73, 112, 482, 53), 0xffb5a043, 1.2f, 9);
    center("카카오로 로그인", r(73, 112, 482, 53), 23, INK, true);
    divider(73, 191, 482);
    text("또는 카톡 코드로 로그인", 75, 221, 18, MUTED, false);
    text("갈매미맨 채널에 ‘웹 로그인’을 보내요.", 75, 249, 18, MUTED, false);
    for (int i = 0; i < 6; i++) {
      float x = 75 + i * 80;
      fill(x, 270, 65, 57, WHITE, 7);
      stroke(r(x, 270, 65, 57), i == 2 ? TEAL : LINE, i == 2 ? 2 : 1.1f, 7);
      center(i == 0 ? "1" : i == 1 ? "3" : "·", r(x, 270, 65, 57), 27, i < 2 ? INK : MUTED, true);
    }
    button("코드로 로그인", 73, 348, 482, 56, true, null);
    text("받은 코드는 10분 안에 한 번만 쓸 수 있어요.", 75, 434, 18, MUTED, false);
    text("로그인은 이 기기에서 30일 동안 유지돼요.", 75, 462, 18, MUTED, false);
    panel(704, 426, 509, 62);
    icon("shield", 724, 441, 29);
    text("기존 수비대와 스토리를 그대로 이어가요.", 767, 463, 18, INK, false);
  }

  void profile() {
    panel(29, 20, 313, 66);
    icon("profile", 47, 36, 34);
    text("수비조장", 94, 49, 23, INK, true);
    text("수비대 Lv18 · 실적 8/70", 95, 71, 16, MUTED, false);
    currencies();
    button("설정", 748, 21, 70, 43, false, null);
  }

  void home() {
    bg("headquarters", .01f);
    profile();
    unit("D1", false, 739, 103, 441, 294);
    panel(29, 112, 403, 259);
    text("오늘의 근무 기록", 54, 145, 18, MUTED, false);
    divider(54, 159, 353);
    text("태초의 갈매미맨", 54, 195, 28, INK, true);
    paleChip("Lv18", 334, 173, 70, TEAL);
    paleChip("돌", 54, 210, 60, 0xff896e4c);
    paleChip("수비", 122, 210, 72, MUTED);
    text("경험치", 55, 261, 18, MUTED, false);
    text("840 / 1,527", 287, 261, 18, INK, true);
    bar(54, 272, 353, 10, 840f / 1527, TEAL);
    icon("skilllearn", 54, 299, 29);
    text("배울 기술 1개", 96, 321, 19, INK, true);
    button("성장", 311, 295, 96, 42, false, null);
    text("승진 조건 · Lv20 + 1부 완료", 55, 353, 17, MUTED, false);
    fill(29, 387, 403, 37, 0xf7f7edda, 7);
    icon("attendance", 42, 391, 28);
    text("출석 보상 준비됐어요", 82, 413, 18, INK, true);
    text("›", 398, 413, 22, TEAL, true);
    panel(930, 391, 321, 45);
    text("다음 이야기", 948, 420, 16, MUTED, false);
    text("1-4 해저 2층 입구", 1047, 420, 18, INK, true);
    String[] names = {"탐험", "뽑기", "수비대", "조합", "상점"};
    String[] icons = {"map", "recruit", "shield", "craft", "store"};
    for (int i = 0; i < names.length; i++)
      button(names[i], 29 + i * 179, 448, 168, 56, false, icons[i]);
    button("플레이", 930, 448, 321, 56, true, "play");
  }

  void chapterRow(String title, String subtitle, float y, boolean selected, boolean locked) {
    boolean twoLines = title.contains("\n");
    float height = twoLines ? 82 : 63;
    fill(48, y, 222, height, selected ? TEAL : 0xffeee5d3, 8);
    stroke(r(48, y, 222, height), selected ? BRASS : LINE, 1, 8);
    wrap(title, 61, y + 25, 196, 18, selected ? WHITE : INK, 22, true);
    text(subtitle, 62, y + (twoLines ? 70 : 49), 16, selected ? 0xffdfede5 : MUTED, false);
    if (locked) circle(252, y + 14, 3, MUTED);
  }

  void route(float x1, float y1, float x2, float y2, boolean finished) {
    line(x1, y1, x2, y2, finished ? TEAL : 0xffc5b692, 3);
  }

  void node(float x, float y, String number, String title, int state) {
    if (state == 1) {
      circle(x, y, 26, 0xffe2ede3);
      circle(x, y, 22, TEAL);
      circle(x, y, 17, WHITE);
    } else circle(x, y, 20, state == 2 ? TEAL : 0xffe3d8bf);
    center(
        number,
        r(x - 20, y - 20, 40, 40),
        19,
        state == 2 ? WHITE : state == 1 ? TEAL : MUTED,
        true);
    center(title, r(x - 76, y + 30, 152, 24), 17, state == 0 ? MUTED : INK, true);
  }

  void chapters() {
    bg("navigation", .18f);
    header("이야기", "수비조장의 근무 지도");
    panel(28, 104, 260, 394);
    text("근무 기록", 49, 140, 23, INK, true);
    divider(49, 153, 216);
    chapterRow("프롤로그 · 빈 자리", "첫 출근 완료", 168, false, false);
    chapterRow("1부 · 먹물 이력서", "항구·해저 2층 진행 중", 241, true, false);
    chapterRow("2부 · 인수합병\n소용돌이", "1단계 승진이 필요해요", 314, false, true);
    chapterRow("3부 · 검은 깃털", "2단계 승진이 필요해요", 406, false, true);
    panel(309, 104, 943, 294);
    text("1부 · 먹물 이력서", 334, 142, 27, INK, true);
    paleChip("진행 중", 1123, 121, 101, TEAL);
    // 항로 같은 경로를 종이 지도 위에 그린다. 일반전·순찰·보스의 실제 순서를 유지한다.
    route(370, 203, 520, 203, true);
    route(520, 203, 670, 203, true);
    route(670, 203, 820, 203, true);
    route(820, 203, 820, 312, false);
    route(820, 312, 1000, 312, false);
    route(1000, 312, 1160, 312, false);
    node(370, 203, "✓", "항구의 먹물", 2);
    node(520, 203, "✓", "이력서 폭탄", 2);
    node(670, 203, "✓", "도장대 사수", 2);
    node(820, 203, "4", "해저 2층 입구", 1);
    node(820, 312, "···", "순찰 1–4", 0);
    node(1000, 312, "5", "여덟발 대장", 0);
    node(1160, 312, "6", "쭈라켄", 0);
    unit("V6", false, 1116, 205, 88, 79);
    paleChip("보스", 1129, 360, 62, MUTED);
    text("보스와 만나기 전, 순찰로 실전 경험을 쌓아요.", 337, 374, 17, MUTED, false);
    panel(309, 412, 943, 86);
    text("다음 근무 · 해저 2층 입구", 335, 445, 23, INK, true);
    icon("ice", 334, 457, 25);
    icon("star", 364, 457, 25);
    text("바다에 강한 얼음·별 기술을 챙겨요.", 405, 477, 18, MUTED, false);
    button("편성 후 출전", 1010, 430, 214, 49, true, "shield");
  }

  void dialogue() {
    bg("deep", .06f);
    button("이전", 28, 19, 102, 43, false, "back");
    text("1부 · 먹물 이력서", 151, 48, 26, WHITE, true);
    text("여덟발 대장", 152, 72, 17, 0xffffe5ba, false);
    button("건너뛰기", 1124, 21, 128, 43, false, null);
    unit("D1", false, 112, 101, 322, 229);
    unit("V5", false, 813, 78, 334, 252);
    fill(456, 119, 351, 71, 0xf5fff9eb, 9);
    stroke(r(456, 119, 351, 71), BRASS, 1, 9);
    text("여덟발 대장", 474, 144, 17, MUTED, false);
    text("다리 여덟 개에 이력서 여덟 장!", 474, 173, 21, INK, true);
    // 이름표와 본문은 별도 면으로 구성해 긴 대사도 가장자리 장식이 늘어나지 않는다.
    panel(89, 352, 1104, 145);
    fill(117, 329, 228, 38, TEAL, 7);
    stroke(r(117, 329, 228, 38), BRASS, 1, 7);
    center("태초의 갈매미맨", r(125, 329, 212, 38), 20, WHITE, true);
    text("저희는 날개예요.", 126, 424, 34, INK, true);
    text("3 / 6", 914, 467, 16, MUTED, false);
    button("다음", 1009, 408, 151, 58, true, "play");
    icon("feather", 126, 450, 23);
    text("화면을 누르면 대화가 이어져요.", 159, 470, 18, MUTED, false);
  }
}
