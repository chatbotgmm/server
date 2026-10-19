package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 안내 터치 전달과 대사 진행을 확인한다. 심사 SDK로만 실행한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTestGuide {
  ActivityController<MainActivity> activity() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_account", 0)
        .edit()
        .clear()
        .commit();
    ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
    c.get().motionEnabled = false;
    NativeTutorialController.get(c.get()).enabled = false;
    return c;
  }

  void layout(MainActivity h) {
    h.root.measure(
        View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY));
    h.root.layout(0, 0, 915, 412);
  }

  @Test
  public void spotlightAllowsExactlyTheHighlightedActionInsideCutoutSafeArea() {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity h = c.get();
      h.root.removeAllViews();
      h.root.setPadding(36, 10, 8, 10);
      AtomicInteger clicks = new AtomicInteger(), hits = new AtomicInteger();
      Button button = h.button("출석체크", clicks::incrementAndGet);
      FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(160, 48);
      position.leftMargin = 60;
      position.topMargin = 270;
      h.root.addView(button, position);
      NativeTutorialOverlay overlay =
          new NativeTutorialOverlay(
              h,
              button,
              1,
              "출석체크",
              "이 버튼을 눌러요.",
              null,
              null,
              () -> {},
              hits::incrementAndGet,
              false,
              false);
      h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
      layout(h);
      float x = overlay.hole.centerX(), y = overlay.hole.centerY();
      overlay.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 8, 220, 0));
      overlay.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 8, 220, 0));
      assertEquals(0, clicks.get());
      overlay.onTouchEvent(MotionEvent.obtain(20, 20, MotionEvent.ACTION_DOWN, x, y, 0));
      overlay.onTouchEvent(MotionEvent.obtain(20, 30, MotionEvent.ACTION_UP, x, y, 0));
      assertEquals(1, clicks.get());
      assertEquals(1, hits.get());
      assertTrue(overlay.hole.left >= 36);
      assertTrue(overlay.bubble.getLeft() >= 36);
      assertTrue(overlay.skip.getHeight() >= 48);
    }
  }

  @Test
  public void titleDismissThenCompleteTypingThenAdvanceWithQuotedTextAndTip() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity h = c.get();
      h.motionEnabled = true;
      h.state = MainActivity.obj("team", new JSONArray(), "chapters", new JSONArray());
      AtomicInteger ended = new AtomicInteger();
      JSONArray lines =
          new JSONArray()
              .put(MainActivity.obj("who", "N", "say", "「첫 출근」🙂 시작해요."))
              .put(MainActivity.obj("who", "TIP", "say", "유리한 속성을 골라요."));
      StoryView story = new StoryView(h, lines, "프롤로그", "harbor", "", ended::incrementAndGet);
      h.root.removeAllViews();
      h.root.addView(story, new FrameLayout.LayoutParams(-1, -1));
      layout(h);
      assertNotNull(story.titleCard);
      story.advance();
      assertNull(story.titleCard);
      assertEquals(0, story.index);
      story.advance();
      assertFalse(story.typing);
      assertEquals("「첫 출근」🙂 시작해요.", story.say.getText().toString());
      assertTrue(story.say.getText() instanceof Spanned);
      assertEquals(
          1,
          ((Spanned) story.say.getText())
              .getSpans(0, story.say.length(), ForegroundColorSpan.class)
              .length);
      story.advance();
      assertEquals(1, story.index);
      assertEquals("대결 팁", story.who.getText().toString());
      story.finishTyping();
      story.advance();
      story.finish();
      assertEquals(1, ended.get());
      h.root.removeView(story);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      assertNull(story.motion.clock);
      assertFalse(story.typing);
    }
  }

  @Test
  public void reviewCaptureAndNarrationDoNotShowTitleOrInventTipSpeakerPortrait() throws Exception {
    try (ActivityController<MainActivity> c = activity()) {
      MainActivity h = c.get();
      h.state = MainActivity.obj("team", new JSONArray(), "chapters", new JSONArray());
      StoryView story =
          new StoryView(
              h,
              new JSONArray().put(MainActivity.obj("who", "TIP", "say", "「교체」를 활용해요.")),
              "제목",
              "harbor",
              "V1",
              () -> {});
      assertNull(story.titleCard);
      assertFalse(story.typing);
      assertEquals("V1", story.foe);
      assertEquals("대결 팁", story.who.getText().toString());
    }
  }
}
