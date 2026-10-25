package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.view.*;import android.widget.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowLooper;

/** Real product views and locally bundled approved art, never a separate review activity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class Roster60ProductPresentationTest {
 final NativeGameFlowTest fixtures=new NativeGameFlowTest();
 @Before public void isolate(){fixtures.isolate();}
 void authenticate(MainActivity h){h.api.close();h.api=new NativeReviewDeepLinkTest.Recording(h);h.api.token("presentation-fixture");}
 JSONObject fighter(String id)throws Exception {JSONArray skills=new JSONArray();for(int i=0;i<6;i++)skills.put(new JSONObject().put("id","s"+i).put("name","기술 "+i).put("element","NORMAL").put("mpCost",2).put("power",12));return new JSONObject().put("speciesId",id).put("name",id.equals("u01")?"갈매미맨":id.equals("u07")?"불씨":id.equals("u06")?"서리날개 수호대원":id.equals("u05")?"황금":"먹구름").put("rarity",id.equals("u01")?"COMMON":"EPIC").put("level",10).put("hp",120).put("mp",23).put("stats",new JSONObject().put("hp",150).put("mp",30).put("durability",15).put("statusResistance",10).put("speed",20)).put("suitability",new JSONObject().put("NORMAL",2)).put("skills",skills);}
 JSONObject snapshot(String status)throws Exception{return new JSONObject().put("battleId","fixture-battle").put("revision",1).put("status",status).put("testBaseline",true).put("balanceProfile","not-for-player-screen").put("state",new JSONObject().put("version","five-battle-v1").put("turn",2).put("outcome","win").put("mode","story").put("fighters",new JSONArray().put(fighter("u01")).put(fighter("u07")))).put("settlement",new JSONObject().put("xpAwarded",10));}
 @Test public void battlefieldHasAttachedVitalsAndCompactCommandsWithoutDebugControls()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();b.bind(snapshot("ACTIVE"));
   h.root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));h.root.layout(0,0,640,360);
   assertEquals(View.GONE,b.retry.getVisibility());assertEquals(View.GONE,b.settle.getVisibility());assertEquals(View.GONE,b.stats.getVisibility());assertNull(h.root.findViewWithTag("theme-page-header"));
   assertTrue(b.art.getContentDescription().toString().contains("HP 120/150 MP 23/30"));assertTrue(b.art.getHeight()>0);assertTrue(((View)b.panel.getParent()).getLeft()>=b.art.getRight());assertTrue(((View)b.panel.getParent()).getBackground() instanceof BattleSkin);assertTrue(b.art.getHeight()>b.art.getWidth()/2);
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
 @Test public void formationKeepsSixSlotsFocusedPortraitAndExplicitStart()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);JSONArray units=new JSONArray();for(int i=0;i<7;i++)units.put(new JSONObject().put("ownedId","o"+i).put("speciesId","u01").put("name","대원 "+i));int[] submissions={0};Roster60FormationView editor=new Roster60FormationView(h,units,ids->{assertEquals(6,ids.length());assertEquals("o0",ids.optString(0));submissions[0]++;},()->{});
   for(int i=0;i<7;i++)editor.toggle(units.getJSONObject(i));assertEquals(6,editor.selected.size());assertNotNull(editor.focus.getDrawable());assertEquals(0,submissions[0]);editor.start.performClick();editor.start.performClick();assertEquals(1,submissions[0]);
  }
 }
 @Test public void switchPanelShowsCurrentAndFallenWithoutAllowingEitherTarget()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();JSONObject value=snapshot("ACTIVE"),s=value.getJSONObject("state");s.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");s.put("reserves",new JSONArray().put(fighter("u07").put("ownedId","fallen").put("hp",0)).put(fighter("u01").put("ownedId","live")));b.bind(value);b.chooseSwitch();assertEquals(View.GONE,b.panel.getVisibility());assertFalse(b.choicePanel.findViewWithTag("battle-switch-current").isEnabled());assertFalse(b.choicePanel.findViewWithTag("battle-switch-fallen").isEnabled());assertTrue(b.choicePanel.findViewWithTag("battle-switch-live").isEnabled());b.back();assertEquals(View.GONE,b.choicePanel.getVisibility());assertEquals(View.VISIBLE,b.panel.getVisibility());b.pause();
  }
 }

 @Test public void craftTabsMatchVisibleModeAndOnlyConfirmIsPrimary()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);
   for(boolean material:new boolean[]{true,false,true}){craft.open(material);craft.loading=false;craft.units=new JSONArray().put(fighter("u01").put("ownedId","one"));craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u07").put("name","불씨"));craft.render();
    assertEquals(material,h.root.findViewWithTag("craft-tab-material").isSelected());assertEquals(!material,h.root.findViewWithTag("craft-tab-result").isSelected());assertNotNull(craft.cols[0].findViewWithTag(material?"craft-material-portrait":"craft-result-portrait"));assertEquals(NavyWorkbenchTheme.TEXT,craft.previewButton.getCurrentTextColor());assertEquals(NavyWorkbenchTheme.INK,craft.confirmButton.getCurrentTextColor());
   }craft.leave();}
 }
 @Test public void formationSwipeCancelsTileClickAndBackRestoresMode()throws Exception{
  try(var c=fixtures.start()){MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}@Override void options(){message.setText("전투 선택");}};b.open();b.bind(snapshot("ACTIVE"));JSONArray units=new JSONArray();for(int i=0;i<8;i++)units.put(fighter("u01").put("ownedId","o"+i));b.showFormation(units,new JSONObject().put("mode","adventure"));assertTrue(b.message.getText().toString().contains("모험"));Roster60FormationView f=b.formation;
   h.root.measure(View.MeasureSpec.makeMeasureSpec(780,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(319,View.MeasureSpec.EXACTLY));h.root.layout(0,0,780,319);View row=f.findViewWithTag("formation-owned-swipe");long time=android.os.SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(time,time+10,action,action==MotionEvent.ACTION_DOWN?200:80,30,0);row.dispatchTouchEvent(e);e.recycle();}assertEquals(1,f.page);assertTrue(f.selected.isEmpty());b.back();assertNull(b.formation);assertEquals("전투 선택",b.message.getText().toString());b.bind(snapshot("ACTIVE"));assertTrue(b.message.getText().toString().startsWith("턴"));b.pause();
  }
 }

 void capture(MainActivity h,String name,int w,int height)throws Exception{
  h.root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));h.root.layout(0,0,w,height);
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,height,android.graphics.Bitmap.Config.ARGB_8888);h.root.draw(new android.graphics.Canvas(bitmap));
  java.io.File dir=new java.io.File("/tmp/gmm-ui-v3-renders");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+"-"+w+"x"+height+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
  JSONArray measured=new JSONArray();for(View v:NativeReviewDeepLinkTest.all(h.root))if(v instanceof TextView){TextView t=(TextView)v;measured.put(new JSONObject().put("text",t.getText()).put("width",v.getWidth()).put("height",v.getHeight()).put("color",t.getCurrentTextColor()).put("size",t.getTextSize()).put("visibility",v.getVisibility()).put("baseline",t.getBaseline()));}try(var out=new java.io.FileWriter(new java.io.File(dir,name+"-"+w+"x"+height+".json"))){out.write(measured.toString(2));}
  for(View v:NativeReviewDeepLinkTest.all(h.root))assertFalse("No page scroll",v instanceof ScrollView);
 }
 @Test public void actualProductViewsRenderPhoneAndTabletWithApprovedAssets()throws Exception{
  for(int[] size:new int[][]{{780,319},{1280,800}})try(var c=fixtures.start()){
   MainActivity h=c.get();authenticate(h);NativeRoster60Battle b=new NativeRoster60Battle(h){@Override void resume(){}};b.open();JSONObject value=snapshot("ACTIVE"),state=value.getJSONObject("state");state.getJSONArray("fighters").getJSONObject(0).put("ownedId","current");JSONArray reserves=new JSONArray();String[] ids={"u06","u07","u02","u04","u05"};for(int i=0;i<5;i++)reserves.put(fighter(ids[i]).put("ownedId","reserve"+i).put("hp",i==4?0:90));state.put("reserves",reserves);value.put("disabledCommands",new JSONObject().put("item","").put("switch","").put("escape",""));b.bind(value);
   capture(h,"battle",size[0],size[1]);
   android.graphics.Bitmap skin=BattleSkin.bitmap(h,"panel-hp");assertEquals(720,skin.getWidth());assertEquals(247,skin.getHeight());
   NativeRoster60ProductBattleTest.named(b.panel,"기술").performClick();capture(h,"skills",size[0],size[1]);
   Button skill=NativeRoster60ProductBattleTest.named(b.panel,"기술 1");long down=android.os.SystemClock.uptimeMillis();NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_DOWN,down);ShadowLooper.idleMainLooper((long)ViewConfiguration.getLongPressTimeout()+1);capture(h,"hold",size[0],size[1]);assertEquals(View.VISIBLE,h.root.findViewWithTag("battle-hold-description").getVisibility());NativeRoster60ProductBattleTest.touch(skill,MotionEvent.ACTION_UP,down);assertEquals(View.INVISIBLE,h.root.findViewWithTag("battle-hold-description").getVisibility());
   b.chooseSwitch();b.choicePanel.findViewWithTag("battle-switch-reserve0").performClick();capture(h,"switch",size[0],size[1]);assertTrue(b.choicePanel.findViewWithTag("battle-switch-confirm").isEnabled());
   JSONArray units=new JSONArray();for(int i=0;i<6;i++)units.put(fighter(i==0?"u01":ids[i-1]).put("ownedId","o"+i));b.closeChoices();b.showFormation(units,new JSONObject().put("mode","story").put("id","fixture"));for(int i=0;i<6;i++)b.formation.toggle(units.getJSONObject(i));b.formation.focused="o1";b.formation.draw();assertTrue(b.message.getText().toString().startsWith("전투 편성"));assertFalse(b.message.getText().toString().contains("턴 2"));capture(h,"formation",size[0],size[1]);assertTrue(b.formation.start.getHeight()>=48);b.pause();
   NativeRoster60Craft craft=new NativeRoster60Craft(h.menus.economy);craft.open(true);craft.loading=false;craft.version="render-fixture";craft.preview=new JSONObject().put("expectedLevel",15).put("maxLevel",60).put("inheritedTotalXp","1200").put("remainingXp","20").put("xpToNext","80");craft.units=units;craft.recipes=new JSONArray().put(new JSONObject().put("resultId","u25").put("name","룬장인"));for(int i=0;i<3;i++)craft.selected.put("o"+i,units.getJSONObject(i));craft.render();capture(h,"craft",size[0],size[1]);craft.leave();
  }
 }

}
