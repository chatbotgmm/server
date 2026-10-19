package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** API 26 전 설정을 읽는 분기를 준비된 심사 SDK에서 검증한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeMotionTest {
  @Test
  public void systemOffSkipsAnimationAndRestoringScaleKeepsAppPreference() {
    Context context = RuntimeEnvironment.getApplication();
    float original =
        Settings.Global.getFloat(
            context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    try {
      Settings.Global.putFloat(
          context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
      assertFalse(NativeMotion.legacyEnabled(context, true));
      Settings.Global.putFloat(
          context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, .5f);
      assertTrue(NativeMotion.legacyEnabled(context, true));
      assertFalse(NativeMotion.legacyEnabled(context, false));
      Settings.Global.putFloat(
          context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
      assertTrue(NativeMotion.legacyEnabled(context, true));
      assertFalse(NativeMotion.legacyEnabled(context, false));
      assertFalse(NativeMotion.enabled(context, false));
    } finally {
      Settings.Global.putFloat(
          context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, original);
    }
  }
}
