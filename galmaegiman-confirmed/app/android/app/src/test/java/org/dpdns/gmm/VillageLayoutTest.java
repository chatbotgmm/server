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

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class VillageLayoutTest {
  JSONObject raw()throws Exception{
    try(InputStream input=RuntimeEnvironment.getApplication().getAssets().open(VillageLayout.MANIFEST)){
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int count;while((count=input.read(block))!=-1)bytes.write(block,0,count);
      return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
    }
  }
  @Test public void actualManifestAndGeometryOnlyFallbackKeepTheApprovedWideReference()throws Exception{
    VillageLayout actual=VillageLayout.load(RuntimeEnvironment.getApplication()),fallback=VillageLayout.referenceLayout();
    assertEquals(8,actual.worldUnits,0);assertEquals(360,actual.minimumUnitDp,0);assertEquals(48,actual.minimumTouchDp,0);
    assertEquals(4,actual.districts.size());assertEquals(7,actual.buildings.size());assertEquals(4,actual.residents.size());
    assertEquals("verified-local-assets",actual.artworkStatus);assertEquals("manifest-unavailable-placeholder",fallback.artworkStatus);
    JSONObject pinned=raw();assertTrue(actual.layeredWorld);assertEquals(29,actual.assets.size());assertEquals(1,actual.decorations.size());
    assertEquals(13,fallback.assets.size());assertTrue(fallback.decorations.isEmpty());
    for(VillageLayout.Asset asset:actual.assets.values()){
      if(!asset.required)assertTrue(asset.id.startsWith("statue-stage-"));
      assertTrue("Original pixels inspected before binding: "+asset.id,asset.verified);
      JSONObject source=pinned.getJSONObject("assets").getJSONObject(asset.id);
      assertTrue(source.getString("sourcePath").startsWith("galmaegiman-confirmed/art/gpt/"));
      assertTrue(source.getString("sourceSha256").matches("[a-f0-9]{64}"));
      assertTrue(source.getInt("sourceWidth")>0);assertTrue(source.getInt("sourceHeight")>0);assertTrue(source.getLong("sourceBytes")>0);assertFalse(asset.useSheetDirectly);
      try(InputStream input=RuntimeEnvironment.getApplication().getAssets().open(asset.path)){
        byte[] bytes=input.readAllBytes();assertEquals(source.getLong("sourceBytes"),bytes.length);
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));assertEquals(source.getString("sourceSha256"),digest);
      }
      if(asset.id.startsWith("statue-stage-")){assertEquals(1024,source.getInt("sourceWidth"));assertEquals(1536,source.getInt("sourceHeight"));assertArrayEquals(new double[]{0,0,1,1},asset.crop,0);}
      if(asset.id.equals("archive")){assertArrayEquals(new double[]{0,0,1,1},asset.crop,0);assertEquals("layered-approved",actual.building("archive").canvasLayer);}
      if(fallback.assets.containsKey(asset.id))assertFalse(fallback.assets.get(asset.id).verified);
    }
    assertEquals(6,fallback.buildings.size());assertFalse(fallback.continuousFoundation);assertTrue(actual.continuousFoundation);
    for(VillageLayout.Building building:actual.buildings){assertTrue(building.x>=0&&building.x<8);assertTrue(building.actionIds.length>0);}
    assertThrows(UnsupportedOperationException.class,()->actual.buildings.clear());
  }
  @Test public void exactBuildingsLeadToTheUsersRequestedMenusOnly()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());
    assertArrayEquals(new String[]{"recruit"},layout.building("recruit").actionIds);
    assertArrayEquals(new String[]{"party"},layout.building("hideout").actionIds);
    assertArrayEquals(new String[]{"collection","craft"},layout.building("archive").actionIds);
    assertArrayEquals(new String[]{"mail","events","notices"},layout.building("mailbox").actionIds);
    assertArrayEquals(new String[]{"explore","departure"},layout.building("harbor").actionIds);
    assertArrayEquals(new String[]{"items","currency"},layout.building("market").actionIds);
    assertArrayEquals(new String[]{"normal","rank"},layout.building("training").actionIds);
    assertEquals("방범대 지원소",layout.building("recruit").name);assertEquals("방범대 아지트",layout.building("hideout").name);
  }
  @Test public void layeredWorldUsesOnePhysicalScaleWithoutMirroredOrFadedScenes()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());assertEquals(6,layout.layers.size());
    for(VillageLayout.Layer layer:layout.layers){assertTrue(layer.asset.isEmpty());assertFalse(layer.mirrorAlternating);}
    for(VillageLayout.DistrictScenery scenery:layout.districtScenery){assertTrue(scenery.worldWidth>0);assertEquals(1,scenery.heightRatio,0);assertEquals(0,scenery.fadeUnits,0);assertFalse(scenery.repeatToDistrict);}
    VillageLayout fallback=VillageLayout.referenceLayout();assertFalse(fallback.layeredWorld);
    for(VillageLayout.Layer layer:fallback.layers)assertEquals(fallback.worldUnits*layer.parallax,layer.repeatUnits,0);
  }
  static boolean fallbackCore(String id){return VillageLayout.referenceLayout().assets.containsKey(id);}
  @Test public void pendingArtNeverClaimsVerifiedPixelsOrUsesTheWhiteResidentSheetDirectly()throws Exception{
    VillageLayout layout=VillageLayout.parse(NativeVillageHomeAssetsTest.pendingManifest());assertEquals("pending-verified-pixels",layout.artworkStatus);assertEquals(29,layout.assets.size());assertEquals(1,layout.decorations.size());
    for(VillageLayout.Asset asset:layout.assets.values()){assertFalse(asset.verified);assertFalse(asset.useSheetDirectly);JSONObject source=raw().getJSONObject("assets").getJSONObject(asset.id);assertTrue(source.getString("sourceSha256").matches("[a-f0-9]{64}"));}
    assertEquals("patrol",layout.resident("guard").asset);
    HashSet<String> silhouettes=new HashSet<>();for(VillageLayout.Resident resident:layout.residents)silhouettes.add(resident.silhouette);assertEquals(4,silhouettes.size());
    assertTrue(layout.residentPlacementIsProposal);assertFalse(layout.currencyHud);assertFalse(layout.cameraResetOnWrap);
  }
  @Test public void interactivePlacesAreDistributedWithoutAnEmptyScreenBetweenDistricts()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());java.util.List<double[]> intervals=new java.util.ArrayList<>();
    for(VillageLayout.Building actor:layout.buildings)intervals.add(new double[]{actor.x-actor.width/2,actor.x+actor.width/2});
    for(VillageLayout.Resident actor:layout.residents)intervals.add(new double[]{actor.x-actor.width/2,actor.x+actor.width/2});
    intervals.add(new double[]{layout.landmark.x-layout.landmark.width/2,layout.landmark.x+layout.landmark.width/2});
    intervals.sort(java.util.Comparator.comparingDouble(a->a[0]));double right=intervals.get(0)[1];
    for(int i=1;i<intervals.size();i++){double[] next=intervals.get(i);assertTrue("No full 1U empty interval between visible interactive silhouettes",next[0]-right<1);right=Math.max(right,next[1]);}
    assertTrue("Wrap also has no 1U empty interval",intervals.get(0)[0]+8-right<1);
  }
  @Test public void rejectedStyleCurrencyHudAndCameraResetCannotBeEnabledByManifest()throws Exception{
    for(String key:new String[]{"currencyHud","cameraResetOnWrap"}){JSONObject data=raw();data.put(key,true);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));}
    JSONObject data=raw();data.put("architectureStyle","blue-roofs-v1");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
  }
  @Test public void assetTraversalBadCropAndWrongParallaxPeriodFailClosed()throws Exception{
    for(String path:new String[]{"../sea.png","https://example.invalid/sea.png","native/village-loop-v2/assets/../sea.png"}){JSONObject data=raw();data.getJSONObject("assets").getJSONObject("layered-sky").put("path",path);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));}
    JSONObject crop=raw();crop.getJSONArray("layers").getJSONObject(0).put("sourceCrop",new JSONArray(Arrays.asList(0,0,1,1.1)));assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(crop));
    JSONObject period=raw();period.getJSONArray("layers").getJSONObject(0).put("repeatUnits",4);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(period));
    JSONObject sheet=raw();sheet.getJSONObject("assets").getJSONObject("patrol").put("useSheetDirectly",true);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(sheet));
  }
  @Test public void sceneScaleRatiosAreExplicitAndInvalidOrMissingRatiosAreRejected()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw()),fallback=VillageLayout.referenceLayout();
    String[] ids={"recruit","hideout","archive","market","mailbox","harbor"};double[] heights={.4883188211654387,.5970471633629528,.8080110497237569,.595721476510067,.16,.5136245819397994};
    for(int i=0;i<ids.length;i++){assertEquals(heights[i],layout.building(ids[i]).heightRatio,0);assertTrue(fallback.building(ids[i]).heightRatio>=.1&&fallback.building(ids[i]).heightRatio<=.75);}
    assertEquals(.24,layout.resident("blacksmith").heightRatio,0);assertEquals(.21,layout.resident("librarian").heightRatio,0);
    assertEquals(.23,layout.resident("guard").heightRatio,0);assertEquals(.23,layout.resident("merchant").heightRatio,0);
    JSONObject absent=raw();absent.getJSONArray("buildings").getJSONObject(0).remove("heightRatio");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(absent));
    JSONObject excessive=raw();excessive.getJSONArray("residents").getJSONObject(0).put("heightRatio",.8);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(excessive));
  }

  @Test public void invalidLayeredWidthAndUnknownFullCanvasParentAreRejected()throws Exception{
    for(double value:new double[]{0,-1,3.1,Double.NaN}){JSONObject data=raw();data.getJSONArray("districtScenery").getJSONObject(0).put("worldWidth",Double.isNaN(value)?"NaN":value);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));}
    JSONObject missing=raw();missing.getJSONArray("buildings").getJSONObject(2).put("canvasLayer","unknown");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(missing));
  }
}
