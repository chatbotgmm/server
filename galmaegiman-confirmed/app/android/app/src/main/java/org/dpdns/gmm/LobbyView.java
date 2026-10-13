package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 웹 로비의 원본 방 레이어·하단 다섯 메뉴·큰 플레이 버튼을 그대로 사용한다. */
final class LobbyView extends FrameLayout {
  final MainActivity host;
  final Paint p = new Paint(3);
  final ArrayList<Button> buttons = new ArrayList<>();
  final String room;
  final Map<String, Bitmap> visibleArt = new HashMap<>();
  static final String[] NAMES = {"탐험", "뽑기", "수비대", "조합", "상점"};
  static final String[] IDS = {"expedition", "draw", "collection", "craft", "shop"};
  static final String[] ICONS = {"mode-camp", "u-draw", "mode-normal", "u-emblem", "u-shop"};
  static final int[] COLORS = {0xffa9d3a4, 0xfff29aa6, 0xffb9a2dc, 0xff9fcbe6, 0xfff5a98f};
  final RectF[] boxes = new RectF[12];

  static String room(MainActivity host) {
    if (host.lobbyRoom == null)
      host.lobbyRoom =
          new String[] {"attic", "cabin", "cafe", "greenhouse"}[new Random().nextInt(4)];
    return host.lobbyRoom;
  }

  static void warm(MainActivity host) {
    for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"})
      host.art.scene("/duel/bg/" + room(host) + layer, () -> {});
    for (String icon :
        new String[] {
          "btn-play",
          "btn-attend",
          "u-profile",
          "u-snack",
          "u-peanut",
          "u-ticket",
          "mode-camp",
          "u-draw",
          "mode-normal",
          "u-emblem",
          "u-shop"
        }) host.art.scene("/duel/icons/" + icon + ".png", () -> {});
  }

  LobbyView(MainActivity host) {
    super(host);
    this.host = host;
    room = room(host);
    for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"})
      image("bg/" + room + layer);
    setWillNotDraw(false);
    for (int i = 0; i < 5; i++) {
      String id = IDS[i];
      add(NAMES[i], () -> host.load(id));
    }
    add("플레이", () -> host.load("modes"));
    add("출석·미션", () -> host.load("daily"));
    add(
        "내 정보",
        () ->
            new GameDialog(host)
                .setTitle(host.me().optString("nickname", "수비조장"))
                .setItems(
                    new String[] {"계정 관리", "랭크 순위", "설정"},
                    (d, which) -> host.load(new String[] {"account", "pvp", "settings"}[which]))
                .setNegativeButton("닫기", null)
                .show());
    add("설정", () -> host.load("settings"));
    add("새우gang · 상점", () -> host.load("shop"));
    add("땅콩 · 상점", () -> host.load("shop"));
    add("뽑기권 · 상점", () -> host.load("shop"));
    if (host.state.optBoolean("activeBattle")) {
      Button resume =
          host.button(
              "진행 중인 전투로 돌아가기",
              () ->
                  host.request(
                      "native/battle/current",
                      null,
                      (r, e) -> {
                        if (r.has("session") && !r.isNull("session")) {
                          host.battle = r;
                          host.battleScreen();
                        } else host.load("home");
                      }));
      LayoutParams lp = new LayoutParams(-2, host.dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      lp.topMargin = host.dp(68);
      addView(resume, lp);
    }
  }

  void add(String name, Runnable click) {
    Button b = host.button(name, click);
    b.setTextColor(Color.TRANSPARENT);
    b.setBackground(null);
    b.setPadding(0, 0, 0, 0);
    buttons.add(b);
    addView(b);
  }

  void positions(int w, int h) {
    float margin = host.dp(12), gap = host.dp(6);
    float bh = Math.min(host.dp(70), Math.max(host.dp(48), h * .12f));
    float playW = Math.min(w * .21f, bh * 1.3f * 535 / 180);
    float menuW = (w - margin * 3 - playW - gap * 4) / 5;
    for (int i = 0; i < 5; i++)
      boxes[i] =
          new RectF(
              margin + i * (menuW + gap),
              h - margin - bh,
              margin + i * (menuW + gap) + menuW,
              h - margin);
    boxes[5] =
        new RectF(w - margin - playW, h - margin - playW * 180 / 535, w - margin, h - margin);
    boxes[6] = new RectF(margin, h * .42f, margin + bh * 1.4f, h * .42f + bh * 1.45f);
    boxes[7] = new RectF(margin, host.dp(10), Math.min(w * .33f, host.dp(265)), host.dp(62));
    boxes[8] = new RectF(w - margin - host.dp(52), host.dp(8), w - margin, host.dp(60));
    float curW = Math.min(host.dp(116), w * .11f);
    float right = boxes[8].left - gap;
    for (int i = 0; i < 3; i++) {
      float x = right - (3 - i) * (curW + gap);
      boxes[9 + i] = new RectF(x, host.dp(12), x + curW, host.dp(60));
    }
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    super.onMeasure(ws, hs);
    positions(getMeasuredWidth(), getMeasuredHeight());
    for (int i = 0; i < buttons.size(); i++) {
      RectF r = boxes[i];
      buttons
          .get(i)
          .measure(
              MeasureSpec.makeMeasureSpec((int) r.width(), MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(
                  Math.max(host.dp(48), (int) r.height()), MeasureSpec.EXACTLY));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    for (int i = 0; i < buttons.size(); i++) {
      RectF box = boxes[i];
      Button button = buttons.get(i);
      button.layout(
          (int) box.left,
          (int) box.top,
          (int) box.right,
          (int) box.top + button.getMeasuredHeight());
    }
  }

  Bitmap image(String path) {
    Bitmap held = visibleArt.get(path);
    if (held != null) return held;
    Bitmap ready =
        host.art.scene(
            "/duel/" + path,
            () -> {
              Bitmap loaded = host.art.peek("/duel/" + path);
              if (loaded != null) visibleArt.put(path, loaded);
              invalidate();
            });
    if (ready != null) visibleArt.put(path, ready);
    return ready;
  }

  boolean ready() {
    for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"})
      if (!visibleArt.containsKey("bg/" + room + layer)) return false;
    return true;
  }

  void fit(Canvas c, Bitmap image, RectF r) {
    if (image == null) return;
    float scale = Math.min(r.width() / image.getWidth(), r.height() / image.getHeight());
    float w = image.getWidth() * scale, h = image.getHeight() * scale;
    c.drawBitmap(
        image,
        null,
        new RectF(
            r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2),
        p);
  }

  void panel(Canvas c, RectF r, int color) {
    p.setColor(0xffc27c3d);
    c.drawRoundRect(
        r.left, r.top + host.dp(4), r.right, r.bottom + host.dp(4), host.dp(14), host.dp(14), p);
    p.setColor(0xffe2a764);
    c.drawRoundRect(r, host.dp(14), host.dp(14), p);
    RectF inside = new RectF(r);
    inside.inset(host.dp(5), host.dp(5));
    p.setColor(color);
    c.drawRoundRect(inside, host.dp(10), host.dp(10), p);
  }

  void label(Canvas c, String text, float x, float y, float size, boolean outline) {
    p.setTypeface(outline ? host.headline : host.font);
    p.setTextSize(size);
    p.setTextAlign(Paint.Align.CENTER);
    if (outline) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(host.dp(3));
      p.setColor(0xff5a3418);
      c.drawText(text, x, y, p);
      p.setStyle(Paint.Style.FILL);
    }
    p.setColor(outline ? Color.WHITE : MainActivity.INK);
    c.drawText(text, x, y, p);
  }

  @Override
  protected void onDraw(Canvas c) {
    int w = getWidth(), h = getHeight();
    Bitmap fallback = host.art.asset("harbor.jpg");
    if (fallback != null) {
      float scale = Math.max(w / (float) fallback.getWidth(), h / (float) fallback.getHeight());
      float bw = fallback.getWidth() * scale, bh = fallback.getHeight() * scale;
      c.drawBitmap(
          fallback, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), p);
    }
    for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"}) {
      Bitmap bg = image("bg/" + room + layer);
      if (bg == null) continue;
      float s = Math.max(w / (float) bg.getWidth(), h / (float) bg.getHeight());
      float bw = bg.getWidth() * s, bh = bg.getHeight() * s;
      c.drawBitmap(bg, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), p);
    }
    for (int i = 0; i < 5; i++) {
      RectF r = boxes[i];
      panel(c, r, COLORS[i]);
      float size = Math.min(host.dp(36), r.width() * .28f);
      fit(
          c,
          image("icons/" + ICONS[i] + ".png"),
          new RectF(
              r.left + host.dp(9),
              r.centerY() - size / 2,
              r.left + host.dp(9) + size,
              r.centerY() + size / 2));
      label(
          c,
          NAMES[i],
          r.left + r.width() * .63f,
          r.centerY() + host.dp(6),
          Math.min(host.dp(22), r.width() * .18f),
          true);
    }
    fit(c, image("icons/btn-play.png"), boxes[5]);
    fit(c, image("icons/btn-attend.png"), boxes[6]);
    RectF profile = boxes[7];
    panel(c, profile, 0xfffffaf0);
    fit(
        c,
        image("icons/u-profile.png"),
        new RectF(
            profile.left + host.dp(5),
            profile.top + host.dp(7),
            profile.left + host.dp(39),
            profile.bottom - host.dp(7)));
    String nick = host.me().optString("nickname", "수비조장");
    float nickWidth = profile.width() - host.dp(58);
    p.setTypeface(host.font);
    p.setTextSize(host.dp(17));
    while (nick.length() > 1 && p.measureText(nick) > nickWidth)
      nick = nick.substring(0, nick.length() - 1);
    float cx = (profile.left + host.dp(43) + profile.right - host.dp(7)) / 2;
    label(c, nick, cx, profile.top + host.dp(24), host.dp(17), false);
    label(c, "수비대 · 내 정보 ▾", cx, profile.bottom - host.dp(9), host.dp(11), false);
    fit(c, host.art.asset("title_settings_button_base.webp"), boxes[8]);
    fit(c, host.art.asset("title_settings_gear.webp"), boxes[8]);
    String[] keys = {"snack", "peanut", "credits"}, icons = {"u-snack", "u-peanut", "u-ticket"};
    for (int i = 0; i < 3; i++) {
      RectF r = boxes[9 + i];
      panel(c, r, 0xfffffaf0);
      fit(
          c,
          image("icons/" + icons[i] + ".png"),
          new RectF(
              r.left + host.dp(5),
              r.top + host.dp(11),
              r.left + host.dp(25),
              r.bottom - host.dp(11)));
      String amount = String.format(Locale.KOREA, "%,d", host.me().optInt(keys[i]));
      label(
          c,
          amount,
          r.left + r.width() * .61f,
          r.centerY() + host.dp(5),
          Math.min(host.dp(15), r.width() / Math.max(5, amount.length())),
          false);
    }
    // 버튼 위 꼬리표는 실제 진행을 보고, 아직 열려 있는 첫 스테이지를 표시한다.
    String next = "모든 이야기 완료";
    JSONArray chapters = host.list(host.state, "chapters");
    outer:
    for (int i = 0; i < chapters.length(); i++) {
      JSONArray stages = host.list(chapters.optJSONObject(i), "stages");
      for (int j = 0; j < stages.length(); j++) {
        JSONObject stage = stages.optJSONObject(j);
        if (stage.optBoolean("open") && !stage.optBoolean("cleared")) {
          next = stage.optString("id") + " " + stage.optString("title");
          break outer;
        }
      }
    }
    RectF play = boxes[5];
    RectF tag = new RectF(play.left, play.top - host.dp(42), play.right, play.top - host.dp(5));
    panel(c, tag, 0xfffffaf0);
    p.setTypeface(host.font);
    p.setTextSize(host.dp(13));
    while (next.length() > 1 && p.measureText(next) > tag.width() - host.dp(18))
      next = next.substring(0, next.length() - 1);
    label(c, "다음 이야기", tag.centerX(), tag.top + host.dp(13), host.dp(10), false);
    label(c, next, tag.centerX(), tag.bottom - host.dp(8), host.dp(13), false);
  }
}
