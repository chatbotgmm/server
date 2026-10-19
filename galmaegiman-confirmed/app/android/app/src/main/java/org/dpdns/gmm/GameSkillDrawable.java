package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 일반 기술은 양피지 기록 카드, 합동기는 청록 항해 표지로 구별한다. */
final class GameSkillDrawable extends Drawable {
  final MainActivity h;
  final int accent;
  final boolean combo;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();
  private final Paint opacity = new Paint();
  private final Path clip = new Path();

  GameSkillDrawable(MainActivity h, int accent, boolean combo) {
    this.h = h;
    this.accent = accent;
    this.combo = combo;
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
  public void draw(Canvas canvas) {
    int save;
    if (state.enabled)
      save = state.begin(canvas, getBounds(), h.getResources().getDisplayMetrics().density);
    else {
      opacity.setAlpha(state.alpha);
      save = canvas.saveLayer(new RectF(getBounds()), opacity);
    }
    if (p.getColorFilter() != null) canvas.saveLayer(new RectF(getBounds()), p);
    RectF box = new RectF(getBounds());
    JournalTheme.drawPaper(
        canvas,
        box,
        h.getResources().getDisplayMetrics().density,
        combo ? JournalTheme.BRASS : JournalTheme.LINE,
        state.enabled ? combo ? JournalTheme.PRIMARY : JournalTheme.PAPER : JournalTheme.DISABLED);
    box.inset(h.dp(5), h.dp(5));
    box.bottom -= h.dp(1);
    int strip = canvas.save();
    clip.reset();
    clip.addRoundRect(box, h.dp(4), h.dp(4), Path.Direction.CW);
    canvas.clipPath(clip);
    p.setStyle(Paint.Style.FILL);
    p.setColor(combo ? JournalTheme.BRASS : accent);
    canvas.drawRect(box.left, box.top, box.right, box.top + h.dp(2), p);
    canvas.restoreToCount(strip);
    state.finish(canvas, save);
  }

  @Override
  public void setAlpha(int value) {
    state.alpha = value;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter value) {
    p.setColorFilter(value);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  /** 원본 아이콘은 별도 로더로 받으며, 그림 뒤의 작은 황동 판만 Canvas로 만든다. */
  static final class Seal extends Drawable {
    final MainActivity h;
    final int accent;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint opacity = new Paint();
    final RectF plate = new RectF(), art = new RectF();
    Bitmap bitmap;
    int alpha = 255;
    ColorFilter filter;

    Seal(MainActivity h, int accent, int size) {
      this.h = h;
      this.accent = accent;
      setBounds(0, 0, h.dp(size), h.dp(size));
    }

    void image(Bitmap image) {
      bitmap = image;
      invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
      plate.set(getBounds());
      int save = canvas.save();
      if (alpha != 255 || filter != null) {
        opacity.setAlpha(alpha);
        opacity.setColorFilter(filter);
        canvas.saveLayer(plate, opacity);
      }
      float density = h.getResources().getDisplayMetrics().density;
      JournalTheme.drawPaper(canvas, plate, density, JournalTheme.BRASS);
      art.set(plate);
      art.inset(3 * density, 3 * density);
      paint.setAlpha(255);
      if (bitmap != null) canvas.drawBitmap(bitmap, null, art, paint);
      // 기술 속성은 작은 잉크 모서리에만 남겨 UI 전체가 속성 색으로 변하지 않게 한다.
      paint.setColor(accent);
      paint.setAlpha(255);
      canvas.drawRect(
          plate.left + 3 * density,
          plate.bottom - 3 * density,
          plate.right - 3 * density,
          plate.bottom - 1.5f * density,
          paint);
      canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int value) {
      alpha = value;
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      this.filter = filter;
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
