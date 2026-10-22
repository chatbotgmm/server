package org.dpdns.gmm;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class SkillAttributeBreakdownTest {
  JSONObject preview(double skill, double own, double same, double product) throws Exception {
    return new JSONObject().put("multiplier",skill).put("innateMultiplier",own)
        .put("sameAttributeMultiplier",same).put("combinedAttributeMultiplier",product);
  }
  @Test public void displaysAuthoritativePartsForOtherAttributeMove() throws Exception {
    String text=SkillAttributeBreakdown.text(preview(1.5,1.1,1,1.65));
    assertTrue(text.contains("기술 상성 x1.5 / 내 속성 x1.1 / 일치 보너스 x1"));
    assertTrue(text.contains("속성 배율 곱 x1.65"));
  }
  @Test public void noLocalProductRecalculationOrHiddenCap() throws Exception {
    String text=SkillAttributeBreakdown.text(preview(1.9,1.1,1.2,2.5079999999999996));
    assertTrue(text.contains("속성 배율 곱 x2.508"));
    assertTrue(SkillAttributeBreakdown.text(preview(1,1,1,7)).contains("곱 x7"));
  }
  @Test public void oldMissingMalformedFieldsDoNotInventOne() throws Exception {
    for(JSONObject p:new JSONObject[]{null,new JSONObject().put("multiplier",1.5),
        preview(1,1,1,1).put("innateMultiplier","1.1"),preview(1,-1,1,1)}) {
      String text=SkillAttributeBreakdown.text(p);
      assertTrue(text.contains("기존 전투 미리보기"));assertFalse(text.contains("내 속성 x1"));
    }
  }
}
