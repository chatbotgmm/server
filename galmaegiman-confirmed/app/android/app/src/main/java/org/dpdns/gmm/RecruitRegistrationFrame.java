package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Objects;

/** 기존 등록증의 투명 프레임을 기존 제목·조작 행 뒤에 배치한다. 별도 행은 만들지 않는다. */
final class RecruitRegistrationFrame extends View {
  static final String PATH = "native/registration-v1/resources/registration-frame.png";
  static final int SIDE_DP = 6;
  private static final int SOURCE_WIDTH = 1086, SOURCE_HEIGHT = 1448;
  private final MainActivity host;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Patch[] patches = new Patch[24];
  private Bitmap bitmap;
  private int windowTop, windowBottom, patchCount, generation;

  RecruitRegistrationFrame(MainActivity host) {
    super(host);
    this.host = host;
    windowTop = host.dp(18);
    windowBottom = host.dp(4);
    for (int i = 0; i < patches.length; i++) patches[i] = new Patch();
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setClickable(false);
    setFocusable(false);
  }

  static int sideInset(MainActivity host) { return host.dp(SIDE_DP); }

  /** 픽셀 단위 기존 제목 높이·하단 조작 높이. 중앙 전신의 레이아웃은 변경하지 않는다. */
  void setWindowInsets(int top, int bottom) {
    top = Math.max(0, top);
    bottom = Math.max(0, bottom);
    if (windowTop == top && windowBottom == bottom) return;
    windowTop = top;
    windowBottom = bottom;
    layoutPatches();
    invalidate();
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    final int serial = ++generation, epoch = host.epoch;
    final String account = host.api.token();
    Bitmap cached = LocalGameArt.peek(PATH, 1);
    if (valid(cached)) bitmap = cached;
    final WeakReference<RecruitRegistrationFrame> weak = new WeakReference<>(this);
    LocalGameArt.load(host, PATH, 1, ready -> {
      RecruitRegistrationFrame frame = weak.get();
      if (frame == null || frame.generation != serial || !frame.isAttachedToWindow()
          || frame.host.epoch != epoch || !Objects.equals(account, frame.host.api.token())
          || frame.host.isDestroyed() || frame.host.isFinishing()) return;
      frame.bitmap = valid(ready) ? ready : null;
      frame.invalidate();
    });
  }

  private static boolean valid(Bitmap image) {
    return image != null && !image.isRecycled()
        && image.getWidth() == SOURCE_WIDTH && image.getHeight() == SOURCE_HEIGHT;
  }

  @Override protected void onDetachedFromWindow() {
    ++generation;
    bitmap = null; // 공유 캐시가 소유한 비트맵을 recycle하지 않는다.
    super.onDetachedFromWindow();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    layoutPatches();
  }

  private void layoutPatches() {
    patchCount = 0;
    float width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) return;
    float side = Math.min(sideInset(host), width / 2f);
    float top = Math.min(windowTop, height);
    float bottom = Math.min(windowBottom, height - top);
    float foot = height - bottom;
    float edge = Math.min(host.dp(3), Math.min(side, Math.min(top, bottom)));
    float corner = Math.min(host.dp(10), Math.min(width / 2f, top));

    // 바깥 9분할의 중앙은 그리지 않는다. 기존 전신 창은 완전히 투명하다.
    put(0, 350, 54, 1000, 0, top, side, foot);
    put(1032, 350, 1086, 1000, width - side, top, width, foot);

    // 무문자 남색 바탕을 늘리고 닻은 별도 등비 패치로 유지한다.
    // 전체 상단 286px를 납작하게 줄이면 닻과 모서리까지 일그러진다.
    put(200, 36, 886, 254, side, edge, width - side, top - edge);
    put(80, 0, 1006, 36, corner, 0, width - corner, edge);
    put(0, 80, 54, 254, 0, corner, side, top);
    put(1032, 80, 1086, 254, width - side, corner, width, top);
    put(100, 254, 986, 286, side, top - edge, width - side, top);
    put(0, 0, 80, 80, 0, 0, corner, corner);
    put(1006, 0, 1086, 80, width - corner, 0, width, corner);
    float anchorHeight = Math.max(0, top - edge * 2);
    float anchorWidth = Math.min(anchorHeight * 85f / 136f,
        Math.max(0, (width - side * 2) / 4f));
    anchorHeight = anchorWidth * 136f / 85f;
    float anchorY = (top - anchorHeight) / 2f;
    put(64, 95, 149, 231, side + edge, anchorY,
        side + edge + anchorWidth, anchorY + anchorHeight);
    put(937, 95, 1022, 231, width - side - edge - anchorWidth, anchorY,
        width - side - edge, anchorY + anchorHeight);

    if (bottom > host.dp(8)) {
      // 기존 불합격 행 뒤의 원본 양피지. 새 이름 행이나 명판 높이를 추가하지 않는다.
      float footEdge = Math.min(host.dp(4), bottom / 2f);
      float footCorner = Math.min(host.dp(10), bottom);
      put(54, 1065, 1032, 1395, side, foot, width - side, height - footEdge);
      put(0, 1086, 54, 1368, 0, foot, side, height - footCorner);
      put(1032, 1086, 1086, 1368, width - side, foot, width, height - footCorner);
      put(80, 1395, 1006, 1448, footCorner, height - footEdge,
          width - footCorner, height);
      put(0, 1368, 80, 1448, 0, height - footCorner, footCorner, height);
      put(1006, 1368, 1086, 1448, width - footCorner, height - footCorner, width, height);
    } else {
      // 하단 조작이 없으면 기존 4dp 안에 원본 테두리만 넣는다.
      put(80, 1395, 1006, 1448, side, foot, width - side, height);
      put(0, 1395, 80, 1448, 0, foot, side, height);
      put(1006, 1395, 1086, 1448, width - side, foot, width, height);
    }
  }

  private void put(int left, int top, int right, int bottom,
      float x, float y, float endX, float endY) {
    if (endX <= x || endY <= y) return;
    Patch patch = patches[patchCount++];
    patch.source.set(left, top, right, bottom);
    patch.destination.set(x, y, endX, endY);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (!valid(bitmap)) return;
    for (int i = 0; i < patchCount; i++) {
      Patch patch = patches[i];
      canvas.drawBitmap(bitmap, patch.source, patch.destination, paint);
    }
  }

  private static final class Patch {
    final Rect source = new Rect();
    final RectF destination = new RectF();
  }
}
