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

  /** 10-18 앱 배치 설계: 보조 버튼·정보 판은 둥근 크림 면과 얇은 테두리. 찢긴 양피지 윤곽은 쓰지 않는다. */
  @Test
  public void paperButtonsAndPanelsUseRoundedQuietSurface() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      for (float density : new float[] {1, 2.25f}) {
        int width = Math.round(320 * density);
        int height = Math.round(160 * density);
        RectF bounds = new RectF(0, 0, width, height);
        for (boolean button : new boolean[] {false, true}) {
          Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(actual);
          int saves = canvas.getSaveCount();
          if (button)
            JournalTheme.drawButton(canvas, bounds, JournalTheme.PAPER, 16 * density, 2 * density, new Paint(3));
          else JournalTheme.drawPanel(canvas, bounds, density, JournalTheme.PAPER, false);
          assertEquals("Canvas 상태 보존", saves, canvas.getSaveCount());
          assertEquals("둥근 모서리 바깥은 투명", 0, Color.alpha(actual.getPixel(0, 0)));
          assertEquals("둥근 모서리 바깥은 투명", 0, Color.alpha(actual.getPixel(width - 1, 0)));
          int center = actual.getPixel(width / 2, height / 2);
          assertEquals("중앙 읽기 면은 완전 불투명", 255, Color.alpha(center));
          assertEquals("중앙은 무늬 없는 크림 면", 0xfffff8e8, center);
          boolean edge = false;
          for (int y = 0; y < Math.ceil(5 * density); y++) {
            int px = actual.getPixel(width / 2, y);
            if (Color.alpha(px) > 0 && Color.red(px) < 0xf0) edge = true;
          }
          assertTrue("윗변에 크림 면보다 진한 얇은 경계선", edge);
          actual.recycle();
        }
      }
    }
  }
}
