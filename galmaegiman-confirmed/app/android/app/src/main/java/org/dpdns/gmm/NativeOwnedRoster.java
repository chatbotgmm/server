package org.dpdns.gmm;

import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Presentation-only owned catalogue normalization. Saved party order is never passed here. */
final class NativeOwnedRoster {
  private NativeOwnedRoster() {}

  static JSONArray levelOrdered(JSONArray source) {
    return new JSONArray(levelOrderedCandidates(source, "ownedId"));
  }

  static ArrayList<JSONObject> levelOrderedCandidates(JSONArray source, String identityField) {
    ArrayList<JSONObject> result = new ArrayList<>();
    HashSet<String> seen = new HashSet<>();
    if (source == null) return result;
    for (int i = 0; i < source.length(); i++) {
      JSONObject unit = source.optJSONObject(i);
      if (unit == null) continue;
      Object identityValue = unit.opt(identityField);
      if (!(identityValue instanceof String)) continue;
      String identity = (String) identityValue;
      // A missing owned identifier cannot be selected or submitted safely. Never use speciesId
      // as a fallback: two separate owned individuals of the same species must remain visible.
      if (!identity.isEmpty() && seen.add(identity)) result.add(unit);
    }
    // List.sort is stable, retaining server order for equal levels. Input objects stay unchanged.
    result.sort((a, b) -> Integer.compare(b.optInt("level", 0), a.optInt("level", 0)));
    return result;
  }
}
