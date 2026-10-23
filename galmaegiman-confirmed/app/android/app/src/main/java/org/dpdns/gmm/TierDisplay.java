package org.dpdns.gmm;

/** 표시 전용. 내부 enum, JSON, 캐시 및 명령은 변경하지 않는다. */
final class TierDisplay {
  private TierDisplay() {}
  static String name(String tier, String original) {
    if ("COMMON".equals(tier)) return "커먼";
    if ("UNCOMMON".equals(tier)) return "레어";
    if ("RARE".equals(tier)) return "에픽";
    if (tier == null || tier.isEmpty()) {
      if ("흔함".equals(original) || "COMMON".equals(original)) return "커먼";
      if ("안흔함".equals(original) || "UNCOMMON".equals(original)) return "레어";
      if ("희귀".equals(original) || "RARE".equals(original)) return "에픽";
    }
    return original == null ? "" : original;
  }
}
