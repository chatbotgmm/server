package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 Android View와 게임 글꼴로 비교창 상단·하단 및 확정 버튼을 검토한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTestGrowthComparison {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGrowthComparisonTest fixture = new NativeGrowthComparisonTest();

  @Before
  public void startPausedAndIsolated() throws Exception {
    review.scheduledFramesUseThePausedLooperClock();
    fixture.isolate();
    Files.createDirectories(Path.of(System.getProperty("gmm.nativeReviewOutput")));
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  void fullText(View view) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      assertNotNull("텍스트 배치 필요: " + text.getText(), text.getLayout());
      for (int i = 0; i < text.getLineCount(); i++)
        assertEquals("비교 설명 말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
      assertTrue(
          "비교 설명 높이 부족: " + text.getText(),
          text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
              >= text.getLayout().getHeight());
    }
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) fullText(parent.getChildAt(i));
    }
  }

  void captureBothEnds(MainActivity host, String name, String comparisonTag) throws Exception {
    review.popup(host, name + "-top");
    View decor = host.visibleDialog.getWindow().getDecorView();
    fullText(decor.findViewWithTag(comparisonTag));
    for (int type : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
      Button button = host.visibleDialog.getButton(type);
      assertTrue("확정·취소 터치 영역 확보", button.getHeight() >= host.dp(48));
    }
    ScrollView scroll = decor.findViewWithTag("growth-comparison-scroll");
    scroll.fullScroll(View.FOCUS_DOWN);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    review.popup(host, name + "-bottom");
    fullText(decor.findViewWithTag(comparisonTag));
    host.visibleDialog.dismiss();
  }

  @Test
  public void phoneAndTabletLargeFontComparisonsKeepAllContentScrollableAndActionsVisible()
      throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(1.3f);
      try (ActivityController<MainActivity> controller = fixture.start()) {
        MainActivity host = controller.get();
        host.root.setPadding(36, 10, 8, 10);
        review.viewport(host, size[0], size[1]);
        host.menus.library.compareReplacement(
            "o1", "learn", fixture.oldSkill(), fixture.nextSkill());
        captureBothEnds(
            host, "growth-" + size[0] + "-font130-skill-comparison", "growth-skill-comparison");
        host.menus.library.comparePromotion("o1", fixture.award());
        captureBothEnds(
            host,
            "growth-" + size[0] + "-font130-promotion-comparison",
            "growth-promotion-comparison");
      }
    }
  }
}
