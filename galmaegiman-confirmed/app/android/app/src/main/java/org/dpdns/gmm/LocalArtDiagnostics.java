package org.dpdns.gmm;

/** Public asset identity and exception type only; never log exception messages or state. */
final class LocalArtDiagnostics {
  static final String TAG = "GmmLocalArt";
  private LocalArtDiagnostics() {}

  static String safeKey(String key) {
    if (key != null && !key.contains("..") && key.matches(
        "(?:roster60-art|roster60-story-art|ui-v3|game-art|native|battle-v1)/[A-Za-z0-9_./-]+")) return key;
    if (key != null && key.matches(
        "(?:u(?:0[1-9]|[1-5][0-9]|60)|story_(?:record|guide|engineer|escort|dark_first))/(?:front|rear)")) return key;
    return "<invalid>";
  }

  static void failed(String key, Throwable failure) {
    android.util.Log.w(TAG, "asset=" + safeKey(key) + " exception="
        + (failure == null ? "DecodeFailure" : failure.getClass().getSimpleName()));
  }
}
