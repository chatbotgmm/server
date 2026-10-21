package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LightingColorFilter;
import java.util.HashMap;
import java.util.Map;

/** 홈 전용: 원본 종이·남색 질감에 입체 황동 테두리와 재화 메달을 더한다. */
final class HomeMenuArt {
  private final Map<String, Bitmap> assets = new HashMap<>();
  private final Rect sliceSource = new Rect();
  private final RectF sliceTarget = new RectF();
  private final MainActivity host;
  private final HomeArt original;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Path clip = new Path();
  private final RectF inner = new RectF();
  private final RectF icon = new RectF();
  private final Bitmap paper, navy;
  private final Rect paperSource, navySource;

  HomeMenuArt(MainActivity host, HomeArt original) {
    this.host = host;
    this.original = original;
    for (String file : new String[]{"play", "menu", "tile", "panel", "currency-shrimp",
        "currency-peanut", "currency-application", "icon-draw", "icon-collection",
        "icon-combine", "icon-explore", "icon-shop", "icon-team", "icon-daily"}) {
      Bitmap bitmap = host.art.asset("home-menu-v1/" + file + ".png");
      if (bitmap != null) assets.put(file, bitmap);
    }
    paper = ThemeArt.of(host).image("ui/button-secondary.png");
    navy = ThemeArt.of(host).image("ui/button-primary.png");
    paperSource = texture(paper, "ui/button-secondary.png");
    navySource = texture(navy, "ui/button-primary.png");
  }

  private Rect texture(Bitmap image, String path) {
    if (image == null) return new Rect();
    Rect a = host.art.alpha(image, "release-v1/" + path);
    // 가운데 글자 없는 원본 질감만 반복 사용한다. 완성 시안 이미지는 사용하지 않는다.
    return new Rect(a.left + Math.round(a.width() * .20f),
        a.top + Math.round(a.height() * .30f),
        a.left + Math.round(a.width() * .80f),
        a.top + Math.round(a.height() * .70f));
  }

  void panel(Canvas canvas, RectF bounds) {
    if (bounds.isEmpty()) return;
    if (!assetSurface(canvas, "panel", bounds, false, true, false, 16))
      surface(canvas, bounds, false, false, true, false, true);
  }

  void tile(Canvas canvas, RectF bounds, boolean pressed, boolean enabled,
      boolean focus, boolean primary) {
    if (bounds.isEmpty()) return;
    String key = primary ? "play" : bounds.width() / bounds.height() < 1.45f ? "tile" : "menu";
    if (!assetSurface(canvas, key, bounds, pressed, enabled, focus,
        primary || bounds.height() >= host.dp(90) ? 16 : 12))
      surface(canvas, bounds, true, pressed, enabled, focus, primary);
  }

  private void surface(Canvas canvas, RectF bounds, boolean parchment, boolean pressed,
      boolean enabled, boolean focus, boolean primary) {
    int layer = canvas.saveLayerAlpha(bounds, enabled ? 255 : 140);
    float d = host.getResources().getDisplayMetrics().density;
    float radius = Math.min(8 * d, Math.min(bounds.width(), bounds.height()) / 5);
    inner.set(bounds); inner.inset(d, d);
    // 바닥의 짙은 접촉 그림자, 따뜻한 윗면, 안쪽 어두운 홈을 별도로 그린다.
    fill(0xff38291c);
    canvas.drawRoundRect(inner, radius, radius, paint);
    inner.bottom -= 2 * d;
    paint.setShader(new LinearGradient(0, inner.top, 0, inner.bottom,
        new int[] {0xffffe0a0, 0xffa96b2e, 0xffe4bd70, 0xff6b431e},
        new float[] {0, .35f, .67f, 1}, Shader.TileMode.CLAMP));
    canvas.drawRoundRect(inner, radius, radius, paint);
    paint.setShader(null);
    inner.inset((primary ? 3 : 2) * d, (primary ? 3 : 2) * d);
    fill(0xff473521);
    canvas.drawRoundRect(inner, Math.max(d, radius - 2 * d), Math.max(d, radius - 2 * d), paint);
    inner.inset(1.3f * d, 1.3f * d);
    clip.reset(); clip.addRoundRect(inner, Math.max(d, radius - 3 * d),
        Math.max(d, radius - 3 * d), Path.Direction.CW);
    int save = canvas.save(); canvas.clipPath(clip);
    fill(parchment ? 0xffefddb7 : 0xff123244);
    canvas.drawRect(inner, paint);
    Bitmap texture = parchment ? paper : navy;
    Rect source = parchment ? paperSource : navySource;
    if (texture != null && !source.isEmpty()) canvas.drawBitmap(texture, source, inner, paint);
    if (pressed) {
      fill(parchment ? 0x30774918 : 0x305c9cbd); canvas.drawRect(inner, paint);
    }
    canvas.restoreToCount(save);
    // 4개 작은 모서리 못: 버튼 전체를 금색으로 두르지 않으면서 입체감 유지.
    float inset = (primary ? 7 : 6) * d;
    float screw = Math.min(2.2f * d, bounds.height() * .035f);
    for (int side = 0; side < 4; side++) {
      float x = (side % 2 == 0) ? bounds.left + inset : bounds.right - inset;
      float y = (side < 2) ? bounds.top + inset : bounds.bottom - inset - 2 * d;
      fill(0xff483420); canvas.drawCircle(x, y, screw + d, paint);
      fill(0xffd9ab60); canvas.drawCircle(x, y, screw, paint);
      fill(0xffffe4a3); canvas.drawCircle(x - .5f * d, y - .6f * d, screw * .4f, paint);
    }
    canvas.restoreToCount(layer);
    if (focus) focus(canvas, bounds);
    fill(Color.WHITE);
  }

  /** 숫자는 호출자가 실제 잔액으로 그린다. 왼쪽 42dp는 세 종류 공통 메달 자리. */
  void currency(Canvas canvas, RectF bounds, int type, boolean pressed,
      boolean enabled, boolean focused) {
    if (bounds.isEmpty()) return;
    if (!assetSurface(canvas, "panel", bounds, pressed, enabled, focused, 8))
      surface(canvas, bounds, false, pressed, enabled, focused, false);
    float d = host.getResources().getDisplayMetrics().density;
    float diameter = Math.min(38 * d, bounds.height() - 8 * d);
    float cx = bounds.left + 22 * d, cy = bounds.centerY();
    float radius = Math.max(d, diameter / 2);
    String key = type == 0 ? "currency-shrimp" : type == 1 ? "currency-peanut" : "currency-application";
    Bitmap medallion = assets.get(key);
    if (medallion != null) {
      icon.set(cx-radius, cy-radius, cx+radius, cy+radius);
      drawIcon(canvas, medallion, icon, enabled ? 255 : 140);
      return;
    }
    int layer = canvas.saveLayerAlpha(bounds, enabled ? 255 : 140);
    fill(0xff322819); canvas.drawCircle(cx, cy + d, radius + d, paint);
    paint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
        new int[] {0xffffe7b0, 0xffbd7b31, 0xffffd88a, 0xff6e421a}, null, Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, radius, paint);
    paint.setShader(null);
    fill(0xff503c24); canvas.drawCircle(cx, cy, radius - 2 * d, paint);
    paint.setShader(new LinearGradient(0, cy - radius, 0, cy + radius,
        0xff19384a, 0xff081822, Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, Math.max(d, radius - 3 * d), paint);
    paint.setShader(null);
    float inset = radius * .73f;
    icon.set(cx - inset, cy - inset, cx + inset, cy + inset);
    // 세 재화 모두 기존 개별 게임 원화. 지원서는 모집에서 쓰는 실제 지원서와 동일.
    original.icon(canvas, type == 0 ? "icons/shrimp-snack.png"
        : type == 1 ? "icons/peanut.png" : "icons/draw.png", icon);
    canvas.restoreToCount(layer);
    fill(Color.WHITE);
  }

  boolean ready() { return assets.size() == 14; }

  void navigationIcon(Canvas canvas, int index, RectF bounds) {
    String key = index == 0 ? "explore" : index == 1 ? "draw" : index == 2 ? "collection"
        : index == 3 ? "combine" : index == 4 ? "shop" : index == 6 ? "daily" : "team";
    icon(canvas, key, bounds);
  }

  void icon(Canvas canvas, String key, RectF bounds) {
    Bitmap bitmap = assets.get("icon-" + key);
    if (bitmap != null) drawIcon(canvas, bitmap, bounds, 255);
    else original.icon(canvas, "icons/" + (key.equals("collection") ? "team" : key) + ".png", bounds);
  }

  private void drawIcon(Canvas canvas, Bitmap bitmap, RectF bounds, int alpha) {
    float scale = Math.min(bounds.width()/bitmap.getWidth(), bounds.height()/bitmap.getHeight());
    float width=bitmap.getWidth()*scale, height=bitmap.getHeight()*scale;
    sliceTarget.set(bounds.centerX()-width/2,bounds.centerY()-height/2,
        bounds.centerX()+width/2,bounds.centerY()+height/2);
    fill(Color.WHITE); paint.setColorFilter(null); paint.setAlpha(alpha);
    canvas.drawBitmap(bitmap, null, sliceTarget, paint);
    paint.setAlpha(255);
  }

  /** 원화 모서리는 고정 크기, 가장자리/중앙만 늘인다. source manifest의64/52px과 일치. */
  private boolean assetSurface(Canvas canvas, String key, RectF bounds,
      boolean pressed, boolean enabled, boolean focused, int cornerDp) {
    Bitmap bitmap = assets.get(key);
    if (bitmap == null) return false;
    int inset = key.equals("panel") ? 52 : 64;
    float corner = Math.min(host.dp(cornerDp), Math.min(bounds.width(),bounds.height())/3);
    int[] x = {0,inset,bitmap.getWidth()-inset,bitmap.getWidth()};
    int[] y = {0,inset,bitmap.getHeight()-inset,bitmap.getHeight()};
    float[] dx = {bounds.left,bounds.left+corner,bounds.right-corner,bounds.right};
    float[] dy = {bounds.top,bounds.top+corner,bounds.bottom-corner,bounds.bottom};
    fill(Color.WHITE); paint.setAlpha(enabled ? 255 : 140);
    paint.setColorFilter(pressed ? new LightingColorFilter(0xffc4ad87,0) : null);
    for (int row=0;row<3;row++) for(int col=0;col<3;col++) {
      sliceSource.set(x[col],y[row],x[col+1],y[row+1]);
      sliceTarget.set(dx[col],dy[row],dx[col+1],dy[row+1]);
      canvas.drawBitmap(bitmap,sliceSource,sliceTarget,paint);
    }
    paint.setColorFilter(null); paint.setAlpha(255);
    if (focused) focus(canvas,bounds);
    return true;
  }

  private void focus(Canvas canvas, RectF bounds) {
    float d = host.getResources().getDisplayMetrics().density;
    inner.set(bounds); inner.inset(2 * d, 2 * d);
    fill(0xfff5faff); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * d);
    canvas.drawRoundRect(inner, 5 * d, 5 * d, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  private void fill(int color) {
    paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
    paint.setAlpha(Color.alpha(color));
  }
}
