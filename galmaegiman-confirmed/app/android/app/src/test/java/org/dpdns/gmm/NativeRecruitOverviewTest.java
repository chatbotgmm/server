package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Offline view-only round navigation and real-source native captures; not APK/device evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitOverviewTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,
   new NativeHomePartyFeedbackTest.Profile("overview-915x412-font130",915,412,160,1.3f,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("overview-1280x720",1280,720,160,1,0,0,0,0)};
 NativeHomePartyFeedbackTest.Profile profile=PHONE;
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}

 NativeRecruitSession open(MainActivity h,RecruitCandidateDecisionTest.Api api,JSONArray rows)throws Exception{
  helper.prepare(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
  NativeRecruitSession ui=new NativeRecruitSession(h);ui.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","offline-overview","offers",new JSONArray(),"pendingSessions",rows));layout(h);if(rows.length()>0){assertTrue("Candidate rounds open in the result grid",ui.overviewMode);assertEquals(View.GONE,ui.overviewControl.getVisibility());}return ui;
 }
 void layout(MainActivity h)throws Exception{helper.layout(h,profile);ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();helper.layout(h,profile);}
 void overview(NativeRecruitSession ui)throws Exception{if(!ui.overviewMode)ui.overviewControl.performClick();layout(ui.h);assertTrue(ui.overviewMode);assertNotNull(ui.overview);ui.overview.bindVisible();LocalGameArtTest.drain();}
 static int loaded(RecruitOverviewView view){int count=0;for(RecruitOverviewView.Tile tile:view.tiles)if(tile.loaded)count++;return count;}
 static void tap(RecruitOverviewView view,View target){
  Rect visible=new Rect();assertTrue(target.getGlobalVisibleRect(visible));int[] position=new int[2];view.getLocationOnScreen(position);
  float x=visible.exactCenterX()-position[0],y=visible.exactCenterY()-position[1];long now=SystemClock.uptimeMillis();
  NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,now,now,x,y);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_UP,now,now+24,x,y);
 }
 static void shot(MainActivity h,Path out,String name)throws Exception{
  Files.createDirectories(out);Bitmap image=NativeHomePartyFeedbackTest.render(h.root);
  try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
 }

 @Test public void toggleAndSummaryTapOnlyNavigateTheSameLocalRound()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(6));
   JSONArray same=ui.candidates;String rows=same.toString(),saved=ui.prefs.getString(ui.key+".result","");int stageHeight=ui.stage.getHeight(),footerHeight=((View)h.root.findViewWithTag("recruit-fixed-footer")).getHeight();
   Button all=ui.confirmControl;assertEquals("전부 합격",all.getText().toString());assertEquals(1,ui.controls.getChildCount());
   overview(ui);assertSame(same,ui.candidates);assertSame(same,ui.overview.rows);assertEquals("결과 목록",ui.overviewControl.getText().toString());assertEquals(View.GONE,ui.overviewControl.getVisibility());
   assertEquals(stageHeight,ui.stage.getHeight());assertEquals(footerHeight,((View)h.root.findViewWithTag("recruit-fixed-footer")).getHeight());assertSame(all,ui.confirmControl);assertEquals(0,ui.overview.tiles.stream().filter(t->t.findViewWithTag("recruit-reroll")!=null||t.findViewWithTag("recruit-reject")!=null).count());
   for(NativeRecruitSession.CandidateSlot slot:ui.slots)assertFalse("Large certificates release while the grid is visible",slot.loaded);
   RecruitOverviewView grid=ui.overview;tap(grid,grid.tiles.get(2));layout(h);assertFalse(ui.overviewMode);assertEquals(2,ui.selected);assertEquals("session-2",ui.state.optString("sessionId"));assertEquals(2*ui.rail.cardWidth,ui.rail.getScrollX());assertEquals(1,ui.slots.get(2).getScaleX(),.01);
   assertEquals(rows,ui.candidates.toString());assertEquals(saved,ui.prefs.getString(ui.key+".result",""));assertEquals("",ui.prefs.getString(ui.key,""));assertEquals(50,ui.info.optInt("credits"));assertTrue(api.paths.isEmpty());assertEquals(0,loaded(grid));
  }
 }

 @Test public void fiftyCandidatesBindSampledOriginalRegistrationsAndReleaseOffscreenArt()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(50));overview(ui);RecruitOverviewView view=ui.overview;
   assertEquals(50,view.tiles.size());assertTrue(loaded(view)>0);assertTrue("Never bind all fifty registrations",loaded(view)<50);
   for(RecruitOverviewView.Tile tile:view.tiles){
    int tileTop=view.grid.getTop()+tile.getTop(),viewportTop=view.getScrollY()+view.getPaddingTop(),viewportBottom=view.getScrollY()+view.getHeight()-view.getPaddingBottom();boolean visible=tileTop+tile.getHeight()>viewportTop&&tileTop<viewportBottom;assertEquals("Only viewport tiles own registration bindings",visible,tile.loaded);
    if(tile.loaded){
     assertTrue(tile.image.getDrawable() instanceof BitmapDrawable);Bitmap bitmap=((BitmapDrawable)tile.image.getDrawable()).getBitmap();
     String file=RegistrationV10Art.file(h,tile.speciesId);assertNotNull("An existing completed registration is required",file);String path=RegistrationV10Art.ROOT+file;
     BitmapFactory.Options source=new BitmapFactory.Options();source.inJustDecodeBounds=true;
     try(java.io.InputStream input=h.getAssets().open(path)){BitmapFactory.decodeStream(input,null,source);}
     assertEquals("Completed registration runtime asset width",720,source.outWidth);assertEquals("Completed registration runtime asset height",960,source.outHeight);
     int displayedSample=0;for(int sample:new int[]{1,2,4,8})if(bitmap==LocalGameArt.peek(path,sample))displayedSample=sample;
     assertTrue("Displayed bitmap must be the exact completed registration cache entry",displayedSample>0);
     assertEquals(source.outWidth/displayedSample,bitmap.getWidth());assertEquals(source.outHeight/displayedSample,bitmap.getHeight());assertEquals("Completed registration keeps its 3:4 ratio",3*bitmap.getHeight(),4*bitmap.getWidth());assertEquals(ImageView.ScaleType.FIT_CENTER,tile.image.getScaleType());
     int width=tile.image.getWidth()-tile.image.getPaddingLeft()-tile.image.getPaddingRight(),height=tile.image.getHeight()-tile.image.getPaddingTop()-tile.image.getPaddingBottom();float scale=Math.min(width/(float)source.outWidth,height/(float)source.outHeight);
     assertTrue("Sampled registration supplies the pixels used on screen",bitmap.getWidth()+1>=source.outWidth*scale&&bitmap.getHeight()+1>=source.outHeight*scale);
     assertTrue("Small grid decodes at most the 180×240 registration preview",bitmap.getWidth()<=180&&bitmap.getHeight()<=240);
     assertTrue("Readable thumbnail height",tile.image.getHeight()>=h.dp(72));assertTrue("Visible-size decode remains bounded",tile.image.getHeight()<=h.dp(240));
    }
   }
   view.scrollTo(0,view.grid.getHeight());view.bindVisible();LocalGameArtTest.drain();assertFalse(view.tiles.get(0).loaded);assertNull(view.tiles.get(0).image.getDrawable());assertTrue(view.tiles.get(49).loaded);assertTrue(loaded(view)<50);
   tap(view,view.tiles.get(49));layout(h);assertFalse(ui.overviewMode);assertEquals(49,ui.selected);assertEquals(49*ui.rail.cardWidth,ui.rail.getScrollX());assertEquals(ui.rail.cardWidth,ui.slots.get(49).getWidth());assertEquals(ui.rail.getWidth()/2f,ui.slots.get(49).getLeft()+ui.rail.getPaddingLeft()-ui.rail.getScrollX()+ui.slots.get(49).getWidth()/2f,1);assertEquals(1f,ui.slots.get(49).getScaleX(),.01f);assertTrue(ui.slots.get(49).loaded);assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void emptyAndProcessedCandidatesHaveNoImplicitDecisions()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,new JSONArray());assertFalse(ui.overviewControl.isEnabled());ui.overviewControl.performClick();assertFalse(ui.overviewMode);assertNotNull(h.root.findViewWithTag("recruit-unresolved-state"));assertTrue(api.paths.isEmpty());
   JSONArray rows=RecruitCandidateDecisionTest.rows(3);rows.getJSONObject(0).put("status","CLAIMED");rows.getJSONObject(1).put("status","REJECTED");rows.getJSONObject(2).getJSONObject("candidate").remove("rarity");
   ui.open(MainActivity.obj("credits",50,"pendingSessions",rows));layout(h);overview(ui);assertTrue(ui.overview.tiles.get(0).getContentDescription().toString().contains("합류 완료"));assertTrue(ui.overview.tiles.get(1).getContentDescription().toString().contains("불합격 완료"));assertTrue(ui.overview.tiles.get(2).getContentDescription().toString().contains("등급 확인 필요"));assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void repeatedToggleAndSystemBackRestoreSelectionWithoutExtraFooterRows()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(12));int height=ui.stage.getHeight();
   for(int i=0;i<8;i++){overview(ui);RecruitOverviewView same=ui.overview;tap(same,same.tiles.get(0));layout(h);assertFalse(ui.overviewMode);assertEquals(0,loaded(same));assertTrue(ui.overviewBack.isEnabled());h.getOnBackPressedDispatcher().onBackPressed();layout(h);assertTrue(ui.overviewMode);assertSame(same,ui.overview);assertFalse(ui.overviewBack.isEnabled());assertEquals(0,ui.selected);assertEquals(height,ui.stage.getHeight());assertEquals(h.dp(48),ui.controls.getHeight());assertEquals(1,ui.controls.getChildCount());}
   assertTrue(api.paths.isEmpty());assertEquals("",ui.prefs.getString(ui.key,""));
  }
 }

 @Test public void actualScrollAndFirstFlingStopDoNotSelectTheUnderlyingCandidate()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(50));overview(ui);RecruitOverviewView view=ui.overview;long down=SystemClock.uptimeMillis();float x=view.getWidth()*.5f,y=view.getHeight()*.72f;
   NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,down,down,x,y);
   for(int i=1;i<=5;i++){ShadowLooper.idleMainLooper(16,TimeUnit.MILLISECONDS);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_MOVE,down,SystemClock.uptimeMillis(),x,y-i*30);}
   NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_UP,down,SystemClock.uptimeMillis()+16,x,y-150);assertTrue(view.getScrollY()>0);assertTrue(ui.overviewMode);assertEquals(0,ui.selected);assertTrue(api.paths.isEmpty());
   view.fling(1800);RecruitOverviewView.Tile visible=view.tiles.stream().filter(t->{Rect box=new Rect();return t.image.getGlobalVisibleRect(box)&&box.height()==t.image.getHeight()&&box.width()==t.image.getWidth();}).findFirst().orElseThrow();NativeNoScrollReviewTest.assertFullyVisible(visible.image,ui.h.root);tap(view,visible.image);assertTrue("A fling-stop touch only stops",ui.overviewMode);tap(view,visible.image);layout(h);assertFalse("The next separate tap selects",ui.overviewMode);assertEquals(visible.index,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void authoritativeReceiptUpdatesCurrentRowsAndAnEmptyRoundExitsOverview()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray original=RecruitCandidateDecisionTest.rows(6);NativeRecruitSession ui=open(h,api,original);overview(ui);RecruitOverviewView.Tile stale=ui.overview.tiles.get(5);
   JSONObject latest=NativeRealtime.copy(original.getJSONObject(2));latest.put("revision",4);latest.getJSONObject("candidate").put("speciesId","u04").put("name","꽃");JSONArray rows=new JSONArray().put(latest);ui.acceptResult(MainActivity.obj("credits",51,"pendingSessions",rows));ui.render(false);layout(h);assertTrue(ui.overviewMode);assertEquals(1,ui.overview.tiles.size());assertTrue(ui.overview.tiles.get(0).getContentDescription().toString().contains("꽃"));stale.performClick();assertTrue(ui.overviewMode);assertEquals(1,ui.candidates.length());
   tap(ui.overview,ui.overview.tiles.get(0));layout(h);assertEquals("session-2",ui.state.optString("sessionId"));assertEquals(4,ui.state.optInt("revision"));assertEquals("u04",ui.state.getJSONObject("candidate").optString("speciesId"));overview(ui);
   ui.acceptResult(MainActivity.obj("credits",51,"pendingSessions",new JSONArray()));ui.render(false);layout(h);assertFalse(ui.overviewMode);assertFalse(ui.overviewControl.isEnabled());assertNotNull(h.root.findViewWithTag("recruit-unresolved-state"));assertTrue(api.paths.isEmpty());assertEquals("",ui.prefs.getString(ui.key,""));
  }
 }

 @Test public void receiptDuringTouchCannotSelectTheReplacedTileAndStaleScreensCannotRebind()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(3));overview(ui);RecruitOverviewView view=ui.overview;RecruitOverviewView.Tile tile=view.tiles.get(1);Rect rect=new Rect();assertTrue(tile.getGlobalVisibleRect(rect));long down=SystemClock.uptimeMillis();int[] origin=new int[2];view.getLocationOnScreen(origin);float x=rect.exactCenterX()-origin[0],y=rect.exactCenterY()-origin[1];
   NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,down,down,x,y);ui.candidates.getJSONObject(1).put("revision",3);ui.render(false);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_UP,down,down+30,x,y);assertTrue(ui.overviewMode);assertEquals(0,ui.selected);
   api.owner="different-account";tile.performClick();view.bindVisible();assertTrue(ui.overviewMode);assertEquals(0,loaded(view));assertEquals(0,ui.selected);api.owner="recruit-decisions";h.epoch++;tile.performClick();view.bindVisible();assertEquals(0,loaded(view));assertEquals(0,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void existingActionReceiptRefreshesOverviewWithoutSubmittingAnythingElse()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(3);NativeRecruitSession ui=open(h,api,rows);ui.hideOverview();layout(h);
   Button reject=ui.slots.get(0).findViewWithTag("recruit-reject");assertNotNull(reject);reject.performClick();assertEquals(1,api.paths.size());JSONObject body=api.bodies.get(0);assertEquals("reject",body.optString("action"));overview(ui);
   // There is no large certificate to shred after its portrait owner was deliberately released.
   h.motionEnabled=true;api.pending.done(new RecruitCandidateDecisionTest().rejected(body,new JSONArray().put(rows.getJSONObject(1)).put(rows.getJSONObject(2)),false),null);layout(h);
   assertTrue(ui.overviewMode);assertEquals(2,ui.overview.tiles.size());assertEquals("session-1",ui.overview.tiles.get(0).sessionId);assertEquals(51,ui.info.optInt("credits"));assertNull(ui.rejectionEffect);assertEquals(1,api.paths.size());assertEquals("",ui.prefs.getString(ui.key,""));
   tap(ui.overview,ui.overview.tiles.get(1));layout(h);assertEquals("session-2",ui.state.optString("sessionId"));assertEquals(1,api.paths.size());
  }
 }

 @Test public void closeAndReentryReleaseTheOldGridAndKeepOnlyTheNewRound()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(12));overview(ui);RecruitOverviewView old=ui.overview;RecruitOverviewView.Tile tile=old.tiles.get(1);h.page.removeView(ui.root);assertEquals(0,loaded(old));assertNull(ui.overviewBack);assertNull(ui.overview);
   JSONArray next=new JSONArray().put(NativeRecruitInertialRailTest.candidate(40));NativeRecruitSession fresh=open(h,api,next);tile.performClick();old.bindVisible();assertEquals(0,loaded(old));assertEquals(0,fresh.selected);assertEquals("session-40",fresh.state.optString("sessionId"));assertTrue(fresh.overviewMode);assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void giantAndFlowerUseExistingRegistrationImagesInCarouselAndOverviewCaptures()throws Exception{
  for(NativeHomePartyFeedbackTest.Profile next:PROFILES){profile=next;helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=RecruitCandidateDecisionTest.rows(2);
    // The active five-stat catalog identifies u03 as 자이언트 and u04 as 꽃 (u20 is 세이렌).
    rows.getJSONObject(0).getJSONObject("candidate").put("speciesId","u03").put("name","자이언트");rows.getJSONObject(1).getJSONObject("candidate").put("speciesId","u04").put("name","꽃");NativeRecruitSession ui=open(h,api,rows);ui.hideOverview();layout(h);int stageHeight=ui.stage.getHeight();
    Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"recruit-overview");shot(h,out,profile.name+"-giant-carousel");
    assertNull(h.root.findViewWithTag("theme-page-header"));assertNull("No reconstructed frame is layered over the original registration card",ui.slots.get(0).findViewWithTag("recruit-original-registration-frame"));TextView identity=ui.slots.get(0).findViewWithTag("recruit-registration-identity");assertEquals("방범대 등록증 · 자이언트 · 커먼",identity.getText().toString());
    ImageView giant=ui.slots.get(0).findViewWithTag("recruit-registration-card");assertTrue(giant.getDrawable() instanceof BitmapDrawable);Bitmap actual=((BitmapDrawable)giant.getDrawable()).getBitmap();boolean exact=false;String path=RegistrationV10Art.ROOT+RegistrationV10Art.file(h,"u03");for(int sample:new int[]{1,2,4,8})exact|=actual==LocalGameArt.peek(path,sample);assertTrue("The selected detail uses the existing completed registration artwork",exact);assertEquals("The printed registration retains its 3:4 ratio",actual.getHeight()*3,actual.getWidth()*4);
    for(Button action:new Button[]{ui.overviewControl,ui.confirmControl,ui.candidateInfo,(Button)h.root.findViewWithTag("recruit-close")}){NativeNoScrollReviewTest.assertFullyVisible(action,h.root);assertTrue(action.getWidth()>=h.dp(48));assertTrue(action.getHeight()>=h.dp(48));}
    overview(ui);assertEquals(stageHeight,ui.stage.getHeight());shot(h,out,profile.name+"-overview-giant-flower");assertEquals("u03",ui.overview.tiles.get(0).speciesId);assertEquals("u04",ui.overview.tiles.get(1).speciesId);
    for(RecruitOverviewView.Tile tile:ui.overview.tiles){NativeNoScrollReviewTest.assertFullyVisible(tile,h.root);assertTrue(tile.image.getDrawable() instanceof BitmapDrawable);for(int line=0;line<tile.identity.getLayout().getLineCount();line++)assertEquals(0,tile.identity.getLayout().getEllipsisCount(line));}
    tap(ui.overview,ui.overview.tiles.get(1));layout(h);assertEquals(1,ui.selected);assertEquals(stageHeight,ui.stage.getHeight());assertEquals(1,ui.slots.get(1).getScaleX(),.01);assertEquals(ui.rail.cardWidth,ui.rail.getScrollX());shot(h,out,profile.name+"-flower-carousel");assertTrue(api.paths.isEmpty());
    Files.write(out.resolve(profile.name+"-evidence.json"),MainActivity.obj("scope","offline native render only; not APK or physical device","profile",profile.json(),"giant","u03","flower","u04","stageHeight",stageHeight,"footerActionHeight",ui.controls.getHeight(),"requestCount",api.paths.size()).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }
  }
 }

 @Test public void detachedRejectCallbacksCannotMutateDuringOrAfterOverview()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(3));ui.hideOverview();layout(h);
   Button stale=ui.slots.get(0).findViewWithTag("recruit-reject");assertNotNull(stale);overview(ui);assertFalse(stale.isAttachedToWindow());stale.performClick();assertTrue(api.paths.isEmpty());assertTrue(ui.overviewMode);
   ui.hideOverview();layout(h);stale.performClick();assertTrue("Returning to the same candidate does not revive a detached action",api.paths.isEmpty());Button current=ui.slots.get(0).findViewWithTag("recruit-reject");assertNotSame(stale,current);current.performClick();assertEquals(1,api.paths.size());assertEquals("reject",api.bodies.get(0).optString("action"));
  }
 }
 @Test public void keyboardActivationAfterFlingStopRemainsAViewOnlySelection()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(50));overview(ui);RecruitOverviewView grid=ui.overview;
   grid.fling(1800);RecruitOverviewView.Tile tile=grid.tiles.stream().filter(t->t.getTop()>=grid.getScrollY()&&t.getBottom()<=grid.getScrollY()+grid.getHeight()).findFirst().orElseThrow();tap(grid,tile);assertTrue(ui.overviewMode);assertTrue("Keyboard activation enters real non-touch focus",tile.requestFocusFromTouch());assertTrue(tile.hasFocus());tile.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));tile.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));layout(h);
   assertFalse(ui.overviewMode);assertEquals(tile.index,ui.selected);assertTrue(api.paths.isEmpty());
  }
 }
}
