package org.dpdns.gmm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** 등록증만 읽는 APK 전용 저장소. 네트워크 요청·게임 상태 변경은 하지 않는다. */
final class RegistrationArt {
  static final String ROOT = "native/registration-v1/";
  static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;
  static final int MAX_QUEUED = 96;
  static final int FULL_MAX_WIDTH = 768, COMPACT_MAX_WIDTH = 256;
  private final Context context;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ThreadPoolExecutor worker =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(MAX_QUEUED),
          task -> { Thread t = new Thread(task, "registration-art"); t.setDaemon(true); return t; },
          new ThreadPoolExecutor.AbortPolicy());
  private final LruCache<String, Bitmap> cache =
      new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getAllocationByteCount();
        }
      };
  private final Map<String, List<Consumer<Bitmap>>> pending = new HashMap<>();
  private Set<String> ids;
  private volatile boolean closed;

  RegistrationArt(Context context) { this.context = context.getApplicationContext(); }

  /** 실제 표시 크기를 버킷으로 묶어 페이지 전환 때 같은 이미지를 공유한다. */
  static int widthBucket(int mode, int width, int height) {
    float ratio = mode == RegistrationCardView.FULL ? 3f / 4f : 978f / 779f;
    int wanted = Math.max(1, Math.round(Math.min(Math.max(1, width), Math.max(1, height) * ratio)));
    int[] buckets = mode == RegistrationCardView.FULL ? new int[] {96, 192, 384, 768}
        : new int[] {64, 128, 256};
    for (int bucket : buckets) if (wanted <= bucket) return bucket;
    return buckets[buckets.length - 1];
  }

  /** 표제·하단 인쇄만 분리한다. 승인된 장면 창 내부는 한 픽셀도 추가로 자르지 않는다. */
  static Rect portraitRect(int width, int height) {
    return new Rect(
        Math.max(0, (int) Math.floor(width * 54d / 1086d)),
        Math.max(0, (int) Math.floor(height * 286d / 1448d)),
        Math.min(width, (int) Math.ceil(width * 1032d / 1086d)),
        Math.min(height, (int) Math.ceil(height * 1065d / 1448d)));
  }

  void request(String id, int mode, int width, int height, Consumer<Bitmap> ready) {
    if (closed) return;
    if (id == null || !id.matches("[A-Z][0-9]{1,3}")
        || (mode != RegistrationCardView.FULL && mode != RegistrationCardView.COMPACT)) {
      post(ready, null);
      return;
    }
    int bucket = widthBucket(mode, width, height);
    enqueue("card:" + id + ":" + mode + ":" + bucket,
        () -> decodeCard(id, mode, bucket), ready);
  }

  void requestEnvelope(Consumer<Bitmap> ready) {
    enqueue("envelope", () -> {
      Bitmap bitmap;
      try (InputStream in = context.getAssets().open(ROOT + "resources/application-envelope-atlas.png")) {
        bitmap = BitmapFactory.decodeStream(in);
      }
      // 프레임은 원본 정수 좌표를 사용한다. 이 아틀라스는 샘플링하지 않는다.
      if (bitmap != null && (bitmap.getWidth() != 1774 || bitmap.getHeight() != 887)) {
        bitmap.recycle();
        return null;
      }
      if (bitmap != null) bitmap.setDensity(Bitmap.DENSITY_NONE);
      return bitmap;
    }, ready);
  }

  private interface Decode { Bitmap run() throws Exception; }

  private void post(Consumer<Bitmap> ready, Bitmap bitmap) {
    ui.post(() -> { if (!closed) ready.accept(bitmap); });
  }

  private synchronized void enqueue(String key, Decode decode, Consumer<Bitmap> ready) {
    if (closed) return;
    Bitmap found = cache.get(key);
    if (found != null) { post(ready, found); return; }
    List<Consumer<Bitmap>> waiting = pending.get(key);
    if (waiting != null) { waiting.add(ready); return; }
    waiting = new ArrayList<>();
    waiting.add(ready);
    pending.put(key, waiting);
    try {
      worker.execute(() -> {
        Bitmap bitmap = null;
        try { if (!closed) bitmap = decode.run(); }
        catch (Exception ignored) { /* 손상/누락은 실제 텍스트와 실패 상태로 표시한다. */ }
        Bitmap result = bitmap;
        List<Consumer<Bitmap>> callbacks;
        synchronized (RegistrationArt.this) {
          callbacks = pending.remove(key);
          if (!closed && result != null) cache.put(key, result);
        }
        if (closed || callbacks == null) return;
        ui.post(() -> {
          if (!closed) for (Consumer<Bitmap> callback : callbacks) callback.accept(result);
        });
      });
    } catch (RejectedExecutionException rejected) {
      List<Consumer<Bitmap>> callbacks = pending.remove(key);
      if (!closed && callbacks != null) for (Consumer<Bitmap> callback : callbacks) post(callback, null);
    }
  }

  // 이 메서드는 단일 디코더 스레드에서만 호출한다. 파일 읽기/JSON 파싱 동안
  // enqueue·close가 쓰는 모니터를 잡으면 UI의 다음 요청까지 디스크 I/O를 기다린다.
  private Set<String> manifestIds() throws Exception {
    if (ids != null) return ids;
    JSONObject manifest = new JSONObject(read("manifest.json"));
    JSONArray items = manifest.getJSONArray("items");
    HashSet<String> validated = new HashSet<>();
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      String id = item.getString("id");
      if (!id.matches("[A-Z][0-9]{1,3}")
          || !item.getString("path").equals("resources/cards/" + id + ".webp")
          || item.getInt("width") != 768 || item.getInt("height") != 1024
          || !validated.add(id)) throw new IOException("잘못된 등록증 목록");
    }
    if (validated.size() != 145 || manifest.getInt("characterCount") != 145)
      throw new IOException("등록증 수량 불일치");
    JSONObject layout = new JSONObject(read("component/layout.json"));
    JSONObject window = layout.getJSONObject("portraitWindow");
    JSONArray master = layout.getJSONArray("viewBox");
    if (master.getInt(0) != 0 || master.getInt(1) != 0
        || master.getInt(2) != 1086 || master.getInt(3) != 1448
        || window.getInt("x") != 54 || window.getInt("y") != 286
        || window.getInt("width") != 978 || window.getInt("height") != 779)
      throw new IOException("등록증 장면 영역 불일치");
    ids = Collections.unmodifiableSet(validated);
    return ids;
  }

  private String read(String path) throws IOException {
    try (InputStream in = context.getAssets().open(ROOT + path);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] data = new byte[8192];
      int length;
      while ((length = in.read(data)) != -1) {
        if (out.size() + length > 512 * 1024) throw new IOException("등록증 메타데이터 크기 초과");
        out.write(data, 0, length);
      }
      return out.toString("UTF-8");
    }
  }

  private Bitmap decodeCard(String id, int mode, int width) throws Exception {
    if (!manifestIds().contains(id) || closed) return null;
    String path = ROOT + "resources/cards/" + id + ".webp";
    BitmapFactory.Options dimensions = new BitmapFactory.Options();
    dimensions.inJustDecodeBounds = true;
    try (InputStream in = context.getAssets().open(path)) {
      BitmapFactory.decodeStream(in, null, dimensions);
    }
    if (dimensions.outWidth != 768 || dimensions.outHeight != 1024) return null;
    Rect source = mode == RegistrationCardView.COMPACT ? portraitRect(768, 1024)
        : new Rect(0, 0, 768, 1024);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    options.inSampleSize = 1;
    while (source.width() / (options.inSampleSize * 2) >= width) options.inSampleSize *= 2;
    Bitmap decoded;
    try (InputStream in = context.getAssets().open(path)) {
      decoded = BitmapFactory.decodeStream(in, null, options);
    }
    if (decoded == null) return null;
    Bitmap selected = decoded;
    if (mode == RegistrationCardView.COMPACT) {
      Rect region = portraitRect(decoded.getWidth(), decoded.getHeight());
      selected = Bitmap.createBitmap(decoded, region.left, region.top, region.width(), region.height());
      if (selected != decoded) decoded.recycle(); // 아직 뷰에 전달하지 않은 작업자 소유 임시 이미지.
    }
    if (selected.getWidth() > width) {
      int height = Math.max(1, Math.round(selected.getHeight() * width / (float) selected.getWidth()));
      Bitmap scaled = Bitmap.createScaledBitmap(selected, width, height, true);
      if (scaled != selected) selected.recycle();
      selected = scaled;
    }
    selected.setDensity(Bitmap.DENSITY_NONE);
    return selected;
  }

  synchronized int cachedBytes() { return cache.size(); }
  synchronized int pendingCount() { return pending.size(); }
  synchronized boolean idle() { return pending.isEmpty(); }
  boolean closed() { return closed; }

  synchronized void close() {
    closed = true;
    pending.clear();
    worker.shutdownNow();
    cache.evictAll(); // 이미 화면에 전달한 Bitmap은 화면이 놓을 때 해제한다.
  }
}
