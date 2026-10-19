package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.core.view.ViewCompat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Native platform action-bar regression: its ScrollView wrapper must not hide clipped actions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class GameDialogActionLayoutTest {
  static final int[][] SIZES = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRealtimeTest transport = new NativeRealtimeTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();

  @Before public void isolate() {
    transport.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  ActivityController<MainActivity> start(int[] size) {
    RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
    RuntimeEnvironment.setFontScale(size[2] / 100f);
    ActivityController<MainActivity> controller = flow.start();
    MainActivity h = controller.get();
    transport.install(h); h.guide.enabled = false;
    ViewCompat.setOnApplyWindowInsetsListener(h.root, null);
    h.root.setPadding(size[0] == 1006 ? 36 : 0, size[0] == 1006 ? 10 : 0,
        size[0] == 1006 ? 10 : 0, size[0] == 1006 ? 10 : 0);
    h.base("대전 결과", "realtimeResult");
    review.viewport(h, size[0], size[1]);
    return controller;
  }

  AlertDialog dialog(MainActivity h, int count, int[] clicked) {
    GameDialog builder = new GameDialog(h).setTitle("승리").setView(h.text("대전 결과가 저장됐어요.", 18))
        .setPositiveButton("모드 선택", (dialog, which) -> clicked[0]++);
    if (count >= 2) builder.setNegativeButton("취소", (dialog, which) -> clicked[1]++);
    if (count >= 3) builder.setNeutralButton("다시 보기", (dialog, which) -> clicked[2]++);
    return builder.show();
  }

  void strictActions(MainActivity h, AlertDialog dialog, View decor, int count) {
    assertTrue(decor.isAttachedToWindow() && decor.isShown());
    int checked = 0;
    for (int id : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
      Button button = dialog.getButton(id);
      if (button == null || !button.isShown()) continue;
      assertTrue(button.getParent() instanceof LinearLayout);
      LinearLayout row = (LinearLayout) button.getParent();
      assertFalse("Fixed action boxes must not be shifted by baseline descent", row.isBaselineAligned());
      assertTrue("The real content clip remains enabled", row.getClipChildren() && row.getClipToPadding());
      // Intentionally do not exempt the platform's buttonPanel ScrollView ancestor.
      geometry.fixedButton(h, decor, button);
      assertTrue(button.getWidth() >= h.dp(48));
      checked++;
    }
    assertEquals(count, checked);
  }

  void draw(View decor) {
    Bitmap pixels = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
    try { decor.draw(new Canvas(pixels)); }
    finally { pixels.recycle(); }
  }

  @Test public void oneTwoAndThreeActionsStayFullyVisibleBeforeAndAfterNativeDraw() {
    for (int[] size : SIZES) for (int count = 1; count <= 3; count++) {
      try (ActivityController<MainActivity> controller = start(size)) {
        MainActivity h = controller.get(); int[] clicked = new int[3];
        AlertDialog dialog = dialog(h, count, clicked);
        View decor = geometry.layoutDialog(h);
        strictActions(h, dialog, decor, count);
        draw(decor);
        strictActions(h, dialog, decor, count);
        int action = count == 1 ? AlertDialog.BUTTON_POSITIVE
            : count == 2 ? AlertDialog.BUTTON_NEGATIVE : AlertDialog.BUTTON_NEUTRAL;
        dialog.getButton(action).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, clicked[count - 1]);
        assertEquals(1, clicked[0] + clicked[1] + clicked[2]);
        assertFalse(dialog.isShowing());
      }
    }
  }

  @Test public void originalBaselineMarginRegressionRemainsDetectableInsidePlatformScrollView() {
    try (ActivityController<MainActivity> controller = start(SIZES[0])) {
      MainActivity h = controller.get(); AlertDialog dialog = dialog(h, 1, new int[3]);
      View decor = geometry.layoutDialog(h); Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
      LinearLayout row = (LinearLayout) button.getParent();
      assertTrue("API35 wraps standard actions in its own scroll container", geometry.hasScrollingAncestor(button, decor));
      strictActions(h, dialog, decor, 1);
      // Negative control: reproduce the original platform baseline + 4dp margins.
      // This confirms the geometry check still catches the real clipped 48dp box.
      row.setBaselineAligned(true);
      // Re-layout without invoking any shared assertions: the negative control
      // must keep working if the generic dialog helper later grows stricter.
      geometry.frame();
      View negativeDecor = decor;
      Runnable measure = () -> {
        review.force(negativeDecor);
        negativeDecor.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h.root.getHeight() - h.root.getPaddingTop() - h.root.getPaddingBottom() - h.dp(16), View.MeasureSpec.AT_MOST));
        negativeDecor.layout(0, 0, negativeDecor.getMeasuredWidth(), negativeDecor.getMeasuredHeight());
      };
      measure.run(); review.preparePreDraw(negativeDecor, measure);
      Rect own = geometry.bounds(button, decor), visible = geometry.visibleIntersection(button, decor);
      assertEquals(h.dp(48), own.height());
      assertEquals("Original top edge is clipped by the row's top padding", own.top + h.dp(4), visible.top);
      assertEquals(own.bottom, visible.bottom);
      row.setBaselineAligned(false);
      decor = geometry.layoutDialog(h);
      strictActions(h, dialog, decor, 1);
      draw(decor);
      strictActions(h, dialog, decor, 1);
      dialog.dismiss();
    }
  }
}
