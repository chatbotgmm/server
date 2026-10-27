package org.dpdns.gmm;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONObject;

/** One durable intent per account. Unknown network results retain the same request id. */
final class Roster60StoryJournal {
  final SharedPreferences preferences;
  final String key;
  Roster60StoryJournal(SharedPreferences preferences, String accountScope) {
    this.preferences = preferences;
    key = NativeProgression.accountKey(BuildConfig.SERVER_URL + "|story-v1|" + accountId(accountScope)) + ":pending";
  }
  JSONObject pending() {
    String raw = preferences.getString(key, "");
    if (raw.isEmpty()) return null;
    try {
      JSONObject value = new JSONObject(raw);
      if (!allowed(value.optString("operation")) || value.optJSONObject("body") == null
          || value.optJSONObject("body").optString("requestId").isEmpty()) throw new IllegalStateException();
      return value;
    } catch (Exception invalid) { throw new IllegalStateException("저장된 이야기 요청 확인이 필요합니다."); }
  }
  JSONObject prepare(String operation, JSONObject body) {
    if (!allowed(operation)) throw new IllegalArgumentException("STORY_OPERATION_INVALID");
    JSONObject prior = pending();
    if (prior != null) return prior;
    try {
      JSONObject safe = new JSONObject(body.toString());
      safe.put("requestId", UUID.randomUUID().toString());
      JSONObject intent = MainActivity.obj("operation", operation, "body", safe);
      if (!preferences.edit().putString(key, intent.toString()).commit())
        throw new IllegalStateException("이야기 요청을 기기에 저장하지 못했습니다.");
      return intent;
    } catch (org.json.JSONException invalid) { throw new IllegalArgumentException(invalid); }
  }
  boolean restore(JSONObject intent){
    if(pending()!=null)return false;
    JSONObject body=intent==null?null:intent.optJSONObject("body");
    if(intent==null||!allowed(intent.optString("operation"))||body==null||body.optString("requestId").isEmpty())return false;
    return preferences.edit().putString(key,intent.toString()).commit();
  }
  static String accountId(String token){int dot=token.indexOf('.');return dot<0?token:token.substring(0,dot);}
  boolean acknowledge(JSONObject intent) {
    JSONObject current = pending();
    if (current == null || !current.optString("operation").equals(intent.optString("operation"))) return false;
    if (!current.optJSONObject("body").optString("requestId")
        .equals(intent.optJSONObject("body").optString("requestId"))) return false;
    return preferences.edit().remove(key).commit();
  }
  static boolean allowed(String operation) {
    return Arrays.asList("open", "advance", "accept", "close", "battle/create", "adventure/create", "adventure/return").contains(operation);
  }
  static boolean definitive(String error) {
    return error != null && (error.startsWith("MENU_LOCKED") || error.startsWith("STORY_")
        || Arrays.asList("STALE_REVISION", "INVALID_REQUEST", "REQUEST_ID_REUSED", "UNIT_UNAVAILABLE",
            "UNIT_NOT_FOUND", "INVALID_PARTY", "ADVENTURE_NOT_FOUND", "ADVENTURE_STATE_CHANGED",
            "ADVENTURE_BATTLE_ACTIVE", "RETURN_ONLY_OUTSIDE_BATTLE", "BATTLE_NOT_FOUND", "CATALOG_CHANGED",
            "UNIT_STATE_CHANGED", "CAP_XP_POLICY_REQUIRED", "BATTLE_POLICY_PENDING", "ROSTER_CATALOG_PENDING").contains(error));
  }
}
