package org.dpdns.gmm;

import android.graphics.*;
import android.os.SystemClock;
import android.widget.ImageView;
import org.json.JSONObject;

/** Instance-only display; never decides eligibility, grants or removes a unit. */
final class TemporaryUnitPresentation {
  static final float SATURATION = .72f;
  private static long serverAnchor, elapsedAnchor;
  static void sync(long serverNow) {
    if (serverNow > 0) { serverAnchor = serverNow; elapsedAnchor = SystemClock.elapsedRealtime(); }
  }
  static void reset() { serverAnchor = 0; elapsedAnchor = 0; }
  static long now() { return serverAnchor > 0 ? serverAnchor + Math.max(0, SystemClock.elapsedRealtime() - elapsedAnchor) : 0; }
  static boolean temporary(JSONObject unit) { return unit != null && Boolean.TRUE.equals(unit.opt("temporary")); }
  // Server-supplied aggregate counts only. Never infer from a species ID.
  static boolean countedAggregate(JSONObject unit) {
    return unit != null && !unit.has("temporary")
        && unit.opt("temporaryCount") instanceof Number && unit.opt("permanentCount") instanceof Number
        && unit.optInt("temporaryCount") >= 0 && unit.optInt("permanentCount") >= 0;
  }
  static JSONObject artUnit(JSONObject unit) {
    if (!countedAggregate(unit) || unit.optInt("temporaryCount") == 0 || unit.optInt("permanentCount") > 0)
      return unit;
    try {
      JSONObject presentation = new JSONObject(unit.toString());
      presentation.put("temporary", true);
      long earliest = 0;
      org.json.JSONArray instances = unit.optJSONArray("temporaryInstances");
      if (instances != null) for (int i=0;i<instances.length();i++) {
        JSONObject instance = instances.optJSONObject(i);
        if (!temporary(instance)) continue;
        long expiry = instance.optLong("expiresAt");
        if (expiry > 0 && (earliest == 0 || expiry < earliest)) earliest = expiry;
      }
      if (earliest > 0) presentation.put("expiresAt", earliest);
      return presentation;
    } catch (org.json.JSONException e) { return unit; }
  }
  static String label(JSONObject unit) { return label(unit, now()); }
  static String label(JSONObject unit, long serverNow) {
    if (countedAggregate(unit) && unit.optInt("temporaryCount") > 0) {
      if (unit.optInt("permanentCount") > 0)
        return "임시 포함 " + unit.optInt("temporaryCount") + " / 영구 " + unit.optInt("permanentCount");
      JSONObject display = artUnit(unit);
      return display == unit ? "임시 / 기간 확인 필요" : label(display, serverNow);
    }
    if (!temporary(unit)) return "";
    long expiry = unit.optLong("expiresAt", 0);
    if (expiry <= 0 || serverNow <= 0) return "임시 / 기간 확인 필요";
    long remaining = expiry - serverNow;
    if (remaining <= 0) return "임시 / 기간 만료";
    if (remaining >= 86400000L) return "임시 / " + ((remaining + 86399999L) / 86400000L) + "일 남음";
    if (remaining >= 3600000L) return "임시 / " + ((remaining + 3599999L) / 3600000L) + "시간 남음";
    return "임시 / " + Math.max(1, (remaining + 59999L) / 60000L) + "분 남음";
  }
  static String suffix(JSONObject unit) { String text = label(unit); return text.isEmpty() ? "" : " / " + text; }
  static void append(ColorMatrix matrix, JSONObject unit) {
    if (temporary(unit)) { ColorMatrix loan = new ColorMatrix(); loan.setSaturation(SATURATION); matrix.postConcat(loan); }
  }
  static ColorMatrixColorFilter filter(JSONObject unit) {
    if (!temporary(unit)) return null;
    ColorMatrix matrix = new ColorMatrix(); append(matrix, unit); return new ColorMatrixColorFilter(matrix);
  }
  static void apply(ImageView image, JSONObject unit) { image.setColorFilter(filter(unit)); }
  static void badge(Canvas canvas, JSONObject unit, RectF bounds, float textSize, Typeface font) { badge(canvas, label(unit), bounds, textSize, font); }
  static void badge(Canvas canvas, String text, RectF bounds, float textSize, Typeface font) {
    if (text.isEmpty() || bounds.width() < textSize * 4 || bounds.height() < textSize * 2) return;
    Paint label = new Paint(Paint.ANTI_ALIAS_FLAG); label.setTextSize(textSize); label.setTypeface(font);
    String shown = text;
    if (label.measureText(shown) > bounds.width() - 8) {
      while (shown.length() > 0 && label.measureText(shown + "...") > bounds.width() - 8)
        shown = shown.substring(0, shown.length() - 1);
      shown += "...";
    }
    float baseline = bounds.top - label.ascent() + 3;
    label.setColor(0xeef3e9d3); canvas.drawRect(bounds.left, bounds.top, bounds.left + label.measureText(shown) + 8, baseline + label.descent() + 3, label);
    label.setColor(0xff193746); canvas.drawText(shown, bounds.left + 4, baseline, label);
  }
}
