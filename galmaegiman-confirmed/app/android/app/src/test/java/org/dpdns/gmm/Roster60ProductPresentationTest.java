package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.view.*;import android.widget.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowLooper;

/** Real product views and locally bundled approved art, never a separate review activity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h360dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ProductPresentationTest {
 final NativeGameFlowTest fixtures=new NativeGameFlowTest();
 @Before public void isolate(){fixtures.isolate();}
 void authenticate(MainActivity h){h.api.close();h.api=new NativeReviewDeepLinkTest.Recording(h);h.api.token("presentation-fixture");}
 JSONObject fighter(String id)throws Exception {JSONArray skills=new JSONArray();for(int i=0;i<6;i++)skills.put(new JSONObject().put("id","s"+i).put("name","기술 "+i).put("element","NORMAL").put("mpCost",2).put("power",12));return new JSONObject().put("speciesId",id).put("name","갈매미맨").put("level",10).put("hp",120).put("mp",23).put("stats",new JSONObject().put("hp",150).put("mp",30).put("durability",15).put("statusResistance",10).put("speed",20)).put("suitability",new JSONObject().put("NORMAL",2)).put("skills",skills);}
 JSONObject snapshot(String status)throws Exception{return new JSONObject().put("battleId","fixture-battle").put("revision",1).put("status",status).put("testBaseline",true).put("balanceProfile","not-for-player-screen").put("state",new JSONObject().put("version","five-battle-v1").put("turn",2).put("outcome","win").put("mode","story").put("fighters",new JSONArray().put(fighter("u01")).put(fighter("u07")))).put("settlement",new JSONObject().put("xpAwarded",10));}
 @Test public void battlefieldHasAttachedVitalsAndCompactCommandsWithoutDebugControls()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();b.bind(snapshot("ACTIVE"));
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   assertEquals(View.GONE,b.retry.getVisibility());assertEquals(View.GONE,b.settle.getVisibility());assertEquals(View.GONE,b.stats.getVisibility());assertNull(h.root.findViewWithTag("theme-page-header"));
   assertTrue(b.art.getContentDescription().toString().contains("HP 120/150 MP 23/30"));assertTrue(b.art.getHeight()>0);assertTrue(b.panel.getTop()>=b.art.getBottom());
   int buttons=0;for(View v:NativeReviewDeepLinkTest.all(b.panel))if(v instanceof Button&&v.getVisibility()==View.VISIBLE){buttons++;assertTrue(v.getHeight()>=40);}assertEquals(6,buttons);
   assertFalse(b.message.getText().toString().contains("시험안"));assertFalse(b.message.getText().toString().contains("five-stat"));b.pause();
  }
 }
 @Test public void finishedSnapshotSettlesOnceAndNeverDuringActiveBattle()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);int[] calls={0};NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}@Override void mutate(String op,JSONObject body){if(op.equals("settle"))calls[0]++;}};b.open();b.bind(snapshot("ACTIVE"));ShadowLooper.idleMainLooper();assertEquals(0,calls[0]);b.bind(snapshot("FINISHED"));b.bind(snapshot("FINISHED"));ShadowLooper.idleMainLooper();assertEquals(1,calls[0]);b.bind(snapshot("SETTLED"));assertEquals(View.GONE,b.panel.getVisibility());assertTrue(b.stats.getText().toString().contains("+10"));b.pause();}
 }
 @Test public void collectionHasReadablePagedDetailsAndRealFrontArt()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Library lib=new NativeRoster60Library(h);lib.base("도감","collection");JSONObject u=fighter("u01");u.put("id","u01").put("rarity","COMMON").put("ownedCount",1).put("levelOneStats",u.getJSONObject("stats"));lib.catalog=new JSONObject().put("grades",new JSONObject().put("COMMON","커먼")).put("characters",new JSONArray().put(u));lib.showCollection();assertNotNull(lib.art.getDrawable());assertEquals(NavyWorkbenchTheme.TEXT,lib.title.getCurrentTextColor());assertEquals(NavyWorkbenchTheme.TEXT,lib.detailBody.getCurrentTextColor());for(View v:NativeReviewDeepLinkTest.all(lib.root))assertFalse(v instanceof ScrollView);lib.detailPage=3;lib.bind();assertTrue(lib.detailBody.getText().toString().contains("기술 5"));}
 }
 @Test public void craftCarouselBindsMaterialAndRecipeSpecies()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);craft.open(true);craft.loading=false;craft.units=new JSONArray().put(new JSONObject().put("ownedId","owned-u05").put("speciesId","u05").put("name","황금").put("level",1));craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u21").put("name","설인"));craft.render();ImageView material=h.root.findViewWithTag("craft-material-portrait"),result=h.root.findViewWithTag("craft-result-portrait");assertNotNull(material.getDrawable());assertNotNull(result.getDrawable());craft.toggle(craft.units.getJSONObject(0));assertEquals(1,craft.selected.size());((Button)h.root.findViewWithTag("roster60-slot-0")).performClick();assertTrue(craft.selected.isEmpty());craft.leave();}
 }
}
