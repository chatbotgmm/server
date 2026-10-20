package org.dpdns.gmm;

import android.graphics.*;
import android.os.SystemClock;

/** 불투명 원본 패치를 같은 자리에서 덮어 그린다. 분리 배/clean plate/이중 배를 만들지 않는다. */
final class HarborBoatMotion {
  private Bitmap patch, original;
  private long started;
  private boolean failed;
  private final float[] vertices = new float[(HarborBoatGeometry.COLS+1)*(HarborBoatGeometry.ROWS+1)*2];
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  void resetClock() { started=0; }
  void clear() { patch=null; original=null; failed=false; started=0; }
  void draw(Canvas canvas, Bitmap image, RectF target, boolean moving) {
    if (!moving) { resetClock(); return; }
    if (failed || image==null || image.getWidth()!=1672 || image.getHeight()!=941) return;
    if (original!=image) {
      patch=null;
      try { patch=Bitmap.createBitmap(image,HarborBoatGeometry.LEFT,HarborBoatGeometry.TOP,HarborBoatGeometry.WIDTH,HarborBoatGeometry.HEIGHT); }
      catch (RuntimeException | OutOfMemoryError error) { failed=true; return; }
      original=image;
    }
    long now=SystemClock.elapsedRealtime();
    if(started==0) started=now;
    HarborBoatGeometry.vertices(vertices,(now-started)/1000d);
    int save=canvas.save();
    canvas.translate(target.left,target.top);
    canvas.scale(target.width()/1672f,target.height()/941f);
    canvas.translate(HarborBoatGeometry.LEFT,HarborBoatGeometry.TOP);
    canvas.clipRect(0,0,HarborBoatGeometry.WIDTH,HarborBoatGeometry.HEIGHT);
    canvas.drawBitmapMesh(patch,HarborBoatGeometry.COLS,HarborBoatGeometry.ROWS,vertices,0,null,0,paint);
    canvas.restoreToCount(save);
  }
}
