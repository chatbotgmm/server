package org.dpdns.gmm;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AugmentPresentationTest {
  JSONObject augment(int version, String tier) throws Exception {
    return new JSONObject().put("version",version).put("tier",tier)
        .put("id","reverse").put("name","자유자재");
  }
  @Test public void allThreeGradesAreTextNotNameGuesses() throws Exception {
    assertEquals("실버",AugmentPresentation.tier(augment(2,"silver")));
    assertEquals("골드",AugmentPresentation.tier(augment(2,"gold")));
    assertEquals("프리즘",AugmentPresentation.tier(augment(2,"prismatic")));
    assertFalse(AugmentPresentation.prism(augment(2,"gold")));
    assertTrue(AugmentPresentation.prism(augment(2,"prismatic")));
  }
  @Test public void legacyAndMissingTierNeverInventPrism() throws Exception {
    assertEquals("",AugmentPresentation.tier(augment(1,"prismatic")));
    assertEquals("",AugmentPresentation.tier(new JSONObject().put("id","reverse")));
    assertEquals("등급 미확인",AugmentPresentation.tier(augment(2,"")));
    assertEquals("등급 미확인",AugmentPresentation.tier(augment(2,"future")));
    assertFalse(AugmentPresentation.prism(augment(2,"future")));
    assertFalse(AugmentPresentation.prism(augment(1,"prismatic")));
    assertEquals("",AugmentPresentation.tier(null));
  }
  @Test public void replayRoundTripPreservesLabelAndFullCostCap() throws Exception {
    JSONObject original=augment(2,"prismatic").put("desc","체력 20% 소모 · 최대 2회 · 3턴");
    JSONObject saved=new JSONObject(original.toString());
    assertEquals("자유자재 / 프리즘",AugmentPresentation.name(saved));
    assertEquals("체력 20% 소모 · 최대 2회 · 3턴",AugmentPresentation.detail(saved));
    assertEquals("자유자재",AugmentPresentation.name(augment(1,"")));
  }
}
