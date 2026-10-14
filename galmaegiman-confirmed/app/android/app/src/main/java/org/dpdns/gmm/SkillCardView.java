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
    content.setPadding(
        h.dp(9), h.dp(combo || large ? 7 : 5), h.dp(8), h.dp(combo || large ? 8 : 5));
    LinearLayout heading = new LinearLayout(h);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout names = new LinearLayout(h);
    names.setOrientation(LinearLayout.VERTICAL);
    TextView category = h.text(kind, large ? 16 : 12);
    category.setTextSize(large ? 16 : 12);
    category.setTypeface(h.prose);
    category.setTextColor(0xff7b5b3a);
    category.setPadding(0, 0, 0, 0);
    category.setIncludeFontPadding(false);
    TextView title = h.text(name, large ? 25 : 19);
    title.setTypeface(h.headline);
    title.setTextSize(large ? 25 : 19);
    title.setPadding(0, 0, 0, 0);
    title.setIncludeFontPadding(false);
    title.setMaxLines(combo ? 1 : 2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    if (!combo) names.addView(category);
    names.addView(title);
    if (combo) {
      category.setText(reason.isEmpty() ? "합동기 · 전투당 한 번" : shortReason(reason));
      names.addView(category);
    }
    heading.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
    detail = h.button("상세", () -> NativeBattleSheets.skill(view, sk));
    detail.setTextSize(large ? 16 : 13);
    detail.setPadding(0, 0, 0, h.dp(2));
    detail.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    detail.setMinHeight(h.dp(48));
    detail.setMinimumHeight(h.dp(48));
    detail.setContentDescription(name + " 전체 효과와 사용 조건 보기");
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(h.dp(48), h.dp(48));
    dp.leftMargin = h.dp(3);
    heading.addView(detail, dp);
    content.addView(heading);

    TextView chips = h.text(meta, large ? 16 : 12);
    chips.setTextSize(large ? 16 : 12);
    chips.setTypeface(h.prose);
    chips.setPadding(0, 0, 0, 0);
    chips.setIncludeFontPadding(false);
    chips.setSingleLine(true);
    chips.setEllipsize(TextUtils.TruncateAt.END);
    SpannableString chipLabel = new SpannableString(meta);
    int at = 0;
    for (String token : meta.split("·")) {
      int end = Math.min(chipLabel.length(), at + token.length());
      if (end > at)
        chipLabel.setSpan(
            new BackgroundColorSpan(at == 0 ? (tint & 0x00ffffff) | 0x55000000 : 0xffeee2ca),
            at,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      at = end + 1;
    }
    chips.setText(chipLabel);
    if (!combo) content.addView(chips);
    effect = h.text(reason.isEmpty() ? effect(sk) : shortReason(reason), large ? 16 : 13);
    effect.setTextSize(large ? 16 : 13);
    effect.setTypeface(h.prose);
    effect.setTextColor(reason.isEmpty() ? 0xff634a35 : 0xffad4839);
    effect.setPadding(0, 0, 0, 0);
    effect.setIncludeFontPadding(false);
    effect.setSingleLine(combo);
    effect.setMaxLines(combo ? 1 : 2);
    effect.setEllipsize(TextUtils.TruncateAt.END);
    if (!combo && (!sk.optString("id").startsWith("sub-") || !reason.isEmpty()))
      content.addView(effect);
    addView(content, new LayoutParams(-1, -2));
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
