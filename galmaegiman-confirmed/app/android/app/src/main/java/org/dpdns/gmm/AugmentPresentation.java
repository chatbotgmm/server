package org.dpdns.gmm;

import org.json.JSONObject;

/** Server metadata only: old saves never acquire a grade from a name or stable ID. */
final class AugmentPresentation {
  static String tier(JSONObject augment) {
    if (augment == null || augment.optInt("version") != 2) return "";
    switch (augment.optString("tier")) {
      case "silver": return "실버";
      case "gold": return "골드";
      case "prismatic": return "프리즘";
      default: return "등급 미확인";
    }
  }
  static boolean prism(JSONObject augment) {
    return augment != null && augment.optInt("version") == 2
        && "prismatic".equals(augment.optString("tier"));
  }
  static int ink(JSONObject augment) {
    if (augment != null && augment.optInt("version") == 2) {
      switch (augment.optString("tier")) {
        case "silver": return 0xff596778;
        case "gold": return 0xff806017;
        case "prismatic": return 0xff735191;
      }
    }
    return 0xff58635d;
  }
  static String name(JSONObject augment) {
    if (augment == null) return "증강체";
    String tier = tier(augment);
    return augment.optString("name", "증강체") + (tier.isEmpty() ? "" : " / " + tier);
  }
  static String detail(JSONObject augment) {
    return augment == null ? "효과 설명을 확인할 수 없어요."
        : augment.optString("desc", "효과 설명을 확인할 수 없어요.");
  }
  private AugmentPresentation() {}
}
