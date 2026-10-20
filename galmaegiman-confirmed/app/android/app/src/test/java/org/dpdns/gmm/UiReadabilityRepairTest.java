package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** 이름·기술 설명·조합 이미지 버튼·미공개 표기의 국소 회귀 검사. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiReadabilityRepairTest {
  final BattleReadabilityTest helper = new BattleReadabilityTest();

  @Before public void isolate() { helper.isolatedAccount(); }

  @Test public void partyNamesKeepFontExtentsAndFullIdentity() {
    try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      String name = "황금빛 바다의 갈매미";
      TextView label = PartyCharacterView.nameLabel(c.get(), name);
      helper.measure(label, 96, 100);
      assertTrue(label.getIncludeFontPadding());
      assertEquals(name, label.getContentDescription());
      assertEquals(name, label.getText().toString().replace('\n', ' '));
      assertNull(label.getEllipsize());
    }
  }

  @Test public void statusNamesOmitChanceAndDurationButKeepOtherEffects() {
    JSONObject status = MainActivity.obj("kind", "status", "status", "burn", "chance", 6, "turns", 3);
    String original = status.toString();
    assertEquals("화상", SkillCardView.shortEffect(status));
    assertEquals(original, status.toString());
    assertEquals("화상 · 방어 -20%", SkillCardView.statusNamesOnly("화상 6% · 3턴 · 방어 -20%"));
    assertEquals("빙결", SkillCardView.statusNamesOnly("빙결6프로"));
    assertEquals("수면", SkillCardView.statusNamesOnly("수면 · 2턴"));
    assertEquals("공격 +20% · 2턴", SkillCardView.shortEffect(
        MainActivity.obj("kind", "buff", "stat", "atk", "pct", 20, "turns", 2)));
    JSONObject skill = MainActivity.obj("summary", "화상 6% · 3턴", "desc", "화상 6%, 3턴");
    assertEquals("화상", SkillCardView.conciseEffect(skill));
    assertEquals("화상 6%, 3턴", skill.optString("desc"));
  }

  @Test public void constrainedSkillDescriptionsUseWholeLinesAndRecoverOnResize() {
    try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      BattleView battle = new BattleView(c.get(), helper.battle());
      JSONObject skill = helper.skill("basic", "기본 공격", null);
      SkillCardView card = new SkillCardView(battle, skill, false, true, null);
      for (int step = 0; step <= 152; step++) {
        // 확대/축소 두 방향에서 1줄·2줄 경계의 폰트 하단 여백도 확인한다.
        int height = step <= 76 ? 124 - step : 48 + (step - 76);
        helper.measure(card, 220, height);
        assertTrue(card.use.getHeight() >= 48);
        if (card.effect.getVisibility() == View.VISIBLE) {
          assertTrue(card.content.getTop() + card.effect.getBottom() <= height - card.content.getPaddingBottom());
          int lines = card.effect.getLayout().getLineCount();
          assertTrue(lines <= 2);
          assertTrue(card.effect.getLayout().getLineBottom(lines - 1)
              + card.effect.getCompoundPaddingTop() + card.effect.getCompoundPaddingBottom()
              <= card.effect.getHeight());
        }
        if (height == 124) assertEquals(View.VISIBLE, card.effect.getVisibility());
        assertTrue(card.use.getContentDescription().toString().contains(skill.optString("desc")));
      }
    }
  }

  @Test public void imageButtonColorsFollowImageAndRestorePaperFallback() {
    try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h = c.get();
      Button button = h.button("조합하기", null);
      ColorDrawable fallback = new ColorDrawable(JournalTheme.PAPER);
      ColorStateList text = JournalTheme.controlText();
      ColorStateList tint = ColorStateList.valueOf(JournalTheme.PAPER);
      int[] padding = {4, 3, 4, 3};
      Bitmap image = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888);
      UiMotionAssets.Background background = new UiMotionAssets.Background(image, new float[] {0,0,0,0}, 1, true);
      for (boolean enabled : new boolean[] {true, false, true}) {
        button.setEnabled(enabled);
        helper.measure(button, 240, 80);
        UiMotionUiBinding.fitPrimary(h, button, background, fallback, text, tint, padding);
        assertSame(background, button.getBackground());
        assertSame(UiMotionUiBinding.IMAGE_PRIMARY_TEXT, button.getTextColors());
        assertNull(button.getBackgroundTintList());
        helper.measure(button, 40, 48);
        UiMotionUiBinding.fitPrimary(h, button, background, fallback, text, tint, padding);
        assertSame(fallback, button.getBackground());
        assertSame(text, button.getTextColors());
        assertSame(tint, button.getBackgroundTintList());
        assertEquals(padding[0], button.getPaddingLeft());
        assertEquals(padding[3], button.getPaddingBottom());
      }
      image.recycle();
    }
  }

  @Test public void hiddenCardLabelsHaveOpaqueBackingAndNeverRevealIdentity() {
    try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity h = c.get();
      JSONObject unknown = MainActivity.obj("known", false, "name", "미공개", "tierName", "입사등록증", "img", "unknown");
      RegistrationCardView card = h.menus.economy.fullEconomyCard(unknown);
      for (TextView label : new TextView[] {card.name, card.grade}) {
        assertEquals(View.VISIBLE, label.getVisibility());
        assertTrue(label.getIncludeFontPadding());
        assertEquals(JournalTheme.PAPER, ((ColorDrawable) label.getBackground()).getColor());
        assertEquals(JournalTheme.INK, label.getCurrentTextColor());
      }
      assertEquals("미공개", card.name.getText().toString());
      assertEquals("입사등록증", card.grade.getText().toString());
      assertEquals(View.INVISIBLE, card.picture.getVisibility());
    }
  }
}
