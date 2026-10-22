package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 84 테스트 APK 전 캡처: 조합 두 모드를 인계 문서의 915×412·2048×838 계열, 글꼴 100/130%에서 찍고 바깥 스크롤 여부를 기록한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Build84ReviewTest {
  final NativeCombinationWorkbenchTest bench = new NativeCombinationWorkbenchTest();

  @Before public void isolate() throws Exception { bench.isolate(); }
  @After public void restore() { bench.restore(); }

  static boolean scrolls(View v) {
    if (v.isShown() && (v.canScrollVertically(1) || v.canScrollVertically(-1))) return true;
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
      if (scrolls(((ViewGroup) v).getChildAt(i))) return true;
    return false;
  }

  @Test public void bothCraftModesAtHandoffViewports() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "build84-craft");
    Files.createDirectories(output);
    StringBuilder report = new StringBuilder();
    for (int[] size : new int[][] {{915, 412, 160}, {2048, 838, 360}, {2048, 838, 480}})
      for (float scale : new float[] {1, 1.3f})
        for (boolean mix : new boolean[] {true, false}) {
          RuntimeEnvironment.setQualifiers("w" + Math.round(size[0] * 160f / size[2]) + "dp-h"
              + Math.round(size[1] * 160f / size[2]) + "dp-land-" + size[2] + "dpi");
          RuntimeEnvironment.setFontScale(scale);
          try (var controller = bench.flow.start()) {
            MainActivity host = controller.get();
            bench.prepare(host, true);
            if (mix) host.menus.mixDraft.addAll(Arrays.asList("C3", "C1"));
            host.menus.economy.craft(mix);
            bench.screens.layout(host, size[0], size[1], size[2]);
            bench.screens.review.await(() -> host.registrationArt().pendingCount() == 0);
            bench.screens.layout(host, size[0], size[1], size[2]);
            String name = (mix ? "mix-" : "result-") + size[0] + "x" + size[1] + "-dpi" + size[2]
                + "-font" + Math.round(scale * 100);
            bench.captures.writeScreenshot(host, output.resolve(name + ".png"), size[0], size[1]);
            // 기록만 한다(판정은 기존 시험이 한다): 화면 안 어떤 영역이든 세로로 스크롤 가능한지.
            report.append(name).append(" scrollable=").append(scrolls(host.root)).append('\n');
          }
        }
    Files.write(output.resolve("scroll-report.txt"), report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(report.length() > 0);
  }
}
