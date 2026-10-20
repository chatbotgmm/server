package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

/** 실제 챕터 카드의 입력·잠금·원본 그림·짧은 안전영역 증거. 서버 쓰기 없음. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StorySelectionCardTest {
  final NativeMenusThemeReviewTest review = new NativeMenusThemeReviewTest();
  @Before public void isolate() { review.isolate(); }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  void tapCard(View card) {
    long time = android.os.SystemClock.uptimeMillis();
    // 위쪽 그림 영역과 아래쪽 텍스트를 한 카드의 실제 터치 대상으로 처리한다.
    android.view.MotionEvent down = android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,card.getWidth()/2f,Math.max(8,card.getHeight()/4f),0);
    android.view.MotionEvent up = android.view.MotionEvent.obtain(time,time+32,android.view.MotionEvent.ACTION_UP,card.getWidth()/2f,Math.max(8,card.getHeight()/4f),0);
    try { card.dispatchTouchEvent(down); card.dispatchTouchEvent(up); }
    finally { down.recycle(); up.recycle(); }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
  }

  @Test public void wholeCardEntersExactlyOnceAndBackReturnsToChapters() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity h = controller.get(); var api = review.prepare(h, "story");
      review.show(h, "story", new int[] {915, 412, 100});
      View card = h.root.findViewWithTag("story-chapter-0");
      assertTrue(card.isEnabled()); tapCard(card);
      assertEquals("stages", h.screen); int epoch = h.epoch;
      card.performClick(); assertEquals(epoch, h.epoch);
      h.back(); assertEquals("story", h.screen);
      assertNotNull(h.root.findViewWithTag("story-chapter-0"));
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void lockedCardCannotEnterEvenThroughProgrammaticClick() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity h = controller.get(); var api = review.prepare(h, "story");
      review.show(h, "story", new int[] {915, 412, 100});
      StorySelectionCard card = h.root.findViewWithTag("story-chapter-1");
      int chapter = h.chapter, epoch = h.epoch; String state = h.state.toString();
      assertTrue(card.locked); assertFalse(card.isEnabled());
      tapCard(card); card.performClick(); card.performClick();
      assertEquals("story", h.screen); assertEquals(chapter, h.chapter); assertEquals(epoch, h.epoch);
      assertEquals(state, h.state.toString()); assertNull(h.visibleDialog); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void busyCardDoesNotConsumeItsLaterValidSelection() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity h = controller.get(); review.prepare(h, "story");
      review.show(h, "story", new int[] {915, 412, 100});
      StorySelectionCard card = h.root.findViewWithTag("story-chapter-0");
      h.busy = true; card.performClick(); assertFalse(card.entered); assertEquals("story", h.screen);
      h.busy = false; card.performClick(); assertTrue(card.entered); assertEquals("stages", h.screen);
    }
  }

  @Test public void obsoleteButtonsDisappearButBattleStartRemains() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity h = controller.get(); review.prepare(h, "story");
      review.show(h, "story", new int[] {915, 412, 100});
      String labels = review.flow.labels(h.root);
      assertFalse(labels.contains("부 소개")); assertFalse(labels.contains("합동기"));
      assertFalse(labels.contains("전투 선택")); assertNull(h.root.findViewWithTag("story-enter-chapter-0"));
      h.root.findViewWithTag("story-chapter-0").performClick();
      assertNotNull(h.root.findViewWithTag("story-stage-start"));
    }
  }

  @Test public void canonicalStageSceneAndRawLockDataStayUnchanged() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity h = controller.get(); review.prepare(h, "story");
      JSONObject chapter = h.state.getJSONArray("chapters").getJSONObject(0);
      chapter.getJSONArray("stages").getJSONObject(0).put("scene", "deep");
      String before = h.state.toString();
      review.show(h, "story", new int[] {915, 412, 100});
      StorySelectionCard card = h.root.findViewWithTag("story-chapter-0");
      assertEquals("deep", card.scene); assertSame(h.art.asset("deepstory.webp"), card.artwork);
      assertEquals(before, h.state.toString());
    }
  }

  @Test public void captureWholeCardsAcrossShortFontAndCutoutSafeAreas() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "story-cards");
    Files.createDirectories(output);
    JSONObject findings = new JSONObject(), metrics = new JSONObject();
    int[][] sizes = {{915,412,100}, {1280,720,100}, {915,320,100}, {915,412,130}};
    for (int[] size : sizes) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      String name = "story-cards-" + size[0] + "x" + size[1] + "-font" + size[2] + "-cutout";
      JSONArray errors = new JSONArray(); findings.put(name, errors);
      try (var controller = review.flow.start()) {
        MainActivity h = controller.get(); var api = review.prepare(h, "story");
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0,8,0,12))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(28,0,0,0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(), Insets.of(28,0,0,0))
            .setVisible(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), true).build();
        Insets effective = GameSafeArea.read(insets);
        review.show(h, "story", size);
        // The shared registration layout resets padding. Apply cutout insets AFTER it,
        // then measure the real root again so pixels and safety metadata describe the same UI.
        ViewCompat.setOnApplyWindowInsetsListener(h.root, (view, delivered) -> {
          Insets actual = GameSafeArea.read(delivered);
          view.setPadding(actual.left, actual.top, actual.right, actual.bottom); return delivered;
        });
        ViewCompat.dispatchApplyWindowInsets(h.root, insets);
        assertEquals(effective.left,h.root.getPaddingLeft());
        assertEquals(effective.top,h.root.getPaddingTop());
        assertEquals(effective.right,h.root.getPaddingRight());
        assertEquals(effective.bottom,h.root.getPaddingBottom());
        h.root.requestLayout();
        h.root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));
        h.root.layout(0,0,size[0],size[1]);
        android.graphics.Rect safe = new android.graphics.Rect(effective.left,effective.top,
            size[0]-effective.right,size[1]-effective.bottom);
        assertEquals(safe,review.geometry.bounds(h.page,h.root));
        Bitmap bitmap = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
        try {
          h.root.draw(new Canvas(bitmap));
          try (OutputStream stream = Files.newOutputStream(output.resolve(name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
          }
        } finally { bitmap.recycle(); }
        JSONObject checked = review.inspect(h,h.root,"story",api);
        assertEquals(safe.width(),checked.getInt("safeWidth"));
        assertEquals(safe.height(),checked.getInt("safeHeight"));
        int cards = 0;
        for (View view : review.geometry.descendants(h.root)) if (view instanceof StorySelectionCard) {
          StorySelectionCard card = (StorySelectionCard)view; cards++;
          assertTrue("카드가 실제 컷아웃 안전영역 안에 있음",safe.contains(review.geometry.bounds(card,h.root)));
          assertNotNull("원본 그림 로딩",card.artwork);
          assertTrue(card.getHeight() >= h.dp(48));
          assertTrue("제목·조건이 카드 높이 안에 있음",card.info.getBottom() <= card.getHeight());
          for (View label : review.geometry.descendants(card.info)) if (label instanceof TextView) {
            TextView text = (TextView)label;
            assertTrue(text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() >= text.getLayout().getHeight());
          }
        }
        assertEquals(3,cards);
        checked.put("cards",cards).put("width",size[0]).put("height",size[1]).put("fontScale",size[2]/100f)
            .put("actualNativeViews",true).put("safeInsetsPx",new JSONArray().put(effective.left).put(effective.top).put(effective.right).put(effective.bottom));
        metrics.put(name,checked);
      } catch (Throwable failure) { errors.put(failure.toString()); }
      Files.write(output.resolve("findings.json"),findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output.resolve("metrics.json"),metrics.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    for (java.util.Iterator<String> it = findings.keys(); it.hasNext();) {
      String name = it.next(); assertEquals(name + ": " + findings.getJSONArray(name),0,findings.getJSONArray(name).length());
    }
  }
}


