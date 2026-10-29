package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Actual packaged PNG checks; pending artwork must fail rather than become a skipped success.
 * The active layered manifest has 25 required references and five authenticated-stage-only references.
 * Exact packaged source bytes/dimensions and alpha are checked without reintroducing retired scenery.
 * Technical verification never implies final user artwork approval.
 * Existing NativeVillageHomeTest covers routes, real gestures, delayed callbacks and owner changes.
 * These are host native-Skia captures, never physical-device or live-account evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageHomeArtTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  final NativeHomePartyFeedbackTest helper=fixture.helper;
  static final Set<String> ASSETS=new HashSet<>(Arrays.asList(
      "recruit","hideout","archive","mailbox","market","harbor","training",
      "blacksmith","merchant","librarian","patrol","market-forge","nameplates",
      "training-nameplate","harbor-nameplate","statue-nameplate","layered-sky",
      "layered-patrol","layered-approved","layered-market","layered-join-patrol-approved",
      "layered-join-approved-market","layered-join-market-patrol-wrap","layered-quay","companions-shadows"));
  static final Set<String> DECORATIONS=new HashSet<>(Arrays.asList("market-forge"));
  static final Set<String> NPCS=new HashSet<>(Arrays.asList("blacksmith","merchant","librarian","patrol"));

  @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}

  static Path output()throws Exception{
    Path path=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"actual-art");
    Files.createDirectories(path);return path;
  }
  static void json(Path target,Object value)throws Exception{
    Files.write(target,(value instanceof JSONObject?((JSONObject)value).toString(2):((JSONArray)value).toString(2))
        .getBytes(StandardCharsets.UTF_8));
  }
  static void png(Path target,Bitmap bitmap)throws Exception{
    try(OutputStream stream=Files.newOutputStream(target)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
  }
  static void requireVerified(VillageLayout layout){
    assertEquals("Real PNG import and pixel review must finish before this suite runs",
        "verified-local-assets",layout.artworkStatus);
    assertTrue("The active product world must use the layered layout",layout.layeredWorld);
    assertEquals("25 active references plus five optional statue stages",30,layout.assets.size());
    Set<String> required=new HashSet<>();for(VillageLayout.Asset a:layout.assets.values())if(a.required)required.add(a.id);assertEquals(ASSETS,required);
    Set<String> decorations=new HashSet<>();for(VillageLayout.Decoration d:layout.decorations)decorations.add(d.asset);assertEquals(DECORATIONS,decorations);
    for(VillageLayout.Asset asset:layout.assets.values()){
      if(!asset.required){assertTrue(asset.id.startsWith("statue-stage-"));assertTrue("All five technical candidates have inspected original pixels",asset.verified);continue;}
      assertTrue("Actual-art suite cannot accept an unverified reference: "+asset.id,asset.verified);
      assertFalse("An individual NPC PNG must replace the white-background sheet",asset.useSheetDirectly);
    }
    assertEquals("patrol",layout.resident("guard").asset);
  }
  static void requireFonts(MainActivity h)throws Exception{
    for(String path:new String[]{"native/Jua-Regular.ttf","native/GowunDodum-Regular.ttf"}){
      try(InputStream font=h.getAssets().open(path)){assertTrue("Bundled game font is required: "+path,font.read()>=0);}
    }
    assertNotNull(h.font);assertNotNull(h.prose);
  }
  NativeVillageHomeView openActual(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
    requireVerified(VillageLayout.load(h));requireFonts(h);
    NativeVillageHomeView view=fixture.open(h,profile);
    LocalGameArtTest.drain();helper.layout(h,profile);
    assertTrue("Real PNG decode did not finish; pending="+view.assets.pending+" missing="+view.assets.missing,
        view.assets.ready());
    assertTrue(view.assets.images.keySet().containsAll(view.assets.requiredImages));assertEquals(0,view.assets.pending);assertEquals(0,view.assets.missing);
    NativeVillageHomeTest.assertFullBleed(view,h);assertFalse(view.settings.getContentDescription().toString().contains("그림을 불러오는 중"));
    return view;
  }
  static JSONObject bitmapInventory(NativeVillageHomeView view)throws Exception{
    JSONArray rows=new JSONArray();long total=0;
    for(VillageLayout.Asset asset:view.layout.assets.values()){
      if(!asset.required&&!asset.id.equals(view.layout.landmark.asset(view.owner.statueStage))){assertNull("Future statue stages are not eagerly decoded",view.assets.get(asset.id));continue;}
      Bitmap bitmap=view.assets.get(asset.id);if(bitmap==null){assertFalse("Visible viewport asset may not be omitted: "+asset.id,view.assets.requiredImages.contains(asset.id));continue;}
      int width=bitmap.getWidth(),height=bitmap.getHeight();assertTrue(width>0&&height>0);
      long transparent=0,opaque=0,nonTransparent=0,transparentBorder=0;int[] row=new int[width];
      for(int y=0;y<height;y++){
        bitmap.getPixels(row,0,width,0,y,width,1);
        for(int x=0;x<width;x++){
          int alpha=Color.alpha(row[x]);
          if(alpha==0){transparent++;if(y==0||y==height-1||x==0||x==width-1)transparentBorder++;}
          if(alpha>0)nonTransparent++;
          if(alpha>=250)opaque++;
        }
      }
      assertTrue("A decoded image must contain visible art: "+asset.id,nonTransparent>0);
      if(NPCS.contains(asset.id)||DECORATIONS.contains(asset.id)){
        assertTrue("Individual NPC or decoration needs transparent margins rather than a white sheet: "+asset.id,
            bitmap.hasAlpha()&&transparent>0&&transparentBorder>0);
        assertTrue("Individual NPC or decoration must contain opaque content pixels: "+asset.id,opaque>0);
      }
      long bytes=bitmap.getAllocationByteCount();assertTrue(bytes>0);total+=bytes;
      rows.put(MainActivity.obj("id",asset.id,"packagedPath",asset.path,"decodedWidth",width,"decodedHeight",height,
          "sample",view.assets.sample(asset.path),"allocationBytes",bytes,"hasAlpha",bitmap.hasAlpha(),"transparentPixels",transparent,
          "nonTransparentPixels",nonTransparent,"opaquePixels",opaque,"transparentBorderPixels",transparentBorder));
    }
    return MainActivity.obj("environment","Robolectric SDK35 native graphics; host only","assetCount",rows.length(),
        "allReady",view.assets.ready(),"manifestAssetReferences",view.layout.assets.size(),"requiredAssetReferences",ASSETS.size(),"decorationAssetCount",DECORATIONS.size(),"layeredSceneryCount",view.layout.districtScenery.size(),"imageNameplateAtlasCount",2,"verifiedStatueStages",5,"userFinalStatueApproval","pending","residentAssetCount",4,"totalDecodedAllocationBytes",total,"sharedCacheBudgetBytes",LocalGameArt.budgetBytes(),
        "sharedCachedBytes",LocalGameArt.cachedBytes(),"viewKeepsStrongBitmapReferences",true,
        "originalFileShaVerification","activeSourceFilesKeepDeclaredBytesDimensionsAspectAndAlpha test; source-inventory.json","assets",rows);
  }


  @Test public void activeSourceFilesKeepDeclaredBytesDimensionsAspectAndAlpha()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();VillageLayout layout=VillageLayout.load(h);requireVerified(layout);
      JSONObject metadata=NativeVillageHomeAssetsTest.manifest().getJSONObject("assets");JSONArray inventory=new JSONArray();
      for(VillageLayout.Asset asset:layout.assets.values()){
        JSONObject source=metadata.getJSONObject(asset.id);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long byteCount=0;
        try(InputStream in=h.getAssets().open(asset.path)){
          byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){digest.update(buffer,0,count);byteCount+=count;}
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        assertEquals("Packaged original bytes: "+asset.id,source.getLong("sourceBytes"),byteCount);
        assertEquals("Packaged original hash: "+asset.id,source.getString("sourceSha256"),hash.toString());
        Bitmap original;
        try(InputStream in=h.getAssets().open(asset.path)){original=android.graphics.BitmapFactory.decodeStream(in);}
        assertNotNull("Every active reference independently decodes: "+asset.id,original);
        try{
          assertEquals("Original width: "+asset.id,source.getInt("sourceWidth"),original.getWidth());
          assertEquals("Original height: "+asset.id,source.getInt("sourceHeight"),original.getHeight());
          double aspect=original.getWidth()/(double)original.getHeight();
          assertEquals("No source stretching or replacement: "+asset.id,asset.sourceWidth/(double)asset.sourceHeight,aspect,1e-12);
          int[] row=new int[original.getWidth()];long visible=0,transparent=0,border=0,opaque=0;int minAlpha=255,maxAlpha=0;
          for(int y=0;y<original.getHeight();y++){
            original.getPixels(row,0,row.length,0,y,row.length,1);
            for(int x=0;x<row.length;x++){int a=Color.alpha(row[x]);minAlpha=Math.min(minAlpha,a);maxAlpha=Math.max(maxAlpha,a);
              if(a>0)visible++;else{transparent++;if(y==0||y==original.getHeight()-1||x==0||x==row.length-1)border++;}if(a>=250)opaque++;}
          }
          assertTrue("Original contains visible pixels: "+asset.id,visible>0);
          if(NPCS.contains(asset.id)||DECORATIONS.contains(asset.id)){
            assertTrue("Actor margins are actually transparent, not a white sheet: "+asset.id,original.hasAlpha()&&transparent>0&&border>0);
            assertTrue("Actor content is opaque: "+asset.id,opaque>0);
          }
          inventory.put(MainActivity.obj("id",asset.id,"path",asset.path,"required",asset.required,"sourceBytes",byteCount,
              "sha256",hash.toString(),"width",original.getWidth(),"height",original.getHeight(),"aspect",aspect,
              "alphaMin",minAlpha,"alphaMax",maxAlpha,"visiblePixels",visible,"transparentPixels",transparent,
              "transparentBorderPixels",border,"opaquePixels",opaque));
        }finally{original.recycle();}
      }
      assertEquals(30,inventory.length());json(output().resolve("source-inventory.json"),inventory);
    }
  }

  @Test public void verifiedPackagedPngsDecodeCoreAndCurrentStageOnlyAndIndividualNpcsAndDecorationsHaveTransparentMargins()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);
      NativeVillageHomeView view=openActual(h,NativeVillageHomeTest.PHONE);
      json(output().resolve("bitmap-inventory.json"),bitmapInventory(view));
      assertTrue("Art loading never creates a game command",api.writes.isEmpty());
    }
  }

  static JSONArray geometry(NativeVillageHomeView view,MainActivity h)throws Exception{
    JSONArray result=new JSONArray();
    NativeVillageHomeTest.assertFullBleed(view,h);
    for(View fixed:new View[]{view.settings,view.daily}){
      NativeNoScrollReviewTest.assertFullyVisible(fixed,h.root);
      if(fixed instanceof Button){assertTrue(fixed.getWidth()>=h.dp(48));assertTrue(fixed.getHeight()>=h.dp(48));}
      result.put(MainActivity.obj("kind","fixed","tag",String.valueOf(fixed.getTag()),"left",fixed.getLeft(),
          "top",fixed.getTop(),"right",fixed.getRight(),"bottom",fixed.getBottom()));
    }
    for(String id:view.buildingButtons.keySet())
      NativeVillageHomeTest.checkWorld(view,view.buildingButtons.get(id),view.buildingBounds.get(id),result);
    for(String id:view.residentButtons.keySet())
      NativeVillageHomeTest.checkWorld(view,view.residentButtons.get(id),view.residentBounds.get(id),result);
    NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(view);
    return result;
  }
  static long differentPixels(Bitmap before,Bitmap after){
    assertEquals(before.getWidth(),after.getWidth());assertEquals(before.getHeight(),after.getHeight());
    long different=0;int width=before.getWidth();int[] left=new int[width],right=new int[width];
    for(int y=0;y<before.getHeight();y++){
      before.getPixels(left,0,width,0,y,width,1);after.getPixels(right,0,width,0,y,width,1);
      for(int x=0;x<width;x++)if(left[x]!=right[x])different++;
    }
    return different;
  }
  static int unpaintedHorizontalRows(Bitmap bitmap,RectF field){
    int left=Math.max(0,(int)Math.ceil(field.left)),right=Math.min(bitmap.getWidth(),(int)Math.floor(field.right));
    int[] row=new int[Math.max(1,right-left)];int rows=0;
    for(int y=Math.max(0,(int)Math.ceil(field.top));y<Math.min(bitmap.getHeight(),(int)Math.floor(field.bottom));y++){
      bitmap.getPixels(row,0,row.length,left,y,row.length,1);int base=0;
      for(int pixel:row)if(pixel==0xffd8e7e4)base++;
      if(base>row.length*.8)rows++;
    }
    return rows;
  }

  @Test public void threeProfilesRenderRealArtAtAllEightPositionsAndEveryWholeTurnIsPixelIdentical()throws Exception{
    Path out=output();JSONArray frames=new JSONArray(),loops=new JSONArray();
    for(NativeHomePartyFeedbackTest.Profile profile:NativeVillageHomeTest.PROFILES){
      helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);
        NativeVillageHomeView view=openActual(h,profile);view.stopMotion();view.ambient.fixedTime=1400L;
        json(out.resolve(profile.name+"-bitmap-inventory.json"),bitmapInventory(view));
        for(int unit=0;unit<8;unit++){
          double camera=unit+.25;view.moveCameraUnits(camera-view.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          JSONArray bounds=null;RectF field=new RectF(view.field);
          Bitmap before=NativeHomePartyFeedbackTest.render(h.root),after=null;
          String name=profile.name+"-unit-"+unit;
          try{
            png(out.resolve(name+".png"),before);
            if(profile==NativeVillageHomeTest.PHONE){
              view.settings.setVisibility(View.INVISIBLE);view.daily.setVisibility(View.INVISIBLE);Bitmap panorama=null;
              try{panorama=NativeHomePartyFeedbackTest.render(h.root);png(out.resolve("phone-915x412-panorama-unit-"+unit+".png"),panorama);}
              finally{if(panorama!=null)panorama.recycle();view.settings.setVisibility(View.VISIBLE);view.daily.setVisibility(View.VISIBLE);}
            }
            bounds=geometry(view,h);
            int uncovered=unpaintedHorizontalRows(before,field);
            frames.put(MainActivity.obj("profile",profile.json(),"unit",unit,"cameraU",camera,"allArtReady",view.assets.ready(),
                "field",NativeHomePartyFeedbackTest.box(field.left,field.top,field.right,field.bottom),"unpaintedHorizontalRows",uncovered,"controls",bounds));
            json(out.resolve("geometry.json"),frames);
            assertEquals("Loaded sky, sea and terrain cover the world without a blank horizontal strip: "+name,0,uncovered);
            view.moveCameraUnits(view.layout.worldUnits);LocalGameArtTest.drain();helper.layout(h,profile);
            assertEquals(camera+8,view.camera.cameraUnits(),1e-9);
            after=NativeHomePartyFeedbackTest.render(h.root);boolean same=before.sameAs(after);
            long difference=same?0:differentPixels(before,after);
            loops.put(MainActivity.obj("profile",profile.name,"unit",unit,"cameraBefore",camera,"cameraAfter",view.camera.cameraUnits(),
                "worldUnits",view.layout.worldUnits,"bitmapSameAs",same,"differentPixels",difference,
                "layers","layered-sky,authored-panels,joins,quay,buildings,residents,statue,image-labels"));
            json(out.resolve("loop-pixels.json"),loops);
            if(!same)png(out.resolve(name+"-after-one-turn.png"),after);
            assertTrue("Actual village pixels changed after one whole turn: "+name+" differentPixels="+difference,same);
          }finally{before.recycle();if(after!=null)after.recycle();}
          view.moveCameraUnits(-view.layout.worldUnits);LocalGameArtTest.drain();assertEquals(camera,view.camera.cameraUnits(),1e-9);
        }
        if(profile==NativeVillageHomeTest.PHONE){
          String[] districts={"companions","memory","harbor","market"};
          double halfViewport=view.field.width()/view.unitPixels()/2;
          double[] positions={view.layout.resident("guard").x-halfViewport,view.layout.resident("librarian").x-halfViewport,
              view.layout.building("harbor").x-halfViewport,(view.layout.resident("blacksmith").x+view.layout.resident("merchant").x)/2-halfViewport};
          JSONArray districtFrames=new JSONArray();
          for(int i=0;i<positions.length;i++){
            view.moveCameraUnits(positions[i]-view.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);Bitmap image=NativeHomePartyFeedbackTest.render(h.root);
            try{assertEquals(915,image.getWidth());assertEquals(412,image.getHeight());png(out.resolve("phone-915x412-district-"+districts[i]+".png"),image);}finally{image.recycle();}
            geometry(view,h);JSONArray visible=new JSONArray();for(String id:view.residentButtons.keySet())if(view.residentButtons.get(id).isShown())visible.put(id);
            if(i==0)assertTrue(view.residentButtons.get("guard").isShown());if(i==1)assertTrue(view.residentButtons.get("librarian").isShown());if(i==3){NativeNoScrollReviewTest.assertFullyVisible(view.residentButtons.get("blacksmith"),h.root);NativeNoScrollReviewTest.assertFullyVisible(view.residentButtons.get("merchant"),h.root);}
            districtFrames.put(MainActivity.obj("district",districts[i],"cameraU",positions[i],"width",915,"height",412,"annotations",false,"outsidePadding",false,"visibleResidents",visible));
            json(out.resolve("district-frames.json"),districtFrames);
          }
        }
        assertTrue("Capture never accepts a quest, spends, or joins a match",api.writes.isEmpty());
      }
    }
    assertEquals(24,frames.length());assertEquals(24,loops.length());
  }
}
