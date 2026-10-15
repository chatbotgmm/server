package org.dpdns.gmm;

import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** 기술은 즉시 사용하고, 별도 상세 버튼으로 전체 효과와 사용 불가 사유를 읽는다. */
final class SkillCardView extends FrameLayout {
  final Button use, detail;
  final TextView effect;
  final JSONObject skill;

  SkillCardView(BattleView view, JSONObject sk, boolean large) {
    super(view.host);
    MainActivity h = view.host;
    skill = sk;
    boolean combo = sk.optString("id").startsWith("combo-");
    String reason = reason(view, sk);
    String kind = kind(sk);
    String name = sk.optString("name");
    String meta = meta(sk);
    use =
        h.button(
            "",
            () ->
                h.battleTurn(
                    MainActivity.obj(
                        "action",
                        MainActivity.obj("kind", "skill", "skillId", sk.optString("id")))));
    JSONObject preview = sk.optJSONObject("preview");
    int tint =
        combo ? 0xffc59326 : MenuKit.attrColor(preview == null ? "" : preview.optString("attr"));
    use.setBackground(new GameSkillDrawable(h, tint, combo));
    use.setEnabled(reason.isEmpty());
    use.setContentDescription(
        name
            + " · "
            + meta
            + " · "
            + sk.optString("desc")
            + (reason.isEmpty() ? "" : " · 사용 불가: " + reason));
    use.setOnLongClickListener(
        v -> {
          NativeBattleSheets.skill(view, sk);
          return true;
        });
    addView(use, new LayoutParams(-1, -1));

    LinearLayout content = new LinearLayout(h);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(h.dp(9), h.dp(7), h.dp(8), h.dp(6));
    title = label(h, name, large ? 25 : 19, true);
    if (combo) {
      title.setCompoundDrawables(new ComboEmblem(h), null, null, null);
      title.setCompoundDrawablePadding(h.dp(6));
    }
    title.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    title.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    // 제목은 상세 버튼과 폭을 나누지 않는다. 긴 이름도 줄바꿈해 전부 보여 준다.
    content.addView(title, new LinearLayout.LayoutParams(-1, -2));
    category =
        label(
            h,
            combo ? "합동기 · 전투당 한 번" : shortKind(kind) + " · " + categoryMeta(sk),
            large ? 16 : 12,
            false);
    category.setTextColor(JournalTheme.MUTED);
    if (!combo) content.addView(category);
    power = label(h, powerMeta(sk), large ? 16 : 13, false);
    power.setTextColor(JournalTheme.MUTED);
    if (!combo) content.addView(power);
    LinearLayout bottom = new LinearLayout(h);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    effect =
        label(
            h,
            (combo ? "전투당 한 번" : conciseEffect(sk))
                + (reason.isEmpty() ? "" : "\n" + shortReason(reason)),
            large ? 16 : 13,
            false);
    effect.setTextColor(reason.isEmpty() ? JournalTheme.MUTED : NativeBattleLog.DAMAGE);
    effect.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    effect.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    // 핵심 수치와 기간은 생략하지 않는다. 큰 글자나 긴 효과는 카드가 늘어나며 패널을 스크롤한다.
    if (combo) {
      // 합동기는 제목 전폭을 유지하고 사용 횟수·상세만 같은 줄에 모아 첫 기술 두 개를 가리지 않는다.
      bottom.addView(effect, new LinearLayout.LayoutParams(0, -2, 1));
    } else {
      content.addView(effect, new LinearLayout.LayoutParams(-1, -2));
      // 상세 조작은 본문 아래 독립된 줄에 둔다. 본문 폭을 48dp 줄여 수치·어절을 끊지 않는다.
      Space spacer = new Space(h);
      bottom.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
    }
    detail = h.button("상세", () -> NativeBattleSheets.skill(view, sk));
    detail.setTextSize(large ? 16 : 13);
    detail.setPadding(0, 0, 0, h.dp(2));
    detail.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    detail.setMinHeight(h.dp(48));
    detail.setMinimumHeight(h.dp(48));
    detail.setContentDescription(name + " 전체 효과와 사용 조건 보기");
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(h.dp(48), h.dp(48));
    dp.leftMargin = h.dp(4);
    bottom.addView(detail, dp);
    content.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
    addView(content, new LayoutParams(-1, -2));
    setMinimumHeight(h.dp(combo ? (large ? 88 : 78) : (large ? 154 : 120)));
  }

  final TextView title, category, power;

  static TextView label(MainActivity h, String text, int size, boolean headline) {
    TextView label = h.text(text, size);
    label.setTextSize(size);
    label.setTypeface(headline ? h.headline : h.prose);
    label.setTextColor(JournalTheme.INK);
    label.setPadding(0, 0, 0, 0);
    // Jua의 일부 한글은 hhea ascent를 넘어가므로 제목 위쪽 획의 여유를 보존한다.
    label.setIncludeFontPadding(true);
    label.setTextLocale(java.util.Locale.KOREAN);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      label.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    return label;
  }

  static String shortKind(String kind) {
    return kind.replace("전용 기술", "전용기").replace("일반 기술", "일반기").replace("보조 속성", "보조");
  }

  static String categoryMeta(JSONObject sk) {
    if (sk.optInt("power") <= 0) return "보조 기술";
    JSONObject p = sk.optJSONObject("preview");
    if (p == null) return "공격 기술";
    return p.optString("attrName")
        + " · "
        + (p.optString("category").equals("mag") ? "✦ 마법" : "⚔ 물리");
  }

  static String powerMeta(JSONObject sk) {
    if (sk.optInt("power") <= 0) return "보조 효과";
    JSONObject p = sk.optJSONObject("preview");
    double multiplier = p == null ? 1 : p.optDouble("multiplier", 1);
    return "위력 "
        + sk.optInt("power")
        + (sk.optInt("hits", 1) > 1 ? "×" + sk.optInt("hits") : "")
        + " · "
        + (multiplier > 1 ? "▲유리" : multiplier < 1 ? "▼불리" : "상성 보통");
  }

  static String conciseEffect(JSONObject sk) {
    return effect(sk)
        .replace("맞은 상대의", "상대")
        .replace("우리 팀의", "우리 팀")
        .replace("체력을 회복해요.", "체력 회복")
        .replace("방어를 낮추고", "방어 감소 ·")
        .replace(" 동안 ", " ")
        .replace("추가 효과 없음", "연속 사용 가능");
  }

  static String kind(JSONObject sk) {
    String id = sk.optString("id");
    return id.startsWith("combo-")
        ? "합동기 · 전투당 한 번"
        : id.startsWith("sub-")
            ? "보조 속성"
            : id.startsWith("learn-")
                ? "일반 기술"
                : id.equals("sig") ? "전용 기술" : id.startsWith("role-") ? "역할기" : "기본기";
  }

  static String meta(JSONObject sk) {
    if (sk.optInt("power") <= 0) return "보조 기술";
    JSONObject p = sk.optJSONObject("preview");
    String attr = p == null ? "" : p.optString("attrName");
    String cat = p == null ? "" : p.optString("category").equals("mag") ? "마법" : "물리";
    double mult = p == null ? 1 : p.optDouble("multiplier", 1);
    String power =
        "위력" + sk.optInt("power") + (sk.optInt("hits", 1) > 1 ? "×" + sk.optInt("hits") : "");
    return (attr.isEmpty() ? "" : attr + "·" + cat + "·")
        + power
        + (mult > 1 ? " ▲유리" : mult < 1 ? " ▼불리" : "");
  }

  static String effect(JSONObject sk) {
    if (sk.optString("id").startsWith("sub-")) return "";
    String description = sk.optString("desc").replaceFirst("^위력 \\d+(으로 \\d+번)?(, )?", "").trim();
    if (description.startsWith("·")) description = description.substring(1).trim();
    return description.isEmpty() ? "추가 효과 없음" : description;
  }

  static String reason(BattleView view, JSONObject sk) {
    String reason = sk.isNull("reason") ? "" : sk.optString("reason");
    if (!reason.isEmpty() && sk.optString("id").startsWith("combo-"))
      return view.side(0).optBoolean("comboUsed")
          ? "이 전투에서 합동기를 이미 사용했어요."
          : "같은 시너지의 살아 있는 동료가 필요해요.";
    return reason;
  }

  static String shortReason(String reason) {
    return reason.contains("합동기를 이미 사용")
        ? "이번 전투에 사용함"
        : reason.contains("이미 적용") ? "이미 적용됨" : reason.contains("합동기") ? "사용 조건 확인" : reason;
  }
}
