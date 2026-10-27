package org.dpdns.gmm;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class RecruitEntryNavigationTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();}
  @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}

  static final class Api extends NativeApi {
    String account="parallel-recruit-entry";
    final List<String> paths=new ArrayList<>();
    final List<Result> sessions=new ArrayList<>();
    Result stateRead;
    Api(MainActivity h){super(h);}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){
      assertNull("Opening a menu must never recruit or spend",body);
      paths.add(path);
      if(path.equals("native/state"))stateRead=done;
      else if(path.equals("native/roster60/recruit/session"))sessions.add(done);
      else throw new AssertionError("Unexpected entry read: "+path);
    }
  }

  Api prepare(MainActivity h)throws Exception{
    helper.prepare(h);h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));
    h.art.key="local-test-key";h.api.close();Api api=new Api(h);h.api=api;
    h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
    return api;
  }
  static JSONObject session(){return MainActivity.obj("enabled",true,"catalogVersion","local", "sessions",new JSONArray(),"offers",new JSONArray().put(MainActivity.obj("kind","LOW","label","하급","cost",2,"enabled",true,"weights",MainActivity.obj("COMMON",100))));}

  @Test public void actualLoadStartsBothIndependentReadsAndConsumesSessionExactlyOnce()throws Exception{
    for(boolean sessionFirst:new boolean[]{true,false})try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=new JSONObject(h.state.toString());fresh.getJSONObject("me").put("credits",71);
      h.load("draw");h.load("draw");assertEquals(1,Collections.frequency(api.paths,"native/state"));assertEquals(1,api.sessions.size());assertTrue(h.busy);
      if(sessionFirst)api.sessions.get(0).done(session(),null);
      api.stateRead.done(fresh,null);assertEquals("draw",h.screen);assertFalse(h.busy);
      if(!sessionFirst)api.sessions.get(0).done(session(),null);
      assertEquals(1,api.sessions.size());assertNotNull(h.root.findViewWithTag("recruitment-stations"));assertEquals(71,h.me().getInt("credits"));assertEquals(2,api.paths.size());
    }
  }

  @Test public void lateSessionCannotRestoreOldPageAfterBackAndReentry()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=prepare(h);JSONObject fresh=new JSONObject(h.state.toString());h.load("draw");api.stateRead.done(fresh,null);Object oldPage=h.page;
      h.base("다른 화면","daily");Object newer=h.page;api.sessions.get(0).done(session(),null);assertSame(newer,h.page);assertNotSame(oldPage,h.page);assertNull(h.root.findViewWithTag("recruitment-stations"));
      h.draw();Object reentered=h.page;assertEquals(2,api.sessions.size());api.sessions.get(1).done(session(),null);assertEquals("draw",h.screen);assertNotSame(reentered,h.page);assertNotNull(h.root.findViewWithTag("recruitment-stations"));assertEquals(1,Collections.frequency(api.paths,"native/state"));
    }
  }

  @Test public void accountChangeRejectsPreparedSessionAndFreshState()throws Exception{
    try(ActivityController<MainActivity> c=helper.flow.start()){
      MainActivity h=c.get();Api api=prepare(h);JSONObject old=h.state;Object page=h.page;h.load("draw");api.account="new-account";api.sessions.get(0).done(session(),null);api.stateRead.done(new JSONObject(old.toString()),null);
      assertSame(old,h.state);assertSame(page,h.page);assertFalse(h.busy);assertNull(h.root.findViewWithTag("recruitment-stations"));
    }
  }
}
