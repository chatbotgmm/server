package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Android Canvas 장면과 네이티브 조작 버튼. 메뉴 뒤 큰 패널은 사용하지 않는다. */
final class BattleView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final DragParallax parallax;
  final JSONObject battle;
  final LinearLayout controls;
  final ScrollView menu, logBox;
  final LinearLayout tools;
  final Button foeInfo, history, augmentInfo;
  final Button[] effectInfo = new Button[2];
  final ImageView[] typeInfo = new ImageView[2];
  String pane = "menu";
  boolean tablet;
  final Scene scene;
  final TextView inputTimer;
  final BattleSkin panelSkin, hpSkin;
  final TextView log;
  final BattlePlayback playback;
  NativeAugmentView augmentView;
  int arenaWidth, arenaHeight, logHeight, panelLeft, panelWidth;
  int safeLeft, safeTop, uiWidth, uiHeight;
  boolean skillSheetNeeded, skillSheetRequested;
  int selectedSwitch = -1, itemPage;
  JSONObject selectedItem;
  boolean expiredInput;

  BattleView(MainActivity a, JSONObject b) {
    super(a);
    host = a;
    parallax = new DragParallax(this, a.dp(12));
    try {
      battle = new JSONObject(b.toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    panelSkin = new BattleSkin(a, "panel-command");
    hpSkin = new BattleSkin(a, "panel-hp");
    for(String asset:new String[]{"bg-harbor","decor-shell","decor-wheel"}) BattleSkin.bitmap(a,asset);
    scene = new Scene();
    addView(scene, new LayoutParams(-1, -1));
    logBox = new FixedViewport(a);
    logBox.setBackground(new FieldLogPaper());
    logBox.setPadding(a.dp(10), a.dp(34), a.dp(10), a.dp(8));
    logBox.setClipToPadding(true);
    log = new RecentLogView(a);
    log.setTextSize(17);
    log.setTextColor(JournalTheme.INK);
    log.setTypeface(a.prose);
    log.setTextLocale(Locale.KOREAN);
    log.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    log.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      log.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    log.setPadding(a.dp(10), 0, a.dp(10), 0);
    log.setLineSpacing(a.dp(3), 1.08f);
    ArrayList<String> lines = new ArrayList<>();
    JSONArray ls = a.list(b, "log");
    for (int i = Math.max(0, ls.length() - 8); i < ls.length(); i++) lines.add(ls.optString(i));
    log.setText(
        NativeBattleLog.format(
            a, lines, a.list(b, "displayEvents"), b.optInt("displayLogStart") + ls.length() - 1));
    logBox.addView(log);
    logBox.setContentDescription("최근 전투 기록 · 터치하여 전체 기록 보기");
    logBox.setOnClickListener(v -> NativeBattleSheets.history(this));
    addView(logBox);
    controls = new LinearLayout(a);
    controls.setOrientation(LinearLayout.VERTICAL);
    menu = new FixedViewport(a);
    menu.addView(controls);
    addView(menu);
    tools = new LinearLayout(a);
    tools.setGravity(Gravity.CENTER_VERTICAL);
    tools.setBaselineAligned(false);
    tools.setBackground(utilityBackground());
    foeInfo = a.button("1턴 뒤", () -> NativeBattleSheets.fighter(this, 1));
    history = a.button("기록", () -> NativeBattleSheets.history(this));
    augmentInfo = a.button("증강체", () -> NativeBattleSheets.augments(this));
    for (Button tool : new Button[] {foeInfo, history, augmentInfo}) {
      tool.setTextSize(12);
      tool.setSingleLine(true);
      tool.setHorizontallyScrolling(false);
      tool.setAllCaps(false);
      tool.setTextColor(0xfffff8e8);
      tool.setPadding(0, 0, 0, 0);
      tool.setMinHeight(a.dp(48));
      tool.setMinimumHeight(a.dp(48));
      tool.setBackgroundColor(Color.TRANSPARENT);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
      tp.setMargins(0, 0, 0, 0);
      tools.addView(tool, tp);
    }
    addView(tools);
    for (int side = 0; side < 2; side++) {
      final int target = side;
      Button effects = a.button("", () -> showEffects(target));
      effects.setBackgroundColor(Color.TRANSPARENT);
      effects.setPadding(0, 0, 0, 0);
      effectInfo[side] = effects;
      addView(effects);
      String attr=active(side).optString("attrCode");
      ImageView type=BattleTypeInfo.icon(a,attr);
      type.setFocusable(true);
      type.setContentDescription((side==0?"내 ":"상대 ")+BattleTypeInfo.name(a,attr)+" 속성 상성 보기");
      type.setOnClickListener(v->BattleTypeInfo.showFighter(this,target));
      typeInfo[side]=type;addView(type);
    }
    inputTimer = a.text("", 18);
    inputTimer.setTextColor(0xfffff8e8);
    inputTimer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    // 28dp 칸 안에서 18sp 숫자가 위아래로 잘리지 않도록 기본 안쪽 여백을 뺀다.
    inputTimer.setPadding(0, 0, 0, 0);
    inputTimer.setIncludeFontPadding(false);
    inputTimer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(inputTimer);
    playback = new BattlePlayback(this);
    render();
  }

  private android.graphics.drawable.Drawable utilityBackground() {
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xdd173e4b); bg.setCornerRadius(host.dp(14));
    bg.setStroke(host.dp(1),0xffc1a473); return bg;
  }

  void playTo(JSONObject next, Runnable complete) {
    pane = "menu";
    render();
    if (augmentView != null && !next.optString("phase").equals("choose")) {
      augmentView.setVisibility(GONE);
      logBox.setVisibility(VISIBLE);
      menu.setVisibility(VISIBLE);
    }
    playback.start(next, complete);
  }

  @Override
  protected void onDetachedFromWindow() {
    parallax.close();
    playback.cancel();
    super.onDetachedFromWindow();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    return parallax.intercept(
            e,
            new RectF(getWidth() * .1f, getHeight() * .30f, getWidth() * .60f, getHeight() * .65f),
            battle.optString("phase").equals("action")
                && !host.busy
                && (augmentView == null || augmentView.getVisibility() != VISIBLE))
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

  void blockExpiredInput() {
    if (expiredInput) return;
    expiredInput = true;
    disableInputs(controls);
  }

  void disableInputs(View view) {
    view.setEnabled(false);
    if(view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group=(android.view.ViewGroup)view;
      for(int i=0;i<group.getChildCount();i++) disableInputs(group.getChildAt(i));
    }
  }

  boolean backInPane() {
    if (!pane.equals("menu")) { pane="menu"; render(); return true; }
    return false;
  }

  static String statusNames(JSONObject fighter) {
    JSONArray effects=BattleEffects.current(fighter);
    ArrayList<String> names=new ArrayList<>();
    for(int i=0;i<effects.length();i++) {
      JSONObject effect=effects.optJSONObject(i);
      if(effect!=null && !effect.optString("label").isEmpty())names.add(effect.optString("label"));
    }
    return android.text.TextUtils.join(" · ",names);
  }

  void showEffects(int side) {
    if (!playback.playing) NativeBattleSheets.effects(this, side);
  }

  RectF statusArea(int side) {
    float world = uiWidth * .655f, height = Math.min(host.dp(92), uiHeight * .24f);
    return side == 0
        ? new RectF(world * .53f, uiHeight * .91f - height, world * .96f, uiHeight * .91f)
        : new RectF(world * .04f, uiHeight * .055f, world * .47f, uiHeight * .055f + height);
  }

  JSONObject side(int i) {
    return host.list(battle, "teams").optJSONObject(i);
  }

  JSONObject active(int i) {
    JSONObject s = side(i);
    return host.list(s, "fighters").optJSONObject(s.optInt("active"));
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec), m = host.dp(8);
    RectF safe = host.safeBounds(w, h);
    boolean resized =
        arenaWidth != w
            || arenaHeight != h
            || uiWidth != Math.round(safe.width())
            || uiHeight != Math.round(safe.height());
    safeLeft = Math.round(safe.left);
    safeTop = Math.round(safe.top);
    uiWidth = Math.round(safe.width());
    uiHeight = Math.round(safe.height());
    arenaWidth = w;
    arenaHeight = h;
    tablet = (float) uiWidth / uiHeight < 1.85f;
    panelLeft = Math.round(uiWidth * .655f);
    panelWidth = uiWidth - panelLeft - host.dp(tablet ? 48 : 32);
    logHeight = 0;
    logBox.setVisibility(GONE);
    if (resized) render();
    getChildAt(0)
        .measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    logBox.measure(
        MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(logHeight, MeasureSpec.EXACTLY));
    menu.measure(
        MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(
            Math.max(1, uiHeight - host.dp(80)), MeasureSpec.EXACTLY));
    inputTimer.measure(MeasureSpec.makeMeasureSpec(host.dp(30), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(28), MeasureSpec.EXACTLY));
    tools.measure(
        MeasureSpec.makeMeasureSpec(Math.max(host.dp(144),panelWidth-host.dp(34)), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
    for (int side = 0; side < 2; side++) {
      RectF area = statusArea(side);
      typeInfo[side].measure(MeasureSpec.makeMeasureSpec(host.dp(48),MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(host.dp(48),MeasureSpec.EXACTLY));
      effectInfo[side].measure(
          MeasureSpec.makeMeasureSpec(Math.round(area.width()), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(
              Math.max(host.dp(48), Math.round(area.height())), MeasureSpec.EXACTLY));
    }
    if (augmentView != null)
      augmentView.measure(
          MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    setMeasuredDimension(w, h);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int m = host.dp(8);
    getChildAt(0).layout(0, 0, arenaWidth, arenaHeight);
    logBox.layout(
        safeLeft + panelLeft, safeTop + m, safeLeft + uiWidth - m, safeTop + m + logHeight);
    int inset = host.dp(tablet ? 24 : 16);
    menu.layout(safeLeft + panelLeft + inset, safeTop + host.dp(64),
        safeLeft + uiWidth - inset, safeTop + uiHeight - host.dp(16));
    inputTimer.layout(safeLeft + uiWidth - host.dp(34), safeTop + host.dp(18),
        safeLeft + uiWidth - host.dp(4), safeTop + host.dp(46));
    tools.layout(safeLeft + panelLeft + inset, safeTop + host.dp(8),
        safeLeft + panelLeft + inset + tools.getMeasuredWidth(), safeTop + host.dp(56));
    for (int side = 0; side < 2; side++) {
      RectF area = statusArea(side);
      area.offset(safeLeft, safeTop);
      typeInfo[side].layout(Math.round(area.right)-host.dp(54),Math.round(area.bottom)-host.dp(50),
          Math.round(area.right)-host.dp(6),Math.round(area.bottom)-host.dp(2));
      effectInfo[side].layout(
          Math.round(area.left),
          Math.round(area.top),
          Math.round(area.right),
          Math.round(area.bottom));
    }
    if (augmentView != null) augmentView.layout(0, 0, arenaWidth, arenaHeight);
  }

  /** 기본 명령판과 최근 기록은 고정한다. 긴 목록의 스크롤은 선택 모달 안에만 둔다. */
  static final class FixedViewport extends ScrollView {
    float downX, downY;
    boolean moved;

    FixedViewport(MainActivity h) {
      super(h);
      setFillViewport(true);
      setVerticalScrollBarEnabled(false);
      setHorizontalScrollBarEnabled(false);
      setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    protected void measureChild(View child, int parentWidthSpec, int parentHeightSpec) {
      measureChildWithMargins(child, parentWidthSpec, 0, parentHeightSpec, 0);
    }

    @Override
    protected void measureChildWithMargins(
        View child, int parentWidthSpec, int widthUsed, int parentHeightSpec, int heightUsed) {
      MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
      int width = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed;
      int height =
          getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed;
      child.measure(
          getChildMeasureSpec(parentWidthSpec, width, lp.width),
          MeasureSpec.makeMeasureSpec(
              Math.max(0, MeasureSpec.getSize(parentHeightSpec) - height), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
      return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (!isClickable()) return false;
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        downX = event.getX();
        downY = event.getY();
        moved = false;
      } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        moved |= Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop;
      } else if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved) performClick();
      return true;
    }

    @Override
    public boolean performClick() {
      return super.performClick();
    }

    @Override
    public void scrollTo(int x, int y) {
      super.scrollTo(0, 0);
    }

    @Override
    public boolean fullScroll(int direction) {
      return false;
    }

    @Override
    public boolean canScrollVertically(int direction) {
      return false;
    }
  }

  /** 공간이 모자라면 이전 줄보다 최신 확정 기록을 우선하고 전체 기록은 별도로 연다. */
  final class RecentLogView extends androidx.appcompat.widget.AppCompatTextView {
    CharSequence full = "";
    boolean rendering;

    RecentLogView(MainActivity h) {
      super(h);
      setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
      if (!rendering) full = text == null ? "" : text;
      super.setText(text, type);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      CharSequence source = full == null ? "" : full;
      int width =
          Math.max(1, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
      int height =
          Math.max(1, MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom());
      android.text.StaticLayout layout =
          android.text.StaticLayout.Builder.obtain(source, 0, source.length(), getPaint(), width)
              .setIncludePad(getIncludeFontPadding())
              .setLineSpacing(getLineSpacingExtra(), getLineSpacingMultiplier())
              .build();
      int previous = source.toString().lastIndexOf('\n');
      CharSequence visible =
          layout.getHeight() > height && previous >= 0
              ? source.subSequence(previous + 1, source.length())
              : source;
      rendering = true;
      if (!android.text.TextUtils.equals(getText(), visible))
        super.setText(visible, BufferType.SPANNABLE);
      int lines = Math.max(1, height / Math.max(1, getLineHeight()));
      if (getMaxLines() != lines) setMaxLines(lines);
      CharSequence description = source + " · 전체 기록은 현장 기록을 눌러 확인";
      if (!android.text.TextUtils.equals(getContentDescription(), description))
        setContentDescription(description);
      rendering = false;
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  void add(Button b) {
    b.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
    b.setTextColor(JournalTheme.controlText(GameButtonDrawable.PAPER));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
    controls.addView(b, p);
  }

  void heading(String s) {
    LinearLayout row = new LinearLayout(host);
    row.setBackground(new GamePaperDrawable(host));
    Button back =
        host.button(
            "‹",
            () -> {
              pane = "menu";
              render();
            });
    back.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
    back.setTextColor(JournalTheme.controlText(GameButtonDrawable.PAPER));
    row.addView(back, new LinearLayout.LayoutParams(host.dp(52), host.dp(48)));
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = host.text(s, 20);
    title.setTypeface(host.headline);
    row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(row, new LinearLayout.LayoutParams(-1, host.dp(48)));
  }

  void render() {
    controls.removeAllViews();
    // 설계(LAYOUT-AND-STATES 2절): 태블릿은 명령·기술 묶음을 패널 세로 가운데에 두어 아래 절반이 비지 않게 한다.
    controls.setGravity(tablet ? Gravity.CENTER_VERTICAL : Gravity.TOP);
    controls.setMinimumHeight(tablet ? Math.max(0, uiHeight - host.dp(80)) : 0);
    controls.setEnabled(!expiredInput || pane.equals("leave"));
    controls.setPadding(0, 0, 0, 0);
    if (!pane.equals("skills")) {
      skillSheetNeeded = false;
      skillSheetRequested = false;
    }
    requestLayout();
    String phase = battle.optString("phase");
    tools.setVisibility(phase.equals("choose") || phase.equals("over") ? GONE : VISIBLE);
    for (int side = 0; side < 2; side++) {
      typeInfo[side].setVisibility(phase.equals("choose") || phase.equals("over") ? GONE : VISIBLE);
      effectInfo[side].setVisibility(
          phase.equals("choose") || phase.equals("over") ? GONE : VISIBLE);
      JSONArray current = BattleEffects.current(active(side));
      ArrayList<String> labels = new ArrayList<>();
      for (int i = 0; i < current.length(); i++)
        labels.add(BattleEffects.label(current.optJSONObject(i)));
      effectInfo[side].setContentDescription(
          (side == 0 ? "내 " : "상대 ")
              + "현재 효과 · "
              + (labels.isEmpty() ? "없음" : android.text.TextUtils.join(" · ", labels))
              + " · 상세 보기");
    }
    foeInfo.setText(battle.optInt("turn") > 1 ? "정보" : "1턴 뒤");
    foeInfo.setEnabled(battle.optInt("turn") > 1 && phase.equals("action"));
    foeInfo.setContentDescription(
        battle.optInt("turn") > 1 ? "상대 능력치와 기술 보기" : "한 턴이 지나면 상대 정보를 볼 수 있어요");
    history.setContentDescription("전투 기록 · " + battle.optInt("turn") + "턴");
    if (NativeRealtime.isBattle(battle) && !battle.optBoolean("canSubmit")
        && !phase.equals("over") && !pane.equals("leave")) {
      TextView waiting=host.text("상대 선택 중",18); waiting.setTextColor(0xfffff8e8); controls.addView(waiting);
      controls.addView(host.button("나가기", this::confirmLeave));
      return;
    }
    if (phase.equals("choose")) {
      choose();
      return;
    }
    if (phase.equals("over")) {
      controls.addView(host.text("전투 종료", 24));
      return;
    }
    if (expiredInput && !pane.equals("leave")) {
      TextView pending=host.text("결과 확인 중",18);pending.setTextColor(0xfffff8e8);controls.addView(pending);return;
    }
    switch (pane) {
      case "skills":
        skills();
        return;
      case "combo":
        combos();
        return;
      case "leave":
        surrenderPane();
        return;
      case "itemTarget":
        itemTargets();
        return;
      case "switch":
        switchPane();
        return;
      case "items":
        itemPane();
        return;
    }
    int height = commandHeight();
    for (int row = 0; row < 2; row++) {
      LinearLayout line = new LinearLayout(host);
      // Different icon aspect ratios produce different Button baselines. Do not shift a
      // command below its allocated row in an attempt to align those text baselines.
      line.setBaselineAligned(false);
      controls.addView(line,new LinearLayout.LayoutParams(-1,height+2*host.dp(3)));
      for (int col = 0; col < 2; col++) {
        final int ix = row * 2 + col;
        String[] names = {
          "공격",
          "아이템",
          "교체",
          battle.optString("mode").equals("normal") || battle.optString("mode").equals("rank")
              ? "항복"
              : "도망가기"
        };
        Button b =
            host.button(
                names[ix],
                () -> {
                  if (ix == 3) confirmLeave();
                  else if (ix == 1) { pane="items"; itemPage=0; render(); }
                  else if (ix == 2) { pane="switch"; selectedSwitch=-1; render(); }
                  else {
                    pane = "skills";
                    render();
                  }
                });
        b.setTypeface(host.headline);
        // 기존 판·07 확정 시안처럼 이름을 크게 쓰고, 아이콘과 이름을 한 묶음으로 버튼 가운데에 둔다.
        b.setTextSize(tablet ? (ix == 3 ? 30 : 33) : (ix == 3 ? 23 : 27));
        // Two weighted cells share panelWidth after four 3dp margins. Respect the
        // real inner width, including 8dp padding per side and rounding slack;
        // a 60dp floor can exceed the whole text area on small inset screens.
        float labelWidth = Math.max(1, (panelWidth - host.dp(12)) / 2f - host.dp(18));
        float measuredLabel = b.getPaint().measureText(names[ix]);
        if (measuredLabel > labelWidth)
          b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, b.getTextSize() * labelWidth / measuredLabel);
        b.setSingleLine(true);
        b.setHorizontallyScrolling(false);
        b.setIncludeFontPadding(false);
        String[] surfaces = {"attack", "item", "switch", "surrender"};
        String name = surfaces[ix];
        b.setBackground(new BattleSkin(host, "button-" + name));
        b.setTextColor(ix == 3 ? JournalTheme.INK : 0xfffff8e8);
        Paint.FontMetrics metrics = b.getPaint().getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        int iconHeight = 0, gap = host.dp(4);
        Bitmap image = BattleSkin.bitmap(host, "icon-" + name);
        if (image != null && uiHeight >= host.dp(320) && host.getResources().getConfiguration().fontScale < 1.5f) {
          android.graphics.drawable.BitmapDrawable icon = new android.graphics.drawable.BitmapDrawable(getResources(),image);
          int bound=Math.min(host.dp(tablet ? 64 : 56), Math.round(height - textHeight - gap - host.dp(16)));
          if (bound >= host.dp(28)) {
            float scale=Math.min((float)bound/image.getWidth(),(float)bound/image.getHeight());
            icon.setBounds(0,0,Math.round(image.getWidth()*scale),Math.round(image.getHeight()*scale));
            iconHeight = icon.getBounds().height();
            b.setCompoundDrawables(null,icon,null,null);
            b.setCompoundDrawablePadding(gap);
          }
        }
        // drawableTop은 위 안쪽 여백에 붙으므로, 아이콘+간격+이름 묶음 높이로 위 여백을 계산해 세로 가운데에 맞춘다.
        int block = Math.round(iconHeight + (iconHeight > 0 ? gap : 0) + textHeight);
        int top = Math.max(host.dp(6), (height - block) / 2);
        b.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        b.setPadding(host.dp(8), top, host.dp(8), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1);
        p.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
        line.addView(b, p);
      }
    }
  }

  int commandHeight() {
    return Math.max(host.dp(48), Math.min(host.dp(tablet ? 160 : 122), (uiHeight-host.dp(92))/2));
  }

  int minimumLogHeight() {
    return host.dp(host.getResources().getConfiguration().fontScale >= 1.15f ? 132 : 104);
  }

  /** 확정된 전투 기록을 먼저 읽도록 제목과 턴을 스크롤 바깥에 고정한다. */
  final class FieldLogPaper extends Drawable {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int alpha = 255;

    @Override
    public void draw(Canvas canvas) {
      RectF box = new RectF(getBounds());
      int save = canvas.saveLayerAlpha(box, alpha);
      JournalTheme.drawPanel(
          canvas, box, host.getResources().getDisplayMetrics().density, JournalTheme.PAPER, false);
      paint.setTypeface(host.headline);
      paint.setTextSize(host.dp(tablet ? 18 : 16));
      paint.setColor(JournalTheme.INK);
      canvas.drawText(
          "현장 기록 · " + battle.optInt("turn") + "턴  ›",
          box.left + host.dp(16),
          box.top + host.dp(23),
          paint);
      paint.setColor(0xffd8c8ab);
      paint.setStrokeWidth(host.dp(1));
      canvas.drawLine(
          box.left + host.dp(15),
          box.top + host.dp(29),
          box.right - host.dp(15),
          box.top + host.dp(29),
          paint);
      canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int value) {
      alpha = value;
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter value) {
      paint.setColorFilter(value);
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  void confirmLeave() {
    if (battle.optString("phase").equals("choose")) return;
    pane="leave"; render();
  }

  void surrenderPane() {
    heading("나가기 확인");
    String mode = battle.optString("mode");
    String message =
        mode.equals("rank")
            ? "랭크게임에서 나갈까요? 패배로 기록되며 랭크 결과에 반영돼요."
            : mode.equals("normal")
                ? "대전에서 나갈까요? 패배로 처리돼요."
                : mode.equals("story")
                    ? "전투에서 도망칠까요? 경험치를 받지 못하고 남은 체력은 다음 전투로 이어져요."
                    : "연습 전투에서 나갈까요? 진행 상황은 저장되지 않아요.";
    TextView explanation=host.text(message,16); explanation.setTextColor(0xfffff8e8);
    controls.addView(explanation);
    add(host.button("확인 · 나가기", () -> host.battleTurnFrom(battle, MainActivity.obj("leave", true))));
  }

  int accessibleCardHeight(int baseDp) {
    return host.dp(baseDp * Math.max(1, host.getResources().getConfiguration().fontScale));
  }

  void skills() {
    JSONArray skills = host.list(active(0), "skills");
    ArrayList<JSONObject> normals = new ArrayList<>();
    for (int i=0;i<skills.length();i++) {
      JSONObject sk=skills.optJSONObject(i);
      if(sk!=null && !sk.optString("id").startsWith("combo-")) normals.add(sk);
    }
    int cardHeight=Math.max(host.dp(48), Math.min(host.dp(tablet?160:124),(uiHeight-host.dp(152))/2));
    for(int row=0;row<2;row++) {
      LinearLayout line=new LinearLayout(host);
      for(int col=0;col<2;col++) {
        int index=row*2+col;
        View card=index<normals.size()?new SkillCardView(this,normals.get(index),false,true,null):new View(host);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,cardHeight,1);
        lp.setMargins(host.dp(3),host.dp(3),host.dp(3),host.dp(3)); line.addView(card,lp);
      }
      controls.addView(line);
    }
    LinearLayout footer=new LinearLayout(host);
    Button combo=host.button("합동기",()->{pane="combo";render();});
    Button back=host.button("뒤로가기",()->{pane="menu";render();});
    for(Button button:new Button[]{combo,back}) {
      button.setTextSize(16); button.setBackground(new BattleSkin(host,button==combo?"button-item":"button-surrender"));
      button.setTextColor(button==combo?0xfffff8e8:JournalTheme.INK);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,host.dp(52),1);
      lp.setMargins(host.dp(3),host.dp(6),host.dp(3),0);footer.addView(button,lp);
    }
    controls.addView(footer);
    JSONArray legal=host.list(side(0),"legal");
    for(int i=0;i<legal.length();i++) {
      JSONObject action=legal.optJSONObject(i);
      if(action!=null && action.optString("skillId").equals("struggle")) {
        combo.setText("몸통 박치기");
        combo.setOnClickListener(v->host.battleTurnFrom(battle,MainActivity.obj("action",action)));
      }
    }
  }

  void combos() {
    heading("합동기");
    JSONArray skills=host.list(active(0),"skills");
    int count=0;
    for(int i=0;i<skills.length();i++) {
      JSONObject sk=skills.optJSONObject(i);
      if(sk!=null && sk.optString("id").startsWith("combo-")) {
        SkillCardView card=new SkillCardView(this,sk,false,true,null);
        card.showCompleteEffect(); controls.addView(card,new LinearLayout.LayoutParams(-1,-2)); count++;
      }
    }
    if(count==0) controls.addView(host.text("사용할 합동기가 없어요.",16));
  }

  void switchPane() {
    heading("교체");
    JSONArray fighters=host.list(side(0),"fighters"), legal=host.list(side(0),"legal");
    for(int i=0;i<fighters.length();i++) {
      final int index=i; JSONObject f=fighters.optJSONObject(i); boolean allowed=false;
      for(int j=0;j<legal.length();j++) {
        JSONObject a=legal.optJSONObject(j);
        if(a!=null && "switch".equals(a.optString("kind")) && a.optInt("to")==i) allowed=true;
      }
      String reason=i==side(0).optInt("active")?"출전 중":f.optInt("hp")<=0?"전투불가":!allowed?"지금 교체 불가":"";
      Button option=host.button((selectedSwitch==i?"✓ ":"")+f.optString("name")+" · "+f.optInt("hp")+"/"+f.optInt("maxHp")
          +(reason.isEmpty()?"":" · "+reason),()->{selectedSwitch=index;render();});
      option.setTextSize(14); option.setEnabled(reason.isEmpty()); add(option);
    }
    Button confirm=host.button("교체 · 이번 턴 행동 사용",()->host.battleTurnFrom(battle,
        MainActivity.obj("action",MainActivity.obj("kind","switch","to",selectedSwitch))));
    confirm.setEnabled(selectedSwitch>=0); confirm.setTextSize(14);add(confirm);
  }

  void itemPane() {
    heading("아이템");
    if(!battle.optString("mode").equals("story") && !NativeRealtime.isBattle(battle)) {
      TextView hint=host.text("스토리에서 사용할 수 있어요.",16);hint.setTextColor(0xfffff8e8);controls.addView(hint);return;
    }
    JSONArray items=host.list(battle,"items"), inventory=host.list(battle,"inventory");
    if(inventory.length()==0 && !NativeRealtime.isBattle(battle)) inventory=host.list(host.state,"items");
    for(int row=0;row<2;row++) {
      ItemRow line=new ItemRow();
      // 설명 줄 수가 다른 버튼을 글자 기준선으로 맞추면 아래로 밀려 잘린다(기본 메뉴와 같은 처리).
      line.setBaselineAligned(false);
      for(int col=0;col<2;col++) {
        int index=itemPage*4+row*2+col;
        if(index>=items.length()) break;
        JSONObject item=items.optJSONObject(index); int count=0;
        for(int j=0;j<inventory.length();j++) if(inventory.optJSONObject(j).optString("id").equals(item.optString("id"))) count=inventory.optJSONObject(j).optInt("count");
        String why=item.isNull("reason")?"":item.optString("reason");
        Button button=host.button(item.optString("name")+"\n×"+count,()->{selectedItem=item;pane="itemTarget";render();});
        button.setContentDescription(item.optString("name")+" "+count+"개 · "+NativeBattleSheets.itemEffect(item)
            +(count<=0?" · 없음":why.isEmpty()?"":" · "+why));
        button.setMaxLines(3); button.setGravity(Gravity.CENTER);
        // Explicit bounded wrapping also on the API24-26 AppCompat autosize path.
        // Balanced paragraph breaking can leave a Korean item line wider than its cell.
        button.setHorizontallyScrolling(false);
        button.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        button.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        button.setIncludeFontPadding(false);
        button.setPadding(host.dp(4),host.dp(4),host.dp(4),host.dp(4));
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button,12,tablet?16:14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setTextSize(tablet?16:14); button.setEnabled(count>0 && why.isEmpty()); button.setBackground(new BattleSkin(host,"button-item"));button.setTextColor(0xfffff8e8);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,itemRowHeight(items.length()>4),1);lp.setMargins(host.dp(3),host.dp(3),host.dp(3),host.dp(3));line.addView(button,lp);
        line.names.add(item.optString("name")); line.quantities.add(count);
      }
      controls.addView(line);
    }
    int pages=(items.length()+3)/4;
    if(pages>1) add(host.button("다음 목록 · "+(itemPage+1)+"/"+pages,()->{itemPage=(itemPage+1)%pages;render();}));
  }

  /** Keep Korean words intact when two name lines fit above the quantity. */
  static String itemLabel(String name, int count, Paint paint, float width, float minSize) {
    String quantity="\n×"+count;
    if(width<=0 || paint.measureText(name)<=width) return name+quantity;
    int split=-1;
    float best=Float.MAX_VALUE;
    for(int i=1;i<name.length()-1;i++) {
      if(name.charAt(i)!=' ') continue;
      float widest=Math.max(paint.measureText(name.substring(0,i)),
          paint.measureText(name.substring(i+1)));
      // On equal widths retain the longer leading phrase (e.g. 다시 출근).
      if(widest<=best) { best=widest; split=i; }
    }
    if(split<0 || best*minSize/paint.getTextSize()>width) return name+quantity;
    return name.substring(0,split)+"\n"+name.substring(split+1)+quantity;
  }

  final class ItemRow extends LinearLayout {
    final ArrayList<String> names=new ArrayList<>();
    final ArrayList<Integer> quantities=new ArrayList<>();
    ItemRow() { super(host); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
      super.onMeasure(widthSpec,heightSpec);
      boolean changed=false;
      for(int i=0;i<getChildCount();i++) {
        Button button=(Button)getChildAt(i);
        Paint paint=new Paint(button.getPaint());
        float scaled=host.getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize((tablet?16:14)*scaled);
        // Use the measured weighted cell, including its real compound padding.
        int width=button.getMeasuredWidth()-button.getCompoundPaddingLeft()-button.getCompoundPaddingRight();
        String label=itemLabel(names.get(i),quantities.get(i),paint,width,12*scaled);
        if(!android.text.TextUtils.equals(button.getText(),label)) {
          button.setText(label); changed=true;
        }
        // Autosize checks line count/end index, but an explicit paragraph can still
        // exceed its width. Retain only sizes whose real shaped lines fit the cell.
        ArrayList<Integer> fitting=new ArrayList<>();
        int minPx=Math.round(12*scaled);
        for(int sp=12;sp<=(tablet?16:14);sp++) {
          int px=Math.round(sp*scaled);
          android.text.TextPaint candidate=new android.text.TextPaint(button.getPaint());
          candidate.setTextSize(px);
          android.text.StaticLayout layout=android.text.StaticLayout.Builder
              .obtain(label,0,label.length(),candidate,Math.max(1,width))
              .setIncludePad(false)
              .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
              .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE).build();
          boolean fits=layout.getLineCount()<=3;
          for(int line=0;line<layout.getLineCount();line++)
            fits &= layout.getLineWidth(line)<=width;
          if(fits && !fitting.contains(px)) fitting.add(px);
        }
        // Preserve the accessibility floor even if an unknown future name cannot fit.
        if(fitting.isEmpty()) fitting.add(minPx);
        int[] sizes=new int[fitting.size()];
        for(int n=0;n<sizes.length;n++) sizes[n]=fitting.get(n);
        if(!Arrays.equals(sizes,androidx.core.widget.TextViewCompat.getAutoSizeTextAvailableSizes(button))) {
          androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithPresetSizes(
              button,sizes,android.util.TypedValue.COMPLEX_UNIT_PX);
          changed=true;
        }
      }
      if(changed) super.onMeasure(widthSpec,heightSpec);
    }
  }

  /** 제목 줄과 쪽 넘김 버튼을 뺀 패널 높이를 두 줄로 나눈다. 폰 124dp·태블릿 160dp를 넘기지 않아 버튼 그림이 세로로 늘어나지 않는다. */
  int itemRowHeight(boolean paged) {
    int menuHeight=uiHeight-host.dp(80);
    int free=menuHeight-host.dp(48)-(paged?host.dp(54):0)-host.dp(12);
    int row=free/2-host.dp(6);
    return Math.max(host.dp(48),Math.min(row,host.dp(tablet?160:124)));
  }

  void itemTargets() {
    heading("아이템 사용");
    if(selectedItem==null){pane="items";render();return;}
    TextView detail=host.text(selectedItem.optString("name")+" 1개 · 사용하면 이번 턴 행동이 끝나요.",14);
    detail.setTextColor(0xfffff8e8);controls.addView(detail);
    JSONObject action=MainActivity.obj("kind","item","item",selectedItem.optString("id"));
    if(selectedItem.optInt("revive")>0) {
      JSONArray fighters=host.list(side(0),"fighters");
      for(int i=0;i<fighters.length();i++) {
        final int index=i;JSONObject f=fighters.optJSONObject(i);
        Button target=host.button(f.optString("name")+(f.optInt("hp")>0?" · 부활 대상 아님":" · 부활 사용"),()->{
          try{action.put("target",index);}catch(JSONException ignored){}
          host.battleTurnFrom(battle,MainActivity.obj("action",action));
        });target.setEnabled(f.optInt("hp")<=0);add(target);
      }
    } else add(host.button("사용",()->host.battleTurnFrom(battle,MainActivity.obj("action",action))));
  }

  void choiceLauncher(String title, Runnable open) {
    controls.removeAllViews();
    heading(title);
    TextView hint =
        NativeBattleSheets.prose(
            host,
            title.equals("기술 선택")
                ? "넓은 기술 카드에서 이름과 효과를 확인해요."
                : title.equals("아이템") ? "사용할 아이템을 고르세요." : "다음에 출전할 갈매미를 고르세요.",
            16);
    controls.addView(hint);
    Button button = host.button(title.equals("기술 선택") ? "기술 카드 선택" : title + " 목록", open);
    button.setBackground(new GameButtonDrawable(host, JournalTheme.PRIMARY));
    button.setTextColor(JournalTheme.controlText(JournalTheme.PRIMARY));
    controls.addView(button, new LinearLayout.LayoutParams(-1, host.dp(56)));
  }

  void switches(boolean required, String aug) {
    NativeBattleSheets.switches(this, required, aug);
  }

  void choose() {
    logBox.setVisibility(GONE);
    menu.setVisibility(GONE);
    if (augmentView == null) {
      augmentView = new NativeAugmentView(this);
      addView(augmentView);
    }
  }

  void items() {
    NativeBattleSheets.items(this);
  }

  final class Scene extends View {
    Paint p = new Paint(3);
    final JournalBattleLight ambientLight = new JournalBattleLight();
    final Runnable timeChanged = this::invalidate;

    Scene() {
      super(host);
    }

    @Override
    protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      host.journalTime.addListener(timeChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
      host.journalTime.removeListener(timeChanged);
      super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      ambientLight.resize(w, h);
    }

    void bitmap(Canvas c, Bitmap b, Rect src, RectF to) {
      if (b != null) {
        p.setAlpha(255);
        c.drawBitmap(b, src, to, p);
      }
    }

    void fit(Canvas c, Bitmap b, String path, RectF to) {
      if (b == null) return;
      Rect src = host.art.alpha(b, path);
      float ratio = (float) src.width() / src.height(),
          w = Math.min(to.width(), to.height() * ratio),
          h = w / ratio;
      bitmap(
          c,
          b,
          src,
          new RectF(to.centerX() - w / 2, to.bottom - h, to.centerX() + w / 2, to.bottom));
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w = getWidth(), h = getHeight();
      String background = BattleBackground.path(battle.optString("scene", "harbor"));
      Bitmap bg = battle.optString("scene", "harbor").equals("harbor")
          ? BattleSkin.bitmap(host,"bg-harbor")
          : background == null ? null : host.art.scene(background, this::invalidate);
      if (bg != null) {
        float scale = Math.max((safeLeft + uiWidth * .655f) / bg.getWidth(), h / bg.getHeight()),
            baseWidth = bg.getWidth() * scale,
            baseHeight = bg.getHeight() * scale,
            bw = baseWidth * 1.035f,
            bh = baseHeight * 1.035f;
        // 웹 background-position:left 62%를 기준으로 패럴랙스 여백만 덧댄다.
        float left = -(bw - baseWidth) / 2, top = (h - baseHeight) * .62f - (bh - baseHeight) / 2;
        RectF r = new RectF(left, top, left + bw, top + bh);
        r.offset(parallax.x * .2f, parallax.y * .2f);
        bitmap(c, bg, null, r);
      } else BattleBackground.fallback(c, p, w, h);
      ambientLight.draw(c, battle.optString("scene", "harbor"), host.journalTime.phase());
      c.save();
      c.translate(safeLeft, safeTop);
      w = uiWidth;
      h = uiHeight;
      float world = w * .655f;
      c.save();
      c.clipRect(0,0,world,h);
      JSONObject a = active(0), b = active(1);
      c.save();
      c.translate(parallax.x, parallax.y);
      playback.fighter(c, a, new RectF(world * .045f, h * .30f, world * .495f, h * .90f), 0);
      c.restore();
      c.save();
      c.translate(parallax.x * .6f, parallax.y * .6f);
      playback.fighter(c, b, new RectF(world * .51f, h * .07f, world * .95f, h * .53f), 1);
      c.restore();
      c.save();
      c.translate(parallax.x * .8f, parallax.y * .8f);
      playback.drawEffects(c, world, h);
      c.restore();
      status(c, b, statusArea(1), false);
      status(c, a, statusArea(0), true);
      c.restore();
      panelSkin.surface(c,new RectF(world,0,w,h));
      // 설계(LAYOUT-AND-STATES 7절): 조개는 패널 하단 중앙, 휠은 하단 오른쪽. 마지막 버튼 아래 16dp를 빼고 40dp 이상 남을 때만.
      float gridHeight=2*(commandHeight()+host.dp(6)), menuTop=host.dp(64), menuHeight=uiHeight-host.dp(80);
      float gridBottom=tablet?menuTop+(menuHeight+gridHeight)/2:menuTop+gridHeight;
      float size=Math.min(host.dp(64),h-gridBottom-host.dp(16)-host.dp(12));
      if(pane.equals("menu") && size>=host.dp(40)) {
        float cx=world+(w-world)*.45f;
        BattleSkin.contain(c,BattleSkin.bitmap(host,"decor-shell"),new RectF(cx-size/2,h-size-host.dp(12),cx+size/2,h-host.dp(12)),p);
        BattleSkin.contain(c,BattleSkin.bitmap(host,"decor-wheel"),new RectF(w-size-host.dp(12),h-size-host.dp(12),w-host.dp(12),h-host.dp(12)),p);
      }
      c.restore();
    }

    void fighter(Canvas c, JSONObject f, RectF area) {
      String path = f.optString("image");
      Bitmap b = host.art.scene(path, this::invalidate);
      boolean flash =
          playback.playing
              && playback.effect.equals("hit")
              && f == active(playback.side)
              && playback.progress < .65f;
      if (flash) {
        float light = 100 * (float) Math.sin(playback.progress * Math.PI / .65f);
        p.setColorFilter(
            new ColorMatrixColorFilter(
                new float[] {
                  1, 0, 0, 0, light, 0, 1, 0, 0, light, 0, 0, 1, 0, light, 0, 0, 0, 1, 0
                }));
      }
      fit(c, b, path, area);
      p.setColorFilter(null);
    }

    void status(Canvas c, JSONObject f, RectF r, boolean own) {
      hpSkin.surface(c,r);
      float inset=host.dp(14),x=r.left+inset,right=r.right-inset;
      p.setTypeface(host.headline);p.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);
      p.setTextAlign(Paint.Align.LEFT);p.setColor(JournalTheme.INK);
      String name=f.optString("name");
      float nameWidth=(right-x)*.53f;
      while(p.measureText(name)>nameWidth && name.length()>2) name=name.substring(0,name.length()-2)+"…";
      float baseline=r.top+host.dp(25);
      c.drawText(name,x,baseline,p);
      p.setTypeface(host.prose);p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText(f.optInt("hp")+" / "+f.optInt("maxHp"),right,baseline,p);
      float frac=Math.max(0,Math.min(1,(float)f.optInt("hp")/Math.max(1,f.optInt("maxHp"))));
      float barY=baseline+host.dp(8), bh=host.dp(10);
      p.setColor(0xff263f45);c.drawRoundRect(new RectF(x,barY,right,barY+bh),bh/2,bh/2,p);
      int clip=c.save();c.clipRect(x,barY,x+(right-x)*frac,barY+bh);
      p.setColor(frac>.5f?0xff438f85:frac>.2f?0xffc49a59:0xffb54a3d);
      c.drawRoundRect(new RectF(x,barY,right,barY+bh),bh/2,bh/2,p);c.restoreToCount(clip);
      p.setTextAlign(Paint.Align.LEFT);p.setColor(JournalTheme.INK);
      JSONArray current=BattleEffects.current(f);
      String effects=statusNames(f);
      float available=right-x-host.dp(44);
      while(p.measureText(effects)>available && effects.length()>2) effects=effects.substring(0,effects.length()-2)+"…";
      if(r.height()>=host.dp(74)) c.drawText(effects,x,r.bottom-host.dp(15),p);
    }
  }
}
