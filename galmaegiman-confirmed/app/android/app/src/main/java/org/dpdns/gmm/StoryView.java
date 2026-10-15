package org.dpdns.gmm;

import android.graphics.*;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;
import java.util.regex.*;
import org.json.*;

/** 장면과 좌우 인물을 유지하는 네이티브 이야기 화면. */
final class StoryView extends FrameLayout implements FullBleed {
  final MainActivity host;
  final JSONArray lines;
  final Runnable end;
  final String scene;
  String foe, speaker = "N", leadImage = "", foeImage = "";
  int index = 0;
  final LinearLayout bubble, words;
  final TextView who, say, count, badge;
  final ScrollView textScroll;
  final Button next, skip;
  final Scene canvas;
  final SceneMotion motion;
  LinearLayout titleCard;
  boolean ended;
  final Runnable fadeTitle =
      () -> {
        if (titleCard != null)
          titleCard.animate().alpha(0).setDuration(650).withEndAction(this::dismissTitle).start();
      };
  String fullText = "";
  int shown;
  boolean typing;
  final Runnable typeTick =
      new Runnable() {
        public void run() {
          if (!typing || !isAttachedToWindow()) return;
          shown = Math.min(fullText.codePointCount(0, fullText.length()), shown + 1);
          say.setText(fullText.substring(0, fullText.offsetByCodePoints(0, shown)));
          typing = shown < fullText.codePointCount(0, fullText.length());
          if (typing) host.api.ui.postDelayed(this, 28);
          else say.setText(highlight(fullText));
        }
      };

  void advance() {
    if (ended) return;
    if (titleCard != null) {
      dismissTitle();
      return;
    }
    if (typing) {
      finishTyping();
      return;
    }
    if (++index >= lines.length()) finish();
    else update();
  }

  void finishTyping() {
    host.api.ui.removeCallbacks(typeTick);
    typing = false;
    shown = fullText.codePointCount(0, fullText.length());
    say.setText(highlight(fullText));
  }

  static CharSequence highlight(String text) {
    SpannableString result = new SpannableString(text);
    Matcher quoted = Pattern.compile("「[^」]+」").matcher(text);
    while (quoted.find()) {
      result.setSpan(
          new ForegroundColorSpan(0xffa46b13),
          quoted.start(),
          quoted.end(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      result.setSpan(
          new StyleSpan(Typeface.BOLD),
          quoted.start(),
          quoted.end(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return result;
  }

  void finish() {
    if (ended) return;
    ended = true;
    host.api.ui.removeCallbacks(typeTick);
    dismissTitle();
    end.run();
  }

  void dismissTitle() {
    removeCallbacks(fadeTitle);
    if (titleCard != null) {
      titleCard.animate().cancel();
      removeView(titleCard);
      titleCard = null;
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    motion.start();
    // 생성자에서는 아직 붙지 않은 View의 공용 실행 큐에 넣지 않는다.
    // 실제 장면 부착 시 한 번만 예약해 화면 전환 직후에도 28ms 간격을 지킨다.
    if (typing) {
      host.api.ui.removeCallbacks(typeTick);
      host.api.ui.postDelayed(typeTick, 28);
    }
    if (titleCard != null) postDelayed(fadeTitle, 1300);
  }

  @Override
  protected void onDetachedFromWindow() {
    host.api.ui.removeCallbacks(typeTick);
    typing = false;
    removeCallbacks(fadeTitle);
    if (titleCard != null) titleCard.animate().cancel();
    motion.close();
    super.onDetachedFromWindow();
  }

  StoryView(
      MainActivity a,
      JSONArray lines,
      String caption,
      String scene,
      String fallback,
      Runnable end) {
    super(a);
    host = a;
    this.lines = lines;
    this.scene = scene;
    this.end = end;
    motion = new SceneMotion(this, a);
    foe = fallback;
    JSONArray team = a.list(a.state, "team");
    if (team.length() > 0) leadImage = a.unit(team.optString(0)).optString("image");
    for (int i = 0; i < lines.length(); i++) {
      String id = lines.optJSONObject(i).optString("who");
      if (id.matches("[VDTEIN]\\d+")) {
        foe = id;
        break;
      }
    }
    JSONObject portraits = a.state.optJSONObject("portraits");
    if (portraits != null) foeImage = portraits.optString(foe);
    canvas = new Scene();
    addView(canvas);
    badge = a.text(caption, 20);
    badge.setTypeface(a.headline);
    badge.setTextColor(Color.WHITE);
    badge.setShadowLayer(a.dp(2), 0, a.dp(1), 0xff302b25);
    badge.setSingleLine(true);
    badge.setEllipsize(android.text.TextUtils.TruncateAt.END);
    badge.setVisibility(caption.isEmpty() ? GONE : VISIBLE);
    badge.setBackground(null);
    badge.setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8));
    addView(badge);
    bubble = new LinearLayout(a);
    bubble.setGravity(Gravity.CENTER_VERTICAL);
    bubble.setBackground(new GamePaperDrawable(a));
    bubble.setPadding(a.dp(20), a.dp(18), a.dp(18), a.dp(12));
    addView(bubble);
    words = new LinearLayout(a);
    words.setOrientation(LinearLayout.VERTICAL);
    bubble.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
    who = a.text("", 17);
    who.setTypeface(a.headline);
    who.setGravity(Gravity.CENTER_VERTICAL);
    who.setPadding(a.dp(14), a.dp(5), a.dp(14), a.dp(6));
    who.setSingleLine(true);
    who.setEllipsize(android.text.TextUtils.TruncateAt.END);
    who.setTag("story-speaker");
    addView(who);
    say = a.text("", 18);
    say.setTypeface(a.prose);
    say.setLineSpacing(a.dp(2), 1.15f);
    textScroll = new ScrollView(a);
    textScroll.addView(say);
    words.addView(textScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = new LinearLayout(a);
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setGravity(Gravity.CENTER);
    bubble.addView(controls, new LinearLayout.LayoutParams(-2, -1));
    count = a.text("", 13);
    count.setPadding(a.dp(8), 0, a.dp(8), 0);
    count.setGravity(Gravity.CENTER);
    count.setTextColor(JournalTheme.MUTED);
    next = a.button("다음", this::advance);
    setOnClickListener(v -> advance());
    bubble.setOnClickListener(v -> advance());
    say.setOnClickListener(v -> advance());
    next.setBackground(new GameButtonDrawable(a, GameButtonDrawable.PRIMARY));
    next.setTextColor(Color.WHITE);
    controls.addView(next, new LinearLayout.LayoutParams(a.dp(130), a.dp(48)));
    controls.addView(count);
    skip = a.button("건너뛰기", this::finish);
    skip.setBackground(new GameButtonDrawable(a, GameButtonDrawable.PAPER));
    addView(skip);
    update();
    if (a.motionEnabled && !caption.isEmpty()) {
      titleCard = new LinearLayout(a);
      titleCard.setOrientation(LinearLayout.VERTICAL);
      titleCard.setGravity(Gravity.CENTER);
      titleCard.setBackgroundColor(0xd1050a0f);
      String sub = "";
      JSONObject chapter = a.list(a.state, "chapters").optJSONObject(a.chapter);
      if (chapter != null) sub = chapter.optString("title");
      TextView subtitle = a.text(sub, 16);
      subtitle.setTypeface(a.prose);
      subtitle.setTextColor(JournalTheme.BRASS);
      subtitle.setGravity(Gravity.CENTER);
      titleCard.addView(subtitle);
      TextView heading = a.text(caption, 28);
      heading.setTextColor(Color.WHITE);
      heading.setGravity(Gravity.CENTER);
      heading.setMaxLines(3);
      heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
      titleCard.addView(heading);
      titleCard.setOnClickListener(v -> dismissTitle());
      addView(titleCard);
    }
  }

  boolean portraitsReady() {
    return (!leadImage.isEmpty() && host.art.peek(leadImage) != null)
        && (!foeImage.isEmpty() && host.art.peek(foeImage) != null);
  }

  void update() {
    JSONObject l = lines.optJSONObject(index);
    speaker = l.optString("who");
    String name = "";
    if (speaker.equals("ME")) {
      JSONArray t = host.list(host.state, "team");
      if (t.length() > 0) name = host.unit(t.optString(0)).optString("name");
    } else if (speaker.equals("TIP")) {
      name = "대결 팁";
    } else if (!speaker.equals("N")) {
      JSONObject names = host.state.optJSONObject("names");
      name = names == null ? speaker : names.optString(speaker, speaker);
      JSONObject portraits = host.state.optJSONObject("portraits");
      JSONObject actor = actor(speaker);
      String image = portraits == null ? "" : portraits.optString(speaker);
      if (image.isEmpty() && actor != null) image = actor.optString("image");
      if (image.isEmpty() && speaker.equals("마을 회관 방송")) image = "/duel/sprites/HALL_front.webp";
      if (!image.isEmpty()) {
        foeImage = image;
        foe = speaker;
      }
    }
    who.setText(name);
    who.setTextColor(Color.WHITE);
    android.graphics.drawable.GradientDrawable nameplate =
        new android.graphics.drawable.GradientDrawable();
    nameplate.setColor(speaker.startsWith("V") ? 0xff8c4b40 : JournalTheme.TEAL);
    nameplate.setCornerRadius(host.dp(7));
    nameplate.setStroke(host.dp(1), JournalTheme.BRASS);
    who.setBackground(nameplate);
    who.setVisibility(name.isEmpty() ? GONE : VISIBLE);
    host.api.ui.removeCallbacks(typeTick);
    fullText = l.optString("say");
    shown = 0;
    typing = host.motionEnabled && !fullText.isEmpty();
    say.setText(typing ? "" : highlight(fullText));
    if (typing && isAttachedToWindow()) host.api.ui.postDelayed(typeTick, 28);
    count.setText((index + 1) + " / " + lines.length());
    textScroll.scrollTo(0, 0);
    requestLayout();
    canvas.invalidate();
  }

  JSONObject actor(String id) {
    JSONObject actors = host.state.optJSONObject("sceneActors");
    return actors == null ? null : actors.optJSONObject(id);
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    int fullW = MeasureSpec.getSize(ws), fullH = MeasureSpec.getSize(hs);
    RectF safe = host.safeBounds(fullW, fullH);
    int w = Math.round(safe.width()), h = Math.round(safe.height());
    float density = getResources().getDisplayMetrics().density;
    say.setTextSize(host.uiText(h / density >= 600 ? 23 : 19));
    int actionH = Math.max(host.dp(48), Math.min(host.dp(64), (int) (h * .095f)));
    next.setLayoutParams(new LinearLayout.LayoutParams((int) (actionH * 2.7f), actionH));
    count.setTextSize(h / density >= 600 ? 17 : 14);
    setMeasuredDimension(fullW, fullH);
    canvas.measure(
        MeasureSpec.makeMeasureSpec(fullW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(fullH, MeasureSpec.EXACTLY));
    int bh =
        Math.min(
            (int) (h * .43f),
            Math.max(
                host.dp(h / density < 600 ? 132 : 168),
                (int) (h * .28f)));
    bubble.measure(
        MeasureSpec.makeMeasureSpec(Math.min(host.dp(1160), (int) (w * .89f)), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY));
    who.measure(
        MeasureSpec.makeMeasureSpec(Math.min(host.dp(380), (int) (w * .48f)), MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(host.dp(52), MeasureSpec.AT_MOST));
    badge.measure(
        MeasureSpec.makeMeasureSpec((int) (w * .45f), MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.AT_MOST));
    if (titleCard != null)
      titleCard.measure(
          MeasureSpec.makeMeasureSpec(fullW, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(fullH, MeasureSpec.EXACTLY));
    skip.measure(
        MeasureSpec.makeMeasureSpec(host.dp(124), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    RectF safe = host.safeBounds(getWidth(), getHeight());
    int w = Math.round(safe.width()),
        h = Math.round(safe.height()),
        ox = Math.round(safe.left),
        oy = Math.round(safe.top),
        x = ox + (w - bubble.getMeasuredWidth()) / 2,
        bottom = oy + h - host.dp(12);
    canvas.layout(0, 0, getWidth(), getHeight());
    if (titleCard != null) titleCard.layout(0, 0, getWidth(), getHeight());
    bubble.layout(x, bottom - bubble.getMeasuredHeight(), x + bubble.getMeasuredWidth(), bottom);
    who.layout(
        x + host.dp(22),
        bubble.getTop() - who.getMeasuredHeight() / 2,
        x + host.dp(22) + who.getMeasuredWidth(),
        bubble.getTop() + who.getMeasuredHeight() / 2);
    badge.layout(
        ox + host.dp(20),
        oy + host.dp(10),
        ox + host.dp(20) + badge.getMeasuredWidth(),
        oy + host.dp(10) + badge.getMeasuredHeight());
    skip.layout(ox + w - host.dp(134), oy + host.dp(8), ox + w - host.dp(10), oy + host.dp(56));
  }

  static ColorMatrix hue(float angle) {
    float cos = (float) Math.cos(Math.toRadians(angle)),
        sin = (float) Math.sin(Math.toRadians(angle));
    return new ColorMatrix(
        new float[] {
          .213f + cos * .787f - sin * .213f,
          .715f - cos * .715f - sin * .715f,
          .072f - cos * .072f + sin * .928f,
          0,
          0,
          .213f - cos * .213f + sin * .143f,
          .715f + cos * .285f + sin * .140f,
          .072f - cos * .072f - sin * .283f,
          0,
          0,
          .213f - cos * .213f - sin * .787f,
          .715f - cos * .715f + sin * .715f,
          .072f + cos * .928f + sin * .072f,
          0,
          0,
          0,
          0,
          0,
          1,
          0
        });
  }

  final class Scene extends View {
    final Paint p = new Paint(3);

    Scene() {
      super(host);
    }

    void fit(Canvas c, String path, String id, RectF area, boolean dim, float pulse) {
      if (path.isEmpty()) return;
      Bitmap b = host.art.scene(path, this::invalidate);
      if (b == null) return;
      Rect src = host.art.alpha(b, path);
      float s = Math.min(area.width() / src.width(), area.height() / src.height()),
          w = src.width() * s,
          h = src.height() * s;
      JSONObject actor = actor(id);
      String tint = actor == null ? "" : actor.optString("tint");
      ColorMatrix filter = new ColorMatrix();
      boolean colored = false;
      if (tint.equals("shadow") || tint.equals("mirror") || tint.equals("peanut")) {
        boolean real = actor.optBoolean("realTintArt");
        if (!real) {
          filter.setSaturation(tint.equals("peanut") ? .4f : .3f);
          float darkness = tint.equals("shadow") ? .25f : tint.equals("mirror") ? .35f : .3f;
          filter.postConcat(
              new ColorMatrix(
                  new float[] {
                    darkness, 0, 0, 0, 0, 0, darkness, 0, 0, 0, 0, 0, darkness, 0, 0, 0, 0, 0, 1, 0
                  }));
          colored = true;
        }
        if (tint.equals("mirror")) {
          filter.postConcat(hue(200));
          colored = true;
        } else if (tint.equals("peanut") && !real) {
          filter.postConcat(
              new ColorMatrix(
                  new float[] {
                    .64f, .46f, .11f, 0, 0, .21f, .83f, .10f, 0, 0, .16f, .32f, .54f, 0, 0, 0, 0, 0,
                    1, 0
                  }));
        }
      }
      c.save();
      c.translate(0, -area.height() * .006f * pulse);
      c.scale(1 + .006f * pulse, 1 - .004f * pulse, area.centerX(), area.bottom);
      p.setAlpha(255);
      if (dim) {
        ColorMatrix muted = new ColorMatrix();
        muted.setSaturation(.85f);
        muted.postConcat(
            new ColorMatrix(
                new float[] {.8f, 0, 0, 0, 0, 0, .8f, 0, 0, 0, 0, 0, .8f, 0, 0, 0, 0, 0, 1, 0}));
        filter.postConcat(muted);
        colored = true;
      }
      RectF destination =
          new RectF(area.centerX() - w / 2, area.bottom - h, area.centerX() + w / 2, area.bottom);
      if (!tint.isEmpty()
          && !tint.equals("null")
          && !(tint.equals("shadow") && actor.optBoolean("realTintArt"))) {
        p.setColorFilter(
            new PorterDuffColorFilter(
                tint.equals("mirror") ? 0xffb9a0ff : 0xffffd34d, PorterDuff.Mode.SRC_IN));
        p.setMaskFilter(
            new BlurMaskFilter(
                host.dp(tint.equals("peanut") ? 10 : 6), BlurMaskFilter.Blur.NORMAL));
        p.setAlpha(160);
        c.drawBitmap(b, src, destination, p);
        p.setMaskFilter(null);
        p.setAlpha(255);
      }
      p.setColorFilter(colored ? new ColorMatrixColorFilter(filter) : null);
      c.drawBitmap(b, src, destination, p);
      p.setAlpha(255);
      p.setColorFilter(null);
      c.restore();
    }

    @Override
    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      Bitmap bg =
          scene.equals("deep")
              ? host.art.asset("deepstory.webp")
              : host.art.get("/duel/bg/" + scene + ".jpg", this::invalidate);
      if (bg == null) bg = host.art.asset("harbor.jpg");
      if (bg != null) {
        float s = Math.max(w / bg.getWidth(), h / bg.getHeight()),
            bw = bg.getWidth() * s,
            bh = bg.getHeight() * s;
        c.drawBitmap(
            bg, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), p);
      }
      RectF safe = host.safeBounds(getWidth(), getHeight());
      c.save();
      c.translate(safe.left, safe.top);
      w = safe.width();
      h = safe.height();
      JSONArray team = host.list(host.state, "team");
      String leadId = team.length() == 0 ? "" : host.unit(team.optString(0)).optString("id");
      boolean narration = speaker.equals("N") || speaker.equals("TIP");
      float floor =
          (bubble.getTop() > 0 ? bubble.getTop() - safe.top : h * .70f) - host.dp(14);
      fit(
          c,
          leadImage,
          leadId,
          new RectF(w * .09f, h * .19f, w * .42f, floor),
          !narration && !speaker.equals("ME"),
          motion.pulse(2800, 0));
      JSONObject foeActor = actor(foe);
      boolean boss = foeActor != null && foeActor.optBoolean("boss");
      fit(
          c,
          foeImage,
          foe,
          new RectF(w * .59f, h * (boss ? .11f : .18f), w * .92f, floor),
          !narration && speaker.equals("ME"),
          motion.pulse(3300, 0));
      c.restore();
    }
  }
}
