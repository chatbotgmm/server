package org.dpdns.gmm;

import android.content.res.AssetManager;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 제공된 매핑만 읽는다. 물 좌표 사각형을 가림 마스크로 추정하지 않는다. */
final class BackgroundMotionSpec {
  static final String ROOT = "native/background-motion-v1/";
  final String scene, file, background;
  final int width, height;
  final float opacity;
  final boolean needsWaterMask;
  final Rect[] sources = new Rect[8];
  final RectF[] destinations = new RectF[8];
  final BackgroundMotionTimeline timeline;

  static BackgroundMotionSpec read(AssetManager assets, String scene) throws IOException {
    try (InputStream in = assets.open(ROOT + "runtime.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      JSONObject manifest = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
      check(manifest.getInt("schemaVersion") == 1, "스키마");
      JSONArray entries = manifest.getJSONArray("assets");
      for (int i = 0; i < entries.length(); i++) {
        JSONObject entry = entries.getJSONObject(i);
        if (scene.equals(entry.getString("sceneId"))) return new BackgroundMotionSpec(entry);
      }
      throw new IOException("배경 효과 장면 없음: " + scene);
    } catch (JSONException | IllegalArgumentException e) { throw new IOException("배경 효과 매핑 오류", e); }
  }

  private BackgroundMotionSpec(JSONObject row) throws JSONException, IOException {
    scene = row.getString("sceneId");
    file = row.getString("file");
    check(file.matches("(indoor|outdoor)/[a-z0-9-]+\\.png"), "시트 경로");
    // navigation도 물 효과이므로 플래그가 누락되어도 안전하게 닫힌다.
    needsWaterMask = row.optBoolean("requiresWaterOcclusionMask") || row.getString("id").contains("water-");
    JSONObject original = row.getJSONObject("background");
    background = original.getString("androidAssetPath");
    check(background.matches("(native/release-v1/backgrounds|battle-v1)/[a-z0-9-]+\\.png"), "원본 경로");
    JSONArray dimensions = original.getJSONArray("dimensions");
    width = dimensions.getInt(0); height = dimensions.getInt(1);
    check(width > 0 && height > 0, "원본 크기");
    JSONArray size = row.getJSONArray("sheetSize"), cell = row.getJSONArray("frameSize");
    check(size.getInt(0) == 2048 && size.getInt(1) == 1024 && cell.getInt(0) == 512 && cell.getInt(1) == 512, "시트 크기");
    check("source-over".equals(row.getString("blend")), "블렌드");
    opacity = finite(row.getDouble("opacity"));
    check(opacity >= 0 && opacity <= 1, "불투명도");
    JSONArray frames = row.getJSONArray("frameRects"), bounds = row.getJSONArray("boundsNormalizedByFrame");
    JSONArray anchors = row.getJSONArray("frameAnchorsNormalized"), anchor = row.getJSONArray("sceneAnchorNormalized");
    JSONArray renderSize = row.getJSONArray("renderFrameSizeNormalized");
    check(frames.length() == 8 && bounds.length() == 8 && anchors.length() == 8, "프레임 개수");
    float ax = finite(anchor.getDouble(0)), ay = finite(anchor.getDouble(1));
    float rw = finite(renderSize.getDouble(0)), rh = finite(renderSize.getDouble(1));
    for (int i = 0; i < 8; i++) {
      JSONObject frame = frames.getJSONObject(i), box = bounds.getJSONObject(i);
      int x = frame.getInt("x"), y = frame.getInt("y");
      check(x == (i % 4) * 512 && y == (i / 4) * 512 && frame.getInt("width") == 512 && frame.getInt("height") == 512, "프레임 좌표");
      sources[i] = new Rect(x, y, x + 512, y + 512);
      float left = finite(box.getDouble("x")), top = finite(box.getDouble("y"));
      float w = finite(box.getDouble("width")), h = finite(box.getDouble("height"));
      JSONArray pivot = anchors.getJSONArray(i);
      float px = finite(pivot.getDouble(0)), py = finite(pivot.getDouble(1));
      check(px >= 0 && px <= 1 && py >= 0 && py <= 1, "피벗");
      check(left >= 0 && top >= 0 && w > 0 && h > 0 && left+w <= 1.00001f && top+h <= 1.00001f, "배치 범위");
      check(Math.abs(w-rw) < .00001f && Math.abs(h-rh) < .00001f
          && Math.abs(left-(ax-px*rw)) < .00001f && Math.abs(top-(ay-py*rh)) < .00001f, "배치 앵커");
      destinations[i] = new RectF(left * width, top * height, (left+w) * width, (top+h) * height);
    }
    JSONArray order = row.getJSONArray("playbackSequence");
    int[] sequence = new int[order.length()];
    for (int i = 0; i < sequence.length; i++) sequence[i] = order.getInt(i);
    long frameMs = row.getLong("frameDurationMs");
    timeline = new BackgroundMotionTimeline(sequence, frameMs, row.getLong("crossfadeMs"));
    check(row.getLong("loopDurationMs") == frameMs * sequence.length, "루프 길이");
  }

  private static float finite(double value) throws IOException {
    check(Double.isFinite(value) && Math.abs(value) <= Float.MAX_VALUE, "유한 좌표");
    return (float) value;
  }
  private static void check(boolean ok, String detail) throws IOException {
    if (!ok) throw new IOException("배경 효과 검증 실패: " + detail);
  }
}
