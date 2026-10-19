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
  final Runnable tick =
      new Runnable() {
        public void run() {
          if (!isAttachedToWindow() || deadline <= 0) return;
          long remaining = Math.max(0, deadline - (System.currentTimeMillis() + clockOffset));
          boolean localInput = canChoose();
          title.setText(
              (switching
                      ? "② 다음 출전"
                      : battleView.side(0).optBoolean("needSwitch") ? "① 증강체" : "전투 증강체")
                  + " · "
                  + (remaining <= 0 ? "결과 확인 중"
                      : NativeRealtime.isBattle(battleView.battle) && !localInput
                          ? h.realtime.writing ? "선택 전송 중" : "상대 선택 대기"
                          : ((remaining + 999) / 1000) + "초"));
          for (View pick : picks) pick.setEnabled(localInput);
          for (int i = 0; i < rerolls.size(); i++)
            rerolls.get(i).setEnabled(localInput && !offers.optJSONObject(i).optBoolean("rerolled"));
          confirm.setEnabled(localInput && (switching ? switchTo >= 0 : !selected.isEmpty()));
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
    confirm.setBackground(new GameButtonDrawable(h, JournalTheme.PRIMARY));
    confirm.setTextColor(JournalTheme.controlText(JournalTheme.PRIMARY));
    confirm.setTextSize(large ? 18 : 16);
    confirm.setMinHeight(h.dp(48));
    confirm.setMinimumHeight(h.dp(48));
    confirm.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    footer = k.row();
    LayoutParams foot = new LayoutParams(-1, h.dp(48));
    addView(footer, foot);
    footer.addView(confirm, new LayoutParams(0, -1, 2));
    if (offers.length() == 0) showSwitch();
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
      int type = kind(id);
      LinearLayout content = columnCard(color(type));
      ImageView icon =
          k.image(
              "/duel/icons/"
                  + (JournalIcons.supports(id) ? id : aug.optString("icon", "u-gift"))
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
          disadvantaged
              && Arrays.asList("camo", "reverse", "weakguard", "neutral", "dual", "counter")
                  .contains(id);
      TextView badge = h.text(kindName(type) + (recommend ? " · 추천" : ""), large ? 15 : 12);
      badge.setTextColor(color(type));
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
      desc.setMaxLines(Integer.MAX_VALUE);
      desc.setEllipsize(null);
      desc.setGravity(Gravity.CENTER);
      desc.setTag("augment-core-effect");
      desc.setBackground(new GamePaperDrawable(h));
      LayoutParams effectSlot = new LayoutParams(-1, 0, 1);
      effectSlot.topMargin = h.dp(5);
      content.addView(desc, effectSlot);
      content.setContentDescription(
          aug.optString("name") + " · " + kindName(type) + " · " + aug.optString("desc"));
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
              aug.optBoolean("rerolled") ? "재추첨 완료" : "다시 뽑기",
              () -> {
                if (!canChoose()) return;
                if (id.equals(selected)) {
                  selected = "";
                  h.pendingAugment = "";
                }
                h.battleTurnFrom(battleView.battle, MainActivity.obj("reroll", ix));
              });
      // 종이 버튼 표면을 유지해 누를 수 있는 버튼으로 보이게 하고, 재추첨 뒤에는 비활성 표면으로 구분한다.
      reroll.setTextSize(large ? 15 : 14);
      reroll.setPadding(h.dp(4), 0, h.dp(4), h.dp(2));
      reroll.setMinHeight(h.dp(48));
      reroll.setMinimumHeight(h.dp(48));
      reroll.setEnabled(!aug.optBoolean("rerolled"));
      LayoutParams rp = new LayoutParams(-1, h.dp(48));
      rp.topMargin = h.dp(4);
      LinearLayout actions = k.row();
      actions.addView(reroll, new LayoutParams(0, -1, 2));

      column.addView(actions, rp);
      rerolls.add(reroll);
    }
    updateSelection();
  }

  void updateSelection() {
    for (int i = 0; i < offers.length(); i++) {
      JSONObject aug = offers.optJSONObject(i);
      boolean on = aug.optString("id").equals(selected);
      ((View) picks.get(i).getTag())
          .setBackground(new CardFrame(color(kind(aug.optString("id"))), on));
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
                ? name + " · 다음 출전 선택"
                : name + " · 선택 확정");
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
            confirm.setText(f.optString("name") + " · 출전 확정");
            confirm.setContentDescription(f.optString("name") + " · 출전 확정");
            confirm.setEnabled(true);
          });
    }
    confirm.setEnabled(switchTo >= 0 && canChoose());
    confirm.setText("출전 선택");
    if (offers.length() > 0) {
      Button back = k.action(null, "증강체", this::showOffers);
      footer.addView(back, 0, new LayoutParams(0, -1, 1));
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

  boolean canChoose() {
    if (h.busy || (deadline > 0 && System.currentTimeMillis() + clockOffset >= deadline)) return false;
    if (!NativeRealtime.isBattle(battleView.battle)) return true;
    NativeRealtime realtime = h.realtime;
    return realtime != null && !realtime.writing && !realtime.playing
        && realtime.latest != null && realtime.latest.optBoolean("canSubmit")
        && !realtime.latest.optBoolean("mySubmitted")
        && battleView.battle.optString("phaseId").equals(realtime.latest.optString("phaseId"));
  }

  void submit() {
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
    final boolean selected;
    final Paint p = new Paint(3);
    int alpha = 255;
    ColorFilter filter;

    CardFrame(int tint, boolean selected) {
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
      ThemeArt.of(h).surface(c, ThemeArt.Surface.NAV, r);
      p.setAlpha(255);
      p.setColorFilter(null);
      r.inset(h.dp(7), h.dp(7));
      p.setStyle(Paint.Style.FILL);
      p.setColor(tint);
      c.drawRect(r.left, r.top, r.right, r.top + h.dp(2), p);
      if (selected) {
        r.set(getBounds());
        r.inset(h.dp(4), h.dp(4));
        r.bottom -= h.dp(4);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h.dp(2));
        p.setColor(JournalTheme.SELECTED_EDGE);
        c.drawRoundRect(r, h.dp(4), h.dp(4), p);
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
