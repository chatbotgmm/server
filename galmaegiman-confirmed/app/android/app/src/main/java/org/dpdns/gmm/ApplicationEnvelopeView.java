package org.dpdns.gmm;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/** 서버 결과를 받는 요청과 독립적인 로컬 봉투 표현. 완료만으로 획득을 만들지 않는다. */
final class ApplicationEnvelopeView extends FrameLayout {
  static final String METADATA_ASSET =
      "native/registration-v1/component/application-animation.json";
  static final String ATLAS_ASSET =
      "native/registration-v1/resources/application-envelope-atlas.png";
  static final int ATLAS_WIDTH = 1774, ATLAS_HEIGHT = 887, TOTAL_DURATION_MS = 1000;

  // Published application-animation.json의 정수 sourceRect. 균등 셀 나눗셈은 금지한다.
  private static final int[][] FRAMES = {
    {0, 0, 443, 443, 130},
    {443, 0, 444, 443, 120},
    {887, 0, 443, 443, 110},
    {1330, 0, 444, 443, 110},
    {0, 443, 443, 444, 120},
    {443, 443, 444, 444, 130},
    {887, 443, 443, 444, 140},
    {1330, 443, 444, 444, 140}
  };

  final MainActivity h;
  final int ownerEpoch;
  final String ownerAccount;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final TextView unavailable;
  Bitmap atlas;
  ValueAnimator clock;
  int frame, elapsedMs, generation;
  boolean started, resultReady, completed, delivered, cancelled, observing;
  private Runnable completion;
  private final Runnable lifecycle = this::refresh;
  private final ViewTreeObserver.OnPreDrawListener visibility =
      () -> {
        refresh();
        return true;
      };
  private final ContentObserver animationSetting =
      new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
          refresh();
        }
      };

  ApplicationEnvelopeView(MainActivity h) {
    super(h);
    this.h = h;
    ownerEpoch = h.epoch;
    ownerAccount = h.api.token();
    setTag("application-envelope");
    setContentDescription("모집 지원서 봉투");
    setWillNotDraw(false);
    unavailable = h.text("지원서 이미지를 불러오지 못했어요. 아래 버튼으로 결과를 확인하세요.", 14);
    unavailable.setGravity(Gravity.CENTER);
    unavailable.setTextColor(JournalTheme.PAPER);
    unavailable.setPadding(h.dp(20), h.dp(12), h.dp(20), h.dp(12));
    unavailable.setVisibility(INVISIBLE);
    addView(unavailable, new LayoutParams(-1, -2, Gravity.CENTER));
    h.registrationArt()
        .requestEnvelope(
            bitmap -> {
              if (!current()) return;
              atlas =
                  bitmap != null
                          && bitmap.getWidth() == ATLAS_WIDTH
                          && bitmap.getHeight() == ATLAS_HEIGHT
                      ? bitmap
                      : null;
              unavailable.setVisibility(atlas == null ? VISIBLE : INVISIBLE);
              invalidate();
            });
  }

  static int frameCount() {
    return FRAMES.length;
  }

  static Rect sourceRect(int frame) {
    int[] source = FRAMES[Math.max(0, Math.min(frame, FRAMES.length - 1))];
    return new Rect(source[0], source[1], source[0] + source[2], source[1] + source[3]);
  }

  static int durationMs(int frame) {
    return FRAMES[frame][4];
  }

  static int frameAt(int elapsedMs) {
    int end = 0;
    for (int i = 0; i < FRAMES.length; i++) {
      end += durationMs(i);
      if (elapsedMs < end) return i;
    }
    return FRAMES.length - 1;
  }

  /** 이 플래그는 실제 result.items를 보유한 호출자만 전달한다. 재탭은 동일 실행을 유지한다. */
  void play(boolean authoritativeResultReady, Runnable onReady) {
    if (started || !current()) return;
    started = true;
    resultReady = authoritativeResultReady;
    completion = onReady;
    refresh();
  }

  /** 느린 결과에서도 마지막 프레임을 유지한다. 결과 도착 자체는 추첨·요청을 하지 않는다. */
  void authoritativeResultReady() {
    if (!current()) return;
    resultReady = true;
    refresh();
  }

  boolean current() {
    return !cancelled
        && !h.isFinishing()
        && !h.isDestroyed()
        && ownerEpoch == h.epoch
        && ownerAccount.equals(h.api.token());
  }

  boolean active() {
    return current()
        && h.motionResumed
        && isAttachedToWindow()
        && getWindowVisibility() == VISIBLE
        && isShown();
  }

  boolean motion() {
    return NativeMotion.enabled(h, h.motionEnabled)
        && NativeMotion.legacyEnabled(h, h.motionEnabled);
  }

  void refresh() {
    if (h == null || cancelled) return;
    if (!current()) {
      cancel();
      return;
    }
    if (!started || delivered) return;
    if (!active()) {
      stopClock();
      return;
    }
    if (!motion()) {
      stopClock();
      elapsedMs = TOTAL_DURATION_MS;
      frame = FRAMES.length - 1;
      completed = true;
      invalidate();
    }
    if (completed) {
      if (resultReady && completion != null) {
        delivered = true;
        Runnable ready = completion;
        completion = null;
        ready.run();
      }
      return;
    }
    if (clock != null) return;
    final int run = ++generation;
    ValueAnimator next = ValueAnimator.ofInt(elapsedMs, TOTAL_DURATION_MS);
    clock = next;
    next.setDuration(Math.max(1, TOTAL_DURATION_MS - elapsedMs));
    next.setInterpolator(new LinearInterpolator());
    next.addUpdateListener(
        animation -> {
          if (run != generation || clock != animation) return;
          if (!active() || !motion()) {
            refresh();
            return;
          }
          elapsedMs = (Integer) animation.getAnimatedValue();
          frame = frameAt(elapsedMs);
          invalidate();
        });
    next.addListener(
        new AnimatorListenerAdapter() {
          boolean interrupted;

          @Override
          public void onAnimationCancel(Animator animation) {
            interrupted = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (interrupted || run != generation || clock != animation || !current()) return;
            clock = null;
            elapsedMs = TOTAL_DURATION_MS;
            frame = FRAMES.length - 1;
            completed = true;
            invalidate();
            refresh();
          }
        });
    next.start();
  }

  private void stopClock() {
    generation++;
    ValueAnimator previous = clock;
    clock = null;
    if (previous != null) previous.cancel();
  }

  /** cancel/end 콜백 순서와 무관하게 폐기된 화면은 결과를 공개하지 않는다. */
  void cancel() {
    cancelled = true;
    completion = null;
    stopClock();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (cancelled) return;
    if (!observing) {
      observing = true;
      if (h.journalTime != null) h.journalTime.addListener(lifecycle);
      getViewTreeObserver().addOnPreDrawListener(visibility);
      h.getContentResolver()
          .registerContentObserver(
              Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
              false,
              animationSetting);
    }
    refresh();
  }

  @Override
  protected void onDetachedFromWindow() {
    cancel();
    if (observing) {
      observing = false;
      if (h.journalTime != null) h.journalTime.removeListener(lifecycle);
      if (getViewTreeObserver().isAlive())
        getViewTreeObserver().removeOnPreDrawListener(visibility);
      h.getContentResolver().unregisterContentObserver(animationSetting);
    }
    // Bitmap 소유자는 공유 저장소이며 여기서 recycle하지 않는다.
    atlas = null;
    super.onDetachedFromWindow();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    refresh();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (atlas == null) return;
    Rect source = sourceRect(frame);
    float size = Math.min(h.dp(320), Math.min(getWidth() * .94f, getHeight() * .94f));
    float scale = Math.min(size / source.width(), size / source.height());
    float width = source.width() * scale, height = source.height() * scale;
    float left = (getWidth() - width) / 2f, top = (getHeight() - height) / 2f;
    canvas.drawBitmap(atlas, source, new RectF(left, top, left + width, top + height), paint);
  }
}
