package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** 기본 페이지는 움직이지 않는 한 화면으로 유지한다. 남은 높이에 온전히 들어가는 섹션만 보여 주고, 긴 목록은 별도 대화상자에서 원래 위젯과 동작 그대로 확인한다. */
final class JournalViewport extends ViewGroup {
  private final MainActivity host;
  private final LinearLayout content;
  private final String title;
  private final Button more;
  private final TextView summary;
  private final int maxPreviewRows;
  private final IdentityHashMap<View, Integer> hidden = new IdentityHashMap<>();
  private int contentHeight, summaryHeight, moreHeight;
  private boolean inSheet;
  private AlertDialog sheet;

  private boolean isModalContent() {
    android.view.ViewParent parent = getParent();
    while (parent instanceof View) {
      if (parent instanceof GameDialogContent) return true;
      parent = parent.getParent();
    }
    return false;
  }

  JournalViewport(MainActivity host, String title, LinearLayout content) {
    this(host, title, content, Integer.MAX_VALUE);
  }

  JournalViewport(MainActivity host, String title, LinearLayout content, int maxPreviewRows) {
    super(host);
    this.host = host;
    this.title = title;
    this.content = content;
    this.maxPreviewRows = Math.max(1, maxPreviewRows);
    setTag("journal-page-viewport");
    setClipChildren(true);
    addView(content, new LayoutParams(-1, -2));
    summary = new MenuKit(host).note("");
    summary.setMaxLines(3);
    summary.setPadding(host.dp(10), host.dp(6), host.dp(10), host.dp(6));
    summary.setBackground(new GamePaperDrawable(host));
    summary.setVisibility(GONE);
    addView(summary, new LayoutParams(-1, -2));
    more = new MenuKit(host).action(null, moreLabel(), this::openContents);
    more.setTag("journal-page-more");
    more.setMinHeight(host.dp(48));
    more.setMinimumHeight(host.dp(48));
    more.setPadding(host.dp(10), host.dp(4), host.dp(10), host.dp(6));
    more.setContentDescription(title + " 전체 보기");
    more.setVisibility(GONE);
    addView(more, new LayoutParams(-1, host.dp(48)));
  }

  private String moreLabel() {
    return title.contains("선택") ? "목록 선택  ›" : "전체 보기  ›";
  }

  private void restoreHidden() {
    for (Map.Entry<View, Integer> entry : hidden.entrySet()) {
      View child = entry.getKey();
      if (child.getParent() == content && child.getVisibility() == GONE)
        child.setVisibility(entry.getValue());
    }
    hidden.clear();
  }

  private int occupiedHeight(View child) {
    int height = child.getMeasuredHeight();
    ViewGroup.LayoutParams params = child.getLayoutParams();
    if (params instanceof MarginLayoutParams) {
      MarginLayoutParams margins = (MarginLayoutParams) params;
      height += margins.topMargin + margins.bottomMargin;
    }
    return height;
  }

  private int visibleRows() {
    int rows = 0;
    for (int i = 0; i < content.getChildCount(); i++)
      if (content.getChildAt(i).getVisibility() != GONE
          || hidden.containsKey(content.getChildAt(i))) rows++;
    return rows;
  }

  private int measureNaturalContent(int innerWidth) {
    java.util.Iterator<View> old = hidden.keySet().iterator();
    while (old.hasNext()) if (old.next().getParent() != content) old.remove();
    int height = content.getPaddingTop() + content.getPaddingBottom();
    for (int i = 0; i < content.getChildCount(); i++) {
      View child = content.getChildAt(i);
      if (child.getVisibility() == GONE && !hidden.containsKey(child)) continue;
      ViewGroup.LayoutParams params = child.getLayoutParams();
      int margins = 0;
      if (params instanceof MarginLayoutParams) {
        MarginLayoutParams lp = (MarginLayoutParams) params;
        margins = lp.leftMargin + lp.rightMargin;
      }
      int width =
          Math.max(0, innerWidth - content.getPaddingLeft() - content.getPaddingRight() - margins);
      int childWidth =
          getChildMeasureSpec(
              MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, params.width);
      int childHeight =
          params.height >= 0
              ? MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY)
              : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
      child.measure(childWidth, childHeight);
      height += occupiedHeight(child);
    }
    return Math.max(height, content.getMinimumHeight());
  }

  private void hide(View child) {
    if (!hidden.containsKey(child)) hidden.put(child, child.getVisibility());
    if (child.getVisibility() != GONE) child.setVisibility(GONE);
  }

  private void reveal(View child) {
    Integer original = hidden.remove(child);
    if (original != null && child.getVisibility() == GONE) child.setVisibility(original);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int widthMode = MeasureSpec.getMode(widthSpec);
    if (widthMode == MeasureSpec.UNSPECIFIED)
      width = Math.max(host.dp(160), getSuggestedMinimumWidth());
    int innerWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
    int widthExact = MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY);
    int heightMode = MeasureSpec.getMode(heightSpec);
    int outerBudget = MeasureSpec.getSize(heightSpec);
    int padding = getPaddingTop() + getPaddingBottom();
    int budget = Math.max(0, outerBudget - padding);
    contentHeight = summaryHeight = moreHeight = 0;

    if (!inSheet) {
      int natural = measureNaturalContent(innerWidth);
      if (heightMode == MeasureSpec.UNSPECIFIED) budget = natural + host.dp(54);
      int previewLimit = isModalContent() ? Integer.MAX_VALUE : maxPreviewRows;
      boolean overflow = natural > budget || visibleRows() > previewLimit;
      if (!overflow) {
        restoreHidden();
        content.setVisibility(VISIBLE);
        more.setVisibility(GONE);
        summary.setVisibility(GONE);
        // Weighted native layouts receive their actual remaining height, without shrinking text.
        int mode = heightMode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
        content.measure(widthExact, MeasureSpec.makeMeasureSpec(budget, mode));
        contentHeight = content.getMeasuredHeight();
      } else {
        int gap = host.dp(6);
        moreHeight = Math.min(host.dp(48), budget);
        int available = Math.max(0, budget - moreHeight - gap);
        int used = content.getPaddingTop() + content.getPaddingBottom();
        int accepted = 0;
        boolean tail = false;
        for (int i = 0; i < content.getChildCount(); i++) {
          View child = content.getChildAt(i);
          if (child.getVisibility() == GONE && !hidden.containsKey(child)) continue;
          int height = occupiedHeight(child);
          if (tail || accepted >= previewLimit || used + height > available) {
            tail = true;
            hide(child);
          } else {
            reveal(child);
            used += height;
            accepted++;
          }
        }
        if (accepted > 0) {
          content.setVisibility(VISIBLE);
          summary.setVisibility(GONE);
          content.measure(widthExact, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
          contentHeight = content.getMeasuredHeight();
        } else {
          content.setVisibility(GONE);
          updateSummary(summaryText());
          summary.setVisibility(VISIBLE);
          summary.measure(widthExact, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
          summaryHeight = summary.getMeasuredHeight();
        }
        more.setVisibility(VISIBLE);
        more.measure(widthExact, MeasureSpec.makeMeasureSpec(moreHeight, MeasureSpec.EXACTLY));
      }
    } else {
      updateSummary(title + "\n열린 창에서 목록과 설정을 확인하세요.");
      summary.setVisibility(VISIBLE);
      summary.measure(widthExact, MeasureSpec.makeMeasureSpec(budget, MeasureSpec.AT_MOST));
      summaryHeight = summary.getMeasuredHeight();
      more.setVisibility(GONE);
    }
    int wanted =
        padding + contentHeight + summaryHeight + (moreHeight > 0 ? host.dp(6) + moreHeight : 0);
    setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(wanted, heightSpec));
  }

  private String summaryText() {
    ArrayList<String> lines = new ArrayList<>();
    collectSummary(content, lines);
    return lines.isEmpty() ? title : android.text.TextUtils.join("\n", lines);
  }

  private void updateSummary(String text) {
    if (!android.text.TextUtils.equals(summary.getText(), text)) summary.setText(text);
  }

  private void collectSummary(View view, ArrayList<String> lines) {
    if (lines.size() >= 3) return;
    if (view instanceof TextView && !(view instanceof Button)) {
      CharSequence value = ((TextView) view).getText();
      if (value != null && value.length() > 0) {
        String line = value.toString().trim().replace('\n', ' ');
        if (!line.isEmpty() && !lines.contains(line)) lines.add(line);
      }
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount() && lines.size() < 3; i++)
        collectSummary(group.getChildAt(i), lines);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int x = getPaddingLeft(), width = getWidth() - getPaddingRight(), y = getPaddingTop();
    if (!inSheet && content.getVisibility() != GONE) {
      content.layout(x, y, width, y + contentHeight);
      y += contentHeight;
    }
    if (summary.getVisibility() != GONE) {
      summary.layout(x, y, width, y + summaryHeight);
      y += summaryHeight;
    }
    if (more.getVisibility() != GONE) {
      y += host.dp(6);
      more.layout(x, y, width, y + moreHeight);
    }
  }

  private void openContents() {
    if (inSheet || !isAttachedToWindow() || host.isFinishing() || host.isDestroyed()) return;
    final int epoch = host.epoch;
    final View page = host.page;
    restoreHidden();
    content.setVisibility(VISIBLE);
    removeView(content);
    ArrayList<ModalSizing> sizing = new ArrayList<>();
    prepareModalSizing(content, sizing);
    inSheet = true;
    requestLayout();
    sheet =
        new GameDialog(host)
            .setMaxWidthDp(900)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("닫기", null)
            .show();
    final AlertDialog opened = sheet;
    SheetGuard guard = new SheetGuard(host, this, opened);
    opened.setOnDismissListener(
        dialog -> {
          if (host.visibleDialog == opened) host.visibleDialog = null;
          guard.remove();
          if (content.getParent() instanceof ViewGroup)
            ((ViewGroup) content.getParent()).removeView(content);
          restoreModalSizing(sizing);
          sheet = null;
          inSheet = false;
          if (!guard.ownerDetached
              && host.epoch == epoch
              && host.page == page
              && isAttachedToWindow()) {
            addView(content, 0, new LayoutParams(-1, -2));
            requestLayout();
          }
        });
  }

  static AlertDialog openSheet(MainActivity host, String title, View contents) {
    final View page = host.page != null && host.page.isAttachedToWindow() ? host.page : host.root;
    ArrayList<ModalSizing> sizing = new ArrayList<>();
    prepareModalSizing(contents, sizing);
    AlertDialog dialog =
        new GameDialog(host)
            .setMaxWidthDp(900)
            .setTitle(title)
            .setView(contents)
            .setPositiveButton("닫기", null)
            .show();
    SheetGuard guard = new SheetGuard(host, page, dialog);
    dialog.setOnDismissListener(
        dismissed -> {
          if (host.visibleDialog == dialog) host.visibleDialog = null;
          guard.remove();
          restoreModalSizing(sizing);
        });
    return dialog;
  }

  /** 세로 weight=1 고정 영역도 모달에서는 본래 목록의 자연 높이를 확보한다. */
  private static void prepareModalSizing(View view, ArrayList<ModalSizing> changes) {
    if (!(view instanceof ViewGroup)) return;
    ViewGroup group = (ViewGroup) view;
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      ViewGroup.LayoutParams params = child.getLayoutParams();
      if (group instanceof LinearLayout
          && ((LinearLayout) group).getOrientation() == LinearLayout.VERTICAL
          && params instanceof LinearLayout.LayoutParams) {
        LinearLayout.LayoutParams original = (LinearLayout.LayoutParams) params;
        if (original.height == 0 && original.weight > 0) {
          LinearLayout.LayoutParams adjusted = new LinearLayout.LayoutParams(original);
          adjusted.height = LayoutParams.WRAP_CONTENT;
          adjusted.weight = 0;
          changes.add(new ModalSizing(child, original, adjusted));
          child.setLayoutParams(adjusted);
        }
      }
      prepareModalSizing(child, changes);
    }
  }

  private static void restoreModalSizing(ArrayList<ModalSizing> changes) {
    for (ModalSizing change : changes)
      if (change.view.getLayoutParams() == change.adjusted)
        change.view.setLayoutParams(change.original);
  }

  private static final class ModalSizing {
    final View view;
    final LinearLayout.LayoutParams original, adjusted;

    ModalSizing(View view, LinearLayout.LayoutParams original, LinearLayout.LayoutParams adjusted) {
      this.view = view;
      this.original = original;
      this.adjusted = adjusted;
    }
  }

  private static final class SheetGuard
      implements View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
    final MainActivity host;
    final View page;
    final AlertDialog dialog;
    final int epoch;
    boolean ownerDetached;

    SheetGuard(MainActivity host, View page, AlertDialog dialog) {
      this.host = host;
      this.page = page;
      this.dialog = dialog;
      epoch = host.epoch;
      page.addOnAttachStateChangeListener(this);
      host.root.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    public void onViewAttachedToWindow(View view) {}

    @Override
    public void onViewDetachedFromWindow(View view) {
      ownerDetached = true;
      if (dialog.isShowing()) dialog.dismiss();
    }

    @Override
    public void onGlobalLayout() {
      if (host.epoch != epoch && dialog.isShowing()) dialog.dismiss();
    }

    void remove() {
      page.removeOnAttachStateChangeListener(this);
      ViewTreeObserver tree = host.root.getViewTreeObserver();
      if (tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
    }
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  }
}
