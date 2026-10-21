package org.dpdns.gmm;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 원본 배경 위, UI 아래에 명시적으로 배치하는 단일 장면 효과 View. 자동 화면 적용 없음. */
final class BackgroundMotionOverlay extends View {
  private final AssetManager assets;
  private View compositeOwner;
  private Runnable repaint;
  private java.util.function.BooleanSupplier runtimeGate = () -> true;

  // 별도 터치 View를 끼우지 않고 기존 배경 renderer의 정확한 레이어 위치에서 합성한다.
  void bindCompositeOwner(View owner, Runnable invalidateOwner, java.util.function.BooleanSupplier gate) {
    mainThread();
    compositeOwner = owner;
    repaint = invalidateOwner;
    runtimeGate = gate;
  }

  void unbindCompositeOwner() {
    mainThread();
    compositeOwner = null;
    repaint = null;
    runtimeGate = () -> true;
    releaseSheet();
  }

  private View owner() { return compositeOwner == null ? this : compositeOwner; }
  private void invalidateDisplay() { invalidate(); if (repaint != null) repaint.run(); }

  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService decoder = Executors.newSingleThreadExecutor();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint layerPaint = new Paint();
  private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
  private final Matrix sourceToLocal = new Matrix();
  private final RectF viewport = new RectF(), blendBounds = new RectF();
  private Path waterMask;
  private String maskBackground = "", mappedBackground = "", scene = "", error = "";
  private int mappedWidth, mappedHeight, generation;
  private boolean hostResumed, reducedMotion, fallback, disposed, inFlight, transformReady;
  private BackgroundMotionSpec spec;
  private Bitmap sheet;
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!canAnimate()) { reconcile(); return; }
      invalidateDisplay();
      // 로컬 주변 효과는 최대 30fps. 경과 시간으로 샘플하므로 프레임 누락을 추격하지 않는다.
      ui.postDelayed(this, 33);
    }
  };

  BackgroundMotionOverlay(Context context) {
    super(context);
    assets = context.getApplicationContext().getAssets();
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setClickable(false);
    setFocusable(false);
  }

  // 모든 공개 조작 메서드는 UI 스레드에서 호출한다. 화면당 하나를 재사용한다.
  void selectScene(String id) {
    mainThread();
    if (disposed) throw new IllegalStateException("종료된 배경 효과");
    generation++;
    scene = id;
    fallback = false;
    error = "";
    waterMask = null;
    maskBackground = "";
    transformReady = false;
    releaseSheet();
    reconcile();
  }

  /** 원본 배경을 실제로 그린 동일 행렬과 viewport를 전달한다. 효과가 자체 crop을 추정하지 않는다. */
  void setSourceTransform(String background, int width, int height, Matrix transform, RectF clip) {
    mainThread();
    float[] values = new float[9];
    transform.getValues(values);
    for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("유한하지 않은 배경 변환");
    if (width <= 0 || height <= 0 || clip.isEmpty() || !finite(clip)) throw new IllegalArgumentException("배경 좌표 범위 오류");
    Matrix inverse = new Matrix();
    if (!transform.invert(inverse)) throw new IllegalArgumentException("역변환 불가능한 배경 행렬");
    if (transformReady && sourceToLocal.equals(transform) && viewport.equals(clip)
        && mappedBackground.equals(background) && mappedWidth == width && mappedHeight == height) return;
    sourceToLocal.set(transform);
    viewport.set(clip);
    mappedBackground = background;
    mappedWidth = width;
    mappedHeight = height;
    transformReady = true;
    reconcile();
  }

  /** 검증된 물/가림 영역 Path만 전달한다. 원본 이미지 픽셀 좌표이며 null은 안전하게 효과를 끈다. */
  void setWaterMask(String background, Path sourcePixelMask) {
    mainThread();
    waterMask = sourcePixelMask == null || sourcePixelMask.isEmpty() ? null : new Path(sourcePixelMask);
    maskBackground = waterMask == null ? "" : background;
    if (waterMask != null) {
      RectF bounds = new RectF();
      waterMask.computeBounds(bounds, true);
      if (!finite(bounds) || waterMask.isInverseFillType()) {
        waterMask = null;
        maskBackground = "";
        reconcile();
        throw new IllegalArgumentException("유효하지 않은 물 영역 마스크");
      }
    }
    reconcile();
  }

  void setHostResumed(boolean resumed) { mainThread(); if (hostResumed == resumed) return; hostResumed = resumed; reconcile(); }
  void setReducedMotion(boolean reduced) { mainThread(); if (reducedMotion == reduced) return; reducedMotion = reduced; reconcile(); }
  void onTrimMemory() { mainThread(); fallback = true; generation++; releaseSheet(); invalidateDisplay(); }
  void retryEffects() { mainThread(); fallback = false; error = ""; reconcile(); }
  String lastError() { return error; }
  void refreshLifecycle() { mainThread(); reconcile(); }

  void dispose() {
    mainThread();
    if (disposed) return;
    disposed = true;
    generation++;
    releaseSheet();
    decoder.shutdownNow();
    waterMask = null;
    invalidateDisplay();
  }

  private boolean visibleHost() {
    return !disposed && hostResumed && !reducedMotion && !fallback && owner().isAttachedToWindow() && runtimeGate.getAsBoolean()
        && owner().getWindowVisibility() == VISIBLE && owner().isShown();
  }

  private boolean canAnimate() {
    return visibleHost() && owner().hasWindowFocus() && sheet != null && spec != null && transformReady
        && spec.background.equals(mappedBackground) && spec.width == mappedWidth && spec.height == mappedHeight
        && (!spec.needsWaterMask || (waterMask != null && spec.background.equals(maskBackground)));
  }

  private void reconcile() {
    // View 기반 클래스 생성 중 가상 메서드가 호출될 수 있다.
    if (ui == null) return;
    ui.removeCallbacks(tick);
    if (!visibleHost()) {
      releaseSheet();
      invalidateDisplay();
      return;
    }
    if (sheet == null && !inFlight && !scene.isEmpty()) load();
    if (canAnimate()) {
      if (!spec.timeline.isRunning()) spec.timeline.start(SystemClock.elapsedRealtime());
      ui.post(tick);
    } else if (spec != null) spec.timeline.stop();
    invalidateDisplay();
  }

  private void releaseSheet() {
    if (ui != null) ui.removeCallbacks(tick);
    if (spec != null) spec.timeline.stop();
    // 하드웨어 display list가 참조할 수 있으므로 표시했던 bitmap을 강제로 recycle하지 않는다.
    sheet = null;
    spec = null;
  }

  private void load() {
    inFlight = true;
    final int request = generation;
    final String requestedScene = scene;
    decoder.execute(() -> {
      BackgroundMotionSpec loadedSpec = null;
      Bitmap loaded = null;
      String failure = "";
      try {
        loadedSpec = BackgroundMotionSpec.read(assets, requestedScene);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = assets.open(BackgroundMotionSpec.ROOT + loadedSpec.file)) {
          loaded = BitmapFactory.decodeStream(in, null, options);
        }
        if (loaded == null || loaded.getWidth() != 2048 || loaded.getHeight() != 1024 || !loaded.hasAlpha()) {
          throw new IOException("효과 시트 크기/알파 오류");
        }
        loaded.setDensity(Bitmap.DENSITY_NONE);
      } catch (IOException | RuntimeException | OutOfMemoryError problem) {
        failure = problem.getClass().getSimpleName() + ": " + problem.getMessage();
        if (loaded != null) loaded.recycle();
        loaded = null;
      }
      final Bitmap ready = loaded;
      final BackgroundMotionSpec readySpec = loadedSpec;
      final String loadError = failure;
      ui.post(() -> {
        inFlight = false;
        if (request != generation || !visibleHost()) {
          if (ready != null) ready.recycle(); // 화면에 한 번도 넘기지 않은 오래된 결과만 해제.
          if (!disposed) reconcile();
          return;
        }
        if (!loadError.isEmpty()) {
          fallback = true;
          error = loadError;
        } else { sheet = ready; spec = readySpec; }
        reconcile();
      });
    });
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    drawEffect(canvas);
  }

  void drawEffect(Canvas canvas) {
    if (visibleHost() && ((sheet == null && !inFlight) || (canAnimate() && !spec.timeline.isRunning()))) reconcile();
    if (!canAnimate()) return; // 효과만 사라져 아래 기존 정적 배경이 그대로 남는다.
    BackgroundMotionTimeline time = spec.timeline;
    time.sample(SystemClock.elapsedRealtime());
    int save = canvas.save();
    canvas.clipRect(viewport);
    canvas.concat(sourceToLocal);
    canvas.clipRect(0, 0, spec.width, spec.height);
    if (spec.needsWaterMask) canvas.clipPath(waterMask);
    float blend = time.nextWeight;
    if (blend <= 0) {
      paint.setAlpha(Math.round(255 * spec.opacity));
      paint.setXfermode(null);
      canvas.drawBitmap(sheet, spec.sources[time.current], spec.destinations[time.current], paint);
    } else {
      // 두 프레임의 premultiplied 색/알파를 ADD로 보간한 뒤 한 번 source-over 합성한다.
      blendBounds.set(spec.destinations[time.current]);
      blendBounds.union(spec.destinations[time.next]);
      layerPaint.setAlpha(Math.round(255 * spec.opacity));
      int layer = canvas.saveLayer(blendBounds, layerPaint);
      paint.setXfermode(null);
      paint.setAlpha(Math.round(255 * (1-blend)));
      canvas.drawBitmap(sheet, spec.sources[time.current], spec.destinations[time.current], paint);
      paint.setXfermode(add);
      paint.setAlpha(Math.round(255 * blend));
      canvas.drawBitmap(sheet, spec.sources[time.next], spec.destinations[time.next], paint);
      paint.setXfermode(null);
      canvas.restoreToCount(layer);
    }
    canvas.restoreToCount(save);
  }

  @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); reconcile(); }
  @Override protected void onDetachedFromWindow() { generation++; releaseSheet(); super.onDetachedFromWindow(); }
  @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); reconcile(); }
  @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); reconcile(); }
  @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); reconcile(); }
  private static boolean finite(RectF r) { return Float.isFinite(r.left) && Float.isFinite(r.top) && Float.isFinite(r.right) && Float.isFinite(r.bottom); }
  private static void mainThread() {
    if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI 스레드에서 호출하세요");
  }
}
