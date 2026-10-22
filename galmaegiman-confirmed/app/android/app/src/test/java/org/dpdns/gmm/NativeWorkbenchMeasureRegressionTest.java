package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Regression coverage for the two real short-screen failures from the 875fdb0 run. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w780dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeWorkbenchMeasureRegressionTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeEconomyThemeReviewTest screens = new NativeEconomyThemeReviewTest();
  @Before public void isolate() throws Exception { screens.isolate(); }
  @After public void restore() { screens.restoreScale(); }

  @Test public void shortResultCarouselAndActionsHavePositiveUnclippedBounds() throws Exception {
    for (int[] size : new int[][] {{540,222}, {683,279}, {780,360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(2f);
      try (var controller = flow.start()) {
        MainActivity h = controller.get();
        NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(h);
        h.menus.economy.craft(false);
        screens.layout(h, size);
        for (String tag : new String[] {"craft-result-carousel", "craft-submit", "craft-quantity",
            "craft-material-picker", "craft-recipe-picker"}) {
          View view = h.root.findViewWithTag(tag);
          assertNotNull(tag, view);
          assertTrue(tag + " positive width/height", view.getWidth() > 0 && view.getHeight() > 0);
          Rect visible = new Rect();
          assertTrue(tag + " initially visible", view.getGlobalVisibleRect(visible));
          assertEquals(tag + " no horizontal clipping", view.getWidth(), visible.width());
          assertEquals(tag + " no vertical clipping", view.getHeight(), visible.height());
        }
        assertEquals(0, api.writes);
      }
    }
  }

  @Test public void emptyResultCopyFitsEveryRenderedLineAtTwoHundredPercent() throws Exception {
    for (int[] size : new int[][] {{540,222}, {683,279}, {780,360}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(2f);
      try (var controller = flow.start()) {
        MainActivity h = controller.get();
        NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(h);
        h.menus.economy.craft(true);
        screens.layout(h, size);
        View empty = h.root.findViewWithTag("mix-result-empty");
        assertNotNull(empty);
        for (View view : NativeNoScrollReviewTest.descendants(empty)) if (view instanceof TextView) {
          TextView label = (TextView) view;
          assertNotNull(label.getLayout());
          int width = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
          for (int line = 0; line < label.getLineCount(); line++) {
            assertTrue("No horizontal clipping: " + label.getText(), label.getLayout().getLineMax(line) <= width + 1);
            assertEquals(0, label.getLayout().getEllipsisCount(line));
          }
          assertTrue("No vertical text clipping", label.getLayout().getHeight()
              <= label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom() + 1);
        }
        assertEquals(0, api.writes);
      }
    }
  }
  @Test public void visibleExtentStillRejectsGenuinelyOversizedSingleLine() throws Exception {
    RuntimeEnvironment.setFontScale(2f);
    try (var controller = flow.start()) {
      MainActivity h = controller.get();
      TextView label = h.text("선택한 재료에 맞는 결과를 이곳에서 확인할 수 있어요", 20);
      label.setSingleLine(true);
      label.setEllipsize(null);
      label.measure(View.MeasureSpec.makeMeasureSpec(h.dp(80), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h.dp(80), View.MeasureSpec.EXACTLY));
      label.layout(0, 0, label.getMeasuredWidth(), label.getMeasuredHeight());
      int width = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
      assertNotNull(label.getLayout());
      assertEquals(0, label.getLayout().getEllipsisCount(0));
      assertTrue("Negative control: real visible text must exceed the unchanged clipping budget",
          label.getLayout().getLineMax(0) > width + 1);
    }
  }

}
