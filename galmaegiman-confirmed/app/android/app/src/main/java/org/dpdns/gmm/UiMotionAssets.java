package org.dpdns.gmm;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 명시적으로 요청한 화면에서만 사용하는 UI 리소스 로더. 버튼 이벤트·텍스트는 소유하지 않는다. */
final class UiMotionAssets {
  static final String ROOT = "native/ui-motion-v1/";
  private final AssetManager assets;
  private final JSONObject manifest;
  private final float density;
  // 비트맵만 공유한다. 상태를 가진 Drawable은 호출할 때마다 새로 만든다.
  private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
    @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
  };

  UiMotionAssets(Context context) throws IOException {
    assets = context.getApplicationContext().getAssets();
    density = context.getResources().getDisplayMetrics().density;
    try (InputStream in = assets.open(ROOT + "runtime.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      manifest = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
      if (manifest.getInt("schemaVersion") != 1) throw new IOException("지원하지 않는 UI 리소스 버전");
    } catch (JSONException e) { throw new IOException("UI 리소스 매핑 오류", e); }
  }

  Background button(String family) throws IOException {
    JSONObject entry = find("buttons", "family", family, "runtime_base");
    if (!"normal".equals(entry.optString("state"))) throw new IOException("버튼 기본 상태가 normal이 아니에요");
    try {
      JSONObject inset = entry.getJSONObject("contentInsets");
      float[] edges = {(float) inset.getDouble("left"), (float) inset.getDouble("top"),
          (float) inset.getDouble("right"), (float) inset.getDouble("bottom")};
      for (float edge : edges) if (!Float.isFinite(edge) || edge < 0 || edge >= 1) throw new IOException("버튼 안쪽 여백 오류");
      if (edges[0] + edges[2] >= 1 || edges[1] + edges[3] >= 1) throw new IOException("버튼 내용 영역이 비어 있어요");
      return new Background(bitmap(entry, true), edges, density, true);
    } catch (JSONException e) { throw new IOException("버튼 안쪽 여백 누락", e); }
  }

  Drawable icon(String id) throws IOException {
    return new Background(bitmap(find("icons", "id", id, "runtime_icon"), false),
        new float[] {0, 0, 0, 0}, density, false);
  }

  void clearCache() { cache.evictAll(); } // 이미 화면에 연결된 비트맵은 recycle하지 않는다.

  private JSONObject find(String list, String key, String value, String usage) throws IOException {
    JSONArray rows = manifest.optJSONArray(list);
    if (rows != null) for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row != null && value.equals(row.optString(key)) && usage.equals(row.optString("usage"))) return row;
    }
    throw new IOException("UI 리소스를 찾을 수 없어요: " + list + "/" + value);
  }

  private synchronized Bitmap bitmap(JSONObject entry, boolean button) throws IOException {
    String file = entry.optString("file");
    String pattern = button ? "buttons/[a-z_]+_normal\\.png" : "icons/[a-z_]+\\.png";
    if (!file.matches(pattern)) throw new IOException("허용하지 않는 UI 리소스 경로");
    Bitmap image = cache.get(file);
    if (image != null) return image;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream in = assets.open(ROOT + file)) { image = BitmapFactory.decodeStream(in, null, options); }
    JSONArray size = entry.optJSONArray("size");
    if (image == null || size == null || size.length() != 2
        || image.getWidth() != size.optInt(0) || image.getHeight() != size.optInt(1) || !image.hasAlpha()) {
      throw new IOException("UI 리소스 PNG 크기·알파 확인 실패: " + file);
    }
    image.setDensity(Bitmap.DENSITY_NONE);
    cache.put(file, image);
    return image;
  }

  /** 단일 비트맵을 contain으로 그린다. 상태 변경 시 경계·피벗·내용 좌표는 고정이다. */
  static final class Background extends Drawable {
    private final Bitmap image;
    private final float[] insets;
    private final float density;
    private final boolean interactiveArt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int alpha = 255;
    private boolean enabled = true, pressed;
    private ColorFilter externalFilter;
    private final ColorFilter downFilter = brightness(.86f, 1f);
    private final ColorFilter disabledFilter = brightness(.85f, .15f);

    Background(Bitmap image, float[] insets, float density, boolean interactiveArt) {
      this.image = image;
      this.insets = insets;
      this.density = density;
      this.interactiveArt = interactiveArt;
    }

    private static ColorFilter brightness(float value, float saturation) {
      ColorMatrix color = new ColorMatrix();
      color.setSaturation(saturation);
      color.postConcat(new ColorMatrix(new float[] {
          value,0,0,0,0, 0,value,0,0,0, 0,0,value,0,0, 0,0,0,1,0}));
      return new ColorMatrixColorFilter(color);
    }

    private RectF artBounds(Rect bounds) {
      float scale = Math.min(bounds.width() / (float) image.getWidth(), bounds.height() / (float) image.getHeight());
      float w = image.getWidth() * scale, h = image.getHeight() * scale;
      return new RectF(bounds.exactCenterX() - w / 2, bounds.exactCenterY() - h / 2,
          bounds.exactCenterX() + w / 2, bounds.exactCenterY() + h / 2);
    }

    // 레이아웃이 정해진 뒤 호출한다. 텍스트 크기·최소 터치 영역은 호출자가 따로 보장한다.
    RectF contentBounds(Rect bounds) {
      RectF art = artBounds(bounds);
      float w = art.width(), h = art.height();
      return new RectF(art.left + w * insets[0], art.top + h * insets[1],
          art.right - w * insets[2], art.bottom - h * insets[3]);
    }

    @Override public void draw(Canvas canvas) {
      if (getBounds().isEmpty()) return;
      paint.setAlpha(!enabled && interactiveArt ? Math.round(alpha * .55f) : alpha);
      paint.setColorFilter(externalFilter != null ? externalFilter
          : !interactiveArt ? null : !enabled ? disabledFilter : pressed ? downFilter : null);
      canvas.drawBitmap(image, null, artBounds(getBounds()), paint);
    }

    @Override public boolean isStateful() { return interactiveArt; }
    @Override protected boolean onStateChange(int[] states) {
      boolean nextEnabled = false, nextPressed = false;
      for (int state : states) {
        if (state == android.R.attr.state_enabled) nextEnabled = true;
        if (state == android.R.attr.state_pressed) nextPressed = true;
      }
      boolean changed = enabled != nextEnabled || pressed != nextPressed;
      enabled = nextEnabled;
      pressed = nextPressed;
      if (changed) invalidateSelf();
      return changed;
    }
    @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { externalFilter = filter; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return Math.round(image.getWidth() * density); }
    @Override public int getIntrinsicHeight() { return Math.round(image.getHeight() * density); }
  }
}
