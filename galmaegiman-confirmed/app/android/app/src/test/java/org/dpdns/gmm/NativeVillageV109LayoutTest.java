package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Actual production village camera, captions and quarter controls; controlled reads only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageV109LayoutTest {
  final NativeVillageExplorationTest exploration=new NativeVillageExplorationTest();
  final NativeVillageHomeTest fixture=exploration.fixture;
  final NativeHomePartyFeedbackTest helper=exploration.helper;
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}

  NativeVillageHomeView open(MainActivity h)throws Exception{
    NativeVillageHomeView v=fixture.open(h,NativeVillageHomeTest.PHONE);
    exploration.settle(h,v,NativeVillageHomeTest.PHONE);v.ambient.fixedTime=0L;return v;
  }

  static void assertMeasuredTargets(NativeVillageHomeView v){
    java.util.ArrayList<View> targets=new java.util.ArrayList<>();targets.addAll(v.buildingButtons.values());targets.addAll(v.residentButtons.values());targets.add(v.statue);
    for(View target:targets){assertEquals("Measured width matches the current rounded target: "+target.getTag(),target.getWidth(),target.getMeasuredWidth());assertEquals("Measured height matches the current rounded target: "+target.getTag(),target.getHeight(),target.getMeasuredHeight());}
  }

  @Test public void committedCameraAndHudCaptionAvoidanceKeepMeasuredTargetsEqualToActualBounds()throws Exception{
    NativeHomePartyFeedbackTest.Profile profile=NativeVillageHomeTest.PROFILES[2];helper.configure(profile);
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);exploration.settle(h,v,profile);
      for(double camera:new double[]{0,.25,2.25,4.25,5.75,6,6.05,6.1,7.75,8}){
        v.moveCameraUnits(camera-v.desiredCamera.cameraUnits());exploration.settle(h,v,profile);assertMeasuredTargets(v);
      }
      v.moveCameraUnits(6-v.desiredCamera.cameraUnits());exploration.settle(h,v,profile);
      Button merchant=v.residentButtons.get("merchant");RectF image=new RectF(v.imageSlot(merchant));int before=merchant.getWidth();
      RectF caption=v.screenRect(v.labelRect(new RectF(merchant.getLeft(),merchant.getTop(),merchant.getRight(),merchant.getBottom()),merchant));
      RectF previous=NativeVillageExplorationTest.bounds(v.previousQuarter);
      try{
        // A HUD change after parent measurement moves only the head label and its union target.
        int left=Math.round(caption.left)-h.dp(48),top=Math.round(caption.top)-h.dp(4);
        v.previousQuarter.layout(left,top,left+h.dp(64),top+h.dp(48));v.layoutWorld();
        assertTrue("The fixture exercises a real change in the label/image union width",merchant.getWidth()>before);
        NativeVillageExplorationTest.assertRect(image,v.imageSlot(merchant),0);assertMeasuredTargets(v);
        for(int frame=0;frame<20;frame++){v.layoutWorld();assertMeasuredTargets(v);}
      }finally{
        v.previousQuarter.layout(Math.round(previous.left),Math.round(previous.top),Math.round(previous.right),Math.round(previous.bottom));v.layoutWorld();
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  static final class MeasureCounter extends View {
    int measurements;
    MeasureCounter(MainActivity h){super(h);}
    @Override protected void onMeasure(int width,int height){measurements++;setMeasuredDimension(MeasureSpec.getSize(width),MeasureSpec.getSize(height));}
  }

  @Test public void repeatedPlacementDoesNotRemeasureUntilTheRoundedSizeChanges()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeView v=open(h);MeasureCounter target=new MeasureCounter(h);
      RectF box=new RectF(10.49f,20.49f,100.51f,60.51f);v.place(target,box);
      assertEquals(1,target.measurements);assertEquals(91,target.getMeasuredWidth());assertEquals(41,target.getMeasuredHeight());
      for(int frame=0;frame<100;frame++)v.place(target,box);
      assertEquals("Stationary frames add no measurement work",1,target.measurements);
      box.offset(17,-9);v.place(target,box);assertEquals("Position-only changes reuse the measured size",1,target.measurements);
      box.right+=3;v.place(target,box);assertEquals("A changed target size is measured exactly once",2,target.measurements);assertEquals(94,target.getMeasuredWidth());assertEquals(target.getWidth(),target.getMeasuredWidth());
      v.place(target,box);assertEquals(2,target.measurements);
      box.setEmpty();v.place(target,box);assertEquals("Empty hidden targets keep a consistent zero size",0,target.getMeasuredWidth());assertEquals(0,target.getWidth());
    }
  }

  @Test public void oldDefaultMigratesOnceAndExplicitZoomOutPersistsAcrossPauseTokenRotationAndAccountChange()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);
      NativeVillageHomeController seed=new NativeVillageHomeController(h);
      seed.preferences.edit().putString(seed.scope+":zoom","1.0").putString(seed.scope+":zoom-pan","0.5").putString(seed.scope+":camera","8006.25").commit();
      NativeVillageHomeView v=open(h);
      assertEquals(1.2f,v.inspection.scale(),.00001f);assertEquals(1f,v.inspection.panNormalized(),.00001f);assertEquals(8006.25,v.camera.cameraUnits(),.000001);
      assertTrue(seed.preferences.getBoolean(seed.scope+NativeVillageHomeController.CAMERA_BASELINE,false));
      v.zoomAt(1f/v.inspection.scale(),v.field.centerX(),v.field.centerY());exploration.settle(h,v,NativeVillageHomeTest.PHONE);
      assertEquals("The old broad view remains a deliberate user choice",1f,v.inspection.scale(),.00001f);
      h.villageHome.pause();api.account="village-player.2.renewed";h.villageHome.resume();
      v=h.villageHome.view;exploration.settle(h,v,NativeVillageHomeTest.PHONE);
      assertEquals("One-time migration cannot override a later pinch",1f,v.inspection.scale(),.00001f);assertEquals(8006.25,v.camera.cameraUnits(),.000001);
      h.villageHome.pause();api.account="another-player.1.signature";h.villageHome.resume();v=h.villageHome.view;exploration.settle(h,v,NativeVillageHomeTest.PHONE);
      assertEquals("New account receives its own close baseline",1.2f,v.inspection.scale(),.00001f);assertEquals(1f,v.inspection.panNormalized(),.00001f);assertEquals(0,v.camera.cameraUnits(),.000001);
      h.villageHome.pause();api.account="village-player.3.returned";h.villageHome.resume();v=h.villageHome.view;exploration.settle(h,v,NativeVillageHomeTest.PHONE);
      assertEquals("Returning account retains its own explicit zoom",1f,v.inspection.scale(),.00001f);assertEquals(8006.25,v.camera.cameraUnits(),.000001);assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void priorDistinctInspectionAndVerticalPanSurviveMigrationResizeAndReentry()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeController seed=new NativeVillageHomeController(h);
      seed.preferences.edit().putString(seed.scope+":zoom","1.4").putString(seed.scope+":zoom-pan","0.23").commit();
      NativeVillageHomeView v=open(h);assertEquals(1.4f,v.inspection.scale(),.00001f);assertEquals(.23f,v.inspection.panNormalized(),.00001f);
      helper.configure(NativeVillageHomeTest.PROFILES[1]);helper.layout(h,NativeVillageHomeTest.PROFILES[1]);
      assertEquals(.23f,v.inspection.panNormalized(),.00001f);assertEquals((1-1.4f)*720*.23f,v.inspection.offsetY(),.001f);
      h.villageHome.pause();h.villageHome.resume();v=h.villageHome.view;exploration.settle(h,v,NativeVillageHomeTest.PROFILES[1]);
      assertEquals(1.4f,v.inspection.scale(),.00001f);assertEquals(.23f,v.inspection.panNormalized(),.00001f);assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void availableQuestPaintsOneHeadMarkerWhileAccessiblePrefixAndReadyCompletedWordsRemain()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=open(h);
      for(int i=0;i<api.story.getJSONArray("residents").length();i++)api.story.getJSONArray("residents").getJSONObject(i).put("status","AVAILABLE");
      api.story.put("revision",api.story.optLong("revision")+1);h.villageHome.refresh();exploration.settle(h,v,NativeVillageHomeTest.PHONE);
      for(VillageLayout.Resident resident:v.layout.residents){
        exploration.camera(h,v,resident.x-v.field.width()/v.unitPixels()/2);
        NativeVillageHomeView.WorldTarget name=(NativeVillageHomeView.WorldTarget)v.residentButtons.get(resident.id);
        assertTrue("The existing accessible quest prefix is retained",name.getText().toString().startsWith("! "));
        assertTrue(name.getContentDescription().toString().contains("새 부탁"));
        assertEquals("The head marker owns the one visible exclamation",resident.role,name.visibleCaption());
        Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);bitmap.recycle();
        RectF label=v.labelRect(new RectF(name.getLeft(),name.getTop(),name.getRight(),name.getBottom()),name),marker=v.residentMarkerRect(resident);
        assertFalse("Available status retains its actual head marker",marker.isEmpty());
        assertFalse("The marker cannot cover the actual role text",RectF.intersects(label,marker));
        Paint expected=new Paint(Paint.ANTI_ALIAS_FLAG);expected.setTypeface(h.font);expected.setTextSize(name.getTextSize()/v.inspection.scale());
        assertEquals("Only the displayed role determines the plaque width",expected.measureText(resident.role)+h.dp(24)/v.inspection.scale(),name.plateBounds.width(),.01f);
      }
      for(String state:new String[]{"READY","COMPLETED"}){
        for(int i=0;i<api.story.getJSONArray("residents").length();i++)api.story.getJSONArray("residents").getJSONObject(i).put("status",state);
        api.story.put("revision",api.story.optLong("revision")+1);h.villageHome.refresh();exploration.settle(h,v,NativeVillageHomeTest.PHONE);
        for(VillageLayout.Resident resident:v.layout.residents){
          NativeVillageHomeView.WorldTarget name=(NativeVillageHomeView.WorldTarget)v.residentButtons.get(resident.id);
          assertTrue("The displayed state words remain readable",name.visibleCaption().contains(state.equals("READY")?"보고":"완료"));
          assertEquals(name.getText().toString(),name.visibleCaption());assertTrue(v.residentMarkerRect(resident).isEmpty());
        }
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void edgeButtonsKeepQuarterTravelAndLoopWhileResidentHeadLabelRemainsTheSameTouchTarget()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=open(h);
      assertEquals(v.safe.left+h.dp(8),v.previousQuarter.getLeft(),.01f);assertEquals(v.safe.right-h.dp(8),v.nextQuarter.getRight(),.01f);
      for(Button b:new Button[]{v.previousQuarter,v.nextQuarter}){assertTrue(b.getText().toString().contains("90°"));assertTrue(b.getWidth()>=h.dp(48)&&b.getHeight()>=h.dp(48));assertTrue(v.safe.contains(NativeVillageExplorationTest.bounds(b)));}
      v.nextQuarter.performClick();exploration.finishMotion(h,v);assertEquals(2,v.camera.cameraUnits(),.000001);
      v.previousQuarter.performClick();exploration.finishMotion(h,v);assertEquals(0,v.camera.cameraUnits(),.000001);
      exploration.camera(h,v,8);assertEquals(8,v.camera.cameraUnits(),.000001);NativeVillageExplorationTest.assertNoGap(v);
      Button guard=v.residentButtons.get("guard");RectF label=v.labelRect(new RectF(guard.getLeft(),guard.getTop(),guard.getRight(),guard.getBottom()),guard);
      assertTrue("Resident caption is above the original picture",label.bottom+v.labelGap()<=v.imageSlot(guard).top+1);
      PointF p=new PointF();v.inspection.toScreen(label.centerX(),label.centerY(),p);
      assertTrue("Head label remains inside safe touch space",v.safe.contains(p.x,p.y));
      assertTrue(v.rawAlphaHitWorldButton(guard,label.centerX()-guard.getLeft(),label.centerY()-guard.getTop()));
      NativeVillageHomeTest.tapAt(v,p.x,p.y);assertNotNull(h.rosterStory);assertTrue("The moved label opens the same resident dialogue",h.rosterStory.villageOverlayOpen());assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void freshNarrowLandscapeCentersBothEntryPlaquesOnceAndSavedZeroStillBelongsToTheUser()throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-v109-initial");Files.createDirectories(out);JSONArray frames=new JSONArray();
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1],
        new NativeHomePartyFeedbackTest.Profile("initial-1280x800-font130",1280,800,160,1.3f,0,0,0,0),
        new NativeHomePartyFeedbackTest.Profile("initial-600x320-font130",600,320,160,1.3f,0,0,0,0),
        new NativeHomePartyFeedbackTest.Profile("initial-1180x720-insets-font130",1180,720,160,1.3f,42,12,26,18)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);exploration.settle(h,v,profile);v.ambient.fixedTime=0L;
      assertFalse("Initial focus is consumed once at the first valid layout",v.pendingInitialPairFocus);
      assertEquals(1.2f,v.inspection.scale(),.00001f);assertEquals(1f,v.inspection.panNormalized(),.00001f);
      boolean narrow=v.field.width()/v.field.height()<1.95f;
      PointF center=v.inspection.toWorld(v.safe.centerX(),v.safe.centerY(),new PointF());
      double expected=narrow?(v.layout.building("recruit").x+v.layout.building("hideout").x)/2-center.x/v.unitPixels():0;
      assertEquals("Only a fresh narrow view receives the pair center",expected,v.camera.cameraUnits(),.000001);
      JSONArray labels=new JSONArray();
      for(String id:new String[]{"recruit","hideout"}){
        Button b=v.buildingButtons.get(id);RectF label=v.screenRect(v.labelRect(new RectF(b.getLeft(),b.getTop(),b.getRight(),b.getBottom()),b));
        assertTrue("Both entry functions are fully readable on first visit: "+profile.name+" "+id+" "+label,v.safe.contains(label));
        for(View control:NativeVillageExplorationTest.fixed(v))if(control.getVisibility()==View.VISIBLE)assertFalse("Fresh entry caption cannot be hidden by HUD",RectF.intersects(label,NativeVillageExplorationTest.bounds(control)));
        labels.put(MainActivity.obj("id",id,"text",b.getText().toString(),"screen",NativeHomePartyFeedbackTest.box(label)));
      }
      Bitmap initial=NativeHomePartyFeedbackTest.render(h.root);String file=profile.name+"-fresh-initial.png";
      try{
        NativeVillageHomeArtTest.png(out.resolve(file),initial);
        double first=v.camera.cameraUnits();v.moveCameraUnits(8);exploration.settle(h,v,profile);
        Bitmap loop=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Fresh camera preserves an exact eight-unit visual closure",initial.sameAs(loop));}finally{loop.recycle();}
        v.moveCameraUnits(first+.37-v.camera.cameraUnits());exploration.settle(h,v,profile);double manual=v.camera.cameraUnits();h.villageHome.saveCamera();
        helper.layout(h,profile);assertEquals("Another layout cannot recenter a manual pan",manual,v.camera.cameraUnits(),.000001);
        NativeVillageHomeView returned=fixture.open(h,profile);exploration.settle(h,returned,profile);
        assertNotSame(v,returned);assertEquals("Reentry retains the saved manual camera",manual,returned.camera.cameraUnits(),.000001);
      }finally{initial.recycle();}
      assertTrue(api.writes.isEmpty());frames.put(MainActivity.obj("file",file,"profile",profile.json(),"freshNarrowCenter",narrow,"initialCameraU",expected,"scale",1.2,"panNormalized",1,"entryPlaques",labels,"exactEightUnitClosure",true,"apiWrites",api.writes.size()));
    }}
    helper.configure(NativeVillageHomeTest.PROFILES[1]);try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);NativeVillageHomeController seed=new NativeVillageHomeController(h);
      seed.preferences.edit().putString(seed.scope+":camera","0").commit();NativeVillageHomeView v=fixture.open(h,NativeVillageHomeTest.PROFILES[1]);exploration.settle(h,v,NativeVillageHomeTest.PROFILES[1]);
      assertEquals("A saved zero camera is never treated as a fresh visit",0,v.camera.cameraUnits(),0);assertFalse(v.pendingInitialPairFocus);
    }
    assertEquals(5,frames.length());NativeVillageHomeArtTest.json(out.resolve("geometry.json"),MainActivity.obj("scope","Actual native-Skia first-visit Views with controlled reads; no device/live-account evidence","frames",frames));
  }

  @Test public void actualFontsAndHeadCaptionsRenderInFourDistrictsWithoutHudOrFacilityCaptionOverlap()throws Exception{
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-v109");Files.createDirectories(out);JSONArray frames=new JSONArray();
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1],NativeVillageHomeTest.PROFILES[2],new NativeHomePartyFeedbackTest.Profile("village-640x320-font130",640,320,160,1.3f,0,0,0,0)};
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);exploration.settle(h,v,profile);v.ambient.fixedTime=0L;
      NativeVillageHomeArtTest.requireFonts(h);NativeVillageHomeArtTest.requireVerified(v.layout);
      assertEquals(1.2f,v.inspection.scale(),.00001f);assertEquals(1f,v.inspection.panNormalized(),.00001f);
      for(int district=0;district<4;district++){
        v.stopMotion();v.moveCameraUnits(district*2-v.desiredCamera.cameraUnits());exploration.settle(h,v,profile);NativeVillageExplorationTest.assertNoGap(v);
        Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);String file=profile.name+"-district-"+district+".png";try{NativeVillageHomeArtTest.png(out.resolve(file),bitmap);}finally{bitmap.recycle();}
        JSONArray captions=new JSONArray();
        for(Button b:NativeVillageHomeTest.worldTargets(v)){
          if(b.getVisibility()!=View.VISIBLE)continue;
          NativeVillageHomeView.WorldTarget target=(NativeVillageHomeView.WorldTarget)b;
          RectF world=v.labelRect(new RectF(b.getLeft(),b.getTop(),b.getRight(),b.getBottom()),b),screen=v.screenRect(world);
          assertTrue("Production game font fits its actual painted plaque",target.plateBounds.contains(target.letteringBounds));
          if(v.safe.contains(screen)){
            assertTrue(v.alphaHitWorldButton(b,world.centerX()-b.getLeft(),world.centerY()-b.getTop()));
            for(View fixed:NativeVillageExplorationTest.fixed(v))if(fixed.getVisibility()==View.VISIBLE)assertFalse("Readable caption cannot sit under fixed HUD: "+profile.name+" district="+district+" "+b.getTag()+" label="+screen+" control="+fixed.getTag()+" box="+NativeVillageExplorationTest.bounds(fixed),RectF.intersects(screen,NativeVillageExplorationTest.bounds(fixed)));
          }
          if(target.resident()){
            RectF image=v.imageSlot(b);assertTrue("Independent head label does not change or cover the portrait",world.bottom+v.labelGap()<=image.top+.01f);
            VillageLayout.Resident resident=v.layout.resident("patrol".equals(target.asset)?"guard":target.asset);
            if(resident.surface.isEmpty())assertEquals("Original authored resident feet remain fixed",resident.baseline*v.field.height(),image.bottom,.01);
          }
          if(target.resident())for(Button facility:v.buildingButtons.values()){
            RectF plaque=v.screenRect(v.labelRect(new RectF(facility.getLeft(),facility.getTop(),facility.getRight(),facility.getBottom()),facility));
            assertFalse("Head name cannot collide with facility plaque",RectF.intersects(screen,plaque));
          }
          captions.put(MainActivity.obj("tag",String.valueOf(b.getTag()),"text",target.visibleCaption(),"accessibleText",b.getText().toString(),"headLabel",target.resident(),"screen",NativeHomePartyFeedbackTest.box(screen),"textPx",b.getTextSize()));
        }
        frames.put(MainActivity.obj("file",file,"profile",profile.json(),"district",district,"scale",v.inspection.scale(),"panNormalized",v.inspection.panNormalized(),"captions",captions,"apiWrites",api.writes.size()));
      }
      assertTrue(api.writes.isEmpty());
    }}
    assertEquals(16,frames.length());NativeVillageHomeArtTest.json(out.resolve("geometry.json"),MainActivity.obj("scope","Actual native-Skia production Views with controlled reads; not physical-device evidence","frames",frames));
  }
}
