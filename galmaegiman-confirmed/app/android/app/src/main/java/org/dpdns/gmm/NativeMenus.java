package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** 기존 게임 화면의 구성과 흐름을 옮긴다. 화면마다 다른 레이아웃을 유지한다. */
final class NativeMenus {
  final MainActivity h;
  final MenuKit k;
  final NativeLibrary library;
  final NativeNews news;
  final NativeEconomy economy;
  final NativeExtras extras;
  final NativeGrowthGuidance growth;
  String query = "", grade = "all", recipeQuery = "", recipeId = "";
  boolean craftOnly, teamChart;
  String selectedStageId = "";
  JSONObject lastDraw;
  int pendingFetches;
  ArrayList<String> teamDraft, mixDraft = new ArrayList<>();
  String teamMode = "", unitReturn = "story";
  int teamScrollX;
  ArrayList<String> teamSavedIds = new ArrayList<>();
  String teamReturn = "", teamQuery = "", teamAttr = "all", stageFocusId = "";
  boolean teamSelectedOnly;

  NativeMenus(MainActivity h) {
    this.h = h;
    k = new MenuKit(h);
    library = new NativeLibrary(this);
    news = new NativeNews(h, k);
    economy = new NativeEconomy(this);
    extras = new NativeExtras(this);
    growth = new NativeGrowthGuidance(h, k);
  }

  void reset() {
    query = "";
    grade = "all";
    recipeQuery = "";
    recipeId = "";
    lastDraw = null;
    teamDraft = null;
    teamSavedIds.clear();
    teamMode = "";
    teamReturn = "";
    teamQuery = "";
    teamAttr = "all";
    teamSelectedOnly = false;
    stageFocusId = "";
    selectedStageId = "";
    mixDraft.clear();
    library.reset();
    news.reset();
    economy.resetAcquisition();
    unitReturn = "story";
    teamScrollX = 0;
    extras.guide = 0;
  }

  void fetch(String endpoint, Consumer<JSONObject> next) {
    int epoch = h.epoch;
    pendingFetches++;
    h.api.call(
        endpoint,
        null,
        (r, e) -> {
          pendingFetches--;
          if (h.isFinishing() || h.epoch != epoch) return;
          if (e != null) {
            if (h.api.token().isEmpty()) h.login();
            h.message(e);
          } else next.accept(r);
        });
  }

  void intro(LinearLayout parent, String icon, String name, String note) {
    LinearLayout row = k.row();
    if (parent.getBackground() == null) {
      row.setBackground(new GamePaperDrawable(h));
      row.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
    }
    row.addView(k.image("/duel/icons/" + icon + ".png", 40, 40));
    LinearLayout words = k.column();
    words.addView(heading(name, 22));
    if (!note.isEmpty()) words.addView(k.note(note));
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    parent.addView(row);
  }

  TextView heading(String label, int size) {
    TextView title = h.text(label, size);
    title.setTypeface(h.headline);
    return title;
  }

  /** These panels sit directly over the location art; core choices never enter an overflow sheet. */
  LinearLayout navyPanel() {
    LinearLayout panel = k.column();
    panel.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
    panel.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
    return panel;
  }

  TextView navyLabel(String text, int size, boolean headline) {
    TextView label = h.text(text, size);
    label.setTypeface(headline ? h.headline : h.prose);
    label.setTextColor(JournalTheme.WHITE);
    label.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
    return label;
  }

  void menuAction(LinearLayout parent, Button action) {
    library.fixedAction(action);
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, h.dp(48));
    slot.topMargin = h.dp(4);
    parent.addView(action, slot);
  }

  /** 처음에는 큰 두 갈래만 보여 주고, 세부 실행은 각 갈래 안에서 고른다. */
  void modes() {
    ModesLayout choices = modePage("게임 모드", "modes");
    Button story = modeCard("스토리", 0, () -> h.load("modesStory"));
    story.setTag("modes-story-category");
    NativeTutorialController.anchorPreservingTag(story, "story");
    Button pvp = modeCard("대전", 1, () -> h.load("modesPvp"));
    pvp.setTag("modes-pvp-category");
    addModeCard(choices, story, true);
    addModeCard(choices, pvp, false);
  }

  void storyModes() {
    boolean roster60=h.roster60Menus();
    ModesLayout choices = modePage("스토리", "modesStory");
    Button story = modeCard("스토리모드", 3, () -> h.load("story"));
    story.setTag("modes-story");
    NativeTutorialController.anchorPreservingTag(story, "story");
    Button adventure = modeCard(roster60 ? "모험" : "모험\n준비 중", 2, () -> {if(roster60)h.openRosterBattle(Roster60BattleMode.ADVENTURE);});
    adventure.setTag("modes-adventure");
    adventure.setEnabled(roster60);
    adventure.setContentDescription(roster60?"새 60종 모험":"모험, 준비 중");
    addModeCard(choices, story, true);
    addModeCard(choices, adventure, false);
  }

  ModesLayout modePage(String title, String route) {
    h.base(title, route);
    h.title.setPadding(h.dp(24), h.dp(4), h.dp(24), 0);
    AppSkin.apply(h, "training");
    h.page.removeViewAt(h.page.getChildCount() - 1);
    ModesLayout panes = new ModesLayout();
    panes.setTag("modes-panes");
    panes.setPadding(0, h.dp(8), 0, 0);
    h.page.addView(panes, new LinearLayout.LayoutParams(-1, 0, 1));
    h.body = panes;
    return panes;
  }

  void addModeCard(LinearLayout row, Button card, boolean first) {
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
    if (first) slot.rightMargin = h.dp(12);
    row.addView(card, slot);
  }

  Button modeCard(String label, int motif, Runnable action) {
    Button button = h.button(label, action);
    button.setTypeface(h.headline);
    button.setTextSize(h.uiText(26));
    button.setTextColor(new android.content.res.ColorStateList(
        new int[][] {{-android.R.attr.state_enabled}, {}},
        new int[] {JournalTheme.MUTED, JournalTheme.WHITE}));
    button.setAllCaps(false);
    button.setSingleLine(false);
    button.setMaxLines(2);
    button.setEllipsize(null);
    button.setGravity(Gravity.CENTER);
    // Center the icon and the actual one/two-line title as a single visual stack.
    button.setIncludeFontPadding(false);
    button.setPadding(h.dp(18), h.dp(78), h.dp(18), 0);
    button.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      int reserve = Math.round(modeIconSize(right - left, bottom - top)) + h.dp(12);
      if (button.getPaddingTop() != reserve)
        button.setPadding(h.dp(18), reserve, h.dp(18), 0);
    });
    button.setMinHeight(h.dp(144));
    button.setMinimumHeight(h.dp(144));
    button.setBackground(new ModeCardDrawable(motif, button));
    return button;
  }

  float modeIconSize(int width, int height) {
    return Math.min(h.dp(66), Math.min(Math.max(0, width - h.dp(4)) * .24f,
        Math.max(0, height - h.dp(4)) * .32f));
  }

  /** 기존 장면 그림 위에 책·방패·지도 표식을 얹어 각 갈래를 구분한다. */
  final class ModeCardDrawable extends android.graphics.drawable.Drawable {
    final int motif;
    final Button label;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean enabled = true, pressed, focused;
    ModeCardDrawable(int motif, Button label) { this.motif = motif; this.label = label; }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
      boolean nextEnabled = false, nextPressed = false, nextFocused = false;
      for (int state : states) {
        nextEnabled |= state == android.R.attr.state_enabled;
        nextPressed |= state == android.R.attr.state_pressed;
        nextFocused |= state == android.R.attr.state_focused;
      }
      if (enabled == nextEnabled && pressed == nextPressed && focused == nextFocused) return false;
      enabled = nextEnabled; pressed = nextPressed; focused = nextFocused; invalidateSelf(); return true;
    }
    @Override public void draw(Canvas canvas) {
      Rect bounds = getBounds();
      float d = h.getResources().getDisplayMetrics().density;
      RectF card = new RectF(bounds); card.inset(2 * d, 2 * d);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(!enabled ? JournalTheme.STONE : pressed ? JournalTheme.PRIMARY_HIGHLIGHT : JournalTheme.NAVY);
      canvas.drawRoundRect(card, 12 * d, 12 * d, paint);
      String scene = motif == 0 ? "cabin" : motif == 1 ? "strait" : motif == 2 ? "sky" : "harbor";
      Bitmap art = h.art.get("/duel/bg/" + scene + ".jpg", this::invalidateSelf);
      int pictureSave = canvas.save();
      Path clip = new Path(); clip.addRoundRect(card, 12 * d, 12 * d, Path.Direction.CW);
      canvas.clipPath(clip);
      if (art != null) {
        float scale = Math.max(card.width() / art.getWidth(), card.height() / art.getHeight());
        float width = art.getWidth() * scale, height = art.getHeight() * scale;
        RectF destination = new RectF(card.centerX() - width / 2, card.centerY() - height / 2,
            card.centerX() + width / 2, card.centerY() + height / 2);
        paint.setFilterBitmap(true);
        if (!enabled) {
          ColorMatrix grayscale = new ColorMatrix(); grayscale.setSaturation(0);
          paint.setColorFilter(new ColorMatrixColorFilter(grayscale));
        }
        canvas.drawBitmap(art, null, destination, paint);
        paint.setColorFilter(null);
      }
      // 그림의 색을 남기되 흰 제목과 황동 표식 뒤는 충분히 어둡게 유지한다.
      paint.setShader(new LinearGradient(0, card.top, 0, card.bottom,
          new int[] {0x40101f2b, 0xb8101f2b, 0xe8101f2b}, new float[] {0, .52f, 1}, Shader.TileMode.CLAMP));
      canvas.drawRect(card, paint); paint.setShader(null);
      if (pressed) { paint.setColor(0x304fb6c2); canvas.drawRect(card, paint); }
      canvas.restoreToCount(pictureSave);
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth((focused ? 3 : 1.5f) * d);
      paint.setColor(focused ? JournalTheme.FOCUS : enabled ? JournalTheme.BRASS : JournalTheme.LINE);
      canvas.drawRoundRect(card, 12 * d, 12 * d, paint);
      // 실제 카드 높이에 따라 장식만 이동한다. 글꼴이나 클릭 영역은 줄이지 않는다.
      float size = modeIconSize(bounds.width(), bounds.height());
      float textHeight = label.getLayout() == null ? label.getLineHeight() : label.getLayout().getHeight();
      float x = card.centerX(), y = card.centerY() - (textHeight + 12 * d) / 2;
      int save = canvas.save(); canvas.translate(x - size / 2, y - size / 2); canvas.scale(size / 64, size / 64);
      paint.setColor(enabled ? JournalTheme.BRASS : JournalTheme.MUTED);
      paint.setStrokeWidth(2); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
      if (motif == 0 || motif == 3) {
        canvas.drawRoundRect(new RectF(9, 6, 55, 59), 3, 3, paint);
        canvas.drawLine(20, 7, 20, 58, paint);
        Path bookmark = new Path(); bookmark.moveTo(38, 7); bookmark.lineTo(38, 29);
        bookmark.lineTo(44, 24); bookmark.lineTo(50, 29); bookmark.lineTo(50, 7); bookmark.close();
        paint.setStyle(Paint.Style.FILL); canvas.drawPath(bookmark, paint);
      } else if (motif == 1) {
        Path shield = new Path(); shield.moveTo(10, 6); shield.lineTo(54, 6); shield.lineTo(51, 38);
        shield.quadTo(45, 50, 32, 60); shield.quadTo(19, 50, 13, 38); shield.close();
        canvas.drawPath(shield, paint); canvas.drawLine(28, 18, 28, 43, paint);
        Path flag = new Path(); flag.moveTo(28, 18); flag.lineTo(45, 20); flag.lineTo(28, 29); flag.close();
        paint.setStyle(Paint.Style.FILL); canvas.drawPath(flag, paint);
      } else {
        Path map = new Path(); map.moveTo(6, 13); map.lineTo(23, 7); map.lineTo(41, 13); map.lineTo(58, 7);
        map.lineTo(58, 52); map.lineTo(41, 58); map.lineTo(23, 52); map.lineTo(6, 58); map.close();
        canvas.drawPath(map, paint); canvas.drawLine(23, 7, 23, 52, paint); canvas.drawLine(41, 13, 41, 58, paint);
      }
      canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }

  /** 글꼴에 없는 꺾쇠 문자 대신 버튼 중앙에 선으로 그린다. */
  void pagerChevron(Button button, boolean forward) {
    pagerChevron(button, forward, JournalTheme.NAVY);
  }

  void pagerChevron(Button button, boolean forward, int enabledColor) {
    button.setText("");
    android.graphics.drawable.Drawable arrow = new android.graphics.drawable.Drawable() {
      final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float x = bounds.exactCenterX(), y = bounds.exactCenterY();
        float dx = h.dp(4) * (forward ? 1 : -1), dy = h.dp(7);
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(h.dp(3));
        ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setColor(button.isEnabled() ? enabledColor : JournalTheme.MUTED);
        Path path = new Path(); path.moveTo(x - dx, y - dy);
        path.lineTo(x + dx, y); path.lineTo(x - dx, y + dy);
        canvas.drawPath(path, ink);
      }
      @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); }
      @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
    button.setBackground(new android.graphics.drawable.LayerDrawable(
        new android.graphics.drawable.Drawable[] {button.getBackground(), arrow}));
  }

  android.graphics.drawable.Drawable modeInfoBackground() {
    android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
    surface.setColor(JournalTheme.NAVY);
    surface.setCornerRadius(h.dp(8));
    surface.setStroke(h.dp(1), JournalTheme.BRASS);
    return surface;
  }

  void pvpModes() {
    if(h.roster60Menus()){h.openPlayMenu();return;}
    ModesLayout panes = modePage("대전", "modesPvp");
    LinearLayout team = k.column(), pvp = k.column();
    LinearLayout.LayoutParams teamSlot = new LinearLayout.LayoutParams(0, -1, .46f);
    teamSlot.rightMargin = h.dp(12);
    panes.addView(team, teamSlot);
    panes.addView(pvp, new LinearLayout.LayoutParams(0, -1, .54f));
    h.body = pvp;
    LinearLayout summary = addModeTeamSummary(team, true);
    summary.setBackground(modeInfoBackground());
    team.addView(new View(h), new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout start = k.row();
    Button normal = k.action("mode-normal", "일반 게임", () -> startPvp("normal"));
    normal.setTag("modes-normal");
    Button ranked = k.primary("mode-rank", "랭크게임", () -> startPvp("rank"));
    ranked.setTag("modes-ranked");
    library.actionCell(start, normal); library.actionCell(start, ranked);
    pvp.addView(start, new LinearLayout.LayoutParams(-1, h.dp(52)));
    ModeRankPanel rank = new ModeRankPanel();
    LinearLayout.LayoutParams rankSlot = new LinearLayout.LayoutParams(-1, -2);
    rankSlot.topMargin = h.dp(8);
    pvp.addView(rank, rankSlot);
    pvp.addView(new View(h), new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout links = k.row();
    Button standings = k.action(null, "순위", () -> h.load("pvp"));
    standings.setTag("modes-standings");
    Button practice = k.action(null, "연습", h::practice);
    practice.setTag("modes-practice");
    Button records = k.action(null, "전투 기록", () -> h.load("battleRecords"));
    records.setTag("modes-battle-records");
    library.actionCell(links, practice); library.actionCell(links, standings); library.actionCell(links, records);
    pvp.addView(links, new LinearLayout.LayoutParams(-1, h.dp(52)));
    panes.reflow = compact -> {
      // 인셋 변경은 이미 만든 View·요청 epoch·편성을 그대로 유지한다.
      rank.setCompact(compact);
      start.getLayoutParams().height = h.dp(compact ? 48 : 52);
      links.getLayoutParams().height = h.dp(compact ? 48 : 52);
      setModeTeamCompact(summary, compact);
    };
    final int screenEpoch = h.epoch;
    pendingFetches++;
    h.api.call("pvp/rank", null, (result, error) -> {
      pendingFetches--;
      if (h.isFinishing() || h.epoch != screenEpoch) return;
      rank.update(result, error);
    });
  }

  /** Read-only rank details may shorten; errors and a missing party remain complete and visible. */
  final class ModeRankPanel extends LinearLayout {
    final TextView status, required;
    String summary = "랭크 불러오는 중…";
    boolean compact, failed, needsTeam;

    ModeRankPanel() {
      super(NativeMenus.this.h);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_VERTICAL);
      setTag("modes-rank-frame");
      status = navyLabel(summary, 14, false);
      status.setTag("modes-rank-status");
      required = navyLabel("편성 3마리 필요", 14, false);
      required.setTag("modes-rank-required-team");
      for (TextView label : new TextView[] {status, required}) {
        label.setIncludeFontPadding(false);
        label.setPadding(0, 0, 0, 0);
        addView(label, new LinearLayout.LayoutParams(-1, -2));
      }
      setCompact(false);
    }

    void setCompact(boolean value) {
      compact = value;
      setBackground(modeInfoBackground());
      setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
      setMinimumHeight(h.dp(52));
      renderStatus();
    }

    void update(JSONObject result, String error) {
      failed = error != null;
      needsTeam = !failed && !result.optBoolean("team", h.list(h.me(), "battleTeam").length() == 3);
      if (failed) summary = "순위 정보를 불러오지 못했어요. 순위에서 다시 확인해 주세요.";
      else {
        JSONObject me = result.optJSONObject("me");
        summary = me == null ? "아직 랭크 기록이 없어요."
            : me.optString("tier") + " · " + me.optInt("point") + "점"
                + (me.optInt("rank") > 0 ? " · " + me.optInt("rank") + "위" : "")
                + " · " + me.optInt("wins") + "승 " + me.optInt("losses") + "패";
        summary += "\n승리 +" + result.optInt("win") + " / 패배 −" + result.optInt("lose") + "점";
      }
      renderStatus();
    }

    void renderStatus() {
      String full = summary + (needsTeam ? " · 편성 3마리 필요" : "");
      // singleLine은 무한 가로 스크롤 폭도 활성화하므로 실제 측정 폭에 맞춘다.
      status.setSingleLine(compact && !failed);
      status.setHorizontallyScrolling(false);
      status.setMaxLines(compact && !failed ? 1 : Integer.MAX_VALUE);
      status.setEllipsize(compact && !failed ? android.text.TextUtils.TruncateAt.END : null);
      status.setText(compact && failed ? "순위 조회 실패\n순위에서 다시 확인" : compact ? summary : full);
      status.setContentDescription(full);
      required.setVisibility(compact && needsTeam ? View.VISIBLE : View.GONE);
    }
  }

  /** Reflow on the actual available height, including later cutout/system-bar inset changes. */
  final class ModesLayout extends LinearLayout {
    Consumer<Boolean> reflow;
    Boolean compactLayout;

    ModesLayout() { super(NativeMenus.this.h); setOrientation(HORIZONTAL); setGravity(Gravity.TOP); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      if (reflow != null && MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        boolean compact = MeasureSpec.getSize(heightSpec) < h.dp(340) * fontScale;
        if (compactLayout == null || compactLayout != compact) {
          compactLayout = compact;
          reflow.accept(compact);
        }
      }
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  void setModeTeamCompact(LinearLayout summary, boolean compact) {
    // Keep the original artwork and 44dp portraits; only their relationship to the names changes.
    summary.setPadding(h.dp(compact ? 24 : 12), h.dp(compact ? 6 : 8),
        h.dp(compact ? 24 : 12), h.dp(compact ? 6 : 8));
    TextView title = (TextView) summary.getChildAt(0);
    // Jua has glyphs above its ascent; keep its font padding instead of clipping those strokes.
    title.setIncludeFontPadding(true);
    title.setPadding(h.dp(4), compact ? 0 : h.dp(2), h.dp(4), compact ? 0 : h.dp(2));
    LinearLayout roster = (LinearLayout) summary.getChildAt(1);
    // 좁은 대전 요약은 3개의 세로 행으로 펼친다. 3열 각각에서 그림 44dp를
    // 다시 빼면 큰 글자의 이름 폭이 46px까지 줄어 줄바꿈/생략 획이 넘친다.
    // 행마다 전체 폭을 쓰되 그림·글꼴 크기와 View/listener는 그대로 유지한다.
    roster.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    for (int i = 0; i < roster.getChildCount(); i++) {
      if (!(roster.getChildAt(i) instanceof LinearLayout)) continue; // Empty-party notice.
      LinearLayout member = (LinearLayout) roster.getChildAt(i);
      member.setLayoutParams(new LinearLayout.LayoutParams(compact ? -1 : 0, -2, compact ? 0 : 1));
      member.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
      member.setGravity(compact ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
      TextView name = (TextView) member.getChildAt(1);
      name.setIncludeFontPadding(!compact);
      name.setGravity(compact ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
      name.setPadding(h.dp(4), compact ? 0 : h.dp(2), h.dp(4), compact ? 0 : h.dp(2));
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setLayoutParams(new LinearLayout.LayoutParams(compact ? 0 : -1, -2, compact ? 1 : 0));
    }
    LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) summary.getLayoutParams();
    // Keep a 2dp rounding reserve at the shortest 130% font viewport.
    slot.topMargin = slot.bottomMargin = h.dp(compact ? 1 : 6);
    summary.setLayoutParams(slot);
  }

  void modeTeamSheet(boolean pvp) {
    k.openSheet(
        pvp ? "현재 전투 편성" : "다음 이야기",
        () -> {
          LinearLayout contents = k.column();
          addModeTeamSummary(contents, pvp);
          return contents;
        });
  }

  LinearLayout addModeTeamSummary(LinearLayout parent, boolean pvp) {
    LinearLayout summary = navyPanel();
    summary.setTag(pvp ? "modes-pvp-summary" : "modes-story-summary");
    summary.setGravity(Gravity.CENTER_VERTICAL);
    String caption = pvp ? "현재 전투 편성" : "스토리 방범대";
    if (!pvp) {
      JSONObject next = null;
      JSONArray chapters = h.list(h.state, "chapters");
      for (int c = 0; c < chapters.length() && next == null; c++) {
        JSONArray stages = h.list(chapters.optJSONObject(c), "stages");
        for (int i = 0; i < stages.length(); i++) {
          JSONObject stage = stages.optJSONObject(i);
          if (stage.optBoolean("open") && !stage.optBoolean("cleared")) { next = stage; break; }
        }
      }
      if (next != null) caption = "다음 · " + storyDescription(next.optString("title"));
    }
    TextView title = navyLabel(caption, 15, true);
    title.setSingleLine(true); title.setHorizontallyScrolling(false); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setContentDescription(caption);
    summary.addView(title);
    LinearLayout line = k.row();
    JSONArray selected = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
    for (int i = 0; i < selected.length(); i++) {
      String key = selected.optString(i);
      JSONObject unit = pvp ? ownedSpecies(key) : h.unit(key);
      LinearLayout member = k.column();
      member.setGravity(Gravity.CENTER_HORIZONTAL);
      PartyCharacterView.Artwork portrait = PartyCharacterView.art(h, unit);
      portrait.setTag("modes-party-character-" + (pvp ? "pvp-" : "story-") + key);
      portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      member.addView(portrait, new LinearLayout.LayoutParams(h.dp(44), h.dp(44)));
      String live = unit.has("level") ? " · Lv" + unit.optInt("level") : "";
      TextView name = navyLabel((i == 0 ? "선봉 · " : "") + unit.optString("name") + live, 13, false);
      name.setContentDescription(name.getText());
      name.setGravity(Gravity.CENTER); name.setMaxLines(2);
      member.addView(name);
      line.addView(member, new LinearLayout.LayoutParams(0, -2, 1));
    }
    if (selected.length() == 0) line.addView(navyLabel("아직 편성한 갈매미가 없어요.", 14, false));
    summary.addView(line);
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2);
    slot.setMargins(0, h.dp(6), 0, h.dp(6));
    parent.addView(summary, slot);
    return summary;
  }

  JSONObject ownedSpecies(String id) {
    JSONArray units = h.list(h.me(), "units");
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (id.equals(unit.optString("id"))) return unit;
    }
    return new JSONObject();
  }

  void startPvp(String mode) {
    if (h.list(h.me(), "battleTeam").length() != 3) {
      h.confirm(
          "PvP는 전투 편성 3마리가 필요해요. 지금 편성할까요?",
          () -> {
            h.selectionMode = "pvp";
            teamDraft = null;
            h.load("team");
          });
      return;
    }
    if (mode.equals("rank"))
      h.confirm("실제 유저와 실시간으로 대전해요. 매칭 후 나가면 패배로 기록돼요. 시작할까요?", () -> h.startFight(mode, null));
    else h.startFight(mode, null);
  }

  /** Display only authored chapter/stage/intro/lock copy; never pass unit or player names here. */
  private String storyDescription(String authoredDescription) {
    return RecruitmentText.display(authoredDescription).replace("수비대", "방범대");
  }

  void story(boolean stages) {
    JSONArray chapters = h.list(h.state, "chapters");
    JSONObject chapter = chapters.optJSONObject(h.chapter);
    h.base(
        stages && chapter != null ? storyDescription(chapter.optString("title")) : "스토리 · 챕터 선택",
        stages ? "stages" : "story");
    AppSkin.apply(h, "training");
    LinearLayout[] cols = k.fixedSplit(.32f);
    LinearLayout team = cols[0], content = cols[1];
    JSONArray keys = h.list(h.state, "team");
    ArrayList<JSONObject> storyTeam = new ArrayList<>();
    ArrayList<View> storyCards = new ArrayList<>();
    ArrayList<String> storyKeys = new ArrayList<>();
    for (int i = 0; i < keys.length(); i++) {
      String key = keys.optString(i);
      JSONObject u = h.unit(key);
      storyTeam.add(u);
      LinearLayout tile =
          storyRosterUnit(
              u,
              i,
              () -> {
                unitReturn = stages ? "stages" : "story";
                h.unitDetail(key);
              },
              stages);
      LinearLayout.LayoutParams unitSlot = new LinearLayout.LayoutParams(-1, -2);
      unitSlot.bottomMargin = h.dp(6);
      team.addView(tile, unitSlot);
      storyCards.add(tile);
      storyKeys.add(key);
    }
    NativeTeamReorder.bind(
        h,
        team,
        storyCards,
        storyKeys,
        changed -> {
          int screenEpoch = h.epoch;
          int[] scroll = scrollPositions(h.page);
          h.request(
              "native/team",
              MainActivity.obj("keys", MainActivity.arr(changed)),
              (r, e) -> {
                if (screenEpoch == h.epoch) refreshStory(stages, scroll);
              });
        });
    for (View card : storyCards) {
      View handle = card.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
      if (handle != null) { handle.setOnClickListener(null); handle.setClickable(false); }
    }
    if (storyCards.size() > 1 && h.contentHeightDp() >= 500) {
      TextView dragHint = k.note("↕ 끌어서 선봉 변경 · 바로 저장");
      dragHint.setTag("story-order-drag-hint");
      dragHint.setTextSize(h.uiText(12));
      dragHint.setTextColor(JournalTheme.WHITE);
      dragHint.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
      dragHint.setPadding(h.dp(6), h.dp(2), h.dp(6), h.dp(2));
      team.addView(dragHint, 0);
    }

    if (keys.length() == 0) {
      TextView empty = navyLabel("먼저 방범대를 정해 주세요.", 16, false);
      empty.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
      team.addView(empty);
    }
    LinearLayout storyHeader = (LinearLayout) h.page.getChildAt(0);
    Button editTeam =
        k.action(
            "m-units",
            "편성",
            () -> {
              h.selectionMode = "story";
              teamDraft = null;
              teamReturn = stages ? "stages" : "story";
              h.team();
            });
    Button healing =
        k.action(
            "potion",
            "치료약",
            () -> library.healSheet(null, () -> h.load(stages ? "stages" : "story")));
    editTeam.setTag("story-edit-team");
    healing.setTag("story-heal");
    library.fixedAction(editTeam); library.fixedAction(healing);
    editTeam.setPadding(h.dp(6), 0, h.dp(6), h.dp(3));
    healing.setPadding(h.dp(6), 0, h.dp(6), h.dp(3));
    float storyFontScale = h.getResources().getConfiguration().fontScale;
    LinearLayout.LayoutParams teamAction = new LinearLayout.LayoutParams(h.dp(Math.round(88 * storyFontScale)), h.dp(48));
    teamAction.leftMargin = h.dp(4);
    LinearLayout.LayoutParams healAction = new LinearLayout.LayoutParams(h.dp(Math.round(104 * storyFontScale)), h.dp(48));
    healAction.leftMargin = h.dp(4);
    storyHeader.addView(editTeam, 2, teamAction);
    storyHeader.addView(healing, 3, healAction);
    if (!stages || chapter == null) {
      HorizontalScrollView scroll = new HorizontalScrollView(h);
      scroll.setTag("story-chapters-inline");
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout row = k.row();
      scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
      content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int i = 0; i < chapters.length(); i++) {
        int index = i;
        JSONObject c = chapters.optJSONObject(i);
        StorySelectionCard card = new StorySelectionCard(this, c, index);
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(h.dp(h.compact() ? 260 : 320), -1);
        slot.setMargins(h.dp(4), h.dp(4), h.dp(4), h.dp(4)); row.addView(card, slot);
      }
      if (chapters.length() == 0) content.addView(k.notice("아직 공개된 이야기가 없어요."));
    } else {
      JSONArray stagesInChapter = h.list(chapter, "stages");
      int chosen = -1;
      String desired = stageFocusId.isEmpty() ? selectedStageId : stageFocusId;
      for (int i = 0; i < stagesInChapter.length(); i++)
        if (stagesInChapter.optJSONObject(i).optString("id").equals(desired)) chosen = i;
      if (chosen < 0) {
        chosen = 0;
        for (int i = 0; i < stagesInChapter.length(); i++) {
          JSONObject stage = stagesInChapter.optJSONObject(i);
          if (stage.optBoolean("open") && !stage.optBoolean("cleared")) { chosen = i; break; }
        }
      }
      stageFocusId = "";
      JSONObject selected = stagesInChapter.optJSONObject(chosen);
      if (selected != null) selectedStageId = selected.optString("id");
      HorizontalScrollView choices = new HorizontalScrollView(h);
      choices.setTag("story-stages-inline"); choices.setHorizontalScrollBarEnabled(false);
      LinearLayout tabs = k.row();
      choices.addView(tabs);
      for (int i = 0; i < stagesInChapter.length(); i++) {
        final int index = i;
        JSONObject stage = stagesInChapter.optJSONObject(i);
        Button pick = k.action(null, (i + 1) + " · "
            + (stage.optBoolean("cleared") ? "완료" : stage.optBoolean("open") ? "진행" : "잠김"),
            () -> selectStoryStage(stagesInChapter, index));
        pick.setContentDescription(storyDescription(stage.optString("title")) + " · " + pick.getText());
        library.fixedAction(pick);
        pick.setTag("story-stage-choice-" + stage.optString("id"));
        pick.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.TAB));
        pick.setTextColor(JournalTheme.tabText());
        pick.setSelected(stage.optString("id").equals(selectedStageId));
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(h.dp(190), h.dp(48));
        slot.setMargins(h.dp(2), 0, h.dp(2), 0); tabs.addView(pick, slot);
        if (pick.isSelected()) choices.post(() -> choices.scrollTo(Math.max(0, pick.getLeft() - h.dp(12)), 0));
      }
      content.addView(choices, new LinearLayout.LayoutParams(-1, h.dp(52)));
      if (selected != null) content.addView(stageCard(selected), new LinearLayout.LayoutParams(-1, 0, 1));
      else content.addView(k.notice("아직 공개된 전투가 없어요."));
    }
    if (stages) {
      LinearLayout tools = k.row();
      library.actionCell(tools, k.action("u-story", "챕터", () -> h.load("story")));
      content.addView(tools, new LinearLayout.LayoutParams(-1, h.dp(52)));
    }
    startStoryRefresh(stages);
  }

  /** 세 동료의 원문 이름·성장·체력을 한 화면에 두고 48dp 순서 손잡이를 유지한다. */
  LinearLayout storyRosterUnit(JSONObject unit, int order, Runnable open, boolean stages) {
    LinearLayout card = k.row(), info = k.column();
    card.setTag("story-team-unit-" + unit.optString("key"));
    card.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
    card.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
    card.addView(k.image(unit.optString("image"), 44, 48));
    TextView name = heading(unit.optString("name"), h.compact() ? 15 : 19);
    name.setTextColor(JournalTheme.WHITE);
    name.setSingleLine(true);
    name.setPadding(h.dp(2), 0, h.dp(2), 0);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    info.addView(name);
    String attr = unit.optString("attrName", unit.optString("attr"));
    TextView health =
        k.note(
            "Lv"
                + unit.optInt("level")
                + " · "
                + attr
                + " · "
                + unit.optInt("hp")
                + "/"
                + unit.optInt("maxHp"));
    health.setTextColor(JournalTheme.WHITE);
    health.setTextSize(h.uiText(12));
    health.setPadding(h.dp(2), 0, h.dp(2), 0);
    info.addView(health);
    String growth =
        h.list(unit, "evolve").length() > 0
            ? "승진"
            : h.list(unit, "promo").length() > 0
                ? "전용기"
                : h.list(unit, "learn").length() > 0 ? "새 기술" : "";
    TextView experience =
        k.note(
            "XP "
                + unit.optInt("xp")
                + "/"
                + unit.optInt("xpNext")
                + (growth.isEmpty() ? "" : " · " + growth));
    experience.setPadding(h.dp(2), 0, h.dp(2), 0);
    experience.setTextSize(h.uiText(12));
    experience.setTextColor(growth.isEmpty() ? JournalTheme.PAPER : JournalTheme.FOCUS);
    info.addView(experience);
    card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    TextView handle = h.text((order == 0 ? "선봉" : (order + 1) + "번") + "\n↕", 14);
    handle.setGravity(Gravity.CENTER);
    handle.setPadding(0, 0, 0, 0);
    handle.setTextColor(JournalTheme.WHITE);
    handle.setTag(NativeTeamReorder.HANDLE_TAG);
    handle.setFocusable(true);
    handle.setClickable(true);
    handle.setMinHeight(h.dp(48));
    handle.setContentDescription(unit.optString("name") + " 출전 순서. 위아래로 끌어 변경");
    card.addView(handle, new LinearLayout.LayoutParams(h.dp(48), -2));
    card.setContentDescription(
        (order == 0 ? "선봉 " : (order + 1) + "번 ")
            + unit.optString("name")
            + " · "
            + health.getText()
            + " · "
            + experience.getText()
            + " · "
            + NativeGrowthGuidance.badge(unit)
            + ". 누르면 성장 보기. 손잡이를 위아래로 끌어 순서 변경");
    card.setOnClickListener(v -> open.run());
    View.AccessibilityDelegate orderActions = new View.AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        int count = h.list(h.state, "team").length();
        if (order > 0) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
            android.R.id.button1, "앞 순서로 이동"));
        if (order + 1 < count) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
            android.R.id.button2, "뒤 순서로 이동"));
      }
      @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
        if (action == android.R.id.button1 || action == android.R.id.button2) {
          moveStoryMember(unit.optString("key"), action == android.R.id.button1 ? -1 : 1, stages);
          return true;
        }
        return super.performAccessibilityAction(host, action, args);
      }
    };
    card.setAccessibilityDelegate(orderActions);
    handle.setAccessibilityDelegate(orderActions);
    return card;
  }

  void selectStoryStage(JSONArray stages, int index) {
    JSONObject stage = stages.optJSONObject(index);
    if (stage == null) return;
    selectedStageId = stage.optString("id");
    story(true);
  }

  void storyStagePicker(JSONArray stages) {
    k.openSheet(
        "전투 선택",
        () -> {
          LinearLayout choices = k.column();
          for (int i = 0; i < stages.length(); i++) {
            int index = i;
            JSONObject stage = stages.optJSONObject(i);
            Button select =
                k.action(
                    null,
                    (stage.optBoolean("cleared") ? "✓ " : stage.optBoolean("open") ? "" : "잠김 · ")
                        + storyDescription(stage.optString("title")),
                    () -> selectStoryStage(stages, index));
            select.setSelected(stage.optString("id").equals(selectedStageId));
            choices.addView(select, new LinearLayout.LayoutParams(-1, h.dp(48)));
          }
          return choices;
        });
  }

  void storyCombos(Collection<JSONObject> members) {
    k.openSheet(
        "우리 방범대 합동기",
        () -> {
          LinearLayout content = k.column();
          content.addView(growth.combos(members));
          return content;
        });
  }

  void storyGuide(JSONObject chapter) {
    k.openSheet(
        "부 소개 · 성장 안내",
        () -> {
          LinearLayout guide = k.column();
          guide.addView(heading(storyDescription(chapter.optString("title")), 22));
          if (!chapter.optString("intro").isEmpty())
            guide.addView(k.note(storyDescription(chapter.optString("intro"))));
          JSONObject lock = chapter.optJSONObject("lock");
          if (lock != null && !lock.optBoolean("open"))
            guide.addView(k.notice(storyDescription(lock.optString("reason"))));
          if (chapter.optInt("needStage") > 0)
            guide.addView(k.note("이 부의 적은 " + chapter.optInt("needStage") + "단계 승진 상태예요. 앞 이야기만 클리어하면 들어갈 수 있고, 승진하면 능력치가 올라 유리해요."));
          guide.addView(k.note("진 전투에서도 경험치를 받아요. 완료한 전투는 다시 도전해 레벨을 올릴 수 있어요."));
          guide.addView(k.note("레벨이 오르면 체력이 가득 차요. 새 기술·전용 기술·승진 가능 표시는 팀 칸에서 확인해 주세요."));
          guide.addView(k.action(null, "스토리 처음부터", this::resetStory));
          return guide;
        });
  }

  LinearLayout stageCard(JSONObject stage) {
    JSONArray foes = h.list(stage, "enemy");
    JSONObject foe = foes.optJSONObject(0);
    String id = foe == null ? "" : foe.optString("id");
    JSONObject portraits = h.state.optJSONObject("portraits");
    LinearLayout card = k.column(), line = k.row();
    card.setTag("story-stage-" + stage.optString("id"));
    ImageView scene = k.image("/duel/bg/" + stage.optString("scene", "harbor") + ".jpg", 1, 1);
    scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
    card.addView(scene, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout info = navyPanel();
    line.addView(k.image(portraits == null ? "" : portraits.optString(id), 60, 60));
    line.addView(
        navyLabel(
            (stage.optBoolean("cleared") ? "완료 · " : stage.optBoolean("open") ? "" : "잠김 · ")
                + storyDescription(stage.optString("title")),
            20, true),
        new LinearLayout.LayoutParams(0, -2, 1));
    info.addView(line);
    ArrayList<String> opponents = new ArrayList<>();
    JSONObject names = h.state.optJSONObject("names");
    for (int j = 0; j < foes.length(); j++) {
      JSONObject enemy = foes.optJSONObject(j);
      String enemyName = enemy.optString("name");
      if (enemyName.isEmpty() && names != null) enemyName = names.optString(enemy.optString("id"));
      if (enemyName.isEmpty()) continue; // Undisclosed identity is not replaced with an internal ID.
      opponents.add(enemyName
              + " Lv"
              + enemy.optInt("level")
              + (enemy.optBoolean("boss") ? " · 보스" : ""));
    }
    TextView enemyText = navyLabel(android.text.TextUtils.join(" · ", opponents), 13, false);
    enemyText.setMaxLines(2);
    info.addView(enemyText);
    Button start =
        k.action(
            "u-story",
            stage.optBoolean("cleared")
                ? "다시 도전"
                : stage.optBoolean("open") ? "이야기 시작" : "잠금 조건 확인",
            () -> {
              if (!stage.optBoolean("open")) {
                showStageLock(stage);
                return;
              }
              h.storyScene = stage.optString("scene", "harbor");
              h.storyFoe = id;
              h.talk(
                  withTip(h.list(stage, "before"), stage.optString("hint")),
                  storyDescription(stage.optString("title")),
                  () -> h.startFight("story", stage.optString("id")));
            });
    if (stage.optBoolean("open")) k.emphasize(start);
    if (stage.optString("id").equals("P-1")) NativeTutorialController.anchorPreservingTag(start, "stage");
    start.setTag("story-stage-start");
    menuAction(info, start);
    card.addView(info);
    return card;
  }

  void showStageLock(JSONObject stage) {
    JSONObject lock = stage.optJSONObject("lock");
    LinearLayout content = k.column();
    content.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
    content.addView(
        k.note(lock == null ? "앞 전투의 완료 여부와 현재 방범대를 다시 확인해 주세요." : storyDescription(lock.optString("reason"))));
    JSONArray requirements = h.list(lock == null ? new JSONObject() : lock, "requirements");
    for (int i = 0; i < requirements.length(); i++) {
      JSONObject requirement = requirements.optJSONObject(i);
      boolean met = requirement.optBoolean("met");
      TextView item = k.note((met ? "✓ " : "○ ") + requirement.optString("label"));
      item.setTextColor(met ? 0xff276646 : 0xff8b4d13);
      content.addView(item);
      if (!met) {
        String action = requirement.optString("action");
        if (action.equals("stage") || action.equals("growth") || action.equals("team")) {
          String caption =
              action.equals("stage")
                  ? "필요한 전투로"
                  : action.equals("growth") ? requirement.optString("name") + " 성장" : "방범대 편성";
          content.addView(k.action(null, caption, () -> navigateRequirement(requirement)));
        }
        JSONArray conditions = h.list(requirement, "conditions");
        for (int j = 0; j < conditions.length(); j++) {
          JSONObject condition = conditions.optJSONObject(j);
          content.addView(
              k.note(
                  "  "
                      + (condition.optBoolean("met") ? "✓ " : "○ ")
                      + condition.optString("label")));
          if (!condition.optBoolean("met") && condition.optString("action").equals("stage"))
            content.addView(k.action(null, "필요한 부 진행", () -> navigateRequirement(condition)));
        }
      }
    }
    new GameDialog(h)
        .setTitle(storyDescription(stage.optString("title")) + " · 잠금 조건")
        .setView(k.scroll(content))
        .setNegativeButton("닫기", null)
        .show();
  }

  void navigateRequirement(JSONObject requirement) {
    if (h.visibleDialog != null) h.visibleDialog.dismiss();
    String action = requirement.optString("action");
    if (action.equals("growth") && !requirement.optString("key").isEmpty()) {
      unitReturn = "stages";
      h.unitDetail(requirement.optString("key"));
    } else if (action.equals("team")) {
      h.selectionMode = "story";
      teamDraft = null;
      teamReturn = "stages";
      h.team();
    } else if (action.equals("stage")) {
      String id = requirement.optString("stageId");
      int chapter = requirement.optInt("chapterIndex", h.chapter);
      JSONArray chapters = h.list(h.state, "chapters");
      for (int c = 0; c < chapters.length(); c++) {
        JSONArray stages = h.list(chapters.optJSONObject(c), "stages");
        for (int i = 0; i < stages.length(); i++)
          if (!id.isEmpty() && id.equals(stages.optJSONObject(i).optString("id"))) chapter = c;
      }
      h.chapter = Math.max(0, Math.min(chapters.length() - 1, chapter));
      stageFocusId = id;
      h.load("stages");
    }
  }

  void revealStage(View card) {
    ViewParent parent = card.getParent();
    while (parent instanceof View) {
      if (parent instanceof ScrollView) {
        ScrollView scroll = (ScrollView) parent;
        android.graphics.Rect bounds = new android.graphics.Rect();
        card.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(card, bounds);
        scroll.smoothScrollTo(0, bounds.top);
        break;
      }
      parent = parent.getParent();
    }
  }

  void startStoryRefresh(boolean stages) {
    android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    String currentScreen = stages ? "stages" : "story";
    View page = h.page;
    int screenEpoch = h.epoch;
    Runnable refresh =
        new Runnable() {
          public void run() {
            if (!page.isAttachedToWindow()
                || !currentScreen.equals(h.screen)
                || screenEpoch != h.epoch) return;
            if (h.busy || !h.getWindow().getDecorView().hasWindowFocus()) {
              ticker.postDelayed(this, 60_000);
              return;
            }
            refreshStory(stages, scrollPositions(page));
          }
        };
    ticker.postDelayed(refresh, 60_000);
    page.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          public void onViewAttachedToWindow(View v) {}

          public void onViewDetachedFromWindow(View v) {
            ticker.removeCallbacks(refresh);
          }
        });
  }

  void refreshStory(boolean stages, int[] scroll) {
    int epoch = h.epoch;
    h.busy = true;
    h.api.call(
        "native/state",
        null,
        (updated, error) -> {
          h.busy = false;
          if (h.isFinishing() || epoch != h.epoch) return;
          if (error != null) {
            startStoryRefresh(stages);
            return;
          }
          h.state = updated;
          h.stateUpdated();
          story(stages);
          View page = h.page;
          page.post(
              () -> {
                if (page == h.page) restoreScroll(page, scroll, new int[] {0});
              });
        });
  }

  int[] scrollPositions(View view) {
    ArrayList<Integer> values = new ArrayList<>();
    gatherScroll(view, values);
    int[] positions = new int[values.size()];
    for (int i = 0; i < positions.length; i++) positions[i] = values.get(i);
    return positions;
  }

  void gatherScroll(View view, ArrayList<Integer> values) {
    if (view instanceof ScrollView) values.add(view.getScrollY());
    if (view instanceof HorizontalScrollView) values.add(view.getScrollX());
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) gatherScroll(group.getChildAt(i), values);
    }
  }

  void restoreScroll(View view, int[] values, int[] at) {
    if (view instanceof ScrollView && at[0] < values.length) view.scrollTo(0, values[at[0]++]);
    if (view instanceof HorizontalScrollView && at[0] < values.length)
      view.scrollTo(values[at[0]++], 0);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        restoreScroll(group.getChildAt(i), values, at);
    }
  }

  void moveStoryMember(String key, int direction, boolean stages) {
    if (h.busy) return;
    JSONArray saved = h.list(h.state, "team");
    ArrayList<String> keys = new ArrayList<>();
    for (int i = 0; i < saved.length(); i++) keys.add(saved.optString(i));
    int from = keys.indexOf(key), to = from + direction;
    if (from < 0 || to < 0 || to >= keys.size()) return;
    keys.remove(from); keys.add(to, key);
    int screenEpoch = h.epoch;
    int[] scroll = scrollPositions(h.page);
    h.request("native/team", MainActivity.obj("keys", MainActivity.arr(keys)), (r, e) -> {
      if (screenEpoch == h.epoch) refreshStory(stages, scroll);
    });
  }

  void resetStory() {
    h.confirm(
        "레벨·경험치·배운 기술·승진·완료한 스토리를 모두 초기화할까요? 가진 갈매미와 재화는 유지돼요.",
        () ->
            h.confirm(
                "초기화한 스토리는 되돌릴 수 없어요. 처음부터 시작할까요?",
                () ->
                    h.request(
                        "native/story/reset",
                        MainActivity.obj("confirm", "RESET"),
                        (r, e) -> {
                          h.chapter = 0;
                          h.load("story");
                        })));
  }

  JSONArray withTip(JSONArray before, String hint) {
    JSONArray lines = new JSONArray();
    for (int i = 0; i < before.length(); i++) lines.put(before.opt(i));
    if (!hint.isEmpty()) lines.put(MainActivity.obj("who", "TIP", "say", hint));
    return lines;
  }

  void startPodiumQuotes(TextView label, Collection<JSONObject> picked) {
    if (!h.motionEnabled) return;
    ArrayList<String> quotes = new ArrayList<>();
    for (JSONObject u : picked) {
      String quote = u.optString("quote");
      if (quote.isEmpty()) {
        JSONArray owned = h.list(h.me(), "units");
        for (int i = 0; i < owned.length(); i++)
          if (u.optString("id").equals(owned.optJSONObject(i).optString("id")))
            quote = owned.optJSONObject(i).optString("quote");
      }
      if (!quote.isEmpty()) quotes.add(u.optString("name") + " · 「" + quote + "」");
    }
    if (quotes.isEmpty()) return;
    android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
    int epoch = h.epoch;
    Runnable cycle =
        new Runnable() {
          int index;

          public void run() {
            if (!label.isAttachedToWindow() || epoch != h.epoch) return;
            label.setText(quotes.get(index++ % quotes.size()));
            label.setTextColor(JournalTheme.MUTED);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            timer.postDelayed(this, 3600);
          }
        };
    timer.postDelayed(cycle, 3600);
    label.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          public void onViewAttachedToWindow(View v) {}

          public void onViewDetachedFromWindow(View v) {
            timer.removeCallbacks(cycle);
          }
        });
  }

  void defenderTabs(LinearLayout parent, int active) {
    k.tabs(
        parent,
        new String[] {"내 갈매미", "전투 편성", "아이템"},
        active,
        i -> {
          if (i == active) return;
          Runnable change =
              () -> {
                if (i == 0) h.collection();
                else if (i == 1) {
                  h.selectionMode =
                      NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
                  teamDraft = null;
                  h.team();
                } else h.inventory();
              };
          if (h.screen.equals("team")) requestLeaveTeam(change);
          else change.run();
        });
    View last = parent.getChildAt(parent.getChildCount() - 1);
    if (last instanceof LinearLayout && ((LinearLayout) last).getChildCount() > 1)
      NativeTutorialController.anchor(((LinearLayout) last).getChildAt(1), "team");
  }

  boolean isTeamDirty() {
    return teamDraft != null && !teamDraft.equals(teamSavedIds);
  }

  String teamReturnTarget() {
    if (!teamReturn.isEmpty()) return teamReturn;
    return teamMode.equals("pvp") ? "collection" : "story";
  }

  void requestLeaveTeam(Runnable leave) {
    if (h.busy) return;
    if (!isTeamDirty()) {
      teamDraft = null;
      leave.run();
      return;
    }
    new GameDialog(h)
        .setTitle("편성을 저장할까요?")
        .setMessage("선택한 갈매미와 출전 순서가 아직 저장되지 않았어요.")
        .setPositiveButton("저장하고 나가기", (dialog, which) -> saveTeamDraft(leave))
        .setNeutralButton(
            "변경 취소",
            (dialog, which) -> {
              teamDraft = null;
              leave.run();
            })
        .setNegativeButton("계속 편집", null)
        .show();
  }

  void saveTeamDraft(Runnable completed) {
    if (teamDraft == null || teamDraft.isEmpty()) {
      h.message("1마리 이상 골라 주세요.");
      return;
    }
    ArrayList<String> submitted = new ArrayList<>(teamDraft);
    boolean pvp = teamMode.equals("pvp");
    if (teamDraft.size() > 3 || (pvp && (teamDraft.size() != 3 || new HashSet<>(teamDraft).size() != 3))) {
      h.message(LineupGuidance.shortText(pvp)); return;
    }
    h.request(
        pvp ? "battle-team" : "native/team",
        MainActivity.obj(pvp ? "ids" : "keys", MainActivity.arr(submitted)),
        (result, error) -> {
          if (error != null) return;
          teamSavedIds = submitted;
          teamDraft = null;
          completed.run();
        });
  }

  void team() {
    boolean pvp = h.selectionMode.equals("pvp");
    String mode = pvp ? "pvp" : "story";
    boolean beginning = teamDraft == null || !mode.equals(teamMode);
    if (beginning) {
      String source = h.screen;
      if (!source.equals("team") && !source.equals("unit"))
        teamReturn =
            source.equals("stages")
                    || source.equals("story")
                    || source.equals("modes")
                    || source.equals("modesStory")
                    || source.equals("modesPvp")
                    || source.equals("collection")
                    || source.equals("acquisition")
                    || source.equals("pvp")
                ? source
                : pvp ? "collection" : "story";
      teamMode = mode;
      teamScrollX = 0;
      teamQuery = "";
      teamAttr = "all";
      teamSelectedOnly = false;
      teamSavedIds = new ArrayList<>();
      JSONArray saved = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
      for (int i = 0; i < saved.length(); i++) teamSavedIds.add(saved.optString(i));
      teamDraft = new ArrayList<>(teamSavedIds);
    } else {
      // 저장 응답이 끊긴 뒤 상태를 다시 확인했을 때도 임시 선택은 유지하고 실제 저장본만 갱신한다.
      JSONArray saved = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
      teamSavedIds = new ArrayList<>();
      for (int i = 0; i < saved.length(); i++) teamSavedIds.add(saved.optString(i));
    }
    h.base(pvp ? "방범대" : "스토리 방범대 편성", "team");
    AppSkin.apply(h, "training");
    float teamFontScale = h.getResources().getConfiguration().fontScale;
    // 휴대폰에서는 탭을 머리줄에 합쳐 캐릭터 그림의 세로 공간을 확보한다.
    boolean inlineTeamNavigation = h.compact();
    if (pvp && h.contentHeightDp() >= 360 && !inlineTeamNavigation) {
      defenderTabs(h.body, 1);
      View tabs = h.body.getChildAt(0);
      h.body.removeView(tabs);
      h.page.addView(tabs, h.page.getChildCount() - 1);
    } else if (pvp) {
      LinearLayout header = (LinearLayout) h.page.getChildAt(0);
      int navigationWidth = h.dp(Math.max(76, Math.round(76 * teamFontScale)));
      Button collection = k.action(null, "갈매미", () -> requestLeaveTeam(h::collection));
      collection.setTag("team-open-collection");
      header.addView(collection, 2, new LinearLayout.LayoutParams(navigationWidth, h.dp(48)));
      Button items = k.action(null, "아이템", () -> requestLeaveTeam(h::inventory));
      items.setTag("team-open-items");
      header.addView(items, 3, new LinearLayout.LayoutParams(navigationWidth, h.dp(48)));
    }
    LinearLayout[] cols = k.fixedSplit(.50f);
    LinearLayout podium = k.row(), summary = cols[1];
    podium.setTag("team-podium");
    podium.setGravity(Gravity.TOP);
    podium.setBaselineAligned(false);
    cols[0].addView(podium, new LinearLayout.LayoutParams(-1, -1));
    ArrayList<JSONObject> candidates = new ArrayList<>();
    JSONArray source = h.list(pvp ? h.me() : h.state, "units");
    for (JSONObject unit : NativeOwnedRoster.levelOrderedCandidates(source, pvp ? "id" : "key")) {
      if (!pvp || unit.optBoolean("top")) candidates.add(unit);
    }
    ArrayList<JSONObject> displayedCandidates = new ArrayList<>();
    java.util.HashMap<String, NativeOwnedRosterGroups.Group> displayedGroups = new java.util.HashMap<>();
    if (pvp) displayedCandidates.addAll(candidates); // This legacy response already aggregates species in have.
    else for (NativeOwnedRosterGroups.Group group : NativeOwnedRosterGroups.group(new JSONArray(candidates), "key", "id")) {
      displayedCandidates.add(group.representative());
      displayedGroups.put(group.representative().optString("key"), group);
    }
    TextView status = k.note("");
    status.setTag("team-draft-status");
    status.setPadding(h.dp(4), 0, h.dp(4), 0);
    status.setIncludeFontPadding(false);
    Button[] save = {null};
    final int[] activeSlot = {0};
    final InertialCardScrollView[] candidateRail = {null};
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
          InertialCardScrollView previousRail = candidateRail[0];
          final int preservedCandidateScroll = previousRail == null ? teamScrollX : previousRail.getScrollX();
          // Detached native scroll callbacks cannot replace the captured offset.
          candidateRail[0] = null;
          if (previousRail != null) previousRail.stopMotion();
          podium.removeAllViews();
          summary.removeAllViews();
          teamScrollX = preservedCandidateScroll;
          ArrayList<View> slots = new ArrayList<>();
          ArrayList<String> slotIds = new ArrayList<>();
          ArrayList<JSONObject> picked = new ArrayList<>();
          for (int slot = 0; slot < 3; slot++) {
            final int chosenSlot = slot;
            String id = slot < teamDraft.size() ? teamDraft.get(slot) : "";
            JSONObject member = null;
            for (JSONObject candidate : candidates)
              if (id.equals(candidate.optString(pvp ? "id" : "key"))) member = candidate;
            JSONObject unit = member;
            LinearLayout card = k.column();
            card.setTag("lineup-slot-" + slot);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(PartyCharacterView.selection(h, slot == activeSlot[0]));
            card.setSelected(slot == activeSlot[0]);
            card.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
            TextView order = PartyCharacterView.stateLabel(h,
                (slot == activeSlot[0] ? "✓ " : "") + (slot == 0 ? "선봉" : (slot + 1) + "번") + " ↔");
            order.setTextSize(h.uiText(15));
            card.addView(order, new LinearLayout.LayoutParams(-1, h.dp(48)));
            if (unit == null) {
              TextView empty = k.note("비어 있음");
              empty.setTextColor(JournalTheme.INK);
              empty.setBackgroundColor(0xccf3e9d3);
              empty.setGravity(Gravity.CENTER);
              card.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
              card.setOnClickListener(v -> { activeSlot[0] = Math.min(chosenSlot, teamDraft.size()); render[0].run(); });
              card.setContentDescription((slot == 0 ? "선봉" : (slot + 1) + "번") + " 빈칸. 누르면 갈매미 선택");
            } else {
              picked.add(unit);
              order.setTag(NativeTeamReorder.HANDLE_TAG);
              order.setContentDescription(unit.optString("name") + " 출전 순서. 좌우로 끌어 변경");
              order.setFocusable(true);
              order.setClickable(true);
              View image = PartyCharacterView.art(h, unit);
              image.setTag("lineup-portrait");
              image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
              card.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
              TextView name = PartyCharacterView.nameLabel(h, unit.optString("name"));
              name.setTag("lineup-name");
              card.addView(name);
              String state = pvp ? "보유 " + unit.optInt("have") : "Lv" + unit.optInt("level");
              // 이름·레벨·체력은 그림에 인쇄하지 않고 실제 저장 상태로 표시한다.
              TextView runtime = PartyCharacterView.liveLabel(h, state
                  + (pvp ? "" : " · " + unit.optInt("hp") + "/" + unit.optInt("maxHp")));
              runtime.setTag("lineup-runtime-state");
              runtime.setContentDescription(state
                  + (pvp ? "" : " · 체력 " + unit.optInt("hp") + "/" + unit.optInt("maxHp")));
              card.addView(runtime);
              card.setOnClickListener(v -> { activeSlot[0] = chosenSlot; render[0].run(); });
              card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                  super.onInitializeAccessibilityNodeInfo(host, info);
                  if (!pvp) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                      android.R.id.edit, "대원 상세 · 성장"));
                  if (chosenSlot > 0) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                      android.R.id.button1, "앞 순서로 이동"));
                  if (chosenSlot + 1 < teamDraft.size()) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                      android.R.id.button2, "뒤 순서로 이동"));
                }
                @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                  if (action == android.R.id.button1 || action == android.R.id.button2) {
                    if (!h.busy) {
                      int from = teamDraft.indexOf(id), to = from + (action == android.R.id.button1 ? -1 : 1);
                      if (from >= 0 && to >= 0 && to < teamDraft.size()) {
                        teamDraft.remove(from); teamDraft.add(to, id);
                        activeSlot[0] = to; render[0].run();
                        View moved = podium.findViewWithTag("lineup-slot-" + to);
                        if (moved != null) moved.requestFocus();
                      }
                    }
                    return true;
                  }
                  if (!pvp && action == android.R.id.edit) {
                    if (!h.busy) { unitReturn = "team"; h.unitDetail(id); }
                    return true;
                  }
                  return super.performAccessibilityAction(host, action, args);
                }
              });
              card.setContentDescription((slot == 0 ? "선봉 " : (slot + 1) + "번 ")
                  + PartyCharacterView.description(unit) + " · " + state
                  + (pvp ? "" : " · 체력 " + unit.optInt("hp") + "/" + unit.optInt("maxHp"))
                  + ". 누르면 이 자리의 교체 후보 선택, 좌우로 끌어 순서 변경");
              slots.add(card);
              slotIds.add(id);
            }
            LinearLayout.LayoutParams cardSlot = new LinearLayout.LayoutParams(0, -1, 1);
            cardSlot.setMargins(h.dp(3), 0, h.dp(3), 0);
            podium.addView(card, cardSlot);
          }
          NativeTeamPodiumReorder.bind(
              h,
              podium,
              slots,
              slotIds,
              changed -> {
                teamDraft.clear();
                teamDraft.addAll(changed);
                render[0].run();
              });
          // 손잡이를 눌러도 별도 순서 창을 띄우지 않고 해당 교체 자리만 고른다.
          for (View card : slots) {
            View handle = card.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
            if (handle != null) handle.setOnClickListener(v -> card.performClick());
          }
          LinearLayout paging = k.row();
          paging.setTag("team-candidate-toolbar");
          android.graphics.drawable.GradientDrawable toolbar = new android.graphics.drawable.GradientDrawable();
          toolbar.setColor(JournalTheme.NAVY);
          toolbar.setCornerRadius(h.dp(5));
          toolbar.setStroke(h.dp(1), JournalTheme.BRASS);
          paging.setBackground(toolbar);
          TextView targetLabel = PartyCharacterView.stateLabel(h, (activeSlot[0] + 1) + "번 교체");
          targetLabel.setTag("team-candidate-target");
          targetLabel.setBackgroundColor(Color.TRANSPARENT);
          targetLabel.setTextColor(JournalTheme.WHITE);
          targetLabel.setTextSize(h.uiText(13));
          int targetWidth = Math.max(h.dp(68), (int) Math.ceil(targetLabel.getPaint().measureText(targetLabel.getText().toString())) + h.dp(12));
          paging.addView(targetLabel, new LinearLayout.LayoutParams(targetWidth, h.dp(48)));
          Button previous = k.action(null, "‹", () -> {
            InertialCardScrollView rail = candidateRail[0];
            if (rail != null) rail.smoothScrollCardsBy(-Math.max(h.dp(100), rail.getWidth() * 3 / 4));
          });
          pagerChevron(previous, false);
          previous.setContentDescription("교체 후보 왼쪽으로 이동");
          previous.setTag("team-candidates-previous");
          previous.setEnabled(teamScrollX > 0);
          paging.addView(previous, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
          TextView pageLabel = PartyCharacterView.stateLabel(h, displayedCandidates.size() + "종");
          pageLabel.setBackgroundColor(Color.TRANSPARENT);
          pageLabel.setTextColor(JournalTheme.WHITE);
          pageLabel.setContentDescription("교체 후보 " + displayedCandidates.size() + "종 · 좌우로 밀어 묶음 탐색");
          pageLabel.setTextSize(h.uiText(14));
          pageLabel.setTag("team-candidate-page");
          pageLabel.setGravity(Gravity.CENTER);
          int pageWidth = Math.max(h.dp(48), (int) Math.ceil(pageLabel.getPaint().measureText(pageLabel.getText().toString())) + h.dp(8));
          paging.addView(pageLabel, new LinearLayout.LayoutParams(pageWidth, h.dp(48)));
          Button next = k.action(null, "›", () -> {
            InertialCardScrollView rail = candidateRail[0];
            if (rail != null) rail.smoothScrollCardsBy(Math.max(h.dp(100), rail.getWidth() * 3 / 4));
          });
          pagerChevron(next, true);
          next.setContentDescription("교체 후보 오른쪽으로 이동");
          next.setTag("team-candidates-next");
          next.setEnabled(displayedCandidates.size() > 2);
          paging.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
          Button remove = k.action(null, "비우기", () -> {
            if (activeSlot[0] < teamDraft.size()) teamDraft.remove(activeSlot[0]);
            render[0].run();
          });
          remove.setTag("team-remove");
          remove.setSingleLine(true);
          remove.setEnabled(activeSlot[0] < teamDraft.size());
          // 큰 글자에서도 세 글자 전체와 버튼 안쪽 여백을 실제 폭으로 확보한다.
          int removeWidth = Math.max(h.dp(72),
              (int) Math.ceil(remove.getPaint().measureText(remove.getText().toString()))
                  + remove.getCompoundPaddingLeft() + remove.getCompoundPaddingRight() + h.dp(4));
          paging.addView(remove, new LinearLayout.LayoutParams(removeWidth, h.dp(48)));
          LinearLayout.LayoutParams toolbarSlot = new LinearLayout.LayoutParams(-2, h.dp(48));
          toolbarSlot.gravity = Gravity.RIGHT;
          summary.addView(paging, toolbarSlot);
          LinearLayout strip = k.row();
          strip.setGravity(Gravity.TOP);
          strip.setBaselineAligned(false);
          InertialCardScrollView rail = new InertialCardScrollView(h) {
            int restoreOffset = preservedCandidateScroll, previousWidth;
            boolean restorePending = true;
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
              int width = MeasureSpec.getSize(widthSpec);
              if (previousWidth != width) {
                // Weighted parents may measure several widths before the first layout.
                // Keep the captured offset until it has actually been restored once.
                if (!restorePending && previousWidth != 0) restoreOffset = getScrollX();
                restorePending = true;
                previousWidth = width;
              }
              // Two full cards and the next edge keep the continuous rail discoverable.
              int cardWidth = Math.max(h.dp(100), Math.min(h.dp(176), Math.round(width / 2.6f))) - h.dp(6);
              for (int i = 0; i < strip.getChildCount(); i++) {
                View child = strip.getChildAt(i);
                LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) child.getLayoutParams();
                if (slot.width != cardWidth) { slot.width = cardWidth; child.setLayoutParams(slot); }
              }
              super.onMeasure(widthSpec, heightSpec);
            }
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
              super.onLayout(changed, left, top, right, bottom);
              if (restorePending) {
                scrollTo(restoreOffset, 0);
                restorePending = false;
                teamScrollX = getScrollX();
              }
              previous.setEnabled(canScrollHorizontally(-1));
              next.setEnabled(canScrollHorizontally(1));
            }
            @Override protected void onScrollChanged(int x, int y, int oldx, int oldy) {
              super.onScrollChanged(x, y, oldx, oldy);
              if (candidateRail[0] != this) return;
              if (!restorePending) teamScrollX = x;
              previous.setEnabled(canScrollHorizontally(-1));
              next.setEnabled(canScrollHorizontally(1));
            }
          };
          candidateRail[0] = rail;
          rail.setTag("team-inline-candidates");
          rail.setContentDescription("교체 후보 · 좌우로 밀어 탐색하고 카드를 눌러 배치");
          rail.addView(strip, new HorizontalScrollView.LayoutParams(-2, -1));
          summary.addView(rail, new LinearLayout.LayoutParams(-1, 0, 1));
          for (int index = 0; index < displayedCandidates.size(); index++) {
            LinearLayout tile = k.column();
            tile.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
            tile.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(h.dp(140), -1);
            cell.setMargins(h.dp(3), h.dp(3), h.dp(3), 0);
            strip.addView(tile, cell);
            JSONObject candidate = displayedCandidates.get(index);
            String key = candidate.optString(pvp ? "id" : "key");
            NativeOwnedRosterGroups.Group group = displayedGroups.get(key);
            if (group != null) tile.setTag("team-candidate-group-" + group.key);
            View portrait = PartyCharacterView.art(h, candidate);
            portrait.setTag("team-candidate-character-" + key);
            portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView candidateName = PartyCharacterView.nameLabel(h, candidate.optString("name"));
            candidateName.setTag("team-candidate-name");
            tile.addView(candidateName);
            boolean isSelected = teamDraft.contains(key);
            if (group != null) for (JSONObject member : group.members) isSelected |= teamDraft.contains(member.optString("key"));
            String state = (isSelected ? "✓ " : "")
                + (pvp ? "보유 " + candidate.optInt("have") : group.summary());
            tile.addView(PartyCharacterView.liveLabel(h, state));
            tile.setSelected(isSelected);
            tile.setBackground(PartyCharacterView.selection(h, isSelected));
            tile.setContentDescription(PartyCharacterView.description(candidate) + " · " + state
                + " · " + (activeSlot[0] + 1) + "번에 배치");
            java.util.function.Consumer<JSONObject> place = chosen -> {
              String chosenKey = chosen.optString(pvp ? "id" : "key");
              int target = Math.min(activeSlot[0], teamDraft.size());
              int old = teamDraft.indexOf(chosenKey);
              if (old >= 0 && old != target && target < teamDraft.size()) {
                String swapped = teamDraft.get(target);
                teamDraft.set(target, chosenKey); teamDraft.set(old, swapped);
              } else if (old < 0) {
                if (target < teamDraft.size()) teamDraft.set(target, chosenKey);
                else if (teamDraft.size() < 3) teamDraft.add(chosenKey);
              }
              render[0].run();
            };
            tile.setOnClickListener(v -> {
              if (h.busy || rail.blocksClick()) return;
              if (group == null) place.accept(candidate);
              else Roster60OwnedGroups.openLegacy(h, group, teamDraft, place);
            });
          }
          if (candidates.isEmpty()) summary.addView(k.note("배치할 수 있는 대원이 없어요."));
          status.setText(
              (isTeamDirty() ? "변경됨 · 저장 필요" : "✓ 저장된 편성") + " · " + teamDraft.size() + "/3");
          status.setTextColor(isTeamDirty() ? 0xff8b4d13 : 0xff276646);
          if (save[0] != null) save[0].setEnabled((pvp ? teamDraft.size() == 3 && new HashSet<>(teamDraft).size() == 3 : !teamDraft.isEmpty()) && isTeamDirty());
        };
    TextView drag = k.note(LineupGuidance.shortText(pvp) + " · 기준 보기");
    drag.setMinHeight(h.dp(48));
    drag.setContentDescription(LineupGuidance.shortText(pvp) + ", 상위와 하위 기준 보기");
    drag.setOnClickListener(v -> LineupGuidance.show(h));
    drag.setTextColor(JournalTheme.INK);
    drag.setTag("team-order-drag-hint");
    drag.setPadding(h.dp(4), 0, h.dp(4), 0);
    drag.setIncludeFontPadding(false);
    LinearLayout footer = k.paper(), line = k.row(), footerStatus = k.column();
    footerStatus.addView(status);
    footerStatus.addView(drag);
    line.addView(footerStatus, new LinearLayout.LayoutParams(0, -2, 1));
    Button random =
        k.action(
            null,
            "무작위",
            () -> {
              ArrayList<JSONObject> shuffled = new ArrayList<>(candidates);
              Collections.shuffle(shuffled);
              teamDraft.clear();
              for (int i = 0; i < Math.min(3, shuffled.size()); i++)
                teamDraft.add(shuffled.get(i).optString(pvp ? "id" : "key"));
              render[0].run();
            });
    line.addView(random, new LinearLayout.LayoutParams(h.dp(88), h.dp(48)));
    if (!pvp && h.list(h.me(), "battleTeam").length() > 0) {
      Button importTeam =
          k.action(
              null,
              "불러오기",
              () -> {
                ArrayList<String> imported = new ArrayList<>();
                JSONArray species = h.list(h.me(), "battleTeam");
                for (int i = 0; i < species.length(); i++)
                  for (JSONObject unit : candidates)
                    if (species.optString(i).equals(unit.optString("id"))
                        && !imported.contains(unit.optString("key"))) {
                      imported.add(unit.optString("key"));
                      break;
                    }
                if (imported.isEmpty()) {
                  h.message("가진 갈매미로 된 전투 편성이 없어요.");
                  return;
                }
                teamDraft.clear();
                teamDraft.addAll(imported);
                render[0].run();
              });
      line.addView(importTeam, new LinearLayout.LayoutParams(h.dp(100), h.dp(48)));
    }
    Button cancel =
        k.action(
            null,
            "변경 취소",
            () -> {
              teamDraft.clear();
              teamDraft.addAll(teamSavedIds);
              render[0].run();
            });
    line.addView(cancel, new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    save[0] = k.primary(null, "저장", () -> saveTeamDraft(() -> h.load(teamReturnTarget())));
    save[0].setTag("team-save");
    NativeTutorialController.anchor(save[0], "team-save");
    line.addView(save[0], new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
    footer.addView(line);
    k.footer(footer);
    render[0].run();
  }

  void teamPicker(boolean pvp, ArrayList<JSONObject> candidates, Runnable updated) {
    k.openSheet(
        "갈매미 선택 · 최대 3마리",
        () -> {
          LinearLayout content = k.column(), controls = k.row(), list = k.column();
          if (!pvp && NativeTutorialController.get(h).needsStoryTeam()) {
            TextView guidance = k.notice("갈매미를 눌러 출전시킨 뒤 닫고, 편성을 저장해요.");
            guidance.setTag("team-picker-guidance");
            content.addView(guidance);
          }
          EditText search = k.search(content, "이름·속성 검색", teamQuery, value -> {});
          search.setTag("team-search");
          Button attr = k.action(null, "모든 속성 ▾", () -> {});
          attr.setTag("team-attr-filter");
          Button selected = k.action(null, "선택만", () -> {});
          selected.setTag("team-selected-filter");
          h.cell(controls, attr);
          h.cell(controls, selected);
          content.addView(controls);
          list.setTag("team-candidates-scroll");
          content.addView(list);
          Runnable[] paint = new Runnable[1];
          paint[0] =
              () -> {
                list.removeAllViews();
                ArrayList<View> cards = new ArrayList<>();
                for (JSONObject unit : candidates) {
                  String id = unit.optString(pvp ? "id" : "key");
                  int order = teamDraft.indexOf(id);
                  if (!candidateMatches(unit, teamQuery, teamAttr, teamSelectedOnly && order < 0))
                    continue;
                  LinearLayout tile = k.paper();
                  tile.setGravity(Gravity.CENTER_HORIZONTAL);
                  if (unit.has("tier"))
                    tile.setBackground(new GamePaperDrawable(h, NativeReveal.tier(unit.optString("tier"))));
                  tile.addView(
                      PartyCharacterView.art(h, unit),
                      new LinearLayout.LayoutParams(-1, h.dp(100)));
                  TextView name = heading(unit.optString("name"), 15);
                  name.setGravity(Gravity.CENTER);
                  name.setMaxLines(2);
                  name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                  name.setContentDescription(unit.optString("name"));
                  tile.addView(name);
                  String label =
                      (order < 0 ? "" : "✓ " + (order == 0 ? "선봉 · " : (order + 1) + "번 · "))
                          + (pvp ? TierDisplay.name(unit.optString("tier"), unit.optString("tierName")) : "Lv" + unit.optInt("level"));
                  TextView meta = k.note(label);
                  meta.setGravity(Gravity.CENTER);
                  tile.addView(meta);
                  tile.setContentDescription(unit.optString("name") + " · " + label);
                  tile.setOnClickListener(
                      v -> {
                        if (h.busy) return;
                        if (teamDraft.contains(id)) teamDraft.remove(id);
                        else if (teamDraft.size() < 3) teamDraft.add(id);
                        else {
                          h.message("3마리까지 고를 수 있어요.");
                          return;
                        }
                        updated.run();
                        paint[0].run();
                      });
                  tile.setTag("lineup-candidate-" + id);
                  tile.setSelected(order >= 0);
                  tile.setBackground(PartyCharacterView.selection(h, order >= 0));
                  if (!pvp && unit.optString("id").equals("D1"))
                    NativeTutorialController.anchor(tile, "team-unit");
                  if (!pvp)
                    tile.setOnLongClickListener(
                        v -> {
                          unitReturn = "team";
                          h.unitDetail(id);
                          return true;
                        });
                  cards.add(tile);
                }
                if (cards.isEmpty()) list.addView(k.notice("조건에 맞는 갈매미가 없어요. 검색어와 필터를 바꿔 보세요."));
                else k.tiles(list, cards, h.compact() ? 3 : 4);
                attr.setText(
                    teamAttr.equals("all")
                        ? "모든 속성 ▾"
                        : attrName(h.state.optJSONObject("attributes"), teamAttr) + " ▾");
                selected.setSelected(teamSelectedOnly);
              };
          search.addTextChangedListener(
              new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                  teamQuery = s.toString();
                  paint[0].run();
                }

                public void afterTextChanged(android.text.Editable s) {}
              });
          attr.setOnClickListener(
              v -> {
                ArrayList<String> attrs = new ArrayList<>();
                attrs.add("all");
                JSONObject definitions = h.state.optJSONObject("attributes");
                if (definitions != null) {
                  Iterator<String> names = definitions.keys();
                  while (names.hasNext()) attrs.add(names.next());
                }
                String[] labels = new String[attrs.size()];
                for (int i = 0; i < labels.length; i++)
                  labels[i] = i == 0 ? "모든 속성" : attrName(definitions, attrs.get(i));
                new GameDialog(h)
                    .setTitle("속성으로 찾기")
                    .setItems(
                        labels,
                        (d, which) -> {
                          teamAttr = attrs.get(which);
                          paint[0].run();
                        })
                    .setNegativeButton("닫기", null)
                    .show();
              });
          selected.setOnClickListener(
              v -> {
                teamSelectedOnly = !teamSelectedOnly;
                paint[0].run();
              });
          paint[0].run();
          return content;
        });
  }

  static boolean candidateMatches(
      JSONObject unit, String query, String attr, boolean excludedBySelection) {
    if (excludedBySelection) return false;
    String code = unit.optString("attrCode", unit.optString("attr"));
    if (!attr.equals("all") && !attr.equals(code)) return false;
    String normalized = query.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "");
    String searchable =
        (unit.optString("name") + unit.optString("attrName") + unit.optString("attr") + code)
            .toLowerCase(Locale.KOREAN)
            .replaceAll("\\s+", "");
    return searchable.contains(normalized);
  }

  void chart(LinearLayout parent, Collection<String> team, boolean species) {
    JSONObject types = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    if (types == null || attrs == null) return;
    LinkedHashSet<String> mine = new LinkedHashSet<>();
    ArrayList<JSONObject> picked = new ArrayList<>();
    for (String id : team) {
      JSONObject unit = species ? ownedSpecies(id) : h.unit(id);
      if (unit.length() == 0) continue;
      mine.add(unit.optString("attrCode", unit.optString("attr")));
      picked.add(unit);
    }
    ArrayList<String> ordered = new ArrayList<>(mine);
    Iterator<String> all = types.keys();
    while (all.hasNext()) {
      String attr = all.next();
      if (!ordered.contains(attr)) ordered.add(attr);
    }
    if (ordered.isEmpty()) return;
    String[] selected = {ordered.get(0)};
    LinearLayout controls = k.row(), current = k.column();
    parent.addView(controls);
    parent.addView(current);
    Button select = k.action(null, "속성 선택", () -> {});
    h.cell(controls, select);
    h.cell(
        controls,
        k.action(
            null,
            "전체 상성",
            () ->
                k.openSheet(
                    "전체 속성 상성",
                    () -> {
                      LinearLayout entries = k.column();
                      for (String attr : ordered) entries.addView(affinityCard(attr, mine, true));
                      return entries;
                    })));
    Runnable paint =
        () -> {
          current.removeAllViews();
          select.setText(attrName(attrs, selected[0]) + " ▾");
          current.addView(affinityCard(selected[0], mine, false));
        };
    select.setOnClickListener(
        v -> {
          String[] labels = new String[ordered.size()];
          for (int i = 0; i < labels.length; i++)
            labels[i] =
                attrName(attrs, ordered.get(i)) + (mine.contains(ordered.get(i)) ? " · 우리 팀" : "");
          new GameDialog(h)
              .setTitle("공격 속성 선택")
              .setItems(
                  labels,
                  (d, which) -> {
                    selected[0] = ordered.get(which);
                    paint.run();
                  })
              .setNegativeButton("닫기", null)
              .show();
        });
    paint.run();
    parent.addView(k.notice("공격 속성 → 방어 속성 · 유리 ×1.5 / 불리 ×0.67"));
    if (!mine.isEmpty()) {
      parent.addView(k.notice("팀 속성 · " + attrNames(attrs, mine)));
      Button summary =
          k.action(
              null,
              "우리 팀 상성 요약",
              () ->
                  k.openSheet(
                      "우리 팀 상성",
                      () -> {
                        LinearLayout detail = k.column();
                        for (JSONObject unit : picked) {
                          LinearLayout who = k.row();
                          who.addView(
                              k.note(unit.optString("name")),
                              new LinearLayout.LayoutParams(0, -2, 1));
                          String attr = unit.optString("attrCode", unit.optString("attr"));
                          who.addView(k.chip(attrName(attrs, attr), attr));
                          detail.addView(who);
                        }
                        LinkedHashSet<String> good = new LinkedHashSet<>(),
                            danger = new LinkedHashSet<>();
                        for (String attr : mine) {
                          JSONArray strong = h.list(types.optJSONObject(attr), "strong");
                          for (int i = 0; i < strong.length(); i++) good.add(strong.optString(i));
                          Iterator<String> attackers = types.keys();
                          while (attackers.hasNext()) {
                            String attacker = attackers.next();
                            JSONArray targets = h.list(types.optJSONObject(attacker), "strong");
                            for (int i = 0; i < targets.length(); i++)
                              if (attr.equals(targets.optString(i))) danger.add(attacker);
                          }
                        }
                        danger.removeAll(good);
                        detail.addView(k.note("유리한 상대 · " + attrNames(attrs, good)));
                        detail.addView(k.note("보완할 상대 · " + attrNames(attrs, danger)));
                        return detail;
                      }));
      summary.setTag("type-team-summary-toggle");
      parent.addView(summary);
    }
  }

  LinearLayout affinityCard(String attr, Collection<String> mine, boolean reasons) {
    JSONObject types = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    JSONObject type = types.optJSONObject(attr);
    ArrayList<String> strong = new ArrayList<>(), weak = new ArrayList<>();
    JSONArray targets = h.list(type, "strong"), explanations = h.list(type, "why");
    for (int i = 0; i < targets.length(); i++) strong.add(targets.optString(i));
    Iterator<String> others = types.keys();
    while (others.hasNext()) {
      String other = others.next();
      JSONArray attacks = h.list(types.optJSONObject(other), "strong");
      for (int i = 0; i < attacks.length(); i++)
        if (attr.equals(attacks.optString(i))) weak.add(other);
    }
    LinearLayout card = k.paper(), head = k.row();
    card.setTag("type-chart-" + attr);
    head.addView(k.chip(attrName(attrs, attr), attr));
    if (mine.contains(attr)) head.addView(h.text("  우리 팀", 14));
    card.addView(head);
    TextView good = k.note("유리 ×1.5  " + attrNames(attrs, strong));
    good.setTextColor(0xff276646);
    card.addView(good);
    TextView bad = k.note("불리 ×0.67  " + attrNames(attrs, weak));
    bad.setTextColor(0xff9b3c31);
    card.addView(bad);
    if (reasons) {
      for (int i = 0; i < targets.length(); i++)
        if (!explanations.optString(i).isEmpty())
          card.addView(
              k.note(attrName(attrs, targets.optString(i)) + " · " + explanations.optString(i)));
    } else {
      card.setContentDescription(
          attrName(attrs, attr) + " · " + good.getText() + " · " + bad.getText() + ". 누르면 상성 이유");
      card.setOnClickListener(
          v -> k.openSheet(attrName(attrs, attr) + " 상성", () -> affinityCard(attr, mine, true)));
    }
    return card;
  }

  String attrNames(JSONObject attrs, Collection<String> ids) {
    if (ids.isEmpty()) return "없음";
    ArrayList<String> names = new ArrayList<>();
    for (String id : ids) names.add(attrName(attrs, id));
    return android.text.TextUtils.join(" · ", names);
  }

  String attrName(JSONObject attrs, String id) {
    JSONObject a = attrs == null ? null : attrs.optJSONObject(id);
    return a == null ? id : a.optString("name", id);
  }
}



