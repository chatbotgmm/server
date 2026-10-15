package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 좁은 가로 화면에서 기술을 모두 볼 수 있고 사용 불가 기술의 설명도 읽을 수 있다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BattleReadabilityTest {
  @Before
  public void isolatedAccount() {
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("native_account", 0).edit().clear().commit();
    app.getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .commit();
  }

  JSONObject skill(String id, String name, String reason) {
    return MainActivity.obj(
        "id",
        id,
        "name",
        name,
        "power",
        40,
        "hits",
        2,
        "desc",
        id.equals("sig")
            ? "위력 40으로 2번, 3턴 동안 받는 피해 -45%"
            : "위력 40으로 2번, 맞은 상대의 방어를 낮추고 우리 팀의 체력을 회복해요.",
        "reason",
        reason == null ? JSONObject.NULL : reason,
        "preview",
        MainActivity.obj("attrName", "바다", "attr", "WATER", "category", "mag", "multiplier", 1.5));
  }

  JSONObject battle() {
    JSONArray skills =
        new JSONArray()
            .put(skill("basic", "기본 공격", null))
            .put(skill("sig", "서광의 방패", null))
            .put(skill("role-DEFENSE", "보호 효과", "이미 적용되었습니다. 같은 보조 기술을 연속해서 사용할 수 없어요."))
            .put(skill("learn-1", "일반 기술", null))
            .put(skill("combo-sea", "함께 밀어내기", null));
    JSONObject f =
        MainActivity.obj(
            "key",
            "o1",
            "id",
            "D1",
            "name",
            "갈매미",
            "level",
            15,
            "hp",
            75,
            "maxHp",
            100,
            "attr",
            "바다",
            "attrCode",
            "WATER",
            "role",
            "DEFENSE",
            "status",
            new JSONObject(),
            "base",
            MainActivity.obj("patk", 40, "pdef", 50, "matk", 60, "mdef", 70, "spd", 80),
            "skills",
            skills);
    JSONArray teams = new JSONArray();
    for (int i = 0; i < 2; i++)
      teams.put(
          MainActivity.obj(
              "active",
              0,
              "fighters",
              new JSONArray().put(f),
              "augments",
              new JSONArray(),
              "legal",
              new JSONArray()));
    return MainActivity.obj(
        "session",
        "offline-review",
        "phase",
        "action",
        "mode",
        "practice",
        "turn",
        1,
        "revision",
        0,
        "teams",
        teams,
        "log",
        new JSONArray().put("첫 기록"),
        "displayEvents",
        new JSONArray());
  }

  BattleView mount(MainActivity a, int w, int h, boolean inset) {
    a.motionEnabled = false;
    a.state = MainActivity.obj("units", new JSONArray());
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, null);
    a.root.setPadding(inset ? 36 : 0, inset ? 10 : 0, inset ? 8 : 0, inset ? 10 : 0);
    a.root.removeAllViews();
    BattleView v = new BattleView(a, battle());
    a.root.addView(v, new FrameLayout.LayoutParams(-1, -1));
    v.pane = "skills";
    v.render();
    measure(a.root, w, h);
    return v;
  }

  void measure(View v, int w, int h) {
    v.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, w, h);
  }

  ArrayList<SkillCardView> cards(View v) {
    ArrayList<SkillCardView> result = new ArrayList<>();
    if (v instanceof SkillCardView) result.add((SkillCardView) v);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) result.addAll(cards(g.getChildAt(i)));
    }
    return result;
  }

  String labels(View v) {
    String result = v instanceof TextView ? ((TextView) v).getText().toString() : "";
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) result += "\n" + labels(g.getChildAt(i));
    }
    return result;
  }

  @Test
  public void fourSkillsAndComboRemainReadableInsideZflipSafeArea() {
    for (boolean inset : new boolean[] {false, true}) {
      try (ActivityController<MainActivity> c =
          Robolectric.buildActivity(MainActivity.class).setup().visible()) {
        BattleView v = mount(c.get(), inset ? 1006 : 915, inset ? 411 : 412, inset);
        assertEquals(View.GONE, v.logBox.getVisibility());
        assertEquals(5, cards(v.controls).size());
        for (SkillCardView card : cards(v.controls)) {
          int y = card.getTop();
          View p = (View) card.getParent();
          while (p != v.menu) {
            y += p.getTop() - p.getScrollY();
            p = (View) p.getParent();
          }
          assertTrue("긴 효과는 명령 패널 스크롤로 접근 가능", y + card.getHeight() <= v.controls.getHeight());
          assertTrue("제목에 카드 전폭 배정", card.title.getWidth() >= card.getWidth() - 20);
          assertNotNull(card.title.getLayout());
          for (int line = 0; line < card.title.getLineCount(); line++)
            assertEquals("기술 이름 생략", 0, card.title.getLayout().getEllipsisCount(line));
          assertTrue(card.use.getHeight() >= 48);
          assertFalse("상세창을 여는 롱프레스 제거", card.use.isLongClickable());
          assertFalse(labels(card).contains("상세"));
          if (card.effect.getParent() instanceof View) {
            int effectBottom = card.effect.getBottom() + ((View) card.effect.getParent()).getTop();
            assertTrue("효과 설명 카드 아래 잘림", effectBottom <= card.getHeight());
          }
          assertEquals("효과는 최대 2줄 요약", 2, card.effect.getMaxLines());
          assertTrue(
              "전체 설명은 접근성에 유지",
              card.use.getContentDescription().toString().contains(card.skill.optString("desc")));
        }
      }
    }
  }

  @Test
  public void disabledUtilityShowsShortReasonAndRetainsCompleteAccessibleDescription() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity a = c.get();
      BattleView v = mount(a, 915, 412, false);
      SkillCardView blocked =
          cards(v.controls).stream()
              .filter(x -> x.skill.optString("id").equals("role-DEFENSE"))
              .findFirst()
              .orElseThrow();
      assertFalse(blocked.use.isEnabled());
      String text = labels(blocked);
      assertTrue(text.contains("이미 적용됨"));
      assertTrue(text.contains("우리 팀 체력 회복"));
      assertTrue(
          blocked.use.getContentDescription().toString().contains("같은 보조 기술을 연속해서 사용할 수 없어요."));
      assertFalse(blocked.use.performLongClick());
      assertNull(a.visibleDialog);
    }
  }

  @Test
  public void powerAppearsOnceAndMultiHitCategoryAreVisible() {
    JSONObject sk = skill("basic", "기본 공격", null);
    assertEquals("맞은 상대의 방어를 낮추고 우리 팀의 체력을 회복해요.", SkillCardView.effect(sk));
    assertTrue(SkillCardView.meta(sk).contains("위력40×2"));
    assertTrue(SkillCardView.meta(sk).contains("마법"));
    assertTrue(SkillCardView.meta(sk).contains("▲유리"));
    assertEquals("", SkillCardView.effect(skill("sub-GOLD", "금빛 일격", null)));
  }

  @Test
  public void currentConditionalPowerAndCompleteEffectRemainVisibleWithoutDetailControls()
      throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity a = c.get();
      BattleView v = mount(a, 915, 412, false);
      JSONObject sk = skill("sub-FIRE", "홍련의 심판", null);
      sk.put("basePower", 40);
      sk.put("power", 44);
      sk.put("recipeValue", 16);
      sk.put("recipeBonusPct", 10);
      sk.put("bonus", MainActivity.obj("when", "burned", "pct", 30));
      sk.put("desc", "위력 44로 2번, 화상 상태의 상대에게 위력 +30%");
      sk.optJSONObject("preview")
          .put("basePower", 44)
          .put("power", 57)
          .put("bonusActive", true)
          .put("bonusPct", 30);
      SkillCardView card = new SkillCardView(v, sk, false);
      card.measure(
          View.MeasureSpec.makeMeasureSpec(230, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
      card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
      assertTrue(card.power.getText().toString().contains("위력 57×2"));
      assertTrue(SkillCardView.meta(sk).contains("위력57×2"));
      assertTrue(card.use.getContentDescription().toString().contains("조건 발동"));
      assertFalse(card.effect.getText().toString().contains("조건 발동"));
      assertTrue(card.effect.getText().toString().contains("화상 적 +30%"));
      assertNotNull(card.effect.getLayout());
      assertEquals(2, card.effect.getMaxLines());
      assertTrue(card.effect.getBottom() <= ((View) card.effect.getParent()).getHeight());
      assertTrue(card.use.getMeasuredHeight() >= 48);
      assertEquals(1, directButtons(card));
      assertFalse(card.use.isLongClickable());
      assertFalse(card.use.performLongClick());
      assertNull(a.visibleDialog);
      assertFalse(labels(card).contains("조합 가치"));
    }
  }

  @Test
  public void invalidOrMissingCurrentPreviewFallsBackToSkillPower() throws Exception {
    JSONObject sk = skill("sig", "서광의 방패", null);
    assertEquals(40, SkillCardView.currentPower(sk));
    for (Object invalid : new Object[] {JSONObject.NULL, 0, -20, "NaN", "잘못된 수치"}) {
      sk.optJSONObject("preview").put("power", invalid);
      assertEquals(40, SkillCardView.currentPower(sk));
      assertTrue(SkillCardView.powerMeta(sk).contains("위력 40×2"));
    }
    sk.remove("preview");
    assertEquals(40, SkillCardView.currentPower(sk));
    sk.put("power", 0);
    assertEquals("보조", SkillCardView.powerMeta(sk));
    assertFalse(SkillCardView.bonusActive(sk));
  }

  @Test
  public void enhancedSubSkillsRetainEffectTextWhileLegacySimpleSubStaysConcise() throws Exception {
    for (String key : new String[] {"bonus", "pierce", "drain", "effects", "crit", "priority"}) {
      JSONObject sk = skill("sub-GOLD", "금익의 심판", null);
      sk.put("desc", "위력 40, 상대 방어 20% 무시");
      Object value =
          key.equals("bonus")
              ? MainActivity.obj("when", "opening", "pct", 30)
              : key.equals("effects")
                  ? new JSONArray().put(MainActivity.obj("kind", "status", "status", "burn"))
                  : key.equals("crit") || key.equals("priority") ? true : 20;
      sk.put(key, value);
      assertEquals("신규 속성 효과 누락: " + key, "상대 방어 20% 무시", SkillCardView.effect(sk));
    }
    JSONObject old = skill("sub-GOLD", "금빛 일격", null);
    old.put("effects", new JSONArray());
    old.put("pierce", 0);
    assertEquals("", SkillCardView.effect(old));
    JSONObject priority = skill("sub-GOLD", "금강 일섬", null);
    priority.put("priority", true);
    priority.put("desc", "무조건 먼저, 위력 40으로 2번, 반드시 급소");
    assertEquals("무조건 먼저, 반드시 급소", SkillCardView.effect(priority));
  }

  @Test
  public void recipeNotesRemainInCollapsedUnitInfoAndInactiveConditionDoesNotClaimActivation()
      throws Exception {
    assertEquals("", NativeLibrary.recipeValueNote(new JSONObject()));
    assertEquals("", NativeLibrary.recipeValueNote(MainActivity.obj("recipeValue", 16)));
    assertEquals(
        "조합 가치 · 흔함 16마리\n공격기 위력 +10%",
        NativeLibrary.recipeValueNote(MainActivity.obj("recipeValue", 16, "recipeBonusPct", 10)));
    assertEquals(
        "조합 가치 · 흔함 16마리\n공격기 위력 +2.5%",
        NativeLibrary.recipeValueNote(MainActivity.obj("recipeValue", 16, "recipeBonusPct", 2.5)));
    assertEquals(
        "조합 가치 · 흔함 1마리\n공격기 위력 +0%",
        NativeLibrary.recipeValueNote(MainActivity.obj("recipeValue", 1, "recipeBonusPct", 0)));
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      BattleView v = mount(c.get(), 915, 412, false);
      LinearLayout details = new LinearLayout(c.get());
      c.get()
          .menus
          .library
          .unitDetails(details, MainActivity.obj("recipeValue", 16, "recipeBonusPct", 10));
      TextView recipe = details.findViewWithTag("unit-recipe-value");
      assertNotNull(recipe);
      View extras = details.findViewWithTag("unit-extra-details");
      assertEquals(View.GONE, extras.getVisibility());
      details.findViewWithTag("unit-details-toggle").performClick();
      assertEquals(View.VISIBLE, extras.getVisibility());
      assertTrue(recipe.getText().toString().contains("흔함 16마리"));
      LinearLayout legacy = new LinearLayout(c.get());
      c.get().menus.library.unitDetails(legacy, new JSONObject());
      assertNull(legacy.findViewWithTag("unit-recipe-value"));
      JSONObject sk = skill("sub-SEA", "해일 격돌", null);
      sk.put("bonus", MainActivity.obj("when", "slowed", "pct", 30));
      sk.optJSONObject("preview").put("power", 40).put("bonusActive", false);
      assertFalse(SkillCardView.bonusActive(sk));
      assertFalse(SkillCardView.meta(sk).contains("조건 발동"));
      sk.put("desc", "위력 40, 속도가 감소한 상대에게 위력 +30%");
      SkillCardView card = new SkillCardView(v, sk, false);
      assertTrue(card.effect.getText().toString().contains("느려진 적 +30%"));
      assertFalse(card.effect.getText().toString().contains("조건 발동"));
    }
  }

  @Test
  public void serverSummaryWinsAndEmptySummaryHidesTheEffectRow() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      BattleView v = mount(c.get(), 915, 412, false);
      JSONObject sk = skill("basic-GOLD", "금빛 관통", null);
      sk.put("summary", "방어 25% 관통");
      SkillCardView card = new SkillCardView(v, sk, false);
      assertEquals("방어 25% 관통", card.effect.getText().toString());
      assertEquals(2, card.effect.getMaxLines());
      assertFalse(card.power.getText().toString().contains("×1.5"));
      assertTrue(card.power.getText().toString().contains("바다"));
      assertTrue(card.use.getContentDescription().toString().contains("×1.5"));
      sk.put("summary", "");
      assertEquals(View.GONE, new SkillCardView(v, sk, false).effect.getVisibility());
    }
  }

  int directButtons(View v) {
    int result = v instanceof Button ? 1 : 0;
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) result += directButtons(group.getChildAt(i));
    }
    return result;
  }

  @Test
  public void enemyInfoUnlocksAfterFirstTurnAndHistoryPreservesOlderRows() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity a = c.get();
      BattleView v = mount(a, 915, 412, false);
      assertFalse(v.foeInfo.isEnabled());
      v.battle.put("turn", 2);
      v.render();
      assertTrue(v.foeInfo.isEnabled());
      v.foeInfo.performClick();
      assertTrue(labels(a.visibleDialog.getWindow().getDecorView()).contains("물리 공격 40"));
      assertTrue(labels(a.visibleDialog.getWindow().getDecorView()).contains("속도가 같아요."));
      a.visibleDialog.dismiss();
      JSONArray history = new JSONArray();
      for (int i = 0; i < 60; i++) history.put("전투 기록 " + i);
      v.battle.put("history", history);
      v.history.performClick();
      assertTrue(labels(a.visibleDialog.getWindow().getDecorView()).contains("전투 기록 0"));
      assertTrue(labels(a.visibleDialog.getWindow().getDecorView()).contains("전투 기록 59"));
      a.visibleDialog.dismiss();
    }
  }

  @Test
  public void currentAugmentMultipliersOverrideBaseChart() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity a = c.get();
      BattleView v = mount(a, 915, 412, false);
      JSONObject mine = v.active(0), foe = v.active(1);
      mine.put(
          "matchup",
          MainActivity.obj(
              "attack",
              MainActivity.obj("multiplier", .67),
              "defense",
              MainActivity.obj("multiplier", 1.9)));
      assertEquals("불리", NativeBattleSheets.effectiveness(a, mine, foe));
      assertEquals("불리", NativeBattleSheets.defense(a, foe, mine));
      assertEquals("현재 상성", NativeBattleSheets.matchupLabel(mine));
      mine.put(
          "matchup",
          MainActivity.obj(
              "attack",
              MainActivity.obj("multiplier", 1),
              "defense",
              MainActivity.obj("multiplier", 1)));
      assertEquals("보통", NativeBattleSheets.effectiveness(a, mine, foe));
      assertEquals("보통", NativeBattleSheets.defense(a, foe, mine));
    }
  }
}
