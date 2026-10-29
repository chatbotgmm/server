package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Two fresh reads overlap; no request-count reduction or real RTT/FPS claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PartyEntryNavigationTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static final class Api extends NativeApi {
  String account="parallel-party-owner";final List<String> paths=new ArrayList<>();final List<Result> options=new ArrayList<>(),states=new ArrayList<>();
  Api(MainActivity h){super(h);}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result done){
   assertNull("Entry cannot save a party or spend",body);paths.add(path);
   if(path.equals("native/state"))states.add(done);else if(path.equals("native/roster60/pvp/options"))options.add(done);else fail("Unexpected read "+path);
  }
 }
 Api prepare(MainActivity h)throws Exception{
  helper.prepare(h);h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.art.key="test-key";h.api.close();Api api=new Api(h);h.api=api;return api;
 }
 static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
 @Test public void bothReadsStartBeforeStateReturnsAndOptionsAreConsumedOnce()throws Exception{
  for(boolean optionsFirst:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state);fresh.getJSONObject("me").put("credits",71);
   h.load("team");h.load("team");assertEquals(1,api.states.size());assertEquals(1,api.options.size());assertEquals(2,api.paths.size());
   if(optionsFirst)api.options.get(0).done(FormationManagementEntryTest.options(),null);
   api.states.get(0).done(fresh,null);assertEquals("roster60Pvp",h.screen);assertEquals(71,h.me().getInt("credits"));
   if(!optionsFirst)api.options.get(0).done(FormationManagementEntryTest.options(),null);
   assertEquals(2,h.rosterPvp.units.length());assertEquals(Arrays.asList("o0","o1"),h.rosterPvp.selected);assertTrue("Prepared two-member entry remains actionable",h.rosterPvp.save.isEnabled());assertEquals(1,api.options.size());assertNull(h.partyEntryRead);
   h.load("team");assertEquals(2,api.options.size());assertEquals(2,api.states.size());
  }
 }
 @Test public void staleNavigationAndAccountCannotApplyPreparedOptions()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state);h.load("team");h.base("다른 화면","daily");Object page=h.page;
   api.options.get(0).done(FormationManagementEntryTest.options(),null);api.states.get(0).done(fresh,null);assertSame(page,h.page);assertEquals("daily",h.screen);
   h.load("team");api.account="another-owner";api.options.get(1).done(FormationManagementEntryTest.options(),null);api.states.get(1).done(fresh,null);
   assertSame(page,h.page);assertNull(h.rosterPvp);assertFalse(h.busy);
  }
 }
 @Test public void optionsErrorDoesNotAddThirdReadAndExplicitRetryIsFresh()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state);h.load("team");api.options.get(0).done(null,"offline");api.states.get(0).done(fresh,null);
   assertEquals(2,api.paths.size());assertTrue(h.rosterPvp.status.getText().toString().contains("offline"));
   h.rosterPvp.fetchOptions();assertEquals(2,api.options.size());assertEquals(1,api.states.size());
  }
 }
 @Test public void options401BeforeOrAfterStateAlwaysClearsAccountAndRoutesLogin()throws Exception{
  for(boolean optionsFirst:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state);h.load("team");
   if(!optionsFirst)api.states.get(0).done(fresh,null);
   api.account="";api.options.get(0).done(null,"expired");
   if(optionsFirst)api.states.get(0).done(fresh,null);
   assertEquals("login",h.screen);assertFalse(h.state.has("me"));assertFalse(h.busy);assertEquals(2,api.paths.size());
  }
 }
 @Test public void syntheticDelaysOverlapRatherThanAdd()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state),options=FormationManagementEntryTest.options();
   h.load("team");assertEquals(2,api.paths.size());
   android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
   ui.postDelayed(()->api.states.get(0).done(fresh,null),80);
   ui.postDelayed(()->api.options.get(0).done(options,null),120);
   org.robolectric.shadows.ShadowLooper.idleMainLooper(80,java.util.concurrent.TimeUnit.MILLISECONDS);
   assertEquals("roster60Pvp",h.screen);assertEquals(0,h.rosterPvp.units.length());
   org.robolectric.shadows.ShadowLooper.idleMainLooper(40,java.util.concurrent.TimeUnit.MILLISECONDS);
   assertEquals(2,h.rosterPvp.units.length());assertEquals(2,api.paths.size());
   // Mock readiness at max(80,120)=120ms, not serial 80+120=200ms. Not network/device data.
  }
 }
 @Test public void nicknameRequirementStillWinsOverPreparedFormation()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=copy(h.state);fresh.getJSONObject("tutorial").put("nicknameRequired",true);
   h.load("team");api.options.get(0).done(FormationManagementEntryTest.options(),null);api.states.get(0).done(fresh,null);
   assertEquals("nickname",h.screen);assertNull(h.rosterPvp);
  }
 }
}
