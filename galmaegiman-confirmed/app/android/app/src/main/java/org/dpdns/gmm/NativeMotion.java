package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** 시스템 애니메이션 끄기를 따른다. appEnabled는 화면/테스트 내부의 비활성 게이트다. */
final class NativeMotion {
  private NativeMotion() {}

  static void migrateLegacyPreference(Context context) {
    android.content.SharedPreferences display =
        context.getSharedPreferences("native_display", Context.MODE_PRIVATE);
    if (display.contains("reduce_motion")) display.edit().remove("reduce_motion").apply();
    // Never write any platform animation/accessibility setting.
  }

  static boolean enabled(Context context, boolean appEnabled) {
    if (!appEnabled) return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled();
    return legacyEnabled(context, true);
  }

  static boolean legacyEnabled(Context context, boolean appEnabled) {
    return appEnabled
        && Settings.Global.getFloat(
                context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            != 0f;
  }
}
