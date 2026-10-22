package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 현재 공통 정보 판으로 안내하고, 가리지 않은 실제 대상 버튼에만 클릭을 전달한다. */
final class NativeTutorialOverlay extends FrameLayout implements FullBleed {
  final MainActivity h;
  final View target;
  final RectF hole = new RectF(), targetBounds = new RectF(), viewport = new RectF();
  final LinearLayout bubble, contents, header;
  final Button skip, revealTarget;
  final TextView progress;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Runnable hit;
  final boolean hint, light;
  final int touchSlop;
  NativeTutorialLayout.Placement placement;
  final NativeTutorialTap tap = new NativeTutorialTap();
  boolean folded;
  final ViewTreeObserver.OnPreDrawListener follow =
      () -> {
        RectF previous = new RectF(hole), previousViewport = new RectF(viewport);
        locate();
        updateViewport(getWidth(), getHeight());
        if (!previous.equals(hole) || !previousViewport.equals(viewport)) {
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
    touchSlop = ViewConfiguration.get(h).getScaledTouchSlop();
    setWillNotDraw(false);
    setElevation(h.dp(20));
    setContentDescription(title + " · " + message);
    MenuKit k = new MenuKit(h);
    bubble = k.paper();
    bubble.setTag("tutorial-guidance-card");
    bubble.setPadding(h.dp(12), h.dp(10), h.dp(12), h.dp(10));
    // 빈 카드 영역을 누른 것도 실제 대상 클릭으로 통과시키지 않는다.
    bubble.setClickable(true);
    contents = k.column();
    header = k.row();
    progress = h.text(hint ? "도움말" : "방범대 안내 · " + Math.max(1, Math.min(5, step)) + " / 5", 14);
    progress.setTypeface(h.font);
    progress.setTextColor(JournalTheme.MUTED);
    progress.setPadding(0, h.dp(4), h.dp(8), h.dp(4));
    header.addView(progress, new LinearLayout.LayoutParams(0, -2, 1));
    skip = h.button(hint ? "닫기" : "건너뛰기", skipped);
    skip.setTag("tutorial-skip");
    skip.setTextSize(h.uiText(14));
    skip.setMinHeight(h.dp(48));
    skip.setMinimumHeight(h.dp(48));
    skip.setPadding(h.dp(8), h.dp(8), h.dp(8), h.dp(8));
    skip.setTextColor(JournalTheme.controlText());
    skip.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    header.addView(skip, new LinearLayout.LayoutParams(h.dp(104), -2));
    contents.addView(header, new LinearLayout.LayoutParams(-1, -2));
    TextView heading = h.text(title, 20);
    heading.setTypeface(h.font);
    heading.setPadding(0, h.dp(6), 0, h.dp(4));
    contents.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    TextView note = k.note(message);
    note.setTextSize(h.uiText(16));
    note.setTextColor(JournalTheme.INK);
    note.setPadding(0, h.dp(4), 0, h.dp(8));
    contents.addView(note, new LinearLayout.LayoutParams(-1, -2));
    if (confirm != null) {
      Button ok = h.button(confirm, confirmed);
      ok.setTag("tutorial-confirm");
      ok.setTextSize(h.uiText(16));
      ok.setTextColor(JournalTheme.navyText());
      ok.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
      ok.setMinHeight(h.dp(48));
      ok.setMinimumHeight(h.dp(48));
      contents.addView(ok, new LinearLayout.LayoutParams(-1, -2));
    }
    revealTarget = h.button("대상 보기", () -> {
      folded = true;
      bubble.setVisibility(INVISIBLE);
      tap.cancel();
      invalidate();
    });
    revealTarget.setTag("tutorial-reveal-target");
    revealTarget.setTextSize(h.uiText(16));
    revealTarget.setTextColor(JournalTheme.navyText());
    revealTarget.setMinHeight(h.dp(48));
    revealTarget.setMinimumHeight(h.dp(48));
    revealTarget.setVisibility(GONE);
    contents.addView(revealTarget, new LinearLayout.LayoutParams(-1, -2));
    // 글꼴 확대·키보드·좁은 안전 영역에서도 글자나 행동을 자르지 않는다.
    ScrollView scroll = k.scroll(contents);
    scroll.setTag("tutorial-guidance-scroll");
    scroll.setClipToPadding(true);
    bubble.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    addView(bubble);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnPreDrawListener(follow);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(follow);
    tap.cancel();
    super.onDetachedFromWindow();
  }

  void locate() {
    hole.setEmpty();
    targetBounds.setEmpty();
    if (target == null || !target.isShown() || !target.isEnabled()) return;
    Rect rect = new Rect();
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    if (target.getGlobalVisibleRect(rect)) {
      targetBounds.set(rect.left - origin[0], rect.top - origin[1],
          rect.right - origin[0], rect.bottom - origin[1]);
      hole.set(targetBounds);
      hole.inset(-h.dp(5), -h.dp(5));
      if (getWidth() > 0 && getHeight() > 0 && !hole.intersect(0, 0, getWidth(), getHeight()))
        hole.setEmpty();
    }
  }

  private void updateViewport(int width, int height) {
    viewport.set(h.safeBounds(width, height));
    if (!viewport.intersect(0, 0, width, height)) viewport.setEmpty();
    // 루트 크기만 믿지 않고 조상 클립과 실제 창의 가시 영역을 함께 적용한다.
    // 첫 측정은 아직 위치가 없으므로, 붙은 뒤 pre-draw에서 다시 계산한다.
    if (getWidth() <= 0 || getHeight() <= 0 || !isAttachedToWindow()) return;
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    Rect visible = new Rect();
    if (getGlobalVisibleRect(visible)) intersectViewport(visible, origin);
    else viewport.setEmpty();
    Rect window = new Rect();
    getWindowVisibleDisplayFrame(window);
    if (!window.isEmpty()) intersectViewport(window, origin);
  }

  private void intersectViewport(Rect screen, int[] origin) {
    if (!viewport.intersect(screen.left - origin[0], screen.top - origin[1],
        screen.right - origin[0], screen.bottom - origin[1])) viewport.setEmpty();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    setMeasuredDimension(width, height);
    locate();
    updateViewport(width, height);
    RectF bounds = new RectF(viewport);
    int margin = Math.min(h.dp(8), Math.max(0, (int) Math.min(bounds.width(), bounds.height()) / 8));
    bounds.inset(margin, margin);
    NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(
        (int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
    NativeTutorialLayout.Box focus = hole.isEmpty() ? null : new NativeTutorialLayout.Box(
        (int) Math.floor(hole.left), (int) Math.floor(hole.top),
        (int) Math.ceil(hole.right), (int) Math.ceil(hole.bottom));
    int desiredWidth = Math.min(h.dp(440), safe.width());
    arrangeHeader(desiredWidth);
    contents.measure(
        MeasureSpec.makeMeasureSpec(Math.max(0, desiredWidth - bubble.getPaddingLeft()
            - bubble.getPaddingRight()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int desiredHeight = Math.min(h.dp(hint ? 220 : 300),
        contents.getMeasuredHeight() + bubble.getPaddingTop() + bubble.getPaddingBottom());
    placement = NativeTutorialLayout.place(safe, focus, desiredWidth, desiredHeight,
        h.dp(220), h.dp(120), h.dp(10));
    revealTarget.setVisibility(placement.coversTarget && target != null ? VISIBLE : GONE);
    if (!folded) bubble.setVisibility(VISIBLE);
    arrangeHeader(placement.card.width());
    bubble.measure(
        MeasureSpec.makeMeasureSpec(placement.card.width(), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(placement.card.height(), MeasureSpec.EXACTLY));
  }

  private void arrangeHeader(int cardWidth) {
    // 좁은 세로 화면·큰 글꼴에서는 진행 문구와 닫기를 위아래로 배치한다.
    boolean stacked = cardWidth < h.dp(320)
        || getResources().getConfiguration().fontScale > 1.3f;
    int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
    if (header.getOrientation() == orientation) return;
    header.setOrientation(orientation);
    progress.setLayoutParams(new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
    skip.setLayoutParams(new LinearLayout.LayoutParams(stacked ? -1 : h.dp(104), -2));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (placement == null) return;
    NativeTutorialLayout.Box box = placement.card;
    bubble.layout(box.left, box.top, box.right, box.bottom);
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
        paint.setColor(JournalTheme.BRASS);
        canvas.drawRoundRect(hole, h.dp(12), h.dp(12), paint);
        paint.setStyle(Paint.Style.FILL);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (hint || light) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      // 대상이 사라지거나 다른 곳을 누르면 안내/건너뛰기를 다시 열 수 있다.
      if (folded && (target == null || !target.isShown() || !target.isEnabled()
          || !targetBounds.contains(event.getX(), event.getY()))) {
        folded = false;
        bubble.setVisibility(VISIBLE);
        tap.cancel();
        requestLayout();
        return true;
      }
      tap.down(event.getX(), event.getY(), target != null && target.isShown() && target.isEnabled()
          && targetBounds.contains(event.getX(), event.getY()));
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      tap.move(event.getX(), event.getY(), touchSlop);
    } else if (action == MotionEvent.ACTION_UP) {
      boolean activate = tap.up(target != null && target.isShown() && target.isEnabled()
          && targetBounds.contains(event.getX(), event.getY()));
      if (activate) {
        if (hit != null) hit.run();
        target.performClick();
      }
    } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
      tap.cancel();
    }
    return true;
  }
}
