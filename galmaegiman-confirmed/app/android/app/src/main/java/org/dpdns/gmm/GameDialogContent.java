package org.dpdns.gmm;

import android.view.*;
import android.widget.ScrollView;
import java.util.function.IntSupplier;

/** 제목·행동의 자연 높이를 먼저 확보하고, 본문만 남은 영역에서 스크롤한다. */
final class GameDialogContent extends ViewGroup {
  final MainActivity host;
  final IntSupplier heightBudget;
  View header, footer;
  ScrollView scroll;

  GameDialogContent(MainActivity host, IntSupplier heightBudget) {
    super(host);
    this.host = host;
    this.heightBudget = heightBudget;
    setTag("game-dialog-body");
    setPadding(host.dp(12), host.dp(4), host.dp(12), host.dp(8));
    setClipChildren(true);
  }

  void header(View view) {
    if (header != null) removeView(header);
    header = view;
    if (view != null) addView(view);
    requestLayout();
  }

  void footer(View view) {
    if (footer != null) removeView(footer);
    footer = view;
    if (view != null) addView(view);
    requestLayout();
  }

  void body(View view) {
    if (scroll != null) removeView(scroll);
    if (view instanceof ScrollView) scroll = (ScrollView) view;
    else {
      scroll = new ScrollView(host);
      scroll.setTag("game-dialog-body-scroll");
      scroll.addView(view, new ScrollView.LayoutParams(-1, -2));
    }
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(true);
    scroll.setClipToPadding(true);
    addView(scroll);
    requestLayout();
  }

  int naturalHeight(View view, int width) {
    if (view == null || view.getVisibility() == GONE) return 0;
    view.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    return view.getMeasuredHeight();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int innerWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
    int budget = Math.max(0, heightBudget.getAsInt());
    if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
      budget = Math.min(budget, MeasureSpec.getSize(heightSpec));
    int headerHeight = naturalHeight(header, innerWidth);
    int footerHeight = naturalHeight(footer, innerWidth);
    int padding = getPaddingTop() + getPaddingBottom();
    int bodyBudget = Math.max(0, budget - padding - headerHeight - footerHeight);
    int bodyHeight = 0;
    if (scroll != null) {
      scroll.measure(
          MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(bodyBudget, MeasureSpec.AT_MOST));
      bodyHeight = scroll.getMeasuredHeight();
    }
    setMeasuredDimension(
        resolveSize(width, widthSpec),
        resolveSize(padding + headerHeight + bodyHeight + footerHeight, heightSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int left = getPaddingLeft(), right = getWidth() - getPaddingRight(), y = getPaddingTop();
    if (header != null && header.getVisibility() != GONE) {
      header.layout(left, y, right, y + header.getMeasuredHeight());
      y += header.getMeasuredHeight();
    }
    if (scroll != null) {
      scroll.layout(left, y, right, y + scroll.getMeasuredHeight());
      y += scroll.getMeasuredHeight();
    }
    if (footer != null && footer.getVisibility() != GONE)
      footer.layout(left, y, right, y + footer.getMeasuredHeight());
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  }
}
