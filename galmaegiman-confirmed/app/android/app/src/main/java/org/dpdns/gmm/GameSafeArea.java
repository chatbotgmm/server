package org.dpdns.gmm;

import android.os.Build;
import android.view.*;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/** 컷아웃·현재 표시되는 시스템 바·둥근 모서리·키보드의 최대 여백을 사용한다. */
final class GameSafeArea {
  static Insets combine(Insets cutout, Insets bars, Insets ime, Insets rounded) {
    return Insets.of(
        Math.max(Math.max(cutout.left, bars.left), rounded.left),
        Math.max(Math.max(cutout.top, bars.top), rounded.top),
        Math.max(Math.max(cutout.right, bars.right), rounded.right),
        Math.max(Math.max(Math.max(cutout.bottom, bars.bottom), rounded.bottom), ime.bottom));
  }

  static int cornerInset(int radius) {
    return (int) Math.ceil(radius * (1 - 1 / Math.sqrt(2)));
  }

  static Insets read(WindowInsetsCompat insets) {
    Insets corners = Insets.NONE;
    if (Build.VERSION.SDK_INT >= 31 && insets.toWindowInsets() != null) {
      WindowInsets nativeInsets = insets.toWindowInsets();
      int[] edge = new int[4];
      for (int position :
          new int[] {
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT
          }) {
        RoundedCorner corner = nativeInsets.getRoundedCorner(position);
        if (corner == null) continue;
        int s = cornerInset(corner.getRadius());
        if (position == RoundedCorner.POSITION_TOP_LEFT
            || position == RoundedCorner.POSITION_BOTTOM_LEFT) edge[0] = Math.max(edge[0], s);
        else edge[2] = Math.max(edge[2], s);
        if (position == RoundedCorner.POSITION_TOP_LEFT
            || position == RoundedCorner.POSITION_TOP_RIGHT) edge[1] = Math.max(edge[1], s);
        else edge[3] = Math.max(edge[3], s);
      }
      corners = Insets.of(edge[0], edge[1], edge[2], edge[3]);
    }
    return combine(
        insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()),
        insets.getInsets(WindowInsetsCompat.Type.systemBars()),
        insets.getInsets(WindowInsetsCompat.Type.ime()),
        corners);
  }
}
