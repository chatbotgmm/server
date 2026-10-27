package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

/** Actual MotionEvent/native-scroller and offline session contract checks; never a live request. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitInertialRailTest {
  static final NativeHomePartyFeedbackTest.Profile PHONE =
      new NativeHomePartyFeedbackTest.Profile("recruit-inertial-915x412", 915, 412, 160, 1, 0, 0, 0, 0);
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();

  @Before public void setup() { helper.isolate(); helper.configure(PHONE); }
  @After public void restore() { helper.restore(); }

  static JSONObject candidate(int index) {
    return MainActivity.obj("sessionId", "session-" + index, "batchId", "inertial-batch",
        "revision", 0, "status", "PENDING", "rerolls", 0, "rerollCost", 1,
        "candidate", MainActivity.obj("speciesId", String.format(java.util.Locale.ROOT, "u%02d", index % 60 + 1), "name", "후보 " + index,
            "rarity", "COMMON", "level", 10));
  }

  static final class Api extends NativeApi {
    final List<String> paths = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    Result pending;
    Api(Context context) { super(context); }
    @Override String token() { return "offline-recruit-inertial"; }
    @Override void call(String path, JSONObject body, Result callback) {
      assertNotNull("The screen should only send the explicitly exercised mutation", body);
      paths.add(path);
      bodies.add(NativeRealtime.copy(body));
      pending = callback;
    }
  }

  NativeRecruitSession open(MainActivity h, Api api, int count) throws Exception {
    return open(h, api, count, PHONE);
  }

  NativeRecruitSession open(MainActivity h, Api api, int count,
      NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    helper.prepare(h); h.api = api; h.motionEnabled = false;
    h.getSharedPreferences("roster60_recruit_session", 0).edit().clear().commit();
    JSONArray candidates = new JSONArray();
    for (int i = 0; i < count; i++) candidates.put(candidate(i));
    NativeRecruitSession screen = new NativeRecruitSession(h);
    screen.open(MainActivity.obj("enabled", true, "catalogVersion", "offline-fixture",
        "offers", new JSONArray(), "sessions", candidates));
    helper.layout(h, profile);
    screen.rail.bindVisible();
    helper.layout(h, profile);
    ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
    return screen;
  }

  static void touch(View target, int action, long down, long time, float x, float y) {
    MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
    target.dispatchTouchEvent(event); event.recycle();
  }

  static void tap(NativeRecruitSession screen, int index) {
    View card = screen.slots.get(index);
    float x = (card.getLeft() + card.getRight()) / 2f - screen.rail.getScrollX();
    float y = screen.rail.getHeight() / 2f;
    long time = SystemClock.uptimeMillis();
    touch(screen.rail, MotionEvent.ACTION_DOWN, time, time, x, y);
    touch(screen.rail, MotionEvent.ACTION_UP, time, time + 24, x, y);
    ShadowLooper.idleMainLooper(40, TimeUnit.MILLISECONDS);
  }

  static OverScroller scroller(NativeRecruitSession screen) {
    return ReflectionHelpers.getField(screen.rail, "mScroller");
  }

  @Test public void oneContinuousRowShowsSeveralCardsAndBindsOnlyNearbyArtwork() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 30);
      assertEquals(30, screen.rail.row.getChildCount());
      int visible = 0, loaded = 0;
      for (NativeRecruitSession.CandidateSlot slot : screen.slots) {
        if (slot.getRight() > screen.rail.getScrollX()
            && slot.getLeft() < screen.rail.getScrollX() + screen.rail.getWidth()) visible++;
        if (slot.loaded) loaded++;
      }
      assertTrue("Several candidates must be visible together", visible >= 2);
      assertTrue("Near-screen artwork must be loaded", loaded >= visible);
      assertTrue("The long batch must not decode every candidate at once", loaded < 30);
      assertTrue(screen.rail.row.getWidth() > screen.rail.getWidth());
      assertEquals(0, screen.selected); assertTrue(api.paths.isEmpty());
      screen.rail.scrollTo(screen.rail.row.getWidth() - screen.rail.getWidth(), 0);
      screen.rail.bindVisible();
      assertFalse("Far-off first card should release its image View", screen.slots.get(0).loaded);
      assertTrue("Last candidate remains reachable in the same row", screen.slots.get(29).loaded);
      assertEquals(0, screen.selected); assertTrue(api.paths.isEmpty());
    }
  }

  @Test public void dragMovesRailWithoutSelectingOrSendingClaimRerollOrBulkClaim() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 12);
      float x = screen.rail.getWidth() * .7f, y = screen.rail.getHeight() / 2f;
      long time = SystemClock.uptimeMillis();
      touch(screen.rail, MotionEvent.ACTION_DOWN, time, time, x, y);
      touch(screen.rail, MotionEvent.ACTION_MOVE, time, time + 25, x - 70, y);
      touch(screen.rail, MotionEvent.ACTION_MOVE, time, time + 50, x - 170, y);
      assertTrue("Actual drag must change horizontal offset", screen.rail.getScrollX() > 0);
      ((Button) h.root.findViewWithTag("recruit-claim")).performClick();
      ((Button) h.root.findViewWithTag("recruit-reroll")).performClick();
      ((Button) h.root.findViewWithTag("recruit-claim-all")).performClick();
      touch(screen.rail, MotionEvent.ACTION_UP, time, time + 65, x - 210, y);
      assertEquals(0, screen.selected);
      assertTrue("A drag must never send a mutation", api.paths.isEmpty());
      assertTrue("Release keeps accidental action taps blocked", screen.rail.blocksClick());
      assertTrue("Release should produce native fling velocity", scroller(screen).getCurrVelocity() > 0);
    }
  }

  @Test public void nativeFlingDistanceDependsOnVelocityAndStoppingTouchCannotSelect() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 30);
      screen.rail.fling(600); int slow = scroller(screen).getFinalX();
      screen.rail.stopMotion(); screen.rail.scrollTo(0, 0);
      screen.rail.fling(1800); int fast = scroller(screen).getFinalX();
      assertTrue("Native velocity determines travel distance", fast > slow && slow > 0);
      View first = screen.slots.get(0), second = screen.slots.get(1);
      int stride = second.getLeft() - first.getLeft();
      assertTrue("Native fling must retain a free offset rather than align every stop to a card",
          slow % stride != 0 || fast % stride != 0);
      tap(screen, 1);
      assertEquals("First touch stops the rail without choosing the card underneath", 0, screen.selected);
      assertTrue(api.paths.isEmpty());
      assertTrue(screen.rail.blocksClick());
    }
  }

  @Test public void tapSelectsOnlyThenExplicitClaimUsesThatSessionsRevision() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 8);
      tap(screen, 1);
      assertEquals(1, screen.selected);
      assertEquals("session-1", screen.state.getString("sessionId"));
      assertTrue("Selecting a card must not claim it", api.paths.isEmpty());
      ((Button) h.root.findViewWithTag("recruit-claim")).performClick();
      assertEquals(1, api.paths.size());
      assertEquals("native/roster60/recruit/session/action", api.paths.get(0));
      JSONObject request = api.bodies.get(0);
      assertEquals("claim", request.getString("action"));
      assertEquals("session-1", request.getString("sessionId"));
      assertEquals(0, request.getInt("revision"));
      assertFalse(request.getString("requestId").isEmpty());
      ((Button) h.root.findViewWithTag("recruit-claim")).performClick();
      assertEquals("Pending lock must still prevent duplicate claims", 1, api.paths.size());
    }
  }

  @Test public void aSeparateTapImmediatelyAfterStoppingFlingSelectsWithoutASecondDeadPeriod() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 12);
      screen.rail.fling(1800);
      tap(screen, 1);
      assertEquals("The first gesture only stops the fling", 0, screen.selected);
      assertTrue("The native trajectory is actually stopped", scroller(screen).isFinished());
      tap(screen, 1);
      assertEquals("The next separate deliberate tap works as soon as the rail is stopped", 1, screen.selected);
      assertTrue("A selection still sends no claim or reroll", api.paths.isEmpty());
    }
  }

  @Test public void releasedOffsetKeepsAdvancingThroughActualPortraitAndLayoutCallbacks() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 30);
      float x = screen.rail.getWidth() * .8f, y = screen.rail.getHeight() * .65f;
      long down = SystemClock.uptimeMillis();
      touch(screen.rail, MotionEvent.ACTION_DOWN, down, down, x, y);
      for (int i = 1; i <= 4; i++) {
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
        touch(screen.rail, MotionEvent.ACTION_MOVE, down, SystemClock.uptimeMillis(), x - i * 50, y);
      }
      assertTrue("The actual candidate rail follows the drag", screen.rail.getScrollX() > 0);
      ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
      touch(screen.rail, MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x - 240, y);
      int last = screen.rail.getScrollX(), advancing = 0;
      for (int i = 0; i < 16; i++) {
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS); screen.rail.computeScroll();
        if (h.root.isLayoutRequested()) helper.layout(h, PHONE);
        int current = screen.rail.getScrollX();
        assertTrue("Visible-image callbacks cannot jump the released row backwards", current >= last);
        if (current > last) advancing++; last = current;
      }
      assertTrue("The actual candidate rail continues over native frames", advancing >= 5);
      assertEquals(0, screen.selected); assertTrue(api.paths.isEmpty());
    }
  }

  @Test public void rendersThreeCandidateScreensAtPhoneAndShortSizesWithUnclippedActions() throws Exception {
    NativeHomePartyFeedbackTest.Profile[] profiles = {PHONE,
        new NativeHomePartyFeedbackTest.Profile("recruit-inertial-640x320", 640, 320, 160, 1, 0, 0, 0, 0)};
    for (NativeHomePartyFeedbackTest.Profile profile : profiles) {
      helper.configure(profile);
      try (ActivityController<MainActivity> controller = helper.flow.start()) {
        MainActivity h = controller.get(); Api api = new Api(h);
        NativeRecruitSession screen = open(h, api, 3, profile);
        assertEquals(3, screen.rail.row.getChildCount());
        int visible = 0;
        for (NativeRecruitSession.CandidateSlot slot : screen.slots)
          if (slot.getLeft() >= 0 && slot.getRight() <= screen.rail.getWidth()) visible++;
        assertEquals("Three complete candidates fit at " + profile.name, 3, visible);
        assertTrue("A useful illustration area remains", screen.rail.getHeight() >= h.dp(100));
        for (NativeRecruitSession.CandidateSlot slot : screen.slots) {
          View artwork = slot.findViewWithTag("recruit-registration-card");
          assertNotNull(artwork);
          assertTrue("Status copy must leave room for the registration illustration", artwork.getHeight() >= h.dp(72));
        }
        for (int i = 0; i < screen.controls.getChildCount(); i++) {
          View child = screen.controls.getChildAt(i);
          assertTrue("Footer stays inside the root", child.getBottom() <= screen.controls.getHeight());
          if (child instanceof Button) {
            Button button = (Button) child;
            Layout text = button.getLayout(); assertNotNull(text);
            int room = button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom();
            assertTrue("Action text is not vertically clipped: " + button.getText(),
                text.getLineBottom(text.getLineCount() - 1) <= room);
            for (int line = 0; line < text.getLineCount(); line++)
              assertEquals("Action text is not ellipsized", 0, text.getEllipsisCount(line));
          }
        }
        Bitmap bitmap = NativeHomePartyFeedbackTest.render(h.root);
        try { helper.png(bitmap, profile.name + ".png"); } finally { bitmap.recycle(); }
        assertTrue(api.paths.isEmpty());
      }
    }
  }
  @Test public void priorUnresolvedErrorAndSameRequestRecoverySurviveRailIntegration() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 0);
      assertEquals(View.VISIBLE, screen.emptyStage.getVisibility());
      assertEquals(View.GONE, screen.rail.getVisibility());
      assertNotNull(h.root.findViewWithTag("recruit-unresolved-state"));
      assertEquals("모집 결과 대기", screen.emptyTitle.getText().toString());
      assertNull("No meaningless free retry without a persisted request", h.root.findViewWithTag("recruit-retry"));
      screen.queue("start", MainActivity.obj("kind", "LOW", "catalogVersion", "offline-fixture"));
      assertEquals(1, api.bodies.size());
      String sent = api.bodies.get(0).toString();
      api.pending.done(null, "network");
      assertFalse(screen.busy);
      assertTrue(screen.noticeFailed);
      assertEquals("모집 결과를 확인하지 못했어요", screen.emptyTitle.getText().toString());
      assertTrue(screen.emptyDetail.getText().toString().contains("network"));
      assertFalse(screen.prefs.getString(screen.key, "").isEmpty());
      ((Button) h.root.findViewWithTag("recruit-retry")).performClick();
      assertEquals(2, api.bodies.size());
      assertEquals("Retry preserves the entire original request body", sent, api.bodies.get(1).toString());
      JSONObject success = candidate(0);
      success.put("requestId", api.bodies.get(0).getString("requestId"));
      success.put("credits", 8);
      api.pending.done(success, null);
      helper.layout(h, PHONE);
      assertEquals(View.GONE, screen.emptyStage.getVisibility());
      assertEquals(View.VISIBLE, screen.rail.getVisibility());
      assertFalse(screen.noticeFailed);
      assertEquals("", screen.prefs.getString(screen.key, ""));
      assertNull(h.root.findViewWithTag("recruit-retry"));
      assertEquals(1, screen.slots.size());
      assertEquals("session-0", screen.state.getString("sessionId"));
      assertTrue(screen.prefs.getString(screen.key + ".result", "").contains("session-0"));
    }
  }

  @Test public void mismatchedResponseKeepsErrorRetryAndDoesNotInstallCandidate() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get(); Api api = new Api(h);
      NativeRecruitSession screen = open(h, api, 0);
      screen.queue("start", MainActivity.obj("kind", "LOW", "catalogVersion", "offline-fixture"));
      JSONObject wrong = candidate(0); wrong.put("requestId", "wrong-response-id");
      api.pending.done(wrong, null);
      assertNull(screen.state);
      assertEquals(0, screen.slots.size());
      assertFalse(screen.busy);
      assertTrue(screen.noticeFailed);
      assertTrue(screen.emptyDetail.getText().toString().contains("응답 식별자 불일치"));
      assertFalse(screen.prefs.getString(screen.key, "").isEmpty());
      assertNotNull(h.root.findViewWithTag("recruit-retry"));
      assertEquals("", screen.prefs.getString(screen.key + ".result", ""));
    }
  }

}
