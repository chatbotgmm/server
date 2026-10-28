package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Full circumference evidence, not a selected-center mockup. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageContinuousReviewTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();final NativeHomePartyFeedbackTest helper=fixture.helper;
 @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 void settle(MainActivity h,NativeVillageHomeView v,NativeHomePartyFeedbackTest.Profile profile,double camera)throws Exception{
  v.moveCameraUnits(camera-v.camera.cameraUnits());for(int n=0;n<4;n++){LocalGameArtTest.drain();helper.layout(h,profile);}assertTrue("Viewport ready at "+camera,v.assets.ready());
 }
 final java.util.Set<String> captionConflicts=new java.util.LinkedHashSet<>();
 void assertNameplatesSeparate(NativeVillageHomeView v){
  java.util.List<android.graphics.RectF> boxes=new java.util.ArrayList<>();java.util.List<String> ids=new java.util.ArrayList<>();
  for(VillageLayout.Placed actor:v.depthActors){android.widget.Button button=v.actorButton(actor);if(button.getVisibility()!=android.view.View.VISIBLE)continue;android.graphics.RectF box=v.labelRect(new android.graphics.RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom()),button);if(!box.intersect(v.safe))continue;boxes.add(box);ids.add(actor.id);}
  if(v.owner.statueStage>0){android.graphics.RectF box=v.statueLabelRect();if(box.intersect(v.safe)){boxes.add(box);ids.add("statue");}}
  for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++)if(android.graphics.RectF.intersects(boxes.get(i),boxes.get(j)))captionConflicts.add(v.getWidth()+"x"+v.getHeight()+": "+ids.get(i)+" / "+ids.get(j));
 }
 @Test public void sharedFoundationRejectsWholeSceneFadeRepeatAndIndependentCoverScale()throws Exception{
  JSONObject data=NativeVillageHomeAssetsTest.manifest();VillageLayout layout=VillageLayout.parse(data);assertTrue(layout.continuousFoundation);assertEquals(7,layout.buildings.size());
  assertArrayEquals(new String[]{"normal","rank"},layout.building("training").actionIds);assertArrayEquals(new String[]{"explore","departure"},layout.building("harbor").actionIds);
  for(VillageLayout.Building b:layout.buildings)for(String action:b.actionIds)assertFalse("Pass has one top-right entry only","pass".equals(action));
  for(String field:new String[]{"fadeUnits","tileOverlapUnits","minimumViewportWidth","minimumWorldWidth"}){JSONObject bad=new JSONObject(data.toString());bad.getJSONArray("districtScenery").getJSONObject(0).put(field,.1);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(bad));}
  JSONObject bad=new JSONObject(data.toString());bad.getJSONArray("districtScenery").getJSONObject(0).put("repeatToDistrict",true);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(bad));
 }
 @Test public void captureEveryQuarterUnitAtBothProfilesAndVerifyExactEightUnitClosure()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)}){
   helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);v.ambient.fixedTime=0L;
    assertEquals(7,v.buildingButtons.size());assertEquals(4,v.residentButtons.size());assertNotNull(v.pass);assertNotNull(v.statue);
    for(int step=0;step<=32;step++){double camera=step*.25;settle(h,v,profile,camera);assertNameplatesSeparate(v);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
     try{String file=String.format(java.util.Locale.ROOT,"continuous-%s-u-%05.2f.png",profile.name,camera);NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(file),before);
      settle(h,v,profile,camera+8);Bitmap after=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Exact 8U closure "+profile.name+" "+camera,before.sameAs(after));}finally{after.recycle();}
     }finally{before.recycle();}
    }
   }
  }
  assertTrue("Image caption collisions: "+captionConflicts,captionConflicts.isEmpty());
 }
}
