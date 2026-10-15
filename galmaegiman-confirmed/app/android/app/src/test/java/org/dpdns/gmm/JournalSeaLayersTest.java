package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

/** 실제 분리 수면의 화면 배율·정적 표시·전환·긴 실행 UV 연속성을 Skia로 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JournalSeaLayersTest {
  JournalScenePack scene(LocalTimeTheme.Phase phase, JournalScenePack.Material material) {
    return JournalScenePack.create(
        JournalScenePackTest.bitmap(945, 416, phase.ordinal() * 20), material, phase);
  }

  JournalScenePack.Material material() {
    return JournalScenePack.Material.create(JournalScenePackTest.bitmap(256, 128, 65));
  }

  Bitmap render(
      JournalScenePack first,
      JournalScenePack incoming,
      float fade,
      int width,
      int height,
      double time,
      boolean moving) {
    return renderWith(new JournalSeaLayers(), first, incoming, fade, width, height, time, moving);
  }

  Bitmap renderWith(
      JournalSeaLayers renderer,
      JournalScenePack first,
      JournalScenePack incoming,
      float fade,
      int width,
      int height,
      double time,
      boolean moving) {
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    RectF bounds = new RectF();
    JournalAmbient.coverBounds(bounds, width, height);
    canvas.drawBitmap(first.backdrop, null, bounds, paint);
    if (incoming != null) {
      paint.setAlpha(Math.round(fade * 255));
      canvas.drawBitmap(incoming.backdrop, null, bounds, paint);
    }
    int saves = canvas.getSaveCount();
    renderer.draw(canvas, first, incoming, fade, bounds, time, moving);
    assertEquals(saves, canvas.getSaveCount());
    return image;
  }

  Region screenWater(JournalScenePack pack, int width, int height) {
    RectF bounds = new RectF();
    JournalAmbient.coverBounds(bounds, width, height);
    Path path = new Path(pack.sourceWaterClip);
    Matrix matrix = new Matrix();
    matrix.setScale(
        bounds.width() / JournalAmbient.SOURCE_WIDTH,
        bounds.height() / JournalAmbient.SOURCE_HEIGHT);
    matrix.postTranslate(bounds.left, bounds.top);
    path.transform(matrix);
    Region region = new Region();
    region.setPath(path, new Region(0, 0, width, height));
    return region;
  }

  @Test
  public void generatedWaterActuallyMovesWhileScaledFixedStructuresRemainPixelExact() {
    JournalScenePack pack = scene(LocalTimeTheme.Phase.DAY, material());
    JournalScenePack flat = JournalScenePack.flat(pack.backdrop, pack.phase);
    for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
      int width = size[0], height = size[1];
      Bitmap base = render(flat, null, 0, width, height, 0, true),
          first = render(pack, null, 0, width, height, 0, true),
          later = render(pack, null, 0, width, height, 2, true);
      Region water = screenWater(pack, width, height);
      int[] original = new int[width * height],
          before = new int[original.length],
          after = new int[original.length];
      base.getPixels(original, 0, width, 0, 0, width, height);
      first.getPixels(before, 0, width, 0, 0, width, height);
      later.getPixels(after, 0, width, 0, 0, width, height);
      int outside = 0, moved = 0, replaced = 0;
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
          int at = y * width + x;
          if (!water.contains(x, y)) {
            if (original[at] != after[at]) outside++;
          } else {
            if (before[at] != after[at]) moved++;
            if (original[at] != before[at]) replaced++;
          }
        }
      assertEquals("축소/확대 화면의 고정물과 수면 밖 픽셀 보존", 0, outside);
      assertTrue("원본 정적 반사 무늬를 새 물 재질로 대체", replaced > 500);
      assertTrue("폰/태블릿에서 수면 자체가 충분히 진행함: " + moved, moved > 500);
      base.recycle();
      first.recycle();
      later.recycle();
    }
  }

  @Test
  public void reducedMotionKeepsTheSameLayeredMaterialStaticAndFlatTransitionsHaveExactEndpoints() {
    JournalScenePack.Material material = material();
    JournalScenePack day = scene(LocalTimeTheme.Phase.DAY, material),
        night = scene(LocalTimeTheme.Phase.NIGHT, material);
    Bitmap first = render(day, null, 0, 915, 412, 0, false),
        later = render(day, null, 0, 915, 412, 40, false),
        movingZero = render(day, null, 0, 915, 412, 0, true);
    assertTrue(first.sameAs(later));
    assertTrue("움직임 설정이 물 재질/색을 바꾸지 않음", first.sameAs(movingZero));
    JournalScenePack flat = JournalScenePack.flat(night.backdrop, night.phase);
    for (JournalScenePack[] pair :
        new JournalScenePack[][] {{day, night}, {night, day}, {day, flat}, {flat, day}}) {
      Bitmap start = render(pair[0], pair[1], 0, 915, 412, 2, true),
          current = render(pair[0], null, 0, 915, 412, 2, true);
      Bitmap end = render(pair[0], pair[1], 1, 915, 412, 2, true),
          target = render(pair[1], null, 0, 915, 412, 2, true);
      assertTrue("레이어/정적 fallback 전환 시작은 현재와 일치", start.sameAs(current));
      assertTrue("레이어/정적 fallback 전환 끝은 대상과 일치", end.sameAs(target));
      start.recycle();
      current.recycle();
      end.recycle();
      target.recycle();
    }
    first.recycle();
    later.recycle();
    movingZero.recycle();
  }

  @Test
  public void bothMaskExpansionAndContractionBlendCompletedScenesInsideTheirUnion() {
    JournalScenePack.Material material = material();
    JournalScenePack day = scene(LocalTimeTheme.Phase.DAY, material),
        night = scene(LocalTimeTheme.Phase.NIGHT, material);
    Region dayWater = JournalSeaMask.region(1890, 832, LocalTimeTheme.Phase.DAY);
    dayWater.op(JournalSeaMask.region(1890, 832, LocalTimeTheme.Phase.NIGHT), Region.Op.DIFFERENCE);
    assertFalse("밤 고정 발광은 day에서 드러나는 물 일부를 가림", dayWater.isEmpty());
    for (JournalScenePack[] pair : new JournalScenePack[][] {{day, night}, {night, day}}) {
      Bitmap current = render(pair[0], null, 0, 915, 412, 2, true),
          target = render(pair[1], null, 0, 915, 412, 2, true);
      Bitmap halfway = render(pair[0], pair[1], .5f, 915, 412, 2, true);
      int max = 0;
      for (int y = 0; y < 412; y++)
        for (int x = 0; x < 915; x++) {
          int a = current.getPixel(x, y),
              b = target.getPixel(x, y),
              actual = halfway.getPixel(x, y);
          for (int shift = 16; shift >= 0; shift -= 8) {
            int expected =
                Math.round((((a >>> shift) & 255) * 127 + ((b >>> shift) & 255) * 128) / 255f);
            max = Math.max(max, Math.abs(expected - ((actual >>> shift) & 255)));
          }
        }
      assertTrue("상이한 시간대의 가림/수면 영역을 완성 장면으로 절반씩 합성: " + max, max <= 2);
      current.recycle();
      target.recycle();
      halfway.recycle();
    }
  }

  void saveDiagnostic(Bitmap image, java.io.File path) throws Exception {
    assertTrue(path.getParentFile().isDirectory() || path.getParentFile().mkdirs());
    try (java.io.OutputStream output = new java.io.FileOutputStream(path)) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
  }

  @Test
  public void actualMaterialAndVectorFrontsCanBeReviewedAtTheSameFrame() throws Exception {
    android.content.res.AssetManager assets =
        org.robolectric.RuntimeEnvironment.getApplication().getAssets();
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 2;
    Bitmap source, surface;
    try (java.io.InputStream input = assets.open("native/journal/c-home-day.webp")) {
      source = BitmapFactory.decodeStream(input, null, options);
    }
    try (java.io.InputStream input = assets.open("native/journal/ocean-surface.webp")) {
      surface = BitmapFactory.decodeStream(input, null, options);
    }
    JournalScenePack pack =
        JournalScenePack.create(
            source, JournalScenePack.Material.create(surface), LocalTimeTheme.Phase.DAY);
    assertTrue(pack.layered());
    Bitmap all = render(pack, null, 0, 915, 412, 0, true);
    JournalSeaLayers materialOnly = new JournalSeaLayers();
    // 제품에 debug 상태를 추가하지 않고 동일한 renderer의 벡터 stroke만 진단용으로 제거한다.
    for (String name : new String[] {"crestPaint", "troughPaint"}) {
      java.lang.reflect.Field field = JournalSeaLayers.class.getDeclaredField(name);
      field.setAccessible(true);
      ((Paint) field.get(materialOnly)).setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST));
    }
    Bitmap withoutFronts = renderWith(materialOnly, pack, null, 0, 915, 412, 0, true);
    Bitmap later = renderWith(materialOnly, pack, null, 0, 915, 412, 2, true);
    int vectorChanges = 0, textureChanges = 0;
    for (int y = 0; y < 412; y++)
      for (int x = 0; x < 915; x++) {
        if (all.getPixel(x, y) != withoutFronts.getPixel(x, y)) vectorChanges++;
        if (withoutFronts.getPixel(x, y) != later.getPixel(x, y)) textureChanges++;
      }
    assertTrue("독립 마루/그늘/포말 stroke가 실제로 그려짐", vectorChanges > 10);
    assertTrue("벡터를 꺼도 생성된 물 재질 자체가 움직임", textureChanges > 500);
    java.io.File output =
        new java.io.File(
            System.getProperty("gmm.nativeReviewOutput", System.getProperty("java.io.tmpdir")),
            "layer-composition-diagnostic");
    saveDiagnostic(all, new java.io.File(output, "day-t0-all-layers.png"));
    saveDiagnostic(withoutFronts, new java.io.File(output, "day-t0-material-only.png"));
    saveDiagnostic(later, new java.io.File(output, "day-t2-material-only.png"));
    RectF bounds = new RectF();
    JournalAmbient.coverBounds(bounds, 915, 412);
    for (int[] point : new int[][] {{750, 480}, {780, 485}, {800, 490}, {820, 480}, {830, 490}}) {
      int x = Math.round(bounds.left + point[0] * bounds.width() / JournalAmbient.SOURCE_WIDTH);
      int y = Math.round(bounds.top + point[1] * bounds.height() / JournalAmbient.SOURCE_HEIGHT);
      System.out.println(
          "SEA_PATCH "
              + point[0]
              + ","
              + point[1]
              + " all="
              + Integer.toHexString(all.getPixel(x, y))
              + " material="
              + Integer.toHexString(withoutFronts.getPixel(x, y)));
    }
  }

  Bitmap smoothMaterial() {
    int[] pixels = new int[256 * 128];
    for (int y = 0; y < 128; y++)
      for (int x = 0; x < 256; x++) pixels[y * 256 + x] = Color.rgb(x, y * 2, 128);
    return Bitmap.createBitmap(pixels, 256, 128, Bitmap.Config.ARGB_8888);
  }

  @Test
  public void nonSeamlessImageEdgesMirrorContinuouslyInsteadOfRepeatingWithAColorJump() {
    Bitmap ramp = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < 64; y++)
      for (int x = 0; x < 256; x++) ramp.setPixel(x, y, Color.rgb(x, x, x));
    for (int depth = 0; depth < 4; depth++) {
      JournalSeaLayers.Band band = new JournalSeaLayers.Band(ramp, null, 0, 0, false);
      double speed = depth == 0 ? 3 : depth == 1 ? 10 : depth == 2 ? 19 : 25;
      double seam = (1064 - 420) / speed;
      Bitmap before = band(band, seam - .001, depth), after = band(band, seam + .001, depth);
      int at = 64;
      assertTrue(
          "좌우 이미지 끝의 RGB 단절이 수면으로 들어오지 않음",
          Math.abs(Color.red(before.getPixel(at, 20)) - Color.red(after.getPixel(at, 20))) <= 2);
      before.recycle();
      after.recycle();
    }
  }

  Bitmap band(JournalSeaLayers.Band band, double time, int depth) {
    Bitmap result = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    canvas.translate(-1000, -470);
    band.place(time, depth);
    canvas.drawRect(1000, 470, 1128, 534, band.paint);
    return result;
  }

  @Test
  public void oldThirtySixFortySixAndNinetyTwoSecondWrapBoundariesDoNotJump() {
    Bitmap smooth = smoothMaterial();
    JournalSeaLayers.Band[] bands = new JournalSeaLayers.Band[4];
    for (int i = 0; i < 4; i++) bands[i] = new JournalSeaLayers.Band(smooth, null, 0, 0, false);
    for (int depth = 1; depth < 4; depth++)
      for (double time : new double[] {506 / 14d, 506 / 11d, 506 / 5.5d}) {
        Bitmap before = band(bands[depth], time - .001, depth),
            after = band(bands[depth], time + .001, depth);
        int max = 0;
        for (int y = 0; y < 64; y++)
          for (int x = 0; x < 128; x++) {
            int a = before.getPixel(x, y), b = after.getPixel(x, y);
            max =
                Math.max(
                    max,
                    Math.max(
                        Math.abs(Color.red(a) - Color.red(b)),
                        Math.max(
                            Math.abs(Color.green(a) - Color.green(b)),
                            Math.abs(Color.blue(a) - Color.blue(b)))));
          }
        assertTrue("고정 506px 나머지/스큐로 생기던 UV 순간 이동 제거: " + max, max <= 4);
        before.recycle();
        after.recycle();
      }
  }
}
