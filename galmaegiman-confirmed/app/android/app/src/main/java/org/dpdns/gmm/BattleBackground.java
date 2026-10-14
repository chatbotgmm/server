package org.dpdns.gmm;

import android.graphics.*;
import java.util.Set;

/** 웹 startBattle과 같은 원본 파일을 고른다. 대화용 deep.jpg와 전투용 그림을 혼동하지 않는다. */
final class BattleBackground {
  private static final Set<String> SCENES =
      Set.of(
          "attic",
          "atticbase",
          "cabin",
          "cabinbase",
          "cafe",
          "cafebase",
          "deep",
          "greenhouse",
          "greenhousebase",
          "hall",
          "harbor",
          "ink",
          "key",
          "laundry",
          "market",
          "nest",
          "sky",
          "strait");

  static String path(String scene) {
    String id = scene == null || scene.isEmpty() ? "harbor" : scene;
    if (id.equals("deep")) return "/duel/bg/deepbattleb.webp";
    return SCENES.contains(id) ? "/duel/bg/" + id + ".jpg" : null;
  }

  /** 그림이 아직 없을 때 웹 #arena의 기본 바다·모래 그라데이션을 사용한다. */
  static void fallback(Canvas canvas, Paint paint, float width, float height) {
    paint.setShader(
        new LinearGradient(
            0,
            0,
            0,
            height,
            new int[] {0xff9fd8f2, 0xffcdeefa, 0xff6fb9dd, 0xff3d8fc2, 0xffe8d6a8, 0xffd9c08a},
            new float[] {0, .38f, .382f, .47f, .472f, 1},
            Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, width, height, paint);
    paint.setShader(null);
  }
}
