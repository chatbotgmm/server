package org.dpdns.gmm;

import android.app.Application;

/** Robolectric에는 Play 스토어가 없다. 필수 업데이트 확인은 「새 버전 없음」, 시작 리소스 준비는 즉시 완료로 끝내고 나머지는 실제 앱 그대로 시험한다. */
public class UnitTestApplication extends Application implements org.robolectric.TestLifecycleApplication {
  @Override public void onCreate() {
    super.onCreate();
    NativeRequiredUpdate.skipPlayCheckForUnitTests = true;
    // 시작 리소스 준비(이미지 약 240개 백그라운드 디코딩)는 LocalGameArtTest·GameResourcePreparation 시험에서만 직접 다룬다.
    GameResourcePreparation.skipForUnitTests = true;
  }

  @Override public void beforeTest(java.lang.reflect.Method method) {}
  @Override public void prepareTest(Object test) {}
  @Override public void afterTest(java.lang.reflect.Method method) {
    // LocalGameArt belongs to the process, while Robolectric clears each test's
    // main-looper queue. Finish worker and UI deliveries before that queue reset;
    // otherwise the discarded callback cannot decrement the shared delivery count.
    // Drain actual work rather than clearing caches, counters, or failed assertions.
    try { LocalGameArtTest.drain(); }
    catch (Exception failed) { throw new AssertionError("Shared art cleanup after "+method.getName(),failed); }
  }
}
