package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.widget.Button;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 재화 정보 홈의 실측 가독성·기존 조작·상태를 검사한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CurrencyDisplayReviewTest {
  final NativeEconomyThemeReviewTest review = new NativeEconomyThemeReviewTest();
  @Before public void isolate() { review.isolate(); }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  @Test public void sharedDisplayRetainsClickAndBusySemantics() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity host = controller.get();
      new NativeEconomyThemeFixture().prepare(host);
      final int[] clicks = {0};
      Button button = new MenuKit(host).action("u-snack", "2,147,483,647", () -> clicks[0]++);
      JournalButton.keepLabelDuringOperation(button);
      CurrencyDisplay.bind(host, button, "snack");
      assertTrue(button.hasOnClickListeners());
      assertFalse(((JournalButton) button).operationLabelEnabled);
      assertTrue(button.getBackground() instanceof CurrencyDisplay);
      assertNotNull(button.getCompoundDrawables()[0]);
      host.busy = false; button.performClick(); assertEquals(1, clicks[0]);
      host.busy = true; button.performClick(); assertEquals(1, clicks[0]);
      button.setEnabled(false);
      assertEquals(CurrencyDisplay.MUTED, button.getCurrentTextColor());
      assertEquals("2,147,483,647", button.getText().toString());
      button.setEnabled(true); host.busy = false;
      button.performClick(); assertEquals(2, clicks[0]);
      assertEquals(CurrencyDisplay.TEXT, button.getCurrentTextColor());
    }
  }

  @Test public void insetSurfaceKeepsReadableStatesWithinBounds() {
    CurrencyDisplay display = new CurrencyDisplay(1);
    display.setBounds(8, 8, 158, 56);
    int[][] states = {{android.R.attr.state_enabled},
        {android.R.attr.state_enabled, android.R.attr.state_pressed}, {},
        {android.R.attr.state_enabled, android.R.attr.state_focused}};
    for (int[] state : states) {
      Bitmap image = Bitmap.createBitmap(166, 64, Bitmap.Config.ARGB_8888);
      try {
        display.setState(state); display.draw(new Canvas(image));
        assertEquals(0, image.getPixel(80, 9));
        assertEquals(0, image.getPixel(80, 55));
        assertEquals(0, image.getPixel(7, 32));
        assertEquals(0, image.getPixel(158, 32));
        assertEquals(state.length > 1 && state[1] == android.R.attr.state_pressed
            ? CurrencyDisplay.PRESSED : CurrencyDisplay.NAVY, image.getPixel(80, 32));
      } finally { image.recycle(); }
    }
  }

  @Test public void renderMeasuredBalancesAcrossSizes() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "currency-display");
    Files.createDirectories(output);
    JSONObject geometry = new JSONObject(), findings = new JSONObject();
    List<String> failures = new ArrayList<>();
    for (int[] size : NativeEconomyThemeReviewTest.SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
      JSONArray problems = new JSONArray(), displays = new JSONArray();
      findings.put(name, problems);
      JSONObject audit = new JSONObject().put("passed", false).put("displays", displays);
      geometry.put(name, audit);
      try (var controller = review.flow.start()) {
        MainActivity host = controller.get();
        try {
          NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(host);
          for (String kind : new String[] {"snack", "peanut", "credits"}) host.me().put(kind, Integer.MAX_VALUE);
          host.menus.economy.draw(); review.ready(host, size);
          int previousRight = 0;
          for (String kind : new String[] {"snack", "peanut", "credits"}) {
            Button button = host.root.findViewWithTag("currency-" + kind);
            assertNotNull(kind, button);
            Rect bounds = review.geometry.bounds(button, host.root);
            float textWidth = button.getPaint().measureText(button.getText().toString());
            int available = button.getWidth() - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight();
            displays.put(MainActivity.obj("kind", kind, "text", button.getText().toString(),
                "width", button.getWidth(), "height", button.getHeight(), "textWidth", textWidth,
                "availableWidth", available, "left", bounds.left, "top", bounds.top,
                "right", bounds.right, "bottom", bounds.bottom));
            assertEquals("2,147,483,647", button.getText().toString());
            assertTrue(button.getBackground() instanceof CurrencyDisplay);
            assertTrue(button.hasOnClickListeners());
            assertFalse(((JournalButton) button).operationLabelEnabled);
            assertTrue(button.getContentDescription().toString().contains("2,147,483,647"));
            assertNotNull(button.getCompoundDrawables()[0]);
            assertTrue(button.getWidth() >= host.dp(48)); assertTrue(button.getHeight() >= host.dp(48));
            assertTrue(textWidth <= available);
            assertTrue(bounds.left >= previousRight); previousRight = bounds.right;
            assertTrue(bounds.top >= 0 && bounds.bottom <= size[1] && bounds.right <= size[0]);
          }
          assertEquals(0, api.writes); audit.put("passed", true);
        } catch (Throwable failure) {
          failures.add(name + ": " + failure); problems.put(failure.toString());
        }
        Bitmap image = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        try {
          host.root.draw(new Canvas(image));
          try (OutputStream stream = Files.newOutputStream(output.resolve(name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
          }
        } finally { image.recycle(); }
      }
    }
    Files.write(output.resolve("geometry.json"), geometry.toString(2).getBytes(StandardCharsets.UTF_8));
    Files.write(output.resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
    assertTrue(String.join("\n", failures), failures.isEmpty());
  }
}
