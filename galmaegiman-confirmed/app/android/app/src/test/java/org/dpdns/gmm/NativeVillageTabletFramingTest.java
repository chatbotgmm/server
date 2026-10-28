package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageTabletFramingTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 static final NativeHomePartyFeedbackTest.Profile[] TABLETS={new NativeHomePartyFeedbackTest.Profile("tablet-2048x1280-240dpi",2048,1280,240,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("tablet-2048x1280-320dpi-taskbar",2048,1280,320,1,0,0,0,80),NativeVillageHomeTest.PHONE};
 @Test public void actualSixteenTenAndPhoneAtFixedCameras()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:TABLETS){fixture.helper.configure(p);try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeTest.Api api=fixture.prepare(h);api.story.getJSONObject("statue").put("stage",2);NativeVillageHomeView v=fixture.open(h,p);v.ambient.fixedTime=1250L;
   for(double camera:new double[]{2.75,4.5,5.75,6.5}){v.moveCameraUnits(camera-v.camera.cameraUnits());for(int i=0;i<4;i++){LocalGameArtTest.drain();fixture.helper.layout(h,p);}assertTrue(v.sceneReady());Bitmap image=NativeHomePartyFeedbackTest.render(h.root);NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(p.name+"-u"+camera+".png"),image);image.recycle();}
  }}
 }
 @Test public void availableMarkerUsesActualHeadNotNameplateAndKeepsOtherStatuses()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{TABLETS[0],NativeVillageHomeTest.PHONE}){fixture.helper.configure(p);try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,p);v.ambient.fixedTime=0L;
   for(VillageLayout.Resident resident:v.layout.residents){v.owner.residents.get(resident.id).put("status","AVAILABLE");v.bindResidents();v.moveCameraUnits(resident.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());for(int i=0;i<4;i++){LocalGameArtTest.drain();fixture.helper.layout(h,p);}android.widget.Button b=v.residentButtons.get(resident.id);NativeVillageHomeView.WorldTarget target=(NativeVillageHomeView.WorldTarget)b;assertEquals(-1,target.statusCell());assertEquals(0,target.statusLane(),0);
    android.graphics.RectF marker=v.residentMarkerRect(resident),art=v.fittedObjectRect(v.assets.get(resident.asset),v.imageSlot(b),resident.asset);NativeVillageHomeView.ResidentHead head=v.residentHeads.get(resident.id);assertFalse(marker.isEmpty());assertTrue(marker.bottom<=art.top+art.height()*head.y);assertTrue(marker.centerX()>=art.left&&marker.centerX()<=art.right);for(int i=0;i<30;i++)v.residentMarkerRect(resident);assertSame(head,v.residentHeads.get(resident.id));assertEquals(4,v.residentButtons.size());
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(p.name+"-available-"+resident.id+".png"),image);image.recycle();
    for(String state:new String[]{"ACTIVE","READY","COMPLETED"}){v.owner.residents.get(resident.id).put("status",state);assertTrue(v.residentMarkerRect(resident).isEmpty());assertEquals("ACTIVE".equals(state)?-1:11,target.statusCell());}
   }
  }}
 }
 @Test public void fullSixteenTenLoopRetainsOriginalScaleAndExactBothDirections()throws Exception{
  NativeLayeredVillageLoopTest settle=new NativeLayeredVillageLoopTest();
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{TABLETS[0],TABLETS[1]}){fixture.helper.configure(p);try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,p);v.ambient.fixedTime=1250L;assertEquals(p.height,v.unitPixels(),0);
   for(int step=0;step<=32;step++){double camera=step*.25;settle.settle(h,v,p,camera);Bitmap first=NativeHomePartyFeedbackTest.render(h.root);
    try{NativeVillageHomeArtTest.png(NativeVillageHomeArtTest.output().resolve(p.name+String.format(java.util.Locale.ROOT,"-loop-u%05.2f.png",camera)),first);
     for(double offset:new double[]{8,-8}){settle.settle(h,v,p,camera+offset);assertEquals(camera+offset,v.camera.cameraUnits(),0);Bitmap loop=NativeHomePartyFeedbackTest.render(h.root);try{assertTrue("Exact 16:10 closure "+p.name+" "+camera+" "+offset,first.sameAs(loop));}finally{loop.recycle();}}
    }finally{first.recycle();}
   }
  }}
 }
 @Test public void headCacheHandlesEmptyAlphaAndBitmapReplacementWithoutNewDrawRectangles()throws Exception{
  NativeHomePartyFeedbackTest.Profile p=TABLETS[0];fixture.helper.configure(p);try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,p);VillageLayout.Resident resident=v.layout.residents.get(0);v.owner.residents.get(resident.id).put("status","AVAILABLE");v.bindResidents();v.moveCameraUnits(resident.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());for(int i=0;i<4;i++){LocalGameArtTest.drain();fixture.helper.layout(h,p);}
   Bitmap original=v.assets.images.get(resident.asset),empty=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888),opaque=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);opaque.eraseColor(android.graphics.Color.WHITE);android.graphics.RectF reused=new android.graphics.RectF();
   try{v.assets.images.put(resident.asset,empty);v.residentMarkerRect(resident,reused);assertTrue(reused.isEmpty());NativeVillageHomeView.ResidentHead emptyHead=v.residentHeads.get(resident.id);v.residentMarkerRect(resident,reused);assertSame(emptyHead,v.residentHeads.get(resident.id));
    v.assets.images.put(resident.asset,opaque);v.residentMarkerRect(resident,reused);assertFalse(reused.isEmpty());assertNotSame(emptyHead,v.residentHeads.get(resident.id));assertSame(opaque,v.residentHeads.get(resident.id).source.get());for(int i=0;i<100;i++)v.residentMarkerRect(resident,reused);
   }finally{v.assets.images.put(resident.asset,original);empty.recycle();opaque.recycle();}
  }
 }

}
