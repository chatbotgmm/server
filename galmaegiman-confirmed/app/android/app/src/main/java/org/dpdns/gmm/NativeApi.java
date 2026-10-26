package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntSupplier;
import org.json.JSONObject;

/** 서버의 서명 세션을 사용. 게임 화면과 전투 코드를 HTML로 실행하지 않는다. */
class NativeApi {
  interface Result {
    void done(JSONObject data, String error);
  }

  final ExecutorService pool = Executors.newFixedThreadPool(3);
  final Handler ui = new Handler(Looper.getMainLooper());
  private final SharedPreferences prefs;
  private final IntSupplier activityEpoch;
  private final Object requests = new Object();
  private final Map<ReadKey, PendingCall> pendingReads = new HashMap<>();
  private final Set<PendingCall> pendingCalls = new HashSet<>();
  private final List<Runnable> closeCleanups = new ArrayList<>();
  private long sessionGeneration, writeGeneration;
  private boolean closed;

  NativeApi(Context c) {
    this(c, () -> 0);
  }

  NativeApi(Context c, IntSupplier activityEpoch) {
    prefs = c.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL), Context.MODE_PRIVATE);
    this.activityEpoch = activityEpoch;
  }

  String token() {
    synchronized (requests) {
      return prefs.getString("token", "");
    }
  }

  void token(String t) {
    synchronized (requests) {
      String next = t == null ? "" : t;
      if (!next.equals(token())) sessionGeneration++;
      prefs.edit().putString("token", next).apply();
    }
  }

  /** 연결 생성만 바꿔 실제 헤더·오류·인증 흐름을 시험할 수 있다. */
  HttpURLConnection openConnection(URL url) throws java.io.IOException {
    return (HttpURLConnection) url.openConnection();
  }

  /** UI 응답을 재전달하지 않고 요청별 수명주기 표식만 정리한다. 반환값은 등록 해제다. */
  Runnable whenClosed(Runnable cleanup) {
    synchronized (requests) {
      if (!closed) {
        closeCleanups.add(cleanup);
        return () -> {
          synchronized (requests) {
            closeCleanups.remove(cleanup);
          }
        };
      }
    }
    runCloseCleanup(cleanup);
    return () -> {};
  }

  void call(String path, JSONObject body, Result callback) {
    synchronized (requests) {
      if (closed) return;
      final String requestSession = token();
      final long requestGeneration = sessionGeneration;
      // 쓰기 전의 읽기를 재사용하지 않는다. POST는 내용이 같아도 항상 별도 실행한다.
      if (body != null) writeGeneration++;
      ReadKey key = body == null
          ? new ReadKey(BuildConfig.SERVER_URL, requestSession, requestGeneration,
              activityEpoch.getAsInt(), writeGeneration, path)
          : null;
      PendingCall existing = key == null ? null : pendingReads.get(key);
      if (existing != null) {
        existing.callbacks.add(callback);
        return;
      }
      PendingCall pending = new PendingCall(callback);
      pendingCalls.add(pending);
      if (key != null) pendingReads.put(key, pending);
      pool.execute(
        () -> {
          JSONObject result = null;
          String responseBody = null;
          String error = null;
          HttpURLConnection conn = null;
          int responseStatus = -1;
          try {
            conn = openConnection(new URL(BuildConfig.SERVER_URL + "/my/api/" + path));
            NativeBackend.directConnection(conn);
            conn.setUseCaches(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("x-gmm", "1");
            conn.setRequestProperty("x-gmm-app", "1");
            // 클라이언트 표시 능력만 알린다. V2 활성화는 서버 설정이 결정한다.
            conn.setRequestProperty("x-gmm-augment-version", "2");
            // 표시/입력 지원 선언일 뿐 인증이나 서버 규칙 우회가 아니다.
            conn.setRequestProperty("x-gmm-combat-epoch", CombatReplayCompatibility.CURRENT_EPOCH);
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
            responseStatus = status;
            NativeBackend.requireDirectResponse(status);
            if (BuildConfig.DEBUG && BuildConfig.SERVER_URL.startsWith("http://10.0.2.2"))
              android.util.Log.i("GmmNative", path + " HTTP " + status);
            try (java.io.InputStream in =
                status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
              if (in == null) throw new java.io.IOException();
              java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
              byte[] buffer = new byte[8192];
              int n;
              while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
              responseBody = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
              result = new JSONObject(responseBody);
            }
            if (status >= 400) {
              error = result.optString("error", "서버에 연결하지 못했어요.");
              if ("COMBAT_EPOCH_UNSUPPORTED".equals(result.optString("code")))
                error = "전투 규칙이 업데이트됐어요. 최신 앱으로 업데이트한 뒤 다시 시도해 주세요.";
              if (status == 401) {
                synchronized (requests) {
                  if (!closed && requestGeneration == sessionGeneration
                      && requestSession.equals(token())) token("");
                }
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
          // 메뉴 구버전 호환에만 실제 HTTP 상태를 전달한다. 401/403/네트워크 오류는 표식이 아니다.
          error = NativeMenuTestPolicy.classify(path, responseStatus, error);
          List<Result> callbacks;
          synchronized (requests) {
            // 쓰기 도중 시작한 읽기도 쓰기 완료 후의 새 읽기와 합치지 않는다.
            if (body != null) writeGeneration++;
            if (key != null && pendingReads.get(key) == pending) pendingReads.remove(key);
            pendingCalls.remove(pending);
            callbacks = new ArrayList<>(pending.callbacks);
            pending.callbacks.clear();
            if (closed) return;
          }
          // JSON 객체는 중첩 배열까지 구독자별 소유다. 큰 응답도 UI 스레드에서 복사하지 않는다.
          for (Result subscriber : callbacks) {
            JSONObject ownResult = result;
            result = null;
            if (ownResult == null && responseBody != null) {
              try {
                ownResult = new JSONObject(responseBody);
              } catch (org.json.JSONException invalid) {
                // 최초 파싱도 실패한 응답은 기존 연결 오류를 그대로 전달한다.
              }
            }
            JSONObject d = ownResult;
            String e = error;
            ui.post(() -> {
              synchronized (requests) {
                if (closed) return;
              }
              subscriber.done(d, e);
            });
          }
        });
    }
  }

  void close() {
    List<Runnable> cleanups;
    synchronized (requests) {
      closed = true;
      for (PendingCall pending : pendingCalls) pending.callbacks.clear();
      pendingCalls.clear();
      pendingReads.clear();
      cleanups = new ArrayList<>(closeCleanups);
      closeCleanups.clear();
    }
    pool.shutdownNow();
    for (Runnable cleanup : cleanups) runCloseCleanup(cleanup);
  }

  private void runCloseCleanup(Runnable cleanup) {
    try {
      cleanup.run();
    } catch (RuntimeException failed) {
      if (BuildConfig.DEBUG) android.util.Log.w("GmmNative", "Request close cleanup failed");
    }
  }

  private static final class PendingCall {
    final List<Result> callbacks = new ArrayList<>();

    PendingCall(Result callback) {
      callbacks.add(callback);
    }
  }

  /** 완료 응답은 보관하지 않는다. 이 키는 진행 중인 동일 읽기에만 존재한다. */
  private static final class ReadKey {
    final String backend, session, path;
    final long sessionGeneration, writeGeneration;
    final int epoch;

    ReadKey(String backend, String session, long sessionGeneration, int epoch,
        long writeGeneration, String path) {
      this.backend = backend;
      this.session = session;
      this.sessionGeneration = sessionGeneration;
      this.epoch = epoch;
      this.writeGeneration = writeGeneration;
      this.path = path;
    }

    @Override public boolean equals(Object other) {
      if (!(other instanceof ReadKey)) return false;
      ReadKey key = (ReadKey) other;
      return backend.equals(key.backend) && session.equals(key.session) && path.equals(key.path)
          && sessionGeneration == key.sessionGeneration && epoch == key.epoch
          && writeGeneration == key.writeGeneration;
    }

    @Override public int hashCode() {
      int hash = 31 * (31 * backend.hashCode() + session.hashCode()) + path.hashCode();
      hash = 31 * hash + Long.hashCode(sessionGeneration);
      hash = 31 * hash + epoch;
      return 31 * hash + Long.hashCode(writeGeneration);
    }
  }
}
