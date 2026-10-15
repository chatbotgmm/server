package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 웹의 전체 화면 증강체 선택판: 비교·안내·3장·개별 재추첨·선택 확인을 유지한다. */
final class NativeAugmentView extends LinearLayout {
  final BattleView battleView;
  final MainActivity h;
  final MenuKit k;
  final JSONArray offers;
  final boolean large;
  final LinearLayout cards;
  final Button confirm;
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
          title.setText(
              (switching
                      ? "② 다음 출전"
                      : battleView.side(0).optBoolean("needSwitch") ? "① 증강체" : "전투 증강체")
                  + " · "
                  + ((remaining + 999) / 1000)
                  + "초");
          boolean urgent = remaining <= 10000;
          title.setTextColor(urgent ? NativeBattleLog.DAMAGE : JournalTheme.INK);
          timerRule.setTextColor(urgent ? NativeBattleLog.DAMAGE : JournalTheme.MUTED);
          timerRule.setTypeface(h.prose, urgent ? Typeface.BOLD : Typeface.NORMAL);
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
    setBackground(new GamePaperDrawable(h, JournalTheme.TEAL));
    setPadding(
        h.dp(large ? 44 : 20) + h.root.getPaddingLeft(),
        h.dp(8) + h.root.getPaddingTop(),
        h.dp(large ? 44 : 20) + h.root.getPaddingRight(),
        h.dp(10) + h.root.getPaddingBottom());
    title = h.text("◆ 전투 증강체 ◆", large ? 28 : 22);
    title.setTypeface(h.headline);
    title.setGravity(Gravity.CENTER);
    title.setTextColor(JournalTheme.INK);
    addView(title, new LayoutParams(-1, -2));
    timerRule = h.text("30초 안에 확정하지 않으면 선택지 중 무작위로 결정돼요.", large ? 17 : 14);
    timerRule.setTypeface(h.prose);
    timerRule.setTextColor(JournalTheme.MUTED);
    timerRule.setGravity(Gravity.CENTER);
    timerRule.setPadding(0, 0, 0, h.dp(3));
    addView(timerRule, new LayoutParams(-1, -2));
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
    addView(versus, new LayoutParams(-1, h.dp(large ? 46 : 38)));
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
    if (!help.isEmpty()) addView(hint, new LayoutParams(-1, -2));
    cards = k.row();
    cards.setGravity(Gravity.TOP);
    LayoutParams cp = new LayoutParams(-1, 0, 1);
    cp.topMargin = h.dp(8);
    cp.bottomMargin = h.dp(6);
    addView(cards, cp);
    confirm = h.button("증강체 선택", this::submit);
    confirm.setTextSize(large ? 22 : 18);
    LayoutParams foot = new LayoutParams(-1, h.dp(large ? 56 : 48));
    foot.leftMargin = h.dp(large ? 140 : 100);
    foot.rightMargin = foot.leftMargin;
    addView(confirm, foot);
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
    ScrollView scroll = new ScrollView(h);
    scroll.setFillViewport(true);
    scroll.setBackground(new CardFrame(tint, false));
    column.addView(scroll, new LayoutParams(-1, 0, 1));
    LinearLayout content = k.column();
    content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    content.setPadding(
        h.dp(large ? 12 : 9), h.dp(large ? 20 : 7), h.dp(large ? 12 : 9), h.dp(large ? 20 : 7));
    scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    picks.add(content);
    content.setTag(scroll);
    return content;
  }

  void showOffers() {
    View back = findViewWithTag("augment-back");
    if (back != null) removeView(back);
    switching = false;
    updateTimerRule();
    cards.removeAllViews();
    picks.clear();
    rerolls.clear();
    names.clear();
    title.setText(
        battleView.side(0).optBoolean("needSwitch") ? "① 증강체 선택 → ② 다음 출전" : "◆ 전투 증강체 ◆");
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
              large ? 56 : 24,
              large ? 56 : 24);
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
      TextView name = h.text(aug.optString("name"), large ? 27 : 20);
      name.setTypeface(h.headline);
      name.setGravity(Gravity.CENTER);
      name.setPadding(h.dp(3), 0, 0, 0);
      name.setIncludeFontPadding(false);
      heading.addView(name, new LayoutParams(0, -2, 1));
      content.addView(heading, new LayoutParams(-1, -2));
      names.add(name);
      if (disadvantaged
          && Arrays.asList("camo", "reverse", "weakguard", "neutral", "dual", "counter")
              .contains(id)) {
        TextView recommended = h.text("지금 추천", large ? 16 : 12);
        recommended.setTextColor(0xff397953);
        recommended.setGravity(Gravity.CENTER);
        content.addView(recommended);
      }
      TextView badge = h.text(kindName(type), large ? 15 : 12);
      badge.setTextColor(color(type));
      badge.setBackground(new GamePaperDrawable(h));
      badge.setPadding(h.dp(10), h.dp(2), h.dp(10), h.dp(2));
      content.addView(badge, new LayoutParams(-2, -2));
      TextView desc = k.note(aug.optString("desc"));
      desc.setTextSize(large ? 19 : 16);
      desc.setPadding(0, h.dp(3), 0, 0);
      desc.setIncludeFontPadding(false);
      desc.setTextColor(JournalTheme.MUTED);
      desc.setGravity(Gravity.CENTER);
      content.addView(desc);
      content.setContentDescription(
          aug.optString("name") + " · " + kindName(type) + " · " + aug.optString("desc"));
      content.setOnClickListener(
          v -> {
            if (h.busy) return;
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
                if (id.equals(selected)) {
                  selected = "";
                  h.pendingAugment = "";
                }
                h.battleTurn(MainActivity.obj("reroll", ix));
              });
      reroll.setTextSize(large ? 16 : 13);
      reroll.setMinHeight(h.dp(48));
      reroll.setMinimumHeight(h.dp(48));
      reroll.setEnabled(!aug.optBoolean("rerolled"));
      LayoutParams rp = new LayoutParams(-1, h.dp(48));
      rp.topMargin = h.dp(4);
      column.addView(reroll, rp);
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
    confirm.setEnabled(!selected.isEmpty());
    String name = "";
    for (int i = 0; i < offers.length(); i++)
      if (offers.optJSONObject(i).optString("id").equals(selected))
        name = offers.optJSONObject(i).optString("name");
    confirm.setText(
        selected.isEmpty()
            ? "증강체 선택"
            : name + (battleView.side(0).optBoolean("needSwitch") ? " · 출전 선택" : " · 확정"));
    confirm.setContentDescription(name.isEmpty() ? "증강체 선택" : name + " 선택 확정");
  }

  void showSwitch() {
    View oldBack = findViewWithTag("augment-back");
    if (oldBack != null) removeView(oldBack);
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
      card.addView(
          k.image(
              f.optString("image").replace("_back.webp", "_front.webp"),
              large ? 150 : 90,
              large ? 150 : 90));
      card.addView(h.text(f.optString("name"), large ? 25 : 19));
      card.addView(
          k.note(
              f.optString("attr")
                  + (NativeBattleSheets.role(f).isEmpty() ? "" : " · " + NativeBattleSheets.role(f))
                  + " · "
                  + NativeBattleSheets.matchupLabel(f)
                  + " · 공격 "
                  + NativeBattleSheets.effectiveness(h, f, battleView.active(1))
                  + " · 방어 "
                  + NativeBattleSheets.defense(h, battleView.active(1), f)));
      k.progress(
          card,
          "체력 " + f.optInt("hp") + " / " + f.optInt("maxHp"),
          f.optInt("hp"),
          f.optInt("maxHp"),
          0xff51c98b);
      card.setOnClickListener(
          v -> {
            if (h.busy) return;
            switchTo = to;
            for (View p : picks)
              ((View) p.getTag()).setBackground(new CardFrame(0xff927017, p == card));
            confirm.setText(f.optString("name") + " · 출전 확정");
            confirm.setEnabled(true);
          });
    }
    confirm.setEnabled(switchTo >= 0);
    confirm.setText("출전 선택");
    if (offers.length() > 0) {
      Button back = k.action(null, "증강체로 돌아가기", this::showOffers);
      addView(back, getChildCount() - 1, new LayoutParams(-1, h.dp(48)));
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

  void submit() {
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
    h.battleTurn(MainActivity.obj("choose", choice));
  }

  final class CardFrame extends Drawable {
    final int tint;
    final boolean selected;
    final Paint p = new Paint(3);

    CardFrame(int tint, boolean selected) {
      this.tint = tint;
      this.selected = selected;
    }

    public void draw(Canvas c) {
      RectF r = new RectF(getBounds());
      float density = h.getResources().getDisplayMetrics().density;
      JournalTheme.drawPanel(c, r, density, selected ? 0xfff2e2bf : JournalTheme.PAPER, true);
      if (selected) {
        r.inset(h.dp(4), h.dp(4));
        r.bottom -= h.dp(4);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h.dp(3));
        p.setColor(JournalTheme.BRASS);
        c.drawRoundRect(r, h.dp(10), h.dp(10), p);
        p.setStyle(Paint.Style.FILL);
      }
    }

    public void setAlpha(int a) {
      p.setAlpha(a);
    }

    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
