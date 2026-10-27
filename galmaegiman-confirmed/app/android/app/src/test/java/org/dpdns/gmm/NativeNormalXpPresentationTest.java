package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.view.View;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Server-authored XP amounts and pending conditions, with the shared battle transport preserved. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeNormalXpPresentationTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();}
 @After public void restore()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static JSONObject settlement(String status,long awarded,long deferred){return MainActivity.obj("xpAwarded",awarded,"xpRequested",awarded+deferred,"xpDeferred",deferred,"xpDiscarded",0,"normalXp",MainActivity.obj("status",status,"units",new JSONArray().put(MainActivity.obj("ownedId","server-owned","appliedXp",999,"discardedXp",777))));}
 static JSONObject clamped(long awarded,long discarded)throws Exception{JSONObject result=settlement("CAP_CLAMPED",awarded,0);return result.put("xpDiscarded",discarded).put("xpRequested",awarded+discarded);}
 static JSONObject feedback(String key,String next,boolean replay){String kind=key.equals("INSUFFICIENT_RECORD")?"record":key.endsWith("_KO")?"battle":"termination";return MainActivity.obj("version","roster60-loss-feedback-v1","kind",kind,"mode","normal","outcome","loss","opponentKind","HUMAN","reason",kind.equals("termination")?key:"BATTLE","explanationKey",key,"nextAttemptKey",next,"fact",MainActivity.obj("kind",key.equals("REFLECTED_KO")?"reflected-ko":key.equals("ENEMY_FIRST_KO")?"enemy-first-ko":"direct-ko","turn",0,"roundIndex",0,"eventIndex",1,"hpBefore",10,"hpAfter",0,"damage",10,"element","FIRE"),"replay",MainActivity.obj("available",replay,"turn",0));}
 @Test public void appliedAndDeferredAmountsComeFromServerRatherThanUnitRecalculation()throws Exception{
  assertEquals("대원 경험치 +37 · 지급 완료",NativeRoster60PvpBattle.normalXpText(settlement("APPLIED",37,0)));
  String cap=NativeRoster60PvpBattle.normalXpText(settlement("CAP_POLICY_PENDING",0,85));assertTrue(cap.contains("보류 85"));assertTrue(cap.contains("자동 지급 아님"));assertFalse(cap.contains("지급 완료"));assertFalse(cap.contains("+0"));
  String mixed=NativeRoster60PvpBattle.normalXpText(settlement("CAP_POLICY_PENDING",37,85));assertTrue(mixed.contains("+37"));assertTrue(mixed.contains("보류 85"));
  JSONObject malformed=settlement("APPLIED",37,0);malformed.remove("xpAwarded");assertTrue(NativeRoster60PvpBattle.normalXpText(malformed).contains("다시 확인"));malformed.put("xpAwarded",-3);assertTrue(NativeRoster60PvpBattle.normalXpText(malformed).contains("다시 확인"));
 }
 @Test public void aClampedGrantShowsBothAppliedAndDiscardedWithoutBankingOrRecalculatingUnits()throws Exception{
  String partial=NativeRoster60PvpBattle.normalXpText(clamped(17,85));assertTrue(partial.contains("+17"));assertTrue(partial.contains("상한 초과 85"));assertTrue(partial.contains("저장 안 됨"));assertFalse(partial.contains("보류"));assertFalse(partial.contains("999"));assertFalse(partial.contains("777"));
  String full=NativeRoster60PvpBattle.normalXpText(clamped(0,85));assertTrue(full.contains("+0"));assertTrue(full.contains("상한 초과 85"));assertFalse(full.contains("보류"));
  JSONObject malformed=clamped(17,85);malformed.remove("xpDiscarded");assertTrue(NativeRoster60PvpBattle.normalXpText(malformed).contains("다시 확인"));malformed.put("xpDiscarded",-1);assertTrue(NativeRoster60PvpBattle.normalXpText(malformed).contains("다시 확인"));malformed.put("xpDiscarded",1.5);assertTrue(NativeRoster60PvpBattle.normalXpText(malformed).contains("다시 확인"));
 }
 @Test public void lossFeedbackUsesOnlyVerifiedEventKeysAndNeverTurnsTimeoutIntoDisconnection()throws Exception{
  JSONObject result=clamped(17,85).put("lossFeedback",feedback("REFLECTED_KO","TRY_DIFFERENT_ELEMENT",true));String reflected=NativeRoster60PvpBattle.lossFeedbackText(result);assertTrue(reflected.contains("1턴"));assertTrue(reflected.contains("불 기술 반사"));assertTrue(reflected.contains("다른 속성"));assertFalse(reflected.contains("기록 없음"));assertFalse(reflected.contains("유일"));assertFalse(reflected.contains("17"));
  result.put("lossFeedback",feedback("TIMEOUT","CHECK_TURN_DEADLINE_AND_REPLAY",false));String timeout=NativeRoster60PvpBattle.lossFeedbackText(result);assertTrue(timeout.contains("제한 시간"));assertFalse(timeout.contains("연결"));assertTrue(timeout.contains("재생 기록 없음"));
  result.put("lossFeedback",feedback("DISCONNECTED","CHECK_CONNECTION_AND_REPLAY",false));assertTrue(NativeRoster60PvpBattle.lossFeedbackText(result).contains("연결 종료"));
  JSONObject bad=feedback("REFLECTED_KO","TRY_DIFFERENT_ELEMENT",true);bad.getJSONObject("fact").remove("element");result.put("lossFeedback",bad);assertEquals("",NativeRoster60PvpBattle.lossFeedbackText(result));bad=feedback("DIRECT_KO","UNKNOWN_NEXT",true);result.put("lossFeedback",bad);assertEquals("",NativeRoster60PvpBattle.lossFeedbackText(result));
 }
 @Test public void oldPolicyDrawAndUnsupportedTerminalRemainExplicitlyUnpaid()throws Exception{
  for(String pending:new String[]{"LEGACY_POLICY_UNCONFIGURED","DRAW_POLICY_PENDING","TERMINAL_POLICY_PENDING","TIMEOUT_RESOLUTION_POLICY_PENDING"}){String label=NativeRoster60PvpBattle.normalXpText(settlement(pending,0,0));assertTrue(pending,label.contains("지급 보류"));assertFalse(label.contains("지급 완료"));assertFalse(label.contains("+0"));}
  assertEquals("항복 · 대원 경험치 0",NativeRoster60PvpBattle.normalXpText(settlement("SURRENDER_ZERO",0,0)));
  assertTrue(NativeRoster60PvpBattle.normalXpText(null).contains("다시 확인"));
 }
 @Test public void actualHumanAndAiResultPanelsShareAuthoritativeNormalXpAndRankStaysSeparate()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();helper.prepare(h);NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;
   for(String kind:new String[]{"HUMAN","AI"}){
    JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal").put("opponentKind",kind).put("status","SETTLED").put("settlement",clamped(17,85));
    h.openRosterHumanBattle(battle);NativeRoster60PvpBattle view=(NativeRoster60PvpBattle)h.rosterBattle;
    assertEquals(View.VISIBLE,view.stats.getVisibility());assertTrue(view.stats.getText().toString().contains("+17"));assertTrue(view.stats.getText().toString().contains("상한 초과 85"));assertFalse(view.stats.getText().toString().contains("보류"));assertFalse(view.stats.getText().toString().contains("결과 저장 완료"));
    battle.put("revision",1).put("settlement",settlement("APPLIED",37,0));view.renderSnapshot(battle);assertEquals("대원 경험치 +37 · 지급 완료",view.stats.getText().toString());view.pause();
   }
   JSONObject rank=NativeRoster60HumanPvpTest.battle();rank.put("mode","rank").put("status","SETTLED").put("settlement",MainActivity.obj("xpAwarded",0));h.openRosterHumanBattle(rank);assertTrue(h.rosterBattle.stats.getText().toString().contains("결과 저장 완료"));assertFalse(h.rosterBattle.stats.getText().toString().contains("지급 보류"));h.rosterBattle.pause();assertTrue(api.writes.isEmpty());
  }
 }
 @Test public void catalogDriftKeepsCancellationAndDoesNotInventAiMatchingOrResetRankClock()throws Exception{
  try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();helper.prepare(h);NativePlayNavigationTest.Api api=new NativePlayNavigationTest.Api(h);h.api=api;
   h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();api.current=MainActivity.obj("status","waiting","mode","normal","normalXpCondition","NORMAL_XP_CATALOG_CHANGED","cancelAllowed",true,"queuedAt",1000000L,"serverNow",1031000L,"aiAvailableAt",1030000L);
   h.openRosterPvp("normal");NativeRoster60Pvp queue=h.rosterPvp;assertTrue(queue.status.getText().toString().contains("성장 설정"));assertTrue(queue.status.getText().toString().contains("취소한 뒤 다시 참가"));assertTrue(queue.cancel.isEnabled());assertTrue(api.writes.isEmpty());queue.cancel.performClick();assertEquals(1,api.writes.size());assertTrue(api.paths.contains("native/roster60/pvp/cancel"));queue.pause();
   h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();api.current.put("mode","rank");h.openRosterPvp("rank");assertTrue(h.rosterPvp.status.getText().toString().contains("0:31 대기"));assertFalse(h.rosterPvp.status.getText().toString().contains("AI"));assertFalse(h.rosterPvp.status.getText().toString().contains("성장 설정"));h.rosterPvp.pause();
  }
 }
 @Test public void knownEarlyPolicyRejectionsBecomeProductCopyWithoutLeakingConfigKeys()throws Exception{
  for(String code:new String[]{"NORMAL_XP_POLICY_PENDING","NORMAL_XP_POLICY_INVALID","NORMAL_XP_SOURCE_PENDING","NORMAL_XP_SOURCE_INVALID"}){String label=NativeRoster60Pvp.normalXpNotice(code);assertTrue(label.contains("성장 보상"));assertFalse(label.contains("NORMAL_XP"));}
  assertTrue(NativeRoster60Pvp.normalXpNotice("NORMAL_XP_CATALOG_CHANGED").contains("다시 참가"));
 }
 @Test public void appliedCapAndLegacyResultCopyRendersAtThreeLandscapeProfiles()throws Exception{
  java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"normal-xp");java.nio.file.Files.createDirectories(out);
  for(NativeHomePartyFeedbackTest.Profile profile:NativeVillageHomeTest.PROFILES){helper.configure(profile);try(ActivityController<MainActivity> activity=helper.flow.start()){
   MainActivity h=activity.get();helper.prepare(h);NativeRoster60HumanPvpTest.Api api=new NativeRoster60HumanPvpTest.Api(h);h.api=api;
   for(String state:new String[]{"APPLIED","CAP_CLAMPED","CAP_POLICY_PENDING","LEGACY_POLICY_UNCONFIGURED"}){
    JSONObject battle=NativeRoster60HumanPvpTest.battle();battle.put("mode","normal").put("status","SETTLED").put("settlement",state.equals("CAP_CLAMPED")?clamped(17,85):settlement(state,state.equals("APPLIED")?37:0,state.equals("CAP_POLICY_PENDING")?85:0));h.openRosterHumanBattle(battle);helper.layout(h,profile);LocalGameArtTest.drain();helper.layout(h,profile);NativeRoster60PvpBattle ui=(NativeRoster60PvpBattle)h.rosterBattle;
    NativeNoScrollReviewTest.assertFullyVisible(ui.stats,h.root);assertNotNull(ui.stats.getLayout());assertTrue(ui.stats.getLayout().getHeight()<=ui.stats.getHeight()-ui.stats.getPaddingTop()-ui.stats.getPaddingBottom());
    android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve(profile.name+"-"+state+".png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}ui.pause();
   }
  }}
 }
}
