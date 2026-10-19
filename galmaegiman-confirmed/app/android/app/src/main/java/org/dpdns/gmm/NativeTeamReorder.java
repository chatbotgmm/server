package org.dpdns.gmm;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** 세로 목록은 그립, 가로 단상은 카드도 끌 수 있다. 긴 누르기가 드래그를 가로채지 않는다. */
final class NativeTeamReorder {
  static final String HANDLE_TAG = "team-order-drag-handle";

  static void bind(
      MainActivity host,
      ViewGroup parent,
      List<View> cards,
      List<String> keys,
      Consumer<List<String>> save) {
    bindAxis(host, parent, cards, keys, save, false);
  }

  static void bindAxis(
      MainActivity host,
      ViewGroup parent,
      List<View> cards,
      List<String> keys,
      Consumer<List<String>> changed,
      boolean horizontal) {
    if (cards.size() < 2 || cards.size() != keys.size()) return;
    // 렌더 중 사용하는 가변 draft와 분리하여 한 제스처가 실제로 시작한 순서만 바꾼다.
    List<String> saved = new ArrayList<>(keys);
    for (int index = 0; index < cards.size(); index++) {
      int from = index;
      View card = cards.get(index);
      View handle = card.findViewWithTag(HANDLE_TAG);
      Handler handler = new Handler(Looper.getMainLooper());
      View.OnTouchListener listener =
          new View.OnTouchListener() {
            float startX, startY, elevation, alpha, scaleX, scaleY;
            boolean active, dragging, scrolling;
            int target = from, pointer = -1;
            View pressed;
            final int slop = ViewConfiguration.get(host).getScaledTouchSlop();
            final Runnable hold =
                () -> {
                  if (card.isAttachedToWindow()) lift();
                };

            void lift() {
              if (!active || scrolling || dragging || host.busy) return;
              dragging = true;
              parent.requestDisallowInterceptTouchEvent(true);
              card.setPressed(false);
              if (pressed != null) pressed.setPressed(false);
              card.setElevation(host.dp(12));
              card.setAlpha(alpha * .94f);
              card.setScaleX(scaleX * 1.025f);
              card.setScaleY(scaleY * 1.025f);
              card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }

            void reset() {
              handler.removeCallbacks(hold);
              active = dragging = scrolling = false;
              pointer = -1;
              card.setTranslationX(0);
              card.setTranslationY(0);
              card.setElevation(elevation);
              card.setAlpha(alpha);
              card.setScaleX(scaleX);
              card.setScaleY(scaleY);
              card.setPressed(false);
              if (pressed != null) pressed.setPressed(false);
              parent.requestDisallowInterceptTouchEvent(false);
              for (View other : cards) if (other != card) other.setAlpha(1);
            }

            boolean inside(float x, float y) {
              Rect area = new Rect();
              if (!parent.getGlobalVisibleRect(area)) {
                int[] location = new int[2];
                parent.getLocationOnScreen(location);
                area.set(
                    location[0],
                    location[1],
                    location[0] + parent.getWidth(),
                    location[1] + parent.getHeight());
              }
              // 경계의 손가락 떨림은 허용하되 화면/편성 영역 밖에 놓으면 취소한다.
              area.inset(-slop, -slop);
              return x >= area.left && x <= area.right && y >= area.top && y <= area.bottom;
            }

            int nearest(float delta) {
              float center =
                  horizontal
                      ? card.getLeft() + card.getWidth() / 2f + delta
                      : card.getTop() + card.getHeight() / 2f + delta;
              float distance = Float.MAX_VALUE;
              int closest = from;
              for (int i = 0; i < cards.size(); i++) {
                View other = cards.get(i);
                float otherCenter =
                    horizontal
                        ? other.getLeft() + other.getWidth() / 2f
                        : other.getTop() + other.getHeight() / 2f;
                float next = Math.abs(center - otherCenter);
                if (next < distance) {
                  closest = i;
                  distance = next;
                }
              }
              return closest;
            }

            @Override
            public boolean onTouch(View view, MotionEvent event) {
              int action = event.getActionMasked();
              if (host.busy) {
                if (active) reset();
                return true;
              }
              if (action == MotionEvent.ACTION_DOWN) {
                pressed = view;
                startX = event.getRawX();
                startY = event.getRawY();
                pointer = event.getPointerId(0);
                elevation = card.getElevation();
                alpha = card.getAlpha();
                scaleX = card.getScaleX();
                scaleY = card.getScaleY();
                active = true;
                dragging = scrolling = false;
                target = from;
                view.setPressed(true);
                parent.requestDisallowInterceptTouchEvent(true);
                handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                return true;
              }
              if (!active) return false;
              if (action == MotionEvent.ACTION_CANCEL
                  || action == MotionEvent.ACTION_POINTER_DOWN
                  || (action == MotionEvent.ACTION_POINTER_UP
                      && event.getPointerId(event.getActionIndex()) == pointer)) {
                reset();
                return true;
              }
              if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                float along = horizontal ? dx : dy, across = horizontal ? dy : dx;
                if (!dragging && !scrolling) {
                  if (Math.abs(across) > slop && Math.abs(across) > Math.abs(along)) {
                    scrolling = true;
                    handler.removeCallbacks(hold);
                    view.setPressed(false);
                    parent.requestDisallowInterceptTouchEvent(false);
                  } else if (Math.abs(along) > slop) {
                    lift();
                  }
                }
                if (dragging) {
                  if (horizontal) card.setTranslationX(along);
                  else card.setTranslationY(along);
                  int next = nearest(along);
                  if (next != target) {
                    target = next;
                    card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                  }
                  for (int i = 0; i < cards.size(); i++)
                    if (cards.get(i) != card) cards.get(i).setAlpha(i == target ? .72f : 1);
                }
                return true;
              }
              if (action == MotionEvent.ACTION_UP) {
                boolean moved = dragging;
                boolean tap = !dragging && !scrolling;
                int to = target;
                boolean drop = inside(event.getRawX(), event.getRawY());
                reset();
                if (moved && drop && from != to)
                  changed.accept(NativeTeamPodiumReorder.orderAfterMove(saved, from, to));
                else if (tap && drop) view.performClick();
                return true;
              }
              return true;
            }
          };
      // 세로 목록 본문은 상세 열기/스크롤을 유지한다. 가로 단상은 카드 전체도 끌 수 있다.
      if (horizontal || handle == null) card.setOnTouchListener(listener);
      if (handle != null) {
        handle.setOnTouchListener(listener);
        handle.setOnClickListener(v -> card.performLongClick());
      }
      card.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}

            public void onViewDetachedFromWindow(View v) {
              handler.removeCallbacksAndMessages(null);
              parent.requestDisallowInterceptTouchEvent(false);
            }
          });
    }
  }
}
