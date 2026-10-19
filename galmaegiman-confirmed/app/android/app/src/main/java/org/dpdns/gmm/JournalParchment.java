package org.dpdns.gmm;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;

final class JournalParchment {
  private static volatile Prepared prepared;
  private static final ThreadLocal<DrawState> DRAW =
      new ThreadLocal<DrawState>() {
        @Override
        protected DrawState initialValue() {
          return new DrawState();
        }
      };

  private JournalParchment() {}

  static synchronized void prepare(Context context) {
    if (prepared != null) return;
    try {
      Resources resources = context.getResources();
      Drawable panel =
          resources.getDrawable(R.drawable.journal_parchment_panel, null);
      Drawable button =
          resources.getDrawable(R.drawable.journal_parchment_button, null);
      if (!(panel instanceof NinePatchDrawable)
          || !(button instanceof NinePatchDrawable)) {
        throw new IllegalStateException("Expected compiled NinePatch resources");
      }
      Drawable.ConstantState panelState = panel.getConstantState();
      Drawable.ConstantState buttonState = button.getConstantState();
      if (panelState == null || buttonState == null) {
        throw new IllegalStateException("Missing ConstantState");
      }
      prepared = new Prepared(panelState, buttonState);
    } catch (Resources.NotFoundException | IllegalStateException failure) {
      Log.e("JournalParchment", "Using existing paper fallback", failure);
    }
  }

  static boolean ready() { return prepared != null; }

  static boolean drawPanel(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, false);
  }

  static boolean drawButton(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, true);
  }

  private static boolean draw(
      Canvas canvas, RectF bounds, float density, boolean button) {
    Prepared source = prepared;
    if (source == null || bounds == null || bounds.isEmpty()) return false;
    float scale = Math.max(.5f, density);
    int left = (int) Math.ceil(bounds.left + scale);
    int top = (int) Math.ceil(bounds.top + scale);
    int right = (int) Math.floor(bounds.right - scale);
    int bottom = (int) Math.floor(bounds.bottom - scale);
    if (right <= left || bottom <= top) return false;
    DrawState state = DRAW.get();
    if (state.source != source) {
      state.panel = (NinePatchDrawable) source.panel.newDrawable().mutate();
      state.button = (NinePatchDrawable) source.button.newDrawable().mutate();
      state.source = source;
    }
    float width = right - left;
    float height = bottom - top;
    float insetX = Math.min(20 * scale, width * .24f);
    float insetY = Math.min(16 * scale, height * .24f);
    state.setCore(left + insetX, top + insetY,
        right - insetX, bottom - insetY, scale);
    int save = canvas.save();
    try {
      canvas.clipRect(bounds);
      state.base.setColor(JournalTheme.PAPER);
      canvas.drawRect(state.core, state.base);
      NinePatchDrawable drawable = button ? state.button : state.panel;
      drawable.setTargetDensity(Math.max(1, Math.round(160 * scale)));
      drawable.setBounds(left, top, right, bottom);
      drawable.setAlpha(255);
      drawable.draw(canvas);
      int washSave = canvas.save();
      try {
        canvas.clipRect(state.core);
        canvas.saveLayer(state.core, null);
        canvas.drawRect(state.core, state.wash);
        canvas.drawRect(state.core, state.mask);
      } finally {
        canvas.restoreToCount(washSave);
      }
    } finally {
      canvas.restoreToCount(save);
    }
    return true;
  }

  private static final class Prepared {
    final Drawable.ConstantState panel;
    final Drawable.ConstantState button;
    Prepared(Drawable.ConstantState panel, Drawable.ConstantState button) {
      this.panel = panel;
      this.button = button;
    }
  }

  private static final class DrawState {
    Prepared source;
    NinePatchDrawable panel;
    NinePatchDrawable button;
    final RectF core = new RectF();
    final Paint base = new Paint();
    final Paint wash = new Paint();
    final Paint mask = new Paint();
    float lastScale = -1;

    DrawState() {
      mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setCore(float left, float top, float right, float bottom, float scale) {
      if (core.left == left && core.top == top && core.right == right
          && core.bottom == bottom && lastScale == scale) return;
      core.set(left, top, right, bottom);
      lastScale = scale;
      float fx = Math.min(.25f, 4 * scale / core.width());
      float fy = Math.min(.25f, 4 * scale / core.height());
      int paper = JournalTheme.PAPER & 0x00ffffff;
      wash.setShader(new LinearGradient(
          core.left, 0, core.right, 0,
          new int[] {paper, 0xc0000000 | paper, 0xc0000000 | paper, paper},
          new float[] {0, fx, 1 - fx, 1}, Shader.TileMode.CLAMP));
      mask.setShader(new LinearGradient(
          0, core.top, 0, core.bottom,
          new int[] {0x00ffffff, Color.WHITE, Color.WHITE, 0x00ffffff},
          new float[] {0, fy, 1 - fy, 1}, Shader.TileMode.CLAMP));
    }
  }
}
