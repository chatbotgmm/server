package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 규칙·엔진에서 만든 10속성 스냅샷을 원본 글꼴과 Android Skia로 검토한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeElementalReviewTest {
  static final String[] ATTRIBUTES = {
    "STONE", "GOLD", "FIRE", "ICE", "BLOSSOM", "SEA", "SHADOW", "STAR", "THREAD", "GRAVITY"
  };
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final BattleReadabilityTest cards = new BattleReadabilityTest();
  Path fixtureDirectory;

  @Before
  public void isolateAndRequireRealEngineFixtures() {
    String folder = System.getProperty("gmm.elementalFixtureDir", "");
    assumeTrue("엔진 생성 fixture 폴더가 지정된 경우 실행", !folder.isEmpty());
    fixtureDirectory = Path.of(folder);
    assumeTrue("엔진 생성 fixture 폴더 존재", Files.isDirectory(fixtureDirectory));
    assumeTrue("실제 캡처 출력 폴더 지정", !System.getProperty("gmm.nativeReviewOutput", "").isEmpty());
    review.scheduledFramesUseThePausedLooperClock();
    cards.isolatedAccount();
  }

  @After
  public void restoreFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject readFixture(String scenario) throws Exception {
    Path file = fixtureDirectory.resolve(scenario + ".json");
    assertTrue("필수 엔진 fixture: " + file, Files.isRegularFile(file));
    JSONObject fixture =
        new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    assertNotNull(fixture.optJSONObject("battle"));
    assertNotNull(fixture.optJSONObject("unit"));
    return fixture;
  }

  static final class NoMutationApi extends NativeApi {
    int calls;

    NoMutationApi(MainActivity host) {
      super(host);
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      calls++;
      throw new AssertionError("화면 검토에서 API 요청 금지: " + path);
    }
  }

  BattleView mount(MainActivity host, JSONObject fixture, int[] size) throws Exception {
    host.motionEnabled = false;
    host.guide.enabled = false;
    host.api = new NoMutationApi(host);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(size[0] == 1006 ? 36 : 8, 8, 8, 8);
    host.state =
        MainActivity.obj(
            "me", MainActivity.obj("nickname", "속성 기술 검토"),
            "units", new JSONArray().put(fixture.getJSONObject("unit")));
    review.viewport(host, size[0], size[1]);
    host.battle = fixture.getJSONObject("battle");
    host.root.removeAllViews();
    BattleView battle = new BattleView(host, host.battle);
    host.root.addView(battle, new FrameLayout.LayoutParams(-1, -1));
    battle.pane = "skills";
    battle.render();
    review.viewport(host, size[0], size[1]);
    return battle;
  }

  boolean effect(JSONObject skill, String kind, String field, String value) {
    JSONArray effects = skill.optJSONArray("effects");
    if (effects == null) return false;
    for (int i = 0; i < effects.length(); i++) {
      JSONObject e = effects.optJSONObject(i);
      if (e != null && kind.equals(e.optString("kind")) && value.equals(e.optString(field)))
        return true;
    }
    return false;
  }

  boolean trait(JSONObject skill, String attribute) {
    if (!attribute.equals(skill.optString("attr"))) return false;
    JSONObject bonus = skill.optJSONObject("bonus");
    return switch (attribute) {
      case "STONE" -> effect(skill, "buff", "stat", "def");
      case "GOLD" -> skill.optInt("pierce") > 0;
      case "FIRE" -> bonus != null && bonus.optString("when").equals("burned");
      case "ICE" -> effect(skill, "buff", "stat", "spd");
      case "BLOSSOM" -> skill.optInt("drain") > 0;
      case "SEA" -> effect(skill, "heal", "target", "bench");
      case "SHADOW" -> bonus != null && bonus.optString("when").equals("finisher");
      case "STAR" -> skill.optInt("hits") > 1;
      case "THREAD" -> effect(skill, "status", "status", "bind");
      case "GRAVITY" -> bonus != null && bonus.optString("when").equals("slower");
      default -> false;
    };
  }

  SkillCardView representative(BattleView battle, String attribute) {
    List<SkillCardView> all = cards.cards(battle.controls);
    if (attribute.equals("FIRE") || attribute.equals("GRAVITY"))
      for (SkillCardView card : all)
        if (card.skill.optString("id").equals("role-ATTACK") && trait(card.skill, attribute))
          return card;
    for (SkillCardView card : all) if (trait(card.skill, attribute)) return card;
    throw new AssertionError(attribute + " 속성의 대표 전략 효과가 엔진 스냅샷에 없음");
  }

  void compactCardGeometry(MainActivity host, BattleView battle) {
    List<SkillCardView> all = cards.cards(battle.controls);
    assertTrue("기본·역할·보조·고유기 카드 존재", all.size() >= 4);
    for (SkillCardView card : all) {
      assertTrue("기술 사용 높이48dp", card.use.getHeight() >= host.dp(48));
      assertEquals("카드 조작은 사용 버튼 하나", 1, cards.directButtons(card));
      assertFalse("별도 상세 롱프레스 없음", card.use.isLongClickable());
      assertFalse(card.use.performLongClick());
      assertNull(host.visibleDialog);
      geometry.readable(card.title);
      geometry.readable(card.power);
      assertTrue("제목 전폭 확보", card.title.getWidth() >= card.getWidth() - host.dp(20));
      assertEquals("효과는 두 줄 요약", 2, card.effect.getMaxLines());
      if (card.effect.isShown()) {
        assertNotNull(card.effect.getLayout());
        assertTrue("실제 효과 줄 수 제한", card.effect.getLineCount() <= 2);
        assertTrue(
            "효과가 카드 하단에 잘리지 않음",
            card.effect.getBottom() <= ((View) card.effect.getParent()).getHeight());
      }
      JSONObject skill = card.skill;
      String accessible = card.use.getContentDescription().toString();
      assertTrue("전체 설명은 접근성에 보존", accessible.contains(skill.optString("desc")));
      assertTrue(
          "현재 위력 또는 보조 기술을 정확하게 표시",
          card.power
              .getText()
              .toString()
              .contains(
                  SkillCardView.currentPower(skill) > 0
                      ? "위력 " + SkillCardView.currentPower(skill)
                      : "보조"));
      assertFalse("정확 배율은 전투 카드에서 생략", card.power.getText().toString().contains("×1."));
      assertFalse("중립 상성 문구 생략", card.power.getText().toString().contains("보통"));
      assertFalse("역할 분류행 제거", cards.labels(card).contains("일반기 ·"));
      if (skill.has("summary"))
        assertEquals(
            "서버와 같은 핵심 요약", skill.optString("summary"), SkillCardView.conciseEffect(skill));
      android.graphics.Rect inControls = geometry.bounds(card, battle.controls);
      assertTrue(
          "카드 전체 가로 영역이 명령 패널 안",
          inControls.left >= 0 && inControls.right <= battle.controls.getWidth());
      assertTrue("마지막 효과까지 스크롤 가능", inControls.bottom <= battle.controls.getHeight());
    }
    assertEquals(
        "명령 메뉴 전체가 안전 영역 안",
        geometry.bounds(battle.menu, host.root),
        geometry.visibleIntersection(battle.menu, host.root));
    for (Button tool : new Button[] {battle.foeInfo, battle.history, battle.augmentInfo})
      geometry.fixedButton(host, host.root, tool);
  }

  int scrollTo(MainActivity host, BattleView battle, int[] size, boolean bottom) {
    battle.menu.setSmoothScrollingEnabled(false);
    review.viewport(host, size[0], size[1]);
    battle.menu.fullScroll(bottom ? View.FOCUS_DOWN : View.FOCUS_UP);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    review.viewport(host, size[0], size[1]);
    int max =
        Math.max(
            0,
            battle.controls.getHeight()
                + battle.menu.getPaddingTop()
                + battle.menu.getPaddingBottom()
                - battle.menu.getHeight());
    assertEquals("실제 메뉴 스크롤 끝까지 도달", bottom ? max : 0, battle.menu.getScrollY());
    return max;
  }

  void captureScenario(String scenario, int[] size, float fontScale) throws Exception {
    RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
    RuntimeEnvironment.setFontScale(fontScale);
    JSONObject fixture = readFixture(scenario);
    String before = fixture.toString();
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity host = controller.get();
      BattleView battle = mount(host, fixture, size);
      String attribute = scenario.replace("-active", "");
      SkillCardView representative = representative(battle, attribute);
      assertFalse(
          "전략 핵심 요약 존재",
          SkillCardView.conciseEffect(representative.skill).isEmpty()
              && representative.skill.optInt("hits") <= 1);
      if (scenario.endsWith("-active"))
        assertTrue("조건 발동은 실제 엔진 판정", SkillCardView.bonusActive(representative.skill));
      if (scenario.equals("FIRE") || scenario.equals("GRAVITY"))
        assertFalse("기본 스냅샷에서는 발동 전", SkillCardView.bonusActive(representative.skill));
      String prefix = "element74-" + size[0] + "-" + scenario;
      scrollTo(host, battle, size, false);
      review.capture(host, prefix + "-skills-top", size[0], size[1]);
      compactCardGeometry(host, battle);
      int range = scrollTo(host, battle, size, true);
      review.capture(host, prefix + "-skills-bottom", size[0], size[1]);
      compactCardGeometry(host, battle);
      JSONObject unit = fixture.getJSONObject("unit");
      assertFalse("조합 가치 안내 제공", NativeLibrary.recipeValueNote(unit).isEmpty());
      assertFalse("조합 명세는 전투 카드에 넣지 않음", cards.labels(battle.controls).contains("조합 가치"));
      JSONObject audit =
          MainActivity.obj(
              "scenario",
              scenario,
              "width",
              size[0],
              "height",
              size[1],
              "fontScale",
              fontScale,
              "representativeId",
              representative.skill.optString("id"),
              "summary",
              SkillCardView.conciseEffect(representative.skill),
              "currentPower",
              SkillCardView.currentPower(representative.skill),
              "bonusActive",
              SkillCardView.bonusActive(representative.skill),
              "scrollRange",
              range,
              "scrollBottom",
              battle.menu.getScrollY(),
              "recipeValue",
              unit.optInt("recipeValue"),
              "recipeBonusPct",
              unit.optInt("recipeBonusPct"),
              "apiCalls",
              ((NoMutationApi) host.api).calls);
      Path out = Path.of(System.getProperty("gmm.nativeReviewOutput"));
      Files.write(
          out.resolve(prefix + "-audit.json"), audit.toString(2).getBytes(StandardCharsets.UTF_8));
      assertEquals("검토에서 서버 요청 없음", 0, ((NoMutationApi) host.api).calls);
      assertEquals("스냅샷의 전투·부대 상태 불변", before, fixture.toString());
    }
  }

  @Test
  public void tenAttributesAndActiveConditionsUseThreeCompactRowsOnPhone() throws Exception {
    for (String attribute : ATTRIBUTES) captureScenario(attribute, new int[] {915, 412}, 1);
    for (String active : new String[] {"FIRE-active", "GRAVITY-active"})
      captureScenario(active, new int[] {915, 412}, 1);
  }

  @Test
  public void keyEffectsRemainReachableOnSmallPhoneZflipAndLargeFontTablet() throws Exception {
    for (int[] size : new int[][] {{780, 360}, {1006, 411}, {1280, 720}})
      for (String scenario : new String[] {"FIRE-active", "GRAVITY-active", "BLOSSOM", "GOLD"})
        captureScenario(scenario, size, size[0] == 1006 ? 1 : 1.3f);
  }
}
