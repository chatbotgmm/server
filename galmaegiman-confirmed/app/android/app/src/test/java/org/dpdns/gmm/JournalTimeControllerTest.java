package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Intent;
import android.os.Looper;
import java.time.Duration;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 실제 Handler·동적 receiver를 API 24와 최신 환경에서 검증한다. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = {24, 35},
    qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalTimeControllerTest {
  TimeZone originalZone;
  JournalTimeController controller;
  final AtomicLong clock = new AtomicLong();
  final AtomicInteger notices = new AtomicInteger();

  @Before
  public void isolate() {
    originalZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 9, 59, 59) + 700);
    controller = new JournalTimeController(RuntimeEnvironment.getApplication(), clock::get);
    controller.addListener(notices::incrementAndGet);
  }

  @After
  public void close() {
    controller.close();
    TimeZone.setDefault(originalZone);
  }

  void idle(long millis) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
  }

  void broadcast(String action) {
    RuntimeEnvironment.getApplication().sendBroadcast(new Intent(action));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void initialPhaseIsCorrectBeforeResumeAndMinuteBoundaryChangesItOnce() {
    assertEquals(LocalTimeTheme.Phase.MORNING, controller.phase());
    assertEquals(clock.get(), controller.now());
    assertFalse(controller.isResumed());
    controller.resume();
    assertTrue(controller.isResumed());
    assertEquals(1, notices.get());
    clock.addAndGet(299);
    idle(299);
    assertEquals(LocalTimeTheme.Phase.MORNING, controller.phase());
    clock.incrementAndGet();
    idle(1);
    assertEquals(LocalTimeTheme.Phase.DAY, controller.phase());
    assertEquals(2, notices.get());
    clock.addAndGet(60_000);
    idle(60_000);
    assertEquals("같은 시간대의 분 경계는 이미지 재로딩 통보를 하지 않음", 2, notices.get());
  }

  @Test
  public void wallClockChangeCancelsOldMinuteDeadlineAndUsesNewWallClock() {
    controller.resume();
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 19, 59, 45));
    broadcast(Intent.ACTION_TIME_CHANGED);
    assertEquals(LocalTimeTheme.Phase.EVENING, controller.phase());
    assertEquals(2, notices.get());
    clock.addAndGet(14_999);
    idle(14_999);
    assertEquals(LocalTimeTheme.Phase.EVENING, controller.phase());
    clock.incrementAndGet();
    idle(1);
    assertEquals(LocalTimeTheme.Phase.NIGHT, controller.phase());
    assertEquals(3, notices.get());
  }

  @Test
  public void timezoneAndDateBroadcastsReevaluateWithoutRestartingTheScreen() {
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 8, 30, 0));
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    controller.resume();
    assertEquals(LocalTimeTheme.Phase.EVENING, controller.phase());
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    broadcast(Intent.ACTION_TIMEZONE_CHANGED);
    assertEquals(LocalTimeTheme.Phase.NIGHT, controller.phase());
    assertEquals(2, notices.get());
    clock.set(LocalTimeThemeTest.utc(2026, 10, 16, 15, 0, 0));
    broadcast(Intent.ACTION_DATE_CHANGED);
    assertEquals(LocalTimeTheme.Phase.DAY, controller.phase());
    assertEquals(3, notices.get());
  }

  @Test
  public void pausedControllerStopsTicksAndBroadcastsThenRefreshesOnReturn() {
    controller.resume();
    controller.pause();
    assertFalse(controller.isResumed());
    assertEquals("렌더러에 중지 통보", 2, notices.get());
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 20, 0, 0));
    broadcast(Intent.ACTION_TIME_CHANGED);
    idle(600_000);
    assertEquals(LocalTimeTheme.Phase.MORNING, controller.phase());
    assertEquals(2, notices.get());
    controller.resume();
    assertEquals(LocalTimeTheme.Phase.NIGHT, controller.phase());
    assertEquals(3, notices.get());
    controller.pause();
    controller.resume();
    assertEquals("시간대가 같은 복귀도 모션 수명주기를 통보", 5, notices.get());
  }

  @Test
  public void repeatedResumeDoesNotAccumulateMinuteCallbacksAndCloseUnregisters() {
    controller.close();
    AtomicInteger reads = new AtomicInteger();
    controller =
        new JournalTimeController(
            RuntimeEnvironment.getApplication(),
            () -> {
              reads.incrementAndGet();
              return clock.get();
            });
    controller.resume();
    controller.resume();
    int beforeTick = reads.get();
    clock.addAndGet(300);
    idle(300);
    assertEquals("분 경계에 판정·다음 예약 각 한 번", 2, reads.get() - beforeTick);
    controller.close();
    int afterClose = reads.get();
    broadcast(Intent.ACTION_TIMEZONE_CHANGED);
    idle(600_000);
    controller.resume();
    assertEquals(afterClose, reads.get());
    assertFalse(controller.isResumed());
  }

  @Test
  public void removedOrSelfRemovingViewListenersDoNotLeakAcrossNavigation() {
    AtomicInteger removed = new AtomicInteger(), once = new AtomicInteger();
    Runnable gone = removed::incrementAndGet;
    Runnable[] selfRemoving = new Runnable[1];
    selfRemoving[0] =
        () -> {
          once.incrementAndGet();
          controller.removeListener(selfRemoving[0]);
        };
    controller.addListener(gone);
    controller.removeListener(gone);
    controller.addListener(selfRemoving[0]);
    controller.resume();
    controller.pause();
    controller.resume();
    assertEquals(0, removed.get());
    assertEquals(1, once.get());
    assertEquals(3, notices.get());
  }

  @Test
  public void motionPreferenceNotifiesViewsWithoutReevaluatingTimeOrResumingTheController() {
    assertFalse(controller.isResumed());
    clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 22, 0, 0));
    controller.motionPreferenceChanged();
    assertEquals(1, notices.get());
    assertEquals("표시 설정 변경으로 시간대 판정을 하지 않음", LocalTimeTheme.Phase.MORNING, controller.phase());
    assertFalse(controller.isResumed());
    controller.close();
    controller.motionPreferenceChanged();
    assertEquals(1, notices.get());
  }
}
