package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/** The actual new-60 owned-member strip uses the same native, unsnapped rail as recruitment. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60CardStripInertialTest {
  static final NativeHomePartyFeedbackTest.Profile PHONE =
      new NativeHomePartyFeedbackTest.Profile("roster-strip-915x412", 915, 412, 160, 1, 0, 0, 0, 0);
  static final NativeHomePartyFeedbackTest.Profile SMALL =
      new NativeHomePartyFeedbackTest.Profile("roster-strip-640x320", 640, 320, 160, 1, 0, 0, 0, 0);
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();

  @Before public void setup() { helper.isolate(); helper.configure(PHONE); }
  @After public void restore() { helper.restore(); }

  static final class Api extends NativeApi {
    JSONArray units = new JSONArray();
    final List<String> reads = new ArrayList<>();
    final List<JSONObject> writes = new ArrayList<>();
    Api(Context context) {
      super(context);
      for (int i = 0; i < 30; i++) units.put(MainActivity.obj("ownedId", "owned-" + i,
          "speciesId", "u01", "name", "보유 대원 " + i, "level", 10));
    }
    @Override String token() { return "offline-new60-inertial-strip"; }
    @Override void call(String path, JSONObject body, Result callback) {
      if (body != null) {
        writes.add(NativeRealtime.copy(body));
        callback.done(new JSONObject(), null);
        return;
      }
      reads.add(path);
      assertEquals("Only the party options read is needed", "native/roster60/pvp/options", path);
      callback.done(MainActivity.obj("units", units, "partyIds", new JSONArray()), null);
    }
  }

  NativeRoster60Pvp open(MainActivity h, Api api) throws Exception {
    helper.prepare(h); h.api = api; h.motionEnabled = false;
    h.getSharedPreferences("roster60_human_queue", 0).edit().clear().commit();
    h.state.put("tutorial", MainActivity.obj("flow", "roster60-v1"));
    NativeRoster60Pvp editor = new NativeRoster60Pvp(h, true);
    editor.open(); helper.layout(h, PHONE);
    ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
    assertTrue("Product strip must use the shared native rail", editor.ownedStrip instanceof InertialCardScrollView);
    assertEquals(30, editor.ownedStrip.cards.getChildCount());
    assertTrue(editor.ownedStrip.canScrollHorizontally(1));
    return editor;
  }

  static OverScroller scroller(Roster60CardStrip strip) {
    return ReflectionHelpers.getField(strip, "mScroller");
  }

  static void touch(View strip, long down, long time, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
    try { strip.dispatchTouchEvent(event); } finally { event.recycle(); }
  }

  static void settle(Roster60CardStrip strip) {
    strip.stopMotion();
    ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
  }

  static void advanceFrames(Roster60CardStrip strip) {
    for (int frame = 0; frame < 30; frame++) {
      ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
      strip.computeScroll();
    }
  }

  @Test public void nativeVelocityHasFreeStoppingDistanceAndFirstTouchOnlyStopsMotion() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRoster60Pvp editor = open(h, api); Roster60CardStrip strip = editor.ownedStrip;
      strip.fling(600); int slow = scroller(strip).getFinalX();
      strip.stopMotion(); strip.scrollTo(0, 0);
      strip.fling(1800); int fast = scroller(strip).getFinalX();
      assertTrue("Higher native velocity travels farther", fast > slow && slow > 0);
      int stride = strip.cards.getChildAt(1).getLeft() - strip.cards.getChildAt(0).getLeft();
      assertTrue("Release positions are not snapped to card boundaries", slow % stride != 0 || fast % stride != 0);
      long revision = editor.selectionRevision;
      Roster60MemberTile second = editor.ownedTiles.get("owned-1");
      float x = (second.getLeft() + second.getRight()) / 2f - strip.getScrollX();
      float y = strip.getHeight() / 2f; long now = SystemClock.uptimeMillis();
      touch(strip, now, now, MotionEvent.ACTION_DOWN, x, y);
      touch(strip, now, now + 24, MotionEvent.ACTION_UP, x, y);
      second.action.performClick();
      assertTrue("Stopping the rail cannot add a member", editor.selected.isEmpty());
      assertEquals(revision, editor.selectionRevision);
      assertTrue(strip.blocksClick()); assertTrue(api.writes.isEmpty());
      settle(strip);
      int offset = strip.getScrollX(); second.action.performClick();
      assertTrue("A subsequent settled explicit tap adds the member", editor.selected.contains("owned-1"));
      assertSame(strip, editor.ownedStrip); assertSame(second, strip.cards.getChildAt(1));
      assertEquals(offset, strip.getScrollX()); assertTrue("Selection is not an automatic save", api.writes.isEmpty());
    }
  }

  @Test public void actualDragAndReleaseCannotSelectOrSaveThroughOwnedButtonCallback() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRoster60Pvp editor = open(h, api); Roster60CardStrip strip = editor.ownedStrip;
      long revision = editor.selectionRevision, now = SystemClock.uptimeMillis();
      float x = strip.getWidth() * .8f, y = strip.getHeight() / 2f;
      touch(strip, now, now, MotionEvent.ACTION_DOWN, x, y);
      touch(strip, now, now + 25, MotionEvent.ACTION_MOVE, x - 70, y);
      touch(strip, now, now + 50, MotionEvent.ACTION_MOVE, x - 170, y);
      assertTrue("Real MotionEvents move the product strip", strip.getScrollX() > 0);
      editor.ownedTiles.get("owned-0").action.performClick();
      assertTrue(editor.selected.isEmpty());
      touch(strip, now, now + 65, MotionEvent.ACTION_UP, x - 210, y);
      editor.ownedTiles.get("owned-0").action.performClick();
      assertTrue("A release cannot fall through into selection", editor.selected.isEmpty());
      assertEquals(revision, editor.selectionRevision);
      assertTrue(strip.blocksClick()); assertTrue(scroller(strip).getCurrVelocity() > 0);
      assertTrue(api.writes.isEmpty());
      settle(strip); editor.ownedTiles.get("owned-0").action.performClick();
      assertTrue(editor.selected.contains("owned-0")); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void optionsRefreshPreservesOwnedIdAnchorAndAbortsTheOldNativeTrajectory() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRoster60Pvp editor = open(h, api); Roster60CardStrip strip = editor.ownedStrip;
      strip.scrollTo(strip.cards.getChildAt(6).getLeft() + 13, 0); settle(strip);
      strip.fling(1800);
      JSONArray remaining = new JSONArray();
      for (int i = 1; i < api.units.length(); i++) remaining.put(api.units.getJSONObject(i));
      api.units = remaining; editor.fetchOptions(); helper.layout(h, PHONE);
      assertSame(strip, editor.ownedStrip); assertEquals(29, strip.cards.getChildCount());
      assertEquals("owned-6", strip.ids.get(5));
      int expected = strip.cards.getChildAt(5).getLeft() + 13;
      assertEquals("Removing an earlier member retains the same visual anchor", expected, strip.getScrollX());
      advanceFrames(strip);
      assertEquals("No pending native fling frame may undo the restored offset", expected, strip.getScrollX());
      assertTrue(editor.selected.isEmpty()); assertTrue(api.writes.isEmpty());
      assertEquals(2, api.reads.size());
    }
  }

  @Test public void releasedOffsetKeepsAdvancingThroughActualPortraitAndLayoutCallbacks() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRoster60Pvp editor = open(h, api); Roster60CardStrip strip = editor.ownedStrip;
      float x = strip.getWidth() * .8f, y = strip.getHeight() / 2f;
      List<String> movement = new ArrayList<>();
      long down = SystemClock.uptimeMillis();
      touch(strip, down, down, MotionEvent.ACTION_DOWN, x, y);
      movement.add(movementSample(h, strip));
      for (int i = 1; i <= 4; i++) {
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
        touch(strip, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x - i * 40, y);
        movement.add(movementSample(h, strip));
      }
      assertTrue("The actual owned-card rail follows the drag: " + movement, strip.getScrollX() > 0);
      ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
      touch(strip, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x - 200, y);
      int last = strip.getScrollX(), advancing = 0;
      for (int i = 0; i < 16; i++) {
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS); strip.computeScroll();
        if (h.root.isLayoutRequested()) helper.layout(h, PHONE);
        int current = strip.getScrollX();
        assertTrue("Portrait layout cannot reset the trajectory to an old offset", current >= last);
        if (current > last) advancing++; last = current;
      }
      assertTrue("The actual owned-card rail continues over native frames", advancing >= 5);
      assertTrue(editor.selected.isEmpty()); assertTrue(api.writes.isEmpty());
    }
  }

  static String movementSample(MainActivity h, Roster60CardStrip strip) {
    return "at="+SystemClock.uptimeMillis()+" x="+strip.getScrollX()+" rail="+strip.getWidth()+"x"+strip.getHeight()
        +" root="+h.root.getWidth()+"x"+h.root.getHeight()+" card="+strip.cards.getChildAt(0).getWidth()
        +" measured="+ReflectionHelpers.getField(strip,"measuredCardWidth")+" assignments="+strip.widthAssignments
        +" pointer="+ReflectionHelpers.getField(strip,"mActivePointerId")+" dragging="+ReflectionHelpers.getField(strip,"mIsBeingDragged");
  }

  @Test public void resizingDuringFlingPreservesTheRelativeOffsetWithinTheAnchoredMember() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRoster60Pvp editor = open(h, api); Roster60CardStrip strip = editor.ownedStrip;
      View anchor = strip.cards.getChildAt(6);
      int oldStride = anchor.getWidth() + strip.gap, inset = oldStride / 3;
      float offset = inset / (float) oldStride;
      strip.scrollTo(anchor.getLeft() + inset, 0); settle(strip); strip.fling(1800);
      helper.configure(SMALL); helper.layout(h, SMALL);
      int expected = Roster60StripPosition.scroll(6, offset, strip.cards.getChildAt(6).getWidth(),
          strip.gap, strip.getWidth(), strip.cards.getChildCount());
      assertEquals(expected, strip.getScrollX()); advanceFrames(strip);
      assertEquals("Resize restoration remains stable after later native frames", expected, strip.getScrollX());
      assertTrue(api.writes.isEmpty());
    }
  }
}
