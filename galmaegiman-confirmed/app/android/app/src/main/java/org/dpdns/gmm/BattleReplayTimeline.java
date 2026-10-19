package org.dpdns.gmm;

import org.json.*;

/** Pure deterministic cursor. No API, activity, combat engine or economy dependency. */
final class BattleReplayTimeline {
  private final JSONArray frames;
  private int position;
  private boolean playing, disposed;
  BattleReplayTimeline(JSONArray source) {
    try { frames = source==null?new JSONArray():new JSONArray(source.toString()); }
    catch (JSONException e) { throw new IllegalArgumentException(e); }
  }
  int size() { return frames.length(); }
  int position() { return position; }
  boolean playing() { return playing && !disposed; }
  JSONObject current() { return size()==0 || frames.optJSONObject(position)==null ? new JSONObject() : BattleReplayStore.copy(frames.optJSONObject(position)); }
  void play() { if (!disposed && position < size()-1) playing=true; }
  void pause() { playing=false; }
  void seek(int index) { if (disposed) return; pause(); position=Math.max(0,Math.min(size()-1,index)); }
  void tick() { if (!playing()) return; if (position<size()-1) position++; if (position>=size()-1) pause(); }
  void close() { pause(); disposed=true; }
}
