package org.dpdns.gmm;

/** Android에 의존하지 않는 안내 카드 배치. 대상 버튼의 빈 옆 공간을 먼저 사용한다. */
final class NativeTutorialLayout {
  static final class Box {
    final int left, top, right, bottom;

    Box(int left, int top, int right, int bottom) {
      this.left = left;
      this.top = top;
      this.right = Math.max(left, right);
      this.bottom = Math.max(top, bottom);
    }

    int width() { return right - left; }
    int height() { return bottom - top; }
    boolean empty() { return width() == 0 || height() == 0; }
    boolean intersects(Box other) {
      return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
    }
  }

  static final class Placement {
    final Box card;
    final boolean coversTarget;

    Placement(Box card, boolean coversTarget) {
      this.card = card;
      this.coversTarget = coversTarget;
    }
  }

  static Placement place(Box safe, Box target, int desiredWidth, int desiredHeight,
      int minWidth, int minHeight, int gap) {
    if (safe.empty()) return new Placement(safe, false);
    int width = Math.max(1, Math.min(safe.width(), desiredWidth));
    int height = Math.max(1, Math.min(safe.height(), desiredHeight));
    if (target == null || target.empty() || !safe.intersects(target)) {
      return new Placement(center(safe, width, height), false);
    }
    Box clipped = new Box(Math.max(safe.left, target.left), Math.max(safe.top, target.top),
        Math.min(safe.right, target.right), Math.min(safe.bottom, target.bottom));
    Box[] regions = {
      new Box(safe.left, safe.top, safe.right, Math.max(safe.top, clipped.top - gap)),
      new Box(safe.left, Math.min(safe.bottom, clipped.bottom + gap), safe.right, safe.bottom),
      new Box(safe.left, safe.top, Math.max(safe.left, clipped.left - gap), safe.bottom),
      new Box(Math.min(safe.right, clipped.right + gap), safe.top, safe.right, safe.bottom)
    };
    Box best = null;
    double score = -1;
    for (Box region : regions) {
      if (region.empty()) continue;
      int w = Math.min(width, region.width()), h = Math.min(height, region.height());
      // 한 줄짜리 틈보다 읽을 수 있는 폭/높이를 우선한다. 부족한 본문은 카드 안에서 스크롤한다.
      double candidate = (double) w * h;
      if (w >= minWidth && h >= minHeight) candidate += (double) safe.width() * safe.height();
      if (candidate > score) {
        score = candidate;
        best = center(region, w, h);
      }
    }
    // 거의 전체 화면인 대상에는 먼저 설명을 읽고 '대상 보기'로 카드를 접는 경로를 사용한다.
    if (best == null || best.width() < minWidth || best.height() < minHeight) {
      return new Placement(center(safe, width, height), true);
    }
    return new Placement(best, false);
  }

  private static Box center(Box area, int width, int height) {
    int left = area.left + (area.width() - width) / 2;
    int top = area.top + (area.height() - height) / 2;
    return new Box(left, top, left + width, top + height);
  }

  private NativeTutorialLayout() {}
}
