package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;import android.widget.*;import org.json.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60RecruitSessionTest {
 static final NativeHomePartyFeedbackTest.Profile SMALL=new NativeHomePartyFeedbackTest.Profile("recruit-640x320",640,320,160,1,0,0,0,0);
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(SMALL);} @After public void restore(){helper.restore();}
 static JSONObject state(int revision)throws Exception{return MainActivity.obj("sessionId","round","revision",revision,"status","PENDING","rerolls",revision,"rerollCost",1,"candidate",MainActivity.obj("speciesId","u01","name","갈매미맨","rarity","COMMON","level",1));}
 static JSONObject info()throws Exception{return MainActivity.obj("enabled",true,"catalogVersion","fixture","offers",new JSONArray(),"sessions",new JSONArray().put(state(0)));}
 static class Api extends NativeApi {Result pending;List<JSONObject> bodies=new ArrayList<>();Api(Context c){super(c);}@Override String token(){return "session-ui-test";}@Override void call(String p,JSONObject b,Result cb){if(p.endsWith("/session")){try{cb.done(info(),null);}catch(Exception e){throw new RuntimeException(e);}return;}bodies.add(b);pending=cb;}}
 @Test public void actualProductPathRerollIsImmediateRetainsRequestOnFailureAndRestoresOffscreenSuccess()throws Exception{
 try(ActivityController<MainActivity> c=helper.flow.start()){MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();h.motionEnabled=false;
 h.draw();Button refresh=NativeRoster60ProductBattleTest.named(h.root,"새로고침");assertTrue(refresh.getText().toString().contains("1장"));refresh.performClick();refresh.performClick();assertEquals(1,api.bodies.size());assertEquals("reroll",api.bodies.get(0).getString("action"));
 api.pending.done(null,"network");NativeRoster60ProductBattleTest.named(h.root,"전송 결과 다시 확인").performClick();assertEquals(api.bodies.get(0).toString(),api.bodies.get(1).toString());
 h.epoch++;JSONObject reply=state(1);reply.put("requestId",api.bodies.get(0).getString("requestId"));api.pending.done(reply,null);
 NativeRecruitSession next=new NativeRecruitSession(h);JSONObject empty=info();empty.put("sessions",new JSONArray());next.open(empty);assertEquals(1,next.state.getInt("rerolls"));assertFalse(NativeRoster60ProductBattleTest.named(h.root,"새로고침").isEnabled());assertEquals(2,api.bodies.size());helper.layout(h,SMALL);assertTrue(next.card.getHeight()>0);
 helper.png(NativeHomePartyFeedbackTest.render(h.root),"recruit-session-640x320-robolectric.png");
 }}
 @Test public void reducedMotionAndDetachKeepCommittedReplacement()throws Exception{try(ActivityController<MainActivity> c=helper.flow.start()){MainActivity h=c.get();helper.prepare(h);RecruitCardTransition card=new RecruitCardTransition(h);TextView old=new TextView(h),next=new TextView(h);card.replace(old,false);card.replace(next,false);assertSame(next,card.getChildAt(0));assertNull(card.animation);TextView newer=new TextView(h);card.replace(newer,true);card.onDetachedFromWindow();assertSame(newer,card.getChildAt(0));assertNull(card.animation);assertEquals(0,newer.getTranslationY(),0);}}
 @Test public void batchPagingKeepsOtherCardsAndOffscreenBulkClaimRestoresWithoutNewCharge()throws Exception{
 try(ActivityController<MainActivity> c=helper.flow.start()){MainActivity h=c.get();helper.prepare(h);Api api=new Api(h);h.api=api;h.motionEnabled=false;h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
 JSONObject a=state(0).put("sessionId","a").put("batchId","batch"),b=state(0).put("sessionId","b").put("batchId","batch");
 NativeRecruitSession screen=new NativeRecruitSession(h);screen.fromCounter=true;screen.open(info().put("sessions",new JSONArray().put(a).put(b)));screen.page(1);assertEquals("b",screen.state.getString("sessionId"));
 ((Button)screen.slots.get(1).findViewWithTag("recruit-reroll")).performClick();assertEquals("b",api.bodies.get(0).getString("sessionId"));JSONObject changed=new JSONObject(b.toString()).put("revision",1).put("rerolls",1).put("requestId",api.bodies.get(0).getString("requestId"));api.pending.done(changed,null);
 assertEquals(0,screen.candidates.getJSONObject(0).getInt("rerolls"));assertEquals(1,screen.candidates.getJSONObject(1).getInt("rerolls"));assertFalse(((Button)screen.slots.get(1).findViewWithTag("recruit-reroll")).isEnabled());
 ((Button)h.root.findViewWithTag("recruit-claim-all")).performClick();assertEquals(2,api.bodies.get(1).getJSONArray("sessions").length());h.epoch++;
 api.pending.done(MainActivity.obj("requestId",api.bodies.get(1).getString("requestId"),"credits",193,"sessions",new JSONArray().put(new JSONObject(a.toString()).put("revision",1).put("status","CLAIMED")).put(new JSONObject(changed.toString()).put("revision",2).put("status","CLAIMED"))),null);
 NativeRecruitSession resumed=new NativeRecruitSession(h);resumed.fromCounter=true;resumed.open(info().put("sessions",new JSONArray()));assertFalse(resumed.hasPending());assertEquals(2,resumed.candidates.length());assertEquals(2,api.bodies.size());assertNotNull(h.root.findViewWithTag("recruit-back-counter"));
 // An old replay must not undo a later claimed revision.
 resumed.acceptResult(changed);assertEquals("CLAIMED",resumed.candidates.getJSONObject(1).getString("status"));
 }}

}
