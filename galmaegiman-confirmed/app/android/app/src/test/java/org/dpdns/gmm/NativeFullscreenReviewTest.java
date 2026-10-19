package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Background coverage is separate from control safety, including runtime inset changes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeFullscreenReviewTest {
  final NativeEconomyThemeReviewTest review = new NativeEconomyThemeReviewTest();
  @Before public void isolate() { review.isolate(); }
  @After public void resetScale() { RuntimeEnvironment.setFontScale(1); }

  void measure(View view, int width, int height) {
    view.requestLayout();
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
  }
  Rect bounds(View v) { return new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom()); }

  @Test public void decorationFillsWindowButContentKeepsEveryInset() {
    GameRootLayout root = new GameRootLayout(RuntimeEnvironment.getApplication());
    GameSceneBackground scene = new GameSceneBackground(root.getContext());
    scene.setBackgroundColor(Color.BLUE);
    View content = new View(root.getContext()); content.setBackgroundColor(Color.RED);
    root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
    root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    for (int[] size : new int[][] {{915,412}, {780,360}, {1280,720}}) {
      // Both landscape rotations, visible bars, rounded corners, IME shown then hidden.
      for (Insets safe : new Insets[] {Insets.of(36,12,10,24), Insets.of(10,12,36,24),
          Insets.of(36,12,10,180), Insets.of(36,12,10,24), Insets.NONE}) {
        root.setPadding(safe.left, safe.top, safe.right, safe.bottom);
        measure(root, size[0], size[1]);
        assertEquals(new Rect(0,0,size[0],size[1]), bounds(scene));
        assertEquals(new Rect(safe.left,safe.top,size[0]-safe.right,size[1]-safe.bottom), bounds(content));
        Bitmap bitmap = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
        try {
          root.draw(new Canvas(bitmap));
          for (int x=0; x<size[0]; x++) {
            assertEquals(safe.top > 0 ? Color.BLUE : Color.RED, bitmap.getPixel(x,0));
            assertEquals(safe.bottom > 0 ? Color.BLUE : Color.RED, bitmap.getPixel(x,size[1]-1));
          }
        } finally { bitmap.recycle(); }
      }
    }
    assertFalse(scene.isClickable()); assertFalse(scene.isFocusable());
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, scene.getImportantForAccessibility());
  }

  @Test public void realNativePagesFillEdgesAndRetainSafeContent() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "fullscreen");
    Files.createDirectories(output);
    JSONArray metrics = new JSONArray();
    for (int[] size : new int[][] {{915,412,100}, {780,360,130}, {1280,720,100}}) {
      RuntimeEnvironment.setQualifiers("w"+size[0]+"dp-h"+size[1]+"dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2]/100f);
      for (String page : new String[] {"recruit", "shop", "craft", "team"}) {
        try (var controller = review.flow.start()) {
          MainActivity host = controller.get();
          if (page.equals("team")) {
            new NativeHomePartyFeedbackTest().prepare(host);
            host.selectionMode = "story"; host.menus.teamDraft = null; host.team();
          } else {
            new NativeEconomyThemeFixture().prepare(host);
            if (page.equals("recruit")) host.menus.economy.draw();
            else if (page.equals("shop")) host.menus.economy.shop();
            else host.menus.economy.craft(false);
          }
          review.ready(host,size);
          for (boolean rightCutout : new boolean[] {false,true}) {
            Insets safe = rightCutout ? Insets.of(10,12,36,24) : Insets.of(36,12,10,24);
            // ready() deliberately detaches the OS listener for deterministic native review.
            host.root.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            measure(host.root,size[0],size[1]);
            View scene = host.root.findViewWithTag("theme-scene-background");
            assertTrue(scene instanceof FullBleed);
            assertEquals(new Rect(0,0,size[0],size[1]),bounds(scene));
            assertEquals(new Rect(safe.left,safe.top,size[0]-safe.right,size[1]-safe.bottom),bounds(host.page));
            assertEquals(new RectF(safe.left,safe.top,size[0]-safe.right,size[1]-safe.bottom),host.safeBounds(size[0],size[1]));
            Bitmap bitmap = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            String name = page+"-"+size[0]+"x"+size[1]+"-font"+size[2]+(rightCutout?"-right":"-left");
            try {
              host.root.draw(new Canvas(bitmap));
              // Rendering the background alone provides exact expected edge pixels, including shade.
              Bitmap expected = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
              try {
                Canvas canvas = new Canvas(expected); scene.draw(canvas);
                host.root.getChildAt(1).draw(canvas);
                for (int x=0;x<size[0];x++) {
                  assertEquals(name+" top "+x,expected.getPixel(x,0),bitmap.getPixel(x,0));
                  assertEquals(name+" bottom "+x,expected.getPixel(x,size[1]-1),bitmap.getPixel(x,size[1]-1));
                }
                for (int y=0;y<size[1];y++) {
                  assertEquals(name+" left "+y,expected.getPixel(0,y),bitmap.getPixel(0,y));
                  assertEquals(name+" right "+y,expected.getPixel(size[0]-1,y),bitmap.getPixel(size[0]-1,y));
                }
              } finally { expected.recycle(); }
              try(OutputStream stream = Files.newOutputStream(output.resolve(name+".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
              }
            } finally { bitmap.recycle(); }
            metrics.put(new JSONObject().put("name",name).put("background",bounds(scene).toShortString())
                .put("content",bounds(host.page).toShortString()).put("allEdgePixelsMatchBackground",true));
            Files.write(output.resolve("metrics.json"),metrics.toString(2).getBytes(StandardCharsets.UTF_8));
          }
        }
      }
    }
    assertEquals(24,metrics.length());
  }

  @Test public void liveInsetsKeepKeyboardAndVisibleBarsAwayFromControls() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity host = controller.get();
      new NativeEconomyThemeFixture().prepare(host); host.menus.economy.draw();
      for (int keyboard : new int[] {0,180,0}) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(),Insets.of(36,0,0,0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(36,0,0,0))
            .setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,12,10,24))
            .setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,keyboard)).build();
        ViewCompat.dispatchApplyWindowInsets(host.root,insets);
        measure(host.root,915,412);
        assertEquals(36,host.root.getPaddingLeft());
        assertEquals(12,host.root.getPaddingTop());
        assertEquals(10,host.root.getPaddingRight());
        assertEquals(Math.max(24,keyboard),host.root.getPaddingBottom());
        assertEquals(new Rect(0,0,915,412),bounds(host.root.findViewWithTag("theme-scene-background")));
        assertEquals(new Rect(36,12,905,412-Math.max(24,keyboard)),bounds(host.page));
      }
    }
  }
}
