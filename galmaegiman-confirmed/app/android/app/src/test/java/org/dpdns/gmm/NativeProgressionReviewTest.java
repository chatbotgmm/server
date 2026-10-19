package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** 실제 native 렌더 + 숫자 검사. 그림 검토 승인과 동일하지 않으며 결과 PNG는 별도 눈검토 필요. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h374dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeProgressionReviewTest {
  final NativeShopCardBoundsTest frames = new NativeShopCardBoundsTest();
  static final String[] TABS = {"pass", "reroll", "reincarnation", "reassessment"};

  Path output() throws Exception {
    Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feature-progression");
    Files.createDirectories(path); return path;
  }

  void populate(NativeProgressionTest f) throws Exception {
    JSONObject r = f.api.response;
    JSONArray rewards = r.getJSONObject("pass").getJSONArray("milestones");
    for (int i = 2; i <= 5; i++) rewards.put(MainActivity.obj("id", "m" + i, "points", 100 * i, "credits", i, "claimable", false, "claimed", false));
    for (String kind : new String[] {"reincarnation", "reassessment"}) {
      JSONArray units = r.getJSONObject(kind).getJSONArray("candidates");
      for (int i = 3; i <= 8; i++) units.put(MainActivity.obj("key", "owned-" + i, "id", "D1", "name", "검토 대원 " + i,
          "rarity", "TOP", "locked", false, "eligible", true, "reincarnation", 2));
    }
  }

  @After public void restore() {
    RuntimeEnvironment.setFontScale(1);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
  }

  @Test public void captureFourTabsWithoutScrollAtFiveProfiles() throws Exception {
    frames.render.scheduledFramesUseThePausedLooperClock();
    JSONObject geometry = new JSONObject(), findings = new JSONObject();
    for (int[] size : new int[][] {{915,374,160}, {780,319,160}, {2048,838,420}}) {
      for (int font : size[2] == 420 ? new int[] {100} : new int[] {100,130}) {
        NativeShopCardBoundsTest.Profile p = new NativeShopCardBoundsTest.Profile(
            size[0] + "x" + size[1] + "-dpi" + size[2], size[0], size[1], size[2], font, false);
        RuntimeEnvironment.setQualifiers(p.qualifiers());
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, p.qualifiers());
        RuntimeEnvironment.setFontScale(font / 100f);
        NativeProgressionTest f = new NativeProgressionTest(); f.setup();
        try {
          populate(f); f.screen.open();
          for (String tab : TABS) {
            f.tab(tab); frames.ready(f.h, p);
            String name = p.id + "-" + tab;
            JSONArray controls = new JSONArray(), texts = new JSONArray();
            JSONObject result = MainActivity.obj("profile", MainActivity.obj("widthPx", p.w, "heightPx", p.h,
                "densityDpi", p.dpi, "fontScale", font / 100f, "insetsPx", new JSONArray().put(0).put(0).put(0).put(0)),
                "tab", tab, "actualNativeViews", true, "apiWrites", f.api.bodies.size(), "scrollContainers", 0,
                "controls", controls, "texts", texts);
            Bitmap image = Bitmap.createBitmap(p.w, p.h, Bitmap.Config.ARGB_8888);
            try {
              f.h.root.draw(new Canvas(image));
              try (OutputStream out = Files.newOutputStream(output().resolve(name + ".png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
              }
            } finally { image.recycle(); }
            int targets = 0;
            for (View view : frames.descendants(f.h.root)) {
              if (!view.isShown()) continue;
              assertFalse(name + " scroll", view.canScrollVertically(1) || view.canScrollVertically(-1));
              assertFalse(name + " horizontal scroll", view.canScrollHorizontally(1) || view.canScrollHorizontally(-1));
              assertFalse(name + " no ScrollView", view instanceof ScrollView || view instanceof HorizontalScrollView);
              if (view instanceof TextView && ((TextView) view).length() > 0) texts.put(textMetric((TextView) view, f.h));
              if (view.isClickable()) {
                targets++;
                Rect bounds = frames.bounds(view, f.h.root);
                assertTrue(name + " button outside " + view.getTag(), bounds.left >= 0 && bounds.top >= 0 && bounds.right <= p.w && bounds.bottom <= p.h);
                assertTrue(name + " 48dp height", view.getHeight() >= f.h.dp(48));
                assertTrue(name + " 48dp width", view.getWidth() >= f.h.dp(48));
                assertEquals(name + " target clipped", bounds, frames.visible(view, f.h.root));
                controls.put(MainActivity.obj("tag", view.getTag() == null ? "control-" + targets : String.valueOf(view.getTag()),
                    "boundsPx", frames.rect(bounds), "visibleBoundsPx", frames.rect(frames.visible(view, f.h.root)),
                    "minTouchTargetPx", f.h.dp(48)));
              }
            }
            result.put("targets", targets); geometry.put(name, result); findings.put(name, new JSONArray());
          }
        } finally { f.cleanup(); }
      }
    }
    Files.writeString(output().resolve("geometry.json"), geometry.toString(2));
    Files.writeString(output().resolve("findings.json"), findings.toString(2));
    assertEquals(20, geometry.length());
  }

  JSONObject textMetric(TextView view, MainActivity host) throws Exception {
    android.text.Layout layout = view.getLayout(); assertNotNull(layout);
    int count = layout.getLineCount(), ellipsis = 0;
    Rect glyphs = new Rect();
    for (int line = 0; line < count; line++) {
      ellipsis += layout.getEllipsisCount(line);
      int from = layout.getLineStart(line), to = layout.getLineEnd(line);
      String text = layout.getText().subSequence(from, to).toString().replace("\n", "");
      Rect ink = new Rect(); view.getPaint().getTextBounds(text, 0, text.length(), ink);
      ink.offset(view.getCompoundPaddingLeft() + Math.round(layout.getLineLeft(line)),
          view.getTotalPaddingTop() + layout.getLineBaseline(line));
      if (!ink.isEmpty()) glyphs.union(ink);
    }
    Rect bounds = frames.bounds(view, host.root), visible = frames.visible(view, host.root);
    Rect worldGlyphs = new Rect(glyphs); worldGlyphs.offset(bounds.left, bounds.top);
    int end = count == 0 ? 0 : layout.getLineEnd(count - 1);
    int contentHeight = view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom();
    assertEquals("native text clipped " + view.getText(), bounds, visible);
    assertEquals("native text ellipsized " + view.getText(), 0, ellipsis);
    assertEquals("native text incomplete", view.length(), end);
    assertTrue("native text height", layout.getHeight() <= contentHeight);
    assertTrue("native glyph ink " + view.getText(), visible.contains(worldGlyphs));
    return MainActivity.obj("text", view.getText().toString(), "boundsPx", frames.rect(bounds),
        "visibleBoundsPx", frames.rect(visible), "glyphBoundsPx", frames.rect(worldGlyphs),
        "ellipsisCount", ellipsis, "textLength", view.length(), "laidOutTextEnd", end,
        "layoutHeightPx", layout.getHeight(), "contentHeightPx", contentHeight);
  }

  @Test public void candidatePaginationKeepsExactKeyAndTabPage() throws Exception {
    NativeProgressionTest f = new NativeProgressionTest(); f.setup();
    try {
      populate(f); f.screen.open(); f.tab("reincarnation");
      f.button("progression-next").performClick();
      assertNull(f.h.root.findViewWithTag("progression-reincarnation-owned-1"));
      assertNotNull(f.h.root.findViewWithTag("progression-reincarnation-owned-3"));
      f.tab("pass"); f.tab("reincarnation");
      assertNotNull(f.h.root.findViewWithTag("progression-reincarnation-owned-3"));
      f.button("progression-previous").performClick();
      assertNotNull(f.h.root.findViewWithTag("progression-reincarnation-owned-1"));
      assertTrue(f.api.bodies.isEmpty());
    } finally { f.cleanup(); }
  }
}
