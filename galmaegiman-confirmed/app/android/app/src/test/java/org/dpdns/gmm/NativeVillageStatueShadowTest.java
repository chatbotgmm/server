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
 boolean nearAlpha(NativeVillageHomeView v,Bitmap source,float x,float y){
  RectF r=v.fittedObjectRect(source,v.landmarkRect(),v.layout.landmark.asset(v.owner.statueStage));int n=v.h.dp(2)+2;
  PointF first=v.inspection.toWorld(x-n,y-n,new PointF()),last=v.inspection.toWorld(x+n,y+n,new PointF());RectF window=new RectF(first.x,first.y,last.x,last.y);
  if(!window.intersect(r))return false;
  // Inspect every texel under the unchanged physical radius. Integer screen steps
  // can skip sparse alpha when this sampled source is reduced during rendering.
  int left=Math.max(0,(int)Math.floor((window.left-r.left)/r.width()*source.getWidth())),top=Math.max(0,(int)Math.floor((window.top-r.top)/r.height()*source.getHeight()));
  int right=Math.min(source.getWidth()-1,(int)Math.floor((window.right-r.left)/r.width()*source.getWidth())),bottom=Math.min(source.getHeight()-1,(int)Math.floor((window.bottom-r.top)/r.height()*source.getHeight()));
  for(int py=top;py<=bottom;py++)for(int px=left;px<=right;px++)if(Color.alpha(source.getPixel(px,py))>0)return true;
  return false;
 }
 int nearbyAlphaDiagnostic(NativeVillageHomeView v,Bitmap source,float x,float y){
  RectF r=v.fittedObjectRect(source,v.landmarkRect(),v.layout.landmark.asset(v.owner.statueStage));PointF world=new PointF();int maximum=0,n=(v.h.dp(2)+2)*4;
  // Dense samples remain diagnostic only; the guard scans the complete source window.
  for(int dy=-n;dy<=n;dy++)for(int dx=-n;dx<=n;dx++){
   v.inspection.toWorld(x+dx*.25f,y+dy*.25f,world);if(!r.contains(world.x,world.y))continue;
   int px=Math.min(source.getWidth()-1,(int)((world.x-r.left)/r.width()*source.getWidth())),py=Math.min(source.getHeight()-1,(int)((world.y-r.top)/r.height()*source.getHeight()));maximum=Math.max(maximum,Color.alpha(source.getPixel(px,py)));
  }
  return maximum;
 }
 @Test public void allFiveStagesUseActualAlphaAndRestoreExactPixelsAtPhoneAndTablet()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile p:new NativeHomePartyFeedbackTest.Profile[]{NativeVillageHomeTest.PHONE,NativeVillageHomeTest.PROFILES[1]})for(int stage=1;stage<=5;stage++){
   helper.fixture.helper.configure(p);try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
    MainActivity h=a.get();NativeVillageHomeView v=open(h,p,stage);PointF point=statuePoint(v);Bitmap before=NativeHomePartyFeedbackTest.render(h.root);helper.event(v,MotionEvent.ACTION_DOWN,point);assertSame(v.statue,v.pressedBuilding);Bitmap mask=v.outlineMask,source=v.assets.get(v.layout.landmark.asset(stage));assertSame(source,v.outlineSource);Bitmap pressed=NativeHomePartyFeedbackTest.render(h.root);int changes=0;
    for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++)if(before.getPixel(x,y)!=pressed.getPixel(x,y)){
     changes++;int old=before.getPixel(x,y),now=pressed.getPixel(x,y);boolean allowed=helper.roundingOnly(old,now)||nearAlpha(v,source,x+.5f,y+.5f);
     if(!allowed){
      java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"building-press");java.nio.file.Files.createDirectories(out);String name=p.name+"-statue-stage-"+stage+"-alpha-diagnostic";
      NativeVillageHomeArtTest.png(out.resolve(name+"-before.png"),before);NativeVillageHomeArtTest.png(out.resolve(name+"-after.png"),pressed);
      RectF worldRect=v.fittedObjectRect(source,v.landmarkRect(),v.layout.landmark.asset(stage));PointF world=v.inspection.toWorld(x+.5f,y+.5f,new PointF());
      fail("Statue alpha only; profile="+p.name+" stage="+stage+" pixel="+x+","+y+" colors="+Integer.toHexString(old)+"/"+Integer.toHexString(now)+" nearbyAlpha(sourceWindow)=0 nearbyAlpha(quarterPixelGrid)="+nearbyAlphaDiagnostic(v,source,x+.5f,y+.5f)+" sourceSize="+source.getWidth()+"x"+source.getHeight()+" sourceCrop="+java.util.Arrays.toString(v.layout.assets.get(v.layout.landmark.asset(stage)).crop)+" scale="+v.inspection.scale()+" worldPoint="+world+" worldRect="+worldRect+" outlineRect="+v.outlineTarget);
     }
    }
    assertTrue(changes>20);helper.capture(h,p.name+"-statue-stage-"+stage+"-pressed");helper.event(v,MotionEvent.ACTION_CANCEL,point);assertNull(v.pressedBuilding);Bitmap released=NativeHomePartyFeedbackTest.render(h.root);assertTrue(before.sameAs(released));helper.capture(h,p.name+"-statue-stage-"+stage+"-released");helper.event(v,MotionEvent.ACTION_DOWN,point);assertSame(mask,v.outlineMask);helper.event(v,MotionEvent.ACTION_CANCEL,point);before.recycle();pressed.recycle();released.recycle();
   }
  }
 }
 @Test public void statueTransparencyStageZeroDragOutsideFocusPauseAndReleaseRemainSafe()throws Exception{
  try(ActivityController<MainActivity> a=helper.fixture.helper.flow.start()){
   MainActivity h=a.get();NativeVillageHomeView v=open(h,NativeVillageHomeTest.PHONE,1);RectF r=v.landmarkRect();v.beginStatuePress(r.left+1,r.top+1);assertNull(v.pressedBuilding);PointF p=statuePoint(v);helper.event(v,MotionEvent.ACTION_DOWN,p);assertSame(v.statue,v.pressedBuilding);helper.event(v,MotionEvent.ACTION_MOVE,new PointF(p.x+v.slop*4,p.y));assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);
   helper.event(v,MotionEvent.ACTION_DOWN,p);helper.event(v,MotionEvent.ACTION_OUTSIDE,p);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);helper.event(v,MotionEvent.ACTION_DOWN,p);v.onWindowFocusChanged(false);assertNull(v.pressedBuilding);helper.event(v,MotionEvent.ACTION_CANCEL,p);v.onWindowFocusChanged(true);
   helper.event(v,MotionEvent.ACTION_DOWN,p);helper.event(v,MotionEvent.ACTION_UP,p);assertNull(v.pressedBuilding);LocalGameArtTest.drain();assertTrue(h.rosterStory.villageOverlayOpen());h.rosterStory.back();LocalGameArtTest.drain();v.owner.statueStage=0;v.updateNotice();v.beginStatuePress(p.x,p.y);assertNull(v.pressedBuilding);assertFalse(v.statueHit(p.x,p.y));v.owner.statueStage=1;v.updateNotice();helper.event(v,MotionEvent.ACTION_DOWN,p);h.villageHome.pause();assertNull(v.pressedBuilding);v.owner.statueStage=0;v.beginStatuePress(p.x,p.y);assertNull(v.pressedBuilding);
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
    for(VillageLayout.Resident resident:v.layout.residents){v.moveCameraUnits(resident.x-v.field.width()/v.unitPixels()/2-v.camera.cameraUnits());helper.settle(h,v,p);Button b=v.residentButtons.get(resident.id);Bitmap shadowOnly=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(shadowOnly);canvas.translate(v.inspection.offsetX(),v.inspection.offsetY());canvas.scale(v.inspection.scale(),v.inspection.scale());v.groundShadows.drawActor(canvas,v,resident);RectF contact=v.screenRect(v.groundShadows.target);float feet=v.screenRect(v.imageSlot(b)).bottom;assertTrue(contact.top<feet&&contact.bottom>feet);assertTrue(contact.height()<v.field.height()*.09f*v.inspection.scale());assertTrue(contact.width()<v.field.height()*.36f*v.inspection.scale());int pixels=0;for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++){int alpha=Color.alpha(shadowOnly.getPixel(x,y));if(alpha>0){pixels++;assertTrue(alpha<=103);assertTrue(contact.contains(x+.5f,y+.5f));}}assertTrue(pixels>20);shadowOnly.recycle();
     Bitmap with=NativeHomePartyFeedbackTest.render(h.root);helper.capture(h,p.name+"-resident-"+resident.id+"-contact");Bitmap atlas=v.assets.images.remove("companions-shadows");Bitmap without=NativeHomePartyFeedbackTest.render(h.root);v.assets.images.put("companions-shadows",atlas);assertFalse("Visible subtle contact",with.sameAs(without));int delta=0;for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++)if(with.getPixel(x,y)!=without.getPixel(x,y)){delta++;}assertTrue(delta>10);with.recycle();without.recycle();
    }
   }
  }
 }
}
