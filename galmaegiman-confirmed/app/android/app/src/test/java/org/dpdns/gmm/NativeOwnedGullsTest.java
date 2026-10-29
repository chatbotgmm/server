package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeOwnedGullsTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();fixture.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore()throws Exception{LocalGameArtTest.drain();fixture.restore();}
  static JSONObject stats(int hp){return MainActivity.obj("hp",hp,"mp",hp+1,"durability",hp+2,"statusResistance",hp+3,"speed",hp+4);}
  static JSONObject summary(String id,String species,int hp){return MainActivity.obj("ownedId",id,"speciesId",species,"name","갈매미 "+id,"level",hp,"maxLevel",60,"reincarnation",0,"status","AVAILABLE","locked",false,"stats",stats(hp));}
  static JSONObject unit(String id,String species,int hp){
    JSONObject skill=MainActivity.obj("id","hit","name","몸통 공격","element","NORMAL","power",12,"mpCost",2,"learnLevel",1,"effectText","위력 12");
    JSONObject element=MainActivity.obj();for(String e:NativeOwnedGulls.ELEMENTS)try{element.put(e,"normal");}catch(Exception bad){throw new AssertionError(bad);}
    JSONObject result=summary(id,species,hp);
    try{result.put("growth",MainActivity.obj("totalXp",hp*100,"xp",10,"xpToNext",20,"levelStartTotalXp",hp*100-10,"nextLevelTotalXp",hp*100+20));result.put("statBreakdown",MainActivity.obj("base",stats(1),"growth",stats(2),"rebirth",stats(3),"total",stats(hp),"other",JSONObject.NULL,"otherStatus","NO_OTHER_BONUS_MODEL"));result.put("aptitude",element);result.put("ownResistance",element);result.put("skills",MainActivity.obj("attack",skill,"current",new JSONArray().put(skill),"nextLearn",MainActivity.obj("level",hp+1,"skills",new JSONArray().put(MainActivity.obj("id","next","name","다음 기술","mpCost",4,"learnLevel",hp+1,"effectText","위력 20"))),"growth",MainActivity.obj("status","DESIGN_NEEDED")));result.put("party",MainActivity.obj("pvp",MainActivity.obj("position",id.equals("b")?1:2,"size",2),"activity","AVAILABLE"));}catch(Exception bad){throw new AssertionError(bad);}return result;
  }
  static JSONObject list(JSONObject... values){JSONArray units=new JSONArray();for(JSONObject value:values)units.put(value);return MainActivity.obj("flow",NativeOwnedGulls.FLOW,"catalogVersion","owned-test-v1","serverNow",1000,"units",units);}
  static JSONObject detail(JSONObject unit){return MainActivity.obj("flow",NativeOwnedGulls.FLOW,"catalogVersion","owned-test-v1","serverNow",1001,"unit",unit);}
  static final class Call {
    final String path;final JSONObject body;final NativeApi.Result reply;
    Call(String path,JSONObject body,NativeApi.Result reply){this.path=path;this.body=body;this.reply=reply;}
  }
  static final class Api extends NativeApi {
    String account="owned-one.1.signature";
    final ArrayList<Call> calls=new ArrayList<>();
    Api(Context c){super(c);}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result reply){assertTrue("Owned screen performs only its list/detail reads: "+path,path.equals("native/roster60/owned")||path.equals("native/roster60/owned/read"));calls.add(new Call(path,body,reply));}
    Call lastList(){for(int i=calls.size()-1;i>=0;i--)if(calls.get(i).body==null)return calls.get(i);throw new AssertionError("No list");}
    Call last(String id){for(int i=calls.size()-1;i>=0;i--)if(calls.get(i).body!=null&&id.equals(calls.get(i).body.optString("ownedId")))return calls.get(i);throw new AssertionError("No detail "+id);}
    List<String> detailIds(){ArrayList<String> ids=new ArrayList<>();for(Call c:calls)if(c.body!=null)ids.add(c.body.optString("ownedId"));return ids;}
  }
  Api prepare(MainActivity h){h.api.close();Api api=new Api(h);h.api=api;h.guide.enabled=false;h.motionEnabled=false;h.state=MainActivity.obj("me",MainActivity.obj("nickname","보유 상세 시험"));return api;}
  NativeOwnedGulls open(MainActivity h,Api api,JSONObject... units){return open(h,api,NativeHomePartyFeedbackTest.PROFILES[0],units);}
  NativeOwnedGulls open(MainActivity h,Api api,NativeHomePartyFeedbackTest.Profile profile,JSONObject... units){h.openOwnedGulls();NativeOwnedGulls view=h.ownedGulls;api.lastList().reply.done(list(units),null);fixture.layout(h,profile);return view;}
  void respond(Api api,String id,String species,int hp){api.last(id).reply.done(detail(unit(id,species,hp)),null);}
  static void touch(NativeOwnedGulls.Portrait view,int action,float x,float y){long now=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);view.onTouchEvent(event);event.recycle();}

  @Test public void exactIndividualsStayDistinctAndOnlyAdjacentDetailsArePrefetched()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);String state=h.state.toString();
      NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u01",9),summary("a","u01",2),summary("c","u02",12),summary("d","u03",14));
      assertEquals(4,view.entries.size());assertEquals(Arrays.asList("a","b"),api.detailIds());assertNull(view.shown);assertEquals("",view.name.getText().toString());
      respond(api,"a","u01",2);respond(api,"b","u01",9);assertEquals("a",view.shown.getString("ownedId"));
      view.next.performClick();assertEquals("b",view.selectedId);assertEquals("갈매미 b\nLv.9",view.name.getText().toString());assertTrue(view.vitals.getText().toString().contains("HP 9"));assertEquals(Arrays.asList("a","b","c"),api.detailIds());
      respond(api,"c","u02",12);view.next.performClick();respond(api,"d","u03",14);view.next.performClick();assertEquals("d",view.selectedId);assertTrue(view.details.size()<=3);assertFalse(view.details.containsKey("a"));assertEquals(state,h.state.toString());
    }
  }
  @Test public void lateOrMismatchedDetailNeverPaintsAnotherOwnedIndividual()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u01",9),summary("c","u02",12));Call old=api.last("a");
      view.step(1);view.step(1);old.reply.done(detail(unit("a","u01",2)),null);assertEquals("c",view.selectedId);assertNull(view.shown);assertNull(view.portrait.getDrawable());
      api.last("c").reply.done(detail(unit("b","u01",99)),null);assertNull(view.shown);assertFalse(view.stats.isEnabled());assertTrue(view.notice.getText().toString().contains("새로고침"));
      view.refresh();api.lastList().reply.done(list(summary("c","u02",14),summary("a","u01",2)),null);respond(api,"c","u02",14);old.reply.done(detail(unit("a","u01",60)),null);assertEquals("c",view.selectedId);assertEquals(14,view.shown.getInt("level"));
    }
  }
  @Test public void largeButtonsOpenFrozenDetailsAndBackPreservesOwnedIdAndPosition()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u01",9));respond(api,"a","u01",2);respond(api,"b","u01",9);view.step(1);fixture.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      for(View target:new View[]{view.previous,view.stats,view.elements,view.skills}){assertTrue(target.getHeight()>=h.dp(48));assertTrue(target.getWidth()>=h.dp(48));}
      view.stats.performClick();AlertDialog modal=view.modal;assertNotNull(modal);String body=((android.widget.TextView)modal.getWindow().getDecorView().findViewWithTag("owned-gulls-detail-body")).getText().toString();assertTrue(body.contains("환생 보정"));
      assertFalse(view.previous.isEnabled());view.step(-1);touch(view.portrait,MotionEvent.ACTION_DOWN,800,30);touch(view.portrait,MotionEvent.ACTION_UP,10,30);view.portrait.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null);assertEquals("b",view.selectedId);
      assertEquals(NativeOwnedGulls.SCREEN,h.screen);assertSame(view,h.ownedGulls);assertFalse(h.updateBlocks());
      h.back();assertNull("Activity Back closes the owned detail",view.modal);assertNull("Activity Back clears the shared dialog",h.visibleDialog);assertEquals("b",view.selectedId);assertEquals(1,view.index);
      view.skills.performClick();AlertDialog reopened=view.modal;Shadows.shadowOf(Looper.getMainLooper()).idle();assertSame(reopened,view.modal);assertSame(reopened,h.visibleDialog);String skillText=((android.widget.TextView)view.modal.getWindow().getDecorView().findViewWithTag("owned-gulls-detail-body")).getText().toString();assertTrue(skillText.contains("기술은 유닛 레벨에 따라 해금돼요."));assertTrue(skillText.contains("현재 유닛 Lv.9"));assertTrue(skillText.contains("유닛 Lv.1 해금 · 사용 가능"));assertTrue(skillText.contains("다음 해금 조건 · 유닛 Lv.10"));assertTrue(skillText.contains("유닛 Lv.10 해금 · 미해금"));assertFalse(skillText.contains("성장 체계 설계 필요"));
      view.modal.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull("Close button closes the owned detail",view.modal);assertNull("Close button clears the shared dialog",h.visibleDialog);assertEquals("b",view.selectedId);
      view.elements.performClick();AlertDialog systemBack=view.modal;systemBack.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK));systemBack.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK));Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(view.modal);assertNull(h.visibleDialog);assertEquals("b",view.selectedId);
      assertTrue(h.title.getText().toString().equals("내 갈매미"));assertEquals(3,api.calls.size());
    }
  }
  @Test public void aLongSwipeSnapsOnlyOnceAndVerticalCancelledOrMultitouchDoesNotMove()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u01",9),summary("c","u02",12));respond(api,"a","u01",2);respond(api,"b","u01",9);
      touch(view.portrait,MotionEvent.ACTION_DOWN,1200,40);touch(view.portrait,MotionEvent.ACTION_MOVE,600,40);touch(view.portrait,MotionEvent.ACTION_UP,-1200,40);assertEquals("b",view.selectedId);
      touch(view.portrait,MotionEvent.ACTION_DOWN,200,40);touch(view.portrait,MotionEvent.ACTION_UP,190,300);assertEquals("b",view.selectedId);
      touch(view.portrait,MotionEvent.ACTION_DOWN,200,40);touch(view.portrait,MotionEvent.ACTION_MOVE,205,300);touch(view.portrait,MotionEvent.ACTION_UP,10,40);assertEquals("b",view.selectedId);
      touch(view.portrait,MotionEvent.ACTION_DOWN,200,40);touch(view.portrait,MotionEvent.ACTION_CANCEL,200,40);touch(view.portrait,MotionEvent.ACTION_UP,1,40);assertEquals("b",view.selectedId);
      touch(view.portrait,MotionEvent.ACTION_DOWN,200,40);touch(view.portrait,MotionEvent.ACTION_POINTER_DOWN,180,40);touch(view.portrait,MotionEvent.ACTION_UP,1,40);assertEquals("b",view.selectedId);
      view.previous.performClick();assertEquals("a",view.selectedId);assertFalse(view.previous.isEnabled());
    }
  }
  @Test public void accountChangeClearsPrivateDataAndOldCallbacksCannotRepopulateIt()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeOwnedGulls first=open(h,api,summary("a","u01",2),summary("b","u01",9));Call delayed=api.last("a");respond(api,"b","u01",9);first.step(1);assertEquals("b",first.selectedId);
      api.account="owned-two.1.signature";delayed.reply.done(detail(unit("a","u01",2)),null);assertNull(first.shown);assertEquals(0,first.details.size());assertEquals(0,first.entries.size());assertEquals("",first.name.getText().toString());assertNull(first.portrait.getDrawable());
      NativeOwnedGulls second=open(h,api,summary("other","u03",15),summary("b","u01",9));assertEquals("other",second.selectedId);respond(api,"other","u03",15);delayed.reply.done(detail(unit("a","u01",60)),null);assertEquals("other",second.selectedId);assertEquals("other",second.shown.getString("ownedId"));
    }
  }
  @Test public void refreshAfterGrowthOrRebirthKeepsExactSelectionAndReplacesAllDetails()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u01",9));respond(api,"a","u01",2);Call old=api.last("b");old.reply.done(detail(unit("b","u01",9)),null);view.step(1);
      view.stats.performClick();int reads=api.calls.size();view.refresh();assertEquals(reads,api.calls.size());assertTrue(view.refreshQueued);view.closeDetails();assertEquals(reads+1,api.calls.size());assertNull(view.shown);assertEquals("",view.vitals.getText().toString());api.lastList().reply.done(list(summary("b","u01",15),summary("a","u01",2)),null);JSONObject upgraded=unit("b","u01",15);upgraded.put("reincarnation",2);api.last("b").reply.done(detail(upgraded),null);old.reply.done(detail(unit("b","u01",9)),null);
      assertEquals("b",view.selectedId);assertEquals(0,view.index);assertEquals(15,view.shown.getInt("level"));assertTrue(NativeOwnedGulls.detailText(view.shown,0).contains("환생 2회"));assertTrue(view.vitals.getText().toString().contains("HP 15"));
      view.pause();view.resume();assertNull(view.shown);api.lastList().reply.done(list(summary("b","u01",16),summary("a","u01",2)),null);respond(api,"b","u01",16);assertEquals("b",view.selectedId);assertEquals(16,view.shown.getInt("level"));
    }
  }
  @Test public void lateImageForPreviousSpeciesCannotOverwriteTheNewIndividual()throws Exception{
    CountDownLatch began=new CountDownLatch(1),release=new CountDownLatch(1);
    AtomicReference<Bitmap> oldImage=new AtomicReference<>();AtomicBoolean oldDelivered=new AtomicBoolean();
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);
      // The public-art startup gate must complete before deliberately holding one shared worker.
      LocalGameArtTest.drain();LocalGameArt.trim();String oldKey=LocalGameArt.key("roster60-art/u01-front.webp",4);
      LocalGameArt.work(oldKey,()->{began.countDown();release.await();Bitmap image=LocalGameArt.decode(h,"roster60-art/u01-front.webp",4);oldImage.set(image);return image;},unused->oldDelivered.set(true));
      assertTrue("Previous-species decode is held before opening the owned screen",began.await(2,TimeUnit.SECONDS));
      NativeOwnedGulls view=open(h,api,summary("a","u01",2),summary("b","u02",9));respond(api,"a","u01",2);
      java.util.Map<?,?> pending=org.robolectric.util.ReflectionHelpers.getStaticField(LocalGameArt.class,"WAITING");
      synchronized(pending){assertTrue("The actual previous portrait binding joined the held decode",pending.get(oldKey) instanceof List&&((List<?>)pending.get(oldKey)).size()>=2);}
      view.step(1);respond(api,"b","u02",9);
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
      while(view.portrait.getDrawable()==null&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));Thread.sleep(5);}
      assertNotNull("The current real portrait completes while the previous decode is still held",view.portrait.getDrawable());
      Bitmap current=((BitmapDrawable)view.portrait.getDrawable()).getBitmap();
      assertTrue(Arrays.asList(LocalGameArt.peek("roster60-art/u02-front.webp",1),LocalGameArt.peek("roster60-art/u02-front.webp",2),LocalGameArt.peek("roster60-art/u02-front.webp",4),LocalGameArt.peek("roster60-art/u02-front.webp",8)).contains(current));
      assertEquals("No automatic release can race the current-image assertion",1L,release.getCount());assertFalse(oldDelivered.get());assertNull(oldImage.get());
      release.countDown();LocalGameArtTest.drain();assertTrue("The held original decode was delivered after the new selection",oldDelivered.get());assertNotNull(oldImage.get());
      assertEquals("b",view.selectedId);assertEquals("b",view.shown.getString("ownedId"));assertNotNull(view.portrait.getDrawable());
      Bitmap completed=((BitmapDrawable)view.portrait.getDrawable()).getBitmap();assertNotSame(oldImage.get(),completed);
      assertTrue(Arrays.asList(LocalGameArt.peek("roster60-art/u02-front.webp",1),LocalGameArt.peek("roster60-art/u02-front.webp",2),LocalGameArt.peek("roster60-art/u02-front.webp",4),LocalGameArt.peek("roster60-art/u02-front.webp",8)).contains(completed));
    }finally{release.countDown();LocalGameArtTest.drain();LocalGameArt.trim();}
  }
  @Test public void phoneAndTabletKeepLargeGroupControlsAndCaptureActualOwnedScreen()throws Exception{
    for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("owned-phone-font130",915,412,160,1.3f,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("owned-tablet",1280,720,160,1,0,0,0,0)}){
      fixture.configure(profile);
      try(ActivityController<MainActivity> activity=fixture.flow.start()){
        MainActivity h=activity.get();Api api=prepare(h);JSONObject first=unit("a","u01",1).put("name","갈매미맨").put("stats",MainActivity.obj("hp",198,"mp",33,"durability",24,"statusResistance",12,"speed",18)),second=unit("b","u02",1).put("name","미니").put("stats",MainActivity.obj("hp",209,"mp",30,"durability",24,"statusResistance",12,"speed",24));NativeOwnedGulls view=open(h,api,profile,first,second);api.last("a").reply.done(detail(first),null);api.last("b").reply.done(detail(second),null);fixture.layout(h,profile);LocalGameArtTest.drain();
        for(View control:new View[]{view.stats,view.elements,view.skills,view.previous,view.next})NativeNoScrollReviewTest.assertFullyVisible(control,h.root);
        assertNotNull(view.position.getLayout());assertTrue(profile.name+" · swipe position hint must fit its content height",view.position.getLayout().getHeight()<=view.position.getHeight()-view.position.getPaddingTop()-view.position.getPaddingBottom());
        Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"owned-gulls");Files.createDirectories(output);Bitmap image=NativeHomePartyFeedbackTest.render(h.root);String filename=profile.width+"x"+profile.height+(profile.font>1?"-font130":"")+".png";try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(filename))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      }
    }
  }
}
