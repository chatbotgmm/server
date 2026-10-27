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
    assertEquals(8,actual.worldUnits,0);assertEquals(650,actual.minimumUnitDp,0);assertEquals(48,actual.minimumTouchDp,0);
    assertEquals(4,actual.districts.size());assertEquals(6,actual.buildings.size());assertEquals(4,actual.residents.size());
    assertEquals("verified-local-assets",actual.artworkStatus);assertEquals("manifest-unavailable-placeholder",fallback.artworkStatus);
    JSONObject pinned=raw();assertEquals("2f1890e1df37b426cbfa27ccce8cd293186ad512",pinned.getString("sourceCommit"));assertEquals(34,pinned.getInt("sourceFileCount"));
    assertEquals(34,actual.assets.size());assertEquals(1,actual.decorations.size());
    assertEquals(13,fallback.assets.size());assertTrue(fallback.decorations.isEmpty());
    for(VillageLayout.Asset asset:actual.assets.values()){
      if(!asset.required)assertTrue(asset.id.startsWith("statue-stage-"));
      assertTrue("Production artwork must be inspected before binding: "+asset.id,asset.verified);
      JSONObject source=pinned.getJSONObject("assets").getJSONObject(asset.id);
      if(source.getString("sourcePath").contains("village-districts-20261028/")){assertTrue(source.getString("artReview").contains("pending"));assertTrue(asset.path.contains("districts-v4-"));assertTrue(source.getString("sourceGitBlobSha").matches("[a-f0-9]{40}"));}else if(source.getString("sourcePath").contains("village-companions-20261027/")){
        assertTrue(source.getString("artReview").contains("pending"));assertTrue(asset.path.contains("companions-v3-"));
      }else if(asset.id.equals("nameplates")){
        assertEquals(1024,source.getInt("sourceWidth"));assertEquals(1536,source.getInt("sourceHeight"));
      }else if(asset.id.startsWith("statue-stage-")){
        assertEquals(1024,source.getInt("sourceWidth"));assertEquals(1536,source.getInt("sourceHeight"));assertArrayEquals(new double[]{0,0,1,1},asset.crop,0);
      }else if(asset.id.equals("grove")||asset.id.equals("neighborhood")){
        assertEquals("49834c6b315aeecda0d4226c543995266466fb5f",source.getString("sourceCommit"));
        assertEquals(1536,source.getInt("sourceWidth"));assertEquals(1024,source.getInt("sourceHeight"));
      }else if(asset.id.startsWith("decor_")){
        assertEquals("78660ff70397434751a4aaa1b4fce85b10cc72ab",source.getString("sourceCommit"));
        assertEquals("decor/"+asset.id.substring("decor_".length())+".png",source.getString("sourceFile"));
        assertEquals("galmaegiman-confirmed/art/gpt/village-npc-20261027/"+source.getString("sourceFile"),source.getString("sourcePath"));
        assertEquals("native/village-loop-v2/assets/"+asset.id.replace('_','-')+".png",asset.path);
        assertNull("Geometry-only fallback has no optional decorative artwork",fallback.assets.get(asset.id));
      }else{
        assertEquals(pinned.getString("sourceCommit"),source.getString("sourceCommit"));
        assertEquals("galmaegiman-confirmed/art/gpt/village-npc-20261027/"+asset.id+".png",source.getString("sourcePath"));
        assertNotNull("Core fallback artwork keeps a placeholder reference",fallback.assets.get(asset.id));
        assertFalse("Missing-manifest fallback must never mark art as verified",fallback.assets.get(asset.id).verified);
      }
      assertTrue(source.getString("sourceSha256").matches("[a-f0-9]{64}"));
      assertTrue(source.getInt("sourceWidth")>0);assertTrue(source.getInt("sourceHeight")>0);assertTrue(source.getLong("sourceBytes")>0);assertFalse(asset.useSheetDirectly);
    }
    for(VillageLayout.Building building:actual.buildings){VillageLayout.Building copy=fallback.building(building.id);assertNotNull(copy);assertEquals(building.x,copy.x,0);assertEquals(building.width,copy.width,0);assertEquals(building.baseline,copy.baseline,0);assertArrayEquals(building.menu,copy.menu);assertArrayEquals(building.actionIds,copy.actionIds);}
    assertThrows(UnsupportedOperationException.class,()->actual.buildings.clear());
  }
  @Test public void exactBuildingsLeadToTheUsersRequestedMenusOnly()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());
    assertArrayEquals(new String[]{"recruit"},layout.building("recruit").actionIds);
    assertArrayEquals(new String[]{"party"},layout.building("hideout").actionIds);
    assertArrayEquals(new String[]{"collection","craft"},layout.building("archive").actionIds);
    assertArrayEquals(new String[]{"mail","events","notices"},layout.building("mailbox").actionIds);
    assertArrayEquals(new String[]{"explore","story","normal","rank"},layout.building("harbor").actionIds);
    assertArrayEquals(new String[]{"items","pass","currency"},layout.building("market").actionIds);
    assertEquals("방범대 지원소",layout.building("recruit").name);assertEquals("방범대 아지트",layout.building("hideout").name);
  }
  @Test public void allSixLayersAreSeparateAndRepeatIncludingMirroredSeaAndSky()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());assertEquals(6,layout.layers.size());
    assertEquals(.25,layout.layer("background").parallax,0);assertEquals(.5,layout.layer("sea").parallax,0);
    assertArrayEquals(new double[]{0,0,1,.51},layout.layer("background").sourceCrop,0);
    assertArrayEquals(new double[]{0,.51,1,1},layout.layer("sea").sourceCrop,0);
    for(VillageLayout.Layer layer:layout.layers)assertEquals(layout.worldUnits*layer.parallax,layer.repeatUnits,0);
    for(String id:new String[]{"background","sea","terrain"})assertTrue(layout.layer(id).mirrorAlternating);
    for(String id:new String[]{"buildings","ships","residents"})assertNotNull(layout.layer(id));
  }
  static boolean fallbackCore(String id){return VillageLayout.referenceLayout().assets.containsKey(id);}
  @Test public void pendingArtNeverClaimsVerifiedPixelsOrUsesTheWhiteResidentSheetDirectly()throws Exception{
    VillageLayout layout=VillageLayout.parse(NativeVillageHomeAssetsTest.pendingManifest());assertEquals("pending-verified-pixels",layout.artworkStatus);assertEquals(34,layout.assets.size());assertEquals(1,layout.decorations.size());
    for(VillageLayout.Asset asset:layout.assets.values()){assertFalse(asset.verified);assertFalse(asset.useSheetDirectly);if(fallbackCore(asset.id)){if(java.util.Arrays.asList("recruit","hideout","patrol","archive","market","harbor").contains(asset.id)){JSONObject source=raw().getJSONObject("assets").getJSONObject(asset.id);assertTrue(source.getString("sourcePath").contains("village-companions-20261027/")||source.getString("sourcePath").contains("village-districts-20261028/"));assertTrue(source.getString("sourceSha256").matches("[a-f0-9]{64}"));}else assertFalse(asset.sourceLibraryId.isEmpty());}}
    assertEquals("patrol",layout.resident("guard").asset);
    HashSet<String> silhouettes=new HashSet<>();for(VillageLayout.Resident resident:layout.residents)silhouettes.add(resident.silhouette);assertEquals(4,silhouettes.size());
    assertTrue(layout.residentPlacementIsProposal);assertFalse(layout.currencyHud);assertFalse(layout.cameraResetOnWrap);
  }
  @Test public void extensionWalkwaysRemainBetweenTheFourAreas()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw());
    double[] ends={layout.building("hideout").x+layout.building("hideout").width/2,layout.building("mailbox").x+layout.building("mailbox").width/2,layout.building("harbor").x+layout.building("harbor").width/2,layout.building("market").x+layout.building("market").width/2};
    double[] starts={layout.building("archive").x-layout.building("archive").width/2,layout.building("harbor").x-layout.building("harbor").width/2,layout.building("market").x-layout.building("market").width/2,8+layout.building("recruit").x-layout.building("recruit").width/2};
    for(int i=0;i<4;i++)assertTrue("Each district keeps a walkable expansion gap",starts[i]-ends[i]>.5);
  }
  @Test public void rejectedStyleCurrencyHudAndCameraResetCannotBeEnabledByManifest()throws Exception{
    for(String key:new String[]{"currencyHud","cameraResetOnWrap"}){JSONObject data=raw();data.put(key,true);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));}
    JSONObject data=raw();data.put("architectureStyle","blue-roofs-v1");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
  }
  @Test public void assetTraversalBadCropAndWrongParallaxPeriodFailClosed()throws Exception{
    for(String path:new String[]{"../sea.png","https://example.invalid/sea.png","native/village-loop-v2/assets/../sea.png"}){JSONObject data=raw();data.getJSONObject("assets").getJSONObject("sea").put("path",path);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));}
    JSONObject crop=raw();crop.getJSONArray("layers").getJSONObject(0).put("sourceCrop",new JSONArray(Arrays.asList(0,0,1,1.1)));assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(crop));
    JSONObject period=raw();period.getJSONArray("layers").getJSONObject(0).put("repeatUnits",4);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(period));
    JSONObject sheet=raw();sheet.getJSONObject("assets").getJSONObject("patrol").put("useSheetDirectly",true);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(sheet));
  }
  @Test public void sceneScaleRatiosAreExplicitAndInvalidOrMissingRatiosAreRejected()throws Exception{
    VillageLayout layout=VillageLayout.parse(raw()),fallback=VillageLayout.referenceLayout();
    String[] ids={"recruit","hideout","archive","market","mailbox","harbor"};double[] heights={.62,.54,.57,.62,.145,.65};
    for(int i=0;i<ids.length;i++){assertEquals(heights[i],layout.building(ids[i]).heightRatio,0);assertTrue(fallback.building(ids[i]).heightRatio>=.1&&fallback.building(ids[i]).heightRatio<=.75);}
    assertEquals(.30,layout.resident("blacksmith").heightRatio,0);assertEquals(.27,layout.resident("librarian").heightRatio,0);
    assertEquals(.30,layout.resident("guard").heightRatio,0);assertEquals(.27,layout.resident("merchant").heightRatio,0);
    JSONObject absent=raw();absent.getJSONArray("buildings").getJSONObject(0).remove("heightRatio");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(absent));
    JSONObject excessive=raw();excessive.getJSONArray("residents").getJSONObject(0).put("heightRatio",.8);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(excessive));
  }

}
