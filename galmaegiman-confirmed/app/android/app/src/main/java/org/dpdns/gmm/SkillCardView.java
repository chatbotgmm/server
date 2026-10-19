package org.dpdns.gmm;

import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** 기술명과 핵심 효과만 표시하고 속성 아이콘에서 상성을 확인하는 전투 카드. */
final class SkillCardView extends FrameLayout {
  final Button use;
  final TextView effect;
  final JSONObject skill;
  final LinearLayout content;

  SkillCardView(BattleView view, JSONObject sk, boolean large) {
    this(view, sk, large, false, null);
  }

  SkillCardView(
      BattleView view, JSONObject sk, boolean large, boolean compact, Runnable beforeUse) {
    super(view.host);
    MainActivity h = view.host;
    skill = sk;
    boolean combo = sk.optString("id").startsWith("combo-");
    String reason = reason(view, sk);
    String name = sk.optString("name");
    String meta = categoryMeta(sk);
    use =
        h.button(
            "",
            () -> {
              if (beforeUse != null) beforeUse.run();
              h.battleTurnFrom(view.battle, 
                  MainActivity.obj(
                      "action", MainActivity.obj("kind", "skill", "skillId", sk.optString("id"))));
            });
    use.setBackground(new BattleSkin(h, "button-surrender"));
    use.setEnabled(reason.isEmpty());
    use.setContentDescription(
        name
            + " · "
            + meta
            + " · "
            + sk.optString("desc")
            + (bonusActive(sk) ? " · 조건 발동" : "")
            + (reason.isEmpty() ? "" : " · 사용 불가: " + reason));
    addView(use, new LayoutParams(-1, -1));

    content = new LinearLayout(h);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(h.dp(8), h.dp(compact ? 5 : 7), h.dp(8), h.dp(compact ? 5 : 6));
    LinearLayout heading = new LinearLayout(h);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    String attr = BattleTypeInfo.skillAttr(view, sk);
    ImageView typeIcon = BattleTypeInfo.icon(h, attr);
    typeIcon.setContentDescription(BattleTypeInfo.name(h, attr) + " 속성 상성 보기");
    typeIcon.setFocusable(true);
    typeIcon.setOnClickListener(v -> BattleTypeInfo.show(view, sk));
    heading.addView(typeIcon, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    title = label(h, name, 16, true);
    title.setTextColor(reason.isEmpty() ? JournalTheme.INK : JournalTheme.MUTED);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    title.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    // Keep the shared field for callers, without displaying power or a damage formula.
    power = label(h, "", 14, false);
    power.setVisibility(GONE);
    String brief = conciseEffect(sk);
    if (combo) brief = "전투당 1회" + (brief.isEmpty() ? "" : " · " + brief);
    if (!reason.isEmpty()) brief += (brief.isEmpty() ? "" : " · ") + shortReason(reason);
    effect = label(h, brief, 14, false);
    effect.setTextColor(
        reason.isEmpty() ? JournalTheme.TEAL : NativeBattleLog.DAMAGE);
    effect.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    effect.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    effect.setMaxLines(2);
    effect.setEllipsize(TextUtils.TruncateAt.END);
    // 카드 표면의 황동 테두리(약 7dp) 위에 효과 글자가 겹치지 않도록 좌우를 더 들인다.
    effect.setPadding(h.dp(6), 0, h.dp(6), 0);
    if (effect.getText().length() == 0) effect.setVisibility(GONE);
    // 효과는 두 줄로 유지하며 전체 효과 문장은 버튼 접근성 설명에 남긴다.
    content.addView(effect, new LinearLayout.LayoutParams(-1, -2));
    addView(content, new LayoutParams(-1, -2, compact ? Gravity.CENTER_VERTICAL : Gravity.TOP));
    setMinimumHeight(h.dp(compact ? 96 : 104));
  }

  final TextView title, power;

  /** 고정 명령판에 넣기 전에 실제 글꼴·줄바꿈으로 필요한 높이를 계산한다. */
  int naturalHeight(int width) {
    content.measure(
        MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    return Math.max(getMinimumHeight(), content.getMeasuredHeight());
  }

  boolean summaryFits(int width) {
    if (effect.getVisibility() == GONE || effect.getText().length() == 0) return true;
    int textWidth = Math.max(1, width - content.getPaddingLeft() - content.getPaddingRight());
    return StaticLayout.Builder.obtain(
                effect.getText(), 0, effect.length(), effect.getPaint(), textWidth)
            .setIncludePad(effect.getIncludeFontPadding())
            .setBreakStrategy(effect.getBreakStrategy())
            .setHyphenationFrequency(effect.getHyphenationFrequency())
            .build()
            .getLineCount()
        <= 2;
  }

  /** 기존 선택 모달 호출과 호환하되 카드의 두 줄 제한은 유지한다. */
  void showCompleteEffect() {
    effect.setMaxLines(2);
    effect.setEllipsize(TextUtils.TruncateAt.END);
  }

  /** 실제 공격 속성의 아이콘만 사용한다. 일반 기술 일러스트로 대체하지 않는다. */
  static String icon(JSONObject sk) {
    JSONObject preview = sk.optJSONObject("preview");
    String attr = preview == null ? sk.optString("attr") : preview.optString("attr");
    return BattleTypeInfo.valid(attr) ? "attr-" + attr.toLowerCase(java.util.Locale.ROOT) : "";
  }

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
    if (currentPower(sk) <= 0) return "보조 기술";
    JSONObject p = sk.optJSONObject("preview");
    if (p == null) return "공격 기술";
    return p.optString("attrName") + " · " + (p.optString("category").equals("mag") ? "마법" : "물리");
  }

  static String powerMeta(JSONObject sk) {
    int power = currentPower(sk);
    JSONObject p = sk.optJSONObject("preview");
    String attr = p == null ? "" : p.optString("attrName");
    String prefix = attr.isEmpty() ? "" : attr + " · ";
    if (power <= 0) return prefix + "보조";
    double multiplier = p == null ? 1 : p.optDouble("multiplier", 1);
    if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1;
    return prefix
        + "위력 "
        + power
        + (sk.optInt("hits", 1) > 1 ? "×" + sk.optInt("hits") : "")
        + (multiplier > 1 ? " ▲유리" : multiplier < 1 ? " ▼불리" : "");
  }

  static CharSequence powerLabel(JSONObject sk) {
    return powerLabel(sk, false);
  }

  static CharSequence powerLabel(JSONObject sk, boolean primary) {
    SpannableStringBuilder text = new SpannableStringBuilder(powerMeta(sk));
    if (bonusActive(sk)) {
      int start = text.toString().indexOf("위력 ");
      int end = text.toString().indexOf(" ▲");
      if (end < 0) end = text.toString().indexOf(" ▼");
      NativeBattleLog.strong(
          text,
          Math.max(0, start),
          end < 0 ? text.length() : end,
          primary ? 0xffcceacb : NativeBattleLog.HEAL);
    }
    int from = Math.max(text.toString().lastIndexOf('▲'), text.toString().lastIndexOf('▼'));
    JSONObject preview = sk.optJSONObject("preview");
    double multiplier = preview == null ? 1 : preview.optDouble("multiplier", 1);
    if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1;
    if (from >= 0)
      NativeBattleLog.strong(
          text,
          from,
          text.length(),
          multiplier > 1
              ? primary ? 0xffcceacb : NativeBattleLog.HEAL
              : multiplier < 1
                  ? primary ? 0xffffcbbd : NativeBattleLog.DAMAGE
                  : JournalTheme.MUTED);
    return text;
  }

  static String conciseEffect(JSONObject sk) {
    if (sk.has("summary")) return sk.optString("summary");
    java.util.ArrayList<String> parts = new java.util.ArrayList<>();
    JSONArray effects = sk.optJSONArray("effects");
    if (effects != null)
      for (int i = 0; i < effects.length(); i++) {
        JSONObject e = effects.optJSONObject(i);
        if (e == null) continue;
        if (e.optString("kind").equals("lockSelf")) parts.add("이후 교체 불가");
        else if (e.optString("kind").equals("recharge")) parts.add("다음 턴 휴식");
        else if (e.optString("kind").equals("buff")
            && e.optString("target").equals("self")
            && e.optInt("pct") < 0) parts.add(shortEffect(e));
      }
    JSONObject bonus = sk.optJSONObject("bonus");
    if (bonus != null) {
      String when =
          switch (bonus.optString("when")) {
            case "burned" -> "화상 적";
            case "slowed" -> "느려진 적";
            case "bound" -> "속박 적";
            case "lowHp" -> "체력 50%↓ 때";
            case "finisher" -> "체력 40%↓ 적";
            case "opening" -> "체력 90%↑ 적";
            case "slower" -> "느릴 때";
            case "weakened" -> "방어 약화 적";
            default -> "조건 충족 시";
          };
      parts.add(when + " +" + bonus.optInt("pct") + "%");
    }
    if (sk.optInt("pierce") > 0) parts.add("방어 " + sk.optInt("pierce") + "% 관통");
    if (sk.optInt("drain") > 0) parts.add("피해 " + sk.optInt("drain") + "% 흡수");
    if (sk.optBoolean("crit")) parts.add("확정 급소");
    if (sk.optBoolean("priority")) parts.add("먼저 공격");
    if (effects != null)
      for (int i = 0; i < effects.length(); i++) {
        JSONObject e = effects.optJSONObject(i);
        if (e == null) continue;
        String summary = shortEffect(e);
        if (!summary.isEmpty() && !parts.contains(summary)) parts.add(summary);
      }
    if (!parts.isEmpty()) return TextUtils.join(" · ", parts.subList(0, Math.min(2, parts.size())));
    return effect(sk)
        .replace("맞은 상대의", "상대")
        .replace("우리 팀의", "우리 팀")
        .replace("체력을 회복해요.", "체력 회복")
        .replace("방어를 낮추고", "방어 감소 ·")
        .replace(" 동안 ", " ")
        .replace("추가 효과 없음", "");
  }

  static String shortEffect(JSONObject effect) {
    String duration = effect.optInt("turns") > 0 ? " · " + effect.optInt("turns") + "턴" : "";
    return switch (effect.optString("kind")) {
      case "buff" ->
          (effect.optString("stat").equals("spd")
                  ? "속도"
                  : effect.optString("stat").equals("def") ? "방어" : "공격")
              + " "
              + (effect.optInt("pct") > 0 ? "+" : "")
              + effect.optInt("pct")
              + "%"
              + duration;
      case "status" ->
          (switch (effect.optString("status")) {
                case "burn" -> "화상";
                case "freeze" -> "빙결";
                case "sleep" -> "수면";
                case "bind" -> "속박";
                default -> "상태 이상";
              })
              + " "
              + effect.optInt("chance", 100)
              + "%"
              + duration;
      case "heal" ->
          (effect.optString("target").equals("bench") ? "대기 아군 " : "체력 ")
              + effect.optInt("pct")
              + "% 회복";
      case "guard" -> "받는 피해 -" + effect.optInt("pct") + "%" + duration;
      case "strip" -> "상대 강화 해제";
      case "protect" -> "이번 턴 공격 방어";
      case "cleanse" -> "상태 이상 해제";
      case "regen" -> "매턴 체력 " + effect.optInt("pct") + "% 회복" + duration;
      case "endure" -> "체력 1로 버팀" + duration;
      case "revive" -> "아군 체력 " + effect.optInt("pct") + "%로 부활";
      default -> "";
    };
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
    int current = currentPower(sk);
    if (current <= 0) return "보조 기술";
    JSONObject p = sk.optJSONObject("preview");
    String attr = p == null ? "" : p.optString("attrName");
    String cat = p == null ? "" : p.optString("category").equals("mag") ? "마법" : "물리";
    double mult = p == null ? 1 : p.optDouble("multiplier", 1);
    String power = "위력" + current + (sk.optInt("hits", 1) > 1 ? "×" + sk.optInt("hits") : "");
    return (attr.isEmpty() ? "" : attr + "·" + cat + "·")
        + power
        + " · ×"
        + java.math.BigDecimal.valueOf(Double.isFinite(mult) && mult > 0 ? mult : 1)
            .stripTrailingZeros()
            .toPlainString()
        + (mult > 1 ? " ▲유리" : mult < 1 ? " ▼불리" : "")
        + (bonusActive(sk) ? " · 조건 발동" : "");
  }

  static int currentPower(JSONObject sk) {
    JSONObject preview = sk.optJSONObject("preview");
    double value = preview == null ? Double.NaN : preview.optDouble("power", Double.NaN);
    return Double.isFinite(value) && value > 0 && value <= Integer.MAX_VALUE
        ? (int) Math.round(value)
        : sk.optInt("power");
  }

  static boolean bonusActive(JSONObject sk) {
    JSONObject preview = sk.optJSONObject("preview");
    return currentPower(sk) > 0 && preview != null && preview.optBoolean("bonusActive");
  }

  static boolean hasExtraEffect(JSONObject sk) {
    JSONArray effects = sk.optJSONArray("effects");
    return (effects != null && effects.length() > 0)
        || sk.optJSONObject("bonus") != null
        || sk.optDouble("pierce") > 0
        || sk.optDouble("drain") > 0
        || sk.optBoolean("crit")
        || sk.optBoolean("priority");
  }

  static String effect(JSONObject sk) {
    if (sk.optString("id").startsWith("sub-") && !hasExtraEffect(sk)) return "";
    String description =
        sk.optString("desc")
            .replaceFirst("(^|,\\s*)위력 \\d+((으로|로) \\d+번)?(,\\s*)?", "$1")
            .replaceFirst(",\\s*$", "")
            .trim();
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
    return reason.contains("이미 적용")
        ? "이미 적용됨"
        : reason.contains("합동기를 이미 사용")
            ? "이번 전투에 사용함"
            : reason.contains("합동기") ? "동료 조건 필요" : reason;
  }
}
