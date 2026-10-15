package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Export the proposed final visual system; does not build or install an APK. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1006dp-h411dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FinalVisionRenderTest {
  @Test
  public void exportTwentyTwoDistinctScreens() throws Exception {
    org.junit.Assume.assumeTrue(
        "1".equals(System.getenv("GMM_RENDER_FINAL_VISION"))
            && !System.getProperty("gmm.nativeReviewTokenFile", "").isEmpty());
    Path out = Path.of(System.getProperty("gmm.nativeReviewOutput")).resolveSibling("final-vision");
    Files.createDirectories(out.resolve("images"));
    Files.createDirectories(out.resolve("thumbs"));
    String[] scenes = {
      "title",
      "login",
      "home",
      "chapters",
      "dialogue",
      "recruit",
      "recruit-result",
      "collection",
      "craft",
      "expedition",
      "shop",
      "team",
      "battle",
      "skills",
      "augment",
      "reward",
      "growth",
      "combo",
      "types",
      "missions",
      "settings",
      "items"
    };
    FinalVisionKit.warnings.clear();
    for (int i = 0; i < scenes.length; i++) {
      String scene = scenes[i], name = String.format("%02d-%s", i + 1, scene);
      Bitmap full = render(scene, 2560, 1048);
      try (OutputStream f = Files.newOutputStream(out.resolve(name + ".png"))) {
        assertTrue(full.compress(Bitmap.CompressFormat.PNG, 100, f));
      }
      try (OutputStream f = Files.newOutputStream(out.resolve("images/" + name + ".webp"))) {
        assertTrue(full.compress(Bitmap.CompressFormat.WEBP_LOSSY, 91, f));
      }
      full.recycle();
      Bitmap small = render(scene, 640, 262);
      try (OutputStream f = Files.newOutputStream(out.resolve("thumbs/" + name + ".webp"))) {
        assertTrue(small.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, f));
      }
      small.recycle();
    }
    Files.write(
        out.resolve("render-warnings.txt"),
        String.join("\n", FinalVisionKit.warnings)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue("시안 라벨 넘침: " + FinalVisionKit.warnings, FinalVisionKit.warnings.isEmpty());
    for (String version : new String[] {"before", "after"}) {
      Files.createDirectories(out.resolve(version));
      Path source = out.resolveSibling(version.equals("before") ? "ux72" : "ux73");
      // Prior versions live one directory higher than this design collection.
      source =
          out.getParent().resolve(version.equals("before") ? "ux72" : "ux73").resolve("native");
      if (!Files.isDirectory(source)) continue;
      try (var paths = Files.list(source)) {
        for (Path file : paths.filter(f -> f.getFileName().toString().endsWith(".png")).toList()) {
          String name = file.getFileName().toString();
          if (!name.startsWith("phone-")
              && !name.startsWith("ux72-reward-915")
              && !name.equals("ux72-acquisition-915.png")
              && !name.equals("growth-915-font130-promotion-comparison-top.png")) continue;
          if (Files.exists(out.resolve(version).resolve(name.replace(".png", ".webp")))) continue;
          Bitmap b = BitmapFactory.decodeFile(file.toString());
          try (OutputStream f =
              Files.newOutputStream(out.resolve(version).resolve(name.replace(".png", ".webp")))) {
            assertTrue(b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 91, f));
          }
          b.recycle();
        }
      }
    }
  }

  Bitmap render(String scene, int w, int h) {
    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    c.scale(w / 1280f, h / 524f);
    var ctx = RuntimeEnvironment.getApplication();
    switch (scene) {
      case "title":
      case "login":
      case "home":
      case "chapters":
      case "dialogue":
        new FinalVisionJourney(ctx, c).draw(scene);
        break;
      case "recruit":
      case "recruit-result":
      case "collection":
      case "craft":
      case "expedition":
      case "shop":
        new FinalVisionCollection(ctx, c).draw(scene);
        break;
      case "team":
      case "battle":
      case "skills":
      case "augment":
      case "reward":
        new FinalVisionBattle(ctx, c).draw(scene);
        break;
      default:
        new FinalVisionStrategy(ctx, c).draw(scene);
    }
    return b;
  }
}
