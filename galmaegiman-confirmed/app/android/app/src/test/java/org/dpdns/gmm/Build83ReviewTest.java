package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.RectF;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 83 빌드 전 화면 검토: 홈 메뉴 닫힘/펼침, 노치·밀도·큰 글꼴, 튜토리얼이 숨은 메뉴를 펼쳐 안내. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Build83ReviewTest {
  final ReleaseHomeLayoutTest home = new ReleaseHomeLayoutTest();

  @Before public void isolate() {
    if (System.getProperty("gmm.nativeReviewOutput") == null) System.setProperty("gmm.nativeReviewOutput", "build/native-review");
    home.isolate();
  }
  @After public void reset() { home.restoreDisplay(); }

  // {가로dp, 세로dp, 글꼴%, 밀도배수, 노치 왼·위·오른·아래 dp}
  static final int[][] PROFILES = {
      {915, 412, 100, 1, 0, 0, 0, 0}, {1080, 444, 100, 1, 0, 0, 0, 0}, {1080, 444, 130, 1, 0, 0, 0, 0},
      {915, 412, 130, 1, 0, 0, 0, 0}, {1006, 411, 100, 1, 32, 8, 24, 16}, {915, 412, 100, 3, 0, 0, 0, 0},
      {1280, 720, 100, 1, 0, 0, 0, 0}};

  static String name(int[] p) {
    return p[0] + "x" + p[1] + (p[2] != 100 ? "-font" + p[2] : "") + (p[3] != 1 ? "-x" + p[3] : "")
        + (p[4] + p[5] + p[6] + p[7] > 0 ? "-notch" : "");
  }

  @Test public void homeClosedOpenAndTutorialUnfoldCaptures() throws Exception {
    for (int[] p : PROFILES) {
      String dpi = p[3] == 3 ? "xxhdpi" : "mdpi";
      RuntimeEnvironment.setQualifiers("w" + p[0] + "dp-h" + p[1] + "dp-land-" + dpi);
      RuntimeEnvironment.setFontScale(p[2] / 100f);
      try (ActivityController<ReleaseHomeLayoutTest.HomeHost> c = home.start(true)) {
        ReleaseHomeLayoutTest.HomeHost h = c.get();
        int w = p[0] * p[3], hh = p[1] * p[3];
        home.layout(h, w, hh, h.dp(p[4]), h.dp(p[5]), h.dp(p[6]), h.dp(p[7]));
        LobbyView l = home.lobby(h);
        assertFalse(l.menuOpen);
        home.review.capture(h, "home-closed-" + name(p), w, hh);
        RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
        // 큰 플레이는 오른쪽 아래, 메뉴 버튼은 그 옆. 둘 다 안전 영역 안.
        assertTrue("플레이 오른쪽 아래", l.boxes[5].right >= safe.right - h.dp(24) && l.boxes[5].bottom >= safe.bottom - h.dp(24));
        assertTrue("말풍선은 캐릭터 아래", l.tipRegion.top > l.tipHeroExclusion().bottom);
        l.setMenuOpen(true);
        home.layout(h, w, hh, h.dp(p[4]), h.dp(p[5]), h.dp(p[6]), h.dp(p[7]));
        assertTrue(safe.contains(l.menuPanel));
        home.review.capture(h, "home-open-" + name(p), w, hh);
        // 바깥 탭 대신 뒤로 가기로 닫힘(바깥 탭은 ReleaseHomeLayoutTest가 검사).
        h.getOnBackPressedDispatcher().onBackPressed();
        assertFalse("뒤로 가기로 닫힘", l.menuOpen);
        // 튜토리얼이 숨은 「출석·미션」을 찾으면 메뉴가 자동으로 펼쳐지고 안내 카드가 붙는다.
        h.guide.enabled = true;
        View target = h.guide.find("daily", "출석체크");
        assertNotNull(target);
        assertTrue("튜토리얼 대상 찾기로 메뉴 자동 펼침", l.menuOpen);
        home.layout(h, w, hh, h.dp(p[4]), h.dp(p[5]), h.dp(p[6]), h.dp(p[7]));
        NativeTutorialController g = NativeTutorialController.get(h);
        g.show("review-home-daily", target, "출석·미션", "메뉴를 펼쳐 출석 보상을 받아요.", null, () -> {}, false);
        home.review.capture(h, "home-tutorial-daily-" + name(p), w, hh);
        g.removeOverlay();
        h.guide.enabled = false;
      }
    }
  }
}
