package org.dpdns.gmm;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TemporaryUnitPresentationTest {
  JSONObject loan(long expiry) throws Exception { return new JSONObject().put("temporary",true).put("expiresAt",expiry); }
  @Test public void missingMetadataNeverGraysPermanentSameSpecies() throws Exception {
    JSONObject permanent = new JSONObject().put("id","D1");
    assertFalse(TemporaryUnitPresentation.temporary(permanent));
    assertEquals("",TemporaryUnitPresentation.label(permanent,100));
    assertFalse(TemporaryUnitPresentation.temporary(new JSONObject().put("temporary","true")));
    assertFalse(TemporaryUnitPresentation.temporary(new JSONObject().put("expiresAt",5000)));
    assertTrue(TemporaryUnitPresentation.temporary(loan(5000).put("id","D1")));
  }
  @Test public void expiryBoundaryDoesNotRenewOrHideLoan() throws Exception {
    assertEquals("임시 / 1분 남음",TemporaryUnitPresentation.label(loan(1001),1000));
    assertEquals("임시 / 기간 만료",TemporaryUnitPresentation.label(loan(1000),1000));
    assertEquals("임시 / 기간 만료",TemporaryUnitPresentation.label(loan(999),1000));
    assertTrue(TemporaryUnitPresentation.temporary(loan(999)));
    assertEquals("임시 / 15일 남음",TemporaryUnitPresentation.label(loan(1000+1296000000L),1000));
  }
  @Test public void reloadAndOfflineNeedAuthoritativeClock() throws Exception {
    JSONObject original = loan(5000);
    JSONObject restored = new JSONObject(original.toString());
    assertEquals(TemporaryUnitPresentation.label(original,1000),TemporaryUnitPresentation.label(restored,1000));
    assertEquals("임시 / 기간 확인 필요",TemporaryUnitPresentation.label(restored,0));
    assertEquals("임시 / 기간 확인 필요",TemporaryUnitPresentation.label(new JSONObject().put("temporary",true),1000));
    assertEquals("임시 / 기간 확인 필요",TemporaryUnitPresentation.label(loan(-1),1000));
  }
  @Test public void subtleArtSaturationKeepsColor() { assertTrue(TemporaryUnitPresentation.SATURATION > .5f); assertTrue(TemporaryUnitPresentation.SATURATION < 1); }
  @Test public void aggregateCopyOnlyWhenAllCopiesAreTemporary() throws Exception {
    JSONObject aggregate = new JSONObject().put("id","T1").put("temporaryCount",1).put("permanentCount",0)
        .put("temporaryInstances",new org.json.JSONArray().put(loan(5000)));
    JSONObject presentation = TemporaryUnitPresentation.artUnit(aggregate);
    assertNotSame(aggregate,presentation);
    assertFalse(aggregate.has("temporary"));
    assertTrue(TemporaryUnitPresentation.temporary(presentation));
    assertEquals(5000,presentation.optLong("expiresAt"));
    aggregate.put("permanentCount",1);
    assertSame(aggregate,TemporaryUnitPresentation.artUnit(aggregate));
    assertFalse(TemporaryUnitPresentation.temporary(aggregate));
    assertEquals("임시 포함 1 / 영구 1",TemporaryUnitPresentation.label(aggregate,1000));
    aggregate.put("temporary",false);
    assertSame(aggregate,TemporaryUnitPresentation.artUnit(aggregate));
    assertEquals("",TemporaryUnitPresentation.label(aggregate,1000));
  }
}
