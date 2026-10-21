package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.View;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** 제목 여백은 합산하지 않고 최소값으로 보장하며 기존 글꼴/줄바꿈/세로 크기를 유지한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MenuHeadingPaddingTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();

  @Before public void isolate() { flow.isolate(); }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  @Test public void backgroundsKeepMinimumInsetsWithoutInflatingHeightOrExistingInsets() {
    for (String size : new String[] {"w915dp-h412dp-land-mdpi", "w1280dp-h720dp-land-mdpi"}) {
      RuntimeEnvironment.setQualifiers(size);
      try (var controller = flow.start()) {
        MainActivity h = controller.get();
        MenuKit k = new MenuKit(h);
        TextView title = h.text("가능한 결과", 17);
        title.setPadding(h.dp(4), h.dp(3), h.dp(20), h.dp(5));
        float textSize = title.getTextSize();
        int minimumHeight = title.getMinimumHeight(), maxLines = title.getMaxLines();
        int inset = h.dp(h.compact() ? 12 : 16);
        for (int pass = 0; pass < 3; pass++) {
          k.headingBackground(title);
          assertEquals(inset, title.getPaddingLeft());
          assertEquals(h.dp(20), title.getPaddingRight());
          assertEquals(h.dp(3), title.getPaddingTop());
          assertEquals(h.dp(5), title.getPaddingBottom());
          assertEquals(minimumHeight, title.getMinimumHeight());
          assertEquals(maxLines, title.getMaxLines());
          assertEquals(textSize, title.getTextSize(), 0);
        }
        TextView section = k.section("얻는 곳");
        assertEquals(inset, section.getPaddingLeft());
        assertEquals(inset, section.getPaddingRight());
        assertEquals(h.dp(7), section.getPaddingTop());
        assertEquals(h.dp(7), section.getPaddingBottom());
      }
    }
  }

  @Test public void longKoreanHeadingKeepsWrappingInsteadOfEllipsizing() {
    for (float scale : new float[] {1f, 1.3f}) {
      RuntimeEnvironment.setFontScale(scale);
      try (var controller = flow.start()) {
        MainActivity h = controller.get();
        TextView title = h.text("꺼지지 않는 그림자의 날갯짓", 17);
        new MenuKit(h).headingBackground(title);
        title.measure(View.MeasureSpec.makeMeasureSpec(h.dp(180), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        title.layout(0, 0, title.getMeasuredWidth(), title.getMeasuredHeight());
        assertNotNull(title.getLayout());
        assertTrue(title.getLineCount() > 1);
        assertNull(title.getEllipsize());
        for (int line = 0; line < title.getLineCount(); line++) {
          assertEquals(0, title.getLayout().getEllipsisCount(line));
          assertTrue(title.getLayout().getLineWidth(line)
              <= title.getWidth() - title.getCompoundPaddingLeft() - title.getCompoundPaddingRight());
        }
        assertTrue(title.getLayout().getHeight()
            <= title.getHeight() - title.getCompoundPaddingTop() - title.getCompoundPaddingBottom());
      }
    }
  }

  @Test public void fixedSkillKeepsItsExistingOneLinePolicyAndFullAccessibleName() {
    try (var controller = flow.start()) {
      MainActivity h = controller.get();
      String fullName = "꺼지지 않는 그림자의 날갯짓";
      android.widget.LinearLayout card = new NativeMenus(h).library.fixedSkill(
          MainActivity.obj("name", fullName, "cat", "phys", "power", 40));
      TextView title = (TextView) card.getChildAt(0);
      assertEquals(fullName, title.getText().toString());
      assertEquals(fullName, title.getContentDescription().toString());
      assertEquals(1, title.getMaxLines());
      assertEquals(android.text.TextUtils.TruncateAt.END, title.getEllipsize());
      assertEquals(h.dp(h.compact() ? 12 : 16), title.getPaddingLeft());
      assertEquals(h.dp(3), title.getPaddingTop());
      assertEquals(h.dp(3), title.getPaddingBottom());
    }
  }

}
