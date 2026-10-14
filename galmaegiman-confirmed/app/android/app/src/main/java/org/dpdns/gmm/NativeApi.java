package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** 서버의 서명 세션을 사용. 게임 화면과 전투 코드를 HTML로 실행하지 않는다. */
final class NativeApi {
  interface Result {
    void done(JSONObject data, String error);
  }

  final ExecutorService pool = Executors.newFixedThreadPool(3);
  final Handler ui = new Handler(Looper.getMainLooper());
  private final SharedPreferences prefs;

  NativeApi(Context c) {
    prefs = c.getSharedPreferences("native_account", Context.MODE_PRIVATE);
  }

  String token() {
    return prefs.getString("token", "");
  }

  void token(String t) {
    prefs.edit().putString("token", t).apply();
  }

  void call(String path, JSONObject body, Result callback) {
    final String requestSession = token();
    pool.execute(
        () -> {
          JSONObject result = null;
          String error = null;
          HttpURLConnection conn = null;
          try {
            conn =
                (HttpURLConnection)
                    new URL(BuildConfig.SERVER_URL + "/my/api/" + path).openConnection();
            conn.setUseCaches(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("x-gmm", "1");
            conn.setRequestProperty("x-gmm-app", "1");
            if (!requestSession.isEmpty()) conn.setRequestProperty("x-gmm-s", requestSession);
            if (body != null) {
              conn.setRequestMethod("POST");
              conn.setDoOutput(true);
              conn.setRequestProperty("Content-Type", "application/json");
              try (java.io.OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
              }
            }
            int status = conn.getResponseCode();
            if (BuildConfig.DEBUG && BuildConfig.SERVER_URL.startsWith("http://10.0.2.2"))
              android.util.Log.i("GmmNative", path + " HTTP " + status);
            try (java.io.InputStream in =
                status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
              if (in == null) throw new java.io.IOException();
              java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
              byte[] buffer = new byte[8192];
              int n;
              while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
              result = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
            if (status >= 400) {
              error = result.optString("error", "서버에 연결하지 못했어요.");
              if (status == 401) {
                if (requestSession.equals(token())) token("");
                error = "로그인이 만료됐어요. 다시 로그인해 주세요.";
              }
            }
          } catch (Exception e) {
            if (BuildConfig.DEBUG && BuildConfig.SERVER_URL.startsWith("http://10.0.2.2"))
              android.util.Log.w("GmmNative", path + " " + e.getClass().getSimpleName());
            error = "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.";
          } finally {
            if (conn != null) conn.disconnect();
          }
          JSONObject d = result;
          String e = error;
          ui.post(() -> callback.done(d, e));
        });
  }

  void close() {
    pool.shutdownNow();
  }
}
