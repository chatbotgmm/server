package org.dpdns.gmm;

/** Keep the page fixed; move the list into a modal when 5 readable columns do not fit. */
final class FreestyleLayout {
  static boolean compact(float widthDp, float heightDp, float fontScale) {
    return widthDp < 600 || heightDp < 340 || fontScale > 1.25f;
  }
  private FreestyleLayout() {}
}
