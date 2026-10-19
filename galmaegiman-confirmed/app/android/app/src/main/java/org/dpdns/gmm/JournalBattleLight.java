package org.dpdns.gmm;

import android.graphics.*;
import java.util.Set;

/** 장소 그림 위·캐릭터와 UI 아래의 환경광만 바꾼다. 별도 프레임 루프는 만들지 않는다. */
final class JournalBattleLight {
  private static final Set<String> OUTDOORS = Set.of("harbor", "market", "strait", "sky");
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Shader[] lights = new Shader[4];
  private final RectF area = new RectF();

  void resize(int width, int height) {
    area.set(0, 0, width, height);
    int[] colors = {0x20ffc078, 0x00000000, 0x32dd8547, 0x40233860};
    for (int i = 0; i < lights.length; i++)
      lights[i] =
          new LinearGradient(
              0,
              0,
              0,
              Math.max(1, height),
              colors[i],
              colors[i] & 0x00ffffff,
              Shader.TileMode.CLAMP);
  }

  void draw(Canvas canvas, String scene, LocalTimeTheme.Phase phase) {
    if (!OUTDOORS.contains(scene) || phase == LocalTimeTheme.Phase.DAY) return;
    Shader shader = lights[phase.ordinal()];
    if (shader == null) return;
    paint.setShader(shader);
    canvas.drawRect(area, paint);
    paint.setShader(null);
  }
}
