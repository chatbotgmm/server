package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real native bitmap compositing, no screenshot mockup or artwork approval claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageFourDistrictReviewTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();final NativeHomePartyFeedbackTest helper=fixture.helper;
 @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 void settle(MainActivity h,NativeVillageHomeView v,NativeHomePartyFeedbackTest.Profile profile,double camera)throws Exception{
  v.moveCameraUnits(camera-v.camera.cameraUnits());helper.layout(h,profile);
  for(int i=0;i<4;i++){LocalGameArtTest.drain();helper.layout(h,profile);}
  assertTrue("Current viewport must be ready at "+camera+" missing="+v.assets.missing+" pending="+v.assets.pending,v.assets.ready());
 }
 @Test public void optionalSurfaceAndWorldCoverageMetadataFailClosed()throws Exception{
  JSONObject data=NativeVillageHomeAssetsTest.manifest();data.getJSONArray("districtScenery").getJSONObject(0).put("minimumWorldWidth",3);
  assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
  data.getJSONArray("districtScenery").getJSONObject(0).put("minimumWorldWidth",0);data.getJSONObject("landmark").put("surface","unknown").put("sourceX",.5).put("sourceY",.8);
  assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
  JSONObject invalid=NativeVillageHomeAssetsTest.manifest();invalid.getJSONArray("decorations").getJSONObject(0).put("surface","unknown").put("sourceX",.5).put("sourceY",.8);
  assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(invalid));
 }
 @Test public void captureFourCentersAndAllSeamsWithFullEightUnitClosure()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)}){
   helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);v.ambient.fixedTime=0L;
    assertEquals(7,v.buildingButtons.size());assertEquals(4,v.residentButtons.size());assertFalse(v.layout.currencyHud);
    String[] labels={"companions","records","harbor","market","seam-0","seam-2","seam-4","seam-6","join-0","join-2","join-4","join-6"};
    double[] cameras={.25,2.25,4.25,6.25,0,2,4,6,7.5,1.5,3.5,5.5};
    for(int i=0;i<cameras.length;i++){
     settle(h,v,profile,cameras[i]);Bitmap first=NativeHomePartyFeedbackTest.render(h.root);
     try{
      NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(profile.name+"-four-district-"+labels[i]+".png"),first);
      settle(h,v,profile,cameras[i]+8);Bitmap again=NativeHomePartyFeedbackTest.render(h.root);
      try{assertTrue("Exact full-screen 8U closure at "+labels[i],first.sameAs(again));}finally{again.recycle();}
     }finally{first.recycle();}
    }
   }
  }
 }
 @Test public void quayOccludesHullInputButExposedBoatAndImageCaptionRemainClickable()throws Exception{
  try(ActivityController<NativeVillageHomeTest.RouteHost> activity=fixture.routeHost()){
   NativeVillageHomeTest.RouteHost h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,NativeVillageHomeTest.PHONE);h.record=true;settle(h,v,NativeVillageHomeTest.PHONE,4.25);
   android.widget.Button boat=v.buildingButtons.get("harbor");android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,boat);android.graphics.PointF hidden=null,visible=null;
   for(float y=Math.max(art.top,v.safe.top);y<Math.min(art.bottom,v.safe.bottom);y+=2)for(float x=Math.max(art.left,v.safe.left);x<Math.min(art.right,v.safe.right);x+=2){
    if(NativeVillageHomeTest.sourceAlphaAt(v,boat,x,y)<200)continue;
    if(v.districtScene.covers(v,"terrace",x,y)){hidden=new android.graphics.PointF(x,y);assertFalse(v.rawAlphaHitWorldButton(boat,x-boat.getLeft(),y-boat.getTop()));}
    else if(v.alphaHitWorldButton(boat,x-boat.getLeft(),y-boat.getTop()))visible=new android.graphics.PointF(x,y);
   }
   assertNotNull("Actual authored quay hides part of the opaque hull",hidden);assertNotNull("Boat retains exposed clickable artwork",visible);
   NativeVillageHomeTest.tapAt(v,hidden.x,hidden.y);assertNull(v.owner.menu);assertTrue(h.routes.isEmpty());
   NativeVillageHomeTest.tapAt(v,visible.x,visible.y);assertNotNull(v.owner.menu);assertTrue(v.owner.menu.isShowing());v.owner.menu.dismiss();v.owner.menu=null;
   android.graphics.RectF label=v.labelRect(new android.graphics.RectF(boat.getLeft(),boat.getTop(),boat.getRight(),boat.getBottom()),boat);
   NativeVillageHomeTest.tapAt(v,label.centerX(),label.centerY());assertNotNull(v.owner.menu);assertTrue(v.owner.menu.isShowing());
  }
 }
 @Test public void completedStatueKeepsCanonicalFullCanvasOnPlazaAtBothProfiles()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)}){
   helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);api.story.getJSONObject("statue").put("stage",5);NativeVillageHomeView v=fixture.open(h,profile);v.ambient.fixedTime=0L;settle(h,v,profile,2.25);
    assertEquals(5,v.owner.statueStage);assertNotNull(v.assets.get("statue-stage-5"));for(int n=1;n<5;n++)assertNull(v.assets.get("statue-stage-"+n));
    assertArrayEquals(new double[]{0,0,1,1},v.layout.assets.get("statue-stage-5").crop,0);
    Bitmap frame=NativeHomePartyFeedbackTest.render(h.root);try{NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(profile.name+"-four-district-records-stage5.png"),frame);}finally{frame.recycle();}
   }
  }
 }

}
