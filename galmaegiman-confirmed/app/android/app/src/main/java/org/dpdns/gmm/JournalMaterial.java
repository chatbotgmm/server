package org.dpdns.gmm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.io.IOException;
import java.io.InputStream;

/** APK에 포함한 작은 종이 재질을 늘이지 않고 반복한다. 화면·시간대·계정과 독립적이다. */
final class JournalMaterial {
  private static final String ASSET = "native/journal/parchment-grain.webp";
  private static volatile Bitmap paper;
  private static boolean attempted;

  private static final ThreadLocal<DrawState> DRAW =
      new ThreadLocal<DrawState>() {
        @Override
        protected DrawState initialValue() {
          return new DrawState();
        }
      };

  private JournalMaterial() {}

  /** 기존 이미지 작업자에서 호출한다. 프로세스당 한 번만 디코딩하며 Context는 보관하지 않는다. */
  static synchronized void prepare(Context context) {
    if (attempted) return;
    attempted = true;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inJustDecodeBounds = true;
    try (InputStream in = context.getAssets().open(ASSET)) {
      BitmapFactory.decodeStream(in, null, options);
    } catch (IOException ignored) {
      return;
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return;
    options.inJustDecodeBounds = false;
    options.inSampleSize = 1;
    while (options.outWidth / options.inSampleSize > 512
        || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    options.inMutable = false;
    try (InputStream in = context.getAssets().open(ASSET)) {
      Bitmap decoded = BitmapFactory.decodeStream(in, null, options);
      if (decoded != null) {
        decoded.setDensity(Bitmap.DENSITY_NONE);
        paper = decoded;
      }
    } catch (IOException ignored) {
      // 재질이 없어도 기존 종이색으로 정상 표시한다. 네트워크 요청은 하지 않는다.
    }
  }

  static boolean ready() {
    return paper != null;
  }

  /**
   * 사각 영역 안에만 그린다. 둥근 모서리는 호출 측에서 clipPath로 제한한다. 최초 스레드 상태 준비 이후에는 Paint·Matrix·Shader를 재사용하며,
   * 디코딩·비트맵 생성·시간 기반 애니메이션을 하지 않는다.
   */
  static boolean draw(Canvas canvas, RectF bounds, float density, int opacity) {
    Bitmap bitmap = paper;
    if (bitmap == null || bounds == null || bounds.isEmpty()) return false;
    int alpha = Math.max(0, Math.min(255, opacity));
    if (alpha == 0) return true;
    DrawState state = DRAW.get();
    if (state.bitmap != bitmap) {
      state.bitmap = bitmap;
      state.shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
      state.paint.setShader(state.shader);
    }
    // 약 384dp의 종이 한 장에 해당하는 결 크기다. 컨트롤 크기에 맞춰 늘이지 않는다.
    float scale = Math.max(.25f, density) * .75f;
    state.matrix.setScale(scale, scale);
    state.matrix.postTranslate(bounds.left, bounds.top);
    state.shader.setLocalMatrix(state.matrix);
    state.paint.setAlpha(alpha);
    canvas.drawRect(bounds, state.paint);
    return true;
  }

  private static final class DrawState {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Matrix matrix = new Matrix();
    Bitmap bitmap;
    BitmapShader shader;
  }
}
