package org.dpdns.gmm;

import java.util.Iterator;
import org.json.*;

/** 서버의 현재 효과 투영을 표시한다. 턴 수를 클라이언트 시계나 로그에서 추측하지 않는다. */
final class BattleEffects {
  static JSONArray current(JSONObject fighter) {
    JSONArray effects = fighter.optJSONArray("effects");
    if (effects != null) return effects;
    JSONArray fallback = new JSONArray();
    JSONObject status = fighter.optJSONObject("status");
    if (status == null) return fallback;
    Iterator<String> ids = status.keys();
    while (ids.hasNext()) {
      String id = ids.next();
      String name =
          switch (id) {
            case "burn" -> "화상";
            case "freeze" -> "빙결";
            case "sleep" -> "수면";
            case "bind" -> "묶임";
            case "rage" -> "땅콩 폭주";
            default -> "";
          };
      if (name.isEmpty()) continue;
      Object value = status.opt(id);
      fallback.put(
          MainActivity.obj(
              "id",
              id,
              "label",
              name,
              "valueText",
              "",
              "kind",
              "status",
              "remainingTurns",
              value instanceof Number ? value : JSONObject.NULL));
    }
    return fallback;
  }

  static String icon(JSONObject effect) {
    return switch (effect.optString("kind")) {
      case "guard" -> "◆";
      case "regen" -> "+";
      case "buff" -> "↑";
      case "debuff" -> "↓";
      default -> "!";
    };
  }

  static String duration(JSONObject effect) {
    return effect.isNull("remainingTurns") || effect.optInt("remainingTurns", -1) < 0
        ? "지속"
        : effect.optInt("remainingTurns") + "턴";
  }

  static String label(JSONObject effect) {
    String value = effect.optString("valueText");
    return icon(effect)
        + " "
        + effect.optString("label")
        + (value.isEmpty() ? "" : " " + value)
        + " · "
        + duration(effect);
  }

  static String compactLabel(JSONObject effect) {
    String value = effect.optString("valueText");
    if (value.startsWith("턴마다 최대 체력 "))
      value = value.substring("턴마다 최대 체력 ".length()).replace(" 피해", "피해/턴").replace(" 회복", "회복/턴");
    value = value.replace(" · ", "·").replace("공격 ", "공격").replace("속도 ", "속도");
    return icon(effect)
        + " "
        + effect.optString("label")
        + (value.isEmpty() ? "" : " " + value)
        + " · "
        + duration(effect);
  }

  static int color(JSONObject effect) {
    return switch (effect.optString("kind")) {
      case "guard" -> 0xff285e83;
      case "regen" -> NativeBattleLog.HEAL;
      case "buff" -> NativeBattleLog.BUFF;
      case "debuff" -> NativeBattleLog.DAMAGE;
      default -> NativeBattleLog.statusColor(effect.optString("id").replaceFirst("^status:", ""));
    };
  }
}
