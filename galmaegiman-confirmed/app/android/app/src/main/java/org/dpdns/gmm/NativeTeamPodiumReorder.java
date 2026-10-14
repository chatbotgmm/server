package org.dpdns.gmm;

import android.os.Handler;
import android.os.Looper;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** 고정된 단상에서 좌우로 끌어 임시 출전 순서를 바꾼다. 저장 전에는 서버에 보내지 않는다. */
final class NativeTeamPodiumReorder {
  static List<String> orderAfterMove(List<String> keys, int from, int to) {
    ArrayList<String> ordered = new ArrayList<>(keys);
    if (from < 0 || to < 0 || from >= keys.size() || to >= keys.size()) return ordered;
    String moved = ordered.remove(from);
    ordered.add(to, moved);
    return ordered;
  }

  static void bind(
      MainActivity host,
      ViewGroup parent,
      List<View> cards,
      List<String> keys,
      Consumer<List<String>> changed) {
    if (cards.size() < 2 || cards.size() != keys.size()) return;
    for (int index = 0; index < cards.size(); index++) {
      int from = index;
      View card = cards.get(index);
      Handler handler = new Handler(Looper.getMainLooper());
      card.setOnTouchListener(
          new View.OnTouchListener() {
            float startX;
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
              card.setTranslationX(0);
              card.setElevation(host.dp(1));
              card.setAlpha(1);
              parent.requestDisallowInterceptTouchEvent(false);
            }

            public boolean onTouch(View v, MotionEvent event) {
              if (host.busy) {
                reset();
                return true;
              }
              switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                  startX = event.getRawX();
                  dragging = false;
                  longPress = false;
                  parent.requestDisallowInterceptTouchEvent(true);
                  handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                  return true;
                case MotionEvent.ACTION_MOVE:
                  if (longPress) return true;
                  float dx = event.getRawX() - startX;
                  if (!dragging && Math.abs(dx) > host.dp(8)) {
                    dragging = true;
                    handler.removeCallbacks(hold);
                    card.setElevation(host.dp(8));
                    card.setAlpha(.9f);
                  }
                  if (dragging) card.setTranslationX(dx);
                  return true;
                case MotionEvent.ACTION_UP:
                  if (dragging) {
                    float middle = card.getLeft() + card.getWidth() / 2f + card.getTranslationX();
                    int to = from;
                    float closest = Float.MAX_VALUE;
                    for (int i = 0; i < cards.size(); i++) {
                      View target = cards.get(i);
                      float distance =
                          Math.abs(middle - (target.getLeft() + target.getWidth() / 2f));
                      if (distance < closest) {
                        closest = distance;
                        to = i;
                      }
                    }
                    reset();
                    if (from != to) changed.accept(orderAfterMove(keys, from, to));
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
            public void onViewAttachedToWindow(View view) {}

            public void onViewDetachedFromWindow(View view) {
              handler.removeCallbacksAndMessages(null);
            }
          });
    }
  }
}
