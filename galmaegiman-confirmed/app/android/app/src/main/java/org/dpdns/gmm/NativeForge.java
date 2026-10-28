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
  NativeUnitDossier dossier;
  final int ownerEpoch;
  final String ownerAccount;
  boolean finishDelivered;

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
    ownerEpoch = h.epoch;
    ownerAccount = h.api.token();
    k = new MenuKit(h);
    setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
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
            if (isAttachedToWindow() && current() && !complete && !leaving) showResult(true);
          }
        });
    setOnClickListener(
        v -> {
          if (current() && !complete && !leaving) {
            complete = true;
            animation.cancel();
            complete = false;
            showResult(true);
          }
        });
    post(
        () -> {
          if (!current() || leaving || complete) return;
          h.sound.effect(fail ? "forgefail" : "forge");
          if (motion()) animation.start();
          else showResult(false);
        });
  }

  boolean current() {
    return !detached && !h.isFinishing() && !h.isDestroyed()
        && ownerEpoch == h.epoch && ownerAccount.equals(h.api.token());
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
    if (!current() || leaving) return;
    // 갱신 응답도 현재 결과의 종 ID와 같아야 한다.
    if (unit == null || (result != null && !result.optString("id").equals(unit.optString("id")))) return;
    if (dossier != null) dossier.dismiss();
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
    if (complete || !current() || leaving) return;
    complete = true;
    if (resultCard != null) resultCard.animate().cancel();
    removeAllViews();
    ring.progress = 1;
    addView(ring, new LayoutParams(-1, -1));
    LinearLayout page = k.column();
    page.setGravity(Gravity.CENTER_HORIZONTAL);
    page.setPadding(h.dp(14), h.dp(10), h.dp(14), h.dp(10));
    LayoutParams box =
        new LayoutParams(
            Math.min(h.dp(760), (int) ((getWidth() > 0 ? getWidth() : h.root.getWidth()) * .9f)),
            -1,
            Gravity.CENTER);
    addView(page, box);
    LinearLayout content = k.row();
    content.setTag("forge-result-stage");
    content.setGravity(Gravity.CENTER_VERTICAL);
    content.setPadding(0, h.dp(6), 0, h.dp(6));
    FrameLayout stage = new FrameLayout(h);
    // Successful art uses the height left after the existing action/footer rows.
    // Keep the failure message centered at its original wrap-content height.
    stage.addView(
        content,
        new FrameLayout.LayoutParams(-1, result != null && !fail ? -1 : -2, Gravity.CENTER));
    page.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
    if (result != null && !fail) {
      RegistrationCardView card = new RegistrationCardView(h, result, RegistrationCardView.FULL);
      if (!RegistrationCardView.canShow(result)) {
        card.name.setVisibility(View.VISIBLE);
        card.grade.setVisibility(View.VISIBLE);
      }
      resultCard = card;
      card.setTag("forge-result-card");
      card.setOnClickListener(v -> openDossier());
      card.setContentDescription(RegistrationCardView.description(result) + " · 눌러 대원 기록 보기");
      content.addView(card, new LinearLayout.LayoutParams(0, -1, .55f));
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
    LinearLayout words = k.paper();
    TextView outcome = h.text(fail ? "조합 확인" : "조합 완료!", 24);
    outcome.setTypeface(h.headline);
    words.addView(outcome);
    String resultText =
        text == null || text.isEmpty() ? (fail ? "조합이 맞지 않아 재료는 그대로예요." : "새로운 갈매미를 얻었어요.") : RecruitmentText.display(text);
    TextView message = k.note(resultText);
    message.setMaxLines(3);
    message.setEllipsize(android.text.TextUtils.TruncateAt.END);
    message.setTag("forge-result-message");
    message.setGravity(Gravity.CENTER);
    message.setPadding(0, h.dp(8), 0, h.dp(8));
    words.addView(message, new LinearLayout.LayoutParams(-1, -2));
    words.addView(
        k.action(
            null,
            "결과 내용",
            () ->
                k.openSheet(
                    "조합 결과",
                    () -> {
                      LinearLayout details = k.column();
                      details.addView(k.note(resultText));
                      return details;
                    })));
    LinearLayout.LayoutParams wordsSlot =
        new LinearLayout.LayoutParams(0, -2, result != null && !fail ? .45f : 1);
    wordsSlot.leftMargin = result != null && !fail ? h.dp(12) : 0;
    content.addView(words, wordsSlot);
    LinearLayout actions = k.row();
    if (flow != null && result != null && !fail) {
      actions.addView(NativeUnitDossier.actions(h, flow, result,
          () -> current() && !leaving && dossier == null && !h.busy),
          new LinearLayout.LayoutParams(0, h.dp(48), 1));
    }
    Button ok = k.primary(null, "확인", this::finish);
    ok.setTag("forge-result-confirm");
    actions.addView(ok, new LinearLayout.LayoutParams(h.dp(160), h.dp(48)));
    actions.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, h.dp(48));
    footer.topMargin = h.dp(6);
    page.addView(actions, footer);
    setOnClickListener(null);
  }

  void openDossier() {
    if (!current() || leaving || !complete || fail || result == null || dossier != null || h.busy) return;
    for (int i = 0; i < getChildCount(); i++)
      getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    dossier = new NativeUnitDossier(h, result, flow, this::current, () -> {
      NativeUnitDossier shown = dossier;
      dossier = null;
      if (shown != null) removeView(shown);
      for (int i = 0; i < getChildCount(); i++)
        getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      if (current() && resultCard != null) resultCard.requestFocus();
    });
    addView(dossier, new LayoutParams(-1, -1));
  }

  void deliverFinish() {
    if (!current() || finishDelivered) return;
    finishDelivered = true;
    next.run();
  }

  void finish() {
    if (leaving || !current() || !complete || dossier != null) return;
    leaving = true;
    if (motion())
      animate()
          .alpha(0)
          .setDuration(200)
          .withEndAction(
              () -> {
                deliverFinish();
              })
          .start();
    else deliverFinish();
  }

  @Override
  protected void onDetachedFromWindow() {
    detached = true;
    if (dossier != null) dossier.dismiss();
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
    final RectF card = new RectF();
    final Path clip = new Path();
    final Matrix glowMatrix = new Matrix();
    final RadialGradient glowShader = new RadialGradient(0, 0, 1,
        new int[] {JournalTheme.PAPER, JournalTheme.BRASS & 0x00ffffff | 0x88000000,
            Color.TRANSPARENT}, new float[] {0, .28f, 1}, Shader.TileMode.CLAMP);
    float progress;

    Ring() {
      super(h);
    }

    @Override
    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f, y = getHeight() / 2f, dimension = Math.min(getHeight(), h.dp(540));
      // The production workshop stays visible behind the existing material/result animation.
      p.setColor(0x260c2335);
      c.drawRect(0, 0, getWidth(), getHeight(), p);
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
        card.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        p.setAlpha((int) (opacity * 255));
        clip.rewind();
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
        glowMatrix.setScale(glowRadius, glowRadius);
        glowMatrix.postTranslate(x, y);
        glowShader.setLocalMatrix(glowMatrix);
        p.setShader(glowShader);
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

