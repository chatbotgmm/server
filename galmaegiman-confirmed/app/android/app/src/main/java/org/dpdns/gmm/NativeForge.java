package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** 재료가 모여 결과로 이어지는 조합 연출. 실패 시 재료가 유지된다는 서버 결과를 표시한다. */
final class NativeForge extends FrameLayout {
  final MainActivity h;
  final JSONArray materials;
  JSONObject result;
  final String text;
  final boolean fail;
  final Runnable next;
  final MenuKit k;
  final ValueAnimator animation;
  boolean complete, detached;

  NativeForge(
      MainActivity h, JSONArray mats, JSONObject result, boolean fail, String text, Runnable next) {
    super(h);
    this.h = h;
    materials = mats;
    this.result = result;
    this.fail = fail;
    this.text = text;
    this.next = next;
    k = new MenuKit(h);
    setBackgroundColor(0xe029211e);
    setClickable(true);
    Ring ring = new Ring(h);
    addView(ring, new LayoutParams(-1, -1));
    animation = ValueAnimator.ofFloat(0, 1);
    animation.setDuration(900);
    animation.addUpdateListener(
        a -> {
          ring.progress = (Float) a.getAnimatedValue();
          ring.invalidate();
        });
    animation.addListener(
        new android.animation.AnimatorListenerAdapter() {
          public void onAnimationEnd(android.animation.Animator a) {
            if (isAttachedToWindow() && !detached) showResult();
          }
        });
    setOnClickListener(
        v -> {
          if (!complete) {
            animation.cancel();
            showResult();
          }
        });
    post(animation::start);
  }

  void updateResult(JSONObject unit) {
    result = unit;
    if (complete) {
      complete = false;
      showResult();
    }
  }

  void showResult() {
    if (complete) return;
    complete = true;
    removeAllViews();
    LinearLayout page = k.paper();
    LayoutParams lp =
        new LayoutParams(
            Math.min(h.dp(520), (int) (h.root.getWidth() * .8f)),
            (int) (h.root.getHeight() * .86f),
            Gravity.CENTER);
    addView(page, lp);
    LinearLayout contents = k.column();
    if (result != null && !fail) {
      contents.addView(
          k.image("/thumbs/" + result.optString("img", result.optString("id")) + ".jpg", 132, 132));
      contents.addView(h.text(result.optString("name"), 22));
    }
    contents.addView(k.note(text));
    page.addView(k.scroll(contents), new LinearLayout.LayoutParams(-1, 0, 1));
    page.addView(k.action(null, "확인", next));
  }

  @Override
  protected void onDetachedFromWindow() {
    detached = true;
    animation.cancel();
    super.onDetachedFromWindow();
  }

  final class Ring extends View {
    final Paint p = new Paint(3);
    float progress;

    Ring(MainActivity h) {
      super(h);
    }

    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f,
          y = getHeight() / 2f,
          r = Math.min(getHeight(), getWidth()) * .28f * (1 - progress),
          size = h.dp(65) * (1 - progress * .4f);
      p.setColor(fail ? 0xffc98443 : 0xffe9a630);
      p.setAlpha((int) (255 * progress));
      c.drawCircle(x, y, h.dp(24) + progress * h.dp(42), p);
      p.setAlpha(255);
      for (int i = 0; i < materials.length(); i++) {
        JSONObject m = materials.optJSONObject(i);
        String id = m == null ? "" : m.optString("img", m.optString("id"));
        Bitmap b = h.art.get("/thumbs/" + id + ".jpg", this::invalidate);
        if (b == null) continue;
        double a = Math.PI * 2 * i / Math.max(1, materials.length());
        float cx = x + (float) Math.cos(a) * r, cy = y + (float) Math.sin(a) * r;
        c.drawBitmap(
            b, null, new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), p);
      }
      p.setColor(Color.WHITE);
      p.setTypeface(h.font);
      p.setTextSize(h.dp(18));
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText("누르면 계속", x, getHeight() - h.dp(32), p);
    }
  }
}
