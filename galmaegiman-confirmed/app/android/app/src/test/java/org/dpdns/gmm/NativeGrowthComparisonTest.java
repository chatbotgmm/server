package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 성장 미리보기만 열어도 실제 기술·표창을 바꾸지 않는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeGrowthComparisonTest {
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
  public void restoreFontSize() {
    RuntimeEnvironment.setFontScale(1);
  }

  ActivityController<MainActivity> start() {
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible();
    MainActivity host = controller.get();
    host.motionEnabled = false;
    host.guide.enabled = false;
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.state =
        MainActivity.obj(
            "units",
            new JSONArray().put(unit()),
            "attributes",
            MainActivity.obj(
                "STONE", MainActivity.obj("name", "돌"), "ICE", MainActivity.obj("name", "얼음")));
    host.menus.library.unit("o1");
    measure(host.root, 915, 412);
    return controller;
  }

  JSONObject oldSkill() {
    return MainActivity.obj(
        "id",
        "role-DEFENSE",
        "name",
        "버티기 자세",
        "attr",
        "STONE",
        "power",
        0,
        "desc",
        "3턴 동안 받는 피해 -45%, 상태이상을 막아요.");
  }

  JSONObject nextSkill() {
    return MainActivity.obj(
        "id",
        "learn-ice",
        "name",
        "빙하를 가르는 얼음 창",
        "attr",
        "ICE",
        "cat",
        "mag",
        "power",
        40,
        "hits",
        2,
        "desc",
        "위력 40으로 2번, 맞은 상대를 2턴 동안 얼려요.");
  }

  JSONObject award() {
    JSONObject before =
        MainActivity.obj("hp", 108, "patk", 53, "pdef", 61, "matk", 45, "mdef", 66, "spd", 12);
    JSONObject after =
        MainActivity.obj("hp", 146, "patk", 64, "pdef", 79, "matk", 54, "mdef", 86, "spd", 12);
    return MainActivity.obj(
        "id",
        "bulwark",
        "name",
        "항구의 든든한 수호자",
        "desc",
        "체력과 방어 능력치가 증가해요.",
        "preview",
        MainActivity.obj(
            "fromStage",
            0,
            "toStage",
            1,
            "beforeStats",
            before,
            "afterStats",
            after,
            "beforeSkills",
            new JSONArray().put(oldSkill()),
            "afterSkills",
            new JSONArray().put(oldSkill()),
            "beforePromo",
            new JSONArray(),
            "afterPromo",
            new JSONArray().put(nextSkill()),
            "cost",
            0,
            "restoresHp",
            true));
  }

  JSONObject unit() {
    return MainActivity.obj(
        "key",
        "o1",
        "name",
        "갈매미맨",
        "level",
        20,
        "hp",
        40,
        "maxHp",
        108,
        "xp",
        20,
        "xpNext",
        400,
        "stage",
        0,
        "skills",
        new JSONArray().put(oldSkill()),
        "learn",
        new JSONArray().put(nextSkill()),
        "evolve",
        new JSONArray().put(award()),
        "promo",
        new JSONArray(),
        "image",
        "");
  }

  void measure(View view, int width, int height) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
  }

  String text(View view) {
    StringBuilder out = new StringBuilder();
    if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) out.append(text(parent.getChildAt(i)));
    }
    return out.toString();
  }

  View clickableWithDescription(View view, String prefix) {
    if (view.isClickable()
        && view.getContentDescription() != null
        && view.getContentDescription().toString().startsWith(prefix)) return view;
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) {
        View found = clickableWithDescription(parent.getChildAt(i), prefix);
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test
  public void selectingForgottenSkillOpensFullComparisonWithoutSpendingLearningChance() {
    try (ActivityController<MainActivity> controller = start()) {
      MainActivity host = controller.get();
      String before = host.state.toString();
      host.menus.library.forgetChoice("o1", "learn", nextSkill());
      View selected =
          clickableWithDescription(host.visibleDialog.getWindow().getDecorView(), "버티기 자세");
      assertNotNull(selected);
      selected.performClick();
      View dialog = host.visibleDialog.getWindow().getDecorView();
      assertNotNull(dialog.findViewWithTag("growth-skill-comparison"));
      String labels = text(dialog);
      for (String value :
          new String[] {
            "버티기 자세",
            "빙하를 가르는 얼음 창",
            "속성 · 돌",
            "속성 · 얼음",
            "직접 공격 없음",
            "위력 · 40 × 2회",
            "-45%",
            "2턴",
            "같은 효과가 이미 적용 중"
          }) assertTrue(value, labels.contains(value));
      assertEquals(
          "교체 확정", host.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
      assertEquals(before, host.state.toString());
      assertFalse(host.busy);
      host.visibleDialog.dismiss();
    }
  }

  @Test
  public void promotionPreviewUsesEachServerStatAndKeepsPermanentConfirmationSeparate() {
    try (ActivityController<MainActivity> controller = start()) {
      MainActivity host = controller.get();
      String before = host.state.toString();
      host.menus.library.promotion("o1");
      View preview =
          host.visibleDialog
              .getWindow()
              .getDecorView()
              .findViewWithTag("promotion-preview-bulwark");
      assertNotNull(preview);
      preview.performClick();
      View dialog = host.visibleDialog.getWindow().getDecorView();
      String labels = text(dialog);
      for (String value :
          new String[] {
            "108 → 146 (+38)",
            "53 → 64 (+11)",
            "12 → 12 · 유지",
            "새로 배울 수 있음",
            "자동으로 배우지 않아요",
            "재화 소모 없음",
            "체력이 가득 회복",
            "확정 후 바꿀 수 없어요"
          }) assertTrue(value, labels.contains(value));
      AlertDialog comparison = host.visibleDialog;
      comparison.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      // AlertDialog의 기본 버튼은 Handler로 선택 콜백·이전 창 닫기를 전달한다.
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertNotNull("승진을 바로 실행하지 않고 영구 선택 확인창을 연다", host.visibleDialog);
      assertNotSame("미리보기와 최종 확인은 별도 창", comparison, host.visibleDialog);
      assertFalse("미리보기 창은 닫힘", comparison.isShowing());
      assertTrue(text(host.visibleDialog.getWindow().getDecorView()).contains("직무 표창은 바꿀 수 없어요."));
      assertEquals(before, host.state.toString());
      assertFalse(host.busy);
      host.visibleDialog.dismiss();
    }
  }

  @Test
  public void missingPreviewNeverInventsPostPromotionStatsOrFreeBenefits() {
    try (ActivityController<MainActivity> controller = start()) {
      MainActivity host = controller.get();
      LinearLayout comparison =
          new NativeGrowthComparison(host, new MenuKit(host))
              .promotion(unit(), MainActivity.obj("name", "표창"));
      assertTrue(text(comparison).contains("미리보기를 받지 못했어요"));
      assertNull(comparison.findViewWithTag("growth-promotion-stats"));
      assertFalse(text(comparison).contains("재화 소모 없음"));
      JSONObject partial =
          MainActivity.obj(
              "preview",
              MainActivity.obj("beforeStats", new JSONObject(), "afterStats", new JSONObject()));
      assertFalse("불완전한 미리보기를 0 능력치로 표시하지 않음", NativeGrowthComparison.hasPreview(partial));
      assertTrue(NativeGrowthComparison.hasPreview(award()));
    }
  }
}
