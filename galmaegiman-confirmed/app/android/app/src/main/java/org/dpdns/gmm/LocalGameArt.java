package org.dpdns.gmm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Public APK art only: byte-bounded shared decoding, never account state or HTTP responses. */
final class LocalGameArt {
  private static final int BUDGET = (int) Math.max(1, Math.min(32L * 1024 * 1024,
      Runtime.getRuntime().maxMemory() / 8));
  private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(BUDGET) {
    @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
  };
  private static final Map<String, List<Consumer<Bitmap>>> WAITING = new HashMap<>();
  private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
  private static final Handler UI = new Handler(Looper.getMainLooper());
  private static int deliveries;

  private LocalGameArt() {}
  static String key(String path, int sample) { return BuildConfig.VERSION_CODE + ":" + path + "@" + sample; }
  static Bitmap peek(String path, int sample) { return CACHE.get(key(path, sample)); }
  static int budgetBytes() { return BUDGET; }
  static int cachedBytes() { return CACHE.size(); }
  static boolean idle() { synchronized (WAITING) { return WAITING.isEmpty() && deliveries == 0; } }
  static void background(Runnable task) { WORKERS.execute(task); }
  private static void deliver(Runnable callback) {
    synchronized (WAITING) { deliveries++; }
    UI.post(() -> { try { callback.run(); } finally { synchronized (WAITING) { deliveries--; } } });
  }

  /** A cache hit and all waiters are delivered on the main thread. A failed decode is retryable. */
  static void load(Context context, String path, int sample, Consumer<Bitmap> ready) {
    Context application = context.getApplicationContext();
    final Context assets = application == null ? context : application;
    final int safeSample = Math.max(1, sample);
    work(key(path, safeSample), () -> decode(assets, path, safeSample), ready);
  }

  static void work(String key, Callable<Bitmap> decode, Consumer<Bitmap> ready) {
    synchronized (WAITING) {
      Bitmap cached = CACHE.get(key);
      if (cached != null) { deliver(() -> ready.accept(cached)); return; }
      List<Consumer<Bitmap>> listeners = WAITING.get(key);
      if (listeners != null) { listeners.add(ready); return; }
      listeners = new ArrayList<>(); listeners.add(ready); WAITING.put(key, listeners);
    }
    WORKERS.execute(() -> {
      Bitmap decoded = null;
      try { decoded = decode.call(); } catch (Exception ignored) { }
      catch (OutOfMemoryError insufficientMemory) { CACHE.evictAll(); }
      final Bitmap result = decoded;
      final List<Consumer<Bitmap>> listeners;
      synchronized (WAITING) {
        if (result != null) CACHE.put(key, result);
        listeners = WAITING.remove(key);
        deliver(() -> { if (listeners != null) for (Consumer<Bitmap> listener : listeners) listener.accept(result); });
      }
    });
  }

  static Bitmap decode(Context context, String path, int sample) throws java.io.IOException {
    if (path.startsWith("/") || path.contains("..") || !path.matches("[A-Za-z0-9_./-]+")) return null;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = Math.max(1, sample);
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream in = context.getAssets().open(path)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
      if (bitmap != null) bitmap.setDensity(Bitmap.DENSITY_NONE);
      return bitmap;
    }
  }

  static boolean inspect(Context context, String path) {
    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
    try (InputStream in = context.getAssets().open(path)) {
      BitmapFactory.decodeStream(in, null, bounds);
      return bounds.outWidth > 0 && bounds.outHeight > 0;
    } catch (Exception missing) { return false; }
    catch (OutOfMemoryError insufficientMemory) { return false; }
  }

  /** Keep enough pixels for FIT_CENTER, with power-of-two keys shared by preload and bind. */
  static int sampleForFit(int sourceWidth, int sourceHeight, int width, int height) {
    if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) return 4;
    float scale = Math.min(1f, Math.min(width / (float) sourceWidth, height / (float) sourceHeight));
    int sample = 1;
    while (sample < 8 && 1f / (sample * 2) >= scale) sample *= 2;
    return sample;
  }

  static void trim() { CACHE.evictAll(); }
}
