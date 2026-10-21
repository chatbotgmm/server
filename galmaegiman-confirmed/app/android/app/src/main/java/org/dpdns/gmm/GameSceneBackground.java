package org.dpdns.gmm;

import android.content.Context;
import android.view.View;

/** Decorative scenery fills the window; sibling controls retain GameRootLayout's safe insets. */
final class GameSceneBackground extends View implements FullBleed {
  GameSceneBackground(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setFocusable(false);
    setClickable(false);
  }
}
