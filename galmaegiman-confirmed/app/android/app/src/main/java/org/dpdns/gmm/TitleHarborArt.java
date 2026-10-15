package org.dpdns.gmm;

import android.graphics.*;

/** G 장면의 명암과 접지. 원본 캐릭터·UI 색을 바꾸지 않는 배경 레이어다. */
final class TitleHarborArt {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix matrix = new Matrix();
  private final LinearGradient contrast =
      new LinearGradient(
          0,
          0,
          1,
          0,
          new int[] {0x0010202c, 0x5510202c, 0xd910202c, 0xf410202c},
          new float[] {0, .44f, .62f, 1},
          Shader.TileMode.CLAMP);
  private final LinearGradient floor =
      new LinearGradient(0, 0, 0, 1, 0x00101b25, 0x9b101b25, Shader.TileMode.CLAMP);
  private final RadialGradient broad =
      new RadialGradient(0, 0, 1, 0x45362d21, 0x00362d21, Shader.TileMode.CLAMP);
  private final RadialGradient contact =
      new RadialGradient(0, 0, 1, 0x9630271e, 0x0030271e, Shader.TileMode.CLAMP);

  void shade(Canvas canvas, int width, int height) {
    paint.setColor(Color.WHITE);
    matrix.setScale(width, 1);
    contrast.setLocalMatrix(matrix);
    paint.setShader(contrast);
    canvas.drawRect(0, 0, width, height, paint);
    matrix.setScale(1, height * .36f);
    matrix.postTranslate(0, height * .64f);
    floor.setLocalMatrix(matrix);
    paint.setShader(floor);
    canvas.drawRect(0, height * .64f, width, height, paint);
    paint.setShader(null);
  }

  void ground(Canvas canvas, RectF hero) {
    oval(
        canvas,
        broad,
        hero.left + hero.width() * .46f,
        hero.bottom + 1,
        hero.width() * .28f,
        hero.height() * .036f);
    oval(
        canvas,
        contact,
        hero.left + hero.width() * .335f,
        hero.bottom - hero.height() * .022f,
        hero.width() * .105f,
        hero.height() * .015f);
    oval(
        canvas,
        contact,
        hero.left + hero.width() * .585f,
        hero.bottom,
        hero.width() * .095f,
        hero.height() * .015f);
  }

  private void oval(Canvas canvas, Shader shader, float x, float y, float rx, float ry) {
    matrix.setScale(rx, ry);
    matrix.postTranslate(x, y);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    canvas.drawOval(x - rx, y - ry, x + rx, y + ry, paint);
    paint.setShader(null);
  }
}
