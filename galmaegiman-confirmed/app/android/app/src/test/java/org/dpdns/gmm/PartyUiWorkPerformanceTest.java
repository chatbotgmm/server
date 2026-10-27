package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.*;
import android.view.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Local UI work measurements; mock response delays are separate from callback/UI wall time. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PartyUiWorkPerformanceTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(Roster60CardStripInertialTest.PHONE);}
 @After public void cleanup()throws Exception{LocalGameArtTest.drain();helper.restore();}
 static JSONArray roster(int count){JSONArray out=new JSONArray();for(int i=0;i<count;i++)out.put(MainActivity.obj("ownedId","o"+i,"speciesId","u01","name","대원 "+i,"level",10,"rarity","COMMON"));return out;}
 static final class Api extends NativeApi{
  JSONArray units=roster(60),party=new JSONArray();String account="party-perf";long delay,applyNanos;int reads,writes;
  final Handler ui=new Handler(Looper.getMainLooper());
  Api(Context c){super(c);}
  @Override String token(){return account;}
  @Override void call(String path,JSONObject body,Result result){
   assertNull("Performance probes never save or match",body);assertEquals("native/roster60/pvp/options",path);reads++;
   JSONObject response=MainActivity.obj("units",units,"partyIds",party);
   Runnable deliver=()->{long began=System.nanoTime();result.done(response,null);applyNanos=System.nanoTime()-began;};
   if(delay==0)deliver.run();else ui.postDelayed(deliver,delay);
  }
 }
 static long[] counts(NativeRoster60Pvp p){return new long[]{p.catalogueSerializations,p.slotReplacements,p.focusBindings,p.tileStateUpdates,p.ownedStrip.widthAssignments,p.ownedStrip.portraitUpdates};}
 static JSONObject delta(long[] a,long[] b){return MainActivity.obj("catalogueSerializations",b[0]-a[0],"slotReplacements",b[1]-a[1],"focusBinds",b[2]-a[2],"badgeStateUpdates",b[3]-a[3],"widthAssignments",b[4]-a[4],"portraitUpdates",b[5]-a[5]);}
 static void save(String name,JSONObject data)throws Exception{Path out=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"party");Files.createDirectories(out);Files.write(out.resolve(name),data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static NativeRoster60Pvp open(MainActivity h,NativeHomePartyFeedbackTest helper,Api api)throws Exception{
  helper.prepare(h);h.api=api;h.getSharedPreferences("roster60_human_queue",0).edit().clear().commit();
  NativeRoster60Pvp p=new NativeRoster60Pvp(h,true);p.open();helper.layout(h,Roster60CardStripInertialTest.PHONE);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();return p;
 }
 static JSONObject probe(NativeRoster60Pvp p,MainActivity h,Api api)throws Exception{
  JSONObject out=MainActivity.obj("ownedCount",60,"device","Robolectric SDK35 native graphics; real device not measured","timing","System.nanoTime UI wall time; API delays are virtual/mock milliseconds");
  long[] before=counts(p);long began=System.nanoTime();for(int i=0;i<20;i++)p.drawParty();
  out.put("unchangedDraw20",delta(before,counts(p)).put("uiWallNanos",System.nanoTime()-began));
  before=counts(p);began=System.nanoTime();for(int i=0;i<20;i++){p.ownedStrip.forceLayout();p.ownedStrip.measure(View.MeasureSpec.makeMeasureSpec(p.ownedStrip.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(p.ownedStrip.getHeight(),View.MeasureSpec.EXACTLY));}
  out.put("unchangedMeasure20",delta(before,counts(p)).put("uiWallNanos",System.nanoTime()-began));
  before=counts(p);began=System.nanoTime();for(int i=0;i<40;i++)p.ownedStrip.visibleArt();
  out.put("unchangedViewport40",delta(before,counts(p)).put("uiWallNanos",System.nanoTime()-began));
  JSONArray responses=new JSONArray();
  for(long delay:new long[]{0,80}){api.delay=delay;before=counts(p);p.fetchOptions();ShadowLooper.idleMainLooper(delay+16,TimeUnit.MILLISECONDS);responses.put(delta(before,counts(p)).put("mockResponseDelayMs",delay).put("callbackApplyWallNanos",api.applyNanos));}
  out.put("sameCatalogueResponses",responses);return out;
 }
 @Test public void unchangedPartyFramesAvoidCatalogueSerializationAndViewRebuilds()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRoster60Pvp p=open(h,helper,api);Object tile=p.ownedTiles.get("o0"),slot=p.slots.getChildAt(0);JSONObject report=probe(p,h,api);save("after-work.json",report);
   JSONObject draw=report.getJSONObject("unchangedDraw20");assertEquals(0,draw.getLong("catalogueSerializations"));assertEquals(0,draw.getLong("slotReplacements"));assertEquals(0,draw.getLong("focusBinds"));assertEquals(0,draw.getLong("badgeStateUpdates"));
   assertEquals(0,report.getJSONObject("unchangedMeasure20").getLong("widthAssignments"));assertEquals(0,report.getJSONObject("unchangedViewport40").getLong("portraitUpdates"));
   assertSame(tile,p.ownedTiles.get("o0"));assertSame(slot,p.slots.getChildAt(0));assertEquals(3,api.reads);assertEquals(0,api.writes);
   p.busy=true;p.enable();assertFalse(p.ownedTiles.get("o0").action.isEnabled());p.busy=false;p.enable();assertTrue(p.ownedTiles.get("o0").action.isEnabled());
  }
 }
 @Test public void firstCommittedLayoutKeepsMeasuredWidthsAndRealResizeAssignsOnce()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);Roster60CardStrip strip=new Roster60CardStrip(h);
   ArrayList<String> ids=new ArrayList<>();ArrayList<Roster60MemberTile> tiles=new ArrayList<>();JSONArray units=roster(60);
   for(int i=0;i<60;i++){JSONObject u=units.getJSONObject(i);ids.add(u.getString("ownedId"));tiles.add(new Roster60MemberTile(h,u,u.getString("name"),true,()->{},null,false,1,0,true));}
   strip.replace(ids,tiles);int width=h.dp(600),height=h.dp(100);
   strip.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
   long first=strip.widthAssignments;assertEquals(60,first);
   strip.layout(0,0,width,height);
   for(int i=0;i<20;i++){strip.forceLayout();strip.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));}
   assertEquals("First committed layout must not discard already-correct widths",first,strip.widthAssignments);
   int narrower=h.dp(450);
   assertTrue("The fixture must cross a real card-width boundary, not the minimum clamp",Roster60StripPosition.cardWidth(narrower,strip.minimum,strip.gap)<Roster60StripPosition.cardWidth(width,strip.minimum,strip.gap));
   strip.forceLayout();strip.measure(View.MeasureSpec.makeMeasureSpec(narrower,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));strip.layout(0,0,narrower,height);
   // A detached view has no ViewRoot traversal to perform the layout-requested second pass.
   // Force that real measure instead of reusing View's previous same-spec measurement cache.
   strip.forceLayout();strip.measure(View.MeasureSpec.makeMeasureSpec(narrower,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));strip.layout(0,0,narrower,height);
   assertEquals("A different real card width still updates every card once",first+60,strip.widthAssignments);
   assertEquals(Roster60StripPosition.cardWidth(narrower,strip.minimum,strip.gap),strip.cards.getChildAt(0).getWidth());
   for(int i=0;i<20;i++){strip.forceLayout();strip.measure(View.MeasureSpec.makeMeasureSpec(narrower,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));}
   assertEquals(first+60,strip.widthAssignments);
  }
 }
 @Test public void lateOptionsCannotReplaceTheNewEpochOrAccount()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();Api api=new Api(h);NativeRoster60Pvp p=open(h,helper,api);JSONArray original=p.units;Object tile=p.ownedTiles.get("o0");api.delay=80;api.units=roster(2);p.fetchOptions();h.base("다른 화면","collection");ShadowLooper.idleMainLooper(100,TimeUnit.MILLISECONDS);assertSame(original,p.units);assertSame(tile,p.ownedTiles.get("o0"));
   api.delay=0;api.units=roster(60);p.open();helper.layout(h,Roster60CardStripInertialTest.PHONE);original=p.units;api.delay=80;api.units=roster(1);p.fetchOptions();api.account="other-account";ShadowLooper.idleMainLooper(100,TimeUnit.MILLISECONDS);assertSame(original,p.units);assertEquals(0,api.writes);
  }
 }
 @Test public void actualBattleFormationScrollsAllOwnedIdsAndOnlyExplicitStartSubmits()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);List<JSONObject> commands=new ArrayList<>();
   NativeRoster60Battle battle=new NativeRoster60Battle(h){@Override void resume(){}@Override void mutate(String op,JSONObject body){assertEquals("create",op);commands.add(NativeRealtime.copy(body));}};battle.open();
   JSONArray units=roster(60);battle.showFormation(units,MainActivity.obj("mode","story","id","encounter"));helper.layout(h,Roster60CardStripInertialTest.PHONE);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();
   Roster60FormationView f=battle.formation;assertTrue(f.findViewWithTag("formation-owned-swipe") instanceof InertialCardScrollView);assertEquals(60,f.ownedStrip.cards.getChildCount());assertEquals(6,f.slots.getChildCount());assertNull(f.findViewWithTag("formation-page"));
   int bound=0;for(Roster60MemberTile tile:f.ownedTiles.values())if(tile.portraitBound)bound++;assertTrue("Only the viewport and a small buffer bind portraits",bound>1&&bound<10);
   View anchor=f.ownedStrip.cards.getChildAt(6);f.ownedStrip.scrollTo(anchor.getLeft()+17,0);Roster60CardStripInertialTest.settle(f.ownedStrip);int offset=f.ownedStrip.getScrollX();Object owned=f.ownedTiles.get("o1");
   long slots=f.slotReplacements,binds=f.focusBindings,states=f.tileStateUpdates;long began=System.nanoTime();for(int i=0;i<20;i++)f.draw();long repeated=System.nanoTime()-began;assertEquals(slots,f.slotReplacements);assertEquals(binds,f.focusBindings);assertEquals(states,f.tileStateUpdates);long unchangedSlots=f.slotReplacements-slots;
   for(int i=0;i<7;i++)f.toggle(units.getJSONObject(i));assertEquals(6,f.selected.size());assertEquals(6,new HashSet<>(f.selected).size());assertSame(owned,f.ownedTiles.get("o1"));helper.layout(h,Roster60CardStripInertialTest.PHONE);assertEquals(offset,f.ownedStrip.getScrollX());assertTrue(commands.isEmpty());
   f.ownedStrip.fling(1800);List<String> before=new ArrayList<>(f.selected);f.start.performClick();f.ownedTiles.get("o0").action.performClick();assertEquals(before,f.selected);assertTrue("Fling cannot start a battle",commands.isEmpty());
   Roster60CardStripInertialTest.settle(f.ownedStrip);f.start.performClick();f.start.performClick();assertEquals(1,commands.size());assertEquals("o0",commands.get(0).getString("ownedId"));assertEquals(5,commands.get(0).getJSONArray("reserveIds").length());assertTrue(f.locked);f.toggle(units.getJSONObject(0));assertEquals(before,f.selected);
   save("formation-work.json",MainActivity.obj("actualRoute","NativeRoster60Battle.showFormation","ownedCount",60,"sameSpeciesDistinctOwnedIds",true,"boundPortraitsAtFirstViewport",bound,"unchangedDraw20WallNanos",repeated,"unchangedDraw20SlotReplacements",unchangedSlots,"realDeviceMeasured",false));
  }
 }
 @Test public void captureActualPrebattleFormationAtPhoneTabletAndLargeFont()throws Exception{
  NativeHomePartyFeedbackTest.Profile[] profiles={Roster60CardStripInertialTest.PHONE,new NativeHomePartyFeedbackTest.Profile("formation-1280x800",1280,800,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("formation-915x412-font130",915,412,160,1.3f,0,0,0,0)};
  JSONArray evidence=new JSONArray();
  for(NativeHomePartyFeedbackTest.Profile profile:profiles){helper.configure(profile);
   try(ActivityController<MainActivity> c=helper.flow.start()){
    MainActivity h=c.get();helper.prepare(h);
    NativeRoster60Battle battle=new NativeRoster60Battle(h){@Override void resume(){}@Override void mutate(String op,JSONObject body){fail("A formation capture cannot start a battle");}};battle.open();
    JSONArray units=roster(60);battle.showFormation(units,MainActivity.obj("mode","story","id","capture"));Roster60FormationView f=battle.formation;for(int i=0;i<6;i++)f.toggle(units.getJSONObject(i));
    helper.layout(h,profile);ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);LocalGameArtTest.drain();helper.layout(h,profile);
    assertEquals(6,f.slots.getChildCount());assertEquals(60,f.ownedStrip.cards.getChildCount());assertNull(f.findViewWithTag("formation-page"));assertFalse(f.indicator.getText().toString().contains("/"));assertTrue(f.start.getHeight()>=h.dp(48));NativeNoScrollReviewTest.assertFullyVisible(f.start,h.root);
    int bound=0;for(Roster60MemberTile tile:f.ownedTiles.values())if(tile.portraitBound)bound++;assertTrue(bound>1&&bound<10);
    for(int i=0;i<6;i++){View tile=f.slots.getChildAt(i);assertTrue(tile.getWidth()>0&&tile.getHeight()>0);NativeNoScrollReviewTest.assertFullyVisible(tile,h.root);}
    Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"party");Files.createDirectories(output);
    android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(profile.name+".png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
    evidence.put(MainActivity.obj("profile",profile.json(),"actualRoute","NativeRoster60Battle.showFormation","selectedSlots",6,"ownedCards",60,"portraitBindings",bound,"pagingIndicator",false));
   }
  }
  save("formation-profile-evidence.json",MainActivity.obj("captures",evidence,"realDeviceMeasured",false));
 }

}
