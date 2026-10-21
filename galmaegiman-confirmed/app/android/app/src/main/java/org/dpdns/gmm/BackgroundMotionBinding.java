package org.dpdns.gmm;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Activity당 한 재생기. 실제 배경 draw 직후에만 합성하여 UI/터치 계층을 바꾸지 않는다. */
final class BackgroundMotionBinding {
  final MainActivity host;
  final BackgroundMotionOverlay effect;
  final HarborBoatMotion boat = new HarborBoatMotion();
  private final Matrix matrix = new Matrix();
  private final RectF source = new RectF(), clip = new RectF();
  private String selected = "";
  private boolean closed;

  BackgroundMotionBinding(MainActivity host) { this.host = host; effect = new BackgroundMotionOverlay(host); }

  void draw(Canvas canvas, String asset, RectF target, View owner, Runnable repaint) {
    String scene = scene(asset);
    if (closed || host.visibleDialog != null) return;
    if (scene == null) { if (!selected.isEmpty()) reset(); return; }
    int width = scene.equals("harbor") ? 1672 : scene.equals("market") ? 1867
        : scene.equals("recruitment") || scene.equals("settings") ? 1866 : 1870;
    int height = scene.equals("harbor") ? 941 : scene.equals("market") ? 842
        : scene.equals("recruitment") || scene.equals("settings") ? 843 : 841;
    effect.bindCompositeOwner(owner, repaint, () -> host.motionResumed && SceneMotion.enabled(host) && host.visibleDialog == null);
    if (!scene.equals(selected)) {
      if (!scene.equals("harbor")) boat.clear();
      selected = scene;
      effect.selectScene(scene);
      effect.setWaterMask(asset, waterMask(scene));
    }
    source.set(0, 0, width, height);
    matrix.setRectToRect(source, target, Matrix.ScaleToFit.FILL);
    // Canvas에 이미 존재하는 viewport clip을 보존한다. target과 동일 로컬 좌표다.
    clip.set(canvas.getClipBounds());
    if (clip.isEmpty()) return;
    effect.setSourceTransform(asset, width, height, matrix, clip);
    effect.setReducedMotion(!SceneMotion.enabled(host));
    effect.setHostResumed(host.motionResumed);
    effect.drawEffect(canvas);
  }

  static String scene(String asset) {
    switch (asset) {
      case "native/release-v1/backgrounds/bg-collection.png": return "collection";
      case "native/release-v1/backgrounds/bg-navigation-workshop-v1.png": return "navigation";
      case "native/release-v1/backgrounds/bg-recruitment-counter-v1.png": return "recruitment";
      case "native/release-v1/backgrounds/bg-settings-harbor-office-v1.png": return "settings";
      case "native/release-v1/backgrounds/bg-market-canopy-v1.png": return "market";
      case "native/release-v1/backgrounds/bg-home.png": return "home";
      case "native/release-v1/backgrounds/bg-team.png": return "team";
      case "battle-v1/bg-harbor.png": return "harbor";
      default: return null;
    }
  }

  // 원본 픽셀을 열어 검토한 보수적 수면 내부. 배치 bounds를 물 마스크로 쓰지 않는다.
  static Path waterMask(String scene) {
    float[] box;
    switch (scene) {
      case "navigation": box = new float[]{1018,370,1090,440}; break;
      case "market": box = new float[]{1250,410,1455,455}; break;
      case "harbor": box = new float[]{1180,400,1245,440}; break;
      case "home": box = new float[]{1280,405,1400,450}; break;
      default: return null;
    }
    Path path = new Path();
    path.addRect(box[0],box[1],box[2],box[3],Path.Direction.CW);
    return path;
  }

  void resumed(boolean resumed) { effect.setHostResumed(resumed); if (!resumed) boat.resetClock(); }
  void reset() { if (closed) return; selected = ""; effect.selectScene(""); effect.unbindCompositeOwner(); boat.clear(); }
  void trim() { effect.onTrimMemory(); boat.clear(); }
  void close() { closed = true; effect.dispose(); effect.unbindCompositeOwner(); boat.clear(); }
}
