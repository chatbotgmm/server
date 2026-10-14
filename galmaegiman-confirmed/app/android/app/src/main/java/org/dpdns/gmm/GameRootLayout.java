package org.dpdns.gmm;

import android.content.Context;
import android.view.View;
import android.widget.*;

/** 안전 영역을 일반 페이지에 적용하되 배경·Canvas 장면은 끝까지 그린다. */
final class GameRootLayout extends FrameLayout {
  GameRootLayout(Context c) {
    super(c);
    setClipToPadding(false);
    setBackgroundColor(0xff1f180f);
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    super.onMeasure(ws, hs);
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v instanceof FullBleed && v.getVisibility() != GONE)
        v.measure(
            MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v instanceof FullBleed && v.getVisibility() != GONE)
        v.layout(0, 0, getWidth(), getHeight());
    }
  }
}
