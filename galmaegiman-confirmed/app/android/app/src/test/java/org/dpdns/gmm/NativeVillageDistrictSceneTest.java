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
  static JSONObject scene(){return MainActivity.obj("id","fixture-companions-backdrop","district","companions","asset","fixture-district-scene","depth","backdrop","x",.75,"heightRatio",1.2,"baseline",.8,"groundAnchor",.8,"fadeUnits",.15);}
  static JSONObject manifest()throws Exception{return plainManifest().put("districtScenery",new JSONArray().put(scene()));}
  /** Explicit legacy geometry fixture: current artwork files are not a retired scene dependency. */
  static JSONObject plainManifest()throws Exception{
    JSONObject d=NativeVillageHomeAssetsTest.manifest();VillageLayout reference=VillageLayout.referenceLayout();
    d.put("layeredWorld",false).put("continuousFoundation",false).put("minimumUnitDp",650);
    JSONArray legacy=new JSONArray();
    for(int n=0;n<d.getJSONArray("buildings").length();n++){
      JSONObject b=d.getJSONArray("buildings").getJSONObject(n);VillageLayout.Building r=reference.building(b.getString("id"));
      if(r==null)continue;
      b.put("x",r.x).put("width",r.width).put("baseline",r.baseline).put("heightRatio",r.heightRatio).put("behindTerrace",false);
      b.remove("canvasLayer");legacy.put(b);
    }
    d.put("buildings",legacy);
    for(String group:new String[]{"buildings","residents"})for(int i=0;i<d.getJSONArray(group).length();i++){
      JSONObject a=d.getJSONArray(group).getJSONObject(i);a.remove("surface");a.remove("sourceX");a.remove("sourceY");a.remove("occludes");
      if("residents".equals(group)){VillageLayout.Resident r=reference.resident(a.getString("id"));a.put("x",r.x).put("width",r.width).put("baseline",r.baseline).put("heightRatio",r.heightRatio);}
    }
    // The synthetic layer is explicitly supplied by layer(), never decoded from retired artwork.
    d.getJSONObject("assets").put("fixture-district-scene",MainActivity.obj("path","native/village-loop-v2/assets/fixture-district-scene.png","verified",false,"crop",new JSONArray().put(0).put(0).put(1).put(1),"sourceWidth",32,"sourceHeight",24));
    d.remove("landmark");d.remove("decorations");d.remove("districtScenery");return d;
  }
  NativeVillageHomeView open(MainActivity h,VillageLayout layout)throws Exception{
    fixture.prepare(h);h.home();h.villageHome.pause();h.root.removeAllViews();h.epoch++;h.screen="home";
    NativeVillageHomeController owner=new NativeVillageHomeController(h);owner.layout=layout;h.villageHome=owner;owner.attach();
    LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);owner.view.ambient.fixedTime=0L;return owner.view;
  }
  static Bitmap layer(NativeVillageHomeView v,String depth){
    Bitmap synthetic=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);synthetic.eraseColor(0xff5d947d);
    Bitmap previous=v.assets.images.put("fixture-district-scene",synthetic);
    try{Bitmap result=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.districtScene.draw(new Canvas(result),v,depth);return result;}
    finally{if(previous==null)v.assets.images.remove("fixture-district-scene");else v.assets.images.put("fixture-district-scene",previous);synthetic.recycle();}
  }
  static int painted(Bitmap bitmap){int count=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if((bitmap.getPixel(x,y)>>>24)>0)count++;return count;}
  @Test public void districtOverlaysDoNotReplaceOrAddAnyInteractiveTargets()throws Exception{
    VillageLayout layout=VillageLayout.parse(manifest());assertEquals(1,layout.districtScenery.size());assertEquals(6,layout.buildings.size());assertEquals(4,layout.residents.size());assertEquals(8,layout.worldUnits,0);
    JSONObject invalid=manifest();invalid.getJSONArray("districtScenery").getJSONObject(0).put("actionIds",new JSONArray().put("recruit"));assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(invalid));
    JSONObject escaped=manifest();escaped.getJSONArray("districtScenery").getJSONObject(0).put("x",2.1);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(escaped));
    JSONObject fallback=plainManifest();fallback.remove("districtScenery");assertTrue(VillageLayout.parse(fallback).districtScenery.isEmpty());
  }
  @Test public void exactRealRendererClosesAtEightUnitsAndNeverPaintsTheOtherDistricts()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      NativeVillageHomeView v=open(activity.get(),VillageLayout.parse(manifest()));v.moveCameraUnits(.25-v.camera.cameraUnits());LocalGameArtTest.drain();
      Bitmap first=layer(v,"backdrop");assertTrue(painted(first)>1000);
      try{
        v.moveCameraUnits(8);LocalGameArtTest.drain();Bitmap repeated=layer(v,"backdrop");try{assertTrue(first.sameAs(repeated));}finally{repeated.recycle();}
        for(double camera:new double[]{2.25,4.25,6.25}){v.moveCameraUnits(camera-v.camera.cameraUnits());LocalGameArtTest.drain();Bitmap other=layer(v,"backdrop");try{assertEquals("No new illustration pixels outside companions",0,painted(other));}finally{other.recycle();}}
      }finally{first.recycle();}
    }
  }
  @Test public void foregroundDepthIsClippedBelowItsExplicitGroundBand()throws Exception{
    JSONObject data=manifest();data.getJSONArray("districtScenery").getJSONObject(0).put("depth","foreground").put("clipTop",.82);
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      NativeVillageHomeView v=open(activity.get(),VillageLayout.parse(data));v.moveCameraUnits(.25-v.camera.cameraUnits());LocalGameArtTest.drain();Bitmap frame=layer(v,"foreground");
      try{assertTrue(painted(frame)>0);for(int y=0;y<Math.floor(v.getHeight()*.82);y++)for(int x=0;x<v.getWidth();x++)assertEquals(0,frame.getPixel(x,y)>>>24);}finally{frame.recycle();}
    }
  }
  @Test public void captureActualCompanionsCandidateAtBothProfiles()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("village-1280x720",1280,720,160,1,0,0,0,0)}){
      helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
        MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);v.ambient.fixedTime=0L;v.moveCameraUnits(.25-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
        assertTrue(v.assets.ready());assertNotNull(v.layout.districtScenery("layered-patrol"));assertTrue(v.layout.continuousFoundation);assertTrue(v.layout.layeredWorld);
        android.graphics.PointF anchor=new android.graphics.PointF();
        for(String id:new String[]{"recruit","hideout","guard"}){VillageLayout.Placed actor=id.equals("guard")?v.layout.resident(id):v.layout.building(id);android.widget.Button target=v.actorButton(actor);v.actorPosition(actor,target);anchor.set(v.actorPoint);android.graphics.RectF art=NativeVillageHomeTest.shownArt(v,target);assertEquals("Authored floor anchor determines actual feet",anchor.y,art.bottom,1.5);assertEquals(anchor.x,art.centerX(),1.5);}
        NativeVillageHomeTest.assertNoOpaqueNeighborOverlap(v);
        Bitmap frame=NativeHomePartyFeedbackTest.render(h.root);try{NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(profile.name+"-companions-v3.png"),frame);}finally{frame.recycle();}
      }
    }
  }
  @Test public void visibleBuildingBitmapCaptionWinsOverFrontPatrolSprite()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile("village-640x320",640,320,160,1,0,0,0,0);helper.configure(profile);
    try(ActivityController<NativeVillageHomeTest.RouteHost> activity=fixture.routeHost()){
      NativeVillageHomeTest.RouteHost h=activity.get();JSONObject data=NativeVillageHomeAssetsTest.manifest();
      double towerX=VillageLayout.parse(data).building("hideout").x;
      for(int n=0;n<data.getJSONArray("residents").length();n++){
        JSONObject r=data.getJSONArray("residents").getJSONObject(n);
        if("guard".equals(r.getString("id")))r.put("x",towerX).put("baseline",.99).put("heightRatio",.26);
      }
      NativeVillageHomeView v=open(h,VillageLayout.parse(data));h.record=true;LocalGameArtTest.drain();helper.layout(h,profile);
      v.moveCameraUnits(towerX-.8-v.camera.cameraUnits());LocalGameArtTest.drain();helper.layout(h,profile);
      // Deterministic opaque patrol pixels must overlap the real bitmap caption, regardless of art revisions.
      Bitmap opaque=Bitmap.createBitmap(32,48,Bitmap.Config.ARGB_8888);opaque.eraseColor(0xff4f7280);
      Bitmap actualPatrol=v.assets.images.put("patrol",opaque);
      try{
        v.layoutWorld();
        android.widget.Button tower=v.buildingButtons.get("hideout"),guard=v.residentButtons.get("guard");
        android.graphics.RectF label=v.labelRect(new android.graphics.RectF(tower.getLeft(),tower.getTop(),tower.getRight(),tower.getBottom()),tower);
        int overlaps=0;float hitX=0,hitY=0;
        for(float y=Math.max(label.top,v.safe.top);y<Math.min(label.bottom,v.safe.bottom);y++)for(float x=Math.max(label.left,v.safe.left);x<Math.min(label.right,v.safe.right);x++)if(NativeVillageHomeTest.sourceAlphaAt(v,guard,x,y)>=128){
          overlaps++;hitX=x;hitY=y;assertTrue(v.alphaHitWorldButton(tower,x-tower.getLeft(),y-tower.getTop()));assertFalse(v.alphaHitWorldButton(guard,x-guard.getLeft(),y-guard.getTop()));
        }
        assertTrue("Fixture genuinely exercises sprite over visible caption lane",overlaps>0);
        NativeVillageHomeTest.tapAt(v,hitX,hitY);assertEquals(java.util.Collections.singletonList("party"),h.routes);
      }finally{if(actualPatrol==null)v.assets.images.remove("patrol");else v.assets.images.put("patrol",actualPatrol);opaque.recycle();}

    }
  }
  @Test public void groundShadowContractAddsNoTargetOrGeometryAndRejectsInvalidReferences()throws Exception{
    JSONObject data=NativeVillageHomeAssetsTest.manifest();VillageLayout before=VillageLayout.parse(data);
    JSONObject shadow=MainActivity.obj("asset","nameplates","crop",new JSONArray().put(0).put(0).put(1).put(1.0/6),"anchorX",.375,"anchorY",.3125,"widthRatio",1.1,"opacity",.5);
    data.getJSONArray("buildings").getJSONObject(0).put("shadow",shadow);VillageLayout after=VillageLayout.parse(data);
    assertEquals(before.buildings.size(),after.buildings.size());assertEquals(before.residents.size(),after.residents.size());
    for(VillageLayout.Building actor:before.buildings){VillageLayout.Building copy=after.building(actor.id);assertEquals(actor.heightRatio,copy.heightRatio,0);assertEquals(actor.sourceX,copy.sourceX,0);assertEquals(actor.sourceY,copy.sourceY,0);assertArrayEquals(actor.actionIds,copy.actionIds);}
    shadow.put("asset","not-an-asset");assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));shadow.put("asset","nameplates").put("opacity",2);assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));shadow.put("opacity",.5).put("actionIds",new JSONArray().put("party"));assertThrows(IllegalArgumentException.class,()->VillageLayout.parse(data));
  }
  /** v4부터 주민 접지 그림자 그림을 쓴다. 바뀌는 픽셀은 주민 발밑 그림자 영역뿐이어야 하고(바닥 대체 금지), 8U 뒤 화면은 같다. */
  @Test public void residentContactShadowsOnlyTouchResidentFeetAndCloseAtEightUnits()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);v.ambient.fixedTime=0L;
      assertTrue(v.assets.ready());assertEquals(7,v.buildingButtons.size());assertEquals(4,v.residentButtons.size());
      Bitmap atlas=v.assets.get("companions-shadows");assertNotNull("Resident ground-contact atlas is loaded",atlas);
      // 화면에 보이는 주민의 실제 그림자 영역만 허용 범위로 모은다.
      java.util.List<android.graphics.RectF> allowed=new java.util.ArrayList<>();
      Bitmap scratch=Bitmap.createBitmap(Math.max(1,v.getWidth()),Math.max(1,v.getHeight()),Bitmap.Config.ARGB_8888);
      for(VillageLayout.Resident resident:v.layout.residents){
        v.groundShadows.target.setEmpty();v.groundShadows.drawActor(new Canvas(scratch),v,resident);
        if(!v.groundShadows.target.isEmpty()&&android.graphics.RectF.intersects(v.groundShadows.target,v.field)){android.graphics.RectF r=new android.graphics.RectF(v.groundShadows.target);r.inset(-2,-2);r.offset(v.getLeft(),v.getTop());allowed.add(r);}
      }
      scratch.recycle();
      assertFalse("At least one resident contact shadow is on screen",allowed.isEmpty());
      Bitmap with=NativeHomePartyFeedbackTest.render(h.root);
      v.assets.images.remove("companions-shadows");Bitmap without=NativeHomePartyFeedbackTest.render(h.root);
      v.assets.images.put("companions-shadows",atlas);
      try{
        int shown=0;
        for(int y=0;y<with.getHeight();y++)for(int x=0;x<with.getWidth();x++){
          if(with.getPixel(x,y)==without.getPixel(x,y))continue;
          shown++;
          boolean inside=false;for(android.graphics.RectF r:allowed)if(r.contains(x+.5f,y+.5f)){inside=true;break;}
          assertTrue("Shadow atlas only paints resident feet, never a floor plane: "+x+","+y,inside);
        }
        assertTrue("Resident contact shadow is actually visible",shown>10);
        v.moveCameraUnits(8);for(int n=0;n<4;n++){LocalGameArtTest.drain();helper.layout(h,NativeVillageHomeTest.PHONE);}
        Bitmap repeat=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Eight units close the loop with the same frame",with.sameAs(repeat));}finally{repeat.recycle();}
      }finally{with.recycle();without.recycle();}
    }
  }

}
