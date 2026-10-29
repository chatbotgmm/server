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
@Config(sdk=35,manifest=Config.NONE)
public class NativeOwnedRosterGroupsTest {
  static JSONObject unit(String ownedId,String speciesId,int level)throws Exception{return new JSONObject().put("ownedId",ownedId).put("speciesId",speciesId).put("level",level).put("name",speciesId+" 대원");}
  static List<String> species(List<NativeOwnedRosterGroups.Group> groups){ArrayList<String> result=new ArrayList<>();for(NativeOwnedRosterGroups.Group group:groups)result.add(group.speciesId);return result;}
  static List<String> ids(JSONArray ids){ArrayList<String> result=new ArrayList<>();for(int i=0;i<ids.length();i++)result.add(ids.optString(i));return result;}

  @Test public void sameSpeciesKeepsEveryOwnedIndividualAndDeduplicatesOnlyExactOwnedId()throws Exception{
    JSONObject low=unit("a","u01",12),high=unit("b","u01",60),tied=unit("d","u01",60);
    JSONArray source=new JSONArray().put(low).put(high).put(unit("c","u02",20)).put(unit("b","u02",99)).put(tied);
    String original=source.toString();ArrayList<NativeOwnedRosterGroups.Group> groups=NativeOwnedRosterGroups.group(source);
    assertEquals(Arrays.asList("u01","u02"),species(groups));
    NativeOwnedRosterGroups.Group first=groups.get(0);assertEquals(3,first.count());assertEquals(60,first.highestLevel);
    assertEquals("최고 Lv.60 · 보유 3명",first.summary());assertEquals(Arrays.asList("b","d","a"),ids(first.ownedIds()));
    assertSame(high,first.representative());assertSame(tied,first.members.get(1));assertSame(low,first.members.get(2));
    assertEquals(4,groups.get(0).count()+groups.get(1).count());assertEquals(original,source.toString());
    try{first.members.clear();fail("A UI picker cannot remove grouped catalogue records");}catch(UnsupportedOperationException expected){}
  }

  @Test public void groupsSortByHighestLevelAndEqualHighestLevelsKeepFirstSpeciesOccurrence()throws Exception{
    JSONArray source=new JSONArray().put(unit("a-low","u01",1)).put(unit("b-high","u02",40))
        .put(unit("c-high","u03",55)).put(unit("a-high","u01",40)).put(unit("b-low","u02",9)).put(unit("d-high","u04",40));
    ArrayList<NativeOwnedRosterGroups.Group> groups=NativeOwnedRosterGroups.group(source);
    assertEquals(Arrays.asList("u03","u01","u02","u04"),species(groups));
    assertEquals(Arrays.asList("a-high","a-low"),ids(groups.get(1).ownedIds()));
    assertEquals(2,groups.get(1).count());assertEquals(2,groups.get(2).count());assertEquals(1,groups.get(3).count());
  }

  @Test public void missingOwnedIdsAreExcludedButUnknownSpeciesRemainSeparateIndividuals()throws Exception{
    JSONObject orphanA=new JSONObject().put("ownedId","orphan-a").put("name","종족 미확인 A").put("level",3);
    JSONObject orphanB=new JSONObject().put("ownedId","orphan-b").put("speciesId",JSONObject.NULL).put("level",7);
    JSONArray source=new JSONArray().put(JSONObject.NULL).put("bad").put(unit("","u01",99))
        .put(new JSONObject().put("ownedId",17).put("speciesId","u01"))
        .put(new JSONObject().put("speciesId","u01")).put(orphanA).put(orphanB)
        .put(unit("named","u01",9)).put(unit("orphan-a","u01",99));
    ArrayList<NativeOwnedRosterGroups.Group> groups=NativeOwnedRosterGroups.group(source);
    assertEquals(3,groups.size());assertEquals(Arrays.asList("u01","",""),species(groups));
    assertEquals(Arrays.asList("orphan-b"),ids(groups.get(1).ownedIds()));assertEquals(Arrays.asList("orphan-a"),ids(groups.get(2).ownedIds()));
    assertNotEquals(groups.get(1).key,groups.get(2).key);assertEquals("종족 미확인 A",groups.get(2).name);
    assertTrue(NativeOwnedRosterGroups.group(null).isEmpty());
    assertTrue("Species identity never replaces a missing owned identity",NativeOwnedRosterGroups.group(new JSONArray().put(new JSONObject().put("speciesId","u01"))).isEmpty());
  }

  @Test public void legacyStoryExplicitKeysStillSelectDistinctOwnedIndividuals()throws Exception{
    JSONObject one=new JSONObject().put("key","key-one").put("id","D1").put("level",1).put("name","갈매미맨");
    JSONObject two=new JSONObject().put("key","key-two").put("id","D1").put("level",30).put("name","갈매미맨");
    JSONObject three=new JSONObject().put("key","key-three").put("id","D2").put("level",20);
    JSONArray source=new JSONArray().put(one).put(two).put(three).put(one);
    ArrayList<NativeOwnedRosterGroups.Group> groups=NativeOwnedRosterGroups.group(source,"key","id");
    assertEquals(Arrays.asList("D1","D2"),species(groups));assertEquals(2,groups.get(0).count());
    assertEquals(Arrays.asList("key-two","key-one"),ids(groups.get(0).ownedIds()));
    assertFalse(ids(groups.get(0).ownedIds()).contains("D1"));assertTrue(NativeOwnedRosterGroups.group(source).isEmpty());
  }
}
