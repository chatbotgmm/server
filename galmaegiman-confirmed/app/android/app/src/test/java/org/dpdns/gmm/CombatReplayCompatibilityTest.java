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
public class CombatReplayCompatibilityTest {
  JSONObject frame() throws Exception {
    return new JSONObject().put("combatRulesEpoch",CombatReplayCompatibility.CURRENT_EPOCH)
        .put("replayAvailable",true);
  }
  JSONObject record(JSONObject first, JSONObject last) throws Exception {
    return new JSONObject().put("frames",new JSONArray().put(first).put(last));
  }
  @Test public void onlyEveryServerApprovedCurrentFrameCanPlay() throws Exception {
    assertTrue(CombatReplayCompatibility.playable(record(frame(),frame())));
    assertFalse(CombatReplayCompatibility.playable(record(new JSONObject(),frame())));
    assertFalse(CombatReplayCompatibility.playable(record(frame(),new JSONObject())));
    assertFalse(CombatReplayCompatibility.playable(record(frame().put("combatRulesEpoch","old-v2"),frame())));
    assertFalse(CombatReplayCompatibility.playable(record(frame(),frame().put("combatRulesEpoch","future"))));
    assertFalse(CombatReplayCompatibility.playable(record(frame(),frame().put("replayAvailable",false))));
    assertFalse(CombatReplayCompatibility.playable(record(frame(),frame().put("replayAvailable","true"))));
  }
  @Test public void blockedRecordIsNotDeletedOrRewritten() throws Exception {
    JSONObject old=record(new JSONObject().put("turn",1),new JSONObject().put("turn",7));
    String before=old.toString();assertFalse(CombatReplayCompatibility.playable(old));
    assertEquals(before,old.toString());
  }
  @Test(expected=IllegalArgumentException.class) public void directPlayerFramesFailClosed() throws Exception {
    CombatReplayCompatibility.requirePlayableFrames(record(new JSONObject(),new JSONObject()));
  }
}
