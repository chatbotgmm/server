package org.dpdns.gmm;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Draft: authenticated server-owned PvP. Never resolves a turn on the device. */
final class NativeRealtime {
  final MainActivity h;
  final SharedPreferences prefs;
  boolean resumed, closed, reading, writing, cancelling, preparing, inspecting, playing;
  int generation;
  boolean queueRecoveryNeeded;
  String owner = "", mode = "rank", attempt = "", token = "", room = "";
  String cancelRequest = "", dismissed = "";
  JSONObject latest, pendingPlayback;
  long serverAtReceive, receivedElapsed;
  TextView queueText, queueElapsed, queueMode, queuePolicyView, clock;
  long queueObservedAt, queueWaitReceivedAt, queueWaitBase = -1;
  Button cancelButton;
  final ArrayList<View> phaseActions = new ArrayList<>();
  int preparationSlot, preparationPage;
  String preparationPhase = "";
  static final int PREPARATION_PAGE_SIZE = 3;
  boolean queueStopped, queueConfirmed, queueRetryPending;
  int queueRetryCount, waitingCopyIndex;
  Runnable queueRetry;
  static final String[] WAITING_LINES = {"갈매기들이 상대를 찾고 있어요", "날개를 풀고, 한 판 준비!", "오늘의 멋진 승부를 기다려요"};
  final Runnable poll = this::refresh;
  final Runnable tick = new Runnable() {
    public void run() {
      if (!resumed || closed) return;
      if (queueElapsed != null && "realtimeQueue".equals(h.screen)) {
        queueElapsed.setVisibility(queueConfirmed && !queueStopped && queueWaitBase >= 0 ? View.VISIBLE : View.GONE);
        if (queueConfirmed && !queueStopped && queueWaitBase >= 0)
          queueElapsed.setText(queueTimeLabel(Math.max(0, (queueWaitBase + SystemClock.elapsedRealtime() - queueWaitReceivedAt) / 1000)));
      }
      if (clock != null && latest != null && clock.isAttachedToWindow()) {
        long deadline = latest.optLong("deadline", 0);
        long now = serverAtReceive + SystemClock.elapsedRealtime() - receivedElapsed;
        String remaining = deadline > 0 ? Math.max(0, (deadline - now + 999) / 1000) + "초 · " : "";
        String state = latest.optJSONObject("result") != null ? "결과 확인 중"
            : "accept".equals(latest.optString("phase")) ? "대전 수락 대기"
            : "prepare".equals(latest.optString("phase")) ? "팀 편성 · 준비 완료를 눌러 주세요"
            : writing ? "선택 전송 중" : latest.optBoolean("mySubmitted") ? "상대 선택 대기"
            : latest.optBoolean("canSubmit") ? "행동 선택" : "상대 선택 대기";
        BattleView battleView = currentView();
        if (battleView != null) {
          boolean ownInput = latest.optBoolean("canSubmit") && !latest.optBoolean("mySubmitted")
              && !writing && !playing && !battleView.playback.playing
              && latest.optJSONObject("result") == null && "action".equals(battleView.battle.optString("phase"));
          clock.setVisibility(ownInput && deadline > 0 ? View.VISIBLE : View.GONE);
          long seconds = Math.max(0, (deadline - now + 999) / 1000);
          clock.setText(seconds > 0 ? Long.toString(seconds) : "결과 확인 중");
          if (ownInput && deadline > 0 && now >= deadline) battleView.blockExpiredInput();
        } else {
          clock.setVisibility(View.VISIBLE);
          JSONObject acceptance = latest.optJSONObject("acceptance");
          JSONObject preparation = latest.optJSONObject("preparation");
          boolean accepting = "accept".equals(latest.optString("phase"));
          boolean selecting = "prepare".equals(latest.optString("phase"));
          boolean input = accepting ? acceptance != null && !acceptance.optBoolean("accepted")
              : selecting && preparation != null && !preparation.optBoolean("ready");
          clock.setText(latest.optJSONObject("result") != null ? "결과 확인 중"
              : writing ? "선택 전송 중"
              : input && deadline > 0 ? now >= deadline ? "결과 확인 중" : remaining.replace(" · ", "")
              : accepting ? "상대 수락 대기" : selecting ? "상대 준비 대기" : state);
          updatePhaseInputs();
        }
      }
      h.api.ui.postDelayed(this, 250);
    }
  };

  NativeRealtime(MainActivity host) {
    h = host;
    prefs = h.getSharedPreferences("native_realtime", 0);
  }
  // casual-ai-disclosure-v3: this label follows the authoritative room kind.
  static boolean isAiRoom(JSONObject room) {
    return room != null && "AI".equals(room.optString("opponentKind"));
  }
  static final String AI_NOTICE = "AI 대전 · 일반 MMR에 반영되지 않아요";
  static String id() { return UUID.randomUUID().toString(); }
  static boolean isBattle(JSONObject b) { return b != null && b.optBoolean("realtime"); }
  String account() {
    try {
      byte[] d = java.security.MessageDigest.getInstance("SHA-256")
          .digest(h.api.token().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte v : d) out.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
      return out.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
  boolean valid(int g, String a) {
    return !closed && !h.isFinishing() && !h.isDestroyed() && g == generation
        && !h.api.token().isEmpty() && a.equals(account());
  }
  void save() {
    prefs.edit().putString("owner", owner).putString("attempt", attempt)
        .putString("token", token).putString("room", room)
        .putString("mode", mode).putString("cancelRequest", cancelRequest)
        .putBoolean("cancelling", cancelling).putBoolean("queueRecoveryNeeded", queueRecoveryNeeded).putString("dismissed", dismissed).apply();
  }
  void loadSaved() {
    String current = account();
    if (!current.equals(owner)) {
      generation++; reading = writing = preparing = inspecting = playing = false;
      if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
      queueRetryPending = false; queueStopped = false; queueConfirmed = false;
      queueRetryCount = 0; queueWaitBase = -1;
      latest = pendingPlayback = null;
      owner = current;
      boolean same = current.equals(prefs.getString("owner", ""));
      attempt = same ? prefs.getString("attempt", "") : "";
      token = same ? prefs.getString("token", "") : "";
      room = same ? prefs.getString("room", "") : "";
      mode = same ? prefs.getString("mode", "rank") : "rank";
      cancelRequest = same ? prefs.getString("cancelRequest", "") : "";
      cancelling = same && prefs.getBoolean("cancelling", false);
      queueRecoveryNeeded = same && prefs.getBoolean("queueRecoveryNeeded", false);
      dismissed = same ? prefs.getString("dismissed", "") : "";
      if (!same) save();
    }
  }
  void resume() {
    resumed = true;
    h.api.ui.removeCallbacks(tick); h.api.ui.post(tick);
    if (!h.api.token().isEmpty() && h.state.has("me")) loadSaved();
    if (queueRetryPending && queueRetry != null) {
      h.api.ui.removeCallbacks(queueRetry); h.api.ui.postDelayed(queueRetry, 1000);
    } else recover();
  }
  void recover() {
    if (!resumed || closed || h.api.token().isEmpty() || !h.state.has("me")) return;
    loadSaved();
    if (queueStopped && room.isEmpty() && !cancelling) return;
    if (reading || writing || preparing) return;
    if (attempt.isEmpty() && room.isEmpty() && !cancelling && !queueRecoveryNeeded) return;
    if (queueRecoveryNeeded && attempt.isEmpty() && room.isEmpty()) {
      inspectCurrent(false); return;
    }
    if (cancelling && !attempt.isEmpty() && !token.isEmpty()) { sendCancel(); return; }
    refresh();
  }
  void pause() {
    resumed = false;
    h.api.ui.removeCallbacks(poll); h.api.ui.removeCallbacks(tick);
    if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
    // The server lease/deadline continues. Backgrounding never surrenders.
  }
  void close() { pause(); closed = true; generation++; }
  void schedule() {
    h.api.ui.removeCallbacks(poll);
    if (resumed && !closed && (!queueStopped || !room.isEmpty()) && (!attempt.isEmpty() || !room.isEmpty())) h.api.ui.postDelayed(poll, 1000);
  }
  void begin(String selectedMode) {
    if (h.api.token().isEmpty()) { h.login(); return; }
    loadSaved();
    if (preparing || inspecting) return;
    if (!room.isEmpty() || !attempt.isEmpty() || cancelling) { showQueue("진행 상태 확인 중…"); recover(); return; }
    if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
    queueRetryPending = false; queueStopped = queueConfirmed = false; queueRetryCount = 0; waitingCopyIndex = 0;
    generation++; mode = selectedMode; preparing = true; writing = reading = false;
    latest = null; dismissed = ""; queueWaitBase = -1; queueObservedAt = SystemClock.elapsedRealtime(); save(); showQueue("진행 중인 매칭 확인 중…");
    inspectCurrent(true);
  }
  void inspectCurrent(boolean startWhenIdle) {
    if (inspecting) return;
    inspecting = preparing = true;
    final int g = generation; final String a = owner;
    h.api.call("pvp/queue/current", null, (r, e) -> {
      if (!valid(g, a)) return;
      inspecting = preparing = false;
      if (e != null) { queueFailure(e, () -> inspectCurrent(startWhenIdle)); return; }
      queueRetryCount = 0;
      String status = r.optString("status");
      queueRecoveryNeeded = false;
      if (status.equals("idle") || status.equals("cancelled") || status.equals("expired")) {
        if (cancelling || !startWhenIdle) { clearQueue(); h.load("modes"); }
        else prepareAttempt();
      } else acceptQueue(r);
    });
  }
  void prepareAttempt() {
    preparing = true; showQueue("대전 연결 준비 중…");
    final int g = generation; final String a = owner;
    h.api.call("pvp/queue/attempt", MainActivity.obj("mode", mode, "protocol", 3), (r, e) -> {
      if (!valid(g, a)) return;
      preparing = false;
      if (e != null) { queueFailure(e, this::prepareAttempt); return; }
      queueRetryCount = 0;
      attempt = r.optString("attemptId"); token = r.optString("token");
      if (attempt.isEmpty() || token.isEmpty()) { queueError("매칭 준비 응답을 확인하지 못했어요."); return; }
      save(); join();
    });
  }
  void join() {
    if (cancelling) { sendCancel(); return; }
    writing = true; final int g = generation; final String a = owner;
    showQueue("매칭 신청을 보내고 있어요…");
    h.api.call("pvp/queue/join", MainActivity.obj("attemptId", attempt, "token", token), (r, e) -> {
      if (!valid(g, a)) return;
      writing = false;
      if (cancelling) { sendCancel(); return; }
      if (e != null) { queueFailure(e, this::refresh); return; }
      acceptQueue(r);
    });
  }
  boolean back() {
    if ("realtimeAccept".equals(h.screen)) {
      if (latest != null && latest.optJSONObject("acceptance") != null
          && !latest.optJSONObject("acceptance").optBoolean("accepted")) {
        final JSONObject shown = copy(latest);
        new GameDialog(h).setTitle("매칭을 거절할까요?")
            .setMessage("수락 전 거절은 패배나 점수 차감으로 기록되지 않아요.")
            .setPositiveButton("거절", (d, w) -> acceptMatch(shown, false))
            .setNegativeButton("돌아가기", null).show();
      }
      return true;
    }
    if ("realtimePrepare".equals(h.screen)) {
      new GameDialog(h).setTitle("대전에서 나갈까요?")
          .setMessage("매칭 후 나가면 패배로 기록돼요.")
          .setPositiveButton("나가기", (d, w) -> surrender())
          .setNegativeButton("계속 편성", null).show();
      return true;
    }
    if (!"realtimeQueue".equals(h.screen)) return false;
    cancel(); return true;
  }
  void cancel() {
    if (cancelling) {
      queueRetryCount = 0; queueStopped = false;
      if (!writing && !inspecting) sendCancel();
      return;
    }
    if (!room.isEmpty()) { refresh(); return; }
    if (inspecting) {
      cancelling = true; if (cancelRequest.isEmpty()) cancelRequest = id();
      save(); showQueue("매칭 취소 확인 중…"); return;
    }
    if (attempt.isEmpty() && token.isEmpty()) {
      // Attempt issuance is inert: invalidating this response cannot leave a queue entry.
      generation++; preparing = false; writing = reading = false;
      clearQueue(); h.load("modes"); return;
    }
    cancelling = true;
    if (cancelRequest.isEmpty()) cancelRequest = id();
    generation++; writing = reading = false;
    h.api.ui.removeCallbacks(poll); save();
    showQueue("매칭 취소 확인 중…"); sendCancel();
  }
  void sendCancel() {
    if (writing || closed) return;
    if (attempt.isEmpty() || token.isEmpty()) { inspectCurrent(false); return; }
    writing = true; final int g = generation; final String a = owner;
    h.api.call("pvp/queue/cancel", MainActivity.obj("attemptId", attempt, "token", token,
        "requestId", cancelRequest), (r, e) -> {
      if (!valid(g, a)) return;
      writing = false;
      if (e != null) { queueFailure(e, this::sendCancel); return; }
      acceptQueue(r);
    });
  }
  void clearQueue() {
    if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
    queueRetryPending = false;
    attempt = token = cancelRequest = ""; cancelling = false; preparing = false; save();
  }
  static String queueTimeLabel(long seconds) {
    return String.format(java.util.Locale.KOREA, "대기 시간 %02d:%02d", seconds / 60, seconds % 60);
  }
  /** A bounded navy desk leaves the real harbor scene visible around every side. */
  LinearLayout focusedPanel(String heading, String screen, String tag) {
    h.base(heading, screen);
    AppSkin.apply(h, "training");
    h.page.removeAllViews(); h.page.setGravity(Gravity.CENTER);
    phaseActions.clear(); clock = null;
    FrameLayout stage = new FrameLayout(h);
    h.page.addView(stage, new LinearLayout.LayoutParams(-1, -1));
    int width = h.root.getWidth() > 0 ? h.root.getWidth() - h.root.getPaddingLeft() - h.root.getPaddingRight()
        : h.dp(h.getResources().getConfiguration().screenWidthDp);
    int height = h.root.getHeight() > 0 ? h.root.getHeight() - h.root.getPaddingTop() - h.root.getPaddingBottom()
        : h.dp(h.getResources().getConfiguration().screenHeightDp);
    LinearLayout panel = new LinearLayout(h);
    panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER);
    panel.setPadding(h.dp(20), h.dp(14), h.dp(20), h.dp(14));
    panel.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV)); panel.setTag(tag);
    stage.addView(panel, new FrameLayout.LayoutParams(Math.min(h.dp(620), Math.max(h.dp(280), width - h.dp(96))),
        Math.min(h.dp(360), Math.max(h.dp(240), height - h.dp(40))), Gravity.CENTER));
    h.body = panel;
    return panel;
  }
  TextView lightText(String text, int size) {
    TextView label = h.text(text, size); label.setTextColor(JournalTheme.PAPER);
    label.setGravity(Gravity.CENTER); return label;
  }
  Button phaseButton(String text, String tag, Runnable action, boolean primary) {
    MenuKit kit = new MenuKit(h);
    Button button = primary ? kit.primary(null, text, action) : kit.action(null, text, action);
    button.setTag(tag); button.setTextSize(h.uiText(16));
    button.setMinHeight(h.dp(48)); button.setMinimumHeight(h.dp(48));
    button.setPadding(h.dp(10), h.dp(3), h.dp(10), h.dp(3));
    return button;
  }
  TextView inlineClock() {
    TextView result = lightText("", 16);
    result.setTag("realtime-phase-clock"); result.setTypeface(h.headline);
    result.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    return result;
  }
  void showQueue(String message) {
    if (!"realtimeQueue".equals(h.screen) || queueMode == null || queueElapsed == null
        || queueText == null || queuePolicyView == null || cancelButton == null) {
      h.busy = false;
      if (queueObservedAt == 0) queueObservedAt = SystemClock.elapsedRealtime();
      LinearLayout panel = focusedPanel("대전 찾기", "realtimeQueue", "realtime-queue-paper");
      queueMode = lightText("", 24); queueMode.setTypeface(h.headline);
      panel.addView(queueMode, new LinearLayout.LayoutParams(-1, -2));
      queueElapsed = lightText(queueTimeLabel(0), 17);
      panel.addView(queueElapsed, new LinearLayout.LayoutParams(-1, -2));
      View separator = new View(h); separator.setBackgroundColor(JournalTheme.BRASS);
      LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(h.dp(56), h.dp(2));
      rule.topMargin = h.dp(8); rule.bottomMargin = h.dp(8); panel.addView(separator, rule);
      queueText = lightText(message, 19);
      queueText.setMaxLines(4); queueText.setEllipsize(null);
      queueText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
      panel.addView(queueText, new LinearLayout.LayoutParams(-1, 0, 1));
      queuePolicyView = lightText("갈매기 결투장", 14);
      panel.addView(queuePolicyView, new LinearLayout.LayoutParams(-1, -2));
      cancelButton = phaseButton("매칭 취소", "realtime-queue-cancel", this::cancel, false);
      LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(h.dp(260), h.dp(48));
      action.topMargin = h.dp(8); panel.addView(cancelButton, action);
    }
    queueMode.setText(mode.equals("rank") ? "랭크 · 대전 찾기" : "일반 · 대전 찾기");
    queueElapsed.setVisibility(queueConfirmed && !queueStopped && queueWaitBase >= 0 ? View.VISIBLE : View.GONE);
    boolean waitingCopy = queueConfirmed && !queueStopped && !cancelling;
    String shownCopy = waitingCopy ? WAITING_LINES[waitingCopyIndex] : message;
    queueText.setText(shownCopy);
    queueText.setContentDescription(waitingCopy ? shownCopy + " · 누르면 다음 문구" : shownCopy);
    queueText.setOnClickListener(waitingCopy ? v -> advanceWaitingCopy() : null);
    queueText.setClickable(waitingCopy); queueText.setFocusable(waitingCopy);
    cancelButton.setText(cancelling ? queueStopped ? "매칭 취소" : "취소 확인 중…"
        : queueStopped && attempt.isEmpty() ? "돌아가기" : "매칭 취소");
    cancelButton.setEnabled(room.isEmpty() && (!cancelling || queueStopped));
  }
  void advanceWaitingCopy() {
    if (!queueConfirmed || queueStopped || cancelling || queueText == null
        || !"realtimeQueue".equals(h.screen)) return;
    waitingCopyIndex = (waitingCopyIndex + 1) % WAITING_LINES.length;
    queueText.setText(WAITING_LINES[waitingCopyIndex]);
    queueText.setContentDescription(WAITING_LINES[waitingCopyIndex] + " · 누르면 다음 문구");
  }
  void queueError(String error) { queueFailure(error, this::refresh); }
  static boolean transientQueueError(String error) {
    return "연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.".equals(error);
  }
  void queueFailure(String error, Runnable retry) {
    h.api.ui.removeCallbacks(poll);
    if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
    queueRetryPending = false;
    queueStopped = true; queueConfirmed = false; queueWaitBase = -1;
    boolean transientError = transientQueueError(error);
    String message = error;
    if (error != null && error.contains("입력 정보"))
      message = "매칭 신청이 거절됐어요. 앱과 서버 버전을 확인해 주세요.";
    if (transientError && queueRetryCount < 3) {
      long delay = 2000L << queueRetryCount++;
      message = cancelling ? "취소 여부를 자동으로 확인하고 있어요…" : "연결이 잠시 끊겼어요. 다시 연결하고 있어요…";
      final int g = generation; final String a = owner;
      queueRetryPending = true;
      queueRetry = () -> {
        if (!valid(g, a) || !resumed) return;
        queueRetryPending = false;
        queueStopped = false; retry.run();
      };
      h.api.ui.postDelayed(queueRetry, delay);
    } else if (transientError) {
      message = cancelling ? "취소를 확인하지 못했어요. 연결 후 매칭 취소를 눌러 주세요."
          : "연결을 확인하지 못했어요. 매칭 취소 후 다시 시작해 주세요.";
    }
    showQueue(message);
    if (h.api.token().isEmpty()) h.login();
  }
  void acceptQueue(JSONObject q) {
    if (queueRetry != null) h.api.ui.removeCallbacks(queueRetry);
    queueRetryPending = false;
    String status = q.optString("status");
    queueRecoveryNeeded = false; queueStopped = false;
    if (!status.equals("preparing")) queueRetryCount = 0;
    queueConfirmed = status.equals("waiting");
    if (status.equals("waiting") && !q.isNull("queuedAt") && q.has("serverNow")) {
      queueWaitBase = Math.max(0, q.optLong("serverNow") - q.optLong("queuedAt"));
      queueWaitReceivedAt = SystemClock.elapsedRealtime();
    }
    mode = q.optString("mode", mode);
    if (q.has("attemptId")) attempt = q.optString("attemptId");
    if (q.has("token")) token = q.optString("token");
    if (status.equals("matched")) {
      boolean lostCancelRace = cancelling;
      room = q.optString("roomId"); clearQueue(); save();
      if (room.isEmpty()) { queueError("대전 번호가 없어요. 상태를 다시 확인해 주세요."); return; }
      if (room.equals(dismissed)) { room = ""; save(); return; }
      if (lostCancelRace) h.message("매칭이 먼저 완료됐어요. 대전으로 돌아가요.");
      refresh(); return;
    }
    if (status.equals("idle") && !attempt.isEmpty() && !token.isEmpty()) {
      // A delayed join may still commit after this read. Tombstone the known attempt first.
      cancelling = true; if (cancelRequest.isEmpty()) cancelRequest = id();
      save(); showQueue("매칭 취소 확인 중…"); sendCancel(); return;
    }
    if (status.equals("cancelled") || status.equals("expired") || status.equals("idle")) {
      boolean wasActive = "realtimeQueue".equals(h.screen);
      clearQueue(); if (wasActive) h.load("modes"); return;
    }
    save();
    if (cancelling) { sendCancel(); return; }
    if (status.equals("preparing")) { join(); return; }
    if (!status.equals("waiting")) { queueError("매칭 상태를 확인하지 못했어요."); return; }
    showQueue(WAITING_LINES[0]); schedule();
  }
  void refresh() {
    if (closed || !resumed || reading || writing || preparing || h.api.token().isEmpty()) return;
    if (cancelling && !writing) { sendCancel(); return; }
    reading = true; final int g = generation; final String a = owner;
    final String requestedRoom = room;
    String path = requestedRoom.isEmpty() ? "pvp/queue/current" : "pvp/room/current?roomId=" + encode(requestedRoom);
    h.api.call(path, null, (r, e) -> {
      if (!valid(g, a)) return;
      reading = false;
      if (e != null) {
        if (h.api.token().isEmpty()) { h.login(); return; }
        if (room.isEmpty()) { queueFailure(e, this::refresh); return; }
        else if (clock != null) clock.setText("연결 확인 중… 서버 제한 시간은 계속돼요.");
        schedule(); return;
      }
      if (requestedRoom.isEmpty()) acceptQueue(r);
      else if (requestedRoom.equals(room)) acceptRoom(r);
      schedule();
    });
  }
  static String encode(String s) {
    try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { throw new IllegalStateException(e); }
  }
  JSONObject decorate(JSONObject envelope) {
    JSONObject b = copy(envelope.optJSONObject("battle"));
    try {
      b.put("realtime", true); b.put("session", room); b.put("phaseId", envelope.optString("phaseId"));
      b.put("revision", envelope.optLong("revision")); b.put("mode", envelope.optString("mode", mode));
      // Transport busy is transient; submit() guards it without poisoning an equal-revision view.
      b.put("canSubmit", envelope.optBoolean("canSubmit"));
      b.put("mySubmitted", envelope.optBoolean("mySubmitted"));
      b.put("serverNow", envelope.optLong("serverNow"));
      b.put("chooseDeadline", envelope.optLong("deadline", 0));
    } catch (Exception e) { throw new IllegalStateException(e); }
    return b;
  }
  static JSONObject copy(JSONObject o) {
    try { return o == null ? new JSONObject() : new JSONObject(o.toString()); }
    catch (Exception e) { throw new IllegalArgumentException(e); }
  }
  void acceptRoom(JSONObject r) {
    if (!room.equals(r.optString("roomId"))) return;
    if (latest != null && r.optLong("revision") < latest.optLong("revision")) return;
    mode = r.optString("mode", mode); save();
    JSONObject outcome = r.optJSONObject("result");
    if (outcome != null && ("MATCH_DECLINED".equals(outcome.optString("reason"))
        || "MATCH_TIMEOUT".equals(outcome.optString("reason")))) {
      finishAcceptance(outcome); return;
    }
    boolean changed = latest == null || r.optLong("revision") != latest.optLong("revision")
        || r.optBoolean("canSubmit") != latest.optBoolean("canSubmit")
        || !r.optString("result").equals(latest.optString("result"));
    latest = copy(r); serverAtReceive = r.optLong("serverNow", System.currentTimeMillis());
    receivedElapsed = SystemClock.elapsedRealtime();
    if (r.optJSONObject("result") != null && r.optJSONObject("battle") == null) {
      if (changed || !"realtimePrepare".equals(h.screen)) {
        h.battle = null; h.busy = false;
        LinearLayout panel = focusedPanel("대전 결과", "realtimePrepare", "realtime-result-stage");
        TextView resultHeading = lightText("대전 결과", 26); resultHeading.setTypeface(h.headline);
        panel.addView(resultHeading);
        panel.addView(lightText("대전 결과를 확인하고 있어요.", 18), new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(inlineClock()); attachClock();
      }
      terminal(r); return;
    }
    if ("accept".equals(r.optString("phase"))) {
      if (changed || !"realtimeAccept".equals(h.screen)) showAcceptance(r);
      return;
    }
    if ("prepare".equals(r.optString("phase"))) {
      if (changed || !"realtimePrepare".equals(h.screen)) showPreparation(r);
      return;
    }
    // Record every accepted viewer-projected snapshot before animation coalescing.
    // Display may skip an intermediate frame; the read-only archive must not.
    JSONObject next = decorate(r);
    NativeBattleReplay.observe(h, next);
    if (playing) { pendingPlayback = copy(r); return; }
    if (!changed && isBattle(h.battle) && "battle".equals(h.screen)) return;
    if (h.visibleDialog != null) { h.visibleDialog.dismiss(); h.visibleDialog = null; }
    BattleView view = currentView();
    final int g = generation; final String a = owner;
    Runnable done = () -> {
      if (!valid(g, a)) return;
      playing = false; h.busy = false; h.battle = next; h.battleScreen(); attachClock();
      if (pendingPlayback != null) {
        JSONObject p = pendingPlayback; pendingPlayback = null;
        // Force application: latest may already contain this coalesced snapshot.
        latest = null; acceptRoom(p); return;
      }
      terminal(r);
    };
    if (view != null && isBattle(h.battle) && room.equals(h.battle.optString("session"))
        && next.optLong("displayEventSeq") > h.battle.optLong("displayEventSeq")) {
      playing = true; view.playTo(next, done);
    } else done.run();
  }
  BattleView currentView() {
    for (int i = 0; i < h.root.getChildCount(); i++)
      if (h.root.getChildAt(i) instanceof BattleView) return (BattleView) h.root.getChildAt(i);
    return null;
  }
  void finishAcceptance(JSONObject outcome) {
    boolean requeued = outcome.optBoolean("requeued");
    queueRecoveryNeeded = requeued;
    dismissed = room; room = ""; latest = pendingPlayback = null; h.battle = null;
    generation++; reading = writing = preparing = inspecting = playing = false; h.busy = false;
    clearQueue(); save(); h.api.ui.removeCallbacks(poll);
    if (h.visibleDialog != null) { h.visibleDialog.dismiss(); h.visibleDialog = null; }
    if (requeued) {
      showQueue("상대의 수락이 확인되지 않아 다시 매칭해요.");
      inspectCurrent(false);
    } else {
      h.load("modes"); h.message("매칭이 종료됐어요. 승패나 점수는 바뀌지 않았어요.");
    }
  }
  void showAcceptance(JSONObject r) {
    JSONObject a = r.optJSONObject("acceptance");
    if (a == null) { h.message("매칭 수락 정보를 확인하지 못했어요."); return; }
    if (h.visibleDialog != null) { h.visibleDialog.dismiss(); h.visibleDialog = null; }
    h.battle = null; h.busy = false;
    LinearLayout panel = focusedPanel("대전 상대를 찾았어요", "realtimeAccept", "realtime-match-acceptance");
    TextView heading = lightText("대전 상대를 찾았어요", 25); heading.setTypeface(h.headline); panel.addView(heading);
    TextView modeLabel = lightText(mode.equals("rank") ? "랭크 · 실제 유저 대전" : isAiRoom(r) ? AI_NOTICE : "일반 · 실제 유저 대전", 15);
    panel.addView(modeLabel);
    TextView status = lightText(a.optBoolean("accepted") ? "수락 완료" : "대전 수락", 23);
    status.setTypeface(h.headline); panel.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
    panel.addView(inlineClock());
    TextView rule = lightText("수락 전에는 승패·점수가 바뀌지 않아요.", 13); panel.addView(rule);
    LinearLayout actions = new LinearLayout(h);
    Button accept = phaseButton("수락", "realtime-match-accept", () -> acceptMatch(r, true), true);
    Button decline = phaseButton("거절", "realtime-match-decline", () -> acceptMatch(r, false), false);
    phaseActions.add(accept); phaseActions.add(decline);
    actions.setGravity(Gravity.CENTER);
    PhaseButtonStyle.apply(h, accept, true); PhaseButtonStyle.apply(h, decline, false);
    LinearLayout.LayoutParams acceptSlot = new LinearLayout.LayoutParams(h.dp(160), h.dp(48));
    acceptSlot.rightMargin = h.dp(8); actions.addView(accept, acceptSlot);
    actions.addView(decline, new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
    LinearLayout.LayoutParams actionSlot = new LinearLayout.LayoutParams(-1, -2);
    actionSlot.topMargin = h.dp(6); panel.addView(actions, actionSlot);
    attachClock(); updatePhaseInputs();
  }
  void acceptMatch(JSONObject shown, boolean accepted) {
    if (writing || latest == null || !phaseDeadlineOpen() || !room.equals(shown.optString("roomId"))
        || !"accept".equals(latest.optString("phase"))
        || !shown.optString("phaseId").equals(latest.optString("phaseId"))
        || latest.optJSONObject("acceptance") == null
        || latest.optJSONObject("acceptance").optBoolean("accepted")) return;
    sendCommand(shown.optString("phaseId"), MainActivity.obj("accept", accepted));
  }
  void showPreparation(JSONObject r) {
    JSONObject preparation = r.optJSONObject("preparation");
    if (preparation == null) { h.message("팀 편성 정보를 확인하지 못했어요."); return; }
    if (h.visibleDialog != null) { h.visibleDialog.dismiss(); h.visibleDialog = null; }
    h.battle = null; h.busy = false;
    h.base(mode.equals("rank") ? "랭크 대전 · 팀 편성" : "일반 대전 · 팀 편성", "realtimePrepare");
    AppSkin.apply(h, "training"); h.page.removeAllViews(); phaseActions.clear();
    MenuKit kit = new MenuKit(h);
    JSONArray selected = h.list(preparation, "selectedIds"), available = h.list(preparation, "available");
    if (!preparationPhase.equals(r.optString("phaseId"))) {
      preparationPhase = r.optString("phaseId"); preparationSlot = preparationPage = 0;
    }
    preparationSlot = Math.max(0, Math.min(preparationSlot, Math.max(0, selected.length() - 1)));
    int pageCount = Math.max(1, (available.length() + PREPARATION_PAGE_SIZE - 1) / PREPARATION_PAGE_SIZE);
    preparationPage = Math.max(0, Math.min(preparationPage, pageCount - 1));
    boolean ready = preparation.optBoolean("ready");
    LinearLayout head = kit.row(); head.setTag("realtime-prepare-header");
    head.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    TextView heading = lightText(mode.equals("rank") ? "랭크 · 팀 편성" : "일반 · 팀 편성", 21);
    heading.setTypeface(h.headline); heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.setPadding(h.dp(16), 0, h.dp(8), 0);
    head.addView(heading, new LinearLayout.LayoutParams(h.dp(250), -1));
    TextView status = lightText(isAiRoom(r) ? AI_NOTICE : preparation.optBoolean("opponentReady")
        ? "상대 준비 완료" : "상대가 팀을 고르고 있어요.", 14);
    status.setTag("realtime-prepare-status"); status.setMaxLines(2); status.setEllipsize(null);
    status.setIncludeFontPadding(false); status.setPadding(h.dp(8), 0, h.dp(8), 0);
    head.addView(status, new LinearLayout.LayoutParams(0, -1, 1));
    head.addView(inlineClock(), new LinearLayout.LayoutParams(h.dp(200), -1));
    h.page.addView(head, new LinearLayout.LayoutParams(-1, h.dp(48)));
    LinearLayout body = kit.row(); body.setGravity(Gravity.TOP); body.setBaselineAligned(false);
    body.setTag("realtime-prepare-fixed");
    LinearLayout.LayoutParams bodySize = new LinearLayout.LayoutParams(-1, 0, 1);
    bodySize.topMargin = h.dp(6); h.page.addView(body, bodySize); h.body = body;
    LinearLayout order = kit.column();
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, .50f);
    left.rightMargin = h.dp(12); body.addView(order, left);
    TextView orderTitle = lightText("출전 순서", 17); orderTitle.setTypeface(h.headline);
    kit.headingBackground(orderTitle);
    order.addView(orderTitle, new LinearLayout.LayoutParams(-1, h.dp(48)));
    LinearLayout podium = kit.row(); podium.setGravity(Gravity.TOP); podium.setBaselineAligned(false);
    podium.setTag("realtime-prepare-podium");
    order.addView(podium, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < selected.length(); i++) {
      final int slot = i;
      JSONObject candidate = preparationCandidate(available, selected.optString(i));
      String orderLabel = i == 0 ? "선봉" : (i + 1) + "번";
      View card;
      if (candidate.length() == 0) {
        TextView unavailable = lightText(orderLabel + " · 정보 확인 중", 14);
        unavailable.setTag("realtime-prepare-slot-" + i); card = unavailable;
      } else {
        card = preparationCard(candidate, "realtime-prepare-slot-" + i, orderLabel,
            i == preparationSlot, () -> choosePreparationSlot(r, slot));
        phaseActions.add(card);
      }
      LinearLayout.LayoutParams slotSize = new LinearLayout.LayoutParams(0, -1, 1);
      slotSize.setMargins(h.dp(3), h.dp(4), h.dp(3), 0); podium.addView(card, slotSize);
    }
    LinearLayout choices = kit.column(); body.addView(choices, new LinearLayout.LayoutParams(0, -1, .50f));
    LinearLayout pager = kit.row(); pager.setBaselineAligned(false);
    TextView candidateTitle = lightText("후보 · " + (preparationSlot == 0 ? "선봉" : (preparationSlot + 1) + "번"), 17);
    kit.headingBackground(candidateTitle);
    pager.addView(candidateTitle, new LinearLayout.LayoutParams(0, -1, 1));
    TextView pageNumber = h.text((preparationPage + 1) + " / " + pageCount, 13);
    pageNumber.setGravity(Gravity.CENTER); pageNumber.setBackground(new GamePaperDrawable(h));
    pager.addView(pageNumber, new LinearLayout.LayoutParams(h.dp(62), -1));
    Button previous = phaseButton("‹", "realtime-prepare-previous", () -> {
      if (preparationPage > 0) { preparationPage--; showPreparation(latest); }
    }, false);
    previous.setContentDescription("이전 후보"); previous.setEnabled(preparationPage > 0);
    pager.addView(previous, new LinearLayout.LayoutParams(h.dp(48), -1));
    Button next = phaseButton("›", "realtime-prepare-next", () -> {
      if (preparationPage + 1 < pageCount) { preparationPage++; showPreparation(latest); }
    }, false);
    next.setContentDescription("다음 후보"); next.setEnabled(preparationPage + 1 < pageCount);
    pager.addView(next, new LinearLayout.LayoutParams(h.dp(48), -1));
    choices.addView(pager, new LinearLayout.LayoutParams(-1, h.dp(48)));
    if (available.length() == 0) {
      TextView empty = h.text("출전 가능한 갈매미가 없어요.", 16); empty.setGravity(Gravity.CENTER);
      empty.setBackground(new GamePaperDrawable(h)); choices.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
    } else {
      LinearLayout line = kit.row(); line.setGravity(Gravity.TOP); line.setBaselineAligned(false);
      line.setTag("realtime-prepare-candidates");
      choices.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int column = 0; column < PREPARATION_PAGE_SIZE; column++) {
        int index = preparationPage * PREPARATION_PAGE_SIZE + column;
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
        cell.setMargins(h.dp(3), h.dp(4), h.dp(3), 0);
        if (index >= available.length()) { line.addView(new View(h), cell); continue; }
        JSONObject candidate = available.optJSONObject(index);
        String id = candidate.optString("id");
        View pick = preparationCard(candidate, "realtime-prepare-candidate-" + index, "",
            id.equals(selected.optString(preparationSlot)), () -> choosePreparationCandidate(r, id));
        line.addView(pick, cell); phaseActions.add(pick);
      }
    }
    LinearLayout footer = kit.row();
    Button confirm = phaseButton(ready ? "준비 완료 · 상대 대기" : "이 편성으로 준비 완료", "realtime-prepare-confirm",
        () -> {
          if (!currentPreparation(r)) { refresh(); return; }
          prepareCommand(latest, h.list(latest.optJSONObject("preparation"), "selectedIds"), true);
        }, true);
    phaseActions.add(confirm);
    LinearLayout.LayoutParams confirmSize = new LinearLayout.LayoutParams(0, -1, 1);
    confirmSize.rightMargin = h.dp(10); footer.addView(confirm, confirmSize);
    footer.addView(phaseButton("대전 나가기", "realtime-prepare-leave", () -> back(), false),
        new LinearLayout.LayoutParams(h.dp(160), -1));
    LinearLayout.LayoutParams footerSize = new LinearLayout.LayoutParams(-1, h.dp(48));
    footerSize.topMargin = h.dp(8); h.page.addView(footer, footerSize);
    attachClock(); updatePhaseInputs();
  }
  /** Character-only approved top33 artwork, with live identity and preparation state below. */
  PartyCharacterView preparationCard(JSONObject candidate, String tag, String slot,
      boolean selected, Runnable action) {
    JSONObject identity;
    try { identity = PreparationIdentity.artwork(candidate); }
    catch (org.json.JSONException invalid) { throw new IllegalArgumentException(invalid); }
    if (!identity.has("tierName")) {
      try { identity.put("tierName", candidate.optString("rarity")); }
      catch (org.json.JSONException invalid) { throw new IllegalArgumentException(invalid); }
    }
    PartyCharacterView card = new PartyCharacterView(h, identity);
    card.setTag(tag); card.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
    card.setBackground(PartyCharacterView.selection(h, selected)); card.setSelected(selected);
    card.setFocusable(true); card.setMinimumWidth(h.dp(48)); card.setMinimumHeight(h.dp(48));
    card.setOnClickListener(view -> action.run());
    card.picture.setTag(tag + "-character");
    String runtime = slot + (candidate.has("level") ? (slot.isEmpty() ? "" : " · ") + "Lv" + candidate.optInt("level") : "");
    if (candidate.has("hp") && candidate.has("maxHp"))
      runtime += (runtime.isEmpty() ? "" : " · ") + candidate.optInt("hp") + "/" + candidate.optInt("maxHp");
    final String liveRuntime = runtime;
    TextView state = PartyCharacterView.liveLabel(h, runtime);
    state.setTag(tag + "-state"); state.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(state, new LinearLayout.LayoutParams(-1, -2));
    // Keep the component's real loading/missing identity announcement and add
    // dynamic state exactly once; the visible name always comes from live identity.
    card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        info.setClassName(Button.class.getName());
        info.setContentDescription((liveRuntime.isEmpty() ? "" : liveRuntime + " · ") + host.getContentDescription());
      }
    });
    return card;
  }
  static JSONObject preparationCandidate(JSONArray candidates, String id) {
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.optJSONObject(i);
      if (candidate != null && id.equals(candidate.optString("id"))) return candidate;
    }
    return new JSONObject();
  }
  boolean phaseDeadlineOpen() {
    if (latest == null) return false;
    long deadline = latest.optLong("deadline", 0);
    return deadline <= 0 || serverAtReceive + SystemClock.elapsedRealtime() - receivedElapsed < deadline;
  }
  void updatePhaseInputs() {
    if (latest == null) return;
    JSONObject state = "accept".equals(latest.optString("phase")) ? latest.optJSONObject("acceptance")
        : latest.optJSONObject("preparation");
    boolean open = !writing && phaseDeadlineOpen() && latest.optJSONObject("result") == null
        && state != null && !state.optBoolean("accepted") && !state.optBoolean("ready");
    for (View button : phaseActions)
      button.setEnabled(open && (!"realtime-prepare-confirm".equals(button.getTag())
          || h.list(state, "selectedIds").length() == 3));
  }
  boolean currentPreparation(JSONObject shown) {
    return latest != null && !writing && phaseDeadlineOpen() && room.equals(shown.optString("roomId"))
        && latest.optJSONObject("result") == null
        && "prepare".equals(latest.optString("phase"))
        && shown.optString("phaseId").equals(latest.optString("phaseId"))
        && latest.optJSONObject("preparation") != null
        && !latest.optJSONObject("preparation").optBoolean("ready");
  }
  void choosePreparationSlot(JSONObject shown, int slot) {
    if (!currentPreparation(shown)) { refresh(); return; }
    if (slot < 0 || slot >= h.list(latest.optJSONObject("preparation"), "selectedIds").length()) return;
    preparationSlot = slot;
    showPreparation(latest);
  }
  void choosePreparationCandidate(JSONObject shown, String chosen) {
    if (!currentPreparation(shown)) { refresh(); return; }
    JSONObject state = latest.optJSONObject("preparation");
    JSONArray selected = h.list(state, "selectedIds"), candidates = h.list(state, "available");
    if (!chosen.equals(preparationCandidate(candidates, chosen).optString("id"))
        || preparationSlot >= selected.length()) return;
    JSONArray team = new JSONArray(); String old = selected.optString(preparationSlot);
    for (int i = 0; i < selected.length(); i++)
      team.put(i == preparationSlot ? chosen : selected.optString(i).equals(chosen) ? old : selected.optString(i));
    prepareCommand(latest, team, false);
  }
  void prepareCommand(JSONObject shown, JSONArray team, boolean ready) {
    if (!currentPreparation(shown)) { refresh(); return; }
    sendCommand(shown.optString("phaseId"), MainActivity.obj("prepare", MainActivity.obj("team", team, "ready", ready)));
  }
  void attachClock() {
    BattleView battleView = currentView();
    if (battleView != null) { clock = battleView.inputTimer; return; }
    View inline = h.root.findViewWithTag("realtime-phase-clock");
    if (inline instanceof TextView) { clock = (TextView) inline; return; }
    clock = inlineClock();
    clock.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
    lp.topMargin = h.dp(4); lp.rightMargin = h.dp(8); h.root.addView(clock, lp);
  }
  void submit(JSONObject shown, JSONObject event) {
    if (!isBattle(shown) || latest == null || writing || playing) return;
    long inputDeadline = latest.optLong("deadline", 0);
    long estimatedNow = serverAtReceive + SystemClock.elapsedRealtime() - receivedElapsed;
    if (!event.optBoolean("leave") && inputDeadline > 0 && estimatedNow >= inputDeadline) { refresh(); return; }
    if (!room.equals(shown.optString("session")) || !shown.optString("phaseId").equals(latest.optString("phaseId"))) {
      h.message("턴이 바뀌었어요. 현재 화면에서 다시 선택해 주세요."); refresh(); return;
    }
    if (event.optBoolean("leave")) { surrender(); return; }
    if (!latest.optBoolean("canSubmit")) { h.message("선택을 이미 보냈어요. 상대를 기다려 주세요."); return; }
    JSONObject action = event.optJSONObject("action");
    if (action != null && "switch".equals(action.optString("kind")) && !action.has("then") && freeSwitch(shown)) {
      followup(shown, action); return;
    }
    sendCommand(shown.optString("phaseId"), event);
  }
  boolean freeSwitch(JSONObject b) {
    JSONObject team = h.list(b, "teams").optJSONObject(0);
    if (team == null) return false;
    JSONArray augs = h.list(team, "augments");
    for (int i = 0; i < augs.length(); i++) {
      JSONObject aug = augs.optJSONObject(i);
      if (aug != null && "freeswitch".equals(aug.optString("id"))) return true;
    }
    return false;
  }
  void followup(JSONObject shown, JSONObject action) {
    JSONObject own = h.list(shown, "teams").optJSONObject(0);
    JSONObject choices = own == null ? null : own.optJSONObject("switchFollowups");
    JSONArray skills = choices == null ? null : choices.optJSONArray(String.valueOf(action.optInt("to")));
    if (skills == null || skills.length() == 0) { h.message("교체 후 기술을 불러오지 못했어요."); refresh(); return; }
    String[] labels = new String[skills.length()];
    for (int i = 0; i < labels.length; i++) labels[i] = skills.optJSONObject(i).optString("name");
    new GameDialog(h).setTitle("교체 후 사용할 기술")
        .setItems(labels, (dialog, which) -> {
          JSONObject next = copy(action);
          try { next.put("then", skills.optJSONObject(which).optString("id")); } catch (Exception e) { return; }
          submit(shown, MainActivity.obj("action", next));
        }).setNegativeButton("돌아가기", null).show();
  }
  void sendCommand(String phaseId, JSONObject event) {
    JSONObject command = new JSONObject();
    try {
      for (String key : new String[] {"action", "choose", "reroll", "prepare", "accept"}) if (event.has(key)) command.put(key, event.get(key));
    } catch (Exception e) { h.message("선택 내용을 확인해 주세요."); return; }
    writing = true; updatePhaseInputs(); final int g = generation; final String a = owner;
    h.api.call("pvp/room/command", MainActivity.obj("roomId", room, "phaseId", phaseId,
        "requestId", id(), "command", command), (r, e) -> {
      if (!valid(g, a)) return;
      writing = false;
      if (e != null) { updatePhaseInputs(); h.message(e + " 현재 대전 상태를 확인할게요."); refresh(); return; }
      acceptRoom(r); schedule();
    });
  }
  void surrender() {
    if (writing || room.isEmpty()) return;
    writing = true; final int g = generation; final String a = owner;
    h.api.call("pvp/room/surrender", MainActivity.obj("roomId", room, "requestId", id()), (r, e) -> {
      if (!valid(g, a)) return;
      writing = false;
      if (e != null) { h.message(e); refresh(); return; }
      acceptRoom(r); schedule();
    });
  }
  String shownResult = "";
  void terminal(JSONObject r) {
    JSONObject result = r.optJSONObject("result");
    if (result == null || "pending".equals(result.optString("settlement")) || room.equals(shownResult)) return;
    shownResult = room;
    boolean draw = result.isNull("winner");
    String label = "SERVER_ABORT".equals(result.optString("reason")) ? "대전 종료"
        : draw ? "무승부" : result.optInt("winner", 1) == 0 ? "승리" : "패배";
    JSONObject rank = result.optJSONObject("rank");
    JSONObject after = rank == null ? null : rank.optJSONObject("after");
    String detail = !mode.equals("rank") || after == null ? "대전 결과가 저장됐어요."
        : rank.optString("tier") + " · " + after.optInt("lp") + " LP"
            + (after.optBoolean("promotionActive") ? " · 승급전 " + after.optInt("promotionWins")
                + "승 " + after.optInt("promotionLosses") + "패" : "")
            + ("PROMOTED".equals(rank.optString("promotionChange")) ? "\n승급했어요!"
                : "FAILED".equals(rank.optString("promotionChange")) ? "\n승급전에 실패했어요." : "");
    LinearLayout resultCard = new MenuKit(h).column();
    resultCard.setTag("realtime-result-card");
    resultCard.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(12));
    resultCard.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
    TextView modeLabel = lightText(isAiRoom(r) ? AI_NOTICE : mode.equals("rank") ? "랭크 대전" : "일반 대전", 15);
    resultCard.addView(modeLabel);
    TextView resultDetail = h.text(detail, 18); resultDetail.setGravity(Gravity.CENTER);
    resultDetail.setBackground(new GamePaperDrawable(h));
    resultDetail.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(12));
    resultCard.addView(resultDetail, new LinearLayout.LayoutParams(-1, -2));
    new GameDialog(h).setTitle(label).setView(resultCard).setCancelable(false)
        .setPositiveButton("모드 선택", (d, w) -> {
          dismissed = room; room = ""; latest = null; shownResult = ""; generation++;
          reading = writing = playing = false; pendingPlayback = null; h.battle = null;
          h.api.ui.removeCallbacks(poll); save(); h.load("modes");
        }).show();
  }
}

