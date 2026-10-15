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
  String pane = "menu";
  boolean tablet;
  final Scene scene;
  final TextView log;
  final BattlePlayback playback;
  NativeAugmentView augmentView;
  int arenaWidth, arenaHeight, logHeight, panelLeft, panelWidth;
  int safeLeft, safeTop, uiWidth, uiHeight;
  boolean skillSheetNeeded, skillSheetRequested;

  BattleView(MainActivity a, JSONObject b) {
    super(a);
    host = a;
    parallax = new DragParallax(this, a.dp(12));
    try {
      battle = new JSONObject(b.toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
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
    foeInfo = a.button("1턴 뒤", () -> NativeBattleSheets.fighter(this, 1));
    history = a.button("기록", () -> NativeBattleSheets.history(this));
    augmentInfo = a.button("증강체", () -> NativeBattleSheets.augments(this));
    for (Button tool : new Button[] {foeInfo, history, augmentInfo}) {
      tool.setTextSize(14);
      tool.setPadding(a.dp(2), 0, a.dp(2), a.dp(3));
      tool.setMinHeight(a.dp(48));
      tool.setMinimumHeight(a.dp(48));
      tool.setBackground(new GameButtonDrawable(a, GameButtonDrawable.PAPER));
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
      tp.setMargins(a.dp(2), 0, a.dp(2), 0);
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
    }
    playback = new BattlePlayback(this);
    render();
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

  void showEffects(int side) {
    if (!playback.playing) NativeBattleSheets.effects(this, side);
  }

  RectF statusArea(int side) {
    float ownHeight = Math.min(uiHeight * .29f, host.dp(160));
    float foeHeight = Math.min(uiHeight * .28f, host.dp(160));
    return side == 0
        ? new RectF(uiWidth * .30f, uiHeight * .97f - ownHeight, uiWidth * .635f, uiHeight * .97f)
        : new RectF(uiWidth * .025f, uiHeight * .04f, uiWidth * .335f, uiHeight * .04f + foeHeight);
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
    panelWidth = uiWidth - panelLeft - m;
    int buttonH = commandHeight();
    logHeight =
        pane.equals("menu")
            ? Math.max(minimumLogHeight(), uiHeight - 2 * buttonH - host.dp(32))
            : 0;
    logBox.setVisibility(
        battle.optString("phase").equals("choose") || logHeight == 0 ? GONE : VISIBLE);
    log.setTextSize(tablet ? 23 : 17);
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
            Math.max(1, uiHeight - logHeight - (logHeight == 0 ? 2 : 3) * m), MeasureSpec.EXACTLY));
    tools.measure(
        MeasureSpec.makeMeasureSpec(Math.round(uiWidth * .285f), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
    for (int side = 0; side < 2; side++) {
      RectF area = statusArea(side);
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
    menu.layout(
        safeLeft + panelLeft,
        safeTop + logHeight + (logHeight == 0 ? m : 2 * m),
        safeLeft + uiWidth - m,
        safeTop + uiHeight - m);
    tools.layout(
        safeLeft + Math.round(uiWidth * .35f),
        safeTop + m,
        safeLeft + Math.round(uiWidth * .635f),
        safeTop + m + host.dp(48));
    for (int side = 0; side < 2; side++) {
      RectF area = statusArea(side);
      area.offset(safeLeft, safeTop);
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
    controls.setPadding(0, 0, 0, 0);
    if (!pane.equals("skills")) {
      skillSheetNeeded = false;
      skillSheetRequested = false;
    }
    requestLayout();
    String phase = battle.optString("phase");
    tools.setVisibility(phase.equals("choose") || phase.equals("over") ? GONE : VISIBLE);
    for (int side = 0; side < 2; side++) {
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
    if (phase.equals("choose")) {
      choose();
      return;
    }
    if (phase.equals("over")) {
      controls.addView(host.text("전투 종료", 24));
      return;
    }
    switch (pane) {
      case "skills":
        skills();
        return;
      case "switch":
        choiceLauncher("교체", () -> NativeBattleSheets.switches(this, false, null));
        return;
      case "items":
        choiceLauncher("아이템", () -> NativeBattleSheets.items(this));
        return;
    }
    int height = commandHeight();
    for (int row = 0; row < 2; row++) {
      LinearLayout line = new LinearLayout(host);
      controls.addView(line);
      for (int col = 0; col < 2; col++) {
        final int ix = row * 2 + col;
        String[] names = {
          "공격",
          "아이템",
          "교체",
          battle.optString("mode").equals("normal") || battle.optString("mode").equals("rank")
              ? "나가기"
              : "도망가기"
        };
        Button b =
            host.button(
                names[ix],
                () -> {
                  if (ix == 3) confirmLeave();
                  else if (ix == 1) NativeBattleSheets.items(this);
                  else if (ix == 2) NativeBattleSheets.switches(this, false, null);
                  else {
                    pane = "skills";
                    render();
                  }
                });
        b.setTypeface(host.headline);
        b.setTextSize(tablet ? (ix == 3 ? 30 : 33) : (ix == 3 ? 23 : 27));
        float labelWidth = Math.max(host.dp(60), (panelWidth - host.dp(8)) / 2f - host.dp(16));
        float measuredLabel = b.getPaint().measureText(names[ix]);
        if (measuredLabel > labelWidth)
          b.setTextSize(
              b.getTextSize()
                  / host.getResources().getDisplayMetrics().scaledDensity
                  * labelWidth
                  / measuredLabel);
        b.setSingleLine(true);
        b.setHorizontallyScrolling(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(10));
        b.setBackground(
            new GameButtonDrawable(
                host, ix == 0 ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
        b.setTextColor(
            JournalTheme.controlText(
                ix == 0 ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1);
        p.setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2));
        line.addView(b, p);
      }
    }
  }

  int commandHeight() {
    int width = panelWidth > 0 ? panelWidth : Math.round(uiWidth * .345f) - host.dp(8);
    // 태블릿에서 버튼만 커지지 않게 하고 여유 높이는 전투 기록에 준다.
    int available =
        uiHeight > 0
            ? Math.max(host.dp(72), (uiHeight - minimumLogHeight() - host.dp(32)) / 2)
            : host.dp(152);
    return Math.max(
        host.dp(72),
        Math.min(available, Math.min(host.dp(152), Math.round((width - host.dp(8)) / 2f * .82f))));
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
    String mode = battle.optString("mode");
    String message =
        mode.equals("rank")
            ? "랭크게임에서 나갈까요? 패배로 처리되며 랭크 점수가 차감돼요."
            : mode.equals("normal")
                ? "대전에서 나갈까요? 패배로 처리돼요."
                : mode.equals("story")
                    ? "전투에서 도망칠까요? 경험치를 받지 못하고 남은 체력은 다음 전투로 이어져요."
                    : "연습 전투에서 나갈까요? 진행 상황은 저장되지 않아요.";
    host.confirm(message, () -> host.battleTurn(MainActivity.obj("leave", true)));
  }

  int accessibleCardHeight(int baseDp) {
    return host.dp(baseDp * Math.max(1, host.getResources().getConfiguration().fontScale));
  }

  void skills() {
    heading("기술 선택");
    JSONArray skills = host.list(active(0), "skills");
    ArrayList<JSONObject> normals = new ArrayList<>();
    ArrayList<JSONObject> combos = new ArrayList<>();
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      if (sk.optString("id").startsWith("combo-")) {
        combos.add(sk);
      } else normals.add(sk);
    }
    if (panelWidth <= 0 || uiHeight <= 0) return;
    int available = uiHeight - host.dp(16), required = host.dp(48);
    int width = Math.max(1, panelWidth / 2 - host.dp(6));
    ArrayList<LinearLayout> rows = new ArrayList<>();
    ArrayList<Integer> rowHeights = new ArrayList<>();
    ArrayList<SkillCardView> joint = new ArrayList<>();
    boolean readable = normals.size() <= 4;
    for (int i = 0; i < normals.size(); i += 2) {
      LinearLayout row = new LinearLayout(host);
      int rowHeight = 0;
      for (int j = i; j < Math.min(i + 2, normals.size()); j++) {
        SkillCardView card = new SkillCardView(this, normals.get(j), tablet, true, null);
        rowHeight = Math.max(rowHeight, card.naturalHeight(width));
        readable &= card.summaryFits(width);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
        row.addView(card, lp);
      }
      if (row.getChildCount() == 1)
        row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
      required += rowHeight + host.dp(6);
      rows.add(row);
      rowHeights.add(rowHeight + host.dp(6));
    }
    for (JSONObject combo : combos) {
      SkillCardView card = new SkillCardView(this, combo, tablet, true, null);
      int cardWidth = panelWidth - host.dp(6);
      required += card.naturalHeight(cardWidth) + host.dp(6);
      readable &= card.summaryFits(cardWidth);
      joint.add(card);
    }
    JSONArray legal = host.list(side(0), "legal");
    for (int i = 0; i < legal.length(); i++)
      if (legal.optJSONObject(i).optString("skillId").equals("struggle")) required += host.dp(54);
    skillSheetNeeded = !readable || required > available;
    if (skillSheetNeeded) {
      choiceLauncher("기술 선택", () -> NativeBattleSheets.skills(this));
      if (!skillSheetRequested) {
        skillSheetRequested = true;
        post(
            () -> {
              if (isAttachedToWindow() && pane.equals("skills") && !playback.playing)
                NativeBattleSheets.skills(this);
            });
      }
      return;
    }
    for (int i = 0; i < rows.size(); i++)
      controls.addView(rows.get(i), new LinearLayout.LayoutParams(-1, rowHeights.get(i), 1));
    for (SkillCardView card : joint) {
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(-1, card.naturalHeight(panelWidth - host.dp(6)));
      lp.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
      controls.addView(card, lp);
    }
    for (int i = 0; i < legal.length(); i++) {
      JSONObject a = legal.optJSONObject(i);
      if (a.optString("skillId").equals("struggle"))
        add(host.button("몸통 박치기 · 위력 30", () -> host.battleTurn(MainActivity.obj("action", a))));
    }
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
      Bitmap bg = background == null ? null : host.art.scene(background, this::invalidate);
      if (bg != null) {
        float scale = Math.max(w / bg.getWidth(), h / bg.getHeight()),
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
      float world = w * .645f;
      JSONObject a = active(0), b = active(1);
      c.save();
      c.translate(parallax.x, parallax.y);
      playback.fighter(c, a, new RectF(world * .015f, h * .36f, world * .48f, h * .96f), 0);
      c.restore();
      c.save();
      c.translate(parallax.x * .6f, parallax.y * .6f);
      playback.fighter(c, b, new RectF(world * .51f, h * .2f, world * .95f, h * .66f), 1);
      c.restore();
      c.save();
      c.translate(parallax.x * .8f, parallax.y * .8f);
      playback.drawEffects(c, world, h);
      c.restore();
      status(c, b, statusArea(1), false);
      status(c, a, statusArea(0), true);
      c.restore();
    }

    void fighter(Canvas c, JSONObject f, RectF area) {
      String path = f.optString("image");
      Bitmap b = host.art.scene(path, this::invalidate);
      p.setColor(0x35484139);
      c.drawOval(
          new RectF(
              area.left + area.width() * .1f,
              area.bottom - host.dp(12),
              area.right - area.width() * .1f,
              area.bottom),
          p);
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
      JournalTheme.drawPanel(
          c, r, host.getResources().getDisplayMetrics().density, JournalTheme.PAPER, true);
      float x = r.left + host.dp(14),
          top = r.top + Math.max(host.dp(25), r.height() * .24f),
          right = r.right - host.dp(14),
          size =
              Math.min(
                  (tablet ? 25 : 20) * getResources().getDisplayMetrics().scaledDensity,
                  r.height() * .17f);
      p.setTypeface(host.headline);
      p.setTextSize(size);
      p.setColor(MainActivity.INK);
      p.setTextAlign(Paint.Align.LEFT);
      String name = f.optString("name");
      float space = right - x - host.dp(48);
      while (p.measureText(name) > space && name.length() > 2)
        name = name.substring(0, name.length() - 2) + "…";
      c.drawText(name, x, top, p);
      p.setTypeface(host.prose);
      p.setTextSize(size * .8f);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText("Lv" + f.optInt("level"), right, top, p);
      p.setTextAlign(Paint.Align.LEFT);
      float barY = top + r.height() * .1f, bh = Math.max(5, r.height() * .08f);
      p.setColor(0xffd9d3bf);
      c.drawRoundRect(new RectF(x, barY, right, barY + bh), bh / 2, bh / 2, p);
      float frac =
          Math.max(0, Math.min(1, (float) f.optInt("hp") / Math.max(1, f.optInt("maxHp"))));
      p.setColor(frac > .5f ? 0xff4b947e : frac > .2f ? 0xffc49a59 : 0xffb54a3d);
      c.drawRoundRect(new RectF(x, barY, x + (right - x) * frac, barY + bh), bh / 2, bh / 2, p);
      p.setTextSize(size * .78f);
      String attribute =
          f.optString("attr") + (f.isNull("extraAttr") ? "" : " · " + f.optString("extraAttr"));
      float attrY = barY + bh + r.height() * .17f;
      float chipWidth = p.measureText(attribute) + host.dp(10);
      p.setColor(MenuKit.attrColor(f.optString("attrCode")));
      c.drawRoundRect(
          new RectF(x - host.dp(3), attrY - size * .9f, x + chipWidth, attrY + host.dp(3)),
          host.dp(7),
          host.dp(7),
          p);
      p.setColor(MainActivity.INK);
      c.drawText(attribute, x + host.dp(2), attrY, p);
      p.setTextAlign(Paint.Align.RIGHT);
      p.setColor(MainActivity.INK);
      if (own)
        c.drawText(
            f.optInt("hp") + "/" + f.optInt("maxHp"), right, barY + bh + r.height() * .17f, p);
      else c.drawText((int) Math.ceil(frac * 100) + "%", right, barY + bh + r.height() * .17f, p);
      p.setTextAlign(Paint.Align.LEFT);
      JSONObject u = host.unit(f.optString("key"));
      p.setTextSize(size * .64f);
      JSONArray team = host.list(side(own ? 0 : 1), "fighters");
      float dotsY = r.bottom - r.height() * .10f - size * .20f;
      for (int i = 0; i < team.length(); i++) {
        p.setColor(team.optJSONObject(i).optInt("hp") > 0 ? 0xff51c98b : 0xffb6ae9d);
        c.drawCircle(right - i * host.dp(10), dotsY, host.dp(3.5f), p);
      }
      p.setTextSize(size * .7f);
      JSONArray current = BattleEffects.current(f);
      float effectY = r.bottom - r.height() * .22f;
      String effectLabel;
      if (playback.playing) {
        effectLabel = "효과 갱신 중";
        p.setColor(MainActivity.INK);
      } else if (current.length() == 0) {
        effectLabel = "효과 없음";
        p.setColor(0xff634a35);
      } else {
        JSONObject first = current.optJSONObject(0);
        int shown = 1;
        effectLabel = BattleEffects.compactLabel(first);
        p.setColor(BattleEffects.color(first));
        if (p.measureText(
                effectLabel + (current.length() > shown ? " +" + (current.length() - shown) : ""))
            > right - x) {
          effectLabel = first.optString("label") + " · " + BattleEffects.duration(first) + " 상세";
        }
        if (p.measureText(effectLabel) > right - x)
          effectLabel = "현재 효과 " + current.length() + "개 · 상세";
        else if (current.length() > shown) effectLabel += " +" + (current.length() - shown);
      }
      c.drawText(effectLabel, x, effectY, p);
      p.setTextSize(size * .64f);
      p.setColor(MainActivity.BLUE);
      if (own)
        c.drawText(
            battle.optString("mode").equals("practice")
                ? "연습 · 저장되지 않음"
                : u.has("key") ? "경험치 " + u.optInt("xp") + " / " + u.optInt("xpNext") : "동일 레벨 대전",
            x,
            r.bottom - r.height() * .10f,
            p);
      else {
        JSONArray augs = host.list(side(1), "augments");
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < augs.length(); i++)
          if (augs.optJSONObject(i) != null) names.add(augs.optJSONObject(i).optString("name"));
        String label = android.text.TextUtils.join(" · ", names);
        float footerWidth = right - x - host.dp(team.length() * 10 + 6);
        while (p.measureText(label) > footerWidth && label.length() > 2)
          label = label.substring(0, label.length() - 2) + "…";
        c.drawText(label, x, r.bottom - r.height() * .10f, p);
      }
    }
  }
}
