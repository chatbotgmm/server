package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Native pixel evidence at a fixed animation time, plus account-isolated restoration state. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageAmbientTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  final NativeHomePartyFeedbackTest helper=fixture.helper;
  @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
  NativeVillageHomeView loaded(MainActivity h)throws Exception{
    NativeVillageHomeView v=fixture.open(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);
    assertTrue(v.assets.ready());assertNotNull(v.assets.get("grove"));assertNotNull(v.assets.get("neighborhood"));v.ambient.fixedTime=1400L;return v;
  }
  @Test public void fourDistrictEdgesAndWorldSeamAreIdenticalAtFixedWaterTime()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=loaded(h);
      for(double position:new double[]{-.125,0,.125,1.875,2,3.875,4,5.875,6,7.875}){
        v.moveCameraUnits(position-v.camera.cameraUnits());Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
        try{for(int direction:new int[]{1,-1}){
          v.moveCameraUnits(direction*8);Bitmap after=NativeHomePartyFeedbackTest.render(h.root);
          try{assertTrue("Full turn at fixed time "+position,before.sameAs(after));}finally{after.recycle();}
          v.moveCameraUnits(-direction*8);
        }}finally{before.recycle();}
      }
    }
  }
  @Test public void timeChangesLocalWaterPixelsButNeverSkyGroundBuildingsOrInland()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=loaded(h);
      v.moveCameraUnits(4.875-v.camera.cameraUnits());v.ambient.fixedTime=0L;
      Bitmap first=NativeHomePartyFeedbackTest.render(h.root);v.ambient.fixedTime=2100L;Bitmap second=NativeHomePartyFeedbackTest.render(h.root);
      try{
        int changed=0;
        for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getPixel(x,y)!=second.getPixel(x,y)){
          changed++;assertTrue("Animation escaped water-only band at "+x+","+y,y>=v.field.height()*.52&&y<v.field.height()*.68);
        }
        assertTrue("Idle foam must visibly change actual water pixels",changed>10);
      }finally{first.recycle();second.recycle();}
      v.moveCameraUnits(.25-v.camera.cameraUnits());v.ambient.fixedTime=0L;first=NativeHomePartyFeedbackTest.render(h.root);
      v.ambient.fixedTime=2100L;second=NativeHomePartyFeedbackTest.render(h.root);
      try{assertTrue("Inland scenery cannot shimmer or drift",first.sameAs(second));}finally{first.recycle();second.recycle();}
    }
  }
  @Test public void pausedOffscreenTimeDoesNotCatchUpOrJump(){
    VillageAmbientScene a=new VillageAmbientScene();assertEquals(0,a.time(100,true));assertEquals(33,a.time(133,true));
    assertEquals(33,a.time(200,false));assertEquals(33,a.time(900000,true));assertEquals(66,a.time(900033,true));
    a.pause();assertEquals(66,a.time(990000,true));
    for(double x:new double[]{-.125,0,.125,2,4,4.5,5.5,6,7.875}){
      assertEquals(VillageAmbientScene.harborWeight(x),VillageAmbientScene.harborWeight(x+8),1e-12);
      assertEquals(VillageAmbientScene.neighborhoodWeight(x),VillageAmbientScene.neighborhoodWeight(x+8),1e-12);
    }
    assertEquals(0,VillageAmbientScene.harborWeight(2.5),0);assertEquals(1,VillageAmbientScene.harborWeight(4.75),0);
  }
  @Test public void statueUsesOnlyValidatedCurrentAccountResponseForAllFiveLazyStages()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);api.story.put("statue",new JSONObject().put("stage",1));
      NativeVillageHomeView v=loaded(h);assertEquals(1,v.owner.statueStage);assertNotNull(v.layout.landmark);assertEquals(1502.0/1536,v.layout.landmark.groundAnchor,1e-9);
      for(int stage=1;stage<=5;stage++){
        api.story.put("statue",new JSONObject().put("stage",stage));v.owner.refresh();LocalGameArtTest.drain();assertEquals(stage,v.owner.statueStage);
        String id=v.layout.landmark.asset(stage);
        assertArrayEquals("Every stage uses the same full canvas, never rubble content auto-fit",new double[]{0,0,1,1},v.layout.assets.get(id).crop,0);
        assertTrue(v.layout.assets.get(id).verified);assertFalse(v.layout.assets.get(id).required);assertNotNull(v.assets.get(id));
        assertEquals(2.0/3,v.assets.get(id).getWidth()/(double)v.assets.get(id).getHeight(),1e-9);
        for(int future=stage+1;future<=5;future++)assertNull("Future stages remain lazy",v.assets.get(v.layout.landmark.asset(future)));

      }
      api.story.put("statue",new JSONObject().put("stage",99));v.owner.refresh();assertEquals(5,v.owner.statueStage);
      api.deferStory=true;v.owner.refresh();assertEquals(1,api.storyReads.size());api.account="other-player.1.signature";
      api.storyReads.get(0).done(NativeVillageHomeTest.copy(api.story).put("statue",new JSONObject().put("stage",2)),null);
      assertEquals("A stale owner response cannot replace a saved stage",5,v.owner.statueStage);
      assertEquals("New controller never borrows the previous account stage",0,new NativeVillageHomeController(h).statueStage);
      assertTrue(api.writes.isEmpty());
    }
  }
  /** Captures the actual native view, not a preview renderer. Root owns the Gradle invocation. */
  @Test public void saveNativePlazaStageOneAndHarborTimedFramesAtPhoneAndTabletSizes()throws Exception{
    java.nio.file.Path output=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"ambient-native-frames");
    java.nio.file.Files.createDirectories(output);org.json.JSONArray frames=new org.json.JSONArray();
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,
        new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){
      helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);
        api.story.put("statue",new JSONObject().put("stage",1));
        NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);
        assertTrue("Core assets must finish before capture",v.assets.ready());
        assertEquals(1,v.owner.statueStage);assertNotNull("Stage 1 must be the real decoded PNG",v.assets.get("statue-stage-1"));
        assertNotNull(v.assets.get("grove"));assertNotNull(v.assets.get("neighborhood"));assertNotNull(v.assets.get("sea"));
        v.stopMotion();
        String[] scenes={"plaza-stage-1","harbor"};
        // Same world coordinate and angle for every timestamp; the statue is centered in the plaza shot.
        // Representative front view only; the existing actual-art district suite keeps the 4.38 offset capture.
        double[] positions={v.layout.landmark.x-v.field.width()/v.unitPixels()/2,4.25};
        for(int scene=0;scene<scenes.length;scene++){
          v.moveCameraUnits(positions[scene]-v.camera.cameraUnits());helper.layout(h,profile);
          for(long millis:new long[]{0,500,1000}){
            v.ambient.fixedTime=millis;
            Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);
            String filename=bitmap.getWidth()+"x"+bitmap.getHeight()+"-"+scenes[scene]+"-t"+millis+"ms.png";
            try{
              assertEquals(v.getWidth(),bitmap.getWidth());assertEquals(v.getHeight(),bitmap.getHeight());
              NativeVillageHomeArtTest.png(output.resolve(filename),bitmap);
              frames.put(MainActivity.obj("file",filename,"profile",profile.name,"scene",scenes[scene],
                  "cameraUnits",v.camera.cameraUnits(),"animationTimeMillis",millis,"statueStage",v.owner.statueStage,
                  "renderer","actual NativeVillageHomeView via native Skia","width",bitmap.getWidth(),"height",bitmap.getHeight()));
              NativeVillageHomeArtTest.json(output.resolve("frames.json"),frames);
            }finally{bitmap.recycle();}
          }
        }
        assertTrue("Captures must not write story or gameplay state",api.writes.isEmpty());
      }
    }
    assertEquals(12,frames.length());
  }

  @Test public void saveAllFiveNativeStatueStagesAtIdenticalTabletCameraAndScale()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0);
    helper.configure(profile);
    java.nio.file.Path output=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"statue-five-native");
    java.nio.file.Files.createDirectories(output);org.json.JSONArray frames=new org.json.JSONArray();
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);api.story.put("statue",new JSONObject().put("stage",1));
      NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);v.stopMotion();v.ambient.fixedTime=0L;
      double camera=v.layout.landmark.x-v.field.width()/v.unitPixels()/2;v.moveCameraUnits(camera-v.camera.cameraUnits());helper.layout(h,profile);
      for(int stage=1;stage<=5;stage++){
        api.story.put("statue",new JSONObject().put("stage",stage));v.owner.refresh();LocalGameArtTest.drain();
        assertEquals(stage,v.owner.statueStage);assertEquals(camera,v.camera.cameraUnits(),0);assertTrue(v.assets.ready());
        String id=v.layout.landmark.asset(stage);assertNotNull(v.assets.get(id));assertArrayEquals(new double[]{0,0,1,1},v.layout.assets.get(id).crop,0);
        for(int future=stage+1;future<=5;future++)assertNull(v.assets.get(v.layout.landmark.asset(future)));
        Bitmap frame=NativeHomePartyFeedbackTest.render(h.root);String filename="1280x720-stage-"+stage+".png";
        try{assertEquals(1280,frame.getWidth());assertEquals(720,frame.getHeight());NativeVillageHomeArtTest.png(output.resolve(filename),frame);
          frames.put(MainActivity.obj("file",filename,"stage",stage,"cameraUnits",camera,"groundAnchor",v.layout.landmark.groundAnchor,"animationTimeMillis",0,"renderer","actual NativeVillageHomeView"));
          NativeVillageHomeArtTest.json(output.resolve("frames.json"),frames);
        }finally{frame.recycle();}
      }
      assertTrue(api.writes.isEmpty());assertEquals(5,frames.length());
    }
  }

  @Test public void imageLetteringAtlasAndStatusMarksStayBelowActualSpritesInEveryReviewProfile()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:NativeVillageHomeTest.PROFILES){
      helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);
        LocalGameArtTest.drain();helper.layout(h,profile);Bitmap atlas=v.assets.get("nameplates");assertNotNull(atlas);
        for(int cell=0;cell<12;cell++){
          int[] bounds=VillageNameplates.BOUNDS[cell];int cw=atlas.getWidth()/2,ch=atlas.getHeight()/6,count=0;
          for(int y=(cell/2)*ch+Math.round(ch*bounds[1]/256f);y<(cell/2)*ch+Math.round(ch*bounds[3]/256f);y++)
            for(int x=(cell%2)*cw+Math.round(cw*bounds[0]/512f);x<(cell%2)*cw+Math.round(cw*bounds[2]/512f);x++)
              if(android.graphics.Color.alpha(atlas.getPixel(x,y))>127)count++;
          assertTrue("Every image label/status source cell contains real opaque artwork: "+cell,count>100);
        }
        for(double position:new double[]{.25,2.25,4.38,5.98}){
          v.moveCameraUnits(position-v.camera.cameraUnits());helper.layout(h,profile);
          for(android.widget.Button button:NativeVillageHomeTest.worldTargets(v)){
            android.graphics.RectF object=new android.graphics.RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom());
            android.graphics.RectF label=v.labelRect(object,button),art=NativeVillageHomeTest.shownArt(v,button);
            assertTrue("Image nameplate is strictly below sprite feet",label.top>=art.bottom+v.labelGap()-1);
            assertTrue("Image nameplate respects lower safe margin",label.bottom<=v.safe.bottom-h.dp(5));
            assertFalse(button.getContentDescription().toString().isEmpty());
            if(v.safe.contains(label.centerX(),label.centerY()))assertTrue("Lower image nameplate remains a real tap target",v.alphaHitWorldButton(button,label.centerX()-button.getLeft(),label.centerY()-button.getTop()));
          }
          NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(v);
        }
      }
    }
  }

  @Test public void residentImageNameplateCentersAloneOrWithOnlyTheActualStatusIcon()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=loaded(h);
      NativeVillageHomeView.WorldTarget button=(NativeVillageHomeView.WorldTarget)v.residentButtons.get("guard");
      JSONObject npc=null;for(int i=0;i<api.story.getJSONArray("residents").length();i++){
        JSONObject row=api.story.getJSONArray("residents").getJSONObject(i);if("guard".equals(row.getString("id")))npc=row;
      }
      assertNotNull(npc);
      for(String status:new String[]{"LOCKED","AVAILABLE","ACTIVE","READY","COMPLETED","LOCKED"}){
        npc.put("status",status);v.owner.refresh();assertTrue("State changes request fresh label geometry",v.isLayoutRequested());helper.layout(h,NativeVillageHomeTest.PHONE);
        int cell=VillageNameplates.statusCell(status);float plateWidth=v.labelHeight(button)*VillageNameplates.aspect(VillageNameplates.cell(button.asset));
        float lane=cell<0?0:h.dp(3)+h.dp(18)*VillageNameplates.aspect(cell);
        assertEquals(lane,button.statusLane(),.001f);assertEquals(plateWidth+lane,v.labelWidth(button),.001f);
        android.graphics.RectF object=v.residentBounds.get("guard"),label=v.labelRect(object,button);
        assertEquals("Plaque alone or the plaque+badge group is centered",object.centerX(),label.centerX(),.01f);
        if(cell<0)assertEquals("No invisible badge lane shifts an inactive plaque",plateWidth,label.width(),.1f);
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void collectivelyEnlargedSceneKeepsOriginalAspectAndSmallerMailboxInChosenFrontViews()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){
      helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);
        for(VillageLayout.Building building:v.layout.buildings){
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.buildingButtons.get(building.id));
          assertEquals("Explicit height must not collapse back to the uniform .62H cap: "+building.id,v.field.height()*building.heightRatio,art.height(),1.1);
        }
        for(VillageLayout.Resident resident:v.layout.residents){
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.residentButtons.get(resident.id));
          assertEquals("NPC scene height is independent of old widthU and species aspect: "+resident.id,v.field.height()*resident.heightRatio,art.height(),1.1);
          assertTrue(v.residentButtons.get(resident.id).getWidth()>=h.dp(48));assertTrue(v.residentButtons.get(resident.id).getHeight()>=h.dp(48));
        }
        // Latest user direction supersedes the earlier equal-door-size proposal: enlarge the group, not the mailbox.
        for(String id:new String[]{"recruit","archive","market"})assertTrue("Single-floor buildings regain substantial presence",v.layout.building(id).heightRatio/("archive".equals(id)?.40:.38)+1e-9>=1.45);
        for(VillageLayout.Resident resident:v.layout.residents)assertTrue("Residents enlarge together with the architecture",resident.heightRatio/("blacksmith".equals(resident.id)?.21:"librarian".equals(resident.id)?.19:.17)+1e-9>=1.45);
        assertTrue("Mailbox is independently reduced, not included in the group enlargement",v.layout.building("mailbox").heightRatio/.23<.65);
        assertEquals("Common resident reference height is now visibly about 107px",107.12,v.layout.resident("guard").heightRatio*412,.1);
        assertEquals("Mailbox is about 60px, clearly smaller than a resident",59.74,v.layout.building("mailbox").heightRatio*412,.1);
        for(VillageLayout.Building building:v.layout.buildings){
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.buildingButtons.get(building.id));Bitmap bitmap=v.assets.get(building.asset);double[] crop=v.layout.assets.get(building.asset).crop;
          double aspect=(crop[2]-crop[0])*bitmap.getWidth()/((crop[3]-crop[1])*bitmap.getHeight());
          assertEquals("Enlargement never stretches the original building",aspect,art.width()/art.height(),.01);
        }
        // Deliberately chosen front views. Partial objects during free scrolling remain valid and are tested separately.
        double[] cameras={.25,2.25,4.25,5.98};String[][] buildingIds={{"recruit","hideout"},{"archive","mailbox"},{"harbor"},{"market"}};String[][] residentIds={{"guard"},{"librarian"},{},{"blacksmith","merchant"}};
        for(int district=0;district<4;district++){
          v.moveCameraUnits(cameras[district]-v.camera.cameraUnits());helper.layout(h,profile);
          for(String id:buildingIds[district])assertTrue("Full building and below-image plaque fit district view: "+profile.name+" / "+id,v.safe.contains(v.buildingBounds.get(id)));
          for(String id:residentIds[district])assertTrue("Full resident and below-image plaque fit district view: "+profile.name+" / "+id,v.safe.contains(v.residentBounds.get(id)));
          NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(v);
        }
      }
    }
  }

}
