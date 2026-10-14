package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 웹의 반복 모션을 같은 주기로 재생한다. 시스템 애니메이션 해제도 따른다. */
final class SceneMotion {
  final View owner;
  final MainActivity host;
  ValueAnimator clock;
  float millis;

  SceneMotion(View owner, MainActivity host) {
    this.owner = owner;
    this.host = host;
  }

  void start() {
    if (!host.motionEnabled
        || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) return;
    close();
    clock = ValueAnimator.ofFloat(0, 120000);
    clock.setDuration(120000);
    clock.setRepeatCount(ValueAnimator.INFINITE);
    clock.setInterpolator(new LinearInterpolator());
    clock.addUpdateListener(
        a -> {
          millis = (float) a.getAnimatedValue();
          owner.postInvalidateOnAnimation();
        });
    clock.start();
  }

  float pulse(float period, float offset) {
    return !host.motionEnabled || clock == null
        ? 0
        : (float) ((1 - Math.cos((millis + offset) * Math.PI * 2 / period)) / 2);
  }

  void close() {
    if (clock != null) clock.cancel();
    clock = null;
    millis = 0;
  }
}
