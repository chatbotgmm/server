package org.dpdns.gmm;

import org.json.JSONException;
import org.json.JSONObject;

/** Display projection only. Owned instance keys must remain untouched in commands. */
final class PreparationIdentity {
  private PreparationIdentity() {}

  static JSONObject artwork(JSONObject candidate) throws JSONException {
    JSONObject display = new JSONObject(candidate.toString());
    String key = candidate.optString("id");
    if (!key.matches("o[1-9][0-9]*")) return display;
    String species = candidate.optString("characterId");
    String image = candidate.optString("image");
    java.util.regex.Matcher thumbnail = java.util.regex.Pattern
        .compile("^/thumbs/([A-Z][1-9][0-9]*)\\.jpg$").matcher(image);
    String pictured = thumbnail.matches() ? thumbnail.group(1) : "";
    // Rank reincarnations omit characterId; the canonical thumbnail names their
    // original species. Never infer from name, list position or inheritedFrom.
    if (species.isEmpty()) species = pictured;
    // Explicit disagreement is a malformed identity, not permission to guess.
    if (!species.matches("[A-Z][1-9][0-9]*")
        || (!pictured.isEmpty() && !pictured.equals(species))) return display;
    display.put("id", species);
    return display;
  }
}
