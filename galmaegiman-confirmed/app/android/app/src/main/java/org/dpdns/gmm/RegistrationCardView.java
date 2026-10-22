package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import org.json.JSONObject;

/** FULL은 원본 인쇄를 그대로 크게 보여 주고, 동적 상태와 접근성만 별도로 둔다. */
final class RegistrationCardView extends LinearLayout {
  static final int FULL = 0, COMPACT = 1;
  final Artwork picture;
  final TextView name, grade;

  RegistrationCardView(MainActivity h, JSONObject unit, int mode) {
    super(h);
    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER_HORIZONTAL);
    FrameLayout photo = new FrameLayout(h);
    picture = new Artwork(h, unit, mode);
    picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    photo.addView(picture, new FrameLayout.LayoutParams(-1, -1));
    addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
    if (unit.optBoolean("first")) badge(h, photo, "NEW", Gravity.TOP | Gravity.LEFT);
    if (unit.optInt("n") > 1) badge(h, photo, "×" + unit.optInt("n"), Gravity.TOP | Gravity.RIGHT);
    name = h.text(unit.optString("name"), 14);
    name.setTypeface(h.headline);
    name.setMaxLines(2);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    name.setGravity(Gravity.CENTER);
    name.setContentDescription(unit.optString("name"));
    name.setVisibility(mode == FULL ? GONE : VISIBLE);
    name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(name, new LinearLayout.LayoutParams(-1, -2));
    grade = h.text(unit.optString("tierName"), 13);
    grade.setTypeface(h.prose);
    grade.setTextColor(NativeReveal.tierInk(unit.optString("tier")));
    grade.setGravity(Gravity.CENTER);
    grade.setVisibility(mode == FULL ? GONE : VISIBLE);
    grade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(grade, new LinearLayout.LayoutParams(-1, -2));
    // 그림은 중복 낭독하지 않되 실패·미발견 상태는 접근성의 대표 카드로 전달한다.
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    picture.statusChanged = () -> {
      // 이름·등급은 FULL에 이미 인쇄되어 있다. 원본 로딩 실패 때만 읽을 수 있는 대체 문자를 둔다.
      boolean fallback = mode == COMPACT || "이미지 없음".equals(picture.status);
      name.setVisibility(fallback ? VISIBLE : GONE);
      grade.setVisibility(fallback ? VISIBLE : GONE);
      setContentDescription(description(unit) + picture.statusDescription());
    };
    picture.updateAccessibility();
  }

  private static void badge(MainActivity h, FrameLayout photo, String text, int gravity) {
    TextView badge = h.text(text, 12);
    badge.setGravity(Gravity.CENTER);
    badge.setSingleLine(true);
    badge.setPadding(h.dp(5), h.dp(2), h.dp(5), h.dp(2));
    badge.setBackground(new GamePaperDrawable(h));
    FrameLayout.LayoutParams slot = new FrameLayout.LayoutParams(-2, -2, gravity);
    slot.setMargins(h.dp(4), h.dp(4), h.dp(4), h.dp(4));
    photo.addView(badge, slot);
  }

  /** 원본 등록증 위에 두 번째 종이 테두리를 덧씌우지 않는다. 선택 상태만 얇게 강조한다. */
  static android.graphics.drawable.Drawable selection(MainActivity h, boolean selected) {
    android.graphics.drawable.GradientDrawable outline = new android.graphics.drawable.GradientDrawable();
    outline.setColor(android.graphics.Color.TRANSPARENT);
    outline.setCornerRadius(h.dp(5));
    outline.setStroke(h.dp(selected ? 2 : 0), JournalTheme.BRASS);
    return outline;
  }

  static TextView stateLabel(MainActivity h, String text) {
    TextView label = h.text(text, 13);
    label.setTypeface(h.prose);
    label.setGravity(Gravity.CENTER);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setIncludeFontPadding(false);
    label.setPadding(h.dp(4), h.dp(3), h.dp(4), h.dp(3));
    label.setTextColor(JournalTheme.PAPER);
    label.setBackgroundColor(JournalTheme.PRIMARY);
    return label;
  }

  static View art(MainActivity h, JSONObject unit, int mode) {
    return new Artwork(h, unit, mode);
  }

  static String description(JSONObject unit) {
    String result = unit.optString("name") + " · " + unit.optString("tierName");
    if (unit.optInt("n") > 1) result += " · " + unit.optInt("n") + "마리";
    if (unit.optBoolean("first")) result += " · 처음 만난 대원";
    return result + TemporaryUnitPresentation.suffix(unit);
  }

  static boolean canShow(JSONObject unit) {
    if (unit.has("known") && !unit.optBoolean("known")) return false;
    String image = unit.optString("image"), img = unit.optString("img");
    // 로컬 145장을 갖고 있어도 서버가 가린 종을 먼저 노출하지 않는다.
    return !image.contains("/unknown.") && !image.contains("/q-")
        && !img.equals("unknown") && !img.startsWith("q-");
  }

  static void contain(RectF destination, int width, int height, int imageWidth, int imageHeight) {
    if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
      destination.setEmpty();
      return;
    }
    float scale = Math.min(width / (float) imageWidth, height / (float) imageHeight);
    // float 나눗셈/곱셈 왕복으로 제한 변이 한 ULP 커질 수 있다.
    // 예: 64×51을 9×7에 맞추면 높이 7.0000005가 되어 top이 음수가 된다.
    float w = Math.min(width, imageWidth * scale), h = Math.min(height, imageHeight * scale);
    destination.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
  }

  /** 승인된 전체 등록증 또는 명시적으로 요청한 기존 초상 창. */
  static final class Artwork extends View {
    final MainActivity host;
    final int mode;
    final String id, identity;
    final boolean visibleIdentity;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF target = new RectF();
    final JSONObject instance;
    Bitmap bitmap;
    boolean loadAttempted;
    String status = "이미지 준비 중";
    Runnable statusChanged;
    private int generation, requestedBucket;

    Artwork(MainActivity h, JSONObject unit, int mode) {
      super(h);
      instance = TemporaryUnitPresentation.artUnit(unit);
      host = h;
      this.mode = mode;
      id = unit.optString("id");
      identity = description(unit);
      visibleIdentity = canShow(unit);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      if (!visibleIdentity) status = "미발견";
      updateAccessibility();
    }

    private String statusDescription() {
      if (!visibleIdentity) return " · 미발견";
      if ("이미지 없음".equals(status)) return " · 이미지를 불러오지 못했어요";
      return status.isEmpty() ? "" : " · " + status;
    }

    private void updateAccessibility() {
      setContentDescription(identity + " · 등록증 그림" + statusDescription());
      if (statusChanged != null) statusChanged.run();
    }

    private void requestImage() {
      if (!isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0
          || !visibleIdentity || host.isDestroyed() || host.isFinishing()) return;
      int bucket = RegistrationArt.widthBucket(mode, getWidth(), getHeight());
      if (loadAttempted && bucket == requestedBucket) return;
      loadAttempted = true;
      requestedBucket = bucket;
      status = "이미지 준비 중";
      updateAccessibility();
      int serial = ++generation, epoch = host.epoch;
      String account = host.api.token();
      WeakReference<Artwork> weak = new WeakReference<>(this);
      host.registrationArt().request(id, mode, getWidth(), getHeight(), ready -> {
        Artwork view = weak.get();
        if (view == null || view.generation != serial || !view.isAttachedToWindow()
            || view.host.epoch != epoch || !java.util.Objects.equals(account, view.host.api.token())
            || view.host.isDestroyed() || view.host.isFinishing()) return;
        view.bitmap = ready;
        view.status = ready == null ? "이미지 없음" : "";
        view.updateAccessibility();
        view.updateTarget();
        view.invalidate();
      });
    }

    private void updateTarget() {
      if (bitmap == null) target.setEmpty();
      else contain(target, getWidth(), getHeight(), bitmap.getWidth(), bitmap.getHeight());
    }

    @Override protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      requestImage();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      updateTarget();
      requestImage();
    }

    @Override protected void onDetachedFromWindow() {
      ++generation;
      loadAttempted = false;
      bitmap = null;
      target.setEmpty();
      super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (bitmap != null && !bitmap.isRecycled()) {
        paint.setColorFilter(TemporaryUnitPresentation.filter(instance));
        canvas.drawBitmap(bitmap, null, target, paint);
        paint.setColorFilter(null);
        TemporaryUnitPresentation.badge(canvas, instance, new RectF(0, 0, getWidth(), getHeight()), 12 * getResources().getDisplayMetrics().scaledDensity, host.prose);
        return;
      }
      // 실패를 다른 인물 그림으로 감추지 않는다. 정체와 실패 이유는 접근성에도 남긴다.
      paint.setColor(JournalTheme.SELECTED);
      canvas.drawColor(JournalTheme.SELECTED);
      paint.setColor(JournalTheme.MUTED);
      paint.setTypeface(host.prose);
      paint.setTextSize(host.dp(12));
      paint.setTextAlign(Paint.Align.CENTER);
      if (getWidth() >= paint.measureText(status) + host.dp(8)
          && getHeight() >= host.dp(20))
        canvas.drawText(status, getWidth() / 2f,
            getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
      TemporaryUnitPresentation.badge(canvas, instance, new RectF(0, 0, getWidth(), getHeight()), 12 * getResources().getDisplayMetrics().scaledDensity, host.prose);
    }
  }
}
