package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60BattleErrorTest {
 final NativeRoster60PlaybackTest helper=new NativeRoster60PlaybackTest();
 @Before public void setup(){helper.setup();}
 @After public void restore(){helper.restore();}
 static class Api extends NativeRoster60PlaybackTest.Api {
  int creates;
  Api(Context c)throws Exception{super(c);}
  @Override void call(String path,JSONObject body,Result cb){
   if(path.endsWith("battle/create")){creates++;paths.add(path);pending=cb;return;}super.call(path,body,cb);
  }
 }
 @Test public void starterRejectionClearsPendingAndOffersConditionNotConnectionRetry()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.helper.prepare(h);Api api=new Api(h);h.api=api;
   h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();h.openRosterBattle();
   NativeRoster60Battle ui=h.rosterBattle;ui.mutate("create",MainActivity.obj("ownedId","owned","encounterId","first"));
   assertFalse(ui.prefs.getString(ui.key+":pending","").isEmpty());
   int calls=api.paths.size();api.pending.done(null,"TUTORIAL_STARTER_REQUIRED");
   assertEquals("",ui.prefs.getString(ui.key+":pending",""));assertEquals(calls,api.paths.size());assertEquals(1,api.creates);
   assertFalse(ui.message.getText().toString().contains("TUTORIAL_"));assertEquals("뒤로",ui.retry.getText().toString());
   assertNotNull(ui.conditionDialog);assertTrue(Roster60BattleErrorText.starterDetail(false).contains("대원을 바꿀 수 없어요"));
   ui.pause();assertNull(ui.conditionDialog);
  }
 }
 @Test public void knownBusinessAndActualNetworkErrorsHaveDifferentActions()throws Exception{
  try(ActivityController<MainActivity> c=helper.helper.flow.start()){
   MainActivity h=c.get();helper.open(h);NativeRoster60Battle ui=h.rosterBattle;
   ui.error("INSUFFICIENT_MP");assertTrue(ui.message.getText().toString().startsWith("MP가 부족"));assertEquals("진행 확인",ui.retry.getText().toString());
   ui.error("UNIT_NOT_FOUND");assertTrue(ui.message.getText().toString().contains("보유 목록"));
   ui.error("연결하지 못했어요. 인터넷 연결을 확인해 주세요.");assertEquals("연결 재시도",ui.retry.getText().toString());
  }
 }
}
