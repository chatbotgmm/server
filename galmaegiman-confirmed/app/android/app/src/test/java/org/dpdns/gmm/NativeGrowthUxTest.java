package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 성장 행동을 첫 시야에서 찾고 편성 밖 갈매미도 바로 회복할 수 있는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeGrowthUxTest {
  @Before
  public void isolate() {
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("native_account", 0).edit().clear().commit();
    app.getSharedPreferences("native_audio", 0)
        .edit()
        .putInt("music", 0)
        .putInt("effects", 0)
        .commit();
  }

  @After
  public void resetFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  ActivityController<MainActivity> start() {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    MainActivity host = controller.get();
    host.motionEnabled = false;
    host.guide.enabled = false;
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    return controller;
  }

  JSONObject unit(String key) {
    JSONObject offer = MainActivity.obj("id", "sig", "name", "새 전용 기술", "desc", "받는 피해 감소");
    return MainActivity.obj(
        "key",
        key,
        "name",
        "태초의 갈매미맨",
        "image",
        "",
        "level",
        12,
        "stage",
        0,
        "hp",
        10,
        "maxHp",
        100,
        "xp",
        30,
        "xpNext",
        100,
        "attr",
        "돌",
        "role",
        "DEFENSE",
        "learnLeft",
        2,
        "learn",
        new JSONArray().put(offer),
        "promo",
        new JSONArray().put(offer),
        "evolve",
        new JSONArray().put(MainActivity.obj("id", "award", "name", "수비 표창")),
        "stats",
        MainActivity.obj("patk", 70, "pdef", 80, "matk", 40, "mdef", 60, "spd", 30),
        "skills",
        new JSONArray(),
        "evoAugs",
        new JSONArray());
  }

  View tag(View view, String wanted) {
    if (wanted.equals(view.getTag())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) {
        View found = tag(parent.getChildAt(i), wanted);
        if (found != null) return found;
      }
    }
    return null;
  }

  void measure(View root, int width, int height) {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
  }

  void assertFullyVisible(View view) {
    assertNotNull(view);
    Rect visible = new Rect();
    assertTrue(view.getTag() + " 숨겨짐", view.getGlobalVisibleRect(visible));
    assertEquals(view.getTag() + " 가로 잘림", view.getWidth(), visible.width());
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    assertEquals(
        view.getTag() + " 세로 잘림 · 위치 " + location[0] + "," + location[1] + " · 가시영역 " + visible,
        view.getHeight(),
        visible.height());
  }

  @Test
  public void growthAndHealingActionsStayInFirstViewportWithLargeFontsAndSafeInsets() {
    for (String size : new String[] {"w915dp-h412dp-land-mdpi", "w1280dp-h720dp-land-mdpi"}) {
      RuntimeEnvironment.setQualifiers(size);
      RuntimeEnvironment.setFontScale(1.3f);
      try (ActivityController<MainActivity> controller = start()) {
        MainActivity host = controller.get();
        host.state =
            MainActivity.obj(
                "units", new JSONArray().put(unit("o1")), "team", new JSONArray().put("o1"));
        host.root.setPadding(36, 10, 8, 10);
        host.menus.library.unit("o1");
        measure(host.root, size.contains("1280") ? 1280 : 915, size.contains("720") ? 720 : 412);
        for (String action :
            new String[] {"unit-promote", "unit-promo", "unit-learn", "unit-heal"}) {
          View button = tag(host.root, action);
          assertFullyVisible(button);
          assertTrue(button.getHeight() >= host.dp(48));
        }
        assertFullyVisible(tag(host.root, "unit-hp"));
        assertFullyVisible(tag(host.root, "unit-xp"));
        assertEquals(View.GONE, tag(host.root, "unit-extra-details").getVisibility());
        LinearLayout actions = (LinearLayout) tag(host.root, "unit-promote").getParent();
        int primary = 0;
        for (int i = 0; i < actions.getChildCount(); i++) {
          GameButtonDrawable background =
              (GameButtonDrawable) actions.getChildAt(i).getBackground();
          if (background.color == GameButtonDrawable.PRIMARY) primary++;
        }
        assertEquals("주요 성장 행동 하나만 강조", 1, primary);
      }
    }
  }

  @Test
  public void statsAndAwardsCanBeExpandedWithoutHidingGrowthActions() {
    try (ActivityController<MainActivity> controller = start()) {
      MainActivity host = controller.get();
      host.state =
          MainActivity.obj(
              "units", new JSONArray().put(unit("o1")), "team", new JSONArray().put("o1"));
      host.menus.library.unit("o1");
      View details = tag(host.root, "unit-extra-details");
      assertEquals(View.GONE, details.getVisibility());
      tag(host.root, "unit-details-toggle").performClick();
      assertEquals(View.VISIBLE, details.getVisibility());
      measure(host.root, 915, 412);
      assertFullyVisible(tag(host.root, "unit-promote"));
      tag(host.root, "unit-details-toggle").performClick();
      assertEquals(View.GONE, details.getVisibility());
    }
  }

  @Test
  public void healingFromDetailsPreselectsTheOwnedUnitEvenOutsideSavedTeam() {
    try (ActivityController<MainActivity> controller = start()) {
      MainActivity host = controller.get();
      host.state =
          MainActivity.obj(
              "units",
              new JSONArray().put(unit("o1")).put(unit("o2")),
              "team",
              new JSONArray().put("o1"),
              "items",
              new JSONArray().put(MainActivity.obj("id", "potion", "count", 2)),
              "itemDefs",
              MainActivity.obj("potion", MainActivity.obj("name", "치료약", "heal", 50)));
      host.menus.library.healSheet(null, "o2", () -> {});
      View dialog = host.visibleDialog.getWindow().getDecorView();
      assertTrue(tag(dialog, "heal-target-o2").isSelected());
      assertFalse(tag(dialog, "heal-target-o1").isSelected());
      host.visibleDialog.dismiss();
    }
  }

  @Test
  public void availableActionsComeFromServerOffersAndHealingFromActualHp() {
    JSONObject unit =
        MainActivity.obj(
            "learn",
            new JSONArray(),
            "promo",
            new JSONArray(),
            "evolve",
            new JSONArray(),
            "hp",
            100,
            "maxHp",
            100,
            "nextLearnLevel",
            15);
    assertEquals("다음 새 기술 · Lv15", NativeGrowthGuidance.actionSummary(unit));
    try {
      unit.put("hp", 1);
      assertEquals("회복 필요", NativeGrowthGuidance.actionSummary(unit));
    } catch (JSONException impossible) {
      throw new AssertionError(impossible);
    }
    assertTrue(NativeGrowthGuidance.actionSummary(unit("o1")).contains("새 기술 2개 배우기"));
  }
}
