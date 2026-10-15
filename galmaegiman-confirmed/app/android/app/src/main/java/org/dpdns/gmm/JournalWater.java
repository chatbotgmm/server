package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;

/** 항구 수면만 원근에 따라 굴절한다. 비트맵·시계·작업 큐는 소유하지 않는다. */
final class JournalWater {
  static final int WIDTH = JournalAmbient.SOURCE_WIDTH, HEIGHT = JournalAmbient.SOURCE_HEIGHT;
  static final int COLUMNS = 36, ROWS = 52;
  // 네 시간대 원본을 함께 확인한 공통 수면. 다리·배·탑·부두·밧줄은 폴리곤 사이에 남긴다.
  static final float[][] POLYGONS = JournalSeaMask.WATER_POLYGONS;
  private final JournalSeaLayers separated = new JournalSeaLayers();

  private static final int[] CREST_COLORS = {0xffffebca, 0xffe6fcff, 0xffffd7a8, 0xffb7def2};
  private static final int[] DEPTH_COLORS = {0xff536f76, 0xff236b7f, 0xff70576a, 0xff09283e};
  private final Path mask = new Path();
  private final Path crest = new Path(), trough = new Path(), foam = new Path();
  private final Paint surface = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Paint bright = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Mesh mesh = new Mesh();

  JournalWater() {
    // 정수 source 픽셀 경계로 한 번 정규화한다. 비스듬한 clip의 반투명 가장자리가
    // 수면 밖 구조물 픽셀에 섞이지 않으며 hardware Canvas에서도 Path로 자를 수 있다.
    Region pixels = JournalSeaMask.region(WIDTH, HEIGHT);
    pixels.getBoundaryPath(mask);
    bright.setStyle(Paint.Style.STROKE);
    bright.setStrokeCap(Paint.Cap.ROUND);
    bright.setStrokeJoin(Paint.Join.ROUND);
    dark.setStyle(Paint.Style.STROKE);
    dark.setStrokeCap(Paint.Cap.ROUND);
  }

  void drawScenes(
      Canvas canvas,
      JournalScenePack current,
      JournalScenePack incoming,
      float transition,
      RectF bounds,
      double seconds,
      boolean moving) {
    separated.draw(canvas, current, incoming, transition, bounds, seconds, moving);
  }

  /** 이미 그린 배경의 물만 같은 원본으로 다시 그려 실제 질감이 흐르게 한다. */
  void draw(
      Canvas canvas,
      Bitmap current,
      Bitmap incoming,
      float transition,
      RectF bounds,
      LocalTimeTheme.Phase phase,
      double seconds,
      boolean moving) {
    canvas.save();
    canvas.translate(bounds.left, bounds.top);
    canvas.scale(bounds.width() / WIDTH, bounds.height() / HEIGHT);
    canvas.clipPath(mask);
    mesh.update(seconds, moving);
    if (moving) {
      // 전환 끝점은 한 원본만 그려 mesh coverage에 이전 장면이 조금 남지 않게 한다.
      Bitmap first = incoming != null && transition >= 1 ? incoming : current;
      surface.setAlpha(255);
      canvas.drawBitmapMesh(first, COLUMNS, ROWS, mesh.vertices, 0, null, 0, surface);
      if (incoming != null && transition > 0 && transition < 1) {
        surface.setAlpha(Math.round(255 * transition));
        canvas.drawBitmapMesh(incoming, COLUMNS, ROWS, mesh.vertices, 0, null, 0, surface);
        surface.setAlpha(255);
      }
    }
    drawWavefronts(canvas, phase, moving ? seconds : 0);
    canvas.restore();
  }

  private void drawWavefronts(Canvas canvas, LocalTimeTheme.Phase phase, double seconds) {
    bright.setColor(CREST_COLORS[phase.ordinal()]);
    dark.setColor(DEPTH_COLORS[phase.ordinal()]);
    // 먼 파도는 짧고 촘촘하게, 가까울수록 넓은 마루와 그 아래 그늘로 두께를 만든다.
    for (int row = 0; row < 13; row++) {
      float depth = row / 12f;
      float y = 354 + 178 * depth * depth;
      float amplitude = .65f + depth * 2.6f;
      float wavelength = 40 + depth * 86;
      double travel = seconds * (1.05 + depth * .55) + row * 1.73;
      crest.rewind();
      trough.rewind();
      for (int x = 680; x <= 1420; x += 20) {
        float ripple =
            (float)
                (Math.sin(x * Math.PI * 2 / wavelength - travel) * amplitude
                    + Math.sin(x * .025 + travel * .61) * amplitude * .32);
        if (x == 680) {
          crest.moveTo(x, y + ripple);
          trough.moveTo(x, y + ripple + 1.8f + depth * 2.3f);
        } else {
          crest.lineTo(x, y + ripple);
          trough.lineTo(x, y + ripple + 1.8f + depth * 2.3f);
        }
      }
      dark.setStrokeWidth(1.6f + depth * 3.5f);
      dark.setAlpha(8 + Math.round(depth * 15));
      canvas.drawPath(trough, dark);
      bright.setStrokeWidth(.65f + depth * 1.65f);
      bright.setAlpha((phase == LocalTimeTheme.Phase.NIGHT ? 11 : 16) + Math.round(depth * 15));
      canvas.drawPath(crest, bright);
    }
    // 맨 앞 물길의 작고 끊긴 포말. 같은 속도로 통째로 미끄러지지 않도록 깊이마다 속도를 다르게 한다.
    for (int i = 0; i < 19; i++) {
      float y = 410 + (i * 37 % 117);
      float depth = Math.max(0, Math.min(1, (y - 354) / 178f));
      float x = (float) (704 + ((i * 83 + seconds * (5 + depth * 11)) % 682));
      float rise = (float) Math.sin(seconds * 1.2 + i * 1.7) * (1 + depth * 1.8f);
      float length = 4 + depth * 13;
      foam.rewind();
      foam.moveTo(x - length, y + rise + 1.1f);
      foam.quadTo(x, y + rise - 1.5f, x + length * .6f, y + rise);
      bright.setStrokeWidth(.65f + depth * 1.15f);
      bright.setAlpha((phase == LocalTimeTheme.Phase.NIGHT ? 15 : 22) + Math.round(depth * 13));
      canvas.drawPath(foam, bright);
    }
  }

  /** 포인트·저진폭 망은 한 번만 준비하고 매 프레임 물 안의 꼭짓점만 갱신한다. */
  static final class Mesh {
    final float[] base = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
    final float[] vertices = new float[base.length];
    final float[] weights = new float[base.length / 2];
    final int[] active = new int[weights.length];
    int activeCount;

    Mesh() {
      int index = 0;
      for (int row = 0; row <= ROWS; row++) {
        float y = HEIGHT * row / (float) ROWS;
        for (int column = 0; column <= COLUMNS; column++, index++) {
          float x = WIDTH * column / (float) COLUMNS;
          base[index * 2] = vertices[index * 2] = x;
          base[index * 2 + 1] = vertices[index * 2 + 1] = y;
          if (!contains(x, y)) continue;
          weights[index] = Math.min(1, distanceToEdge(x, y) / 7);
          active[activeCount++] = index;
        }
      }
    }

    void update(double seconds, boolean moving) {
      for (int point = 0; point < activeCount; point++) {
        int index = active[point], coordinate = index * 2;
        float x = base[coordinate], y = base[coordinate + 1];
        if (!moving) {
          vertices[coordinate] = x;
          vertices[coordinate + 1] = y;
          continue;
        }
        float depth = Math.max(0, Math.min(1, (y - 350) / 190f));
        float strength = (.28f + depth * .72f) * weights[index];
        vertices[coordinate] =
            x
                + (float)
                    ((Math.sin(x * .038 + y * .021 - seconds * 1.25)
                            + Math.sin(y * .073 + seconds * .69) * .35)
                        * 1.5
                        * strength);
        vertices[coordinate + 1] =
            y
                + (float)
                    ((Math.sin(x * .027 - y * .033 - seconds * 1.37)
                            + Math.sin(x * .066 + seconds * .83) * .3)
                        * 2.1
                        * strength);
      }
    }
  }

  static boolean contains(float x, float y) {
    return JournalSeaMask.contains(x, y);
  }

  private static float distanceToEdge(float x, float y) {
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
