package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** 앱 설정과 시스템의 애니메이션 끄기를 지원하는 모든 Android 버전에서 함께 따른다. */
final class NativeMotion {
  private NativeMotion() {}

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
