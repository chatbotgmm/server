package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** 기존 GAMEUI의 종이 카드·아이콘·분할 영역을 만드는 네이티브 컴포넌트. */
final class MenuKit {
  final MainActivity h;

  MenuKit(MainActivity host) {
    h = host;
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(h);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(h);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  TextView note(String s) {
    TextView v = h.text(s, 14);
    v.setTypeface(h.prose);
    v.setTextColor(0xff7d5f40);
    return v;
  }

  TextView notice(String s) {
    TextView v = note(s);
    v.setBackground(h.material(0xfffffaf0));
    v.setPadding(h.dp(10), h.dp(7), h.dp(10), h.dp(7));
    return v;
  }

  TextView section(String s) {
    TextView v = notice(s);
    v.setTypeface(h.font);
    v.setTextSize(h.uiText(20));
    v.setTextColor(MainActivity.INK);
    return v;
  }

  LinearLayout paper() {
    LinearLayout v = column();
    v.setPadding(h.dp(10), h.dp(h.compact() ? 6 : 8), h.dp(10), h.dp(h.compact() ? 6 : 8));
    v.setBackground(new GamePaperDrawable(h));
    v.setElevation(h.dp(1));
    return v;
  }

  ImageView image(String path, int width, int height) {
    ImageView v = new ImageView(h);
    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    v.setLayoutParams(new LinearLayout.LayoutParams(h.dp(width), h.dp(height)));
    if (path != null && !path.isEmpty()) h.art.into(v, path);
    return v;
  }

  Button action(String icon, String name, Runnable click) {
    Button b = h.button(name, click);
    b.setTextSize(h.uiText(16));
    b.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    if (icon != null) {
      String path = "/duel/icons/" + icon + ".png";
      Runnable ready =
          () -> {
            Bitmap bitmap = h.art.peek(path);
            if (bitmap == null) return;
            BitmapDrawable d = new BitmapDrawable(h.getResources(), bitmap);
            d.setBounds(0, 0, h.dp(26), h.dp(26));
            b.setCompoundDrawablePadding(h.dp(6));
            b.setCompoundDrawables(d, null, null, null);
          };
      if (h.art.get(path, ready) != null) ready.run();
    }
    return b;
  }

  Button painted(String icon, String name, Runnable click) {
    Button b = h.button(name, click);
    b.setTextColor(Color.TRANSPARENT);
    b.setCompoundDrawables(null, null, null, null);
    b.setBackground(new Picture(h, "/duel/icons/" + icon + ".png"));
    return b;
  }

  ScrollView scroll(View v) {
    ScrollView s = new ScrollView(h);
    s.setFillViewport(false);
    s.addView(v);
    return s;
  }

  LinearLayout[] split(float leftWeight) {
    h.page.removeViewAt(h.page.getChildCount() - 1);
    LinearLayout line = row();
    line.setGravity(Gravity.TOP);
    h.page.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout left = column(), right = column();
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, leftWeight),
        b = new LinearLayout.LayoutParams(0, -1, 1 - leftWeight);
    a.rightMargin = h.dp(8);
    line.addView(scroll(left), a);
    line.addView(scroll(right), b);
    h.body = right;
    return new LinearLayout[] {left, right};
  }

  void footer(View v) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = h.dp(6);
    h.page.addView(v, lp);
  }

  void tabs(LinearLayout parent, String[] names, int active, IntConsumer select) {
    LinearLayout line = row();
    line.setGravity(Gravity.CENTER);
    for (int i = 0; i < names.length; i++) {
      int index = i;
      Button b = action(null, names[i], () -> select.accept(index));
      if (i == active) b.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
      b.setTextSize(h.uiText(15));
      LinearLayout.LayoutParams slot =
          new LinearLayout.LayoutParams(h.dp(names.length > 3 ? 82 : 124), h.dp(48));
      slot.setMargins(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
      b.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(7));
      line.addView(b, slot);
    }
    parent.addView(line);
  }

  LinearLayout tiles(LinearLayout parent, java.util.List<View> views, int columns) {
    LinearLayout grid = column();
    parent.addView(grid);
    for (int i = 0; i < views.size(); i += columns) {
      LinearLayout line = row();
      line.setGravity(Gravity.TOP);
      grid.addView(line);
      for (int j = 0; j < columns; j++)
        h.cell(line, i + j < views.size() ? views.get(i + j) : new View(h));
    }
    return grid;
  }

  LinearLayout tile(JSONObject u, String path, String label, Runnable click) {
    LinearLayout v = paper();
    v.setGravity(Gravity.CENTER_HORIZONTAL);
    if (u.has("tier")) {
      GradientDrawable frame = h.material(0xfffffaf0);
      frame.setStroke(h.dp(2), NativeReveal.tier(u.optString("tier")));
      v.setBackground(frame);
    }
    v.addView(image(path, 100, 100));
    TextView title = h.text(u.optString("name"), 15);
    title.setTypeface(h.headline);
    title.setGravity(Gravity.CENTER);
    v.addView(title);
    TextView sub = note(label);
    sub.setGravity(Gravity.CENTER);
    v.addView(sub);
    if (click != null) {
      v.setContentDescription(u.optString("name") + " · " + label);
      v.setOnClickListener(
          w -> {
            if (!h.busy) click.run();
          });
    }
    return v;
  }

  EditText search(LinearLayout parent, String placeholder, String initial, Consumer<String> edit) {
    EditText e = new EditText(h);
    e.setSingleLine(true);
    e.setTextColor(MainActivity.INK);
    e.setTextSize(16);
    e.setTypeface(h.prose);
    e.setHint(placeholder);
    e.setText(initial);
    e.setPadding(h.dp(12), h.dp(5), h.dp(12), h.dp(5));
    e.setBackground(h.material(0xfffffaf0));
    parent.addView(e, new LinearLayout.LayoutParams(-1, h.dp(48)));
    e.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            edit.accept(s.toString());
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    return e;
  }

  void compactQuantity(LinearLayout parent, int max, IntConsumer change) {
    quantity(parent, max, change);
    LinearLayout row = (LinearLayout) parent.getChildAt(parent.getChildCount() - 1);
    for (int i = 0; i < row.getChildCount(); i++) {
      View v = row.getChildAt(i);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              v instanceof EditText ? h.dp(36) : 0, h.dp(48), v instanceof EditText ? 0 : 1);
      lp.setMargins(h.dp(2), 0, h.dp(2), 0);
      v.setLayoutParams(lp);
      if (v instanceof Button) {
        Button b = (Button) v;
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(h.dp(48));
        b.setMinimumHeight(h.dp(48));
        b.setTextSize(14);
        b.setPadding(h.dp(3), 0, h.dp(3), h.dp(3));
      }
      if (v instanceof EditText) {
        v.setBackgroundTintList(null);
        v.setBackground(new GamePaperDrawable(h));
        v.setPadding(0, 0, 0, 0);
      }
    }
  }

  void quantity(LinearLayout parent, int max, IntConsumer change) {
    quantity(parent, max, true, change);
  }

  void quantity(LinearLayout parent, int max, boolean includeMax, IntConsumer change) {
    LinearLayout row = row();
    EditText input = new EditText(h);
    input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    input.setSingleLine(true);
    input.setGravity(Gravity.CENTER);
    input.setTextColor(MainActivity.INK);
    input.setTypeface(h.font);
    input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffa8672e));
    input.setText("1");
    int[] amount = {1};
    Runnable paint =
        () -> {
          input.setText(String.valueOf(amount[0]));
          change.accept(amount[0]);
        };
    h.cell(
        row,
        action(
            null,
            "−",
            () -> {
              amount[0] = Math.max(1, amount[0] - 1);
              paint.run();
            }));
    row.addView(input, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    h.cell(
        row,
        action(
            null,
            "＋",
            () -> {
              amount[0] = Math.min(Math.max(1, max), amount[0] + 1);
              paint.run();
            }));
    if (includeMax) {
      h.cell(
          row,
          action(
              null,
              "최대",
              () -> {
                amount[0] = Math.max(1, max);
                paint.run();
              }));
    }
    input.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            try {
              amount[0] = Math.max(1, Math.min(Math.max(1, max), Integer.parseInt(s.toString())));
            } catch (NumberFormatException ignored) {
              amount[0] = 1;
            }
            change.accept(amount[0]);
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    input.setOnFocusChangeListener(
        (v, focus) -> {
          if (!focus) paint.run();
        });
    parent.addView(row);
    change.accept(1);
    if (max <= 0) {
      input.setEnabled(false);
      for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setEnabled(false);
    }
  }

  void progress(LinearLayout parent, String label, int value, int max, int color) {
    parent.addView(note(label));
    ProgressBar bar = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(Math.max(1, max));
    bar.setProgress(Math.max(0, value));
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
    parent.addView(bar, new LinearLayout.LayoutParams(-1, h.dp(12)));
  }

  static int attrColor(String id) {
    switch (id) {
      case "STONE":
        return 0xffc9b48a;
      case "GOLD":
        return 0xfff0b93a;
      case "FIRE":
        return 0xfff26b3a;
      case "ICE":
        return 0xff8fdcf5;
      case "BLOSSOM":
        return 0xfff7a3c6;
      case "SEA":
        return 0xff3d94e6;
      case "SHADOW":
        return 0xff8a6bb8;
      case "STAR":
        return 0xffffe36e;
      case "THREAD":
        return 0xffc3c6dc;
      case "GRAVITY":
        return 0xff5b63c9;
      default:
        return 0xffc9b48a;
    }
  }

  TextView chip(String name, String id) {
    TextView v = h.text(name, 13);
    v.setGravity(Gravity.CENTER);
    v.setPadding(h.dp(6), h.dp(3), h.dp(6), h.dp(3));
    GradientDrawable d = h.material(attrColor(id));
    d.setCornerRadius(h.dp(9));
    d.setStroke(0, 0);
    v.setBackground(d);
    if (id.equals("SHADOW") || id.equals("GRAVITY")) v.setTextColor(Color.WHITE);
    return v;
  }

  static final class Picture extends Drawable {
    final MainActivity host;
    final String path;
    final Paint p = new Paint(3);
    final GamePressState state = new GamePressState();

    Picture(MainActivity h, String path) {
      host = h;
      this.path = path;
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

    public void draw(Canvas c) {
      Bitmap b = host.art.get(path, this::invalidateSelf);
      if (b == null) return;
      Rect r = getBounds();
      int saved = state.begin(c, r, host.getResources().getDisplayMetrics().density);
      float scale = Math.min(r.width() / (float) b.getWidth(), r.height() / (float) b.getHeight());
      float w = b.getWidth() * scale, h = b.getHeight() * scale;
      c.drawBitmap(
          b,
          null,
          new RectF(
              r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2),
          p);
      state.finish(c, saved);
    }

    public void setAlpha(int alpha) {
      state.alpha = alpha;
      invalidateSelf();
    }

    public void setColorFilter(ColorFilter filter) {
      p.setColorFilter(filter);
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
