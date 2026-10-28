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
    assertTrue(v.assets.ready());assertNotNull(v.assets.get("layered-sky"));assertNotNull(v.assets.get("layered-patrol"));v.ambient.fixedTime=1400L;return v;
  }
  void settleViewport(MainActivity h,NativeVillageHomeView v)throws Exception{
    for(int i=0;i<4;i++){LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);}
    assertTrue("Viewport images must be ready before pixel equality",v.assets.ready());
  }
  @Test public void fourDistrictEdgesAndWorldSeamAreIdenticalAtFixedWaterTime()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=loaded(h);
      for(double position:new double[]{-.125,0,.125,1.875,2,3.875,4,5.875,6,7.875}){
        v.moveCameraUnits(position-v.camera.cameraUnits());settleViewport(h,v);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);
        try{for(int direction:new int[]{1,-1}){
          v.moveCameraUnits(direction*8);settleViewport(h,v);Bitmap after=NativeHomePartyFeedbackTest.render(h.root);
          try{assertTrue("Full turn at fixed time "+position,before.sameAs(after));}finally{after.recycle();}
          v.moveCameraUnits(-direction*8);settleViewport(h,v);
        }}finally{before.recycle();}
      }
    }
  }
  @Test public void timeChangesLocalWaterPixelsButNeverSkyGroundBuildingsOrInland()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=loaded(h);
      v.moveCameraUnits(3.75-v.camera.cameraUnits());settleViewport(h,v);v.ambient.fixedTime=0L;
      VillageLayout.DistrictScenery water=v.layout.districtScenery("layered-join-approved-market");assertTrue(v.districtScene.geometry(v,water,v.assets.get(water.asset)));
      v.districtScene.layeredWaterClip();android.graphics.RectF region=new android.graphics.RectF(v.districtScene.waterClip);
      Bitmap first=NativeHomePartyFeedbackTest.render(h.root);v.ambient.fixedTime=2100L;Bitmap second=NativeHomePartyFeedbackTest.render(h.root);
      try{
        int changed=0;
        for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getPixel(x,y)!=second.getPixel(x,y)){
          changed++;assertTrue("Animation escaped water-only band at "+x+","+y,region.contains(x+.5f,y+.5f));
        }
        assertTrue("Authored water ripples must visibly change",changed>10);
      }finally{first.recycle();second.recycle();}
      v.moveCameraUnits(.25-v.camera.cameraUnits());settleViewport(h,v);v.ambient.fixedTime=0L;first=NativeHomePartyFeedbackTest.render(h.root);
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
        assertNotNull(v.assets.get("layered-sky"));assertNotNull(v.assets.get("layered-patrol"));
        v.stopMotion();
        String[] scenes={"plaza-stage-1","harbor"};
        // Same world coordinate and angle for every timestamp; the statue is centered in the plaza shot.
        // Representative front view only; the existing actual-art district suite keeps the 4.38 offset capture.
        double[] positions={v.layout.landmark.x-v.field.width()/v.unitPixels()/2,4.25};
        for(int scene=0;scene<scenes.length;scene++){
          v.moveCameraUnits(positions[scene]-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
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
      double camera=v.layout.landmark.x-v.field.width()/v.unitPixels()/2;v.moveCameraUnits(camera-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
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
        for(double position:new double[]{.25,2.25,4.25,6.25}){
          v.moveCameraUnits(position-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          for(android.widget.Button button:NativeVillageHomeTest.worldTargets(v)){
            android.graphics.RectF object=new android.graphics.RectF(button.getLeft(),button.getTop(),button.getRight(),button.getBottom());
            android.graphics.RectF label=v.labelRect(object,button),art=NativeVillageHomeTest.shownArt(v,button);
            NativeVillageHomeView.WorldTarget wt=(NativeVillageHomeView.WorldTarget)button;
            if(wt.building!=null&&!wt.building.canvasLayer.isEmpty()){double[] content=v.layout.assets.get(wt.asset).contentBounds;art.bottom=art.top+art.height()*(float)content[3];}
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
        // V5: AVAILABLE 느낌표는 이름표 옆 칸 대신 주민 머리 위에 그린다.
        int cell="AVAILABLE".equals(status)?-1:VillageNameplates.statusCell(status);float plateWidth=v.labelHeight(button)*VillageNameplates.aspect(VillageNameplates.cell(button.asset));
        float lane=cell<0?0:h.dp(3)+h.dp(18)*VillageNameplates.aspect(cell);
        assertEquals(lane,button.statusLane(),.001f);assertEquals(plateWidth+lane,v.labelWidth(button),.001f);
        android.graphics.RectF object=v.residentBounds.get("guard"),label=v.labelRect(object,button);
        assertEquals("Plaque alone or the plaque+badge group is centered",object.centerX(),label.centerX(),.01f);
        if(cell<0)assertEquals("No invisible badge lane shifts an inactive plaque",plateWidth,label.width(),.1f);
        VillageLayout.Resident guard=null;for(VillageLayout.Resident r:v.layout.residents)if("guard".equals(r.id))guard=r;assertNotNull(guard);
        android.graphics.RectF marker=v.residentMarkerRect(guard);
        if("AVAILABLE".equals(status)){
          assertFalse("AVAILABLE shows the exclamation above the head",marker.isEmpty());
          assertTrue("Head marker sits above the nameplate",marker.bottom<=label.top);
          assertTrue("Head marker stays over the resident",marker.centerX()>=object.left&&marker.centerX()<=object.right);
          android.graphics.Bitmap with=NativeHomePartyFeedbackTest.render(h.root);android.graphics.Bitmap atlas=v.assets.images.remove("nameplates");android.graphics.Bitmap without=NativeHomePartyFeedbackTest.render(h.root);v.assets.images.put("nameplates",atlas);
          int drawn=0;for(int y=Math.max(0,(int)marker.top);y<Math.min(with.getHeight(),(int)Math.ceil(marker.bottom));y++)for(int x=Math.max(0,(int)marker.left);x<Math.min(with.getWidth(),(int)Math.ceil(marker.right));x++)if(with.getPixel(x,y)!=without.getPixel(x,y))drawn++;
          with.recycle();without.recycle();assertTrue("Head exclamation is actually painted",drawn>10);
        }else assertTrue("Only AVAILABLE draws a head marker",marker.isEmpty());
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void layeredSceneKeepsOriginalAspectAndSmallerMailboxInChosenFrontViews()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){
      helper.configure(profile);
      try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);
        for(VillageLayout.Building building:v.layout.buildings){
          v.moveCameraUnits(Math.floor(building.x/2)*2+.25-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.buildingButtons.get(building.id));
          v.actorPosition(building,v.buildingButtons.get(building.id));double expected=building.canvasLayer.isEmpty()?v.field.height()*building.heightRatio:v.field.height();assertEquals("Authored full-canvas scale and footprint remain fixed: "+building.id,expected,art.height(),1.1);
        }
        for(VillageLayout.Resident resident:v.layout.residents){
          v.moveCameraUnits(Math.floor(resident.x/2)*2+.25-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.residentButtons.get(resident.id));
          v.actorPosition(resident,v.residentButtons.get(resident.id));double expected=Math.min(v.field.height()*resident.heightRatio,v.actorPoint.y-v.safe.top-h.dp(64));assertEquals("NPC height honors platform clearance and original aspect: "+resident.id,expected,art.height(),1.1);
          assertTrue(v.residentButtons.get(resident.id).getWidth()>=h.dp(48));assertTrue(v.residentButtons.get(resident.id).getHeight()>=h.dp(48));
        }
        // Latest user direction supersedes the earlier equal-door-size proposal: enlarge the group, not the mailbox.
        for(String id:new String[]{"recruit","archive","market"})assertTrue("Functional buildings retain substantial scene presence",v.layout.building(id).heightRatio>=.48);
        for(VillageLayout.Resident resident:v.layout.residents)assertTrue("Residents remain readable beside architecture",resident.heightRatio>=.20&&resident.heightRatio<=.25);
        assertTrue("Mailbox is independently reduced, not included in the group enlargement",v.layout.building("mailbox").heightRatio>=.15&&v.layout.building("mailbox").heightRatio<=.17);
        assertEquals("Front patrol keeps authored 95px height",94.76,v.layout.resident("guard").heightRatio*412,.1);
        assertEquals("Mailbox remains below resident size at 66px",65.92,v.layout.building("mailbox").heightRatio*412,.1);
        for(VillageLayout.Building building:v.layout.buildings){
          v.moveCameraUnits(Math.floor(building.x/2)*2+.25-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,v.buildingButtons.get(building.id));Bitmap bitmap=v.assets.get(building.asset);double[] crop=v.layout.assets.get(building.asset).crop;
          double aspect=(crop[2]-crop[0])*bitmap.getWidth()/((crop[3]-crop[1])*bitmap.getHeight());
          assertEquals("Enlargement never stretches the original building",aspect,art.width()/art.height(),.01);
        }
        // Deliberately chosen front views. Partial objects during free scrolling remain valid and are tested separately.
        for(VillageLayout.Placed actor:v.depthActors){
          v.actorPosition(actor,v.actorButton(actor));
          double focus=actor.x-v.field.width()/v.unitPixels()/2;
          v.moveCameraUnits(focus-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
          android.widget.Button target=v.actorButton(actor);
          android.graphics.RectF bounds=new android.graphics.RectF(target.getLeft(),target.getTop(),target.getRight(),target.getBottom());
          assertTrue("Focused full artwork and image nameplate fit: "+profile.name+" / "+actor.id,v.safe.contains(bounds));
          NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(v);
        }
      }
    }
  }

}
