package org.dpdns.gmm;

import android.graphics.RectF;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Local, read-only observations of inspected pixels in the existing village artwork. */
final class VillageObservationContent {
  static final float MIN_SCALE = 1.15f;
  static final float MIN_TARGET_PIXELS = 48f;

  static final class Observation {
    final String id, title, description, sceneId;
    final float sourceLeft, sourceTop, sourceRight, sourceBottom;

    private Observation(String id, String title, String description, String sceneId,
        float sourceLeft, float sourceTop, float sourceRight, float sourceBottom) {
      this.id = id;
      this.title = title;
      this.description = description;
      this.sceneId = sceneId;
      this.sourceLeft = sourceLeft;
      this.sourceTop = sourceTop;
      this.sourceRight = sourceRight;
      this.sourceBottom = sourceBottom;
    }
  }

  // Bounds are measured in each unmodified PNG, not inferred from a building's input region.
  static final List<Observation> OBSERVATIONS = Collections.unmodifiableList(Arrays.asList(
      new Observation("stone-wall-planter", "돌담 아래 화분",
          "둥근 화분에 흰 꽃과 푸른 꽃이 모여 있어요. 깨끗한 돌계단 앞을 작게 꾸미고 있어요.",
          "layered-join-patrol-approved", 445f / 1254, 823f / 1254, 640f / 1254, 1004f / 1254),
      new Observation("quay-rope", "부두의 밧줄",
          "굵은 밧줄이 나무 기둥을 감고 있어요. 기둥 사이에서는 돌 가장자리를 따라 낮게 늘어져 있어요.",
          "layered-quay", 1540f / 2169, 570f / 725, 1810f / 2169, 654f / 725),
      new Observation("house-lantern", "돌벽의 등불",
          "검은 테두리 안에서 노란 불빛이 빛나요. 돌문 옆에 걸려 길을 따뜻하게 밝히고 있어요.",
          "layered-join-market-patrol-wrap", 725f / 1254, 577f / 1254, 829f / 1254, 752f / 1254)));

  private VillageObservationContent() {}

  /** Input is in world pixels before inspection zoom/pan, as returned by inspection.toWorld(). */
  static Observation find(NativeVillageHomeView view, float worldX, float worldY) {
    if (view == null || !Float.isFinite(worldX) || !Float.isFinite(worldY)
        || view.inspection.scale() < MIN_SCALE) return null;
    RectF viewport = view.inspection.visibleWorldRect(new RectF());
    if (viewport.isEmpty() || !viewport.contains(worldX, worldY)) return null;
    RectF hit = new RectF();
    float minimum = view.h.dp(48) / view.inspection.scale();
    for (Observation observation : OBSERVATIONS) {
      if (!bounds(view, observation, hit)) continue;
      // Keep small props usable in physical pixels, including at a clipped viewport edge.
      float width = Math.min(viewport.width(), Math.max(minimum, hit.width()));
      float height = Math.min(viewport.height(), Math.max(minimum, hit.height()));
      float left = Math.max(viewport.left, Math.min(hit.centerX() - width / 2, viewport.right - width));
      float top = Math.max(viewport.top, Math.min(hit.centerY() - height / 2, viewport.bottom - height));
      hit.set(left, top, left + width, top + height);
      if (hit.contains(worldX, worldY)) return observation;
    }
    return null;
  }

  /** Visible artwork bounds in pre-inspection world pixels; false clears out. */
  static boolean bounds(NativeVillageHomeView view, Observation observation, RectF out) {
    if (out == null) throw new NullPointerException("out");
    out.setEmpty();
    if (view == null || observation == null || !view.layout.layeredWorld
        || view.inspection.scale() < MIN_SCALE) return false;
    VillageLayout.DistrictScenery scene = view.layout.districtScenery(observation.sceneId);
    if (scene == null || view.assets.get(scene.asset) == null
        || !view.districtScene.bounds(view, observation.sceneId, out)) return false;
    float left = out.left, top = out.top, width = out.width(), height = out.height();
    out.set(left + width * observation.sourceLeft, top + height * observation.sourceTop,
        left + width * observation.sourceRight, top + height * observation.sourceBottom);
    RectF viewport = view.inspection.visibleWorldRect(new RectF());
    float period = (float) (view.layout.worldUnits * view.unitPixels());
    // Rendering repeats whole panels; select the visible equivalent of the small prop itself.
    out.offset(Math.round((viewport.centerX() - out.centerX()) / period) * period, 0);
    if (viewport.isEmpty() || !out.intersect(viewport)) {
      out.setEmpty();
      return false;
    }
    return true;
  }
}
