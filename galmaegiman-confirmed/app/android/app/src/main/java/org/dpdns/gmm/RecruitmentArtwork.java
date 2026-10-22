package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.Locale;
import java.util.function.Function;

/** Versioned recruitment art only. Bounds are fixed metadata, never discovered during animation. */
final class RecruitmentArtwork {
  private RecruitmentArtwork() {}

  static final class Asset {
    final String path;
    final Bitmap bitmap;
    private final int left, top, right, bottom;
    Asset(String path, Bitmap bitmap, Rect source) {
      this.path = path; this.bitmap = bitmap;
      left = source.left; top = source.top; right = source.right; bottom = source.bottom;
    }
    // Rect itself is mutable: never expose a shared metadata rectangle to a View.
    Rect sourceRect() { return new Rect(left, top, right, bottom); }
  }

  static Asset load(MainActivity host, String kind) {
    // ThemeArt.image owns its per-Activity bitmap cache; do not add another bitmap owner.
    return resolve(kind, file -> ThemeArt.of(host).image(file));
  }

  /** Loader seam permits deterministic missing/corrupt/recycled-asset fallback tests. */
  static Asset resolve(String kind, Function<String, Bitmap> loader) {
    String key = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
    Rect source = referenceSource(key);
    if (source != null) {
      String path = "recruitment/tray-" + key + "-reference-v3.png";
      Bitmap bitmap = read(loader, path);
      // Exact dimensions prevent a different revision from silently using stale crop metadata.
      if (validateSourceRect(bitmap, source) && bitmap.getWidth() == 1663 && bitmap.getHeight() == 946)
        return new Asset(path, bitmap, source);
    }
    String fallback = "recruitment/tray-" + key + "-v1.png";
    Bitmap bitmap = read(loader, fallback);
    if (bitmap != null && bitmap.isRecycled()) bitmap = null;
    return new Asset(fallback, bitmap, bitmap == null ? new Rect(0, 0, 3, 2)
        : NativeEconomy.RecruitmentTray.inkBounds(bitmap));
  }

  private static Bitmap read(Function<String, Bitmap> loader, String path) {
    try { return loader.apply(path); } catch (RuntimeException invalidAsset) { return null; }
  }

  static boolean validateSourceRect(Bitmap bitmap, Rect source) {
    return bitmap != null && !bitmap.isRecycled() && source != null
        && source.left >= 0 && source.top >= 0 && source.right > source.left && source.bottom > source.top
        && source.right <= bitmap.getWidth() && source.bottom <= bitmap.getHeight();
  }

  private static Rect referenceSource(String key) {
    // metadata.json solid_alpha_gt128_bbox; coordinates are right/bottom exclusive.
    switch (key) {
      case "low": return new Rect(59, 84, 1639, 937);
      case "mid": return new Rect(59, 83, 1637, 938);
      case "high": return new Rect(60, 86, 1640, 937);
      default: return null;
    }
  }
}
