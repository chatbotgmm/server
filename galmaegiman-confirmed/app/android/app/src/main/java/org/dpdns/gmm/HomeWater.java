package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Restrained, visible water refraction for the approved release home artwork only. */
final class HomeWater {
  static final int WIDTH = 1870, HEIGHT = 841, COLUMNS = 72, ROWS = 36;
  // Source-pixel water interiors, checked against bg-home.png. Margins deliberately
  // exclude sails, boats, shoreline, lamps, bollards, ropes, barrels and paving.
  static final float[][] POLYGONS = {
    {1365, 328, 1694, 328, 1705, 342, 1643, 348, 1626, 382, 1630, 414,
      1584, 441, 1554, 459, 1535, 450, 1414, 447, 1407, 471, 1372, 476,
      1365, 497, 1355, 503, 1322, 514, 1270, 503, 1241, 494, 1241, 479,
      1224, 470, 1255, 452, 1268, 427, 1279, 406, 1358, 397},
    {1022, 488, 1090, 484, 1139, 475, 1142, 521, 1101, 524, 1084, 548,
      1025, 549, 1022, 532},
    {925, 485, 970, 485, 970, 495, 944, 509, 925, 509},
    {1241, 544, 1292, 549, 1341, 541, 1364, 535, 1364, 565, 1241, 566},
    {1541, 478, 1567, 461, 1577, 462, 1576, 489, 1554, 492, 1541, 487}
  };
  private final Path mask = new Path();
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  final float[] base = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
  final float[] vertices = new float[base.length];
  private final int[] active = new int[base.length / 2];
  private final float[] weights = new float[active.length];
  int activeCount;

  HomeWater() {
    for (float[] polygon : POLYGONS) {
      mask.moveTo(polygon[0], polygon[1]);
      for (int i = 2; i < polygon.length; i += 2) mask.lineTo(polygon[i], polygon[i + 1]);
      mask.close();
    }
    int index = 0;
    for (int row = 0; row <= ROWS; row++)
      for (int column = 0; column <= COLUMNS; column++, index++) {
        float x = WIDTH * column / (float) COLUMNS, y = HEIGHT * row / (float) ROWS;
        base[index * 2] = vertices[index * 2] = x;
        base[index * 2 + 1] = vertices[index * 2 + 1] = y;
        if (!contains(x, y)) continue;
        active[activeCount++] = index;
        weights[index] = Math.min(1, edgeDistance(x, y) / 10f);
      }
  }

  void draw(Canvas canvas, Bitmap bitmap, RectF bounds, double seconds, boolean moving) {
    if (!moving || bitmap == null) return;
    // Source motion of 4–6px becomes roughly 2–3px on a landscape phone.
    // The old subpixel output disappeared after downscaling under menu artwork.
    // Keep the original mask and edge taper: architecture and shore never move.
    update(seconds);
    int saved = canvas.save();
    canvas.translate(bounds.left, bounds.top);
    canvas.scale(bounds.width() / WIDTH, bounds.height() / HEIGHT);
    canvas.clipPath(mask);
    canvas.drawBitmapMesh(bitmap, COLUMNS, ROWS, vertices, 0, null, 0, paint);
    canvas.restoreToCount(saved);
  }

  void update(double seconds) {
    for (int i = 0; i < activeCount; i++) {
      int point = active[i], coordinate = point * 2;
      float x = base[coordinate], y = base[coordinate + 1];
      float depth = Math.max(0, Math.min(1, (y - 325) / 245f));
      float weight = weights[point] * (.35f + depth * .65f);
      vertices[coordinate] = x + (float) (Math.sin(y * .075 - seconds * 1.25)
          + .3 * Math.sin(x * .04 + seconds * .77)) * 4.2f * weight;
      vertices[coordinate + 1] = y + (float) (Math.sin(x * .047 - seconds * 1.45)
          + .25 * Math.sin(y * .05 + seconds * .95)) * 5.2f * weight;
    }
  }

  static boolean contains(float x, float y) {
    for (float[] polygon : POLYGONS) {
      boolean inside = false;
      for (int i = 0, j = polygon.length - 2; i < polygon.length; j = i, i += 2) {
        float xi = polygon[i], yi = polygon[i + 1], xj = polygon[j], yj = polygon[j + 1];
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi)
          inside = !inside;
      }
      if (inside) return true;
    }
    return false;
  }

  private static float edgeDistance(float x, float y) {
    float distance = Float.MAX_VALUE;
    for (float[] polygon : POLYGONS)
      for (int i = 0, j = polygon.length - 2; i < polygon.length; j = i, i += 2) {
        float ax = polygon[j], ay = polygon[j + 1], dx = polygon[i] - ax, dy = polygon[i + 1] - ay;
        float t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        float px = x - ax - t * dx, py = y - ay - t * dy;
        distance = Math.min(distance, (float) Math.sqrt(px * px + py * py));
      }
    return distance;
  }
}

