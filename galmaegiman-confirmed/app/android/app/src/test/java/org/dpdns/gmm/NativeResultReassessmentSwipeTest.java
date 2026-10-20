package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

/** 실제 터치·등록증·서버 응답으로 결과 재심사와 세 장 단위 넘기기를 함께 검증한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeResultReassessmentSwipeTest {
  final NativeResultDossierTest records = new NativeResultDossierTest();
  final NativeGameFlowTest fixtures = records.fixtures;
  final NativeAcquisitionFlowTest acquisition = records.acquisition;
  JSONObject catalog;
  final JSONObject screenshotObservations = new JSONObject();

  @Before public void setup() throws Exception {
    records.setup();
    catalog = records.catalog;
    RuntimeEnvironment.getApplication().getSharedPreferences("native_progression_requests", 0)
        .edit().clear().commit();
  }

  @After public void restore() { records.restore(); }

  JSONObject item(String id) throws Exception {
    return new JSONObject(NativeUnitDossier.entry(catalog, id).toString());
  }

  JSONObject payload() throws Exception {
    JSONArray items = new JSONArray();
    for (String id : new String[] {"R13", "R14", "S3", "S4", "D1", "I3"}) {
      JSONObject unit = item(id);
      if (id.equals("R13")) unit.put("ownedKeys", new JSONArray().put("o2"));
      if (id.equals("R14")) unit.put("ownedKeys", new JSONArray().put("o3"));
      items.put(unit);
    }
    return MainActivity.obj("items", items);
  }

  JSONObject secondPagePayload() throws Exception {
    JSONArray items = new JSONArray();
    for (String id : new String[] {"S3", "R14", "S4", "D1", "R13", "I3"}) {
      JSONObject unit = item(id);
      if (id.equals("R13")) unit.put("ownedKeys", new JSONArray().put("o2"));
      items.put(unit);
    }
    return MainActivity.obj("items", items);
  }

  JSONObject payloadWithCopies(int count) throws Exception {
    JSONObject result = payload();
    JSONArray keys = new JSONArray();
    for (int i = 0; i < count; i++) keys.put("o" + (2 + i));
    result.getJSONArray("items").getJSONObject(0).put("n", count).put("ownedKeys", keys);
    // R13의 두 번째 개체 o3를 별도 R14에도 배정하지 않는다.
    result.getJSONArray("items").getJSONObject(1).put("ownedKeys", new JSONArray().put("o30"));
    Set<String> seen = new HashSet<>();
    JSONArray items = result.getJSONArray("items");
    for (int i = 0; i < items.length(); i++) {
      JSONArray owned = items.getJSONObject(i).optJSONArray("ownedKeys");
      for (int j = 0; owned != null && j < owned.length(); j++)
        assertTrue("실제 지급 영수증 fixture의 개체 키는 결과 전체에서 고유하다", seen.add(owned.getString(j)));
    }
    return result;
  }

  JSONObject candidate(String key, String id) throws Exception {
    JSONObject unit = item(id);
    // 실제 progression 응답에는 eligible/locked가 생략된다. 후보 목록 자체가 서버의 허가다.
    return MainActivity.obj("key", key, "id", id, "name", unit.optString("name"),
        "rarity", unit.optString("tier"));
  }

  JSONObject progression(JSONObject... candidates) {
    JSONObject result = NativeProgressionTest.fixture();
    try {
      result.getJSONObject("reassessment").put("candidates", new JSONArray(Arrays.asList(candidates)));
    } catch (JSONException error) { throw new AssertionError(error); }
    return result;
  }

  JSONObject state() {
    // native/state.units에는 전투 최상위 개체만 있다. 희귀 개체를 여기에 만들어 넣지 않는다.
    return MainActivity.obj("me", MainActivity.obj("units", catalog.optJSONArray("units"),
        "snack", 0, "peanut", 0, "credits", 0), "units", new JSONArray(), "team", new JSONArray());
  }

  JSONObject receipt(String key, String id) throws Exception {
    JSONObject unit = item(id);
    return MainActivity.obj("ok", true, "key", key, "id", id,
        "name", unit.optString("name"), "rarity", unit.optString("tier"),
        "reincarnation", 0, "inheritedFrom", JSONObject.NULL, "statBonusPct", 0,
        "nextAvailableAt", 2000000L);
  }

  final class FakeApi extends NativeApi {
    String account = "result-reassessment.session";
    JSONObject progression, snapshot;
    String progressionError;
    boolean holdProgression, holdState;
    final List<String> reads = new ArrayList<>(), paths = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    final List<Result> progressionReads = new ArrayList<>(), stateReads = new ArrayList<>(), writes = new ArrayList<>();

    FakeApi(MainActivity host) throws Exception {
      super(host);
      progression = progression(candidate("o1", "R13"), candidate("o2", "R13"));
      snapshot = state();
    }

    @Override String token() { return account; }

    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        assertEquals("결과 확인에서 지급·모집을 다시 요청하지 않는다", "native/progression/reassess", path);
        paths.add(path);
        try { bodies.add(new JSONObject(body.toString())); }
        catch (JSONException error) { throw new AssertionError(error); }
        writes.add(done);
        return;
      }
      reads.add(path);
      if (path.equals("native/progression")) {
        if (holdProgression) progressionReads.add(done); else done.done(progression, progressionError);
      } else if (path.equals("native/state")) {
        if (holdState) stateReads.add(done); else done.done(snapshot, null);
      } else if (path.equals("native/catalog")) done.done(catalog, null);
      else fail("예상하지 않은 결과 화면 읽기: " + path);
    }

    void deliverProgression() {
      List<Result> pending = new ArrayList<>(progressionReads); progressionReads.clear();
      for (Result done : pending) done.done(progression, progressionError);
    }

    void deliverState() {
      List<Result> pending = new ArrayList<>(stateReads); stateReads.clear();
      for (Result done : pending) done.done(snapshot, null);
    }

    void reply(JSONObject result, String error) {
      assertFalse("미결 요청이 있어야 서버 응답을 전달한다", writes.isEmpty());
      writes.remove(0).done(result, error);
    }
  }

  final class Session implements AutoCloseable {
    final ActivityController<MainActivity> controller;
    final MainActivity h;
    final FakeApi api;
    final NativeAcquisitionFlow flow;
    final AtomicInteger finished = new AtomicInteger();
    NativeReveal reveal;

    Session() throws Exception { this("draw"); }

    Session(String origin) throws Exception {
      controller = fixtures.start(); h = controller.get(); h.api.close();
      api = new FakeApi(h); h.api = api; h.state = api.snapshot;
      h.motionEnabled = false; h.guide.enabled = false; h.art.key = "result-reassessment-review";
      h.base("모집 결과", "drawResult");
      flow = new NativeAcquisitionFlow(h, origin, Set.of());
    }

    NativeReveal attach(JSONObject result) {
      reveal = new NativeReveal(h, result, finished::incrementAndGet, true, flow);
      h.root.addView(reveal, new FrameLayout.LayoutParams(-1, -1));
      layout();
      return reveal;
    }

    void layout() { records.layout(h, 915, 412, 160); }

    @Override public void close() {
      controller.pause().stop().destroy();
      // 실제 Window detach를 먼저 끝내고 지연된 overlay 제거까지 확인한다.
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull(h.root.findViewWithTag("progression-reassessment-overlay"));
      while (!api.writes.isEmpty())
        api.reply(MainActivity.obj("ok", false, "retryable", false), "검수 요청 종료");
      api.close();
    }
  }

  View icon(NativeReveal reveal, int index) { return reveal.findViewWithTag("acquisition-reassess-card-" + index); }

  Button button(MainActivity h, String tag) {
    View found = h.root.findViewWithTag(tag);
    assertTrue(tag, found instanceof Button);
    return (Button) found;
  }

  void frame() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48)); }

  void touch(View target, int action, long down, long time, float x, float y) {
    MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
    target.dispatchTouchEvent(event); event.recycle();
  }

  void gesture(Session session, float dx, float dy, boolean cancel) {
    View strip = session.reveal.findViewWithTag("acquisition-cards-scroll");
    assertNotNull(strip);
    NativeReveal.RevealCard middle = records.revealFixture.cards(session.reveal).get(1);
    Rect start = records.bounds(middle, (android.view.ViewGroup) strip);
    float x = start.exactCenterX(), y = start.exactCenterY();
    long time = SystemClock.uptimeMillis();
    touch(strip, MotionEvent.ACTION_DOWN, time, time, x, y);
    touch(strip, MotionEvent.ACTION_MOVE, time, time + 32, x + dx * .6f, y + dy * .6f);
    touch(strip, MotionEvent.ACTION_MOVE, time, time + 64, x + dx, y + dy);
    touch(strip, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP,
        time, time + 96, x + dx, y + dy);
    frame(); session.layout();
  }

  void openReassessment(Session session, int index, String key) {
    View refresh = icon(session.reveal, index); assertNotNull(refresh); assertTrue(refresh.isEnabled());
    refresh.performClick(); frame();
    assertNull("재심사 아이콘은 기록 화면을 열지 않는다", session.reveal.dossier);
    assertNotNull(session.h.root.findViewWithTag("progression-reassessment-overlay"));
    assertNotNull(session.h.root.findViewWithTag("progression-reassessment-" + key));
    assertTrue(session.api.bodies.isEmpty());
  }

  void confirm(Session session, String key, boolean yes) {
    button(session.h, "progression-reassessment-" + key).performClick();
    assertTrue("확인 창에서는 아직 소모 요청을 보내지 않는다", session.api.writes.isEmpty());
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog);
    assertTrue(dialog.isShowing());
    assertTrue("소모 대상 실제 개체 번호 표시", fixtures.labels(dialog.getWindow().getDecorView()).contains(key));
    dialog.getButton(yes ? AlertDialog.BUTTON_POSITIVE : AlertDialog.BUTTON_NEGATIVE).performClick();
    frame();
  }

  @Test public void iconWaitsForServerAndOnlyMapsNewEligibleExactSpecies() throws Exception {
    try (Session s = new Session()) {
      s.api.holdProgression = true;
      s.api.progression = progression(candidate("o1", "R13"), candidate("o2", "R13"),
          candidate("o3", "R14").put("eligible", false), candidate("o4", "R14").put("locked", true),
          candidate("o5", "S3"));
      JSONObject result = payload();
      result.getJSONArray("items").getJSONObject(1).put("n", 2)
          .put("ownedKeys", new JSONArray().put("o3").put("o4"));
      result.getJSONArray("items").getJSONObject(2).put("ownedKeys", new JSONArray().put("o5"));
      s.attach(result);
      assertNull("서버 응답 전에 등급만 보고 버튼을 만들지 않는다", icon(s.reveal, 0));
      assertEquals(Collections.singletonList("native/progression"), s.api.reads);
      s.api.deliverProgression(); s.api.holdProgression = false; s.layout();
      assertNotNull(icon(s.reveal, 0));
      assertNull("명시적 거부·보호 개체는 사용하지 않는다", icon(s.reveal, 1));
      assertNull("서버와 무관한 낮은 등급을 재심사하지 않는다", icon(s.reveal, 2));
      assertTrue(s.api.bodies.isEmpty());
      openReassessment(s, 0, "o2");
      assertNull(s.h.root.findViewWithTag("progression-reassessment-o1"));
      button(s.h, "progression-close").performClick();
      assertEquals(0, s.reveal.resultPage); assertEquals(0, s.flow.selected);
    }
  }

  @Test public void oldOwnedConcurrentAndUnprovenKeysNeverBecomeThisAcquisition() throws Exception {
    try (Session s = new Session()) {
      s.api.progression = progression(candidate("o1", "R13"), candidate("o99", "R13"));
      s.attach(payload());
      for (int i = 0; i < 3; i++) assertNull(icon(s.reveal, i));
      assertTrue(s.api.bodies.isEmpty());
    }
    try (Session s = new Session()) {
      JSONObject unproven = payload();
      for (int i = 0; i < unproven.getJSONArray("items").length(); i++)
        unproven.getJSONArray("items").getJSONObject(i).remove("ownedKeys");
      s.attach(unproven);
      assertNull("과거 서버 결과에는 개체 차집합을 추측하지 않는다", icon(s.reveal, 0));
      assertTrue(s.api.bodies.isEmpty());
    }
    try (Session s = new Session("craft")) {
      s.attach(payload());
      assertNull("조합 결과에는 고급 모집 재심사를 붙이지 않는다", icon(s.reveal, 0));
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void unsupportedServerExplainsAndReadOnlyRetryDoesNotFakeSuccess() throws Exception {
    try (Session s = new Session()) {
      s.api.progression = new JSONObject(); s.api.progressionError = "HTTP 404";
      JSONObject result = payload(); String original = result.toString(); s.attach(result);
      assertNull(icon(s.reveal, 0));
      TextView status = s.reveal.findViewWithTag("acquisition-reassessment-status");
      assertNotNull(status); assertFalse(status.getText().toString().isBlank());
      View retry = s.reveal.findViewWithTag("acquisition-reassessment-refresh"); assertNotNull(retry);
      s.api.progressionError = null; s.api.progression = progression(candidate("o2", "R13"));
      retry.performClick(); frame(); s.layout();
      assertNotNull(icon(s.reveal, 0));
      assertEquals(original, result.toString());
      assertTrue(s.api.bodies.isEmpty());
      assertEquals(2, Collections.frequency(s.api.reads, "native/progression"));
    }
  }

  @Test public void malformedOrCrossSpeciesReceiptKeysFailClosed() throws Exception {
    for (JSONArray keys : new JSONArray[] {
        new JSONArray().put("o2").put("o2"), new JSONArray().put("o2").put("o3"),
        new JSONArray().put("o0"), new JSONArray().put("R13"), new JSONArray().put("o99")}) {
      try (Session s = new Session()) {
        JSONObject result = payload(); result.getJSONArray("items").getJSONObject(0).put("ownedKeys", keys);
        if (keys.length() == 2 && keys.optString(0).equals(keys.optString(1)))
          result.getJSONArray("items").getJSONObject(0).put("n", 2);
        s.api.progression = progression(candidate("o2", "R13"), candidate("o99", "R14"));
        s.attach(result);
        assertNull("개체 수·고유 키·종이 모두 일치해야 한다: " + keys, icon(s.reveal, 0));
        assertTrue(s.api.bodies.isEmpty());
      }
    }
    try (Session s = new Session()) {
      JSONObject result = payload();
      result.getJSONArray("items").getJSONObject(1).put("ownedKeys", new JSONArray().put("o2"));
      s.api.progression = progression(candidate("o2", "R13"));
      s.attach(result);
      assertNull("다른 종의 카드와 공유한 개체 키는 실행하지 않는다", icon(s.reveal, 0));
      assertNull(icon(s.reveal, 1));
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void serverCooldownCannotBeBypassedByWaitingOnClientClock() throws Exception {
    try (Session s = new Session()) {
      s.api.progression.getJSONObject("reassessment").put("nextAvailableAt", 2000000L);
      s.attach(payload());
      View refresh = icon(s.reveal, 0);
      assertTrue("서버 대기 중에는 없거나 비활성인 아이콘", refresh == null || !refresh.isEnabled());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30));
      refresh = icon(s.reveal, 0);
      assertTrue("기기 시간 경과만으로 권한을 만들지 않는다", refresh == null || !refresh.isEnabled());
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void horizontalSwipesMoveThreeCardsWithoutDossierAndKeepBackSelection() throws Exception {
    try (Session s = new Session()) {
      s.attach(payload());
      String original = s.reveal.result.toString();
      gesture(s, -s.h.dp(160), s.h.dp(4), false);
      assertEquals(1, s.reveal.resultPage); assertEquals(3, s.flow.selected);
      assertNull(s.reveal.dossier); assertEquals(3, records.revealFixture.cards(s.reveal).size());
      assertNotNull(s.reveal.findViewWithTag("acquisition-card-S4"));
      assertNull(s.reveal.findViewWithTag("acquisition-card-R13"));
      gesture(s, -s.h.dp(160), 0, false);
      assertEquals("최종 페이지를 넘어서지 않는다", 1, s.reveal.resultPage);
      gesture(s, s.h.dp(160), 0, false);
      assertEquals(0, s.reveal.resultPage); assertNull(s.reveal.dossier);
      assertEquals(Collections.singletonList("native/progression"), s.api.reads);
      button(s.h, "acquisition-next-page").performClick(); s.layout();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
      View tapped = s.reveal.findViewWithTag("acquisition-card-I3"); tapped.performClick();
      assertNotNull(s.reveal.dossier); assertEquals(5, s.flow.selected);
      s.h.getOnBackPressedDispatcher().onBackPressed();
      assertNull(s.reveal.dossier); assertEquals(1, s.reveal.resultPage); assertEquals(5, s.flow.selected);
      assertSame(tapped, s.reveal.findViewWithTag("acquisition-card-I3"));
      assertEquals(original, s.reveal.result.toString()); assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void cancelledAndVerticalGesturesDoNotPageOrOpenDossier() throws Exception {
    try (Session s = new Session()) {
      s.attach(payload());
      gesture(s, -s.h.dp(160), 0, true);
      assertEquals(0, s.reveal.resultPage); assertNull(s.reveal.dossier);
      gesture(s, s.h.dp(4), s.h.dp(100), false);
      assertEquals(0, s.reveal.resultPage); assertNull(s.reveal.dossier);
      assertEquals(Collections.singletonList("native/progression"), s.api.reads);
      assertTrue(s.api.bodies.isEmpty());
    }
  }

  @Test public void cancelIsReadOnlyAndConfirmedRetryUsesSameExactDurableIntent() throws Exception {
    try (Session s = new Session()) {
      s.attach(payload()); String original = s.reveal.result.toString();
      openReassessment(s, 0, "o2"); confirm(s, "o2", false);
      assertTrue(s.api.bodies.isEmpty());
      confirm(s, "o2", true);
      assertEquals(1, s.api.bodies.size());
      JSONObject body = s.api.bodies.get(0);
      assertEquals("o2", body.optString("key")); assertFalse(body.has("id"));
      assertFalse(body.optString("requestId").isEmpty());
      View retry = s.h.root.findViewWithTag("progression-retry"); assertNotNull(retry);
      retry.performClick(); assertEquals("동시 중복 요청 방지", 1, s.api.bodies.size());
      s.api.reply(new JSONObject(), null);
      assertEquals("ok 없는 응답은 카드에 성공을 그리지 않는다", original, s.reveal.result.toString());
      button(s.h, "progression-retry").performClick();
      assertEquals(body.toString(), s.api.bodies.get(1).toString());
      s.api.reply(MainActivity.obj("ok", true, "key", "o2", "id", "R14", "rarity", "RARE"), null);
      assertEquals("소모 전과 같은 개체 번호는 변환 영수증이 아니다", original, s.reveal.result.toString());
      assertFalse(s.api.reads.contains("native/state"));
      button(s.h, "progression-retry").performClick();
      assertEquals(body.toString(), s.api.bodies.get(2).toString());
      s.api.reply(null, "연결하지 못했어요.");
      button(s.h, "progression-retry").performClick();
      assertEquals(body.toString(), s.api.bodies.get(3).toString());
      assertEquals(original, s.reveal.result.toString());
      s.api.reply(MainActivity.obj("ok", false, "retryable", false), "지금은 대상이 아니에요.");
      assertEquals(original, s.reveal.result.toString());
    }
  }

  @Test public void multipleNewInstancesRequireChooserAndNeverSelectOldOwnedKey() throws Exception {
    try (Session s = new Session()) {
      s.api.progression = progression(candidate("o1", "R13"), candidate("o2", "R13"),
          candidate("o3", "R13"), candidate("o99", "R13"));
      JSONObject result = payloadWithCopies(2);
      s.attach(result);
      View refresh = icon(s.reveal, 0); assertNotNull("고유한 이번 획득 개체 둘의 재심사 선택", refresh);
      refresh.performClick(); frame();
      AlertDialog chooser = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(chooser);
      assertNotNull(chooser.getListView()); assertEquals(2, chooser.getListView().getAdapter().getCount());
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertTrue(s.api.bodies.isEmpty());
      Shadows.shadowOf(chooser).clickOnItem(1); frame();
      assertNotNull(s.h.root.findViewWithTag("progression-reassessment-o3"));
      assertNull(s.h.root.findViewWithTag("progression-reassessment-o1"));
      confirm(s, "o3", true);
      assertEquals("o3", s.api.bodies.get(0).optString("key"));
      s.api.reply(MainActivity.obj("ok", false, "retryable", false), "검수 취소");
    }
  }

  @Test public void successRefreshesAuthoritativeStateAndArtWithoutLosingPageOrSelection() throws Exception {
    try (Session s = new Session()) {
      s.attach(secondPagePayload()); button(s.h, "acquisition-next-page").performClick(); s.layout();
      openReassessment(s, 4, "o2"); confirm(s, "o2", true);
      String before = s.reveal.result.toString(); s.api.holdState = true;
      s.api.progression = progression();
      s.api.progression.getJSONObject("reassessment").put("nextAvailableAt", 2000000L);
      s.api.reply(receipt("o20", "R14"), null);
      assertEquals("상태 재조회 전에는 결과를 추측해 바꾸지 않는다", before, s.reveal.result.toString());
      assertEquals(1, s.api.stateReads.size());
      s.api.snapshot = state().put("revision", 20);
      s.api.deliverState(); frame();
      View close = s.h.root.findViewWithTag("progression-close"); if (close != null) close.performClick();
      s.layout();
      assertSame(s.api.snapshot, s.h.state);
      assertEquals(1, s.reveal.resultPage); assertEquals(4, s.flow.selected);
      JSONObject replaced = s.reveal.units.getJSONObject(4);
      assertEquals("R14", replaced.optString("id")); assertEquals(1, replaced.optInt("n", 1));
      NativeReveal.RevealCard card = s.reveal.findViewWithTag("acquisition-card-R14"); assertNotNull(card);
      RegistrationCardView.Artwork artwork = acquisition.registrationArtwork(card);
      assertEquals("R14", artwork.id);
      records.review.await(() -> artwork.bitmap != null); assertNotNull(artwork.bitmap);
      assertNull(icon(s.reveal, 4)); assertEquals(1, s.api.bodies.size());
      button(s.h, "acquisition-confirm").performClick(); button(s.h, "acquisition-confirm").performClick();
      assertEquals(1, s.finished.get());
    }
  }

  @Test public void convertingOneOfAggregatedCopiesPreservesEveryUnconvertedCopy() throws Exception {
    try (Session s = new Session()) {
      s.api.progression = progression(candidate("o1", "R13"), candidate("o2", "R13"));
      JSONObject result = payloadWithCopies(3); result.getJSONArray("items").getJSONObject(0).put("first", true);
      s.attach(result); openReassessment(s, 0, "o2"); confirm(s, "o2", true);
      s.api.progression = progression(); s.api.reply(receipt("o20", "R14"), null); frame();
      View close = s.h.root.findViewWithTag("progression-close"); if (close != null) close.performClick();
      s.layout();
      assertEquals(7, s.reveal.units.length());
      assertEquals("R14", s.reveal.units.getJSONObject(0).optString("id"));
      assertEquals(1, s.reveal.units.getJSONObject(0).optInt("n", 1));
      assertEquals("R13", s.reveal.units.getJSONObject(1).optString("id"));
      assertEquals(2, s.reveal.units.getJSONObject(1).optInt("n"));
      assertEquals("[\"o20\"]", s.reveal.units.getJSONObject(0).getJSONArray("ownedKeys").toString());
      assertEquals("[\"o3\",\"o4\"]", s.reveal.units.getJSONObject(1).getJSONArray("ownedKeys").toString());
      int total = 0;
      for (int i = 0; i < s.reveal.units.length(); i++) total += s.reveal.units.getJSONObject(i).optInt("n", 1);
      assertEquals("재심사 한 마리만 교체하며 총 획득 수 보존", 8, total);
      assertEquals(0, s.reveal.resultPage); assertEquals(0, s.flow.selected);
      assertTrue("일곱 provenance 묶음의 중복 종을 종류 수로 세지 않는다",
          fixtures.labels(s.reveal).contains("모집 결과 6종"));
      assertFalse(fixtures.labels(s.reveal).contains("모집 결과 7종"));
      assertEquals("전체 결과 목록 · 6종",
          button(s.h, "acquisition-result-picker").getContentDescription().toString());
    }
  }

  @Test public void closingOverlayDuringMutationStillReconcilesTheOwnedResultWithoutReopening() throws Exception {
    try (Session s = new Session()) {
      s.attach(payload()); openReassessment(s, 0, "o2"); confirm(s, "o2", true);
      int epoch = s.h.epoch;
      button(s.h, "progression-close").performClick(); frame();
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertEquals("R13", s.reveal.units.getJSONObject(0).optString("id"));
      s.api.holdState = true; s.api.progression = progression();
      s.api.reply(receipt("o20", "R14"), null);
      assertEquals(1, s.api.stateReads.size());
      assertEquals("상태 재조회 전에는 이전 카드 유지", "R13", s.reveal.units.getJSONObject(0).optString("id"));
      s.api.deliverState(); frame(); s.layout();
      assertNull("닫은 진행 화면은 다시 열지 않는다", s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertEquals("R14", s.reveal.units.getJSONObject(0).optString("id"));
      assertEquals("[\"o20\"]", s.reveal.units.getJSONObject(0).getJSONArray("ownedKeys").toString());
      assertEquals(epoch, s.h.epoch); assertEquals("drawResult", s.h.screen);
      assertEquals(0, s.reveal.resultPage); assertEquals(0, s.flow.selected);
      assertEquals(1, s.api.bodies.size());
    }
  }

  @Test public void retryOpenedFromAnotherCardReplacesTheOriginalSourceKeyOnly() throws Exception {
    try (Session s = new Session()) {
      s.api.progression = progression(candidate("o2", "R13"), candidate("o3", "R14"));
      s.attach(payload()); openReassessment(s, 0, "o2"); confirm(s, "o2", true);
      String request = s.api.bodies.get(0).toString();
      s.api.reply(null, "연결하지 못했어요.");
      button(s.h, "progression-close").performClick(); frame(); s.layout();
      View other = icon(s.reveal, 1); assertNotNull(other); other.performClick(); frame();
      assertNotNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertNull("이전 요청 확인 전에 다른 개체를 소모할 수 없다", s.h.root.findViewWithTag("progression-reassessment-o3"));
      button(s.h, "progression-retry").performClick();
      assertEquals(2, s.api.bodies.size()); assertEquals(request, s.api.bodies.get(1).toString());
      s.api.progression = progression(); s.api.reply(receipt("o20", "R14"), null); frame(); s.layout();
      assertEquals("R14", s.reveal.units.getJSONObject(0).optString("id"));
      assertEquals("[\"o20\"]", s.reveal.units.getJSONObject(0).getJSONArray("ownedKeys").toString());
      assertEquals("R14", s.reveal.units.getJSONObject(1).optString("id"));
      assertEquals("다른 카드의 개체 키는 교체하지 않는다", "[\"o3\"]",
          s.reveal.units.getJSONObject(1).getJSONArray("ownedKeys").toString());
      assertEquals(6, s.reveal.units.length());
    }
  }

  @Test public void lateEligibilityAndStateReadsCannotPaintDetachedOrOtherAccountResults() throws Exception {
    for (String exit : new String[] {"detach", "epoch", "account"}) {
      try (Session s = new Session()) {
        s.api.holdProgression = true; s.attach(payload());
        if (exit.equals("detach")) s.h.root.removeView(s.reveal);
        else if (exit.equals("epoch")) s.h.epoch++;
        else s.api.account = "different.session";
        s.api.deliverProgression(); frame();
        assertNull(icon(s.reveal, 0)); assertTrue(s.api.bodies.isEmpty());
      }
      try (Session s = new Session()) {
        s.attach(payload()); openReassessment(s, 0, "o2"); confirm(s, "o2", true);
        s.api.holdState = true; s.api.progression = progression();
        s.api.reply(receipt("o20", "R14"), null);
        String original = s.reveal.result.toString(); JSONObject oldState = s.h.state;
        if (exit.equals("detach")) s.h.root.removeView(s.reveal);
        else if (exit.equals("epoch")) s.h.epoch++;
        else s.api.account = "different.session";
        s.api.snapshot = state().put("revision", 99); s.api.deliverState(); frame();
        assertEquals(original, s.reveal.result.toString()); assertSame(oldState, s.h.state);
      }
    }
  }

  @Test public void navigatingDuringMutationDoesNotReopenOrRepaintAnAbandonedResult() throws Exception {
    for (boolean switchAccount : new boolean[] {false, true}) try (Session s = new Session()) {
      JSONObject result = payload(); s.attach(result);
      NativeReveal abandoned = s.reveal;
      openReassessment(s, 0, "o2"); confirm(s, "o2", true);
      String original = result.toString(), originalAccount = s.api.account;
      s.h.base("다른 화면", "home");
      if (switchAccount) {
        s.api.account = "foreign.session";
        s.h.state = state().put("accountMarker", "foreign");
      }
      JSONObject visibleState = s.h.state;
      int stateReadsBeforeReply = Collections.frequency(s.api.reads, "native/state");
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      s.api.reply(receipt("o20", "R14"), null); frame();
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertNull("이전 결과 화면은 복원하지 않는다", abandoned.getParent());
      assertEquals("다른 화면에서 이전 결과를 덧그리지 않는다", original, result.toString());
      assertSame("성공 응답은 현재 화면/다른 계정 상태를 덮지 않는다", visibleState, s.h.state);
      assertEquals("home", s.h.screen);
      assertEquals(stateReadsBeforeReply, Collections.frequency(s.api.reads, "native/state"));
      assertNotNull("원래 계정의 획득 흐름에는 확정 영수증을 남긴다", s.flow.pendingReassessment);
      assertEquals("o2", s.flow.pendingReassessmentKey);
      assertEquals("o20", s.flow.pendingReassessment.optString("key"));

      if (switchAccount) {
        s.api.holdState = true;
        s.h.base("모집 결과", "drawResult"); s.attach(result);
        assertNull("다른 계정에서 원래 흐름을 다시 열어도 영수증을 가져오지 않는다", s.reveal.pendingReassessment);
        assertEquals(stateReadsBeforeReply, Collections.frequency(s.api.reads, "native/state"));
        assertTrue(s.api.stateReads.isEmpty());
        assertEquals(original, result.toString()); assertSame(visibleState, s.h.state);
        assertNotNull("다른 계정 화면에서 원래 계정 영수증을 버리지 않는다", s.flow.pendingReassessment);
        s.h.base("다른 화면", "home"); s.api.account = originalAccount;
      }

      s.api.holdState = true; s.api.progression = progression();
      s.h.base("모집 결과", "drawResult"); s.attach(result);
      assertEquals("같은 계정으로 돌아오면 상태 읽기로만 영수증을 반영한다", 1, s.api.stateReads.size());
      assertEquals(original, result.toString()); assertEquals(1, s.api.bodies.size());
      s.api.snapshot = state().put("revision", 20); s.api.deliverState(); frame(); s.layout();
      assertSame(s.api.snapshot, s.h.state);
      assertEquals("R14", s.reveal.units.getJSONObject(0).optString("id"));
      assertEquals("[\"o20\"]", s.reveal.units.getJSONObject(0).getJSONArray("ownedKeys").toString());
      assertNull(s.flow.pendingReassessment);
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertEquals("drawResult", s.h.screen);
      assertEquals(0, s.reveal.resultPage); assertEquals(0, s.flow.selected);
      assertEquals("영수증 재조정으로 두 번째 변환 요청을 보내지 않는다", 1, s.api.bodies.size());
    }
    // 결과를 먼저 다시 연 뒤, 이전 화면의 변환 응답이 도착하는 순서도 같은 흐름으로 합친다.
    try (Session s = new Session()) {
      JSONObject result = payload(); s.attach(result);
      NativeReveal abandoned = s.reveal;
      NativeReveal.RevealCard abandonedCard = abandoned.findViewWithTag("acquisition-card-R13");
      openReassessment(s, 0, "o2"); confirm(s, "o2", true);
      String original = result.toString();
      s.h.base("다른 화면", "home");
      s.api.holdState = true;
      s.h.base("모집 결과", "drawResult"); s.attach(result);
      NativeReveal reopened = s.reveal;
      assertNotSame(abandoned, reopened);
      assertNull(s.flow.pendingReassessment); assertNull(reopened.pendingReassessment);
      assertTrue("영수증 도착 전에는 상태를 추측해서 조회하지 않는다", s.api.stateReads.isEmpty());
      s.api.progression = progression(); s.api.reply(receipt("o20", "R14"), null);
      assertNotNull(s.flow.pendingReassessment);
      reopened.refreshMotion(); reopened.refreshMotion(); frame(); s.layout();
      assertEquals("여러 preDraw에도 새 영수증을 한 번만 동기화한다", 1, s.api.stateReads.size());
      assertEquals(original, result.toString());
      assertNull(abandoned.getParent());
      assertEquals("이전 카드 View는 다시 그리지 않는다", "R13", acquisition.registrationArtwork(abandonedCard).id);
      s.api.snapshot = state().put("revision", 21); s.api.deliverState(); frame(); s.layout();
      assertSame(reopened, s.reveal); assertSame(s.h.root, reopened.getParent());
      assertSame(s.api.snapshot, s.h.state);
      assertEquals("R14", reopened.units.getJSONObject(0).optString("id"));
      assertEquals("[\"o20\"]", reopened.units.getJSONObject(0).getJSONArray("ownedKeys").toString());
      assertEquals("R13", acquisition.registrationArtwork(abandonedCard).id);
      assertNull(s.flow.pendingReassessment);
      assertEquals(1, Collections.frequency(s.api.reads, "native/state"));
      assertEquals(1, s.api.bodies.size());
      assertNull(s.h.root.findViewWithTag("progression-reassessment-overlay"));
      assertEquals(0, reopened.resultPage); assertEquals(0, s.flow.selected);
    }
  }

  void capture(Session session, String name, int[] size) throws Exception {
    records.layout(session.h, size[0], size[1], size[2]);
    records.review.await(() -> session.h.registrationArt().pendingCount() == 0);
    records.layout(session.h, size[0], size[1], size[2]);
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"), "result-reassessment");
    Files.createDirectories(output);
    records.captures.writeScreenshot(session.h, output.resolve(name + ".png"), size[0], size[1]);
    JSONArray species = new JSONArray(), refreshTargets = new JSONArray();
    for (NativeReveal.RevealCard card : records.revealFixture.cards(session.reveal)) {
      species.put(acquisition.registrationArtwork(card).id);
      for (View view : records.captures.geometry.descendants(card)) {
        if (view.getTag() == null || !view.getTag().toString().startsWith("acquisition-reassess-card-")) continue;
        Rect rect = records.bounds(view, session.h.root);
        float density = session.h.getResources().getDisplayMetrics().density;
        refreshTargets.put(MainActivity.obj("tag", view.getTag().toString(),
            "bounds", new JSONArray(Arrays.asList(rect.left, rect.top, rect.right, rect.bottom)),
            "widthDp", view.getWidth() / density, "heightDp", view.getHeight() / density,
            "enabled", view.isEnabled(), "description", String.valueOf(view.getContentDescription())));
      }
    }
    NativeReveal.RevealStrip strip = session.reveal.findViewWithTag("acquisition-cards-scroll");
    screenshotObservations.put(name, MainActivity.obj(
        "viewport", new JSONArray(Arrays.asList(session.h.root.getWidth(), session.h.root.getHeight())),
        "densityDpi", session.h.getResources().getDisplayMetrics().densityDpi,
        "fontScale", session.h.getResources().getConfiguration().fontScale,
        "resultPage", session.reveal.resultPage, "selectedIndex", session.flow.selected,
        "visibleSpecies", species, "visibleCardCount", species.length(),
        "translationX", strip.cardsRow.getTranslationX(), "mutationCount", session.api.bodies.size(),
        "dossierOpen", session.reveal.dossier != null, "refreshTargets", refreshTargets));
    Files.write(output.resolve("observations.json"), screenshotObservations.toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void captureRefreshTargetAndSwipeAtRealDensityAndFont130() throws Exception {
    for (int[] size : new int[][] {{915, 412, 160}, {780, 360, 160}, {2048, 838, 360}, {1280, 720, 160}})
      for (float font : new float[] {1, 1.3f}) {
        RuntimeEnvironment.setQualifiers("w" + Math.round(size[0] * 160f / size[2]) + "dp-h"
            + Math.round(size[1] * 160f / size[2]) + "dp-land-" + size[2] + "dpi");
        RuntimeEnvironment.setFontScale(font);
        try (Session s = new Session()) {
          JSONObject result = payloadWithCopies(3); result.getJSONArray("items").getJSONObject(0).put("first", true);
          s.reveal = new NativeReveal(s.h, result, s.finished::incrementAndGet, true, s.flow);
          s.h.root.addView(s.reveal, new FrameLayout.LayoutParams(-1, -1));
          String name = size[0] + "x" + size[1] + "-dpi" + size[2] + "-font" + Math.round(font * 100);
          capture(s, name + "-eligible", size);
          View refresh = icon(s.reveal, 0); assertNotNull(refresh); records.fullyVisible(refresh, s.h);
          assertTrue(refresh.getWidth() >= s.h.dp(48)); assertTrue(refresh.getHeight() >= s.h.dp(48));
          assertNotNull(refresh.getContentDescription());
          assertTrue(refresh.getContentDescription().toString().contains("재심사"));
          NativeReveal.RevealCard first = s.reveal.findViewWithTag("acquisition-card-R13");
          assertTrue("48dp 터치 영역은 유지하고 보이는 인장은 작게 그린다",
              refresh.getBackground() instanceof NativeReveal.ReassessmentSeal);
          assertEquals("글꼴에 의존하는 회전 문자를 겹쳐 그리지 않는다", "", ((TextView) refresh).getText().toString());
          assertNotNull(first.newBadge); assertNotNull(first.countBadge);
          assertTrue("작은 NEW 배지가 제목 쪽으로 넓어지지 않는다", first.newBadge.getWidth() <= s.h.dp(36));
          assertTrue("확대 글꼴에서도 NEW 전체를 표시한다",
              first.newBadge.getWidth() >= Math.ceil(first.newBadge.getPaint().measureText("NEW")));
          assertEquals("NEW", first.newBadge.getText().toString());
          assertTrue("NEW는 초상 대사를 가리지 않는 왼쪽 위 모서리에 둔다",
              first.newBadge.getTop() <= s.h.dp(4));
          assertTrue("수량은 인쇄된 등록증 제목 아래에 놓인다",
              first.countBadge.getTop() >= Math.round(first.portraitHeight * .205f));
          Rect card = records.bounds(first, s.h.root), target = records.bounds(refresh, s.h.root);
          assertTrue(card.contains(target));
          assertTrue("등록증 오른쪽 위 터치 영역", target.top - card.top <= s.h.dp(8)
              && card.right - target.right <= s.h.dp(8));
          for (View view : records.captures.geometry.descendants(first)) {
            if (!(view instanceof TextView) || view == refresh || !view.isShown()) continue;
            String label = ((TextView) view).getText().toString();
            if (label.equals("NEW") || label.startsWith("×"))
              assertFalse("NEW·수량 배지가 재심사 버튼을 가리지 않는다: " + label,
                  Rect.intersects(target, records.bounds(view, s.h.root)));
          }
          for (NativeReveal.RevealCard shown : records.revealFixture.cards(s.reveal)) {
            records.fullyVisible(shown, s.h);
            RegistrationCardView.Artwork art = acquisition.registrationArtwork(shown);
            assertNotNull(art.bitmap); assertEquals(.75f, art.target.width() / art.target.height(), .01f);
          }
          // 실제 밀도에서는 mdpi 고정 layout 헬퍼를 거치지 않고 스트립 터치를 전달한다.
          View strip = s.reveal.findViewWithTag("acquisition-cards-scroll");
          long time = SystemClock.uptimeMillis(); float x = strip.getWidth() * .7f, y = strip.getHeight() * .55f;
          touch(strip, MotionEvent.ACTION_DOWN, time, time, x, y);
          touch(strip, MotionEvent.ACTION_MOVE, time, time + 32, x - s.h.dp(80), y);
          touch(strip, MotionEvent.ACTION_MOVE, time, time + 64, x - s.h.dp(160), y);
          NativeReveal.RevealStrip moving = (NativeReveal.RevealStrip) strip;
          assertTrue("놓기 전에도 손가락을 따라 카드가 이동한다", moving.cardsRow.getTranslationX() <= -s.h.dp(100));
          assertEquals(0, s.reveal.resultPage); assertNull(s.reveal.dossier);
          capture(s, name + "-mid-drag", size);
          touch(strip, MotionEvent.ACTION_UP, time, time + 96, x - s.h.dp(160), y);
          frame(); capture(s, name + "-swiped", size);
          assertEquals(1, s.reveal.resultPage); assertNull(s.reveal.dossier);
          assertEquals("놓은 뒤 새 페이지는 기준 위치로 정렬", 0, moving.cardsRow.getTranslationX(), .5f);
          assertEquals(size[2], s.h.getResources().getDisplayMetrics().densityDpi);
          assertEquals(font, s.h.getResources().getConfiguration().fontScale, .01f);
          for (String tag : new String[] {"acquisition-previous-page", "acquisition-next-page", "acquisition-confirm"})
            records.readable(button(s.h, tag), s.h);
          assertTrue(s.api.bodies.isEmpty());
        }
      }
    assertEquals("모든 밀도·글꼴의 정지/드래그/다음 페이지 캡처 관측", 24, screenshotObservations.length());
  }
}

