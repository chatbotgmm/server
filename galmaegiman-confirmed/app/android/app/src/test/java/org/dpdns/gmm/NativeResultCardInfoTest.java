package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 모집 결과 카드를 눌러 뒤집으면 속성·소개 면이 나오고 다시 누르면 앞면으로 돌아온다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeResultCardInfoTest {
  // 결과 기록 검증과 같은 실제 등록증·가짜 API·화면 배치를 그대로 사용한다.
  final NativeResultDossierTest d = new NativeResultDossierTest();

  @Before public void setup() throws Exception { d.setup(); }

  @After public void restore() { d.restore(); }

  @Test public void tapTurnsCardToAttributeAndIntroAndTapAgainReturnsFront() throws Exception {
    try (var controller = d.fixtures.start()) {
      MainActivity host = controller.get(); NativeResultDossierTest.FakeApi api = d.prepare(host);
      // 서버는 전투 종에만 속성을 싣는다. 뒷면 속성 표시를 확인하려고 이 응답에만 넣는다.
      NativeUnitDossier.entry(d.catalog, "R14").put("attr", "SEA").put("attrName", "바다");
      NativeReveal reveal = d.attach(host, new AtomicInteger());
      String original = reveal.result.toString();
      NativeReveal.RevealCard card = reveal.findViewWithTag("acquisition-card-R14");
      card.performClick();
      assertNull("새 창 없이 그 자리에서 뒤집힌다", reveal.dossier);
      assertTrue(card.infoShown);
      assertEquals(View.VISIBLE, card.info.getVisibility());
      assertNotEquals(View.VISIBLE, card.front.getVisibility());
      assertEquals(Collections.singletonList("native/catalog"), api.reads);
      JSONObject expected = NativeUnitDossier.entry(d.catalog, "R14");
      // 도감 설명은 뒷면에 싣지 않는다(도감에서 직접 본다).
      String shown = d.fixtures.labels(card.info);
      assertFalse("한 줄 소개 없음", shown.contains(expected.getString("tagline")));
      assertFalse("소개 없음", shown.contains(expected.getString("intro").substring(0, 12)));
      assertNull(card.findViewWithTag("acquisition-card-info-intro"));
      assertNull(card.findViewWithTag("acquisition-card-info-tagline"));
      assertEquals(expected.getString("name"),
          ((TextView) card.findViewWithTag("acquisition-card-info-name")).getText().toString().replace('\n', ' '));
      assertEquals(expected.getString("tierName"),
          ((TextView) card.findViewWithTag("acquisition-card-info-tier")).getText().toString().replace('\n', ' '));
      assertEquals("바다 속성",
          ((TextView) card.findViewWithTag("acquisition-card-info-attribute")).getText().toString());
      assertTrue(card.getContentDescription().toString().contains("다시 눌러 앞면"));
      card.performClick();
      assertFalse("다시 누르면 앞면", card.infoShown);
      assertEquals(View.VISIBLE, card.front.getVisibility());
      assertEquals(View.GONE, card.info.getVisibility());
      assertNull(reveal.dossier);
      card.performClick();
      assertTrue(card.infoShown);
      assertEquals("소개는 한 번만 읽는다", 1, api.reads.size());
      card.info.findViewWithTag("acquisition-card-info-reading").performClick();
      assertEquals(1, api.reads.size());
      assertEquals(original, reveal.result.toString());
    }
  }

  @Test public void animatedTurnIgnoresTapsMidwayAndEndsOnRequestedFace() throws Exception {
    try (var controller = d.fixtures.start()) {
      MainActivity host = controller.get(); d.prepare(host);
      NativeReveal reveal = d.attach(host, new AtomicInteger());
      host.motionEnabled = true;
      host.motionResumed = true;
      NativeReveal.RevealCard card = reveal.findViewWithTag("acquisition-card-R14");
      org.junit.Assume.assumeTrue("애니메이션이 켜진 환경에서만 의미가 있다", reveal.motion() && reveal.active());
      card.performClick();
      android.animation.ValueAnimator turning = card.turning;
      assertNotNull("뒤집는 중", turning);
      card.performClick();
      assertTrue("뒤집는 중 탭은 무시", card.infoShown);
      assertSame(turning, card.turning);
      // 기존 결과 화면 테스트처럼 실제 애니메이터 시간을 직접 진행한다.
      turning.setCurrentPlayTime(80);
      assertEquals("앞쪽 절반은 앞면", View.VISIBLE, card.front.getVisibility());
      assertTrue("가로로 접히는 중", card.getScaleX() < 1f);
      turning.setCurrentPlayTime(240);
      assertEquals("뒤쪽 절반은 소개 면", View.VISIBLE, card.info.getVisibility());
      assertNotEquals(View.VISIBLE, card.front.getVisibility());
      turning.end();
      assertNull(card.turning);
      assertEquals(View.VISIBLE, card.info.getVisibility());
      assertNotEquals(View.VISIBLE, card.front.getVisibility());
      assertEquals(1f, card.getScaleX(), .001f);
      card.performClick();
      assertNotNull(card.turning);
      card.turning.end();
      assertFalse(card.infoShown);
      assertEquals(View.VISIBLE, card.front.getVisibility());
      assertEquals(View.GONE, card.info.getVisibility());
      assertEquals(1f, card.getScaleX(), .001f);
    }
  }

  @Test public void cardIntroFailureShowsRetryAndLateReplyIsDiscarded() throws Exception {
    try (var controller = d.fixtures.start()) {
      MainActivity host = controller.get(); NativeResultDossierTest.FakeApi api = d.prepare(host); api.deferred = true;
      NativeReveal reveal = d.attach(host, new AtomicInteger());
      NativeReveal.RevealCard card = reveal.findViewWithTag("acquisition-card-R14");
      card.performClick();
      assertNotNull(card.findViewWithTag("acquisition-card-info-status"));
      host.epoch++;
      api.deliver();
      assertNull("다른 화면이 된 뒤 도착한 응답은 반영하지 않음", reveal.infoCatalog);
    }
    try (var controller = d.fixtures.start()) {
      MainActivity host = controller.get(); d.prepare(host);
      host.api.close();
      NativeApi failing = new NativeApi(host) {
        @Override String token() { return "result-dossier-review"; }
        @Override void call(String path, JSONObject body, Result done) { done.done(null, "연결 실패"); }
      };
      host.api = failing;
      NativeReveal reveal = d.attach(host, new AtomicInteger());
      NativeReveal.RevealCard card = reveal.findViewWithTag("acquisition-card-R14");
      card.performClick();
      assertNotNull(card.findViewWithTag("acquisition-card-info-error"));
      assertNotNull(card.findViewWithTag("acquisition-card-info-retry"));
      assertNull(card.findViewWithTag("acquisition-card-info-intro"));
    }
  }
}
