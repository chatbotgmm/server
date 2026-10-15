package org.dpdns.gmm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 현지 시간 항구 배경. 별도 프레임 루프 없이 SceneMotion의 단조 시계로만 그린다. */
final class JournalAmbient {
  static final int SOURCE_WIDTH = 1890, SOURCE_HEIGHT = 832;
  static final float HERO_X = .72f, HERO_FOOT = .766f;
  static final double TRANSITION_MILLIS = 600;

  interface Loader {
    void load(
        LocalTimeTheme.Phase phase,
        int width,
        int height,
        BooleanSupplier wanted,
        Consumer<Bitmap> ready);
  }

  final View owner;
  final MainActivity host;
  final SceneMotion motion;
  private final Loader loader;
  private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint reflection = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF target = new RectF();
  private final RectF grounding = new RectF();
  private final Path water = new Path();
  private final Runnable timeChanged = this::refresh;
  private final RadialGradient lantern =
      new RadialGradient(
          454, 210, 116, new int[] {0xffffd18a, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
  Bitmap current, incoming;
  LocalTimeTheme.Phase currentPhase, incomingPhase, requestedPhase;
  volatile int generation;
  private boolean attached, loading;
  private int width, height, requestedWidth, requestedHeight;
  private int currentWidth, currentHeight, incomingWidth, incomingHeight;
  private double fadeStarted;

  JournalAmbient(View owner, MainActivity host, SceneMotion motion) {
    this(owner, host, motion, host.art::journalScene);
  }

  JournalAmbient(View owner, MainActivity host, SceneMotion motion, Loader loader) {
    this.owner = owner;
    this.host = host;
    this.motion = motion;
    this.loader = loader;
    reflection.setStrokeWidth(1.3f);
    reflection.setStrokeCap(Paint.Cap.ROUND);
    reflection.setColor(Color.WHITE);
    // 실제 물 부분만 클립한다. 교량·성곽·말뚝·지도에는 반사광이 올라가지 않는다.
    polygon(
        710, 451, 824, 443, 838, 443, 824, 459, 822, 478, 809, 481, 799, 475, 781, 477, 767, 482,
        746, 480, 719, 473);
    polygon(
        935, 442, 1007, 435, 1010, 456, 1024, 468, 1063, 468, 1070, 445, 1132, 443, 1187, 439, 1246,
        442, 1302, 446, 1374, 449, 1375, 479, 1367, 518, 1358, 529, 1240, 529, 1227, 522, 1226, 485,
        1209, 478, 1161, 478, 1149, 487, 1147, 516, 1126, 522, 1115, 518, 1113, 487, 1100, 477,
        1038, 478, 1024, 487, 1023, 516, 1007, 524, 994, 525, 992, 504, 974, 491, 938, 489);
  }

  private void polygon(float... points) {
    water.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) water.lineTo(points[i], points[i + 1]);
    water.close();
  }

  void attach() {
    if (attached) return;
    attached = true;
    if (host.journalTime != null) host.journalTime.addListener(timeChanged);
    refresh();
  }

  void detach() {
    attached = false;
    generation++;
    loading = false;
    if (host.journalTime != null) host.journalTime.removeListener(timeChanged);
    // Canvas/드라이버가 참조할 수 있으므로 수동 recycle 대신 소유 참조만 해제한다.
    current = incoming = null;
    currentPhase = incomingPhase = requestedPhase = null;
  }

  void resize(int width, int height) {
    if (this.width == width && this.height == height) return;
    this.width = width;
    this.height = height;
    if (attached) refresh();
  }

  void refresh() {
    if (!attached) return;
    if (!host.motionResumed || !SceneMotion.enabled(host)) finishTransition();
    LocalTimeTheme.Phase phase =
        host.journalTime == null
            ? LocalTimeTheme.resolve(System.currentTimeMillis()).phase
            : host.journalTime.phase();
    int w = width > 0 ? width : host.getResources().getDisplayMetrics().widthPixels;
    int h = height > 0 ? height : host.getResources().getDisplayMetrics().heightPixels;
    if (phase == requestedPhase
        && w == requestedWidth
        && h == requestedHeight
        && (loading || current != null)) return;
    request(phase, Math.max(1, w), Math.max(1, h));
  }

  void request(LocalTimeTheme.Phase phase, int width, int height) {
    if (!attached) return;
    requestedPhase = phase;
    requestedWidth = width;
    requestedHeight = height;
    final int request = ++generation;
    // 연속 시간대 변경 중 이전 전환 대상은 더 이상 보관하지 않는다.
    incoming = null;
    incomingPhase = null;
    if (current != null
        && currentPhase == phase
        && currentWidth == width
        && currentHeight == height) {
      loading = false;
      owner.invalidate();
      return; // 새 시간대 로딩 도중 원래 시간대로 돌아오면 보관 중인 배경을 바로 재사용한다.
    }
    loading = true;
    loader.load(
        phase,
        width,
        height,
        () -> generation == request,
        bitmap -> {
          if (!attached || generation != request) return;
          loading = false;
          if (bitmap == null) return; // 오프라인/자산 오류여도 UI·기존 배경을 계속 쓴다.
          if (current == null
              || currentPhase == phase
              || !host.motionResumed
              || !SceneMotion.enabled(host)
              || motion.clock == null) {
            current = bitmap;
            currentPhase = phase;
            currentWidth = width;
            currentHeight = height;
          } else {
            incoming = bitmap;
            incomingPhase = phase;
            incomingWidth = width;
            incomingHeight = height;
            fadeStarted = motion.millis;
          }
          owner.invalidate();
        });
  }

  boolean ready() {
    return current != null;
  }

  int cachedSceneCount() {
    return (current == null ? 0 : 1) + (incoming == null ? 0 : 1);
  }

  void finishTransition() {
    if (incoming == null) return;
    current = incoming;
    currentPhase = incomingPhase;
    currentWidth = incomingWidth;
    currentHeight = incomingHeight;
    incoming = null;
    incomingPhase = null;
    owner.invalidate();
  }

  static void coverBounds(RectF out, int width, int height) {
    float scale = Math.max(width / (float) SOURCE_WIDTH, height / (float) SOURCE_HEIGHT) * 1.035f;
    float w = SOURCE_WIDTH * scale, h = SOURCE_HEIGHT * scale;
    out.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
  }

  void draw(Canvas canvas, float parallaxX, float parallaxY) {
    canvas.drawColor(JournalTheme.LEATHER);
    if (current == null) return;
    coverBounds(target, owner.getWidth(), owner.getHeight());
    target.offset(parallaxX * .25f, parallaxY * .25f);
    bitmapPaint.setAlpha(255);
    canvas.drawBitmap(current, null, target, bitmapPaint);
    if (incoming != null) {
      float progress =
          SceneMotion.enabled(host) && host.motionResumed
              ? (float) Math.max(0, Math.min(1, (motion.millis - fadeStarted) / TRANSITION_MILLIS))
              : 1;
      bitmapPaint.setAlpha(Math.round(255 * progress));
      canvas.drawBitmap(incoming, null, target, bitmapPaint);
      bitmapPaint.setAlpha(255);
      if (progress >= 1) finishTransition();
    }
    LocalTimeTheme.Phase phase = incomingPhase == null ? currentPhase : incomingPhase;
    drawEnvironment(canvas, target, phase, motion.millis / 1000d, SceneMotion.enabled(host));
  }

  private void drawEnvironment(
      Canvas canvas, RectF bounds, LocalTimeTheme.Phase phase, double seconds, boolean moving) {
    canvas.save();
    canvas.translate(bounds.left, bounds.top);
    canvas.scale(bounds.width() / SOURCE_WIDTH, bounds.height() / SOURCE_HEIGHT);
    double time = moving ? seconds : 0;
    canvas.save();
    canvas.clipPath(water);
    for (int i = 0; i < 26; i++) {
      float x = 630 + (i * 137 % 770), y = 449 + (i * 31 % 94);
      double wave = time * .55 + i * 2.3;
      float drift = moving ? (float) Math.sin(wave) * 5 : 0;
      reflection.setAlpha(
          (phase == LocalTimeTheme.Phase.NIGHT ? 5 : 9) + (int) (5 * (1 + Math.sin(wave))));
      canvas.drawLine(x - 9 + drift, y, x + 9 + drift, y, reflection);
    }
    canvas.restore();
    if (phase == LocalTimeTheme.Phase.EVENING || phase == LocalTimeTheme.Phase.NIGHT) {
      light.setShader(lantern);
      light.setAlpha(
          (phase == LocalTimeTheme.Phase.NIGHT ? 22 : 15) + (int) (3 * (1 + Math.sin(time * 1.3))));
      canvas.drawRect(338, 94, 570, 326, light);
    }
    canvas.restore();
  }

  /** 원본 캐릭터 발만 지도 위에 붙인다. 캐릭터·UI는 시간대 색 보정을 하지 않는다. */
  void ground(Canvas canvas, RectF hero, float parallaxX, float parallaxY) {
    float width = hero.width() * .58f;
    grounding.set(
        hero.centerX() - width / 2,
        hero.bottom - hero.height() * .018f,
        hero.centerX() + width / 2,
        hero.bottom + hero.height() * .035f);
    grounding.offset(parallaxX, parallaxY);
    shadow.setColor(0x3d2d2015);
    canvas.drawOval(grounding, shadow);
  }
}
