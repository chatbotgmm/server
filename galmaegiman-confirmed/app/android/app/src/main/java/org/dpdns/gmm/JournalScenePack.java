package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import java.util.Arrays;

/** 시간대 한 장면의 분리 자산. 생성은 로더 스레드에서 하고 표시 중에는 recycle하지 않는다. */
final class JournalScenePack {
  final Bitmap backdrop, occluder;
  final RectF sourceCrop;
  final Material material;
  final LocalTimeTheme.Phase phase;
  final JournalSeaLayers.LayerSet layers;
  final Path sourceWaterClip;
  final float[] waterMean;

  /** 모든 시간대가 같은 수면/포말 자산을 공유한다. 시간대 색은 Shader의 Paint에서 처리한다. */
  static final class Material {
    final Bitmap surface, foam;
    final float[] mean;
    final float meanLuma;

    private Material(Bitmap surface, Bitmap foam, float[] mean) {
      this.surface = surface;
      this.foam = foam;
      this.mean = mean;
      meanLuma = mean[0] * .2126f + mean[1] * .7152f + mean[2] * .0722f;
    }

    static Material create(Bitmap surface) {
      if (surface == null) return null;
      int width = surface.getWidth(), height = surface.getHeight();
      int[] pixels = new int[width * height];
      surface.getPixels(pixels, 0, width, 0, 0, width, height);
      long red = 0, green = 0, blue = 0;
      for (int i = 0; i < pixels.length; i++) {
        int color = pixels[i];
        red += (color >>> 16) & 255;
        green += (color >>> 8) & 255;
        blue += color & 255;
        int white = Math.min((color >>> 16) & 255, Math.min((color >>> 8) & 255, color & 255));
        int alpha = Math.max(0, Math.min(255, (white - 145) * 3));
        pixels[i] = (alpha << 24) | 0x00ffffff;
      }
      Bitmap foam = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
      foam.setDensity(Bitmap.DENSITY_NONE);
      surface.setDensity(Bitmap.DENSITY_NONE);
      return new Material(
          surface,
          foam,
          new float[] {
            red / (float) pixels.length, green / (float) pixels.length, blue / (float) pixels.length
          });
    }

    long byteCount() {
      return (long) surface.getAllocationByteCount() + foam.getAllocationByteCount();
    }
  }

  private JournalScenePack(
      Bitmap backdrop,
      Bitmap occluder,
      RectF crop,
      Material material,
      LocalTimeTheme.Phase phase,
      Path clip,
      float[] mean) {
    this.backdrop = backdrop;
    this.occluder = occluder;
    sourceCrop = crop;
    this.material = material;
    this.phase = phase;
    sourceWaterClip = clip;
    waterMean = mean;
    layers = material == null ? null : new JournalSeaLayers.LayerSet(material, phase, mean);
  }

  static JournalScenePack flat(Bitmap backdrop, LocalTimeTheme.Phase phase) {
    return backdrop == null
        ? null
        : new JournalScenePack(backdrop, null, new RectF(), null, phase, new Path(), new float[3]);
  }

  static JournalScenePack create(Bitmap backdrop, Material material, LocalTimeTheme.Phase phase) {
    if (backdrop == null) return null;
    if (material == null) return flat(backdrop, phase);
    int width = backdrop.getWidth(), height = backdrop.getHeight();
    Region water = JournalSeaMask.region(width, height, phase);
    Rect crop = water.getBounds();
    if (crop.isEmpty()) return flat(backdrop, phase);
    crop.inset(-2, -2);
    crop.intersect(0, 0, width, height);
    int[] pixels = new int[crop.width() * crop.height()];
    backdrop.getPixels(pixels, 0, crop.width(), crop.left, crop.top, crop.width(), crop.height());
    RegionIterator spans = new RegionIterator(water);
    Rect span = new Rect();
    long red = 0, green = 0, blue = 0, count = 0;
    while (spans.next(span)) {
      for (int y = span.top; y < span.bottom; y++) {
        int start = (y - crop.top) * crop.width() + span.left - crop.left;
        for (int i = start; i < start + span.width(); i++) {
          int color = pixels[i];
          red += (color >>> 16) & 255;
          green += (color >>> 8) & 255;
          blue += color & 255;
          count++;
        }
        Arrays.fill(pixels, start, start + span.width(), 0);
      }
    }
    Bitmap occluder =
        Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888);
    occluder.setDensity(Bitmap.DENSITY_NONE);
    RectF sourceCrop =
        new RectF(
            crop.left * JournalAmbient.SOURCE_WIDTH / (float) width,
            crop.top * JournalAmbient.SOURCE_HEIGHT / (float) height,
            crop.right * JournalAmbient.SOURCE_WIDTH / (float) width,
            crop.bottom * JournalAmbient.SOURCE_HEIGHT / (float) height);
    Path sourceWaterClip = new Path();
    water.getBoundaryPath(sourceWaterClip);
    Matrix inverseScale = new Matrix();
    inverseScale.setScale(
        JournalAmbient.SOURCE_WIDTH / (float) width, JournalAmbient.SOURCE_HEIGHT / (float) height);
    sourceWaterClip.transform(inverseScale);
    float[] mean = {red / (float) count, green / (float) count, blue / (float) count};
    return new JournalScenePack(
        backdrop, occluder, sourceCrop, material, phase, sourceWaterClip, mean);
  }

  boolean layered() {
    return occluder != null && material != null;
  }

  long byteCount() {
    return (long) backdrop.getAllocationByteCount()
        + (occluder == null ? 0 : occluder.getAllocationByteCount());
  }
}
