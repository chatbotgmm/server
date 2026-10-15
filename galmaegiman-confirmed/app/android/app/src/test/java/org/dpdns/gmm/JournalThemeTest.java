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
        assertTrue("밝은 기록지 면 확보", luminance(center) >= luminance(JournalTheme.PAPER));
        assertTrue("종이 위 본문 대비 확보", contrast(JournalTheme.INK, center) >= 4.5);
        assertTrue("가죽 프레임도 실제로 표시", colorCount(bitmap, JournalTheme.LEATHER) > 0);
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
  public void skillAttributeStripeAndComboBrassKeepTheirMeaningOnTheSamePaper() {
    MainActivity host = Robolectric.buildActivity(MainActivity.class).get();
    int attribute = 0xff8fdcf5;
    for (boolean combo : new boolean[] {false, true}) {
      Bitmap bitmap = Bitmap.createBitmap(240, 140, Bitmap.Config.ARGB_8888);
      GameSkillDrawable drawable = new GameSkillDrawable(host, attribute, combo);
      drawable.setBounds(0, 0, 240, 140);
      drawable.setState(new int[] {android.R.attr.state_enabled});
      drawable.draw(new Canvas(bitmap));
      assertTrue(
          "속성과 관계없이 밝은 종이 면", luminance(bitmap.getPixel(120, 70)) >= luminance(JournalTheme.PAPER));
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
          {JournalTheme.WHITE, 0xff3f7079},
          {JournalTheme.MUTED, JournalTheme.DISABLED},
          {JournalTheme.TEAL, JournalTheme.SELECTED}
        }) {
      double a = luminance(pair[0]), b = luminance(pair[1]);
      assertTrue("본문 대비 4.5 이상", (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
    }
  }
}
