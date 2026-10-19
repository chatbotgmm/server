package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;

/** 요청 중 조작을 잠그고 같은 요청의 완료만 표시를 정리한다. 재화 소비는 자동 재시도하지 않는다. */
final class NativeOperationUi {
  final MainActivity host;
  final Handler clock = new Handler(Looper.getMainLooper());
  int sequence;
  FrameLayout overlay;
  Button trigger;
  CharSequence originalText, originalDescription;
  boolean originalEnabled;
  boolean triggerLabelChanged;
  Runnable pending;

  NativeOperationUi(MainActivity host) {
    this.host = host;
  }

  int begin(String label, Button button) {
    clear();
    int operation = ++sequence;
    trigger = button;
    if (button != null) {
      originalText = button.getText();
      originalDescription = button.getContentDescription();
      originalEnabled = button.isEnabled();
      triggerLabelChanged =
          !(button instanceof JournalButton) || ((JournalButton) button).operationLabelEnabled;
      if (triggerLabelChanged) {
        button.setText(label);
        button.setContentDescription(label);
      }
      button.setEnabled(false);
    }
    pending =
        () -> {
          if (operation != sequence || host.isFinishing() || host.isDestroyed()) return;
          overlay = new FrameLayout(host);
          overlay.setTag("request-progress");
          overlay.setClickable(true);
          overlay.setFocusable(true);
          overlay.setContentDescription(label);
          overlay.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
          LinearLayout paper = new LinearLayout(host);
          paper.setGravity(Gravity.CENTER_VERTICAL);
          paper.setPadding(host.dp(18), host.dp(14), host.dp(18), host.dp(14));
          paper.setBackground(new GamePaperDrawable(host));
          ProgressBar spinner = new ProgressBar(host);
          spinner.setIndeterminateTintList(ColorStateList.valueOf(JournalTheme.TEAL));
          paper.addView(spinner, new LinearLayout.LayoutParams(host.dp(28), host.dp(28)));
          TextView status = host.text(label, 17);
          status.setTypeface(host.prose);
          status.setTag("request-progress-label");
          paper.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
          FrameLayout.LayoutParams box =
              new FrameLayout.LayoutParams(
                  Math.min(
                      host.dp(340), Math.max(host.dp(220), host.root.getWidth() - host.dp(48))),
                  -2,
                  Gravity.CENTER);
          overlay.addView(paper, box);
          host.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
          overlay.announceForAccessibility(label);
        };
    // 빠른 응답에서는 창이 깜박이지 않도록 하고 버튼 자체는 즉시 반응한다.
    clock.postDelayed(pending, 180);
    return operation;
  }

  boolean finish(int operation) {
    if (operation != sequence) return false;
    clear();
    return true;
  }

  void clear() {
    if (pending != null) clock.removeCallbacks(pending);
    pending = null;
    if (overlay != null && overlay.getParent() instanceof ViewGroup)
      ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null;
    if (trigger != null) {
      // 유지한 숫자가 요청 중 갱신됐어도 이전 값으로 되돌리지 않는다.
      if (triggerLabelChanged) {
        trigger.setText(originalText);
        trigger.setContentDescription(originalDescription);
      }
      trigger.setEnabled(originalEnabled);
    }
    trigger = null;
    triggerLabelChanged = false;
  }

  void close() {
    ++sequence;
    clear();
  }

  static String label(String path) {
    if (path.contains("battle/turn")) return "행동 처리 중…";
    if (path.contains("battle/start")) return "전투 준비 중…";
    if (path.contains("team") || path.equals("party")) return "편성 저장 중…";
    if (path.contains("claim") || path.equals("attend") || path.equals("coupon")) return "보상 받는 중…";
    if (path.contains("growth")) return "성장 적용 중…";
    if (path.contains("heal")) return "회복 중…";
    if (path.contains("expedition/start")) return "탐험 출발 중…";
    if (path.contains("draw")) return "지원서 읽는 중…";
    if (path.contains("craft") || path.contains("mix")) return "조합 중…";
    return "진행 확인 중…";
  }
}
