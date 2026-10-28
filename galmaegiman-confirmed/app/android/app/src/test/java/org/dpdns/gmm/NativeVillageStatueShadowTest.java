package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageStatueShadowTest {
 final NativeVillageBuildingPressTest helper=new NativeVillageBuildingPressTest();
 @Before public void setup(){helper.setup();}
 @After public void restore()throws Exception{helper.restore();}
 PointF statuePoint(NativeVillageHomeView v){RectF r=v.landmarkRect();for(int y=(int)r.top;y<r.bottom;y++)for(int x=(int)r.left;x<r.right;x++)if(v.statueHit(x+.5f,y+.5f))return new PointF(x+.5f,y+.5f);throw new AssertionError("Visible statue alpha required");}
 NativeVillageHomeView open(MainActivity h,NativeHomePartyFeedbackTest.Profile p,int stage)throws Exception{
  NativeVillageHomeTest.Api api=helper.fixture.prepare(h);api.story.getJSONObject("statue").put("stage",stage);NativeVillageHomeView v=helper.fixture.open(h,p);v.ambient.fixedTime=0L;v.moveCameraUnits(v.layout.landmark.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());helper.settle(h,v,p);return v;
 }
 boolean nearAlpha(NativeVillageHomeView v,Bitmap source,float x,float y){RectF r=v.fittedObjectRect(source,v.landmarkRect(),v.layout.landmark.asset(v.owner.statueStage));int n=v.h.dp(2)+2;for(int dy=-n;dy<=n;dy++)for(int dx=-n;dx<=n;dx++){float px=x+dx,py=y+dy;if(r.contains(px,py)&&Color.alpha(source.getPixel(Math.min(source.getWidth()-1,(int)((px-r.left)/r.width()*source.getWidth())),Math.min(source.getHeight()-1,(int)((py-r.top)/r.height()*source.getHeight()))))>0)return true;}return false;}
 @Test public void allFiveStagesUseActualAlphaAndRestoreExactPixelsAtPhoneAndTablet()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1]})for(int stage=1;stage<=5;stage++){
   helper.fixture.helper.configure(p);try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
    MainActivity h=a.get();NativeVillageHomeView v=open(h,p,stage);PointF point=statuePoint(v);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);helper.event(v,MotionEvent.ACTION_DOWN,point);assertSame(v.statue,v.pressedBuilding);Bitmap mask=v.outlineMask,source=v.assets.get(v.layout.landmark.asset(stage));assertSame(source,v.outlineSource);Bitmap pressed=NativeHomePartyFeedbackTest.render(h.root);int changes=0;
    for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++)if(before.getPixel(x,y)!=pressed.getPixel(x,y)){changes++;assertTrue("Statue alpha only",helper.roundingOnly(before.getPixel(x,y),pressed.getPixel(x,y))||nearAlpha(v,source,x+.5f,y+.5f));}
    assertTrue(changes>20);helper.capture(h,p.name+"-statue-stage-"+stage+"-pressed");helper.event(v,MotionEvent.ACTION_CANCEL,point);assertNull(v.pressedBuilding);Bitmap released=NativeHomePartyFeedbackTest.render(h.root);assertTrue(before.sameAs(released));helper.capture(h,p.name+"-statue-stage-"+stage+"-released");helper.event(v,MotionEvent.ACTION_DOWN,point);assertSame(mask,v.outlineMask);helper.event(v,MotionEvent.ACTION_CANCEL,point);before.recycle();pressed.recycle();released.recycle();
   }
  }
 }
 @Test public void statueTransparencyStageZeroDragOutsideFocusPauseAndReleaseRemainSafe()throws Exception{
  try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE,1);RectF r=v.landmarkRect();v.beginStatuePress(r.left+1,r.top+1);assertNull(v.pressedBuilding);PointF p=statuePoint(v);helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.statue,v.pressedBuilding);helper.event(v,MotionEvent.ACTION_MOVE,new PointF(p.x+v.slop*4,p.y));assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);
   helper.event(v,MotionEvent.ACTION_DOWN,p);helper.event(v,MotionEvent.ACTION_OUTSIDE,p);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);helper.event(v,MotionEvent.ACTION_DOWN,p);v.onWindowFocusChanged(false);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);v.onWindowFocusChanged(true);
   helper.event(v,MotionEvent.ACTION_DOWN,p);helper.event(v,MotionEvent.ACTION_UP,p);assertNull(v.pressedBuilding);LocalGameArtTest.drain();assertTrue(h.rosterStory.villageOverlayOpen());h.rosterStory.back();LocalGameArtTest.drain();helper.event(v,MotionEvent.ACTION_DOWN,p);h.villageHome.pause();assertNull(v.pressedBuilding);v.owner.statueStage=0;v.beginStatuePress(p.x,p.y);assertNull(v.pressedBuilding);assertFalse(v.statueHit(p.x,p.y));
  }
 }
 @Test public void stageChangesAndSecondPointerCannotKeepAnOldStatueMask()throws Exception{
  try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE,1);PointF p=statuePoint(v);helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.statue,v.pressedBuilding);v.owner.statueStage=2;v.updateNotice();assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);v.owner.statueStage=1;v.updateNotice();
   helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.statue,v.pressedBuilding);helper.event(v,MotionEvent.ACTION_POINTER_DOWN,p);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);
   v.owner.statueStage=0;v.updateNotice();v.beginStatuePress(p.x,p.y);assertNull(v.pressedBuilding);assertFalse(v.statueHit(p.x,p.y));v.owner.statueStage=1;v.updateNotice();
   helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.statue,v.pressedBuilding);h.villageHome.pause();v.beginStatuePress(p.x,p.y);assertNull(v.pressedBuilding);
  }
 }
 @Test public void fourResidentPaintedContactsStayAtFeetAndNeverBecomeLargeGroundDiscs()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1]}){
   helper.fixture.helper.configure(p);try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
    MainActivity h=a.get();NativeVillageHomeView v=helper.open(h,p);
    for(VillageLayout.Resident resident:v.layout.residents){v.moveCameraUnits(resident.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());helper.settle(h,v,p);Button b=v.residentButtons.get(resident.id);Bitmap shadowOnly=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);v.groundShadows.drawActor(new Canvas(shadowOnly),v,resident);RectF contact=new RectF(v.groundShadows.target);float feet=b.getBottom()-v.labelHeight(b)-v.labelGap();assertTrue(contact.top<feet&&contact.bottom>feet);assertTrue(contact.height()<v.field.height()*.09f);assertTrue(contact.width()<v.field.height()*.36f);int pixels=0;for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++){int alpha=Color.alpha(shadowOnly.getPixel(x,y));if(alpha>0){pixels++;assertTrue(alpha<=103);assertTrue(contact.contains(x+.5f,y+.5f));}}assertTrue(pixels>20);shadowOnly.recycle();
     Bitmap with=NativeHomePartyFeedbackTest.render(h.root);helper.capture(h,p.name+"-resident-"+resident.id+"-contact");Bitmap atlas=v.assets.images.remove("companions-shadows");Bitmap without=NativeHomePartyFeedbackTest.render(h.root);v.assets.images.put("companions-shadows",atlas);assertFalse("Visible subtle contact",with.sameAs(without));int delta=0;for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++)if(with.getPixel(x,y)!=without.getPixel(x,y)){delta++;}assertTrue(delta>10);with.recycle();without.recycle();
    }
   }
  }
 }
}
