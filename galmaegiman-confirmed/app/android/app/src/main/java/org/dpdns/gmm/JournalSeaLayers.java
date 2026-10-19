package org.dpdns.gmm;

import android.graphics.*;

/** 멀리 흐르는 물·중경 파도·가까운 포말 위에 원본 구조물을 다시 올리는 실제 분리 장면. */
final class JournalSeaLayers {
  private final Paint fixed = new Paint();
  private final Paint crestPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
      troughPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path crest = new Path(), trough = new Path(), foam = new Path();
  private final Paint backdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final RectF unionCrop = new RectF();
  private final Path unionWater = new Path(), currentWater = new Path(), incomingWater = new Path();
  private final Region currentPixels = new Region(),
      incomingPixels = new Region(),
      unionPixels = new Region(),
      pixelBounds = new Region();
  private final Matrix screenTransform = new Matrix();
  private final RectF preparedBounds = new RectF(), screenUnionCrop = new RectF();
  private Path preparedCurrentClip, preparedIncomingClip;

  JournalSeaLayers() {
    crestPaint.setStyle(Paint.Style.STROKE);
    crestPaint.setStrokeCap(Paint.Cap.ROUND);
    crestPaint.setStrokeJoin(Paint.Join.ROUND);
    troughPaint.setStyle(Paint.Style.STROKE);
    troughPaint.setStrokeCap(Paint.Cap.ROUND);
    troughPaint.setStrokeJoin(Paint.Join.ROUND);
  }

  /** Shader·행렬은 ScenePack을 준비할 때 한 번만 만든다. */
  static final class LayerSet {
    final Band far, mid, near, foam;
    final int crestColor, troughColor;

    LayerSet(JournalScenePack.Material material, LocalTimeTheme.Phase phase, float[] mean) {
      float saturation =
          switch (phase) {
            case MORNING -> .12f;
            case DAY -> .35f;
            case EVENING -> .08f;
            case NIGHT -> .05f;
          };
      float contrast =
          switch (phase) {
            case MORNING -> .90f;
            case DAY -> .65f;
            case EVENING -> .75f;
            case NIGHT -> .60f;
          };
      float[] values = new float[20], luma = {.2126f, .7152f, .0722f};
      for (int row = 0; row < 3; row++) {
        for (int column = 0; column < 3; column++)
          values[row * 5 + column] = contrast * luma[column] + (row == column ? saturation : 0);
        values[row * 5 + 4] =
            mean[row] - saturation * material.mean[row] - contrast * material.meanLuma;
      }
      values[18] = 1;
      ColorMatrixColorFilter tint = new ColorMatrixColorFilter(values);
      far = new Band(material.surface, tint, 0, 0, false);
      mid = new Band(material.surface, tint, 405, 454, false);
      near = new Band(material.surface, tint, 463, 516, false);
      crestColor =
          switch (phase) {
            case MORNING -> 0xffffe0a9;
            case DAY -> 0xffe7fbff;
            case EVENING -> 0xffffbb83;
            case NIGHT -> 0xff9cc7e7;
          };
      troughColor = phase == LocalTimeTheme.Phase.NIGHT ? 0xff05182a : 0xff174e69;
      foam =
          new Band(
              material.foam,
              new PorterDuffColorFilter(crestColor, PorterDuff.Mode.SRC_IN),
              449,
              516,
              true);
    }
  }

  static final class Band {
    final BitmapShader texture;
    final Matrix transform = new Matrix();
    final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    final int width, height;

    Band(Bitmap bitmap, ColorFilter tint, float fadeTop, float fadeBottom, boolean foam) {
      width = bitmap.getWidth();
      height = bitmap.getHeight();
      texture = new BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
      if (fadeTop == fadeBottom) paint.setShader(texture);
      else {
        LinearGradient depth =
            new LinearGradient(
                0, fadeTop, 0, fadeBottom, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(texture, depth, PorterDuff.Mode.DST_IN));
      }
      paint.setColorFilter(tint);
      if (foam) paint.setAlpha(105);
    }

    void place(double seconds, int depth) {
      float speed = depth == 0 ? 3 : depth == 1 ? 10 : depth == 2 ? 19 : 25;
      float stretch =
          1
              + (float) Math.sin(seconds * (depth == 0 ? .42 : .71) + depth)
                  * (.012f + depth * .012f);
      float shiftX = (float) ((seconds * speed) % (1195 * 2));
      float shiftY =
          (float)
              ((seconds * (depth == 0 ? 1.1 : depth == 1 ? 5.5 : depth == 2 ? 11 : 14))
                  % (253 * stretch * 2));
      transform.setScale(1195f / width, 253f / height * stretch);
      transform.postTranslate(420 + shiftX, 332 + shiftY);
      texture.setLocalMatrix(transform);
    }
  }

  void draw(
      Canvas canvas,
      JournalScenePack current,
      JournalScenePack incoming,
      float progress,
      RectF bounds,
      double seconds,
      boolean moving) {
    if (current == null) return;
    double time = moving ? seconds : 0;
    JournalScenePack first = progress >= 1 && incoming != null ? incoming : current;
    JournalScenePack second = progress > 0 && progress < 1 ? incoming : null;
    prepare(first, second, bounds);
    if (unionCrop.isEmpty()) return;
    int save = canvas.save();
    // 축소/확대 뒤 실제 화면 픽셀 경계로 정규화한다. source 정수 경계도 배율이
    // 적용되면 반투명 clip edge가 생기므로 구조물 보존/전환에 같은 raster를 쓴다.
    canvas.clipPath(unionWater);
    drawScene(canvas, first, currentWater, bounds, time);
    if (second != null) {
      int fade = canvas.saveLayerAlpha(screenUnionCrop, Math.round(progress * 255));
      drawScene(canvas, second, incomingWater, bounds, time);
      canvas.restoreToCount(fade);
    }
    canvas.restoreToCount(save);
  }

  private void prepare(JournalScenePack first, JournalScenePack second, RectF bounds) {
    Path firstClip = first == null ? null : first.sourceWaterClip;
    Path secondClip = second == null ? null : second.sourceWaterClip;
    if (preparedCurrentClip == firstClip
        && preparedIncomingClip == secondClip
        && preparedBounds.equals(bounds)) return;
    // Path만 캐시해 미표시/취소된 ScenePack의 Bitmap 수명을 늘리지 않는다.
    preparedCurrentClip = firstClip;
    preparedIncomingClip = secondClip;
    preparedBounds.set(bounds);
    screenTransform.setScale(
        bounds.width() / JournalAmbient.SOURCE_WIDTH,
        bounds.height() / JournalAmbient.SOURCE_HEIGHT);
    screenTransform.postTranslate(bounds.left, bounds.top);
    pixelBounds.set(
        (int) Math.floor(bounds.left),
        (int) Math.floor(bounds.top),
        (int) Math.ceil(bounds.right),
        (int) Math.ceil(bounds.bottom));
    unionCrop.setEmpty();
    raster(first, currentWater, currentPixels);
    raster(second, incomingWater, incomingPixels);
    unionPixels.set(currentPixels);
    unionPixels.op(incomingPixels, Region.Op.UNION);
    unionPixels.getBoundaryPath(unionWater);
    if (first != null && first.layered()) unionCrop.set(first.sourceCrop);
    if (second != null && second.layered()) unionCrop.union(second.sourceCrop);
    screenUnionCrop.set(unionCrop);
    screenTransform.mapRect(screenUnionCrop);
  }

  private void raster(JournalScenePack scene, Path output, Region pixels) {
    output.rewind();
    pixels.setEmpty();
    if (scene == null || !scene.layered()) return;
    output.set(scene.sourceWaterClip);
    output.transform(screenTransform);
    pixels.setPath(output, pixelBounds);
    pixels.getBoundaryPath(output);
  }

  private void drawScene(
      Canvas canvas, JournalScenePack scene, Path waterClip, RectF bounds, double seconds) {
    canvas.drawBitmap(scene.backdrop, null, bounds, backdropPaint);
    if (!scene.layered()) return;
    int save = canvas.save();
    canvas.clipPath(waterClip);
    canvas.translate(bounds.left, bounds.top);
    canvas.scale(
        bounds.width() / JournalAmbient.SOURCE_WIDTH,
        bounds.height() / JournalAmbient.SOURCE_HEIGHT);
    LayerSet layers = scene.layers;
    layers.far.place(seconds, 0);
    canvas.drawRect(scene.sourceCrop, layers.far.paint);
    layers.mid.place(seconds, 1);
    canvas.drawRect(scene.sourceCrop, layers.mid.paint);
    layers.near.place(seconds, 2);
    canvas.drawRect(scene.sourceCrop, layers.near.paint);
    drawRollingFronts(canvas, layers, seconds);
    layers.foam.place(seconds, 3);
    canvas.drawRect(scene.sourceCrop, layers.foam.paint);
    // 원본에서 물만 투명화한 RGBA. 구조물 RGB와 좌표는 변형하지 않는다.
    canvas.drawBitmap(scene.occluder, null, scene.sourceCrop, fixed);
    canvas.restoreToCount(save);
  }

  private void drawRollingFronts(Canvas canvas, LayerSet layers, double seconds) {
    crestPaint.setColor(layers.crestColor);
    troughPaint.setColor(layers.troughColor);
    for (int wave = 0; wave < 5; wave++) {
      float progress = (float) (((seconds + wave * 1.34) % 6.7) / 6.7);
      float depth = progress * progress;
      float y = 390 + depth * 165;
      float strength = (float) Math.sin(progress * Math.PI);
      crest.rewind();
      trough.rewind();
      for (int x = 650; x <= 1450; x += 8) {
        float roll =
            (float) Math.sin(x * (.038 - depth * .016) - seconds * .8 + wave) * (.8f + depth * 6);
        if (x == 650) {
          crest.moveTo(x, y + roll);
          trough.moveTo(x, y + roll + 3 + depth * 10);
        } else {
          crest.lineTo(x, y + roll);
          trough.lineTo(x, y + roll + 3 + depth * 10);
        }
      }
      troughPaint.setStrokeWidth(2 + depth * 9);
      troughPaint.setAlpha(Math.round((14 + depth * 36) * strength));
      canvas.drawPath(trough, troughPaint);
      crestPaint.setStrokeWidth(.7f + depth * 4);
      crestPaint.setAlpha(Math.round((24 + depth * 75) * strength));
      canvas.drawPath(crest, crestPaint);
      if (progress > .52f) {
        foam.rewind();
        for (int fragment = 0; fragment < 12; fragment++) {
          float x = 704 + fragment * 59 + (float) Math.sin(seconds * .65 + fragment) * 10;
          float roll =
              (float) Math.sin(x * (.038 - depth * .016) - seconds * .8 + wave) * (.8f + depth * 6);
          foam.moveTo(x, y + roll);
          foam.quadTo(x + 9, y + roll - 2 - depth * 3, x + 19 + depth * 11, y + roll + 1);
        }
        crestPaint.setStrokeWidth(1 + depth * 2.2f);
        crestPaint.setAlpha(
            Math.round(105 * strength * depth * Math.min(1, (progress - .52f) / .1f)));
        canvas.drawPath(foam, crestPaint);
      }
    }
  }
}
