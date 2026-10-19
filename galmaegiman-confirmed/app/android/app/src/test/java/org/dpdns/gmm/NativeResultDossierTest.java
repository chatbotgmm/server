package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.*;
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
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** 실제 Android View·글꼴·번들 등록증의 결과→기록→복귀를 검증한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeResultDossierTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeAcquisitionFlowTest acquisition = new NativeAcquisitionFlowTest();
  final RegistrationRevealTest revealFixture = new RegistrationRevealTest();
  final NativeRegistrationReviewTest captures = new NativeRegistrationReviewTest();
  JSONObject catalog;

  @Before public void setup() throws Exception {
    fixtures.isolate();
    review.scheduledFramesUseThePausedLooperClock();
    try (InputStream stream = getClass().getResourceAsStream("/acquisition-catalog.json")) {
      assertNotNull(stream);
      catalog = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @After public void restore() {
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
  }

  JSONObject item(String id) throws Exception {
    return new JSONObject(NativeUnitDossier.entry(catalog, id).toString());
  }

  JSONObject payload() throws Exception {
    JSONArray items = new JSONArray();
    for (String id : new String[] {"R13", "R14", "S3", "S4", "D1", "I3"}) {
      JSONObject result = item(id);
      // 결과 응답에 도감 설명이 없는 실제 경로를 그대로 검증한다.
      result.remove("intro"); result.remove("tagline"); result.remove("quote");
      if (id.equals("R13")) result.put("n", 3).put("first", true);
      items.put(result);
    }
    return MainActivity.obj("items", items);
  }

  JSONObject state() {
    JSONObject old = fixtures.fighter("o1"), added = fixtures.fighter("o2");
    try {
      old.put("skills", new JSONArray().put(MainActivity.obj("id", "old-only", "name", "이전 개체 기술", "power", 80)));
      added.put("skills", new JSONArray().put(MainActivity.obj("id", "exact-skill", "name", "응답 그대로의 기술",
          "power", 73, "hits", 2, "cat", "phys", "attr", "STONE")));
    } catch (JSONException error) { throw new AssertionError(error); }
    return MainActivity.obj("me", MainActivity.obj("units", catalog.optJSONArray("units")),
        "units", new JSONArray().put(old).put(added), "team", new JSONArray(),
        "attributes", MainActivity.obj("STONE", MainActivity.obj("name", "돌")));
  }

  final class FakeApi extends NativeApi {
    final List<String> reads = new ArrayList<>();
    final List<Result> callbacks = new ArrayList<>();
    final List<String> paths = new ArrayList<>();
    final JSONObject snapshot;
    String account = "result-dossier-review";
    boolean deferred;
    FakeApi(MainActivity host) { super(host); snapshot = state(); }
    @Override String token() { return account; }
    @Override void call(String path, JSONObject body, Result done) {
      assertNull("카드 열기·닫기·선택은 지급/구매/조합을 재요청하지 않음", body);
      assertTrue(path, path.equals("native/catalog") || path.equals("native/state"));
      reads.add(path);
      if (deferred) { callbacks.add(done); paths.add(path); }
      else done.done(path.equals("native/catalog") ? catalog : snapshot, null);
    }
    void deliver() {
      for (int i = 0; i < callbacks.size(); i++)
        callbacks.get(i).done(paths.get(i).equals("native/catalog") ? catalog : snapshot, null);
      callbacks.clear(); paths.clear();
    }
  }

  FakeApi prepare(MainActivity host) {
    host.api.close();
    FakeApi api = new FakeApi(host);
    host.api = api;
    host.state = api.snapshot;
    host.motionEnabled = false;
    host.guide.enabled = false;
    host.art.key = "result-dossier-review";
    return api;
  }

  NativeReveal attach(MainActivity host, AtomicInteger finished) throws Exception {
    host.base("모집 결과", "drawResult");
    NativeAcquisitionFlow flow = new NativeAcquisitionFlow(host, "draw", Set.of("o1"));
    NativeReveal reveal = new NativeReveal(host, payload(), finished::incrementAndGet, true, flow);
    host.root.addView(reveal, new FrameLayout.LayoutParams(-1, -1));
    layout(host, 915, 412, 160);
    return reveal;
  }

  void layout(MainActivity host, int width, int height, int density) {
    String qualifiers = "w" + Math.round(width * 160f / density) + "dp-h"
        + Math.round(height * 160f / density) + "dp-land-" + density + "dpi";
    // 기존 review.viewport는 mdpi로 바꾸므로 밀도 검사는 자체 Window 배치를 쓴다.
    ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, qualifiers);
    host.getWindow().setLayout(width, height);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(host.dp(18), host.dp(4), host.dp(8), host.dp(4));
    Runnable measure = () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, width, height);
    };
    measure.run();
    review.preparePreDraw(host.root, measure);
  }

  Rect bounds(View view, ViewGroup root) {
    Rect rect = new Rect();
    view.getDrawingRect(rect);
    root.offsetDescendantRectToMyCoords(view, rect);
    return rect;
  }

  void fullyVisible(View view, MainActivity host) {
    assertNotNull(view);
    Rect safe = new Rect(host.root.getPaddingLeft(), host.root.getPaddingTop(),
        host.root.getWidth() - host.root.getPaddingRight(), host.root.getHeight() - host.root.getPaddingBottom());
    assertTrue(String.valueOf(view.getTag()) + " " + bounds(view, host.root) + " in " + safe,
        safe.contains(bounds(view, host.root)));
  }

  void readable(Button button, MainActivity host) {
    fullyVisible(button, host);
    assertTrue(button.getHeight() >= host.dp(48));
    assertNotNull(button.getLayout());
    for (int i = 0; i < button.getLineCount(); i++) {
      assertEquals("라벨 말줄임 없음", 0, button.getLayout().getEllipsisCount(i));
      assertTrue("글자 가로 잘림 없음: " + button.getText(),
          button.getLayout().getLineWidth(i) <= button.getWidth()
              - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight() + 1);
    }
    assertTrue("글자 세로 잘림 없음: " + button.getText(), button.getLayout().getHeight()
        <= button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
  }

  @Test public void tapIsReadOnlyAndBackKeepsExactPageSelectionAndConfirmationOnce() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get(); FakeApi api = prepare(host);
      AtomicInteger finished = new AtomicInteger();
      NativeReveal reveal = attach(host, finished);
      String original = reveal.result.toString(), originalState = host.state.toString();
      reveal.findViewWithTag("acquisition-next-page").performClick();
      View selected = reveal.findViewWithTag("acquisition-card-D1");
      selected.performClick(); selected.performClick();
      NativeUnitDossier dossier = reveal.dossier;
      assertNotNull(dossier);
      assertEquals(1, reveal.resultPage); assertEquals(4, reveal.flow.selected);
      assertEquals(Arrays.asList("native/catalog", "native/state"), api.reads);
      assertEquals(item("D1").getString("intro"),
          ((TextView) dossier.findViewWithTag("acquisition-dossier-intro")).getText().toString());
      assertNotNull(dossier.findViewWithTag("acquisition-dossier-skill-o2-exact-skill"));
      assertNotNull("재심사는 이번 획득 실제 key로 연결", dossier.findViewWithTag("acquisition-reassess-o2"));
      assertNull("과거 개체를 이번 획득으로 재심사하지 않음", dossier.findViewWithTag("acquisition-reassess-o1"));
      assertNull(dossier.findViewWithTag("acquisition-dossier-skill-o1-old-only"));
      assertTrue(fixtures.labels(dossier).contains("73 ×2"));
      assertNull(reveal.findViewWithTag("acquisition-detail"));
      reveal.findViewWithTag("acquisition-confirm").performClick();
      assertEquals("기록이 열려 있으면 뒤쪽 확인 버튼 무효", 0, finished.get());
      host.getOnBackPressedDispatcher().onBackPressed();
      assertNull(reveal.dossier);
      assertSame(selected, reveal.findViewWithTag("acquisition-card-D1"));
      assertEquals(1, reveal.resultPage); assertEquals(4, reveal.flow.selected);
      assertEquals(original, reveal.result.toString()); assertEquals(originalState, host.state.toString());
      Button confirm = reveal.findViewWithTag("acquisition-confirm");
      confirm.performClick(); confirm.performClick();
      assertEquals(1, finished.get());
      assertEquals(2, api.reads.size());
    }
  }

  @Test public void closeEpochAccountAndDetachDiscardLateResponses() throws Exception {
    for (String exit : new String[] {"close", "epoch", "account", "detach"}) {
      try (var controller = fixtures.start()) {
        MainActivity host = controller.get(); FakeApi api = prepare(host); api.deferred = true;
        NativeReveal reveal = attach(host, new AtomicInteger());
        reveal.findViewWithTag("acquisition-card-R13").performClick();
        NativeUnitDossier old = reveal.dossier;
        assertNotNull(old);
        if (exit.equals("close")) old.findViewWithTag("acquisition-dossier-back").performClick();
        else if (exit.equals("epoch")) host.epoch++;
        else if (exit.equals("account")) api.account = "another-account";
        else host.root.removeView(reveal);
        api.deliver();
        assertNull("닫히거나 다른 화면/계정의 응답은 기록에 반영하지 않음", old.catalog);
        assertNull(old.snapshot);
        assertEquals(1, api.reads.size());
      }
    }
  }

  @Test public void onlyRequestedSpeciesMaySupplyRecordsAndHiddenCardsStayMasked() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get(); FakeApi api = prepare(host);
      NativeReveal reveal = attach(host, new AtomicInteger());
      catalog = MainActivity.obj("units", new JSONArray().put(item("D1")));
      reveal.findViewWithTag("acquisition-card-R13").performClick();
      assertNull(reveal.dossier.catalog);
      assertFalse(fixtures.labels(reveal.dossier).contains("진짜 대장님"));
      reveal.dossier.dismiss();
      JSONObject hidden = MainActivity.obj("id", "I3", "name", "???", "tier", "IMMORTAL",
          "tierName", "불멸", "known", false, "img", "unknown", "intro", "노출하면 안 되는 설명");
      int reads = api.reads.size();
      reveal.openDossier(hidden, null);
      assertEquals(reads, api.reads.size());
      assertFalse(reveal.dossier.registration.picture.visibleIdentity);
      assertFalse(fixtures.labels(reveal.dossier).contains("노출하면 안 되는 설명"));
      assertNull(reveal.dossier.findViewWithTag("acquisition-grow"));
      assertNotNull(reveal.dossier.findViewWithTag("acquisition-dossier-back"));
    }
  }

  @Test public void recipeRouteAndAllResultPagesRemainReachable() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get(); FakeApi api = prepare(host);
      NativeReveal reveal = attach(host, new AtomicInteger());
      List<String> seen = new ArrayList<>();
      for (int page = 0; page < 2; page++) {
        for (NativeReveal.RevealCard card : revealFixture.cards(reveal))
          seen.add(acquisition.registrationArtwork(card).id);
        if (page == 0) reveal.findViewWithTag("acquisition-next-page").performClick();
      }
      assertEquals(Arrays.asList("R13", "R14", "S3", "S4", "D1", "I3"), seen);
      reveal.findViewWithTag("acquisition-previous-page").performClick();
      reveal.findViewWithTag("acquisition-card-R14").performClick();
      NativeUnitDossier dossier = reveal.dossier;
      api.deferred = true;
      dossier.findViewWithTag("acquisition-craft").performClick();
      assertNull(reveal.dossier);
      assertEquals(item("R14").getString("name"), host.menus.recipeQuery);
      assertFalse(host.menus.craftOnly);
      assertTrue(api.reads.contains("native/state"));
    }
  }

  @Test public void forgeUsesSameRecordAndStaleFinishCannotNavigate() throws Exception {
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get(); prepare(host);
      host.base("조합 결과", "craftResult");
      AtomicInteger finished = new AtomicInteger();
      NativeForge forge = new NativeForge(host, new JSONArray(), item("R13"), false, "조합 완료",
          finished::incrementAndGet, new NativeAcquisitionFlow(host, "craft", Set.of()));
      host.root.addView(forge, new FrameLayout.LayoutParams(-1, -1));
      forge.showResult(false); layout(host, 915, 412, 160);
      forge.resultCard.performClick(); forge.resultCard.performClick();
      assertNotNull(forge.dossier);
      forge.finish(); assertEquals(0, finished.get());
      host.getOnBackPressedDispatcher().onBackPressed();
      assertNull(forge.dossier);
      assertNull(forge.findViewWithTag("acquisition-detail"));
      forge.finish(); forge.finish(); assertEquals(1, finished.get());
    }
    try (var controller = fixtures.start()) {
      MainActivity host = controller.get(); prepare(host);
      host.base("조합 결과", "craftResult");
      AtomicInteger finished = new AtomicInteger();
      NativeForge forge = new NativeForge(host, new JSONArray(), item("R13"), false, "조합 완료",
          finished::incrementAndGet);
      host.root.addView(forge, new FrameLayout.LayoutParams(-1, -1));
      forge.showResult(false);
      host.epoch++;
      forge.updateResult(item("R14")); forge.finish();
      assertEquals("R13", forge.result.optString("id"));
      assertEquals(0, finished.get());
    }
  }

  void capture(MainActivity host, String name, int width, int height, int density) throws Exception {
    layout(host, width, height, density);
    review.await(() -> host.registrationArt().pendingCount() == 0);
    layout(host, width, height, density);
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"), "result-dossier");
    Files.createDirectories(output);
    captures.writeScreenshot(host, output.resolve(name + ".png"), width, height);
  }

  @Test public void captureLargeCardsAndSplitRecordsAtRealScreenshotDensityAndFont130() throws Exception {
    for (int[] size : new int[][] {{915, 412, 160}, {780, 360, 160}, {2048, 838, 360}, {1280, 720, 160}})
      for (float fontScale : new float[] {1, 1.3f}) {
        RuntimeEnvironment.setQualifiers("w" + Math.round(size[0] * 160f / size[2]) + "dp-h"
            + Math.round(size[1] * 160f / size[2]) + "dp-land-" + size[2] + "dpi");
        RuntimeEnvironment.setFontScale(fontScale);
        try (var controller = fixtures.start()) {
          MainActivity host = controller.get(); FakeApi api = prepare(host);
          host.base("모집 결과", "drawResult");
          NativeReveal reveal = new NativeReveal(host, payload(), () -> {}, true,
              new NativeAcquisitionFlow(host, "draw", Set.of("o1")));
          host.root.addView(reveal, new FrameLayout.LayoutParams(-1, -1));
          String name = size[0] + "x" + size[1] + "-dpi" + size[2] + "-font" + Math.round(fontScale * 100);
          capture(host, name + "-results", size[0], size[1], size[2]);
          assertEquals(size[2], host.getResources().getDisplayMetrics().densityDpi);
          assertEquals(fontScale, host.getResources().getConfiguration().fontScale, .01f);
          HorizontalScrollView strip = reveal.findViewWithTag("acquisition-cards-scroll");
          assertFalse(strip.canScrollHorizontally(1)); assertFalse(strip.canScrollVertically(1));
          for (NativeReveal.RevealCard card : revealFixture.cards(reveal)) {
            fullyVisible(card, host);
            RegistrationCardView.Artwork art = acquisition.registrationArtwork(card);
            assertNotNull(art.bitmap);
            assertEquals(.75f, art.target.width() / art.target.height(), .01f);
            assertTrue("회수한 버튼 높이만큼 카드 확대", art.target.height() >= host.dp(215));
          }
          for (String tag : new String[] {"acquisition-previous-page", "acquisition-next-page",
              "acquisition-result-picker", "acquisition-craft", "acquisition-confirm"})
            readable(reveal.findViewWithTag(tag), host);
          assertNull(reveal.findViewWithTag("acquisition-detail"));
          View tapped = reveal.findViewWithTag("acquisition-card-R13");
          tapped.performClick();
          NativeUnitDossier firstOpen = reveal.dossier;
          tapped.performClick();
          assertSame("빠른 연속 탭도 기록 화면은 하나", firstOpen, reveal.dossier);
          capture(host, name + "-record", size[0], size[1], size[2]);
          NativeUnitDossier dossier = reveal.dossier;
          fullyVisible(dossier.registration, host);
          readable(dossier.findViewWithTag("acquisition-dossier-back"), host);
          readable(dossier.findViewWithTag("acquisition-craft"), host);
          Rect art = bounds(dossier.registration, host.root), text = bounds(dossier.reading, host.root);
          assertTrue("원본 등록증은 왼쪽, 설명은 오른쪽", art.right < text.left);
          assertNotNull(dossier.registration.picture.bitmap);
          assertEquals(.75f, dossier.registration.picture.target.width() / dossier.registration.picture.target.height(), .01f);
          assertTrue("도감 카드가 결과 카드보다 큼", dossier.registration.picture.target.height() > tapped.getHeight());
          TextView intro = dossier.findViewWithTag("acquisition-dossier-intro");
          assertEquals(item("R13").getString("intro"), intro.getText().toString());
          assertEquals(Integer.MAX_VALUE, intro.getMaxLines());
          assertNull(intro.getEllipsize());
          assertNull("인쇄 이름을 별도 표시하지 않음", revealFixture.text(dossier, item("R13").getString("name")));
          dossier.findViewWithTag("acquisition-dossier-back").performClick();
          assertSame(tapped, reveal.findViewWithTag("acquisition-card-R13"));
          assertEquals(0, reveal.resultPage); assertEquals(0, reveal.flow.selected);
          assertEquals(Collections.singletonList("native/catalog"), api.reads);
          // 같은 짧은/고밀도/확대 글꼴 조건에서도 늦은 이전 화면 응답을 거른다.
          api.deferred = true;
          tapped.performClick(); tapped.performClick();
          NativeUnitDossier stale = reveal.dossier;
          assertEquals(2, api.reads.size());
          host.epoch++;
          api.deliver();
          assertNull(stale.catalog);
          layout(host, size[0], size[1], size[2]);
          assertNull(reveal.dossier);
        }
      }
  }
}
