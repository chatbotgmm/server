package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;
import java.nio.file.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageBuildingPressTest {
 final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
 @Before public void setup(){fixture.setup();}
 @After public void restore()throws Exception{fixture.restore();}
 NativeVillageHomeView open(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  fixture.prepare(h);NativeVillageHomeView v=fixture.open(h,profile);settle(h,v,profile);v.stopMotion();v.ambient.fixedTime=0L;return v;
 }
 void settle(MainActivity h,NativeVillageHomeView v,NativeHomePartyFeedbackTest.Profile p)throws Exception{for(int i=0;i<3;i++){LocalGameArtTest.drain();fixture.helper.layout(h,p);}assertTrue(v.assets.ready());}
 void event(NativeVillageHomeView v,int action,PointF point){long t=SystemClock.uptimeMillis();NativeVillageHomeTest.touch(v,action,t,t,point.x,point.y);}
 void capture(MainActivity h,String name)throws Exception{java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"building-press");Files.createDirectories(out);Bitmap b=NativeHomePartyFeedbackTest.render(h.root);try{NativeVillageHomeArtTest.png(out.resolve(name+".png"),b);}finally{b.recycle();}}
 boolean roundingOnly(int a,int b){
  // Native filtered alpha compositing can round one RGB level even at a zero-alpha texel.
  return Color.alpha(a)==Color.alpha(b)&&Math.abs(Color.red(a)-Color.red(b))<=1&&Math.abs(Color.green(a)-Color.green(b))<=1&&Math.abs(Color.blue(a)-Color.blue(b))<=1;
 }
 boolean nearAuthoredAlpha(NativeVillageHomeView v,Button b,float x,float y){
  // Filtered source alpha includes soft pixels outside the artist's alpha>127 content bbox.
  // Validate the actual sampled alpha, never a rectangular canvas outline.
  int radius=v.h.dp(2)+2;for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)
   if(NativeVillageHomeTest.sourceAlphaAt(v,b,x+dx,y+dy)>0)return true;
  return false;
 }
 @Test public void actualSilhouettePressAndReleaseAtPhoneAndTabletPreserveArtworkAndReuseMask()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1]}){
   for(String id:new String[]{"recruit","archive","training"}){fixture.helper.configure(p);try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
    MainActivity h=a.get();NativeVillageHomeView v=open(h,p);v.moveCameraUnits(v.layout.building(id).x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());settle(h,v,p);Button b=v.buildingButtons.get(id);PointF point=NativeVillageHomeTest.opaqueArtPoint(v,b);
    Bitmap before=NativeHomePartyFeedbackTest.render(h.root);event(v,MotionEvent.ACTION_DOWN,point);assertSame(b,v.pressedBuilding);Bitmap mask=v.outlineMask;assertNotNull(mask);
    Bitmap pressed=NativeHomePartyFeedbackTest.render(h.root);int changed=0;RectF silhouette=NativeVillageHomeTest.shownArt(v,b);silhouette.inset(-h.dp(3),-h.dp(3));
    capture(h,p.name+"-"+id+"-pressed-diagnostic");
    for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++)if(before.getPixel(x,y)!=pressed.getPixel(x,y)){changed++;assertTrue("Only the building silhouette changes: "+id+" at "+x+","+y+" colors "+Integer.toHexString(before.getPixel(x,y))+"/"+Integer.toHexString(pressed.getPixel(x,y))+" expected "+silhouette,silhouette.contains(x+.5f,y+.5f)&&(roundingOnly(before.getPixel(x,y),pressed.getPixel(x,y))||nearAuthoredAlpha(v,b,x+.5f,y+.5f)));}
    assertTrue("Visible black contour",changed>40);capture(h,p.name+"-"+id+"-pressed");
    event(v,MotionEvent.ACTION_CANCEL,point);assertNull(v.pressedBuilding);Bitmap released=NativeHomePartyFeedbackTest.render(h.root);assertTrue("Cancel restores exact original pixels",before.sameAs(released));capture(h,p.name+"-"+id+"-released");
    event(v,MotionEvent.ACTION_DOWN,point);assertSame(mask,v.outlineMask);event(v,MotionEvent.ACTION_UP,point);assertNull(v.pressedBuilding);
    before.recycle();pressed.recycle();released.recycle();
   }}
  }
 }
 @Test public void transparentPixelsDragPauseAndFocusLossNeverKeepSelection()throws Exception{
  try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE);Button b=v.buildingButtons.get("recruit");PointF p=NativeVillageHomeTest.isolatedTransparentArtPoint(v,b);
   event(v,MotionEvent.ACTION_DOWN,p);assertNull(v.pressedBuilding);event(v,MotionEvent.ACTION_CANCEL,p);
   p=NativeVillageHomeTest.opaqueArtPoint(v,v.residentButtons.get("guard"));event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.residentButtons.get("guard"),v.pressedBuilding);event(v,MotionEvent.ACTION_CANCEL,p);assertNull(v.pressedBuilding);
   p=NativeVillageHomeTest.opaqueArtPoint(v,b);event(v,MotionEvent.ACTION_DOWN,p);event(v,MotionEvent.ACTION_MOVE,new PointF(p.x+v.slop*3,p.y));assertNull(v.pressedBuilding);event(v,MotionEvent.ACTION_CANCEL,p);
   p=NativeVillageHomeTest.opaqueArtPoint(v,b);event(v,MotionEvent.ACTION_DOWN,p);v.onWindowFocusChanged(false);assertNull(v.pressedBuilding);event(v,MotionEvent.ACTION_CANCEL,p);
   event(v,MotionEvent.ACTION_DOWN,p);h.villageHome.pause();assertNull(v.pressedBuilding);
  }
 }
 boolean solidQuay(NativeVillageHomeView v,int x,int y){
  VillageLayout.DistrictScenery item=v.layout.districtScenery(v.layout.layeredWorld?"layered-quay":"harbor-quay");Bitmap bitmap=v.assets.get(item.asset);
  if(!v.districtScene.geometry(v,item,bitmap))return false;RectF target=v.districtScene.target;Rect source=v.districtScene.source;
  if(x<target.left+3||x>=target.right-3||y<target.top+target.height()*(float)item.clipSourceTop+3||y>=target.bottom-3)return false;
  int sx=source.left+(int)((x-target.left)/target.width()*source.width()),sy=source.top+(int)((y-target.top)/target.height()*source.height());
  // A filtered semi-transparent shore edge may naturally reveal the layer beneath.
  // Authored stone has alpha250–253, not255: at most six channel levels may transmit.
  for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)if(sx+dx<0||sy+dy<0||sx+dx>=bitmap.getWidth()||sy+dy>=bitmap.getHeight()||Color.alpha(bitmap.getPixel(sx+dx,sy+dy))<250)return false;
  return true;
 }
 @Test public void boatContourIsBehindQuayAndReleaseNeverLeavesHiddenHullHighlight()throws Exception{
  try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE);v.moveCameraUnits(3.75-v.camera.cameraUnits());settle(h,v,NativeVillageHomeTest.PHONE);
   Button boat=v.buildingButtons.get("harbor");PointF p=NativeVillageHomeTest.opaqueArtPoint(v,boat);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);event(v,MotionEvent.ACTION_DOWN,p);assertSame(boat,v.pressedBuilding);Bitmap after=NativeHomePartyFeedbackTest.render(h.root);int protectedPixels=0;
   for(int y=0;y<before.getHeight();y+=2)for(int x=0;x<before.getWidth();x+=2)if(solidQuay(v,x,y)){int old=before.getPixel(x,y),now=after.getPixel(x,y);assertTrue("Quay alpha bounds any outline contribution",Math.abs(Color.red(old)-Color.red(now))<=6&&Math.abs(Color.green(old)-Color.green(now))<=6&&Math.abs(Color.blue(old)-Color.blue(now))<=6);protectedPixels++;}
   assertTrue(protectedPixels>100);capture(h,"boat-pressed");event(v,MotionEvent.ACTION_CANCEL,p);assertNull(v.pressedBuilding);Bitmap released=NativeHomePartyFeedbackTest.render(h.root);assertTrue(before.sameAs(released));capture(h,"boat-released");before.recycle();after.recycle();released.recycle();
  }
 }
 @Test public void secondPointerClearsAndCannotTransferPressedBuilding()throws Exception{
  try(ActivityController<MainActivity> a=fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE);PointF p=NativeVillageHomeTest.opaqueArtPoint(v,v.buildingButtons.get("recruit"));event(v,MotionEvent.ACTION_DOWN,p);assertNotNull(v.pressedBuilding);
   MotionEvent.PointerProperties[] props={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int i=0;i<2;i++){props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].x=p.x+i*10;coords[i].y=p.y;coords[i].pressure=1;coords[i].size=1;}
   long t=SystemClock.uptimeMillis();MotionEvent multi=MotionEvent.obtain(t,t,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,coords,0,0,1,1,0,0,0,0);try{v.dispatchTouchEvent(multi);}finally{multi.recycle();}assertNull(v.pressedBuilding);event(v,MotionEvent.ACTION_CANCEL,p);
  }
 }
}
