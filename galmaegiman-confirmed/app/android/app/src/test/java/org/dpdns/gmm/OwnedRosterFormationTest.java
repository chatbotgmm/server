package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Uses production formation cards; no live server or account data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class OwnedRosterFormationTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();

  @Before public void setup() { helper.isolate(); }
  @After public void cleanup() throws Exception { LocalGameArtTest.drain(); helper.restore(); }

  @Test public void candidatesHaveOneCardPerOwnedIdAndSelectionRetainsChosenOrder() throws Exception {
    try (ActivityController<MainActivity> controller = helper.flow.start()) {
      MainActivity h = controller.get();
      h.api.close();
      JSONArray source = PartyUiWorkPerformanceTest.roster(3);
      source.getJSONObject(0).put("level", 3);
      source.getJSONObject(1).put("level", 50);
      source.getJSONObject(2).put("level", 12);
      // Same species in all three records is intentional. Only the repeated ownedId is removed.
      source.put(new JSONObject(source.getJSONObject(1).toString()));
      ArrayList<JSONArray> submitted = new ArrayList<>();
      Roster60FormationView form = new Roster60FormationView(h, source, submitted::add, () -> {});
      form.toggle(source.getJSONObject(0));
      form.toggle(source.getJSONObject(1));
      form.measure(View.MeasureSpec.makeMeasureSpec(h.dp(915), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h.dp(350), View.MeasureSpec.EXACTLY));

      assertEquals(Arrays.asList("o1", "o2", "o0"), new ArrayList<>(form.ownedTiles.keySet()));
      assertEquals(3, form.units.length());
      assertEquals(3, form.ownedStrip.cards.getChildCount());
      assertEquals(Arrays.asList("o0", "o1"), form.selected);
      form.start.performClick();
      assertEquals(1, submitted.size());
      assertEquals("o0", submitted.get(0).getString(0));
      assertEquals("o1", submitted.get(0).getString(1));
      assertEquals(4, source.length());
    }
  }
}
