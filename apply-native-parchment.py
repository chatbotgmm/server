from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parent
APP = ROOT / "galmaegiman-confirmed/app/android/app"
JAVA = APP / "src/main/java/org/dpdns/gmm"
RES = APP / "src/main/res/drawable-xxxhdpi"
ZIP = ROOT / "gmm-parchment-assets.zip"
HELPER = JAVA / "JournalParchment.java"

def require(ok, message):
    if not ok:
        raise SystemExit("STOP: " + message)

def replace_once(text, old, new, label):
    require(text.count(old) == 1, label + ": baseline block mismatch")
    return text.replace(old, new, 1)

require(ZIP.is_file(), "Missing gmm-parchment-assets.zip")
require(not HELPER.exists(), "Helper already exists; use a fresh checkout")

expected = {
    "journal_parchment_panel.9.png":
        "6780fe653ff364b1ee2633eab2e37743ae0a07e5e5911e0f023047b44ec9fd83",
    "journal_parchment_button.9.png":
        "1cc288ad100210d9ca0299171978c445dba13a22217404eea6b4ce9f8d39c238",
}
assets = {}
with zipfile.ZipFile(ZIP) as archive:
    for name, digest in expected.items():
        matches = [
            info for info in archive.infolist()
            if not info.is_dir() and Path(info.filename).name == name
        ]
        require(len(matches) == 1, "Expected one ZIP member: " + name)
        require(matches[0].file_size < 4_000_000, "Resource too large")
        data = archive.read(matches[0])
        require(hashlib.sha256(data).hexdigest() == digest,
                "Resource hash mismatch: " + name)
        require(not (RES / name).exists(), "Resource already exists: " + name)
        assets[name] = data

theme_path = JAVA / "JournalTheme.java"
art_path = JAVA / "NativeArt.java"
require(theme_path.is_file() and art_path.is_file(), "Native source missing")
theme = theme_path.read_text(encoding="utf-8")
art = art_path.read_text(encoding="utf-8")
require("JournalParchment" not in theme and "JournalParchment" not in art,
        "Source already patched")

art = replace_once(
    art,
    "            if (!closed) JournalMaterial.prepare(context);",
    """            if (!closed) {
              JournalMaterial.prepare(context);
              JournalParchment.prepare(context);
            }""",
    "NativeArt",
)

theme = replace_once(
    theme,
    """      surface(b, b.inner, fill, false);
      canvas.drawRoundRect(b.inner, innerRadius, innerRadius, p);
      p.setShader(null);
      material(canvas, b, b.inner, innerRadius, density, fill);
      if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, clipped);""",
    """      boolean parchment =
          fill == PAPER && JournalParchment.drawPanel(canvas, b.inner, density);
      if (!parchment) {
        surface(b, b.inner, fill, false);
        canvas.drawRoundRect(b.inner, innerRadius, innerRadius, p);
        p.setShader(null);
        material(canvas, b, b.inner, innerRadius, density, fill);
        if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, clipped);
      }
      p.setShader(null);""",
    "Panel",
)

theme = replace_once(
    theme,
    """    surface(b, b.inner, fill, true);
    canvas.drawRoundRect(b.inner, Math.max(1, corner - density), Math.max(1, corner - density), p);
    p.setShader(null);
    material(canvas, b, b.inner, Math.max(1, corner - density), density, fill);
    if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, false);""",
    """    boolean parchment =
        fill == PAPER && JournalParchment.drawButton(canvas, b.inner, density);
    if (!parchment) {
      surface(b, b.inner, fill, true);
      canvas.drawRoundRect(b.inner, Math.max(1, corner - density), Math.max(1, corner - density), p);
      p.setShader(null);
      material(canvas, b, b.inner, Math.max(1, corner - density), density, fill);
      if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, false);
    }
    p.setShader(null);""",
    "Button",
)

helper = """package org.dpdns.gmm;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;

final class JournalParchment {
  private static volatile Prepared prepared;
  private static final ThreadLocal<DrawState> DRAW =
      new ThreadLocal<DrawState>() {
        @Override
        protected DrawState initialValue() {
          return new DrawState();
        }
      };

  private JournalParchment() {}

  static synchronized void prepare(Context context) {
    if (prepared != null) return;
    try {
      Resources resources = context.getResources();
      Drawable panel =
          resources.getDrawable(R.drawable.journal_parchment_panel, null);
      Drawable button =
          resources.getDrawable(R.drawable.journal_parchment_button, null);
      if (!(panel instanceof NinePatchDrawable)
          || !(button instanceof NinePatchDrawable)) {
        throw new IllegalStateException("Expected compiled NinePatch resources");
      }
      Drawable.ConstantState panelState = panel.getConstantState();
      Drawable.ConstantState buttonState = button.getConstantState();
      if (panelState == null || buttonState == null) {
        throw new IllegalStateException("Missing ConstantState");
      }
      prepared = new Prepared(panelState, buttonState);
    } catch (Resources.NotFoundException | IllegalStateException failure) {
      Log.e("JournalParchment", "Using existing paper fallback", failure);
    }
  }

  static boolean ready() {
    return prepared != null;
  }

  static boolean drawPanel(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, false);
  }

  static boolean drawButton(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, true);
  }

  private static boolean draw(
      Canvas canvas, RectF bounds, float density, boolean button) {
    Prepared source = prepared;
    if (source == null || bounds == null || bounds.isEmpty()) return false;
    float scale = Math.max(.5f, density);
    if (bounds.width() < 40 * scale || bounds.height() < 32 * scale) return false;
    int left = (int) Math.ceil(bounds.left);
    int top = (int) Math.ceil(bounds.top);
    int right = (int) Math.floor(bounds.right);
    int bottom = (int) Math.floor(bounds.bottom);
    if (right <= left || bottom <= top) return false;

    DrawState state = DRAW.get();
    if (state.source != source) {
      state.panel = (NinePatchDrawable) source.panel.newDrawable().mutate();
      state.button = (NinePatchDrawable) source.button.newDrawable().mutate();
      state.source = source;
    }
    NinePatchDrawable drawable = button ? state.button : state.panel;
    drawable.setTargetDensity(Math.max(1, Math.round(160 * scale)));
    drawable.setBounds(left, top, right, bottom);
    drawable.draw(canvas);
    return true;
  }

  private static final class Prepared {
    final Drawable.ConstantState panel;
    final Drawable.ConstantState button;
    Prepared(Drawable.ConstantState panel, Drawable.ConstantState button) {
      this.panel = panel;
      this.button = button;
    }
  }

  private static final class DrawState {
    Prepared source;
    NinePatchDrawable panel;
    NinePatchDrawable button;
  }
}
"""

RES.mkdir(parents=True, exist_ok=True)
for name, data in assets.items():
    (RES / name).write_bytes(data)
HELPER.write_text(helper, encoding="utf-8")
theme_path.write_text(theme, encoding="utf-8")
art_path.write_text(art, encoding="utf-8")
print("Parchment preview generated: 2 resources, 1 helper, 2 source patches.")
print("Version, signing, application ID and non-PAPER states unchanged.")

changes = [
    (
        JAVA / "JournalTheme.java",
        "0xff58635d",
        "0xff46504a",
    ),
    (
        APP / "src/main/res/values/journal_theme.xml",
        "#58635D",
        "#46504A",
    ),
    (
        APP / "src/test/java/org/dpdns/gmm/JournalThemeTest.java",
        "0xff58635d",
        "0xff46504a",
    ),
]
pending = []
for path, old, new in changes:
    text = path.read_text(encoding="utf-8")
    pending.append((path, replace_once(text, old, new, str(path))))

test_path = APP / "src/test/java/org/dpdns/gmm/JournalMaterialTest.java"
text = test_path.read_text(encoding="utf-8")
old = "      assertTrue(JournalMaterial.ready());"
new = old + '\n      assertTrue("Parchment resources must load", JournalParchment.ready());'
pending.append((test_path, replace_once(text, old, new, "Parchment readiness")))

for path, text in pending:
    path.write_text(text, encoding="utf-8")
print("Secondary text darkened; contrast thresholds preserved; readiness check added.")
text = HELPER.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    drawable.draw(canvas);",
    """    state.base.setColor(JournalTheme.PAPER);
    canvas.drawRoundRect(bounds, 3 * scale, 3 * scale, state.base);
    drawable.setAlpha(64);
    drawable.draw(canvas);
    drawable.setAlpha(255);""",
    "Quiet parchment surface",
)
text = replace_once(
    text,
    "    NinePatchDrawable button;",
    """    NinePatchDrawable button;
    final android.graphics.Paint base =
        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);""",
    "Parchment base paint",
)
HELPER.write_text(text, encoding="utf-8")
print("Quiet paper base added; parchment texture blended; test thresholds unchanged.")
# Natural PAPER silhouette: bypass legacy rectangular rims.
HELPER.write_text(
r"""package org.dpdns.gmm;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;

final class JournalParchment {
  private static volatile Prepared prepared;
  private static final ThreadLocal<DrawState> DRAW =
      new ThreadLocal<DrawState>() {
        @Override
        protected DrawState initialValue() {
          return new DrawState();
        }
      };

  private JournalParchment() {}

  static synchronized void prepare(Context context) {
    if (prepared != null) return;
    try {
      Resources resources = context.getResources();
      Drawable panel =
          resources.getDrawable(R.drawable.journal_parchment_panel, null);
      Drawable button =
          resources.getDrawable(R.drawable.journal_parchment_button, null);
      if (!(panel instanceof NinePatchDrawable)
          || !(button instanceof NinePatchDrawable)) {
        throw new IllegalStateException("Expected compiled NinePatch resources");
      }
      Drawable.ConstantState panelState = panel.getConstantState();
      Drawable.ConstantState buttonState = button.getConstantState();
      if (panelState == null || buttonState == null) {
        throw new IllegalStateException("Missing ConstantState");
      }
      prepared = new Prepared(panelState, buttonState);
    } catch (Resources.NotFoundException | IllegalStateException failure) {
      Log.e("JournalParchment", "Using existing paper fallback", failure);
    }
  }

  static boolean ready() { return prepared != null; }

  static boolean drawPanel(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, false);
  }

  static boolean drawButton(Canvas canvas, RectF bounds, float density) {
    return draw(canvas, bounds, density, true);
  }

  private static boolean draw(
      Canvas canvas, RectF bounds, float density, boolean button) {
    Prepared source = prepared;
    if (source == null || bounds == null || bounds.isEmpty()) return false;
    float scale = Math.max(.5f, density);
    int left = (int) Math.ceil(bounds.left + scale);
    int top = (int) Math.ceil(bounds.top + scale);
    int right = (int) Math.floor(bounds.right - scale);
    int bottom = (int) Math.floor(bounds.bottom - scale);
    if (right <= left || bottom <= top) return false;
    DrawState state = DRAW.get();
    if (state.source != source) {
      state.panel = (NinePatchDrawable) source.panel.newDrawable().mutate();
      state.button = (NinePatchDrawable) source.button.newDrawable().mutate();
      state.source = source;
    }
    float width = right - left;
    float height = bottom - top;
    float insetX = Math.min(20 * scale, width * .24f);
    float insetY = Math.min(16 * scale, height * .24f);
    state.setCore(left + insetX, top + insetY,
        right - insetX, bottom - insetY, scale);
    int save = canvas.save();
    try {
      canvas.clipRect(bounds);
      state.base.setColor(JournalTheme.PAPER);
      canvas.drawRect(state.core, state.base);
      NinePatchDrawable drawable = button ? state.button : state.panel;
      drawable.setTargetDensity(Math.max(1, Math.round(160 * scale)));
      drawable.setBounds(left, top, right, bottom);
      drawable.setAlpha(255);
      drawable.draw(canvas);
      int washSave = canvas.save();
      try {
        canvas.clipRect(state.core);
        canvas.saveLayer(state.core, null);
        canvas.drawRect(state.core, state.wash);
        canvas.drawRect(state.core, state.mask);
      } finally {
        canvas.restoreToCount(washSave);
      }
    } finally {
      canvas.restoreToCount(save);
    }
    return true;
  }

  private static final class Prepared {
    final Drawable.ConstantState panel;
    final Drawable.ConstantState button;
    Prepared(Drawable.ConstantState panel, Drawable.ConstantState button) {
      this.panel = panel;
      this.button = button;
    }
  }

  private static final class DrawState {
    Prepared source;
    NinePatchDrawable panel;
    NinePatchDrawable button;
    final RectF core = new RectF();
    final Paint base = new Paint();
    final Paint wash = new Paint();
    final Paint mask = new Paint();
    float lastScale = -1;

    DrawState() {
      mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setCore(float left, float top, float right, float bottom, float scale) {
      if (core.left == left && core.top == top && core.right == right
          && core.bottom == bottom && lastScale == scale) return;
      core.set(left, top, right, bottom);
      lastScale = scale;
      float fx = Math.min(.25f, 4 * scale / core.width());
      float fy = Math.min(.25f, 4 * scale / core.height());
      int paper = JournalTheme.PAPER & 0x00ffffff;
      wash.setShader(new LinearGradient(
          core.left, 0, core.right, 0,
          new int[] {paper, 0xc0000000 | paper, 0xc0000000 | paper, paper},
          new float[] {0, fx, 1 - fx, 1}, Shader.TileMode.CLAMP));
      mask.setShader(new LinearGradient(
          0, core.top, 0, core.bottom,
          new int[] {0x00ffffff, Color.WHITE, Color.WHITE, 0x00ffffff},
          new float[] {0, fy, 1 - fy, 1}, Shader.TileMode.CLAMP));
    }
  }
}
""", encoding="utf-8")

text = theme_path.read_text(encoding="utf-8")
text = replace_once(
    text,
    """    if (bounds.isEmpty()) return;
    Brushes b = BRUSHES.get();
    Paint p = b.paint;""",
    """    if (bounds.isEmpty()) return;
    if (fill == PAPER && JournalParchment.drawPanel(canvas, bounds, density)) return;
    Brushes b = BRUSHES.get();
    Paint p = b.paint;""",
    "Natural PAPER record before legacy frame",
)
text = replace_once(
    text,
    """    float density = Math.max(.5f, radius / 16f);
    Brushes b = BRUSHES.get();""",
    """    float density = Math.max(.5f, radius / 16f);
    if (fill == PAPER && JournalParchment.drawButton(canvas, bounds, density)) {
      canvas.restoreToCount(save);
      return;
    }
    Brushes b = BRUSHES.get();""",
    "Natural PAPER button before legacy frame",
)
theme_path.write_text(text, encoding="utf-8")

theme_test = APP / "src/test/java/org/dpdns/gmm/JournalThemeTest.java"
text = theme_test.read_text(encoding="utf-8")
text = replace_once(
    text,
    '        assertTrue("목재 프레임도 실제로 표시", colorCount(bitmap, JournalTheme.WOOD) > 0);',
    '''        if (!JournalParchment.ready())
          assertTrue("리소스 미준비 시 기존 목재 fallback 유지",
              colorCount(bitmap, JournalTheme.WOOD) > 0);''',
    "Fallback status-frame assertion",
)
text = replace_once(
    text,
    '''      assertTrue("이중 프레임에 황동선이 실제 존재", nearColorCount(first, JournalTheme.BRASS) > 50);
      int topSeamPixel = (int) Math.floor(bounds.top + 3.1f * density);
      assertEquals(
          "황동선의 핵심은 픽셀 경계에 분산되지 않음 · density=" + density,
          JournalTheme.BRASS,
          first.getPixel(width / 2, topSeamPixel));''',
    '''      if (!JournalParchment.ready()) {
        assertTrue("기존 fallback의 황동선 유지",
            nearColorCount(first, JournalTheme.BRASS) > 50);
        int topSeamPixel = (int) Math.floor(bounds.top + 3.1f * density);
        assertEquals(
            "fallback 황동선 픽셀 정렬 · density=" + density,
            JournalTheme.BRASS,
            first.getPixel(width / 2, topSeamPixel));
      }''',
    "Fallback brass assertions",
)
text = replace_once(
    text,
    '''      // 위쪽 안쪽 지도선과 바깥 목재의 사이에 밝은 종이·황동이 모두 남는다.
      assertTrue("황동선 바깥에는 목재 받침도 구별되어야 함", colorCount(first, JournalTheme.WOOD) > 0);''',
    '''      if (!JournalParchment.ready())
        assertTrue("기존 fallback의 목재 받침 유지",
            colorCount(first, JournalTheme.WOOD) > 0);''',
    "Fallback wood assertion",
)
theme_test.write_text(text, encoding="utf-8")

material_test = APP / "src/test/java/org/dpdns/gmm/JournalMaterialTest.java"
text = material_test.read_text(encoding="utf-8")
require(text.rstrip().endswith("}"), "Material test closing brace missing")
text = text.rstrip()[:-1] + r"""
  @Test
  public void loadedPaperEdgesMatchTheAssetWithoutRectangularRims() throws Exception {
    try (ActivityController<MainActivity> controller = fixtures.start()) {
      MainActivity host = controller.get();
      review.await(host.art::idle);
      assertTrue("실제 NinePatch 리소스 준비", JournalParchment.ready());
      for (float density : new float[] {1, 2.25f}) {
        int width = Math.round(320 * density);
        int height = Math.round(160 * density);
        RectF bounds = new RectF(0, 0, width, height);
        int left = (int) Math.ceil(density);
        int top = (int) Math.ceil(density);
        int right = (int) Math.floor(width - density);
        int bottom = (int) Math.floor(height - density);
        RectF core = new RectF(
            left + 20 * density, top + 16 * density,
            right - 20 * density, bottom - 16 * density);
        for (boolean button : new boolean[] {false, true}) {
          Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
          Bitmap expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(actual);
          int saves = canvas.getSaveCount();
          if (button)
            JournalTheme.drawButton(
                canvas, bounds, JournalTheme.PAPER, 16 * density,
                2 * density, new Paint(3));
          else
            JournalTheme.drawPanel(canvas, bounds, density, JournalTheme.PAPER, true);
          assertEquals("자연 종이도 Canvas 상태 보존", saves, canvas.getSaveCount());
          assertEquals(new RectF(0, 0, width, height), bounds);
          android.graphics.drawable.NinePatchDrawable reference =
              (android.graphics.drawable.NinePatchDrawable)
                  host.getResources().getDrawable(
                      button ? R.drawable.journal_parchment_button
                             : R.drawable.journal_parchment_panel, null).mutate();
          reference.setTargetDensity(Math.round(160 * density));
          reference.setBounds(left, top, right, bottom);
          reference.setAlpha(255);
          reference.draw(new Canvas(expected));
          int transparent = 0, visible = 0;
          for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
              if (x >= Math.floor(core.left) - 1
                  && x <= Math.ceil(core.right) + 1
                  && y >= Math.floor(core.top) - 1
                  && y <= Math.ceil(core.bottom) + 1) continue;
              assertEquals(
                  "바깥 띠는 원본 종이 윤곽 그대로이며 목재·황동 사각선 없음",
                  expected.getPixel(x, y), actual.getPixel(x, y));
              int alpha = Color.alpha(actual.getPixel(x, y));
              if (x >= left && x < right && y >= top && y < bottom) {
                if (alpha == 0) transparent++;
                if (alpha > 0) visible++;
              }
            }
          }
          assertTrue("잘려 나간 종이 외곽은 투명", transparent > 0);
          assertTrue("자연 종이 가장자리도 실제로 보임", visible > 0);
          assertEquals("중앙 읽기 면은 완전 불투명",
              255, Color.alpha(actual.getPixel(width / 2, height / 2)));
          actual.recycle();
          expected.recycle();
        }
      }
    }
  }
}
"""
material_test.write_text(text, encoding="utf-8")
print("Natural PAPER outline applied; legacy fallback retained; loaded-edge test added.")
print("Existing contrast, opacity, quietness and control-state checks remain unchanged.")
