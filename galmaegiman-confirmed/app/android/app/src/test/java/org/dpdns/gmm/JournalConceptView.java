package org.dpdns.gmm;

import android.graphics.*;
import android.view.View;
import java.io.*;
import java.util.*;

/** 승인 전 테마 검토용 실제 글꼴·원본 캐릭터 합성. 제품 화면에는 연결하지 않는다. */
final class JournalConceptView extends View {
  static final int INK = 0xff302b25,
      PAPER = 0xfff7edda,
      LEATHER = 0xff51372f,
      BRASS = 0xffc49a59,
      TEAL = 0xff28535b,
      MUTED = 0xff796550;
  final MainActivity host;
  final String scene;
  final Paint p = new Paint(3);
  final Typeface label;
  final Map<String, Bitmap> art = new HashMap<>();
  final Bitmap room;
  float W = 1280, H;
  final List<String> overflowing = new ArrayList<>();

  JournalConceptView(MainActivity h, String scene, String background) {
    super(h);
    host = h;
    this.scene = scene;
    label = Typeface.createFromAsset(h.getAssets(), "native/Jua-Regular.ttf");
    room = BitmapFactory.decodeFile(background);
    if (room == null) throw new IllegalArgumentException("배경 시안 없음");
    setBackgroundColor(PAPER);
  }

  Bitmap asset(String name) {
    if (art.containsKey(name)) return art.get(name);
    try (InputStream in = host.getAssets().open(name)) {
      Bitmap b = BitmapFactory.decodeStream(in);
      art.put(name, b);
      return b;
    } catch (IOException e) {
      throw new IllegalStateException(name, e);
    }
  }

  void fill(Canvas c, RectF r, int color, float radius) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    c.drawRoundRect(r, radius, radius, p);
  }

  void stroke(Canvas c, RectF r, int color, float width, float radius) {
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(width);
    p.setColor(color);
    c.drawRoundRect(r, radius, radius, p);
    p.setStyle(Paint.Style.FILL);
  }

  void text(Canvas c, String s, float x, float baseline, float size, int color, boolean heading) {
    p.setTypeface(heading ? label : host.prose);
    p.setTextSize(size);
    p.setColor(color);
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setTextAlign(Paint.Align.LEFT);
    c.drawText(s, x, baseline, p);
  }

  void centered(Canvas c, String s, RectF r, float size, int color, boolean heading) {
    p.setTypeface(heading ? label : host.prose);
    p.setTextSize(size);
    Paint.FontMetrics f = p.getFontMetrics();
    float width = p.measureText(s);
    if (width > r.width() - 16) overflowing.add(scene + ":" + s);
    text(
        c,
        s,
        r.centerX() - width / 2,
        r.centerY() - (f.ascent + f.descent) / 2,
        size,
        color,
        heading);
  }

  void line(Canvas c, float x, float y, float x2, float y2, int color, float width) {
    p.setShader(null);
    p.setColor(color);
    p.setStrokeWidth(width);
    c.drawLine(x, y, x2, y2, p);
  }

  void circle(Canvas c, float x, float y, float r, int color) {
    p.setShader(null);
    p.setColor(color);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(x, y, r, p);
  }

  void cover(Canvas c, Bitmap b, RectF r) {
    float z = Math.max(r.width() / b.getWidth(), r.height() / b.getHeight());
    RectF to =
        new RectF(
            r.centerX() - b.getWidth() * z / 2,
            r.centerY() - b.getHeight() * z / 2,
            r.centerX() + b.getWidth() * z / 2,
            r.centerY() + b.getHeight() * z / 2);
    c.save();
    c.clipRect(r);
    p.setAlpha(255);
    c.drawBitmap(b, null, to, p);
    c.restore();
  }

  void sprite(Canvas c, String name, RectF r) {
    Bitmap b = asset(name);
    int l = b.getWidth(), t = b.getHeight(), right = 0, bottom = 0;
    for (int y = 0; y < b.getHeight(); y += 3)
      for (int x = 0; x < b.getWidth(); x += 3)
        if (Color.alpha(b.getPixel(x, y)) > 20) {
          l = Math.min(l, x);
          t = Math.min(t, y);
          right = Math.max(right, x + 3);
          bottom = Math.max(bottom, y + 3);
        }
    Rect src =
        new Rect(
            Math.max(0, l - 3),
            Math.max(0, t - 3),
            Math.min(b.getWidth(), right + 3),
            Math.min(b.getHeight(), bottom + 3));
    float z = Math.min(r.width() / src.width(), r.height() / src.height());
    RectF to =
        new RectF(
            r.centerX() - src.width() * z / 2,
            r.bottom - src.height() * z,
            r.centerX() + src.width() * z / 2,
            r.bottom);
    p.setColor(Color.WHITE);
    p.setAlpha(255);
    p.setShader(null);
    c.drawBitmap(b, src, to, p);
  }

  void leather(Canvas c, RectF r, float radius) {
    RectF shadow = new RectF(r);
    shadow.offset(0, 6);
    fill(c, shadow, 0x80301e17, radius);
    p.setShader(
        new LinearGradient(
            r.left,
            r.top,
            r.right,
            r.bottom,
            new int[] {0xff775340, LEATHER, 0xff3f2a25},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(r, radius, radius, p);
    p.setShader(null);
    // 가죽 결·봉제선은 문구 영역 밖의 일정한 폭에 둔다.
    for (float y = r.top + 6; y < r.bottom - 5; y += 5) {
      line(c, r.left + 6, y, r.right - 6, y + 1, 0x0ffff1d7, 1);
    }
    RectF seam = new RectF(r);
    seam.inset(5, 5);
    p.setPathEffect(new DashPathEffect(new float[] {4, 4}, 0));
    stroke(c, seam, 0xffb89a72, 1.2f, radius - 4);
    p.setPathEffect(null);
    stroke(c, r, 0xff35251e, 1.5f, radius);
  }

  void paper(Canvas c, RectF r, String label) {
    leather(c, r, 14);
    RectF page = new RectF(r);
    page.inset(9, 9);
    page.bottom -= 2;
    p.setShader(
        new LinearGradient(0, page.top, 0, page.bottom, 0xfffff9eb, PAPER, Shader.TileMode.CLAMP));
    c.drawRoundRect(page, 8, 8, p);
    p.setShader(null);
    stroke(c, page, 0xffcbb68c, 1, 8);
    // 본문의 평탄한 면과 금속 클립을 분리한다.
    RectF clip = new RectF(r.left + 22, r.top - 4, r.left + 68, r.top + 12);
    p.setShader(
        new LinearGradient(
            clip.left, 0, clip.right, 0, 0xffe6cb8b, 0xff98703b, Shader.TileMode.CLAMP));
    c.drawRoundRect(clip, 4, 4, p);
    p.setShader(null);
    if (label != null) {
      text(c, label, r.left + 25, r.top + 43, 18, MUTED, false);
      line(c, r.left + 24, r.top + 55, r.right - 24, r.top + 55, 0xffddc8a0, 1);
    }
  }

  void button(Canvas c, RectF r, String label, boolean primary, String icon) {
    leather(c, r, 13);
    RectF plate = new RectF(r);
    plate.inset(8, 7);
    plate.bottom -= 4;
    int top = primary ? 0xffe3b66d : 0xfffff8e9, bottom = primary ? 0xffc58b49 : 0xffe9d7b9;
    p.setShader(
        new LinearGradient(0, plate.top, 0, plate.bottom, top, bottom, Shader.TileMode.CLAMP));
    c.drawRoundRect(plate, 7, 7, p);
    p.setShader(null);
    stroke(c, plate, primary ? 0xff8e6033 : 0xffad8e5c, 1.4f, 7);
    RectF gleam = new RectF(plate);
    gleam.inset(2, 2);
    stroke(c, gleam, 0x99fff9e4, 1, 5);
    if (r.width() > 110) {
      for (float x : new float[] {r.left + 1, r.right - 11}) {
        RectF fitting = new RectF(x, r.top + 11, x + 10, r.bottom - 11);
        p.setShader(
            new LinearGradient(
                x,
                0,
                x + 10,
                0,
                new int[] {0xff795a38, 0xffecd08d, 0xffbb914f},
                null,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(fitting, 3, 3, p);
        p.setShader(null);
      }
      for (float x : new float[] {r.left + 5, r.right - 5}) {
        circle(c, x, r.centerY(), 2.2f, BRASS);
        circle(c, x - .5f, r.centerY() - .6f, .8f, 0xfff1d99b);
      }
    }
    RectF words = new RectF(plate);
    words.inset(8, 0);
    if (icon != null) {
      float size = Math.min(34, r.height() * .48f);
      sprite(
          c,
          "game-art/duel/icons/" + icon + ".png",
          new RectF(
              plate.left + 11,
              plate.centerY() - size / 2,
              plate.left + 11 + size,
              plate.centerY() + size / 2));
      words.left += size + 15;
    }
    centered(c, label, words, r.height() > 78 ? 30 : 22, primary ? 0xff38281d : INK, true);
  }

  void crest(Canvas c, float x, float y, float size) {
    circle(c, x, y, size, LEATHER);
    circle(c, x, y, size - 3, BRASS);
    circle(c, x, y, size - 6, TEAL);
    p.setColor(PAPER);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(3);
    c.drawCircle(x, y - size * .3f, size * .13f, p);
    c.drawLine(x, y - size * .16f, x, y + size * .42f, p);
    c.drawLine(x - size * .33f, y, x + size * .33f, y, p);
    RectF arc = new RectF(x - size * .4f, y - size * .06f, x + size * .4f, y + size * .48f);
    c.drawArc(arc, 0, 180, false, p);
    p.setStyle(Paint.Style.FILL);
  }

  void gear(Canvas c, RectF r) {
    button(c, r, "", false, null);
    p.setColor(TEAL);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(4);
    c.drawCircle(r.centerX(), r.centerY() - 2, 12, p);
    for (int i = 0; i < 8; i++) {
      double a = i * Math.PI / 4;
      c.drawLine(
          r.centerX() + (float) Math.cos(a) * 12,
          r.centerY() - 2 + (float) Math.sin(a) * 12,
          r.centerX() + (float) Math.cos(a) * 18,
          r.centerY() - 2 + (float) Math.sin(a) * 18,
          p);
    }
    p.setStyle(Paint.Style.FILL);
  }

  void title(Canvas c, boolean login) {
    cover(c, room, new RectF(0, 0, W, H));
    RectF journal = new RectF(54, 30, 642, H - 30);
    paper(c, journal, null);
    crest(c, 112, 87, 27);
    text(c, "갈매미 섬 항구 수비대", 155, 84, 20, MUTED, false);
    text(c, "인사 기록부", 155, 112, 17, MUTED, false);
    line(c, 84, 140, 610, 140, 0xffc9b188, 1);
    text(c, "갈매미맨 수비대", 84, 212, 44, INK, true);
    text(c, "수비조장의 모험 도감", 86, 250, 23, TEAL, false);
    if (login) {
      text(c, "카톡에서 받은 로그인 코드를 입력해요.", 86, 292, 20, MUTED, false);
      for (int i = 0; i < 6; i++) {
        RectF cell = new RectF(88 + i * 88, 310, 164 + i * 88, 365);
        fill(c, cell, 0xfffffbf2, 7);
        stroke(c, cell, 0xffb59c74, 1.5f, 7);
        centered(c, i < 2 ? new String[] {"1", "3"}[i] : "", cell, 26, INK, true);
      }
      button(c, new RectF(86, 385, 330, 447), "코드로 로그인", true, null);
      button(c, new RectF(342, 385, 608, 447), "카카오로 로그인", false, null);
    } else {
      text(c, "오늘도 출근 도장부터 찍어요.", 86, 292, 22, MUTED, false);
      text(c, "모으고, 발령하고, 한 마리씩 성장해요.", 86, 326, 21, INK, false);
      button(c, new RectF(86, H - 115, 610, H - 47), "로그인", true, "u-profile");
    }
    float bottom = H * .74f;
    sprite(c, "native/t2_guardian.webp", new RectF(690, 100, 1195, bottom));
    RectF tag = new RectF(770, H - 91, 1192, H - 34);
    paper(c, tag, null);
    centered(c, "갈매미는 늘 조금씩 허술해요.", new RectF(784, H - 85, 1182, H - 40), 21, INK, false);
    gear(c, new RectF(W - 90, 18, W - 24, 80));
  }

  void topBar(Canvas c) {
    RectF profile = new RectF(25, 18, 380, 92);
    paper(c, profile, null);
    crest(c, 66, 54, 24);
    text(c, "와로듀", 105, 52, 23, INK, true);
    text(c, "항구 수비조장 · 실적 6/19", 105, 77, 17, MUTED, false);
    String[] values = {"4,563", "0", "231"}, icons = {"u-snack", "u-peanut", "u-ticket"};
    float start = W - 575;
    for (int i = 0; i < 3; i++) {
      RectF r = new RectF(start + i * 160, 22, start + i * 160 + 150, 82);
      paper(c, r, null);
      sprite(
          c,
          "game-art/duel/icons/" + icons[i] + ".png",
          new RectF(r.left + 17, r.top + 13, r.left + 49, r.bottom - 13));
      centered(
          c,
          values[i],
          new RectF(r.left + 49, r.top + 7, r.right - 12, r.bottom - 8),
          22,
          INK,
          true);
    }
    gear(c, new RectF(W - 84, 21, W - 23, 82));
  }

  void home(Canvas c) {
    cover(c, room, new RectF(0, 0, W, H));
    topBar(c);
    float foot = H - 26;
    sprite(c, "game-art/duel/sprites/D1_s0_front.webp", new RectF(765, 105, 1117, H * .74f));
    RectF ledger = new RectF(29, 132, 468, Math.min(H - 132, 386));
    paper(c, ledger, "오늘의 근무 기록");
    text(c, "한 마리씩, 차근차근.", 56, 222, 28, INK, true);
    text(c, "태초의 갈매미맨 · Lv18", 56, 259, 22, INK, false);
    text(c, "승진까지 2레벨", 56, 293, 21, TEAL, false);
    RectF xp = new RectF(56, 309, 437, 321);
    fill(c, xp, 0xffded0b4, 6);
    fill(c, new RectF(56, 309, 333, 321), TEAL, 6);
    if (ledger.height() > 235) text(c, "다음 이야기 · 1부 먹물 이력서", 56, 356, 19, MUTED, false);
    String[] names = {"탐험", "뽑기", "수비대", "조합", "상점"},
        icons = {"mode-camp", "u-draw", "mode-normal", "u-emblem", "u-shop"};
    float width = 177;
    for (int i = 0; i < 5; i++)
      button(
          c,
          new RectF(25 + i * (width + 9), foot - 65, 25 + i * (width + 9) + width, foot),
          names[i],
          false,
          icons[i]);
    button(c, new RectF(959, foot - 77, 1255, foot), "플레이", true, "u-story");
    RectF next = new RectF(959, foot - 123, 1255, foot - 84);
    fill(c, next, PAPER, 7);
    stroke(c, next, 0xffb99a65, 1, 7);
    centered(c, "1A-3 1부 순찰 3", next, 18, INK, true);
  }

  void health(Canvas c, RectF r, String name, String level, boolean mine) {
    paper(c, r, null);
    text(c, name, r.left + 23, r.top + 36, 23, INK, true);
    text(c, level, r.right - 82, r.top + 35, 19, MUTED, false);
    RectF bar = new RectF(r.left + 23, r.top + 49, r.right - 23, r.top + 61);
    fill(c, bar, 0xffd9d2be, 6);
    fill(c, new RectF(bar.left, bar.top, bar.right - (mine ? 42 : 0), bar.bottom), 0xff4b967c, 6);
    text(c, mine ? "돌 · 168/188" : "바다 · 100%", r.left + 23, r.top + 87, 18, INK, false);
    if (r.height() > 119)
      text(
          c,
          mine ? "받는 피해 감소 −33% · 2턴" : "공격 약화 −25% · 1턴",
          r.left + 23,
          r.top + 114,
          17,
          TEAL,
          false);
  }

  void command(Canvas c, RectF r, String name, String icon, boolean primary) {
    button(c, r, "", primary, null);
    float size = Math.min(r.width() * .38f, r.height() * .47f);
    sprite(
        c,
        icon.equals("escape") ? "native/t2_gull.webp" : "game-art/duel/icons/" + icon + ".png",
        new RectF(r.centerX() - size / 2, r.top + 14, r.centerX() + size / 2, r.top + 14 + size));
    centered(
        c,
        name,
        new RectF(r.left + 12, r.bottom - 59, r.right - 12, r.bottom - 10),
        r.width() < 195 ? 29 : 32,
        INK,
        true);
  }

  void battle(Canvas c) {
    cover(c, asset("game-art/duel/bg/deepbattleb.webp"), new RectF(0, 0, W, H));
    RectF commands = new RectF(856, 17, 1261, H - 17);
    // 명령 버튼 뒤의 큰 패널은 두지 않고 개별 기록 카드만 띄운다.
    float commandHeight = Math.min(181, (H - 183) / 2f);
    float gap = 11, top = H - 32 - commandHeight * 2 - gap, mid = top + commandHeight;
    RectF log = new RectF(870, 30, 1247, top - 14);
    paper(c, log, null);
    text(c, "현장 기록 · 5턴", 893, 59, 18, MUTED, false);
    text(c, "가시 깃털로 35 피해!", 893, 94, 22, INK, true);
    if (log.height() > 160) {
      text(c, "내 팀 · 받는 피해 감소 −33%", 893, 136, 19, TEAL, false);
      text(c, "상대 · 공격 약화 −25%", 893, 174, 19, 0xffa7463e, false);
      text(c, "교체와 현장 판단으로 다음 수를 골라요.", 893, 218, 18, MUTED, false);
    }
    command(c, new RectF(872, top, 1055, mid), "공격", "mode-pvp", true);
    command(c, new RectF(1066, top, 1247, mid), "아이템", "u-bag", false);
    command(c, new RectF(872, mid + gap, 1055, H - 32), "교체", "freeswitch", false);
    command(c, new RectF(1066, mid + gap, 1247, H - 32), "도망가기", "escape", false);
    health(c, new RectF(25, 20, 376, 153), "쭈라켄", "Lv13", false);
    RectF turn = new RectF(415, 20, 656, 69);
    button(c, turn, "", false, null);
    centered(c, "상성 ×0.67 불리", turn, 21, 0xffa7463e, true);
    sprite(c, "game-art/duel/sprites/V6_front.webp", new RectF(470, 126, 807, H - 164));
    sprite(c, "game-art/duel/sprites/D1_s0_back.webp", new RectF(31, H * .40f, 366, H - 23));
    health(c, new RectF(398, H - 153, 815, H - 22), "태초의 갈매미맨", "Lv18", true);
  }

  void components(Canvas c) {
    fill(c, new RectF(0, 0, W, H), 0xffe5d9c2, 0);
    text(c, "항구 수비조장의 모험 도감", 36, 58, 29, INK, true);
    text(c, "가죽 표지 · 크림 서류 · 황동 클립", 36, 94, 21, MUTED, false);
    button(c, new RectF(37, 126, 395, 207), "출전하기", true, "u-story");
    button(c, new RectF(420, 126, 778, 207), "성장 확인", false, "learn");
    RectF selected = new RectF(803, 126, 1161, 207);
    button(c, selected, "편성 선택됨", false, null);
    stroke(c, new RectF(814, 136, 1150, 191), TEAL, 3, 7);
    button(c, new RectF(37, 230, 190, 292), "−", false, null);
    button(c, new RectF(202, 230, 355, 292), "+", false, null);
    button(c, new RectF(420, 230, 778, 292), "확률 보기", false, null);
    c.saveLayerAlpha(new RectF(803, 230, 1161, 292), 110);
    button(c, new RectF(803, 230, 1161, 292), "재추첨 완료", false, null);
    c.restore();
    paper(c, new RectF(37, 330, 778, H - 30), "정보 카드");
    text(c, "장식은 테두리에, 수치와 효과는 밝은 종이에.", 62, 421, 24, INK, false);
    text(c, "돌  /  전용기  /  위력 60", 62, 460, 22, TEAL, false);
    crest(c, 923, 390, 50);
    text(c, "항구 수비대", 1000, 391, 26, INK, true);
    text(c, "출근 도장 · 발령표 · 직무 표창", 824, 477, 21, MUTED, false);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    overflowing.clear();
    float scale = Math.min(getWidth() / 1280f, getHeight() / 524f);
    H = getHeight() / scale;
    c.save();
    c.scale(scale, scale);
    if (scene.equals("title")) title(c, false);
    else if (scene.equals("login")) title(c, true);
    else if (scene.equals("home")) home(c);
    else if (scene.equals("battle")) battle(c);
    else components(c);
    c.restore();
  }
}
