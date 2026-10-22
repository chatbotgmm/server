package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** Recorded server compatibility is required on every frame. Never infer it from app version. */
final class CombatReplayCompatibility {
  static final String CURRENT_EPOCH = "gmm-combat-20261021-v3";
  static final String BLOCKED_LABEL = "이전 버전 전투기록";
  static final String BLOCKED_REASON = "전투 규칙이 변경되어 재생할 수 없습니다";
  static boolean playable(JSONObject record) {
    JSONArray frames = record == null ? null : record.optJSONArray("frames");
    if (frames == null || frames.length() < 2) return false;
    for (int i = 0; i < frames.length(); i++) {
      JSONObject frame = frames.optJSONObject(i);
      if (frame == null || !CURRENT_EPOCH.equals(frame.optString("combatRulesEpoch"))
          || !Boolean.TRUE.equals(frame.opt("replayAvailable"))) return false;
    }
    return true;
  }
  static JSONArray requirePlayableFrames(JSONObject record) {
    if (!playable(record)) throw new IllegalArgumentException(BLOCKED_REASON);
    return record.optJSONArray("frames");
  }
  private CombatReplayCompatibility() {}
}
