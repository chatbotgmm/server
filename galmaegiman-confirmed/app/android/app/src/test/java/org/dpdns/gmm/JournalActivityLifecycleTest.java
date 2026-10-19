package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Intent;
import android.os.Looper;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 오프라인에서도 시간과 복귀는 표시만 갱신하고 진행·전투·화면을 유지한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalActivityLifecycleTest {
  @Test
  public void reduceMotionAppliesImmediatelyAndRestoresItsSavedValueOnNewActivity() {
    NativeGameFlowTest fixtures = new NativeGameFlowTest();
    fixtures.isolate();
    var display = RuntimeEnvironment.getApplication().getSharedPreferences("native_display", 0);
    display.edit().clear().commit();
    try {
      try (var lifecycle =
          Robolectric.buildActivity(MainActivity.class).create().start().resume().visible()) {
        MainActivity host = lifecycle.get();
        assertTrue("기본값은 움직임 허용", host.motionEnabled);
        host.api.close();
        host.api =
            new NativeApi(host) {
              @Override
              void call(String path, JSONObject body, Result done) {
                throw new AssertionError("표시 설정에 API를 호출하면 안 됨: " + path);
              }
            };
        host.state = MainActivity.obj("me", MainActivity.obj("ticket", 277));
        host.battle = MainActivity.obj("turn", 7);
        JSONObject state = host.state, battle = host.battle;
        android.view.View page = host.root.getChildAt(0);
        String screen = host.screen;
        LocalTimeTheme.Phase phase = host.journalTime.phase();
        AtomicInteger notices = new AtomicInteger();
        host.journalTime.addListener(notices::incrementAndGet);
        host.reducedMotion(true);
        assertFalse(host.motionEnabled);
        assertTrue(display.getBoolean("reduce_motion", false));
        host.reducedMotion(false);
        assertTrue(host.motionEnabled);
        assertFalse(display.getBoolean("reduce_motion", true));
        assertEquals(2, notices.get());
        assertSame(state, host.state);
        assertSame(battle, host.battle);
        assertSame(page, host.root.getChildAt(0));
        assertEquals(screen, host.screen);
        assertEquals(phase, host.journalTime.phase());
        host.reducedMotion(true);
      }
      try (var lifecycle =
          Robolectric.buildActivity(MainActivity.class).create().start().resume().visible()) {
        MainActivity host = lifecycle.get();
        assertFalse("앱 재진입에도 움직임 줄이기를 보존", host.motionEnabled);
        assertTrue(host.motionResumed);
        host.reducedMotion(false);
      }
      try (var lifecycle = Robolectric.buildActivity(MainActivity.class).create()) {
        assertTrue("해제한 값도 다음 Activity에 반영", lifecycle.get().motionEnabled);
      }
    } finally {
      display.edit().clear().commit();
    }
  }

  @Test
  public void actualActivityResumePauseAndTimeChangeLeaveGameStateAndApiUntouched() {
    NativeGameFlowTest fixtures = new NativeGameFlowTest();
    fixtures.isolate();
    TimeZone previous = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var lifecycle = fixtures.start()) {
      MainActivity host = lifecycle.get();
      assertTrue(host.motionResumed);
      assertTrue(host.journalTime.isResumed());
      host.journalTime.close();
      AtomicLong clock = new AtomicLong(LocalTimeThemeTest.utc(2026, 10, 15, 9, 0, 0));
      host.journalTime = new JournalTimeController(host, clock::get);
      host.journalTime.resume();
      host.api.close();
      final int[] requests = {0};
      host.api =
          new NativeApi(host) {
            @Override
            void call(String path, JSONObject body, Result done) {
              requests[0]++;
              throw new AssertionError("시간대 표시에 API를 호출하면 안 됨: " + path);
            }
          };
      host.state = MainActivity.obj("me", MainActivity.obj("ticket", 277, "story", "1-3"));
      host.battle = MainActivity.obj("turn", 7, "phase", "action");
      JSONObject originalState = host.state, originalBattle = host.battle;
      android.view.View originalPage = host.root.getChildAt(0);
      String originalScreen = host.screen;
      lifecycle.pause();
      assertFalse(host.motionResumed);
      assertFalse(host.journalTime.isResumed());
      clock.set(LocalTimeThemeTest.utc(2026, 10, 15, 22, 0, 0));
      lifecycle.resume();
      assertTrue(host.motionResumed);
      assertEquals(LocalTimeTheme.Phase.NIGHT, host.journalTime.phase());
      clock.set(LocalTimeThemeTest.utc(2026, 10, 16, 12, 0, 0));
      host.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(LocalTimeTheme.Phase.DAY, host.journalTime.phase());
      assertSame(originalState, host.state);
      assertSame(originalBattle, host.battle);
      assertSame("화면을 재생성하지 않음", originalPage, host.root.getChildAt(0));
      assertEquals(originalScreen, host.screen);
      assertEquals(0, requests[0]);
    } finally {
      TimeZone.setDefault(previous);
    }
  }
}
