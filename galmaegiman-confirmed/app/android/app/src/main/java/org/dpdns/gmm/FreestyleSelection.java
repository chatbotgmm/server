package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** A local draft. Only confirmation serializes the exact server-issued scope. */
final class FreestyleSelection {
  final JSONObject scope;
  String selected = "";
  FreestyleSelection(JSONObject scope) { this.scope = scope; }
  boolean valid() {
    return scope != null && "own-attribute".equals(scope.optString("kind"))
        && !scope.optString("selectionId").isEmpty() && !scope.optString("phaseId").isEmpty()
        && !scope.optString("targetKey").isEmpty();
  }
  boolean allows(String code) {
    if (!valid() || code == null || code.isEmpty()) return false;
    JSONArray attributes = scope.optJSONArray("attributes");
    if (attributes == null || attributes.length() > 10) return false;
    for (int i = 0; i < attributes.length(); i++) if (code.equals(attributes.opt(i))) return true;
    return false;
  }
  void select(String code) { if (allows(code)) selected = code; }
  void cancel() { selected = ""; }
  boolean sameScope(JSONObject current) {
    if (!valid() || current == null) return false;
    for (String key : new String[]{"kind", "selectionId", "phaseId", "targetKey"})
      if (!scope.optString(key).equals(current.optString(key))) return false;
    return true;
  }
  JSONObject confirmed() {
    if (!allows(selected)) return null;
    return MainActivity.obj("selectionId",scope.optString("selectionId"),
        "phaseId",scope.optString("phaseId"),"targetKey",scope.optString("targetKey"),
        "attribute",selected);
  }
}
