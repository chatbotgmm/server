package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 실제 View 회귀 검사. 서버 없이 선택·취소·잠금·고정 배치 계약을 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w840dp-h344dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCollectionFocusTest {
  final RegistrationIntegrationScreensTest fixture = new RegistrationIntegrationScreensTest();
  @Before public void isolate() { fixture.isolate(); }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  void event(View card, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(0, 100, action, x, y, 0);
    card.dispatchTouchEvent(event); event.recycle();
  }

  @Test public void repeatedSwipeTapCancelAndMultitouchNeverOpenOrWrite() {
    try (var controller = fixture.flow.start()) {
      MainActivity h = controller.get();
      var api = fixture.prepare(h);
      h.collection(); fixture.layout(h, 840, 344);
      View card = h.root.findViewWithTag("collection-inline-grid");
      for (int i = 0; i < 5; i++) {
        event(card, MotionEvent.ACTION_DOWN, 200, 100);
        event(card, MotionEvent.ACTION_UP, 100, 100);
        assertEquals("C2", h.menus.library.selectedCollectionId);
        event(card, MotionEvent.ACTION_DOWN, 100, 100);
        event(card, MotionEvent.ACTION_UP, 100, 100);
        assertEquals("C2", h.menus.library.selectedCollectionId);
        assertTrue(card.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
        assertEquals("C1", h.menus.library.selectedCollectionId);
        for (int cancel : new int[]{MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN}) {
          event(card, MotionEvent.ACTION_DOWN, 200, 100);
          event(card, cancel, 180, 100);
          event(card, MotionEvent.ACTION_UP, 100, 100);
          assertEquals("C1", h.menus.library.selectedCollectionId);
        }
        event(card, MotionEvent.ACTION_DOWN, 200, 100);
        card.onWindowFocusChanged(false);
        event(card, MotionEvent.ACTION_UP, 100, 100);
        assertEquals("C1", h.menus.library.selectedCollectionId);
      }
      assertNull(h.visibleDialog);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void informationBackPreservesTheSameCardFilterAndPosition() {
    try (var controller = fixture.flow.start()) {
      MainActivity h = controller.get(); var api = fixture.prepare(h);
      h.menus.query = "등록증"; h.menus.library.collectionOwned = true;
      h.collection(); fixture.layout(h, 840, 344);
      View pane = h.root.findViewWithTag("collection-inline-grid");
      pane.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null);
      View originalArt = h.root.findViewWithTag("collection-registration-C2");
      for (boolean systemBack : new boolean[]{false, true}) {
        h.root.findViewWithTag("collection-info").performClick();
        assertSame(originalArt, h.root.findViewWithTag("collection-registration-C2"));
        assertNull(h.visibleDialog);
        assertFalse(pane.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        h.root.findViewWithTag("collection-info-state").performClick();
        if (systemBack) h.back(); else h.root.findViewWithTag("collection-info-back").performClick();
        assertSame(originalArt, h.root.findViewWithTag("collection-registration-C2"));
        assertEquals("collection", h.screen);
        assertEquals("C2", h.menus.library.selectedCollectionId);
        assertEquals(1, h.menus.library.collectionPage);
        assertEquals("등록증", h.menus.query);
        assertTrue(h.menus.library.collectionOwned);
        assertNotNull(h.root.findViewWithTag("collection-growth"));
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void emptyAndHiddenSpeciesCannotLeaveStaleActions() throws Exception {
    try (var controller = fixture.flow.start()) {
      MainActivity h = controller.get();
      fixture.prepare(h);
      JSONObject hidden = h.me().getJSONArray("units").getJSONObject(0);
      hidden.put("known", false).put("tier", "HIDDEN").put("have", 0);
      h.state.put("units", new JSONArray());
      h.menus.library.selectedCollectionId = "C1";
      h.collection();
      assertFalse(h.root.findViewWithTag("collection-growth").isEnabled());
      assertFalse(h.root.findViewWithTag("collection-craft").isEnabled());
      RegistrationCardView.Artwork art = (RegistrationCardView.Artwork) h.root.findViewWithTag("collection-registration-C1");
      assertFalse(art.visibleIdentity);
      h.menus.query = "일치하지않는검색"; h.collection();
      assertEquals("", h.menus.library.selectedCollectionId);
      assertNull(h.root.findViewWithTag("collection-growth"));
      assertNotNull(h.root.findViewWithTag("collection-empty-action"));
      h.me().put("units", new JSONArray()); h.menus.query = ""; h.collection();
      assertEquals("갈매미 모집", ((Button)h.root.findViewWithTag("collection-empty-action")).getText().toString());
    }
  }

  @Test public void multipleInstancesKeepGrowthChooserAndAwayManagementGates() throws Exception {
    try (var controller = fixture.flow.start()) {
      MainActivity h = controller.get();
      var api = fixture.prepare(h);
      JSONArray roster = h.state.getJSONArray("units");
      roster.put(new JSONObject(roster.getJSONObject(0).toString()).put("key", "second-C1").put("level", 36));
      h.collection();
      h.root.findViewWithTag("collection-growth").performClick();
      assertNotNull(h.visibleDialog);
      assertNotNull(h.visibleDialog.getListView());
      assertEquals(2, h.visibleDialog.getListView().getAdapter().getCount());
      h.visibleDialog.dismiss();
      JSONObject species = h.me().getJSONArray("units").getJSONObject(0);
      species.put("have", 2).put("away", 2);
      java.util.ArrayList<JSONObject> list = new java.util.ArrayList<>(); list.add(species);
      h.menus.library.hold(list, 0);
      View dialog = h.visibleDialog.getWindow().getDecorView();
      assertFalse(fixture.button(dialog, "탐험 편성에 넣기").isEnabled());
      assertFalse(fixture.button(dialog, "보호하기").isEnabled());
      h.visibleDialog.dismiss();
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void gradeEntryIntersectsFiltersResetsAndPreservesBack() throws Exception {
    try (var controller = fixture.flow.start()) {
      MainActivity h = controller.get(); var api = fixture.prepare(h);
      JSONArray units = h.me().getJSONArray("units");
      units.getJSONObject(0).put("tier", "LEGEND").put("tierName", "전설").put("have", 0);
      units.getJSONObject(1).put("tier", "HIDDEN").put("tierName", "히든");
      h.collection();
      NativeLibrary library = h.menus.library;
      NativeCollectionFocus focus = library.collectionFocus;
      h.root.findViewWithTag("collection-grade-filter").performClick();
      h.visibleDialog.getWindow().getDecorView().findViewWithTag("collection-grade-LEGEND").performClick();
      assertEquals("LEGEND", h.menus.grade);
      assertEquals("C1", library.selectedCollectionId);
      assertEquals(0, library.collectionPage);
      for (JSONObject unit : focus.visible) assertEquals("LEGEND", unit.optString("tier"));
      assertFalse(focus.move(1));
      h.root.findViewWithTag("collection-info").performClick(); h.back();
      assertEquals("LEGEND", h.menus.grade);
      assertEquals("C1", library.selectedCollectionId);
      h.root.findViewWithTag("collection-owned-filter").performClick();
      assertTrue(focus.visible.isEmpty());
      assertNull(h.root.findViewWithTag("collection-growth"));
      library.selectCollectionGrade("HIDDEN", focus.all); focus.filter();
      assertEquals("C2", library.selectedCollectionId);
      h.menus.query = "일치하지않는검색"; focus.filter();
      assertTrue(focus.visible.isEmpty());
      assertEquals("", library.selectedCollectionId);
      assertNull(h.root.findViewWithTag("collection-record"));
      assertTrue(library.collectionGrades(focus.all).containsKey("LEGEND"));
      assertTrue(library.collectionGrades(focus.all).containsKey("HIDDEN"));
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void fixedActionsRemainVisibleAtRequestedSizesAndLargeText() {
    for (float scale : new float[]{1f, 1.5f, 2f})
      for (int[] size : new int[][]{{840,344},{800,360},{640,320},{568,320}}) {
        RuntimeEnvironment.setFontScale(scale);
        try (var controller = fixture.flow.start()) {
          MainActivity h = controller.get(); fixture.prepare(h);
          h.root.setPadding(16, 0, 16, 24);
          h.collection(); fixture.layout(h, size[0], size[1]);
          for (String tag : new String[]{"collection-info", "collection-growth", "collection-team",
              "collection-craft", "collection-record", "collection-open-list", "collection-open-items", "collection-grade-filter"}) {
            View action = h.root.findViewWithTag(tag);
            NativeNoScrollReviewTest.assertFullyVisible(action, h.root);
            assertTrue(tag, action.getWidth() >= h.dp(48) && action.getHeight() >= h.dp(48));
          }
          for (String tag : new String[]{"collection-growth", "collection-team", "collection-craft", "collection-record"}) {
            Button action = h.root.findViewWithTag(tag);
            assertNotNull(tag + " local JournalIcons", action.getCompoundDrawablesRelative()[0]);
            assertNotNull(tag + " full accessible label", action.getContentDescription());
            assertEquals(h.dp(scale > 1.3f ? 28 : 32), action.getCompoundDrawablesRelative()[0].getBounds().width());
          }
          assertSame(h.root.findViewWithTag("collection-info").getParent(),
              h.root.findViewWithTag("collection-grade-filter").getParent());
          View grow = h.root.findViewWithTag("collection-growth"), team = h.root.findViewWithTag("collection-team");
          assertSame(grow.getParent(), team.getParent());
          assertEquals(grow.getTop(), team.getTop());
          for (View view : fixture.geometry.descendants(h.body)) assertFalse(view instanceof ScrollView);
        }
      }
  }
}
