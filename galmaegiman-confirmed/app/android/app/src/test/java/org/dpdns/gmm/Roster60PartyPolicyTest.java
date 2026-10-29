package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE)
public class Roster60PartyPolicyTest {
  /** Literal verified server contract for unrelated transport fixtures too. */
  static JSONObject response(JSONObject value,boolean required){
    try{return value.put("minParty",1).put("maxParty",3).put("partyPolicy",MainActivity.obj("version","single-active-party-3-v1","minParty",1,"maxParty",3,"activeSlots",1,"sizeRule","up-to-max"))
        .put("partySelectionRequired",required).put("partyCondition",required?"PARTY_RESELECTION_REQUIRED":JSONObject.NULL);}catch(Exception bad){throw new AssertionError(bad);}
  }
  static JSONObject response(JSONObject value){JSONArray ids=value.optJSONArray("partyIds");return response(value,ids!=null&&(ids.length()<1||ids.length()>3));}
  static JSONObject battle(JSONObject value){response(value,false);value.remove("partySelectionRequired");value.remove("partyCondition");JSONArray rows=value.optJSONArray("encounters");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)try{row.put("partyPolicy",new JSONObject(value.optJSONObject("partyPolicy").toString()));}catch(Exception bad){throw new AssertionError(bad);}}return value;}
  @Test public void actualContractAcceptsOneToThreeAndNeverMutatesLegacyIds()throws Exception{
    Roster60PartyPolicy policy=Roster60PartyPolicy.read(response(new JSONObject(),false),true);
    JSONArray units=new JSONArray(),legacy=new JSONArray();for(int i=0;i<6;i++){units.put(MainActivity.obj("ownedId","id"+i,"speciesId","u01"));legacy.put("id"+i);}
    assertTrue(policy.ready);assertEquals(1,policy.minParty);assertEquals(3,policy.maxParty);
    for(int count=0;count<=6;count++){JSONArray ids=new JSONArray();for(int i=0;i<count;i++)ids.put("id"+i);assertEquals(count>=1&&count<=3,policy.allows(ids,units));}
    String untouched=legacy.toString();assertFalse(policy.allows(legacy,units));assertEquals(untouched,legacy.toString());assertEquals(6,policy.visibleSlots(legacy.length()));
    assertFalse(policy.allows(new JSONArray().put("id0").put("id0"),units));assertFalse(policy.allows(new JSONArray().put("foreign"),units));
  }
  @Test public void missingOldInconsistentAndUnsupportedPoliciesCannotAdmitNewParties()throws Exception{
    assertFalse(Roster60PartyPolicy.read(MainActivity.obj("maxParty",6),true).ready);
    JSONObject valid=response(new JSONObject(),false);valid.getJSONObject("partyPolicy").put("maxParty",6);assertFalse(Roster60PartyPolicy.read(valid,true).ready);
    valid=response(new JSONObject(),false);valid.put("maxParty",6);assertFalse(Roster60PartyPolicy.read(valid,true).ready);
    valid=response(new JSONObject(),false);valid.getJSONObject("partyPolicy").put("version","future-policy");assertFalse(Roster60PartyPolicy.read(valid,true).ready);
    valid=response(new JSONObject(),false);valid.getJSONObject("partyPolicy").put("activeSlots",2);assertFalse(Roster60PartyPolicy.read(valid,true).ready);
  }
  @Test public void selectionStateMustMatchItsConditionAndBattleOptionsNeedNoSavedSelection()throws Exception{
    JSONObject value=response(new JSONObject(),true);Roster60PartyPolicy policy=Roster60PartyPolicy.read(value,true);assertTrue(policy.ready);assertTrue(policy.selectionRequired);
    value.put("partyCondition",JSONObject.NULL);assertFalse(Roster60PartyPolicy.read(value,true).ready);
    value=response(new JSONObject(),false);value.remove("partyCondition");assertFalse(Roster60PartyPolicy.read(value,true).ready);
    value.remove("partySelectionRequired");assertTrue(Roster60PartyPolicy.read(value,false).ready);
  }
  @Test public void reselectionIsDefinitiveAndHasActionableClientNotice(){
    assertTrue(Roster60StoryJournal.definitive("PARTY_RESELECTION_REQUIRED"));assertTrue(NativeRoster60Pvp.partyRejection("PARTY_RESELECTION_REQUIRED"));
    assertFalse(Roster60StoryJournal.definitive("network"));assertFalse(NativeRoster60Pvp.partyRejection("network"));
    assertTrue(Roster60BattleErrorText.message("PARTY_RESELECTION_REQUIRED").contains("최대 3마리"));assertFalse(Roster60BattleErrorText.message("PARTY_RESELECTION_REQUIRED").equals("PARTY_RESELECTION_REQUIRED"));
  }
}
