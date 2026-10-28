package org.dpdns.gmm;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** The five temporary art aliases explicitly approved by the user; battle identity is untouched. */
final class StoryBattleArt {
  private StoryBattleArt() {}
  static boolean supported(String id) {
    return !sourceSpecies(id).isEmpty();
  }
  static String sourceSpecies(String id) {
    if (id == null) return "";
    switch (id) {
      case "story_record": return "u01";
      case "story_guide": return "u02";
      case "story_engineer": return "u22";
      case "story_escort": return "u03";
      case "story_dark_first": return "u31";
      default: return "";
    }
  }

  static JSONObject entry(Context context, String id) throws Exception {
    if (!supported(id)) throw new IOException("unknown story art");
    JSONObject manifest;
    try (InputStream in = context.getAssets().open("roster60-story-art/index.json")) {
      manifest = new JSONObject(Roster60Portrait.read(in));
    }
    if (manifest.optInt("schemaVersion") != 1) throw new IOException("story art schema");
    JSONArray entries = manifest.getJSONArray("entries");
    for (int i = 0; i < entries.length(); i++) {
      JSONObject entry = entries.getJSONObject(i);
      if (!id.equals(entry.optString("id"))) continue;
      String source = sourceSpecies(id);
      if (!"approved-temporary-alias".equals(entry.optString("status"))
          || !entry.optBoolean("temporary") || !source.equals(entry.optString("sourceSpeciesId"))
          || !entry.optString("assetSha256").matches("[0-9a-f]{64}"))
        throw new IOException("story art approval");
      JSONObject front = entry.getJSONObject("views").getJSONObject("front");
      if (!(source + "-front.webp").equals(front.optString("file"))) throw new IOException("story art metadata");
      return entry;
    }
    throw new IOException("missing story art alias");
  }
}
