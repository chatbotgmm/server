package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageResidentPressTest {
 final NativeVillageBuildingPressTest helper=new NativeVillageBuildingPressTest();
 @Before public void setup(){helper.setup();}
 @After public void restore()throws Exception{helper.restore();}
 void focus(MainActivity h,NativeVillageHomeView v,String id,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  for(VillageLayout.Resident resident:v.layout.residents)if(resident.id.equals(id)){v.moveCameraUnits(resident.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());helper.settle(h,v,profile);return;}
  fail("Unknown resident "+id);
 }
 @Test public void allFourResidentsHaveOnlySilhouetteFeedbackAndExactReleaseAtPhoneAndTablet()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1]}){
   helper.fixture.helper.configure(profile);try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
    MainActivity h=a.get();NativeVillageHomeView v=helper.open(h,profile);
    for(String id:new String[]{"guard","librarian","merchant","blacksmith"}){
     focus(h,v,id,profile);Button b=v.residentButtons.get(id);PointF p=NativeVillageHomeTest.opaqueArtPoint(v,b);
     Bitmap before=NativeHomePartyFeedbackTest.render(h.root);helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(id,b,v.pressedBuilding);Bitmap mask=v.outlineMask;assertNotNull(mask);
     Bitmap pressed=NativeHomePartyFeedbackTest.render(h.root);RectF bounds=NativeVillageHomeTest.shownArt(v,b);bounds.inset(-h.dp(3),-h.dp(3));int changed=0;
     for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++)if(before.getPixel(x,y)!=pressed.getPixel(x,y)){
      changed++;assertTrue("Only resident alpha contour: "+id+" "+x+","+y,bounds.contains(x+.5f,y+.5f)&&(helper.roundingOnly(before.getPixel(x,y),pressed.getPixel(x,y))||helper.nearAuthoredAlpha(v,b,x+.5f,y+.5f)));
     }
     assertTrue(id+" has visible contour",changed>20);helper.capture(h,profile.name+"-resident-"+id+"-pressed");
     helper.event(v,MotionEvent.ACTION_CANCEL,p);assertNull(v.pressedBuilding);Bitmap released=NativeHomePartyFeedbackTest.render(h.root);assertTrue(id+" exact pixels restored",before.sameAs(released));helper.capture(h,profile.name+"-resident-"+id+"-released");
     helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame("Repeated press reuses alpha mask",mask,v.outlineMask);helper.event(v,MotionEvent.ACTION_CANCEL,p);
     before.recycle();pressed.recycle();released.recycle();
    }
   }
  }
 }
 @Test public void residentTransparentDragOutsideFocusPauseAndSecondPointerClearWithoutNavigation()throws Exception{
  try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=helper.open(h,NativeVillageHomeTest.PHONE);focus(h,v,"librarian",NativeVillageHomeTest.PHONE);Button b=v.residentButtons.get("librarian");
   // Resident transparent pixels can legitimately expose the library behind them.
   // Check this resident's own dispatch rejects the hole, not the unrelated underlying building.
   PointF blank=null;RectF art=NativeVillageHomeTest.shownArt(v,b);
   for(int y=(int)art.top;y<art.bottom&&blank==null;y++)for(int x=(int)art.left;x<art.right;x++)if(v.safe.contains(x+.5f,y+.5f)&&NativeVillageHomeTest.sourceAlphaAt(v,b,x+.5f,y+.5f)==0&&!v.rawAlphaHitWorldButton(b,x+.5f-b.getLeft(),y+.5f-b.getTop())){blank=new PointF(x+.5f,y+.5f);break;}
   assertNotNull("Actual transparent resident pixel",blank);long blankTime=SystemClock.uptimeMillis();MotionEvent blankDown=MotionEvent.obtain(blankTime,blankTime,MotionEvent.ACTION_DOWN,blank.x-b.getLeft(),blank.y-b.getTop(),0);try{assertFalse(b.dispatchTouchEvent(blankDown));}finally{blankDown.recycle();}assertNull(v.pressedBuilding);
   PointF p=NativeVillageHomeTest.opaqueArtPoint(v,b);double camera=v.camera.cameraUnits();helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(b,v.pressedBuilding);helper.event(v,MotionEvent.ACTION_MOVE,new PointF(p.x+v.slop*4,p.y));assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_MOVE,new PointF(p.x+v.slop*6,p.y));helper.event(v,MotionEvent.ACTION_CANCEL,p);assertNotEquals(camera,v.camera.cameraUnits(),0);
   assertFalse(h.rosterStory!=null&&h.rosterStory.villageOverlayOpen());focus(h,v,"librarian",NativeVillageHomeTest.PHONE);p=NativeVillageHomeTest.opaqueArtPoint(v,b);
   helper.event(v,MotionEvent.ACTION_DOWN,p);helper.event(v,MotionEvent.ACTION_OUTSIDE,p);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);
   helper.event(v,MotionEvent.ACTION_DOWN,p);v.onWindowFocusChanged(false);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);v.onWindowFocusChanged(true);
   helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(b,v.pressedBuilding);
   MotionEvent.PointerProperties[] props={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int i=0;i<2;i++){props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].x=p.x+i*10;coords[i].y=p.y;coords[i].pressure=1;coords[i].size=1;}
   long t=SystemClock.uptimeMillis();MotionEvent multi=MotionEvent.obtain(t,t,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,coords,0,0,1,1,0,0,0,0);try{v.dispatchTouchEvent(multi);}finally{multi.recycle();}assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);
   helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(b,v.pressedBuilding);h.villageHome.pause();assertNull(v.pressedBuilding);
  }
 }
 @Test public void residentReleaseOpensExistingDialogueWithNoStickyOutline()throws Exception{
  try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=helper.open(h,NativeVillageHomeTest.PHONE);focus(h,v,"librarian",NativeVillageHomeTest.PHONE);Button b=v.residentButtons.get("librarian");PointF p=NativeVillageHomeTest.opaqueArtPoint(v,b);double camera=v.camera.cameraUnits();
   helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(b,v.pressedBuilding);helper.event(v,MotionEvent.ACTION_UP,p);assertNull(v.pressedBuilding);LocalGameArtTest.drain();assertNotNull(h.rosterStory);assertTrue(h.rosterStory.villageOverlayOpen());assertSame(v,h.villageHome.view);assertEquals(camera,v.camera.cameraUnits(),0);
   h.rosterStory.back();LocalGameArtTest.drain();assertNull(v.pressedBuilding);assertSame(v,h.villageHome.view);assertEquals(camera,v.camera.cameraUnits(),0);
  }
 }
}
