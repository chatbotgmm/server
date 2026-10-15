package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.*;

/** Final design proposal only; never connected to production navigation. Logical 1280 × 524. */
class FinalVisionKit {
  static final int INK = 0xff302b25,
      PAPER = 0xfff7edda,
      WHITE = 0xfffffbf0,
      LEATHER = 0xff51372f,
      BRASS = 0xffc49a59,
      TEAL = 0xff28535b,
      MUTED = 0xff6c6053,
      LINE = 0xffd8c8ab,
      GREEN = 0xff356954,
      RED = 0xffa4463d,
      BLUE = 0xff456b8a;
  final Context ctx;
  final Canvas c;
  final Paint p = new Paint(3);
  final Typeface heading, prose;
  static final Map<String, Bitmap> images = new HashMap<>();
  static final Map<String, Rect> bounds = new HashMap<>();
  static final List<String> warnings = new ArrayList<>();

  FinalVisionKit(Context ctx, Canvas c) {
    this.ctx = ctx;
    this.c = c;
    heading = Typeface.createFromAsset(ctx.getAssets(), "native/Jua-Regular.ttf");
    prose = Typeface.createFromAsset(ctx.getAssets(), "native/GowunDodum-Regular.ttf");
  }

  RectF r(float x, float y, float w, float h) {
    return new RectF(x, y, x + w, y + h);
  }

  void fill(RectF r, int color, float radius) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    c.drawRoundRect(r, radius, radius, p);
  }

  void fill(float x, float y, float w, float h, int color, float radius) {
    fill(r(x, y, w, h), color, radius);
  }

  void line(float x, float y, float x2, float y2, int color, float width) {
    p.setShader(null);
    p.setColor(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(width);
    c.drawLine(x, y, x2, y2, p);
    p.setStyle(Paint.Style.FILL);
  }

  void stroke(RectF r, int color, float width, float radius) {
    p.setShader(null);
    p.setColor(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(width);
    c.drawRoundRect(r, radius, radius, p);
    p.setStyle(Paint.Style.FILL);
  }

  void circle(float x, float y, float radius, int color) {
    p.setShader(null);
    p.setColor(color);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(x, y, radius, p);
  }

  void text(String s, float x, float baseline, float size, int color, boolean bold) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(bold ? heading : prose);
    p.setTextSize(size);
    p.setColor(color);
    p.setTextAlign(Paint.Align.LEFT);
    c.drawText(s, x, baseline, p);
  }

  float width(String s, float size, boolean bold) {
    p.setTypeface(bold ? heading : prose);
    p.setTextSize(size);
    return p.measureText(s);
  }

  void center(String s, RectF box, float size, int color, boolean bold) {
    float tw = width(s, size, bold);
    if (tw > box.width() - 10) warnings.add(s + " / " + box.width());
    Paint.FontMetrics f = p.getFontMetrics();
    text(s, box.centerX() - tw / 2, box.centerY() - (f.ascent + f.descent) / 2, size, color, bold);
  }

  float wrap(
      String s,
      float x,
      float y,
      float maxWidth,
      float size,
      int color,
      float leading,
      boolean bold) {
    for (String paragraph : s.split("\\n", -1)) {
      StringBuilder row = new StringBuilder();
      for (String word : paragraph.split(" +")) {
        if (word.isEmpty()) continue;
        String candidate = row.length() == 0 ? word : row + " " + word;
        if (row.length() > 0 && width(candidate, size, bold) > maxWidth) {
          text(row.toString(), x, y, size, color, bold);
          y += leading;
          row.setLength(0);
        }
        if (width(word, size, bold) > maxWidth) {
          for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (row.length() > 0 && width(row.toString() + ch, size, bold) > maxWidth) {
              text(row.toString(), x, y, size, color, bold);
              y += leading;
              row.setLength(0);
            }
            row.append(ch);
          }
        } else {
          if (row.length() > 0) row.append(' ');
          row.append(word);
        }
      }
      if (row.length() > 0) text(row.toString(), x, y, size, color, bold);
      y += leading;
    }
    return y;
  }

  Bitmap asset(String path) {
    if (images.containsKey(path)) return images.get(path);
    try (InputStream in = ctx.getAssets().open(path)) {
      Bitmap b = BitmapFactory.decodeStream(in);
      if (b == null) throw new IOException(path);
      b.setDensity(Bitmap.DENSITY_NONE);
      images.put(path, b);
      return b;
    } catch (IOException e) {
      throw new IllegalStateException(path, e);
    }
  }

  void image(Bitmap b, RectF box, boolean cover) {
    float z =
        cover
            ? Math.max(box.width() / b.getWidth(), box.height() / b.getHeight())
            : Math.min(box.width() / b.getWidth(), box.height() / b.getHeight());
    RectF dst =
        r(
            box.centerX() - b.getWidth() * z / 2,
            box.centerY() - b.getHeight() * z / 2,
            b.getWidth() * z,
            b.getHeight() * z);
    c.save();
    c.clipRect(box);
    p.setShader(null);
    p.setColor(-1);
    c.drawBitmap(b, null, dst, p);
    c.restore();
  }

  void bg(String name, float dim) {
    image(asset("native/journal/" + name + ".png"), r(0, 0, 1280, 524), true);
    if (dim > 0) fill(0, 0, 1280, 524, ((int) (dim * 255) << 24) | 0x182a2d, 0);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0,
            0,
            0,
            524,
            new int[] {0x50222622, 0x00222622, 0x48222622},
            null,
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 1280, 524, p);
    p.setShader(null);
  }

  void sprite(String path, float x, float y, float w, float h) {
    Bitmap b = asset(path);
    Rect src = bounds.get(path);
    if (src == null) {
      int l = b.getWidth(), t = b.getHeight(), right = 0, bottom = 0;
      for (int yy = 0; yy < b.getHeight(); yy += 3)
        for (int xx = 0; xx < b.getWidth(); xx += 3)
          if (Color.alpha(b.getPixel(xx, yy)) > 20) {
            l = Math.min(l, xx);
            t = Math.min(t, yy);
            right = Math.max(right, xx + 3);
            bottom = Math.max(bottom, yy + 3);
          }
      src =
          new Rect(
              Math.max(0, l - 3),
              Math.max(0, t - 3),
              Math.min(b.getWidth(), right + 3),
              Math.min(b.getHeight(), bottom + 3));
      bounds.put(path, src);
    }
    float z = Math.min(w / src.width(), h / src.height());
    p.setShader(null);
    p.setColor(-1);
    c.drawBitmap(
        b,
        src,
        r(
            x + (w - src.width() * z) / 2,
            y + h - src.height() * z,
            src.width() * z,
            src.height() * z),
        p);
  }

  void unit(String id, boolean back, float x, float y, float w, float h) {
    sprite(
        "game-art/duel/sprites/"
            + id
            + (id.startsWith("V") ? "" : "_s0")
            + (back ? "_back.webp" : "_front.webp"),
        x,
        y,
        w,
        h);
  }

  void thumb(String id, float x, float y, float w, float h) {
    RectF box = r(x, y, w, h);
    c.save();
    Path clip = new Path();
    clip.addRoundRect(box, 7, 7, Path.Direction.CW);
    c.clipPath(clip);
    image(asset("game-art/thumbs/" + id + ".jpg"), box, true);
    c.restore();
    stroke(box, BRASS, 1, 7);
  }

  void icon(String name, float x, float y, float size) {
    String key = "icon/" + name;
    Bitmap b = images.get(key);
    if (b == null) {
      b = JournalIcons.create(ctx, name);
      if (b == null) throw new IllegalArgumentException(name);
      images.put(key, b);
    }
    image(b, r(x, y, size, size), false);
  }

  void leather(float x, float y, float w, float h) {
    RectF box = r(x, y, w, h);
    fill(x, y + 5, w, h, 0x55271d18, 12);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x + w,
            y + h,
            new int[] {0xff735343, LEATHER, 0xff382a25},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(box, 12, 12, p);
    p.setShader(null);
    RectF seam = new RectF(box);
    seam.inset(5, 5);
    p.setPathEffect(new DashPathEffect(new float[] {3, 4}, 0));
    stroke(seam, 0xffad9070, .8f, 8);
    p.setPathEffect(null);
    stroke(box, 0xff2e2520, 1, 12);
  }

  void panel(float x, float y, float w, float h) {
    leather(x, y, w, h);
    RectF page = r(x + 6, y + 6, w - 12, h - 13);
    p.setColor(Color.WHITE);
    p.setShader(new LinearGradient(0, y, 0, y + h, WHITE, PAPER, Shader.TileMode.CLAMP));
    c.drawRoundRect(page, 8, 8, p);
    p.setShader(null);
    stroke(page, 0xffceb58a, 1, 8);
    circle(x + 17, y + 17, 2, BRASS);
    circle(x + w - 17, y + 17, 2, BRASS);
  }

  void inset(float x, float y, float w, float h) {
    fill(x, y, w, h, 0xffeee4d0, 7);
    stroke(r(x, y, w, h), LINE, 1, 7);
  }

  void divider(float x, float y, float w) {
    line(x, y, x + w, y, LINE, 1);
  }

  void chip(String s, float x, float y, float w, int color) {
    fill(x, y, w, 27, color, 6);
    center(s, r(x, y, w, 27), 16, WHITE, true);
  }

  void paleChip(String s, float x, float y, float w, int color) {
    fill(x, y, w, 27, 0xffe9e4d5, 6);
    stroke(r(x, y, w, 27), color, 1, 6);
    center(s, r(x, y, w, 27), 16, color, true);
  }

  void button(String s, float x, float y, float w, float h, boolean primary, String glyph) {
    RectF box = r(x, y, w, h);
    fill(x, y + 4, w, h, 0x60301e16, 10);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0,
            y,
            0,
            y + h,
            primary ? 0xff477680 : 0xfffff9e8,
            primary ? 0xff21444c : 0xffe0ccb0,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(box, 10, 10, p);
    p.setShader(null);
    stroke(box, primary ? 0xffd6b26b : 0xff957048, 1.6f, 10);
    RectF inner = r(x + 3, y + 3, w - 6, h - 7);
    stroke(inner, primary ? 0xff729392 : 0xfffff8de, .8f, 7);
    circle(x + 10, y + h / 2, 2, primary ? BRASS : 0xffa17b44);
    circle(x + w - 10, y + h / 2, 2, primary ? BRASS : 0xffa17b44);
    if (glyph != null) {
      icon(glyph, x + 15, y + (h - 32) / 2, 32);
      box.left += 30;
    }
    center(s, box, h >= 60 ? 25 : 21, primary ? WHITE : INK, true);
  }

  void tab(String s, float x, float y, float w, boolean selected) {
    fill(x, y, w, 36, selected ? TEAL : 0xffeee5d3, 7);
    stroke(r(x, y, w, 36), selected ? BRASS : LINE, 1, 7);
    center(s, r(x, y, w, 36), 18, selected ? WHITE : MUTED, true);
  }

  void bar(float x, float y, float w, float h, float fraction, int color) {
    fill(x, y, w, h, 0xffdbd1ba, h / 2);
    if (fraction > 0)
      fill(x, y, Math.max(h, w * Math.max(0, Math.min(1, fraction))), h, color, h / 2);
    stroke(r(x, y, w, h), 0x403d4334, .7f, h / 2);
  }

  void header(String title, String sub) {
    button("뒤로", 28, 19, 102, 56, false, "back");
    text(title, 150, 49, 29, WHITE, true);
    if (sub != null) text(sub, 151, 71, 15, 0xffffe5ba, false);
  }

  void currencies() {
    String[] names = {"snack", "peanut", "ticket"}, values = {"1,403", "0", "231"};
    for (int i = 0; i < 3; i++) {
      float x = 838 + i * 132;
      fill(x, 20, 122, 43, 0xf8f7edda, 9);
      stroke(r(x, 20, 122, 43), BRASS, 1.5f, 9);
      icon(names[i], x + 8, 25, 31);
      text(values[i], x + 45, 49, 20, INK, true);
    }
  }

  void footer(String text) {
    text(text, 37, 507, 14, 0xfff4e6ce, false);
  }

  void stamp(String label, float x, float y, float w, int color) {
    c.save();
    c.rotate(-7, x + w / 2, y + 19);
    stroke(r(x, y, w, 38), color, 2, 3);
    stroke(r(x + 3, y + 3, w - 6, 32), color, .8f, 2);
    center(label, r(x, y, w, 38), 18, color, true);
    c.restore();
  }
}
