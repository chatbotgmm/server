package org.dpdns.gmm;

import android.animation.*;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

/** 웹의 알 흔들림 → 등급 빛 → 카드 뒤집기. 결과와 등급은 서버 응답만 사용한다. */
final class NativeReveal extends FrameLayout {
  final MainActivity h;
  final JSONObject result;
  final Runnable next;
  final MenuKit k;
  final List<Animator> animations = new ArrayList<>();
  final JSONArray units;
  final NativeAcquisitionFlow flow;
  final int topColor, topRank;
  final Glow glow;
  boolean opening, detached, revealed, leaving;
  ValueAnimator animation;

  NativeReveal(MainActivity h, JSONObject result, Runnable next, boolean again) {
    this(h, result, next, again, null);
  }

  NativeReveal(
      MainActivity h, JSONObject result, Runnable next, boolean again, NativeAcquisitionFlow flow) {
    super(h);
    this.h = h;
    this.result = result;
    this.next = next;
    this.flow = flow;
    k = new MenuKit(h);
    units = h.list(result, "items");
    String best = "COMMON";
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && rank(unit.optString("tier")) > rank(best)) best = unit.optString("tier");
    }
    topColor = tier(best);
    topRank = rank(best);
    setBackgroundColor(JournalTheme.TEAL);
    setClickable(true);
    glow = new Glow();
    if (again) cards(false);
    else egg();
  }

  static int rank(String name) {
    switch (name) {
      case "NEUTRAL":
        return 1;
      case "UNCOMMON":
        return 2;
      case "SPECIAL":
        return 3;
      case "RARE":
        return 4;
      case "EPIC":
        return 5;
      case "LEGEND":
        return 6;
      case "HIDDEN":
        return 7;
      case "LIMITED":
        return 8;
      case "TRANSCEND":
        return 9;
      case "ETERNAL":
        return 10;
      case "IMMORTAL":
        return 11;
      default:
        return 0;
    }
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

  // 원본 등급색은 카드 테두리에 유지하고, 작은 등급 글자는 종이 위 대비를 확보한다.
  static int tierInk(String name) {
    int original = tier(name);
    for (int step = 0; step <= 10; step++) {
      int ink = androidx.core.graphics.ColorUtils.blendARGB(original, JournalTheme.INK, step / 10f);
      if (androidx.core.graphics.ColorUtils.calculateContrast(ink, JournalTheme.PAPER) >= 4.5)
        return ink;
    }
    return JournalTheme.INK;
  }

  boolean motion() {
    return NativeMotion.enabled(h, h.motionEnabled);
  }

  void animate(ValueAnimator animator) {
    animations.add(animator);
    animator.start();
  }

  void stopAnimations() {
    for (Animator animator : new ArrayList<>(animations)) animator.cancel();
    animations.clear();
  }

  void addGlow() {
    if (glow.getParent() instanceof ViewGroup) ((ViewGroup) glow.getParent()).removeView(glow);
    addView(glow, new LayoutParams(-1, -1));
  }

  void egg() {
    addGlow();
    Egg art = new Egg();
    addView(art, new LayoutParams(-1, -1));
    Button open = h.button("알 열기", this::openEgg);
    open.setBackgroundColor(Color.TRANSPARENT);
    open.setTextColor(Color.TRANSPARENT);
    open.setContentDescription("알을 열어 뽑기 결과 보기");
    addView(open, new LayoutParams(h.dp(240), h.dp(240), Gravity.CENTER));
    if (motion()) {
      ValueAnimator idle = ValueAnimator.ofFloat(0, 1);
      idle.setDuration(1600);
      idle.setRepeatCount(ValueAnimator.INFINITE);
      idle.setInterpolator(new LinearInterpolator());
      idle.addUpdateListener(
          a -> {
            if (!opening) {
              art.idle = (Float) a.getAnimatedValue();
              art.invalidate();
            }
          });
      animate(idle);
    }
  }

  void openEgg() {
    if (opening || revealed || detached) return;
    opening = true;
    h.sound.effect("draw");
    if (!motion()) {
      cards(true);
      return;
    }
    Egg egg = (Egg) getChildAt(1);
    animation = ValueAnimator.ofFloat(0, 1);
    animation.setDuration(700);
    animation.setInterpolator(new LinearInterpolator());
    animation.addUpdateListener(
        a -> {
          egg.shake = (Float) a.getAnimatedValue();
          egg.invalidate();
        });
    animation.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator a) {
            if (isAttachedToWindow() && !detached && !revealed) cards(true);
          }
        });
    animate(animation);
  }

  void cards() {
    cards(false);
  }

  void cards(boolean playEffect) {
    revealed = true;
    stopAnimations();
    removeAllViews();
    addGlow();
    glow.revealed = true;
    if (playEffect) h.sound.effect(topRank >= 6 ? "drawlegend" : topRank >= 4 ? "drawrare" : "aug");
    LinearLayout page = k.column();
    page.setGravity(Gravity.CENTER);
    page.setPadding(h.dp(12), h.dp(10), h.dp(12), h.dp(10));
    addView(page, new LayoutParams(-1, -1));
    final TextView selectedLabel = k.note("");
    if (flow != null && units.length() > 1) {
      selectedLabel.setTextColor(JournalTheme.PAPER);
      selectedLabel.setGravity(Gravity.CENTER);
      selectedLabel.setSingleLine(true);
      selectedLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
      page.addView(selectedLabel);
    }
    HorizontalScrollView scroll = new HorizontalScrollView(h);
    scroll.setFillViewport(true);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = k.row();
    row.setGravity(Gravity.CENTER);
    row.setPadding(h.dp(6), h.dp(6), h.dp(6), h.dp(6));
    scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2, Gravity.CENTER));
    ScrollView cardScroll = k.scroll(scroll);
    cardScroll.setTag("acquisition-cards-scroll");
    cardScroll.setFillViewport(true);
    page.addView(cardScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    int available =
        getHeight() > 0
            ? (int) (getHeight() / h.getResources().getDisplayMetrics().density)
            : h.contentHeightDp();
    int height = Math.max(116, available - (flow == null ? 104 : 168)),
        width = Math.max(84, Math.min(units.length() == 1 ? 220 : 168, (int) (height * 3 / 4.15f)));
    final LinearLayout footer = k.column();
    final ArrayList<RevealCard> shown = new ArrayList<>();
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          footer.removeAllViews();
          if (flow != null && units.length() > 0) {
            flow.selected = Math.max(0, Math.min(flow.selected, units.length() - 1));
            footer.addView(flow.actions(units.optJSONObject(flow.selected)));
            selectedLabel.setText(
                "선택 · " + units.optJSONObject(flow.selected).optString("name") + " · 카드를 눌러 변경");
            for (int j = 0; j < shown.size(); j++) {
              RevealCard selectedCard = shown.get(j);
              selectedCard.setSelected(j == flow.selected);
              selectedCard.front.setBackground(
                  j == flow.selected
                      ? new GamePanelDrawable(h)
                      : new GamePaperDrawable(h, tier(units.optJSONObject(j).optString("tier"))));
              selectedCard.setElevation(h.dp(j == flow.selected ? 8 : 2));
              selectedCard.setContentDescription(
                  units.optJSONObject(j).optString("name")
                      + (j == flow.selected ? " · 선택됨" : " · 눌러 선택"));
            }
          }
          Button ok = k.action(null, "확인", this::finish);
          ok.setTag("acquisition-confirm");
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(180), h.dp(48));
          lp.gravity = Gravity.CENTER_HORIZONTAL;
          lp.topMargin = h.dp(8);
          footer.addView(ok, lp);
        };
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit == null) continue;
      RevealCard card = new RevealCard(unit, width);
      shown.add(card);
      final int index = i;
      if (flow != null) {
        card.setTag("acquisition-card-" + unit.optString("id"));
        card.setOnClickListener(
            v -> {
              if (h.busy || card.front.getVisibility() != VISIBLE) return;
              flow.selected = index;
              paint[0].run();
            });
      }
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(h.dp(width), card.preferredHeight);
      lp.setMargins(h.dp(5), h.dp(6), h.dp(5), h.dp(6));
      row.addView(card, lp);
      if (playEffect && motion()) {
        final long delay = Math.min(i, 12) * 120L;
        ValueAnimator flip = ValueAnimator.ofFloat(0, 1);
        flip.setDuration(500);
        flip.setStartDelay(delay);
        flip.addUpdateListener(a -> card.flip((Float) a.getAnimatedValue()));
        animate(flip);
      } else card.flip(1);
    }
    page.addView(
        footer,
        new LinearLayout.LayoutParams(
            Math.min(h.dp(600), h.getResources().getDisplayMetrics().widthPixels - h.dp(24)), -2));
    paint[0].run();
    if (motion()) {
      ValueAnimator rays = ValueAnimator.ofFloat(0, 360);
      rays.setDuration(14000);
      rays.setInterpolator(new LinearInterpolator());
      rays.setRepeatCount(topRank >= 6 ? ValueAnimator.INFINITE : 0);
      rays.addUpdateListener(
          a -> {
            glow.rotation = (Float) a.getAnimatedValue();
            glow.invalidate();
          });
      if (topRank >= 6) animate(rays);
      if (playEffect) {
        ValueAnimator flash = ValueAnimator.ofFloat(1, 0);
        flash.setDuration(500);
        flash.addUpdateListener(
            a -> {
              glow.flash = (Float) a.getAnimatedValue();
              glow.invalidate();
            });
        animate(flash);
      }
    }
  }

  void finish() {
    if (leaving) return;
    leaving = true;
    stopAnimations();
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
    stopAnimations();
    animate().cancel();
    super.onDetachedFromWindow();
  }

  final class RevealCard extends FrameLayout {
    final View front, back;
    final int preferredHeight;

    RevealCard(JSONObject unit, int width) {
      super(h);
      int color = tier(unit.optString("tier"));
      LinearLayout face = k.column();
      face.setGravity(Gravity.CENTER);
      face.setPadding(h.dp(8), h.dp(8), h.dp(8), h.dp(8));
      face.setBackground(new GamePaperDrawable(h, color));
      FrameLayout photo = new FrameLayout(h);
      photo.addView(
          k.image("/thumbs/" + unit.optString("img") + ".jpg", width - 16, width - 16),
          new LayoutParams(-1, -1));
      if (unit.optBoolean("first")) {
        TextView badge = h.text("NEW", 11);
        badge.setGravity(Gravity.CENTER);
        fitBadge(badge);
        badge.setBackground(new GamePaperDrawable(h, JournalTheme.BRASS));
        LayoutParams box = new LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        box.setMargins(h.dp(4), h.dp(4), 0, 0);
        photo.addView(badge, box);
      }
      if (unit.optInt("n") > 1) {
        TextView count = h.text("×" + unit.optInt("n"), 11);
        count.setGravity(Gravity.CENTER);
        fitBadge(count);
        count.setBackground(new GamePaperDrawable(h));
        LayoutParams box = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        box.setMargins(0, h.dp(4), h.dp(4), 0);
        photo.addView(count, box);
      }
      face.addView(photo, new LinearLayout.LayoutParams(-1, h.dp(width - 16)));
      TextView name = h.text(unit.optString("name"), width < 110 ? 12 : 14);
      name.setTypeface(h.headline);
      name.setGravity(Gravity.CENTER);
      name.setTextLocale(java.util.Locale.KOREA);
      name.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
      if (android.os.Build.VERSION.SDK_INT >= 33)
        name.setLineBreakWordStyle(
            android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
      face.addView(name, new LinearLayout.LayoutParams(-1, -2));
      TextView grade = k.note(unit.optString("tierName"));
      grade.setTextSize(13);
      grade.setTextColor(tierInk(unit.optString("tier")));
      grade.setGravity(Gravity.CENTER);
      face.addView(grade);
      // 뒤집기용 MATCH_PARENT 뒷면이 ScrollView의 남는 높이까지 카드를 늘리지 않게 한다.
      // 원문 이름·등급·배지를 포함한 앞면을 실제 글꼴로 먼저 측정한다.
      face.measure(
          MeasureSpec.makeMeasureSpec(h.dp(width), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      preferredHeight = Math.max(h.dp(width * 4.15f / 3), face.getMeasuredHeight());
      front = face;
      back =
          new View(h) {
            final Paint p = new Paint(3);

            @Override
            protected void onDraw(Canvas c) {
              RectF bounds = new RectF(0, 0, getWidth(), getHeight());
              GameButtonDrawable.drawSurface(c, bounds, JournalTheme.TEAL, h.dp(14), h.dp(3), 0, p);
              float w = getWidth() * .38f, x = getWidth() / 2f, y = getHeight() / 2f;
              p.setColor(JournalTheme.BRASS);
              p.setStyle(Paint.Style.STROKE);
              p.setStrokeWidth(h.dp(2));
              c.drawCircle(x, y, w * .6f, p);
              p.setColor(JournalTheme.PAPER);
              c.drawLine(x, y - w * .36f, x, y + w * .36f, p);
              c.drawLine(x - w * .22f, y - w * .1f, x + w * .22f, y - w * .1f, p);
              c.drawArc(
                  new RectF(x - w * .38f, y - w * .16f, x + w * .38f, y + w * .36f),
                  0,
                  180,
                  false,
                  p);
              c.drawCircle(x, y - w * .36f, w * .1f, p);
              p.setStyle(Paint.Style.FILL);
            }
          };
      addView(front, new LayoutParams(-1, -1));
      addView(back, new LayoutParams(-1, -1));
      if (rank(unit.optString("tier")) >= 6) setElevation(h.dp(6));
      flip(0);
    }

    void fitBadge(TextView label) {
      label.setPadding(0, 0, 0, 0);
      label.setSingleLine(true);
      label.setMinWidth(
          Math.max(
              h.dp(36),
              (int) Math.ceil(label.getPaint().measureText(label.getText().toString()))
                  + h.dp(12)));
      label.setMinHeight(Math.max(h.dp(20), label.getLineHeight() + h.dp(10)));
    }

    void flip(float progress) {
      front.setVisibility(progress >= .5f ? VISIBLE : INVISIBLE);
      back.setVisibility(progress < .5f ? VISIBLE : INVISIBLE);
      setScaleX(Math.max(.02f, Math.abs((float) Math.cos(progress * Math.PI))));
      setAlpha(.55f + .45f * Math.min(1, progress * 4));
    }
  }

  final class Glow extends View {
    final Paint p = new Paint(3);
    float flash, rotation;
    boolean revealed;

    Glow() {
      super(h);
    }

    @Override
    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f,
          y = getHeight() * .45f,
          radius = Math.max(1, Math.max(getWidth(), getHeight()) * .55f);
      NativeResultBackdrop.draw(h, c, getWidth(), getHeight(), p);
      if (revealed && topRank >= 6) {
        c.save();
        c.rotate(rotation, x, y);
        p.setColor(JournalTheme.BRASS);
        p.setAlpha(30);
        for (int angle = 0; angle < 360; angle += 22) {
          Path ray = new Path();
          ray.moveTo(x, y);
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle)));
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle + 8)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle + 8)));
          ray.close();
          c.drawPath(ray, p);
        }
        c.restore();
        p.setAlpha(255);
      }
      if (flash > 0) {
        p.setShader(
            new RadialGradient(
                x,
                y,
                radius * (1.6f - flash * 1.2f),
                new int[] {
                  JournalTheme.PAPER,
                  JournalTheme.BRASS & 0x00ffffff | 0x77000000,
                  Color.TRANSPARENT
                },
                new float[] {0, .4f, 1},
                Shader.TileMode.CLAMP));
        p.setAlpha((int) (flash * 255));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null);
        p.setAlpha(255);
      }
    }
  }

  final class Egg extends View {
    final Paint p = new Paint(3);
    float shake, idle;

    Egg() {
      super(h);
    }

    @Override
    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f,
          y = getHeight() / 2f,
          w = Math.min(h.dp(130), getHeight() * .36f),
          height = w * 1.27f;
      float bob = opening ? 0 : (float) Math.sin(idle * Math.PI) * h.dp(6);
      c.save();
      c.translate(0, -bob);
      c.rotate(
          opening
              ? (float) Math.sin(shake * Math.PI * 2 * 700 / 120) * 8
              : (float) Math.sin(idle * Math.PI) * -3,
          x,
          y);
      p.setShader(
          new RadialGradient(
              x,
              y,
              w * .8f,
              topColor & 0x00ffffff | 0xa0000000,
              Color.TRANSPARENT,
              Shader.TileMode.CLAMP));
      c.drawCircle(x, y, w * .8f, p);
      p.setShader(
          new RadialGradient(
              x - w * .18f,
              y - height * .2f,
              w,
              new int[] {JournalTheme.PAPER, 0xffe4d6b5, JournalTheme.BRASS},
              null,
              Shader.TileMode.CLAMP));
      RectF egg = new RectF(x - w / 2, y - height / 2, x + w / 2, y + height / 2);
      c.drawOval(egg, p);
      p.setShader(null);
      p.setColor(JournalTheme.LEATHER);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(h.dp(4));
      c.drawOval(egg, p);
      p.setStyle(Paint.Style.FILL);
      c.restore();
      p.setColor(JournalTheme.PAPER);
      p.setAlpha(opening ? 255 : (int) (170 + Math.cos(idle * Math.PI * 2) * 70));
      p.setTypeface(h.prose);
      p.setTextSize(h.dp(15));
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText("눌러서 열기", x, y + height / 2 + h.dp(32), p);
      p.setAlpha(255);
    }
  }
}
