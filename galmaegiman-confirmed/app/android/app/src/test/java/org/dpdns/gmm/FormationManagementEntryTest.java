package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Account/lifecycle and visible-label regression checks; no live HTTP or real-device timing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FormationManagementEntryTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static final class Api extends NativeApi {
  String account="formation-owner";final List<Result> callbacks=new ArrayList<>();final List<String> paths=new ArrayList<>();
  Api(MainActivity h){super(h);}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result done){assertNull(body);assertEquals("native/roster60/pvp/options",path);paths.add(path);callbacks.add(done);}
 }
 Api prepare(MainActivity h)throws Exception{helper.prepare(h);h.api.close();Api api=new Api(h);h.api=api;return api;}
 static JSONObject options()throws Exception{
  JSONArray units=PartyUiWorkPerformanceTest.roster(2);units.getJSONObject(0).put("level",7);units.getJSONObject(1).put("level",43);
  return MainActivity.obj("units",units,"partyIds",new JSONArray().put("o0").put("o1"));
 }
 @Test public void managementReadsOncePerOpenAndUsesOwnedLevels()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);NativeRoster60Pvp p=new NativeRoster60Pvp(h,true);p.open();p.fetchOptions();assertEquals(1,api.paths.size());
   api.callbacks.get(0).done(options(),null);
   assertEquals("Lv. 7",((Roster60MemberTile)p.slots.getChildAt(0)).role.getText().toString());
   assertEquals("Lv. 43",p.ownedTiles.get("o1").role.getText().toString());
   assertTrue(((Roster60MemberTile)p.slots.getChildAt(1)).action.getContentDescription().toString().contains("예비 1"));
   assertEquals(Arrays.asList("o0","o1"),p.selected);
   p.open();assertEquals(2,api.paths.size());
  }
 }
 @Test public void expiredCurrentOptionsRoutesToLoginWithoutRestoringAccount()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);NativeRoster60Pvp p=new NativeRoster60Pvp(h,true);p.open();api.account="";
   api.callbacks.get(0).done(null,"로그인이 만료됐어요. 다시 로그인해 주세요.");
   assertEquals("login",h.screen);assertFalse(h.state.has("me"));assertEquals(0,p.units.length());
  }
 }
 @Test public void oldEpochAndDifferentAccountCannotApplyOptionsOrRedirect()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=prepare(h);NativeRoster60Pvp p=new NativeRoster60Pvp(h,true);p.open();h.base("다른 화면","daily");api.account="";
   api.callbacks.get(0).done(null,"expired");assertEquals("daily",h.screen);
   api.account="formation-owner";p.open();api.account="new-owner";api.callbacks.get(1).done(options(),null);
   assertEquals(0,p.units.length());assertEquals("roster60Pvp",h.screen);
  }
 }
}
