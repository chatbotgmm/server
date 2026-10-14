package org.dpdns.gmm;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.*;

/** 원본 그림은 APK에서 읽고, 새 서버 그림은 기기에 저장해 다음 실행에도 재사용한다. */
final class NativeArt {
  private final Context context;
  private final NativeApi api;
  private final File disk;
  private static final long MAX_DISK = 40L * 1024 * 1024;
  private static final long MAX_DOWNLOAD = 12L * 1024 * 1024;
  private static final long MAX_AGE = 7L * 24 * 60 * 60 * 1000;
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
    disk = new File(c.getCacheDir(), "game-art-" + BuildConfig.VERSION_CODE);
    disk.mkdirs();
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

  private final java.util.concurrent.ExecutorService scenes =
      java.util.concurrent.Executors.newFixedThreadPool(2);

  void close() {
    scenes.shutdownNow();
    images.shutdownNow();
    clear();
  }

  private final Map<String, java.util.List<Runnable>> waiters = new HashMap<>();

  boolean idle() {
    return loading.isEmpty();
  }

  Bitmap peek(String path) {
    return bitmaps.get(path);
  }

  Bitmap scene(String path, Runnable ready) {
    return get(path, ready, true);
  }

  Bitmap get(String path, Runnable ready) {
    return get(path, ready, false);
  }

  private synchronized Bitmap get(String path, Runnable ready, boolean priority) {
    if (path == null
        || !path.matches(
            "/(duel/(sprites|bg|icons)|images|thumbs)/[A-Za-z0-9_-]+\\.(webp|png|jpg)"))
      return null;
    Bitmap b = bitmaps.get(path);
    if (b != null) return b;
    waiters.computeIfAbsent(path, k -> new ArrayList<>()).add(ready);
    Set<String> pending = loading;
    if (!pending.add(path)) return null;
    (priority ? scenes : images)
        .execute(
            () -> {
              Bitmap decoded = null;
              HttpURLConnection c = null;
              File cached = cachedFile(path), temp = null;
              try {
                decoded = bundled(path);
                if (decoded == null
                    && cached.isFile()
                    && System.currentTimeMillis() - cached.lastModified() < MAX_AGE)
                  decoded = BitmapFactory.decodeFile(cached.getPath());
                if (decoded == null) {
                  String url =
                      BuildConfig.SERVER_URL
                          + path
                          + (path.startsWith("/duel/")
                              ? "?k=" + URLEncoder.encode(key, "UTF-8")
                              : "");
                  c = (HttpURLConnection) new URL(url).openConnection();
                  c.setConnectTimeout(15000);
                  c.setReadTimeout(15000);
                  if (!api.token().isEmpty()) c.setRequestProperty("x-gmm-s", api.token());
                  temp = File.createTempFile("image-", ".tmp", disk);
                  try (InputStream in = c.getInputStream();
                      OutputStream out = new FileOutputStream(temp)) {
                    byte[] bytes = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(bytes)) != -1) {
                      total += n;
                      if (total > MAX_DOWNLOAD) throw new IOException("image too large");
                      out.write(bytes, 0, n);
                    }
                  }
                  decoded = BitmapFactory.decodeFile(temp.getPath());
                  if (decoded != null && temp.renameTo(cached)) trimDisk();
                }
                if (decoded != null) {
                  if (path.startsWith("/duel/sprites/")) alpha(decoded, path);
                  bitmaps.put(path, decoded);
                }
              } catch (Exception ignored) {
                // 인터넷이 없어도 이미 받아 둔 원본은 표시한다.
                if (decoded == null && cached.isFile()) {
                  decoded = BitmapFactory.decodeFile(cached.getPath());
                  if (decoded != null) bitmaps.put(path, decoded);
                }
              } finally {
                if (temp != null) temp.delete();
                if (c != null) c.disconnect();
                pending.remove(path);
              }
              java.util.List<Runnable> listeners;
              synchronized (this) {
                listeners = waiters.remove(path);
              }
              // UI 처리 전 LRU에서 빠져도 이번 요청의 그림을 잃지 않는다.
              Bitmap completed = decoded;
              if (listeners != null)
                for (Runnable callback : listeners)
                  api.ui.post(
                      () -> {
                        if (completed != null) bitmaps.put(path, completed);
                        callback.run();
                      });
            });
    return null;
  }

  private Bitmap bundled(String path) {
    String local = path.substring(1);
    // 목록 카드에는 같은 원본으로 만든 기존 480px 미리보기를 사용한다.
    if (path.startsWith("/images/"))
      local = "thumbs/" + path.substring(8).replaceFirst("\\.png$", ".jpg");
    try (InputStream in = context.getAssets().open("game-art/" + local)) {
      return BitmapFactory.decodeStream(in);
    } catch (IOException ignored) {
      return null;
    }
  }

  private File cachedFile(String path) {
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(
                  (BuildConfig.SERVER_URL + path)
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder name = new StringBuilder();
      for (byte b : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return new File(disk, name + ".img");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private synchronized void trimDisk() {
    File[] files = disk.listFiles((dir, name) -> name.endsWith(".img"));
    if (files == null) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    long total = 0;
    for (File f : files) total += f.length();
    for (File f : files) {
      if (total <= MAX_DISK) break;
      long size = f.length();
      if (f.delete()) total -= size;
    }
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
