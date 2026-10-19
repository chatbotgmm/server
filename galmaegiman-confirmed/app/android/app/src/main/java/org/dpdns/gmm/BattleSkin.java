package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** Layered battle assets. Ordinary PNGs, manifest-defined nine-slice, never fake .9.png. */
final class BattleSkin extends Drawable {
  static final Map<String, Bitmap> CACHE = new HashMap<>();
  final MainActivity host;
  final String asset;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  int alpha = 255;
  BattleSkin(MainActivity h, String name) { host = h; asset = name; bitmap(h, name); }
  static Bitmap bitmap(MainActivity h, String name) {
    synchronized (CACHE) {
      if (!CACHE.containsKey(name)) {
        try (java.io.InputStream in = h.getAssets().open("battle-v1/" + name + ".png")) {
          CACHE.put(name, BitmapFactory.decodeStream(in));
        } catch (java.io.IOException e) { CACHE.put(name, null); }
      }
      return CACHE.get(name);
    }
  }
  static void contain(Canvas c, Bitmap image, RectF box, Paint p) {
    if (image == null || box.isEmpty()) return;
    float s = Math.min(box.width()/image.getWidth(), box.height()/image.getHeight());
    float w=image.getWidth()*s, h=image.getHeight()*s;
    c.drawBitmap(image, null, new RectF(box.centerX()-w/2,box.centerY()-h/2,box.centerX()+w/2,box.centerY()+h/2),p);
  }
  void surface(Canvas c, RectF box) {
    Bitmap b = bitmap(host, asset);
    if (b == null) { paint.setColor(0xff163e52); c.drawRoundRect(box,host.dp(12),host.dp(12),paint); return; }
    boolean panel = asset.equals("panel-command");
    int ix = panel ? 80 : 64, iy = panel ? 80 : 48;
    float density = panel ? 4 : 3;
    float x=host.dp(ix/density), y=host.dp(iy/density);
    // Compact bounds shrink corners uniformly; never invert source/destination rectangles.
    float compact=Math.min(1,Math.min(box.width()/(2*x),box.height()/(2*y)));
    x*=compact; y*=compact;
    int[] sx={0,ix,b.getWidth()-ix,b.getWidth()}, sy={0,iy,b.getHeight()-iy,b.getHeight()};
    float[] dx={box.left,box.left+x,box.right-x,box.right},dy={box.top,box.top+y,box.bottom-y,box.bottom};
    for(int row=0;row<3;row++) for(int col=0;col<3;col++)
      c.drawBitmap(b,new Rect(sx[col],sy[row],sx[col+1],sy[row+1]),new RectF(dx[col],dy[row],dx[col+1],dy[row+1]),paint);
  }
  @Override public void draw(Canvas c) {
    paint.setAlpha(alpha); surface(c,new RectF(getBounds()));
    boolean pressed=false,enabled=false;
    for(int s:getState()){ pressed|=s==android.R.attr.state_pressed; enabled|=s==android.R.attr.state_enabled; }
    if(pressed || !enabled) { paint.setColor(pressed?0x20000000:0x48787979); c.drawRect(getBounds(),paint); }
  }
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] s){invalidateSelf();return true;}
  @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
