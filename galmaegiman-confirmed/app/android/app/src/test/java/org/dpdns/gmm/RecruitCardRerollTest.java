package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Card-local paid rerolls, offline authoritative fixtures, and actual native pixels. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitCardRerollTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final class Api extends NativeApi {
  String owner="offline-card-reroll";final List<String> paths=new ArrayList<>();final List<JSONObject> bodies=new ArrayList<>();Result pending;
  Api(Context c){super(c);}@Override String token(){return owner;}
  @Override void call(String path,JSONObject body,Result callback){assertNotNull(body);paths.add(path);bodies.add(NativeRealtime.copy(body));pending=callback;}
 }
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}
 static JSONArray rows(int count){JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(NativeRecruitInertialRailTest.candidate(i));return rows;}
 NativeRecruitSession open(MainActivity h,Api api,JSONArray rows,NativeHomePartyFeedbackTest.Profile profile,Integer credits)throws Exception{
  helper.prepare(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();NativeRecruitSession screen=new NativeRecruitSession(h);JSONObject info=MainActivity.obj("sessions",rows,"offers",new JSONArray());if(credits!=null)info.put("credits",credits);screen.open(info);helper.layout(h,profile);screen.rail.bindVisible();helper.settle(h,profile);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);return screen;
 }
 Button reroll(NativeRecruitSession screen,int index){Button button=screen.slots.get(index).findViewWithTag("recruit-reroll");assertNotNull("Card-local reroll "+index,button);return button;}
 @Test public void unselectedCardRerollUsesItsOwnSnapshotAndNeverClaimsOrSelects()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession screen=open(h,api,rows(3),PHONE,50);assertNull(screen.controls.findViewWithTag("recruit-reroll"));Button second=reroll(screen,1);assertTrue(second.isEnabled());second.performClick();second.performClick();assertEquals(1,api.paths.size());JSONObject body=api.bodies.get(0);assertEquals("session-1",body.getString("sessionId"));assertEquals(0,body.getInt("revision"));assertEquals("reroll",body.getString("action"));assertEquals(0,screen.selected);assertFalse(reroll(screen,0).isEnabled());
   api.pending.done(null,"network");assertEquals(0,screen.selected);((Button)h.root.findViewWithTag("recruit-retry")).performClick();assertEquals(body.toString(),api.bodies.get(1).toString());JSONObject reply=new JSONObject(screen.candidates.getJSONObject(1).toString()).put("revision",1).put("rerolls",1).put("credits",49).put("requestId",body.getString("requestId"));api.pending.done(reply,null);assertEquals(0,screen.selected);assertEquals(0,screen.candidates.getJSONObject(0).getInt("rerolls"));assertEquals(1,screen.candidates.getJSONObject(1).getInt("rerolls"));assertEquals("session-1",screen.shredSessionId);assertFalse(reroll(screen,1).isEnabled());assertTrue(reroll(screen,0).isEnabled());assertTrue(reroll(screen,1).getContentDescription().toString().contains("남은 0회"));
   for(JSONObject sent:api.bodies)assertEquals("reroll",sent.optString("action"));assertEquals(2,api.paths.size());
  }
 }
 @Test public void claimedUsedUnknownAndUnaffordableCardsCannotSendAndStaleButtonsAreRejected()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(5);list.getJSONObject(1).put("status","CLAIMED");list.getJSONObject(2).put("rerolls",1);list.getJSONObject(3).remove("rerollCost");list.getJSONObject(4).put("rerollCost",100);
   NativeRecruitSession screen=open(h,api,list,PHONE,10);screen.rail.bindVisible();for(int i=1;i<5;i++){screen.slots.get(i).bind(false);Button button=reroll(screen,i);assertFalse(button.isEnabled());button.performClick();}assertTrue(api.paths.isEmpty());assertTrue(reroll(screen,3).getText().toString().contains("?장"));assertTrue(reroll(screen,3).getContentDescription().toString().contains("지원서 확인 필요"));assertTrue(reroll(screen,4).getText().toString().contains("100장"));assertTrue(reroll(screen,4).getContentDescription().toString().contains("지원서 부족"));
   Button old=reroll(screen,0);screen.candidates.getJSONObject(0).put("revision",7);old.performClick();assertTrue("Old rendered revision cannot send",api.paths.isEmpty());screen.render(false);Button current=reroll(screen,0);api.owner="another-account";current.performClick();assertTrue(api.paths.isEmpty());api.owner="offline-card-reroll";h.epoch++;current.performClick();assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void dragBeginningOnRerollAndStoppingFlingCannotRerollSelectOrClaim()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession screen=open(h,api,rows(30),PHONE,100);Button refresh=reroll(screen,1);int[] rail=new int[2],button=new int[2];screen.rail.getLocationOnScreen(rail);refresh.getLocationOnScreen(button);float x=button[0]-rail[0]+refresh.getWidth()/2f,y=button[1]-rail[1]+refresh.getHeight()/2f;long t=SystemClock.uptimeMillis();
   NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_DOWN,t,t,x,y);NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_MOVE,t,t+16,x-70,y);NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_MOVE,t,t+32,x-170,y);NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_UP,t,t+48,x-200,y);assertTrue(screen.rail.getScrollX()>0);assertEquals(0,screen.selected);assertTrue(api.paths.isEmpty());refresh.performClick();((Button)h.root.findViewWithTag("recruit-confirm")).performClick();assertTrue(api.paths.isEmpty());
   screen.rail.stopMotion();ShadowLooper.idleMainLooper(250,TimeUnit.MILLISECONDS);screen.rail.scrollTo(0,0);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);screen.rail.fling(1600);long stop=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_DOWN,stop,stop,x,y);NativeRecruitInertialRailTest.touch(screen.rail,MotionEvent.ACTION_UP,stop,stop+20,x,y);ShadowLooper.idleMainLooper(40,TimeUnit.MILLISECONDS);assertTrue(api.paths.isEmpty());assertEquals(0,screen.selected);
  }
 }
 @Test public void staleRerollRecoversRequestedCardAndInsufficientCreditsDoesNotClaim()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession screen=open(h,api,rows(3),PHONE,null);reroll(screen,1).performClick();api.pending.done(null,"STALE_REVISION");assertEquals(2,api.paths.size());assertEquals("native/roster60/recruit/session/read",api.paths.get(1));assertEquals("session-1",api.bodies.get(1).getString("sessionId"));JSONObject latest=new JSONObject(screen.candidates.getJSONObject(1).toString()).put("revision",2);api.pending.done(latest,null);assertEquals(0,screen.selected);assertEquals(2,screen.candidates.getJSONObject(1).getInt("revision"));
   reroll(screen,1).performClick();assertEquals("session-1",api.bodies.get(2).getString("sessionId"));assertEquals(2,api.bodies.get(2).getInt("revision"));api.pending.done(null,"INSUFFICIENT_CREDITS");assertEquals("PENDING",screen.candidates.getJSONObject(1).getString("status"));assertEquals(0,screen.candidates.getJSONObject(1).getInt("rerolls"));assertNull(h.root.findViewWithTag("recruit-retry"));assertTrue(screen.message.getText().toString().contains("INSUFFICIENT_CREDITS"));for(JSONObject body:api.bodies)assertNotEquals("claim",body.optString("action"));
  }
 }
 void captureGeometry(MainActivity h,NativeRecruitSession screen,String name,Path out)throws Exception{
  JSONArray cards=new JSONArray();for(NativeRecruitSession.CandidateSlot slot:screen.slots){if(!slot.loaded)continue;ImageView image=slot.findViewWithTag("recruit-registration-card");Button button=slot.findViewWithTag("recruit-reroll");TextView status=slot.findViewWithTag("recruit-candidate-status");if(image==null||button==null||status==null)continue;cards.put(MainActivity.obj("index",slot.index,"slotWidth",slot.getWidth(),"slotHeight",slot.getHeight(),"imageHeight",image.getHeight(),"imageTopPadding",image.getPaddingTop(),"imageContentHeight",image.getHeight()-image.getPaddingTop()-image.getPaddingBottom(),"buttonWidth",button.getWidth(),"buttonHeight",button.getHeight(),"buttonTextLines",button.getLayout()==null?-1:button.getLayout().getLineCount(),"statusHeight",status.getHeight()));}
  JSONObject data=MainActivity.obj("profile",name,"rootHeight",h.root.getHeight(),"pageHeight",h.page.getHeight(),"bodyHeight",screen.root.getHeight(),"messageHeight",screen.message.getHeight(),"messageVisible",screen.message.getVisibility()==View.VISIBLE,"selectedHeight",screen.selectedCandidate.getHeight(),"controlsHeight",screen.controls.getHeight(),"railHeight",screen.rail.getHeight(),"cards",cards);Files.write(out.resolve(name+".json"),data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Bitmap png=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(png.compress(Bitmap.CompressFormat.PNG,100,file));}finally{png.recycle();}
 }
 @Test public void actualPixelsKeepCardHeaderAndStatusClearAcrossCountsSizesAndFontScales()throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"recruit-reroll");Files.createDirectories(out);
  for(int[] size:new int[][]{{640,320},{915,412},{1280,720}})for(float font:new float[]{1f,1.3f})for(int count:new int[]{1,3,30}){
   String name="recruit-card-"+size[0]+"x"+size[1]+"-font"+Math.round(font*100)+"-"+count;NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile(name,size[0],size[1],160,font,0,0,0,0);helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession screen=open(h,api,rows(count),profile,100);captureGeometry(h,screen,name,out);int visible=0;for(NativeRecruitSession.CandidateSlot slot:screen.slots){Rect cardBounds=new Rect();if(!slot.getGlobalVisibleRect(cardBounds)||!slot.loaded||cardBounds.width()<slot.getWidth())continue;ImageView image=slot.findViewWithTag("recruit-registration-card");Button button=slot.findViewWithTag("recruit-reroll");TextView status=slot.findViewWithTag("recruit-candidate-status");Rect imageBounds=new Rect(),buttonBounds=new Rect(),statusBounds=new Rect();if(!button.getGlobalVisibleRect(buttonBounds))continue;visible++;assertTrue(image.getGlobalVisibleRect(imageBounds));assertTrue(status.getGlobalVisibleRect(statusBounds));assertEquals("그림을 줄이던 위쪽 여백 제거",0,image.getPaddingTop());assertEquals("작은 새로고침도 48dp 터치 폭 유지",h.dp(48),button.getWidth());assertEquals("그림 우상단에 배치",imageBounds.top,buttonBounds.top);assertTrue("새로고침은 그림 영역 안에 배치",imageBounds.contains(buttonBounds));assertTrue("오른쪽 가장자리의 작은 여백만 사용",imageBounds.right-buttonBounds.right<=h.dp(3));assertTrue("교체의 포기 의미를 안내",button.getContentDescription().toString().contains("현재 후보를 포기"));assertFalse("Status remains outside registration",Rect.intersects(imageBounds,statusBounds));assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());assertTrue(name+" Illustration keeps visible content area (image="+image.getHeight()+", top="+image.getPaddingTop()+")",image.getHeight()-image.getPaddingTop()>=h.dp(36));assertNotNull(image.getDrawable());assertTrue(button.getHeight()>=h.dp(48));assertNotNull(button.getLayout());int room=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom();assertTrue(name+" reroll text not clipped",button.getLayout().getLineBottom(button.getLayout().getLineCount()-1)<=room);assertEquals(0,button.getLayout().getEllipsisCount(button.getLayout().getLineCount()-1));}
    assertTrue(visible>=1);if(count>1)assertTrue("Several candidates still visible",visible>=2);assertEquals(count,screen.candidates.length());assertEquals(0,screen.selected);assertTrue(api.paths.isEmpty());Bitmap png=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(png.compress(Bitmap.CompressFormat.PNG,100,file));}finally{png.recycle();}
   }
  }
 }
}
