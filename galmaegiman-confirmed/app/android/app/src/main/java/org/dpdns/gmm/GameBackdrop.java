package org.dpdns.gmm;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

final class GameBackdrop extends ImageView implements FullBleed {
  boolean blurred;
  android.graphics.Bitmap original;
  int preparedWidth;

  GameBackdrop(Context c) {
    super(c);
  }

  void blurred(boolean on) {
    blurred = on;
    prepare();
  }

  @Override
  public void setImageBitmap(android.graphics.Bitmap bitmap) {
    original = bitmap;
    preparedWidth = 0;
    prepare();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged(w, h, oldW, oldH);
    if (blurred && w != preparedWidth) prepare();
  }

  private void prepare() {
    if (original == null) {
      super.setImageBitmap(null);
      return;
    }
    if (!blurred) {
      super.setImageBitmap(original);
      return;
    }
    int display =
        Math.max(1, getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels);
    preparedWidth = display;
    int width = Math.min(600, original.getWidth()),
        height =
            Math.max(1, Math.round(original.getHeight() * width / (float) original.getWidth()));
    android.graphics.Bitmap small =
        android.graphics.Bitmap.createScaledBitmap(original, width, height, true);
    int[] pixels = new int[width * height];
    small.getPixels(pixels, 0, width, 0, 0, width, height);
    int radius =
        Math.max(1, Math.round(5 * getResources().getDisplayMetrics().density * width / display));
    for (int pass = 0; pass < 3; pass++) {
      pixels = box(pixels, width, height, radius, true);
      pixels = box(pixels, width, height, radius, false);
    }
    android.graphics.Bitmap result =
        android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888);
    result.setPixels(pixels, 0, width, 0, 0, width, height);
    super.setImageBitmap(result);
    android.graphics.ColorMatrix color = new android.graphics.ColorMatrix();
    color.setSaturation(.95f);
    color.postConcat(
        new android.graphics.ColorMatrix(
            new float[] {.97f, 0, 0, 0, 0, 0, .97f, 0, 0, 0, 0, 0, .97f, 0, 0, 0, 0, 0, 1, 0}));
    setColorFilter(new android.graphics.ColorMatrixColorFilter(color));
  }

  static int[] box(int[] input, int w, int h, int radius, boolean horizontal) {
    int[] out = new int[input.length];
    int length = horizontal ? w : h, lines = horizontal ? h : w, window = radius * 2 + 1;
    for (int line = 0; line < lines; line++) {
      long red = 0, green = 0, blue = 0, alpha = 0;
      for (int t = -radius; t <= radius; t++) {
        int at = Math.max(0, Math.min(length - 1, t)),
            c = input[horizontal ? line * w + at : at * w + line];
        alpha += (c >>> 24);
        red += (c >> 16) & 255;
        green += (c >> 8) & 255;
        blue += c & 255;
      }
      for (int at = 0; at < length; at++) {
        out[horizontal ? line * w + at : at * w + line] =
            (int) (alpha / window) << 24
                | (int) (red / window) << 16
                | (int) (green / window) << 8
                | (int) (blue / window);
        int remove = Math.max(0, at - radius), add = Math.min(length - 1, at + radius + 1);
        int r = input[horizontal ? line * w + remove : remove * w + line],
            a = input[horizontal ? line * w + add : add * w + line];
        alpha += (a >>> 24) - (r >>> 24);
        red += ((a >> 16) & 255) - ((r >> 16) & 255);
        green += ((a >> 8) & 255) - ((r >> 8) & 255);
        blue += (a & 255) - (r & 255);
      }
    }
    return out;
  }
}

final class GameShade extends View implements FullBleed {
  GameShade(Context c) {
    super(c);
  }
}
