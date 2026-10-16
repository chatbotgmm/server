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
