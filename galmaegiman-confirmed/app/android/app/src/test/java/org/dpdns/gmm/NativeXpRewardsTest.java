package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import java.math.BigInteger;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeXpRewardsTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();}
  @After public void restore(){fixture.restore();}
  static JSONObject snapshot(JSONObject settlement){return MainActivity.obj("battleId","xp-battle-1","status","SETTLED","settlement",settlement,"state",MainActivity.obj("fighters",new JSONArray().put(MainActivity.obj("ownedId","first","name","도시락 갈매미맨")),"reserves",new JSONArray().put(MainActivity.obj("ownedId","second","name","예비 대원"))));}
  static JSONObject commander(int level,long revision,String total,String next){return MainActivity.obj("level",level,"revision",revision,"totalXp",total,"nextLevelXp",next,"policyVersion","commander-test-v1","status","READY");}
  static JSONObject commanderReceipt(boolean replay){return MainActivity.obj("status","APPLIED","xpAwarded",replay?0:40,"recordedXp",40,"replayed",replay,"commander",commander(3,8,"140","200"));}
  static JSONObject unit(String id,int applied,String before,String after){return MainActivity.obj("ownedId",id,"appliedXp",applied,"startingXp",before,"endingXp",after,"status","APPLIED");}
  static JSONObject point(int level,String total,String start,String next){return MainActivity.obj("level",level,"maxLevel",60,"totalXp",total,"levelStartTotalXp",start,"nextLevelTotalXp",next==null?JSONObject.NULL:next);}
  static JSONObject progress(JSONObject before,JSONObject after){return MainActivity.obj("before",before,"after",after);}

  @Test public void immediateUnitsUseExactTotalsAndPendingReturnNeverLooksPaid()throws Exception{
    JSONObject source=snapshot(MainActivity.obj("unitXp",MainActivity.obj("delivery","immediate","units",new JSONArray().put(unit("first",60,"100","160")).put(unit("second",30,"900","930")))));
    String untouched=source.toString();NativeXpReceipt receipt=NativeXpReceipt.from(source,null);
    assertEquals("battle:xp-battle-1",receipt.event);assertEquals(2,receipt.rows.size());assertTrue(receipt.positive());
    assertEquals("도시락 갈매미맨",receipt.rows.get(0).label);assertEquals(60,receipt.rows.get(0).amount);
    assertEquals(new BigInteger("100"),receipt.rows.get(0).before);assertEquals(new BigInteger("160"),receipt.rows.get(0).after);
    assertEquals(0,receipt.rows.get(0).level);assertFalse(receipt.rows.get(0).levelUp());assertEquals(untouched,source.toString());
    source.getJSONObject("settlement").getJSONObject("unitXp").put("delivery","pending-return");
    receipt=NativeXpReceipt.from(source,null);assertFalse(receipt.positive());
    for(NativeXpReceipt.Row row:receipt.rows){assertTrue(row.pending);assertFalse(row.hasBar());assertNull(row.after);}
  }
  @Test public void normalReceiptUsesAppliedAmountsAndDoesNotInventUnitProgressOrLevels(){
    NativeXpReceipt receipt=NativeXpReceipt.from(snapshot(MainActivity.obj("xpRequested",300,"xpDeferred",90,"normalXp",MainActivity.obj("status","CAP_CLAMPED","units",new JSONArray().put(MainActivity.obj("ownedId","first","appliedXp",20,"requestedXp",200)).put(MainActivity.obj("ownedId","second","appliedXp",0,"deferredXp",90))))),null);
    assertEquals(2,receipt.rows.size());assertEquals(20,receipt.rows.get(0).amount);assertEquals(0,receipt.rows.get(1).amount);
    for(NativeXpReceipt.Row row:receipt.rows){assertFalse(row.hasBar());assertEquals(0,row.level);assertFalse(row.levelUp());}
  }
  @Test public void commanderUsesActualNextThresholdAndOnlyVerifiedConsecutiveLevelTransition(){
    JSONObject source=snapshot(MainActivity.obj("commanderReward",commanderReceipt(false)));
    NativeXpReceipt.Row row=NativeXpReceipt.from(source,commander(2,7,"100","120")).rows.get(0);
    assertEquals(40,row.amount);assertEquals(new BigInteger("100"),row.before);assertEquals(new BigInteger("140"),row.after);
    assertEquals(new BigInteger("200"),row.target);assertEquals(500,row.progress(row.before));assertEquals(700,row.progress(row.after));assertTrue(row.levelUp());
    assertFalse(NativeXpReceipt.from(source,commander(1,6,"100","120")).rows.get(0).levelUp());
    assertFalse(NativeXpReceipt.from(source,commander(2,7,"95","120")).rows.get(0).levelUp());
    NativeXpReceipt replay=NativeXpReceipt.from(snapshot(MainActivity.obj("commanderReward",commanderReceipt(true))),commander(2,7,"100","120"));
    assertTrue(replay.replayed);assertEquals(40,replay.rows.get(0).amount);assertFalse(replay.rows.get(0).levelUp());
  }
  @Test public void malformedAndUnsettledPayloadsNeverBecomePositiveXp(){
    assertTrue(NativeXpReceipt.from(snapshot(MainActivity.obj("xpAwarded",-1)),null).rows.isEmpty());
    assertTrue(NativeXpReceipt.from(snapshot(MainActivity.obj("xpAwarded","100")),null).rows.isEmpty());
    NativeXpReceipt badTotals=NativeXpReceipt.from(snapshot(MainActivity.obj("unitXp",MainActivity.obj("delivery","immediate","units",new JSONArray().put(unit("first",10,"100","999"))))),null);
    assertEquals(10,badTotals.rows.get(0).amount);assertFalse(badTotals.rows.get(0).hasBar());
    JSONObject active=MainActivity.obj("battleId","b","status","ACTIVE","settlement",MainActivity.obj("xpAwarded",100));assertTrue(NativeXpReceipt.from(active,null).rows.isEmpty());
  }
  @Test public void persistentPresentationMarkerScopesAccountServerEventAndRenewedSessions(){
    Context context=RuntimeEnvironment.getApplication();String account="xp-"+UUID.randomUUID();
    assertTrue(NativeXpRewards.claim(context,"https://one",account+".1.signature","battle:a"));
    assertFalse(NativeXpRewards.claim(context,"https://one",account+".2.renewed","battle:a"));
    assertTrue(NativeXpRewards.claim(context,"https://one",account+".2.renewed","battle:b"));
    assertTrue(NativeXpRewards.claim(context,"https://two",account+".1.signature","battle:a"));
    assertTrue(NativeXpRewards.claim(context,"https://one","other.1.signature","battle:a"));
    assertFalse(NativeXpRewards.claim(context,"https://one","","battle:a"));
  }
  @Test public void touchSkipCompletesExactNumbersAndReopenedRecordHasNoLevelUp()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h).close();
      NativeXpReceipt receipt=NativeXpReceipt.from(snapshot(MainActivity.obj("commanderReward",commanderReceipt(false),"unitXp",MainActivity.obj("delivery","immediate","units",new JSONArray().put(unit("first",60,"100","160"))))),commander(2,7,"100","120"));
      NativeXpRewards.Panel fresh=new NativeXpRewards.Panel(h,receipt,true);h.root.addView(fresh);fresh.start();fresh.skip.performClick();
      assertTrue(fresh.complete);assertNull(fresh.animator);assertEquals("+40 XP",fresh.cells.get(0).number.getText().toString());assertEquals(700,fresh.cells.get(0).bar.getProgress());assertEquals(View.VISIBLE,fresh.cells.get(0).level.getVisibility());
      assertEquals("+60 XP",fresh.cells.get(1).number.getText().toString());assertEquals(1000,fresh.cells.get(1).bar.getProgress());assertEquals(View.GONE,fresh.skip.getVisibility());
      NativeXpRewards.Panel record=new NativeXpRewards.Panel(h,receipt,false);assertEquals("기록 40 XP",record.cells.get(0).number.getText().toString());assertEquals(View.GONE,record.cells.get(0).level.getVisibility());
    }
  }
  @Test public void authoritativeProgressUsesRealLevelBoundariesAndCapWithoutGuessing()throws Exception{
    JSONObject member=MainActivity.obj("ownedId","first","appliedXp",220,"xpProgress",progress(point(3,"480","400","500"),point(5,"700","650","900")));
    NativeXpReceipt receipt=NativeXpReceipt.from(snapshot(MainActivity.obj("normalXp",MainActivity.obj("units",new JSONArray().put(member)))),null);
    NativeXpReceipt.Row row=receipt.rows.get(0);assertEquals(3,row.previousLevel);assertEquals(5,row.level);assertTrue(row.levelUp());assertEquals(800,row.progressBefore.progress());assertEquals(200,row.progressAfter.progress());
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h).close();NativeXpRewards.Panel panel=new NativeXpRewards.Panel(h,receipt,true);
      assertEquals(800,panel.cells.get(0).bar.getProgress());assertTrue(panel.cells.get(0).label.getText().toString().contains("Lv.3"));
      panel.finish();assertEquals(200,panel.cells.get(0).bar.getProgress());assertTrue(panel.cells.get(0).level.getText().toString().contains("Lv.3 → Lv.5"));
    }
    member.put("appliedXp",0).put("xpProgress",progress(point(60,"999","999",null),point(60,"999","999",null)));
    row=NativeXpReceipt.from(snapshot(MainActivity.obj("normalXp",MainActivity.obj("units",new JSONArray().put(member)))),null).rows.get(0);
    assertEquals(1000,row.progressAfter.progress());assertFalse(row.levelUp());
  }
  @Test public void inconsistentProgressNeverCreatesALevelUpOrFalseProgressBar()throws Exception{
    JSONObject member=MainActivity.obj("ownedId","first","appliedXp",20,"xpProgress",progress(point(1,"100","0","150"),point(2,"999","200","1200")));
    NativeXpReceipt.Row row=NativeXpReceipt.from(snapshot(MainActivity.obj("normalXp",MainActivity.obj("units",new JSONArray().put(member)))),null).rows.get(0);
    assertFalse(row.hasBar());assertEquals(0,row.level);assertFalse(row.levelUp());
    member.getJSONObject("xpProgress").put("after",point(2,"120","130","200"));
    row=NativeXpReceipt.from(snapshot(MainActivity.obj("normalXp",MainActivity.obj("units",new JSONArray().put(member)))),null).rows.get(0);
    assertFalse(row.hasBar());assertFalse(row.levelUp());
  }
  @Test public void adventureAndQuestReceiptsUseStableActualServerProgress()throws Exception{
    JSONObject boundaries=progress(point(1,"50","0","100"),point(1,"70","0","100"));
    JSONObject adventure=MainActivity.obj("adventureId","return-1","status","RETURNED","party",new JSONArray().put(MainActivity.obj("ownedId","first","name","귀환한 대원")),"settlement",MainActivity.obj("rewards",MainActivity.obj("xp",MainActivity.obj("first",20)),"xpProgressByOwnedId",MainActivity.obj("first",boundaries)));
    NativeXpReceipt receipt=NativeXpReceipt.from(adventure,null);assertEquals("adventure:return-1",receipt.event);assertEquals("귀환한 대원",receipt.rows.get(0).label);assertEquals(700,receipt.rows.get(0).progressAfter.progress());
    JSONObject reward=commanderReceipt(false).put("xpProgress",progress(point(2,"100","80","120"),point(3,"140","120","200")));
    receipt=NativeXpReceipt.from(MainActivity.obj("commanderReward",reward),null);assertEquals("commander:commander-test-v1:8",receipt.event);assertTrue(receipt.rows.get(0).levelUp());assertEquals(250,receipt.rows.get(0).progressAfter.progress());
    reward.put("replayed",true).put("xpAwarded",0);receipt=NativeXpReceipt.from(MainActivity.obj("commanderReward",reward),null);assertTrue(receipt.replayed);assertEquals(40,receipt.rows.get(0).amount);assertFalse(receipt.rows.get(0).levelUp());
  }
}
