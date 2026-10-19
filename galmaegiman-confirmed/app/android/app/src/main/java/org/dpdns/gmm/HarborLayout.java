package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** assets/harbor-v1/layout.json(galmaemiman-runtime-v1)을 한 번 읽어 화면별 요소 목록을 돌려준다. */
final class HarborLayout {
  private static JSONObject cached;

  static synchronized JSONObject root(MainActivity h) throws Exception {
    if (cached == null)
      try (java.io.InputStream in = h.getAssets().open("harbor-v1/layout.json")) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
        cached = new JSONObject(bytes.toString("UTF-8"));
      }
    return cached;
  }

  static JSONArray screen(MainActivity h, String profile, String screen) throws Exception {
    return root(h).getJSONObject("profiles").getJSONObject(profile).getJSONObject("screens").getJSONArray(screen);
  }

  private HarborLayout() {}
}
