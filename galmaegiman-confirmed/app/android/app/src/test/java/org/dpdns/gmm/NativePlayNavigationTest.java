package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativePlayNavigationTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static class Api extends NativeApi {
  String account="play-account";JSONObject current=MainActivity.obj("status","idle"),eligibility=rankEligibility(60,true,"");boolean rankFields=true;int unitLevel=60;boolean unitEligible=true;String unitReason="RANK_UNIT_LEVEL_REQUIRED";
  final List<String> paths=new ArrayList<>();final List<JSONObject> writes=new ArrayList<>();Result pending,partyPending,currentPending;boolean deferParty,deferCurrent;
  Api(Context c){super(c);}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result cb){
   paths.add(path);if(body!=null)writes.add(NativeRealtime.copy(body));
   if(path.equals("native/roster60/pvp/options")){JSONArray units=new JSONArray(),ids=new JSONArray();for(int i=0;i<8;i++){JSONObject unit=MainActivity.obj("ownedId","o"+i,"speciesId","u01","name","갈매미맨 "+i,"level",unitLevel,"maxLevel",60);if(rankFields)try{unit.put("rankEligible",unitEligible).put("rankReason",unitEligible?"":unitReason);}catch(JSONException invalid){throw new AssertionError(invalid);}units.put(unit);if(i<6)ids.put("o"+i);}JSONObject options=MainActivity.obj("units",units,"partyIds",ids);if(rankFields)try{options.put("rankEligibility",eligibility);}catch(JSONException invalid){throw new AssertionError(invalid);}cb.done(options,null);}
   else if(path.equals("native/roster60/pvp/current")){if(deferCurrent)currentPending=cb;else cb.done(current,null);}
   else if(path.equals("native/roster60/pvp/party")){if(deferParty)partyPending=cb;else cb.done(new JSONObject(),null);}
   else if(path.equals("native/roster60/pvp/join")||path.equals("native/roster60/pvp/cancel"))pending=cb;
   else if(path.equals("native/roster60/home"))cb.done(MainActivity.obj("mailUnread",0),null);
   else if(path.equals("native/roster60/story/state"))cb.done(NativeRoster60StoryTest.state(),null);
   else if(path.equals("native/menu-access"))cb.done(MainActivity.obj("features",new JSONObject()),null);
   else if(path.equals("daily"))cb.done(MainActivity.obj("attend",MainActivity.obj("today",true),"missions",new JSONArray()),null);
   else if(path.equals("draw"))cb.done(MainActivity.obj("wait",3600000,"credits",30),null);
   else throw new AssertionError("PLAY must use the authenticated product route: "+path);
  }
 }
 static JSONObject rankEligibility(Integer level,boolean eligible,String reason){return MainActivity.obj("eligible",eligible,"reason",reason,"accountLevel",level==null?JSONObject.NULL:level,"requiredAccountLevel",60,"requiredUnitLevel",60,"pool",level==null?JSONObject.NULL:"FIRST_LIFE","scope","rank-only");}
 Api prepare(MainActivity h)throws Exception{helper.prepare(h);Api api=new Api(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.getSharedPreferences("roster60_story_v1",0).edit().clear().commit();return api;}
 static void choose(MainActivity h,String mode)throws Exception{LocalGameArtTest.drain();assertEquals("home",h.screen);assertNotNull(h.villageHome);h.villageHome.navigate(mode);}
 static int calls(Api api,String suffix){int n=0;for(String p:api.paths)if(p.endsWith(suffix))n++;return n;}

 @Test public void actualHomeSeparatesHarborAndTrainingWithoutStartingCombat()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.home();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
   assertNotNull(h.villageHome);assertTrue(h.root.getChildAt(0) instanceof NativeVillageHomeView);int homeReads=calls(api,"story/state");assertEquals(1,homeReads);
   h.villageHome.view.moveCameraUnits(4);LocalGameArtTest.drain();h.root.findViewWithTag("village-building-harbor").performClick();assertArrayEquals(new CharSequence[]{"탐험","스토리 외부 출항"},org.robolectric.Shadows.shadowOf(h.villageHome.menu).getItems());h.villageHome.menu.dismiss();h.villageHome.view.moveCameraUnits(1);LocalGameArtTest.drain();h.root.findViewWithTag("village-building-training").performClick();assertArrayEquals(new CharSequence[]{"일반전","랭크전"},org.robolectric.Shadows.shadowOf(h.villageHome.menu).getItems());h.villageHome.menu.dismiss();assertTrue(api.writes.isEmpty());
   h.openPlayMenu();assertEquals("home",h.screen);assertNull(h.root.findViewWithTag("play-menu-choices"));assertTrue(api.writes.isEmpty());
   int before=api.paths.size();h.busy=true;choose(h,"rank");h.busy=false;assertEquals("home",h.screen);assertEquals(before,api.paths.size());
   h.openRosterStory();assertEquals("home",h.screen);assertTrue(h.rosterStory.villageOverlayOpen());assertNotNull(h.rosterStory.state);assertNull(h.root.findViewWithTag("story-tab-main"));assertTrue(api.writes.isEmpty());
   assertFalse(api.paths.contains("native/roster60/battle/options"));assertFalse(api.paths.contains("native/roster60/battle/active"));h.back();assertEquals("home",h.screen);assertFalse(h.rosterStory.villageOverlayOpen());
  }
 }

 @Test public void eachMenuModeRequiresExplicitOneToSixMemberPartyThenOneCorrectJoin()throws Exception{
  for(int count:new int[]{1,6})for(String mode:new String[]{"normal","rank"})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();choose(h,mode);NativeRoster60Pvp p=h.rosterPvp;
   assertEquals(mode,p.selectedMode);assertEquals(6,p.selected.size());assertEquals(6,new HashSet<>(p.selected).size());assertTrue(api.writes.isEmpty());
   if(count==1)for(int i=5;i>0;i--)p.ownedTiles.get("o"+i).action.performClick();assertEquals(count,p.selected.size());assertEquals(6,p.slots.getChildCount());
   Button target=mode.equals("normal")?p.normal:p.rank,other=mode.equals("normal")?p.rank:p.normal;assertEquals(View.GONE,other.getVisibility());other.performClick();assertTrue(api.writes.isEmpty());
   target.performClick();target.performClick();assertEquals(2,api.writes.size());assertEquals(count,api.writes.get(0).getJSONArray("ownedIds").length());if(mode.equals("rank"))assertEquals("rank",api.writes.get(0).getString("mode"));else assertFalse(api.writes.get(0).has("mode"));assertEquals(mode,api.writes.get(1).getString("mode"));assertEquals(1,calls(api,"pvp/join"));assertFalse(api.paths.contains("native/team"));
   h.back();assertEquals("home",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void rankRequiresTheAccountReaderAndLevelSixtyWithoutInferringFromOwnedUnits()throws Exception{
  for(int scenario=0;scenario<3;scenario++)try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.unitLevel=99;
   if(scenario==0)api.rankFields=false;else api.eligibility=rankEligibility(scenario==1?null:59,false,scenario==1?"ACCOUNT_LEVEL_POLICY_PENDING":"RANK_ACCOUNT_LEVEL_REQUIRED");
   h.openRosterPvp("rank");NativeRoster60Pvp p=h.rosterPvp;assertFalse(p.rank.isEnabled());assertFalse(p.save.isEnabled());p.join("rank");p.save();assertTrue(api.writes.isEmpty());
   assertTrue(p.rankNotice.getText().toString().contains(scenario==2?"59 / 필요 Lv.60":"방범대장 레벨"));assertEquals("rank",p.selectedMode);p.pause();
  }
 }

 @Test public void rankRejectsLowLevelOrServerIneligibleUnitsWhileNormalAndGenericPartyKeepThem()throws Exception{
  for(boolean serverFlag:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.unitLevel=serverFlag?1:99;api.unitEligible=serverFlag;h.openRosterPvp("rank");NativeRoster60Pvp p=h.rosterPvp;
   assertFalse("Rank stays locked for unit level="+api.unitLevel+", server eligible="+serverFlag,p.rank.isEnabled());assertFalse("Invalid rank party cannot be saved",p.save.isEnabled());
   for(int i=0;i<6;i++)p.ownedTiles.get("o"+i).action.performClick();assertTrue("Every existing invalid member remains removable: "+p.selected,p.selected.isEmpty());
   p.ownedTiles.get("o6").action.performClick();assertTrue("Rank cannot add this unit",p.selected.isEmpty());assertTrue("Low-level denial explains the unit threshold: "+p.status.getText(),p.status.getText().toString().contains("Lv.60"));assertTrue("Refused selection never sends a write",api.writes.isEmpty());p.pause();
   h.openRosterPvp("normal");p=h.rosterPvp;assertEquals(6,p.selected.size());assertTrue(p.normal.isEnabled());p.normal.performClick();assertEquals(2,api.writes.size());assertFalse(api.writes.get(0).has("mode"));assertEquals("normal",api.writes.get(1).getString("mode"));p.pause();
  }
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.unitLevel=1;api.unitEligible=false;api.rankFields=false;h.team();NativeRoster60Pvp p=h.rosterPvp;assertTrue(p.management);assertTrue(p.save.isEnabled());p.save.performClick();assertEquals(1,api.writes.size());assertFalse(api.writes.get(0).has("mode"));p.pause();
  }
 }

 @Test public void explicitRankSaveCarriesRankModeAndAChangedEligibilityClearsOnlyItsDefinitiveRequest()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openRosterPvp("rank");NativeRoster60Pvp p=h.rosterPvp;assertTrue(p.rank.isEnabled());assertTrue(p.rankNotice.getText().toString().contains("방범대장 Lv.60"));p.save.performClick();assertEquals("rank",api.writes.get(0).getString("mode"));assertFalse("Replacement party schema has no requestId",api.writes.get(0).has("requestId"));
   p.rank.performClick();assertEquals(3,api.writes.size());assertEquals("rank",api.writes.get(1).getString("mode"));assertEquals("rank",api.writes.get(2).getString("mode"));assertFalse(p.prefs.getString(p.key,"").isEmpty());
   api.eligibility=rankEligibility(59,false,"RANK_ACCOUNT_LEVEL_REQUIRED");api.pending.done(null,"RANK_ACCOUNT_LEVEL_REQUIRED");assertTrue(p.prefs.getString(p.key,"").isEmpty());assertFalse(p.rank.isEnabled());assertEquals("rank",p.selectedMode);int joins=calls(api,"pvp/join");p.resume();assertEquals(joins,calls(api,"pvp/join"));p.pause();
  }
 }

 @Test public void recoveredRankJoinReplaysItsExactRequestBeforeCheckingNewEligibility()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openRosterPvp("rank");h.rosterPvp.rank.performClick();JSONObject original=api.writes.get(1);api.pending.done(null,"network");h.back();api.rankFields=false;h.openRosterPvp("rank");NativeRoster60Pvp p=h.rosterPvp;
   assertEquals(original.toString(),api.writes.get(2).toString());assertEquals(1,calls(api,"pvp/party"));api.current=MainActivity.obj("status","waiting","mode","rank","rankCondition","RANK_QUEUE_CLASS_CHANGED","queuedAt",1000000L,"serverNow",1010000L);api.pending.done(api.current,null);
   assertTrue(p.waiting);assertTrue(p.cancel.isEnabled());assertTrue(p.status.getText().toString().contains("환생 이력"));assertEquals("rank",p.selectedMode);assertEquals(2,calls(api,"pvp/join"));assertFalse(p.status.getText().toString().contains("AI"));p.pause();
  }
 }

 @Test public void sharedPoolChangesKeepBothQueuesCancelableAndClearDefinitiveNormalJoinReceipts()throws Exception{
  for(String mode:new String[]{"normal","rank"})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode",mode,"matchPoolCondition","MATCH_POOL_HISTORY_CHANGED","cancelAllowed",true,"queuedAt",1000000L,"serverNow",1031000L);h.openRosterPvp(mode);NativeRoster60Pvp p=h.rosterPvp;
   assertTrue(p.waiting);assertTrue(p.cancel.isEnabled());assertTrue(p.status.getText().toString().contains("매칭을 취소"));assertFalse(p.status.getText().toString().contains("MATCH_POOL"));assertTrue(api.writes.isEmpty());p.cancel.performClick();assertEquals(1,calls(api,"pvp/cancel"));assertEquals(0,calls(api,"pvp/join"));assertEquals(mode,p.selectedMode);p.pause();
  }
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openRosterPvp("normal");NativeRoster60Pvp p=h.rosterPvp;p.normal.performClick();assertFalse(p.prefs.getString(p.key,"").isEmpty());api.pending.done(null,"MATCH_POOL_HISTORY_CHANGED");assertTrue(p.prefs.getString(p.key,"").isEmpty());assertEquals("normal",p.selectedMode);int writes=api.writes.size();p.resume();assertEquals(writes,api.writes.size());p.pause();
  }
 }

 @Test public void waitingServerModeWinsOverAnotherMenuAndBackNeverCancelsQueue()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal");h.openPlayMenu();choose(h,"rank");
   assertTrue(h.rosterPvp.waiting);assertEquals("normal",h.rosterPvp.selectedMode);assertEquals(View.VISIBLE,h.rosterPvp.normal.getVisibility());assertEquals(View.GONE,h.rosterPvp.rank.getVisibility());assertTrue(h.title.getText().toString().contains("일반게임"));assertTrue(api.writes.isEmpty());
   h.back();assertEquals("home",h.screen);assertEquals(0,calls(api,"pvp/cancel"));choose(h,"rank");assertEquals("normal",h.rosterPvp.selectedMode);assertTrue(h.rosterPvp.waiting);
   h.rosterPvp.cancel.performClick();assertEquals(1,calls(api,"pvp/cancel"));assertEquals(0,calls(api,"pvp/join"));api.pending.done(MainActivity.obj("status","idle"),null);h.back();assertEquals("home",h.screen);
  }
 }

 @Test public void durableJoinKeepsOriginalModeAndRequestIdAcrossBackAndReentry()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();choose(h,"normal");h.rosterPvp.normal.performClick();JSONObject first=api.writes.get(1);String key=h.rosterPvp.key;
   api.pending.done(null,"network");assertFalse(h.rosterPvp.prefs.getString(key,"").isEmpty());h.back();assertEquals("home",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
   choose(h,"rank");assertEquals("normal",h.rosterPvp.selectedMode);assertEquals(3,api.writes.size());assertEquals(first.toString(),api.writes.get(2).toString());assertEquals(1,calls(api,"pvp/party"));
   h.back();assertEquals("home",h.screen);assertFalse(h.rosterPvp.prefs.getString(key,"").isEmpty());
   api.pending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("home",h.screen);assertTrue(h.rosterPvp.prefs.getString(key,"").isEmpty());
  }
 }

 @Test public void leavingDuringPartySavePreventsLateJoinAndStaleCurrentCannotChangeMode()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.deferParty=true;h.openPlayMenu();choose(h,"rank");h.rosterPvp.rank.performClick();assertNotNull(api.partyPending);h.back();assertEquals("home",h.screen);api.partyPending.done(new JSONObject(),null);assertEquals(0,calls(api,"pvp/join"));
   api.deferCurrent=true;choose(h,"rank");assertNotNull(api.currentPending);h.back();api.currentPending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("home",h.screen);assertEquals("rank",h.rosterPvpMode);
   choose(h,"rank");NativeRoster60Pvp p=h.rosterPvp;api.account="different-account";api.currentPending.done(MainActivity.obj("status","waiting","mode","normal"),null);assertEquals("rank",p.selectedMode);assertEquals(0,calls(api,"pvp/join"));assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void pendingCancelRestoresLocalModeAndLegacyMissingModeWaitsForServer()throws Exception{
  for(boolean metadata:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal");h.openPlayMenu();choose(h,"normal");NativeRoster60Pvp old=h.rosterPvp;old.cancel.performClick();JSONObject first=api.writes.get(0);api.pending.done(null,"network");
   JSONObject saved=new JSONObject(old.prefs.getString(old.key,""));assertEquals("normal",saved.getString("mode"));assertFalse(first.has("mode"));
   if(!metadata){saved.remove("mode");old.prefs.edit().putString(old.key,saved.toString()).commit();}
   h.back();assertEquals("home",h.screen);choose(h,"rank");NativeRoster60Pvp restored=h.rosterPvp;assertEquals(2,api.writes.size());assertEquals(first.toString(),api.writes.get(1).toString());assertEquals(0,calls(api,"pvp/join"));assertEquals(0,calls(api,"pvp/party"));
   if(metadata){assertEquals("normal",restored.selectedMode);assertTrue(h.title.getText().toString().contains("일반게임"));}else{assertEquals("",restored.selectedMode);assertTrue(restored.pendingModeUnknown);assertEquals("대전 확인 중",h.title.getText().toString());assertEquals(View.GONE,restored.normal.getVisibility());assertEquals(View.GONE,restored.rank.getVisibility());}
   api.pending.done(MainActivity.obj("status","idle"),null);assertFalse(restored.pendingModeUnknown);assertTrue(restored.prefs.getString(restored.key,"").isEmpty());assertEquals(metadata?"normal":"rank",restored.selectedMode);h.back();assertEquals("home",h.screen);assertEquals(2,calls(api,"pvp/cancel"));
  }
 }

 @Test public void serverWaitClockCrossesThirtySecondsWithoutClientMatchAndReconnectKeepsElapsed()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000000L,"serverNow",1029000L,"aiAvailableAt",1030000L);h.openPlayMenu();choose(h,"normal");NativeRoster60Pvp p=h.rosterPvp;
   assertTrue(p.status.getText().toString().contains("0:29 대기"));assertTrue(p.status.getText().toString().contains("1초 후 AI"));api.deferCurrent=true;org.robolectric.shadows.ShadowLooper.idleMainLooper(1000,java.util.concurrent.TimeUnit.MILLISECONDS);
   assertTrue(p.status.getText().toString().contains("0:30 대기"));assertTrue(p.status.getText().toString().contains("AI 매칭 확인 중"));assertTrue("The display never starts its own AI match",api.writes.isEmpty());
   h.back();int calls=api.paths.size();org.robolectric.shadows.ShadowLooper.idleMainLooper(3000,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals(calls,api.paths.size());
   api.deferCurrent=false;api.current.put("serverNow",1036000L);choose(h,"rank");assertEquals("normal",h.rosterPvp.selectedMode);assertTrue(h.rosterPvp.status.getText().toString().contains("0:36 대기"));
   h.rosterPvp.cancel.performClick();h.rosterPvp.cancel.performClick();assertEquals(1,calls(api,"pvp/cancel"));api.pending.done(MainActivity.obj("status","idle"),null);assertFalse(h.rosterPvp.waiting);h.rosterPvp.pause();
  }
 }

 @Test public void cancelAtThirtySecondsRemainsAvailableDuringSlowPollAndRejectsItsLateResult()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000000L,"serverNow",1028000L,"aiAvailableAt",1030000L);h.openPlayMenu();choose(h,"normal");NativeRoster60Pvp p=h.rosterPvp;api.deferCurrent=true;
   org.robolectric.shadows.ShadowLooper.idleMainLooper(2000,java.util.concurrent.TimeUnit.MILLISECONDS);assertTrue(p.currentRead);assertTrue(p.cancel.isEnabled());p.cancel.performClick();p.cancel.performClick();assertEquals(1,calls(api,"pvp/cancel"));assertFalse(p.cancel.isEnabled());
   JSONObject late=NativeRoster60HumanPvpTest.battle();late.put("mode","normal").put("opponentKind","AI");api.currentPending.done(MainActivity.obj("status","matched","battle",late),null);assertEquals("roster60Pvp",h.screen);assertTrue(p.busy);
   api.pending.done(MainActivity.obj("status","idle"),null);assertFalse(p.waiting);assertFalse(p.busy);assertFalse(p.status.getText().toString().contains("대기"));p.pause();assertEquals(1,api.writes.size());
  }
 }

 @Test public void durableJoinReplayReadsCurrentBeforeUsingTheCachedServerTimestamp()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();choose(h,"normal");h.rosterPvp.normal.performClick();JSONObject first=api.writes.get(1);api.pending.done(null,"network");h.back();
   choose(h,"rank");assertEquals(first.toString(),api.writes.get(2).toString());api.current=MainActivity.obj("status","waiting","mode","normal","queuedAt",1000000L,"serverNow",1024000L,"aiAvailableAt",1030000L);int before=calls(api,"pvp/current");
   api.pending.done(MainActivity.obj("status","waiting","mode","normal","queuedAt",1000000L,"serverNow",1000000L,"aiAvailableAt",1030000L),null);
   assertEquals(before+1,calls(api,"pvp/current"));assertTrue(h.rosterPvp.status.getText().toString().contains("0:24 대기"));assertTrue(h.rosterPvp.status.getText().toString().contains("6초 후 AI"));assertFalse(h.rosterPvp.status.getText().toString().contains("0:00"));assertEquals(3,api.writes.size());h.rosterPvp.pause();
  }
 }

 @Test public void rankWaitingNeverAdvertisesAiAndServerAiUsesSharedTransport()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);api.current=MainActivity.obj("status","waiting","mode","rank","queuedAt",1000000L,"serverNow",1065000L,"aiAvailableAt",1030000L);h.openPlayMenu();choose(h,"rank");
   assertTrue(h.rosterPvp.status.getText().toString().contains("1:05 대기"));assertFalse(h.rosterPvp.status.getText().toString().contains("AI"));h.back();
   JSONObject ai=NativeRoster60HumanPvpTest.battle();ai.put("mode","normal").put("opponentKind","AI").put("status","ACCEPTING").put("serverNow",1000000L).put("deadline",1010000L);api.current=MainActivity.obj("status","matched","mode","normal","battle",ai);choose(h,"normal");assertTrue(h.rosterBattle instanceof NativeRoster60PvpBattle);assertTrue(((NativeRoster60PvpBattle)h.rosterBattle).readyView.mode.getText().toString().contains("AI 상대"));assertNull(h.rosterBattle.art);assertTrue(h.rosterBattle.message.getText().toString().contains("10초"));h.rosterBattle.bind(new JSONObject(ai.toString()).put("status","ACTIVE"));assertTrue(h.rosterBattle.message.getText().toString().contains("AI 대전"));assertTrue(api.writes.isEmpty());assertFalse(api.paths.contains("native/roster60/battle/options"));h.rosterBattle.pause();
  }
 }

 @Test public void matchedOuterModeOverridesMenuWithoutStartingNpcOrNewJoin()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal");battle.getJSONObject("state").put("mode","duel");api.current=MainActivity.obj("status","matched","mode","normal","battle",battle);
   h.openPlayMenu();choose(h,"rank");assertTrue(h.rosterBattle instanceof NativeRoster60PvpBattle);assertEquals("normal",h.rosterPvpMode);assertEquals("normal",h.rosterBattle.view.optString("mode"));assertTrue(api.writes.isEmpty());assertEquals(0,calls(api,"battle/options"));assertEquals(0,calls(api,"pvp/join"));h.back();assertEquals("home",h.screen);assertEquals(0,calls(api,"pvp/cancel"));
  }
 }

 @Test public void villageReplacesDuplicateHubAndTrainingModeRemainsVisibleAcrossSafeAreaProfiles()throws Exception{
  List<NativeHomePartyFeedbackTest.Profile> profiles=new ArrayList<>(Arrays.asList(NativeHomePartyFeedbackTest.PROFILES));profiles.add(new NativeHomePartyFeedbackTest.Profile("tablet-1280x800",1280,800,160,1,0,0,0,0));JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);h.openPlayMenu();helper.layout(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);assertEquals("home",h.screen);assertNull(h.root.findViewWithTag("play-menu-choices"));assertNull(h.root.findViewWithTag("story-tab-main"));assertTrue(h.root.getChildAt(0) instanceof NativeVillageHomeView);captureSettled(h,profile,"village");
   choose(h,"rank");helper.layout(h,profile);LocalGameArtTest.drain();assertEquals(6,h.rosterPvp.slots.getChildCount());assertEquals("rank",h.rosterPvp.selectedMode);assertEquals(View.VISIBLE,h.rosterPvp.rank.getVisibility());assertEquals(View.GONE,h.rosterPvp.normal.getVisibility());assertTrue(api.writes.isEmpty());captureSettled(h,profile,"rank-party");evidence.put(MainActivity.obj("profile",profile.json(),"hubRemoved",true,"trainingMode","rank"));h.rosterPvp.pause();
  }}
  Files.write(output().resolve("geometry.json"),evidence.toString(2).getBytes(StandardCharsets.UTF_8));
 }
 Path output()throws Exception{Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"play-navigation");Files.createDirectories(p);return p;}
 void captureSettled(MainActivity h,NativeHomePartyFeedbackTest.Profile p,String scene)throws Exception{capture(h,p.name+"-"+scene+"-first-paint.png");RegistrationArtTest.await(()->h.art.idle()&&h.registrationArt().idle());org.robolectric.shadows.ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);helper.layout(h,p);capture(h,p.name+"-"+scene+".png");}
 void capture(MainActivity h,String name)throws Exception{Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream out=Files.newOutputStream(output().resolve(name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
}
