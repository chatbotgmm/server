package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60ProductBattleTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore(){helper.restore();}
 static JSONObject view() throws Exception {
  JSONArray skills=new JSONArray();for(int i=0;i<6;i++)skills.put(MainActivity.obj("id","s"+i,"name","시험기술"+i,"element","NORMAL","power",1,"mpCost",2));
  JSONObject stats=MainActivity.obj("hp",10,"mp",10,"durability",1,"statusResistance",2,"speed",3);
  JSONObject self=MainActivity.obj("name","갈매미맨","hp",10,"mp",10,"stats",stats,"suitability",new JSONObject(),"skills",skills);
  return MainActivity.obj("battleId","test-battle","revision",0,"status","ACTIVE","state",MainActivity.obj("version","five-battle-v1","turn",0,"fighters",new JSONArray(Arrays.asList(self,self))));
 }
 static class Api extends NativeApi {
  JSONObject state,run;boolean noBattle;List<String> paths=new ArrayList<>();List<JSONObject> actions=new ArrayList<>();Result pending;boolean unavailable,unlocked,introPending;int intros;
  Api(Context c)throws Exception{super(c);state=view();}
  @Override String token(){return "product-battle-fixture-account";}
  @Override void call(String p,JSONObject b,Result cb){paths.add(p);
   if(p.endsWith("battle/action")){actions.add(b);pending=cb;return;}
   if(p.endsWith("battle/active")){cb.done(MainActivity.obj("battles",(unavailable||noBattle)?new JSONArray():new JSONArray(Arrays.asList(state))),null);return;}
   if(p.endsWith("battle/read")){cb.done(state,null);return;}
   if(p.endsWith("adventure/active")){cb.done(MainActivity.obj("adventures",run==null?new JSONArray():new JSONArray().put(run)),null);return;}
   if(p.endsWith("adventure/return")){actions.add(b);pending=cb;return;}
   if(p.endsWith("battle/options")){cb.done(null,"BATTLE_POLICY_PENDING");return;}
   if(p.equals("native/menu-access")){cb.done(MainActivity.obj("features",MainActivity.obj("rebirth",MainActivity.obj("enabled",unlocked))),null);return;}
   if(p.endsWith("roster60/home")){cb.done(MainActivity.obj("rebirthUnlocked",unlocked,"rebirthIntroPending",introPending,"mailUnread",1),null);return;}
   if(p.endsWith("rebirth-intro/complete")){intros++;introPending=false;cb.done(MainActivity.obj("complete",true),null);return;}
   cb.done(new JSONObject(),null);
  }
 }
 // This headless controller has no attached decor. Dispatch the View lifecycle explicitly;
 // native layout is measured separately and is not a physical-device rendering claim.
 void home(MainActivity h){
  if(h.root.getChildCount()>0&&h.root.getChildAt(0) instanceof LobbyView){LobbyView old=(LobbyView)h.root.getChildAt(0);if(old.live&&!old.isAttachedToWindow())old.onDetachedFromWindow();}
  h.home();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);LobbyView lobby=(LobbyView)h.root.getChildAt(0);if(!lobby.isAttachedToWindow())lobby.onAttachedToWindow();
 }
 static void touch(Button b,int action,long down){MotionEvent e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,5,5,0);b.dispatchTouchEvent(e);e.recycle();}
 static List<Button> buttons(View v){List<Button> out=new ArrayList<>();if(v instanceof Button)out.add((Button)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(buttons(((ViewGroup)v).getChildAt(i)));return out;}
 static Button named(View root,String label){for(Button b:buttons(root))if(b.getText().toString().startsWith(label))return b;throw new AssertionError(label);}
 @Test public void actualHomePlayUsesAuthenticatedServerAndSixthSkillKeepsRequestOnRetry()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();home(h);
   ((Button)h.root.findViewWithTag("lobby-play")).performClick();assertEquals("roster60Battle",h.screen);assertTrue(api.paths.contains("native/roster60/battle/read"));
   NativeRoster60Battle battle=h.rosterBattle;assertEquals(View.GONE,battle.stats.getVisibility());assertEquals(2,battle.view.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getJSONObject("stats").getInt("statusResistance"));named(battle.panel,"기술").performClick();
   Button sixth=named(battle.panel,"시험기술5");assertNotNull(sixth);
   long down=android.os.SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);org.robolectric.shadows.ShadowLooper.idleMainLooper((long)ViewConfiguration.getLongPressTimeout()+1);touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,api.actions.size());
   down=android.os.SystemClock.uptimeMillis();touch(sixth,MotionEvent.ACTION_DOWN,down);touch(sixth,MotionEvent.ACTION_CANCEL,down);touch(sixth,MotionEvent.ACTION_UP,down);assertEquals(0,api.actions.size());
   sixth.performClick();assertEquals(1,api.actions.size());
   JSONObject first=api.actions.get(0);assertEquals("s5",first.getJSONObject("action").getString("skillId"));assertEquals(0,first.getInt("revision"));
   battle.resume();assertEquals(1,api.actions.size());api.pending.done(null,"연결하지 못했어요.");
   h.openRosterBattle();assertEquals(2,api.actions.size());assertEquals(first.toString(),api.actions.get(1).toString());
   JSONObject next=view();next.put("revision",1);next.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).put("mp",8);api.state=next;api.pending.done(next,null);
   assertEquals(8,h.rosterBattle.view.getJSONObject("state").getJSONArray("fighters").getJSONObject(0).getInt("mp"));
   named(h.rosterBattle.panel,"기술").performClick();h.back();assertEquals("roster60Battle",h.screen);assertFalse(h.rosterBattle.panel.isSkillPage());
   named(h.rosterBattle.panel,"집중").performClick();assertEquals("focus",api.actions.get(2).getJSONObject("action").getString("kind"));api.pending.done(null,"STALE_REVISION");
  }
 }
 @Test public void lockedHomeKeepsMailAndPolicyPendingIsVisible()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();home(h);
   org.robolectric.shadows.ShadowLooper.idleMainLooper();LobbyView lobby=(LobbyView)h.root.getChildAt(0);lobby.fetchRosterHome();assertFalse(lobby.rebirthUnlocked);assertTrue(lobby.buttons.get(14).getContentDescription().toString().contains("잠김"));assertTrue(lobby.buttons.get(15).isEnabled());assertTrue("mail badge live="+lobby.live+" current="+lobby.current()+" rootAttached="+h.root.isAttachedToWindow()+" decorAttached="+h.getWindow().getDecorView().isAttachedToWindow()+" screen="+h.screen,lobby.badges[15]);
   lobby.positions(640,320);assertTrue(lobby.boxes[15].width()>=48);assertTrue(lobby.boxes[15].height()>=48);assertFalse(android.graphics.RectF.intersects(lobby.boxes[15],lobby.boxes[5]));assertFalse(android.graphics.RectF.intersects(lobby.boxes[15],lobby.boxes[14]));
   api.unlocked=true;lobby.fetchRosterHome();assertTrue(lobby.buttons.get(14).isEnabled());
   api.unavailable=true;lobby.buttons.get(5).performClick();assertTrue(h.rosterBattle.message.getText().toString().contains("준비 중"));assertEquals(0,api.actions.size());
  }
 }
 @Test public void rebirthIntroCancelDoesNotCompleteAndRestartRecovers()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);api.unlocked=true;api.introPending=true;h.api=api;
   helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);home(h);
   LobbyView lobby=(LobbyView)h.root.getChildAt(0);lobby.fetchRosterHome();assertNotNull(lobby.rebirthIntro);
   lobby.rebirthIntro.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals(0,api.intros);
   home(h);lobby=(LobbyView)h.root.getChildAt(0);lobby.fetchRosterHome();assertNotNull(lobby.rebirthIntro);
   lobby.rebirthIntro.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals("paths="+api.paths,1,api.intros);
   home(h);lobby=(LobbyView)h.root.getChildAt(0);lobby.fetchRosterHome();assertNull(lobby.rebirthIntro);
  }
 }


 @Test public void newCommandsOpenSelectorsCancelSafelyAndSendOwnedTarget()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
   JSONObject state=api.state.getJSONObject("state"),self=state.getJSONArray("fighters").getJSONObject(0);self.put("ownedId","start-owned");
   state.put("reserves",new JSONArray().put(MainActivity.obj("ownedId","reserve-owned","name","예비","hp",10,"mp",5)));
   state.put("inventory",new JSONArray().put(MainActivity.obj("itemId","potion","count",4)));
   api.state.put("disabledCommands",MainActivity.obj("item","","switch","","escape",""));api.state.put("itemDefinitions",MainActivity.obj("potion",MainActivity.obj("name","치료약","heal",35)));
   h.openRosterBattle();NativeRoster60Battle battle=h.rosterBattle;
   named(battle.panel,"교체").performClick();assertEquals(View.VISIBLE,battle.choicePanel.getVisibility());battle.choicePanel.findViewWithTag("battle-choices-back").performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals(0,api.actions.size());
   named(battle.panel,"교체").performClick();battle.choicePanel.findViewWithTag("battle-switch-reserve-owned").performClick();assertEquals("reserve-owned",api.actions.get(0).getJSONObject("action").getString("targetOwnedId"));org.robolectric.shadows.ShadowLooper.idleMainLooper();api.pending.done(api.state,null);org.robolectric.shadows.ShadowLooper.idleMainLooper();
   named(battle.panel,"아이템").performClick();named(battle.choicePanel,"치료약").performClick();assertEquals("potion",api.actions.get(1).getJSONObject("action").getString("itemId"));org.robolectric.shadows.ShadowLooper.idleMainLooper();api.pending.done(api.state,null);org.robolectric.shadows.ShadowLooper.idleMainLooper();
   assertTrue("escape before first click prefs="+battle.prefs.getString(battle.key+":pending","")+" current="+battle.current(),named(battle.panel,"도망치기").isEnabled());named(battle.panel,"도망치기").performClick();assertEquals("항복",org.robolectric.Shadows.shadowOf(battle.selection).getTitle());assertTrue("negative disabled",battle.selection.getButton(-2).isEnabled());assertTrue("dialog not showing",battle.selection.isShowing());battle.selection.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals(2,api.actions.size());
   assertTrue("after cancel current="+battle.current()+" view="+(battle.view!=null)+" pending="+battle.prefs.getString(battle.key+":pending","")+" selection="+battle.selection+" message="+battle.message.getText(),named(battle.panel,"도망치기").isEnabled());named(battle.panel,"도망치기").performClick();assertTrue(battle.current());assertEquals("",battle.prefs.getString(battle.key+":pending",""));battle.selection.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals("escape",api.actions.get(2).getJSONObject("action").getString("kind"));org.robolectric.shadows.ShadowLooper.idleMainLooper();api.pending.done(api.state,null);org.robolectric.shadows.ShadowLooper.idleMainLooper();
  }
 }
 @Test public void adventureReconnectShowsEscrowAndReturnsThroughAuthenticatedRequest()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);api.noBattle=true;api.run=MainActivity.obj("adventureId","run-1","revision",4,"status","ACTIVE","activeBattleId",JSONObject.NULL,"party",new JSONArray(),"pendingRewards",MainActivity.obj("xp",MainActivity.obj("owned",10)));h.api=api;h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
   h.openRosterBattle();NativeRoster60Battle battle=h.rosterBattle;assertTrue(api.paths.contains("native/roster60/adventure/active"));assertTrue(battle.stats.getText().toString().contains("10"));assertTrue(battle.returnAdventure.isEnabled());battle.returnAdventure.performClick();assertEquals("run-1",api.actions.get(0).getString("adventureId"));assertEquals(4,api.actions.get(0).getInt("revision"));api.run.put("status","RETURNED");api.pending.done(api.run,null);assertEquals(View.GONE,battle.returnAdventure.getVisibility());
  }
 }

}
