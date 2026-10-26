package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.*;

/** Home-only release art. Five horizontal slices preserve the central gold crest. */
final class HomeArt {
  private final MainActivity host;
  private final android.view.View owner;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Map<String, Bitmap> images = new HashMap<>();
  private final Map<String, Rect> bounds = new HashMap<>();
  final HomeWater water = new HomeWater();
  private HomeHarborMotion harbor;
  private final RectF backgroundTarget = new RectF(), drawTarget = new RectF();
  private final RectF drawableTarget = new RectF();
  private final Rect drawSource = new Rect();
  private final int[] sx = new int[6], sy = new int[4];
  private final float[] dx = new float[6], dy = new float[4];

  HomeArt(MainActivity host) { this(host, host.root); }

  HomeArt(MainActivity host, android.view.View owner) {
    this.host = host;
    this.owner = owner;
    for (String file : new String[] {"backgrounds/bg-home.png", "ui/button-primary.png",
        "ui/button-secondary.png", "icons/harbor.png", "icons/explore.png", "icons/draw.png",
        "icons/team.png", "icons/combine.png", "icons/shop.png", "icons/daily.png",
        "icons/settings.png", "icons/peanut.png", "icons/shrimp-snack.png",
        "characters/home-guide-map.png"}) {
      // Keep the existing menu key/geometry, but show the same application as recruitment.
      boolean recruitment = file.equals("icons/draw.png");
      String path = recruitment ? "registration-v1/resources/application-envelope-atlas.png"
          : "release-v1/" + file;
      Bitmap b = host.art.asset(path);
      if (recruitment && b != null && (b.getWidth() != ApplicationEnvelopeView.ATLAS_WIDTH
          || b.getHeight() != ApplicationEnvelopeView.ATLAS_HEIGHT)) continue;
      if (b != null) {
        images.put(file, b);
        bounds.put(file, recruitment ? NativeEconomy.RecruitmentSeal.envelopeInk(b)
            : host.art.alpha(b, path));
      }
    }
  }

  boolean ready() { return images.size() == 14; }

  Bitmap hero() { return images.get("characters/home-guide-map.png"); }
  Rect heroBounds() { return bounds.get("characters/home-guide-map.png"); }

  void background(Canvas canvas, int width, int height) {
    background(canvas, width, height, 0, false);
  }

  void background(Canvas canvas, int width, int height, double seconds, boolean moving) {
    Bitmap b = images.get("backgrounds/bg-home.png");
    if (b == null) { canvas.drawColor(0xff173d50); return; }
    float scale = Math.max(width / (float) b.getWidth(), height / (float) b.getHeight());
    float w = b.getWidth() * scale, h = b.getHeight() * scale;
    backgroundTarget.set((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2);
    canvas.drawBitmap(b, null, backgroundTarget, paint);
    water.draw(canvas, b, backgroundTarget, seconds, moving);
    if (harbor == null) harbor = new HomeHarborMotion(host);
    harbor.draw(canvas, backgroundTarget, seconds, moving);
    if (host.backgroundEffects != null)
      host.backgroundEffects.draw(canvas, "native/release-v1/backgrounds/bg-home.png", backgroundTarget, owner, owner::invalidate);
  }

  void icon(Canvas canvas, String file, RectF target) {
    Bitmap b = images.get(file);
    Rect source = bounds.get(file);
    if (b == null || source == null) return;
    float scale = Math.min(target.width()/source.width(), target.height()/source.height());
    float w = source.width()*scale, h = source.height()*scale;
    drawTarget.set(target.centerX()-w/2, target.centerY()-h/2,
        target.centerX()+w/2, target.centerY()+h/2);
    canvas.drawBitmap(b, source, drawTarget, paint);
  }

  void surface(Canvas canvas, String file, RectF target) {
    Bitmap b = images.get(file);
    Rect s = bounds.get(file);
    if (b == null || s == null || target.isEmpty()) return;
    // Preserve end ornaments, top/bottom borders, and the central diamond instead of
    // stretching those details along with the plain middle texture.
    int edgeX = Math.min(80, s.width()/4), edgeY = Math.min(72, s.height()/3);
    int crest = 28, center = s.centerX();
    sx[0] = s.left; sx[1] = s.left+edgeX; sx[2] = center-crest;
    sx[3] = center+crest; sx[4] = s.right-edgeX; sx[5] = s.right;
    sy[0] = s.top; sy[1] = s.top+edgeY; sy[2] = s.bottom-edgeY; sy[3] = s.bottom;
    float scale = Math.min(host.getResources().getDisplayMetrics().density/3f, target.height()/s.height());
    float ex = Math.min(edgeX*scale, target.width()/5), ey = edgeY*scale;
    float mid = Math.min(crest*scale, target.width()/10);
    dx[0] = target.left; dx[1] = target.left+ex; dx[2] = target.centerX()-mid;
    dx[3] = target.centerX()+mid; dx[4] = target.right-ex; dx[5] = target.right;
    dy[0] = target.top; dy[1] = target.top+ey; dy[2] = target.bottom-ey; dy[3] = target.bottom;
    for (int y=0; y<3; y++) for (int x=0; x<5; x++) {
      drawSource.set(sx[x], sy[y], sx[x+1], sy[y+1]);
      drawTarget.set(dx[x], dy[y], dx[x+1], dy[y+1]);
      canvas.drawBitmap(b, drawSource, drawTarget, paint);
    }
  }

  Drawable surfaceDrawable(String file) {
    return new Drawable() {
      @Override public void draw(Canvas c) {
        drawableTarget.set(getBounds());
        surface(c, file, drawableTarget);
      }
      @Override public void setAlpha(int alpha) { }
      @Override public void setColorFilter(ColorFilter filter) { }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
  }
}

