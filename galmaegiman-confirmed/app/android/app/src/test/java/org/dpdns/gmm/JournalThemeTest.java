package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

/** 비율이 달라도 기록지가 읽히고 선택·속성·접근성 의미가 유지되는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JournalThemeTest {
  Bitmap button(MainActivity host, int[] states) {
    Bitmap bitmap = Bitmap.createBitmap(240, 64, Bitmap.Config.ARGB_8888);
    GameButtonDrawable drawable = new GameButtonDrawable(host, GameButtonDrawable.PAPER);
    Button button = new Button(host);
    button.setBackground(drawable);
    boolean enabled = false, selected = false, pressed = false;
    for (int state : states) {
      enabled |= state == android.R.attr.state_enabled;
      selected |= state == android.R.attr.state_selected;
      pressed |= state == android.R.attr.state_pressed;
    }
    // 실제 위젯이 배경을 설치한 뒤 보내는 상태 전이를 검증한다.
    button.setEnabled(enabled);
    button.setSelected(selected);
    button.setPressed(pressed);
    assertEquals(enabled, drawable.state.enabled);
    assertEquals(pressed, drawable.state.pressed);
    drawable.setBounds(0, 0, 240, 64);
    Canvas canvas = new Canvas(bitmap);
    int saves = canvas.getSaveCount();
    drawable.draw(canvas);
    assertEquals(saves, canvas.getSaveCount());
    return bitmap;
  }

  int colorCount(Bitmap bitmap, int color) {
    int count = 0;
    for (int y = 0; y < bitmap.getHeight(); y++)
      for (int x = 0; x < bitmap.getWidth(); x++) if (bitmap.getPixel(x, y) == color) count++;
    return count;
  }

  int nearColorCount(Bitmap bitmap, int color) {
    int count = 0;
    for (int y = 0; y < bitmap.getHeight(); y++)
      for (int x = 0; x < bitmap.getWidth(); x++) {
        int pixel = bitmap.getPixel(x, y);
        if (Color.alpha(pixel) == 255
            && Math.abs(Color.red(pixel) - Color.red(color)) < 28
            && Math.abs(Color.green(pixel) - Color.green(color)) < 28
            && Math.abs(Color.blue(pixel) - Color.blue(color)) < 28) count++;
      }
    return count;
  }

  @Test
  public void statusDialogAndLogFramesRenderWithoutBitmapAssetsAtDifferentAspectRatios() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    assertNull("프레임은 그림 저장소 없이도 그려야 함", host.art);
    for (int[] size : new int[][] {{220, 64}, {640, 150}, {420, 280}}) {
      for (String name : new String[] {"status.webp", "log.webp", "dialog.webp"}) {
        Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF bounds = new RectF(0, 0, size[0], size[1]);
        int saves = canvas.getSaveCount();
        FrameDrawable.drawFrame(canvas, host, name, bounds, new Paint(3));
        assertEquals(new RectF(0, 0, size[0], size[1]), bounds);
        assertEquals(saves, canvas.getSaveCount());
        int center = bitmap.getPixel(size[0] / 2, size[1] / 2);
        assertEquals("종이 면은 사진 뒤를 비추지 않음", 255, Color.alpha(center));
        assertTrue("밝은 기록지 면 확보", luminance(center) >= .72);
        assertTrue("종이 위 본문 대비 확보", contrast(JournalTheme.INK, center) >= 4.5);
        assertTrue("보조 설명도 종이 재질 위에서 읽을 수 있음", contrast(JournalTheme.MUTED, center) >= 4.5);
        assertTrue("목재 프레임도 실제로 표시", colorCount(bitmap, JournalTheme.WOOD) > 0);
        bitmap.recycle();
      }
    }
  }

  @Test
  public void selectedAndDisabledControlsStayDistinctAndRestoreCanvasState() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    Bitmap normal = button(host, new int[] {android.R.attr.state_enabled});
    Bitmap selected =
        button(host, new int[] {android.R.attr.state_enabled, android.R.attr.state_selected});
    Bitmap disabled = button(host, new int[0]);
    Bitmap pressed =
        button(host, new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed});
    assertEquals(0, colorCount(normal, JournalTheme.TEAL));
    assertTrue(colorCount(selected, JournalTheme.TEAL) > 20);
    assertEquals("비활성도 사진 배경에 묻히지 않는 읽기 면 유지", 255, Color.alpha(disabled.getPixel(120, 32)));
    assertEquals(JournalTheme.DISABLED, disabled.getPixel(120, 32));
    assertFalse("비활성 면과 정상 면을 구별", normal.sameAs(disabled));
    assertFalse("눌린 프레임은 실제 위치가 달라짐", normal.sameAs(pressed));
    normal.recycle();
    selected.recycle();
    disabled.recycle();
    pressed.recycle();
  }

  @Test
  public void skillAttributeAndComboBrassKeepTheirMeaningOnReadableSurfaces() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    int attribute = 0xff8fdcf5;
    for (boolean combo : new boolean[] {false, true}) {
      Bitmap bitmap = Bitmap.createBitmap(240, 140, Bitmap.Config.ARGB_8888);
      GameSkillDrawable drawable = new GameSkillDrawable(host, attribute, combo);
      drawable.setBounds(0, 0, 240, 140);
      drawable.setState(new int[] {android.R.attr.state_enabled});
      drawable.draw(new Canvas(bitmap));
      int center = bitmap.getPixel(120, 70);
      assertEquals("합동기·일반 기술의 정보 면은 모두 불투명", 255, Color.alpha(center));
      assertTrue(
          combo ? "청록 합동기는 흰 글자가 읽히는 대비" : "일반 기술은 밝은 종이 위 잉크 대비",
          contrast(combo ? JournalTheme.WHITE : JournalTheme.INK, center) >= 4.5);
      if (!combo) assertTrue("일반 기술의 속성은 종이 전체를 물들이지 않음", luminance(center) >= .72);
      assertEquals(combo ? JournalTheme.BRASS : attribute, bitmap.getPixel(120, 6));
      bitmap.recycle();
    }
  }

  double luminance(int color) {
    double[] channels = {
      Color.red(color) / 255d, Color.green(color) / 255d, Color.blue(color) / 255d
    };
    for (int i = 0; i < channels.length; i++)
      channels[i] =
          channels[i] <= .04045 ? channels[i] / 12.92 : Math.pow((channels[i] + .055) / 1.055, 2.4);
    return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
  }

  double contrast(int first, int second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }

  @Test
  public void changingButtonRoleUpdatesForegroundTogetherWithBackground() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    JournalButton button = new JournalButton(host);
    button.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
    assertEquals(JournalTheme.INK, button.getCurrentTextColor());
    button.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PRIMARY));
    assertEquals(JournalTheme.WHITE, button.getCurrentTextColor());
    button.setPressed(true);
    assertEquals(JournalTheme.WHITE, button.getCurrentTextColor());
    button.setPressed(false);
    button.setSelected(true);
    assertEquals(JournalTheme.TEAL, button.getCurrentTextColor());
    button.setEnabled(false);
    assertEquals(JournalTheme.MUTED, button.getCurrentTextColor());
    button.setSelected(false);
    button.setEnabled(true);
    assertEquals(JournalTheme.WHITE, button.getCurrentTextColor());
    button.setBackground(new GameButtonDrawable(host, GameButtonDrawable.PAPER));
    assertEquals(JournalTheme.INK, button.getCurrentTextColor());
  }

  @Test
  public void bodySupportingAndControlTextMeetReadableContrastOnTheirSurfaces() {
    for (int[] pair :
        new int[][] {
          {JournalTheme.INK, JournalTheme.PAPER},
          {JournalTheme.MUTED, JournalTheme.PAPER},
          {JournalTheme.TEAL, JournalTheme.PAPER},
          {JournalTheme.WHITE, JournalTheme.PRIMARY},
          {JournalTheme.WHITE, JournalTheme.PRIMARY_HIGHLIGHT},
          {JournalTheme.MUTED, JournalTheme.DISABLED},
          {JournalTheme.TEAL, JournalTheme.SELECTED}
        }) {
      double a = luminance(pair[0]), b = luminance(pair[1]);
      assertTrue("본문 대비 4.5 이상", (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
    }
  }

  @Test
  public void canvasAndAndroidResourceTokensMatchApprovedCaptainJournalPalette() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    for (int[] entry :
        new int[][] {
          {R.color.journal_paper, JournalTheme.PAPER, 0xfff5efdc},
          {R.color.journal_ink, JournalTheme.INK, 0xff292d2a},
          // 실제 종이 섬유 위에서도 작은 보조 설명 대비 4.5를 확보한 C 보조 잉크.
          {R.color.journal_muted_ink, JournalTheme.MUTED, 0xff58635d},
          {R.color.journal_wood, JournalTheme.WOOD, 0xff34261f},
          {R.color.journal_brass, JournalTheme.BRASS, 0xffb68b45},
          {R.color.journal_primary, JournalTheme.PRIMARY, 0xff245b61},
          {R.color.journal_primary_text, JournalTheme.WHITE, 0xffffffff},
          {R.color.journal_positive_info, JournalTheme.POSITIVE_INFO, 0xff3e7865},
          {R.color.journal_negative_info, JournalTheme.NEGATIVE_INFO, 0xffa44136}
        }) {
      assertEquals("승인 색상은 Canvas에도 동일 적용", entry[2], entry[1]);
      assertEquals("View 리소스와 Canvas 토큰을 함께 유지", entry[1], host.getColor(entry[0]));
    }
    assertEquals(48, host.getResources().getDimension(R.dimen.journal_touch_target_min), .001);
  }

  @Test
  public void agedPaperHasStableEdgeDetailAndQuietOpaqueReadingAreaInsideItsOwnBounds() {
    for (float density : new float[] {1, 2.25f}) {
      int width = Math.round(360 * density), height = Math.round(180 * density);
      RectF bounds = new RectF(12 * density, 10 * density, 348 * density, 170 * density);
      Bitmap first = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Bitmap second = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      for (Bitmap bitmap : new Bitmap[] {first, second}) {
        Canvas canvas = new Canvas(bitmap);
        int saves = canvas.getSaveCount();
        JournalTheme.drawPanel(canvas, bounds, density, JournalTheme.PAPER, true);
        assertEquals("장식 그리기는 상위 Canvas 클립을 보존", saves, canvas.getSaveCount());
      }
      assertTrue("종이 섬유·지도 장식은 프레임마다 바뀌지 않음", first.sameAs(second));
      assertTrue("이중 프레임에 황동선이 실제 존재", nearColorCount(first, JournalTheme.BRASS) > 50);
      int topSeamPixel = (int) Math.floor(bounds.top + 3.1f * density);
      assertEquals(
          "황동선의 핵심은 픽셀 경계에 분산되지 않음 · density=" + density,
          JournalTheme.BRASS,
          first.getPixel(width / 2, topSeamPixel));
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
          int pixel = first.getPixel(x, y);
          if (!bounds.contains(x + .5f, y + .5f))
            assertEquals("프레임 장식은 다른 UI나 안전 영역으로 번지지 않음", 0, Color.alpha(pixel));
        }
      // 종이 섬유는 밝기가 조금 달라도 정상이다. 동일 픽셀을 강요하면 단색 면만 통과하므로
      // 실제 읽기 영역의 불투명도·명도 변화·본문 대비를 검사한다.
      int row = Math.round(90 * density);
      double darkest = 1, lightest = 0;
      for (int x = Math.round(80 * density); x < Math.round(280 * density); x++) {
        int pixel = first.getPixel(x, row);
        assertEquals("본문은 사진을 비추지 않는 불투명 기록지", 255, Color.alpha(pixel));
        darkest = Math.min(darkest, luminance(pixel));
        lightest = Math.max(lightest, luminance(pixel));
        assertTrue("종이 섬유 위에서도 양피지 중앙 본문 대비 유지", contrast(JournalTheme.INK, pixel) > 10);
      }
      assertTrue("본문 중앙에는 읽기를 방해하는 거친 무늬가 없음", lightest - darkest <= .10);
      // 위쪽 안쪽 지도선과 바깥 목재의 사이에 밝은 종이·황동이 모두 남는다.
      assertTrue("황동선 바깥에는 목재 받침도 구별되어야 함", colorCount(first, JournalTheme.WOOD) > 0);
      first.recycle();
      second.recycle();
    }
  }
}
