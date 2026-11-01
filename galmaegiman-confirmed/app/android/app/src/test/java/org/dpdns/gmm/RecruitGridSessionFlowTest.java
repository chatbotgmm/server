package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Real result-screen integration: visual stacks never become grouped financial commands. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitGridSessionFlowTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=RecruitCandidateDecisionTest.PHONE;
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}
 void layout(MainActivity h,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  helper.layout(h,profile);ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();helper.layout(h,profile);
 }
 static JSONArray stacked()throws Exception{
  JSONArray rows=RecruitCandidateDecisionTest.rows(3);
  JSONObject first=rows.getJSONObject(0),copy=new JSONObject(first.toString());
  first.put("batchIndex",0).put("batchSize",3);
  copy.put("sessionId","session-1").put("batchIndex",1).put("batchSize",3);
  rows.put(1,copy);
  return rows;
 }
 NativeRecruitSession open(MainActivity h,RecruitCandidateDecisionTest.Api api,JSONArray rows,NativeHomePartyFeedbackTest.Profile profile)throws Exception{
  helper.prepare(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
  NativeRecruitSession ui=new NativeRecruitSession(h);
  ui.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","result-grid","offers",new JSONArray(),"pendingSessions",rows));
  layout(h,profile);assertTrue(ui.overviewMode);assertNotNull(ui.overview);assertEquals(View.GONE,ui.overviewControl.getVisibility());
  assertEquals(View.INVISIBLE,ui.rail.getVisibility());assertEquals(1,ui.controls.getChildCount());assertEquals("전부 합격",ui.confirmControl.getText().toString());
  return ui;
 }

 @Test public void stackRerollUsesOneOriginalSessionAndRetryThenBulkKeepsEveryRevision()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=stacked();NativeRecruitSession ui=open(h,api,rows,PHONE);
   assertSame(rows,ui.candidates);assertSame(rows,ui.overview.rows);assertEquals(3,ui.candidates.length());assertEquals(2,ui.overview.tiles.size());
   TextView count=h.root.findViewWithTag("recruit-group-count-0");assertEquals("×2",count.getText().toString());
   Button reroll=h.root.findViewWithTag("recruit-grid-reroll-0");assertTrue(reroll.isEnabled());assertTrue(reroll.getText().toString().contains("지원서 1장"));
   Rect picture=new Rect(),target=new Rect();assertTrue(ui.overview.tiles.get(0).image.getGlobalVisibleRect(picture));assertTrue(reroll.getGlobalVisibleRect(target));assertTrue(target.top>=picture.bottom);assertTrue(target.width()>=h.dp(48)&&target.height()>=h.dp(48));
   reroll.performClick();reroll.performClick();assertEquals(1,api.paths.size());JSONObject request=api.bodies.get(0);assertEquals("session-0",request.optString("sessionId"));assertEquals(0,request.optInt("revision"));assertEquals("reroll",request.optString("action"));assertFalse(request.has("sessions"));
   api.pending.done(null,"network");assertTrue(ui.overviewMode);assertEquals(3,ui.candidates.length());assertFalse(reroll.isEnabled());assertNotNull(ui.retryControl);assertTrue(ui.retryControl.isEnabled());
   ui.retryControl.performClick();assertEquals(2,api.paths.size());assertEquals(request.toString(),api.bodies.get(1).toString());
   JSONObject changed=new JSONObject(rows.getJSONObject(0).toString()).put("revision",1).put("rerolls",1);changed.getJSONObject("candidate").put("speciesId","u04").put("name","꽃");
   JSONArray pending=new JSONArray().put(changed).put(rows.getJSONObject(1)).put(rows.getJSONObject(2));
   JSONObject response=new JSONObject(changed.toString()).put("requestId",request.optString("requestId")).put("credits",49).put("pendingSessions",pending);
   api.pending.done(response,null);layout(h,PHONE);assertTrue(ui.overviewMode);assertEquals(3,ui.candidates.length());assertEquals(3,ui.overview.tiles.size());assertEquals(0,ui.candidates.getJSONObject(1).optInt("rerolls"));assertEquals(1,ui.candidates.getJSONObject(0).optInt("rerolls"));
   ui.confirmControl.performClick();ui.confirmControl.performClick();assertEquals(3,api.paths.size());JSONObject bulk=api.bodies.get(2);assertTrue(api.paths.get(2).endsWith("claim-all"));JSONArray all=bulk.getJSONArray("sessions");assertEquals(3,all.length());assertEquals("session-0",all.getJSONObject(0).getString("sessionId"));assertEquals(1,all.getJSONObject(0).getInt("revision"));assertEquals("session-1",all.getJSONObject(1).getString("sessionId"));assertEquals(0,all.getJSONObject(1).getInt("revision"));
  }
 }

 @Test public void staleSnapshotDetachedSourceAndAnotherAccountCannotUseGridReroll()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,stacked(),PHONE);
   Button source=h.root.findViewWithTag("recruit-grid-reroll-0");JSONObject stale=ui.copyCandidate(ui.candidates.getJSONObject(0));
   ui.candidates.getJSONObject(0).put("revision",3);ui.rerollOverviewCandidate(source,stale);assertTrue(api.paths.isEmpty());
   ui.render(false);layout(h,PHONE);JSONObject current=ui.copyCandidate(ui.candidates.getJSONObject(0));api.owner="other-account";ui.rerollOverviewCandidate(source,current);assertTrue(api.paths.isEmpty());api.owner="recruit-decisions";
   ui.chooseOverviewCandidate("session-0");layout(h,PHONE);assertFalse(source.isShown());ui.rerollOverviewCandidate(source,current);assertTrue(api.paths.isEmpty());
   ui.showOverview();layout(h,PHONE);h.epoch++;ui.rerollOverviewCandidate((Button)h.root.findViewWithTag("recruit-grid-reroll-0"),current);assertTrue(api.paths.isEmpty());
  }
 }

 @Test public void pendingRerollKeepsReadOnlyDetailNavigationAndTheOriginalRetryIdentity()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,stacked(),PHONE);RecruitOverviewView grid=ui.overview;
   ((Button)h.root.findViewWithTag("recruit-grid-reroll-0")).performClick();assertEquals(1,api.paths.size());JSONObject request=api.bodies.get(0);assertTrue(ui.busy);
   grid.choose(grid.tiles.get(1),false);layout(h,PHONE);assertFalse("Read-only details remain available while sending",ui.overviewMode);assertEquals("session-2",ui.state.optString("sessionId"));Button detail=ui.slots.get(2).findViewWithTag("recruit-reroll");assertFalse(detail.isEnabled());detail.performClick();ui.confirmCandidates();assertEquals(1,api.paths.size());
   ui.showOverview();layout(h,PHONE);assertTrue(ui.overviewMode);api.pending.done(null,"network");layout(h,PHONE);assertFalse(ui.busy);assertFalse(((Button)h.root.findViewWithTag("recruit-grid-reroll-0")).isEnabled());
   grid.choose(grid.tiles.get(1),false);layout(h,PHONE);assertFalse("Uncertain requests do not block reading another candidate",ui.overviewMode);assertEquals("session-2",ui.state.optString("sessionId"));assertFalse(((Button)ui.slots.get(2).findViewWithTag("recruit-reroll")).isEnabled());assertTrue(ui.retryControl.isEnabled());ui.retryControl.performClick();assertEquals(2,api.paths.size());assertEquals(request.toString(),api.bodies.get(1).toString());assertEquals("session-0",api.bodies.get(1).optString("sessionId"));
  }
 }

 @Test public void detailBackRestoresTheSameScrolledGridWithoutDecidingAnyCandidate()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);NativeRecruitSession ui=open(h,api,RecruitCandidateDecisionTest.rows(30),PHONE);RecruitOverviewView grid=ui.overview;
   grid.scrollTo(0,grid.grid.getHeight());grid.bindVisible();layout(h,PHONE);int scroll=grid.getScrollY();assertTrue(scroll>0);
   ui.chooseOverviewCandidate("session-29");layout(h,PHONE);assertFalse(ui.overviewMode);assertEquals("session-29",ui.state.optString("sessionId"));assertEquals("결과 목록",ui.overviewControl.getText().toString());assertTrue(ui.overviewBack.isEnabled());
   h.getOnBackPressedDispatcher().onBackPressed();layout(h,PHONE);assertTrue(ui.overviewMode);assertSame(grid,ui.overview);assertEquals(scroll,grid.getScrollY());assertFalse(ui.overviewBack.isEnabled());assertEquals(View.GONE,ui.overviewControl.getVisibility());assertTrue(api.paths.isEmpty());assertEquals("",ui.prefs.getString(ui.key,""));
  }
 }

 @Test public void actualGridCapturesKeepFullScreenFooterAndReadableStackActionsAcrossProfiles()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={PHONE,
   new NativeHomePartyFeedbackTest.Profile("result-640x320",640,320,160,1,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("result-915-font130",915,412,160,1.3f,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("result-tablet",1280,720,160,1,0,0,0,0)};
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"recruit-results");Files.createDirectories(out);JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);JSONArray rows=stacked();rows.getJSONObject(0).getJSONObject("candidate").put("name","갈매미맨");rows.getJSONObject(1).getJSONObject("candidate").put("name","갈매미맨");rows.getJSONObject(2).getJSONObject("candidate").put("name","자이언트");NativeRecruitSession ui=open(h,api,rows,profile);
    NativeVillageHomeArtTest.requireFonts(h);assertNull(h.root.findViewWithTag("theme-page-header"));assertEquals(0,h.page.getPaddingTop());assertEquals(2,ui.overview.tiles.size());
    NativeNoScrollReviewTest.assertFullyVisible(ui.confirmControl,h.root);View close=h.root.findViewWithTag("recruit-close");NativeNoScrollReviewTest.assertFullyVisible(close,h.root);assertTrue(close.getWidth()>=h.dp(48)&&close.getHeight()>=h.dp(48));
    for(RecruitOverviewView.Tile tile:ui.overview.tiles){assertNotNull(tile.image.getDrawable());assertTrue(tile.reroll.getWidth()>=h.dp(48));assertTrue(tile.reroll.getHeight()>=h.dp(48));assertTrue(tile.identity.getText().length()>0);for(int line=0;line<tile.identity.getLayout().getLineCount();line++)assertEquals(0,tile.identity.getLayout().getEllipsisCount(line));}
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);String file=profile.name+"-default-grid.png";try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(file))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
    evidence.put(MainActivity.obj("file",file,"profile",profile.json(),"rawSessions",3,"displayGroups",2,"apiCommands",api.paths.size(),"defaultGrid",ui.overviewMode,"footerFixed",true));assertTrue(api.paths.isEmpty());
   }
  }
  Files.write(out.resolve("geometry.json"),evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
