package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Server-authoritative decisions, actual inertial gestures, and original runtime art. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitCandidateDecisionTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final class Api extends NativeApi {
  String owner="recruit-decisions";final List<String> paths=new ArrayList<>();final List<JSONObject> bodies=new ArrayList<>();Result pending;
  Api(Context context){super(context);}@Override String token(){return owner;}
  @Override void call(String path,JSONObject body,Result callback){assertNotNull("No follow-up GET after success",body);paths.add(path);bodies.add(NativeRealtime.copy(body));pending=callback;}
 }
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}
 static JSONArray rows(int count)throws Exception{JSONArray result=new JSONArray();for(int i=0;i<count;i++)result.put(NativeRecruitInertialRailTest.candidate(i).put("ownedSpecies",i%2==0).put("canReject",i%2==0).put("rejectRefund",i%2==0?1:0));return result;}
 NativeRecruitSession open(MainActivity h,Api api,JSONArray rows,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  helper.prepare(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();NativeRecruitSession ui=new NativeRecruitSession(h);ui.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","fixture","offers",new JSONArray(),"pendingSessions",rows));helper.layout(h,profile);if(ui.overviewMode)ui.hideOverview();helper.layout(h,profile);ui.rail.bindVisible();helper.layout(h,profile);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);return ui;
 }
 JSONObject rejected(JSONObject body,JSONArray pending,boolean replay){return MainActivity.obj("requestId",body.optString("requestId"),"sessionId",body.optString("sessionId"),"revision",body.optInt("revision")+1,"status","REJECTED","credits",51,"pendingSessions",pending,"receipt",MainActivity.obj("requestId",body.optString("requestId"),"action","reject","refundedTickets",1,"grantedOwnedIds",new JSONArray(),"sessionIds",new JSONArray().put(body.optString("sessionId")),"replayed",replay));}
 Button reject(NativeRecruitSession ui,int at){ui.slots.get(at).bind(false);Button b=ui.slots.get(at).findViewWithTag("recruit-reject");assertNotNull(b);return b;}
 void frames(NativeRecruitSession ui,MainActivity h,int count){for(int i=0;i<count;i++){ShadowLooper.idleMainLooper(16,TimeUnit.MILLISECONDS);ui.rail.computeScroll();if(h.root.isLayoutRequested())helper.layout(h,PHONE);}}
 void tap(NativeRecruitSession ui,int at){View card=ui.slots.get(at).frame;Rect visible=new Rect();assertTrue(card.getGlobalVisibleRect(visible));int[] b=new int[2];ui.rail.getLocationOnScreen(b);long now=SystemClock.uptimeMillis();float x=visible.exactCenterX()-b[0],y=visible.exactCenterY()-b[1];NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_DOWN,now,now,x,y);NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_UP,now,now+24,x,y);ShadowLooper.idleMainLooper(40,TimeUnit.MILLISECONDS);}
 @Test public void centerEnlargesFirstLastAndSettledScrollSnapsWithoutSubmitting()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(30),PHONE);assertTrue(ui.rail instanceof CraftResultCarousel);assertEquals(ui.rail.getWidth()/2f,ui.slots.get(0).getLeft()+ui.rail.getPaddingLeft()+ui.slots.get(0).getWidth()/2f,1);assertEquals(1,ui.slots.get(0).getScaleX(),.01);assertTrue(ui.slots.get(1).getScaleX()<1);int loaded=0;for(NativeRecruitSession.CandidateSlot slot:ui.slots)if(slot.loaded)loaded++;assertTrue("Only bounded neighbouring art",loaded<10);assertTrue(loaded>=2);
   ui.rail.scrollTo(Math.round(ui.rail.cardWidth*2.38f),0);frames(ui,h,100);assertEquals(2*ui.rail.cardWidth,ui.rail.getScrollX(),1);assertEquals(2,ui.selected);assertTrue(api.paths.isEmpty());
   ui.page(50);frames(ui,h,100);assertEquals(29,ui.selected);assertEquals(29*ui.rail.cardWidth,ui.rail.getScrollX(),1);assertEquals(1,ui.slots.get(29).getScaleX(),.01);assertTrue(ui.slots.get(29).loaded);assertFalse(ui.slots.get(0).loaded);assertTrue(api.paths.isEmpty());int oldCardWidth=ui.rail.cardWidth,nextWidth=ui.rail.getWidth()+100,height=ui.rail.getHeight();ui.rail.measure(View.MeasureSpec.makeMeasureSpec(nextWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));ui.rail.layout(0,0,nextWidth,height);assertTrue("Wider viewport leaves more room between clipped previews",ui.rail.cardWidth>oldCardWidth);View last=ui.slots.get(29);assertEquals(nextWidth/2f,last.getLeft()+ui.rail.getPaddingLeft()-ui.rail.getScrollX()+last.getWidth()/2f,1);assertEquals(29,ui.selected);
  }
 }
 @Test public void fastFlingTouchStopsOnlyAndNextDeliberateTapCanChoose()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(12),PHONE);ui.rail.fling(1800);tap(ui,1);assertEquals(0,ui.selected);assertTrue(api.paths.isEmpty());tap(ui,1);assertEquals(1,ui.selected);assertTrue(api.paths.isEmpty());frames(ui,h,100);assertEquals(ui.rail.cardWidth,ui.rail.getScrollX(),1);
  }
 }
 @Test public void onlyAuthoritativeOwnedPendingCandidateCanRejectAndDoubleTapSendsOnce()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(3);NativeRecruitSession ui=open(h,api,list,PHONE);assertNull(ui.slots.get(1).findViewWithTag("recruit-reject"));Button old=reject(ui,0);assertEquals("불합격 · 지원서 1장 환급",old.getText().toString());old.performClick();old.performClick();assertEquals(1,api.paths.size());assertEquals("reject",api.bodies.get(0).optString("action"));assertEquals("session-0",api.bodies.get(0).optString("sessionId"));
   api.pending.done(rejected(api.bodies.get(0),new JSONArray().put(list.optJSONObject(1)).put(list.optJSONObject(2)),false),null);assertEquals(1,ui.rejectionEffects);assertEquals(2,ui.candidates.length());assertEquals("session-1",ui.state.optString("sessionId"));assertEquals(51,ui.info.optInt("credits"));assertEquals(51,h.me().optInt("credits"));((Button)h.root.findViewWithTag("recruit-info")).performClick();assertTrue(h.visibleDialog.isShowing());assertTrue(NativeNoScrollReviewTest.descendants(h.visibleDialog.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("지원서 51장")));h.visibleDialog.dismiss();assertEquals(1,api.paths.size());old.performClick();assertEquals("Detached/stale rejection cannot resubmit",1,api.paths.size());assertTrue(ui.message.getText().toString().contains("지원서 +1"));assertNull(ui.rejectionEffect);
  }
 }
 @Test public void changedCandidateMetadataAndAccountEpochCannotReject()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(2),PHONE);Button stale=reject(ui,0);ui.candidates.optJSONObject(0).put("revision",5);stale.performClick();assertTrue(api.paths.isEmpty());ui.render(false);Button fresh=reject(ui,0);api.owner="other";fresh.performClick();assertTrue(api.paths.isEmpty());api.owner="recruit-decisions";h.epoch++;fresh.performClick();assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void uncertainRetryReusesIdentityAndNeverAnimatesOrShowsAnUnconfirmedRefund()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(2);NativeRecruitSession ui=open(h,api,list,PHONE);reject(ui,0).performClick();JSONObject sent=api.bodies.get(0);api.pending.done(null,"network");assertEquals(0,ui.rejectionEffects);assertEquals(50,ui.info.optInt("credits"));assertFalse(ui.message.getText().toString().contains("지원서 +1"));((Button)h.root.findViewWithTag("recruit-retry")).performClick();assertEquals(sent.toString(),api.bodies.get(1).toString());api.pending.done(rejected(sent,new JSONArray().put(list.optJSONObject(1)),false),null);assertEquals(0,ui.rejectionEffects);assertEquals(1,ui.candidates.length());assertEquals(51,ui.info.optInt("credits"));assertEquals(2,api.paths.size());
  }
 }
 @Test public void replayedReceiptAndLateDifferentScreenCannotRunRejectionEffect()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(2);NativeRecruitSession ui=open(h,api,list,PHONE);reject(ui,0).performClick();api.pending.done(rejected(api.bodies.get(0),new JSONArray().put(list.optJSONObject(1)),true),null);assertEquals(0,ui.rejectionEffects);
   ui=open(h,api,list,PHONE);reject(ui,0).performClick();JSONObject body=api.bodies.get(api.bodies.size()-1);h.epoch++;api.pending.done(rejected(body,new JSONArray().put(list.optJSONObject(1)),false),null);assertEquals(0,ui.rejectionEffects);assertEquals(2,ui.candidates.length());assertTrue(ui.prefs.getString(ui.key+".result","").contains("session-1"));
  }
 }
 @Test public void allAcceptAlwaysUsesOneBulkRequestWithOnlyRemainingRevisions()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(1),PHONE);Button all=h.root.findViewWithTag("recruit-confirm");assertEquals("전부 합격",all.getText().toString());assertEquals(1,ui.controls.getChildCount());all.performClick();all.performClick();assertEquals(1,api.paths.size());assertTrue(api.paths.get(0).endsWith("claim-all"));assertFalse(api.bodies.get(0).has("action"));assertEquals(1,api.bodies.get(0).getJSONArray("sessions").length());assertEquals(0,api.bodies.get(0).getJSONArray("sessions").getJSONObject(0).getInt("revision"));
   api.pending.done(MainActivity.obj("requestId",api.bodies.get(0).optString("requestId"),"credits",50,"pendingSessions",new JSONArray(),"sessions",new JSONArray().put(rows(1).getJSONObject(0).put("status","CLAIMED"))),null);assertFalse(ui.hasPending());assertEquals(0,ui.candidates.length());assertEquals(1,api.paths.size());assertNull(h.root.findViewWithTag("recruit-confirm"));
  }
 }
 @Test public void authoritativeEmptyReadDoesNotResurrectCachedCandidates()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession previous=open(h,api,rows(2),PHONE);previous.prefs.edit().putString(previous.key+".result",MainActivity.obj("sessions",rows(2)).toString()).commit();NativeRecruitSession fresh=new NativeRecruitSession(h);fresh.open(MainActivity.obj("enabled",true,"credits",50,"pendingSessions",new JSONArray(),"offers",new JSONArray()));helper.layout(h,PHONE);assertEquals(0,fresh.candidates.length());assertNull(fresh.state);assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void confirmedShredReusesExistingViewAndFinishesOnTouchWithoutBitmapCapture()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(3);NativeRecruitSession ui=open(h,api,list,PHONE);h.motionEnabled=true;View original=ui.slots.get(0).frame.getChildAt(0);ImageView originalImage=original.findViewWithTag("recruit-registration-card");assertNotNull(originalImage.getDrawable());reject(ui,0).performClick();api.pending.done(rejected(api.bodies.get(0),new JSONArray().put(list.optJSONObject(1)).put(list.optJSONObject(2)),false),null);assertNotNull(ui.rejectionEffect);assertSame(original,ui.rejectionEffect.getChildAt(0));assertNotNull(ui.rejectionEffect.animation);assertEquals(1,ui.rejectionEffects);long now=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(ui.rejectionEffect,MotionEvent.ACTION_DOWN,now,now,5,5);NativeRecruitInertialRailTest.touch(ui.rejectionEffect,MotionEvent.ACTION_UP,now,now+20,5,5);assertNull(ui.rejectionEffect);assertNull(originalImage.getDrawable());ShadowLooper.idleMainLooper(250,TimeUnit.MILLISECONDS);assertNull(originalImage.getDrawable());assertEquals(1,api.paths.size());
  }
 }
 @Test public void sideCardOnlyCentersAndActionsUseUnscaledFortyEightDpTargets()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(4),PHONE);Button sideRefresh=ui.slots.get(1).findViewWithTag("recruit-reroll"),sideReject=reject(ui,2);assertFalse(sideRefresh.isEnabled());assertFalse(sideReject.isEnabled());sideRefresh.performClick();sideReject.performClick();assertTrue(api.paths.isEmpty());tap(ui,1);assertEquals(1,ui.selected);frames(ui,h,100);assertTrue(api.paths.isEmpty());assertEquals(1,ui.slots.get(1).getScaleX(),.01);Button centerRefresh=ui.slots.get(1).findViewWithTag("recruit-reroll");assertTrue(centerRefresh.isEnabled());Rect target=new Rect();assertTrue(centerRefresh.getGlobalVisibleRect(target));assertTrue(target.width()>=h.dp(48));assertTrue(target.height()>=h.dp(48));tap(ui,2);frames(ui,h,100);Button centerReject=reject(ui,2);assertTrue(centerReject.isEnabled());assertEquals(1,ui.slots.get(2).getScaleX(),.01);assertTrue(centerReject.getGlobalVisibleRect(target));assertTrue(target.height()>=h.dp(48));assertTrue(target.width()>=h.dp(48));assertFalse(((Button)ui.slots.get(1).findViewWithTag("recruit-reroll")).isEnabled());assertTrue(api.paths.isEmpty());
  }
 }
 @Test public void malformedRefundReceiptKeepsSameRequestAndDoesNotClaimOrShowRefund()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(2);NativeRecruitSession ui=open(h,api,list,PHONE);reject(ui,0).performClick();JSONObject body=api.bodies.get(0),bad=rejected(body,new JSONArray().put(list.optJSONObject(1)),false);bad.optJSONObject("receipt").put("refundedTickets",0);api.pending.done(bad,null);assertEquals(0,ui.rejectionEffects);assertEquals(2,ui.candidates.length());assertEquals(50,ui.info.optInt("credits"));assertTrue(ui.message.getText().toString().contains("INVALID_RECRUIT_RECEIPT"));assertFalse(ui.prefs.getString(ui.key,"").isEmpty());assertFalse(ui.message.getText().toString().contains("지원서 +1"));((Button)h.root.findViewWithTag("recruit-retry")).performClick();assertEquals(body.toString(),api.bodies.get(1).toString());api.pending.done(rejected(body,new JSONArray().put(list.optJSONObject(1)),true),null);assertEquals(0,ui.rejectionEffects);assertEquals(1,ui.candidates.length());
  }
 }
 @Test public void freeAndLegacyCandidatesExplainRefundBlockButRemainAcceptable()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);JSONArray list=rows(3);list.optJSONObject(0).put("canReject",false).put("rejectRefund",0).put("rejectReason","PAYMENT_PROOF_UNAVAILABLE");list.optJSONObject(2).put("canReject",false).put("rejectRefund",0).put("rejectReason","FREE_GENERATION");NativeRecruitSession ui=open(h,api,list,PHONE);for(int at:new int[]{0,2}){ui.slots.get(at).bind(false);assertNull(ui.slots.get(at).findViewWithTag("recruit-reject"));assertNotNull(ui.slots.get(at).findViewWithTag("recruit-reject-reason"));}assertTrue(((TextView)ui.slots.get(0).findViewWithTag("recruit-reject-reason")).getText().toString().contains("환급 기록"));assertTrue(((TextView)ui.slots.get(2).findViewWithTag("recruit-reject-reason")).getText().toString().contains("지원서 미사용"));((Button)h.root.findViewWithTag("recruit-confirm")).performClick();assertEquals(3,api.bodies.get(0).getJSONArray("sessions").length());assertTrue(api.paths.get(0).endsWith("claim-all"));
  }
 }
 @Test public void draggingFromRejectThenCancelCannotRejectOrAcceptAndDoesNoPerFrameSerialization()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(30),PHONE);Button reject=reject(ui,0);int[] a=new int[2],b=new int[2];reject.getLocationOnScreen(a);ui.rail.getLocationOnScreen(b);float x=a[0]-b[0]+reject.getWidth()/2f,y=a[1]-b[1]+reject.getHeight()/2f;long now=SystemClock.uptimeMillis(),serializations=ui.candidateSerializations;NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_DOWN,now,now,x,y);for(int n=1;n<=3;n++)NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_MOVE,now,now+n*16,x-n*25,y);assertTrue(ui.rail.getScrollX()>0);assertEquals(serializations,ui.candidateSerializations);assertTrue(api.paths.isEmpty());NativeRecruitInertialRailTest.touch(ui.rail,MotionEvent.ACTION_CANCEL,now,now+64,x-75,y);reject.performClick();((Button)h.root.findViewWithTag("recruit-confirm")).performClick();assertTrue(api.paths.isEmpty());assertTrue(ui.rail.blocksClick());
  }
 }
 void assertBottomRefreshLabel(MainActivity h,Button refresh){
  assertTrue("Reroll has a readable label",refresh.getText().toString().contains("지원서"));
  assertTrue("Reroll keeps a 48dp target",refresh.getWidth()>=h.dp(48)&&refresh.getHeight()>=h.dp(48));
  ViewGroup actions=(ViewGroup)refresh.getParent(),body=(ViewGroup)actions.getParent();ImageView image=body.findViewWithTag("recruit-registration-card");
  Rect picture=new Rect(),button=new Rect();assertTrue(image.getGlobalVisibleRect(picture));assertTrue(refresh.getGlobalVisibleRect(button));assertTrue("Reroll is below the full-body picture: button="+button.toShortString()+" picture="+picture.toShortString()+" parentScale="+body.getScaleX(),button.top>=picture.bottom);NativeNoScrollReviewTest.assertFullyVisible(refresh,h.root);
 }
 @Test public void phoneShortTabletAndLargerFontKeepCostsAndSingleBottomActionReadable()throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"recruit-decisions");Files.createDirectories(out);
  for(int[] size:new int[][]{{640,320},{915,412},{1280,720}})for(float font:new float[]{1f,1.3f}){
   String name=size[0]+"x"+size[1]+"-font"+Math.round(font*100);NativeHomePartyFeedbackTest.Profile profile=new NativeHomePartyFeedbackTest.Profile(name,size[0],size[1],160,font,0,0,0,0);helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();Api api=new Api(h);NativeRecruitSession ui=open(h,api,rows(3),profile);Button refresh=ui.slots.get(0).findViewWithTag("recruit-reroll"),reject=reject(ui,0),all=h.root.findViewWithTag("recruit-confirm");assertEquals("리롤 · 지원서 1장",refresh.getText().toString());assertTrue(refresh.getWidth()>=h.dp(48));assertTrue(refresh.getHeight()>=h.dp(48));assertBottomRefreshLabel(h,refresh);assertTrue(ui.slots.get(0).getScaleX()>ui.slots.get(1).getScaleX());assertEquals("전부 합격",all.getText().toString());assertEquals(1,ui.controls.getChildCount());assertTrue(all.getHeight()>=h.dp(48));assertEquals(ui.controls.getWidth()/2f,(all.getLeft()+all.getRight())/2f,1);assertTrue(ui.selectedCandidate.getText().toString().contains("지원서 50장"));assertNull("No duplicate state or level row takes illustration space",ui.slots.get(0).findViewWithTag("recruit-candidate-caption"));assertTrue(ui.selectedCandidate.getText().toString().contains("새로고침 · 지원서 1장"));((Button)h.root.findViewWithTag("recruit-info")).performClick();assertTrue(h.visibleDialog.isShowing());assertTrue(NativeNoScrollReviewTest.descendants(h.visibleDialog.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("지원서 50장")&&((TextView)v).getText().toString().contains("새로고침 · 지원서 1장")));h.visibleDialog.dismiss();for(Button button:new Button[]{reject,all,refresh}){assertNotNull(button.getLayout());int room=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom();assertTrue(name+" "+button.getText()+" text clipped",button.getLayout().getLineBottom(button.getLayout().getLineCount()-1)<=room);for(int line=0;line<button.getLayout().getLineCount();line++)assertEquals(0,button.getLayout().getEllipsisCount(line));}ImageView image=ui.slots.get(0).findViewWithTag("recruit-registration-card");assertNotNull(image.getDrawable());assertTrue(image.getHeight()>=h.dp(48));Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,file));}finally{bitmap.recycle();}assertTrue(api.paths.isEmpty());
   }
  }
 }
}
