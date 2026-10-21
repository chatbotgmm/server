package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

/** Compact rounded phase actions; disabled state retains its recognizable surface. */
final class PhaseButtonStyle {
  private PhaseButtonStyle() {}
  static void apply(MainActivity h, Button button, boolean primary) {
    int fill = primary ? 0xffd7b779 : JournalTheme.PAPER;
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[] {-android.R.attr.state_enabled}, surface(h, fill, 0xff8d8b80));
    states.addState(new int[] {android.R.attr.state_pressed}, surface(h, 0xff285566, JournalTheme.BRASS));
    states.addState(new int[] {android.R.attr.state_focused}, surface(h, fill, JournalTheme.FOCUS));
    states.addState(new int[] {}, surface(h, fill, JournalTheme.BRASS));
    button.setBackground(states);
    button.setBackgroundTintList(null);
    button.setTextColor(new ColorStateList(new int[][] {
        {-android.R.attr.state_enabled}, {android.R.attr.state_pressed}, {}},
        new int[] {0xff687375, JournalTheme.WHITE, JournalTheme.INK}));
    button.setMinHeight(h.dp(48)); button.setMinimumHeight(h.dp(48));
    button.setAllCaps(false);
  }
  static GradientDrawable surface(MainActivity h, int fill, int border) {
    GradientDrawable result = new GradientDrawable();
    result.setColor(fill); result.setCornerRadius(h.dp(16)); result.setStroke(h.dp(2), border);
    return result;
  }
}
