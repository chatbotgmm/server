package org.dpdns.gmm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.LongSupplier;

/** 화면이 보이는 동안만 현지 시간 경계를 감시한다. 게임 상태나 API에는 접근하지 않는다. */
final class JournalTimeController implements AutoCloseable {
  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LongSupplier clock;
  private final ArrayList<Runnable> listeners = new ArrayList<>();
  private LocalTimeTheme.State state;
  private boolean resumed, registered, closed;

  private final Runnable minute =
      () -> {
        if (!resumed || closed) return;
        evaluate(false);
        scheduleMinute();
      };

  private final BroadcastReceiver timeChanges =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
          if (!resumed || closed) return;
          evaluate(false);
          scheduleMinute();
        }
      };

  JournalTimeController(Context context) {
    this(context, System::currentTimeMillis);
  }

  /** 화면 검수와 시간·수명주기 검증도 같은 판정 경로를 사용한다. */
  JournalTimeController(Context context, LongSupplier clock) {
    Context app = context.getApplicationContext();
    this.context = app == null ? context : app;
    this.clock = Objects.requireNonNull(clock);
    state = LocalTimeTheme.resolve(now());
  }

  long now() {
    return clock.getAsLong();
  }

  LocalTimeTheme.Phase phase() {
    return state.phase;
  }

  boolean isResumed() {
    return resumed;
  }

  void addListener(Runnable listener) {
    if (!closed && !listeners.contains(listener)) listeners.add(Objects.requireNonNull(listener));
  }

  void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** 표시 설정만 통보한다. 시계·이미지 선택·게임 진행은 다시 계산하지 않는다. */
  void motionPreferenceChanged() {
    if (!closed) notifyListeners();
  }

  void resume() {
    if (closed) return;
    resumed = true;
    if (!registered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      filter.addAction(Intent.ACTION_DATE_CHANGED);
      if (Build.VERSION.SDK_INT >= 33)
        context.registerReceiver(timeChanges, filter, Context.RECEIVER_NOT_EXPORTED);
      else context.registerReceiver(timeChanges, filter);
      registered = true;
    }
    // 같은 배경으로 복귀해도 렌더러가 모션 수명주기를 다시 맞춰야 한다.
    evaluate(true);
    scheduleMinute();
  }

  void pause() {
    boolean wasResumed = resumed;
    resumed = false;
    handler.removeCallbacks(minute);
    if (registered) {
      context.unregisterReceiver(timeChanges);
      registered = false;
    }
    if (wasResumed) notifyListeners();
  }

  private void evaluate(boolean returning) {
    LocalTimeTheme.State next = LocalTimeTheme.resolve(now());
    boolean changed = state.phase != next.phase;
    state = next;
    if (changed || returning) notifyListeners();
  }

  private void scheduleMinute() {
    handler.removeCallbacks(minute);
    if (resumed && !closed)
      handler.postDelayed(minute, LocalTimeTheme.millisUntilNextMinute(now()));
  }

  private void notifyListeners() {
    // detach나 화면 이동으로 통보 중 구독을 해제해도 다른 렌더러의 통보를 빠뜨리지 않는다.
    for (Runnable listener : new ArrayList<>(listeners)) {
      if (listeners.contains(listener)) listener.run();
    }
  }

  @Override
  public void close() {
    if (closed) return;
    pause();
    closed = true;
    listeners.clear();
  }
}
