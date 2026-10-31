package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Actual production Views, transforms and ancestor clipping. Fixture DTOs only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitFooterRegressionTest {
 final RecruitCandidateDecisionTest fixture=new RecruitCandidateDecisionTest();
 final Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"recruit-footer");
 @Before public void setup(){fixture.setup();}
 @After public void restore(){fixture.restore();}
 RectF map(View view,RectF local,View root){RectF r=new RectF(local);View at=view;while(at!=root){at.getMatrix().mapRect(r);r.offset(at.getLeft(),at.getTop());assertTrue(at.getParent() instanceof View);at=(View)at.getParent();r.offset(-at.getScrollX(),-at.getScrollY());}return r;}
 RectF bounds(View view,MainActivity h){return map(view,new RectF(0,0,view.getWidth(),view.getHeight()),h.root);}
 RectF visible(View view,MainActivity h){RectF r=bounds(view,h);View at=view;while(at!=h.root){assertTrue(at.getParent() instanceof ViewGroup);ViewGroup parent=(ViewGroup)at.getParent();if(parent.getClipChildren()){RectF clip=new RectF(0,0,parent.getWidth(),parent.getHeight());if(parent.getClipToPadding())clip.set(parent.getPaddingLeft(),parent.getPaddingTop(),parent.getWidth()-parent.getPaddingRight(),parent.getHeight()-parent.getPaddingBottom());if(!r.intersect(map(parent,clip,h.root)))return new RectF();}at=parent;}if(!r.intersect(0,0,h.root.getWidth(),h.root.getHeight()))return new RectF();return r;}
 void same(RectF a,RectF b){assertEquals(a.left,b.left,.5f);assertEquals(a.top,b.top,.5f);assertEquals(a.right,b.right,.5f);assertEquals(a.bottom,b.bottom,.5f);}
 void full(Button b,MainActivity h){RectF box=bounds(b,h);same(box,visible(b,h));assertTrue("Final transformed hit width >=48dp "+box,box.width()+.5f>=h.dp(48));assertTrue("Final transformed hit height >=48dp "+box,box.height()+.5f>=h.dp(48));assertNotNull(b.getLayout());assertTrue("Text fits vertical box",b.getLayout().getHeight()<=b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom());for(int i=0;i<b.getLayout().getLineCount();i++)assertEquals("Action text is not ellipsized",0,b.getLayout().getEllipsisCount(i));}
 void layout(MainActivity h,NativeHomePartyFeedbackTest.Profile p){fixture.helper.layout(h,p);}
 void png(MainActivity h,String name)throws Exception{Files.createDirectories(out);Bitmap b=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream file=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,file));}finally{b.recycle();}}
 @Test public void centralFooterTargetAndTransformedRejectRemainClearThroughPendingFailureAndRecovery()throws Exception{
  JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:new NativeHomePartyFeedbackTest.Profile[]{
   NativeHomePartyFeedbackTest.PROFILES[2],
   RecruitCandidateDecisionTest.PHONE,
   new NativeHomePartyFeedbackTest.Profile("short-font130",640,320,160,1.3f,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("tablet",1280,720,160,1,0,0,0,0)}){
   fixture.helper.configure(profile);
   try(ActivityController<MainActivity> c=fixture.helper.flow.start()){
    MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(3);NativeRecruitSession ui=fixture.open(h,api,rows,profile);
    Button all=h.root.findViewWithTag("recruit-confirm"),reject=fixture.reject(ui,0);RectF fixed=bounds(all,h);full(all,h);full(reject,h);full((Button)h.root.findViewWithTag("recruit-close"),h);assertFalse(RectF.intersects(bounds(reject,h),fixed));
    png(h,profile.name+"-idle");reject.performClick();reject.performClick();assertEquals(1,api.paths.size());JSONObject request=api.bodies.get(0);layout(h,profile);
    Button checking=h.root.findViewWithTag("recruit-retry");assertNotNull(checking);png(h,profile.name+"-pending");same(fixed,bounds(checking,h));full(checking,h);assertFalse(checking.isEnabled());
    full(reject,h);assertFalse("Status text must not cover the card action",RectF.intersects(bounds(reject,h),bounds(ui.message,h)));assertFalse("Status must not cover primary action",RectF.intersects(fixed,bounds(ui.message,h)));png(h,profile.name+"-pending");
    api.pending.done(null,"network");layout(h,profile);same(fixed,bounds(ui.retryControl,h));full(ui.retryControl,h);assertTrue(ui.retryControl.isEnabled());assertEquals(0,ui.rejectionEffects);assertEquals(50,ui.info.optInt("credits"));assertFalse(ui.message.getText().toString().contains("지원서 +1"));
    ui.retryControl.performClick();assertEquals(request.toString(),api.bodies.get(1).toString());api.pending.done(fixture.rejected(request,new JSONArray().put(rows.optJSONObject(1)).put(rows.optJSONObject(2)),false),null);layout(h,profile);
    same(fixed,bounds(ui.confirmControl,h));full(ui.confirmControl,h);assertEquals(51,ui.info.optInt("credits"));assertEquals(0,ui.rejectionEffects);assertEquals(2,api.paths.size());assertFalse(RectF.intersects(bounds(ui.confirmControl,h),bounds(ui.message,h)));png(h,profile.name+"-recovered");
    assertTrue("Footer keeps the designed visible gap above safe bottom",fixed.bottom<=h.root.getHeight()-profile.bottom-h.dp(4));
    ImageView illustration=ui.slots.get(ui.selected).findViewWithTag("recruit-registration-card");assertTrue("Central art keeps most of available stage height",illustration.getHeight()>=ui.stage.getHeight()*.65f);
    evidence.put(MainActivity.obj("profile",profile.json(),"fixedActionRect",fixed.toShortString(),"retrySameRect",true,"transformedRejectFullyVisible",true,"apiRequests",api.paths.size(),"receiptRefund",1,"testData",true));
   }
  }
  Files.createDirectories(out);Files.write(out.resolve("footer-geometry.json"),evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void confirmedRejectionShowsCutSeparateAndFallBeforeRevealingTheNextCandidate()throws Exception{
  try(ActivityController<MainActivity> c=fixture.helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(3);NativeRecruitSession ui=fixture.open(h,api,rows,RecruitCandidateDecisionTest.PHONE);h.motionEnabled=true;
   View original=ui.slots.get(0).frame.getChildAt(0);ImageView image=original.findViewWithTag("recruit-registration-card");assertNotNull(image.getDrawable());fixture.reject(ui,0).performClick();assertNull(ui.rejectionEffect);api.pending.done(fixture.rejected(api.bodies.get(0),new JSONArray().put(rows.optJSONObject(1)).put(rows.optJSONObject(2)),false),null);
   assertNotNull(ui.rejectionEffect);assertSame(original,ui.rejectionEffect.getChildAt(0));assertTrue("Clear cut/separate/fall duration",ui.rejectionEffect.animation.getDuration()>=1200);assertTrue(ui.rejectionEffect.animation.getDuration()<=1800);assertEquals(View.INVISIBLE,ui.rail.getVisibility());assertEquals(51,ui.info.optInt("credits"));assertEquals(1,ui.rejectionEffects);layout(h,RecruitCandidateDecisionTest.PHONE);
   assertEquals("Status shows the outcome without repeating the balance", "불합격 처리 · 지원서 +1",ui.message.getText().toString());assertEquals("Balance remains in its existing left footer", "지원서 51 · ↻ 1장",ui.candidateInfo.getText().toString());long serializations=ui.candidateSerializations;String confirmed=ui.prefs.getString(ui.key+".result","");assertTrue(confirmed.contains("session-1"));assertTrue(ui.prefs.getString(ui.key,"").isEmpty());
   for(int ms:new int[]{200,600,1100}){ui.rejectionEffect.animation.setCurrentPlayTime(ms);layout(h,RecruitCandidateDecisionTest.PHONE);png(h,"confirmed-rejection-"+ms+"ms");assertEquals("Frames do not rebuild candidate payloads",serializations,ui.candidateSerializations);assertEquals(1,api.paths.size());assertEquals(View.INVISIBLE,ui.rail.getVisibility());ui.confirmCandidates();assertEquals("Accept-all cannot race a finishing rejection effect",1,api.paths.size());}
   for(int ms=0,frame=0;ms<1450;ms+=50,frame++){ui.rejectionEffect.animation.setCurrentPlayTime(ms);layout(h,RecruitCandidateDecisionTest.PHONE);png(h,"rejection-sequence-frame"+String.format(java.util.Locale.ROOT,"%03d",frame));}
   ui.rejectionEffect.animation.end();layout(h,RecruitCandidateDecisionTest.PHONE);png(h,"rejection-sequence-frame029");assertNull(ui.rejectionEffect);assertNull(image.getDrawable());assertEquals(View.VISIBLE,ui.rail.getVisibility());assertEquals("session-1",ui.state.optString("sessionId"));assertEquals(2,ui.candidates.length());assertEquals(1,api.paths.size());png(h,"confirmed-rejection-next-candidate");
  }
 }
 @Test public void effectTouchFocusBackgroundResizeAndDetachCleanUpWithoutASecondGrant()throws Exception{
  for(String exit:new String[]{"touch","focus","background","resize","detach"})try(ActivityController<MainActivity> c=fixture.helper.flow.start()){
   fixture.helper.configure(RecruitCandidateDecisionTest.PHONE);MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(2);NativeRecruitSession ui=fixture.open(h,api,rows,RecruitCandidateDecisionTest.PHONE);h.motionEnabled=true;View source=ui.slots.get(0).frame.getChildAt(0);ImageView image=source.findViewWithTag("recruit-registration-card");fixture.reject(ui,0).performClick();api.pending.done(fixture.rejected(api.bodies.get(0),new JSONArray().put(rows.optJSONObject(1)),false),null);layout(h,RecruitCandidateDecisionTest.PHONE);assertNotNull(ui.rejectionEffect);
   if(exit.equals("touch")){long now=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(ui.rejectionEffect,MotionEvent.ACTION_DOWN,now,now,5,5);NativeRecruitInertialRailTest.touch(ui.rejectionEffect,MotionEvent.ACTION_UP,now,now+20,5,5);}
   else if(exit.equals("focus")){ui.rail.onWindowFocusChanged(false);ui.rail.onWindowFocusChanged(true);}
   else if(exit.equals("background")){ui.rail.dispatchWindowVisibilityChanged(View.GONE);ui.rail.dispatchWindowVisibilityChanged(View.VISIBLE);}
   else if(exit.equals("resize")){NativeHomePartyFeedbackTest.Profile next=new NativeHomePartyFeedbackTest.Profile("resize-tablet",1280,720,160,1,0,0,0,0);fixture.helper.configure(next);h.onConfigurationChanged(new android.content.res.Configuration(h.getResources().getConfiguration()));layout(h,next);}
   else {h.epoch++;h.root.removeAllViews();}
   ShadowLooper.idleMainLooper(50,TimeUnit.MILLISECONDS);assertNull(exit,ui.rejectionEffect);assertNull(exit,image.getDrawable());assertEquals(51,ui.info.optInt("credits"));assertEquals(1,ui.rejectionEffects);assertEquals(1,api.paths.size());assertTrue(ui.prefs.getString(ui.key,"").isEmpty());
  }
 }
 @Test public void lateResizeCompletionCannotDismissANewerConfirmedRejection()throws Exception{
  try(ActivityController<MainActivity> c=fixture.helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(3);NativeRecruitSession ui=fixture.open(h,api,rows,RecruitCandidateDecisionTest.PHONE);h.motionEnabled=true;
   fixture.reject(ui,0).performClick();api.pending.done(fixture.rejected(api.bodies.get(0),new JSONArray().put(rows.optJSONObject(1)).put(rows.optJSONObject(2)),false),null);layout(h,RecruitCandidateDecisionTest.PHONE);RecruitCardTransition first=ui.rejectionEffect;assertNotNull(first);
   first.layout(0,0,first.getWidth()+10,first.getHeight()); // queues the old resize completion
   ui.finishRejectionEffect();ui.chooseCandidate(1,false);ui.rail.stopMotion();
   fixture.reject(ui,1).performClick();assertEquals(2,api.paths.size());api.pending.done(fixture.rejected(api.bodies.get(1),new JSONArray().put(rows.optJSONObject(1)),false).put("credits",52),null);RecruitCardTransition second=ui.rejectionEffect;assertNotNull(second);assertNotSame(first,second);
   ShadowLooper.idleMainLooper(1,TimeUnit.MILLISECONDS);assertSame("Old resize completion keeps its original effect identity",second,ui.rejectionEffect);assertEquals(52,ui.info.optInt("credits"));assertEquals(2,ui.rejectionEffects);ui.finishRejectionEffect();assertNull(ui.rejectionEffect);assertEquals(2,api.paths.size());
  }
 }
 @Test public void existingCloseHitTargetStaysAboveTheRejectionEffect()throws Exception{
  try(ActivityController<MainActivity> c=fixture.helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(2);NativeRecruitSession ui=fixture.open(h,api,rows,RecruitCandidateDecisionTest.PHONE);h.motionEnabled=true;fixture.reject(ui,0).performClick();api.pending.done(fixture.rejected(api.bodies.get(0),new JSONArray().put(rows.optJSONObject(1)),false),null);layout(h,RecruitCandidateDecisionTest.PHONE);RecruitCardTransition effect=ui.rejectionEffect;assertNotNull(effect);
   Button close=h.root.findViewWithTag("recruit-close");assertSame(close,ui.stage.getChildAt(ui.stage.getChildCount()-1));full(close,h);int[] closed={0};close.setOnClickListener(v->closed[0]++);RectF hit=bounds(close,h);long now=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(h.root,MotionEvent.ACTION_DOWN,now,now,hit.centerX(),hit.centerY());NativeRecruitInertialRailTest.touch(h.root,MotionEvent.ACTION_UP,now,now+20,hit.centerX(),hit.centerY());ShadowLooper.idleMainLooper(40,TimeUnit.MILLISECONDS);assertEquals("The visible X receives its own touch, rather than skipping animation",1,closed[0]);assertSame(effect,ui.rejectionEffect);ui.finishRejectionEffect();assertEquals(1,api.paths.size());
  }
 }
}
