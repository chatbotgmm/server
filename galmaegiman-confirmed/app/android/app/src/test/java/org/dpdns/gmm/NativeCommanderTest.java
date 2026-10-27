package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Server-only account growth snapshots, replay safety, and actual native profile/result rendering. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCommanderTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeRankAdmissionRenderReviewTest.PROFILES[0]);}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static JSONObject ready(long level,long revision){return MainActivity.obj("name","방범대장","level",level,"totalXp","12000","nextLevelXp","12300","xpToNext","300","maxLevel",99,"policyVersion","commander-offline-test-v1","revision",revision,"initialized",true,"status","READY","testValues",true,"productionBalanceApproved",false,"initialization","TEST_ZERO_NO_BACKFILL","unitXpIndependent",true,"totalXpPolicy","retain-lifetime-total");}
 static JSONObject reward(JSONObject commander,long awarded,long recorded,boolean replayed){return MainActivity.obj("status",recorded>0?"APPLIED":"RECORDED_ZERO","reason","NORMAL_LOSS","xpAwarded",awarded,"recordedXp",recorded,"requestedXp",recorded,"replayed",replayed,"commander",commander);}
 static final class Api extends NativeApi {
  String account="commander-"+UUID.randomUUID()+".1.signature";JSONObject commander=ready(60,10),legacy;boolean deferCommander,deferStory;Result storyPending;int unitLevel=60,reincarnation;
  final List<String> reads=new ArrayList<>(),writes=new ArrayList<>();final List<Result> pending=new ArrayList<>();
  Api(Context h,JSONObject legacy){super(h);this.legacy=legacy;}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result done){
   if(body!=null){writes.add(path);throw new AssertionError("Commander displays must never initialize, award, or reset: "+path);}reads.add(path);
   if(path.equals("native/roster60/commander")){if(deferCommander)pending.add(done);else done.done(commander,null);return;}
   if(path.equals("pvp/rank")){done.done(NativeThemeReviewTest.rank(),null);return;}
   if(path.equals("native/state")){try{done.done(new JSONObject(legacy.toString()).put("commander",commander),null);}catch(Exception invalid){throw new AssertionError(invalid);}return;}
   if(path.equals("native/roster60/story/state")){if(deferStory)storyPending=done;else done.done(MainActivity.obj("residents",new JSONArray(),"commander",commander),null);return;}
   if(path.equals("native/roster60/pvp/current")){done.done(MainActivity.obj("status","idle"),null);return;}
   if(path.equals("native/roster60/pvp/options")){JSONObject unit=MainActivity.obj("ownedId","commander-unit","speciesId","u01","name","확인 대원","level",unitLevel,"maxLevel",reincarnation>0?99:60,"reincarnation",reincarnation,"rankEligible",unitLevel>=60,"rankReason",unitLevel>=60?"":"RANK_UNIT_LEVEL_REQUIRED");done.done(MainActivity.obj("units",new JSONArray().put(unit),"partyIds",new JSONArray().put("commander-unit"),"rankEligibility",NativePlayNavigationTest.rankEligibility(60,true,""),"commander",commander),null);return;}
   if(path.equals("native/menu-access")){done.done(MainActivity.obj("features",new JSONObject()),null);return;}
   if(path.equals("starter-loans")){done.done(StarterLoanTestFixture.ineligible(),null);return;}
   throw new AssertionError("Unexpected account display read: "+path);
  }
 }
 Api prepare(MainActivity h)throws Exception{helper.prepare(h).close();h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));Api api=new Api(h,h.state);h.api=api;h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();return api;}

 @Test public void currentSnapshotsAreMonotonicAndOldReceiptsCannotRestoreAnUnavailablePolicy()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);String owner=api.account;int epoch=h.epoch;
   assertTrue(NativeCommander.sync(h,ready(60,10),owner,epoch));JSONObject repeated=new JSONObject(ready(60,10).toString());assertTrue("An identical same-revision response survives JSONObject integer/long round trips",NativeCommander.sync(h,repeated,owner,epoch));assertEquals("READY",NativeCommander.state(h).getString("status"));assertFalse(NativeCommander.syncReward(h,ready(20,3),owner,epoch));assertEquals(60,NativeCommander.state(h).getInt("level"));
   assertTrue(NativeCommander.syncReward(h,ready(61,11),owner,epoch));assertEquals(61,NativeCommander.state(h).getInt("level"));assertFalse(NativeCommander.sync(h,ready(59,9),owner,epoch));assertEquals(11,NativeCommander.state(h).getInt("revision"));
   NativeCommander.sync(h,NativeCommander.pending("POLICY_PENDING"),owner,epoch);assertTrue(NativeCommander.state(h).isNull("level"));assertFalse(NativeCommander.syncReward(h,ready(62,12),owner,epoch));assertTrue(NativeCommander.state(h).isNull("level"));
   assertTrue("A live identical snapshot restores the display after an unavailable policy read",NativeCommander.sync(h,ready(61,11),owner,epoch));JSONObject local=NativeCommander.state(h);local.put("level",1);assertEquals(61,NativeCommander.state(h).getInt("level"));assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void sessionEpochAndAccountScopeProtectReloginAndLateResponses()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);String first=api.account;int epoch=h.epoch;NativeCommander.sync(h,ready(60,10),first,epoch);
   api.account=Roster60StoryJournal.accountId(first)+".2.renewed";assertFalse(NativeCommander.sync(h,ready(1,999),first,epoch));assertEquals(60,NativeCommander.state(h).getInt("level"));String renewed=api.account;
   api.account="other-"+UUID.randomUUID()+".1.signature";assertTrue(NativeCommander.state(h).isNull("level"));assertFalse(NativeCommander.sync(h,ready(99,999),renewed,epoch));NativeCommander.sync(h,ready(3,1),api.account,epoch);assertEquals(3,NativeCommander.state(h).getInt("level"));
   api.account=renewed;h.epoch++;assertFalse(NativeCommander.sync(h,ready(1,1000),renewed,epoch));assertEquals(60,NativeCommander.state(h).getInt("level"));api.account="";assertTrue(NativeCommander.state(h).isNull("level"));
  }
 }
 @Test public void missingStateAndConflictingRevisionNeverGuessLevelOneOrAHardCap()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);String owner=api.account;int epoch=h.epoch;NativeCommander.sync(h,null,owner,epoch);assertTrue(NativeCommander.state(h).isNull("level"));assertFalse(NativeCommander.profileText(NativeCommander.state(h)).contains("Lv.1"));
   NativeCommander.sync(h,ready(60,10),owner,epoch);JSONObject conflict=ready(60,10).put("totalXp","999999");assertFalse(NativeCommander.sync(h,conflict,owner,epoch));assertEquals("DATA_INVALID",NativeCommander.state(h).getString("status"));
   JSONObject extension=ready(99,11).put("status","CURVE_EXTENSION_PENDING").put("totalXp","999999999999").put("nextLevelXp",JSONObject.NULL).put("xpToNext",JSONObject.NULL);NativeCommander.sync(h,extension,owner,epoch);String text=NativeCommander.profileText(NativeCommander.state(h));assertTrue(text.contains("Lv.99"));assertTrue(text.contains("999999999999"));assertTrue(text.contains("다음 성장 구간"));assertFalse(text.contains("최대"));assertFalse(text.contains("폐기"));
  }
 }
 @Test public void ownedUnitRebirthDoesNotResetCommanderStateOrUnlockRankForALevelOneUnit()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);h.openRosterPvp("rank");assertTrue(h.rosterPvp.rank.isEnabled());assertEquals(60,NativeCommander.state(h).getInt("level"));
   api.unitLevel=1;api.reincarnation=1;h.rosterPvp.fetchOptions();assertFalse(h.rosterPvp.rank.isEnabled());assertTrue(h.rosterPvp.rankNotice.getText().toString().contains("Lv.60"));assertEquals(60,NativeCommander.state(h).getInt("level"));assertEquals("12000",NativeCommander.state(h).getString("totalXp"));assertEquals(10,NativeCommander.state(h).getInt("revision"));assertTrue(api.writes.isEmpty());h.rosterPvp.pause();
  }
 }
 @Test public void authenticatedNativeStateFeedsTheCacheBeforeTheIndependentHomeReadCompletes()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);api.deferStory=true;h.load("home");assertNotNull(api.storyPending);assertEquals("home",h.screen);assertEquals(60,NativeCommander.state(h).getInt("level"));assertEquals(10,NativeCommander.state(h).getInt("revision"));assertEquals(1,count(api,"native/state"));assertEquals(0,count(api,"native/roster60/commander"));assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void onlyOpeningTheProfileFetchesCommanderAndLatePopupReadsCannotLowerIt()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);h.home();helper.layout(h,NativeRankAdmissionRenderReviewTest.PROFILES[0]);assertEquals(60,NativeCommander.state(h).getInt("level"));h.villageHome.view.moveCameraUnits(8006.25);helper.layout(h,NativeRankAdmissionRenderReviewTest.PROFILES[0]);assertEquals(0,count(api,"native/roster60/commander"));
   api.deferCommander=true;h.menus.extras.profile(h.villageHome.view.settings);PopupWindow first=h.menus.extras.profilePopup;h.menus.extras.profile(h.villageHome.view.settings);PopupWindow second=h.menus.extras.profilePopup;assertFalse(first.isShowing());assertEquals(2,api.pending.size());api.pending.get(1).done(ready(62,12),null);api.pending.get(0).done(ready(61,11),null);TextView text=second.getContentView().findViewWithTag("profile-commander");assertTrue(text.getText().toString().contains("Lv.62"));assertFalse(text.getText().toString().contains("Lv.61"));assertEquals(62,NativeCommander.state(h).getInt("level"));assertNull(h.root.findViewWithTag("profile-commander"));assertEquals(2,count(api,"native/roster60/commander"));assertTrue(api.writes.isEmpty());second.dismiss();
  }
 }
 static int count(Api api,String path){int count=0;for(String value:api.reads)if(value.equals(path))count++;return count;}

 @Test public void profileCommanderFieldsRenderAtThreeLandscapeProfilesWithoutAHigherKnownLevelBecomingAHardCap()throws Exception{
  JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:NativeRankAdmissionRenderReviewTest.PROFILES){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();prepare(h);
   for(String scenario:new String[]{"READY","POLICY_PENDING","CURVE_EXTENSION_PENDING"}){
    Api api=new Api(h,h.state);h.api=api;if(scenario.equals("POLICY_PENDING"))api.commander=NativeCommander.pending("POLICY_PENDING");else if(scenario.equals("CURVE_EXTENSION_PENDING"))api.commander=ready(99,10).put("status",scenario).put("nextLevelXp",JSONObject.NULL).put("xpToNext",JSONObject.NULL);h.home();helper.settle(h,profile);h.menus.extras.profile(h.villageHome.view.settings);PopupWindow popup=h.menus.extras.profilePopup;View panel=popup.getContentView();int limit=(int)h.safeBounds(h.root.getWidth(),h.root.getHeight()).height()-h.dp(56);panel.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());
    TextView text=panel.findViewWithTag("profile-commander");assertFits(text,panel);assertTrue(panel.getHeight()<=limit);assertFalse(text.getText().toString().contains("최대"));if(scenario.equals("POLICY_PENDING")){assertTrue("Pending server state: "+text.getText(),text.getText().toString().contains("준비 중"));assertFalse(text.getText().toString().contains("Lv.1"));}else assertTrue("Repeated live server reads retain the configured test state: "+text.getText(),text.getText().toString().contains("시험 수치"));for(String tag:new String[]{"profile-action-account","profile-action-pvp"}){Button b=panel.findViewWithTag(tag);assertFits(b,panel);assertTrue(b.getWidth()>=h.dp(48));assertTrue(b.getHeight()>=h.dp(48));}
    capture(panel,profile.name+"-profile-"+scenario+".png");evidence.put(MainActivity.obj("profile",profile.json(),"case",scenario,"commander",bounds(text),"panelHeight",panel.getHeight(),"panelLimit",limit));assertTrue(api.writes.isEmpty());popup.dismiss();api.close();
   }
  }}
  Files.write(output().resolve("profile-geometry.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 @Test public void commanderAndOwnedXpRemainSeparateAndAnOldRewardReceiptCannotLowerCurrentLevel()throws Exception{
  JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:NativeRankAdmissionRenderReviewTest.PROFILES){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();Api api=prepare(h);NativeCommander.sync(h,ready(60,10),api.account,h.epoch);
   for(boolean replay:new boolean[]{false,true}){
    JSONObject result=NativeNormalXpPresentationTest.clamped(17,85).put("commanderReward",reward(ready(replay?21:61,replay?3:11),replay?0:25,25,replay));result.put("lossFeedback",NativeNormalXpPresentationTest.feedback("REFLECTED_KO","TRY_DIFFERENT_ELEMENT",true));JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal").put("status","SETTLED").put("settlement",result);battle.getJSONObject("state").put("outcome","loss");battle.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).put("hp",0);h.openRosterHumanBattle(battle);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;helper.settle(h,profile);
    assertFits(ui.stats,h.root);assertFits(ui.commanderStats,h.root);assertFits(ui.lossFeedback,h.root);assertSeparate(ui.stats,ui.commanderStats);assertSeparate(ui.commanderStats,ui.lossFeedback);assertSeparate(ui.lossFeedback,ui.retry);assertTrue(ui.stats.getText().toString().contains("대원 경험치 +17"));assertTrue(ui.stats.getText().toString().contains("상한 초과 85"));assertTrue(ui.commanderStats.getText().toString().contains("방범대장 XP"));assertTrue(ui.commanderStats.getText().toString().contains("25"));if(replay){assertTrue(ui.commanderStats.getText().toString().contains("이번 추가 지급 0"));assertEquals(61,NativeCommander.state(h).getInt("level"));assertEquals(11,NativeCommander.state(h).getInt("revision"));}
    capture(h.root,profile.name+"-result-"+(replay?"OLD_RECEIPT":"NEW_REWARD")+".png");evidence.put(MainActivity.obj("profile",profile.json(),"replayed",replay,"ownedXp",bounds(ui.stats),"commanderXp",bounds(ui.commanderStats),"feedback",bounds(ui.lossFeedback)));assertTrue(api.writes.isEmpty());ui.pause();
   }
  }}
  Files.write(output().resolve("result-geometry.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 static void assertFits(TextView view,View root){NativeNoScrollReviewTest.assertFullyVisible(view,root);assertNotNull(view.getLayout());int height=view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom(),width=view.getWidth()-view.getCompoundPaddingLeft()-view.getCompoundPaddingRight();assertTrue("Full height: "+view.getText(),view.getLayout().getHeight()<=height);for(int line=0;line<view.getLayout().getLineCount();line++){assertEquals("No ellipsis: "+view.getText(),0,view.getLayout().getEllipsisCount(line));assertTrue("Full width: "+view.getText(),view.getLayout().getLineWidth(line)<=width+1);}assertTrue("Full content: "+view.getText(),view.getLayout().getLineEnd(view.getLayout().getLineCount()-1)>=view.getText().length());}
 static void assertSeparate(View first,View second){Rect a=new Rect(),b=new Rect();assertTrue(first.getGlobalVisibleRect(a));assertTrue(second.getGlobalVisibleRect(b));assertFalse(a+" intersects "+b,Rect.intersects(a,b));}
 static JSONObject bounds(View view){Rect r=new Rect();view.getGlobalVisibleRect(r);return MainActivity.obj("tag",String.valueOf(view.getTag()),"left",r.left,"top",r.top,"right",r.right,"bottom",r.bottom);}
 static Path output()throws Exception{Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"commander");Files.createDirectories(out);return out;}
 static void capture(View view,String name)throws Exception{Bitmap image=NativeHomePartyFeedbackTest.render(view);try(java.io.OutputStream stream=Files.newOutputStream(output().resolve(name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}}
}
