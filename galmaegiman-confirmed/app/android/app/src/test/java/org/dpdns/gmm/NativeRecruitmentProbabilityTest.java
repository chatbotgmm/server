package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;

/** Current rates, terminology and lifecycle, with a recording API and no real account writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
// Robolectric 4.16.1 marks application windows visible only in native graphics mode.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitmentProbabilityTest {
  final NativeGameFlowTest fixture = new NativeGameFlowTest();

  @Before public void isolate() { fixture.isolate(); }

  /** Exact economy.yaml config at 9d8d7b4, with a fresh-account pity state for UI screenshots. */
  static JSONObject serverEconomyFixture() {
    String[] tiers = {"COMMON", "UNCOMMON", "SPECIAL", "RARE", "LEGEND"};
    String[] names = {"흔함", "안흔함", "특별", "희귀", "전설"};
    int[][] rates = {{70, 30}, {20, 50, 30}, {10, 15, 54, 20, 1}};
    int[] costs = {2, 3, 5};
    JSONArray offers = new JSONArray();
    for (int i = 0; i < rates.length; i++) {
      JSONArray weights = new JSONArray();
      for (int j = 0; j < rates[i].length; j++)
        weights.put(MainActivity.obj("tier", tiers[j], "tierName", names[j], "w", rates[i][j]));
      offers.put(MainActivity.obj("kind", RecruitmentProbabilityView.KINDS[i],
          "label", new String[] {"하급", "중급", "고급"}[i], "cost", costs[i], "weights", weights));
    }
    return MainActivity.obj("gachas", offers, "credits", 200, "wait", 0, "claim", 30,
        "cooldownHours", 1, "event", JSONObject.NULL,
        "pity", MainActivity.obj("tierName", "전설", "left", 100));
  }

  static JSONObject draw() {
    JSONArray offers = new JSONArray();
    for (int i = 0; i < RecruitmentProbabilityView.KINDS.length; i++)
      offers.put(MainActivity.obj("kind", RecruitmentProbabilityView.KINDS[i],
          "cost", i == 2 ? 5 : i + 1, "label", new String[] {"하급", "중급", "고급"}[i],
          "weights", new JSONArray()
              .put(MainActivity.obj("tier", "COMMON", "tierName", "흔함", "w", 99.995 - i))
              .put(MainActivity.obj("tier", "LEGEND", "tierName", "전설", "w", 0.005 + i))));
    return MainActivity.obj("gachas", offers, "credits", 30, "wait", 0, "claim", 10,
        "cooldownHours", 1, "pity", MainActivity.obj("tierName", "전설", "left", 17));
  }

  static final class FakeApi extends NativeApi {
    final List<String> calls = new ArrayList<>();
    final List<JSONObject> writes = new ArrayList<>();
    final List<Result> pending = new ArrayList<>();
    String session = "recruitment-review";
    boolean deferred;
    JSONObject response = draw();
    FakeApi(MainActivity host) { super(host); }
    @Override String token() { return session; }
    @Override void call(String path, JSONObject body, Result done) {
      calls.add((body == null ? "GET " : "POST ") + path);
      if (body != null) {
        writes.add(body);
        // Keep the mutation pending so no fake server result alters the original fixture.
        return;
      }
      assertEquals("draw", path);
      if (deferred) pending.add(done);
      else done.done(response, null);
    }
  }

  FakeApi prepare(MainActivity host) {
    host.api.close();
    FakeApi api = new FakeApi(host);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = false;
    host.state = MainActivity.obj("me", MainActivity.obj("credits", 30), "units", new JSONArray());
    host.base("모집 검토", "draw");
    new NativeRenderReviewTest().viewport(host, 915, 412);
    assertTrue("Test host must be attached to its real window", host.root.isAttachedToWindow());
    return api;
  }

  LinearLayout attach(MainActivity host) {
    LinearLayout view = RecruitmentProbabilityView.content(host);
    host.root.addView(view);
    fixture.measure(host.root);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    assertTrue("Probability content must be attached", view.isAttachedToWindow());
    assertTrue("Probability content must be shown", view.isShown());
    assertEquals("Probability window must be visible", View.VISIBLE, view.getWindowVisibility());
    return view;
  }

  View dialogFrame(MainActivity host) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    View decor = host.visibleDialog.getWindow().getDecorView();
    int width = host.visibleDialog.getWindow().getAttributes().width;
    decor.measure(View.MeasureSpec.makeMeasureSpec(width > 0 ? width : 891, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(396, View.MeasureSpec.AT_MOST));
    decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
    return decor;
  }

  Button button(View view, String text) {
    if (view instanceof Button && ((Button) view).getText().toString().equals(text))
      return (Button) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = button(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test public void settingsLoadsOnlyWhenProbabilityTabIsOpenedAndShowsEveryTier() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      NativeSettingsSheet.show(host);
      assertTrue("Unopened settings tabs never fetch rates", api.calls.isEmpty());
      View dialog = dialogFrame(host);
      View tab = dialog.findViewWithTag("settings-tab-3");
      assertNotNull(tab);
      assertEquals("모집 확률", ((TextView) tab).getText().toString());
      tab.performClick();
      dialogFrame(host);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals(List.of("GET draw"), api.calls);
      for (String kind : RecruitmentProbabilityView.KINDS)
        assertNotNull(dialog.findViewWithTag("recruitment-probability-" + kind));
      String labels = fixture.labels(dialog);
      assertTrue(labels.contains("0.005%"));
      assertTrue(labels.contains("1.005%"));
      assertTrue(labels.contains("2.005%"));
      assertTrue(labels.contains("보장까지 17회"));
      assertFalse(labels.contains("뽑기"));
      assertTrue(api.writes.isEmpty());
      host.visibleDialog.dismiss();
    }
  }

  @Test public void loadingErrorRetryAndMissingRateNeverInventZeroPercent() throws Exception {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      api.deferred = true;
      LinearLayout view = attach(host);
      assertEquals(1, api.calls.size());
      assertTrue(fixture.labels(view).contains("불러오는 중"));
      assertFalse(fixture.labels(view).contains("0%"));
      api.pending.get(0).done(null, "연결하지 못했어요.");
      assertTrue(fixture.labels(view).contains("다시 시도"));
      assertFalse(fixture.labels(view).contains("0%"));
      button(view, "다시 시도").performClick();
      assertEquals(2, api.calls.size());
      api.response.getJSONArray("gachas").getJSONObject(0).getJSONArray("weights")
          .getJSONObject(0).remove("w");
      api.pending.get(1).done(api.response, null);
      assertTrue(fixture.labels(view).contains("확인 필요"));
      TextView missing = view.findViewWithTag("recruitment-rate-LOW-COMMON");
      assertEquals("확인 필요", missing.getText().toString());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void hiddenDetachedOrChangedAccountRejectsStaleResponse() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      api.deferred = true;
      LinearLayout view = attach(host);
      NativeApi.Result first = api.pending.get(0);
      view.setVisibility(View.GONE);
      first.done(api.response, null);
      assertNull(view.findViewWithTag("recruitment-rate-LOW-COMMON"));
      view.setVisibility(View.VISIBLE);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals(2, api.calls.size());
      api.session = "other-account";
      api.pending.get(1).done(api.response, null);
      assertNull(view.findViewWithTag("recruitment-rate-LOW-COMMON"));
      assertTrue(fixture.labels(view).contains("계정이 변경"));
      button(view, "새로고침").performClick();
      NativeApi.Result detached = api.pending.get(2);
      host.root.removeView(view);
      detached.done(api.response, null);
      assertNull(view.findViewWithTag("recruitment-rate-LOW-COMMON"));
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void changedNavigationRejectsInFlightRates() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      api.deferred = true;
      LinearLayout view = attach(host);
      host.epoch++;
      api.pending.get(0).done(api.response, null);
      assertNull(view.findViewWithTag("recruitment-rate-LOW-COMMON"));
      assertTrue(fixture.labels(view).contains("화면이나 계정이 변경"));
    }
  }

  @Test public void recruitUsesCorrectActionAndUnchangedKindTimesCostPityWithoutExtraPost() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      String original = api.response.toString();
      host.menus.economy.draw();
      fixture.measure(host.root);
      Button claim = host.root.findViewWithTag("draw-claim");
      assertEquals("지원서 받기", claim.getText().toString());
      assertTrue("Claim wording fits beside its icon without wrapping",
          claim.getPaint().measureText(claim.getText().toString())
              <= claim.getWidth() - claim.getCompoundPaddingLeft() - claim.getCompoundPaddingRight());
      for (String kind : RecruitmentProbabilityView.KINDS) {
        host.root.findViewWithTag("draw-card-" + kind).performClick();
        Button go = host.root.findViewWithTag("draw-submit");
        assertEquals("모집하기", go.getText().toString());
      }
      View quantity = host.root.findViewWithTag("draw-quantity");
      button(quantity, "＋").performClick();
      TextView total = host.root.findViewWithTag("draw-total");
      assertTrue(total.getText().toString().contains("10장"));
      Button go = host.root.findViewWithTag("draw-submit");
      go.performClick();
      assertTrue(api.writes.isEmpty());
      assertNotNull(host.visibleDialog);
      String confirmation = fixture.labels(host.visibleDialog.getWindow().getDecorView());
      assertTrue(confirmation.contains("고급 모집 2회 · 지원서 10장"));
      assertFalse(confirmation.contains("%"));
      host.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, api.writes.size());
      JSONObject body = api.writes.get(0);
      assertEquals(2, body.length());
      assertEquals("HIGH", body.optString("kind"));
      assertEquals(2, body.optInt("times"));
      host.menus.economy.performDraw("draw", MainActivity.obj("kind", "HIGH", "times", 2), "draw");
      assertEquals("A repeated tap cannot spend again while the request is pending", 1, api.writes.size());
      assertEquals("Client must not alter server credits, weights or pity", original, api.response.toString());
      assertEquals(List.of("GET draw", "POST draw"), api.calls);
    }
  }

  @Test public void claimKeepsItsOwnEndpointAndEmptyBody() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      host.menus.economy.draw();
      Button claim = host.root.findViewWithTag("draw-claim");
      assertTrue(claim.isEnabled());
      claim.performClick();
      assertEquals(List.of("GET draw", "POST claim"), api.calls);
      assertEquals(1, api.writes.size());
      assertEquals(0, api.writes.get(0).length());
    }
  }

  @Test public void recruitmentGuidanceDoesNotRepeatProbabilityTables() {
    try (var controller = fixture.start()) {
      MainActivity host = controller.get();
      FakeApi api = prepare(host);
      host.menus.economy.draw();
      assertNull(button(host.root, "확률·보장"));
      button(host.root, "지급·보장").performClick();
      String labels = fixture.labels(host.visibleDialog.getWindow().getDecorView());
      assertTrue(labels.contains("설정 > 모집 확률"));
      assertTrue(labels.contains("보장까지 17회"));
      assertFalse(labels.contains("%"));
      assertNull(button(host.visibleDialog.getWindow().getDecorView(), "고급 모집 확률 보기"));
      assertEquals(List.of("GET draw"), api.calls);
      host.visibleDialog.dismiss();
    }
  }

  @Test public void serverTerminologyIsOnlyConvertedAtDisplayBoundary() {
    String original = "뽑기권을 받아 뽑기로 가요. 뽑기권이 모자라요.";
    assertEquals("지원서를 받아 모집으로 가요. 지원서가 모자라요.", RecruitmentText.display(original));
    assertTrue(original.contains("뽑기권"));
    assertEquals("확인 필요", RecruitmentProbabilityView.percent(new JSONObject()));
    assertEquals("0%", RecruitmentProbabilityView.percent(MainActivity.obj("w", 0)));
    assertEquals("0.005%", RecruitmentProbabilityView.percent(MainActivity.obj("w", 0.005)));
  }
}
