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
  private volatile JournalScenePack.Material journalMaterial;
  private volatile boolean closed;

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
    prepareJournalMaterial();
  }

  /** 작은 공통 종이도 기존 이미지 작업자에서 준비하고, 준비 후 현재 화면만 한 번 다시 그린다. */
  private void prepareJournalMaterial() {
    String pending = "journal-paper";
    loading.add(pending);
    images.execute(
        () -> {
          try {
            if (!closed) {
              JournalMaterial.prepare(context);
              JournalParchment.prepare(context);
            }
          } finally {
            api.ui.post(
                () -> {
                  loading.remove(pending);
                  if (!closed
                      && context instanceof android.app.Activity
                      && !((android.app.Activity) context).isDestroyed()
                      && !((android.app.Activity) context).isFinishing())
                    invalidateMaterial(((android.app.Activity) context).getWindow().getDecorView());
                });
          }
        });
  }

  private static void invalidateMaterial(android.view.View view) {
    view.invalidate();
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) invalidateMaterial(group.getChildAt(i));
    }
  }

  Bitmap asset(String path) {
    String themed = JournalAssets.sceneAsset(path);
    if (themed != null) path = themed;
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
    closed = true;
    scenes.shutdownNow();
    images.shutdownNow();
    synchronized (this) {
      journalMaterial = null;
    }
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

  /** 첫 화면 원본 캐릭터도 그리기 밖에서 디코딩·알파 범위를 준비한다. */
  void localAsset(String path, java.util.function.Consumer<Bitmap> ready) {
    String pending = "local:" + java.util.UUID.randomUUID();
    loading.add(pending);
    images.execute(
        () -> {
          Bitmap bitmap = asset(path);
          if (bitmap != null) alpha(bitmap, "native/" + path);
          api.ui.post(
              () -> {
                loading.remove(pending);
                ready.accept(bitmap);
              });
        });
  }

  /** 시간대 배경은 전역 LRU에 넣지 않는다. 표시 화면이 현재/전환 대상 두 장만 소유한다. */
  void journalScene(
      LocalTimeTheme.Phase phase,
      int width,
      int height,
      java.util.function.BooleanSupplier wanted,
      java.util.function.Consumer<Bitmap> ready) {
    journalScenePack(
        phase, width, height, wanted, scene -> ready.accept(scene == null ? null : scene.backdrop));
  }

  /** 한 시간대의 원본·고정물·공유 수면을 함께 준비한 뒤 원자적으로 UI에 전달한다. */
  void journalScenePack(
      LocalTimeTheme.Phase phase,
      int width,
      int height,
      java.util.function.BooleanSupplier wanted,
      java.util.function.Consumer<JournalScenePack> ready) {
    if (closed) return;
    String pending = "journal:" + java.util.UUID.randomUUID();
    loading.add(pending);
    scenes.execute(
        () -> {
          JournalScenePack result = null;
          try {
            if (wanted.getAsBoolean() && !closed) {
              Bitmap backdrop = decodeJournalScene(phase, width, height);
              if (backdrop != null && wanted.getAsBoolean() && !closed) {
                JournalScenePack.Material material = journalMaterial();
                if (wanted.getAsBoolean() && !closed)
                  result = JournalScenePack.create(backdrop, material, phase);
              }
            }
          } finally {
            JournalScenePack completed = result;
            api.ui.post(
                () -> {
                  loading.remove(pending);
                  if (wanted.getAsBoolean() && !closed) ready.accept(completed);
                });
          }
        });
  }

  private synchronized JournalScenePack.Material journalMaterial() {
    if (journalMaterial != null) return journalMaterial;
    if (closed) return null;
    String path = "native/journal/ocean-surface.webp";
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (InputStream in = context.getAssets().open(path)) {
      BitmapFactory.decodeStream(in, null, options);
    } catch (IOException ignored) {
      return null;
    }
    int originalWidth = options.outWidth, originalHeight = options.outHeight;
    if (originalWidth <= 0 || originalHeight <= 0) return null;
    options.inJustDecodeBounds = false;
    options.inSampleSize = 1;
    while (originalWidth / options.inSampleSize > 1152
        || originalHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream in = context.getAssets().open(path)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
      if (bitmap == null || closed) return null;
      JournalScenePack.Material material = JournalScenePack.Material.create(bitmap);
      if (closed) return null;
      journalMaterial = material;
      return journalMaterial;
    } catch (IOException ignored) {
      return null;
    }
  }

  private Bitmap decodeJournalScene(LocalTimeTheme.Phase phase, int width, int height) {
    String path = "native/journal/c-home-" + phase.name().toLowerCase(Locale.ROOT) + ".webp";
    BitmapFactory.Options dimensions = new BitmapFactory.Options();
    dimensions.inJustDecodeBounds = true;
    try (InputStream in = context.getAssets().open(path)) {
      BitmapFactory.decodeStream(in, null, dimensions);
    } catch (IOException ignored) {
      return null;
    }
    if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null;
    // cover와 작은 패럴랙스 여백에 필요한 픽셀만 유지한다. 큰 화면에서도 원본 이상 확대 저장하지 않는다.
    float scale =
        Math.min(
            1,
            Math.max(width * 1.035f / dimensions.outWidth, height * 1.035f / dimensions.outHeight));
    int targetWidth = Math.max(1, Math.round(dimensions.outWidth * scale));
    int targetHeight = Math.max(1, Math.round(dimensions.outHeight * scale));
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 1;
    while (dimensions.outWidth / (options.inSampleSize * 2) >= targetWidth
        && dimensions.outHeight / (options.inSampleSize * 2) >= targetHeight)
      options.inSampleSize *= 2;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream in = context.getAssets().open(path)) {
      Bitmap decoded = BitmapFactory.decodeStream(in, null, options);
      if (decoded == null) return null;
      Bitmap fitted = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true);
      if (fitted != decoded) decoded.recycle(); // 아직 UI에 전달하지 않은 작업 스레드 소유 원본이다.
      fitted.setDensity(Bitmap.DENSITY_NONE);
      return fitted;
    } catch (IOException ignored) {
      return null;
    }
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
                  if (path.startsWith("/duel/sprites/")
                      || (path.startsWith("/duel/bg/") && path.endsWith("char.webp")))
                    alpha(decoded, path);
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
    if (JournalAssets.oldPlatform(path)) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    String themed = JournalAssets.sceneAsset(path);
    if (themed != null) return asset(themed);
    if (path.startsWith("/duel/icons/")) {
      Bitmap icon = JournalIcons.create(context, path);
      if (icon != null) return icon;
    }
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
    // 화면의 의미 태그와 비동기 이미지 요청 식별자는 서로 덮어쓰지 않는다.
    view.setTag(R.id.native_art_request, path);
    Bitmap b =
        get(
            path,
            () -> {
              if (path.equals(view.getTag(R.id.native_art_request))) {
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
