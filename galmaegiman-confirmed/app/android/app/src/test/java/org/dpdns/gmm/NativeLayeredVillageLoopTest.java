package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Actual production renderer. Authored perspectives and every loop boundary require image review. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeLayeredVillageLoopTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
 final NativeHomePartyFeedbackTest helper=fixture.helper;
 @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 void settle(MainActivity h,NativeVillageHomeView view,NativeHomePartyFeedbackTest.Profile profile,double camera)throws Exception{
  view.moveCameraUnits(camera-view.camera.cameraUnits());helper.layout(h,profile);
  for(int i=0;i<5;i++){LocalGameArtTest.drain();helper.layout(h,profile);}
  assertTrue("All visible art must be ready at "+camera,view.sceneReady());
 }
 @Test public void everyQuarterUnitAndExactFullLoopAtThreeAspectRatios()throws Exception{
  NativeVillageContinuousReviewTest captions=new NativeVillageContinuousReviewTest();
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,
    new NativeHomePartyFeedbackTest.Profile("layered-tablet-1280x720",1280,720,160,1,0,0,0,0),
    new NativeHomePartyFeedbackTest.Profile("layered-user-2048x838",2048,838,420,1,73,26,26,26)}){
   helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView view=fixture.open(h,profile);view.ambient.fixedTime=1250L;
    assertTrue(view.layout.layeredWorld);assertEquals(7,view.buildingButtons.size());assertEquals(4,view.residentButtons.size());
    assertEquals(profile.height,view.unitPixels(),0);
    for(int step=0;step<=32;step++){
     double camera=step*.25;settle(h,view,profile,camera);captions.assertNameplatesSeparate(view);Bitmap first=NativeHomePartyFeedbackTest.render(h.root);
     try{
      NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(profile.name+String.format(java.util.Locale.ROOT,"-layered-u%05.2f.png",camera)),first);
      settle(h,view,profile,camera+8);assertEquals(camera+8,view.camera.cameraUnits(),0);
      Bitmap second=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Exact 8U closure at "+camera+" "+profile.name,first.sameAs(second));}finally{second.recycle();}
      settle(h,view,profile,camera-8);assertEquals(camera-8,view.camera.cameraUnits(),0);
      Bitmap previous=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Exact backward closure at "+camera,first.sameAs(previous));}finally{previous.recycle();}
     }finally{first.recycle();}
    }
   }
  }
  assertTrue("No caption collisions across all three profiles: "+captions.captionConflicts,captions.captionConflicts.isEmpty());
 }
 @Test public void runtimePackContainsOnlyManifestVillageImages()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();VillageLayout layout=VillageLayout.parse(NativeVillageHomeAssetsTest.manifest());
   Set<String> expected=new TreeSet<>(),actual=new TreeSet<>();
   for(VillageLayout.Asset asset:layout.assets.values())expected.add(asset.path);
   for(String directory:new String[]{"native/village-loop-v2/assets","native/village-loop-v2/residents"}){
    String[] names=h.getAssets().list(directory);assertNotNull(directory,names);
    for(String name:names)actual.add(directory+"/"+name);
   }
   assertEquals("Retired village panoramas must not be bundled alongside the layered world",expected,actual);
  }
 }
 @Test public void intervalLoaderNeverRequestsRetiredWorldOrOffscreenPanels()throws Exception{
  VillageLayout layout=VillageLayout.parse(NativeVillageHomeAssetsTest.manifest());assertTrue(layout.layeredWorld);
  List<String> requests=new ArrayList<>();Bitmap pixel=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
  NativeVillageHomeAssets assets=new NativeVillageHomeAssets((path,ready)->{requests.add(path);ready.accept(pixel);});
  assets.setViewport(0,1.8);assets.load(layout,()->true,()->{});
  assertTrue(assets.ready());
  assertFalse("The opposite approved panorama is not pinned at initial patrol view",assets.wantedImages.contains("layered-approved"));
  int count=requests.size();for(int i=0;i<60;i++)assets.setViewport(.001*i,1.8);
  assertEquals("Unchanged visibility does not restart IO",count,requests.size());
  for(int i=0;i<=32;i++){assets.setViewport(i*.25,2.45);assertTrue(assets.ready());}
  for(String path:requests){
   assertFalse(path,path.endsWith("continuous-v5-ground.png")||path.endsWith("continuous-v5-sky.png")
     ||path.endsWith("continuous-v5-harbor-open.png")||path.contains("companions-v3-background")
     ||path.contains("districts-v4-records-background")||path.endsWith("/terrain.png")||path.endsWith("/sea.png"));
  }
  assertTrue("Travel requests the active approved panorama declared by the manifest",requests.contains(layout.assets.get("layered-approved").path));
 }
}
