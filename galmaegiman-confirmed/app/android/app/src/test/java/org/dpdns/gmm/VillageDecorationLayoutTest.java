package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Non-interactive decoration definitions; no image readiness or rendering is inferred here. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class VillageDecorationLayoutTest {
  static final String PLAN="native/village-loop-v2/decor-planning.json";
  JSONObject read(String path)throws Exception{
    try(InputStream input=RuntimeEnvironment.getApplication().getAssets().open(path)){
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
      while((n=input.read(block))!=-1)bytes.write(block,0,n);
      return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
    }
  }
  JSONObject base()throws Exception{JSONObject data=read(VillageLayout.MANIFEST);data.remove("decorations");return data;}
  void asset(JSONObject data,String id,String path)throws Exception{
    data.getJSONObject("assets").put(id,new JSONObject().put("path",path).put("verified",false)
        .put("useSheetDirectly",false).put("crop",new JSONArray(Arrays.asList(0,0,1,1))));
  }
  JSONObject decoration(String id,String asset,double x)throws Exception{
    return new JSONObject().put("id",id).put("asset",asset).put("x",x).put("width",.20)
        .put("baseline",.71).put("parallax",1);
  }
  JSONObject fixture()throws Exception{
    JSONObject data=base();asset(data,"test-decor-cottage","native/village-loop-v2/assets/decor-cottage.png");
    asset(data,"test-decor-warehouse","native/village-loop-v2/assets/decor-warehouse.png");
    asset(data,"test-decor-shop","native/village-loop-v2/assets/decor-closed-shop.png");
    return data.put("decorations",new JSONArray().put(decoration("test-cottage","test-decor-cottage",1.6))
        .put(decoration("test-warehouse","test-decor-warehouse",3.5))
        .put(decoration("test-shop","test-decor-shop",7.88)));
  }
  @Test public void omittedOrEmptyDecorationListPreservesTheExistingScene()throws Exception{
    JSONObject data=base();int assets=data.getJSONObject("assets").length();
    for(boolean declared:new boolean[]{false,true}){
      if(declared)data.put("decorations",new JSONArray());
      VillageLayout layout=VillageLayout.parse(data);
      assertTrue(layout.decorations.isEmpty());assertNull(layout.decoration("missing"));
      assertEquals(assets,layout.assets.size());assertEquals(6,layout.layers.size());
      assertEquals(6,layout.buildings.size());assertEquals(4,layout.residents.size());
      assertArrayEquals(new String[]{"recruit"},layout.building("recruit").actionIds);
      assertEquals(.76,layout.resident("guard").x,0);assertEquals("patrol",layout.resident("guard").asset);
    }
    VillageLayout fallback=VillageLayout.referenceLayout();assertEquals(13,fallback.assets.size());
    assertTrue(fallback.decorations.isEmpty());for(VillageLayout.Asset asset:fallback.assets.values())assertFalse(asset.verified);
  }
  @Test public void smallDedicatedDecorationsParseAsAnImmutableSeparateList()throws Exception{
    VillageLayout layout=VillageLayout.parse(fixture());assertEquals(3,layout.decorations.size());
    assertEquals("test-decor-cottage",layout.decoration("test-cottage").asset);
    assertEquals(1.6,layout.decoration("test-cottage").x,0);
    assertEquals(.20,layout.decoration("test-cottage").width,0);
    for(VillageLayout.Decoration d:layout.decorations){assertEquals(1,d.parallax,0);assertNull(layout.building(d.id));assertNull(layout.resident(d.id));assertFalse(layout.assets.get(d.asset).verified);}
    assertThrows(UnsupportedOperationException.class,()->layout.decorations.clear());
  }
  @Test public void decorationMetadataCannotAddLabelsMenusQuestsOrHitTargets()throws Exception{
    for(String key:new String[]{"name","sub","label","menu","actionIds","questId","exclamation","clickable","hitbox","role","silhouette","onClick"}){
      JSONObject data=fixture();data.getJSONArray("decorations").getJSONObject(0).put(key,false);
      assertThrows("Decoration field must be rejected: "+key,IllegalArgumentException.class,()->VillageLayout.parse(data));
    }
  }
  @Test public void invalidGeometryAndNonUnitParallaxFailClosed()throws Exception{
    Object[][] invalid={{"width",0},{"width",.36},{"x",-.01},{"x",8},{"baseline",-.01},{"baseline",1.01},{"parallax",.9},{"parallax",0},{"width","small"}};
    for(Object[] change:invalid){
      JSONObject data=fixture();data.getJSONArray("decorations").getJSONObject(0).put((String)change[0],change[1]);
      assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
    }
    JSONObject data=fixture();data.getJSONArray("decorations").getJSONObject(0).remove("parallax");
    assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
    for(Object wrong:new Object[]{JSONObject.NULL,"decorations",new JSONObject()}){
      JSONObject typed=base().put("decorations",wrong);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(typed));
    }
  }
  @Test public void duplicateIdsAndExistingSceneSilhouettesCannotBecomeDecorations()throws Exception{
    for(String asset:new String[]{"recruit","patrol","ship","sea","terrain","missing-asset"}){
      JSONObject data=fixture();data.getJSONArray("decorations").getJSONObject(0).put("asset",asset);
      assertThrows("Main artwork must not be copied into decorative menus: "+asset,IllegalArgumentException.class,()->VillageLayout.parse(data));
    }
    for(String id:new String[]{"recruit","guard","ship","test-warehouse"}){
      JSONObject data=fixture();data.getJSONArray("decorations").getJSONObject(0).put("id",id);
      assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
    }
  }
  static double clearance(double ax,double aw,double bx,double bw,double world){
    double distance=Math.abs(ax-bx);distance=Math.min(distance,world-distance);return distance-(aw+bw)/2;
  }
  @Test public void placementProposalKeepsNominalMenuAndResidentCorridorsAcrossTheLoop()throws Exception{
    JSONObject plan=read(PLAN),data=base();assertFalse(plan.getBoolean("appliesAtRuntime"));
    assertTrue(plan.getBoolean("requiresRuntimeAndPixelValidation"));assertEquals(8,plan.getDouble("worldUnits"),0);
    JSONObject proposedAssets=plan.getJSONObject("proposedAssets");
    for(String id:new String[]{"decor_cottage","decor_warehouse","decor_closed_shop"})asset(data,id,proposedAssets.getJSONObject(id).getString("apkPath"));
    data.put("decorations",new JSONArray(plan.getJSONArray("proposedDecorations").toString()));
    VillageLayout layout=VillageLayout.parse(data);assertEquals(8,layout.decorations.size());
    int[] districtCount=new int[4];double footprint=0;HashSet<String> types=new HashSet<>();
    for(VillageLayout.Decoration d:layout.decorations){
      districtCount[(int)(d.x/2)]++;footprint+=d.width;types.add(d.asset);
      assertTrue(d.width>=.16&&d.width<=.24);assertTrue(d.baseline>=.70&&d.baseline<=.73);
      for(VillageLayout.Building b:layout.buildings)assertTrue("Nominal building corridor: "+d.id+" / "+b.id,clearance(d.x,d.width,b.x,b.width,8)>=.10);
      for(VillageLayout.Resident r:layout.residents)assertTrue("Nominal resident corridor: "+d.id+" / "+r.id,clearance(d.x,d.width,r.x,r.width,8)>=.10);
    }
    assertArrayEquals(new int[]{2,2,2,2},districtCount);assertEquals(3,types.size());assertTrue(footprint<=8*.20);
    for(int i=0;i<layout.decorations.size();i++)for(int j=i+1;j<layout.decorations.size();j++){
      VillageLayout.Decoration a=layout.decorations.get(i),b=layout.decorations.get(j);
      assertTrue("Nominal decorative breathing space including the wrap",clearance(a.x,a.width,b.x,b.width,8)>=.10);
    }
    // These are normalized proposal intervals, not proof of actual alpha overlap or final artwork readiness.
  }
}
