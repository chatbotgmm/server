package org.dpdns.gmm;

import android.content.Context;
import android.view.View;
import android.widget.*;

/** 안전 영역을 일반 페이지에 적용하되 배경·Canvas 장면은 끝까지 그린다. */
final class GameRootLayout extends FrameLayout {
  GameRootLayout(Context c) {
    super(c);
    setClipToPadding(false);
    setBackgroundColor(JournalTheme.LEATHER);
  }

  @Override public void removeAllViews() {
    if (getContext() instanceof MainActivity) {
      MainActivity host = (MainActivity) getContext();
      if (host.backgroundEffects != null) host.backgroundEffects.reset();
    }
    super.removeAllViews();
  }

  @Override
  protected void measureChildWithMargins(
      View child, int parentWidthSpec, int widthUsed, int parentHeightSpec, int heightUsed) {
    if (child instanceof FullBleed) {
      // Measure full-bleed content correctly on the FIRST pass. Measuring once
      // with FrameLayout padding and again without it made BattleView alternate
      // sizes and recreate its controls every traversal. AppCompat autosize then
      // never received a follow-up traversal on the same button instance.
      child.measure(
          MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(parentWidthSpec), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(parentHeightSpec), MeasureSpec.EXACTLY));
    } else {
      super.measureChildWithMargins(child, parentWidthSpec, widthUsed, parentHeightSpec, heightUsed);
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
