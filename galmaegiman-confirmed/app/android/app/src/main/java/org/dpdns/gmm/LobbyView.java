package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 항구 본부의 모험 도감 UI. 원본 캐릭터와 보상·조언·패럴랙스 동작은 유지한다. */
final class LobbyView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final DragParallax parallax;
  final SceneMotion motion;
  final Paint p = new Paint(3);
  final ArrayList<Button> buttons = new ArrayList<>();
  final String room;
  final Button gull;
  final TextView tip;
  final Runnable hideTip = this::hideGullTip;
  final boolean[] badges = new boolean[12];
  long claimReadyMono = -1;
  int liveEpoch, tipAt = -1;
  String dailyDay = "";
  boolean live, dailyLoading;
  final Random random = new Random();
  final Runnable refreshBadges =
      new Runnable() {
        @Override
        public void run() {
          if (!current()) return;
          updateBadges();
          String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString();
          if (!dailyDay.equals(day) && !dailyLoading) fetchDaily();
          postDelayed(this, 1000);
        }
      };
  final Map<String, Bitmap> visibleArt = new HashMap<>();
  static final String[] NAMES = {"탐험", "뽑기", "수비대", "조합", "상점"};
  static final String[] IDS = {"expedition", "draw", "collection", "craft", "shop"};
  static final String[] ICONS = {"mode-camp", "u-draw", "mode-normal", "u-emblem", "u-shop"};
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
          "u-story",
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
    motion = new SceneMotion(this, host);
    parallax = new DragParallax(this, host.dp(18));
    room = room(host);
    for (String layer : new String[] {"base.jpg", "plat.webp", "char.webp"})
      image("bg/" + room + layer);
    setWillNotDraw(false);
    gull = host.button("갈매미 조언", this::showGullTip);
    gull.setTextColor(Color.TRANSPARENT);
    gull.setBackground(null);
    gull.setPadding(0, 0, 0, 0);
    gull.setTag("lobby-gull");
    addView(gull);
    for (int i = 0; i < 5; i++) {
      String id = IDS[i];
      add(NAMES[i], () -> host.load(id));
    }
    add("플레이", () -> host.load("modes"));
    add("출석·미션", () -> host.load("daily"));
    add("내 정보", () -> host.menus.extras.profile(buttons.get(7)));
    add("설정", () -> host.load("settings"));
    add("새우gang · 상점", () -> host.load("shop"));
    add("땅콩 · 상점", () -> host.load("shop"));
    add("뽑기권 · 상점", () -> host.load("shop"));
    for (int i = 0; i < 5; i++) buttons.get(i).setTag("lobby-" + IDS[i]);
    buttons.get(5).setTag("lobby-play");
    buttons.get(6).setTag("lobby-daily");
    buttons.get(7).setTag("lobby-profile");
    buttons.get(8).setTag("lobby-settings");
    tip = host.text("", host.getResources().getConfiguration().screenHeightDp < 600 ? 13 : 15);
    tip.setTypeface(host.prose);
    tip.setTextColor(MainActivity.INK);
    tip.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
    tip.setBackground(new GamePaperDrawable(host, 0xffb8823f));
    tip.setVisibility(View.GONE);
    tip.setTag("lobby-gull-tip");
    addView(tip);
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
      LayoutParams lp =
          new LayoutParams(
              Math.min(
                  host.dp(350),
                  Math.round(host.getResources().getDisplayMetrics().widthPixels * .37f)),
              host.dp(48),
              Gravity.TOP | Gravity.LEFT);
      lp.leftMargin = host.dp(12);
      lp.topMargin = host.dp(72);
      resume.setTag("lobby-resume");
      addView(resume, lp);
    }
  }

  void add(String name, Runnable click) {
    Button b = host.button(name, click);
    b.setTextColor(Color.TRANSPARENT);
    b.setBackground(null);
    b.setOnTouchListener(
        (v, e) -> {
          postInvalidateOnAnimation();
          return false;
        });
    b.setPadding(0, 0, 0, 0);
    buttons.add(b);
    addView(b);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    return parallax.intercept(
            e,
            new RectF(getWidth() * .16f, getHeight() * .2f, getWidth() * .85f, getHeight() * .75f),
            true)
        || super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return parallax.touch(e) || super.onTouchEvent(e);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    motion.start();
    live = true;
    liveEpoch = host.epoch;
    refreshBadges.run();
    host.api.call(
        "draw",
        null,
        (r, e) -> {
          if (!current() || e != null || r == null) return;
          claimReadyMono =
              android.os.SystemClock.elapsedRealtime() + Math.max(0, r.optLong("wait"));
          updateBadges();
        });
  }

  @Override
  protected void onDetachedFromWindow() {
    live = false;
    dailyLoading = false;
    removeCallbacks(refreshBadges);
    removeCallbacks(hideTip);
    tip.setVisibility(View.GONE);
    motion.close();
    parallax.close();
    super.onDetachedFromWindow();
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
    boxes[5] = new RectF(w - margin - playW, h - margin - bh, w - margin, h - margin);
    boxes[6] =
        new RectF(
            margin,
            h - margin - bh - host.dp(62),
            margin + Math.min(host.dp(154), w * .20f),
            h - margin - bh - host.dp(12));
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
    setPadding(
        host.root.getPaddingLeft(),
        host.root.getPaddingTop(),
        host.root.getPaddingRight(),
        host.root.getPaddingBottom());
    super.onMeasure(ws, hs);
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    positions(Math.round(safe.width()), Math.round(safe.height()));
    for (RectF box : boxes) box.offset(safe.left, safe.top);
    RectF gullRect = gullBox();
    gullRect.intersect(safe);
    gull.measure(
        MeasureSpec.makeMeasureSpec(
            Math.max(host.dp(48), (int) gullRect.width()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(
            Math.max(host.dp(48), (int) gullRect.height()), MeasureSpec.EXACTLY));
    if (tip.getVisibility() == View.VISIBLE)
      tip.measure(
          MeasureSpec.makeMeasureSpec(
              Math.round(Math.min(host.dp(300), safe.width() * .42f)), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(
              Math.max(host.dp(48), (int) safe.height() - host.dp(140)), MeasureSpec.AT_MOST));
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
    RectF safe = host.safeBounds(getWidth(), getHeight()), area = gullBox();
    area.intersect(safe);
    gull.layout((int) area.left, (int) area.top, (int) area.right, (int) area.bottom);
    if (tip.getVisibility() == View.VISIBLE) {
      int width = tip.getMeasuredWidth(), height = tip.getMeasuredHeight();
      int x = (int) Math.max(safe.left + host.dp(8), area.left - width - host.dp(10));
      x = Math.min(x, (int) safe.right - width - host.dp(8));
      int y =
          (int)
              Math.max(
                  safe.top + host.dp(56),
                  Math.min(safe.bottom - height - host.dp(90), area.top + area.height() * .12f));
      tip.layout(x, y, x + width, y + height);
    }
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
    JournalTheme.drawPanel(c, r, host.getResources().getDisplayMetrics().density, color, true);
  }

  void label(Canvas c, String text, float x, float y, float size, boolean heading) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setAlpha(255);
    p.setTypeface(heading ? host.headline : host.prose);
    p.setTextSize(size);
    p.setTextAlign(Paint.Align.CENTER);
    p.setColor(JournalTheme.INK);
    c.drawText(text, x, y, p);
  }

  String fitted(String text, float width, float size, boolean heading) {
    p.setTypeface(heading ? host.headline : host.prose);
    p.setTextSize(size);
    if (p.measureText(text) <= width) return text;
    String value = text;
    while (value.length() > 1 && p.measureText(value + "…") > width)
      value = value.substring(0, value.length() - 1);
    return value + "…";
  }

  void command(Canvas c, int index, String name, String icon, boolean primary) {
    RectF r = new RectF(boxes[index]);
    boolean pressed = buttons.get(index).isPressed();
    if (pressed) r.offset(0, host.dp(2));
    JournalTheme.drawButton(
        c,
        r,
        primary ? GameButtonDrawable.PRIMARY : JournalTheme.PAPER,
        host.dp(13),
        pressed ? host.dp(1) : host.dp(4),
        p);
    float size = Math.min(host.dp(32), r.height() * .48f);
    if (icon != null)
      fit(
          c,
          image("icons/" + icon + ".png"),
          new RectF(
              r.left + host.dp(11),
              r.centerY() - size / 2,
              r.left + host.dp(11) + size,
              r.centerY() + size / 2));
    float left = r.left + (icon == null ? host.dp(10) : host.dp(16) + size);
    float textSize = Math.min(host.dp(primary ? 24 : 19), r.width() * (primary ? .16f : .17f));
    label(
        c,
        fitted(name, r.right - left - host.dp(10), textSize, true),
        (left + r.right - host.dp(8)) / 2,
        r.centerY() + textSize * .32f,
        textSize,
        true);
  }

  RectF backgroundBox(Bitmap bg) {
    float s = Math.max(getWidth() / (float) bg.getWidth(), getHeight() / (float) bg.getHeight());
    s *= 1.035f;
    float width = bg.getWidth() * s, height = bg.getHeight() * s;
    return new RectF(
        (getWidth() - width) / 2,
        (getHeight() - height) / 2,
        (getWidth() + width) / 2,
        (getHeight() + height) / 2);
  }

  RectF heroBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    Bitmap bg = host.art.asset("journal/headquarters.png");
    RectF backdrop =
        bg == null ? new RectF(0, 0, getMeasuredWidth(), getMeasuredHeight()) : backgroundBox(bg);
    float center = backdrop.left + backdrop.width() * .72f,
        foot = backdrop.top + backdrop.height() * .76f;
    float width = Math.min(safe.width() * .40f, host.dp(500)),
        height = Math.min(safe.height() * .61f, host.dp(420));
    center =
        Math.min(safe.right - host.dp(14) - width / 2, Math.max(safe.left + width / 2, center));
    // 발은 배경 속 받침대에 놓는다. 하단 메뉴 기준으로 올리면 짧은 화면에서 공중에 뜬다.
    return new RectF(
        center - width / 2,
        Math.max(safe.top + host.dp(82), foot - height),
        center + width / 2,
        foot);
  }

  void character(Canvas c) {
    String path = "bg/" + room + "char.webp";
    Bitmap bitmap = image(path);
    if (bitmap == null) return;
    Rect source = host.art.alpha(bitmap, "/duel/" + path);
    RectF area = heroBox();
    float pulse = motion.pulse(3600, 0);
    float scale = Math.min(area.width() / source.width(), area.height() / source.height());
    float width = source.width() * scale * (1 + .004f * pulse),
        height = source.height() * scale * (1 - .004f * pulse);
    RectF target =
        new RectF(
            area.centerX() - width / 2,
            area.bottom - height,
            area.centerX() + width / 2,
            area.bottom);
    target.offset(parallax.x, parallax.y - getHeight() * .005f * pulse);
    p.setShader(null);
    p.setAlpha(255);
    c.drawBitmap(bitmap, source, target, p);
  }

  void loosePaper(Canvas c) {
    // 배경 소품만 중간 깊이로 움직이고 기록 카드와 모든 누르는 요소는 고정한다.
    float width = host.dp(116), height = host.dp(46);
    RectF safe = host.safeBounds(getWidth(), getHeight());
    RectF page =
        new RectF(
            safe.left + safe.width() * .16f,
            safe.bottom - host.dp(116),
            safe.left + safe.width() * .16f + width,
            safe.bottom - host.dp(116) + height);
    c.save();
    c.translate(parallax.x * .55f, parallax.y * .55f);
    c.rotate(-7, page.centerX(), page.centerY());
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0xfff3e8d3);
    c.drawRoundRect(page, host.dp(3), host.dp(3), p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(1));
    p.setColor(0xffbca783);
    c.drawRoundRect(page, host.dp(3), host.dp(3), p);
    p.setStyle(Paint.Style.FILL);
    c.restore();
  }

  void ledger(Canvas c) {
    JSONArray team = host.list(host.state, "team");
    JSONObject lead = team.length() == 0 ? null : host.unit(team.optString(0));
    RectF safe = host.safeBounds(getWidth(), getHeight());
    float top = boxes[7].bottom + host.dp(host.state.optBoolean("activeBattle") ? 76 : 18),
        bottom = Math.min(top + host.dp(186), boxes[6].top - host.dp(14));
    if (bottom - top < host.dp(112)) return;
    RectF r =
        new RectF(
            safe.left + host.dp(12),
            top,
            safe.left + Math.min(host.dp(410), safe.width() * .37f),
            bottom);
    panel(c, r, JournalTheme.PAPER);
    float left = r.left + host.dp(18), right = r.right - host.dp(18);
    label(c, "오늘의 근무 기록", r.centerX(), r.top + host.dp(27), host.dp(12), false);
    String name =
        lead == null
            ? "첫 수비대를 편성해요"
            : lead.optString("name", "갈매미") + " · Lv" + lead.optInt("level", 1);
    label(
        c,
        fitted(name, right - left, host.dp(17), true),
        r.centerX(),
        r.top + host.dp(54),
        host.dp(17),
        true);
    String growth = "수비대에서 동료를 골라요";
    if (lead != null) {
      if (host.list(lead, "evolve").length() > 0) growth = "승진할 수 있어요";
      else if (host.list(lead, "learn").length() > 0 || host.list(lead, "promo").length() > 0)
        growth = "새 기술을 배울 수 있어요";
      else
        growth =
            "경험치 "
                + String.format(
                    Locale.KOREA, "%,d / %,d", lead.optInt("xp"), lead.optInt("xpNext"));
    }
    label(
        c,
        fitted(growth, right - left, host.dp(13), false),
        r.centerX(),
        r.top + host.dp(78),
        host.dp(13),
        false);
    RectF track = new RectF(left, r.top + host.dp(90), right, r.top + host.dp(98));
    p.setColor(0xffd9cdae);
    c.drawRoundRect(track, host.dp(4), host.dp(4), p);
    float progress =
        lead == null || lead.optInt("xpNext") <= 0
            ? 0
            : Math.max(0, Math.min(1, lead.optInt("xp") / (float) lead.optInt("xpNext")));
    if (progress > 0) {
      p.setColor(JournalTheme.TEAL);
      c.drawRoundRect(
          new RectF(track.left, track.top, track.left + track.width() * progress, track.bottom),
          host.dp(4),
          host.dp(4),
          p);
    }
    if (r.height() > host.dp(142))
      label(c, "한 마리씩, 차근차근.", r.centerX(), r.bottom - host.dp(21), host.dp(13), false);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    Bitmap background = host.art.asset("journal/headquarters.png");
    if (background != null) {
      RectF target = backgroundBox(background);
      target.offset(parallax.x * .25f, parallax.y * .25f);
      p.setShader(null);
      p.setAlpha(255);
      c.drawBitmap(background, null, target, p);
    } else c.drawColor(JournalTheme.LEATHER);
    loosePaper(c);
    character(c);
    ledger(c);
    for (int i = 0; i < 5; i++) command(c, i, NAMES[i], ICONS[i], false);
    command(c, 5, "플레이", "u-story", true);
    command(c, 6, "출석·미션", null, false);
    command(c, 8, "설정", null, false);
    RectF profile = boxes[7];
    panel(c, profile, JournalTheme.PAPER);
    fit(
        c,
        image("icons/u-profile.png"),
        new RectF(
            profile.left + host.dp(9),
            profile.top + host.dp(10),
            profile.left + host.dp(39),
            profile.bottom - host.dp(10)));
    String nick = host.me().optString("nickname", "수비조장");
    float cx = (profile.left + host.dp(43) + profile.right - host.dp(7)) / 2;
    label(
        c,
        fitted(nick, profile.width() - host.dp(58), host.dp(17), true),
        cx,
        profile.top + host.dp(24),
        host.dp(17),
        true);
    label(c, profileSummary(), cx, profile.bottom - host.dp(9), host.dp(11), false);
    String[] keys = {"snack", "peanut", "credits"}, icons = {"u-snack", "u-peanut", "u-ticket"};
    for (int i = 0; i < 3; i++) {
      RectF r = boxes[9 + i];
      panel(c, r, JournalTheme.PAPER);
      fit(
          c,
          image("icons/" + icons[i] + ".png"),
          new RectF(
              r.left + host.dp(9),
              r.top + host.dp(12),
              r.left + host.dp(29),
              r.bottom - host.dp(12)));
      String amount = String.format(Locale.KOREA, "%,d", host.me().optInt(keys[i]));
      label(
          c,
          amount,
          r.left + r.width() * .64f,
          r.centerY() + host.dp(5),
          Math.min(host.dp(15), r.width() / Math.max(5, amount.length())),
          true);
    }
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
    RectF tag = new RectF(play.left, play.top - host.dp(52), play.right, play.top - host.dp(7));
    panel(c, tag, JournalTheme.PAPER);
    label(c, "다음 이야기", tag.centerX(), tag.top + host.dp(16), host.dp(9), false);
    label(
        c,
        fitted(next, tag.width() - host.dp(26), host.dp(12), true),
        tag.centerX(),
        tag.bottom - host.dp(10),
        host.dp(12),
        true);
    for (int i = 0; i < badges.length; i++) if (badges[i]) dot(c, boxes[i]);
  }

  String profileSummary() {
    JSONArray team = host.list(host.state, "team");
    if (team.length() == 0) return "아직 팀이 없어요 ▾";
    int sum = 0, total = 0, cleared = 0;
    for (int i = 0; i < team.length(); i++) sum += host.unit(team.optString(i)).optInt("level", 1);
    JSONArray chapters = host.list(host.state, "chapters");
    for (int i = 0; i < chapters.length(); i++) {
      JSONArray stages = host.list(chapters.optJSONObject(i), "stages");
      total += stages.length();
      for (int j = 0; j < stages.length(); j++)
        if (stages.optJSONObject(j).optBoolean("cleared")) cleared++;
    }
    return "수비대 Lv"
        + Math.round(sum / (float) team.length())
        + " · 실적 "
        + cleared
        + "/"
        + total
        + " ▾";
  }

  boolean current() {
    return live
        && host.epoch == liveEpoch
        && host.screen.equals("home")
        && !host.isFinishing()
        && host.root.indexOfChild(this) >= 0;
  }

  void fetchDaily() {
    dailyLoading = true;
    String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString();
    host.api.call(
        "daily",
        null,
        (r, e) -> {
          if (!current()) return;
          dailyLoading = false;
          dailyDay = day; // 오류일 때도 초마다 서버 요청을 반복하지 않는다.
          if (e != null || r == null) return;
          JSONObject attend = r.optJSONObject("attend");
          boolean ready = attend != null && !attend.optBoolean("today");
          JSONArray missions = host.list(r, "missions");
          for (int i = 0; i < missions.length(); i++) {
            JSONObject mission = missions.optJSONObject(i);
            ready |=
                !mission.optBoolean("done") && mission.optInt("value") >= mission.optInt("goal");
          }
          badges[6] = ready;
          buttons.get(6).setContentDescription(ready ? "출석·미션 · 받을 보상이 있어요" : "출석·미션");
          invalidate();
        });
  }

  void updateBadges() {
    long now = System.currentTimeMillis();
    long claimAt = parseTime(host.me().optString("claimReadyAt"));
    badges[1] =
        claimReadyMono >= 0
            ? android.os.SystemClock.elapsedRealtime() >= claimReadyMono
            : claimAt > 0 && now >= claimAt;
    JSONObject trip = host.me().optJSONObject("trip");
    long tripAt = trip == null ? 0 : parseTime(trip.optString("endsAt"));
    badges[0] = tripAt > 0 && now >= tripAt;
    boolean growth = false;
    JSONArray team = host.list(host.state, "team");
    for (int i = 0; i < team.length(); i++) {
      JSONObject unit = host.unit(team.optString(i));
      growth |= host.list(unit, "learn").length() > 0 || host.list(unit, "evolve").length() > 0;
    }
    badges[5] = growth;
    buttons.get(0).setContentDescription(badges[0] ? "탐험 · 귀환 보상을 받을 수 있어요" : "탐험");
    buttons.get(1).setContentDescription(badges[1] ? "뽑기 · 뽑기권을 받을 수 있어요" : "뽑기");
    buttons.get(5).setContentDescription(growth ? "플레이 · 배울 기술 또는 승진이 있어요" : "플레이");
    invalidate();
  }

  long parseTime(String value) {
    try {
      return java.time.Instant.parse(value).toEpochMilli();
    } catch (Exception ignored) {
      return 0;
    }
  }

  void dot(Canvas c, RectF target) {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    float radius = host.dp(7) * (1 + .2f * motion.pulse(1600, 0));
    float x = Math.min(safe.right - radius - host.dp(2), target.right - host.dp(3)),
        y = Math.max(safe.top + radius + host.dp(2), target.top + host.dp(3));
    p.setStyle(Paint.Style.FILL);
    p.setColor(0xffff5a5a);
    c.drawCircle(x, y, radius, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(2));
    p.setColor(0xfffffaf0);
    c.drawCircle(x, y, radius, p);
    p.setStyle(Paint.Style.FILL);
  }

  RectF gullBox() {
    return heroBox();
  }

  String[] gullTips() {
    return new String[] {
      "끼룩! 상대 속성에 강한 기술은 <b>1.5배</b>야. 기술 칸의 <b>▲유리</b>를 찾아봐!",
      "속성마다 <b>강한 상대 2개, 약한 상대 2개</b>가 있어. 상대 속성을 먼저 보자, 끼룩.",
      "체력은 전투가 끝나도 그대로야. <b>"
          + host.state.optInt("storyHpFullMinutes", 60)
          + "분</b>이면 가득 차고, 치료약으로 바로 채울 수도 있어!",
      "같은 시너지 갈매미 <b>2마리</b>를 같이 데려가면 <b>합동기</b>가 생겨. 대결당 한 번뿐이니 아껴 써!",
      "출석은 날마다 보상이 커져. 하루 빠지면 <b>1일째부터</b>니까 매일 들러 줘, 끼룩!",
      "전투를 시작하면 <b>증강체 3개</b> 중 하나를 골라. 마음에 안 들면 칸마다 한 번씩 다시 뽑을 수 있어.",
      "레벨이 오르면 <b>새 기술</b>을 배울 수 있어. 스토리 화면에서 갈매미를 눌러 봐!",
      "진 대결도 경험치를 조금 줘. 깬 스테이지는 다시 도전해서 레벨을 올려도 돼, 끼룩."
    };
  }

  void hideGullTip() {
    tip.setVisibility(View.GONE);
  }

  void showGullTip() {
    String[] tips = gullTips();
    tipAt =
        tipAt < 0
            ? random.nextInt(tips.length)
            : (tipAt + 1 + random.nextInt(tips.length - 1)) % tips.length;
    tip.setText(android.text.Html.fromHtml(tips[tipAt], android.text.Html.FROM_HTML_MODE_LEGACY));
    tip.setVisibility(View.VISIBLE);
    tip.setContentDescription("갈매미 조언 " + tip.getText());
    removeCallbacks(hideTip);
    postDelayed(hideTip, 6000);
    requestLayout();
    invalidate();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && tip.getVisibility() == View.VISIBLE) {
      tip.setVisibility(View.GONE);
      removeCallbacks(hideTip);
    }
    return super.dispatchTouchEvent(event);
  }
}
