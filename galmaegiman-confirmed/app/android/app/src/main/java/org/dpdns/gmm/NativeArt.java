package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.*;

/** 원본 캐릭터 그림은 서버에서 그대로 읽고, UI 재질은 앱에 함께 넣는다. */
final class NativeArt {
  private final Context context;
  private final NativeApi api;
  String key = "";
  private org.json.JSONObject knownBounds = new org.json.JSONObject();
  private final LruCache<String, Bitmap> bitmaps =
      new LruCache<String, Bitmap>(64 * 1024 * 1024) {
        protected int sizeOf(String k, Bitmap b) {
          return b.getAllocationByteCount();
        }
      };
  private final Set<String> loading = Collections.synchronizedSet(new HashSet<>());

  NativeArt(Context c, NativeApi a) {
    context = c;
    api = a;
    try (InputStream in = c.getAssets().open("native/sprite-bounds.json")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
      knownBounds = new org.json.JSONObject(bytes.toString("UTF-8"));
    } catch (Exception ignored) {
    }
  }

  Bitmap asset(String path) {
    String k = "asset:" + path;
    Bitmap b = bitmaps.get(k);
    if (b != null) return b;
    try (InputStream in = context.getAssets().open("native/" + path)) {
      b = BitmapFactory.decodeStream(in);
      if (b != null) bitmaps.put(k, b);
    } catch (IOException ignored) {
    }
    return b;
  }

  private final java.util.concurrent.ExecutorService images =
      java.util.concurrent.Executors.newFixedThreadPool(2);

  void close() {
    images.shutdownNow();
    clear();
  }

  private final Map<String, java.util.List<Runnable>> waiters = new HashMap<>();

  synchronized Bitmap get(String path, Runnable ready) {
    Bitmap b = bitmaps.get(path);
    if (b != null) return b;
    waiters.computeIfAbsent(path, k -> new ArrayList<>()).add(ready);
    if (!loading.add(path)) return null;
    images.execute(
        () -> {
          Bitmap decoded = null;
          HttpURLConnection c = null;
          try {
            String url =
                BuildConfig.SERVER_URL
                    + path
                    + (path.startsWith("/duel/") ? "?k=" + URLEncoder.encode(key, "UTF-8") : "");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            if (!api.token().isEmpty()) c.setRequestProperty("x-gmm-s", api.token());
            try (InputStream in = c.getInputStream()) {
              decoded = BitmapFactory.decodeStream(in);
            }
            if (decoded != null) {
              alpha(decoded, path);
              bitmaps.put(path, decoded);
            }
          } catch (Exception ignored) {
          } finally {
            if (c != null) c.disconnect();
            loading.remove(path);
          }
          java.util.List<Runnable> listeners;
          synchronized (this) {
            listeners = waiters.remove(path);
          }
          if (listeners != null) for (Runnable callback : listeners) api.ui.post(callback);
        });
    return null;
  }

  void into(ImageView view, String path) {
    view.setTag(path);
    Bitmap b =
        get(
            path,
            () -> {
              if (path.equals(view.getTag())) {
                Bitmap loaded = bitmaps.get(path);
                if (loaded != null) view.setImageBitmap(loaded);
              }
            });
    if (b != null) view.setImageBitmap(b);
  }

  void clear() {
    key = "";
    bitmaps.evictAll();
  }

  private final Map<String, Rect> bounds = new java.util.concurrent.ConcurrentHashMap<>();

  Rect alpha(Bitmap b, String path) {
    if (path.equals("log.webp")) return new Rect(54, 110, 2119, 584);
    if (path.equals("status.webp")) return new Rect(14, 72, 2157, 645);
    org.json.JSONArray known = knownBounds.optJSONArray(path);
    if (known != null && known.optInt(0) == b.getWidth() && known.optInt(1) == b.getHeight())
      return new Rect(known.optInt(2), known.optInt(3), known.optInt(4), known.optInt(5));
    Rect r = bounds.get(path);
    if (r != null) return r;
    int w = b.getWidth(), h = b.getHeight();
    int[] pixels = new int[w * h];
    b.getPixels(pixels, 0, w, 0, 0, w, h);
    int top = 0, bottom = h - 1, left = 0, right = w - 1;
    outer:
    for (; top < h; top++)
      for (int x = 0; x < w; x++) if ((pixels[top * w + x] >>> 24) > 12) break outer;
    outer:
    for (; bottom >= top; bottom--)
      for (int x = 0; x < w; x++) if ((pixels[bottom * w + x] >>> 24) > 12) break outer;
    outer:
    for (; left < w; left++)
      for (int y = top; y <= bottom; y++) if ((pixels[y * w + left] >>> 24) > 12) break outer;
    outer:
    for (; right >= left; right--)
      for (int y = top; y <= bottom; y++) if ((pixels[y * w + right] >>> 24) > 12) break outer;
    r =
        right >= left && bottom >= top
            ? new Rect(left, top, right + 1, bottom + 1)
            : new Rect(0, 0, w, h);
    bounds.put(path, r);
    return r;
  }
}
