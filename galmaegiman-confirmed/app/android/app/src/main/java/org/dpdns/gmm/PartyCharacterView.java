package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.Objects;
import org.json.JSONObject;

/** Party/dispatch artwork uses the approved whole character sprite, never a registration crop. */
final class PartyCharacterView extends LinearLayout {
  // 흰 깃털과 어두운 의상 모두 배경 풍경과 분리되는 불투명한 중간 명도.
  static final int PORTRAIT_BACKPLATE = 0xff87999b;
  final Artwork picture;
  final TextView name;

  PartyCharacterView(MainActivity h, JSONObject unit) {
    super(h);
    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER_HORIZONTAL);
    picture = art(h, unit);
    picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(picture, new LinearLayout.LayoutParams(-1, 0, 1));
    name = nameLabel(h, unit.optString("name"));
    name.setTag("party-character-name");
    addView(name, new LinearLayout.LayoutParams(-1, -2));
    picture.statusChanged = () -> setContentDescription(description(unit) + picture.statusDescription());
    picture.updateAccessibility();
  }

  static Artwork art(MainActivity h, JSONObject unit) { return new Artwork(h, unit); }

  static String description(JSONObject unit) {
    return unit.optString("name") + (unit.optString("tierName").isEmpty() ? "" : " · " + unit.optString("tierName"));
  }

  static String spritePath(JSONObject unit) {
    String id = unit.optString("id");
    String image = unit.optString("image"), img = unit.optString("img");
    if ((unit.has("known") && !unit.optBoolean("known")) || image.contains("/unknown.")
        || image.contains("/q-") || img.equals("unknown") || img.startsWith("q-")) return "";
    // This is the existing top33 identity set. Never substitute a different unit's sprite.
    if (id.matches("(?:D[1-8]|E[1-8]|I[1-9]|T[1-8])"))
      return "/duel/sprites/" + id + "_s0_front.webp";
    // 일반 개체는 서버가 명시한 동일 개체의 기존 원본만 온전히 표시한다.
    // 환생 조상의 전투 정의나 다른 캐릭터 그림으로 바꾸지 않는다.
    if (id.matches("C[1-9][0-9]*") && image.equals("/thumbs/" + id + ".jpg")) return image;
    return "";
  }

  static TextView stateLabel(MainActivity h, String text) {
    TextView label = h.text(text, 13);
    label.setTypeface(h.prose);
    label.setGravity(Gravity.CENTER);
    label.setSingleLine(true);
    label.setHorizontallyScrolling(false);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setIncludeFontPadding(true);
    label.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
    label.setTextColor(JournalTheme.INK);
    label.setBackgroundColor(0xeef3e9d3);
    label.setContentDescription(text);
    return label;
  }

  /** Keep Korean name words intact instead of leaving a final syllable on its own line. */
  static TextView nameLabel(MainActivity h, String fullName) {
    TextView label = new TextView(h) {
      @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.UNSPECIFIED
            ? Integer.MAX_VALUE : View.MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        String display = fullName;
        if (width > 0 && getPaint().measureText(fullName) > width) {
          int split = -1;
          float best = Float.MAX_VALUE;
          for (int i = 1; i < fullName.length() - 1; i++) {
            if (!Character.isWhitespace(fullName.charAt(i))) continue;
            float first = getPaint().measureText(fullName.substring(0, i));
            float second = getPaint().measureText(fullName.substring(i + 1));
            if (first <= width && second <= width && Math.abs(first - second) < best) {
              best = Math.abs(first - second); split = i;
            }
          }
          if (split >= 0) display = fullName.substring(0, split) + "\n" + fullName.substring(split + 1);
        }
        if (!getText().toString().equals(display)) setText(display);
        super.onMeasure(widthSpec, heightSpec);
      }
    };
    label.setText(fullName);
    label.setTextSize(h.uiText(13));
    label.setTypeface(h.headline);
    label.setTextColor(JournalTheme.INK);
    label.setGravity(Gravity.CENTER);
    label.setIncludeFontPadding(false);
    label.setSingleLine(false);
    // No ellipsis or maximum: an unusually long original name must remain readable.
    label.setEllipsize(null);
    label.setPadding(h.dp(4), h.dp(3), h.dp(4), h.dp(3));
    label.setBackgroundColor(0xeef3e9d3);
    label.setContentDescription(fullName);
    label.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return label;
  }

  static TextView liveLabel(MainActivity h, String text) {
    TextView label = stateLabel(h, text);
    label.setSingleLine(false);
    label.setMaxLines(2);
    label.setEllipsize(null);
    return label;
  }

  /** A selected party member has a thin brass edge, independent of the transient pressed fill. */
  static Drawable selection(MainActivity h, boolean selected) {
    return new Drawable() {
      final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      final RectF bounds = new RectF();
      final GamePressState press = new GamePressState();
      int alpha = 255;
      @Override public boolean isStateful() { return true; }
      @Override protected boolean onStateChange(int[] states) {
        boolean changed = press.update(states, true);
        if (changed) invalidateSelf();
        return changed;
      }
      @Override public void draw(Canvas canvas) {
        bounds.set(getBounds()); bounds.inset(h.dp(1), h.dp(1));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(!press.enabled ? 0x30d7d5cd
            : press.pressed ? 0xb010344c : selected ? 0x28628c90 : Color.TRANSPARENT);
        paint.setAlpha(Color.alpha(paint.getColor()) * alpha / 255);
        canvas.drawRoundRect(bounds, h.dp(5), h.dp(5), paint);
        if (selected) {
          paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(h.dp(2));
          paint.setColor(press.enabled ? JournalTheme.BRASS : JournalTheme.LINE);
          paint.setAlpha(alpha);
          canvas.drawRoundRect(bounds, h.dp(5), h.dp(5), paint);
        }
      }
      @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
      @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
  }

  static final class Artwork extends View {
    final MainActivity host;
    final String id, path, identity;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF target = new RectF();
    Bitmap bitmap;
    String status;
    Runnable statusChanged;
    boolean requested;
    int generation;

    Artwork(MainActivity h, JSONObject unit) {
      super(h);
      host = h; id = unit.optString("id"); path = spritePath(unit); identity = description(unit);
      setBackgroundColor(PORTRAIT_BACKPLATE);
      status = path.isEmpty() ? "이미지 없음" : "이미지 준비 중";
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      updateAccessibility();
    }

    String statusDescription() { return status.isEmpty() ? "" : " · " + status; }
    void updateAccessibility() {
      setContentDescription(identity + " · 캐릭터 그림" + statusDescription());
      if (statusChanged != null) statusChanged.run();
    }

    void requestImage() {
      if (requested || path.isEmpty() || !isAttachedToWindow() || host.art == null
          || host.isDestroyed() || host.isFinishing()) return;
      requested = true;
      int serial = ++generation, epoch = host.epoch;
      String account = host.api.token();
      WeakReference<Artwork> weak = new WeakReference<>(this);
      Runnable ready = () -> {
        Artwork view = weak.get();
        if (view == null || serial != view.generation || !view.isAttachedToWindow()
            || view.host.epoch != epoch || !Objects.equals(account, view.host.api.token())
            || view.host.isDestroyed() || view.host.isFinishing()) return;
        view.bitmap = view.host.art.peek(view.path);
        view.status = view.bitmap == null ? "이미지 없음" : "";
        view.updateAccessibility(); view.updateTarget(); view.invalidate();
      };
      if (host.art.scene(path, ready) != null) ready.run();
    }

    void updateTarget() {
      if (bitmap == null) { target.setEmpty(); return; }
      contain(target, getWidth(), getHeight(), bitmap.getWidth(), bitmap.getHeight());
    }

    static void contain(RectF target, int width, int height, int imageWidth, int imageHeight) {
      if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) { target.setEmpty(); return; }
      float scale = Math.min(width / (float) imageWidth, height / (float) imageHeight);
      float w = Math.min(width, imageWidth * scale), h = Math.min(height, imageHeight * scale);
      target.set((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); requestImage(); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      super.onSizeChanged(w, h, ow, oh); updateTarget(); requestImage();
    }
    @Override protected void onDetachedFromWindow() {
      ++generation; requested = false; bitmap = null; target.setEmpty();
      super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (bitmap != null && !bitmap.isRecycled()) { canvas.drawBitmap(bitmap, null, target, paint); return; }
      // An explicit, identity-preserving placeholder is safer than using another character.
      paint.setColor(PORTRAIT_BACKPLATE);
      canvas.drawRoundRect(0, 0, getWidth(), getHeight(), host.dp(4), host.dp(4), paint);
      paint.setColor(JournalTheme.MUTED); paint.setTypeface(host.prose);
      paint.setTextSize(host.dp(12)); paint.setTextAlign(Paint.Align.CENTER);
      if (getWidth() >= paint.measureText(status) + host.dp(8) && getHeight() >= host.dp(20))
        canvas.drawText(status, getWidth()/2f, getHeight()/2f - (paint.ascent()+paint.descent())/2f, paint);
    }
  }
}


