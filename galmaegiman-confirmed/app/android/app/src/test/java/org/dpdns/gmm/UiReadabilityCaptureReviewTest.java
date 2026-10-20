package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 가독성 수정의 실제 글꼴 화면 캡처: 휴대폰·태블릿 가로, 글자 130%. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiReadabilityCaptureReviewTest {
  final BattleReadabilityTest helper = new BattleReadabilityTest();
  final NativeRegistrationReviewTest captures = new NativeRegistrationReviewTest();

  @Before public void isolate() { helper.isolatedAccount(); }

  @After public void restore() {
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
  }

  Path output() throws Exception {
    Path folder = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"), "ui-readability");
    Files.createDirectories(folder);
    return folder;
  }

  void sized(int width, int height, float font) {
    RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-land-mdpi");
    RuntimeEnvironment.setFontScale(font);
  }

  @Test public void battleSkillPanelShowsStatusNamesAndWholeLinesAtPhoneAndTablet() throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}})
      for (float font : new float[] {1, 1.3f}) {
        sized(size[0], size[1], font);
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
          MainActivity a = c.get();
          a.motionEnabled = false;
          a.state = MainActivity.obj("units", new JSONArray());
          JSONObject battle = helper.battle();
          // 서버 요약에 확률·기간이 붙은 상태 이상 기술: 화면에는 상태명만, 접근성에는 원문 전체.
          JSONObject burn = battle.getJSONArray("teams").getJSONObject(0).getJSONArray("fighters")
              .getJSONObject(0).getJSONArray("skills").getJSONObject(3);
          burn.put("summary", "화상 6% · 3턴 · 방어 -20%").put("desc", "위력 40으로 2번, 화상 6% 3턴, 방어 -20%");
          androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(a.root, null);
          a.root.removeAllViews();
          BattleView v = new BattleView(a, battle);
          a.root.addView(v, new FrameLayout.LayoutParams(-1, -1));
          a.battle = v.battle; a.screen = "battle";
          v.pane = "skills";
          v.render();
          helper.measure(a.root, size[0], size[1]);
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
          helper.measure(a.root, size[0], size[1]);
          String name = "battle-skills-" + size[0] + "x" + size[1] + "-font" + Math.round(font * 100);
          captures.writeScreenshot(a, output().resolve(name + ".png"), size[0], size[1]);
          boolean sawBurn = false;
          for (SkillCardView card : helper.cards(a.root)) {
            String shown = card.effect.getText().toString();
            assertFalse("상태 확률 미표시 · " + shown, shown.matches(".*(화상|빙결|수면|속박)\\s*\\d+\\s*%.*"));
            if (card.skill.optString("summary").startsWith("화상")) {
              sawBurn = true;
              assertTrue(name, shown.startsWith("화상"));
              assertTrue("원문은 접근성에 유지", card.use.getContentDescription().toString().contains("화상 6% 3턴"));
            }
            if (card.effect.getVisibility() == View.VISIBLE && card.effect.getLayout() != null) {
              int lines = card.effect.getLayout().getLineCount();
              assertTrue(name, lines <= 2);
              assertTrue("완전한 줄만 · " + name, card.effect.getLayout().getLineBottom(lines - 1)
                  + card.effect.getCompoundPaddingTop() + card.effect.getCompoundPaddingBottom() <= card.effect.getHeight());
              int bottom = card.effect.getBottom() + ((View) card.effect.getParent()).getTop();
              assertTrue("카드 아래로 넘치지 않음 · " + name, bottom <= card.getHeight());
            }
          }
          assertTrue("상태 이상 기술 카드가 보임 · " + name, sawBurn);
        }
      }
  }

  @Test public void navyMixButtonUsesLightTextAtPhoneAndTabletWidths() throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}})
      for (float font : new float[] {1, 1.3f}) {
        sized(size[0], size[1], font);
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
          MainActivity a = c.get();
          a.screen = "mix";
          MenuKit k = new MenuKit(a);
          LinearLayout row = k.row();
          row.setBackgroundColor(0xff26323a);
          row.setPadding(24, 16, 24, 16);
          Button make = k.primary(null, "조합하기", () -> {});
          // 재료 탭의 오른쪽 결과 칸 폭(휴대폰 약 290dp, 태블릿 약 420dp)과 48dp 높이.
          int width = size[0] < 1000 ? 290 : 420;
          row.addView(make, new LinearLayout.LayoutParams(width, 48));
          Button off = k.primary(null, "조합하기", () -> {});
          off.setEnabled(false);
          LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(width, 48);
          second.leftMargin = 16;
          row.addView(off, second);
          a.root.removeAllViews();
          a.root.addView(row, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
          android.content.res.ColorStateList paperText = make.getTextColors();
          // 비동기 그림 적용이 끝날 때까지(또는 공간이 부족해 종이로 남는 경우 충분히) 기다린다.
          long end = System.currentTimeMillis() + 4000;
          while (System.currentTimeMillis() < end
              && !(make.getBackground() instanceof UiMotionAssets.Background
                  && off.getBackground() instanceof UiMotionAssets.Background)) {
            helper.measure(a.root, size[0], size[1]);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
            Thread.sleep(10);
          }
          helper.measure(a.root, size[0], size[1]);
          String name = "mix-button-" + size[0] + "x" + size[1] + "-font" + Math.round(font * 100);
          captures.writeScreenshot(a, output().resolve(name + ".png"), size[0], size[1]);
          // 태블릿은 uiText가 글자를 1.14배로 키워 그림 안쪽 폭을 넘으므로 종이 버튼으로 남는다(0bf7846 적용 범위).
          if (font == 1 && size[0] < 1000) assertTrue("휴대폰 기본 글자 크기에서는 남색 이미지 적용 · " + name,
              make.getBackground() instanceof UiMotionAssets.Background);
          for (Button button : new Button[] {make, off}) {
            if (button.getBackground() instanceof UiMotionAssets.Background)
              assertSame("남색 위 밝은 글자 · " + name, UiMotionUiBinding.IMAGE_PRIMARY_TEXT, button.getTextColors());
            else assertSame("종이로 돌아가면 원래 글자색 · " + name, paperText, button.getTextColors());
          }
          System.out.println("MIX-BUTTON " + name + " image=" + (make.getBackground() instanceof UiMotionAssets.Background));
          assertNotNull(make.getLayout());
          assertEquals(0, make.getLayout().getEllipsisCount(0));
          assertTrue("글자가 버튼 안 · " + name, make.getLayout().getLineWidth(0)
              <= make.getWidth() - make.getCompoundPaddingLeft() - make.getCompoundPaddingRight() + 1);
        }
      }
  }
}
