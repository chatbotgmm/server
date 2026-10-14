package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 격리된 3999 서버와 실제 Android View로 큰 글자·잘림·선택 보호를 재현한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeUx71ReviewTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before
  public void pausedAnimationClock() {
    review.scheduledFramesUseThePausedLooperClock();
  }

  <T extends View> List<T> views(View root, Class<T> kind) {
    ArrayList<T> found = new ArrayList<>();
    if (kind.isInstance(root)) found.add(kind.cast(root));
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++)
        found.addAll(views(group.getChildAt(i), kind));
    }
    return found;
  }

  void fullTextFits(TextView text) {
    Layout layout = text.getLayout();
    assertNotNull("텍스트 배치가 있어야 함: " + text.getText(), layout);
    for (int i = 0; i < layout.getLineCount(); i++)
      assertEquals("내용을 말줄임하지 않음: " + text.getText(), 0, layout.getEllipsisCount(i));
    assertTrue(
        "텍스트 높이 확보: " + text.getText(),
        text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
            >= layout.getHeight());
  }

  void viewport(MainActivity host, int width, int height) {
    // 다음 기기의 안전 높이를 화면 생성 전에 적용해 이전 폰의 compact 판정을 물려받지 않는다.
    review.force(host.root);
    host.root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    host.root.layout(0, 0, width, height);
  }

  void koreanWordsStayTogether(TextView view) {
    String text = view.getText().toString();
    Layout layout = view.getLayout();
    for (int i = 0; i < layout.getLineCount() - 1; i++) {
      int at = layout.getLineEnd(i);
      if (at <= 0 || at >= text.length()) continue;
      boolean broken =
          Character.UnicodeBlock.of(text.charAt(at - 1)) == Character.UnicodeBlock.HANGUL_SYLLABLES
              && Character.UnicodeBlock.of(text.charAt(at))
                  == Character.UnicodeBlock.HANGUL_SYLLABLES;
      assertFalse("한글 어절 중간 줄바꿈: " + text, broken);
    }
  }

  JSONObject skill(String id, String name, int power, String description) {
    return MainActivity.obj(
        "id", id,
        "name", name,
        "power", power,
        "desc", description,
        "preview",
            MainActivity.obj(
                "attr", "STONE", "attrName", "돌", "category", "phys", "multiplier", 1.5));
  }

  JSONObject battle() throws JSONException {
    JSONArray teams = new JSONArray();
    for (int side = 0; side < 2; side++) {
      JSONObject fighter =
          MainActivity.obj(
              "key",
              "ux71-side-" + side,
              "id",
              "D1",
              "name",
              side == 0 ? "갈매미맨" : "상대 갈매미맨",
              "image",
              "/duel/sprites/D1_s0_" + (side == 0 ? "back" : "front") + ".webp",
              "level",
              11,
              "hp",
              168,
              "maxHp",
              188,
              "attr",
              "돌",
              "attrCode",
              "STONE",
              "status",
              new JSONObject(),
              "effects",
              new JSONArray(),
              "skills",
              new JSONArray()
                  .put(skill("role-guard", "버티기 자세", 0, "3턴 동안 받는 피해가 33% 감소해요."))
                  .put(skill("sig", "바다의 황금 수호빛", 60, "상대에게 공격하고 2턴 동안 방어력이 20% 올라요."))
                  .put(skill("learn-wave", "파도 밀어내기", 45, "상대를 공격하고 속도를 10% 낮춰요."))
                  .put(skill("combo-stone", "함께 지키는 항구", 100, "같은 시너지 동료와 강하게 공격해요.")));
      if (side == 0)
        fighter.put(
            "effects",
            new JSONArray()
                .put(
                    MainActivity.obj(
                        "id",
                        "guard:0",
                        "label",
                        "받는 피해 감소",
                        "kind",
                        "guard",
                        "valueText",
                        "-33%",
                        "remainingTurns",
                        2))
                .put(
                    MainActivity.obj(
                        "id",
                        "regen:0",
                        "label",
                        "재생",
                        "kind",
                        "regen",
                        "valueText",
                        "턴마다 최대 체력 10% 회복",
                        "remainingTurns",
                        1))
                .put(
                    MainActivity.obj(
                        "id",
                        "status:burn",
                        "label",
                        "화상",
                        "kind",
                        "status",
                        "valueText",
                        "",
                        "remainingTurns",
                        2)));
      teams.put(
          MainActivity.obj(
              "active", 0,
              "fighters", new JSONArray().put(fighter),
              "offers", new JSONArray(),
              "augments", new JSONArray(),
              "legal", new JSONArray()));
    }
    return MainActivity.obj(
        "session",
        "ux71-render-only",
        "revision",
        0,
        "scene",
        "deep",
        "mode",
        "practice",
        "phase",
        "action",
        "turn",
        2,
        "teams",
        teams,
        "displayEvents",
        new JSONArray(),
        "log",
        new JSONArray().put("[내] 갈매미맨이 버티기 자세! 3턴 동안 받는 피해가 33% 감소해요."));
  }

  BattleView showBattle(MainActivity host, JSONObject data) {
    host.battle = data;
    host.battleScreen();
    return (BattleView) host.root.getChildAt(0);
  }

  @Test
  public void largeFontSkillsExposeFullTitlesAndIndependentDetailsOnPhoneAndTablet()
      throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      assertEquals("/duel/bg/deepbattleb.webp", BattleBackground.path("deep"));
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        RuntimeEnvironment.setFontScale(1.3f);
        viewport(host, size[0], size[1]);
        BattleView view = showBattle(host, battle());
        review.capture(host, "ux71-" + size[0] + "-font130-current-effects", size[0], size[1]);
        String effects = view.effectInfo[0].getContentDescription().toString();
        assertTrue(effects.contains("-33% · 2턴"));
        assertTrue(effects.contains("최대 체력 10% 회복 · 1턴"));
        assertTrue(effects.contains("화상 · 2턴"));
        view.effectInfo[0].performClick();
        review.popup(host, "ux71-" + size[0] + "-font130-effects-details");
        JSONArray current = BattleEffects.current(view.active(0));
        for (int i = 0; i < current.length(); i++) {
          JSONObject effect = current.optJSONObject(i);
          String label = BattleEffects.label(effect);
          boolean retained = false;
          for (TextView text :
              views(host.visibleDialog.getWindow().getDecorView(), TextView.class)) {
            if (!label.equals(text.getText().toString()) || !(text.getText() instanceof Spanned))
              continue;
            Spanned styled = (Spanned) text.getText();
            for (ForegroundColorSpan span :
                styled.getSpans(0, styled.length(), ForegroundColorSpan.class))
              retained |= span.getForegroundColor() == BattleEffects.color(effect);
          }
          assertTrue("대화창 스타일 적용 후에도 효과 의미 색상 유지: " + label, retained);
        }
        host.visibleDialog.dismiss();
        view.pane = "skills";
        view.render();
        review.capture(host, "ux71-" + size[0] + "-font130-skills", size[0], size[1]);
        List<SkillCardView> skills = views(view.controls, SkillCardView.class);
        assertEquals(4, skills.size());
        for (SkillCardView card : skills) {
          fullTextFits(card.title);
          fullTextFits(card.effect);
          if (size[0] == 915) {
            for (TextView text :
                new TextView[] {card.title, card.category, card.power, card.effect})
              if (text.getParent() != null) koreanWordsStayTogether(text);
          }
          assertTrue(
              "제목이 상세 버튼과 폭을 나누지 않음", card.title.getWidth() >= card.getWidth() - host.dp(24));
          assertTrue("상세 조작 48dp 유지", card.detail.getHeight() >= host.dp(48));
        }
        view.menu.fullScroll(View.FOCUS_DOWN);
        review.capture(host, "ux71-" + size[0] + "-font130-skills-bottom", size[0], size[1]);
      }
    } finally {
      RuntimeEnvironment.setFontScale(1f);
    }
  }

  @Test
  public void missionRewardsRemainSingleLineForOneToFourDigitsAtLargeFont() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        RuntimeEnvironment.setFontScale(1.3f);
        viewport(host, size[0], size[1]);
        host.base("오늘의 미션", "daily");
        LinearLayout[] columns = new MenuKit(host).split(.52f);
        columns[0].addView(host.text("보상 표시 검토 · 글자 1.3배", 18));
        for (int reward : new int[] {1, 20, 100, 1000}) {
          View row =
              host.menus.extras.missionCard(
                  MainActivity.obj(
                      "label",
                      "전투 3번 완료",
                      "tickets",
                      reward,
                      "value",
                      2,
                      "goal",
                      3,
                      "done",
                      false));
          columns[1].addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        review.capture(host, "ux71-" + size[0] + "-font130-mission-rewards", size[0], size[1]);
        int checked = 0;
        for (TextView text : views(host.root, TextView.class)) {
          if (!"native-mission-reward".equals(text.getTag())) continue;
          assertEquals("보상 숫자와 단위 한 줄", 1, text.getLineCount());
          assertTrue("보상 단위를 함께 표시", text.getText().toString().endsWith("장"));
          fullTextFits(text);
          float width = text.getPaint().measureText(text.getText().toString());
          assertTrue(
              "보상 숫자 폭 확보: " + text.getText(),
              width <= text.getWidth() - text.getPaddingLeft() - text.getPaddingRight() + 1);
          checked++;
        }
        assertEquals(4, checked);
      }
    } finally {
      RuntimeEnvironment.setFontScale(1f);
    }
  }

  @Test
  public void zflipLargeFontAugmentExplainsRandomTimeoutAndNamesConfirmedChoice() throws Exception {
    RuntimeEnvironment.setQualifiers("w1006dp-h411dp-land-mdpi");
    RuntimeEnvironment.setFontScale(1.3f);
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
          host.root, (v, insets) -> insets);
      host.root.setPadding(36, 10, 8, 10);
      JSONObject data = battle();
      data.put("phase", "choose");
      data.put("serverNow", System.currentTimeMillis());
      data.put("chooseDeadline", System.currentTimeMillis() + 30000);
      data.optJSONArray("teams")
          .optJSONObject(0)
          .put(
              "offers",
              new JSONArray()
                  .put(
                      MainActivity.obj(
                          "id", "dual", "name", "이중 속성", "desc", "상대 약점을 찌르는 공격 속성을 하나 더 얻어요."))
                  .put(
                      MainActivity.obj(
                          "id", "focus", "name", "야근 수당", "desc", "내 갈매미들의 물리·마법 공격 +12%"))
                  .put(
                      MainActivity.obj(
                          "id", "personnel", "name", "인사 발령", "desc", "이번 전투 동안 교체는 턴을 쓰지 않아요.")));
      NativeAugmentView choice = showBattle(host, data).augmentView;
      review.fullBleedCapture(host, "ux71-font130-augment");
      assertTrue("시간 초과 시 무작위 선택 규칙 안내", choice.timerRule.getText().toString().contains("무작위"));
      fullTextFits(choice.timerRule);
      choice.picks.get(0).performClick();
      assertTrue(choice.confirm.getText().toString().contains("이중 속성"));
      assertTrue(choice.confirm.isEnabled());
      review.fullBleedCapture(host, "ux71-font130-augment-selected");
    } finally {
      RuntimeEnvironment.setFontScale(1f);
    }
  }

  @Test
  public void unsavedTeamExitAndRejectedSaveKeepDraftAndReturnContext() throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity host = controller.get();
      host.screen = "stages";
      host.selectionMode = "story";
      host.menus.teamDraft = null;
      host.team();
      review.capture(host, "ux71-team-saved", 915, 412);
      assertEquals("stages", host.menus.teamReturnTarget());
      assertFalse(host.menus.isTeamDirty());
      host.menus.teamDraft = new ArrayList<>(List.of("o999999999"));
      assertTrue(host.menus.isTeamDirty());
      AtomicBoolean left = new AtomicBoolean();
      host.menus.requestLeaveTeam(() -> left.set(true));
      AlertDialog guard = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      assertEquals("저장하고 나가기", guard.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
      assertEquals("변경 취소", guard.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
      assertEquals("계속 편집", guard.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
      guard.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertFalse(left.get());
      assertEquals(List.of("o999999999"), host.menus.teamDraft);
      host.menus.requestLeaveTeam(() -> left.set(true));
      guard = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      guard.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      review.await(
          () -> !host.busy && host.visibleDialog != null && host.visibleDialog.isShowing());
      assertFalse("소유하지 않은 키의 격리 서버 거절 후 화면 이탈하지 않음", left.get());
      assertEquals("실패한 요청의 임시 편성 보존", List.of("o999999999"), host.menus.teamDraft);
      assertEquals("team", host.screen);
      assertEquals("stages", host.menus.teamReturnTarget());
      host.visibleDialog.dismiss();
      host.menus.requestLeaveTeam(() -> left.set(true));
      guard = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      guard.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertTrue(left.get());
      assertNull(host.menus.teamDraft);
    }
  }
}
