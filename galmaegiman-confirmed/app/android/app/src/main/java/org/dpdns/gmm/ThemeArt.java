package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** 공통 황동·남색 표면. 홈 원본과 분리해 기존 홈/전투 배치와 동작을 바꾸지 않는다. */
final class ThemeArt {
  enum Surface { NAVY, PAPER, HEADER, TAB, PANEL, NAV }
  final MainActivity host;
  private final Map<String, Bitmap> images = new HashMap<>();
  private final Map<String, Rect> sources = new HashMap<>();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Rect source = new Rect();
  private final RectF target = new RectF();
  private final int[] sx = new int[6], sy = new int[4];
  private final float[] dx = new float[6], dy = new float[4];

  private ThemeArt(MainActivity host) { this.host = host; }
  static ThemeArt of(MainActivity host) {
    if (host.themeArt == null) host.themeArt = new ThemeArt(host);
    return host.themeArt;
  }

  Bitmap image(String file) {
    // Activity 생성 전 프레임·위젯도 그릴 수 있다. 아직 없는 저장소를 실패로 캐시하지 않는다.
    if (host.art == null) return null;
    // 배경은 NativeArt의 제한된 LRU만 소유한다. 두 번째 무제한 보관소를 만들지 않는다.
    if (file.startsWith("backgrounds/")) return host.art.asset("release-v1/" + file);
    if (!images.containsKey(file)) {
      Bitmap image = host.art.asset("release-v1/" + file);
      images.put(file, image);
      if (image != null) sources.put(file, host.art.alpha(image, "release-v1/" + file));
    }
    return images.get(file);
  }

  static String file(Surface type) {
    switch (type) {
      case NAVY: return "ui/button-primary.png";
      case HEADER: return "ui/title-strip.png";
      case TAB: return "ui/tab.png";
      case PANEL: return "shared/panel-hp.png";
      case NAV: return "shared/panel-command.png";
      default: return "ui/button-secondary.png";
    }
  }

  /** 5×3 분할: 모서리와 가운데 문장은 늘이지 않고, 조용한 면만 늘인다. */
  void surface(Canvas canvas, Surface type, RectF bounds) {
    String file = file(type);
    Bitmap bitmap = image(file);
    Rect area = sources.get(file);
    if (bounds.isEmpty()) return;
    if (bitmap == null || area == null) {
      JournalTheme.drawPanel(canvas, bounds, density(),
          type == Surface.NAV || type == Surface.NAVY || type == Surface.HEADER
              ? JournalTheme.PRIMARY : JournalTheme.PAPER, true);
      return;
    }
    int edgeX = Math.min(80, area.width() / 4);
    int edgeY = Math.min(type == Surface.NAV ? 80 : 72, area.height() / 3);
    int crest = Math.min(28, area.width() / 12), center = area.centerX();
    sx[0] = area.left; sx[1] = area.left + edgeX; sx[2] = center - crest;
    sx[3] = center + crest; sx[4] = area.right - edgeX; sx[5] = area.right;
    sy[0] = area.top; sy[1] = area.top + edgeY;
    sy[2] = area.bottom - edgeY; sy[3] = area.bottom;
    // 큰 패널도 테두리를 키우지 않는다. 48dp 조작부에서는 전체 문장 높이에 맞춘다.
    float scale = Math.min(density() / 3f,
        Math.min(bounds.height() / (type == Surface.PANEL || type == Surface.NAV ? 240f : area.height()),
            bounds.width() / 200f));
    float ex = Math.min(edgeX * scale, bounds.width() / 5f);
    float ey = Math.min(edgeY * scale, bounds.height() / 3f);
    float middle = Math.min(crest * scale, bounds.width() / 10f);
    dx[0] = bounds.left; dx[1] = bounds.left + ex; dx[2] = bounds.centerX() - middle;
    dx[3] = bounds.centerX() + middle; dx[4] = bounds.right - ex; dx[5] = bounds.right;
    dy[0] = bounds.top; dy[1] = bounds.top + ey;
    dy[2] = bounds.bottom - ey; dy[3] = bounds.bottom;
    paint.setAlpha(255); paint.setColorFilter(null);
    for (int y = 0; y < 3; y++) for (int x = 0; x < 5; x++) {
      source.set(sx[x], sy[y], sx[x + 1], sy[y + 1]);
      target.set(dx[x], dy[y], dx[x + 1], dy[y + 1]);
      canvas.drawBitmap(bitmap, source, target, paint);
    }
  }

  float density() { return host.getResources().getDisplayMetrics().density; }
  Drawable drawable(Surface type) { return new SurfaceDrawable(this, type); }

  Drawable background(String file) {
    return new Drawable() {
      final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      final RectF destination = new RectF();
      int alpha = 255;
      Bitmap bitmap;
      boolean requested;
      void request() {
        if (requested || host.art == null) return;
        requested = true;
        host.art.localOpaqueAsset("release-v1/" + file, ready -> { bitmap = ready; invalidateSelf(); });
      }
      { request(); }
      @Override public void draw(Canvas canvas) {
        request();
        Rect b = getBounds();
        if (bitmap == null) { brush.setColor(JournalTheme.NAVY); brush.setAlpha(alpha); canvas.drawRect(b, brush); return; }
        float scale = Math.max(b.width() / (float) bitmap.getWidth(), b.height() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
        destination.set(b.exactCenterX() - width / 2, b.exactCenterY() - height / 2,
            b.exactCenterX() + width / 2, b.exactCenterY() + height / 2);
        int save = canvas.save(); canvas.clipRect(b);
        brush.setAlpha(alpha); canvas.drawBitmap(bitmap, null, destination, brush);
        canvas.restoreToCount(save);
      }
      @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
      @Override public void setColorFilter(ColorFilter value) { brush.setColorFilter(value); invalidateSelf(); }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
  }

  /** 독립 paint/layer로 alpha·filter가 다른 버튼이나 원본 비트맵에 새지 않는다. */
  static final class SurfaceDrawable extends Drawable {
    final ThemeArt art; final Surface type;
    final GamePressState press = new GamePressState();
    final Paint layer = new Paint(Paint.ANTI_ALIAS_FLAG), detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    boolean selected, focused;
    ColorFilter filter;
    SurfaceDrawable(ThemeArt art, Surface type) {
      this.art = art; this.type = type;
      art.image(file(type));
      if (type == Surface.TAB) {
        art.image(file(Surface.PAPER)); art.image(file(Surface.NAVY));
      }
    }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
      boolean select = false, focus = false;
      for (int state : states) {
        if (state == android.R.attr.state_selected || state == android.R.attr.state_checked
            || state == android.R.attr.state_activated) select = true;
        if (state == android.R.attr.state_focused) focus = true;
      }
      boolean changed = press.update(states, type == Surface.TAB) | (select != selected) | (focus != focused);
      selected = select; focused = focus;
      if (changed) invalidateSelf();
      return changed;
    }
    @Override public void draw(Canvas canvas) {
      rect.set(getBounds()); float d = art.density();
      int outer = canvas.save();
      layer.setAlpha(255); layer.setColorFilter(filter);
      if (filter != null) canvas.saveLayer(rect, layer);
      int saved = press.begin(canvas, getBounds(), d);
      Surface visible = type == Surface.TAB
          ? (press.enabled && press.pressed ? Surface.NAVY : Surface.PAPER) : type;
      // 비활성 상태에서도 원본 표면을 유지하고 공통 상태 필터만 적용한다.
      art.surface(canvas, visible, rect);
      if (press.enabled && (selected || focused)) {
        rect.inset(3 * d, 3 * d);
        detail.setStyle(Paint.Style.STROKE); detail.setStrokeWidth(2 * d);
        detail.setColor(focused ? JournalTheme.FOCUS : JournalTheme.BRASS);
        canvas.drawRoundRect(rect, 3 * d, 3 * d, detail);
        detail.setStyle(Paint.Style.FILL);
        if (type != Surface.NAVY && type != Surface.HEADER && type != Surface.NAV && !press.pressed) {
          detail.setColor(0x196dc9ff); canvas.drawRoundRect(rect, 3 * d, 3 * d, detail);
        }
      }
      press.finish(canvas, saved); canvas.restoreToCount(outer);
    }
    @Override public void setAlpha(int value) { press.alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
}
