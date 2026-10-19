package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 실제 번들 종이가 조작부 크기에 맞춰 늘어나지 않고 읽기 대비를 지키는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class JournalMaterialTest {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();
  final JournalThemeTest contrast = new JournalThemeTest();

  @Before
  public void isolate() {
    fixtures.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @Test
  public void bundledPaperWaitsForExistingLoaderAndKeepsItsGrainSizeAcrossControlBounds()
      throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      review.await(host.art::idle);
      assertTrue("기존 리소스 로더 완료 시 APK 종이도 준비됨", JournalMaterial.ready());
      Bitmap small = Bitmap.createBitmap(260, 150, Bitmap.Config.ARGB_8888);
      Bitmap large = Bitmap.createBitmap(500, 270, Bitmap.Config.ARGB_8888);
      for (Bitmap bitmap : new Bitmap[] {small, large}) {
        Canvas canvas = new Canvas(bitmap);
        int save = canvas.getSaveCount();
        assertTrue(
            JournalMaterial.draw(
                canvas, new RectF(4, 4, bitmap.getWidth() - 4, bitmap.getHeight() - 4), 1, 255));
        assertEquals("재질은 호출자의 Canvas 상태를 바꾸지 않음", save, canvas.getSaveCount());
        assertEquals("재질은 자신의 사각 영역 밖으로 번지지 않음", 0, Color.alpha(bitmap.getPixel(0, 0)));
      }
      int first = small.getPixel(5, 5), variations = 0;
      for (int y = 5; y < small.getHeight() - 5; y++)
        for (int x = 5; x < small.getWidth() - 5; x++) {
          int pixel = small.getPixel(x, y);
          assertEquals("정상 종이 파일은 불투명", 255, Color.alpha(pixel));
          assertEquals("같은 종이 결을 버튼 크기에 따라 왜곡하지 않음", pixel, large.getPixel(x, y));
          if (pixel != first) variations++;
        }
      assertTrue("실제 섬유 재질이 들어가며 단색으로 대체되지 않음", variations > 1000);
      small.recycle();
      large.recycle();
    }
  }

  @Test
  public void actualLoadedPaperKeepsOpaqueQuietReadingAreaAndAllTextRolesReadable()
      throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      review.await(host.art::idle);
      assertTrue(JournalMaterial.ready());
      assertTrue("Parchment resources must load", JournalParchment.ready());
      for (int fill :
          new int[] {
            JournalTheme.PAPER, JournalTheme.SELECTED, JournalTheme.DISABLED, JournalTheme.PRIMARY
          }) {
        Bitmap first = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888);
        for (Bitmap bitmap : new Bitmap[] {first, second})
          JournalTheme.drawPaper(
              new Canvas(bitmap), new RectF(0, 0, 320, 160), 1, JournalTheme.LINE, fill);
        assertTrue("같은 UI의 종이 섬유는 정적으로 표시", first.sameAs(second));
        double darkest = 1, lightest = 0;
        for (int y = 32; y < 128; y += 3)
          for (int x = 40; x < 280; x += 3) {
            int pixel = first.getPixel(x, y);
            assertEquals("배경의 낮·밤과 독립적인 불투명 정보 면", 255, Color.alpha(pixel));
            int foreground = fill == JournalTheme.PRIMARY ? JournalTheme.WHITE : JournalTheme.INK;
            assertTrue("실제 종이 재질 위 본문 대비 4.5 이상", contrast.contrast(foreground, pixel) >= 4.5);
            if (fill != JournalTheme.PRIMARY)
              assertTrue(
                  String.format(
                      java.util.Locale.ROOT,
                      "실제 종이 재질 위 보조 설명 대비 4.5 이상: fill=%08x pixel=%08x contrast=%.4f",
                      fill,
                      pixel,
                      contrast.contrast(JournalTheme.MUTED, pixel)),
                  contrast.contrast(JournalTheme.MUTED, pixel) >= 4.5);
            darkest = Math.min(darkest, contrast.luminance(pixel));
            lightest = Math.max(lightest, contrast.luminance(pixel));
          }
        assertTrue("정보 면 중앙에는 거친 재질이나 지도선이 없음", lightest - darkest <= .10);
        first.recycle();
        second.recycle();
      }
    }
  }

  @Test
  public void loadedPaperEdgesMatchTheAssetWithoutRectangularRims() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      review.await(host.art::idle);
      assertTrue("실제 NinePatch 리소스 준비", JournalParchment.ready());
      for (float density : new float[] {1, 2.25f}) {
        int width = Math.round(320 * density);
        int height = Math.round(160 * density);
        RectF bounds = new RectF(0, 0, width, height);
        int left = (int) Math.ceil(density);
        int top = (int) Math.ceil(density);
        int right = (int) Math.floor(width - density);
        int bottom = (int) Math.floor(height - density);
        RectF core = new RectF(
            left + 20 * density, top + 16 * density,
            right - 20 * density, bottom - 16 * density);
        for (boolean button : new boolean[] {false, true}) {
          Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
          Bitmap expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(actual);
          int saves = canvas.getSaveCount();
          if (button)
            JournalTheme.drawButton(
                canvas, bounds, JournalTheme.PAPER, 16 * density,
                2 * density, new Paint(3));
          else
            JournalTheme.drawPanel(canvas, bounds, density, JournalTheme.PAPER, true);
          assertEquals("자연 종이도 Canvas 상태 보존", saves, canvas.getSaveCount());
          assertEquals(new RectF(0, 0, width, height), bounds);
          android.graphics.drawable.NinePatchDrawable reference =
              (android.graphics.drawable.NinePatchDrawable)
                  host.getResources().getDrawable(
                      button ? R.drawable.journal_parchment_button
                             : R.drawable.journal_parchment_panel, null).mutate();
          reference.setTargetDensity(Math.round(160 * density));
          reference.setBounds(left, top, right, bottom);
          reference.setAlpha(255);
          reference.draw(new Canvas(expected));
          int transparent = 0, visible = 0;
          for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
              if (x >= Math.floor(core.left) - 1
                  && x <= Math.ceil(core.right) + 1
                  && y >= Math.floor(core.top) - 1
                  && y <= Math.ceil(core.bottom) + 1) continue;
              assertEquals(
                  "바깥 띠는 원본 종이 윤곽 그대로이며 목재·황동 사각선 없음",
                  expected.getPixel(x, y), actual.getPixel(x, y));
              int alpha = Color.alpha(actual.getPixel(x, y));
              if (x >= left && x < right && y >= top && y < bottom) {
                if (alpha == 0) transparent++;
                if (alpha > 0) visible++;
              }
            }
          }
          assertTrue("잘려 나간 종이 외곽은 투명", transparent > 0);
          assertTrue("자연 종이 가장자리도 실제로 보임", visible > 0);
          assertEquals("중앙 읽기 면은 완전 불투명",
              255, Color.alpha(actual.getPixel(width / 2, height / 2)));
          actual.recycle();
          expected.recycle();
        }
      }
    }
  }
}
