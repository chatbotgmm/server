package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.time.Duration;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Scrolls the actual result surface without changing authoritative results or the chosen unit. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRevealInertialRailTest {
  final NativeResultReassessmentSwipeTest fixture = new NativeResultReassessmentSwipeTest();

  @Before public void setup() throws Exception { fixture.setup(); }
  @After public void restore() { fixture.restore(); }

  void idle(long millis) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
  }

  @Test public void allResultsFormOneRailAndAnArbitraryOffsetDoesNotSnap() throws Exception {
    try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
      JSONObject result = fixture.payload();
      String original = result.toString();
      s.attach(result); idle(200); s.layout();
      NativeReveal.RevealStrip strip = s.reveal.resultStrip;
      assertEquals(6, strip.cardsRow.getChildCount());
      int visible = 0;
      for (int i = 0; i < strip.cardsRow.getChildCount(); i++) {
        View card = strip.cardsRow.getChildAt(i);
        if (card.getLeft() < strip.getWidth() && card.getRight() > 0) visible++;
      }
      assertTrue("Several cards are visible together", visible >= 2);
      assertTrue("More cards continue outside the viewport", strip.canScrollHorizontally(1));
      strip.scrollTo(73, 0); idle(600); strip.computeScroll();
      assertEquals("Release never aligns to a card or page", 73, strip.getScrollX());
      assertEquals("Browsing keeps the selected unit", 0, s.flow.selected);
      assertEquals(original, result.toString());
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void realDragAndStoppingFlingDoNotFlipSelectOrFinish() throws Exception {
    try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
      s.attach(fixture.payload()); idle(200); s.layout();
      NativeReveal.RevealStrip strip = s.reveal.resultStrip;
      long time = SystemClock.uptimeMillis();
      float x = strip.getWidth() * .70f, y = strip.getHeight() * .50f;
      fixture.touch(strip, MotionEvent.ACTION_DOWN, time, time, x, y);
      fixture.touch(strip, MotionEvent.ACTION_MOVE, time, time + 16, x - 60, y);
      fixture.touch(strip, MotionEvent.ACTION_MOVE, time, time + 32, x - 160, y);
      assertTrue("The row follows the finger", strip.getScrollX() > 0);
      fixture.touch(strip, MotionEvent.ACTION_UP, time, time + 48, x - 200, y);
      assertEquals(0, s.flow.selected);
      for (NativeReveal.RevealCard card : s.reveal.pageCards) assertFalse(card.infoShown);
      strip.fling(1600);
      NativeReveal.RevealCard first = (NativeReveal.RevealCard) strip.cardsRow.getChildAt(0);
      first.performClick();
      fixture.button(s.h, "acquisition-confirm").performClick();
      assertFalse("A touch that stops motion does not flip the card", first.infoShown);
      assertEquals("A moving rail cannot accidentally confirm", 0, s.finished.get());
      assertEquals(0, s.flow.selected);
      assertNull(s.reveal.dossier);
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void serverReadRepaintPreservesOffsetAndSelectedReceipt() throws Exception {
    try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
      s.attach(fixture.payload()); idle(200); s.layout();
      NativeReveal.RevealStrip strip = s.reveal.resultStrip;
      s.flow.selected = 4;
      strip.scrollTo(119, 0); idle(200);
      s.reveal.repaintResults.run(); s.layout(); idle(200);
      assertEquals(119, strip.getScrollX());
      assertEquals(4, s.flow.selected);
      NativeReveal.RevealCard selected = (NativeReveal.RevealCard) strip.cardsRow.getChildAt(4);
      assertTrue(selected.isSelected());
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void fiftyResultsKeepEverySlotButOnlyNearbyArtworkAttached() throws Exception {
    try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
      JSONArray items = new JSONArray();
      for (int i = 0; i < 50; i++) items.put(fixture.item("R13"));
      s.attach(MainActivity.obj("items", items)); idle(200); s.layout();
      NativeReveal.RevealStrip strip = s.reveal.resultStrip;
      assertEquals(50, strip.cardsRow.getChildCount());
      int attached = 0;
      for (NativeReveal.RevealCard card : s.reveal.pageCards)
        if (card.artwork.getParent() == card.photo) attached++;
      assertTrue(attached > 0);
      assertTrue("Offscreen results do not all decode FULL bitmaps", attached < 50);
      NativeReveal.RevealCard first = s.reveal.pageCards.get(0), last = s.reveal.pageCards.get(49);
      assertNull(last.artwork.getParent());
      strip.revealIndex(49); idle(200); s.layout();
      assertSame(last.photo, last.artwork.getParent());
      assertNull("Leaving the buffer releases the old artwork", first.artwork.getParent());
      assertEquals(0, s.flow.selected);
      assertTrue(s.api.bodies.isEmpty());
    }
  }
}
