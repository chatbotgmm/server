package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 항구 본부의 모험 도감 UI. 원본 캐릭터와 보상·조언·패럴랙스 동작은 유지한다. */
final class LobbyView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final HomeGullDrag parallax;
  final SceneMotion motion;
  final JournalAmbient ambient;
  final Runnable motionChanged;
  final Paint p = new Paint(3);
  final Path mapRoute = new Path();
  final RectF dockFrame = new RectF();
  final RectF heroFrame = new RectF(), heroPose = new RectF(), heroContact = new RectF();
  final Paint.FontMetrics canvasMetrics = new Paint.FontMetrics();
  final ArrayList<Button> buttons = new ArrayList<>();
  final String room;
  final HomeArt homeArt;
  final Button gull;
  final TextView tip;
  final LinearLayout growthCard;
  final LinearLayout growthInfo;
  final TextView growthName, growthMeta, growthXp, growthNeed, growthSummary, growthCompactXp;
  final ProgressBar growthProgress;
  final Button growthAction;
  final Runnable hideTip = this::hideGullTip;
  final boolean[] badges = new boolean[14];
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
  final Map<String, Rect> visibleBounds = new HashMap<>();
  static final String[] NAMES = {"탐험", "뽑기", "방범대", "조합", "상점"};
  static final String[] IDS = {"expedition", "draw", "collection", "craft", "shop"};
  static final String[] ICONS = {"mode-camp", "u-draw", "mode-normal", "u-emblem", "u-shop"};
  final RectF[] boxes = new RectF[14];

  static String room(MainActivity host) {
    if (host.lobbyRoom == null)
      host.lobbyRoom =
          new String[] {"attic", "cabin", "cafe", "greenhouse"}[new Random().nextInt(4)];
    return host.lobbyRoom;
  }

  static void warm(MainActivity host) {
    host.art.asset("release-v1/characters/home-guide-map.png");
    for (String icon :
        new String[] {
          "u-story",
          "u-profile",
          "u-snack",
          "u-peanut",
          "u-ticket",
          "u-attend",
          "u-settings",
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
    homeArt = new HomeArt(host);
    motion = new SceneMotion(this, host);
    ambient = new JournalAmbient(this, host, motion);
    parallax = new HomeGullDrag(this, host.dp(4), host.dp(1.2f));
    motionChanged =
        () -> {
          if (!SceneMotion.enabled(host) || !host.motionResumed) parallax.close();
        };
    room = room(host);
    // Representative guide is bundled locally; no room-dependent network image.
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
    add("전투 편성", this::openTeam);
    add("항구", () -> host.load("home"));
    buttons.get(12).setTag("lobby-team");
    buttons.get(13).setTag("lobby-harbor");
    buttons.get(13).setSelected(true);
    growthCard = new LinearLayout(host);
    growthCard.setOrientation(LinearLayout.VERTICAL);
    growthCard.setPadding(host.dp(12), host.dp(9), host.dp(12), host.dp(10));
    growthCard.setBackground(homeArt.surfaceDrawable("ui/button-secondary.png"));
    growthCard.setTag("lobby-growth-card");
    LinearLayout details = new LinearLayout(host);
    details.setOrientation(LinearLayout.VERTICAL);
    TextView growthHeading = host.text("오늘의 근무 기록", 13);
    growthHeading.setTextColor(JournalTheme.MUTED);
    details.addView(growthHeading);
    growthName = host.text("", 19);
    growthName.setTypeface(host.headline);
    growthName.setMaxLines(1);
    growthName.setEllipsize(android.text.TextUtils.TruncateAt.END);
    details.addView(growthName);
    growthMeta = host.text("", 13);
    growthMeta.setTextColor(JournalTheme.MUTED);
    details.addView(growthMeta);
    growthXp = host.text("", 14);
    details.addView(growthXp);
    growthProgress = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
    growthProgress.setProgressTintList(
        android.content.res.ColorStateList.valueOf(JournalTheme.TEAL));
    growthProgress.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(0xffddd2ba));
    LinearLayout.LayoutParams meterSlot = new LinearLayout.LayoutParams(-1, host.dp(6));
    meterSlot.setMargins(host.dp(8), host.dp(3), host.dp(8), host.dp(3));
    details.addView(growthProgress, meterSlot);
    growthNeed = host.text("", 12);
    growthNeed.setTextColor(JournalTheme.MUTED);
    details.addView(growthNeed);
    growthInfo = details;
    growthCard.addView(growthInfo, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout growthControls = new LinearLayout(host);
    growthControls.setGravity(Gravity.CENTER_VERTICAL);
    growthSummary = host.text("", 14);
    growthSummary.setTextColor(JournalTheme.TEAL);
    growthSummary.setMaxLines(2);
    growthSummary.setIncludeFontPadding(false);
    growthSummary.setPadding(host.dp(6), 0, host.dp(6), 0);
    growthSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
    growthControls.addView(growthSummary, new LinearLayout.LayoutParams(0, -2, 1));
    growthAction = host.button("성장", this::openGrowth);
    growthAction.setTag("lobby-growth");
    growthAction.setTextSize(host.uiText(16));
    growthAction.setPadding(host.dp(8), host.dp(3), host.dp(8), host.dp(6));
    growthAction.setBackground(homeArt.surfaceDrawable("ui/button-primary.png"));
    growthAction.setTextColor(Color.WHITE);
    growthControls.addView(growthAction, new LinearLayout.LayoutParams(host.dp(88), host.dp(48)));
    growthCard.addView(growthControls, new LinearLayout.LayoutParams(-1, host.dp(48)));
    growthCompactXp = host.text("", 12);
    growthCompactXp.setTag("lobby-growth-core");
    growthCompactXp.setIncludeFontPadding(false);
    growthCompactXp.setSingleLine(true);
    growthCompactXp.setEllipsize(android.text.TextUtils.TruncateAt.END);
    growthCompactXp.setTextColor(JournalTheme.INK);
    growthCompactXp.setPadding(host.dp(6), host.dp(4), host.dp(6), 0);
    growthCard.addView(growthCompactXp, new LinearLayout.LayoutParams(-1, -2));
    addView(growthCard);
    refreshGrowth();
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

  void openTeam() {
    host.selectionMode = "story";
    host.menus.teamDraft = null;
    host.team();
    host.menus.teamReturn = "home";
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
    // Only the character region responds to dragging; menu touches stay fixed.
    return parallax.intercept(e, heroBox(), true) || super.onInterceptTouchEvent(e);
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
    ambient.attach();
    if (host.journalTime != null) host.journalTime.addListener(motionChanged);
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
    ambient.detach();
    if (host.journalTime != null) host.journalTime.removeListener(motionChanged);
    live = false;
    dailyLoading = false;
    removeCallbacks(refreshBadges);
    removeCallbacks(hideTip);
    tip.setVisibility(View.GONE);
    motion.close();
    parallax.close();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    ambient.resize(width, height);
  }

  void positions(int w, int h) {
    boolean tablet = w / (float) h < 1.85f;
    float margin = host.dp(tablet ? 24 : 16), gap = host.dp(8);
    float hit = host.dp(48), header = hit;
    float navH = Math.max(hit, Math.min(host.dp(64), h * .1262f));
    float navW = (w - margin * 2 - gap * 5) / 6;
    int[] dock = {13, 0, 1, 2, 3, 4};
    for (int i = 0; i < dock.length; i++) {
      float x = margin + i * (navW + gap);
      boxes[dock[i]] = new RectF(x, h - margin - navH, x + navW, h - margin);
    }
    float stackLeft = w * (tablet ? .70625f : .699454f);
    float stackTop = h * (tablet ? .20f : .213592f);
    float stackH = Math.max(hit, h * (tablet ? .09f : .145631f));
    float stackGap = Math.max(host.dp(12), h * (tablet ? .04f : .038835f));
    int[] stack = {5, 12, 6};
    for (int i = 0; i < stack.length; i++) {
      float y = stackTop + i * (stackH + stackGap);
      boxes[stack[i]] = new RectF(stackLeft, y, w - margin, y + stackH);
    }
    boxes[8] = new RectF(w - margin - hit, margin, w - margin, margin + header);
    float currencyLeft = w * .49f, currencyRight = boxes[8].left - gap;
    float curW = (currencyRight - currencyLeft - gap * 2) / 3;
    for (int i = 0; i < 3; i++) {
      float x = currencyLeft + i * (curW + gap);
      boxes[9 + i] = new RectF(x, margin, x + curW, margin + header);
    }
    // A profile chip needs only the portrait and name. Rank/details remain in the
    // existing profile popup and accessibility description, not a scene-wide bar.
    p.setTypeface(host.headline);
    p.setTextSize(canvasTextSize(host.dp(16), host.dp(24), true));
    float profileWidth = Math.max(host.dp(148),
        p.measureText(host.me().optString("nickname", "수비조장")) + host.dp(64));
    profileWidth = Math.min(Math.min(host.dp(220), currencyLeft - margin - gap), profileWidth);
    boxes[7] = new RectF(margin, margin, margin + profileWidth, margin + header);
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
    growthCompactXp.setTextSize(host.uiText(12));
    growthCompactXp.setPadding(host.dp(6), host.dp(4), host.dp(6), 0);
    RectF availableGrowth = growthAvailableBox();
    boolean detailedGrowth = availableGrowth.height() >= host.dp(140);
    growthNeed.setVisibility(View.VISIBLE);
    int infoWidth = Math.max(1, Math.round(availableGrowth.width()) - host.dp(24));
    int infoRoom = Math.max(0, Math.round(availableGrowth.height()) - host.dp(48 + 9 + 10));
    growthInfo.measure(
        MeasureSpec.makeMeasureSpec(infoWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    if (growthInfo.getMeasuredHeight() > infoRoom) {
      // 승진의 긴 조건은 성장 화면에서 확인한다. 기본 기록판은 스크롤하지 않는다.
      growthNeed.setVisibility(View.GONE);
      growthInfo.measure(
          MeasureSpec.makeMeasureSpec(infoWidth, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
    detailedGrowth &= growthInfo.getMeasuredHeight() <= infoRoom;
    growthInfo.setVisibility(detailedGrowth ? View.VISIBLE : View.GONE);
    growthCompactXp.setVisibility(detailedGrowth ? View.GONE : View.VISIBLE);
    RectF growth = growthBox();
    if (detailedGrowth) {
      growthCard.setPadding(host.dp(12), host.dp(9), host.dp(12), host.dp(10));
    } else {
      int rowHeight = growthCompactXp.getLineHeight() + host.dp(4);
      int room = Math.max(0, Math.round(growth.height()) - host.dp(48));
      if (rowHeight > room) {
        // 극단적으로 낮은 안전 영역에서도 글자를 압축하지 않는다. 성장 버튼은 그대로 남긴다.
        growthCompactXp.setVisibility(View.GONE);
        rowHeight = 0;
      }
      int padding = Math.max(0, room - rowHeight);
      int top = Math.min(host.dp(9), padding / 2);
      growthCard.setPadding(host.dp(12), top, host.dp(12), padding - top);
      growthCompactXp.getLayoutParams().height = rowHeight;
    }
    growthCard.setVisibility(growth.height() >= host.dp(64) ? View.VISIBLE : View.GONE);
    refreshGrowth();
    growthCard.measure(
        MeasureSpec.makeMeasureSpec(Math.max(1, (int) growth.width()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(Math.max(1, (int) growth.height()), MeasureSpec.EXACTLY));
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
    heroFrame.set(heroBox());
    RectF safe = host.safeBounds(getWidth(), getHeight()), area = gullBox();
    area.intersect(safe);
    gull.layout((int) area.left, (int) area.top, (int) area.right, (int) area.bottom);
    View resume = findViewWithTag("lobby-resume");
    if (resume != null) {
      int x = Math.round(safe.left + host.dp(24));
      int y = Math.round(boxes[7].bottom + host.dp(8));
      int width = Math.round(safe.width() * .58f);
      resume.layout(x, y, x + width, y + host.dp(48));
    }
    RectF growth = growthBox();
    growthCard.layout((int) growth.left, (int) growth.top, (int) growth.right, (int) growth.bottom);
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
              if (loaded != null) holdImage(path, loaded);
              invalidate();
            });
    if (ready != null) holdImage(path, ready);
    return ready;
  }

  void holdImage(String path, Bitmap bitmap) {
    visibleArt.put(path, bitmap);
    if (path.startsWith("bg/") && path.endsWith("char.webp"))
      visibleBounds.put(path, host.art.alpha(bitmap, "/duel/" + path));
  }

  boolean ready() {
    return homeArt.ready() && homeArt.hero() != null && homeArt.heroBounds() != null;
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
    JournalTheme.drawPaper(
        c, r, host.getResources().getDisplayMetrics().density, JournalTheme.WOOD, color);
  }

  void label(Canvas c, String text, float x, float y, float size, boolean heading) {
    label(c, text, x, y, size, heading, JournalTheme.INK);
  }

  void label(Canvas c, String text, float x, float y, float size, boolean heading, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setAlpha(255);
    p.setTypeface(heading ? host.headline : host.prose);
    p.setTextSize(size);
    p.setTextAlign(Paint.Align.CENTER);
    p.setColor(color);
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

  float canvasTextSize(float basePixels, float maxHeight, boolean heading) {
    android.util.DisplayMetrics metrics = host.getResources().getDisplayMetrics();
    float size = basePixels * metrics.scaledDensity / metrics.density;
    p.setTypeface(heading ? host.headline : host.prose);
    p.setTextSize(size);
    p.getFontMetrics(canvasMetrics);
    float height = canvasMetrics.descent - canvasMetrics.ascent;
    return height > maxHeight ? size * maxHeight / height : size;
  }

  float centeredBaseline(float centerY, float size, boolean heading) {
    p.setTypeface(heading ? host.headline : host.prose);
    p.setTextSize(size);
    p.getFontMetrics(canvasMetrics);
    return centerY - (canvasMetrics.ascent + canvasMetrics.descent) / 2;
  }

  void command(Canvas c, int index, String name, String icon, boolean primary) {
    RectF r = new RectF(boxes[index]);
    if (buttons.get(index).isPressed()) r.offset(0, host.dp(1));
    homeArt.surface(c, primary ? "ui/button-primary.png" : "ui/button-secondary.png", r);
    boolean small = index == 8;
    float size = Math.min(host.dp(small ? 25 : 36), r.height() * .55f);
    float inset = host.dp(small ? 10 : 14);
    if (icon != null) homeArt.icon(c, "icons/" + icon + ".png",
        new RectF(r.left + inset, r.centerY() - size / 2,
            r.left + inset + size, r.centerY() + size / 2));
    if (small && icon != null) return;
    float left = r.left + (icon == null ? host.dp(8) : inset + size + host.dp(5));
    float right = r.right - host.dp(12);
    float textSize = canvasTextSize(host.dp(index == 5 || index == 12 ? 21 : 16), r.height() - host.dp(18), true);
    p.setTypeface(host.headline); p.setTextSize(textSize);
    float measured = p.measureText(name);
    if (measured > right - left) textSize *= (right - left) / measured;
    label(c, name, (left + right) / 2, centeredBaseline(r.centerY(), textSize, true),
        textSize, true, primary ? Color.WHITE : 0xff203b45);
  }

  /** 메뉴의 터치 영역을 바꾸지 않고 얇은 목재 받침과 황동 가장자리만 더한다. */
  void navigationDock(Canvas c) {
    dockFrame.set(boxes[0].left, boxes[0].top, boxes[5].right, boxes[5].bottom);
    dockFrame.inset(-host.dp(4), -host.dp(4));
    JournalTheme.drawPanel(
        c, dockFrame, host.getResources().getDisplayMetrics().density, JournalTheme.WOOD, false);
  }

  void chevron(Canvas c, float x, float y, float size, int color) {
    p.setShader(null);
    p.setAlpha(255);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(1.5f));
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setColor(color);
    mapRoute.reset();
    mapRoute.moveTo(x - size / 2, y - size);
    mapRoute.lineTo(x + size / 2, y);
    mapRoute.lineTo(x - size / 2, y + size);
    c.drawPath(mapRoute, p);
    p.setStyle(Paint.Style.FILL);
    p.setStrokeCap(Paint.Cap.BUTT);
    p.setStrokeJoin(Paint.Join.MITER);
  }

  /** C 시안의 출항 표식. 글자 없는 판화만 그리고 실제 플레이 행동은 기존 버튼이 맡는다. */
  void ship(Canvas c, RectF area) {
    int save = c.save();
    c.translate(area.left, area.top);
    c.scale(area.width() / 100, area.height() / 100);
    p.setShader(null);
    p.setAlpha(255);
    p.setStrokeWidth(2.6f);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStyle(Paint.Style.FILL);
    p.setColor(JournalTheme.PAPER);
    mapRoute.reset();
    mapRoute.moveTo(48, 17);
    mapRoute.lineTo(26, 58);
    mapRoute.lineTo(48, 58);
    mapRoute.close();
    mapRoute.moveTo(57, 25);
    mapRoute.lineTo(80, 58);
    mapRoute.lineTo(57, 58);
    mapRoute.close();
    c.drawPath(mapRoute, p);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(JournalTheme.BRASS);
    c.drawPath(mapRoute, p);
    p.setColor(JournalTheme.PAPER);
    c.drawLine(53, 10, 53, 70, p);
    c.drawLine(53, 11, 68, 15, p);
    c.drawLine(53, 20, 68, 15, p);
    mapRoute.reset();
    mapRoute.moveTo(16, 68);
    mapRoute.lineTo(86, 68);
    mapRoute.lineTo(75, 82);
    mapRoute.lineTo(30, 82);
    mapRoute.close();
    p.setStyle(Paint.Style.FILL);
    p.setColor(JournalTheme.BRASS);
    c.drawPath(mapRoute, p);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(JournalTheme.PAPER);
    c.drawPath(mapRoute, p);
    mapRoute.reset();
    mapRoute.moveTo(13, 91);
    mapRoute.cubicTo(25, 83, 30, 98, 44, 90);
    mapRoute.cubicTo(56, 83, 63, 98, 78, 90);
    mapRoute.lineTo(88, 91);
    c.drawPath(mapRoute, p);
    p.setStyle(Paint.Style.FILL);
    p.setStrokeCap(Paint.Cap.BUTT);
    p.setStrokeJoin(Paint.Join.MITER);
    c.restoreToCount(save);
  }

  RectF backgroundBox(Bitmap bg) {
    RectF box = new RectF();
    JournalAmbient.coverBounds(box, getWidth(), getHeight());
    return box;
  }

  RectF heroBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    boolean tablet = safe.width() / safe.height() < 1.85f;
    // Reserve a foreground stage beside the growth card, not a strip above it.
    // The paving/contact point stays visible above the bottom navigation.
    float left = safe.left + host.dp(tablet ? 40 : 24) + host.dp(18);
    float right = growthAvailableBox().left - host.dp(24 + 18);
    float top = safe.top + Math.max(host.dp(host.state.optBoolean("activeBattle") ? 132 : 90), safe.height() * .18f);
    float bottom = boxes[13].top - host.dp(20);
    float maxHeight = Math.max(1, Math.min(bottom - top - host.dp(6), safe.height() * .56f));
    float maxWidth = Math.max(1, right - left);
    Rect source = homeArt.heroBounds();
    float aspect = source == null ? 1 : source.width() / (float) source.height();
    // Leave room for the small breathing scale as well as the bounded drag.
    float height = Math.min(maxHeight, maxWidth / aspect) / 1.004f;
    float width = height * aspect, center = (left + right) / 2;
    return new RectF(center - width / 2, bottom - height, center + width / 2, bottom);
  }

  RectF heroDrawBox(float pulse, float dragX, float dragY) {
    RectF target = new RectF();
    heroDrawBox(heroBox(), target, pulse, dragY);
    return target;
  }

  private void heroDrawBox(RectF area, RectF target, float pulse, float dragY) {
    float width = area.width() * (1 + .004f * pulse);
    float height = area.height() * (1 - .004f * pulse);
    target.set(area.centerX() - width / 2, area.bottom - height,
        area.centerX() + width / 2, area.bottom);
    // The feet never translate. Vertical drag only adds a tiny planted compression.
    float compression = Math.max(-host.dp(1.2f), Math.min(host.dp(1.2f), dragY));
    target.top += compression;
  }

  RectF heroShadowBox(RectF target) {
    RectF shadow = new RectF();
    heroShadowBox(target, shadow);
    return shadow;
  }

  private void heroShadowBox(RectF target, RectF shadow) {
    float center = target.centerX() - target.width() * .055f;
    float radius = target.width() * .23f;
    float height = Math.max(host.dp(8), target.height() * .035f);
    shadow.set(center - radius, target.bottom - height * .85f,
        center + radius, target.bottom + height * .15f);
  }

  void character(Canvas c) {
    Bitmap bitmap = homeArt.hero();
    Rect source = homeArt.heroBounds();
    if (bitmap == null || source == null) return;
    if (heroFrame.isEmpty()) return;
    heroDrawBox(heroFrame, heroPose, motion.pulse(3600, 0), parallax.y);
    heroShadowBox(heroFrame, heroContact);
    RectF target = heroPose, shadow = heroContact;
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0x180f2430);
    c.drawOval(shadow, p);
    shadow.inset(shadow.width() * .15f, shadow.height() * .18f);
    p.setColor(0x240f2430);
    c.drawOval(shadow, p);
    p.setShader(null);
    p.setAlpha(255);
    int pose = c.save();
    // Shear about the contact line: upper body leans at most 4dp, feet stay planted.
    float lean = Math.max(-host.dp(4), Math.min(host.dp(4), parallax.x));
    c.translate(target.centerX(), target.bottom);
    c.skew(-lean / Math.max(1, target.height()), 0);
    c.translate(-target.centerX(), -target.bottom);
    c.drawBitmap(bitmap, source, target, p);
    c.restoreToCount(pose);
  }

  void loosePaper(Canvas c) {
    // 배경 소품만 중간 깊이로 움직이고 기록 카드와 모든 누르는 요소는 고정한다.
    float width = host.dp(116), height = host.dp(46);
    // 출석·미션 버튼 아래에 깔려 겹쳐 보이지 않도록 버튼 오른쪽 빈 바닥에 둔다(회전·움직임 여유 14dp).
    RectF daily = boxes[6];
    if (daily == null || daily.isEmpty()) return;
    float left = daily.right + host.dp(14);
    RectF page = new RectF(left, daily.centerY() - height / 2, left + width, daily.centerY() + height / 2);
    RectF dock = boxes[0];
    if (dock != null && page.bottom + host.dp(6) > dock.top) page.offset(0, dock.top - host.dp(6) - page.bottom);
    if (page.right > getWidth() * .5f) return;
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
    // 움직이는 소품에는 항로만 인쇄한다. 실제 성장·재화 정보와 메뉴는 고정된 UI에 둔다.
    int clip = c.save();
    c.clipRect(
        page.left + host.dp(5),
        page.top + host.dp(5),
        page.right - host.dp(5),
        page.bottom - host.dp(5));
    p.setColor(0x386e654d);
    p.setStrokeWidth(host.dp(.5f));
    for (int i = 1; i < 4; i++) {
      float x = page.left + page.width() * i / 4;
      c.drawLine(x, page.top + host.dp(4), x, page.bottom - host.dp(4), p);
    }
    c.drawLine(page.left, page.centerY(), page.right, page.centerY(), p);
    p.setColor(0x9c625239);
    p.setStrokeWidth(host.dp(.9f));
    mapRoute.reset();
    mapRoute.moveTo(page.left + page.width() * .12f, page.bottom - host.dp(9));
    mapRoute.cubicTo(
        page.left + page.width() * .38f,
        page.top + host.dp(7),
        page.left + page.width() * .58f,
        page.bottom - host.dp(7),
        page.right - host.dp(13),
        page.top + host.dp(11));
    c.drawPath(mapRoute, p);
    c.drawCircle(page.right - host.dp(13), page.top + host.dp(11), host.dp(2), p);
    c.restoreToCount(clip);
    p.setStyle(Paint.Style.FILL);
    c.restore();
  }

  String growthKey() {
    JSONArray team = host.list(host.state, "team");
    for (String action : new String[] {"evolve", "promo", "learn"})
      for (int i = 0; i < team.length(); i++) {
        String key = team.optString(i);
        if (host.list(host.unit(key), action).length() > 0) return key;
      }
    return team.optString(0, "");
  }

  void openGrowth() {
    String key = growthKey();
    if (key.isEmpty()) host.load("collection");
    else {
      host.currentKey = key;
      host.menus.unitReturn = "home";
      host.load("unit");
    }
  }

  RectF growthBox() {
    RectF box = growthAvailableBox();
    if (growthInfo.getVisibility() == View.GONE) {
      float content =
          host.dp(48 + 9 + 10) + growthCompactXp.getLineHeight() + growthCompactXp.getPaddingTop();
      box.bottom = box.top + Math.min(box.height(), content);
    }
    return box;
  }

  RectF growthAvailableBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    float bottom = boxes[13].top - host.dp(8);
    // Reserve the measured single-line XP row at the user's font scale.
    // The 48dp action remains intact; growing text must never silently hide XP.
    float height = Math.max(host.dp(72),
        host.dp(48 + 9 + 10) + growthCompactXp.getLineHeight() + growthCompactXp.getPaddingTop());
    float right = boxes[5].left - host.dp(24);
    float width = Math.min(host.dp(300), Math.max(host.dp(232), safe.width() * .24f));
    return new RectF(right - width, bottom - height, right, bottom);
  }

  void setText(TextView view, String value) {
    if (!view.getText().toString().equals(value)) view.setText(value);
  }

  void refreshGrowth() {
    String key = growthKey();
    JSONObject unit = key.isEmpty() ? null : host.unit(key);
    if (unit == null) {
      setText(growthName, "첫 수비대를 편성해요");
      setText(growthMeta, "보유한 동료 중 3마리를 골라요");
      setText(growthXp, "함께 전투하며 경험치를 쌓아요");
      setText(growthNeed, "수비대에서 동료를 확인해요");
      setText(growthSummary, "첫 동료부터 차근차근");
      setText(growthCompactXp, "동료 3마리를 편성해요");
      growthProgress.setProgress(0);
      setText(growthAction, "수비대");
    } else {
      setText(growthName, unit.optString("name"));
      String role = unit.optString("role");
      role =
          role.equals("ATTACK")
              ? "공격"
              : role.equals("DEFENSE") ? "수비" : role.equals("UTILITY") ? "유틸" : role;
      setText(
          growthMeta,
          "Lv" + unit.optInt("level", 1) + " · " + unit.optString("attr") + " · " + role);
      int xp = unit.optInt("xp"), next = unit.optInt("xpNext");
      setText(
          growthXp,
          next > 0 ? "경험치 " + String.format(Locale.KOREA, "%,d / %,d", xp, next) : "최고 레벨 · 성장 완료");
      setText(
          growthCompactXp,
          "Lv"
              + unit.optInt("level", 1)
              + " · "
              + (next > 0 ? "경험치 " + String.format(Locale.KOREA, "%,d / %,d", xp, next) : "최고 레벨"));
      growthProgress.setMax(Math.max(1, next));
      growthProgress.setProgress(next <= 0 ? 1 : Math.min(xp, next));
      setText(growthNeed, new NativeGrowthGuidance(host, new MenuKit(host)).promotionNeed(unit));
      String summary = NativeGrowthGuidance.actionSummary(unit);
      setText(
          growthSummary,
          growthInfo.getVisibility() == View.GONE ? unit.optString("name") : summary);
      setText(
          growthAction,
          host.list(unit, "evolve").length() > 0
              ? "승진"
              : host.list(unit, "promo").length() > 0 || host.list(unit, "learn").length() > 0
                  ? "기술"
                  : "성장");
    }
    growthAction.setContentDescription(
        growthName.getText()
            + " · "
            + growthMeta.getText()
            + " · "
            + growthXp.getText()
            + " · "
            + growthNeed.getText()
            + " · "
            + (unit == null ? growthSummary.getText() : NativeGrowthGuidance.actionSummary(unit))
            + " · 상세 열기");
    growthCompactXp.setContentDescription(growthCompactXp.getText());
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    boolean moving = SceneMotion.enabled(host) && host.motionResumed && motion.clock != null
        && isShown() && getWindowVisibility() == View.VISIBLE;
    if (!moving) parallax.close();
    homeArt.background(c, getWidth(), getHeight(), motion.millis / 1000d, moving);
    character(c);
    String[] icons = {"explore", "draw", "team", "combine", "shop"};
    for (int i = 0; i < 5; i++) command(c, i, NAMES[i], icons[i], false);
    command(c, 13, "항구", "harbor", true);
    command(c, 5, "플레이", "explore", true);
    command(c, 12, "전투 편성", "team", false);
    command(c, 6, "출석·미션", "daily", false);
    command(c, 8, "설정", "settings", false);
    RectF profile = boxes[7];
    homeArt.surface(c, "ui/button-primary.png", profile);
    fit(c, image("icons/u-profile.png"), new RectF(profile.left + host.dp(10),
        profile.top + host.dp(8), profile.left + host.dp(42), profile.bottom - host.dp(8)));
    String nick = host.me().optString("nickname", "수비조장"), summary = profileSummary();
    float cx = (profile.left + host.dp(47) + profile.right - host.dp(12)) / 2;
    float nickSize = canvasTextSize(host.dp(16), profile.height() - host.dp(16), true);
    label(c, fitted(nick, profile.width() - host.dp(65), nickSize, true), cx,
        centeredBaseline(profile.centerY(), nickSize, true), nickSize, true, Color.WHITE);
    buttons.get(7).setContentDescription("내 정보 · " + nick + " · " + summary);
    String[] keys = {"snack", "peanut", "credits"};
    String[] currencyIcons = {"shrimp-snack", "peanut", null};
    String[] currencyNames = {"새우gang", "땅콩", "뽑기권"};
    for (int i = 0; i < 3; i++) {
      RectF r = boxes[9 + i];
      homeArt.surface(c, "ui/button-primary.png", r);
      RectF iconBox = new RectF(r.left + host.dp(7), r.centerY() - host.dp(10),
          r.left + host.dp(27), r.centerY() + host.dp(10));
      if (currencyIcons[i] != null) homeArt.icon(c, "icons/" + currencyIcons[i] + ".png", iconBox);
      else fit(c, image("icons/u-ticket.png"), iconBox);
      String amount = String.format(Locale.KOREA, "%,d", host.me().optInt(keys[i]));
      float amountSize = host.dp(14), width = Math.max(1, r.width() - host.dp(38));
      p.setTypeface(host.headline); p.setTextSize(amountSize);
      if (p.measureText(amount) > width) amountSize *= width / p.measureText(amount);
      label(c, amount, (r.left + host.dp(29) + r.right - host.dp(7)) / 2,
          centeredBaseline(r.centerY(), amountSize, true), amountSize, true, Color.WHITE);
      buttons.get(9 + i).setContentDescription(currencyNames[i] + " " + amount + " · 상점");
    }
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
    refreshGrowth();
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
    float radius = host.dp(6) * (1 + .15f * motion.pulse(1600, 0));
    float x = Math.min(safe.right - radius - host.dp(2), target.right - host.dp(3)),
        y = Math.max(safe.top + radius + host.dp(2), target.top + host.dp(3));
    p.setStyle(Paint.Style.FILL);
    p.setColor(JournalTheme.NEGATIVE_INFO);
    c.drawCircle(x, y, radius, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(1.3f));
    p.setColor(JournalTheme.BRASS);
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

