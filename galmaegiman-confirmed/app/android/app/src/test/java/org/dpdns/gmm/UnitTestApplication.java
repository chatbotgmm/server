package org.dpdns.gmm;

import android.app.Application;

/** Robolectric에는 Play 스토어가 없다. 필수 업데이트 확인만 「새 버전 없음」으로 끝내고 나머지는 실제 앱 그대로 시험한다. */
public class UnitTestApplication extends Application {
  @Override public void onCreate() {
    super.onCreate();
    NativeRequiredUpdate.skipPlayCheckForUnitTests = true;
  }
}
