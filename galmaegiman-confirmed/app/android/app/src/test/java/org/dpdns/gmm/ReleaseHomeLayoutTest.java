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
        {1280, 720, 0, 0, 0, 0}, {1280, 800, 0, 0, 0, 0}};
    for (int[] p : profiles) {
      RuntimeEnvironment.setQualifiers("w" + p[0] + "dp-h" + p[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(p[1] == 800 ? 1.3f : 1f);
      for (boolean resume : new boolean[] {false, true}) {
        try (ActivityController<HomeHost> controller = start(resume)) {
          HomeHost h = controller.get();
          layout(h, p[0], p[1], p[2], p[3], p[4], p[5]);
          LobbyView l = lobby(h);
          assertTrue("All home artwork including local map-reading guide loads", l.homeArt.ready());
          assertNotNull(l.homeArt.hero());
          assertNotNull(l.homeArt.heroBounds());
          assertEquals("Only duplicate information is removed; every route remains accessible", 14, l.buttons.size());
          RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
          for (Button button : l.buttons) containedTouch(h, l, button, safe);
          for (int i = 0; i < l.buttons.size(); i++)
            for (int j = i + 1; j < l.buttons.size(); j++)
              assertFalse("Overlapping commands " + i + " / " + j,
                  Rect.intersects(box(l, l.buttons.get(i)), box(l, l.buttons.get(j))));
          int[] dock = {13, 0, 1, 2, 3, 4};
          for (int i = 1; i < dock.length; i++) {
            Rect a = box(l, l.buttons.get(dock[i - 1])), b = box(l, l.buttons.get(dock[i]));
            assertTrue("Harbor, explore, draw, collection, craft, shop order", a.right <= b.left);
            assertEquals("Bottom commands share a baseline", a.bottom, b.bottom);
          }
          int[] stack = {5, 12, 6};
          for (int i = 1; i < stack.length; i++) {
            Rect a = box(l, l.buttons.get(stack[i - 1])), b = box(l, l.buttons.get(stack[i]));
            assertTrue("Play, team, daily vertical order", a.bottom <= b.top);
            assertEquals("Right commands align", a.right, b.right);
          }
          assertTrue(box(l, l.buttons.get(6)).bottom <= box(l, l.buttons.get(13)).top);
          containedTouch(h, l, l.growthAction, safe);
          assertEquals("Compact XP remains readable at enlarged font scale",
              View.VISIBLE, l.growthCompactXp.getVisibility());
          assertTrue("XP row fits without clipping",
              l.growthCompactXp.getHeight() >= l.growthCompactXp.getLineHeight()
                  + l.growthCompactXp.getPaddingTop());
          Rect growth = box(l, l.growthCard);
          for (Button button : l.buttons)
            assertFalse("Growth card must not cover a menu", Rect.intersects(growth, box(l, button)));
          assertTrue("Profile is a compact portrait/name chip", l.boxes[7].width() <= h.dp(220));
          containedTouch(h, l, l.gull, safe);
          RectF hero = l.heroBox();
          assertTrue("Guide stands on foreground paving", hero.bottom >= safe.top + safe.height() * .72f);
          assertTrue("Guide keeps a sensible tablet scale", hero.height() <= safe.height() * .56f);
          assertTrue("Growth sits beside the guide, leaving the feet visible",
              growth.left >= hero.right + h.dp(24));
          View resumeButton = l.findViewWithTag("lobby-resume");
          if (resume) {
            containedTouch(h, l, resumeButton, safe);
            for (Button button : l.buttons)
              assertFalse("Resume must not cover a menu", Rect.intersects(box(l, resumeButton), box(l, button)));
            assertFalse("Resume and growth stay separate", Rect.intersects(box(l, resumeButton), growth));
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
                assertFalse("Growth never masks the guide or shadow", RectF.intersects(drawn, new RectF(growth))
                    || RectF.intersects(shadow, new RectF(growth)));
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
        h.requested = "";
        assertTrue(l.buttons.get(i).performClick());
        assertEquals("Original command " + i, destinations[i], h.requested);
      }
      assertSame(l.buttons.get(12), l.findViewWithTag("lobby-team"));
      assertSame(l.buttons.get(13), l.findViewWithTag("lobby-harbor"));
      h.requested = "";
      l.buttons.get(13).performClick();
      assertEquals("home", h.requested);
      assertEquals(14, l.buttons.size());
      assertNull(l.findViewWithTag("lobby-information"));
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
      lobby(h).growthAction.performClick();
      assertEquals("unit", h.requested);
      assertEquals("instance-one", h.currentKey);
      assertEquals("home", h.menus.unitReturn);
      h.state.put("team", new JSONArray());
      h.home();
      lobby(h).growthAction.performClick();
      assertEquals("collection", h.requested);
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
}
