package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** 장면과 좌우 인물을 유지하는 네이티브 이야기 화면. */
final class StoryView extends FrameLayout {
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
    foe = fallback;
    JSONArray team = a.list(a.state, "team");
    if (team.length() > 0) leadImage = a.unit(team.optString(0)).optString("image");
    for (int i = 0; i < lines.length(); i++) {
      String id = lines.optJSONObject(i).optString("who");
      if (id.startsWith("V")) {
        foe = id;
        break;
      }
    }
    JSONObject portraits = a.state.optJSONObject("portraits");
    if (portraits != null) foeImage = portraits.optString(foe);
    canvas = new Scene();
    addView(canvas);
    badge = a.text(caption, 16);
    badge.setBackground(a.material(MainActivity.CREAM));
    badge.setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8));
    addView(badge);
    bubble = new LinearLayout(a);
    bubble.setGravity(Gravity.CENTER_VERTICAL);
    bubble.setBackground(new FrameDrawable(a, "dialog.webp"));
    bubble.setPadding(a.dp(24), a.dp(16), a.dp(24), a.dp(16));
    addView(bubble);
    words = new LinearLayout(a);
    words.setOrientation(LinearLayout.VERTICAL);
    bubble.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
    who = a.text("", 16);
    who.setTypeface(a.headline);
    words.addView(who);
    say = a.text("", 18);
    say.setTypeface(a.prose);
    say.setLineSpacing(a.dp(2), 1.15f);
    textScroll = new ScrollView(a);
    textScroll.addView(say);
    words.addView(textScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = new LinearLayout(a);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    bubble.addView(controls, new LinearLayout.LayoutParams(-2, -1));
    count = a.text("", 13);
    count.setPadding(a.dp(8), 0, a.dp(8), 0);
    controls.addView(count);
    next =
        a.button(
            "다음",
            () -> {
              if (++index >= lines.length()) end.run();
              else update();
            });
    next.setTextColor(Color.TRANSPARENT);
    next.setBackground(new ArtButton(a, "next"));
    controls.addView(next, new LinearLayout.LayoutParams(a.dp(130), a.dp(48)));
    skip = a.button("건너뛰기", end);
    skip.setTextColor(Color.TRANSPARENT);
    skip.setBackground(new ArtButton(a, "skip"));
    addView(skip);
    update();
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
    } else if (!speaker.equals("N")) {
      JSONObject names = host.state.optJSONObject("names");
      name = names == null ? speaker : names.optString(speaker, speaker);
      JSONObject portraits = host.state.optJSONObject("portraits");
      String image = portraits == null ? "" : portraits.optString(speaker);
      if (!image.isEmpty()) {
        foeImage = image;
        foe = speaker;
      }
    }
    who.setText(name);
    who.setVisibility(name.isEmpty() ? GONE : VISIBLE);
    say.setText(l.optString("say"));
    count.setText((index + 1) + " / " + lines.length());
    textScroll.scrollTo(0, 0);
    canvas.invalidate();
  }

  @Override
  protected void onMeasure(int ws, int hs) {
    int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs), small = host.dp(8);
    float density = getResources().getDisplayMetrics().density;
    say.setTextSize(Math.max(14, Math.min(24, h / density * .034f)));
    int actionH = Math.max(host.dp(48), Math.min(host.dp(64), (int) (h * .095f)));
    next.setLayoutParams(new LinearLayout.LayoutParams((int) (actionH * 2.7f), actionH));
    count.setTextSize(h / density >= 600 ? 17 : 13);
    setMeasuredDimension(w, h);
    canvas.measure(
        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    int bh = Math.min((int) (h * .43f), Math.max(host.dp(100), (int) (h * .27f)));
    bubble.measure(
        MeasureSpec.makeMeasureSpec((int) (w * .68f), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY));
    badge.measure(
        MeasureSpec.makeMeasureSpec((int) (w * .45f), MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.AT_MOST));
    skip.measure(
        MeasureSpec.makeMeasureSpec(host.dp(124), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(host.dp(48), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int w = getWidth(), h = getHeight(), x = (int) (w * .16f), bottom = h - host.dp(12);
    canvas.layout(0, 0, w, h);
    bubble.layout(x, bottom - bubble.getMeasuredHeight(), x + bubble.getMeasuredWidth(), bottom);
    badge.layout(
        host.dp(20),
        host.dp(10),
        host.dp(20) + badge.getMeasuredWidth(),
        host.dp(10) + badge.getMeasuredHeight());
    skip.layout(w - host.dp(134), host.dp(8), w - host.dp(10), host.dp(56));
  }

  final class Scene extends View {
    final Paint p = new Paint(3);

    Scene() {
      super(host);
    }

    void fit(Canvas c, String path, RectF area, boolean dim) {
      if (path.isEmpty()) return;
      Bitmap b = host.art.scene(path, this::invalidate);
      if (b == null) return;
      Rect src = host.art.alpha(b, path);
      float s = Math.min(area.width() / src.width(), area.height() / src.height()),
          w = src.width() * s,
          h = src.height() * s;
      p.setAlpha(255);
      p.setColorFilter(
          dim
              ? new ColorMatrixColorFilter(
                  new float[] {.8f, 0, 0, 0, 0, 0, .8f, 0, 0, 0, 0, 0, .8f, 0, 0, 0, 0, 0, 1, 0})
              : null);
      c.drawBitmap(
          b,
          src,
          new RectF(area.centerX() - w / 2, area.bottom - h, area.centerX() + w / 2, area.bottom),
          p);
      p.setAlpha(255);
      p.setColorFilter(null);
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
      fit(
          c,
          leadImage,
          new RectF(w * .07f, h * .22f, w * .42f, h * .93f),
          !speaker.equals("N") && !speaker.equals("ME"));
      fit(c, foeImage, new RectF(w * .54f, h * .1f, w * .9f, h * .86f), speaker.equals("ME"));
    }
  }
}
