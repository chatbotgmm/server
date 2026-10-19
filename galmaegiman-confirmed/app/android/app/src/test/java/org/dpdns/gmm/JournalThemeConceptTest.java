package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.graphics.*;
import android.view.View;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 승인 전 스타일 시트를 원본 글꼴과 캐릭터로 Android Skia에서 촬영한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1006dp-h411dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalThemeConceptTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  @Test
  public void renderDirectionOnWidePhoneAndTablet() throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput", "");
    assumeTrue(!System.getProperty("gmm.nativeReviewTokenFile", "").isEmpty());
    Path folder = Path.of(output).resolveSibling("theme");
    Path background = folder.resolve("assets/headquarters.png");
    assumeTrue(Files.isRegularFile(background));
    Files.createDirectories(folder);
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      for (int[] size : new int[][] {{1006, 411}, {1280, 720}}) {
        String device = size[0] == 1006 ? "phone" : "tablet";
        for (String scene : new String[] {"title", "login", "home", "battle", "components"}) {
          JournalConceptView view = new JournalConceptView(h, scene, background.toString());
          view.measure(
              View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
          view.layout(0, 0, size[0], size[1]);
          Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
          view.draw(new Canvas(bitmap));
          try (OutputStream file =
              Files.newOutputStream(folder.resolve("journal-" + scene + "-" + device + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, file));
          }
          assertTrue(
              scene + " " + device + " 라벨 넘침: " + view.overflowing, view.overflowing.isEmpty());
          bitmap.recycle();
        }
      }
    }
  }
}
