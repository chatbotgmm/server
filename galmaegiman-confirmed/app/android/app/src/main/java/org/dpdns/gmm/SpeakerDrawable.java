package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class SpeakerDrawable extends Drawable {
  final Paint p = new Paint(3);
  final boolean muted;

  SpeakerDrawable(boolean muted) {
    this.muted = muted;
  }

  public void draw(Canvas c) {
    c.save();
    Rect r = getBounds();
    c.translate(r.left, r.top);
    c.scale(r.width() / 24f, r.height() / 24f);
    p.setColor(0xff3b2614);
    p.setStyle(Paint.Style.FILL);
    Path speaker = new Path();
    speaker.moveTo(3, 9);
    speaker.lineTo(7, 9);
    speaker.lineTo(13, 5);
    speaker.lineTo(13, 19);
    speaker.lineTo(7, 15);
    speaker.lineTo(3, 15);
    speaker.close();
    c.drawPath(speaker, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2);
    p.setStrokeCap(Paint.Cap.ROUND);
    if (muted) {
      c.drawLine(17, 9, 22, 15, p);
      c.drawLine(22, 9, 17, 15, p);
    } else {
      c.drawArc(new RectF(12, 8, 21, 16), -45, 90, false, p);
      c.drawArc(new RectF(10, 5, 24, 19), -45, 90, false, p);
    }
    p.setStyle(Paint.Style.FILL);
    c.restore();
  }

  public void setAlpha(int a) {
    p.setAlpha(a);
  }

  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
