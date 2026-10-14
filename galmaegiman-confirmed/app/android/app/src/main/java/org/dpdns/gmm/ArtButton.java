package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 원본 버튼·문구를 분리된 이미지 그대로 합성한다. */
final class ArtButton extends Drawable {
  final MainActivity host;
  final String kind;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();

  ArtButton(MainActivity h, String k) {
    host = h;
    kind = k;
  }

  @Override
  public void draw(Canvas c) {
    int save = state.begin(c, getBounds(), host.getResources().getDisplayMetrics().density);
    RectF r = new RectF(getBounds());
    Bitmap base = host.art.asset("b3_button_" + kind + ".webp"),
        label = host.art.asset("b3_label_" + kind + ".webp");
    if (base != null) c.drawBitmap(base, null, r, p);
    if (label != null) {
      float w = r.width() * (kind.equals("next") ? .34f : .56f),
          h = w * label.getHeight() / label.getWidth(),
          x = r.left + r.width() * (kind.equals("next") ? .455f : .5f),
          y = r.centerY();
      c.drawBitmap(label, null, new RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), p);
    }
    state.finish(c, save);
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean changed = state.update(states);
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void setAlpha(int a) {
    state.alpha = a;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
