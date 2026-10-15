package org.dpdns.gmm;

import android.animation.*;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.*;
import org.json.*;

/** 웹의 재료 흡입·빛·결과 카드와 실패 시 흩어짐을 복원한다. 서버 결과만 표시한다. */
final class NativeForge extends FrameLayout {
  final MainActivity h;
  final JSONArray materials;
  JSONObject result;
  final String text;
  final boolean fail;
  final Runnable next;
  final MenuKit k;
  final NativeAcquisitionFlow flow;
  final ValueAnimator animation;
  boolean complete, detached, leaving;
  Ring ring;
  View resultCard;

  NativeForge(
      MainActivity h, JSONArray mats, JSONObject result, boolean fail, String text, Runnable next) {
    this(h, mats, result, fail, text, next, null);
  }

  NativeForge(
      MainActivity h,
      JSONArray mats,
      JSONObject result,
      boolean fail,
      String text,
      Runnable next,
      NativeAcquisitionFlow flow) {
    super(h);
    this.h = h;
    materials = mats;
    this.result = result;
    this.fail = fail;
    this.text = text;
    this.next = next;
    this.flow = flow;
    k = new MenuKit(h);
    setBackgroundColor(JournalTheme.TEAL);
    setClickable(true);
    ring = new Ring();
    addView(ring, new LayoutParams(-1, -1));
    animation = ValueAnimator.ofFloat(0, 1);
    animation.setDuration(fail ? 1300 : 1500);
    animation.setInterpolator(new LinearInterpolator());
    animation.addUpdateListener(
        a -> {
          ring.progress = (Float) a.getAnimatedValue();
          ring.invalidate();
        });
    animation.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator a) {
            if (isAttachedToWindow() && !detached && !complete) showResult(true);
          }
        });
    setOnClickListener(
        v -> {
          if (!complete) {
            complete = true;
            animation.cancel();
            complete = false;
            showResult(true);
          }
        });
    post(
        () -> {
          if (detached) return;
          h.sound.effect(fail ? "forgefail" : "forge");
          if (motion()) animation.start();
          else showResult(false);
        });
  }

  boolean motion() {
    return NativeMotion.enabled(h, h.motionEnabled);
  }

  int color() {
    return fail || result == null
        ? JournalTheme.BRASS
        : NativeReveal.tier(result.optString("tier"));
  }

  void updateResult(JSONObject unit) {
    if (detached) return;
    result = unit;
    if (complete) {
      complete = false;
      showResult(false);
    } else ring.invalidate();
  }

  void showResult() {
    showResult(true);
  }

  void showResult(boolean playEffect) {
    if (complete || detached) return;
    complete = true;
    if (resultCard != null) resultCard.animate().cancel();
    removeAllViews();
    ring.progress = 1;
    addView(ring, new LayoutParams(-1, -1));
    LinearLayout page = k.column();
    page.setGravity(Gravity.CENTER_HORIZONTAL);
    page.setPadding(h.dp(14), h.dp(10), h.dp(14), h.dp(10));
    int available =
        getHeight() > 0
            ? (int) (getHeight() / h.getResources().getDisplayMetrics().density)
            : h.contentHeightDp();
    int imageSize = Math.max(72, Math.min(200, (int) (available * (flow == null ? .42f : .30f))));
    LayoutParams box =
        new LayoutParams(
            Math.min(h.dp(520), (int) ((getWidth() > 0 ? getWidth() : h.root.getWidth()) * .8f)),
            -1,
            Gravity.CENTER);
    addView(page, box);
    LinearLayout content = k.column();
    content.setGravity(Gravity.CENTER);
    content.setPadding(0, h.dp(6), 0, h.dp(6));
    ScrollView resultScroll = k.scroll(content);
    resultScroll.setTag("forge-result-scroll");
    resultScroll.setFillViewport(true);
    page.addView(resultScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (result != null && !fail) {
      LinearLayout card = k.column();
      resultCard = card;
      card.setGravity(Gravity.CENTER);
      card.setPadding(h.dp(10), h.dp(10), h.dp(10), h.dp(10));
      card.setBackground(new GamePaperDrawable(h, color()));
      card.setElevation(h.dp(5));
      card.addView(
          k.image(
              "/thumbs/" + result.optString("img", result.optString("id")) + ".jpg",
              imageSize,
              imageSize));
      TextView name = h.text(result.optString("name"), 20);
      name.setTag("forge-result-name");
      name.setTypeface(h.headline);
      name.setGravity(Gravity.CENTER);
      name.setTextLocale(java.util.Locale.KOREA);
      name.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
      if (android.os.Build.VERSION.SDK_INT >= 33)
        name.setLineBreakWordStyle(
            android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
      card.addView(name);
      if (!result.optString("tierName").isEmpty()) {
        TextView grade = k.note(result.optString("tierName"));
        grade.setTextColor(NativeReveal.tierInk(result.optString("tier")));
        card.addView(grade);
      }
      int nameWidth = (int) Math.ceil(name.getPaint().measureText(result.optString("name")));
      int cardWidth =
          Math.min(box.width - h.dp(28), Math.max(h.dp(imageSize + 20), nameWidth + h.dp(36)));
      content.addView(card, new LinearLayout.LayoutParams(cardWidth, -2));
      if (playEffect && motion()) {
        card.setScaleX(.4f);
        card.setScaleY(.4f);
        card.setAlpha(0);
        card.animate()
            .scaleX(1)
            .scaleY(1)
            .alpha(1)
            .setDuration(450)
            .setInterpolator(new OvershootInterpolator(1.1f))
            .start();
      }
      if (playEffect)
        h.sound.effect(NativeReveal.rank(result.optString("tier")) >= 6 ? "drawlegend" : "levelup");
    }
    TextView message =
        k.note(text == null || text.isEmpty() ? (fail ? "조합이 맞지 않아 재료는 그대로예요." : "조합 완료!") : text);
    message.setTextColor(JournalTheme.PAPER);
    message.setTag("forge-result-message");
    message.setGravity(Gravity.CENTER);
    message.setPadding(0, h.dp(8), 0, h.dp(8));
    content.addView(message, new LinearLayout.LayoutParams(-1, -2));
    if (flow != null && result != null && !fail) {
      page.addView(flow.actions(result), new LinearLayout.LayoutParams(-1, h.dp(48)));
    }
    Button ok = k.action(null, "확인", this::finish);
    ok.setTag("forge-result-confirm");
    LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(h.dp(180), h.dp(48));
    footer.gravity = Gravity.CENTER_HORIZONTAL;
    footer.topMargin = h.dp(6);
    page.addView(ok, footer);
    setOnClickListener(null);
  }

  void finish() {
    if (leaving) return;
    leaving = true;
    if (motion())
      animate()
          .alpha(0)
          .setDuration(200)
          .withEndAction(
              () -> {
                if (!detached) next.run();
              })
          .start();
    else next.run();
  }

  @Override
  protected void onDetachedFromWindow() {
    detached = true;
    animation.cancel();
    animate().cancel();
    if (resultCard != null) resultCard.animate().cancel();
    for (int i = 0; i < getChildCount(); i++) getChildAt(i).animate().cancel();
    super.onDetachedFromWindow();
  }

  final class Ring extends View {
    final Paint p = new Paint(3);
    final PathInterpolator absorb = new PathInterpolator(.6f, -.3f, .7f, .2f);
    final PathInterpolator scatter = new PathInterpolator(.2f, .8f, .3f, 1);
    float progress;

    Ring() {
      super(h);
    }

    @Override
    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f, y = getHeight() / 2f, dimension = Math.min(getHeight(), h.dp(540));
      NativeResultBackdrop.draw(h, c, getWidth(), getHeight(), p);
      float ms = progress * (fail ? 1300 : 1500);
      float ringSize = Math.min(dimension * .56f, h.dp(300)), startRadius = ringSize / 2.6f;
      float move =
          fail
              ? scatter.getInterpolation(Math.max(0, Math.min(1, (ms - 500) / 900)))
              : absorb.getInterpolation(Math.min(1, ms / 1000));
      float radius =
          fail ? startRadius + (ringSize / 1.4f - startRadius) * move : startRadius * (1 - move);
      float size = Math.min(dimension * .15f, h.dp(84)) * (fail ? 1 - move * .4f : 1 - move * .8f);
      float opacity = ms < 1000 ? 1 : Math.max(0, 1 - (ms - 1000) / 300);
      for (int i = 0; i < materials.length() && opacity > 0; i++) {
        JSONObject material = materials.optJSONObject(i);
        String id =
            material == null
                ? materials.optString(i, "")
                : material.optString("img", material.optString("id"));
        Bitmap bitmap = h.art.get("/thumbs/" + id + ".jpg", this::invalidate);
        if (bitmap == null) continue;
        double angle =
            Math.PI * 2 * i / Math.max(1, materials.length())
                - Math.PI / 2
                + (fail ? 0 : Math.toRadians(200 * move));
        float cx = x + (float) Math.cos(angle) * radius, cy = y + (float) Math.sin(angle) * radius;
        RectF card = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        p.setAlpha((int) (opacity * 255));
        Path clip = new Path();
        clip.addRoundRect(card, h.dp(10), h.dp(10), Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawBitmap(bitmap, null, card, p);
        c.restore();
        p.setShader(null);
        p.setColor(JournalTheme.BRASS);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h.dp(3));
        c.drawRoundRect(card, h.dp(10), h.dp(10), p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
      }
      float coreOpacity, scale;
      if (fail) {
        float t = Math.min(1, ms / 1200);
        coreOpacity = t < .4f ? t / .4f * .9f : (1 - t) / .6f * .9f;
        scale = t < .4f ? 1 + t * 5 : 3 + (t - .4f) * 3.33f;
      } else if (progress < .6f) {
        coreOpacity = progress / .6f;
        scale = 1;
      } else if (progress < .85f) {
        coreOpacity = 1;
        scale = 1 + (progress - .6f) / .25f * 5;
      } else {
        coreOpacity = (1 - progress) / .15f;
        scale = 6 + (progress - .85f) / .15f * 8;
      }
      if (coreOpacity > 0) {
        float glowRadius = Math.max(1, h.dp(30) * scale);
        p.setShader(
            new RadialGradient(
                x,
                y,
                glowRadius,
                new int[] {
                  JournalTheme.PAPER,
                  JournalTheme.BRASS & 0x00ffffff | 0x88000000,
                  Color.TRANSPARENT
                },
                new float[] {0, .28f, 1},
                Shader.TileMode.CLAMP));
        p.setAlpha((int) (Math.min(1, coreOpacity) * 255));
        c.drawCircle(x, y, glowRadius, p);
        p.setShader(null);
        p.setAlpha(255);
      }
      if (!complete) {
        p.setColor(JournalTheme.PAPER);
        p.setTypeface(h.prose);
        p.setTextSize(h.dp(14));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(fail ? "…조합을 확인하고 있어요" : "누르면 계속", x, getHeight() - h.dp(20), p);
      }
    }
  }
}
