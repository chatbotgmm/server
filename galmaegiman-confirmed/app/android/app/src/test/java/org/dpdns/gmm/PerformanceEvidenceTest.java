package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/** Identical test-only before/after harness. Host JVM timings are not Android device latency. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PerformanceEvidenceTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();
  static final long FAKE_LATENCY_MS = 250;
  static final NativeHomePartyFeedbackTest.Profile PROFILE = NativeHomePartyFeedbackTest.PROFILES[0];

  @Before public void setup() { helper.isolate(); helper.configure(PROFILE); }
  @After public void cleanup() throws Exception { LocalGameArtTest.drain(); helper.restore(); }

  static final class Api extends NativeApi {
    final Handler main = new Handler(Looper.getMainLooper());
    final JSONObject stored;
    final List<JSONObject> calls = new ArrayList<>();
    int pending, delivered;
    long callbackNanos;
    Api(Context context, JSONObject stored) { super(context); this.stored = stored; }
    @Override String token() { return "offline-performance-evidence"; }
    @Override void call(String path, JSONObject body, Result callback) {
      long issued = SystemClock.uptimeMillis();
      JSONObject entry = MainActivity.obj("path",path,"bodyPresent",body!=null,"issuedVirtualMs",issued);
      calls.add(entry); pending++;
      main.postDelayed(() -> {
        JSONObject response;
        try { response = response(path, body); }
        catch (Exception failure) { throw new AssertionError("Fixture response "+path,failure); }
        try { entry.put("deliveredVirtualMs",SystemClock.uptimeMillis()); }
        catch (JSONException impossible) { throw new AssertionError(impossible); }
        pending--; delivered++;
        long began = System.nanoTime();
        callback.done(response,null);
        callbackNanos += System.nanoTime()-began;
      },FAKE_LATENCY_MS);
    }
    JSONObject response(String path,JSONObject body) throws Exception {
      if(path.equals("native/state"))return stored;
      if(path.equals("native/menu-access"))return MainActivity.obj("features",new JSONObject());
      if(path.equals("native/roster60/home"))return MainActivity.obj("rebirthUnlocked",true,"rebirthIntroPending",false,"mailUnread",0);
      if(path.equals("daily"))return NativeThemeReviewTest.daily();
      if(path.equals("draw"))return NativeRecruitmentProbabilityTest.serverEconomyFixture();
      if(path.equals("starter-loans"))return StarterLoanTestFixture.ineligible();
      if(path.equals("pvp/rank"))return NativeThemeReviewTest.rank();
      if(path.equals("attend"))return MainActivity.obj("result",MainActivity.obj("tickets",8,"streak",1));
      if(path.equals("native/roster60/pvp/options")) {
        JSONArray ids = new JSONArray(); for(int i=0;i<6;i++)ids.put("owned"+i);
        return MainActivity.obj("units",units(),"partyIds",ids);
      }
      if(path.equals("native/roster60/pvp/current"))return MainActivity.obj("status","idle");
      if(path.equals("native/roster60/craft/options"))return MainActivity.obj("catalogVersion","offline-measurement","units",units(),"recipes",new JSONArray().put(MainActivity.obj("resultId","u09","name","노움","ingredients",new JSONArray().put(MainActivity.obj("unitId","u01","count",2,"minLevel",1)))));
      if(path.equals("native/roster60/recruit/session")) {
        JSONArray rows = new JSONArray(); for(int i=0;i<30;i++)rows.put(NativeRecruitInertialRailTest.candidate(i));
        return MainActivity.obj("enabled",true,"catalogVersion","offline-measurement","offers",new JSONArray(),"sessions",rows);
      }
      if(path.equals("native/roster60/battle/active"))return MainActivity.obj("battles",new JSONArray().put(NativeRoster60ProductBattleTest.view()));
      if(path.equals("native/roster60/battle/read"))return NativeRoster60ProductBattleTest.view();
      throw new AssertionError("Unexpected offline request: "+path);
    }
    static JSONArray units() {
      JSONArray units=new JSONArray();
      for(int i=0;i<12;i++)units.put(MainActivity.obj("ownedId","owned"+i,"speciesId",String.format(Locale.ROOT,"u%02d",i+1),"name","측정대원"+i,"level",10,"maxLevel",60,"totalXp","100"));
      return units;
    }
    void flush() {
      int rounds=0;
      while(pending>0) {
        if(++rounds>12)throw new AssertionError("Unbounded fixture request chain: "+calls);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FAKE_LATENCY_MS));
      }
    }
  }

  static final class Hierarchy implements ViewGroup.OnHierarchyChangeListener {
    final MainActivity h;
    int added, removed, pages, lobbies;
    final Set<ViewGroup> watching = Collections.newSetFromMap(new IdentityHashMap<>());
    Hierarchy(MainActivity h) { this.h=h; watch(h.root); }
    void watch(View view) {
      if(!(view instanceof ViewGroup))return;
      ViewGroup group=(ViewGroup)view;
      if(!watching.add(group))return;
      group.setOnHierarchyChangeListener(this);
      for(int i=0;i<group.getChildCount();i++)watch(group.getChildAt(i));
    }
    @Override public void onChildViewAdded(View parent,View child) {
      added+=tree(child).size();
      if(parent==h.root&&child==h.page)pages++;
      if(parent==h.root&&child instanceof LobbyView)lobbies++;
      watch(child);
    }
    @Override public void onChildViewRemoved(View parent,View child) { removed+=tree(child).size(); }
    void clear() { added=removed=pages=lobbies=0; }
    void close() { for(ViewGroup group:watching)group.setOnHierarchyChangeListener(null); }
  }
  static Set<View> tree(View view) {
    Set<View> set=Collections.newSetFromMap(new IdentityHashMap<>());collect(view,set);return set;
  }
  static void collect(View view,Set<View> set) {
    set.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),set);}
  }
  Api prepare(MainActivity h) throws Exception {
    NativeHomePartyFeedbackTest.Api old=helper.prepare(h);
    JSONObject state=old.stored;
    state.put("tutorial",MainActivity.obj("flow","roster60-v1","step",9));
    old.close();Api api=new Api(h,state);h.api=api;h.art.key=api.token();
    h.getSharedPreferences("roster60_recruit_session",0).edit().clear().commit();
    h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
    h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();
    helper.layout(h,PROFILE);return api;
  }
  void settle(MainActivity h) throws Exception { helper.layout(h,PROFILE);LocalGameArtTest.drain();helper.layout(h,PROFILE); }
  JSONObject sample(MainActivity h,Api api,Hierarchy hierarchy,String actionName,Runnable action,boolean flush) throws Exception {
    hierarchy.clear();Set<View> before=tree(h.root);int start=api.calls.size();
    long virtual=SystemClock.uptimeMillis(),callback=api.callbackNanos,began=System.nanoTime();
    action.run();long actionNanos=System.nanoTime()-began;
    if(flush)api.flush();long critical=SystemClock.uptimeMillis()-virtual;
    began=System.nanoTime();helper.layout(h,PROFILE);long layoutNanos=System.nanoTime()-began;
    Set<View> after=tree(h.root);Set<View> retained=Collections.newSetFromMap(new IdentityHashMap<>());retained.addAll(before);retained.retainAll(after);
    JSONArray calls=new JSONArray();for(int i=start;i<api.calls.size();i++)calls.put(api.calls.get(i));
    JSONObject result=MainActivity.obj("action",actionName,"actionCodeNanos",actionNanos,"callbackCodeNanos",api.callbackNanos-callback,"explicitLayoutCodeNanos",layoutNanos,"syntheticResponseDelayEachMs",FAKE_LATENCY_MS,"syntheticCriticalPathVirtualMs",critical,"requests",calls,"requestCount",calls.length(),"hierarchyViewsAdded",hierarchy.added,"hierarchyViewsRemoved",hierarchy.removed,"basePagesBuilt",hierarchy.pages,"homeViewsBuilt",hierarchy.lobbies,"treeViewsBefore",before.size(),"treeViewsAfter",after.size(),"retainedViewIdentities",retained.size(),"newFinalViewIdentities",after.size()-retained.size());
    return result;
  }
  void write(String name,MainActivity h,JSONArray samples) throws Exception {
    settle(h);Path dir=Path.of(System.getProperty("gmm.nativeReviewOutput"),"performance-evidence");Files.createDirectories(dir);
    Bitmap bitmap=NativeHomePartyFeedbackTest.render(h.root);
    try(java.io.OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
    finally { bitmap.recycle(); }
    JSONObject report=MainActivity.obj("scenario",name,"profile","915x412 mdpi font1","measurementRuntime","Robolectric 4.16.1 SDK35 native graphics on host JVM; not real-device performance","fakeNetwork","No external request. Each callback is posted 250 ms later on paused virtual main looper. Callback/action/layout System.nanoTime spans exclude that virtual delay. Draw/art/startup are outside action timings.","samples",samples);
    Files.write(dir.resolve(name+".json"),(report.toString(2)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    System.out.println("PERFORMANCE_EVIDENCE "+name+" "+samples);
  }

  @Test public void homeAttachRequestsAndUiWork() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {for(int i=0;i<3;i++)samples.put(sample(h,api,hierarchy,"home-open-"+i,()->{h.home();LobbyView lobby=(LobbyView)h.root.getChildAt(0);if(!lobby.live)lobby.onAttachedToWindow();},true));assertEquals("home",h.screen);write("home",h,samples);}
      finally {hierarchy.close();api.close();}
    }
  }
  @Test public void partySelectionReusesLiveCatalogue() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {NativeRoster60Pvp party=new NativeRoster60Pvp(h,true);samples.put(sample(h,api,hierarchy,"party-open",party::open,true));settle(h);
        for(int i=0;i<7;i++)samples.put(sample(h,api,hierarchy,"party-toggle-"+i,()->{View target=h.root.findViewWithTag("party-unit-owned0");assertNotNull(target);assertTrue(target.performClick());},false));
        assertEquals(5,party.selected.size());assertTrue(api.calls.stream().noneMatch(x->x.optBoolean("bodyPresent")));write("party",h,samples);
      }finally{hierarchy.close();api.close();}
    }
  }
  @Test public void recruitSelectionKeepsCandidateRail() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {samples.put(sample(h,api,hierarchy,"recruit-open",h::draw,true));settle(h);View rail=h.root.findViewWithTag("recruit-candidate-rail");assertNotNull(rail);
        for(int i=0;i<7;i++){final int index=i%3;samples.put(sample(h,api,hierarchy,"candidate-select-"+index,()->{View slot=h.root.findViewWithTag("recruit-candidate-"+index);assertNotNull(slot);assertTrue(slot.performClick());},false));assertSame(rail,h.root.findViewWithTag("recruit-candidate-rail"));}
        assertTrue(api.calls.stream().noneMatch(x->x.optBoolean("bodyPresent")));write("recruit",h,samples);
      }finally{hierarchy.close();api.close();}
    }
  }
  @Test public void dailyMutationRendersOnceAfterAuthoritativeRefresh() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {samples.put(sample(h,api,hierarchy,"daily-open",h::daily,true));settle(h);
        for(int i=0;i<3;i++) {
          samples.put(sample(h,api,hierarchy,"attend-complete-"+i,()->h.mutate("attend",MainActivity.obj(),"daily"),true));
          System.out.println("DAILY_MODAL_DIAGNOSTIC cycle="+i+" busy="+h.busy+" blocked="+h.updateBlocks()+" epoch="+h.epoch+" screen="+h.screen+" hostDialog="+h.visibleDialog+" calls="+api.calls+" sample="+samples.getJSONObject(samples.length()-1));
          samples.put(sample(h,api,hierarchy,"attend-dismiss-"+i,()->{AlertDialog dialog=h.visibleDialog;assertNotNull("Production host tracks its current result modal",dialog);assertTrue("Current result modal is showing",dialog.isShowing());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));},true));
        }
        assertEquals("daily",h.screen);assertFalse(h.busy);write("daily",h,samples);
      }finally{hierarchy.close();api.close();}
    }
  }
  @Test public void craftSelectionKeepsCarouselViews() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {samples.put(sample(h,api,hierarchy,"craft-open",h::mix,true));settle(h);NativeRoster60Craft craft=h.menus.economy.roster60Craft;assertNotNull(craft);
        for(int i=0;i<7;i++)samples.put(sample(h,api,hierarchy,"material-toggle-"+i,()->craft.toggle(craft.units.optJSONObject(0)),false));
        assertEquals(1,craft.selected.size());assertTrue(api.calls.stream().noneMatch(x->x.optBoolean("bodyPresent")));write("craft",h,samples);
      }finally{hierarchy.close();api.close();}
    }
  }
  @Test public void completeActiveBattleDtoAvoidsSecondRead() throws Exception {
    try(ActivityController<MainActivity> c=helper.flow.start()) {
      MainActivity h=c.get();Api api=prepare(h);Hierarchy hierarchy=new Hierarchy(h);JSONArray samples=new JSONArray();
      try {for(int i=0;i<3;i++)samples.put(sample(h,api,hierarchy,"active-battle-open-"+i,h::openRosterBattle,true));
        assertNotNull(h.rosterBattle.view);assertEquals("test-battle",h.rosterBattle.view.optString("battleId"));assertEquals("ACTIVE",h.rosterBattle.view.optString("status"));write("battle",h,samples);
      }finally{hierarchy.close();api.close();}
    }
  }
}
