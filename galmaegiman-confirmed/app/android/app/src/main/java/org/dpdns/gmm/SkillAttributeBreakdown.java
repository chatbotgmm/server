package org.dpdns.gmm;

import org.json.JSONObject;

/** Formats only authoritative preview values, never recomputes combat rules on device. */
final class SkillAttributeBreakdown {
  static boolean complete(JSONObject preview) {
    if (preview == null) return false;
    for (String field : new String[]{"multiplier", "innateMultiplier",
        "sameAttributeMultiplier", "combinedAttributeMultiplier"}) {
      Object raw = preview.opt(field);
      if (!(raw instanceof Number)) return false;
      double value = ((Number) raw).doubleValue();
      if (!Double.isFinite(value) || value < 0) return false;
    }
    return true;
  }
  static String amount(JSONObject preview, String field) {
    return java.math.BigDecimal.valueOf(((Number)preview.opt(field)).doubleValue())
        .setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }
  static String text(JSONObject preview) {
    if (!complete(preview)) return "기존 전투 미리보기 / 추가 속성 보정 정보 없음";
    return "기술 상성 x" + amount(preview, "multiplier")
        + " / 내 속성 x" + amount(preview, "innateMultiplier")
        + " / 일치 보너스 x" + amount(preview, "sameAttributeMultiplier")
        + "\n속성 배율 곱 x" + amount(preview, "combinedAttributeMultiplier")
        + " (최종 피해량 아님)"
        + "\n내 속성 보정은 다른 속성의 공격 기술에도 적용돼요.";
  }
  private SkillAttributeBreakdown() {}
}
