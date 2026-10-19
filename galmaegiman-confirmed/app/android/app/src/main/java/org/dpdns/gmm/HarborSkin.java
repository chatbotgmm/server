package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/**
 * galmaemiman-runtime-v1(assets/harbor-v1) 표면을 일반 9분할로 그린다. Android 컴파일 NinePatch가 아니다.
 * 원본 inset은 asset-manifest의 시작값(80/72/80/72px, density 3)이며 모서리는 균일 배율로만 줄인다.
 * 선택은 황동 안쪽 선과 체크(색만으로 구별하지 않음), 눌림은 어둡게, 비활성은 회색 덮개로 표면 위에 덧그린다.
 */
final class HarborSkin extends Drawable {
  static final Map<String, Bitmap> CACHE = new HashMap<>();
  static final int INSET_X = 80, INSET_Y = 72;
  static final float DENSITY = 3f;
  final MainActivity host;
  final String asset;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path check = new Path();
  int alpha = 255;
  boolean pressed, enabled = true, selected;
  /** 탭·하단 메뉴처럼 「지금 위치」 표시는 황동 선만 두고 체크는 생략한다. */
  boolean checkMark = true;

  HarborSkin(MainActivity host, String asset) {
    this.host = host;
    this.asset = asset;
    bitmap(host, asset);
  }

  static Bitmap bitmap(MainActivity h, String name) {
    synchronized (CACHE) {
      if (!CACHE.containsKey(name)) {
        try (java.io.InputStream in = h.getAssets().open("harbor-v1/" + name + ".png")) {
          CACHE.put(name, BitmapFactory.decodeStream(in));
        } catch (java.io.IOException e) {
          CACHE.put(name, null);
        }
      }
      return CACHE.get(name);
    }
  }

  /** 원본 모서리를 보존한 채 가운데·변만 늘린다. 칸이 모서리 합보다 작으면 모서리를 같은 비율로 줄인다. */
  static void nineSlice(MainActivity h, Canvas c, Bitmap b, RectF box, Paint p) {
    nineSlice(h, c, b, box, p, DENSITY);
  }

  /** density: 원본 px를 dp로 나누는 값. 48dp 높이 띠처럼 낮은 칸은 값을 키워 모서리를 작게 둔다(실측 검수 값). */
  static void nineSlice(MainActivity h, Canvas c, Bitmap b, RectF box, Paint p, float density) {
    float x = h.dp(INSET_X / density), y = h.dp(INSET_Y / density);
    float shrink = Math.min(1, Math.min(box.width() / (2 * x), box.height() / (2 * y)));
    x *= shrink;
    y *= shrink;
    int[] sx = {0, INSET_X, b.getWidth() - INSET_X, b.getWidth()};
    int[] sy = {0, INSET_Y, b.getHeight() - INSET_Y, b.getHeight()};
    float[] dx = {box.left, box.left + x, box.right - x, box.right};
    float[] dy = {box.top, box.top + y, box.bottom - y, box.bottom};
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 3; col++)
        c.drawBitmap(
            b,
            new Rect(sx[col], sy[row], sx[col + 1], sy[row + 1]),
            new RectF(dx[col], dy[row], dx[col + 1], dy[row + 1]),
            p);
  }

  static void contain(Canvas c, Bitmap image, RectF box, Paint p) {
    BattleSkin.contain(c, image, box, p);
  }

  @Override
  public void draw(Canvas c) {
    RectF box = new RectF(getBounds());
    if (pressed) box.offset(0, host.dp(1));
    Bitmap b = bitmap(host, asset);
    paint.setAlpha(alpha);
    if (b == null) {
      paint.setColor(asset.equals("button-primary") ? JournalTheme.PRIMARY : JournalTheme.PAPER);
      c.drawRoundRect(box, host.dp(10), host.dp(10), paint);
    } else nineSlice(host, c, b, box, paint, asset.equals("title-strip") ? 5f : DENSITY);
    RectF inner = new RectF(box);
    inner.inset(host.dp(5), host.dp(5));
    if (selected && enabled) {
      mark.setStyle(Paint.Style.STROKE);
      mark.setStrokeWidth(host.dp(2));
      mark.setColor(JournalTheme.BRASS);
      c.drawRoundRect(inner, host.dp(6), host.dp(6), mark);
    }
    if (selected && enabled && checkMark) {
      float r = host.dp(9), cx = inner.right - r - host.dp(2), cy = inner.top + r + host.dp(2);
      mark.setStyle(Paint.Style.FILL);
      c.drawCircle(cx, cy, r, mark);
      mark.setStyle(Paint.Style.STROKE);
      mark.setStrokeWidth(host.dp(2));
      mark.setStrokeCap(Paint.Cap.ROUND);
      mark.setColor(Color.WHITE);
      check.reset();
      check.moveTo(cx - r * .45f, cy);
      check.lineTo(cx - r * .1f, cy + r * .35f);
      check.lineTo(cx + r * .5f, cy - r * .35f);
      c.drawPath(check, mark);
    }
    if (pressed || !enabled) {
      mark.setStyle(Paint.Style.FILL);
      mark.setColor(pressed ? 0x1f000000 : 0x66d7d5cd);
      c.drawRoundRect(inner, host.dp(6), host.dp(6), mark);
    }
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean p = false, e = false, s = false;
    for (int v : states) {
      p |= v == android.R.attr.state_pressed;
      e |= v == android.R.attr.state_enabled;
      s |= v == android.R.attr.state_selected;
    }
    boolean changed = p != pressed || e != enabled || s != selected;
    pressed = p;
    enabled = e;
    selected = s;
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void setAlpha(int a) {
    alpha = a;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter f) {
    paint.setColorFilter(f);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
