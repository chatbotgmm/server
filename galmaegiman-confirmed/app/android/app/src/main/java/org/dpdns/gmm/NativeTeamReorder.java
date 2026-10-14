package org.dpdns.gmm;

import android.os.Handler;
import android.os.Looper;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** 팀 칸 탭은 상세, 길게 누르기는 접근성 메뉴, 끌어서 놓기는 실제 출전 순서 저장. */
final class NativeTeamReorder {
  static void bind(
      MainActivity host,
      ViewGroup parent,
      List<View> cards,
      List<String> keys,
      Consumer<List<String>> save) {
    if (cards.size() < 2 || cards.size() != keys.size()) return;
    for (int i = 0; i < cards.size(); i++) {
      View card = cards.get(i);
      Handler handler = new Handler(Looper.getMainLooper());
      card.setOnTouchListener(
          new View.OnTouchListener() {
            float startY;
            boolean dragging, longPress;
            final Runnable hold =
                () -> {
                  if (!dragging && card.isAttachedToWindow()) {
                    longPress = true;
                    card.performLongClick();
                  }
                };

            void reset() {
              handler.removeCallbacks(hold);
              card.setTranslationY(0);
              card.setElevation(host.dp(1));
              card.setAlpha(1);
              parent.requestDisallowInterceptTouchEvent(false);
              for (View other : cards) other.setScaleX(1);
            }

            public boolean onTouch(View v, MotionEvent event) {
              if (host.busy) {
                reset();
                return true;
              }
              switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                  startY = event.getRawY();
                  dragging = false;
                  longPress = false;
                  parent.requestDisallowInterceptTouchEvent(true);
                  handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                  return true;
                case MotionEvent.ACTION_MOVE:
                  if (longPress) return true;
                  float dy = event.getRawY() - startY;
                  if (!dragging && Math.abs(dy) > host.dp(8)) {
                    dragging = true;
                    handler.removeCallbacks(hold);
                    card.setElevation(host.dp(8));
                    card.setAlpha(.92f);
                  }
                  if (dragging) {
                    card.setTranslationY(dy);
                    float center = card.getTop() + card.getHeight() / 2f + dy;
                    for (View other : cards)
                      if (other != card)
                        other.setScaleX(
                            center >= other.getTop() && center <= other.getBottom() ? 1.025f : 1);
                  }
                  return true;
                case MotionEvent.ACTION_UP:
                  if (dragging) {
                    ArrayList<Integer> order = new ArrayList<>();
                    for (int j = 0; j < cards.size(); j++) order.add(j);
                    order.sort(
                        Comparator.comparingDouble(
                            j ->
                                cards.get(j).getTop()
                                    + cards.get(j).getHeight() / 2f
                                    + cards.get(j).getTranslationY()));
                    ArrayList<String> changed = new ArrayList<>();
                    for (int j : order) changed.add(keys.get(j));
                    reset();
                    if (!changed.equals(keys)) save.accept(changed);
                  } else {
                    reset();
                    if (!longPress) card.performClick();
                  }
                  return true;
                case MotionEvent.ACTION_CANCEL:
                  reset();
                  return true;
                default:
                  return true;
              }
            }
          });
      card.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}

            public void onViewDetachedFromWindow(View v) {
              handler.removeCallbacksAndMessages(null);
            }
          });
    }
  }
}
