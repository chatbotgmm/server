package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

/** 원본 가림 레이어 추출·공유 재질·API24 생성 경로를 검사한다. 서버나 네트워크는 쓰지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JournalScenePackTest {
  static Bitmap bitmap(int width, int height, int offset) {
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++)
        pixels[y * width + x] =
            0xff000000
                | ((40 + (x + offset) % 180) << 16)
                | ((30 + (y + offset) % 190) << 8)
                | (50 + (x + y) % 170);
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
  }

  @Test
  public void originalPixelsAreOnlyMadeTransparentWhereWaterWasApproved() {
    Bitmap source = bitmap(945, 416, 0), copy = source.copy(Bitmap.Config.ARGB_8888, false);
    JournalScenePack.Material material = JournalScenePack.Material.create(bitmap(128, 64, 20));
    JournalScenePack pack = JournalScenePack.create(source, material, LocalTimeTheme.Phase.DAY);
    assertTrue(pack.layered());
    Region water = JournalSeaMask.region(source.getWidth(), source.getHeight(), pack.phase);
    int left = Math.round(pack.sourceCrop.left * source.getWidth() / JournalAmbient.SOURCE_WIDTH);
    int top = Math.round(pack.sourceCrop.top * source.getHeight() / JournalAmbient.SOURCE_HEIGHT);
    int transparent = 0, fixed = 0;
    for (int y = 0; y < pack.occluder.getHeight(); y++)
      for (int x = 0; x < pack.occluder.getWidth(); x++) {
        int pixel = pack.occluder.getPixel(x, y);
        if (water.contains(left + x, top + y)) {
          assertEquals(0, pixel);
          transparent++;
        } else {
          assertEquals(source.getPixel(left + x, top + y), pixel);
          fixed++;
        }
      }
    assertTrue(transparent > 1000 && fixed > 0);
    assertTrue("원본 Bitmap 내용과 알파를 수정하지 않음", source.sameAs(copy));
    JournalScenePack night = JournalScenePack.create(source, material, LocalTimeTheme.Phase.NIGHT);
    assertSame("시간대별 수면/포말 Bitmap 복제 없음", pack.material, night.material);
    assertTrue(
        "원본 두 장 복사 대신 필요한 작은 고정물 crop만 추출",
        pack.occluder.getAllocationByteCount() < source.getAllocationByteCount() / 4);
  }

  @Test
  public void missingMaterialUsesTheOriginalStaticSceneAndFoamHasIndependentAlpha() {
    Bitmap source = bitmap(189, 83, 0);
    JournalScenePack flat = JournalScenePack.create(source, null, LocalTimeTheme.Phase.MORNING);
    assertFalse(flat.layered());
    assertSame(source, flat.backdrop);
    assertNull(flat.occluder);
    Bitmap surface = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888);
    surface.setPixel(0, 0, 0xff1ba2d0);
    surface.setPixel(1, 0, 0xfffaf9f2);
    JournalScenePack.Material material = JournalScenePack.Material.create(surface);
    assertEquals(0, Color.alpha(material.foam.getPixel(0, 0)));
    assertTrue(Color.alpha(material.foam.getPixel(1, 0)) > 200);
    assertEquals("surface RGB는 그대로 공유", 0xfffaf9f2, surface.getPixel(1, 0));
  }

  @Test
  @Config(sdk = 24)
  @GraphicsMode(GraphicsMode.Mode.LEGACY)
  public void api24CreatesBitmapGradientShadersAndDrawsSeparatedSceneWithoutNewPlatformApis() {
    Bitmap source = bitmap(378, 166, 0);
    JournalScenePack pack =
        JournalScenePack.create(
            source,
            JournalScenePack.Material.create(bitmap(64, 32, 10)),
            LocalTimeTheme.Phase.EVENING);
    // API24의 Robolectric LEGACY는 Region.setPath raster를 구현하지 않는다.
    // 실제 Region/occluder 통합은 위 native-Skia 검사로 보장하고 여기서는
    // 실제 minSdk의 Bitmap+Gradient ComposeShader/Canvas 호출을 검증한다.
    JournalScenePack.Material material = JournalScenePack.Material.create(bitmap(64, 32, 10));
    JournalSeaLayers.LayerSet layers =
        new JournalSeaLayers.LayerSet(
            material, LocalTimeTheme.Phase.EVENING, new float[] {130, 120, 100});
    assertNotNull(layers.mid.paint.getShader());
    Canvas canvas = new Canvas(Bitmap.createBitmap(378, 166, Bitmap.Config.ARGB_8888));
    int count = canvas.getSaveCount();
    layers.far.place(2, 0);
    layers.mid.place(2, 1);
    layers.near.place(2, 2);
    layers.foam.place(2, 3);
    for (JournalSeaLayers.Band band :
        new JournalSeaLayers.Band[] {layers.far, layers.mid, layers.near, layers.foam})
      canvas.drawRect(0, 0, 378, 166, band.paint);
    new JournalSeaLayers().draw(canvas, pack, null, 0, new RectF(0, 0, 378, 166), 2, true);
    assertEquals(count, canvas.getSaveCount());
  }
}
