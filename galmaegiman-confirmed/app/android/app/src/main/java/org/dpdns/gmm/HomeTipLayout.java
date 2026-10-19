package org.dpdns.gmm;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.text.Layout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Measured native advice in free scene space. It never uses font shrinking to fit. */
final class HomeTipLayout {
  static final class Result {
    final Rect bounds;
    final boolean paged;
    final int consumed;
    Result(Rect bounds, boolean paged, int consumed) {
      this.bounds = bounds;
      this.paged = paged;
      this.consumed = consumed;
    }
  }

  static Result measure(TextView text, RectF safe, RectF hero, List<RectF> exclusions,
      int gap, int preferredWidth, int maximumWidth, int minimumWidth) {
    CharSequence copy = text.getText();
    Result result = fit(text, safe, hero, exclusions, gap, preferredWidth, maximumWidth, minimumWidth);
    // Before paging, try the same readable font with a measured 4dp rather than 8dp vertical inset.
    // This recovers complete advice on particularly short phones with a battle-resume row.
    int compactInset = Math.max(1, gap / 2);
    if ((result.paged || result.bounds.isEmpty()) && text.getPaddingTop() > compactInset) {
      text.setText(copy);
      text.setPadding(text.getPaddingLeft(), compactInset, text.getPaddingRight(), compactInset);
      result = fit(text, safe, hero, exclusions, gap, preferredWidth, maximumWidth, minimumWidth);
    }
    return result;
  }

  private static Result fit(TextView text, RectF safe, RectF hero, List<RectF> exclusions,
      int gap, int preferredWidth, int maximumWidth, int minimumWidth) {
    // Whole text is measured first. AT_MOST height must not masquerade as a successful fit.
    ArrayList<Rect> spaces = freeSpaces(safe, exclusions, gap);
    Rect best = null, fallback = null;
    double bestScore = Double.MAX_VALUE, fallbackScore = Double.MAX_VALUE;
    int minimumHeight = Math.max(text.getMinHeight(), text.getCompoundPaddingTop()
        + text.getLineHeight() + text.getCompoundPaddingBottom());
    for (Rect space : spaces) {
      if (space.width() < minimumWidth || space.height() < minimumHeight) continue;
      int widest = Math.min(maximumWidth, space.width());
      // The second width is a measured, bounded alternative when large text needs more room.
      int[] widths = {Math.min(preferredWidth, widest), widest};
      for (int width : widths) {
        text.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int fullHeight = text.getMeasuredHeight();
        int height = Math.min(fullHeight, space.height());
        int x = clamp(Math.round(hero.right + gap), space.left, space.right - width);
        int y = clamp(Math.round(hero.top - height * .45f), space.top, space.bottom - height);
        Rect candidate = new Rect(x, y, x + width, y + height);
        double score = score(candidate, hero, gap, preferredWidth);
        if (fullHeight <= space.height()) {
          if (score < bestScore) { best = candidate; bestScore = score; }
        } else {
          // Retain a bounded fallback for complete native lines, advanced by a text tap.
          // Supported phone/tablet fixtures must fit the complete original advice.
          double overflowScore = fullHeight / (double) height * 1000000 + score;
          if (overflowScore < fallbackScore) {
            fallback = candidate;
            fallbackScore = overflowScore;
          }
        }
      }
    }
    boolean paged = best == null && fallback != null;
    Rect result = best != null ? best : fallback != null ? fallback : new Rect();
    int consumed = text.length();
    if (paged) {
      CharSequence remaining = text.getText();
      text.measure(View.MeasureSpec.makeMeasureSpec(result.width(), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      Layout layout = text.getLayout();
      int room = result.height() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
      consumed = 0;
      if (layout != null) for (int line = 0; line < layout.getLineCount(); line++) {
        if (layout.getLineBottom(line) > room) break;
        consumed = layout.getLineEnd(line);
      }
      // Include font padding on the page's new last line, too. Retain styled native spans.
      while (consumed > 0) {
        text.setText(remaining.subSequence(0, consumed));
        text.measure(View.MeasureSpec.makeMeasureSpec(result.width(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (text.getMeasuredHeight() <= result.height()) break;
        Layout page = text.getLayout();
        consumed = page == null || page.getLineCount() <= 1 ? 0
            : page.getLineStart(page.getLineCount() - 1);
      }
      if (consumed == 0) result.setEmpty();
      else result.bottom = result.top + text.getMeasuredHeight();
    }
    text.measure(View.MeasureSpec.makeMeasureSpec(result.width(), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(result.height(), View.MeasureSpec.EXACTLY));
    return new Result(result, paged, consumed);
  }

  private static double score(Rect box, RectF hero, int gap, int preferredWidth) {
    double preference = box.left >= hero.right + gap - 1 ? 0
        : box.bottom <= hero.top - gap + 1 ? 100000 : 200000;
    return preference + Math.abs(box.left - (hero.right + gap))
        + Math.abs(box.top - (hero.top - box.height() * .45f)) * 1.4
        + Math.abs(box.width() - preferredWidth) * .2;
  }

  private static int clamp(int value, int low, int high) {
    return Math.max(low, Math.min(high, value));
  }

  /** Overlapping maximal empty rectangles retain room across staggered headers and actions. */
  static ArrayList<Rect> freeSpaces(RectF safe, List<RectF> exclusions, int gap) {
    ArrayList<Rect> spaces = new ArrayList<>();
    Rect inside = new Rect((int) Math.ceil(safe.left + gap), (int) Math.ceil(safe.top + gap),
        (int) Math.floor(safe.right - gap), (int) Math.floor(safe.bottom - gap));
    if (!inside.isEmpty()) spaces.add(inside);
    for (RectF excluded : exclusions) {
      Rect blocked = new Rect((int) Math.floor(excluded.left - gap),
          (int) Math.floor(excluded.top - gap), (int) Math.ceil(excluded.right + gap),
          (int) Math.ceil(excluded.bottom + gap));
      ArrayList<Rect> next = new ArrayList<>();
      for (Rect space : spaces) {
        if (!Rect.intersects(space, blocked)) { addMaximal(next, space); continue; }
        if (space.left < blocked.left)
          addMaximal(next, new Rect(space.left, space.top, blocked.left, space.bottom));
        if (space.right > blocked.right)
          addMaximal(next, new Rect(blocked.right, space.top, space.right, space.bottom));
        if (space.top < blocked.top)
          addMaximal(next, new Rect(space.left, space.top, space.right, blocked.top));
        if (space.bottom > blocked.bottom)
          addMaximal(next, new Rect(space.left, blocked.bottom, space.right, space.bottom));
      }
      spaces = next;
    }
    return spaces;
  }

  private static void addMaximal(ArrayList<Rect> spaces, Rect candidate) {
    if (candidate.isEmpty()) return;
    for (Rect present : spaces) if (present.contains(candidate)) return;
    for (int i = spaces.size() - 1; i >= 0; i--)
      if (candidate.contains(spaces.get(i))) spaces.remove(i);
    spaces.add(candidate);
  }

  private HomeTipLayout() {}
}
