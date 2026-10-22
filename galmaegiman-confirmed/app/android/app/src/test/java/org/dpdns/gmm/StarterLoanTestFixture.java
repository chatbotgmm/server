package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** Existing accounts do not open onboarding; no catch-all API response or client grant. */
final class StarterLoanTestFixture {
  static JSONObject ineligible() {
    return MainActivity.obj("state", "ineligible", "serverNow", 1700000000000L,
        "chooseCount", 2, "durationMs", 1296000000L, "options", new JSONArray(),
        "receipt", JSONObject.NULL, "levelArchives", new JSONArray());
  }
  private StarterLoanTestFixture() {}
}
