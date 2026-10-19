package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 첫 화면 방향 선택용. 실제 APK 자산과 Native View만 사용하며 제품 화면에는 연결하지 않는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TitleConceptReviewTest {
  @Test
  public void renderSevenDistinctDirectionsOnPhoneAndTablet() throws Exception {
    java.nio.file.Path folder =
        java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput"))
            .resolve("title-concepts");
    Files.createDirectories(folder);
    String[] keys = {
      "a-hero", "b-departure", "c-collection", "d-boss", "e-chart", "f-chronicle", "g-moonlight"
    };
    for (int concept = 0; concept < keys.length; concept++) {
      TitleConceptScene scene = new TitleConceptScene(RuntimeEnvironment.getApplication(), concept);
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        scene.measure(
            View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
        scene.layout(0, 0, size[0], size[1]);
        assertTrue("시작 버튼 터치 높이", scene.enter.getHeight() >= 48);
        assertTrue("설정 터치 높이", scene.settings.getHeight() >= 48);
        assertFalse("설정과 시작 버튼 겹침", RectF.intersects(scene.enterBox, scene.settingsBox));
        assertTrue(
            "시작 버튼 안전 영역", scene.enterBox.left >= 20 && scene.enterBox.right <= size[0] - 20);
        Bitmap image = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        scene.draw(new Canvas(image));
        assertTrue("잘리거나 넘친 글자: " + scene.overflow, scene.overflow.isEmpty());
        float scale = size[0] / scene.W;
        for (RectF artwork : scene.protectedArtwork) {
          RectF pixels =
              new RectF(
                  artwork.left * scale,
                  artwork.top * scale,
                  artwork.right * scale,
                  artwork.bottom * scale);
          assertFalse(
              "새 구도의 주요 캐릭터와 시작 버튼 겹침: " + keys[concept], RectF.intersects(pixels, scene.enterBox));
        }
        try (OutputStream output =
            Files.newOutputStream(folder.resolve(keys[concept] + "-" + size[0] + ".png"))) {
          assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        image.recycle();
      }
      scene.close();
    }
  }
}

/** 큰 설명판 대신 캐릭터·타이틀·시작 행동을 주연으로 세운 일곱 방향의 정지 프레임. */
final class TitleConceptScene extends FrameLayout {
  static final int PAPER = 0xfff5efdc,
      INK = 0xff292d2a,
      WOOD = 0xff34261f,
      BRASS = 0xffb68b45,
      TEAL = 0xff245b61;
  final int concept;
  final Typeface heading, prose;
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final Map<String, Bitmap> art = new HashMap<>();
  final Map<String, Rect> alpha = new HashMap<>();
  final List<String> overflow = new ArrayList<>();
  final List<RectF> protectedArtwork = new ArrayList<>();
  final Button enter, settings;
  final RectF enterBox = new RectF(), settingsBox = new RectF();
  float W = 1000, H = 450;

  TitleConceptScene(Context context, int concept) {
    super(context);
    this.concept = concept;
    heading = Typeface.createFromAsset(context.getAssets(), "native/Jua-Regular.ttf");
    prose = Typeface.createFromAsset(context.getAssets(), "native/GowunDodum-Regular.ttf");
    setWillNotDraw(false);
    setClipChildren(false);
    // 디코딩과 투명 여백 계산은 onDraw 밖에서만 한다. 검토 화면은 네트워크에 접근하지 않는다.
    String[] paths = {
      "native/t2_logo.webp",
      "native/journal/c-home-morning.webp",
      "native/journal/c-home-evening.webp",
      "native/journal/c-home-night.webp",
      "native/journal/harbor.png",
      "game-art/thumbs/D1.jpg",
      "game-art/thumbs/T6.jpg",
      "game-art/thumbs/I9.jpg",
      "game-art/thumbs/L2.jpg",
      "game-art/thumbs/H6.jpg",
      "game-art/duel/sprites/D1_s0_front.webp",
      "game-art/duel/sprites/T6_s0_front.webp",
      "game-art/duel/sprites/I9_s0_front.webp",
      "game-art/duel/sprites/V6_front.webp",
      "game-art/duel/sprites/D1_s0_back.webp"
    };
    for (String path : paths) load(path);
    enter = new Button(context);
    enter.setText("게임 시작");
    enter.setTypeface(heading);
    enter.setTextColor(concept == 2 ? PAPER : 0xfffff6df);
    enter.setTextSize(23);
    enter.setAllCaps(false);
    enter.setMinWidth(0);
    enter.setMinimumWidth(0);
    enter.setMinHeight(0);
    enter.setMinimumHeight(0);
    enter.setPadding(28, 0, 18, 4);
    enter.setBackground(new StartPlate(concept));
    enter.setTag("title-concept-enter");
    enter.setContentDescription("게임 시작, 검토용 디자인");
    addView(enter);
    settings = new Button(context);
    settings.setText("설정");
    settings.setTypeface(heading);
    settings.setTextColor(PAPER);
    settings.setTextSize(15);
    settings.setMinWidth(0);
    settings.setMinimumWidth(0);
    settings.setMinHeight(0);
    settings.setMinimumHeight(0);
    settings.setPadding(0, 0, 0, 2);
    settings.setBackground(new SmallPlate());
    settings.setTag("title-concept-settings");
    addView(settings);
  }

  void load(String path) {
    try (InputStream input = getContext().getAssets().open(path)) {
      Bitmap b = BitmapFactory.decodeStream(input);
      if (b == null) throw new IllegalStateException(path);
      b.setDensity(Bitmap.DENSITY_NONE);
      art.put(path, b);
      if (b.hasAlpha()) {
        int left = b.getWidth(), top = b.getHeight(), right = 0, bottom = 0;
        int[] row = new int[b.getWidth()];
        for (int y = 0; y < b.getHeight(); y++) {
          b.getPixels(row, 0, row.length, 0, y, row.length, 1);
          for (int x = 0; x < row.length; x++)
            if (Color.alpha(row[x]) > 18) {
              left = Math.min(left, x);
              top = Math.min(top, y);
              right = Math.max(right, x + 1);
              bottom = Math.max(bottom, y + 1);
            }
        }
        alpha.put(
            path,
            right > left
                ? new Rect(left, top, right, bottom)
                : new Rect(0, 0, b.getWidth(), b.getHeight()));
      }
    } catch (IOException error) {
      throw new IllegalStateException(path, error);
    }
  }

  void close() {
    for (Bitmap b : art.values()) b.recycle();
    art.clear();
  }

  @Override
  protected void onMeasure(int width, int height) {
    int w = MeasureSpec.getSize(width), h = MeasureSpec.getSize(height);
    setMeasuredDimension(w, h);
    float s = w / W;
    H = h / s;
    float buttonH = Math.max(58, Math.min(76, H * .145f));
    if (concept == 0) enterBox.set(70, H - buttonH - 39, 440, H - 39);
    else if (concept == 1) enterBox.set(48, H - buttonH - 32, 339, H - 32);
    else if (concept == 2) enterBox.set(573, H - buttonH - 45, 891, H - 45);
    else if (concept == 3) enterBox.set(360, H - buttonH - 28, 660, H - 28);
    else if (concept == 4) enterBox.set(582, H - buttonH - 34, 929, H - 34);
    else if (concept == 5) enterBox.set(342, H - buttonH - 25, 658, H - 25);
    else enterBox.set(617, H - buttonH - 48, 928, H - 48);
    enterBox.left *= s;
    enterBox.right *= s;
    enterBox.top *= s;
    enterBox.bottom *= s;
    settingsBox.set(w - 78, 21, w - 26, 73);
    enter.measure(
        MeasureSpec.makeMeasureSpec(Math.round(enterBox.width()), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(Math.round(enterBox.height()), MeasureSpec.EXACTLY));
    settings.measure(
        MeasureSpec.makeMeasureSpec(52, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(52, MeasureSpec.EXACTLY));
    enter.setTextSize(Math.max(21, Math.min(30, w * .026f)));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    enter.layout(
        Math.round(enterBox.left),
        Math.round(enterBox.top),
        Math.round(enterBox.right),
        Math.round(enterBox.bottom));
    settings.layout(
        Math.round(settingsBox.left),
        Math.round(settingsBox.top),
        Math.round(settingsBox.right),
        Math.round(settingsBox.bottom));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    overflow.clear();
    protectedArtwork.clear();
    c.save();
    c.scale(getWidth() / W, getWidth() / W);
    if (concept == 0) hero(c);
    else if (concept == 1) departure(c);
    else if (concept == 2) collection(c);
    else if (concept == 3) boss(c);
    else if (concept == 4) chart(c);
    else if (concept == 5) chronicle(c);
    else moonlight(c);
    c.restore();
  }

  void reset(int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    p.setAlpha(Color.alpha(color));
    p.setShadowLayer(0, 0, 0, 0);
    p.setPathEffect(null);
  }

  void rect(Canvas c, RectF box, int color, float radius) {
    reset(color);
    c.drawRoundRect(box, radius, radius, p);
  }

  void line(Canvas c, float x, float y, float right, float bottom, int color, float width) {
    reset(color);
    p.setStrokeWidth(width);
    c.drawLine(x, y, right, bottom, p);
  }

  void gradient(Canvas c, RectF box, int from, int to, boolean vertical) {
    reset(Color.WHITE);
    p.setShader(
        new LinearGradient(
            box.left,
            box.top,
            vertical ? box.left : box.right,
            vertical ? box.bottom : box.top,
            from,
            to,
            Shader.TileMode.CLAMP));
    c.drawRect(box, p);
    p.setShader(null);
  }

  void glow(Canvas c, float x, float y, float radius, int color) {
    reset(Color.WHITE);
    p.setShader(new RadialGradient(x, y, radius, color, color & 0x00ffffff, Shader.TileMode.CLAMP));
    c.drawCircle(x, y, radius, p);
    p.setShader(null);
  }

  void cover(Canvas c, String name, RectF box) {
    Bitmap b = art.get(name);
    float s = Math.max(box.width() / b.getWidth(), box.height() / b.getHeight());
    RectF target =
        new RectF(
            box.centerX() - b.getWidth() * s / 2,
            box.centerY() - b.getHeight() * s / 2,
            box.centerX() + b.getWidth() * s / 2,
            box.centerY() + b.getHeight() * s / 2);
    reset(Color.WHITE);
    c.save();
    c.clipRect(box);
    c.drawBitmap(b, null, target, p);
    c.restore();
  }

  void sprite(Canvas c, String name, RectF box) {
    Bitmap b = art.get(name);
    Rect source = alpha.get(name);
    if (source == null) source = new Rect(0, 0, b.getWidth(), b.getHeight());
    float s = Math.min(box.width() / source.width(), box.height() / source.height());
    RectF target =
        new RectF(
            box.centerX() - source.width() * s / 2,
            box.bottom - source.height() * s,
            box.centerX() + source.width() * s / 2,
            box.bottom);
    reset(Color.WHITE);
    c.drawBitmap(b, source, target, p);
  }

  void text(Canvas c, String value, float x, float y, float size, int color, boolean display) {
    reset(color);
    p.setTypeface(display ? heading : prose);
    p.setTextSize(size);
    if (x < 18
        || x + p.measureText(value) > W - 18
        || y + p.getFontMetrics().descent > H - 8
        || y + p.getFontMetrics().ascent < 5) overflow.add(value);
    c.drawText(value, x, y, p);
  }

  void outlineText(Canvas c, String value, float x, float y, float size, int fill, int edge) {
    reset(edge);
    p.setTypeface(heading);
    p.setTextSize(size);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeWidth(6);
    c.drawText(value, x, y, p);
    text(c, value, x, y, size, fill, true);
  }

  void centerText(
      Canvas c, String value, float center, float y, float size, int color, boolean display) {
    p.setTypeface(display ? heading : prose);
    p.setTextSize(size);
    text(c, value, center - p.measureText(value) / 2, y, size, color, display);
  }

  void shadow(Canvas c, float x, float y, float w) {
    reset(0x7034261f);
    p.setShader(
        new RadialGradient(
            x,
            y,
            w,
            new int[] {0x7034261f, 0x0034261f},
            new float[] {0, 1},
            Shader.TileMode.CLAMP));
    c.save();
    c.scale(1, .23f, x, y);
    c.drawCircle(x, y, w, p);
    c.restore();
    p.setShader(null);
  }

  /** 원본 발 위치를 기준으로 넓고 약한 환경 그림자와 얇은 접촉 그림자를 분리한다. */
  void groundedBattleSprite(Canvas c, String name, RectF box, boolean onPaper) {
    Rect source = alpha.get(name);
    float scale = Math.min(box.width() / source.width(), box.height() / source.height());
    float width = source.width() * scale, height = source.height() * scale;
    RectF target =
        new RectF(
            box.centerX() - width / 2, box.bottom - height, box.centerX() + width / 2, box.bottom);
    boolean primordial = name.contains("D1_s0_front");
    boolean waterShoes = name.contains("I9_s0_front");
    float center = primordial ? .46f : .50f;
    // D1의 발과 오른쪽 청백색 이펙트는 원본의 일부이며 자르거나 다시 그리지 않는다.
    ellipseShadow(
        c,
        target.left + width * center,
        target.bottom + 1,
        width * .28f,
        height * .036f,
        onPaper ? 0x213e3323 : 0x35362d21);
    if (waterShoes) {
      ellipseShadow(
          c,
          target.centerX(),
          target.bottom,
          width * .36f,
          height * .015f,
          onPaper ? 0x313e3323 : 0x5930271e);
    } else {
      float leftFoot = primordial ? .335f : .39f;
      float rightFoot = primordial ? .585f : .64f;
      ellipseShadow(
          c,
          target.left + width * leftFoot,
          target.bottom - height * .022f,
          width * .105f,
          height * .015f,
          onPaper ? 0x403e3323 : 0x7830271e);
      ellipseShadow(
          c,
          target.left + width * rightFoot,
          target.bottom,
          width * .095f,
          height * .015f,
          onPaper ? 0x403e3323 : 0x7830271e);
    }
    sprite(c, name, box);
  }

  void ellipseShadow(Canvas c, float x, float y, float rx, float ry, int color) {
    reset(Color.WHITE);
    p.setShader(new RadialGradient(x, y, rx, color, color & 0x00ffffff, Shader.TileMode.CLAMP));
    c.save();
    c.scale(1, ry / rx, x, y);
    c.drawCircle(x, y, rx, p);
    c.restore();
    p.setShader(null);
  }

  void compass(Canvas c, float x, float y, float radius, int color) {
    reset(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.3f);
    c.drawCircle(x, y, radius, p);
    c.drawCircle(x, y, radius * .8f, p);
    for (int i = 0; i < 8; i++) {
      double angle = Math.PI * i / 4;
      line(
          c,
          x + (float) Math.cos(angle) * radius * .56f,
          y + (float) Math.sin(angle) * radius * .56f,
          x + (float) Math.cos(angle) * radius * 1.11f,
          y + (float) Math.sin(angle) * radius * 1.11f,
          color,
          1.4f);
    }
    reset(color);
    Path points = new Path();
    points.moveTo(x, y - radius * .9f);
    points.lineTo(x + radius * .14f, y);
    points.lineTo(x, y + radius * .9f);
    points.lineTo(x - radius * .14f, y);
    points.close();
    c.drawPath(points, p);
  }

  void particles(
      Canvas c, int seed, int count, float l, float top, float r, float bottom, int color) {
    Random random = new Random(seed);
    for (int i = 0; i < count; i++) {
      float x = l + random.nextFloat() * (r - l),
          y = top + random.nextFloat() * (bottom - top),
          radius = 1 + random.nextFloat() * 2.5f;
      reset(color);
      c.drawCircle(x, y, radius, p);
      if (i % 5 == 0) {
        line(c, x - 5, y, x + 5, y, color, .8f);
        line(c, x, y - 5, x, y + 5, color, .8f);
      }
    }
  }

  void hero(Canvas c) {
    cover(c, "native/journal/c-home-morning.webp", new RectF(0, 0, W, H));
    gradient(c, new RectF(0, 0, W, H), 0x48432316, 0x001e2b2d, false);
    gradient(c, new RectF(0, H * .60f, W, H), 0x00302417, 0x9b312418, true);
    glow(c, 754, H * .45f, H * .47f, 0x2bffe7b6);
    groundedBattleSprite(
        c,
        "game-art/duel/sprites/D1_s0_front.webp",
        new RectF(536, H * .12f, 970, H * .84f),
        false);
    sprite(c, "native/t2_logo.webp", new RectF(49, H * .095f, 606, H * .45f));
    outlineText(c, "작은 동료, 커다란 모험.", 80, H * .57f, 25, PAPER, 0xff483728);
    particles(c, 42, 24, 40, H * .14f, 920, H * .82f, 0xb8fff2bb);
    text(c, "수집 · 조합 · 턴제 전략", 82, H * .67f, 17, 0xffffefcd, false);
    // 광고성 수치나 문구 대신 원본 캐릭터와 로고를 가장 먼저 보게 한다.
  }

  void departure(Canvas c) {
    cover(c, "native/journal/harbor.png", new RectF(0, 0, W, H));
    gradient(c, new RectF(0, H * .47f, W, H), 0x00245b61, 0xe8143038, true);
    Path banner = new Path();
    banner.moveTo(27, 0);
    banner.lineTo(344, 0);
    banner.lineTo(344, H * .66f);
    banner.lineTo(185, H * .74f);
    banner.lineTo(27, H * .66f);
    banner.close();
    reset(0xe51b4c56);
    c.drawPath(banner, p);
    line(c, 40, 0, 40, H * .637f, BRASS, 2);
    line(c, 331, 0, 331, H * .637f, BRASS, 2);
    line(c, 40, H * .637f, 185, H * .711f, BRASS, 2);
    line(c, 331, H * .637f, 185, H * .711f, BRASS, 2);
    compass(c, 185, H * .135f, 26, 0xffdcc48f);
    text(c, "갈매미맨", 63, H * .295f, 51, PAPER, true);
    text(c, "수비대", 91, H * .447f, 65, 0xffffd982, true);
    text(c, "나만의 동료와, 출항.", 75, H * .565f, 19, PAPER, false);
    groundedBattleSprite(
        c,
        "game-art/duel/sprites/D1_s0_front.webp",
        new RectF(440, H * .12f, 854, H * .82f),
        false);
    groundedBattleSprite(
        c,
        "game-art/duel/sprites/T6_s0_front.webp",
        new RectF(323, H * .39f, 547, H * .86f),
        false);
    groundedBattleSprite(
        c,
        "game-art/duel/sprites/I9_s0_front.webp",
        new RectF(765, H * .43f, 965, H * .855f),
        false);
    particles(c, 54, 16, 363, H * .2f, 928, H * .74f, 0xa6fff6cc);
    line(c, 372, H - 29, 940, H - 29, 0x75edd5a1, 1);
    text(c, "함께라서 더 강한 모험", 537, H - 48, 20, PAPER, false);
  }

  void collection(Canvas c) {
    cover(c, "native/journal/c-home-evening.webp", new RectF(0, 0, W, H));
    rect(c, new RectF(0, 0, W, H), 0xa32c221a, 0);
    RectF book = new RectF(34, 31, 963, H - 26);
    rect(
        c,
        new RectF(book.left - 9, book.top + 4, book.right + 9, book.bottom + 11),
        0xff3b2920,
        13);
    rect(c, book, 0xffb68b45, 9);
    RectF page = new RectF(book);
    page.inset(7, 7);
    gradient(c, page, 0xfffcf6e7, 0xffeee1c2, false);
    gradient(c, new RectF(484, page.top, 521, page.bottom), 0x00b68b45, 0x48a17a45, false);
    line(c, 516, page.top + 10, 516, page.bottom - 10, 0x60967a50, 1);
    for (int i = 0; i < 3; i++) {
      line(
          c,
          book.left + 13,
          book.bottom - 8 + i * 3,
          book.right - 13,
          book.bottom - 8 + i * 3,
          0xffbfae87,
          1);
    }
    compass(c, 796, H * .41f, 138, 0x3f9c783d);
    text(c, "선장의 모험일지", 78, H * .172f, 17, 0xff87683b, false);
    text(c, "갈매미맨", 76, H * .307f, 50, INK, true);
    text(c, "수비대", 76, H * .442f, 60, TEAL, true);
    text(c, "모으고, 조합하고, 강해지는.", 78, H * .527f, 19, 0xff5d6257, false);
    float smallCard = Math.min(149, H * .28f), mainCard = Math.min(171, H * .32f);
    card(
        c,
        "game-art/thumbs/T6.jpg",
        new RectF(164 - smallCard / 2, H * .59f, 164 + smallCard / 2, H * .59f + smallCard),
        -12);
    card(
        c,
        "game-art/thumbs/I9.jpg",
        new RectF(380 - smallCard / 2, H * .59f, 380 + smallCard / 2, H * .59f + smallCard),
        12);
    card(
        c,
        "game-art/thumbs/D1.jpg",
        new RectF(272 - mainCard / 2, H * .55f, 272 + mainCard / 2, H * .55f + mainCard),
        0);
    groundedBattleSprite(
        c,
        "game-art/duel/sprites/D1_s0_front.webp",
        new RectF(564, H * .15f, 908, H * .735f),
        true);
    particles(c, 8, 16, 550, H * .12f, 902, H * .73f, 0x99b68b45);
  }

  void card(Canvas c, String path, RectF box, float angle) {
    c.save();
    c.rotate(angle, box.centerX(), box.centerY());
    RectF behind = new RectF(box);
    behind.offset(1, 5);
    rect(c, behind, 0x6034261f, 5);
    rect(c, new RectF(box.left - 4, box.top - 4, box.right + 4, box.bottom + 4), 0xfffff6de, 5);
    cover(c, path, box);
    c.restore();
  }

  void boss(Canvas c) {
    cover(c, "native/journal/c-home-night.webp", new RectF(0, 0, W, H));
    gradient(c, new RectF(0, 0, W, H), 0xa512273b, 0x4617243c, false);
    gradient(c, new RectF(0, H * .48f, W, H), 0x00101d2e, 0xe90b1a2b, true);
    glow(c, 731, H * .46f, H * .49f, 0x633b8399);
    glow(c, 338, H * .63f, H * .42f, 0x50f6cb63);
    // 캐릭터에는 색 필터나 재생성 이미지를 씌우지 않는다.
    shadow(c, 294, H * .775f, 192);
    sprite(c, "game-art/duel/sprites/V6_front.webp", new RectF(573, H * .21f, 936, H * .742f));
    sprite(c, "game-art/duel/sprites/D1_s0_back.webp", new RectF(78, H * .30f, 459, H * .77f));
    line(c, 31, 26, 837, 26, 0xffc4a15d, 1.5f);
    line(c, 31, H - 16, 968, H - 16, 0xff8c7955, 1);
    text(c, "갈매미맨 수비대", 61, H * .182f, 46, PAPER, true);
    text(c, "작은 날개로, 거대한 승부.", 64, H * .273f, 21, 0xffe6c886, false);
    // 중간의 임시 선 대신 황동 전투 문장을 사용한다.
    float emblemX = 519, emblemY = H * .51f;
    reset(0xff172b38);
    c.drawCircle(emblemX, emblemY, 29, p);
    reset(0xffc5a262);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.4f);
    c.drawCircle(emblemX, emblemY, 29, p);
    p.setStrokeWidth(.8f);
    c.drawCircle(emblemX, emblemY, 24, p);
    p.setStyle(Paint.Style.FILL);
    text(c, "VS", emblemX - 15, emblemY + 8, 24, 0xfff4dea4, true);
    particles(c, 96, 32, 69, H * .30f, 946, H * .73f, 0xadb8d6d9);
    compass(c, 933, H * .25f, 32, 0x99cab47d);
  }

  /** E: 왼쪽의 수호자와 오른쪽의 펼친 항로가 이어지는 비대칭 출항 지도. */
  void chart(Canvas c) {
    cover(c, "native/journal/c-home-morning.webp", new RectF(0, 0, W, H));
    gradient(c, new RectF(0, 0, 610, H), 0x1e39301f, 0x0039301f, false);
    Path map = new Path();
    map.moveTo(548, -8);
    map.lineTo(W, -8);
    map.lineTo(W, H + 8);
    map.lineTo(485, H + 8);
    map.lineTo(510, H * .77f);
    map.lineTo(479, H * .54f);
    map.lineTo(525, H * .28f);
    map.close();
    reset(0xffe5d4ae);
    c.drawPath(map, p);
    c.save();
    c.clipPath(map);
    gradient(c, new RectF(479, 0, W, H), 0xfff8efd7, 0xffe4d7b5, false);
    for (int i = 0; i < 8; i++) {
      float y = H * (.08f + i * .13f);
      line(c, 482, y, W, y - 16, 0x1e8a8368, .8f);
    }
    for (int i = 0; i < 7; i++) {
      float x = 507 + i * 79;
      line(c, x, 0, x - 36, H, 0x168a8368, .8f);
    }
    // 항로는 장식이며 스테이지명이나 임의 게임 수치를 넣지 않는다.
    Path coast = new Path();
    coast.moveTo(1003, H * .42f);
    coast.cubicTo(928, H * .47f, 960, H * .60f, 884, H * .61f);
    coast.cubicTo(830, H * .60f, 857, H * .74f, 776, H * .73f);
    coast.lineTo(1003, H * .88f);
    coast.close();
    reset(0x1e5a8674);
    c.drawPath(coast, p);
    reset(0x60587d70);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.4f);
    c.drawPath(coast, p);
    p.setStyle(Paint.Style.FILL);
    compass(c, 865, H * .555f, 43, 0x9b9c773d);
    Path route = new Path();
    route.moveTo(563, H * .72f);
    route.cubicTo(621, H * .58f, 691, H * .73f, 758, H * .58f);
    reset(0xff3f7473);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.5f);
    p.setPathEffect(new DashPathEffect(new float[] {5, 6}, 0));
    c.drawPath(route, p);
    p.setPathEffect(null);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(563, H * .72f, 4, p);
    compass(c, 759, H * .58f, 11, 0xff956f36);
    c.restore();
    text(c, "지도를 펼치고, 출항.", 581, H * .16f, 19, 0xff6d6c58, false);
    text(c, "갈매미맨", 579, H * .305f, 52, INK, true);
    text(c, "수비대", 579, H * .45f, 65, TEAL, true);
    RectF hero = new RectF(36, H * .10f, 493, H * .865f);
    protectedArtwork.add(hero);
    groundedBattleSprite(c, "game-art/duel/sprites/D1_s0_front.webp", hero, false);
    particles(c, 341, 17, 57, H * .19f, 439, H * .79f, 0x78f9e3b1);
  }

  /** F: units.json의 실제 조합식 L2 + H6 → D1을 원본 카드와 잉크 연결로 보여준다. */
  void chronicle(Canvas c) {
    cover(c, "native/journal/c-home-evening.webp", new RectF(0, 0, W, H));
    rect(c, new RectF(0, 0, W, H), 0xa32e2c23, 0);
    gradient(c, new RectF(0, H * .55f, W, H), 0x0034261f, 0xc832271f, true);
    line(c, 55, 24, 846, 24, 0x72d1b883, 1);
    compass(c, 500, H * .505f, H * .278f, 0x538caaa1);
    glow(c, 500, H * .49f, H * .31f, 0x35f3d39b);
    centerText(c, "갈매미맨 수비대", 500, H * .14f, 44, PAPER, true);
    centerText(c, "두 동료가 만나, 하나의 수호자로.", 500, H * .223f, 18, 0xffdfd0ae, false);
    float size = Math.min(180, H * .32f);
    RectF left = new RectF(199 - size / 2, H * .35f, 199 + size / 2, H * .35f + size);
    RectF right = new RectF(801 - size / 2, H * .35f, 801 + size / 2, H * .35f + size);
    recipeInk(c, left.right + 12, left.centerY(), 330, H * .515f);
    recipeInk(c, right.left - 12, right.centerY(), 670, H * .515f);
    card(c, "game-art/thumbs/L2.jpg", left, -6);
    card(c, "game-art/thumbs/H6.jpg", right, 6);
    centerText(c, "쌍패성왕", 199, left.bottom + 30, 20, PAPER, true);
    centerText(c, "거울떼갈매미", 801, right.bottom + 30, 20, PAPER, true);
    RectF hero = new RectF(335, H * .265f, 665, H * .72f);
    protectedArtwork.add(hero);
    groundedBattleSprite(c, "game-art/duel/sprites/D1_s0_front.webp", hero, false);
    centerText(c, "태초의 갈매미맨", 500, H * .77f, 19, 0xffe6d4a1, true);
    particles(c, 479, 22, 324, H * .29f, 677, H * .71f, 0x96e3ca8c);
  }

  void recipeInk(Canvas c, float x, float y, float toX, float toY) {
    Path link = new Path();
    float direction = toX > x ? 1 : -1;
    link.moveTo(x, y);
    link.cubicTo(x + direction * 37, y - 19, toX - direction * 29, toY + 12, toX, toY);
    reset(0xffead097);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.8f);
    p.setPathEffect(new DashPathEffect(new float[] {7, 4}, 0));
    c.drawPath(link, p);
    p.setPathEffect(null);
    p.setStyle(Paint.Style.FILL);
    Path arrow = new Path();
    arrow.moveTo(toX, toY);
    arrow.lineTo(toX - direction * 14, toY - 7);
    arrow.lineTo(toX - direction * 13, toY + 8);
    arrow.close();
    c.drawPath(arrow, p);
  }

  /** G: 어둠 속 큰 전투 원본과 오른쪽의 제목을 달빛·황동 등불로 분리한다. */
  void moonlight(Canvas c) {
    cover(c, "native/journal/c-home-night.webp", new RectF(0, 0, W, H));
    gradient(c, new RectF(0, 0, W, H), 0x11132135, 0xdd10202c, false);
    gradient(c, new RectF(0, H * .64f, W, H), 0x00101e2c, 0xc9101b25, true);
    glow(c, 139, H * .37f, H * .29f, 0x57e5ab54);
    glow(c, 437, H * .32f, H * .37f, 0x246bacc3);
    compass(c, 882, H * .24f, 23, 0x9bb89b64);
    text(c, "달빛 아래, 수비대의 약속", 640, H * .235f, 17, 0xffe5c68a, false);
    text(c, "갈매미맨", 615, H * .37f, 54, PAPER, true);
    text(c, "수비대", 615, H * .535f, 72, 0xffedcf8c, true);
    text(c, "오늘도 우리 섬을 지켜요.", 620, H * .65f, 18, 0xffd7e3de, false);
    line(c, 620, H * .695f, 923, H * .695f, 0x798ba097, 1);
    RectF hero = new RectF(39, H * .12f, 580, H * .875f);
    protectedArtwork.add(hero);
    groundedBattleSprite(c, "game-art/duel/sprites/D1_s0_front.webp", hero, false);
    particles(c, 947, 21, 66, H * .16f, 568, H * .78f, 0x88cae1dd);
  }

  static final class StartPlate extends Drawable {
    final Paint paint = new Paint(3);
    final int style;

    StartPlate(int style) {
      this.style = style;
    }

    @Override
    public void draw(Canvas c) {
      Rect b = getBounds();
      RectF r = new RectF(b.left + 2, b.top + 2, b.right - 2, b.bottom - 5);
      paint.setShader(null);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(0xff31251e);
      c.drawRoundRect(new RectF(r.left, r.top + 5, r.right, r.bottom + 5), 12, 12, paint);
      paint.setColor(style == 3 ? 0xffe2bd75 : 0xffc29b57);
      c.drawRoundRect(r, 12, 12, paint);
      r.inset(3, 3);
      int upper =
          style == 0 ? 0xffda9b44 : style == 1 ? 0xff2f7380 : style == 2 ? 0xff37616b : 0xffb96537;
      int lower =
          style == 0 ? 0xff9e592b : style == 1 ? 0xff153e49 : style == 2 ? 0xff163f4b : 0xff753e28;
      if (style == 4) {
        upper = 0xff336b6b;
        lower = 0xff194446;
      } else if (style == 5) {
        upper = 0xff8c5141;
        lower = 0xff55362c;
      } else if (style == 6) {
        upper = 0xffb38743;
        lower = 0xff735326;
      }
      paint.setShader(
          new LinearGradient(0, r.top, 0, r.bottom, upper, lower, Shader.TileMode.CLAMP));
      c.drawRoundRect(r, 9, 9, paint);
      paint.setShader(null);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.2f);
      paint.setColor(0x90fff1cc);
      RectF inner = new RectF(r);
      inner.inset(3, 3);
      c.drawRoundRect(inner, 6, 6, paint);
      paint.setStyle(Paint.Style.FILL);
      float x = r.left + 25, y = r.centerY();
      Path arrow = new Path();
      arrow.moveTo(x - 4, y - 9);
      arrow.lineTo(x + 9, y);
      arrow.lineTo(x - 4, y + 9);
      arrow.close();
      paint.setColor(0xffffefc5);
      c.drawPath(arrow, paint);
      for (float cx : new float[] {r.left + 10, r.right - 10}) {
        paint.setColor(0xffd4b16b);
        c.drawCircle(cx, r.centerY(), 2.2f, paint);
      }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  static final class SmallPlate extends Drawable {
    final Paint paint = new Paint(3);

    @Override
    public void draw(Canvas c) {
      RectF r = new RectF(getBounds());
      r.inset(1, 1);
      paint.setColor(0xd92b3434);
      c.drawRoundRect(r, 11, 11, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.5f);
      paint.setColor(0xffc3a86e);
      c.drawRoundRect(r, 11, 11, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
