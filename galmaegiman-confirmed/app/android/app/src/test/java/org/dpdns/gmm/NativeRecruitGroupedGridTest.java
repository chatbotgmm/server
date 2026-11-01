package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Real server-shaped rows, native gestures and packaged-art captures. No live account or paid API. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitGroupedGridTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 static final NativeHomePartyFeedbackTest.Profile PHONE=NativeHomePartyFeedbackTest.PROFILES[0];
 static final NativeHomePartyFeedbackTest.Profile[] PROFILES={PHONE,
   new NativeHomePartyFeedbackTest.Profile("grid-915x412-font130",915,412,160,1.3f,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("grid-1280x720",1280,720,160,1,0,0,0,0),
   new NativeHomePartyFeedbackTest.Profile("grid-640x320",640,320,160,1,0,0,0,0)};
 NativeHomePartyFeedbackTest.Profile profile=PHONE;
 final ArrayList<String> selections=new ArrayList<>();
 final ArrayList<JSONObject> rerolls=new ArrayList<>();
 @Before public void setup(){helper.isolate();helper.configure(PHONE);}
 @After public void restore(){helper.restore();}

 static JSONObject row(int index,String species){
  return MainActivity.obj("batchId","batch-"+(index/10),"batchIndex",index%10,"batchSize",50,
    "sessionId","grid-session-"+index,"revision",0,"status","PENDING","kind","LOW","baseCost",2,
    "rerollCost",1,"rerolls",0,"ownedId",JSONObject.NULL,"catalogVersion","offline-grouped-grid",
    "refundedTickets",0,"ownedSpecies",false,"canReject",false,"rejectRefund",0,"rejectReason","NOT_OWNED_SPECIES",
    "candidate",MainActivity.obj("speciesId",species,"name","u01".equals(species)?"갈매미맨":"u04".equals(species)?"꽃":species,
       "rarity","COMMON","level",10,"totalXp",0,"stats",MainActivity.obj("hp",20,"speed",10)));
 }
 static JSONArray identical(int count){JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(row(i,"u01"));return rows;}
 static JSONArray distinct(int count){JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(row(i,String.format(java.util.Locale.ROOT,"u%02d",i%60+1)));return rows;}
 void layout(MainActivity h)throws Exception{helper.layout(h,profile);ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();helper.layout(h,profile);}
 RecruitOverviewView open(MainActivity h,JSONArray rows)throws Exception{
  helper.prepare(h);h.base("모집","draw");h.page.removeAllViews();h.page.setPadding(0,0,0,0);
  RecruitOverviewView view=new RecruitOverviewView(h,()->true,selections::add,(button,source)->rerolls.add(source));
  h.page.addView(view,new LinearLayout.LayoutParams(-1,0,1));view.setCredits(50);view.showCandidates(rows);layout(h);view.bindVisible();LocalGameArtTest.drain();return view;
 }
 static void tap(RecruitOverviewView view,View target){
  Rect visible=new Rect();assertTrue(target.getGlobalVisibleRect(visible));int[] position=new int[2];view.getLocationOnScreen(position);
  float x=visible.exactCenterX()-position[0],y=visible.exactCenterY()-position[1];long now=SystemClock.uptimeMillis();
  NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,now,now,x,y);
  NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_UP,now,now+24,x,y);
  ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);
 }

 @Test public void fiftyIdenticalRealDtoRowsBecomeOneStackAndRerollExactlyOneOriginalSession()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   JSONArray original=identical(50);String unchanged=original.toString();RecruitOverviewView view=open(activity.get(),original);
   assertSame(original,view.rows);assertEquals(50,view.rows.length());assertEquals(1,view.groups.size());assertEquals(50,view.groups.get(0).members.size());assertEquals(1,view.tiles.size());
   RecruitOverviewView.Tile tile=view.tiles.get(0);assertEquals("×50",tile.quantity.getText().toString());assertEquals(View.VISIBLE,tile.quantity.getVisibility());assertTrue(tile.getContentDescription().toString().contains("동일 카드 50장"));
   assertEquals(Gravity.BOTTOM|Gravity.RIGHT,((FrameLayout.LayoutParams)tile.quantity.getLayoutParams()).gravity);
   assertTrue("The paid control is below the portrait and metadata",tile.reroll.getTop()>=tile.detail.getBottom());assertTrue(tile.reroll.getHeight()>=activity.get().dp(48));
   tap(view,tile.reroll);assertEquals(1,rerolls.size());assertEquals("grid-session-0",rerolls.get(0).optString("sessionId"));assertEquals(0,rerolls.get(0).optInt("revision"));assertFalse(rerolls.get(0).has("sessionIds"));
   assertNotSame(original.getJSONObject(0),rerolls.get(0));rerolls.get(0).put("revision",99);assertEquals(unchanged,original.toString());assertTrue(selections.isEmpty());
   assertTrue(tile.onKeyUp(KeyEvent.KEYCODE_ENTER,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)));assertEquals("grid-session-0",selections.get(0));
  }
 }

 @Test public void canonicalCandidateOrderGroupsButLevelStatsStatusAndAuthorityStaySeparate()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   JSONArray rows=identical(2);JSONObject candidate=rows.getJSONObject(1).getJSONObject("candidate");
   rows.getJSONObject(1).put("candidate",new JSONObject("{\"stats\":{\"speed\":10,\"hp\":20},\"totalXp\":0,\"level\":10,\"rarity\":\"COMMON\",\"name\":\"갈매미맨\",\"speciesId\":\"u01\"}"));
   JSONObject level=row(2,"u01");level.getJSONObject("candidate").put("level",11);rows.put(level);
   JSONObject stats=row(3,"u01");stats.getJSONObject("candidate").getJSONObject("stats").put("speed",11);rows.put(stats);
   JSONObject claimed=row(4,"u01");claimed.put("status","CLAIMED").put("ownedId","owned-4");rows.put(claimed);
   JSONObject paid=row(5,"u01");paid.put("rerolls",1);rows.put(paid);
   JSONObject refund=row(6,"u01");refund.put("ownedSpecies",true).put("canReject",true).put("rejectRefund",1).put("rejectReason","");rows.put(refund);
   RecruitOverviewView view=open(activity.get(),rows);assertEquals(6,view.groups.size());assertEquals(2,view.groups.get(0).members.size());assertEquals("×2",view.tiles.get(0).quantity.getText().toString());
   assertFalse(view.tiles.get(3).reroll.isEnabled());assertFalse(view.tiles.get(4).reroll.isEnabled());assertTrue(view.tiles.get(5).reroll.isEnabled());
   JSONObject a=MainActivity.obj("values",new JSONArray().put("first").put("second"));JSONObject b=MainActivity.obj("values",new JSONArray().put("second").put("first"));assertNotEquals(RecruitOverviewView.canonical(a),RecruitOverviewView.canonical(b));
  }
 }

 @Test public void unknownBalancePreservesServerJudgmentButKnownInsufficientOrExplicitDenialBlocks()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   JSONArray rows=identical(1);RecruitOverviewView view=open(activity.get(),rows);RecruitOverviewView.Tile tile=view.tiles.get(0);
   view.setCredits(null);assertTrue(tile.reroll.isEnabled());assertTrue(tile.reroll.getContentDescription().toString().contains("잔액 확인 필요"));tile.reroll.performClick();assertEquals(1,rerolls.size());
   view.setCredits(0);assertFalse(tile.reroll.isEnabled());tile.reroll.performClick();assertEquals(1,rerolls.size());
   view.setCredits(50);rows.getJSONObject(0).put("canReroll",false);view.showCandidates(rows);assertFalse(tile.reroll.isEnabled());tile.reroll.performClick();assertEquals(1,rerolls.size());
   rows.getJSONObject(0).remove("canReroll");rows.getJSONObject(0).put("rerollCost",JSONObject.NULL);view.showCandidates(rows);assertFalse(tile.reroll.isEnabled());assertTrue(tile.reroll.getText().toString().contains("확인 필요"));
   rows.getJSONObject(0).getJSONObject("candidate").remove("level");view.showCandidates(rows);assertTrue(tile.detail.getText().toString().contains("레벨 확인 필요"));
  }
 }

 @Test public void pendingMutationsBlockRerollButKeepReadOnlyCandidateInspection()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   RecruitOverviewView view=open(activity.get(),identical(2));RecruitOverviewView.Tile tile=view.tiles.get(0);view.setInteractionEnabled(false);
   assertFalse(tile.reroll.isEnabled());tile.reroll.performClick();assertTrue(rerolls.isEmpty());tile.performClick();assertEquals(1,selections.size());assertEquals("grid-session-0",selections.get(0));
   tile.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);assertEquals(2,selections.size());assertTrue(rerolls.isEmpty());view.setInteractionEnabled(true);assertTrue(tile.reroll.isEnabled());
  }
 }

 @Test public void identicalRefreshKeepsScrollAndVersionButReplacementInvalidatesReleasedQueuedClick()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();JSONArray rows=distinct(50);RecruitOverviewView view=open(h,rows);view.scrollTo(0,h.dp(170));layout(h);int scroll=view.getScrollY();long version=view.dataVersion;
   view.showCandidates(new JSONArray(rows.toString()));layout(h);assertEquals(scroll,view.getScrollY());assertEquals(version,view.dataVersion);
   view.scrollTo(0,0);layout(h);RecruitOverviewView.Tile first=view.tiles.get(0);
   // Android may post performClick after UP. Keep that released gesture bound to its original data.
   long down=SystemClock.uptimeMillis();NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,down,down,1,view.getPaddingTop()+1);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_UP,down,down+20,1,view.getPaddingTop()+1);
   view.post(()->first.reroll.performClick());rows.getJSONObject(0).put("revision",3);view.showCandidates(rows);ShadowLooper.idleMainLooper(32,TimeUnit.MILLISECONDS);assertTrue(rerolls.isEmpty());assertTrue(selections.isEmpty());
   first.reroll.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);assertEquals(1,rerolls.size());assertEquals(3,rerolls.get(0).optInt("revision"));
  }
 }

 @Test public void dragCancelFirstFlingStopAndHiddenWindowCannotSpend()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();RecruitOverviewView view=open(h,distinct(50));RecruitOverviewView.Tile tile=view.tiles.get(0);long down=SystemClock.uptimeMillis();
   NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_DOWN,down,down,view.getWidth()/2f,view.getHeight()/2f);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_MOVE,down,down+16,view.getWidth()/2f,view.getHeight()/2f-50);NativeRecruitInertialRailTest.touch(view,MotionEvent.ACTION_CANCEL,down,down+32,view.getWidth()/2f,view.getHeight()/2f-50);tile.reroll.performClick();assertTrue(rerolls.isEmpty());
   view.scrollTo(0,0);layout(h);view.fling(1800);tap(view,tile.reroll);assertTrue("First touch only stops the fling",rerolls.isEmpty());tap(view,tile.reroll);assertEquals(1,rerolls.size());
   view.onWindowFocusChanged(false);tile.reroll.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);tile.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);assertEquals(1,rerolls.size());assertTrue(selections.isEmpty());view.bindVisible();assertFalse(tile.loaded);
   view.onWindowFocusChanged(true);layout(h);tile.reroll.performClick();assertEquals("A delayed click from a hidden window cannot spend on return",1,rerolls.size());tap(view,tile.reroll);assertEquals(2,rerolls.size());
  }
 }

 @Test public void fixedCloseLaneClipsScrolledCardsAndCannotReceivePaidTouches()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();RecruitOverviewView view=open(h,distinct(50));view.scrollTo(0,view.grid.getHeight());layout(h);assertTrue(view.getScrollY()>0);assertEquals(h.dp(52),view.getPaddingTop());assertTrue(view.getClipToPadding());
   long down=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,view.getWidth()/2f,h.dp(30),0);boolean handled;try{handled=view.dispatchTouchEvent(event);}finally{event.recycle();}assertFalse("The empty fixed close lane is not a card hit target",handled);assertTrue(rerolls.isEmpty());assertTrue(selections.isEmpty());
   for(RecruitOverviewView.Tile tile:view.tiles){Rect visible=new Rect();if(tile.reroll.getGlobalVisibleRect(visible)){int[] location=new int[2];view.getLocationOnScreen(location);assertTrue("Only the clipped content portion can remain visible",visible.top>=location[1]+view.getPaddingTop());}}
  }
 }

 @Test public void actualSessionCapturesShowCenteredResultsCountsAndLowerRerollAcrossProfiles()throws Exception{
  Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","/workspace/scratch/gmm107-layout-evidence/captures"),"grouped-grid");Files.createDirectories(out);JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile p:PROFILES){profile=p;helper.configure(p);
   try(ActivityController<MainActivity> activity=helper.flow.start()){
    MainActivity h=activity.get();helper.prepare(h);RecruitCandidateDecisionTest.Api api=new RecruitCandidateDecisionTest.Api(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
    JSONArray rows=identical(25);for(int i=25;i<50;i++)rows.put(row(i,"u04"));NativeRecruitSession session=new NativeRecruitSession(h);session.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","offline-grouped-grid","offers",new JSONArray(),"pendingSessions",rows));layout(h);
    assertTrue("The grid is the actual default results screen",session.overviewMode);RecruitOverviewView view=session.overview;assertNotNull(view);view.bindVisible();LocalGameArtTest.drain();layout(h);assertEquals(2,view.tiles.size());
    RecruitOverviewView.Tile first=view.tiles.get(0),last=view.tiles.get(1);assertEquals("×25",first.quantity.getText().toString());assertEquals(50,view.rows.length());assertTrue(view.summary.getText().toString().contains("50명"));assertTrue(first.image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
    assertTrue("A small result row is centered",Math.abs((first.getLeft()+last.getRight())/2f-view.grid.getWidth()/2f)<=h.dp(2));
    for(RecruitOverviewView.Tile tile:view.tiles){assertTrue(tile.reroll.getTop()>=tile.detail.getBottom());assertTrue(tile.reroll.getHeight()>=h.dp(48));assertTrue(tile.reroll.getWidth()>=h.dp(48));assertTrue(tile.quantity.getRight()<=tile.illustration.getWidth());assertTrue(tile.quantity.getBottom()<=tile.illustration.getHeight());}
    Button all=session.confirmControl;assertNotNull(all);assertEquals("전부 합격",all.getText().toString());NativeNoScrollReviewTest.assertFullyVisible(all,h.root);assertTrue(api.paths.isEmpty());
    String file=p.name+"-grouped-recruit-results.png";Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(out.resolve(file))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{bitmap.recycle();}
    evidence.put(MainActivity.obj("file",file,"profile",p.json(),"groups",view.tiles.size(),"originalSessions",view.rows.length(),"firstCount",view.groups.get(0).members.size(),"cardWidth",first.getWidth(),"portraitHeight",first.image.getHeight(),"rerollHeight",first.reroll.getHeight(),"rerollBelowCard",first.reroll.getTop()>=first.detail.getBottom(),"apiWrites",api.paths.size()));
    session.open(MainActivity.obj("enabled",true,"credits",50,"catalogVersion","offline-grouped-grid","offers",new JSONArray(),"pendingSessions",distinct(50)));layout(h);view=session.overview;view.bindVisible();LocalGameArtTest.drain();layout(h);assertEquals(50,view.tiles.size());
    long loaded=view.tiles.stream().filter(tile->tile.loaded).count();assertTrue(loaded>0&&loaded<50);NativeRecruitOverviewTest.shot(h,out,p.name+"-fifty-results-top");
    view.scrollTo(0,view.grid.getHeight());layout(h);view.bindVisible();LocalGameArtTest.drain();assertFalse(view.tiles.get(0).loaded);assertTrue(view.tiles.get(49).loaded);NativeRecruitOverviewTest.shot(h,out,p.name+"-fifty-results-bottom");
    Rect close=new Rect();assertTrue(session.stage.findViewWithTag("recruit-close").getGlobalVisibleRect(close));
    for(RecruitOverviewView.Tile tile:view.tiles){Rect visible=new Rect();if(tile.reroll.getGlobalVisibleRect(visible))assertFalse("Visible reroll targets remain outside the fixed close button",Rect.intersects(close,visible));}
    assertEquals(50,session.pendingRows().length());assertTrue(api.paths.isEmpty());evidence.put(MainActivity.obj("profile",p.json(),"fiftyGroups",view.tiles.size(),"authoritativePendingSessions",session.pendingRows().length(),"scrollY",view.getScrollY(),"scrollRange",view.grid.getHeight()-view.getHeight()+view.getPaddingTop()+view.getPaddingBottom(),"apiWrites",api.paths.size()));
   }
  }
  Files.write(out.resolve("geometry.json"),evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
