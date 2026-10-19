package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;

/** Native, asset-independent architectural materials. No fabricated character artwork. */
final class AppSkin {
  static void apply(MainActivity h, String place) {
    android.view.View backdrop = h.root.findViewWithTag("theme-scene-background");
    if (backdrop != null) {
      backdrop.setBackground(ThemeArt.of(h).background(background(place)));
      if (h.page != null) h.page.setBackground(null);
    } else if (h.page != null) h.page.setBackground(ThemeArt.of(h).background(background(place)));
  }

  static String backgroundForScreen(String id) {
    if (id.equals("draw") || id.equals("drawResult")) return background("reception");
    if (id.equals("shop")) return background("market");
    if (id.equals("craft") || id.equals("mix")) return background("workshop");
    if (id.equals("expedition")) return background("routes");
    if (id.equals("modes") || id.equals("pvp") || id.equals("practice")
        || id.equals("chapters") || id.equals("stages") || id.equals("team")) return background("training");
    if (id.equals("account")) return "backgrounds/bg-settings-harbor-office-v1.png";
    return background("archive");
  }

  static String background(String place) {
    if (place.equals("training")) return "backgrounds/bg-team.png";
    if (place.equals("reception")) return "backgrounds/bg-recruitment-counter-v1.png";
    if (place.equals("market")) return "backgrounds/bg-market-canopy-v1.png";
    if (place.equals("routes") || place.equals("workshop")) return "backgrounds/bg-augments.png";
    return "backgrounds/bg-collection.png";
  }

  static final class Place extends Drawable {
    final String place;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    int alpha = 255;
    Place(String place) { this.place = place; }
    public void draw(Canvas c) {
      Rect b = getBounds();
      float w = b.width(), h = b.height();
      int stone = 0xfff3e9d3;
      if (place.equals("archive")) stone = 0xffe5e9e3;
      else if (place.equals("workshop")) stone = 0xffe8e0e8;
      else if (place.equals("routes")) stone = 0xffdce8e6;
      else if (place.equals("market")) stone = 0xffeee2c7;
      else if (place.equals("training")) stone = 0xffe5e7df;
      int save = c.save();
      c.translate(b.left, b.top);
      paint.setAlpha(alpha);
      paint.setStyle(Paint.Style.FILL);
      // 불투명하게 칠한다. 반투명이면 아래 화면(전투 상태창·명령 패널·로비 글자)이 비쳐 보인다.
      paint.setColor(stone);
      paint.setAlpha(alpha);
      c.drawRect(0, 0, w, h, paint);
      // Keep decoration at the perimeter, away from candidate text and controls.
      paint.setColor(JournalTheme.PRIMARY);
      c.drawRect(0, 0, w, Math.min(5, h * .012f), paint);
      paint.setColor(0x18bc9555);
      if (place.equals("market")) {
        for (int i=0; i<12; i+=2) c.drawRect(i*w/12, 0, (i+1)*w/12, h*.07f, paint);
      } else if (place.equals("workshop")) {
        for (int i=0; i<8; i++) c.drawRect(i*w/8, h*.96f, (i+1)*w/8-2, h, paint);
      } else if (place.equals("routes")) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        path.reset(); path.moveTo(0, h*.88f);
        path.cubicTo(w*.3f,h*.96f,w*.65f,h*.82f,w,h*.92f);
        c.drawPath(path,paint);
      } else {
        c.drawRect(0,h*.97f,w,h,paint);
      }
      paint.setStyle(Paint.Style.FILL);
      c.restoreToCount(save);
    }
    public void setAlpha(int a) { alpha=a; invalidateSelf(); }
    public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
  private AppSkin() {}
}
