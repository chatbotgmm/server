package org.dpdns.gmm;

import android.graphics.*;

/** 로비 전용 국소 clean plate + 투명 배. 정적인 원본 배를 남겨 중복하지 않는다. */
final class HomeHarborMotion {
  private final Bitmap plate, boat;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Path repair = new Path(), reflectionClip = new Path();
  private final Rect boatSource = new Rect();
  private final RectF boatTarget = new RectF(855, 279, 1109, 480);
  private final RectF plateTarget = new RectF(833, 258, 1128, 557);
  private final RectF waterline = new RectF();
  private final boolean ready;

  HomeHarborMotion(MainActivity host) {
    // 배와 가려졌던 배후만 복원. 전경 석벽/밧줄/계선주는 원본 픽셀을 유지한다.
    float[] points = {845,270, 1116,270, 1116,545, 1017,545,
        1017,485, 910,485, 910,452, 845,452};
    repair.moveTo(points[0], points[1]);
    for (int i=2;i<points.length;i+=2) repair.lineTo(points[i],points[i+1]);
    repair.close();
    // 한 번만 좁은 영역을 디코드한다. 전체 생성 plate를 GPU에 올리지 않는다.
    plate = repairPlate(host, repair);
    boat = decodeBoat(host);
    ready = plate != null && boat != null && boat.getWidth() > 0 && boat.getHeight() > 0;
    if (boat != null) boatSource.set(Math.round(54 * boat.getWidth()/1410f),
        Math.round(29 * boat.getHeight()/1115f), Math.round(1360 * boat.getWidth()/1410f),
        Math.round(1064 * boat.getHeight()/1115f));
    reflectionClip.moveTo(912,480); reflectionClip.lineTo(1110,480);
    reflectionClip.lineTo(1100,525); reflectionClip.lineTo(1018,545);
    reflectionClip.lineTo(1018,487); reflectionClip.lineTo(912,487);
    reflectionClip.close();
  }

  private static Bitmap decodeBoat(MainActivity host) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 4;
    try (java.io.InputStream in = host.getAssets().open("native/home-motion-v1/boat.png")) {
      return BitmapFactory.decodeStream(in,null,options);
    } catch (java.io.IOException | RuntimeException | OutOfMemoryError error) { return null; }
  }

  private static Bitmap repairPlate(MainActivity host, Path shape) {
    BitmapRegionDecoder decoder = null;
    Bitmap region = null;
    try (java.io.InputStream in = host.getAssets().open("native/home-motion-v1/boat-clean-plate.png")) {
      decoder = BitmapRegionDecoder.newInstance(in,false);
      if (decoder.getWidth()!=1870 || decoder.getHeight()!=841) return null;
      region = decoder.decodeRegion(new Rect(833,258,1128,557),null);
      if (region == null) return null;
      Bitmap result = Bitmap.createBitmap(295,299,Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(result);
      Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
      mask.setColor(Color.WHITE);
      // 안쪽은 완전 불투명(원래 배가 비치지 않음), 바깥만 6px 페더로 패치 이음새 제거.
      mask.setMaskFilter(new BlurMaskFilter(6,BlurMaskFilter.Blur.SOLID));
      canvas.translate(-833,-258);
      canvas.drawPath(shape,mask);
      canvas.translate(833,258);
      mask.setMaskFilter(null);
      mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
      canvas.drawBitmap(region,0,0,mask);
      return result;
    } catch (java.io.IOException | RuntimeException | OutOfMemoryError error) { return null; }
    finally {
      if (decoder != null) decoder.recycle();
      if (region != null) region.recycle();
    }
  }

  void draw(Canvas canvas, RectF bounds, double seconds, boolean moving) {
    // 불완전한 자산이면 원본만 남긴다. 프레임마다 비트맵/Path/시계를 생성하지 않는다.
    if (!ready) return;
    double t = moving ? seconds : 0;
    float bob = moving ? HomeWave.bob(t) : 0, roll = moving ? HomeWave.roll(t) : 0;
    int outer = canvas.save();
    canvas.translate(bounds.left,bounds.top);
    canvas.scale(bounds.width()/1870f,bounds.height()/841f);
    paint.setAlpha(255);
    canvas.drawBitmap(plate,null,plateTarget,paint);
    int local;

    // 반사와 접수선은 같은 bob/roll을 사용하며 수면 내부에서만 보인다.
    local = canvas.save();
    canvas.clipPath(reflectionClip);
    canvas.translate(0,bob);
    canvas.rotate(roll,985,475);
    canvas.translate(0,480);
    canvas.scale(1,-.24f);
    canvas.translate(0,-480);
    paint.setAlpha(32);
    canvas.drawBitmap(boat,boatSource,boatTarget,paint);
    canvas.restoreToCount(local);
    local = canvas.save();
    canvas.clipRect(0,452,910,841,Region.Op.DIFFERENCE);
    canvas.translate(0,bob);
    canvas.rotate(roll,985,475);
    paint.setAlpha(255);
    canvas.drawBitmap(boat,boatSource,boatTarget,paint);
    paint.setColor(0x66d9f7ec);
    waterline.set(913,477,1090,480);
    canvas.drawOval(waterline,paint);
    paint.setColor(Color.WHITE);
    canvas.restoreToCount(local);
    canvas.restoreToCount(outer);
  }
}
