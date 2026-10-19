package org.dpdns.gmm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 도감의 황동 도구·청록 잉크 판화. 아이콘마다 같은 선 굵기를 쓰고 바탕은 투명하게 둔다. */
final class JournalIcons {
  private static final int INK = 0xff393b34;
  private static final int TEAL = 0xff2c606a;
  private static final int TEAL_LIGHT = 0xff779b98;
  private static final int BRASS = 0xffb18c52;
  private static final int BRASS_LIGHT = 0xffead4a0;
  private static final int PAPER = 0xfffff5dc;
  private static final int LEATHER = 0xff887057;
  private static final int SHADOW = 0x283e3428;
  private static final Map<String, String> ALIASES;

  static {
    Map<String, String> names = new LinkedHashMap<>();
    aliases(names, "map", "mode-camp", "m-party", "btn-party", "expedition");
    aliases(names, "recruitcard", "u-draw", "m-draw", "btn-draw", "draw", "recruit");
    aliases(names, "shield", "m-units", "btn-units", "mode-normal", "team", "guard");
    aliases(names, "craft", "u-emblem", "m-craft", "btn-craft", "combine");
    aliases(names, "store", "u-shop", "m-shop", "btn-shop", "shop");
    aliases(names, "ticket", "u-ticket", "currency-ticket");
    aliases(names, "snack", "u-snack", "currency-snack");
    aliases(names, "peanut", "u-peanut", "currency-peanut");
    aliases(names, "play", "btn-play");
    aliases(names, "back", "btn-back", "return");
    aliases(names, "save", "btn-save");
    aliases(names, "save-off", "btn-save-off");
    aliases(names, "cog", "u-settings", "settings", "gear");
    aliases(names, "profile", "u-profile", "m-account", "account");
    aliases(names, "story", "u-story", "book", "campaign");
    aliases(names, "attendance", "u-attend", "btn-attend", "daily");
    aliases(names, "gift", "u-gift", "reward");
    aliases(names, "pvp", "mode-pvp", "attack", "swords");
    aliases(names, "rank", "mode-rank", "trophy");
    aliases(names, "compass", "mode-adv", "adventure");
    aliases(names, "practice", "training");
    aliases(names, "bag", "u-bag", "item", "items");
    aliases(names, "feather", "escape", "quill");
    aliases(names, "skilllearn", "skill-learn", "learn-skill");
    aliases(names, "promotion", "promote", "medal");
    aliases(names, "potion", "heal", "healing");
    aliases(names, "big", "big-potion");
    aliases(names, "clear", "cleanse");
    for (String name :
        new String[] {
          "reverse", "weakguard", "focus", "neutral", "camo", "dual", "counter", "learn",
          "first", "bulwark", "firstaid", "freeswitch", "lastone", "strip", "rustle", "sharp",
          "swift", "thick", "overtime", "drain", "thorns", "pierce", "lunch"
        }) aliases(names, name);
    for (String name :
        new String[] {
          "stone", "gold", "fire", "ice", "blossom", "sea", "shadow", "star", "thread", "gravity"
        }) aliases(names, name, "attr-" + name, "type-" + name);
    for (String name : new String[] {"burn", "freeze", "sleep", "bind", "rage"})
      aliases(names, name, "status-" + name);
    ALIASES = Collections.unmodifiableMap(names);
  }

  private JournalIcons() {}

  private static void aliases(Map<String, String> map, String canonical, String... names) {
    map.put(canonical, canonical);
    for (String name : names) map.put(name, canonical);
  }

  private static String basename(String name) {
    if (name == null) return "";
    String value = name.toLowerCase(Locale.ROOT);
    int query = value.indexOf('?');
    if (query >= 0) value = value.substring(0, query);
    int slash = value.lastIndexOf('/');
    if (slash >= 0) value = value.substring(slash + 1);
    return value.replaceFirst("\\.(png|webp|jpg)$", "");
  }

  static boolean supports(String name) {
    return ALIASES.containsKey(basename(name));
  }

  /** 감사용으로 별칭까지 포함한 지원 이름을 반환한다. 바깥에서 수정할 수 없다. */
  static Set<String> names() {
    return ALIASES.keySet();
  }

  /** 호출 쪽 NativeArt가 캐시한다. 128px 원본의 밀도를 고정해 기기별 이중 확대를 피한다. */
  static Bitmap create(Context context, String name) {
    String canonical = ALIASES.get(basename(name));
    if (canonical == null) return null;
    Bitmap image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    image.setDensity(Bitmap.DENSITY_NONE);
    Artist artist = new Artist(new Canvas(image));
    artist.draw(canonical);
    return image;
  }

  private static final class Artist {
    private final Canvas canvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Artist(Canvas canvas) {
      this.canvas = canvas;
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
    }

    private void fill(int color) {
      paint.setShader(null);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(color);
    }

    private void stroke(int color, float width) {
      paint.setShader(null);
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(color);
      paint.setStrokeWidth(width);
    }

    private void line(float x1, float y1, float x2, float y2) {
      line(x1, y1, x2, y2, INK, 3.4f);
    }

    private void line(float x1, float y1, float x2, float y2, int color, float width) {
      stroke(color, width);
      canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private Path polygon(float... points) {
      Path path = new Path();
      path.moveTo(points[0], points[1]);
      for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
      path.close();
      return path;
    }

    private void body(Path path, int color) {
      canvas.save();
      canvas.translate(1.5f, 2.5f);
      fill(SHADOW);
      canvas.drawPath(path, paint);
      canvas.restore();
      fill(color);
      if (color == BRASS)
        paint.setShader(
            new LinearGradient(18, 18, 100, 112, BRASS_LIGHT, BRASS, Shader.TileMode.CLAMP));
      else if (color == TEAL)
        paint.setShader(
            new LinearGradient(24, 18, 108, 120, TEAL_LIGHT, TEAL, Shader.TileMode.CLAMP));
      canvas.drawPath(path, paint);
      stroke(INK, 3.4f);
      canvas.drawPath(path, paint);
    }

    private void hatch(Path path) {
      canvas.save();
      canvas.clipPath(path);
      for (int i = -64; i <= 160; i += 13) line(i, 128, i + 100, 28, 0x24664f30, 1.5f);
      canvas.restore();
    }

    private void rect(float left, float top, float right, float bottom, float radius, int color) {
      Path p = new Path();
      p.addRoundRect(new RectF(left, top, right, bottom), radius, radius, Path.Direction.CW);
      body(p, color);
    }

    private void oval(float left, float top, float right, float bottom, int color) {
      Path p = new Path();
      p.addOval(new RectF(left, top, right, bottom), Path.Direction.CW);
      body(p, color);
    }

    private void dot(float x, float y, float radius, int color) {
      fill(color);
      canvas.drawCircle(x, y, radius, paint);
    }

    private void arc(
        float left,
        float top,
        float right,
        float bottom,
        float start,
        float sweep,
        int color,
        float width) {
      stroke(color, width);
      canvas.drawArc(new RectF(left, top, right, bottom), start, sweep, false, paint);
    }

    private void star(float x, float y, float radius, int color) {
      Path p = new Path();
      for (int i = 0; i < 10; i++) {
        double angle = -Math.PI / 2 + Math.PI * i / 5;
        float r = i % 2 == 0 ? radius : radius * .46f;
        float px = x + (float) Math.cos(angle) * r;
        float py = y + (float) Math.sin(angle) * r;
        if (i == 0) p.moveTo(px, py);
        else p.lineTo(px, py);
      }
      p.close();
      body(p, color);
    }

    private void check(float x, float y, float scale, int color) {
      line(x, y, x + scale * .32f, y + scale * .3f, color, 6);
      line(x + scale * .32f, y + scale * .3f, x + scale, y - scale * .45f, color, 6);
    }

    private void arrow(float x1, float y1, float x2, float y2, int color) {
      float dx = x2 - x1, dy = y2 - y1;
      float length = (float) Math.hypot(dx, dy), ux = dx / length, uy = dy / length;
      line(x1, y1, x2 - ux * 9, y2 - uy * 9, INK, 11);
      line(x1, y1, x2 - ux * 9, y2 - uy * 9, color, 6);
      body(
          polygon(
              x2,
              y2,
              x2 - ux * 19 - uy * 10,
              y2 - uy * 19 + ux * 10,
              x2 - ux * 19 + uy * 10,
              y2 - uy * 19 - ux * 10),
          color);
    }

    private void draw(String name) {
      switch (name) {
        case "map":
          map();
          break;
        case "recruitcard":
          recruit();
          break;
        case "shield":
          shield();
          break;
        case "craft":
          craft();
          break;
        case "store":
          store();
          break;
        case "ticket":
          ticket();
          break;
        case "snack":
          snack();
          break;
        case "peanut":
          peanut();
          break;
        case "play":
          body(polygon(39, 20, 108, 64, 39, 108), BRASS);
          line(46, 32, 46, 92, PAPER, 3);
          break;
        case "back":
          arrow(109, 64, 21, 64, BRASS);
          break;
        case "save":
          save(false);
          break;
        case "save-off":
          save(true);
          break;
        case "cog":
          cog();
          break;
        case "profile":
          profile();
          break;
        case "story":
          book(false);
          break;
        case "attendance":
          attendance();
          break;
        case "gift":
          gift();
          break;
        case "pvp":
          swords();
          break;
        case "rank":
          trophy();
          break;
        case "compass":
          compass();
          break;
        case "practice":
          practice();
          break;
        case "bag":
          bag(false);
          break;
        case "feather":
          feather();
          break;
        case "skilllearn":
          book(true);
          break;
        case "promotion":
          promotion();
          break;
        case "potion":
          potion(false);
          break;
        case "big":
          potion(true);
          break;
        case "clear":
          clear();
          break;
        case "reverse":
          reverse();
          break;
        case "weakguard":
          shield();
          arrow(11, 39, 59, 59, TEAL);
          break;
        case "focus":
          focus();
          break;
        case "neutral":
          scales();
          break;
        case "camo":
          mask();
          break;
        case "dual":
          dual();
          break;
        case "counter":
          counter();
          break;
        case "learn":
          book(true);
          break;
        case "first":
          first();
          break;
        case "bulwark":
          bulwark();
          break;
        case "firstaid":
          bag(true);
          break;
        case "freeswitch":
          swap();
          break;
        case "lastone":
          lastone();
          break;
        case "strip":
          broom();
          break;
        case "rustle":
          rustle();
          break;
        case "sharp":
          sharp();
          break;
        case "swift":
          swift();
          break;
        case "thick":
          thick();
          break;
        case "overtime":
          hourglass();
          break;
        case "drain":
          drain();
          break;
        case "thorns":
          thorns();
          break;
        case "pierce":
          pierce();
          break;
        case "lunch":
          lunch();
          break;
        case "stone":
          stone();
          break;
        case "gold":
          gold();
          break;
        case "fire":
          flame(0xfff26b3a);
          break;
        case "ice":
          snowflake();
          break;
        case "blossom":
          flower();
          break;
        case "sea":
          waves();
          break;
        case "shadow":
          moon(0xff8a6bb8);
          break;
        case "star":
          star(64, 65, 48, 0xffffe36e);
          line(64, 30, 57, 59, PAPER, 3);
          break;
        case "thread":
          spool();
          break;
        case "gravity":
          gravity();
          break;
        case "burn":
          flame(0xffe58448);
          line(24, 108, 104, 108, INK, 4);
          break;
        case "freeze":
          crystal();
          break;
        case "sleep":
          moon(TEAL);
          star(90, 29, 12, BRASS);
          star(102, 66, 8, BRASS);
          break;
        case "bind":
          rope();
          break;
        case "rage":
          peanut();
          spark(103, 29, 0xffb7553c);
          spark(24, 84, 0xffb7553c);
          break;
        default:
          break;
      }
    }

    private void map() {
      Path p = polygon(15, 26, 46, 17, 81, 28, 111, 18, 111, 101, 81, 111, 46, 100, 15, 110);
      body(p, PAPER);
      body(polygon(46, 17, 81, 28, 81, 111, 46, 100), 0xffd7d5bd);
      line(46, 22, 46, 95, LEATHER, 2);
      line(81, 33, 81, 104, LEATHER, 2);
      Path route = new Path();
      route.moveTo(28, 87);
      route.cubicTo(88, 77, 28, 32, 96, 48);
      stroke(TEAL, 4);
      paint.setPathEffect(new android.graphics.DashPathEffect(new float[] {5, 6}, 0));
      canvas.drawPath(route, paint);
      paint.setPathEffect(null);
      dot(28, 87, 5, TEAL);
      star(96, 48, 10, BRASS);
      line(21, 33, 34, 29, BRASS, 3);
    }

    private void recruit() {
      canvas.save();
      canvas.rotate(-13, 58, 64);
      rect(21, 17, 91, 103, 5, LEATHER);
      canvas.restore();
      rect(37, 24, 108, 111, 5, PAPER);
      rect(45, 32, 100, 88, 2, 0xffd5dfd6);
      star(72, 59, 19, BRASS);
      line(49, 99, 94, 99, INK, 3);
      line(48, 39, 56, 39, PAPER, 2);
    }

    private Path shieldPath(float inset) {
      Path p = new Path();
      p.moveTo(64, 15 + inset);
      p.lineTo(108 - inset, 32 + inset);
      p.lineTo(101 - inset, 75);
      p.cubicTo(96, 94 - inset, 80, 106 - inset, 64, 115 - inset);
      p.cubicTo(48, 106 - inset, 32, 94 - inset, 27 + inset, 75);
      p.lineTo(20 + inset, 32 + inset);
      p.close();
      return p;
    }

    private void shield() {
      body(shieldPath(0), BRASS);
      body(shieldPath(8), TEAL);
      anchor(64, 68, .72f, PAPER);
    }

    private void anchor(float x, float y, float scale, int color) {
      canvas.save();
      canvas.translate(x, y);
      canvas.scale(scale, scale);
      stroke(color, 5);
      canvas.drawCircle(0, -31, 7, paint);
      line(0, -23, 0, 34, color, 5);
      line(-20, -8, 20, -8, color, 5);
      arc(-29, -15, 29, 37, 5, 170, color, 5);
      line(-29, 12, -26, 23, color, 5);
      line(29, 12, 26, 23, color, 5);
      canvas.restore();
    }

    private void craft() {
      body(polygon(33, 30, 56, 19, 73, 41, 59, 62, 35, 57), PAPER);
      body(polygon(73, 65, 95, 56, 112, 77, 97, 100, 75, 95), TEAL);
      body(polygon(16, 78, 35, 64, 56, 81, 47, 105, 23, 107), BRASS);
      arrow(69, 24, 99, 45, BRASS);
      arrow(78, 109, 63, 109, BRASS);
      arrow(14, 57, 25, 38, BRASS);
    }

    private void store() {
      rect(22, 49, 108, 110, 4, PAPER);
      rect(39, 73, 67, 110, 2, TEAL);
      rect(77, 69, 98, 89, 2, 0xffd1e1d9);
      line(87, 70, 87, 89, INK, 2);
      body(polygon(17, 45, 30, 20, 100, 20, 114, 45), BRASS);
      for (int i = 0; i < 4; i++) {
        Path p = new Path();
        float x = 17 + i * 24;
        p.moveTo(x, 44);
        p.lineTo(x + 24, 44);
        p.lineTo(x + 24, 54);
        p.quadTo(x + 12, 66, x, 54);
        p.close();
        body(p, i % 2 == 0 ? TEAL : PAPER);
      }
      line(16, 112, 114, 112, INK, 4);
    }

    private void ticket() {
      canvas.save();
      canvas.rotate(-12, 64, 64);
      Path p = new Path();
      p.moveTo(16, 40);
      p.lineTo(109, 40);
      p.lineTo(109, 53);
      p.cubicTo(94, 53, 94, 75, 109, 75);
      p.lineTo(109, 88);
      p.lineTo(16, 88);
      p.lineTo(16, 75);
      p.cubicTo(31, 75, 31, 53, 16, 53);
      p.close();
      body(p, BRASS);
      hatch(p);
      line(35, 48, 35, 55, PAPER, 2);
      line(35, 63, 35, 67, PAPER, 2);
      line(35, 75, 35, 80, PAPER, 2);
      star(67, 64, 16, PAPER);
      canvas.restore();
    }

    private void snack() {
      Path p = new Path();
      p.moveTo(38, 21);
      p.lineTo(85, 21);
      p.lineTo(100, 106);
      p.quadTo(64, 115, 27, 106);
      p.close();
      body(p, 0xffc18a5c);
      hatch(p);
      body(polygon(38, 21, 85, 21, 90, 38, 33, 38), PAPER);
      line(43, 28, 80, 28, LEATHER, 2);
      oval(42, 53, 87, 92, PAPER);
      Path fish = polygon(53, 72, 72, 60, 80, 72, 72, 82, 53, 72, 47, 62, 47, 81);
      fill(TEAL);
      canvas.drawPath(fish, paint);
      dot(73, 70, 2, PAPER);
    }

    private void peanut() {
      canvas.save();
      canvas.rotate(30, 64, 65);
      Path p = new Path();
      p.moveTo(64, 18);
      p.cubicTo(93, 16, 103, 42, 83, 63);
      p.cubicTo(105, 93, 88, 112, 65, 112);
      p.cubicTo(38, 114, 24, 93, 44, 65);
      p.cubicTo(25, 39, 37, 18, 64, 18);
      p.close();
      body(p, BRASS);
      hatch(p);
      Path seam = new Path();
      seam.moveTo(63, 24);
      seam.cubicTo(52, 44, 76, 53, 63, 69);
      seam.cubicTo(52, 85, 71, 94, 65, 105);
      stroke(LEATHER, 2.5f);
      canvas.drawPath(seam, paint);
      line(51, 33, 74, 42, LEATHER, 2);
      line(48, 84, 77, 95, LEATHER, 2);
      canvas.restore();
    }

    private void save(boolean off) {
      // 저장은 기록을 확정한 닫힌 장부로 표시한다. 종이 단면과 잠금쇠로 책을 구별한다.
      rect(29, 28, 109, 115, 6, PAPER);
      line(37, 108, 99, 108, LEATHER, 2);
      rect(21, 15, 106, 104, 6, TEAL);
      rect(21, 15, 35, 104, 4, LEATHER);
      line(42, 25, 96, 25, BRASS_LIGHT, 2);
      line(28, 27, 28, 90, BRASS, 2);
      rect(46, 36, 94, 82, 3, PAPER);
      check(54, 58, 28, TEAL);
      line(55, 72, 83, 72, BRASS, 2);
      rect(98, 51, 115, 70, 2, BRASS);
      line(107, 57, 107, 64, INK, 2);
      if (off) {
        line(20, 115, 114, 20, PAPER, 10);
        line(20, 115, 114, 20, LEATHER, 5);
      }
    }

    private void cog() {
      Path p = new Path();
      for (int i = 0; i < 48; i++) {
        double angle = Math.PI * 2 * i / 48;
        float radius = i % 6 < 3 ? 49 : 39;
        float x = 64 + (float) Math.cos(angle) * radius, y = 64 + (float) Math.sin(angle) * radius;
        if (i == 0) p.moveTo(x, y);
        else p.lineTo(x, y);
      }
      p.close();
      body(p, BRASS);
      hatch(p);
      oval(34, 34, 94, 94, TEAL);
      oval(50, 50, 78, 78, PAPER);
    }

    private void profile() {
      rect(20, 19, 108, 110, 8, PAPER);
      line(28, 29, 28, 100, BRASS, 3);
      oval(49, 35, 82, 68, TEAL);
      Path p = new Path();
      p.moveTo(38, 98);
      p.cubicTo(40, 63, 91, 63, 94, 98);
      p.close();
      body(p, TEAL);
      rect(17, 36, 25, 47, 1, BRASS);
      rect(17, 80, 25, 91, 1, BRASS);
    }

    private void book(boolean withQuill) {
      Path left = new Path();
      left.moveTo(64, 31);
      left.quadTo(39, 17, 17, 28);
      left.lineTo(17, 103);
      left.quadTo(41, 94, 64, 108);
      left.close();
      Path right = new Path();
      right.moveTo(64, 31);
      right.quadTo(90, 17, 111, 28);
      right.lineTo(111, 103);
      right.quadTo(88, 94, 64, 108);
      right.close();
      body(left, PAPER);
      body(right, 0xffe7dcc0);
      line(64, 33, 64, 105, BRASS, 4);
      for (int y = 45; y < 90; y += 14) {
        line(27, y, 52, y + 3, LEATHER, 2);
        line(77, y + 3, 100, y, LEATHER, 2);
      }
      if (withQuill) {
        canvas.save();
        canvas.translate(28, -7);
        canvas.scale(.7f, .7f, 64, 64);
        feather();
        canvas.restore();
      } else body(polygon(83, 24, 93, 24, 93, 63, 88, 57, 83, 63), TEAL);
    }

    private void attendance() {
      rect(26, 23, 106, 112, 5, LEATHER);
      rect(18, 18, 98, 107, 5, PAPER);
      rect(38, 12, 77, 32, 4, BRASS);
      line(33, 45, 82, 45, LEATHER, 3);
      check(33, 73, 40, TEAL);
      line(34, 94, 77, 94, BRASS, 2);
    }

    private void gift() {
      rect(25, 50, 106, 111, 2, PAPER);
      rect(18, 39, 112, 58, 3, TEAL);
      rect(57, 40, 74, 111, 1, BRASS);
      Path l = new Path();
      l.moveTo(64, 39);
      l.cubicTo(18, 41, 24, 3, 48, 15);
      l.close();
      body(l, BRASS);
      Path r = new Path();
      r.moveTo(64, 39);
      r.cubicTo(110, 41, 103, 3, 80, 15);
      r.close();
      body(r, BRASS);
      line(36, 71, 48, 71, BRASS, 2);
      line(83, 99, 96, 99, BRASS, 2);
    }

    private void sword(float angle, int blade) {
      canvas.save();
      canvas.rotate(angle, 64, 64);
      body(polygon(64, 11, 76, 31, 70, 83, 58, 83, 52, 31), blade);
      line(64, 23, 64, 75, PAPER, 2.5f);
      rect(43, 79, 85, 90, 3, BRASS);
      rect(58, 91, 70, 111, 2, LEATHER);
      oval(56, 108, 72, 118, BRASS);
      canvas.restore();
    }

    private void swords() {
      sword(-39, 0xffcdd9d5);
      sword(39, TEAL);
    }

    private void trophy() {
      stroke(INK, 8);
      canvas.drawOval(new RectF(14, 30, 52, 76), paint);
      canvas.drawOval(new RectF(76, 30, 114, 76), paint);
      stroke(BRASS, 4);
      canvas.drawOval(new RectF(14, 30, 52, 76), paint);
      canvas.drawOval(new RectF(76, 30, 114, 76), paint);
      Path p = new Path();
      p.moveTo(32, 18);
      p.lineTo(96, 18);
      p.lineTo(91, 61);
      p.quadTo(84, 82, 64, 85);
      p.quadTo(44, 82, 37, 61);
      p.close();
      body(p, BRASS);
      hatch(p);
      rect(57, 84, 71, 102, 2, TEAL);
      rect(40, 103, 88, 113, 2, BRASS);
      star(64, 46, 15, PAPER);
    }

    private void compass() {
      oval(20, 20, 108, 108, BRASS);
      oval(29, 29, 99, 99, PAPER);
      body(polygon(80, 37, 73, 73, 43, 91, 53, 55), TEAL);
      body(polygon(80, 37, 64, 64, 53, 55), BRASS);
      dot(64, 64, 5, INK);
      line(64, 25, 64, 34, INK, 2);
      line(96, 64, 103, 64, INK, 2);
      line(64, 96, 64, 103, INK, 2);
      line(25, 64, 33, 64, INK, 2);
    }

    private void practice() {
      rect(58, 79, 70, 111, 2, LEATHER);
      rect(36, 108, 92, 116, 2, BRASS);
      oval(21, 15, 107, 101, PAPER);
      oval(37, 31, 91, 85, TEAL);
      oval(52, 46, 76, 70, BRASS);
      arrow(106, 18, 67, 55, BRASS);
    }

    private void bag(boolean firstaid) {
      arc(41, 16, 87, 65, 180, 180, INK, 14);
      arc(41, 16, 87, 65, 180, 180, BRASS, 8);
      rect(22, 41, 106, 112, 13, firstaid ? PAPER : TEAL);
      rect(25, 43, 103, 69, 9, firstaid ? 0xffdfd6bb : BRASS);
      rect(54, 56, 73, 78, 3, BRASS_LIGHT);
      line(63, 61, 63, 71, INK, 3);
      if (firstaid) {
        fill(TEAL);
        canvas.drawPath(
            polygon(
                58, 79, 72, 79, 72, 87, 80, 87, 80, 101, 72, 101, 72, 109, 58, 109, 58, 101, 50,
                101, 50, 87, 58, 87),
            paint);
      } else {
        line(35, 86, 35, 99, PAPER, 2);
        line(94, 86, 94, 99, PAPER, 2);
      }
    }

    private void feather() {
      Path p = new Path();
      p.moveTo(23, 111);
      p.cubicTo(23, 71, 37, 22, 108, 13);
      p.cubicTo(108, 67, 87, 98, 39, 98);
      p.close();
      body(p, PAPER);
      hatch(p);
      Path spine = new Path();
      spine.moveTo(18, 117);
      spine.cubicTo(45, 86, 63, 58, 98, 25);
      stroke(INK, 5);
      canvas.drawPath(spine, paint);
      stroke(BRASS, 2.5f);
      canvas.drawPath(spine, paint);
      line(39, 92, 40, 66, TEAL, 2);
      line(56, 71, 58, 44, TEAL, 2);
      line(58, 73, 85, 71, TEAL, 2);
      line(75, 53, 96, 50, TEAL, 2);
    }

    private void promotion() {
      body(polygon(42, 73, 62, 84, 47, 117, 39, 102, 23, 104), TEAL);
      body(polygon(67, 82, 89, 72, 106, 105, 90, 103, 81, 118), TEAL);
      Path seal = new Path();
      for (int i = 0; i < 24; i++) {
        double a = -Math.PI / 2 + Math.PI * i / 12;
        float r = i % 2 == 0 ? 39 : 34;
        float x = 64 + (float) Math.cos(a) * r, y = 52 + (float) Math.sin(a) * r;
        if (i == 0) seal.moveTo(x, y);
        else seal.lineTo(x, y);
      }
      seal.close();
      body(seal, BRASS);
      star(64, 52, 24, PAPER);
    }

    private void potion(boolean big) {
      if (big) {
        canvas.save();
        canvas.scale(1.12f, 1, 64, 64);
      }
      Path p = new Path();
      p.moveTo(47, 34);
      p.lineTo(81, 34);
      p.lineTo(81, 50);
      p.cubicTo(113, 74, 107, 112, 64, 113);
      p.cubicTo(21, 112, 15, 74, 47, 50);
      p.close();
      body(p, PAPER);
      Path fluid = new Path();
      fluid.moveTo(34, 76);
      fluid.quadTo(52, 66, 71, 76);
      fluid.quadTo(86, 83, 97, 75);
      fluid.cubicTo(104, 112, 25, 112, 34, 76);
      fluid.close();
      fill(TEAL);
      canvas.drawPath(fluid, paint);
      line(41, 80, 41, 91, 0xffa9c6ba, 3);
      rect(43, 17, 85, 35, 3, BRASS);
      line(52, 23, 76, 23, PAPER, 2);
      if (big) {
        line(64, 80, 64, 99, PAPER, 4);
        line(55, 89, 73, 89, PAPER, 4);
        canvas.restore();
      }
    }

    private void clear() {
      star(63, 55, 30, BRASS);
      star(23, 88, 15, TEAL);
      star(102, 30, 13, TEAL);
      line(71, 101, 97, 77, BRASS, 4);
      line(78, 111, 110, 81, BRASS, 3);
    }

    private void reverse() {
      arc(23, 22, 106, 106, 200, 133, INK, 11);
      arc(23, 22, 106, 106, 200, 133, BRASS, 6);
      body(polygon(101, 25, 112, 60, 79, 50), BRASS);
      arc(23, 22, 106, 106, 20, 133, INK, 11);
      arc(23, 22, 106, 106, 20, 133, TEAL, 6);
      body(polygon(28, 102, 16, 70, 47, 79), TEAL);
      line(51, 53, 77, 76, INK, 3);
      line(51, 76, 77, 53, INK, 3);
    }

    private void focus() {
      Path p = new Path();
      p.moveTo(15, 64);
      p.cubicTo(37, 29, 91, 29, 113, 64);
      p.cubicTo(91, 99, 37, 99, 15, 64);
      p.close();
      body(p, PAPER);
      oval(45, 44, 83, 84, TEAL);
      dot(64, 64, 9, INK);
      dot(58, 57, 3, PAPER);
      line(64, 16, 64, 31, BRASS, 5);
      line(64, 97, 64, 112, BRASS, 5);
      line(12, 64, 29, 64, BRASS, 4);
      line(99, 64, 116, 64, BRASS, 4);
    }

    private void scales() {
      line(64, 23, 64, 107, INK, 10);
      line(64, 23, 64, 107, BRASS, 5);
      line(22, 40, 106, 40, INK, 7);
      dot(64, 30, 7, TEAL);
      for (int x : new int[] {31, 97}) {
        line(x, 42, x - 17, 78, LEATHER, 2);
        line(x, 42, x + 17, 78, LEATHER, 2);
        Path p = new Path();
        p.moveTo(x - 20, 78);
        p.lineTo(x + 20, 78);
        p.quadTo(x, 108, x - 20, 78);
        p.close();
        body(p, TEAL);
      }
      rect(42, 106, 86, 116, 2, BRASS);
    }

    private void mask() {
      Path p = new Path();
      p.moveTo(16, 39);
      p.cubicTo(51, 45, 78, 45, 112, 39);
      p.lineTo(104, 83);
      p.quadTo(83, 99, 65, 82);
      p.quadTo(44, 100, 24, 82);
      p.close();
      body(p, TEAL);
      oval(29, 54, 54, 72, PAPER);
      oval(75, 54, 100, 72, PAPER);
      line(61, 55, 58, 77, BRASS, 3);
      line(18, 44, 12, 22, LEATHER, 3);
      line(110, 45, 116, 23, LEATHER, 3);
    }

    private void dual() {
      body(polygon(44, 18, 79, 59, 44, 108, 9, 59), BRASS);
      body(polygon(85, 19, 120, 60, 85, 109, 50, 60), TEAL);
      line(85, 32, 67, 60, PAPER, 3);
      line(44, 29, 25, 59, PAPER, 3);
      line(48, 60, 72, 60, INK, 3);
    }

    private void counter() {
      Path p = new Path();
      p.moveTo(29, 102);
      p.lineTo(29, 68);
      p.cubicTo(28, 30, 93, 23, 102, 63);
      stroke(INK, 14);
      canvas.drawPath(p, paint);
      stroke(TEAL, 8);
      canvas.drawPath(p, paint);
      body(polygon(28, 113, 10, 87, 45, 87), TEAL);
      body(polygon(104, 72, 82, 50, 114, 43), BRASS);
      spark(68, 72, BRASS);
    }

    private void first() {
      oval(22, 24, 106, 108, PAPER);
      oval(55, 10, 74, 27, BRASS);
      line(64, 37, 64, 67, TEAL, 5);
      line(64, 67, 86, 77, TEAL, 5);
      for (int i = 0; i < 4; i++) {
        canvas.save();
        canvas.rotate(90 * i, 64, 66);
        line(64, 29, 64, 36, INK, 3);
        canvas.restore();
      }
      body(polygon(97, 16, 80, 49, 96, 49, 83, 75, 115, 39, 99, 39, 113, 16), BRASS);
    }

    private void bulwark() {
      body(
          polygon(
              19, 109, 19, 37, 33, 37, 33, 22, 48, 22, 48, 37, 78, 37, 78, 22, 94, 22, 94, 37, 109,
              37, 109, 109),
          TEAL);
      line(18, 109, 110, 109, INK, 5);
      line(20, 57, 106, 57, BRASS, 3);
      line(45, 58, 45, 83, BRASS, 2);
      line(83, 58, 83, 83, BRASS, 2);
      line(20, 83, 107, 83, BRASS, 2);
      body(polygon(53, 109, 53, 86, 64, 78, 75, 86, 75, 109), PAPER);
    }

    private void swap() {
      arrow(18, 41, 110, 41, BRASS);
      arrow(110, 88, 18, 88, TEAL);
      line(21, 53, 21, 68, LEATHER, 3);
      line(107, 62, 107, 76, LEATHER, 3);
    }

    private void lastone() {
      oval(50, 28, 78, 56, TEAL);
      body(polygon(54, 56, 74, 56, 83, 96, 45, 96), TEAL);
      rect(36, 99, 92, 113, 2, BRASS);
      star(64, 14, 10, BRASS);
      line(24, 65, 36, 73, BRASS, 3);
      line(104, 65, 92, 73, BRASS, 3);
    }

    private void broom() {
      canvas.save();
      canvas.rotate(33, 64, 64);
      rect(59, 12, 69, 81, 3, LEATHER);
      body(polygon(46, 73, 82, 73, 101, 111, 27, 111), BRASS);
      hatch(polygon(46, 73, 82, 73, 101, 111, 27, 111));
      rect(43, 73, 85, 85, 3, TEAL);
      line(45, 91, 38, 109, INK, 2);
      line(64, 91, 64, 109, INK, 2);
      line(83, 91, 90, 109, INK, 2);
      canvas.restore();
    }

    private void rustle() {
      body(polygon(19, 51, 40, 51, 66, 29, 66, 99, 40, 77, 19, 77), TEAL);
      arc(48, 40, 93, 89, -62, 124, INK, 4);
      arc(60, 25, 119, 104, -60, 120, BRASS, 5);
      line(22, 42, 15, 32, BRASS, 3);
      line(26, 89, 17, 100, BRASS, 3);
    }

    private void sharp() {
      canvas.save();
      canvas.rotate(35, 64, 64);
      sword(0, PAPER);
      canvas.restore();
      spark(34, 33, BRASS);
      line(84, 23, 96, 13, TEAL, 3);
    }

    private void swift() {
      Path p = new Path();
      p.moveTo(34, 99);
      p.quadTo(67, 24, 114, 22);
      p.quadTo(101, 47, 82, 58);
      p.lineTo(103, 54);
      p.quadTo(89, 74, 64, 81);
      p.lineTo(84, 79);
      p.quadTo(65, 99, 34, 99);
      p.close();
      body(p, PAPER);
      hatch(p);
      line(12, 47, 45, 47, TEAL, 4);
      line(12, 65, 34, 65, TEAL, 4);
      line(11, 83, 25, 83, TEAL, 4);
      line(44, 91, 98, 35, BRASS, 3);
    }

    private void thick() {
      canvas.save();
      canvas.translate(-8, 5);
      canvas.scale(.86f, .86f, 64, 64);
      body(shieldPath(0), LEATHER);
      canvas.restore();
      canvas.save();
      canvas.translate(12, -2);
      canvas.scale(.86f, .86f, 64, 64);
      body(shieldPath(0), BRASS);
      body(shieldPath(10), TEAL);
      canvas.restore();
      line(66, 41, 66, 91, PAPER, 4);
    }

    private void hourglass() {
      rect(33, 14, 95, 25, 3, BRASS);
      rect(33, 104, 95, 115, 3, BRASS);
      Path p = new Path();
      p.moveTo(40, 27);
      p.lineTo(88, 27);
      p.cubicTo(88, 52, 70, 55, 67, 64);
      p.cubicTo(70, 76, 88, 77, 88, 102);
      p.lineTo(40, 102);
      p.cubicTo(40, 77, 58, 76, 61, 64);
      p.cubicTo(58, 55, 40, 52, 40, 27);
      p.close();
      body(p, PAPER);
      body(polygon(45, 42, 83, 42, 64, 63), TEAL);
      body(polygon(46, 101, 64, 77, 82, 101), BRASS);
      line(64, 65, 64, 73, BRASS, 3);
    }

    private void drop(float x, float y, float scale, int color) {
      canvas.save();
      canvas.translate(x, y);
      canvas.scale(scale, scale);
      Path p = new Path();
      p.moveTo(0, -44);
      p.cubicTo(-6, -26, -34, -4, -31, 15);
      p.cubicTo(-29, 51, 30, 51, 32, 15);
      p.cubicTo(34, -4, 6, -26, 0, -44);
      p.close();
      body(p, color);
      arc(-21, -5, 18, 34, 95, 61, PAPER, 3);
      canvas.restore();
    }

    private void drain() {
      drop(70, 64, 1, TEAL);
      arrow(12, 81, 65, 81, BRASS);
    }

    private void thorns() {
      Path p = new Path();
      p.moveTo(32, 112);
      p.cubicTo(78, 99, 42, 58, 98, 17);
      stroke(INK, 13);
      canvas.drawPath(p, paint);
      stroke(TEAL, 7);
      canvas.drawPath(p, paint);
      body(polygon(42, 99, 22, 82, 57, 85), BRASS);
      body(polygon(59, 73, 87, 70, 63, 57), BRASS);
      body(polygon(72, 43, 55, 20, 86, 30), BRASS);
    }

    private void pierce() {
      for (int x : new int[] {35, 59, 83}) {
        Path p = polygon(x, 29, x + 9, 20, x + 9, 99, x, 108);
        body(p, PAPER);
      }
      arrow(13, 73, 116, 49, TEAL);
    }

    private void lunch() {
      arc(33, 14, 95, 85, 180, 180, INK, 12);
      arc(33, 14, 95, 85, 180, 180, BRASS, 7);
      body(polygon(18, 60, 110, 60, 98, 109, 31, 109), BRASS);
      hatch(polygon(18, 60, 110, 60, 98, 109, 31, 109));
      rect(18, 54, 110, 67, 3, TEAL);
      line(30, 82, 100, 82, LEATHER, 2);
      line(34, 96, 96, 96, LEATHER, 2);
      line(47, 70, 51, 106, LEATHER, 2);
      line(78, 70, 75, 106, LEATHER, 2);
      body(polygon(40, 34, 61, 30, 79, 38, 67, 48, 48, 46, 36, 54, 36, 36), PAPER);
      dot(68, 37, 2, INK);
    }

    private void stone() {
      Path p = polygon(16, 90, 25, 50, 51, 22, 89, 28, 114, 67, 104, 101, 48, 111);
      body(p, 0xffc9b48a);
      hatch(p);
      body(polygon(51, 22, 63, 63, 25, 50), 0xffe1cfac);
      body(polygon(63, 63, 114, 67, 104, 101, 48, 111), 0xffb09a76);
      line(63, 63, 48, 111);
      line(63, 63, 89, 28);
    }

    private void gold() {
      body(polygon(13, 83, 25, 61, 61, 61, 72, 83, 68, 103, 17, 103), 0xfff0b93a);
      body(polygon(60, 83, 73, 61, 108, 61, 118, 83, 113, 103, 64, 103), 0xfff0b93a);
      body(polygon(38, 48, 50, 26, 84, 26, 96, 48, 91, 68, 42, 68), 0xfff0b93a);
      line(48, 37, 79, 37, PAPER, 4);
      line(42, 51, 91, 51, LEATHER, 2);
      line(24, 85, 59, 85, PAPER, 3);
      line(73, 85, 107, 85, PAPER, 3);
    }

    private void flame(int color) {
      Path p = new Path();
      p.moveTo(64, 13);
      p.cubicTo(70, 43, 94, 43, 89, 67);
      p.lineTo(103, 48);
      p.cubicTo(126, 88, 98, 114, 65, 114);
      p.cubicTo(22, 115, 10, 78, 31, 51);
      p.quadTo(32, 74, 42, 75);
      p.cubicTo(33, 49, 62, 41, 64, 13);
      p.close();
      body(p, color);
      Path inner = new Path();
      inner.moveTo(63, 61);
      inner.cubicTo(66, 80, 88, 80, 80, 99);
      inner.cubicTo(70, 119, 44, 101, 49, 88);
      inner.quadTo(57, 74, 63, 61);
      inner.close();
      body(inner, BRASS_LIGHT);
    }

    private void snowflake() {
      for (int i = 0; i < 6; i++) {
        canvas.save();
        canvas.rotate(i * 60, 64, 64);
        line(64, 17, 64, 66, INK, 10);
        line(64, 17, 64, 66, 0xff8fdcf5, 6);
        line(50, 28, 64, 42, INK, 8);
        line(78, 28, 64, 42, INK, 8);
        line(50, 28, 64, 42, 0xff8fdcf5, 4);
        line(78, 28, 64, 42, 0xff8fdcf5, 4);
        canvas.restore();
      }
      body(polygon(64, 51, 77, 64, 64, 77, 51, 64), PAPER);
    }

    private void flower() {
      for (int i = 0; i < 5; i++) {
        canvas.save();
        canvas.rotate(i * 72, 64, 64);
        Path p = new Path();
        p.moveTo(64, 67);
        p.cubicTo(16, 40, 43, -1, 64, 26);
        p.cubicTo(85, -1, 112, 40, 64, 67);
        p.close();
        body(p, 0xfff7a3c6);
        canvas.restore();
      }
      oval(50, 50, 78, 78, BRASS);
      dot(59, 59, 2, PAPER);
      dot(68, 68, 2, PAPER);
    }

    private void waves() {
      for (int y : new int[] {37, 67, 97}) {
        Path p = new Path();
        p.moveTo(15, y);
        p.cubicTo(32, y - 24, 48, y - 24, 64, y);
        p.cubicTo(79, y + 21, 96, y + 21, 113, y);
        p.lineTo(113, y + 11);
        p.cubicTo(96, y + 32, 79, y + 32, 64, y + 11);
        p.cubicTo(48, y - 13, 32, y - 13, 15, y + 11);
        p.close();
        body(p, 0xff3d94e6);
        line(27, y - 1, 37, y - 6, PAPER, 2);
      }
    }

    private void moon(int color) {
      Path p = new Path();
      p.moveTo(91, 20);
      p.cubicTo(56, 24, 49, 81, 104, 92);
      p.cubicTo(75, 123, 21, 105, 21, 62);
      p.cubicTo(21, 30, 56, 10, 91, 20);
      p.close();
      body(p, color);
      hatch(p);
      line(39, 44, 34, 59, PAPER, 3);
    }

    private void spool() {
      body(polygon(42, 29, 85, 29, 91, 97, 37, 97), 0xffc3c6dc);
      rect(25, 17, 103, 33, 4, LEATHER);
      rect(25, 96, 103, 112, 4, LEATHER);
      for (int y = 42; y <= 85; y += 10) line(41, y, 86, y + 6, INK, 2);
      Path thread = new Path();
      thread.moveTo(90, 83);
      thread.cubicTo(120, 86, 117, 42, 104, 37);
      stroke(0xff7e7c9b, 4);
      canvas.drawPath(thread, paint);
      line(108, 40, 116, 18, INK, 3);
    }

    private void gravity() {
      oval(39, 39, 89, 89, 0xff5b63c9);
      canvas.save();
      canvas.rotate(-32, 64, 64);
      stroke(INK, 8);
      canvas.drawOval(new RectF(10, 45, 118, 83), paint);
      stroke(0xff969ad9, 4);
      canvas.drawOval(new RectF(10, 45, 118, 83), paint);
      canvas.restore();
      dot(98, 34, 7, BRASS);
      line(48, 99, 48, 114, INK, 3);
      line(64, 101, 64, 119, INK, 3);
      line(80, 99, 80, 114, INK, 3);
      line(44, 110, 48, 115, INK, 3);
      line(68, 115, 64, 119, INK, 3);
    }

    private void crystal() {
      body(polygon(65, 13, 100, 41, 108, 84, 65, 115, 22, 84, 30, 41), 0xff8fdcf5);
      body(polygon(65, 13, 78, 63, 65, 115, 49, 63), 0xffc5eefb);
      line(30, 41, 49, 63);
      line(100, 41, 78, 63);
      line(22, 84, 49, 63);
      line(108, 84, 78, 63);
      line(62, 26, 55, 54, PAPER, 3);
    }

    private void rope() {
      canvas.save();
      canvas.rotate(-25, 64, 64);
      stroke(INK, 18);
      canvas.drawOval(new RectF(38, 14, 84, 77), paint);
      canvas.drawOval(new RectF(43, 58, 90, 117), paint);
      stroke(BRASS, 11);
      canvas.drawOval(new RectF(38, 14, 84, 77), paint);
      canvas.drawOval(new RectF(43, 58, 90, 117), paint);
      for (int y = 28; y <= 95; y += 14) {
        line(37, y, 44, y + 5, LEATHER, 2);
        line(82, y, 89, y + 5, LEATHER, 2);
      }
      canvas.restore();
    }

    private void spark(float x, float y, int color) {
      body(
          polygon(
              x, y - 14, x + 4, y - 4, x + 14, y, x + 4, y + 4, x, y + 14, x - 4, y + 4, x - 14, y,
              x - 4, y - 4),
          color);
    }
  }
}
