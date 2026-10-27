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

  @Test public void delayedReassessmentReadKeepsReleasedNativeMotionAndExistingCards() throws Exception {
    try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
      s.api.holdProgression = true;
      JSONObject result = fixture.payload(); String original = result.toString();
      s.attach(result); idle(200); s.layout();
      assertEquals("The actual metadata read is still pending", 1, s.api.progressionReads.size());
      NativeReveal.RevealStrip strip = s.reveal.resultStrip;
      NativeReveal.RevealCard first = s.reveal.pageCards.get(0);
      long down = SystemClock.uptimeMillis();
      float x = strip.getWidth() * .75f, y = strip.getHeight() * .5f;
      fixture.touch(strip, MotionEvent.ACTION_DOWN, down, down, x, y);
      for (int step = 1; step <= 3; step++) {
        idle(16);
        fixture.touch(strip, MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), x - step * 12, y);
      }
      idle(16);
      fixture.touch(strip, MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x - 48, y);
      int released = strip.getScrollX();
      assertTrue("Real touch movement must precede the release", released > 0);
      for (int frame = 0; frame < 3; frame++) { idle(16); strip.computeScroll(); }
      int beforeRead = strip.getScrollX();
      assertTrue("Release advances through native frames before the response", beforeRead > released);

      s.api.deliverProgression(); s.layout();
      int last = beforeRead, advances = 0;
      for (int frame = 0; frame < 6; frame++) {
        idle(16); strip.computeScroll();
        int current = strip.getScrollX();
        assertTrue("A metadata response cannot jump backwards", current >= last);
        if (current > last) advances++;
        last = current;
      }
      assertTrue("The response must not abort an ongoing native trajectory", advances >= 2);
      assertSame("Metadata refresh keeps the same card views", first, s.reveal.pageCards.get(0));
      assertNotNull("Server-approved reassessment still appears", fixture.icon(s.reveal, 0));
      assertEquals(original, result.toString());
      assertEquals(0, s.flow.selected); assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void delayedMetadataCannotCancelEitherDirectionOfAnActiveFingerDrag() throws Exception {
    for (int direction : new int[] {-1, 1}) {
      try (NativeResultReassessmentSwipeTest.Session s = fixture.new Session()) {
        s.api.holdProgression = true;
        JSONObject result = fixture.payload(); String original = result.toString();
        s.attach(result); idle(200); s.layout();
        NativeReveal.RevealStrip strip = s.reveal.resultStrip;
        NativeReveal.RevealCard first = s.reveal.pageCards.get(0);
        strip.scrollTo((strip.cardsRow.getWidth() - strip.getWidth()) / 2, 0); idle(200);
        int start = strip.getScrollX();
        long down = SystemClock.uptimeMillis();
        float x = strip.getWidth() * .5f, y = strip.getHeight() * .5f;
        fixture.touch(strip, MotionEvent.ACTION_DOWN, down, down, x, y);
        for (int step = 1; step <= 2; step++) {
          idle(16);
          fixture.touch(strip, MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), x + direction * step * 40, y);
        }
        int beforeRead = strip.getScrollX();
        assertTrue("The finger owns the row before the read", (beforeRead - start) * direction < 0);
        s.api.deliverProgression(); s.layout();
        assertEquals("Metadata does not reset the current finger offset", beforeRead, strip.getScrollX());
        idle(16);
        fixture.touch(strip, MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), x + direction * 120, y);
        assertTrue("The same finger keeps tracking after the response", (strip.getScrollX() - beforeRead) * direction < 0);
        fixture.touch(strip, MotionEvent.ACTION_CANCEL, down, SystemClock.uptimeMillis(), x + direction * 120, y);
        assertSame(first, s.reveal.pageCards.get(0));
        assertEquals(original, result.toString());
        assertEquals(0, s.flow.selected); assertTrue(s.api.bodies.isEmpty());
      }
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
