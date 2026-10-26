package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeHomePartyFeedbackTest.*;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Candidate browsing never mutates the draft; only a separate settled card tap does. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeTeamInertialRailTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();
  @Before public void setup() { helper.isolate(); }
  @After public void restore() { helper.restore(); }

  @Test public void allCandidatesShareOneRailWithMultipleCardsVisible() throws Exception {
    for (Profile profile : PROFILES) {
      helper.configure(profile);
      try (ActivityController<MainActivity> controller = helper.flow.start()) {
        MainActivity h = controller.get(); Api api = helper.prepare(h);
        helper.show(h, profile, "party-story");
        InertialCardScrollView rail = (InertialCardScrollView) required(h, "team-inline-candidates");
        ViewGroup row = (ViewGroup) rail.getChildAt(0);
        assertEquals("Every candidate stays in the same continuous strip", IDS.length, row.getChildCount());
        assertTrue("Two whole cards fit together", row.getChildAt(1).getRight() <= rail.getWidth());
        assertTrue("More candidates continue outside the viewport", row.getWidth() > rail.getWidth());
        assertTrue(rail.canScrollHorizontally(1));
        assertTrue(api.writes.isEmpty());
      }
    }
  }

  @Test public void dragDoesNotSelectAndOffsetSurvivesSlotAndCandidateChanges() throws Exception {
    Profile profile = PROFILES[0]; helper.configure(profile);
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = helper.prepare(h);
      helper.show(h, profile, "party-story");
      InertialCardScrollView rail = (InertialCardScrollView) required(h, "team-inline-candidates");
      List<String> before = new ArrayList<>(h.menus.teamDraft);
      long start = SystemClock.uptimeMillis();
      touch(rail, start, start, MotionEvent.ACTION_DOWN, 220, rail.getHeight() / 2f);
      touch(rail, start, start + 40, MotionEvent.ACTION_MOVE, 170, rail.getHeight() / 2f);
      touch(rail, start, start + 80, MotionEvent.ACTION_MOVE, 70, rail.getHeight() / 2f);
      touch(rail, start, start + 100, MotionEvent.ACTION_UP, 50, rail.getHeight() / 2f);
      assertEquals("Dragging must not replace a team member", before, h.menus.teamDraft);
      assertTrue(api.writes.isEmpty());

      rail.stopMotion(); rail.scrollTo(73, 0);
      ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
      int offset = rail.getScrollX(); assertTrue(offset > 0);
      required(h, "lineup-slot-1").performClick(); helper.layout(h, profile);
      InertialCardScrollView replacement = (InertialCardScrollView) required(h, "team-inline-candidates");
      assertEquals("Changing the target slot preserves the browsed offset", offset, replacement.getScrollX());
      replacement.stopMotion(); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
      View lastCard = (View) required(h, "team-candidate-character-o6").getParent();
      assertTrue(lastCard.performClick()); helper.layout(h, profile);
      assertEquals("A settled explicit tap updates only the draft", "o6", h.menus.teamDraft.get(1));
      assertEquals(offset, required(h, "team-inline-candidates").getScrollX());
      assertTrue(api.writes.isEmpty()); assertNull(h.visibleDialog);
    }
  }

  private static void touch(View rail, long down, long time, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
    try { rail.dispatchTouchEvent(event); } finally { event.recycle(); }
  }
}
