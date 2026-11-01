package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

/** Confirmed start receipts drive the existing envelope, then the actual candidate carousel. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitOpeningRegressionTest {
  final RecruitCandidateDecisionTest fixture = new RecruitCandidateDecisionTest();
  static final NativeHomePartyFeedbackTest.Profile PHONE = RecruitCandidateDecisionTest.PHONE;
  float originalScale;

  @Before public void setup() {
    fixture.setup();
    originalScale = Settings.Global.getFloat(RuntimeEnvironment.getApplication()
        .getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, 1);
  }

  @After public void restore() {
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, originalScale);
    fixture.restore();
  }

  JSONObject info() {
    return MainActivity.obj("enabled", true, "credits", 50, "catalogVersion", "opening-fixture",
        "offers", new JSONArray().put(MainActivity.obj("kind", "LOW", "label", "하급",
            "cost", 2, "rerollCost", 1)), "pendingSessions", new JSONArray());
  }

  NativeRecruitSession open(MainActivity h, RecruitCandidateDecisionTest.Api api) throws Exception {
    fixture.helper.prepare(h);
    h.api = api;
    h.motionEnabled = true;
    h.motionResumed = true;
    h.getSharedPreferences("roster60_recruit_session", 0).edit().clear().commit();
    return reopen(h, info());
  }

  NativeRecruitSession reopen(MainActivity h, JSONObject response) {
    NativeRecruitSession ui = new NativeRecruitSession(h);
    ui.open(response);
    layout(h);
    return ui;
  }

  void layout(MainActivity h) {
    fixture.helper.review.viewport(h, PHONE.width, PHONE.height);
    fixture.helper.layout(h, PHONE);
  }

  void start(NativeRecruitSession ui, int count) {
    ui.queue("start", MainActivity.obj("kind", "LOW", "times", count,
        "catalogVersion", "opening-fixture"));
  }

  JSONObject started(JSONObject request, int count, String prefix) throws Exception {
    JSONArray rows = new JSONArray(), ids = new JSONArray();
    for (int i = 0; i < count; i++) {
      String id = prefix + "-" + i;
      JSONObject row = NativeRecruitInertialRailTest.candidate(i)
          .put("sessionId", id).put("batchId", request.getString("requestId"))
          .put("batchIndex", i).put("batchSize", count).put("kind", "LOW")
          .put("baseCost", 2).put("catalogVersion", "opening-fixture")
          .put("refundedTickets", 0).put("ownedSpecies", true)
          .put("canReject", true).put("rejectRefund", 1).put("rejectReason", "");
      rows.put(row);
      ids.put(id);
    }
    JSONObject response = new JSONObject(rows.getJSONObject(0).toString());
    int credits = 50 - 2 * count;
    return response.put("requestId", request.getString("requestId"))
        .put("sessions", rows).put("pendingSessions", rows).put("credits", credits)
        .put("balances", MainActivity.obj("applicationTickets", credits))
        .put("receipt", MainActivity.obj("requestId", request.getString("requestId"),
            "action", "start", "chargedTickets", 2 * count, "refundedTickets", 0,
            "grantedOwnedIds", new JSONArray(), "sessionIds", ids));
  }

  JSONObject claimed(JSONObject request, JSONArray pending, int credits) throws Exception {
    JSONArray claimed = new JSONArray(), ids = new JSONArray(), grants = new JSONArray();
    for (int i = 0; i < pending.length(); i++) {
      JSONObject row = new JSONObject(pending.getJSONObject(i).toString());
      String ownedId = "owned-" + row.getString("sessionId");
      row.put("status", "CLAIMED").put("revision", row.getInt("revision") + 1)
          .put("ownedId", ownedId).put("canReject", false).put("rejectRefund", 0)
          .put("rejectReason", "NOT_PENDING");
      claimed.put(row);
      ids.put(row.getString("sessionId"));
      grants.put(ownedId);
    }
    return MainActivity.obj("requestId", request.optString("requestId"), "sessions", claimed,
        "pendingSessions", new JSONArray(), "credits", credits,
        "balances", MainActivity.obj("applicationTickets", credits),
        "receipt", MainActivity.obj("requestId", request.optString("requestId"),
            "action", "claim-all", "chargedTickets", 0, "refundedTickets", 0,
            "grantedOwnedIds", grants, "sessionIds", ids));
  }

  ApplicationEnvelopeView envelope(NativeRecruitSession ui, MainActivity h) {
    layout(h);
    ApplicationEnvelopeView view = ui.openingEnvelope;
    assertNotNull("Verified fresh start automatically presents the existing envelope", view);
    assertNotNull(ui.openingLayer);
    assertSame(view, ui.openingLayer.findViewWithTag("application-envelope"));
    assertTrue("Use the actual attached window", view.isAttachedToWindow());
    assertTrue(view.active());
    assertTrue(view.resultReady);
    assertNotNull(view.clock);
    assertEquals(ApplicationEnvelopeView.TOTAL_DURATION_MS, view.clock.getDuration());
    assertEquals(View.INVISIBLE, ui.rail.getVisibility());
    assertEquals(View.GONE, ui.emptyStage.getVisibility());
    assertFalse(ui.busy);
    assertEquals("Result is saved before any presentation", "", ui.prefs.getString(ui.key, ""));
    assertTrue(ui.prefs.getString(ui.key + ".result", "").contains(ui.state.optString("sessionId")));
    assertNull(ui.rejectionEffect);
    return view;
  }

  void carousel(NativeRecruitSession ui, MainActivity h, int count) throws Exception {
    layout(h);LocalGameArtTest.drain();layout(h);
    assertNull(ui.openingEnvelope);assertNull(ui.openingLayer);
    assertTrue("Verified opening reveals the result grid",ui.overviewMode);
    assertEquals(View.INVISIBLE,ui.rail.getVisibility());assertEquals(View.GONE,ui.emptyStage.getVisibility());
    assertNotNull(ui.overview);assertEquals(View.VISIBLE,ui.overview.getVisibility());
    assertEquals(count,ui.candidates.length());assertSame(ui.candidates,ui.overview.rows);
    ui.overview.bindVisible();LocalGameArtTest.drain();layout(h);
    assertEquals(count,ui.overview.tiles.size());assertFalse(ui.overview.tiles.isEmpty());
    assertNotNull("Original candidate front art is loaded in the grid",ui.overview.tiles.get(0).image.getDrawable());
    assertEquals(View.GONE,ui.overviewControl.getVisibility());assertFalse(ui.overviewBack.isEnabled());
    assertTrue(ui.confirmControl.isEnabled());assertEquals("전부 합격",ui.confirmControl.getText().toString());
    assertEquals("No redundant open-result or switch-view button",1,ui.controls.getChildCount());
  }

  void png(MainActivity h, String name) throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output == null) return;
    Path folder = Path.of(output, "recruit-opening");
    Files.createDirectories(folder);
    Bitmap bitmap = NativeHomePartyFeedbackTest.render(h.root);
    try (OutputStream file = Files.newOutputStream(folder.resolve(name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, file));
    } finally {
      bitmap.recycle();
    }
  }

  @Test public void slowFailedUserStartRetriesSameIdentityThenOpensBeforeActualCarousel() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      Button originalStart = ui.offerControls.get(0);
      originalStart.performClick();
      assertNotNull(h.visibleDialog);
      h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      ShadowLooper.idleMainLooper(40, TimeUnit.MILLISECONDS);
      originalStart.performClick();
      assertEquals(1, api.paths.size());
      assertTrue(api.paths.get(0).endsWith("/start"));
      JSONObject request = api.bodies.get(0);
      ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);
      assertNull("A slow server never starts speculative presentation", ui.openingEnvelope);
      assertEquals(0, ui.openingEffects);
      assertNull(ui.state);
      assertEquals(50, ui.info.getInt("credits"));
      assertEquals(1, api.paths.size());
      api.pending.done(null, "network");
      assertNull(ui.openingEnvelope);
      assertTrue(ui.retryControl.isEnabled());
      assertEquals(request.optString("requestId"), ui.freshStartRequestId);
      ui.retryControl.performClick();
      ui.retryControl.performClick();
      assertEquals(2, api.paths.size());
      assertEquals("The full original request is preserved", request.toString(), api.bodies.get(1).toString());
      api.pending.done(started(request, 1, "retried"), null);
      ApplicationEnvelopeView view = envelope(ui, h);
      assertEquals(1, ui.openingEffects);
      assertEquals("", ui.freshStartRequestId);
      assertEquals(48, ui.info.getInt("credits"));
      assertEquals(48, h.me().getInt("credits"));
      fixture.helper.review.await(() -> view.atlas != null);
      assertEquals(ApplicationEnvelopeView.ATLAS_WIDTH, view.atlas.getWidth());
      assertEquals(ApplicationEnvelopeView.ATLAS_HEIGHT, view.atlas.getHeight());
      view.clock.setCurrentPlayTime(0);
      png(h, "verified-start-0000ms");
      view.clock.setCurrentPlayTime(500);
      assertEquals(ApplicationEnvelopeView.frameAt(500), view.frame);
      assertEquals(View.INVISIBLE, ui.rail.getVisibility());
      png(h, "verified-start-0500ms");
      view.clock.end();
      carousel(ui, h, 1);
      png(h, "verified-start-result");
      assertEquals(2, api.paths.size());
      assertEquals(0, ui.rejectionEffects);
    }
  }

  @Test public void rapidTapsAndEverySubmissionPathStayLockedUntilOpeningCompletes() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 3);
      start(ui, 3);
      assertEquals(1, api.paths.size());
      api.pending.done(started(api.bodies.get(0), 3, "locked"), null);
      ApplicationEnvelopeView view = envelope(ui, h);
      assertFalse(ui.confirmControl.isEnabled());
      assertFalse(ui.candidateInfo.isEnabled());
      assertEquals(1, ui.controls.getChildCount());
      long serializations = ui.candidateSerializations;
      String saved = ui.prefs.getString(ui.key + ".result", "");
      for (int ms : new int[] {0, 125, 500, 999}) {
        view.clock.setCurrentPlayTime(ms);
        ui.confirmControl.performClick();
        ui.confirmCandidates();
        ui.chooseCandidate(2, true);
        ui.page(1);
        ui.action("reroll");
        ui.rejectCandidate(ui.copyCandidate(ui.state));
        ui.queue("claim-all", MainActivity.obj("sessions", ui.pendingRows()));
        start(ui, 1);
        ui.send();
        assertSame(view, ui.openingEnvelope);
        assertEquals(0, ui.selected);
        assertEquals("Animation frames never rebuild candidate payloads", serializations,
            ui.candidateSerializations);
        assertEquals(saved, ui.prefs.getString(ui.key + ".result", ""));
        assertEquals(1, api.paths.size());
      }
      view.clock.end();
      carousel(ui, h, 3);
      ui.confirmControl.performClick();
      ui.confirmControl.performClick();
      assertEquals(2, api.paths.size());
      assertTrue(api.paths.get(1).endsWith("/claim-all"));
      assertEquals(3, api.bodies.get(1).getJSONArray("sessions").length());
    }
  }

  @Test public void mismatchedResponseCannotPresentOrReplaceThePendingRequest() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 1);
      JSONObject request = api.bodies.get(0);
      String pending = ui.prefs.getString(ui.key, "");
      api.pending.done(started(request, 1, "wrong").put("requestId", "some-other-request"), null);
      assertNull(ui.openingEnvelope);
      assertEquals(0, ui.openingEffects);
      assertNull(ui.state);
      assertEquals(0, ui.slots.size());
      assertEquals(pending, ui.prefs.getString(ui.key, ""));
      assertEquals("", ui.prefs.getString(ui.key + ".result", ""));
      assertTrue(ui.noticeFailed);
      ui.retryControl.performClick();
      assertEquals(request.toString(), api.bodies.get(1).toString());
      api.pending.done(started(request, 1, "right"), null);
      envelope(ui, h).clock.end();
      carousel(ui, h, 1);
      assertEquals(1, ui.openingEffects);
      assertEquals(2, api.paths.size());
    }
  }

  @Test public void malformedOpeningReceiptsNeverStartAnEnvelope() throws Exception {
    for (String fault : new String[] {"missing", "request", "action", "refund", "grant", "ids", "order"}) {
      try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
        MainActivity h = c.get();
        RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
        NativeRecruitSession ui = open(h, api);
        start(ui, 2);
        JSONObject response = started(api.bodies.get(0), 2, "invalid-" + fault);
        JSONObject receipt = response.getJSONObject("receipt");
        if (fault.equals("missing")) response.remove("receipt");
        else if (fault.equals("request")) receipt.put("requestId", "wrong-receipt");
        else if (fault.equals("action")) receipt.put("action", "claim-all");
        else if (fault.equals("refund")) receipt.put("refundedTickets", 1);
        else if (fault.equals("grant")) receipt.getJSONArray("grantedOwnedIds").put("unconfirmed-grant");
        else if (fault.equals("ids")) receipt.put("sessionIds", new JSONArray().put("unrelated-session"));
        else receipt.put("sessionIds", new JSONArray().put("invalid-order-1").put("invalid-order-0"));
        api.pending.done(response, null);
        layout(h);
        assertNull(fault, ui.openingEnvelope);
        assertNull(fault, ui.openingLayer);
        assertEquals(fault, 0, ui.openingEffects);
        assertEquals("Receipt inspection never sends another request: " + fault, 1, api.paths.size());
      }
    }
  }

  @Test public void existingCloseTargetWinsTouchAndVerifiedReentryDoesNotReplay() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 2);
      api.pending.done(started(api.bodies.get(0), 2, "closed"), null);
      ApplicationEnvelopeView old = envelope(ui, h);
      ValueAnimator oldClock = old.clock;
      Button close = h.root.findViewWithTag("recruit-close");
      assertSame(close, ui.stage.getChildAt(ui.stage.getChildCount() - 1));
      Rect target = new Rect();
      assertTrue(close.getGlobalVisibleRect(target));
      assertTrue(target.width() >= h.dp(48));
      assertTrue(target.height() >= h.dp(48));
      int[] closed = {0};
      close.setOnClickListener(v -> closed[0]++);
      int[] location = new int[2];
      h.root.getLocationOnScreen(location);
      long now = SystemClock.uptimeMillis();
      NativeRecruitInertialRailTest.touch(h.root, MotionEvent.ACTION_DOWN, now, now,
          target.exactCenterX() - location[0], target.exactCenterY() - location[1]);
      NativeRecruitInertialRailTest.touch(h.root, MotionEvent.ACTION_UP, now, now + 20,
          target.exactCenterX() - location[0], target.exactCenterY() - location[1]);
      ShadowLooper.idleMainLooper(40, TimeUnit.MILLISECONDS);
      assertEquals("The visible X receives the touch", 1, closed[0]);
      assertSame("The X is above the skip surface", old, ui.openingEnvelope);
      String saved = ui.prefs.getString(ui.key + ".result", "");
      h.epoch++;
      h.root.removeAllViews();
      assertTrue(old.cancelled);
      assertNull(old.clock);
      oldClock.end();
      ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
      assertEquals(0, h.root.getChildCount());
      assertEquals(saved, ui.prefs.getString(ui.key + ".result", ""));
      JSONObject cached = info();
      cached.remove("pendingSessions");
      NativeRecruitSession reentered = reopen(h, cached);
      carousel(reentered, h, 2);
      assertEquals("closed-0", reentered.state.getString("sessionId"));
      assertEquals(0, reentered.openingEffects);
      assertEquals(1, api.paths.size());
    }
  }

  @Test public void reopenedPendingRequestReconcilesWithoutReplayingFreshPresentation() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession previous = open(h, api);
      start(previous, 1);
      JSONObject request = api.bodies.get(0);
      api.pending.done(null, "network");
      NativeRecruitSession reentered = reopen(h, info());
      assertEquals("Restored pending requests automatically reconcile once", 2, api.paths.size());
      assertEquals(request.toString(), api.bodies.get(1).toString());
      assertEquals("", reentered.freshStartRequestId);
      api.pending.done(started(request, 1, "restored"), null);
      carousel(reentered, h, 1);
      assertEquals(0, reentered.openingEffects);
      assertEquals("", reentered.prefs.getString(reentered.key, ""));
      assertEquals(2, api.paths.size());
    }
  }

  @Test public void retriedHistoricalReceiptShowsCurrentOtherDeviceRoundWithoutOpening() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 1);
      JSONObject originalRequest = api.bodies.get(0);
      JSONObject historicalReceipt = started(originalRequest, 1, "historical-round");
      api.pending.done(null, "network");
      assertEquals(originalRequest.getString("requestId"), ui.freshStartRequestId);
      ui.retryControl.performClick();
      assertEquals(2, api.paths.size());
      assertEquals("Recovery still uses the original purchase identity",
          originalRequest.toString(), api.bodies.get(1).toString());

      // Another device claimed the original round, then bought a distinct current round.
      // Replaying the old receipt retains its historical rows alongside the latest pending list.
      JSONObject otherDeviceRequest = MainActivity.obj("requestId", "other-device-new-start");
      JSONArray currentRound = started(otherDeviceRequest, 1, "current-round")
          .getJSONArray("pendingSessions");
      historicalReceipt.put("pendingSessions", currentRound).put("credits", 46)
          .put("balances", MainActivity.obj("applicationTickets", 46));
      assertEquals("historical-round-0", historicalReceipt.getJSONArray("sessions")
          .getJSONObject(0).getString("sessionId"));
      assertEquals("historical-round-0", historicalReceipt.getJSONObject("receipt")
          .getJSONArray("sessionIds").getString(0));
      assertEquals("current-round-0", currentRound.getJSONObject(0).getString("sessionId"));
      api.pending.done(historicalReceipt, null);

      carousel(ui, h, 1);
      assertEquals("Only the authoritative current round is displayed", "current-round-0",
          ui.state.getString("sessionId"));
      assertEquals("Historical receipt cannot animate a newer round", 0, ui.openingEffects);
      assertEquals(0, ui.rejectionEffects);
      assertEquals("", ui.freshStartRequestId);
      assertEquals("", ui.prefs.getString(ui.key, ""));
      JSONObject saved = new JSONObject(ui.prefs.getString(ui.key + ".result", ""));
      assertEquals(1, saved.getJSONArray("sessions").length());
      assertEquals("current-round-0", saved.getJSONArray("sessions").getJSONObject(0)
          .getString("sessionId"));
      assertEquals(46, ui.info.getInt("credits"));
      assertEquals(46, h.me().getInt("credits"));
      assertEquals("No additional purchase, read, or claim during reconciliation", 2, api.paths.size());
    }
  }

  @Test public void staleEpochAndOtherAccountResponsesOnlyCacheTheirOriginalResult() throws Exception {
    for (boolean changeAccount : new boolean[] {false, true}) {
      try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
        MainActivity h = c.get();
        RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
        NativeRecruitSession previous = open(h, api);
        start(previous, 1);
        JSONObject response = started(api.bodies.get(0), 1, "stale");
        String owner = api.owner;
        int balance = h.me().optInt("credits");
        if (changeAccount) api.owner = "other-opening-account";
        else h.epoch++;
        api.pending.done(response, null);
        assertNull(previous.openingEnvelope);
        assertEquals(0, previous.openingEffects);
        assertNull(previous.state);
        assertEquals(balance, h.me().optInt("credits"));
        assertTrue(previous.prefs.getString(previous.key + ".result", "").contains("stale-0"));
        assertEquals("", previous.prefs.getString(previous.key, ""));
        if (changeAccount) {
          NativeRecruitSession other = reopen(h, info());
          assertNotEquals(previous.key, other.key);
          assertNull(other.state);
          assertEquals("", other.prefs.getString(other.key + ".result", ""));
          assertEquals(0, other.openingEffects);
          api.owner = owner;
        }
        JSONObject cached = info();
        cached.remove("pendingSessions");
        NativeRecruitSession recovered = reopen(h, cached);
        carousel(recovered, h, 1);
        assertEquals(previous.key, recovered.key);
        assertEquals(0, recovered.openingEffects);
        assertEquals(1, api.paths.size());
      }
    }
  }

  @Test public void pauseCancelsOldClockAndResumeOnlyCompletesTheCurrentOpening() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 1);
      api.pending.done(started(api.bodies.get(0), 1, "paused"), null);
      ApplicationEnvelopeView view = envelope(ui, h);
      ValueAnimator oldClock = view.clock;
      oldClock.setCurrentPlayTime(400);
      int elapsed = view.elapsedMs;
      c.pause();
      assertNull(view.clock);
      oldClock.end();
      ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
      assertSame(view, ui.openingEnvelope);
      assertEquals(elapsed, view.elapsedMs);
      assertFalse(ui.confirmControl.isEnabled());
      c.resume();
      assertNotNull(view.clock);
      assertNotSame(oldClock, view.clock);
      assertEquals(1000 - elapsed, view.clock.getDuration());
      view.clock.end();
      carousel(ui, h, 1);
      assertEquals(1, ui.openingEffects);
      assertEquals(1, api.paths.size());
    }
  }

  @Test public void distinctNewStartGetsItsOwnOpeningAndOldCompletionCannotDismissIt() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 1);
      JSONObject firstRequest = api.bodies.get(0);
      api.pending.done(started(firstRequest, 1, "first"), null);
      ApplicationEnvelopeView first = envelope(ui, h);
      ValueAnimator oldClock = first.clock;
      oldClock.setCurrentPlayTime(350);
      ui.finishOpeningEffect();
      assertTrue(first.cancelled);
      carousel(ui, h, 1);
      JSONArray firstRows = new JSONArray(ui.candidates.toString());
      ui.confirmCandidates();
      api.pending.done(claimed(api.bodies.get(1), firstRows, 48), null);
      assertFalse(ui.hasPending());
      start(ui, 1);
      JSONObject secondRequest = api.bodies.get(2);
      assertNotEquals(firstRequest.getString("requestId"), secondRequest.getString("requestId"));
      api.pending.done(started(secondRequest, 1, "second"), null);
      ApplicationEnvelopeView second = envelope(ui, h);
      assertNotSame(first, second);
      assertEquals(2, ui.openingEffects);
      oldClock.end();
      first.authoritativeResultReady();
      ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
      assertSame("A cancelled prior animation cannot reveal the next round", second, ui.openingEnvelope);
      assertEquals(View.INVISIBLE, ui.rail.getVisibility());
      second.clock.end();
      carousel(ui, h, 1);
      assertEquals("second-0", ui.state.getString("sessionId"));
      assertEquals(3, api.paths.size());
      assertEquals(0, ui.rejectionEffects);
    }
  }

  @Test public void applicationAndSystemMotionOffBypassStraightToConfirmedCarousel() throws Exception {
    for (boolean systemOff : new boolean[] {false, true}) {
      try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
        MainActivity h = c.get();
        RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
        NativeRecruitSession ui = open(h, api);
        if (systemOff) Settings.Global.putFloat(h.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
        else h.motionEnabled = false;
        start(ui, 2);
        api.pending.done(started(api.bodies.get(0), 2, "motion-off"), null);
        carousel(ui, h, 2);
        assertEquals(0, ui.openingEffects);
        assertEquals("", ui.prefs.getString(ui.key, ""));
        assertTrue(ui.prefs.getString(ui.key + ".result", "").contains("motion-off-0"));
        assertEquals(1, api.paths.size());
        Settings.Global.putFloat(h.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
      }
    }
  }

  @Test public void rerollRejectionAndClaimKeepTheirOwnEffectsWithoutAnotherOpening() throws Exception {
    try (ActivityController<MainActivity> c = fixture.helper.flow.start()) {
      MainActivity h = c.get();
      RecruitCandidateDecisionTest.Api api = new RecruitCandidateDecisionTest.Api(h);
      NativeRecruitSession ui = open(h, api);
      start(ui, 3);
      api.pending.done(started(api.bodies.get(0), 3, "actions"), null);
      envelope(ui, h).clock.end();
      carousel(ui, h, 3);
      ui.chooseOverviewCandidate("actions-0");layout(h);LocalGameArtTest.drain();layout(h);
      assertFalse("Card-specific effects are exercised from the explicit registration detail",ui.overviewMode);
      Button refresh = ui.slots.get(0).findViewWithTag("recruit-reroll");
      refresh.performClick();
      JSONObject rerollRequest = api.bodies.get(1);
      assertEquals("reroll", rerollRequest.getString("action"));
      JSONArray pending = new JSONArray(ui.candidates.toString());
      JSONObject replacement = pending.getJSONObject(0);
      replacement.put("revision", 1).put("rerolls", 1);
      replacement.getJSONObject("candidate").put("name", "새로 확인한 후보");
      JSONObject rerolled = new JSONObject(replacement.toString())
          .put("requestId", rerollRequest.getString("requestId")).put("pendingSessions", pending)
          .put("credits", 43).put("balances", MainActivity.obj("applicationTickets", 43))
          .put("receipt", MainActivity.obj("requestId", rerollRequest.getString("requestId"),
              "action", "reroll", "chargedTickets", 1, "refundedTickets", 0,
              "grantedOwnedIds", new JSONArray(), "sessionIds", new JSONArray().put("actions-0")));
      api.pending.done(rerolled, null);
      assertNull(ui.openingEnvelope);
      assertNull(ui.rejectionEffect);
      assertEquals(1, ui.openingEffects);
      assertEquals("actions-0", ui.shredSessionId);
      assertNotNull("Reroll retains its existing card replacement effect", ui.card.animation);
      ui.card.finish();
      layout(h);
      fixture.reject(ui, 0).performClick();
      JSONObject rejectRequest = api.bodies.get(2);
      assertEquals("reject", rejectRequest.getString("action"));
      JSONArray remaining = new JSONArray().put(pending.getJSONObject(1)).put(pending.getJSONObject(2));
      api.pending.done(fixture.rejected(rejectRequest, remaining, false).put("credits", 44), null);
      assertNull(ui.openingEnvelope);
      assertNotNull("Rejection retains its separate confirmed shred", ui.rejectionEffect);
      assertEquals(1, ui.rejectionEffects);
      assertEquals(1, ui.openingEffects);
      ui.confirmCandidates();
      assertEquals("Claims remain blocked during rejection too", 3, api.paths.size());
      ui.finishRejectionEffect();
      layout(h);
      assertFalse("Finishing a card effect keeps the current registration detail",ui.overviewMode);
      assertNull(ui.rejectionEffect);assertEquals(2,ui.candidates.length());assertEquals(3,api.paths.size());
      assertEquals(View.VISIBLE,ui.overviewControl.getVisibility());assertEquals("결과 목록",ui.overviewControl.getText().toString());
      ui.overviewControl.performClick();
      carousel(ui, h, 2);
      assertEquals("Returning to results only navigates",3,api.paths.size());
      JSONArray claimRows = new JSONArray(ui.candidates.toString());
      ui.confirmCandidates();
      assertTrue(api.paths.get(3).endsWith("/claim-all"));
      api.pending.done(claimed(api.bodies.get(3), claimRows, 44), null);
      assertNull(ui.openingEnvelope);
      assertNull(ui.rejectionEffect);
      assertEquals(1, ui.openingEffects);
      assertEquals(1, ui.rejectionEffects);
      assertFalse(ui.hasPending());
      assertEquals(0, ui.candidates.length());
      assertEquals(4, api.paths.size());
    }
  }
}
