package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class NativeOwnedRosterTest {
  static JSONObject unit(String ownedId, String species, int level) throws Exception {
    return new JSONObject().put("ownedId", ownedId).put("speciesId", species).put("level", level);
  }

  static List<String> identities(JSONArray source) {
    ArrayList<String> result = new ArrayList<>();
    for (int i = 0; i < source.length(); i++) result.add(source.optJSONObject(i).optString("ownedId"));
    return result;
  }

  @Test public void levelsDescendWithStableTiesAndOriginalResponseUnchanged() throws Exception {
    JSONObject first = unit("first", "u01", 3), high = unit("high", "u02", 42),
        tied = unit("tied", "u03", 42);
    JSONArray source = new JSONArray().put(first).put(high).put(tied);
    JSONArray ordered = NativeOwnedRoster.levelOrdered(source);
    assertEquals(Arrays.asList("high", "tied", "first"), identities(ordered));
    assertEquals(Arrays.asList("first", "high", "tied"), identities(source));
    assertSame(first, ordered.optJSONObject(2));
  }

  @Test public void exactOwnedDuplicateIsShownOnceWhileSameSpeciesIndividualsRemain() throws Exception {
    JSONObject original = unit("a", "u01", 10);
    JSONArray source = new JSONArray().put(original).put(unit("b", "u01", 20))
        .put(unit("a", "u01", 60)).put(unit("c", "u01", 10));
    JSONArray ordered = NativeOwnedRoster.levelOrdered(source);
    assertEquals(Arrays.asList("b", "a", "c"), identities(ordered));
    assertSame("First authoritative entry wins; duplicate metadata cannot replace it", original,
        ordered.optJSONObject(1));
    assertEquals(4, source.length());
  }

  @Test public void missingIdentityIsNotCollapsedBySpeciesAndLegacyKeyIsExplicit() throws Exception {
    JSONArray malformed = new JSONArray().put(JSONObject.NULL).put("bad")
        .put(unit("", "u01", 99)).put(new JSONObject().put("speciesId", "u01"))
        .put(new JSONObject().put("ownedId", JSONObject.NULL))
        .put(new JSONObject().put("ownedId", 17))
        .put(unit("valid", "u01", 2));
    assertEquals(Arrays.asList("valid"), identities(NativeOwnedRoster.levelOrdered(malformed)));
    assertEquals(0, NativeOwnedRoster.levelOrdered(null).length());
    JSONObject first = new JSONObject().put("key", "individual-a").put("id", "D1").put("level", 2);
    JSONObject second = new JSONObject().put("key", "individual-b").put("id", "D1").put("level", 9);
    ArrayList<JSONObject> legacy = NativeOwnedRoster.levelOrderedCandidates(
        new JSONArray().put(first).put(second).put(first), "key");
    assertEquals(2, legacy.size());
    assertSame(second, legacy.get(0));
    assertSame(first, legacy.get(1));
  }
}
