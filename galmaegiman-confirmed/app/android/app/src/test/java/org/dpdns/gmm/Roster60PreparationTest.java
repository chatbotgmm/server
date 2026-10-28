package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60PreparationTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore(){helper.restore();}
  static JSONArray units(){return new JSONArray().put(MainActivity.obj("ownedId","first","speciesId","u01","name","첫 대원","level",2)).put(MainActivity.obj("ownedId","second","speciesId","u01","name","둘째 대원","level",30));}
  static class Api extends NativeApi {
    final ArrayList<JSONObject> writes=new ArrayList<>();Result reply;
    Api(Context context){super(context);}
    @Override String token(){return "preparation-test-account";}
    @Override void call(String path,JSONObject body,Result callback){assertEquals("native/roster60/pvp/party",path);writes.add(body);reply=callback;}
  }
  Roster60PreparationView mount(MainActivity h,JSONArray ids,java.util.function.Consumer<JSONArray> submit,Runnable cancel){
    Roster60PreparationView view=new Roster60PreparationView(h,units(),ids,"출항 준비",submit,cancel,()->true);
    h.root.removeAllViews();h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);return view;
  }
  @Test public void savedPartyProceedsOnceInItsSavedOrderWithoutOpeningOrSavingEditor()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();Api api=new Api(h);h.api=api;ArrayList<JSONArray> departures=new ArrayList<>();
      Roster60PreparationView view=mount(h,new JSONArray().put("first").put("second"),departures::add,()->{});
      assertNull(view.formation);assertTrue(view.start.isEnabled());view.start.performClick();view.start.performClick();
      assertEquals(1,departures.size());assertEquals("first",departures.get(0).getString(0));assertEquals("second",departures.get(0).getString(1));assertTrue(api.writes.isEmpty());
    }
  }
  @Test public void cancellingDraftKeepsSavedPartyAndDoesNotWriteOrDepart()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();Api api=new Api(h);h.api=api;ArrayList<JSONArray> departures=new ArrayList<>();
      Roster60PreparationView view=mount(h,new JSONArray().put("first"),departures::add,()->{});view.edit.performClick();
      view.formation.toggle(view.formation.unit("second"));view.back();assertNull(view.formation);assertEquals(1,view.saved.length());assertEquals("first",view.saved.getString(0));assertTrue(api.writes.isEmpty());assertTrue(departures.isEmpty());
    }
  }
  @Test public void saveIsSeparateFromDepartureAndFailedSaveCanRetry()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();Api api=new Api(h);h.api=api;ArrayList<JSONArray> departures=new ArrayList<>();
      Roster60PreparationView view=mount(h,new JSONArray().put("first"),departures::add,()->{});view.edit.performClick();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);
      view.formation.toggle(view.formation.unit("second"));view.formation.start.performClick();assertEquals(1,api.writes.size());assertTrue(view.saving);assertTrue(departures.isEmpty());assertEquals(1,view.saved.length());
      api.reply.done(null,"UNIT_UNAVAILABLE");assertFalse(view.saving);assertFalse(view.formation.locked);view.formation.start.performClick();assertEquals(2,api.writes.size());
      api.reply.done(MainActivity.obj("partyIds",new JSONArray().put("first").put("second")),null);assertNull(view.formation);assertEquals(2,view.saved.length());assertTrue(departures.isEmpty());view.start.performClick();assertEquals(1,departures.size());
    }
  }
  @Test public void emptyOrRejectedPartyRequiresEditingAndUncertainRequestRetriesWithoutNewDeparture()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();h.api=new Api(h);ArrayList<JSONArray> departures=new ArrayList<>();final int[] retries={0};
      Roster60PreparationView view=mount(h,new JSONArray(),departures::add,()->{});assertFalse(view.start.isEnabled());assertTrue(view.edit.isEnabled());
      view.saved=new JSONArray().put("first");view.refresh();view.start.performClick();assertEquals(1,departures.size());
      view.unlock("UNIT_UNAVAILABLE");assertFalse(view.start.isEnabled());assertTrue(view.edit.isEnabled());
      view.retryPending("연결 오류",()->retries[0]++);assertTrue(view.start.isEnabled());assertFalse(view.edit.isEnabled());view.start.performClick();view.start.performClick();assertEquals(1,retries[0]);assertEquals(1,departures.size());
    }
  }
  @Test public void lateSaveAfterLeavingCannotReplaceSavedSelectionOrDepart()throws Exception{
    try(ActivityController<MainActivity> activity=helper.flow.start()){
      MainActivity h=activity.get();h.api.close();Api api=new Api(h);h.api=api;final boolean[] current={true};ArrayList<JSONArray> departures=new ArrayList<>();
      Roster60PreparationView view=new Roster60PreparationView(h,units(),new JSONArray().put("first"),"출항 준비",departures::add,()->{},()->current[0]);
      h.root.removeAllViews();h.root.addView(view,new FrameLayout.LayoutParams(-1,-1));view.edit();helper.layout(h,NativeHomePartyFeedbackTest.PROFILES[0]);view.formation.toggle(view.formation.unit("second"));view.formation.start.performClick();
      assertEquals(1,api.writes.size());current[0]=false;api.reply.done(MainActivity.obj("partyIds",new JSONArray().put("first").put("second")),null);
      assertEquals(1,view.saved.length());assertTrue(departures.isEmpty());assertNotNull(view.formation);
    }
  }
}
