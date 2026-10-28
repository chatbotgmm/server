package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.view.*;import android.widget.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeEconomyHotPathReuseTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);} @After public void restore(){helper.restore();}
 static EditText quantity(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText found=quantity(g.getChildAt(i));if(found!=null)return found;}}return null;}
 @Test public void shopReusesBarsAndQuantityControlsButResetsAmountOnReselection()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeEconomyThemeContractTest contract=new NativeEconomyThemeContractTest();NativeEconomyThemeContractTest.Api api=contract.prepare(h);NativeEconomy ui=h.menus.economy;ui.shop();
   View first=h.root.findViewWithTag("shop-purchase-rail");EditText amount=quantity(first);assertNotNull(amount);amount.setText("3");Button buy=ui.shopActions.get("shop:tickets");long builds=ui.shopBuyBarBuilds;
   h.root.findViewWithTag("shop-card-shop:peanutTickets").performClick();View second=h.root.findViewWithTag("shop-purchase-rail");assertNotSame(first,second);assertEquals(builds+1,ui.shopBuyBarBuilds);
   View rail=h.root.findViewWithTag("shop-product-rail");for(int i=0;i<12;i++){h.root.findViewWithTag("shop-card-shop:tickets").performClick();assertSame(first,h.root.findViewWithTag("shop-purchase-rail"));assertSame(amount,quantity(first));assertSame(buy,ui.shopActions.get("shop:tickets"));assertEquals("1",amount.getText().toString());h.root.findViewWithTag("shop-card-shop:peanutTickets").performClick();assertSame(second,h.root.findViewWithTag("shop-purchase-rail"));}
   assertEquals(builds+1,ui.shopBuyBarBuilds);assertSame(rail,h.root.findViewWithTag("shop-product-rail"));assertTrue(api.writes.isEmpty());
   h.root.findViewWithTag("shop-card-shop:tickets").performClick();amount.setText("2");buy.performClick();assertTrue(api.writes.isEmpty());assertTrue(new NativeGameFlowTest().labels(h.visibleDialog.getWindow().getDecorView()).contains("새우gang 2000개"));contract.confirm(h);
   // 확인 콜백은 ViewRoot의 다음 프레임 뒤에 게시된다(기존 상점 계약 시험과 동일).
   org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
   assertEquals(1,api.writes.size());assertEquals("tickets",api.writes.get(0).optString("item"));assertEquals(2,api.writes.get(0).optInt("count"));
  }
 }
 @Test public void newShopResponseNeverReusesOldPriceLimitOrBuyBar()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeEconomyThemeContractTest contract=new NativeEconomyThemeContractTest();NativeEconomyThemeContractTest.Api api=contract.prepare(h);NativeEconomy ui=h.menus.economy;ui.shop();View old=h.root.findViewWithTag("shop-purchase-rail");
   api.me.getJSONObject("prices").getJSONObject("tickets").put("snack",2000);api.me.put("snack",1000);ui.shop();h.root.findViewWithTag("shop-card-shop:tickets").performClick();assertNotSame(old,h.root.findViewWithTag("shop-purchase-rail"));assertFalse(ui.shopActions.get("shop:tickets").isEnabled());assertTrue(api.writes.isEmpty());
  }
 }
 static class TrackedRecipes extends JSONArray {int serializations;@Override public String toString(){serializations++;return super.toString();}}
 @Test public void craftLocalRendersNeverSerializeRecipesRebuildCardsOrRebindArt()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();NativeRoster60CraftApprovedWorkbenchTest fixture=new NativeRoster60CraftApprovedWorkbenchTest();NativeRoster60CraftApprovedWorkbenchTest.Api api=new NativeRoster60CraftApprovedWorkbenchTest.Api(h);NativeRoster60Craft ui=fixture.open(h,api);NativeRoster60CraftWorkbench w=ui.workbench;
   TrackedRecipes recipes=new TrackedRecipes();for(int i=0;i<ui.recipes.length();i++)recipes.put(ui.recipes.opt(i));ui.recipes=recipes;w.allRecipes=recipes;ui.render();CraftResultCarousel carousel=w.carousel;View first=carousel.cards.getChildAt(0);long builds=w.carouselBuilds,binds=w.resultArtBinds;
   for(int i=0;i<20;i++){ui.message="선택 상태 "+i;ui.render();}assertSame(carousel,w.carousel);assertSame(first,w.carousel.cards.getChildAt(0));assertEquals(builds,w.carouselBuilds);assertEquals(binds,w.resultArtBinds);assertEquals(1,recipes.serializations);
   ui.version="new-authoritative-version";ui.render();assertNotSame(carousel,w.carousel);CraftResultCarousel next=w.carousel;ui.recipes=new JSONArray().put(api.recipe("u11",true));ui.render();assertNotSame(next,w.carousel);assertNotNull(h.root.findViewWithTag("craft-result-u11"));assertNull(h.root.findViewWithTag("craft-result-u10"));api.pool.shutdownNow();
  }
 }

 @Test public void sameCatalogRefreshUpdatesSelectedMaterialLevelsWithoutRebuildingCarousel()throws Exception{
  try(ActivityController<MainActivity> c=helper.flow.start()){
   MainActivity h=c.get();helper.prepare(h);
   final JSONObject[] payload={new NativeRoster60CraftApprovedWorkbenchTest.Api(h).options()};final String[] owner={"craft-live-inventory"};
   NativeApi api=new NativeApi(h){@Override String token(){return owner[0];}@Override void call(String path,JSONObject body,Result done){if(path.endsWith("/options"))done.done(payload[0],null);else throw new AssertionError(path);}};
   h.api=api;h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));h.mix();helper.settle(h,NativeHomePartyFeedbackTest.PROFILES[0]);NativeRoster60Craft ui=h.menus.economy.roster60Craft;NativeRoster60CraftWorkbench w=ui.workbench;w.choose(0);CraftResultCarousel carousel=w.carousel;carousel.scrollTo(100,0);int offset=carousel.getScrollX();assertTrue(offset>0);long builds=w.carouselBuilds;assertTrue(w.slots[1].getText().toString().contains("레벨제한"));
   payload[0]=new JSONObject(payload[0].toString());payload[0].getJSONArray("units").getJSONObject(1).put("level",8);ui.load();assertSame(w,ui.workbench);assertSame(carousel,w.carousel);assertEquals(builds,w.carouselBuilds);assertEquals(offset,carousel.getScrollX());assertEquals(8,ui.selected.get("low").optInt("level"));assertFalse(w.slots[1].getText().toString().contains("레벨제한"));assertNull(ui.preview);owner[0]="different-account";ui.open(true);assertNotSame(w,ui.workbench);assertTrue(ui.selected.isEmpty());api.pool.shutdownNow();
  }
 }
}
