package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** Authored quest recommendation only. Never derives a range from enemy or owned-unit levels. */
final class Roster60RecommendedLevel {
  private Roster60RecommendedLevel() {}
  static String label(JSONObject quest){
    JSONArray levels=quest==null?null:quest.optJSONArray("recommendedLevel");if(levels==null||levels.length()!=2)return "";
    Object first=levels.opt(0),last=levels.opt(1);if(!(first instanceof Number)||!(last instanceof Number))return "";
    double min=((Number)first).doubleValue(),max=((Number)last).doubleValue();
    if(!Double.isFinite(min)||!Double.isFinite(max)||min!=Math.floor(min)||max!=Math.floor(max)||min<1||max>99||min>max)return "";
    return "권장 대원 레벨 Lv. "+(int)min+(min==max?"":"–"+(int)max);
  }
}
