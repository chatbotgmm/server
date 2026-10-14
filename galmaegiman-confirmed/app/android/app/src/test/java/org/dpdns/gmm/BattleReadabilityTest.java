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
          assertTrue(card.detail.getHeight() >= 48);
          assertTrue(card.use.getHeight() >= 48);
          if (card.effect.getParent() instanceof View) {
            int effectBottom = card.effect.getBottom() + ((View) card.effect.getParent()).getTop();
            assertTrue("효과 설명 카드 아래 잘림", effectBottom <= card.getHeight());
          }
          if (card.skill.optString("id").equals("sig")) {
            assertNotNull(card.effect.getLayout());
            for (int line = 0; line < card.effect.getLineCount(); line++)
              assertEquals("피해 감소 수치 생략", 0, card.effect.getLayout().getEllipsisCount(line));
          }
        }
      }
    }
  }

  @Test
  public void disabledUtilityStillShowsCompleteEffectAndReason() {
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
      assertTrue(blocked.detail.isEnabled());
      blocked.detail.performClick();
      String text = labels(a.visibleDialog.getWindow().getDecorView());
      assertTrue(text.contains("같은 보조 기술을 연속해서 사용할 수 없어요."));
      assertTrue(text.contains("우리 팀의 체력을 회복해요."));
      a.visibleDialog.dismiss();
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
