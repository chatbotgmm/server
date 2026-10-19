package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** 수면 질감은 움직이되 다리·배·지도는 한 픽셀도 바뀌지 않는지 실제 Skia로 검사한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JournalWaterTest {
  final int width = JournalWater.WIDTH, height = JournalWater.HEIGHT;

  Bitmap source(int offset) {
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++)
        pixels[y * width + x] =
            0xff000000
                | ((20 + (x * 17 + y * 3 + offset) % 180) << 16)
                | ((40 + (x + y * 11 + offset) % 180) << 8)
                | (60 + (x * 3 + y * 7 + offset) % 180);
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
  }

  Bitmap render(
      JournalWater water,
      Bitmap first,
      Bitmap incoming,
      float fade,
      double seconds,
      boolean moving) {
    Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(frame);
    canvas.drawBitmap(first, 0, 0, null);
    if (incoming != null) {
      Paint paint = new Paint();
      paint.setAlpha(Math.round(255 * fade));
      canvas.drawBitmap(incoming, 0, 0, paint);
    }
    int saved = canvas.getSaveCount();
    water.draw(
        canvas,
        first,
        incoming,
        fade,
        new RectF(0, 0, width, height),
        LocalTimeTheme.Phase.DAY,
        seconds,
        moving);
    assertEquals("수면 그리기가 다음 캐릭터/UI의 Canvas 변환을 남기지 않음", saved, canvas.getSaveCount());
    return frame;
  }

  Region waterRegion() {
    return JournalSeaMask.region(width, height, LocalTimeTheme.Phase.DAY);
  }

  @Test
  public void movingTextureAndWavefrontsRemainInsideTheMeasuredWaterMask() {
    Bitmap source = source(0);
    JournalWater water = new JournalWater();
    Bitmap first = render(water, source, null, 0, 0, true);
    Bitmap later = render(water, source, null, 0, 1.4, true);
    Region mask = waterRegion();
    int[] before = new int[width * height],
        after = new int[before.length],
        original = new int[before.length];
    first.getPixels(before, 0, width, 0, 0, width, height);
    later.getPixels(after, 0, width, 0, 0, width, height);
    source.getPixels(original, 0, width, 0, 0, width, height);
    int changed = 0, backWaterChanged = 0, outsideChanges = 0;
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int index = y * width + x;
        if (!mask.contains(x, y)) {
          if (original[index] != after[index]) outsideChanges++;
        } else if (before[index] != after[index]) {
          changed++;
          if (y < 443) backWaterChanged++;
        }
      }
    assertEquals("다리·배·성벽·지도 등 물 밖 픽셀 원본 보존", 0, outsideChanges);
    assertTrue("반사선만이 아니라 넓은 수면 질감이 변함: " + changed, changed > 2_000);
    assertTrue("추가한 뒤쪽 수면에도 움직임이 있음: " + backWaterChanged, backWaterChanged > 400);
    source.recycle();
    first.recycle();
    later.recycle();
  }

  @Test
  public void staticMotionIsIndependentOfClockAndCrossfadeEndpointsStayExact() {
    JournalWater water = new JournalWater();
    Bitmap day = source(0), evening = source(37);
    Bitmap staticFirst = render(water, day, null, 0, 0, false);
    Bitmap staticLater = render(water, day, null, 0, 30, false);
    assertTrue("감소 모션·백그라운드·미표시는 시계와 무관한 같은 정적 수면", staticFirst.sameAs(staticLater));
    Bitmap start = render(water, day, evening, 0, 1.4, true);
    Bitmap dayOnly = render(water, day, null, 0, 1.4, true);
    assertTrue("전환 시작의 수면은 현재 배경과 일치", start.sameAs(dayOnly));
    Bitmap end = render(water, day, evening, 1, 1.4, true);
    Bitmap eveningOnly = render(water, evening, null, 0, 1.4, true);
    assertTrue("전환 끝의 수면은 대상 배경과 일치", end.sameAs(eveningOnly));
    for (Bitmap bitmap :
        new Bitmap[] {day, evening, staticFirst, staticLater, start, dayOnly, end, eveningOnly})
      bitmap.recycle();
  }

  @Test
  public void fixedMeshReusesArraysAndLimitsRefractionToLowAmplitudeWaterVertices() {
    JournalWater.Mesh mesh = new JournalWater.Mesh();
    float[] coordinates = mesh.vertices;
    int[] active = mesh.active;
    assertTrue(
        "전체 배경 대신 수면 안의 작은 꼭짓점 집합만 갱신",
        mesh.activeCount > 20 && mesh.activeCount < mesh.base.length / 8);
    for (int frame = 0; frame < 100; frame++) {
      mesh.update(frame / 30d, true);
      assertSame(coordinates, mesh.vertices);
      assertSame(active, mesh.active);
      for (int index = 0; index < mesh.weights.length; index++) {
        int at = index * 2;
        float dx = coordinates[at] - mesh.base[at], dy = coordinates[at + 1] - mesh.base[at + 1];
        assertTrue("수면 원근은 원본 4px보다 작아 육지/구조물 경계를 끌지 않음", Math.hypot(dx, dy) < 4);
        if (!JournalWater.contains(mesh.base[at], mesh.base[at + 1])) {
          assertEquals(0, dx, 0);
          assertEquals(0, dy, 0);
        }
      }
    }
    mesh.update(10, false);
    assertArrayEquals("중지 시 원본 망으로 복원", mesh.base, mesh.vertices, 0);
  }

  @Test
  public void maskIncludesRearWaterAndKeepsForegroundAndHarborStructuresExcluded() {
    for (LocalTimeTheme.Phase phase : LocalTimeTheme.Phase.values()) {
      for (float[] point :
          new float[][] {
            {710, 465},
            {715, 490},
            {805, 491},
            {810, 359},
            {900, 360},
            {998, 365},
            {1073, 367},
            {1005, 571}
          })
        assertTrue("원본 검수에서 실제 수면으로 확인한 좌표", JournalSeaMask.contains(point[0], point[1], phase));
      for (float[] point :
          new float[][] {
            {755, 398},
            {629, 399},
            {684, 366},
            {760, 366},
            {855, 390},
            {959, 391},
            {761, 430},
            {659, 370},
            {818, 370},
            {829, 367},
            {894, 372},
            {922, 377},
            {985, 418},
            {1008, 425},
            {999, 384},
            {1038, 420},
            {1019, 420},
            {1070, 386},
            {1115, 386},
            {1159, 380},
            {1190, 394},
            {1068, 414},
            {1084, 413},
            {1097, 414},
            {1096, 424},
            {997, 458},
            {681, 430},
            {679, 468},
            {884, 475},
            {743, 484},
            {778, 475},
            {774, 490},
            {1065, 536},
            {1185, 526},
            {1015, 550},
            {1128, 547},
            {1410, 486},
            {1382, 511},
            {1400, 423},
            {1510, 453}
          }) assertFalse("원본 다리·배·부표·밧줄·목주 보존", JournalSeaMask.contains(point[0], point[1], phase));
    }
    assertFalse(
        "밤 등대의 유리 주변 고정 발광 보존", JournalSeaMask.contains(1030, 387, LocalTimeTheme.Phase.NIGHT));
  }
}
