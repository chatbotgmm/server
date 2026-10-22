package org.dpdns.gmm;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

// org.json is supplied by Android: use the same runtime as the native suites.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FreestyleSelectionTest {
  JSONObject scope() throws Exception {
    return new JSONObject().put("kind","own-attribute").put("selectionId","choice-a")
        .put("phaseId","freestyle:1:1").put("targetKey","unit-a")
        .put("attributes",new JSONArray().put("FIRE").put("SEA"));
  }
  @Test public void draftCancelAndConfirmation() throws Exception {
    FreestyleSelection draft=new FreestyleSelection(scope());
    assertNull(draft.confirmed());
    draft.select("FIRE"); assertEquals("FIRE",draft.selected);
    draft.cancel(); assertNull(draft.confirmed());
    draft.select("SEA");
    JSONObject command=draft.confirmed();
    assertEquals("SEA",command.getString("attribute"));
    assertEquals("unit-a",command.getString("targetKey"));
    assertEquals("choice-a",command.getString("selectionId"));
    assertEquals("freestyle:1:1",command.getString("phaseId"));
    assertEquals(4,command.length());
  }
  @Test public void rejectArbitraryAttributesAndStaleScope() throws Exception {
    FreestyleSelection draft=new FreestyleSelection(scope());
    draft.select("injected"); assertNull(draft.confirmed());
    assertTrue(draft.sameScope(scope()));
    for(String key:new String[]{"kind","selectionId","phaseId","targetKey"})
      assertFalse(draft.sameScope(scope().put(key,"different")));
    assertFalse(draft.sameScope(null));
    assertFalse(new FreestyleSelection(null).valid());
  }
  @Test public void repeatedDraftIsNotAutomaticSubmission() throws Exception {
    FreestyleSelection draft=new FreestyleSelection(scope());
    draft.select("FIRE");draft.select("SEA");draft.select("FIRE");
    assertEquals("FIRE",draft.selected);
    assertEquals("FIRE",draft.confirmed().getString("attribute"));
    draft.cancel();assertNull(draft.confirmed());
  }
}
