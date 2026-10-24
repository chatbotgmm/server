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
  private final GamePressState disabledControlTone = new GamePressState();
  private final Rect disabledControlBounds = new Rect();
  final Path mapRoute = new Path();
  final RectF dockFrame = new RectF();
  final RectF heroFrame = new RectF(), heroPose = new RectF(), heroContact = new RectF();
  final Paint.FontMetrics canvasMetrics = new Paint.FontMetrics();
  private final Paint groundShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Matrix groundShadowMatrix = new Matrix();
  private static final float[] GROUND_PROJECTION = {1, .92f * .32f, 0,
      0, -.39f * .32f, 0, 0, 0, 1};
  private final RectF groundShadowSource = new RectF();
  private final ColorFilter groundShadowColor = new PorterDuffColorFilter(0xff26323a, PorterDuff.Mode.SRC_IN);
  final ArrayList<Button> buttons = new ArrayList<>();
  final String room;
  final HomeArt homeArt;
  final HomeMenuArt menuArt;
  final RectF menuPanel = new RectF(), heroStage = new RectF(), tipRegion = new RectF();
  static final int[] MENU_ITEMS = {1, 2, 3, 0, 4, 12, 6, 14};
  boolean menuOpen, dismissGesture, rebirthUnlocked;
  android.app.AlertDialog rebirthIntro;
  Typeface homeHeading;
  final Button gull;
  final TextView tip;
  final Rect tipFrame = new Rect();
  boolean tipPaged;
  CharSequence tipCopy = "";
  int tipPageStart, tipNextPageStart;
  final Runnable hideTip = this::hideGullTip;
  final boolean[] badges = new boolean[16];
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
  static final String[] NAMES = {"탐험", "모집", "방범대", "조합", "상점"};
  static final String[] IDS = {"expedition", "draw", "collection", "craft", "shop"};
  static final String[] ICONS = {"mode-camp", "u-draw", "mode-normal", "u-emblem", "u-shop"};
  final RectF[] boxes = new RectF[16];

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
    homeArt = new HomeArt(host, this);
    menuArt = new HomeMenuArt(host, homeArt);
    // The approved home uses the bundled compact Jua face, not the workbench Noto.
    // MainActivity loads this asset explicitly; do not silently substitute a system font.
    homeHeading = host.font;
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
    add("플레이", host::openRosterBattle);
    add("출석·미션", () -> host.load("daily"));
    add("내 정보", () -> host.menus.extras.profile(buttons.get(7)));
    add("설정", () -> host.load("settings"));
    add("새우gang · 상점", () -> host.load("shop"));
    add("땅콩 · 상점", () -> host.load("shop"));
    add("지원서 · 상점", () -> host.load("shop"));
    for (int i = 0; i < 5; i++) buttons.get(i).setTag("lobby-" + IDS[i]);
    buttons.get(5).setTag("lobby-play");
    buttons.get(6).setTag("lobby-daily");
    buttons.get(7).setTag("lobby-profile");
    buttons.get(8).setTag("lobby-settings");
    add("전투 편성", this::openTeam);
    buttons.get(12).setTag("lobby-team");
    add("메뉴", () -> setMenuOpen(!menuOpen));
    buttons.get(13).setTag("lobby-menu-toggle");
    add("환생", () -> {if(rebirthUnlocked)NativeProgression.openRosterRebirth(host);});
    buttons.get(14).setTag("lobby-rebirth");buttons.get(14).setEnabled(false);buttons.get(14).setContentDescription("환생 잠김 · 제한 이상 개체 첫 획득 시 해제");
    add("우편함", () -> {host.menus.news.tab=0;host.load("news");});
    buttons.get(15).setTag("lobby-news");
    for (Button button : buttons) button.setId(View.generateViewId());
    for (int n = 0; n < MENU_ITEMS.length; n++) {
      Button item = buttons.get(MENU_ITEMS[n]);
      item.setVisibility(View.GONE);
      item.setAccessibilityTraversalAfter(n == 0 ? buttons.get(13).getId()
          : buttons.get(MENU_ITEMS[n-1]).getId());
      item.setNextFocusForwardId(n + 1 < MENU_ITEMS.length
          ? buttons.get(MENU_ITEMS[n+1]).getId() : buttons.get(13).getId());
      if (n % 4 > 0) item.setNextFocusLeftId(buttons.get(MENU_ITEMS[n-1]).getId());
      if (n % 4 < 3 && n+1 < MENU_ITEMS.length)
        item.setNextFocusRightId(buttons.get(MENU_ITEMS[n+1]).getId());
      if (n >= 4) item.setNextFocusUpId(buttons.get(MENU_ITEMS[n-4]).getId());
      item.setNextFocusDownId(n+4 < MENU_ITEMS.length
          ? buttons.get(MENU_ITEMS[n+4]).getId() : buttons.get(13).getId());
    }
    updateMenuDescription();
    tip = host.text("", host.getResources().getConfiguration().screenHeightDp < 600 ? 13 : 15);
    tip.setTypeface(host.prose);
    tip.setHorizontallyScrolling(false);
    tip.setGravity(Gravity.TOP | Gravity.START);
    tip.setMinHeight(host.dp(48));
    tip.setTextColor(MainActivity.INK);
    tip.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
    tip.setBackground(new GamePaperDrawable(host, 0xffb8823f));
    tip.setVisibility(View.GONE);
    tip.setTag("lobby-gull-tip");
    tip.setOnClickListener(v -> advanceGullTip());
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
      resume.setMaxLines(2);
      resume.setEllipsize(android.text.TextUtils.TruncateAt.END);
      resume.setContentDescription("진행 중인 전투로 돌아가기");
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
    Button b = host.button(name, () -> {
      if (menuOpen && !name.equals("메뉴")) setMenuOpen(false);
      click.run();
    });
    b.setTextColor(Color.TRANSPARENT);
    // Observe the actual native button state without replacing its click/write/sound behavior.
    b.setBackground(new android.graphics.drawable.Drawable() {
      @Override public boolean isStateful() { return true; }
      @Override protected boolean onStateChange(int[] states) {
        LobbyView.this.postInvalidateOnAnimation();
        return true;
      }
      @Override public void draw(Canvas canvas) { }
      @Override public void setAlpha(int alpha) { }
      @Override public void setColorFilter(ColorFilter filter) { }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    });
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
    // Lock eligibility at DOWN. A drag that starts on a menu must never become
    // a scene gesture when the finger leaves that button.
    return parallax.intercept(e, sceneDragBox(), sceneDragStart(e.getX(), e.getY()))
        || super.onInterceptTouchEvent(e);
  }

  RectF sceneDragBox() {
    RectF safe = host.safeBounds(getMeasuredWidth(), getMeasuredHeight());
    // Use measured controls, not screen percentages: reserve the complete header
    // and bottom navigation strips, including the blank gaps between controls.
    if (menuOpen || boxes[7] == null || boxes[13] == null) return new RectF();
    safe.top = Math.max(safe.top, Math.max(boxes[7].bottom, boxes[8].bottom));
    safe.bottom = Math.min(safe.bottom, boxes[13].top);
    return safe;
  }

  boolean sceneDragStart(float x, float y) {
    if (!sceneDragBox().contains(x, y)) return false;
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View child = getChildAt(i);
      // The guide still supports tap-for-advice and drag-for-weighted-pose.
      // Every other interactive child, including resume and the tip, keeps input.
      if (child == gull || child.getVisibility() != View.VISIBLE
          || (!child.isClickable() && !child.isLongClickable())) continue;
      if (x >= child.getLeft() && x < child.getRight()
          && y >= child.getTop() && y < child.getBottom()) return false;
    }
    return true;
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
    fetchRosterHome();
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
    if(rebirthIntro!=null){rebirthIntro.dismiss();rebirthIntro=null;}
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
    float density = host.getResources().getDisplayMetrics().density;
    HomeMenuLayout.Result layout = HomeMenuLayout.calculate(w, h, density, host.dp(64),
        host.getResources().getConfiguration().fontScale, host.state.optBoolean("activeBattle"));
    for (int i = 0; i < 13; i++) boxes[i] = rect(layout.controls[i]);
    boxes[13] = rect(layout.toggle);
    boxes[14] = rect(layout.controls[14]);
    menuPanel.set(rect(layout.panel));
    heroStage.set(rect(layout.heroStage));
    boxes[15]=new RectF(host.dp(16),host.dp(72),host.dp(64),host.dp(120));
    heroStage.top=Math.max(heroStage.top,boxes[15].bottom+host.dp(8));
    tipRegion.set(rect(layout.tipRegion));
  }

  static RectF rect(HomeMenuLayout.Box box) {
    return new RectF(box.left, box.top, box.right, box.bottom);
  }

  void updateMenuDescription() {
    boolean notice = false;
    for (int i : MENU_ITEMS) notice |= badges[i];
    buttons.get(13).setText(menuOpen ? "닫기" : "메뉴");
    buttons.get(13).setContentDescription((menuOpen ? "메뉴 닫기" : "메뉴 펼치기 · 8개 기능")
        + (notice ? " · 받을 보상이 있어요" : ""));
  }

  void setMenuOpen(boolean open) {
    if (menuOpen == open) return;
    menuOpen = open;
    parallax.close();
    for (int i : MENU_ITEMS) buttons.get(i).setVisibility(open ? View.VISIBLE : View.GONE);
    updateMenuDescription();
    requestLayout();
    invalidate();
    if (open) buttons.get(MENU_ITEMS[0]).requestFocus();
    else buttons.get(13).requestFocus();
    announceForAccessibility(open ? "메뉴 펼침, 모집부터 8개 기능" : "메뉴 닫힘");
  }

  View tutorialMenuTarget(String key) {
    int target = key.equals("daily") ? 6 : key.equals("draw") ? 1
        : key.equals("defenders") || key.equals("team") ? 12 : -1;
    if (target < 0) return null;
    setMenuOpen(true);
    return buttons.get(target);
  }

  boolean closeMenuOnBack() {
    if (!menuOpen) return false;
    setMenuOpen(false);
    return true;
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
    menuPanel.offset(safe.left, safe.top);
    heroStage.offset(safe.left, safe.top);
    tipRegion.offset(safe.left, safe.top);
    RectF gullRect = gullBox();
    gullRect.intersect(safe);
    gull.measure(
        MeasureSpec.makeMeasureSpec(
            Math.max(host.dp(48), (int) gullRect.width()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(
            Math.max(host.dp(48), (int) gullRect.height()), MeasureSpec.EXACTLY));
    for (int i = 0; i < buttons.size(); i++) {
      RectF r = boxes[i];
      buttons
          .get(i)
          .measure(
              MeasureSpec.makeMeasureSpec((int) r.width(), MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(
                  Math.max(host.dp(48), (int) r.height()), MeasureSpec.EXACTLY));
    }
    View resume = findViewWithTag("lobby-resume");
    if (resume != null) {
      RectF r = resumeBox(safe);
      resume.measure(MeasureSpec.makeMeasureSpec(Math.round(r.width()), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(Math.round(r.height()), MeasureSpec.EXACTLY));
    }
    if (tip.getVisibility() == View.VISIBLE) measureGullTip(safe);
  }

  /** The complete planted animation envelope is protected, not just the tap target. */
  RectF tipHeroExclusion() {
    RectF hero = heroBox();
    float side = hero.width() * .002f + host.dp(4);
    RectF occupied = new RectF(hero.left - side,
        hero.top - hero.height() * .004f - host.dp(1.2f), hero.right + side, hero.bottom);
    occupied.union(heroShadowBox(hero));
    return occupied;
  }

  RectF resumeBox(RectF safe) {
    int x = Math.round(safe.left + host.dp(72));
    int y = Math.round(boxes[7].bottom + host.dp(8));
    return new RectF(x, y, Math.min(heroStage.right, x + host.dp(300)), y + host.dp(48));
  }

  ArrayList<RectF> tipExclusions(RectF safe) {
    ArrayList<RectF> occupied = new ArrayList<>();
    occupied.add(tipHeroExclusion());
    for (int i = 0; i < buttons.size(); i++) {
      if (buttons.get(i).getVisibility() != View.VISIBLE) continue;
      RectF box = boxes[i];
      occupied.add(new RectF((int) box.left, (int) box.top, (int) box.right,
          (int) box.top + buttons.get(i).getMeasuredHeight()));
    }
    if (menuOpen) occupied.add(new RectF(menuPanel));
    if (findViewWithTag("lobby-resume") != null) occupied.add(resumeBox(safe));
    return occupied;
  }

  void measureGullTip(RectF safe) {
    tip.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8));
    tip.setText(tipCopy.subSequence(tipPageStart, tipCopy.length()));
    HomeTipLayout.Result placement = HomeTipLayout.measure(tip, tipRegion, tipHeroExclusion(),
        tipExclusions(safe), host.dp(8), host.dp(300), host.dp(420), host.dp(120));
    tipFrame.set(placement.bounds);
    tipPaged = placement.paged;
    tipNextPageStart = tipPageStart + placement.consumed;
    if (tipFrame.isEmpty()) tip.setVisibility(View.GONE);
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
      RectF resumeArea = resumeBox(safe);
      resume.layout((int) resumeArea.left, (int) resumeArea.top,
          (int) resumeArea.right, (int) resumeArea.bottom);
    }
    if (tip.getVisibility() == View.VISIBLE)
      tip.layout(tipFrame.left, tipFrame.top, tipFrame.right, tipFrame.bottom);
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
    p.setTypeface(heading ? homeHeading : NavyWorkbenchTheme.font(host));
    p.setTextSize(size);
    p.setTextAlign(Paint.Align.CENTER);
    p.setColor(color);
    if (heading && text.indexOf('·') >= 0) {
      // Jua does not ship U+00B7. Draw the separator as ink rather than relying
      // on a device-dependent fallback face (or a missing-glyph square).
      String[] pieces = text.split("·", -1);
      float dotWidth = size * .32f, width = dotWidth * (pieces.length - 1);
      for (String piece : pieces) width += p.measureText(piece);
      float at = x - width / 2;
      p.setTextAlign(Paint.Align.LEFT);
      for (int i = 0; i < pieces.length; i++) {
        c.drawText(pieces[i], at, y, p);
        at += p.measureText(pieces[i]);
        if (i + 1 < pieces.length) {
          c.drawCircle(at + dotWidth/2, y - size * .35f, size * .065f, p);
          at += dotWidth;
        }
      }
      p.setTextAlign(Paint.Align.CENTER);
    } else c.drawText(text, x, y, p);
  }

  String fitted(String text, float width, float size, boolean heading) {
    p.setTypeface(heading ? homeHeading : NavyWorkbenchTheme.font(host));
    p.setTextSize(size);
    if (p.measureText(text) <= width) return text;
    String value = text;
    while (value.length() > 1 && p.measureText(value + "…") > width)
      value = value.substring(0, value.length() - 1);
    return value + "…";
  }

  String currencyAmount(int balance, float available, float size) {
    String full = String.format(Locale.KOREA, "%,d", balance);
    p.setTypeface(homeHeading); p.setTextSize(size);
    if (p.measureText(full) <= available) return full;
    return balance >= 100000000 ? String.format(Locale.KOREA, "%.1f억", balance / 100000000d)
        : balance >= 10000 ? String.format(Locale.KOREA, "%.1f만", balance / 10000d)
        : String.valueOf(balance);
  }

  float currencyAmountSize(String shown, float available) {
    float size = currencyTextSize();
    p.setTypeface(homeHeading); p.setTextSize(size);
    return size * Math.min(1, available / Math.max(1, p.measureText(shown)));
  }

  float currencyTextSize() {
    return canvasTextSize(host.dp(14), host.dp(30), true);
  }

  float canvasTextSize(float basePixels, float maxHeight, boolean heading) {
    android.util.DisplayMetrics metrics = host.getResources().getDisplayMetrics();
    float size = basePixels * metrics.scaledDensity / metrics.density;
    p.setTypeface(heading ? homeHeading : NavyWorkbenchTheme.font(host));
    p.setTextSize(size);
    p.getFontMetrics(canvasMetrics);
    float height = canvasMetrics.descent - canvasMetrics.ascent;
    return height > maxHeight ? size * maxHeight / height : size;
  }

  float centeredBaseline(float centerY, float size, boolean heading) {
    p.setTypeface(heading ? homeHeading : NavyWorkbenchTheme.font(host));
    p.setTextSize(size);
    p.getFontMetrics(canvasMetrics);
    return centerY - (canvasMetrics.ascent + canvasMetrics.descent) / 2;
  }

  boolean controlPressed(int index) {
    Button button = buttons.get(index);
    return button.isEnabled() && (button.isPressed() || button.isHovered());
  }

  void controlSurface(Canvas canvas, int index, RectF bounds) {
    menuArt.tile(canvas, bounds, controlPressed(index), buttons.get(index).isEnabled(),
        buttons.get(index).isFocused(), index == 5);
  }

  int controlInk(int index) {
    return !buttons.get(index).isEnabled() ? JournalTheme.MUTED
        : JournalTheme.INK;
  }

  void command(Canvas c, int index, String name, String icon, boolean primary) {
    RectF r = new RectF(boxes[index]);
    boolean pressed = controlPressed(index);
    if (pressed) r.offset(0, host.dp(1));
    controlSurface(c, index, r);
    boolean small = index == 8;
    float size = Math.min(host.dp(small ? 25 : primary ? 44 : 36),
        r.height() * (primary ? .72f : .55f));
    float inset = host.dp(small ? 10 : 14);
    if (icon != null) menuArt.icon(c, icon,
        new RectF(r.left + inset, r.centerY() - size / 2,
            r.left + inset + size, r.centerY() + size / 2));
    if (small && icon != null) return;
    if (index == 13) {
      size = Math.min(host.dp(24), r.height() * .42f);
      float x = r.left + host.dp(12), y = r.centerY() - size / 2;
      p.setShader(null); p.setColor(controlInk(index)); p.setStyle(Paint.Style.FILL);
      if (menuOpen) {
        p.setStrokeWidth(host.dp(3)); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x+host.dp(3), y+host.dp(3), x+size-host.dp(3), y+size-host.dp(3), p);
        c.drawLine(x+size-host.dp(3), y+host.dp(3), x+host.dp(3), y+size-host.dp(3), p);
        p.setStrokeCap(Paint.Cap.BUTT);
      } else {
        float cell = (size-host.dp(3))/2;
        for (int row=0;row<2;row++) for(int col=0;col<2;col++) {
          float a=x+col*(cell+host.dp(3)), b=y+row*(cell+host.dp(3));
          c.drawRoundRect(new RectF(a,b,a+cell,b+cell), host.dp(2),host.dp(2),p);
        }
      }
    }
    float left = index == 13 ? r.left + host.dp(12) + size + host.dp(6)
        : r.left + (icon == null ? host.dp(8) : inset + size + host.dp(5));
    float right = r.right - host.dp(12);
    float textSize = canvasTextSize(host.dp(index == 5 ? 28 : index == 12 ? 21 : 16), r.height() - host.dp(18), true);
    p.setTypeface(homeHeading); p.setTextSize(textSize);
    float measured = p.measureText(name);
    if (measured > right - left) textSize *= (right - left) / measured;
    label(c, name, (left + right) / 2, centeredBaseline(r.centerY(), textSize, true),
        textSize, true, controlInk(index));
  }

  void menuTile(Canvas c, int index, String name, String icon) {
    RectF r = boxes[index];
    controlSurface(c, index, r);
    float labelHeight = Math.min(host.dp(26), r.height() * .27f);
    // A wide envelope must not be fitted into a square slot: its visible height
    // would shrink again even after removing transparent padding.
    float iconHeight = Math.max(host.dp(18), Math.min(r.height() * .58f,
        r.height() - labelHeight - host.dp(14)));
    float iconWidth = r.width() * .86f;
    if (index == 14) {
      label(c, rebirthUnlocked?"↻":"🔒", r.centerX(), r.top+iconHeight, host.dp(22), true, controlInk(index));
    }
    else menuArt.navigationIcon(c, index, new RectF(r.centerX()-iconWidth/2,
        r.top+host.dp(7), r.centerX()+iconWidth/2, r.top+host.dp(7)+iconHeight));
    float font = canvasTextSize(host.dp(16), labelHeight-host.dp(3), true);
    p.setTypeface(homeHeading); p.setTextSize(font);
    font *= Math.min(1, (r.width()-host.dp(20))/Math.max(1,p.measureText(name)));
    label(c, name, r.centerX(), centeredBaseline(r.bottom-labelHeight/2-host.dp(8), font, true),
        font, true, controlInk(index));
  }

  /** A distinct brass bell, not the recruitment envelope asset. */
  void newsBell(Canvas c, float cx, float top, float height) {
    float w = Math.min(height * .72f, host.dp(32));
    float y = top + height * .14f;
    p.setColor(JournalTheme.BRASS); p.setStyle(Paint.Style.FILL);
    c.drawCircle(cx, y, w * .12f, p);
    c.drawRoundRect(cx-w*.38f, y+w*.12f, cx+w*.38f, y+w*.86f, w*.35f, w*.35f, p);
    c.drawRoundRect(cx-w*.5f, y+w*.72f, cx+w*.5f, y+w*.87f, w*.08f, w*.08f, p);
    p.setColor(JournalTheme.INK);
    c.drawCircle(cx, y+w, w*.10f, p);
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
    // 아래 조언 영역은 항상 예약: 말풍선 표시나 메뉴 토글 때 캐릭터가 뛰지 않는다.
    float left = heroStage.left + host.dp(8), right = heroStage.right - host.dp(8);
    float bottom = heroStage.bottom;
    float maxHeight = Math.max(1, Math.min(bottom - heroStage.top - host.dp(4), safe.height() * .43f));
    float maxWidth = Math.max(1, right - left);
    Rect source = homeArt.heroBounds();
    float aspect = source == null ? 1 : source.width() / (float) source.height();
    // Leave room for the small breathing scale as well as the bounded drag.
    float height = Math.min(maxHeight, maxWidth / aspect) / 1.004f;
    float width = height * aspect;
    // Pin the guide to the left scene composition, while clamping its full ink to stage.
    float center = Math.max(left + width / 2,
        Math.min(getMeasuredWidth() * .23f, right - width / 2));
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
    // Upper-right daylight: project the actual alpha silhouette lower-left.
    // Use the planted frame, never the breathing/drag pose or padded sprite canvas.
    // This decorative cast shadow may sit behind the foreground advice paper; it
    // must not consume the paper's readable/tappable below-hero safe region.
    groundShadowMatrix.setValues(GROUND_PROJECTION);
    groundShadowSource.set(-heroFrame.width()/2, -heroFrame.height(), heroFrame.width()/2, 0);
    groundShadowPaint.setColorFilter(groundShadowColor);
    groundShadowPaint.setAlpha(65);
    int ground = c.save();
    c.translate(heroFrame.centerX(), heroFrame.bottom);
    c.concat(groundShadowMatrix);
    c.drawBitmap(bitmap, source, groundShadowSource, groundShadowPaint);
    c.restoreToCount(ground);
    // A separate, tighter contact patch anchors the feet even with motion disabled.
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0x40232d33);
    c.drawOval(shadow, p);
    shadow.inset(shadow.width() * .16f, shadow.height() * .22f);
    p.setColor(0x62232d33);
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

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    boolean moving = SceneMotion.enabled(host) && host.motionResumed && motion.clock != null
        && isShown() && getWindowVisibility() == View.VISIBLE;
    if (!moving) parallax.close();
    homeArt.background(c, getWidth(), getHeight(), motion.millis / 1000d, moving);
    character(c);
    if (menuOpen) {
      menuArt.panel(c, menuPanel);
      String[] icons = {"explore", "draw", "team", "combine", "shop"};
      for (int i = 0; i < 5; i++) menuTile(c, i, NAMES[i], icons[i]);
      menuTile(c, 12, "전투 편성", "team");
      menuTile(c, 6, "출석·미션", "daily");
      menuTile(c, 14, rebirthUnlocked?"환생":"환생 잠김", null);
    }
    command(c, 15, "우편", null, false);
    command(c, 5, "플레이", "explore", true);
    command(c, 13, menuOpen ? "닫기" : "메뉴", null, false);
    command(c, 8, "설정", "settings", false);
    RectF profile = boxes[7];
    controlSurface(c, 7, profile);
    fit(c, image("icons/u-profile.png"), new RectF(profile.left + host.dp(10),
        profile.top + host.dp(8), profile.left + host.dp(42), profile.bottom - host.dp(8)));
    String nick = host.me().optString("nickname", "수비조장"), summary = profileSummary();
    float cx = (profile.left + host.dp(47) + profile.right - host.dp(12)) / 2;
    float nickSize = canvasTextSize(host.dp(16), profile.height() - host.dp(16), true);
    label(c, fitted(nick, profile.width() - host.dp(65), nickSize, true), cx,
        centeredBaseline(profile.centerY(), nickSize, true), nickSize, true,
        controlInk(7));
    buttons.get(7).setContentDescription("내 정보 · " + nick + " · " + summary);
    String[] keys = {"snack", "peanut", "credits"};
    String[] currencyIcons = {"shrimp-snack", "peanut", null};
    String[] currencyNames = {"새우gang", "땅콩", "지원서"};
    for (int i = 0; i < 3; i++) {
      RectF r = boxes[9 + i];
      Button currency = buttons.get(9 + i);
      menuArt.currency(c, r, i, currency.isPressed(), currency.isEnabled(), currency.isFocused());
      int balance = host.me().optInt(keys[i]);
      String amount = String.format(Locale.KOREA, "%,d", balance);
      float room = Math.max(1, r.width() - host.dp(49));
      String shown = currencyAmount(balance, room, currencyTextSize());
      float amountSize = currencyAmountSize(shown, room);
      label(c, shown, (r.left + host.dp(42) + r.right - host.dp(7)) / 2,
          centeredBaseline(r.centerY(), amountSize, true), amountSize, true,
          currency.isEnabled() ? Color.WHITE : JournalTheme.MUTED);
      buttons.get(9 + i).setContentDescription(currencyNames[i] + " " + amount + " · 상점");
    }
    for (int i = 0; i < badges.length; i++)
      if (badges[i] && buttons.get(i).getVisibility() == View.VISIBLE) dot(c, boxes[i]);
    for (int i : MENU_ITEMS) if (badges[i]) { dot(c, boxes[13]); break; }
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
          updateMenuDescription();
          invalidate();
        });
  }

  void fetchRosterHome(){
    String owner=host.api.token();
    host.api.call("native/roster60/home",null,(r,e)->{
      if(!current()||!owner.equals(host.api.token())||e!=null||r==null)return;
      rebirthUnlocked=r.optBoolean("rebirthUnlocked");buttons.get(14).setEnabled(rebirthUnlocked);
      buttons.get(14).setContentDescription(rebirthUnlocked?"환생 · 조건과 안내 확인":"환생 잠김 · 제한 이상 개체 첫 획득 시 해제");
      badges[15]=r.optInt("mailUnread")>0||r.optBoolean("mailRewardPending");buttons.get(15).setContentDescription("우편함"+(badges[15]?" · 안 읽은 우편 또는 받을 보상 있음":""));invalidate();
      if(r.optBoolean("rebirthIntroPending")&&rebirthIntro==null){
        rebirthIntro=new GameDialog(host).navy().setTitle("환생 기능 해제")
          .setMessage("제한·초월·불멸·영원 개체를 획득해 환생 안내가 열렸습니다. 실제 환생은 60·70·80·90·99레벨 조건을 따릅니다. HP·MP·내구력 기본값이 회당 10% 증가하며 속도·저항력은 유지됩니다. 시험 기본안입니다.")
          .setNegativeButton("나중에",(d,w)->{rebirthIntro=null;})
          .setPositiveButton("안내 확인",null).show();
        final android.app.AlertDialog intro=rebirthIntro;
        intro.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
          if(!current()||!owner.equals(host.api.token())||!v.isEnabled())return;
          v.setEnabled(false);
          host.api.call("native/roster60/home/rebirth-intro/complete",new JSONObject(),(done,error)->{
            if(!current()||!owner.equals(host.api.token()))return;
            if(error==null){intro.dismiss();if(rebirthIntro==intro)rebirthIntro=null;}
            else {v.setEnabled(true);host.message("확인 저장에 실패했습니다. 다시 시도하거나 다음 접속에서 안내를 확인하세요.");}
          });
        });
        rebirthIntro.setOnCancelListener(d->rebirthIntro=null);
      }
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
    buttons.get(1).setContentDescription(badges[1] ? "모집 · 지원서를 받을 수 있어요" : "모집");
    buttons.get(5).setContentDescription(growth ? "플레이 · 배울 기술 또는 승진이 있어요" : "플레이");
    updateMenuDescription();
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
    float radius = Math.min(host.dp(5), Math.min(target.width(), target.height()) * .07f)
        * (1 + .07f * motion.pulse(1600, 0));
    float inset = radius + host.dp(3);
    float x = Math.min(safe.right - inset, target.right - inset),
        y = Math.max(safe.top + inset, target.top + inset);
    p.setStyle(Paint.Style.FILL);
    p.setShader(new LinearGradient(x, y-radius, x, y+radius,
        0xfffa665e, 0xffc42128, Shader.TileMode.CLAMP));
    c.drawCircle(x, y, radius, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(.8f));
    p.setColor(0xffe6bc70);
    c.drawCircle(x, y, radius, p);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0x90fff0d0);
    c.drawCircle(x-radius*.25f, y-radius*.32f, radius*.2f, p);
  }

  RectF gullBox() {
    RectF area = heroBox();
    float width = Math.max(host.dp(48), area.width());
    float height = Math.max(host.dp(48), area.height());
    float left = Math.max(heroStage.left, Math.min(area.centerX()-width/2, heroStage.right-width));
    float top = Math.max(heroStage.top, Math.min(area.centerY()-height/2, heroStage.bottom-height));
    return new RectF(left, top, left+width, top+height);
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
    tipCopy = android.text.Html.fromHtml(tips[tipAt], android.text.Html.FROM_HTML_MODE_LEGACY);
    tipPageStart = 0;
    tipNextPageStart = 0; // A new tip cannot inherit a page offset before the next traversal.
    tipPaged = false;
    tip.setText(tipCopy);
    tip.setVisibility(View.VISIBLE);
    tip.scrollTo(0, 0);
    tip.setContentDescription("갈매미 조언 " + tipCopy + " · 눌러서 다음 조언");
    removeCallbacks(hideTip);
    postDelayed(hideTip, 6000);
    requestLayout();
    invalidate();
  }

  void advanceGullTip() {
    if (tipNextPageStart < 0) return; // The requested page has not been measured yet.
    if (tipNextPageStart > tipPageStart && tipNextPageStart < tipCopy.length()) {
      tipPageStart = tipNextPageStart;
      tipNextPageStart = -1; // Repeated taps before layout cannot skip unread pages.
      removeCallbacks(hideTip);
      postDelayed(hideTip, 6000);
      requestLayout();
      invalidate();
    } else showGullTip();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      dismissGesture = menuOpen && !menuPanel.contains(event.getX(), event.getY())
          && !boxes[13].contains(event.getX(), event.getY())
          && !boxes[5].contains(event.getX(), event.getY());
      if (dismissGesture) setMenuOpen(false);
    }
    if (dismissGesture) {
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) dismissGesture = false;
      return true; // 닫기 탭이 아래 캐릭터·재화·설정으로 관통하지 않는다.
    }
    boolean readingTip = tipFrame.contains((int) event.getX(), (int) event.getY());
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
        && tip.getVisibility() == View.VISIBLE && !readingTip) {
      tip.setVisibility(View.GONE);
      removeCallbacks(hideTip);
    }
    return super.dispatchTouchEvent(event);
  }
}



