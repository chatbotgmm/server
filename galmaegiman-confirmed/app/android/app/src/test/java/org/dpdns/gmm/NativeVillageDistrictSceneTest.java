package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Synthetic scene metadata exercises the real district renderer; it is not candidate artwork approval. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageDistrictSceneTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();final NativeHomePartyFeedbackTest helper=fixture.helper;
  @Before public void setup(){helper.isolate();helper.configure(NativeVillageHomeTest.PHONE);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
  static JSONObject scene(){return MainActivity.obj("id","fixture-companions-backdrop","district","companions","asset","grove","depth","backdrop","x",.75,"heightRatio",1.2,"baseline",.8,"groundAnchor",.8,"fadeUnits",.15);}
  static JSONObject manifest()throws Exception{return plainManifest().put("districtScenery",new JSONArray().put(scene()));}
  static JSONObject plainManifest()throws Exception{JSONObject d=NativeVillageHomeAssetsTest.manifest();for(String group:new String[]{"buildings","residents"})for(int i=0;i<d.getJSONArray(group).length();i++){JSONObject a=d.getJSONArray(group).getJSONObject(i);a.remove("surface");a.remove("sourceX");a.remove("sourceY");a.remove("occludes");}return d;}
  NativeVillageHomeView open(MainActivity h,VillageLayout layout)throws Exception{
    fixture.prepare(h);h.home();h.villageHome.pause();h.root.removeAllViews();h.epoch++;h.screen="home";
    NativeVillageHomeController owner=new NativeVillageHomeController(h);owner.layout=layout;h.villageHome=owner;owner.attach();
    LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);owner.view.ambient.fixedTime=0L;return owner.view;
  }
  static Bitmap layer(NativeVillageHomeView v,String depth){Bitmap result=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.districtScene.draw(new Canvas(result),v,depth);return result;}
  static int painted(Bitmap bitmap){int count=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if((bitmap.getPixel(x,y)>>>24)>0)count++;return count;}
  @Test public void districtOverlaysDoNotReplaceOrAddAnyInteractiveTargets()throws Exception{
    VillageLayout layout=VillageLayout.parse(manifest());assertEquals(1,layout.districtScenery.size());assertEquals(6,layout.buildings.size());assertEquals(4,layout.residents.size());assertEquals(8,layout.worldUnits,0);
    JSONObject invalid=manifest();invalid.getJSONArray("districtScenery").getJSONObject(0).put("actionIds",new JSONArray().put("recruit"));assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(invalid));
    JSONObject escaped=manifest();escaped.getJSONArray("districtScenery").getJSONObject(0).put("x",2.1);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(escaped));
    JSONObject fallback=plainManifest();fallback.remove("districtScenery");assertTrue(VillageLayout.parse(fallback).districtScenery.isEmpty());
  }
  @Test public void exactRealRendererClosesAtEightUnitsAndNeverPaintsTheOtherDistricts()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      NativeVillageHomeView v=open(activity.get(),VillageLayout.parse(manifest()));v.moveCameraUnits(.25-v.camera.cameraUnits());
      Bitmap first=layer(v,"backdrop");assertTrue(painted(first)>1000);
      try{
        v.moveCameraUnits(8);Bitmap repeated=layer(v,"backdrop");try{assertTrue(first.sameAs(repeated));}finally{repeated.recycle();}
        for(double camera:new double[]{2.25,4.25,6.25}){v.moveCameraUnits(camera-v.camera.cameraUnits());Bitmap other=layer(v,"backdrop");try{assertEquals("No new illustration pixels outside companions",0,painted(other));}finally{other.recycle();}}
      }finally{first.recycle();}
    }
  }
  @Test public void foregroundDepthIsClippedBelowItsExplicitGroundBand()throws Exception{
    JSONObject data=manifest();data.getJSONArray("districtScenery").getJSONObject(0).put("depth","foreground").put("clipTop",.82);
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      NativeVillageHomeView v=open(activity.get(),VillageLayout.parse(data));v.moveCameraUnits(.25-v.camera.cameraUnits());Bitmap frame=layer(v,"foreground");
      try{assertTrue(painted(frame)>0);for(int y=0;y<Math.floor(v.getHeight()*.82);y++)for(int x=0;x<v.getWidth();x++)assertEquals(0,frame.getPixel(x,y)>>>24);}finally{frame.recycle();}
    }
  }
  @Test public void captureActualCompanionsCandidateAtBothProfiles()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)}){
      helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);v.ambient.fixedTime=0L;v.moveCameraUnits(.25-v.camera.cameraUnits());helper.layout(h,profile);
        assertTrue(v.assets.ready());assertEquals(2,v.layout.districtScenery.size());
        android.graphics.PointF anchor=new android.graphics.PointF();
        for(String id:new String[]{"recruit","hideout","guard"}){VillageLayout.Placed actor=id.equals("guard")?v.layout.resident(id):v.layout.building(id);assertTrue(v.districtScene.point(v,actor.surface,actor.sourceX,actor.sourceY,anchor));android.widget.Button target=v.actorButton(actor);android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,target);assertEquals("Original source floor anchor determines actual feet",anchor.y,art.bottom,1.5);assertEquals(anchor.x,art.centerX(),1.5);}
        NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(v);
        Bitmap frame=NativeHomePartyFeedbackTest.render(h.root);try{NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(profile.name+"-companions-v3.png"),frame);}finally{frame.recycle();}
      }
    }
  }
  @Test public void visibleBuildingBitmapCaptionWinsOverFrontPatrolSprite()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("village-640x320",640,320,160,1,0,0,0,0);helper.configure(profile);
    try(ActivityController<NativeVillageHomeTest.RouteHost> activity=fixture.routeHost()){
      NativeVillageHomeTest.RouteHost h=activity.get();JSONObject data=NativeVillageHomeAssetsTest.manifest();data.getJSONArray("districtScenery").getJSONObject(1).put("minimumViewportWidth",0).put("groundAnchor",1);NativeVillageHomeView v=open(h,VillageLayout.parse(data));h.record=true;LocalGameArtTest.drain();helper.layout(h,profile);v.moveCameraUnits(-v.camera.cameraUnits());helper.layout(h,profile);
      android.widget.Button tower=v.buildingButtons.get("hideout"),guard=v.residentButtons.get("guard");android.graphics.RectF label=v.labelRect(new android.graphics.RectF(tower.getLeft(),tower.getTop(),tower.getRight(),tower.getBottom()),tower);int overlaps=0;float hitX=0,hitY=0;
      for(float y=label.top;y<label.bottom;y++)for(float x=label.left;x<label.right;x++)if(NativeVillageHomeTest.sourceAlphaAt(v,guard,x,y)>=128){overlaps++;hitX=x;hitY=y;assertTrue(v.alphaHitWorldButton(tower,x-tower.getLeft(),y-tower.getTop()));assertFalse(v.alphaHitWorldButton(guard,x-guard.getLeft(),y-guard.getTop()));}
      assertTrue("Fixture genuinely exercises sprite over visible caption lane",overlaps>0);NativeVillageHomeTest.tapAt(v,hitX,hitY);assertEquals(java.util.Collections.singletonList("party"),h.routes);
    }
  }
}
