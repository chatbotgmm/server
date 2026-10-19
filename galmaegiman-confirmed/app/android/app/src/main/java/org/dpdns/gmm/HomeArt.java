package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.*;

/** Home-only release art. Five horizontal slices preserve the central gold crest. */
final class HomeArt {
  private final MainActivity host;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Map<String, Bitmap> images = new HashMap<>();
  private final Map<String, Rect> bounds = new HashMap<>();

  HomeArt(MainActivity host) {
    this.host = host;
    for (String file : new String[] {"backgrounds/bg-home.png", "ui/button-primary.png",
        "ui/button-secondary.png", "icons/harbor.png", "icons/explore.png", "icons/draw.png",
        "icons/team.png", "icons/combine.png", "icons/shop.png", "icons/daily.png",
        "icons/settings.png", "icons/peanut.png", "icons/shrimp-snack.png",
        "characters/home-guide-map.png"}) {
      Bitmap b = host.art.asset("release-v1/" + file);
      if (b != null) {
        images.put(file, b);
        bounds.put(file, host.art.alpha(b, "release-v1/" + file));
      }
    }
  }

  boolean ready() { return images.size() == 14; }

  Bitmap hero() { return images.get("characters/home-guide-map.png"); }
  Rect heroBounds() { return bounds.get("characters/home-guide-map.png"); }

  void background(Canvas canvas, int width, int height) {
    Bitmap b = images.get("backgrounds/bg-home.png");
    if (b == null) { canvas.drawColor(0xff173d50); return; }
    float scale = Math.max(width / (float) b.getWidth(), height / (float) b.getHeight());
    float w = b.getWidth() * scale, h = b.getHeight() * scale;
    canvas.drawBitmap(b, null, new RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint);
  }

  void icon(Canvas canvas, String file, RectF target) {
    Bitmap b = images.get(file);
    Rect source = bounds.get(file);
    if (b == null || source == null) return;
    float scale = Math.min(target.width()/source.width(), target.height()/source.height());
    float w = source.width()*scale, h = source.height()*scale;
    canvas.drawBitmap(b, source, new RectF(target.centerX()-w/2, target.centerY()-h/2,
        target.centerX()+w/2, target.centerY()+h/2), paint);
  }

  void surface(Canvas canvas, String file, RectF target) {
    Bitmap b = images.get(file);
    Rect s = bounds.get(file);
    if (b == null || s == null || target.isEmpty()) return;
    // Preserve end ornaments, top/bottom borders, and the central diamond instead of
    // stretching those details along with the plain middle texture.
    int edgeX = Math.min(80, s.width()/4), edgeY = Math.min(72, s.height()/3);
    int crest = 28, center = s.centerX();
    int[] sx = {s.left, s.left+edgeX, center-crest, center+crest, s.right-edgeX, s.right};
    int[] sy = {s.top, s.top+edgeY, s.bottom-edgeY, s.bottom};
    float scale = Math.min(host.getResources().getDisplayMetrics().density/3f, target.height()/s.height());
    float ex = Math.min(edgeX*scale, target.width()/5), ey = edgeY*scale;
    float mid = Math.min(crest*scale, target.width()/10);
    float[] dx = {target.left, target.left+ex, target.centerX()-mid, target.centerX()+mid,
        target.right-ex, target.right};
    float[] dy = {target.top, target.top+ey, target.bottom-ey, target.bottom};
    for (int y=0; y<3; y++) for (int x=0; x<5; x++)
      canvas.drawBitmap(b, new Rect(sx[x],sy[y],sx[x+1],sy[y+1]),
          new RectF(dx[x],dy[y],dx[x+1],dy[y+1]), paint);
  }

  Drawable surfaceDrawable(String file) {
    return new Drawable() {
      @Override public void draw(Canvas c) { surface(c, file, new RectF(getBounds())); }
      @Override public void setAlpha(int alpha) { }
      @Override public void setColorFilter(ColorFilter filter) { }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
  }
}
