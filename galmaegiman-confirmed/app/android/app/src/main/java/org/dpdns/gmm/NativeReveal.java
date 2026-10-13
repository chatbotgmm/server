package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** 기존 알 열기 → 등급 빛 → 가로 결과 카드 연출. 모든 결과는 서버가 확정한 것만 표시한다. */
final class NativeReveal extends FrameLayout {
  final MainActivity h;
  final JSONObject result;
  final Runnable next;
  final MenuKit k;
  boolean opening, detached;
  ValueAnimator animation;

  NativeReveal(MainActivity h, JSONObject result, Runnable next, boolean again) {
    super(h);
    this.h = h;
    this.result = result;
    this.next = next;
    k = new MenuKit(h);
    setBackgroundColor(0xe029211e);
    setClickable(true);
    if (again) cards();
    else egg();
  }

  static int tier(String name) {
    switch (name) {
      case "COMMON":
        return 0xff8a8a8a;
      case "UNCOMMON":
        return 0xff2f8f5b;
      case "SPECIAL":
      case "NEUTRAL":
        return 0xff8a7a6a;
      case "RARE":
        return 0xff1f8fb0;
      case "EPIC":
        return 0xff7a4fd0;
      case "LEGEND":
      case "IMMORTAL":
        return 0xffd9a400;
      case "HIDDEN":
        return 0xff5b3d8f;
      case "LIMITED":
        return 0xffd9483b;
      case "TRANSCEND":
        return 0xff2a6fc0;
      case "ETERNAL":
        return 0xff0f8a7b;
      default:
        return 0xffc9b08a;
    }
  }

  void egg() {
    Egg art = new Egg(h);
    addView(art, new LayoutParams(-1, -1));
    Button open =
        h.button(
            "알 열기",
            () -> {
              if (opening) return;
              opening = true;
              animation = ValueAnimator.ofFloat(0, 1);
              animation.setDuration(700);
              animation.addUpdateListener(
                  a -> {
                    float t = (Float) a.getAnimatedValue();
                    art.shake = t;
                    art.invalidate();
                  });
              animation.addListener(
                  new android.animation.AnimatorListenerAdapter() {
                    public void onAnimationEnd(android.animation.Animator a) {
                      if (isAttachedToWindow() && !detached) cards();
                    }
                  });
              animation.start();
            });
    open.setBackgroundColor(Color.TRANSPARENT);
    open.setTextColor(Color.TRANSPARENT);
    LayoutParams box = new LayoutParams(h.dp(240), h.dp(240), Gravity.CENTER);
    addView(open, box);
  }

  void cards() {
    removeAllViews();
    JSONArray units = h.list(result, "items");
    LinearLayout page = k.column();
    page.setPadding(h.dp(16), h.dp(14), h.dp(16), h.dp(12));
    addView(page, new LayoutParams(-1, -1));
    TextView caption = h.text("뽑기 결과", 22);
    caption.setTextColor(Color.WHITE);
    caption.setGravity(Gravity.CENTER);
    page.addView(caption);
    HorizontalScrollView scroll = new HorizontalScrollView(h);
    scroll.setFillViewport(false);
    LinearLayout row = k.row();
    row.setGravity(Gravity.CENTER);
    scroll.addView(row);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    int height = Math.max(132, h.getResources().getConfiguration().screenHeightDp - 110),
        width = Math.max(90, Math.min(units.length() == 1 ? 220 : 168, (int) (height * .65f)));
    for (int i = 0; i < units.length(); i++) {
      JSONObject u = units.optJSONObject(i);
      LinearLayout card = k.paper();
      card.setGravity(Gravity.CENTER);
      android.graphics.drawable.GradientDrawable frame = h.material(0xfffffaf0);
      frame.setStroke(h.dp(3), tier(u.optString("tier")));
      card.setBackground(frame);
      if (u.optBoolean("first")) {
        TextView badge = h.text("NEW", 14);
        badge.setBackgroundColor(0xffe9a630);
        card.addView(badge);
      }
      ImageView picture = k.image("/thumbs/" + u.optString("img") + ".jpg", width - 18, width - 18);
      card.addView(picture);
      TextView name = h.text(u.optString("name"), 16);
      name.setGravity(Gravity.CENTER);
      card.addView(name);
      card.addView(
          k.note(u.optString("tierName") + (u.optInt("n") > 1 ? " ×" + u.optInt("n") : "")));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(width), -2);
      lp.setMargins(h.dp(5), 0, h.dp(5), 0);
      row.addView(card, lp);
      card.setScaleX(.75f);
      card.setAlpha(0);
      card.animate()
          .alpha(1)
          .scaleX(1)
          .setStartDelay(Math.min(i, 12) * 120L)
          .setDuration(250)
          .start();
    }
    Button ok = k.action(null, "확인", next);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(180), h.dp(48));
    lp.gravity = Gravity.CENTER_HORIZONTAL;
    page.addView(ok, lp);
  }

  protected void onDetachedFromWindow() {
    detached = true;
    if (animation != null) animation.cancel();
    super.onDetachedFromWindow();
  }

  static final class Egg extends View {
    final MainActivity h;
    final Paint p = new Paint(3);
    float shake;

    Egg(MainActivity h) {
      super(h);
      this.h = h;
    }

    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f + (float) Math.sin(shake * 70) * h.dp(8) * shake,
          y = getHeight() / 2f;
      float w = Math.min(h.dp(130), getHeight() * .36f), height = w * 1.27f;
      p.setShader(
          new RadialGradient(
              x - w * .18f,
              y - height * .2f,
              w,
              new int[] {0xffffffef, 0xfff3e2bf, 0xffd9b98a},
              null,
              Shader.TileMode.CLAMP));
      RectF egg = new RectF(x - w / 2, y - height / 2, x + w / 2, y + height / 2);
      c.drawOval(egg, p);
      p.setShader(null);
      p.setColor(0xff7a4e2a);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(h.dp(4));
      c.drawOval(egg, p);
      p.setStyle(Paint.Style.FILL);
      p.setColor(Color.WHITE);
      p.setTypeface(h.font);
      p.setTextSize(h.dp(19));
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText("눌러서 열기", getWidth() / 2f, y + height / 2 + h.dp(32), p);
    }
  }
}
