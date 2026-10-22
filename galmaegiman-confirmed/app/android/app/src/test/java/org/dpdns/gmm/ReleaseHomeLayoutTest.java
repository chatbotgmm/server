package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import androidx.core.view.ViewCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real native home controls and layout, with isolated API responses and no account writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReleaseHomeLayoutTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  /** Keep actual listeners and team/profile flows; unrelated destination pages are not under test. */
  public static class HomeHost extends MainActivity {
    String requested = "";
    boolean followTeam;

    @Override void load(String target) {
      requested = target;
      if (followTeam && (target.equals("team") || target.equals("home"))) super.load(target);
    }

    @Override void battleScreen() { screen = "battle"; }
  }

  static final class FixtureApi extends NativeApi {
    final JSONObject state;
    final List<String> paths = new ArrayList<>();
    JSONObject currentBattle = MainActivity.obj("session", "resume-fixture");

    FixtureApi(MainActivity h, JSONObject state) { super(h); this.state = state; }
    @Override String token() { return "isolated-home-fixture"; }
    @Override void call(String path, JSONObject body, Result callback) {
      paths.add(path);
      assertNull("Home navigation must never submit account/game mutations: " + path, body);
      if (path.equals("native/state")) callback.done(state, null);
      else if (path.equals("native/battle/current")) callback.done(currentBattle, null);
      else callback.done(new JSONObject(), null);
    }
  }

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }

  @After public void restoreDisplay() {
    RuntimeEnvironment.setFontScale(1f);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
  }

  JSONObject fixture(boolean activeBattle) {
    return MainActivity.obj(
        "me", MainActivity.obj("nickname", "검토 수비조장", "units", new JSONArray(),
            "battleTeam", new JSONArray().put("D1")),
        "team", new JSONArray().put("instance-one"),
        "units", new JSONArray().put(MainActivity.obj("key", "instance-one", "id", "D1",
            "name", "갈매미", "level", 12, "hp", 100, "maxHp", 100,
            "xp", 20, "xpNext", 100, "attr", "돌", "attrCode", "STONE",
            "skills", new JSONArray(), "learn", new JSONArray(), "evolve", new JSONArray())),
        "chapters", new JSONArray(), "attributes", new JSONObject(),
        "typeChart", new JSONObject(), "activeBattle", activeBattle);
  }

  ActivityController<HomeHost> start(boolean activeBattle) {
    ActivityController<HomeHost> controller = Robolectric.buildActivity(HomeHost.class).create();
    HomeHost h = controller.get();
    h.motionEnabled = false;
    controller.start().resume().visible();
    h.api.close();
    h.state = fixture(activeBattle);
    h.api = new FixtureApi(h, h.state);
    h.art.key = "isolated-home-fixture";
    h.guide.enabled = false;
    h.home();
    return controller;
  }

  LobbyView lobby(HomeHost h) { return (LobbyView) h.root.getChildAt(0); }

  void layout(HomeHost h, int width, int height, int left, int top, int right, int bottom) {
    review.viewport(h, width, height);
    // Same effective safe-area values used by GameSafeArea, insulated from test window callbacks.
    ViewCompat.setOnApplyWindowInsetsListener(h.root, null);
    h.root.setPadding(left, top, right, bottom);
    Runnable measure = () -> {
      review.force(h.root);
      h.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      h.root.layout(0, 0, width, height);
    };
    measure.run();
    review.preparePreDraw(h.root, measure);
  }

  Rect box(LobbyView lobby, View view) {
    Rect r = new Rect();
    view.getDrawingRect(r);
    lobby.offsetDescendantRectToMyCoords(view, r);
    return r;
  }

  void containedTouch(HomeHost h, LobbyView lobby, View view, RectF safe) {
    assertNotNull(view);
    assertEquals(View.VISIBLE, view.getVisibility());
    Rect r = box(lobby, view);
    assertTrue(view.getTag() + " width " + r, r.width() >= h.dp(48));
    assertTrue(view.getTag() + " height " + r, r.height() >= h.dp(48));
    assertTrue(view.getTag() + " outside safe area " + r + " / " + safe,
        r.left >= safe.left - 1 && r.top >= safe.top - 1
            && r.right <= safe.right + 1 && r.bottom <= safe.bottom + 1);
    assertTrue(view.isClickable());
    assertTrue(view.isFocusable());
  }

  @Test public void phoneAndTabletMenusAreSafeDisjointAndKeepOrnateHierarchy() {
    int[][] profiles = {{915, 412, 0, 0, 0, 0}, {1006, 411, 32, 8, 24, 16},
        {1280, 720, 0, 0, 0, 0}, {640, 320, 0, 0, 0, 0}, {1280, 800, 0, 0, 0, 0}};
    for (int[] p : profiles) {
      RuntimeEnvironment.setQualifiers("w" + p[0] + "dp-h" + p[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(p[1] == 320 ? 2f : p[1] == 800 ? 1.3f : 1f);
      for (boolean resume : new boolean[] {false, true}) {
        try (ActivityController<HomeHost> controller = start(resume)) {
          HomeHost h = controller.get();
          layout(h, p[0], p[1], p[2], p[3], p[4], p[5]);
          LobbyView l = lobby(h);
          assertTrue("All home artwork including local map-reading guide loads", l.homeArt.ready());
          assertTrue("Four surfaces and ten separate runtime icons load", l.menuArt.ready());
          assertNotNull(l.homeArt.hero());
          assertNotNull(l.homeArt.heroBounds());
          assertEquals("Original actions, menu toggle and news", 15, l.buttons.size());
          assertFalse(l.menuOpen);
          for (int index : LobbyView.MENU_ITEMS) assertEquals(View.GONE, l.buttons.get(index).getVisibility());
          Rect fixedPlay = box(l, l.buttons.get(5));
          RectF fixedHero = l.heroBox();
          l.buttons.get(13).performClick();
          layout(h, p[0], p[1], p[2], p[3], p[4], p[5]);
          assertTrue(l.menuOpen);
          assertEquals("Play does not move when opened", fixedPlay, box(l, l.buttons.get(5)));
          assertEquals("Hero does not jump when opened", fixedHero, l.heroBox());
          assertNull("Retired harbor shortcut stays absent", l.findViewWithTag("lobby-harbor"));
          assertNull("Retired growth panel stays absent", l.findViewWithTag("lobby-growth"));
          RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
          for (Button button : l.buttons) containedTouch(h, l, button, safe);
          for (int i = 0; i < l.buttons.size(); i++)
            for (int j = i + 1; j < l.buttons.size(); j++)
              assertFalse("Overlapping commands " + i + " / " + j,
                  Rect.intersects(box(l, l.buttons.get(i)), box(l, l.buttons.get(j))));
          int[] ordered = LobbyView.MENU_ITEMS;
          for (int i = 0; i < ordered.length; i++) {
            Rect current = box(l, l.buttons.get(ordered[i]));
            assertTrue("Eight menu tiles stay in the panel", l.menuPanel.contains(new RectF(current)));
            if (i % 4 != 0) {
              Rect previous = box(l, l.buttons.get(ordered[i-1]));
              assertTrue("Approved menu order left to right", previous.right <= current.left);
              assertEquals(previous.top, current.top);
            }
          }
          assertTrue("Second row is below first row", l.boxes[ordered[4]].top >= l.boxes[ordered[0]].bottom);
          assertTrue("Panel clears fixed action row", l.menuPanel.bottom < l.boxes[5].top);
          assertEquals(l.boxes[5].bottom, l.boxes[13].bottom, .01f);
          assertTrue("Profile is compact", l.boxes[7].width() <= h.dp(220));
          containedTouch(h, l, l.gull, safe);
          RectF hero = l.heroBox();
          assertTrue("Advice is below the complete hero", l.tipRegion.top > l.tipHeroExclusion().bottom);
          assertFalse("Open panel never covers hero", RectF.intersects(hero, l.menuPanel));
          View resumeButton = l.findViewWithTag("lobby-resume");
          if (resume) {
            containedTouch(h, l, resumeButton, safe);
            for (Button button : l.buttons)
              assertFalse("Resume must not cover a menu", Rect.intersects(box(l, resumeButton), box(l, button)));
            assertFalse("Resume stays clear of the foreground guide",
                RectF.intersects(new RectF(box(l, resumeButton)), hero));
          } else assertNull(resumeButton);
          for (float pulse : new float[] {-1, 0, 1})
            for (float dragX : new float[] {-h.dp(18), 0, h.dp(18)})
              for (float dragY : new float[] {-h.dp(18), 0, h.dp(18)}) {
                RectF pose = l.heroDrawBox(pulse, dragX, dragY);
                // character() shears around the foot line, so include the displaced
                // upper corners in collision bounds rather than testing only its bitmap box.
                float lean = HomeGullDrag.bounded(dragX, h.dp(4));
                RectF drawn = new RectF(pose.left + Math.min(0, lean), pose.top,
                    pose.right + Math.max(0, lean), pose.bottom);
                RectF shadow = l.heroShadowBox(l.heroBox());
                assertTrue("Animated guide stays in the mobile safe area", safe.contains(drawn));
                assertTrue("Separate contact shadow stays safe", safe.contains(shadow));
                assertNull("Removed growth surface cannot mask the guide or shadow",
                    l.findViewWithTag("lobby-growth"));
                assertTrue("Contact shadow meets the feet", shadow.top < drawn.bottom && shadow.bottom > drawn.bottom);
                assertEquals("Breathing and drag never lift the feet", l.heroBox().bottom,
                    drawn.bottom, .001f);
                for (Button button : l.buttons) {
                  assertFalse("Guide stays clear of every menu", RectF.intersects(drawn, new RectF(box(l, button))));
                  assertFalse("Shadow stays clear of every menu", RectF.intersects(shadow, new RectF(box(l, button))));
                }
                if (resumeButton != null)
                  assertFalse("Guide stays clear of battle resume", RectF.intersects(drawn, new RectF(box(l, resumeButton))));
              }
        }
      }
    }
  }

  @Test public void originalCommandsKeepTheirDestinationsAndNewCommandsHaveStableTags() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      layout(h, 915, 412, 0, 0, 0, 0);
      LobbyView l = lobby(h);
      String[] destinations = {"expedition", "draw", "collection", "craft", "shop", "modes",
          "daily", null, "settings", "shop", "shop", "shop"};
      for (int i = 0; i < destinations.length; i++) if (destinations[i] != null) {
        l.setMenuOpen(true);
        h.requested = "";
        assertTrue(l.buttons.get(i).performClick());
        assertEquals("Original command " + i, destinations[i], h.requested);
      }
      assertSame(l.buttons.get(12), l.findViewWithTag("lobby-team"));
      assertNull("Removed harbor command is not mounted", l.findViewWithTag("lobby-harbor"));
      assertNull("Removed direct growth command is not mounted", l.findViewWithTag("lobby-growth"));
      assertEquals(15, l.buttons.size());
      assertNull(l.findViewWithTag("lobby-information"));
      assertSame(l.buttons.get(14), l.findViewWithTag("lobby-news"));
      l.setMenuOpen(true);
      h.requested = "";
      assertTrue(l.buttons.get(14).performClick());
      assertEquals("news", h.requested);
      assertSame(l.buttons.get(7), l.findViewWithTag("lobby-profile"));
      assertSame(l.buttons.get(8), l.findViewWithTag("lobby-settings"));
    }
  }

  @Test public void teamOpensFreshStoryInstanceRosterAndReturnsHome() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      h.followTeam = true;
      h.selectionMode = "pvp";
      h.menus.teamMode = "pvp";
      h.menus.teamDraft = new ArrayList<>(Arrays.asList("stale-pvp-draft"));
      layout(h, 915, 412, 0, 0, 0, 0);
      lobby(h).setMenuOpen(true);
      lobby(h).findViewWithTag("lobby-team").performClick();
      assertEquals("team", h.screen);
      assertEquals("story", h.selectionMode);
      assertEquals("story", h.menus.teamMode);
      assertEquals(Arrays.asList("instance-one"), h.menus.teamDraft);
      assertEquals("home", h.menus.teamReturnTarget());
      assertFalse(h.menus.isTeamDirty());
      h.getOnBackPressedDispatcher().onBackPressed();
      assertEquals("home", h.screen);
    }
  }

  @Test public void growthKeepsExactUnitAndHomeReturnAndEmptyRosterOpensCollection() throws Exception {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      layout(h, 915, 412, 0, 0, 0, 0);
      // HOME no longer prints a growth card. The existing collection is the growth entry.
      JSONObject owned = new JSONObject(h.state.getJSONArray("units").getJSONObject(0).toString());
      owned.put("known", true).put("have", 1).put("tier", "COMMON").put("tierName", "흔함");
      h.me().put("units", new JSONArray().put(owned));
      lobby(h).setMenuOpen(true);
      lobby(h).findViewWithTag("lobby-collection").performClick();
      assertEquals("collection", h.requested);
      h.collection();
      layout(h, 915, 412, 0, 0, 0, 0);
      Button growth = h.root.findViewWithTag("collection-growth");
      assertNotNull("Existing collection growth action remains", growth);
      assertTrue(growth.isEnabled());
      new NativeDialogLayoutReviewTest().fixedButton(h, h.root, growth);
      JSONObject authoritative = h.state;
      growth.performClick();
      assertEquals("unit", h.screen);
      assertEquals("instance-one", h.currentKey);
      assertEquals("collection", h.menus.unitReturn);
      assertSame("Growth navigation does not mutate or replace inventory", authoritative, h.state);
      layout(h, 915, 412, 0, 0, 0, 0);
      android.widget.TextView experience = h.root.findViewWithTag("unit-xp");
      assertNotNull("Actual growth retains the server experience row", experience);
      assertEquals("경험치 20 / 100", experience.getText().toString());
      new NativeDialogLayoutReviewTest().readable(experience);
      NativeNoScrollReviewTest.assertFullyVisible(experience, h.root);
      assertTrue("Actual server level remains visible", flow.labels(h.root).contains("Lv12"));
      h.getOnBackPressedDispatcher().onBackPressed();
      assertEquals("Existing growth back route returns to its collection", "collection", h.screen);
      h.state.put("team", new JSONArray()).put("units", new JSONArray());
      h.me().put("units", new JSONArray());
      h.home();
      assertNull("No growth panel reappears for an empty roster", lobby(h).findViewWithTag("lobby-growth"));
      h.requested = "";
      lobby(h).setMenuOpen(true);
      lobby(h).findViewWithTag("lobby-collection").performClick();
      assertEquals("collection", h.requested);
      h.collection();
      assertEquals("collection", h.screen);
      assertNull("Empty collection does not invent an individual growth action",
          h.root.findViewWithTag("collection-growth"));
    }
  }

  @Test public void tutorialFindUnfoldsRealActionsWithoutAdvancingProgress() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get(); layout(h, 915, 412, 0, 0, 0, 0);
      LobbyView l = lobby(h);
      String[] keys = {"daily", "draw", "defenders", "team"};
      String[] labels = {"출석체크", "모집", "방범대", "전투 편성"};
      int[] indices = {6, 1, 12, 12};
      int step = h.guide.progress.step;
      String progress = h.state.toString();
      for (int i = 0; i < keys.length; i++) {
        l.setMenuOpen(false);
        ((FixtureApi) h.api).paths.clear();
        View target = h.guide.find(keys[i], labels[i]);
        assertSame("Tutorial retains the actual original action", l.buttons.get(indices[i]), target);
        assertTrue("Target lookup unfolds hidden home menu", l.menuOpen);
        assertEquals(View.VISIBLE, target.getVisibility());
        layout(h, 915, 412, 0, 0, 0, 0);
        containedTouch(h, l, target, h.safeBounds(l.getWidth(), l.getHeight()));
        assertEquals("Anchor lookup must not advance tutorial", step, h.guide.progress.step);
        assertEquals("Anchor lookup never mutates game state", progress, h.state.toString());
        assertTrue("Finding a tutorial anchor sends no API mutation", ((FixtureApi) h.api).paths.isEmpty());
      }
    }
  }

  @Test public void veryShortLargeFontBattleGuideKeepsFortyEightDpHitTarget() {
    RuntimeEnvironment.setQualifiers("w568dp-h280dp-land-mdpi");
    RuntimeEnvironment.setFontScale(2f);
    try (ActivityController<HomeHost> controller = start(true)) {
      HomeHost h = controller.get(); layout(h, 568, 280, 0, 0, 0, 0);
      LobbyView l = lobby(h);
      RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
      containedTouch(h, l, l.gull, safe);
      Rect hit = box(l, l.gull);
      assertFalse("Guide touch area stays clear of battle resume",
          Rect.intersects(hit, box(l, l.findViewWithTag("lobby-resume"))));
      assertFalse("Guide touch area stays clear of fixed play", Rect.intersects(hit, box(l, l.buttons.get(5))));
      assertFalse("Guide touch area stays clear of menu toggle", Rect.intersects(hit, box(l, l.buttons.get(13))));
    }
  }

  @Test public void menuToggleOutsideTapAndBackKeepUnderlyingActionsUntouched() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get(); layout(h, 915, 412, 0, 0, 0, 0);
      LobbyView l = lobby(h);
      l.buttons.get(13).performClick(); layout(h, 915, 412, 0, 0, 0, 0);
      assertTrue(l.menuOpen);
      h.requested = "";
      RectF settings = l.boxes[8];
      for (int action : new int[] {android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
        android.view.MotionEvent e = android.view.MotionEvent.obtain(0, 1, action, settings.centerX(), settings.centerY(), 0);
        assertTrue(l.dispatchTouchEvent(e)); e.recycle();
      }
      assertFalse("Outside tap closes panel", l.menuOpen);
      assertEquals("Closing gesture cannot activate the covered setting", "", h.requested);
      l.setMenuOpen(true);
      h.getOnBackPressedDispatcher().onBackPressed();
      assertFalse("Android back closes menu first", l.menuOpen);
      assertNull("First back does not show exit dialog", h.visibleDialog);
      assertFalse(h.isFinishing());
      assertEquals("home", h.screen);
      for (int i : LobbyView.MENU_ITEMS) assertEquals(View.GONE, l.buttons.get(i).getVisibility());
      l.badges[1] = true; l.updateMenuDescription();
      assertTrue(l.buttons.get(13).getContentDescription().toString().contains("받을 보상"));
      l.badges[1] = false; l.badges[0] = true; l.updateMenuDescription();
      assertTrue(l.buttons.get(13).getContentDescription().toString().contains("받을 보상"));
      l.badges[0] = false; l.updateMenuDescription();
      assertFalse(l.buttons.get(13).getContentDescription().toString().contains("받을 보상"));
    }
  }

  @Test public void profileKeepsProfilePopupAndResumeUsesCurrentBattleEndpoint() {
    try (ActivityController<HomeHost> controller = start(true)) {
      HomeHost h = controller.get();
      layout(h, 915, 412, 0, 0, 0, 0);
      FixtureApi api = (FixtureApi) h.api;
      for (String tag : new String[] {"lobby-profile"}) {
        api.paths.clear();
        lobby(h).findViewWithTag(tag).performClick();
        assertTrue("Profile popup reads the existing rank endpoint", api.paths.contains("pvp/rank"));
        assertEquals("home", h.screen);
      }
      api.paths.clear();
      lobby(h).findViewWithTag("lobby-resume").performClick();
      assertEquals(Arrays.asList("native/battle/current"), api.paths);
      assertEquals("resume-fixture", h.battle.optString("session"));
      assertEquals("battle", h.screen);
    }
  }

  @Test public void approvedHomeUsesBundledJuaCompactHudAndGroundedGuide() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      layout(h, 915, 412, 0, 0, 0, 0);
      LobbyView l = lobby(h);
      assertSame("Home must use the explicitly bundled Jua face, not system/workbench fallback",
          h.font, l.homeHeading);
      assertEquals(915 * .175f, l.boxes[7].width(), 1f);
      assertEquals(915 * .58f, l.boxes[9].left, 1f);
      RectF hero = l.heroBox();
      assertEquals(915 * .23f, hero.centerX(), 1f);
      assertTrue("Repositioning must not inflate the approved guide", hero.height() <= 412 * .43f);
      assertTrue("Feet sit in the lower scene", hero.bottom >= 412 * .72f);
      assertEquals("Advice remains tap-triggered", View.GONE, l.tip.getVisibility());
      l.gull.performClick();
      layout(h, 915, 412, 0, 0, 0, 0);
      assertEquals(View.VISIBLE, l.tip.getVisibility());
      assertTrue("Visible advice is below all hero/contact ink", l.tipFrame.top > l.tipHeroExclusion().bottom);
      assertEquals("Advice does not reposition the guide", hero, l.heroBox());
    }
  }

  @Test public void broadEnvelopeUsesVisibleAlphaRatherThanPaddedSquareCanvas() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      LobbyView l = lobby(h);
      android.graphics.Bitmap rendered = android.graphics.Bitmap.createBitmap(120, 100,
          android.graphics.Bitmap.Config.ARGB_8888);
      l.menuArt.icon(new android.graphics.Canvas(rendered), "draw", new RectF(10, 10, 96, 68));
      int top = 100, bottom = -1, left = 120, right = -1;
      for (int y = 0; y < 100; y++) for (int x = 0; x < 120; x++) {
        if (android.graphics.Color.alpha(rendered.getPixel(x, y)) <= 12) continue;
        top = Math.min(top, y); bottom = Math.max(bottom, y);
        left = Math.min(left, x); right = Math.max(right, x);
      }
      assertTrue("Envelope ink fills the wide slot (not the 512px transparent canvas)",
          right - left >= 82 && bottom - top >= 53);
      assertTrue("Ink remains inside slot", left >= 9 && right <= 97 && top >= 9 && bottom <= 69);
      rendered.recycle();
    }
  }
}

