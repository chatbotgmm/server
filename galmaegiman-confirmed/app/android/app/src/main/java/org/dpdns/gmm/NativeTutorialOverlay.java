package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 안내 대상만 밝히고 실제 버튼의 클릭을 전달한다. 대화·보상 연출 위에는 올리지 않는다. */
final class NativeTutorialOverlay extends FrameLayout implements FullBleed {
  final MainActivity h;
  final View target;
  final RectF hole = new RectF();
  final LinearLayout bubble;
  final Button skip;
  final TextView progress;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Runnable hit;
  final boolean hint, light;
  boolean pressed;
  final ViewTreeObserver.OnPreDrawListener follow =
      () -> {
        RectF previous = new RectF(hole);
        locate();
        if (!previous.equals(hole)) {
          requestLayout();
          invalidate();
        }
        return true;
      };

  NativeTutorialOverlay(
      MainActivity h,
      View target,
      int step,
      String title,
      String message,
      String confirm,
      Runnable confirmed,
      Runnable skipped,
      Runnable hit,
      boolean hint,
      boolean light) {
    super(h);
    this.h = h;
    this.target = target;
    this.hit = hit;
    this.hint = hint;
    this.light = light;
    setWillNotDraw(false);
    setElevation(h.dp(20));
    setContentDescription(title + " · " + message);
    MenuKit k = new MenuKit(h);
    progress = h.text("①  ②  ③  ④  ⑤  ·  " + Math.min(5, step) + " / 5", 15);
    progress.setTextColor(Color.WHITE);
    progress.setShadowLayer(h.dp(2), 0, h.dp(1), 0xff3b2614);
    progress.setGravity(Gravity.CENTER);
    addView(progress);
    skip = h.button(hint ? "닫기" : "건너뛰기", skipped);
    skip.setTextSize(15);
    skip.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    addView(skip);
    if (hint) progress.setVisibility(GONE);
    bubble = k.paper();
    bubble.setBackground(new GamePanelDrawable(h));
    bubble.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(12));
    LinearLayout row = k.row();
    row.setGravity(Gravity.TOP);
    ImageView guardian = new ImageView(h);
    guardian.setImageBitmap(h.art.asset("t2_guardian.webp"));
    guardian.setScaleType(ImageView.ScaleType.FIT_CENTER);
    row.addView(
        guardian, new LinearLayout.LayoutParams(h.dp(hint ? 62 : 72), h.dp(hint ? 72 : 84)));
    LinearLayout words = k.column();
    TextView heading = h.text(title, 20);
    words.addView(heading);
    TextView note = k.note(message);
    note.setTextSize(16);
    note.setTextColor(MainActivity.INK);
    words.addView(note);
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    ScrollView scroll = k.scroll(row);
    bubble.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (confirm != null) {
      Button ok = h.button(confirm, confirmed);
      ok.setTextSize(16);
      ok.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
      LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, h.dp(48));
      action.topMargin = h.dp(6);
      bubble.addView(ok, action);
    }
    addView(bubble);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnPreDrawListener(follow);
  }

  @Override
  protected void onDetachedFromWindow() {
    getViewTreeObserver().removeOnPreDrawListener(follow);
    super.onDetachedFromWindow();
  }

  void locate() {
    hole.setEmpty();
    if (target == null || !target.isShown()) return;
    Rect rect = new Rect();
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    if (target.getGlobalVisibleRect(rect)) {
      hole.set(
          rect.left - origin[0],
          rect.top - origin[1],
          rect.right - origin[0],
          rect.bottom - origin[1]);
      hole.inset(-h.dp(5), -h.dp(5));
    }
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    setMeasuredDimension(width, height);
    RectF safe = h.safeBounds(width, height);
    progress.measure(
        MeasureSpec.makeMeasureSpec((int) safe.width(), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(h.dp(28), MeasureSpec.EXACTLY));
    skip.measure(
        MeasureSpec.makeMeasureSpec(h.dp(hint ? 86 : 124), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(h.dp(48), MeasureSpec.EXACTLY));
    int bubbleWidth = Math.min(h.dp(620), Math.max(h.dp(260), (int) (safe.width() * .7f)));
    int bodyHeight =
        Math.min(h.dp(hint ? 146 : 220), Math.max(h.dp(108), (int) safe.height() - h.dp(92)));
    if (target != null && !hint && !light) bodyHeight = Math.min(bodyHeight, h.dp(174));
    bubble.measure(
        MeasureSpec.makeMeasureSpec(bubbleWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    locate();
    RectF safe = h.safeBounds(getWidth(), getHeight());
    progress.layout(
        (int) safe.left, (int) safe.top + h.dp(6), (int) safe.right, (int) safe.top + h.dp(34));
    int skipX = (int) safe.right - skip.getMeasuredWidth() - h.dp(8);
    int skipY = (int) safe.top + h.dp(6);
    if (!hole.isEmpty() && hole.top < skipY + h.dp(52) && hole.right > skipX)
      skipY = (int) safe.bottom - h.dp(56);
    skip.layout(skipX, skipY, skipX + skip.getMeasuredWidth(), skipY + h.dp(48));
    int bw = bubble.getMeasuredWidth(), bh = bubble.getMeasuredHeight();
    float x = safe.centerX() - bw / 2f, y = safe.centerY() - bh / 2f;
    if (!hole.isEmpty()) {
      boolean below = hole.centerY() < safe.centerY();
      y = below ? hole.bottom + h.dp(10) : hole.top - bh - h.dp(10);
      x = hole.centerX() - bw / 2f;
    } else if (light) y = safe.top + h.dp(54);
    x = Math.max(safe.left + h.dp(8), Math.min(safe.right - bw - h.dp(8), x));
    y = Math.max(safe.top + h.dp(42), Math.min(safe.bottom - bh - h.dp(8), y));
    bubble.layout((int) x, (int) y, (int) x + bw, (int) y + bh);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (!hint && !light) {
      paint.setColor(0xa6000000);
      if (hole.isEmpty()) canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
      else {
        canvas.drawRect(0, 0, getWidth(), hole.top, paint);
        canvas.drawRect(0, hole.bottom, getWidth(), getHeight(), paint);
        canvas.drawRect(0, hole.top, hole.left, hole.bottom, paint);
        canvas.drawRect(hole.right, hole.top, getWidth(), hole.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(h.dp(3));
        paint.setColor(0xffffd45b);
        canvas.drawRoundRect(hole, h.dp(16), h.dp(16), paint);
        paint.setStyle(Paint.Style.FILL);
      }
    }
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent event) {
    if (hint || light) return false;
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      pressed = target != null && hole.contains(event.getX(), event.getY());
      return true;
    }
    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
      if (pressed && target != null && hole.contains(event.getX(), event.getY())) {
        if (hit != null) hit.run();
        target.performClick();
      }
      pressed = false;
      return true;
    }
    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) pressed = false;
    return true;
  }
}
