package org.dpdns.gmm;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Fixed native equipment desk: direct effects, selection and authoritative deadline. */
// augment-no-scroll-v1
final class NativeAugmentView extends LinearLayout {
  final BattleView battleView;
  final MainActivity h;
  final MenuKit k;
  final JSONArray offers;
  final FreestyleSelection freestyle;
  final boolean attributePhase;
  final String selectionOwner;
  final int selectionEpoch;
  Button attributePicker;
  final ArrayList<Button> attributeButtons = new ArrayList<>();
  final boolean large;
  final LinearLayout cards, footer;
  final Button confirm;
  static final int CARD_MAX_DP = 360;
  final ArrayList<View> picks = new ArrayList<>();
  final ArrayList<Button> rerolls = new ArrayList<>();
  final ArrayList<TextView> names = new ArrayList<>();
  final TextView title, compare, hint, timerRule;
  final long deadline, clockOffset;
  boolean refreshing;
  final long bonusReceivedAt = android.os.SystemClock.elapsedRealtime();
  final Runnable tick =
      new Runnable() {
        public void run() {
          if (!isAttachedToWindow() || deadline <= 0) return;
          long remaining = Math.max(0, deadline - (System.currentTimeMillis() + clockOffset));
          boolean localInput = canChoose();
          title.setText(
              (attributePhase ? "자유자재 / 속성 선택" : switching
                      ? "② 다음 출전"
                      : battleView.side(0).optBoolean("needSwitch") ? "① 증강체" : "전투 증강체")
                  + " / "
                  + (remaining <= 0 ? "결과 확인 중"
                      : NativeRealtime.isBattle(battleView.battle) && !localInput
                          ? h.realtime.writing ? "선택 전송 중" : "상대 선택 대기"
                          : ((remaining + 999) / 1000) + "초"));
          for (View pick : picks) pick.setEnabled(localInput);
          for (int i = 0; i < rerolls.size(); i++) {
            JSONObject offer = offers.optJSONObject(i);
            rerolls.get(i).setEnabled(localInput && canReroll(offer));
            rerolls.get(i).setText(rerollLabel(offer));
          }
          confirm.setEnabled(localInput && (freestyle != null
              ? freestyle.allows(freestyle.selected) : switching ? switchTo >= 0 : !selected.isEmpty()));
          if (remaining <= 0) {
            confirm.setEnabled(false);
            confirm.setText("결과 확인 중");
            for (View pick : picks) pick.setEnabled(false);
            for (Button reroll : rerolls) reroll.setEnabled(false);
          }
          boolean urgent = remaining <= 10000;
          title.setTextColor(urgent ? 0xffffd2bc : JournalTheme.WHITE);
          timerRule.setTextColor(urgent ? NativeBattleLog.DAMAGE : JournalTheme.MUTED);
          timerRule.setTypeface(h.prose, urgent ? Typeface.BOLD : Typeface.NORMAL);
          if (remaining <= 0 && NativeRealtime.isBattle(battleView.battle)) {
            h.realtime.refresh();
            postDelayed(this, 1000);
            return;
          }
          if (remaining <= 0 && !h.busy && !refreshing) {
            refreshing = true;
            int epoch = h.epoch;
            h.api.call(
                "native/battle/current",
                null,
                (r, e) -> {
                  refreshing = false;
                  if (!isAttachedToWindow() || h.epoch != epoch) return;
                  if (e == null && r.has("session") && !r.isNull("session")) {
                    h.battle = r;
                    h.battleScreen();
                  } else postDelayed(this, 1500);
                });
          } else postDelayed(this, 250);
        }
      };
  String selected;
  int switchTo = -1;
  boolean switching;

  NativeAugmentView(BattleView view) {
    super(view.host);
    battleView = view;
    h = view.host;
    selectionOwner = NativeBattleReplay.account(h);
    selectionEpoch = h.epoch;
    attributePhase = "own-attribute".equals(view.battle.optString("choiceKind"));
    JSONObject attributeScope = view.side(0).optJSONObject("attributeSelection");
    freestyle = attributeScope == null ? null : new FreestyleSelection(attributeScope);
    deadline = view.battle.optLong("chooseDeadline", 0);
    clockOffset =
        view.battle.optLong("serverNow", System.currentTimeMillis()) - System.currentTimeMillis();
    k = new MenuKit(h);
    offers = h.list(view.side(0), "offers");
    large =
        Math.min(
                h.getResources().getConfiguration().screenHeightDp,
                h.root.getHeight() > 0
                    ? (h.root.getHeight() - h.root.getPaddingTop() - h.root.getPaddingBottom())
                        / h.getResources().getDisplayMetrics().density
                    : Integer.MAX_VALUE)
            >= 600;
    selected = h.pendingAugment;
    boolean valid = false;
    for (int i = 0; i < offers.length(); i++)
      valid |= offers.optJSONObject(i).optString("id").equals(selected);
    if (!valid) selected = "";
    setOrientation(VERTICAL);
    setTag("theme-augment-desk");
    // This view covers the battle while choosing. The decoded scene is opaque,
    // and ThemeArt paints navy until it is ready, so no secret battle UI leaks through.
    setBackground(ThemeArt.of(h).background(AppSkin.background("workshop")));
    setPadding(
        h.dp(large ? 44 : 20) + h.root.getPaddingLeft(),
        h.dp(8) + h.root.getPaddingTop(),
        h.dp(large ? 44 : 20) + h.root.getPaddingRight(),
        h.dp(10) + h.root.getPaddingBottom());
    title = h.text("증강체 선택", large ? 24 : 19);
    title.setTextSize(large ? 24 : 19);
    title.setPadding(h.dp(16), h.dp(7), h.dp(16), h.dp(7));
    title.setIncludeFontPadding(false);
    title.setTypeface(h.headline);
    title.setGravity(Gravity.CENTER);
    title.setTextColor(JournalTheme.WHITE);
    title.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    addView(title, new LayoutParams(-1, -2));
    timerRule = h.text("30초 안에 확정하지 않으면 선택지 중 무작위로 결정돼요.", large ? 17 : 14);
    timerRule.setTypeface(h.prose);
    timerRule.setTextColor(JournalTheme.MUTED);
    timerRule.setGravity(Gravity.CENTER);
    timerRule.setPadding(0, 0, 0, h.dp(3));
    // Keep deadline logic without a visible explanation panel.
    LinearLayout versus = k.row();
    versus.setBackground(new GamePaperDrawable(h));
    for (int side = 0; side < 3; side++) {
      if (side == 1) continue;
      JSONObject fighter = view.active(side == 0 ? 0 : 1);
      LinearLayout who = k.row();
      who.setGravity(Gravity.CENTER);
      TextView name = h.text(fighter.optString("name"), large ? 17 : 13);
      name.setTextColor(JournalTheme.INK);
      who.addView(name);
      who.addView(k.chip(fighter.optString("attr"), fighter.optString("attrCode")));
      versus.addView(who, new LayoutParams(0, -1, 1));
    }
    compare =
        k.note(
            "내 공격 "
                + mult(view.active(0), view.active(1))
                + " · 상대 공격 "
                + mult(view.active(1), view.active(0)));
    compare.setTextSize(large ? 17 : 13);
    compare.setGravity(Gravity.CENTER);
    compare.setPadding(0, 0, 0, 0);
    compare.setTextColor(JournalTheme.INK);
    android.text.SpannableString comparison = new android.text.SpannableString(compare.getText());
    java.util.regex.Matcher match =
        java.util.regex.Pattern.compile("×[0-9.]+ (유리|불리)").matcher(comparison);
    while (match.find()) {
      comparison.setSpan(
          new android.text.style.ForegroundColorSpan(
              match.group(1).equals("유리") ? NativeBattleLog.HEAL : NativeBattleLog.DAMAGE),
          match.start(),
          match.end(),
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    compare.setText(comparison);
    versus.addView(compare, 1, new LayoutParams(0, -1, 1.4f));
    // Matchup panel omitted on the selection screen.
    String help = view.battle.optInt("turn") == 1 ? view.battle.optString("hint") : "";
    hint = k.notice(help);
    hint.setTextColor(JournalTheme.TEAL);
    hint.setTextSize(large ? 17 : 13);
    hint.setBackground(new GamePaperDrawable(h, JournalTheme.TEAL));
    hint.setMaxLines(large ? 2 : 1);
    hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
    hint.setContentDescription(help + " · 터치하여 전체 안내 보기");
    hint.setOnClickListener(
        v ->
            new GameDialog(h)
                .setTitle("전투 안내")
                .setMessage(help)
                .setPositiveButton("닫기", null)
                .show());
    // Long guidance omitted on the selection screen.
    cards = k.row();
    cards.setGravity(Gravity.TOP);
    // 큰 화면에서 카드가 세로로 끝없이 늘어나 글자만 작게 떠 보이지 않도록 높이를 제한한다.
    // 큰 화면은 제목·카드·확정 버튼을 한 묶음으로 세로 가운데에 모아 버튼이 카드와 멀리 떨어지지 않게 한다.
    if (large) setGravity(Gravity.CENTER_VERTICAL);
    LayoutParams cp = large ? new LayoutParams(-1, h.dp(CARD_MAX_DP)) : new LayoutParams(-1, 0, 1);
    cp.topMargin = h.dp(8);
    cp.bottomMargin = h.dp(6);
    addView(cards, cp);
    confirm = h.button("증강체 선택", this::submit);
    PhaseButtonStyle.apply(h, confirm, true);
    confirm.setTag("augment-confirm");
    confirm.setTextSize(large ? 18 : 16);
    confirm.setMinHeight(h.dp(48));
    confirm.setMinimumHeight(h.dp(48));
    confirm.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    footer = k.row();
    LayoutParams foot = new LayoutParams(-1, h.dp(48));
    addView(footer, foot);
    footer.setGravity(Gravity.CENTER);
    footer.addView(confirm, new LayoutParams(h.dp(large ? 360 : 300), -1));
    if (freestyle != null) showAttributes();
    else if (attributePhase) showAttributeWaiting();
    else if (offers.length() == 0) showSwitch();
    else showOffers();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    post(tick);
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(tick);
    super.onDetachedFromWindow();
  }

  static String coreEffect(JSONObject augment) {
    // V2의 횟수·기간·대상은 카탈로그 설명이 기준이다. V1 템플릿을 재사용하지 않는다.
    if (augment.optInt("version") == 2)
      return augment.optString("desc", "효과 설명을 확인할 수 없어요.");
    String template;
    switch (augment.optString("id")) {
      case "reverse": template = "이번 전투: 내 공격 상성\n강함 ↔ 약함"; break;
      case "weakguard": template = "받는 강한 상성 피해\n→ 보통 피해"; break;
      case "focus": template = "강한 상성 공격 배율\n×{value}"; break;
      case "neutral": template = "{turns}턴 동안\n양쪽 모든 상성 ×1.0"; break;
      case "camo": template = "내 선두 속성 변경\n→ 상대에게 강한 속성"; break;
      case "dual": template = "기본기·역할 공격에\n상대 약점 속성 추가"; break;
      case "counter": template = "약한 상성 공격을 맞고 생존 시\n위력 {power} 반격"; break;
      case "learn": template = "동일 속성 피격마다\n해당 피해 -{step}% · 최대 -{max}%"; break;
      case "first": template = "다음 기술에\n선공 우선권"; break;
      case "bulwark": template = "내 선두 받는 피해\n-{pct}% · {turns}턴"; break;
      case "firstaid": template = "쓰러지지 않은 아군 모두\n체력 {pct}% 회복"; break;
      case "freeswitch": template = "이번 전투:\n교체 시 턴 소모 없음"; break;
      case "lastone": template = "아군이 하나만 남으면\n공격·방어·속도 +{pct}%"; break;
      case "strip": template = "상대 선두 강화 능력치·보호막 제거\n공격 -{pct}% · {turns}턴"; break;
      case "rustle": template = "상대 선두 수면 1턴\n상태 이상 면역 제외"; break;
      case "peanut": template = "아군 갈매미맨 공격 +{atk}%\n대신 받는 피해 +{taken}%"; break;
      case "sharp": template = "아군 급소 확률\n+{pct}%"; break;
      case "swift": template = "아군 속도\n+{pct}%"; break;
      case "thick": template = "아군 물리·마법 방어\n+{pct}%"; break;
      case "overtime": template = "아군 물리·마법 공격\n+{pct}%"; break;
      case "drain": template = "내가 준 피해의\n{pct}%만큼 체력 회복"; break;
      case "thorns": template = "나를 때린 상대에게\n받은 피해의 {pct}% 반사"; break;
      case "pierce": template = "내 공격 시\n상대 방어 {pct}% 무시"; break;
      case "lunch": template = "매 턴 종료 시\n내 선두 체력 {pct}% 회복"; break;
      default: return augment.optString("desc");
    }
    JSONObject parameters = augment.optJSONObject("p");
    java.util.regex.Matcher fields =
        java.util.regex.Pattern.compile("\\{([a-z]+)\\}").matcher(template);
    StringBuffer result = new StringBuffer();
    while (fields.find()) {
      String key = fields.group(1);
      if (parameters == null || !parameters.has(key) || parameters.isNull(key))
        return augment.optString("desc");
      fields.appendReplacement(result,
          java.util.regex.Matcher.quoteReplacement(parameters.optString(key)));
    }
    fields.appendTail(result);
    return result.toString();
  }

  String mult(JSONObject from, JSONObject to) {
    JSONObject matchup = from.optJSONObject("matchup");
    JSONObject attack = matchup == null ? null : matchup.optJSONObject("attack");
    if (attack != null) {
      double multiplier = attack.optDouble("multiplier", 1);
      return "×"
          + java.math.BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString()
          + " "
          + NativeBattleSheets.relation(multiplier);
    }
    JSONObject chart = h.state.optJSONObject("typeChart");
    if (chart == null) return "×1 보통";
    String a = from.optString("attrCode"), b = to.optString("attrCode");
    JSONArray strong = h.list(chart.optJSONObject(a), "strong"),
        weak = h.list(chart.optJSONObject(b), "strong");
    for (int i = 0; i < strong.length(); i++) if (b.equals(strong.optString(i))) return "×1.5 유리";
    for (int i = 0; i < weak.length(); i++) if (a.equals(weak.optString(i))) return "×0.67 불리";
    return "×1 보통";
  }

  static int kind(String id) {
    if (Arrays.asList(
            "focus", "dual", "first", "lastone", "peanut", "sharp", "overtime", "drain", "pierce")
        .contains(id)) return 0;
    if (Arrays.asList(
            "weakguard", "counter", "learn", "bulwark", "firstaid", "thick", "thorns", "lunch")
        .contains(id)) return 2;
    return 1;
  }

  static int color(int kind) {
    return kind == 0 ? 0xffb94339 : kind == 2 ? 0xff326eaa : 0xff927017;
  }

  static String kindName(int kind) {
    return kind == 0 ? "공격" : kind == 2 ? "수비" : "유틸";
  }

  // 희귀도와 공격·수비·유틸 역할은 별개다. 등급은 V2에서만 표시한다.
  static String tierName(JSONObject augment) {
    return AugmentPresentation.tier(augment);
  }

  static int cardColor(JSONObject augment) {
    return augment.optInt("version") == 2 ? AugmentPresentation.ink(augment)
        : color(kind(augment.optString("id")));
  }

  static String cardRole(JSONObject augment) {
    return augment.optInt("version") == 2 ? "증강" : kindName(kind(augment.optString("id")));
  }

  static String cardLabel(JSONObject augment) {
    String tier = tierName(augment);
    return (tier.isEmpty() ? "" : tier + " / ") + cardRole(augment);
  }

  LinearLayout columnCard(int tint) {
    LinearLayout column = k.column();
    LayoutParams col = new LayoutParams(0, -1, 1);
    col.setMargins(h.dp(4), 0, h.dp(4), 0);
    cards.addView(column, col);
    FrameLayout surface = new FrameLayout(h);
    surface.setBackground(new CardFrame(tint, false));
    column.addView(surface, new LayoutParams(-1, 0, 1));
    LinearLayout content = k.column();
    content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    content.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(8));
    surface.addView(content, new FrameLayout.LayoutParams(-1, -1));
    picks.add(content);
    content.setTag(surface);
    return content;
  }

  void showOffers() {
    View back = findViewWithTag("augment-back");
    if (back != null) ((ViewGroup) back.getParent()).removeView(back);
    switching = false;
    updateTimerRule();
    cards.removeAllViews();
    picks.clear();
    rerolls.clear();
    names.clear();
    title.setText(
        battleView.side(0).optBoolean("needSwitch") ? "① 증강체 · ② 다음 출전" : "증강체 선택");
    boolean disadvantaged =
        mult(battleView.active(0), battleView.active(1)).contains("불리")
            || mult(battleView.active(1), battleView.active(0)).contains("유리");
    for (int i = 0; i < offers.length(); i++) {
      int ix = i;
      JSONObject aug = offers.optJSONObject(i);
      String id = aug.optString("id");
      LinearLayout content = columnCard(cardColor(aug));
      ImageView icon =
          k.image(
              "/duel/icons/"
                  + (JournalIcons.supports(id) ? id : aug.optInt("version") == 2 ? "u-gift" : aug.optString("icon", "u-gift"))
                  + ".png",
              large ? 76 : 56,
              large ? 76 : 56);
      icon.setTag("augment-icon-" + id);
      android.graphics.drawable.GradientDrawable ring =
          new android.graphics.drawable.GradientDrawable();
      ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
      ring.setColor(JournalTheme.PAPER);
      ring.setStroke(h.dp(2), JournalTheme.BRASS);
      icon.setBackground(ring);
      icon.setPadding(
          h.dp(large ? 6 : 3), h.dp(large ? 6 : 3), h.dp(large ? 6 : 3), h.dp(large ? 6 : 3));
      LinearLayout heading = k.row();
      heading.setGravity(Gravity.CENTER_VERTICAL);
      heading.addView(icon);
      TextView name = h.text(aug.optString("name"), large ? 23 : 18);
      name.setTextSize(large ? 23 : 18);
      name.setMaxLines(2);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setTypeface(h.headline);
      name.setTextColor(JournalTheme.WHITE);
      name.setGravity(Gravity.CENTER);
      name.setPadding(h.dp(3), 0, 0, 0);
      name.setIncludeFontPadding(true);
      heading.addView(name, new LayoutParams(0, -2, 1));
      content.addView(heading, new LayoutParams(-1, -2));
      names.add(name);
      boolean recommend =
          aug.optInt("version") != 2 && disadvantaged
              && Arrays.asList("camo", "reverse", "weakguard", "neutral", "dual", "counter")
                  .contains(id);
      TextView badge = h.text(cardLabel(aug) + (recommend ? " · 추천" : "") + " / 길게 눌러 상세", large ? 15 : 12);
      badge.setTextColor(cardColor(aug));
      badge.setBackground(new GamePaperDrawable(h));
      badge.setPadding(h.dp(10), h.dp(2), h.dp(10), h.dp(2));
      content.addView(badge, new LayoutParams(-2, -2));
      TextView desc = k.note(coreEffect(aug));
      desc.setTextSize(large ? 19 : 16);
      desc.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
      desc.setIncludeFontPadding(false);
      desc.setTextColor(JournalTheme.MUTED);
      desc.setGravity(Gravity.CENTER);
      desc.setTextSize(large ? 18 : 15);
      desc.setTextColor(JournalTheme.INK);
      desc.setMaxLines(large ? 7 : 4);
      desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
      desc.setGravity(Gravity.CENTER);
      desc.setTag("augment-core-effect");
      // V2 카탈로그 설명에는 줄바꿈 표시가 없으므로 낱말 중간(「흡수쉴드/로」)에서 끊지 않고 띄어쓰기에서만 바꾼다.
      if (aug.optInt("version") == 2) NativeEconomy.wrapAtSpaces(desc, coreEffect(aug));
      desc.setBackground(new GamePaperDrawable(h));
      LayoutParams effectSlot = new LayoutParams(-1, 0, 1);
      effectSlot.topMargin = h.dp(5);
      content.addView(desc, effectSlot);
      // Long catalog text remains complete in a modal; do not shrink the fixed desk.
      content.setContentDescription(
          aug.optString("name") + " · " + cardLabel(aug) + " · " + aug.optString("desc")
              + " · 길게 눌러 전체 효과 보기");
      content.setOnLongClickListener(v -> { showDetail(aug); return true; });

      content.setOnClickListener(
          v -> {
            if (!canChoose()) return;
            h.sound.click();
            selected = id;
            h.pendingAugment = id;
            updateSelection();
          });
      LinearLayout column = (LinearLayout) ((View) content.getTag()).getParent();
      Button reroll =
          k.action(
              null,
              rerollLabel(aug),
              () -> {
                if (!canChoose() || !canReroll(aug)) return;
                if (id.equals(selected)) {
                  selected = "";
                  h.pendingAugment = "";
                }
                h.battleTurnFrom(battleView.battle, MainActivity.obj("reroll", ix));
              });
      // 종이 버튼 표면을 유지해 누를 수 있는 버튼으로 보이게 하고, 재추첨 뒤에는 비활성 표면으로 구분한다.
      PhaseButtonStyle.apply(h, reroll, false);
      reroll.setTag("augment-reroll-" + ix);
      reroll.setTextSize(large ? 15 : 14);
      reroll.setPadding(h.dp(4), 0, h.dp(4), h.dp(2));
      reroll.setMinHeight(h.dp(48));
      reroll.setMinimumHeight(h.dp(48));
      reroll.setEnabled(canChoose() && canReroll(aug));
      LayoutParams rp = new LayoutParams(-1, h.dp(48));
      rp.topMargin = h.dp(4);
      LinearLayout actions = k.row();
      actions.setGravity(Gravity.CENTER);
      actions.addView(reroll, new LayoutParams(h.dp(136), -1));

      column.addView(actions, rp);
      rerolls.add(reroll);
    }
    updateSelection();
  }

  void showAttributeWaiting() {
    cards.removeAllViews(); picks.clear(); rerolls.clear(); names.clear();
    selected = "";
    TextView waiting = h.text("상대가 자유자재 속성을 선택하고 있어요.", large ? 22 : 18);
    waiting.setGravity(Gravity.CENTER); waiting.setTextColor(JournalTheme.INK);
    waiting.setBackground(new GamePaperDrawable(h));
    cards.addView(waiting, new LayoutParams(-1, -1));
    confirm.setText("상대 선택 대기"); confirm.setEnabled(false);
  }

  String attributeName(String code) {
    return h.menus.attrName(h.state.optJSONObject("attributes"), code);
  }

  void showAttributes() {
    switching = false;
    selected = "";
    title.setText("자유자재 / 속성 선택");
    cards.removeAllViews(); picks.clear(); rerolls.clear(); names.clear();
    LinearLayout panel = k.column();
    panel.setGravity(Gravity.CENTER);
    panel.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
    panel.setBackground(new GamePaperDrawable(h, 0xff735191));
    cards.addView(panel, new LayoutParams(-1, -1));
    float density = h.getResources().getDisplayMetrics().density;
    float width = h.root.getWidth() > 0 ? h.root.getWidth() / density
        : h.getResources().getConfiguration().screenWidthDp;
    float height = h.root.getHeight() > 0 ? h.root.getHeight() / density
        : h.getResources().getConfiguration().screenHeightDp;
    boolean compact = FreestyleLayout.compact(
        width - (getPaddingLeft() + getPaddingRight()) / density,
        height - (getPaddingTop() + getPaddingBottom()) / density,
        h.getResources().getConfiguration().fontScale);
    String opponent = attributeName(freestyle.scope.optString("opponentAttribute"));
    String own = battleView.active(0).optString("name");
    String matchupText = "상대 속성: " + opponent + " / 내 선두: " + own;
    TextView matchup = h.text(matchupText, large ? 21 : 17);
    matchup.setTextColor(JournalTheme.INK); matchup.setGravity(Gravity.CENTER);
    matchup.setMaxLines(compact ? 2 : 3);
    matchup.setEllipsize(android.text.TextUtils.TruncateAt.END);
    matchup.setContentDescription(matchupText + " / 길게 눌러 효과 안내");
    matchup.setOnLongClickListener(v -> { showFreestyleRules(); return true; });
    panel.addView(matchup, new LayoutParams(-1, -2));
    if (!compact) {
      TextView rule = h.text("이번 전투 동안 유지 / 다른 속성 기술에도 상성 적용\n속성을 누른 뒤 아래에서 확정해 주세요.", large ? 17 : 14);
      rule.setTextColor(JournalTheme.INK); rule.setGravity(Gravity.CENTER);
      panel.addView(rule, new LayoutParams(-1, -2));
    }
    JSONArray attributes = freestyle.scope.optJSONArray("attributes");
    int count = attributes == null ? 0 : Math.min(10, attributes.length());
    if (compact) {
      attributePicker = h.button("속성 고르기", this::openAttributePicker);
      attributePicker.setTextSize(large ? 18 : 15);
      attributePicker.setMinHeight(h.dp(48)); attributePicker.setMinimumHeight(h.dp(48));
      PhaseButtonStyle.apply(h, attributePicker, false);
      panel.addView(attributePicker, new LayoutParams(-1, -2));
      picks.add(attributePicker);
    } else for (int row = 0; row < 2; row++) {
      LinearLayout line = k.row();
      for (int i = row * 5; i < Math.min(count, row * 5 + 5); i++) {
        String code = attributes.optString(i);
        Button button = h.button(attributeName(code), () -> {
          if (!canChoose()) return;
          freestyle.select(code); updateAttributeDraft();
        });
        button.setTag(code);
        button.setMinHeight(h.dp(48)); button.setMinimumHeight(h.dp(48));
        button.setTextSize(large ? 18 : 15);
        PhaseButtonStyle.apply(h, button, false);
        LayoutParams lp = new LayoutParams(0, h.dp(48), 1);
        lp.setMargins(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
        line.addView(button, lp); attributeButtons.add(button); picks.add(button);
      }
      panel.addView(line, new LayoutParams(-1, -2));
    }
    Button cancel = h.button("취소", () -> {
      if (!canChoose()) return;
      freestyle.cancel(); updateAttributeDraft();
    });
    PhaseButtonStyle.apply(h, cancel, false);
    // No fixed 420dp footer: both controls stay inside the measured safe-area width.
    cancel.setTextSize(16);
    confirm.setLayoutParams(new LayoutParams(0, -1, 1));
    LayoutParams cp = new LayoutParams(0, -1, 1); cp.rightMargin = h.dp(8);
    footer.addView(cancel, 0, cp);
    updateAttributeDraft();
  }

  void showFreestyleRules() {
    new GameDialog(h).setTitle("자유자재 효과 안내")
        .setMessage("내 선두: " + battleView.active(0).optString("name")
            + "\n상대 속성: " + attributeName(freestyle.scope.optString("opponentAttribute"))
            + "\n선택한 속성은 이번 전투 동안 유지돼요. 다른 속성 기술에도 상성이 적용돼요."
            + "\n속성 선택은 초안이며 아래 확정을 눌러야 전송돼요.")
        .setPositiveButton("닫기", null).show();
  }

  void openAttributePicker() {
    if (!canChoose()) return;
    JSONArray attributes = freestyle.scope.optJSONArray("attributes");
    if (attributes == null || attributes.length() == 0 || attributes.length() > 10) return;
    String[] labels = new String[attributes.length()];
    for (int i = 0; i < labels.length; i++) {
      String code = attributes.optString(i);
      labels[i] = attributeName(code) + (code.equals(freestyle.selected) ? " (선택됨)" : "");
    }
    new GameDialog(h).setTitle(battleView.active(0).optString("name") + " 속성 선택")
        .setItems(labels, (dialog, which) -> {
          if (!canChoose()) return;
          freestyle.select(attributes.optString(which)); updateAttributeDraft();
        }).setNeutralButton("효과 안내", (dialog, which) -> showFreestyleRules())
        .setNegativeButton("닫기", null).show();
  }

  void updateAttributeDraft() {
    for (Button button : attributeButtons) {
      String code = (String) button.getTag();
      boolean on = code.equals(freestyle.selected);
      button.setSelected(on);
      button.setText(attributeName(code));
      PhaseButtonStyle.apply(h, button, on);
      button.setContentDescription(attributeName(code) + (on ? " 선택됨" : " 선택"));
    }
    confirm.setEnabled(canChoose() && freestyle.allows(freestyle.selected));
    if (attributePicker != null) attributePicker.setText(freestyle.selected.isEmpty()
        ? "속성 고르기" : attributeName(freestyle.selected) + " (선택됨)");
    confirm.setText(attributePicker == null && !freestyle.selected.isEmpty()
        ? attributeName(freestyle.selected) + " 확정" : "확정");
    confirm.setContentDescription(freestyle.selected.isEmpty() ? "속성을 먼저 선택해 주세요"
        : attributeName(freestyle.selected) + " 속성 확정");
  }

  void showDetail(JSONObject augment) {
    new GameDialog(h).setTitle(AugmentPresentation.name(augment))
        .setMessage(AugmentPresentation.detail(augment))
        .setPositiveButton("닫기", null).show();
  }

  void updateSelection() {
    for (int i = 0; i < offers.length(); i++) {
      JSONObject aug = offers.optJSONObject(i);
      boolean on = aug.optString("id").equals(selected);
      ((View) picks.get(i).getTag())
          .setBackground(new CardFrame(cardColor(aug), on, AugmentPresentation.prism(aug)));
      picks.get(i).setSelected(on);
      names.get(i).setText((on ? "✓ " : "") + aug.optString("name"));
    }
    confirm.setEnabled(!selected.isEmpty() && canChoose());
    String name = "";
    for (int i = 0; i < offers.length(); i++)
      if (offers.optJSONObject(i).optString("id").equals(selected))
        name = offers.optJSONObject(i).optString("name");
    confirm.setText(
        selected.isEmpty()
            ? "증강체 선택"
            : battleView.side(0).optBoolean("needSwitch")
                ? name + " · 다음"
                : name + " · 확정");
    confirm.setContentDescription(name.isEmpty() ? "증강체 선택" : name + " 선택 확정");
  }

  void showSwitch() {
    View oldBack = findViewWithTag("augment-back");
    if (oldBack != null) ((ViewGroup) oldBack.getParent()).removeView(oldBack);
    switchTo = -1;
    switching = true;
    updateTimerRule();
    title.setText("다음에 나갈 갈매미");
    cards.removeAllViews();
    picks.clear();
    rerolls.clear();
    names.clear();
    JSONArray fs = h.list(battleView.side(0), "fighters");
    for (int i = 0; i < fs.length(); i++) {
      JSONObject f = fs.optJSONObject(i);
      if (f.optInt("hp") <= 0) continue;
      int to = i;
      LinearLayout card = columnCard(0xff927017);
      LinearLayout identity = k.row();
      identity.setGravity(Gravity.CENTER_VERTICAL);
      identity.addView(
          k.image(
              f.optString("image").replace("_back.webp", "_front.webp"),
              large ? 100 : 56,
              large ? 100 : 56));
      TextView name = h.text(f.optString("name"), large ? 25 : 19);
      name.setTypeface(h.headline);
      name.setTextColor(JournalTheme.WHITE);
      identity.addView(name, new LayoutParams(0, -2, 1));
      card.addView(identity, new LayoutParams(-1, -2));
      TextView match =
          k.note(
              f.optString("attr")
                  + " · 공격 "
                  + NativeBattleSheets.effectiveness(h, f, battleView.active(1))
                  + " · 방어 "
                  + NativeBattleSheets.defense(h, battleView.active(1), f));
      match.setTextSize(large ? 17 : 13);
      match.setTextColor(JournalTheme.PAPER);
      card.addView(match);
      LinearLayout vitality = k.column();
      vitality.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(8));
      vitality.setBackground(new GamePaperDrawable(h));
      card.addView(vitality, new LayoutParams(-1, -2));
      k.progress(
          vitality,
          "체력 " + f.optInt("hp") + " / " + f.optInt("maxHp"),
          f.optInt("hp"),
          f.optInt("maxHp"),
          JournalTheme.PRIMARY);
      card.setOnClickListener(
          v -> {
            if (!canChoose()) return;
            switchTo = to;
            for (View p : picks)
              ((View) p.getTag()).setBackground(new CardFrame(0xff927017, p == card));
            confirm.setText("출전 확정");
            confirm.setContentDescription(f.optString("name") + " · 출전 확정");
            confirm.setEnabled(true);
          });
    }
    confirm.setEnabled(switchTo >= 0 && canChoose());
    confirm.setText("출전 선택");
    if (offers.length() > 0) {
      Button back = k.action(null, "증강체", this::showOffers);
      PhaseButtonStyle.apply(h, back, false);
      LayoutParams backSize = new LayoutParams(h.dp(112), -1);
      backSize.rightMargin = h.dp(8);
      footer.addView(back, 0, backSize);
      back.setTag("augment-back");
    }
  }

  void updateTimerRule() {
    timerRule.setText(
        battleView.side(0).optBoolean("needSwitch") && offers.length() > 0
            ? "30초 안에 출전까지 확정하지 않으면 증강체·출전을 무작위로 결정해요."
            : switching
                ? "30초 안에 출전을 확정하지 않으면 살아 있는 갈매미 중 무작위로 출전해요."
                : "30초 안에 확정하지 않으면 선택지 중 무작위로 결정돼요.");
  }

  static boolean bonusRerollAvailable(JSONObject battle, long estimatedServerNow) {
    JSONObject bonus = battle.optJSONObject("bonusReroll");
    return "choose".equals(battle.optString("phase")) && battle.optLong("serverNow") > 0
        && bonus != null && bonus.optBoolean("available")
        && bonus.optLong("expiresAt") > estimatedServerNow;
  }

  boolean canReroll(JSONObject offer) {
    if (offer == null) return false;
    long now = battleView.battle.optLong("serverNow")
        + Math.max(0, android.os.SystemClock.elapsedRealtime() - bonusReceivedAt);
    return !offer.optBoolean("rerolled") || bonusRerollAvailable(battleView.battle, now);
  }

  String rerollLabel(JSONObject offer) {
    return !offer.optBoolean("rerolled") ? "다시 뽑기" : canReroll(offer) ? "추가 +1 사용" : "재추첨 완료";
  }

  boolean canChoose() {
    if (attributePhase && freestyle == null) return false;
    if (freestyle != null) {
      if (h.epoch != selectionEpoch || selectionOwner.isEmpty()
          || !selectionOwner.equals(NativeBattleReplay.account(h))
          || !freestyle.valid()
          || !freestyle.scope.optString("targetKey").equals(battleView.active(0).optString("key"))) return false;
      if (NativeRealtime.isBattle(battleView.battle)
          && (h.realtime == null || h.realtime.latest == null)) return false;
      JSONObject current = NativeRealtime.isBattle(battleView.battle) && h.realtime != null
          ? h.realtime.decorate(h.realtime.latest) : h.battle;
      JSONArray teams = current == null ? null : current.optJSONArray("teams");
      JSONObject own = teams == null ? null : teams.optJSONObject(0);
      if (current == null || !battleView.battle.optString("session").equals(current.optString("session"))
          || !freestyle.sameScope(own == null ? null : own.optJSONObject("attributeSelection"))) return false;
    }
    if (h.busy || (deadline > 0 && System.currentTimeMillis() + clockOffset >= deadline)) return false;
    if (!NativeRealtime.isBattle(battleView.battle)) return true;
    NativeRealtime realtime = h.realtime;
    return realtime != null && !realtime.writing && !realtime.playing
        && realtime.latest != null && realtime.latest.optBoolean("canSubmit")
        && !realtime.latest.optBoolean("mySubmitted")
        && battleView.battle.optString("phaseId").equals(realtime.latest.optString("phaseId"));
  }

  void submit() {
    if (freestyle != null) {
      if (!isAttachedToWindow() || !canChoose()) return;
      JSONObject choice = freestyle.confirmed();
      if (choice == null) return;
      // Existing transport locks busy/writing synchronously and owns revision/requestId.
      h.battleTurnFrom(battleView.battle, MainActivity.obj("choose", MainActivity.obj("freestyle", choice)));
      confirm.setEnabled(false);
      return;
    }
    if (!canChoose() || (switching ? switchTo < 0 : selected.isEmpty())) return;
    if (!switching && battleView.side(0).optBoolean("needSwitch")) {
      showSwitch();
      return;
    }
    JSONObject choice = MainActivity.obj();
    try {
      if (!selected.isEmpty()) choice.put("augment", selected);
      if (switching) choice.put("switchTo", switchTo);
    } catch (JSONException ignored) {
    }
    h.battleTurnFrom(battleView.battle, MainActivity.obj("choose", choice));
  }

  final class CardFrame extends Drawable {
    final int tint;
    final boolean selected, prismatic;
    final Paint p = new Paint(3);
    int alpha = 255;
    ColorFilter filter;

    CardFrame(int tint, boolean selected) { this(tint, selected, false); }

    CardFrame(int tint, boolean selected, boolean prismatic) {
      this.prismatic = prismatic;
      this.tint = tint;
      this.selected = selected;
      ThemeArt.of(h).image(ThemeArt.file(ThemeArt.Surface.NAV));
    }

    public void draw(Canvas c) {
      RectF r = new RectF(getBounds());
      int saved = c.save();
      p.setAlpha(alpha);
      p.setColorFilter(filter);
      if (alpha != 255 || filter != null) c.saveLayer(r, p);
      r.inset(h.dp(2), h.dp(2));
      p.setStyle(Paint.Style.FILL);
      p.setColor(0xff163846);
      c.drawRoundRect(r, h.dp(16), h.dp(16), p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(h.dp(selected ? 3 : 1));
      p.setColor(selected ? JournalTheme.SELECTED_EDGE : tint);
      c.drawRoundRect(r, h.dp(16), h.dp(16), p);
      p.setStyle(Paint.Style.FILL);
      p.setAlpha(255);
      p.setColorFilter(null);
      r.inset(h.dp(7), h.dp(7));
      p.setStyle(Paint.Style.FILL);
      p.setColor(tint);
      // Only the 56dp crest shimmers; the navy card and selection edge remain unchanged.
      if (prismatic) p.setShader(new LinearGradient(r.centerX() - h.dp(28), r.top,
          r.centerX() + h.dp(28), r.top,
          new int[]{0xff78a5a4, 0xffb4a0c7, 0xffc8aa70}, null, Shader.TileMode.CLAMP));
      c.drawRoundRect(r.centerX() - h.dp(28), r.top, r.centerX() + h.dp(28), r.top + h.dp(3), h.dp(2), h.dp(2), p);
      p.setShader(null);
      if (selected) {
        r.set(getBounds());
        r.inset(h.dp(4), h.dp(4));
        r.bottom -= h.dp(4);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h.dp(2));
        p.setColor(JournalTheme.SELECTED_EDGE);
        c.drawRoundRect(r, h.dp(12), h.dp(12), p);
        p.setStyle(Paint.Style.FILL);
      }
      c.restoreToCount(saved);
    }

    public void setAlpha(int a) {
      alpha = Math.max(0, Math.min(255, a));
      invalidateSelf();
    }

    public void setColorFilter(ColorFilter f) {
      filter = f;
      invalidateSelf();
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}


