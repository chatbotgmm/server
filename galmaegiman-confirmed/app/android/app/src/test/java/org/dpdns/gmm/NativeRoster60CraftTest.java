package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60CraftTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
 @After public void restore(){helper.restore();}
 static JSONObject preview(JSONObject input){
  try{JSONArray slots=new JSONArray();for(int i=0;i<6;i++)slots.put(MainActivity.obj("slot",i,"skillId","base-"+i,"name","기존 기술 "+(i+1),"element","NORMAL","mpCost",1,"power",1,"requiredLevel",1));
   JSONObject skill=MainActivity.obj("skillId","inherited-test","name","계승 기술","element","NORMAL","mpCost",1,"power",1,"requiredLevel",1,"allowedReplaceSlots",new JSONArray(Arrays.asList(0,1,2,3,4,5)));JSONObject selected=null;
   if(input.optJSONObject("inheritance")!=null){selected=new JSONObject(skill.toString());selected.put("replaceSlot",input.getJSONObject("inheritance").getInt("replaceSlot")).put("kindUnchanged",false).put("requiredLevelChanged",false);}
   return MainActivity.obj("resultId",input.getString("resultId"),"resultName","노움","materialIds",input.getJSONArray("materialIds"),"catalogVersion",input.getString("catalogVersion"),"expectedLevel",2,"maxLevel",60,"inheritedTotalXp",13,"previewFingerprint","e".repeat(64),"inheritance",MainActivity.obj("version",NativeCraftInheritance.VERSION,"status",selected==null?"selection-required":"selected","candidates",new JSONArray().put(skill),"resultSkillSlots",slots,"selected",selected==null?JSONObject.NULL:selected));
  }catch(Exception invalid){throw new AssertionError(invalid);}
 }
 static class Api extends NativeApi {
  List<JSONObject> confirms=new ArrayList<>();Result confirm;boolean unavailable;String receipt="unknown";
  Api(Context c){super(c);}
  @Override String token(){return "synthetic-craft-test";}
  @Override void call(String path,JSONObject b,Result cb){
   if(path.endsWith("/options")){cb.done(MainActivity.obj("catalogVersion","synthetic-only","units",new JSONArray(Arrays.asList(MainActivity.obj("ownedId","a","name","미니","level",2,"maxLevel",60,"totalXp","11"),MainActivity.obj("ownedId","b","name","미니","level",2,"maxLevel",60,"totalXp","16"))),"recipes",new JSONArray(Arrays.asList(MainActivity.obj("resultId","u09","name","노움")))),unavailable?"ROSTER_CATALOG_PENDING":null);}
   else if(path.endsWith("/preview"))cb.done(preview(b),null);
   else if(path.endsWith("/confirm")){confirms.add(b);confirm=cb;}
   else if(path.endsWith("/receipt"))cb.done(MainActivity.obj("status",receipt),null);
  }
 }
 @Test public void actualMenuUsesPreviewAndPersistsSameRequestAcrossBackAndRetry() throws Exception {
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);h.api=api;
   try {
    h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.mix();
    NativeRoster60Craft ui=h.menus.economy.roster60Craft;assertNotNull(ui);assertEquals("mix",h.screen);
    assertTrue(h.root.findViewWithTag("roster60-material-carousel").getContentDescription().toString().contains("/ 60"));
    ui.units.getJSONObject(0).put("maxLevel",99);ui.render();
    assertTrue(h.root.findViewWithTag("roster60-material-carousel").getContentDescription().toString().contains("/ 99"));
    ui.toggle(ui.units.getJSONObject(0));ui.toggle(ui.units.getJSONObject(1));ui.requestPreview();
    assertEquals(13,ui.preview.getInt("inheritedTotalXp"));assertFalse(ui.confirmButton.isEnabled());ui.selectInheritance("inherited-test",0);assertTrue(ui.confirmButton.isEnabled());
    ui.toggle(ui.units.getJSONObject(0));assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());
    ui.toggle(ui.units.getJSONObject(0));ui.requestPreview();ui.selectInheritance("inherited-test",0);ui.confirm();ui.confirm();assertEquals(1,api.confirms.size());
    String id=api.confirms.get(0).getString("requestId");NativeApi.Result stale=api.confirm;
    h.back();stale.done(null,"PREVIEW_CHANGED");assertNotNull(ui.pending);
    h.busy=false; // home reload is outside this API fixture
    h.mix();assertNotNull(ui.pending);ui.confirm();assertEquals(id,api.confirms.get(1).getString("requestId"));
    api.confirm.done(null,"NETWORK_ERROR");assertNotNull(ui.pending);ui.confirm();assertEquals(id,api.confirms.get(2).getString("requestId"));
    api.confirm.done(null,"PREVIEW_CHANGED");assertNull(ui.pending);assertNull(ui.preview);assertFalse(ui.confirmButton.isEnabled());
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void shortLandscapeKeepsControlsInsideFixedViewport() throws Exception {
  NativeHomePartyFeedbackTest.Profile p=NativeHomePartyFeedbackTest.PROFILES[1];helper.configure(p);
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);h.api=api;
   try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));helper.layout(h,p);h.mix();NativeRoster60Craft ui=h.menus.economy.roster60Craft;helper.settle(h,p);
    android.view.View viewport=h.root.findViewWithTag("navy-workbench-viewport");assertNotNull(viewport);
    assertTrue(viewport.getHeight()>0);android.graphics.Rect bounds=new android.graphics.Rect(),button=new android.graphics.Rect();
    assertTrue("viewport visible size="+viewport.getWidth()+"x"+viewport.getHeight()+" top="+viewport.getTop()+" page="+h.page.getWidth()+"x"+h.page.getHeight()+" root="+h.root.getWidth()+"x"+h.root.getHeight(),viewport.getGlobalVisibleRect(bounds));assertTrue("button visible: top="+ui.confirmButton.getTop()+" parent height="+ui.cols[2].getHeight()+" viewport="+bounds,ui.confirmButton.getGlobalVisibleRect(button));assertTrue("bounds "+bounds+" button "+button,bounds.contains(button));
    assertEquals(h.dp(48),ui.confirmButton.getHeight());
    android.view.View parent=(android.view.View)viewport.getParent();assertFalse(parent instanceof android.widget.ScrollView);
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void unapprovedCatalogDisablesGrantWithoutLegacyFallback() throws Exception {
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);api.unavailable=true;h.api=api;
   try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.mix();NativeRoster60Craft ui=h.menus.economy.roster60Craft;
    assertEquals(0,ui.units.length());assertFalse(ui.confirmButton.isEnabled());assertTrue(ui.previewButton.isEnabled());assertTrue(api.confirms.isEmpty());
   }finally{api.pool.shutdownNow();}
  }
 }
}
